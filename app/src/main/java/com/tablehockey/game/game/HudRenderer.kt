package com.tablehockey.game.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.tablehockey.game.model.TeamInfo
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen-space HUD and "moment" drawing: compact broadcast score pill, restyled touch
 * buttons, banner plates, goal celebration (flash, spotlight beams, team band, confetti),
 * pre-game versus splash and the trophy / confetti finish.
 *
 * Read-only with respect to [World]; all animation state lives here and is advanced by
 * [update], called once per frame before drawing. Paints, paths and particle arrays are
 * preallocated; per-frame strings are cached and only rebuilt when their value changes.
 */
class HudRenderer(private val density: Float) {

    private fun dp(v: Float) = v * density

    private val K_92400E = Color.parseColor("#92400E")
    private val K_FDE68A = Color.parseColor("#FDE68A")
    private val K_1B2230 = Color.parseColor("#1B2230")
    private val K_0B1426 = Color.parseColor("#0B1426")
    private val K_22C55E = Color.parseColor("#22C55E")
    private val K_2563EB = Color.parseColor("#2563EB")
    private val K_3B82F6 = Color.parseColor("#3B82F6")
    private val K_64748B = Color.parseColor("#64748B")
    private val K_8B5CF6 = Color.parseColor("#8B5CF6")
    private val K_9FB3CC = Color.parseColor("#9FB3CC")
    private val K_B45309 = Color.parseColor("#B45309")
    private val K_B91C1C = Color.parseColor("#B91C1C")
    private val K_CBD5E1 = Color.parseColor("#CBD5E1")
    private val K_D97706 = Color.parseColor("#D97706")
    private val K_DC2626 = Color.parseColor("#DC2626")
    private val K_EA580C = Color.parseColor("#EA580C")
    private val K_EF4444 = Color.parseColor("#EF4444")
    private val K_F59E0B = Color.parseColor("#F59E0B")
    private val K_F87171 = Color.parseColor("#F87171")
    private val K_F97316 = Color.parseColor("#F97316")
    private val K_FBBF24 = Color.parseColor("#FBBF24")
    private val K_FDE047 = Color.parseColor("#FDE047")
    private val K_FEF2F2 = Color.parseColor("#FEF2F2")
    private val K_FFF3B0 = Color.parseColor("#FFF3B0")

    // ---------------------------------------------------------------- state
    private var sw = 0f
    private var sh = 0f
    private var worldRef: World? = null
    private val lastScore = IntArray(2)
    private val scorePop = FloatArray(2)
    private var celebT = -1f
    private var celebTeam = 0
    private var celebLocal = true
    private val celebScore = IntArray(2)
    private var bannerAge = 0f
    private var lastBannerText: String? = null
    private var lastBannerTimer = 0f
    private var introT = -1f
    private var finalT = -1f
    private var anim = 0f
    private var localTeamId = 0
    private val press = FloatArray(4)
    private val rng = Random(11)

    // cached strings
    private val teamRef = arrayOfNulls<TeamInfo>(2)
    private val nameUp = arrayOf("", "")
    private val cityUp = arrayOf("", "")
    private val emptyNetStr = arrayOf("", "")
    private val numStr = Array(100) { it.toString() }
    private var clockKey = -1
    private var clockStr = ""
    private var shotsKey = -1
    private var shotsStr = ""
    private var ppKey = -1
    private var ppStr = ""
    private var soKey = -1
    private var soStr = ""
    private var soTimeKey = -1
    private var soTimeStr = ""
    private val fireKey = intArrayOf(-1, -1)
    private val fireStr = arrayOf("", "")
    private var finalScoreStr = ""
    private var celebScoreStr = ""
    private val sb = StringBuilder()

    private fun num(n: Int) = if (n in 0..99) numStr[n] else n.toString()

