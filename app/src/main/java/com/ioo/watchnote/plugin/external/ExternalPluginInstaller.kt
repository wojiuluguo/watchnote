package com.ioo.watchnote.plugin.external

import android.content.Context
import com.ioo.watchnote.EventLog
import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.PluginCore
import com.ioo.watchnote.plugin.RegisterResult
import com.ioo.watchnote.plugin.WristPlugin
import dalvik.system.DexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** 已安装的外置插件记录（落盘；启动时按它恢复）。 */
data class ExternalPluginRecord(
    val id: String,
    val name: String,
    val version: Int,
    val entryClass: String,
    val author: String?,
    val sha256: String,
    val installedAt: Long,
    val fileName: String,
    /** 作者签名公钥指纹；null 表示这个包装的时候没签名。 */
    val keyFingerprint: String? = null,
)

/**
 * 安装前给用户看的清单：名字、版本、作者、要什么权限、多大、指纹、首装还是升级。
 * 只由 preview() 产生，不落盘、不加载代码。
 */
data class ExternalInstallPreview(
    val id: String,
    val name: String,
    val version: Int,
    val minApi: Int,
    val author: String?,
    val description: String?,
    val permissions: Set<Permission>,
    val sha256: String,
    val packageBytes: Long,
    val installedVersion: Int?,
    val isUpgrade: Boolean,
    /** 作者签名公钥指纹；null 表示这个包没签名。 */
    val keyFingerprint: String? = null,
)

/** 预览结果：Ready 才允许弹确认；Failed 直接告诉用户为什么装不了。 */
sealed interface ExternalPreviewResult {
    data class Ready(val preview: ExternalInstallPreview) : ExternalPreviewResult
    data class Failed(val reason: String) : ExternalPreviewResult
}

/** 安装结果：要么装好并给出记录，要么说清哪一步失败。 */
sealed interface ExternalInstallResult {
    data class Installed(val record: ExternalPluginRecord) : ExternalInstallResult
    data class Failed(val reason: String) : ExternalInstallResult
}

/**
 * 外置插件加载器（v2.0）。
 *
 * 一条安装链路固定为：
 *   包结构检查 -> 解压到 filesDir/plugins/<id>/<version> -> DexClassLoader 加载
 *   -> 断言入口类实现 WristPlugin -> 交给 PluginCore.install -> 成功才写安装记录
 *
 * 任何一步失败都只返回 Failed，并把半成品目录删干净；
 * 绝不因为一个坏包影响宿主，也不留下「记录里说有、实际加载不了」的幽灵插件。
 */
