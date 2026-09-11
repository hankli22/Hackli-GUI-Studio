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

package com.hackli.guidesigner.gl;

import com.hackli.guidesigner.export.JavaExporter;
import com.hackli.guidesigner.export.JsonExporter;
import com.hackli.guidesigner.export.MeteorExporter;
import com.hackli.guidesigner.model.AnchorX;
import com.hackli.guidesigner.model.AnchorY;
import com.hackli.guidesigner.model.DocumentStore;
import com.hackli.guidesigner.model.InputFilter;
import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.TemplateStore;
import com.hackli.guidesigner.model.TextAlign;
import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.UiType;
import com.hackli.guidesigner.render.MeteorPainter;
import com.hackli.guidesigner.render.MeteorTheme;
import com.hackli.guidesigner.runtime.AlignTools;
import com.hackli.guidesigner.runtime.UiLayout;
import com.hackli.guidesigner.runtime.UiRect;
import com.hackli.guidesigner.runtime.Undoer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.lwjgl.opengl.GL11.glDeleteTextures;

import java.util.function.Supplier;

/**
 * The OpenGL designer: a full editor rendered through Meteor's own chrome.
 *
 * <p>Everything the in-game designer and the desktop editor can do is here:</p>
 * <ul>
 *   <li>palette (every widget type) and reusable templates</li>
 *   <li>hierarchy tree and a live property inspector</li>
 *   <li>drag to reorder / alt+drag for free placement / 8 resize handles</li>
 *   <li>edge snapping with guides, marquee and ctrl multi-select</li>
 *   <li>align &amp; distribute tools, duplicate, delete, undo/redo</li>
 *   <li>inline text editing, project new/open/save/save-as</li>
 *   <li>export to runtime Java, static Meteor code or JSON + schema</li>
 *   <li>play mode where widgets behave exactly like the in-game click GUI</li>
 * </ul>
 */
public class GlEditor {
    public enum Mode { DESIGN, PLAY }

    private enum Drag { NONE, MOVE, RESIZE, MARQUEE, SLIDER, PAN }

    /** One inspector row. */
    private static final class Row {
        static final int TEXT = 0, NUM = 1, CYCLE = 2, CHECK = 3, READ = 4;
        final String label;
        final int kind;
        final Supplier<String> get;
        final Consumer<String> set;
        final String[] options;

        Row(String label, int kind, Supplier<String> get, Consumer<String> set, String... options) {
            this.label = label;
            this.kind = kind;
            this.get = get;
            this.set = set;
            this.options = options;
        }
    }

    private record TreeItem(UiNode node, int depth) {}

    private static final int HANDLE_NW = 0, HANDLE_N = 1, HANDLE_NE = 2, HANDLE_E = 3,
        HANDLE_SE = 4, HANDLE_S = 5, HANDLE_SW = 6, HANDLE_W = 7;
    private static final double HANDLE_SIZE = 7;
    private static final double SNAP = 5;
    /** Snapping only kicks in after the pointer really moved, so grabs feel free. */
    private static final double SNAP_ARM_PX = 4;

    private final Gl2D gl;
    private final DesignRenderer renderer;
    private final MeteorTheme theme;
    private final GlUi ui;
    private final UiDocument doc;
    private final Undoer undoer = new Undoer();

    private Path file;

    private Mode mode = Mode.DESIGN;
    private final List<UiNode> selection = new ArrayList<>();
    private UiNode hovered;
    private UiNode textEdit;
    private int textCursor;

    /** Screen-space pointer. */
    private double mouseX = -1, mouseY = -1;
    /** Canvas zoom and the world-space pointer that goes with it. */
    private double zoom = 1.0;
    private double worldX = -1, worldY = -1;
    private boolean mouseDown;
    private double panX = 24, panY = 24;
    private boolean snap = true;
    private boolean showChrome = true;
    private boolean dirty;

    private Drag drag = Drag.NONE;
    private int handle = -1;
    private UiNode dragNode;
    /** True while {@link #dragNode} is a NUMBER widget being scrubbed. */
    private boolean dragNumber;

    /** The widget the editor is currently waiting for a key press for. */
    private UiNode listeningKeybind;
    private double dragStartX, dragStartY;
    /** Screen position where the current drag began (for the snap arm test). */
    private double downScreenX, downScreenY;
    private final List<double[]> dragOrigin = new ArrayList<>();
    private double marqueeX0, marqueeY0, marqueeX1, marqueeY1;
    private int dropIndex = -1;
    private double dropY = -1;
    private final List<double[]> guides = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    private double treeScroll, inspectorScroll, paletteScroll;

    private String popupId;
    private double popupX, popupY;
    private String[] popupItems;
    private Consumer<Integer> popupAction;

    private String promptTitle;
    private String promptValue = "";
    private Consumer<String> promptAction;
    private String promptFieldId = "prompt";

    private long lastClickTime;
    private double lastClickX = -1, lastClickY = -1;

    private double[] canvasRect, paletteRect, rightRect, treeRect, inspectorRect;

    private boolean ctrlDown, shiftDown, altDown;

    public GlEditor(Gl2D gl, DesignRenderer renderer, FontAtlas uiFont, double uiScale,
                    MeteorTheme theme, UiDocument doc, Path file, String fontPath) {
        this.gl = gl;
        this.renderer = renderer;
        this.theme = theme;
        this.ui = new GlUi(gl, uiFont, theme, uiScale);
        this.doc = doc;
        this.file = file;
        this.fontPath = fontPath;

        renderer.setDocument(doc);
        undoer.reset(doc);
        // GLFW can deliver a cursor event before the first frame, so the panel
        // rectangles must exist already.
        computeRegions();
    }

    private final String fontPath;

    /**
     * Requests a new editor chrome scale. The atlas is rebuilt at the next
     * frame boundary, never in the middle of a draw pass, so GL texture state
     * and the UI metrics stay consistent for the whole frame.
     */
    public void setUiScale(double value) {
        double clamped = Math.max(0.75, Math.min(3.0, Math.round(value * 20) / 20.0));
        if (Math.abs(clamped - ui.uiScale()) < 0.01) return;
        pendingUiScale = clamped;
    }

    private double pendingUiScale = -1;

    private void applyPendingUiScale() {
        if (pendingUiScale < 0) return;
        double value = pendingUiScale;
        pendingUiScale = -1;

        FontAtlas old = ui.font();
        try {
            FontAtlas fresh = new FontAtlas(fontPath, (int) Math.round(14 * value));
            ui.setFont(fresh, value);
            if (old != null) glDeleteTextures(old.texture);
            fire(null, "ui scale " + String.format("%.2f", value));
        } catch (RuntimeException e) {
            fire(null, "ui scale failed: " + e);
        }
    }

    /** Canvas zoom factor (1.0 = Meteor's GUI scale). */
    public double zoom() {
        return zoom;
    }

    public void setZoom(double value) {
        zoom = Math.max(0.25, Math.min(6.0, value));
    }

    /** Converts a document point to screen coordinates (tooling / self test). */
    public double[] worldToScreen(double wx, double wy) {
        return new double[]{toScreenX(wx), toScreenY(wy)};
    }

    /** Last drawn toolbar button rect, for tooling / self test. */
    public double[] toolbarButtonRect(String label) {
        double[] r = toolRects.get(label);
        return r == null ? null : r.clone();
    }

    /** Canvas rect in screen space (toolbar, palette and panels excluded). */
    public double[] canvasRect() {
        return canvasRect == null ? null : canvasRect.clone();
    }

    /** Inspector rect in screen space, for tooling and the self test. */
    public double[] inspectorRect() {
        return inspectorRect == null ? null : inspectorRect.clone();
    }

    private final java.util.Map<String, double[]> toolRects = new java.util.HashMap<>();

    /** Screen position of a resize handle, for tooling and the self test. */
    public double[] handleScreenPos(UiNode node, int index) {
        double[] p = handlePoints(node)[index];
        return new double[]{toScreenX(p[0]) + HANDLE_SIZE / 2, toScreenY(p[1]) + HANDLE_SIZE / 2};
    }

    /** World point (0,0) is the document origin. */
    private double viewX() {
        if (canvasRect == null) return 0;
        return -(canvasRect[0] + panX) / zoom;
    }

    private double viewY() {
        if (canvasRect == null) return 0;
        return -(canvasRect[1] + panY) / zoom;
    }

    private double toScreenX(double worldX) {
        return (worldX - viewX()) * zoom;
    }

    private double toScreenY(double worldY) {
        return (worldY - viewY()) * zoom;
    }

    private void updateWorldPointer() {
        worldX = mouseX / zoom + viewX();
        worldY = mouseY / zoom + viewY();
        // Tooltips are placed by the painter, which works in document space.
        renderer.setPointer(worldX, worldY);
    }

    public Mode mode() {
        return mode;
    }

    public List<String> logLines() {
        return log;
    }

    /** Hides the editor chrome (toolbar/panels) for clean offscreen renders. */
    public void setShowChrome(boolean show) {
        this.showChrome = show;
    }

    public void setModifiers(boolean ctrl, boolean shift, boolean alt) {
        this.ctrlDown = ctrl;
        this.shiftDown = shift;
        this.altDown = alt;
    }

    /** The hover text currently shown, or null (Meteor's tooltip pass). */
    public String activeTooltip() {
        return renderer.activeTooltip();
    }

    /** Id of the node the pointer is over, or null (tooling / self test). */
    public String hoveredId() {
        return hovered == null ? null : hovered.id;
    }

    /** Selects a node by id (used by tooling and the headless self test). */
    public boolean selectById(String id) {
        UiNode node = doc.find(id);
        if (node == null) return false;
        selection.clear();
        selection.add(node);
        inspectorScroll = 0;
        return true;
    }

    /** The current selection, for tooling. */
    public List<UiNode> selection() {
        return selection;
    }

    /** Screen rect of an inspector row's control, for tooling and the self test. */
    public double[] inspectorRowRect(int index) {
        double rowH = ui.lineHeight() + ui.scaled(2);
        double labelW = Math.min(ui.scaled(96), inspectorRect[2] * 0.44);
        double y = inspectorRect[1] + ui.lineHeight() - inspectorScroll + index * rowH + ui.scaled(1);
        return new double[]{inspectorRect[0] + labelW, y,
            inspectorRect[2] - labelW - ui.scaled(4), rowH - ui.scaled(2)};
    }

    /** Number of inspector rows the current selection produces (tooling / self test). */
    public int inspectorRowCount() {
        UiNode node = primary();
        return node == null ? 0 : buildRows(node).size();
    }

    /** Labels of the inspector rows the current selection produces (tooling / self test). */
    public List<String> inspectorRowLabels() {
        UiNode node = primary();
        if (node == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Row row : buildRows(node)) out.add(row.label);
        return out;
    }

    /** Opens a toolbar menu programmatically (tooling / self test). */
    public void openMenu(String name) {
        toolLastX = ui.pad();
        switch (name) {
            case "align" -> openAlignMenu();
            case "export" -> openExportMenu();
            case "open" -> openProjectMenu();
            default -> { }
        }
    }

    /** Opens the modal prompt programmatically (tooling / self test). */
    public void openNamePrompt(String title, String initial, Consumer<String> action) {
        openPrompt(title, initial, action);
    }

    public void confirmPrompt() {
        commitPrompt();
    }

    // ==================================================================
    // frame
    // ==================================================================

