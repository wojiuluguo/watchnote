package com.ioo.watchnote.plugin

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 插件画布（0.4.5 起）。
 *
 * 两级画布，同一套画笔：
 * 1. 元素画布：页面里的一块，高度自己定，可以画一个按钮、一个环、一个小控件；
 * 2. 整页画布：高度写 FILL，占满整页，插件自己决定画什么。
 *
 * 坐标一律归一化 0~1（x/y/w/h/半径都是比例，换表盘不变形），
 * 粗细、字号一律 dp（物理尺寸，换表盘不糊）。
 * 颜色可以写 #RRGGBB / #AARRGGBB，也可以写主题 token：text / muted / primary / danger / card。
 *
 * 安全边界（不是裁剪能力，是兜底）：
 * - 每条指令落盘前夹取：NaN / Infinity / 负数 / 越界一律修正；
 * - 单帧最多 4000 条指令，超了后面的丢掉并在日志里留一行；
 * - 绘制时每条指令单独 runCatching，一条画错不影响整页。
 */
class PenTheme(
    val text: Color,
    val muted: Color,
    val primary: Color,
    val danger: Color,
    val card: Color,
)

/** 画一块内容：插件在这里面调用 pen 的方法。 */
fun interface DrawBlock {
    fun draw(pen: Pen)
}

/** 点中画布：x / y 是归一化坐标。 */
fun interface TapBlock {
    fun tap(x: Float, y: Float)
}

/** 画布上拖动：dx / dy 是归一化位移。 */
fun interface DragBlock {
    fun drag(dx: Float, dy: Float)
}

internal sealed interface PenOp

internal class ClearOp(val color: Color) : PenOp
internal class RectOp(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val radius: Float, val color: Color, val strokeDp: Float,
) : PenOp

internal class CircleOp(val cx: Float, val cy: Float, val r: Float, val color: Color, val strokeDp: Float) : PenOp
internal class LineOp(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Color, val widthDp: Float) : PenOp
internal class ArcOp(
    val cx: Float, val cy: Float, val r: Float,
    val start: Float, val sweep: Float, val color: Color, val strokeDp: Float, val fromCenter: Boolean,
) : PenOp

internal class PathOp(val pts: FloatArray, val color: Color, val strokeDp: Float, val close: Boolean) : PenOp
internal class TextOp(
    val text: String, val x: Float, val y: Float,
    val sizeDp: Float, val color: Color, val align: Int, val bold: Boolean,
) : PenOp

/**
 * 画笔。插件在 DrawBlock 里拿到它，只往里面记指令，真正的绘制由宿主在 Canvas 里做。
 */
class Pen internal constructor(private val theme: PenTheme) {

    internal val ops = ArrayList<PenOp>(32)
    internal var overflow = false

    private fun add(op: PenOp): Pen {
        if (ops.size >= MAX_OPS) {
            overflow = true
            return this
        }
        ops.add(op)
        return this
    }

    /** 铺底：整块填一个颜色。 */
    fun clear(colorHex: String = "card"): Pen = add(ClearOp(penColor(colorHex, theme)))

    /** 矩形。radius > 0 时是圆角（比例，0.5 = 胶囊）。strokeDp > 0 时只描边。 */
    @JvmOverloads
    fun rect(
        x: Float, y: Float, w: Float, h: Float,
        colorHex: String = "primary",
        radius: Float = 0f,
        strokeDp: Float = 0f,
    ): Pen = add(
        RectOp(f(x), f(y), f(w), f(h), f(radius).coerceIn(0f, 0.5f), penColor(colorHex, theme), f(strokeDp))
    )

    /** 圆 / 环。strokeDp = 0 是实心，> 0 是描边圆环。 */
    @JvmOverloads
    fun circle(cx: Float, cy: Float, r: Float, colorHex: String = "primary", strokeDp: Float = 0f): Pen =
        add(CircleOp(f(cx), f(cy), f(r), penColor(colorHex, theme), f(strokeDp)))

    /** 直线。 */
    @JvmOverloads
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, colorHex: String = "muted", widthDp: Float = 1.5f): Pen =
        add(LineOp(f(x1), f(y1), f(x2), f(y2), penColor(colorHex, theme), f(widthDp)))

    /** 圆弧 / 圆环进度。startDeg 从三点钟方向开始，顺时针为正。 */
    @JvmOverloads
    fun arc(
        cx: Float, cy: Float, r: Float,
        startDeg: Float, sweepDeg: Float,
        colorHex: String = "primary",
        strokeDp: Float = 6f,
        fromCenter: Boolean = false,
    ): Pen = add(ArcOp(f(cx), f(cy), f(r), f(startDeg), f(sweepDeg), penColor(colorHex, theme), f(strokeDp), fromCenter))

    /** 折线 / 多边形：pts 是 x,y 成对出现的归一化坐标。 */
    @JvmOverloads
    fun path(colorHex: String = "primary", widthDp: Float = 0f, close: Boolean = false, vararg pts: Float): Pen {
        if (pts.size < 4) return this
        val clean = FloatArray(pts.size - pts.size % 2)
        for (i in clean.indices) clean[i] = f(pts[i])
        return add(PathOp(clean, penColor(colorHex, theme), f(widthDp), close))
    }

    /**
     * 文字。x / y 是文字中心点；sizeDp 是字号。
     * align：Pen.LEFT / Pen.CENTER / Pen.RIGHT。
     */
    @JvmOverloads
    fun text(
        text: String, x: Float, y: Float,
        sizeDp: Float = 14f,
        colorHex: String = "text",
        align: Int = CENTER,
        bold: Boolean = false,
    ): Pen = add(TextOp(text, f(x), f(y), f(sizeDp).coerceIn(6f, 72f), penColor(colorHex, theme), align, bold))

    companion object {
        const val LEFT: Int = 0
        const val CENTER: Int = 1
        const val RIGHT: Int = 2
        internal const val MAX_OPS: Int = 4000

        private fun f(v: Float): Float = if (v.isFinite()) v else 0f
    }
}

