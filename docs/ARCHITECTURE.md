# 架构说明（Hackli GUI Studio）

> 面向贡献者与二次开发者：说明各模块的职责、绘制与布局的设计取舍、导出器的工作方式，
> 以及"改完之后怎么验证"。
> 控件移植的进度与计划见 [`PLAN-WIDGETS.md`](PLAN-WIDGETS.md)，构建与贡献约定见
> [`CONTRIBUTING.md`](CONTRIBUTING.md)，第三方声明见仓库根目录的
> [`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md)。

## 0. 命名约定

- **展示名**：Hackli GUI Studio。
- **Java 包名**：`com.hackli.guidesigner`（沿用早期名称，保持二进制与源码兼容，**不改**）。
- **资源目录 / mixin 配置 / mod id / 配置目录**：全部已统一为 `hackli-gui-studio`
  （`assets/hackli-gui-studio/`、`hackli-gui-studio.mixins.json`、`config/hackli-gui-studio/`）。
  旧版配置目录 `hackli-gui-designer` 会在启动时自动改名迁移。
- **数据位置**（两套根，生命周期不同）：
  - **游戏内 mod**：`config/hackli-gui-studio/`（Fabric 惯例，随实例走）；
  - **独立编辑器**：**便携** —— 数据放在程序同目录的 `data/`（启动脚本传 `-Dhackli.home=<脚本目录>`），
    不写用户主目录，整个目录拷走即带走全部设计稿；用 `--data <dir>` 或 `-Dhackli.home` 指到别处，
    也可以指向实例的配置目录以便两套编辑器共用一份工程列表。

## 1. 一句话概述

设计稿是一份**与 Minecraft 无关的 JSON 文档**（`UiDocument` → `UiNode` 树）。
同一棵树被三种方式消费：编辑器的像素级预览、游戏内的真实控件、以及代码导出器。
所有几何计算（布局数学、命中测试、控件内部几何）都在 `core/` 里，只有一份实现。

## 2. 模块划分

| 模块 | 目录 | 职责 | 关键依赖 |
| --- | --- | --- | --- |
| `core/` | `core/` | **与 Minecraft 完全无关**：文档模型、布局数学、彗星绘制规则、导出器、贴图索引 | 仅 Gson |
| `simulator-gl/` | `simulator-gl/` | **OpenGL 编辑器 / 模拟器**（独立窗口 + 离屏 PNG），当前唯一维护的独立编辑器 | `core` + LWJGL 3.3.6（glfw / opengl / stb） |
| `tools/selftest/` | `tools/selftest/` | **无头自检**：不需要窗口、GL 上下文或字体文件 | `core` |
| `src/` | 仓库根项目 | **游戏内 mod**：设计器界面、运行时库（插件链接的那部分）、模块与命令 | `core` + Minecraft 1.21.11 + Fabric Loader + Meteor Client |

```
                        ┌───────────────────────────────────────────┐
                        │  core/   （零 Minecraft 依赖）             │
                        │                                           │
                        │  model/    UiDocument · UiNode · UiType    │
                        │            ContainerStyle · LayoutMode     │
                        │            AnchorX · AnchorY · Undoer …    │
                        │  render/   MeteorLayout  ← 布局数学        │
                        │            MeteorTheme   ← 配色与度量      │
                        │            MeteorPainter ← 唯一一份绘制规则 │
                        │            MeteorCanvas  ← 后端接口        │
                        │            TextureSource ← 贴图来源接口    │
                        │  export/   Java · Meteor · Json (+Schema)  │
                        │  assets/   AssetStore · AssetExtractor     │
                        │  runtime/  UiLayout（早期布局）· EditorOps   │
                        └───────┬───────────────────────┬───────────┘
                                │                       │
        ┌───────────────────────┘                       └──────────────────────┐
        ▼                                                                      ▼
┌──────────────────────────────┐                          ┌────────────────────────────────┐
│ simulator-gl/                │                          │ src/  游戏内 mod                │
│  GlMeteorCanvas implements   │                          │  DesignerPage  自绘设计器界面    │
│      MeteorCanvas            │                          │  UiRenderer    → 真实 Meteor 控件│
│  DesignRenderer → painter    │                          │  UiContainer extends WContainer │
│  GlEditor（调色板/树/Inspector）│                         │  UiScreen extends WidgetScreen  │
│  GlSimulator（窗口 / 离屏）    │                          │  modules/ · commands/           │
│  run-gl.cmd                  │                          │  .hackligui（别名 .hgd）         │
└──────────────────────────────┘                          └────────────────────────────────┘
```

**依赖方向是单向的**：`simulator-gl` 与 `src` 都只依赖 `core`，`core` 不反向依赖任何一方。
因此 `core` 可以脱离 Minecraft 单独编译、单独测试、单独打包——这正是无头自检能存在的前提。

## 3. 唯一 painter：一份绘制规则，多个后端

### 3.1 为什么要"唯一"

编辑器与游戏内如果各自实现一遍控件画法，就会出现"预览好看、进游戏变形"这类问题，
而且每加一个控件都要写两遍、对齐两遍像素。因此项目把**彗星外观的绘制规则收敛成一份**：

- `core/render/MeteorPainter`：**唯一一份绘制规则**。它按 `UiType` 分支，
  把每个节点画到抽象的 `MeteorCanvas` 上，内部用 `MeteorTheme` 取配色与度量。
- `core/render/MeteorCanvas`：**后端接口**，只提供绘制原语——
  `quad`（实心矩形）、`quadGradientH`（水平渐变）、`circle`、`triangle`（由条带拼出的三角形）、
  `text` / `textWidth` / `ascent`、`pushClip` / `popClip`、`texture`（按句柄绘制贴图）。
  坐标一律是**文档像素**，原点在左上角，颜色是打包的 ARGB int。
- `core/render/MeteorTheme`：配色（三态背景/描边、强调色、滑块、分隔线、滚动条…）
  与度量（`scale(v)`、`pad() = scale(6)`、`textHeight() = 9 * scale`、`headerHeight()`）。

后端只需实现 `MeteorCanvas`（目前是 `simulator-gl` 的 `GlMeteorCanvas`），
**画法自动获得**，且与编辑器内部完全一致。历史上的 Java2D 后端也是同一个接口的实现。

### 3.2 绘制流程

```java
painter.setInteraction(hovered, pressed, focused, openDropdown); // 谁在被悬停/按下/聚焦
painter.setPointer(mouseX, mouseY);                              // 提示框跟手
painter.tick(dt, root);                                          // 动画进度（折叠、滚动、淡入）
UiNode hit = painter.paint(canvas, root, originX, originY);       // 布局 + 绘制，返回命中的节点
```

`paint()` 内部先调用 `MeteorLayout.layoutTree(...)` 把每个节点的**计算后矩形**写回节点，
再逐个绘制。因此"画出来的位置"与"命中测试用的位置"永远是同一份数据。

### 3.3 几何只有一份：绘制与命中测试同源

组合控件最容易出的错是"看得到、点不到"。项目对这类控件的做法是**把几何抽成一份**：
例如数值控件（`UiType.NUMBER` = 文本框 + `−` + `+` + 滑块）由
`MeteorPainter.numberParts(canvas, node)` 返回一个 `NumberParts` 记录，
绘制用它，命中测试（`inBox` / `inMinus` / `inPlus` / `inSlider`）也用它。
表格的列宽（`MeteorLayout.tableColumns`）与 `Select` 行的标签文案同样由共享函数产出。
新增组合控件时请沿用这个模式。

### 3.4 三条渲染路径

| 渲染路径 | 位置 | 依据的画法 |
| --- | --- | --- |
| GL 编辑器的设计视图 / 播放视图 / 离屏 PNG | `simulator-gl`：`DesignRenderer` + `GlMeteorCanvas` | `MeteorPainter`（唯一一份规则） |
| 游戏内设计器界面 | `src`：`DesignerPage`（用 `GuiRenderer` 原语自绘） | 真实 Meteor 渲染器 |
| 游戏内运行时（插件 GUI） | `src`：`UiRenderer` + `UiContainer` | 真实 Meteor 控件（`theme.button(...)` 等） |

编辑器这一侧的三处输出（编辑、试玩、出图）共用同一份 `MeteorPainter`，
所以"编辑器里看到什么，导出的设计就是什么"。
游戏内一侧直接用 Meteor 自己的控件与渲染器，天然与 Meteor 一致。
两条路线的几何都来自 `core`，这也是把布局数学放进 `core` 的原因。

## 4. 数据模型

```
UiDocument { name, root }
UiNode {
    id, type, layoutMode, visible,
    anchorX, anchorY, x, y,                  // 绝对定位：锚点 + 偏移
    width, height,                           // 0 或负值 = 自动尺寸
    text, placeholder, checked, value, min, max, step, options, selected,
    texture, key, tooltip, inputFilter,
    cell { padTop/Right/Bottom/Left, expandX, alignX, alignY, minWidth, group, row, column },
    style (ContainerStyle), orientation, collapsed, maxHeight,
    children
}
```

- **`UiType`**（14 种）：`CONTAINER`、`LABEL`、`BUTTON`、`TEXTBOX`、`CHECKBOX`、`SLIDER`、
  `DROPDOWN`、`SEPARATOR`、`NUMBER`、`TEXTURE`、`KEYBIND`、`ITEM`、`ENTITY`、`SELECT`。
- **`ContainerStyle`**（容器样式）：`PANEL`（窗口）/ `SECTION`（可折叠分组）/
  `VIEW`（可滚动区）/ `TABLE`（按列对齐）。
- **`LayoutMode`**：`FLOW`（流式）/ `ABSOLUTE`（绝对定位）。
- **序列化**：Gson 美化输出，人可读、可 diff。新增字段一律给默认值，
  旧文档缺字段时按默认值解析，保证向后兼容。

## 5. 布局引擎

### 5.1 两种模式

- **`FLOW`（流式）**：容器的子节点按 `orientation`（垂直/水平）依次排布，尺寸自算。
  由 `core/render/MeteorLayout` 实现，语义与 Meteor 的
  `WVerticalList` / `WHorizontalList` / `Cell` 一致：
  - 垂直列表：`spacing = scale(3)`，**每个** cell 前都加一次 spacing，
    再叠加 `padTop` / `padBottom`；容器宽度取"最宽 cell"。
  - 水平列表：`x += spacing + padLeft + 宽度 + padRight`；
    `expandX` 的 cell 分摊剩余宽度。
  - 常量：`SPACING = 3`、`WINDOW_PADDING = 8`、`SECTION_PADDING = 6`、`TABLE_SPACING = 3`。
- **`ABSOLUTE`（绝对定位）**：子节点放在**锚点 + 偏移**处。
  锚点由 `AnchorX`（Left/Center/Right）× `AnchorY`（Top/Middle/Bottom）决定，
  偏移量是 `x` / `y`，因此窗口缩放时控件仍贴住预期的角或边。
  这类布局在编辑器里用拖拽/8 个角手柄/边缘吸附编辑。

新增 `ContainerStyle` 或 `UiType` 时，先问一句："这需要新的布局语义，还是现有语义的组合？"
现有的流式 + 锚点两套已经覆盖了全部已实现控件。

### 5.2 容器样式

- **`SECTION`**：第一个 cell 是标题栏（分隔线 + 右侧三角），
  折叠动画是对高度做插值（`animProgress` 0..1），动画期间对内容做裁剪。
- **`VIEW`**：内容高度超过 `maxHeight` 时可滚动，
  渲染时先 `pushClip` 再对内容整体位移，最后画滚动条；
  滚轮滚动量按 `scale(40)` 缩放并做插值逼近。
- **`TABLE`**：列宽 = 同列最宽、行高 = 行内最高；
  `Cell.row` 标在**每行最后一格**（对应 Meteor 的 `table.row()`），
  `Cell.group` 让跨表的同名列共享宽度，`expandX` 分摊余宽。

### 5.3 命中测试

`MeteorLayout.hitTest` 按**绘制顺序从后往前**取最上层命中的节点，
并且**不因父容器矩形而裁剪子节点**——溢出的控件（例如弹出列表、被折叠动画遮住的边缘）
仍然可点。改动命中逻辑时不要退回"先判父矩形"的写法。

## 6. 导出器

`core/export/` 下三个导出器，对应编辑器的三种导出模式：

| 模式 | 类 | 产物 | 运行时依赖 |
| --- | --- | --- | --- |
| **Runtime** | `JavaExporter.generate(doc, pkg)` | `UiScreen` 子类（布局全部展开成可读的构建器调用）+ 一个 handler 类（每个控件一个空方法 + 按 id 分派的 `onAction`） | 需要本 mod 在 `mods/` 中 |
| **Static Meteor** | `MeteorExporter.generate(doc, pkg)` | 自包含的 `WidgetScreen`，用普通 Meteor 控件拼出 | **零依赖**（不依赖本 mod） |
| **JSON + Schema** | `JsonExporter.generate(doc)` / `export(doc)` | 设计 JSON + `hackli-gui-document.schema.json`（JSON Schema draft 2020-12） | 无 |

- 生成文件的头部注释会写明该文件由本工具生成及其许可证，请保留。
- **静态导出的限制**：`ABSOLUTE` 布局在普通 Meteor 控件里无法表示，
  会被展平为流式并在对应位置写出警告注释；
  个别属性（例如按钮的文本对齐）同样不可表示，也会留注释。
- 生成产物写到数据根的 `export/`（编辑器是程序同目录的 `data/export/`，游戏内是
  `config/hackli-gui-studio/export/`）；
  导出器通过查询物品索引判断某个 `Items.常量` 是否真实存在，
  因此生成的代码不会引用不存在的常量。

### 新增一个控件要同步的地方

`UiType` 的新枚举值会**打断所有穷尽 switch**（`DesignerPage` 的绘制分支、`UiRenderer.build`、
`JavaExporter` 的节点工厂等）——编译器会直接报错，按提示补齐 `case` 即可，
这属于刻意保留的编译期提醒。完整清单见
[`PLAN-WIDGETS.md` §4](PLAN-WIDGETS.md#4-每个控件的统一落地清单)。

## 7. 素材与图标

图标有两个**互相独立**的来源，由 `core/assets/AssetStore` 统一查找，
**本地提取的真实贴图优先**，其次是生成图标集，都没有时画占位：

1. **本地提取的真实贴图**（仅本地使用，不分发）
   - `gradle :core:extractMcAssets -PclientJar="<1.21.11.jar>"` 从**使用者自己的**
     Minecraft 客户端 jar 提取到 `build/mc-assets/`（**已 gitignore**）：
     扁平化的物品/方块贴图 + `item-textures.tsv` 索引，把物品 id 映射到游戏真正使用的那张贴图
     （`diamond` → `item/diamond`，`stone` → `block/stone`）。
   - 这一份**不会**进仓库，也**不会**进发行包。
2. **生成图标集**（随仓库提交、随发行包分发）
   - `assets/icons/icon-atlas.png` + `icon-atlas.tsv`：每个原版物品 id 一格 16×16 图标，
     由 id 的**稳定哈希**生成配色与字形，不是 Mojang 美术资源，也刻意不模仿原版像素。
   - 重新生成：`gradle :core:genIconAtlas -PclientJar="<1.21.11.jar>"`（只需 jar 里的物品 id 列表）。
   - 本项目的原创产物，以 **CC0-1.0** 发布，见 `assets/icons/LICENSE-icons.txt`。

两个来源都取不到时，painter 画一个带短标签的占位块（`MeteorTheme.TEXTURE_MISSING`），
因此全新检出、没跑过任何提取步骤也能正常打开设计、跑自检。

细节与命令见 [`CONTRIBUTING.md`](CONTRIBUTING.md) 与
[`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md)。

