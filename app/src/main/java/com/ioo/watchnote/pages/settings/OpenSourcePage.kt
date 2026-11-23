package com.ioo.watchnote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** 项目主页地址（关于 → 开源网址）。 */
internal const val PROJECT_URL = "https://github.com/wojiuluguo/watchnote"

@Composable
internal fun OpenSourcePage(onBack: () -> Unit, onCopy: () -> Unit, onShowQr: () -> Unit = {}) {
    SubPageFrame(onBack) {
        PageTitle("开源网址", "源码都放在 GitHub")
        Spacer(Modifier.height(6.dp))
        // 4.1.24：地址本身也能点，点了出二维码页（手表上没有浏览器，手机扫一下就能打开）
        Text(
            PROJECT_URL,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onShowQr() }
        )
        Text(
            "源码、构建方法、更新记录都能在这里看。\n手表打不开网页：点地址或下面的按钮出二维码，用手机扫一下就到。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        )
        SettingPill("▦", "显示二维码", onClick = onShowQr)
        Spacer(Modifier.height(4.dp))
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

/** 4.1.24 新增：GitHub 仓库二维码页。手表上不能直接开网页，用手机扫这张码直接进仓库。 */
@Composable
internal fun ProjectQrPage(onBack: () -> Unit, onCopy: () -> Unit) {
    SubPageFrame(onBack) {
        PageTitle("扫码打开", "手机一扫就到 GitHub 仓库")
        Spacer(Modifier.height(6.dp))
        // 二维码必须白底黑块才好扫，所以单独给一张白色圆角底
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(squircle(24.dp))
                    .background(Color.White)
                    .padding(6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.qr_project),
                    contentDescription = "GitHub 仓库二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.94f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            PROJECT_URL,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "用手机相机或扫码功能对着手表扫一下；\n扫不动就把屏幕亮度调高、手表拿稳一点。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)
        )
        SettingPill("⧉", "复制网址", onClick = onCopy)
    }
}
