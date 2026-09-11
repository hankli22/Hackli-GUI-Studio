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

package com.hackli.guidesigner;

import com.hackli.guidesigner.commands.GuiDesignerCommand;
import com.hackli.guidesigner.modules.DesignerModule;
import com.hackli.guidesigner.modules.LayoutEditorModule;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class HackliGuiDesigner extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("GUI Designer");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Hackli GUI Studio (GPLv3)");

        // Snapshot / load all designs under config/hackli-gui-studio. The store
        // renames a pre-rename hackli-gui-designer folder once, so designs saved
        // by an older build are still picked up.
        com.hackli.guidesigner.model.DocumentStore.init(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("hackli-gui-studio")
        );
        if (com.hackli.guidesigner.model.DocumentStore.migrateLegacyDir()) {
            LOG.info("Migrated config/hackli-gui-designer to config/hackli-gui-studio");
        }

        Modules.get().add(new DesignerModule());
        Modules.get().add(new LayoutEditorModule());

        Commands.add(new GuiDesignerCommand());

        LOG.info("Hackli GUI Studio ready. Run .hackligui or enable the gui-designer module.");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hackli.guidesigner";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("hankli22", "hackli-gui-studio");
    }
}
