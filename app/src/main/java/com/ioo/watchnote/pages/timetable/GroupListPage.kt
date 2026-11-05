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
internal fun GroupListPage(
    store: Store,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    store.version.intValue
    val groups = store.groupsSorted()
    SubPageListFrame(onBack = onBack) {
        item(key = "group-title") {
            Fish { PageTitle("时间组", "共 " + groups.size + " 组") }
        }
        item(key = "group-add") {
            Fish { SettingPill("＋", "新建时间组", onClick = onAdd) }
        }
        if (groups.isEmpty()) {
            item(key = "group-empty") {
                Fish {
                    Text(
                        "还没有时间组\n新建一组常用时间\n以后添加时段直接点选",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp)
                    )
                }
            }
        }
        groups.forEach { g ->
            item(key = "group-" + g.id) {
                Fish {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .heightIn(min = 48.dp)
                            .clip(squircle(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onEdit(g.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (g.items.isEmpty()) "空时间组"
                                else g.items.size.toString() + " 个时间 · " + g.items.first().start + "–" + g.items.last().end,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}
