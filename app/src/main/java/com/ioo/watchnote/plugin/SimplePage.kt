package com.ioo.watchnote.plugin

/**
 * 声明式页面的按钮回调。
 *
 * 为什么不用 Kotlin 的 (String) -> Unit：外置插件是 Java 写的，Function1 得写
 * "return kotlin.Unit.INSTANCE"，太容易写错；这里给一个 void 的 fun interface，
 * Java 里直接 lambda 就行：
 *
 *     page.onAction(id -> host.toast("点了 " + id));
 */
fun interface SimpleAction {
    fun onAction(actionId: String)
}

/**
 * 声明式页面（4.1.21 起）：插件只交「有哪些行」，宿主用官方组件画。
 *
 * 为什么需要它：PageSpec 的 content 是 @Composable 函数，只有 Kotlin + Compose 编译器写得出来；
 * 而外置插件是独立编译的 dex（javac + d8，见 docs/external-sample），画不了 Compose。
 * 如果页面 API 只留 PageSpec，那么「带页面的插件」就只有能引 Compose 编译器的作者写得了。
 *
 * 所以给这类插件一条只交数据的路：写几行统计、几个按钮、一个回调，
 * 宿主在 PluginPageHost 里用 Official.Page / Official.Stat / Official.Button 渲染成官方样子，
 * 圆屏边距、返回键、主题色、鱼眼动画全部和内置页一致 —— 插件不画自由画布这条规矩没变。
 *
 * 规矩照旧：页面 id 必须带自己插件 id 加冒号前缀、每插件最多 3 页、卸载/关掉时一起收走。
 *
 * Java 示例：
 *
 *     host.getPages().add(
 *         new SimplePage("com.example.page:main", "示例页面")
 *             .sub("外置插件")
 *             .stat("宿主笔记", String.valueOf(host.getData().notes().size()))
 *             .button("＋", "计数加一", null, "inc")
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

    /** 标题下面那行小字。 */
    fun sub(text: String): SimplePage {
        subtitle = text
        return this
    }

    /** 一行只读统计：左边标签、右边数值，和官方 Stat 同一种行。 */
    fun stat(label: String, value: String): SimplePage {
        rows.add(SimpleRow(SimpleRowKind.STAT, label, value, "", "", false))
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
        rows.add(SimpleRow(SimpleRowKind.BUTTON, label, trailing ?: "", symbol, actionId ?: label, danger))
        return this
    }

    /** 页面上按钮的点按回调：参数是那个按钮的 actionId。 */
    fun onAction(action: SimpleAction): SimplePage {
        handler = action
        return this
    }

    internal fun dispatch(actionId: String) {
        handler?.onAction(actionId)
    }
}

internal enum class SimpleRowKind { STAT, BUTTON }

internal class SimpleRow(
    val kind: SimpleRowKind,
    val label: String,
    val value: String,
    val symbol: String,
    val actionId: String,
    val danger: Boolean,
)
