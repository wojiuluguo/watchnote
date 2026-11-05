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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun GroupEditPage(
    store: Store,
    groupId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditItem: (Int) -> Unit
) {
    store.version.intValue
    val group = remember(groupId, store.version.intValue) { store.getGroup(groupId) }
    if (group == null) {
        SubPageFrame(onBack) {
            PageTitle("时间组")
            Text("这个时间组已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    var day by remember { mutableIntStateOf(Store.todayIndex()) }
    var overwrite by remember { mutableStateOf(true) }
    var confirmApply by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    SubPageFrame(onBack) {
        PageTitle(group.name.ifBlank { "时间组" })
        FieldLabel("名称")
        TextFieldBox(group.name, { group.name = it }, "例如：上午课 / 周三")
        FieldLabel("时间列表（点一条编辑）")
        if (group.items.isEmpty()) {
            Text(
                "还没有时间，点下面添加\n或从今天的时间表导入",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
        group.items.forEachIndexed { i, item ->
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
            group.items.add(GroupItem())
            store.save()
        })
        SettingPill("↓", "从今天的时间表导入", onClick = {
            group.items.clear()
            store.forDay(Store.todayIndex()).forEach { s ->
                group.items.add(GroupItem(s.start, s.end, ""))
            }
            store.save()
        })
        FieldLabel("应用到哪一天")
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
            for (d in 0..6) {
                ChoiceChip(Store.DAY_NAMES[d], day == d, { day = d }, Modifier.weight(1f))
            }
        }
        FieldLabel("模式")
        Row(Modifier.fillMaxWidth()) {
            ChoiceChip("覆盖", overwrite, { overwrite = true }, Modifier.weight(1f))
            ChoiceChip("合并", !overwrite, { overwrite = false }, Modifier.weight(1f))
        }
        Text(
            if (overwrite) "覆盖：这一天不在组里的时段会被删除"
            else "合并：只补组里的时间，原有的不动",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingPill("✓", "应用到这一天", onClick = { confirmApply = true })
        Spacer(Modifier.height(6.dp))
        SettingPill("✓", "保存时间组", onClick = {
            store.saveGroup(group)
            onSaved()
        })
        SettingPill("✕", "删除时间组", danger = true, onClick = {
            store.deleteGroup(group.id)
            onSaved()
        })
        pendingDelete?.let { idx ->
            val item = group.items.getOrNull(idx)
            ConfirmDialog(
                ConfirmData(
                    if (item != null) item.label.ifBlank { item.start + " – " + item.end } else "删除",
                    if (item != null) item.start + " – " + item.end else "",
                    "删除", true, {
                        if (idx in group.items.indices) group.items.removeAt(idx)
                        store.save()
                        pendingDelete = null
                    }
                ),
                onClosed = { pendingDelete = null }
            )
        }
        if (confirmApply) {
            ConfirmDialog(
                ConfirmData(
                    if (overwrite) "覆盖模式" else "合并模式",
                    if (overwrite) "这一天现有的时段会全部被删除，替换成组里的时间。\n确定应用吗？"
                    else "与组时间重叠的原有段会被删除并替换，其他保留。\n确定应用吗？",
                    "应用", false, {
                        confirmApply = false
                        store.applyGroupToDay(group, day, overwrite)
                        onSaved()
                    }
                ),
                onClosed = { confirmApply = false }
            )
        }
    }
}
