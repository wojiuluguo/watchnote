# 插件模块 · API 设计 v1

> 目标版本：4.1.0（versionCode 109 起）
> 状态：核心代码已进 app/src/main/java/com/ioo/watchnote/plugin/，主题颜色页已作为第一个试点插件接入；
> 三个按钮插槽的宿主渲染与 10 项自测已就绪（4.1.1 / versionCode 110）；
> 插件管理页 + 开关落盘已就绪（4.1.2 / versionCode 111）；
> 导航 API、官方整页/统计组件、插件 kv 落盘、13 项自测已就绪（4.1.3 / versionCode 112）；
> 插件管理页升级为「列表 → 详情」（版本/权限/页面/按钮/数据、重新加载、失败原因），14 项自测（4.1.4 / versionCode 113）；
> CARD_ACTION 已真正接进笔记卡片、默认关闭（defaultEnabled）机制、16 项自测（4.1.5 / versionCode 114）；
> 三个插槽都有真实消费者（PAGE_BOTTOM 刷新钮）、内置插件目录健康自测、17 项自测（4.1.6 / versionCode 115）；
> 页面分成「内容页 / 列表页」两种，列表页交给宿主懒加载列表（鱼眼动画、圆屏边距、key 保持状态都由宿主包好），18 项自测（4.1.7 / versionCode 116）；
> 插件可以直接引用官方页面（HostScreen 白名单 + Official.ScreenRow）、第五个内置试点「快捷入口」、19 项自测（4.1.8 / versionCode 117）；
> 外置插件 v2 的清单契约（plugin.json 校验器）已定、20 项自测（4.1.9 / versionCode 118）；
> 外置插件 v2.0 加载器（包结构检查 → 解压 → DexClassLoader → 安装记录 / 启动恢复 / 卸载）、
> 内置示例插件 hello.wplugin、管理页外置插件区、21 项自测（4.1.10 / versionCode 119）；
> 安装前清单确认弹窗、降级拒绝、连续 3 次出错自动停用、23 项自测（4.1.11 / versionCode 120）；
> 管理页统一列出并可打开所有插件页面（插件不用再为了能被找到注册按钮），23 项自测（4.1.12 / versionCode 121）；
> 系统文件选择器安装入口（选 .wplugin → 预览 → 确认 → 安装），23 项自测（4.1.13 / versionCode 122）；
> 外置插件可选作者签名（ECDSA P-256，换钥匙/未签名覆盖被拒，预览与详情显示公钥指纹），25 项自测（4.1.14 / versionCode 123）；
> 两条自助安装通道：手机浏览器传包 / 手表按直链自己下载（`PluginReceiverServer`，局域网网页，25 项自测不变）（4.1.15 / versionCode 124）；
> PAGE_BOTTOM 改由宿主页面壳统一摆放与升降（`LocalPluginPageBottomBar` → `SubPageFrame(bottomStart)`，与主页面小圆按钮共用 `rememberEdgeReveal`），不再跟大返回键并排（4.1.16 / versionCode 125）。

## 一句话结论

插件不给自由画布，只给**声明式插槽**：插件能加页面、能往宿主已有的位置加按钮、能用官方组件和数据接口，
但画不到宿主以外的地方，卸载能一键收干净，插件崩了宿主不崩。

## 为什么不用「插件自己画 UI」

| 方案 | 长什么样 | 结论 |
| --- | --- | --- |
| A 自由画布 | 插件自己画整个圆屏 | 不选。每个插件都重画一遍，圆屏安全区/表冠/返回键各写一套，早晚崩 |
| B 声明式插槽 | 插件交页面结构 + 内容，位置由宿主定 | **选这个**。样式统一、能兜底、能回收 |
| C 纯 JSON 配置 | 插件只交配置数据 | 不够用。按钮要回调、要读数据，纯配置写不了复杂插件 |

## 三层 API

### 1. 插件本体（WristPlugin）

    interface WristPlugin {
        val manifest: PluginManifest          // id / name / version / minApi / permissions
        fun install(host: PluginHost)         // 注册页面、按钮、订阅事件
        fun uninstall()                       // 自己收拾；忘了宿主也会按 pluginId 兜底清
    }

install 里只能调 host 给的东西，拿不到 Activity、拿不到宿主导航栈。
install 抛异常 = 安装失败，宿主回滚这个插件注册过的全部内容。

### 2. 页面（PageRegistry）