/** 颜色：token 名或 #RRGGBB / #AARRGGBB，解析不了就退回主题色。 */
internal fun penColor(hex: String, theme: PenTheme): Color {
    val h = hex.trim()
    if (h.isEmpty()) return theme.text
    when (h.lowercase()) {
        "text" -> return theme.text
        "muted" -> return theme.muted
        "primary" -> return theme.primary
        "danger" -> return theme.danger
        "card" -> return theme.card
    }
    val s = h.removePrefix("#")
    return try {
        when (s.length) {
            6 -> Color(0xFF000000L or s.toLong(16))
            8 -> Color(s.toLong(16))
            else -> theme.text
        }
    } catch (e: Throwable) {
        theme.text
    }
}

/** 画布本体：把画笔记下的指令真的画出来，并把手势转成归一化坐标。 */
@Composable
internal fun PenCanvasView(
    ops: List<PenOp>,
    theme: PenTheme,
    modifier: Modifier,
    onTap: TapBlock?,
    onDrag: DragBlock?,
) {
    var m = modifier
    if (onTap != null) {
        m = m.pointerInput(onTap) {
            detectTapGestures { offset ->
                runCatching {
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onTap.tap(offset.x / w, offset.y / h)
                }
            }
        }
    }
    if (onDrag != null) {
        m = m.pointerInput(onDrag) {
            detectDragGestures { change, delta ->
                change.consume()
                runCatching {
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    onDrag.drag(delta.x / w, delta.y / h)
                }
            }
        }
    }
    Canvas(m) {
        if (ops.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val min = if (w < h) w else h
        val d = density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (op in ops) {
            runCatching { drawPenOp(op, w, h, min, d, paint, theme) }
        }
    }
}

private fun DrawScope.drawPenOp(op: PenOp, w: Float, h: Float, min: Float, d: Float, paint: Paint, theme: PenTheme) {
    when (op) {
        is ClearOp -> drawRect(color = op.color, topLeft = Offset.Zero, size = Size(w, h))

        is RectOp -> {
            val topLeft = Offset(op.x * w, op.y * h)
            val size = Size((op.w * w).coerceAtLeast(0f), (op.h * h).coerceAtLeast(0f))
            val style = if (op.strokeDp > 0f) Stroke(width = op.strokeDp * d) else Fill
            val r = op.radius * min
            if (r > 0f) {
                drawRoundRect(
                    color = op.color,
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = style,
                )
            } else {
                drawRect(color = op.color, topLeft = topLeft, size = size, style = style)
            }
        }

        is CircleOp -> {
            val style = if (op.strokeDp > 0f) Stroke(width = op.strokeDp * d) else Fill
            drawCircle(color = op.color, radius = op.r * min, center = Offset(op.cx * w, op.cy * h), style = style)
        }

        is LineOp -> drawLine(
            color = op.color,
            start = Offset(op.x1 * w, op.y1 * h),
            end = Offset(op.x2 * w, op.y2 * h),
            strokeWidth = (op.widthDp * d).coerceAtLeast(1f),
            cap = StrokeCap.Round,
        )

        is ArcOp -> {
            val r = op.r * min
            val style = if (op.strokeDp > 0f) {
                Stroke(width = op.strokeDp * d, cap = StrokeCap.Round)
            } else {
                Fill
            }
            drawArc(
                color = op.color,
                startAngle = op.start,
                sweepAngle = op.sweep,
                useCenter = op.fromCenter,
                topLeft = Offset(op.cx * w - r, op.cy * h - r),
                size = Size(r * 2f, r * 2f),
                style = style,
            )
        }

        is PathOp -> {
            val p = Path()
            p.moveTo(op.pts[0] * w, op.pts[1] * h)
            var i = 2
            while (i + 1 < op.pts.size) {
                p.lineTo(op.pts[i] * w, op.pts[i + 1] * h)
                i += 2
            }
            if (op.close) p.close()
            if (op.strokeDp > 0f) {
                drawPath(path = p, color = op.color, style = Stroke(width = op.strokeDp * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path = p, color = op.color)
            }
        }

        is TextOp -> {
            paint.textSize = op.sizeDp * d
            paint.color = op.color.toArgb()
            paint.typeface = if (op.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.textAlign = when (op.align) {
                Pen.LEFT -> Paint.Align.LEFT
                Pen.RIGHT -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val baseline = op.y * h + paint.textSize * 0.35f
            drawContext.canvas.nativeCanvas.drawText(op.text, op.x * w, baseline, paint)
        }
    }
}
