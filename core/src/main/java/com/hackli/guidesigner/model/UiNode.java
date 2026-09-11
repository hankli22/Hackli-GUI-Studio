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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A single node of a GUI layout tree. This class is a plain data object that is
 * serialized to JSON by Gson, so every public (non transient) field is part of
 * the on-disk format. Keep it free of runtime state.
 */
public class UiNode {
    /** Unique id within its parent, used to route callbacks from the GUI to the backend. */
    public String id;

    /** Widget kind. */
    public UiType type;

    /** How this node places its children (only meaningful for containers). */
    public LayoutMode layout = LayoutMode.FLOW;

    /** Whether the widget should be shown at all. */
    public boolean visible = true;

    // ---- Absolute placement (used when the PARENT uses LayoutMode.ABSOLUTE) ----

    /** Horizontal anchor point inside the parent. */
    public AnchorX anchorX = AnchorX.LEFT;

    /** Vertical anchor point inside the parent. */
    public AnchorY anchorY = AnchorY.TOP;

    /** Offset in GUI pixels from the anchor point. */
    public double x, y;

    // ---- Size ----

    /** Fixed width in GUI pixels; 0 or negative means "auto". */
    public double width, height;

    // ---- Content ----

    /** Label / button / container title / separator text / text box content. */
    public String text = "";

    /** Placeholder text, only used by text boxes. */
    public String placeholder = "";

    /** Checked state, only used by check boxes. */
    public boolean checked;

    /** Slider value and range, only used by sliders. */
    public double value, min, max;

    /** Number widget: integer (WIntEdit) or decimal (WDoubleEdit). */
    public boolean integer = true;

    /** Number widget: step of the -/+ buttons. */
    public double step = 1;

    /** Number widget: show the slider part (Meteor's {@code noSlider}). */
    public boolean showSlider = true;

    /** Number widget: show the -/+ buttons. */
    public boolean showButtons = true;

    /** Dropdown entries and selected entry, only used by drop downs. */
    public List<String> options = new ArrayList<>();

    /** The currently selected dropdown entry (null when nothing selected). */
    public String selected;

    /**
     * Texture reference for a {@link UiType#TEXTURE} node: either a vanilla
     * texture path ({@code item/diamond}, {@code block/stone}), an item id
     * ({@code minecraft:diamond}) or a Meteor GUI icon name ({@code reset},
     * {@code edit}, {@code favorite_yes}).
     */
    public String texture = "reset";

    /** Clockwise rotation of a texture in degrees; Meteor's {@code WTexture} takes one. */
    public double rotation;

    /**
     * {@link UiType#KEYBIND}: the bound GLFW key code, or {@link KeyNames#NONE}
     * when nothing is bound. Modifier keys are bits in {@link #modifiers}.
     */
    public int key = KeyNames.NONE;

    /** {@link UiType#KEYBIND}: {@code true} for a key, {@code false} for a mouse button. */
    public boolean keyIsKey = true;

    /** {@link UiType#KEYBIND}: GLFW modifier bits (Ctrl / Shift / Alt / Super). */
    public int modifiers;

    /** {@link UiType#KEYBIND}: true while the widget waits for the next input (transient). */
    public transient boolean listening;

    /**
     * {@link UiType#ITEM}: the item to draw the icon of, as an id
     * ({@code minecraft:diamond}, {@code stone}). Meteor renders an item as a
     * 16x16 sprite scaled 2x inside a 32x32 box ({@code WItem}).
     */
    public String itemId = "minecraft:diamond";

    /**
     * {@link UiType#ITEM}: count drawn over the icon, 1..64, or 0 for none -
     * {@code WItem} passes {@code overlay = true}, which draws a stack size.
     */
    public int itemCount = 1;

    /**
     * {@link UiType#ENTITY}: the entity type whose icon to show. The icon is the
     * spawn egg's sprite, which is what Meteor's entity list uses; entities
     * without an egg fall back to the placeholder.
     */
    public String entityId = "minecraft:zombie";

    /** {@link UiType#SELECT}: number shown as "(N selected)"; negative hides it. */
    public int itemCountSelected = -1;

    // ---- Style & input configuration ----

    /** Text color as "#RRGGBB" or "#RRGGBBAA"; empty = theme default. */
    public String textColor = "";

    /** Horizontal alignment of the text for text-bearing widgets. */
    public TextAlign textAlign = TextAlign.LEFT;

    /** Maximum input length; 0 = unlimited (only used by text boxes). */
    public int maxLength;

    /** Input restriction (only used by text boxes). */
    public InputFilter inputFilter = InputFilter.NONE;

    /** Custom callback method name (only used by interactive widgets). */
    public String handler = "";