## 8. 验证方式

改完代码必须跑两套自检，**全绿才算完成**。

### 8.1 无头 core 自检

```bat
run-selftest.cmd
```

- 入口：`com.hackli.guidesigner.testing.CoreSelfTest`，以 `-Djava.awt.headless=true` 运行，
  不需要窗口、GL 上下文或字体文件（文字度量由确定性的 `TextMetrics` 提供）。
- **160 项断言**（运行时打印 `SELF-TEST OK (<N> checks)`，以实际输出为准），覆盖：
  文档模型与 JSON 往返、三种导出器（含导出字符串断言）、
  撤销与对齐、模板、剪贴板、`MeteorLayout` 的垂直/水平/Cell 语义、
  `SECTION` / `VIEW` / `TABLE` 三种容器样式、数值控件的几何与取值数学、
  按键绑定、悬停提示、物品/实体/贴图控件、图标图集的生成、素材索引与容器样式导出。

### 8.2 无头 GL 自检

```bat
java -cp simulator-gl\build\libs\simulator-gl-0.2.0-all.jar ^
     com.hackli.guidesigner.gl.GlSimulator examples\welcome.json --selftest
```

- 用隐藏窗口（`GLFW_VISIBLE=false`）+ 离屏帧缓冲驱动**真实编辑器实例**，
  所以它验证的是"编辑器真的能这样用"，而不只是纯函数：
  命中测试选中预期节点、F5 在设计与试玩之间切换、工具栏/调色板/Inspector 行、
  拖动与缩放稳定性、数值控件的 `−`/`+`/滑块/键入提交、按键绑定监听与 Esc 取消、
  悬停提示、设置行四格与顺序、`Select` 标签、对齐、模板存取、导出菜单产出文件、
  折叠分组的展开/收起、菜单的打开与 Esc 关闭。
