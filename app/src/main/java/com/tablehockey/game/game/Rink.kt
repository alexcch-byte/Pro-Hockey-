package com.tablehockey.game.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Regulation-sized rink geometry in feet. World origin is centre ice,
 * +x runs along the length of the rink, +y runs "down" the screen
 * (same handedness as the Android canvas so nothing needs flipping).
 */
object Rink {
    const val LENGTH = 200f
    const val WIDTH = 85f
    const val HALF_L = 100f
    const val HALF_W = 42.5f
    const val CORNER_R = 28f

    const val GOAL_LINE_X = 89f       // distance from centre to each goal line
    const val BLUE_LINE_X = 25f
    const val GOAL_HALF_W = 3f        // posts at y = +-3
    const val NET_DEPTH = 4f          // net runs from 89 to 93
    const val NET_HALF_W = 3.4f       // outside of the net frame
    const val CREASE_R = 6f
    const val FACEOFF_R = 15f
    const val END_DOT_X = 69f
    const val NEUTRAL_DOT_X = 20f
    const val DOT_Y = 22f
    const val POST_R = 0.35f

    const val PUCK_R = 0.5f           // physical puck radius (exaggerated for feel)

    /** How far outside the boards the drawable world extends (stands, glass). */
    const val WORLD_MARGIN = 14f

    /**
     * Pushes a circle of radius [r] at (pos[0], pos[1]) back inside the
     * rounded-rectangle boards. Returns true when a wall was touched, in which
     * case [normal] receives the inward-facing unit normal of that wall.
     */
    fun containCircle(pos: FloatArray, r: Float, normal: FloatArray): Boolean {
        var x = pos[0]
        var y = pos[1]
        val ax = abs(x)
        val ay = abs(y)
        val cx = HALF_L - CORNER_R
        val cy = HALF_W - CORNER_R
        var hit = false
        if (ax > cx && ay > cy) {
            val dx = ax - cx
            val dy = ay - cy
            val d = hypot(dx, dy)
            val maxD = CORNER_R - r
            if (d > maxD && d > 0.0001f) {
                val nx = dx / d
                val ny = dy / d
                x = sign(x).orOne() * (cx + nx * maxD)
                y = sign(y).orOne() * (cy + ny * maxD)
                normal[0] = -nx * sign(x).orOne()
                normal[1] = -ny * sign(y).orOne()
                hit = true
            }
        } else {
            normal[0] = 0f
            normal[1] = 0f
            if (ax > HALF_L - r) {
                val s = sign(x).orOne()
                x = s * (HALF_L - r)
                normal[0] = -s
                hit = true
            }
            if (ay > HALF_W - r) {
                val s = sign(y).orOne()
                y = s * (HALF_W - r)
                normal[1] = -s
                hit = true
            }
            if (hit) {
                val len = hypot(normal[0], normal[1])
                if (len > 0f) { normal[0] /= len; normal[1] /= len }
            }
        }
        pos[0] = x
        pos[1] = y
        return hit
    }

    private fun Float.orOne(): Float = if (this == 0f) 1f else this

    /** True when the point is inside the playing surface (ignoring radius). */
    fun isInside(x: Float, y: Float, margin: Float = 0f): Boolean {
        val p = floatArrayOf(x, y)
        val n = FloatArray(2)
        val hit = containCircle(p, margin, n)
        return !hit
    }
}
