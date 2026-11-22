package com.ioo.watchnote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.wear.compose.material3.AppScaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    lateinit var store: Store
        private set

    private var recorder: MediaRecorder? = null
    private var recordPath: String? = null
    private var recordStartMs = 0L
    private var pendingStart: ((Boolean, String) -> Unit)? = null
    private var pendingFinish: ((String?, Long, String?) -> Unit)? = null
    private var pendingPermission = false

    private var player: MediaPlayer? = null
    private var speechResult: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashPrefs = getSharedPreferences("crash", MODE_PRIVATE)
        val crashLog = crashPrefs.getString("log", "")
        val crashVer = crashPrefs.getString("ver", "")
        val curVer = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        if (!crashLog.isNullOrEmpty()) {
            if (crashVer.isNullOrEmpty() || crashVer == curVer) {
                startActivity(Intent(this, CrashActivity::class.java))
                finish()
                return
            } else {
                crashPrefs.edit().clear().apply()
            }
        }
        enableEdgeToEdge()
        store = Store(this)
        EventLog.init(this)
        Haptics.init(this)
        EventLog.add("app", "onCreate topTimePref=" + store.getShowTopTime())
        EventLog.add("app", "appScaffold defaultTime disabled")
        EventLog.add("app", "onCreate version=" + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }))
        setContent {
            WristNoteTheme(UiPrefsState.accent.value, UiPrefsState.customAccent.value) {
                AppScaffold(timeText = { }) {
                    AppRoot(this@MainActivity, store)
                }
            }
        }
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /* ---------------- 语音转文字 ---------------- */

    fun startSpeech(onResult: (String?) -> Unit) {
        speechResult = onResult
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        try {
            startActivityForResult(intent, REQ_SPEECH)
        } catch (e: Exception) {
            speechResult = null
            toast("本机没有语音识别，试试「录音」")
            onResult(null)
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SPEECH) {
            val cb = speechResult
            speechResult = null
            if (resultCode == RESULT_OK && data != null) {
                val list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                cb?.invoke(list?.firstOrNull())
            } else {
                cb?.invoke(null)
            }
        }
    }

    /* ---------------- 录音 ---------------- */

    fun startRecording(
        onStarted: (Boolean, String) -> Unit,
        onFinish: (String?, Long, String?) -> Unit
    ) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = onStarted
            pendingFinish = onFinish
            pendingPermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
            return
        }
        startRecorderInternal(onStarted, onFinish)
    }

    private fun startRecorderInternal(
        onStarted: (Boolean, String) -> Unit,
        onFinish: (String?, Long, String?) -> Unit
    ) {
        try {
            val dir = File(filesDir, "audio")
            if (!dir.exists()) dir.mkdirs()
            recordPath = File(dir, "rec_" + System.currentTimeMillis() + ".m4a").absolutePath
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            recorder = rec
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100)
            rec.setAudioEncodingBitRate(96000)
            rec.setOutputFile(recordPath)
            rec.prepare()
            rec.start()
            recordStartMs = System.currentTimeMillis()
            pendingStart = null
            pendingFinish = onFinish
            onStarted(true, "")
        } catch (e: Exception) {
            cleanupRecorder(true)
            onStarted(false, "录音启动失败：" + (e.message ?: ""))
        }
    }

    fun stopRecording(onResult: (String?, Long, String?) -> Unit) {
        pendingFinish = onResult
        stopRecordingInternal()
    }

    private fun stopRecordingInternal() {
        val finish = pendingFinish
        if (recorder == null) {
            finish?.invoke(null, 0, "")
            pendingFinish = null
            return
        }
        val dur = System.currentTimeMillis() - recordStartMs
        val path = recordPath
        try {
            recorder?.stop()
        } catch (e: Exception) {
            cleanupRecorder(true)
            pendingFinish = null
            finish?.invoke(null, 0, "录音太短或失败")
            return
        }
        cleanupRecorder(false)
        pendingFinish = null
        if (path == null || !File(path).exists() || File(path).length() < 200) {
            path?.let { File(it).delete() }
            finish?.invoke(null, 0, "录音太短，没有保存")
            return
        }
        finish?.invoke(path, dur, "")
    }

    private fun cleanupRecorder(deleteFile: Boolean) {
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
        if (deleteFile) {
            try { recordPath?.let { File(it).delete() } } catch (e: Exception) { }
        }
        recordPath = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            val started = pendingStart
            val finish = pendingFinish
            pendingStart = null
            pendingFinish = null
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && started != null && finish != null) {
                startRecorderInternal(started, finish)
            } else {
                started?.invoke(false, "没有麦克风权限，无法录音")
            }
        }
    }

    /* ---------------- 播放 ---------------- */

    fun playAudio(path: String) {
        if (!File(path).exists()) {
            toast("录音文件不存在")
            return
        }
        stopAudio()
        try {
            val p = MediaPlayer()
            player = p
            p.setDataSource(path)
            p.setOnCompletionListener { stopAudio() }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            stopAudio()
            toast("播放失败")
        }
    }

    fun stopAudio() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) { }
        player = null
    }

    /* ---------------- 备份 ---------------- */

    fun exportBackup(): String? {
        return try {
            val dir = getExternalFilesDir(null) ?: filesDir
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "watchnote-backup.json")
            FileOutputStream(f).use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun importBackup(): String? {
        return try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val f = File(dir, "watchnote-backup.json")
            if (!f.exists()) return null
            val out = ByteArrayOutputStream()
            FileInputStream(f).use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            out.toString("UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        EventLog.add("life", "onResume")
    }

    override fun onPause() {
        super.onPause()
        EventLog.add("life", "onPause")
        EventLog.flush()
        if (recorder != null) stopRecordingInternal()
        stopAudio()
    }

    fun copyText(text: String) {
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("watchnote_log", text))
            toast("已复制")
        } catch (e: Exception) {
        }
    }

    /** 把文本写到公共 Download 目录（老系统写 App 自己的外部目录），返回路径。 */
    fun writeDownload(name: String, text: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues()
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    val done = android.content.ContentValues()
                    done.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, done, null, null)
                    "Download/" + name
                } else null
            } else {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = java.io.File(dir, name)
                java.io.FileOutputStream(f).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 导出 App 事件日志到公共 Download 目录，返回路径。 */
    fun exportAppLog(): String? {
        return try {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.CHINA).format(java.util.Date())
            val name = "腕记Beta_事件日志_" + stamp + ".txt"
            val text = EventLog.dump()
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues()
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    val done = android.content.ContentValues()
                    done.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, done, null, null)
                    "Download/" + name
                } else null
            } else {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = java.io.File(dir, name)
                java.io.FileOutputStream(f).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /* 表冠统一入口：所有页面都通过 RotaryBus 滚动，避免部分页面没反应 */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (v != 0f && RotaryBus.scroll(v)) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    companion object {
        private const val REQ_SPEECH = 41
        private const val REQ_AUDIO = 42
    }
}