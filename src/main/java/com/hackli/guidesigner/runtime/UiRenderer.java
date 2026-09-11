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

import com.hackli.guidesigner.model.InputFilter;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.UiType;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Turns a {@link UiNode} tree into real Meteor GUI widgets and wires every
 * interactive widget to the screen's {@link UiCallbacks}.
 */
public class UiRenderer {
    private UiRenderer() {}

    /**
     * Builds the widget for a node. Hover text (Meteor's {@code widget.tooltip})
     * is applied here so every widget type honours {@code UiNode.tooltip}.
     */
    public static WWidget build(UiNode node, GuiTheme theme, EditSession edit) {
        WWidget widget = buildWidget(node, theme, edit);
        if (widget != null && node.tooltip != null && !node.tooltip.isEmpty()) {
            widget.tooltip = node.tooltip;
        }
        return widget;
    }

    private static WWidget buildWidget(UiNode node, GuiTheme theme, EditSession edit) {
        if (node.type == UiType.CONTAINER) {
            // Meteor container styles: WSection (collapsible) or WView (scrollable).
            if (node.style == com.hackli.guidesigner.model.ContainerStyle.SECTION) {
                meteordevelopment.meteorclient.gui.widgets.containers.WSection section =
                    theme.section(node.text.isEmpty() ? node.id : node.text, !node.collapsed);
                section.action = () -> fire(edit, node.id, "toggle", String.valueOf(section.isExpanded()));
                for (UiNode child : node.children) {
                    section.add(build(child, theme, edit)).top();
                }
                return section;
            }
            if (node.style == com.hackli.guidesigner.model.ContainerStyle.VIEW) {
                meteordevelopment.meteorclient.gui.widgets.containers.WView view = theme.view();
                view.maxHeight = node.height > 0 ? node.height : 400;
                for (UiNode child : node.children) {
                    view.add(build(child, theme, edit)).top();
                }
                return view;
            }

            UiContainer container = new UiContainer(node, edit);

            for (UiNode child : node.children) {
                container.add(build(child, theme, edit)).top();
            }

            return container;
        }

        return switch (node.type) {
            case LABEL -> {
                WLabel label = theme.label(node.text.isEmpty() ? node.id : node.text);
                if (!node.textColor.isEmpty()) label.color(parseColor(node.textColor));
                yield label;
            }
            case BUTTON -> {
                WButton button = theme.button(node.text.isEmpty() ? node.id : node.text);
                // The node id stays a tooltip when the author did not set one.
                if (node.tooltip == null || node.tooltip.isEmpty()) button.tooltip = node.id;
                button.action = () -> fire(edit, node.id, "click", "");
                yield button;
            }
            case TEXTBOX -> {
                CharFilter filter = textBoxFilter(node);
                WTextBox textBox = filter == null
                    ? theme.textBox(node.text, node.placeholder)
                    : theme.textBox(node.text, node.placeholder, filter);
                textBox.action = () -> fire(edit, node.id, "change", textBox.get());
                textBox.actionOnUnfocused = () -> fire(edit, node.id, "change", textBox.get());
                yield textBox;
            }
            case CHECKBOX -> {
                WCheckbox checkBox = theme.checkbox(node.checked);
                checkBox.action = () -> fire(edit, node.id, "toggle", String.valueOf(checkBox.checked));
                yield checkBox;
            }
            case SLIDER -> {
                double min = node.min;
                double max = node.max;
                if (max <= min) max = min + 1;

                WSlider slider = theme.slider(node.value, min, max);
                slider.actionOnRelease = () -> fire(edit, node.id, "release", String.valueOf(slider.get()));
                yield slider;
            }
            case DROPDOWN -> {
                String[] values = node.options.isEmpty() ? new String[]{"-"} : node.options.toArray(new String[0]);
                String selected = node.selected != null ? node.selected : values[0];

                WDropdown<String> dropdown = theme.dropdown(values, selected);
                dropdown.action = () -> fire(edit, node.id, "select", dropdown.get());
                yield dropdown;
            }
            case NUMBER -> {
                // Meteor's WIntEdit / WDoubleEdit (text box + -/+ buttons + slider).
                double min = node.min;
                double max = node.max > min ? node.max : min + 1;
                if (node.integer) {
                    meteordevelopment.meteorclient.gui.widgets.input.WIntEdit edit2 =
                        theme.intEdit((int) Math.rint(node.value), (int) Math.rint(min), (int) Math.rint(max),
                            (int) Math.rint(min), (int) Math.rint(max), !node.showSlider);
                    edit2.action = () -> fire(edit, node.id, "change", String.valueOf(edit2.get()));
                    edit2.actionOnRelease = edit2.action;
                    yield edit2;
                }
                meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit edit2 =
                    theme.doubleEdit(node.value, min, max, min, max);
                edit2.action = () -> fire(edit, node.id, "change", String.valueOf(edit2.get()));
                edit2.actionOnRelease = edit2.action;
                yield edit2;
            }
            case SEPARATOR -> node.text.isEmpty() ? theme.horizontalSeparator() : theme.horizontalSeparator(node.text);
            case TEXTURE -> {
                // WTexture needs a loaded Texture; the in-game runtime resolves it
                // from the mod's own resources, so unknown paths fall back to a label.
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(
                    node.texture.contains(":") ? node.texture : "minecraft:" + node.texture);
                meteordevelopment.meteorclient.renderer.Texture texture = id == null ? null
                    : meteordevelopment.meteorclient.renderer.Texture.readResource(
                        "/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png", false,
                        com.mojang.blaze3d.textures.FilterMode.NEAREST);
                if (texture == null) yield theme.label(node.texture.isEmpty() ? node.id : node.texture);
                yield theme.texture(node.width > 0 ? node.width : 32, node.height > 0 ? node.height : 32,
                    node.rotation, texture);
            }
            case KEYBIND -> {
                // Meteor's WKeybind is a WButton whose label is the binding; the
                // in-game runtime uses the real Keybind so it can capture input.
                meteordevelopment.meteorclient.utils.misc.Keybind bind = node.keyIsKey
                    ? meteordevelopment.meteorclient.utils.misc.Keybind.fromKeys(node.key, node.modifiers)
                    : meteordevelopment.meteorclient.utils.misc.Keybind.fromButton(node.key);
                meteordevelopment.meteorclient.gui.widgets.WKeybind keybind = theme.keybind(bind);
                keybind.action = () -> fire(edit, node.id, "change", String.valueOf(keybind));
                yield keybind;
            }
            case ITEM -> theme.item(itemStack(node.itemId, node.itemCount));
            case ENTITY -> {
                // Meteor shows an entity as its spawn egg; entities without one fall
                // back to a label with the id, which is readable inside the game.
                net.minecraft.item.Item egg = spawnEgg(node.entityId);
                yield egg == null ? theme.label(node.entityId) : theme.item(new net.minecraft.item.ItemStack(egg));
            }
            case SELECT -> {
                // GuiTheme.selectW: the button plus "(N selected)".
                String label = com.hackli.guidesigner.render.MeteorPainter.selectLabel(node);
                meteordevelopment.meteorclient.gui.widgets.pressable.WButton select = theme.button(label);
                select.action = () -> fire(edit, node.id, "select", "");
                yield select;
            }
            case CONTAINER -> theme.label(node.id); // unreachable, handled above
        };
    }