    public void frame(double dt) {
        applyPendingUiScale();
        computeRegions();
        ui.begin(mouseX, mouseY, mouseDown, pendingPress, pendingRelease, pendingScroll, dt);
        pendingPress = false;
        pendingRelease = false;
        pendingScroll = 0;

        renderer.tick(dt);

        // Pass 1: the design canvas, drawn in document space with the canvas zoom.
        gl.begin(zoom, viewX(), viewY());
        drawCanvasWorld();
        if (!showChrome) {
            gl.end();
            ui.end();
            return;
        }

        // Pass 2: editor chrome, drawn unscaled on top.
        gl.beginOverlay();
        if (mode == Mode.DESIGN) drawDesignOverlay();
        else drawPlayOverlay();
        drawPalette();
        drawRightPanel();
        drawToolbar();
        drawStatusBar();
        drawPopup();
        drawPrompt();
        gl.end();
        ui.end();
    }

    private void computeRegions() {
        double toolbarH = ui.lineHeight();
        double statusH = ui.lineHeight();
        double w = gl.width(), h = gl.height();

        // Keep the canvas usable in a small window: the side panels shrink
        // proportionally instead of squeezing the canvas to nothing.
        double paletteW = Math.min(ui.scaled(150), Math.max(ui.scaled(90), w * 0.2));
        double rightW = Math.min(ui.scaled(300), Math.max(ui.scaled(160), w * 0.32));
        if (w - paletteW - rightW < ui.scaled(80)) {
            double available = Math.max(0, w - ui.scaled(80));
            double total = paletteW + rightW;
            if (total > 0) {
                paletteW = available * (paletteW / total);
                rightW = available - paletteW;
            }
        }

        paletteRect = new double[]{0, toolbarH, paletteW, h - toolbarH - statusH};
        rightRect = new double[]{w - rightW, toolbarH, rightW, h - toolbarH - statusH};
        canvasRect = new double[]{paletteW, toolbarH, Math.max(1, w - paletteW - rightW),
            h - toolbarH - statusH};

        double treeH = Math.max(ui.lineHeight() * 3, canvasRect[3] * 0.42);
        treeRect = new double[]{rightRect[0], rightRect[1], rightW, treeH};
        inspectorRect = new double[]{rightRect[0], rightRect[1] + treeH, rightW, rightRect[3] - treeH};
    }

