package com.tablehockey.game.game

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Extra world art for the indoor arena, kept out of [Renderer] so that file stays
 * mergeable: a pre-baked ice overlay (lighting, reflections, scuffs, edge shadow),
 * a denser and more varied crowd bitmap, board advert panels, glass highlights,
 * extra rink markings and a referee.
 *
 * Everything here is read-only with respect to the world. The referee's position is
 * renderer-side animation state (it follows the puck), not simulation state.
 * Bitmaps and paths are built once; the per-frame draw functions do not allocate.
 */
class WorldArt {

    companion object {
        private const val ICE_PX = 4f
        private const val STAND_PX = 6f
        private const val RAKE_H = 20f
        private const val SIDE_SPAN = 120f
        private const val RAKE_D = 26f
    }

    private val tmpRect = RectF()
    private val signs = floatArrayOf(-1f, 1f)

    // ------------------------------------------------------------------ ice overlay

    /** Soft lighting, reflections and scuffs covering the whole rink; draw it clipped to the rink. */
    val iceOverlay: Bitmap by lazy { buildIceOverlay() }
    val iceRect = RectF(-Rink.HALF_L, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
    val iceOverlayPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun buildIceOverlay(): Bitmap {
        val bw = (Rink.LENGTH * ICE_PX).toInt()
        val bh = (Rink.WIDTH * ICE_PX).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(ICE_PX, ICE_PX)
        c.translate(Rink.HALF_L, Rink.HALF_W)
        val rr = Path()
        rr.addRoundRect(RectF(-Rink.HALF_L, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W), Rink.CORNER_R, Rink.CORNER_R, Path.Direction.CW)
        c.clipPath(rr)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng = Random(31)

        // Cool shading toward the long boards, a touch lighter down the middle.
        p.shader = LinearGradient(
            0f, -Rink.HALF_W, 0f, Rink.HALF_W,
            intArrayOf(Color.argb(70, 70, 110, 165), Color.argb(0, 70, 110, 165), Color.argb(0, 255, 255, 255), Color.argb(55, 70, 110, 165)),
            floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(-Rink.HALF_L, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W, p)
        p.shader = null

        // Arena light reflections: flattened soft hot-spots in a row.
        p.shader = RadialGradient(0f, 0f, 26f, Color.argb(150, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        for (lx in floatArrayOf(-72f, -36f, 0f, 36f, 72f)) {
            for (ly in floatArrayOf(-14f, 15f)) {
                c.save()
                c.translate(lx, ly)
                c.scale(1f, 0.42f)
                c.drawCircle(0f, 0f, 26f, p)
                c.restore()
            }
        }
        p.shader = null

        // Soft specular sheen bands across the ice, like the arena lights on a fresh surface.
        for (band in 0..1) {
            c.save()
            c.rotate(-16f + band * 10f)
            p.shader = LinearGradient(0f, -9f, 0f, 9f, intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.translate(0f, -14f + band * 30f)
            c.drawRect(-260f, -9f, 260f, 9f, p)
            p.shader = null
            c.restore()
        }
        // Long glassy streaks left by the resurfacer.
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 48) {
            val x = (rng.nextFloat() - 0.5f) * 2f * Rink.HALF_L
            val y = (rng.nextFloat() - 0.5f) * 2f * Rink.HALF_W
            val len = 14f + rng.nextFloat() * 50f
            p.strokeWidth = 0.14f + rng.nextFloat() * 0.4f
            p.color = if (i % 3 == 0) Color.argb(10 + rng.nextInt(14), 150, 195, 235) else Color.argb(10 + rng.nextInt(18), 255, 255, 255)
            c.drawLine(x, y, x + len, y + (rng.nextFloat() - 0.5f) * 0.9f, p)
        }

        // Skate scuffs, clustered round the faceoff dots and creases where play lingers.
        val ax = floatArrayOf(0f, -Rink.END_DOT_X, Rink.END_DOT_X, -Rink.END_DOT_X, Rink.END_DOT_X, -Rink.GOAL_LINE_X + 4f, Rink.GOAL_LINE_X - 4f, -Rink.NEUTRAL_DOT_X, Rink.NEUTRAL_DOT_X)
        val ay = floatArrayOf(0f, -Rink.DOT_Y, -Rink.DOT_Y, Rink.DOT_Y, Rink.DOT_Y, 0f, 0f, Rink.DOT_Y, -Rink.DOT_Y)
        val path = Path()
        for (i in 0 until 900) {
            var x: Float
            var y: Float
            if (rng.nextFloat() < 0.55f) {
                val a = rng.nextInt(ax.size)
                x = ax[a] + (rng.nextFloat() + rng.nextFloat() - 1f) * 11f
                y = ay[a] + (rng.nextFloat() + rng.nextFloat() - 1f) * 9f
            } else {
                x = (rng.nextFloat() - 0.5f) * 2f * Rink.HALF_L
                y = (rng.nextFloat() - 0.5f) * 2f * Rink.HALF_W
            }
            val ang = rng.nextFloat() * 6.2832f
            val len = 1.5f + rng.nextFloat() * 5f
            val bend = (rng.nextFloat() - 0.5f) * 2.2f
            val ex = x + kotlin.math.cos(ang) * len
            val ey = y + sin(ang) * len
            path.reset()
            path.moveTo(x, y)
            path.quadTo((x + ex) * 0.5f - sin(ang) * bend, (y + ey) * 0.5f + kotlin.math.cos(ang) * bend, ex, ey)
            p.strokeWidth = 0.05f + rng.nextFloat() * 0.07f
            p.color = if (i % 4 == 0) Color.argb(24 + rng.nextInt(24), 110, 145, 180) else Color.argb(26 + rng.nextInt(30), 255, 255, 255)
            c.drawPath(path, p)
        }

        // Darkening along the boards (stroke straddles the edge; the clip keeps the inner half).
        for (w in floatArrayOf(14f, 9f, 6f, 3.5f)) {
            p.strokeWidth = w
            p.color = Color.argb(17, 30, 60, 100)
            c.drawPath(rr, p)
        }
        return bmp
    }

    // ------------------------------------------------------------------ markings

    private val centerDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 0.34f
        pathEffect = DashPathEffect(floatArrayOf(1.1f, 1.1f), 0f)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.2f; strokeCap = Paint.Cap.ROUND
    }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.16f
    }
    private val blueEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 12, 40, 120); style = Paint.Style.STROKE; strokeWidth = 0.14f
    }
    private val whiteEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 0.12f
    }
    private val markLines: FloatArray = buildMarkLines()

    private fun buildMarkLines(): FloatArray {
        val out = FloatArray(4 * 4 * 2 * 4)
        var n = 0
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f)) {
            val cx = sx * Rink.END_DOT_X
            val cy = sy * Rink.DOT_Y
            // Four "L" marks hugging the dot.
            for (kx in floatArrayOf(-1f, 1f)) for (ky in floatArrayOf(-1f, 1f)) {
                out[n++] = cx + kx * 2f; out[n++] = cy + ky * 0.7f
                out[n++] = cx + kx * 2f; out[n++] = cy + ky * 2.7f
                out[n++] = cx + kx * 2f; out[n++] = cy + ky * 2.7f
                out[n++] = cx + kx * 4f; out[n++] = cy + ky * 2.7f
            }
        }
        return out
    }

    /** Extra markings drawn on the ice after the base lines: L marks, dot rings, dashed centre line, crease. */
    fun drawMarkings(canvas: Canvas) {
        canvas.drawLines(markLines, markPaint)
        for (sx in signs) for (sy in signs) {
            canvas.drawCircle(sx * Rink.END_DOT_X, sy * Rink.DOT_Y, 1.9f, dotRing)
        }
        canvas.drawLine(0f, -Rink.HALF_W, 0f, Rink.HALF_W, centerDash)
        // Thin bevelled edges on the blue lines.
        canvas.drawLine(-Rink.BLUE_LINE_X - 0.5f, -Rink.HALF_W, -Rink.BLUE_LINE_X - 0.5f, Rink.HALF_W, blueEdge)
        canvas.drawLine(Rink.BLUE_LINE_X + 0.5f, -Rink.HALF_W, Rink.BLUE_LINE_X + 0.5f, Rink.HALF_W, blueEdge)
        // Referee's crease in front of the penalty box.
        tmpRect.set(-10f, Rink.HALF_W - 10f, 10f, Rink.HALF_W + 10f)
        canvas.drawArc(tmpRect, 180f, 180f, false, markPaint)
    }


    // ------------------------------------------------------------------ perspective helpers

    private val warpVerts = arrayOf(FloatArray(33 * 21 * 2), FloatArray(25 * 17 * 2))
    private val warpDims = arrayOf(intArrayOf(32, 20), intArrayOf(24, 16))

    /**
     * Draws a top-down bitmap covering the world rectangle (l, t, r, b) through the camera's
     * perspective by warping a mesh of it. [slot] picks a preallocated vertex buffer (0 rink, 1 landscape).
     */
    fun drawWarped(canvas: Canvas, cam: Camera, bmp: Bitmap, l: Float, t: Float, r: Float, b: Float, slot: Int, paint: Paint) {
        val mw = warpDims[slot][0]
        val mh = warpDims[slot][1]
        val v = warpVerts[slot]
        var n = 0
        for (j in 0..mh) {
            val wy = t + (b - t) * j / mh
            val sy = cam.py(wy)
            for (i in 0..mw) {
                val wx = l + (r - l) * i / mw
                v[n++] = cam.px(wx, wy)
                v[n++] = sy
            }
        }
        canvas.drawBitmapMesh(bmp, mw, mh, v, 0, null, 0, paint)
    }


    // ------------------------------------------------------------------ screen-clipped quad batches

    /**
     * Collects textured quads clipped to the screen rectangle and draws them with one drawVertices
     * call. Clipping on our side keeps triangles that reach far off-screen (common with strong
     * perspective) from being dropped or mangled by the rasteriser.
     */
    private class QuadBatch(maxQuads: Int) {
        val v = FloatArray(maxQuads * 18 * 2)
        val t = FloatArray(maxQuads * 18 * 2)
        var nv = 0
        var ni = 0
        private var pa = FloatArray(10 * 4)
        private var pb = FloatArray(10 * 4)

        fun reset() { nv = 0; ni = 0 }

        fun quad(
            x0: Float, y0: Float, u0: Float, v0: Float, x1: Float, y1: Float, u1: Float, v1: Float,
            x2: Float, y2: Float, u2: Float, v2: Float, x3: Float, y3: Float, u3: Float, v3: Float,
            l: Float, tp: Float, r: Float, b: Float
        ) {
            var src = pa
            var dst = pb
            src[0] = x0; src[1] = y0; src[2] = u0; src[3] = v0
            src[4] = x1; src[5] = y1; src[6] = u1; src[7] = v1
            src[8] = x2; src[9] = y2; src[10] = u2; src[11] = v2
            src[12] = x3; src[13] = y3; src[14] = u3; src[15] = v3
            var n = 4
            for (edge in 0 until 4) {
                var m = 0
                for (i in 0 until n) {
                    val c = i * 4
                    val p = ((i + n - 1) % n) * 4
                    val dc = dist(src, c, edge, l, tp, r, b)
                    val dp = dist(src, p, edge, l, tp, r, b)
                    if (dc >= 0f) {
                        if (dp < 0f) { m = cross(src, p, c, dp, dc, dst, m) }
                        dst[m * 4] = src[c]; dst[m * 4 + 1] = src[c + 1]; dst[m * 4 + 2] = src[c + 2]; dst[m * 4 + 3] = src[c + 3]
                        m++
                    } else if (dp >= 0f) {
                        m = cross(src, p, c, dp, dc, dst, m)
                    }
                }
                val tmp = src; src = dst; dst = tmp
                n = m
                if (n < 3) return
            }
            quads++
            // Plain (non-indexed) triangles: the fan around vertex 0.
            for (k in 1 until n - 1) {
                for (q in 0..2) {
                    val s = if (q == 0) 0 else if (q == 1) k else k + 1
                    v[nv * 2] = src[s * 4]; v[nv * 2 + 1] = src[s * 4 + 1]
                    t[nv * 2] = src[s * 4 + 2]; t[nv * 2 + 1] = src[s * 4 + 3]
                    nv++
                }
            }
            ni = nv
        }

        private fun dist(a: FloatArray, o: Int, edge: Int, l: Float, tp: Float, r: Float, b: Float): Float = when (edge) {
            0 -> a[o] - l
            1 -> r - a[o]
            2 -> a[o + 1] - tp
            else -> b - a[o + 1]
        }

        private fun cross(a: FloatArray, p: Int, c: Int, dp: Float, dc: Float, out: FloatArray, m: Int): Int {
            val f = dp / (dp - dc)
            for (k in 0 until 4) out[m * 4 + k] = a[p + k] + (a[c + k] - a[p + k]) * f
            return m + 1
        }

        var quads = 0

        fun draw(canvas: Canvas, paint: Paint) {
            if (ni == 0) return
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, nv * 2, v, 0, t, 0, null, 0, null, 0, 0, paint)
            nv = 0; ni = 0; quads = 0
        }
    }

    private val wallBatch = QuadBatch(100)
    private val rakeBatch = QuadBatch(100)

    // ------------------------------------------------------------------ boards and glass walls

    private val wallH = 8.4f
    private val texPx = 7f
    private var perN = 0
    private var perX = FloatArray(0)
    private var perY = FloatArray(0)
    private var perU = FloatArray(0)
    private var nrmX = FloatArray(0)
    private var nrmY = FloatArray(0)
    private var outX = FloatArray(0)
    private var outY = FloatArray(0)
    private var rakeVerts = FloatArray(0)
    private var rakeTex = FloatArray(0)
    private var rakeIdx = ShortArray(0)
    private var wallVerts = FloatArray(0)
    private var wallTex = FloatArray(0)
    private var wallIdx = ShortArray(0)
    private var wallBmp: Bitmap? = null
    private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sponsors = arrayOf("POWER PLAY", "NORTH STAR ICE", "FROSTBITE", "BLUE LINE", "ARCTIC FUEL", "SLAPSHOT", "ZAMBONI CLEAN", "HAT TRICK")

    init {
        buildPerimeter()
    }

    private fun buildPerimeter() {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val r = Rink.CORNER_R
        val hx = Rink.HALF_L
        val hw = Rink.HALF_W
        fun straight(x1: Float, y1: Float, x2: Float, y2: Float) {
            val len = hypot(x2 - x1, y2 - y1)
            val k = max(1, Math.ceil((len / 8f).toDouble()).toInt())
            for (i in 0 until k) {
                xs.add(x1 + (x2 - x1) * i / k)
                ys.add(y1 + (y2 - y1) * i / k)
            }
        }
        fun arc(cx: Float, cy: Float, a0: Float, a1: Float) {
            val k = 9
            for (i in 0 until k) {
                val a = Math.toRadians((a0 + (a1 - a0) * i / k).toDouble())
                xs.add(cx + r * kotlin.math.cos(a).toFloat())
                ys.add(cy + r * sin(a).toFloat())
            }
        }
        straight(-(hx - r), -hw, hx - r, -hw)
        arc(hx - r, -hw + r, -90f, 0f)
        straight(hx, -hw + r, hx, hw - r)
        arc(hx - r, hw - r, 0f, 90f)
        straight(hx - r, hw, -(hx - r), hw)
        arc(-(hx - r), hw - r, 90f, 180f)
        straight(-hx, hw - r, -hx, -hw + r)
        arc(-(hx - r), -hw + r, 180f, 270f)
        xs.add(xs[0]); ys.add(ys[0])
        perN = xs.size
        perX = FloatArray(perN) { xs[it] }
        perY = FloatArray(perN) { ys[it] }
        perU = FloatArray(perN)
        nrmX = FloatArray(perN)
        nrmY = FloatArray(perN)
        for (i in 1 until perN) perU[i] = perU[i - 1] + hypot(perX[i] - perX[i - 1], perY[i] - perY[i - 1])
        for (i in 0 until perN - 1) {
            val dx = perX[i + 1] - perX[i]
            val dy = perY[i + 1] - perY[i]
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            var nx = -dy / len
            var ny = dx / len
            val mx = (perX[i] + perX[i + 1]) * 0.5f
            val my = (perY[i] + perY[i + 1]) * 0.5f
            if (nx * -mx + ny * -my < 0f) { nx = -nx; ny = -ny }
            nrmX[i] = nx; nrmY[i] = ny
        }
        outX = FloatArray(perN)
        outY = FloatArray(perN)
        for (i in 0 until perN) {
            val a = if (i == 0) perN - 2 else i - 1
            val b = if (i == perN - 1) 0 else i
            var ox = -(nrmX[a] + nrmX[b])
            var oy = -(nrmY[a] + nrmY[b])
            val l = hypot(ox, oy).coerceAtLeast(0.001f)
            ox /= l; oy /= l
            outX[i] = ox; outY[i] = oy
        }
        rakeVerts = FloatArray(perN * 4)
        rakeTex = FloatArray(perN * 4)
        rakeIdx = ShortArray(perN * 6)
        wallVerts = FloatArray(perN * 4)
        wallTex = FloatArray(perN * 4)
        wallIdx = ShortArray(perN * 6)
    }

    private fun luminance(c: Int) = 0.3f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)

    /** Bakes the board and glass strip: glass and posts on top, ad panels with sponsor text, kick plate. */
    fun buildWall(home: Int, away: Int) {
        val total = perU[perN - 1]
        val tw = Math.ceil((total * texPx).toDouble()).toInt().coerceIn(64, 4090)
        val th = Math.ceil((wallH * texPx).toDouble()).toInt()
        val sxs = tw / total
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sxs, texPx)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Glass.
        p.shader = LinearGradient(0f, 0f, 0f, 3.8f, Color.argb(20, 170, 210, 240), Color.argb(85, 170, 210, 240), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, total, 3.8f, p)
        p.shader = null
        val glare = Path()
        val nGl = max(1, Math.round(total / 24f))
        p.color = Color.argb(46, 255, 255, 255)
        for (i in 0 until nGl) {
            val x = total * (i + 0.3f) / nGl
            glare.reset()
            glare.moveTo(x, 0f); glare.lineTo(x + 2.4f, 0f); glare.lineTo(x + 0.2f, 3.8f); glare.lineTo(x - 1.6f, 3.8f); glare.close()
            c.drawPath(glare, p)
        }
        val nPost = max(1, Math.round(total / 10f))
        p.color = Color.argb(225, 70, 84, 104)
        for (i in 0..nPost) {
            val x = total * i / nPost
            c.drawRect(x - 0.14f, 0f, x + 0.14f, 4.1f, p)
        }
        // Rail.
        p.color = Color.parseColor("#DCE5EF")
        c.drawRect(0f, 3.8f, total, 4.15f, p)
        // Boards.
        p.shader = LinearGradient(0f, 4.15f, 0f, 7.7f, Color.parseColor("#FFFFFF"), Color.parseColor("#CBD6E2"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 4.15f, total, 7.7f, p)
        p.shader = null
        // Ad panels, a whole number of them so there is no seam where the strip wraps.
        val nPan = max(2, Math.round(total / 14f))
        val pw = total / nPan
        val colors = intArrayOf(home, Color.parseColor("#1E3A8A"), away, Color.parseColor("#B91C1C"), Color.parseColor("#0F766E"), Color.parseColor("#CA8A04"))
        val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.LEFT
        for (i in 0 until nPan) {
            val x0 = i * pw + 0.3f
            val x1 = (i + 1) * pw - 0.3f
            val col = colors[i % colors.size]
            p.color = col
            c.drawRoundRect(x0, 4.45f, x1, 7.4f, 0.25f, 0.25f, p)
            p.color = Color.argb(70, 255, 255, 255)
            c.drawRect(x0, 4.45f, x1, 4.75f, p)
            p.color = Color.argb(230, 255, 255, 255)
            c.drawCircle(x0 + 0.9f, 5.95f, 0.38f, p)
        }
        // Text is drawn in pixel space (no canvas scale) so glyph advances are not clipped or kerned wrongly.
        c.save()
        c.scale(1f / sxs, 1f / texPx)
        for (i in 0 until nPan) {
            val x0 = (i * pw + 0.3f) * sxs
            val x1 = ((i + 1) * pw - 0.3f) * sxs
            val col = colors[i % colors.size]
            val txt = sponsors[i % sponsors.size]
            tp.color = if (luminance(col) > 150f) Color.parseColor("#0B1220") else Color.WHITE
            tp.textSize = 2.1f * texPx
            tp.letterSpacing = 0.06f
            var mw = tp.measureText(txt)
            val avail = (x1 - x0) * 0.74f
            if (mw > avail) {
                tp.textSize = tp.textSize * avail / mw
                mw = tp.measureText(txt)
            }
            val left = x0 + (x1 - x0) * 0.2f + ((x1 - x0) * 0.76f - mw) / 2f
            c.drawText(txt, left, 6.5f * texPx, tp)
        }
        c.restore()
        // Kick plate.
        p.shader = LinearGradient(0f, 7.7f, 0f, wallH, Color.parseColor("#F4C542"), Color.parseColor("#B8891A"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 7.7f, total, wallH, p)
        p.shader = null
        wallBmp?.recycle()
        wallBmp = bmp
        wallPaint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        // Texture coordinates are in texels.
        for (i in 0 until perN) {
            wallTex[i * 4] = perU[i] * sxs
            wallTex[i * 4 + 1] = 0f
            wallTex[i * 4 + 2] = perU[i] * sxs
            wallTex[i * 4 + 3] = th.toFloat()
        }
    }

    /** The wall faces that can be seen from the camera, as one textured, perspective-projected mesh. */
    fun drawWalls(canvas: Canvas, cam: Camera) {
        if (wallBmp == null) return
        for (i in 0 until perN) {
            val k = cam.ppf(perY[i])
            val bx = cam.px(perX[i], perY[i])
            val by = cam.py(perY[i])
            wallVerts[i * 4] = bx
            wallVerts[i * 4 + 1] = by - wallH * k
            wallVerts[i * 4 + 2] = bx
            wallVerts[i * 4 + 3] = by
        }
        val sw = cam.screenW + 4f
        val sh = cam.screenH + 4f
        wallBatch.reset()
        for (i in 0 until perN - 1) {
            val mx = (perX[i] + perX[i + 1]) * 0.5f
            val my = (perY[i] + perY[i + 1]) * 0.5f
            val vx = cam.x - mx
            val vy = cam.y + 70f - my
            if (nrmX[i] * vx + nrmY[i] * vy > 0f && my < cam.y + 34f) {
                val j = i + 1
                wallBatch.quad(
                    wallVerts[i * 4], wallVerts[i * 4 + 1], wallTex[i * 4], wallTex[i * 4 + 1],
                    wallVerts[i * 4 + 2], wallVerts[i * 4 + 3], wallTex[i * 4 + 2], wallTex[i * 4 + 3],
                    wallVerts[j * 4 + 2], wallVerts[j * 4 + 3], wallTex[j * 4 + 2], wallTex[j * 4 + 3],
                    wallVerts[j * 4], wallVerts[j * 4 + 1], wallTex[j * 4], wallTex[j * 4 + 1],
                    -4f, -4f, sw, sh
                )
            }
        }
        wallBatch.draw(canvas, wallPaint)
    }

    // ------------------------------------------------------------------ stands

    private class Layer(val yb: Float, val hFt: Float, val wFt: Float, val dim: Float) {
        var bmp: Bitmap? = null
        val dst = RectF()
    }

    private val layers = arrayOf(
        Layer(-Rink.HALF_W - 4f, 9f, 300f, 1.05f),
        Layer(-Rink.HALF_W - 12f, 11f, 330f, 1.0f),
        Layer(-Rink.HALF_W - 24f, 14f, 380f, 0.95f)
    )
    private var sideStand: Bitmap? = null
    private val standPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sideVerts = FloatArray(8)
    private val sideTex = FloatArray(8)
    private val sideIdx = shortArrayOf(0, 1, 2, 2, 1, 3)
    private val sidePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.LightingColorFilter(0xFFB4BACB.toInt(), 0x00000000) }
    private val skin = intArrayOf(
        Color.parseColor("#F2C9A5"), Color.parseColor("#E0A87C"), Color.parseColor("#C58C5E"),
        Color.parseColor("#8D5A3B"), Color.parseColor("#5C3A26"), Color.parseColor("#F7D9C0")
    )
    private val hair = intArrayOf(
        Color.parseColor("#1B1410"), Color.parseColor("#3B2A1C"), Color.parseColor("#6B4A2B"),
        Color.parseColor("#C9A25B"), Color.parseColor("#8A8A8A"), Color.parseColor("#0A0A0A")
    )
    private val shirts = intArrayOf(
        Color.parseColor("#D62839"), Color.parseColor("#E23A4A"), Color.parseColor("#F1F5F9"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#F2C230"), Color.parseColor("#2D5BD8"), Color.parseColor("#EA6A1E"), Color.parseColor("#14A38B"),
        Color.parseColor("#C2185B"), Color.parseColor("#7C4DFF"), Color.parseColor("#2B3A57"), Color.parseColor("#F1F5F9")
    )

    private fun shade(color: Int, f: Float): Int =
        Color.rgb((Color.red(color) * f).toInt().coerceIn(0, 255), (Color.green(color) * f).toInt().coerceIn(0, 255), (Color.blue(color) * f).toInt().coerceIn(0, 255))

    /** One upright spectator; [yb] is the bottom of his seat row. */
    private fun fan(c: Canvas, p: Paint, x: Float, yb: Float, shirt: Int, rng: Random, dim: Float) {
        p.style = Paint.Style.FILL
        p.color = shade(shirt, dim)
        tmpRect.set(x - 0.55f, yb - 1.05f, x + 0.55f, yb + 0.15f)
        c.drawRoundRect(tmpRect, 0.4f, 0.4f, p)
        if (rng.nextFloat() < 0.1f) {
            p.color = shade(skin[rng.nextInt(skin.size)], dim)
            c.drawCircle(x - 0.6f, yb - 1.45f, 0.17f, p)
            c.drawCircle(x + 0.6f, yb - 1.45f, 0.17f, p)
        }
        p.color = shade(hair[rng.nextInt(hair.size)], dim)
        c.drawCircle(x, yb - 1.25f, 0.36f, p)
        p.color = shade(skin[rng.nextInt(skin.size)], dim)
        c.drawCircle(x, yb - 1.15f, 0.28f, p)
    }

    private fun fanRows(c: Canvas, wFt: Float, hFt: Float, home: Int, away: Int, dim: Float, rng: Random) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rowH = 1.7f
        val rows = (hFt / rowH).toInt() + 1
        for (r in 0 until rows) {
            val yb = hFt - r * rowH
            val rd = dim * (1f - r * 0.04f).coerceAtLeast(0.75f)
            p.style = Paint.Style.FILL
            p.color = shade(Color.parseColor("#2A3556"), rd)
            c.drawRect(0f, yb - 0.1f, wFt, yb + 0.6f, p)
            var x = 0.6f + rng.nextFloat() * 0.5f
            var idx = 0
            while (x < wFt) {
                idx++
                if (idx % 28 != 0) {
                    if (rng.nextFloat() < 0.06f) {
                        p.color = shade(Color.parseColor("#22304A"), rd)
                        c.drawRect(x - 0.4f, yb - 0.7f, x + 0.4f, yb, p)
                    } else {
                        val q = rng.nextFloat()
                        val shirt = when {
                            q < 0.45f -> if (x < wFt / 2f) home else away
                            q < 0.46f -> if (x < wFt / 2f) away else home
                            else -> shirts[rng.nextInt(shirts.size)]
                        }
                        fan(c, p, x, yb, shirt, rng, rd)
                    }
                }
                x += 1.0f + rng.nextFloat() * 0.25f
            }
        }
            // Aisles: lit stair strips running up the rake.
        var ax = 11f
        while (ax < wFt) {
            p.color = shade(Color.parseColor("#2B3652"), dim)
            c.drawRect(ax - 0.8f, 0f, ax + 0.8f, hFt, p)
            p.color = shade(Color.parseColor("#56658A"), dim)
            var yy = hFt
            while (yy > 0f) {
                c.drawRect(ax - 0.8f, yy - 0.1f, ax + 0.8f, yy + 0.05f, p)
                yy -= rowH
            }
            ax += 26f
        }
    }

    /** Bakes the three tiers behind the far wall plus the stand at the ends. */
    fun buildStands(home: Int, away: Int) {
        val rng = Random(7)
        for ((li, l) in layers.withIndex()) {
            val bw = (l.wFt * STAND_PX).toInt()
            val bh = (l.hFt * STAND_PX).toInt()
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            c.drawColor(shade(Color.parseColor("#1B2542"), l.dim))
            c.scale(STAND_PX, STAND_PX)
            fanRows(c, l.wFt, l.hFt, home, away, l.dim, rng)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (li == 2) {
                // Arena lights glowing in the rafters.
                var x = 4f
                while (x < l.wFt) {
                    p.color = Color.argb(40, 255, 250, 225)
                    c.drawCircle(x, 1.2f, 2.2f, p)
                    p.color = Color.argb(230, 255, 252, 235)
                    c.drawCircle(x, 1.2f, 0.55f, p)
                    x += 17f
                }
            }
            l.bmp?.recycle()
            l.bmp = bmp
        }
        val sw = (SIDE_SPAN * STAND_PX).toInt()
        val sh = (RAKE_H * STAND_PX).toInt()
        val sb = Bitmap.createBitmap(sw, sh, Bitmap.Config.RGB_565)
        val c = Canvas(sb)
        c.drawColor(Color.parseColor("#0A101C"))
        c.scale(STAND_PX, STAND_PX)
        fanRows(c, SIDE_SPAN, RAKE_H, home, away, 0.9f, rng)
        sideStand?.recycle()
        sideStand = sb
        sidePaint.shader = BitmapShader(sb, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    }

    /** Draws the tiers as billboards at their own depths, so they parallax against each other. */
    fun drawStands(canvas: Canvas, cam: Camera) {
        val side = sideStand
        if (side != null) {
            // A raked bowl of seats running round the whole rink, so the corners and ends are never empty.
            val th = side.height.toFloat()
            for (i in 0 until perN) {
                val ox = perX[i] + outX[i] * RAKE_D
                val oy = perY[i] + outY[i] * RAKE_D
                val kb = cam.ppf(perY[i])
                val ko = cam.ppf(oy)
                rakeVerts[i * 4] = cam.px(perX[i], perY[i])
                rakeVerts[i * 4 + 1] = cam.py(perY[i]) - wallH * kb
                rakeVerts[i * 4 + 2] = cam.px(ox, oy)
                rakeVerts[i * 4 + 3] = cam.py(oy) - (wallH + RAKE_H) * ko
                rakeTex[i * 4] = perU[i] * STAND_PX
                rakeTex[i * 4 + 1] = th
                rakeTex[i * 4 + 2] = perU[i] * STAND_PX
                rakeTex[i * 4 + 3] = 0f
            }
            rakeBatch.reset()
            val sw = cam.screenW + 4f
            val sh = cam.screenH + 4f
            for (i in 0 until perN - 1) {
                val my = (perY[i] + perY[i + 1]) * 0.5f
                if (my > cam.y + 30f) continue
                val j = i + 1
                rakeBatch.quad(
                    rakeVerts[i * 4], rakeVerts[i * 4 + 1], rakeTex[i * 4], rakeTex[i * 4 + 1],
                    rakeVerts[i * 4 + 2], rakeVerts[i * 4 + 3], rakeTex[i * 4 + 2], rakeTex[i * 4 + 3],
                    rakeVerts[j * 4 + 2], rakeVerts[j * 4 + 3], rakeTex[j * 4 + 2], rakeTex[j * 4 + 3],
                    rakeVerts[j * 4], rakeVerts[j * 4 + 1], rakeTex[j * 4], rakeTex[j * 4 + 1],
                    -4f, -4f, sw, sh
                )
            }
            rakeBatch.draw(canvas, sidePaint)
        }
        var bottom = cam.py(-Rink.HALF_W) - 4.2f * cam.ppf(-Rink.HALF_W)
        for (l in layers) {
            val bmp = l.bmp ?: continue
            val f = cam.depth(l.yb)
            val wpx = l.wFt * cam.scale * f
            val hpx = l.hFt * cam.scale * f
            val cx = cam.px(0f, l.yb)
            l.dst.set(cx - wpx / 2f, bottom - hpx, cx + wpx / 2f, bottom)
            if (l.dst.bottom > 0f && l.dst.top < cam.screenH) canvas.drawBitmap(bmp, null, l.dst, standPaint)
            bottom -= hpx - 1f
        }
    }

    // ------------------------------------------------------------------ camera flashes

    private val flashRng = Random(5)
    private val flashLayer = IntArray(14)
    private val flashU = FloatArray(14)
    private val flashV = FloatArray(14)
    private val flashLife = FloatArray(14)
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun updateFlashes(dt: Float) {
        for (i in flashLife.indices) {
            flashLife[i] -= dt
            if (flashLife[i] <= 0f && flashRng.nextFloat() < dt * 1.6f) {
                flashLayer[i] = flashRng.nextInt(layers.size)
                flashU[i] = flashRng.nextFloat()
                flashV[i] = 0.15f + flashRng.nextFloat() * 0.85f
                flashLife[i] = 0.14f + flashRng.nextFloat() * 0.1f
            }
        }
    }

    fun drawFlashes(canvas: Canvas) {
        for (i in flashLife.indices) {
            if (flashLife[i] <= 0f) continue
            val d = layers[flashLayer[i]].dst
            if (d.width() <= 0f) continue
            val x = d.left + flashU[i] * d.width()
            val y = d.top + flashV[i] * d.height()
            val a = (flashLife[i] / 0.2f).coerceIn(0f, 1f)
            flashPaint.color = Color.argb((90 * a).toInt(), 255, 255, 240)
            canvas.drawCircle(x, y, 9f, flashPaint)
            flashPaint.color = Color.argb((235 * a).toInt(), 255, 255, 250)
            canvas.drawCircle(x, y, 3f, flashPaint)
        }
    }

    // ------------------------------------------------------------------ referee (animation state only)

    var refX = 0f
        private set
    var refY = 28f
        private set
    var refAngle = 0f
        private set
    var refStride = 0f
        private set
    private var refVx = 0f
    private var refVy = 0f
    private var refSide = 1f

    /** Moves the referee toward a spot off the play. Animation state only; world is not modified. */
    fun updateReferee(world: World, dt: Float) {
        val d = min(dt, 0.05f)
        if (d <= 0f) return
        val pk = world.puck
        if (abs(pk.y) > 16f) refSide = if (pk.y > 0f) -1f else 1f
        val tx: Float
        val ty: Float
        if (world.phase == Phase.FACEOFF) {
            tx = world.faceoffX
            ty = world.faceoffY + (if (world.faceoffY > 0f) -7f else 7f)
        } else {
            tx = (pk.x * 0.85f).coerceIn(-(Rink.HALF_L - 22f), Rink.HALF_L - 22f)
            ty = refSide * 29f
        }
        val dx = tx - refX
        val dy = ty - refY
        val dist = hypot(dx, dy)
        val sp = min(17f, dist * 1.4f)
        val dvx = if (dist > 0.05f) dx / dist * sp else 0f
        val dvy = if (dist > 0.05f) dy / dist * sp else 0f
        val k = 1f - exp(-d * 3.2f)
        refVx += (dvx - refVx) * k
        refVy += (dvy - refVy) * k
        refX += refVx * d
        refY += refVy * d
        val spd = hypot(refVx, refVy)
        val target = if (spd > 1.5f) atan2(refVy, refVx) else atan2(pk.y - refY, pk.x - refX)
        var diff = target - refAngle
        while (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
        while (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
        refAngle += diff * (1f - exp(-d * 6f))
        refStride += spd * d * 0.8f
    }
}
