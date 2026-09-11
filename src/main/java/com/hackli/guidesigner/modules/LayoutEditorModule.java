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

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * When enabled, GUI screens created with Hackli GUI Studio enter edit mode:
 * <ul>
 *   <li>Drag widgets to move them (absolute layout).</li>
 *   <li>Drag the corner handles to resize them.</li>
 *   <li>Press DEL to delete the selected widget.</li>
 *   <li>Layouts are saved to {@code config/hackli-gui-studio/layouts/} when
 *       a drag is finished.</li>
 * </ul>
 */
public class LayoutEditorModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> snap = sgGeneral.add(new DoubleSetting.Builder()
        .name("snap")
        .description("Snap grid (in GUI pixels) used while moving or resizing widgets.")
        .defaultValue(4.0)
        .range(0.0, 32.0)
        .build()
    );

    public LayoutEditorModule() {
        super(
            com.hackli.guidesigner.HackliGuiDesigner.CATEGORY,
            "gui-layout-editor",
            "Enables in-game editing for GUIs made with Hackli GUI Studio. " +
                "Drag widgets to move them, drag corner handles to resize, press DEL to delete. " +
                "Layouts are saved automatically and override the defaults."
        );
    }

    @Override
    public void onActivate() {
        info("Layout editing enabled. Open a GUI built with Hackli GUI Studio to edit it.");
    }

    @Override
    public void onDeactivate() {
        info("Layout editing disabled.");
    }

    /** Static, crash-safe access for the runtime. */
    public static boolean isEnabled() {
        Modules modules = Modules.get();
        if (modules == null) return false;

        LayoutEditorModule module = modules.get(LayoutEditorModule.class);
        return module != null && module.isActive();
    }

    public static double getSnap() {
        Modules modules = Modules.get();
        if (modules == null) return 4;

        LayoutEditorModule module = modules.get(LayoutEditorModule.class);
        return module != null ? module.snap.get() : 4;
    }
}
