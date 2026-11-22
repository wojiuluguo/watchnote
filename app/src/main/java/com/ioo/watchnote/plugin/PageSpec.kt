package com.ioo.watchnote.plugin

import androidx.compose.runtime.Composable

/** 页面层级。v1 只支持 SUB/LIST（从别的页面点进去），ROOT 留给以后接主页。 */
enum class PageKind { ROOT, SUB, LIST }

/**
 * 插件页面的唯一出口。插件只能关自己、开自己的另一个页面，
 * 碰不到宿主导航栈，也就不会把别人的页面顶掉。
 */
interface PageScope {
    /** 关掉当前页，回上一层。 */
    fun close()

    /** 打开本插件的另一个页面；arg 是给那个页面的一个 Int 参数（比如第几个色系）。 */
    fun open(pageId: String, arg: Int = 0)
}

/**
 * 页面规格的公共部分。宿主只认这四样（id/标题/层级/排序），
 * 不关心插件交的是内容页还是列表页。
 *
 * 插件不直接实现它：用下面两个具体类型之一。
 */
sealed interface PluginPageSpec {
    val id: String
    val title: String
    val kind: PageKind
    val order: Int
}

/**
 * 内容页（默认那种）：一屏内容从上往下排，宿主负责背景、返回键、圆屏安全区、表冠滚动。
 * 适合固定几行文字/统计/按钮；**行数会变多、要滚很久的内容用 ListPageSpec**。
 */
data class PageSpec(
    override val id: String,
    override val title: String,
    override val kind: PageKind = PageKind.SUB,
    override val order: Int = 0,
    val content: @Composable (scope: PageScope, arg: Int) -> Unit,
) : PluginPageSpec

/**
 * 列表页（4.1.7 起）：和宿主自己的笔记/办事页同一套懒加载列表。
 *
 * 为什么单独一种：内容页的 content 是普通 Column，条目多起来会一次性量完所有行；
 * 列表页交给宿主 TransformingLazyColumn，只渲染屏幕附近那几行，条目再多也不卡。
 *
 * 写法和普通 lazy 列表一样，只是多了两个参数：
 *
 *     host.pages.add(
 *         ListPageSpec(id = manifest.id + ":tags", title = "分类分布") { list, scope, _ ->
 *             list.item { Official.Title("分类分布") }
 *             tags.forEach { tag ->
 *                 list.item(key = "tag-" + tag) { Official.Stat(tag, "3") }
 *             }
 *         }
 *     )
 *
 * 行外壳（圆屏边距、鱼眼动画）由宿主的 PluginListScope 负责，插件只管行里画什么。
 */
/**
 * 列表页里加行的出口。宿主用它包好 item 外壳和鱼眼动画，
 * 插件不用碰 lazy 的细节，也就写不出「一行没动画」的页面。
 */
interface PluginListScope {
    /** 加一行；key 一般传这条数据自己的 id，刷新时靠它保持条目状态。 */
    fun item(key: Any? = null, content: @Composable () -> Unit)

    /** 加 count 行（内部就是按序号循环 item），适合纯粹的下标列表。 */
    fun items(count: Int, key: ((Int) -> Any)? = null, itemContent: @Composable (Int) -> Unit)
}

data class ListPageSpec(
    override val id: String,
    override val title: String,
    override val order: Int = 0,
    val content: (list: PluginListScope, scope: PageScope, arg: Int) -> Unit,
) : PluginPageSpec {
    override val kind: PageKind get() = PageKind.LIST
}

/** 注册页面/按钮的结果。被拒时原因会写清楚，插件自己看得见。 */
sealed interface RegisterResult {
    object Ok : RegisterResult
    data class Rejected(val reason: String) : RegisterResult
}
