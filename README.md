# 腕记 WristNote（小米 Watch 5 · Wear OS 圆屏笔记）

给小米 Watch 5（Wear OS，480×480 圆屏）做的免费手表笔记应用：记作业、记事情、课程表、倒计时，配中国传统色主题，并带一套可扩展的插件系统。

- 包名：`com.ioo.watchnote`
- 当前版本：**4.1.34（versionCode 143）**
- 技术：Kotlin + Jetpack Compose for Wear OS，原生实现，不依赖 WebView

## 最近更新（4.1.34 · 插件模块 0.4.5）

【插件能自己排界面了】 新页面类型 `UIPage` 是一棵容器树：横排、竖排、叠放、分组、网格、滚动，内边距、间距、对齐、权重都自己定。两种排布同时成立——容器里按顺序自动从上往下，也能表达「这是第几个」「属于哪个容器」。老的 18 种官方行照旧能用，同一个页面里可以混着来。

【画布：画一个按钮，也画一整页】 元素画布 `Ui.canvas(56) { pen -> ... }` 跟官方行同层，用来画环形图、仪表盘、自己画的按钮；整页画布 `Ui.fillCanvas { ... }` 配 `page.fullPage()` 连标题都不要，整页自己画。画笔有 clear / rect / circle / line / arc / path / text，坐标归一化，颜色可写十六进制或主题色名，换主题自动跟着变；支持点击与拖动回调，回调里能调完整宿主 API。**画布不是开关**：官方行、画布、第三方插件组件在同一层混排，不存在「选了画布就没 API」。

【联网渠道 host.net()】 插件终于能联网：`get / post / request`，回调回主线程。只放行 http/https，拒本机与内网，响应上限 256KB，超时 5~30 秒，每插件 30 次/分、并发 2，离开页面就取消，请求带插件 id。要在清单里声明 `"permissions": ["net"]` 才生效。诚实说明：外置插件是普通 Java 字节码，技术上拦不住它自己开 socket，「唯一渠道」靠的是只提供这一条 API + 安装时写明 + 日志 + 审核，不是沙箱。

【插件主页面与指示栏第 6 格】 插件可以申请一整页当主页面（`asMainPage()`）。只有 1 个时，左边指示栏从 5 格变 6 格，第 6 格就是它；有 2 个以上时第 6 格变成「插件页面」抽屉，列全部插件主页；一个都没有就还是 5 格。卸载 / 停用插件，主页自动消失，指示栏退回 5 格，不留空页。

【打开就能看的内置样板】 官方插件里多了一个「画布演示」（默认关闭，去「插件管理 → 官方插件」打开）：打开后左边指示栏从 5 格变 6 格，第 1 页是分组 + 环形进度画布（点一下 +10%）+ 一次真联网测试，第 2 页是整页自绘表盘（点哪红点去哪）。想快速验收这一版，打开它把两页点一遍就够。

【为什么这一版叫 0.4.5，不叫 0.5】 0.5 的验收线里有一条还没做到：**插件要能改腕记自己的官方页面**——官方页面的布置、按钮样式都能被插件改。那需要官方页面结构可注入、多插件冲突仲裁、一键回退，工程量和风险都不是这一版能塞进去的。所以这一版只把「插件自己的页面能有多自由」交到位，版本号停在 0.4.5；等官方页面也能被插件修改了，才叫 0.5。这段话在 App 里也写了：关于 → 详细信息、插件页底部各一段。

之前的 0.1 → 0.4 更新说明（官方 UI 行从 2 种到 17 种、前置模块、SHAPE 形状行、插件页重排、设置页配额、插件自检 40 项）见 [docs/版本历史.md](docs/版本历史.md)。

## 支持作者

腕记由个人开发者 **wojiuluguo** 独立开发并免费发布。