    private boolean inside(double[] r, double x, double y) {
        return r != null && x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    private boolean insidePanels(double x, double y) {
        return y <= ui.lineHeight() || inside(paletteRect, x, y) || inside(rightRect, x, y);
    }

    // ==================================================================
    // canvas
    // ==================================================================

    /** Design pass: everything here is in document coordinates. */
    private void drawCanvasWorld() {
        double x = canvasRect[0], y = canvasRect[1], w = canvasRect[2], h = canvasRect[3];

        gl.scissor((int) x, (int) y, (int) w, (int) h);
        double wx = x / zoom + viewX();
        double wy = y / zoom + viewY();
        ui.quad(wx, wy, w / zoom, h / zoom, new int[]{26, 28, 34, 255});

        renderer.render(doc.root, 0, 0);
        gl.scissorOff();
    }

    /** Chrome pass: converts a node rect from document to screen space. */
    private void borderWorld(UiNode node, int[] color, double alpha) {
        ui.border(toScreenX(node.renderX), toScreenY(node.renderY),
            node.renderW * zoom, node.renderH * zoom, color, alpha);
    }

    private void drawDesignOverlay() {
        if (hovered != null && !selection.contains(hovered)) {
            borderWorld(hovered, MeteorTheme.ACCENT, 0.6);
        }
        for (UiNode node : selection) {
            borderWorld(node, MeteorTheme.ACCENT, 1.0);
        }

        if (selection.size() == 1) {
            UiNode node = selection.get(0);
            for (double[] p : handlePoints(node)) {
                double px = toScreenX(p[0]), py = toScreenY(p[1]);
                ui.quad(px - 1, py - 1, HANDLE_SIZE + 2, HANDLE_SIZE + 2, MeteorTheme.OUTLINE);
                ui.quad(px, py, HANDLE_SIZE, HANDLE_SIZE, MeteorTheme.ACCENT);
            }
        }

        if (drag == Drag.MARQUEE) {
            double x0 = toScreenX(Math.min(marqueeX0, marqueeX1)), x1 = toScreenX(Math.max(marqueeX0, marqueeX1));
            double y0 = toScreenY(Math.min(marqueeY0, marqueeY1)), y1 = toScreenY(Math.max(marqueeY0, marqueeY1));
            float[] accent = MeteorTheme.f(MeteorTheme.ACCENT, 0.25);
            gl.colorQuad(x0, y0, x1 - x0, y1 - y0, accent[0], accent[1], accent[2], accent[3]);
            ui.border(x0, y0, x1 - x0, y1 - y0, MeteorTheme.ACCENT, 1);
        }

        for (double[] g : guides) {
            float[] c = {0.4f, 0.85f, 1f, 0.9f};
            double gx0 = toScreenX(g[0]), gx1 = toScreenX(g[2]);
            double gy0 = toScreenY(g[1]), gy1 = toScreenY(g[3]);
            gl.colorQuad(gx0, gy0, Math.max(1, gx1 - gx0), Math.max(1, gy1 - gy0), c[0], c[1], c[2], c[3]);
        }

        if (dropIndex >= 0 && dropY >= 0 && dragNode != null && dragNode.parent != null) {
            double px = toScreenX(dragNode.parent.renderX + 4);
            double py = toScreenY(dropY - 1);
            ui.quad(px, py, (dragNode.parent.renderW - 8) * zoom, Math.max(2, 2 * zoom), MeteorTheme.ACCENT);
        }

        if (textEdit != null) {
            double worldTextX;
            if (textEdit.type == UiType.NUMBER) {
                // the caret sits after the number box's live buffer
                worldTextX = numberParts(textEdit).boxX() + theme.pad()
                    + renderer.font().textWidth(MeteorPainter.displayValue(textEdit), theme.scale);
            } else {
                worldTextX = textEdit.renderX + (textEdit.type == UiType.BUTTON
                    ? textEdit.renderW / 2.0 - renderer.font().textWidth(textEdit.text, theme.scale) / 2.0 : 0);
            }
            double caretWorldX = textEdit.type == UiType.NUMBER ? worldTextX
                : worldTextX + renderer.font().textWidth(
                    textEdit.text.substring(0, Math.min(textCursor, textEdit.text.length())), theme.scale);
            ui.quad(toScreenX(caretWorldX), toScreenY(textEdit.renderY),
                Math.max(1, theme.scaled(1.75) * zoom), theme.textHeight() * zoom, MeteorTheme.TEXT);
            borderWorld(textEdit, MeteorTheme.TEXT, 1);
        }
    }

    private void drawPlayOverlay() {
        if (log.isEmpty()) return;
        double x = canvasRect[0] + ui.pad() * 2, y = canvasRect[1] + ui.pad() * 2;
        double lineH = ui.lineHeight();
        double w = ui.scaled(420);
        double h = lineH * log.size() + ui.pad() * 2;
        ui.quad(x, y, w, h, MeteorTheme.BACKGROUND);
        for (int i = 0; i < log.size(); i++) {
            ui.textDim(log.get(i), x + ui.pad(), y + ui.pad() + i * lineH);
        }
    }

    // ==================================================================
    // palette + templates
    // ==================================================================

    private void drawPalette() {
        double x = paletteRect[0], y = paletteRect[1], w = paletteRect[2], h = paletteRect[3];
        ui.panel(x, y, w, h);
        ui.header("Widgets", x, y, w, ui.lineHeight());

        double rowH = ui.lineHeight();
        double pad = ui.scaled(4);
        double contentH = ui.lineHeight() + UiType.values().length * (rowH + pad)
            + ui.lineHeight() * 2 + ui.scaled(80);

        ui.pushClip(x, y + ui.lineHeight(), w, h - ui.lineHeight());
        paletteScroll = ui.scrollOffset("palette", x, y + ui.lineHeight(), w, h - ui.lineHeight(),
            contentH, paletteScroll);

        double cy = y + ui.lineHeight() - paletteScroll + pad;
        for (UiType type : UiType.values()) {
            if (ui.button("pal-" + type.name(), x + pad, cy, w - pad * 2, rowH, type.displayName)) {
                addWidget(type);
            }
            cy += rowH + pad;
        }
        // Container style presets (Meteor WSection / WView / WTable).
        for (com.hackli.guidesigner.model.ContainerStyle style
            : new com.hackli.guidesigner.model.ContainerStyle[]{
                com.hackli.guidesigner.model.ContainerStyle.SECTION,
                com.hackli.guidesigner.model.ContainerStyle.VIEW,
                com.hackli.guidesigner.model.ContainerStyle.TABLE}) {
            if (ui.button("pal-style-" + style.name(), x + pad, cy, w - pad * 2, rowH,
                style == com.hackli.guidesigner.model.ContainerStyle.SECTION ? "Section"
                    : style == com.hackli.guidesigner.model.ContainerStyle.VIEW ? "View" : "Table")) {
                addWidget(UiType.CONTAINER, style);
            }
            cy += rowH + pad;
        }

        // A ready made settings row: the shape Meteor's own settings pages use.
        if (ui.button("pal-settings-row", x + pad, cy, w - pad * 2, rowH, "Settings Row")) {
            addSettingsRow();
        }
        cy += rowH + pad;

        cy += ui.scaled(4);
        ui.separator(x + pad, cy, w - pad * 2);
        cy += ui.scaled(4);
        ui.header("Templates", x, cy, w, ui.lineHeight());
        cy += ui.lineHeight() + pad;

        List<String> templates = TemplateStore.list();
        if (templates.isEmpty()) {
            ui.textDim("none saved yet", x + pad, cy);
            cy += rowH;
        }
        for (String name : templates) {
            if (ui.row("tpl-" + name, x + pad, cy, w - pad * 2, rowH, name, false)) {
                insertTemplate(name);
            }
            cy += rowH + pad;
        }

        if (ui.button("tpl-save", x + pad, cy, w - pad * 2, rowH, "Save selection")) {
            if (selection.isEmpty()) fire(null, "select something first");
            else openPrompt("Template name", "MyTemplate", name -> saveTemplate(name));
        }

        ui.popClip();
    }

    // ==================================================================
    // right panel: tree + inspector
    // ==================================================================

    private void drawRightPanel() {
        ui.panel(rightRect[0], rightRect[1], rightRect[2], rightRect[3]);
        drawTree();
        drawInspector();
    }

    private void drawTree() {
        double x = treeRect[0], y = treeRect[1], w = treeRect[2], h = treeRect[3];
        ui.header("Tree", x, y, w, ui.lineHeight());

        double top = y + ui.lineHeight();
        double hh = h - ui.lineHeight();
        double rowH = ui.lineHeight();
        List<TreeItem> items = new ArrayList<>();
        flatten(doc.root, 0, items);

        ui.pushClip(x, top, w, hh);
        treeScroll = ui.scrollOffset("tree", x, top, w, hh, items.size() * rowH, treeScroll);

        double ry = top - treeScroll;
        for (TreeItem item : items) {
            if (ry + rowH > top && ry < top + hh) {
                String label = "  ".repeat(item.depth()) + item.node().type.displayName + "  " + item.node().id;
                if (ui.row("tree-" + item.node().id, x, ry, w, rowH, label, selection.contains(item.node()))) {
                    if (ctrlDown || shiftDown) {
                        if (selection.contains(item.node())) selection.remove(item.node());
                        else selection.add(item.node());
                    } else {
                        selection.clear();
                        selection.add(item.node());
                    }
                }
            }
            ry += rowH;
        }
        ui.popClip();
    }

    private void drawInspector() {
        double x = inspectorRect[0], y = inspectorRect[1], w = inspectorRect[2], h = inspectorRect[3];
        ui.header("Inspector", x, y, w, ui.lineHeight());

        double top = y + ui.lineHeight();
        double hh = h - ui.lineHeight();
        UiNode node = primary();
        if (node == null) {
            ui.textDim("no selection", x + ui.pad(), top + ui.pad());
            return;
        }

        List<Row> rows = buildRows(node);
        double rowH = ui.lineHeight() + ui.scaled(2);
        double contentH = rows.size() * rowH + ui.pad();

        ui.pushClip(x, top, w, hh);
        inspectorScroll = ui.scrollOffset("inspector", x, top, w, hh, contentH, inspectorScroll);

        double labelW = Math.min(ui.scaled(96), w * 0.44);
        double ry = top - inspectorScroll;
        for (int i = 0; i < rows.size(); i++) {
            if (ry + rowH > top && ry < top + hh) drawRow(rows.get(i), i, x, ry, w, rowH, labelW, node);
            ry += rowH;
        }
        ui.popClip();
    }

    private void drawRow(Row row, int index, double x, double y, double w, double rowH, double labelW, UiNode node) {
        double pad = ui.scaled(4);
        ui.textDim(row.label, x + pad, y + (rowH - ui.textHeight()) / 2.0);

        double cx = x + labelW;
        double cw = w - labelW - pad;
        double ch = rowH - ui.scaled(2);
        double cy = y + ui.scaled(1);
        String id = "insp-" + index;

        switch (row.kind) {
            case Row.READ -> ui.text(row.get.get(), cx, y + (rowH - ui.textHeight()) / 2.0, MeteorTheme.TEXT);
            case Row.TEXT, Row.NUM -> {
                GlUi.Field f = ui.field(id, cx, cy, cw, ch, row.get.get());
                if (f.changed && row.set != null) {
                    row.set.accept(f.text);
                    markDirty("insp:" + id);
                }
            }
            case Row.CHECK -> {
                double size = Math.min(ch, ui.pad() * 2 + ui.textHeight());
                boolean value = Boolean.parseBoolean(row.get.get());
                boolean now = ui.checkbox(id, cx, y + (rowH - size) / 2.0, size, value);
                if (now != value && row.set != null) {
                    row.set.accept(String.valueOf(now));
                    markDirty(null);
                }
            }
            case Row.CYCLE -> {
                String[] options = row.options;
                String current = row.get.get();
                int idx = 0;
                for (int i = 0; i < options.length; i++) if (options[i].equals(current)) idx = i;
                int picked = ui.cycle(id, cx, cy, cw, ch, options, idx);
                if (picked >= 0 && row.set != null) {
                    row.set.accept(options[picked]);
                    markDirty(null);
                }
            }
            default -> { }
        }
    }

    /** True when the node sits directly inside a {@code WTable} container. */
    private static boolean isTableCell(UiNode n) {
        return n.parent != null
            && n.parent.style == com.hackli.guidesigner.model.ContainerStyle.TABLE;
    }

    /** The node's cell settings, created on demand. */
    private static UiNode.Cell cellOf(UiNode n) {
        return n.cell();
    }

    /** Sets or clears one GLFW modifier bit. */
    private static int setMod(int modifiers, int bit, boolean on) {
        return on ? (modifiers | bit) : (modifiers & ~bit);
    }

    private List<Row> buildRows(UiNode n) {        List<Row> rows = new ArrayList<>();
        rows.add(new Row("type", Row.READ, () -> n.type.name(), null));
        rows.add(new Row("id", Row.TEXT, () -> n.id, v -> {
            if (!v.isEmpty()) n.id = v;
        }));
        if (n.type.isContainer) {
            rows.add(new Row("style", Row.CYCLE, () -> n.style.name(),
                v -> n.style = com.hackli.guidesigner.model.ContainerStyle.valueOf(v),
                names(com.hackli.guidesigner.model.ContainerStyle.values())));
            rows.add(new Row("flow", Row.CYCLE, () -> n.orientation.name(),
                v -> n.orientation = com.hackli.guidesigner.model.Orientation.valueOf(v),
                names(com.hackli.guidesigner.model.Orientation.values())));
            if (n.style == com.hackli.guidesigner.model.ContainerStyle.SECTION) {
                rows.add(new Row("collapsed", Row.CHECK, () -> String.valueOf(n.collapsed),
                    v -> n.collapsed = Boolean.parseBoolean(v)));
            }
            rows.add(new Row("layout", Row.CYCLE, () -> n.layout.name(),
                v -> n.layout = LayoutMode.valueOf(v), names(LayoutMode.values())));
        }
        // A cell only matters inside a table: these are Meteor's Cell options that
        // drive column alignment (WTable) and width sharing.
        if (isTableCell(n)) {
            rows.add(new Row("expandX", Row.CHECK, () -> String.valueOf(cellOf(n).expandX),
                v -> cellOf(n).expandX = Boolean.parseBoolean(v)));
            rows.add(new Row("minWidth", Row.NUM, () -> fmt(cellOf(n).minWidth),
                v -> cellOf(n).minWidth = Math.max(0, parseD(v, cellOf(n).minWidth))));
            rows.add(new Row("alignX", Row.CYCLE, () -> cellOf(n).alignX.name(),
                v -> cellOf(n).alignX = AnchorX.valueOf(v), names(AnchorX.values())));
            rows.add(new Row("alignY", Row.CYCLE, () -> cellOf(n).alignY.name(),
                v -> cellOf(n).alignY = AnchorY.valueOf(v), names(AnchorY.values())));
            rows.add(new Row("newColumn", Row.CHECK, () -> String.valueOf(cellOf(n).column),
                v -> cellOf(n).column = Boolean.parseBoolean(v)));
            rows.add(new Row("endRow", Row.CHECK, () -> String.valueOf(cellOf(n).row),
                v -> cellOf(n).row = Boolean.parseBoolean(v)));
            rows.add(new Row("group", Row.TEXT, () -> cellOf(n).group, v -> cellOf(n).group = v));
            rows.add(new Row("pad", Row.NUM, () -> fmt(cellOf(n).padTop), v -> {
                double pad = Math.max(0, parseD(v, cellOf(n).padTop));
                cellOf(n).pad(pad);
            }));
        }
        rows.add(new Row("visible", Row.CHECK, () -> String.valueOf(n.visible),
            v -> n.visible = Boolean.parseBoolean(v)));
        rows.add(new Row("anchorX", Row.CYCLE, () -> n.anchorX.name(),
            v -> n.anchorX = AnchorX.valueOf(v), names(AnchorX.values())));
        rows.add(new Row("anchorY", Row.CYCLE, () -> n.anchorY.name(),
            v -> n.anchorY = AnchorY.valueOf(v), names(AnchorY.values())));
        rows.add(new Row("x", Row.NUM, () -> fmt(n.x), v -> n.x = parseD(v, n.x)));
        rows.add(new Row("y", Row.NUM, () -> fmt(n.y), v -> n.y = parseD(v, n.y)));
        rows.add(new Row("width", Row.NUM, () -> fmt(n.width), v -> n.width = Math.max(0, parseD(v, n.width))));
        rows.add(new Row("height", Row.NUM, () -> fmt(n.height), v -> n.height = Math.max(0, parseD(v, n.height))));

        if (n.type.hasText()) {
            rows.add(new Row("text", Row.TEXT, () -> n.text, v -> n.text = v));
            rows.add(new Row("textColor", Row.TEXT, () -> n.textColor, v -> n.textColor = v));            rows.add(new Row("textAlign", Row.CYCLE, () -> n.textAlign.name(),
                v -> n.textAlign = TextAlign.valueOf(v), names(TextAlign.values())));
        }
        if (n.type.hasPlaceholder()) {
            rows.add(new Row("placeholder", Row.TEXT, () -> n.placeholder, v -> n.placeholder = v));
            rows.add(new Row("maxLength", Row.NUM, () -> String.valueOf(n.maxLength),
                v -> n.maxLength = (int) parseD(v, n.maxLength)));
            rows.add(new Row("filter", Row.CYCLE, () -> n.inputFilter.name(),
                v -> n.inputFilter = InputFilter.valueOf(v), names(InputFilter.values())));
        }
        if (n.type == UiType.CHECKBOX) {
            rows.add(new Row("checked", Row.CHECK, () -> String.valueOf(n.checked),
                v -> n.checked = Boolean.parseBoolean(v)));
        }
        if (n.type == UiType.SLIDER) {
            rows.add(new Row("value", Row.NUM, () -> fmt(n.value), v -> n.value = parseD(v, n.value)));
            rows.add(new Row("min", Row.NUM, () -> fmt(n.min), v -> n.min = parseD(v, n.min)));
            rows.add(new Row("max", Row.NUM, () -> fmt(n.max), v -> n.max = parseD(v, n.max)));
        }
        if (n.type == UiType.NUMBER) {            rows.add(new Row("value", Row.NUM, () -> fmt(n.value), v -> n.value = parseD(v, n.value)));
            rows.add(new Row("min", Row.NUM, () -> fmt(n.min), v -> n.min = parseD(v, n.min)));
            rows.add(new Row("max", Row.NUM, () -> fmt(n.max), v -> n.max = parseD(v, n.max)));
            rows.add(new Row("step", Row.NUM, () -> fmt(n.step), v -> n.step = parseD(v, n.step)));
            rows.add(new Row("integer", Row.CHECK, () -> String.valueOf(n.integer),
                v -> n.integer = Boolean.parseBoolean(v)));
            rows.add(new Row("slider", Row.CHECK, () -> String.valueOf(n.showSlider),
                v -> n.showSlider = Boolean.parseBoolean(v)));
            rows.add(new Row("buttons", Row.CHECK, () -> String.valueOf(n.showButtons),
                v -> n.showButtons = Boolean.parseBoolean(v)));
        }
        if (n.type == UiType.KEYBIND) {
            rows.add(new Row("binding", Row.READ,
                () -> com.hackli.guidesigner.model.KeyNames.label(n.key, n.modifiers, n.keyIsKey), null));
            rows.add(new Row("keyCode", Row.NUM, () -> String.valueOf(n.key),
                v -> n.key = (int) parseD(v, n.key)));
            rows.add(new Row("mouse", Row.CHECK, () -> String.valueOf(!n.keyIsKey),
                v -> n.keyIsKey = !Boolean.parseBoolean(v)));
            rows.add(new Row("ctrl", Row.CHECK, () -> String.valueOf(
                (n.modifiers & com.hackli.guidesigner.model.KeyNames.MOD_CONTROL) != 0), v -> {
                n.modifiers = setMod(n.modifiers, com.hackli.guidesigner.model.KeyNames.MOD_CONTROL,
                    Boolean.parseBoolean(v));
            }));
            rows.add(new Row("shift", Row.CHECK, () -> String.valueOf(
                (n.modifiers & com.hackli.guidesigner.model.KeyNames.MOD_SHIFT) != 0), v -> {
                n.modifiers = setMod(n.modifiers, com.hackli.guidesigner.model.KeyNames.MOD_SHIFT,
                    Boolean.parseBoolean(v));
            }));
            rows.add(new Row("alt", Row.CHECK, () -> String.valueOf(
                (n.modifiers & com.hackli.guidesigner.model.KeyNames.MOD_ALT) != 0), v -> {
                n.modifiers = setMod(n.modifiers, com.hackli.guidesigner.model.KeyNames.MOD_ALT,
                    Boolean.parseBoolean(v));
            }));
            // "Click the widget, then press a key" is the real workflow; this is
            // here so a binding can be typed in as well.
            rows.add(new Row("keyName", Row.TEXT,
                () -> com.hackli.guidesigner.model.KeyNames.keyName(n.key),
                v -> n.key = com.hackli.guidesigner.model.KeyNames.codeOf(v)));
        }
        if (n.type == UiType.DROPDOWN) {
            rows.add(new Row("options", Row.TEXT, () -> String.join(", ", n.options), v -> {
                n.options.clear();
                for (String part : v.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) n.options.add(trimmed);
                }
                if (n.selected == null || !n.options.contains(n.selected)) {
                    n.selected = n.options.isEmpty() ? null : n.options.get(0);
                }
            }));
            String[] opts = n.options.isEmpty() ? new String[]{""} : n.options.toArray(new String[0]);
            rows.add(new Row("selected", Row.CYCLE, () -> n.selected == null ? "" : n.selected,
                v -> n.selected = v, opts));
        }
        if (n.type == UiType.ITEM) {
            rows.add(new Row("item", Row.TEXT, () -> n.itemId, v -> n.itemId = v));
            rows.add(new Row("count", Row.NUM, () -> String.valueOf(n.itemCount),
                v -> n.itemCount = (int) parseD(v, n.itemCount)));
            rows.add(new Row("icon", Row.READ, () -> {
                String texture = com.hackli.guidesigner.assets.AssetStore.get().textureOfItem(n.itemId);
                return texture == null ? "(no texture)" : texture;
            }, null));
        }
        if (n.type == UiType.ENTITY) {
            rows.add(new Row("entity", Row.TEXT, () -> n.entityId, v -> n.entityId = v));
            rows.add(new Row("spawnEgg", Row.READ, () -> {
                String egg = com.hackli.guidesigner.assets.AssetStore.get().spawnEggOf(n.entityId);
                return egg == null ? "(none - placeholder)" : egg;
            }, null));
        }
        if (n.type == UiType.SELECT) {
            rows.add(new Row("label", Row.TEXT,
                () -> n.text.isEmpty() ? "Select" : n.text, v -> n.text = v));
            rows.add(new Row("selected", Row.NUM, () -> String.valueOf(n.itemCountSelected),
                v -> n.itemCountSelected = (int) parseD(v, n.itemCountSelected)));
        }
        rows.add(new Row("handler", Row.TEXT, () -> n.handler, v -> n.handler = v));
        rows.add(new Row("tooltip", Row.TEXT, () -> n.tooltip, v -> n.tooltip = v));
        return rows;
    }

