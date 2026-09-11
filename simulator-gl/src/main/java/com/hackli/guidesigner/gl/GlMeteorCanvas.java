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

import com.hackli.guidesigner.render.MeteorCanvas;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenGL implementation of {@link MeteorCanvas}: the batch renderer used by the
 * standalone comet editor. Coordinates are document space; the backend applies
 * the canvas zoom/pan when clipping.
 */
public class GlMeteorCanvas implements MeteorCanvas {
    private final Gl2D gl;
    private final FontAtlas font;

    private final List<double[]> clips = new ArrayList<>();
    private double[] clip;

    public GlMeteorCanvas(Gl2D gl, FontAtlas font) {
        this.gl = gl;
        this.font = font;
    }

    public FontAtlas font() {
        return font;
    }

    @Override
    public void quad(double x, double y, double w, double h, int argb) {
        gl.colorQuad(x, y, w, h, ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
            (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    @Override
    public void quadGradientH(double x, double y, double w, double h, int argbLeft, int argbRight) {
        gl.quadGradient(x, y, w, h, argbF(argbLeft), argbF(argbRight));
    }

    private static float[] argbF(int argb) {
        return new float[]{
            ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
            (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f
        };
    }

    @Override
    public void circle(double x, double y, double d, int argb) {
        float[] c = argbF(argb);
        gl.texture(Gl2D.CIRCLE);
        gl.quad(x, y, d, d, 0, 0, 1, 1, c[0], c[1], c[2], c[3]);
        gl.texture(Gl2D.WHITE);
    }

    @Override
    public void text(String text, double x, double y, int argb, double scale) {
        float[] c = argbF(argb);
        font.draw(gl, text, x, y, c[0], c[1], c[2], c[3], scale);
    }

    @Override
    public double textWidth(String text, double scale) {
        return font.textWidth(text, scale);
    }

    @Override
    public double ascent(double scale) {
        return font.ascent(scale);
    }

    /**
     * Draws an uploaded texture. {@link GlTextureSource} stores the rows flipped
     * so that {@code v = 0} is the top of the image, matching this top-left
     * origin coordinate system.
     */
    @Override
    public void texture(Object handle, double x, double y, double w, double h, double rotation) {
        if (!(handle instanceof Integer id) || id == 0 || w <= 0 || h <= 0) return;

        gl.texture(id);
        // Right-angle rotations are the only ones Meteor itself uses; anything
        // else is approximated by the nearest quarter turn.
        int quarter = (int) Math.round(rotation / 90.0) & 3;
        switch (quarter) {
            case 1 -> gl.quad(x, y, w, h, 0, 1, 1, 0, 1, 1, 1, 1);
            case 2 -> gl.quad(x, y, w, h, 1, 1, 0, 0, 1, 1, 1, 1);
            case 3 -> gl.quad(x, y, w, h, 1, 0, 0, 1, 1, 1, 1, 1);
            default -> gl.quad(x, y, w, h, 0, 0, 1, 1, 1, 1, 1, 1);
        }
        gl.texture(Gl2D.WHITE);
    }

    @Override
    public void pushClip(double x, double y, double w, double h) {
        double x0 = Math.max(x, clip == null ? x : clip[0]);
        double y0 = Math.max(y, clip == null ? y : clip[1]);
        double x1 = Math.min(x + w, clip == null ? x + w : clip[0] + clip[2]);
        double y1 = Math.min(y + h, clip == null ? y + h : clip[1] + clip[3]);
        clips.add(clip);
        clip = new double[]{x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
        applyClip();
    }

    @Override
    public void popClip() {
        clip = clips.isEmpty() ? null : clips.remove(clips.size() - 1);
        applyClip();
    }

    private void applyClip() {
        if (clip == null) {
            gl.scissorOff();
            return;
        }
        // document space -> framebuffer pixels, so zoom/pan stay correct
        double sx = gl.screenX(clip[0]);
        double sy = gl.screenY(clip[1]);
        double sw = clip[2] * gl.viewScale();
        double sh = clip[3] * gl.viewScale();
        gl.scissor((int) sx, (int) sy, (int) sw, (int) sh);
    }
}
