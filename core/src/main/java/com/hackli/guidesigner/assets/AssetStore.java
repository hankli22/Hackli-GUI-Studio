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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Locates the images the designers draw: texture PNGs (extracted from a vanilla
 * client jar by {@link AssetExtractor}) and Meteor's own GUI icons.
 *
 * <p>Pure filesystem work, shared by both back ends - they only differ in how
 * they turn a {@link Path} into something the painter can draw.</p>
 *
 * <p>Roots it looks in, in order:</p>
 * <ol>
 *   <li>{@code -Dhackli.assets=&lt;dir&gt;} (explicit override)</li>
 *   <li>{@code <root>/build/mc-assets} walked up from the working directory</li>
 *   <li>{@code hackli-gui-studio/mc-assets} in the user home</li>
 * </ol>
 *
 * <p>Extracted game textures always win. When they are absent the store falls
 * back to the <em>generated</em> icon atlas ({@code icon-atlas.png} +
 * {@code icon-atlas.tsv}, drawn from item id hashes by {@link AssetExtractor}
 * {@code --icons-only}). That keeps a release package self-contained without
 * redistributing any of the game's artwork, and it is the only reason
 * {@link #iconOf(String)} exists.</p>
 */
public final class AssetStore {
    /** System property that points straight at an extracted asset directory. */
    public static final String ASSETS_PROPERTY = "hackli.assets";

    /** Extracted vanilla assets (textures/, item-textures.tsv). */
    private static final String EXTRACTED_DIR = "build/mc-assets";
    private static final String USER_DIR = ".hackli-gui-studio/mc-assets";

    private static final String EXTRACTED_INDEX = "item-textures.tsv";
    private static final String ATLAS_IMAGE = "icon-atlas.png";
    private static final String ATLAS_INDEX = "icon-atlas.tsv";

    /** Where the generated atlas is committed, relative to the project root. */
    private static final String ATLAS_DIR = "assets/icons";

    /**
     * A cell in the generated icon atlas: the image lives in
     * {@code icon-atlas.png} at {@code (column, row)} of a {@code cell} sized grid.
     */
    public record IconTile(int column, int row, int cell) {
        /** Left edge of the cell, in atlas pixels. */
        public int x() {
            return column * cell;
        }

        /** Top edge of the cell, in atlas pixels. */
        public int y() {
            return row * cell;
        }
    }

    private static final AssetStore INSTANCE = new AssetStore();

    private final Map<String, String> itemTextures = new HashMap<>();
    private final Map<String, IconTile> iconAtlas = new HashMap<>();
    private Path root;
    private Path atlasRoot;
    private boolean loaded;

    private AssetStore() {}

    public static AssetStore get() {
        return INSTANCE;
    }

    /** Where the textures live, or {@code null} when nothing was found. */
    public Path root() {
        ensureLoaded();
        return root;
    }

    /** True when a texture root (extracted or atlas) is available. */
    public boolean available() {
        return root() != null;
    }

    /**
     * True only when the <em>extracted</em> game textures are present. The
     * exporters use this: the generated atlas knows which item ids exist but
     * nothing about the game's registries, so it cannot back a claim like
     * "this id is a real {@code Items} constant".
     */
    public boolean extractedTextures() {
        ensureLoaded();
        return !itemTextures.isEmpty();
    }

    /** Number of indexed items (0 when no index was found). */
    public int itemCount() {
        ensureLoaded();
        return itemTextures.size();
    }

    /**
     * Item id to texture path, e.g. {@code diamond} or {@code minecraft:stone} to
     * {@code block/stone}. Returns {@code null} for unknown items.
     */
    public String textureOfItem(String itemId) {
        ensureLoaded();
        String key = lookupKey(itemId);
        if (key == null) return null;

        String direct = itemTextures.get(key);
        if (direct != null) return direct;
        if (key.startsWith("item/") || key.startsWith("block/")) return key;
        return null;
    }

    /**
     * The generated atlas cell for an item id, or {@code null} when there is no
     * atlas or the id is not in it. Same spellings as {@link #textureOfItem}.
     */
    public IconTile iconOf(String itemId) {
        ensureLoaded();
        String key = lookupKey(itemId);
        if (key == null) return null;

        IconTile tile = iconAtlas.get(key);
        if (tile != null) return tile;
        if (key.startsWith("item/") || key.startsWith("block/")) {
            return iconAtlas.get(key.substring(key.indexOf('/') + 1));
        }
        return null;
    }

    /**
     * The {@code icon-atlas.png} the tiles of {@link #iconOf(String)} live in, or
     * {@code null} when no generated atlas was found.
     */
    public Path iconAtlas() {
        ensureLoaded();
        return atlasRoot == null ? null : atlasRoot.resolve(ATLAS_IMAGE);
    }

    /** Shared spelling normalisation for id lookups (index and atlas). */
    private static String lookupKey(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        String key = itemId.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        // Tolerate "item/xxx" and "textures/item/xxx.png" spellings as well.
        if (key.startsWith("textures/")) key = key.substring("textures/".length());
        if (key.endsWith(".png")) key = key.substring(0, key.length() - ".png".length());
        return key.isEmpty() ? null : key;
    }

    /**
     * The spawn egg item for an entity type, which is the icon Meteor shows for
     * entities ({@code EntityTypeListSettingScreen.addEntityType}).
     *
     * <p>Vanilla names the egg after the entity ({@code zombie} ->
     * {@code zombie_spawn_egg}), so the lookup is the index entry with the
     * {@code _spawn_egg} suffix (or the generated atlas entry, when the release
     * package ships no extracted textures). Entities without an egg (the player, most
     * vehicles and projectiles) return {@code null} and get the placeholder.</p>
     */
    public String spawnEggOf(String entityId) {
        ensureLoaded();
        if (entityId == null || entityId.isEmpty()) return null;

        String key = lookupKey(entityId);
        if (key == null) return null;
        if (key.endsWith("_spawn_egg")) return key;

        String egg = key + "_spawn_egg";
        // The extracted index is authoritative, but the generated atlas knows the
        // same ids - so an entity still gets an icon in a release package, which
        // ships no extracted textures at all.
        if (itemTextures.containsKey(egg) || iconAtlas.containsKey(egg)) return egg;
        return null;
    }

    /** True when the item has an entry in the extracted index. */
    public boolean hasItem(String itemId) {
        return textureOfItem(itemId) != null;
    }

    /**
     * Whether {@code Items.<CONSTANT>} exists in vanilla, derived from the index
     * instead of a hand written list: every entry in {@code assets/minecraft/items/}
     * is a vanilla item, and the constant is its upper case id.
     *
     * <p>The exporter uses this to decide between the readable {@code Items.DIAMOND}
     * spelling and a {@code Registries.ITEM.get(...)} lookup, so generated code can
     * never mention a constant that does not exist.</p>
     */
    public boolean isVanillaItemConstant(String constant) {
        ensureLoaded();
        if (constant == null || constant.isEmpty()) return false;
        // The atlas lists ids but not registries, so only a real extraction can
        // answer this: a wrong "yes" would emit a constant that does not exist.
        if (itemTextures.isEmpty()) return false;
        String id = constant.toLowerCase(Locale.ROOT);
        // A few constants are named after the item but not spelled like it.
        if (id.equals("snow_golem_spawn_egg")) id = "snow_golem_spawn_egg";
        return itemTextures.containsKey(id);
    }

    /**
     * Resolves a texture reference to a file. Accepts a texture path
     * ({@code item/diamond}, {@code block/stone}, {@code textures/item/x.png}),
     * a qualified texture name ({@code minecraft:item/diamond}) and a plain item
     * id ({@code diamond}, {@code minecraft:diamond_sword}) - the latter is looked
     * up in the extracted index first, so the game's own resolution rules apply.
     */
    public Path texturePath(String reference) {
        ensureLoaded();
        if (root == null || reference == null || reference.isEmpty()) return null;

        String path = normalize(reference);
        // A colon means this is not a plain texture path (an unresolved namespace
        // or a mod id); building a Path from it would throw on Windows.
        if (path.indexOf(':') < 0) {
            Path candidate = root.resolve("textures").resolve(path + ".png");
            if (Files.isRegularFile(candidate)) return candidate;
        }

        // Not a texture path: try it as an item id (the index knows block items
        // and their real texture, e.g. stone -> block/stone).
        String viaItem = textureOfItem(reference);
        if (viaItem != null && !viaItem.equals(path) && viaItem.indexOf(':') < 0) {
            Path candidate = root.resolve("textures").resolve(viaItem + ".png");
            if (Files.isRegularFile(candidate)) return candidate;
        }

        // Nothing extracted: the generated atlas may still cover this id, but a
        // single-cell file has to be produced first. Back ends that can slice a
        // sheet ask iconOf()/iconAtlas() and skip this entirely; the rest call
        // iconAtlasCell() and get a real file.
        return null;
    }

    /**
     * Writes one generated atlas cell out as a standalone 16x16 PNG and returns
     * it, or {@code null} when the atlas has no cell for {@code reference}.
     *
     * <p>Bridges the "one atlas file" packaging to back ends that can only draw a
     * whole image. The slices are cached in the system temp directory, keyed by
     * atlas tile, so the cost is paid once per run.</p>
     */
    public Path iconAtlasCell(String reference) {
        ensureLoaded();
        IconTile tile = iconOf(reference);
        Path atlas = iconAtlas();
        if (tile == null || atlas == null) return null;

        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir", "."), "hackli-gui-studio-icons");
            Files.createDirectories(dir);
            Path cell = dir.resolve("cell-" + tile.column() + "-" + tile.row() + "-" + tile.cell() + ".png");
            if (Files.isRegularFile(cell)) return cell;

            BufferedImage sheet = ImageIO.read(atlas.toFile());
            if (sheet == null) return null;
            int size = tile.cell();
            if (tile.x() + size > sheet.getWidth() || tile.y() + size > sheet.getHeight()) return null;

            BufferedImage slice = sheet.getSubimage(tile.x(), tile.y(), size, size);
            ImageIO.write(slice, "png", cell.toFile());
            return cell;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Strips a leading slash, a {@code textures/} (or {@code assets/minecraft/textures/})
     * prefix and the {@code .png} suffix, leaving a path relative to
     * {@code textures/}. A namespace is only dropped when the reference names an
     * actual texture path ({@code minecraft:item/diamond}); a bare namespaced item
     * id ({@code minecraft:diamond_sword}) is left intact so the index can match it.
     */
    private static String normalize(String reference) {
        String path = reference.trim();
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("assets/minecraft/textures/")) {
            path = path.substring("assets/minecraft/textures/".length());
        } else if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        } else if (path.startsWith("minecraft:")) {
            String withoutNamespace = path.substring("minecraft:".length());
            if (withoutNamespace.startsWith("textures/")) {
                path = withoutNamespace.substring("textures/".length());
            } else if (withoutNamespace.startsWith("item/") || withoutNamespace.startsWith("block/")) {
                path = withoutNamespace;
            }
        }
        if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
        return path;
    }

    /** Meteor's own GUI icon (reset / edit / copy / favorite_yes ...), or null. */
    public Path meteorIcon(String name) {
        ensureLoaded();
        if (root == null || name == null || name.isEmpty()) return null;
        // Icon names are bare file names ("reset"); anything namespaced or with a
        // separator is a texture reference and must not become a path here.
        if (name.indexOf(':') >= 0 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return null;

        String file = name.endsWith(".png") ? name : name + ".png";
        Path candidate = root.resolve("meteor-icons").resolve(file);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    // ================= internals =================

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        String override = System.getProperty(ASSETS_PROPERTY);
        List<Path> candidates = new ArrayList<>();
        if (override != null && !override.isBlank()) candidates.add(Paths.get(override));

        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            candidates.add(dir.resolve(EXTRACTED_DIR));
            candidates.add(dir.resolve(ATLAS_DIR));
        }
        Path home = Paths.get(System.getProperty("user.home", "."));
        candidates.add(home.resolve(USER_DIR));
        candidates.add(home.resolve(USER_DIR).resolve("icons"));

        // Extracted game textures win: they are the real artwork, the atlas is
        // only the redistributable stand-in.
        for (Path candidate : candidates) {
            Path index = candidate.resolve(EXTRACTED_INDEX);
            if (Files.isRegularFile(index)) {
                root = candidate;
                loadIndex(index);
                break;
            }
        }
        if (root == null) {
            // No index: still usable when someone points us at a bare texture folder.
            for (Path candidate : candidates) {
                if (Files.isDirectory(candidate.resolve("textures"))) {
                    root = candidate;
                    break;
                }
            }
        }

        // The generated atlas is independent of that: it can be the only thing
        // that was shipped.
        for (Path candidate : candidates) {
            Path index = candidate.resolve(ATLAS_INDEX);
            Path image = candidate.resolve(ATLAS_IMAGE);
            if (Files.isRegularFile(index) && Files.isRegularFile(image)) {
                atlasRoot = candidate;
                loadIconAtlas(index);
                break;
            }
        }
        // A root set explicitly to the atlas folder should also serve images.
        if (root == null && atlasRoot != null) root = atlasRoot;
    }

    private void loadIconAtlas(Path index) {
        try (BufferedReader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\t");
                if (parts.length < 4) continue;
                try {
                    iconAtlas.put(parts[0], new IconTile(
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim())));
                } catch (NumberFormatException ignored) {
                    // A malformed row just loses that one icon.
                }
            }
        } catch (IOException e) {
            // A damaged atlas means no generated icons; textures still work.
            iconAtlas.clear();
        }
    }

    private void loadIndex(Path index) {
        try (BufferedReader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                itemTextures.put(line.substring(0, tab), line.substring(tab + 1).trim());
            }
        } catch (IOException e) {
            // A damaged index just means no resolved items; textures still work.
            itemTextures.clear();
        }
    }
}
