package com.ioo.watchnote.plugin

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ioo.watchnote.ConfirmData
import com.ioo.watchnote.ConfirmDialog
import com.ioo.watchnote.Fish
import com.ioo.watchnote.PageTitle
import com.ioo.watchnote.SettingPill
import com.ioo.watchnote.Store
import com.ioo.watchnote.SubPageListFrame
import com.ioo.watchnote.localWifiIp
import com.ioo.watchnote.plugin.external.ExternalInstallPolicy
import com.ioo.watchnote.plugin.external.ExternalInstallPreview
import com.ioo.watchnote.plugin.external.ExternalInstallResult
import com.ioo.watchnote.plugin.external.ExternalPluginInstaller
import com.ioo.watchnote.plugin.external.ExternalPluginRecord
import com.ioo.watchnote.plugin.external.ExternalPreviewResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 插件管理页（设置 → 插件）。
 *
 * 这一页本身不属于任何插件，是宿主自己的页面：插件全关掉它也还在，
 * 否则关掉插件就再也点不开、只能清数据，那就成死胡同了。
 *
 * 两层结构：列表 → 详情。为什么不让列表一行直接切换开关：
 * 插件一多，一行里既显示状态又当开关既容易误触、也没地方放版本/权限/页面/数据这些用户该看到的东西。
 * 所以点一行先看详情，开关和「重新加载」都在详情里。
 *
 * 4.1.10 起多了「外置插件」一块：内置插件来自代码目录，外置插件是从 .wplugin 装进来的；
 * 两者在列表里分开显示，详情页也分开——外置插件多了卸载和包指纹。
 */