    // ==================================================================
    // toolbar / status / popup / prompt
    // ==================================================================

    private void drawToolbar() {
        double h = ui.lineHeight();
        double pad = ui.scaled(4);
        ui.panel(0, 0, gl.width(), h);

        double x = pad, y = 0;

        if (ui.button("tb-mode", x, y, btnW(mode == Mode.DESIGN ? "Design" : "Play"), h,
            mode == Mode.DESIGN ? "Design" : "Play")) {
            toggleMode();
        }
        toolRects.put(mode == Mode.DESIGN ? "Design" : "Play",
            new double[]{x, y, btnW(mode == Mode.DESIGN ? "Design" : "Play"), h});
        x += btnW("Design") + pad;

        x = tool(x, y, h, pad, "New", this::newDocument);
        x = tool(x, y, h, pad, "Open", this::openProjectMenu);
        x = tool(x, y, h, pad, "Save", this::save);
        x = tool(x, y, h, pad, "Save As", () -> openPrompt("File name", file.getFileName().toString(),
            this::saveAs));
        x = tool(x, y, h, pad, "Export", this::openExportMenu);
        x = tool(x, y, h, pad, "Undo", () -> undo(false));
        x = tool(x, y, h, pad, "Redo", this::redo);
        x = tool(x, y, h, pad, "Align", this::openAlignMenu);
        x = tool(x, y, h, pad, snap ? "Snap: on" : "Snap: off", () -> snap = !snap);
        x = tool(x, y, h, pad, "UI -", () -> setUiScale(ui.uiScale() - 0.25));
        x = tool(x, y, h, pad, "UI +", () -> setUiScale(ui.uiScale() + 0.25));
        x = tool(x, y, h, pad, "Reset view", () -> {
            panX = 24;
            panY = 24;
            zoom = 1.0;
        });

        String title = (dirty ? "*" : "") + doc.name + "  -  " + file.getFileName();
        ui.textDim(title, x + pad, (h - ui.textHeight()) / 2.0);
    }

    private double toolLastX;

    private void openProjectMenu() {
        String[] projects = DocumentStore.listProjects().toArray(new String[0]);
        if (projects.length == 0) {
            fire(null, "no projects in " + DocumentStore.displayPath(DocumentStore.projectsDir()));
            return;
        }
        openPopup("open", toolLastX, ui.lineHeight(), projects, i -> openProject(projects[i]));
    }

    private void openExportMenu() {
        openPopup("export", toolLastX, ui.lineHeight(),
            new String[]{"Java (runtime)", "Meteor (static)", "JSON + schema"},
            this::export);
    }

    private void openAlignMenu() {
        AlignTools.Align[] all = AlignTools.Align.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
        openPopup("align", toolLastX, ui.lineHeight(), labels, i -> align(all[i]));
    }

    private double btnW(String label) {
        return ui.textWidth(label) + ui.pad() * 2;
    }

    private double tool(double x, double y, double h, double pad, String label, Runnable action) {
        double w = btnW(label);
        toolLastX = x;
        toolRects.put(label, new double[]{x, y, w, h});
        if (ui.button("tb-" + label, x, y, w, h, label)) action.run();
        return x + w + pad;
    }

    private void drawStatusBar() {
        double h = ui.lineHeight();
        double y = gl.height() - h;
        ui.panel(0, y, gl.width(), h);

        String sel = selection.isEmpty() ? "-" : (selection.size() == 1
            ? selection.get(0).type.name().toLowerCase() + " " + selection.get(0).id
            : selection.size() + " selected");
        String hint = mode == Mode.DESIGN
            ? "F5 play | drag = move/reorder | alt+drag free | dbl-click text | Ctrl+scroll zoom | Ctrl+C/V copy/paste | Ctrl+S save | Ctrl+Q quit"
            : "F5 design | click widgets | scroll slider | Esc close dropdown";
        String line = String.format("%s  |  %s  |  %d,%d  |  zoom %.2fx  |  %s", mode.name(), sel,
            (int) mouseX, (int) mouseY, zoom, hint);
        ui.textDim(line, ui.pad(), y + ui.pad());
    }

    private void openPopup(String id, double x, double y, String[] items, Consumer<Integer> action) {
        popupId = id;
        popupX = x;
        popupY = y;
        popupItems = items;
        popupAction = action;
        // The click that opened the menu is still "pressed" this frame; without
        // this flag the auto-close below would shut it again immediately.
        popupFresh = true;
    }

    /** Whether a toolbar menu is currently open (tooling / self test). */
    public boolean menuOpen() {
        return popupItems != null;
    }

    /** Screen rect of a menu item, for tooling / self test. */
    public double[] menuItemRect(int index) {
        if (popupItems == null || index < 0 || index >= popupItems.length) return null;
        return new double[]{popupRect[0], popupRect[1] + index * popupRect[3], popupRect[2], popupRect[3]};
    }

    private boolean popupFresh;
    private final double[] popupRect = new double[4];

    private void drawPopup() {
        if (popupItems == null) return;

        double rowH = ui.lineHeight();
        double w = ui.scaled(150);
        for (String item : popupItems) w = Math.max(w, ui.textWidth(item) + ui.pad() * 4);
        double h = rowH * popupItems.length;

        double x = Math.min(popupX, gl.width() - w - 2);
        double y = popupY;
        if (y + h > gl.height() - ui.lineHeight()) y = gl.height() - ui.lineHeight() - h;

        popupRect[0] = x;
        popupRect[1] = y;
        popupRect[2] = w;
        popupRect[3] = rowH;

        boolean fresh = popupFresh;
        popupFresh = false;

        ui.quad(x, y, w, h, MeteorTheme.BACKGROUND);
        ui.border(x, y, w, h, MeteorTheme.OUTLINE, 1);

        boolean anyHover = false;
        for (int i = 0; i < popupItems.length; i++) {
            double ry = y + i * rowH;
            if (ui.row("pop-" + i, x, ry, w, rowH, popupItems[i], false)) {
                Consumer<Integer> action = popupAction;
                closePopup();
                action.accept(i);
                return;
            }
            anyHover |= mouseX >= x && mouseX <= x + w && mouseY >= ry && mouseY <= ry + rowH;
        }

        if (!fresh && ui.mousePressed && !anyHover) closePopup();
    }
    private void closePopup() {
        popupItems = null;
        popupAction = null;
    }

    private void openPrompt(String title, String initial, Consumer<String> action) {
        promptTitle = title;
        promptValue = initial;
        promptAction = action;
        ui.focusField(promptFieldId, initial);
    }

    private void drawPrompt() {
        if (promptTitle == null) return;

        double w = ui.scaled(320), h = ui.lineHeight() * 3 + ui.pad() * 3;
        double x = (gl.width() - w) / 2, y = (gl.height() - h) / 2;

        ui.quad(x, y, w, h, MeteorTheme.BACKGROUND);
        ui.border(x, y, w, h, MeteorTheme.ACCENT, 1);
        ui.header(promptTitle, x, y, w, ui.lineHeight());

        GlUi.Field f = ui.field(promptFieldId, x + ui.pad(), y + ui.lineHeight() + ui.pad(),
            w - ui.pad() * 2, ui.lineHeight(), promptValue);
        if (f.changed) promptValue = f.text;

        double by = y + h - ui.lineHeight() - ui.pad();
        double bw = ui.scaled(80);
        if (ui.button("prompt-ok", x + w - bw * 2 - ui.pad() * 2, by, bw, ui.lineHeight(), "OK")) {
            commitPrompt();
        }
        if (ui.button("prompt-cancel", x + w - bw - ui.pad(), by, bw, ui.lineHeight(), "Cancel")) {
            cancelPrompt();
        }
    }

    private void commitPrompt() {
        String value = promptValue == null ? "" : promptValue.trim();
        Consumer<String> action = promptAction;
        promptTitle = null;
        promptAction = null;
        ui.blur();
        if (!value.isEmpty() && action != null) action.accept(value);
    }

    private void cancelPrompt() {
        promptTitle = null;
        promptAction = null;
        ui.blur();
    }

    // ==================================================================
    // input
    // ==================================================================

    private boolean pendingPress, pendingRelease;
    private double pendingScroll;

    public void onMouseMove(double mx, double my) {
        double dx = mx - mouseX, dy = my - mouseY;
        mouseX = mx;
        mouseY = my;
        updateWorldPointer();

        switch (drag) {
            case MOVE -> applyMove();
            case RESIZE -> applyResize();
            case MARQUEE -> {
                marqueeX1 = worldX;
                marqueeY1 = worldY;
            }
            case SLIDER -> {
                // keep the hover state in sync with the pointer while dragging
                renderer.setHovered(insidePanels(mx, my) ? null : hovered);
                if (dragNode != null) {
                    if (dragNumber) {
                        MeteorPainter.NumberParts parts = numberParts(dragNode);
                        dragNode.value = MeteorPainter.numberValueAt(dragNode,
                            parts.fraction(worldX, theme.handleSize()));
                        dirty = true;
                    } else {
                        setSliderValue(dragNode, (worldX - dragNode.renderX) / Math.max(1, dragNode.renderW));
                    }
                }
            }
            case PAN -> {
                panX += dx;
                panY += dy;
            }
            case NONE -> {
                if (!insidePanels(mx, my)) hovered = hit(worldX, worldY);
                // The painter draws state colours and tooltips from this, so both
                // design and play mode get them.
                renderer.setHovered(insidePanels(mx, my) ? null : hovered);
            }
        }
    }

