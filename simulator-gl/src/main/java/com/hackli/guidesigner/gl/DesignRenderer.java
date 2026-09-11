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

import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.render.MeteorPainter;
import com.hackli.guidesigner.render.MeteorTheme;

/**
 * Thin OpenGL adapter around the shared {@link MeteorPainter}. The actual
 * Meteor drawing rules live in {@code core}, so the OpenGL editor and the
 * desktop editor always show the same thing.
 */
public class DesignRenderer {
    private final MeteorTheme theme;
    private final MeteorPainter painter;
    private final GlMeteorCanvas canvas;

    private UiDocument doc;
    private UiNode lastRoot;

    public DesignRenderer(Gl2D gl, FontAtlas font, MeteorTheme theme) {
        this.theme = theme;
        this.painter = new MeteorPainter(theme);
        this.canvas = new GlMeteorCanvas(gl, font);
    }

    public MeteorTheme theme() {
        return theme;
    }

    public FontAtlas font() {
        return canvas.font();
    }

    /**
     * The Meteor canvas this renderer draws through. Text measurement works
     * without a live GL context, so the editor uses it for hit testing too.
     */
    public GlMeteorCanvas canvas() {
        return canvas;
    }

    /** Geometry of a NUMBER widget's parts, exactly as painted. */
    public MeteorPainter.NumberParts numberParts(UiNode node) {
        return painter.numberParts(canvas, node);
    }

    public UiNode hovered() {
        return painter.hovered();
    }

    public UiNode pressed() {
        return painter.pressed();
    }

    public UiNode focused() {
        return painter.focused();
    }

    public UiNode openDropdown() {
        return painter.openDropdown();
    }

    public void setHovered(UiNode node) {
        painter.setInteraction(node, painter.pressed(), painter.focused(), painter.openDropdown());
    }

    public void setPressed(UiNode node) {
        painter.setInteraction(painter.hovered(), node, painter.focused(), painter.openDropdown());
    }

    public void setFocused(UiNode node) {
        painter.setInteraction(painter.hovered(), painter.pressed(), node, painter.openDropdown());
    }

    public void setOpenDropdown(UiNode node) {
        painter.setInteraction(painter.hovered(), painter.pressed(), painter.focused(), node);
    }

    public void setDocument(UiDocument doc) {
        this.doc = doc;
    }

    /** Advances the check box and caret animations. */
    public void tick(double dt) {
        UiNode root = lastRoot != null ? lastRoot : (doc == null ? null : doc.root);
        painter.tick(dt, root);
    }

    /** Lays out and draws the document, returning the laid out root. */
    public UiNode render(UiNode root, double originX, double originY) {
        lastRoot = painter.paint(canvas, root, originX, originY);
        return lastRoot;
    }

    /** Pointer in document space, so tooltips follow it. */
    public void setPointer(double x, double y) {
        painter.setPointer(x, y);
    }

    /** The hover text currently shown, or null. */
    public String activeTooltip() {
        return painter.activeTooltip();
    }
}
