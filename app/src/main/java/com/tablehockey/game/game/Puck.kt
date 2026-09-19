package com.tablehockey.game.game

import kotlin.math.hypot

class Puck {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f

    /** Skater currently carrying the puck on their stick, or null when loose. */
    var carrier: Skater? = null

    /** True while the puck is travelling as a shot (until it is touched again). */
    var shot = false
    var shooter: Skater? = null
    var passTarget: Skater? = null
    var lastTouchTeam = -1
    /** True while a shot is slipping through the goalie (difficulty leak); cleared once clear of the pads. */
    var leaking = false

    // Network interpolation targets (client only)
    var netX = 0f
    var netY = 0f

    val speed: Float get() = hypot(vx, vy)

    fun reset(px: Float, py: Float) {
        x = px; y = py; vx = 0f; vy = 0f
        carrier = null
        shot = false
        shooter = null
        passTarget = null
        leaking = false
        netX = px; netY = py
    }
}