    public void onMouseDown(double mx, double my, int button) {
        mouseX = mx;
        mouseY = my;
        updateWorldPointer();
        pendingPress = true;
        mouseDown = true;
        downScreenX = mx;
        downScreenY = my;

        if (button == 2) {
            // Right-click clears a keybind waiting for input (Meteor's onClear).
            if (listeningKeybind != null) {
                listeningKeybind.key = -2;      // Keybind "None"
                stopListeningKeybind();
                markDirty("keybind:clear");
                return;
            }
            drag = Drag.PAN;
            return;
        }
        if (button != 0) return;

        if (promptTitle != null) return;              // modal
        if (popupItems != null) return;               // popup handles it
        if (insidePanels(mx, my)) return;             // panels handle it

        if (mode == Mode.PLAY) {
            playClick(worldX, worldY);
            return;
        }

        if (!selection.isEmpty()) {
            int h = handleAt();
            if (h >= 0) {
                beginResize(h);
                return;
            }
        }

        UiNode hit = hit(worldX, worldY);
        boolean additive = ctrlDown || shiftDown;
        long now = System.currentTimeMillis();
        boolean doubleClick = hit != null && hit == hovered && now - lastClickTime < 400
            && Math.abs(mx - lastClickX) < 4 && Math.abs(my - lastClickY) < 4;
        lastClickTime = now;
        lastClickX = mx;
        lastClickY = my;

        // A section header collapses / expands instead of selecting.
        if (com.hackli.guidesigner.render.MeteorLayout.isSectionHeader(hit, worldX, worldY, theme)) {
            hit.collapsed = !hit.collapsed;
            markDirty(null);
            fire(hit, "section " + hit.id + (hit.collapsed ? " collapsed" : " expanded"));
            return;
        }

        if (hit == null) {
            if (!additive) {
                selection.clear();
                ui.blur();
            }
            textEdit = null;
            drag = Drag.MARQUEE;
            marqueeX0 = marqueeX1 = worldX;
            marqueeY0 = marqueeY1 = worldY;
            return;
        }

        if (doubleClick && hit.type.hasText()) {
            textEdit = hit;
            textCursor = hit.text.length();
            return;
        }

        if (additive) {
            if (selection.contains(hit)) selection.remove(hit);
            else selection.add(hit);
            ui.blur();
            return;
        }

        if (!selection.contains(hit)) {
            selection.clear();
            selection.add(hit);
            ui.blur();
            inspectorScroll = 0;
        }
        textEdit = null;
        beginMove();
    }

    public void onMouseUp(double mx, double my, int button) {
        mouseX = mx;
        mouseY = my;
        updateWorldPointer();
        pendingRelease = true;
        mouseDown = false;

        switch (drag) {
            case MOVE, RESIZE -> undoer.record(doc, null);
            case MARQUEE -> selectInMarquee();
            case SLIDER -> {
                if (dragNode != null) {
                    if (dragNumber) {
                        fire(dragNode, "number " + dragNode.id + " = " + MeteorPainter.formatNumber(dragNode));
                        undoer.record(doc, "number:" + dragNode.id);
                    } else {
                        fire(dragNode, "slider " + dragNode.id + " = " + fmt(dragNode.value));
                        undoer.record(doc, "slider:" + dragNode.id);
                    }
                }
            }
            default -> { }
        }

        if (mode == Mode.PLAY && drag == Drag.NONE) {
            UiNode hit = hit(worldX, worldY);
            renderer.setPressed(null);
            if (hit != null) playRelease(hit);
        }

        drag = Drag.NONE;
        handle = -1;
        dragNode = null;
        dragNumber = false;
        dragOrigin.clear();
        guides.clear();
        dropIndex = -1;
        dropY = -1;
    }

    public void onScroll(double amount) {
        pendingScroll += amount;

        // A WView under the pointer scrolls its content instead of the canvas.
        if (!insidePanels(mouseX, mouseY)) {
            UiNode view = com.hackli.guidesigner.render.MeteorLayout.viewAt(doc.root, worldX, worldY);
            if (view != null && com.hackli.guidesigner.render.MeteorLayout.scroll(view,
                -amount * ui.scaled(24))) {
                return;
            }
        }

        if (!insidePanels(mouseX, mouseY)) {
            if (ctrlDown) {
                // zoom around the pointer: the document point under the cursor stays put
                double factor = Math.pow(1.15, amount);
                double newZoom = Math.max(0.25, Math.min(6.0, zoom * factor));
                if (newZoom != zoom) {
                    zoom = newZoom;
                    panX = mouseX - canvasRect[0] - worldX * zoom;
                    panY = mouseY - canvasRect[1] - worldY * zoom;
                    updateWorldPointer();
                }
            } else if (shiftDown) {
                panX += amount * ui.scaled(36);
            } else {
                panY += amount * ui.scaled(36);
            }
        }
    }

    public void onKey(int key, boolean ctrl, boolean shift, boolean alt) {
        setModifiers(ctrl, shift, alt);

        // A keybind waiting for input swallows the next key press, exactly like
        // Meteor's WKeybind.onAction. Esc cancels instead of binding.
        if (listeningKeybind != null && key != Keys.ESCAPE) {
            if (key != Keys.LEFT_SHIFT && key != Keys.LEFT_CONTROL && key != Keys.LEFT_ALT
                && key != Keys.RIGHT_SHIFT && key != Keys.RIGHT_CONTROL && key != Keys.RIGHT_ALT) {
                listeningKeybind.key = key;
                listeningKeybind.keyIsKey = true;
                listeningKeybind.modifiers = modifiersOf(ctrl, shift, alt);
                fire(listeningKeybind, "keybind " + listeningKeybind.id + " = "
                    + com.hackli.guidesigner.model.KeyNames.label(listeningKeybind.key,
                        listeningKeybind.modifiers, true));
                markDirty("keybind:" + listeningKeybind.id);
            }
            stopListeningKeybind();
            return;
        }
        if (listeningKeybind != null && key == Keys.ESCAPE) {
            stopListeningKeybind();
            return;
        }

        if (promptTitle != null) {
            if (key == Keys.ENTER) commitPrompt();
            else if (key == Keys.ESCAPE) cancelPrompt();
            else ui.key(key, ctrl);
            return;
        }

        if (popupItems != null && key == Keys.ESCAPE) {
            closePopup();
            return;
        }

        if (textEdit != null && mode == Mode.DESIGN) {
            if (key == Keys.ENTER) {
                if (textEdit.type == UiType.NUMBER) commitNumber(textEdit);
                else textEdit = null;
                undoer.record(doc, null);
                return;
            }
            if (key == Keys.ESCAPE) {
                textEdit.editText = null;
                textEdit = null;
                renderer.setFocused(null);
                return;
            }
            if (key == Keys.BACKSPACE) {
                if (textEdit.type == UiType.NUMBER) {
                    String buffer = MeteorPainter.displayValue(textEdit);
                    if (!buffer.isEmpty()) {
                        textEdit.editText = buffer.substring(0, buffer.length() - 1);
                        dirty = true;
                    }
                    return;
                }
                if (textCursor > 0 && textCursor <= textEdit.text.length()) {
                    textEdit.text = textEdit.text.substring(0, textCursor - 1) + textEdit.text.substring(textCursor);
                    textCursor--;
                    markDirty("text:" + textEdit.id);
                }
                return;
            }
            if (textEdit.type == UiType.NUMBER) {
                if (key == Keys.LEFT || key == Keys.RIGHT) return;   // no caret in the buffer
                if (key == 45 && !MeteorPainter.displayValue(textEdit).contains("-")) {
                    textEdit.editText = "-" + MeteorPainter.displayValue(textEdit);
                    dirty = true;
                }
                return;
            }
            if (key == Keys.LEFT) {
                textCursor = Math.max(0, textCursor - 1);
                return;
            }
            if (key == Keys.RIGHT) {
                textCursor = Math.min(textEdit.text.length(), textCursor + 1);
                return;
            }
            return;
        }

        if (ui.key(key, ctrl)) return;

        switch (key) {
            case Keys.F5 -> {
                toggleMode();
                return;
            }
            case Keys.ESCAPE -> {
                if (mode == Mode.PLAY && renderer.openDropdown() != null) {
                    renderer.setOpenDropdown(null);
                    return;
                }
                if (listeningKeybind != null) {
                    stopListeningKeybind();
                    return;
                }
                UiNode focused = renderer.focused();
                if (focused != null && focused.type == UiType.NUMBER && focused.editText != null) {
                    focused.editText = null;
                    renderer.setFocused(null);
                    return;
                }
                selection.clear();
                return;
            }
            case Keys.ENTER -> {
                UiNode focused = renderer.focused();
                if (focused != null && focused.type == UiType.NUMBER && focused.editText != null) {
                    commitNumber(focused);
                }
                return;
            }
            case Keys.TAB -> {
                cycleSelection(shift ? -1 : 1);
                return;
            }
            case Keys.DELETE, Keys.BACKSPACE -> {
                if (mode == Mode.DESIGN) deleteSelection();
                return;
            }
            case Keys.S -> {
                if (ctrl) save();
                return;
            }
            case Keys.Z -> {
                if (ctrl) undo(shift);
                return;
            }
            case Keys.Y -> {
                if (ctrl) redo();
                return;
            }
            case Keys.D -> {
                if (ctrl) duplicateSelection();
                return;
            }
            case Keys.A -> {
                if (ctrl) {
                    selection.clear();
                    selection.addAll(doc.root.children);
                }
                return;
            }
            case Keys.C -> {
                if (ctrl) copySelection();
                return;
            }
            case Keys.X -> {
                if (ctrl) {
                    copySelection();
                    deleteSelection();
                }
                return;
            }
            case Keys.V -> {
                if (ctrl) pasteClipboard();
                return;
            }
            case Keys.SPACE -> {
                if (mode == Mode.DESIGN) toggleChecked();
                return;
            }
            case Keys.LEFT_BRACKET -> {
                adjustSlider(-1);
                return;
            }
            case Keys.RIGHT_BRACKET -> {
                adjustSlider(1);
                return;
            }
            default -> { }
        }

        if (mode == Mode.PLAY) return;

        double step = shift ? 10 : 1;
        double dx = 0, dy = 0;
        switch (key) {
            case Keys.LEFT -> dx = -step;
            case Keys.RIGHT -> dx = step;
            case Keys.UP -> dy = -step;
            case Keys.DOWN -> dy = step;
            default -> { return; }
        }
        if (selection.isEmpty()) return;

        boolean converted = false;
        for (UiNode node : selection) {
            if (node == doc.root) continue;
            if (node.parent != null && node.parent.layout != LayoutMode.ABSOLUTE) {
                freeLayout(node.parent);
                converted = true;
            }
        }
        for (UiNode node : selection) {
            if (node == doc.root) continue;
            node.x += dx;
            node.y += dy;
        }
        markDirty("nudge");
        if (converted) fire(null, "parent switched to free (absolute) layout");
    }