    // confetti
    private val MAXC = 200
    private val cx = FloatArray(MAXC)
    private val cy = FloatArray(MAXC)
    private val cvx = FloatArray(MAXC)
    private val cvy = FloatArray(MAXC)
    private val cph = FloatArray(MAXC)
    private val cbk = IntArray(MAXC)
    private val cang = FloatArray(MAXC)
    private val cspin = FloatArray(MAXC)
    private var cn = 0
    private val bucketPts = Array(4) { FloatArray(MAXC * 4) }
    private val bucketN = IntArray(4)
    private val bucketColor = IntArray(4)
    private val confPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }

    // ---------------------------------------------------------------- paints
    private val rect = RectF()
    private val path = Path()
    private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) }
    private val pPlate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 9, 15, 30) }
    private val pScoreBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 6, 10, 22) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pGloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTextC = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pTextL = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
    private val pBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER; textSkewX = -0.15f
    }
    private val pVignette = Paint()
    private var vignetteShader: Shader? = null
    private val pRays = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crestPath = Path().apply {
        moveTo(-0.8f, -0.9f); lineTo(0.8f, -0.9f); lineTo(0.8f, 0.1f)
        cubicTo(0.8f, 0.6f, 0.3f, 0.85f, 0f, 1.0f)
        cubicTo(-0.3f, 0.85f, -0.8f, 0.6f, -0.8f, 0.1f); close()
    }
    private val trophyBody = Path().apply {
        moveTo(-0.55f, -0.95f); lineTo(0.55f, -0.95f)
        cubicTo(0.55f, -0.25f, 0.3f, 0.05f, 0.1f, 0.12f); lineTo(0.1f, 0.5f)
        lineTo(0.22f, 0.62f); lineTo(0.38f, 0.62f); lineTo(0.38f, 0.74f); lineTo(0.5f, 0.74f); lineTo(0.5f, 0.92f)
        lineTo(-0.5f, 0.92f); lineTo(-0.5f, 0.74f); lineTo(-0.38f, 0.74f); lineTo(-0.38f, 0.62f); lineTo(-0.22f, 0.62f)
        lineTo(-0.1f, 0.5f); lineTo(-0.1f, 0.12f)
        cubicTo(-0.3f, 0.05f, -0.55f, -0.25f, -0.55f, -0.95f); close()
    }
    private val trophyHandles = Path().apply {
        moveTo(-0.55f, -0.8f); cubicTo(-1.05f, -0.8f, -0.95f, -0.1f, -0.3f, 0.0f)
        moveTo(0.55f, -0.8f); cubicTo(1.05f, -0.8f, 0.95f, -0.1f, 0.3f, 0.0f)
    }
    private val starPath = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 0.26f else 0.11f
            val a = (-PI / 2 + i * PI / 5).toFloat()
            if (i == 0) moveTo(cos(a) * r, sin(a) * r - 0.5f) else lineTo(cos(a) * r, sin(a) * r - 0.5f)
        }
        close()
    }
    private val tmpLines = FloatArray(32)
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamShader = arrayOfNulls<Shader>(2)
    private val glowShader = arrayOfNulls<Shader>(2)
    private var celebWho = ""
    private var celebScorer: Skater? = null
    private var celebLight = false
    private var celebDur = CEL_DUR
    private var leagueUp = ""
    private var introCap = ""
    private val fullUp = arrayOf("", "")
    private var periodKey = -1
    private var periodStr = ""
    private var trophyBmp: Bitmap? = null
    private var trophyFor: TeamInfo? = null
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val statL = arrayOf("", "", "")
    private val statR = arrayOf("", "", "")
    private val statFrac = FloatArray(3)
    private var statP0 = 0f
    private var statP1 = 0f
    private var statRows = 3
    private val rayPath = Path()
    private val ellipse = RectF()
    private val goldGlowShader: Shader = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(120, 255, 214, 90), Color.argb(50, 255, 190, 60), Color.argb(0, 255, 180, 40)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val tFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tRect = RectF()
    private fun lg(x0: Float, y0: Float, x1: Float, y1: Float, cols: IntArray, pos: FloatArray? = null): Shader =
        LinearGradient(x0, y0, x1, y1, cols, pos, Shader.TileMode.CLAMP)
    private val shPedTop by lazy { lg(0f, 0.92f, 0f, 1.08f, intArrayOf(0xFF566074.toInt(), 0xFF2A3242.toInt(), 0xFF141A26.toInt())) }
    private val shPedBot by lazy { lg(0f, 1.08f, 0f, 1.18f, intArrayOf(0xFF3A4458.toInt(), 0xFF0E121B.toInt())) }
    private val shHandle by lazy { lg(0f, -0.9f, 0f, 0.1f, intArrayOf(0xFFFFE08A.toInt(), 0xFFD97706.toInt())) }
    private val shBody by lazy {
        lg(-0.55f, 0f, 0.55f, 0f,
            intArrayOf(0xFF8A3B0C.toInt(), 0xFFF59E0B.toInt(), 0xFFFFF6C7.toInt(), 0xFFFBBF24.toInt(), 0xFFB45309.toInt(), 0xFF6B2D0A.toInt()),
            floatArrayOf(0f, 0.22f, 0.38f, 0.58f, 0.82f, 1f))
    }
    private val shBodyV by lazy {
        lg(0f, -1f, 0f, 0.95f, intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 0, 0, 0), Color.argb(90, 40, 15, 0)), floatArrayOf(0f, 0.45f, 1f))
    }
    private val shKnob by lazy { lg(-0.2f, 0f, 0.2f, 0f, intArrayOf(0xFFB45309.toInt(), 0xFFFFF0A8.toInt(), 0xFFB45309.toInt())) }
    private val shRim by lazy { lg(-0.55f, 0f, 0.55f, 0f, intArrayOf(0xFFB45309.toInt(), 0xFFFFF6C7.toInt(), 0xFFB45309.toInt())) }
    private val shHot1 by lazy { RadialGradient(-0.3f, -0.55f, 0.22f, Color.argb(210, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP) }
    private val shHot2 by lazy { RadialGradient(0f, 0.34f, 0.2f, Color.argb(190, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP) }
    private val shFade by lazy { lg(0f, 1.18f, 0f, 1.8f, intArrayOf(Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255))) }
    private val fadePaint by lazy { Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN); shader = shFade } }
    private val trophyBounds = RectF(-1.2f, 1.18f, 1.2f, 1.8f)
    private val statLabel = arrayOf("GOALS", "SHOTS ON GOAL", "SHOOTING %")
    private val redGlow: Shader = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(255, 255, 70, 50), Color.argb(130, 255, 30, 20), Color.argb(0, 255, 0, 0)),
        floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    private var celebSub = ""

    private fun layout(w: Int, h: Int) {
        sw = w.toFloat(); sh = h.toFloat()
        pGloss.shader = LinearGradient(0f, dp(8f), 0f, dp(8f) + dp(17f), Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        vignetteShader = LinearGradient(0f, 0f, 0f, sh,
            intArrayOf(Color.argb(95, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(95, 0, 0, 0)),
            floatArrayOf(0f, 0.2f, 0.8f, 1f), Shader.TileMode.CLAMP)
        pVignette.shader = vignetteShader
        rayPath.reset()
        val rr = max(sw, sh)
        for (i in 0 until 12) {
            val a0 = i * (PI / 6).toFloat()
            val a1 = a0 + (PI / 12).toFloat()
            rayPath.moveTo(0f, 0f); rayPath.lineTo(cos(a0) * rr, sin(a0) * rr); rayPath.lineTo(cos(a1) * rr, sin(a1) * rr); rayPath.close()
        }
    }

    // ---------------------------------------------------------------- update

    fun update(w: World, localTeam: Int, dt: Float, screenW: Int, screenH: Int) {
        if (screenW.toFloat() != sw || screenH.toFloat() != sh) layout(screenW, screenH)
        val d = dt.coerceIn(0f, 0.1f)
        anim += d
        localTeamId = localTeam
        if (w !== worldRef || w.teams[0].score < lastScore[0] || w.teams[1].score < lastScore[1]) {
            worldRef = w
            resetState(w)
        }
        for (i in 0..1) {
            val info = w.teams[i].info
            if (teamRef[i] !== info) {
                teamRef[i] = info
                nameUp[i] = info.name.uppercase()
                cityUp[i] = info.city.uppercase()
                fullUp[i] = info.fullName.uppercase()
                if (i == 0) introCap = info.league.uppercase() + "  -  PUCK DROP"
                emptyNetStr[i] = info.abbr + " EMPTY NET"
                val bc = bright(info.primary)
                val beamC = Color.rgb((Color.red(bc) + 255) / 2, (Color.green(bc) + 255) / 2, (Color.blue(bc) + 255) / 2)
                beamShader[i] = LinearGradient(0f, 0f, 0f, sh * 0.78f, Color.argb(235, Color.red(beamC), Color.green(beamC), Color.blue(beamC)),
                    Color.argb(0, Color.red(beamC), Color.green(beamC), Color.blue(beamC)), Shader.TileMode.CLAMP)
                glowShader[i] = RadialGradient(0f, 0f, 1f, intArrayOf(Color.argb(230, Color.red(bc), Color.green(bc), Color.blue(bc)),
                    Color.argb(110, Color.red(bc), Color.green(bc), Color.blue(bc)), Color.argb(0, Color.red(bc), Color.green(bc), Color.blue(bc))),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                ppKey = -1; soKey = -1
            }
            if (w.teams[i].score > lastScore[i]) {
                scorePop[i] = 0.7f
                startCelebration(w, i)
                lastScore[i] = w.teams[i].score
            }
            if (scorePop[i] > 0f) scorePop[i] = max(0f, scorePop[i] - d)
        }

        if (celebT >= 0f) {
            celebT += d
            if (celebT > celebDur) celebT = -1f
        }
        if (introT >= 0f) {
            introT += d
            if (introT > INTRO_DUR) introT = -1f
        }

        val b = w.banner
        if (b == null || w.bannerTimer <= 0f) {
            bannerAge = 0f; lastBannerText = null; lastBannerTimer = 0f
        } else {
            if (b !== lastBannerText || w.bannerTimer > lastBannerTimer + 0.02f) bannerAge = 0f else bannerAge += d
            lastBannerText = b
            lastBannerTimer = w.bannerTimer
        }

        if (w.phase == Phase.GAME_OVER) {
            if (finalT < 0f) {
                finalT = 0f
                val win = winnerOf(w)
                buildStats(w)
                finalScoreStr = buildFinalScore(w)
                if (win >= 0) {
                    setConfettiColors(w.teams[win].info)
                    if (trophyFor !== w.teams[win].info) {
                        trophyBmp?.recycle()
                        trophyBmp = buildTrophy(w.teams[win].info)
                        trophyFor = w.teams[win].info
                    }
                    if (localTeam < 0 || win == localTeam) spawnBurst(60)
                }
            } else finalT += d
            if (finalT < 8f) {
                val win = winnerOf(w)
                if (win >= 0 && (localTeam < 0 || win == localTeam) && rng.nextFloat() < d * 22f) spawnRain(1)
            }
        } else finalT = -1f

        updateConfetti(d)
        for (i in 0..3) press[i] += ((if (pressedNow[i]) 1f else 0f) - press[i]) * min(1f, d * 18f)
    }

    private val pressedNow = BooleanArray(4)

    private fun resetState(w: World) {
        lastScore[0] = w.teams[0].score; lastScore[1] = w.teams[1].score
        scorePop[0] = 0f; scorePop[1] = 0f
        celebT = -1f; finalT = -1f; cn = 0
        bannerAge = 0f; lastBannerText = null
        val fresh = !w.isShootout && w.period == 1 && !w.overtime && lastScore[0] == 0 && lastScore[1] == 0 &&
            w.phase == Phase.FACEOFF && w.clock >= w.periodLength - 1f
        introT = if (fresh) 0f else -1f
        teamRef[0] = null; teamRef[1] = null
        clockKey = -1; shotsKey = -1
    }

    private fun winnerOf(w: World): Int =
        if (w.teams[0].score > w.teams[1].score) 0 else if (w.teams[1].score > w.teams[0].score) 1 else -1

    private fun scoreLine(w: World): String {
        sb.setLength(0)
        sb.append(w.teams[0].info.abbr).append(' ').append(w.teams[0].score).append("  -  ")
            .append(w.teams[1].score).append(' ').append(w.teams[1].info.abbr)
        return sb.toString()
    }

    private fun buildFinalScore(w: World) = scoreLine(w)

    private fun startCelebration(w: World, team: Int) {
        celebT = 0f
        celebTeam = team
        celebLocal = localTeamId < 0 || localTeamId == team
        celebScoreStr = scoreLine(w)
        // puck.shooter is not part of the network snapshot, so on WiFi/Bluetooth clients the scorer is
        // unknown: the ring is skipped and the caption falls back to the team name.
        val sc = w.puck.shooter
        celebScorer = if (sc != null && sc.team == team) sc else null
        celebWho = if (celebScorer != null) "#" + sc!!.number + " " + sc.role.label else ""
        val full = fullUp[team]
        celebSub = if (celebWho.isEmpty()) full else "$celebWho  -  $full"
        celebLight = w.isShootout
        celebDur = if (celebLight) 1.9f else CEL_DUR
        setConfettiColors(w.teams[team].info)
        spawnBurst(if (celebLight) 20 else if (celebLocal) 80 else 24)
    }

    private fun buildStats(w: World) {
        val g0 = w.teams[0].score; val g1 = w.teams[1].score
        val s0 = w.teams[0].shots; val s1 = w.teams[1].shots
        val p0 = if (s0 > 0) min(100, g0 * 100 / s0) else 0
        val p1 = if (s1 > 0) min(100, g1 * 100 / s1) else 0
        statL[0] = g0.toString(); statR[0] = g1.toString(); statFrac[0] = frac(g0, g1)
        statL[1] = s0.toString(); statR[1] = s1.toString(); statFrac[1] = frac(s0, s1)
        statL[2] = "$p0%"; statR[2] = "$p1%"
        statP0 = p0 / 100f; statP1 = p1 / 100f
        // shots are not synced to network clients; with no shot data only the goals row is meaningful
        statRows = if (s0 + s1 == 0) 1 else 3
    }

    private fun frac(a: Int, b: Int) = if (a + b <= 0) 0.5f else a.toFloat() / (a + b)

    fun release() {
        trophyBmp?.recycle(); trophyBmp = null; trophyFor = null
    }

    /** Full trophy + pedestal in unit space (x -1.15..1.15, y -1.12..1.18); floor line at y = 1.18. Build-time only. */
    private fun drawTrophyVec(c: Canvas, info: TeamInfo) {
        val fill = tFill
        val st = tStroke
        val rr = tRect
        fill.shader = shPedTop
        rr.set(-0.8f, 0.92f, 0.8f, 1.08f); c.drawRoundRect(rr, 0.03f, 0.03f, fill)
        fill.shader = shPedBot
        rr.set(-0.95f, 1.08f, 0.95f, 1.18f); c.drawRoundRect(rr, 0.03f, 0.03f, fill)
        fill.shader = null
        st.shader = null; st.strokeCap = Paint.Cap.ROUND
        st.color = Color.argb(150, 255, 255, 255); st.strokeWidth = 0.025f
        c.drawLine(-0.78f, 0.93f, 0.78f, 0.93f, st)
        fill.color = info.primary; rr.set(-0.38f, 0.96f, 0.38f, 1.05f); c.drawRoundRect(rr, 0.02f, 0.02f, fill)
        fill.color = info.secondary; c.drawRect(-0.38f, 0.96f, 0.38f, 0.975f, fill)
        st.color = 0xFF6B2D0A.toInt(); st.strokeWidth = 0.2f; c.drawPath(trophyHandles, st)
        st.shader = shHandle
        st.strokeWidth = 0.12f; c.drawPath(trophyHandles, st)
        st.shader = null
        st.color = Color.argb(200, 255, 255, 255); st.strokeWidth = 0.025f
        c.drawPath(trophyHandles, st)
        fill.shader = shBody
        c.drawPath(trophyBody, fill)
        fill.shader = shBodyV
        c.drawPath(trophyBody, fill)
        fill.shader = null
        st.color = 0xFF5A2508.toInt(); st.strokeWidth = 0.04f; c.drawPath(trophyBody, st)
        fill.shader = shKnob
        rr.set(-0.2f, 0.27f, 0.2f, 0.4f); c.drawOval(rr, fill)
        fill.shader = shRim
        rr.set(-0.56f, -1.04f, 0.56f, -0.86f); c.drawOval(rr, fill)
        fill.shader = null
        fill.color = 0xFF4A1D06.toInt(); rr.set(-0.47f, -1.0f, 0.47f, -0.9f); c.drawOval(rr, fill)
        st.strokeCap = Paint.Cap.BUTT
        st.color = info.primary; st.strokeWidth = 0.14f; c.drawLine(-0.46f, -0.62f, 0.46f, -0.62f, st)
        st.color = info.secondary; st.strokeWidth = 0.03f
        c.drawLine(-0.47f, -0.7f, 0.47f, -0.7f, st); c.drawLine(-0.45f, -0.54f, 0.45f, -0.54f, st)
        st.strokeCap = Paint.Cap.ROUND
        fill.color = 0xFF92400E.toInt(); c.drawPath(starPath, fill)
        st.color = Color.argb(215, 255, 255, 255); st.strokeWidth = 0.08f
        path.reset(); path.moveTo(-0.42f, -0.82f); path.quadTo(-0.5f, -0.4f, -0.27f, -0.05f)
        c.drawPath(path, st)
        st.color = Color.argb(140, 255, 255, 255); st.strokeWidth = 0.035f
        path.reset(); path.moveTo(0.36f, -0.8f); path.quadTo(0.4f, -0.45f, 0.24f, -0.12f)
        c.drawPath(path, st)
        fill.shader = shHot1
        c.drawCircle(-0.3f, -0.55f, 0.22f, fill)
        fill.shader = shHot2
        c.drawCircle(0f, 0.34f, 0.2f, fill)
        fill.shader = null
    }

    /** Pre-renders trophy, pedestal and floor reflection once per winner, at the size it is drawn. */
    private fun buildTrophy(info: TeamInfo): Bitmap {
        val yMin = -1.12f
        val hPx = max(96, dp(TROPHY_DP).toInt())
        val sc = hPx / (1.8f - yMin)
        val wPx = (2.3f * sc).toInt()
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(wPx / 2f, -yMin * sc)
        c.scale(sc, sc)
        val layer = c.saveLayer(trophyBounds, null)
        c.save(); c.translate(0f, 2f * 1.18f); c.scale(1f, -1f)
        drawTrophyVec(c, info)
        c.restore()
        c.drawRect(trophyBounds, fadePaint)
        c.restoreToCount(layer)
        drawTrophyVec(c, info)
        return bmp
    }

    private fun bright(c: Int): Int {
        val lum = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f) / 255f
        if (lum >= 0.3f) return c
        val t = 0.55f
        return Color.rgb(
            (Color.red(c) + (255 - Color.red(c)) * t).toInt(),
            (Color.green(c) + (255 - Color.green(c)) * t).toInt(),
            (Color.blue(c) + (255 - Color.blue(c)) * t).toInt()
        )
    }

    private fun setConfettiColors(info: TeamInfo) {
        bucketColor[0] = bright(info.primary)
        bucketColor[1] = bright(info.secondary)
        bucketColor[2] = Color.WHITE
        bucketColor[3] = K_FBBF24
    }

    private fun addConfetti(x: Float, y: Float, vx: Float, vy: Float) {
        if (cn >= MAXC) return
        cx[cn] = x; cy[cn] = y; cvx[cn] = vx; cvy[cn] = vy
        cph[cn] = rng.nextFloat() * 6.28f; cbk[cn] = rng.nextInt(4)
        cang[cn] = rng.nextFloat() * 6.28f; cspin[cn] = (rng.nextFloat() - 0.5f) * 14f
        cn++
    }

    /** Two cannons from the lower corners. */
    private fun spawnBurst(n: Int) {
        for (i in 0 until n) {
            val left = (i and 1) == 0
            val x = if (left) dp(6f) else sw - dp(6f)
            val vx = (if (left) 1f else -1f) * dp(120f + rng.nextFloat() * 420f)
            val vy = -dp(300f + rng.nextFloat() * 520f)
            addConfetti(x, sh * 0.78f, vx, vy)
        }
    }

    private fun spawnRain(n: Int) {
        for (i in 0 until n) addConfetti(rng.nextFloat() * sw, -dp(8f), (rng.nextFloat() - 0.5f) * dp(60f), dp(40f + rng.nextFloat() * 80f))
    }

    private fun updateConfetti(d: Float) {
        val g = dp(520f)
        val maxFall = dp(230f)
        var i = 0
        while (i < cn) {
            cvy[i] = min(cvy[i] + g * d, maxFall)
            cvx[i] *= (1f - 0.9f * d)
            cph[i] += d * 6f
            cang[i] += cspin[i] * d
            cx[i] += (cvx[i] + sin(cph[i]) * dp(26f)) * d
            cy[i] += cvy[i] * d
            if (cy[i] > sh + dp(12f)) {
                cn--
                cx[i] = cx[cn]; cy[i] = cy[cn]; cvx[i] = cvx[cn]; cvy[i] = cvy[cn]; cph[i] = cph[cn]; cbk[i] = cbk[cn]; cang[i] = cang[cn]; cspin[i] = cspin[cn]
            } else i++
        }
    }

    private fun drawConfetti(canvas: Canvas) {
        if (cn == 0) return
        for (b in 0..3) bucketN[b] = 0
        for (i in 0 until cn) {
            val b = cbk[i]
            val a = bucketPts[b]
            val k = bucketN[b] * 4
            // short thick segment = rotated rect; length flips with the flutter phase
            val hl = dp(5.5f) * (0.25f + 0.75f * abs(cos(cph[i])))
            val dx = cos(cang[i]) * hl
            val dy = sin(cang[i]) * hl
            a[k] = cx[i] - dx; a[k + 1] = cy[i] - dy; a[k + 2] = cx[i] + dx; a[k + 3] = cy[i] + dy
            bucketN[b]++
        }
        for (b in 0..3) {
            if (bucketN[b] == 0) continue
            confPaint.color = bucketColor[b]
            confPaint.strokeWidth = dp(3f + (b and 1) * 1.6f)
            canvas.drawLines(bucketPts[b], 0, bucketN[b] * 4, confPaint)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun easeOutBack(t: Float): Float {
        val x = t.coerceIn(0f, 1f) - 1f
        return 1f + 2.70158f * x * x * x + 1.70158f * x * x
    }

    private fun easeOut(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x * x
    }

    private fun drawCrest(canvas: Canvas, x: Float, y: Float, size: Float, info: TeamInfo) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 2f, size / 2f)
        pFill.color = info.secondary
        canvas.drawPath(crestPath, pFill)
        pFill.color = info.primary
        canvas.save(); canvas.scale(0.62f, 0.62f); canvas.translate(0f, 0.05f)
        canvas.drawPath(crestPath, pFill)
        canvas.restore()
        pStroke.color = Color.argb(230, 255, 255, 255)
        pStroke.strokeWidth = 0.12f
        canvas.drawPath(crestPath, pStroke)
        canvas.restore()
    }

    // ---------------------------------------------------------------- scoreboard

    fun drawScoreboard(canvas: Canvas, w: World) {
        val t = dp(8f); val h = dp(34f)
        val blockW = dp(78f); val scoreW = dp(30f); val centreW = dp(98f)
        val r = dp(9f)
        val total = (blockW + scoreW) * 2f + centreW
        val l = (sw - total) / 2f
        val rt = l + total
        rect.set(l, t + dp(2f), rt, t + h + dp(2f)); canvas.drawRoundRect(rect, r, r, pShadow)
        rect.set(l, t, rt, t + h); canvas.drawRoundRect(rect, r, r, pPlate)

        // team blocks (extend under the score boxes so inner corners stay square)
        val homeInfo = w.teams[0].info; val awayInfo = w.teams[1].info
        pFill.color = homeInfo.primary
        rect.set(l, t, l + blockW + dp(10f), t + h); canvas.drawRoundRect(rect, r, r, pFill)
        pFill.color = awayInfo.primary
        rect.set(rt - blockW - dp(10f), t, rt, t + h); canvas.drawRoundRect(rect, r, r, pFill)
        // score boxes
        val hsx = l + blockW
        rect.set(hsx, t, hsx + scoreW, t + h); canvas.drawRect(rect, pScoreBox)
        val asx = rt - blockW - scoreW
        rect.set(asx, t, asx + scoreW, t + h); canvas.drawRect(rect, pScoreBox)
        // secondary-colour trim on the inner edge of each block
        pFill.color = homeInfo.secondary
        rect.set(hsx - dp(3f), t, hsx, t + h); canvas.drawRect(rect, pFill)
        pFill.color = awayInfo.secondary
        val aex = rt - blockW
        rect.set(aex, t, aex + dp(3f), t + h); canvas.drawRect(rect, pFill)
        // gloss
        rect.set(l, t, rt, t + h * 0.5f); canvas.drawRoundRect(rect, r, r, pGloss)

        // crests + abbreviations
        drawCrest(canvas, l + dp(17f), t + h / 2f, dp(21f), homeInfo)
        drawCrest(canvas, rt - dp(17f), t + h / 2f, dp(21f), awayInfo)
        pTextL.textSize = dp(15f)
        pTextL.color = homeInfo.text
        canvas.drawText(homeInfo.abbr, l + dp(32f), t + dp(22f), pTextL)
        pTextL.color = awayInfo.text
        canvas.drawText(awayInfo.abbr, aex + dp(8f), t + dp(22f), pTextL)

        // scores with a pop on change
        for (i in 0..1) {
            val cxs = if (i == 0) hsx + scoreW / 2f else asx + scoreW / 2f
            val pop = scorePop[i]
            pTextC.textSize = dp(21f)
            pTextC.color = Color.WHITE
            if (pop > 0f) {
                val s = 1f + 0.55f * (pop / 0.7f)
                canvas.save(); canvas.scale(s, s, cxs, t + h / 2f)
                pTextC.color = K_FDE047
                canvas.drawText(num(w.teams[i].score), cxs, t + dp(24.5f), pTextC)
                canvas.restore()
            } else canvas.drawText(num(w.teams[i].score), cxs, t + dp(24.5f), pTextC)
        }

        // centre: period + clock (or shootout timer)
        val ccx = hsx + scoreW + centreW / 2f
        pTextC.color = K_9FB3CC
        pTextC.textSize = dp(10.5f)
        if (w.isShootout) {
            canvas.drawText(if (w.shootoutRound <= 5) "SHOOTOUT" else "SUDDEN", ccx - dp(26f), t + dp(21f), pTextC)
            val tenths = (w.shootoutTimer.coerceAtLeast(0f) * 10f).toInt()
            if (tenths != soTimeKey) {
                soTimeKey = tenths
                sb.setLength(0); sb.append(tenths / 10).append('.').append(tenths % 10).append('s')
                soTimeStr = sb.toString()
            }
            pTextC.color = Color.WHITE; pTextC.textSize = dp(15f)
            canvas.drawText(soTimeStr, ccx + dp(26f), t + dp(22f), pTextC)
        } else {
            val pk = if (w.overtime) 0 else w.period
            if (pk != periodKey) { periodKey = pk; periodStr = if (w.overtime) "OT" else w.periodText() }
            canvas.drawText(periodStr, ccx - dp(28f), t + dp(21f), pTextC)
            val total0 = if (w.overtime) w.clock.toInt() else kotlin.math.ceil(w.clock.toDouble()).toInt()
            if (total0 != clockKey) {
                clockKey = total0
                sb.setLength(0)
                sb.append(total0 / 60).append(':')
                if (total0 % 60 < 10) sb.append('0')
                sb.append(total0 % 60)
                clockStr = sb.toString()
            }
            val low = !w.overtime && w.clock < 10f && w.phase == Phase.PLAY
            pTextC.color = if (low && (anim * 2f).toInt() % 2 == 0) K_F87171 else Color.WHITE
            pTextC.textSize = dp(16f)
            canvas.drawText(clockStr, ccx + dp(20f), t + dp(22.5f), pTextC)
        }

        var y = t + h + dp(6f)
        if (w.isShootout) {
            drawShootoutDots(canvas, w, l + (blockW + scoreW) / 2f, y, 0)
            drawShootoutDots(canvas, w, rt - (blockW + scoreW) / 2f, y, 1)
            val key = w.shootoutTurn * 100 + w.shootoutRound
            if (key != soKey) {
                soKey = key
                sb.setLength(0)
                sb.append(w.teams[w.shootoutTurn].info.abbr).append(" SHOOTING - RD ").append(w.shootoutRound)
                soStr = sb.toString()
            }
            drawPill(canvas, ccx, y, dp(176f), dp(20f), pPlate.color, soStr, Color.WHITE, 13f)
        } else {
            val key = w.teams[0].shots * 1000 + w.teams[1].shots
            if (key != shotsKey) {
                shotsKey = key
                sb.setLength(0)
                sb.append("SHOTS  ").append(w.teams[0].shots).append("  -  ").append(w.teams[1].shots)
                shotsStr = sb.toString()
            }
            drawPill(canvas, ccx, y, dp(124f), dp(20f), pPlate.color, shotsStr, K_CBD5E1, 13f)
        }
        y += dp(24f)

        // on-fire pills under the respective blocks
        for (i in 0..1) {
            if (!w.isOnFire(i)) continue
            val secs = w.fireTimer[i].toInt()
            if (secs != fireKey[i]) {
                fireKey[i] = secs
                sb.setLength(0); sb.append("ON FIRE ").append(secs).append('s')
                fireStr[i] = sb.toString()
            }
            val px = if (i == 0) l + dp(42f) else rt - dp(42f)
            drawPill(canvas, px, t + h + dp(6f) + (if (w.isShootout) dp(24f) else 0f), dp(100f), dp(20f), K_EA580C, fireStr[i], Color.WHITE, 13f)
        }

        // power play / empty net
        if (w.penaltyTeam != -1) {
            val adv = w.opponent(w.penaltyTeam)
            val pt = w.penaltyTimer.toInt().coerceAtLeast(0)
            val key = adv.id * 10000 + pt
            if (key != ppKey) {
                ppKey = key
                sb.setLength(0)
                sb.append(adv.info.abbr).append(" POWER PLAY ").append(pt / 60).append(':')
                if (pt % 60 < 10) sb.append('0')
                sb.append(pt % 60)
                ppStr = sb.toString()
            }
            drawPill(canvas, ccx, y, dp(154f), dp(20f), adv.info.primary, ppStr, adv.info.text, 13f, true)
        } else if (w.goaliePulled[0] || w.goaliePulled[1]) {
            val id = if (w.goaliePulled[0]) 0 else 1
            drawPill(canvas, ccx, y, dp(132f), dp(20f), K_B91C1C, emptyNetStr[id], Color.WHITE, 13f, true)
        }
    }

    private fun drawShootoutDots(canvas: Canvas, w: World, cxm: Float, y: Float, side: Int = 0) {
        val sp = dp(10f)
        val x0 = cxm - 2 * sp
        rect.set(x0 - dp(8f), y, x0 + 4 * sp + dp(8f), y + dp(20f))
        canvas.drawRoundRect(rect, dp(7f), dp(7f), pPlate)
        for (r in 0 until 5) {
            val res = w.shootoutAttempts[side][r]
            val px = x0 + r * sp
            val py = y + dp(10f)
            if (res == 0) {
                pStroke.color = K_64748B; pStroke.strokeWidth = dp(1.2f)
                canvas.drawCircle(px, py, dp(3.2f), pStroke)
            } else {
                pFill.color = if (res == 1) K_22C55E else K_EF4444
                canvas.drawCircle(px, py, dp(3.4f), pFill)
            }
        }
    }

    private fun drawPill(canvas: Canvas, cxp: Float, y: Float, wd: Float, ht: Float, fill: Int, text: String, tc: Int, size: Float, border: Boolean = false) {
        rect.set(cxp - wd / 2f, y, cxp + wd / 2f, y + ht)
        pFill.color = fill
        canvas.drawRoundRect(rect, ht / 2f, ht / 2f, pFill)
        if (border) {
            pStroke.color = K_FBBF24; pStroke.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(rect, ht / 2f, ht / 2f, pStroke)
        }
        pTextC.color = tc
        pTextC.textSize = dp(size)
        canvas.drawText(text, cxp, y + ht * 0.5f + dp(size) * 0.36f, pTextC)
    }

    // ---------------------------------------------------------------- banner plate

    /** Accent bar colour: scoring / penalised team colours for goal and penalty banners, gold otherwise. */
    private fun bannerAccent(w: World, text: String, a: Int): Int {
        val team = when {
            text.startsWith("GOAL") -> celebTeam
            text.startsWith("PENALTY") -> w.penaltyTeam
            else -> -1
        }
        if (team !in 0..1) return Color.argb(a, 251, 191, 36)
        val c = w.teams[team].info.primary
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    }

    fun drawBanner(canvas: Canvas, w: World) {
        val text = w.banner ?: return
        if (w.bannerTimer <= 0f || w.phase == Phase.GAME_OVER) return
        if (celebT >= 0f && !celebLight && text == "GOAL!") return
        val out = (w.bannerTimer / 0.35f).coerceIn(0f, 1f)
        val inT = easeOut(bannerAge / 0.28f)
        val sub = w.bannerSub
        pBig.textSize = dp(if (text.length > 14) 24f else 32f)
        val tw = pBig.measureText(text)
        var sw2 = 0f
        if (sub != null) { pTextC.textSize = dp(13f); sw2 = pTextC.measureText(sub) }
        val pw = max(tw, sw2) + dp(56f)
        val ph = if (sub != null) dp(64f) else dp(46f)
        val cxp = sw / 2f
        val cyp = sh * 0.30f
        val off = (1f - inT) * (-(sw / 2f + pw / 2f))
        val a = (255 * out).toInt()
        canvas.save()
        canvas.translate(off, 0f)
        // skewed plate
        val sk = dp(14f)
        path.reset()
        path.moveTo(cxp - pw / 2f + sk, cyp - ph / 2f); path.lineTo(cxp + pw / 2f, cyp - ph / 2f)
        path.lineTo(cxp + pw / 2f - sk, cyp + ph / 2f); path.lineTo(cxp - pw / 2f, cyp + ph / 2f); path.close()
        pFill.color = Color.argb((225 * out).toInt(), 8, 14, 28)
        canvas.drawPath(path, pFill)
        pFill.color = bannerAccent(w, text, a)
        rect.set(cxp - pw / 2f, cyp + ph / 2f - dp(4f), cxp + pw / 2f - sk, cyp + ph / 2f)
        canvas.drawRect(rect, pFill)
        pBig.alpha = a
        canvas.drawText(text, cxp, cyp + (if (sub != null) -dp(4f) else dp(11f)), pBig)
        if (sub != null) {
            pTextC.color = Color.argb(a, 253, 230, 138)
            canvas.drawText(sub, cxp, cyp + dp(20f), pTextC)
        }
        canvas.restore()
        pBig.alpha = 255
    }

    // ---------------------------------------------------------------- controls

    fun setPressed(c: TouchControls) {
        pressedNow[0] = c.shootDown; pressedNow[1] = c.passDown; pressedNow[2] = c.hitDown
    }

    fun drawControls(canvas: Canvas, w: World, localTeam: Int, c: TouchControls) {
        setPressed(c)
        // joystick: translucent ring, as in the reference
        val jr = c.joyRadius
        val ax = if (c.joyActive) c.joyAnchorX else c.joyRestX
        val ay = if (c.joyActive) c.joyAnchorY else c.joyRestY
        pFill.color = Color.argb(if (c.joyActive) 55 else 28, 120, 160, 210)
        canvas.drawCircle(ax, ay, jr, pFill)
        pStroke.color = Color.argb(if (c.joyActive) 200 else 130, 255, 255, 255)
        pStroke.strokeWidth = dp(2f)
        canvas.drawCircle(ax, ay, jr, pStroke)
        val flash = c.dekeFlash()
        if (flash > 0f) {
            pStroke.color = Color.argb((230 * flash).toInt(), 255, 255, 255)
            pStroke.strokeWidth = dp(4f)
            canvas.drawCircle(ax, ay, jr * (1f + 0.12f * (1f - flash)), pStroke)
            pStroke.strokeWidth = dp(2f)
        }
        // Knob: always visible (dim at rest, bright while dragging).
        val kx = if (c.joyActive) c.joyKnobX else c.joyRestX
        val ky = if (c.joyActive) c.joyKnobY else c.joyRestY
        pFill.color = Color.argb(if (c.joyActive) 150 else 70, 255, 255, 255)
        canvas.drawCircle(kx, ky, jr * 0.4f, pFill)
        pStroke.color = Color.argb(if (c.joyActive) 230 else 140, 255, 255, 255)
        pStroke.strokeWidth = dp(2f)
        canvas.drawCircle(kx, ky, jr * 0.4f, pStroke)

        val controlled = if (localTeam >= 0) w.controlledSkater(localTeam) else null
        c.carrying = controlled != null && w.puck.carrier === controlled
        if (controlled?.actsAsGoalie(w) == true) {
            textButton(canvas, c.shootX, c.shootY, c.shootR, "BUTTERFLY", "5-hole", press[0], K_DC2626)
            textButton(canvas, c.passX, c.passY, c.passR, "POKE", "stick", press[1], K_2563EB)
            textButton(canvas, c.hitX, c.hitY, c.hitR, "PAD STACK", "sprawl", press[2], K_D97706)
            return
        }
        val hasPuck = localTeam >= 0 && w.puck.carrier != null && w.puck.carrier === controlled
        buttonBase(canvas, c.shootX, c.shootY, c.shootR, press[0], K_EF4444)
        iconCrosshair(canvas, c.shootX, c.shootY - c.shootR * 0.2f, c.shootR * 0.5f)
        buttonLabel(canvas, c.shootX, c.shootY, c.shootR, if (hasPuck) "SHOOT" else "POKE")
        buttonBase(canvas, c.passX, c.passY, c.passR, press[1], K_3B82F6)
        iconPass(canvas, c.passX, c.passY - c.passR * 0.2f, c.passR * 0.66f)
        buttonLabel(canvas, c.passX, c.passY, c.passR, if (hasPuck) "PASS" else "SWITCH")
        buttonBase(canvas, c.hitX, c.hitY, c.hitR, press[2], K_F59E0B)
        iconBurst(canvas, c.hitX, c.hitY - c.hitR * 0.2f, c.hitR * 0.5f)
        buttonLabel(canvas, c.hitX, c.hitY, c.hitR, "HIT")

        if (c.shootDown && hasPuck) {
            val ch = c.currentCharge()
            pStroke.strokeWidth = dp(5f)
            pStroke.color = when {
                ch >= 1f -> if ((anim * 10f).toInt() % 2 == 0) K_FEF2F2 else K_EF4444
                ch > 0.6f -> K_F97316
                else -> K_FDE047
            }
            rect.set(c.shootX - c.shootR - dp(7f), c.shootY - c.shootR - dp(7f), c.shootX + c.shootR + dp(7f), c.shootY + c.shootR + dp(7f))
            canvas.drawArc(rect, -90f, 360f * ch, false, pStroke)
        }
    }

    private fun buttonBase(canvas: Canvas, x: Float, y: Float, r0: Float, press: Float, tint: Int) {
        val r = r0 * (1f - 0.08f * press)
        pFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(x, y + dp(3f), r, pFill)
        pFill.color = Color.argb((110 + 90 * press).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawCircle(x, y, r, pFill)
        pFill.color = Color.argb((45 + 30 * press).toInt(), 255, 255, 255)
        canvas.drawCircle(x, y, r, pFill)
        // subtle inner highlight on the upper half
        pFill.color = Color.argb((38 * (1f - press)).toInt(), 255, 255, 255)
        canvas.drawCircle(x, y - r * 0.28f, r * 0.62f, pFill)
        pStroke.color = Color.argb((200 + 55 * press).toInt(), 255, 255, 255)
        pStroke.strokeWidth = dp(2.5f)
        canvas.drawCircle(x, y, r - dp(1.2f), pStroke)
    }

    private fun buttonLabel(canvas: Canvas, x: Float, y: Float, r: Float, label: String) {
        pTextC.color = Color.WHITE
        pTextC.textSize = dp(12.5f)
        canvas.drawText(label, x, y + r * 0.68f, pTextC)
    }

    private fun textButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, sub: String, press: Float, tint: Int) {
        buttonBase(canvas, x, y, r, press, tint)
        pTextC.color = Color.WHITE
        pTextC.textSize = r * 0.28f
        canvas.drawText(label, x, y + r * 0.05f, pTextC)
        pTextC.textSize = r * 0.22f
        pTextC.color = Color.argb(210, 255, 255, 255)
        canvas.drawText(sub, x, y + r * 0.42f, pTextC)
    }

    private fun iconStroke(r: Float) {
        pStroke.color = Color.WHITE
        pStroke.strokeWidth = max(dp(2f), r * 0.14f)
    }

    private fun iconCrosshair(canvas: Canvas, x: Float, y: Float, r: Float) {
        iconStroke(r)
        canvas.drawCircle(x, y, r * 0.62f, pStroke)
        val a = tmpLines
        a[0] = x - r * 1.05f; a[1] = y; a[2] = x - r * 0.3f; a[3] = y
        a[4] = x + r * 0.3f; a[5] = y; a[6] = x + r * 1.05f; a[7] = y
        a[8] = x; a[9] = y - r * 1.05f; a[10] = x; a[11] = y - r * 0.3f
        a[12] = x; a[13] = y + r * 0.3f; a[14] = x; a[15] = y + r * 1.05f
        canvas.drawLines(a, 0, 16, pStroke)
    }

    private fun iconPass(canvas: Canvas, x: Float, y: Float, r: Float) {
        iconStroke(r)
        canvas.drawCircle(x - r * 0.8f, y, r * 0.24f, pStroke)
        canvas.drawCircle(x + r * 0.8f, y, r * 0.24f, pStroke)
        val a = tmpLines
        a[0] = x - r * 0.45f; a[1] = y; a[2] = x - r * 0.2f; a[3] = y
        a[4] = x - r * 0.02f; a[5] = y; a[6] = x + r * 0.24f; a[7] = y
        canvas.drawLines(a, 0, 8, pStroke)
    }

    private fun iconBurst(canvas: Canvas, x: Float, y: Float, r: Float) {
        iconStroke(r)
        val a = tmpLines
        for (i in 0 until 8) {
            val ang = i * (PI / 4).toFloat()
            a[i * 4] = x + cos(ang) * r * 0.45f; a[i * 4 + 1] = y + sin(ang) * r * 0.45f
            a[i * 4 + 2] = x + cos(ang) * r * 1.05f; a[i * 4 + 3] = y + sin(ang) * r * 1.05f
        }
        canvas.drawLines(a, 0, 32, pStroke)
        canvas.drawCircle(x, y, r * 0.22f, pStroke)
    }

    private fun iconZigzag(canvas: Canvas, x: Float, y: Float, r: Float) {
        iconStroke(r)
        path.reset()
        path.moveTo(x - r * 0.95f, y + r * 0.55f)
        path.lineTo(x - r * 0.3f, y - r * 0.05f)
        path.lineTo(x + r * 0.1f, y + r * 0.35f)
        path.lineTo(x + r * 0.85f, y - r * 0.5f)
        canvas.drawPath(path, pStroke)
        path.reset()
        path.moveTo(x + r * 0.3f, y - r * 0.55f); path.lineTo(x + r * 0.9f, y - r * 0.55f); path.lineTo(x + r * 0.9f, y + r * 0.05f)
        canvas.drawPath(path, pStroke)
    }

    // ---------------------------------------------------------------- overlays

    // Ground-pass projection through the perspective camera: world feet -> screen pixels.
    // [h] lifts a point above the ice by that many feet (screen-up, scaled by the depth).
    private var pjx = 0f
    private var pjy = 0f
    private var gcam: Camera? = null
    private fun project(wx: Float, wy: Float, h: Float = 0f) {
        val c = gcam ?: return
        pjx = c.px(wx, wy)
        pjy = c.py(wy) - h * c.ppf(wy)
    }

    private fun pathMove(wx: Float, wy: Float, h: Float = 0f) { project(wx, wy, h); path.moveTo(pjx, pjy) }
    private fun pathLine(wx: Float, wy: Float, h: Float = 0f) { project(wx, wy, h); path.lineTo(pjx, pjy) }
    private fun pathQuad(cx: Float, cy: Float, wx: Float, wy: Float) {
        project(cx, cy); val qx = pjx; val qy = pjy
        project(wx, wy); path.quadTo(qx, qy, pjx, pjy)
    }

    /** Projects a ground circle at (wx, wy) of radius rw feet into [ellipse] (ry = rx * vk), lifted by [h] feet. */
    private fun setGroundEllipse(wx: Float, wy: Float, rw: Float, h: Float = 0f) {
        val c = gcam ?: return
        project(wx, wy, h)
        val rx = rw * c.ppf(wy)
        ellipse.set(pjx - rx, pjy - rx * c.vk, pjx + rx, pjy + rx * c.vk)
    }

    private val LAMP_H = 6f

    private fun celebK(): Float = min((celebT / 0.3f).coerceIn(0f, 1f), ((celebDur - celebT) / 0.5f).coerceIn(0f, 1f))

    /**
     * GROUND pass: called from the world pass before skaters and the puck are drawn, so these effects
     * sit on the ice under the players. Drawn in screen pixels (stroke widths in dp), all geometry going
     * through [project] so it follows the perspective camera and its shake.
     */
    fun drawCelebrationGround(canvas: Canvas, w: World, cam: Camera) {
        if (celebT < 0f) return
        val t = celebT
        val k = celebK()
        val dir = w.teams[celebTeam].attackDir
        val glX = dir * Rink.GOAL_LINE_X
        gcam = cam

        // team-colour glow pooled on the ice in front of the net
        if (glowShader[celebTeam] != null) {
            setGroundEllipse(glX - dir * 2f, 0f, 14f)
            pGlow.shader = glowShader[celebTeam]
            pGlow.alpha = (200 * k * (0.75f + 0.25f * sin(t * 6f))).toInt()
            canvas.save(); canvas.translate(ellipse.centerX(), ellipse.centerY()); canvas.scale(ellipse.width() / 2f, ellipse.height() / 2f)
            canvas.drawCircle(0f, 0f, 1f, pGlow)
            canvas.restore()
        }

        // net: white flash plus a mesh that bulges back in the direction the puck travelled
        val x0 = glX
        val depth = Rink.NET_DEPTH
        val hw = Rink.NET_HALF_W
        val fl = (1f - t / 1.7f).coerceIn(0f, 1f)
        if (fl > 0f) {
            val decay = exp(-t * 2.2f)
            val bulge = 2.2f * decay * (0.65f + 0.35f * cos(t * 17f))
            path.reset()
            pathMove(x0, -hw); pathLine(x0 + dir * depth, -hw); pathLine(x0 + dir * depth, hw); pathLine(x0, hw); path.close()
            pFill.color = Color.argb((85 * fl * fl).toInt(), 255, 255, 255)
            canvas.drawPath(path, pFill)
            path.reset()
            // rows from the goal line to the displaced back wall
            for (i in 0 until 5) {
                val y = -hw + hw * 2f * (i + 1) / 6f
                val bf = 1f - (y / hw) * (y / hw)
                pathMove(x0, y)
                pathQuad(x0 + dir * (depth * 0.5f + bulge * bf * 0.5f), y, x0 + dir * (depth + bulge * bf), y)
            }
            // columns bowed out in the middle
            for (i in 1..4) {
                val d = i / 4f
                val xd = x0 + dir * (depth * d)
                pathMove(xd, -hw)
                pathQuad(xd + dir * bulge * d * 2f, 0f, xd, hw)
            }
            pStroke.color = Color.argb((235 * fl).toInt(), 255, 255, 255)
            pStroke.strokeWidth = dp(1.3f)
            canvas.drawPath(path, pStroke)
            if (t < 0.7f) {
                // the puck, briefly visible in the netting
                val pa = (1f - t / 0.7f)
                project(x0 + dir * (depth * 0.8f + bulge * 0.6f), 0f)
                val pr = 0.55f * cam.ppf(0f)
                pFill.color = Color.argb((255 * pa).toInt(), 12, 12, 14)
                canvas.drawCircle(pjx, pjy, pr, pFill)
                pStroke.color = Color.argb((220 * pa).toInt(), 255, 255, 255)
                pStroke.strokeWidth = dp(1f)
                canvas.drawCircle(pjx, pjy, pr, pStroke)
            }
        }

        // goal lamp on the end boards behind the cage, with a light cone falling onto the crease
        if (t < 2.6f) {
            val lampX = dir * (Rink.GOAL_LINE_X + 6f)
            val fo = ((2.6f - t) / 0.5f).coerceIn(0f, 1f)
            val pulse = 0.5f + 0.5f * sin(t * 13f)
            val inten = fo * min(1f, t / 0.12f) * (0.55f + 0.45f * pulse)
            path.reset()
            pathMove(lampX, -0.7f, LAMP_H); pathLine(lampX, 0.7f, LAMP_H); pathLine(glX - dir * 5f, 6f); pathLine(glX - dir * 5f, -6f); path.close()
            pFill.color = Color.argb((40 * inten).toInt(), 255, 60, 50)
            canvas.drawPath(path, pFill)
            setGroundEllipse(lampX, 0f, 6f, LAMP_H)
            pGlow.shader = redGlow
            pGlow.alpha = (190 * inten).toInt()
            canvas.save(); canvas.translate(ellipse.centerX(), ellipse.centerY()); canvas.scale(ellipse.width() / 2f, ellipse.height() / 2f)
            canvas.drawCircle(0f, 0f, 1f, pGlow)
            canvas.restore()
            setGroundEllipse(lampX, 0f, 1.15f, LAMP_H)
            pFill.color = K_1B2230
            canvas.drawOval(ellipse, pFill)
            setGroundEllipse(lampX, 0f, 0.8f, LAMP_H)
            pFill.color = Color.argb((110 + 145 * inten).toInt(), 255, 50, 40)
            canvas.drawOval(ellipse, pFill)
            setGroundEllipse(lampX - dir * 0.2f, -0.25f, 0.28f, LAMP_H)
            pFill.color = Color.argb((255 * inten).toInt(), 255, 240, 235)
            canvas.drawOval(ellipse, pFill)
        }

        // shockwave ring expanding along the ice from the goal line
        if (t < 0.9f) {
            setGroundEllipse(glX, 0f, 2f + t * 16f)
            pStroke.color = Color.argb((190 * (1f - t / 0.9f)).toInt(), 255, 255, 255)
            pStroke.strokeWidth = dp(3f)
            canvas.drawOval(ellipse, pStroke)
        }

        // scorer: faint light cone and a ring around the skater's feet
        val sc = celebScorer
        if (sc != null) {
            path.reset()
            pathMove(sc.x - 0.6f, sc.y, 30f); pathLine(sc.x + 0.6f, sc.y, 30f); pathLine(sc.x + 2.4f, sc.y); pathLine(sc.x - 2.4f, sc.y); path.close()
            pFill.color = Color.argb((30 * k).toInt(), 255, 244, 200)
            canvas.drawPath(path, pFill)
            setGroundEllipse(sc.x, sc.y, 2.6f * (1f + 0.08f * sin(t * 8f)))
            pFill.color = Color.argb((55 * k).toInt(), 255, 224, 71)
            canvas.drawOval(ellipse, pFill)
            pStroke.color = Color.argb((255 * k).toInt(), 253, 224, 71)
            pStroke.strokeWidth = dp(3f)
            canvas.drawOval(ellipse, pStroke)
        }
    }

    /** SKY pass: after the world, in screen space; edge vignette, beams, flash, confetti. */
    fun drawCelebrationBack(canvas: Canvas, w: World, cam: Camera) {
        if (celebT >= 0f) {
            val t = celebT
            val k = celebK()
            if (celebLocal && !celebLight) {
                pVignette.alpha = (255 * k).toInt()
                canvas.drawRect(0f, 0f, sw, sh, pVignette)
                val sway = sin(t * 2.2f) * sw * 0.10f
                val topW = dp(16f); val botW = dp(80f)
                val tx0 = sw * 0.22f; val tx1 = sw * 0.78f
                val bx0 = sw * 0.5f + sway; val bx1 = sw * 0.5f - sway
                val by = sh * 0.78f
                path.reset()
                path.moveTo(tx0 - topW, -dp(6f)); path.lineTo(tx0 + topW, -dp(6f)); path.lineTo(bx0 + botW, by); path.lineTo(bx0 - botW, by); path.close()
                path.moveTo(tx1 - topW, -dp(6f)); path.lineTo(tx1 + topW, -dp(6f)); path.lineTo(bx1 + botW, by); path.lineTo(bx1 - botW, by); path.close()
                pBeam.shader = beamShader[celebTeam]
                pBeam.alpha = (255 * k).toInt()
                canvas.drawPath(path, pBeam)
            }
            if (t < 0.35f) {
                val f = 1f - t / 0.35f
                pFill.color = Color.argb((60 * f * f).toInt(), 255, 255, 255)
                canvas.drawRect(0f, 0f, sw, sh, pFill)
            }
        }
        drawConfetti(canvas)
    }

    /** Goal band + pregame splash + final trophy, over everything except the pause button. */
    fun drawOverlays(canvas: Canvas, w: World) {
        drawGoalBand(canvas, w)
        drawFaceoffBand(canvas, w)
        drawIntro(canvas, w)
        drawFinal(canvas, w)
        drawShootoutHint(canvas, w)
    }

    /** Broadcast-style lower third: keeps the centre of the rink clear. */
    private fun drawGoalBand(canvas: Canvas, w: World) {
        if (celebT < 0f || celebLight) return
        val t = celebT
        val info = w.teams[celebTeam].info
        val inP = easeOutBack((t - 0.35f) / 0.4f)
        val outP = ((t - (CEL_DUR - 0.5f)) / 0.5f).coerceIn(0f, 1f)
        val x0 = sw * 0.20f
        val x1 = sw * 0.70f
        val bh = dp(70f)
        val top = sh * 0.74f
        val blockW = dp(96f)
        val sk = dp(14f)
        val off = (inP - 1f) * (x1 + dp(40f)) - outP * (x1 + dp(40f))
        canvas.save()
        canvas.translate(off, 0f)
        path.reset()
        path.moveTo(x0 + sk, top + dp(3f)); path.lineTo(x1, top + dp(3f)); path.lineTo(x1 - sk, top + bh + dp(3f)); path.lineTo(x0, top + bh + dp(3f)); path.close()
        pFill.color = Color.argb(90, 0, 0, 0)
        canvas.drawPath(path, pFill)
        path.reset()
        path.moveTo(x0 + sk, top); path.lineTo(x1, top); path.lineTo(x1 - sk, top + bh); path.lineTo(x0, top + bh); path.close()
        pFill.color = Color.argb(235, 8, 14, 28)
        canvas.drawPath(path, pFill)
        path.reset()
        path.moveTo(x0 + sk, top); path.lineTo(x0 + blockW + sk, top); path.lineTo(x0 + blockW, top + bh); path.lineTo(x0, top + bh); path.close()
        pFill.color = info.primary
        canvas.drawPath(path, pFill)
        pStroke.color = info.secondary; pStroke.strokeWidth = dp(4f)
        canvas.drawLine(x0 + blockW + sk, top, x0 + blockW, top + bh, pStroke)
        drawCrest(canvas, x0 + blockW * 0.5f + sk * 0.5f, top + dp(26f), dp(38f), info)
        pTextC.color = info.text; pTextC.textSize = dp(13f)
        canvas.drawText(info.abbr, x0 + blockW * 0.5f + sk * 0.5f, top + dp(60f), pTextC)
        pFill.color = info.secondary
        rect.set(x0 + blockW + sk, top, x1, top + dp(3f))
        canvas.drawRect(rect, pFill)

        val tx = x0 + blockW + dp(24f)
        val label = if (celebLocal) "GOAL!" else "GOAL"
        pBig.textSize = dp(34f)
        val tw = pBig.measureText(label)
        val pop = max(0.01f, easeOutBack((t - 0.5f) / 0.4f))
        canvas.save()
        canvas.scale(pop, pop, tx, top + dp(36f))
        pBig.color = Color.WHITE
        canvas.drawText(label, tx + tw / 2f, top + dp(37f), pBig)
        canvas.restore()
        pTextC.textSize = dp(17f); pTextC.color = Color.WHITE
        val sw2 = pTextC.measureText(celebScoreStr)
        canvas.drawText(celebScoreStr, x1 - dp(26f) - sw2 / 2f, top + dp(32f), pTextC)
        pTextL.textSize = dp(13f)
        pTextL.color = K_FDE68A
        canvas.drawText(celebSub, tx, top + bh - dp(11f), pTextL)
        canvas.restore()
    }

    private var foPhase: Phase? = null
    private var foStart = 0f
    private var foLabel = ""
    private var foScoreKey = -1
    private var foScoreStr = ""

    /** Lower third at faceoffs and between periods: both teams with crests and the score. */
    private fun drawFaceoffBand(canvas: Canvas, w: World) {
        if (w.phase != foPhase) {
            foPhase = w.phase
            foStart = anim
            foScoreKey = -1
            foLabel = if (w.phase == Phase.PERIOD_END) "END OF " + w.periodText() else if (w.overtime) "OVERTIME FACEOFF" else w.periodText() + " PERIOD"
        }
        if (w.phase != Phase.FACEOFF && w.phase != Phase.PERIOD_END) return
        if (w.isShootout || introT >= 0f || celebT >= 0f) return
        val key = w.teams[0].score * 100 + w.teams[1].score
        if (key != foScoreKey) {
            foScoreKey = key
            sb.setLength(0); sb.append(w.teams[0].score).append("  -  ").append(w.teams[1].score)
            foScoreStr = sb.toString()
        }
        val inP = easeOut(((anim - foStart) / 0.3f).coerceIn(0f, 1f))
        val outP = if (w.phaseTimer < 0.3f) (w.phaseTimer / 0.3f).coerceIn(0f, 1f) else 1f
        val k = inP * outP
        if (k <= 0.01f) return
        val x0 = sw * 0.20f
        val x1 = sw * 0.80f
        val bh = dp(56f)
        val top = sh * 0.78f + (1f - k) * dp(30f)
        val blockW = dp(120f)
        val sk = dp(12f)
        val a = (255 * k).toInt()
        canvas.saveLayerAlpha(x0 - dp(10f), top - dp(6f), x1 + dp(10f), top + bh + dp(10f), a)
        path.reset()
        path.moveTo(x0 + sk, top); path.lineTo(x1, top); path.lineTo(x1 - sk, top + bh); path.lineTo(x0, top + bh); path.close()
        pFill.color = Color.argb(235, 8, 14, 28)
        canvas.drawPath(path, pFill)
        for (i in 0..1) {
            val info = w.teams[i].info
            pFill.color = info.primary
            path.reset()
            if (i == 0) {
                path.moveTo(x0 + sk, top); path.lineTo(x0 + blockW + sk, top); path.lineTo(x0 + blockW, top + bh); path.lineTo(x0, top + bh)
            } else {
                path.moveTo(x1 - blockW, top); path.lineTo(x1, top); path.lineTo(x1 - sk, top + bh); path.lineTo(x1 - blockW - sk, top + bh)
            }
            path.close()
            canvas.drawPath(path, pFill)
            val bx = if (i == 0) x0 + blockW * 0.5f + sk * 0.5f else x1 - blockW * 0.5f - sk * 0.5f
            pFill.color = info.secondary
            rect.set(if (i == 0) x0 + blockW else x1 - blockW - sk, top + bh - dp(4f), if (i == 0) x0 + blockW + sk else x1 - blockW, top + bh)
            drawCrest(canvas, bx - dp(26f), top + bh / 2f, dp(32f), info)
            pTextL.textSize = dp(16f); pTextL.color = info.text
            canvas.drawText(info.abbr, bx - dp(6f), top + bh / 2f + dp(6f), pTextL)
        }
        val cx = (x0 + x1) / 2f
        pTextC.color = K_FDE68A; pTextC.textSize = dp(12f)
        canvas.drawText(foLabel, cx, top + dp(22f), pTextC)
        pTextC.color = Color.WHITE; pTextC.textSize = dp(20f)
        canvas.drawText(foScoreStr, cx, top + dp(45f), pTextC)
        canvas.restore()
    }

    private fun drawIntro(canvas: Canvas, w: World) {
        if (introT < 0f) return
        val t = introT
        val inP = easeOut(t / 0.55f)
        val outP = ((t - (INTRO_DUR - 0.5f)) / 0.5f).coerceIn(0f, 1f)
        val k = inP * (1f - outP)
        pFill.color = Color.argb((150 * k).toInt(), 4, 8, 18)
        canvas.drawRect(0f, 0f, sw, sh, pFill)
        val cyb = sh * 0.44f
        val ph = dp(150f)
        val mid = sw / 2f
        val sk = dp(34f)
        for (i in 0..1) {
            val info = w.teams[i].info
            val dir = if (i == 0) -1f else 1f
            val off = (1f - inP) * dir * sw * 0.6f + outP * dir * sw * 0.6f
            canvas.save()
            canvas.translate(off, 0f)
            // panel: parallelogram from the screen edge to the centre
            path.reset()
            if (i == 0) {
                path.moveTo(-sk * 2f, cyb - ph / 2f); path.lineTo(mid + sk, cyb - ph / 2f); path.lineTo(mid - sk, cyb + ph / 2f); path.lineTo(-sk * 2f, cyb + ph / 2f)
            } else {
                path.moveTo(mid + sk, cyb - ph / 2f); path.lineTo(sw + sk * 2f, cyb - ph / 2f); path.lineTo(sw + sk * 2f, cyb + ph / 2f); path.lineTo(mid - sk, cyb + ph / 2f)
            }
            path.close()
            pFill.color = info.primary
            canvas.drawPath(path, pFill)
            // secondary trim along the centre diagonal
            pStroke.color = info.secondary; pStroke.strokeWidth = dp(7f)
            canvas.drawLine(mid + sk - (if (i == 0) dp(2f) else -dp(2f)), cyb - ph / 2f, mid - sk - (if (i == 0) dp(2f) else -dp(2f)), cyb + ph / 2f, pStroke)
            val ccx = if (i == 0) mid - dp(250f) else mid + dp(250f)
            drawCrest(canvas, ccx, cyb - dp(18f), dp(66f), info)
            pTextC.color = info.text
            pTextC.textSize = dp(13f)
            canvas.drawText(cityUp[i], ccx, cyb + dp(42f), pTextC)
            pBig.textSize = dp(30f)
            pBig.color = info.text
            canvas.drawText(nameUp[i], ccx, cyb + dp(70f), pBig)
            pBig.color = Color.WHITE
            canvas.restore()
        }
        // VS disc
        val vs = easeOutBack((t - 0.25f) / 0.4f) * (1f - outP)
        if (vs > 0f) {
            pFill.color = K_0B1426
            canvas.drawCircle(mid, cyb, dp(34f) * vs, pFill)
            pStroke.color = K_FBBF24; pStroke.strokeWidth = dp(3f)
            canvas.drawCircle(mid, cyb, dp(34f) * vs, pStroke)
            pBig.textSize = dp(30f) * vs
            canvas.drawText("VS", mid, cyb + dp(10f) * vs, pBig)
        }
        val cap = easeOut((t - 0.4f) / 0.3f) * (1f - outP)
        if (cap > 0f) {
            pTextC.color = Color.argb((255 * cap).toInt(), 253, 230, 138)
            pTextC.textSize = dp(14f)
            canvas.drawText(introCap, mid, cyb + ph / 2f + dp(28f), pTextC)
        }
    }

    private fun drawFinal(canvas: Canvas, w: World) {
        if (finalT < 0f) return
        val t = finalT
        val k = easeOut(t / 0.6f)
        pFill.color = Color.argb((90 * k).toInt(), 4, 8, 18)
        canvas.drawRect(0f, 0f, sw, sh, pFill)
        val win = winnerOf(w)
        val celebrate = win >= 0 && (localTeamId < 0 || win == localTeamId)
        val trophyX = sw * 0.27f
        val px = if (win >= 0) sw * 0.67f else sw * 0.5f
        if (win >= 0) {
            val info = w.teams[win].info
            val ty = sh * 0.5f
            if (celebrate) {
                canvas.save()
                canvas.translate(trophyX, ty)
                canvas.rotate(t * 14f)
                pRays.color = Color.argb((34 * k).toInt(), 253, 224, 71)
                canvas.drawPath(rayPath, pRays)
                canvas.restore()
            }
            val bmp = trophyBmp
            if (bmp != null) {
                val pop = max(0.01f, easeOutBack((t - 0.1f) / 0.6f))
                val hh = dp(TROPHY_DP) * pop
                val ww = hh * bmp.width / bmp.height
                // soft gold glow behind the cup; the pedestal and reflection stay still
                val gr = hh * 0.62f * (1f + 0.05f * sin(t * 2.5f))
                pGlow.shader = goldGlowShader
                pGlow.alpha = (255 * k).toInt()
                canvas.save(); canvas.translate(trophyX, ty - hh * 0.12f); canvas.scale(gr, gr)
                canvas.drawCircle(0f, 0f, 1f, pGlow)
                canvas.restore()
                rect.set(trophyX - ww * 0.5f, ty - hh * 0.5f, trophyX + ww * 0.5f, ty + hh * 0.5f)
                bmpPaint.alpha = (255 * min(1f, k * 1.5f)).toInt()
                canvas.drawBitmap(bmp, null, rect, bmpPaint)
            }
        }

        val txt = easeOut((t - 0.35f) / 0.4f)
        val ha = (255 * txt).toInt()
        val top = sh * 0.17f
        pBig.textSize = dp(36f)
        pBig.color = Color.WHITE
        pBig.alpha = ha
        val head = if (win < 0) "FINAL" else if (w.isShootout) "SHOOTOUT VICTORY" else if (celebrate) "VICTORY" else "FINAL"
        canvas.drawText(head, px, top, pBig)
        pBig.alpha = 255
        if (win >= 0) {
            pTextC.textSize = dp(17f)
            pTextC.color = Color.argb(ha, 253, 230, 138)
            canvas.drawText(fullUp[win], px, top + dp(26f), pTextC)
        }
        pTextC.textSize = dp(26f)
        pTextC.color = Color.argb(ha, 255, 255, 255)
        canvas.drawText(finalScoreStr, px, top + dp(62f), pTextC)

        // stats card
        val cardIn = easeOut((t - 0.6f) / 0.5f)
        if (cardIn > 0f) {
            val cw = dp(310f)
            val ch = dp(62f) + statRows * dp(32f)
            val cl = px - cw / 2f
            val ct = top + dp(84f) + (1f - cardIn) * dp(24f)
            val a = (255 * cardIn).toInt()
            rect.set(cl, ct, cl + cw, ct + ch)
            pFill.color = Color.argb((225 * cardIn).toInt(), 8, 14, 28)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), pFill)
            pStroke.color = Color.argb((70 * cardIn).toInt(), 255, 255, 255); pStroke.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(rect, dp(12f), dp(12f), pStroke)
            val i0 = w.teams[0].info; val i1 = w.teams[1].info
            drawCrest(canvas, cl + dp(26f), ct + dp(24f), dp(24f), i0)
            drawCrest(canvas, cl + cw - dp(26f), ct + dp(24f), dp(24f), i1)
            pTextL.textSize = dp(14f); pTextL.color = Color.argb(a, 255, 255, 255)
            canvas.drawText(i0.abbr, cl + dp(44f), ct + dp(29f), pTextL)
            val aw = pTextL.measureText(i1.abbr)
            canvas.drawText(i1.abbr, cl + cw - dp(44f) - aw, ct + dp(29f), pTextL)
            val barL = px - dp(86f); val barW = dp(172f)
            for (r in 0 until statRows) {
                val ry = ct + dp(60f) + r * dp(32f)
                pTextC.textSize = dp(11f); pTextC.color = Color.argb(a, 159, 179, 204)
                canvas.drawText(statLabel[r], px, ry, pTextC)
                val by = ry + dp(5f)
                pFill.color = Color.argb(a, 30, 41, 59)
                rect.set(barL, by, barL + barW, by + dp(7f)); canvas.drawRoundRect(rect, dp(3.5f), dp(3.5f), pFill)
                if (r < 2) {
                    // share of the two totals
                    val f = statFrac[r] * cardIn
                    pFill.color = i0.primary; pFill.alpha = a
                    rect.set(barL, by, barL + max(dp(4f), (barW - dp(2f)) * f), by + dp(7f)); canvas.drawRoundRect(rect, dp(3.5f), dp(3.5f), pFill)
                    pFill.color = i1.primary; pFill.alpha = a
                    val rl = barL + (barW - dp(2f)) * f + dp(2f)
                    rect.set(min(rl, barL + barW - dp(4f)), by, barL + barW, by + dp(7f)); canvas.drawRoundRect(rect, dp(3.5f), dp(3.5f), pFill)
                } else {
                    // absolute percentage (0-100) per team, each filling its own half outward from the centre
                    val half = barW / 2f
                    val bm = barL + half
                    pFill.color = i0.primary; pFill.alpha = a
                    rect.set(bm - half * statP0 * cardIn - dp(1f), by, bm - dp(1f), by + dp(7f)); canvas.drawRect(rect, pFill)
                    pFill.color = i1.primary; pFill.alpha = a
                    rect.set(bm + dp(1f), by, bm + half * statP1 * cardIn + dp(1f), by + dp(7f)); canvas.drawRect(rect, pFill)
                    pFill.color = Color.argb(a, 120, 140, 170)
                    canvas.drawRect(bm - dp(0.5f), by - dp(2f), bm + dp(0.5f), by + dp(9f), pFill)
                }
                pTextC.textSize = dp(15f); pTextC.color = Color.argb(a, 255, 255, 255)
                canvas.drawText(statL[r], cl + dp(30f), by + dp(8f), pTextC)
                canvas.drawText(statR[r], cl + cw - dp(30f), by + dp(8f), pTextC)
            }
        }
        if (t > 1.2f) {
            pTextC.textSize = dp(13f)
            pTextC.color = Color.argb((120 + 100 * sin(anim * 4f)).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawText("TAP TO CONTINUE", sw / 2f, sh - dp(22f), pTextC)
        }
    }

    private fun drawShootoutHint(canvas: Canvas, w: World) {
        if (!w.isShootout || w.phase != Phase.PLAY || localTeamId < 0 || w.shootoutTurn == localTeamId) return
        pTextC.textSize = dp(15f)
        val tw = pTextC.measureText("MOVE TO BLOCK SHOT")
        rect.set(sw / 2f - tw / 2f - dp(16f), sh - dp(46f), sw / 2f + tw / 2f + dp(16f), sh - dp(14f))
        pFill.color = Color.argb(185, 8, 14, 28)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), pFill)
        pStroke.color = K_FBBF24; pStroke.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), pStroke)
        pTextC.color = Color.WHITE
        canvas.drawText("MOVE TO BLOCK SHOT", sw / 2f, sh - dp(24f), pTextC)
    }

    companion object {
        const val CEL_DUR = 3.1f
        const val TROPHY_DP = 250f
        const val INTRO_DUR = 3.2f
    }
}