    /**
     * Hover text (Meteor's {@code WWidget.tooltip}). Drawn last, next to the
     * pointer, by every widget type - {@code null} or empty means "no tooltip".
     */
    public String tooltip = "";

    // ---- Container behaviour ----

    /** How a container places its children (vertical list by default). */
    public Orientation orientation = Orientation.VERTICAL;

    /** Which Meteor container widget this maps to (only used by containers). */
    public ContainerStyle style = ContainerStyle.PANEL;

    /** Collapsed state of a {@link ContainerStyle#SECTION}. */
    public boolean collapsed;

    /** Height cap of a {@link ContainerStyle#VIEW} (0 = use the node height). */
    public double maxHeight;

    /**
     * Meteor cell settings for this node inside its parent. {@code null} means
     * "all defaults", and Gson omits it, so documents stay clean.
     */
    public Cell cell;

    /** Per-cell layout options, mirroring Meteor's {@code Cell}. */
    public static class Cell {
        public double padTop, padRight, padBottom, padLeft;

        /** Share the leftover width of a horizontal list between these cells. */
        public boolean expandX;

        /** Alignment inside the cell (Meteor's AlignmentX / AlignmentY). */
        public AnchorX alignX = AnchorX.LEFT;
        public AnchorY alignY = AnchorY.TOP;

        /** Minimum width used by horizontal lists (Meteor's Cell.minWidth). */
        public double minWidth;

        /** Cells sharing a group are aligned together in a table layout. */
        public String group = "";

        /**
         * Forces a new row to start at this cell (Meteor's {@code WTable.row()}
         * before the cell). For uneven rows; a plain table only needs {@link #row}.
         */
        public boolean column;

        /**
         * Ends the row after this cell (Meteor's {@code WTable.row()} straight
         * after the cell). The first hint in a table also fixes its column count.
         */
        public boolean row;

        public Cell() {}

        /** Deep copy, used by {@link UiNode#copy(String)}. */
        public Cell(Cell other) {
            padTop = other.padTop;
            padRight = other.padRight;
            padBottom = other.padBottom;
            padLeft = other.padLeft;
            expandX = other.expandX;
            alignX = other.alignX;
            alignY = other.alignY;
            minWidth = other.minWidth;
            group = other.group;
            column = other.column;
            row = other.row;
        }

        public Cell pad(double v) {
            padTop = padRight = padBottom = padLeft = v;
            return this;
        }

        public Cell padHorizontal(double v) {
            padLeft = padRight = v;
            return this;
        }

        public Cell padVertical(double v) {
            padTop = padBottom = v;
            return this;
        }

        public Cell expandX(boolean value) {
            expandX = value;
            return this;
        }

        /** Makes this cell the last one of its row (Meteor's {@code table.row()}). */
        public Cell endRow() {
            row = true;
            return this;
        }

        /** Makes this cell the first one of a new row (for rows of uneven length). */
        public Cell startColumn() {
            column = true;
            return this;
        }

        /** Alignment inside the cell. */
        public Cell align(AnchorX x, AnchorY y) {
            alignX = x;
            alignY = y;
            return this;
        }

        /** Minimum width for this cell (Meteor's {@code Cell.minWidth}). */
        public Cell minWidth(double value) {
            minWidth = value;
            return this;
        }

        /** Syncs this cell's column width with every other cell in the same group. */
        public Cell group(String value) {
            group = value;
            return this;
        }
    }

    /** Convenience: create (or reuse) this node's cell settings. */
    public Cell cell() {
        if (cell == null) cell = new Cell();
        return cell;
    }

    // ---- Children ----

    public List<UiNode> children = new ArrayList<>();

    // ---- Runtime cache (never serialized) ----

    /** Screen-space rect of the last layout computation (designer canvas / runtime edit). */
    public transient double renderX, renderY, renderW, renderH;

    /** Transient pointer to the parent node, set while rendering. */
    public transient UiNode parent;

    /** Check box open/close animation progress (renderer only, never serialised). */
    public transient double animProgress;

    /** Whether {@link #animProgress} has been seeded from {@code checked}. */
    public transient boolean animInit;

    /** Scroll offset of a {@link ContainerStyle#VIEW} (view state, not design data). */
    public transient double scroll;

    /** Content height of the last layout, used for the scrollbar (transient). */
    public transient double scrollHeight;

    /**
     * Live text of a NUMBER widget's text box while it is being typed into
     * (transient editor state, never serialised). {@code null} = show the value.
     */
    public transient String editText;

    // ================= Factories =================

    public static UiNode create(UiType type, String id) {
        UiNode node = new UiNode();
        node.type = type;
        node.id = id;
        return node;
    }

    public static UiNode container(String id) {
        return create(UiType.CONTAINER, id);
    }

