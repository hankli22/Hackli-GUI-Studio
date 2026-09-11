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

import com.hackli.guidesigner.model.DocumentStore;
import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import net.minecraft.client.input.KeyInput;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;

/**
 * Base class for plugin GUIs built with Hackli GUI Studio.
 *
 * <p>Extend this class and pass your design (or the generated builder code) plus
 * a {@link UiCallbacks} implementation. When the "GUI Layout Editor" module is
 * active, players can drag widgets around, resize them and save their custom
 * layout to {@code config/hackli-gui-studio/layouts/}. Saved layouts
 * automatically override the defaults on the next open.</p>
 */
public class UiScreen extends WidgetScreen {
    protected final UiDocument doc;
    protected final UiCallbacks callbacks;
    protected EditSession edit;

    public UiScreen(String title, UiDocument design, UiCallbacks callbacks) {
        this(title, design, callbacks, true);
    }

    /**
     * @param loadOverride whether a player-saved layout override should be loaded
     *                     from {@code config/hackli-gui-studio/layouts/}.
     */
    public UiScreen(String title, UiDocument design, UiCallbacks callbacks, boolean loadOverride) {
        super(GuiThemes.get(), title);

        UiDocument override = loadOverride ? DocumentStore.loadLayout(title) : null;
        this.doc = override != null ? override : design;
        this.callbacks = callbacks;
    }

    @Override
    public void initWidgets() {
        edit = new EditSession(this);
        edit.callbacks = callbacks;
        edit.snap = com.hackli.guidesigner.modules.LayoutEditorModule.getSnap();

        WWidget root = UiRenderer.build(doc.root, theme, edit);
        add(root).center();
    }

    /** Called whenever the player finished a drag/resize edit. Persists the layout. */
    public void onLayoutEdited() {
        DocumentStore.saveLayout(doc);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (edit != null && edit.isEditing() && input.key() == GLFW_KEY_DELETE
            && edit.selected != null && edit.selected != doc.root) {

            UiNode removed = doc.root.remove(edit.selected);
            if (removed != null) {
                edit.selected = null;
                onLayoutEdited();
                reload();
            }
            return true;
        }

        return super.keyPressed(input);
    }
}
