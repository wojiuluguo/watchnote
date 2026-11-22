package com.ioo.watchnote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/* ============================================================
   点开一条：这一页只显示内容。
   有录音才有「播放语音」；办事多一行时间段。
   退出键 = 屏幕底部正中央的 ←（和其它子页一模一样）。
   修改 / 导出 / 标记完成 / 删除：从屏幕最上边往下滑，
   不松手滑到屏幕一半就进「更多」（圆屏左下角会被裁掉，
   所以不再放按钮）。
   ============================================================ */

@Composable
internal fun NoteViewPage(
    activity: MainActivity,
    store: Store,
    noteId: String,
    onBack: () -> Unit,
    onMore: (String) -> Unit
) {
    val note = store.getNote(noteId)
    if (note == null) {
        LaunchedEffect(noteId) { onBack() }
        return
    }

    var pull by remember(noteId) { mutableFloatStateOf(0f) }
    var fired by remember(noteId) { mutableStateOf(false) }
    val density = LocalDensity.current
    val halfPx = with(density) { (LocalConfiguration.current.screenHeightDp.dp / 2).toPx() }
    val fadePx = with(density) { 24.dp.toPx() }
    val pullDp = with(density) { pull.toDp() }

    Box(Modifier.fillMaxSize()) {
        SubPageFrame(
            onBack = {
                activity.stopAudio()
                onBack()
            }
        ) {
            // 屏幕最上边 36dp：往下滑的起手区（透明，不挡内容）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .offset(y = (-16).dp)
                    .pointerInput(noteId) {
                        detectVerticalDragGestures(
                            onDragStart = { pull = 0f; fired = false },
                            onDragEnd = { pull = 0f; fired = false },
                            onDragCancel = { pull = 0f; fired = false }
                        ) { change, dy ->
                            change.consume()
                            if (fired) return@detectVerticalDragGestures
                            pull = (pull + dy).coerceAtLeast(0f)
                            if (pull >= halfPx) {
                                fired = true
                                Haptics.tap()
                                onMore(note.id)
                            }
                        }
                    }
            )

            if (note.todo) {
                val head = if (note.dueAt <= 0L) "◷ 没定时间"
                else (if (note.done) "✓ 已办完 · " else "") + "◷ " + Store.rangeLabel(note.dueAt, note.endAt)
                Text(
                    head,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                )
                val dur = if (note.dueAt > 0L) Store.durationLabel(note.dueAt, note.endAt) else ""
                if (dur.isNotBlank()) {
                    Text(
                        if (note.done) "已办完 · " + dur else dur,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 1.dp, start = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(squircle(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    if (note.text.isBlank()) "（语音笔记）" else note.text,
                    color = if (note.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textDecoration = if (note.done) TextDecoration.LineThrough else null
                )
            }

            if (note.audioPath.isNotEmpty()) {
                SettingPill(
                    "▶",
                    "播放语音（" + ((note.audioDur / 1000L).coerceAtLeast(1L)) + " 秒）",
                    onClick = { activity.playAudio(note.audioPath) }
                )
            }
        }

        // 下滑时跟着手指出来的小胶囊，提示松手/继续滑就是「更多」
        if (pull > 2f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = pullDp)
                    .alpha((pull / fadePx).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text("更多", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}
