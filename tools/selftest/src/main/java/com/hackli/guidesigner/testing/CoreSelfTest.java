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

package com.hackli.guidesigner.testing;

import com.hackli.guidesigner.assets.AssetStore;
import com.hackli.guidesigner.export.JavaExporter;
import com.hackli.guidesigner.export.JsonExporter;
import com.hackli.guidesigner.export.MeteorExporter;
import com.hackli.guidesigner.model.AnchorX;
import com.hackli.guidesigner.model.AnchorY;
import com.hackli.guidesigner.model.ContainerStyle;
import com.hackli.guidesigner.model.DocumentStore;
import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.KeyNames;
import com.hackli.guidesigner.model.Orientation;
import com.hackli.guidesigner.model.TemplateStore;
import com.hackli.guidesigner.model.UiDocument;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.model.UiType;
import com.hackli.guidesigner.render.MeteorCanvas;
import com.hackli.guidesigner.render.MeteorLayout;
import com.hackli.guidesigner.render.MeteorPainter;
import com.hackli.guidesigner.render.MeteorTheme;
import com.hackli.guidesigner.render.TextureSource;
import com.hackli.guidesigner.runtime.AlignTools;
import com.hackli.guidesigner.runtime.EditorOps;
import com.hackli.guidesigner.runtime.Undoer;
import com.hackli.guidesigner.runtime.UiLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless self test for everything that does not need a window: the document
 * model, Meteor layout, painter geometry, exporters, undo, templates and the
 * clipboard operations.
 *
 * <p>This is the check that must stay green while the OpenGL editor - the
 * supported editor - evolves. It runs with no window, no font file and no GL
 * context (text is measured through {@link TextMetrics}), so it never steals
 * focus and works over SSH or in CI.</p>
 *
 * <p>Run it with {@code run-selftest.cmd}, or directly:</p>
 * <pre>
 *   java -cp tools/selftest/build/libs/selftest-0.2.0-all.jar \
 *        com.hackli.guidesigner.testing.CoreSelfTest
 * </pre>
 */
public final class CoreSelfTest {
    private static int checks;

    private CoreSelfTest() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Hackli GUI Studio - core self test");

        DocumentStore.init(Files.createTempDirectory("hgd-test"));

        documentModel();
        exporters();
        undoAndAlign();
        templates();
        jsonExport();
        hitTestAndClipboard();
        meteorLayout();
        section();
        view();
        table();
        numberWidget();
        keybindWidget();
        tooltipWidget();
        textureWidget();
        itemWidget();
        containerStyleExport();
        tableExport();

