# 控件移植计划（第一批 + 第二批）

> 依据：Meteor Client 1.21.11 分支上各控件的渲染与布局规则整理而成。
> 下面每条的"彗星实现"指的是 Meteor 侧对应控件的配色常量、几何规则与排布顺序，
> 用于对齐外观；本项目的实现全部是自行编写的独立实现（见 [`ARCHITECTURE.md`](ARCHITECTURE.md)
> 与 [`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md)）。

## 0. 现状盘点

彗星 GUI 一共 **33 个具体控件**（+3 抽象基类 `WWidget`/`WPressable`/`WRoot`）。

我们目前实现 **8 个**（`core/.../model/UiType`）：

| 彗星 | 我们 | 画法 | 运行时 | 导出 |
| --- | --- | --- | --- | --- |
| `WLabel` | `LABEL` | ✅ | ✅ | ✅ |
| `WButton` | `BUTTON` | ✅ | ✅ | ✅ |
| `WCheckbox` | `CHECKBOX` | ✅ | ✅ | ✅ |
| `WTextBox` | `TEXTBOX` | ✅ | ✅ | ✅ |
| `WSlider` | `SLIDER` | ✅ | ✅ | ✅ |
| `WDropdown` | `DROPDOWN` | ✅ | ✅ | ✅ |
| `WHorizontalSeparator` | `SEPARATOR` | ✅ | ✅ | ✅ |
| `WContainer`/`WVerticalList` | `CONTAINER` | ✅ | ✅ | ✅ |

覆盖率 8/33 ≈ 24%，但都是最常用的。剩余 25 个按批次排。

> 上表是**计划制定时的基线**（8 个）；当前实现进度见 §7。

---

## 1. 基础设施：把布局换成彗星的 Cell 模型（M0）

**这是两批控件的共同前提。** 彗星的 `WSection`、`WView`、`WIntEdit`、`WTable`
全是"容器 + Cell 排版"的组合，我们现在的 `UiLayout` 只有"垂直流式 + 绝对定位"，
撑不住。

### 1.1 Meteor 的布局语义（`WVerticalList` / `WHorizontalList` / `Cell`）

`Cell` 的字段：`padTop/Right/Bottom/Left`、`marginTop`、`expandWidgetX/Y`、
`expandCellX`、`alignX/alignY`（默认 Left/Top）、`minWidth`、`group`；
所有 pad 都乘 `theme.scale()`。

**垂直列表**（`WVerticalList`，`spacing = scale(3)`）：

```
size:   width  = max(cell.padLeft + w + cell.padRight)
        height += spacing + cell.padTop + h + cell.padBottom     // 每个 cell 都加 spacing
layout: y 从容器 y 开始
        y += spacing + cell.padTop
        cell.x = x + cell.padLeft
        cell.width = width - widthRemove - padLeft - padRight
        cell.height = widget.height
        alignWidget(); y += cell.height + cell.padBottom
```

**水平列表**（`WHorizontalList`，`spacing = scale(3)`）：

```
size:   width += spacing + padLeft + w + padRight
        height = max(padTop + h + padBottom)
        fillXCount = 有 expandCellX 的 cell 数量
        calculatedWidth = width
layout: extra = (width - calculatedWidth) / fillXCount        // 分摊给 expandCellX
        每个 cell: x += spacing + padLeft; cell.x = x; ... 带 expandCellX 的再 += extra
```

**`Cell.alignWidget()`**：`expandWidgetX` → 直接铺满 cell；否则按 `alignX`
（Left/Center/Right）对齐，Y 同理。

### 1.2 我们的落点

在 `core/render` 新增 `MeteorLayout`（保留 `UiLayout` 作为薄封装，避免一次性大改）：

1. `UiNode` 增加可选 cell 字段（JSON 里省略时用默认值，保证旧文档可读）：
   `padTop/padRight/padBottom/padLeft`、`expandX`、`alignX/alignY`、`minWidth`、`group`。
2. `MeteorLayout.layoutTree` 实现上面两套算法 + `alignWidget`。
3. 容器增加 `style` 属性：`PANEL`（现在的 WWindow 风格）/ `SECTION` / `VIEW` /
   `LIST`（水平）。旧文档默认 `PANEL`，行为不变。
4. `spacing` 从我们的 `GAP=6` 改成彗星的 `scale(3)`；`PAD` 不再固定 8，改为
   `theme.pad()`（=scale(6)）—— 这一步会让现有设计看起来更紧凑，需要视觉复核，
   必要时给一个"兼容间距"开关。

**验收 M0**：现有 `examples/welcome.json` 在 `simulator-gl/` 编辑器里的渲染与间距/边距
符合 Meteor 的布局规则（用 `--render` 出图对比）；两个自检全绿。

---

## 2. 第一批（6 个控件，覆盖真实插件配置界面）

### 2.1 `WSection` — 可折叠分组

**彗星实现**（`WSection` + `WMeteorSection` + `WMeteorHeader`）：
- `WSection extends WVerticalList`，第一个 cell 是 header（`expandX()`），第二个是
  可选的 `headerWidget`（`padHorizontal(6)`）。
- `animProgress` 每 tick `+= (expanded?1:-1)*delta*14`，钳制 0..1（和复选框一样）。
- `forcedHeight = header.height + (actualHeight - header.height) * animProgress`
  → 折叠动画就是"高度插值"。
- `render` 在动画期间 `scissorStart(x, y + header.height, width, height - header.height)`。
- 标题栏：`WHorizontalSeparator(title)`（分隔线 + 缺口文字）+ 右侧三角
  （`WTriangle.rotation = -90 * animProgress`）。

**我们的落点**：
- 模型：`CONTAINER.style = SECTION` + `collapsed`（bool）+ 复用 `text` 作标题。
- 画法：`MeteorPainter` 新增 SECTION 分支（分隔线用已有的渐变线，三角用两个四边形拼）。
- 运行时：`UiRenderer` 用真 `theme.section(title, expanded)`。
- 导出：`theme.section("Title", true)`。
- Inspector：`style` 下拉 + `collapsed` 勾选。
- 编辑器：点击标题栏折叠/展开（带动画），折叠后子控件不参与命中测试。

### 2.2 `WView` — 滚动区

**彗星实现**（`WView` + `WMeteorView`）：
- `maxHeight` 默认 `窗口高 - scale(128)`；`height > maxHeight` → `canScroll=true`，
  `actualHeight=height`，`height=maxHeight`，`widthRemove = handleWidth()*2` 并加到 width。
- `onCalculateWidgetPositions`：先做垂直列表排版，再
  `scroll = clamp(scroll, 0, actualHeight - height)`，`moveCells(0, -scroll)`。
- 滚轮：`targetScroll -= round(amount * scale(40))`，渲染时插值逼近。
- `render`：`canScroll` 时 `scissorStart(x, y, width, height)` 包住列表，再画滚动条。
- 滚动条：`handleWidth = scale(6)`，`handleHeight = (height/actualHeight)*height`，
  `handleX = x + width - handleWidth`，`handleY` 按 `scroll/(actualHeight-height)` 比例，
  颜色 `scrollbarColor`（30/40/50，α200）。

**我们的落点**：
- 模型：`CONTAINER.style = VIEW` + `maxHeight`。
- 画法：`MeteorPainter` 按 clip + 滚动条；滚动偏移用 `UiNode` 的 transient 字段。
- 运行时：`theme.view()` + `maxHeight`。
- `simulator-gl/` 编辑器与游戏内设计器：鼠标在容器内滚轮即滚动（预览）；编辑模式下滚轮仍平移画布。
- Inspector：`style` + `maxHeight`。

### 2.3 水平排列（`WHorizontalList`）

- 模型：`CONTAINER.style = LIST` + `orientation`（VERTICAL/HORIZONTAL）。
- 画法/运行时/导出：直接用 `M0` 的水平算法 / `WHorizontalList`。
- Inspector：`orientation` 下拉；子节点可勾选 `expandX`（分摊剩余宽度）。

### 2.4 `WIntEdit` / `WDoubleEdit` — 数值编辑

**彗星实现**（`WIntEdit` 的构建顺序）：`WHorizontalList`，依次
1. `textBox(value, filter).minWidth(75)`
2. 若 `!noSlider`：`button("-")`、`button("+")`
3. `slider(value, sliderMin, sliderMax).minWidth(small?125:200).centerY().expandX()`

文本失焦解析、滑块拖动同步文本；`action` / `actionOnRelease` 回调。

**我们的落点**：
- 新 `UiType.NUMBER`，字段：`integer`、`min/max/step`、`showSlider`、`showButtons`。
- 画法：`MeteorPainter` 组合绘制（文本框 + 两个按钮 + 滑块），几何沿用上面的 minWidth 规则。
- 运行时：`theme.intEdit(...)` / `theme.doubleEdit(...)`。
- 导出：同上；`inputFilter` 自动设为 INT/DECIMAL。
- 编辑器（试玩模式）：可拖动滑块、点 +/-、直接输入。

**实现要点（已完成）**：四个部件（文本框 / − / + / 滑块）的几何只有一份 ——
`MeteorPainter.numberParts(canvas, node)` 返回 `NumberParts` 记录，画法和命中测试都用它，
所以"点到的一定是画出来的"。文本框输入用 `UiNode.editText`（transient 缓冲区）：
`MeteorPainter.displayValue()` 优先显示它，Enter 解析并钳制到 `min..max`，Esc 取消。
整数控件只收数字，`double` 控件多收一个小数点。

### 2.5 `WKeybind` — 按键绑定

**彗星实现**：`WKeybind` 内部就是一个 `WButton`；点击后 `listening=true`，
按钮文字显示 "..."，随后 `onAction` 捕获按键/鼠标，写入 `Keybind`，刷新文字；
右键清除。

**我们的落点**：
- 新 `UiType.KEYBIND`，字段 `key`（字符串，如 `key.keyboard.f`、`key.mouse.left`）。
- 画法：按钮样式（沿用 BUTTON）。
- 运行时：`theme.keybind(Keybind.fromKey(...))`。
- 导出：需要 `Keybind` 类型 —— 导出代码里用 `Keybind.none()` + 注释说明，
  或生成 `keybind(Keybind.fromKey(GLFW.GLFW_KEY_F))`。
- 编辑器（试玩模式）：点击进入监听，下一次按键即绑定（Esc 取消）。
- 注意：不链接 GLFW 的后端（独立编辑器）用字符串键名映射表。

### 2.6 `WTooltip` — 悬停提示

**彗星实现**：`WTooltip` 是挂在 root 上的特殊 widget；悬停时按鼠标位置定位，
背景 = `backgroundColor`（单色 quad，**无描边**），文本用 `textColor`。

**我们的落点**：
- 模型：任意 `UiNode` 增加 `tooltip`（字符串）。
- 画法：`MeteorPainter` 在 hover 时最后绘制（避免被遮挡），自动避免超出屏幕边缘。
- 运行时：`widget.tooltip = "..."`。
- Inspector：所有类型都显示 `tooltip` 一行。

---

## 3. 第二批（剩余 19 个）

按实现成本分三组，**建议按组推进**（下表为计划原文，实际完成情况见 §7；
`WTable` / `WTexture` / `WItem` 已完成）：

### A 组：靠第一批基础设施，几乎零新逻辑（5 个）

| 控件 | 彗星实现要点 | 我们的做法 |
| --- | --- | --- |
| `WVerticalSeparator` | `scale(1)` 竖线，上下渐隐（`separatorEdges`→`center`→`edges`） | `SEPARATOR.vertical = true` |
| `WQuad` | 纯色矩形，`color` 可配 | `UiType.QUAD` + 颜色属性 |
| `WMultiLabel` | 多行文本，按 `maxWidth` 换行 | `LABEL.multiLine = true` + `maxWidth` |
| `WTriangle` | 三角形，`rotation` 驱动（section 用） | 内部绘制（作为 SECTION 的一部分） |
| `WFavorite` | 星标按钮，`checked` 时用 `favoriteColor`(250,215,0) | `BUTTON.variant = FAVORITE` |

### B 组：简单新类型（4 个）

| 控件 | 彗星实现要点 | 我们的做法 |
| --- | --- | --- |
| `WPlus` / `WMinus` | 按钮 + `+`/`-` 图标，颜色 `plusColor`(50,255,50)/`minusColor`(255,50,50) | `BUTTON.variant = PLUS/MINUS` |
| `WConfirmedButton` | 第一次点击变 `confirmText`，再点才触发；移开鼠标复位 | `BUTTON.variant = CONFIRMED` + `confirmText` |
| `WConfirmedMinus` | 同上，配 `-` | `BUTTON.variant = CONFIRMED_MINUS` |
| `WTexture` | 贴图 + 尺寸，来自 `GuiTexture` 图集 | `UiType.TEXTURE` + 内置图标选择 |

### C 组：需要新的编辑器 UI 或 MC 数据（5 个）

| 控件 | 难点 | 计划 |
| --- | --- | --- |
| `WBlockPosEdit` | 3 个数字框 + "拾取方块"按钮（依赖 MC 世界） | 做 3 个数字框 + 坐标；拾取按钮仅在游戏内启用 |
| `WItem` / `WItemWithLabel` | 需要物品注册表 | 编辑器里做静态预览（真实贴图或占位 + 物品 id 文本），导出/运行时用真 `WItem` |
| `WTable` | 行列布局算法（`calculateInfo` 按列宽/行高） | 第二批末尾做，作为 CONTAINER.style=TABLE |
| `WTopBar` | 标签页栏（彗星内部 UI） | **建议跳过**（插件 GUI 用不到） |
| `WAccount` | 依赖彗星账号系统 | **建议跳过**（只留导出占位） |

---

## 4. 每个控件的统一落地清单

新增一个控件，必须同时改这 7 处（缺一处就会出现"能画不能点 / 能点导不出"）：

1. **模型** `core/model`：`UiType`/变体 + `UiNode` 字段 + `copyWithFreshIds`
2. **画法** `core/render/MeteorPainter`（`MeteorCanvas` 接口不变，各后端无需改动）
3. **布局** `core/render/MeteorLayout`（如果涉及容器语义）
4. **运行时** `src/.../runtime/UiRenderer` → 真实 `theme.xxx(...)`
5. **导出** `core/export/MeteorExporter` + `JavaExporter`
6. **编辑器 UI**：`simulator-gl` 的 `GlEditor`（调色板 + Inspector 行）与游戏内设计器
   `DesignerPage`（调色板 + Inspector）
7. **自检**：`tools/selftest`（当前 160 项断言，含几何断言与导出字符串断言）与 GL `--selftest`

---

## 5. 里程碑与验收

| 里程碑 | 内容 | 验收 |
| --- | --- | --- |
| **M0** | Cell 布局移植 + `style`/`orientation` 属性 | 现有设计渲染复核（间距 3）；自检全绿 |
| **M1** | 第一批：SECTION / VIEW / 水平 / NUMBER / KEYBIND / TOOLTIP | 用它们搭一个"真实插件配置界面"（分组 + 滚动 + 数值 + 按键 + 提示），编辑器里可交互、导出代码含正确构造器 |
| **M2** | 第二批 A+B 组（9 个） | 同上，调色板补齐 |
| **M3** | 第二批 C 组（3~5 个）+ 文档 | 物品/坐标控件的静态预览 + 运行时真控件 |

每个里程碑结束跑：`gradlew build` → `run-selftest.cmd` → GL `--selftest` →
`--render` 出图对比 → 导出文件的人工抽查。

---

## 6. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 布局重写（spacing 6→3、pad 可变） | 旧设计视觉变化 | 旧文档字段缺省 → 用彗星默认值；出图对比确认；必要时保留"兼容间距"开关 |
| `WView`/`WSection` 需要动画状态 | 模型污染 | 用 `UiNode` 的 transient 字段（已有 `animProgress` 先例） |
| `WKeybind` 依赖 GLFW 键码 | 独立编辑器不链接 GLFW | 用字符串键名（`key.keyboard.f`）+ 映射表 |
| `WItem`/`WBlockPosEdit` 依赖 MC 注册表 | 编辑器没有真实注册表 | 编辑器只做静态预览（贴图索引或占位），运行时/导出用真控件 |
| 编辑器与游戏内需要同步 | 工作量大 | 以 `simulator-gl/` 编辑器与游戏内设计器为主目标；两者共享 `core` 的模型与几何，画法只有一份 `MeteorPainter` |
| 控件数量膨胀 | `UiType` 爆炸 | 类型 + 变体混合（`BUTTON.variant`、`CONTAINER.style`） |

---

## 7. 进度

| 项 | 状态 | 说明 |
| --- | --- | --- |
| M0 Cell 布局 | ✅ 完成 | `MeteorLayout`（spacing 3 / padding 8 / 水平 expandX / align） |
| 字体清晰度 | ✅ 完成 | GL 图集 3× 超采样，界面与预览字号对齐到整数设备像素 |
| 第一批 WSection | ✅ 完成 | `ContainerStyle.SECTION` + `collapsed` + 三角/分隔线 + 折叠动画 + 点击折叠 + 运行时 `theme.section` + 导出 + 编辑器与游戏内设计器 |
| 第一批 WView | ✅ 完成 | `ContainerStyle.VIEW` + viewport 裁剪 + 滚动条 + 滚轮 + 内容高度钳制 |
| 第一批 水平布局 | ✅ 底层完成 | `Orientation.HORIZONTAL` + `expandX` 已可用（UI 已暴露 Flow 下拉） |
| 第一批 WIntEdit/WDoubleEdit | ✅ 完成 | `UiType.NUMBER`：模型（`integer`/`step`/`showSlider`/`showButtons`）+ 组合画法 + 运行时 `intEdit`/`doubleEdit` + 两种导出；交互与 Inspector 行（`simulator-gl` 编辑器与游戏内设计器）+ 自检断言 |
| 第一批 WKeybind | ✅ 完成 | `UiType.KEYBIND`：GLFW 键码 + 修饰位 + 键名双向表（`KeyNames`）；点击→按键绑定、Esc 取消、右键清除；导出 `Keybind.fromKeys/fromButton` |
| 第一批 WTooltip | ✅ 完成 | `UiNode.tooltip` 对所有控件生效，沿父链继承；painter 最后绘制，跟随指针 +12 并贴边，14/秒淡入淡出 |
| 第二批 WTable | ✅ 完成 | `ContainerStyle.TABLE`：列宽 = 同列最宽、行高 = 行内最高、`Cell.row/column` = Meteor 的 `table.row()`、`Cell.group` 跨表同步列宽、`expandX` 分摊余宽；导出 `theme.table()` + `.row()` |
| 第二批 WTexture | ✅ 完成 | `UiType.TEXTURE` + 贴图管线（`extractMcAssets` 生成物品→贴图索引 + Meteor GUI 图标，`AssetStore`/`TextureSource`，GL 上传翻转行序 + NEAREST），取不到图时画占位 |
| 第二批 WItem / 实体 / Select | ✅ 完成 | `UiType.ITEM`（16 贴图 × `scale(2)` 装进 `scale(32)` 方框 + 数量角标）、`UiType.ENTITY`（刷怪蛋反查，无蛋走占位）、`UiType.SELECT`（`Select (N selected)`）；导出常量由索引判定 |
| 第二批 A/B/C 组（其余） | ⏳ | 见上文分组 |

尚未做：**多选列表控件本体**（图标 + 名字 + 勾选框 + 分组 + 搜索 + 组级全选）与其弹出选择界面
（左「全部」/右「已选」两栏 + 加减按钮）。这部分没有现成规格，
实现时参考同类多选列表控件的通用做法（列表 = 一组条目 + 每条的选中状态），
行为规格由本项目自行定义并写进自检。

展示文件：

- `examples/widgets.json`：分组 + 折叠 + 水平行 + 文本框 + 滚动区 + 三个数值控件 + 贴图
- `examples/welcome.json`：起点示例（面板 + 常用控件），编辑器默认打开的文件

出图与排障：

```bat
java -cp simulator-gl\build\libs\simulator-gl-0.2.0-all.jar com.hackli.guidesigner.gl.GlSimulator ^
     --render examples\widgets.json out.png --zoom 2 --dump-layout
```

`--dump-layout` 会打印每个节点**计算后**的矩形（含 cell 标记），排查布局问题时比看像素可靠得多。

