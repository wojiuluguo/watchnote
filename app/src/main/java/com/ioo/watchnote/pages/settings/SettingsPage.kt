package com.ioo.watchnote

import com.ioo.watchnote.plugin.ButtonSlot
import com.ioo.watchnote.plugin.PluginCore
import com.ioo.watchnote.plugin.PluginSettingsRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxScope
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.ceil

/* ============================================================
   D 设置（v2.5 结构：ScreenScaffold + TransformingLazyColumn + Fish）
   ============================================================ */

@Composable
internal fun SettingsPage(
    store: Store,
    onShowTopTime: () -> Unit,
    active: Boolean,
    onBackToNotes: () -> Unit,
    onAnimMode: () -> Unit,
    onManage: () -> Unit,
    onSpecial: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    pluginCore: PluginCore? = null,
    themeInstalled: Boolean = true,
    pluginCount: Int = 0,
    pluginEnabled: Boolean = false,
    onPlugins: () -> Unit = {}
) {
    WearListPage(edgeIcon = "↩", onEdge = onBackToNotes, fishEye = UiPrefsState.animMode.value == 0, active = active) {
        item {
            Fish {
                Column {
                    PageTitle("设置", "版本 " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）")
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        item { Fish { SettingPill("≡", "时间表管理", onClick = onManage) } }
        item { Fish { SettingPill("◷", "特殊时段", onClick = onSpecial) } }
        item { Fish { SettingPill("↻", "恢复默认时间表", onClick = onReset) } }
        item { Fish { SettingPill("↓", "导出备份", onClick = onExport) } }
        item { Fish { SettingPill("↑", "从备份恢复", onClick = onImport) } }
        item { Fish { SettingPill("⇪", "发到手机", onClick = onShare) } }
        // 主题颜色插件被关掉时，这一行跟着消失；从「插件」页可以再开回来
        if (themeInstalled) {
            item { Fish { SettingPill("◐", "更换主题颜色", onClick = onTheme) } }
        }
        // 插件模块默认隐藏：总开关没开时，这一行不出现
        if (pluginEnabled) {
            item { Fish { SettingPill("⌘", "插件", trailing = pluginCount.toString() + " 个", onClick = onPlugins) } }
        }
        item { Fish { SettingPill("◔", "动画效果", trailing = if (UiPrefsState.animMode.value == 0) "满血" else "性能", onClick = onAnimMode) } }
        item {
            LaunchedEffect(UiPrefsState.showTopTime.value) {
                EventLog.add("ui", "settingsRow topTime=" + UiPrefsState.showTopTime.value)
            }
            Fish { SettingPill("◷", "顶部时间", trailing = if (UiPrefsState.showTopTime.value) "显示" else "隐藏", onClick = onShowTopTime) }
        }
        item { Fish { SettingPill("≈", "震动反馈", trailing = if (UiPrefsState.haptics.value) "开" else "关", onClick = { store.setHaptics(!UiPrefsState.haptics.value) }) } }
        item { Fish { SettingPill("✕", "清空全部数据", danger = true, onClick = onClear) } }
        // 插件贡献的设置行：插件注册 SETTINGS_ROW 按钮，宿主负责让它们长成和其它行一样
        // 4.1.24：插件行要排在「关于」上面，「关于」永远是设置页最后一行
        val cores = pluginCore
        if (cores != null) {
            cores.buttonsOf(ButtonSlot.SETTINGS_ROW).forEach { button ->
                item { Fish { PluginSettingsRow(cores, button) } }
            }
        }
        item { Fish { SettingPill("i", "关于", onClick = onAbout) } }
    }
}