页面分两种，接口上也分成两个类型（列表页 4.1.7 起）：

    sealed interface PluginPageSpec {
        val id: String                          // 必须 "pluginId:xxx"
        val title: String
        val kind: PageKind                      // ROOT / SUB / LIST
        val order: Int
    }

    // ① 内容页：自己排版的固定内容（一屏放得下的东西）
    data class PageSpec(
        override val id: String,
        override val title: String,
        override val kind: PageKind = PageKind.SUB,
        override val order: Int = 0,
        val content: @Composable (PageScope, Int) -> Unit,   // 参数是「第几个」这类 Int
    ) : PluginPageSpec

    // ② 列表页：条目可能很多，必须懒加载（见 2.2）
    data class ListPageSpec(
        override val id: String,
        override val title: String,
        override val order: Int = 0,
        val content: (list: PluginListScope, scope: PageScope, arg: Int) -> Unit,
    ) : PluginPageSpec {
        override val kind: PageKind get() = PageKind.LIST
    }

    interface PageScope {
        fun close()                            // 关自己，回上一层
        fun open(pageId: String, arg: Int = 0) // 开自己插件的另一个页面
    }

页面挂进宿主 Compose 树时，背景、返回键、圆屏安全区、表冠滚动全部由宿主负责（PluginPageHost）。

### 2.1 打开自己的页面：host.openPage（4.1.3 起）

    interface PluginHost {
        fun openPage(pageId: String)   // 宿主压栈，切到这个页面
    }

插件按钮点了要跳自己的页，走这个，返回键自动回上一层。
**只能开自己插件的页面**：宿主校验 owner，跨插件打开直接拒绝并 toast，
不会把别人的页面借道翻出来（自测第 13 项守着这条）。

### 2.2 列表页：ListPageSpec（4.1.7 起）

内容页的 content 是一个普通 Column，条目一多就一次性把每一行都量一遍，几百条分类会卡。
所以条目数量不可控的页面用列表页：

    ListPageSpec(id = "my.plugin:tags", title = "分类分布") { list, scope, arg ->
        list.item(key = "head") { Official.Title("分类分布", "共 12 类") }
        tags.forEach { tag ->
            list.item(key = "tag-" + tag) { Official.Stat(tag, "3") }
        }
    }

    interface PluginListScope {
        fun item(key: Any? = null, content: @Composable () -> Unit)
        fun items(count: Int, key: ((Int) -> Any)? = null, itemContent: @Composable (Int) -> Unit)
    }

- 宿主把它塞进自己的懒加载列表（TransformingLazyColumn）：只渲染屏幕附近那几行，
  滚动时的鱼眼缩放、圆屏边距、背景、返回键全部由宿主负责，插件不碰 lazy 的细节。
- key 传这条数据自己的 id，刷新时条目状态和滚动位置不会乱跳。
- 插件拿不到 TransformingLazyColumnScope，所以也写不出「一次性量 500 行」的页面。
- 第一个真实消费者：builtin/StatsPlugin.kt 的「▤ 全部分类」页（自测第 18 项守着类型合同）。

### 2.3 引用官方页面（HostScreen，4.1.8 起）

插件不要重画宿主已有的页面——重画的迟早对不上（少一个设置项、字号差半号、圆屏边距不一样）。
要跳官方页时只说「去哪个」，由宿主打开自己那一份：

    enum class HostScreen(val title: String) {
        ABOUT, SELF_TEST, OPEN_SOURCE, PLUGIN_MANAGER,
        MANAGE, SPECIAL_LIST, SHARE_TO_PHONE, ANIM_MODE
    }

    interface PluginHost {
        fun openHostPage(screen: HostScreen)   // 白名单之外没有这个入口
    }

    // 一行官方入口按钮（官方组件的糖）：
    Official.ScreenRow(host, HostScreen.SELF_TEST, "⚙")

- 白名单制：只有 enum 里列出来的只读入口能被打开；编辑页、设置页正文、备份恢复这类会改数据的地方
  根本不在列表里，插件拿不到。
- 打开的是宿主自己的页面实例：返回键回插件页、圆屏安全区、动画、主题色全部自动一致。
- 宿主导航没接上时只 `toast`「宿主导航未就绪」，不抛异常（自测第 19 项守着）。
- 新开放一个官方页：改 HostScreen + AppRoot 的 when 各一行，插件侧不用动。
- 样板插件：`builtin/ShortcutsPlugin.kt`（「快捷入口」，默认关闭），整页都是官方页面引用。

