package com.ioo.watchnote

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun SpecialItemEditPage(
    store: Store,
    ruleId: String,
    index: Int,
    onBack: () -> Unit
) {
    val rule = remember(ruleId, store.version.intValue) { store.specials.firstOrNull { it.id == ruleId } }
    val item = rule?.items?.getOrNull(index)
    if (rule == null || item == null) {
        SubPageFrame(onBack) {
            PageTitle("时间")
            Text("这条时间已失效", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        return
    }
    var start by remember { mutableIntStateOf(Store.minsOf(item.start)) }
    var end by remember { mutableIntStateOf(Store.minsOf(item.end)) }
    var label by remember { mutableStateOf(item.label) }
    SubPageFrame(onBack) {
        PageTitle("编辑特殊时间")
        FieldLabel("开始时间")
        TimeStepper(start) { start = it }
        FieldLabel("结束时间")
        TimeStepper(end) { end = it }
        FieldLabel("名称（可选，不填显示时间）")
        TextFieldBox(label, { label = it }, "例如：考试 / 调课")
        Spacer(Modifier.height(8.dp))
        SettingPill("✓", "保存", onClick = {
            rule.items[index] = GroupItem(Store.fmtMins(start), Store.fmtMins(end), label)
            store.save()
            onBack()
        })
        SettingPill("✕", "删除这条时间", danger = true, onClick = {
            rule.items.removeAt(index)
            store.save()
            onBack()
        })
    }
}
