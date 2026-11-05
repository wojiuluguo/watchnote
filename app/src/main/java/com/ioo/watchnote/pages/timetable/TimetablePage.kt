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
import androidx.compose.ui.draw.drawBehind
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
   B 课程表（v2.5 结构：ScreenScaffold + TransformingLazyColumn + Fish）
   ============================================================ */

@Composable
internal fun TimetablePage(store: Store, refresh: Int, fullAnim: Boolean, active: Boolean, onManage: () -> Unit, onEdit: (String) -> Unit) {
    store.version.intValue
    var day by remember { mutableIntStateOf(Store.todayIndex()) }
    val items = store.forDay(day)
    val isToday = day == Store.todayIndex()
    val st = if (isToday) store.stateAt(day, Store.nowMins()) else null
    val special = store.specialForDate(store.epochOfThisWeek(day))
    val specialItems = special?.items ?: emptyList()
    val pending = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Slot?>(null) }
    WearListPage(edgeIcon = "✎", onEdge = onManage, fishEye = fullAnim, active = active) {
        item {
            Fish {
                Column {
                    PageTitle("课程表", Store.fmtDate())
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (d in 0..6) {
                            ChoiceChip(
                                text = Store.DAY_NAMES[d],
                                active = day == d,
                                onClick = { day = d },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        if (items.isEmpty() && specialItems.isEmpty()) {
            item {
                Fish {
                    Text(
                        "这一天没有安排\n点下面 ✎ 管理时间表",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
                    )
                }
            }
        }
        if (specialItems.isNotEmpty()) {
            item {
                Fish {
                    Text(
                        "特殊时段 · 周" + (special?.let { Store.DAY_NAMES.getOrElse(it.weekday) { "?" } } ?: "?") + " · 剩 " + (special?.let { ((it.endEpochDay() - store.epochNow()) / 7L + 1L).coerceAtLeast(1L) } ?: 1) + " 周",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
            specialItems.forEachIndexed { i, it ->
                item(key = "special-" + i) {
                    Fish { SpecialSlotCard(it) }
                }
            }
        } else {
            items.forEachIndexed { idx, s ->
                val active = isToday && st?.active?.id == s.id
                item(key = s.id.ifBlank { "slot-" + s.start + "-" + s.end + "-" + idx }) {
                    Fish {
                        SlotCard(s, active, if (active) st?.remain ?: 0.0 else 0.0, onClick = { onEdit(s.id) }, onDelete = { pending.value = s })
                    }
                }
            }
        }
        if (items.isNotEmpty() || specialItems.isNotEmpty()) {
            item {
                Fish {
                    Text(
                        if (specialItems.isNotEmpty()) "特殊时段生效中 · 到期自动恢复" else "点击时段可以编辑",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
    pending.value?.let { s ->
        ConfirmDialog(
            ConfirmData(s.name, s.start + " – " + s.end, "删除", true, {
                store.deleteSlot(s.id)
                pending.value = null
            }),
            onClosed = { pending.value = null }
        )
    }
}

@Composable
internal fun SlotCard(s: Slot, active: Boolean, remain: Double, onClick: () -> Unit, onDelete: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val hold = rememberHoldProgress()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(18.dp))
            .background(bg)
            .drawBehind {
                val a = holdTintAlpha(hold.floatValue)
                if (a > 0f) drawRect(androidx.compose.ui.graphics.Color.Red.copy(alpha = a))
            }
            .holdToDelete(hold, onTap = onClick, onHold = onDelete)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                Store.typeLabel(s.type),
                color = Store.typeColor(s.type),
                fontSize = 10.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Store.typeBg(s.type)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Text(s.start + " – " + s.end, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (active) {
                Spacer(Modifier.weight(1f))
                Text("还剩 " + Math.max(1, ceil(remain).toInt()) + " 分钟", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
internal fun SpecialSlotCard(item: GroupItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.label.ifBlank { "特殊安排" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "特殊",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 10.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            item.start + " – " + item.end,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
