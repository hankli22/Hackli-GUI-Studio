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

import java.util.HashMap;
import java.util.Map;

/**
 * GLFW key codes and their names.
 *
 * <p>Meteor's {@code Keybind.toString()} renders a binding through
 * {@code Utils.getKeyName(code)}, which defers to {@code glfwGetKeyName}. The
 * designers are Minecraft-independent, so this table reproduces the names GLFW
 * returns for the keys a plugin actually binds (letters, digits, function keys,
 * the named keys and numpad), and the code is what gets exported.</p>
 *
 * <p>Mouse buttons use Meteor's {@code Utils.getButtonName} spelling.</p>
 */
public final class KeyNames {
    /** GLFW_KEY_UNKNOWN: "no key bound". */
    public static final int NONE = -1;

    /** GLFW modifier bits, as used by {@code Keybind.fromKeys}. */
    public static final int MOD_SHIFT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_ALT = 0x0004;
    public static final int MOD_SUPER = 0x0008;

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<String, Integer> CODES = new HashMap<>();

    private KeyNames() {}

    static {
        // Printable keys: GLFW reports their lower case character, and Meteor
        // capitalises it ("k" -> "K").
        for (int c = 'A'; c <= 'Z'; c++) {
            int glfw = c;                       // GLFW_KEY_A == 65 == 'A'
            add(glfw, String.valueOf((char) c));
        }
        for (int d = '0'; d <= '9'; d++) {
            add(d, String.valueOf((char) d));   // GLFW_KEY_0 == 48
        }
        String[] punctuation = {
            "Space", "Apostrophe", "Comma", "Minus", "Period", "Slash",
            "Semicolon", "Equal", "Left Bracket", "Backslash", "Right Bracket", "Grave Accent",
        };
        for (int i = 0; i < punctuation.length; i++) add(32 + i, punctuation[i]);

        String[] named = {
            "Esc", "Enter", "Tab", "Backspace", "Insert", "Delete",
            "Right", "Left", "Down", "Up", "Page Up", "Page Down", "Home", "End",
            "Caps Lock", "Scroll Lock", "Num Lock", "Print Screen", "Pause",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25",
            "Kp 0", "Kp 1", "Kp 2", "Kp 3", "Kp 4", "Kp 5", "Kp 6", "Kp 7", "Kp 8", "Kp 9",
            "Kp Decimal", "Kp Divide", "Kp Multiply", "Kp Subtract", "Kp Add", "Kp Enter", "Kp Equal",
            "Left Shift", "Left Control", "Left Alt", "Left Super",
            "Right Shift", "Right Control", "Right Alt", "Right Super", "Menu",
        };
        // Same order as `named`: GLFW numbers the named keys from 256, with the
        // numpad block at 320 and the modifier keys at 340.
        int[] codes = {
            256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267, 268, 269,
            280, 281, 282, 283, 284,
            290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301,
            302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314,
            320, 321, 322, 323, 324, 325, 326, 327, 328, 329,
            330, 331, 332, 333, 334, 335, 336,
            340, 341, 342, 343, 344, 345, 346, 347, 348,
        };

        for (int i = 0; i < named.length && i < codes.length; i++) add(codes[i], named[i]);

        add(NONE, "Unknown");
        add(-2, "None");                        // Keybind.none() renders as "None"
    }

    private static void add(int code, String name) {
        NAMES.put(code, name);
        CODES.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), code);
    }

    /** Meteor's {@code Utils.getKeyName}: the label shown on the button. */
    public static String keyName(int code) {
        String name = NAMES.get(code);
        return name == null ? "Key " + code : name;
    }

    /** Meteor's {@code Utils.getButtonName}. */
    public static String buttonName(int button) {
        return switch (button) {
            case 0 -> "Left Mouse";
            case 1 -> "Middle Mouse";
            case 2 -> "Right Mouse";
            default -> "Mouse " + (button + 1);
        };
    }

    /** Looks a name back up (used when a document is hand-edited). */
    public static int codeOf(String name) {
        if (name == null || name.isEmpty()) return NONE;
        Integer code = CODES.get(name.trim().toLowerCase(java.util.Locale.ROOT));
        return code == null ? NONE : code;
    }

    /** Comma separated modifier labels, in Meteor's order. */
    public static String modifierLabel(int modifiers) {
        StringBuilder label = new StringBuilder();
        if ((modifiers & MOD_CONTROL) != 0) label.append("Ctrl + ");
        if ((modifiers & MOD_SUPER) != 0) label.append("Cmd + ");
        if ((modifiers & MOD_ALT) != 0) label.append("Alt + ");
        if ((modifiers & MOD_SHIFT) != 0) label.append("Shift + ");
        return label.toString();
    }

    /**
     * The binding as the button shows it: {@code Keybind.toString()} with the
     * "None"-when-unset behaviour. {@code code} is NONE for an unbound keybind,
     * {@code -2} for the "explicitly reset to nothing" state.
     */
    public static String label(int code, int modifiers, boolean isKey) {
        if (code == NONE) return "Unknown";
        if (code == -2) return "None";
        if (!isKey) return buttonName(code);
        return modifierLabel(modifiers) + keyName(code);
    }
}