    public void onChar(int codepoint) {
        if (promptTitle != null) {
            ui.typeChar(codepoint);
            return;
        }

        if (mode == Mode.PLAY) {
            UiNode focused = renderer.focused();
            if (focused != null && focused.type == UiType.TEXTBOX) {
                String c = new String(Character.toChars(codepoint));
                if (accepts(focused, c) && (focused.maxLength <= 0 || focused.text.length() < focused.maxLength)) {
                    focused.text += c;
                    markDirty("type:" + focused.id);
                    fire(focused, "text " + focused.id + " = \"" + focused.text + "\"");
                }
            } else if (focused != null && focused.type == UiType.NUMBER) {
                String c = new String(Character.toChars(codepoint));
                String buffer = MeteorPainter.displayValue(focused);
                if ((c.equals("-") && !buffer.contains("-"))
                    || (c.equals(".") && !focused.integer && !buffer.contains("."))
                    || c.chars().allMatch(Character::isDigit)) {
                    focused.editText = buffer + c;
                    dirty = true;
                    fire(focused, "typing " + focused.id + " = " + focused.editText);
                }
            }
            return;
        }

        if (ui.typeChar(codepoint)) return;

        if (textEdit == null) return;
        String c = new String(Character.toChars(codepoint));
        if (textEdit.type == UiType.NUMBER) {
            String buffer = MeteorPainter.displayValue(textEdit);
            if (c.equals("-") && buffer.contains("-")) return;
            if (c.equals(".") && (textEdit.integer || buffer.contains("."))) return;
            if (!c.chars().allMatch(Character::isDigit) && !c.equals("-") && !c.equals(".")) return;
            textEdit.editText = buffer + c;
            dirty = true;
            return;
        }
        if (!accepts(textEdit, c)) return;
        if (textEdit.maxLength > 0 && textEdit.text.length() >= textEdit.maxLength) return;
        textEdit.text = textEdit.text.substring(0, textCursor) + c + textEdit.text.substring(textCursor);
        textCursor++;
        markDirty("text:" + textEdit.id);
    }

    // ==================================================================
    // editing operations
    // ==================================================================

    private UiNode primary() {
        return selection.isEmpty() ? null : selection.get(0);
    }

    private void markDirty(String editKey) {
        dirty = true;
        undoer.record(doc, editKey);
    }

    private void beginMove() {
        drag = Drag.MOVE;
        dragStartX = worldX;
        dragStartY = worldY;
        dragOrigin.clear();
        dragNode = selection.isEmpty() ? null : selection.get(0);

        if (dragNode == doc.root) {
            dragOrigin.add(new double[]{panX, panY, 0, 0, 0, 0});
            return;
        }
        for (UiNode node : selection) {
            dragOrigin.add(new double[]{node.x, node.y, node.renderX, node.renderY,
                node.renderW, node.renderH});
        }
    }

    private void beginResize(int h) {
        drag = Drag.RESIZE;
        handle = h;
        dragStartX = worldX;
        dragStartY = worldY;
        dragNode = selection.get(0);
        dragOrigin.clear();
        dragOrigin.add(new double[]{dragNode.x, dragNode.y, dragNode.renderX, dragNode.renderY,
            dragNode.renderW, dragNode.renderH});
        if (dragNode.parent != null && dragNode.parent.layout != LayoutMode.ABSOLUTE) {
            freeLayout(dragNode.parent);
            dragOrigin.set(0, new double[]{dragNode.x, dragNode.y, dragNode.renderX, dragNode.renderY,
                dragNode.renderW, dragNode.renderH});
        }
    }

    private void applyMove() {
        if (dragNode == doc.root) {
            if (dragOrigin.isEmpty()) return;
            // dragging the root pans the canvas, in screen pixels
            panX = dragOrigin.get(0)[0] + (worldX - dragStartX) * zoom;
            panY = dragOrigin.get(0)[1] + (worldY - dragStartY) * zoom;
            return;
        }

        boolean free = altDown || dragNode == null || dragNode.parent == null
            || dragNode.parent.layout == LayoutMode.ABSOLUTE;

        if (!free && dragNode.parent != null) {
            // Inside a flow panel a vertical drag reorders, a clearly horizontal
            // drag means "place it freely", so break out of the flow.
            double sx = mouseX - downScreenX, sy = mouseY - downScreenY;
            if (Math.abs(sx) <= Math.abs(sy) + 6) {
                applyFlowReorder();
                return;
            }
        }

        if (dragNode.parent != null && dragNode.parent.layout != LayoutMode.ABSOLUTE) {
            freeLayout(dragNode.parent);
            dragOrigin.clear();
            for (UiNode node : selection) {
                dragOrigin.add(new double[]{node.x, node.y, node.renderX, node.renderY,
                    node.renderW, node.renderH});
            }
        }

        double mdx = worldX - dragStartX;
        double mdy = worldY - dragStartY;

        guides.clear();
        boolean snapArmed = Math.hypot(mouseX - downScreenX, mouseY - downScreenY) > SNAP_ARM_PX;
        if (snap && !altDown && snapArmed && !dragOrigin.isEmpty()) {
            double[] target = {dragOrigin.get(0)[2] + mdx, dragOrigin.get(0)[3] + mdy,
                dragNode.renderW, dragNode.renderH};
            double[] adjusted = snapRect(target, dragNode);
            mdx += adjusted[0];
            mdy += adjusted[1];
        }

        for (int i = 0; i < selection.size() && i < dragOrigin.size(); i++) {
            UiNode node = selection.get(i);
            if (node == doc.root) continue;
            node.x = dragOrigin.get(i)[0] + mdx;
            node.y = dragOrigin.get(i)[1] + mdy;
        }
        dirty = true;
    }

    private void applyFlowReorder() {
        UiNode parent = dragNode.parent;
        if (parent == null) return;

        int index = parent.children.indexOf(dragNode);
        if (index < 0) return;

        int target = index;
        for (int i = 0; i < parent.children.size(); i++) {
            UiNode sibling = parent.children.get(i);
            if (sibling == dragNode) continue;
            if (worldY < sibling.renderY + sibling.renderH / 2.0) {
                target = i;
                break;
            }
            target = i + 1;
        }
        target = Math.max(0, Math.min(parent.children.size() - 1, target));

        if (target != index) {
            parent.children.remove(index);
            parent.children.add(target, dragNode);
            dirty = true;
        }
        dropIndex = target;
        dropY = target < parent.children.size()
            ? parent.children.get(target).renderY - UiLayout.GAP / 2.0
            : (parent.children.isEmpty() ? parent.renderY
                : parent.children.get(parent.children.size() - 1).renderY
                    + parent.children.get(parent.children.size() - 1).renderH + UiLayout.GAP / 2.0);
    }

    private void applyResize() {
        if (dragNode == null || dragOrigin.isEmpty()) return;
        double[] o = dragOrigin.get(0);

        // Always start from the rect captured when the drag began: using the
        // live render size would re-apply the whole delta on every frame.
        double x = o[2], y = o[3], w = o[4], h = o[5];
        double mdx = worldX - dragStartX;
        double mdy = worldY - dragStartY;

        switch (handle) {
            case HANDLE_NW -> { x += mdx; y += mdy; w -= mdx; h -= mdy; }
            case HANDLE_N -> { y += mdy; h -= mdy; }
            case HANDLE_NE -> { y += mdy; w += mdx; h -= mdy; }
            case HANDLE_E -> w += mdx;
            case HANDLE_SE -> { w += mdx; h += mdy; }
            case HANDLE_S -> h += mdy;
            case HANDLE_SW -> { x += mdx; w -= mdx; h += mdy; }
            case HANDLE_W -> { x += mdx; w -= mdx; }
            default -> { }
        }

        if (w < 8) {
            if (handle == HANDLE_NW || handle == HANDLE_SW || handle == HANDLE_W) x -= 8 - w;
            w = 8;
        }
        if (h < 8) {
            if (handle == HANDLE_NW || handle == HANDLE_NE || handle == HANDLE_N) y -= 8 - h;
            h = 8;
        }

        if (snap && !altDown) {
            double[] r = snapRect(new double[]{x, y, w, h}, dragNode);
            boolean horizontal = handle == HANDLE_NW || handle == HANDLE_NE || handle == HANDLE_SW
                || handle == HANDLE_SE || handle == HANDLE_W || handle == HANDLE_E;
            if (horizontal) {
                if (handle == HANDLE_NW || handle == HANDLE_SW || handle == HANDLE_W) {
                    x += r[0];
                    w -= r[0];
                } else {
                    w += r[0];
                }
            } else {
                if (handle == HANDLE_NW || handle == HANDLE_NE || handle == HANDLE_N) {
                    y += r[1];
                    h -= r[1];
                } else {
                    h += r[1];
                }
            }
            w = Math.max(8, w);
            h = Math.max(8, h);
        }

        dragNode.width = w;
        dragNode.height = h;
        dragNode.x = x;
        dragNode.y = y;
        dirty = true;
    }

    private double[] snapRect(double[] rect, UiNode self) {
        double bx = 0, by = 0;
        double bestX = SNAP + 1, bestY = SNAP + 1;
        double rx = rect[0], ry = rect[1], rw = rect[2], rh = rect[3];

        List<UiNode> candidates = new ArrayList<>();
        if (self.parent != null) {
            candidates.add(self.parent);
            candidates.addAll(self.parent.children);
        }
        candidates.add(doc.root);

        for (UiNode other : candidates) {
            if (other == self || isDescendant(other, self)) continue;
            double ox = other.renderX, oy = other.renderY, ow = other.renderW, oh = other.renderH;

            double[] xs = {ox, ox + ow, ox + ow / 2, ox + ow / 2};
            double[] rxs = {rx, rx + rw, rx + rw / 2, rx};
            for (int i = 0; i < xs.length; i++) {
                double d = xs[i] - rxs[i];
                if (Math.abs(d) < bestX) {
                    bestX = Math.abs(d);
                    bx = d;
                }
            }
            double[] ys = {oy, oy + oh, oy + oh / 2, oy + oh / 2};
            double[] rys = {ry, ry + rh, ry + rh / 2, ry};
            for (int i = 0; i < ys.length; i++) {
                double d = ys[i] - rys[i];
                if (Math.abs(d) < bestY) {
                    bestY = Math.abs(d);
                    by = d;
                }
            }
        }

        if (bestX <= SNAP) {
            guides.add(new double[]{rect[0] + bx, rect[1] - 2000, rect[0] + bx, rect[1] + 2000});
        } else {
            bx = 0;
        }
        if (bestY <= SNAP) {
            guides.add(new double[]{rect[0] - 2000, rect[1] + by, rect[0] + 2000, rect[1] + by});
        } else {
            by = 0;
        }
        return new double[]{bx, by};
    }

    private void selectInMarquee() {
        double x0 = Math.min(marqueeX0, marqueeX1), x1 = Math.max(marqueeX0, marqueeX1);
        double y0 = Math.min(marqueeY0, marqueeY1), y1 = Math.max(marqueeY0, marqueeY1);
        selection.clear();
        collectIntersecting(doc.root, new UiRect(x0, y0, x1 - x0, y1 - y0));
        if (!selection.isEmpty()) fire(null, selection.size() + " selected");
    }

