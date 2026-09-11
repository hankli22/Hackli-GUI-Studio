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

import com.hackli.guidesigner.render.MeteorTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode widgets drawn with Meteor's own chrome (state coloured
 * background, {@code scale(2)} inset outline, accent selection). The editor
 * builds its whole UI - toolbar, palette, tree, inspector, popup menus - from
 * these primitives, so the tool itself looks like the click GUI.
 */
public class GlUi {
    /** Result of {@link #field(String, double, double, double, double, String)}. */
    public static final class Field {
        public String text;
        public boolean focused;
        public boolean changed;
        public boolean committed;
        public boolean cancelled;
    }

    private final Gl2D gl;
    private FontAtlas font;
    private final MeteorTheme theme;
    private double uiScale;

    public double mouseX = -1, mouseY = -1;
    public boolean mouseDown;
    public boolean mousePressed;
    public boolean mouseReleased;
    public double scroll;

    private String hot;
    private String active;
    private String focus;
    private String buffer = "";
    private int caret;
    private double blink;
    private boolean caretVisible = true;

    private final List<double[]> clips = new ArrayList<>();
    private double[] clip;

    public GlUi(Gl2D gl, FontAtlas font, MeteorTheme theme, double uiScale) {
        this.gl = gl;
        this.font = font;
        this.theme = theme;
        this.uiScale = uiScale;
    }

    public MeteorTheme theme() {
        return theme;
    }

    public FontAtlas font() {
        return font;
    }

    /** Swaps in a freshly rasterised atlas when the editor UI scale changes. */
    public void setFont(FontAtlas font, double uiScale) {
        this.font = font;
        this.uiScale = uiScale;
    }

    /** Display scale of the editor chrome (DPI aware). */
    public double uiScale() {
        return uiScale;
    }

    /** Scaled length for editor chrome metrics. */
    public double scaled(double value) {
        return value * uiScale;
    }

    /** Meteor's pad(6), scaled for the editor UI. */
    public double pad() {
        return scaled(6);
    }

    /** Height of a line of UI text. */
    public double textHeight() {
        return font.textHeight;
    }

    /** Height of one list row / toolbar button. */
    public double lineHeight() {
        return textHeight() + pad() * 2;
    }

    /** Call once per frame before building the UI. */
    public void begin(double mouseX, double mouseY, boolean mouseDown, boolean mousePressed,
                      boolean mouseReleased, double scroll, double dt) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.mouseDown = mouseDown;
        this.mousePressed = mousePressed;
        this.mouseReleased = mouseReleased;
        this.scroll = scroll;
        this.hot = null;
        if (mouseReleased) active = null;

