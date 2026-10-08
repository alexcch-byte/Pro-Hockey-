package com.tablehockey.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.model.TeamStyle
import com.tablehockey.game.model.TeamStyleStore
import java.util.Random

/**
 * Full-screen main menu backdrop: night arena with crowd, spotlight cones, an ice sheet with
 * markings and a hero skater wearing the player's club kit. Pure canvas, drawn on invalidate only.
 */
class MenuBackdropView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var team: TeamInfo = TeamInfo.ALL[TeamInfo.DEFAULT_HOME]
    private var style: TeamStyle = TeamStyle.defaultFor(team)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private var wallShader: Shader? = null
    private var iceShader: Shader? = null
    private var glowShader: Shader? = null
    private var bandShader: Shader? = null
    private var coneShader: Shader? = null
    private var vignetteShader: Shader? = null
    private var scrimShader: Shader? = null
    private var heroBmp: Bitmap? = null
    private var heroUnit = 0f
    private var goalieBmp: Bitmap? = null
    private var goalieUnit = 0f
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var crowd: FloatArray = FloatArray(0)
    private var crowdColors: IntArray = IntArray(0)

    /** Shows [index] (into [TeamInfo.ALL]) as the hero, using its saved customisation. */
    fun setTeam(index: Int) {
        team = TeamInfo.ALL[index.coerceIn(0, TeamInfo.ALL.size - 1)]
        style = TeamStyleStore.styleFor(team)
        buildHero()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val horizon = h * 0.60f
        wallShader = LinearGradient(0f, 0f, 0f, horizon,
            intArrayOf(0xFF03060B.toInt(), 0xFF0B1B2E.toInt(), 0xFF16385C.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        iceShader = LinearGradient(0f, horizon, 0f, h.toFloat(),
            intArrayOf(0xFFB9D7EE.toInt(), 0xFFE9F4FC.toInt(), 0xFF9CC3E2.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        glowShader = RadialGradient(w * 0.3f, h * 0.75f, w * 0.38f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        bandShader = LinearGradient(0f, h * 0.30f, 0f, horizon, 0x00000000, 0xCC03060B.toInt(), Shader.TileMode.CLAMP)
        coneShader = LinearGradient(0f, 0f, 0f, horizon + h * 0.12f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        vignetteShader = LinearGradient(0f, 0f, 0f, h * 0.2f, 0xAA000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        scrimShader = LinearGradient(0f, 0f, w * 0.62f, 0f, 0xC003060B.toInt(), 0x0003060B, Shader.TileMode.CLAMP)
        buildHero()
        // crowd: seeded specks in the upper wall
        val rnd = Random(7)
        val n = 1100
        crowd = FloatArray(n * 3)
        crowdColors = IntArray(n)
        val palette = intArrayOf(0xFFC8323C.toInt(), 0xFFE8D2B4.toInt(), 0xFF2E5FA8.toInt(), 0xFFF2B84B.toInt(), 0xFF3B4A5E.toInt())
        for (i in 0 until n) {
            crowd[i * 3] = rnd.nextFloat() * w
            crowd[i * 3 + 1] = h * (0.08f + 0.3f * rnd.nextFloat())
            crowd[i * 3 + 2] = h * (0.0022f + 0.0035f * rnd.nextFloat())
            crowdColors[i] = palette[rnd.nextInt(palette.size)]
        }
    }

    /** Re-renders the hero skater bitmap; only runs on size or team/style change. */
    private fun buildHero() {
        if (height <= 0) return
        heroBmp?.recycle()
        goalieBmp?.recycle()
        val px = (height * 0.68f).toInt().coerceAtLeast(16)
        heroUnit = px / 100f
        heroBmp = TeamArt.skaterBitmap(style, team.abbr, px)
        val gpx = (px * 0.62f).toInt().coerceAtLeast(16)
        goalieUnit = gpx / 100f
        goalieBmp = TeamArt.goalieBitmap(style, team.abbr, gpx)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * 0.60f

        // arena wall
        p.shader = wallShader
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null
        p.alpha = 255
        for (i in crowdColors.indices) {
            p.color = crowdColors[i]
            p.alpha = 190
            c.drawCircle(crowd[i * 3], crowd[i * 3 + 1], crowd[i * 3 + 2], p)
        }
        p.alpha = 255
        // dark band above boards to calm the crowd
        p.shader = bandShader
        c.drawRect(0f, h * 0.30f, w, horizon, p)
        p.shader = null

        // spotlight cones
        val spots = floatArrayOf(0.12f, 0.36f, 0.62f, 0.88f)
        for (sx in spots) {
            path.reset()
            path.moveTo(w * (sx - 0.015f), 0f); path.lineTo(w * (sx + 0.015f), 0f)
            path.lineTo(w * (sx + 0.17f), horizon + h * 0.12f); path.lineTo(w * (sx - 0.17f), horizon + h * 0.12f)
            path.close()
            p.shader = coneShader
            c.drawPath(path, p)
        }
        p.shader = null

        // boards
        p.color = 0xFF0D2540.toInt()
        c.drawRect(0f, horizon - h * 0.035f, w, horizon, p)
        p.color = 0xFFF5E11B.toInt()
        c.drawRect(0f, horizon - h * 0.038f, w, horizon - h * 0.032f, p)
        p.color = 0xFFD01F2E.toInt()
        c.drawRect(0f, horizon - 0.004f * h, w, horizon + 0.004f * h, p)

        // ice
        p.shader = iceShader
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.008f
        // blue line + red centre line in perspective
        p.color = 0x992E86FF.toInt()
        path.reset(); path.moveTo(w * 0.78f, horizon); path.lineTo(w * 1.02f, h); c.drawPath(path, p)
        p.color = 0x99D8323C.toInt()
        path.reset(); path.moveTo(w * 0.54f, horizon); path.lineTo(w * 0.62f, h); c.drawPath(path, p)
        // faceoff circle
        p.color = 0x88D8323C.toInt()
        oval.set(w * 0.05f, h * 0.76f, w * 0.55f, h * 1.12f)
        c.drawOval(oval, p)
        p.style = Paint.Style.FILL
        p.shader = glowShader
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null

        // speed streaks
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 5) {
            p.strokeWidth = h * (0.004f + 0.002f * i)
            p.color = Color.argb(70 - i * 8, 255, 255, 255)
            val y = h * (0.7f + 0.045f * i)
            c.drawLine(w * (0.02f + 0.02f * i), y, w * (0.18f + 0.03f * i), y - h * 0.02f, p)
        }
        p.style = Paint.Style.FILL

        // hero pair (cached bitmaps): goalie behind and smaller, skater in front, floor shadows
        val gcx = w * 0.20f
        val gfeet = h * 0.88f
        val gu = goalieUnit
        p.color = 0x55102A44
        oval.set(gcx - gu * 36f, gfeet - gu * 3f, gcx + gu * 38f, gfeet + gu * 4f)
        c.drawOval(oval, p)
        goalieBmp?.let { c.drawBitmap(it, gcx - it.width / 2f, gfeet - TeamArt.FEET_UNITS * gu, bmpPaint) }
        val cx = w * 0.40f
        val feet = h * 0.95f
        val hu = heroUnit
        p.color = 0x66102A44
        oval.set(cx - hu * 30f, feet - hu * 3f, cx + hu * 36f, feet + hu * 4f)
        c.drawOval(oval, p)
        heroBmp?.let { c.drawBitmap(it, cx - it.width / 2f, feet - TeamArt.FEET_UNITS * hu, bmpPaint) }

        // left scrim keeps the title block readable over the crowd
        p.shader = scrimShader
        c.drawRect(0f, 0f, w * 0.62f, h * 0.6f, p)
        p.shader = null

        // top vignette
        p.shader = vignetteShader
        c.drawRect(0f, 0f, w, h * 0.2f, p)
        p.shader = null
    }
}