    public static UiNode container(String id, double width, double height) {
        return container(id).size(width, height);
    }

    public static UiNode label(String id) {
        return create(UiType.LABEL, id);
    }

    public static UiNode button(String id) {
        return create(UiType.BUTTON, id);
    }

    public static UiNode textBox(String id) {
        return create(UiType.TEXTBOX, id);
    }

    public static UiNode checkBox(String id) {
        return create(UiType.CHECKBOX, id);
    }

    public static UiNode slider(String id) {
        return create(UiType.SLIDER, id);
    }

    public static UiNode dropdown(String id) {
        return create(UiType.DROPDOWN, id);
    }

    /** A Meteor number editor (WIntEdit / WDoubleEdit). */
    public static UiNode number(String id) {
        UiNode node = create(UiType.NUMBER, id);
        node.min = 0;
        node.max = 100;
        node.value = 50;
        return node;
    }

    /** A raw image (Meteor's {@code WTexture}); see {@link #texture}. */
    public static UiNode texture(String id) {
        return create(UiType.TEXTURE, id);
    }

    /** A key bind button (Meteor's {@code WKeybind}). */
    public static UiNode keybind(String id) {
        return create(UiType.KEYBIND, id);
    }

    /** An item icon (Meteor's {@code WItem}): 32x32 box, 16x16 sprite scaled 2x. */
    public static UiNode item(String id) {
        return create(UiType.ITEM, id);
    }

    /** An entity icon: the spawn egg sprite of that entity type. */
    public static UiNode entity(String id) {
        return create(UiType.ENTITY, id);
    }

    /** The {@code Select} button of a settings row, with its "(N selected)" count. */
    public static UiNode select(String id) {
        return create(UiType.SELECT, id);
    }

    public static UiNode separator(String id) {
        return create(UiType.SEPARATOR, id);
    }

    // ================= Fluent builder (used by the exporter and by plugin authors) =================

    public UiNode text(String text) {
        this.text = text;
        return this;
    }

    public UiNode placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public UiNode pos(double x, double y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public UiNode size(double width, double height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public UiNode anchor(AnchorX anchorX, AnchorY anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        return this;
    }

    public UiNode layout(LayoutMode layout) {
        this.layout = layout;
        return this;
    }

    /** Which Meteor container widget this maps to. */
    public UiNode style(ContainerStyle style) {
        this.style = style;
        return this;
    }

    /** Collapse a section (only meaningful for {@link ContainerStyle#SECTION}). */
    public UiNode collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        return this;
    }

    /** Flow direction of a container. */
    public UiNode orientation(Orientation orientation) {
        this.orientation = orientation;
        return this;
    }

    public UiNode checked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public UiNode sliderValue(double value) {
        this.value = value;
        return this;
    }

    public UiNode range(double min, double max) {
        this.min = min;
        this.max = max;
        return this;
    }

    /** Step of a NUMBER widget's -/+ buttons. */
    public UiNode step(double step) {
        this.step = step;
        return this;
    }

    /** Whether a NUMBER widget is an integer editor (WIntEdit) or a double one (WDoubleEdit). */
    public UiNode integer(boolean integer) {
        this.integer = integer;
        return this;
    }

    /** Whether a NUMBER widget shows the slider part. */
    public UiNode showSlider(boolean showSlider) {
        this.showSlider = showSlider;
        return this;
    }

    /** Whether a NUMBER widget shows the -/+ buttons. */
    public UiNode showButtons(boolean showButtons) {
        this.showButtons = showButtons;
        return this;
    }

    public UiNode options(List<String> options) {
        this.options = options;
        return this;
    }

    public UiNode options(String... options) {
        this.options = new ArrayList<>(List.of(options));
        return this;
    }

    public UiNode selected(String selected) {
        this.selected = selected;
        return this;
    }

    public UiNode textColor(String textColor) {
        this.textColor = textColor;
        return this;
    }

    public UiNode textAlign(TextAlign textAlign) {
        this.textAlign = textAlign;
        return this;
    }

    public UiNode maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public UiNode inputFilter(InputFilter inputFilter) {
        this.inputFilter = inputFilter;
        return this;
    }

    public UiNode handler(String handler) {
        this.handler = handler;
        return this;
    }

    /** Hover text (Meteor's {@code WWidget.tooltip}). */
    public UiNode tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** Item of a {@link UiType#ITEM} node, and the stack count drawn over it. */
    public UiNode item(String itemId, int count) {
        this.itemId = itemId;
        this.itemCount = count;
        return this;
    }

    /** Entity of a {@link UiType#ENTITY} node. */
    public UiNode entityId(String entityId) {
        this.entityId = entityId;
        return this;
    }

    /** {@code (N selected)} count of a {@link UiType#SELECT} node; negative hides it. */
    public UiNode selectedCount(int count) {
        this.itemCountSelected = count;
        return this;
    }

    public UiNode visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /** Texture reference of a {@link UiType#TEXTURE} node. */
    public UiNode textureRef(String texture) {
        this.texture = texture;
        return this;
    }

    /** Clockwise rotation in degrees, for {@link UiType#TEXTURE}. */
    public UiNode rotation(double rotation) {
        this.rotation = rotation;
        return this;
    }

    /** Binding of a {@link UiType#KEYBIND}: a GLFW key code plus modifier bits. */
    public UiNode keybind(int key, int modifiers, boolean isKey) {
        this.key = key;
        this.modifiers = modifiers;
        this.keyIsKey = isKey;
        return this;
    }

    public UiNode child(UiNode child) {
        child.parent = this;
        children.add(child);
        return this;
    }

    // ================= Tree helpers =================

    /** Depth-first lookup by id. */
    public UiNode find(String id) {
        if (this.id.equals(id)) return this;

        for (UiNode child : children) {
            UiNode found = child.find(id);
            if (found != null) return found;
        }

        return null;
    }

    /** Removes the given node (anywhere in the subtree) and returns it. */
    public UiNode remove(UiNode target) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == target) {
                children.remove(i);
                return target;
            }
        }

