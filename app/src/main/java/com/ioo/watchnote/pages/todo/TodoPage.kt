package com.ioo.watchnote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.SplitRadioButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePickerDefaults
import androidx.wear.compose.material3.rememberPickerState

/* ============================================================
   办事（待办）：列表 / 定时间 / 编辑
   和笔记分开：只有这里才有 todo=true 的条目。
   ============================================================ */

private const val DAY_MS = 86_400_000L
private const val MIN_MS = 60_000L

/** 「哪一天」滚轮：今天起可选 14 天。 */
private const val DAY_OPTIONS = 14

/** 「今天 20:00-21:00」；跨天写「次日」，过期的写「逾期 M/d」。 */
private fun whenLabel(n: Note): String {
    if (n.dueAt <= 0L) return "没定时间"
    val diff = n.dueAt - Store.startOfToday()
    val prefix = when {
        diff < 0L -> "逾期 " + Store.fmtDay(n.dueAt)
        diff < DAY_MS -> "今天"
        diff < 2 * DAY_MS -> "明天"
        diff < 3 * DAY_MS -> "后天"
        else -> Store.fmtDay(n.dueAt)
    }
    val end = if (n.endAt > n.dueAt) {
        val cross = (n.endAt - Store.startOfToday()) / DAY_MS != (n.dueAt - Store.startOfToday()) / DAY_MS
        "-" + (if (cross) "次日 " else "") + Store.hhmm(n.endAt)
    } else ""
    return prefix + " " + Store.hhmm(n.dueAt) + end
}

private fun dayLabel(base: Long): String {
    val diff = base - Store.startOfToday()
    val days = Math.round(diff / DAY_MS.toDouble()).toInt()
    return when {
        days < 0 -> Store.fmtDay(base)
        days == 0 -> "今天"
        days == 1 -> "明天"
        days == 2 -> "后天"
        else -> Store.fmtDay(base)
    }
}