- GitHub 账号：wojiuluguo（[wojiuluguo/watchnote](https://github.com/wojiuluguo/watchnote)）
- 爱发电：昵称「我就」，主页 <https://ifdian.net/a/wojiuluguo>
- 爱发电作品集「腕记 WristNote」：<https://ifdian.net/album/9f79f506e67511f1a687525400c8052f>

如果喜欢腕记，欢迎在爱发电支持后续开发。

## 下载与安装（Releases）

最新构建发布在 [GitHub Releases](https://github.com/wojiuluguo/watchnote/releases)，当前为 **v4.1.34（versionCode 143）**：

| 附件 | 说明 |
| --- | --- |
| [watchnote-4.1.34-143.apk](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/watchnote-4.1.34-143.apk) | 腕记 Beta 4.1.34（插件模块 0.4.5：布局容器树 + 元素/整页画布 + 联网渠道 host.net() + 插件主页面/指示栏第 6 格），安装到手表/手机（Android 8.0+） |
| [hello.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/hello.wplugin) | 示例外置插件：设置页底部多一行入口 |
| [page-demo.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/page-demo.wplugin) | 示例页面插件 v3（minApi 2）：一个页面里演示整套官方 UI 行（大数字、进度条、进度环、步进、选项、开关、颜色卡、时间卡、柱状图） |
| [uikit.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/uikit.wplugin) | 示例**前置模块**（lib 型，minApi 3）：不占界面，提供 `com.example.uikit.FrontKit`（时间格式化、百分比、大数字、进度环） |
| [frontui.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/frontui.wplugin) | 示例**依赖前置的 UI 插件**（minApi 3，requires uikit>=1）：用 FrontKit 画页面，前置不可用时退回官方组件 |
| [frontalgo.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/frontalgo.wplugin) | 示例**依赖前置的算法插件**（minApi 3，requires uikit>=1）：用 FrontKit 算笔记字数估阅读时长 |
| [shapekit.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/shapekit.wplugin) | 示例**前置模块**（lib 型，minApi 4）：提供 `com.example.shapekit.ShapeKit`（三角形/星形/菱形/六边形绘制） |
| [triangle.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.34/triangle.wplugin) | 示例**依赖前置的 SHAPE 插件**（minApi 4，requires shapekit>=1）：整页显示三角形，点一下换形状、按色环变色；先装 shapekit 再装这个 |

4.1.26 起可以在手表里直接查新版：**关于 → 详细信息 → 检查更新**（进页面时若距上次超过 24 小时会自动查一次，平时不联网）。有新版会显示版本号和更新介绍，点下载有进度条；**4.1.28 起下载完会直接弹出系统安装界面**——手表上没有文件管理器，不用再自己去找 APK。第一次装会先跳到「允许腕记安装应用」开关，打开后回来点一次「安装这个版本」即可。

安装后先别找插件：4.1.24 起插件模块**默认隐藏**，默认只加载官方主题颜色。要装插件先到「设置 → 关于 → 详细信息」，对版本号连点三下开启插件模块（再连点三下关闭），之后「关于」页会多出「插件自检」，确认插件自检 40/40 通过。插件安装见下文「插件系统」。

## 页面

- **笔记**：分类筛选、新建/编辑/查看、勾完成、长按删除
- **办事**：待办卡片、完成/编辑/删除
- **课程表**：星期切换、时段管理、单双周/特殊周
- **设置**：震动反馈、主题颜色（5 个预设色 + 526 个中国传统色，按 15 个主色系分页）、分类管理、导出备份、发到手机、关于；插件模块默认隐藏，开启后才多出插件管理

## 插件系统（4.1.x）

插件不是"随便画界面"，而是**声明式插槽**：插件只提交数据和声明，宿主负责渲染，所以插件再多也不会把界面搞乱。

- **默认隐藏（4.1.24 起）**：插件模块总开关默认关，关闭时只加载官方主题颜色插件；关于 → 详细信息对版本号连点三下开启 / 再连点三下关闭，状态保存在本地
- **三个插槽**：`PAGE_BOTTOM`（页面底部小圆按钮）、`SETTINGS_ROW`（设置页行）、`CARD_ACTION`（卡片右侧动作），各最多 2 个
- **外置插件**：`.wplugin` 包（zip，内含 `plugin.json` 清单 + 代码/资源），支持安装确认、降级拒绝、连续失败熔断（3 次）
- **安装方式**：系统文件选择器选包 / 手机浏览器传包 / 手表上按网址下载
- **声明式页面**：4.1.21 起外置插件也能带页面（`SimplePage`：插件只交行数据和一个回调，宿主用官方组件渲染，Java + d8 就能编）；4.1.22 起 `SimplePage.render(...)` 让页面按最新数据重画（宿主每次重画都重跑回调，数字不会停在装上的那一刻）
- **官方 UI 全开（0.2 / 4.1.27）**：`SimplePage` 从 2 种行扩到 17 种——小节标题、说明、大数字、两行列表、按钮、进度条、进度环、数值步进、选项排、颜色卡、时间卡、开关、分割线、空白、空状态、柱状图；Kotlin 侧 `Official.*` 同名函数补齐，两边同一份宿主实现。带参数行的回调编号是契约（选项 `id:i`、步进 `id:+步长`/`id:-步长`、开关 `id:on`/`id:off`），`PluginApi.API_LEVEL` 升到 2，插件用 `minApi 2` 声明，老宿主安装前明确拒绝
- **可选签名**：插件可用 ECDSA P-256 作者签名，宿主记录但非强制
- **能加就能收**：插件注册的页面和按钮都挂在 pluginId 名下，关闭/卸载一键收回
- **数据隔离**：每个插件的数据存在自己的 `watchnote_plugin_<id>` 里，卸载只清自己的
- **插件自检**（需先开启插件模块）：关于 → 插件自检，40 项覆盖插件清单校验、加载器、插槽渲染、外置插件真加载 / 真安装、外置插件 ABI 类名是否被混淆、声明式页面注册与页面型示例包真装、页面按最新数据重画 / 渲染出错退回静态行、17 种官方 UI 行的数据保留与点按编号约定，以及前置模块五连（清单解析 / 缺前置拒绝 / lib 不许注册页面 / 同名只关一个符号 / 有前置后可装）与前置 + UI + 算法三个示例包真装一遍

插件目录：`app/src/main/java/com/ioo/watchnote/plugin/`（含 `external/` 下的外置插件清单与安装器）

## 构建

```
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot
ANDROID_HOME=C:\Users\ioo\AppData\Local\Android\Sdk
gradle --offline --console=plain :app:assembleRelease
```

（本工程没有 gradlew，gradle 用本机安装版，例如 `D:\gradle-9.4.1\gradle-9.4.1\bin\gradle.bat`）

产物：`out/app/outputs/apk/release/app-release.apk`

签名用的是仓库里的测试 keystore：`app/debug.keystore`（别名 `watchnote`，密码 `123456`），只用于调试/内测，正式发布请换成自己的正式签名。

## 文档

- [docs/版本历史.md](docs/版本历史.md)：从早期版本到 4.1.34 的更新记录
- [docs/最终版施工计划.md](docs/最终版施工计划.md)：最终版各 Step 的施工计划
- [docs/插件模块-版本说明.md](docs/插件模块-版本说明.md)：插件系统逐版说明
- [docs/插件模块-API设计.md](docs/插件模块-API设计.md)：插件 API 设计与硬规则
- [docs/插件外置化-v2设计.md](docs/插件外置化-v2设计.md)：外置 `.wplugin` 加载器设计
- [docs/插件作者手册.md](docs/插件作者手册.md)：写插件的手册（含「官方 UI 行」17 种行的表格与回调编号）
- [docs/插件API扩展建议.md](docs/插件API扩展建议.md)：下一批 API 该开哪些、不该开哪些
- [docs/external-sample/](docs/external-sample/)：插件示例源码与打包/签名脚本
