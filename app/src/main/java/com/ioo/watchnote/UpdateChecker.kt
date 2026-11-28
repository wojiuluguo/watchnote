package com.ioo.watchnote

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/* ============================================================
   4.1.26：App 内检查更新（数据来源 GitHub Releases）
   - 只读 GitHub 的「最新正式 Release」，比 versionCode 才提示
   - 结果缓存到本地：进「关于 → 详细信息」时距上次超过 24 小时才自动查一次
   - 网络失败静默记录 + 指数退避，绝不启动/后台乱查
   ============================================================ */

/** 一次「检查更新」的结果。 */
internal sealed class UpdateCheck {
    /** GitHub 上有一个比本机新的版本 */
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long
    ) : UpdateCheck()

    /** 已经是最新 */
    data class Latest(val versionName: String) : UpdateCheck()

    /** 这次没查成，reason 直接给用户看 */
    data class Failed(val reason: String) : UpdateCheck()
}

internal object UpdateRepo {
    const val OWNER = "wojiuluguo"
    const val REPO = "watchnote"
    const val API_LATEST = "https://api.github.com/repos/wojiuluguo/watchnote/releases/latest"
    const val RELEASES_PAGE = "https://github.com/wojiuluguo/watchnote/releases"
}

/** Release 说明（GitHub 的 markdown）转成圆屏上好读的纯文本。 */
internal fun prettyReleaseNotes(body: String): String {
    val cleaned = body
        .replace("\r\n", "\n")
        .replace("**", "")
        .replace("```", "")
        .replace("`", "")
        .lines()
        .map { line ->
            val t = line.trim()
            when {
                t.startsWith("### ") -> t.removePrefix("### ").trim()
                t.startsWith("## ") -> t.removePrefix("## ").trim()
                t.startsWith("# ") -> t.removePrefix("# ").trim()
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> "· " + t.substring(2).trim()
                else -> t
            }
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    return if (cleaned.length > 3200) cleaned.substring(0, 3200) + "\n…（介绍太长，后面省略）" else cleaned
}

internal class UpdateChecker(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("watchnote_update", Context.MODE_PRIVATE)

    private val codeInBody = Regex("versionCode\\s*[:：]\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val codeInAsset = Regex("-(\\d+)\\.apk$", RegexOption.IGNORE_CASE)
    private val tagVersion = Regex("^[vV]?(\\d+(?:\\.\\d+)*)")

    /* ---------------- 本地状态 ---------------- */

    fun lastCheckAt(): Long = prefs.getLong("last_check_at", 0L)

    fun lastCheckText(): String {
        val at = lastCheckAt()
        if (at <= 0L) return "还没查过"
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        return "上次 " + fmt.format(java.util.Date(at))
    }

    /** 距上次检查够不够久（失败时按 1/2/4/8 天退避，别一直撞墙） */
    fun shouldAutoCheck(): Boolean {
        val at = lastCheckAt()
        if (at <= 0L) return true
        val fails = prefs.getInt("fail_count", 0).coerceIn(0, 3)
        val gap = DAY_MS * (1L shl fails)
        return System.currentTimeMillis() - at >= gap
    }

    /** 上次结果的缓存（失败也缓存，但页面上会显示原因） */
    fun cached(): UpdateCheck? {
        return when (prefs.getString("last_kind", null)) {
            "available" -> UpdateCheck.Available(
                versionName = prefs.getString("last_version_name", "") ?: "",
                versionCode = prefs.getInt("last_version_code", 0),
                notes = prefs.getString("last_notes", "") ?: "",
                apkUrl = prefs.getString("last_apk_url", "") ?: "",
                apkSize = prefs.getLong("last_apk_size", 0L)
            )
            "latest" -> UpdateCheck.Latest(prefs.getString("last_version_name", "") ?: "")
            "failed" -> UpdateCheck.Failed(prefs.getString("last_reason", "") ?: "检查失败")
            else -> null
        }
    }

    /** 关于页那一行显示的小字 */
    fun cachedStatusText(): String {
        return when (val c = cached()) {
            is UpdateCheck.Available -> "有新版 " + c.versionName
            is UpdateCheck.Latest -> "已是最新"
            is UpdateCheck.Failed -> "检查失败"
            null -> "点一下查"
        }
    }

    /* ---------------- 真正去查 ---------------- */

    /** 阻塞式检查，必须放在 IO 线程跑；任何异常都变成 UpdateCheck.Failed，不往外抛。 */
    fun check(): UpdateCheck {
        val started = System.currentTimeMillis()
        val result: UpdateCheck = try {
            val conn = (URL(UpdateRepo.API_LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WristNote/" + BuildConfig.VERSION_NAME)
            }
            try {
                val code = conn.responseCode
                when {
                    code == 404 -> UpdateCheck.Failed("GitHub 上还没有正式发布的版本")
                    code == 403 || code == 429 -> UpdateCheck.Failed("GitHub 暂时限制了请求，过一会儿再试")
                    code !in 200..299 -> UpdateCheck.Failed("GitHub 返回了 HTTP " + code)
                    else -> {
                        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        parseRelease(text)
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: java.net.SocketTimeoutException) {
            UpdateCheck.Failed("连接 GitHub 超时，检查手表网络后再试")
        } catch (e: java.net.UnknownHostException) {
            UpdateCheck.Failed("连不上 GitHub（可能是网络挡了），可以稍后重试")
        } catch (e: Exception) {
            UpdateCheck.Failed("检查失败：" + (e.message ?: e.javaClass.simpleName))
        }
        save(result, started)
        return result
    }

    private fun parseRelease(json: String): UpdateCheck {
        val obj = JSONObject(json)
        if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
            return UpdateCheck.Failed("最新 Release 还是草稿/预发布，等正式版")
        }
        val tag = obj.optString("tag_name", "").trim()
        val notes = prettyReleaseNotes(obj.optString("body", ""))
        val assets = obj.optJSONArray("assets")
        var apkUrl = ""
        var apkSize = 0L
        var codeFromAsset = 0
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                apkUrl = a.optString("browser_download_url", "")
                apkSize = a.optLong("size", 0L)
                codeFromAsset = codeInAsset.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                break
            }
        }
        val versionName = tag.replaceFirst(Regex("^[vV]"), "").ifEmpty { "未知版本" }
        val codeInBodyValue = codeInBody.find(obj.optString("body", ""))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val remoteCode = if (codeFromAsset > 0) codeFromAsset else codeInBodyValue
        val newer = if (remoteCode > 0) {
            remoteCode > BuildConfig.VERSION_CODE
        } else {
            compareVersions(versionName, BuildConfig.VERSION_NAME) > 0
        }
        if (!newer) return UpdateCheck.Latest(versionName)
        if (apkUrl.isEmpty()) {
            return UpdateCheck.Failed("发现 " + versionName + "，但 Release 里没有 APK 附件")
        }
        return UpdateCheck.Available(
            versionName = versionName,
            versionCode = remoteCode,
            notes = notes,
            apkUrl = apkUrl,
            apkSize = apkSize
        )
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private fun save(result: UpdateCheck, at: Long) {
        val e = prefs.edit()
        e.putLong("last_check_at", at)
        when (result) {
            is UpdateCheck.Available -> {
                e.putString("last_kind", "available")
                e.putString("last_version_name", result.versionName)
                e.putInt("last_version_code", result.versionCode)
                e.putString("last_notes", result.notes)
                e.putString("last_apk_url", result.apkUrl)
                e.putLong("last_apk_size", result.apkSize)
                e.putInt("fail_count", 0)
            }
            is UpdateCheck.Latest -> {
                e.putString("last_kind", "latest")
                e.putString("last_version_name", result.versionName)
                e.putInt("fail_count", 0)
            }
            is UpdateCheck.Failed -> {
                e.putString("last_kind", "failed")
                e.putString("last_reason", result.reason)
                e.putInt("fail_count", prefs.getInt("fail_count", 0) + 1)
            }
        }
        e.apply()
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
/**
 * 下载更新包。
 *
 * 4.1.28 起不再往公共 Download 目录写：手表上没有文件管理器，用户根本点不到那个 APK。
 * 现在落在 App 自己的 updates 目录，下完由 [ApkInstaller] 直接交给系统安装器。
 */
internal class ApkDownloader(private val context: Context) {

    /**
     * @param onProgress 已下载字节 / 总字节（总数未知时是 0），在 IO 线程回调
     * @return 下载好的安装包文件（在 App 私有 updates 目录里）
     */
    fun download(url: String, fileName: String, sizeHint: Long, onProgress: (Long, Long) -> Unit): File {
        val dir = updateDir(context)
        if (!dir.exists() && !dir.mkdirs()) throw java.io.IOException("建不了更新目录")
        // 旧包先清掉：省空间，也保证待会儿装上的就是这次下的
        dir.listFiles()?.forEach { it.delete() }
        val tmp = File(dir, "watchnote-update.tmp")
        if (tmp.exists()) tmp.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WristNote/" + BuildConfig.VERSION_NAME)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("下载失败（HTTP " + code + "）")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: sizeHint
            var done = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(32 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport >= 64L * 1024L || (total > 0 && done >= total)) {
                            lastReport = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw java.io.IOException("下载不完整（" + done + "/" + total + " 字节）")
            if (done <= 0L) throw java.io.IOException("下载到的是空文件")
            val target = File(dir, fileName)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.inputStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                tmp.delete()
            }
            return target
        } finally {
            conn.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}

/** 更新包存放目录：App 私有，卸载 App 时一起清掉，也不用向用户要存储权限。 */
internal fun updateDir(context: Context): File =
    context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")
