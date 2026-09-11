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

package com.hackli.guidesigner.gl;

import com.hackli.guidesigner.render.MeteorTheme;
import com.hackli.guidesigner.model.UiDocument;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.opengl.GL;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * OpenGL "comet" simulator and editor.
 *
 * <p>Renders a design document with the same primitive model Meteor uses
 * (state-coloured quads with a 2px inset outline, accent window headers, glyph
 * atlas text, scissor clipping) and lets you edit it live: drag to reorder,
 * alt+drag for free placement, resize handles, snapping, undo/redo, text
 * editing, and a play mode where the widgets behave like the real click GUI.</p>
 *
 * <pre>
 *   GlSimulator &lt;design.json&gt;                        live editor window (ESC quits)
 *   GlSimulator &lt;design.json&gt; --play                 start in play mode
 *   GlSimulator --render &lt;design.json&gt; &lt;out.png&gt;     offscreen render
 *   GlSimulator &lt;design.json&gt; --scale 0.75           Meteor's default GUI scale
 *   GlSimulator &lt;design.json&gt; --font &lt;file.ttf&gt; --font-size 12
 * </pre>
 */
public class GlSimulator {
    private static final int DESIGN_SUPERSAMPLE = 3;   // crisp canvas text up to 3x zoom

    private static final int MIN_W = 1280;
    private static final int MIN_H = 800;

