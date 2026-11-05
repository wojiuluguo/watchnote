package com.ioo.watchnote

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.os.Build
import android.widget.Toast
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter

/** 应用入口：捕获崩溃，把堆栈写进 SharedPreferences，下次启动先显示日志。 */
class WristNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                PrintWriter(sw).use { e.printStackTrace(it) }
                val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (x: Exception) { "" }
                getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit()
                    .putString("log", sw.toString())
                    .putString("ver", ver)
                    .putLong("time", System.currentTimeMillis())
                    .commit()
            } catch (x: Throwable) {
            }
            if (prev != null) prev.uncaughtException(thread, e)
            else {
                Process.killProcess(Process.myPid())
                System.exit(1)
            }
        }
    }
}

/** 原生崩溃日志页：黑底白字，可滚动，点“继续使用”清掉日志并打开 App。 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashPrefs = getSharedPreferences("crash", Context.MODE_PRIVATE)
        val log = crashPrefs.getString("log", "") ?: ""
        val crashVer = crashPrefs.getString("ver", "") ?: ""
        val crashTime = crashPrefs.getLong("time", 0L)
        val timeText = try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(crashTime))
        } catch (e: Exception) { "" }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(14, 14, 14, 14)

        val title = TextView(this)
        title.text = "上次崩溃日志（拍照发给开发者）"
        title.setTextColor(Color.parseColor("#C8B6FF"))
        title.textSize = 12f
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val appVer = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        val deviceInfo = "崩溃时间: $timeText\n" +
                "崩溃时App版本: $crashVer\n" +
                "设备: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "当前App版本: $appVer\n\n"
        val fullLog = deviceInfo + (if (log.isEmpty()) "（没有日志）" else log)
        val tv = TextView(this)
        tv.text = fullLog
        tv.setTextIsSelectable(true)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 9f
        tv.setPadding(0, 8, 0, 8)
        val sv = ScrollView(this)
        sv.addView(tv)
        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(sv, svLp)

        val saveBtn = Button(this)
        saveBtn.text = "下载日志到 Download"
        saveBtn.setTextColor(Color.BLACK)
        saveBtn.setBackgroundColor(Color.parseColor("#E7E2E9"))
        saveBtn.setOnClickListener {
            val where = saveLogToDownloads(fullLog)
            Toast.makeText(this, "已保存到 " + where, Toast.LENGTH_LONG).show()
        }
        root.addView(saveBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val copyBtn = Button(this)
        copyBtn.text = "复制日志"
        copyBtn.setTextColor(Color.BLACK)
        copyBtn.setBackgroundColor(Color.parseColor("#E7E2E9"))
        copyBtn.setOnClickListener {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("崩溃日志", fullLog))
                Toast.makeText(this, "已复制日志，可粘贴发我", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
            }
        }
        root.addView(copyBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val btn = Button(this)
        btn.text = "继续使用"
        btn.setTextColor(Color.BLACK)
        btn.setBackgroundColor(Color.parseColor("#C8B6FF"))
        btn.setOnClickListener {
            getSharedPreferences("crash", Context.MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    /** 把崩溃日志保存到公共 Download 目录，返回显示路径。 */
    private fun saveLogToDownloads(text: String): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.CHINA).format(java.util.Date())
        val name = "腕记Beta_崩溃日志_" + stamp + ".txt"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues()
                values.put(MediaStore.Downloads.DISPLAY_NAME, name)
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                values.put(MediaStore.Downloads.IS_PENDING, 1)
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    val done = ContentValues()
                    done.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, done, null, null)
                    "Download/" + name
                } else "保存失败（无法创建文件）"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, name)
                java.io.FileOutputStream(file).use { it.write(text.toByteArray()) }
                "Download/" + name
            }
        } catch (e: Exception) {
            "保存失败：" + (e.message ?: "")
        }
    }
}