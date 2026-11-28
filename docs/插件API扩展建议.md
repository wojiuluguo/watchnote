# 插件 API 扩展建议（下一版起）

> 状态：建议稿。**批次 A3「组件扩展」已在 4.1.27（插件模块 0.2）落地**，实际命名与本稿草案不同，见 3.A3 末尾对照表；**4.1.29（0.3）另加了不在本稿的「前置模块」**（lib 型插件 + provides/requires + 单符号冲突隔离，见 `插件模块-API设计.md` 2.6 与 `插件作者手册.md` 九）；A1/A2/A4 未做，B/C 未做。
> 代码基线：4.1.29（138）／PluginApi.API_LEVEL = 3／MODULE_VERSION 0.3
> 相关源码：plugin/WristPlugin.kt、DataApi.kt、PageSpec.kt、SimplePage.kt、ButtonSpec.kt、Official.kt、HostScreen.kt、PluginManifest.kt
>
> 需求方目标（m09865）：给更多能力方便用户；给更多可能性；更方便、更好用的 UI。
> 下面的排序按「用户能得到什么」排，不按实现难度排。

## 0. 一句话结论

**开放 API 不是「给插件更多自由」，而是「多给能力、少给自由度」。**

继续守住「插件只交数据和声明、宿主负责渲染」这条线，把作者真正缺的四类东西按顺序补上：

1. **数据（读）** —— 现在只能读笔记，待办、课程表、设置都读不到
2. **组件（显示）** —— 声明式页面只有 stat / button 两种行，稍微复杂就写不出来（**已落地**：4.1.27 起 17 种官方行）
3. **事件（刷新）** —— 数据变了只能靠用户手动点刷新
4. **受控写入与联网** —— 最后一档，必须带权限、确认、限频

每批只开一个主题，配自测、示例插件、文档。不要一次全开。

---

## 1. 现在有什么（v1 全貌）

| 层 | 现在开放的东西 | 位置 |
| --- | --- | --- |
| 插件契约 | WristPlugin（manifest / install / uninstall） | WristPlugin.kt |
| 宿主能力 | pages、buttons、data、events、invalidate、toast、openPage、openHostPage | WristPlugin.kt 的 PluginHost |
| 读数据 | notes()、tags()、kv()（插件私有存储） | DataApi.kt |
| 事件 | onNoteSaved、onPageShown | DataApi.kt 的 EventBus |
| 页面 | PageSpec（Compose，仅 Kotlin 作者）、ListPageSpec（懒加载）、SimplePage（Java 可写） | PageSpec.kt / SimplePage.kt |
| 按钮 | 三个插槽，各上限 2 | ButtonSpec.kt |
| 官方组件 | Page / Title / Button / Stat / ScreenRow / Confirm | Official.kt |
| 官方页面 | 8 个只读入口（关于、自测、开源、插件、时间表管理、特殊时段、发到手机、动画） | HostScreen.kt |
| 权限枚举 | READ_NOTES、WRITE_NOTES、READ_SETTINGS（WRITE_NOTES 还没实现） | PluginManifest.kt |
| 硬限制 | 每插件最多 3 页、页面 id 必须带插件前缀、插槽上限 2、连续 3 次失败熔断 | PluginCore.kt |

**结论：现在这套 API 能做的插件类型只有一种——读笔记 + 显示统计 + 放几个按钮。**

---

## 2. 作者真正缺什么（按痛点排序）

1. **读不到自己想用的数据**：待办、课程表、主题设置都读不到；做「今天几节课」「还剩几个待办」这类最常见的手表插件，数据拿不到
2. **页面太素**：SimplePage 只有 stat 和 button，做进度、分组、列表都不行；Java 作者又没有 Compose 可用
3. **不会自己刷新**：只有 noteSaved 和 pageShown 两个事件，待办完成、主题切换、跨天都不会通知插件
4. **不能联网、不能写**：想做个天气、倒数日、单词卡，一个都做不了
5. **图表和输入看起来要自由画布，其实不用**：环形进度、柱状对比、数字步进，都可以做成参数化官方组件

---

## 3. 三批路线

