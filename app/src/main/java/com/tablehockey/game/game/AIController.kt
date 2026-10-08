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

    private class Pt { var x = 0f; var y = 0f }
    private val leadPuck = Pt()

    private enum class Situation { OWN, OPP, LOOSE }

    private companion object {
        const val MARK_GOALSIDE_FT = 3f
        const val NET_CRASH_FT = 20f
        const val HANG_ZONE_FT = 55f
    }

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
                    s.markId = -1
                    carrierBehaviour(world, team, s, sim)
                } else if (guardHanger(world, team, s, puck)) {
                    // One defenceman stays home on an opponent loitering near our net.
                } else {
                    val ax = team.toAttackX(puck.x)
                    val (tx, ty) = offensiveSpot(s.role, ax, puck.y, world.penaltyTeam == world.opponent(team.id).id)
                    setTarget(team, s, tx, ty)
                }
            }
            Situation.OPP -> {
                val c = carrier!!
                val chaser = pickChaser(team, c.x + c.vx * 0.2f, c.y + c.vy * 0.2f, humanIdx)
                if (chaser === s) {
                    s.aiChaser = true
                    s.markId = -1
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
                    // Penalty kill: a man down, stay in the zone formation (box) instead of chasing marks.
                    if (world.penaltyTeam == team.id || !assignMark(world, team, s, c)) {
                        s.markId = -1
                        val (tx, ty) = defensiveSpot(s.role, cax, c.y)
                        setTarget(team, s, tx, ty)
                    }
                }
            }
            Situation.LOOSE -> {
                if (puck.passTarget === s && team.toAttackX(s.x) > -10f && rng.nextFloat() < ai.shootTendency * 0.75f) {
                    s.oneTimerArmed = true
                }
                val px = (puck.x + puck.vx * 0.35f).coerceIn(-97f, 97f)
                val py = (puck.y + puck.vy * 0.35f).coerceIn(-40f, 40f)
                pickChasers(team, px, py, humanIdx, team.toAttackX(px) > Rink.GOAL_LINE_X - NET_CRASH_FT)
                if (s === chaserA || s === chaserB) {
                    s.aiChaser = true
                    s.markId = -1
                    setWorldTarget(s, px, py)
                } else {
                    s.aiChaser = false
                    if (!guardHanger(world, team, s, puck)) {
                        val (tx, ty) = offensiveSpot(s.role, team.toAttackX(px), py, world.penaltyTeam == world.opponent(team.id).id)
                        setTarget(team, s, tx, ty)
                    }
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

    private var chaserA: Skater? = null
    private var chaserB: Skater? = null

    /**
     * Fills [chaserA]/[chaserB] with the two best loose-puck chasers (no allocation).
     * Near the opposing net ([netCrash]) the first chaser is the nearest forward and
     * defencemen are penalised for the second, so one D stays back.
     */
    private fun pickChasers(team: Team, x: Float, y: Float, humanIdx: Int, netCrash: Boolean) {
        var a: Skater? = null
        var aD = Float.MAX_VALUE
        if (netCrash) {
            for (s in team.skaters) {
                if (s.isGoalie || s.role == Role.LD || s.role == Role.RD || s.index == humanIdx || s.stunTimer > 0f) continue
                val d = s.distanceTo(x, y) - (if (s.aiChaser) 3.5f else 0f)
                if (d < aD) { aD = d; a = s }
            }
        }
        var b: Skater? = null
        var bD = Float.MAX_VALUE
        val aFixed = a != null // net-crash forward already chosen as A
        for (s in team.skaters) {
            if (s === a && aFixed) continue
            if (s.isGoalie || s.index == humanIdx || s.stunTimer > 0f) continue
            var d = s.distanceTo(x, y) - (if (s.aiChaser) 3.5f else 0f)
            if (netCrash && (s.role == Role.LD || s.role == Role.RD)) d += 12f
            if (aFixed) {
                if (d < bD) { bD = d; b = s }
            } else if (a == null || d < aD) {
                b = a; bD = aD; a = s; aD = d
            } else if (d < bD) { bD = d; b = s }
        }
        chaserA = a
        chaserB = b
    }

    /**
     * Man-to-man: marks the nearest unmarked attacker inside our defensive zone and heads for
     * a point [MARK_GOALSIDE_FT] goal-side of him. Defencemen weight slot attackers first.
     * Returns false when there is nobody to cover (caller falls back to zone spots).
     */
    private fun assignMark(world: World, team: Team, s: Skater, carrier: Skater): Boolean {
        val opp = world.opponent(team.id)
        var best: Skater? = null
        var bestScore = Float.MAX_VALUE
        val isD = s.role == Role.LD || s.role == Role.RD
        for (o in opp.skaters) {
            if (o.isGoalie || o === carrier || o.inPenaltyBox || o.stunTimer > 0f) continue
            val oax = team.toAttackX(o.x)
            if (oax > -Rink.BLUE_LINE_X) continue // only attackers already in (or entering) our zone
            // Claimed by a teammate who is still covering him?
            var taken = false
            for (t in team.skaters) {
                if (t === s || t.isGoalie || t.aiChaser || t.inPenaltyBox || t.stunTimer > 0f) continue
                if (t.index == world.controlled[team.id] && world.isHuman(team.id)) continue
                if (t.markId == o.index) { taken = true; break }
            }
            if (taken) continue
            val inSlot = oax < -50f && abs(o.y) < 13f
            var score = s.distanceTo(o.x, o.y)
            if (inSlot) score += if (isD) -10f else 10f
            if (s.markId == o.index) score -= 6f // stickiness
            if (score < bestScore) { bestScore = score; best = o }
        }
        val m = best ?: return false
        s.markId = m.index
        val gx = team.ownGoalX
        val dx = gx - m.x
        val dy = -m.y
        val d = hypot(dx, dy).coerceAtLeast(0.01f)
        setWorldTarget(s, m.x + dx / d * MARK_GOALSIDE_FT, m.y + dy / d * MARK_GOALSIDE_FT)
        return true
    }

    /**
     * Anti goal-hanging: while we have (or are chasing) the puck, the defenceman nearest to an
     * opposing skater loitering deep in our zone (and well behind the puck) stays goal-side of him.
     * Only one defenceman does this, so the attack keeps its numbers. Returns true if [s] took the job.
     */
    private fun guardHanger(world: World, team: Team, s: Skater, puck: Puck): Boolean {
        // s.markId >= 0 on entry means s was already guarding last tick (hysteresis); it is cleared on failure.
        val wasGuarding = s.markId >= 0
        if (guardHangerInner(world, team, s, puck, wasGuarding)) return true
        s.markId = -1
        return false
    }

    private fun guardHangerInner(world: World, team: Team, s: Skater, puck: Puck, wasGuarding: Boolean): Boolean {
        if (s.role != Role.LD && s.role != Role.RD) return false
        if (s.inPenaltyBox || s.stunTimer > 0f) return false
        val pax = team.toAttackX(puck.x)
        if (pax < -Rink.BLUE_LINE_X) return false // puck is back in our zone anyway; normal defence applies
        val opp = world.opponent(team.id)
        val limit = if (wasGuarding) -(HANG_ZONE_FT - 7f) else -HANG_ZONE_FT // enter at -55, leave at -48
        var hanger: Skater? = null
        var hx = 0f
        for (o in opp.skaters) {
            if (o.isGoalie || o.inPenaltyBox) continue
            val oax = team.toAttackX(o.x)
            if (oax > limit) continue
            if (hanger == null || oax < hx) { hanger = o; hx = oax }
        }
        val h = hanger ?: return false
        // Only defencemen who could actually take the job compete: not the carrier, a loose-puck chaser,
        // a stunned/boxed D, or the human-controlled skater.
        val humanTeam = world.isHuman(team.id)
        val carrier = puck.carrier
        for (t in team.skaters) {
            if (t === s || t.isGoalie || (t.role != Role.LD && t.role != Role.RD)) continue
            if (t.inPenaltyBox || t.stunTimer > 0f || t.aiChaser || t === carrier) continue
            if (humanTeam && t.index == world.controlled[team.id]) continue
            if (t.distanceTo(h.x, h.y) < s.distanceTo(h.x, h.y)) return false
        }
        s.markId = h.index
        val dx = team.ownGoalX - h.x
        val dy = -h.y
        val d = hypot(dx, dy).coerceAtLeast(0.01f)
        setWorldTarget(s, h.x + dx / d * MARK_GOALSIDE_FT, h.y + dy / d * MARK_GOALSIDE_FT)
        return true
    }

    /** Attack-frame spot for a supporting skater while the team has (or is chasing) the puck. */
    private fun offensiveSpot(role: Role, pax: Float, pay: Float, powerPlay: Boolean = false): Pair<Float, Float> {
        val inZone = pax > Rink.BLUE_LINE_X
        // Power play umbrella: D on the points, wings at the circles, centre in the slot.
        if (powerPlay && inZone) {
            return when (role) {
                Role.C -> 60f to 0f
                Role.LW -> 68f to -22f
                Role.RW -> 68f to 22f
                Role.LD -> 40f to -24f
                Role.RD -> 40f to 24f
                Role.G -> 0f to 0f
            }
        }
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
            var p = ai.shootTendency * (0.2f + 0.8f * closeness) * (0.45f + 0.55f * angleQuality)
            if (world.penaltyTeam == world.opponent(team.id).id) p *= 1.3f  // power play: shoot more
            if (rng.nextFloat() < p) {
                val corner = (1.4f + rng.nextFloat() * 1.2f) * (if (rng.nextBoolean()) 1f else -1f)
                sim.shoot(s, 0.55f + rng.nextFloat() * 0.45f, corner, ai.shotAccuracy)
                return
            }
        }

        // Seam pass: in the offensive zone, move the puck to an open teammate in a clearly better shooting spot.
        if (sax > Rink.BLUE_LINE_X && s.seamCooldown <= 0f) {
            s.seamCooldown = 0.6f
            if (rng.nextFloat() < ai.seamPass) {
            val ownQ = shotQuality(sax, say)
            if (ownQ < 0.5f) {
                var target: Skater? = null
                var bestQ = ownQ + 0.2f
                for (m in team.skaters) {
                    if (m === s || m.isGoalie || m.stunTimer > 0f || m.inPenaltyBox) continue
                    val q = shotQuality(team.toAttackX(m.x), m.y)
                    if (q <= bestQ) continue
                    var open = true
                    for (o in opp.skaters) {
                        if (!o.isGoalie && o.distanceTo(m.x, m.y) < 5f) { open = false; break }
                    }
                    if (open) { bestQ = q; target = m }
                }
                if (target != null && sim.passTo(s, target, ai.passAccuracy, human = false, checkLane = true)) return
            }
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

    /** 0..1 scoring chance from an attack-frame spot: closer to the net and nearer the slot is better. */
    private fun shotQuality(ax: Float, ay: Float): Float {
        val dist = hypot(Rink.GOAL_LINE_X - ax, ay)
        val closeness = (1f - dist / 44f).coerceIn(0f, 1f)
        val angle = 1f - (abs(ay) / 40f).coerceIn(0f, 1f)
        return closeness * (0.45f + 0.55f * angle)
    }

    // ---------------------------------------------------------------- goalies

    fun updateGoalie(world: World, team: Team, g: Skater, skill: Float, padScale: Float, leadSeconds: Float, dt: Float) {
        g.padScale = padScale
        val realPuck = world.puck
        val a = team.attackDir
        val gx = team.ownGoalX
        // Anticipate a puck in flight (shot or cross-ice pass) by leading it along its velocity.
        val lead = if (realPuck.carrier == null) leadSeconds * ((realPuck.speed - 15f) / 20f).coerceIn(0f, 1f) else 0f
        val puck = leadPuck.also { it.x = realPuck.x + realPuck.vx * lead; it.y = realPuck.y + realPuck.vy * lead }
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
        val pd = hypot(realPuck.x - g.x, realPuck.y - g.y)
        val carrier = realPuck.carrier
        g.butterfly = pd < 18f && (realPuck.shot || realPuck.speed > 30f || (carrier != null && carrier.team != team.id && pd < 12f))
    }
}
