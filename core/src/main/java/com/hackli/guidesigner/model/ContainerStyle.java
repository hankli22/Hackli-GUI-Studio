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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hackli.guidesigner.model;

/**
 * Which Meteor container widget a {@code CONTAINER} node maps to.
 *
 * <ul>
 *   <li>{@code PANEL} - {@code WWindow}: accent header, flat body, padded
 *       content (this is what the designer has always drawn).</li>
 *   <li>{@code SECTION} - {@code WSection}: a horizontal separator with the
 *       title in the gap plus a collapse triangle, no background.</li>
 *   <li>{@code VIEW} - {@code WView}: scrollable area with a scrollbar.</li>
 *   <li>{@code TABLE} - {@code WTable}: lines its children up in columns. This
 *       is how Meteor's settings pages line up {@code [label][widget][reset]};
 *       children fill row by row, and {@code UiNode.Cell.row()} starts a new row
 *       explicitly.</li>
 * </ul>
 */
public enum ContainerStyle {
    PANEL("Panel (window)"),
    SECTION("Section (collapsible)"),
    VIEW("View (scrollable)"),
    TABLE("Table (columns)");

    public final String displayName;

    ContainerStyle(String displayName) {
        this.displayName = displayName;
    }
}
