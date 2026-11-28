package com.ioo.watchnote.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioo.watchnote.ConfirmData
import com.ioo.watchnote.ConfirmDialog
import com.ioo.watchnote.PageTitle
import com.ioo.watchnote.SettingPill
import com.ioo.watchnote.SubPageFrame

/**
 * 官方组件门面：插件不用自己画外壳、标题、按钮、确认框，直接用宿主这套。
 *
 * 这就是「有些按钮直接引用」的那层 API——用的是宿主同一份实现，
 * 所以圆屏适配、主题色、字号、点击反馈全自动跟着走，插件也不可能画得跟别处不一样。
 *
 * 用法（插件 install 里注册的页面 content 里）：
 *
 *     Official.Page("喝水提醒", "每 45 分钟", onBack = { scope.close() }) {
 *         Official.Stat("今天喝了", "3 杯")
 *         Official.Button("＋", "现在提醒我", onClick = { ... })
 *     }
 */
object Official {

    /**
     * 一整页：背景、返回键、圆屏安全区、表冠滚动全部由宿主配好，插件只往里放内容。
     * 内容按顺序竖排，放在宿主的滚动容器里，不用自己套 Column/verticalScroll。
     */
    @Composable
    fun Page(
        title: String,
        sub: String? = null,
        onBack: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        SubPageFrame(onBack = onBack, bottomStart = LocalPluginPageBottomBar.current) {
            PageTitle(title, sub)
            content()
        }
    }

    /** 页面标题：大标题 + 可选副标题，和宿主所有页面同一套字号与间距。 */
    @Composable
    fun Title(title: String, sub: String? = null) {
        PageTitle(title, sub)
    }

    /** 设置风格的一行按钮：左边符号、中间文字、右边状态；danger = 红色（清空/删除用）。 */
    @Composable
    fun Button(
        symbol: String,
        label: String,
        trailing: String? = null,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) {
        SettingPill(symbol, label, danger = danger, trailing = trailing, onClick = onClick)
    }

    /**
     * 一行「直接引用官方页面」的按钮（4.1.8 起）：
     * 插件只交一个 HostScreen，宿主打开自己那一份页面，插件不用重画、也不知道它长什么样。
     *
     *     Official.ScreenRow(host, HostScreen.SELF_TEST, "⚙")
     *
     * 这样一行就等于「把用户送到官方自测工具」，返回键自动回插件页。
     */
    @Composable
    fun ScreenRow(host: PluginHost, screen: HostScreen, symbol: String, trailing: String? = null) {
        Button(symbol = symbol, label = screen.title, trailing = trailing, onClick = { host.openHostPage(screen) })
    }

    /**
     * 一行只读统计：左边说明、右边数值。
     * 样式跟设置页的按钮行长一样，但不可点——统计数字不该看起来像按钮。
     */
    @Composable
    fun Stat(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    /** 官方确认框：插件要删东西前必须弹这个，而不是自己画个 Dialog。 */
    @Composable
    fun Confirm(data: ConfirmData, onClosed: () -> Unit) {
        ConfirmDialog(data, onClosed)
    }

    /* ============================================================
       0.2 新增：整套官方 UI 组件
       下面每个函数都是宿主内置页同款实现，插件直接调，不许自己拼一套外观。
       Java 插件用 SimplePage 里同名的行方法（section / hero / progress …）。
       ============================================================ */

    /** 小节标题：一组内容前面的灰色小标题。 */
    @Composable
    fun Section(text: String) {
        OfficialSection(text)
    }

    /** 一段说明文字：比正文小一号，用来解释这一页在干什么。 */
    @Composable
    fun Note(text: String) {
        OfficialNote(text)
    }

    /** 大数字卡片：标签 + 中间一行大字 + 可选副标题。 */
    @Composable
    fun Hero(label: String, value: String, sub: String? = null) {
        OfficialHero(label, value, sub)
    }

    /**
     * 两行列表行：左边符号、上面标题、下面小字、右边状态。
     * 不给 onClick 就是纯展示行（不可点）。
     */
    @Composable
    fun Item(
        symbol: String,
        title: String,
        sub: String? = null,
        trailing: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        OfficialItem(symbol, title, sub, trailing, onClick)
    }

    /** 线形进度条：label 左边、百分比右边，下面一根进度条。 */
    @Composable
    fun Progress(label: String, value: Long, total: Long, trailing: String? = null) {
        OfficialProgress(label, value, total, trailing)
    }

    /** 环形进度：圆环 + 中间百分比，圆屏上最省地方。 */
    @Composable
    fun Ring(label: String, value: Long, total: Long) {
        OfficialRing(label, value, total)
    }

    /**
     * 数值步进：− 数值 +，点一下加减 step，结果夹在 min..max。
     * onChange 收到的是夹好之后的新值（不是增量）。
     */
    @Composable
    fun Stepper(
        label: String,
        value: Int,
        min: Int = 0,
        max: Int = 999,
        step: Int = 1,
        onChange: (Int) -> Unit,
    ) {
        OfficialStepper(label, value, min, max, step, onChange)
    }

    /**
     * 一排可选项：选中项高亮，每行最多 4 个、多了自动换行。
     * onSelect 收到的是被点中那一项的序号（从 0 数）。
     */
    @Composable
    fun Chips(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        OfficialChips(label, options, selected, onSelect)
    }

    /** 整块彩色行：底色就是 colorHex（#RRGGBB 或 #AARRGGBB），选中/使用中时 active = true。 */
    @Composable
    fun ColorCard(
        label: String,
        colorHex: String,
        trailing: String = "",
        active: Boolean = false,
        onClick: () -> Unit,
    ) {
        OfficialColorCard(label, colorHex, trailing, active, onClick)
    }

    /** 时间摘要卡：开始/截止两行看得清（毫秒时间戳）。 */
    @Composable
    fun TimeCard(start: Long, end: Long, trailing: String? = null, onClick: (() -> Unit)? = null) {
        OfficialTimeCard(start, end, trailing, onClick)
    }

    /** 开关行：右侧显示 开/关，点一下回调切换后的状态。 */
    @Composable
    fun Toggle(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
        OfficialToggle(label, on, onToggle)
    }

    /** 一条分割线。 */
    @Composable
    fun Divider() {
        OfficialDivider()
    }

    /** 空白占位：height 是 dp。 */
    @Composable
    fun Space(height: Int) {
        OfficialSpace(height)
    }

    /** 空状态文字：居中一行灰字。 */
    @Composable
    fun Empty(text: String) {
        OfficialEmpty(text)
    }

    /** 柱状小图：values 是每根柱子相对高度（自动按最大值缩放），trailing 是右边一句话。 */
    @Composable
    fun Bars(label: String, values: List<Float>, trailing: String? = null) {
        OfficialBars(label, values, trailing)
    }

    /**
     * 图形行（0.4）：shape 取 triangle / star / diamond / hexagon，
     * size 是边长 dp，colorHex 为空就用主题色。
     */
    @Composable
    fun Shape(
        shape: String,
        label: String = "",
        size: Int = 56,
        colorHex: String = "",
        onClick: (() -> Unit)? = null,
    ) {
        OfficialShape(shape, label, size, colorHex, onClick)
    }
}
