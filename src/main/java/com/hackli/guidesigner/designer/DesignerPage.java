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

package com.hackli.guidesigner.designer;

import com.hackli.guidesigner.export.JavaExporter;
import com.hackli.guidesigner.export.JsonExporter;
import com.hackli.guidesigner.export.MeteorExporter;
import com.hackli.guidesigner.model.AnchorX;
import com.hackli.guidesigner.model.AnchorY;
import com.hackli.guidesigner.model.DocumentStore;
import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.TemplateStore;
import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.UiType;
import com.hackli.guidesigner.runtime.AlignTools;
import com.hackli.guidesigner.runtime.UiLayout;
import com.hackli.guidesigner.runtime.UiRect;
import com.hackli.guidesigner.runtime.UiRenderer;
import com.hackli.guidesigner.runtime.Undoer;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

/**
 * The complete designer UI as a single custom-drawn page. Everything - toolbar,
 * widget palette, WYSIWYG canvas, structure tree, inspector and text editing -
 * is rendered with GuiRenderer primitives and handled by this class directly.
 * This keeps the editor independent from Meteor's widget lifecycle (which is
 * what makes complex design tools fragile).
 */
public class DesignerPage extends WWidget {
    // ================= colors =================

    private static final Color BG = new Color(18, 18, 21);
    private static final Color PANEL_BG = new Color(24, 24, 28);
    private static final Color PANEL_BORDER = new Color(58, 58, 66);
    private static final Color BTN_BG = new Color(38, 38, 46);
    private static final Color BTN_HOVER = new Color(52, 52, 62);
    private static final Color BTN_ACTIVE = new Color(70, 60, 30);
    private static final Color ACCENT = new Color(0, 180, 140);
    private static final Color ACCENT_LIGHT = new Color(0, 255, 200);
    private static final Color TEXT = new Color(215, 215, 220);
    private static final Color TEXT_DIM = new Color(140, 140, 148);
    private static final Color SELECTED = new Color(255, 210, 0);
    private static final Color FIELD_BG = new Color(30, 30, 36);
    private static final Color FIELD_BORDER = new Color(70, 70, 80);
    private static final Color FIELD_FOCUS = new Color(0, 180, 140);
    private static final Color CANVAS_BG = new Color(15, 15, 18);
    private static final Color GRID_COLOR = new Color(38, 38, 45, 110);
    private static final Color GHOST_FILL = new Color(0, 200, 160, 40);
    private static final Color GHOST_BORDER = new Color(0, 255, 200, 200);
    private static final Color HOVER_BORDER = new Color(0x91, 0x3d, 0xe2);
    private static final Color POPUP_BG = new Color(32, 32, 38);
    private static final Color POPUP_HOVER = new Color(48, 50, 60);
    private static final Color MARQUEE_FILL = new Color(255, 210, 0, 24);
    private static final Color MARQUEE_BORDER = new Color(255, 210, 0, 180);

    private static final Color NODE_FILL_CONTAINER = new Color(30, 36, 44);
    private static final Color NODE_FILL_LABEL = new Color(28, 30, 34);
    private static final Color NODE_FILL_BUTTON = new Color(34, 50, 72);
    private static final Color NODE_FILL_TEXTBOX = new Color(26, 31, 38);
    private static final Color NODE_FILL_CHECKBOX = new Color(44, 32, 58);
    private static final Color NODE_FILL_SLIDER = new Color(28, 42, 36);
    private static final Color NODE_FILL_DROPDOWN = new Color(48, 38, 26);
    private static final Color NODE_FILL_SEPARATOR = new Color(58, 58, 66, 160);

    private static final Color NODE_BORDER_CONTAINER = new Color(70, 80, 95);
    private static final Color NODE_BORDER_LABEL = new Color(65, 65, 72);
    private static final Color NODE_BORDER_BUTTON = new Color(70, 105, 150);
    private static final Color NODE_BORDER_TEXTBOX = new Color(62, 78, 95);
    private static final Color NODE_BORDER_CHECKBOX = new Color(95, 70, 120);
    private static final Color NODE_BORDER_SLIDER = new Color(60, 100, 80);
    private static final Color NODE_BORDER_DROPDOWN = new Color(105, 85, 60);
    private static final Color NODE_BORDER_SEPARATOR = new Color(80, 80, 90);

    // ================= geometry =================

    private static final double TOOLBAR_H = 38;
    private static final double STATUS_H = 22;
    private static final double PALETTE_W = 170;
    private static final double SIDE_W = 320;
    private static final double TREE_FRACTION = 0.40;
    private static final double ROW_H = 22;
    private static final double PAD = 6;
    private static final double GRID = 8;
    private static final double POPUP_ITEM_H = 18;

    private double cTx, cTy, cTw, cTh;
    private double pX, pY, pW, pH;
    private double tX, tY, tW, tH;
    private double iX, iY, iW, iH;

    // ================= state =================

    private UiDocument doc = new UiDocument("My GUI");
    private final List<UiNode> selection = new ArrayList<>();
    private UiType pendingType;
    private UiNode pendingTemplate;
    private boolean dirty;
    private String status = "Ready. Pick a widget on the left, then click the canvas.";

    private final Undoer undoer = new Undoer();

    private double originX = 24, originY = 24;
    private UiNode hoverNode;
    private double ghostX, ghostY;

    private UiNode dragging;
    private double grabDX, grabDY;
    private UiNode resizing;
    private int corner;
    private double startW, startH, startMX, startMY;

    private boolean marquee;
    private double marqueeX0, marqueeY0, marqueeX1, marqueeY1;

    private String pkg = "com.yourplugin.gui";
    private String tplName = "MyComponent";

    private final List<Row> rows = new ArrayList<>();
    private final List<TreeRow> treeRows = new ArrayList<>();

    private String openPopup;
    private double popupX, popupY, popupW, popupH;
    private int popupHover = -1;

    private String activeField;
    private int cursor;

    private double paletteScroll, treeScroll, inspectorScroll;
    private double mouseOverX, mouseOverY;

    private final List<ClickableRect> buttonRects = new ArrayList<>();
    private final List<FieldRect> fieldRects = new ArrayList<>();
    private final List<CheckRect> checkRects = new ArrayList<>();
    private final List<DropRect> dropRects = new ArrayList<>();
    private final List<PaletteRect> paletteRects = new ArrayList<>();
    private final List<TemplateRect> templateRects = new ArrayList<>();
    private final java.util.Map<String, List<String>> dropItems = new java.util.HashMap<>();
    private final java.util.Map<String, Consumer<Integer>> dropOnSelect = new java.util.HashMap<>();

    // ================= selection helpers =================

    private UiNode selected() {
        return selection.isEmpty() ? null : selection.get(selection.size() - 1);
    }

    private void setSelection(UiNode node) {
        selection.clear();
        if (node != null) selection.add(node);
        rebuildRows();
    }

    private void toggleSelection(UiNode node) {
        if (!selection.remove(node)) selection.add(node);
        rebuildRows();
    }

    private void clearSelection() {
        selection.clear();
        rebuildRows();
    }

    private void refreshSelectionUi() {
        rebuildRows();
    }

    /** Replaces the document without touching the undo history (undo/redo path). */
    private void applyDoc(UiDocument newDoc, String statusText) {
        doc = newDoc;
        selection.clear();
        pendingType = null;
        pendingTemplate = null;
        activeField = null;
        openPopup = null;
        dirty = true;
        status = statusText;
        rebuildRows();
    }

