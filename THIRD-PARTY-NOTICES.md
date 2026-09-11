# 第三方声明（Third-Party Notices）

> 本文件说明 Hackli GUI Studio 的仓库与发行包中涉及、依赖或参考到的第三方组件及其许可证。
> **本文件不是法律建议**；如有疑问，请以各组件的官方许可证文本为准，必要时咨询专业人士。

本项目自身以 **GNU GPLv3** 发布，完整文本见 [`LICENSE`](LICENSE)
（发行包中的许可证文本为 `LICENSE.txt`，即同一份 GPLv3 的副本）。

## 1. Meteor Client（GPL-3.0）

- 项目：Meteor Client — <https://github.com/MeteorDevelopment/meteor-client>
- 许可证：**GNU General Public License v3.0**
- 与本项目的关系：本工具用于为 Meteor Client 开发插件 GUI，
  游戏内模块以 Fabric mod 的形式与 Meteor Client 协同运行（编译期依赖、运行期共存，
  **不包含**其代码）。

**关于 GUI 外观的复刻声明**：本项目为使编辑器与游戏内效果一致，
**依据 Meteor Client 的渲染规则复刻了其 GUI 外观**，包括 `MeteorGuiTheme`、`MeteorWidget`
及其各 `WMeteor*` 控件所定义的配色三态（背景 / 描边 / 悬停 / 按下）、强调色、
内边距与行高、圆角与描边方式、分隔线与滚动条的渐变、滑块与复选框的几何规则等。

- 这些常量与规则在本项目中是**独立的原创实现**（见 `core/render/MeteorTheme.java`、
  `core/render/MeteorPainter.java`、`core/render/MeteorLayout.java`），
  **未复制 Meteor Client 的源代码**；
- 本项目的全部源码（`com.hackli.guidesigner.*` 包）均为自行编写；
- 编辑器可选地从**使用者本机**的 Meteor Client jar 中提取其 GUI 图标用于本地预览，
  这些图标不属于本项目、其权利归 Meteor Client 所有，仅供本地使用，不随发行包分发。
- 本项目**不会移除或改写**任何第三方文件中的版权与许可证声明。

Meteor Client 与本项目均为 GPLv3，二者在许可证上兼容。使用本项目开发的插件所涉及的
许可证问题见 [`docs/LICENSE-PLUGINS.md`](docs/LICENSE-PLUGINS.md)。

## 2. Minecraft 与 Mojang

- **本仓库与发行包均不内置任何 Mojang 素材**：不打包、不分发原版贴图、模型、
  声音或其他客户端资源。
- 编辑器的**真实贴图预览**功能从**使用者自己的** Minecraft 客户端 jar 中**本地提取**资源：
  产物位于被 `.gitignore` 排除的构建目录（`build/mc-assets/`），
  仅用于该使用者本机的预览与自检，**不会**被打进发行包，也请勿再分发。
  相关命令见 [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md)。
- 仓库内提交、并随发行包分发的图标集 `assets/icons/icon-atlas.png`（索引为
  `icon-atlas.tsv`）是**程序生成的合成图**：每个图标由物品 id 的稳定哈希决定其配色、
  渐变、斜面与字形，并非 Mojang 的美术资源，也不包含任何原版像素。
  这一项目内自制产物采用 **CC0-1.0**（公有领域奉献）发布，
  详见 `assets/icons/LICENSE-icons.txt`。
- Minecraft 是 Mojang Studios / Microsoft 的商标。
  本项目是**非官方的第三方工具**，与 Mojang Studios 或 Microsoft **无隶属关系**，
  未获其批准、赞助或认可。

## 3. 其他第三方组件

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| [Gson](https://github.com/google/gson) 2.13.2 | `core` 的 JSON 序列化 | Apache-2.0 |
| [Gradle](https://gradle.org/) 9.2.0（Gradle Wrapper，含 `gradle-wrapper.jar`） | 构建系统 | Apache-2.0 |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) 0.19.3+ | 游戏内 mod 的加载器 | Apache-2.0 |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) 1.14.10 | Gradle 构建插件 | MIT |
| [Yarn](https://github.com/FabricMC/yarn) `1.21.11+build.1` | Minecraft 映射 | CC0-1.0 |
| [LWJGL](https://www.lwjgl.org/) 3.3.6（`lwjgl`、`glfw`、`opengl`、`stb`） | `simulator-gl` 的 OpenGL 编辑器窗口与渲染 | BSD-3-Clause |
| [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) 1.21.11-SNAPSHOT | 目标平台与 addon API | GPL-3.0（见第 1 节） |

以上组件均以其各自许可证分发；本项目不修改、不重新授权这些组件的代码。
发行包与源码分发中保留了 `gradle-wrapper.jar` 等第三方二进制文件的原始内容。

## 4. 声明保留与合规提示

- 请勿移除、改写或遮蔽任何第三方文件中的版权头与许可证声明。
  本项目自身即为 GPLv3，抹除他人的版权标记既违反其许可证，也自相矛盾。
- 若你重新分发本项目或其修改版，请一并保留本文件、[`LICENSE`](LICENSE)
  以及随附第三方组件的许可证文本。
- 若你是上述任何组件的权利人，认为本项目的使用方式存在问题，
  请通过仓库的 Issue 与我们联系，我们会尽快处理。
