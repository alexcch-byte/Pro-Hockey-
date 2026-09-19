package com.tablehockey.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * A static picture of the in-game touch layout: ice, the controlled skater,
 * the floating joystick on the left and the SHOOT / PASS / HIT buttons on
 * the right. Drawn to scale with the view so it stays crisp on any tablet.
 */
class ControlsDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val ice = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EDF4F9") }
    private val boards = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4C542"); style = Paint.Style.STROKE; strokeWidth = dp(3f) }
    private val blueLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F5FBF"); style = Paint.Style.STROKE; strokeWidth = dp(4f) }
    private val redLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = dp(4f) }
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D7263D"); style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 40, 60, 90) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 40, 60, 90); style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 255, 255) }
    private val knobEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937") }
    private val btn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(2f) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937"); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B6E3A") }
    private val bodyEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A"); style = Paint.Style.STROKE; strokeWidth = dp(1.5f) }
    private val helmet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2937") }
    private val yoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5D130"); style = Paint.Style.STROKE; strokeWidth = dp(4f); strokeCap = Paint.Cap.ROUND }
    private val stick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B2A1A"); style = Paint.Style.STROKE; strokeWidth = dp(3f); strokeCap = Paint.Cap.ROUND }
    private val puck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0A") }
    private val you = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDE047"); style = Paint.Style.STROKE; strokeWidth = dp(3f) }
    private val youGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 253, 224, 71) }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = dp(26f)

        // Ice sheet with boards and a few markings.
        rect.set(dp(2f), dp(2f), w - dp(2f), h - dp(2f))
        canvas.drawRoundRect(rect, corner, corner, ice)
        canvas.drawRoundRect(rect, corner, corner, boards)
        canvas.save()
        path.reset()
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawLine(w * 0.36f, 0f, w * 0.36f, h, blueLine)
        canvas.drawLine(w * 0.5f, 0f, w * 0.5f, h, redLine)
        canvas.drawCircle(w * 0.5f, h * 0.5f, dp(38f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F5FBF"); style = Paint.Style.STROKE; strokeWidth = dp(1.5f) })
        canvas.drawCircle(w * 0.15f, h * 0.2f, dp(30f), circle)
        canvas.restore()

        // Skater with the yellow "you" ring, carrying the puck.
        val sx = w * 0.56f
        val sy = h * 0.42f
        val r = dp(15f)
        canvas.drawCircle(sx, sy, r * 2f, youGlow)
        canvas.drawCircle(sx, sy, r * 2f, you)
        canvas.drawLine(sx + r * 0.2f, sy + r * 0.7f, sx + r * 2.6f, sy + r * 0.9f, stick)
        canvas.drawCircle(sx, sy, r, body)
        canvas.drawCircle(sx, sy, r, bodyEdge)
        canvas.drawLine(sx - r * 0.35f, sy - r * 0.75f, sx - r * 0.35f, sy + r * 0.75f, yoke)
        canvas.drawCircle(sx + r * 0.3f, sy, r * 0.5f, helmet)
        canvas.drawCircle(sx + r * 2.9f, sy + r * 0.9f, dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(sx + r * 2.9f, sy + r * 0.9f, dp(4f), puck)
        caption.textSize = dp(11f)
        canvas.drawText("YOU", sx, sy - r * 2.5f, caption)

        // Joystick.
        val jr = dp(40f)
        val jx = w * 0.19f
        val jy = h * 0.66f
        canvas.drawCircle(jx, jy, jr, base)
        canvas.drawCircle(jx, jy, jr, ring)
        val kx = jx + jr * 0.42f
        val ky = jy - jr * 0.3f
        canvas.drawCircle(kx, ky, jr * 0.42f, knob)
        canvas.drawCircle(kx, ky, jr * 0.42f, knobEdge)
        drawChevron(canvas, jx, jy - jr - dp(9f), 0f)
        drawChevron(canvas, jx, jy + jr + dp(9f), 180f)
        drawChevron(canvas, jx - jr - dp(9f), jy, 270f)
        drawChevron(canvas, jx + jr + dp(9f), jy, 90f)
        caption.textSize = dp(12f)
        canvas.drawText("DRAG TO SKATE", jx, jy + jr + dp(30f), caption)

        // Buttons.
        val big = dp(31f)
        val small = dp(26f)
        val bx = w - dp(50f)
        val by = h - dp(52f)
        drawButton(canvas, bx, by, big, "SHOOT", Color.parseColor("#DC2626"))
        drawButton(canvas, bx - dp(78f), by + dp(12f), small, "PASS", Color.parseColor("#2563EB"))
        drawButton(canvas, bx - dp(8f), by - dp(80f), small, "HIT", Color.parseColor("#D97706"))
    }

    private fun drawChevron(canvas: Canvas, x: Float, y: Float, rotation: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        path.reset()
        path.moveTo(0f, -dp(6f))
        path.lineTo(dp(6f), dp(3f))
        path.lineTo(-dp(6f), dp(3f))
        path.close()
        canvas.drawPath(path, arrow)
        canvas.restore()
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, r: Float, text: String, color: Int) {
        btn.color = color
        btn.alpha = 210
        canvas.drawCircle(x, y, r, btn)
        canvas.drawCircle(x, y, r, btnEdge)
        label.textSize = r * 0.42f
        canvas.drawText(text, x, y + r * 0.15f, label)
    }
}
