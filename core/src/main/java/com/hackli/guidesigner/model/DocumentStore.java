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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where everything lives on disk, as a portable data folder next to the
 * application:
 *
 * <ul>
 *   <li>{@code projects/}   - designs you are working on in the designer.</li>
 *   <li>{@code layouts/}    - runtime layout overrides saved by players in edit mode.</li>
 *   <li>{@code export/}     - generated Java sources.</li>
 *   <li>{@code templates/}  - reusable widget subtrees.</li>
 * </ul>
 *
 * <p><b>Portable by default.</b> The standalone editor keeps its data in
 * {@code <working directory>/data} - and {@code run-gl.cmd} / {@code run-editor.cmd}
 * start it from their own folder, so everything sits next to the software and
 * copying that folder copies every design with it. Nothing is written to the
 * user's home directory, which also keeps the editor usable from a read-only or
 * USB location.</p>
 *
 * <p>The data root is decided in this order:</p>
 * <ol>
 *   <li>{@code -Dhackli.home=&lt;dir&gt;} - explicit override (what the launch
 *       scripts pass, and how you point an editor at a shared folder)</li>
 *   <li>{@code <working directory>/data} - the portable default</li>
 * </ol>
 *
 * <p>The Minecraft mod does <em>not</em> use this default: it calls
 * {@link #init(Path)} with the instance's {@code config/hackli-gui-studio}, which
 * is where Fabric mods are expected to keep their files. Point both at the same
 * folder with {@code -Dhackli.home} if you want the in-game designer and the
 * standalone editor to share one project list.</p>
 */
public class DocumentStore {
    /** System property that overrides the whole data root. */
    public static final String HOME_PROPERTY = "hackli.home";

    /** Portable default, relative to the working directory. */
    public static final String DEFAULT_DIR = "data";

    /** Directory name used before the project was renamed to Hackli GUI Studio. */
    private static final String LEGACY_NAME = "hackli-gui-designer";

    private static Path base = defaultBase();

    private DocumentStore() {}

    /** {@code -Dhackli.home} when set, otherwise {@code ./data}. */
    private static Path defaultBase() {
        String override = System.getProperty(HOME_PROPERTY);
        if (override != null && !override.isBlank()) {
            try {
                return Path.of(override.trim());
            } catch (RuntimeException e) {
                // A malformed override must not stop the application from starting;
                // fall back to the portable default.
            }
        }
        return Path.of(DEFAULT_DIR);
    }

    /**
     * Configures where everything lives, overriding the portable default.
     *
     * <p>The Minecraft mod calls this with {@code config/hackli-gui-studio}. The
     * standalone editor leaves it alone and gets {@code ./data} (or
     * {@code -Dhackli.home}).</p>
     */
    public static void init(Path baseDir) {
        base = baseDir;
    }

    /**
     * Renames a pre-rename directory ({@code hackli-gui-studio}) to the current
     * name, so designs, player layouts and templates saved by an older build are
     * still found.
     *
     * <p>Only acts when the old directory exists <em>and</em> the new one does
     * not, so it can never clobber current data, and it is safe to call on every
     * start. A failed rename (locked file, permissions, cross-device) is ignored:
     * the worst case is the old behaviour, where the old folder is simply not
     * picked up.</p>
     *
     * @return true when a migration actually happened
     */
    public static boolean migrateLegacyDir() {
        try {
            Path target = base.toAbsolutePath().normalize();
            Path legacy = target.resolveSibling(LEGACY_NAME);
            if (Files.isDirectory(target) || !Files.isDirectory(legacy)) return false;
            if (target.getParent() == null) return false;
            Files.createDirectories(target.getParent());
            Files.move(legacy, target);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static Path baseDir() {
        return base;
    }

    public static Path projectsDir() {
        return dir("projects");
    }

    public static Path layoutsDir() {
        return dir("layouts");
    }

    public static Path exportDir() {
        return dir("export");
    }

    /** Saved reusable component templates. */
    public static Path templatesDir() {
        return dir("templates");
    }

    private static Path dir(String name) {
        Path dir = baseDir().resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory " + dir, e);
        }
        return dir;
    }

    // ================= Projects (designer) =================

    public static Path saveProject(UiDocument doc) {
        Path path = projectsDir().resolve(sanitize(doc.name) + ".json");
        write(path, doc.toJson());
        return path;
    }

    public static UiDocument loadProject(String name) {
        return load(projectsDir().resolve(sanitize(name) + ".json"));
    }

    public static boolean deleteProject(String name) {
        try {
            return Files.deleteIfExists(projectsDir().resolve(sanitize(name) + ".json"));
        } catch (IOException e) {
            return false;
        }
    }

    public static List<String> listProjects() {
        return list(projectsDir());
    }

    // ================= Layouts (runtime overrides) =================

    public static Path saveLayout(UiDocument doc) {
        Path path = layoutsDir().resolve(sanitize(doc.name) + ".json");
        write(path, doc.toJson());
        return path;
    }

    public static UiDocument loadLayout(String name) {
        return load(layoutsDir().resolve(sanitize(name) + ".json"));
    }

    // ================= Export =================

    public static Path writeExport(String fileName, String content) {
        Path path = exportDir().resolve(fileName);
        write(path, content);
        return path;
    }

    // ================= Helpers =================

    private static UiDocument load(Path path) {
        if (!Files.exists(path)) return null;
        try {
            return UiDocument.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
    }

    private static List<String> list(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
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

    /** Makes an arbitrary string safe as a file name. */
    public static String sanitize(String name) {
        return name == null ? "gui" : name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /**
     * A short, shareable spelling of {@code path}: relative to the config base
     * directory when it lives under it, otherwise just the file name.
     *
     * <p>Use this for anything the user <em>sees</em> (status bars, log lines,
     * chat). It keeps the design tool from printing the absolute path of the
     * machine it runs on - those end up in screenshots and bug reports, and they
     * say more about the reporter's disk than about the design.</p>
     */
    public static String displayPath(Path path) {
        if (path == null) return "";
        try {
            Path absolute = path.toAbsolutePath().normalize();
            Path root = base.toAbsolutePath().normalize();
            if (absolute.startsWith(root)) return root.relativize(absolute).toString();
            Path name = absolute.getFileName();
            return name == null ? absolute.toString() : name.toString();
        } catch (RuntimeException e) {
            return path.toString();
        }
    }

    /** Makes an arbitrary string safe as a Java identifier part. */
    public static String toJava(String name) {
        if (name == null || name.isEmpty()) return "Gui";
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            } else {
                capitalize = true;
            }
        }
        if (sb.length() == 0) return "Gui";
        if (Character.isDigit(sb.charAt(0))) sb.insert(0, "Gui");
        return sb.toString();
    }
}
