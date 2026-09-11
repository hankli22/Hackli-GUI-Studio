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

import com.hackli.guidesigner.model.UiDocument;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Snapshot-based undo/redo for designs. Consecutive edits that share the same
 * {@code editKey} within a small time window are merged into a single undo
 * step (so typing in a field or dragging a node is one step, not one per
 * keystroke/frame). Pure JVM - shared by the Minecraft mod and the desktop
 * editor.
 */
public class Undoer {
    private static final int LIMIT = 100;
    private static final long MERGE_WINDOW_MS = 800;

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();

    private String current;
    private String editKey;
    private long lastTime;

    /** Call when a new document is opened/created. */
    public void reset(UiDocument doc) {
        current = doc == null ? null : doc.toJson();
        undo.clear();
        redo.clear();
        editKey = null;
    }

    /**
     * Call after every mutation.
     *
     * @param editKey stable key for the edit "session" (e.g. a field key or
     *                "drag:<node-id>"); pass {@code null} to force a new undo
     *                step for this mutation.
     */
    public void record(UiDocument doc, String editKey) {
        String json = doc.toJson();
        if (current != null && json.equals(current)) return;

        long now = System.currentTimeMillis();
        boolean merge = editKey != null && editKey.equals(this.editKey) && now - lastTime < MERGE_WINDOW_MS;

        if (!merge) {
            if (current != null) {
                undo.push(current);
                if (undo.size() > LIMIT) undo.removeLast();
            }
            redo.clear();
        }

        current = json;
        this.editKey = editKey;
        this.lastTime = now;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** @return the document to replace the current one with, or {@code null}. */
    public UiDocument undo() {
        if (undo.isEmpty()) return null;
        redo.push(current);
        current = undo.pop();
        return UiDocument.fromJson(current);
    }

    /** @return the document to replace the current one with, or {@code null}. */
    public UiDocument redo() {
        if (redo.isEmpty()) return null;
        undo.push(current);
        current = redo.pop();
        return UiDocument.fromJson(current);
    }
}
