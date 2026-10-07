package com.tablehockey.game.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.tablehockey.game.model.CrestFrame
import com.tablehockey.game.model.CrestType
import com.tablehockey.game.model.JerseyPattern
import com.tablehockey.game.model.TeamStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural front-view skater / goalie / crest art for menus. UI thread only (shared paints).
 * Figures are drawn in a 0..100 unit box (x centred on 0) that the public functions scale to
 * the requested pixel height. This does not touch the in-game top-down Renderer sprites.
 */
object TeamArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val clip = Path()
    private val rect = RectF()

    private const val SKIN = 0xFFE5B48F.toInt()
    private const val STEEL = 0xFFD5DBE2.toInt()
    private const val DARK = 0xFF1B1F27.toInt()

    fun shade(c: Int, f: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * f).toInt().coerceIn(0, 255),
            (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))

    fun lighten(c: Int, f: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
            (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())

    fun luma(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun color(c: Int): Paint { fill.style = Paint.Style.FILL; fill.shader = null; fill.color = c; return fill }
    private fun stroke(c: Int, w: Float): Paint { line.color = c; line.strokeWidth = w; return line }

    private fun poly(vararg v: Float): Path {
        path.reset()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
        path.close()
        return path
    }

    // ------------------------------------------------------------------ patterns

    /** Paints [pattern] stripes over a region whose base fill (primary) is already drawn. Caller clips. */
    fun drawPattern(c: Canvas, l: Float, t: Float, r: Float, b: Float, st: TeamStyle, pattern: JerseyPattern) {
        val w = r - l
        val h = b - t
        val cx = (l + r) / 2f
        when (pattern) {
            JerseyPattern.SOLID -> {}
            JerseyPattern.HOOPS -> for (i in 0 until 3) {
                val y = t + h * (0.16f + 0.3f * i)
                c.drawRect(l, y, r, y + h * 0.15f, color(st.secondary))
            }
            JerseyPattern.CHEST_BAND -> {
                c.drawRect(l, t + h * 0.26f, r, t + h * 0.56f, color(st.trim))
                c.drawRect(l, t + h * 0.3f, r, t + h * 0.52f, color(st.secondary))
            }
            JerseyPattern.SASH -> {
                c.drawPath(poly(l, t, l + 0.42f * w, t, r, b, r - 0.42f * w, b), color(st.secondary))
            }
            JerseyPattern.SPLIT -> c.drawRect(cx, t, r, b, color(st.secondary))
            JerseyPattern.CHEVRON -> {
                c.drawPath(poly(l, t + 0.14f * h, cx, t + 0.44f * h, r, t + 0.14f * h,
                    r, t + 0.34f * h, cx, t + 0.64f * h, l, t + 0.34f * h), color(st.secondary))
            }
            JerseyPattern.YOKE -> {
                c.drawPath(poly(l, t, r, t, r, t + 0.24f * h, cx, t + 0.4f * h, l, t + 0.24f * h), color(st.secondary))
            }
        }
    }

    /** Mini jersey tile used on the pattern picker chips; centred on (cx, cy) with half-size [r]. */
    fun drawPatternTile(c: Canvas, cx: Float, cy: Float, r: Float, st: TeamStyle, pattern: JerseyPattern) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        clip.reset()
        clip.addRoundRect(rect, r * 0.25f, r * 0.25f, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawRect(rect, color(st.primary))
        drawPattern(c, cx - r, cy - r, cx + r, cy + r, st, pattern)
        c.restore()
    }

    // ------------------------------------------------------------------ crest

    /** Crest centred on (cx, cy) with radius [r] (in whatever units the canvas is currently in). */
    fun drawCrest(c: Canvas, cx: Float, cy: Float, r: Float, st: TeamStyle, abbr: String) {
        // Frame
        fun frame(scale: Float): Path {
            val k = r * scale
            path.reset()
            when (st.frame) {
                CrestFrame.ROUND -> path.addCircle(cx, cy, k, Path.Direction.CW)
                CrestFrame.SHIELD -> {
                    path.moveTo(cx - k, cy - k)
                    path.lineTo(cx + k, cy - k)
                    path.lineTo(cx + k, cy + k * 0.15f)
                    path.quadTo(cx + k, cy + k * 0.8f, cx, cy + k * 1.15f)
                    path.quadTo(cx - k, cy + k * 0.8f, cx - k, cy + k * 0.15f)
                    path.close()
                }
                CrestFrame.DIAMOND -> {
                    path.moveTo(cx, cy - k * 1.2f); path.lineTo(cx + k * 1.05f, cy)
                    path.lineTo(cx, cy + k * 1.2f); path.lineTo(cx - k * 1.05f, cy); path.close()
                }
                CrestFrame.BADGE -> {
                    rect.set(cx - k, cy - k * 0.95f, cx + k, cy + k * 0.95f)
                    path.addRoundRect(rect, k * 0.4f, k * 0.4f, Path.Direction.CW)
                }
            }
            return path
        }
        c.drawPath(frame(1f), color(st.crestOutline))
        c.drawPath(frame(0.86f), color(st.crestBg))
        drawEmblem(c, cx, cy + if (st.frame == CrestFrame.SHIELD) r * 0.05f else 0f, r * 0.5f, st, abbr)
    }

    private fun drawEmblem(c: Canvas, cx: Float, cy: Float, k: Float, st: TeamStyle, abbr: String) {
        val fg = st.crestFg
        c.save()
        c.translate(cx, cy)
        c.scale(k, k)
        when (st.crest) {
            CrestType.LETTERS -> {
                text.color = fg
                text.textSize = if (abbr.length > 2) 1.15f else 1.5f
                c.drawText(abbr.take(3), 0f, 0.42f, text)
            }
            CrestType.STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = (-PI / 2 + i * PI / 5).toFloat()
                    val rr = if (i % 2 == 0) 1.1f else 0.45f
                    if (i == 0) path.moveTo(cos(a) * rr, sin(a) * rr) else path.lineTo(cos(a) * rr, sin(a) * rr)
                }
                path.close()
                c.drawPath(path, color(fg))
            }
            CrestType.BOLT -> c.drawPath(poly(0.2f, -1.1f, -0.6f, 0.1f, -0.05f, 0.1f, -0.25f, 1.1f, 0.65f, -0.25f, 0.08f, -0.25f), color(fg))
            CrestType.PEAKS -> c.drawPath(poly(-1.1f, 0.8f, -0.4f, -0.3f, -0.1f, 0.2f, 0.3f, -0.85f, 1.1f, 0.8f), color(fg))
            CrestType.CROWN -> c.drawPath(poly(-1f, 0.7f, -1f, -0.55f, -0.5f, 0.1f, 0f, -0.85f, 0.5f, 0.1f, 1f, -0.55f, 1f, 0.7f), color(fg))
            CrestType.FLAME -> {
                path.reset()
                path.moveTo(0.05f, -1.1f)
                path.quadTo(1.0f, -0.1f, 0.6f, 0.65f)
                path.quadTo(0.35f, 1.1f, 0f, 1.05f)
                path.quadTo(-0.45f, 1.1f, -0.65f, 0.6f)
                path.quadTo(-0.95f, -0.1f, -0.25f, -0.5f)
                path.quadTo(-0.2f, -0.8f, 0.05f, -1.1f)
                path.close()
                c.drawPath(path, color(fg))
            }
            CrestType.WAVES -> {
                for (row in -1..1) {
                    path.reset()
                    val y = row * 0.6f
                    path.moveTo(-1f, y)
                    path.cubicTo(-0.6f, y - 0.45f, -0.35f, y - 0.45f, 0f, y)
                    path.cubicTo(0.35f, y + 0.45f, 0.6f, y + 0.45f, 1f, y)
                    line.style = Paint.Style.STROKE
                    c.drawPath(path, stroke(fg, 0.32f))
                }
            }
            CrestType.PAW -> {
                rect.set(-0.62f, 0.0f, 0.62f, 0.95f)
                c.drawOval(rect, color(fg))
                c.drawCircle(-0.8f, -0.2f, 0.26f, color(fg))
                c.drawCircle(-0.3f, -0.68f, 0.28f, color(fg))
                c.drawCircle(0.3f, -0.68f, 0.28f, color(fg))
                c.drawCircle(0.8f, -0.2f, 0.26f, color(fg))
            }
            CrestType.WINGS -> {
                for (s in intArrayOf(-1, 1)) {
                    c.drawPath(poly(s * 0.2f, 0.1f, s * 1.1f, -0.85f, s * 1.0f, -0.2f, s * 0.9f, 0.1f,
                        s * 0.95f, 0.35f, s * 0.6f, 0.4f, s * 0.7f, 0.65f, s * 0.2f, 0.7f), color(fg))
                }
                c.drawCircle(0f, 0.05f, 0.3f, color(fg))
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------------ figures

    /** Front-view skater, [h] px tall, feet on [feetY], centred on [cx]. */
    fun drawSkater(c: Canvas, cx: Float, feetY: Float, h: Float, st: TeamStyle, abbr: String) {
        val u = h / 100f
        c.save()
        c.translate(cx, feetY - h)
        c.scale(u, u)
        val darkPants = shade(st.primary, 0.5f)

        for (s in intArrayOf(-1, 1)) {
            val x = s * 8f
            // skate boot, holder, runner
            rect.set(x - 6.5f, 91f, x + 6.5f, 97.5f)
            c.drawRoundRect(rect, 2f, 2f, color(DARK))
            c.drawRect(x - 5f, 97.2f, x + 5f, 98.4f, color(Color.WHITE))
            c.drawLine(x - 8f, 99.4f, x + 8f, 99.4f, stroke(STEEL, 1.2f))
            // sock with two trim bands
            rect.set(x - 4.8f, 66f, x + 4.8f, 92f)
            c.drawRect(rect, color(st.sock))
            c.drawRect(x - 4.8f, 73f, x + 4.8f, 76f, color(st.trim))
            c.drawRect(x - 4.8f, 78.5f, x + 4.8f, 80.5f, color(st.trim))
        }
        // pants
        rect.set(-15.5f, 47f, 15.5f, 70f)
        c.drawRoundRect(rect, 5f, 5f, color(darkPants))
        c.drawLine(0f, 58f, 0f, 70f, stroke(shade(darkPants, 0.6f), 0.8f))
        for (s in intArrayOf(-1, 1)) c.drawLine(s * 14.4f, 52f, s * 14.4f, 67f, stroke(st.trim, 1.8f))

        // arms (sleeves) drawn under the jersey
        for (s in intArrayOf(-1, 1)) {
            val sx = s * 17f
            val ex = s * 21.5f
            c.drawLine(sx, 27f, ex, 52f, stroke(st.primary, 9f))
            c.drawLine(s * 20.4f, 44f, ex, 52f, stroke(st.secondary, 9.2f))
            c.drawLine(s * 19.5f, 41.5f, s * 20.1f, 43f, stroke(st.trim, 9.4f))
        }

        drawJerseyTorso(c, st, abbr, 1f)

        // neck + head
        rect.set(-3.5f, 18f, 3.5f, 24f)
        c.drawRect(rect, color(shade(SKIN, 0.85f)))
        for (s in intArrayOf(-1, 1)) { rect.set(s * 10.4f - 2.2f, 8.5f, s * 10.4f + 2.2f, 16f); c.drawOval(rect, color(shade(st.helmet, 0.7f))) }
        rect.set(-6.8f, 7f, 6.8f, 20.5f)
        c.drawRoundRect(rect, 5f, 5f, color(SKIN))
        // helmet shell
        path.reset()
        path.moveTo(-10.6f, 14f)
        path.cubicTo(-12f, -2f, 12f, -2f, 10.6f, 14f)
        path.lineTo(7.6f, 11f)
        path.lineTo(-7.6f, 11f)
        path.close()
        c.drawPath(path, color(st.helmet))
        c.drawLine(0f, 0.8f, 0f, 9f, stroke(st.trim, 2.2f))
        line.alpha = 255
        c.drawLine(-7f, 4f, -3f, 1.8f, stroke(0x66FFFFFF, 1.4f))
        // face cage
        val cage = stroke(0xCCDCE3EA.toInt(), 0.6f)
        for (i in 0..2) c.drawLine(-6.6f, 11.5f + i * 3.3f, 6.6f, 11.5f + i * 3.3f, cage)
        for (i in -1..1) c.drawLine(i * 3.4f, 11f, i * 3.4f, 20f, cage)
        // eyes
        c.drawCircle(-2.6f, 13.6f, 0.7f, color(DARK))
        c.drawCircle(2.6f, 13.6f, 0.7f, color(DARK))

        // stick: shaft from right glove down to blade on the ice, with a puck
        c.drawLine(22.5f, 54f, 31f, 96.5f, stroke(0xFF2A2A2A.toInt(), 1.5f))
        c.drawLine(29.2f, 97.2f, 38f, 97.2f, stroke(0xFF2A2A2A.toInt(), 2.4f))
        rect.set(34f, 96.2f, 38.6f, 98.8f)
        c.drawRoundRect(rect, 1f, 1f, color(0xFF0B0B0B.toInt()))
        // gloves
        for (s in intArrayOf(-1, 1)) {
            rect.set(s * 21.8f - 4.6f, 50.5f, s * 21.8f + 4.6f, 59f)
            c.drawRoundRect(rect, 3f, 3f, color(shade(st.secondary, 0.45f)))
            c.drawLine(s * 21.8f - 4f, 51.5f, s * 21.8f + 4f, 51.5f, stroke(st.secondary, 1.3f))
        }
        c.restore()
    }

    /** Jersey body + pattern + hem + crest for a skater (or, with [bulk] > 1, a goalie chest). Unit space. */
    private fun drawJerseyTorso(c: Canvas, st: TeamStyle, abbr: String, bulk: Float) {
        val a = 18.5f * bulk
        path.reset()
        path.moveTo(-14f * bulk, 22f)
        path.lineTo(14f * bulk, 22f)
        path.cubicTo(a + 2f, 22f, a + 3f, 26f, a + 1.5f, 33f)
        path.lineTo(16.8f * bulk, 57f)
        path.lineTo(-16.8f * bulk, 57f)
        path.lineTo(-a - 1.5f, 33f)
        path.cubicTo(-a - 3f, 26f, -a - 2f, 22f, -14f * bulk, 22f)
        path.close()
        clip.set(path)
        c.drawPath(clip, color(st.primary))
        c.save()
        c.clipPath(clip)
        drawPattern(c, -22f * bulk, 22f, 22f * bulk, 57f, st, st.pattern)
        // hem stripes
        c.drawRect(-22f * bulk, 52f, 22f * bulk, 55f, color(st.secondary))
        c.drawRect(-22f * bulk, 55f, 22f * bulk, 57f, color(st.trim))
        // soft side shading
        c.drawRect(-22f * bulk, 22f, -14f * bulk, 57f, color(0x22000000))
        c.drawRect(14f * bulk, 22f, 22f * bulk, 57f, color(0x22000000))
        c.restore()
        // collar
        c.drawPath(poly(-5.5f, 21.5f, 0f, 28f, 5.5f, 21.5f), color(shade(st.primary, 0.45f)))
        c.drawLine(-5.5f, 21.8f, 0f, 28f, stroke(st.trim, 1f))
        c.drawLine(5.5f, 21.8f, 0f, 28f, stroke(st.trim, 1f))
        drawCrest(c, 0f, 39f, 7.4f, st, abbr)
    }

    /** Front-view goalie in full pads, [h] px tall. */
    fun drawGoalie(c: Canvas, cx: Float, feetY: Float, h: Float, st: TeamStyle, abbr: String) {
        val u = h / 100f
        c.save()
        c.translate(cx, feetY - h)
        c.scale(u, u)
        val pad = 0xFFF1F3F6.toInt()
        // leg pads
        for (s in intArrayOf(-1, 1)) {
            val x = s * 9.5f
            rect.set(x - 7.5f, 54f, x + 7.5f, 97f)
            c.drawRoundRect(rect, 4f, 4f, color(pad))
            c.drawRect(x - 7.5f, 60f, x + 7.5f, 66f, color(st.secondary))
            c.drawRect(x - 7.5f, 80f, x + 7.5f, 84f, color(st.primary))
            c.drawRect(x - 7.5f, 87f, x + 7.5f, 90f, color(st.primary))
            line.style = Paint.Style.STROKE
            rect.set(x - 7.5f, 54f, x + 7.5f, 97f)
            c.drawRoundRect(rect, 4f, 4f, stroke(0xFF9AA3AE.toInt(), 0.8f))
            c.drawRect(x - 6f, 96f, x + 6f, 99.6f, color(DARK))
        }
        // pants
        rect.set(-14f, 45f, 14f, 60f)
        c.drawRoundRect(rect, 4f, 4f, color(shade(st.primary, 0.5f)))
        // arms behind chest
        for (s in intArrayOf(-1, 1)) {
            c.drawLine(s * 20f, 29f, s * 27f, 50f, stroke(st.primary, 11f))
            c.drawLine(s * 25.3f, 44f, s * 27f, 50f, stroke(st.secondary, 11.2f))
        }
        drawJerseyTorso(c, st, abbr, 1.12f)
        // blocker (left) and catcher (right)
        rect.set(-34f, 47f, -22.5f, 62f)
        c.drawRoundRect(rect, 3f, 3f, color(pad))
        c.drawRect(-34f, 47f, -22.5f, 50f, color(st.secondary))
        c.drawRoundRect(rect, 3f, 3f, stroke(0xFF9AA3AE.toInt(), 0.8f))
        rect.set(22.5f, 44f, 36f, 61f)
        c.drawRoundRect(rect, 4f, 4f, color(shade(st.secondary, 0.8f)))
        c.drawRoundRect(rect, 4f, 4f, stroke(st.trim, 1.2f))
        c.drawLine(24f, 52f, 34.5f, 52f, stroke(shade(st.secondary, 0.5f), 0.9f))
        // goalie stick: shaft + wide paddle
        c.drawLine(-28f, 55f, -28f, 82f, stroke(0xFF2A2A2A.toInt(), 1.6f))
        rect.set(-31f, 80f, -25f, 98.5f)
        c.drawRoundRect(rect, 1.5f, 1.5f, color(0xFFC59A5B.toInt()))
        // mask: helmet colour with cage
        rect.set(-6.8f, 18.8f, 6.8f, 23f)
        c.drawRect(rect, color(shade(SKIN, 0.85f)))
        rect.set(-11.2f, -1f, 11.2f, 22.5f)
        c.drawOval(rect, color(st.helmet))
        rect.set(-6.6f, 7.5f, 6.6f, 19.5f)
        c.drawRoundRect(rect, 4f, 4f, color(0xFF14181F.toInt()))
        val cage = stroke(0xFFC8D0D9.toInt(), 0.7f)
        for (i in -2..2) c.drawLine(i * 2.8f, 8f, i * 2.8f, 19.2f, cage)
        for (i in 0..2) c.drawLine(-6.4f, 10f + i * 3.6f, 6.4f, 10f + i * 3.6f, cage)
        c.drawLine(0f, 0.5f, 0f, 7f, stroke(st.secondary, 2.4f))
        c.drawLine(-8.5f, 3f, -4f, 0.4f, stroke(0x66FFFFFF, 1.4f))
        c.restore()
    }
}
