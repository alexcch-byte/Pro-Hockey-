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
import com.tablehockey.game.model.ArenaType
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

    // ----- Winter Pond paints
    private val pondIcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#98C2D1") }
    private val pondIceShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80AEC0") }
    private val pondBoardsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#442B18"); style = Paint.Style.STROKE; strokeWidth = 2.4f }
    private val pondKickPlate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#29170A"); style = Paint.Style.STROKE; strokeWidth = 0.9f }
    private val pondSnowCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EDF6F9"); style = Paint.Style.STROKE; strokeWidth = 1.3f; strokeCap = Paint.Cap.ROUND }
    private val pondCrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 225, 245, 255); style = Paint.Style.STROKE; strokeWidth = 0.16f }
    private val snowflakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val breathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(125, 235, 248, 255); style = Paint.Style.FILL }

    // ----- Shattered Glass paints
    private val glassShardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(175, 215, 242, 255); style = Paint.Style.FILL }
    private val glassShardEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 0.1f }
    private val glassSpiderwebPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 255, 255, 255); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 0.22f }
    private val glassSpiderwebFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 220, 245, 255); style = Paint.Style.FILL }

    // ----- player paints
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 10, 20, 40) }
    private val contactShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(125, 4, 8, 16) }
    private val torsoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.16f; color = Color.parseColor("#0F172A") }
    private val yokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val yokeThin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#F8FAFC") }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val armOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#0F172A") }
    private val sleevePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val helmetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val helmetOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f; color = Color.parseColor("#0B1220") }
    private val helmetVent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#070B14") }
    private val earGuardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B") }
    private val chinStrapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.06f; color = Color.parseColor("#0F172A") }
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 125, 211, 252) }
    private val visorGleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 0.06f }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255) }
    private val glovePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloveCuff = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gloveOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.09f; color = Color.parseColor("#0B1220") }
    private val gloveRollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.07f; color = Color.parseColor("#0F172A") }
    private val glovePalmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4B996") }
    private val pantsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827") }
    private val pantsOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.14f; color = Color.parseColor("#090D16") }
    private val pantsStripe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827") }
    private val bootLacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#CBD5E1") }
    private val holderWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
    private val runnerSteel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 0.07f; color = Color.parseColor("#E2E8F0") }
    private val runnerGlint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val skateBladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#B8C6D4") }
    private val shaftDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#2B1D12") }
    private val shaftCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#A0642F") }
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#F2F2F2") }
    private val bladeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#111111") }
    private val bladeTape = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#E8E8E8") }
    private val puckScuffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 20, 20, 20); style = Paint.Style.FILL }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F3F4F6") }
    private val padOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f; color = Color.parseColor("#1F2937") }
    private val padStripe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padCrease = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.09f; color = Color.parseColor("#9CA3AF") }
    private val padPlate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5E7EB") }
    private val blockerBevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D1D5DB") }
    private val trapperLace = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.06f; color = Color.parseColor("#FDE047") }
    private val leatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8B5A2B") }
    private val leatherLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C58B4A") }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.06f; color = Color.parseColor("#111827") }
    private val catEyeCage = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.07f; color = Color.parseColor("#CBD5E1") }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.14f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#FDE047") }
    private val sprayOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8FB3CC") }
    private val sprayInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 1.7f }
    private val numberShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 1.7f; color = Color.parseColor("#0B1220") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.3f; color = Color.parseColor("#FDE047") }
    private val ringGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 253, 224, 71) }
    private val puckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0A") }
    private val puckRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A3A"); style = Paint.Style.STROKE; strokeWidth = 0.12f }
    private val puckBevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#262626"); style = Paint.Style.STROKE; strokeWidth = 0.16f }
    private val puckKnurl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); style = Paint.Style.STROKE; strokeWidth = 0.08f }
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

    // ----- Shootout Shooter Selector
    private val switchShooterRect = RectF()
    private val switchShooterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 15, 23, 42) }
    private val switchShooterBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#F59E0B") }
    private val switchShooterTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24"); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val switchShooterSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    // ----- Ice wear & scratches (Zamboni reset)
    private val scratchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val MAX_SCRATCHES = 120
    private val scratchX1 = FloatArray(MAX_SCRATCHES)
    private val scratchY1 = FloatArray(MAX_SCRATCHES)
    private val scratchX2 = FloatArray(MAX_SCRATCHES)
    private val scratchY2 = FloatArray(MAX_SCRATCHES)
    private val scratchAlpha = IntArray(MAX_SCRATCHES)
    private val scratchWidth = FloatArray(MAX_SCRATCHES)
    private var scratchCount = 0
    private var scratchNext = 0
    private var lastPhase = Phase.FACEOFF

    // ----- Goal siren & beacon effects
    private val sirenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sirenBeam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ----- Puck speed & comet trail
    private val TRAIL_POINTS = 8
    private val puckTrailX = FloatArray(TRAIL_POINTS)
    private val puckTrailY = FloatArray(TRAIL_POINTS)
    private var puckTrailHead = 0
    private val cometTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cometGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // ----- Scoreboard penalty / empty net badge
    private val ppBadgeBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ppBadgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FBBF24"); style = Paint.Style.STROKE }
    private val ppBadgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    // ----- Fire effects & Shootout UI
    private val fireEmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shootoutDotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shootoutDotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f) }

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

    // ----- Falling Snow weather
    private val MAX_SNOW = 85
    private val snowX = FloatArray(MAX_SNOW)
    private val snowY = FloatArray(MAX_SNOW)
    private val snowVy = FloatArray(MAX_SNOW)
    private val snowVx = FloatArray(MAX_SNOW)
    private val snowR = FloatArray(MAX_SNOW)
    private val snowAlpha = IntArray(MAX_SNOW)
    private val snowSway = FloatArray(MAX_SNOW)
    private var snowInitialized = false

    // ----- Skater Breath Vapor
    private val MAX_BREATH = 40
    private val breathX = FloatArray(MAX_BREATH)
    private val breathY = FloatArray(MAX_BREATH)
    private val breathVx = FloatArray(MAX_BREATH)
    private val breathVy = FloatArray(MAX_BREATH)
    private val breathLife = FloatArray(MAX_BREATH)
    private val breathMax = FloatArray(MAX_BREATH)
    private val breathR = FloatArray(MAX_BREATH)
    private var breathHead = 0

    // ----- Shattered Glass Shards
    private val MAX_SHARDS = 50
    private val shardX = FloatArray(MAX_SHARDS)
    private val shardY = FloatArray(MAX_SHARDS)
    private val shardVx = FloatArray(MAX_SHARDS)
    private val shardVy = FloatArray(MAX_SHARDS)
    private val shardRot = FloatArray(MAX_SHARDS)
    private val shardVrot = FloatArray(MAX_SHARDS)
    private val shardLife = FloatArray(MAX_SHARDS)
    private val shardMax = FloatArray(MAX_SHARDS)
    private val shardSize = FloatArray(MAX_SHARDS)
    private var prevGlassTimer = 0f

    private var winterLandscape: Bitmap? = null

    fun resize(w: Int, h: Int) {
        camera.resize(w, h)
        buildCrowd()
        buildWinterLandscape()
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

    private fun buildWinterLandscape() {
        val pxPerFt = 5f
        val bw = (crowdRect.width() * pxPerFt).toInt().coerceAtLeast(8)
        val bh = (crowdRect.height() * pxPerFt).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#09101C"))
        val rng = Random(42)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        c.translate(bw / 2f, bh / 2f)
        c.scale(pxPerFt, pxPerFt)

        // Distant alpine mountain silhouettes
        val mountainPath = Path()
        mountainPath.moveTo(-Camera.WORLD_HALF_W - 5f, -Camera.WORLD_HALF_H)
        mountainPath.lineTo(-Camera.WORLD_HALF_W - 5f, -Camera.WORLD_HALF_H + 8f)
        var mx = -Camera.WORLD_HALF_W
        while (mx <= Camera.WORLD_HALF_W + 10f) {
            mountainPath.lineTo(mx, -Camera.WORLD_HALF_H + 4f + rng.nextFloat() * 7f)
            mx += 12f
        }
        mountainPath.lineTo(Camera.WORLD_HALF_W + 5f, -Camera.WORLD_HALF_H)
        mountainPath.close()
        p.color = Color.parseColor("#152438")
        p.style = Paint.Style.FILL
        c.drawPath(mountainPath, p)

        // Rolling snowdrifts surrounding the lake
        val snowColors = intArrayOf(
            Color.parseColor("#C3D9E9"),
            Color.parseColor("#D6E7F4"),
            Color.parseColor("#E7F3FA")
        )
        for (layer in 0..2) {
            p.color = snowColors[layer]
            val ringW = Rink.HALF_L + 2.5f + (2 - layer) * 5.5f
            val ringH = Rink.HALF_W + 2.5f + (2 - layer) * 5.5f
            val cr = Rink.CORNER_R + (2 - layer) * 5.5f
            c.drawRoundRect(RectF(-ringW, -ringH, ringW, ringH), cr, cr, p)
        }

        // Pine trees clustered in the snow around the rink perimeter
        val pineGreens = intArrayOf(
            Color.parseColor("#0F261B"),
            Color.parseColor("#173727"),
            Color.parseColor("#1F4B35")
        )
        val snowCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4FAFC"); style = Paint.Style.FILL }
        val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#341F10"); style = Paint.Style.FILL }

        // Ring of pine trees
        var angle = 0.0
        while (angle < Math.PI * 2) {
            val dist = Rink.HALF_W + 7f + rng.nextFloat() * 12f
            val tx = (cos(angle) * (Rink.HALF_L + 8f + rng.nextFloat() * 10f)).toFloat()
            val ty = (sin(angle) * dist).toFloat()
            if (kotlin.math.abs(tx) <= Camera.WORLD_HALF_W - 2f && kotlin.math.abs(ty) <= Camera.WORLD_HALF_H - 2f) {
                val treeScale = 0.85f + rng.nextFloat() * 0.65f
                c.drawRect(tx - 0.25f * treeScale, ty, tx + 0.25f * treeScale, ty + 1.2f * treeScale, trunkPaint)
                for (tier in 0..2) {
                    val tierY = ty - tier * 1.0f * treeScale
                    val tierW = (2.2f - tier * 0.55f) * treeScale
                    val treePath = Path()
                    treePath.moveTo(tx, tierY - 1.2f * treeScale)
                    treePath.lineTo(tx - tierW / 2f, tierY)
                    treePath.lineTo(tx + tierW / 2f, tierY)
                    treePath.close()
                    p.color = pineGreens[rng.nextInt(pineGreens.size)]
                    c.drawPath(treePath, p)

                    val capPath = Path()
                    capPath.moveTo(tx, tierY - 1.2f * treeScale)
                    capPath.lineTo(tx - tierW * 0.35f, tierY - 0.4f * treeScale)
                    capPath.lineTo(tx + tierW * 0.35f, tierY - 0.4f * treeScale)
                    capPath.close()
                    c.drawPath(capPath, snowCap)
                }
            }
            angle += 0.16 + rng.nextDouble() * 0.08
        }

        // Cozy rustic cabin glowing softly in the distance near corner
        val cabinX = -Rink.HALF_L - 8f
        val cabinY = -Rink.HALF_W - 7f
        p.color = Color.parseColor("#422A1A")
        c.drawRect(cabinX - 3.5f, cabinY - 2.5f, cabinX + 3.5f, cabinY + 2.5f, p)
        val roofPath = Path()
        roofPath.moveTo(cabinX - 4.2f, cabinY - 2.5f)
        roofPath.lineTo(cabinX, cabinY - 5.2f)
        roofPath.lineTo(cabinX + 4.2f, cabinY - 2.5f)
        roofPath.close()
        p.color = Color.parseColor("#EDF5FA")
        c.drawPath(roofPath, p)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL }
        c.drawRect(cabinX - 1.5f, cabinY - 0.5f, cabinX - 0.3f, cabinY + 0.9f, glowPaint)
        c.drawRect(cabinX + 0.3f, cabinY - 0.5f, cabinX + 1.5f, cabinY + 0.9f, glowPaint)

        winterLandscape?.recycle()
        winterLandscape = bmp
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
        updateSnow(dt)
        updateBreath(world, dt)
        updateGlassShards(world, dt)

        // Zamboni resurfacing between periods
        if (world.phase == Phase.PERIOD_END && lastPhase != Phase.PERIOD_END) {
            scratchCount = 0
            scratchNext = 0
        }
        lastPhase = world.phase

        // Accumulate subtle skate scratches during play
        if (world.phase == Phase.PLAY) {
            for (s in world.allSkaters) {
                if (s.speed > 11f && sprayRng.nextFloat() < 0.12f) {
                    val idx = scratchNext
                    scratchX1[idx] = s.x + (sprayRng.nextFloat() - 0.5f) * 0.8f
                    scratchY1[idx] = s.y + (sprayRng.nextFloat() - 0.5f) * 0.8f
                    val angle = s.facing + (sprayRng.nextFloat() - 0.5f) * 0.6f
                    val len = 1.0f + sprayRng.nextFloat() * 2.0f
                    scratchX2[idx] = scratchX1[idx] + cos(angle) * len
                    scratchY2[idx] = scratchY1[idx] + sin(angle) * len
                    scratchAlpha[idx] = 20 + sprayRng.nextInt(40)
                    scratchWidth[idx] = 0.08f + sprayRng.nextFloat() * 0.08f
                    scratchNext = (scratchNext + 1) % MAX_SCRATCHES
                    if (scratchCount < MAX_SCRATCHES) scratchCount++
                }
            }
        }

        val isPond = world.arenaType == ArenaType.WINTER_POND
        canvas.drawColor(if (isPond) Color.parseColor("#09101C") else Color.parseColor("#0B1424"))
        canvas.save()
        camera.apply(canvas)
        if (isPond) {
            winterLandscape?.let { canvas.drawBitmap(it, null, crowdRect, null) }
        } else {
            crowd?.let { canvas.drawBitmap(it, null, crowdRect, null) }
        }
        drawRink(canvas, world)
        if (world.phase == Phase.FACEOFF) {
            val r = 2.2f + 0.6f * sin(animTime * 8f)
            faceoffPulse.alpha = 200
            canvas.drawCircle(world.faceoffX, world.faceoffY, r, faceoffPulse)
        }
        for (s in world.allSkaters) {
            if (!world.isShootout || kotlin.math.abs(s.y) < 45f) drawShadow(canvas, s)
        }
        drawSpray(canvas)
        val sorted = world.allSkaters.filter { !world.isShootout || kotlin.math.abs(it.y) < 45f }.sortedBy { it.y }
        val controlled = if (localTeam >= 0) world.controlledSkater(localTeam) else null
        for (s in sorted) {
            drawSkater(canvas, world, s, s === controlled, if (localTeam >= 0) world.shotCharge[localTeam] else 0f)
        }
        drawPuck(canvas, world.puck)
        drawBreath(canvas)
        drawGlassShards(canvas, world)
        if (isPond) {
            drawSnow(canvas)
        }
        canvas.restore()

        drawScoreboard(canvas, world)
        drawShootoutControls(canvas, world, localTeam)
        drawBanner(canvas, world)
        if (controls != null) drawControls(canvas, world, localTeam, controls)
        drawPauseButton(canvas)
    }

    // ================================================================ rink

    private fun drawRink(canvas: Canvas, world: World) {
        val isPond = world.arenaType == ArenaType.WINTER_POND
        canvas.drawPath(rinkPath, if (isPond) pondBoardsPaint else boardsPaint)
        canvas.drawPath(rinkPath, if (isPond) pondIcePaint else icePaint)
        canvas.save()
        canvas.clipPath(rinkPath)

        // Skate scratch marks on the ice
        for (i in 0 until scratchCount) {
            scratchPaint.alpha = scratchAlpha[i]
            scratchPaint.strokeWidth = scratchWidth[i]
            canvas.drawLine(scratchX1[i], scratchY1[i], scratchX2[i], scratchY2[i], scratchPaint)
        }

        if (isPond) {
            // Natural frozen lake ice veins and stress fractures
            canvas.drawLine(-25f, -14f, -5f, 6f, pondCrackPaint)
            canvas.drawLine(-5f, 6f, 18f, 14f, pondCrackPaint)
            canvas.drawLine(18f, 14f, 32f, 11f, pondCrackPaint)
            canvas.drawLine(-55f, 12f, -30f, 24f, pondCrackPaint)
            canvas.drawLine(35f, -22f, 62f, -8f, pondCrackPaint)
            canvas.drawLine(-12f, -25f, 8f, -18f, pondCrackPaint)
        }

        // Faint zone shading toward the ends.
        val shade = if (isPond) pondIceShadePaint else iceShadePaint
        tmpRect.set(-Rink.HALF_L, -Rink.HALF_W, -Rink.GOAL_LINE_X, Rink.HALF_W)
        canvas.drawRect(tmpRect, shade)
        tmpRect.set(Rink.GOAL_LINE_X, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
        canvas.drawRect(tmpRect, shade)

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

        // Red goal siren beacons behind nets when a goal is scored
        if (world.phase == Phase.GOAL) {
            val pulse = (sin(animTime * 14f) * 0.5f + 0.5f)
            val redAlpha = (130 + pulse * 125).toInt()
            sirenPaint.color = Color.argb(redAlpha, 255, 20, 20)
            for (e in floatArrayOf(-1f, 1f)) {
                val beaconX = e * (Rink.GOAL_LINE_X + Rink.NET_DEPTH + 1.2f)
                val beaconY = 0f
                canvas.drawCircle(beaconX, beaconY, 1.4f + pulse * 0.6f, sirenPaint)
                canvas.save()
                canvas.translate(beaconX, beaconY)
                val beamAngle = animTime * 7f * e
                canvas.rotate(Math.toDegrees(beamAngle.toDouble()).toFloat())
                sirenBeam.color = Color.argb((50 + pulse * 70).toInt(), 255, 40, 40)
                tmpPath.reset()
                tmpPath.moveTo(0f, 0f)
                tmpPath.lineTo(14f, -4.5f)
                tmpPath.lineTo(14f, 4.5f)
                tmpPath.close()
                canvas.drawPath(tmpPath, sirenBeam)
                tmpPath.reset()
                tmpPath.moveTo(0f, 0f)
                tmpPath.lineTo(-14f, -4.5f)
                tmpPath.lineTo(-14f, 4.5f)
                tmpPath.close()
                canvas.drawPath(tmpPath, sirenBeam)
                canvas.restore()
            }
        }
        canvas.restore()

        // Boards: kick plate inside, glass outside.
        if (isPond) {
            canvas.drawPath(rinkPath, pondKickPlate)
            tmpRect.set(rinkRect)
            tmpRect.inset(-1.1f, -1.1f)
            tmpPath.reset()
            tmpPath.addRoundRect(tmpRect, Rink.CORNER_R + 1.1f, Rink.CORNER_R + 1.1f, Path.Direction.CW)
            canvas.drawPath(tmpPath, pondBoardsPaint)
            tmpRect.inset(-0.8f, -0.8f)
            tmpPath.reset()
            tmpPath.addRoundRect(tmpRect, Rink.CORNER_R + 1.9f, Rink.CORNER_R + 1.9f, Path.Direction.CW)
            canvas.drawPath(tmpPath, pondSnowCapPaint)
        } else {
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

    // ================================================================ snowfall

    private fun updateSnow(dt: Float) {
        if (!snowInitialized) {
            for (i in 0 until MAX_SNOW) {
                snowX[i] = (sprayRng.nextFloat() - 0.5f) * 2f * Camera.WORLD_HALF_W
                snowY[i] = (sprayRng.nextFloat() - 0.5f) * 2f * Camera.WORLD_HALF_H
                snowVy[i] = 4.5f + sprayRng.nextFloat() * 6.0f
                snowVx[i] = -0.5f + sprayRng.nextFloat() * 1.0f
                snowR[i] = 0.22f + sprayRng.nextFloat() * 0.42f
                snowAlpha[i] = 110 + sprayRng.nextInt(125)
                snowSway[i] = sprayRng.nextFloat() * 6.28f
            }
            snowInitialized = true
        }
        for (i in 0 until MAX_SNOW) {
            snowY[i] += snowVy[i] * dt
            snowX[i] += (snowVx[i] + sin(animTime * 1.5f + snowSway[i]) * 1.2f) * dt
            if (snowY[i] > Camera.WORLD_HALF_H) {
                snowY[i] = -Camera.WORLD_HALF_H
                snowX[i] = (sprayRng.nextFloat() - 0.5f) * 2f * Camera.WORLD_HALF_W
            }
            if (snowX[i] > Camera.WORLD_HALF_W) snowX[i] = -Camera.WORLD_HALF_W
            if (snowX[i] < -Camera.WORLD_HALF_W) snowX[i] = Camera.WORLD_HALF_W
        }
    }

    private fun drawSnow(canvas: Canvas) {
        for (i in 0 until MAX_SNOW) {
            snowflakePaint.alpha = snowAlpha[i]
            canvas.drawCircle(snowX[i], snowY[i], snowR[i], snowflakePaint)
        }
    }

    // ================================================================ skater breath vapor

    private fun updateBreath(world: World, dt: Float) {
        for (i in 0 until MAX_BREATH) {
            if (breathLife[i] <= 0f) continue
            breathLife[i] -= dt
            breathX[i] += breathVx[i] * dt
            breathY[i] += breathVy[i] * dt
            val drag = exp(-dt * 3.5f)
            breathVx[i] *= drag
            breathVy[i] *= drag
        }

        if (world.arenaType != ArenaType.WINTER_POND) return

        for (s in world.allSkaters) {
            s.breathTimer -= dt
            if (s.breathTimer <= 0f) {
                s.breathTimer = 1.3f + sprayRng.nextFloat() * 1.2f
                val hx = s.x + cos(s.facing) * 0.9f
                val hy = s.y + sin(s.facing) * 0.9f
                for (p in 0..1) {
                    val idx = breathHead
                    breathHead = (breathHead + 1) % MAX_BREATH
                    breathX[idx] = hx + (sprayRng.nextFloat() - 0.5f) * 0.25f
                    breathY[idx] = hy + (sprayRng.nextFloat() - 0.5f) * 0.25f
                    val sp = 1.2f + sprayRng.nextFloat() * 1.4f
                    val angle = s.facing + (sprayRng.nextFloat() - 0.5f) * 0.5f
                    breathVx[idx] = cos(angle) * sp + s.vx * 0.25f
                    breathVy[idx] = sin(angle) * sp + s.vy * 0.25f
                    breathMax[idx] = 0.55f + sprayRng.nextFloat() * 0.25f
                    breathLife[idx] = breathMax[idx]
                    breathR[idx] = 0.22f + sprayRng.nextFloat() * 0.12f
                }
            }
        }
    }

    private fun drawBreath(canvas: Canvas) {
        for (i in 0 until MAX_BREATH) {
            val life = breathLife[i]
            if (life <= 0f) continue
            val frac = (life / breathMax[i]).coerceIn(0f, 1f)
            val rad = breathR[i] + (1f - frac) * 0.45f
            breathPaint.alpha = (130 * frac).toInt()
            canvas.drawCircle(breathX[i], breathY[i], rad, breathPaint)
        }
    }

    // ================================================================ shattered glass

    private fun updateGlassShards(world: World, dt: Float) {
        if (world.glassShatterTimer > 3.8f && prevGlassTimer <= 3.8f) {
            val sx = world.glassShatterX
            val sy = world.glassShatterY
            var nx = -sx
            var ny = -sy
            val len = hypot(nx, ny).coerceAtLeast(0.1f)
            nx /= len; ny /= len

            for (i in 0 until MAX_SHARDS) {
                shardX[i] = sx + (sprayRng.nextFloat() - 0.5f) * 1.8f
                shardY[i] = sy + (sprayRng.nextFloat() - 0.5f) * 1.8f
                val cone = (sprayRng.nextFloat() - 0.5f) * 2.0f
                val sp = 11f + sprayRng.nextFloat() * 22f
                val c = cos(cone); val sn = sin(cone)
                shardVx[i] = (nx * c - ny * sn) * sp
                shardVy[i] = (nx * sn + ny * c) * sp
                shardRot[i] = sprayRng.nextFloat() * 360f
                shardVrot[i] = (sprayRng.nextFloat() - 0.5f) * 900f
                shardMax[i] = 1.8f + sprayRng.nextFloat() * 1.8f
                shardLife[i] = shardMax[i]
                shardSize[i] = 0.32f + sprayRng.nextFloat() * 0.52f
            }
        }
        prevGlassTimer = world.glassShatterTimer

        for (i in 0 until MAX_SHARDS) {
            if (shardLife[i] <= 0f) continue
            shardLife[i] -= dt
            shardX[i] += shardVx[i] * dt
            shardY[i] += shardVy[i] * dt
            shardRot[i] += shardVrot[i] * dt
            val drag = exp(-dt * 2.2f)
            shardVx[i] *= drag
            shardVy[i] *= drag
            shardVrot[i] *= drag
        }
    }

    private fun drawGlassShards(canvas: Canvas, world: World) {
        if (world.glassShatterTimer > 0f) {
            val frac = (world.glassShatterTimer / 4.0f).coerceIn(0f, 1f)
            val alpha = (240 * frac).toInt()
            glassSpiderwebPaint.alpha = alpha
            glassSpiderwebFill.alpha = (50 * frac).toInt()
            val cx = world.glassShatterX
            val cy = world.glassShatterY

            canvas.drawCircle(cx, cy, 1.8f, glassSpiderwebFill)

            val rings = floatArrayOf(1.2f, 2.6f, 4.2f)
            for (r in rings) {
                tmpPath.reset()
                for (s in 0..7) {
                    val a = s * (Math.PI / 4) + (s % 2) * 0.15
                    val dist = r * (0.8f + (s % 3) * 0.18f)
                    val px = (cx + cos(a) * dist).toFloat()
                    val py = (cy + sin(a) * dist).toFloat()
                    if (s == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
                }
                tmpPath.close()
                canvas.drawPath(tmpPath, glassSpiderwebPaint)
            }

            for (s in 0..11) {
                val a = s * (Math.PI / 6) + ((s * 7) % 5) * 0.08
                val len = 3.5f + ((s * 11) % 4) * 1.5f
                val ex = (cx + cos(a) * len).toFloat()
                val ey = (cy + sin(a) * len).toFloat()
                canvas.drawLine(cx, cy, ex, ey, glassSpiderwebPaint)
            }
        }

        for (i in 0 until MAX_SHARDS) {
            val life = shardLife[i]
            if (life <= 0f) continue
            val frac = (life / shardMax[i]).coerceIn(0f, 1f)
            val sz = shardSize[i]
            canvas.save()
            canvas.translate(shardX[i], shardY[i])
            canvas.rotate(shardRot[i])
            glassShardFill.alpha = (190 * frac).toInt()
            glassShardEdge.alpha = (250 * frac).toInt()
            tmpPath.reset()
            tmpPath.moveTo(-sz * 0.6f, -sz * 0.9f)
            tmpPath.lineTo(sz * 0.9f, -sz * 0.2f)
            tmpPath.lineTo(sz * 0.2f, sz * 0.9f)
            tmpPath.close()
            canvas.drawPath(tmpPath, glassShardFill)
            canvas.drawPath(tmpPath, glassShardEdge)
            if (frac > 0.3f && i % 3 == 0) {
                snowflakePaint.alpha = (230 * frac).toInt()
                canvas.drawCircle(0f, 0f, sz * 0.18f, snowflakePaint)
            }
            canvas.restore()
        }
    }

    // ================================================================ players

    private fun drawShadow(canvas: Canvas, s: Skater) {
        val r = s.radius * 1.15f * BODY_SCALE
        // Ambient soft cast shadow
        tmpRect.set(s.x - r + 0.35f, s.y - r * 0.78f + 0.55f, s.x + r + 0.35f, s.y + r * 0.78f + 0.55f)
        canvas.drawOval(tmpRect, shadowPaint)
        // Tight contact shadow right under skates/body
        tmpRect.set(s.x - r * 0.7f, s.y - r * 0.48f + 0.2f, s.x + r * 0.7f, s.y + r * 0.48f + 0.2f)
        canvas.drawOval(tmpRect, contactShadow)
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
        var rot = Math.toDegrees(s.facing.toDouble()).toFloat()
        if (s.dekeTimer > 0f) {
            rot += s.dekeDir * (s.dekeTimer / 0.38f) * 22f
        }
        // Dynamic edge carving / banking lean into turns
        val latSpd = -s.vx * sin(s.facing) + s.vy * cos(s.facing)
        val bankAngle = (latSpd * 1.25f).coerceIn(-16f, 16f)
        canvas.rotate(rot + bankAngle)

        val stunned = s.stunTimer > 0f
        if (stunned) {
            // Flat on the ice.
            canvas.rotate(90f)
            canvas.scale(1.45f, 0.72f)
        }
        if (s.isGoalie) drawGoalieBody(canvas, s, info, goalieShader[s.team]!!)
        else drawSkaterBody(canvas, s, info, skaterShader[s.team]!!, if (controlled) charge else 0f)
        canvas.restore()

        if (world.isOnFire(s.team)) {
            // Flickering fire particles trailing behind the skater
            for (k in 0..3) {
                val fPhase = (animTime * 9f + k * 0.25f + s.index * 0.37f) % 1f
                val offBack = 0.8f + fPhase * 1.5f
                val jiggle = sin(animTime * 16f + k * 2.3f) * 0.32f
                val fx = s.x - cos(s.facing) * offBack - sin(s.facing) * jiggle
                val fy = s.y - sin(s.facing) * offBack + cos(s.facing) * jiggle
                val fr = (0.36f * (1f - fPhase * 0.7f)).coerceAtLeast(0.08f)
                val alpha = (235 * (1f - fPhase)).toInt().coerceIn(0, 255)
                fireEmberPaint.color = if (k % 2 == 0) Color.argb(alpha, 255, 90, 10) else Color.argb(alpha, 255, 210, 30)
                canvas.drawCircle(fx, fy, fr, fireEmberPaint)
            }
        }

        // Pro dual-tone drop shadow jersey numbers
        val numX = s.x - cos(s.facing) * 0.5f
        val numY = s.y - sin(s.facing) * 0.5f + 0.7f
        numberShadowPaint.textSize = if (s.isGoalie) 2.1f else 2.0f
        numberShadowPaint.strokeWidth = 0.35f
        numberShadowPaint.style = Paint.Style.STROKE
        canvas.drawText(s.number.toString(), numX, numY, numberShadowPaint)
        numberPaint.color = info.text
        numberPaint.textSize = if (s.isGoalie) 2.1f else 2.0f
        numberPaint.style = Paint.Style.FILL
        canvas.drawText(s.number.toString(), numX, numY, numberPaint)

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
        // Leather palm underneath
        tmpRect.set(x - size * 0.72f, y - size * 0.68f, x + size * 0.72f, y + size * 0.68f)
        canvas.drawRoundRect(tmpRect, size * 0.28f, size * 0.28f, glovePalmPaint)

        // Main glove shell with team primary color
        glovePaint.color = gloveColor[team]
        tmpRect.set(x - size, y - size * 0.85f, x + size, y + size * 0.85f)
        canvas.drawRoundRect(tmpRect, size * 0.45f, size * 0.45f, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.45f, size * 0.45f, gloveOutline)

        // 4-roll segmented finger rolls across the backhand
        val rollStep = size * 0.36f
        for (i in -1..1) {
            val rx = x + i * rollStep * 0.65f
            canvas.drawLine(rx, y - size * 0.62f, rx, y + size * 0.62f, gloveRollPaint)
        }

        // Flared protective wrist cuff in team secondary color with piping
        gloveCuff.color = info.secondary
        tmpRect.set(x - size * 1.05f, y - size * 0.82f, x - size * 0.45f, y + size * 0.82f)
        canvas.drawRoundRect(tmpRect, size * 0.22f, size * 0.22f, gloveCuff)
        canvas.drawRoundRect(tmpRect, size * 0.22f, size * 0.22f, gloveOutline)

        // Anatomical locked thumb guard
        tmpRect.set(x + size * 0.2f, y - size * 0.95f, x + size * 0.82f, y - size * 0.4f)
        canvas.drawRoundRect(tmpRect, size * 0.18f, size * 0.18f, glovePaint)
        canvas.drawRoundRect(tmpRect, size * 0.18f, size * 0.18f, gloveOutline)
    }

    private fun drawSkaterBody(canvas: Canvas, s: Skater, info: TeamInfo, shader: Shader, charge: Float) {
        val r = s.radius
        canvas.scale(BODY_SCALE, BODY_SCALE)
        val moving = s.speed > 2f
        val stride = if (moving) sin(s.stride * 1.3f) else 0f
        val bladeLocal = s.stickReach / BODY_SCALE

        // Skates: contoured boot, white TUUK holder, stainless runner with glints
        for (side in intArrayOf(-1, 1)) {
            val phase = stride * side
            val push = max(0f, -phase)
            val bx = -0.15f * r + phase * 0.42f * r
            val by = side * (0.72f * r + push * 0.4f * r)
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(side * (-6f + push * 32f))

            // White TUUK holder
            tmpRect.set(-0.52f * r, 0.01f * r, 0.52f * r, 0.12f * r)
            canvas.drawRoundRect(tmpRect, 0.05f * r, 0.05f * r, holderWhite)

            // High-carbon stainless steel runner
            canvas.drawLine(-0.64f * r, 0.12f * r, 0.64f * r, 0.12f * r, runnerSteel)
            canvas.drawCircle(0.48f * r, 0.12f * r, 0.035f * r, runnerGlint)

            // Contoured boot with tendon guard
            tmpRect.set(-0.5f * r, -0.21f * r, 0.5f * r, 0.21f * r)
            canvas.drawRoundRect(tmpRect, 0.15f * r, 0.15f * r, bootPaint)

            // Laces
            bootLacePaint.strokeWidth = 0.05f * r
            canvas.drawLine(-0.24f * r, -0.06f * r, 0.28f * r, -0.06f * r, bootLacePaint)
            canvas.drawLine(-0.24f * r, 0.06f * r, 0.28f * r, 0.06f * r, bootLacePaint)
            canvas.restore()
        }

        // Hockey Pants (Breezers) - dual thigh shells & kidney belt
        tmpRect.set(-0.85f * r, -0.85f * r, -0.22f * r, 0.85f * r)
        canvas.drawRoundRect(tmpRect, 0.22f * r, 0.22f * r, pantsPaint)
        canvas.drawRoundRect(tmpRect, 0.22f * r, 0.22f * r, pantsOutline)
        for (side in intArrayOf(-1, 1)) {
            val py = side * 0.48f * r
            tmpRect.set(-0.96f * r, py - 0.36f * r, -0.26f * r, py + 0.36f * r)
            canvas.drawRoundRect(tmpRect, 0.16f * r, 0.16f * r, pantsPaint)
            canvas.drawRoundRect(tmpRect, 0.16f * r, 0.16f * r, pantsOutline)
            // Accent stripe on outer leg shell
            pantsStripe.color = info.primary
            pantsStripe.strokeWidth = 0.11f * r
            canvas.drawLine(-0.92f * r, py + side * 0.25f * r, -0.32f * r, py + side * 0.25f * r, pantsStripe)
        }

        // Stick and arms: swings on shot/pass, flexes shaft on shot wind-up
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

        // Arms with sleeve stripes
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

        // Shaft: bows dynamically under shot tension (stick flex)
        shaftDark.strokeWidth = 0.2f * r
        shaftCore.strokeWidth = 0.08f * r
        if (charge > 0.05f) {
            val flexDisp = charge * 0.65f * r
            val midX = (bX + hX) * 0.5f - dY * flexDisp
            val midY = (bY + hY) * 0.5f + dX * flexDisp
            tmpPath.reset()
            tmpPath.moveTo(bX, bY)
            tmpPath.quadTo(midX, midY, hX, hY)
            canvas.drawPath(tmpPath, shaftDark)
            canvas.drawPath(tmpPath, shaftCore)
        } else {
            canvas.drawLine(bX, bY, hX, hY, shaftDark)
            canvas.drawLine(bX, bY, hX, hY, shaftCore)
        }
        tapePaint.strokeWidth = 0.22f * r
        canvas.drawLine(bX, bY, bX + dX * 0.4f * r, bY + dY * 0.4f * r, tapePaint)

        // Curved blade with serrated tape wraps and puck scuff
        tmpPath.reset()
        tmpPath.moveTo(hX, hY)
        tmpPath.quadTo(bladeLocal + 0.05f, 0.42f, bladeLocal + 0.5f, -0.25f)
        bladeOutline.strokeWidth = 0.46f
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.3f
        canvas.drawPath(tmpPath, bladeTape)

        // Tape wrap ribs along blade
        tapePaint.strokeWidth = 0.07f * r
        for (ti in 0..3) {
            val frac = 0.25f + ti * 0.18f
            val tx = hX + (bladeLocal + 0.5f - hX) * frac
            val ty = hY + (-0.25f - hY) * frac
            canvas.drawLine(tx - 0.08f * r, ty - 0.12f * r, tx + 0.08f * r, ty + 0.12f * r, tapePaint)
        }
        // Puck friction mark on sweet spot
        canvas.drawCircle(bladeLocal + 0.1f, 0.22f, 0.12f * r, puckScuffPaint)

        // Segmented gloves on the shaft
        drawGlove(canvas, tX, tY, 0.4f * r, s.team, info)
        drawGlove(canvas, uX, uY, 0.4f * r, s.team, info)
        canvas.restore()

        // Torso with shaded jersey, shoulder pads, and yoke stripes
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

        // Pro sculpted helmet with vents, ear guards, visor gleam, and chin strap
        helmetPaint.color = helmetColor[s.team]
        val hx = 0.28f * r
        val hr = 0.58f * r

        // Ear protectors
        canvas.drawCircle(hx - 0.08f * r, -0.52f * r, 0.14f * r, earGuardPaint)
        canvas.drawCircle(hx - 0.08f * r, 0.52f * r, 0.14f * r, earGuardPaint)

        // Helmet shell
        canvas.drawCircle(hx, 0f, hr, helmetPaint)
        canvas.drawCircle(hx, 0f, hr, helmetOutline)

        // Aerodynamic ventilation ports on crown
        for (vy in floatArrayOf(-0.25f, 0.25f)) {
            tmpRect.set(hx - 0.3f * r, vy * r - 0.06f * r, hx - 0.05f * r, vy * r + 0.06f * r)
            canvas.drawRoundRect(tmpRect, 0.05f * r, 0.05f * r, helmetVent)
        }

        // Chin strap
        canvas.drawLine(hx - 0.1f * r, -0.45f * r, hx + 0.35f * r, 0f, chinStrapPaint)
        canvas.drawLine(hx - 0.1f * r, 0.45f * r, hx + 0.35f * r, 0f, chinStrapPaint)

        // Tinted curved Oakley-style visor with dual-specular gloss highlight
        tmpRect.set(hx - hr, -hr, hx + hr, hr)
        canvas.drawArc(tmpRect, -50f, 100f, false, visorPaint)
        canvas.drawArc(tmpRect, -45f, 90f, false, visorGleamPaint)
        canvas.drawCircle(hx + 0.15f * r, -0.2f * r, 0.15f * r, glossPaint)
    }

    private fun drawGoalieBody(canvas: Canvas, g: Skater, info: TeamInfo, shader: Shader) {
        val r = g.radius
        canvas.scale(GOALIE_SCALE, GOALIE_SCALE)
        val bladeLocal = g.stickReach / GOALIE_SCALE
        padStripe.color = info.primary

        if (g.goalieAction == GoalieAction.PAD_STACK) {
            canvas.rotate(g.padStackDir * 65f)
            for (pIdx in 0..1) {
                val pY = if (pIdx == 0) -1.0f * r else -0.1f * r
                // Pad shell
                tmpRect.set(-0.95f * r, pY - 0.42f * r, 0.95f * r, pY + 0.42f * r)
                canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padOutline)
                // Team graphic stripe
                tmpRect.set(-0.35f * r, pY - 0.28f * r, 0.35f * r, pY + 0.28f * r)
                canvas.drawRect(tmpRect, padStripe)
                // Knee roll creases
                for (k in -1..1) {
                    val kx = k * 0.45f * r
                    canvas.drawLine(kx, pY - 0.38f * r, kx, pY + 0.38f * r, padCrease)
                }
            }
            // Goalie stick flat on ice
            shaftDark.strokeWidth = 0.38f * r
            canvas.drawLine(-0.4f * r, -1.5f * r, bladeLocal + 0.3f, -1.5f * r, shaftDark)
            shaftCore.strokeWidth = 0.16f * r
            canvas.drawLine(-0.4f * r, -1.5f * r, bladeLocal + 0.3f, -1.5f * r, shaftCore)
            tmpPath.reset()
            tmpPath.moveTo(bladeLocal - 0.7f, -1.5f * r)
            tmpPath.lineTo(bladeLocal + 0.5f, -1.5f * r)
            bladeOutline.strokeWidth = 0.55f
            canvas.drawPath(tmpPath, bladeOutline)
            bladeTape.strokeWidth = 0.38f
            canvas.drawPath(tmpPath, bladeTape)

            torsoPaint.shader = shader
            tmpRect.set(-0.85f * r, 0.4f * r, 0.75f * r, 1.8f * r)
            canvas.drawOval(tmpRect, torsoPaint)
            canvas.drawOval(tmpRect, bodyOutline)

            canvas.drawCircle(0.85f * r, 1.7f * r, 0.48f * r, leatherPaint)
            canvas.drawCircle(0.85f * r, 1.7f * r, 0.48f * r, gloveOutline)
            canvas.drawCircle(0.2f * r, 1.7f * r, 0.45f * r, glovePaint)

            maskPaint.color = info.secondary
            val mx = 0.25f * r
            val mr = 0.58f * r
            canvas.drawCircle(mx, 1.7f * r, mr, maskPaint)
            canvas.drawCircle(mx, 1.7f * r, mr, helmetOutline)
            return
        }

        // Leg pads: flared butterfly with sliding plates or upright 3-roll stance
        if (g.goalieAction == GoalieAction.BUTTERFLY || g.butterfly) {
            canvas.save()
            canvas.translate(-0.1f * r, -0.4f * r)
            canvas.rotate(-38f)
            // Left butterfly pad
            tmpRect.set(-0.7f * r, -1.35f * r, 0.7f * r, 0.15f * r)
            canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padOutline)
            // Inner sliding plate
            tmpRect.set(-0.6f * r, -0.1f * r, 0.6f * r, 0.12f * r)
            canvas.drawRoundRect(tmpRect, 0.08f * r, 0.08f * r, padPlate)
            // Team graphic wedge
            tmpRect.set(-0.25f * r, -1.2f * r, 0.15f * r, 0.05f * r)
            canvas.drawRect(tmpRect, padStripe)
            // Knee rolls
            for (k in -1..1) {
                val kx = k * 0.32f * r
                canvas.drawLine(kx, -1.15f * r, kx, -0.1f * r, padCrease)
            }
            canvas.restore()

            canvas.save()
            canvas.translate(-0.1f * r, 0.4f * r)
            canvas.rotate(38f)
            // Right butterfly pad
            tmpRect.set(-0.7f * r, -0.15f * r, 0.7f * r, 1.35f * r)
            canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.28f * r, 0.28f * r, padOutline)
            // Inner sliding plate
            tmpRect.set(-0.6f * r, -0.12f * r, 0.6f * r, 0.1f * r)
            canvas.drawRoundRect(tmpRect, 0.08f * r, 0.08f * r, padPlate)
            // Team graphic wedge
            tmpRect.set(-0.25f * r, -0.05f * r, 0.15f * r, 1.2f * r)
            canvas.drawRect(tmpRect, padStripe)
            // Knee rolls
            for (k in -1..1) {
                val kx = k * 0.32f * r
                canvas.drawLine(kx, 0.1f * r, kx, 1.15f * r, padCrease)
            }
            canvas.restore()

            // Five-hole sealed knee stack blocks
            tmpRect.set(-0.45f * r, -0.38f * r, 0.35f * r, 0.38f * r)
            canvas.drawRoundRect(tmpRect, 0.18f * r, 0.18f * r, padPaint)
            canvas.drawRoundRect(tmpRect, 0.18f * r, 0.18f * r, padOutline)
        } else {
            // Upright ready stance pads with 3-tier knee rolls
            for (side in intArrayOf(-1, 1)) {
                val inner = side * 0.32f * r
                val outer = side * 1.12f * r
                tmpRect.set(-0.7f * r, min(inner, outer), 0.72f * r, max(inner, outer))
                canvas.drawRoundRect(tmpRect, 0.3f * r, 0.3f * r, padPaint)
                canvas.drawRoundRect(tmpRect, 0.3f * r, 0.3f * r, padOutline)
                // Team color chevron stripe
                tmpRect.set(-0.2f * r, min(inner, outer) + 0.08f * r, 0.08f * r, max(inner, outer) - 0.08f * r)
                canvas.drawRect(tmpRect, padStripe)
                // 3 segmented knee roll grooves
                for (k in -1..1) {
                    val kx = k * 0.32f * r
                    canvas.drawLine(kx, min(inner, outer) + 0.1f * r, kx, max(inner, outer) - 0.1f * r, padCrease)
                }
            }
        }

        // Goalie stick: wide reinforced paddle tapering down to wide curved blade
        val pokeDist = if (g.pokeTimer > 0f) 1.6f else 0f
        shaftDark.strokeWidth = 0.44f * r
        canvas.drawLine(0.45f * r + pokeDist, 1.0f * r, bladeLocal - 0.9f + pokeDist, 0.85f, shaftDark)
        shaftCore.strokeWidth = 0.18f * r
        canvas.drawLine(0.45f * r + pokeDist, 1.0f * r, bladeLocal - 0.9f + pokeDist, 0.85f, shaftCore)
        tmpPath.reset()
        tmpPath.moveTo(bladeLocal - 0.95f + pokeDist, 0.9f)
        tmpPath.quadTo(bladeLocal + pokeDist, 0.7f, bladeLocal + 0.55f + pokeDist, 0.15f)
        bladeOutline.strokeWidth = 0.58f
        canvas.drawPath(tmpPath, bladeOutline)
        bladeTape.strokeWidth = 0.40f
        canvas.drawPath(tmpPath, bladeTape)

        // Arms & shoulder floaters
        armOutline.strokeWidth = 0.68f * r
        canvas.drawLine(-0.1f * r, -0.9f * r, 0.8f * r, -1.15f * r, armOutline)
        canvas.drawLine(-0.1f * r, 0.9f * r, 0.75f * r, 1.05f * r, armOutline)
        armPaint.color = info.primary
        armPaint.strokeWidth = 0.55f * r
        canvas.drawLine(-0.1f * r, -0.9f * r, 0.8f * r, -1.15f * r, armPaint)
        canvas.drawLine(-0.1f * r, 0.9f * r, 0.75f * r, 1.05f * r, armPaint)

        // Chest protector under the jersey
        torsoPaint.shader = shader
        tmpRect.set(-1.0f * r, -1.2f * r, 0.85f * r, 1.2f * r)
        canvas.drawOval(tmpRect, torsoPaint)
        canvas.drawOval(tmpRect, bodyOutline)
        canvas.drawCircle(-0.2f * r, -1.05f * r, 0.4f * r, torsoPaint)
        canvas.drawCircle(-0.2f * r, 1.05f * r, 0.4f * r, torsoPaint)
        yokePaint.color = info.secondary
        yokePaint.strokeWidth = 0.32f * r
        canvas.drawLine(-0.5f * r, -1.0f * r, -0.5f * r, 1.0f * r, yokePaint)

        // Catching glove (trapper): laced T-trap pocket webbing
        canvas.drawCircle(0.95f * r, -1.25f * r, 0.52f * r, leatherPaint)
        canvas.drawCircle(0.95f * r, -1.25f * r, 0.52f * r, gloveOutline)
        canvas.drawCircle(1.02f * r, -1.3f * r, 0.3f * r, leatherLight)
        // Cross-laced T-trap cords
        canvas.drawLine(0.72f * r, -1.25f * r, 1.22f * r, -1.25f * r, trapperLace)
        canvas.drawLine(0.95f * r, -1.48f * r, 0.95f * r, -1.02f * r, trapperLace)

        // Blocker: beveled rectangular deflection board with angled face
        canvas.save()
        canvas.translate(0.95f * r, 1.15f * r)
        canvas.rotate(-15f)
        tmpRect.set(-0.38f * r, -0.52f * r, 0.38f * r, 0.52f * r)
        canvas.drawRoundRect(tmpRect, 0.12f * r, 0.12f * r, padPaint)
        canvas.drawRoundRect(tmpRect, 0.12f * r, 0.12f * r, gloveOutline)
        // Beveled deflecting rim
        tmpRect.set(-0.32f * r, -0.46f * r, 0.32f * r, 0.46f * r)
        canvas.drawRoundRect(tmpRect, 0.08f * r, 0.08f * r, blockerBevel)
        tmpRect.set(-0.36f * r, -0.1f * r, 0.36f * r, 0.1f * r)
        canvas.drawRect(tmpRect, padStripe)
        canvas.restore()

        // Mask: sculpted fiberglass shell with chrome cat-eye cage
        maskPaint.color = info.secondary
        val mx = 0.32f * r
        val mr = 0.62f * r
        canvas.drawCircle(mx, 0f, mr, maskPaint)
        canvas.drawCircle(mx, 0f, mr, helmetOutline)

        // Cat-eye curved wire cage grille
        for (yy in floatArrayOf(-0.28f, 0f, 0.28f)) {
            canvas.drawLine(mx + 0.15f * r, yy * r, mx + 0.58f * r, yy * r * 0.8f, catEyeCage)
        }
        canvas.drawLine(mx + 0.26f * r, -0.38f * r, mx + 0.26f * r, 0.38f * r, catEyeCage)
        canvas.drawLine(mx + 0.44f * r, -0.32f * r, mx + 0.44f * r, 0.32f * r, catEyeCage)
        // Curved eye opening arc
        tmpRect.set(mx + 0.12f * r, -0.22f * r, mx + 0.52f * r, 0.22f * r)
        canvas.drawArc(tmpRect, -70f, 140f, false, catEyeCage)

        // Temple gloss gleam
        canvas.drawCircle(mx + 0.05f * r, -0.28f * r, 0.13f * r, glossPaint)
    }

    private fun drawPuck(canvas: Canvas, p: Puck) {
        val sp = p.speed
        puckTrailX[puckTrailHead] = p.x
        puckTrailY[puckTrailHead] = p.y
        puckTrailHead = (puckTrailHead + 1) % TRAIL_POINTS

        if (p.carrier == null && sp > 35f) {
            val isBoomer = sp > 95f
            for (i in 1 until TRAIL_POINTS) {
                val currIdx = (puckTrailHead - i + TRAIL_POINTS) % TRAIL_POINTS
                val prevIdx = (puckTrailHead - i - 1 + TRAIL_POINTS) % TRAIL_POINTS
                val frac = 1f - (i / TRAIL_POINTS.toFloat())
                val alpha = (frac * 190).toInt()
                if (isBoomer) {
                    cometTrail.color = Color.argb(alpha, 255, (120 * frac + 30).toInt(), 20)
                    cometTrail.strokeWidth = 1.0f * frac + 0.3f
                } else {
                    cometTrail.color = Color.argb(alpha, 56, 189, 248)
                    cometTrail.strokeWidth = 0.65f * frac + 0.2f
                }
                canvas.drawLine(puckTrailX[prevIdx], puckTrailY[prevIdx], puckTrailX[currIdx], puckTrailY[currIdx], cometTrail)
            }
            if (isBoomer) {
                cometGlow.color = Color.argb(130, 255, 140, 20)
                canvas.drawCircle(p.x, p.y, 2.0f, cometGlow)
            }
        }
        // Dual-tier shadow
        canvas.drawCircle(p.x + 0.22f, p.y + 0.28f, 0.95f, shadowPaint)
        canvas.drawCircle(p.x + 0.08f, p.y + 0.1f, 0.82f, contactShadow)

        // Vulcanized rubber puck body with knurled textured edge
        canvas.drawCircle(p.x, p.y, 0.95f, puckHalo)
        canvas.drawCircle(p.x, p.y, 0.82f, puckPaint)
        canvas.drawCircle(p.x, p.y, 0.80f, puckKnurl)

        // Beveled upper rim & center embossed medallion
        canvas.drawCircle(p.x, p.y, 0.62f, puckBevel)
        canvas.drawCircle(p.x, p.y, 0.38f, puckRim)
        canvas.drawCircle(p.x - 0.12f, p.y - 0.12f, 0.18f, glossPaint)
    }

    // ================================================================ HUD

    private fun dp(v: Float) = v * density

    private fun drawScoreboard(canvas: Canvas, w: World) {
        val cx = camera.screenW / 2f
        val width = dp(330f)
        val height = if (w.isShootout) dp(54f) else dp(46f)
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
        val scoreY = if (w.isShootout) top + dp(27f) else top + dp(33f)
        canvas.drawText(w.teams[0].score.toString(), cx - dp(92f), scoreY, hudText)
        canvas.drawText(w.teams[1].score.toString(), cx + dp(92f), scoreY, hudText)

        if (w.isShootout) {
            hudText.textSize = dp(18f)
            canvas.drawText(String.format("%.1fs", w.shootoutTimer.coerceAtLeast(0f)), cx, top + dp(21f), hudText)
            hudSmall.textSize = dp(10f)
            val stText = if (w.shootoutRound <= 5) "ROUND ${w.shootoutRound}" else "SUDDEN DEATH"
            canvas.drawText(stText, cx, top + dp(35f), hudSmall)
            val shooterAbbr = w.teams[w.shootoutTurn].info.abbr
            canvas.drawText("$shooterAbbr SHOOTING", cx, top + dp(47f), hudSmall)

            // 5-round shootout indicators positioned directly under team scores
            val dotR = dp(3.2f)
            val dotSpacing = dp(8.5f)
            val dotY = top + dp(42f)
            val t0StartX = (cx - dp(92f)) - 2 * dotSpacing
            for (r in 0 until 5) {
                val res = w.shootoutAttempts[0][r]
                val dx = t0StartX + r * dotSpacing
                if (res == 1) {
                    shootoutDotFill.color = Color.parseColor("#22C55E")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else if (res == 2) {
                    shootoutDotFill.color = Color.parseColor("#EF4444")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else {
                    shootoutDotStroke.color = Color.parseColor("#64748B")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotStroke)
                }
            }
            val t1StartX = (cx + dp(92f)) - 2 * dotSpacing
            for (r in 0 until 5) {
                val res = w.shootoutAttempts[1][r]
                val dx = t1StartX + r * dotSpacing
                if (res == 1) {
                    shootoutDotFill.color = Color.parseColor("#22C55E")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else if (res == 2) {
                    shootoutDotFill.color = Color.parseColor("#EF4444")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotFill)
                } else {
                    shootoutDotStroke.color = Color.parseColor("#64748B")
                    canvas.drawCircle(dx, dotY, dotR, shootoutDotStroke)
                }
            }
        } else {
            hudText.textSize = dp(19f)
            canvas.drawText(w.clockText(), cx, top + dp(23f), hudText)
            hudSmall.textSize = dp(11f)
            canvas.drawText(w.periodText() + (if (w.overtime) "  SUDDEN DEATH" else "  PERIOD"), cx, top + dp(39f), hudSmall)

            // Shots on goal
            hudSmall.textSize = dp(11f)
            canvas.drawText("SHOTS  " + w.teams[0].shots + " - " + w.teams[1].shots, cx, top + height + dp(14f), hudSmall)
        }

        // Flame indicators for on-fire teams
        if (w.isOnFire(0)) {
            hudSmall.textSize = dp(11f)
            hudSmall.color = Color.parseColor("#F97316")
            canvas.drawText("🔥 ${w.fireTimer[0].toInt()}s", cx - width / 2f + dp(33f), top - dp(3f), hudSmall)
            hudSmall.color = Color.parseColor("#CBD5E1")
        }
        if (w.isOnFire(1)) {
            hudSmall.textSize = dp(11f)
            hudSmall.color = Color.parseColor("#F97316")
            canvas.drawText("🔥 ${w.fireTimer[1].toInt()}s", cx + width / 2f - dp(33f), top - dp(3f), hudSmall)
            hudSmall.color = Color.parseColor("#CBD5E1")
        }

        // Power play or empty net badge
        if (w.penaltyTeam != -1) {
            val advTeam = w.opponent(w.penaltyTeam)
            val pTimer = w.penaltyTimer.toInt().coerceAtLeast(0)
            val m = pTimer / 60
            val s = pTimer % 60
            val ppStr = String.format("%s PP %d:%02d", advTeam.info.abbr, m, s)
            val badgeW = dp(104f)
            val badgeH = dp(17f)
            val badgeY = top + height + dp(20f)
            tmpRect.set(cx - badgeW / 2f, badgeY, cx + badgeW / 2f, badgeY + badgeH)
            ppBadgeBack.color = advTeam.info.primary
            ppBadgeBorder.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), ppBadgeBack)
            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), ppBadgeBorder)
            ppBadgeText.textSize = dp(10.5f)
            ppBadgeText.color = advTeam.info.text
            canvas.drawText(ppStr, cx, badgeY + dp(12.5f), ppBadgeText)
        } else if (w.goaliePulled[0] || w.goaliePulled[1]) {
            val pulledId = if (w.goaliePulled[0]) 0 else 1
            val pTeam = w.teams[pulledId]
            val enStr = "${pTeam.info.abbr} EMPTY NET"
            val badgeW = dp(108f)
            val badgeH = dp(17f)
            val badgeY = top + height + dp(20f)
            tmpRect.set(cx - badgeW / 2f, badgeY, cx + badgeW / 2f, badgeY + badgeH)
            ppBadgeBack.color = Color.parseColor("#B91C1C")
            ppBadgeBorder.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), ppBadgeBack)
            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), ppBadgeBorder)
            ppBadgeText.textSize = dp(10f)
            ppBadgeText.color = Color.WHITE
            canvas.drawText(enStr, cx, badgeY + dp(12.5f), ppBadgeText)
        }
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

        val controlled = if (localTeam >= 0) w.controlledSkater(localTeam) else null
        val isGoalie = controlled?.isGoalie == true
        if (isGoalie) {
            drawButton(canvas, c.shootX, c.shootY, c.shootR, "BUTTERFLY", "5-hole", c.shootDown, Color.parseColor("#DC2626"))
            drawButton(canvas, c.passX, c.passY, c.passR, "POKE", "stick", c.passDown, Color.parseColor("#2563EB"))
            drawButton(canvas, c.hitX, c.hitY, c.hitR, "PAD STACK", "sprawl", c.hitDown, Color.parseColor("#D97706"))
        } else {
            val hasPuck = localTeam >= 0 && w.puck.carrier != null && w.puck.carrier === controlled
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

    private fun drawShootoutControls(canvas: Canvas, w: World, localTeam: Int) {
        if (!w.isShootout || w.phase != Phase.PLAY || localTeam < 0) return
        val shooterTeamId = w.shootoutTurn
        if (shooterTeamId == localTeam) {
            val shooter = w.controlledSkater(localTeam) ?: return
            if (kotlin.math.hypot(shooter.x, shooter.y) > 12f) return

            val bw = dp(154f)
            val bh = dp(42f)
            val bx = dp(14f)
            val by = dp(10f)
            switchShooterRect.set(bx, by, bx + bw, by + bh)

            canvas.drawRoundRect(switchShooterRect, dp(8f), dp(8f), switchShooterPaint)
            switchShooterBorder.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(switchShooterRect, dp(8f), dp(8f), switchShooterBorder)

            switchShooterTitle.textSize = dp(9.5f)
            canvas.drawText("TAP TO CHANGE SHOOTER", bx + bw / 2f, by + dp(14f), switchShooterTitle)

            val roleStr = when (shooter.role) {
                Role.C -> "CENTER"
                Role.LW -> "LEFT WING"
                Role.RW -> "RIGHT WING"
                Role.LD -> "LEFT DEFENSE"
                Role.RD -> "RIGHT DEFENSE"
                Role.G -> "GOALIE"
            }
            switchShooterSub.textSize = dp(13f)
            canvas.drawText("🔁 $roleStr #${shooter.number}", bx + bw / 2f, by + dp(32f), switchShooterSub)
        } else {
            // Defending turn! Indicate user is controlling the goalie
            val bw = dp(130f)
            val bh = dp(32f)
            val bx = dp(14f)
            val by = dp(10f)
            switchShooterRect.set(0f, 0f, 0f, 0f)
            tmpRect.set(bx, by, bx + bw, by + bh)

            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), switchShooterPaint)
            switchShooterBorder.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(tmpRect, dp(6f), dp(6f), switchShooterBorder)

            switchShooterTitle.textSize = dp(10f)
            canvas.drawText("DEFENDING GOALIE", bx + bw / 2f, by + dp(14f), switchShooterTitle)
            switchShooterSub.textSize = dp(12f)
            val gNum = w.teams[localTeam].goalie.number
            canvas.drawText("YOU ARE GOALIE #$gNum", bx + bw / 2f, by + dp(26f), switchShooterSub)
        }
    }

    fun isSwitchShooterHit(x: Float, y: Float, w: World, localTeam: Int): Boolean {
        if (!w.isShootout || w.phase != Phase.PLAY || localTeam < 0 || w.shootoutTurn != localTeam) return false
        val shooter = w.controlledSkater(localTeam) ?: return false
        if (kotlin.math.hypot(shooter.x, shooter.y) > 12f) return false
        if (switchShooterRect.contains(x, y)) return true
        val sx = camera.toScreenX(shooter.x)
        val sy = camera.toScreenY(shooter.y)
        return kotlin.math.hypot(x - sx, y - sy) <= dp(45f)
    }

    fun release() {
        crowd?.recycle()
        crowd = null
        winterLandscape?.recycle()
        winterLandscape = null
    }
}