- 失败时直接抛出带上下文的 `IllegalStateException`。

两个自检都是**无窗口后台运行**的，不会抢焦点——请保持这一点。

### 8.3 出图对比

```bat
java -cp simulator-gl\build\libs\simulator-gl-0.2.0-all.jar com.hackli.guidesigner.gl.GlSimulator ^
     --render examples\welcome.json out.png --zoom 2 --dump-layout
```

`--dump-layout` 会打印每个节点**计算后**的矩形（含 cell 标记），
排查布局问题时比看像素可靠得多。`--stress` 可用于压力场景。

## 9. 关键文件速查

```
core/render/MeteorTheme.java     配色与度量常量（三态色、滑块、滚动条、标题行高…）
core/render/MeteorPainter.java   唯一一份绘制规则 + 组合控件几何 + 贴图回退
core/render/MeteorLayout.java    Cell 布局 + 命中测试 + 滚动/折叠辅助
core/render/MeteorCanvas.java    后端接口（quad / 渐变 / 圆 / 三角 / 文字 / 裁剪 / 贴图）
core/render/TextureSource.java   贴图来源接口（每个后端一个实现）
core/model/UiNode.java           文档节点（cell / style / orientation / collapsed / tooltip …）
core/model/UiType.java           14 种控件类型
core/export/{Java,Meteor,Json}Exporter.java
core/assets/AssetStore.java      贴图查找：本地提取的 build/mc-assets 优先，回退到 assets/icons 图集
core/assets/AssetExtractor.java  客户端 jar → 本地贴图 + 索引；--icons-only 生成图标图集
simulator-gl/…/GlEditor.java     OpenGL 编辑器（调色板 / 结构树 / Inspector）
simulator-gl/…/DesignRenderer.java  painter 适配层（把 canvas 与几何暴露给命中测试）
simulator-gl/…/GlMeteorCanvas.java  唯一在维护的 MeteorCanvas 实现
tools/selftest/…/CoreSelfTest.java  无头自检（当前 160 项，以打印的 checks 数为准）
src/…/runtime/{UiRenderer,UiContainer,UiScreen}.java  游戏内真控件与插件基类
docs/PLAN-WIDGETS.md             控件移植计划与进度
docs/CONTRIBUTING.md             构建、自检与贡献约定
```

