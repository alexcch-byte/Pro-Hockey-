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
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.model.TeamStyle
import kotlin.math.min

/**
 * Dark spotlit stage (like the Hockey Allstars customise screen): skater front-left, a smaller
 * goalie back-right, a crest header and the club name. The shaded figures are rendered once into
 * bitmaps and only re-rendered when the team, style or view size changes, so onDraw is cheap.
 */
class TeamPreviewView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var team: TeamInfo? = null
        set(v) { if (field != v) { field = v; dirty = true; invalidate() } }
    var style: TeamStyle? = null
        set(v) { if (field != v) { field = v; dirty = true; invalidate() } }

    /** Small status line under the club name (e.g. "UNSAVED CHANGES"). */
    var caption: String = ""
        set(v) { if (field != v) { field = v; invalidate() } }

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val decalPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 }
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9FB3C8.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.12f
    }
    private val cone = Path()
    private val oval = RectF()
    private var bgShader: Shader? = null
    private var coneShader: Shader? = null
    private var floorShader: Shader? = null
    private var shadowShader: Shader? = null

    private var title = ""
    private var dirty = true
    private var skaterBmp: Bitmap? = null
    private var goalieBmp: Bitmap? = null
    private var crestBmp: Bitmap? = null

    // layout results (pixels), computed with the bitmaps
    private var skaterCx = 0f
    private var goalieCx = 0f
    private var skaterFeet = 0f
    private var goalieFeet = 0f
    private var skaterUnit = 0f
    private var goalieUnit = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w * 0.5f
        bgShader = RadialGradient(cx, h * 0.45f, w * 0.8f, intArrayOf(0xFF2A4260.toInt(), 0xFF0A1420.toInt(), 0xFF04080D.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        coneShader = LinearGradient(0f, 0f, 0f, h * 0.85f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        floorShader = RadialGradient(cx, h * 0.88f, w * 0.55f, 0x66BFE3FF, 0x00BFE3FF, Shader.TileMode.CLAMP)
        shadowShader = null
        dirty = true
    }

    /**
     * Skater sits front-left, goalie smaller and further back at right. Sizes come from one
     * constraint: both figures' art extents (skater x -27..39, goalie x -36..37 units) must fit
     * inside the width with margins and not touch, so nothing clips or overlaps at any aspect.
     */
    private fun rebuild() {
        val w = width
        val h = height
        val st = style
        val t = team
        skaterBmp?.recycle(); goalieBmp?.recycle(); crestBmp?.recycle()
        skaterBmp = null; goalieBmp = null; crestBmp = null
        dirty = false
        title = t?.fullName?.uppercase() ?: ""
        if (w <= 0 || h <= 0 || st == null || t == null) return

        // Figures deliberately overlap a little (skater in front, goalie behind and smaller).
        // Art extents in units: skater x -29..31 (head reaches y = -8), goalie x -41..42.
        //   skater left edge: cx - 29u >= 0.03w  with cx = 0.36w
        //   goalie right edge: cx + 42*gs*u <= 0.97w  with cx = 0.68w
        //   skater head top: feet - 108u >= 0.03h
        val goalieScale = 0.84f
        val uByWidth = min((0.36f - 0.03f) * w / 29f, (0.97f - 0.68f) * w / (42f * goalieScale))
        val uByHeight = h * (0.88f - 0.03f) / 108f
        val u = min(uByWidth, uByHeight)
        skaterUnit = u
        goalieUnit = u * goalieScale
        skaterCx = 0.36f * w
        goalieCx = 0.68f * w
        skaterFeet = h * 0.88f
        goalieFeet = h * 0.80f

        // bitmaps use whole-pixel heights; take the real unit size from them so feet land exactly
        val sPx = (u * 100f).toInt().coerceAtLeast(8)
        val gPx = (goalieUnit * 100f).toInt().coerceAtLeast(8)
        skaterUnit = sPx / 100f
        goalieUnit = gPx / 100f
        skaterBmp = TeamArt.skaterBitmap(st, t.abbr, sPx)
        goalieBmp = TeamArt.goalieBitmap(st, t.abbr, gPx)

    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (team == null || style == null) return
        if (dirty) rebuild()

        p.shader = bgShader
        oval.set(0f, 0f, w, h)
        c.drawRoundRect(oval, 14 * d, 14 * d, p)

        cone.reset()
        cone.moveTo(w * 0.40f, 0f); cone.lineTo(w * 0.60f, 0f)
        cone.lineTo(w * 0.98f, h * 0.9f); cone.lineTo(w * 0.02f, h * 0.9f); cone.close()
        p.shader = coneShader
        c.drawPath(cone, p)

        p.shader = floorShader
        oval.set(w * 0.02f, h * 0.82f, w * 0.98f, h * 0.99f)
        c.drawOval(oval, p)
        p.shader = null

        // contact shadows (goalie first: it stands behind)
        p.color = 0x77000000
        oval.set(goalieCx - goalieUnit * 30f, goalieFeet - h * 0.012f, goalieCx + goalieUnit * 30f, goalieFeet + h * 0.018f)
        c.drawOval(oval, p)
        oval.set(skaterCx - skaterUnit * 22f, skaterFeet - h * 0.014f, skaterCx + skaterUnit * 26f, skaterFeet + h * 0.022f)
        c.drawOval(oval, p)

        goalieBmp?.let { c.drawBitmap(it, goalieCx - it.width / 2f, goalieFeet - TeamArt.FEET_UNITS * goalieUnit, bmpPaint) }
        skaterBmp?.let { c.drawBitmap(it, skaterCx - it.width / 2f, skaterFeet - TeamArt.FEET_UNITS * skaterUnit, bmpPaint) }

        name.textSize = h * 0.06f
        c.drawText(title, w * 0.5f, h * 0.955f, name)
        sub.textSize = h * 0.026f
        c.drawText(caption, w * 0.5f, h * 0.99f, sub)
    }
}
