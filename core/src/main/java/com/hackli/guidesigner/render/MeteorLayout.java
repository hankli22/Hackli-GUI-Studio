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

package com.hackli.guidesigner.render;

import com.hackli.guidesigner.model.AnchorX;
import com.hackli.guidesigner.model.ContainerStyle;
import com.hackli.guidesigner.model.AnchorY;
import com.hackli.guidesigner.model.LayoutMode;
import com.hackli.guidesigner.model.Orientation;
import com.hackli.guidesigner.model.UiNode;
import com.hackli.guidesigner.runtime.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor's container layout, ported from the compiled client so the designer
 * positions widgets exactly like the game.
 *
 * <p>Facts taken from {@code WVerticalList} / {@code WHorizontalList} /
 * {@code Cell} / {@code WWindow}:</p>
 * <ul>
 *   <li>{@code spacing = theme.scale(3)} and it is added <em>before every
 *       cell</em>, including the first one.</li>
 *   <li>Vertical list: {@code cell.x = x + padLeft},
 *       {@code cell.width = width - widthRemove - padLeft - padRight},
 *       {@code y += spacing + padTop + height + padBottom}.</li>
 *   <li>Horizontal list: {@code x += spacing + padLeft + width + padRight};
 *       the leftover width is split between the cells marked
 *       {@code expandCellX}.</li>
 *   <li>{@code Cell.alignWidget()}: {@code expandWidgetX} fills the cell,
 *       otherwise the widget is aligned by {@code alignX} / {@code alignY}.</li>
 *   <li>{@code WWindow}: header cell, then a view cell with
 *       {@code pad(8)} - so content is inset by {@code scale(8)} and there is
 *       one {@code spacing} gap below the header.</li>
 * </ul>
 */
public final class MeteorLayout {
    /** Meteor's {@code WVerticalList/WHorizontalList} spacing, before scaling. */
    public static final double SPACING = 3;

    /** Meteor's {@code WWindow.padding}, before scaling. */
    public static final double WINDOW_PADDING = 8;

    /** Meteor's {@code WSection.add(...)} padding, before scaling. */
    public static final double SECTION_PADDING = 6;

    /** Meteor's {@code WTable.horizontalSpacing} / {@code verticalSpacing}. */
    public static final double TABLE_SPACING = 3;

    private MeteorLayout() {}

    /** Content inset of a container, before scaling. */
    private static double padding(UiNode node) {
        if (node.style == ContainerStyle.SECTION) return SECTION_PADDING;
        // WTable has no padding of its own: its cells sit flush against the
        // parent's content box. It has no header either.
        if (node.style == ContainerStyle.TABLE) return 0;
        return WINDOW_PADDING;
    }

    /** Zeroes the rects of a collapsed section's descendants so they cannot be hit. */
    private static void hideChildren(UiNode node) {
        for (UiNode child : node.children) {
            child.renderW = 0;
            child.renderH = 0;
            hideChildren(child);
        }
    }

