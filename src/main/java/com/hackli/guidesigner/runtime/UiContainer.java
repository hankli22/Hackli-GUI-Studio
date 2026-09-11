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

import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.UiNode;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/**
 * Runtime container for a {@link UiNode}. Supports both layout modes:
 * <ul>
 *   <li>{@code FLOW} - children are stacked vertically and size themselves.</li>
 *   <li>{@code ABSOLUTE} - children are placed using anchors + offsets, and can
 *       be moved/resized live when edit mode is active.</li>
 * </ul>
 */
public class UiContainer extends WContainer {
    /** MeteorGuiTheme backgroundColor (normal). */
    private static final Color BG = new Color(20, 20, 20, 200);
    /** MeteorGuiTheme accentColor - WMeteorWindow draws its header with it. */
    private static final Color HEADER = new Color(145, 61, 226, 255);
    /** MeteorGuiTheme titleTextColor. */
    private static final Color TITLE_TEXT = new Color(255, 255, 255, 255);
    private static final Color EDIT_ACCENT = new Color(0, 180, 140, 255);
    private static final Color EDIT_SELECTED = new Color(255, 210, 0, 255);
    private static final Color HANDLE_BG = new Color(0, 180, 140, 220);

    public final UiNode node;
    private final EditSession edit;
    private final UiRect self = new UiRect();

    public UiContainer(UiNode node, EditSession edit) {
        this.node = node;
        this.edit = edit;
    }

    // ================= Size =================

    /** Meteor's window header height: pad + textHeight + pad. */
    private double headerHeight() {
        if (theme == null) return UiLayout.TITLE_H;
        return theme.pad() * 2 + theme.textHeight();
    }

    @Override
    protected void onCalculateSize() {
        for (Cell<?> cell : cells) cell.widget().calculateSize();

        if (node.width > 0) {
            width = node.width;
        } else {
            width = node.type.defaultWidth;
            for (Cell<?> cell : cells) {
                width = Math.max(width, cell.padLeft() + cell.widget().width + cell.padRight() + UiLayout.PAD * 2);
            }
        }

        if (node.height > 0) {
            height = node.height;
        } else {
            height = node.type.defaultHeight;
            if (node.layout == LayoutMode.FLOW) {
                double gap = UiLayout.GAP;
                double content = 0;
                for (int i = 0; i < cells.size(); i++) {
                    if (i > 0) content += gap;
                    content += cells.get(i).padTop() + cells.get(i).widget().height + cells.get(i).padBottom();
                }
                double top = node.text.isEmpty() ? UiLayout.PAD : headerHeight();
                height = content + top + UiLayout.PAD;
            }
        }
    }

    // ================= Positions =================

    @Override
    protected void onCalculateWidgetPositions() {
        x = Math.round(x);
        y = Math.round(y);

        self.set(x, y, Math.max(1, width), Math.max(1, height));
        node.renderX = self.x;
        node.renderY = self.y;
        node.renderW = self.w;
        node.renderH = self.h;

        if (node.layout == LayoutMode.ABSOLUTE) {
            positionAbsolute();
        } else {
            positionFlow();
        }
    }

    private void positionAbsolute() {
        for (int i = 0; i < cells.size(); i++) {
            Cell<?> cell = cells.get(i);
            UiNode child = node.children.get(i);

            UiRect r = UiLayout.absoluteRect(child, self);
            cell.widget().calculateSize();

            cell.x = r.x;
            cell.y = r.y;

            if (child.width > 0) cell.expandX();
            cell.width = child.width > 0 ? r.w : cell.widget().width;
            cell.height = child.height > 0 ? r.h : cell.widget().height;

            cell.alignWidget();

            cacheRect(child, cell.widget());
        }
    }

    private void positionFlow() {
        double innerX = self.x + UiLayout.PAD;
        double innerW = Math.max(1, self.w - UiLayout.PAD * 2);
        double cy = self.y + (node.text.isEmpty() ? UiLayout.PAD : headerHeight());

        for (int i = 0; i < cells.size(); i++) {
            Cell<?> cell = cells.get(i);
            UiNode child = node.children.get(i);

            double h = child.height > 0 ? child.height : cell.widget().height;

            cell.x = innerX;
            cell.y = cy;
            cell.width = innerW;
            cell.height = h;
            cell.expandX();

            cell.alignWidget();

            cacheRect(child, cell.widget());

            cy += h + UiLayout.GAP;
        }
    }

