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
        if (world.isShootout) {
            world.shootoutRound = 1
            world.shootoutTurn = 0
            world.shootoutShooterIndex[0] = 0
            world.shootoutShooterIndex[1] = 0
            setupShootoutAttempt()
            world.showBanner("SHOOTOUT SHOWDOWN", "5 Rounds • 1-on-1 Breakaways", 2.2f)
        } else {
            setupFaceoff(0f, 0f)
            world.showBanner(world.periodText() + " PERIOD", world.teams[0].info.fullName + " vs " + world.teams[1].info.fullName, 2.2f)
        }
    }

    /** Advances the match by [dt] seconds. [inputs] holds one entry per team; null = AI. */
    fun step(dt: Float, inputs: Array<PlayerInput?>) {
        val w = world
        w.events.clear()
        if (w.bannerTimer > 0f) {
            w.bannerTimer -= dt
            if (w.bannerTimer <= 0f) { w.banner = null; w.bannerSub = null }
        }
        for (t in 0..1) {
            w.switchLock[t] = max(0f, w.switchLock[t] - dt)
            if (w.fireTimer[t] > 0f) {
                w.fireTimer[t] = max(0f, w.fireTimer[t] - dt)
            }
        }
        for (s in w.allSkaters) {
            s.stunTimer = max(0f, s.stunTimer - dt)
            s.pokeTimer = max(0f, s.pokeTimer - dt)
            s.checkTimer = max(0f, s.checkTimer - dt)
            s.dekeTimer = max(0f, s.dekeTimer - dt)
            s.actionCooldown = max(0f, s.actionCooldown - dt)
            s.pickupCooldown = max(0f, s.pickupCooldown - dt)
            s.swingTimer = max(0f, s.swingTimer - dt)
            if (s.goalieActionTimer > 0f) {
                s.goalieActionTimer = max(0f, s.goalieActionTimer - dt)
                if (s.goalieActionTimer <= 0f) s.goalieAction = GoalieAction.NONE
            }
        }
        if (w.glassShatterTimer > 0f) w.glassShatterTimer = max(0f, w.glassShatterTimer - dt)

        when (w.phase) {
            Phase.FACEOFF -> {
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) dropPuck(inputs)
            }
            Phase.WHISTLE -> {
                coastAll(dt)
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) {
                    if (w.isShootout) advanceShootout() else setupFaceoff(w.faceoffX, w.faceoffY)
                }
            }
            Phase.GOAL -> {
                coastAll(dt)
                w.phaseTimer -= dt
                if (w.phaseTimer <= 0f) {
                    if (w.isShootout) advanceShootout() else if (w.overtime) gameOver() else setupFaceoff(0f, 0f)
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
                if (s.inPenaltyBox) {
                    val boxY = if (team.id == 0) -44.5f else 44.5f
                    s.place(0f, boxY, 0f)
                    continue
                }
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
            if (w.isHuman(team.id)) {
                val active = team.skaters.firstOrNull { !it.isGoalie && !it.inPenaltyBox }?.index ?: 0
                w.controlled[team.id] = active
            }
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

        if (w.isShootout) {
            recordShootoutAttempt(true)
            return
        }

        addMomentum(scorer.id, 2)
        w.phase = Phase.GOAL
        w.phaseTimer = GOAL_HOLD
        w.showBanner("GOAL!", scorer.info.fullName.uppercase(), GOAL_HOLD)
        w.events.add(GameEvent.GOAL)
        w.events.add(GameEvent.HORN)

        w.goaliePulled[0] = false
        w.goaliePulled[1] = false

        if (w.penaltyTeam != -1 && w.penaltyTeam != scorer.id) {
            releasePenalty()
        }
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
        if (w.penaltyTeam != -1) releasePenalty()
    }

    fun scoreLine(): String =
        world.teams[0].info.abbr + " " + world.teams[0].score + " - " + world.teams[1].score + " " + world.teams[1].info.abbr

    // ------------------------------------------------------------------ play

    private fun playStep(dt: Float, inputs: Array<PlayerInput?>) {
        val w = world
        if (w.isShootout) {
            w.shootoutTimer -= dt
            w.clock = max(0f, w.shootoutTimer)
            if (w.shootoutTimer <= 0f) {
                recordShootoutAttempt(false)
                return
            }
            val shooterTeam = w.teams[w.shootoutTurn]
            val attackingPuck = w.puck
            if (attackingPuck.shot && attackingPuck.speed < 5f && attackingPuck.carrier == null) {
                recordShootoutAttempt(false)
                return
            }
            if (attackingPuck.shot && attackingPuck.x * shooterTeam.attackDir < 0f) {
                recordShootoutAttempt(false)
                return
            }
        } else if (w.overtime) {
            w.clock += dt
        } else {
            w.clock -= dt
            if (w.clock <= 0f) { endPeriod(); return }
        }

        if (w.penaltyTeam != -1 && !w.isShootout) {
            w.penaltyTimer -= dt
            if (w.penaltyTimer <= 0f) {
                releasePenalty()
            }
        }

        // Late game AI pull goalie logic (trailing by 1 or 2 with < 50s in 3rd period)
        if (!w.isShootout) {
            for (team in w.teams) {
                if (!w.isHuman(team.id) && w.period == 3 && w.clock < 50f && !w.goaliePulled[team.id]) {
                    val opp = w.opponent(team.id)
                    if (team.score < opp.score && opp.score - team.score <= 2) {
                        togglePullGoalie(team.id)
                    }
                }
            }
        }

        for (t in 0..1) {
            val input = inputs[t]
            if (input != null && w.isHuman(t)) updateControl(t, input)
        }

        for (team in w.teams) {
            val humanIdx = w.controlled[team.id]
            val fireMul = if (w.isOnFire(team.id)) 1.20f else 1f
            val skaterMax = Skater.MAX_SPEED * fireMul
            val speedMul = (if (w.isHuman(team.id)) 1f else ai.speedMul) * fireMul
            val goalieSkill = if (w.isHuman(team.id)) HUMAN_GOALIE_SKILL else ai.goalieSkill
            val padScale = if (w.isHuman(team.id)) 1f else ai.goaliePadScale
            for (s in team.skaters) {
                if (s.inPenaltyBox) continue
                if (w.isShootout) {
                    val shooterTeamId = w.shootoutTurn
                    val defendingTeamId = 1 - shooterTeamId
                    val shooterIdx = w.shootoutShooterIndex[shooterTeamId]
                    val isCurrentShooter = (team.id == shooterTeamId && s.index == shooterIdx)
                    val isCurrentGoalie = (team.id == defendingTeamId && s.isGoalie)
                    if (!isCurrentShooter && !isCurrentGoalie) {
                        s.vx = 0f; s.vy = 0f
                        s.x = 0f; s.y = 250f
                        continue
                    }
                }
                if (s.isGoalie && !w.goaliePulled[team.id]) {
                    if (w.isShootout && team.id == (1 - w.shootoutTurn) && s.index == humanIdx) {
                        // Human controlled goalie in shootout defense
                        val input = inputs[team.id]
                        val mx = input?.moveX ?: 0f
                        val my = input?.moveY ?: 0f
                        val mag = hypot(mx, my)
                        if (mag > 0.1f) {
                            val normMx = if (mag > 1f) mx / mag else mx
                            val normMy = if (mag > 1f) my / mag else my
                            val gSpeed = Skater.GOALIE_SPEED * 1.35f
                            PhysicsEngine.moveSkater(s, normMx * gSpeed, normMy * gSpeed, gSpeed, dt)
                            val gx = team.ownGoalX
                            val a = team.attackDir
                            val minX = if (a > 0) gx - 0.5f else gx - 5.5f
                            val maxX = if (a > 0) gx + 5.5f else gx + 0.5f
                            s.x = s.x.coerceIn(minX, maxX)
                            s.y = s.y.coerceIn(-3.6f, 3.6f)
                            val pdx = w.puck.x - s.x
                            val pdy = w.puck.y - s.y
                            s.facing = kotlin.math.atan2(pdy, pdx)
                        } else {
                            aiController.updateGoalie(w, team, s, goalieSkill, padScale, dt)
                        }
                    } else if (w.isShootout && team.id == w.shootoutTurn && s.index == w.shootoutShooterIndex[w.shootoutTurn]) {
                        // Goalie is the active shooter in shootout!
                        if (s.index == humanIdx) {
                            val input = inputs[team.id]
                            var mx = input?.moveX ?: 0f
                            var my = input?.moveY ?: 0f
                            val mag = hypot(mx, my)
                            if (mag > 1f) { mx /= mag; my /= mag }
                            PhysicsEngine.moveSkater(s, mx * skaterMax, my * skaterMax, skaterMax, dt)
                        } else {
                            aiController.updateSkater(w, team, s, speedMul, dt, this)
                        }
                    } else {
                        aiController.updateGoalie(w, team, s, goalieSkill, padScale, dt)
                    }
                } else if (s.index == humanIdx) {
                    val input = inputs[team.id]
                    var mx = input?.moveX ?: 0f
                    var my = input?.moveY ?: 0f
                    val mag = hypot(mx, my)
                    if (mag > 1f) { mx /= mag; my /= mag }
                    PhysicsEngine.moveSkater(s, mx * skaterMax, my * skaterMax, skaterMax, dt)
                } else {
                    aiController.updateSkater(w, team, s, speedMul, dt, this)
                }
            }
        }

        for (t in 0..1) {
            val input = inputs[t]
            if (input != null && w.isHuman(t)) handleActions(t, input)
        }

        // AI puck carrier deke attempt against lunging opponents
        val carrier = w.puck.carrier
        if (carrier != null && !w.isHuman(carrier.team) && carrier.dekeTimer <= 0f && carrier.actionCooldown <= 0f) {
            for (o in w.opponent(carrier.team).skaters) {
                if (!o.isGoalie && (o.checkTimer > 0f || o.distanceTo(carrier.x, carrier.y) < 6f)) {
                    if (rng.nextFloat() < 0.35f) {
                        startDeke(carrier)
                        break
                    }
                }
            }
        }

        PhysicsEngine.resolveSkaterCollisions(w.allSkaters) { a, b, closing -> handleContact(a, b, closing) }
        for (s in w.allSkaters) PhysicsEngine.constrainSkater(s)

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
        if (w.isShootout) return
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
                if (s.isGoalie || s.inPenaltyBox || s === cur) continue
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
            if (s.isGoalie || s.inPenaltyBox || s.stunTimer > 0f) continue
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
        if (input.deke) startDeke(s)
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
            if (s.isGoalie) {
                // Active Goalie Save Controls!
                if (input.shootRelease && s.goalieActionTimer <= 0f) {
                    s.goalieAction = GoalieAction.BUTTERFLY
                    s.goalieActionTimer = 0.8f
                    w.events.add(GameEvent.GOALIE_SAVE_MOVE)
                    w.showBanner("BUTTERFLY DROP!", "Five-Hole Sealed", 1.0f)
                } else if (input.hit && s.goalieActionTimer <= 0f) {
                    s.goalieAction = GoalieAction.PAD_STACK
                    s.goalieActionTimer = 0.9f
                    val dy = if (abs(input.moveY) > 0.2f) sign(input.moveY) else (if (rng.nextBoolean()) 1f else -1f)
                    s.padStackDir = dy
                    s.vy += dy * 7.5f
                    w.events.add(GameEvent.GOALIE_SAVE_MOVE)
                    w.showBanner("TWO-PAD STACK!", "Diving Pad Sprawl!", 1.0f)
                } else if (input.pass) {
                    startPoke(s)
                }
            } else {
                if (w.puck.passTarget === s && (input.shootHeld || input.shootRelease)) {
                    s.oneTimerArmed = true
                }
                if (input.shootRelease) {
                    if (!s.oneTimerArmed) startPoke(s)
                }
                if (input.hit) startHit(s, null)
            }
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
        var speed = 58f + 68f * power.coerceIn(0f, 1f)
        if (w.isOnFire(s.team)) speed *= 1.20f
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

    fun startDeke(s: Skater) {
        if (s.actionCooldown > 0f || s.stunTimer > 0f || s.dekeTimer > 0f) return
        s.dekeTimer = 0.38f
        s.actionCooldown = 0.65f
        val perpX = -sin(s.facing)
        val perpY = cos(s.facing)
        s.dekeDir = if (rng.nextBoolean()) 1f else -1f
        val sp = 14f
        s.vx += perpX * s.dekeDir * sp
        s.vy += perpY * s.dekeDir * sp
        world.events.add(GameEvent.DEKE)
    }

    fun addMomentum(teamId: Int, points: Int) {
        val w = world
        if (w.isShootout || teamId !in 0..1 || w.isOnFire(teamId)) return
        w.momentum[teamId] += points
        if (w.momentum[teamId] >= 3) {
            w.momentum[teamId] = 0
            w.fireTimer[teamId] = 25f
            w.events.add(GameEvent.ON_FIRE)
            val team = w.team(teamId)
            w.showBanner("${team.info.abbr} IS ON FIRE!", "Speed & Slapshot Surge!", 2.5f)
        }
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
            if (victim.dekeTimer > 0f) {
                // Juked! Evaded the hit cleanly
                checker.checkTimer = 0f
                checker.vx *= 0.35f
                checker.vy *= 0.35f
                addMomentum(victim.team, 1)
                world.showBanner("DEKE!", "${victim.number} EVADED HIT", 1.0f)
                return
            }
            knockDown(victim, checker, 1.2f)
            checker.checkTimer = 0f
            checker.vx *= 0.4f
            world.events.add(GameEvent.HIT)
            addMomentum(checker.team, 1)

            // Shattered Glass on monster board checks
            val nearBoards = kotlin.math.abs(victim.y) > Rink.HALF_W - 5.5f || kotlin.math.abs(victim.x) > Rink.HALF_L - 8f
            val isMonsterHit = closing > 22f || world.isOnFire(checker.team)
            if (nearBoards && isMonsterHit && world.glassShatterTimer <= 0f) {
                world.glassShatterX = (checker.x + victim.x) / 2f
                world.glassShatterY = (checker.y + victim.y) / 2f
                world.glassShatterTimer = 4.0f
                world.events.add(GameEvent.GLASS_SHATTER)
                world.showBanner("GLASS SHATTERED!", "MONSTER BOARD CHECK!", 1.8f)
                addMomentum(checker.team, 1)
            }

            // Penalty check: Interference / Charging
            if (world.penaltyTeam == -1 && world.phase == Phase.PLAY) {
                val hasPuck = world.puck.carrier === victim
                val puckDist = hypot(victim.x - world.puck.x, victim.y - world.puck.y)
                if (!hasPuck && puckDist > 10f && rng.nextFloat() < 0.25f) {
                    callPenalty(checker, "INTERFERENCE")
                    return
                }
                if (closing > 28f && rng.nextFloat() < 0.22f) {
                    callPenalty(checker, "CHARGING")
                    return
                }
            }
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
        s.oneTimerArmed = false
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
                if (world.penaltyTeam == -1 && world.phase == Phase.PLAY && rng.nextFloat() < 0.08f) {
                    knockDown(carrier, o, 1.0f)
                    callPenalty(o, "TRIPPING")
                    return
                }
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
            if (w.goaliePulled[team.id]) continue
            val g = team.goalie
            if (g.inPenaltyBox) continue
            val beforeX = p.x
            val beforeY = p.y
            val padStackContact = g.goalieAction == GoalieAction.PAD_STACK && hypot((g.x - p.x) * 1.4f, g.y - p.y) < g.radius + 1.8f
            val butterflyContact = g.goalieAction == GoalieAction.BUTTERFLY && hypot(g.x - p.x, (g.y - p.y) * 0.75f) < g.radius + 1.3f
            val contact = PhysicsEngine.goalieContact(g, p, normal) || padStackContact || butterflyContact
            val shooterDeking = (p.shooter?.dekeTimer ?: 0f) > 0f && g.goalieAction == GoalieAction.NONE
            if (contact && p.shot && (shooterDeking || (!w.isHuman(team.id) && (p.leaking || rng.nextFloat() < ai.goalieLeak)))) {
                // Beaten cleanly or goalie frozen by deke move
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
                addMomentum(team.id, 1)
            }
            if (w.isShootout) {
                p.carrier = g
                p.shot = false
                p.vx = 0f; p.vy = 0f
                recordShootoutAttempt(false)
                return true
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
            val isShootoutShooterGoalie = w.isShootout && s.team == w.shootoutTurn && s.index == w.shootoutShooterIndex[w.shootoutTurn]
            if ((s.isGoalie && !isShootoutShooterGoalie) || s.stunTimer > 0f || s.pickupCooldown > 0f || s.inPenaltyBox) continue
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
        if (best != null) {
            if (best.oneTimerArmed && p.passTarget === best) {
                executeOneTimer(best)
            } else {
                best.oneTimerArmed = false
                takePossession(best)
            }
        }
    }

    fun executeOneTimer(s: Skater) {
        val w = world
        val team = w.team(s.team)
        val goalX = team.targetGoalX
        val goalie = w.opponent(s.team).goalie
        val openSide = if (abs(goalie.y) < 0.4f) (if (rng.nextBoolean()) 1f else -1f) else -sign(goalie.y)
        val aimY = openSide * (1.8f + rng.nextFloat() * 1.6f)
        var dx = goalX - w.puck.x
        var dy = aimY - w.puck.y
        val len = hypot(dx, dy).coerceAtLeast(0.01f)
        dx /= len; dy /= len

        var speed = 112f + rng.nextFloat() * 18f
        if (w.isOnFire(s.team)) speed *= 1.18f
        w.puck.carrier = null
        w.puck.vx = dx * speed
        w.puck.vy = dy * speed
        w.puck.shot = true
        w.puck.shooter = s
        w.puck.passTarget = null
        w.puck.leaking = false
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.5f
        s.swingTimer = 0.45f
        s.facing = atan2(dy, dx)
        s.oneTimerArmed = false
        w.events.add(GameEvent.ONE_TIMER)
        w.events.add(GameEvent.SHOT)
        addMomentum(s.team, 1)
    }

    fun callPenalty(offender: Skater, foulName: String) {
        val w = world
        if (w.penaltyTeam != -1 || offender.isGoalie) return
        w.penaltyTeam = offender.team
        w.penaltyTimer = 40f
        w.penaltyPlayerIndex = offender.index
        offender.inPenaltyBox = true
        offender.stunTimer = 0f
        offender.pokeTimer = 0f
        offender.checkTimer = 0f
        offender.vx = 0f; offender.vy = 0f
        val boxY = if (offender.team == 0) -44.5f else 44.5f
        offender.place(0f, boxY, 0f)

        if (w.controlled[offender.team] == offender.index) {
            val next = w.teams[offender.team].skaters.firstOrNull { !it.isGoalie && !it.inPenaltyBox }
            if (next != null) w.controlled[offender.team] = next.index
        }

        w.showBanner("PENALTY - $foulName", "${offender.number} ${offender.role.label} (2 MIN)", 2.0f)
        w.events.add(GameEvent.PENALTY)
        w.events.add(GameEvent.WHISTLE)

        val team = w.team(offender.team)
        val fx = team.ownGoalX + team.attackDir * (Rink.GOAL_LINE_X - Rink.END_DOT_X)
        val fy = if (rng.nextBoolean()) Rink.DOT_Y else -Rink.DOT_Y
        startWhistle(fx, fy)
    }

    fun releasePenalty() {
        val w = world
        val t = w.penaltyTeam
        val idx = w.penaltyPlayerIndex
        if (t in 0..1 && idx in 0..5) {
            val s = w.teams[t].skaters[idx]
            s.inPenaltyBox = false
            s.place(0f, if (t == 0) -38f else 38f, 0f)
        }
        w.penaltyTeam = -1
        w.penaltyTimer = 0f
        w.penaltyPlayerIndex = -1
        w.showBanner("FULL STRENGTH", null, 1.5f)
    }

    fun togglePullGoalie(teamId: Int): Boolean {
        val w = world
        w.goaliePulled[teamId] = !w.goaliePulled[teamId]
        val pulled = w.goaliePulled[teamId]
        val team = w.team(teamId)
        val goalie = team.goalie
        if (pulled) {
            w.showBanner("GOALIE PULLED!", "${team.info.abbr} EXTRA ATTACKER", 1.8f)
            goalie.place(team.ownGoalX + team.attackDir * 35f, (rng.nextFloat() - 0.5f) * 20f, 0f)
        } else {
            w.showBanner("GOALIE IN NET", "${team.info.abbr} NET PROTECTED", 1.5f)
            val gx = team.ownGoalX + team.attackDir * 2.8f
            goalie.place(gx, 0f, if (team.attackDir > 0f) 0f else Math.PI.toFloat())
        }
        return pulled
    }

    fun setupShootoutAttempt() {
        val w = world
        val shooterTeamId = w.shootoutTurn
        val defendingTeamId = 1 - shooterTeamId
        val shooterTeam = w.teams[shooterTeamId]
        val defendingTeam = w.teams[defendingTeamId]

        shooterTeam.attackDir = if (shooterTeamId == 0) 1f else -1f
        defendingTeam.attackDir = -shooterTeam.attackDir

        w.puck.reset(0f, 0f)

        val shooterIdx = w.shootoutShooterIndex[shooterTeamId]
        val shooter = shooterTeam.skaters[shooterIdx]
        val goalie = defendingTeam.goalie

        shooter.place(0f, 0f, if (shooterTeam.attackDir > 0f) 0f else Math.PI.toFloat())
        w.puck.carrier = shooter
        w.puck.lastTouchTeam = shooterTeamId

        val gx = defendingTeam.ownGoalX + defendingTeam.attackDir * 2.8f
        goalie.place(gx, 0f, if (defendingTeam.attackDir > 0f) 0f else Math.PI.toFloat())

        for (s in w.allSkaters) {
            if (s !== shooter && s !== goalie) {
                s.place(0f, 250f, 0f)
            }
        }

        if (w.isHuman(shooterTeamId)) {
            w.controlled[shooterTeamId] = shooter.index
        } else {
            w.controlled[shooterTeamId] = -1
        }
        if (w.isHuman(defendingTeamId)) {
            w.controlled[defendingTeamId] = goalie.index
        } else {
            w.controlled[defendingTeamId] = -1
        }

        w.shootoutTimer = 15f
        w.clock = 15f
        w.phase = Phase.PLAY
        val roleStr = when (shooter.role) {
            Role.C -> "CENTER"
            Role.LW -> "LEFT WING"
            Role.RW -> "RIGHT WING"
            Role.LD -> "LEFT DEFENSE"
            Role.RD -> "RIGHT DEFENSE"
            Role.G -> "GOALIE"
        }
        w.showBanner("ROUND ${w.shootoutRound}", "${shooterTeam.info.abbr} SHOOTER: $roleStr #${shooter.number}", 1.6f)
        w.events.add(GameEvent.WHISTLE)
    }

    fun cycleShootoutShooter(teamId: Int) {
        val w = world
        if (!w.isShootout || w.shootoutTurn != teamId || w.phase != Phase.PLAY) return
        val shooterTeam = w.teams[teamId]
        w.shootoutShooterIndex[teamId] = (w.shootoutShooterIndex[teamId] + 1) % shooterTeam.skaters.size
        val shooterIdx = w.shootoutShooterIndex[teamId]
        val shooter = shooterTeam.skaters[shooterIdx]
        val defendingTeam = w.teams[1 - teamId]
        val goalie = defendingTeam.goalie

        w.puck.reset(0f, 0f)
        shooter.place(0f, 0f, if (shooterTeam.attackDir > 0f) 0f else Math.PI.toFloat())
        w.puck.carrier = shooter
        w.puck.lastTouchTeam = teamId

        for (s in w.allSkaters) {
            if (s !== shooter && s !== goalie) {
                s.place(0f, 250f, 0f)
            }
        }

        if (w.isHuman(teamId)) {
            w.controlled[teamId] = shooter.index
        }
        val roleStr = when (shooter.role) {
            Role.C -> "CENTER"
            Role.LW -> "LEFT WING"
            Role.RW -> "RIGHT WING"
            Role.LD -> "LEFT DEFENSE"
            Role.RD -> "RIGHT DEFENSE"
            Role.G -> "GOALIE"
        }
        w.showBanner("SHOOTER: $roleStr #${shooter.number}", shooterTeam.info.fullName.uppercase(), 1.4f)
        w.events.add(GameEvent.WHISTLE)
    }

    private fun recordShootoutAttempt(goal: Boolean) {
        val w = world
        if (w.phase != Phase.PLAY) return
        val shooterTeamId = w.shootoutTurn
        val roundIdx = (w.shootoutRound - 1).coerceIn(0, w.shootoutAttempts[shooterTeamId].size - 1)
        w.shootoutAttempts[shooterTeamId][roundIdx] = if (goal) 1 else 2

        if (goal) {
            w.events.add(GameEvent.GOAL)
            w.events.add(GameEvent.HORN)
            w.showBanner("GOAL!", "${w.teams[shooterTeamId].info.fullName.uppercase()} SCORES!", 2.2f)
        } else {
            w.events.add(GameEvent.WHISTLE)
            w.showBanner("NO GOAL", "ATTEMPT OVER", 1.8f)
        }

        w.puck.carrier = null
        w.puck.vx = 0f; w.puck.vy = 0f
        w.phase = Phase.WHISTLE
        w.phaseTimer = 2.0f
    }

    private fun advanceShootout() {
        val w = world
        val t0Goals = w.shootoutAttempts[0].count { it == 1 }
        val t1Goals = w.shootoutAttempts[1].count { it == 1 }

        if (w.shootoutTurn == 0) {
            val t1Remaining = if (w.shootoutRound <= 5) (5 - w.shootoutRound + 1) else 1
            if (w.shootoutRound <= 5 && t0Goals > t1Goals + t1Remaining) {
                shootoutGameOver()
                return
            }
            w.shootoutTurn = 1
            w.shootoutShooterIndex[1] = (w.shootoutRound - 1) % w.teams[1].skaters.size
            setupShootoutAttempt()
        } else {
            val t0Remaining = if (w.shootoutRound < 5) (5 - w.shootoutRound) else 0
            val t1Remaining = if (w.shootoutRound < 5) (5 - w.shootoutRound) else 0

            if (w.shootoutRound < 5) {
                if (t0Goals > t1Goals + t1Remaining || t1Goals > t0Goals + t0Remaining) {
                    shootoutGameOver()
                    return
                }
                w.shootoutRound++
                w.shootoutTurn = 0
                w.shootoutShooterIndex[0] = (w.shootoutRound - 1) % w.teams[0].skaters.size
                setupShootoutAttempt()
            } else {
                if (t0Goals != t1Goals) {
                    shootoutGameOver()
                } else {
                    w.shootoutRound++
                    w.shootoutTurn = 0
                    w.shootoutShooterIndex[0] = (w.shootoutRound - 1) % w.teams[0].skaters.size
                    setupShootoutAttempt()
                    w.showBanner("SUDDEN DEATH", "Round ${w.shootoutRound} • Next Lead Wins!", 2.0f)
                }
            }
        }
    }

    private fun shootoutGameOver() {
        val w = world
        w.phase = Phase.GAME_OVER
        w.shootoutOver = true
        val winner = if (w.teams[0].score > w.teams[1].score) w.teams[0] else w.teams[1]
        w.showBanner("SHOOTOUT VICTORY!", "${winner.info.fullName.uppercase()} WIN ${w.teams[0].score} - ${w.teams[1].score}", 9999f)
        w.events.add(GameEvent.HORN)
        w.events.add(GameEvent.GAME_OVER)
    }
}
