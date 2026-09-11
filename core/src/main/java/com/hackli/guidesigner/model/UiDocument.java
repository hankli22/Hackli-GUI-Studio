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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A whole GUI design: a name plus its layout tree. Serialized to JSON with
 * Gson (transient fields are skipped automatically).
 */
public class UiDocument {
    public static final String CURRENT_VERSION = "1";

    /** Name of the GUI, also used as the file name and the generated class name. */
    public String name;

    /** Format version of this document. */
    public String version = CURRENT_VERSION;

    /** The root container. It is always a CONTAINER with LayoutMode.ABSOLUTE. */
    public UiNode root;

    public UiDocument() {}

    public UiDocument(String name) {
        this.name = name;
        this.root = UiNode.container("root", 480, 320).text(name).layout(LayoutMode.ABSOLUTE);
    }

    // ================= JSON =================

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static UiDocument fromJson(String json) {
        UiDocument doc = GSON.fromJson(json, UiDocument.class);
        if (doc == null || doc.root == null) return null;

        linkParents(doc.root, null);
        return doc;
    }

    private static void linkParents(UiNode node, UiNode parent) {
        node.parent = parent;
        for (UiNode child : node.children) linkParents(child, node);
    }

    // ================= Tree helpers =================

    public UiNode find(String id) {
        return root == null ? null : root.find(id);
    }

    public void remove(UiNode node) {
        if (root != null) root.remove(node);
    }

    /** All nodes that produce callbacks (buttons and value widgets), in tree order. */
    public List<UiNode> interactiveNodes() {
        List<UiNode> list = new ArrayList<>();
        collectInteractive(root, list);
        return list;
    }

    private void collectInteractive(UiNode node, List<UiNode> list) {
        if (node.type == UiType.BUTTON || node.type == UiType.TEXTBOX || node.type == UiType.CHECKBOX
            || node.type == UiType.SLIDER || node.type == UiType.DROPDOWN) {
            list.add(node);
        }

        for (UiNode child : node.children) collectInteractive(child, list);
    }
}
