package com.ioo.watchnote.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme

/**
 * 插件页面（0.4.5 起）。
 *
 * 一页 = 一棵节点树。节点可以是你自己的容器（横排/竖排/叠放/分组/网格/滚动）、
 * 官方组件（section、按钮、进度、环、列表项……）、画布（元素画布或整页画布）。
 * 三类节点可以任意混排在同一层，没有「选了画布就不能用官方行」这回事。
 *
 * 排布两种并存：
 * - 自动：容器按顺序从上往下 / 从左往右摆；
 * - 指定：节点自己定 height / pad / weight，容器里第几个就是第几个，属于哪个容器就跟着哪个容器走。
 */
class UIPage @JvmOverloads constructor(
    override val id: String,
    override val title: String,
    override val order: Int = 0,
) : PluginPageSpec {

    override val kind: PageKind get() = PageKind.SUB

    internal val root: UINode = Ui.column()
    internal var subtitle: String? = null
    internal var fullScreen: Boolean = false
    internal var mainPage: Boolean = false
    private var handler: ((String) -> Unit)? = null

    /** 页面副标题（官方页眉下面那行小字）。 */
    fun sub(text: String): UIPage {
        subtitle = text
        return this
    }

    /** 整页模式：不要官方页眉，整页交给根节点（一般就是一块 FILL 画布）。 */
    fun fullPage(): UIPage {
        fullScreen = true
        return this
    }

    /** 声明成插件的主页面：会占指示栏第 6 格（全 App 最多一个插件主页显示在这一格）。 */
    fun asMainPage(): UIPage {
        mainPage = true
        return this
    }

    /** 往页面里加节点。（根节点默认是一个竖排容器，从上往下） */
    fun add(node: UINode): UIPage {
        root.children.add(node)
        return this
    }

    fun add(vararg nodes: UINode): UIPage {
        nodes.forEach { root.children.add(it) }
        return this
    }

    /** 点中官方行 / 画布里挂的 actionId 时回调到这里。 */
    fun onAction(block: (String) -> Unit): UIPage {
        handler = block
        return this
    }

    internal fun dispatch(actionId: String) {
        if (actionId.isEmpty()) return
        runCatching { handler?.invoke(actionId) }
    }
}

internal enum class NodeKind {
    COLUMN, ROW, BOX, GROUP, GRID, SCROLL, SPACER, DIVIDER,
    OFFICIAL, CANVAS, CUSTOM,
}

/** 官方行的种类（跟 18 种老行一一对应，但这里直接落到 Official 组件）。 */
internal enum class OfficialKind {
    SECTION, NOTE, STAT, HERO, BUTTON, ITEM, PROGRESS, RING, STEPPER,
    CHIPS, COLOR, TIME, TOGGLE, EMPTY, BARS, SHAPE,
}

/**
 * 一个节点。不要直接 new，用 [Ui] 里的工厂方法；返回的都是同一个对象，可以继续链式配置：
 *
 *     page.add(Ui.canvas(56, pen -> pen.text("打卡", 0.5f, 0.5f)).weight(1f))
 */
class UINode internal constructor(internal val kind: NodeKind) {

    internal val children = ArrayList<UINode>()
    internal var weight: Float = 0f
    internal var heightDp: Int = 0
    internal var padDp: Int = 0
    internal var spacingDp: Int = 6
    internal var cols: Int = 2
    internal var align: Int = 0
    internal var scrollable: Boolean = true

    internal var official: OfficialKind = OfficialKind.NOTE
    internal var label: String = ""
    internal var value: String = ""
    internal var sub: String = ""
    internal var symbol: String = ""
    internal var trailing: String = ""
    internal var colorHex: String = ""
    internal var actionId: String = ""
    internal var danger: Boolean = false
    internal var active: Boolean = false
    internal var on: Boolean = false
    internal var done: Long = 0L
    internal var total: Long = 0L
    internal var current: Int = 0
    internal var min: Int = 0
    internal var max: Int = 100
    internal var step: Int = 1
    internal var options: List<String> = emptyList()
    internal var selected: Int = 0
    internal var numbers: List<Float> = emptyList()
    internal var shape: String = "circle"
    internal var sizeDp: Int = 56
    internal var start: String = ""
    internal var end: String = ""

