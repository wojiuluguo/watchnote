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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
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
   通用组件
   ============================================================ */

@Composable
internal fun PageTitle(title: String, sub: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        if (sub != null) {
            Text(
                sub,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
            )
        }
    }
}

internal val LocalFishEye = compositionLocalOf { true }

@Composable
internal fun TransformingLazyColumnItemScope.Fish(content: @Composable () -> Unit) {
    if (!LocalFishEye.current) {
        Box(Modifier.fillMaxWidth()) {
            content()
        }
        return
    }
    val spec = rememberTransformationSpec()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec)
            .graphicsLayer {
                with(spec) {
                    applyContainerTransformation(scrollProgress)
                }
            }
    ) {
        content()
    }
}

@Composable
internal fun WearListPage(
    edgeIcon: String? = null,
    onEdge: (() -> Unit)? = null,
    fishEye: Boolean = true,
    active: Boolean = true,
    smallEdge: Boolean = true,
    state: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    content: TransformingLazyColumnScope.() -> Unit
) {
    DisposableEffect(active) {
        val unregister = if (active) {
            RotaryBus.register { delta -> state.dispatchRawDelta(delta * -150f) }
        } else null
        onDispose { unregister?.invoke() }
    }
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            scrollState = state,
            timeText = { },
            edgeButton = {
                if (edgeIcon != null && onEdge != null) {
                    EdgeButton(onClick = { Haptics.tap(); onEdge() }) {
                        Text(edgeIcon, fontSize = 22.sp)
                    }
                }
            }
        ) { padding ->
            CompositionLocalProvider(LocalFishEye provides fishEye) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                    state = state,
                    contentPadding = padding,
                    rotaryScrollableBehavior = rememberNoopRotaryBehavior()
                ) {
                    content()
                }
            }
        }
        // 所有带主按钮的列表页都放底部小圆按钮（与卡片数无关）；它自己按滚动状态隐藏/飞出
        if (smallEdge && edgeIcon != null && onEdge != null) {
            SmallEdgeIconButton(state = state, icon = edgeIcon, onClick = { Haptics.tap(); onEdge() })
        }
    }
}

@Composable
internal fun SettingPill(symbol: String, label: String, danger: Boolean = false, trailing: String? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable { Haptics.tap(); onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = fg, fontSize = 16.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                color = if (danger) fg else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
internal fun ChoiceChip(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(squircle(18.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { Haptics.tap(); onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun BigChoiceChip(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(squircle(20.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { Haptics.tap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
internal fun TextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    heightDp: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (heightDp > 0) Modifier.height(heightDp.dp) else Modifier.heightIn(min = 40.dp))
            .clip(squircle(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, squircle(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                inner()
            }
        )
    }
}

@Composable
internal fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    val current by remember { derivedStateOf { (((pager.currentPage - 2000) % 5) + 5) % 5 } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (i in 0 until 5) {
            val dotHeight by animateDpAsState(if (i == current) 10.dp else 3.dp, label = "dot")
            Box(
                Modifier
                    .width(3.dp)
                    .height(dotHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/**
 * 子页面底部的小返回键（常亮）。
 * 判定值与官方 ScreenScaffold 完全一致：lastItemOffset = 视口高度 - 最后一个 item 的底边
 *（TransformingLazyColumnStateScrollInfoProvider.getLastItemOffset 的算法）。
 * reveal = 0 时官方 EdgeButton 高度为 0（不显示），小按钮常亮在最下方；
 * reveal 变大（滚到列表末尾、大按钮开始升起）时，小按钮一边向下飞出屏幕一边淡出。
 */
@Composable
internal fun SmallBackButton(state: TransformingLazyColumnState, onBack: () -> Unit) {
    SmallEdgeIconButton(state = state, icon = "←", onClick = onBack)
}

/**
 * 主页面底部的小圆按钮（＋ / ✎ 等），规则与 SmallBackButton 相同：
 * 所有列表页常显，不用滑到底找大按钮；大按钮升起时它自己飞下去消失。
 */
@Composable
internal fun SmallEdgeIconButton(state: TransformingLazyColumnState, icon: String, onClick: () -> Unit) {
    val density = LocalDensity.current
    // ScreenScaffoldDefaults.EdgeButtonSpacing(16dp) - EdgeButtonMinSpacing(3dp)
    val correctionPx = with(density) { 13.dp.toPx() }
    // EdgeButton Small 满高 56dp + 上下各 3dp padding
    val fullHeightPx = with(density) { 62.dp.toPx() }
    val travelPx = with(density) { 130.dp.toPx() }
    val reveal by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItems.lastOrNull()
            if (last != null && last.index == info.totalItemsCount - 1) {
                val free = (info.viewportSize.height - last.offset - last.transformedHeight).coerceAtLeast(0)
                ((free - correctionPx) / fullHeightPx).coerceIn(0f, 1f)
            } else 0f
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .graphicsLayer {
                    alpha = (1f - reveal * 2.2f).coerceIn(0f, 1f)
                    translationY = reveal * travelPx
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = reveal < 0.6f) { Haptics.tap(); onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = MaterialTheme.colorScheme.onPrimary, fontSize = 17.sp)
        }
    }
}

/** 子页面里“列表型”页面：和主页面一样有鱼眼/满血动画；性能模式自动关掉。 */
@Composable
internal fun SubPageListFrame(
    onBack: () -> Unit,
    fishEye: Boolean = UiPrefsState.animMode.value == 0,
    smallBack: Boolean = true,
    content: TransformingLazyColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val state = rememberTransformingLazyColumnState()
        WearListPage(edgeIcon = "←", onEdge = onBack, fishEye = fishEye, active = true, state = state) {
            content()
        }
        // 底部小返回键所有列表型子页面都给；大按钮升起时 SmallBackButton 自己飞下去
        if (smallBack) SmallBackButton(state = state, onBack = onBack)
    }
}


/**
 * 子页面（表单型）。
 * 结构与主页面完全一致：ScreenScaffold + TransformingLazyColumn，返回键是官方 EdgeButton，
 * 放在 ScreenScaffold 的 edgeButton 槽位里（不是叠在内容上）。
 * 这样 Scaffold 自己算好内容留白和边距，返回键既不会盖住页面内容，动画也由官方组件自己判定。
 */
@Composable
internal fun SubPageFrame(
    onBack: () -> Unit,
    smallBack: Boolean = true,
    bottomStart: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val state = rememberTransformingLazyColumnState()
        DisposableEffect(Unit) {
            val unregister = RotaryBus.register { delta -> state.dispatchRawDelta(delta * -150f) }
            onDispose { unregister() }
        }
        ScreenScaffold(
            scrollState = state,
            timeText = { },
            edgeButton = {
                EdgeButton(onClick = { Haptics.tap(); onBack() }) {
                    Text("←", fontSize = 22.sp)
                }
            }
        ) { padding ->
            TransformingLazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                state = state,
                contentPadding = padding,
                rotaryScrollableBehavior = rememberNoopRotaryBehavior()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp)
                            .padding(bottom = 16.dp),
                        content = content
                    )
                }
            }
        }
        if (smallBack) SmallBackButton(state = state, onBack = onBack)
        if (bottomStart != null) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 30.dp)) {
                bottomStart()
            }
        }
    }
}