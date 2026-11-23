package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================
   4.1.26：App 内「检查更新」页
   - 进页面就查一次 GitHub 最新 Release（用户自己点的，不算偷跑）
   - 有新版：显示版本号 + Release 介绍全文 + 下载按钮
   - 下载时显示自绘进度条（百分比 + 已下载/总大小），存到 Download 目录
   ============================================================ */

private sealed class UpdateUi {
    object Idle : UpdateUi()
    object Checking : UpdateUi()
    data class Done(val result: UpdateCheck) : UpdateUi()
    data class Downloading(val done: Long, val total: Long) : UpdateUi()
    data class Downloaded(val path: String) : UpdateUi()
    data class DownloadFailed(val reason: String) : UpdateUi()
}

@Composable
internal fun UpdatePage(
    checker: UpdateChecker,
    onBack: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { ApkDownloader(context) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    fun runCheck() {
        ui = UpdateUi.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { checker.check() }
            ui = UpdateUi.Done(result)
        }
    }

    fun startDownload(available: UpdateCheck.Available) {
        ui = UpdateUi.Downloading(0L, available.apkSize)
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    downloader.download(
                        url = available.apkUrl,
                        fileName = "watchnote-" + available.versionName + ".apk",
                        sizeHint = available.apkSize
                    ) { done, total ->
                        // 进度回调在 IO 线程：扔回主线程更新界面，别每 64KB 都重组整页
                        scope.launch { ui = UpdateUi.Downloading(done, total) }
                    }
                }
            }
            ui = outcome.fold(
                onSuccess = { path -> UpdateUi.Downloaded(path) },
                onFailure = { e -> UpdateUi.DownloadFailed(e.message ?: e.javaClass.simpleName) }
            )
            outcome.onSuccess { path ->
                onToast("已保存到 " + path)
                EventLog.add("update", "下载完成：" + path)
            }
            outcome.onFailure { e ->
                EventLog.add("update", "下载失败：" + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    // 用户主动进来一次就查一次；结果放缓存里，关于页那一行也会跟着变
    LaunchedEffect(Unit) { runCheck() }

    SubPageListFrame(onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PageTitle("检查更新")
                Text(
                    "数据来自 GitHub Releases\n当前版本 " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }

        when (val state = ui) {
            is UpdateUi.Idle, is UpdateUi.Checking -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "正在检查 GitHub…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            is UpdateUi.Done -> {
                when (val result = state.result) {
                    is UpdateCheck.Available -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "发现新版本 " + result.versionName,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (result.versionCode > 0) {
                                    Text(
                                        "versionCode " + result.versionCode,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        item { Fish { SettingPill("↓", "下载这个版本", trailing = sizeText(result.apkSize), onClick = { startDownload(result) }) } }
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp)) {
                                Text(
                                    "更新介绍",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    result.notes.ifEmpty { "这个版本没有写更新介绍。" },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                "下载好是 APK 文件，在 Download 目录里\n用文件管理器点开手动安装",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp)
                            )
                        }
                    }

                    is UpdateCheck.Latest -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("已经是最新版本", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "GitHub 上最新是 " + result.versionName + "（" + checker.lastCheckText() + "）",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        item { Fish { SettingPill("↻", "再查一次", trailing = checker.lastCheckText(), onClick = { runCheck() }) } }
                    }

                    is UpdateCheck.Failed -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("这次没查成", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    result.reason,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                        item { Fish { SettingPill("↻", "重试", trailing = checker.lastCheckText(), onClick = { runCheck() }) } }
                        item {
                            Text(
                                "手机流量/家里 Wi-Fi 都可能连不上 GitHub，\n可以换网络再试，或到电脑上打开 Releases 页面下载",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            is UpdateUi.Downloading -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DownloadProgress(state.done, state.total)
                    }
                }
            }

            is UpdateUi.Downloaded -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("下载完成", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            state.path,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        Text(
                            "在手表/手机的文件管理里打开 Download 目录，点 APK 安装",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                }
                item { Fish { SettingPill("↻", "重新检查", trailing = checker.lastCheckText(), onClick = { runCheck() }) } }
            }

            is UpdateUi.DownloadFailed -> {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("下载失败", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            state.reason,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
                item { Fish { SettingPill("↻", "重新检查", trailing = checker.lastCheckText(), onClick = { runCheck() }) } }
            }
        }
    }
}

/** 自绘下载进度条（不引新组件：外层底槽 + 内层按比例填充）。 */
@Composable
private fun DownloadProgress(done: Long, total: Long) {
    val fraction = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (total > 0L) "正在下载 " + (fraction * 100f).toInt() + "%" else "正在下载…",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (total > 0L) sizeText(done) + " / " + sizeText(total) else sizeText(done),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "下载时别退出这个页面",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 字节数转人话：1.9 MB / 512 KB / 3.1 GB */
internal fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1.0) return bytes.toString() + " B"
    if (kb < 1024.0) return String.format(java.util.Locale.CHINA, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.CHINA, "%.2f MB", mb)
    return String.format(java.util.Locale.CHINA, "%.2f GB", mb / 1024.0)
}
