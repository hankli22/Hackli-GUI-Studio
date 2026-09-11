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

package com.hackli.guidesigner.runtime;

import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.LayoutMode;

/**
 * Pure layout math shared by the designer canvas and the runtime renderer so
 * that what you see in the editor is exactly what players get.
 */
public class UiLayout {
    /** Inner padding of a container. */
    public static final double PAD = 8;

    /** Gap between widgets in a flow container. */
    public static final double GAP = 6;

    /**
     * Height reserved for a container's title bar. Matches Meteor's section
     * header (pad + text height + pad), so flow content starts below it.
     */
    public static final double TITLE_H = 21;

    private UiLayout() {}

    /** Rect of an absolutely placed widget inside a parent rect. */
    public static UiRect absoluteRect(UiNode node, UiRect parent) {
        double w = node.width > 0 ? node.width : node.type.defaultWidth;
        double h = node.height > 0 ? node.height : node.type.defaultHeight;

        double px = parent.x + node.anchorX.f * (parent.w - w) + node.x;
        double py = parent.y + node.anchorY.f * (parent.h - h) + node.y;

        return new UiRect(px, py, w, h);
    }

    /**
     * Recursively computes the screen-space rect of every node in the tree and
     * writes it to the transient render fields of each {@link UiNode}.
     */
    public static void layoutTree(UiNode node, double x, double y, double w, double h) {
        layoutTree(node, x, y, w, h, TITLE_H);
    }

    /**
     * Same as {@link #layoutTree(UiNode, double, double, double, double)} but
     * with an explicit header height, so a renderer running at another Meteor
     * GUI scale keeps its content aligned with the title bar it draws.
     */
    public static void layoutTree(UiNode node, double x, double y, double w, double h, double titleH) {
        node.renderX = x;
        node.renderY = y;
        node.renderW = w;
        node.renderH = h;

        if (node.layout == LayoutMode.ABSOLUTE) {
            UiRect parent = new UiRect(x, y, w, h);

            for (UiNode child : node.children) {
                UiRect r = absoluteRect(child, parent);
                layoutTree(child, r.x, r.y, r.w, r.h, titleH);
            }
        } else {
            double innerX = x + PAD;
            double innerW = Math.max(1, w - PAD * 2);
            double cy = y + (node.text.isEmpty() ? PAD : titleH);

            for (UiNode child : node.children) {
                double ch = child.height > 0 ? child.height : child.type.defaultHeight;

                layoutTree(child, innerX, cy, innerW, ch, titleH);
                cy += ch + GAP;
            }
        }
    }

    /**
     * Selects the widget under the pointer, honouring draw order: children are
     * painted in order, so the <em>last</em> child is on top and wins.
     *
     * <p>Children are tested even when the pointer is outside the parent's
     * rectangle, because nothing clips them: a widget that overflows its
     * container (or was dragged past its edge) is still visible and must stay
     * clickable. Only the widget that is finally returned has to contain the
     * point. Allocation free: it runs on every mouse move.</p>
     */
    public static UiNode hitTest(UiNode node, double px, double py) {
        if (!node.visible) return null;

        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode found = hitTest(node.children.get(i), px, py);
            if (found != null) return found;
        }

        boolean inside = px >= node.renderX && px <= node.renderX + node.renderW
            && py >= node.renderY && py <= node.renderY + node.renderH;
        return inside ? node : null;
    }
}