    /** New document / open: replaces the document and resets the undo history. */
    private void replaceDoc(UiDocument newDoc, String statusText) {
        applyDoc(newDoc, statusText);
        undoer.reset(doc);
    }

    private void recordEdit(String key) {
        undoer.record(doc, key);
    }

    private void markEdited(String key) {
        dirty = true;
        undoer.record(doc, key);
    }

    // ================= rows =================

    private static class Row {
        String key, label;
        RowKind kind;
        double x, y, w, h;
        String value = "", placeholder = "";
        boolean numeric, autoHint;
        boolean checked;
        List<String> items = new ArrayList<>();
        int selectedIndex;
        Consumer<String> onText;
        Consumer<Boolean> onCheck;
        Consumer<Integer> onSelect;
        Consumer<Double> onNumber;
    }

    private enum RowKind { FIELD, CHECK, DROPDOWN, LABEL }

    private static class TreeRow {
        UiNode node;
        int depth;
        double y;
    }

    private static class ClickableRect {
        double x, y, w, h;
        Runnable action;

        ClickableRect(double x, double y, double w, double h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class FieldRect {
        String key;
        double x, y, w, h;

        FieldRect(String key, double x, double y, double w, double h) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class CheckRect {
        String key;
        Runnable action;
        double x, y, size;

        CheckRect(String key, double x, double y, double size, Runnable action) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.size = size;
            this.action = action;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + size && my >= y && my <= y + size;
        }
    }

    private static class DropRect {
        String key;
        double x, y, w, h;

        DropRect(String key, double x, double y, double w, double h) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class PaletteRect {
        UiType type;
        double x, y, w, h;

        PaletteRect(UiType type, double x, double y, double w, double h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class TemplateRect {
        String name;
        double x, y, w, h;

        TemplateRect(String name, double x, double y, double w, double h) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private void rebuildRows() {
        rows.clear();

        if (selection.isEmpty()) {
            Row hint = new Row();
            hint.kind = RowKind.LABEL;
            hint.label = "Nothing selected. Click a widget.";
            rows.add(hint);
            refreshTreeRows();
            return;
        }

        if (selection.size() > 1) {
            Row count = new Row();
            count.kind = RowKind.LABEL;
            count.label = selection.size() + " widgets selected";
            rows.add(count);

            Row hint = new Row();
            hint.kind = RowKind.LABEL;
            hint.label = "Use the toolbar alignment buttons.";
            rows.add(hint);

            refreshTreeRows();
            return;
        }

        UiNode node = selected();

        Row id = row("id", "ID", RowKind.FIELD);
        id.value = node.id;
        id.placeholder = "unique id";
        id.onText = v -> {
            node.id = v;
            markEdited("field:id");
        };

        Row visible = row("visible", "Visible", RowKind.CHECK);
        visible.checked = node.visible;
        visible.onCheck = v -> {
            node.visible = v;
            markEdited("field:visible");
        };

        if (node.type.isContainer) {
            Row layout = row("layout", "Layout", RowKind.DROPDOWN);
            layout.items = List.of("Flow", "Absolute");
            layout.selectedIndex = node.layout == LayoutMode.FLOW ? 0 : 1;
            layout.onSelect = i -> {
                node.layout = i == 0 ? LayoutMode.FLOW : LayoutMode.ABSOLUTE;
                markEdited("field:layout");
                rebuildRows();
            };
        }

        if (node.type.hasText()) {
            Row text = row("text", "Text", RowKind.FIELD);
            text.value = node.text;
            text.onText = v -> {
                node.text = v;
                markEdited("field:text");
            };
        }

        if (node.type.hasPlaceholder()) {
            Row ph = row("placeholder", "Placeholder", RowKind.FIELD);
            ph.value = node.placeholder;
            ph.placeholder = "...";
            ph.onText = v -> {
                node.placeholder = v;
                markEdited("field:placeholder");
            };
        }

        boolean absolute = node.parent != null && node.parent.layout == LayoutMode.ABSOLUTE;

        if (absolute) {
            Row ax = row("ax", "Anchor X", RowKind.DROPDOWN);
            ax.items = List.of("Left", "Center", "Right");
            ax.selectedIndex = node.anchorX.ordinal();
            ax.onSelect = i -> {
                node.anchorX = AnchorX.values()[i];
                markEdited("field:ax");
            };

            Row ay = row("ay", "Anchor Y", RowKind.DROPDOWN);
            ay.items = List.of("Top", "Middle", "Bottom");
            ay.selectedIndex = node.anchorY.ordinal();
            ay.onSelect = i -> {
                node.anchorY = AnchorY.values()[i];
                markEdited("field:ay");
            };

            Row xRow = row("x", "X", RowKind.FIELD);
            xRow.numeric = true;
            xRow.value = num(node.x);
            xRow.onNumber = v -> {
                node.x = v;
                markEdited("field:x");
            };

            Row yRow = row("y", "Y", RowKind.FIELD);
            yRow.numeric = true;
            yRow.value = num(node.y);
            yRow.onNumber = v -> {
                node.y = v;
                markEdited("field:y");
            };
        }

        Row wRow = row("w", "Width", RowKind.FIELD);
        wRow.numeric = true;
        wRow.value = num(node.width);
        wRow.placeholder = "0 = auto";
        wRow.autoHint = true;
        wRow.onNumber = v -> {
            node.width = v;
            markEdited("field:w");
        };

        Row hRow = row("h", "Height", RowKind.FIELD);
        hRow.numeric = true;
        hRow.value = num(node.height);
        hRow.placeholder = "0 = auto";
        hRow.autoHint = true;
        hRow.onNumber = v -> {
            node.height = v;
            markEdited("field:h");
        };

        // ---- text / limits / options / interface settings ----
        if (node.type.hasText()) {
            Row textColor = row("textColor", "Color", RowKind.FIELD);
            textColor.value = node.textColor;
            textColor.placeholder = "#RRGGBB (empty = theme)";
            textColor.onText = v -> {
                node.textColor = v;
                markEdited("field:textColor");
            };
        }
        if (node.type == UiType.LABEL || node.type == UiType.BUTTON || node.type == UiType.TEXTBOX) {
            Row align = row("textAlign", "Align", RowKind.DROPDOWN);
            align.items = List.of("LEFT", "CENTER", "RIGHT");
            align.selectedIndex = node.textAlign.ordinal();
            align.onSelect = i -> {
                node.textAlign = com.hackli.guidesigner.model.TextAlign.values()[i];
                markEdited("field:textAlign");
            };
        }
        if (node.type == UiType.TEXTBOX) {
            Row maxLen = row("maxLength", "Max Len", RowKind.FIELD);
            maxLen.numeric = true;
            maxLen.value = num(node.maxLength);
            maxLen.placeholder = "0 = any";
            maxLen.onNumber = v -> {
                node.maxLength = v.intValue();
                markEdited("field:maxLength");
                rebuildRows();
            };

            Row filter = row("filter", "Filter", RowKind.DROPDOWN);
            filter.items = List.of("NONE", "INT", "DECIMAL");
            filter.selectedIndex = node.inputFilter.ordinal();
            filter.onSelect = i -> {
                node.inputFilter = com.hackli.guidesigner.model.InputFilter.values()[i];
                markEdited("field:filter");
            };
        }
        if (node.type == UiType.BUTTON || node.type == UiType.TEXTBOX || node.type == UiType.CHECKBOX
            || node.type == UiType.SLIDER || node.type == UiType.DROPDOWN) {
            Row handler = row("handler", "Handler", RowKind.FIELD);
            handler.value = node.handler;
            handler.placeholder = "on<Name> override";
            handler.onText = v -> {
                node.handler = v;
                markEdited("field:handler");
            };
        }

        if (node.type == UiType.CHECKBOX) {
            Row checked = row("checked", "Checked", RowKind.CHECK);
            checked.checked = node.checked;
            checked.onCheck = v -> {
                node.checked = v;
                markEdited("field:checked");
            };
        }

        if (node.type == UiType.SLIDER) {
            Row val = row("value", "Value", RowKind.FIELD);
            val.numeric = true;
            val.value = num(node.value);
            val.onNumber = v -> {
                node.value = v;
                markEdited("field:value");
            };

            Row min = row("min", "Min", RowKind.FIELD);
            min.numeric = true;
            min.value = num(node.min);
            min.onNumber = v -> {
                node.min = v;
                markEdited("field:min");
            };

            Row max = row("max", "Max", RowKind.FIELD);
            max.numeric = true;
            max.value = num(node.max);
            max.onNumber = v -> {
                node.max = v;
                markEdited("field:max");
            };
        }

        if (node.type == UiType.DROPDOWN) {
            Row options = row("options", "Options", RowKind.FIELD);
            options.value = String.join(", ", node.options);
            options.placeholder = "a, b, c";
            options.onText = v -> {
                List<String> out = new ArrayList<>();
                for (String part : v.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) out.add(t);
                }
                node.options = out;
                markEdited("field:options");
            };

            Row sel = row("selected", "Selected", RowKind.FIELD);
            sel.value = node.selected == null ? "" : node.selected;
            sel.onText = v -> {
                node.selected = v;
                markEdited("field:selected");
            };
        }

        if (node.type.isContainer) {
            Row count = row("children", "Children", RowKind.LABEL);
            count.label = node.children.size() + " children";
        }

        refreshTreeRows();
    }

    private Row row(String key, String label, RowKind kind) {
        Row r = new Row();
        r.key = key;
        r.label = label;
        r.kind = kind;
        rows.add(r);
        return r;
    }

    private void refreshTreeRows() {
        treeRows.clear();
        addTreeRow(doc.root, 0);
    }

    private void addTreeRow(UiNode node, int depth) {
        TreeRow row = new TreeRow();
        row.node = node;
        row.depth = depth;
        treeRows.add(row);
        for (UiNode child : node.children) addTreeRow(child, depth + 1);
    }

    private static String num(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ================= documents =================

    private void newDocument() {
        replaceDoc(new UiDocument("My GUI"), "New design created.");
        dirty = false;
    }

    private void save() {
        try {
            Path path = DocumentStore.saveProject(doc);
            dirty = false;
            status = "Saved to " + DocumentStore.displayPath(path);
        } catch (Exception e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private void openProject(String name) {
        UiDocument loaded = DocumentStore.loadProject(name);
        if (loaded == null) {
            status = "Could not load " + name;
            return;
        }
        replaceDoc(loaded, "Opened " + name + ".");
        dirty = false;
    }

    private void deleteSelected() {
        if (selection.isEmpty()) return;
        List<UiNode> doomed = new ArrayList<>(selection);
        for (UiNode node : doomed) {
            if (node != doc.root) doc.remove(node);
        }
        setSelection(null);
        markEdited("delete");
        status = "Deleted " + doomed.size() + " item(s).";
    }

    private void moveSelected(int direction) {
        UiNode node = selected();
        if (node == null || node == doc.root || node.parent == null) return;
        List<UiNode> siblings = node.parent.children;
        int index = siblings.indexOf(node);
        int newIndex = index + direction;
        if (index < 0 || newIndex < 0 || newIndex >= siblings.size()) return;
        siblings.remove(index);
        siblings.add(newIndex, node);
        markEdited("move");
        rebuildRows();
    }

    private void align(AlignTools.Align align) {
        if (selection.size() < 2) {
            status = "Align needs at least 2 selected widgets in the same absolute container.";
            return;
        }
        if (AlignTools.apply(selection, align)) {
            markEdited("align");
            status = align.label + " applied to " + selection.size() + " widget(s).";
        } else {
            status = "Align: select widgets inside an Absolute container.";
        }
    }

    private void saveTemplate() {
        UiNode node = selected();
        if (node == null || node == doc.root) {
            status = "Select a widget (subtree) to save as a template.";
            return;
        }
        String name = tplName.trim().isEmpty() ? "Component" : tplName.trim();
        try {
            Path path = TemplateStore.save(name, node);
            status = "Template '" + name + "' saved (" + DocumentStore.displayPath(path) + ").";
        } catch (Exception e) {
            status = "Save template failed: " + e.getMessage();
        }
    }

    private void exportJava(boolean staticMode) {
        String p = pkg.trim().isEmpty() ? "com.yourplugin.gui" : pkg.trim();
        try {
            String className = DocumentStore.toJava(doc.name);
            String fileName = className + (staticMode ? "Static" : "") + ".java";
            String code = staticMode ? MeteorExporter.generate(doc, p) : JavaExporter.generate(doc, p);
            Path path = DocumentStore.writeExport(fileName, code + "\n");
            mc.keyboard.setClipboard(code);
            status = "Exported " + DocumentStore.displayPath(path) + " (also copied to clipboard).";
        } catch (Exception e) {
            status = "Export failed: " + e.getMessage();
        }
    }

    private void exportJson() {
        try {
            Path path = JsonExporter.export(doc);
            status = "Exported JSON + schema to " + DocumentStore.displayPath(path);
        } catch (Exception e) {
            status = "Export failed: " + e.getMessage();
        }
    }

    private void openExportFolder() {
        try {
            Desktop.getDesktop().open(DocumentStore.exportDir().toFile());
        } catch (Exception e) {
            status = "Could not open folder: " + e.getMessage();
        }
    }

    private void undo() {
        UiDocument result = undoer.undo();
        if (result == null) return;
        applyDoc(result, "Undo.");
    }

    private void redo() {
        UiDocument result = undoer.redo();
        if (result == null) return;
        applyDoc(result, "Redo.");
    }

    // ================= rendering =================

    @Override
    public void onCalculateSize() {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (rows.isEmpty()) rebuildRows();
    }

    @Override
    protected void onRender(GuiRenderer r, double mouseX, double mouseY, double delta) {
        computeAreas();

        buttonRects.clear();
        fieldRects.clear();
        checkRects.clear();
        dropRects.clear();
        paletteRects.clear();
        templateRects.clear();

        r.quad(0, 0, width, height, BG);

        renderToolbar(r, mouseX, mouseY);
        renderPalette(r, mouseX, mouseY);
        renderCanvas(r, mouseX, mouseY, delta);
        renderTree(r, mouseX, mouseY);
        renderInspector(r, mouseX, mouseY);

        r.quad(0, height - STATUS_H, width, STATUS_H, PANEL_BG);
        r.quad(0, height - STATUS_H, width, 1, PANEL_BORDER);
        r.text(status, PAD, height - STATUS_H + 6, TEXT_DIM, false);

        if (openPopup != null) renderPopup(r, mouseX, mouseY);
    }

    private void computeAreas() {
        pX = 0;
        pY = TOOLBAR_H;
        pW = PALETTE_W;
        pH = height - TOOLBAR_H - STATUS_H;

        tX = width - SIDE_W;
        tY = TOOLBAR_H;
        tW = SIDE_W;
        tH = (height - TOOLBAR_H - STATUS_H) * TREE_FRACTION;

        iX = width - SIDE_W;
        iY = tY + tH;
        iW = SIDE_W;
        iH = height - TOOLBAR_H - STATUS_H - tH;

        cTx = PALETTE_W;
        cTy = TOOLBAR_H;
        cTw = width - PALETTE_W - SIDE_W;
        cTh = height - TOOLBAR_H - STATUS_H;
    }

    // ---------------- toolbar ----------------

    private void renderToolbar(GuiRenderer r, double mouseX, double mouseY) {
        r.quad(0, 0, width, TOOLBAR_H, PANEL_BG);
        r.quad(0, TOOLBAR_H - 1, width, 1, PANEL_BORDER);

        double x = PAD;
        double y = (TOOLBAR_H - 16) / 2;

        x = button(r, "New", x, y, 44, mouseX, mouseY, this::newDocument);
        x = button(r, "Open", x, y, 44, mouseX, mouseY, () -> openPopup = openPopup == null ? "open" : null);
        List<String> projects = DocumentStore.listProjects();
        String openLabel = projects.isEmpty() ? "(no projects)" : "Open: " + projects.getFirst();
        if (openPopup != null) openLabel = projects.isEmpty() ? "(no projects)" : projects.getFirst();

        x = dropdown(r, "open", openLabel, projects, x, y, false, mouseX, mouseY, i -> {
            if (i >= 0 && i < projects.size()) openProject(projects.get(i));
        });
        x = button(r, "Save", x, y, 44, mouseX, mouseY, this::save) + 4;
        x = button(r, "Undo", x, y, 40, mouseX, mouseY, this::undo);
        x = button(r, "Redo", x, y, 40, mouseX, mouseY, this::redo) + 8;

        x = separator(r, x, y);
        x = button(r, "Del", x, y, 40, mouseX, mouseY, this::deleteSelected);
        x = button(r, "Up", x, y, 36, mouseX, mouseY, () -> moveSelected(-1));
        x = button(r, "Down", x, y, 44, mouseX, mouseY, () -> moveSelected(1));
        x = button(r, "SaveTpl", x, y, 58, mouseX, mouseY, this::saveTemplate);
        x = field(r, "tpl", tplName, x, y, 90, false, v -> tplName = v) + 4;

        if (selection.size() >= 2) {
            x = separator(r, x, y);
            x = button(r, "AL", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.LEFT));
            x = button(r, "AH", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.CENTER_H));
            x = button(r, "AR", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.RIGHT));
            x = button(r, "AV", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.TOP));
            x = button(r, "AM", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.MIDDLE_V));
            x = button(r, "AB", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.BOTTOM));
            x = button(r, "dH", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.SPREAD_H));
            x = button(r, "dV", x, y, 28, mouseX, mouseY, () -> align(AlignTools.Align.SPREAD_V));
        }

        x = separator(r, x, y);
        x = field(r, "pkg", pkg, x, y, 130, false, v -> pkg = v);

        x = button(r, "Export Java", x, y, 84, mouseX, mouseY, () -> exportJava(false));
        x = button(r, "Export Meteor", x, y, 92, mouseX, mouseY, () -> exportJava(true));
        x = button(r, "Export JSON", x, y, 84, mouseX, mouseY, this::exportJson);
        button(r, "Folder", x, y, 52, mouseX, mouseY, this::openExportFolder);
    }