### 3. 按钮（ButtonRegistry / ButtonSpec）

    enum class ButtonSlot { PAGE_BOTTOM, CARD_ACTION, SETTINGS_ROW }

    data class ButtonSpec(
        val id: String, val slot: ButtonSlot,
        val icon: String, val label: String,
        val onClick: () -> Unit,                          // PAGE_BOTTOM / SETTINGS_ROW 用
        val onCardAction: ((Note) -> Unit)? = null,       // 只有 CARD_ACTION 用：收到被点的那条笔记
    )

只能往宿主已有的三个位置塞，不能新开一排按钮。三个插槽的宿主渲染已经全部写好（PluginUi.kt）：

- `PAGE_BOTTOM`：插件页面左下角的小圆按钮（36dp，和主页面底部小按钮同一套尺寸）。
  4.1.16 起不再是「叠在内容上的死图层」：PluginPageHost 把按钮条经 `LocalPluginPageBottomBar` 交给
  官方页面壳 `Official.Page` → `SubPageFrame(bottomStart = …)` → `SmallBottomStartSlot` 摆放，
  和主页面小圆按钮共用 `rememberEdgeReveal` 的升降判定：大返回键升起（页面内容不用再滚）时它跟着飞下去。
  4.1.15 之前它是死叠在 BottomStart 的图层，手机这种高屏上内容一屏放得下，就会出现
  「左下角小圆钮 + 中间大返回键」并排的画面，看着像返回键碎了一块。
  一个都没注册时整块不画；没用 `Official.Page` 画壳的裸页不会出现这排按钮。
  4.1.6 起「笔记统计」插件在统计页挂了一颗「⟳ 刷新」在这里（点击就是 host.invalidate()），
  这是该插槽第一个真实消费者，渲染层不再是「写了没人用」。
- `SETTINGS_ROW`：设置页底部多出来的行，宿主用官方 SettingPill 画，和内置设置行长得一样。
- `CARD_ACTION`：笔记卡片右侧的小圆动作（4.1.5 起真正接进 NoteCard）。
  **它和其它两个插槽的合同不一样**：卡片上的按钮必须知道用户点的是哪条笔记，
  所以必须提供 `onCardAction`，宿主会在点击时把那条 `Note` 原样交回来；
  只给 `onClick` 的 CARD_ACTION 会被拒绝注册（自测第 15 项守着）。
  没有插件注册 CARD_ACTION 时，渲染层直接 return，卡片布局和 4.0.x 一模一样。

按钮回调统一走 PluginCore.runButton()，插件里抛异常只弹一句 toast，宿主不闪退。

### 4. 数据（DataApi）

    interface DataApi {
        fun notes(): List<Note>          // 只读快照
        fun tags(): List<String>
        fun kv(): KeyValueStore          // 插件私有，按 pluginId 隔离，已落盘，卸载即清
                                     // KeyValueStore 另有 keys()：插件存了哪些键（宿主管理页只读展示）
    }

权限：READ_NOTES / WRITE_NOTES / READ_SETTINGS。没申请的权限调用时直接抛异常，
开发期就能发现，不会静默拿空数据。v1 不开放写笔记。

### 5. 事件（EventBus）

    interface EventBus {
        fun onNoteSaved(listener: (Note) -> Unit): () -> Unit   // 返回值是取消订阅
        fun onPageShown(listener: (String) -> Unit): () -> Unit
    }

监听器抛异常一律吞掉，不影响宿主。

### 6. 官方组件（Official）

插件不自己画外壳，直接用宿主这套（4.1.0 起有 Title/Button/Confirm，4.1.3 补 Page/Stat）：

    object Official {
        fun Page(title, sub = null, onBack, content)        // 整页：背景 + 返回键 + 圆屏安全区 + 滚动
        fun Title(title, sub = null)                        // 页面标题
        fun Stat(label, value)                              // 只读统计行（不可点，样式同设置行）
        fun Button(symbol, label, trailing = null, danger = false, onClick)
        fun Confirm(data: ConfirmData, onClosed)            // 官方确认框
    }

这就是「有些按钮直接引用官方」：用的是宿主同一份实现，
圆屏安全区、主题色、字号、点击反馈全自动跟着走，插件不可能画走样。

### 7. 外置插件清单（v2 契约，4.1.9 起）

v2 要让用户自己装 `.wplugin` 插件，先得把「插件包长什么样」定死。4.1.9 只做**清单校验**，不做加载
（加载器方案见同目录 `插件外置化-v2设计.md`）。

`.wplugin` 就是一个 zip：

