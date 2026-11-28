package com.ioo.watchnote.plugin

/**
 * 声明式页面的按钮回调。
 *
 * 为什么不用 Kotlin 的 (String) -> Unit：外置插件是 Java 写的，Function1 得写
 * "return kotlin.Unit.INSTANCE"，太容易写错；这里给一个 void 的 fun interface，
 * Java 里直接 lambda 就行：
 *
 *     page.onAction(id -> host.toast("点了 " + id));
 *
 * 0.2 起带参数的行会在 id 后面接冒号（约定见 SimplePage 的 KDoc）：
 * 选项行 "选项id:2"、步进行 "步进id:+1" / "步进id:-1"、开关行 "开关id:on" / "开关id:off"。
 */
fun interface SimpleAction {
    fun onAction(actionId: String)
}

/**
 * 声明式页面的「按最新数据重画」回调（4.1.22 起）。
 *
 * 只注册一次 stat/button 的话，行内容是注册那一刻算好的：插件数据后来变了、
 * 调 host.invalidate() 让宿主重画，页面上还是旧数字（4.1.21 的示例页面就踩了这个坑：
 * 点一次「计数加一」toast 从 0 数到 10，页面上的数字还是 0）。
 * 给一个 renderer 后，宿主每次重画这一页都会重新调一次，插件在里面读最新数据、重新给行。
 *
 * 规矩：这里只读数据、只往 p 里写行；不要 toast / 改数据 / 开页面 —— 页面重画就会被重复触发。
 *
 * Java：
 *
 *     page.render(p -> {
 *         p.stat("插件计数", String.valueOf(kv.getInt("clicks", 0)));
 *         p.button("＋", "计数加一", null, "inc");
 *     });
 */
fun interface SimplePageRenderer {
    fun render(page: SimplePage)
}

/**
 * 声明式页面（4.1.21 起；0.2 起把整套官方 UI 都变成行）：插件只交「有哪些行」，宿主用官方组件画。
 *
 * 为什么需要它：PageSpec 的 content 是 @Composable 函数，只有 Kotlin + Compose 编译器写得出来；
 * 而外置插件是独立编译的 dex（javac + d8，见 docs/external-sample），画不了 Compose。
 * 如果页面 API 只留 PageSpec，那么「带页面的插件」就只有能引 Compose 编译器的作者写得了。
 *
 * 所以给这类插件一条只交数据的路：写几行官方 UI，宿主在 PluginPageHost 里用本文件上面那些
 * Official.* 同一份实现渲染，圆屏边距、返回键、主题色、鱼眼动画全部和内置页一致 ——
 * 插件不画自由画布这条规矩没变。
 *
 * 0.2 新增行类型（都会返回 SimplePage，可以链式往下写）：
 *
 *     section(text)                         小节标题（11sp 灰字）
 *     note(text)                            一段说明文字
 *     hero(label, value, sub)               大数字卡片（中间一行大字）
 *     item(symbol, title, sub, trailing, id) 两行列表行（上面标题、下面小字）
 *     progress(label, done, total)          线形进度条
 *     ring(label, done, total)              环形进度（圆屏上一眼能看懂）
 *     stepper(label, value, min, max, step, id)  −/+ 数值步进
 *     chips(label, options, selected, id)   一排可选项（窄屏自动折行，每行最多 4 个）
 *     colorCard(label, colorHex, trailing, active, id)  整块彩色行（色号 #RRGGBB）
 *     timeCard(start, end, trailing, id)    开始/截止两行的时间卡（毫秒时间戳）
 *     toggle(label, on, id)                 开关行（右侧显示 开/关）
 *     divider() / space(dp)                 分割线 / 空白
 *     empty(text)                          空状态文字
 *     bars(label, values, trailing)         柱状小图（0.1 到 1 的相对高度也行，自动按最大值缩放）
 *
 * 带参数的行（stepper / chips / toggle）点下去回调里收到的 actionId 后面接冒号：
 *
 *     chips   选中第 2 个       -> "选项id:2"
 *     stepper 点了 +            -> "步进id:+1"   （- 就是 "步进id:-1"）
 *     toggle  打开了            -> "开关id:on"   （关掉是 "开关id:off"）
 *
 * 其余行（item / colorCard / timeCard / button）点下去就是原样的 actionId。
 *
 * 规矩照旧：页面 id 必须带自己插件 id 加冒号前缀、每插件最多 3 页、卸载/关掉时一起收走。
 *
 * Java 示例：
 *
 *     host.getPages().add(
 *         new SimplePage("com.example.page:main", "示例页面")
 *             .sub("外置插件")
 *             .section("今天")
 *             .hero("喝水", "3 杯", "目标 8 杯")
 *             .progress("完成度", 3, 8)
 *             .stepper("杯数", 3, 0, 20, 1, "cups")
 *             .button("＋", "记一杯", null, "inc")
 *             .onAction(id -> host.toast("点了 " + id))
 *     );
 */
