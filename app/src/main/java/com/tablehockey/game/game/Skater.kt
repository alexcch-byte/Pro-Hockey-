package com.tablehockey.game.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class Role(val label: String) { C("C"), LW("LW"), RW("RW"), LD("LD"), RD("RD"), G("G") }

/**
 * One player on the ice - five skaters and a goalie per team. Pure state;
 * all behaviour lives in [Simulation] / [AIController] and drawing in [Renderer].
 */
class Skater(val team: Int, val index: Int, val role: Role, val number: Int) {
    val isGoalie: Boolean get() = role == Role.G

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    /** Facing direction in radians (world frame). */
    var facing = 0f

    val radius: Float = if (role == Role.G) 2.1f else 1.5f

    /** > 0 while knocked down after a body check. */
    var stunTimer = 0f
    /** > 0 while the stick is extended for a poke check. */
    var pokeTimer = 0f
    /** > 0 while lunging into a body check. */
    var checkTimer = 0f
    /** Generic cooldown between poke/hit attempts. */
    var actionCooldown = 0f
    /** Can't re-collect the puck until this expires (after shooting / passing). */
    var pickupCooldown = 0f
    /** Shot / pass animation. */
    var swingTimer = 0f
    /** Advances with distance skated; drives the stride animation. */
    var stride = 0f

    // AI scratch state
    var aiTimer = 0f
    var aiTargetX = 0f
    var aiTargetY = 0f
    var aiChaser = false

    // Goalie-only
    var butterfly = false
    /** Multiplier on the goalie's blocking area (difficulty). */
    var padScale = 1f

    // Network interpolation targets (client only)
    var netX = 0f
    var netY = 0f
    var netFacing = 0f

    val speed: Float get() = hypot(vx, vy)

    val stickReach: Float get() = radius + if (pokeTimer > 0f) POKE_REACH else STICK_REACH

    fun bladeX(): Float = x + cos(facing) * stickReach
    fun bladeY(): Float = y + sin(facing) * stickReach

    fun distanceTo(px: Float, py: Float): Float = hypot(px - x, py - y)

    fun place(px: Float, py: Float, face: Float) {
        x = px; y = py; vx = 0f; vy = 0f
        facing = face
        stunTimer = 0f; pokeTimer = 0f; checkTimer = 0f
        actionCooldown = 0f; pickupCooldown = 0f; swingTimer = 0f
        aiTimer = 0f; aiTargetX = px; aiTargetY = py; aiChaser = false
        butterfly = false
        netX = px; netY = py; netFacing = face
    }

    companion object {
        const val STICK_REACH = 2.6f
        const val POKE_REACH = 4.6f
        const val MAX_SPEED = 25f      // ft/s
        const val GOALIE_SPEED = 20f
    }
}
