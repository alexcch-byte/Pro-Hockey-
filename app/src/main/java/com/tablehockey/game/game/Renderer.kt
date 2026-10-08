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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
        private const val STRIDE_FRAMES = 16
    }

    private val rinkRect = RectF(-Rink.HALF_L, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
    private val rinkPath = Path().apply { addRoundRect(rinkRect, Rink.CORNER_R, Rink.CORNER_R, Path.Direction.CW) }
    private val tmpRect = RectF()
    private val tmpPath = Path()

    private val art = WorldArt()
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
    private val puckBevelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2F38") }
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

    // ----- upright character sprites (see CharacterArt); built lazily per facing and pose
    private var charArt: CharacterArt? = null
    private var charArtScale = 0f
    private val skaterSpr = arrayOf(
        arrayOfNulls<Bitmap>(CharacterArt.FACINGS * CharacterArt.FRAMES),
        arrayOfNulls<Bitmap>(CharacterArt.FACINGS * CharacterArt.FRAMES)
    )
    private val goalieSpr = arrayOf(
        arrayOfNulls<Bitmap>(CharacterArt.FACINGS * CharacterArt.GOALIE_STANCES),
        arrayOfNulls<Bitmap>(CharacterArt.FACINGS * CharacterArt.GOALIE_STANCES)
    )
    private val refSpr = arrayOfNulls<Bitmap>(CharacterArt.FACINGS * CharacterArt.STRIDE_FRAMES)
    private val spriteInfo = arrayOfNulls<TeamInfo>(2)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val spriteDst = RectF()
    private val meshPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val numberStr = Array(100) { it.toString() }

    // ----- baked top-down rink (ice, markings, creases, nets), warped through the camera each frame
    private val bakeRect = RectF(-Rink.HALF_L - 3f, -Rink.HALF_W - 3f, Rink.HALF_L + 3f, Rink.HALF_W + 3f)
    private var rinkBake: Bitmap? = null
    private var bakeHome = 0
    private var bakeAway = 0
    private var bakePond = false
    private var artDirty = true

    // ----- draw order scratch (no per-frame allocation)
    private val orderIdx = IntArray(16)
    private val orderKey = FloatArray(16)
    private val scratchLines = FloatArray(120 * 4)

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
        artDirty = true
        buildWinterLandscape()
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

    private fun clearSprites() {
        for (a in skaterSpr) for (i in a.indices) { a[i]?.recycle(); a[i] = null }
        for (a in goalieSpr) for (i in a.indices) { a[i]?.recycle(); a[i] = null }
        for (i in refSpr.indices) { refSpr[i]?.recycle(); refSpr[i] = null }
        spriteInfo[0] = null
        spriteInfo[1] = null
    }

    private fun clearTeamSprites(t: Int) {
        for (i in skaterSpr[t].indices) { skaterSpr[t][i]?.recycle(); skaterSpr[t][i] = null }
        for (i in goalieSpr[t].indices) { goalieSpr[t][i]?.recycle(); goalieSpr[t][i] = null }
    }

    /** Rebuilds sprites, stands, walls and the baked rink when the zoom, teams or arena change. */
    private fun ensureArt(world: World) {
        val rScale = min(camera.scale, 13f)
        if (charArt == null || kotlin.math.abs(charArtScale - rScale) > 0.01f) {
            clearSprites()
            charArt = CharacterArt(rScale)
            charArtScale = rScale
        }
        for (t in 0..1) {
            if (spriteInfo[t] !== world.teams[t].info) {
                clearTeamSprites(t)
                spriteInfo[t] = world.teams[t].info
            }
        }
        val h = world.teams[0].info.primary
        val a = world.teams[1].info.primary
        val pond = world.arenaType == ArenaType.WINTER_POND
        if (!artDirty && h == bakeHome && a == bakeAway && pond == bakePond) return
        artDirty = false
        bakeHome = h
        bakeAway = a
        bakePond = pond
        if (!pond) {
            art.buildStands(h, a)
            art.buildWall(h, a)
        }
        bakeRink(world)
    }

    private fun bakeRink(world: World) {
        val px = 9f
        val bw = (bakeRect.width() * px).toInt()
        val bh = (bakeRect.height() * px).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(px, px)
        c.translate(-bakeRect.left, -bakeRect.top)
        drawRinkStatic(c, world)
        rinkBake?.recycle()
        rinkBake = bmp
    }

    // ================================================================ frame

    fun draw(canvas: Canvas, world: World, localTeam: Int, controls: TouchControls?, dt: Float) {
        animTime += dt
        ensureArt(world)
        camera.beginFrame()
        art.updateReferee(world, dt)
        art.updateFlashes(dt)
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
            for (t in 0..1) {
                val list = world.teams[t].skaters
                for (i in list.indices) {
                    val s = list[i]
                    if (s.speed > 11f && sprayRng.nextFloat() < 0.12f) {
                        val idx = scratchNext
                        scratchX1[idx] = s.x + (sprayRng.nextFloat() - 0.5f) * 0.8f
                        scratchY1[idx] = s.y + (sprayRng.nextFloat() - 0.5f) * 0.8f
                        val angle = s.facing + (sprayRng.nextFloat() - 0.5f) * 0.6f
                        val len = 1.0f + sprayRng.nextFloat() * 2.0f
                        scratchX2[idx] = scratchX1[idx] + cos(angle) * len
                        scratchY2[idx] = scratchY1[idx] + sin(angle) * len
                        scratchNext = (scratchNext + 1) % MAX_SCRATCHES
                        if (scratchCount < MAX_SCRATCHES) scratchCount++
                    }
                }
            }
        }

        val isPond = world.arenaType == ArenaType.WINTER_POND
        canvas.drawColor(if (isPond) Color.parseColor("#09101C") else Color.parseColor("#05080F"))
        if (isPond) {
            winterLandscape?.let {
                art.drawWarped(canvas, camera, it, crowdRect.left, crowdRect.top, crowdRect.right, crowdRect.bottom, 1, meshPaint)
            }
        } else {
            art.drawStands(canvas, camera)
            art.drawFlashes(canvas)
        }
        rinkBake?.let {
            art.drawWarped(canvas, camera, it, bakeRect.left, bakeRect.top, bakeRect.right, bakeRect.bottom, 0, meshPaint)
        }
        if (!isPond) art.drawWalls(canvas, camera)
        drawIceDynamic(canvas, world)
        drawObjects(canvas, world, localTeam, isPond)
        drawGlassShards(canvas, world)
        if (isPond) drawSnow(canvas)

        drawScoreboard(canvas, world)
        drawShootoutControls(canvas, world, localTeam)
        drawBanner(canvas, world)
        if (controls != null) drawControls(canvas, world, localTeam, controls)
        drawPauseButton(canvas)
    }

    // ================================================================ rink

    /** Ground circle/ellipse in world feet; stroke widths on [paint] are in pixels. */
    private fun groundOval(canvas: Canvas, wx: Float, wy: Float, rFt: Float, paint: Paint) {
        val k = camera.ppf(wy)
        val cx = camera.px(wx, wy)
        val cy = camera.py(wy)
        val rx = rFt * k
        tmpRect.set(cx - rx, cy - rx * camera.vk, cx + rx, cy + rx * camera.vk)
        canvas.drawOval(tmpRect, paint)
    }

    /** Things that change on the ice: scuffs, faceoff marker and the goal siren. */
    private fun drawIceDynamic(canvas: Canvas, world: World) {
        if (scratchCount > 0) {
            for (i in 0 until scratchCount) {
                scratchLines[i * 4] = camera.px(scratchX1[i], scratchY1[i])
                scratchLines[i * 4 + 1] = camera.py(scratchY1[i])
                scratchLines[i * 4 + 2] = camera.px(scratchX2[i], scratchY2[i])
                scratchLines[i * 4 + 3] = camera.py(scratchY2[i])
            }
            scratchPaint.alpha = 42
            scratchPaint.strokeWidth = 1.2f
            canvas.drawLines(scratchLines, 0, scratchCount * 4, scratchPaint)
        }
        if (world.phase == Phase.FACEOFF) {
            val r = 2.2f + 0.6f * sin(animTime * 8f)
            faceoffPulse.alpha = 200
            faceoffPulse.strokeWidth = 0.4f * camera.ppf(world.faceoffY)
            groundOval(canvas, world.faceoffX, world.faceoffY, r, faceoffPulse)
        }
        if (world.phase == Phase.GOAL) {
            val pulse = (sin(animTime * 14f) * 0.5f + 0.5f)
            sirenPaint.color = Color.argb((130 + pulse * 125).toInt(), 255, 20, 20)
            for (e in signs) {
                val bx = e * (Rink.GOAL_LINE_X + Rink.NET_DEPTH + 1.2f)
                val k = camera.ppf(0f)
                canvas.drawCircle(camera.px(bx, 0f), camera.py(0f) - 3.2f * k, (1.4f + pulse * 0.6f) * k, sirenPaint)
                sirenBeam.color = Color.argb((40 + pulse * 50).toInt(), 255, 40, 40)
                groundOval(canvas, bx, 0f, 7f, sirenBeam)
            }
        }
    }

    private val signs = floatArrayOf(-1f, 1f)

    /** The static top-down rink, drawn once into the bake bitmap in world feet. */
    private fun drawRinkStatic(canvas: Canvas, world: World) {
        val isPond = world.arenaType == ArenaType.WINTER_POND
        canvas.drawPath(rinkPath, if (isPond) pondBoardsPaint else boardsPaint)
        canvas.drawPath(rinkPath, if (isPond) pondIcePaint else icePaint)
        canvas.save()
        canvas.clipPath(rinkPath)
        if (!isPond) canvas.drawBitmap(art.iceOverlay, null, art.iceRect, art.iceOverlayPaint)

        if (isPond) {
            canvas.drawLine(-25f, -14f, -5f, 6f, pondCrackPaint)
            canvas.drawLine(-5f, 6f, 18f, 14f, pondCrackPaint)
            canvas.drawLine(18f, 14f, 32f, 11f, pondCrackPaint)
            canvas.drawLine(-55f, 12f, -30f, 24f, pondCrackPaint)
            canvas.drawLine(35f, -22f, 62f, -8f, pondCrackPaint)
            canvas.drawLine(-12f, -25f, 8f, -18f, pondCrackPaint)
        }

        val shade = if (isPond) pondIceShadePaint else iceShadePaint
        tmpRect.set(-Rink.HALF_L, -Rink.HALF_W, -Rink.GOAL_LINE_X, Rink.HALF_W)
        canvas.drawRect(tmpRect, shade)
        tmpRect.set(Rink.GOAL_LINE_X, -Rink.HALF_W, Rink.HALF_L, Rink.HALF_W)
        canvas.drawRect(tmpRect, shade)

        canvas.drawLine(-Rink.GOAL_LINE_X, -Rink.HALF_W, -Rink.GOAL_LINE_X, Rink.HALF_W, redLine)
        canvas.drawLine(Rink.GOAL_LINE_X, -Rink.HALF_W, Rink.GOAL_LINE_X, Rink.HALF_W, redLine)
        canvas.drawLine(-Rink.BLUE_LINE_X, -Rink.HALF_W, -Rink.BLUE_LINE_X, Rink.HALF_W, blueLine)
        canvas.drawLine(Rink.BLUE_LINE_X, -Rink.HALF_W, Rink.BLUE_LINE_X, Rink.HALF_W, blueLine)
        canvas.drawLine(0f, -Rink.HALF_W, 0f, Rink.HALF_W, centerLine)

        logoPaint.color = world.teams[0].info.primary
        logoPaint.alpha = 60
        canvas.drawCircle(0f, 0f, 10f, logoPaint)
        logoText.color = world.teams[0].info.primary
        logoText.alpha = 120
        canvas.drawText(world.teams[0].info.abbr, 0f, 2.6f, logoText)
        canvas.drawCircle(0f, 0f, Rink.FACEOFF_R, circleBlue)
        canvas.drawCircle(0f, 0f, 1f, dotBlue)

        for (sx in signs) {
            for (sy in signs) {
                val cx = sx * Rink.END_DOT_X
                val cy = sy * Rink.DOT_Y
                canvas.drawCircle(cx, cy, Rink.FACEOFF_R, circleRed)
                canvas.drawCircle(cx, cy, 1f, dotRed)
                canvas.drawLine(cx - 3f, cy - Rink.FACEOFF_R - 2f, cx - 3f, cy - Rink.FACEOFF_R, circleRed)
                canvas.drawLine(cx + 3f, cy - Rink.FACEOFF_R - 2f, cx + 3f, cy - Rink.FACEOFF_R, circleRed)
                canvas.drawLine(cx - 3f, cy + Rink.FACEOFF_R, cx - 3f, cy + Rink.FACEOFF_R + 2f, circleRed)
                canvas.drawLine(cx + 3f, cy + Rink.FACEOFF_R, cx + 3f, cy + Rink.FACEOFF_R + 2f, circleRed)
                canvas.drawCircle(sx * Rink.NEUTRAL_DOT_X, cy, 1f, dotRed)
            }
        }
        if (!isPond) art.drawMarkings(canvas)

        for (e in signs) {
            val gx = e * Rink.GOAL_LINE_X
            tmpRect.set(gx - Rink.CREASE_R, -Rink.CREASE_R, gx + Rink.CREASE_R, Rink.CREASE_R)
            val start = if (e > 0f) 90f else -90f
            canvas.drawArc(tmpRect, start, 180f, true, creaseFill)
            canvas.drawArc(tmpRect, start, 180f, false, circleRed)
            canvas.drawLine(gx, -11f, e * Rink.HALF_L, -14f, trapezoid)
            canvas.drawLine(gx, 11f, e * Rink.HALF_L, 14f, trapezoid)
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
        for (i in 0 until 12) {
            val s = world.teams[i / 6].skaters[i % 6]
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
            val rad = (0.22f + (1f - t) * 0.28f) * camera.ppf(sprayY[i])
            sprayOuter.alpha = (170 * t).toInt()
            sprayInner.alpha = (230 * t).toInt()
            val cx = camera.px(sprayX[i], sprayY[i])
            val cy = camera.py(sprayY[i])
            canvas.drawCircle(cx, cy, rad, sprayOuter)
            canvas.drawCircle(cx, cy, rad * 0.55f, sprayInner)
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
            canvas.drawCircle(camera.px(snowX[i], snowY[i]), camera.py(snowY[i]), snowR[i] * camera.ppf(snowY[i]), snowflakePaint)
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

        for (bi in 0 until 12) {
            val s = world.teams[bi / 6].skaters[bi % 6]
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
            val k = camera.ppf(breathY[i])
            val rad = (breathR[i] + (1f - frac) * 0.45f) * k
            breathPaint.alpha = (130 * frac).toInt()
            canvas.drawCircle(camera.px(breathX[i], breathY[i]), camera.py(breathY[i]) - 5.4f * k, rad, breathPaint)
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
            glassSpiderwebPaint.alpha = (240 * frac).toInt()
            glassSpiderwebFill.alpha = (50 * frac).toInt()
            val k = camera.ppf(world.glassShatterY)
            canvas.save()
            canvas.translate(camera.px(world.glassShatterX, world.glassShatterY), camera.py(world.glassShatterY) - 5.5f * k)
            canvas.scale(k, k)
            canvas.drawCircle(0f, 0f, 1.8f, glassSpiderwebFill)
            val rings = floatArrayOf(1.2f, 2.6f, 4.2f)
            for (r in rings) {
                tmpPath.reset()
                for (s in 0..7) {
                    val a = s * (Math.PI / 4) + (s % 2) * 0.15
                    val dist = r * (0.8f + (s % 3) * 0.18f)
                    val px = (cos(a) * dist).toFloat()
                    val py = (sin(a) * dist).toFloat()
                    if (s == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
                }
                tmpPath.close()
                canvas.drawPath(tmpPath, glassSpiderwebPaint)
            }
            for (s in 0..11) {
                val a = s * (Math.PI / 6) + ((s * 7) % 5) * 0.08
                val len = 3.5f + ((s * 11) % 4) * 1.5f
                canvas.drawLine(0f, 0f, (cos(a) * len).toFloat(), (sin(a) * len).toFloat(), glassSpiderwebPaint)
            }
            canvas.restore()
        }

        for (i in 0 until MAX_SHARDS) {
            val life = shardLife[i]
            if (life <= 0f) continue
            val frac = (life / shardMax[i]).coerceIn(0f, 1f)
            val sz = shardSize[i]
            val k = camera.ppf(shardY[i])
            canvas.save()
            canvas.translate(camera.px(shardX[i], shardY[i]), camera.py(shardY[i]) - 4f * k * frac)
            canvas.rotate(shardRot[i])
            canvas.scale(k, k)
            glassShardFill.alpha = (190 * frac).toInt()
            glassShardEdge.alpha = (250 * frac).toInt()
            tmpPath.reset()
            tmpPath.moveTo(-sz * 0.6f, -sz * 0.9f)
            tmpPath.lineTo(sz * 0.9f, -sz * 0.2f)
            tmpPath.lineTo(sz * 0.2f, sz * 0.9f)
            tmpPath.close()
            canvas.drawPath(tmpPath, glassShardFill)
            canvas.drawPath(tmpPath, glassShardEdge)
            canvas.restore()
        }
    }

    // ================================================================ players

    private fun ovalPx(canvas: Canvas, cx: Float, cy: Float, rx: Float, paint: Paint) {
        tmpRect.set(cx - rx, cy - rx * camera.vk, cx + rx, cy + rx * camera.vk)
        canvas.drawOval(tmpRect, paint)
    }

    private fun drawShadow(canvas: Canvas, s: Skater) {
        val k = camera.ppf(s.y)
        val cx = camera.px(s.x, s.y)
        val cy = camera.py(s.y)
        val r = s.radius * 1.15f * BODY_SCALE * k
        ovalPx(canvas, cx + 0.35f * k, cy + 0.2f * k, r, shadowPaint)
        ovalPx(canvas, cx, cy + 0.1f * k, r * 0.7f, contactShadow)
    }

    private fun drawBillboard(canvas: Canvas, ch: CharacterArt, bmp: Bitmap, sx: Float, sy: Float, scale: Float) {
        val l = sx - ch.anchorX * scale
        val t = sy - ch.anchorY * scale
        spriteDst.set(l, t, l + bmp.width * scale, t + bmp.height * scale)
        canvas.drawBitmap(bmp, null, spriteDst, spritePaint)
    }

    private fun drawObjects(canvas: Canvas, world: World, localTeam: Int, isPond: Boolean) {
        val controlled = if (localTeam >= 0) world.controlledSkater(localTeam) else null
        val charge = if (localTeam >= 0) world.shotCharge[localTeam] else 0f
        var n = 0
        for (t in 0..1) {
            val list = world.teams[t].skaters
            for (i in list.indices) {
                val s = list[i]
                if (world.isShootout && kotlin.math.abs(s.y) >= 45f) continue
                drawShadow(canvas, s)
                orderIdx[n] = t * 6 + i
                orderKey[n] = s.y
                n++
            }
        }
        val showRef = !isPond && !world.isShootout
        if (showRef) {
            val k = camera.ppf(art.refY)
            val cx = camera.px(art.refX, art.refY)
            val cy = camera.py(art.refY)
            ovalPx(canvas, cx + 0.3f * k, cy + 0.2f * k, 1.9f * k, shadowPaint)
        }
        drawSpray(canvas)
        orderIdx[n] = 100
        orderKey[n] = world.puck.y
        n++
        if (showRef) {
            orderIdx[n] = 101
            orderKey[n] = art.refY
            n++
        }
        for (i in 1 until n) {
            val ki = orderKey[i]
            val ii = orderIdx[i]
            var j = i - 1
            while (j >= 0 && orderKey[j] > ki) {
                orderKey[j + 1] = orderKey[j]
                orderIdx[j + 1] = orderIdx[j]
                j--
            }
            orderKey[j + 1] = ki
            orderIdx[j + 1] = ii
        }
        for (q in 0 until n) {
            val id = orderIdx[q]
            if (id == 100) drawPuck(canvas, world.puck)
            else if (id == 101) drawReferee(canvas, world)
            else {
                val s = world.teams[id / 6].skaters[id % 6]
                drawSkater(canvas, world, s, s === controlled, charge)
            }
        }
        drawBreath(canvas)
    }

    private fun drawReferee(canvas: Canvas, world: World) {
        val ch = charArt ?: return
        val k = camera.ppf(art.refY)
        val fi = CharacterArt.facingIndex(art.refAngle)
        var frame = ((art.refStride * 1.3f / (2f * PI.toFloat())) * CharacterArt.STRIDE_FRAMES).toInt() % CharacterArt.STRIDE_FRAMES
        if (frame < 0) frame += CharacterArt.STRIDE_FRAMES
        val idx = frame * CharacterArt.FACINGS + fi
        val bmp = refSpr[idx] ?: ch.skater(world.teams[0].info, fi, frame, true).also { refSpr[idx] = it }
        drawBillboard(canvas, ch, bmp, camera.px(art.refX, art.refY), camera.py(art.refY), k / ch.pxPerFt)
    }

    private fun drawSkater(canvas: Canvas, world: World, s: Skater, controlled: Boolean, charge: Float) {
        val ch = charArt ?: return
        val info = world.teams[s.team].info
        val r = s.radius
        val k = camera.ppf(s.y)
        val sx = camera.px(s.x, s.y)
        val sy = camera.py(s.y)
        if (controlled) {
            val pulse = 1f + 0.06f * sin(animTime * 6f)
            ringPaint.strokeWidth = 0.3f * k
            groundOval(canvas, s.x, s.y, r * 2.2f * pulse, ringGlow)
            groundOval(canvas, s.x, s.y, r * 2.2f * pulse, ringPaint)
        }
        var ang = s.facing
        if (s.dekeTimer > 0f) ang += s.dekeDir * (s.dekeTimer / 0.38f) * 0.38f
        val fi = CharacterArt.facingIndex(ang)
        val stunned = s.stunTimer > 0f
        val scale = k / ch.pxPerFt
        val spr: Bitmap
        if (s.isGoalie) {
            val stance = when {
                s.goalieAction == GoalieAction.PAD_STACK -> if (s.padStackDir >= 0f) 2 else 3
                s.goalieAction == GoalieAction.BUTTERFLY || s.butterfly -> 1
                else -> 0
            }
            val idx = stance * CharacterArt.FACINGS + fi
            spr = goalieSpr[s.team][idx] ?: ch.goalie(info, fi, stance).also { goalieSpr[s.team][idx] = it }
        } else {
            val frame = when {
                stunned -> CharacterArt.F_FALLEN
                s.pokeTimer > 0f -> CharacterArt.F_POKE
                s.swingTimer > 0f -> if (1f - s.swingTimer / 0.35f < 0.4f) CharacterArt.F_WIND else CharacterArt.F_FOLLOW
                controlled && charge > 0.05f && world.puck.carrier === s -> CharacterArt.F_WIND
                s.speed > 2f -> {
                    val f = ((s.stride * 1.3f / (2f * PI.toFloat())) * CharacterArt.STRIDE_FRAMES).toInt() % CharacterArt.STRIDE_FRAMES
                    if (f < 0) f + CharacterArt.STRIDE_FRAMES else f
                }
                else -> 0
            }
            val idx = frame * CharacterArt.FACINGS + fi
            spr = skaterSpr[s.team][idx] ?: ch.skater(info, fi, frame, false).also { skaterSpr[s.team][idx] = it }
        }
        drawBillboard(canvas, ch, spr, sx, sy, scale)

        if (world.isOnFire(s.team)) {
            for (j in 0..3) {
                val fPhase = (animTime * 9f + j * 0.25f + s.index * 0.37f) % 1f
                val offBack = 0.8f + fPhase * 1.5f
                val jiggle = sin(animTime * 16f + j * 2.3f) * 0.32f
                val fx = s.x - cos(s.facing) * offBack - sin(s.facing) * jiggle
                val fy = s.y - sin(s.facing) * offBack + cos(s.facing) * jiggle
                val fr = (0.36f * (1f - fPhase * 0.7f)).coerceAtLeast(0.08f)
                val alpha = (235 * (1f - fPhase)).toInt().coerceIn(0, 255)
                fireEmberPaint.color = if (j % 2 == 0) Color.argb(alpha, 255, 90, 10) else Color.argb(alpha, 255, 210, 30)
                val fk = camera.ppf(fy)
                canvas.drawCircle(camera.px(fx, fy), camera.py(fy) - 0.9f * fk, fr * fk * 1.6f, fireEmberPaint)
            }
        }

        // Jersey number on the back when he skates away from the camera.
        val bk = ch.backness(fi)
        if (bk > 0f && !stunned) {
            val nx = sx + ch.numberDx(fi) * scale
            val ny = sy + ch.numberDy(fi) * scale
            val ts = 1.55f * k
            val str = if (s.number in 0..99) numberStr[s.number] else s.number.toString()
            canvas.save()
            canvas.translate(nx, ny)
            canvas.scale(max(bk, 0.45f), 1f)
            numberShadowPaint.textSize = ts
            numberShadowPaint.strokeWidth = 0.3f * k
            numberShadowPaint.style = Paint.Style.STROKE
            canvas.drawText(str, 0f, ts * 0.35f, numberShadowPaint)
            numberPaint.color = info.text
            numberPaint.textSize = ts
            numberPaint.style = Paint.Style.FILL
            canvas.drawText(str, 0f, ts * 0.35f, numberPaint)
            canvas.restore()
        }

        if (stunned) {
            starPaint.strokeWidth = 0.14f * k
            val kk = 0.32f * k
            for (i in 0..2) {
                val a = animTime * 5f + i * 2.094f
                val px = sx + cos(a) * r * 1.5f * k
                val py = sy - 6.9f * k + sin(a) * r * 0.5f * k
                canvas.drawLine(px - kk, py, px + kk, py, starPaint)
                canvas.drawLine(px, py - kk, px, py + kk, starPaint)
                canvas.drawLine(px - kk * 0.6f, py - kk * 0.6f, px + kk * 0.6f, py + kk * 0.6f, starPaint)
                canvas.drawLine(px - kk * 0.6f, py + kk * 0.6f, px + kk * 0.6f, py - kk * 0.6f, starPaint)
            }
        }

        if (controlled && charge > 0f && world.puck.carrier === s) {
            val rr = r * 1.9f * k
            tmpRect.set(sx - rr, sy - rr * camera.vk, sx + rr, sy + rr * camera.vk)
            meterBack.strokeWidth = 0.5f * k
            meterFill.strokeWidth = 0.5f * k
            canvas.drawArc(tmpRect, -210f, 240f, false, meterBack)
            canvas.drawArc(tmpRect, -210f, 240f * charge, false, meterFill)
        }
    }

    private fun drawPuck(canvas: Canvas, p: Puck) {
        val sp = p.speed
        puckTrailX[puckTrailHead] = p.x
        puckTrailY[puckTrailHead] = p.y
        puckTrailHead = (puckTrailHead + 1) % TRAIL_POINTS
        val k = camera.ppf(p.y)
        val cx = camera.px(p.x, p.y)
        val cy = camera.py(p.y)

        if (p.carrier == null && sp > 35f) {
            val isBoomer = sp > 95f
            for (i in 1 until TRAIL_POINTS) {
                val currIdx = (puckTrailHead - i + TRAIL_POINTS) % TRAIL_POINTS
                val prevIdx = (puckTrailHead - i - 1 + TRAIL_POINTS) % TRAIL_POINTS
                val frac = 1f - (i / TRAIL_POINTS.toFloat())
                val alpha = (frac * 190).toInt()
                if (isBoomer) {
                    cometTrail.color = Color.argb(alpha, 255, (120 * frac + 30).toInt(), 20)
                    cometTrail.strokeWidth = (1.0f * frac + 0.3f) * k
                } else {
                    cometTrail.color = Color.argb(alpha, 56, 189, 248)
                    cometTrail.strokeWidth = (0.65f * frac + 0.2f) * k
                }
                canvas.drawLine(
                    camera.px(puckTrailX[prevIdx], puckTrailY[prevIdx]), camera.py(puckTrailY[prevIdx]),
                    camera.px(puckTrailX[currIdx], puckTrailY[currIdx]), camera.py(puckTrailY[currIdx]), cometTrail
                )
            }
            if (isBoomer) {
                cometGlow.color = Color.argb(130, 255, 140, 20)
                ovalPx(canvas, cx, cy, 2.0f * k, cometGlow)
            }
        }
        ovalPx(canvas, cx + 0.25f * k, cy + 0.2f * k, 0.95f * k, shadowPaint)
        ovalPx(canvas, cx, cy + 0.12f * k, 0.85f * k, contactShadow)
        // A fat disc: dark side wall below, lit face above.
        ovalPx(canvas, cx, cy + 0.2f * k, 0.9f * k, puckPaint)
        ovalPx(canvas, cx, cy, 0.9f * k, puckHalo)
        ovalPx(canvas, cx, cy, 0.8f * k, puckPaint)
        ovalPx(canvas, cx, cy, 0.5f * k, puckBevelFill)
        ovalPx(canvas, cx - 0.2f * k, cy - 0.1f * k, 0.2f * k, glossPaint)
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
        val sx = camera.toScreenX(shooter.x, shooter.y)
        val sy = camera.toScreenY(shooter.y)
        return kotlin.math.hypot(x - sx, y - sy) <= dp(45f)
    }

    fun release() {
        rinkBake?.recycle()
        rinkBake = null
        winterLandscape?.recycle()
        winterLandscape = null
        clearSprites()
    }
}
