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

package com.hackli.guidesigner.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hackli.guidesigner.model.DocumentStore;
import com.hackli.guidesigner.model.UiDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports a design as pure JSON data (no Hackli dependency) plus the
 * accompanying JSON Schema for validation and editor tooling.
 * A design exported here can be re-imported by any tool that understands
 * the schema - including the other exporters of this project.
 */
public class JsonExporter {
    public static final String SCHEMA_FILE = "hackli-gui-document.schema.json";

    /** JSON Schema (draft 2020-12) describing a Hackli GUI Studio document. */
    public static final String SCHEMA = """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "$id": "hackli-gui-document.schema.json",
          "title": "Hackli GUI Studio document",
          "description": "Layout document of Hackli GUI Studio - a plain data format usable by any tool.",
          "type": "object",
          "required": ["name", "root"],
          "properties": {
            "name": { "type": "string", "description": "GUI name, also used as file name / class name." },
            "version": { "type": "string", "description": "Document format version." },
            "$schema": { "type": "string", "description": "Optional reference to this schema." },
            "root": { "$ref": "#/$defs/node", "description": "Root container of the layout tree." }
          },
          "$defs": {
            "node": {
              "type": "object",
              "required": ["id", "type"],
              "properties": {
                "id": { "type": "string", "description": "Unique-ish id within its parent, used for callbacks." },
                "type": {
                  "enum": ["CONTAINER", "LABEL", "BUTTON", "TEXTBOX", "CHECKBOX", "SLIDER", "DROPDOWN", "SEPARATOR"]
                },
                "layout": { "enum": ["FLOW", "ABSOLUTE"], "description": "How this container arranges its children." },
                "orientation": { "enum": ["VERTICAL", "HORIZONTAL"], "description": "Flow direction (Meteor WVerticalList / WHorizontalList)." },
                "visible": { "type": "boolean" },
                "anchorX": { "enum": ["LEFT", "CENTER", "RIGHT"] },
                "anchorY": { "enum": ["TOP", "MIDDLE", "BOTTOM"] },
                "x": { "type": "number", "description": "Offset from the anchor point, in GUI pixels." },
                "y": { "type": "number" },
                "width": { "type": "number", "description": "Fixed width in GUI pixels; 0/negative = auto." },
                "height": { "type": "number", "description": "Fixed height in GUI pixels; 0/negative = auto." },
                "text": { "type": "string" },
                "placeholder": { "type": "string", "description": "Text box placeholder." },
                "checked": { "type": "boolean", "description": "Check box state." },
                "value": { "type": "number", "description": "Slider value." },
                "min": { "type": "number", "description": "Slider minimum." },
                "max": { "type": "number", "description": "Slider maximum." },
                "options": { "type": "array", "items": { "type": "string" }, "description": "Dropdown entries." },
                "selected": { "type": ["string", "null"], "description": "Selected dropdown entry." },
                "textColor": { "type": "string", "description": "#RRGGBB or #RRGGBBAA; empty = theme default." },
                "textAlign": { "enum": ["LEFT", "CENTER", "RIGHT"] },
                "maxLength": { "type": "integer", "description": "Maximum input length; 0 = unlimited." },
                "inputFilter": { "enum": ["NONE", "INT", "DECIMAL"] },
                "handler": { "type": "string", "description": "Backend callback method name." },
                "cell": {
                  "type": "object",
                  "description": "Meteor Cell settings inside the parent container.",
                  "properties": {
                    "padTop": { "type": "number" },
                    "padRight": { "type": "number" },
                    "padBottom": { "type": "number" },
                    "padLeft": { "type": "number" },
                    "expandX": { "type": "boolean", "description": "Share leftover width in a horizontal list." },
                    "alignX": { "enum": ["LEFT", "CENTER", "RIGHT"] },
                    "alignY": { "enum": ["TOP", "MIDDLE", "BOTTOM"] },
                    "minWidth": { "type": "number" },
                    "group": { "type": "string" }
                  }
                },
                "children": { "type": "array", "items": { "$ref": "#/$defs/node" } }
              }
            }
          }
        }
        """;

    private JsonExporter() {}

    /** Pretty JSON data with a {@code $schema} reference and no Hackli-specific code. */
    public static String generate(UiDocument doc) {
        JsonObject json = JsonParser.parseString(doc.toJson()).getAsJsonObject();
        json.addProperty("$schema", SCHEMA_FILE);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
    }

    /** Writes the design JSON + the schema file into the export directory. */
    public static Path export(UiDocument doc) {
        writeSchemaIfMissing();

        String fileName = DocumentStore.sanitize(doc.name) + ".json";
        Path path = DocumentStore.exportDir().resolve(fileName);
        try {
            Files.writeString(path, generate(doc) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
        return path;
    }

    public static void writeSchemaIfMissing() {
        Path schema = DocumentStore.exportDir().resolve(SCHEMA_FILE);
        if (Files.exists(schema)) return;
        try {
            Files.writeString(schema, SCHEMA + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write schema " + schema, e);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
}