## 10. 设计取舍与已知技术债

### 10.1 为什么只有一条独立编辑器维护线

历史上项目并行维护过三个前端：Swing 桌面编辑器、浏览器编辑器、OpenGL 编辑器，
它们共用同一份 `core`。多前端意味着每加一个控件都要同步若干套 Inspector、调色板与
交互代码，而收益只是"换个壳"。因此：

- **保留并长期维护**：`simulator-gl/`（OpenGL 编辑器）与 `src/`（游戏内设计器）。
  GL 版具备 DPI 感知、真实像素对齐，并且与游戏内共享 `core` 的全部几何；
- **停止维护并移出仓库**：Swing 桌面版与浏览器版。
  它们的功能被子集/超集覆盖，代码仍留在版本历史中（加入与移除都各有一个里程碑提交），
  需要参考时可以按历史取回；
- **从桌面版的遗产里保留了最有价值的部分**：那套不依赖窗口的自检
  （文档模型、布局、painter 几何、导出器、撤销、模板、剪贴板），
  它就是今天的 `tools/selftest/`——回归网比界面本身更值钱。

### 10.2 已知技术债

| 项 | 现状 | 影响 |
| --- | --- | --- |
| 两套布局 | 游戏内运行时（`UiContainer`）仍使用早期的 `core/runtime/UiLayout`（`PAD = 8`、`GAP = 6`、`TITLE_H = 21`）；`MeteorLayout`（`spacing = scale(3)`）用于 painter 与 GL 编辑器 | 编辑器预览与游戏内渲染的间距存在细微差异，属于待收尾项；`UiLayout` 保留为兼容层 |
| 绝对定位的静态导出 | `ABSOLUTE` 在静态导出中展平为流式 | 生成的代码会带警告注释，布局与该模式下不可完全还原 |
| 文档体积 | Gson 会写出所有非 transient 字段 | 新增字段会让 JSON 变大；如在意可加自定义序列化器只写非默认值 |
| 新增 `UiType` | 会打断所有穷尽 switch | 编译器报错、按提示补 `case`，属刻意设计 |

