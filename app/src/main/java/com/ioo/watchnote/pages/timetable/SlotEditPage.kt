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
   数据 / 导航
   ============================================================ */

/* ============================================================
   子页面：时段编辑
   ============================================================ */

@Composable
internal fun SlotEditPage(activity: MainActivity, store: Store, slotId: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val existing = remember(slotId) { if (slotId.isEmpty()) null else store.getSlot(slotId) }
    var id by remember { mutableStateOf(existing?.id ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "class") }
    var start by remember { mutableIntStateOf(Store.minsOf(existing?.start ?: "08:00")) }
    var end by remember { mutableIntStateOf(Store.minsOf(existing?.end ?: "08:45")) }
    var days by remember { mutableStateOf(existing?.days?.toMutableList() ?: mutableListOf<Int>()) }
    var showDelete by remember { mutableStateOf(false) }
    var pickGroup by remember { mutableStateOf(false) }
    var pickGroupName by remember { mutableStateOf<String?>(null) }
    store.version.intValue
    val groupRanges = store.groups.flatMap { g -> g.items.map { g.name to it } }

    fun save() {
        val draft = Slot(id = id, name = name, type = type, start = Store.fmtMins(start), end = Store.fmtMins(end), days = days.toMutableList())
        val saved = store.saveSlot(draft)
        if (saved != null) id = saved.id
        onSaved()
        activity.toast("已保存")
    }

    SubPageFrame(onBack) {
        PageTitle(if (existing == null) "添加时段" else "编辑时段")
        FieldLabel("名称")
        TextFieldBox(name, { name = it }, "例如：第一节 / 午饭 / 晚自习")
        FieldLabel("类型")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Store.TYPE_KEYS.forEachIndexed { i, key ->
                BigChoiceChip(Store.TYPE_LABELS[i], type == key, { type = key }, Modifier.weight(1f))
            }
        }
        FieldLabel("开始时间")
        TimeStepper(start) { start = it }
        FieldLabel("结束时间")
        TimeStepper(end) { end = it }
        if (groupRanges.isNotEmpty()) {
            SettingPill("↧", "选时间组", onClick = { pickGroup = true; pickGroupName = null })
        }
        FieldLabel("重复")
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChoiceChip("每天", days.isEmpty(), { days = mutableListOf() }, Modifier.weight(1f))
            for (d in 0..2) {
                ChoiceChip(Store.DAY_NAMES[d], days.contains(d), {
                    val nextDays = days.toMutableList()
                    if (nextDays.contains(d)) nextDays.remove(d) else nextDays.add(d)
                    days = nextDays
                }, Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 3..6) {
                ChoiceChip(Store.DAY_NAMES[d], days.contains(d), {
                    val nextDays = days.toMutableList()
                    if (nextDays.contains(d)) nextDays.remove(d) else nextDays.add(d)
                    days = nextDays
                }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingPill("✓", "保存", onClick = {
            save()
            onBack()
        })
        if (existing != null) {
            if (!showDelete) {
                SettingPill("✕", "删除", danger = true, onClick = { showDelete = true })
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        SettingPill("✕", "确定删除", danger = true, onClick = {
                            store.deleteSlot(id)
                            onSaved()
                            onBack()
                        })
                    }
                    Box(Modifier.weight(1f)) {
                        SettingPill("←", "取消", onClick = { showDelete = false })
                    }
                }
            }
        }
    }
    if (pickGroup && groupRanges.isNotEmpty()) {
        Dialog(onDismissRequest = { pickGroup = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(14.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val currentGroup = pickGroupName
                    if (currentGroup == null) {
                        Text("选择时间组", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
                        val names = groupRanges.map { it.first }.distinct()
                        names.forEach { gName ->
                            val count = groupRanges.count { it.first == gName }
                            SettingPill("◷", gName + "  (" + count + " 段)", onClick = { pickGroupName = gName })
                        }
                    } else {
                        Text(currentGroup, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
                        groupRanges.filter { it.first == currentGroup }.forEach { pair ->
                            val item = pair.second
                            SettingPill(
                                "◷",
                                if (item.label.isNotBlank()) item.label + "  " + item.start + "–" + item.end
                                else item.start + "–" + item.end,
                                onClick = {
                                    start = Store.minsOf(item.start)
                                    end = Store.minsOf(item.end)
                                    pickGroup = false
                                    pickGroupName = null
                                }
                            )
                        }
                        SettingPill("←", "返回时间组", onClick = { pickGroupName = null })
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingPill("←", "取消", onClick = { pickGroup = false })
                }
            }
        }
    }
}

@Composable
internal fun TimeStepper(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−时") { onChange((value - 60 + 1440) % 1440) }
        Spacer(Modifier.width(4.dp))
        StepButton("−分") { onChange((value - 5 + 1440) % 1440) }
        Text(
            Store.fmtMins(value),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        StepButton("+分") { onChange((value + 5) % 1440) }
        Spacer(Modifier.width(4.dp))
        StepButton("+时") { onChange((value + 60) % 1440) }
    }
}

@Composable
internal fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(squircle(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, maxLines = 1, softWrap = false)
    }
}