    public static void main(String[] args) throws Exception {
        String designPath = null;
        String outPath = null;
        String dataDir = null;
        int width = 0, height = 0;
        String fontPath = null;
        int fontSize = 9;
        int uiFontSize = 0;
        double uiScaleOverride = 0;
        double scale = 1.0;
        double zoomOverride = 0;
        boolean startPlay = false;
        boolean withUi = false;
        boolean selfTest = false;
        boolean stress = false;
        boolean dumpLayout = false;
        String selectId = null;
        String menuId = null;
        boolean prompt = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--render" -> {
                    designPath = args[++i];
                    outPath = args[++i];
                }
                case "--size" -> {
                    String[] parts = args[++i].split("x");
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                }
                case "--font" -> fontPath = args[++i];
                case "--font-size" -> fontSize = Integer.parseInt(args[++i]);
                case "--ui-font-size" -> uiFontSize = Integer.parseInt(args[++i]);
                case "--ui-scale" -> uiScaleOverride = Double.parseDouble(args[++i]);
                case "--zoom" -> zoomOverride = Double.parseDouble(args[++i]);
                case "--scale" -> scale = Double.parseDouble(args[++i]);
                case "--play" -> startPlay = true;
                case "--with-ui" -> withUi = true;
                case "--select" -> selectId = args[++i];
                case "--menu" -> menuId = args[++i];
                case "--prompt" -> prompt = true;
                case "--selftest" -> selfTest = true;
                case "--dump-layout" -> dumpLayout = true;
                case "--stress" -> stress = true;
                // Where projects/layouts/export/templates live. Defaults to the
                // portable data folder next to the application (-Dhackli.home,
                // or ./data); the launch scripts pass their own directory.
                case "--data" -> dataDir = args[++i];
                default -> designPath = args[i];
            }
        }

        if (designPath == null) {
            System.out.println("Usage: GlSimulator <design.json> [--play] [--scale 0.75] [--font file.ttf] [--font-size 9]");
            System.out.println("       GlSimulator --render <design.json> <out.png> [--size WxH] [--scale 0.75] [--with-ui]");
            System.out.println("       --ui-scale 1.4  --ui-font-size 16   editor chrome size");
            System.out.println("       --data <dir>    project data folder (default: ./data, or -Dhackli.home)");
            return;
        }

        // Projects, templates and exports are portable: they live in the data
        // folder next to the application (--data / -Dhackli.home / ./data) rather
        // than in the user's home directory, so the whole studio can be copied to
        // another machine or run from a USB stick. The in-game designer uses the
        // instance's config directory instead; point both at one folder with
        // -Dhackli.home to share a single project list.
        if (dataDir != null) {
            com.hackli.guidesigner.model.DocumentStore.init(Path.of(dataDir));
        }
        com.hackli.guidesigner.model.DocumentStore.migrateLegacyDir();

        UiDocument doc = UiDocument.fromJson(Files.readString(Path.of(designPath)));
        if (doc == null) {
            System.err.println("Could not parse design: " + designPath);
            System.exit(1);
        }

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

        // Default to a large window: the editor needs the palette, canvas,
        // tree and inspector side by side. glfwGetMonitorWorkarea is in screen
        // coordinates, so it stays correct on scaled displays.
        long monitor = glfwGetPrimaryMonitor();
        org.lwjgl.glfw.GLFWVidMode videoMode = monitor == NULL ? null : glfwGetVideoMode(monitor);
        int[] workX = new int[1], workY = new int[1], workW = new int[1], workH = new int[1];
        boolean haveWork = monitor != NULL;
        if (haveWork) glfwGetMonitorWorkarea(monitor, workX, workY, workW, workH);
        if (width <= 0 || height <= 0) {
            if (haveWork && workW[0] > 0) {
                width = Math.max(MIN_W, (int) (workW[0] * 0.96));
                height = Math.max(MIN_H, (int) (workH[0] * 0.96));
            } else if (videoMode != null) {
                width = Math.max(MIN_W, (int) (videoMode.width() * 0.92));
                height = Math.max(MIN_H, (int) (videoMode.height() * 0.90));
            } else {
                width = MIN_W;
                height = MIN_H;
            }
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        // Hidden for offscreen renders and for the self test: a background test
        // must never steal focus or pop a window in front of the user.
        glfwWindowHint(GLFW_VISIBLE, outPath != null || selfTest ? GLFW_FALSE : GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        long window = glfwCreateWindow(width, height, "Hackli GUI Studio - Meteor (OpenGL) editor", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Could not create GLFW window");

        if (outPath == null && haveWork && workW[0] > 0) {
            glfwSetWindowPos(window,
                workX[0] + Math.max(0, (workW[0] - width) / 2),
                workY[0] + Math.max(0, (workH[0] - height) / 2));
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        // DPI aware editor scale: the chrome font is rasterised at the size it
        // is drawn at, so text stays crisp instead of being scaled up.
        float[] contentScaleX = new float[1], contentScaleY = new float[1];
        glfwGetWindowContentScale(window, contentScaleX, contentScaleY);
        double uiScale = uiScaleOverride > 0 ? uiScaleOverride
            : Math.min(2.0, Math.max(1.0, Math.max(contentScaleX[0], contentScaleY[0])));
        if (uiScaleOverride <= 0 && uiScale < 1.2) {
            int[] fbW = new int[1], fbH = new int[1];
            glfwGetFramebufferSize(window, fbW, fbH);
            if (fbH[0] >= 1400) uiScale = 1.4;   // big screen at 100% scaling
        }
        if (uiFontSize <= 0) uiFontSize = (int) Math.round(14 * uiScale);
        else uiScale = Math.max(1.0, uiFontSize / 14.0);

        // Document units are Meteor's GUI pixels, so on a scaled display the
        // preview needs zooming to look like the real click GUI.
        double defaultZoom = zoomOverride > 0 ? zoomOverride
            : Math.max(1.0, Math.min(3.0, Math.round(Math.max(contentScaleX[0], contentScaleY[0]))));

        System.out.println("GL: " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
        System.out.println("Window: " + width + "x" + height
            + (haveWork && workW[0] > 0 ? " (work area " + workW[0] + "x" + workH[0] + ")" : "")
            + "  ui scale " + String.format("%.2f", uiScale) + " (font " + uiFontSize + "px)");

        int fbo = 0, colorTex = 0;
        if (outPath != null) {
            colorTex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, colorTex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer incomplete");
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }

        Gl2D gl = new Gl2D();
        gl.init(width, height);

        // Textures come from the extracted vanilla assets (gradle :core:extractMcAssets).
        GlTextureSource textures = new GlTextureSource();
        com.hackli.guidesigner.render.MeteorPainter.setTextureSource(textures);

        MeteorTheme theme = new MeteorTheme();
        theme.scale = scale;

        // Two atlases: the design preview must keep Meteor's 9px font, while the
        // editor chrome is rasterised at the display's real pixel size.
        FontAtlas designFont = new FontAtlas(fontPath, fontSize, DESIGN_SUPERSAMPLE);
        FontAtlas uiFont = new FontAtlas(fontPath, uiFontSize);
        int rounded = Gl2D.roundedRectTexture(16, 3, 4);
        int circle = Gl2D.roundedRectTexture(32, 16, 8);
        Gl2D.setCircleTexture(circle);
        DesignRenderer renderer = new DesignRenderer(gl, designFont, theme);
        Path savePath = selfTest
            ? Files.createTempFile("hackli-selftest-", ".json")
            : Path.of(designPath);
        GlEditor editor = new GlEditor(gl, renderer, uiFont, uiScale, theme, doc, savePath, fontPath);
        editor.setZoom(defaultZoom);
        if (selectId != null) editor.selectById(selectId);
        if (menuId != null) editor.openMenu(menuId);
        if (prompt) editor.openNamePrompt("Template name", "MyTemplate", value -> System.out.println("prompt: " + value));

        if (outPath != null) {
            editor.setShowChrome(withUi);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            editor.frame(0);

            if (dumpLayout) dumpLayout(doc.root, 0);

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            writePng(pixels, width, height, Path.of(outPath));

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            System.out.println("Rendered " + doc.name + " -> " + outPath);
        } else if (selfTest) {
            runSelfTest(editor, doc, gl, fbo);
        } else {
            wireInput(window, editor);
            if (startPlay) editor.onKey(GlEditor.Keys.F5, false, false, false);

            int[] fbW = new int[1], fbH = new int[1];
            int[] winW = new int[1], winH = new int[1];
            int frameNo = 0;
            double last = glfwGetTime();
            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents();
                // Esc belongs to the editor (close menu, cancel, deselect);
                // quit with Ctrl+Q or the window close button.
                if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS
                    && (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS
                        || glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS)) {
                    glfwSetWindowShouldClose(window, true);
                }

                // Follow window resizes and DPI changes every frame.
                glfwGetFramebufferSize(window, fbW, fbH);
                gl.resize(fbW[0], fbH[0]);
                glfwGetWindowSize(window, winW, winH);

                double now = glfwGetTime();
                double dt = Math.min(0.1, now - last);
                last = now;

                if (stress && frameNo % 30 == 29) {
                    editor.setUiScale(frameNo % 60 < 30 ? 1.5 : 2.0);
                }
                frameNo++;

                editor.frame(dt);
                glfwSwapBuffers(window);
            }
        }

        textures.dispose();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    /**
     * Prints the computed rect of every node after a render ({@code --dump-layout}).
     * Layout bugs are much easier to see as numbers than as pixels.
     */
    private static void dumpLayout(com.hackli.guidesigner.model.UiNode node, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ".repeat(Math.max(0, depth)));
        sb.append(String.format("%-14s %-10s x=%7.1f y=%7.1f w=%6.1f h=%6.1f",
            node.id, node.type.name().toLowerCase(),
            node.renderX, node.renderY, node.renderW, node.renderH));
        if (node.type == com.hackli.guidesigner.model.UiType.CONTAINER) {
            sb.append("  style=").append(node.style.name().toLowerCase());
        }
        com.hackli.guidesigner.model.UiNode.Cell cell = node.cell;
        if (cell != null && (cell.row || cell.column || cell.expandX)) {
            sb.append("  cell[");
            if (cell.row) sb.append("endRow ");
            if (cell.column) sb.append("newRow ");
            if (cell.expandX) sb.append("expandX ");
            sb.append("]");
        }
        System.out.println(sb);
        for (com.hackli.guidesigner.model.UiNode child : node.children) dumpLayout(child, depth + 1);
    }

    /**
     * Headless interaction check: drives the editor through selection, drag,
     * resize, undo/redo, text editing and play-mode widget behaviour, then
     * saves the document. Any exception fails the run.
     */
    private static void runSelfTest(GlEditor editor, UiDocument doc, Gl2D gl, int fbo) throws Exception {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        // Events can arrive before the first frame (GLFW reports the cursor as
        // soon as the window opens); this must not throw.
        editor.onMouseMove(600, 400);
        editor.onMouseDown(600, 400, 0);
        editor.onMouseUp(600, 400, 0);
        editor.onScroll(1);

        editor.frame(0.016);

        java.util.List<com.hackli.guidesigner.model.UiNode> nodes = new java.util.ArrayList<>();
        collectNodes(doc.root, nodes);
        if (nodes.isEmpty()) throw new IllegalStateException("selftest: design has no nodes");

        // Node rects are document space; pointer events are screen space.
        com.hackli.guidesigner.model.UiNode first = nodes.get(0);
        double[] centre = editor.worldToScreen(first.renderX + first.renderW / 2,
            first.renderY + first.renderH / 2);
        double cx = centre[0], cy = centre[1];

        // --- z-order: the Close button overlaps the panel, it must win ---
        double[] overlap = editor.worldToScreen(400, 330);
        editor.onMouseDown(overlap[0], overlap[1], 0);
        editor.onMouseUp(overlap[0], overlap[1], 0);
        editor.frame(0.016);
        String picked = editor.selection().isEmpty() ? "-" : editor.selection().get(0).id;
        if (!picked.equals("btn-close")) {
            throw new IllegalStateException("selftest: hit test picked " + picked + " instead of btn-close");
        }
        // --- design mode: select, drag, undo/redo, text edit ---
        editor.onMouseMove(cx, cy);
        editor.onMouseDown(cx, cy, 0);
        editor.onMouseMove(cx + 24, cy + 12);
        editor.onMouseUp(cx + 24, cy + 12, 0);
        editor.frame(0.016);

        editor.onMouseDown(cx + 24, cy + 12, 0);
        editor.onMouseMove(cx + 60, cy + 40);
        editor.onMouseUp(cx + 60, cy + 40, 0);
        editor.frame(0.016);

        editor.onKey(GlEditor.Keys.Z, true, false, false);
        editor.onKey(GlEditor.Keys.Y, true, false, false);
        editor.onKey(GlEditor.Keys.D, true, false, false);
        editor.onKey(GlEditor.Keys.LEFT, false, false, false);
        editor.onKey(GlEditor.Keys.DOWN, false, true, false);
        editor.onKey(GlEditor.Keys.TAB, false, false, false);
        editor.onKey(GlEditor.Keys.S, true, false, false);
        editor.onChar('x');
        editor.frame(0.016);

        // --- palette: add one of every widget type, then undo them all ---
        for (com.hackli.guidesigner.model.UiType type : com.hackli.guidesigner.model.UiType.values()) {
            editor.addWidget(type);
            editor.frame(0.016);
        }
        for (int i = 0; i < com.hackli.guidesigner.model.UiType.values().length; i++) {
            editor.onKey(GlEditor.Keys.Z, true, false, false);
        }
        editor.frame(0.016);

        // --- play mode: every widget kind ---
        editor.onKey(GlEditor.Keys.F5, false, false, false);
        editor.frame(0.016);
        if (editor.mode() != GlEditor.Mode.PLAY) throw new IllegalStateException("selftest: F5 did not enter play mode");
        for (com.hackli.guidesigner.model.UiNode node : nodes) {
            double[] p = editor.worldToScreen(node.renderX + node.renderW / 2,
                node.renderY + node.renderH / 2);
            editor.onMouseMove(p[0], p[1]);
            editor.onMouseDown(p[0], p[1], 0);
            editor.onMouseUp(p[0], p[1], 0);
            editor.onChar('7');
            editor.onScroll(1);
            editor.frame(0.016);
        }
        editor.onKey(GlEditor.Keys.ESCAPE, false, false, false);
        editor.onKey(GlEditor.Keys.F5, false, false, false);
        editor.frame(0.016);
        if (editor.mode() != GlEditor.Mode.DESIGN) throw new IllegalStateException("selftest: F5 did not return to design mode");

        // --- toolbar: UI -/+ must survive the atlas rebuild (real click path) ---
        editor.frame(0.016);
        for (String label : new String[]{"UI +", "UI +", "UI -", "UI -", "Snap: on", "Snap: off"}) {
            double[] r = editor.toolbarButtonRect(label);
            if (r == null) throw new IllegalStateException("selftest: no toolbar button " + label);
            editor.onMouseDown(r[0] + r[2] / 2, r[1] + r[3] / 2, 0);
            editor.frame(0.016);
            editor.onMouseUp(r[0] + r[2] / 2, r[1] + r[3] / 2, 0);
            editor.frame(0.016);
        }


        // --- inspector: edit a numeric property through the real widgets ---
        com.hackli.guidesigner.model.UiNode slider = doc.find("sld-size");
        if (slider != null) {
            editor.selectById("sld-size");
            editor.frame(0.016);
            double[] row = editor.inspectorRowRect(5);   // type,id,visible,anchorX,anchorY,x
            editor.onMouseDown(row[0] + 4, row[1] + row[3] / 2, 0);
            editor.frame(0.016);
            editor.onChar('5');
            editor.frame(0.016);
            editor.onKey(GlEditor.Keys.ENTER, false, false, false);
            editor.frame(0.016);
            if (!String.valueOf((long) slider.x).endsWith("5")) {
                throw new IllegalStateException("selftest: inspector edit failed (x=" + slider.x + ")");
            }
        }

        // --- number widget: -/+ buttons, slider drag and typing ---
        editor.selectById("root");
        editor.addWidget(com.hackli.guidesigner.model.UiType.NUMBER);
        com.hackli.guidesigner.model.UiNode number = editor.selection().get(0);
        editor.frame(0.016);

        editor.onKey(GlEditor.Keys.F5, false, false, false);   // play: widgets are live
        editor.frame(0.016);
        com.hackli.guidesigner.render.MeteorPainter.NumberParts np = editor.numberPartsOf(number);
        if (np.minusW() <= 0 || np.plusW() <= 0 || np.sliderW() <= 0) {
            throw new IllegalStateException("selftest: number widget parts missing " + np);
        }
        // parts are document space, pointer events are screen space
        double rowMid = editor.worldToScreen(0, np.rowY() + np.rowH() / 2)[1];
        double minusX = editor.worldToScreen(np.minusX() + np.minusW() / 2, 0)[0];
        double plusX = editor.worldToScreen(np.plusX() + np.plusW() / 2, 0)[0];
        double boxX = editor.worldToScreen(np.boxX() + 5, 0)[0];

        double startValue = number.value;
        editor.onMouseDown(minusX, rowMid, 0);
        editor.onMouseUp(minusX, rowMid, 0);
        if (number.value != startValue - 1) {
            throw new IllegalStateException("selftest: number '-' did not step (" + number.value + ")");
        }
        editor.onMouseDown(plusX, rowMid, 0);
        editor.onMouseUp(plusX, rowMid, 0);
        editor.onMouseDown(plusX, rowMid, 0);
        editor.onMouseUp(plusX, rowMid, 0);
        if (number.value != startValue + 1) {
            throw new IllegalStateException("selftest: number '+' did not step (" + number.value + ")");
        }

        double nHandle = new com.hackli.guidesigner.render.MeteorTheme().handleSize();
        double nSliderX = editor.worldToScreen(
            np.sliderX() + nHandle / 2 + (np.sliderW() - nHandle) * 0.75, 0)[0];
        editor.onMouseDown(nSliderX, rowMid, 0);
        editor.onMouseUp(nSliderX, rowMid, 0);
        if (number.value < 73 || number.value > 77) {
            throw new IllegalStateException("selftest: number slider wrong (" + number.value + ")");
        }

        editor.onMouseDown(boxX, rowMid, 0);
        editor.onMouseUp(boxX, rowMid, 0);
        editor.onChar('7');
        editor.frame(0.016);
        if (!"7".equals(number.editText)) {
            throw new IllegalStateException("selftest: number buffer wrong (" + number.editText + ")");
        }
        editor.onKey(GlEditor.Keys.ENTER, false, false, false);
        editor.frame(0.016);
        if (number.value != 7 || number.editText != null) {
            throw new IllegalStateException("selftest: number typing did not commit (" + number.value + ")");
        }
        editor.onKey(GlEditor.Keys.F5, false, false, false);   // back to design
        editor.frame(0.016);
        editor.onKey(GlEditor.Keys.Z, true, false, false);
        editor.frame(0.016);
        editor.setModifiers(false, false, false);   // GLFW reports the release next frame
        number = doc.find(number.id);   // undo replaces the node graph with fresh objects

        // --- inspector: the NUMBER rows (value/min/max/step/integer/slider/buttons) ---
        editor.selectById(number.id);
        editor.frame(0.016);
        List<String> rows = editor.inspectorRowLabels();
        for (String expected : new String[]{"value", "min", "max", "step", "integer", "slider", "buttons"}) {
            if (!rows.contains(expected)) {
                throw new IllegalStateException("selftest: NUMBER inspector row missing: " + expected
                    + " in " + rows);
            }
        }
        // the "visible" check box (row 2) must be live through the real widget path
        boolean wasVisible = number.visible;
        double[] visRow = editor.inspectorRowRect(2);
        double[] inspRect = editor.inspectorRect();
        double visX = visRow[0] + visRow[3] / 2;
        double visY = visRow[1] + visRow[3] / 2;
        if (visY > inspRect[1] + 4 && visY < inspRect[1] + inspRect[3] - 4) {
            editor.onMouseDown(visX, visY, 0);
            editor.frame(0.016);
            editor.onMouseUp(visX, visY, 0);
            editor.frame(0.016);
            if (number.visible == wasVisible) {
                throw new IllegalStateException("selftest: inspector check box did not toggle");
            }
            number.visible = wasVisible;
            editor.onKey(GlEditor.Keys.Z, true, false, false);
            editor.frame(0.016);
            editor.setModifiers(false, false, false);
        }
        editor.selectById(number.id);
        editor.frame(0.016);

        // --- keybind: click arms it, the next key binds, Esc cancels ---
        editor.selectById("root");
        editor.addWidget(com.hackli.guidesigner.model.UiType.KEYBIND);
        com.hackli.guidesigner.model.UiNode bind = editor.selection().get(0);
        editor.frame(0.016);

        editor.onKey(GlEditor.Keys.F5, false, false, false);   // play: widgets are live
        editor.frame(0.016);
        double[] bindPoint = editor.worldToScreen(
            bind.renderX + bind.renderW / 2, bind.renderY + bind.renderH / 2);

        editor.onMouseDown(bindPoint[0], bindPoint[1], 0);
        editor.onMouseUp(bindPoint[0], bindPoint[1], 0);
        editor.frame(0.016);
        if (!bind.listening) {
            throw new IllegalStateException("selftest: clicking a keybind should arm it");
        }

        editor.onKey(GlEditor.Keys.K, false, false, false);
        editor.frame(0.016);
        if (bind.listening || bind.key != GlEditor.Keys.K) {
            throw new IllegalStateException("selftest: the pressed key should bind (key=" + bind.key
                + " listening=" + bind.listening + ")");
        }

        editor.onMouseDown(bindPoint[0], bindPoint[1], 0);
        editor.onMouseUp(bindPoint[0], bindPoint[1], 0);
        editor.onKey(GlEditor.Keys.ESCAPE, false, false, false);
        editor.frame(0.016);
        if (bind.listening) {
            throw new IllegalStateException("selftest: Esc should cancel listening");
        }
        if (bind.key != GlEditor.Keys.K) {
            throw new IllegalStateException("selftest: Esc must not change the binding");
        }

        editor.onKey(GlEditor.Keys.F5, false, false, false);   // back to design
        editor.frame(0.016);
        editor.onKey(GlEditor.Keys.Z, true, false, false);
        editor.frame(0.016);
        editor.setModifiers(false, false, false);
        bind = doc.find(bind.id);

        // --- tooltip: hover text is drawn on top, next to the pointer ---
        bind.tooltip = "Hold to fly";
        editor.selectById(bind.id);
        editor.frame(0.016);
        bind = doc.find(bind.id);
        double[] tipPoint = editor.worldToScreen(
            bind.renderX + bind.renderW / 2, bind.renderY + bind.renderH / 2);
        editor.onMouseMove(tipPoint[0], tipPoint[1]);
        editor.frame(0.016);
        editor.frame(0.016);   // the fade needs a second tick to become visible
        if (!"Hold to fly".equals(editor.activeTooltip())) {
            throw new IllegalStateException("selftest: hovering should show the tooltip, got "
                + editor.activeTooltip());
        }
        bind.tooltip = "";

        // --- settings row: label + Select (N selected) + item icon + reset ---
        editor.selectById("root");
        editor.addSettingsRow();
        com.hackli.guidesigner.model.UiNode settingsRow = editor.selection().get(0);
        editor.frame(0.016);
        if (settingsRow.children.size() != 4) {
            throw new IllegalStateException("selftest: a settings row should hold four cells, got "
                + settingsRow.children.size());
        }
        // The four cells must sit on one row and in order (that is the table's job).
        // The four cells must share one horizontal band and run left to right.
        // (Their tops differ on purpose: a cell aligned MIDDLE centres inside the
        // row, which is exactly what a reset button wants.)
        double rowTop = settingsRow.renderY;
        double rowBottom = settingsRow.renderY + settingsRow.renderH;
        for (com.hackli.guidesigner.model.UiNode cell : settingsRow.children) {
            double cellCentre = cell.renderY + cell.renderH / 2.0;
            if (cellCentre < rowTop || cellCentre > rowBottom) {
                throw new IllegalStateException("selftest: cell " + cell.id
                    + " is outside the row band (" + cellCentre + " not in " + rowTop + ".." + rowBottom + ")");
            }
        }
        if (settingsRow.children.get(0).renderX >= settingsRow.children.get(1).renderX) {
            throw new IllegalStateException("selftest: settings row cells are out of order");
        }
        // The Select label carries its count.
        com.hackli.guidesigner.model.UiNode selectNode = settingsRow.children.get(1);
        if (!com.hackli.guidesigner.render.MeteorPainter.selectLabel(selectNode)
            .equals("Select (4 selected)")) {
            throw new IllegalStateException("selftest: Select label wrong: "
                + com.hackli.guidesigner.render.MeteorPainter.selectLabel(selectNode));
        }
        editor.onKey(GlEditor.Keys.Z, true, false, false);
        editor.frame(0.016);
        editor.setModifiers(false, false, false);

        // --- align: add two buttons to the absolute root and align them ---
        editor.selectById("root");
        editor.addWidget(com.hackli.guidesigner.model.UiType.BUTTON);
        com.hackli.guidesigner.model.UiNode one = editor.selection().get(0);
        editor.addWidget(com.hackli.guidesigner.model.UiType.BUTTON);
        com.hackli.guidesigner.model.UiNode two = editor.selection().get(0);
        editor.selection().clear();
        editor.selection().add(one);
        editor.selection().add(two);
        editor.align(com.hackli.guidesigner.runtime.AlignTools.Align.LEFT);
        editor.frame(0.016);
        if (Math.abs(one.x - two.x) > 0.01) {
            throw new IllegalStateException("selftest: align left failed");
        }

        // --- resize: must grow once and then stay stable while the pointer rests ---
        editor.selectById(one.id);
        editor.frame(0.016);
        double w0 = one.width, h0 = one.height;
        double[] se = editor.handleScreenPos(one, 4);
        editor.onMouseDown(se[0], se[1], 0);
        editor.onMouseMove(se[0] + 60, se[1] + 40);
        editor.frame(0.016);
        double w1 = one.width, h1 = one.height;
        editor.onMouseMove(se[0] + 60, se[1] + 40);
        editor.frame(0.016);
        editor.onMouseMove(se[0] + 60, se[1] + 40);
        editor.frame(0.016);
        if (Math.abs(one.width - w1) > 0.01 || Math.abs(one.height - h1) > 0.01) {
            throw new IllegalStateException("selftest: resize keeps growing ("
                + w1 + "->" + one.width + ")");
        }
        if (w1 <= w0 || h1 <= h0) {
            throw new IllegalStateException("selftest: resize did not grow (" + w0 + "->" + w1 + ")");
        }
        editor.onMouseUp(se[0] + 60, se[1] + 40, 0);
        editor.frame(0.016);

        // --- templates + export ---
        editor.selectById(one.id);
        editor.saveTemplate("SelfTest");
        editor.insertTemplate("SelfTest");
        editor.export(0);
        editor.export(1);
        editor.export(2);
        editor.frame(0.016);

        // --- modal prompt ---
        editor.openNamePrompt("Template name", "Prompted", value ->
            System.out.println("[simulator] prompt committed: " + value));
        editor.frame(0.016);
        editor.confirmPrompt();
        editor.frame(0.016);

        // --- new document, then drag: a widget must follow the pointer exactly ---
        editor.newDocument();
        if (editor.mode() != GlEditor.Mode.DESIGN) {
            throw new IllegalStateException("selftest: expected design mode after new document");
        }
        // Reset the view first: earlier tests may have panned or zoomed, and the
        // drag assertion below compares screen pixels against document units.
        double[] resetView = editor.toolbarButtonRect("Reset view");
        if (resetView == null) throw new IllegalStateException("selftest: no Reset view button");
        editor.onMouseDown(resetView[0] + resetView[2] / 2, resetView[1] + resetView[3] / 2, 0);
        editor.frame(0.016);
        editor.onMouseUp(resetView[0] + resetView[2] / 2, resetView[1] + resetView[3] / 2, 0);
        editor.frame(0.016);
        doc.root.layout = com.hackli.guidesigner.model.LayoutMode.ABSOLUTE;
        editor.selectById("root");
        editor.addWidget(com.hackli.guidesigner.model.UiType.BUTTON);
        com.hackli.guidesigner.model.UiNode dragTarget = editor.selection().get(0);
        editor.frame(0.016);

        double dragX0 = dragTarget.x, dragY0 = dragTarget.y;
        double[] p0 = editor.worldToScreen(dragTarget.renderX + dragTarget.renderW / 2,
            dragTarget.renderY + dragTarget.renderH / 2);
        editor.onMouseMove(p0[0], p0[1]);
        editor.onMouseDown(p0[0], p0[1], 0);
        editor.onMouseMove(p0[0] - 80, p0[1] - 60);
        editor.frame(0.016);
        double expectedDx = -80 / editor.zoom(), expectedDy = -60 / editor.zoom();
        editor.onMouseUp(p0[0] - 80, p0[1] - 60, 0);
        editor.frame(0.016);
        if (Math.abs((dragTarget.x - dragX0) - expectedDx) > 8
            || Math.abs((dragTarget.y - dragY0) - expectedDy) > 8) {
            throw new IllegalStateException("selftest: drag does not follow the pointer (dx="
                + (dragTarget.x - dragX0) + " expected=" + expectedDx
                + ", dy=" + (dragTarget.y - dragY0) + " expected=" + expectedDy + ")");
        }

        // --- section: clicking the header collapses / expands it ---
        editor.selectById("root");
        editor.addWidget(com.hackli.guidesigner.model.UiType.CONTAINER,
            com.hackli.guidesigner.model.ContainerStyle.SECTION);
        com.hackli.guidesigner.model.UiNode section = editor.selection().get(0);
        editor.frame(0.016);
        double[] headerPoint = editor.worldToScreen(section.renderX + 10, section.renderY + 2);
        editor.onMouseDown(headerPoint[0], headerPoint[1], 0);
        editor.onMouseUp(headerPoint[0], headerPoint[1], 0);
        editor.frame(0.016);
        if (!section.collapsed) {
            throw new IllegalStateException("selftest: section header click did not collapse it");
        }
        editor.onMouseDown(headerPoint[0], headerPoint[1], 0);
        editor.onMouseUp(headerPoint[0], headerPoint[1], 0);
        editor.frame(0.016);
        if (section.collapsed) {
            throw new IllegalStateException("selftest: second click did not expand the section");
        }

        // --- toolbar menu: a real click opens it, picking an item runs it ---
        editor.frame(0.016);
        double[] exportBtn = editor.toolbarButtonRect("Export");
        if (exportBtn == null) throw new IllegalStateException("selftest: no Export button");
        editor.onMouseDown(exportBtn[0] + exportBtn[2] / 2, exportBtn[1] + exportBtn[3] / 2, 0);
        editor.frame(0.016);
        editor.onMouseUp(exportBtn[0] + exportBtn[2] / 2, exportBtn[1] + exportBtn[3] / 2, 0);
        editor.frame(0.016);
        if (!editor.menuOpen()) {
            throw new IllegalStateException("selftest: Export menu closed on the opening click");
        }
        double[] item0 = editor.menuItemRect(0);
        editor.onMouseDown(item0[0] + item0[2] / 2, item0[1] + item0[3] / 2, 0);
        editor.frame(0.016);
        editor.onMouseUp(item0[0] + item0[2] / 2, item0[1] + item0[3] / 2, 0);
        editor.frame(0.016);
        if (editor.menuOpen()) throw new IllegalStateException("selftest: Export menu stayed open");
        if (editor.logLines().stream().noneMatch(l -> l.contains("exported"))) {
            throw new IllegalStateException("selftest: Export menu item produced no file");
        }

        // Esc closes a menu (and must not be treated as "quit")
        editor.openMenu("align");
        editor.frame(0.016);
        if (!editor.menuOpen()) throw new IllegalStateException("selftest: align menu did not open");
        editor.onKey(GlEditor.Keys.ESCAPE, false, false, false);
        editor.frame(0.016);
        if (editor.menuOpen()) throw new IllegalStateException("selftest: Esc did not close the menu");

        gl.end();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        String json = doc.toJson();
        UiDocument roundTrip = UiDocument.fromJson(json);
        if (roundTrip == null || roundTrip.root == null) throw new IllegalStateException("selftest: JSON round trip failed");

        System.out.println("SELFTEST OK: " + nodes.size() + " nodes, " + editor.logLines().size()
            + " events, json " + json.length() + " bytes");
        for (String line : editor.logLines()) System.out.println("  " + line);
    }

    private static void collectNodes(com.hackli.guidesigner.model.UiNode node,
                                     java.util.List<com.hackli.guidesigner.model.UiNode> out) {
        if (node != null) out.add(node);
        if (node == null) return;
        for (com.hackli.guidesigner.model.UiNode child : node.children) collectNodes(child, out);
    }

    /**
     * GLFW reports the cursor in window (screen) coordinates while we draw in
     * framebuffer pixels, so on a scaled display the two differ.
     */
    private static double[] toPixels(long window, double x, double y) {
        int[] fbW = new int[1], fbH = new int[1], winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        glfwGetWindowSize(window, winW, winH);
        double sx = winW[0] <= 0 ? 1 : (double) fbW[0] / winW[0];
        double sy = winH[0] <= 0 ? 1 : (double) fbH[0] / winH[0];
        return new double[]{x * sx, y * sy};
    }

    private static void wireInput(long window, GlEditor editor) {
        glfwSetCursorPosCallback(window, (GLFWCursorPosCallbackI) (win, x, y) -> {
            double[] p = toPixels(win, x, y);
            editor.onMouseMove(p[0], p[1]);
        });

        glfwSetMouseButtonCallback(window, (GLFWMouseButtonCallbackI) (win, button, action, mods) -> {
            double[] mx = new double[1], my = new double[1];
            glfwGetCursorPos(win, mx, my);
            double[] p = toPixels(win, mx[0], my[0]);
            editor.setModifiers((mods & GLFW_MOD_CONTROL) != 0, (mods & GLFW_MOD_SHIFT) != 0,
                (mods & GLFW_MOD_ALT) != 0);
            if (action == GLFW_PRESS) editor.onMouseDown(p[0], p[1], button);
            else if (action == GLFW_RELEASE) editor.onMouseUp(p[0], p[1], button);
        });

        glfwSetScrollCallback(window, (GLFWScrollCallbackI) (win, dx, dy) -> editor.onScroll(dy));

        glfwSetKeyCallback(window, (GLFWKeyCallbackI) (win, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            boolean ctrl = (mods & GLFW_MOD_CONTROL) != 0 || glfwGetKey(win, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
            boolean shift = (mods & GLFW_MOD_SHIFT) != 0 || glfwGetKey(win, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS;
            boolean alt = (mods & GLFW_MOD_ALT) != 0 || glfwGetKey(win, GLFW_KEY_LEFT_ALT) == GLFW_PRESS;
            editor.setModifiers(ctrl, shift, alt);
            editor.onKey(key, ctrl, shift, alt);
        });

        glfwSetCharCallback(window, (GLFWCharCallbackI) (win, codepoint) -> editor.onChar(codepoint));
    }

    /** Writes a bottom-up RGBA buffer as a PNG (top-down). */
    private static void writePng(ByteBuffer pixels, int width, int height, Path path) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = ((height - 1 - y) * width + x) * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                int a = pixels.get(i + 3) & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        File file = path.toFile();
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(image, "png", file);
    }
}