        blink += dt;
        if (blink >= 1.0) {
            blink = 0;
            caretVisible = !caretVisible;
        }
    }

    public void end() {
        mousePressed = false;
        mouseReleased = false;
        scroll = 0;
    }

    public boolean isFocused(String id) {
        return id.equals(focus);
    }

    public boolean hasFocus() {
        return focus != null;
    }

    /** Commits the focused field (used when the editor wants the value). */
    public void blur() {
        focus = null;
    }

    /** Programmatically focuses a field (used by modal prompts). */
    public void focusField(String id, String value) {
        focus = id;
        buffer = value == null ? "" : value;
        caret = buffer.length();
    }

    /** Routes a typed character into the focused field. Returns true if consumed. */
    public boolean typeChar(int codepoint) {
        if (focus == null) return false;
        String c = new String(Character.toChars(codepoint));
        buffer = buffer.substring(0, caret) + c + buffer.substring(caret);
        caret += c.length();
        return true;
    }

    /**
     * Routes a key into the focused field. Returns true when the field
     * consumed it (so the editor must not treat it as a shortcut).
     */
    public boolean key(int key, boolean ctrl) {
        if (focus == null) return false;

        switch (key) {
            case 259 -> { // backspace
                if (caret > 0) {
                    buffer = buffer.substring(0, caret - 1) + buffer.substring(caret);
                    caret--;
                }
                return true;
            }
            case 261 -> { // delete
                if (caret < buffer.length()) {
                    buffer = buffer.substring(0, caret) + buffer.substring(caret + 1);
                }
                return true;
            }
            case 263 -> { // left
                caret = Math.max(0, caret - 1);
                return true;
            }
            case 262 -> { // right
                caret = Math.min(buffer.length(), caret + 1);
                return true;
            }
            case 268 -> { // home
                caret = 0;
                return true;
            }
            case 269 -> { // end
                caret = buffer.length();
                return true;
            }
            case 257 -> { // enter -> commit
                focus = null;
                return true;
            }
            case 256 -> { // escape -> cancel
                focus = null;
                buffer = null;
                return true;
            }
            default -> {
                return ctrl && key == 86; // Ctrl+V is swallowed (no clipboard support)
            }
        }
    }

    // ------------------------------------------------------------------
    // drawing helpers
    // ------------------------------------------------------------------

    public void text(String s, double x, double y, int[] color) {
        float[] c = MeteorTheme.f(color);
        font.draw(gl, s, x, y, c[0], c[1], c[2], c[3], 1.0);
    }

    public void textDim(String s, double x, double y) {
        text(s, x, y, MeteorTheme.TEXT_SECONDARY);
    }

    public double textWidth(String s) {
        return font.textWidth(s, 1.0);
    }

    public void quad(double x, double y, double w, double h, int[] color) {
        float[] c = MeteorTheme.f(color);
        gl.colorQuad(x, y, w, h, c[0], c[1], c[2], c[3]);
    }

    public void panel(double x, double y, double w, double h) {
        quad(x, y, w, h, MeteorTheme.BACKGROUND);
    }

    /** Meteor's renderBackground: background inset by scale(2) in an outline frame. */
    public void chrome(double x, double y, double w, double h, boolean pressed, boolean hovered) {
        double s = scaled(2);
        int[] bg = MeteorTheme.tri(pressed, hovered, MeteorTheme.BACKGROUND);
        int[] ol = MeteorTheme.tri(pressed, hovered, MeteorTheme.OUTLINE);
        quad(x + s, y + s, w - s * 2, h - s * 2, bg);
        quad(x, y, w, s, ol);
        quad(x, y + h - s, w, s, ol);
        quad(x + s, y + s, s, h - s * 2, ol);
        quad(x + w - s, y + s, s, h - s * 2, ol);
    }

    public void border(double x, double y, double w, double h, int[] color, double alpha) {
        float[] c = MeteorTheme.f(color, alpha);
        double t = 1;
        gl.colorQuad(x, y, w, t, c[0], c[1], c[2], c[3]);
        gl.colorQuad(x, y + h - t, w, t, c[0], c[1], c[2], c[3]);
        gl.colorQuad(x, y, t, h, c[0], c[1], c[2], c[3]);
        gl.colorQuad(x + w - t, y, t, h, c[0], c[1], c[2], c[3]);
    }

    /** Section header with the accent bar Meteor uses for window titles. */
    public void header(String title, double x, double y, double w, double h) {
        quad(x, y, w, h, MeteorTheme.ACCENT);
        text(title, x + pad(), y + pad(), MeteorTheme.TITLE_TEXT);
    }

    // ------------------------------------------------------------------
    // clipping
    // ------------------------------------------------------------------

    public void pushClip(double x, double y, double w, double h) {
        double x0 = Math.max(x, clip == null ? x : clip[0]);
        double y0 = Math.max(y, clip == null ? y : clip[1]);
        double x1 = Math.min(x + w, clip == null ? x + w : clip[0] + clip[2]);
        double y1 = Math.min(y + h, clip == null ? y + h : clip[1] + clip[3]);
        clips.add(clip);
        clip = new double[]{x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)};
        applyClip();
    }

    public void popClip() {
        clip = clips.isEmpty() ? null : clips.remove(clips.size() - 1);
        applyClip();
    }

    private void applyClip() {
        if (clip == null) {
            gl.scissorOff();
        } else if (clip[2] <= 0 || clip[3] <= 0) {
            gl.scissor(0, 0, 0, 0);
        } else {
            gl.scissor((int) clip[0], (int) clip[1], (int) clip[2], (int) clip[3]);
        }
    }

    public double[] clip() {
        return clip;
    }

    // ------------------------------------------------------------------
    // widgets
    // ------------------------------------------------------------------

    private boolean inside(double x, double y, double w, double h) {
        if (clip != null && (mouseX < clip[0] || mouseX > clip[0] + clip[2]
            || mouseY < clip[1] || mouseY > clip[1] + clip[3])) return false;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** Meteor button. Returns true when clicked. */
    public boolean button(String id, double x, double y, double w, double h, String label) {
        boolean over = inside(x, y, w, h);
        if (over) hot = id;
        boolean clicked = over && mousePressed;
        if (clicked) active = id;

        boolean pressed = id.equals(active) && mouseDown && over;
        chrome(x, y, w, h, pressed, over);
        text(label, x + w / 2.0 - textWidth(label) / 2.0, y + pad(), MeteorTheme.TEXT);
        return clicked;
    }

    /** Button with a left-aligned label (used by the tree and palette). */
    public boolean row(String id, double x, double y, double w, double h, String label, boolean selected) {
        boolean over = inside(x, y, w, h);
        if (over) hot = id;

        if (selected) quad(x, y, w, h, MeteorTheme.MODULE_BACKGROUND);
        else if (over) quad(x, y, w, h, MeteorTheme.BACKGROUND_HOVERED);

        text(label, x + pad(), y + (h - textHeight()) / 2.0,
            selected ? MeteorTheme.TEXT : MeteorTheme.TEXT_SECONDARY);

        if (!over || !mousePressed) return false;
        active = id;
        return true;
    }

    /** Meteor check box (accent square inside the state background). */
    public boolean checkbox(String id, double x, double y, double size, boolean value) {
        boolean over = inside(x, y, size, size);
        boolean pressed = over && mouseDown;
        chrome(x, y, size, size, pressed, over);
        if (value) {
            double s = (size - scaled(2)) / 1.75;
            quad(x + (size - s) / 2.0, y + (size - s) / 2.0, s, s, MeteorTheme.CHECKBOX);
        }
        return over && mousePressed ? !value : value;
    }

    /**
     * Text field with a caret. While focused the live buffer is returned and
     * {@code changed} tells the caller to apply it immediately.
     */
    public Field field(String id, double x, double y, double w, double h, String value) {
        Field out = new Field();
        boolean over = inside(x, y, w, h);

        if (mousePressed) {
            if (over) {
                if (!isFocused(id)) {
                    focus = id;
                    buffer = value == null ? "" : value;
                    caret = buffer.length();
                }
            } else if (isFocused(id)) {
                focus = null;
                out.committed = true;
            }
        }

        boolean focused = isFocused(id);
        String shown = focused ? buffer : (value == null ? "" : value);
        chrome(x, y, w, h, focused, over);

        double inner = pad();
        pushClip(x + scaled(2), y + scaled(2), w - scaled(4), h - scaled(4));
        text(shown, x + inner, y + pad(), MeteorTheme.TEXT);
        if (focused && caretVisible) {
            String before = buffer.substring(0, Math.min(caret, buffer.length()));
            quad(x + inner + textWidth(before), y + pad(), scaled(1.75), textHeight(),
                MeteorTheme.TEXT);
        }
        popClip();

        out.text = shown;
        out.focused = focused;
        out.changed = focused && !shown.equals(value == null ? "" : value);
        if (out.committed) out.text = buffer == null ? shown : buffer;
        return out;
    }

    /**
     * Cyclic selector: the left quarter steps back, the right quarter steps
     * forward, the middle does nothing. Returns the new index or -1.
     */
    public int cycle(String id, double x, double y, double w, double h, String[] options, int current) {
        boolean over = inside(x, y, w, h);
        chrome(x, y, w, h, over && mouseDown, over);

        int result = -1;
        if (over && mousePressed) {
            if (mouseX < x + w / 3.0) result = Math.floorMod(current - 1, options.length);
            else if (mouseX > x + w * 2 / 3.0) result = Math.floorMod(current + 1, options.length);
        }

        String label = options.length == 0 ? "-" : options[Math.max(0, Math.min(options.length - 1, current))];
        text(label, x + w / 2.0 - textWidth(label) / 2.0, y + pad(), MeteorTheme.TEXT);
        if (over) {
            text("<", x + pad(), y + pad(), MeteorTheme.TEXT_SECONDARY);
            text(">", x + w - pad() - textWidth(">"), y + pad(), MeteorTheme.TEXT_SECONDARY);
        }
        return result;
    }

    /** Meteor slider (bar + circular handle). Returns the new value or NaN. */
    public double slider(String id, double x, double y, double w, double h, double value, double min, double max) {
        boolean over = inside(x, y, w, h);
        if (over) hot = id;

        double range = max > min ? max - min : 1;
        double frac = Math.max(0, Math.min(1, (value - min) / range));
        double handle = textHeight();
        double barH = Math.max(1, scaled(3));
        double valueWidth = frac * (w - handle);
        double barX = x + handle / 2.0;
        double barY = y + h / 2.0 - barH / 2.0;

        quad(barX, barY, valueWidth, barH, MeteorTheme.SLIDER_LEFT);
        quad(barX + valueWidth, barY, Math.max(0, w - valueWidth - handle), barH, MeteorTheme.SLIDER_RIGHT);

        float[] hc = MeteorTheme.f(MeteorTheme.state(over && mouseDown, over,
            MeteorTheme.SLIDER_HANDLE, MeteorTheme.SLIDER_HANDLE_HOVERED, MeteorTheme.SLIDER_HANDLE_PRESSED));
        gl.texture(Gl2D.CIRCLE);
        gl.quad(x + valueWidth, y + (h - handle) / 2.0, handle, handle, 0, 0, 1, 1,
            hc[0], hc[1], hc[2], hc[3]);
        gl.texture(Gl2D.WHITE);

        if (over && mousePressed) active = id;
        if (id.equals(active) && mouseDown) {
            double t = Math.max(0, Math.min(1, (mouseX - x - handle / 2.0) / Math.max(1, w - handle)));
            return min + t * range;
        }
        return Double.NaN;
    }

    /** Divider matching WMeteorHorizontalSeparator. */
    public void separator(double x, double y, double w) {
        double lineH = Math.max(1, scaled(1));
        float[] edges = MeteorTheme.f(MeteorTheme.SEPARATOR_EDGES);
        float[] center = MeteorTheme.f(MeteorTheme.SEPARATOR_CENTER);
        gl.quadGradient(x, y, w / 2.0, lineH, edges, center);
        gl.quadGradient(x + w / 2.0, y, w / 2.0, lineH, center, edges);
    }

    /** A scrollable region: draws nothing, returns the offset to apply. */
    public double scrollOffset(String id, double x, double y, double w, double h,
                               double contentHeight, double offset) {
        double max = Math.max(0, contentHeight - h);
        if (inside(x, y, w, h) && scroll != 0) offset -= scroll * scaled(24);
        offset = Math.max(0, Math.min(max, offset));

        if (max > 0) {
            double barH = Math.max(12, h * (h / Math.max(h, contentHeight)));
            double barY = y + (h - barH) * (offset / max);
            quad(x + w - 3, y, 3, h, MeteorTheme.BACKGROUND_PRESSED);
            quad(x + w - 3, barY, 3, barH, MeteorTheme.MODULE_BACKGROUND);
        }
        return offset;
    }
}
