package com.ioo.watchnote

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * App 内事件日志：页面切换/子页面/表冠/生命周期，最多 800 条。
 * 内存里立即记录；文件写入放到后台单线程，避免滚动/切页时主线程做磁盘 IO（性能优化）。
 */
object EventLog {
    private const val MAX = 800
    private val entries = ArrayDeque<String>()
    private val pending = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private var file: File? = null
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "event-log-writer").apply { isDaemon = true }
    }
    private var flushScheduled = false

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "event_log.txt")
        file = f
        try {
            if (f.exists()) {
                f.readLines().forEach { if (it.isNotBlank()) entries.addLast(it) }
                trimMemory()
                rewriteFile()
            }
        } catch (e: Exception) {
        }
    }

    fun add(tag: String, msg: String) {
        val line = fmt.format(Date()) + " [" + tag + "] " + msg
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX) entries.removeFirst()
            pending.addLast(line)
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        writer.execute {
            val batch: List<String>
            synchronized(lock) {
                batch = pending.toList()
                pending.clear()
                flushScheduled = false
            }
            if (batch.isEmpty()) return@execute
            try {
                file?.appendText(batch.joinToString(separator = "") { it + "\n" })
            } catch (e: Exception) {
            }
        }
    }

    fun flush() {
        scheduleFlush()
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            pending.clear()
        }
        writer.execute {
            try {
                file?.writeText("")
            } catch (e: Exception) {
            }
        }
    }

    fun dump(): String = synchronized(lock) {
        if (entries.isEmpty()) "（没有事件记录）" else entries.joinToString("\n")
    }

    private fun trimMemory() {
        while (entries.size > MAX) entries.removeFirst()
    }

    private fun rewriteFile() {
        try {
            file?.writeText(entries.joinToString("\n") + "\n")
        } catch (e: Exception) {
        }
    }
}
