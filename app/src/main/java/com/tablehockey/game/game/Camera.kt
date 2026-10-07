package com.tablehockey.game.game

import kotlin.math.exp
import kotlin.math.max

/**
 * Broadcast-style follow camera with a real perspective projection.
 *
 * The rink is seen from behind the near side boards, tilted down. A world point
 * (wx, wy) on the ice (feet) projects to the screen as a homography:
 *
 *   f  = 1 / (1 - persp * (wy - y))            depth factor, < 1 far away, > 1 near
 *   sx = screenW/2 + (wx - x) * scale * f
 *   sy = anchorY   + (wy - y) * scale * vk * f
 *
 * so the side boards converge toward the far side and near players are drawn bigger
 * than far ones. [ppf] gives the screen pixels per foot at a depth, used to size
 * upright sprites; [vk] is the extra foreshortening of the ice plane (ground
 * circles become ellipses with ry = rx * vk).
 */
class Camera {
    var x = 0f
    var y = 0f
    /** Screen pixels per foot (horizontal) at the focus depth. */
    var scale = 10f
    var screenW = 1
    var screenH = 1
    /** Screen y of the focus point. */
    var anchorY = 0f

    companion object {
        /** Feet of rink length visible across the screen at the focus depth. */
        const val VISIBLE_WIDTH_FT = 88f
        /** Vertical foreshortening of the ice plane. */
        const val VK = 0.80f
        /** Perspective strength per foot of depth. */
        const val PERSP = 0.011f
        val WORLD_HALF_W = Rink.HALF_L + Rink.WORLD_MARGIN
        val WORLD_HALF_H = Rink.HALF_W + Rink.WORLD_MARGIN
    }

    val vk = VK

    fun resize(w: Int, h: Int) {
        screenW = max(1, w)
        screenH = max(1, h)
        scale = screenW / VISIBLE_WIDTH_FT
        anchorY = screenH * 0.58f
    }

    var shakeTrauma = 0f
    private var shakeTime = 0f
    private var shakeX = 0f
    private var shakeY = 0f

    fun snapTo(tx: Float, ty: Float) {
        x = clampX(tx)
        y = clampY(ty)
    }

    fun addShake(amount: Float) {
        shakeTrauma = (shakeTrauma + amount).coerceIn(0f, 1f)
    }

    fun follow(tx: Float, ty: Float, dt: Float) {
        val k = 1f - exp(-dt * 6f)
        x += (clampX(tx) - x) * k
        y += (clampY(ty) - y) * k

        if (shakeTrauma > 0f) {
            shakeTime += dt
            shakeTrauma = max(0f, shakeTrauma - dt * 2.2f)
        }
    }

    /** Computes this frame's shake offset; call once per frame before projecting. */
    fun beginFrame() {
        if (shakeTrauma > 0.01f) {
            val mag = shakeTrauma * shakeTrauma * (screenH * 0.035f)
            shakeX = kotlin.math.cos(shakeTime * 48f) * mag
            shakeY = kotlin.math.sin(shakeTime * 42f) * mag
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }

    private fun clampX(v: Float): Float {
        val lim = Rink.HALF_L - 40f
        return v.coerceIn(-lim, lim)
    }

    /** Keeps the near boards from rising above the bottom of the screen. */
    private fun clampY(v: Float): Float {
        val k = (screenH - anchorY) / (scale * VK)
        val nearRel = k / (1f + PERSP * k)
        val lim = Rink.HALF_W - nearRel
        return if (lim <= 0f) 0f else v.coerceIn(-lim, lim)
    }

    /** Depth factor for a world y: 1 at the focus depth, smaller farther away. */
    fun depth(wy: Float): Float = 1f / (1f - PERSP * (wy - y)).coerceAtLeast(0.25f)

    /** Screen pixels per foot (horizontal) for things standing at world y. */
    fun ppf(wy: Float): Float = scale * depth(wy)

    fun px(wx: Float, wy: Float): Float = screenW / 2f + shakeX + (wx - x) * scale * depth(wy)
    fun py(wy: Float): Float = anchorY + shakeY + (wy - y) * scale * VK * depth(wy)

    fun toScreenX(wx: Float, wy: Float) = px(wx, wy)
    fun toScreenY(wy: Float) = py(wy)
}
