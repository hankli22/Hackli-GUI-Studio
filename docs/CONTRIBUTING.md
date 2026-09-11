# 构建与贡献约定（Hackli GUI Studio）

> 这份文档说明如何搭建环境、跑构建与自检、提取贴图素材，以及提交改动时必须遵守的约定。
> 模块职责与设计取舍见 [`ARCHITECTURE.md`](ARCHITECTURE.md)，
> 控件移植计划见 [`PLAN-WIDGETS.md`](PLAN-WIDGETS.md)，
> 第三方声明见仓库根目录的 [`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md)。

## 1. 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | **21**（所有模块 `options.release = 21`；用更高的 JDK 运行 Gradle 也可以，编译目标仍是 Java 21） |
| Gradle | **由 Gradle Wrapper 提供（9.2.0）**，首次运行自动下载，无需预装；只有在自己知道在做什么时才用系统 Gradle |
| Minecraft | **1.21.11**（Yarn `1.21.11+build.1`） |
| Fabric Loader | **0.19.3+** |
| [Meteor Client](https://meteorclient.com/) | **1.21.11-SNAPSHOT**（addon API 入口点 `meteor`） |
| Fabric Loom | 1.14.10（由 Gradle 插件解析） |

首次构建会从 Maven 下载 Minecraft、Meteor Client 与 Fabric 依赖，耗时较长属正常现象。
版本号集中在 `gradle.properties` 与各模块的 `build.gradle` 里，请勿在源码中硬编码。

编辑器（`simulator-gl`）额外需要一块支持 OpenGL 3.2 core 的显卡与 LWJGL 的本地库
（由 Gradle 依赖自动带上）；**无头自检不需要 GL、不需要窗口、也不需要字体文件**。

## 2. 常用命令

```bat
:: 构建全部模块（core + simulator-gl + selftest + 游戏内 mod）
gradlew build

:: 显式构建自包含的 *-all.jar（编辑器 / 自检的可执行包）
gradlew build fatJar

:: 无头 core 自检：无窗口、无 GL、无字体文件
run-selftest.cmd

:: 打开 OpenGL 编辑器（默认在 build\welcome-edit.json 的副本上工作）
run-gl.cmd
run-gl.cmd examples\welcome.json            :: 指定设计稿
run-gl.cmd examples\welcome.json play       :: 直接进入试玩模式
run-gl.cmd examples\welcome.json out.png    :: 离屏渲染成 PNG

:: 构建 + 可选地把 mod jar 复制到游戏实例的 mods/（设置 HACKLI_MODS_DIR 才会复制）
build-all.cmd

:: 打一个自包含发行包到 release/
build-release.cmd
```

- Linux / macOS 下把 `gradlew` / `*.cmd` 换成 `./gradlew` 与对应的 `java -cp ...` 命令即可。
- `gradlew build` 会同时产出：
  `build/libs/hackli-gui-studio-<版本>.jar`（mod）、
  `simulator-gl/build/libs/simulator-gl-<版本>-all.jar`（编辑器）、
  `tools/selftest/build/libs/selftest-<版本>-all.jar`（自检）。
- **`java -cp` 跑的永远是上面那两个 `-all.jar`**：只改代码不重新打包，跑到的还是旧代码。
- 生成物、`release/`、`build/` 都已在 `.gitignore` 中，不要提交。

## 3. 自检：必须全绿才算改完

改完代码请**按顺序**跑完下面两项，任何一项失败都不算完成。

### 3.1 无头 core 自检

```bat
run-selftest.cmd
```

等价于：

```bat
java -Djava.awt.headless=true -cp tools\selftest\build\libs\selftest-0.2.0-all.jar ^
     com.hackli.guidesigner.testing.CoreSelfTest
```

它检查文档模型与 JSON 往返、布局数学、painter 几何、两个导出器、撤销、模板、剪贴板、
图标图集的生成、以及各个控件的几何与取值数学，结束时打印
`SELF-TEST OK (<N> checks)`（当前 **160** 项断言，请以实际输出为准）。
不需要窗口、GL 上下文或字体文件，可以在 CI 里直接跑。

### 3.2 无头 GL 自检

```bat
java -cp simulator-gl\build\libs\simulator-gl-0.2.0-all.jar ^
     com.hackli.guidesigner.gl.GlSimulator examples\welcome.json --selftest
```

它用隐藏窗口 + 离屏帧缓冲驱动**真实的编辑器实例**，检查命中测试、模式切换、
工具栏/调色板/Inspector、拖动与缩放、数值控件交互、按键绑定、悬停提示、
对齐与模板、导出菜单、折叠分组等端到端行为。

两个自检都是**无窗口后台运行**的（core 用 `-Djava.awt.headless=true`，
GL 用 `GLFW_VISIBLE=false`），不会抢焦点。**请不要把它们改成可见窗口。**

### 3.3 出图自查

改动布局或画法时，除了自检，建议出图肉眼比对：

```bat
java -cp simulator-gl\build\libs\simulator-gl-0.2.0-all.jar com.hackli.guidesigner.gl.GlSimulator ^
     --render examples\welcome.json out.png --zoom 2 --dump-layout
```

`--dump-layout` 打印每个节点计算后的矩形（含 cell 标记），排查布局问题非常有效。

## 4. 图标与素材（两个来源）

编辑器的图标有两个**互相独立**的来源，`AssetStore` **优先使用本地提取的真实贴图**，
取不到时回退到生成图标集，两者都没有时画占位块。

### 4.1 生成图标集（随仓库提交，随发行包分发）

`assets/icons/` 下是**程序生成**的图标集，不含任何游戏美术资源：

- `icon-atlas.png`：一张方格图集，每个原版物品 id 一格 16×16 图标；
- `icon-atlas.tsv`：索引（`物品 id <TAB> 列 <TAB> 行 <TAB> 格大小`）；
- `LICENSE-icons.txt`：来源与许可证说明。

每个图标都由**物品 id 的稳定哈希**生成（基色 + 渐变 + 斜面 + 八种简单字形之一），
因此在任何机器、任何一次运行上都完全一致，也不模仿原版像素。
这是本项目自己的原创产物，以 **CC0-1.0** 公有领域奉献发布，可以随发行包分发。

目标 Minecraft 版本变化时重新生成：

```bat
gradle :core:genIconAtlas -PclientJar="<path to 1.21.11.jar>"
```

（客户端 jar 只用来读取物品 id 列表；该任务内部走 `--icons-only` 模式。）

### 4.2 本地提取真实贴图（**仅供本地使用**）

需要像素级对齐真实贴图时，从**你自己拥有的**客户端 jar 里本地提取：

```bat
gradle :core:extractMcAssets -PclientJar="<path to 1.21.11.jar>"
```

- 输出目录是 `build/mc-assets/`（**已 gitignore**），内容是扁平化的物品/方块贴图
  与 `item-textures.tsv` 索引（物品 id → 游戏实际使用的贴图路径）；
- 不加 `-PclientJar` 时，任务会在当前用户常见的启动器位置里找 1.21.11 客户端 jar；
- Meteor 的 GUI 图标（重置、编辑等）在 Meteor 的 jar 里，可用
  `-PmeteorJar="<path>"` 指定，否则任务会尝试从 Gradle 缓存里找；
- **该产物仅供本地预览与自检使用**：请勿把它提交进仓库、打进发行包或另行分发。
  仓库与发行包都**不内置任何 Mojang 素材**。

详细的权利说明见 [`THIRD-PARTY-NOTICES.md`](../THIRD-PARTY-NOTICES.md) 与
`assets/icons/LICENSE-icons.txt`。

## 5. 代码风格

- **许可证文件头**：每个新建的 `.java` 文件都必须带 GPLv3 头（本项目自身即 GPLv3）。
  新增文件请复制现有文件的头部，保留版权行与免责声明：

  ```java
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
  ```

  仓库地址如有调整，文件头里的 URL 一并更新。
- **不要移除或改写第三方的版权与许可证头**。本项目自身就是 GPLv3，
  抹掉别人的版权标记既违约也自相矛盾；需要某个控件的画法时，
  按公开的渲染规则/行为规格**自行实现**，写出来就是本项目的代码。
- **编码与换行**：源码一律 **UTF-8**；`.editorconfig` 已声明缩进（默认 4 空格，
  JSON / YAML 2 空格）、行尾不留空格、文件末尾留一个空行。请让编辑器遵循它。
- **`.java` 源码保持纯 ASCII**：注释与字符串用英文，中文只出现在 Markdown 文档与
  `examples/` 的说明性文本里。这条约定让源码在任何编码环境下都能安全编译。
- **Java 21 语言级别**：可以用 record、switch 表达式、文本块等；不要用更高的语言特性。
- **`core/` 不得引入 Minecraft 依赖**：这是无头自检与独立编辑器成立的前提。
  新增几何/布局逻辑请放进 `core/`，让所有后端共享同一份实现
  （见 [`ARCHITECTURE.md`](ARCHITECTURE.md) 第 3 节）。

## 6. 提交约定

- **一条提交做一件事**，标题用简洁的祈使句（`Fix …` / `Add …` / `Update …`），
  首字母大写，不加句号；
- **正文说明"为什么"**：改动解决了什么问题、有哪些取舍、如何验证。
  代码本身已经说明了"做了什么"，正文只需要交代动机与影响；
- 一次提交里同时包含实现与它对应的自检断言；
- 新增控件请照着 [`PLAN-WIDGETS.md` §4](PLAN-WIDGETS.md#4-每个控件的统一落地清单)
  的清单逐项补齐（模型 / 画法 / 布局 / 运行时 / 导出 / 编辑器 UI / 自检），
  缺一项就会出现"能画不能点"或"能点导不出"；
- 提交前确认：`gradlew build` 通过、**两个自检全绿**、没有把 `build/`、`release/`、
  `build/mc-assets/`（本地提取的原版贴图）或本地配置目录加进版本控制。
  提交 `assets/icons/` 里的**生成图标集**是可以的——那是本项目的 CC0 产物。

## 7. 提交前的检查清单

- [ ] `gradlew build` 通过
- [ ] `run-selftest.cmd` 全绿（看结尾的 `SELF-TEST OK (<N> checks)`）
- [ ] GL `--selftest` 全绿
- [ ] 新增/修改的 `.java` 带完整的 GPLv3 文件头，且保持纯 ASCII
- [ ] 没有把 Minecraft / Mojang 素材或 `build/mc-assets/` 的提取产物加入版本控制
- [ ] 如果改了图标生成逻辑：重新跑过 `gradle :core:genIconAtlas` 并提交 `assets/icons/`
- [ ] 如果改了布局或画法：出图或 `--dump-layout` 自查过
- [ ] 文档（`README.md`、`docs/`）里受影响的说明已同步更新
