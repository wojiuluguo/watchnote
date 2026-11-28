package com.ioo.watchnote

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
import androidx.compose.foundation.Image
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ioo.watchnote.plugin.PluginApi
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
   数据 / 导航
   ============================================================ */

/* ============================================================
   子页面：关于 / 确认框
   ============================================================ */

@Composable
internal fun AnimModePage(current: Int, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    SubPageFrame(onBack) {
        PageTitle("动画效果")
        Text(
            "满血：全部动画（鱼眼、滑入、圆环、页点）\n性能：只保留翻页和按压，最流畅",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        ModeRow("满血模式", current == 0) { onSelect(0) }
        ModeRow("性能模式", current == 1) { onSelect(1) }
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 52.dp)
            .clip(squircle(26.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = squircle(26.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
internal fun AboutPage(
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenPluginSelfTest: (() -> Unit)? = null
) {
    // 列表型子页面：每一行都跟设置页同一套 Fish + SettingPill，左右宽度完全一致（4.1.24 修「按钮太窄」）
    SubPageListFrame(onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 关于页顶图：白底黑图已转成「透明底 + 纯白线条」，深色界面下不会糊成一块
                Image(
                    painter = painterResource(R.drawable.about_logo),
                    contentDescription = "腕记",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(104.dp)
                        .padding(vertical = 2.dp)
                )
                PageTitle("腕记")
            }
        }
        item { Fish { SettingPill("i", "详细信息", onClick = onOpenDetail) } }
        item { Fish { SettingPill("⌘", "开源网址", onClick = onOpenSource) } }
        item { Fish { SettingPill("☰", "日志管理", onClick = onOpenLogs) } }
        // 插件自检只在插件模块开启后出现
        if (onOpenPluginSelfTest != null) {
            item { Fish { SettingPill("⚙", "插件自检", onClick = onOpenPluginSelfTest) } }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "课程表倒计时 · 语音笔记 · 快捷备忘\n为小米 Watch 5 圆屏与表冠优化",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "笔记与录音保存在手表本地\n卸载前请先导出备份",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
internal fun AboutDetailPage(
    pluginModuleOn: Boolean,
    checker: UpdateChecker,
    onEnablePluginModule: () -> Unit,
    onDisablePluginModule: () -> Unit,
    onOpenUpdate: () -> Unit,
    onBack: () -> Unit
) {
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    // 4.1.26：进「详细信息」时，距上次检查超过 24 小时就静默查一次（失败也不弹窗）
    var updateStatus by remember { mutableStateOf(checker.cachedStatusText()) }
    LaunchedEffect(Unit) {
        if (!checker.shouldAutoCheck()) return@LaunchedEffect
        updateStatus = "正在检查…"
        val result = withContext(Dispatchers.IO) { checker.check() }
        updateStatus = when (result) {
            is UpdateCheck.Available -> "有新版 " + result.versionName
            is UpdateCheck.Latest -> "已是最新"
            is UpdateCheck.Failed -> "检查失败"
        }
    }
    SubPageFrame(onBack) {
        PageTitle("详细信息")
        Spacer(Modifier.height(10.dp))
        // 版本号：页面主角。连点三下（每下间隔 1.5 秒内）才打开插件模块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .clip(squircle(34.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTapAt <= 1500L) taps + 1 else 1
                    lastTapAt = now
                    if (taps >= 3) {
                        taps = 0
                        // 4.1.24：三连点变成开关——没开就打开，已开就关掉并恢复原样
                        if (pluginModuleOn) onDisablePluginModule() else onEnablePluginModule()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "版本 " + BuildConfig.VERSION_NAME,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "（" + BuildConfig.VERSION_CODE + "）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        if (pluginModuleOn) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(squircle(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    Text("插件模块", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Text(
                        "v" + PluginApi.MODULE_VERSION + "（测试版）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        "连点版本号三下可关闭插件模块",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        if (pluginModuleOn) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(squircle(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        "为什么是 0.4.5，不是 0.5",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "0.5 的要求里还有一条没做到：插件要能改腕记自己的页面——官方页面的布置、按钮样式都能被插件改。\n" +
                            "0.4.5 先把能做好的四件事给出来：布局容器、画布、联网、插件主页面。\n" +
                            "等官方页面也能被插件修改了，这一版才叫 0.5。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingPill("↑", "检查更新", trailing = updateStatus, onClick = onOpenUpdate)
        Spacer(Modifier.height(10.dp))
        Text(
            "腕记 WristNote · 小米手表本地笔记\n笔记与录音只保存在本机，卸载前请先导出备份",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
