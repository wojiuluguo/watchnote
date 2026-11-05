package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun SpecialEditPage(
    store: Store,
    ruleId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditItem: (Int) -> Unit
) {
    store.version.intValue
    val rule = remember(ruleId, store.version.intValue) { store.specials.firstOrNull { it.id == ruleId } }
    if (rule == null) {
        SubPageFrame(onBack) {
            PageTitle("特殊时段")
            Text("这个特殊时段已结束", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    SubPageFrame(onBack) {
        PageTitle("特殊时段")
        Text(
            "周" + Store.DAY_NAMES.getOrElse(rule.weekday) { "?" } + " · " + Store.fmtDayEpoch(rule.startEpochDay) + " 开始 · 共 " + rule.weeks + " 周",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        FieldLabel("星期几（本周几）")
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
            for (d in 0..6) {
                ChoiceChip(Store.DAY_NAMES[d], rule.weekday == d, {
                    rule.weekday = d
                    var start = store.epochOfThisWeek(d)
                    if (start < store.epochNow()) start += 7
                    rule.startEpochDay = start
                    store.save()
                }, Modifier.weight(1f))
            }
        }
        FieldLabel("持续几周")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") {
                rule.weeks = (rule.weeks - 1).coerceAtLeast(1)
                store.save()
            }
            Text(
                rule.weeks.toString() + " 周",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            StepButton("＋") {
                rule.weeks = (rule.weeks + 1).coerceAtMost(30)
                store.save()
            }
        }
        FieldLabel("这一天的时段（点一条编辑，长按删除）")
        if (rule.items.isEmpty()) {
            Text("还没有时间，点下面添加", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        rule.items.forEachIndexed { i, item ->
            val hold = rememberHoldProgress()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .heightIn(min = 44.dp)
                    .clip(squircle(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .drawBehind {
                        val a = holdTintAlpha(hold.floatValue)
                        if (a > 0f) drawRect(androidx.compose.ui.graphics.Color.Red.copy(alpha = a))
                    }
                    .holdToDelete(hold, onTap = { onEditItem(i) }, onHold = { pendingDelete = i })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.start + " – " + item.end, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(item.label.ifBlank { "未命名" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                Spacer(Modifier.weight(1f))
                Text("✎", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        SettingPill("＋", "添加一条时间", onClick = {
            rule.items.add(GroupItem())
            store.save()
        })
        SettingPill("↓", "从原时间表导入这一天", onClick = {
            rule.items.clear()
            store.forDay(rule.weekday).forEach { s -> rule.items.add(GroupItem(s.start, s.end, s.name)) }
            store.save()
        })
        SettingPill("✓", "保存", onClick = {
            store.saveSpecial(rule)
            onSaved()
        })
        SettingPill("✕", "关闭特殊时段", danger = true, onClick = {
            store.closeSpecial(rule.id)
            onSaved()
        })
    }
    pendingDelete?.let { idx ->
        val item = rule.items.getOrNull(idx)
        ConfirmDialog(
            ConfirmData(
                if (item != null) item.label.ifBlank { item.start + " – " + item.end } else "删除",
                if (item != null) item.start + " – " + item.end else "",
                "删除", true, {
                    if (idx in rule.items.indices) rule.items.removeAt(idx)
                    store.save()
                    pendingDelete = null
                }
            ),
            onClosed = { pendingDelete = null }
        )
    }
}
