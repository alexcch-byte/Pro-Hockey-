package com.tablehockey.game.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drives every skater that a human isn't controlling, plus both goalies.
 * Positional logic is written in each team's "attack frame" (+ax toward the
 * net being attacked) so the same code serves both ends of the rink.
 */
class AIController(private val ai: AiSettings, private val rng: Random) {

    private enum class Situation { OWN, OPP, LOOSE }

    // ---------------------------------------------------------------- skaters

    fun updateSkater(world: World, team: Team, s: Skater, speedMul: Float, dt: Float, sim: Simulation) {
        s.aiTimer -= dt
        if (s.aiTimer <= 0f) {
            s.aiTimer = ai.reaction * (0.7f + rng.nextFloat() * 0.6f)
            think(world, team, s, sim)
        }
        val dx = s.aiTargetX - s.x
        val dy = s.aiTargetY - s.y
        val d = hypot(dx, dy)
        val maxSpeed = Skater.MAX_SPEED * speedMul
        if (d < 0.6f) {
            PhysicsEngine.moveSkater(s, 0f, 0f, maxSpeed, dt)
        } else {
            val arrive = min(1f, d / 7f + 0.25f)
            val sp = maxSpeed * arrive
            PhysicsEngine.moveSkater(s, dx / d * sp, dy / d * sp, maxSpeed, dt)
        }
    }

    private fun think(world: World, team: Team, s: Skater, sim: Simulation) {
        val puck = world.puck
        val carrier = puck.carrier
        val situation = when {
            carrier == null -> Situation.LOOSE
            carrier.team == team.id -> Situation.OWN
            else -> Situation.OPP
        }
        val humanIdx = world.controlled[team.id]

        when (situation) {
            Situation.OWN -> {
                if (carrier === s) {
                    carrierBehaviour(world, team, s, sim)
                } else {
                    val ax = team.toAttackX(puck.x)
                    val (tx, ty) = offensiveSpot(s.role, ax, puck.y)
                    setTarget(team, s, tx, ty)
                }
            }
            Situation.OPP -> {
                val c = carrier!!
                val chaser = pickChaser(team, c.x + c.vx * 0.2f, c.y + c.vy * 0.2f, humanIdx)
                if (chaser === s) {
                    s.aiChaser = true
                    setWorldTarget(s, c.x + c.vx * 0.25f, c.y + c.vy * 0.25f)
                    val d = s.distanceTo(c.x, c.y)
                    if (d < 6.5f && s.actionCooldown <= 0f) {
                        val roll = rng.nextFloat()
                        if (roll < ai.hitChance) sim.startHit(s, c)
                        else if (roll < ai.hitChance + ai.pokeChance) sim.startPoke(s)
                    }
                } else {
                    s.aiChaser = false
                    val cax = team.toAttackX(c.x)
                    val (tx, ty) = defensiveSpot(s.role, cax, c.y)
                    setTarget(team, s, tx, ty)
                }
            }
            Situation.LOOSE -> {
                if (puck.passTarget === s && team.toAttackX(s.x) > -10f && rng.nextFloat() < ai.shootTendency * 0.75f) {
                    s.oneTimerArmed = true
                }
                val px = (puck.x + puck.vx * 0.35f).coerceIn(-97f, 97f)
                val py = (puck.y + puck.vy * 0.35f).coerceIn(-40f, 40f)
                val chasers = pickChasers(team, px, py, humanIdx, 2)
                if (chasers.contains(s)) {
                    s.aiChaser = true
                    setWorldTarget(s, px, py)
                } else {
                    s.aiChaser = false
                    val (tx, ty) = offensiveSpot(s.role, team.toAttackX(px), py)
                    setTarget(team, s, tx, ty)
                }
            }
        }
        // Wingers stay clear of the net's frame.
        if (abs(s.aiTargetX) > Rink.GOAL_LINE_X - 1.5f && abs(s.aiTargetY) < 5f) {
            s.aiTargetY = if (s.aiTargetY < 0f) -6f else 6f
        }
        s.aiTargetX = s.aiTargetX.coerceIn(-96f, 96f)
        s.aiTargetY = s.aiTargetY.coerceIn(-39f, 39f)
    }

    private fun setTarget(team: Team, s: Skater, ax: Float, ay: Float) {
        s.aiTargetX = team.toWorldX(ax)
        s.aiTargetY = ay
    }

    private fun setWorldTarget(s: Skater, x: Float, y: Float) {
        s.aiTargetX = x
        s.aiTargetY = y
    }

    private fun pickChaser(team: Team, x: Float, y: Float, humanIdx: Int): Skater? {
        var best: Skater? = null
        var bestD = Float.MAX_VALUE
        for (s in team.skaters) {
            if (s.isGoalie || s.index == humanIdx || s.stunTimer > 0f) continue
            val d = s.distanceTo(x, y) - (if (s.aiChaser) 3.5f else 0f)
            if (d < bestD) { bestD = d; best = s }
        }
        return best
    }

    private fun pickChasers(team: Team, x: Float, y: Float, humanIdx: Int, count: Int): List<Skater> {
        return team.skaters
            .filter { !it.isGoalie && it.index != humanIdx && it.stunTimer <= 0f }
            .sortedBy { it.distanceTo(x, y) - (if (it.aiChaser) 3.5f else 0f) }
            .take(count)
    }

    /** Attack-frame spot for a supporting skater while the team has (or is chasing) the puck. */
    private fun offensiveSpot(role: Role, pax: Float, pay: Float): Pair<Float, Float> {
        val inZone = pax > Rink.BLUE_LINE_X
        return when (role) {
            Role.C -> if (inZone) (56f to -pay * 0.4f) else ((pax + 6f) to pay * 0.3f)
            Role.LW -> if (inZone) (74f to -13f) else (min(pax + 12f, 23f) to -24f)
            Role.RW -> if (inZone) (74f to 13f) else (min(pax + 12f, 23f) to 24f)
            Role.LD -> if (inZone) (36f to -18f) else ((pax - 20f) to -14f)
            Role.RD -> if (inZone) (36f to 18f) else ((pax - 20f) to 14f)
            Role.G -> 0f to 0f
        }
    }

