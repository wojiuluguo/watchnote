package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * 确认弹窗。
 *
 * 4.1.24 起改成自绘 Dialog：原先用官方 Wear Material3 AlertDialog，它内部会走
 * ScalingLazyColumn → HapticConstants$WearSDKHapticConstants.<clinit> →
 * com.google.wear.input.WearHapticFeedbackConstants，小米手表上缺这个类，
 * 一点确认弹窗就 NoClassDefFoundError 崩溃（4.1.23 真机日志）。
 * 现在只用 Compose 基础组件，不再引用任何会触达 Wear SDK 旋转触感的类。
 */
@Composable
internal fun ConfirmDialog(data: ConfirmData, onClosed: () -> Unit) {
    Dialog(
        onDismissRequest = { onClosed() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(squircle(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    data.title,
                    color = if (data.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    data.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                SettingPill(
                    symbol = "✓",
                    label = if (data.okLabel.isBlank()) "确定" else data.okLabel,
                    danger = data.danger,
                    onClick = { onClosed(); data.onOk() }
                )
                SettingPill(
                    symbol = "←",
                    label = "取消",
                    onClick = { onClosed() }
                )
            }
        }
    }
}
