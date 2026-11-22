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
import androidx.compose.ui.draw.drawBehind
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
   A 笔记（v2.5 结构：ScreenScaffold + TransformingLazyColumn + Fish）
   ============================================================ */

@Composable
internal fun NotesPage(store: Store, refresh: Int, fullAnim: Boolean, active: Boolean, onAdd: () -> Unit, onOpen: (String) -> Unit, onChanged: () -> Unit) {
    store.version.intValue
    val notes = store.notesSorted().filter { !it.todo }
    val pending = remember { mutableStateOf<Note?>(null) }
    WearListPage(edgeIcon = "＋", onEdge = onAdd, fishEye = fullAnim, active = active) {
        item {
            Fish {
                PageTitle("笔记", Store.fmtDate() + " · " + notes.size + " 条")
            }
        }
        if (notes.isEmpty()) {
            item {
                Fish {
                    Text(
                        "还没有笔记\n点下面的 ＋ 记一条\n可以写字，也可以语音",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp)
                    )
                }
            }
        }
        notes.forEachIndexed { idx, n ->
            item(key = n.id.ifBlank { "note-" + idx }) {
                Fish {
                    NoteCard(
                        store = store,
                        n = n,
                        onClick = { onOpen(n.id) },
                        onChanged = onChanged,
                        onDelete = { pending.value = n }
                    )
                }
            }
        }
    }
    pending.value?.let { note ->
        ConfirmDialog(
            data = ConfirmData(
                title = "删除笔记",
                message = "确定删除这条笔记吗？",
                okLabel = "删除",
                danger = true,
                onOk = {
                    store.deleteNote(note.id)
                    onChanged()
                    pending.value = null
                }
            ),
            onClosed = { pending.value = null }
        )
    }
}

@Composable
internal fun NoteCard(
    store: Store,
    n: Note,
    onClick: () -> Unit,
    onChanged: () -> Unit,
    onDelete: () -> Unit
) {
    val hold = rememberHoldProgress()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(squircle(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                val a = holdTintAlpha(hold.floatValue)
                if (a > 0f) drawRect(Color.Red.copy(alpha = a))
            }
            .holdToDelete(hold, onTap = onClick, onHold = onDelete)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Store.fmtStamp(n.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
            Spacer(Modifier.weight(1f))
            if (n.tag.isNotEmpty()) {
                Text(
                    n.tag,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (n.marked) "★" else "☆",
                color = if (n.marked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.clickable { store.toggleMark(n.id); onChanged() }.padding(horizontal = 4.dp)
            )
            Text(
                if (n.done) "✓" else "○",
                color = if (n.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.clickable { store.toggleDone(n.id); onChanged() }.padding(horizontal = 4.dp)
            )
            if (n.audioPath.isNotEmpty()) {
                Text("♪", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        val body = if (n.text.trim().isEmpty()) "（语音笔记）" else n.text.trim()
        Text(
            body,
            color = if (n.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.5.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
        if (n.audioPath.isNotEmpty()) {
            Text("▶ " + Math.max(1, Math.round(n.audioDur / 1000.0)) + " 秒语音", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

