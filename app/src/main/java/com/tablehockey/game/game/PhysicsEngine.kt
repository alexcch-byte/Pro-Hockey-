package com.tablehockey.game.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Arcade physics tuned for feel rather than realism: skater acceleration,
 * body collisions, puck glide, boards, posts and the nets.
 */
object PhysicsEngine {

    private const val PUCK_FRICTION = 0.32f     // fraction of speed lost per second
    private const val BOARD_RESTITUTION = 0.62f
    private const val POST_RESTITUTION = 0.8f
    private const val MAX_PUCK_SPEED = 130f

    private val tmpPos = FloatArray(2)
    private val tmpNormal = FloatArray(2)

    /**
     * Accelerates [s] toward the desired velocity and integrates. Facing turns
     * toward the direction of travel while moving.
     */
    fun moveSkater(s: Skater, desiredVx: Float, desiredVy: Float, maxSpeed: Float, dt: Float) {
        if (abs(s.y) > 100f) return
        if (s.stunTimer > 0f) {
            val f = exp(-dt * 5f)
            s.vx *= f; s.vy *= f
        } else {
            val responsiveness = if (s.checkTimer > 0f) 2f else 6f
            val k = 1f - exp(-responsiveness * dt)
            s.vx += (desiredVx - s.vx) * k
            s.vy += (desiredVy - s.vy) * k
            val cap = maxSpeed * (if (s.checkTimer > 0f) 1.35f else 1f)
            val sp = s.speed
            if (sp > cap) { s.vx *= cap / sp; s.vy *= cap / sp }
        }
        s.x += s.vx * dt
        s.y += s.vy * dt

        val sp = s.speed
        if (sp > 1.2f && s.stunTimer <= 0f) {
            val want = atan2(s.vy, s.vx)
            s.facing = turnToward(s.facing, want, dt * (if (s.isGoalie) 14f else 10f))
            s.stride += sp * dt
        }
    }

    fun turnToward(current: Float, target: Float, maxStep: Float): Float {
        var diff = target - current
        while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
        while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
        val step = diff.coerceIn(-maxStep, maxStep)
        var out = current + step
        while (out > Math.PI) out -= (2 * Math.PI).toFloat()
        while (out < -Math.PI) out += (2 * Math.PI).toFloat()
        return out
    }

    /** Keeps skaters inside the boards and out of the nets. */
    fun constrainSkater(s: Skater) {
        if (s.inPenaltyBox || abs(s.y) > 100f) return
        tmpPos[0] = s.x; tmpPos[1] = s.y
        if (Rink.containCircle(tmpPos, s.radius, tmpNormal)) {
            s.x = tmpPos[0]; s.y = tmpPos[1]
            val dot = s.vx * tmpNormal[0] + s.vy * tmpNormal[1]
            if (dot < 0f) {
                s.vx -= dot * tmpNormal[0]
                s.vy -= dot * tmpNormal[1]
            }
        }
        if (!s.isGoalie) {
            for (e in intArrayOf(-1, 1)) {
                val left = Rink.GOAL_LINE_X - 0.3f
                val right = Rink.GOAL_LINE_X + Rink.NET_DEPTH + 0.3f
                val ax = s.x * e
                val halfW = Rink.NET_HALF_W + 0.2f
                if (ax + s.radius > left && ax - s.radius < right && abs(s.y) < halfW + s.radius) {
                    // Push out along the axis of least penetration.
                    val penLeft = (ax + s.radius) - left
                    val penRight = right - (ax - s.radius)
                    val penTop = (halfW + s.radius) - s.y
                    val penBot = (halfW + s.radius) + s.y
                    val m = minOf(penLeft, penRight, penTop, penBot)
                    when (m) {
                        penLeft -> { s.x = (left - s.radius) * e; if (s.vx * e > 0f) s.vx = 0f }
                        penRight -> { s.x = (right + s.radius) * e; if (s.vx * e < 0f) s.vx = 0f }
                        penTop -> { s.y = halfW + s.radius; if (s.vy < 0f) s.vy = 0f }
                        penBot -> { s.y = -(halfW + s.radius); if (s.vy > 0f) s.vy = 0f }
                    }
                }
            }
        }
    }