    internal var draw: DrawBlock? = null
    internal var tap: TapBlock? = null
    internal var drag: DragBlock? = null
    internal var fill: Boolean = false

    /** 占剩余空间的比例（在横排/竖排容器里有效）。 */
    fun weight(w: Float): UINode {
        weight = w
        return this
    }

    /** 高度（dp）。画布用它定尺寸；0 表示自适应。 */
    fun height(dp: Int): UINode {
        heightDp = dp
        return this
    }

    /** 内边距（dp）。 */
    fun pad(dp: Int): UINode {
        padDp = dp
        return this
    }

    /** 子节点间距（dp）。 */
    fun spacing(dp: Int): UINode {
        spacingDp = dp
        return this
    }

    /** 网格列数。 */
    fun columns(n: Int): UINode {
        cols = n.coerceIn(1, 4)
        return this
    }

    /**
     * 这一块被点中时上报的 actionId（官方行和画布都能用）。
     * 画布已经有 onTap 时，这里是兜底：点在没挂命中区的空白处也会报这个。
     */
    fun action(id: String): UINode {
        actionId = id
        return this
    }

    /** 叠放容器里的对齐：0 左上、1 中上、2 右上、3 左中、4 居中、5 右中、6 左下、7 中下、8 右下。 */
    fun align(pos: Int): UINode {
        align = pos
        return this
    }
}

/** 节点工厂。全部 @JvmStatic，外置插件用 Java 也能直接写。 */
object Ui {

    // ---------- 容器 ----------

    /** 竖排（默认从上往下）。 */
    @JvmStatic
    @JvmOverloads
    fun column(spacingDp: Int = 6, vararg children: UINode): UINode =
        UINode(NodeKind.COLUMN).apply {
            this.spacingDp = spacingDp
            this.children.addAll(children)
        }

    /** 横排。 */
    @JvmStatic
    @JvmOverloads
    fun row(spacingDp: Int = 6, vararg children: UINode): UINode =
        UINode(NodeKind.ROW).apply {
            this.spacingDp = spacingDp
            this.children.addAll(children)
        }

    /** 叠放：子节点压在彼此上面，用 align 定位。 */
    @JvmStatic
    fun box(vararg children: UINode): UINode =
        UINode(NodeKind.BOX).apply { this.children.addAll(children) }

    /** 分组卡片：带标题和圆角背景，里面继续排。 */
    @JvmStatic
    @JvmOverloads
    fun group(title: String = "", vararg children: UINode): UINode =
        UINode(NodeKind.GROUP).apply {
            label = title
            this.children.addAll(children)
        }

    /** 网格：固定列数，自动换行。 */
    @JvmStatic
    @JvmOverloads
    fun grid(cols: Int = 2, vararg children: UINode): UINode =
        UINode(NodeKind.GRID).apply {
            this.cols = cols.coerceIn(1, 4)
            this.children.addAll(children)
        }

    /** 可滚动容器（内容比屏幕长时用）。 */
    @JvmStatic
    fun scroll(vararg children: UINode): UINode =
        UINode(NodeKind.SCROLL).apply { this.children.addAll(children) }

    /** 空白。 */
    @JvmStatic
    fun spacer(dp: Int = 8): UINode = UINode(NodeKind.SPACER).apply { heightDp = dp }

    @JvmStatic
    fun divider(): UINode = UINode(NodeKind.DIVIDER)

    // ---------- 画布 ----------

    /**
     * 元素画布：页面里的一块，高度自己定。
     * 里面画什么由 [draw] 决定，点中回调走 [UINode.action] 或再挂 onTap。
     */
    @JvmStatic
    @JvmOverloads
    fun canvas(heightDp: Int = 56, draw: DrawBlock): UINode =
        UINode(NodeKind.CANVAS).apply {
            this.heightDp = heightDp
            this.draw = draw
        }

