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
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Minimal OpenGL 2D batch renderer - the same primitive model Meteor's
 * GuiRenderer is built on: textured/coloured quads, alpha blending, scissor
 * clipping and an orthographic projection with the origin at the top-left.
 */
public class Gl2D {
    public static final int FLOATS_PER_VERTEX = 8; // x, y, u, v, r, g, b, a

    private int program;
    private int vao;
    private int vbo;
    private int uProj;
    /** 1x1 white texture: core-profile GL has no valid texture 0. */
    private int whiteTexture;

    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(4096 * FLOATS_PER_VERTEX);
    private int vertexCount;
    private int boundTexture;
    private int width, height;
    private double viewScale = 1.0, viewX, viewY;

    /** Texture id that resolves to the 1x1 white texture (0 is remapped in {@link #texture(int)}). */
    public static final int WHITE = 0;

    /** Circle texture, installed once at startup by the simulator. */
    public static int CIRCLE = 0;

    public static void setCircleTexture(int id) {
        CIRCLE = id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        whiteTexture = createWhiteTexture();

        program = createProgram();
        uProj = glGetUniformLocation(program, "uProj");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void begin() {
        begin(1.0, 0, 0);
    }

    /** Resizes the target surface (window resize / DPI change). */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
    }

    /**
     * Starts a frame whose world space is scaled and translated before being
     * mapped to the window, which is how the designer canvas zooms.
     */
    public void begin(double viewScale, double viewX, double viewY) {
        this.viewScale = viewScale;
        this.viewX = viewX;
        this.viewY = viewY;

        glViewport(0, 0, width, height);
        glClearColor(0.078f, 0.078f, 0.078f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(program);
        updateProjection();

        buffer.clear();
        vertexCount = 0;
        boundTexture = 0;
    }

    public void end() {
        flush();
        glUseProgram(0);
    }

    /**
     * Switches to an unscaled, untranslated pass without clearing, so editor
     * chrome can be drawn on top of a zoomed canvas in the same frame.
     */
    public void beginOverlay() {
        flush();
        viewScale = 1.0;
        viewX = 0;
        viewY = 0;
        updateProjection();
    }

    /** Current canvas zoom. */
    public double viewScale() {
        return viewScale;
    }

    /** Document x -> framebuffer x. */
    public double screenX(double worldX) {
        return (worldX - viewX) * viewScale;
    }

    /** Document y -> framebuffer y. */
    public double screenY(double worldY) {
        return (worldY - viewY) * viewScale;
    }

    /** Recomputes the orthographic projection from size, view scale and offset. */
    private void updateProjection() {        double sx = 2.0 * viewScale / width;
        double sy = -2.0 * viewScale / height;
        // screen = (world - view) * scale, with the origin at the top left
        float[] proj = {
            (float) sx, 0, 0, 0,
            0, (float) sy, 0, 0,
            0, 0, 1, 0,
            (float) (-1 - sx * viewX), (float) (1 + 2.0 * viewScale / height * viewY), 0, 1
        };
        glUniformMatrix4fv(uProj, false, proj);
    }

    private void flush() {
        if (vertexCount == 0) return;

        buffer.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STREAM_DRAW);

        glBindTexture(GL_TEXTURE_2D, boundTexture);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);

