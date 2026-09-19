package com.tablehockey.game.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tablehockey.game.model.TeamInfo
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the world (rink, players, puck) through the [Camera], then the
 * scoreboard, banners and touch controls in screen space.
 */
class Renderer(private val density: Float) {

    val camera = Camera()

    companion object {
        /** Bodies are drawn larger than their physics radius so they read well on 8-inch screens. */
        const val BODY_SCALE = 1.3f
        const val GOALIE_SCALE = 1.2f
        private const val MAX_SPRAY = 240
    }

    private val rinkRect = RectF(-Rink.HALF_L, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
    private val rinkPath = Path().apply { addRoundRect(rinkRect, Rink.CORNER_R, Rink.CORNER_R, Path.Direction.CW) }
    private val tmpRect = RectF()
    private val tmpPath = Path()

    private var crowd: Bitmap? = null
    private val crowdRect = RectF(-Camera.WORLD_HALF_W, -Camera.WORLD_HALF_H, Camera.WORLD_HALF_W, Camera.WORLD_HALF_H)
    private var animTime = 0f

    // ----- paints (world space, stroke widths in feet)
    private val icePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EDF4F9") }
    private val iceShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DCE8F1") }
    private val redLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.6f }
    private val blueLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F5FBF"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val centerLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val circleRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.28f }
    private val circleBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F5FBF"); style = Paint.Style.STROKE; strokeWidth = 0.28f }
    private val dotRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D") }
    private val dotBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F5FBF") }
    private val creaseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#BFE0F5") }
    private val trapezoid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.2f }
    private val netFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9DEE3") }
    private val netMesh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA3AD"); style = Paint.Style.STROKE; strokeWidth = 0.08f }
    private val netFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = 0.35f; strokeCap = Paint.Cap.ROUND }
    private val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E23B4F") }
    private val kickPlate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4C542"); style = Paint.Style.STROKE; strokeWidth = 0.9f }
    private val boardsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.STROKE; strokeWidth = 2.2f }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7DA6C9"); style = Paint.Style.STROKE; strokeWidth = 0.7f; alpha = 190 }
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 7f }
    private val faceoffPulse = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24"); style = Paint.Style.STROKE; strokeWidth = 0.4f }

    // ----- player paints
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 10, 20, 40) }
    private val torsoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.16f; color = Color.parseColor("#0F172A") }
    private val yokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val yokeThin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#F8FAFC") }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val armOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#0F172A") }
    private val sleevePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val helmetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val helmetOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f; color = Color.parseColor("#0B1220") }
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 125, 211, 252) }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255) }
    private val glovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloveCuff = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloveOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.09f; color = Color.parseColor("#0B1220") }
    private val bootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827") }
    private val bootLacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#CBD5E1") }
    private val skateBladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#B8C6D4") }
    private val shaftDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#2B1D12") }
    private val shaftCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#A0642F") }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#F2F2F2") }
    private val bladeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#111111") }
    private val bladeTape = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#E8E8E8") }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3F4F6") }
    private val padOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f; color = Color.parseColor("#1F2937") }
    private val padStripe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8B5A2B") }
    private val leatherLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C58B4A") }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.06f; color = Color.parseColor("#111827") }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.14f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#FDE047") }
    private val sprayOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8FB3CC") }
    private val sprayInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 1.7f }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f; color = Color.parseColor("#FDE047") }
    private val ringGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 253, 224, 71) }
    private val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0A") }
    private val puckRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A3A"); style = Paint.Style.STROKE; strokeWidth = 0.12f }
    private val puckHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val puckTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 20, 20, 20); style = Paint.Style.STROKE; strokeWidth = 0.6f; strokeCap = Paint.Cap.ROUND }
    private val meterBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 0.5f }
    private val meterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F97316"); style = Paint.Style.STROKE; strokeWidth = 0.5f; strokeCap = Paint.Cap.ROUND }

    // ----- HUD paints (screen space)
    private val hudBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 8, 14, 26) }
    private val hudText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val hudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textAlign = Paint.Align.CENTER }
    private val hudTeamBox = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bannerBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 5, 10, 20) }
    private val bannerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val bannerSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDE68A"); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val ctrlBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val ctrlRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255); style = Paint.Style.STROKE }
    private val ctrlKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val btnFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val chargeArc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDE047"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    // ----- per-team cached jersey shading
    private val shaderColor = IntArray(2)
    private val skaterShader = arrayOfNulls<RadialGradient>(2)
    private val goalieShader = arrayOfNulls<RadialGradient>(2)
    private val helmetColor = IntArray(2)
    private val gloveColor = IntArray(2)

    // ----- snow spray particles + per-skater motion memory
    private val sprayX = FloatArray(MAX_SPRAY)
    private val sprayY = FloatArray(MAX_SPRAY)
    private val sprayVx = FloatArray(MAX_SPRAY)
    private val sprayVy = FloatArray(MAX_SPRAY)
    private val sprayLife = FloatArray(MAX_SPRAY)
    private val sprayMax = FloatArray(MAX_SPRAY)
    private var sprayHead = 0
    private val sprayRng = Random(11)
    private val emaVx = FloatArray(12)
    private val emaVy = FloatArray(12)
    private val sprayCooldown = FloatArray(12)
    private val wasStunned = BooleanArray(12)

    fun resize(w: Int, h: Int) {
        camera.resize(w, h)
        buildCrowd()
    }

    private fun buildCrowd() {
        val pxPerFt = 5f
        val bw = (crowdRect.width() * pxPerFt).toInt().coerceAtLeast(8)
        val bh = (crowdRect.height() * pxPerFt).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0B1424"))
        val rng = Random(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val palette = intArrayOf(
            Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.parseColor("#7F1D1D"), Color.parseColor("#1E3A8A"),
            Color.parseColor("#374151"), Color.parseColor("#4B5563"), Color.parseColor("#9A3412"), Color.parseColor("#14532D"),
            Color.parseColor("#F1F5F9"), Color.parseColor("#FDE68A")
        )
        // Rows of seats radiating outwards from the boards.
        c.translate(bw / 2f, bh / 2f)
        c.scale(pxPerFt, pxPerFt)
        val stepRow = 1.6f
        val stepSeat = 1.15f
        var ring = Rink.HALF_W + 3.5f
        var row = 0
        while (ring < Camera.WORLD_HALF_H + 1f) {
            val halfL = Rink.HALF_L + 3.5f + row * stepRow
            val halfW = ring
            val shade = 1f - row * 0.07f
            var sx = -halfL
            while (sx <= halfL) {
                for (sy in floatArrayOf(-halfW, halfW)) {
                    p.color = shadeColor(palette[rng.nextInt(palette.size)], shade)
                    c.drawCircle(sx + rng.nextFloat() * 0.3f, sy + rng.nextFloat() * 0.3f, 0.48f, p)
                }
                sx += stepSeat
            }
            var sy = -halfW
            while (sy <= halfW) {
                for (sx2 in floatArrayOf(-halfL, halfL)) {
                    p.color = shadeColor(palette[rng.nextInt(palette.size)], shade)
                    c.drawCircle(sx2 + rng.nextFloat() * 0.3f, sy + rng.nextFloat() * 0.3f, 0.48f, p)
                }
                sy += stepSeat
            }
            ring += stepRow
            row++
        }
        // Dark walkway right behind the glass.
        p.color = Color.parseColor("#0F1B2E")
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawRoundRect(RectF(-Rink.HALF_L - 2.5f, -Rink.HALF_W - 2.5f, Rink.HALF_L + 2.5f, Rink.HALF_W + 2.5f), Rink.CORNER_R + 2.5f, Rink.CORNER_R + 2.5f, p)
        crowd?.recycle()
        crowd = bmp
    }

    private fun shadeColor(color: Int, f: Float): Int {
        val r = (Color.red(color) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lighten(color: Int, f: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * f).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * f).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun ensureTeamShaders(world: World) {
        for (t in 0..1) {
            val primary = world.teams[t].info.primary
            if (shaderColor[t] == primary && skaterShader[t] != null) continue
            shaderColor[t] = primary
            val colors = intArrayOf(lighten(primary, 0.42f), primary, shadeColor(primary, 0.68f))
            val stops = floatArrayOf(0f, 0.55f, 1f)
            skaterShader[t] = RadialGradient(-0.2f, -0.25f, 1.5f * 1.15f, colors, stops, Shader.TileMode.CLAMP)
            goalieShader[t] = RadialGradient(-0.3f, -0.3f, 2.1f * 1.25f, colors, stops, Shader.TileMode.CLAMP)
            helmetColor[t] = shadeColor(primary, 0.55f)
            gloveColor[t] = shadeColor(primary, 0.45f)
        }
    }

    // ================================================================ frame

    fun draw(canvas: Canvas, world: World, localTeam: Int, controls: TouchControls?, dt: Float) {
        animTime += dt
        ensureTeamShaders(world)
        updateSpray(world, dt)

        canvas.drawColor(Color.parseColor("#0B1424"))
        canvas.save()
        camera.apply(canvas)
        crowd?.let { canvas.drawBitmap(it, null, crowdRect, null) }
        drawRink(canvas, world)
        if (world.phase == Phase.FACEOFF) {
            val r = 2.2f + 0.6f * sin(animTime * 8f)
            faceoffPulse.alpha = 200
            canvas.drawCircle(world.faceoffX, world.faceoffY, r, faceoffPulse)
        }
        for (s in world.allSkaters) drawShadow(canvas, s)
        drawSpray(canvas)
        val sorted = world.allSkaters.sortedBy { it.y }
        val controlled = if (localTeam >= 0) world.controlledSkater(localTeam) else null
        for (s in sorted) {
            drawSkater(canvas, world, s, s === controlled, if (localTeam >= 0) world.shotCharge[localTeam] else 0f)
        }
        drawPuck(canvas, world.puck)
        canvas.restore()

        drawScoreboard(canvas, world)
        drawBanner(canvas, world)
        if (controls != null) drawControls(canvas, world, localTeam, controls)
        drawPauseButton(canvas)
    }

    // ================================================================ rink

    private fun drawRink(canvas: Canvas, world: World) {
        canvas.drawPath(rinkPath, boardsPaint)
        canvas.drawPath(rinkPath, icePaint)
        canvas.save()
        canvas.clipPath(rinkPath)
        // Faint zone shading toward the ends.
        tmpRect.set(-Rink.HALF_L, -Rink.HALF_W, -Rink.GOAL_LINE_X, Rink.HALF_W)
        canvas.drawRect(tmpRect, iceShadePaint)
        tmpRect.set(Rink.GOAL_LINE_X, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
        canvas.drawRect(tmpRect, iceShadePaint)

        // Goal lines, blue lines, centre line.
        canvas.drawLine(-Rink.GOAL_LINE_X, -Rink.HALF_W, -Rink.GOAL_LINE_X, Rink.HALF_W, redLine)
        canvas.drawLine(Rink.GOAL_LINE_X, -Rink.HALF_W, Rink.GOAL_LINE_X, Rink.HALF_W, redLine)
        canvas.drawLine(-Rink.BLUE_LINE_X, -Rink.HALF_W, -Rink.BLUE_LINE_X, Rink.HALF_W, blueLine)
        canvas.drawLine(Rink.BLUE_LINE_X, -Rink.HALF_W, Rink.BLUE_LINE_X, Rink.HALF_W, blueLine)
        canvas.drawLine(0f, -Rink.HALF_W, 0f, Rink.HALF_W, centerLine)

        // Centre ice logo + circle.
        logoPaint.color = world.teams[0].info.primary
        logoPaint.alpha = 60
        canvas.drawCircle(0f, 0f, 10f, logoPaint)
        logoText.color = world.teams[0].info.primary
        logoText.alpha = 120
        canvas.drawText(world.teams[0].info.abbr, 0f, 2.6f, logoText)
        canvas.drawCircle(0f, 0f, Rink.FACEOFF_R, circleBlue)
        canvas.drawCircle(0f, 0f, 1f, dotBlue)

        // Faceoff circles / dots.
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sy in floatArrayOf(-1f, 1f)) {
                val cx = sx * Rink.END_DOT_X
                val cy = sy * Rink.DOT_Y
                canvas.drawCircle(cx, cy, Rink.FACEOFF_R, circleRed)
                canvas.drawCircle(cx, cy, 1f, dotRed)
                // hash marks
                canvas.drawLine(cx - 3f, cy - Rink.FACEOFF_R - 2f, cx - 3f, cy - Rink.FACEOFF_R, circleRed)
                canvas.drawLine(cx + 3f, cy - Rink.FACEOFF_R - 2f, cx + 3f, cy - Rink.FACEOFF_R, circleRed)
                canvas.drawLine(cx - 3f, cy + Rink.FACEOFF_R, cx - 3f, cy + Rink.FACEOFF_R + 2f, circleRed)
                canvas.drawLine(cx + 3f, cy + Rink.FACEOFF_R, cx + 3f, cy + Rink.FACEOFF_R + 2f, circleRed)
                canvas.drawCircle(sx * Rink.NEUTRAL_DOT_X, cy, 1f, dotRed)
            }
        }

        // Creases, trapezoids and nets at both ends.
        for (e in floatArrayOf(-1f, 1f)) {
            val gx = e * Rink.GOAL_LINE_X
            tmpRect.set(gx - Rink.CREASE_R, -Rink.CREASE_R, gx + Rink.CREASE_R, Rink.CREASE_R)
            val start = if (e > 0f) 90f else -90f
            canvas.drawArc(tmpRect, start, 180f, true, creaseFill)
            canvas.drawArc(tmpRect, start, 180f, false, circleRed)
            // trapezoid
            canvas.drawLine(gx, -11f, e * Rink.HALF_L, -14f, trapezoid)
            canvas.drawLine(gx, 11f, e * Rink.HALF_L, 14f, trapezoid)
            // net
            val backX = e * (Rink.GOAL_LINE_X + Rink.NET_DEPTH)
            tmpRect.set(min(gx, backX), -Rink.NET_HALF_W, max(gx, backX), Rink.NET_HALF_W)
            canvas.drawRect(tmpRect, netFill)
            var mx = tmpRect.left
            while (mx <= tmpRect.right) { canvas.drawLine(mx, tmpRect.top, mx, tmpRect.bottom, netMesh); mx += 0.6f }
            var my = tmpRect.top
            while (my <= tmpRect.bottom) { canvas.drawLine(tmpRect.left, my, tmpRect.right, my, netMesh); my += 0.6f }
            tmpPath.reset()
            tmpPath.moveTo(gx, -Rink.NET_HALF_W)
            tmpPath.lineTo(backX, -Rink.NET_HALF_W)
            tmpPath.lineTo(backX, Rink.NET_HALF_W)
            tmpPath.lineTo(gx, Rink.NET_HALF_W)
            canvas.drawPath(tmpPath, netFrame)
            canvas.drawCircle(gx, -Rink.GOAL_HALF_W, Rink.POST_R, postPaint)
            canvas.drawCircle(gx, Rink.GOAL_HALF_W, Rink.POST_R, postPaint)
        }
        canvas.restore()

        // Boards: kick plate inside, glass outside.
        canvas.drawPath(rinkPath, kickPlate)
        tmpRect.set(rinkRect)
        tmpRect.inset(-1.1f, -1.1f)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, Rink.CORNER_R + 1.1f, Rink.CORNER_R + 1.1f, Path.Direction.CW)
        canvas.drawPath(tmpPath, boardsPaint)
        tmpRect.inset(-1.4f, -1.4f)
        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, Rink.CORNER_R + 2.5f, Rink.CORNER_R + 2.5f, Path.Direction.CW)
        canvas.drawPath(tmpPath, glassPaint)
    }

    // ================================================================ spray

    private fun updateSpray(world: World, dt: Float) {
        for (i in 0 until MAX_SPRAY) {
            if (sprayLife[i] <= 0f) continue
            sprayLife[i] -= dt
            sprayX[i] += sprayVx[i] * dt
            sprayY[i] += sprayVy[i] * dt
            val f = exp(-dt * 6f)
            sprayVx[i] *= f
            sprayVy[i] *= f
        }
        val k = 1f - exp(-dt / 0.12f)
        val skaters = world.allSkaters
        for (i in skaters.indices) {
            if (i >= emaVx.size) break
            val s = skaters[i]
            val dvx = s.vx - emaVx[i]
            val dvy = s.vy - emaVy[i]
            val dv = hypot(dvx, dvy)
            sprayCooldown[i] = max(0f, sprayCooldown[i] - dt)
            val stunned = s.stunTimer > 0f
            val knocked = stunned && !wasStunned[i]
            if ((dv > 9f && sprayCooldown[i] <= 0f) || knocked) {
                // Snow flies along the old direction of travel, like a hockey stop.
                val dirX = if (dv > 0.01f) -dvx / dv else cos(s.facing)
                val dirY = if (dv > 0.01f) -dvy / dv else sin(s.facing)
                val px = -sin(s.facing) * 0.9f
                val py = cos(s.facing) * 0.9f
                val count = if (knocked) 7 else 5
                val speed = 7f + min(dv, 30f) * 0.35f
                spawnSpray(s.x + px, s.y + py, dirX, dirY, count, speed)
                spawnSpray(s.x - px, s.y - py, dirX, dirY, count, speed)
                sprayCooldown[i] = 0.22f
            }
            wasStunned[i] = stunned
            emaVx[i] += dvx * k
            emaVy[i] += dvy * k
        }
    }

    private fun spawnSpray(x: Float, y: Float, dirX: Float, dirY: Float, count: Int, speed: Float) {
        for (n in 0 until count) {
            val i = sprayHead
            sprayHead = (sprayHead + 1) % MAX_SPRAY
            val ang = (sprayRng.nextFloat() - 0.5f) * 2.1f
            val c = cos(ang)
            val sn = sin(ang)
            val sp = speed * (0.45f + sprayRng.nextFloat() * 0.8f)
            sprayX[i] = x
            sprayY[i] = y
            sprayVx[i] = (dirX * c - dirY * sn) * sp
            sprayVy[i] = (dirX * sn + dirY * c) * sp
            sprayMax[i] = 0.28f + sprayRng.nextFloat() * 0.22f
            sprayLife[i] = sprayMax[i]
        }
    }

    private fun drawSpray(canvas: Canvas) {
        for (i in 0 until MAX_SPRAY) {
            val life = sprayLife[i]
            if (life <= 0f) continue
            val t = (life / sprayMax[i]).coerceIn(0f, 1f)
            val rad = 0.22f + (1f - t) * 0.28f
            sprayOuter.alpha = (170 * t).toInt()
            sprayInner.alpha = (230 * t).toInt()
            canvas.drawCircle(sprayX[i], sprayY[i], rad, sprayOuter)
            canvas.drawCircle(sprayX[i], sprayY[i], rad * 0.55f, sprayInner)
        }
    }

    // ================================================================ players

    private fun drawShadow(canvas: Canvas, s: Skater) {
        val r = s.radius * 1.15f * BODY_SCALE
        tmpRect.set(s.x - r + 0.3f, s.y - r * 0.8f + 0.5f, s.x + r + 0.3f, s.y + r * 0.8f + 0.5f)
        canvas.drawOval(tmpRect, shadowPaint)
    }

    private fun drawSkater(canvas: Canvas, world: World, s: Skater, controlled: Boolean, charge: Float) {
        val info = world.teams[s.team].info
        val r = s.radius
        if (controlled) {
            val pulse = 1f + 0.06f * sin(animTime * 6f)
            canvas.drawCircle(s.x, s.y, r * 2.2f * pulse, ringGlow)
            canvas.drawCircle(s.x, s.y, r * 2.2f * pulse, ringPaint)
        }
        canvas.save()
        canvas.translate(s.x, s.y)
        canvas.rotate(Math.toDegrees(s.facing.toDouble()).toFloat())
        val stunned = s.stunTimer > 0f
        if (stunned) {
            // Flat on the ice.
            canvas.rotate(90f)
            canvas.scale(1.45f, 0.72f)
        }
        if (s.isGoalie) drawGoalieBody(canvas, s, info, goalieShader[s.team]!!)
        else drawSkaterBody(canvas, s, info, skaterShader[s.team]!!)
        canvas.restore()

        // Upright number on the shoulders.
        numberPaint.color = info.text
        numberPaint.textSize = if (s.isGoalie) 2.1f else 2.0f
        canvas.drawText(s.number.toString(), s.x - cos(s.facing) * 0.5f, s.y - sin(s.facing) * 0.5f + 0.7f, numberPaint)

        if (stunned) drawDizzyStars(canvas, s)

        if (controlled && charge > 0f && world.puck.carrier === s) {
            tmpRect.set(s.x - r * 1.9f, s.y - r * 1.9f, s.x + r * 1.9f, s.y + r * 1.9f)
            canvas.drawArc(tmpRect, -210f, 240f, false, meterBack)
            canvas.drawArc(tmpRect, -210f, 240f * charge, false, meterFill)
        }
    }

    private fun drawDizzyStars(canvas: Canvas, s: Skater) {
        val r = s.radius
        for (i in 0..2) {
            val a = animTime * 5f + i * 2.094f
            val sx = s.x + cos(a) * r * 1.5f
            val sy = s.y + sin(a) * r * 0.8f - r * 1.1f
            val k = 0.32f
            canvas.drawLine(sx - k, sy, sx + k, sy, starPaint)
            canvas.drawLine(sx, sy - k, sx, sy + k, starPaint)
            canvas.drawLine(sx - k * 0.6f, sy - k * 0.6f, sx + k * 0.6f, sy + k * 0.6f, starPaint)
            canvas.drawLine(sx - k * 0.6f, sy + k * 0.6f, sx + k * 0.6f, sy - k * 0.6f, starPaint)
        }
    }

    /** Draws the part of the segment between parameters [t0] and [t1]. */
    private fun drawSegmentPortion(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, t0: Float, t1: Float, paint: Paint) {
        canvas.drawLine(x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1, paint)
    }

    private fun drawGlove(canvas: Canvas, x: Float, y: Float, size: Float, team: Int, info: TeamInfo) {
        glovePaint.color = gloveColor[team]
        tmpRect.set(x - size, y - size * 0.85f, x + size, y + size * 0.85f)
        canvas.drawRoundRect(tmpRect, size * 0.45f, size * 0.45f, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.45f, size * 0.45f, gloveOutline)
        gloveCuff.color = info.secondary
        tmpRect.set(x - size * 0.95f, y - size * 0.8f, x - size * 0.45f, y + size * 0.8f)
        canvas.drawRoundRect(tmpRect, size * 0.25f, size * 0.25f, gloveCuff)
    }

    private fun drawSkaterBody(canvas: Canvas, s: Skater, info: TeamInfo, shader: Shader) {
        val r = s.radius
        canvas.scale(BODY_SCALE, BODY_SCALE)
        val moving = s.speed > 2f
        val stride = if (moving) sin(s.stride * 1.3f) else 0f
        val bladeLocal = s.stickReach / BODY_SCALE

        // Skates: the pushing foot swings back and splays outward.
        for (side in intArrayOf(-1, 1)) {
            val phase = stride * side
            val push = max(0f, -phase)
            val bx = -0.15f * r + phase * 0.42f * r
            val by = side * (0.72f * r + push * 0.4f * r)
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(side * (-6f + push * 32f))
            skateBladePaint.strokeWidth = 0.09f * r
            canvas.drawLine(-0.62f * r, 0.05f * r, 0.62f * r, 0.05f * r, skateBladePaint)
            tmpRect.set(-0.5f * r, -0.21f * r, 0.5f * r, 0.21f * r)
            canvas.drawRoundRect(tmpRect, 0.15f * r, 0.15f * r, bootPaint)
            bootLacePaint.strokeWidth = 0.05f * r
            canvas.drawLine(-0.25f * r, 0f, 0.32f * r, 0f, bootLacePaint)
            canvas.restore()
        }

        // Stick and arms; the whole group swings on a shot or pass.
        var kick = 0f
        if (s.swingTimer > 0f) {
            val t = 1f - s.swingTimer / 0.35f
            kick = if (t < 0.4f) -55f * (t / 0.4f) else -55f + 110f * ((t - 0.4f) / 0.6f)
        }
        canvas.save()
        canvas.rotate(kick)
        val bX = 0.2f * r
        val bY = -0.95f * r
        val hX = bladeLocal - 0.5f
        val hY = 0.3f
        var dX = hX - bX
        var dY = hY - bY
        val len = hypot(dX, dY).coerceAtLeast(0.01f)
        dX /= len; dY /= len
        val tX = bX + dX * 0.3f * r
        val tY = bY + dY * 0.3f * r
        val uDist = min(1.3f * r, len * 0.55f)
        val uX = bX + dX * uDist
        val uY = bY + dY * uDist
        val s1X = -0.15f * r; val s1Y = -0.8f * r
        val s2X = -0.15f * r; val s2Y = 0.8f * r
        // Arms with sleeve stripes.
        armOutline.strokeWidth = 0.62f * r
        canvas.drawLine(s1X, s1Y, tX, tY, armOutline)
        canvas.drawLine(s2X, s2Y, uX, uY, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.5f * r
        canvas.drawLine(s1X, s1Y, tX, tY, armPaint)
        canvas.drawLine(s2X, s2Y, uX, uY, armPaint)
        sleevePaint.color = info.secondary
        sleevePaint.strokeWidth = 0.5f * r
        drawSegmentPortion(canvas, s1X, s1Y, tX, tY, 0.42f, 0.6f, sleevePaint)
        drawSegmentPortion(canvas, s2X, s2Y, uX, uY, 0.42f, 0.6f, sleevePaint)
        // Shaft with grip tape.
        shaftDark.strokeWidth = 0.2f * r
        canvas.drawLine(bX, bY, hX, hY, shaftDark)
        shaftCore.strokeWidth = 0.08f * r
        canvas.drawLine(bX, bY, hX, hY, shaftCore)
        tapePaint.strokeWidth = 0.22f * r
        canvas.drawLine(bX, bY, bX + dX * 0.4f * r, bY + dY * 0.4f * r, tapePaint)
        // Curved, taped blade.
        tmpPath.reset()
        tmpPath.moveTo(hX, hY)
        tmpPath.quadTo(bladeLocal + 0.05f, 0.42f, bladeLocal + 0.5f, -0.25f)
        bladeOutline.strokeWidth = 0.46f
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.3f
        canvas.drawPath(tmpPath, bladeTape)
        // Gloves on the shaft.
        drawGlove(canvas, tX, tY, 0.4f * r, s.team, info)
        drawGlove(canvas, uX, uY, 0.4f * r, s.team, info)
        canvas.restore()

        // Torso with shaded jersey, shoulder pads and yoke stripes.
        torsoPaint.shader = shader
        tmpRect.set(-0.95f * r, -1.1f * r, 0.8f * r, 1.1f * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.15f * r, -0.95f * r, 0.36f * r, torsoPaint)
        canvas.drawCircle(-0.15f * r, 0.95f * r, 0.36f * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.3f * r
        canvas.drawLine(-0.5f * r, -0.95f * r, -0.5f * r, 0.95f * r, yokePaint)
        yokeThin.strokeWidth = 0.08f * r
        canvas.drawLine(-0.74f * r, -0.82f * r, -0.74f * r, 0.82f * r, yokeThin)

        // Helmet with visor.
        helmetPaint.color = helmetColor[s.team]
        val hx = 0.28f * r
        val hr = 0.58f * r
        canvas.drawCircle(hx, 0f, hr, helmetPaint)
        canvas.drawCircle(hx, 0f, hr, helmetOutline)
        tmpRect.set(hx - hr, -hr, hx + hr, hr)
        canvas.drawArc(tmpRect, -50f, 100f, false, visorPaint)
        canvas.drawCircle(hx + 0.15f * r, -0.2f * r, 0.15f * r, glossPaint)
    }

    private fun drawGoalieBody(canvas: Canvas, g: Skater, info: TeamInfo, shader: Shader) {
        val r = g.radius
        canvas.scale(GOALIE_SCALE, GOALIE_SCALE)
        val bladeLocal = g.stickReach / GOALIE_SCALE
        padStripe.color = info.primary

        // Leg pads: stacked flat across the crease in the butterfly, upright otherwise.
        if (g.butterfly) {
            tmpRect.set(-0.35f * r, -1.6f * r, 0.9f * r, 1.6f * r)
            canvas.drawRoundRect(tmpRect, 0.35f * r, 0.35f * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.35f * r, 0.35f * r, padOutline)
            for (yy in floatArrayOf(-0.8f, 0.8f)) {
                tmpRect.set(-0.3f * r, yy * r - 0.14f * r, 0.85f * r, yy * r + 0.14f * r)
                canvas.drawRect(tmpRect, padStripe)
            }
            canvas.drawLine(-0.3f * r, 0f, 0.85f * r, 0f, padOutline)
        } else {
            for (side in intArrayOf(-1, 1)) {
                val inner = side * 0.32f * r
                val outer = side * 1.12f * r
                tmpRect.set(-0.7f * r, min(inner, outer), 0.72f * r, max(inner, outer))
                canvas.drawRoundRect(tmpRect, 0.3f * r, 0.3f * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.3f * r, 0.3f * r, padOutline)
                tmpRect.set(-0.2f * r, min(inner, outer) + 0.08f * r, 0.08f * r, max(inner, outer) - 0.08f * r)
                canvas.drawRect(tmpRect, padStripe)
            }
        }

        // Goalie stick: paddle down to a wide blade on the ice.
        shaftDark.strokeWidth = 0.34f * r
        canvas.drawLine(0.45f * r, 1.0f * r, bladeLocal - 0.9f, 0.85f, shaftDark)
        shaftCore.strokeWidth = 0.12f * r
        canvas.drawLine(0.45f * r, 1.0f * r, bladeLocal - 0.9f, 0.85f, shaftCore)
        tmpPath.reset()
        tmpPath.moveTo(bladeLocal - 0.95f, 0.9f)
        tmpPath.quadTo(bladeLocal, 0.7f, bladeLocal + 0.5f, 0.15f)
        bladeOutline.strokeWidth = 0.55f
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.38f
        canvas.drawPath(tmpPath, bladeTape)

        // Arms.
        armOutline.strokeWidth = 0.68f * r
        canvas.drawLine(-0.1f * r, -0.9f * r, 0.8f * r, -1.15f * r, armOutline)
        canvas.drawLine(-0.1f * r, 0.9f * r, 0.75f * r, 1.05f * r, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.55f * r
        canvas.drawLine(-0.1f * r, -0.9f * r, 0.8f * r, -1.15f * r, armPaint)
        canvas.drawLine(-0.1f * r, 0.9f * r, 0.75f * r, 1.05f * r, armPaint)

        // Chest protector under the jersey.
        torsoPaint.shader = shader
        tmpRect.set(-1.0f * r, -1.2f * r, 0.85f * r, 1.2f * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.2f * r, -1.05f * r, 0.4f * r, torsoPaint)
        canvas.drawCircle(-0.2f * r, 1.05f * r, 0.4f * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.32f * r
        canvas.drawLine(-0.5f * r, -1.0f * r, -0.5f * r, 1.0f * r, yokePaint)

        // Catching glove (trapper) and blocker.
        canvas.drawCircle(0.95f * r, -1.25f * r, 0.52f * r, leatherPaint)
        canvas.drawCircle(0.95f * r, -1.25f * r, 0.52f * r, gloveOutline)
        canvas.drawCircle(1.02f * r, -1.3f * r, 0.3f * r, leatherLight)
        canvas.drawLine(0.7f * r, -1.25f * r, 1.2f * r, -1.25f * r, cagePaint)
        canvas.save()
        canvas.translate(0.95f * r, 1.15f * r)
        canvas.rotate(-15f)
        tmpRect.set(-0.36f * r, -0.5f * r, 0.36f * r, 0.5f * r)
        canvas.drawRoundRect(tmpRect, 0.12f * r, 0.12f * r, padPaint)
        canvas.drawRoundRect(tmpRect, 0.12f * r, 0.12f * r, gloveOutline)
        tmpRect.set(-0.36f * r, -0.1f * r, 0.36f * r, 0.1f * r)
        canvas.drawRect(tmpRect, padStripe)
        canvas.restore()

        // Mask with cage.
        maskPaint.color = info.secondary
        val mx = 0.32f * r
        val mr = 0.62f * r
        canvas.drawCircle(mx, 0f, mr, maskPaint)
        canvas.drawCircle(mx, 0f, mr, helmetOutline)
        for (yy in floatArrayOf(-0.28f, 0f, 0.28f)) {
            canvas.drawLine(mx + 0.15f * r, yy * r, mx + 0.58f * r, yy * r * 0.8f, cagePaint)
        }
        canvas.drawLine(mx + 0.28f * r, -0.36f * r, mx + 0.28f * r, 0.36f * r, cagePaint)
        canvas.drawLine(mx + 0.46f * r, -0.3f * r, mx + 0.46f * r, 0.3f * r, cagePaint)
        canvas.drawCircle(mx + 0.05f * r, -0.28f * r, 0.13f * r, glossPaint)
    }

    private fun drawPuck(canvas: Canvas, p: Puck) {
        val sp = p.speed
        if (p.carrier == null && sp > 35f) {
            val len = min(4f, sp / 25f)
            canvas.drawLine(p.x, p.y, p.x - p.vx / sp * len, p.y - p.vy / sp * len, puckTrail)
        }
        canvas.drawCircle(p.x + 0.15f, p.y + 0.2f, 0.85f, shadowPaint)
        canvas.drawCircle(p.x, p.y, 0.95f, puckHalo)
        canvas.drawCircle(p.x, p.y, 0.8f, puckPaint)
        canvas.drawCircle(p.x, p.y, 0.55f, puckRim)
    }

    // ================================================================ HUD

    private fun dp(v: Float) = v * density

    private fun drawScoreboard(canvas: Canvas, w: World) {
        val cx = camera.screenW / 2f
        val width = dp(330f)
        val height = dp(46f)
        val top = dp(8f)
        tmpRect.set(cx - width / 2f, top, cx + width / 2f, top + height)
        canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), hudBack)

        val boxW = dp(58f)
        hudTeamBox.color = w.teams[0].info.primary
        tmpRect.set(cx - width / 2f + dp(4f), top + dp(4f), cx - width / 2f + dp(4f) + boxW, top + height - dp(4f))
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), hudTeamBox)
        hudText.textSize = dp(17f)
        hudText.color = w.teams[0].info.text
        canvas.drawText(w.teams[0].info.abbr, tmpRect.centerX(), tmpRect.centerY() + dp(6f), hudText)

        hudTeamBox.color = w.teams[1].info.primary
        tmpRect.set(cx + width / 2f - dp(4f) - boxW, top + dp(4f), cx + width / 2f - dp(4f), top + height - dp(4f))
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), hudTeamBox)
        hudText.color = w.teams[1].info.text
        canvas.drawText(w.teams[1].info.abbr, tmpRect.centerX(), tmpRect.centerY() + dp(6f), hudText)

        hudText.color = Color.WHITE
        hudText.textSize = dp(24f)
        canvas.drawText(w.teams[0].score.toString(), cx - dp(92f), top + dp(33f), hudText)
        canvas.drawText(w.teams[1].score.toString(), cx + dp(92f), top + dp(33f), hudText)

        hudText.textSize = dp(19f)
        canvas.drawText(w.clockText(), cx, top + dp(23f), hudText)
        hudSmall.textSize = dp(11f)
        canvas.drawText(w.periodText() + (if (w.overtime) "  SUDDEN DEATH" else "  PERIOD"), cx, top + dp(39f), hudSmall)

        hudSmall.textSize = dp(11f)
        canvas.drawText("SHOTS  " + w.teams[0].shots + " - " + w.teams[1].shots, cx, top + height + dp(14f), hudSmall)
    }

    private fun drawBanner(canvas: Canvas, w: World) {
        val text = w.banner ?: return
        val alpha = if (w.bannerTimer < 0.4f) (w.bannerTimer / 0.4f).coerceIn(0f, 1f) else 1f
        val cy = camera.screenH * 0.36f
        val h = dp(96f)
        bannerBack.alpha = (170 * alpha).toInt()
        tmpRect.set(0f, cy - h / 2f, camera.screenW.toFloat(), cy + h / 2f)
        canvas.drawRect(tmpRect, bannerBack)
        bannerText.textSize = dp(if (text.length > 12) 30f else 44f)
        bannerText.alpha = (255 * alpha).toInt()
        canvas.drawText(text, camera.screenW / 2f, cy + dp(if (w.bannerSub != null) 4f else 14f), bannerText)
        w.bannerSub?.let {
            bannerSub.textSize = dp(16f)
            bannerSub.alpha = (255 * alpha).toInt()
            canvas.drawText(it, camera.screenW / 2f, cy + dp(30f), bannerSub)
        }
    }

    private fun drawControls(canvas: Canvas, w: World, localTeam: Int, c: TouchControls) {
        // Joystick.
        val jr = c.joyRadius
        val ax = if (c.joyActive) c.joyAnchorX else c.joyRestX
        val ay = if (c.joyActive) c.joyAnchorY else c.joyRestY
        ctrlBase.alpha = if (c.joyActive) 90 else 45
        ctrlRing.strokeWidth = dp(2f)
        ctrlRing.alpha = if (c.joyActive) 160 else 80
        canvas.drawCircle(ax, ay, jr, ctrlBase)
        canvas.drawCircle(ax, ay, jr, ctrlRing)
        val kx = if (c.joyActive) c.joyKnobX else ax
        val ky = if (c.joyActive) c.joyKnobY else ay
        ctrlKnob.alpha = if (c.joyActive) 220 else 110
        canvas.drawCircle(kx, ky, jr * 0.42f, ctrlKnob)

        val hasPuck = localTeam >= 0 && w.puck.carrier != null && w.puck.carrier === w.controlledSkater(localTeam)
        drawButton(canvas, c.shootX, c.shootY, c.shootR, "SHOOT", if (hasPuck) "" else "poke", c.shootDown, Color.parseColor("#DC2626"))
        drawButton(canvas, c.passX, c.passY, c.passR, "PASS", if (hasPuck) "" else "switch", c.passDown, Color.parseColor("#2563EB"))
        drawButton(canvas, c.hitX, c.hitY, c.hitR, "HIT", "", c.hitDown, Color.parseColor("#D97706"))
        if (c.shootDown && hasPuck) {
            val charge = c.currentCharge()
            chargeArc.strokeWidth = dp(5f)
            tmpRect.set(c.shootX - c.shootR - dp(6f), c.shootY - c.shootR - dp(6f), c.shootX + c.shootR + dp(6f), c.shootY + c.shootR + dp(6f))
            canvas.drawArc(tmpRect, -90f, 360f * charge, false, chargeArc)
        }
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, sub: String, down: Boolean, color: Int) {
        btnFill.color = color
        btnFill.alpha = if (down) 230 else 120
        canvas.drawCircle(x, y, r, btnFill)
        ctrlRing.strokeWidth = dp(2f)
        ctrlRing.alpha = if (down) 255 else 150
        canvas.drawCircle(x, y, r, ctrlRing)
        btnText.textSize = r * 0.42f
        btnText.alpha = 255
        canvas.drawText(label, x, y + (if (sub.isEmpty()) r * 0.15f else r * 0.02f), btnText)
        if (sub.isNotEmpty()) {
            btnText.textSize = r * 0.26f
            btnText.alpha = 200
            canvas.drawText(sub, x, y + r * 0.42f, btnText)
        }
    }

    private fun drawPauseButton(canvas: Canvas) {
        val s = dp(40f)
        val x = camera.screenW - s - dp(10f)
        val y = dp(10f)
        tmpRect.set(x, y, x + s, y + s)
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), hudBack)
        hudText.textSize = dp(18f)
        hudText.color = Color.WHITE
        canvas.drawText("II", x + s / 2f, y + s * 0.68f, hudText)
    }

    fun isPauseHit(x: Float, y: Float): Boolean {
        val s = dp(40f)
        val px = camera.screenW - s - dp(10f)
        val py = dp(10f)
        return x >= px - dp(8f) && x <= px + s + dp(8f) && y >= py - dp(8f) && y <= py + s + dp(8f)
    }

    fun release() {
        crowd?.recycle()
        crowd = null
    }
}