    private double button(GuiRenderer r, String label, double x, double y, double w, double mouseX, double mouseY, Runnable action) {
        double h = 16;
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        r.quad(x, y, w, h, hover ? BTN_HOVER : BTN_BG);
        border(r, x, y, w, h, PANEL_BORDER);
        r.text(label, x + (w - theme.textWidth(label)) / 2, y + (h - textH()) / 2, TEXT, false);

        buttonRects.add(new ClickableRect(x, y, w, h, action));
        return x + w + 4;
    }

    private double separator(GuiRenderer r, double x, double y) {
        r.quad(x, y + 3, 1, 10, PANEL_BORDER);
        return x + 8;
    }

    private double field(GuiRenderer r, String key, String value, double x, double y, double w, boolean numeric, Consumer<String> onChange) {
        double h = 16;
        boolean focused = key.equals(activeField);
        boolean hover = mouseXOver(x, y, w, h);

        r.quad(x, y, w, h, FIELD_BG);
        border(r, x, y, w, h, focused ? FIELD_FOCUS : hover ? FIELD_BORDER : new Color(50, 50, 58));

        String display = value;
        if (display.isEmpty() && numeric) display = "0";

        double textY = y + (h - textH()) / 2;
        r.text(display, x + 4, textY, TEXT, false);

        if (focused) {
            int len = Math.min(cursor, display.length());
            double caretX = x + 4 + theme.textWidth(display.substring(0, len));
            r.quad(caretX, textY + 1, 1, textH() - 2, ACCENT);
        }

        fieldRects.add(new FieldRect(key, x, y, w, h));
        return x + w + 4;
    }

