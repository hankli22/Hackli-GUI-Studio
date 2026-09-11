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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hackli.guidesigner.gl;

import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * Glyph atlas for the GL simulator. Glyphs are rasterised with AWT into an
 * RGBA atlas (the same idea Meteor uses: a texture atlas of glyph quads).
 * If a Minecraft font TTF is available it is used, otherwise a close system
 * fallback keeps the text metrics stable.
 */
public class FontAtlas {
    public static final int FIRST = 32;
    public static final int LAST = 126;
    private static final int COLS = 16;
    private static final int PADDING = 2;

    public final int texture;
    public final int cellWidth;
    public final int cellHeight;
    public final int glyphHeight;
    public final int baseline;

    /** Ascent, i.e. the height a line of text actually occupies (Meteor uses 9). */
    public final int textHeight;
    private final int atlasWidth;
    private final int atlasHeight;

    private final int[] widths = new int[LAST - FIRST + 1];

    /** How many atlas pixels are used per logical pixel (crisp when zoomed in). */
    private final int supersample;

    public FontAtlas(String ttfPath, int fontSize) {
        this(ttfPath, fontSize, 1);
    }

    /**
     * @param fontSize    logical text size in document pixels (Meteor uses 9)
     * @param supersample rasterise this many times larger and scale the glyph
     *                    quads down, so magnifying the canvas stays sharp
     */
    public FontAtlas(String ttfPath, int fontSize, int supersample) {
        this.supersample = Math.max(1, supersample);
        Font font = loadFont(ttfPath, fontSize * this.supersample);

        // measure
        BufferedImage tmp = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gm = tmp.createGraphics();
        gm.setFont(font);
        FontMetrics fm = gm.getFontMetrics();
        int maxWidth = 1;
        for (char c = FIRST; c <= LAST; c++) {
            widths[c - FIRST] = Math.max(1, fm.charWidth(c));
            maxWidth = Math.max(maxWidth, widths[c - FIRST]);
        }
        this.glyphHeight = fm.getHeight();
        this.baseline = fm.getAscent();
        this.textHeight = fm.getAscent();
        this.cellWidth = maxWidth + PADDING * 2;
        this.cellHeight = fm.getHeight() + PADDING * 2;
        gm.dispose();

        int rows = (LAST - FIRST + COLS) / COLS;
        int texWidth = cellWidth * COLS;
        int texHeight = cellHeight * rows;
        this.atlasWidth = texWidth;
        this.atlasHeight = texHeight;

        BufferedImage atlas = new BufferedImage(texWidth, texHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);

        for (char c = FIRST; c <= LAST; c++) {
            int i = c - FIRST;
            int cx = (i % COLS) * cellWidth;
            int cy = (i / COLS) * cellHeight;
            g.drawString(String.valueOf(c), cx + PADDING, cy + baseline);
        }
        g.dispose();

        ByteBuffer buf = BufferUtils.createByteBuffer(texWidth * texHeight * 4);
        for (int y = 0; y < texHeight; y++) {
            for (int x = 0; x < texWidth; x++) {
                int argb = atlas.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                // white glyph, alpha from coverage (tinted at draw time)
                buf.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) a);
            }
        }
        buf.flip();

        this.texture = Gl2D.uploadTexture(buf, texWidth, texHeight);
    }

    private static Font loadFont(String ttfPath, int size) {
        if (ttfPath != null) {
            try {
                Font f = Font.createFont(Font.TRUETYPE_FONT, new java.io.File(ttfPath)).deriveFont((float) size);
                return f;
            } catch (Exception ignored) {
            }
        }
        // Logical font: the platform picks the best available UI font, so the
        // editor is not tied to one operating system (a specific family here
        // would silently fall back to something unrelated elsewhere).
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    public int glyphWidth(char c) {
        if (c < FIRST || c > LAST) return widths[0];
        return widths[c - FIRST];
    }

    public int textWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) w += glyphWidth(text.charAt(i));
        return w;
    }

    /** Width of the text after applying a Meteor GUI scale factor. */
    public double textWidth(String text, double scale) {
        return textWidth(text) * scale / supersample;
    }

    /** Atlas pixels per logical pixel. */
    public int supersample() {
        return supersample;
    }

    /** Ascent in logical pixels at the given scale. */
    public double ascent(double scale) {
        return textHeight * scale / supersample;
    }

    /** Emits glyph quads for the given text. */
    public void draw(Gl2D gl, String text, double x, double y, float r, float g, float b, float a) {
        draw(gl, text, x, y, r, g, b, a, 1.0);
    }

    /**
     * Emits glyph quads for the given text at an arbitrary scale, which is how
     * Meteor renders its own text ({@code GuiTheme.scale}).
     */
    public void draw(Gl2D gl, String text, double x, double y, float r, float g, float b, float a, double scale) {
        if (scale <= 0) return;
        gl.texture(texture);

        // glyph quads are drawn in logical pixels; the atlas holds supersample
        // times more detail so the canvas can be magnified without blurring
        double s = scale / supersample;
        double cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                cursor += glyphWidth(' ') * s;
                continue;
            }
            if (c < FIRST || c > LAST) {
                cursor += glyphWidth('?') * s;
                continue;
            }

            int idx = c - FIRST;
            int col = idx % COLS;
            int row = idx / COLS;
            int gw = glyphWidth(c);

            // sample only the glyph's own pixels inside its atlas cell
            double u0 = (col * cellWidth + PADDING) / (double) atlasWidth;
            double u1 = u0 + gw / (double) atlasWidth;
            double v0 = (row * cellHeight) / (double) atlasHeight;
            double v1 = (row * cellHeight + cellHeight) / (double) atlasHeight;

            gl.quad(cursor, y, gw * s, cellHeight * s, u0, v0, u1, v1, r, g, b, a);
            cursor += gw * s;
        }
    }
}
