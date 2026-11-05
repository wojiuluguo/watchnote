package com.ioo.watchnote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxScope
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.ceil

/* ============================================================
   数据 / 导航
   ============================================================ */

/* ============================================================
   子页面：笔记编辑
   ============================================================ */

@Composable
internal fun NoteEditPage(activity: MainActivity, store: Store, noteId: String, onBack: () -> Unit, onChanged: () -> Unit) {
    val existing = remember(noteId) { if (noteId.isEmpty()) null else store.getNote(noteId) }
    var id by remember { mutableStateOf(existing?.id ?: "") }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var tag by remember { mutableStateOf(existing?.tag ?: "") }
    var marked by remember { mutableStateOf(existing?.marked ?: false) }
    var done by remember { mutableStateOf(existing?.done ?: false) }
    var audioPath by remember { mutableStateOf(existing?.audioPath ?: "") }
    var audioDur by remember { mutableStateOf(existing?.audioDur ?: 0L) }
    var recording by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun commit() {
        val draft = Note(id = id, text = text, tag = tag, marked = marked, done = done, audioPath = audioPath, audioDur = audioDur)
        val saved = store.saveNote(draft)
        if (saved != null) id = saved.id
    }

    SubPageFrame(onBack) {
        PageTitle(if (existing == null) "新建笔记" else "编辑笔记")
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceChip(if (marked) "★ 标记" else "☆ 标记", marked, { marked = !marked; if (id.isNotEmpty()) commit() }, Modifier.weight(1f))
            ChoiceChip(if (done) "✓ 完成" else "○ 完成", done, { done = !done; if (id.isNotEmpty()) commit() }, Modifier.weight(1f))
        }
        FieldLabel("内容")
        TextFieldBox(text, { text = it; if (id.isNotEmpty()) commit() }, "写点什么…也可以点下面语音输入", singleLine = false, minLines = 3, heightDp = 130)
        FieldLabel("标签")
        TextFieldBox(tag, { tag = it; if (id.isNotEmpty()) commit() }, "输入标签，比如：作业 / 数学 / 待办")
        Spacer(Modifier.height(8.dp))
        SettingPill("🎤", "语音转文字", onClick = {
            activity.startSpeech { result ->
                if (result != null) {
                    text = if (text.isEmpty()) result else text + " " + result
                    if (id.isNotEmpty()) commit()
                } else activity.toast("没听清，再试一次")
            }
        })
        if (!recording) {
            SettingPill("●", "录音", onClick = {
                activity.startRecording(
                    onStarted = { ok, msg ->
                        if (ok) { recording = true; activity.toast("正在录音，点「停止录音」结束") }
                        else activity.toast(msg)
                    },
                    onFinish = { path, dur, err ->
                        recording = false
                        if (path != null) { audioPath = path; audioDur = dur; commit(); onChanged(); activity.toast("录音已保存") }
                        else if (err != null && err.isNotEmpty()) activity.toast(err)
                    }
                )
            })
        } else {
            SettingPill("■", "停止录音", danger = true, onClick = {
                activity.stopRecording { path, dur, err ->
                    recording = false
                    if (path != null) { audioPath = path; audioDur = dur; commit(); onChanged(); activity.toast("录音已保存") }
                    else if (err != null && err.isNotEmpty()) activity.toast(err)
                }
            })
        }
        if (audioPath.isNotEmpty()) {
            SettingPill("▶", "播放语音（" + Math.max(1, Math.round(audioDur / 1000.0)) + " 秒）", onClick = { activity.playAudio(audioPath) })
            SettingPill("✕", "删除语音", onClick = {
                audioPath = ""; audioDur = 0
                if (id.isNotEmpty()) commit()
            })
        }
        if (!showDelete) {
            SettingPill("✓", "保存", onClick = {
                if (recording) activity.toast("先停止录音")
                else { commit(); onChanged(); onBack() }
            })
            if (existing != null) {
                SettingPill("✕", "删除笔记", danger = true, onClick = { showDelete = true })
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    SettingPill("✕", "确定删除", danger = true, onClick = {
             
                        store.deleteNote(id)
                        onChanged()
                        onBack()
                    })
                }
                Box(Modifier.weight(1f)) {
                    SettingPill("←", "取消", onClick = { showDelete = false })
                }
            }
        }
    }
}

