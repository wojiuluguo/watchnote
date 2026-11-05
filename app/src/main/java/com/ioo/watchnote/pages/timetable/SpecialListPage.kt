package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun SpecialListPage(
    store: Store,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    store.version.intValue
    val rules = store.activeSpecials()
    SubPageListFrame(onBack = onBack) {
        item(key = "special-title") {
            Fish { PageTitle("特殊时段", "共 " + rules.size + " 个") }
        }
        item(key = "special-add") {
            Fish { SettingPill("＋", "新建特殊时段", onClick = onAdd) }
        }
        if (rules.isEmpty()) {
            item(key = "special-empty") {
                Fish {
                    Text(
                        "还没有特殊时段\n比如这周三的课不一样\n可以单独设一个，到期自动恢复",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                }
            }
        }
        rules.forEach { r ->
            val now = store.epochNow()
            val left = ((r.endEpochDay() - now) / 7L + 1L).coerceAtLeast(1L)
            item(key = "special-" + r.id) {
                Fish {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .heightIn(min = 48.dp)
                            .clip(squircle(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onEdit(r.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("周" + Store.DAY_NAMES.getOrElse(r.weekday) { "?" } + " · 剩 " + left + " 周", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(r.items.size.toString() + " 段 · " + Store.fmtDayEpoch(r.startEpochDay) + " 开始", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}
