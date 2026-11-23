# 腕记 WristNote（小米 Watch 5 · Wear OS 圆屏笔记）

给小米 Watch 5（Wear OS，480×480 圆屏）做的免费手表笔记应用：记作业、记事情、课程表、倒计时，配中国传统色主题，并带一套可扩展的插件系统。

- 包名：`com.ioo.watchnote`
- 当前版本：**4.1.24（versionCode 133）**
- 技术：Kotlin + Jetpack Compose for Wear OS，原生实现，不依赖 WebView

## 支持作者

腕记由个人开发者 **wojiuluguo** 独立开发并免费发布。

- GitHub 账号：wojiuluguo（[wojiuluguo/watchnote](https://github.com/wojiuluguo/watchnote)）
- 爱发电：昵称「我就」，主页 <https://ifdian.net/a/wojiuluguo>
- 爱发电作品集「腕记 WristNote」：<https://ifdian.net/album/9f79f506e67511f1a687525400c8052f>

如果喜欢腕记，欢迎在爱发电支持后续开发。

## 下载与安装（Releases）

最新构建发布在 [GitHub Releases](https://github.com/wojiuluguo/watchnote/releases)，当前为 **v4.1.24（versionCode 133）**：

| 附件 | 说明 |
| --- | --- |
| [watchnote-4.1.24-133.apk](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.24/watchnote-4.1.24-133.apk) | 腕记 Beta 4.1.24，安装到手表/手机（Android 8.0+） |
| [hello.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.24/hello.wplugin) | 示例外置插件：设置页底部多一行入口 |
| [page-demo.wplugin](https://github.com/wojiuluguo/watchnote/releases/download/v4.1.24/page-demo.wplugin) | 示例页面插件：带一个页面，点「计数加一」数字实时变 |

安装后先别找插件：4.1.24 起插件模块**默认隐藏**，默认只加载官方主题颜色。要装插件先到「设置 → 关于 → 详细信息」，对版本号连点三下开启插件模块（再连点三下关闭），之后「关于」页会多出「插件自检」，确认插件自检 32/32 通过。插件安装见下文「插件系统」。

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
- **声明式页面**：4.1.21 起外置插件也能带页面（`SimplePage`：插件只交统计行/按钮行和一个回调，宿主用官方组件渲染，Java + d8 就能编）；4.1.22 起 `SimplePage.render(...)` 让页面按最新数据重画（宿主每次重画都重跑回调，数字不会停在装上的那一刻）
- **可选签名**：插件可用 ECDSA P-256 作者签名，宿主记录但非强制
- **能加就能收**：插件注册的页面和按钮都挂在 pluginId 名下，关闭/卸载一键收回
- **数据隔离**：每个插件的数据存在自己的 `watchnote_plugin_<id>` 里，卸载只清自己的
- **插件自检**（需先开启插件模块）：关于 → 插件自检，32 项覆盖插件清单校验、加载器、插槽渲染、外置插件真加载 / 真安装、外置插件 ABI 类名是否被混淆、声明式页面注册与页面型示例包真装，以及页面按最新数据重画 / 渲染出错退回静态行

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

- [docs/版本历史.md](docs/版本历史.md)：从早期版本到 4.1.24 的更新记录
- [docs/最终版施工计划.md](docs/最终版施工计划.md)：最终版各 Step 的施工计划
- [docs/插件模块-版本说明.md](docs/插件模块-版本说明.md)：插件系统逐版说明
- [docs/插件模块-API设计.md](docs/插件模块-API设计.md)：插件 API 设计与硬规则
- [docs/插件外置化-v2设计.md](docs/插件外置化-v2设计.md)：外置 `.wplugin` 加载器设计
- [docs/插件作者手册.md](docs/插件作者手册.md)：写插件的手册
- [docs/external-sample/](docs/external-sample/)：插件示例源码与打包/签名脚本