    private void collectIntersecting(UiNode node, UiRect r) {
        if (!node.visible) return;
        UiRect n = new UiRect(node.renderX, node.renderY, node.renderW, node.renderH);
        if (n.x < r.x + r.w && n.x + n.w > r.x && n.y < r.y + r.h && n.y + n.h > r.y) {
            if (node != doc.root) selection.add(node);
        }
        for (UiNode child : node.children) collectIntersecting(child, r);
    }

    private void freeLayout(UiNode parent) {
        if (parent.layout == LayoutMode.ABSOLUTE) return;
        parent.layout = LayoutMode.ABSOLUTE;
        for (UiNode child : parent.children) {
            double w = child.renderW > 0 ? child.renderW : child.type.defaultWidth;
            double h = child.renderH > 0 ? child.renderH : child.type.defaultHeight;
            child.anchorX = AnchorX.LEFT;
            child.anchorY = AnchorY.TOP;
            child.x = child.renderX - parent.renderX;
            child.y = child.renderY - parent.renderY;
            child.width = w;
            child.height = h;
        }
        markDirty(null);
    }

    private UiNode clipboard;

    private void copySelection() {
        UiNode node = primary();
        if (node == null || node == doc.root) {
            fire(null, "nothing to copy");
            return;
        }
        clipboard = com.hackli.guidesigner.runtime.EditorOps.copyOf(node, doc.root);
        fire(null, "copied " + node.id);
    }

    private void pasteClipboard() {
        if (clipboard == null) {
            fire(null, "clipboard is empty");
            return;
        }
        UiNode parent = com.hackli.guidesigner.runtime.EditorOps.pasteTarget(doc, selection);
        UiNode copy = com.hackli.guidesigner.runtime.EditorOps.paste(doc, clipboard, parent);
        if (copy == null) return;
        selection.clear();
        selection.add(copy);
        markDirty(null);
        fire(null, "pasted " + copy.id + " into " + parent.id);
    }

    private void deleteSelection() {
        boolean changed = false;
        for (UiNode node : new ArrayList<>(selection)) {
            if (node == doc.root || node.parent == null) continue;
            node.parent.children.remove(node);
            changed = true;
        }
        selection.clear();
        if (changed) {
            markDirty(null);
            fire(null, "deleted");
        }
    }

    private void duplicateSelection() {
        List<UiNode> copies = new ArrayList<>();
        for (UiNode node : new ArrayList<>(selection)) {
            if (node.parent == null) continue;
            UiNode copy = node.copyWithFreshIds(doc.root);
            copy.x += 12;
            copy.y += 12;
            node.parent.child(copy);
            copies.add(copy);
        }
        if (!copies.isEmpty()) {
            selection.clear();
            selection.addAll(copies);
            markDirty(null);
            fire(null, copies.size() + " duplicated");
        }
    }

    private void cycleSelection(int dir) {
        List<UiNode> all = new ArrayList<>();
        for (TreeItem item : flattenList(doc.root)) all.add(item.node());
        if (all.isEmpty()) return;
        int index = selection.isEmpty() ? -1 : all.indexOf(selection.get(0));
        index = (index + dir + all.size()) % all.size();
        selection.clear();
        selection.add(all.get(index));
    }

    private void toggleChecked() {
        boolean changed = false;
        for (UiNode node : selection) {
            if (node.type == UiType.CHECKBOX) {
                node.checked = !node.checked;
                changed = true;
            }
        }
        if (changed) markDirty(null);
    }

    private void adjustSlider(double delta) {
        boolean changed = false;
        for (UiNode node : selection) {
            if (node.type != UiType.SLIDER) continue;
            double range = node.max > node.min ? node.max - node.min : 1;
            double step = shiftDown ? range / 10.0 : 1;
            node.value = clamp(node.value + delta * step, node.min, node.max);
            changed = true;
        }
        if (changed) markDirty("slider:" + (selection.isEmpty() ? "" : selection.get(0).id));
    }

    public void align(AlignTools.Align align) {
        if (selection.size() < 2) {
            fire(null, "select at least two widgets to align");
            return;
        }
        if (AlignTools.apply(selection, align)) {
            markDirty(null);
            fire(null, align.label.toLowerCase());
        }
    }

    /** Adds a widget of the given type to the selected container (or its parent). */
    public void addWidget(UiType type) {
        addWidget(type, null);
    }

    /** Adds a widget, optionally with a container style preset. */
    public void addWidget(UiType type, com.hackli.guidesigner.model.ContainerStyle style) {
        UiNode parent = targetParent();
        UiNode node = UiNode.create(type, UiNode.uniqueId(doc.root, type));

        if (type == UiType.CONTAINER && style != null) {
            node.style = style;
        }
        switch (type) {
            case LABEL -> node.text = "Label";
            case BUTTON -> node.text = "Button";
            case CONTAINER -> {
                node.text = style == com.hackli.guidesigner.model.ContainerStyle.SECTION ? "Section"
                    : style == com.hackli.guidesigner.model.ContainerStyle.VIEW ? "View"
                    : style == com.hackli.guidesigner.model.ContainerStyle.TABLE ? "" : "Panel";
                node.width = 260;
                node.height = style == com.hackli.guidesigner.model.ContainerStyle.TABLE ? 90 : 160;
                if (style == com.hackli.guidesigner.model.ContainerStyle.TABLE) {
                    // A ready-made settings row: [label][widget][reset].
                    UiNode valueCell = UiNode.checkBox("row-value").size(24, 24);
                    valueCell.cell().align(AnchorX.RIGHT, AnchorY.MIDDLE).endRow();

                    node.child(UiNode.label("row-label").text("Name").size(90, 20));
                    node.child(valueCell);
                    node.child(UiNode.texture("row-reset").textureRef("reset").size(24, 24));
                }
            }
            case TEXTBOX -> node.placeholder = "Type here...";
            case SLIDER -> {
                node.min = 0;
                node.max = 100;
                node.value = 50;
            }
            case NUMBER -> {
                node.min = 0;
                node.max = 100;
                node.value = 50;
            }
            case KEYBIND -> {
                // A fresh keybind starts unbound, like Keybind.none().
                node.key = com.hackli.guidesigner.model.KeyNames.NONE;
                node.keyIsKey = true;
                node.modifiers = 0;
            }
            case DROPDOWN -> {
                node.options("Option A", "Option B", "Option C");
                node.selected = "Option A";
            }
            default -> { }
        }

        if (parent.layout == LayoutMode.ABSOLUTE) {
            double w = node.width > 0 ? node.width : type.defaultWidth;
            double h = node.height > 0 ? node.height : type.defaultHeight;
            node.anchorX = AnchorX.LEFT;
            node.anchorY = AnchorY.TOP;
            node.x = Math.max(UiLayout.PAD, (parent.renderW - w) / 2);
            node.y = Math.max(UiLayout.PAD, (parent.renderH - h) / 2);
        }

        parent.child(node);
        selection.clear();
        selection.add(node);
        markDirty(null);
        fire(node, "added " + type.name().toLowerCase());
    }

    /**
     * Drops in a Meteor settings row: a table with
     * {@code [label][Select (N selected)][item icon][reset]}, which is the shape
     * the plugin screenshots use for a list setting.
     */
    public void addSettingsRow() {
        UiNode parent = targetParent();
        UiNode table = UiNode.container(UiNode.uniqueId(doc.root, UiType.CONTAINER), 320, 60)
            .style(com.hackli.guidesigner.model.ContainerStyle.TABLE);
        table.text = "";

        UiNode label = UiNode.label(UiNode.uniqueId(doc.root, UiType.LABEL)).text("Items").size(90, 20);

        UiNode select = UiNode.select(UiNode.uniqueId(doc.root, UiType.SELECT))
            .size(120, 24).selectedCount(4);
        select.cell().expandX(true);

        UiNode icon = UiNode.item(UiNode.uniqueId(doc.root, UiType.ITEM))
            .item("minecraft:diamond", 1).size(32, 32);

        UiNode reset = UiNode.texture(UiNode.uniqueId(doc.root, UiType.TEXTURE))
            .textureRef("reset").size(20, 20);
        reset.cell().align(AnchorX.RIGHT, AnchorY.MIDDLE).endRow();

        table.child(label).child(select).child(icon).child(reset);
        placeInto(parent, table, 320, 60);
        fire(table, "added a settings row");
    }

    /** Adds a widget of the given size to a parent, honouring absolute layout. */
    private void placeInto(UiNode parent, UiNode node, double w, double h) {
        node.width = w;
        node.height = h;
        if (parent.layout == LayoutMode.ABSOLUTE) {
            node.anchorX = AnchorX.LEFT;
            node.anchorY = AnchorY.TOP;
            node.x = Math.max(UiLayout.PAD, (parent.renderW - w) / 2);
            node.y = Math.max(UiLayout.PAD, (parent.renderH - h) / 2);
        }
        parent.child(node);
        selection.clear();
        selection.add(node);
        markDirty(null);
    }

    private UiNode targetParent() {        if (!selection.isEmpty()) {
            UiNode sel = selection.get(0);
            if (sel.type.isContainer) return sel;
            if (sel.parent != null) return sel.parent;
        }
        return doc.root;
    }

    // ==================================================================
    // documents / templates / export
    // ==================================================================

    public void newDocument() {
        UiNode root = UiNode.container("root", 460, 320);
        root.text = "New Panel";
        doc.root = root;
        doc.name = "Untitled";
        selection.clear();
        undoer.reset(doc);
        dirty = false;
        renderer.setDocument(doc);
        fire(null, "new document");
    }

    public void openProject(String name) {
        UiDocument loaded = DocumentStore.loadProject(name);
        if (loaded == null) {
            fire(null, "could not load " + name);
            return;
        }
        doc.name = loaded.name;
        doc.root = loaded.root;
        selection.clear();
        textEdit = null;
        undoer.reset(doc);
        dirty = false;
        renderer.setDocument(doc);
        fire(null, "opened project " + name);
    }

    private void save() {
        try {
            Files.writeString(file, doc.toJson());
            dirty = false;
            fire(null, "saved " + file.getFileName());
        } catch (IOException e) {
            fire(null, "save failed: " + e.getMessage());
        }
    }

    public void saveAs(String name) {
        try {
            Path dir = file.getParent() == null ? Path.of(".") : file.getParent();
            Path target = dir.resolve(DocumentStore.sanitize(name) + ".json");
            Files.writeString(target, doc.toJson());
            file = target;
            doc.name = name;
            dirty = false;
            fire(null, "saved as " + target.getFileName());
        } catch (IOException e) {
            fire(null, "save failed: " + e.getMessage());
        }
    }

    public void saveTemplate(String name) {
        UiNode source = primary();
        if (source == null) return;
        TemplateStore.save(name, source);
        fire(null, "template saved: " + name);
    }

    public void insertTemplate(String name) {
        TemplateStore.Template template = TemplateStore.load(name);
        if (template == null || template.root == null) {
            fire(null, "could not load template " + name);
            return;
        }
        UiNode copy = template.root.copyWithFreshIds(doc.root);
        UiNode parent = targetParent();
        if (parent.layout == LayoutMode.ABSOLUTE) {
            copy.anchorX = AnchorX.LEFT;
            copy.anchorY = AnchorY.TOP;
            copy.x = Math.max(UiLayout.PAD, (parent.renderW - copy.width) / 2);
            copy.y = Math.max(UiLayout.PAD, (parent.renderH - copy.height) / 2);
        }
        parent.child(copy);
        selection.clear();
        selection.add(copy);
        markDirty(null);
        fire(null, "inserted template " + name);
    }

