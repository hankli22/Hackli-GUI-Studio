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

import com.hackli.guidesigner.model.UiNode;

/** Backend interface implemented by plugin code. Every widget event is routed here. */
public interface UiCallbacks {
    /**
     * Called whenever an interactive widget in the GUI fires an event.
     *
     * @param id    the widget's id (as set in the designer).
     * @param event one of "click" (button), "change" (text box / slider),
     *              "toggle" (check box) or "select" (drop down).
     * @param value event payload: empty for clicks, the check box state
     *              ("true"/"false"), the slider value or the selected option.
     */
    default void onAction(String id, String event, String value) {}

    /** Convenience: fired when a widget's value changed (text, slider, check box, drop down). */
    default void onValueChanged(String id, String value) {}

    /** Convenience: fired when a {@link UiNode} assigned to the callback is clicked. */
    default void onButtonClick(String id) {}
}