```
hello.wplugin
├─ plugin.json          清单（必需）
├─ classes.dex          插件编译产物（必需，可多 dex）
└─ assets/              插件自带资源（可选）
```

`plugin.json` 字段与规则：

| 字段 | 必需 | 规则 |
| --- | --- | --- |
| `schemaVersion` | 是 | 目前只认 `1` |
| `id` | 是 | 反向域名，小写字母/数字/下划线，至少两段，≤64；禁止 `com.ioo.watchnote` 前缀 |
| `name` | 是 | 显示名，≤24 |
| `version` | 是 | ≥1 的整数 |
| `minApi` | 否 | 默认`API_LEVEL`；大于宿主 `API_LEVEL` 直接拒绝 |
| `entryClass` | 是 | 完整类名，必须实现 `WristPlugin` |
| `permissions` | 否 | 只能是已知枚举（`READ_NOTES` / `WRITE_NOTES` / `READ_SETTINGS`） |
| `author` / `description` | 否 | ≤40 / ≤200 字符 |

宿主侧入口（纯函数，不加载 dex、不执行插件代码）：

```kotlin
val result = ExternalPluginManifestParser.parseFile(file)   // 或 parse(json)
when (result) {
    is ExternalManifestResult.Valid -> { /* result.parsed.manifest / entryClass */ }
    is ExternalManifestResult.Invalid -> { /* result.reason 直接给人看 */ }
}
```

- 校验失败一律给中文原因（不抛异常），自测第 20 项把六种坏清单都钉住了。
- 加载器（解压、DexClassLoader、安装/升级/卸载、管理页外置入口）还没做，是 v2.0 的第一件事。
- 清单只加字段不删字段：v3 宿主将来能装 v1/v2 契约插件。

### 2.4 页面入口：管理页统一列出（4.1.12 起）

页面注册完，用户怎么找到它？4.1.12 起宿主统一负责：

- 「设置 → 插件」列表顶部有「插件页面 N 个」，运行中插件的每个页面一行（`▸` 内容页 / `▤` 列表页），
  点了直接进；**不需要插件为了「能被找到」额外注册一个 SETTINGS_ROW 按钮**。
- 插件详情（内置 / 外置）里原本只读的「注册页面」列表也变成可点按钮，顺手就能预览。
- 页面仍然跑在宿主的 `SubPage.PluginPage` 里，插件代码不参与导航；从页面返回落回管理页列表。
- 插件关掉/卸载后，这些行自动消失；管理页本身属于宿主，插件全关也进得去，不会变成死胡同。
- 这条不算插件 API 扩展（没有新增可调用方法），是宿主对 `page()` 的兜底入口；
  插件想同时放设置行、页面底部按钮也照旧。

## 代码在哪

    app/src/main/java/com/ioo/watchnote/plugin/
      PluginManifest.kt      PluginApi.API_LEVEL / Permission / PluginManifest
      WristPlugin.kt         WristPlugin / PluginHost
      PageSpec.kt            PageKind / PageScope / PluginPageSpec / PageSpec / ListPageSpec / PluginListScope / RegisterResult
      HostScreen.kt          插件可直接引用的官方页面白名单（openHostPage 的枚举）
      external/ExternalPluginManifest.kt 外置插件 v2 的 plugin.json 契约与校验器
      external/ExternalPluginPackage.kt  .wplugin 包结构检查（路径越界 / 条目数 / 大小上限 / 清单+dex 必备）
      external/ExternalPluginInstaller.kt 外置插件加载器：预览 / 解压 / DexClassLoader / 安装记录 / 启动恢复 / 卸载 / 示例包
      external/ExternalInstallPolicy.kt 外置插件升级策略（降级拒绝、首装/升级/重装文案）
      ButtonSpec.kt          ButtonSlot / ButtonSpec
      DataApi.kt             KeyValueStore / DataApi / EventBus
      PluginCore.kt          三个 Registry 实现 + DataApiImpl + EventBusImpl + PluginHostImpl + PluginCore
      PluginPageHost.kt      宿主渲染插件页（导航、返回、空页兜底、底部按钮插槽）
      PluginUi.kt            三个按钮插槽的宿主渲染层（底部小圆按钮 / 设置行 / 卡片动作）
      PluginKvStore.kt       插件 kv 的落盘实现（一个插件一个偏好文件）
      Official.kt            官方组件门面：Page / Title / Stat / Button / Confirm
      PluginSelfTest.kt      插件系统自测（25 项，见「自测」一节）
      PluginPrefs.kt         插件开关的落盘状态（关掉的插件下次启动不 install）
      BuiltinPlugins.kt      内置插件目录：加插件只往这里加一行
      PluginManagerPage.kt   插件管理页（设置 → 插件）：列表 + 详情、开启/关闭、重新加载、外置插件安装/卸载
      builtin/ThemePlugin.kt 第一个试点：主题颜色整页
      builtin/StatsPlugin.kt 第二个试点：笔记统计（只读笔记 + kv + 官方组件 + openPage + PAGE_BOTTOM 刷新钮 + 列表页「分类分布」）
      builtin/NoteInfoPlugin.kt 第三个试点、第一个 CARD_ACTION 插件：笔记卡片 ⓘ → 这条笔记的字数/时间（默认关闭）
      builtin/ShortcutsPlugin.kt 第四个试点、官方页面引用样板：「快捷入口」整页都是 HostScreen 入口（默认关闭）

