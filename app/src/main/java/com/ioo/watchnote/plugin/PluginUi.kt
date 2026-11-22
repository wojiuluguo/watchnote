package com.ioo.watchnote.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ioo.watchnote.Haptics
import com.ioo.watchnote.Note
import com.ioo.watchnote.SettingPill

/* ============================================================
   插件按钮插槽的宿主渲染层
   ------------------------------------------------------------
   三个插槽各有各的位置，宿主统一在这里画，插件只提供 icon / label / onClick：
   - PAGE_BOTTOM：插件页面左下角的小圆按钮（和主页面底部那颗小圆按钮同一套尺寸）
   - SETTINGS_ROW：设置页底部多出来的一行（走官方 SettingPill）
   - CARD_ACTION：笔记卡片右侧的动作（见 PluginCardActions，按笔记调用）
   ============================================================ */

/**
 * 当前插件页面左下角要挂的附加槽位内容（PAGE_BOTTOM 按钮条）。
 *
 * 由 PluginPageHost 在渲染页面时提供、由官方页面壳 Official.Page 取走，
 * 最后由宿主页面壳（SubPageFrame）放在左下角并按「大返回键升起就飞下去」的规则收起。
 *
 * 这样插件页里不会出现「插件自己叠一颗圆钮 + 官方大返回键并排」的画面 ——
 * 那个位置是宿主的地盘，插件只提供按钮、不决定位置，也不参与底部冲突。
 */
internal val LocalPluginPageBottomBar = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** 插件页面底部的小圆按钮。一个都没注册时整块不画，页面布局完全不变。 */
@Composable
fun PluginPageBottomBar(core: PluginCore, ownerPluginId: String, modifier: Modifier = Modifier) {
    val items = core.buttonsOf(ownerPluginId, ButtonSlot.PAGE_BOTTOM)
    if (items.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { button ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { Haptics.tap(); core.runButton(button) },
                contentAlignment = Alignment.Center
            ) {
                Text(button.spec.icon, color = MaterialTheme.colorScheme.onPrimary, fontSize = 17.sp)
            }
        }
    }
}

/** 设置页里插件贡献的一行。宿主自己决定要塞进哪个 item，这里只负责长成和其它行一样。 */
@Composable
fun PluginSettingsRow(core: PluginCore, button: PluginButton) {
    SettingPill(
        symbol = button.spec.icon,
        label = button.spec.label,
        onClick = { core.runButton(button) },
    )
}

/**
 * 笔记卡片右侧的插件动作。没有插件注册 CARD_ACTION 时整块不画，卡片布局跟原来一模一样。
 * 每个按钮都把「被点的那条笔记」交给插件（ButtonSpec.onCardAction），不是全局瞎点。
 */
@Composable
fun PluginCardActions(core: PluginCore?, note: Note, modifier: Modifier = Modifier) {
    if (core == null) return
    val items = core.buttonsOf(ButtonSlot.CARD_ACTION)
    if (items.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { button ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { Haptics.tap(); core.runCardAction(button, note) },
                contentAlignment = Alignment.Center
            ) {
                Text(button.spec.icon, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 14.sp)
            }
        }
    }
}