    /** Computes every node's rect, writing the transient render fields. */
    public static void layoutTree(UiNode node, double x, double y, double w, double h, MeteorTheme theme) {
        node.renderX = x;
        node.renderY = y;
        node.renderW = w;
        node.renderH = h;

        if (node.children.isEmpty()) return;

        if (node.layout == LayoutMode.ABSOLUTE) {
            UiRect parent = new UiRect(x, y, w, h);
            for (UiNode child : node.children) {
                UiRect r = absoluteRect(child, parent);
                layoutTree(child, r.x, r.y, r.w, r.h, theme);
            }
            return;
        }

        double spacing = theme.scaled(SPACING);
        double padding = theme.scaled(padding(node));

        // Meteor's containers put their content below the header cell:
        // - WWindow: accent header, body cell with pad(8)
        // - WSection: separator header with a collapse triangle, children get
        //   padHorizontal(6); a collapsed section hides its children entirely
        boolean hasHeader = node.style == ContainerStyle.SECTION || !node.text.isEmpty();
        double headerH = hasHeader ? theme.headerHeight() : 0;
        if (node.collapsed) {
            node.renderH = Math.min(h, headerH);
            hideChildren(node);
            return;
        }

        double contentTop = y + headerH + padding;
        double contentX = x + padding;
        double contentW = Math.max(1, w - padding * 2);

        // WView: the viewport is the node's rect, the content scrolls inside it.
        boolean scrollable = node.style == ContainerStyle.VIEW;
        double scroll = 0;
        if (scrollable) {
            double viewport = viewportHeight(node, h);
            double maxScroll = Math.max(0, node.scrollHeight - viewport);
            node.scroll = Math.max(0, Math.min(node.scroll, maxScroll));
            scroll = node.scroll;
            contentTop -= scroll;
        }

        double end;
        if (node.style == ContainerStyle.TABLE) {
            end = layoutTable(node, contentX, contentTop, contentW, theme);
        } else if (node.orientation == Orientation.HORIZONTAL) {
            end = layoutHorizontal(node, contentX, contentTop, contentW, theme, spacing);
        } else {
            end = layoutVertical(node, contentX, contentTop, contentW, theme, spacing);
        }

        if (scrollable) {
            node.scrollHeight = Math.max(0, end - (y + headerH) + padding + scroll);
        }
    }

    /** Visible height of a view: its own height, optionally capped by maxHeight. */
    public static double viewportHeight(UiNode node, double height) {
        if (node.maxHeight > 0) return Math.min(height, node.maxHeight);
        return height;
    }

    private static double layoutVertical(UiNode parent, double x, double startY, double width,
                                         MeteorTheme theme, double spacing) {
        double cursor = startY;

        for (UiNode child : parent.children) {
            UiNode.Cell cell = child.cell;
            double padTop = pad(cell == null ? 0 : cell.padTop, theme);
            double padRight = pad(cell == null ? 0 : cell.padRight, theme);
            double padBottom = pad(cell == null ? 0 : cell.padBottom, theme);
            double padLeft = pad(cell == null ? 0 : cell.padLeft, theme);

            cursor += spacing + padTop;

            double cellX = x + padLeft;
            double cellW = Math.max(1, width - padLeft - padRight);
            double naturalW = naturalWidth(child);
            // Meteor stretches a cell only when it is marked expandX; in the
            // designer an explicit width means "natural size, aligned", while
            // width 0 keeps the familiar "fill the container" behaviour.
            boolean fill = isExpand(cell) || child.width <= 0;
            double childW = fill ? cellW : Math.min(naturalW, cellW);
            double childH = naturalHeight(child);

            double childX = cellX + alignX(cell).f * (cellW - childW);
            double childY = cursor + alignY(cell).f * 0;

            layoutTree(child, childX, childY, childW, childH, theme);

            cursor += childH + padBottom;
        }

        return cursor;
    }

