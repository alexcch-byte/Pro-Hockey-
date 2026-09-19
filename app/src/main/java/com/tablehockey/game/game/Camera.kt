package com.tablehockey.game.game

import android.graphics.Canvas
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Broadcast-style follow camera. Zoom is fixed so the full rink width plus
 * the boards is visible; the view pans to follow the puck along the ice.
 */
class Camera {
    var x = 0f
    var y = 0f
    var scale = 10f
    var screenW = 1
    var screenH = 1

    companion object {
        const val VISIBLE_HEIGHT_FT = 84f
        val WORLD_HALF_W = Rink.HALF_L + Rink.WORLD_MARGIN
        val WORLD_HALF_H = Rink.HALF_W + Rink.WORLD_MARGIN
    }

    fun resize(w: Int, h: Int) {
        screenW = max(1, w)
        screenH = max(1, h)
        scale = screenH / VISIBLE_HEIGHT_FT
        if (screenW / scale > WORLD_HALF_W * 2f) scale = screenW / (WORLD_HALF_W * 2f)
    }

    fun snapTo(tx: Float, ty: Float) {
        x = clampX(tx)
        y = clampY(ty)
    }

    fun follow(tx: Float, ty: Float, dt: Float) {
        val k = 1f - exp(-dt * 4.5f)
        x += (clampX(tx) - x) * k
        y += (clampY(ty) - y) * k
    }

    private fun clampX(v: Float): Float {
        val halfVis = screenW / scale / 2f
        val lim = WORLD_HALF_W - halfVis
        return if (lim <= 0f) 0f else v.coerceIn(-lim, lim)
    }

    private fun clampY(v: Float): Float {
        val halfVis = screenH / scale / 2f
        val lim = WORLD_HALF_H - halfVis
        return if (lim <= 0f) 0f else v.coerceIn(-lim, lim)
    }

    fun apply(canvas: Canvas) {
        canvas.translate(screenW / 2f, screenH / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-x, -y)
    }

    fun toScreenX(wx: Float) = (wx - x) * scale + screenW / 2f
    fun toScreenY(wy: Float) = (wy - y) * scale + screenH / 2f

    fun visibleLeft() = x - screenW / scale / 2f
    fun visibleRight() = x + screenW / scale / 2f
    fun visibleTop() = y - screenH / scale / 2f
    fun visibleBottom() = y + screenH / scale / 2f

    fun minScale() = min(screenW / (WORLD_HALF_W * 2f), screenH / (WORLD_HALF_H * 2f))
}
