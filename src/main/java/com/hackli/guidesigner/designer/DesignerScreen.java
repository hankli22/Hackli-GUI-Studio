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

package com.hackli.guidesigner.designer;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;

/**
 * Thin screen wrapper around the custom drawn {@link DesignerPage}. All UI is
 * rendered by the page itself; this class only hosts it inside Meteor's screen
 * infrastructure so the ESC-key, scaling and rendering plumbing keep working.
 */
public class DesignerScreen extends WidgetScreen {
    public DesignerScreen() {
        super(GuiThemes.get(), "Hackli GUI Studio");
    }

    @Override
    public void initWidgets() {
        DesignerPage page = new DesignerPage();
        add(page).top().expandX().expandWidgetY();
    }
}