class ExternalPluginInstaller(context: Context, private val core: PluginCore) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, DIR_PLUGINS)

    fun records(): List<ExternalPluginRecord> = readRecords()

    fun record(pluginId: String): ExternalPluginRecord? = readRecords().firstOrNull { it.id == pluginId }

    fun isInstalled(pluginId: String): Boolean = record(pluginId) != null && core.isInstalled(pluginId)

    /**
     * 装之前先看清楚：只解析包，不落地、不加载代码。
     * 管理页拿它拼确认弹窗；确认之后才走 installFromFile。
     */
    fun preview(file: File): ExternalPreviewResult {
        val checked = ExternalPackageChecker.inspect(file)
        if (checked is ExternalPackageResult.Invalid) {
            return ExternalPreviewResult.Failed("插件包不合法：" + checked.reason)
        }
        val pkg = (checked as ExternalPackageResult.Valid).pkg
        val external = pkg.manifest
        val manifest = external.manifest
        val installed = record(manifest.id)
        val installedVersion = installed?.version
        val versionProblem = ExternalInstallPolicy.checkVersion(installedVersion, manifest.version)
        if (versionProblem != null) return ExternalPreviewResult.Failed(versionProblem)
        val keyFingerprint = (pkg.signature as? ExternalSignatureResult.Valid)?.keyFingerprint
        val signingProblem = ExternalInstallPolicy.checkSigning(installed?.keyFingerprint, keyFingerprint)
        if (signingProblem != null) return ExternalPreviewResult.Failed(signingProblem)
        return ExternalPreviewResult.Ready(
            ExternalInstallPreview(
                id = manifest.id,
                name = manifest.name,
                version = manifest.version,
                minApi = manifest.minApi,
                author = external.author,
                description = external.description,
                permissions = manifest.permissions,
                sha256 = sha256(file),
                packageBytes = file.length(),
                installedVersion = installedVersion,
                isUpgrade = installedVersion != null && manifest.version > installedVersion,
                keyFingerprint = keyFingerprint,
            ),
        )
    }

    /** 自带示例插件的预览（只给确认弹窗看，不装）。 */
    fun previewBundledSample(): ExternalPreviewResult {
        val temp = File(appContext.cacheDir, "bundled-sample-preview.wplugin")
        return try {
            appContext.assets.open(BUNDLED_SAMPLE).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            preview(temp)
        } catch (t: Throwable) {
            ExternalPreviewResult.Failed("读不到内置示例插件：" + (t.message ?: "assets 里没有 " + BUNDLED_SAMPLE))
        } finally {
            temp.delete()
        }
    }

    /** 从文件装（升级同一个 id 时保留插件私有数据）。 */
    fun installFromFile(file: File): ExternalInstallResult {
        val checked = ExternalPackageChecker.inspect(file)
        if (checked is ExternalPackageResult.Invalid) {
            return ExternalInstallResult.Failed("插件包不合法：" + checked.reason)
        }
        val pkg = (checked as ExternalPackageResult.Valid).pkg
        val external = pkg.manifest
        val manifest = external.manifest
        val id = manifest.id
        val version = manifest.version
        val installedRecord = record(id)
        val versionProblem = ExternalInstallPolicy.checkVersion(installedRecord?.version, version)
        if (versionProblem != null) return ExternalInstallResult.Failed(versionProblem)
        val keyFingerprint = (pkg.signature as? ExternalSignatureResult.Valid)?.keyFingerprint
        val signingProblem = ExternalInstallPolicy.checkSigning(installedRecord?.keyFingerprint, keyFingerprint)
        if (signingProblem != null) return ExternalInstallResult.Failed(signingProblem)

        EventLog.add("plugin", "外置插件安装开始 " + file.name + "（" + file.length() + " B）：" + id + " v" + version)
        // 无论记录里有没有，先把宿主里可能残留的同 id 状态收走。否则 core.install 会因为
        // hosts 里还留着旧 entry 直接回「插件已安装：<id>」，4.1.18 真机上就是这么被拒的。
        core.disable(id)
        deleteRecursively(pluginDir(id))

        val pluginRoot = pluginDir(id)
        if (!pluginRoot.mkdirs() && !pluginRoot.isDirectory) {
            return ExternalInstallResult.Failed("没法创建插件目录：" + pluginRoot.path)
        }
        val staging = File(pluginRoot, "staging-" + System.currentTimeMillis())
        deleteRecursively(staging)
        if (!staging.mkdirs()) return ExternalInstallResult.Failed("没法创建临时目录")

        try {
            extract(file, staging)
            val finalDir = File(pluginRoot, version.toString())
            deleteRecursively(finalDir)
            if (!staging.renameTo(finalDir)) {
                return ExternalInstallResult.Failed("解压目录改名失败（可能有残留下次再试）")
            }
            val codeCache = codeCacheDir(id)
            deleteRecursively(codeCache)
            codeCache.mkdirs()
            EventLog.add("plugin", "外置插件已解压 " + finalDir.path + "，dex=" + pkg.dexEntries.size + " 个")
            val plugin = when (val loaded = loadPlugin(finalDir, pkg.dexEntries, external.entryClass, codeCache, id)) {
                is LoadResult.Failed -> {
                    EventLog.add("plugin", "外置插件加载失败 " + id + "：" + loaded.reason)
                    deleteRecursively(pluginDir(id))
                    return ExternalInstallResult.Failed("插件加载失败：" + loaded.reason)
                }
                is LoadResult.Loaded -> {
                    EventLog.add("plugin", "外置插件已加载 " + external.entryClass)
                    loaded.plugin
                }
            }
            when (val installed = core.install(plugin)) {
                is RegisterResult.Ok -> Unit
                is RegisterResult.Rejected -> {
                    // 这条分支以前一个字都不写日志，所以真机只看到「安装被拒绝」却查不到原因。
                    EventLog.add("plugin", "外置插件注册被拒绝 " + id + "：" + installed.reason)
                    deleteRecursively(pluginDir(id))
                    deleteRecursively(codeCache)
                    return ExternalInstallResult.Failed("安装被拒绝：" + installed.reason)
                }
            }
            val record = ExternalPluginRecord(
                id = id,
                name = manifest.name,
                version = version,
                entryClass = external.entryClass,
                author = external.author,
                sha256 = sha256(file),
                installedAt = System.currentTimeMillis(),
                fileName = file.name,
                keyFingerprint = keyFingerprint,
            )
            writeRecords(readRecords().filter { it.id != id } + record)
            EventLog.add("plugin", "外置插件安装完成 " + id + " v" + version)
            return ExternalInstallResult.Installed(record)
        } catch (t: Throwable) {
            EventLog.add("plugin", "外置插件安装出错 " + id + "：" + (t.message ?: t.javaClass.simpleName))
            deleteRecursively(staging)
            deleteRecursively(pluginDir(id))
            deleteRecursively(codeCacheDir(id))
            core.disable(id)
            return ExternalInstallResult.Failed("安装出错：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /** 装 APK 里自带的示例插件（不需要任何文件传输，验证整条加载链路）。 */
    fun installBundledSample(): ExternalInstallResult {
        val temp = File(appContext.cacheDir, "bundled-sample.wplugin")
        return try {
            appContext.assets.open(BUNDLED_SAMPLE).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            installFromFile(temp)
        } catch (t: Throwable) {
            ExternalInstallResult.Failed("读不到内置示例插件：" + (t.message ?: "assets 里没有 " + BUNDLED_SAMPLE))
        } finally {
            temp.delete()
        }
    }

    /**
     * 自测专用：把内置示例插件包真正解压并用 DexClassLoader 加载一遍（不安装、不改任何记录）。
     * 返回 null = 通过；否则是具体原因。补的是「只校验结构、从没真加载过外置 dex」的空洞。
     */
    fun selfTestExternalLoad(): String? = selfTestExternalLoadAsset(BUNDLED_SAMPLE)

    /** 自测专用：页面型示例包真加载（页面型 dex 引用 SimplePage/SimpleAction，ABI 不一样）。 */
    fun selfTestExternalPageLoad(): String? = selfTestExternalLoadAsset(PAGE_SAMPLE)

    private fun selfTestExternalLoadAsset(asset: String): String? {
        val packageFile = File(appContext.cacheDir, "selftest-sample-" + File(asset).name)
        val extractDir = File(appContext.cacheDir, "selftest-extract")
        val codeCache = File(appContext.cacheDir, "selftest-dex")
        return try {
            appContext.assets.open(asset).use { input ->
                packageFile.outputStream().use { output -> input.copyTo(output) }
            }
            val checked = ExternalPackageChecker.inspect(packageFile)
            if (checked !is ExternalPackageResult.Valid) {
                return "示例包校验失败：" + (checked as ExternalPackageResult.Invalid).reason
            }
            val pkg = checked.pkg
            deleteRecursively(extractDir)
            if (!extractDir.mkdirs()) return "建不了临时解压目录"
            deleteRecursively(codeCache)
            codeCache.mkdirs()
            extract(packageFile, extractDir)
            when (val loaded = loadPlugin(
                dir = extractDir,
                dexEntries = pkg.dexEntries,
                entryClass = pkg.manifest.entryClass,
                codeCache = codeCache,
                expectedId = pkg.manifest.manifest.id,
            )) {
                is LoadResult.Failed -> "加载失败：" + loaded.reason
                is LoadResult.Loaded -> null
            }
        } catch (t: Throwable) {
            "抛异常：" + describe(t)
        } finally {
            packageFile.delete()
            deleteRecursively(extractDir)
            deleteRecursively(codeCache)
        }
    }

    /**
     * 自测专用：把内置示例插件**真的装一遍**（解压 → 加载 → 注册 → 写记录），装完再恢复原状。
     * 返回 null = 通过；否则是具体原因。
     *
     * 补的是 4.1.18 的第二个空洞：26 项里只「加载」不「注册」，所以自测全绿、真机装
     * 同一个包却卡在「安装被拒绝」。现在自测会完整走一遍真实安装链路。
     */
    fun selfTestExternalInstall(): String? = selfTestExternalInstallAsset(BUNDLED_SAMPLE, requirePage = false)

    /** 自测专用：页面型示例包真装一遍，而且必须真的注册出页面。 */
    fun selfTestExternalPageInstall(): String? = selfTestExternalInstallAsset(PAGE_SAMPLE, requirePage = true)

    private fun selfTestExternalInstallAsset(asset: String, requirePage: Boolean): String? {
        val packageFile = File(appContext.cacheDir, "selftest-install-" + File(asset).name)
        var testId: String? = null
        var installedByTest = false
        return try {
            appContext.assets.open(asset).use { input ->
                packageFile.outputStream().use { output -> input.copyTo(output) }
            }
            val checked = ExternalPackageChecker.inspect(packageFile)
            if (checked !is ExternalPackageResult.Valid) {
                return "示例包校验失败：" + (checked as ExternalPackageResult.Invalid).reason
            }
            val id = checked.pkg.manifest.manifest.id
            testId = id
            if (record(id) != null) {
                // 用户自己已经装过（或关掉）了示例插件：不动他的状态，只记一笔，别让自测报假失败
                EventLog.add(
                    "selftest",
                    "跳过真装（已存在记录 " + id + "，宿主里" + (if (core.isInstalled(id)) "是活的" else "未注册") + "）",
                )
                return null
            }
            when (val result = installFromFile(packageFile)) {
                is ExternalInstallResult.Failed -> "真装失败：" + result.reason
                is ExternalInstallResult.Installed -> {
                    installedByTest = true
                    if (!core.isInstalled(id)) {
                        "真装完成但宿主里没注册"
                    } else {
                        val pages = core.pagesOf(id).size
                        val buttons = core.buttonsOfPlugin(id).size
                        if (requirePage && pages == 0) {
                            "真装完成但没有注册出页面（页面型插件的页面链路没通）"
                        } else if (pages + buttons == 0) {
                            "真装完成但一个页面/按钮都没注册上"
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            "抛异常：" + describe(t)
        } finally {
            packageFile.delete()
            // 自测装进去的测试插件要清干净，别让用户的管理页里多一个
            if (installedByTest) {
                val id = testId
                if (id != null) runCatching { uninstall(id) }
            }
        }
    }

    /** 用户可放包的文件夹（把 .wplugin 放进去，再点「从文件夹安装」）。 */
    fun inboxDir(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.cacheDir
        val dir = File(base, DIR_INBOX)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** inbox 里现成的 .wplugin，按文件名排序。 */
    fun inboxFiles(): List<File> = inboxDir()
        .listFiles { f -> f.isFile && f.name.endsWith(PLUGIN_PACKAGE_EXTENSION) }
        ?.sortedBy { it.name }
        ?: emptyList()

    /** 批量安装（管理页确认之后调用）；每个包各自成败，一个失败不耽误后面的。 */
    fun installFiles(files: List<File>): List<ExternalInstallResult> = files.map { installFromFile(it) }

    /** 扫描 inbox 并逐个安装（旧入口，内部转调 installFiles）。 */
    fun installFromInbox(): List<ExternalInstallResult> = installFiles(inboxFiles())

    /** 真卸载：插件代码、数据、目录、记录一起清。 */
    fun uninstall(pluginId: String): Boolean {
        core.uninstall(pluginId)
        deleteRecursively(pluginDir(pluginId))
        deleteRecursively(codeCacheDir(pluginId))
        writeRecords(readRecords().filter { it.id != pluginId })
        return true
    }

    /** App 启动时按记录恢复外置插件。返回成功恢复的个数。 */
    fun restoreAll(): Int {
        var restored = 0
        val kept = ArrayList<ExternalPluginRecord>()
        for (record in readRecords()) {
            if (core.isInstalled(record.id)) {
                kept.add(record)
                continue
            }
            val dir = File(pluginDir(record.id), record.version.toString())
            val dexEntries = dexEntriesIn(dir)
            if (!dir.isDirectory || dexEntries.isEmpty()) {
                deleteRecursively(pluginDir(record.id))
                continue
            }
            val codeCache = codeCacheDir(record.id)
            deleteRecursively(codeCache)
            codeCache.mkdirs()
            val loaded = loadPlugin(dir, dexEntries, record.entryClass, codeCache, record.id)
            if (loaded is LoadResult.Failed) {
                EventLog.add("plugin", "外置插件恢复失败 " + record.id + "：" + loaded.reason)
                deleteRecursively(pluginDir(record.id))
                deleteRecursively(codeCache)
                continue
            }
            val plugin = (loaded as LoadResult.Loaded).plugin
            if (core.install(plugin) is RegisterResult.Rejected) {
                deleteRecursively(pluginDir(record.id))
                deleteRecursively(codeCache)
                continue
            }
            restored++
            kept.add(record)
        }
        writeRecords(kept)
        return restored
    }

    private fun extract(packageFile: File, target: File) {
        ZipFile(packageFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                val out = File(target, name)
                val targetRoot = target.canonicalFile
                val canonical = out.canonicalFile
                if (canonical.path != targetRoot.path &&
                    !canonical.path.startsWith(targetRoot.path + File.separator)
                ) {
                    throw IllegalStateException("包内路径越界：" + name)
                }
                canonical.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    canonical.outputStream().use { output -> input.copyTo(output) }
                }
                // Android 14+ 不允许 DexClassLoader 加载「对应用可写」的 dex，
                // 解压完立刻去掉写权限（4.1.18；之前只校验包结构，从没真正加载过外置 dex）。
                canonical.setReadOnly()
            }
        }
    }

    /** 加载结果：成功拿到插件实例，或者给出具体原因（原因进日志和提示，不再只说「看日志」）。 */
    private sealed interface LoadResult {
        class Loaded(val plugin: WristPlugin) : LoadResult
        class Failed(val reason: String) : LoadResult
    }

    private fun loadPlugin(
        dir: File,
        dexEntries: List<String>,
        entryClass: String,
        codeCache: File,
        expectedId: String,
    ): LoadResult {
        val dexFiles = dexEntries.map { File(dir, it) }
        // Android 14+ 会拒绝加载「应用可写」的 dex（SecurityException: Writable dex file ... is not allowed）。
        // extract() 已经置过只读，这里再兜一次底；失败时重试一次再定论。
        dexFiles.forEach { f ->
            runCatching {
                f.setWritable(false, false)
                f.setReadOnly()
            }
        }
        val first = runCatching { loadPluginAttempt(dexFiles, entryClass, codeCache, expectedId) }
        first.getOrNull()?.let { return it }
        val second = runCatching { loadPluginAttempt(dexFiles, entryClass, codeCache, expectedId) }
        second.getOrNull()?.let { return it }
        return LoadResult.Failed(describe(second.exceptionOrNull() ?: first.exceptionOrNull()))
    }

    private fun loadPluginAttempt(
        dexFiles: List<File>,
        entryClass: String,
        codeCache: File,
        expectedId: String,
    ): LoadResult {
        val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val loader = DexClassLoader(dexPath, codeCache.absolutePath, null, appContext.classLoader)
        val cls = Class.forName(entryClass, true, loader)
        if (!WristPlugin::class.java.isAssignableFrom(cls)) {
            return LoadResult.Failed("入口类没有实现 WristPlugin：" + entryClass)
        }
        val ctor = cls.getDeclaredConstructor()
        ctor.isAccessible = true
        val plugin = ctor.newInstance() as WristPlugin
        if (plugin.manifest.id != expectedId) {
            return LoadResult.Failed("代码里的插件 id 与清单不一致：" + plugin.manifest.id + " ≠ " + expectedId)
        }
        return LoadResult.Loaded(plugin)
    }

    private fun describe(t: Throwable?): String {
        if (t == null) return "未知原因"
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        val message = root.message
        return root.javaClass.simpleName + (if (message.isNullOrBlank()) "" else ": " + message)
    }

    private fun dexEntriesIn(dir: File): List<String> {
        if (!dir.isDirectory) return emptyList()
        val dexName = Regex("classes[0-9]*\\.dex")
        return (dir.listFiles() ?: emptyArray()).filter { it.isFile && dexName.matches(it.name) }
            .map { it.name }
            .sorted()
    }

    private fun pluginDir(pluginId: String): File = File(root, pluginId)

    private fun codeCacheDir(pluginId: String): File = File(appContext.codeCacheDir, "plugin-dex/" + pluginId)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    private fun readRecords(): List<ExternalPluginRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<ExternalPluginRecord>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isEmpty()) continue
                list.add(
                    ExternalPluginRecord(
                        id = id,
                        name = item.optString("name", id),
                        version = item.optInt("version", 1),
                        entryClass = item.optString("entryClass", ""),
                        author = item.optString("author", "").takeIf { it.isNotEmpty() },
                        sha256 = item.optString("sha256", ""),
                        installedAt = item.optLong("installedAt", 0L),
                        fileName = item.optString("fileName", ""),
                        keyFingerprint = item.optString("keyFingerprint", "").takeIf { it.isNotEmpty() },
                    ),
                )
            }
            list
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun writeRecords(records: List<ExternalPluginRecord>) {
        val array = JSONArray()
        for (record in records) {
            val item = JSONObject()
            item.put("id", record.id)
            item.put("name", record.name)
            item.put("version", record.version)
            item.put("entryClass", record.entryClass)
            item.put("author", record.author ?: "")
            item.put("sha256", record.sha256)
            item.put("installedAt", record.installedAt)
            item.put("fileName", record.fileName)
            item.put("keyFingerprint", record.keyFingerprint ?: "")
            array.put(item)
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        val children = file.listFiles()
        if (children != null) for (child in children) deleteRecursively(child)
        file.delete()
    }

    companion object {
        const val BUNDLED_SAMPLE = "plugins/hello.wplugin"
        /** 4.1.21 起内置的第二个示例包：带页面（按钮型示例只加一行按钮，覆盖不到页面链路）。 */
        const val PAGE_SAMPLE = "plugins/page-demo.wplugin"
        private const val PREFS_NAME = "watchnote_external_plugins"
        private const val KEY_RECORDS = "records"
        private const val DIR_PLUGINS = "plugins"
        private const val DIR_INBOX = "plugins-inbox"
    }
}