| 批次 | 主题 | 内容 | 风险 | 建议版本 |
| --- | --- | --- | --- | --- |
| A | 只读数据 + 官方组件 + 事件 | 读待办/课程表/设置，补 6~8 个组件，补 4 个事件 | 极低（不写数据、不联网） | 4.1.27 / API_LEVEL 2 |
| B | 受控写入 + 网络代理 + 提醒 | 只能改自己创建的对象、宿主代理 HTTP、宿主统一提醒 | 中（要权限、确认、限频） | 4.2.0 / API_LEVEL 3 |
| C | 参数化图表 + 输入页 + 图片资源 | 环形/条形/折线组件，数字与时间选择器，包内图片 | 低到中（都不给自由画布） | 4.2.x / API_LEVEL 4 |

### 批次 A（建议先做）：只读 + 组件 + 事件

#### A1. 读数据扩展（DataApi）

    interface DataApi {
        // 已有
        fun notes(): List<Note>
        fun tags(): List<String>
        fun kv(): KeyValueStore

        // 新增：待办（只读快照，按截止时间升序）
        fun todos(includeDone: Boolean = false): List<TodoSnap>
        fun todoCounts(): TodoCounts          // 未完成 / 今天到期 / 已过期

        // 新增：课程表（按当天算好的结果，插件不用自己算单双周）
        fun todaySlots(): List<SlotSnap>
        fun slotsOf(dayOfWeek: Int): List<SlotSnap>

        // 新增：设置只读子集（不含笔记正文、不含备份）
        fun settings(): SettingsSnap          // 主题色、震动开关、顶部时间、分类列表
    }

快照类型（全是不可变 data class，插件拿不到 Store）：

    data class TodoSnap(val id: Long, val title: String, val noteTitle: String,
                        val startAt: Long, val done: Boolean, val tag: String?)
    data class SlotSnap(val title: String, val startMinute: Int, val endMinute: Int,
                        val repeat: String)   // 单双周已经算好
    data class TodoCounts(val pending: Int, val dueToday: Int, val overdue: Int)
    data class SettingsSnap(val accentColor: Int, val haptics: Boolean,
                            val showTopTime: Boolean, val categories: List<String>)

要点：

- 每次调用返回**拷贝**，插件改不动宿主数据
- 加**查询上限**：默认最多返回 200 条，避免在 render() 里每帧复制整个库（见 A4）
- 权限：READ_TODOS / READ_TIMETABLE / READ_SETTINGS，没申请就抛 SecurityException（和现在 notes() 一样）

#### A2. 事件扩展（EventBus）

    interface EventBus {
        fun onNoteSaved(listener: (Note) -> Unit): () -> Unit      // 已有
        fun onPageShown(listener: (String) -> Unit): () -> Unit    // 已有

        // 新增
        fun onNoteChanged(listener: (NoteEvent) -> Unit): () -> Unit
        fun onTodoChanged(listener: (TodoEvent) -> Unit): () -> Unit
        fun onThemeChanged(listener: (Int) -> Unit): () -> Unit     // 新主题色
        fun onDayChanged(listener: () -> Unit): () -> Unit          // 跨天，一天一次
    }

规矩写进文档：

- 回调**只在主线程**触发
- 回调里抛异常：宿主捕获、记日志、不传到 App 崩溃；同一插件连续抛 3 次直接熔断（和现在 install 失败同一套策略）
- 回调里**不许** toast / 开页面 / 写数据（避免重入），只能改自己的 kv 并 invalidate()

#### A3. 组件扩展（Official + SimplePage）

    object Official {
        // 已有：Page / Title / Button / Stat / ScreenRow / Confirm

        fun Header(text: String)                                  // 分组小标题
        fun Progress(label: String, value: Int, max: Int, trailing: String? = null)
        fun Value(label: String, big: String, sub: String? = null) // 中间大数字
        fun TwoLineRow(symbol: String, title: String, sub: String, onClick: (() -> Unit)? = null)
        fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit)
        fun ColorDot(color: Int, label: String, trailing: String? = null)
        fun Divider()
    }