    /** 整页画布：占满整页（配合 UIPage.fullPage()）。 */
    @JvmStatic
    fun fillCanvas(draw: DrawBlock): UINode =
        UINode(NodeKind.CANVAS).apply {
            this.draw = draw
            fill = true
        }

    // ---------- 官方组件（跟内置页面同一套外观） ----------

    @JvmStatic
    fun section(label: String): UINode = official(OfficialKind.SECTION, label)

    @JvmStatic
    fun note(label: String): UINode = official(OfficialKind.NOTE, label)

    @JvmStatic
    @JvmOverloads
    fun stat(label: String, value: String, actionId: String = ""): UINode =
        official(OfficialKind.STAT, label).apply {
            this.value = value
            this.actionId = actionId
        }

    @JvmStatic
    @JvmOverloads
    fun hero(label: String, value: String, sub: String = "", actionId: String = ""): UINode =
        official(OfficialKind.HERO, label).apply {
            this.value = value
            this.sub = sub
            this.actionId = actionId
        }

    @JvmStatic
    fun button(
        label: String,
        symbol: String = "",
        trailing: String = "",
        danger: Boolean = false,
        actionId: String,
    ): UINode = official(OfficialKind.BUTTON, label).apply {
        this.symbol = symbol
        this.trailing = trailing
        this.danger = danger
        this.actionId = actionId
    }

    @JvmStatic
    fun button(label: String, actionId: String): UINode = button(label, "", "", false, actionId)

    @JvmStatic
    @JvmOverloads
    fun item(
        title: String,
        sub: String = "",
        symbol: String = "",
        trailing: String = "",
        actionId: String = "",
    ): UINode = official(OfficialKind.ITEM, title).apply {
        this.sub = sub
        this.symbol = symbol
        this.trailing = trailing
        this.actionId = actionId
    }

    @JvmStatic
    @JvmOverloads
    fun progress(label: String, done: Long, total: Long, trailing: String = ""): UINode =
        official(OfficialKind.PROGRESS, label).apply {
            this.done = done
            this.total = total
            this.trailing = trailing
        }

    @JvmStatic
    fun ring(label: String, done: Long, total: Long): UINode =
        official(OfficialKind.RING, label).apply {
            this.done = done
            this.total = total
        }

    @JvmStatic
    @JvmOverloads
    fun stepper(label: String, value: Int, min: Int = 0, max: Int = 100, step: Int = 1, actionId: String): UINode =
        official(OfficialKind.STEPPER, label).apply {
            current = value
            this.min = min
            this.max = max
            this.step = step
            this.actionId = actionId
        }

    @JvmStatic
    @JvmOverloads
    fun chips(label: String, options: List<String>, selected: Int = 0, actionId: String = ""): UINode =
        official(OfficialKind.CHIPS, label).apply {
            this.options = options
            this.selected = selected
            this.actionId = actionId
        }

    @JvmStatic
    fun chips(label: String, optionsCsv: String, selected: Int = 0, actionId: String = ""): UINode =
        chips(label, optionsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }, selected, actionId)

    @JvmStatic
    @JvmOverloads
    fun colorCard(label: String, colorHex: String, trailing: String = "", active: Boolean = false, actionId: String = ""): UINode =
        official(OfficialKind.COLOR, label).apply {
            this.colorHex = colorHex
            this.trailing = trailing
            this.active = active
            this.actionId = actionId
        }

    @JvmStatic
    @JvmOverloads
    fun timeCard(start: String, end: String, trailing: String = "", actionId: String = ""): UINode =
        official(OfficialKind.TIME, start).apply {
            this.start = start
            this.end = end
            this.trailing = trailing
            this.actionId = actionId
        }

    @JvmStatic
    @JvmOverloads
    fun toggle(label: String, on: Boolean, actionId: String = ""): UINode =
        official(OfficialKind.TOGGLE, label).apply {
            this.on = on
            this.actionId = actionId
        }

    @JvmStatic
    fun empty(label: String = "还没有内容"): UINode = official(OfficialKind.EMPTY, label)

    @JvmStatic
    @JvmOverloads
    fun bars(label: String, numbers: List<Float>, trailing: String = ""): UINode =
        official(OfficialKind.BARS, label).apply {
            this.numbers = numbers
            this.trailing = trailing
        }

    @JvmStatic
    @JvmOverloads
    fun shape(shape: String, label: String, sizeDp: Int = 56, colorHex: String = "", actionId: String = ""): UINode =
        official(OfficialKind.SHAPE, label).apply {
            this.shape = shape
            this.sizeDp = sizeDp
            this.colorHex = colorHex
            this.actionId = actionId
        }

    private fun official(kind: OfficialKind, label: String): UINode =
        UINode(NodeKind.OFFICIAL).apply {
            official = kind
            this.label = label
        }
}