    private void checkbox(GuiRenderer r, String key, boolean checked, double x, double y, Runnable action) {
        double size = 12;
        r.quad(x, y, size, size, checked ? ACCENT : FIELD_BG);
        border(r, x, y, size, size, FIELD_BORDER);
        if (checked) {
            r.text("x", x + 2, y + 1, Color.BLACK, false);
        }
        checkRects.add(new CheckRect(key, x, y, size, action));
    }

    private double dropdown(GuiRenderer r, String key, String value, List<String> items, double x, double y, boolean wide, double mouseX, double mouseY, Consumer<Integer> onChange) {
        double h = 16;
        double w = wide ? 130 : Math.max(60, Math.min(150, theme.textWidth(value) + 24));
        boolean open = key.equals(openPopup);
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        r.quad(x, y, w, h, hover || open ? BTN_HOVER : BTN_BG);
        border(r, x, y, w, h, open ? ACCENT : PANEL_BORDER);
        r.text(value, x + 4, y + (h - textH()) / 2, TEXT, false);
        r.text("v", x + w - 12, y + (h - textH()) / 2, TEXT_DIM, false);

        dropRects.add(new DropRect(key, x, y, w, h));
        dropItems.put(key, items);
        dropOnSelect.put(key, onChange);
        return x + w + 4;
    }

    private void renderPopup(GuiRenderer r, double mouseX, double mouseY) {
        DropRect drop = null;
        for (DropRect d : dropRects) {
            if (d.key.equals(openPopup)) {
                drop = d;
                break;
            }
        }
        if (drop == null) return;

        List<String> items = dropItems.getOrDefault(openPopup, List.of());
        int maxVisible = 8;
        List<String> visibleItems = items.subList(0, Math.min(items.size(), maxVisible));
        double pw = Math.max(drop.w, 150);
        double ph = visibleItems.size() * POPUP_ITEM_H;
        double px = Math.min(drop.x, Math.max(0, cTx + cTw - pw));

        r.quad(px, drop.y + drop.h, pw, ph, POPUP_BG);
        border(r, px, drop.y + drop.h, pw, ph, ACCENT);

        for (int i = 0; i < visibleItems.size(); i++) {
            double iy = drop.y + drop.h + i * POPUP_ITEM_H;
            boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= iy && mouseY <= iy + POPUP_ITEM_H;
            if (hover) {
                popupHover = i;
                r.quad(px, iy, pw, POPUP_ITEM_H, POPUP_HOVER);
            }
            r.text(visibleItems.get(i), px + 6, iy + (POPUP_ITEM_H - textH()) / 2, TEXT, false);
        }

        popupX = px;
        popupY = drop.y + drop.h;
        popupW = pw;
        popupH = ph;
    }

    // ---------------- palette ----------------

