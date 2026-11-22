package com.ioo.watchnote

import com.ioo.watchnote.plugin.PluginSelfTest
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
   自测工具（关于 → 自测工具 单独页面）
   ============================================================ */

@Composable
internal fun SelfTestPage(
    store: Store,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    SubPageFrame(onBack) {
        PageTitle("自测工具")
        val topTimeNow = UiPrefsState.showTopTime.value
        Text(
            "顶部时间当前状态：" + (if (topTimeNow) "显示" else "隐藏"),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
        Text(
            "App 日志最多保存 800 条，写入本地文件，App 退出后仍保留，直到手动清空。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        // 插件系统自测：单独建一个沙盒核心跑，不影响正在用的插件和设置
        val pluginResults = remember { PluginSelfTest.run(store) }
        val failed = pluginResults.count { !it.ok }
        LaunchedEffect(Unit) {
            EventLog.add(
                "selftest",
                "plugin " + pluginResults.size + " 项，失败 " + failed + " 项"
            )
            pluginResults.filter { !it.ok }.forEach { EventLog.add("selftest", "FAIL " + it.name + "：" + it.detail) }
        }
        Text(
            "插件自检：" + (pluginResults.size - failed) + "/" + pluginResults.size + " 通过",
            color = if (failed == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
        )
        pluginResults.forEach { r ->
            Text(
                (if (r.ok) "✓ " else "✗ ") + r.name + (if (r.ok) "" else "：" + r.detail),
                color = if (r.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                fontSize = 9.5.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        SettingPill("⬇", "导出日志到 Download", onClick = onExport)
        SettingPill("⎘", "复制日志", onClick = onCopy)
        SettingPill("✕", "清空日志", danger = true, onClick = onClear)
        Spacer(Modifier.height(8.dp))
        Text(
            "系统日志说明：普通 App 没有 READ_LOGS 权限（系统签名级），无法直接读取系统 logcat。需要系统日志请用手表开发者选项的\"错误报告\"或电脑 adb logcat。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}
