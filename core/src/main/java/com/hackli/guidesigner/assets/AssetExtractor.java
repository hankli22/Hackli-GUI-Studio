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

package com.hackli.guidesigner.assets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * One-off extractor that turns a vanilla Minecraft client jar into the flat
 * asset set the designers need: the item/block textures plus an index that maps
 * {@code minecraft:diamond} (or just {@code diamond}) to its texture path.
 *
 * <p>Item icons are not simply "item/&lt;id&gt;.png". The real chain is:</p>
 * <pre>
 *   assets/minecraft/items/&lt;id&gt;.json   ->  { "model": { "model": "minecraft:item/diamond" } }
 *   assets/minecraft/models/&lt;model&gt;.json ->  { "parent": "...", "textures": { "layer0": "..." } }
 * </pre>
 * and the {@code layer0} (or a block model's {@code particle}/{@code all}) can
 * itself be a {@code #variable} that the parent chain defines. Block items point
 * at {@code block/<id>} models instead of {@code item/<id>}. This tool walks that
 * whole chain once and writes the answer out, so the designers only need a
 * lookup.
 *
 * <p>Run through the {@code extractMcAssets} Gradle task; it is not part of the
 * shipped application.</p>
 *
 * <p><b>Icon atlas mode.</b> A release package must not redistribute the game's
 * artwork, so there is a second mode that writes only a <i>synthetic</i> item
 * icon set instead of the extracted textures:</p>
 *
 * <pre>
 *   AssetExtractor --icons-only &lt;client.jar&gt; &lt;output-dir&gt;
 *     -&gt; icon-atlas.png   16x16 cells, one per vanilla item id
 *     -&gt; icon-atlas.tsv   "id \t column \t row \t cell size" index
 * </pre>
 *
 * <p>The atlas is generated, not extracted: each item id picks a stable colour
 * and glyph from its own hash, so the picture is identical on every machine and
 * contains no third-party artwork. It is deliberately <b>not</b> a look-alike of
 * the real sprites - it only makes an offline editor readable. The client jar is
 * still needed as the source of item ids (and only for that).</p>
 */
public final class AssetExtractor {
    private static final String MINECRAFT = "minecraft:";
    private static final String ITEMS_PREFIX = "assets/minecraft/items/";
    private static final String MODELS_PREFIX = "assets/minecraft/models/";
    private static final String TEXTURES_PREFIX = "assets/minecraft/textures/";
    private static final String METEOR_ICONS_PREFIX = "assets/meteor-client/textures/icons/gui/";
    private static final String INDEX_FILE = "item-textures.tsv";
    private static final String ATLAS_PNG = "icon-atlas.png";
    private static final String ATLAS_TSV = "icon-atlas.tsv";

    /** Side of one atlas cell, in pixels. Matches a vanilla item sprite. */
    private static final int ATLAS_CELL = 16;
    /** Atlas width in cells; the height grows with the item count. */
    private static final int ATLAS_COLUMNS = 64;

    /** Command line arguments, kept for the optional Meteor jar path. */
    private static String[] args2 = new String[0];

    /** {@code --icons-only}: write the generated icon atlas and nothing else. */
    private static boolean iconsOnly;

    private final ZipFile jar;
    private final Gson gson = new Gson();
    private final Map<String, JsonObject> modelCache = new HashMap<>();

    private AssetExtractor(ZipFile jar) {
        this.jar = jar;
    }

    public static void main(String[] args) throws IOException {
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--icons-only")) iconsOnly = true;
            else positional.add(arg);
        }
        if (positional.size() < 2) {
            System.err.println("usage: AssetExtractor [--icons-only] <client.jar> <output-dir> [meteor-client.jar]");
            System.err.println("       --icons-only  generate icon-atlas.png/.tsv instead of extracting textures");
            System.exit(2);
        }
        args2 = args;
        Path jarPath = Path.of(positional.get(0));
        Path out = Path.of(positional.get(1));

        if (!Files.isRegularFile(jarPath)) {
            System.err.println("client jar not found: " + jarPath);
            System.exit(2);
        }
        Files.createDirectories(out);

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            new AssetExtractor(zip).run(out);
        }
    }

    private void run(Path out) throws IOException {
        List<String> itemFiles = itemFiles();

        if (iconsOnly) {
            // Only the id list is needed: the atlas is drawn from hashes, not
            // from the game's artwork.
            List<String> ids = new ArrayList<>();
            for (String itemFile : itemFiles) {
                String id = idOf(itemFile);
                if (id.equals("air")) continue;
                ids.add(id);
            }
            writeIconAtlas(out, ids);
            return;
        }

        Map<String, String> index = new TreeMap<>();
        int missingModel = 0;
        int missingTexture = 0;
        Set<String> texturesToCopy = new LinkedHashSet<>();

        for (String itemFile : itemFiles) {
            String id = idOf(itemFile);
            if (id.equals("air")) continue;

            JsonObject definition = readJson(itemFile);
            if (definition == null) continue;

            String modelId = modelOf(definition);
            if (modelId == null) {
                missingModel++;
                continue;
            }

            String texture = resolveTexture(modelId);
            if (texture == null) {
                missingTexture++;
                continue;
            }

            index.put(id, texture);
            texturesToCopy.add(texture);
        }

        // Copy every texture the index points at, plus the GUI icon strip the
        // editors draw (Meteor's reset/edit/favorite buttons live in its own jar).
        int copied = 0;
        for (String texture : texturesToCopy) {
            String entryName = TEXTURES_PREFIX + texture + ".png";
            if (copyEntry(entryName, out.resolve("textures/" + texture + ".png"))) copied++;
        }

        // Item and block textures wholesale: designers may reference any of them.
        copied += copyDirectory("assets/minecraft/textures/item/", out.resolve("textures/item"));
        copied += copyDirectory("assets/minecraft/textures/block/", out.resolve("textures/block"));

        // Meteor's own GUI icons (reset / edit / favorite / copy / paste) come
        // from the Meteor jar, not the client, so they are a separate step:
        // `extractMcAssets -PmeteorJar=<meteor-client.jar>`.
        String meteorJar = System.getProperty("hackli.meteorJar");
        if (meteorJar == null && args2.length > 2) meteorJar = args2[2];
        if (meteorJar != null && Files.isRegularFile(Path.of(meteorJar))) {
            copied += copyMeteorIcons(Path.of(meteorJar), out.resolve("meteor-icons"));
        }

        Path indexPath = out.resolve(INDEX_FILE);
        StringBuilder sb = new StringBuilder("# minecraft item id -> texture path (no extension)\n");
        index.forEach((id, texture) -> sb.append(id).append('\t').append(texture).append('\n'));
        Files.writeString(indexPath, sb.toString(), StandardCharsets.UTF_8);

        System.out.println("AssetExtractor: " + index.size() + " items indexed, "
            + copied + " textures extracted");
        System.out.println("  unresolved model:   " + missingModel);
        System.out.println("  unresolved texture: " + missingTexture);
        System.out.println("  index: " + indexPath.toAbsolutePath());
    }

    /** Every {@code assets/minecraft/items/*.json} entry in the jar, sorted. */
    private List<String> itemFiles() {
        List<String> files = new ArrayList<>();
        for (ZipEntry entry : java.util.Collections.list(jar.entries())) {
            String name = entry.getName();
            if (name.startsWith(ITEMS_PREFIX) && name.endsWith(".json")) files.add(name);
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /** {@code assets/minecraft/items/diamond.json} to {@code diamond}. */
    private static String idOf(String itemFile) {
        return itemFile.substring(ITEMS_PREFIX.length(), itemFile.length() - ".json".length());
    }

    // =======================================================================
    // Generated icon atlas (no third-party artwork)
    // =======================================================================

    /**
     * Draws one synthetic icon per item id into a single sheet, plus the index
     * that maps an id to its cell: {@code icon-atlas.png} / {@code icon-atlas.tsv}
     * in {@code out}.
     *
     * <p>Everything is derived from the id's hash (colour, wash, glyph), so the
     * output is stable across machines and runs - a committed atlas never shows
     * up as a spurious diff.</p>
     */
    private void writeIconAtlas(Path out, List<String> ids) throws IOException {
        int columns = ATLAS_COLUMNS;
        int rows = Math.max(1, (ids.size() + columns - 1) / columns);
        int cell = ATLAS_CELL;

        BufferedImage atlas = new BufferedImage(columns * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        StringBuilder index = new StringBuilder("# Hackli GUI Studio - generated item icon atlas (synthetic, CC0-1.0).\n");
        index.append("# columns: id, column, row, cell size in pixels\n");

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            int column = i % columns;
            int row = i / columns;
            drawCell(atlas, column * cell, row * cell, cell, id);
            index.append(id).append('\t').append(column).append('\t').append(row)
                 .append('\t').append(cell).append('\n');
        }

        Path sheet = out.resolve(ATLAS_PNG);
        Path tsv = out.resolve(ATLAS_TSV);
        ImageIO.write(atlas, "png", sheet.toFile());
        Files.writeString(tsv, index.toString(), StandardCharsets.UTF_8);

        System.out.println("AssetExtractor: " + ids.size() + " icons generated ("
            + columns + "x" + rows + " cells of " + cell + "px)");
        System.out.println("  atlas: " + sheet.toAbsolutePath());
        System.out.println("  index: " + tsv.toAbsolutePath());
    }

    /**
     * Paints one cell: a base colour with a vertical gradient wash, a bevel and
     * one of eight simple glyphs. All four numbers come from the id's hash, so
     * two ids differ in at least colour or shape.
     */
    private static void drawCell(BufferedImage image, int x0, int y0, int cell, String id) {
        int hash = fnv1a(id);
        float hue = (hash & 0x3FF) / 1024f;                       // 1024 steps around the wheel
        float saturation = 0.30f + ((hash >>> 10) & 0x1F) / 100f; // 0.30 .. 0.61
        float brightness = 0.42f + ((hash >>> 15) & 0x1F) / 100f; // 0.42 .. 0.73
        int wash = 1 + ((hash >>> 20) & 0x3);                     // 1 .. 4
        int glyph = (hash >>> 22) & 0x7;

        int[] ramp = new int[cell];
        for (int y = 0; y < cell; y++) {
            float t = (y + 0.5f) / cell;                          // 0 at the top
            ramp[y] = hsb(hue, clamp(saturation * (1.06f - 0.22f * t)), brightness * (1.08f - 0.20f * t));
        }

        boolean[][] mask = glyphCells(cell, glyph);
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                int argb = mask[y][x]
                    ? hsb(hue, clamp(saturation * 0.88f + 0.06f), clamp(brightness * 1.34f + 0.05f))
                    : ramp[y];
                image.setRGB(x0 + x, y0 + y, argb);
            }
        }

        // One pass of a diagonal wash, so neighbouring ids do not look like
        // perfect solid squares.
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                if (mask[y][x]) continue;
                int shift = (((x + y) / wash) & 1) == 0 ? 0x06 : -0x06;
                image.setRGB(x0 + x, y0 + y, shift(image.getRGB(x0 + x, y0 + y), shift));
            }
        }

        // Bevel: a light top/left edge and a dark bottom/right edge.
        for (int i = 0; i < cell; i++) {
            image.setRGB(x0 + i, y0, blend(image.getRGB(x0 + i, y0), 0xFFFFFF, 0.18f));
            image.setRGB(x0, y0 + i, blend(image.getRGB(x0, y0 + i), 0xFFFFFF, 0.10f));
            image.setRGB(x0 + i, y0 + cell - 1, blend(image.getRGB(x0 + i, y0 + cell - 1), 0x000000, 0.22f));
            image.setRGB(x0 + cell - 1, y0 + i, blend(image.getRGB(x0 + cell - 1, y0 + i), 0x000000, 0.14f));
        }
    }

    /**
     * The eight glyph shapes, as a {@code cell x cell} mask. Every shape is
     * symmetric and stays inside the middle of the cell, so it reads as a mark on
     * the coloured square rather than as a sprite.
     */
    private static boolean[][] glyphCells(int cell, int glyph) {
        boolean[][] mask = new boolean[cell][cell];
        int center = cell / 2;
        int radius = Math.max(2, cell / 4);
        int thickness = Math.max(1, cell / 6);

        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                int dx = Math.abs(x - center);
                int dy = Math.abs(y - center);
                boolean on = switch (glyph) {
                    case 0 -> dx <= radius && dy <= radius;                       // full block
                    case 1 -> dx * dx + dy * dy <= radius * radius                  // disc
                              && dx * dx + dy * dy >= (radius - thickness) * (radius - thickness);
                    case 2 -> dx <= thickness / 2 || dy <= thickness / 2;          // plus
                    case 3 -> dx + dy <= radius;                                   // diamond
                    case 4 -> dy <= thickness / 2;                                 // bar
                    case 5 -> dx <= thickness / 2;                                 // column
                    case 6 -> dx <= radius && dy <= radius                          // checker
                              && (x / thickness + y / thickness) % 2 == 0;
                    default -> dx == dy || dx + dy == radius;                      // chevron
                };
                mask[y][x] = on;
            }
        }
        return mask;
    }

    /** FNV-1a over the id: a stable hash that does not depend on the JVM. */
    private static int fnv1a(String value) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int hsb(float hue, float saturation, float brightness) {
        int rgb = java.awt.Color.HSBtoRGB(hue, clamp(saturation), clamp(brightness));
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Brightens (positive) or darkens (negative) a channel of an ARGB pixel. */
    private static int shift(int argb, int amount) {
        int r = clampChannel(((argb >> 16) & 0xFF) + amount);
        int g = clampChannel(((argb >> 8) & 0xFF) + amount);
        int b = clampChannel((argb & 0xFF) + amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int argb, int rgb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) + (((rgb >> 16) & 0xFF) - ((argb >> 16) & 0xFF)) * factor);
        int g = (int) (((argb >> 8) & 0xFF) + (((rgb >> 8) & 0xFF) - ((argb >> 8) & 0xFF)) * factor);
        int b = (int) ((argb & 0xFF) + ((rgb & 0xFF) - (argb & 0xFF)) * factor);
        return (argb & 0xFF000000) | (clampChannel(r) << 16) | (clampChannel(g) << 8) | clampChannel(b);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Copies {@code assets/meteor-client/textures/icons/gui/*.png} out of a Meteor
     * jar, which is what the reset / edit / favourite buttons draw.
     *
     * @return the number of icons copied
     */
    private static int copyMeteorIcons(Path meteorJar, Path target) throws IOException {
        int count = 0;
        try (ZipFile icons = new ZipFile(meteorJar.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(icons.entries())) {
                String name = entry.getName();
                if (!name.startsWith(METEOR_ICONS_PREFIX) || !name.endsWith(".png")) continue;
                String relative = name.substring(METEOR_ICONS_PREFIX.length());
                if (relative.contains("/")) continue;          // flat directory only
                Files.createDirectories(target);
                try (InputStream in = icons.getInputStream(entry)) {
                    Files.copy(in, target.resolve(relative),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        }
        return count;
    }

    /** Walks the item definition to the model id it uses by default. */
    private String modelOf(JsonObject definition) {
        JsonElement model = definition.get("model");
        if (model == null || !model.isJsonObject()) return null;
        return modelId(model.getAsJsonObject());
    }

    /**
     * Item definitions nest: {@code minecraft:model} has a {@code model} field,
     * while {@code minecraft:condition} / {@code minecraft:select} hold nested
     * definitions ({@code on_false}, {@code cases}, ...). Take the first model we
     * can find; the "broken"/"empty" variants are close enough for a design tool.
     */
    private String modelId(JsonObject node) {
        JsonElement direct = node.get("model");
        if (direct != null && direct.isJsonPrimitive()) return strip(direct.getAsString());

        for (String key : new String[]{"on_false", "on_true", "fallback", "entries", "cases"}) {
            JsonElement nested = node.get(key);
            if (nested == null) continue;
            String found = firstModelIn(nested);
            if (found != null) return found;
        }
        return null;
    }

    private String firstModelIn(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") || object.has("model")) {
                String found = modelId(object);
                if (found != null) return found;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String found = firstModelIn(entry.getValue());
                if (found != null) return found;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String found = firstModelIn(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Resolves a model id to a texture path, following the parent chain for
     * {@code #variable} references. Prefers {@code layer0} (the flat item icon),
     * then {@code particle} / the first texture the model defines.
     */
    private String resolveTexture(String modelId) {
        Map<String, String> textures = new HashMap<>();
        collectTextures(modelId, textures, 0);

        for (String key : new String[]{"layer0", "particle", "all", "top", "side", "texture", "front"}) {
            String value = follow(textures.get(key), textures, 0);
            if (value != null) return value;
        }
        for (String value : textures.values()) {
            String resolved = follow(value, textures, 0);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private void collectTextures(String modelId, Map<String, String> out, int depth) {
        if (depth > 8) return;
        JsonObject model = model(modelId);
        if (model == null) return;

        JsonElement parent = model.get("parent");
        if (parent != null && parent.isJsonPrimitive()) collectTextures(strip(parent.getAsString()), out, depth + 1);

        JsonElement textures = model.get("textures");
        if (textures != null && textures.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : textures.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                // Child models win over parents.
                out.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    /** Follows {@code #variable} references until a concrete resource path appears. */
    private String follow(String value, Map<String, String> textures, int depth) {
        if (value == null || depth > 8) return null;
        if (!value.startsWith("#")) return strip(value);

        String target = textures.get(value.substring(1));
        if (target == null) return null;
        return follow(target, textures, depth + 1);
    }

    private JsonObject model(String modelId) {
        String id = strip(modelId);
        return modelCache.computeIfAbsent(id, key -> {
            JsonObject found = readJson(MODELS_PREFIX + key + ".json");
            if (found == null && !key.contains("/")) found = readJson(MODELS_PREFIX + "item/" + key + ".json");
            return found;
        });
    }

    private JsonObject readJson(String entryName) {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null) return null;
        try (InputStream in = jar.getInputStream(entry);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean copyEntry(String entryName, Path target) throws IOException {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null) return false;
        Files.createDirectories(target.getParent());
        try (InputStream in = jar.getInputStream(entry)) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private int copyDirectory(String prefix, Path target) throws IOException {
        int count = 0;
        for (ZipEntry entry : java.util.Collections.list(jar.entries())) {
            String name = entry.getName();
            if (!name.startsWith(prefix) || !name.endsWith(".png")) continue;
            String relative = name.substring(prefix.length());
            if (copyEntry(name, target.resolve(relative))) count++;
        }
        return count;
    }

    private static String strip(String id) {
        String value = id.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(MINECRAFT) ? value.substring(MINECRAFT.length()) : value;
    }
}