SimplePage 同步扩展（让 Java/dex 作者也能用）：

    page.header("今天")
        .progress("已完成", 3, 5, "60%")
        .value("待办", "3", "共 5 条")
        .twoLine("▤", "高数", "08:00-09:40", "openSlot")   // 带 actionId，点了回调
        .chips(new String[]{"全部", "未完成"}, 0, "filter")
        .divider();

新增行类型：HEADER / PROGRESS / VALUE / TWOLINE / CHIP / DIVIDER。所有行仍由宿主渲染，插件还是只交数据。

**落地情况（4.1.27 / 插件模块 0.2）**：这一批做完了，但命名按「宿主里已经有的组件」来，不新造概念：

| 本稿草案 | 实际落地 |
| --- | --- |
| `Header(text)` | `Section(text)`（小节标题；另有 `Note(text)` 说明文字） |
| `Progress(label, value, max, trailing)` | `Progress(label, value: Long, total: Long, trailing)`，另加 `Ring` 环形进度 |
| `Value(label, big, sub)` | `Hero(label, value, sub)`（大号数字主指标） |
| `TwoLineRow(symbol, title, sub, onClick)` | `Item(symbol, title, sub, trailing, onClick)` |
| `ChipRow(options, selected, onSelect)` | `Chips(label, options, selected, onSelect)`，每行最多 4 个自动折行 |
| `ColorDot(color, label, trailing)` | 没做圆点，改成 `ColorCard(label, colorHex, trailing, active, onClick)`（整行彩色卡片，复用了主题颜色页的官方卡片） |
| `Divider()` | `Divider()` / `Space(height)` / `Empty(text)` |

草案之外还多给了 4 种：`Stepper`（数值步进）、`Toggle`（开关行）、`TimeCard`（时间范围卡）、`Bars`（柱状图）——
都是宿主里已经在用的组件，开出来零新风险。带参数行的回调编号定为契约（`id:i` / `id:±n` / `id:on|off`），
SimplePage 同步扩充（Java 可用，选项/柱状另给字符串重载），API_LEVEL 1→2。仍守住「行由宿主渲染」。

#### A4. 顺手要补的三处坑

1. **DataApi 每次全量复制**：SimplePage.render() 每次重画都会调 notes()，插件写个「笔记统计」就会每次复制整库。建议加 notes(limit, offset) 和 counts()，文档写清「render 里只调轻量方法」
2. **事件回调线程没写清**：现在只写「别忘了取消订阅」，没说在哪个线程。补：主线程、抛异常处理、禁止重入
3. **onPageShown 只传 pageId 字符串**：插件得自己判断是不是自己的页。建议改成 PageShownEvent(pageId, isOwner)

### 批次 B：受控写入 + 网络代理 + 提醒

#### B1. 写数据：只允许「自己创建的对象」

绝不给「改用户已有笔记」的权限。做法是加 owner 字段：

    interface WriteApi {
        fun createNote(title: String, body: String, category: String?): Note   // 记 createdBy = 插件 id
        fun createTodo(title: String, startAt: Long, endAt: Long): TodoSnap
        fun completeMyTodo(id: Long)
        fun updateMyNote(id: Long, title: String, body: String)
    }

规矩：

- 只能改 createdBy 是自己的对象，改别人的直接拒绝并写日志
- 删除一律留给用户（插件最多把内容清空）
- 每次写都要用户确认（复用 Official.Confirm），且每分钟限 10 次
- 笔记列表里给插件创建的笔记加来源标记

#### B2. 网络：宿主代理，不给原始 socket

    interface NetApi {
        fun get(url: String, maxBytes: Int = 64 * 1024): NetResult
        fun postJson(url: String, body: String): NetResult
    }

    sealed interface NetResult {
        data class Ok(val status: Int, val body: String) : NetResult
        data class Failed(val reason: String) : NetResult
    }

规矩：

- plugin.json 里声明 allowedHosts，宿主做白名单，子域名不算
- 只允许 https，超时 5 秒，响应上限 64KB，不自动重试
- install() 里禁止联网（没有上下文），只能在页面/按钮/事件回调里调
- 每次请求写 EventLog（插件 id + host + 状态码，不记 body）

