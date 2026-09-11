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

import com.hackli.guidesigner.model.ContainerStyle;
import com.hackli.guidesigner.model.KeyNames;
import com.hackli.guidesigner.model.TextAlign;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.UiType;

/**
 * Draws a design tree exactly the way Meteor Client draws its click GUI.
 * Backend independent: hand it a {@link MeteorCanvas} and the same picture
 * comes out of OpenGL and Java2D.
 *
 * <p>Rules taken from the compiled Meteor Client:</p>
 * <ul>
 *   <li>{@code MeteorWidget.renderBackground}: background inset by
 *       {@code scale(2)} inside a {@code scale(2)} outline frame</li>
 *   <li>{@code WMeteorWindow}: flat {@code backgroundColor} body with an accent
 *       coloured header of {@code pad + textHeight + pad}</li>
 *   <li>{@code WMeteorButton}: state background, text centred on x at
 *       {@code y + pad}</li>
 *   <li>{@code WMeteorCheckbox}: the widget rect is the background, the accent
 *       square grows to {@code (min(w,h) - scale(2)) / 1.75} while checked</li>
 *   <li>{@code WMeteorSlider}: a {@code scale(3)} bar inset by half the handle,
 *       with a circular handle of {@code textHeight()}</li>
 *   <li>{@code WMeteorTextBox}: outline + background, text at {@code pad}, a
 *       blinking 1.75px cursor while focused</li>
 *   <li>{@code WMeteorHorizontalSeparator}: {@code scale(1)} gradient line</li>
 * </ul>
 */
public class MeteorPainter {
    private final MeteorTheme theme;

    /**
     * The images {@link UiType#TEXTURE} nodes draw.
     *
     * <p>This is process-wide on purpose: an editor is a singleton process, the
     * lookup is a read-only cache, and every {@code MeteorPainter} instance
     * (canvas, simulator, GL editor, exporter preview) must agree on it without
     * threading it through each constructor.</p>
     */
    private static volatile TextureSource sharedTextureSource = TextureSource.NONE;

    private TextureSource textureSource = sharedTextureSource;

    private UiNode hovered;
    private UiNode pressed;
    private UiNode focused;
    private UiNode openDropdown;

    private double cursorTimer;
    private boolean cursorVisible = true;

    /** Pointer position in document space, for tooltip placement. */
    private double pointerX, pointerY;

    /** Tooltip fade, Meteor's {@code tooltipAnimProgress}. */
    private double tooltipAnim;
    private String activeTooltip;
    private UiNode tooltipNode;

    public MeteorPainter(MeteorTheme theme) {
        this.theme = theme;
    }

    /**
     * Supplies the images textures draw. Set once at start-up; without one every
     * texture renders as a placeholder, which keeps headless tests and
     * asset-less runs working.
     */
    public static void setTextureSource(TextureSource source) {
        sharedTextureSource = source == null ? TextureSource.NONE : source;
    }

    public static TextureSource textureSource() {
        return sharedTextureSource;
    }

    /** Per-instance override, for tests that need a specific source. */
    public void setLocalTextureSource(TextureSource source) {
        this.textureSource = source == null ? TextureSource.NONE : source;
    }

    public MeteorTheme theme() {
        return theme;
    }

    /** Feeds the interaction state the widgets react to. */
    public void setInteraction(UiNode hovered, UiNode pressed, UiNode focused, UiNode openDropdown) {
        this.hovered = hovered;
        this.pressed = pressed;
        this.focused = focused;
        this.openDropdown = openDropdown;
    }

    public UiNode hovered() {
        return hovered;
    }

    public UiNode pressed() {
        return pressed;
    }

    public UiNode focused() {
        return focused;
    }

    public UiNode openDropdown() {
        return openDropdown;
    }

    public boolean cursorVisible() {
        return cursorVisible;
    }

    /** Advances the animations Meteor runs on check boxes and text cursors. */
    public void tick(double dt, UiNode root) {
        tickCheckboxes(root, dt);

        cursorTimer += dt;
        if (cursorTimer >= 1.75) {
            cursorTimer = 0;
            cursorVisible = !cursorVisible;
        }

        // Meteor fades the tooltip in and out at 14 per second.
        double target = activeTooltip == null ? 0 : 1;
        if (tooltipAnim < target) tooltipAnim = Math.min(target, tooltipAnim + dt * 14);
        else if (tooltipAnim > target) tooltipAnim = Math.max(target, tooltipAnim - dt * 14);
    }

