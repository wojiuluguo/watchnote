package com.ioo.watchnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import kotlinx.coroutines.CoroutineScope

/** 全局表冠事件总线：当前可见页面注册自己的滚动处理，Activity 收到表冠事件后统一转发。 */
object RotaryBus {
    private val stack = ArrayDeque<((Float) -> Unit)>()

    /** 页面进入时注册，离开时注销；后注册的（子页面/当前页）优先。 */
    fun register(handler: (Float) -> Unit): () -> Unit {
        stack.addLast(handler)
        return {
            stack.remove(handler)
        }
    }

    private var count = 0

    fun scroll(delta: Float): Boolean {
        val h = stack.lastOrNull() ?: return false
        h(delta)
        count++
        if (count % 100 == 0) {
            EventLog.add("crown", "events=" + count + " lastDelta=" + delta)
        }
        return true
    }
}

/** 占位滚动行为：TransformingLazyColumn 需要它来避免默认触感类导致崩溃；真正滚动由 RotaryBus 负责。 */
private class NoopRotaryBehavior : RotaryScrollableBehavior {
    override suspend fun CoroutineScope.performScroll(
        timestampMillis: Long,
        delta: Float,
        inputDeviceId: Int,
        orientation: androidx.compose.foundation.gestures.Orientation
    ) {
    }
}

@Composable
fun rememberNoopRotaryBehavior(): RotaryScrollableBehavior {
    return remember { NoopRotaryBehavior() }
}