    private void renderPalette(GuiRenderer r, double mouseX, double mouseY) {
        r.quad(pX, pY, pW, pH, PANEL_BG);
        r.quad(pX + pW - 1, pY, 1, pH, PANEL_BORDER);

        double headerY = pY + PAD;
        r.text("Widgets - click to pick", pX + PAD, headerY, TEXT_DIM, false);

        double rowH = 26;
        double widgetH = UiType.values().length * rowH + 6;

        List<String> templates = new ArrayList<>(TemplateStore.list());
        double tplHeader = 16;
        double tplH = templates.size() * rowH + 6;

        double contentH = widgetH + tplHeader + tplH + 8;
        paletteScroll = clamp(paletteScroll, 0, Math.max(0, contentH - (pH - 24)));

        r.scissorStart(pX, pY + 20, pW, pH - 20);
        double topY = pY + 20 + PAD - paletteScroll;

        for (UiType type : UiType.values()) {
            double by = topY + type.ordinal() * rowH;
            boolean active = pendingType == type;
            boolean hover = mouseX >= pX + 4 && mouseX <= pX + pW - 4 && mouseY >= by && mouseY <= by + rowH - 4;

            double bx = pX + 4, bw = pW - 8, bh = rowH - 4;
            r.quad(bx, by, bw, bh, active ? BTN_ACTIVE : hover ? BTN_HOVER : BTN_BG);
            border(r, bx, by, bw, bh, active ? ACCENT : PANEL_BORDER);
            r.text(type.displayName, bx + 6, by + (bh - textH()) / 2, active ? ACCENT_LIGHT : TEXT, false);

            paletteRects.add(new PaletteRect(type, bx, by, bw, bh));
        }

        // Templates section
        double tplY = topY + widgetH + 12;
        r.text("Templates - or save your own", pX + 4, tplY, TEXT_DIM, false);
        tplY += tplHeader;

        for (int i = 0; i < templates.size(); i++) {
            double by = tplY + i * rowH;
            boolean active = pendingTemplate != null;
            boolean hover = mouseX >= pX + 4 && mouseX <= pX + pW - 4 && mouseY >= by && mouseY <= by + rowH - 4;

            double bx = pX + 4, bw = pW - 8, bh = rowH - 4;
            r.quad(bx, by, bw, bh, active ? BTN_ACTIVE : hover ? BTN_HOVER : new Color(42, 40, 34));
            border(r, bx, by, bw, bh, new Color(110, 95, 60));
            r.text("T: " + templates.get(i), bx + 6, by + (bh - textH()) / 2, active ? ACCENT_LIGHT : TEXT, false);

            templateRects.add(new TemplateRect(templates.get(i), bx, by, bw, bh));
        }
        r.scissorEnd();
    }

    // ---------------- canvas ----------------

    /** Real Meteor widget tree used to render the canvas through the comet pipeline. */
    private WWidget realRoot;
    private String realSignature;

    private void ensureRealPreview() {
        String signature = doc.toJson();
        if (realRoot != null && signature.equals(realSignature)) return;

        realSignature = signature;
        try {
            realRoot = UiRenderer.build(doc.root, theme, null);
            applyTheme(realRoot, theme);
            realRoot.init();
            realRoot.calculateSize();
        } catch (Throwable t) {
            realRoot = null;
        }
    }

    private void applyTheme(WWidget widget, meteordevelopment.meteorclient.gui.GuiTheme guiTheme) {
        widget.theme = guiTheme;
        if (widget instanceof meteordevelopment.meteorclient.gui.widgets.containers.WContainer container) {
            for (meteordevelopment.meteorclient.gui.utils.Cell<?> cell : container.cells) {
                applyTheme(cell.widget(), guiTheme);
            }
        }
    }

    private void renderCanvas(GuiRenderer r, double mouseX, double mouseY, double delta) {
        r.quad(cTx, cTy, cTw, cTh, CANVAS_BG);
        r.quad(cTx + cTw - 1, cTy, 1, cTh, PANEL_BORDER);
        r.quad(cTx, cTy + cTh - 1, cTw, 1, PANEL_BORDER);

        double startGX = cTx + (originX % GRID);
        double startGY = cTy + (originY % GRID);
        for (double gx = startGX; gx < cTx + cTw; gx += GRID) r.quad(gx, cTy, 1, cTh, GRID_COLOR);
        for (double gy = startGY; gy < cTy + cTh; gy += GRID) r.quad(cTx, gy, cTw, 1, GRID_COLOR);

        UiNode root = doc.root;
        double rootW = root.width > 0 ? root.width : root.type.defaultWidth;
        double rootH = root.height > 0 ? root.height : root.type.defaultHeight;

        ensureRealPreview();

        if (realRoot != null) {
            // Render the design through Meteor's own widget/render pipeline.
            realRoot.x = cTx + originX;
            realRoot.y = cTy + originY;
            realRoot.calculateSize();
            realRoot.calculateWidgetPositions();

            r.scissorStart(cTx, cTy, cTw, cTh);
            realRoot.render(r, mouseX, mouseY, delta);
            r.scissorEnd();
        } else {
            // Fallback: self-drawn representation (only if the widget tree failed).
            UiLayout.layoutTree(root, cTx + originX, cTy + originY, rootW, rootH);
            drawNode(r, root);
        }

        // ---- editor overlay (selection, handles, marquee, ghost) ----
        drawOverlay(r, root, mouseX, mouseY);

        if (pendingTemplate != null && mouseX >= cTx && mouseX <= cTx + cTw && mouseY >= cTy && mouseY <= cTy + cTh) {
            double w = pendingTemplate.width > 0 ? pendingTemplate.width : pendingTemplate.type.defaultWidth;
            double h = pendingTemplate.height > 0 ? pendingTemplate.height : pendingTemplate.type.defaultHeight;
            r.quad(ghostX - w / 2, ghostY - h / 2, w, h, GHOST_FILL);
            border(r, ghostX - w / 2, ghostY - h / 2, w, h, GHOST_BORDER);
            r.text("Template: " + pendingTemplate.id, ghostX - w / 2 + 5, ghostY - 12, GHOST_BORDER, false);
        } else if (pendingType != null && mouseX >= cTx && mouseX <= cTx + cTw && mouseY >= cTy && mouseY <= cTy + cTh) {
            double w = pendingType.defaultWidth;
            double h = pendingType.defaultHeight;
            r.quad(ghostX - w / 2, ghostY - h / 2, w, h, GHOST_FILL);
            border(r, ghostX - w / 2, ghostY - h / 2, w, h, GHOST_BORDER);
            r.text(pendingType.displayName, ghostX - w / 2 + 5, ghostY - 12, GHOST_BORDER, false);
        }

        // marquee
        if (marquee) {
            double mx = Math.min(marqueeX0, marqueeX1);
            double my = Math.min(marqueeY0, marqueeY1);
            double mw = Math.abs(marqueeX1 - marqueeX0);
            double mh = Math.abs(marqueeY1 - marqueeY0);
            r.quad(mx, my, mw, mh, MARQUEE_FILL);
            border(r, mx, my, mw, mh, MARQUEE_BORDER);
        }
    }

