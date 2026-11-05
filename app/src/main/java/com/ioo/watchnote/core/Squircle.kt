package com.ioo.watchnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续曲率圆角（squircle）。
 *
 * 普通 RoundedCornerShape 的角是四分之一圆，圆和直边相接的地方曲率从 0 一下变成 1/r，
 * 眼睛能看出"从这里开始拐弯"。这里把角换成超椭圆 u^n + v^n = P^n：
 * 两个端点曲率是 0，和直边平滑接上（C2），所以看起来是慢慢弯下去的。
 * n = 2 就是普通圆角；n 越大越方，n≈4.2 接近 iOS 图标的手感。
 */
class SquircleShape(
    private val radius: Dp,
    private val exponent: Float = 4.2f,
) : Shape {
    private var lastW = -1f
    private var lastH = -1f
    private var lastP = -1f
    private var lastPath: Path? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }
        var p = with(density) { radius.toPx() }
        val maxP = minOf(size.width, size.height) / 2f
        if (p > maxP) p = maxP
        if (p <= 0.01f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        if (size.width == lastW && size.height == lastH && p == lastP) {
            lastPath?.let { return Outline.Generic(it) }
        }
        val path = buildPath(size.width, size.height, p)
        lastW = size.width
        lastH = size.height
        lastP = p
        lastPath = path
        return Outline.Generic(path)
    }

    private fun buildPath(w: Float, h: Float, p: Float): Path {
        val e = 2.0 / exponent
        val steps = 20

        fun pw(v: Double): Float {
            val safe = if (v < 0.0) 0.0 else v
            return safe.pow(e).toFloat()
        }

        // 角上的采样点 = 圆心 + (sx*P*cos^(2/n)θ, sy*P*sin^(2/n)θ)
        fun arc(path: Path, cx: Float, cy: Float, sx: Float, sy: Float, start: Double, end: Double) {
            for (i in 0..steps) {
                val t = start + (end - start) * i / steps
                val c = pw(cos(t))
                val s = pw(sin(t))
                path.lineTo(cx + sx * p * c, cy + sy * p * s)
            }
        }

        val path = Path()
        path.moveTo(p, 0f)
        path.lineTo(w - p, 0f)
        arc(path, w - p, p, 1f, -1f, PI / 2, 0.0)      // 右上：顶边 -> 右边
        path.lineTo(w, h - p)
        arc(path, w - p, h - p, 1f, 1f, 0.0, PI / 2)    // 右下：右边 -> 底边
        path.lineTo(p, h)
        arc(path, p, h - p, -1f, 1f, PI / 2, 0.0)       // 左下：底边 -> 左边
        path.lineTo(0f, p)
        arc(path, p, p, -1f, -1f, 0.0, PI / 2)          // 左上：左边 -> 顶边
        path.close()
        return path
    }
}

@Composable
fun squircle(radius: Dp, exponent: Float = 4.2f): Shape = remember(radius, exponent) {
    SquircleShape(radius, exponent)
}
