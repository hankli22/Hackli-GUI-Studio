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

import com.hackli.guidesigner.model.AnchorX;
import com.hackli.guidesigner.model.AnchorY;
import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.UiNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alignment & distribution operations for (multi-)selections.
 *
 * <p>Alignment is computed per parent container and only applies to children
 * of ABSOLUTE containers. Nodes using non-default anchors are normalized to
 * LEFT/TOP (keeping their absolute position) first, so the results are
 * predictable.</p>
 */
public class AlignTools {
    public enum Align {
        LEFT("Align Left"),
        CENTER_H("Center H"),
        RIGHT("Align Right"),
        TOP("Align Top"),
        MIDDLE_V("Middle V"),
        BOTTOM("Align Bottom"),
        SPREAD_H("Distribute H"),
        SPREAD_V("Distribute V");

        public final String label;

        Align(String label) {
            this.label = label;
        }
    }

    private AlignTools() {}

    /**
     * Aligns/distributes the given nodes.
     *
     * @return true when anything changed (caller should mark dirty + record undo).
     */
    public static boolean apply(List<UiNode> nodes, Align align) {
        if (nodes == null || nodes.size() < 2) return false;

        // Group by parent
        Map<UiNode, List<UiNode>> byParent = new LinkedHashMap<>();
        for (UiNode node : nodes) {
            if (node.parent == null) continue;
            byParent.computeIfAbsent(node.parent, p -> new ArrayList<>()).add(node);
        }

        boolean changed = false;
        for (Map.Entry<UiNode, List<UiNode>> entry : byParent.entrySet()) {
            UiNode parent = entry.getKey();
            if (parent.layout != LayoutMode.ABSOLUTE) continue;
            changed |= applyGroup(parent, entry.getValue(), align);
        }
        return changed;
    }

    private static boolean applyGroup(UiNode parent, List<UiNode> group, Align align) {
        double pw = parent.width > 0 ? parent.width : parent.type.defaultWidth;
        double ph = parent.height > 0 ? parent.height : parent.type.defaultHeight;

        // Normalize anchors to LEFT/TOP, keeping absolute positions
        for (UiNode n : group) {
            if (n.anchorX != AnchorX.LEFT) {
                n.x = n.anchorX.f * (pw - n.width) + n.x;
                n.anchorX = AnchorX.LEFT;
            }
            if (n.anchorY != AnchorY.TOP) {
                n.y = n.anchorY.f * (ph - n.height) + n.y;
                n.anchorY = AnchorY.TOP;
            }
        }

        double left = Double.MAX_VALUE, right = -Double.MAX_VALUE;
        double top = Double.MAX_VALUE, bottom = -Double.MAX_VALUE;

        for (UiNode n : group) {
            left = Math.min(left, n.x);
            right = Math.max(right, n.x + Math.max(1, n.width));
            top = Math.min(top, n.y);
            bottom = Math.max(bottom, n.y + Math.max(1, n.height));
        }

        switch (align) {
            case LEFT -> {
                for (UiNode n : group) n.x = left;
            }
            case CENTER_H -> {
                double center = (left + right) / 2;
                for (UiNode n : group) n.x = center - Math.max(1, n.width) / 2;
            }
            case RIGHT -> {
                for (UiNode n : group) n.x = right - Math.max(1, n.width);
            }
            case TOP -> {
                for (UiNode n : group) n.y = top;
            }
            case MIDDLE_V -> {
                double center = (top + bottom) / 2;
                for (UiNode n : group) n.y = center - Math.max(1, n.height) / 2;
            }
            case BOTTOM -> {
                for (UiNode n : group) n.y = bottom - Math.max(1, n.height);
            }
            case SPREAD_H -> {
                if (group.size() > 2) {
                    // sort by center x, keep first & last, distribute middle evenly
                    group.sort((a, b) -> Double.compare(centerX(a), centerX(b)));
                    double first = (group.getFirst().x + group.getFirst().width / 2) - left;
                    double last = right - (group.getLast().x + group.getLast().width / 2);
                    double span = (right - left) - first - last;
                    for (int i = 1; i < group.size() - 1; i++) {
                        UiNode n = group.get(i);
                        double cx = left + first + span * i / (group.size() - 1);
                        n.x = cx - n.width / 2;
                    }
                }
            }
            case SPREAD_V -> {
                if (group.size() > 2) {
                    group.sort((a, b) -> Double.compare(centerY(a), centerY(b)));
                    double first = (group.getFirst().y + group.getFirst().height / 2) - top;
                    double last = bottom - (group.getLast().y + group.getLast().height / 2);
                    double span = (bottom - top) - first - last;
                    for (int i = 1; i < group.size() - 1; i++) {
                        UiNode n = group.get(i);
                        double cy = top + first + span * i / (group.size() - 1);
                        n.y = cy - n.height / 2;
                    }
                }
            }
        }

        return true;
    }

    private static double centerX(UiNode n) {
        return n.x + Math.max(1, n.width) / 2;
    }

    private static double centerY(UiNode n) {
        return n.y + Math.max(1, n.height) / 2;
    }
}
