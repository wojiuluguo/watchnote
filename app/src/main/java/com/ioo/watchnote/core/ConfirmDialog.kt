package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 外观按老版本（官方 Wear AlertDialog）还原：标题 + 正文居中，底部左「✕」右「✓」两颗大圆按钮。
 * 但实现是自绘 Dialog，不再用官方 AlertDialog —— 它内部会走
 * ScalingLazyColumn → HapticConstants$WearSDKHapticConstants.<clinit> →
 * com.google.wear.input.WearHapticFeedbackConstants，小米手表上缺这个类，
 * 一点确认弹窗就 NoClassDefFoundError 崩溃（4.1.23 真机日志）。
 * 4.1.24 曾改成两条整行胶囊按钮，不好按；4.1.25 按用户要求恢复成大圆按钮。
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
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 文字区垂直居中；正文太长（比如插件安装信息）时自己滚，不压到底部按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
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
                        if (data.message.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                data.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 底部两颗大圆按钮：左「✕」灰、右「✓」主题色（危险操作时用错误色）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogRoundButton(
                        symbol = "✕",
                        background = MaterialTheme.colorScheme.surfaceContainerHigh,
                        foreground = MaterialTheme.colorScheme.onSurface,
                        onClick = onClosed
                    )
                    Spacer(Modifier.width(24.dp))
                    DialogRoundButton(
                        symbol = "✓",
                        background = if (data.danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        foreground = if (data.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        onClick = { onClosed(); data.onOk() }
                    )
                }
            }
        }
    }
}

/** 弹窗底部的大圆按钮（直径 60dp，手指好按）。 */
@Composable
private fun DialogRoundButton(symbol: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(background)
            .clickable { Haptics.tap(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = foreground, fontSize = 26.sp)
    }
}
