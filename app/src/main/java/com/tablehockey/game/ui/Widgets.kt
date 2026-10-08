package com.tablehockey.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/** Wraps children onto new rows when the row is full. Children should be WRAP_CONTENT. */
class FlowLayout @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : ViewGroup(ctx, attrs) {

    private fun flow(maxW: Int, doLayout: Boolean): Int {
        var x = paddingLeft
        var y = paddingTop
        var rowH = 0
        val limit = paddingLeft + maxW
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val lp = c.layoutParams as MarginLayoutParams
            val cw = c.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = c.measuredHeight + lp.topMargin + lp.bottomMargin
            if (x + cw > limit && x > paddingLeft) {
                x = paddingLeft
                y += rowH
                rowH = 0
            }
            if (doLayout) {
                val l = x + lp.leftMargin
                val t = y + lp.topMargin
                c.layout(l, t, l + c.measuredWidth, t + c.measuredHeight)
            }
            x += cw
            if (ch > rowH) rowH = ch
        }
        return y + rowH + paddingBottom
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        }
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), flow(maxW, false))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        flow(r - l - paddingLeft - paddingRight, true)
    }

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}

/**
 * Square selectable tile. [painter] draws the content given (cx, cy, radius) in view pixels;
 * the tile draws its own rounded background and selection ring.
 */
class ChipView(
    ctx: Context,
    private val sizeDp: Int,
    private val caption: String? = null,
    private val painter: (Canvas, Float, Float, Float) -> Unit
) : View(ctx) {
    var chosen = false
        set(v) { field = v; invalidate() }
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF33475B.toInt()
        textSize = 10f * d
        letterSpacing = 0.06f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val box = RectF()
    private val captionText = caption?.uppercase()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = (sizeDp * d).toInt()
        val tw = captionText?.let { (label.measureText(it) + 4 * d).toInt() } ?: 0
        setMeasuredDimension(maxOf(s, tw), s + if (captionText != null) (16 * d).toInt() else 0)
    }

    override fun onDraw(canvas: Canvas) {
        val tile = sizeDp * d
        val ox = (width - tile) / 2f
        val inset = 1.5f * d
        box.set(ox + inset, inset, ox + tile - inset, tile - inset)
        bg.color = if (chosen) 0xFFFFF3A8.toInt() else 0xFFC9D1DB.toInt()
        canvas.drawRoundRect(box, 9 * d, 9 * d, bg)
        painter(canvas, ox + tile / 2f, tile / 2f, tile * 0.34f)
        ring.strokeWidth = if (chosen) 3.5f * d else 1f * d
        ring.color = if (chosen) 0xFFE0197D.toInt() else 0x33000000
        canvas.drawRoundRect(box, 9 * d, 9 * d, ring)
        captionText?.let { canvas.drawText(it, width / 2f, tile + 11.5f * d, label) }
    }
}