    /**
     * Separates overlapping skaters and reports each contact with its closing
     * speed so the simulation can turn hard contacts into body checks.
     */
    fun resolveSkaterCollisions(skaters: List<Skater>, onContact: (Skater, Skater, Float) -> Unit) {
        val n = skaters.size
        for (i in 0 until n) {
            val a = skaters[i]
            if (a.inPenaltyBox || abs(a.y) > 100f) continue
            for (j in i + 1 until n) {
                val b = skaters[j]
                if (b.inPenaltyBox || abs(b.y) > 100f) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val minDist = a.radius + b.radius
                val distSq = dx * dx + dy * dy
                if (distSq >= minDist * minDist) continue
                val dist = sqrt(distSq)
                val nx: Float
                val ny: Float
                if (dist > 0.0001f) { nx = dx / dist; ny = dy / dist } else { nx = 1f; ny = 0f }
                val overlap = minDist - dist
                val wa = if (a.isGoalie) 0.15f else 0.5f
                val wb = if (b.isGoalie) 0.15f else 0.5f
                val total = wa + wb
                a.x -= nx * overlap * (wa / total)
                a.y -= ny * overlap * (wa / total)
                b.x += nx * overlap * (wb / total)
                b.y += ny * overlap * (wb / total)

                val relVx = a.vx - b.vx
                val relVy = a.vy - b.vy
                val closing = relVx * nx + relVy * ny
                if (closing > 0f) {
                    // Soft inelastic bump so players do not glue together.
                    val impulse = closing * 0.55f
                    a.vx -= impulse * nx * (wb / total) * 2f
                    a.vy -= impulse * ny * (wb / total) * 2f
                    b.vx += impulse * nx * (wa / total) * 2f
                    b.vy += impulse * ny * (wa / total) * 2f
                    onContact(a, b, closing)
                }
            }
        }
    }

    /**
     * Integrates a loose puck. Returns +1 / -1 when it crossed the goal line
     * from the front, between the posts, at that end of the rink; else 0.
     * Uses the pre-step position for a swept test so a fast puck can never
     * tunnel through the netting and register from behind or from the side.
     * Emits BOARDS / POST events.
     */
    fun updatePuck(puck: Puck, dt: Float, events: MutableList<GameEvent>): Int {
        val prevX = puck.x
        val prevY = puck.y
        puck.x += puck.vx * dt
        puck.y += puck.vy * dt

        val f = Math.pow((1f - PUCK_FRICTION).toDouble(), dt.toDouble()).toFloat()
        puck.vx *= f
        puck.vy *= f
        val sp = puck.speed
        if (sp > MAX_PUCK_SPEED) { puck.vx *= MAX_PUCK_SPEED / sp; puck.vy *= MAX_PUCK_SPEED / sp }

        val r = Rink.PUCK_R
        val backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        for (e in intArrayOf(-1, 1)) {
            val gx = e * Rink.GOAL_LINE_X
            // Posts.
            for (py in floatArrayOf(-Rink.GOAL_HALF_W, Rink.GOAL_HALF_W)) {
                val dx = puck.x - gx
                val dy = puck.y - py
                val d = hypot(dx, dy)
                val minD = Rink.POST_R + r
                if (d < minD && d > 0.0001f) {
                    val nx = dx / d; val ny = dy / d
                    puck.x = gx + nx * minD
                    puck.y = py + ny * minD
                    val dot = puck.vx * nx + puck.vy * ny
                    if (dot < 0f) {
                        puck.vx -= (1f + POST_RESTITUTION) * dot * nx
                        puck.vy -= (1f + POST_RESTITUTION) * dot * ny
                        events.add(GameEvent.POST)
                    }
                }
            }

            val pax = prevX * e
            val nax = puck.x * e

            // A goal is only a goal when the puck crosses the goal line from
            // the front, and the crossing point lies between the posts.
            if (pax <= Rink.GOAL_LINE_X && nax > Rink.GOAL_LINE_X) {
                val t = ((Rink.GOAL_LINE_X - pax) / (nax - pax)).coerceIn(0f, 1f)
                val yCross = prevY + (puck.y - prevY) * t
                if (abs(yCross) < Rink.GOAL_HALF_W - r * 0.3f) {
                    return e
                }
                if (abs(yCross) < Rink.NET_HALF_W + r) {
                    // Into the goal frame beside a post: stays in front.
                    puck.x = e * (Rink.GOAL_LINE_X - r)
                    puck.vx = -puck.vx * 0.4f
                    continue
                }
            }

            // Anything now inside the net box that did not enter legitimately
            // came through the side or back netting (or was carried in):
            // push it back out the way it came.
            if (nax > Rink.GOAL_LINE_X && nax < backX + r && abs(puck.y) < Rink.NET_HALF_W + r) {
                when {
                    pax >= backX -> {
                        puck.x = e * (backX + r)
                        if (puck.vx * e < 0f) puck.vx = -puck.vx * 0.3f
                    }
                    abs(prevY) >= Rink.NET_HALF_W + r * 0.5f -> {
                        val sy = if (prevY == 0f) 1f else sign(prevY)
                        puck.y = sy * (Rink.NET_HALF_W + r)
                        if (puck.vy * sy < 0f) puck.vy = -puck.vy * 0.3f
                    }
                    pax <= Rink.GOAL_LINE_X -> {
                        puck.x = e * (Rink.GOAL_LINE_X - r)
                        if (puck.vx * e > 0f) puck.vx = -puck.vx * 0.4f
                    }
                    else -> ejectFromNet(puck, e)
                }
            }
        }

        tmpPos[0] = puck.x; tmpPos[1] = puck.y
        if (Rink.containCircle(tmpPos, r, tmpNormal)) {
            puck.x = tmpPos[0]; puck.y = tmpPos[1]
            val dot = puck.vx * tmpNormal[0] + puck.vy * tmpNormal[1]
            if (dot < 0f) {
                puck.vx -= (1f + BOARD_RESTITUTION) * dot * tmpNormal[0]
                puck.vy -= (1f + BOARD_RESTITUTION) * dot * tmpNormal[1]
                // A little tangential scrub along the boards.
                puck.vx *= 0.92f
                puck.vy *= 0.92f
                if (abs(dot) > 12f) events.add(GameEvent.BOARDS)
            }
        }
        return 0
    }

