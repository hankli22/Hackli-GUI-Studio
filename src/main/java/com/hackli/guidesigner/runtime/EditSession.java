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

import com.hackli.guidesigner.modules.LayoutEditorModule;
import com.hackli.guidesigner.model.UiNode;

/**
 * Tracks a single in-progress drag/resize edit on a runtime GUI. Owned by
 * {@link UiScreen} and shared with all {@link UiContainer}s below it.
 */
public class EditSession {
    public static final int MODE_NONE = 0;
    public static final int MODE_MOVE = 1;
    public static final int MODE_RESIZE = 2;

    /** Callbacks of the screen being edited (may be null). */
    public UiCallbacks callbacks;

    /** The node last clicked/selected in edit mode (DEL removes it). */
    public UiNode selected;

    /** Snap grid in GUI pixels for drag operations. */
    public double snap = 4;

    private final UiScreen screen;

    private UiNode target;
    private UiNode parent;
    private int mode = MODE_NONE;

    private double grabDX, grabDY;

    private int corner;
    private double startW, startH;
    private double startMouseX, startMouseY;

    private UiRect parentRect = new UiRect();
    private boolean changed;

    public EditSession(UiScreen screen) {
        this.screen = screen;
    }

    /** Whether edit mode is currently enabled (via the "GUI Layout Editor" module). */
    public boolean isEditing() {
        return LayoutEditorModule.isEnabled();
    }

    public void begin(UiNode node, UiNode parent, UiRect widgetRect, double mouseX, double mouseY) {
        this.target = node;
        this.parent = parent;
        this.parentRect = new UiRect(parent.renderX, parent.renderY, parent.renderW, parent.renderH);
        this.selected = node;

        grabDX = widgetRect.x - mouseX;
        grabDY = widgetRect.y - mouseY;

        startW = widgetRect.w;
        startH = widgetRect.h;
        startMouseX = mouseX;
        startMouseY = mouseY;
        corner = -1;

        if (widgetRect.nearCorner(mouseX, mouseY, 6)) {
            mode = MODE_RESIZE;

            if (Math.abs(mouseX - widgetRect.x) < Math.abs(mouseX - (widgetRect.x + widgetRect.w))) corner |= 1; // left
            if (Math.abs(mouseY - widgetRect.y) < Math.abs(mouseY - (widgetRect.y + widgetRect.h))) corner |= 2; // top
        } else {
            mode = MODE_MOVE;
        }
    }

    /** @return true when something changed and layouts must be recalculated. */
    public boolean update(double mouseX, double mouseY) {
        if (target == null || mode == MODE_NONE) return false;

        double sx = Math.round(mouseX / snap) * snap;
        double sy = Math.round(mouseY / snap) * snap;

        if (mode == MODE_MOVE) {
            target.x = Math.round(sx + grabDX - parentRect.x - parentRect.w * target.anchorX.f);
            target.y = Math.round(sy + grabDY - parentRect.y - parentRect.h * target.anchorY.f);
        } else if (mode == MODE_RESIZE) {
            double dx = sx - startMouseX;
            double dy = sy - startMouseY;

            double newW = startW, newH = startH;

            if ((corner & 1) != 0) newW += -dx;
            else newW += dx;

            if ((corner & 2) != 0) newH += -dy;
            else newH += dy;

            target.width = Math.max(8, newW);
            target.height = Math.max(8, newH);
        }

        changed = true;
        return true;
    }

    public void commit() {
        if (changed && screen != null) {
            screen.onLayoutEdited();
        }

        target = null;
        parent = null;
        mode = MODE_NONE;
        changed = false;
    }

    public UiNode target() {
        return target;
    }

    public int mode() {
        return mode;
    }
}