### 10.3 容易踩的坑（已固化在代码与自检里）

- **painter 的状态是显式注入的**：悬停、按下、聚焦、指针位置、动画时间都必须由调用方喂给它。
  漏喂一个状态的表现是"画得出来但没反应、也不报错"。新增状态时，请同时检查所有调用点
  （编辑器的鼠标移动、按下/松开、拖动过程、试玩模式）。
- **贴图路径拼接前要挡掉非法字符**：物品 id 里带 `:` 时直接拼进路径会在 Windows 上抛
  `InvalidPathException`，因此索引查找与路径拼接都先做规范化。
- **撤销 / 打开文档 / 新建文档会整树替换**：这些操作会让此前的节点引用变成孤儿。
  改完节点后要么重新按 `id` 查找，要么推迟取引用。自检里专门覆盖了这条。
- **自检必须保持无窗口**：core 自检用 `-Djava.awt.headless=true`，
  GL 自检隐藏 GLFW 窗口。不要改成可见窗口。
- **GL 自检里按过的修饰键要复位**：真实 GLFW 要到下一帧才报告松键，
  否则后续点击会被当成"加选"。
- **导出/解析后不要沿用旧对象**：JSON 往返会产生新对象树，理由同上。

## 11. 相关文档

- 控件移植计划与进度：[`PLAN-WIDGETS.md`](PLAN-WIDGETS.md)
- 构建、自检与贡献约定：[`CONTRIBUTING.md`](CONTRIBUTING.md)
- 早期设计笔记：[`DESIGN.md`](DESIGN.md)
- 使用本工具生成的插件需要注意的许可证问题：[`LICENSE-PLUGINS.md`](LICENSE-PLUGINS.md)
- 第三方声明（Meteor / Minecraft / 各依赖）：[`../THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md)