    private static double layoutHorizontal(UiNode parent, double startX, double y, double width,
                                           MeteorTheme theme, double spacing) {
        // Pass 1: natural widths and the expand count (Meteor's fillXCount).
        double natural = 0;
        int fillCount = 0;
        double rowHeight = 0;

        List<double[]> metrics = new ArrayList<>();
        for (UiNode child : parent.children) {
            UiNode.Cell cell = child.cell;
            double padTop = pad(cell == null ? 0 : cell.padTop, theme);
            double padRight = pad(cell == null ? 0 : cell.padRight, theme);
            double padBottom = pad(cell == null ? 0 : cell.padBottom, theme);
            double padLeft = pad(cell == null ? 0 : cell.padLeft, theme);

            double w = Math.max(naturalWidth(child), cell == null ? 0 : theme.scaled(cell.minWidth));
            double h = naturalHeight(child);

            natural += spacing + padLeft + w + padRight;
            rowHeight = Math.max(rowHeight, padTop + h + padBottom);
            if (isExpand(cell)) fillCount++;

            metrics.add(new double[]{padTop, padRight, padBottom, padLeft, w, h});
        }

        // Pass 2: distribute the leftover width across the expanding cells.
        double extra = fillCount > 0 ? Math.max(0, (width - natural) / fillCount) : 0;
        double cursor = startX;

        for (int i = 0; i < parent.children.size(); i++) {
            UiNode child = parent.children.get(i);
            UiNode.Cell cell = child.cell;
            double[] m = metrics.get(i);

            double padTop = m[0], padRight = m[1], padBottom = m[2], padLeft = m[3];
            double childW = m[4] + (isExpand(cell) ? extra : 0);
            double childH = m[5];

            cursor += spacing + padLeft;
            double childX = cursor + alignX(cell).f * Math.max(0, (childW - m[4]));
            double childY = y + padTop + alignY(cell).f * Math.max(0, (rowHeight - padTop - padBottom - childH));

            layoutTree(child, childX, childY, childW, childH, theme);
            cursor += childW + padRight;
        }

        return y + rowHeight;
    }

    /** Meteor's {@code Cell.alignWidget} for X. */
    private static AnchorX alignX(UiNode.Cell cell) {
        return cell == null || cell.alignX == null ? AnchorX.LEFT : cell.alignX;
    }
    private static AnchorY alignY(UiNode.Cell cell) {
        return cell == null || cell.alignY == null ? AnchorY.TOP : cell.alignY;
    }

    private static boolean isExpand(UiNode.Cell cell) {
        return cell != null && cell.expandX;
    }

    // ================= WTable =================

    /**
     * Default column count for a table container, without a known width.
     *
     * <p>Used by the exporters, which describe the widget tree but do not know how
     * wide the table will end up being. Explicit breaks win (the longest row is
     * the column count); with no breaks every child goes on one row, which is what
     * a settings row like {@code [label][widget][reset]} wants.</p>
     */
    public static int tableColumns(UiNode parent) {
        List<List<UiNode>> rows = tableRows(parent, Math.max(1, parent.children.size()));
        int columns = 1;
        for (List<UiNode> row : rows) columns = Math.max(columns, row.size());
        return Math.max(1, columns);
    }

    /**
     * Splits a table's children into rows.
     *
     * <p>Meteor builds a table with {@code add(...)} calls and a {@code row()}
     * after each row; in the document that break is a {@code cell.row} flag (with
     * {@code cell.column} as the "a new row starts here" spelling). Without any
     * flag the cells are grouped {@code columns} at a time, and any leftover cells
     * spill onto a last, shorter row.</p>
     *
     * @param columns used only when the table has no explicit breaks
     */
    public static List<List<UiNode>> tableRows(UiNode parent, int columns) {
        return tableRows(parent, columns, hasExplicitBreaks(parent));
    }

