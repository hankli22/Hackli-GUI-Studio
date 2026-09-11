/*
 * This file is part of Hackli GUI Studio (https://github.com/hankli22/hackli-gui-studio).
 * Copyright (C) 2026 hankli22 and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hackli.guidesigner.gl;

import com.hackli.guidesigner.assets.AssetStore;
import com.hackli.guidesigner.render.TextureSource;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_CLAMP;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;

/**
 * OpenGL texture source: uploads each PNG once and keeps the GL texture id.
 *
 * <p>Uploads must happen on the thread that owns the GL context, and both the
 * editor and the offscreen renderer draw from that thread, so a lazy upload on
 * first draw is safe. {@code glDeleteTextures} on shutdown is handled through
 * {@link #dispose()}.</p>
 */
public final class GlTextureSource implements TextureSource {
    /** Texture ids, in upload order, so {@link #dispose()} can free them. */
    private final Map<String, Integer> textures = new HashMap<>();
    private final Set<String> misses = new HashSet<>();

    /** The generated icon atlas, decoded once on first use. */
    private BufferedImage atlas;
    private boolean atlasTried;

    private final AssetStore assets = AssetStore.get();

    @Override
    public Object resolve(String reference) {
        if (reference == null || reference.isEmpty()) return null;

        String key = reference.trim();
        if (misses.contains(key)) return null;

        Integer cached = textures.get(key);
        if (cached != null) return cached;

        // Extracted textures first, then the generated atlas: an atlas cell is
        // uploaded as its own 16x16 texture, so the painter and GlMeteorCanvas
        // keep working with plain texture ids.
        Integer id = null;
        Path file = file(key);
        if (file != null) id = upload(file);
        if (id == null) id = tile(key);

        if (id == null) {
            misses.add(key);
            return null;
        }
        // upload()/tile() bind and unbind behind the batch's back.
        Gl2D.invalidateBinding();
        textures.put(key, id);
        return id;
    }

    @Override
    public Path fileOf(String reference) {
        String key = reference == null ? null : reference.trim();
        if (key == null || key.isEmpty()) return null;
        Path extracted = file(key);
        // Generated cells only exist as a slice of the sheet.
        return extracted != null ? extracted : assets.iconAtlasCell(key);
    }

    /** Frees every uploaded texture. Call before terminating GLFW. */
    public void dispose() {
        for (Integer id : textures.values()) {
            if (id != null) glDeleteTextures(id.intValue());
        }
        textures.clear();
        misses.clear();
    }

    /** Number of uploaded textures (diagnostics / self test). */
    public int uploaded() {
        return textures.size();
    }

    private Path file(String reference) {
        if (reference == null || reference.isEmpty()) return null;

        Path icon = assets.meteorIcon(reference);
        if (icon != null) return icon;

        Path texture = assets.texturePath(reference);
        if (texture != null) return texture;

        String resolved = assets.textureOfItem(reference);
        return resolved == null ? null : assets.texturePath(resolved);
    }

    /**
     * Uploads one cell of the generated icon atlas ({@code assets/icons/icon-atlas.png}),
     * or returns {@code null} when it has no cell for the reference. The sheet is
     * decoded once and kept for the lifetime of the editor; each cell becomes its
     * own texture so the rest of the renderer never has to know about atlases.
     */
    private Integer tile(String reference) {
        BufferedImage sheet = atlasImage();
        AssetStore.IconTile cell = assets.iconOf(reference);
        if (sheet == null || cell == null) return null;

        int size = cell.cell();
        if (cell.x() + size > sheet.getWidth() || cell.y() + size > sheet.getHeight()) return null;
        return upload(sheet.getSubimage(cell.x(), cell.y(), size, size));
    }

    /** The decoded atlas sheet, or {@code null} when no generated atlas is present. */
    private BufferedImage atlasImage() {
        if (atlasTried) return atlas;
        atlasTried = true;

        Path file = assets.iconAtlas();
        if (file == null) return null;
        try {
            atlas = ImageIO.read(file.toFile());
        } catch (IOException e) {
            atlas = null;
        }
        return atlas;
    }

    private static Integer upload(Path file) {
        try {
            return upload(ImageIO.read(file.toFile()));
        } catch (IOException e) {
            return null;
        }
    }

    private static Integer upload(BufferedImage image) {
        if (image == null) return null;

        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        try {
            // GL's texture origin is bottom-left while PNG rows start at the top.
            // The renderer maps v=0 to the top of the quad, so upload the rows in
            // reverse: that makes the topmost PNG row land at v=0.
            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    buffer.put((byte) ((argb >> 16) & 0xFF));
                    buffer.put((byte) ((argb >> 8) & 0xFF));
                    buffer.put((byte) (argb & 0xFF));
                    buffer.put((byte) ((argb >>> 24) & 0xFF));
                }
            }
            buffer.flip();

            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            // Pixel art: NEAREST keeps a 16x16 item icon crisp when scaled 2x.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA,
                org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            glBindTexture(GL_TEXTURE_2D, 0);
            return id;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /** Unused import guard: keeps GL_LINEAR referenced for future smoothing modes. */
    @SuppressWarnings("unused")
    private static final int SMOOTH_FILTER = GL_LINEAR;
}
