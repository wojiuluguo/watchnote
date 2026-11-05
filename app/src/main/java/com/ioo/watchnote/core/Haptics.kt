package com.ioo.watchnote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 轻震动反馈：只用 Android Vibrator，不碰 Wear 触感类（小米 Watch 5 缺类会崩）。
 * 点击=短震一下，翻页=更轻一下；滚动不震。
 */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(ctx: Context) {
        vibrator = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    fun tap() = play(14L, 55)

    fun page() = play(9L, 35)

    private fun play(ms: Long, amplitude: Int) {
        if (!UiPrefsState.haptics.value) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
        }
    }
}
