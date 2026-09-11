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

package com.hackli.guidesigner.testing;

import com.hackli.guidesigner.render.MeteorCanvas;

/**
 * Deterministic text metrics for headless runs.
 *
 * <p>The designers measure text with the real font (Java2D or the GL glyph
 * atlas). Headless tests must not depend on either, but they do need numbers
 * that stay stable across machines, so this models Meteor's 9px font as a fixed
 * advance per character scaled by the widget scale. It is intentionally simple:
 * tests assert layout relationships (padding, spacing, ordering), not glyph
 * shapes.</p>
 */
public final class TextMetrics {
    /** Advance of one character at scale 1, close to Minecraft's 9px font. */
    public static final double CHAR_WIDTH = 6.0;

    private TextMetrics() {}

    /** Width of a string at the given scale. */
    public static double width(String text, double scale) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() * CHAR_WIDTH * scale;
    }

    /** Meteor's {@code textHeight(scale)}. */
    public static double height(double scale) {
        return 9.0 * scale;
    }

    /**
     * A canvas that measures text with {@link TextMetrics} and discards every
     * drawing call. Use it to run the painter (and therefore the layout) with no
     * window, no font file and no GL context.
     */
    public static MeteorCanvas measurementCanvas() {
        return new MeteorCanvas() {
            @Override public void quad(double x, double y, double w, double h, int argb) {}

            @Override public void quadGradientH(double x, double y, double w, double h,
                                                 int argbLeft, int argbRight) {}

            @Override public void circle(double x, double y, double d, int argb) {}

            @Override public void text(String text, double x, double y, int argb, double scale) {}

            @Override public double textWidth(String text, double scale) {
                return width(text, scale);
            }

            @Override public double ascent(double scale) {
                return height(scale) * 0.8;
            }

            @Override public void pushClip(double x, double y, double w, double h) {}

            @Override public void popClip() {}
        };
    }
}
