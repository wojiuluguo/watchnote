package com.ioo.watchnote

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * 官方 Wear Material3 AlertDialog（确认/取消两个圆形按钮）。
 * 只替换渲染层，ConfirmData 的语义和 onOk 回调与之前完全一致。
 */
@Composable
internal fun ConfirmDialog(data: ConfirmData, onClosed: () -> Unit) {
    AlertDialog(
        visible = true,
        onDismissRequest = { onClosed() },
        title = {
            Text(
                data.title,
                color = if (data.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                data.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onClosed()
                    data.onOk()
                },
                colors = if (data.danger) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    IconButtonDefaults.filledIconButtonColors()
                }
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { onClosed() })
        }
    )
}