    /** Attack-frame spot for a defender while the opponent carries the puck at (cax, cay). */
    private fun defensiveSpot(role: Role, cax: Float, cay: Float): Pair<Float, Float> {
        val behindNet = cax < -Rink.GOAL_LINE_X
        return when (role) {
            Role.C -> if (behindNet) (-70f to cay * 0.5f) else ((cax - 8f).coerceAtLeast(-80f) to cay * 0.6f)
            Role.LW -> min(cax - 4f, 15f) to -18f
            Role.RW -> min(cax - 4f, 15f) to 18f
            Role.LD -> if (behindNet) (-80f to -6f) else ((cax - 16f).coerceIn(-82f, 5f) to (-9f + cay * 0.25f))
            Role.RD -> if (behindNet) (-80f to 6f) else ((cax - 16f).coerceIn(-82f, 5f) to (9f + cay * 0.25f))
            Role.G -> 0f to 0f
        }
    }

    private fun carrierBehaviour(world: World, team: Team, s: Skater, sim: Simulation) {
        val sax = team.toAttackX(s.x)
        val say = s.y
        val goalDx = Rink.GOAL_LINE_X - sax
        val dist = hypot(goalDx, say)
        val opp = world.opponent(team.id)

        // Nearest opponent in front of us.
        var pressure: Skater? = null
        var pressureD = 7f
        val fx = cos(s.facing) * team.attackDir
        val fy = sin(s.facing)
        for (o in opp.skaters) {
            if (o.isGoalie) continue
            val d = s.distanceTo(o.x, o.y)
            if (d < pressureD) {
                val ox = team.toAttackX(o.x) - sax
                val oy = o.y - say
                if (ox * fx + oy * fy > -1f) { pressureD = d; pressure = o }
            }
        }

        // Shooting.
        val angleQuality = 1f - (abs(say) / 40f).coerceIn(0f, 1f)
        if (dist < 44f && sax < Rink.GOAL_LINE_X - 2f) {
            val closeness = 1f - dist / 44f
            val p = ai.shootTendency * (0.2f + 0.8f * closeness) * (0.45f + 0.55f * angleQuality)
            if (rng.nextFloat() < p) {
                val corner = (1.4f + rng.nextFloat() * 1.2f) * (if (rng.nextBoolean()) 1f else -1f)
                sim.shoot(s, 0.55f + rng.nextFloat() * 0.45f, corner, ai.shotAccuracy)
                return
            }
        }

        // Passing when pressured, or occasionally to move the puck up ice.
        val wantPass = (pressure != null && rng.nextFloat() < 0.75f) || (dist > 55f && rng.nextFloat() < 0.15f)
        if (wantPass && sim.pass(s, 0f, 0f, ai.passAccuracy, human = false, checkLane = true)) return

        // Skate toward the slot, steering around pressure.
        var tx = 78f
        var ty = say * 0.35f
        if (pressure != null) {
            val side = if (pressure.y > say) -1f else 1f
            tx = sax + 12f
            ty = say + side * 11f
        }
        if (sax > 76f && abs(say) > 7f) {
            // Too deep with a bad angle: curl back toward the slot.
            tx = 62f
            ty = say * 0.4f
        }
        setTarget(team, s, tx, ty)
    }

    // ---------------------------------------------------------------- goalies

    fun updateGoalie(world: World, team: Team, g: Skater, skill: Float, padScale: Float, dt: Float) {
        g.padScale = padScale
        val puck = world.puck
        val a = team.attackDir
        val gx = team.ownGoalX
        val depthOfPuck = a * (puck.x - gx)
        val tx: Float
        val ty: Float
        if (depthOfPuck < 0f) {
            // Puck behind the goal line: hug the near post.
            tx = gx + a * 2.6f
            ty = if (abs(puck.y) < 0.6f) 0f else sign(puck.y) * 2.4f
        } else {
            val dx = puck.x - gx
            val dy = puck.y
            val d = hypot(dx, dy).coerceAtLeast(0.01f)
            var depth = 2.6f + (d / 35f).coerceIn(0f, 1f) * 1.7f
            depth *= 0.8f + 0.2f * skill
            var px = gx + dx / d * depth
            var py = dy / d * depth
            py = py.coerceIn(-3.2f, 3.2f)
            val dep = (a * (px - gx)).coerceIn(2.5f, 5.5f)
            px = gx + a * dep
            tx = px
            ty = py
        }
        val maxSpeed = Skater.GOALIE_SPEED * skill
        val dx = tx - g.x
        val dy = ty - g.y
        val d = hypot(dx, dy)
        if (d < 0.15f) {
            PhysicsEngine.moveSkater(g, 0f, 0f, maxSpeed, dt)
        } else {
            val sp = min(maxSpeed, d * 9f)
            PhysicsEngine.moveSkater(g, dx / d * sp, dy / d * sp, maxSpeed, dt)
        }
        // Goalies square up to the puck rather than facing where they skate.
        val want = atan2(puck.y - g.y, puck.x - g.x)
        g.facing = PhysicsEngine.turnToward(g.facing, want, dt * 12f)
        val pd = hypot(puck.x - g.x, puck.y - g.y)
        val carrier = puck.carrier
        g.butterfly = pd < 18f && (puck.shot || puck.speed > 30f || (carrier != null && carrier.team != team.id && pd < 12f))
    }
}
