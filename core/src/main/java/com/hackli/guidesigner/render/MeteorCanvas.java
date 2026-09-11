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

package com.hackli.guidesigner.render;

/**
 * The drawing primitives {@link MeteorPainter} needs. Implementations exist for
 * OpenGL ({@code simulator-gl}) and Java2D (the desktop editor), so the Meteor
 * look is defined exactly once and every backend renders it identically.
 *
 * <p>All coordinates are in document pixels, the origin is the top left, and
 * colors are packed ARGB ints (alpha is honoured).</p>
 */
public interface MeteorCanvas {
    /** Solid quad. */
    void quad(double x, double y, double w, double h, int argb);

    /** Horizontal gradient quad (Meteor's separators use this). */
    void quadGradientH(double x, double y, double w, double h, int argbLeft, int argbRight);

    /** Filled circle whose bounding box is {@code d} by {@code d}. */
    void circle(double x, double y, double d, int argb);

    /** Text with its top-left corner at {@code (x, y)}. */
    void text(String text, double x, double y, int argb, double scale);

    /** Width of the text at the given scale. */
    double textWidth(String text, double scale);

    /** Font ascent at the given scale, for vertical centring. */
    double ascent(double scale);

    /** Intersects the current clip with the given rectangle. */
    void pushClip(double x, double y, double w, double h);

    /** Restores the previous clip. */
    void popClip();

    /**
     * Draws a texture handle (from {@link TextureSource#resolve(String)}) into
     * the given rect. Pixel art must be drawn with nearest-neighbour filtering.
     *
     * @param rotation degrees clockwise around the rect centre (Meteor's
     *                 {@code WTexture} takes one; sections and icons use 0)
     */
    default void texture(Object handle, double x, double y, double w, double h, double rotation) {
        // Back ends without texture support fall back to nothing.
    }

    /**
     * Filled triangle, used by Meteor's {@code WTriangle} (section headers).
     * Built from strips so every backend gets it for free.
     *
     * @param rotation degrees; 0 points right, -90 points down (Meteor's
     *                 {@code WSection} uses exactly these two states)
     */
    default void triangle(double x, double y, double w, double h, int argb, double rotation) {
        boolean down = Math.abs(rotation % 360) >= 45;

        if (down) {
            int steps = Math.max(3, (int) Math.round(h * 2));
            double step = h / steps;
            for (int i = 0; i < steps; i++) {
                double rowW = w * (1 - (i + 0.5) / steps);
                if (rowW <= 0.01) continue;
                quad(x + (w - rowW) / 2.0, y + i * step, rowW, step + 0.5, argb);
            }
        } else {
            int steps = Math.max(3, (int) Math.round(w * 2));
            double step = w / steps;
            for (int i = 0; i < steps; i++) {
                double colH = h * (1 - (i + 0.5) / steps);
                if (colH <= 0.01) continue;
                quad(x + i * step, y + (h - colH) / 2.0, step + 0.5, colH, argb);
            }
        }
    }
}
