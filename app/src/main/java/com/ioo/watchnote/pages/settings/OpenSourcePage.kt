package com.ioo.watchnote

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** 项目主页地址（关于 → 开源网址）。 */
internal const val PROJECT_URL = "https://github.com/wojiuluguo/watchnote"

@Composable
internal fun OpenSourcePage(onBack: () -> Unit, onCopy: () -> Unit) {
    SubPageFrame(onBack) {
        PageTitle("开源网址", "源码都放在 GitHub")
        Spacer(Modifier.height(6.dp))
        Text(
            PROJECT_URL,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            "源码、构建方法、更新记录都能在这里看。\n装到手表上的版本就是这个仓库编译出来的。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
        SettingPill("⧉", "复制网址", onClick = onCopy)
        Spacer(Modifier.height(6.dp))
        Text(
            "开源协议：Apache-2.0\n基于 Wear Compose for Material 3 构建",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}
