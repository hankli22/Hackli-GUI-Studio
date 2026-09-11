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

/** The kinds of widgets that can be placed on a design canvas. */
public enum UiType {
    CONTAINER("Panel", "panel", 320, 200, true),
    LABEL("Label", "label", 120, 22, false),
    BUTTON("Button", "btn", 160, 42, false),
    TEXTBOX("Text Box", "text", 160, 42, false),
    CHECKBOX("Check Box", "check", 60, 24, false),
    SLIDER("Slider", "slider", 200, 24, false),
    DROPDOWN("Dropdown", "drop", 140, 30, false),
    SEPARATOR("Separator", "sep", 200, 10, false),
    NUMBER("Number", "num", 320, 30, false),
    TEXTURE("Texture", "tex", 32, 32, false),
    KEYBIND("Keybind", "bind", 120, 30, false),
    ITEM("Item", "item", 32, 32, false),
    ENTITY("Entity", "entity", 32, 32, false),
    SELECT("Select", "select", 140, 30, false);

    /** Human readable name shown in the palette. */
    public final String displayName;

    /** Short prefix used for auto generated widget ids (e.g. "btn-1"). */
    public final String idPrefix;

    /** Suggested width in GUI pixels when the widget is placed absolutely. */
    public final double defaultWidth;

    /** Suggested height in GUI pixels when the widget is placed absolutely. */
    public final double defaultHeight;

    /** Whether this type can contain child widgets. */
    public final boolean isContainer;

    UiType(String displayName, String idPrefix, double defaultWidth, double defaultHeight, boolean isContainer) {
        this.displayName = displayName;
        this.idPrefix = idPrefix;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.isContainer = isContainer;
    }

    /** Whether a node of this type renders a text property. */
    public boolean hasText() {
        return this == CONTAINER || this == LABEL || this == BUTTON || this == TEXTBOX || this == SEPARATOR;
    }

    /** Whether a node of this type renders a text placeholder property. */
    public boolean hasPlaceholder() {
        return this == TEXTBOX;
    }

    /** Whether a node of this type carries a value (checked / slider / dropdown). */
    public boolean hasValue() {
        return this == CHECKBOX || this == SLIDER || this == DROPDOWN || this == NUMBER;
    }
}