宿主接入点（navigation/AppRoot.kt）：
- remember 出唯一的 PluginCore，设置 toast / invalidate 回调；
- remember 出 PluginPrefs（独立偏好文件 watchnote_plugins，不跟笔记数据混）；
- remember 出 ExternalPluginInstaller（外置插件加载器：包检查、解压、DexClassLoader、安装记录都在里面）；
- LaunchedEffect 里遍历 BuiltinPlugins.all，只 install 开关为「开」的插件；随后 restoreAll() 按记录恢复外置插件；
- 设置页显示「插件」行（实时条数）和「更换主题颜色」行；主题插件被关掉时后者自动消失；
- SubPage.PluginPage(pageId, arg) 走 PluginPageHost 渲染；
- 设置页 onTheme 指向 ThemePlugin.PAGE_LIST（原来写死的 SubPage.Theme / ColorFamily 已删除）。

## 四条硬规则（在 PluginCore 里落实）

1. **能加能收**：卸载按 pluginId 清页面、按钮、kv、事件订阅；插件自己忘了也不留残影。
2. **不给自由画布**：插件只交内容，外壳归宿主。
3. **只加不删**：插件不能删宿主页面、不能改宿主数据；v1 连写笔记都不开放。
4. **崩溃隔离**：install / 事件回调 / 数据接口全部 runCatching 或显式返回拒绝原因。
5. **有上限**：每插件最多 3 页，每个插槽最多 2 个按钮。

## 验收（v1）

1. 设置 → 主题颜色 能进，和 4.0.20 完全一样（5 预设色 + 15 色系 + 滚轮选色）。
2. 选完色回到设置，主题已变、重开应用颜色还在。
3. 卸载 ThemePlugin 后，设置里那行入口点进去不留空页（PluginPageHost 直接退回）。
4. 插件 install 抛异常 → 安装返回 Rejected，应用不闪退。
5. 插件页在圆屏上不压边、返回键可用、表冠能滚。

## 自测（4.1.1 起）

「关于 → 自测工具」页面顶部会跑一遍插件系统自测，结果显示「插件自检：N/25 通过」，
失败项逐条列出原因，同时写进 App 日志。自测在**单独的沙盒 PluginCore** 里跑，
不碰正在使用的插件和设置，跑完就扔。

25 项分别是：安装主题插件并注册两页 / 重复安装被拒绝 / 页面 id 必须带插件前缀 /
每插件最多 3 页 / 按钮插槽上限 2 且可查询 / 卸载不留痕迹且可重装 /
安装抛异常会整体回滚 / minApi 高于宿主被拒绝 / 越权读笔记会抛异常 / 事件监听器崩溃隔离 /
管理页关掉保留插件数据 / 真卸载清空插件数据 / 插件不能打开别人的页面 / 重新加载保留插件数据 /
卡片按钮必须提供 onCardAction / 卡片动作收到被点的笔记 /
内置插件目录全部可安装（id 唯一、manifest.id 与目录一致、全部能装、不超页数按钮数上限、页面 id 不冲突）/
列表页是单独一种页面类型（注册接口独立、core.page() 存进去是 ListPageSpec、kind 是 LIST、卸载一起收走）/
插件能引用官方页面（宿主收到白名单里的 HostScreen；宿主没接导航时只提示、不抛异常）/
外置插件清单校验（合法清单通过；坏 id、保留前缀 id、未知权限、minApi 过高、清单版本不对、缺 entryClass 全部被拒并说明原因）/
外置插件包结构校验（合法包通过并读出 id 与 dex 数量；越界路径、缺清单、缺代码、不认识的文件、非 zip 全部被拒并说明原因）/
连续出错自动停用（连错 3 次后页面/按钮收走、isInstalled 变 false、有提示、私有 kv 保留、能重新装回）/
外置插件升级版本策略（首次安装放行、升级放行、同版本算重装、降级当场拒绝并说明原因）/
作者签名能验真也能抓篡改（P-256 真签名通过且指纹一致；改任一文件摘要、有公钥没签名、有签名没公钥都判 Invalid；全空判 Unsigned）/
换签名钥匙被拒绝（未签名首装放行、同钥放行、签名版被未签名包覆盖被拒、换钥被拒；标签对不对）。