@Composable
fun PluginManagerPage(
    core: PluginCore,
    store: Store,
    prefs: PluginPrefs,
    installer: ExternalPluginInstaller? = null,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    // 宿主统一给插件页面一个入口：插件只管注册页面，不需要为了「能被点开」再加一个设置行按钮
    onOpenPage: (String) -> Unit = {},
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedExternalId by remember { mutableStateOf<String?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    var pendingConfirm by remember { mutableStateOf<ConfirmData?>(null) }
    var receiving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun applied() {
        bump++
        onChanged()
    }
    // 手机传来的包：先本地预览清单，用户点「安装」才落地（和文件选择器同一条链路）
    fun handleReceived(inst: ExternalPluginInstaller, file: File) {
        scope.launch {
            when (val previewResult = withContext(Dispatchers.IO) { inst.preview(file) }) {
                is ExternalPreviewResult.Ready -> pendingConfirm = sampleInstallConfirm(previewResult.preview) {
                    pendingConfirm = null
                    installPicked(core, inst, file) { applied() }
                }
                is ExternalPreviewResult.Failed -> {
                    file.delete()
                    core.toastHandler?.invoke("装不了：" + previewResult.reason)
                }
            }
        }
    }
    // 系统文件选择器：选一个 .wplugin → 复制进缓存 → 先预览清单 → 用户确认才装
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val inst = installer
        if (uri != null && inst != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) { copyPickedPackage(context, uri) }
                if (copied == null) {
                    core.toastHandler?.invoke("读不到这个文件")
                    return@launch
                }
                when (val previewResult = withContext(Dispatchers.IO) { inst.preview(copied) }) {
                    is ExternalPreviewResult.Ready -> pendingConfirm = sampleInstallConfirm(previewResult.preview) {
                        pendingConfirm = null
                        installPicked(core, inst, copied) { applied() }
                    }
                    is ExternalPreviewResult.Failed -> {
                        copied.delete()
                        core.toastHandler?.invoke("装不了：" + previewResult.reason)
                    }
                }
            }
        }
    }

    val externalId = selectedExternalId
    // 选中的外置插件如果被卸载或记录丢了，退回列表。不能直接在组合期改状态，所以放进 LaunchedEffect。
    LaunchedEffect(externalId) {
        if (externalId != null && installer?.record(externalId) == null) selectedExternalId = null
    }
    if (externalId != null && installer != null) {
        val record = remember(externalId, bump) { installer.record(externalId) }
        if (record != null) {
            ExternalPluginDetail(
                core = core,
                installer = installer,
                record = record,
                onOpenPage = onOpenPage,
                onBack = { selectedExternalId = null },
                onUninstalled = {
                    selectedExternalId = null
                    applied()
                },
            )
            return
        }
    }

    // 手机传 / 网址下载：手表自己当一个小服务端，手机浏览器打开地址即可
    val receiveInstaller = installer
    if (receiving && receiveInstaller != null) {
        PluginReceivePage(
            installer = receiveInstaller,
            onIncoming = { file -> handleReceived(receiveInstaller, file) },
            onBack = { receiving = false },
        )
        val receiveConfirm = pendingConfirm
        if (receiveConfirm != null) ConfirmDialog(receiveConfirm, onClosed = { pendingConfirm = null })
        return
    }

    val selected = selectedId
    if (selected != null) {
        PluginDetail(
            core = core,
            store = store,
            prefs = prefs,
            pluginId = selected,
            onOpenPage = onOpenPage,
            onBack = { selectedId = null },
            onChanged = { applied() },
        )
        return
    }

    // 装之前先看清楚：示例包和文件夹安装都先弹清单确认，点「安装」才真正落地
    val currentInstaller = installer
    val requestSample: () -> Unit = {
        if (currentInstaller != null) {
            when (val previewResult = currentInstaller.previewBundledSample()) {
                is ExternalPreviewResult.Ready -> pendingConfirm = sampleInstallConfirm(previewResult.preview) {
                    pendingConfirm = null
                    installSample(core, currentInstaller) { applied() }
                }
                is ExternalPreviewResult.Failed -> core.toastHandler?.invoke("装不了：" + previewResult.reason)
            }
        }
    }
    val requestInbox: () -> Unit = {
        if (currentInstaller != null) {
            val files = currentInstaller.inboxFiles()
            if (files.isEmpty()) {
                core.toastHandler?.invoke("文件夹里没有 .wplugin：" + currentInstaller.inboxDir().path)
            } else {
                val ready = ArrayList<Pair<File, ExternalInstallPreview>>()
                var failed = 0
                var firstFailure: String? = null
                for (file in files) {
                    when (val previewResult = currentInstaller.preview(file)) {
                        is ExternalPreviewResult.Ready -> ready.add(file to previewResult.preview)
                        is ExternalPreviewResult.Failed -> {
                            failed++
                            if (firstFailure == null) firstFailure = previewResult.reason
                        }
                    }
                }
                if (ready.isEmpty()) {
                    core.toastHandler?.invoke("一个都没法装：" + (firstFailure ?: "没有可安装的包"))
                } else {
                    if (failed > 0) core.toastHandler?.invoke("跳过 " + failed + " 个包：" + firstFailure)
                    val readyFiles = ready.map { it.first }
                    pendingConfirm = inboxInstallConfirm(ready.map { it.second }) {
                        pendingConfirm = null
                        installFiles(core, currentInstaller, readyFiles) { applied() }
                    }
                }
            }
        }
    }

    // 不是每块手表都装了系统文件选择器：起不来就提示退回「从文件夹安装」，别闪退
    val requestPick: () -> Unit = {
        try {
            filePicker.launch(arrayOf("*/*"))
        } catch (t: Throwable) {
            core.toastHandler?.invoke("这台手表没有文件选择器，用「从文件夹安装」")
        }
    }

    PluginList(
        core = core,
        installer = installer,
        bump = bump,
        onBack = onBack,
        onOpenBuiltin = { selectedId = it },
        onOpenExternal = { selectedExternalId = it },
        onRequestSample = requestSample,
        onRequestInbox = requestInbox,
        onRequestPick = requestPick,
        onRequestReceive = { receiving = true },
        onOpenPage = onOpenPage,
    )

    val confirm = pendingConfirm
    if (confirm != null) ConfirmDialog(confirm, onClosed = { pendingConfirm = null })
}