    /** Selection/hover outlines drawn on top of the real rendering. */
    private void drawOverlay(GuiRenderer r, UiNode node, double mouseX, double mouseY) {
        if (!node.visible) return;

        boolean isSel = selection.contains(node);
        boolean isHover = node == hoverNode && !isSel;

        if (isSel || isHover) {
            double w = Math.max(1, node.renderW);
            double h = Math.max(1, node.renderH);
            border(r, node.renderX - 1, node.renderY - 1, w + 2, h + 2, isSel ? SELECTED : HOVER_BORDER);
            if (isSel) {
                double s = 7;
                r.quad(node.renderX - s / 2, node.renderY - s / 2, s, s, SELECTED);
                r.quad(node.renderX + w - s / 2, node.renderY - s / 2, s, s, SELECTED);
                r.quad(node.renderX - s / 2, node.renderY + h - s / 2, s, s, SELECTED);
                r.quad(node.renderX + w - s / 2, node.renderY + h - s / 2, s, s, SELECTED);
            }
        }

        for (UiNode child : node.children) drawOverlay(r, child, mouseX, mouseY);
    }

    private void drawNode(GuiRenderer r, UiNode node) {
        if (!node.visible) return;

        Color fill = switch (node.type) {
            case CONTAINER -> NODE_FILL_CONTAINER;
            case LABEL -> NODE_FILL_LABEL;
            case BUTTON -> NODE_FILL_BUTTON;
            case TEXTBOX -> NODE_FILL_TEXTBOX;
            case CHECKBOX -> NODE_FILL_CHECKBOX;
            case SLIDER -> NODE_FILL_SLIDER;
            case DROPDOWN -> NODE_FILL_DROPDOWN;
            case SEPARATOR -> NODE_FILL_SEPARATOR;
            case NUMBER -> NODE_FILL_SLIDER;
            case TEXTURE -> NODE_FILL_TEXTBOX;
            case KEYBIND -> NODE_FILL_BUTTON;
            case ITEM, ENTITY -> NODE_FILL_CHECKBOX;
            case SELECT -> NODE_FILL_BUTTON;
        };

        boolean isSel = selection.contains(node);
        boolean isHover = node == hoverNode && !isSel;

        Color bc;
        if (isSel) bc = SELECTED;
        else if (isHover) bc = HOVER_BORDER;
        else {
            bc = switch (node.type) {
                case CONTAINER -> NODE_BORDER_CONTAINER;
                case LABEL -> NODE_BORDER_LABEL;
                case BUTTON -> NODE_BORDER_BUTTON;
                case TEXTBOX -> NODE_BORDER_TEXTBOX;
                case CHECKBOX -> NODE_BORDER_CHECKBOX;
                case SLIDER -> NODE_BORDER_SLIDER;
                case DROPDOWN -> NODE_BORDER_DROPDOWN;
                case SEPARATOR -> NODE_BORDER_SEPARATOR;
                case NUMBER -> NODE_BORDER_SLIDER;
                case TEXTURE -> NODE_BORDER_TEXTBOX;
                case KEYBIND -> NODE_BORDER_BUTTON;
                case ITEM, ENTITY -> NODE_BORDER_CHECKBOX;
                case SELECT -> NODE_BORDER_BUTTON;
            };
        }

        double w = Math.max(1, node.renderW);
        double h = Math.max(1, node.renderH);

        r.quad(node.renderX, node.renderY, w, h, fill);
        border(r, node.renderX, node.renderY, w, h, bc);

        String label = node.type.displayName + " - " + node.id;
        if (!node.text.isEmpty() && (node.type == UiType.LABEL || node.type == UiType.BUTTON || node.type == UiType.CONTAINER)) {
            label = node.text;
        }
        r.text(label, node.renderX + 5, node.renderY + 3, TEXT, false);

        if (isSel && node.parent != null && node.parent.layout == LayoutMode.ABSOLUTE) {
            double s = 7;
            r.quad(node.renderX - s / 2, node.renderY - s / 2, s, s, SELECTED);
            r.quad(node.renderX + w - s / 2, node.renderY - s / 2, s, s, SELECTED);
            r.quad(node.renderX - s / 2, node.renderY + h - s / 2, s, s, SELECTED);
            r.quad(node.renderX + w - s / 2, node.renderY + h - s / 2, s, s, SELECTED);
        }

        for (UiNode child : node.children) drawNode(r, child);
    }

    private void border(GuiRenderer r, double x, double y, double w, double h, Color c) {
        r.quad(x, y, w, 1, c);
        r.quad(x, y + h - 1, w, 1, c);
        r.quad(x, y, 1, h, c);
        r.quad(x + w - 1, y, 1, h, c);
    }

    // ---------------- tree ----------------

    private void renderTree(GuiRenderer r, double mouseX, double mouseY) {
        r.quad(tX, tY, tW, tH, PANEL_BG);
        r.quad(tX, tY, 1, tH, PANEL_BORDER);
        r.quad(tX + tW - 1, tY, 1, tH, PANEL_BORDER);
        r.quad(tX, tY + tH - 1, tW, 1, PANEL_BORDER);

        r.text("Structure (" + doc.root.count() + " nodes)", tX + PAD, tY + PAD, TEXT_DIM, false);

        double contentH = treeRows.size() * ROW_H;
        treeScroll = clamp(treeScroll, 0, Math.max(0, contentH - (tH - 24)));

        r.scissorStart(tX, tY + 20, tW, tH - 20);
        for (int i = 0; i < treeRows.size(); i++) {
            TreeRow row = treeRows.get(i);
            double y = tY + 20 + i * ROW_H - treeScroll;
            row.y = y;

            boolean isSel = selection.contains(row.node);
            boolean hover = !isSel && mouseX >= tX && mouseX <= tX + tW && mouseY >= y && mouseY <= y + ROW_H;
            if (isSel) r.quad(tX, y, tW, ROW_H, new Color(70, 60, 30, 200));
            else if (hover) r.quad(tX, y, tW, ROW_H, new Color(45, 45, 52, 160));

            String label = nodeLabel(row.node);
            r.text("  ".repeat(row.depth) + label, tX + 8, y + (ROW_H - textH()) / 2, isSel ? SELECTED : TEXT, false);
        }
        r.scissorEnd();
    }

    private String nodeLabel(UiNode node) {
        return node.type.displayName + " - " + node.id;
    }

    // ---------------- inspector ----------------

    private void renderInspector(GuiRenderer r, double mouseX, double mouseY) {
        r.quad(iX, iY, iW, iH, PANEL_BG);
        r.quad(iX, iY, 1, iH, PANEL_BORDER);
        r.quad(iX + iW - 1, iY, 1, iH, PANEL_BORDER);

        r.text("Inspector", iX + PAD, iY + PAD, TEXT_DIM, false);

        double labelW = 82;
        double controlW = iW - labelW - PAD * 2;

        double contentH = rows.size() * ROW_H;
        inspectorScroll = clamp(inspectorScroll, 0, Math.max(0, contentH - (iH - 24)));

        r.scissorStart(iX, iY + 20, iW, iH - 20);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            double y = iY + 20 + i * ROW_H - inspectorScroll;
            row.x = iX + PAD;
            row.y = y;
            row.w = iW - PAD * 2;
            row.h = ROW_H;

            double cx = row.x + labelW;
            double cw = Math.max(40, row.w - labelW);

            switch (row.kind) {
                case LABEL -> r.text(row.label, row.x, row.y + (ROW_H - textH()) / 2, TEXT_DIM, false);
                case FIELD -> {
                    r.text(row.label, row.x, row.y + (ROW_H - textH()) / 2, TEXT_DIM, false);
                    field(r, row.key, row.value, cx, row.y + 2, cw, row.numeric, v -> {
                        if (row.onText != null) row.onText.accept(v);
                    });
                }
                case CHECK -> {
                    r.text(row.label, row.x, row.y + (ROW_H - textH()) / 2, TEXT_DIM, false);
                    checkbox(r, row.key, row.checked, cx, row.y + 2, () -> {
                        if (row.onCheck != null) row.onCheck.accept(!row.checked);
                    });
                }
                case DROPDOWN -> {
                    r.text(row.label, row.x, row.y + (ROW_H - textH()) / 2, TEXT_DIM, false);
                    String value = row.selectedIndex >= 0 && row.selectedIndex < row.items.size() ? row.items.get(row.selectedIndex) : "";
                    dropdown(r, row.key, value, row.items, cx, row.y + 2, false, mouseX, mouseY, idx -> {
                        if (row.onSelect != null) row.onSelect.accept(idx);
                    });
                }
            }
        }
        r.scissorEnd();
    }