        for (UiNode child : children) {
            UiNode removed = child.remove(target);
            if (removed != null) return removed;
        }

        return null;
    }

    /** Deep copy with a new unique id. */
    public UiNode copy(String newId) {
        UiNode copy = new UiNode();
        copy.id = newId;
        copy.type = type;
        copy.layout = layout;
        copy.visible = visible;
        copy.anchorX = anchorX;
        copy.anchorY = anchorY;
        copy.x = x;
        copy.y = y;
        copy.width = width;
        copy.height = height;
        copy.text = text;
        copy.placeholder = placeholder;
        copy.checked = checked;
        copy.value = value;
        copy.min = min;
        copy.max = max;
        copy.integer = integer;
        copy.step = step;
        copy.showSlider = showSlider;
        copy.showButtons = showButtons;
        copy.texture = texture;
        copy.rotation = rotation;
        copy.key = key;
        copy.keyIsKey = keyIsKey;
        copy.modifiers = modifiers;
        copy.itemId = itemId;
        copy.itemCount = itemCount;
        copy.entityId = entityId;
        copy.itemCountSelected = itemCountSelected;
        copy.options = new ArrayList<>(options);
        copy.selected = selected;
        copy.textColor = textColor;
        copy.textAlign = textAlign;
        copy.maxLength = maxLength;
        copy.inputFilter = inputFilter;
        copy.handler = handler;
        copy.tooltip = tooltip;
        copy.orientation = orientation;
        copy.style = style;
        copy.collapsed = collapsed;
        copy.maxHeight = maxHeight;
        copy.cell = cell == null ? null : new Cell(cell);
        copy.children = new ArrayList<>(children.size());

        for (UiNode child : children) copy.children.add(child.copy(child.id));

        return copy;
    }

    /** Counts all nodes in the subtree including this one. */
    public int count() {
        int count = 1;
        for (UiNode child : children) count += child.count();
        return count;
    }

    // ================= Auto ids =================

    private static final Map<UiType, Integer> COUNTERS = new HashMap<>();

    /** Generates a new id for the given type, guaranteed to be unique in the given tree. */
    public static String uniqueId(UiNode root, UiType type) {
        String id;
        do {
            int n = COUNTERS.merge(type, 1, Integer::sum);
            id = type.idPrefix + "-" + n;
        } while (root != null && root.find(id) != null);

        return id;
    }

    /**
     * Deep copy with all ids regenerated so the copy has no id collisions
     * against {@code treeScope} (used when placing component templates).
     */
    public UiNode copyWithFreshIds(UiNode treeScope) {
        UiNode copy = copy(id);

        java.util.Set<String> used = new java.util.HashSet<>();
        if (treeScope != null) collectIds(treeScope, used);

        regenerateIds(copy, used);
        return copy;
    }

    private static void collectIds(UiNode node, java.util.Set<String> out) {
        out.add(node.id);
        for (UiNode child : node.children) collectIds(child, out);
    }

    private void regenerateIds(UiNode node, java.util.Set<String> used) {
        String id;
        do {
            int n = COUNTERS.merge(node.type, 1, Integer::sum);
            id = node.type.idPrefix + "-" + n;
        } while (used.contains(id));

        node.id = id;
        used.add(id);

        for (UiNode child : node.children) regenerateIds(child, used);
    }
}