    /** Builds the item stack for an ITEM node (Meteor draws the sprite + count). */
    private static net.minecraft.item.ItemStack itemStack(String itemId, int count) {
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(
            itemId.contains(":") ? itemId : "minecraft:" + itemId);
        if (id == null) return net.minecraft.item.ItemStack.EMPTY;
        net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
        if (item == null || item == net.minecraft.item.Items.AIR) return net.minecraft.item.ItemStack.EMPTY;
        return new net.minecraft.item.ItemStack(item, Math.max(1, Math.min(64, count)));
    }

    /**
     * The spawn egg of an entity type, spelled the way vanilla names them
     * ({@code zombie} to {@code zombie_spawn_egg}). Null when there is none.
     */
    private static net.minecraft.item.Item spawnEgg(String entityId) {
        String key = entityId.contains(":") ? entityId.substring(entityId.indexOf(':') + 1) : entityId;
        if (key.endsWith("_spawn_egg")) key = key.substring(0, key.length() - "_spawn_egg".length());
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse("minecraft:" + key + "_spawn_egg");
        if (id == null) return null;
        net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
        return item == net.minecraft.item.Items.AIR ? null : item;
    }

    private static void fire(EditSession edit, String id, String event, String value) {
        if (edit == null || edit.callbacks == null) return;

        edit.callbacks.onAction(id, event, value);

        if (event.equals("click")) edit.callbacks.onButtonClick(id);
        else edit.callbacks.onValueChanged(id, value);
    }

    private static CharFilter textBoxFilter(UiNode node) {
        boolean need = node.inputFilter != InputFilter.NONE || node.maxLength > 0;
        if (!need) return null;

        return (text, c) -> {
            if (node.maxLength > 0 && text.length() >= node.maxLength) return false;
            return switch (node.inputFilter) {
                case INT -> Character.isDigit(c) || c == '-';
                case DECIMAL -> Character.isDigit(c) || c == '-' || c == '.';
                case NONE -> true;
            };
        };
    }

    private static Color parseColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        if (h.length() < 8) h = (h + "00000000").substring(0, 8);
        try {
            return new Color(
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16),
                Integer.parseInt(h.substring(6, 8), 16)
            );
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }
}
