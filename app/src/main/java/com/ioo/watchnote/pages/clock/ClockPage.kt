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
   C 剩余时间（用户图纸版式，圆环顶满屏幕）
   ============================================================ */

@Composable
internal fun ClockPage(store: Store, refresh: Int, fullAnim: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val nowMin = Store.nowMins()
    val st = remember(now, refresh) { store.stateAt(Store.todayIndex(), Store.nowMins()) }
    val active = st.active
    val next = st.next

    var targetProgress = 0f
    var stageName = "今天没有安排"
    var rangeLine = ""
    var bigNumber = "--"
    var bigUnit = "分钟"
    var nextName = "去课程表或办事加一条"
    var nextRange = ""

    if (active != null) {
        val total = (st.activeEnd - st.activeStart).toFloat().coerceAtLeast(1f)
        val elapsed = (nowMin - st.activeStart).toFloat().coerceIn(0f, total)
        targetProgress = elapsed / total
        stageName = active.name
        rangeLine = active.start + " – " + active.end
        bigNumber = Math.max(0, ceil(st.remain).toInt()).toString()
        bigUnit = "分钟后结束"
        if (next != null) {
            nextName = (if (st.nextOffset == 1) "明天 " else "") + next.name
            nextRange = next.start + " – " + next.end
        } else {
            nextName = "今天没有下一段了"
        }
    } else if (next != null) {
        if (st.nextOffset == 1) {
            targetProgress = 0f
            stageName = "现在空闲"
            rangeLine = "空闲到明天 " + Store.fmtMins(st.nextStart)
        } else {
            val prevEnd = store.forDay(Store.todayIndex())
                .filter { Store.minsOf(it.end) <= nowMin }
                .maxByOrNull { Store.minsOf(it.end) }?.let { Store.minsOf(it.end) }
            val windowStart = prevEnd ?: nowMin.toInt()
            val total = (st.nextStart - windowStart).coerceAtLeast(1).toFloat()
            val elapsed = (nowMin - windowStart).toFloat().coerceIn(0f, total)
            targetProgress = elapsed / total
            stageName = "现在空闲"
            rangeLine = "空闲 " + Store.fmtMins(windowStart) + " – " + Store.fmtMins(st.nextStart)
        }
        bigNumber = Math.max(0, ceil(st.nextIn).toInt()).toString()
        bigUnit = "分钟后开始"
        nextName = (if (st.nextOffset == 1) "明天 " else "") + next.name
        nextRange = next.start + " – " + next.end
    } else {
        rangeLine = "今天没有安排"
    }

    // 办事也进这条时间线：正在办 > 课表当前段；下一件谁先到就算谁。
    // 按开始时间排序后取「最后一条已经开始的」，同时有好几条重叠也不会拿错。
    val todoList = store.notes.filter { it.todo && !it.done && it.dueAt > 0L }.sortedBy { it.dueAt }
    val todoActive = todoList.lastOrNull { it.dueAt <= now && it.endAt > now }
    val todoNext = todoList.firstOrNull { it.dueAt > now }
    val scheduleNextAbs: Long? = if (next != null) {
        Store.startOfToday() + st.nextOffset * 86_400_000L + st.nextStart * 60_000L
    } else null

    if (todoActive != null) {
        val total = (todoActive.endAt - todoActive.dueAt).toFloat().coerceAtLeast(1f)
        targetProgress = ((now - todoActive.dueAt).toFloat() / total).coerceIn(0f, 1f)
        stageName = todoTitle(todoActive)
        rangeLine = todoRange(todoActive)
        bigNumber = Math.max(0, ceil((todoActive.endAt - now) / 60_000.0).toInt()).toString()
        bigUnit = "分钟后结束"
        if (todoNext != null && (scheduleNextAbs == null || todoNext.dueAt <= scheduleNextAbs)) {
            nextName = "办事 " + todoTitle(todoNext)
            nextRange = todoRange(todoNext)
        } else if (next != null) {
            nextName = (if (st.nextOffset == 1) "明天 " else "") + next.name
            nextRange = next.start + " – " + next.end
        } else {
            nextName = "没有下一件了"
        }
    } else if (todoNext != null && (scheduleNextAbs == null || todoNext.dueAt < scheduleNextAbs)) {
        targetProgress = 0f
        stageName = "现在空闲"
        rangeLine = "空闲到 " + Store.hhmm(todoNext.dueAt)
        bigNumber = Math.max(0, ceil((todoNext.dueAt - now) / 60_000.0).toInt()).toString()
        bigUnit = "分钟后开始"
        nextName = "办事 " + todoTitle(todoNext)
        nextRange = todoRange(todoNext)
    }

    val progress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(if (fullAnim) 600 else 0),
        label = "ring"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(1.06f),
            gapSize = 0.dp
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stageName, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rangeLine, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                Text("现在 " + Store.fmtClock(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(bigNumber, color = MaterialTheme.colorScheme.onSurface, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(bigUnit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("下一段 " + nextName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (nextRange.isNotEmpty()) {
                Text(nextRange, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** 办事时间：「20:00 – 21:00」；跨天写「次日」。 */
private fun todoRange(n: Note): String {
    if (n.endAt <= n.dueAt) return Store.hhmm(n.dueAt) + " 开始"
    val cross = (n.endAt - Store.startOfToday()) / 86_400_000L != (n.dueAt - Store.startOfToday()) / 86_400_000L
    return Store.hhmm(n.dueAt) + " – " + (if (cross) "次日 " else "") + Store.hhmm(n.endAt)
}

/** 办事标题：取第一行正文，空的话就叫「办事」。 */
private fun todoTitle(n: Note): String {
    val t = n.text.trim().split("\n").firstOrNull()?.trim().orEmpty()
    return if (t.isEmpty()) "办事" else t
}