    /**
     * @param explicit when true the {@code row}/{@code column} flags alone decide
     *                 the rows; when false the cells are grouped {@code columns}
     *                 at a time
     */
    private static List<List<UiNode>> tableRows(UiNode parent, int columns, boolean explicit) {
        List<List<UiNode>> rows = new ArrayList<>();
        List<UiNode> current = new ArrayList<>();
        int perRow = Math.max(1, columns);

        for (UiNode child : parent.children) {
            UiNode.Cell cell = child.cell;

            // column() starts a new row at this cell.
            if (cell != null && cell.column && !current.isEmpty()) {
                rows.add(current);
                current = new ArrayList<>();
            }

            current.add(child);

            // row() ends the row after this cell.
            if (cell != null && cell.row) {
                rows.add(current);
                current = new ArrayList<>();
            } else if (!explicit && current.size() >= perRow) {
                rows.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    /** True when any child carries an explicit {@code row}/{@code column} break. */
    private static boolean hasExplicitBreaks(UiNode parent) {
        for (UiNode child : parent.children) {
            UiNode.Cell cell = child.cell;
            if (cell != null && (cell.row || cell.column)) return true;
        }
        return false;
    }

    /**
     * Lays children out like Meteor's {@code WTable}: columns are aligned, each
     * column is as wide as its widest cell and each row as tall as its tallest.
     *
     * <p>Ported from {@code WTable.calculateInfo()} / {@code onCalculateWidgetPositions()}:</p>
     * <ul>
     *   <li>one child is one cell, in order (Meteor's {@code table.add(...)})</li>
     *   <li>{@code cell.row} is Meteor's {@code table.row()} after that cell - it
     *       ends the row, and repeating it on every row is what keeps the columns
     *       aligned</li>
     *   <li>{@code cell.column} starts a new row at that cell, for tables whose
     *       rows do not all have the same length</li>
     *   <li>without any hint the row wraps on its own as soon as the next cell
     *       would not fit, which is what makes a table survive a resized window</li>
     *   <li>extra width goes to the {@code expandX} cells of that row, split
     *       evenly (Meteor: {@code rowExpandCellXCounts})</li>
     *   <li>cells sharing a {@code group} share the widest width of that group,
     *       which is how two tables line their columns up with each other</li>
     * </ul>
     */
    private static double layoutTable(UiNode parent, double x, double startY, double width, MeteorTheme theme) {
        List<UiNode> children = parent.children;
        int count = children.size();
        if (count == 0) return startY;

        double spacing = theme.scaled(TABLE_SPACING);
        int columns = tableColumnCount(parent, width, theme, spacing);
        List<List<UiNode>> rows = tableRows(parent, columns);

        // Pass 2: column widths (max over the column, padding included) and row
        // heights (max over the row).
        List<Double> columnWidths = new ArrayList<>();
        List<Double> rowHeights = new ArrayList<>();
        for (List<UiNode> row : rows) {
            double rowHeight = 0;
            for (int i = 0; i < row.size(); i++) {
                double cellWidth = cellWidth(row.get(i), theme);
                if (columnWidths.size() <= i) columnWidths.add(cellWidth);
                else if (cellWidth > columnWidths.get(i)) columnWidths.set(i, cellWidth);
                rowHeight = Math.max(rowHeight, cellHeight(row.get(i), theme));
            }
            rowHeights.add(rowHeight);
        }

        // Pass 2b: cells sharing a group share the widest column of that group.
        syncGroups(children, columnWidths);

        // Pass 3: place the widgets, spreading the leftover width over the
        // expandX cells exactly like Meteor does.
        double y = startY;
        for (int rowI = 0; rowI < rows.size(); rowI++) {
            List<UiNode> row = rows.get(rowI);

            if (rowI > 0) y += spacing;
            double cursor = x;
            double rowHeight = rowHeights.get(rowI);

            double rowWidth = 0;
            int expandCount = 0;
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) rowWidth += spacing;
                rowWidth += columnWidths.get(i);
                if (isExpand(row.get(i).cell)) expandCount++;
            }
            double extra = expandCount > 0 ? Math.max(0, (width - rowWidth) / expandCount) : 0;

            for (int i = 0; i < row.size(); i++) {
                UiNode child = row.get(i);
                UiNode.Cell cell = child.cell;
                if (i > 0) cursor += spacing;

                double columnWidth = columnWidths.get(i);
                double cellSlot = columnWidth + (isExpand(cell) ? extra : 0);

                double padTop = pad(cell == null ? 0 : cell.padTop, theme);
                double padRight = pad(cell == null ? 0 : cell.padRight, theme);
                double padBottom = pad(cell == null ? 0 : cell.padBottom, theme);
                double padLeft = pad(cell == null ? 0 : cell.padLeft, theme);

                double innerW = Math.max(1, cellSlot - padLeft - padRight);
                double innerH = Math.max(1, rowHeight - padTop - padBottom);
                // Meteor's expandCellX() also expands the widget, so an expandable
                // column is filled by its widget. A widget that asked for its own
                // width keeps it and is aligned inside the cell instead.
                boolean fill = isExpand(cell) || child.width <= 0;
                double childW = fill ? innerW : Math.min(naturalWidth(child), innerW);
                double childH = naturalHeight(child);

                double childX = cursor + padLeft + alignX(cell).f * Math.max(0, innerW - childW);
                double childY = y + padTop + alignY(cell).f * Math.max(0, innerH - childH);

                layoutTree(child, childX, childY, childW, childH, theme);
                cursor += cellSlot;
            }

            y += rowHeight;
        }

        return y;
    }

    /** A cell's width including its padding and minimum width. */
    private static double cellWidth(UiNode child, MeteorTheme theme) {
        UiNode.Cell cell = child.cell;
        return pad(cell == null ? 0 : cell.padLeft, theme)
            + Math.max(naturalWidth(child), cell == null ? 0 : theme.scaled(cell.minWidth))
            + pad(cell == null ? 0 : cell.padRight, theme);
    }

    /** A cell's height including its vertical padding. */
    private static double cellHeight(UiNode child, MeteorTheme theme) {
        UiNode.Cell cell = child.cell;
        return pad(cell == null ? 0 : cell.padTop, theme)
            + naturalHeight(child)
            + pad(cell == null ? 0 : cell.padBottom, theme);
    }

    /** Widest width of each {@code group}, applied to every column in it. */
    private static void syncGroups(List<UiNode> children, List<Double> columnWidths) {
        java.util.Map<String, Integer> groups = new java.util.HashMap<>();
        for (int column = 0; column < columnWidths.size(); column++) {
            UiNode.Cell cell = column < children.size() ? children.get(column).cell : null;
            if (cell == null || cell.group == null || cell.group.isEmpty()) continue;
            groups.put(cell.group, column);
        }
        for (int column = 0; column < columnWidths.size(); column++) {
            UiNode.Cell cell = column < children.size() ? children.get(column).cell : null;
            if (cell == null || cell.group == null || cell.group.isEmpty()) continue;
            Integer first = groups.get(cell.group);
            if (first == null) continue;
            columnWidths.set(first, Math.max(columnWidths.get(first), columnWidths.get(column)));
        }
        for (int column = 0; column < columnWidths.size(); column++) {
            UiNode.Cell cell = column < children.size() ? children.get(column).cell : null;
            if (cell == null || cell.group == null || cell.group.isEmpty()) continue;
            Integer first = groups.get(cell.group);
            if (first != null) columnWidths.set(column, columnWidths.get(first));
        }
    }

    /**
     * How many cells fit on one row.
     *
     * <p>An explicit {@code column}/{@code row} hint fixes the count; otherwise
     * the widest prefix of cells that still fits within {@code width} wins, so a
     * plain list of cells behaves like a responsive table instead of overflowing.</p>
     */
    /**
     * How many cells fit on one row.
     *
     * <p>An explicit {@code row} hint ends a row, so the first one fixes the
     * column count for the whole table - that is Meteor's {@code table.row()} and
     * it is what keeps every row of a settings page aligned. Without a hint the
     * widest column count that still fits wins: the width is the sum of the
     * <em>effective</em> column widths (the widest cell of each column), because a
     * narrow first cell must not make room for a wide fourth column.</p>
     */
    private static int tableColumnCount(UiNode parent, double width, MeteorTheme theme, double spacing) {
        List<UiNode> children = parent.children;

        // An explicit hint from the author always wins: the longest row is the
        // column count, so every later row lines up with it. The grouping must not
        // wrap on width here - the hints alone decide, however wide the cells are.
        if (hasExplicitBreaks(parent)) {
            List<List<UiNode>> rows = tableRows(parent, Integer.MAX_VALUE, true);
            int columns = 0;
            for (List<UiNode> row : rows) columns = Math.max(columns, row.size());
            return Math.max(1, columns);
        }

        // Otherwise the widest column count whose cells fit, one row deep. A table
        // is filled row by row, so a plain list of six cells must break after the
        // same number of cells as the width allows.
        int total = children.size();
        int best = 1;
        for (int columns = 1; columns <= total; columns++) {
            double used = 0;
            for (int column = 0; column < columns; column++) {
                double columnWidth = 0;
                for (int i = column; i < total; i += columns) {
                    columnWidth = Math.max(columnWidth, cellWidth(children.get(i), theme));
                }
                used += (column == 0 ? 0 : spacing) + columnWidth;
            }
            if (used <= width) best = columns;
            else break;
        }
        return Math.max(1, best);
    }

    /** Cell padding is multiplied by the theme scale, like Meteor's {@code Cell.s()}. */
    private static double pad(double value, MeteorTheme theme) {
        return value == 0 ? 0 : theme.scaled(value);
    }

    /**
     * True when the point is inside a section's header bar, i.e. a click should
     * collapse or expand it instead of hitting a child.
     */
    public static boolean isSectionHeader(UiNode node, double px, double py, MeteorTheme theme) {
        if (node == null || node.style != ContainerStyle.SECTION) return false;
        return px >= node.renderX && px <= node.renderX + node.renderW
            && py >= node.renderY && py <= node.renderY + theme.headerHeight();
    }

    /** Deepest {@link ContainerStyle#VIEW} containing the point, or {@code null}. */
    public static UiNode viewAt(UiNode node, double px, double py) {
        if (node == null || !node.visible) return null;

        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode found = viewAt(node.children.get(i), px, py);
            if (found != null) return found;
        }

        if (node.style == ContainerStyle.VIEW
            && px >= node.renderX && px <= node.renderX + node.renderW
            && py >= node.renderY && py <= node.renderY + node.renderH) {
            return node;
        }
        return null;
    }