    public void export(int which) {
        try {
            String pkg = "com.example.gui";
            String base = DocumentStore.toJava(doc.name);
            Path out;
            switch (which) {
                case 0 -> out = DocumentStore.writeExport(base + "Screen.java", JavaExporter.generate(doc, pkg));
                case 1 -> out = DocumentStore.writeExport(base + "Meteor.java", MeteorExporter.generate(doc, pkg));
                default -> {
                    JsonExporter.writeSchemaIfMissing();
                    out = JsonExporter.export(doc);
                }
            }
            fire(null, "exported " + out.getFileName() + " -> " + DocumentStore.displayPath(out.getParent()));
        } catch (Exception e) {
            fire(null, "export failed: " + e.getMessage());
        }
    }

    private void undo(boolean redoInstead) {
        UiDocument restored = redoInstead ? undoer.redo() : undoer.undo();
        if (restored == null) return;
        replaceRoot(restored);
        fire(null, "undo");
    }

    private void redo() {
        UiDocument restored = undoer.redo();
        if (restored == null) return;
        replaceRoot(restored);
        fire(null, "redo");
    }

    private void replaceRoot(UiDocument restored) {
        List<String> ids = new ArrayList<>();
        for (UiNode node : selection) ids.add(node.id);
        doc.root = restored.root;
        selection.clear();
        for (String id : ids) {
            UiNode node = doc.find(id);
            if (node != null) selection.add(node);
        }
        textEdit = null;
        dirty = true;
        renderer.setDocument(doc);
    }

    private void toggleMode() {
        mode = mode == Mode.DESIGN ? Mode.PLAY : Mode.DESIGN;
        selection.clear();
        textEdit = null;
        ui.blur();
        renderer.setHovered(null);
        renderer.setPressed(null);
        renderer.setFocused(null);
        renderer.setOpenDropdown(null);
        fire(null, mode == Mode.PLAY ? "play mode: widgets are live" : "design mode");
    }

    // ==================================================================
    // play mode
    // ==================================================================

    private void playClick(double mx, double my) {
        UiNode open = renderer.openDropdown();
        if (open != null) {
            double rowH = Math.max(ui.pad() * 2 + ui.textHeight(), ui.textHeight() + 4);
            double ly = open.renderY + open.renderH;
            for (int i = 0; i < open.options.size(); i++) {
                double ry = ly + i * rowH;
                if (mx >= open.renderX && mx <= open.renderX + open.renderW && my >= ry && my <= ry + rowH) {
                    open.selected = open.options.get(i);
                    renderer.setOpenDropdown(null);
                    markDirty(null);
                    fire(open, "dropdown " + open.id + " = " + open.selected);
                    return;
                }
            }
            renderer.setOpenDropdown(null);
        }

        UiNode hit = hit(mx, my);
        if (hit == null) {
            renderer.setFocused(null);
            return;
        }

        // Sections collapse / expand in play mode too.
        if (com.hackli.guidesigner.render.MeteorLayout.isSectionHeader(hit, mx, my, theme)) {
            hit.collapsed = !hit.collapsed;
            markDirty(null);
            fire(hit, "section " + hit.id + (hit.collapsed ? " collapsed" : " expanded"));
            return;
        }

        renderer.setPressed(hit);
        switch (hit.type) {
            case SLIDER -> {
                drag = Drag.SLIDER;
                dragNode = hit;
                setSliderValue(hit, (mx - hit.renderX) / Math.max(1, hit.renderW));
                fire(hit, "slider " + hit.id + " = " + fmt(hit.value));
            }
            case NUMBER -> {
                // WIntEdit / WDoubleEdit: slider drags, -/+ steps, the box types.
                MeteorPainter.NumberParts parts = numberParts(hit);
                if (parts.inSlider(mx, my)) {
                    drag = Drag.SLIDER;
                    dragNode = hit;
                    dragNumber = true;
                    hit.value = MeteorPainter.numberValueAt(hit, parts.fraction(mx, theme.handleSize()));
                    markDirty("number:" + hit.id);
                    fire(hit, "number " + hit.id + " = " + MeteorPainter.formatNumber(hit));
                } else if (parts.inMinus(mx, my) || parts.inPlus(mx, my)) {
                    hit.editText = null;
                    hit.value = MeteorPainter.stepNumber(hit, parts.inPlus(mx, my) ? 1 : -1);
                    markDirty("number:" + hit.id);
                    fire(hit, "number " + hit.id + " = " + MeteorPainter.formatNumber(hit));
                } else if (parts.inBox(mx, my)) {
                    textEdit = hit;
                    textCursor = MeteorPainter.displayValue(hit).length();
                    hit.editText = "";
                    renderer.setFocused(hit);
                    fire(hit, "focus " + hit.id);
                }
            }
            case DROPDOWN -> {
                if (hit.options.isEmpty()) fire(hit, "dropdown " + hit.id + " has no options");
                else renderer.setOpenDropdown(hit == renderer.openDropdown() ? null : hit);
            }
            case KEYBIND -> {
                // WKeybind: click arms it, the next key press binds, right-click clears.
                hit.listening = true;
                listeningKeybind = hit;
                fire(hit, "press a key to bind " + hit.id + " (Esc cancels)");
            }
            case CHECKBOX -> {
                hit.checked = !hit.checked;
                markDirty(null);
                fire(hit, "checkbox " + hit.id + " = " + hit.checked);
            }
            case TEXTBOX -> {
                renderer.setFocused(hit);
                fire(hit, "focus " + hit.id);
            }
            default -> { }
        }
    }

    private void playRelease(UiNode hit) {
        if (hit.type == UiType.BUTTON) {
            markDirty(null);
            fire(hit, "button " + hit.id + " clicked"
                + (hit.handler.isEmpty() ? "" : " -> " + hit.handler + "()"));
        }
    }

    private void setSliderValue(UiNode node, double t) {
        t = clamp(t, 0, 1);
        double range = node.max > node.min ? node.max - node.min : 1;
        double raw = node.min + t * range;
        node.value = range <= 10 ? Math.round(raw * 10.0) / 10.0 : Math.round(raw);
        dirty = true;
    }

    /**
     * Hit rects of a NUMBER widget's parts, measured with the same font the
     * design is drawn with ({@link DesignRenderer#canvas()} measures without a
     * live GL context).
     */
    private MeteorPainter.NumberParts numberParts(UiNode node) {
        return renderer.numberParts(node);
    }

    /** Geometry of a NUMBER widget's parts (used by the self test). */
    public MeteorPainter.NumberParts numberPartsOf(UiNode node) {
        return numberParts(node);
    }

    /** GLFW modifier bits for the currently held modifiers. */
    private static int modifiersOf(boolean ctrl, boolean shift, boolean alt) {
        int mods = 0;
        if (shift) mods |= com.hackli.guidesigner.model.KeyNames.MOD_SHIFT;
        if (ctrl) mods |= com.hackli.guidesigner.model.KeyNames.MOD_CONTROL;
        if (alt) mods |= com.hackli.guidesigner.model.KeyNames.MOD_ALT;
        return mods;
    }

    /** Leaves the "waiting for a key" state (Meteor's WKeybind.reset). */
    private void stopListeningKeybind() {
        if (listeningKeybind == null) return;
        listeningKeybind.listening = false;
        listeningKeybind = null;
    }

    /** Applies the number box's live buffer, clamped to the node's range. */    private void commitNumber(UiNode node) {
        String buffer = node.editText;
        node.editText = null;
        if (textEdit == node) textEdit = null;
        if (renderer.focused() == node) renderer.setFocused(null);
        if (buffer == null || buffer.isEmpty() || buffer.equals("-")) return;

        double parsed;
        try {
            parsed = Double.parseDouble(buffer);
        } catch (NumberFormatException e) {
            return;
        }
        double min = Math.min(node.min, node.max);
        double max = Math.max(node.min, node.max);
        node.value = clamp(node.integer ? Math.rint(parsed) : parsed, min, max);
        markDirty("number:" + node.id);
        fire(node, "number " + node.id + " = " + MeteorPainter.formatNumber(node));
    }

    private void fire(UiNode node, String message) {
        String line = node == null ? message : "[" + node.type.name().toLowerCase() + " " + node.id + "] " + message;
        log.add(line);
        if (log.size() > 12) log.remove(0);
        System.out.println("[simulator] " + line);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private UiNode hit(double wx, double wy) {
        return UiLayout.hitTest(doc.root, wx, wy);
    }

    private int handleAt() {
        UiNode node = selection.get(0);
        double[][] points = handlePoints(node);
        for (int i = 0; i < points.length; i++) {
            // points are world space; handles are drawn at a fixed screen size
            double px = toScreenX(points[i][0]) + HANDLE_SIZE / 2;
            double py = toScreenY(points[i][1]) + HANDLE_SIZE / 2;
            if (Math.abs(mouseX - px) <= HANDLE_SIZE && Math.abs(mouseY - py) <= HANDLE_SIZE) return i;
        }
        return -1;
    }

    private double[][] handlePoints(UiNode node) {
        double x = node.renderX, y = node.renderY, w = node.renderW, h = node.renderH;
        double o = HANDLE_SIZE / 2;
        return new double[][]{
            {x - o, y - o},
            {x + w / 2 - o, y - o},
            {x + w - o, y - o},
            {x + w - o, y + h / 2 - o},
            {x + w - o, y + h - o},
            {x + w / 2 - o, y + h - o},
            {x - o, y + h - o},
            {x - o, y + h / 2 - o}
        };
    }

    private void flatten(UiNode node, int depth, List<TreeItem> out) {
        out.add(new TreeItem(node, depth));
        for (UiNode child : node.children) flatten(child, depth + 1, out);
    }

    private List<TreeItem> flattenList(UiNode node) {
        List<TreeItem> out = new ArrayList<>();
        flatten(node, 0, out);
        return out;
    }

    private static boolean isDescendant(UiNode candidate, UiNode ancestor) {
        for (UiNode n = candidate.parent; n != null; n = n.parent) {
            if (n == ancestor) return true;
        }
        return false;
    }

    private static String[] names(Object[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = ((Enum<?>) values[i]).name();
        return out;
    }

    private static boolean accepts(UiNode node, String c) {
        if (node.inputFilter == null || node.inputFilter == InputFilter.NONE) return true;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            switch (node.inputFilter) {
                case INT -> {
                    if (!Character.isDigit(ch) && ch != '-') return false;
                }
                case DECIMAL -> {
                    if (!Character.isDigit(ch) && ch != '-' && ch != '.') return false;
                }
                default -> { }
            }
        }
        return true;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format("%.2f", v);
    }

    private static double parseD(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    // ==================================================================
    // GLFW key codes
    // ==================================================================

    public static final class Keys {
        public static final int SPACE = 32, LEFT_BRACKET = 91, RIGHT_BRACKET = 93,
            ESCAPE = 256, ENTER = 257, TAB = 258, BACKSPACE = 259,
            DELETE = 261, RIGHT = 262, LEFT = 263, DOWN = 264, UP = 265,
            LEFT_SHIFT = 340, LEFT_CONTROL = 341, LEFT_ALT = 342,
            RIGHT_SHIFT = 344, RIGHT_CONTROL = 345, RIGHT_ALT = 346,
            F5 = 294, A = 65, B = 66, C = 67, D = 68, E = 69, F = 70, G = 71, H = 72,
            I = 73, J = 74, K = 75, L = 76, M = 77, N = 78, O = 79, P = 80, Q = 81,
            R = 82, S = 83, T = 84, U = 85, V = 86, W = 87, X = 88, Y = 89, Z = 90;
        private Keys() {}
    }
}