class SimplePage @JvmOverloads constructor(
    override val id: String,
    override val title: String,
    override val order: Int = 0,
) : PluginPageSpec {

    override val kind: PageKind get() = PageKind.SUB

    internal var subtitle: String? = null
    internal val rows = ArrayList<SimpleRow>()
    private var handler: SimpleAction? = null
    private var renderer: SimplePageRenderer? = null

    /** 标题下面那行小字。 */
    fun sub(text: String): SimplePage {
        subtitle = text
        return this
    }

    /** 一行只读统计：左边标签、右边数值，和官方 Stat 同一种行。 */
    fun stat(label: String, value: String): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.STAT, label = label, value = value))
        return this
    }

    /** 一行按钮：symbol 是左边那个符号，actionId 是点下去回调里收到的值（不给就用 label）。 */
    @JvmOverloads
    fun button(
        symbol: String,
        label: String,
        trailing: String? = null,
        actionId: String? = null,
        danger: Boolean = false,
    ): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.BUTTON,
                label = label,
                value = trailing ?: "",
                symbol = symbol,
                actionId = actionId ?: label,
                danger = danger,
            )
        )
        return this
    }

    /** 小节标题：一组行前面的灰色小标题，和官方设置页的分节一致。 */
    fun section(text: String): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.SECTION, label = text))
        return this
    }

    /** 一段说明文字：比正文小一号，用来解释这一页在干什么。 */
    fun note(text: String): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.NOTE, label = text))
        return this
    }

    /** 大数字卡片：标签 + 中间一行大字 + 可选副标题，适合「今天 3 杯」这类主指标。 */
    @JvmOverloads
    fun hero(label: String, value: String, sub: String? = null): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.HERO, label = label, value = value, sub = sub ?: ""))
        return this
    }

    /** 两行列表行：左边符号、上面标题、下面小字、右边状态；给了 actionId 才可点。 */
    @JvmOverloads
    fun item(
        symbol: String,
        title: String,
        sub: String? = null,
        trailing: String? = null,
        actionId: String? = null,
    ): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.ITEM,
                symbol = symbol,
                label = title,
                sub = sub ?: "",
                value = trailing ?: "",
                actionId = actionId ?: title,
            )
        )
        return this
    }

    /** 线形进度条：左边标签、右边百分比，下面一根进度条和 已完成/总量。 */
    fun progress(label: String, done: Long, total: Long): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.PROGRESS, label = label, done = done, total = total))
        return this
    }

    /** 环形进度：一个圆环 + 中间百分比，圆屏上最省地方。 */
    fun ring(label: String, done: Long, total: Long): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.RING, label = label, done = done, total = total))
        return this
    }

    /** 数值步进：左边标签，右边 − 数值 +；点一下按 step 加减，结果夹在 min..max。 */
    @JvmOverloads
    fun stepper(
        label: String,
        value: Int,
        min: Int = 0,
        max: Int = 999,
        step: Int = 1,
        actionId: String,
    ): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.STEPPER,
                label = label,
                current = value,
                min = min,
                max = max,
                step = step,
                actionId = actionId,
            )
        )
        return this
    }

    /**
     * 一排可选项：label 是上面的小标题（可以不传空串），选中项高亮。
     * 点第 i 个（从 0 数）回调收到 "actionId:i"。
     */
    fun chips(label: String, options: List<String>, selected: Int, actionId: String): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.CHIPS,
                label = label,
                options = ArrayList(options),
                selected = selected,
                actionId = actionId,
            )
        )
        return this
    }

    /** 同上，Java 里不想自己拼 List 时用这个：选项用竖线隔开，例如 "低|中|高"。 */
    fun chips(label: String, optionsCsv: String, selected: Int, actionId: String): SimplePage =
        chips(label, optionsCsv.split("|").map { it.trim() }.filter { it.isNotEmpty() }, selected, actionId)

    /** 整块彩色行：底色就是 colorHex（#RRGGBB），点一下回调 actionId；active 时右侧显示使用中。 */
    @JvmOverloads
    fun colorCard(
        label: String,
        colorHex: String,
        trailing: String? = null,
        active: Boolean = false,
        actionId: String? = null,
    ): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.COLOR,
                label = label,
                value = trailing ?: "使用中",
                colorHex = colorHex,
                active = active,
                actionId = actionId ?: label,
            )
        )
        return this
    }

    /** 时间摘要卡：第一行 开始时间、第二行 截止时间（都是毫秒时间戳），点一下回调 actionId。 */
    @JvmOverloads
    fun timeCard(
        start: Long,
        end: Long,
        trailing: String? = null,
        actionId: String? = null,
    ): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.TIME,
                start = start,
                end = end,
                value = trailing ?: "查看",
                actionId = actionId ?: "time",
            )
        )
        return this
    }

    /** 开关行：右侧显示 开/关；点一下回调 "actionId:on" 或 "actionId:off"（目标是切换后的状态）。 */
    fun toggle(label: String, on: Boolean, actionId: String): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.TOGGLE, label = label, active = on, actionId = actionId))
        return this
    }

    /** 一条分割线。 */
    fun divider(): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.DIVIDER))
        return this
    }

    /** 空白：高度 dp 像素，用来把内容推开一点。 */
    fun space(dp: Int): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.SPACE, amount = dp))
        return this
    }

    /** 空状态文字：居中一行灰字，比如「还没有数据」。 */
    fun empty(text: String): SimplePage {
        rows.add(SimpleRow(kind = SimpleRowKind.EMPTY, label = text))
        return this
    }

    /**
     * 柱状小图：values 是每根柱子的数值（相对高度，自动按最大值缩放），
     * trailing 是右边一句话（比如 "峰值 5"）。Java 可以用 barsCsv 传 "1,5,3"。
     */
    @JvmOverloads
    fun bars(label: String, values: List<Float>, trailing: String? = null): SimplePage {
        rows.add(
            SimpleRow(
                kind = SimpleRowKind.BARS,
                label = label,
                numbers = ArrayList(values),
                value = trailing ?: "",
            )
        )
        return this
    }

    /** 同上，Java 里用逗号隔开的数字串，例如 "1,5,3"。 */
    fun bars(label: String, valuesCsv: String, trailing: String?): SimplePage =
        bars(
            label,
            valuesCsv.split(",").mapNotNull { it.trim().toFloatOrNull() },
            trailing,
        )

    /** 页面上按钮的点按回调：参数是那个按钮的 actionId（带参数的行走上面写的冒号约定）。 */
    fun onAction(action: SimpleAction): SimplePage {
        handler = action
        return this
    }

    internal fun dispatch(actionId: String) {
        handler?.onAction(actionId)
    }

    internal fun dispatchIndexed(actionId: String, index: Int) {
        dispatch(actionId + ":" + index)
    }

    internal fun dispatchDelta(actionId: String, delta: Int) {
        dispatch(actionId + ":" + (if (delta > 0) "+" else "") + delta)
    }

    internal fun dispatchToggle(actionId: String, on: Boolean) {
        dispatch(actionId + if (on) ":on" else ":off")
    }

    /**
     * 让页面按最新数据重画（4.1.22 起）：宿主每次渲染这一页都会重新调一次 renderer，
     * 行来自这次回调；插件数据变了只要 host.invalidate()，页面上的数字就会跟着变。
     */
    fun render(renderer: SimplePageRenderer): SimplePage {
        this.renderer = renderer
        return this
    }

    /**
     * 渲染前取一次「当前该画的行」：没有 renderer 就是注册时那些静态行；
     * 有 renderer 就在一个临时 SimplePage 上重新生成。插件抛异常时退回静态行 ——
     * 页面不能因为插件写错渲染回调就崩掉或全白。
     */
    internal fun renderOnce(): SimplePageRender {
        val r = renderer ?: return SimplePageRender(subtitle, rows)
        val fresh = SimplePage(id, title, order)
        return try {
            r.render(fresh)
            SimplePageRender(fresh.subtitle, fresh.rows)
        } catch (t: Throwable) {
            SimplePageRender(subtitle, rows)
        }
    }
}

