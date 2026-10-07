package com.tablehockey.game.ui

import android.content.Context
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

/**
 * Dark spotlit stage showing a skater and a goalie in the club's current style, with a big crest
 * and the club name. Set [team] and [style] and it redraws.
 */
class TeamPreviewView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var team: TeamInfo? = null
        set(v) { field = v; invalidate() }
    var style: TeamStyle? = null
        set(v) { field = v; invalidate() }

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
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

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w * 0.5f
        bgShader = RadialGradient(cx, h * 0.45f, w * 0.75f, intArrayOf(0xFF22364D.toInt(), 0xFF0A1420.toInt(), 0xFF04080D.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        coneShader = LinearGradient(0f, 0f, 0f, h * 0.85f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        floorShader = RadialGradient(cx, h * 0.9f, w * 0.5f, 0x55BFE3FF, 0x00BFE3FF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val st = style ?: return
        val t = team ?: return

        p.shader = bgShader
        oval.set(0f, 0f, w, h)
        c.drawRoundRect(oval, 14 * d, 14 * d, p)

        // light cone from above
        cone.reset()
        cone.moveTo(w * 0.42f, 0f); cone.lineTo(w * 0.58f, 0f)
        cone.lineTo(w * 0.92f, h * 0.88f); cone.lineTo(w * 0.08f, h * 0.88f); cone.close()
        p.shader = coneShader
        c.drawPath(cone, p)

        // ice pool
        val feet = h * 0.86f
        p.shader = floorShader
        oval.set(w * 0.04f, feet - h * 0.07f, w * 0.96f, feet + h * 0.09f)
        c.drawOval(oval, p)
        p.shader = null

        // soft contact shadows
        p.color = 0x66000000
        val sx = w * 0.34f
        val gx = w * 0.7f
        oval.set(sx - w * 0.13f, feet - h * 0.015f, sx + w * 0.13f, feet + h * 0.02f); c.drawOval(oval, p)
        oval.set(gx - w * 0.15f, feet - h * 0.015f, gx + w * 0.15f, feet + h * 0.02f); c.drawOval(oval, p)

        val abbr = t.abbr
        TeamArt.drawGoalie(c, gx, feet, h * 0.6f, st, abbr)
        TeamArt.drawSkater(c, sx, feet, h * 0.7f, st, abbr)

        // crest + name
        TeamArt.drawCrest(c, w * 0.5f, h * 0.11f, h * 0.07f, st, abbr)
        name.textSize = h * 0.06f
        c.drawText(t.fullName.uppercase(), w * 0.5f, h * 0.955f, name)
        sub.textSize = h * 0.028f
        c.drawText("SKATER  /  GOALIE", w * 0.5f, h * 0.995f - h * 0.0f, sub)
    }
}
