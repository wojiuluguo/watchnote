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
}