#### B3. 提醒：交给宿主统一发

    interface ReminderApi {
        fun schedule(atMillis: Long, title: String, body: String): Long  // 返回 id
        fun cancel(id: Long)
        fun pending(): List<ReminderSnap>
    }

宿主统一做通知、振动、亮屏；每插件最多 3 条待触发；用户能在插件详情里看到并清空。

#### B4. 包内图片资源

    interface AssetApi {
        fun image(name: String): ImageBitmap?   // 从插件包 assets 读，最大 256KB / 1024px
    }

### 批次 C：参数化图表 + 输入

- Official.Ring(label, value, max)：环形进度（统计页那张圆环同款）
- Official.Bars(items)：横向条形对比，最多 8 条
- Official.Line(points)：迷你折线，最多 32 点
- Official.NumberPicker / TimePicker / DatePicker：打开宿主的选择页，选完回调

这些都还是「插件交数据、宿主画」，不给 Canvas，也不给自定义 View。

---

## 4. 不要开的（这条线比开什么更重要）

| 不给 | 原因 |
| --- | --- |
| 自由画布 / 任意 Compose / 自定义 View | 圆屏布局、字号、返回键立刻失控，插件越多越乱 |
| Activity / Service / 后台常驻 | 手表续航和内存都撑不住，也无法审计 |
| 直接文件读写 / 原始 socket | 越过权限模型，卸载也清不干净 |
| 直接发通知 | 用户会被插件轰炸，必须由宿主统一发 |
| 跨插件互相调用 | 插件间耦合后，卸载顺序、版本兼容全是坑 |
| 改设置正文 / 备份恢复 / 清空数据 | 一个插件写错就能毁数据 |
| 访问其他插件的 kv | 现在按 pluginId 隔离，这条不能破 |

---

## 5. 治理三件套（每开一批必须一起做）

### 5.1 能力查询，别让插件猜

    enum class Capability { READ_TODOS, READ_TIMETABLE, READ_SETTINGS, WRITE_OWN, NET, REMINDER }

    interface PluginHost {
        fun supports(capability: Capability): Boolean
    }

用处：老宿主上新插件不会崩、插件自己降级；宿主以后收紧某个能力，插件能感知。

### 5.2 权限到能力清单，安装时就给用户看

现在安装预览已经显示「权限：读笔记」，扩成完整清单：

- 读笔记 / 读待办 / 读课程表 / 读设置
- 创建自己的笔记 / 待办
- 联网（列域名）
- 发提醒

用户拒绝某项就不给这个能力，插件仍可安装（降级），而不是整包装不上。

### 5.3 每个新 API 的验收清单

1. 自测里加对应项（现在 32 项，每批预计 +4~6 项）
2. external-sample 里加一个真用这个 API 的 Java 示例插件
3. 插件作者手册补一条（签名 + 限制 + 失败表现）
4. 版本说明写清「这一版新开了什么、API_LEVEL 升到几」
5. 插件模块-API设计.md 同步改，别让代码和文档两条线

### 5.4 版本与兼容

- 每次开一批：API_LEVEL + 1，旧插件不用改
- plugin.json 的 minApi 继续生效：minApi 高于宿主的包直接拒绝安装（现在已有）
- 0.x 阶段允许破坏性变更，但每次都要在版本说明里单独列一段「这次改了什么旧接口」
- 到了 1.0 再承诺不破坏

---

## 6. 我的建议（如果只做一件事）

**先做批次 A 的 A1 + A3，一个版本（4.1.27）就能出：**

- A1 读数据扩展 → 插件从「只能做笔记统计」变成「能做今天课程 / 待办倒计时 / 分类分布」
- A3 组件扩展 → Java 作者能写出进度、分组、两行列表这种像样的页面
- 这两块**不写数据、不联网**，风险接近零，但作者能做的事情翻几倍

下一版再做 A2（事件）+ B1（受控写入），最后才是网络和提醒。

一句话记法：**先让插件「看得更多」（读 + 显示），再让它「动得了」（写 + 联网）。**
