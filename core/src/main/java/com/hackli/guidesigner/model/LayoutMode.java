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

package com.hackli.guidesigner.model;

/** How a container arranges its child widgets. */
public enum LayoutMode {
    /** Children are stacked vertically and size themselves (Meteor natives). */
    FLOW("Flow"),

    /** Children are placed freely using anchors and offsets (drag & drop). */
    ABSOLUTE("Absolute");

    public final String displayName;

    LayoutMode(String displayName) {
        this.displayName = displayName;
    }
}
