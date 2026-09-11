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
 * The real Meteor GUI theme, extracted from the compiled Meteor Client
 * ({@code MeteorGuiTheme} + {@code MeteorWidget} on the 1.21.11 branch).
 *
 * <p>Every constant is the value Meteor ships with, including alpha channels
 * and the normal / hovered / pressed triples, so any renderer backend can
 * reproduce the in-game click GUI pixel for pixel.</p>
 *
 * <p>Meteor's {@code GuiTheme.scale(v)} multiplies by the user's "scale"
 * setting (default {@code 0.75}); {@code pad() = scale(6)} and
 * {@code textHeight() = 9 * scale} (Minecraft's font is 9px tall).</p>
 */
public class MeteorTheme {
    /** Meteor's GUI scale setting. 1.0 == the values used by the designer. */
    public double scale = 1.0;

    /** Meteor draws title labels 1.25x larger (GuiTheme.TITLE_TEXT_SCALE). */
    public double titleTextScale = 1.25;

    /** Minecraft's font height at scale 1. */
    public static final double FONT_HEIGHT = 9;

    // ------------------------------------------------------------------
    // colors (r, g, b, a) - straight from MeteorGuiTheme's constructor
    // ------------------------------------------------------------------

    /** backgroundColor: normal / hovered / pressed (note the alpha 200). */
    public static final int[] BACKGROUND = {20, 20, 20, 200};
    public static final int[] BACKGROUND_HOVERED = {30, 30, 30, 200};
    public static final int[] BACKGROUND_PRESSED = {40, 40, 40, 200};

    /** outlineColor: normal / hovered / pressed. */
    public static final int[] OUTLINE = {0, 0, 0, 255};
    public static final int[] OUTLINE_HOVERED = {10, 10, 10, 255};
    public static final int[] OUTLINE_PRESSED = {20, 20, 20, 255};

    public static final int[] ACCENT = {145, 61, 226, 255};
    public static final int[] CHECKBOX = {145, 61, 226, 255};
    public static final int[] TEXT = {255, 255, 255, 255};
    public static final int[] TEXT_SECONDARY = {150, 150, 150, 255};
    public static final int[] TEXT_HIGHLIGHT = {45, 125, 245, 100};
    public static final int[] TITLE_TEXT = {255, 255, 255, 255};
    public static final int[] PLACEHOLDER = {255, 255, 255, 20};
    public static final int[] MODULE_BACKGROUND = {50, 50, 50, 255};

    public static final int[] SEPARATOR_TEXT = {255, 255, 255, 255};
    public static final int[] SEPARATOR_CENTER = {255, 255, 255, 255};
    public static final int[] SEPARATOR_EDGES = {225, 225, 225, 150};

    public static final int[] SLIDER_LEFT = {100, 35, 170, 255};
    public static final int[] SLIDER_RIGHT = {50, 50, 50, 255};
    public static final int[] SLIDER_HANDLE = {130, 0, 255, 255};
    public static final int[] SLIDER_HANDLE_HOVERED = {140, 30, 255, 255};
    public static final int[] SLIDER_HANDLE_PRESSED = {150, 60, 255, 255};

    /** scrollbarColor: normal / hovered / pressed. */
    public static final int[] SCROLLBAR = {30, 30, 30, 200};
    public static final int[] SCROLLBAR_HOVERED = {40, 40, 40, 200};
    public static final int[] SCROLLBAR_PRESSED = {50, 50, 50, 200};

    public static final int[] PLUS = {50, 255, 50, 255};
    public static final int[] MINUS = {255, 50, 50, 255};
    public static final int[] FAVORITE = {250, 215, 0, 255};
    public static final int[] LOGGED_IN = {45, 225, 45, 255};

    /** Backdrop drawn in place of a texture that could not be loaded. */
    public static final int[] TEXTURE_MISSING = {45, 45, 55, 220};

    // ------------------------------------------------------------------

    /** Meteor's {@code scale(double)}. */
    public double scaled(double value) {
        return value * scale;
    }

    /** Meteor's {@code pad() = scale(6)}. */
    public double pad() {
        return scaled(6);
    }

    /** Meteor's {@code textHeight() = 9 * scale}. */
    public double textHeight() {
        return FONT_HEIGHT * scale;
    }

    /** Meteor's {@code textHeight(true)} used for title labels. */
    public double titleTextHeight() {
        return textHeight() * titleTextScale;
    }

    /** Height of a window/section header: {@code pad + text + pad}. */
    public double headerHeight() {
        return pad() * 2 + textHeight();
    }

    /** Slider handle size is {@code theme.textHeight()}. */
    public double handleSize() {
        return textHeight();
    }

    /** Two-state lookup in Meteor's {@code ThreeStateColorSetting.get(pressed, hovered)}. */
    public static int[] state(boolean pressed, boolean hovered, int[] normal, int[] hoveredColor, int[] pressedColor) {
        if (pressed) return pressedColor;
        if (hovered) return hoveredColor;
        return normal;
    }

    /** Normal / hovered / pressed triple lookup. */
    public static int[] tri(boolean pressed, boolean hovered, int[] normal) {
        if (normal == BACKGROUND) return state(pressed, hovered, BACKGROUND, BACKGROUND_HOVERED, BACKGROUND_PRESSED);
        if (normal == OUTLINE) return state(pressed, hovered, OUTLINE, OUTLINE_HOVERED, OUTLINE_PRESSED);
        if (normal == SLIDER_HANDLE) return state(pressed, hovered, SLIDER_HANDLE, SLIDER_HANDLE_HOVERED, SLIDER_HANDLE_PRESSED);
        return normal;
    }

    /** Converts a color to the float array the GL batch expects. */
    public static float[] f(int[] color) {
        return new float[]{color[0] / 255f, color[1] / 255f, color[2] / 255f, color[3] / 255f};
    }

    /** Converts a color with an alpha multiplier (0..1) to floats. */
    public static float[] f(int[] color, double alpha) {
        return new float[]{
            color[0] / 255f, color[1] / 255f, color[2] / 255f,
            (float) Math.max(0, Math.min(1, color[3] / 255.0 * alpha))
        };
    }

    /** Color as a packed ARGB int, with an optional alpha multiplier. */
    public static int argb(int[] color) {
        return argb(color, 1.0);
    }

    public static int argb(int[] color, double alpha) {
        int a = (int) Math.max(0, Math.min(255, color[3] * alpha));
        return (a << 24) | (color[0] << 16) | (color[1] << 8) | color[2];
    }

    /** Interpolates two colors, used by the separator gradient. */
    public static int mix(int argbLeft, int argbRight, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((argbLeft >>> 24) & 0xFF) + (((argbRight >>> 24) & 0xFF) - ((argbLeft >>> 24) & 0xFF)) * t);
        int r = (int) (((argbLeft >> 16) & 0xFF) + (((argbRight >> 16) & 0xFF) - ((argbLeft >> 16) & 0xFF)) * t);
        int g = (int) (((argbLeft >> 8) & 0xFF) + (((argbRight >> 8) & 0xFF) - ((argbLeft >> 8) & 0xFF)) * t);
        int b = (int) ((argbLeft & 0xFF) + ((argbRight & 0xFF) - (argbLeft & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Parses {@code #rrggbb} / {@code #aarrggbb}-style hex used by UiNode.textColor. */
    public static int[] parse(String hex, int[] fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        if (h.length() != 8) return fallback;
        try {
            return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16),
                Integer.parseInt(h.substring(6, 8), 16)
            };
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