    /** Pointer position in document space; the tooltip follows it. */
    public void setPointer(double x, double y) {
        pointerX = x;
        pointerY = y;
    }

    /** The tooltip currently shown, or {@code null}. */
    public String activeTooltip() {
        return activeTooltip;
    }

    /** Tooltip fade progress, 0..1 (exposed for tests and tooling). */
    public double tooltipFade() {
        return tooltipAnim;
    }

    private void tickCheckboxes(UiNode node, double dt) {
        if (node == null) return;
        if (node.type == UiType.CHECKBOX || (node.type == UiType.CONTAINER
            && node.style == ContainerStyle.SECTION)) {
            if (!node.animInit) {
                node.animProgress = (node.type == UiType.CHECKBOX ? node.checked : !node.collapsed) ? 1 : 0;
                node.animInit = true;
            }
            boolean target = node.type == UiType.CHECKBOX ? node.checked : !node.collapsed;
            node.animProgress = Math.max(0, Math.min(1,
                node.animProgress + (target ? 1 : -1) * dt * 14));
        }
        for (UiNode child : node.children) tickCheckboxes(child, dt);
    }

    /** Lays out the tree and paints it; returns the laid out root. */
    public UiNode paint(MeteorCanvas canvas, UiNode root, double originX, double originY) {
        double rootW = root.width > 0 ? root.width : root.type.defaultWidth;
        double rootH = root.height > 0 ? root.height : root.type.defaultHeight;
        MeteorLayout.layoutTree(root, originX, originY, rootW, rootH, theme);
        paintNode(canvas, root);

        // Tooltips go on top of everything, like Meteor's deferred tooltip pass.
        resolveTooltip(root);
        paintTooltip(canvas, originX, originY, rootW, rootH);
        return root;
    }

    /**
     * Finds the tooltip to show: the text of the hovered widget, or of its nearest
     * ancestor that has one (so a tooltip on a section covers its children, which
     * is what Meteor's inherited {@code tooltip} field does).
     */
    private void resolveTooltip(UiNode root) {
        activeTooltip = null;
        tooltipNode = null;
        if (hovered == null) return;

        for (UiNode node = hovered; node != null; node = node.parent) {
            if (node.tooltip != null && !node.tooltip.isEmpty()) {
                activeTooltip = node.tooltip;
                tooltipNode = node;
                return;
            }
        }
    }

    /**
     * Draws the tooltip 12px below and right of the pointer, pulled back inside
     * the canvas when it would overflow - Meteor's {@code renderTooltip}.
     */
    private void paintTooltip(MeteorCanvas canvas, double originX, double originY,
                              double rootW, double rootH) {
        if (activeTooltip == null || activeTooltip.isEmpty() || tooltipAnim <= 0.01) return;

        double pad = theme.scaled(4);
        double w = canvas.textWidth(activeTooltip, theme.scale) + pad * 2;
        double h = theme.textHeight() + pad * 2;

        double x = pointerX + 12;
        double y = pointerY + 12;
        double right = originX + rootW;
        double bottom = originY + rootH;
        if (x + w > right) x = Math.max(originX, right - w);
        if (y + h > bottom) y = Math.max(originY, bottom - h);

        int bg = MeteorTheme.argb(MeteorTheme.BACKGROUND);
        int text = MeteorTheme.argb(MeteorTheme.TEXT);
        if (tooltipAnim < 1) {
            bg = fade(bg, tooltipAnim);
            text = fade(text, tooltipAnim);
        }

        canvas.quad(x, y, w, h, bg);
        canvas.text(activeTooltip, x + pad, y + pad, text, theme.scale);
    }

