package com.ioo.watchnote

import android.graphics.Bitmap
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.io.File

/**
 * 发到手机：先选笔记（可多选），再选文本/图片，只生成选中的；
 * 然后开一个局域网下载服务，手机浏览器打开地址就直接下载，不返回网页。
 */
@Composable
internal fun SharePage(store: Store, onBack: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    var server by remember { mutableStateOf<ShareServer?>(null) }
    var url by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    store.version.intValue

    fun stopServer() {
        server?.stop()
        server = null
        url = ""
    }

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    fun publish(asPng: Boolean) {
        val notes = store.notesSorted().filter { selected.contains(it.id) }
        if (notes.isEmpty()) {
            hint = "先选至少一条笔记"
            step = 0
            return
        }
        stopServer()
        try {
            val file: File
            if (asPng) {
                val bmp = buildSharePng(notes)
                file = File(context.cacheDir, shareFileName(notes, true))
                file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                bmp.recycle()
            } else {
                file = File(context.cacheDir, shareFileName(notes, false))
                file.writeText(buildShareText(notes))
            }
            val ip = localWifiIp()
            if (ip == null) {
                hint = "没有连上 Wi-Fi，先让手表连同一个 Wi-Fi"
                return
            }
            val s = ShareServer(file, if (asPng) "image/png" else "text/plain; charset=utf-8")
            val port = s.start()
            server = s
            url = "http://" + ip + ":" + port
            hint = (if (asPng) "图片" else "文本") + "已生成 · " + notes.size + " 条笔记"
            EventLog.add("share", (if (asPng) "png" else "txt") + " n=" + notes.size + " port=" + port)
            step = 2
        } catch (e: Exception) {
            hint = "生成失败：" + (e.message ?: "未知错误")
            EventLog.add("share", "failed " + (e.message ?: ""))
        }
    }

    if (step == 0) {
        SubPageListFrame(onBack = onBack) {
            item(key = "share-title") {
                Fish { PageTitle("发到手机", "选要发的笔记，可以多选") }
            }
            item(key = "share-next") {
                Fish {
                    SettingPill(
                        "✓",
                        if (selected.isEmpty()) "下一步：选格式" else "下一步（已选 " + selected.size + " 条）",
                        onClick = {
                            if (selected.isEmpty()) hint = "先选至少一条笔记"
                            else {
                                hint = ""
                                step = 1
                            }
                        }
                    )
                }
            }
            val notes = store.notesSorted()
            if (notes.isEmpty()) {
                item(key = "share-empty") {
                    Fish {
                        Text(
                            "还没有笔记",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    }
                }
            }
            notes.forEachIndexed { idx, n ->
                item(key = "share-" + n.id.ifBlank { "n" + idx }) {
                    val checked = selected.contains(n.id)
                    Fish {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .heightIn(min = 48.dp)
                                .clip(squircle(18.dp))
                                .background(
                                    if (checked) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .clickable {
                                    Haptics.tap()
                                    if (checked) selected.remove(n.id) else selected.add(n.id)
                                    hint = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    noteDisplayName(n),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Store.fmtStamp(n.updatedAt) + " · " + n.tag,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (checked) "✓" else "○",
                                color = if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
            if (hint.isNotEmpty()) {
                item(key = "share-hint") {
                    Fish {
                        Text(
                            hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
        return
    }

    if (step == 1) {
        SubPageFrame(onBack = { step = 0 }) {
            PageTitle("选格式", "已选 " + selected.size + " 条笔记")
            Spacer(Modifier.height(6.dp))
            Text(
                "只生成选中的笔记，没有选的不生成。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            SettingPill("≡", "文本 .txt", onClick = { publish(false) })
            SettingPill("▣", "图片 .png", onClick = { publish(true) })
            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        return
    }

    SubPageFrame(onBack = { stopServer(); hint = ""; step = 1 }) {
        PageTitle("手机下载", "手表和手机连同一个 Wi-Fi")
        Text(
            "手机浏览器打开：",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Text(
            url,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            "打开后直接下载文件，不用等页面；\n下载完点下面按钮停止。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        SettingPill("■", "停止服务", danger = true, onClick = { stopServer(); onBack() })
        if (hint.isNotEmpty()) {
            Text(
                hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}
