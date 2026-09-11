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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persisted reusable component templates: each template is a widget subtree
 * that can be placed onto the canvas from the palette. Templates live in
 * {@code <base>/templates/} and are shared between the Minecraft mod and the
 * desktop editor.
 */
public class TemplateStore {
    private TemplateStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Template name + serialized root node. */
    public static class Template {
        public String name;
        public UiNode root;

        public Template() {}

        public Template(String name, UiNode root) {
            this.name = name;
            this.root = root;
        }
    }

    public static Path save(String name, UiNode root) {
        Path path = DocumentStore.templatesDir().resolve(DocumentStore.sanitize(name) + ".json");
        try {
            Files.writeString(path, GSON.toJson(new Template(name, root)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write template " + path, e);
        }
        return path;
    }

    public static Template load(String name) {
        Path path = DocumentStore.templatesDir().resolve(DocumentStore.sanitize(name) + ".json");
        if (!Files.exists(path)) return null;
        try {
            Template t = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Template.class);
            if (t == null || t.root == null) return null;
            linkParents(t.root, null);
            return t;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(DocumentStore.templatesDir().resolve(DocumentStore.sanitize(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    public static List<String> list() {
        try (Stream<Path> stream = Files.list(DocumentStore.templatesDir())) {
            List<String> names = new ArrayList<>();
            stream.filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString())
                .map(n -> n.substring(0, n.length() - 5))
                .sorted()
                .forEach(names::add);
            return names;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static void linkParents(UiNode node, UiNode parent) {
        node.parent = parent;
        for (UiNode child : node.children) linkParents(child, node);
    }
}