## 插件管理页与开关落盘（4.1.2 起，4.1.4 升级为二级页）

设置 → 插件：**先列表、后详情**。列表顶部是汇总（正在运行 N 个 / 注册页面 N 页 /
注册按钮 N 个 / 私有数据 N 项），下面每个插件一行（◉ 使用中 / ○ 已关闭）。

点一行进详情：插件 id、API 等级、权限（读笔记 / 读设置 / 不读任何数据）、
注册的每一页（标题 + 页面 id 尾）、每个按钮（label + 插槽名）、私有数据的键名，
以及「关闭/开启这个插件」「重新加载」两个动作。安装失败时详情顶部显示失败原因
（`PluginCore.lastInstallError`），下次装成功自动清掉。

为什么不做「一行直接 toggle」：插件一多，一行里同时当状态显示和开关既容易误触，
也没地方摆版本/权限/数据这些用户该看见的东西；二级页才是插件数量真正上去以后的样子。

「重新加载」= `PluginCore.reload(plugin)`：先按关掉收走页面/按钮、**保留 kv**，再 install 一遍；
插件自己状态乱了时用这个，比卸载重装安全（不会清数据）。

- 这一页**本身不属于任何插件**，挂在宿主设置页上。插件全关掉它也还在，
  否则关掉最后一个插件就再也点不进来，只能清数据——死胡同。
- 关掉 = `disable(pluginId)`：页面、按钮、清单整块消失，但**插件私有 kv 保留**；
  再打开 = 重新 `install`，页面回来，主题颜色和插件自己的数据原样还在。
  只有真卸载（`uninstall`）才清 kv。
- 开关存 SharedPreferences 文件 `watchnote_plugins`，**不和笔记数据混**；
  只有写「关」才落盘，默认全开，所以升级上来的老用户不用迁移。
- 设置页的「插件」行实时显示 `core.manifests.size`（Compose 快照列表驱动），
  装/卸后数字、以及「更换主题颜色」行在不在一屏内自己变，不用重启。
- 加新插件只改 `BuiltinPlugins.all` 一行；安装、开关、管理页都不用动。
- **默认关闭**（`BuiltinPluginEntry.defaultEnabled = false`）：会改主界面观感的插件不能装完就生效，
  比如「笔记详情」会往每张笔记卡片上加一个按钮，它默认关着，用户去管理页打开才出现；
  关掉又恢复原样。默认值由插件目录决定，实际开关照样落盘在 `watchnote_plugins`。
- 管理页要的只读查询都在 PluginCore：`pagesOf(id)` / `buttonsOfPlugin(id)` /
  `dataKeysOf(id)`（只给键名不给值）/ `lastInstallError(id)`。

## 外置插件 v2.0（4.1.10 起）

装一个插件不用再发版：.wplugin 包（zip：plugin.json + classes*.dex + assets/，可选 plugin.sig）→ 校验 → 解压 → 用 DexClassLoader 加载 → 走同一套 PluginCore。内置和外置在管理页、开关、详情、卸载上完全一样，唯一区别是列表里标「◈」和详情多一个 SHA-256 指纹（4.1.14 起还有签名指纹）。

