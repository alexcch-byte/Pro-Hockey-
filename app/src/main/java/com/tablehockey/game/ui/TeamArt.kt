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
 * Horizontal art extents: skater x in [-29, 31], goalie x in [-39, 40].
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


    // ------------------------------------------------------------------ lighting helpers

    // One key light for every figure: the spotlight is above and slightly left, so surfaces
    // facing up-left are bright, the opposite edge gets a rim, everything else falls off.
    private const val LX = -0.62f
    private const val LY = -0.78f

    /** Gradient across a limb (axis a->b, thickness w) that always brightens the up-left side. */
    private fun lit(ax: Float, ay: Float, bx: Float, by: Float, w: Float, base: Int): Paint {
        val dx = bx - ax
        val dy = by - ay
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        var nx = -dy / len
        var ny = dx / len
        if (nx * LX + ny * LY < 0f) { nx = -nx; ny = -ny }
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
        val h = w / 2f
        return linN(mx + nx * h, my + ny * h, mx - nx * h, my - ny * h,
            intArrayOf(lighten(base, 0.34f), lighten(base, 0.08f), base, shade(base, 0.52f), lighten(shade(base, 0.6f), 0.2f)),
            floatArrayOf(0f, 0.22f, 0.5f, 0.88f, 1f))
    }

    private fun limb(c: Canvas, ax: Float, ay: Float, aw: Float, bx: Float, by: Float, bw: Float, base: Int) =
        drawTaper(c, ax, ay, aw, bx, by, bw, lit(ax, ay, bx, by, (aw + bw) / 2f, base))

    /** A band across a limb between fractions [t0] and [t1] of its length. */
    private fun band(c: Canvas, ax: Float, ay: Float, aw: Float, bx: Float, by: Float, bw: Float, t0: Float, t1: Float, col: Int) {
        val x0 = ax + (bx - ax) * t0; val y0 = ay + (by - ay) * t0; val w0 = aw + (bw - aw) * t0 + 0.5f
        val x1 = ax + (bx - ax) * t1; val y1 = ay + (by - ay) * t1; val w1 = aw + (bw - aw) * t1 + 0.5f
        val dx = x1 - x0
        val dy = y1 - y0
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        val nx = -dy / len
        val ny = dx / len
        c.drawPath(poly(x0 + nx * w0 / 2f, y0 + ny * w0 / 2f, x1 + nx * w1 / 2f, y1 + ny * w1 / 2f,
            x1 - nx * w1 / 2f, y1 - ny * w1 / 2f, x0 - nx * w0 / 2f, y0 - ny * w0 / 2f),
            lit((x0 + x1) / 2f, (y0 + y1) / 2f, (x0 + x1) / 2f + nx, (y0 + y1) / 2f + ny, (w0 + w1) / 2f, col))
    }

    /** Side-on skate: boot, toe cap, holder and steel runner. [x] is the ankle, [y] the sole. */
    private fun drawBootSide(c: Canvas, x: Float, y: Float, far: Boolean) {
        path.reset()
        path.moveTo(x - 5f, y - 11f)
        path.lineTo(x + 3f, y - 11f)
        path.lineTo(x + 4.2f, y - 6.5f)
        path.cubicTo(x + 9f, y - 5.8f, x + 12.5f, y - 3.5f, x + 12f, y)
        path.lineTo(x - 6f, y)
        path.lineTo(x - 6.6f, y - 5f)
        path.close()
        c.drawPath(path, linN(x - 6f, y - 11f, x + 10f, y, intArrayOf(0xFF6B7788.toInt(), 0xFF2B323D.toInt(), 0xFF0E1116.toInt()),
            floatArrayOf(0f, 0.5f, 1f)))
        if (far) c.drawPath(path, color(0x50000000))
        // toe cap sheen + laces
        c.drawPath(poly(x + 5f, y - 5f, x + 11f, y - 3f, x + 11.6f, y - 0.6f, x + 6f, y - 0.6f), lin(x, y - 5f, x, y, 0x66FFFFFF, NONE))
        c.drawLine(x - 3f, y - 9f, x + 2.4f, y - 8.4f, stroke(0x99FFFFFF.toInt(), 0.5f))
        c.drawLine(x - 3.2f, y - 7f, x + 2.8f, y - 6.4f, stroke(0x99FFFFFF.toInt(), 0.5f))
        c.drawRect(x - 5.5f, y, x + 9f, y + 1.2f, lin(x, y, x, y + 1.2f, 0xFFFFFFFF.toInt(), 0xFFB5BCC6.toInt()))
        c.drawRect(x - 9f, y + 1.2f, x + 13f, y + 2.5f, lin(x, y + 1.2f, x, y + 2.5f, 0xFFF4F7FA.toInt(), 0xFF7E8894.toInt()))
    }

    /** Glove gripping a shaft: rounded mitt, knuckle lines, thumb and a glossy sheen. */
    private fun drawMitt(c: Canvas, gx: Float, gy: Float, angleDeg: Float, st: TeamStyle, far: Boolean) {
        val base = if (far) shade(st.secondary, 0.7f) else shade(st.secondary, 0.9f)
        c.save()
        c.translate(gx, gy)
        c.rotate(angleDeg)
        path.reset()
        path.moveTo(-4.4f, -3.4f)
        path.cubicTo(-5.6f, 0.5f, -5f, 4.5f, -2f, 5.8f)
        path.cubicTo(0.8f, 6.8f, 4.6f, 5.2f, 4.9f, 1f)
        path.cubicTo(5f, -2.6f, 2.8f, -4.4f, -0.8f, -4.4f)
        path.close()
        c.drawPath(path, rad(-1.8f, -2f, 8.5f, lighten(base, 0.42f), shade(base, 0.5f)))
        c.drawLine(-2.6f, 1.2f, -2.4f, 5.4f, stroke(0x55000000, 0.5f))
        c.drawLine(0f, 1.4f, 0.3f, 5.8f, stroke(0x55000000, 0.5f))
        c.drawLine(2.5f, 1.2f, 2.8f, 5.2f, stroke(0x55000000, 0.5f))
        c.drawLine(-4.3f, -2.6f, 3.2f, -3.2f, stroke(st.secondary, 1.5f))
        rect.set(-3.6f, -3.6f, 0.2f, -1.6f)
        c.drawOval(rect, color(0x88FFFFFF.toInt()))
        c.restore()
    }

    // ------------------------------------------------------------------ skater (3/4 view)

    /**
     * Skater turned three quarters to the right in a crouch, both hands on a stick that runs
     * diagonally across the body. Far-side limbs are darker and partly hidden behind the torso.
     * Art extents: x in [-29, 31].
     */
    private fun drawSkaterUnits(c: Canvas, st: TeamStyle, abbr: String) {
        c.save()
        c.translate(-13f, 0f)
        val pants = shade(st.primary, 0.5f)
        val pantsFar = shade(st.primary, 0.36f)
        val sockFar = shade(st.sock, 0.72f)

        // ---- far (back) leg: skate, shin, thigh
        drawBootSide(c, -5f, 94.5f, true)
        limb(c, -4f, 74f, 9f, -5f, 88f, 6.8f, sockFar)
        band(c, -4f, 74f, 9f, -5f, 88f, 6.8f, 0.36f, 0.5f, shade(st.trim, 0.72f))
        band(c, -4f, 74f, 9f, -5f, 88f, 6.8f, 0.66f, 0.76f, shade(st.trim, 0.72f))
        limb(c, -1f, 58f, 14f, -4f, 74f, 11f, pantsFar)

        // ---- near (front) leg: bent knee out in front
        drawBootSide(c, 11f, 94.5f, false)
        limb(c, 14f, 73f, 10f, 11.5f, 88f, 7.4f, st.sock)
        band(c, 14f, 73f, 10f, 11.5f, 88f, 7.4f, 0.36f, 0.5f, st.trim)
        band(c, 14f, 73f, 10f, 11.5f, 88f, 7.4f, 0.66f, 0.76f, st.trim)
        // hips and thigh
        path.reset()
        path.moveTo(-9f, 51f)
        path.quadTo(2f, 48.5f, 12f, 51f)
        path.cubicTo(15f, 56f, 15.5f, 62f, 12f, 66f)
        path.lineTo(-6f, 66f)
        path.cubicTo(-10.5f, 62f, -11f, 56f, -9f, 51f)
        path.close()
        c.drawPath(path, lit(-9f, 51f, 12f, 66f, 20f, pants))
        limb(c, 5f, 59f, 15f, 14f, 73f, 11.5f, pants)
        c.drawCircle(14f, 73f, 5.2f, rad(12f, 71f, 6f, lighten(pants, 0.35f), shade(pants, 0.55f)))
        c.drawLine(10f, 60f, 15f, 70f, stroke(st.trim, 1.5f))

        // ---- far arm (behind the torso): shoulder -> elbow -> lower hand on the shaft
        limb(c, 12f, 30f, 10f, 18f, 45f, 8.6f, shade(st.primary, 0.78f))
        limb(c, 18f, 45f, 8.6f, 13.5f, 62f, 7f, shade(st.primary, 0.78f))
        band(c, 18f, 45f, 8.6f, 13.5f, 62f, 7f, 0.62f, 0.8f, shade(st.secondary, 0.78f))

        drawTorso3q(c, st, abbr)

        // ---- near arm: shoulder -> elbow (bent back) -> top hand on the shaft
        limb(c, -8f, 31f, 11f, -12f, 45f, 9.2f, st.primary)
        limb(c, -12f, 45f, 9.2f, 4f, 49.5f, 7.4f, st.primary)
        band(c, -12f, 45f, 9.2f, 4f, 49.5f, 7.4f, 0.64f, 0.78f, st.trim)
        band(c, -12f, 45f, 9.2f, 4f, 49.5f, 7.4f, 0.78f, 0.95f, st.secondary)
        rect.set(-13.5f, 25.5f, -3f, 33.5f)
        c.drawOval(rect, rad(-10f, 28f, 7f, 0x66FFFFFF, NONE))

        drawHead3q(c, st)

        // ---- stick: butt end above the top hand, shaft crossing the body, blade on the ice
        val sx0 = 1f; val sy0 = 43f
        val sx1 = 31.5f; val sy1 = 96.5f
        limb(c, sx0, sy0, 2.1f, sx1, sy1, 1.8f, 0xFF3A3F47.toInt())
        drawTaper(c, sx0, sy0, 2.5f, 3f, 46.5f, 2.5f, color(0xFFF1F4F6.toInt()))
        path.reset()
        path.moveTo(29.8f, 94.6f); path.lineTo(43f, 95.2f); path.lineTo(43.2f, 98.4f); path.lineTo(31f, 98.6f); path.close()
        c.drawPath(path, lin(0f, 94.6f, 0f, 98.6f, 0xFF4C525B.toInt(), 0xFF0F1114.toInt()))
        c.drawLine(31f, 95.2f, 42.6f, 95.7f, stroke(0x66FFFFFF, 0.6f))
        c.drawCircle(40f, 99f, 2.2f, color(0x55000000))
        c.drawCircle(40f, 98.4f, 2.2f, rad(39.2f, 97.6f, 3f, 0xFF4B515A.toInt(), 0xFF0B0B0D.toInt()))

        // ---- hands on top so they visibly grip the shaft
        drawMitt(c, 13.5f, 63f, -32f, st, true)
        drawMitt(c, 4.2f, 49.5f, -32f, st, false)
        c.restore()
    }

    /** Jersey with a three-quarter chest: narrowed, leaning, crest squeezed toward the front edge. */
    private fun drawTorso3q(c: Canvas, st: TeamStyle, abbr: String) {
        c.save()
        c.translate(2f, 42f)
        c.rotate(-7f)
        path.reset()
        path.moveTo(-10f, -16f)
        path.quadTo(0f, -18f, 11f, -16f)
        path.cubicTo(14.5f, -14f, 14f, -6f, 12.5f, 16f)
        path.quadTo(1f, 18.5f, -10.5f, 16f)
        path.cubicTo(-12.5f, -4f, -13.5f, -13f, -10f, -16f)
        path.close()
        clip.set(path)
        c.drawPath(clip, lin(0f, -16f, 0f, 17f, lighten(st.primary, 0.1f), shade(st.primary, 0.82f)))
        c.save()
        c.clipPath(clip)
        drawPattern(c, -16f, -16f, 16f, 17f, st, st.pattern)
        c.drawRect(-16f, 9.5f, 16f, 13f, color(st.secondary))
        c.drawRect(-16f, 13f, 16f, 17f, color(st.trim))
        // crest turned toward the viewer's right (front of the chest)
        c.save()
        c.translate(4.5f, -3f)
        c.scale(0.62f, 1f)
        drawCrest(c, 0f, 0f, 6.8f, st, abbr)
        c.restore()
        // key light from the upper left: bright left, falloff, dark right
        c.drawRect(-16f, -17f, 16f, 18f, linN(-16f, 0f, 16f, 0f,
            intArrayOf(0x30FFFFFF, 0x10FFFFFF, NONE, 0x55000000, 0xB0000000.toInt()),
            floatArrayOf(0f, 0.2f, 0.45f, 0.78f, 1f)))
        c.drawRect(-16f, -17f, 16f, -4f, lin(0f, -17f, 0f, -4f, 0x50FFFFFF, NONE))
        c.drawCircle(-5f, -6f, 12f, rad(-5f, -6f, 12f, 0x38FFFFFF, NONE))
        c.drawRect(-16f, 4f, 16f, 18f, lin(0f, 4f, 0f, 18f, NONE, 0x66000000))
        // rim light on the edge opposite the key
        c.save()
        c.clipRect(8f, -17f, 16f, 18f)
        c.drawPath(clip, stroke(withAlpha(lighten(st.primary, 0.65f), 100), 2.2f))
        c.restore()
        c.restore()
        // collar, turned to the right
        c.drawPath(poly(-2f, -16.4f, 4.5f, -9.5f, 9.5f, -16.4f), lin(0f, -16f, 0f, -9f, shade(st.primary, 0.3f), shade(st.primary, 0.6f)))
        c.drawLine(-2f, -16f, 4.5f, -9.5f, stroke(st.trim, 1f))
        c.drawLine(9.5f, -16f, 4.5f, -9.5f, stroke(st.trim, 1f))
        c.restore()
    }

    /** Head turned three quarters right: helmet dome with specular, visible ear flap, curved cage. */
    private fun drawHead3q(c: Canvas, st: TeamStyle) {
        val h = st.helmet
        // neck
        limb(c, 5f, 23f, 5.5f, 5f, 27f, 6.5f, shade(SKIN, 0.55f))
        // ear flap on the near side
        rect.set(-3.8f, 9f, 1.2f, 18f)
        c.drawOval(rect, lit(-3f, 9f, -1f, 18f, 5f, shade(h, 0.7f)))
        // face
        rect.set(2.5f, 7.5f, 15.5f, 22f)
        c.drawRoundRect(rect, 6f, 6.5f, rad(6f, 12f, 14f, lighten(SKIN, 0.2f), shade(SKIN, 0.62f)))
        c.drawRect(3f, 10.8f, 15f, 12.6f, color(0x2A000000))
        c.drawCircle(8f, 14.4f, 0.8f, color(DARK))
        c.drawCircle(12.6f, 14.4f, 0.6f, color(DARK))
        // helmet dome
        path.reset()
        path.moveTo(-5.5f, 16f)
        path.cubicTo(-8f, -5f, 18f, -5f, 16.8f, 13.5f)
        path.lineTo(14.2f, 11.4f)
        path.quadTo(6f, 9f, -1.5f, 11.8f)
        path.close()
        c.drawPath(path, radN(-0.5f, 3f, 20f, intArrayOf(lighten(h, 0.62f), lighten(h, 0.12f), h, shade(h, 0.42f)),
            floatArrayOf(0f, 0.28f, 0.58f, 1f)))
        c.save()
        c.clipPath(path)
        c.clipRect(11.5f, -6f, 20f, 20f)
        c.drawPath(path, stroke(withAlpha(lighten(h, 0.7f), 170), 2.4f))
        c.restore()
        // brim shadow and centre stripe following the curve of the dome
        c.drawPath(poly(-1.5f, 11.8f, 14.2f, 11.4f, 15.2f, 13.2f, -2.2f, 13.6f), color(0x33000000))
        path.reset(); path.moveTo(6.5f, -0.6f); path.quadTo(9f, 4f, 8.6f, 10.6f)
        c.drawPath(path, stroke(st.trim, 2.1f))
        // specular spot
        c.save()
        c.translate(0.2f, 2.6f)
        c.rotate(-25f)
        c.scale(1.7f, 1f)
        c.drawCircle(0f, 0f, 2.5f, rad(0f, 0f, 2.5f, 0xEEFFFFFF.toInt(), NONE))
        c.restore()
        // cage: curved bars bulging toward the viewer's right
        for (pass in 0..1) {
            val col = if (pass == 0) 0x88000000.toInt() else 0xFFE6ECF2.toInt()
            val w = if (pass == 0) 1.15f else 0.55f
            val off = if (pass == 0) 0.35f else 0f
            for (i in 0..2) {
                val y = 13.2f + i * 3.2f + off
                path.reset(); path.moveTo(3f, y); path.quadTo(10f, y + 1.6f, 16f, y - 0.2f)
                c.drawPath(path, stroke(col, w))
            }
            for (i in 0..3) {
                val x = 4.2f + i * 3.4f
                path.reset(); path.moveTo(x, 11.6f); path.quadTo(x + 1.4f, 16f, x + 0.2f + off, 21.4f)
                c.drawPath(path, stroke(col, w))
            }
        }
        rect.set(5f, 19.6f, 14.5f, 23.2f)
        c.drawOval(rect, lin(0f, 19.6f, 0f, 23.2f, 0xFFBFC8D2.toInt(), 0xFF566170.toInt()))
        c.drawRoundRect(RectF(3.6f, 11.6f, 15.4f, 20.4f), 4f, 4f, lin(4f, 11f, 15f, 20f, 0x30FFFFFF, NONE))
    }

    // ------------------------------------------------------------------ goalie

    /**
     * Goalie in a ready stance: bulky chest and arms, leaning slightly, stick in the blocker
     * hand, catcher on the other arm, decorated mask. Art extents: x in [-39, 40].
     */
    private fun drawGoalieUnits(c: Canvas, st: TeamStyle, abbr: String) {
        val pad = 0xFFF1F3F6.toInt()
        c.save()
        c.rotate(-3.5f, 0f, 100f)

        // leg pads, each tilted slightly outward, lit across their axis
        for (s in intArrayOf(-1, 1)) {
            val x = s * 10.5f
            c.save()
            c.rotate(s * 3.2f, x, 56f)
            path.reset()
            path.moveTo(x - 7.4f, 55f); path.lineTo(x + 7.4f, 55f)
            path.cubicTo(x + 8.6f, 70f, x + 8.8f, 85f, x + 8f, 97.5f)
            path.lineTo(x - 8f, 97.5f)
            path.cubicTo(x - 8.8f, 85f, x - 8.6f, 70f, x - 7.4f, 55f)
            path.close()
            clip.set(path)
            c.drawPath(clip, lit(x, 55f, x, 97f, 17f, pad))
            c.save()
            c.clipPath(clip)
            c.drawRect(x - 10f, 60f, x + 10f, 66.5f, lit(x, 63f, x + 1f, 63.5f, 17f, st.secondary))
            c.drawRect(x - 10f, 80f, x + 10f, 84.2f, lit(x, 82f, x + 1f, 82.5f, 17f, st.primary))
            c.drawRect(x - 10f, 87f, x + 10f, 90.4f, lit(x, 88f, x + 1f, 88.5f, 17f, st.primary))
            c.drawRect(x - 10f, 55f, x + 10f, 59.5f, lin(0f, 55f, 0f, 59.5f, 0x66FFFFFF, NONE))
            c.drawRect(x - 10f, 90f, x + 10f, 98f, lin(0f, 90f, 0f, 98f, NONE, 0x66000000))
            c.restore()
            c.drawLine(x - 7.8f, 72.5f, x + 7.8f, 72.5f, stroke(0x66000000, 0.7f))
            c.drawLine(x - 8f, 76f, x + 8f, 76f, stroke(0x33000000, 0.6f))
            c.drawPath(clip, stroke(0xFF8F98A3.toInt(), 0.7f))
            rect.set(x - 7.4f, 96.4f, x + 7.4f, 99.8f)
            c.drawRoundRect(rect, 1.6f, 1.6f, lin(0f, 96.4f, 0f, 99.8f, 0xFF3A4350.toInt(), 0xFF0C0F14.toInt()))
            c.restore()
        }
        // pants
        rect.set(-16f, 46f, 16f, 61f)
        c.drawRoundRect(rect, 5f, 5f, lit(-16f, 46f, 16f, 61f, 24f, shade(st.primary, 0.5f)))

        // goalie stick: shaft from the blocker hand to a wide paddle on the ice
        limb(c, -31f, 56f, 2.2f, -27f, 82f, 2.2f, 0xFF30343A.toInt())
        path.reset()
        path.moveTo(-30.8f, 79f); path.lineTo(-23.4f, 79f); path.lineTo(-22.6f, 98.6f); path.lineTo(-31.8f, 98.6f); path.close()
        c.drawPath(path, linN(-31.8f, 0f, -22.6f, 0f, intArrayOf(0xFFEBC384.toInt(), 0xFFB98A4C.toInt(), 0xFF6C4B24.toInt()), floatArrayOf(0f, 0.5f, 1f)))
        c.drawRect(-22.8f, 96.4f, -9f, 98.8f, lin(0f, 96.4f, 0f, 98.8f, 0xFFC99A5B.toInt(), 0xFF5A3F1E.toInt()))

        // arms (thick, tapered) behind the chest guard so the guard overlaps the shoulders
        limb(c, 21f, 31f, 14f, 30f, 45f, 11.5f, shade(st.primary, 0.85f))
        limb(c, 30f, 45f, 11.5f, 31f, 55f, 9f, shade(st.primary, 0.85f))
        limb(c, -21f, 31f, 14f, -29f, 45f, 11.5f, st.primary)
        limb(c, -29f, 45f, 11.5f, -31f, 55f, 9f, st.primary)
        band(c, -29f, 45f, 11.5f, -31f, 55f, 9f, 0.5f, 0.75f, st.secondary)
        band(c, 30f, 45f, 11.5f, 31f, 55f, 9f, 0.5f, 0.75f, shade(st.secondary, 0.85f))

        drawJerseyTorso(c, st, abbr, 1.22f)
        rect.set(-27f, 25f, -14f, 35f)
        c.drawOval(rect, rad(-21f, 29f, 8f, 0x66FFFFFF, NONE))

        // blocker (on the stick hand) and catcher, rotated with the forearms
        c.save()
        c.translate(-31.5f, 56.5f)
        c.rotate(-7f)
        rect.set(-6.6f, -8.5f, 6.6f, 8.5f)
        c.drawRoundRect(rect, 3.4f, 3.4f, linN(-6.6f, -8f, 6.6f, 8f, intArrayOf(0xFFF8FAFC.toInt(), 0xFFC3CBD5.toInt(), 0xFF6B7480.toInt()), floatArrayOf(0f, 0.55f, 1f)))
        c.drawRect(-6.6f, -8.5f, 6.6f, -4.5f, lit(0f, -6.5f, 1f, -6f, 13f, st.secondary))
        c.drawRect(-3.5f, -1f, 3.5f, 6f, color(0x22000000))
        c.drawRoundRect(rect, 3.4f, 3.4f, stroke(0xFF6B7480.toInt(), 0.7f))
        rect.set(-4.8f, -7f, -1f, -3f)
        c.drawOval(rect, color(0x88FFFFFF.toInt()))
        c.restore()

        c.save()
        c.translate(32.5f, 56f)
        c.rotate(7f)
        path.reset()
        path.moveTo(-6.4f, -8f)
        path.cubicTo(-5.5f, -13.5f, 4.5f, -13.5f, 7f, -7f)
        path.cubicTo(9f, 0f, 8f, 8f, 1.5f, 9f)
        path.cubicTo(-4.5f, 9f, -7.4f, 4f, -6.4f, -8f)
        path.close()
        c.drawPath(path, rad(-2f, -6f, 17f, lighten(st.secondary, 0.38f), shade(st.secondary, 0.48f)))
        c.drawPath(path, stroke(st.trim, 1.1f))
        c.drawLine(-3.5f, 0f, 6f, 0f, stroke(0x66000000, 0.8f))
        rect.set(-4f, -9.5f, 0.6f, -5.2f)
        c.drawOval(rect, color(0x88FFFFFF.toInt()))
        c.restore()

        drawGoalieMask(c, st, abbr)
        c.restore()
    }

    /** Throat guard plus a mask decorated with side stripes, a forehead crest and a lit dome. */
    private fun drawGoalieMask(c: Canvas, st: TeamStyle, abbr: String) {
        val h = st.helmet
        c.save()
        c.rotate(3.5f, 0f, 22f)
        // throat guard
        rect.set(-5.2f, 20f, 5.2f, 27f)
        c.drawRoundRect(rect, 2.4f, 2.4f, lit(0f, 20f, 1f, 27f, 10f, shade(h, 0.6f)))
        rect.set(-3.8f, 18.4f, 3.8f, 24.5f)
        c.drawRect(rect, color(shade(SKIN, 0.6f)))
        // mask shell
        rect.set(-12f, -1.4f, 12f, 22.8f)
        c.drawOval(rect, radN(-5.5f, 3.5f, 24f, intArrayOf(lighten(h, 0.6f), lighten(h, 0.1f), h, shade(h, 0.4f)), floatArrayOf(0f, 0.3f, 0.6f, 1f)))
        // decoration: two side stripes in the secondary colour and a thin trim line
        c.save()
        clip.reset(); clip.addOval(rect, Path.Direction.CW)
        c.clipPath(clip)
        for (s in intArrayOf(-1, 1)) {
            path.reset()
            path.moveTo(s * 5.5f, -2f); path.quadTo(s * 12.5f, 8f, s * 7.5f, 24f)
            c.drawPath(path, stroke(st.secondary, 3.2f))
            path.reset()
            path.moveTo(s * 7.6f, -2f); path.quadTo(s * 14.6f, 8f, s * 9.6f, 24f)
            c.drawPath(path, stroke(st.trim, 0.9f))
        }
        c.restore()
        c.save()
        c.translate(0f, 3.2f)
        c.scale(0.9f, 1f)
        drawCrest(c, 0f, 0f, 2.9f, st, abbr)
        c.restore()
        // rim light on the right edge
        c.save()
        c.clipRect(7.5f, -3f, 14f, 25f)
        c.drawOval(RectF(-12f, -1.4f, 12f, 22.8f), stroke(withAlpha(lighten(h, 0.7f), 150), 2.2f))
        c.restore()
        // face opening and cage
        rect.set(-6.9f, 8f, 6.9f, 20.2f)
        c.drawRoundRect(rect, 4.2f, 4.2f, rad(0f, 13.5f, 9f, 0xFF2A313B.toInt(), 0xFF07090C.toInt()))
        for (pass in 0..1) {
            val col = if (pass == 0) 0x88000000.toInt() else 0xFFC9D1DA.toInt()
            val w = if (pass == 0) 1.15f else 0.6f
            for (i in -2..2) {
                path.reset(); path.moveTo(i * 2.9f, 8.3f); path.quadTo(i * 3.2f, 14.5f, i * 2.7f, 19.8f)
                c.drawPath(path, stroke(col, w))
            }
            for (i in 0..2) {
                val y = 10.5f + i * 3.6f
                path.reset(); path.moveTo(-6.7f, y); path.quadTo(0f, y + 1.5f, 6.7f, y)
                c.drawPath(path, stroke(col, w))
            }
        }
        c.save()
        c.translate(-6.5f, 3.4f)
        c.rotate(-30f)
        c.scale(1.6f, 1f)
        c.drawCircle(0f, 0f, 2.8f, rad(0f, 0f, 2.8f, 0xEEFFFFFF.toInt(), NONE))
        c.restore()
        c.restore()
    }

    /**
     * Jersey body for the goalie chest guard (bulk > 1 widens it): pattern, hem, crest, then the
     * same key-light overlays as the skater (bright left, falloff, rim on the right edge).
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
        c.drawRect(l, 51.5f, r, 54.5f, color(st.secondary))
        c.drawRect(l, 54.5f, r, 57.5f, color(st.trim))
        c.save()
        c.scale(0.92f, 1f)
        drawCrest(c, 1f, 39f, 7.4f * bulk, st, abbr)
        c.restore()
        c.drawRect(l, 22f, r, 58f, linN(l, 0f, r, 0f,
            intArrayOf(0x30FFFFFF, 0x10FFFFFF, NONE, 0x55000000, 0xB0000000.toInt()),
            floatArrayOf(0f, 0.2f, 0.45f, 0.78f, 1f)))
        c.drawRect(l, 22f, r, 34f, lin(0f, 22f, 0f, 34f, 0x50FFFFFF, NONE))
        c.drawCircle(-6f, 33f, 16f, rad(-6f, 33f, 16f, 0x38FFFFFF, NONE))
        c.drawRect(l, 46f, r, 58f, lin(0f, 46f, 0f, 58f, NONE, 0x66000000))
        c.save()
        c.clipRect(a - 3f, 22f, r + 2f, 58f)
        c.drawPath(clip, stroke(withAlpha(lighten(st.primary, 0.65f), 170), 2.6f))
        c.restore()
        c.restore()
        c.drawPath(poly(-5.8f, 21.4f, 0f, 28.4f, 5.8f, 21.4f), lin(0f, 21f, 0f, 28f, shade(st.primary, 0.3f), shade(st.primary, 0.6f)))
        c.drawLine(-5.8f, 21.8f, 0f, 28.4f, stroke(st.trim, 1f))
        c.drawLine(5.8f, 21.8f, 0f, 28.4f, stroke(st.trim, 1f))
    }
}