    // ================= input =================

    private boolean ctrlHeld() {
        long handle = mc.getWindow().getHandle();
        return glfwGetKey(handle, 341 /* GLFW_KEY_LEFT_CONTROL */) == GLFW_PRESS
            || glfwGetKey(handle, 345 /* GLFW_KEY_RIGHT_CONTROL */) == GLFW_PRESS;
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() != 0) return false;

        // Double-click on a node: select it and jump to its text property.
        if (doubled) {
            if (mx >= cTx && mx <= cTx + cTw && my >= cTy && my <= cTy + cTh) {
                UiNode hit = UiLayout.hitTest(doc.root, mx, my);
                if (hit != null) {
                    if (!selection.contains(hit)) setSelection(hit);
                    if (hit.type.hasText()) {
                        activeField = "text";
                        cursor = Integer.MAX_VALUE;
                    }
                }
            }
            return true;
        }

        // Popup
        if (openPopup != null) {
            if (mx >= popupX && mx <= popupX + popupW && my >= popupY && my <= popupY + popupH) {
                int idx = (int) ((my - popupY) / POPUP_ITEM_H);
                Consumer<Integer> select = dropOnSelect.get(openPopup);
                if (select != null && idx >= 0 && idx < dropItems.getOrDefault(openPopup, List.of()).size()) {
                    select.accept(idx);
                }
                openPopup = null;
                return true;
            }
            openPopup = null;
        }

        // Fields
        for (FieldRect rect : fieldRects) {
            if (rect.contains(mx, my)) {
                activeField = rect.key;
                cursor = Integer.MAX_VALUE;
                return true;
            }
        }

        // Checkboxes
        for (CheckRect rect : checkRects) {
            if (rect.contains(mx, my)) {
                rect.action.run();
                return true;
            }
        }

        // Dropdowns
        for (DropRect rect : dropRects) {
            if (rect.contains(mx, my)) {
                openPopup = rect.key;
                return true;
            }
        }

        // Toolbar buttons
        for (ClickableRect rect : buttonRects) {
            if (rect.contains(mx, my)) {
                rect.action.run();
                return true;
            }
        }

        // Palette widgets
        if (mx >= pX && mx <= pX + pW && my >= pY && my <= pY + pH) {
            for (PaletteRect rect : paletteRects) {
                if (rect.contains(mx, my)) {
                    UiType type = rect.type;
                    pendingType = pendingType == type ? null : type;
                    pendingTemplate = null;
                    return true;
                }
            }
            for (TemplateRect rect : templateRects) {
                if (rect.contains(mx, my)) {
                    TemplateStore.Template t = TemplateStore.load(rect.name);
                    if (t != null) {
                        pendingTemplate = t.root.copyWithFreshIds(doc.root);
                        pendingType = null;
                        status = "Template '" + rect.name + "' - click the canvas to place it.";
                    } else {
                        status = "Could not load template '" + rect.name + "'.";
                    }
                    return true;
                }
            }
            activeField = null;
            return true;
        }

        // Tree
        if (mx >= tX && mx <= tX + tW && my >= tY && my <= tY + tH) {
            for (TreeRow row : treeRows) {
                if (my >= row.y && my <= row.y + ROW_H) {
                    if (ctrlHeld()) toggleSelection(row.node);
                    else setSelection(row.node);
                    activeField = null;
                    openPopup = null;
                    return true;
                }
            }
            if (ctrlHeld()) {
                // keep selection
            } else {
                clearSelection();
            }
            activeField = null;
            return true;
        }

        // Inspector area
        if (mx >= iX && mx <= iX + iW && my >= iY && my <= iY + iH) {
            activeField = null;
            return true;
        }

        // Canvas
        if (mx >= cTx && mx <= cTx + cTw && my >= cTy && my <= cTy + cTh) {
            return handleCanvasClick(mx, my);
        }

