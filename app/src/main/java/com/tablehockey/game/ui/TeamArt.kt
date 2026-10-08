package com.tablehockey.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tablehockey.game.model.CrestFrame
import com.tablehockey.game.model.CrestType
import com.tablehockey.game.model.JerseyPattern
import com.tablehockey.game.model.TeamStyle
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Procedural front-view skater / goalie / crest art for menus. UI thread only (shared paints).
 *
 * Figures are drawn in a 0..100 unit box (x centred on 0, feet at y = 100) with gradient-shaded,
 * tapered shapes so they read as lit, rounded bodies. Use [skaterBitmap] / [goalieBitmap] to
 * render a figure once into a Bitmap and blit that; [drawSkater] / [drawGoalie] draw directly
 * (the gradients are allocated per call, so don't call those from onDraw every frame).
 * Horizontal art extents: skater x in [-27, 39], goalie x in [-36, 37].
 * This does not touch the in-game top-down Renderer sprites.
 */
object TeamArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
        isLinearText = true
    }
    private val path = Path()
    private val tpath = Path()
    private val clip = Path()
    private val rect = RectF()

    private const val SKIN = 0xFFE5B48F.toInt()
    private const val STEEL = 0xFFD5DBE2.toInt()
    private const val DARK = 0xFF1B1F27.toInt()
    private const val NONE = 0x00000000

    /** Figure box, in units: width 84 (x -42..42), height 104 (y -2..102). */
    const val BOX_W = 84f
    const val BOX_H = 104f

    fun shade(c: Int, f: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * f).toInt().coerceIn(0, 255),
            (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))

    fun lighten(c: Int, f: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
            (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())

    fun luma(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun color(c: Int): Paint { fill.style = Paint.Style.FILL; fill.shader = null; fill.color = c; return fill }
    private fun stroke(c: Int, w: Float): Paint { line.color = c; line.strokeWidth = w; return line }

    private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int): Paint {
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        return gp
    }

    private fun linN(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray): Paint {
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP)
        return gp
    }

    private fun rad(cx: Float, cy: Float, r: Float, c0: Int, c1: Int): Paint {
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(cx, cy, r, c0, c1, Shader.TileMode.CLAMP)
        return gp
    }

    private fun radN(cx: Float, cy: Float, r: Float, colors: IntArray, pos: FloatArray): Paint {
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(cx, cy, r, colors, pos, Shader.TileMode.CLAMP)
        return gp
    }

    /** Horizontal "cylinder" shading for a vertical limb centred on [xc]: lit from the upper left. */
    private fun cyl(xc: Float, hw: Float, base: Int): Paint =
        linN(xc - hw, 0f, xc + hw, 0f,
            intArrayOf(shade(base, 0.55f), lighten(base, 0.22f), base, shade(base, 0.58f)),
            floatArrayOf(0f, 0.28f, 0.6f, 1f))

    private fun poly(vararg v: Float): Path {
        path.reset()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
        path.close()
        return path
    }

    /** Tapered limb: width [w0] at (x0,y0) narrowing to [w1] at (x1,y1), with rounded ends. */
    private fun drawTaper(c: Canvas, x0: Float, y0: Float, w0: Float, x1: Float, y1: Float, w1: Float, p: Paint) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = -dy / len
        val ny = dx / len
        tpath.reset()
        tpath.moveTo(x0 + nx * w0 / 2f, y0 + ny * w0 / 2f)
        tpath.lineTo(x1 + nx * w1 / 2f, y1 + ny * w1 / 2f)
        tpath.lineTo(x1 - nx * w1 / 2f, y1 - ny * w1 / 2f)
        tpath.lineTo(x0 - nx * w0 / 2f, y0 - ny * w0 / 2f)
        tpath.close()
        c.drawPath(tpath, p)
        c.drawCircle(x0, y0, w0 / 2f, p)
        c.drawCircle(x1, y1, w1 / 2f, p)
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
        c.drawRect(rect, lin(cx - r, cy - r, cx + r, cy + r, 0x33FFFFFF, 0x44000000))
        c.restore()
    }

    // ------------------------------------------------------------------ crest

    /**
     * Crest centred on (cx, cy) with radius [r] (in whatever units the canvas is currently in).
     * Gradient-filled with an embossed emblem; on a jersey the caller's shading overlay then
     * wraps it around the chest.
     */
    fun drawCrest(c: Canvas, cx: Float, cy: Float, r: Float, st: TeamStyle, abbr: String) {
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
        c.drawPath(frame(1f), lin(cx - r, cy - r, cx + r, cy + r, lighten(st.crestOutline, 0.35f), shade(st.crestOutline, 0.7f)))
        c.drawPath(frame(0.86f), rad(cx - r * 0.3f, cy - r * 0.35f, r * 1.3f, lighten(st.crestBg, 0.3f), shade(st.crestBg, 0.75f)))
        val ey = cy + if (st.frame == CrestFrame.SHIELD) r * 0.05f else 0f
        drawEmblem(c, cx + r * 0.03f, ey + r * 0.05f, r * 0.5f, st, abbr, 0x66000000)
        drawEmblem(c, cx, ey, r * 0.5f, st, abbr, st.crestFg)
    }

    private fun drawEmblem(c: Canvas, cx: Float, cy: Float, k: Float, st: TeamStyle, abbr: String, fg: Int) {
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

    // ------------------------------------------------------------------ cached bitmaps

    /** Renders the skater once into a bitmap [hPx] tall for the figure (box is [BOX_W] x [BOX_H] units). */
    fun skaterBitmap(st: TeamStyle, abbr: String, hPx: Int): Bitmap = renderBitmap(hPx) { drawSkaterUnits(it, st, abbr) }

    fun goalieBitmap(st: TeamStyle, abbr: String, hPx: Int): Bitmap = renderBitmap(hPx) { drawGoalieUnits(it, st, abbr) }

    private fun renderBitmap(hPx: Int, body: (Canvas) -> Unit): Bitmap {
        val u = hPx / 100f
        val bmp = Bitmap.createBitmap(ceil(BOX_W * u).toInt().coerceAtLeast(1), ceil(BOX_H * u).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(bmp.width / 2f, 2f * u)
        c.scale(u, u)
        body(c)
        return bmp
    }

    // ------------------------------------------------------------------ figures

    /** Direct (uncached) draw, [h] px tall, feet on [feetY], centred on [cx]. */
    fun drawSkater(c: Canvas, cx: Float, feetY: Float, h: Float, st: TeamStyle, abbr: String) {
        c.save()
        c.translate(cx, feetY - h)
        c.scale(h / 100f, h / 100f)
        drawSkaterUnits(c, st, abbr)
        c.restore()
    }

    fun drawGoalie(c: Canvas, cx: Float, feetY: Float, h: Float, st: TeamStyle, abbr: String) {
        c.save()
        c.translate(cx, feetY - h)
        c.scale(h / 100f, h / 100f)
        drawGoalieUnits(c, st, abbr)
        c.restore()
    }

    private fun drawBoot(c: Canvas, x: Float) {
        // boot shell
        path.reset()
        path.moveTo(x - 6.2f, 88f)
        path.lineTo(x + 6.2f, 88f)
        path.cubicTo(x + 7.2f, 92f, x + 7.4f, 96f, x + 6.4f, 97.6f)
        path.lineTo(x - 6.4f, 97.6f)
        path.cubicTo(x - 7.4f, 96f, x - 7.2f, 92f, x - 6.2f, 88f)
        path.close()
        c.drawPath(path, linN(x - 7f, 0f, x + 7f, 0f, intArrayOf(0xFF161A21.toInt(), 0xFF4A5565.toInt(), 0xFF1D222B.toInt(), 0xFF0C0F14.toInt()),
            floatArrayOf(0f, 0.3f, 0.65f, 1f)))
        // toe cap highlight and laces
        rect.set(x - 4.8f, 93.2f, x + 4.8f, 97.4f)
        c.drawOval(rect, lin(x, 93f, x, 97.5f, 0x55FFFFFF, NONE))
        c.drawLine(x - 3f, 90.2f, x + 3f, 90.2f, stroke(0x66FFFFFF, 0.5f))
        c.drawLine(x - 3f, 91.8f, x + 3f, 91.8f, stroke(0x66FFFFFF, 0.5f))
        // holder + runner
        c.drawRect(x - 5.2f, 97.5f, x + 5.2f, 98.7f, lin(x, 97.5f, x, 98.7f, 0xFFFFFFFF.toInt(), 0xFFB5BCC6.toInt()))
        c.drawRect(x - 8f, 98.7f, x + 8f, 99.9f, lin(x, 98.7f, x, 99.9f, 0xFFF4F7FA.toInt(), 0xFF7E8894.toInt()))
    }

    private fun drawGlove(c: Canvas, gx: Float, gy: Float, st: TeamStyle, s: Int) {
        val base = shade(st.secondary, 0.88f)
        path.reset()
        path.moveTo(gx - 4.2f, gy - 3f)
        path.cubicTo(gx - 5.4f, gy + 2f, gx - 4.4f, gy + 7f, gx - 1.5f, gy + 8.4f)
        path.cubicTo(gx + 1.5f, gy + 9.2f, gx + 5f, gy + 6.5f, gx + 4.6f, gy + 1f)
        path.cubicTo(gx + 4.3f, gy - 2.5f, gx + 2f, gy - 4f, gx - 1f, gy - 4f)
        path.close()
        c.drawPath(path, rad(gx - 1.6f, gy - 1.2f, 8f, lighten(base, 0.35f), shade(base, 0.5f)))
        c.drawLine(gx - 1.8f, gy + 2f, gx - 1.6f, gy + 7.2f, stroke(0x55000000, 0.5f))
        c.drawLine(gx + 0.6f, gy + 2f, gx + 0.8f, gy + 7.6f, stroke(0x55000000, 0.5f))
        c.drawLine(gx - 4f, gy - 2.4f, gx + 3.6f, gy - 2.4f, stroke(st.secondary, 1.4f))
        if (s != 0) {
            // thumb
            rect.set(gx - 5.6f * s - 1.2f, gy - 0.5f, gx - 5.6f * s + 1.2f, gy + 3.5f)
            c.drawOval(rect, color(shade(base, 0.8f)))
        }
    }

    private fun drawHead(c: Canvas, st: TeamStyle) {
        // neck
        rect.set(-3.6f, 18f, 3.6f, 24.5f)
        c.drawRect(rect, lin(-3.6f, 0f, 3.6f, 0f, shade(SKIN, 0.8f), shade(SKIN, 0.55f)))
        // ear flaps
        for (s in intArrayOf(-1, 1)) {
            rect.set(s * 10.6f - 2.3f, 8.2f, s * 10.6f + 2.3f, 16.4f)
            c.drawOval(rect, lin(s * 8.3f, 0f, s * 12.9f, 0f, shade(st.helmet, 0.55f), shade(st.helmet, 0.8f)))
        }
        // face
        rect.set(-7f, 7f, 7f, 20.8f)
        c.drawRoundRect(rect, 5.2f, 5.2f, rad(-2f, 12f, 12f, lighten(SKIN, 0.18f), shade(SKIN, 0.68f)))
        // eyes and brow shadow
        c.drawRect(-6.5f, 10.4f, 6.5f, 12.4f, color(0x2A000000))
        c.drawCircle(-2.7f, 13.6f, 0.75f, color(DARK))
        c.drawCircle(2.7f, 13.6f, 0.75f, color(DARK))
        // helmet dome
        path.reset()
        path.moveTo(-10.9f, 14.5f)
        path.cubicTo(-12.4f, -3f, 12.4f, -3f, 10.9f, 14.5f)
        path.lineTo(7.8f, 11f)
        path.quadTo(0f, 8.6f, -7.8f, 11f)
        path.close()
        val h = st.helmet
        c.drawPath(path, radN(-4.2f, 2.6f, 16f, intArrayOf(lighten(h, 0.6f), lighten(h, 0.1f), h, shade(h, 0.45f)),
            floatArrayOf(0f, 0.3f, 0.6f, 1f)))
        // rim light on the right edge of the dome
        c.save()
        c.clipPath(path)
        c.drawPath(path, stroke(withAlpha(lighten(h, 0.6f), 150), 2.2f).also { it.style = Paint.Style.STROKE })
        c.restore()
        // front brim shadow + centre stripe
        c.drawPath(poly(-7.8f, 11f, 7.8f, 11f, 8.6f, 12.6f, -8.6f, 12.6f), color(0x33000000))
        c.drawLine(0f, 0.2f, 0f, 8.8f, stroke(st.trim, 2.2f))
        // specular spot
        c.save()
        c.translate(-4.6f, 2.8f)
        c.scale(1.5f, 1f)
        c.drawCircle(0f, 0f, 2.6f, rad(0f, 0f, 2.6f, 0xE6FFFFFF.toInt(), NONE))
        c.restore()
        // cage: curved bars with a dark underlay so they read as wire
        val under = 0x88000000.toInt()
        val wire = 0xFFE3E9EF.toInt()
        for (pass in 0..1) {
            val col = if (pass == 0) under else wire
            val w = if (pass == 0) 1.15f else 0.55f
            val off = if (pass == 0) 0.35f else 0f
            for (i in 0..2) {
                val y = 12.6f + i * 3.2f + off
                path.reset()
                path.moveTo(-7f, y)
                path.quadTo(0f, y + 1.5f, 7f, y)
                c.drawPath(path, stroke(col, w).also { it.style = Paint.Style.STROKE })
            }
            for (i in -2..2) {
                path.reset()
                path.moveTo(i * 3.1f, 11.6f)
                path.quadTo(i * 3.45f, 16f, i * 2.6f, 20.4f + off)
                c.drawPath(path, stroke(col, w).also { it.style = Paint.Style.STROKE })
            }
        }
        // chin cup
        rect.set(-4.2f, 18.6f, 4.2f, 22.4f)
        c.drawOval(rect, lin(0f, 18.6f, 0f, 22.4f, 0xFFB9C2CC.toInt(), 0xFF5B6673.toInt()))
        // visor sheen
        c.drawRoundRect(RectF(-6.4f, 11.2f, 6.4f, 19.8f), 4f, 4f, lin(-6f, 11f, 6f, 20f, 0x30FFFFFF, NONE))
    }

    private fun drawSkaterUnits(c: Canvas, st: TeamStyle, abbr: String) {
        val darkPants = shade(st.primary, 0.5f)

        // legs: boots, tapered socks with trim bands
        for (s in intArrayOf(-1, 1)) {
            val x = s * 8f
            drawBoot(c, x)
            drawTaper(c, x, 67f, 10.4f, x, 89f, 8.6f, cyl(x, 5.2f, st.sock))
            c.save()
            rect.set(x - 6f, 72.5f, x + 6f, 76f)
            c.drawRect(rect, cyl(x, 5.2f, st.trim))
            rect.set(x - 6f, 78.5f, x + 6f, 80.6f)
            c.drawRect(rect, cyl(x, 5.2f, st.trim))
            c.restore()
        }
        // pants (breezers) with a crotch notch
        path.reset()
        path.moveTo(-15.5f, 49f)
        path.quadTo(0f, 46f, 15.5f, 49f)
        path.cubicTo(17.4f, 57f, 16.8f, 65f, 14.6f, 71.5f)
        path.lineTo(1.4f, 71.5f)
        path.lineTo(0f, 61f)
        path.lineTo(-1.4f, 71.5f)
        path.lineTo(-14.6f, 71.5f)
        path.cubicTo(-16.8f, 65f, -17.4f, 57f, -15.5f, 49f)
        path.close()
        c.drawPath(path, linN(-16f, 0f, 16f, 0f,
            intArrayOf(shade(darkPants, 0.7f), lighten(darkPants, 0.25f), shade(darkPants, 0.6f), darkPants, lighten(darkPants, 0.12f), shade(darkPants, 0.55f)),
            floatArrayOf(0f, 0.16f, 0.46f, 0.54f, 0.78f, 1f)))
        for (s in intArrayOf(-1, 1)) c.drawLine(s * 15.3f, 52f, s * 14.8f, 68f, stroke(st.trim, 1.6f))
        c.drawRect(-15.5f, 48.4f, 15.5f, 51f, lin(0f, 48.4f, 0f, 51f, 0x44FFFFFF, NONE))

        drawJerseyTorso(c, st, abbr, 1f)

        // arm shadows on the jersey, then tapered sleeves with cuff stripes
        for (s in intArrayOf(-1, 1)) {
            drawTaper(c, s * 15.2f, 29f, 12f, s * 20.2f, 49f, 9.8f, color(0x66000000))
        }
        for (s in intArrayOf(-1, 1)) {
            drawTaper(c, s * 17f, 28f, 11.4f, s * 21.5f, 49f, 8.8f, cyl(s * 19.2f, 6f, st.primary))
            // cuff: trim line then stripe colour
            drawTaper(c, s * 20.7f, 42.5f, 9.4f, s * 21.1f, 44f, 9.2f, cyl(s * 20.9f, 5f, st.trim))
            drawTaper(c, s * 21.1f, 45f, 9.1f, s * 21.4f, 49f, 8.8f, cyl(s * 21.2f, 5f, st.secondary))
            // shoulder cap highlight
            rect.set(s * 17f - 4.5f, 24.6f, s * 17f + 4.5f, 31f)
            c.drawOval(rect, rad(s * 17f - 1.4f, 27f, 6f, 0x55FFFFFF, NONE))
        }

        drawHead(c, st)

        // stick: tapered shaft, tape at the hands, blade and puck
        val shaft = linN(22f, 0f, 32f, 0f, intArrayOf(0xFF4A4F57.toInt(), 0xFF1A1C20.toInt()), floatArrayOf(0f, 1f))
        drawTaper(c, 23f, 55f, 1.7f, 31f, 96.4f, 1.5f, shaft)
        drawTaper(c, 22.6f, 56f, 2f, 22.9f, 62f, 2f, color(0xFFE8ECEF.toInt()))
        path.reset()
        path.moveTo(29.4f, 95.6f); path.lineTo(38.4f, 95.6f); path.lineTo(38.4f, 98.2f); path.lineTo(30.6f, 98.2f); path.close()
        c.drawPath(path, lin(0f, 95.6f, 0f, 98.2f, 0xFF3B4048.toInt(), 0xFF121316.toInt()))
        c.drawCircle(35.2f, 99f, 2.3f, color(0x55000000))
        c.drawCircle(35.2f, 98.2f, 2.3f, rad(34.4f, 97.4f, 3f, 0xFF4B515A.toInt(), 0xFF0B0B0D.toInt()))
        // gloves on top
        drawGlove(c, -21.9f, 52.2f, st, -1)
        drawGlove(c, 22.2f, 52f, st, 1)
    }

    /**
     * Jersey body + pattern + hem + crest, then the shading overlays that give it volume:
     * dark sides, shoulder and chest highlights, a rim light on the lit edge and a hem shadow.
     * For a goalie chest pass bulk > 1.
     */
    private fun drawJerseyTorso(c: Canvas, st: TeamStyle, abbr: String, bulk: Float) {
        val a = 18.5f * bulk
        path.reset()
        path.moveTo(-14f * bulk, 22f)
        path.quadTo(0f, 20.2f, 14f * bulk, 22f)
        path.cubicTo(a + 2f, 22f, a + 3f, 26f, a + 1.5f, 33f)
        path.lineTo(16.8f * bulk, 57f)
        path.quadTo(0f, 59f, -16.8f * bulk, 57f)
        path.lineTo(-a - 1.5f, 33f)
        path.cubicTo(-a - 3f, 26f, -a - 2f, 22f, -14f * bulk, 22f)
        path.close()
        clip.set(path)
        val l = -a - 3f
        val r = a + 3f
        c.drawPath(clip, lin(0f, 22f, 0f, 58f, lighten(st.primary, 0.1f), shade(st.primary, 0.82f)))
        c.save()
        c.clipPath(clip)
        drawPattern(c, l, 22f, r, 57f, st, st.pattern)
        // hem stripes
        c.drawRect(l, 51.5f, r, 54.5f, color(st.secondary))
        c.drawRect(l, 54.5f, r, 57.5f, color(st.trim))
        // crest on the chest, narrowed a little so it turns with the body
        c.save()
        c.scale(0.9f, 1f)
        drawCrest(c, 0f, 39f, 7.4f * bulk, st, abbr)
        c.restore()
        // --- shading overlays (also darken the crest and pattern near the sides)
        c.drawRect(l, 22f, r, 58f, linN(l, 0f, r, 0f,
            intArrayOf(0xB8000000.toInt(), 0x30000000, NONE, NONE, 0x40000000, 0xA0000000.toInt()),
            floatArrayOf(0f, 0.16f, 0.34f, 0.62f, 0.84f, 1f)))
        // shoulder yoke highlight, chest highlight, belly falloff
        c.drawRect(l, 22f, r, 34f, lin(0f, 22f, 0f, 34f, 0x58FFFFFF, NONE))
        c.drawCircle(-5f, 33f, 15f, rad(-5f, 33f, 15f, 0x34FFFFFF, NONE))
        c.drawRect(l, 46f, r, 58f, lin(0f, 46f, 0f, 58f, NONE, 0x66000000))
        // rim light: thin bright stroke along the right (lit-from-behind) edge
        c.save()
        c.clipRect(a - 3f, 22f, r + 2f, 58f)
        c.drawPath(clip, stroke(withAlpha(lighten(st.primary, 0.6f), 150), 2.6f).also { it.style = Paint.Style.STROKE })
        c.restore()
        c.restore()
        // collar
        c.drawPath(poly(-5.8f, 21.4f, 0f, 28.4f, 5.8f, 21.4f), lin(0f, 21f, 0f, 28f, shade(st.primary, 0.3f), shade(st.primary, 0.6f)))
        c.drawLine(-5.8f, 21.8f, 0f, 28.4f, stroke(st.trim, 1f))
        c.drawLine(5.8f, 21.8f, 0f, 28.4f, stroke(st.trim, 1f))
    }

    /** Front-view goalie: stacked pads, bulky chest, blocker, catcher, mask. Smaller figures sit behind. */
    private fun drawGoalieUnits(c: Canvas, st: TeamStyle, abbr: String) {
        val pad = 0xFFF1F3F6.toInt()
        // leg pads with cylinder shading, straps and stripe bands
        for (s in intArrayOf(-1, 1)) {
            val x = s * 9.8f
            rect.set(x - 7.6f, 54f, x + 7.6f, 97.5f)
            c.drawRoundRect(rect, 4.4f, 4.4f, cyl(x, 7.6f, pad))
            c.save()
            clip.reset()
            clip.addRoundRect(rect, 4.4f, 4.4f, Path.Direction.CW)
            c.clipPath(clip)
            c.drawRect(x - 8f, 60f, x + 8f, 66.5f, cyl(x, 7.6f, st.secondary))
            c.drawRect(x - 8f, 79.5f, x + 8f, 84f, cyl(x, 7.6f, st.primary))
            c.drawRect(x - 8f, 86.6f, x + 8f, 90.2f, cyl(x, 7.6f, st.primary))
            c.drawRect(x - 8f, 54f, x + 8f, 59f, lin(0f, 54f, 0f, 59f, 0x55FFFFFF, NONE))
            c.drawRect(x - 8f, 90f, x + 8f, 97.5f, lin(0f, 90f, 0f, 97.5f, NONE, 0x55000000))
            c.restore()
            c.drawLine(x - 7.4f, 72.5f, x + 7.4f, 72.5f, stroke(0x66000000, 0.7f))
            c.drawRoundRect(rect, 4.4f, 4.4f, stroke(0xFF8F98A3.toInt(), 0.7f).also { it.style = Paint.Style.STROKE })
            // skate toe
            rect.set(x - 6.4f, 96.3f, x + 6.4f, 99.8f)
            c.drawRoundRect(rect, 1.6f, 1.6f, lin(0f, 96.3f, 0f, 99.8f, 0xFF3A4350.toInt(), 0xFF0C0F14.toInt()))
        }
        // pants
        rect.set(-14.5f, 45f, 14.5f, 61f)
        c.drawRoundRect(rect, 4.5f, 4.5f, cyl(0f, 14.5f, shade(st.primary, 0.5f)))
        drawJerseyTorso(c, st, abbr, 1.12f)
        // arm shadows then sleeves
        for (s in intArrayOf(-1, 1)) drawTaper(c, s * 19f, 31f, 14f, s * 25.5f, 50f, 11.6f, color(0x66000000))
        for (s in intArrayOf(-1, 1)) {
            drawTaper(c, s * 21f, 30f, 13.4f, s * 26.6f, 50f, 10.8f, cyl(s * 23.5f, 7f, st.primary))
            drawTaper(c, s * 26f, 45f, 11f, s * 26.6f, 49f, 10.8f, cyl(s * 26.3f, 6f, st.secondary))
            rect.set(s * 21f - 5.4f, 25f, s * 21f + 5.4f, 33f)
            c.drawOval(rect, rad(s * 21f - 1.5f, 28f, 7f, 0x55FFFFFF, NONE))
        }
        // blocker (left)
        rect.set(-34.6f, 46f, -22.6f, 62f)
        c.drawRoundRect(rect, 3.4f, 3.4f, linN(-34.6f, 0f, -22.6f, 0f, intArrayOf(0xFF9CA6B2.toInt(), 0xFFF6F8FA.toInt(), 0xFFB4BCC6.toInt(), 0xFF6B7480.toInt()), floatArrayOf(0f, 0.3f, 0.65f, 1f)))
        c.drawRect(-34.6f, 46f, -22.6f, 50f, cyl(-28.6f, 6f, st.secondary))
        c.drawRoundRect(rect, 3.4f, 3.4f, stroke(0xFF6B7480.toInt(), 0.7f).also { it.style = Paint.Style.STROKE })
        // catcher (right)
        path.reset()
        path.moveTo(22.8f, 47f)
        path.cubicTo(24f, 42f, 33f, 42f, 36.2f, 48f)
        path.cubicTo(38f, 54f, 37f, 62f, 31f, 63f)
        path.cubicTo(25f, 63f, 22f, 58f, 22.8f, 47f)
        path.close()
        c.drawPath(path, rad(27f, 48f, 17f, lighten(st.secondary, 0.3f), shade(st.secondary, 0.5f)))
        c.drawPath(path, stroke(st.trim, 1.1f).also { it.style = Paint.Style.STROKE })
        path.reset(); path.moveTo(25.5f, 54f); path.quadTo(30f, 58.5f, 34.5f, 54f)
        c.drawPath(path, stroke(0x66000000, 0.8f).also { it.style = Paint.Style.STROKE })
        // goalie stick: shaft + wide paddle
        drawTaper(c, -28.6f, 56f, 1.8f, -28.6f, 82f, 1.8f, color(0xFF23262B.toInt()))
        rect.set(-31.6f, 80f, -25.6f, 98.6f)
        c.drawRoundRect(rect, 1.6f, 1.6f, linN(-31.6f, 0f, -25.6f, 0f, intArrayOf(0xFFE0B676.toInt(), 0xFFB98A4C.toInt(), 0xFF6C4B24.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        // mask
        rect.set(-3.8f, 18.4f, 3.8f, 24.5f)
        c.drawRect(rect, color(shade(SKIN, 0.6f)))
        rect.set(-11.6f, -1.2f, 11.6f, 22.8f)
        val h = st.helmet
        c.drawOval(rect, radN(-4.5f, 4f, 22f, intArrayOf(lighten(h, 0.55f), lighten(h, 0.08f), h, shade(h, 0.42f)), floatArrayOf(0f, 0.3f, 0.62f, 1f)))
        c.save()
        c.clipRect(7f, -2f, 13f, 24f)
        c.drawOval(rect, stroke(withAlpha(lighten(h, 0.6f), 140), 2.2f).also { it.style = Paint.Style.STROKE })
        c.restore()
        rect.set(-6.9f, 7.4f, 6.9f, 19.8f)
        c.drawRoundRect(rect, 4.2f, 4.2f, rad(0f, 13f, 9f, 0xFF2A313B.toInt(), 0xFF07090C.toInt()))
        for (pass in 0..1) {
            val col = if (pass == 0) 0x88000000.toInt() else 0xFFC9D1DA.toInt()
            val w = if (pass == 0) 1.15f else 0.6f
            for (i in -2..2) {
                path.reset(); path.moveTo(i * 2.9f, 7.8f); path.quadTo(i * 3.1f, 14f, i * 2.7f, 19.4f)
                c.drawPath(path, stroke(col, w).also { it.style = Paint.Style.STROKE })
            }
            for (i in 0..2) {
                val y = 10f + i * 3.7f
                path.reset(); path.moveTo(-6.7f, y); path.quadTo(0f, y + 1.4f, 6.7f, y)
                c.drawPath(path, stroke(col, w).also { it.style = Paint.Style.STROKE })
            }
        }
        c.drawLine(0f, 0.4f, 0f, 7f, stroke(st.secondary, 2.6f))
        c.save()
        c.translate(-5f, 3.6f)
        c.scale(1.5f, 1f)
        c.drawCircle(0f, 0f, 2.8f, rad(0f, 0f, 2.8f, 0xE6FFFFFF.toInt(), NONE))
        c.restore()
    }
}
