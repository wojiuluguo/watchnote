package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun ThemePage(current: Int, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    SubPageListFrame(onBack = onBack) {
        item(key = "theme-title") {
            Fish { PageTitle("更换主题颜色") }
        }
        ACCENT_THEMES.forEachIndexed { i, theme ->
            item(key = "theme-" + i) {
                Fish {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .heightIn(min = 48.dp)
                            .clip(squircle(24.dp))
                            .background(if (i == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(theme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            theme.name,
                            color = if (i == current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