@Composable
private fun PluginList(
    core: PluginCore,
    installer: ExternalPluginInstaller?,
    bump: Int,
    onBack: () -> Unit,
    onOpenBuiltin: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onRequestSample: () -> Unit,
    onRequestInbox: () -> Unit,
    onRequestPick: () -> Unit,
    onRequestReceive: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val installedIds = core.manifests.map { it.id }.toSet()
    // bump 是安装/卸载后的刷新信号：外置插件记录在 SharedPreferences 里，不是 Compose 状态
    val externalRecords = remember(bump) { installer?.records() ?: emptyList() }
    val externalRunning = externalRecords.count { core.isInstalled(it.id) }
    val total = BuiltinPlugins.all.size + externalRecords.size
    val onCount = BuiltinPlugins.all.count { installedIds.contains(it.id) } + externalRunning
    val pages = core.pages().size
    val buttons = core.buttons().size
    val dataKeys = BuiltinPlugins.all.sumOf { core.dataKeysOf(it.id).size } +
        externalRecords.sumOf { core.dataKeysOf(it.id).size }

    SubPageListFrame(onBack = onBack) {
        item(key = "plugin-title") {
            Fish { PageTitle("插件", "共 " + total + " 个 · 开着 " + onCount) }
        }
        item(key = "plugin-stat-running") {
            Fish { Official.Stat("正在运行", onCount.toString() + " 个") }
        }
        item(key = "plugin-stat-pages") {
            Fish { Official.Stat("注册页面", pages.toString() + " 页") }
        }
        item(key = "plugin-stat-buttons") {
            Fish { Official.Stat("注册按钮", buttons.toString() + " 个") }
        }
        item(key = "plugin-stat-data") {
            Fish { Official.Stat("私有数据", dataKeys.toString() + " 项") }
        }
        // 插件页面总入口：插件一多，不用挨个点进详情找页面
        val allPages = core.pages()
        fun ownerName(pluginId: String): String {
            val builtin = BuiltinPlugins.find(pluginId)
            if (builtin != null) return builtin.name
            val external = externalRecords.firstOrNull { it.id == pluginId }
            if (external != null) return external.name
            return pluginId
        }
        if (allPages.isNotEmpty()) {
            item(key = "plugin-pages-header") {
                Fish { Official.Stat("插件页面", allPages.size.toString() + " 个") }
            }
            allPages.forEach { page ->
                item(key = "plugin-page-" + page.spec.id) {
                    Fish {
                        Official.Button(
                            symbol = if (page.spec.kind == PageKind.LIST) "▤" else "▸",
                            label = page.spec.title,
                            trailing = ownerName(page.ownerPluginId),
                            onClick = { onOpenPage(page.spec.id) },
                        )
                    }
                }
            }
        }
        item(key = "plugin-explain") {
            Fish {
                Text(
                    "点一个插件看它的版本、权限、页面和按钮，开关也在里面；上面的「插件页面」能直接打开页面。关掉插件不会丢数据。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        BuiltinPlugins.all.forEach { entry ->
            item(key = "plugin-row-" + entry.id) {
                Fish {
                    val on = installedIds.contains(entry.id)
                    SettingPill(
                        symbol = if (on) "◉" else "○",
                        label = entry.name,
                        trailing = if (on) "使用中" else "已关闭",
                        onClick = { onOpenBuiltin(entry.id) },
                    )
                }
            }
        }
        if (installer != null) {
            item(key = "plugin-ext-header") {
                Fish { Official.Stat("外置插件", externalRecords.size.toString() + " 个") }
            }
            item(key = "plugin-ext-receive") {
                Fish {
                    Official.Button(
                        symbol = "⇅",
                        label = "手机传 / 网址安装",
                        trailing = "同一 Wi-Fi",
                        onClick = onRequestReceive,
                    )
                }
            }
            externalRecords.forEach { record ->
                item(key = "plugin-ext-row-" + record.id) {
                    Fish {
                        SettingPill(
                            symbol = "◈",
                            label = record.name,
                            trailing = "v" + record.version + if (core.isInstalled(record.id)) " · 使用中" else " · 未运行",
                            onClick = { onOpenExternal(record.id) },
                        )
                    }
                }
            }
            item(key = "plugin-ext-sample") {
                Fish {
                    Official.Button(
                        symbol = "＋",
                        label = "安装示例插件",
                        trailing = if (externalRecords.any { it.id == SAMPLE_ID }) "已装" else "离线",
                        onClick = onRequestSample,
                    )
                }
            }
            item(key = "plugin-ext-pick") {
                Fish {
                    Official.Button(
                        symbol = "◎",
                        label = "选择文件安装",
                        trailing = "系统文件",
                        onClick = onRequestPick,
                    )
                }
            }
            item(key = "plugin-ext-inbox") {
                Fish {
                    Official.Button(
                        symbol = "⇩",
                        label = "从文件夹安装",
                        trailing = "插件包",
                        onClick = onRequestInbox,
                    )
                }
            }
            item(key = "plugin-ext-inbox-hint") {
                Fish {
                    Text(
                        "把 .wplugin 放进：" + installer.inboxDir().path,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            item(key = "plugin-ext-type") {
                Fish {
                    Text(
                        "插件就是一个 .wplugin 文件（本质是 zip：plugin.json + classes.dex，可带 assets/ 和签名）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            item(key = "plugin-ext-explain") {
                Fish {
                    Text(
                        "外置插件是独立打包的代码，先从包结构检查、再解压、再加载；任何一步失败都不会影响腕记本身。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        item(key = "plugin-bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ExternalPluginDetail(
    core: PluginCore,
    installer: ExternalPluginInstaller,
    record: ExternalPluginRecord,
    onOpenPage: (String) -> Unit,
    onBack: () -> Unit,
    onUninstalled: () -> Unit,
) {
    var confirmUninstall by remember(record.id) { mutableStateOf(false) }
    val pages = core.pagesOf(record.id)
    val buttons = core.buttonsOfPlugin(record.id)
    val running = core.isInstalled(record.id)

    SubPageListFrame(onBack = onBack) {
        item(key = "ext-detail-title") {
            Fish { PageTitle(record.name, "外置插件 · v" + record.version) }
        }
        item(key = "ext-detail-state") {
            Fish { Official.Stat("状态", if (running) "正在运行" else "未运行") }
        }
        item(key = "ext-detail-id") {
            Fish {
                Text(record.id, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        if (record.author != null) {
            item(key = "ext-detail-author") { Fish { Official.Stat("作者", record.author) } }
        }
        item(key = "ext-detail-sign") {
            Fish { Official.Stat("签名", ExternalInstallPolicy.signingLabel(record.keyFingerprint)) }
        }
        item(key = "ext-detail-sha") {
            Fish {
                Text(
                    "SHA-256：" + record.sha256.take(16) + "…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item(key = "ext-detail-installed") {
            Fish { Official.Stat("安装时间", formatTime(record.installedAt)) }
        }
        if (pages.isNotEmpty()) {
            item(key = "ext-detail-pages-header") {
                Fish { Official.Stat("注册页面", pages.size.toString() + " 页") }
            }
            pages.forEach { page ->
                item(key = "ext-detail-page-" + page.spec.id) {
                    Fish {
                        Official.Button(
                            symbol = if (page.spec.kind == PageKind.LIST) "▤" else "▸",
                            label = page.spec.title,
                            trailing = "外置 · 打开",
                            onClick = { onOpenPage(page.spec.id) },
                        )
                    }
                }
            }
        }
        if (buttons.isNotEmpty()) {
            item(key = "ext-detail-buttons-header") {
                Fish { Official.Stat("注册按钮", buttons.size.toString() + " 个") }
            }
            buttons.forEach { button ->
                item(key = "ext-detail-button-" + button.spec.id) {
                    Fish { Official.Stat(button.spec.label, slotLabel(button.spec.slot)) }
                }
            }
        }
        item(key = "ext-detail-uninstall") {
            Fish {
                Official.Button(
                    symbol = "✕",
                    label = if (confirmUninstall) "再点一次确认卸载" else "卸载这个插件",
                    danger = true,
                    onClick = {
                        if (confirmUninstall) {
                            installer.uninstall(record.id)
                            core.toastHandler?.invoke(record.name + " 已卸载")
                            onUninstalled()
                        } else {
                            confirmUninstall = true
                        }
                    },
                )
            }
        }
        item(key = "ext-detail-hint") {
            Fish {
                Text(
                    if (confirmUninstall) "卸载会删掉插件代码和它的私有数据，笔记不受影响。" else "外置插件的代码在应用私有目录里，笔记数据跟它完全分开。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item(key = "ext-detail-bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PluginDetail(
    core: PluginCore,
    store: Store,
    prefs: PluginPrefs,
    pluginId: String,
    onOpenPage: (String) -> Unit,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val entry = BuiltinPlugins.find(pluginId)
    if (entry == null) {
        SubPageListFrame(onBack = onBack) {
            item(key = "plugin-missing") { Fish { PageTitle("插件不存在", pluginId) } }
        }
        return
    }
    // 读快照列表而不是 hosts 里的 HashMap：装/卸后这一页才会自动重组
    val on = core.manifests.any { it.id == pluginId }
    // 只读 manifest 用：装上时用运行中那份，关掉时临时 new 一个读它的声明（构建本身没有副作用）
    val built = remember(entry.id, on) { entry.build(store, onChanged) }
    val manifest = built.manifest
    val pages = core.pagesOf(pluginId)
    val buttons = core.buttonsOfPlugin(pluginId)
    val dataKeys = core.dataKeysOf(pluginId).sorted()
    val error = core.lastInstallError(pluginId)

    SubPageListFrame(onBack = onBack) {
        item(key = "detail-title") {
            Fish { PageTitle(entry.name, if (on) "使用中 · v" + manifest.version else "已关闭 · v" + manifest.version) }
        }
        item(key = "detail-toggle") {
            Fish {
                Official.Button(
                    symbol = if (on) "◉" else "○",
                    label = if (on) "关闭这个插件" else "开启这个插件",
                    trailing = null,
                    onClick = { toggle(core, store, prefs, entry, on, onChanged) },
                )
            }
        }
        if (error != null) {
            item(key = "detail-error") {
                Fish {
                    Text(
                        "上次安装失败：" + error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        item(key = "detail-id") {
            Fish {
                Text(
                    manifest.id,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item(key = "detail-api") {
            Fish { Official.Stat("API 等级", manifest.minApi.toString()) }
        }
        item(key = "detail-perms") {
            Fish {
                Text(
                    "权限：" + permissionsText(manifest.permissions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        item(key = "detail-summary") {
            Fish {
                Text(
                    entry.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        if (!entry.defaultEnabled) {
            item(key = "detail-default-off") {
                Fish {
                    Text(
                        "默认关闭：开启后才会往每条笔记卡片上加按钮，关掉就恢复原样。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        if (pages.isNotEmpty()) {
            item(key = "detail-pages-header") {
                Fish { Official.Stat("注册页面", pages.size.toString() + " 页") }
            }
            pages.forEach { page ->
                item(key = "detail-page-" + page.spec.id) {
                    Fish {
                        Official.Button(
                            symbol = if (page.spec.kind == PageKind.LIST) "▤" else "▸",
                            label = page.spec.title,
                            trailing = if (page.spec.kind == PageKind.LIST) "列表页 · 打开" else "打开",
                            onClick = { onOpenPage(page.spec.id) },
                        )
                    }
                }
            }
        }
        if (buttons.isNotEmpty()) {
            item(key = "detail-buttons-header") {
                Fish { Official.Stat("注册按钮", buttons.size.toString() + " 个") }
            }
            buttons.forEach { button ->
                item(key = "detail-button-" + button.spec.id) {
                    Fish { Official.Stat(button.spec.label, slotLabel(button.spec.slot)) }
                }
            }
        }
        if (dataKeys.isNotEmpty()) {
            item(key = "detail-data") {
                Fish {
                    Text(
                        "私有数据键：" + dataKeys.joinToString("、"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        if (on) {
            item(key = "detail-reload") {
                Fish {
                    Official.Button(
                        symbol = "⟳",
                        label = "重新加载",
                        onClick = {
                            when (val r = core.reload(entry.build(store, onChanged))) {
                                is RegisterResult.Ok -> core.toastHandler?.invoke(entry.name + " 已重新加载")
                                is RegisterResult.Rejected -> core.toastHandler?.invoke("重新加载失败：" + r.reason)
                            }
                            onChanged()
                        },
                    )
                }
            }
        }
        item(key = "detail-bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

// 把用户从系统文件选择器挑中的内容复制成缓存里的 .wplugin（installer 只认 File）
private fun copyPickedPackage(context: Context, uri: Uri): File? {
    val file = File(context.cacheDir, "picked-" + System.currentTimeMillis() + ".wplugin")
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            file.outputStream().use { target -> source.copyTo(target) }
        }
        file
    } catch (t: Throwable) {
        file.delete()
        null
    }
}

private fun installPicked(
    core: PluginCore,
    installer: ExternalPluginInstaller,
    file: File,
    onApplied: () -> Unit,
) {
    val result = installer.installFromFile(file)
    file.delete()
    when (result) {
        is ExternalInstallResult.Installed ->
            core.toastHandler?.invoke("已装上：" + result.record.name + "，去插件列表看它")
        is ExternalInstallResult.Failed ->
            core.toastHandler?.invoke("安装失败：" + result.reason)
    }
    onApplied()
}

private fun installSample(
    core: PluginCore,
    installer: ExternalPluginInstaller,
    onApplied: () -> Unit,
) {
    when (val result = installer.installBundledSample()) {
        is ExternalInstallResult.Installed ->
            core.toastHandler?.invoke("已装上示例插件：回设置页看「★ 示例外置插件」")
        is ExternalInstallResult.Failed ->
            core.toastHandler?.invoke("安装失败：" + result.reason)
    }
    onApplied()
}

private fun installFiles(
    core: PluginCore,
    installer: ExternalPluginInstaller,
    files: List<File>,
    onApplied: () -> Unit,
) {
    val results = installer.installFiles(files)
    val ok = results.count { it is ExternalInstallResult.Installed }
    val failed = results.size - ok
    core.toastHandler?.invoke(
        if (failed == 0) "装上了 " + ok + " 个外置插件" else "成功 " + ok + " 个，失败 " + failed + " 个",
    )
    onApplied()
}

private fun previewLines(preview: ExternalInstallPreview): List<String> {
    val lines = ArrayList<String>()
    lines.add(preview.name + " · v" + preview.version + " · API " + preview.minApi)
    if (!preview.author.isNullOrBlank()) lines.add("作者：" + preview.author)
    lines.add("权限：" + permissionsText(preview.permissions))
    lines.add("大小：" + formatBytes(preview.packageBytes))
    lines.add("包指纹：" + preview.sha256.take(12) + "…")
    lines.add("签名：" + ExternalInstallPolicy.signingLabel(preview.keyFingerprint))
    lines.add(
        when {
            preview.isUpgrade -> "升级 v" + preview.installedVersion + " → v" + preview.version
            preview.installedVersion != null -> "重装 v" + preview.version
            else -> "首次安装"
        },
    )
    if (!preview.description.isNullOrBlank()) lines.add(preview.description)
    return lines
}

private fun sampleInstallConfirm(preview: ExternalInstallPreview, onOk: () -> Unit): ConfirmData =
    ConfirmData(
        title = "安装这个插件？",
        message = previewLines(preview).joinToString("\n") + "\n\n装完会出现在设置页和插件列表里。",
        okLabel = "安装",
        danger = false,
        onOk = onOk,
    )

private fun inboxInstallConfirm(previews: List<ExternalInstallPreview>, onOk: () -> Unit): ConfirmData {
    val lines = ArrayList<String>()
    for (preview in previews) {
        lines.add(preview.name + " · v" + preview.version + " · " + formatBytes(preview.packageBytes))
    }
    lines.add("权限合计：" + permissionsText(previews.flatMap { it.permissions }.toSet()))
    return ConfirmData(
        title = "安装 " + previews.size + " 个外置插件？",
        message = lines.joinToString("\n"),
        okLabel = "安装",
        danger = false,
        onOk = onOk,
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> bytes.toString() + " B"
}

private fun toggle(
    core: PluginCore,
    store: Store,
    prefs: PluginPrefs,
    entry: BuiltinPluginEntry,
    currentlyOn: Boolean,
    onChanged: () -> Unit,
) {
    if (currentlyOn) {
        // 管理页「关掉」用 disable：页面/按钮收走，插件自己的数据留着
        core.disable(entry.id)
        prefs.setEnabled(entry.id, false)
        core.toastHandler?.invoke(entry.name + " 已关闭")
    } else {
        when (val result = core.install(entry.build(store, onChanged))) {
            is RegisterResult.Ok -> {
                prefs.setEnabled(entry.id, true)
                core.toastHandler?.invoke(entry.name + " 已开启")
            }
            is RegisterResult.Rejected -> {
                core.toastHandler?.invoke("打开失败：" + result.reason)
            }
        }
    }
    onChanged()
}

private fun permissionsText(permissions: Set<Permission>): String =
    if (permissions.isEmpty()) "不读任何数据"
    else permissions.joinToString("、") { permissionLabel(it) }

private fun permissionLabel(permission: Permission): String = when (permission) {
    Permission.READ_NOTES -> "读笔记"
    Permission.WRITE_NOTES -> "写笔记（未开放）"
    Permission.READ_SETTINGS -> "读设置"
}

private fun slotLabel(slot: ButtonSlot): String = when (slot) {
    ButtonSlot.PAGE_BOTTOM -> "页面底部"
    ButtonSlot.CARD_ACTION -> "卡片动作"
    ButtonSlot.SETTINGS_ROW -> "设置行"
}

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * 「装外置插件」的接收页（4.1.15）：手表开一个局域网小服务，手机浏览器打开地址，
 * 页面上既能传文件，也能填插件包的网址让手表自己去下。收到后照旧先弹清单确认。
 */
@Composable
private fun PluginReceivePage(
    installer: ExternalPluginInstaller,
    onIncoming: (File) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val inboxDir = remember { File(context.cacheDir, "received").apply { mkdirs() } }
    val main = remember { Handler(Looper.getMainLooper()) }
    var server by remember { mutableStateOf<PluginReceiverServer?>(null) }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在启动接收服务…") }
    var count by remember { mutableIntStateOf(0) }

    fun stopServer() {
        server?.stop()
        server = null
        url = ""
    }

    fun startServer() {
        stopServer()
        val ip = localWifiIp()
        if (ip == null) {
            status = "手表没连 Wi-Fi。先连上能上网的 Wi-Fi，再点「重新开始服务」"
            return
        }
        try {
            val s = PluginReceiverServer(inboxDir) { file ->
                main.post {
                    count += 1
                    status = "收到 " + file.name + " · 抬手看确认框"
                    onIncoming(file)
                }
            }
            s.start()
            server = s
            url = "http://" + ip + ":" + s.port
            status = "服务已开：手机浏览器打开上面的地址"
        } catch (t: Throwable) {
            status = "服务起不来：" + (t.message ?: "端口被占用，点「重新开始服务」再试")
        }
    }

    LaunchedEffect(Unit) { startServer() }
    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    SubPageListFrame(onBack = { stopServer(); onBack() }) {
        item(key = "receive-title") {
            Fish { PageTitle("装外置插件", "手机传 / 网址下载") }
        }
        item(key = "receive-url") {
            Fish {
                Text(
                    if (url.isEmpty()) "（服务还没起来）" else url,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        item(key = "receive-status") {
            Fish {
                Text(
                    status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item(key = "receive-count") {
            Fish { Official.Stat("这次收到", count.toString() + " 个") }
        }
        item(key = "receive-restart") {
            Fish {
                Official.Button(
                    symbol = "⟳",
                    label = "重新开始服务",
                    onClick = { startServer() },
                )
            }
        }
        item(key = "receive-steps") {
            Fish {
                Text(
                    "怎么用：①手机连同一个 Wi-Fi；②浏览器输入上面的地址；③页面里选 .wplugin 上传，或者填插件包网址让手表下载；④手表弹清单，点「安装」。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item(key = "receive-stop") {
            Fish {
                Official.Button(
                    symbol = "■",
                    label = "停止服务并返回",
                    danger = true,
                    onClick = {
                        stopServer()
                        onBack()
                    },
                )
            }
        }
        item(key = "receive-bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

private const val SAMPLE_ID = "com.example.hello"
