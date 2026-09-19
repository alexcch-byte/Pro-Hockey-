package com.tablehockey.game.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Authoritative match simulation: game flow (faceoffs, whistles, periods,
 * overtime), puck possession, shooting, passing, poke and body checks, goalie
 * saves and scoring. Runs on the host; a WiFi client only mirrors the result.
 */
class Simulation(val world: World, private val ai: AiSettings, seed: Long = System.nanoTime()) {

    private val rng = Random(seed)
    private val aiController = AIController(ai, rng)
    private val normal = FloatArray(2)

    companion object {
        const val FACEOFF_HOLD = 1.4f
        const val WHISTLE_HOLD = 1.3f
        const val GOAL_HOLD = 3.2f
        const val PERIOD_HOLD = 3.5f
        const val HUMAN_GOALIE_SKILL = 0.92f
    }

    fun start() {
        setupFaceoff(0f, 0f)
        world.showBanner(world.periodText() + " PERIOD", world.teams[0].info.fullName + " vs " + world.teams[1].info.fullName, 2.2f)
    }

    /** Advances the match by [dt] seconds. [inputs] holds one entry per team; null = AI. */
    fun step(dt: Float, inputs: Array<PlayerInput?>) {
        val w = world
        w.events.clear()
        if (w.bannerTimer > 0f) {
            w.bannerTimer -= dt
            if (w.bannerTimer <= 0f) { w.banner = null; w.bannerSub = null }
        }
        for (t in 0..1) w.switchLock[t] = max(0f, w.switchLock[t] - dt)
        for (s in w.allSkaters) {
            s.stunTimer = max(0f, s.stunTimer - dt)
            s.pokeTimer = max(0f, s.pokeTimer - dt)
            s.checkTimer = max(0f, s.checkTimer - dt)
            s.actionCooldown = max(0f, s.actionCooldown - dt)
            s.pickupCooldown = max(0f, s.pickupCooldown - dt)
            s.swingTimer = max(0f, s.swingTimer - dt)
        }

        when (w.phase) {
            Phase.FACEOFF -> {
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) dropPuck(inputs)
            }
            Phase.WHISTLE -> {
                coastAll(dt)
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) setupFaceoff(w.faceoffX, w.faceoffY)
            }
            Phase.GOAL -> {
                coastAll(dt)
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) {
                    if (w.overtime) gameOver() else setupFaceoff(0f, 0f)
                }
            }
            Phase.PERIOD_END -> {
                coastAll(dt)
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) startNextPeriod()
            }
            Phase.GAME_OVER -> coastAll(dt)
            Phase.PLAY -> playStep(dt, inputs)
        }
        for (t in 0..1) inputs[t]?.clearPulses()
    }

    private fun coastAll(dt: Float) {
        for (s in world.allSkaters) {
            PhysicsEngine.moveSkater(s, 0f, 0f, Skater.MAX_SPEED, dt)
            PhysicsEngine.constrainSkater(s)
        }
        val c = world.puck.carrier
        if (c != null) PhysicsEngine.carryPuck(world.puck, c, dt)
    }

    // ------------------------------------------------------------------ flow

    fun setupFaceoff(fx: Float, fy: Float) {
        val w = world
        w.faceoffX = fx
        w.faceoffY = fy
        w.puck.reset(fx, fy)
        for (team in w.teams) {
            val a = team.attackDir
            for (s in team.skaters) {
                if (s.isGoalie) {
                    val gx = team.ownGoalX + a * 2.8f
                    s.place(gx, 0f, if (a > 0f) 0f else Math.PI.toFloat())
                    continue
                }
                val (ax, ay) = when (s.role) {
                    Role.C -> -1.6f to 0f
                    Role.LW -> -2.5f to -16f
                    Role.RW -> -2.5f to 16f
                    Role.LD -> -20f to -10f
                    Role.RD -> -20f to 10f
                    Role.G -> 0f to 0f
                }
                var x = fx + a * ax
                var y = fy + ay
                x = x.coerceIn(-84f, 84f)
                y = y.coerceIn(-38f, 38f)
                s.place(x, y, atan2(fy - y, fx - x))
            }
            if (w.isHuman(team.id)) w.controlled[team.id] = 0
        }
        w.phase = Phase.FACEOFF
        w.phaseTimer = FACEOFF_HOLD
        w.events.add(GameEvent.FACEOFF_SET)
    }

    private fun dropPuck(inputs: Array<PlayerInput?>) {
        val w = world
        val human0 = w.isHuman(0)
        val human1 = w.isHuman(1)
        val winner = when {
            human0 && !human1 -> if (rng.nextFloat() < ai.faceoffHumanBias) 0 else 1
            human1 && !human0 -> if (rng.nextFloat() < ai.faceoffHumanBias) 1 else 0
            else -> if (rng.nextBoolean()) 0 else 1
        }
        val a = w.teams[winner].attackDir
        w.puck.vx = -a * (15f + rng.nextFloat() * 8f)
        w.puck.vy = (rng.nextFloat() - 0.5f) * 18f
        w.puck.lastTouchTeam = winner
        w.phase = Phase.PLAY
        w.events.add(GameEvent.FACEOFF_DROP)
    }

    private fun startWhistle(fx: Float, fy: Float) {
        world.faceoffX = fx
        world.faceoffY = fy
        world.phase = Phase.WHISTLE
        world.phaseTimer = WHISTLE_HOLD
        world.events.add(GameEvent.WHISTLE)
    }

    private fun scoreGoal(end: Int) {
        val w = world
        val scorer = w.teams.first { it.attackDir.toInt() == end }
        scorer.score++
        scorer.shots++
        w.puck.vx = 0f; w.puck.vy = 0f
        w.puck.carrier = null
        w.puck.shot = false
        w.phase = Phase.GOAL
        w.phaseTimer = GOAL_HOLD
        w.showBanner("GOAL!", scorer.info.fullName.uppercase(), GOAL_HOLD)
        w.events.add(GameEvent.GOAL)
        w.events.add(GameEvent.HORN)
    }

    private fun endPeriod() {
        val w = world
        w.clock = 0f
        val tied = w.teams[0].score == w.teams[1].score
        when {
            w.period < 3 -> {
                w.phase = Phase.PERIOD_END
                w.phaseTimer = PERIOD_HOLD
                w.showBanner("END OF " + w.periodText() + " PERIOD", scoreLine(), PERIOD_HOLD)
                w.events.add(GameEvent.HORN)
                w.events.add(GameEvent.PERIOD_END)
            }
            tied -> {
                w.phase = Phase.PERIOD_END
                w.phaseTimer = PERIOD_HOLD
                w.showBanner("OVERTIME", "Sudden death - next goal wins", PERIOD_HOLD)
                w.events.add(GameEvent.HORN)
                w.events.add(GameEvent.PERIOD_END)
            }
            else -> gameOver()
        }
    }

    private fun startNextPeriod() {
        val w = world
        w.period++
        if (w.period >= 4) {
            w.overtime = true
            w.clock = 0f
        } else {
            w.clock = w.periodLength.toFloat()
        }
        for (t in w.teams) t.attackDir = -t.attackDir
        setupFaceoff(0f, 0f)
        w.showBanner(if (w.overtime) "OVERTIME" else w.periodText() + " PERIOD", scoreLine(), 2f)
    }

    private fun gameOver() {
        val w = world
        w.phase = Phase.GAME_OVER
        val winner = if (w.teams[0].score > w.teams[1].score) w.teams[0] else w.teams[1]
        w.showBanner("FINAL", winner.info.fullName.uppercase() + " WIN " + scoreLine(), 9999f)
        w.events.add(GameEvent.HORN)
        w.events.add(GameEvent.GAME_OVER)
    }

    fun scoreLine(): String =
        world.teams[0].info.abbr + " " + world.teams[0].score + " - " + world.teams[1].score + " " + world.teams[1].info.abbr

    // ------------------------------------------------------------------ play

    private fun playStep(dt: Float, inputs: Array<PlayerInput?>) {
        val w = world
        if (w.overtime) {
            w.clock += dt
        } else {
            w.clock -= dt
            if (w.clock <= 0f) { endPeriod(); return }
        }

        for (t in 0..1) {
            val input = inputs[t]
            if (input != null && w.isHuman(t)) updateControl(t, input)
        }

        for (team in w.teams) {
            val humanIdx = w.controlled[team.id]
            val speedMul = if (w.isHuman(team.id)) 1f else ai.speedMul
            val goalieSkill = if (w.isHuman(team.id)) HUMAN_GOALIE_SKILL else ai.goalieSkill
            val padScale = if (w.isHuman(team.id)) 1f else ai.goaliePadScale
            for (s in team.skaters) {
                if (s.isGoalie) {
                    aiController.updateGoalie(w, team, s, goalieSkill, padScale, dt)
                } else if (s.index == humanIdx) {
                    val input = inputs[team.id]
                    var mx = input?.moveX ?: 0f
                    var my = input?.moveY ?: 0f
                    val mag = hypot(mx, my)
                    if (mag > 1f) { mx /= mag; my /= mag }
                    PhysicsEngine.moveSkater(s, mx * Skater.MAX_SPEED, my * Skater.MAX_SPEED, Skater.MAX_SPEED, dt)
                } else {
                    aiController.updateSkater(w, team, s, speedMul, dt, this)
                }
            }
        }

        for (t in 0..1) {
            val input = inputs[t]
            if (input != null && w.isHuman(t)) handleActions(t, input)
        }

        PhysicsEngine.resolveSkaterCollisions(w.allSkaters) { a, b, closing -> handleContact(a, b, closing) }
        for (s in w.allSkaters) PhysicsEngine.constrainSkater(s)

        val carrier = w.puck.carrier
        if (carrier != null) {
            PhysicsEngine.carryPuck(w.puck, carrier, dt)
            contestPuck(carrier, dt)
        } else {
            val end = PhysicsEngine.updatePuck(w.puck, dt, w.events)
            if (end != 0) { scoreGoal(end); return }
            if (!handleGoalies()) tryPickups()
        }
    }

    private fun updateControl(t: Int, input: PlayerInput) {
        val w = world
        val team = w.team(t)
        val carrier = w.puck.carrier
        if (carrier != null && carrier.team == t && !carrier.isGoalie) {
            w.controlled[t] = carrier.index
            return
        }
        val px = w.puck.x + w.puck.vx * 0.25f
        val py = w.puck.y + w.puck.vy * 0.25f
        val cur = w.controlledSkater(t)
        if (input.pass) {
            // PASS without the puck = switch to the skater nearest the puck.
            var best: Skater? = null
            var bestD = Float.MAX_VALUE
            for (s in team.skaters) {
                if (s.isGoalie || s === cur) continue
                val d = s.distanceTo(px, py)
                if (d < bestD) { bestD = d; best = s }
            }
            if (best != null) { w.controlled[t] = best.index; w.switchLock[t] = 0.6f }
            return
        }
        if (w.switchLock[t] > 0f || cur == null) return
        var nearest: Skater? = null
        var nearestD = Float.MAX_VALUE
        for (s in team.skaters) {
            if (s.isGoalie || s.stunTimer > 0f) continue
            val d = s.distanceTo(px, py)
            if (d < nearestD) { nearestD = d; nearest = s }
        }
        if (nearest != null && nearest !== cur && cur.distanceTo(px, py) > nearestD + 7f) {
            w.controlled[t] = nearest.index
            w.switchLock[t] = 0.5f
        }
    }

    private fun handleActions(t: Int, input: PlayerInput) {
        val w = world
        val s = w.controlledSkater(t) ?: return
        if (s.stunTimer > 0f) { w.shotCharge[t] = 0f; return }
        val hasPuck = w.puck.carrier === s
        if (hasPuck) {
            w.shotCharge[t] = if (input.shootHeld) input.shootCharge else 0f
            if (input.shootRelease) {
                val mag = input.moveMagnitude
                // Aim assist (difficulty): steer toward the side of the net the goalie has left open.
                val goalie = w.opponent(t).goalie
                val openSide = if (abs(goalie.y) < 0.4f) (if (rng.nextBoolean()) 1f else -1f) else -sign(goalie.y)
                val assistY = openSide * 2.4f
                val assist = ai.aimAssist
                val aimY = if (mag > 0.25f) {
                    val raw = (input.moveY / mag) * 2.6f
                    raw * (1f - assist * 0.5f) + assistY * assist * 0.5f
                } else {
                    assistY * assist + (rng.nextFloat() - 0.5f) * 3f * (1f - assist)
                }
                shoot(s, 0.3f + 0.7f * input.shootCharge, aimY, 0.9f)
                w.shotCharge[t] = 0f
            } else if (input.pass) {
                pass(s, input.moveX, input.moveY, 0.95f, human = true, checkLane = false)
            } else if (input.hit) {
                startHit(s, null)
            }
        } else {
            w.shotCharge[t] = 0f
            if (input.shootRelease) startPoke(s)
            if (input.hit) startHit(s, null)
        }
    }

    // --------------------------------------------------------------- actions

    fun shoot(s: Skater, power: Float, aimY: Float, accuracy: Float) {
        val w = world
        if (w.puck.carrier !== s) return
        val team = w.team(s.team)
        val goalX = team.targetGoalX
        val noise = PhysicsEngine.gaussian(rng) * (1f - accuracy) * 7f
        val targetY = (aimY + noise).coerceIn(-5f, 5f)
        var dx = goalX - w.puck.x
        var dy = targetY - w.puck.y
        val len = hypot(dx, dy)
        if (len < 0.01f) { dx = team.attackDir; dy = 0f } else { dx /= len; dy /= len }
        val speed = 58f + 68f * power.coerceIn(0f, 1f)
        w.puck.carrier = null
        w.puck.vx = dx * speed
        w.puck.vy = dy * speed
        w.puck.shot = true
        w.puck.shooter = s
        w.puck.passTarget = null
        w.puck.leaking = false
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.45f
        s.swingTimer = 0.35f
        s.facing = atan2(dy, dx)
        w.events.add(GameEvent.SHOT)
    }

    /**
     * Passes to the teammate best matching the joystick direction, or the best
     * "ahead and open" teammate when no direction is given.
     */
    fun pass(s: Skater, mx: Float, my: Float, accuracy: Float, human: Boolean, checkLane: Boolean): Boolean {
        val w = world
        if (w.puck.carrier !== s) return false
        val team = w.team(s.team)
        val mag = hypot(mx, my)
        var best: Skater? = null
        var bestScore = Float.MAX_VALUE
        for (m in team.skaters) {
            if (m === s || m.isGoalie || m.stunTimer > 0f) continue
            val dx = m.x - s.x
            val dy = m.y - s.y
            val d = hypot(dx, dy)
            if (d < 3f) continue
            if (checkLane && laneBlocked(s, m)) continue
            val score: Float
            if (mag > 0.3f) {
                val cosA = (dx * mx + dy * my) / (d * mag)
                val angle = Math.acos(cosA.coerceIn(-1f, 1f).toDouble()).toFloat()
                if (angle > 1.3f) continue
                score = angle * 20f + d * 0.15f
            } else {
                score = -(team.attackDir * dx) + d * 0.45f
            }
            if (score < bestScore) { bestScore = score; best = m }
        }
        val target = best ?: return false
        val d = hypot(target.x - s.x, target.y - s.y)
        val speed = (38f + d * 0.9f).coerceIn(42f, 78f)
        val tt = d / speed
        val aimX = target.x + target.vx * tt * 0.85f
        val aimY = target.y + target.vy * tt * 0.85f
        var ang = atan2(aimY - w.puck.y, aimX - w.puck.x)
        ang += PhysicsEngine.gaussian(rng) * (1f - accuracy) * 0.3f
        w.puck.carrier = null
        w.puck.vx = cos(ang) * speed
        w.puck.vy = sin(ang) * speed
        w.puck.shot = false
        w.puck.shooter = null
        w.puck.passTarget = target
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.4f
        s.swingTimer = 0.25f
        s.facing = ang
        w.events.add(GameEvent.PASS)
        if (human) {
            w.controlled[s.team] = target.index
            w.switchLock[s.team] = 0.8f
        }
        return true
    }

    private fun laneBlocked(from: Skater, to: Skater): Boolean {
        val opp = world.opponent(from.team)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len2 = dx * dx + dy * dy
        if (len2 < 0.01f) return false
        for (o in opp.skaters) {
            val t = ((o.x - from.x) * dx + (o.y - from.y) * dy) / len2
            if (t <= 0.05f || t >= 0.95f) continue
            val cx = from.x + dx * t
            val cy = from.y + dy * t
            if (hypot(o.x - cx, o.y - cy) < 2.6f) return true
        }
        return false
    }

    fun startPoke(s: Skater) {
        if (s.actionCooldown > 0f || s.stunTimer > 0f) return
        s.pokeTimer = 0.38f
        s.actionCooldown = 0.75f
    }

    /** Lunges at [target] (or the nearest opponent in front when null). */
    fun startHit(s: Skater, target: Skater?) {
        if (s.actionCooldown > 0f || s.stunTimer > 0f) return
        var victim = target
        if (victim == null) {
            var bestD = 9f
            for (o in world.opponent(s.team).skaters) {
                if (o.isGoalie) continue
                val d = s.distanceTo(o.x, o.y)
                val bonus = if (world.puck.carrier === o) 3f else 0f
                if (d - bonus < bestD) { bestD = d - bonus; victim = o }
            }
        }
        val dirX: Float
        val dirY: Float
        if (victim != null) {
            val dx = victim.x - s.x
            val dy = victim.y - s.y
            val d = hypot(dx, dy).coerceAtLeast(0.01f)
            dirX = dx / d; dirY = dy / d
        } else {
            dirX = cos(s.facing); dirY = sin(s.facing)
        }
        s.checkTimer = 0.42f
        s.actionCooldown = 1.0f
        val sp = Skater.MAX_SPEED * 1.35f
        s.vx = dirX * sp
        s.vy = dirY * sp
        s.facing = atan2(dirY, dirX)
    }

    private fun handleContact(a: Skater, b: Skater, closing: Float) {
        if (a.team == b.team) return
        val checker = when {
            a.checkTimer > 0f && !a.isGoalie -> a
            b.checkTimer > 0f && !b.isGoalie -> b
            else -> null
        }
        if (checker != null) {
            val victim = if (checker === a) b else a
            if (victim.isGoalie || victim.stunTimer > 0f) return
            knockDown(victim, checker, 1.2f)
            checker.checkTimer = 0f
            checker.vx *= 0.4f
            checker.vy *= 0.4f
            world.events.add(GameEvent.HIT)
            return
        }
        if (closing > 20f && !a.isGoalie && !b.isGoalie) {
            val fast = if (a.speed >= b.speed) a else b
            val slow = if (fast === a) b else a
            if (slow.stunTimer <= 0f && rng.nextFloat() < 0.5f) {
                knockDown(slow, fast, 0.7f)
                world.events.add(GameEvent.HIT)
            }
        }
    }

    private fun knockDown(victim: Skater, by: Skater, seconds: Float) {
        victim.stunTimer = seconds
        victim.pokeTimer = 0f
        victim.checkTimer = 0f
        victim.vx = by.vx * 0.5f
        victim.vy = by.vy * 0.5f
        if (world.puck.carrier === victim) {
            val d = by.speed.coerceAtLeast(1f)
            loosePuck(victim, by.vx / d * 9f + (rng.nextFloat() - 0.5f) * 6f, by.vy / d * 9f + (rng.nextFloat() - 0.5f) * 6f, 0.6f)
        }
    }

    private fun loosePuck(from: Skater, vx: Float, vy: Float, cooldown: Float) {
        val p = world.puck
        p.carrier = null
        p.vx = vx
        p.vy = vy
        p.shot = false
        p.shooter = null
        p.passTarget = null
        from.pickupCooldown = cooldown
    }

    private fun takePossession(s: Skater) {
        val w = world
        val p = w.puck
        p.carrier = s
        p.shot = false
        p.shooter = null
        p.passTarget = null
        p.leaking = false
        p.lastTouchTeam = s.team
        if (w.isHuman(s.team)) {
            w.controlled[s.team] = s.index
        }
        w.events.add(GameEvent.PICKUP)
    }

    /** Opponents try to strip the puck from [carrier] with pokes, stick contact or body contact. */
    private fun contestPuck(carrier: Skater, dt: Float) {
        val w = world
        val p = w.puck
        for (o in w.opponent(carrier.team).skaters) {
            if (o.stunTimer > 0f) continue
            if (o.isGoalie) {
                if (hypot(o.x - p.x, o.y - p.y) < o.radius + Rink.PUCK_R + 0.3f) {
                    loosePuck(carrier, o.vx * 0.5f + (p.x - o.x) * 3f, o.vy * 0.5f + (p.y - o.y) * 3f, 0.4f)
                    return
                }
                continue
            }
            val bx = o.bladeX()
            val by = o.bladeY()
            val bladeD = hypot(bx - p.x, by - p.y)
            if (o.pokeTimer > 0f && bladeD < 1.7f) {
                loosePuck(carrier, cos(o.facing) * 15f + o.vx * 0.3f, sin(o.facing) * 15f + o.vy * 0.3f, 0.55f)
                o.pokeTimer = 0f
                w.events.add(GameEvent.POKE)
                return
            }
            if (bladeD < 1.15f && rng.nextFloat() < dt * 1.8f) {
                carrier.pickupCooldown = 0.5f
                takePossession(o)
                w.events.add(GameEvent.POKE)
                return
            }
            if (hypot(o.x - p.x, o.y - p.y) < o.radius + Rink.PUCK_R + 0.15f) {
                loosePuck(carrier, o.vx * 0.5f + (p.x - o.x) * 4f, o.vy * 0.5f + (p.y - o.y) * 4f, 0.35f)
                return
            }
        }
    }

    /** Returns true when a goalie dealt with the puck this tick. */
    private fun handleGoalies(): Boolean {
        val w = world
        val p = w.puck
        for (team in w.teams) {
            val g = team.goalie
            val beforeX = p.x
            val beforeY = p.y
            val contact = PhysicsEngine.goalieContact(g, p, normal)
            if (contact && p.shot && !w.isHuman(team.id) && (p.leaking || rng.nextFloat() < ai.goalieLeak)) {
                // Beaten cleanly: the puck slips through the AI goalie until it is clear of the pads.
                p.leaking = true
                p.x = beforeX
                p.y = beforeY
                continue
            }
            if (!contact && p.leaking) p.leaking = false
            val smother = !contact && p.speed < 12f && hypot(g.x - p.x, g.y - p.y) < g.radius + 1.8f
            if (!contact && !smother) continue
            val opp = w.opponent(team.id)
            val onGoal = p.shot || p.speed > 25f
            if (onGoal) {
                opp.shots++
                w.events.add(GameEvent.SAVE)
            }
            val cover = smother || p.speed < 15f || rng.nextFloat() < ai.coverChance
            if (cover) {
                p.carrier = g
                p.shot = false
                p.vx = 0f; p.vy = 0f
                val fx = team.ownGoalX + team.attackDir * (Rink.GOAL_LINE_X - Rink.END_DOT_X)
                val fy = if (abs(p.y) < 0.5f) (if (rng.nextBoolean()) Rink.DOT_Y else -Rink.DOT_Y) else sign(p.y) * Rink.DOT_Y
                w.showBanner(if (onGoal) "SAVE!" else "COVERED", g.number.toString() + " " + team.info.name, 1.2f)
                startWhistle(fx, fy)
                return true
            }
            // Rebound: reflect off the pads and kick it toward open ice.
            val dot = p.vx * normal[0] + p.vy * normal[1]
            if (dot < 0f) {
                p.vx -= 2f * dot * normal[0]
                p.vy -= 2f * dot * normal[1]
            }
            p.vx *= 0.32f
            p.vy *= 0.32f
            p.vy += (rng.nextFloat() - 0.5f) * 10f
            if (p.vx * team.attackDir < 6f) p.vx = team.attackDir * (6f + rng.nextFloat() * 10f)
            p.shot = false
            p.shooter = null
            p.passTarget = null
            return true
        }
        return false
    }

    private fun tryPickups() {
        val w = world
        val p = w.puck
        var best: Skater? = null
        var bestD = Float.MAX_VALUE
        for (s in w.allSkaters) {
            if (s.isGoalie || s.stunTimer > 0f || s.pickupCooldown > 0f) continue
            val bladeD = hypot(s.bladeX() - p.x, s.bladeY() - p.y)
            val bodyD = hypot(s.x - p.x, s.y - p.y) - s.radius + 0.5f
            val d = min(bladeD, bodyD)
            val threshold = if (s.pokeTimer > 0f) 1.9f else 1.55f
            val maxSpeed = if (p.passTarget === s) 95f else 72f
            if (d < threshold && p.speed < maxSpeed && d < bestD) {
                bestD = d
                best = s
            }
        }
        if (best != null) takePossession(best)
    }
}