    /** Moves a puck that is somehow inside the net box at end [e] out through the nearest wall. */
    private fun ejectFromNet(puck: Puck, e: Int) {
        val r = Rink.PUCK_R
        val ax = puck.x * e
        val backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        val penFront = ax - (Rink.GOAL_LINE_X - r)
        val penBack = (backX + r) - ax
        val penSide = (Rink.NET_HALF_W + r) - abs(puck.y)
        when {
            penSide <= penFront && penSide <= penBack -> {
                val sy = if (puck.y == 0f) 1f else sign(puck.y)
                puck.y = sy * (Rink.NET_HALF_W + r)
                puck.vy = abs(puck.vy) * sy * 0.5f
            }
            penBack <= penFront -> {
                puck.x = e * (backX + r)
                puck.vx = abs(puck.vx) * e * 0.5f
            }
            else -> {
                puck.x = e * (Rink.GOAL_LINE_X - r)
                puck.vx = -abs(puck.vx) * e * 0.5f
            }
        }
    }

    /**
     * Places a carried puck on the carrier's blade and matches its velocity.
     * The puck is kept out of both net boxes, so a skater behind or beside
     * the net cannot hold it inside the goal.
     */
    fun carryPuck(puck: Puck, s: Skater, dt: Float) {
        val tx = s.x + cos(s.facing) * (s.radius + 1.9f)
        val ty = s.y + sin(s.facing) * (s.radius + 1.9f)
        val k = 1f - exp(-dt * 22f)
        puck.x += (tx - puck.x) * k
        puck.y += (ty - puck.y) * k
        puck.vx = s.vx
        puck.vy = s.vy
        tmpPos[0] = puck.x; tmpPos[1] = puck.y
        if (Rink.containCircle(tmpPos, Rink.PUCK_R, tmpNormal)) {
            puck.x = tmpPos[0]; puck.y = tmpPos[1]
        }
        keepCarriedPuckOutOfNets(puck, s)
    }

    private fun keepCarriedPuckOutOfNets(puck: Puck, s: Skater) {
        val r = Rink.PUCK_R
        val backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        for (e in intArrayOf(-1, 1)) {
            val ax = puck.x * e
            if (ax <= Rink.GOAL_LINE_X - r || ax >= backX + r || abs(puck.y) >= Rink.NET_HALF_W + r) continue
            val sax = s.x * e
            when {
                sax >= backX -> puck.x = e * (backX + r)                       // carrier behind the net
                abs(s.y) >= Rink.NET_HALF_W -> {                                // carrier beside the net
                    val sy = if (s.y == 0f) 1f else sign(s.y)
                    puck.y = sy * (Rink.NET_HALF_W + r)
                }
                sax <= Rink.GOAL_LINE_X -> puck.x = e * (Rink.GOAL_LINE_X - r)  // carrier in front: must shoot it in
                else -> ejectFromNet(puck, e)
            }
        }
    }

    /**
     * Goalie capsule: a pad-wide segment perpendicular to the goalie's facing.
     * On contact the puck is pushed out and [out] receives the contact normal
     * (pointing from the goalie toward the puck).
     */
    fun goalieContact(g: Skater, puck: Puck, out: FloatArray): Boolean {
        val half = (if (g.butterfly) 2.9f else 1.9f) * g.padScale
        val thick = (if (g.butterfly) 1.4f else 1.7f) * g.padScale
        val lx = -sin(g.facing)
        val ly = cos(g.facing)
        val px = puck.x - g.x
        val py = puck.y - g.y
        val t = (px * lx + py * ly).coerceIn(-half, half)
        val cx = g.x + lx * t
        val cy = g.y + ly * t
        val dx = puck.x - cx
        val dy = puck.y - cy
        val d = hypot(dx, dy)
        val minD = thick + Rink.PUCK_R
        if (d >= minD) return false
        if (d > 0.0001f) { out[0] = dx / d; out[1] = dy / d } else { out[0] = cos(g.facing); out[1] = sin(g.facing) }
        puck.x = cx + out[0] * minD
        puck.y = cy + out[1] * minD
        return true
    }

    fun gaussian(rng: Random): Float {
        val u1 = rng.nextFloat().coerceAtLeast(1e-6f)
        val u2 = rng.nextFloat()
        return (sqrt(-2.0 * Math.log(u1.toDouble())) * Math.cos(2.0 * Math.PI * u2)).toFloat()
    }
}
