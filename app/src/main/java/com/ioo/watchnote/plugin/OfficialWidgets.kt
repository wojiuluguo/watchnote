package com.ioo.watchnote.plugin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ioo.watchnote.ChoiceChip
import com.ioo.watchnote.ColorCard as HostColorCard
import com.ioo.watchnote.Haptics
import com.ioo.watchnote.SettingPill
import com.ioo.watchnote.squircle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================
   官方 UI 组件（0.2）

   这一层的意义：插件要画「官方样子」的东西，不用自己拼 Compose，
   也不许自己拼 —— 用的就是宿主这同一份实现，所以圆屏边距、主题色、
   字号、点击反馈自然跟着走，插件再多也不会出现第 18 种风格。

   对外入口是 Official.*（Kotlin 插件）和 SimplePage 的行方法（Java 插件），
   两边最终都落到下面这些 internal 函数，保证同一份实现。
   ============================================================ */

/** 小节标题：一组行前面的灰色小标题。 */
@Composable
internal fun OfficialSection(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

/** 一段说明文字：比正文小一号。 */
@Composable
internal fun OfficialNote(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
    )
}

/** 大数字卡片：标签 + 中间一行大字 + 可选副标题。 */
@Composable
internal fun OfficialHero(label: String, value: String, sub: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (sub != null) {
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 两行列表行：左边符号、上面标题、下面小字、右边状态；onClick 为空则不可点。 */
@Composable
internal fun OfficialItem(
    symbol: String,
    title: String,
    sub: String?,
    trailing: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable { Haptics.tap(); onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(
                    sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** 进度条：标签 + 百分比 + 一根 8dp 的进度条 + 已完成/总量。 */
@Composable
internal fun OfficialProgress(label: String, value: Long, total: Long, trailing: String?) {
    val frac = if (total <= 0L) 0f else (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                trailing ?: ((frac * 100f).toInt().toString() + "%"),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (frac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(value.toString() + " / " + total, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

/** 环形进度：圆环 + 中间百分比，圆屏上最省地方。 */
@Composable
internal fun OfficialRing(label: String, value: Long, total: Long) {
    val frac = if (total <= 0L) 0f else (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(96.dp)) {
                val strokeW = 10.dp.toPx()
                val inset = strokeW / 2f
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                if (frac > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * frac,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ((frac * 100f).toInt()).toString() + "%",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 62.dp)
                )
            }
        }
        Text(
            value.toString() + " / " + total,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** 步进里的 − / + 小方钮（和官方时间步进同尺寸）。 */
@Composable
private fun StepSquare(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(squircle(17.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { Haptics.tap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}

/** 数值步进：标签 + − 数值 +，结果夹在 min..max，每次加减 step。 */
@Composable
internal fun OfficialStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    val stepValue = if (step <= 0) 1 else step
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        StepSquare("−") { onChange((value - stepValue).coerceIn(min, max)) }
        Text(
            value.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        StepSquare("＋") { onChange((value + stepValue).coerceIn(min, max)) }
    }
}

/** 一排可选项：每行最多 4 个，超出自动换行；选中项高亮。 */
@Composable
internal fun OfficialChips(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        if (label.isNotEmpty()) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 3.dp)
            )
        }
        options.chunked(4).forEachIndexed { chunkIndex, chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chunk.forEachIndexed { j, option ->
                    val index = chunkIndex * 4 + j
                    ChoiceChip(
                        text = option,
                        active = index == selected,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat((4 - chunk.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 整块彩色行：底色就是 colorHex（#RRGGBB），点一下回调。 */
@Composable
internal fun OfficialColorCard(
    label: String,
    colorHex: String,
    trailing: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val parsed = remember(colorHex) {
        runCatching { android.graphics.Color.parseColor(colorHex) }.getOrNull()
    }
    val cardColor = if (parsed != null) Color(parsed) else MaterialTheme.colorScheme.primary
    HostColorCard(
        cardColor = cardColor,
        label = label,
        trailing = trailing,
        active = active,
        onClick = onClick
    )
}

/** 时间摘要卡：开始/截止两行看得清，点一下回调。 */
@Composable
internal fun OfficialTimeCard(start: Long, end: Long, trailing: String?, onClick: (() -> Unit)?) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(squircle(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, squircle(18.dp))
            .then(if (onClick != null) Modifier.clickable { Haptics.tap(); onClick() } else Modifier)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                fmt.format(Date(start)) + " 开始",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                fmt.format(Date(end)) + " 截止",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 开关行：右侧显示 开/关，点一下切换。 */
@Composable
internal fun OfficialToggle(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    SettingPill(
        symbol = "⇄",
        label = label,
        trailing = if (on) "开" else "关",
        onClick = { Haptics.tap(); onToggle(!on) }
    )
}

/** 一条分割线。 */
@Composable
internal fun OfficialDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/** 空白占位。 */
@Composable
internal fun OfficialSpace(dp: Int) {
    Spacer(Modifier.height(dp.coerceAtLeast(0).dp))
}

/** 空状态文字：居中一行灰字。 */
@Composable
internal fun OfficialEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

/** 柱状小图：柱子相对高度，自动按最大值缩放；trailing 是右边一句话。 */
@Composable
internal fun OfficialBars(label: String, values: List<Float>, trailing: String?) {
    val maxValue = (values.maxOrNull() ?: 0f).takeIf { it > 0f } ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.height(34.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEach { v ->
                    val h = (34f * (v / maxValue)).coerceIn(3f, 34f)
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                    )
                }
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

/**
 * 宿主渲染声明式页面的一行。
 *
 * 所有行都走 Official.*，也就是和 Kotlin 插件手动调用的是同一份实现；
 * 带参数的行按 SimplePage KDoc 里写的冒号约定回调。
 */
@Composable
internal fun SimpleRowView(row: SimpleRow, page: SimplePage) {
    when (row.kind) {
        SimpleRowKind.SECTION -> Official.Section(row.label)
        SimpleRowKind.NOTE -> Official.Note(row.label)
        SimpleRowKind.STAT -> Official.Stat(row.label, row.value)
        SimpleRowKind.HERO -> Official.Hero(row.label, row.value, row.sub.ifEmpty { null })
        SimpleRowKind.BUTTON -> Official.Button(
            symbol = row.symbol.ifEmpty { "▸" },
            label = row.label,
            trailing = row.value.ifEmpty { null },
            danger = row.danger,
            onClick = { page.dispatch(row.actionId) },
        )
        SimpleRowKind.ITEM -> Official.Item(
            symbol = row.symbol.ifEmpty { "▸" },
            title = row.label,
            sub = row.sub.ifEmpty { null },
            trailing = row.value.ifEmpty { null },
            onClick = { page.dispatch(row.actionId) },
        )
        SimpleRowKind.PROGRESS -> Official.Progress(row.label, row.done, row.total, row.value.ifEmpty { null })
        SimpleRowKind.RING -> Official.Ring(row.label, row.done, row.total)
        SimpleRowKind.STEPPER -> Official.Stepper(
            label = row.label,
            value = row.current,
            min = row.min,
            max = row.max,
            step = row.step,
        ) { newValue ->
            val delta = newValue - row.current
            if (delta != 0) page.dispatchDelta(row.actionId, delta)
        }
        SimpleRowKind.CHIPS -> Official.Chips(row.label, row.options, row.selected) { index ->
            page.dispatchIndexed(row.actionId, index)
        }
        SimpleRowKind.COLOR -> Official.ColorCard(
            label = row.label,
            colorHex = row.colorHex,
            trailing = row.value,
            active = row.active,
            onClick = { page.dispatch(row.actionId) },
        )
        SimpleRowKind.TIME -> Official.TimeCard(row.start, row.end, row.value.ifEmpty { null }) {
            page.dispatch(row.actionId)
        }
        SimpleRowKind.TOGGLE -> Official.Toggle(row.label, row.active) { on ->
            page.dispatchToggle(row.actionId, on)
        }
        SimpleRowKind.DIVIDER -> Official.Divider()
        SimpleRowKind.SPACE -> Official.Space(row.amount)
        SimpleRowKind.EMPTY -> Official.Empty(row.label)
        SimpleRowKind.BARS -> Official.Bars(row.label, row.numbers, row.value.ifEmpty { null })
    }
}