// ---------------- 渲染 ----------------

@Composable
internal fun UIPageContent(page: UIPage, scope: PageScope, revision: Int) {
    if (page.fullScreen) {
        Box(Modifier.fillMaxSize()) {
            NodeView(page.root, page, revision, 0, Modifier.fillMaxSize())
        }
    } else {
        Official.Page(title = page.title, sub = page.subtitle, onBack = { scope.close() }) {
            NodeView(page.root, page, revision, 0, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NodeView(node: UINode, page: UIPage, revision: Int, depth: Int, modifier: Modifier) {
    if (depth > MAX_DEPTH) {
        Official.Note("嵌套太深，已截断")
        return
    }
    val m = if (node.padDp > 0) modifier.padding(node.padDp.dp) else modifier
    when (node.kind) {
        NodeKind.COLUMN -> Column(
            m.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
        ) {
            node.children.forEach { child -> NodeView(child, page, revision, depth + 1, childModifier(child)) }
        }

        NodeKind.SCROLL -> Column(
            m.fillMaxWidth().let { if (node.scrollable) it.verticalScroll(rememberScrollState()) else it },
            verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
        ) {
            node.children.forEach { child -> NodeView(child, page, revision, depth + 1, childModifier(child)) }
        }

        NodeKind.ROW -> Row(
            m.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child -> NodeView(child, page, revision, depth + 1, rowChildModifier(child)) }
        }

        NodeKind.GRID -> Column(
            m.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
        ) {
            node.children.chunked(node.cols).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.forEach { child ->
                        Box(Modifier.weight(1f)) { NodeView(child, page, revision, depth + 1, Modifier.fillMaxWidth()) }
                    }
                    repeat(node.cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        NodeKind.BOX -> Box(
            m.fillMaxWidth().let { if (node.heightDp > 0) it.height(node.heightDp.dp) else it },
        ) {
            node.children.forEach { child ->
                NodeView(child, page, revision, depth + 1, Modifier.align(childAlign(child.align)))
            }
        }

        NodeKind.GROUP -> Column(
            m.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(node.spacingDp.dp),
        ) {
            if (node.label.isNotEmpty()) Official.Section(node.label)
            node.children.forEach { child -> NodeView(child, page, revision, depth + 1, childModifier(child)) }
        }

        NodeKind.SPACER -> Spacer(Modifier.height((if (node.heightDp > 0) node.heightDp else 8).dp))

        NodeKind.DIVIDER -> Official.Divider()

        NodeKind.CANVAS -> CanvasNode(node, page, revision, m)

        NodeKind.OFFICIAL -> OfficialNode(node, page, m)

        NodeKind.CUSTOM -> Official.Note("暂不支持的自定义节点")
    }
}

private fun childModifier(node: UINode): Modifier {
    var m: Modifier = Modifier.fillMaxWidth()
    if (node.heightDp > 0) m = m.height(node.heightDp.dp)
    return m
}

private fun RowScope.rowChildModifier(node: UINode): Modifier {
    var m: Modifier = if (node.weight > 0f) Modifier.weight(node.weight) else Modifier
    if (node.heightDp > 0) m = m.height(node.heightDp.dp)
    return m
}

private fun childAlign(pos: Int): Alignment = when (pos) {
    1 -> Alignment.TopCenter
    2 -> Alignment.TopEnd
    3 -> Alignment.CenterStart
    4 -> Alignment.Center
    5 -> Alignment.CenterEnd
    6 -> Alignment.BottomStart
    7 -> Alignment.BottomCenter
    8 -> Alignment.BottomEnd
    else -> Alignment.TopStart
}

@Composable
private fun CanvasNode(node: UINode, page: UIPage, revision: Int, modifier: Modifier) {
    val theme = penTheme()
    val ops = remember(node, revision, theme) {
        val pen = Pen(theme)
        node.draw?.let { block -> runCatching { block.draw(pen) } }
        pen.ops.toList()
    }
    val h = when {
        node.fill -> 220
        node.heightDp > 0 -> node.heightDp
        else -> 56
    }
    val m = modifier
        .fillMaxWidth()
        .let { if (node.fill) it.heightIn(min = h.dp) else it.height(h.dp) }
    val tap = node.tap ?: node.actionId.takeIf { it.isNotEmpty() }?.let { id ->
        TapBlock { _, _ -> page.dispatch(id) }
    }
    PenCanvasView(ops, theme, m, tap, node.drag)
}

@Composable
private fun OfficialNode(node: UINode, page: UIPage, modifier: Modifier) {
    val click: (() -> Unit)? = if (node.actionId.isEmpty()) null else { { page.dispatch(node.actionId) } }
    when (node.official) {
        OfficialKind.SECTION -> Official.Section(node.label)
        OfficialKind.NOTE -> Official.Note(node.label)
        OfficialKind.STAT -> Official.Stat(node.label, node.value)
        OfficialKind.HERO -> Official.Hero(node.label, node.value, node.sub.ifEmpty { null })
        OfficialKind.BUTTON -> Official.Button(
            symbol = node.symbol,
            label = node.label,
            trailing = node.trailing,
            danger = node.danger,
            onClick = click ?: {},
        )

        OfficialKind.ITEM -> Official.Item(
            symbol = node.symbol,
            title = node.label,
            sub = node.sub,
            trailing = node.trailing,
            onClick = click ?: {},
        )

        OfficialKind.PROGRESS -> Official.Progress(node.label, node.done, node.total, node.trailing.ifEmpty { null })
        OfficialKind.RING -> Official.Ring(node.label, node.done, node.total)
        OfficialKind.STEPPER -> Official.Stepper(node.label, node.current, node.min, node.max, node.step) { newValue ->
            page.dispatch(node.actionId + ":" + newValue)
        }

        OfficialKind.CHIPS -> Official.Chips(node.label, node.options, node.selected) { index ->
            page.dispatch(node.actionId + ":" + index)
        }

        OfficialKind.COLOR -> Official.ColorCard(
            node.label,
            node.colorHex,
            node.trailing,
            node.active,
            click ?: {},
        )

        OfficialKind.TIME -> Official.TimeCard(
            node.start.toLongOrNull() ?: 0L,
            node.end.toLongOrNull() ?: 0L,
            node.trailing.ifEmpty { null },
            click,
        )
        OfficialKind.TOGGLE -> Official.Toggle(node.label, node.on) { on ->
            page.dispatch(node.actionId + ":" + if (on) "1" else "0")
        }

        OfficialKind.EMPTY -> Official.Empty(node.label)
        OfficialKind.BARS -> Official.Bars(node.label, node.numbers, node.trailing.ifEmpty { null })
        OfficialKind.SHAPE -> Official.Shape(node.shape, node.label, node.sizeDp, node.colorHex, click ?: {})
    }
}

@Composable
private fun penTheme(): PenTheme {
    val cs = MaterialTheme.colorScheme
    return PenTheme(
        text = cs.onSurface,
        muted = cs.onSurfaceVariant,
        primary = cs.primary,
        danger = cs.error,
        card = cs.surfaceContainer,
    )
}

internal const val MAX_DEPTH: Int = 16