/** 一次渲染用到的数据：标题下的小字 + 这一帧要画的行。 */
internal class SimplePageRender(val subtitle: String?, val rows: List<SimpleRow>)

/** 声明式页面支持的行类型（0.2 起覆盖整套官方 UI 行）。 */
internal enum class SimpleRowKind {
    SECTION,
    NOTE,
    STAT,
    HERO,
    BUTTON,
    ITEM,
    PROGRESS,
    RING,
    STEPPER,
    CHIPS,
    COLOR,
    TIME,
    TOGGLE,
    DIVIDER,
    SPACE,
    EMPTY,
    BARS,
}

/**
 * 一行声明式数据。字段是所有行类型的并集，各类型只用自己那几个：
 * 这样插件 ABI 上只有一个类，加新行类型不会多出类名。
 */
internal class SimpleRow(
    val kind: SimpleRowKind,
    val label: String = "",
    val value: String = "",
    val symbol: String = "",
    val actionId: String = "",
    val danger: Boolean = false,
    val sub: String = "",
    val active: Boolean = false,
    val done: Long = 0L,
    val total: Long = 0L,
    val options: List<String> = emptyList(),
    val selected: Int = 0,
    val current: Int = 0,
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 1,
    val numbers: List<Float> = emptyList(),
    val colorHex: String = "",
    val start: Long = 0L,
    val end: Long = 0L,
    val amount: Int = 0,
)