        glBindVertexArray(0);
        buffer.clear();
        vertexCount = 0;
    }

    /** Switches the active texture (flushes the pending batch first). */
    public void texture(int id) {
        if (id == 0) id = whiteTexture;
        if (!bindingDirty && boundTexture == id) return;
        flush();
        boundTexture = id;
        bindingDirty = false;
    }

    /**
     * Marks the GL texture binding as unknown. {@link #uploadTexture} binds and
     * unbinds a texture behind the batch's back, so without this the next draw
     * could sample a stale (even deleted) texture.
     */
    private static boolean bindingDirty;

    public static void invalidateBinding() {
        bindingDirty = true;
    }

    private static int createWhiteTexture() {
        ByteBuffer buf = BufferUtils.createByteBuffer(4);
        buf.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
        buf.flip();
        return uploadTexture(buf, 1, 1);
    }

    public void scissor(int x, int y, int w, int h) {
        flush();
        if (w <= 0 || h <= 0) {
            glEnable(GL_SCISSOR_TEST);
            glScissor(0, 0, 0, 0);
            return;
        }
        glEnable(GL_SCISSOR_TEST);
        // GL's origin is bottom-left
        glScissor(x, height - y - h, w, h);
    }

    public void scissorOff() {
        flush();
        glDisable(GL_SCISSOR_TEST);
    }

    /** A solid (or textured) quad. Pass texture 0 for pure colour. */
    public void quad(double x, double y, double w, double h,
                     double u0, double v0, double u1, double v1,
                     float r, float g, float b, float a) {
        if (w <= 0 || h <= 0) return;

        float x0 = (float) x, y0 = (float) y;
        float x1 = (float) (x + w), y1 = (float) (y + h);

        vertex(x0, y0, (float) u0, (float) v0, r, g, b, a);
        vertex(x1, y0, (float) u1, (float) v0, r, g, b, a);
        vertex(x1, y1, (float) u1, (float) v1, r, g, b, a);

        vertex(x0, y0, (float) u0, (float) v0, r, g, b, a);
        vertex(x1, y1, (float) u1, (float) v1, r, g, b, a);
        vertex(x0, y1, (float) u0, (float) v1, r, g, b, a);
    }

    public void colorQuad(double x, double y, double w, double h, float r, float g, float b, float a) {
        texture(0);
        quad(x, y, w, h, 0, 0, 1, 1, r, g, b, a);
    }

    /** Horizontal gradient quad (Meteor's separators use this). */
    public void quadGradient(double x, double y, double w, double h, float[] left, float[] right) {
        if (w <= 0 || h <= 0) return;
        texture(0);

        float x0 = (float) x, y0 = (float) y;
        float x1 = (float) (x + w), y1 = (float) (y + h);

        vertex(x0, y0, 0, 0, left[0], left[1], left[2], left[3]);
        vertex(x1, y0, 1, 0, right[0], right[1], right[2], right[3]);
        vertex(x1, y1, 1, 1, right[0], right[1], right[2], right[3]);

        vertex(x0, y0, 0, 0, left[0], left[1], left[2], left[3]);
        vertex(x1, y1, 1, 1, right[0], right[1], right[2], right[3]);
        vertex(x0, y1, 0, 1, left[0], left[1], left[2], left[3]);
    }

    public void border(double x, double y, double w, double h, float r, float g, float b, float a) {
        colorQuad(x, y, w, 1, r, g, b, a);
        colorQuad(x, y + h - 1, w, 1, r, g, b, a);
        colorQuad(x, y, 1, h, r, g, b, a);
        colorQuad(x + w - 1, y, 1, h, r, g, b, a);
    }

    /**
     * Nine-patch draw of a rounded-rect texture: corners are drawn 1:1 and the
     * edges/center are stretched. This is how Meteor renders its rounded
     * widgets, so the silhouette matches exactly.
     */
    public void roundedRect(int textureId, double x, double y, double w, double h,
                            double cornerPx, double texSize, float r, float g, float b, float a) {
        texture(textureId);

        double c = Math.min(cornerPx, Math.min(w / 2, h / 2));
        double uCorner = c / texSize;
        double uInner = 1 - uCorner;

        // corners
        quad(x, y, c, c, 0, 0, uCorner, uCorner, r, g, b, a);
        quad(x + w - c, y, c, c, uInner, 0, 1, uCorner, r, g, b, a);
        quad(x, y + h - c, c, c, 0, uInner, uCorner, 1, r, g, b, a);
        quad(x + w - c, y + h - c, c, c, uInner, uInner, 1, 1, r, g, b, a);

        // edges
        quad(x + c, y, w - c * 2, c, uCorner, 0, uInner, uCorner, r, g, b, a);
        quad(x + c, y + h - c, w - c * 2, c, uCorner, uInner, uInner, 1, r, g, b, a);
        quad(x, y + c, c, h - c * 2, 0, uCorner, uCorner, uInner, r, g, b, a);
        quad(x + w - c, y + c, c, h - c * 2, uInner, uCorner, 1, uInner, r, g, b, a);

        // center
        quad(x + c, y + c, w - c * 2, h - c * 2, uCorner, uCorner, uInner, uInner, r, g, b, a);
    }

    private void vertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        buffer.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a);
        vertexCount++;
    }

    // ================= textures =================

    /** Uploads an RGBA byte buffer as a GL texture. */
    public static int uploadTexture(ByteBuffer rgba, int w, int h) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glBindTexture(GL_TEXTURE_2D, 0);
        invalidateBinding();
        return id;
    }

    /** Loads a PNG from disk through STB (returns {textureId, width, height}). */
    public static int[] loadPng(String path) {
        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer comp = BufferUtils.createIntBuffer(1);
        STBImage.stbi_set_flip_vertically_on_load(false);
        ByteBuffer pixels = STBImage.stbi_load(path, w, h, comp, 4);
        if (pixels == null) return null;
        int tex = uploadTexture(pixels, w.get(0), h.get(0));
        STBImage.stbi_image_free(pixels);
        return new int[]{tex, w.get(0), h.get(0)};
    }

    /** Builds a 9-patch friendly rounded-rect texture with the given radius. */
    public static int roundedRectTexture(int size, int radius, int samples) {
        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float alpha = 0f;
                // supersample the corner coverage for a smooth edge
                for (int sy = 0; sy < samples; sy++) {
                    for (int sx = 0; sx < samples; sx++) {
                        float px = x + (sx + 0.5f) / samples;
                        float py = y + (sy + 0.5f) / samples;
                        if (insideRounded(px, py, size, radius)) alpha += 1f;
                    }
                }
                alpha /= (samples * samples);
                buf.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) Math.round(alpha * 255));
            }
        }
        buf.flip();
        return uploadTexture(buf, size, size);
    }

    private static boolean insideRounded(float px, float py, int size, int radius) {
        float rx = Math.min(px, size - px);
        float ry = Math.min(py, size - py);
        if (rx >= radius || ry >= radius) return true;
        float dx = radius - rx;
        float dy = radius - ry;
        return dx * dx + dy * dy <= radius * radius;
    }

    // ================= shaders =================

    private static int createProgram() {
        String vs = """
            #version 150 core
            in vec2 aPos;
            in vec2 aUv;
            in vec4 aColor;
            uniform mat4 uProj;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vUv = aUv;
                vColor = aColor;
                gl_Position = uProj * vec4(aPos, 0.0, 1.0);
            }
            """;

        String fs = """
            #version 150 core
            in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uTex;
            out vec4 fragColor;
            void main() {
                fragColor = vColor * texture(uTex, vUv);
            }
            """;

        int vsId = compile(GL_VERTEX_SHADER, vs);
        int fsId = compile(GL_FRAGMENT_SHADER, fs);

        int prog = glCreateProgram();
        glAttachShader(prog, vsId);
        glAttachShader(prog, fsId);
        glBindAttribLocation(prog, 0, "aPos");
        glBindAttribLocation(prog, 1, "aUv");
        glBindAttribLocation(prog, 2, "aColor");
        glLinkProgram(prog);

        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(prog));
        }

        glDeleteShader(vsId);
        glDeleteShader(fsId);

        glUseProgram(prog);
        int loc = glGetUniformLocation(prog, "uTex");
        glUniform1i(loc, 0);
        glUseProgram(0);

        return prog;
    }

    private static int compile(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Shader compile failed: " + glGetShaderInfoLog(id));
        }
        return id;
    }
}
