# 设计说明（为什么这样写）

本文只讲**设计取舍与理由**。模块划分、渲染管线、验证方式等"怎么做的"请看
[ARCHITECTURE.md](ARCHITECTURE.md)；构建与提交流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 目标

把 SquareLine Studio 式的工作流搬进游戏内：

1. **可视化设计** —— 调色板 + 画布 + 属性检查器，拖拽摆放控件。
2. **导出代码** —— 生成可直接编译的 Java 代码，并为每个交互控件生成回调桩。
3. **运行时改布局** —— 玩家在游戏里拖动/缩放控件，布局自动保存并在下次打开时生效。

## 核心决策：一份文档，一个渲染器

设计稿是**一棵纯数据的树**（`UiDocument` / `UiNode`，Gson 序列化，人可读可 diff）。
关键决策是：**这棵树只有一份绘制实现**（`MeteorPainter`），它只依赖 `MeteorCanvas`
这个极小的绘制接口，由各后端实现（OpenGL 编辑器、无头自检、Java2D 后端）。

为什么不是"编辑器一套、运行时一套"？因为那样必然出现两种画法：编辑时看着对齐，进游戏差几个像素，
而且每个新控件都要在两处同步实现、两处修 bug。这个项目早期正是如此（编辑器画占位方块、运行时用真控件），
结果是"所见即所得"名不副实。现在编辑器与运行时共用同一份几何与配色，控件只写一次。

代价与边界：

- 后端必须实现 `MeteorCanvas` 的全部原语（quad / 渐变 / 圆 / 文本 / 裁剪 / 纹理 / 三角形），
  能力弱的后端要自己降级（三角形就用条带拼）。
- 游戏内那条路径仍然用 Meteor 的真控件（`UiRenderer` + `UiContainer`），因为它必须真的可交互；
  两者一致靠的是同一份 `MeteorTheme` 常量与 `MeteorLayout` 几何，而不是同一段绘制代码。
  `docs/ARCHITECTURE.md` 里有这三条路径的对照表。

## 节点模型

```
UiNode { id, type, layoutMode, visible,
         anchorX, anchorY, x, y,          // 绝对定位（父容器为 ABSOLUTE 时生效）
         width, height,                   // 0/负数 = 自适应
         text, placeholder, checked, value, min, max, options, selected,
         itemId / entityId / textureRef / keybind, tooltip, cell, style,
         children }
```

- **`layout` 决定"这个容器的孩子怎么排"**，而不是这个节点自己怎么放：
  - `FLOW` —— 垂直堆叠、自己算尺寸，贴近 Meteor 原生布局；
  - `ABSOLUTE` —— 锚点 + 偏移，适合拖拽式设计。
- 锚点偏移是设计稿能同时适配不同分辨率的唯一手段，所以它是一等公民而不是后期补丁。
- 未知字段在反序列化时被忽略、缺失字段取默认值：旧设计稿在新版本里仍能打开。

## 编辑态 vs 运行态

同一棵树，两种意图，用显式标志区分而不是靠"哪段代码在跑"：

- **编辑态**画选择框、手柄、网格与 id 标签，并接管鼠标；
- **运行态**把它们全部关掉，只留控件本身与真实的交互。

命中测试**不按裁剪矩形限制**：允许控件溢出父容器并仍可点中（原作者可能故意这样设计）。
设计期也是这样，所以编辑时能选中的东西，运行时也点得到。

## 导出器：两种产物，两种取舍

- **Runtime 导出**（`JavaExporter`）：生成 `UiScreen` 子类，`buildDocument()` 是一棵可读的流式
  节点表达式树，外加一个按控件 id 分支的 handler 类。要装本 mod 才能跑，但**支持运行时改布局**。
- **Static 导出**（`MeteorExporter`）：生成只依赖 Meteor 的 `WidgetScreen`，零外部依赖。
  绝对定位会被压平成流式布局并在代码里留警告注释——这是一个**有损转换**，但换来的是"复制走就能用"。
- **JSON 导出**：设计数据 + JSON Schema，供其他工具消费。数据格式不绑定任何编辑器。

之所以保留两种而不是只留一种：插件作者的真实需求分成两类——想要玩家可自定义布局的，
和只想拿一份不惹麻烦的静态界面的。

## 素材与图标

- **发行包不含任何 Mojang 美术资源**：内置物品图标是**按物品 id 哈希程序生成**的合成图集
  （`assets/icons/`，CC0-1.0），只为了让离线编辑器可读。
- 想要像素级真实的图标，使用者可**从自己拥有的客户端 jar 本地提取**（`extractMcAssets`），
  结果落在被 gitignore 的构建目录，只在本机使用。
- 提取结果优先于生成图集：**真素材赢**，图集只是可再发行的兜底。

## 版本基线

- Minecraft **1.21.11** + Fabric Loader 0.19.3+ + Meteor Client 1.21.11-SNAPSHOT。
- **JDK 21**（`options.release = 21`），构建用仓库自带的 Gradle Wrapper。
- 升级到 Meteor 的 26.x 线（改用 Mojang 映射、GUI/输入 API 有差异）需要改动的包见
  [CONTRIBUTING.md](CONTRIBUTING.md)。
