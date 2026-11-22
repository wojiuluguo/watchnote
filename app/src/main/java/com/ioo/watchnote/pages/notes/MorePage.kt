package com.ioo.watchnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/* ============================================================
   「⋯ 更多」：修改 / 导出 / 标记完成 / 删除。
   ============================================================ */

@Composable
internal fun NoteMorePage(
    activity: MainActivity,
    store: Store,
    noteId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onGone: () -> Unit
) {
    val note = store.getNote(noteId)
    if (note == null) {
        LaunchedEffect(noteId) { onBack() }
        return
    }
    var showDelete by remember { mutableStateOf(false) }

    SubPageFrame(onBack = onBack) {
        PageTitle("更多", previewLine(note))

        SettingPill("✎", "修改", onClick = { onEdit(note.id) })

        SettingPill("⇪", "导出为文本", onClick = {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.CHINA)
                .format(java.util.Date())
            val name = "腕记_" + stamp + ".txt"
            val path = activity.writeDownload(name, noteToText(note))
            activity.toast(if (path == null) "导出失败" else "已导出到 下载")
        })

        SettingPill(
            if (note.done) "↺" else "✓",
            if (note.done) "标记为未完成" else "标记已完成",
            onClick = {
                store.toggleDone(note.id)
                onBack()
            }
        )

        SettingPill("🗑", "删除", danger = true, onClick = { showDelete = true })
    }

    if (showDelete) {
        ConfirmDialog(
            ConfirmData("删除", "这一条删掉后找不回来。", "删除", true) {
                activity.stopAudio()
                store.deleteNote(noteId)
                showDelete = false
                onGone()
            }
        ) { showDelete = false }
    }
}

private fun previewLine(note: Note): String {
    val body = note.text.trim().replace("\n", " ")
    return when {
        body.isEmpty() -> "语音笔记"
        body.length <= 14 -> body
        else -> body.substring(0, 14) + "…"
    }
}

private fun noteToText(note: Note): String {
    val sb = StringBuilder()
    sb.append(if (note.todo) "办事" else "笔记").append("\n")
    sb.append("分类：").append(note.tag.ifBlank { "随手记" }).append("\n")
    sb.append("创建：").append(Store.fmtStamp(note.createdAt)).append("\n")
    if (note.todo) {
        sb.append("时间：").append(Store.rangeLabel(note.dueAt, note.endAt)).append("\n")
        sb.append("状态：").append(if (note.done) "已办完" else "未办完").append("\n")
    } else if (note.done) {
        sb.append("状态：已完成").append("\n")
    }
    if (note.audioPath.isNotEmpty()) {
        sb.append("语音：").append((note.audioDur / 1000L).coerceAtLeast(1L)).append(" 秒").append("\n")
    }
    sb.append("\n")
    sb.append(if (note.text.isBlank()) "（语音笔记）" else note.text)
    return sb.toString()
}
