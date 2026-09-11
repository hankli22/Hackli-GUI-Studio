# Hackli GUI Studio

**A visual GUI designer for Meteor Client plugins — think SquareLine Studio, but inside the game.**

Hackli GUI Studio is a **Minecraft client mod** (Fabric + [Meteor Client](https://meteorclient.com/)) that lets you
design plugin GUIs **visually**: drag & drop widgets onto a canvas, tweak them in a property inspector, and then
**export ready-to-use Java code** with a generated **backend interface** for your plugin. Designs are stored as simple
JSON layout documents, and players can **re-arrange the layout live in-game and save it** via the built-in edit mode.

> **Licence:** Released under **GPLv3**. The generated **runtime** export links against this mod's
> `UiScreen`, so distributing it comes with the usual GPLv3 obligations; the **static** export is
> self-contained and normally does not. Details, and why this is not legal advice, in
> [docs/LICENSE-PLUGINS.md](docs/LICENSE-PLUGINS.md) — full text in [LICENSE](LICENSE).

```
┌-------------------------------------------------------------------------┐
│  New | Open [v] Open | Save | Del Up Down | [package] Export Runtime …  │  ← toolbar
├---------------------┬------------------------------------┬-------------┤
│  Widgets            │                                    │ Structure   │
│  [Panel]            │           WYSIWYG Canvas           │ ▸ Panel·root │
│  [Label]            │                                    │   ▸ Button…  │
│  [Button]           │   click to place / drag to move,   │ ----------- │
│  [Text Box]         │   resize via corner handles        │ Inspector   │
│  [Check Box]        │                                    │  ID, Text,  │
│  [Slider]           │                                    │  X, Y, W, H…│
│  [Dropdown]         │                                    │             │
├---------------------┴------------------------------------┴-------------┤
│  status bar                                                            │
└-------------------------------------------------------------------------┘
```

## Features

| | |
|---|---|
| 🖱️ **Drag & drop design** | Click a widget in the palette, click the canvas to place it. Drag to move, drag corner handles to resize, DEL to remove. Grid snapping included. |
| 🧩 **Widget types** | Panel, Label, Button, Text Box, Check Box, Slider, Dropdown, Separator, Number (Meteor's `WIntEdit`/`WDoubleEdit`), Texture, Keybind, Item, Entity, Select — 14 in total. |
| 🗂️ **Layout engine** | Two modes per container — `Flow` (auto stacked, Meteor-native sizing) and `Absolute` (anchor + offset, like SquareLine). |
| 📐 **WYSIWYG** | The canvas uses the exact same layout math as the runtime renderer. What you see is what players get. |
| ↩️ **Undo / Redo** | Ctrl+Z / Ctrl+Y everywhere (keybinds, toolbar). Typing and dragging merge into single steps, up to 100 levels. |
| 🎯 **Multi-select + Align** | Ctrl+click or drag a selection box; align left/center/right, top/middle/bottom and distribute horizontally/vertically. |
| 🧩 **Component templates** | Save any widget subtree as a reusable template and place it from the palette; templates persist with the project. |
| 💻 **Code export** | Three export modes, written to `config/hackli-gui-studio/export/` and (for Java) copied to the clipboard: |
| | **Runtime** — a `UiScreen` subclass + a backend handler class with stubs for every widget. Needs the mod installed at runtime; supports in-game layout editing. |
| | **Static (Meteor-native)** — a self-contained Meteor `WidgetScreen` using plain Meteor widgets. Zero dependency on this mod (or anything else). |
| | **JSON** — portable design data plus a JSON Schema (`hackli-gui-document.schema.json`, draft 2020-12) for validation and editor tooling. |
| 🎮 **In-game layout editing** | Players enable the `gui-layout-editor` module, then drag/resize/delete widgets of your GUI live. Layout is auto-saved and overrides the defaults. |
| 💾 **JSON documents** | Designs live in `config/hackli-gui-studio/projects/*.json`, player overrides in `config/hackli-gui-studio/layouts/*.json` — both plain, readable, diffable. |

## 中文简介

Hackli GUI Studio 是一个以 Minecraft 客户端 Mod 形式实现的 **Meteor Client 插件 GUI 可视化设计器**
（类比 SquareLine Studio，但直接运行在游戏内，基于 Meteor 的 ImGui 界面系统）：

- **拖拽放置控件**：左侧控件面板 → 点击拾取 → 点击画布放置；拖动可移动、拖拽角点可缩放，支持网格吸附。
- **自动生成代码与后端接口**：一键导出 `UiScreen` Java 类 + 带占位方法的回调 Handler（每个控件对应一个空方法，你只需填空）。
- **游戏内自由调整布局**：启用 `gui-layout-editor` 模块后，插件 GUI 可直接拖拽重排，松手自动保存到
  `config/hackli-gui-studio/layouts/`，下次打开自动加载（类似「config 界面自由改变 GUI 排布」）。
- **许可**：全项目 GPLv3；**Runtime 导出**（链接本 mod 的 `UiScreen`）分发时涉及 GPLv3 的下游义务，
  **Static 导出**自带全部代码、通常不涉及 —— 详见 [docs/LICENSE-PLUGINS.md](docs/LICENSE-PLUGINS.md)（非法律建议）。

## Requirements

- Minecraft **1.21.11**, Fabric Loader 0.19.3+
- [Meteor Client](https://meteorclient.com/) **1.21.11-SNAPSHOT** (addon API entrypoint `meteor`)
- Java 21 (the build targets `options.release = 21`; Gradle runs on the wrapper's own JVM)

## Building

```bash
git clone --depth 1 https://github.com/hankli22/hackli-gui-studio
cd hackli-gui-studio
./gradlew build
```

The mod jar lands in `build/libs/hackli-gui-studio-0.2.0.jar`. Put it in your `mods/` folder next to Meteor Client.

`build-all.cmd` does the same plus deploys the jar to a known instance folder;
`run-gl.cmd` builds and launches the OpenGL editor; `run-selftest.cmd` runs the
headless core check.

### Release package

`build-release.cmd` produces a self-contained distribution in `release/`:

```
hackli-gui-studio-0.2.0/
├── hackli-gui-studio-0.2.0.jar   the mod
├── editor/        standalone OpenGL editor (no Minecraft needed)
├── selftest/      headless core check
├── run-editor.cmd / run-selftest.cmd
├── examples/      design documents
├── assets/        generated item icon set (no Minecraft artwork)
├── data/          your projects/layouts/templates/exports (created on first use)
├── docs/          design, architecture, widget plan, licensing notes
├── THIRD-PARTY-NOTICES.md
└── README.txt
```

**Portable by design.** The editor keeps everything it creates in `data/` next to
`run-editor.cmd` and writes nothing to your home directory — copy or move the
whole folder (USB stick, another PC) and your projects come with it. Use
`run-editor.cmd <design> --data <dir>` to keep the data somewhere else.

plus a `-src.zip` (a clean source snapshot of the commit) and a single `.zip` with
everything. The package contains **no Minecraft artwork**: the item icons are a
generated set (see below), which keeps the download self-contained and the
licensing unambiguous.

> A newer Minecraft/Meteor version? Change the pinned versions in `gradle.properties` (and the
> `mappings(yarn)` dependency if the Yarn mapping changes) — follow the
> [Meteor addon template docs](https://github.com/MeteorDevelopment/meteor-addon-template).
> The *26.x* branch of Meteor Client (Minecraft 26.2) uses a slightly different GUI/input API and needs
> code adjustments (the current codebase targets the 1.21.11 API).

## Quick start

1. Launch Minecraft with Meteor Client + this mod.
2. Open the designer: **`.hackligui`** in chat (alias `.hgd`), or the **`gui-designer`** module in
   the *GUI Designer* category (bind a key for quick access).
3. Click **New** → click, e.g. *Button* in the palette → click on the canvas to place it.
4. Select the button and edit **ID** (e.g. `btn-start`) and **Text** in the inspector.
5. Click **Save** (design stored in `config/hackli-gui-studio/projects/`).
6. Click **Export Runtime** (or **Export Meteor**), with your package name filled in.
7. Grab the generated `.java` from `config/hackli-gui-studio/export/` (also on the clipboard) and drop it into
   your plugin.
8. In-game, enable the **`gui-layout-editor`** module, open your plugin GUI, and drag the widgets around — the player
   layout is saved automatically.

## Using the generated code

### Runtime export (recommended)

```java
// Generated file: MyGui.java
public class MyGui extends UiScreen {
    public MyGui() {
        super("My GUI", buildDocument(), new MyGuiHandler());
    }
    // ... buildDocument() contains the whole layout as readable builder calls ...
}

// Open it from somewhere in your plugin:
MinecraftClient.getInstance().setScreen(new MyGui());
```

The generated `MyGuiHandler` receives every widget event:

```java
@Override
public void onAction(String id, String event, String value) {
    switch (id) {
        case "btn-start": if (event.equals("click")) onBtnStart(); break;
        case "sld-size":  if (event.equals("release")) onSldSize(Double.parseDouble(value)); break;
    }
}

public void onBtnStart() { /* TODO */ }
public void onSldSize(double value) { /* TODO */ }
```

Requires `hackli-gui-studio` in `mods/` at runtime (add `implementation` on the mod jar or just document the
dependency). The runtime export links against this mod, so the GPLv3 obligations above apply to it.

### Static export

A single self-contained `WidgetScreen` built from plain Meteor widgets
(`theme.button(...)`, `theme.section(...)`, …). No dependency on this mod at runtime.
Absolute positions are flattened to flow layout (a warning comment marks the spots).

## In-game layout editing

- Enable the **`gui-layout-editor`** module (bind `keybind` in its settings).
- Open any GUI that extends `com.hackli.guidesigner.runtime.UiScreen`.
- **Drag** a widget to move it — snap grid configurable in the module settings.
- **Drag a corner handle** (teal square) to resize.
- **DEL** deletes the selected widget.
- Every change is saved to `config/hackli-gui-studio/layouts/<gui-name>.json` when the mouse is released and is
  re-applied next time the GUI opens.

Players changing the layout does not require GPLv3 (mere use); distribution of the plugin itself does.

## File layout

Two independent data roots, because the two things have different lifetimes:

```
# the mod: inside the game instance, where Fabric mods keep their files
config/hackli-gui-studio/
├── projects/   ← designs you are working on (JSON documents)
├── layouts/    ← player-saved runtime layout overrides
├── export/     ← generated Java sources
└── templates/  ← reusable widget subtrees

# the standalone editor: next to the application (portable)
data/
├── projects/  layouts/  export/  templates/
```

The editor is portable on purpose: `run-gl.cmd` / `run-editor.cmd` start it with
`-Dhackli.home=<their own folder>`, so its data sits beside the software instead of
in the user's home directory. Point it at the instance folder to share one project
list with the in-game designer:

```bat
run-gl.cmd --data "%APPDATA%\.minecraft\config\hackli-gui-studio"
```

## Earlier editors (archived 2026-09)

The Swing desktop editor and the browser editor are **no longer maintained**. The
OpenGL editor (`simulator-gl/`) is the supported design tool, and the in-game
designer covers everything else. The archived sources are kept in a private
working copy outside this repository and are not part of the build.

What survived is the desktop editor's headless self test, which checked the
document model, Meteor layout, painter geometry, exporters, undo, templates and
clipboard operations. It lives in `tools/selftest/` and runs without a window, so
the supported editor keeps that safety net:

```bat
run-selftest.cmd
```

## OpenGL "comet" editor + simulator (pixel-accurate)

`simulator-gl/` renders designs with its own **OpenGL 2D pipeline** that follows
Meteor's own drawing code. The constants and rules below were taken from the
1.21.11 Meteor Client sources (`MeteorGuiTheme`, `MeteorWidget`, `WMeteor*`), so
the result is the real thing, not an approximation:

| Meteor code | Reproduced as |
| --- | --- |
| `MeteorWidget.renderBackground` | background inset by `scale(2)` inside a `scale(2)` outline frame |
| `backgroundColor` / `outlineColor` triples | normal `20,20,20,200` / hovered `30,30,30,200` / pressed `40,40,40,200`, outline `0` / `10` / `20` |
| `WMeteorWindow` | flat `backgroundColor` body + accent `145,61,226` header of `pad + textHeight + pad` |
| `WMeteorButton` | state background, text centred on x at `y + pad` |
| `WMeteorCheckbox` | the widget rect is the background, the accent square grows to `(min(w,h) - scale(2)) / 1.75` |
| `WMeteorSlider` | `scale(3)` bar inset by half the handle, circular handle of `textHeight()`, handle `130,0,255` / `140,30,255` / `150,60,255` |
| `WMeteorTextBox` | outline + background, scissor inset by `scale(2)`, placeholder `255,255,255,20`, blinking cursor |
| `WMeteorHorizontalSeparator` | `scale(1)` line, `separatorEdges 225,225,225,150` -> `separatorCenter 255,255,255` |
| `GuiTheme.pad()` / `textHeight()` | `scale(6)` / `9 * scale` (Meteor's scale setting, default `0.75`) |

```bat
run-gl.cmd                                   :: live editor (ESC quits)
run-gl.cmd examples\welcome.json play        :: live editor in play mode
run-gl.cmd examples\welcome.json out.png     :: offscreen render to PNG
```

The live window opens **sized to your monitor** (92% of the work area, centred) and is a
**complete editor** rendered through that pipeline - everything the in-game designer can do
lives here:

* **palette** on the left: add any widget type, plus reusable **templates** (saved
  selections); they are stored under the project's `templates/` directory and
  reappear in the palette on the next start
* **tree** on the right: the full hierarchy, click / ctrl+click to select
* **inspector** below it: every property as a live widget - id, layout, anchors, x/y/w/h,
  text, placeholder, checked, value/min/max, dropdown options, text color, alignment,
  max length, input filter, handler
* click / ctrl+click / marquee to select, double-click to edit text
* drag inside a flow panel to **reorder**, alt+drag for **free placement**
* 8 resize handles with edge snapping and guides, arrow keys nudge
* **Ctrl+scroll** zooms the canvas (0.25x - 6x), scroll pans, middle-drag pans
* **UI -** / **UI +** resize the whole editor chrome (0.75x - 3x); it starts at your
  display's DPI scale, so 4K/high-scaling screens get readable text, and the
  preview zoom starts at the same scale
* `Space` toggles a check box, `[` / `]` nudge a slider value
* **Align** menu with all 8 align/distribute operations, `Ctrl+D` duplicate, `Del` remove,
  `Ctrl+Z`/`Ctrl+Y` undo/redo, `Ctrl+S` save
* **New / Open / Save / Save As** projects, and **Export** to runtime Java, static Meteor
  code or JSON + schema
* `F5` toggles **play mode**, where every widget behaves like the in-game click
  GUI (buttons fire, checkboxes animate, sliders drag/scroll, dropdowns open,
  text boxes take typing) and each callback is logged on screen and in the console

Useful flags: `--size 1600x900` (window size), `--scale 0.75` (Meteor's default GUI
scale), `--ui-scale 1.4` / `--ui-font-size 18` (editor chrome size), `--font <ttf>`,
`--font-size 12`, `--with-ui` (keep the editor chrome in an offscreen render),
`--select <id>`, `--play`, `--selftest` (headless check that drives selection, drag,
resize stability, reorder, the inspector fields, align, templates, export, the modal
prompt and every play-mode widget).

> Why not call Meteor's own `GuiRenderer` on the desktop? Its renderer is built on
> Minecraft's blaze3d layer (`MinecraftClient.getFramebuffer()`, `GpuTextureView`,
> `GpuSampler`), so reusing it would mean reimplementing Minecraft's rendering engine.
> The in-game designer canvas *does* use the real pipeline (`UiRenderer` + real widgets).

## Project structure

```
core/            Minecraft-independent: model, layout math, painters, exporters (shared)
simulator-gl/    OpenGL "comet" editor (live window + offscreen PNG)  ← supported editor
tools/selftest/  Headless self test for core (no window, no GL, no font file)
src/             The Minecraft mod (meteor addon)

src/main/java/com/hackli/guidesigner/
├── HackliGuiDesigner.java        ← Meteor addon entry point
├── modules/                      ← gui-designer + gui-layout-editor modules
├── commands/                     ← .hackligui command
├── designer/                     ← DesignerScreen + custom-drawn DesignerPage
└── runtime/                      ← UiScreen / UiRenderer / UiContainer / EditSession
                                   (the library plugins link against)

build/mc-assets/                 ← locally extracted vanilla textures (gitignored)
assets/icons/                    ← generated item icon set (committed, CC0-1.0)
```

Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Building and contributing: [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md).
Third-party components and licences: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

### Item icons (two sources, no bundled artwork)

The editors draw a 16×16 icon per item. There are two independent sources, and
the extracted one always wins:

**1. Generated icon set (committed, shipped).** `assets/icons/` holds
`icon-atlas.png` plus `icon-atlas.tsv`, which maps an item id to its cell
(`diamond` → column 12, row 0). Every icon is drawn from a hash of its own id —
colour, wash, bevel and one of eight glyphs — so it is identical on every machine
and contains none of the game's artwork. It is original work released as
**CC0-1.0** (see `assets/icons/LICENSE-icons.txt`). Regenerate it after a
Minecraft version bump:

```bash
./gradlew :core:genIconAtlas -PclientJar="<path to 1.21.11.jar>"
```

**2. Extracted game textures (local, never redistributed).** For pixel-accurate
design work, extract the real sprites from a client jar you own:

```bash
./gradlew :core:extractMcAssets -PclientJar="<path to 1.21.11.jar>"
```

The task walks the real chain
(`assets/minecraft/items/<id>.json` → `models/...` → parent chain → `layer0`) and
writes every item/block texture plus `item-textures.tsv` into the gitignored
`build/mc-assets/`. `com.hackli.guidesigner.assets.AssetStore` reads the
extracted index first and falls back to the generated atlas; `UiType.TEXTURE`,
`ITEM` and `ENTITY` nodes draw a readable placeholder when neither is available,
so every editor works with no assets at all.

## License

This mod is licensed under the **GNU General Public License v3.0**.

Generated code that links against this mod (the **runtime** export) is a derivative work and must be
distributed under GPLv3 with the full license text; the generated files carry a header stating this.
The **static** export contains only plain Meteor widgets and normally carries no such obligation.

- Full text: [LICENSE](LICENSE)
- Plugin obligation, detailed: [docs/LICENSE-PLUGINS.md](docs/LICENSE-PLUGINS.md)
