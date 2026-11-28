package com.ioo.watchnote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/* ============================================================
   4.1.28：更新包「直接装」
   ------------------------------------------------------------
   4.1.26/4.1.27 的坑：下载完只把 APK 扔进公共 Download 目录，提示
   「用文件管理器点开手动安装」。可手表（Wear OS）根本没有文件管理
   器，用户进不去 Download，更新链路等于断的。

   现在改成：下载落在 App 自己的 updates 目录，下载完直接调这里拉起
   系统安装界面。系统会问「安装未知应用」权限（不是存储权限），
   没给过就先跳到本应用的开关页。
   ============================================================ */

/** 一次安装尝试的结果。 */
internal sealed class InstallResult {
    /** 系统安装界面已经拉起来了。 */
    object Started : InstallResult()

    /** 没有「安装未知应用」权限，已经跳到设置页让用户开。 */
    object NeedPermission : InstallResult()

    /** 真装不了，reason 给用户看。 */
    data class Failed(val reason: String) : InstallResult()
}

internal object ApkInstaller {

    const val AUTHORITY = "com.ioo.watchnote.fileprovider"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 是否已经有「安装未知应用」（安装其他应用）权限。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true

    /**
     * 跳到本应用的「安装未知应用」开关页。
     * 不同系统暴露的入口不一样，按从准到糙的顺序一个个试。
     */
    fun openPermissionSettings(context: Context): Boolean {
        val pkg = Uri.parse("package:" + context.packageName)
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                EventLog.add("update", "已打开安装权限设置：" + intent.action)
                return true
            } catch (e: Exception) {
                EventLog.add(
                    "update",
                    "打开安装权限设置失败（" + intent.action + "）：" + (e.message ?: e.javaClass.simpleName)
                )
            }
        }
        return false
    }

    /**
     * 拉起系统安装界面。
     *
     * @param apk 下载好的安装包（在 App 自己的 updates 目录里）
     */
    fun start(context: Context, apk: File): InstallResult {
        if (!apk.exists() || apk.length() <= 0L) {
            EventLog.add("update", "安装包不见了：" + apk.absolutePath)
            return InstallResult.Failed("安装包不见了，请重新下载")
        }
        if (!canInstall(context)) {
            EventLog.add("update", "没有「安装未知应用」权限，先跳设置页")
            return InstallResult.NeedPermission
        }
        val uri = try {
            FileProvider.getUriForFile(context, AUTHORITY, apk)
        } catch (e: Exception) {
            EventLog.add("update", "取安装包地址失败：" + (e.message ?: e.javaClass.simpleName))
            return InstallResult.Failed("读不到安装包，请重新下载")
        }
        // 各系统安装器入口不一样：标准 VIEW 优先，再退老的 INSTALL_PACKAGE，最后按通用二进制喂一次
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME_APK),
            Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, MIME_APK),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
        )
        var lastReason = "这台手表没有安装界面"
        for (intent in attempts) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                EventLog.add("update", "已拉起系统安装界面（" + intent.action + "）")
                return InstallResult.Started
            } catch (e: Exception) {
                lastReason = e.message ?: e.javaClass.simpleName
                EventLog.add("update", "拉起安装界面失败（" + intent.action + "）：" + lastReason)
            }
        }
        return InstallResult.Failed(lastReason)
    }
}