        activeField = null;
        return false;
    }

    private boolean handleCanvasClick(double mx, double my) {
        if (pendingTemplate != null) {
            placeNode(null, pendingTemplate, mx, my);
            pendingTemplate = null;
            return true;
        }
        if (pendingType != null) {
            placeNode(pendingType, null, mx, my);
            pendingType = null;
            return true;
        }

        UiNode hit = UiLayout.hitTest(doc.root, mx, my);

        if (hit == null) {
            // Blank canvas: start a marquee (or clear on plain click)
            marquee = true;
            marqueeX0 = marqueeX1 = mx;
            marqueeY0 = marqueeY1 = my;
            return true;
        }

        if (ctrlHeld()) {
            toggleSelection(hit);
            return true;
        }

        if (!selection.contains(hit)) {
            setSelection(hit);
        }

        if (hit == doc.root || hit.parent == null || hit.parent.layout != LayoutMode.ABSOLUTE) {
            return true;
        }

        UiRect rect = new UiRect(hit.renderX, hit.renderY, hit.renderW, hit.renderH);
        if (rect.nearCorner(mx, my, 7)) {
            resizing = hit;
            startW = rect.w;
            startH = rect.h;
            startMX = mx;
            startMY = my;
            corner = 0;
            if (Math.abs(mx - rect.x) < Math.abs(mx - (rect.x + rect.w))) corner |= 1;
            if (Math.abs(my - rect.y) < Math.abs(my - (rect.y + rect.h))) corner |= 2;
        } else {
            dragging = hit;
            grabDX = rect.x - mx;
            grabDY = rect.y - my;
        }
        return true;
    }

    private void placeNode(UiType type, UiNode templateRoot, double mx, double my) {
        UiNode parent = pickContainer(mx, my);
        UiNode node;

        if (templateRoot != null) {
            node = templateRoot;
        } else {
            node = UiNode.create(type, UiNode.uniqueId(doc.root, type));
            node.width = type.defaultWidth;
            node.height = type.defaultHeight;
            node.layout = LayoutMode.FLOW;

            switch (type) {
                case LABEL, BUTTON, CONTAINER -> node.text = type.displayName;
                case TEXTBOX -> node.placeholder = "...";
                case SLIDER -> {
                    node.value = 50;
                    node.min = 0;
                    node.max = 100;
                }
                case DROPDOWN -> {
                    node.options.add("Option 1");
                    node.options.add("Option 2");
                    node.selected = "Option 1";
                }
                case CHECKBOX -> node.checked = false;
                case SEPARATOR -> node.text = "";
            }
        }

        node.x = Math.round(mx - node.width / 2 - parent.renderX - parent.renderW * node.anchorX.f);
        node.y = Math.round(my - node.height / 2 - parent.renderY - parent.renderH * node.anchorY.f);

        parent.child(node);
        selection.clear();
        selection.add(node);
        activeField = null;
        markEdited("place");
        status = "Placed " + node.id + ".";
        rebuildRows();
    }

    private UiNode pickContainer(double mx, double my) {
        UiNode hit = UiLayout.hitTest(doc.root, mx, my);
        while (hit != null && hit.type != UiType.CONTAINER) hit = hit.parent;
        return hit != null ? hit : doc.root;
    }

    @Override
    public boolean onMouseReleased(Click click) {
        if (marquee) {
            marquee = false;
            double w = Math.abs(marqueeX1 - marqueeX0);
            double h = Math.abs(marqueeY1 - marqueeY0);

            if (w < 6 && h < 6) {
                clearSelection();
            } else {
                double mx = Math.min(marqueeX0, marqueeX1);
                double my = Math.min(marqueeY0, marqueeY1);
                double mw = Math.max(w, 1);
                double mh = Math.max(h, 1);

                if (!ctrlHeld()) selection.clear();
                collectInRect(doc.root, mx, my, mw, mh);
                refreshSelectionUi();
            }
            return true;
        }

        if (dragging != null || resizing != null) {
            dragging = null;
            resizing = null;
            markEdited("drag:" + (selection.isEmpty() ? "?" : selected().id));
            status = "Modified - press Save.";
            rebuildRows();
        }
        return false;
    }

    private void collectInRect(UiNode node, double x, double y, double w, double h) {
        if (node.visible && intersects(node.renderX, node.renderY, node.renderW, node.renderH, x, y, w, h)) {
            if (node != doc.root) selection.add(node);
            for (UiNode child : node.children) collectInRect(child, x, y, w, h);
        } else {
            for (UiNode child : node.children) collectInRect(child, x, y, w, h);
        }
    }

    private boolean intersects(double x1, double y1, double w1, double h1, double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    @Override
    public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
        mouseOverX = mouseX;
        mouseOverY = mouseY;
        ghostX = mouseX;
        ghostY = mouseY;
        hoverNode = UiLayout.hitTest(doc.root, mouseX, mouseY);

        if (marquee) {
            marqueeX1 = mouseX;
            marqueeY1 = mouseY;
            return;
        }

        if (dragging != null && dragging.parent != null) {
            UiNode parent = dragging.parent;
            UiRect pr = new UiRect(parent.renderX, parent.renderY, parent.renderW, parent.renderH);
            double sx = Math.round(mouseX / GRID) * GRID;
            double sy = Math.round(mouseY / GRID) * GRID;
            dragging.x = Math.round(sx + grabDX - pr.x - pr.w * dragging.anchorX.f);
            dragging.y = Math.round(sy + grabDY - pr.y - pr.h * dragging.anchorY.f);
        } else if (resizing != null) {
            double sx = Math.round(mouseX / GRID) * GRID;
            double sy = Math.round(mouseY / GRID) * GRID;
            double dx = sx - startMX;
            double dy = sy - startMY;
            double nw = (corner & 1) != 0 ? startW - dx : startW + dx;
            double nh = (corner & 2) != 0 ? startH - dy : startH + dy;
            resizing.width = Math.max(8, nw);
            resizing.height = Math.max(8, nh);
        }
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        if (mouseOverX >= pX && mouseOverX <= pX + pW && mouseOverY >= pY && mouseOverY <= pY + pH) {
            paletteScroll += amount * -20;
            return true;
        }
        if (mouseOverX >= tX && mouseOverX <= tX + tW && mouseOverY >= tY && mouseOverY <= tY + tH) {
            treeScroll += amount * -20;
            return true;
        }
        if (mouseOverX >= iX && mouseOverX <= iX + iW && mouseOverY >= iY && mouseOverY <= iY + iH) {
            inspectorScroll += amount * -20;
            return true;
        }
        if (mouseOverX >= cTx && mouseOverX <= cTx + cTw && mouseOverY >= cTy && mouseOverY <= cTy + cTh) {
            originY += amount * -20;
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPressed(KeyInput input) {
        if (activeField != null) {
            if (activeField.equals("pkg")) {
                if (input.key() == GLFW_KEY_BACKSPACE || input.key() == GLFW_KEY_DELETE) {
                    if (!pkg.isEmpty()) pkg = pkg.substring(0, pkg.length() - 1);
                    return true;
                }
                if (input.key() == GLFW_KEY_ENTER || input.key() == GLFW_KEY_ESCAPE) {
                    activeField = null;
                    return true;
                }
                return false;
            }
            if (activeField.equals("tpl")) {
                if (input.key() == GLFW_KEY_BACKSPACE || input.key() == GLFW_KEY_DELETE) {
                    if (!tplName.isEmpty()) tplName = tplName.substring(0, tplName.length() - 1);
                    return true;
                }
                if (input.key() == GLFW_KEY_ENTER || input.key() == GLFW_KEY_ESCAPE) {
                    activeField = null;
                    return true;
                }
                return false;
            }

            Row row = rowByKey(activeField);
            if (row == null) return false;

            if (input.key() == GLFW_KEY_BACKSPACE) {
                if (!row.value.isEmpty()) {
                    int end = Math.min(cursor, row.value.length());
                    int start = Math.max(0, end - 1);
                    row.value = row.value.substring(0, start) + row.value.substring(end);
                    cursor = start;
                    commitField(row);
                }
                return true;
            }
            if (input.key() == GLFW_KEY_DELETE) {
                if (!row.value.isEmpty()) {
                    int end = Math.min(cursor, row.value.length());
                    row.value = row.value.substring(0, end) + row.value.substring(Math.min(end + 1, row.value.length()));
                    commitField(row);
                }
                return true;
            }
            if (input.key() == GLFW_KEY_LEFT) {
                cursor = Math.max(0, cursor - 1);
                return true;
            }
            if (input.key() == GLFW_KEY_RIGHT) {
                cursor++;
                return true;
            }
            if (input.key() == GLFW_KEY_ENTER || input.key() == GLFW_KEY_ESCAPE) {
                commitField(row);
                activeField = null;
                return true;
            }
            return false;
        }

        if (ctrlHeld() && input.key() == GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (ctrlHeld() && input.key() == GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (input.key() == GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        if (input.modifiers() == GLFW_MOD_CONTROL && input.key() == GLFW_KEY_S) {
            save();
            return true;
        }
        if (input.key() == GLFW_KEY_ESCAPE) {
            if (!selection.isEmpty()) {
                clearSelection();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCharTyped(CharInput input) {
        if (activeField == null) return false;

        int cp = input.codepoint();
        if (cp < 32) return false;
        char c = (char) cp;

        if (activeField.equals("pkg")) {
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_')) return false;
            pkg = pkg + c;
            return true;
        }
        if (activeField.equals("tpl")) {
            if (Character.isWhitespace(c) || c == '.' || c == '/') return false;
            tplName = tplName + c;
            return true;
        }

        Row row = rowByKey(activeField);
        if (row == null) return false;

        if (row.numeric && !((c >= '0' && c <= '9') || c == '-' || c == '.')) return false;
        if (Character.isWhitespace(c)) return false;

        String text = row.value;
        int pos = Math.min(cursor, text.length());
        row.value = text.substring(0, pos) + c + text.substring(pos);
        cursor = pos + 1;
        commitField(row);
        return true;
    }

    private void commitField(Row row) {
        if (row == null) return;
        if (row.onText != null) {
            row.onText.accept(row.value);
        } else if (row.onNumber != null) {
            double v = parseDouble(row.value);
            if (!Double.isNaN(v)) row.onNumber.accept(v);
        }
    }

    private Row rowByKey(String key) {
        for (Row row : rows) {
            if (key.equals(row.key)) return row;
        }
        return null;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double textH() {
        return theme.textHeight();
    }

    private boolean mouseXOver(double x, double y, double w, double h) {
        return mouseOverX >= x && mouseOverX <= x + w && mouseOverY >= y && mouseOverY <= y + h;
    }
}
