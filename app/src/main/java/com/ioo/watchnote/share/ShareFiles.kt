package com.ioo.watchnote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** 列表里显示的笔记名：取第一行；没有文字就显示（空笔记）。 */
internal fun noteDisplayName(n: Note): String {
    val first = n.text.trim().lines().firstOrNull()?.trim().orEmpty()
    return when {
        first.isEmpty() -> "（空笔记）"
        first.length > 16 -> first.take(16) + "…"
        else -> first
    }
}

private fun safeFileName(n: Note): String {
    val raw = n.text.trim().lines().firstOrNull()?.trim().orEmpty()
        .replace(Regex("[\\\\/:*?\"<>|\r\n]"), "")
        .trim()
    return raw.take(20).ifBlank { "watchnote-note" }
}

/** 下载文件名：只选一条时用它的名字，多选用条数。 */
internal fun shareFileName(notes: List<Note>, png: Boolean): String {
    val ext = if (png) ".png" else ".txt"
    val base = if (notes.size == 1) safeFileName(notes.first()) else "watchnote-notes-" + notes.size
    return base + ext
}

/** 发给手机的文字版：只导出选中的笔记。 */
internal fun buildShareText(notes: List<Note>): String {
    val sb = StringBuilder()
    sb.append("腕记 · 笔记\n")
    sb.append(Store.fmtDate()).append(" ").append(Store.fmtClock()).append("\n")
    sb.append("共 ").append(notes.size).append(" 条\n\n")
    if (notes.isEmpty()) sb.append("（没有选中笔记）\n")
    notes.forEach { n ->
        sb.append("· ")
        if (n.marked) sb.append("★ ")
        sb.append(n.text.replace("\n", " ").ifBlank { "(无内容)" })
        sb.append("  [").append(n.tag).append("]")
        if (n.done) sb.append("  已完成")
        sb.append("  ").append(Store.fmtStamp(n.updatedAt)).append("\n")
    }
    return sb.toString()
}

private class ShareLine(val text: String, val color: Int, val size: Float, val bold: Boolean = false)

/** 发给手机的图片版：只导出选中的笔记，480 宽，高度随内容。 */
internal fun buildSharePng(notes: List<Note>): Bitmap {
    val bg = Color.rgb(15, 19, 24)
    val fg = Color.rgb(236, 241, 246)
    val dim = Color.rgb(156, 164, 174)
    val accent = Color.rgb(255, 170, 90)
    val width = 480
    val padH = 30f
    val maxW = width - padH * 2

    val measure = Paint(Paint.ANTI_ALIAS_FLAG)
    val lines = ArrayList<ShareLine>()
    lines.add(ShareLine("腕记", accent, 34f, true))
    lines.add(ShareLine(Store.fmtDate() + " " + Store.fmtClock(), dim, 20f))
    lines.add(ShareLine("", bg, 10f))
    lines.add(ShareLine("笔记 · " + notes.size + " 条", accent, 22f, true))
    lines.add(ShareLine("", bg, 6f))

    if (notes.isEmpty()) {
        lines.add(ShareLine("没有选中笔记", dim, 24f))
    } else {
        notes.forEach { n ->
            val head = (if (n.marked) "★ " else "") + n.tag + if (n.done) " · 已完成" else ""
            lines.add(ShareLine(head, accent, 18f, true))
            measure.textSize = 24f
            var rest = n.text.replace("\n", " ").ifBlank { "(无内容)" }
            var rows = 0
            while (rest.isNotEmpty() && rows < 8) {
                val count = measure.breakText(rest, true, maxW, null)
                val take = if (count <= 0) rest.length else count
                lines.add(ShareLine(rest.substring(0, take), fg, 24f))
                rest = rest.substring(take)
                rows++
            }
            if (rest.isNotEmpty()) lines.add(ShareLine("…", fg, 24f))
            lines.add(ShareLine(Store.fmtStamp(n.updatedAt), dim, 17f))
            lines.add(ShareLine("", bg, 12f))
        }
    }
    lines.add(ShareLine("", bg, 20f))

    var height = 40f
    lines.forEach { height += it.size * 1.55f }
    height += 30f
    val bmp = Bitmap.createBitmap(width, height.toInt().coerceAtLeast(240), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(bg)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = 54f
    lines.forEach { line ->
        if (line.text.isNotEmpty()) {
            paint.textSize = line.size
            paint.color = line.color
            paint.typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(line.text, padH, y, paint)
        }
        y += line.size * 1.55f
    }
    return bmp
}
