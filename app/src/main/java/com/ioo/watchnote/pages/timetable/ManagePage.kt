package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun ManagePage(store: Store, refresh: Int, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onGroups: () -> Unit) {
    store.version.intValue
    val dayState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(Store.todayIndex()) }
    val day = dayState.intValue
    val items = store.forDay(day)
    val pending = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Slot?>(null) }
    SubPageListFrame(onBack = onBack) {
        item(key = "manage-title") {
            Fish { PageTitle("时间表管理", Store.DAY_NAMES.getOrElse(day) { "?" } + " · 共 " + items.size + " 个时段") }
        }
        item(key = "manage-days") {
            Fish {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (d in 0..6) {
                        DayChip(
                            text = Store.DAY_NAMES[d],
                            active = day == d,
                            onClick = { dayState.intValue = d },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item(key = "manage-add") {
            Fish { SettingPill("＋", "添加时段", onClick = onAdd) }
        }
        item(key = "manage-groups") {
            Fish { SettingPill("◷", "时间组", onClick = onGroups) }
        }
        items.forEachIndexed { idx, s ->
            item(key = "manage-" + s.id.ifBlank { idx.toString() }) {
                Fish {
                    val hold = rememberHoldProgress()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .heightIn(min = 48.dp)
                            .clip(squircle(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .drawBehind {
                                val a = holdTintAlpha(hold.floatValue)
                                if (a > 0f) drawRect(androidx.compose.ui.graphics.Color.Red.copy(alpha = a))
                            }
                            .holdToDelete(hold, onTap = { onEdit(s.id) }, onHold = { pending.value = s })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                s.start + " – " + s.end + " · " + repeatText(s.days),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            Store.typeLabel(s.type),
                            color = Store.typeColor(s.type),
                            fontSize = 10.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Store.typeBg(s.type)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
    pending.value?.let { s ->
        ConfirmDialog(
            ConfirmData(s.name, s.start + " – " + s.end + "\n确定删除这个时段吗？", "删除", true, {
                store.deleteSlot(s.id)
                pending.value = null
            }),
            onClosed = { pending.value = null }
        )
    }
}

internal fun repeatText(days: List<Int>): String {
    if (days.isEmpty()) return "每天"
    val sb = StringBuilder("周")
    days.forEachIndexed { i, d ->
        if (i > 0) sb.append("/")
        sb.append(Store.DAY_NAMES.getOrElse(d) { "?" })
    }
    return sb.toString()
}

@Composable
private fun DayChip(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(squircle(15.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}
