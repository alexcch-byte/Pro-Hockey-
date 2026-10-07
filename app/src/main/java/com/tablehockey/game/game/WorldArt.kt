package com.tablehockey.game.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
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
        private const val CROWD_PX = 6f
    }

    private val tmpRect = RectF()
    private val signs = floatArrayOf(-1f, 1f)
    private val isigns = intArrayOf(-1, 1)
    private val stripeX = floatArrayOf(-0.62f, -0.2f, 0.25f)

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
        p.shader = RadialGradient(0f, 0f, 26f, Color.argb(95, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
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
            p.color = if (i % 4 == 0) Color.argb(12 + rng.nextInt(14), 110, 145, 180) else Color.argb(14 + rng.nextInt(20), 255, 255, 255)
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

    // ------------------------------------------------------------------ boards & glass

    private val adPath = Path().apply {
        addRoundRect(RectF(-Rink.HALF_L - 1.1f, -Rink.HALF_W - 1.1f, Rink.HALF_L + 1.1f, Rink.HALF_W + 1.1f), Rink.CORNER_R + 1.1f, Rink.CORNER_R + 1.1f, Path.Direction.CW)
    }
    private val glassPath = Path().apply {
        addRoundRect(RectF(-Rink.HALF_L - 2.5f, -Rink.HALF_W - 2.5f, Rink.HALF_L + 2.5f, Rink.HALF_W + 2.5f), Rink.CORNER_R + 2.5f, Rink.CORNER_R + 2.5f, Path.Direction.CW)
    }
    private val shadowPath = Path().apply {
        addRoundRect(RectF(-Rink.HALF_L - 3.9f, -Rink.HALF_W - 3.9f, Rink.HALF_L + 3.9f, Rink.HALF_W + 3.9f), Rink.CORNER_R + 3.9f, Rink.CORNER_R + 3.9f, Path.Direction.CW)
    }
    private val adPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.3f }
    private val adColors = IntArray(4)
    private val adDash = Array(4) { i -> DashPathEffect(floatArrayOf(8f, 26.4f), 34.4f - i * 8.6f) }
    private val adTrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.18f; color = Color.argb(110, 0, 0, 0) }
    private val outerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.6f; color = Color.argb(85, 0, 0, 8) }
    private val gleamWide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.34f; color = Color.argb(105, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(7f, 29f), 0f)
    }
    private val gleamThin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.2f; color = Color.argb(90, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(2.5f, 17f), 11f)
    }
    private val stanchions = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.9f; color = Color.argb(215, 60, 72, 90)
        pathEffect = DashPathEffect(floatArrayOf(0.45f, 9.55f), 0f)
    }
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.22f; color = Color.argb(170, 226, 236, 246) }

    /** A soft shadow cast by the glass onto the stands; draw before the boards. */
    fun drawOuterShadow(canvas: Canvas) {
        canvas.drawPath(shadowPath, outerShadow)
    }

    /** Coloured advert panels on the dasher boards (two of every four use the club colours). */
    fun drawBoardAds(canvas: Canvas, homeColor: Int, awayColor: Int) {
        adColors[0] = homeColor
        adColors[1] = Color.parseColor("#1E3A8A")
        adColors[2] = awayColor
        adColors[3] = Color.parseColor("#CA8A04")
        for (i in 0..3) {
            adPaint.color = adColors[i]
            adPaint.pathEffect = adDash[i]
            canvas.drawPath(adPath, adPaint)
        }
        adPaint.pathEffect = null
        canvas.drawPath(adPath, adTrim)
    }

    /** Glare, posts and the top rail of the glass; draw after the glass stroke. */
    fun drawGlassDetail(canvas: Canvas) {
        canvas.drawPath(glassPath, gleamWide)
        canvas.drawPath(glassPath, gleamThin)
        canvas.drawPath(glassPath, stanchions)
        canvas.drawPath(glassPath, rail)
    }


    // Far-side wall seen from the tilted camera: a tall board face with ad panels and glass above it.
    private val farLeft = -(Rink.HALF_L - 6f)
    private val farRight = Rink.HALF_L - 6f
    private val farTop = -Rink.HALF_W - 8.5f
    private val farBoardTop = -Rink.HALF_W - 4.2f
    private val farBottom = -Rink.HALF_W - 1.1f
    private val farGlass = Paint().apply {
        shader = LinearGradient(0f, farTop, 0f, farBoardTop,
            intArrayOf(Color.argb(10, 190, 225, 255), Color.argb(95, 190, 225, 255)), null, Shader.TileMode.CLAMP)
    }
    private val farBoard = Paint().apply {
        shader = LinearGradient(0f, farBoardTop, 0f, farBottom,
            intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#C9D5E2")), null, Shader.TileMode.CLAMP)
    }
    private val farAd = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.9f }
    private val farLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f; color = Color.argb(200, 226, 236, 246) }

    fun drawFarWall(canvas: Canvas, homeColor: Int, awayColor: Int) {
        canvas.drawRect(farLeft, farTop, farRight, farBoardTop, farGlass)
        canvas.drawLine(farLeft, farTop, farRight, farTop, farLine)
        canvas.drawRect(farLeft, farBoardTop, farRight, farBottom, farBoard)
        val y = farBoardTop + 1.7f
        adColors[0] = homeColor; adColors[1] = Color.parseColor("#1E3A8A"); adColors[2] = awayColor; adColors[3] = Color.parseColor("#CA8A04")
        for (i in 0..3) {
            farAd.color = adColors[i]
            farAd.pathEffect = adDash[i]
            canvas.drawLine(farLeft, y, farRight, y, farAd)
        }
        farAd.pathEffect = null
        canvas.drawLine(farLeft, farBottom, farRight, farBottom, farLine)
    }

    // ------------------------------------------------------------------ crowd

    private val skin = intArrayOf(
        Color.parseColor("#F2C9A5"), Color.parseColor("#E0A87C"), Color.parseColor("#C58C5E"),
        Color.parseColor("#8D5A3B"), Color.parseColor("#5C3A26"), Color.parseColor("#F7D9C0")
    )
    private val hair = intArrayOf(
        Color.parseColor("#1B1410"), Color.parseColor("#3B2A1C"), Color.parseColor("#6B4A2B"),
        Color.parseColor("#C9A25B"), Color.parseColor("#8A8A8A"), Color.parseColor("#0A0A0A")
    )
    private val shirts = intArrayOf(
        Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.parseColor("#7F1D1D"), Color.parseColor("#1E3A8A"),
        Color.parseColor("#374151"), Color.parseColor("#9A3412"), Color.parseColor("#14532D"), Color.parseColor("#E2E8F0"),
        Color.parseColor("#FDE68A"), Color.parseColor("#6D28D9"), Color.parseColor("#0F766E")
    )

    private fun shade(color: Int, f: Float): Int =
        Color.rgb((Color.red(color) * f).toInt().coerceIn(0, 255), (Color.green(color) * f).toInt().coerceIn(0, 255), (Color.blue(color) * f).toInt().coerceIn(0, 255))

    /** Draws one spectator facing the rink; [ang] is the direction (degrees) of the rink from the seat. */
    private fun fan(c: Canvas, p: Paint, x: Float, y: Float, ang: Float, shirt: Int, rng: Random, dim: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(ang)
        p.style = Paint.Style.FILL
        p.color = shade(shirt, dim)
        tmpRect.set(-0.4f, -0.66f, 0.28f, 0.66f)
        c.drawOval(tmpRect, p)
        if (rng.nextFloat() < 0.1f) {
            // Arms up, cheering.
            p.color = shade(skin[rng.nextInt(skin.size)], dim)
            c.drawCircle(0.5f, -0.58f, 0.17f, p)
            c.drawCircle(0.5f, 0.58f, 0.17f, p)
        }
        p.color = shade(hair[rng.nextInt(hair.size)], dim)
        c.drawCircle(0.04f, 0f, 0.33f, p)
        p.color = shade(skin[rng.nextInt(skin.size)], dim)
        c.drawCircle(0.14f, 0f, 0.26f, p)
        c.restore()
    }

    fun buildCrowd(homeColor: Int, awayColor: Int): Bitmap {
        val bw = (2f * Camera.WORLD_HALF_W * CROWD_PX).toInt().coerceAtLeast(8)
        val bh = (2f * Camera.WORLD_HALF_H * CROWD_PX).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#070C18"))
        c.translate(bw / 2f, bh / 2f)
        c.scale(CROWD_PX, CROWD_PX)
        val rng = Random(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val stepRow = 1.6f
        val stepSeat = 1.1f
        var ring = Rink.HALF_W + 3.5f
        var row = 0
        while (ring < Camera.WORLD_HALF_H + 1f) {
            val halfL = Rink.HALF_L + 3.5f + row * stepRow
            val halfW = ring
            val dim = (1f - row * 0.075f).coerceAtLeast(0.42f)
            // Stand risers: a darker band behind each row.
            p.style = Paint.Style.STROKE
            p.strokeWidth = stepRow * 0.9f
            p.color = shade(Color.parseColor("#141C2E"), dim)
            tmpRect.set(-halfL, -halfW, halfL, halfW)
            c.drawRoundRect(tmpRect, 3f, 3f, p)
            var idx = 0
            var sx = -halfL
            while (sx <= halfL) {
                idx++
                if (idx % 26 != 0) {
                    for (side in 0..1) {
                        val sy = if (side == 0) -halfW else halfW
                        seat(c, p, sx + rng.nextFloat() * 0.25f, sy + rng.nextFloat() * 0.25f, if (side == 0) 90f else -90f, sx, homeColor, awayColor, rng, dim)
                    }
                }
                sx += stepSeat
            }
            var sy = -halfW
            while (sy <= halfW) {
                idx++
                if (idx % 26 != 0) {
                    for (side in 0..1) {
                        val x = if (side == 0) -halfL else halfL
                        seat(c, p, x + rng.nextFloat() * 0.25f, sy + rng.nextFloat() * 0.25f, if (side == 0) 0f else 180f, x, homeColor, awayColor, rng, dim)
                    }
                }
                sy += stepSeat
            }
            ring += stepRow
            row++
        }

        // Dark walkway behind the glass with a thin lit edge.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.parseColor("#0F1B2E")
        tmpRect.set(-Rink.HALF_L - 2.5f, -Rink.HALF_W - 2.5f, Rink.HALF_L + 2.5f, Rink.HALF_W + 2.5f)
        c.drawRoundRect(tmpRect, Rink.CORNER_R + 2.5f, Rink.CORNER_R + 2.5f, p)
        p.strokeWidth = 0.3f
        p.color = Color.parseColor("#27406A")
        tmpRect.inset(-1.5f, -1.5f)
        c.drawRoundRect(tmpRect, Rink.CORNER_R + 4f, Rink.CORNER_R + 4f, p)

        // Camera flashes twinkling in the stands.
        p.style = Paint.Style.FILL
        for (i in 0 until 90) {
            val x = (rng.nextFloat() - 0.5f) * 2f * Camera.WORLD_HALF_W
            val y = (rng.nextFloat() - 0.5f) * 2f * Camera.WORLD_HALF_H
            if (abs(x) < Rink.HALF_L + 5f && abs(y) < Rink.HALF_W + 5f) continue
            p.color = Color.argb(70, 255, 255, 235)
            c.drawCircle(x, y, 0.55f, p)
            p.color = Color.argb(210, 255, 255, 245)
            c.drawCircle(x, y, 0.2f, p)
        }

        // Arena lighting falls off toward the back rows and corners.
        p.shader = RadialGradient(
            0f, 0f, Camera.WORLD_HALF_W * 1.1f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(175, 2, 6, 16)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(-Camera.WORLD_HALF_W, -Camera.WORLD_HALF_H, Camera.WORLD_HALF_W, Camera.WORLD_HALF_H, p)
        p.shader = null
        return bmp
    }

    private fun seat(c: Canvas, p: Paint, x: Float, y: Float, ang: Float, along: Float, home: Int, away: Int, rng: Random, dim: Float) {
        val r = rng.nextFloat()
        if (r < 0.06f) {
            // Empty seat.
            p.style = Paint.Style.FILL
            p.color = shade(Color.parseColor("#22304A"), dim)
            tmpRect.set(x - 0.4f, y - 0.4f, x + 0.4f, y + 0.4f)
            c.drawRoundRect(tmpRect, 0.15f, 0.15f, p)
            return
        }
        // Each end of the arena backs its own club; the rest wear assorted colours.
        val shirt = when {
            r < 0.40f -> if (along < 0f) home else away
            r < 0.46f -> if (along < 0f) away else home
            else -> shirts[rng.nextInt(shirts.size)]
        }
        fan(c, p, x, y, ang, shirt, rng, dim)
    }

    // ------------------------------------------------------------------ referee

    private var refX = 0f
    private var refY = 28f
    private var refVx = 0f
    private var refVy = 0f
    private var refAngle = 0f
    private var refStride = 0f
    private var refSide = 1f

    private val refShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 10, 20, 40) }
    private val refWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F1F5F9") }
    private val refBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F17") }
    private val refOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.14f; color = Color.parseColor("#0B0F17") }
    private val refStripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.26f; strokeCap = Paint.Cap.BUTT; color = Color.parseColor("#0B0F17") }
    private val refArm = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#0B0F17") }
    private val refBand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.72f; strokeCap = Paint.Cap.BUTT; color = Color.parseColor("#F97316") }
    private val refGloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
    private val refStripes = FloatArray(12)
    private val refBands = FloatArray(8)

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

    /** Striped shirt, black helmet, orange armbands. A dozen draw calls in total. */
    fun drawReferee(canvas: Canvas) {
        val r = 1.5f
        tmpRect.set(refX - r * 1.3f + 0.3f, refY - r * 1.0f + 0.5f, refX + r * 1.3f + 0.3f, refY + r * 1.0f + 0.5f)
        canvas.drawOval(tmpRect, refShadow)
        canvas.save()
        canvas.translate(refX, refY)
        canvas.rotate(Math.toDegrees(refAngle.toDouble()).toFloat())
        canvas.scale(1.25f, 1.25f)
        val st = sin(refStride * 1.3f)
        for (side in isigns) {
            val ph = st * side
            tmpRect.set(-0.5f * r + ph * 0.6f * r, side * 0.72f * r - 0.2f * r, 0.5f * r + ph * 0.6f * r, side * 0.72f * r + 0.2f * r)
            canvas.drawRoundRect(tmpRect, 0.15f * r, 0.15f * r, refBlack)
        }
        // Arms and orange bands.
        canvas.drawLine(-0.1f * r, -0.8f * r, 0.7f * r, -0.95f * r, refArm)
        canvas.drawLine(-0.1f * r, 0.8f * r, 0.7f * r, 0.95f * r, refArm)
        refBands[0] = 0.25f * r; refBands[1] = -0.88f * r; refBands[2] = 0.25f * r + 0.01f; refBands[3] = -0.88f * r
        refBands[4] = 0.25f * r; refBands[5] = 0.88f * r; refBands[6] = 0.25f * r + 0.01f; refBands[7] = 0.88f * r
        canvas.drawLine(0.15f * r, -0.86f * r, 0.35f * r, -0.9f * r, refBand)
        canvas.drawLine(0.15f * r, 0.86f * r, 0.35f * r, 0.9f * r, refBand)
        // Torso with vertical stripes.
        tmpRect.set(-0.95f * r, -1.05f * r, 0.8f * r, 1.05f * r)
        canvas.drawOval(tmpRect, refWhite)
        canvas.drawOval(tmpRect, refOutline)
        var n = 0
        for (sx in stripeX) {
            val h = sqrt((1f - ((sx + 0.075f) / 0.875f) * ((sx + 0.075f) / 0.875f)).coerceAtLeast(0f)) * 1.0f
            refStripes[n++] = sx * r; refStripes[n++] = -h * r
            refStripes[n++] = sx * r; refStripes[n++] = h * r
        }
        canvas.drawLines(refStripes, 0, n, refStripe)
        // Helmet.
        canvas.drawCircle(0.26f * r, 0f, 0.55f * r, refBlack)
        canvas.drawCircle(0.4f * r, -0.2f * r, 0.13f * r, refGloss)
        canvas.restore()
    }
}