@Composable
internal fun TodoPage(
    store: Store,
    refresh: Int,
    fullAnim: Boolean,
    active: Boolean,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onChanged: () -> Unit
) {
    store.version.intValue
    val todos = store.todoNotes()
    WearListPage(edgeIcon = "＋", onEdge = { Haptics.tap(); onAdd() }, fishEye = fullAnim, active = active) {
        item {
            Fish { PageTitle("办事", Store.fmtDate() + " · 待办 " + todos.count { !it.done } + " 条") }
        }
        if (todos.isEmpty()) {
            item {
                Fish {
                    Text(
                        "还没有要办的事。\n点右下角 ＋ 加一件。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp)
                    )
                }
            }
        }
        todos.forEachIndexed { idx, n ->
            item(key = n.id.ifBlank { "todo-" + idx }) {
                Fish {
                    TodoCard(
                        note = n,
                        onClick = { Haptics.tap(); onOpen(n.id) },
                        onToggle = { Haptics.tap(); store.toggleDone(n.id); onChanged() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoCard(note: Note, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 4.dp)
    ) {
        SplitRadioButton(
            selected = note.done,
            onSelectionClick = onToggle,
            selectionContentDescription = if (note.done) "标记未办完" else "标记已办完",
            onContainerClick = onClick,
            containerClickLabel = "打开详细内容",
            modifier = Modifier.weight(1f),
            secondaryLabel = {
                Text(
                    whenLabel(note),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            label = {
                Text(
                    if (note.text.isBlank()) "（没写内容）" else note.text.trim(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        )
    }
}

/* ---------------- 定时间：哪一天 → 滚轮选开始 → 滚轮选结束 ---------------- */

private enum class TimeStep { Day, Start, End }

private fun roundUpQuarter(min: Int): Int = (((min + 14) / 15) * 15) % 1440

/** 按「天」算差距：跨月、跨年都不会算错。 */
private fun dayIndex(ms: Long): Long = Math.floorDiv(ms - Store.startOfToday(), DAY_MS)

/** 结束时间的写法：「次日 01:30」/「22:00」。 */
private fun endLabel(start: Long, end: Long): String {
    if (end <= start) return "没设"
    val cross = dayIndex(end) != dayIndex(start)
    return (if (cross) "次日 " else "") + Store.hhmm(end)
}

/** 新办事：从今天开始选。 */
@Composable
internal fun TodoTimePage(store: Store, onBack: () -> Unit, onNext: (Long, Long) -> Unit) {
    TimePickFlow(
        initialStart = 0L,
        initialEnd = 0L,
        requireFuture = true,
        onBack = onBack,
        onDone = { start, end -> onNext(start, end) }
    )
}

/**
 * 选时间三步：哪一天（Picker 滚轮）→ 开始（TimePicker）→ 结束（TimePicker）。
 * 编辑页点「时间」也走这里（initialStart/initialEnd 预填上次选的），随时能重设。
 */
@Composable
internal fun TimePickFlow(
    initialStart: Long,
    initialEnd: Long,
    requireFuture: Boolean,
    onBack: () -> Unit,
    onDone: (Long, Long) -> Unit
) {
    val today0 = remember { Store.startOfToday() }
    val firstDay = remember(initialStart, today0) {
        if (initialStart > 0L) today0 + dayIndex(initialStart) * DAY_MS else today0
    }
    var dayBase by remember(initialStart) { mutableLongStateOf(firstDay) }
    var step by remember { mutableStateOf(TimeStep.Day) }
    val nowQuarter = remember { roundUpQuarter(Store.nowMins().toInt()) }
    var startMin by remember {
        mutableIntStateOf(
            if (initialStart > 0L) (((initialStart - firstDay) / MIN_MS).toInt()).coerceIn(0, 1439)
            else nowQuarter
        )
    }
    var endMin by remember {
        mutableIntStateOf(
            if (initialEnd > initialStart) ((((initialEnd - firstDay) / MIN_MS) % 1440).toInt()).coerceIn(0, 1439)
            else (startMin + 60) % 1440
        )
    }
    var warn by remember { mutableStateOf("") }

    fun confirm() {
        Haptics.tap()
        val start = dayBase + startMin * MIN_MS
        var end = dayBase + endMin * MIN_MS
        if (end <= start) end += DAY_MS
        if (requireFuture && end <= System.currentTimeMillis()) {
            warn = "这个时间已经过了"
            step = TimeStep.Day
        } else {
            warn = ""
            onDone(start, end)
        }
    }

    BackHandler(enabled = step != TimeStep.Day) {
        step = if (step == TimeStep.End) TimeStep.Start else TimeStep.Day
    }

    when (step) {
        TimeStep.Start -> TimeWheelStep(
            label = "开始时间",
            initialMinuteOfDay = startMin,
            onBack = { step = TimeStep.Day },
            onPicked = { m ->
                startMin = m
                if (endMin == m) endMin = (m + 60) % 1440
                step = TimeStep.End
            }
        )
        TimeStep.End -> TimeWheelStep(
            label = "结束时间",
            initialMinuteOfDay = endMin,
            onBack = { step = TimeStep.Start },
            onPicked = { m -> endMin = m; confirm() }
        )
        TimeStep.Day -> DayWheelStep(
            initialDay = dayBase,
            hint = warn,
            onBack = onBack,
            onPicked = { picked ->
                dayBase = picked
                warn = ""
                step = TimeStep.Start
            }
        )
    }
}

/**
 * 时 / 分双滚轮选时间（和「哪一天」同一个 Picker 样式）。
 * 不能用官方 TimePicker：它内部的 Picker 走默认表冠吸附行为，小米手表缺
 * com.google.wear.input.WearHapticFeedbackConstants，一进这个页面就
 * NoClassDefFoundError 闪退。这里两个 Picker 都显式 rotaryScrollableBehavior = null。
 */
@Composable
private fun TimeWheelStep(
    label: String,
    initialMinuteOfDay: Int,
    onBack: () -> Unit,
    onPicked: (Int) -> Unit
) {
    val hourState = rememberPickerState(
        initialNumberOfOptions = 24,
        initiallySelectedIndex = (initialMinuteOfDay / 60).coerceIn(0, 23),
        shouldRepeatOptions = true
    )
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedIndex = (initialMinuteOfDay % 60).coerceIn(0, 59),
        shouldRepeatOptions = true
    )
    val confirmColors = TimePickerDefaults.timePickerColors()
    val hour = hourState.selectedOptionIndex
    val minute = minuteState.selectedOptionIndex

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 选中行高亮：左右各一个胶囊，框比字小一圈，抬眼看就知道选了哪个
        Row(modifier = Modifier.align(Alignment.Center).height(48.dp)) {
            Box(
                Modifier
                    .width(68.dp)
                    .fillMaxHeight()
                    .clip(squircle(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier
                    .width(68.dp)
                    .fillMaxHeight()
                    .clip(squircle(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center).height(206.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(68.dp).fillMaxHeight()) {
                Picker(
                    state = hourState,
                    contentDescription = { "小时 " + hour },
                    modifier = Modifier.fillMaxSize(),
                    rotaryScrollableBehavior = null,
                    option = { index ->
                        val picked = index == hour
                        Text(
                            index.toString().padStart(2, '0'),
                            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (picked) 24.sp else 17.sp,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
            Text(
                ":",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                modifier = Modifier.width(18.dp),
                textAlign = TextAlign.Center
            )
            Box(Modifier.width(68.dp).fillMaxHeight()) {
                Picker(
                    state = minuteState,
                    contentDescription = { "分钟 " + minute },
                    modifier = Modifier.fillMaxSize(),
                    rotaryScrollableBehavior = null,
                    option = { index ->
                        val picked = index == minute
                        Text(
                            index.toString().padStart(2, '0'),
                            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (picked) 24.sp else 17.sp,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        // 上下两头淡出，循环滚起来时不硬顶屏幕边缘
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
        )
        // 返回键放圆屏左边中间（底部中间留给确认键）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { Haptics.tap(); onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
        }
        // 确认键：跟前面「哪一天」那一步长得一样
        EdgeButton(
            onClick = { Haptics.tap(); onPicked(hour * 60 + minute) },
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(
                contentColor = confirmColors.confirmButtonContentColor,
                containerColor = confirmColors.confirmButtonContainerColor
            )
        ) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 官方 Picker 滚轮：选哪一天（今天起 14 天，到头会绕回今天，转起来更顺）。
 * 中间那一行高亮（颜色 + 胶囊底），不然看不出选了哪一行。
 * 显式把 rotaryScrollableBehavior 设为 null —— 小米手表缺 Wear 触感类，
 * 官方默认的表冠吸附行为会触发 NoClassDefFoundError 闪退，这里只用手指拖。
 */
@Composable
private fun DayWheelStep(
    initialDay: Long,
    hint: String,
    onBack: () -> Unit,
    onPicked: (Long) -> Unit
) {
    val today = remember { Store.startOfToday() }
    val days = remember(today) { (0 until DAY_OPTIONS).map { today + it * DAY_MS } }
    val initialIndex = remember(today, initialDay) {
        (((initialDay - today) / DAY_MS).toInt()).coerceIn(0, DAY_OPTIONS - 1)
    }
    val pickerState = rememberPickerState(
        initialNumberOfOptions = days.size,
        initiallySelectedIndex = initialIndex,
        shouldRepeatOptions = true
    )
    val confirmColors = TimePickerDefaults.timePickerColors()
    val selectedIndex = pickerState.selectedOptionIndex
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 高亮胶囊：Picker 的选中项固定停在屏幕正中间。
        // 框比字小一圈，选中项的字会明显顶满框，抬眼看就知道选了什么。
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(152.dp)
                .height(48.dp)
                .clip(squircle(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Picker(
            state = pickerState,
            contentDescription = { dayLabel(days[selectedIndex]) },
            modifier = Modifier.fillMaxSize(),
            rotaryScrollableBehavior = null,
            option = { index ->
                val picked = index == selectedIndex
                Text(
                    dayLabel(days[index]),
                    color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (picked) 24.sp else 17.sp,
                    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal
                )
            }
        )
        // 上下两头淡出：循环滚起来时列表不会硬生生顶在屏幕边缘
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(76.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("哪一天", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { Haptics.tap(); onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
        }
        // 确认键用官方 TimePicker 同款 EdgeButton（同 color token），跟前后两步长得一样
        EdgeButton(
            onClick = { Haptics.tap(); onPicked(days[pickerState.selectedOptionIndex]) },
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.Small,
            colors = ButtonDefaults.buttonColors(
                contentColor = confirmColors.confirmButtonContentColor,
                containerColor = confirmColors.confirmButtonContainerColor
            )
        ) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ---------------- 编辑：内容 + 标签 + 状态 + 时间（点一下就重设） ---------------- */

@Composable
internal fun TodoEditPage(
    store: Store,
    noteId: String,
    presetStart: Long,
    presetEnd: Long,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val existing = remember(noteId) { store.getNote(noteId) }
    var text by remember { mutableStateOf(if (existing != null && existing.todo) existing.text else "") }
    var tag by remember { mutableStateOf(existing?.tag ?: "") }
    var done by remember { mutableStateOf(existing?.done ?: false) }
    var start by remember {
        mutableLongStateOf(
            when {
                existing != null && existing.todo && existing.dueAt > 0 -> existing.dueAt
                presetStart > 0 -> presetStart
                else -> Store.startOfToday() + (((Store.nowMins().toInt() + 14) / 15) * 15) * MIN_MS
            }
        )
    }
    var end by remember {
        mutableLongStateOf(
            when {
                existing != null && existing.todo && existing.endAt > existing.dueAt -> existing.endAt
                presetEnd > start -> presetEnd
                else -> start + 60 * MIN_MS
            }
        )
    }
    var picking by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val isNew = existing == null || !existing.todo

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubPageFrame(onBack = onBack) {
            PageTitle(if (isNew) "新办事" else "编辑办事")
            FieldLabel("内容")
            TextFieldBox(
                value = text,
                onValueChange = { text = it },
                placeholder = "要做什么？比如 交作业 / 拿快递",
                singleLine = false,
                minLines = 3,
                heightDp = 108
            )
            FieldLabel("标签")
            TextFieldBox(
                value = tag,
                onValueChange = { tag = it },
                placeholder = "输入标签，比如：作业 / 快递"
            )
            FieldLabel("状态")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("未办完", !done, onClick = { done = false })
                ChoiceChip("已办完", done, onClick = { done = true })
            }
            FieldLabel("时间")
            TimeSummaryCard(start = start, end = end, onClick = { picking = true })
            Spacer(Modifier.height(8.dp))
            if (saveFailed) {
                Text(
                    "没保存成功，再点一次试试",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
            SettingPill("✓", if (isNew) "添加" else "保存", onClick = {
                saveFailed = false
                val draft = Note(
                    id = if (existing != null && existing.todo) existing.id else "",
                    text = text,
                    tag = tag,
                    marked = existing?.marked ?: false,
                    done = done,
                    todo = true,
                    dueAt = start,
                    endAt = end,
                    audioPath = existing?.audioPath ?: "",
                    audioDur = existing?.audioDur ?: 0,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                val saved = store.saveNote(draft)
                if (saved != null) {
                    Haptics.tap()
                    onChanged()
                    onBack()
                } else {
                    text = text.trim()
                    saveFailed = true
                }
            })
            if (!isNew) {
                SettingPill("🗑", "删除这条", danger = true, onClick = { askDelete = true })
            }
        }
        // 点「时间」→ 原地起滚轮流程（预填现在选的时间），选完回到这一页，写到一半的内容不丢
        if (picking) {
            TimePickFlow(
                initialStart = start,
                initialEnd = end,
                requireFuture = isNew,
                onBack = { picking = false },
                onDone = { s, e ->
                    start = s
                    end = e
                    picking = false
                }
            )
        }
    }
    BackHandler(enabled = picking) { picking = false }
    if (askDelete) {
        ConfirmDialog(
            ConfirmData("删除办事", "这条删掉后找不回来。", "删除", true) {
                store.deleteNote(noteId)
                askDelete = false
                onChanged()
                onBack()
            }
        ) { askDelete = false }
    }
}

/** 已选的时间：开始/截止两行看得清，点一下就能重选（保底）。 */
@Composable
private fun TimeSummaryCard(start: Long, end: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(squircle(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, squircle(18.dp))
            .clickable { Haptics.tap(); onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dayLabel(start) + " " + Store.hhmm(start) + " 开始",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                endLabel(start, end) + " 截止",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "改时间",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