    /**
     * Scrolls a view by {@code amount} document pixels, clamped to its content.
     *
     * @return true when the offset actually changed
     */
    public static boolean scroll(UiNode view, double amount) {
        if (view == null || view.style != ContainerStyle.VIEW) return false;
        double max = Math.max(0, view.scrollHeight - viewportHeight(view, view.renderH));
        double next = Math.max(0, Math.min(max, view.scroll + amount));
        if (next == view.scroll) return false;
        view.scroll = next;
        return true;
    }

    /** Rect of an absolutely placed widget inside a parent rect. */
    public static UiRect absoluteRect(UiNode node, UiRect parent) {
        double w = naturalWidth(node);
        double h = naturalHeight(node);

        double px = parent.x + node.anchorX.f * (parent.w - w) + node.x;
        double py = parent.y + node.anchorY.f * (parent.h - h) + node.y;

        return new UiRect(px, py, w, h);
    }

    private static double naturalWidth(UiNode node) {
        return node.width > 0 ? node.width : node.type.defaultWidth;
    }

    private static double naturalHeight(UiNode node) {
        return node.height > 0 ? node.height : node.type.defaultHeight;
    }

    /**
     * Selects the widget under the pointer, honouring draw order: children are
     * painted in order, so the <em>last</em> child is on top and wins.
     *
     * <p>Children are tested even when the pointer is outside the parent's
     * rectangle, because nothing clips them: a widget that overflows its
     * container (or was dragged past its edge) is still visible and must stay
     * clickable. Allocation free: it runs on every mouse move.</p>
     */
    public static UiNode hitTest(UiNode node, double px, double py) {
        if (!node.visible) return null;

        for (int i = node.children.size() - 1; i >= 0; i--) {
            UiNode found = hitTest(node.children.get(i), px, py);
            if (found != null) return found;
        }

        boolean inside = px >= node.renderX && px <= node.renderX + node.renderW
            && py >= node.renderY && py <= node.renderY + node.renderH;
        return inside ? node : null;
    }
}
