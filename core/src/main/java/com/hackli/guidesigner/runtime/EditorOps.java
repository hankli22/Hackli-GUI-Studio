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

package com.hackli.guidesigner.runtime;

import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing operations shared by every editor (desktop, in-game, OpenGL), so the
 * keyboard shortcuts behave identically everywhere.
 */
public final class EditorOps {
    /** How far a pasted or duplicated widget is offset in free layout. */
    public static final double PASTE_OFFSET = 12;

    private EditorOps() {}

    /** Independent deep copy of a node, with fresh ids for the whole subtree. */
    public static UiNode copyOf(UiNode node, UiNode scope) {
        return node == null ? null : node.copyWithFreshIds(scope);
    }

    /**
     * Inserts a copied tree into {@code parent} and returns the new node, or
     * {@code null} when there is nothing to paste or the parent is invalid.
     */
    public static UiNode paste(UiDocument doc, UiNode clipboard, UiNode parent) {
        if (doc == null || clipboard == null || parent == null) return null;

        UiNode copy = clipboard.copyWithFreshIds(doc.root);
        if (parent.layout == LayoutMode.ABSOLUTE) {
            copy.x += PASTE_OFFSET;
            copy.y += PASTE_OFFSET;
        }
        parent.child(copy);
        return copy;
    }

    /** Convenience: copy then paste into the paste target of the selection. */
    public static UiNode duplicate(UiDocument doc, UiNode node) {
        if (doc == null || node == null || node.parent == null) return null;
        UiNode copy = node.copyWithFreshIds(doc.root);
        if (node.parent.layout == LayoutMode.ABSOLUTE) {
            copy.x += PASTE_OFFSET;
            copy.y += PASTE_OFFSET;
        }
        node.parent.child(copy);
        return copy;
    }

    /** The container a paste should land in, given the current selection. */
    public static UiNode pasteTarget(UiDocument doc, List<UiNode> selection) {
        if (doc == null) return null;
        if (selection != null && !selection.isEmpty()) {
            UiNode sel = selection.get(selection.size() - 1);
            if (sel.type.isContainer) return sel;
            if (sel.parent != null) return sel.parent;
        }
        return doc.root;
    }

    /**
     * Removes every selected node that has a parent (the root is never removed).
     *
     * @return how many nodes were removed
     */
    public static int delete(UiDocument doc, List<UiNode> selection) {
        if (doc == null || selection == null || selection.isEmpty()) return 0;

        int removed = 0;
        for (UiNode node : new ArrayList<>(selection)) {
            if (node == doc.root || node.parent == null) continue;
            node.parent.children.remove(node);
            removed++;
        }
        return removed;
    }
}
