package com.ioo.watchnote

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 长按进度 0..1；从 1 秒开始泛红，满 2 秒触发删除确认。 */
@Composable
fun rememberHoldProgress(): MutableFloatState = remember { mutableFloatStateOf(0f) }

fun holdTintAlpha(progress: Float): Float {
    val start = 0.5f
    if (progress <= start) return 0f
    return ((progress - start) / (1f - start)).coerceIn(0f, 1f) * 0.62f
}

fun Modifier.holdToDelete(
    progress: MutableFloatState,
    holdMillis: Long = 2000L,
    onTap: () -> Unit = {},
    onHold: () -> Unit
): Modifier = pointerInput(holdMillis, onTap, onHold) {
    detectTapGestures(onPress = {
        val downAt = System.currentTimeMillis()
        progress.floatValue = 0f
        val result = coroutineScope {
            val ticker = launch {
                while (isActive) {
                    val p = ((System.currentTimeMillis() - downAt).toFloat() / holdMillis.toFloat()).coerceIn(0f, 1f)
                    progress.floatValue = p
                    if (p >= 1f) break
                    delay(40L)
                }
            }
            val released = withTimeoutOrNull(holdMillis + 80L) { tryAwaitRelease() }
            ticker.cancel()
            released
        }
        progress.floatValue = 0f
        val heldMs = System.currentTimeMillis() - downAt
        if (result == null && heldMs >= holdMillis - 120L) {
            Haptics.tap()
            onHold()
        } else if (result == true && heldMs < 500L) {
            Haptics.tap()
            onTap()
        }
    })
}

/**
 * 按住不动（系统长按时长）后进入“滑动选择”模式：手指不抬起时左右拖动只更新回调，
 * 并消费指针事件（页面不会跟着左右翻页），抬起手指回调 onHoldEnd。
 * 未按住就抬起 = 普通点击；被翻页/滚动手势接管 = 取消，不会误触。
 */
fun Modifier.holdSlideMenu(
    progress: MutableFloatState,
    onTap: () -> Unit = {},
    onHoldStart: (Offset) -> Unit = {},
    onHoldMove: (Offset) -> Unit = {},
    onHoldEnd: (Offset) -> Unit = {},
    onHoldCancel: () -> Unit = {}
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val downAt = System.currentTimeMillis()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            progress.floatValue = 0f
            val endChange = currentEvent.changes.firstOrNull { it.id == down.id }
            if (endChange != null && !endChange.pressed && !endChange.isConsumed &&
                System.currentTimeMillis() - downAt < 500L
            ) {
                Haptics.tap()
                onTap()
            }
            return@awaitEachGesture
        }
        progress.floatValue = 1f
        Haptics.tap()
        onHoldStart(longPress.position)
        var last = longPress.position
        while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull { it.id == down.id }
            if (ch == null) { onHoldCancel(); break }
            last = ch.position
            onHoldMove(last)
            ch.consume()
            if (!ch.pressed) {
                onHoldEnd(last)
                break
            }
        }
        progress.floatValue = 0f
    }
}