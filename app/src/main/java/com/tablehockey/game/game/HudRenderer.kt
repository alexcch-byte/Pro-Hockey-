package com.tablehockey.game.game

import android.graphics.Canvas
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
import kotlin.math.cos
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
    private val MAXC = 130
    private val cx = FloatArray(MAXC)
    private val cy = FloatArray(MAXC)
    private val cvx = FloatArray(MAXC)
    private val cvy = FloatArray(MAXC)
    private val cph = FloatArray(MAXC)
    private val cbk = IntArray(MAXC)
    private var cn = 0
    private val bucketPts = Array(4) { FloatArray(MAXC * 2) }
    private val bucketN = IntArray(4)
    private val bucketColor = IntArray(4)
    private val confPaint = Paint().apply { strokeCap = Paint.Cap.SQUARE; isAntiAlias = false }

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
    private val pBigShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER; textSkewX = -0.15f
    }
    private val pVignette = Paint()
    private var vignetteShader: RadialGradient? = null
    private val pGoldFill = Paint(Paint.ANTI_ALIAS_FLAG)
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

    init {
        pGoldFill.shader = LinearGradient(-0.55f, 0f, 0.55f, 0f,
            intArrayOf(Color.parseColor("#FFF3B0"), Color.parseColor("#FBBF24"), Color.parseColor("#B45309")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    private fun layout(w: Int, h: Int) {
        sw = w.toFloat(); sh = h.toFloat()
        pGloss.shader = LinearGradient(0f, dp(8f), 0f, dp(8f) + dp(17f), Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        vignetteShader = RadialGradient(sw / 2f, sh / 2f, max(sw, sh) * 0.62f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(190, 0, 0, 0)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        pVignette.shader = vignetteShader
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
                emptyNetStr[i] = info.abbr + " EMPTY NET"
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
            if (celebT > CEL_DUR) celebT = -1f
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
                if (win >= 0) {
                    setConfettiColors(w.teams[win].info)
                    finalScoreStr = buildFinalScore(w)
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
        setConfettiColors(w.teams[team].info)
        spawnBurst(if (celebLocal) 80 else 24)
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
        bucketColor[3] = Color.parseColor("#FBBF24")
    }

    private fun addConfetti(x: Float, y: Float, vx: Float, vy: Float) {
        if (cn >= MAXC) return
        cx[cn] = x; cy[cn] = y; cvx[cn] = vx; cvy[cn] = vy
        cph[cn] = rng.nextFloat() * 6.28f; cbk[cn] = rng.nextInt(4)
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
            cx[i] += (cvx[i] + sin(cph[i]) * dp(26f)) * d
            cy[i] += cvy[i] * d
            if (cy[i] > sh + dp(12f)) {
                cn--
                cx[i] = cx[cn]; cy[i] = cy[cn]; cvx[i] = cvx[cn]; cvy[i] = cvy[cn]; cph[i] = cph[cn]; cbk[i] = cbk[cn]
            } else i++
        }
    }

    private fun drawConfetti(canvas: Canvas) {
        if (cn == 0) return
        for (b in 0..3) bucketN[b] = 0
        for (i in 0 until cn) {
            val b = cbk[i]
            val a = bucketPts[b]
            val k = bucketN[b]
            a[k * 2] = cx[i]; a[k * 2 + 1] = cy[i]
            bucketN[b] = k + 1
        }
        for (b in 0..3) {
            if (bucketN[b] == 0) continue
            confPaint.color = bucketColor[b]
            confPaint.strokeWidth = dp(4.5f + b * 1.3f)
            canvas.drawPoints(bucketPts[b], 0, bucketN[b] * 2, confPaint)
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
        val l = dp(10f); val t = dp(8f); val h = dp(34f)
        val blockW = dp(78f); val scoreW = dp(30f); val centreW = dp(98f)
        val r = dp(9f)
        val total = (blockW + scoreW) * 2f + centreW
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
                pTextC.color = Color.parseColor("#FDE047")
                canvas.drawText(num(w.teams[i].score), cxs, t + dp(24.5f), pTextC)
                canvas.restore()
            } else canvas.drawText(num(w.teams[i].score), cxs, t + dp(24.5f), pTextC)
        }

        // centre: period + clock (or shootout timer)
        val ccx = hsx + scoreW + centreW / 2f
        pTextC.color = Color.parseColor("#9FB3CC")
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
            canvas.drawText(if (w.overtime) "OT" else w.periodText(), ccx - dp(28f), t + dp(21f), pTextC)
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
            pTextC.color = if (low && (anim * 2f).toInt() % 2 == 0) Color.parseColor("#F87171") else Color.WHITE
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
            drawPill(canvas, ccx, y, dp(150f), dp(15f), pPlate.color, soStr, Color.WHITE, 9.5f)
        } else {
            val key = w.teams[0].shots * 1000 + w.teams[1].shots
            if (key != shotsKey) {
                shotsKey = key
                sb.setLength(0)
                sb.append("SHOTS  ").append(w.teams[0].shots).append("  -  ").append(w.teams[1].shots)
                shotsStr = sb.toString()
            }
            drawPill(canvas, ccx, y, dp(104f), dp(15f), pPlate.color, shotsStr, Color.parseColor("#CBD5E1"), 9.5f)
        }
        y += dp(19f)

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
            drawPill(canvas, px, t + h + dp(6f) + (if (w.isShootout) dp(18f) else 0f), dp(80f), dp(15f), Color.parseColor("#EA580C"), fireStr[i], Color.WHITE, 9.5f)
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
            drawPill(canvas, ccx, y, dp(132f), dp(15f), adv.info.primary, ppStr, adv.info.text, 9.5f, true)
        } else if (w.goaliePulled[0] || w.goaliePulled[1]) {
            val id = if (w.goaliePulled[0]) 0 else 1
            drawPill(canvas, ccx, y, dp(112f), dp(15f), Color.parseColor("#B91C1C"), emptyNetStr[id], Color.WHITE, 9.5f, true)
        }
    }

    private fun drawShootoutDots(canvas: Canvas, w: World, cxm: Float, y: Float, side: Int = 0) {
        val sp = dp(10f)
        val x0 = cxm - 2 * sp
        rect.set(x0 - dp(8f), y, x0 + 4 * sp + dp(8f), y + dp(15f))
        canvas.drawRoundRect(rect, dp(7f), dp(7f), pPlate)
        for (r in 0 until 5) {
            val res = w.shootoutAttempts[side][r]
            val px = x0 + r * sp
            val py = y + dp(7.5f)
            if (res == 0) {
                pStroke.color = Color.parseColor("#64748B"); pStroke.strokeWidth = dp(1.2f)
                canvas.drawCircle(px, py, dp(3.2f), pStroke)
            } else {
                pFill.color = if (res == 1) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
                canvas.drawCircle(px, py, dp(3.4f), pFill)
            }
        }
    }

    private fun drawPill(canvas: Canvas, cxp: Float, y: Float, wd: Float, ht: Float, fill: Int, text: String, tc: Int, size: Float, border: Boolean = false) {
        rect.set(cxp - wd / 2f, y, cxp + wd / 2f, y + ht)
        pFill.color = fill
        canvas.drawRoundRect(rect, ht / 2f, ht / 2f, pFill)
        if (border) {
            pStroke.color = Color.parseColor("#FBBF24"); pStroke.strokeWidth = dp(1.2f)
            canvas.drawRoundRect(rect, ht / 2f, ht / 2f, pStroke)
        }
        pTextC.color = tc
        pTextC.textSize = dp(size)
        canvas.drawText(text, cxp, y + ht * 0.5f + dp(size) * 0.36f, pTextC)
    }

    // ---------------------------------------------------------------- banner plate

    fun drawBanner(canvas: Canvas, w: World) {
        val text = w.banner ?: return
        if (w.bannerTimer <= 0f || w.phase == Phase.GAME_OVER) return
        if (celebT >= 0f && text == "GOAL!") return
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
        pFill.color = Color.argb(a, 251, 191, 36)
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
        pressedNow[0] = c.shootDown; pressedNow[1] = c.passDown; pressedNow[2] = c.hitDown; pressedNow[3] = c.dekeDown
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
        if (c.joyActive) {
            pFill.color = Color.argb(150, 255, 255, 255)
            canvas.drawCircle(c.joyKnobX, c.joyKnobY, jr * 0.4f, pFill)
            pStroke.color = Color.argb(230, 255, 255, 255)
            canvas.drawCircle(c.joyKnobX, c.joyKnobY, jr * 0.4f, pStroke)
        }

        val controlled = if (localTeam >= 0) w.controlledSkater(localTeam) else null
        c.dekeEnabled = controlled?.isGoalie != true
        if (controlled?.isGoalie == true) {
            textButton(canvas, c.shootX, c.shootY, c.shootR, "BUTTERFLY", "5-hole", press[0], Color.parseColor("#DC2626"))
            textButton(canvas, c.passX, c.passY, c.passR, "POKE", "stick", press[1], Color.parseColor("#2563EB"))
            textButton(canvas, c.hitX, c.hitY, c.hitR, "PAD STACK", "sprawl", press[2], Color.parseColor("#D97706"))
            return
        }
        val hasPuck = localTeam >= 0 && w.puck.carrier != null && w.puck.carrier === controlled
        buttonBase(canvas, c.shootX, c.shootY, c.shootR, press[0], Color.parseColor("#EF4444"))
        iconCrosshair(canvas, c.shootX, c.shootY - c.shootR * 0.16f, c.shootR * 0.40f)
        buttonLabel(canvas, c.shootX, c.shootY, c.shootR, if (hasPuck) "SHOOT" else "POKE")
        buttonBase(canvas, c.passX, c.passY, c.passR, press[1], Color.parseColor("#3B82F6"))
        iconPass(canvas, c.passX, c.passY - c.passR * 0.16f, c.passR * 0.5f)
        buttonLabel(canvas, c.passX, c.passY, c.passR, if (hasPuck) "PASS" else "SWITCH")
        buttonBase(canvas, c.hitX, c.hitY, c.hitR, press[2], Color.parseColor("#F59E0B"))
        iconBurst(canvas, c.hitX, c.hitY - c.hitR * 0.16f, c.hitR * 0.36f)
        buttonLabel(canvas, c.hitX, c.hitY, c.hitR, "HIT")
        buttonBase(canvas, c.dekeX, c.dekeY, c.dekeR, press[3], Color.parseColor("#8B5CF6"))
        iconZigzag(canvas, c.dekeX, c.dekeY - c.dekeR * 0.16f, c.dekeR * 0.5f)
        buttonLabel(canvas, c.dekeX, c.dekeY, c.dekeR, "DEKE")
        val cd = c.dekeCooldownFrac()
        if (cd > 0f) {
            pStroke.color = Color.argb(220, 255, 255, 255); pStroke.strokeWidth = dp(3f)
            rect.set(c.dekeX - c.dekeR - dp(4f), c.dekeY - c.dekeR - dp(4f), c.dekeX + c.dekeR + dp(4f), c.dekeY + c.dekeR + dp(4f))
            canvas.drawArc(rect, -90f, 360f * cd, false, pStroke)
        }

        if (c.shootDown && hasPuck) {
            val ch = c.currentCharge()
            pStroke.strokeWidth = dp(5f)
            pStroke.color = when {
                ch >= 1f -> if ((anim * 10f).toInt() % 2 == 0) Color.parseColor("#FEF2F2") else Color.parseColor("#EF4444")
                ch > 0.6f -> Color.parseColor("#F97316")
                else -> Color.parseColor("#FDE047")
            }
            rect.set(c.shootX - c.shootR - dp(7f), c.shootY - c.shootR - dp(7f), c.shootX + c.shootR + dp(7f), c.shootY + c.shootR + dp(7f))
            canvas.drawArc(rect, -90f, 360f * ch, false, pStroke)
        }
    }

    private fun buttonBase(canvas: Canvas, x: Float, y: Float, r0: Float, press: Float, tint: Int) {
        val r = r0 * (1f - 0.08f * press)
        pFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawCircle(x, y + dp(3f), r, pFill)
        pFill.color = Color.argb((55 + 110 * press).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawCircle(x, y, r, pFill)
        pFill.color = Color.argb((45 + 30 * press).toInt(), 255, 255, 255)
        canvas.drawCircle(x, y, r, pFill)
        pStroke.color = Color.argb((200 + 55 * press).toInt(), 255, 255, 255)
        pStroke.strokeWidth = dp(2.5f)
        canvas.drawCircle(x, y, r - dp(1.2f), pStroke)
    }

    private fun buttonLabel(canvas: Canvas, x: Float, y: Float, r: Float, label: String) {
        pTextC.color = Color.WHITE
        pTextC.textSize = r * 0.25f
        canvas.drawText(label, x, y + r * 0.66f, pTextC)
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

    /** Drawn over the world, under the scoreboard: vignette, light beams and confetti. */
    fun drawCelebrationBack(canvas: Canvas, w: World) {
        if (celebT >= 0f) {
            val t = celebT
            val fadeIn = (t / 0.3f).coerceIn(0f, 1f)
            val fadeOut = ((CEL_DUR - t) / 0.5f).coerceIn(0f, 1f)
            val k = min(fadeIn, fadeOut)
            val info = w.teams[celebTeam].info
            if (celebLocal) {
                // spotlight vignette
                pVignette.alpha = (255 * k).toInt()
                canvas.drawRect(0f, 0f, sw, sh, pVignette)
                // sweeping beams from the roof
                val sway = sin(t * 2.4f) * sw * 0.08f
                path.reset()
                path.moveTo(sw * 0.18f, -dp(10f)); path.lineTo(sw * 0.5f + sway - dp(70f), sh); path.lineTo(sw * 0.5f + sway + dp(70f), sh); path.lineTo(sw * 0.18f + dp(30f), -dp(10f)); path.close()
                path.moveTo(sw * 0.82f, -dp(10f)); path.lineTo(sw * 0.5f - sway + dp(70f), sh); path.lineTo(sw * 0.5f - sway - dp(70f), sh); path.lineTo(sw * 0.82f - dp(30f), -dp(10f)); path.close()
                pFill.color = Color.argb((46 * k).toInt(), Color.red(bright(info.primary)), Color.green(bright(info.primary)), Color.blue(bright(info.primary)))
                canvas.drawPath(path, pFill)
            }
            // goal-horn flash
            if (t < 0.18f) {
                pFill.color = Color.argb((140 * (1f - t / 0.18f)).toInt(), 255, 255, 255)
                canvas.drawRect(0f, 0f, sw, sh, pFill)
            }
        }
        drawConfetti(canvas)
    }

    /** Goal band + pregame splash + final trophy, over everything except the pause button. */
    fun drawOverlays(canvas: Canvas, w: World) {
        drawGoalBand(canvas, w)
        drawIntro(canvas, w)
        drawFinal(canvas, w)
        drawShootoutHint(canvas, w)
    }

    private fun drawGoalBand(canvas: Canvas, w: World) {
        if (celebT < 0f) return
        val t = celebT
        val info = w.teams[celebTeam].info
        val inP = easeOutBack(t / 0.4f)
        val outP = ((t - (CEL_DUR - 0.5f)) / 0.5f).coerceIn(0f, 1f)
        val cyb = sh * 0.40f
        val bh = dp(104f)
        val bandOff = (1f - inP.coerceAtMost(1f)) * -sw - outP * sw
        canvas.save()
        canvas.translate(bandOff, 0f)
        val sk = dp(26f)
        // secondary pin-stripes
        pFill.color = info.secondary
        path.reset()
        path.moveTo(-sk, cyb - bh / 2f - dp(10f)); path.lineTo(sw + sk * 2f, cyb - bh / 2f - dp(10f)); path.lineTo(sw + sk, cyb - bh / 2f - dp(4f)); path.lineTo(-sk * 2f, cyb - bh / 2f - dp(4f)); path.close()
        path.moveTo(-sk * 2f, cyb + bh / 2f + dp(4f)); path.lineTo(sw + sk, cyb + bh / 2f + dp(4f)); path.lineTo(sw + sk * 2f, cyb + bh / 2f + dp(10f)); path.lineTo(-sk, cyb + bh / 2f + dp(10f)); path.close()
        canvas.drawPath(path, pFill)
        pFill.color = info.primary
        canvas.drawRect(-sk * 2f, cyb - bh / 2f, sw + sk * 2f, cyb + bh / 2f, pFill)
        // moving sheen
        val sheenX = ((t * 0.9f) % 1.6f - 0.3f) * sw
        pFill.color = Color.argb(34, 255, 255, 255)
        path.reset()
        path.moveTo(sheenX, cyb - bh / 2f); path.lineTo(sheenX + dp(120f), cyb - bh / 2f); path.lineTo(sheenX + dp(90f), cyb + bh / 2f); path.lineTo(sheenX - dp(30f), cyb + bh / 2f); path.close()
        canvas.drawPath(path, pFill)
        drawCrest(canvas, sw * 0.5f - dp(230f), cyb, dp(76f), info)
        drawCrest(canvas, sw * 0.5f + dp(230f), cyb, dp(76f), info)
        canvas.restore()

        // GOAL! text with spring pop
        val pop = easeOutBack((t - 0.1f) / 0.45f)
        val sc = max(0.01f, pop) * (1f + 0.04f * sin(t * 10f))
        canvas.save()
        canvas.translate(sw / 2f, cyb)
        canvas.scale(sc, sc)
        canvas.rotate(-3f)
        pBig.textSize = dp(78f); pBigShadow.textSize = dp(78f)
        val tcol = info.text
        pBig.color = if (Color.red(info.primary) * 3 + Color.green(info.primary) * 6 > 1500) Color.parseColor("#111827") else tcol
        pBig.alpha = ((1f - outP) * 255).toInt()
        pBigShadow.alpha = (140 * (1f - outP)).toInt()
        canvas.drawText(if (celebLocal) "GOAL!" else "GOAL", dp(3f), dp(3f) + dp(10f), pBigShadow)
        canvas.drawText(if (celebLocal) "GOAL!" else "GOAL", 0f, dp(10f), pBig)
        canvas.restore()
        pBig.alpha = 255; pBig.color = Color.WHITE
        // caption under band
        val subIn = easeOut((t - 0.35f) / 0.3f) * (1f - outP)
        if (subIn > 0f) {
            pTextC.textSize = dp(15f)
            pTextC.color = Color.argb((255 * subIn).toInt(), 253, 230, 138)
            canvas.drawText(info.fullName.uppercase(), sw / 2f, cyb + bh / 2f + dp(34f), pTextC)
            pTextC.textSize = dp(22f)
            pTextC.color = Color.argb((255 * subIn).toInt(), 255, 255, 255)
            canvas.drawText(celebScoreStr, sw / 2f, cyb + bh / 2f + dp(62f), pTextC)
        }
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
            pFill.color = Color.parseColor("#0B1426")
            canvas.drawCircle(mid, cyb, dp(34f) * vs, pFill)
            pStroke.color = Color.parseColor("#FBBF24"); pStroke.strokeWidth = dp(3f)
            canvas.drawCircle(mid, cyb, dp(34f) * vs, pStroke)
            pBig.textSize = dp(30f) * vs
            canvas.drawText("VS", mid, cyb + dp(10f) * vs, pBig)
        }
        val cap = easeOut((t - 0.4f) / 0.3f) * (1f - outP)
        if (cap > 0f) {
            pTextC.color = Color.argb((255 * cap).toInt(), 253, 230, 138)
            pTextC.textSize = dp(14f)
            canvas.drawText(w.teams[0].info.league.uppercase() + "  -  PUCK DROP", mid, cyb + ph / 2f + dp(28f), pTextC)
        }
    }

    private fun drawFinal(canvas: Canvas, w: World) {
        if (finalT < 0f) return
        val t = finalT
        val k = easeOut(t / 0.6f)
        pFill.color = Color.argb((150 * k).toInt(), 4, 8, 18)
        canvas.drawRect(0f, 0f, sw, sh, pFill)
        val win = winnerOf(w)
        val mid = sw / 2f
        val ty = sh * 0.38f
        if (win >= 0) {
            val info = w.teams[win].info
            val celebrate = localTeamId < 0 || win == localTeamId
            val pop = easeOutBack((t - 0.1f) / 0.6f)
            val size = dp(120f) * max(0.01f, pop)
            if (celebrate) {
                // sunburst rays
                canvas.save()
                canvas.translate(mid, ty)
                canvas.rotate(t * 14f)
                path.reset()
                val rr = max(sw, sh)
                for (i in 0 until 12) {
                    val a0 = i * (PI / 6).toFloat()
                    val a1 = a0 + (PI / 12).toFloat()
                    path.moveTo(0f, 0f); path.lineTo(cos(a0) * rr, sin(a0) * rr); path.lineTo(cos(a1) * rr, sin(a1) * rr); path.close()
                }
                pRays.color = Color.argb((38 * k).toInt(), 253, 224, 71)
                canvas.drawPath(path, pRays)
                canvas.restore()
            }
            // trophy
            canvas.save()
            canvas.translate(mid, ty + sin(t * 2f) * dp(3f))
            canvas.scale(size / 2f, size / 2f)
            canvas.drawPath(trophyBody, pGoldFill)
            pStroke.color = Color.parseColor("#92400E"); pStroke.strokeWidth = 0.05f
            canvas.drawPath(trophyBody, pStroke)
            pStroke.color = Color.parseColor("#F59E0B"); pStroke.strokeWidth = 0.13f
            canvas.drawPath(trophyHandles, pStroke)
            pFill.color = Color.parseColor("#B45309")
            canvas.drawPath(starPath, pFill)
            pStroke.color = Color.argb(150, 255, 255, 255); pStroke.strokeWidth = 0.07f
            canvas.drawLine(-0.4f, -0.8f, -0.3f, -0.25f, pStroke)
            canvas.restore()

            val txt = easeOut((t - 0.35f) / 0.4f)
            pBig.textSize = dp(34f)
            pBig.color = Color.WHITE
            pBig.alpha = (255 * txt).toInt()
            val head = if (w.isShootout) "SHOOTOUT VICTORY" else if (celebrate) "VICTORY" else "FINAL"
            canvas.drawText(head, mid, ty + dp(100f), pBig)
            pTextC.textSize = dp(17f)
            pTextC.color = Color.argb((255 * txt).toInt(), 253, 230, 138)
            canvas.drawText(info.fullName.uppercase(), mid, ty + dp(126f), pTextC)
            pTextC.textSize = dp(24f)
            pTextC.color = Color.argb((255 * txt).toInt(), 255, 255, 255)
            canvas.drawText(finalScoreStr, mid, ty + dp(158f), pTextC)
            pBig.alpha = 255
        }
    }

    private fun drawShootoutHint(canvas: Canvas, w: World) {
        if (!w.isShootout || w.phase != Phase.PLAY || localTeamId < 0 || w.shootoutTurn == localTeamId) return
        pTextC.textSize = dp(15f)
        pTextC.color = Color.argb(120, 0, 0, 0)
        canvas.drawText("MOVE TO BLOCK SHOT", sw / 2f + dp(1f), sh - dp(15f), pTextC)
        pTextC.color = Color.WHITE
        canvas.drawText("MOVE TO BLOCK SHOT", sw / 2f, sh - dp(16f), pTextC)
    }

    companion object {
        const val CEL_DUR = 3.1f
        const val INTRO_DUR = 3.2f
    }
}