- **包检查先于解压**：路径越界（../、绝对路径）、重复条目、条目数 > 128、单文件 > 8 MB、解压总大小 > 16 MB、包体 > 4 MB、plugin.json > 64 KB、只允许三类文件（plugin.json / classes*.dex / assets/**）——任何一条不过就不往磁盘写。
- **安装**：解压到 filesDir/plugins/<id>/<version>/，code cache 在 codeCacheDir/plugin-dex/<id>/，加载后先断言实现 WristPlugin、再核对 manifest.id 与清单一致，最后 PluginCore.install；成功才写安装记录（SharedPreferences watchnote_external_plugins：id / name / version / entryClass / author / sha256 / installedAt / fileName / keyFingerprint）。失败会清目录并 disable 兜底。
- **启动恢复**：restoreAll() 按记录重新加载；目录丢了或类加载失败就删掉这条记录，不影响其它插件和其它数据。
- **卸载**：PluginCore.uninstall + 删代码目录 + 删 code cache + 删记录；插件 kv 由 core 一起清。
- **安装入口**：管理页「＋ 安装示例插件」（assets 里内置 hello.wplugin，不用电脑）、「⇩ 从文件夹安装」（getExternalFilesDir(null)/plugins-inbox 里的 *.wplugin）、「◎ 选择文件安装」（系统文件选择器，4.1.13 起；手表没有选择器时提示改用文件夹安装）。
- **示例插件**：源码在 docs/external-sample/HelloPlugin.java + build-sample.ps1（javac → jar → d8 → 打 zip），产物 1790 字节，注册一个设置行按钮，点了弹 toast——证明代码确实是从包里的 dex 加载的。
- **限制照旧**：每插件 3 页 / 每插槽 2 按钮；外置插件和内置一样没有网络、不能写笔记；带页面 UI 的插件要用 Compose 编译器编 Kotlin，Java 只能做按钮/数据类插件（作者手册里写了）。

## 安装确认、降级拒绝与自动停用（4.1.11 起）

外置插件是真代码，装上之前要让用户看清楚，装之后出问题要能自己停住。

- **安装前清单确认**：`ExternalPluginInstaller.preview(file)` 只解析包，不落地、不加载代码，
  返回 `ExternalInstallPreview`（名字 / 版本 / API 等级 / 作者 / 权限 / 包大小 / SHA-256 / 首装还是升级 / 描述）。
  管理页三个入口（示例包 / plugins-inbox 文件夹 / 系统文件选择器）都先 preview 再弹 `ConfirmDialog`，
  用户点「安装」才走 `installFromFile`；系统文件选择器选中的文件会先复制到 cache 再预览，失败就删掉；
  手表没装系统文件选择器时 launch 包 try/catch，提示改用「从文件夹安装」，不闪退；
  `previewBundledSample()` 是示例包的预览版本。批量装文件夹时先逐个 preview，坏的跳过并提示，
  好的合成一条「安装 N 个外置插件？」。
- **降级拒绝**：`ExternalInstallPolicy.checkVersion(installed, incoming)` —— 同一个 id 再装，新版本低于已装版本
  直接失败并说明原因（插件数据按 id 存，旧版本很可能读不懂新版本写下的数据）；同版本算重装，允许。
  preview 阶段先拦一次，`installFromFile` 落地前再拦一次（防预览之后记录变了）。
- **连续出错自动停用**：`PluginCore.recordFailure(pluginId, where)` 计数，按钮 / 卡片按钮 / 事件回调抛异常都会记。
  连续 `MAX_PLUGIN_FAILURES`（=3）次就 `disable(pluginId)`：页面和按钮收走、私有 kv 保留、
  失败原因写进 `installErrors`（管理页详情能看到）、toast 告知用户；插件成功跑一次或重新安装就清零。
  失败位置分「按钮 / 卡片按钮 / 事件」，排查时能看出是哪类回调在炸。

## 作者签名（4.1.14 起）

外置插件是真代码，谁都能改包重发；加签名至少让「装过的作者钥匙换了 / 包被改过」能被发现。

- 签名可选：没签名的包第一次仍能装（没有分发中心时信任来自用户自己选的包）；但已装签名版之后，升级必须是同一把钥匙，换钥或未签名包一律拒。
- 格式（v1）：包内 plugin.sig（Base64 DER 签名），plugin.json 里 publicKey（Base64 的 X.509 SPKI）+ signingAlgorithm = SHA256withECDSA；
  签名原文＝首行 WRISTNOTE-PLUGIN-SIGNATURE-V1，之后每个非 plugin.sig 文件一行「sha256小写hex 字节数 路径」，按路径 UTF-8 字节序升序；
  公钥指纹＝SHA-256(SPKI) 前 16 个 hex 大写。
- 校验时机：`ExternalPackageChecker.inspect` 解压前逐条算摘要并验签（签名不对整包拒、不写盘）；`ExternalInstallPolicy.checkSigning` 在 preview 与 installFromFile 各拦一次。
- 用户能看见：安装预览多「包指纹 / 签名」两行，外置插件详情多「签名：已签名 · 指纹 / 未签名」一行。
- 作者工具：`docs/external-sample/sign-sample.ps1`（-NewKey 首次生成 P-256 私钥，之后自动把公钥写回 plugin.json 并重打包；私钥在包旁 keys/，别外传）。
- 边界：签名只证明包未被改、钥匙未换，不证明作者可信；证书链/第三方背书还没做。

## API 变更纪律（4.1.6 起）

插件一多，最怕的不是写不出来，是「改一行宿主 API，把别人的插件改崩」。这份 API 从这一版起按下面几条走：

1. **只加不改**：新增字段给默认值，新增能力做成可选回调/新接口；
   已经发布的名字不改、不删、不换参数顺序、不换返回类型。
2. **加能力 = 升 API_LEVEL**：`PluginApi.API_LEVEL` 加一，插件用 `manifest.minApi` 声明自己要哪级；
   宿主装不下时返回明确原因（Rejected），不是静默不生效。
3. **不算破坏的改动**：纯内部实现、修 bug、新增官方组件（给 Official 加函数）；
   这些不必升 API_LEVEL，但要在作者手册里补一句。
4. **语义变化也是破坏**：同一个回调从主线程挪到后台、从「每次点都调」变成「只在变化时调」，
   都算破坏，按第 2 条处理。
5. **每次改 API 三件事一起做**：更新本文档 + 更新 `插件作者手册.md` + 自测至少加一条守着新合同
   （例：CARD_ACTION 必须有 onCardAction 那条，就是防止以后有人把合同放宽）。
6. **淘汰要留窗口**：先标 Deprecated、文档写清替代写法，至少留一个大版本再删；
   不直接删——仓库里还有别的分支，也可能有别人照着写插件。
7. **内置插件目录健康检查**（自测第 17 项）兜底：新加内置插件忘了改 id/manifest/上限，
   自测页立刻变红，不用等用户装上才发现。

## 下一版路线

- ~~v1.1：给应用加「插件管理」页（列出已装插件、能关掉）~~ → 4.1.2 已完成。
- v1.1 剩余：把「设置」整页本身拆成插件（下一个最容易看出插件化的改造）。
- ~~v1.2：CARD_ACTION 接进笔记卡片~~ → 4.1.5 已完成（ButtonSpec.onCardAction + PluginCardActions 按笔记渲染）；
  ~~首个真正使用 PAGE_BOTTOM 的官方按钮~~ → 4.1.6 已完成（StatsPlugin 的「⟳ 刷新统计」）；
  ~~PAGE_BOTTOM 是叠在内容上的死图层~~ → 4.1.16 改为宿主页面壳统一摆放 + 跟大返回键联动（见 §3 按钮）。
- ~~页面 API 只有固定内容页，条目一多就一次性量完~~ → 4.1.7 已完成 ListPageSpec + PluginListScope，
  宿主负责懒加载/鱼眼/key；第一个消费者是统计插件的「分类分布」页。
- ~~插件只能开自己的页面，想跳官方页只能重画~~ → 4.1.8 已完成 HostScreen 白名单 + openHostPage + Official.ScreenRow，
  样板是「快捷入口」插件；导航白名单与权限分开，能开的只有只读入口。
- ~~v2 契约：外置插件包格式 + plugin.json 校验器~~ → 4.1.9 已完成（`ExternalPluginManifest.kt`，设计见 `插件外置化-v2设计.md`）。
- ~~v2.0 加载器（解压 / DexClassLoader / 安装流程 / 管理页外置入口）~~ → 4.1.10 已完成
  （`ExternalPluginPackage.kt` + `ExternalPluginInstaller.kt`）；示例包 hello.wplugin 内置在 assets，
  「安装示例插件」一键验证整条链路。
- ~~v2.1 前两项：安装前清单/权限确认弹窗、升级版本比较（拒绝降级）~~ → 4.1.11 已完成
  （`ExternalInstallPreview` + `preview()` / `ExternalInstallPolicy`）；连续 3 次出错自动停用同在 4.1.11。
  系统文件选择器入口仍未做。
- ~~持久化：插件私有 kv 仍是内存态~~ → 4.1.3 已由 PrefKeyValueStore 落盘（一个插件一个偏好文件），
  开关（watchnote_plugins）与插件数据分开存，管理页关掉不清、真卸才清。
- 导航与官方组件：`host.openPage` + `Official.Page/Stat` 已就绪（4.1.3），
  新插件照着 `builtin/StatsPlugin.kt` 抄即可，不用碰宿主代码。