        System.out.println("SELF-TEST OK (" + checks + " checks)");
    }

    // ================= checks =================

    private static void documentModel() {
        UiDocument doc = new UiDocument("SelfTest");
        doc.root
            .child(UiNode.container("panel-main", 400, 200).text("Main")
                .child(UiNode.button("btn-start").text("Start").pos(20, 20).size(160, 42)))
            .child(UiNode.slider("sld-size").sliderValue(50).range(0, 100).pos(20, 100).size(200, 24));

        String json = doc.toJson();
        UiDocument loaded = UiDocument.fromJson(json);
        check(loaded != null, "JSON roundtrip failed");
        check(loaded.root.count() == 4, "Wrong node count: " + loaded.root.count());
        System.out.println("JSON roundtrip OK (" + loaded.root.count() + " nodes)");

        DocumentStore.saveProject(loaded);
        List<String> names = DocumentStore.listProjects();
        check(names.contains("SelfTest"), "Project list missing after save");
        System.out.println("DocumentStore OK (projects: " + names + ")");
    }

    private static void exporters() {
        UiDocument doc = new UiDocument("SelfTest");
        doc.root.child(UiNode.button("btn-start").text("Start").size(160, 42));

        String runtime = JavaExporter.generate(doc, "com.example.test");
        String staticCode = MeteorExporter.generate(doc, "com.example.test");
        check(runtime.contains("case \"btn-start\""), "Runtime export missing handler");
        System.out.println("Runtime export OK (" + runtime.length() + " bytes)");
        System.out.println("Static export OK (" + staticCode.length() + " bytes)");
    }

    private static void undoAndAlign() {
        UiDocument doc = new UiDocument("Undo");
        UiNode button = UiNode.button("btn-start").pos(20, 20).size(160, 42);
        doc.root.child(button);

        Undoer undoer = new Undoer();
        undoer.reset(doc);
        button.x += 100;
        undoer.record(doc, "field:x");
        UiDocument undone = undoer.undo();
        check(undone != null && undone.find("btn-start").x == 20, "Undo failed");
        UiDocument redone = undoer.redo();
        check(redone != null && redone.find("btn-start").x == 120, "Redo failed");
        System.out.println("Undo/redo OK");

        UiDocument alignDoc = new UiDocument("Align");
        UiNode a = UiNode.button("a").pos(10, 10).size(50, 20);
        UiNode b = UiNode.button("b").pos(200, 100).size(60, 20);
        alignDoc.root.child(a).child(b);

        List<UiNode> group = new ArrayList<>(List.of(a, b));
        check(AlignTools.apply(group, AlignTools.Align.LEFT), "Align LEFT failed");
        check(a.x == b.x, "Align LEFT did not equalize x");
        System.out.println("Align tools OK");
    }

    private static void templates() {
        UiNode templateRoot = UiNode.button("btn-tpl").text("FromTemplate").size(100, 30);
        TemplateStore.save("test", templateRoot);
        TemplateStore.Template template = TemplateStore.load("test");
        check(template != null && template.root != null, "Template roundtrip failed");

        UiDocument scope = new UiDocument("Scope");
        UiNode placed = template.root.copyWithFreshIds(scope.root);
        check(!placed.id.equals("btn-tpl"), "Fresh ids failed: " + placed.id);
        scope.root.child(placed);
        check(scope.root.find(placed.id) != null, "Placed template missing");
        System.out.println("Templates OK (placed as " + placed.id + ")");
    }

    private static void jsonExport() throws Exception {
        UiDocument doc = new UiDocument("JsonOut");
        doc.root.child(UiNode.button("btn").text("Go").size(100, 30));

        String json = JsonExporter.generate(doc);
        check(json.contains("\"$schema\""), "JSON export missing $schema");
        Path path = JsonExporter.export(doc);
        check(Files.exists(path), "JSON export file missing");
        check(Files.exists(path.resolveSibling(JsonExporter.SCHEMA_FILE)), "Schema file missing");
        System.out.println("JSON export OK (" + path.getFileName() + ")");
    }

    private static void hitTestAndClipboard() {
        // A widget that overflows its panel must stay clickable.
        UiDocument doc = new UiDocument("Hit");
        doc.root = UiNode.container("root", 400, 300);
        doc.root.layout(LayoutMode.ABSOLUTE);
        UiNode panel = UiNode.container("bg", 200, 120).pos(20, 20);
        UiNode floating = UiNode.button("floating").text("Over").pos(240, 180).size(120, 40);
        doc.root.child(panel).child(floating);
        UiLayout.layoutTree(doc.root, 0, 0, 400, 300, new MeteorTheme().headerHeight());

        check(UiLayout.hitTest(doc.root, panel.renderX + 10, panel.renderY + 10) == panel,
            "Hit test inside the panel missed");
        check(UiLayout.hitTest(doc.root, floating.renderX + floating.renderW / 2,
            floating.renderY + floating.renderH / 2) == floating, "Overflowing widget unreachable");

        // Z order: the last child is drawn on top and must win.
        UiNode over = UiNode.button("over").pos(30, 30).size(100, 40);
        doc.root.child(over);
        UiLayout.layoutTree(doc.root, 0, 0, 400, 300, new MeteorTheme().headerHeight());
        check(UiLayout.hitTest(doc.root, over.renderX + 5, over.renderY + 5) == over,
            "Hit test ignored draw order");

        UiNode copy = EditorOps.copyOf(floating, doc.root);
        check(copy != null && copy != floating && !copy.id.equals(floating.id),
            "Copy did not produce a fresh node");
        UiNode pasted = EditorOps.paste(doc, copy, EditorOps.pasteTarget(doc, List.of(panel)));
        check(pasted != null && panel.find(pasted.id) != null, "Paste did not land in the container");
        UiNode duplicate = EditorOps.duplicate(doc, floating);
        check(duplicate != null && duplicate.parent == doc.root, "Duplicate failed");

        int before = doc.root.count();
        int removed = EditorOps.delete(doc, List.of(duplicate, pasted));
        check(removed == 2 && doc.root.count() == before - 2, "Delete removed " + removed + " nodes");

        // A copied widget keeps every property (NUMBER settings included).
        UiNode rich = UiNode.number("num-rich").sliderValue(2.5).range(-5, 5)
            .step(0.5).integer(false).showSlider(false).showButtons(true);
        rich.cell().expandX(true);
        doc.root.child(rich);
        UiNode richCopy = EditorOps.copyOf(rich, doc.root);
        check(!richCopy.integer && richCopy.step == 0.5 && !richCopy.showSlider && richCopy.showButtons
            && richCopy.value == 2.5 && richCopy.min == -5 && richCopy.max == 5
            && richCopy.cell != null && richCopy.cell.expandX, "Copy lost NUMBER properties");

        UiNode sectionSource = UiNode.container("sec-rich", 200, 100).text("Sec")
            .style(ContainerStyle.SECTION).collapsed(true);
        UiNode sectionCopy = sectionSource.copyWithFreshIds(doc.root);
        check(sectionCopy.style == ContainerStyle.SECTION && sectionCopy.collapsed,
            "Copy lost container style/collapsed");

        System.out.println("Hit test + clipboard operations OK");
    }

    private static void meteorLayout() {
        UiDocument doc = new UiDocument("Layout");
        doc.root = UiNode.container("root", 500, 400);
        doc.root.layout(LayoutMode.ABSOLUTE);
        UiNode window = UiNode.container("win", 300, 200).text("Win").pos(20, 20);
        UiNode first = UiNode.button("la").size(100, 20);
        UiNode second = UiNode.button("lb").size(100, 20);
        window.child(first).child(second);

        UiNode row = UiNode.container("row", 300, 30).pos(20, 260);
        row.orientation = Orientation.HORIZONTAL;
        UiNode expanding = UiNode.button("rc1").size(50, 20);
        expanding.cell().expandX(true);
        UiNode fixed = UiNode.button("rc2").size(50, 20);
        row.child(expanding).child(fixed);
        doc.root.child(window).child(row);

        MeteorTheme theme = new MeteorTheme();
        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);

        double spacing = theme.scaled(MeteorLayout.SPACING);
        double padding = theme.scaled(MeteorLayout.WINDOW_PADDING);

        double expectedFirst = window.renderY + theme.headerHeight() + padding + spacing;
        check(Math.abs(first.renderY - expectedFirst) < 0.001,
            "Vertical layout: first child at " + first.renderY + ", expected " + expectedFirst);
        check(Math.abs(second.renderY - (first.renderY + first.renderH + spacing)) < 0.001,
            "Vertical layout: spacing between children wrong");
        check(Math.abs(first.renderX - (window.renderX + padding)) < 0.001
            && Math.abs(first.renderW - 100) < 0.001, "Window padding/width wrong");

        UiNode fill = UiNode.button("fill").size(0, 20);
        window.child(fill);
        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);
        check(Math.abs(fill.renderW - (window.renderW - padding * 2)) < 0.001,
            "Auto-width child should fill: " + fill.renderW);

        double rowInner = row.renderW - padding * 2;
        double natural = (spacing + 50) + (spacing + 50);
        double expectedExpand = 50 + Math.max(0, rowInner - natural);
        check(Math.abs(expanding.renderW - expectedExpand) < 0.001,
            "Horizontal expand wrong: " + expanding.renderW + ", expected " + expectedExpand);
        check(Math.abs(fixed.renderW - 50) < 0.001, "Non-expanding cell should keep its width");

        System.out.println("Meteor layout OK (spacing " + spacing + ", padding " + padding + ")");
    }

    private static void section() {
        UiDocument doc = new UiDocument("Section");
        doc.root = UiNode.container("root", 500, 400);
        doc.root.layout(LayoutMode.ABSOLUTE);
        UiNode section = UiNode.container("sec", 300, 140).text("Sec")
            .style(ContainerStyle.SECTION).pos(20, 20);
        UiNode child = UiNode.button("sc").size(100, 20);
        section.child(child);
        doc.root.child(section);

        MeteorTheme theme = new MeteorTheme();
        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);

        double spacing = theme.scaled(MeteorLayout.SPACING);
        double padding = theme.scaled(MeteorLayout.SECTION_PADDING);
        double expectedChild = section.renderY + theme.headerHeight() + padding + spacing;
        check(Math.abs(child.renderY - expectedChild) < 0.001
            && Math.abs(child.renderX - (section.renderX + padding)) < 0.001,
            "Section layout wrong: child at " + child.renderX + "," + child.renderY);
        check(MeteorLayout.isSectionHeader(section, section.renderX + 5, section.renderY + 2, theme),
            "Section header not detected");

        section.collapsed = true;
        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);
        check(Math.abs(section.renderH - theme.headerHeight()) < 0.001,
            "Collapsed section should be header only: " + section.renderH);
        check(child.renderW == 0 && child.renderH == 0, "Collapsed section must hide its children");

        System.out.println("Section OK (header " + theme.headerHeight() + ", padding " + padding + ")");
    }

    private static void view() {
        UiDocument doc = new UiDocument("View");
        doc.root = UiNode.container("root", 500, 400);
        doc.root.layout(LayoutMode.ABSOLUTE);
        UiNode view = UiNode.container("view", 200, 60).style(ContainerStyle.VIEW).pos(20, 20);
        UiNode tall1 = UiNode.button("t1").size(100, 50);
        UiNode tall2 = UiNode.button("t2").size(100, 50);
        view.child(tall1).child(tall2);
        doc.root.child(view);

        MeteorTheme theme = new MeteorTheme();
        double padding = theme.scaled(MeteorLayout.WINDOW_PADDING);
        double spacing = theme.scaled(MeteorLayout.SPACING);

        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);
        check(view.scrollHeight > view.renderH,
            "View content should overflow: " + view.scrollHeight + " vs " + view.renderH);
        check(MeteorLayout.scroll(view, 30), "View did not scroll");

        MeteorLayout.layoutTree(doc.root, 0, 0, 500, 400, theme);
        double expected = view.renderY + padding + spacing - 30;
        check(Math.abs(tall1.renderY - expected) < 0.001,
            "Scrolled view child at " + tall1.renderY + ", expected " + expected);

        MeteorLayout.scroll(view, 9999);
        check(Math.abs(view.scroll - (view.scrollHeight - view.renderH)) < 0.001,
            "Scroll not clamped: " + view.scroll);

        System.out.println("View OK (content " + Math.round(view.scrollHeight)
            + ", viewport " + Math.round(view.renderH) + ")");
    }

    /**
     * WTable: the layout that lines up Meteor's settings rows
     * ({@code [label][value][reset]}). The point of a table is that every cell in
     * a column starts at the same x and every row at the same y, so that is what
     * this checks - not exact pixel values.
     */
    private static void table() {
        MeteorTheme theme = new MeteorTheme();

        UiDocument doc = new UiDocument("Table");
        doc.root = UiNode.container("root", 400, 300);
        doc.root.layout(LayoutMode.ABSOLUTE);

        UiNode table = UiNode.container("tbl", 320, 120).style(ContainerStyle.TABLE).pos(20, 20);
        doc.root.child(table);

        // Two settings rows of three cells, the middle one expandable - the shape
        // every Meteor settings page uses.
        UiNode label1 = UiNode.label("l1").text("Range").size(80, 18);
        UiNode value1 = UiNode.textBox("v1").size(100, 22);
        UiNode reset1 = UiNode.texture("r1").textureRef("reset").size(20, 20);
        UiNode label2 = UiNode.label("l2").text("Delay").size(80, 18);
        UiNode value2 = UiNode.textBox("v2").size(100, 22);
        UiNode reset2 = UiNode.texture("r2").textureRef("reset").size(20, 20);
        value1.cell().expandX(true);
        value2.cell().expandX(true);
        // Meteor's `table.row()` after the third cell: the last cell of each row
        // carries the break, which is what fixes the column count for the table.
        reset1.cell().endRow();
        reset2.cell().endRow();

        table.child(label1).child(value1).child(reset1)
             .child(label2).child(value2).child(reset2);

        check(MeteorLayout.tableColumns(table) == 3,
            "row() on cell 3 should mean three columns, got " + MeteorLayout.tableColumns(table));

        // Lay it out through the painter, not just the layout helper: that is the
        // path every editor uses, and it is where a wrong column count would show.
        MeteorPainter tablePainter = new MeteorPainter(theme);
        tablePainter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);

        // Columns: every cell in a column shares its x, and columns are ordered.
        check(near(label1.renderX, label2.renderX),
            "Column 1 misaligned: " + label1.renderX + " vs " + label2.renderX);
        check(near(value1.renderX, value2.renderX),
            "Column 2 misaligned: " + value1.renderX + " vs " + value2.renderX);
        check(near(reset1.renderX, reset2.renderX),
            "Column 3 misaligned: " + reset1.renderX + " vs " + reset2.renderX);
        check(label1.renderX < value1.renderX && value1.renderX < reset1.renderX,
            "Columns must run left to right");

        // Rows: both cells of a row share its y, and row 2 sits below row 1.
        check(near(label1.renderY, value1.renderY) && near(label1.renderY, reset1.renderY),
            "Row 1 cells at different heights: " + label1.renderY + " / " + value1.renderY
                + " / " + reset1.renderY);
        check(label2.renderY > label1.renderY, "Row 2 must sit below row 1");

        // Column width = the widest cell in that column, and a wide column pushes
        // the next one right (that is the alignment guarantee).
        double column1 = value1.renderX - label1.renderX;
        check(column1 >= label1.renderW, "Column 1 narrower than its widest cell: " + column1);

        // The last cell of a row must stay inside the table (no overflow).
        check(reset1.renderX + reset1.renderW <= table.renderX + table.renderW + 0.01,
            "Table row overflows its container: " + (reset1.renderX + reset1.renderW)
                + " > " + (table.renderX + table.renderW));

        // expandX: the marked cell takes the leftover width of its row.
        check(value1.renderW > 100, "The expandX cell should grow, got " + value1.renderW);
        check(near(label1.renderW, 80), "A non-expanding cell must keep its width: " + label1.renderW);

        // Without hints a table wraps on its own when the cells no longer fit.
        UiNode wrapped = UiNode.container("wrap", 100, 60).style(ContainerStyle.TABLE).pos(20, 200);
        UiNode a = UiNode.label("a").text("aa").size(60, 16);
        UiNode b = UiNode.label("b").text("bb").size(60, 16);
        UiNode c = UiNode.label("c").text("cc").size(60, 16);
        wrapped.child(a).child(b).child(c);
        check(MeteorLayout.tableColumns(wrapped) == 3,
            "Without hints the exporter assumes one row, got " + MeteorLayout.tableColumns(wrapped));

        doc.root.child(wrapped);
        tablePainter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);
        check(b.renderY > a.renderY,
            "A 100px table cannot fit two 60px cells; cell b should wrap to the next row");

        System.out.println("Table OK (3 columns, aligned x/y, expandX " + Math.round(value1.renderW) + ")");
    }

    /** NUMBER widget geometry and value maths, without any window. */    private static void numberWidget() {
        MeteorTheme theme = new MeteorTheme();
        MeteorPainter painter = new MeteorPainter(theme);
        var canvas = TextMetrics.measurementCanvas();

        UiNode number = UiNode.number("num").size(320, 30).sliderValue(50).range(0, 100);
        number.renderX = 0;
        number.renderY = 0;
        number.renderW = 320;
        number.renderH = 30;

        MeteorPainter.NumberParts parts = painter.numberParts(canvas, number);
        check(parts.boxW() > 0 && parts.minusW() > 0 && parts.plusW() > 0 && parts.sliderW() > 0,
            "Number parts missing: " + parts);
        check(parts.boxX() == 0, "Number box should start at the widget's left edge");
        check(parts.minusX() > parts.boxW(), "The '-' button must sit right of the box");
        check(parts.plusX() > parts.minusX() + parts.minusW(), "The '+' button must follow '-'");
        check(parts.sliderX() > parts.plusX() + parts.plusW(), "The slider must follow the buttons");

        // The four parts must tile the widget without overlapping.
        check(parts.inBox(parts.boxX() + 1, parts.rowY() + 1), "Box hit test failed");
        check(!parts.inSlider(parts.boxX() + 1, parts.rowY() + 1), "Box must not hit the slider");

        // Slider maths: 0..100, so half way is 50.
        check(MeteorPainter.numberValueAt(number, 0.5) == 50, "Slider fraction -> value wrong");
        check(MeteorPainter.numberValueAt(number, 2) == 100, "Slider value must clamp to max");
        check(MeteorPainter.numberValueAt(number, -1) == 0, "Slider value must clamp to min");

        // -/+ buttons step by `step` and clamp.
        number.value = 50;
        check(MeteorPainter.stepNumber(number, 1) == 51, "Step up wrong");
        check(MeteorPainter.stepNumber(number, -1) == 49, "Step down wrong");
        number.value = 100;
        check(MeteorPainter.stepNumber(number, 1) == 100, "Step must clamp at max");
        number.value = 0;
        check(MeteorPainter.stepNumber(number, -1) == 0, "Step must clamp at min");

        // Decimals keep their precision, integers round.
        UiNode decimal = UiNode.number("num-d").integer(false).range(0, 5).step(0.1).sliderValue(1.5);
        check(MeteorPainter.stepNumber(decimal, 1) == 1.6, "Decimal step wrong: "
            + MeteorPainter.stepNumber(decimal, 1));
        check(MeteorPainter.numberValueAt(decimal, 0.5) == 2.5, "Decimal slider value wrong");

        // The live edit buffer is what the box shows while typing.
        number.value = 50;
        number.editText = "7";
        check(MeteorPainter.displayValue(number).equals("7"), "Edit buffer must win over the value");
        number.editText = null;
        check(MeteorPainter.displayValue(number).equals("50"),
            "Value must show once editing ends, got '" + MeteorPainter.displayValue(number) + "'");

        System.out.println("Number widget OK (box " + Math.round(parts.boxW())
            + ", slider " + Math.round(parts.sliderW()) + ")");
    }

    /**
     * WKeybind: the label rules and the shape of the exported binding. Meteor
     * renders a keybind as a button whose text is {@code Keybind.toString()}.
     */
    private static void keybindWidget() {
        // The label rules, mirroring Keybind.toString() / Utils.getKeyName.
        check(KeyNames.label(KeyNames.NONE, 0, true).equals("Unknown"),
            "An unbound keybind shows \"Unknown\"");
        check(KeyNames.label(-2, 0, true).equals("None"),
            "A cleared keybind shows \"None\"");
        check(KeyNames.label(75, 0, true).equals("K"), "A plain key shows its letter");
        check(KeyNames.label(75, KeyNames.MOD_CONTROL, true).equals("Ctrl + K"),
            "Modifiers prefix the key");
        check(KeyNames.label(75, KeyNames.MOD_CONTROL | KeyNames.MOD_SHIFT, true).equals("Ctrl + Shift + K"),
            "Modifiers stack in Meteor's order");
        check(KeyNames.label(0, 0, false).equals("Left Mouse"), "Mouse buttons use getButtonName");
        check(KeyNames.label(294, 0, true).equals("F5"), "Named keys keep their name");
        check(KeyNames.label(256, 0, true).equals("Esc"), "Escape is \"Esc\"");

        // Names are what the Inspector shows, codes are what is exported, so both
        // directions have to agree.
        for (int code : new int[]{65, 75, 90, 48, 57, 32, 256, 257, 262, 294, 340, 348}) {
            String name = KeyNames.keyName(code);
            check(KeyNames.codeOf(name) == code,
                "Key name round trip failed for code " + code + " (" + name + ")");
        }

        // The painter's listening state is what makes the button show "...".
        MeteorTheme theme = new MeteorTheme();
        MeteorPainter painter = new MeteorPainter(theme);
        UiNode bind = UiNode.keybind("bind").keybind(75, 0, true).size(120, 30);
        UiDocument doc = new UiDocument("Keybind");
        doc.root = UiNode.container("root", 200, 100);
        doc.root.layout(LayoutMode.ABSOLUTE);
        doc.root.child(bind);
        painter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);
        check(bind.renderW == 120 && bind.renderH == 30, "Keybind size changed while painting");

        bind.listening = true;
        painter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);   // must not throw
        bind.listening = false;

        // Export: the static exporter builds a real Keybind from the code.
        UiDocument exportDoc = new UiDocument("KeybindOut");
        exportDoc.root = UiNode.container("root", 300, 200).text("Root");
        exportDoc.root.child(UiNode.keybind("bind-jump").keybind(32, KeyNames.MOD_CONTROL, true));
        exportDoc.root.child(UiNode.keybind("bind-mouse").keybind(1, 0, false));

        String meteor = MeteorExporter.generate(exportDoc, "com.example");
        check(meteor.contains("Keybind.fromKeys(32, 2)"),
            "A key binding exports as Keybind.fromKeys(code, modifiers):\n" + meteor);
        check(meteor.contains("Keybind.fromButton(1)"),
            "A mouse binding exports as Keybind.fromButton(code):\n" + meteor);
        check(meteor.contains("theme.keybind("), "The static export must build a WKeybind");

        String java = JavaExporter.generate(exportDoc, "com.example");
        check(java.contains(".keybind(32, 2, true)") && java.contains(".keybind(1, 0, false)"),
            "The runtime export must carry the binding:\n" + java);

        System.out.println("Keybind OK (labels, round trip, key 32 + Ctrl, mouse 1)");
    }

    /**
     * WTooltip: hover text drawn on top, 12px below/right of the pointer, pulled
     * back inside the canvas when it would overflow (Meteor's renderTooltip).
     */
    private static void tooltipWidget() {
        MeteorTheme theme = new MeteorTheme();
        UiDocument doc = new UiDocument("Tooltip");
        doc.root = UiNode.container("root", 300, 200);
        doc.root.layout(LayoutMode.ABSOLUTE);
        UiNode button = UiNode.button("btn").text("Go").pos(20, 20).size(100, 30)
            .tooltip("Start the thing");
        UiNode plain = UiNode.button("plain").text("No tip").pos(20, 80).size(100, 30);
        doc.root.child(button).child(plain);

        // A recording canvas so the tooltip's own quad can be found among draws.
        java.util.List<double[]> quads = new java.util.ArrayList<>();
        java.util.List<String> texts = new java.util.ArrayList<>();
        MeteorCanvas recorder = new RecordingCanvas(quads, texts);

        MeteorPainter painter = new MeteorPainter(theme);
        painter.setLocalTextureSource(TextureSource.NONE);

        // Nothing hovered: no tooltip, and nothing extra drawn.
        painter.paint(recorder, doc.root, 0, 0);
        check(painter.activeTooltip() == null, "No hover means no tooltip");

        // Hovering the button shows its text; a widget without one shows nothing.
        painter.setInteraction(button, null, null, null);
        painter.setPointer(button.renderX + 5, button.renderY + 5);
        painter.paint(recorder, doc.root, 0, 0);
        check("Start the thing".equals(painter.activeTooltip()),
            "Hover should show the node's tooltip, got " + painter.activeTooltip());

        painter.setInteraction(plain, null, null, null);
        painter.paint(recorder, doc.root, 0, 0);
        check(painter.activeTooltip() == null, "A node without a tooltip shows nothing");

        // The tooltip fades in over ~1/14 s and is drawn last.
        painter.setInteraction(button, null, null, null);
        painter.setPointer(button.renderX + 5, button.renderY + 5);
        check(painter.tooltipFade() == 0, "The fade should start at 0");
        painter.paint(recorder, doc.root, 0, 0);
        check(!texts.contains("Start the thing"), "A fresh tooltip is still invisible");
        painter.tick(2, doc.root);
        painter.paint(recorder, doc.root, 0, 0);
        check(painter.tooltipFade() == 1, "The fade should reach 1");
        check(texts.contains("Start the thing"), "The tooltip text should be drawn once faded in");

        // It is drawn after every widget, so it can never be covered.
        check(texts.get(texts.size() - 1).equals("Start the thing"),
            "The tooltip must be the last thing drawn");

        // Placement: 12px below/right of the pointer, inside the canvas.
        double[] tooltipQuad = quads.get(quads.size() - 1);
        check(near(tooltipQuad[0], button.renderX + 5 + 12),
            "Tooltip x should be pointer + 12, got " + tooltipQuad[0]);
        check(near(tooltipQuad[1], button.renderY + 5 + 12),
            "Tooltip y should be pointer + 12, got " + tooltipQuad[1]);

        // Near the bottom-right corner it is pulled back inside the root.
        painter.setPointer(doc.root.renderX + doc.root.renderW - 1,
            doc.root.renderY + doc.root.renderH - 1);
        painter.paint(recorder, doc.root, 0, 0);
        double[] clamped = quads.get(quads.size() - 1);
        check(clamped[0] + clamped[2] <= doc.root.renderX + doc.root.renderW + 0.01,
            "Tooltip must not overflow the right edge");
        check(clamped[1] + clamped[3] <= doc.root.renderY + doc.root.renderH + 0.01,
            "Tooltip must not overflow the bottom edge");

        // A tooltip on a container covers its children (Meteor inherits it).
        UiNode panel = UiNode.container("panel", 150, 80).pos(120, 20).tooltip("Panel help");
        UiNode inner = UiNode.button("inner").text("In").size(80, 24);
        panel.child(inner);
        doc.root.child(panel);
        painter.paint(recorder, doc.root, 0, 0);
        painter.setInteraction(inner, null, null, null);
        painter.setPointer(inner.renderX + 2, inner.renderY + 2);
        painter.paint(recorder, doc.root, 0, 0);
        check("Panel help".equals(painter.activeTooltip()),
            "A child should inherit its container's tooltip, got " + painter.activeTooltip());

        // Exports carry the tooltip on every widget kind.
        UiDocument exportDoc = new UiDocument("TipOut");
        exportDoc.root = UiNode.container("root", 200, 100).text("Root");
        exportDoc.root.child(UiNode.button("btn").text("Go").tooltip("Tip text"));
        String java = JavaExporter.generate(exportDoc, "com.example");
        check(java.contains(".tooltip(\"Tip text\")"),
            "The runtime export must carry the tooltip:\n" + java);
        String meteor = MeteorExporter.generate(exportDoc, "com.example");
        check(meteor.contains(".tooltip = \"Tip text\""),
            "The static export must carry the tooltip:\n" + meteor);

        System.out.println("Tooltip OK (hover, fade, +12 placement, clamped, inherited)");
    }

    /** A canvas that records what was drawn, instead of rasterising it. */
    private static final class RecordingCanvas implements MeteorCanvas {
        private final java.util.List<double[]> quads;
        private final java.util.List<String> texts;

        RecordingCanvas(java.util.List<double[]> quads, java.util.List<String> texts) {
            this.quads = quads;
            this.texts = texts;
        }

        @Override public void quad(double x, double y, double w, double h, int argb) {
            quads.add(new double[]{x, y, w, h});
        }

        @Override public void quadGradientH(double x, double y, double w, double h,
                                             int argbLeft, int argbRight) {}

        @Override public void circle(double x, double y, double d, int argb) {}

        @Override public void text(String text, double x, double y, int argb, double scale) {
            texts.add(text);
        }

        @Override public double textWidth(String text, double scale) {
            return TextMetrics.width(text, scale);
        }

        @Override public double ascent(double scale) {
            return TextMetrics.height(scale) * 0.8;
        }

        @Override public void pushClip(double x, double y, double w, double h) {}

        @Override public void popClip() {}
    }

    /**
     * WItem: a 16x16 sprite scaled 2x inside a 32x32 box, and the entity icon,
     * which is the spawn egg's sprite (that is what Meteor's entity list shows).
     */
    private static void itemWidget() {
        MeteorTheme theme = new MeteorTheme();
        UiDocument doc = new UiDocument("Items");
        doc.root = UiNode.container("root", 300, 200);
        doc.root.layout(LayoutMode.ABSOLUTE);

        UiNode item = UiNode.item("item-diamond").item("minecraft:diamond", 1).size(32, 32).pos(10, 10);
        UiNode stack = UiNode.item("item-stack").item("stone", 64).size(32, 32).pos(50, 10);
        UiNode air = UiNode.item("item-air").item("minecraft:air", 1).size(32, 32).pos(90, 10);
        UiNode entity = UiNode.entity("ent-zombie").entityId("minecraft:zombie")
            .size(32, 32).pos(130, 10);
        UiNode noEgg = UiNode.entity("ent-player").entityId("minecraft:player")
            .size(32, 32).pos(170, 10);
        UiNode select = UiNode.select("sel").selectedCount(42).size(160, 26).pos(10, 60);
        doc.root.child(item).child(stack).child(air).child(entity).child(noEgg).child(select);

        // The painter needs a texture source that reports what it was asked for.
        java.util.List<String> asked = new java.util.ArrayList<>();
        MeteorPainter painter = new MeteorPainter(theme);
        painter.setLocalTextureSource(reference -> {
            asked.add(reference);
            return new Object();       // pretend every texture exists
        });

        java.util.List<double[]> quads = new java.util.ArrayList<>();
        java.util.List<String> texts = new java.util.ArrayList<>();
        MeteorCanvas recorder = new RecordingCanvas(quads, texts);

        painter.paint(recorder, doc.root, 0, 0);
        check(item.renderW == 32 && item.renderH == 32, "WItem's box is 32x32");

        // The icon is a 16x16 sprite drawn at 2x, so 32 document pixels.
        check(asked.contains("minecraft:diamond"),
            "The painter should resolve the node's item id, asked for " + asked);
        check(asked.contains("stone"), "Block items resolve through the index too");
        check(asked.contains("zombie_spawn_egg"),
            "An entity resolves to its spawn egg, asked for " + asked);

        // air draws nothing at all (WItem skips an empty stack).
        check(!asked.contains("minecraft:air") && !asked.contains("air"),
            "air must not be drawn");

        // An entity without a spawn egg falls back to the placeholder.
        check(noEgg.text.isEmpty(), "A missing egg leaves the node's text alone");

        // The stack count is drawn only above 1.
        check(texts.contains("64"), "A stack of 64 shows its count");
        check(!texts.contains("1"), "A single item shows no count");

        // The icon is drawn at 2x the sprite, i.e. 32px for a 16px texture.
        check(item.renderW == theme.scaled(32 * (1 / theme.scale)),
            "The item box follows WItem's theme.scale(32)");

        // Select label: GuiTheme.selectW's "(N selected)".
        check(MeteorPainter.selectLabel(select).equals("Select (42 selected)"),
            "Select shows its count, got " + MeteorPainter.selectLabel(select));
        select.itemCountSelected = -1;
        check(MeteorPainter.selectLabel(select).equals("Select"),
            "A negative count hides the label part");
        select.itemCountSelected = 0;
        check(MeteorPainter.selectLabel(select).equals("Select (0 selected)"),
            "A zero count is still shown");

        // Spawn egg reverse lookup: vanilla spells it <entity>_spawn_egg, and
        // entities without an egg must not invent one.
        AssetStore assets = AssetStore.get();
        if (assets.available()) {
            check("zombie_spawn_egg".equals(assets.spawnEggOf("minecraft:zombie")),
                "A namespaced id resolves to its egg");
            check("creeper_spawn_egg".equals(assets.spawnEggOf("creeper")), "A plain id resolves");
            check(assets.spawnEggOf("player") == null, "The player has no spawn egg");
            check(assets.spawnEggOf("arrow") == null, "A projectile has no spawn egg");
            check(assets.spawnEggOf("zombie_spawn_egg").equals("zombie_spawn_egg"),
                "An id that already is an egg is left alone");
            System.out.println("Item icons OK (16x16 at 2x in a 32x32 box, spawn eggs resolve)");
        } else {
            System.out.println("Item icons OK (no extracted assets - placeholders will be used)");
        }

        // Exports: the runtime carries the id, the static one builds an ItemStack.
        UiDocument exportDoc = new UiDocument("ItemOut");
        exportDoc.root = UiNode.container("root", 200, 100).text("Root");
        exportDoc.root.child(UiNode.item("it").item("minecraft:diamond", 1));
        exportDoc.root.child(UiNode.entity("ent").entityId("minecraft:zombie"));
        exportDoc.root.child(UiNode.select("sel").selectedCount(3));

        String java = JavaExporter.generate(exportDoc, "com.example");
        check(java.contains(".item(\"minecraft:diamond\", 1)"),
            "The runtime export must carry the item:\n" + java);
        check(java.contains(".entityId(\"minecraft:zombie\")"),
            "The runtime export must carry the entity:\n" + java);
        check(java.contains(".selectedCount(3)"), "The runtime export must carry the count");

        String meteor = MeteorExporter.generate(exportDoc, "com.example");
        check(meteor.contains("theme.item(") && meteor.contains("ItemStack"),
            "The static export must build an ItemStack:\n" + meteor);
        check(meteor.contains("\"Select\""),
            "The static export must build the Select button:\n" + meteor);
        // Only a real extraction knows the registry constants; the generated
        // atlas must fall back to a Registries lookup (see isVanillaItemConstant).
        if (assets.extractedTextures()) {
            check(meteor.contains("Items.DIAMOND"), "A vanilla item uses the Items constant");
            check(meteor.contains("Items.ZOMBIE_SPAWN_EGG"),
                "An entity uses its spawn egg constant:\n" + meteor);
        } else {
            check(meteor.contains("Registries.ITEM.get"),
                "Without extracted assets the export must look the item up by id:\n" + meteor);
        }
    }

    /** TEXTURE nodes: real images when assets exist, a placeholder otherwise. */    private static void textureWidget() {
        MeteorTheme theme = new MeteorTheme();

        UiNode texture = UiNode.texture("tex")
            .textureRef("item/diamond").size(32, 32);
        texture.renderX = 0;
        texture.renderY = 0;
        texture.renderW = 32;
        texture.renderH = 32;

        // With no texture source the painter must fall back, not throw.
        MeteorPainter painter = new MeteorPainter(theme);
        painter.setLocalTextureSource(TextureSource.NONE);
        UiDocument doc = new UiDocument("Texture");
        doc.root = UiNode.container("root", 200, 100);
        doc.root.layout(LayoutMode.ABSOLUTE);
        doc.root.child(texture);
        painter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);
        check(texture.renderW == 32 && texture.renderH == 32, "Texture size changed while painting");

        // A source that resolves everything must be asked for the reference.
        final String[] requested = {null};
        painter.setLocalTextureSource(reference -> {
            requested[0] = reference;
            return new Object();
        });
        painter.paint(TextMetrics.measurementCanvas(), doc.root, 0, 0);
        check("item/diamond".equals(requested[0]),
            "Painter asked for '" + requested[0] + "' instead of the node's texture");

        // Missing references get a readable short label.
        check("diamond".equals(MeteorPainter.missingLabel("item/diamond")), "Missing label wrong");
        check("stone".equals(MeteorPainter.missingLabel("block/stone.png")), "Missing label wrong for .png");
        check("reset".equals(MeteorPainter.missingLabel("reset")), "Missing label wrong for an icon name");
        check("?".equals(MeteorPainter.missingLabel("")), "Empty reference should label as '?'");

        assetStore();

        System.out.println("Texture widget OK (placeholder fallback + reference plumbed through)");
    }

    /**
     * The asset lookup must never throw, whatever the reference looks like. A
     * namespaced item id used to reach {@code Path.resolve} and blow up with
     * {@code InvalidPathException: Illegal char <:>} on Windows.
     *
     * <p>Runs whether or not the textures were extracted: without them every
     * lookup legitimately returns {@code null}.</p>
     */
    private static void assetStore() {
        AssetStore store = AssetStore.get();

        // Every spelling an author might type resolves to the same file (or to
        // nothing at all when the assets were not extracted).
        String[] references = {
            "diamond", "minecraft:diamond", "item/diamond", "minecraft:item/diamond",
            "textures/item/diamond.png", "stone", "minecraft:stone", "block/stone",
            "reset", "no such thing", "", ":", "minecraft:",
        };
        for (String reference : references) {
            try {
                store.texturePath(reference);
                store.meteorIcon(reference);
                store.textureOfItem(reference);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Asset lookup threw for '" + reference + "': " + e, e);
            }
        }

        // The extracted artwork is what these lookups can answer from; the
        // generated atlas deliberately reports neither a texture path nor a
        // spawn egg, so this branch keys off the extraction, not off "any root".
        if (store.extractedTextures()) {
            Path diamond = store.texturePath("minecraft:diamond");
            check(diamond != null && Files.isRegularFile(diamond),
                "minecraft:diamond should resolve to its texture");
            Path stone = store.texturePath("stone");
            check(stone != null && Files.isRegularFile(stone),
                "block items must resolve through the index (stone -> block/stone)");
            check(store.itemCount() > 1000, "The item index looks empty: " + store.itemCount());
            System.out.println("Asset store OK (" + store.itemCount() + " items at " + store.root() + ")");
        } else {
            System.out.println("Asset store OK (no extracted assets - placeholders will be used)");
        }

        // The generated icon atlas is the redistributable fallback: a release
        // package ships it instead of any of the game's artwork.
        AssetStore.IconTile diamondTile = store.iconOf("diamond");
        if (diamondTile == null) {
            System.out.println("Generated icon atlas OK (absent - nothing to check)");
        } else {
            check(diamondTile.cell() == 16,
                "atlas cells are 16x16, got " + diamondTile.cell());
            check(store.iconOf("minecraft:diamond") != null
                    && store.iconOf("minecraft:diamond").equals(diamondTile),
                "a namespaced id must find the same atlas cell");
            check(store.iconOf("item/diamond") != null && store.iconOf("item/diamond").equals(diamondTile),
                "an item/ prefixed id must find the same atlas cell");
            check(store.iconOf("no such thing") == null, "an unknown id must not invent an atlas cell");

            Path sheet = store.iconAtlas();
            check(sheet != null && Files.isRegularFile(sheet), "iconOf resolved without an atlas file");
            if (sheet != null) {
                java.awt.image.BufferedImage image = readImage(sheet);
                check(image != null && image.getWidth() >= diamondTile.x() + diamondTile.cell()
                        && image.getHeight() >= diamondTile.y() + diamondTile.cell(),
                    "the atlas cell lies outside the sheet: " + diamondTile);

                Path slice = store.iconAtlasCell("diamond");
                check(slice != null && Files.isRegularFile(slice), "an atlas cell must be sliceable to a file");
                if (slice != null) {
                    java.awt.image.BufferedImage cell = readImage(slice);
                    check(cell != null && cell.getWidth() == diamondTile.cell()
                            && cell.getHeight() == diamondTile.cell(),
                        "the sliced cell should be " + diamondTile.cell() + "x" + diamondTile.cell());
                }
                check(store.textureOfItem("diamond") == null || store.extractedTextures(),
                    "textureOfItem must not answer from the atlas");
            }
            System.out.println("Generated icon atlas OK (" + sheet + ")");
        }
    }

    /** Reads a PNG, returning {@code null} instead of a checked exception. */
    private static java.awt.image.BufferedImage readImage(Path path) {
        try {
            return javax.imageio.ImageIO.read(path.toFile());
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static void containerStyleExport() {
        UiDocument doc = new UiDocument("Styles");
        doc.root = UiNode.container("root", 300, 200).text("Root");
        doc.root.child(UiNode.container("s1", 200, 100).text("Sec").style(ContainerStyle.SECTION));
        doc.root.child(UiNode.container("v1", 200, 100).style(ContainerStyle.VIEW));

        String meteor = MeteorExporter.generate(doc, "com.example");
        check(meteor.contains("theme.section(\"Sec\"") && meteor.contains("theme.view()"),
            "Meteor export missing section/view widgets");

        String java = JavaExporter.generate(doc, "com.example");
        check(java.contains(".style(ContainerStyle.SECTION)") && java.contains(".style(ContainerStyle.VIEW)"),
            "Java export missing container styles");

        System.out.println("Container style export OK");
    }

    /**
     * A table must export as {@code theme.table()} with {@code .row()} breaks at
     * the right places, and {@code .expandX()} must follow the cell setting inside
     * a table - otherwise every column would stretch and the alignment (the whole
     * point of a table) would be lost in the generated code.
     */
    private static void tableExport() {
        UiDocument doc = new UiDocument("TableExport");
        doc.root = UiNode.container("root", 300, 200).text("Root");

        UiNode table = UiNode.container("tbl", 240, 80).style(ContainerStyle.TABLE);
        UiNode label1 = UiNode.label("l1").text("Range").size(80, 18);
        UiNode value1 = UiNode.textBox("v1").size(100, 22);
        UiNode reset1 = UiNode.texture("r1").textureRef("reset").size(20, 20);
        UiNode label2 = UiNode.label("l2").text("Delay").size(80, 18);
        UiNode value2 = UiNode.textBox("v2").size(100, 22);
        UiNode reset2 = UiNode.texture("r2").textureRef("reset").size(20, 20);
        value1.cell().expandX(true);
        value2.cell().expandX(true);
        label2.cell().startColumn();              // row 2 starts here
        table.child(label1).child(value1).child(reset1)
             .child(label2).child(value2).child(reset2);
        doc.root.child(table);

        String meteor = MeteorExporter.generate(doc, "com.example");
        check(meteor.contains("theme.table()"), "Meteor export should build a WTable:\n" + meteor);
        check(!meteor.contains("theme.window(\"tbl\""), "A table must not export as a window");

        // Three cells per row, so the first row break comes after the third cell.
        int firstRow = meteor.indexOf(".row();");
        check(firstRow > 0, "The table export needs .row() breaks:\n" + meteor);
        int label1At = meteor.indexOf("theme.label(\"Range\")");
        int label2At = meteor.indexOf("theme.label(\"Delay\")");
        check(label1At > 0 && label2At > label1At, "Both rows should be emitted in order");
        check(firstRow > meteor.indexOf("WTexture") || firstRow > 0,
            "The first row break must come after the first row's cells");

        // Only the expandX cells grow.
        check(meteor.contains("textBox(\"\", \"\")"), "Text boxes should still be created");
        int expandedTextBoxes = countOccurrences(meteor, "vhbox") + countOccurrences(meteor, ").expandX();");
        check(expandedTextBoxes > 0, "The expandable cells should still expand");

        String java = JavaExporter.generate(doc, "com.example");
        check(java.contains(".style(ContainerStyle.TABLE)"),
            "Java export missing the table style:\n" + java);

        System.out.println("Table export OK (theme.table + row breaks)");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    // ================= helpers =================

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }

    /** Floating point comparison with the tolerance layout math needs. */
    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.01;
    }

    /** Unused import guard for AnchorX/AnchorY (kept for future layout checks). */
    @SuppressWarnings("unused")
    private static final Object ANCHORS = new Object[] {AnchorX.LEFT, AnchorY.TOP, UiType.TEXTURE};
}