    /** Scales the alpha channel of a packed ARGB colour. */
    private static int fade(int argb, double factor) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Paints an already laid out node (and its children). */
    public void paintNode(MeteorCanvas canvas, UiNode n) {
        if (!n.visible) return;

        double x = n.renderX, y = n.renderY;
        double w = Math.max(1, n.renderW), h = Math.max(1, n.renderH);
        boolean isHovered = n == hovered;
        boolean isPressed = n == pressed;

        switch (n.type) {
            case CONTAINER -> {
                double header = theme.headerHeight();
                if (n.style == ContainerStyle.SECTION) {
                    // WMeteorSection: a separator with the title in the gap plus a
                    // collapse triangle, and no background of its own.
                    double triangle = theme.textHeight();
                    double lineW = Math.max(0, w - triangle - theme.scaled(3));
                    double lineH = Math.max(1, theme.scaled(1));
                    double ly = y + theme.pad();
                    int edges = MeteorTheme.argb(MeteorTheme.SEPARATOR_EDGES);
                    int center = MeteorTheme.argb(MeteorTheme.SEPARATOR_CENTER);

                    if (n.text.isEmpty()) {
                        canvas.quadGradientH(x, ly, lineW / 2.0, lineH, edges, center);
                        canvas.quadGradientH(x + lineW / 2.0, ly, lineW / 2.0, lineH, center, edges);
                    } else {
                        double tw = canvas.textWidth(n.text, theme.scale);
                        double gap = theme.scaled(2);
                        double leftW = Math.max(0, Math.round(lineW / 2.0 - tw / 2.0 - gap));
                        double rightX = gap + leftW + tw + gap;
                        canvas.quadGradientH(x, ly, leftW, lineH, edges, center);
                        canvas.quadGradientH(x + rightX, ly, Math.max(0, lineW - rightX), lineH, center, edges);
                        canvas.text(n.text, x + leftW + gap, ly - theme.textHeight() / 2.0,
                            MeteorTheme.argb(MeteorTheme.SEPARATOR_TEXT), theme.scale);
                    }

                    // WTriangle.rotation = -90 * animProgress: down when open.
                    double progress = n.animInit ? n.animProgress : (n.collapsed ? 0 : 1);
                    double tx = x + w - triangle;
                    double ty = y + (header - triangle) / 2.0;
                    canvas.triangle(tx, ty, triangle * 0.55, triangle,
                        MeteorTheme.argb(MeteorTheme.TEXT), -90 * progress);

                    if (n.collapsed) return;
                } else if (!n.text.isEmpty()) {
                    // WMeteorWindow: flat body, accent header
                    canvas.quad(x, y + header, w, h - header, MeteorTheme.argb(MeteorTheme.BACKGROUND));
                    canvas.quad(x, y, w, header, MeteorTheme.argb(MeteorTheme.ACCENT));
                    canvas.text(n.text, x + theme.pad(), y + theme.pad(),
                        MeteorTheme.argb(MeteorTheme.TITLE_TEXT), theme.scale * theme.titleTextScale);
                } else {
                    canvas.quad(x, y, w, h, MeteorTheme.argb(MeteorTheme.BACKGROUND));
                }
            }
            case LABEL -> {
                int[] color = n.textColor.isEmpty() ? MeteorTheme.TEXT : MeteorTheme.parse(n.textColor, MeteorTheme.TEXT);
                String value = n.text.isEmpty() ? n.id : n.text;
                double tx = x;
                if (n.textAlign == TextAlign.CENTER) tx = x + w / 2.0 - canvas.textWidth(value, theme.scale) / 2.0;
                else if (n.textAlign == TextAlign.RIGHT) tx = x + w - canvas.textWidth(value, theme.scale);
                canvas.text(value, tx, y, MeteorTheme.argb(color), theme.scale);
            }
            case BUTTON -> {
                background(canvas, x, y, w, h, isPressed, isHovered);
                String value = n.text.isEmpty() ? n.id : n.text;
                canvas.text(value, x + w / 2.0 - canvas.textWidth(value, theme.scale) / 2.0, y + theme.pad(),
                    MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
            }
            case TEXTBOX -> {
                background(canvas, x, y, w, h, isPressed, isHovered);
                double s = theme.scaled(2);
                boolean empty = n.text.isEmpty();
                String value = empty ? n.placeholder : n.text;
                int[] color = empty ? MeteorTheme.PLACEHOLDER : MeteorTheme.TEXT;

                canvas.pushClip(x + s, y + s, w - s * 2, h - s * 2);
                canvas.text(value, x + theme.pad(), y + theme.pad(), MeteorTheme.argb(color), theme.scale);
                if (n == focused && cursorVisible) {
                    double caretX = x + theme.pad() + canvas.textWidth(value, theme.scale);
                    canvas.quad(caretX, y + theme.pad(), theme.scaled(1.75), theme.textHeight(),
                        MeteorTheme.argb(MeteorTheme.TEXT));
                }
                canvas.popClip();
            }
            case CHECKBOX -> {
                double boxSize = Math.min(w, Math.min(h, theme.pad() * 2 + theme.textHeight()));
                double bx = x, by = y + (h - boxSize) / 2.0;

                background(canvas, bx, by, boxSize, boxSize, isPressed, isHovered);
                double progress = n.animInit ? n.animProgress : (n.checked ? 1 : 0);
                if (progress > 0) {
                    double size = ((boxSize - theme.scaled(2)) / 1.75) * progress;
                    canvas.quad(bx + (boxSize - size) / 2.0, by + (boxSize - size) / 2.0, size, size,
                        MeteorTheme.argb(MeteorTheme.CHECKBOX));
                }
                if (!n.text.isEmpty()) {
                    canvas.text(n.text, bx + boxSize + theme.pad(), y + theme.pad(),
                        MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
                } else if (!n.id.isEmpty()) {
                    canvas.text(n.id, bx + boxSize + theme.pad(), y + theme.pad(),
                        MeteorTheme.argb(MeteorTheme.TEXT_SECONDARY), theme.scale);
                }
            }
            case ITEM, ENTITY -> {
                // WItem: a 16x16 sprite drawn at theme.scale(2) inside a
                // theme.scale(32) box, with the stack count overlaid.
                // Entities reuse it with their spawn egg, which is exactly how
                // Meteor's entity list shows an entity type.
                String reference = n.type == UiType.ENTITY ? spawnEgg(n.entityId) : n.itemId;
                paintItemIcon(canvas, reference, x, y, n, isHovered);
            }
            case SELECT -> {
                // GuiTheme.selectW: a "Select" button plus "(N selected)" in
                // secondary text, as one widget so the row stays simple.
                String label = selectLabel(n);
                background(canvas, x, y, w, h, isPressed, isHovered);
                canvas.text(label, x + w / 2.0 - canvas.textWidth(label, theme.scale) / 2.0, y + theme.pad(),
                    MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
            }
            case SLIDER -> {
                double handle = theme.handleSize();
                double barH = theme.scaled(3);
                double range = n.max > n.min ? n.max - n.min : 1;
                double frac = Math.max(0, Math.min(1, (n.value - n.min) / range));
                double valueWidth = frac * (w - handle);
                double barX = x + handle / 2.0;
                double barY = y + h / 2.0 - barH / 2.0;

                canvas.quad(barX, barY, valueWidth, barH, MeteorTheme.argb(MeteorTheme.SLIDER_LEFT));
                canvas.quad(barX + valueWidth, barY, Math.max(0, w - valueWidth - handle), barH,
                    MeteorTheme.argb(MeteorTheme.SLIDER_RIGHT));

                int[] handleColor = MeteorTheme.state(isPressed || n == focused, isHovered,
                    MeteorTheme.SLIDER_HANDLE, MeteorTheme.SLIDER_HANDLE_HOVERED, MeteorTheme.SLIDER_HANDLE_PRESSED);
                canvas.circle(x + valueWidth, y + (h - handle) / 2.0, handle, MeteorTheme.argb(handleColor));
            }
            case DROPDOWN -> {
                // WMeteorDropdown$WValue: flat background (no outline), centred text
                canvas.quad(x, y, w, h, MeteorTheme.argb(MeteorTheme.tri(isPressed, isHovered, MeteorTheme.BACKGROUND)));
                String value = n.selected == null || n.selected.isEmpty() ? "-" : n.selected;
                canvas.text(value, x + w / 2.0 - canvas.textWidth(value, theme.scale) / 2.0, y + theme.pad(),
                    MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);

                if (n == openDropdown && !n.options.isEmpty()) {
                    double rowH = Math.max(theme.pad() * 2 + theme.textHeight(), theme.textHeight() + 4);
                    double ly = y + h;
                    canvas.quad(x, ly, w, rowH * n.options.size(), MeteorTheme.argb(MeteorTheme.BACKGROUND));
                    for (int i = 0; i < n.options.size(); i++) {
                        String option = n.options.get(i);
                        double ry = ly + i * rowH;
                        if (option.equals(value)) {
                            canvas.quad(x, ry, w, rowH, MeteorTheme.argb(MeteorTheme.BACKGROUND_HOVERED));
                        }
                        canvas.text(option, x + theme.pad(), ry + theme.pad(), MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
                    }
                }
            }
            case NUMBER -> {
                // WIntEdit / WDoubleEdit are horizontal lists:
                //   textBox(value).minWidth(75) + [-] + [+] + slider().expandX()
                NumberParts p = numberParts(canvas, n);

                background(canvas, p.boxX(), p.rowY(), p.boxW(), p.rowH(), false, n == focused);
                String value = displayValue(n);
                canvas.text(value, p.boxX() + theme.pad(), p.rowY() + theme.pad(),
                    MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
                if (n == focused && cursorVisible) {
                    double caretX = p.boxX() + theme.pad() + canvas.textWidth(value, theme.scale);
                    canvas.quad(caretX, p.rowY() + theme.pad(), theme.scaled(1.75), theme.textHeight(),
                        MeteorTheme.argb(MeteorTheme.TEXT));
                }

                if (n.showButtons) {
                    numberButton(canvas, p.minusX(), p.rowY(), p.minusW(), p.rowH(),
                        "-", MeteorTheme.MINUS, isPressed, isHovered);
                    numberButton(canvas, p.plusX(), p.rowY(), p.plusW(), p.rowH(),
                        "+", MeteorTheme.PLUS, isPressed, isHovered);
                }

                if (n.showSlider) {
                    double sliderW = p.sliderW();
                    double handle = theme.handleSize();
                    double barH = theme.scaled(3);
                    double frac = numberFraction(n);
                    double valueWidth = frac * Math.max(0, sliderW - handle);
                    double barX = p.sliderX() + handle / 2.0;
                    double barY = p.rowY() + p.rowH() / 2.0 - barH / 2.0;

                    canvas.quad(barX, barY, valueWidth, barH, MeteorTheme.argb(MeteorTheme.SLIDER_LEFT));
                    canvas.quad(barX + valueWidth, barY, Math.max(0, sliderW - valueWidth - handle), barH,
                        MeteorTheme.argb(MeteorTheme.SLIDER_RIGHT));
                    int[] handleColor = MeteorTheme.state(isPressed, isHovered,
                        MeteorTheme.SLIDER_HANDLE, MeteorTheme.SLIDER_HANDLE_HOVERED, MeteorTheme.SLIDER_HANDLE_PRESSED);
                    canvas.circle(p.sliderX() + valueWidth, p.rowY() + (p.rowH() - handle) / 2.0, handle,
                        MeteorTheme.argb(handleColor));
                }
            }
            case KEYBIND -> {
                // WKeybind is a WButton whose label is the binding; while it waits
                // for input it shows "..." exactly like Meteor does.
                background(canvas, x, y, w, h, isPressed, isHovered);
                String value = n.listening
                    ? "..."
                    : KeyNames.label(n.key, n.modifiers, n.keyIsKey);
                canvas.text(value, x + w / 2.0 - canvas.textWidth(value, theme.scale) / 2.0, y + theme.pad(),
                    MeteorTheme.argb(n.listening ? MeteorTheme.TEXT_SECONDARY : MeteorTheme.TEXT), theme.scale);
            }
            case TEXTURE -> {
                // WTexture: a raw image, scale(32) by default, optionally rotated.
                // Unknown references draw a deterministic placeholder so a design
                // stays readable without any Minecraft assets.
                Object handle = textureSource.resolve(n.texture);
                if (handle != null) {
                    canvas.texture(handle, x, y, w, h, n.rotation);
                } else {
                    int placeholder = MeteorTheme.argb(MeteorTheme.TEXTURE_MISSING);
                    canvas.quad(x, y, w, h, placeholder);
                    canvas.quad(x, y, w, Math.max(1, theme.scaled(1)), MeteorTheme.argb(MeteorTheme.OUTLINE));
                    String label = n.text.isEmpty() ? missingLabel(n.texture) : n.text;
                    if (!label.isEmpty() && canvas.textWidth(label, theme.scale) <= w - theme.pad()) {
                        canvas.text(label, x + w / 2.0 - canvas.textWidth(label, theme.scale) / 2.0,
                            y + (h - theme.textHeight()) / 2.0, MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
                    }
                }
            }
            case SEPARATOR -> {
                // WMeteorHorizontalSeparator: scale(1) line, edges -> center gradient
                double lineH = Math.max(1, theme.scaled(1));
                double ly = y + Math.round(h / 2.0);
                int edges = MeteorTheme.argb(MeteorTheme.SEPARATOR_EDGES);
                int center = MeteorTheme.argb(MeteorTheme.SEPARATOR_CENTER);

                if (n.text.isEmpty()) {
                    canvas.quadGradientH(x, ly, w / 2.0, lineH, edges, center);
                    canvas.quadGradientH(x + w / 2.0, ly, w / 2.0, lineH, center, edges);
                } else {
                    double tw = canvas.textWidth(n.text, theme.scale);
                    double gap = theme.scaled(2);
                    double leftW = Math.max(0, Math.round(w / 2.0 - tw / 2.0 - gap));
                    double rightX = gap + leftW + tw + gap;
                    canvas.quadGradientH(x, ly, leftW, lineH, edges, center);
                    canvas.quadGradientH(x + rightX, ly, Math.max(0, w - rightX), lineH, center, edges);
                    canvas.text(n.text, x + leftW + gap, ly - theme.textHeight() / 2.0,
                        MeteorTheme.argb(MeteorTheme.SEPARATOR_TEXT), theme.scale);
                }
            }
        }

        if (n.style == ContainerStyle.VIEW) {
            // WMeteorView: content is clipped to the viewport and gets a scrollbar.
            canvas.pushClip(x, y, w, h);
            for (UiNode child : n.children) paintNode(canvas, child);
            canvas.popClip();
            paintScrollbar(canvas, n, x, y, w, h);
            return;
        }

        for (UiNode child : n.children) paintNode(canvas, child);
    }

    /** Meteor's list spacing, used when composing multi-part widgets. */
    public static final double SPACING_GAP = 3;

    /**
     * Screen-space geometry of a NUMBER widget's four parts, computed from the
     * node's laid out rect. The painters draw from this and the editors hit test
     * against it, so a click always lands on what was drawn.
     */
    public record NumberParts(double rowY, double rowH,
                              double boxX, double boxW,
                              double minusX, double minusW,
                              double plusX, double plusW,
                              double sliderX, double sliderW) {

        public boolean inBox(double px, double py) {
            return inside(boxX, rowY, boxW, rowH, px, py);
        }

        public boolean inMinus(double px, double py) {
            return inside(minusX, rowY, minusW, rowH, px, py);
        }

        public boolean inPlus(double px, double py) {
            return inside(plusX, rowY, plusW, rowH, px, py);
        }

        public boolean inSlider(double px, double py) {
            return inside(sliderX, rowY, sliderW, rowH, px, py);
        }

        /** Pointer position -> 0..1 along the slider track. */
        public double fraction(double px, double handle) {
            double span = Math.max(1, sliderW - handle);
            return Math.max(0, Math.min(1, (px - sliderX - handle / 2.0) / span));
        }

        private static boolean inside(double x, double y, double w, double h, double px, double py) {
            return w > 0 && px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    /** Lays out the parts of a NUMBER widget inside its rendered rect. */
    public NumberParts numberParts(MeteorCanvas canvas, UiNode n) {
        double w = Math.max(1, n.renderW);
        double h = Math.max(1, n.renderH);
        double x = n.renderX;
        double y = n.renderY;

        double rowH = theme.pad() * 2 + theme.textHeight();
        double rowY = y + Math.max(0, (h - rowH) / 2.0);
        double cursor = x;

        double boxW = Math.max(theme.scaled(75),
            canvas.textWidth(displayValue(n), theme.scale) + theme.pad() * 2);
        cursor += boxW + theme.scaled(SPACING_GAP);

        double minusX = 0, minusW = 0, plusX = 0, plusW = 0;
        if (n.showButtons) {
            minusW = numberButtonWidth(canvas, "-");
            minusX = cursor;
            cursor += minusW + theme.scaled(SPACING_GAP);
            plusW = numberButtonWidth(canvas, "+");
            plusX = cursor;
            cursor += plusW + theme.scaled(SPACING_GAP);
        }

        double sliderX = 0, sliderW = 0;
        if (n.showSlider) {
            sliderX = cursor;
            sliderW = Math.max(0, x + w - cursor);
        }
        return new NumberParts(rowY, rowH, x, boxW, minusX, minusW, plusX, plusW, sliderX, sliderW);
    }

    /** Width of one of the number widget's -/+ buttons. */
    public double numberButtonWidth(MeteorCanvas canvas, String label) {
        return canvas.textWidth(label, theme.scale) + theme.pad() * 2;
    }

    /** Where the number widget's value sits between {@code min} and {@code max}. */
    public static double numberFraction(UiNode n) {
        double range = n.max > n.min ? n.max - n.min : 1;
        return Math.max(0, Math.min(1, (n.value - n.min) / range));
    }

    /**
     * Pointer x -> value, exactly like dragging the slider part. Integers snap
     * to whole numbers, decimals to 0.1 (Meteor's WDoubleEdit precision).
     */
    public static double numberValueAt(UiNode n, double fraction) {
        double range = n.max > n.min ? n.max - n.min : 1;
        double raw = n.min + Math.max(0, Math.min(1, fraction)) * range;
        return n.integer ? Math.rint(raw) : Math.round(raw * 10.0) / 10.0;
    }

    /** Applies one -/+ button click, clamped to the node's range. */
    public static double stepNumber(UiNode n, double delta) {
        double step = n.step != 0 ? n.step : 1;
        double raw = n.value + delta * step;
        if (n.integer) raw = Math.rint(raw);
        else raw = Math.round(raw * 1000.0) / 1000.0;
        double min = Math.min(n.min, n.max);
        double max = Math.max(n.min, n.max);
        return Math.max(min, Math.min(max, raw));
    }

    /** Text shown in a number widget's box: the live edit buffer while typing. */
    public static String displayValue(UiNode n) {
        return n.editText != null ? n.editText : formatNumber(n);
    }

    /**
     * Draws an item sprite the way {@code WItem} does: a 16x16 texture scaled
     * {@code theme.scale(2)} into the top-left of a {@code theme.scale(32)} box,
     * with the stack count in the bottom-right corner when it is above 1.
     *
     * <p>{@code air} draws nothing at all, which is what {@code WItem} does for an
     * empty stack. A known item with a missing texture gets the placeholder so the
     * design stays readable.</p>
     */
    private void paintItemIcon(MeteorCanvas canvas, String reference, double x, double y,
                               UiNode n, boolean isHovered) {
        if (reference == null || reference.isEmpty() || reference.equalsIgnoreCase("minecraft:air")
            || reference.equalsIgnoreCase("air")) {
            return;
        }

        double box = Math.min(n.renderW, n.renderH);
        Object handle = textureSource.resolve(reference);
        if (handle != null) {
            double side = Math.min(box, theme.scaled(16 * 2) * (box / theme.scaled(32)));
            canvas.texture(handle, x, y, side, side, 0);
        } else {
            paintPlaceholder(canvas, reference, n, x, y, box);
        }

        if (n.itemCount > 1 && box >= theme.scaled(16)) {
            String count = String.valueOf(Math.min(64, n.itemCount));
            double tw = canvas.textWidth(count, theme.scale);
            canvas.text(count, x + box - tw - theme.scaled(1), y + box - theme.textHeight(),
                MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
        }
    }

    /** The "no texture" look: a darker box with the reference's short name. */
    private void paintPlaceholder(MeteorCanvas canvas, String reference, UiNode n,
                                  double x, double y, double box) {
        canvas.quad(x, y, box, box, MeteorTheme.argb(MeteorTheme.TEXTURE_MISSING));
        String label = n.text.isEmpty() ? missingLabel(reference) : n.text;
        if (label.isEmpty()) return;
        if (canvas.textWidth(label, theme.scale) <= box - theme.pad()) {
            canvas.text(label, x + box / 2.0 - canvas.textWidth(label, theme.scale) / 2.0,
                y + (box - theme.textHeight()) / 2.0, MeteorTheme.argb(MeteorTheme.TEXT), theme.scale);
        }
    }

    /** The spawn egg item for an entity type, or null when it has none. */
    private static String spawnEgg(String entityId) {
        return com.hackli.guidesigner.assets.AssetStore.get().spawnEggOf(entityId);
    }

    /** Label of a {@link UiType#SELECT} node: "Select" and optionally the count. */
    public static String selectLabel(UiNode n) {
        String base = n.text.isEmpty() ? "Select" : n.text;
        if (n.itemCountSelected < 0) return base;
        return base + " (" + n.itemCountSelected + " selected)";
    }

    /** Short label for a texture that could not be loaded. */
    public static String missingLabel(String reference) {        if (reference == null || reference.isEmpty()) return "?";
        String value = reference;
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        if (value.endsWith(".png")) value = value.substring(0, value.length() - 4);
        return value.isEmpty() ? "?" : value;
    }

    /** Value shown in a number widget's text box. */
    public static String formatNumber(UiNode n) {
        if (n.integer) return String.valueOf((long) Math.rint(n.value));
        String s = String.format(java.util.Locale.ROOT, "%.3f", n.value);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** Draws one of the number widget's -/+ buttons. */
    private void numberButton(MeteorCanvas canvas, double x, double y, double w, double h,
                              String label, int[] iconColor, boolean isPressed, boolean isHovered) {
        if (w <= 0) return;
        background(canvas, x, y, w, h, isPressed, isHovered);
        canvas.text(label, x + w / 2.0 - canvas.textWidth(label, theme.scale) / 2.0, y + theme.pad(),
            MeteorTheme.argb(iconColor), theme.scale);
    }

    /** WMeteorView + WMeteorView.onRender: handle width scale(6), scrollbarColor. */
    private void paintScrollbar(MeteorCanvas canvas, UiNode n, double x, double y, double w, double h) {
        double contentH = Math.max(h, n.scrollHeight);
        if (contentH <= h + 0.5) return;

        double handleW = theme.scaled(6);
        double handleH = Math.max(theme.scaled(6), h * h / contentH);
        double maxScroll = contentH - h;
        double handleY = y + (maxScroll <= 0 ? 0 : (n.scroll / maxScroll) * (h - handleH));

        int[] color = MeteorTheme.state(n == pressed, n == hovered,
            MeteorTheme.SCROLLBAR, MeteorTheme.SCROLLBAR_HOVERED, MeteorTheme.SCROLLBAR_PRESSED);
        canvas.quad(x + w - handleW, handleY, handleW, handleH, MeteorTheme.argb(color));
    }

    /** MeteorWidget.renderBackground: inset background + scale(2) outline frame. */
    public void background(MeteorCanvas canvas, double x, double y, double w, double h,
                           boolean isPressed, boolean isHovered) {
        double s = theme.scaled(2);
        int bg = MeteorTheme.argb(MeteorTheme.tri(isPressed, isHovered, MeteorTheme.BACKGROUND));
        int ol = MeteorTheme.argb(MeteorTheme.tri(isPressed, isHovered, MeteorTheme.OUTLINE));

        canvas.quad(x + s, y + s, w - s * 2, h - s * 2, bg);
        canvas.quad(x, y, w, s, ol);
        canvas.quad(x, y + h - s, w, s, ol);
        canvas.quad(x + s, y + s, s, h - s * 2, ol);
        canvas.quad(x + w - s, y + s, s, h - s * 2, ol);
    }
}
