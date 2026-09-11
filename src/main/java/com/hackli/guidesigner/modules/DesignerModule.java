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

package com.hackli.guidesigner.modules;

import com.hackli.guidesigner.designer.DesignerScreen;
import meteordevelopment.meteorclient.systems.modules.Module;

/** Opens the GUI designer screen. Bind a key to it for quick access. */
public class DesignerModule extends Module {
    public DesignerModule() {
        super(
            com.hackli.guidesigner.HackliGuiDesigner.CATEGORY,
            "gui-designer",
            "Opens the Hackli GUI Studio screen. Assign a keybind in the module settings " +
                "to open the designer quickly in-game."
        );
    }

    @Override
    public void onActivate() {
        try {
            mc.setScreen(new DesignerScreen());
        } finally {
            // Stay off so the keybind opens the designer every time it is pressed
            // (and never auto-reactivates on game join after a crash).
            toggle();
        }
    }
}