    private void cacheRect(UiNode child, meteordevelopment.meteorclient.gui.widgets.WWidget widget) {
        child.renderX = widget.x;
        child.renderY = widget.y;
        child.renderW = widget.width;
        child.renderH = widget.height;
    }

    // ================= Rendering =================

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        // WMeteorWindow: flat backgroundColor body, accent coloured header,
        // pad + textHeight + pad tall, no outline.
        if (node.text.isEmpty()) {
            renderer.quad(x, y, width, height, BG);
            return;
        }

        double header = headerHeight();
        renderer.quad(x, y + header, width, height - header, BG);
        renderer.quad(x, y, width, header, HEADER);
        renderer.text(node.text, x + theme.pad(), y + theme.pad(), TITLE_TEXT, true);
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean result = super.render(renderer, mouseX, mouseY, delta);

        if (edit != null && edit.isEditing()) {
            renderEditOverlay(renderer);
        }

        return result;
    }

    private void renderEditOverlay(GuiRenderer renderer) {
        if (node.layout == LayoutMode.FLOW) {
            if (edit.selected == node) renderBorder(renderer, self, EDIT_SELECTED);
            return; // flow children are positioned by the container, no moving
        }

        for (UiNode child : node.children) {
            UiRect r = new UiRect(child.renderX, child.renderY, child.renderW, child.renderH);
            Color color = child == edit.selected ? EDIT_SELECTED : EDIT_ACCENT;

            renderBorder(renderer, r, color);
            renderer.text(child.id, r.x + 3, r.y - 12, color, false);

            // Handles
            double s = 6;
            renderer.quad(r.x - s / 2, r.y - s / 2, s, s, HANDLE_BG);
            renderer.quad(r.x + r.w - s / 2, r.y - s / 2, s, s, HANDLE_BG);
            renderer.quad(r.x - s / 2, r.y + r.h - s / 2, s, s, HANDLE_BG);
            renderer.quad(r.x + r.w - s / 2, r.y + r.h - s / 2, s, s, HANDLE_BG);
        }
    }

    private void renderBorder(GuiRenderer renderer, UiRect r, Color color) {
        renderer.quad(r.x - 1, r.y - 1, r.w + 2, 1, color);
        renderer.quad(r.x - 1, r.y + r.h, r.w + 2, 1, color);
        renderer.quad(r.x - 1, r.y, 1, r.h, color);
        renderer.quad(r.x + r.w, r.y, 1, r.h, color);
    }

    // ================= Editing =================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (edit != null && edit.isEditing() && click.button() == GLFW_MOUSE_BUTTON_LEFT && !doubled) {
            handleEditClick(click.x(), click.y());
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleEditClick(double mouseX, double mouseY) {
        // children of a FLOW container cannot be moved -> just select
        if (node.layout != LayoutMode.ABSOLUTE) {
            UiNode hit = UiLayout.hitTest(node, mouseX, mouseY);
            if (hit != null && !hit.children.isEmpty()) edit.selected = hit;
            return;
        }

        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode child = node.children.get(i);
            UiRect rect = new UiRect(child.renderX, child.renderY, child.renderW, child.renderH);

            if (rect.contains(mouseX, mouseY)) {
                edit.begin(child, node, rect, mouseX, mouseY);
                return;
            }
        }

        edit.selected = null;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (edit != null && edit.isEditing()) {
            if (edit.mode() != EditSession.MODE_NONE) {
                edit.commit();
                return true;
            }
            return super.mouseReleased(click);
        }

        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        if (edit != null && edit.isEditing() && edit.mode() != EditSession.MODE_NONE) {
            if (edit.update(mouseX, mouseY)) invalidate();
            return;
        }

        super.mouseMoved(mouseX, mouseY, lastMouseX, lastMouseY);
    }
}
