import Foundation

/// Authoritative match simulation: game flow (faceoffs, whistles, periods,
/// overtime), puck possession, shooting, passing, poke and body checks, goalie
/// saves and scoring. Runs on the host; a WiFi client only mirrors the result.
final class Simulation {
    static let FACEOFF_HOLD: Float = 1.4
    static let WHISTLE_HOLD: Float = 1.3
    static let GOAL_HOLD: Float = 3.2
    static let PERIOD_HOLD: Float = 3.5
    static let HUMAN_GOALIE_SKILL: Float = 0.92

    let world: World
    private let ai: AiSettings
    private let rng: SeededRandom
    private let aiController: AIController
    private var normalX: Float = 0
    private var normalY: Float = 0

    init(world: World, ai: AiSettings, seed: UInt64 = UInt64.random(in: UInt64.min...UInt64.max)) {
        self.world = world
        self.ai = ai
        self.rng = SeededRandom(seed: seed)
        self.aiController = AIController(ai: ai, rng: rng)
    }

    func start() {
        if world.isShootout {
            world.shootoutRound = 1
            world.shootoutTurn = 0
            world.shootoutShooterIndex[0] = 0
            world.shootoutShooterIndex[1] = 0
            setupShootoutAttempt()
            world.showBanner("SHOOTOUT SHOWDOWN", "5 Rounds • 1-on-1 Breakaways", 2.2)
        } else {
            setupFaceoff(0, 0)
            world.showBanner(world.periodText() + " PERIOD", world.teams[0].info.fullName + " vs " + world.teams[1].info.fullName, 2.2)
        }
    }

    /// Advances the match by [dt] seconds. [inputs] holds one entry per team; nil = AI.
    func step(dt: Float, inputs: [PlayerInput?]) {
        let w = world
        w.events.removeAll()
        if w.bannerTimer > 0 {
            w.bannerTimer -= dt
            if w.bannerTimer <= 0 { w.banner = nil; w.bannerSub = nil }
        }
        for t in 0...1 {
            w.switchLock[t] = max(0, w.switchLock[t] - dt)
            if w.fireTimer[t] > 0 {
                w.fireTimer[t] = max(0, w.fireTimer[t] - dt)
            }
        }
        for s in w.allSkaters {
            s.stunTimer = max(0, s.stunTimer - dt)
            s.pokeTimer = max(0, s.pokeTimer - dt)
            s.checkTimer = max(0, s.checkTimer - dt)
            s.dekeTimer = max(0, s.dekeTimer - dt)
            s.actionCooldown = max(0, s.actionCooldown - dt)
            s.pickupCooldown = max(0, s.pickupCooldown - dt)
            s.swingTimer = max(0, s.swingTimer - dt)
            if s.goalieActionTimer > 0 {
                s.goalieActionTimer = max(0, s.goalieActionTimer - dt)
                if s.goalieActionTimer <= 0 { s.goalieAction = .none }
            }
        }
        if w.glassShatterTimer > 0 {
            w.glassShatterTimer = max(0, w.glassShatterTimer - dt)
        }

        switch w.phase {
        case .faceoff:
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 { dropPuck(inputs) }
        case .whistle:
            coastAll(dt)
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 {
                if w.isShootout { advanceShootout() } else { setupFaceoff(w.faceoffX, w.faceoffY) }
            }
        case .goal:
            coastAll(dt)
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 {
                if w.isShootout {
                    advanceShootout()
                } else if w.overtime {
                    gameOver()
                } else {
                    setupFaceoff(0, 0)
                }
            }
        case .periodEnd:
            coastAll(dt)
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 { startNextPeriod() }
        case .gameOver:
            coastAll(dt)
        case .play:
            playStep(dt, inputs)
        }
        for t in 0...1 { inputs[t]?.clearPulses() }
    }

    private func coastAll(_ dt: Float) {
        for s in world.allSkaters {
            PhysicsEngine.moveSkater(s, desiredVx: 0, desiredVy: 0, maxSpeed: Skater.MAX_SPEED, dt: dt)
            PhysicsEngine.constrainSkater(s)
        }
        if let c = world.puck.carrier {
            PhysicsEngine.carryPuck(world.puck, c, dt)
        }
    }

    // ------------------------------------------------------------------ flow

    func setupFaceoff(_ fx: Float, _ fy: Float) {
        valSetupFaceoff(fx, fy)
    }

    private func valSetupFaceoff(_ fx: Float, _ fy: Float) {
        let w = world
        w.faceoffX = fx
        w.faceoffY = fy
        w.puck.reset(fx, fy)
        for team in w.teams {
            let a = team.attackDir
            for s in team.skaters {
                if s.isGoalie {
                    let gx = team.ownGoalX + a * 2.8
                    s.place(gx, 0, a > 0 ? 0 : .pi)
                    continue
                }
                let (ax, ay): (Float, Float)
                switch s.role {
                case .c: (ax, ay) = (-1.6, 0)
                case .lw: (ax, ay) = (-2.5, -16)
                case .rw: (ax, ay) = (-2.5, 16)
                case .ld: (ax, ay) = (-20, -10)
                case .rd: (ax, ay) = (-20, 10)
                case .g: (ax, ay) = (0, 0)
                }
                var x = fx + a * ax
                var y = fy + ay
                x = min(max(x, -84), 84)
                y = min(max(y, -38), 38)
                s.place(x, y, atan2(fy - y, fx - x))
            }
            if w.isHuman(team.id) { w.controlled[team.id] = 0 }
        }
        w.phase = .faceoff
        w.phaseTimer = Simulation.FACEOFF_HOLD
        w.events.append(.faceoffSet)
    }

    private func dropPuck(_ inputs: [PlayerInput?]) {
        let w = world
        let human0 = w.isHuman(0)
        let human1 = w.isHuman(1)
        let winner: Int
        if human0 && !human1 {
            winner = rng.nextFloat() < ai.faceoffHumanBias ? 0 : 1
        } else if human1 && !human0 {
            winner = rng.nextFloat() < ai.faceoffHumanBias ? 1 : 0
        } else {
            winner = rng.nextBool() ? 0 : 1
        }
        let a = w.teams[winner].attackDir
        w.puck.vx = -a * (15 + rng.nextFloat() * 8)
        w.puck.vy = (rng.nextFloat() - 0.5) * 18
        w.puck.lastTouchTeam = winner
        w.phase = .play
        w.events.append(.faceoffDrop)
    }

    private func startWhistle(_ fx: Float, _ fy: Float) {
        world.faceoffX = fx
        world.faceoffY = fy
        world.phase = .whistle
        world.phaseTimer = Simulation.WHISTLE_HOLD
        world.events.append(.whistle)
    }

    private func scoreGoal(_ end: Int) {
        let w = world
        guard let scorer = w.teams.first(where: { Int($0.attackDir) == end }) else { return }
        scorer.score += 1
        scorer.shots += 1
        w.puck.vx = 0; w.puck.vy = 0
        w.puck.carrier = nil
        w.puck.shot = false

        if w.isShootout {
            recordShootoutAttempt(true)
            return
        }

        addMomentum(scorer.id, 2)
        w.phase = .goal
        w.phaseTimer = Simulation.GOAL_HOLD
        w.showBanner("GOAL!", scorer.info.fullName.uppercased(), Simulation.GOAL_HOLD)
        w.events.append(.goal)
        w.events.append(.horn)

        w.goaliePulled[0] = false
        w.goaliePulled[1] = false

        if w.penaltyTeam != -1 && w.penaltyTeam != scorer.id {
            releasePenalty()
        }
    }

    private func endPeriod() {
        let w = world
        w.clock = 0
        let tied = w.teams[0].score == w.teams[1].score
        if w.period < 3 {
            w.phase = .periodEnd
            w.phaseTimer = Simulation.PERIOD_HOLD
            w.showBanner("END OF " + w.periodText() + " PERIOD", scoreLine(), Simulation.PERIOD_HOLD)
            w.events.append(.horn)
            w.events.append(.periodEnd)
        } else if tied {
            w.phase = .periodEnd
            w.phaseTimer = Simulation.PERIOD_HOLD
            w.showBanner("OVERTIME", "Sudden death - next goal wins", Simulation.PERIOD_HOLD)
            w.events.append(.horn)
            w.events.append(.periodEnd)
        } else {
            gameOver()
        }
    }

    private func startNextPeriod() {
        let w = world
        w.period += 1
        if w.period >= 4 {
            w.overtime = true
            w.clock = 0
        } else {
            w.clock = Float(w.periodLength)
        }
        for t in w.teams { t.attackDir = -t.attackDir }
        valSetupFaceoff(0, 0)
        w.showBanner(w.overtime ? "OVERTIME" : w.periodText() + " PERIOD", scoreLine(), 2)
    }

    private func gameOver() {
        let w = world
        w.phase = .gameOver
        let winner = w.teams[0].score > w.teams[1].score ? w.teams[0] : w.teams[1]
        w.showBanner("FINAL", winner.info.fullName.uppercased() + " WIN " + scoreLine(), 9999)
        w.events.append(.horn)
        w.events.append(.gameOver)
    }

    func scoreLine() -> String {
        world.teams[0].info.abbr + " " + String(world.teams[0].score) + " - " + String(world.teams[1].score) + " " + world.teams[1].info.abbr
    }

    // ------------------------------------------------------------------ play

    private func playStep(_ dt: Float, _ inputs: [PlayerInput?]) {
        let w = world
        if w.isShootout {
            w.shootoutTimer -= dt
            w.clock = max(0, w.shootoutTimer)
            if w.shootoutTimer <= 0 {
                recordShootoutAttempt(false)
                return
            }
            let shooterTeam = w.teams[w.shootoutTurn]
            let attackingPuck = w.puck
            if attackingPuck.shot && attackingPuck.speed < 5 && attackingPuck.carrier == nil {
                recordShootoutAttempt(false)
                return
            }
            if attackingPuck.shot && attackingPuck.x * shooterTeam.attackDir < 0 {
                recordShootoutAttempt(false)
                return
            }
        } else if w.overtime {
            w.clock += dt
        } else {
            w.clock -= dt
            if w.clock <= 0 { endPeriod(); return }
        }

        if w.penaltyTeam != -1 && !w.isShootout {
            w.penaltyTimer -= dt
            if w.penaltyTimer <= 0 {
                releasePenalty()
            }
        }

        // Late game AI pull goalie logic (trailing by 1 or 2 with < 50s in 3rd period)
        if !w.isShootout {
            for team in w.teams {
                if !w.isHuman(team.id) && w.period == 3 && w.clock < 50 && !w.goaliePulled[team.id] {
                    let opp = w.opponent(team.id)
                    if team.score < opp.score && opp.score - team.score <= 2 {
                        _ = togglePullGoalie(team.id)
                    }
                }
            }
        }

        for t in 0...1 {
            if let input = inputs[t], w.isHuman(t) { updateControl(t, input) }
        }

        for team in w.teams {
            let humanIdx = w.controlled[team.id]
            let fireMul: Float = w.isOnFire(team.id) ? 1.20 : 1.0
            let skaterMax = Skater.MAX_SPEED * fireMul
            let speedMul: Float = (w.isHuman(team.id) ? 1 : ai.speedMul) * fireMul
            let goalieSkill: Float = w.isHuman(team.id) ? Simulation.HUMAN_GOALIE_SKILL : ai.goalieSkill
            let padScale: Float = w.isHuman(team.id) ? 1 : ai.goaliePadScale

            for s in team.skaters {
                if s.inPenaltyBox { continue }

                if w.isShootout {
                    let shooterTeamId = w.shootoutTurn
                    let defendingTeamId = 1 - shooterTeamId
                    let shooterIdx = w.shootoutShooterIndex[shooterTeamId]
                    let isCurrentShooter = (team.id == shooterTeamId && s.index == shooterIdx)
                    let isCurrentGoalie = (team.id == defendingTeamId && s.isGoalie)
                    if !isCurrentShooter && !isCurrentGoalie {
                        s.vx = 0; s.vy = 0
                        s.x = 0; s.y = 250
                        continue
                    }
                }

                if s.isGoalie && !w.goaliePulled[team.id] {
                    if w.isShootout && team.id == (1 - w.shootoutTurn) && s.index == humanIdx {
                        // Human controlled goalie in shootout defense
                        let input = inputs[team.id]
                        let mx = input?.moveX ?? 0
                        let my = input?.moveY ?? 0
                        let mag = hypot(mx, my)
                        if mag > 0.1 {
                            let normMx = mag > 1 ? mx / mag : mx
                            let normMy = mag > 1 ? my / mag : my
                            let gSpeed = Skater.GOALIE_SPEED * 1.35
                            PhysicsEngine.moveSkater(s, desiredVx: normMx * gSpeed, desiredVy: normMy * gSpeed, maxSpeed: gSpeed, dt: dt)
                            let gx = team.ownGoalX
                            let a = team.attackDir
                            let minX = a > 0 ? gx - 0.5 : gx - 5.5
                            let maxX = a > 0 ? gx + 5.5 : gx + 0.5
                            s.x = min(max(s.x, minX), maxX)
                            s.y = min(max(s.y, -3.6), 3.6)
                            let pdx = w.puck.x - s.x
                            let pdy = w.puck.y - s.y
                            s.facing = atan2(pdy, pdx)
                        } else {
                            aiController.updateGoalie(w, team, s, skill: goalieSkill, padScale: padScale, dt: dt)
                        }
                    } else if w.isShootout && team.id == w.shootoutTurn && s.index == w.shootoutShooterIndex[w.shootoutTurn] {
                        // Goalie is active shooter in shootout!
                        if s.index == humanIdx {
                            let input = inputs[team.id]
                            var mx = input?.moveX ?? 0
                            var my = input?.moveY ?? 0
                            let mag = hypot(mx, my)
                            if mag > 1 { mx /= mag; my /= mag }
                            PhysicsEngine.moveSkater(s, desiredVx: mx * skaterMax, desiredVy: my * skaterMax, maxSpeed: skaterMax, dt: dt)
                        } else {
                            aiController.updateSkater(w, team, s, speedMul: speedMul, dt: dt, sim: self)
                        }
                    } else {
                        aiController.updateGoalie(w, team, s, skill: goalieSkill, padScale: padScale, dt: dt)
                    }
                } else if s.index == humanIdx {
                    let input = inputs[team.id]
                    var mx = input?.moveX ?? 0
                    var my = input?.moveY ?? 0
                    let mag = hypot(mx, my)
                    if mag > 1 { mx /= mag; my /= mag }
                    PhysicsEngine.moveSkater(s, desiredVx: mx * skaterMax, desiredVy: my * skaterMax, maxSpeed: skaterMax, dt: dt)
                } else {
                    aiController.updateSkater(w, team, s, speedMul: speedMul, dt: dt, sim: self)
                }
            }
        }

        for t in 0...1 {
            if let input = inputs[t], w.isHuman(t) { handleActions(t, input) }
        }

        // AI puck carrier deke attempt against lunging opponents
        if let carrier = w.puck.carrier, !w.isHuman(carrier.team), carrier.dekeTimer <= 0, carrier.actionCooldown <= 0 {
            for o in w.opponent(carrier.team).skaters {
                if !o.isGoalie && (o.checkTimer > 0 || o.distanceTo(carrier.x, carrier.y) < 6) {
                    if rng.nextFloat() < 0.35 {
                        startDeke(carrier)
                        break
                    }
                }
            }
        }

        PhysicsEngine.resolveSkaterCollisions(w.allSkaters) { a, b, closing in self.handleContact(a, b, closing) }
        for s in w.allSkaters { PhysicsEngine.constrainSkater(s) }

        if let carrier = w.puck.carrier {
            PhysicsEngine.carryPuck(w.puck, carrier, dt)
            contestPuck(carrier, dt)
        } else {
            let end = PhysicsEngine.updatePuck(w.puck, dt: dt, events: &w.events)
            if end != 0 { scoreGoal(end); return }
            if !handleGoalies() { tryPickups() }
        }
    }

    private func updateControl(_ t: Int, _ input: PlayerInput) {
        let w = world
        if w.isShootout { return }
        let team = w.team(t)
        let carrier = w.puck.carrier
        if let carrier = carrier, carrier.team == t, !carrier.isGoalie {
            w.controlled[t] = carrier.index
            return
        }
        let px = w.puck.x + w.puck.vx * 0.25
        let py = w.puck.y + w.puck.vy * 0.25
        let cur = w.controlledSkater(t)
        if input.pass {
            // PASS without the puck = switch to the skater nearest the puck.
            var best: Skater?
            var bestD = Float.greatestFiniteMagnitude
            for s in team.skaters {
                if s.isGoalie || s.inPenaltyBox || s === cur { continue }
                let d = s.distanceTo(px, py)
                if d < bestD { bestD = d; best = s }
            }
            if let best = best { w.controlled[t] = best.index; w.switchLock[t] = 0.6 }
            return
        }
        guard w.switchLock[t] <= 0, let cur = cur else { return }
        var nearest: Skater?
        var nearestD = Float.greatestFiniteMagnitude
        for s in team.skaters {
            if s.isGoalie || s.inPenaltyBox || s.stunTimer > 0 { continue }
            let d = s.distanceTo(px, py)
            if d < nearestD { nearestD = d; nearest = s }
        }
        if let nearest = nearest, nearest !== cur, cur.distanceTo(px, py) > nearestD + 7 {
            w.controlled[t] = nearest.index
            w.switchLock[t] = 0.5
        }
    }

    private func handleActions(_ t: Int, _ input: PlayerInput) {
        let w = world
        guard let s = w.controlledSkater(t) else { return }
        if s.stunTimer > 0 { w.shotCharge[t] = 0; return }
        if input.deke { startDeke(s) }
        let hasPuck = w.puck.carrier === s
        if hasPuck {
            w.shotCharge[t] = input.shootHeld ? input.shootCharge : 0
            if input.shootRelease {
                let mag = input.moveMagnitude
                // Aim assist (difficulty): steer toward the side of the net the goalie has left open.
                let goalie = w.opponent(t).goalie
                let openSide: Float = abs(goalie.y) < 0.4 ? (rng.nextBool() ? 1 : -1) : -sign(goalie.y)
                let assistY = openSide * 2.4
                let assist = ai.aimAssist
                let aimY: Float
                if mag > 0.25 {
                    let raw = (input.moveY / mag) * 2.6
                    aimY = raw * (1 - assist * 0.5) + assistY * assist * 0.5
                } else {
                    aimY = assistY * assist + (rng.nextFloat() - 0.5) * 3 * (1 - assist)
                }
                shoot(s, power: 0.3 + 0.7 * input.shootCharge, aimY: aimY, accuracy: 0.9)
                w.shotCharge[t] = 0
            } else if input.pass {
                pass(s, mx: input.moveX, my: input.moveY, accuracy: 0.95, human: true, checkLane: false)
            } else if input.hit {
                startHit(s, nil)
            }
        } else {
            w.shotCharge[t] = 0
            if s.isGoalie {
                // Active Goalie Save Controls!
                if input.shootRelease && s.goalieActionTimer <= 0 {
                    s.goalieAction = .butterfly
                    s.goalieActionTimer = 0.8
                    w.events.append(.goalieSaveMove)
                    w.showBanner("BUTTERFLY DROP!", "Five-Hole Sealed", 1.0)
                } else if input.hit && s.goalieActionTimer <= 0 {
                    s.goalieAction = .padStack
                    s.goalieActionTimer = 0.9
                    let dy: Float = abs(input.moveY) > 0.2 ? sign(input.moveY) : (rng.nextBool() ? 1 : -1)
                    s.padStackDir = dy
                    s.vy += dy * 7.5
                    w.events.append(.goalieSaveMove)
                    w.showBanner("TWO-PAD STACK!", "Diving Pad Sprawl!", 1.0)
                } else if input.pass {
                    startPoke(s)
                }
            } else {
                if w.puck.passTarget === s && (input.shootHeld || input.shootRelease) {
                    s.oneTimerArmed = true
                }
                if input.shootRelease {
                    if !s.oneTimerArmed { startPoke(s) }
                }
                if input.hit { startHit(s, nil) }
            }
        }
    }

    private func sign(_ v: Float) -> Float { v > 0 ? 1 : (v < 0 ? -1 : 0) }

    // --------------------------------------------------------------- actions

    func shoot(_ s: Skater, power: Float, aimY: Float, accuracy: Float) {
        let w = world
        if w.puck.carrier !== s { return }
        let team = w.team(s.team)
        let goalX = team.targetGoalX
        let noise = PhysicsEngine.gaussian(rng) * (1 - accuracy) * 7
        let targetY = min(max(aimY + noise, -5), 5)
        var dx = goalX - w.puck.x
        var dy = targetY - w.puck.y
        let len = hypot(dx, dy)
        if len < 0.01 { dx = team.attackDir; dy = 0 } else { dx /= len; dy /= len }
        var speed = 58 + 68 * min(max(power, 0), 1)
        if w.isOnFire(s.team) { speed *= 1.20 }
        w.puck.carrier = nil
        w.puck.vx = dx * speed
        w.puck.vy = dy * speed
        w.puck.shot = true
        w.puck.shooter = s
        w.puck.passTarget = nil
        w.puck.leaking = false
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.45
        s.swingTimer = 0.35
        s.facing = atan2(dy, dx)
        w.events.append(.shot)
    }

    func startDeke(_ s: Skater) {
        if s.actionCooldown > 0 || s.stunTimer > 0 || s.dekeTimer > 0 { return }
        s.dekeTimer = 0.38
        s.actionCooldown = 0.65
        let perpX = -sin(s.facing)
        let perpY = cos(s.facing)
        s.dekeDir = rng.nextBool() ? 1 : -1
        let sp: Float = 14
        s.vx += perpX * s.dekeDir * sp
        s.vy += perpY * s.dekeDir * sp
        world.events.append(.deke)
    }

    func addMomentum(_ teamId: Int, _ points: Int) {
        let w = world
        guard !w.isShootout, (0...1).contains(teamId), !w.isOnFire(teamId) else { return }
        w.momentum[teamId] += points
        if w.momentum[teamId] >= 3 {
            w.momentum[teamId] = 0
            w.fireTimer[teamId] = 25
            w.events.append(.onFire)
            let team = w.team(teamId)
            w.showBanner("\(team.info.abbr) IS ON FIRE!", "Speed & Slapshot Surge!", 2.5)
        }
    }

    /// Passes to the teammate best matching the joystick direction, or the best
    /// "ahead and open" teammate when no direction is given.
    @discardableResult
    func pass(_ s: Skater, mx: Float, my: Float, accuracy: Float, human: Bool, checkLane: Bool) -> Bool {
        let w = world
        if w.puck.carrier !== s { return false }
        let team = w.team(s.team)
        let mag = hypot(mx, my)
        var best: Skater?
        var bestScore = Float.greatestFiniteMagnitude
        for m in team.skaters {
            if m === s || m.isGoalie || m.inPenaltyBox || m.stunTimer > 0 { continue }
            let dx = m.x - s.x
            let dy = m.y - s.y
            let d = hypot(dx, dy)
            if d < 3 { continue }
            if checkLane && laneBlocked(s, m) { continue }
            let score: Float
            if mag > 0.3 {
                let cosA = (dx * mx + dy * my) / (d * mag)
                let angle = acos(min(max(cosA, -1), 1))
                if angle > 1.3 { continue }
                score = angle * 20 + d * 0.15
            } else {
                score = -(team.attackDir * dx) + d * 0.45
            }
            if score < bestScore { bestScore = score; best = m }
        }
        guard let target = best else { return false }
        let d = hypot(target.x - s.x, target.y - s.y)
        let speed = min(max(38 + d * 0.9, 42), 78)
        let tt = d / speed
        let aimX = target.x + target.vx * tt * 0.85
        let aimY = target.y + target.vy * tt * 0.85
        var ang = atan2(aimY - w.puck.y, aimX - w.puck.x)
        ang += PhysicsEngine.gaussian(rng) * (1 - accuracy) * 0.3
        w.puck.carrier = nil
        w.puck.vx = cos(ang) * speed
        w.puck.vy = sin(ang) * speed
        w.puck.shot = false
        w.puck.shooter = nil
        w.puck.passTarget = target
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.4
        s.swingTimer = 0.25
        s.facing = ang
        w.events.append(.pass)
        if human {
            w.controlled[s.team] = target.index
            w.switchLock[s.team] = 0.8
        }
        return true
    }

    private func laneBlocked(_ from: Skater, _ to: Skater) -> Bool {
        let opp = world.opponent(from.team)
        let dx = to.x - from.x
        let dy = to.y - from.y
        let len2 = dx * dx + dy * dy
        if len2 < 0.01 { return false }
        for o in opp.skaters {
            let t = ((o.x - from.x) * dx + (o.y - from.y) * dy) / len2
            if t <= 0.05 || t >= 0.95 { continue }
            let cx = from.x + dx * t
            let cy = from.y + dy * t
            if hypot(o.x - cx, o.y - cy) < 2.6 { return true }
        }
        return false
    }

    func startPoke(_ s: Skater) {
        if s.actionCooldown > 0 || s.stunTimer > 0 { return }
        s.pokeTimer = 0.38
        s.actionCooldown = 0.75
    }

    /// Lunges at [target] (or the nearest opponent in front when nil).
    func startHit(_ s: Skater, _ target: Skater?) {
        if s.actionCooldown > 0 || s.stunTimer > 0 { return }
        var victim = target
        if victim == nil {
            var bestD: Float = 9
            for o in world.opponent(s.team).skaters {
                if o.isGoalie || o.inPenaltyBox { continue }
                let d = s.distanceTo(o.x, o.y)
                let bonus: Float = world.puck.carrier === o ? 3 : 0
                if d - bonus < bestD { bestD = d - bonus; victim = o }
            }
        }
        let dirX: Float
        let dirY: Float
        if let victim = victim {
            let dx = victim.x - s.x
            let dy = victim.y - s.y
            let d = max(hypot(dx, dy), 0.01)
            dirX = dx / d; dirY = dy / d
        } else {
            dirX = cos(s.facing); dirY = sin(s.facing)
        }
        s.checkTimer = 0.42
        s.actionCooldown = 1.0
        let sp = Skater.MAX_SPEED * 1.35
        s.vx = dirX * sp
        s.vy = dirY * sp
        s.facing = atan2(dirY, dirX)
    }

    private func handleContact(_ a: Skater, _ b: Skater, _ closing: Float) {
        if a.team == b.team { return }
        let checker: Skater?
        if a.checkTimer > 0 && !a.isGoalie { checker = a }
        else if b.checkTimer > 0 && !b.isGoalie { checker = b }
        else { checker = nil }

        if let checker = checker {
            let victim = checker === a ? b : a
            if victim.isGoalie || victim.stunTimer > 0 { return }

            if victim.dekeTimer > 0 {
                // Juked! Evaded the hit cleanly
                checker.checkTimer = 0
                checker.vx *= 0.35
                checker.vy *= 0.35
                addMomentum(victim.team, 1)
                world.showBanner("DEKE!", "\(victim.number) EVADED HIT", 1.0)
                return
            }

            knockDown(victim, checker, 1.2)
            checker.checkTimer = 0
            checker.vx *= 0.4
            checker.vy *= 0.4
            world.events.append(.hit)
            addMomentum(checker.team, 1)

            // Shattered Glass on monster board checks
            let nearBoards = abs(victim.y) > Rink.HALF_W - 5.5 || abs(victim.x) > Rink.HALF_L - 8
            let isMonsterHit = closing > 22 || world.isOnFire(checker.team)
            if nearBoards && isMonsterHit && world.glassShatterTimer <= 0 {
                world.glassShatterX = (checker.x + victim.x) / 2
                world.glassShatterY = (checker.y + victim.y) / 2
                world.glassShatterTimer = 4.0
                world.events.append(.glassShatter)
                world.showBanner("GLASS SHATTERED!", "MONSTER BOARD CHECK!", 1.8)
                addMomentum(checker.team, 1)
            }

            // Penalty check: Interference / Charging
            if world.penaltyTeam == -1 && world.phase == .play {
                let hasPuck = world.puck.carrier === victim
                let puckDist = hypot(victim.x - world.puck.x, victim.y - world.puck.y)
                if !hasPuck && puckDist > 10 && rng.nextFloat() < 0.25 {
                    callPenalty(checker, foulName: "INTERFERENCE")
                    return
                }
                if closing > 28 && rng.nextFloat() < 0.22 {
                    callPenalty(checker, foulName: "CHARGING")
                    return
                }
            }
            return
        }

        if closing > 20 && !a.isGoalie && !b.isGoalie {
            let fast = a.speed >= b.speed ? a : b
            let slow = fast === a ? b : a
            if slow.stunTimer <= 0 && rng.nextFloat() < 0.5 {
                knockDown(slow, fast, 0.7)
                world.events.append(.hit)
            }
        }
    }

    private func knockDown(_ victim: Skater, _ by: Skater, _ seconds: Float) {
        victim.stunTimer = seconds
        victim.pokeTimer = 0
        victim.checkTimer = 0
        victim.vx = by.vx * 0.5
        victim.vy = by.vy * 0.5
        if world.puck.carrier === victim {
            let d = max(by.speed, 1)
            loosePuck(victim, vx: by.vx / d * 9 + (rng.nextFloat() - 0.5) * 6, vy: by.vy / d * 9 + (rng.nextFloat() - 0.5) * 6, cooldown: 0.6)
        }
    }

    private func loosePuck(_ from: Skater, vx: Float, vy: Float, cooldown: Float) {
        let p = world.puck
        p.carrier = nil
        p.vx = vx
        p.vy = vy
        p.shot = false
        p.shooter = nil
        p.passTarget = nil
        from.pickupCooldown = cooldown
    }

    private func takePossession(_ s: Skater) {
        let w = world
        let p = w.puck
        p.carrier = s
        p.shot = false
        p.shooter = nil
        p.passTarget = nil
        p.leaking = false
        p.lastTouchTeam = s.team
        if w.isHuman(s.team) {
            w.controlled[s.team] = s.index
        }
        w.events.append(.pickup)
    }

    /// Opponents try to strip the puck from [carrier] with pokes, stick contact or body contact.
    private func contestPuck(_ carrier: Skater, _ dt: Float) {
        let w = world
        let p = w.puck
        for o in w.opponent(carrier.team).skaters {
            if o.stunTimer > 0 { continue }
            if o.isGoalie {
                if hypot(o.x - p.x, o.y - p.y) < o.radius + Rink.PUCK_R + 0.3 {
                    loosePuck(carrier, vx: o.vx * 0.5 + (p.x - o.x) * 3, vy: o.vy * 0.5 + (p.y - o.y) * 3, cooldown: 0.4)
                    return
                }
                continue
            }
            let bx = o.bladeX()
            let by = o.bladeY()
            let bladeD = hypot(bx - p.x, by - p.y)
            if o.pokeTimer > 0 && bladeD < 1.7 {
                if world.penaltyTeam == -1 && world.phase == .play && rng.nextFloat() < 0.08 {
                    knockDown(carrier, o, 1.0)
                    callPenalty(o, foulName: "TRIPPING")
                    return
                }
                loosePuck(carrier, vx: cos(o.facing) * 15 + o.vx * 0.3, vy: sin(o.facing) * 15 + o.vy * 0.3, cooldown: 0.55)
                o.pokeTimer = 0
                w.events.append(.poke)
                return
            }
            if bladeD < 1.15 && rng.nextFloat() < dt * 1.8 {
                carrier.pickupCooldown = 0.5
                takePossession(o)
                w.events.append(.poke)
                return
            }
            if hypot(o.x - p.x, o.y - p.y) < o.radius + Rink.PUCK_R + 0.15 {
                loosePuck(carrier, vx: o.vx * 0.5 + (p.x - o.x) * 4, vy: o.vy * 0.5 + (p.y - o.y) * 4, cooldown: 0.35)
                return
            }
        }
    }

    /// Returns true when a goalie dealt with the puck this tick.
    private func handleGoalies() -> Bool {
        let w = world
        let p = w.puck
        for team in w.teams {
            if w.goaliePulled[team.id] { continue }
            let g = team.goalie
            if g.inPenaltyBox { continue }
            let beforeX = p.x
            let beforeY = p.y
            let padStackContact = g.goalieAction == .padStack && hypot((g.x - p.x) * 1.4, g.y - p.y) < g.radius + 1.8
            let butterflyContact = g.goalieAction == .butterfly && hypot(g.x - p.x, (g.y - p.y) * 0.75) < g.radius + 1.3
            let contact = PhysicsEngine.goalieContact(g, p, outX: &normalX, outY: &normalY) || padStackContact || butterflyContact
            let shooterDeking = (p.shooter?.dekeTimer ?? 0) > 0 && g.goalieAction == .none

            if contact && p.shot && (shooterDeking || (!w.isHuman(team.id) && (p.leaking || rng.nextFloat() < ai.goalieLeak))) {
                // Beaten cleanly or goalie frozen by deke move
                p.leaking = true
                p.x = beforeX
                p.y = beforeY
                continue
            }
            if !contact && p.leaking { p.leaking = false }
            let smother = !contact && p.speed < 12 && hypot(g.x - p.x, g.y - p.y) < g.radius + 1.8
            if !contact && !smother { continue }
            let opp = w.opponent(team.id)
            let onGoal = p.shot || p.speed > 25
            if onGoal {
                opp.shots += 1
                w.events.append(.save)
                addMomentum(team.id, 1)
            }
            if w.isShootout {
                p.carrier = g
                p.shot = false
                p.vx = 0; p.vy = 0
                recordShootoutAttempt(false)
                return true
            }
            let cover = smother || p.speed < 15 || rng.nextFloat() < ai.coverChance
            if cover {
                p.carrier = g
                p.shot = false
                p.vx = 0; p.vy = 0
                let fx = team.ownGoalX + team.attackDir * (Rink.GOAL_LINE_X - Rink.END_DOT_X)
                let fy: Float = abs(p.y) < 0.5 ? (rng.nextBool() ? Rink.DOT_Y : -Rink.DOT_Y) : sign(p.y) * Rink.DOT_Y
                w.showBanner(onGoal ? "SAVE!" : "COVERED", String(g.number) + " " + team.info.name, 1.2)
                startWhistle(fx, fy)
                return true
            }
            // Rebound: reflect off the pads and kick it toward open ice.
            let dot = p.vx * normalX + p.vy * normalY
            if dot < 0 {
                p.vx -= 2 * dot * normalX
                p.vy -= 2 * dot * normalY
            }
            p.vx *= 0.32
            p.vy *= 0.32
            p.vy += (rng.nextFloat() - 0.5) * 10
            if p.vx * team.attackDir < 6 { p.vx = team.attackDir * (6 + rng.nextFloat() * 10) }
            p.shot = false
            p.shooter = nil
            p.passTarget = nil
            return true
        }
        return false
    }

    private func tryPickups() {
        let w = world
        let p = w.puck
        var best: Skater?
        var bestD = Float.greatestFiniteMagnitude
        for s in w.allSkaters {
            let isShootoutShooterGoalie = w.isShootout && s.team == w.shootoutTurn && s.index == w.shootoutShooterIndex[w.shootoutTurn]
            if (s.isGoalie && !isShootoutShooterGoalie) || s.stunTimer > 0 || s.pickupCooldown > 0 || s.inPenaltyBox { continue }
            let bladeD = hypot(s.bladeX() - p.x, s.bladeY() - p.y)
            let bodyD = hypot(s.x - p.x, s.y - p.y) - s.radius + 0.5
            let d = min(bladeD, bodyD)
            let threshold: Float = s.pokeTimer > 0 ? 1.9 : 1.55
            let maxSpeed: Float = p.passTarget === s ? 95 : 72
            if d < threshold && p.speed < maxSpeed && d < bestD {
                bestD = d
                best = s
            }
        }
        if let best = best {
            if best.oneTimerArmed && p.passTarget === best {
                executeOneTimer(best)
            } else {
                best.oneTimerArmed = false
                takePossession(best)
            }
        }
    }

    func executeOneTimer(_ s: Skater) {
        let w = world
        let team = w.team(s.team)
        let goalX = team.targetGoalX
        let goalie = w.opponent(s.team).goalie
        let openSide: Float = abs(goalie.y) < 0.4 ? (rng.nextBool() ? 1 : -1) : -sign(goalie.y)
        let aimY = openSide * (1.8 + rng.nextFloat() * 1.6)
        var dx = goalX - w.puck.x
        var dy = aimY - w.puck.y
        let len = max(hypot(dx, dy), 0.01)
        dx /= len; dy /= len

        var speed = 112 + rng.nextFloat() * 18
        if w.isOnFire(s.team) { speed *= 1.18 }
        w.puck.carrier = nil
        w.puck.vx = dx * speed
        w.puck.vy = dy * speed
        w.puck.shot = true
        w.puck.shooter = s
        w.puck.passTarget = nil
        w.puck.leaking = false
        w.puck.lastTouchTeam = s.team
        s.pickupCooldown = 0.5
        s.swingTimer = 0.45
        s.facing = atan2(dy, dx)
        s.oneTimerArmed = false
        w.events.append(.oneTimer)
        w.events.append(.shot)
        addMomentum(s.team, 1)
    }

    func callPenalty(_ offender: Skater, foulName: String) {
        let w = world
        if w.penaltyTeam != -1 || offender.isGoalie { return }
        w.penaltyTeam = offender.team
        w.penaltyTimer = 40
        w.penaltyPlayerIndex = offender.index
        offender.inPenaltyBox = true
        offender.stunTimer = 0
        offender.pokeTimer = 0
        offender.checkTimer = 0
        offender.vx = 0; offender.vy = 0
        let boxY: Float = offender.team == 0 ? -44.5 : 44.5
        offender.place(0, boxY, 0)

        if w.controlled[offender.team] == offender.index {
            let next = w.teams[offender.team].skaters.first { !$0.isGoalie && !$0.inPenaltyBox }
            if let next = next { w.controlled[offender.team] = next.index }
        }

        w.showBanner("PENALTY - \(foulName)", "\(offender.number) \(offender.role.label) (2 MIN)", 2.0)
        w.events.append(.penalty)
        w.events.append(.whistle)

        let team = w.team(offender.team)
        let fx = team.ownGoalX + team.attackDir * (Rink.GOAL_LINE_X - Rink.END_DOT_X)
        let fy: Float = rng.nextBool() ? Rink.DOT_Y : -Rink.DOT_Y
        startWhistle(fx, fy)
    }

    func releasePenalty() {
        let w = world
        let t = w.penaltyTeam
        let idx = w.penaltyPlayerIndex
        if (0...1).contains(t), (0...5).contains(idx) {
            let s = w.teams[t].skaters[idx]
            s.inPenaltyBox = false
            s.place(0, t == 0 ? -38 : 38, 0)
        }
        w.penaltyTeam = -1
        w.penaltyTimer = 0
        w.penaltyPlayerIndex = -1
        w.showBanner("FULL STRENGTH", nil, 1.5)
    }

    func togglePullGoalie(_ teamId: Int) -> Bool {
        let w = world
        w.goaliePulled[teamId] = !w.goaliePulled[teamId]
        let pulled = w.goaliePulled[teamId]
        let team = w.team(teamId)
        let goalie = team.goalie
        if pulled {
            w.showBanner("GOALIE PULLED!", "\(team.info.abbr) EXTRA ATTACKER", 1.8)
            goalie.place(team.ownGoalX + team.attackDir * 35, (rng.nextFloat() - 0.5) * 20, 0)
        } else {
            w.showBanner("GOALIE IN NET", "\(team.info.abbr) NET PROTECTED", 1.5)
            let gx = team.ownGoalX + team.attackDir * 2.8
            goalie.place(gx, 0, team.attackDir > 0 ? 0 : .pi)
        }
        return pulled
    }

    // --------------------------------------------------------------- shootout

    func setupShootoutAttempt() {
        let w = world
        let shooterTeamId = w.shootoutTurn
        let defendingTeamId = 1 - shooterTeamId
        let shooterTeam = w.teams[shooterTeamId]
        let defendingTeam = w.teams[defendingTeamId]

        shooterTeam.attackDir = shooterTeamId == 0 ? 1 : -1
        defendingTeam.attackDir = -shooterTeam.attackDir

        w.puck.reset(0, 0)

        let shooterIdx = w.shootoutShooterIndex[shooterTeamId]
        let shooter = shooterTeam.skaters[shooterIdx]
        let goalie = defendingTeam.goalie

        shooter.place(0, 0, shooterTeam.attackDir > 0 ? 0 : .pi)
        w.puck.carrier = shooter
        w.puck.lastTouchTeam = shooterTeamId

        let gx = defendingTeam.ownGoalX + defendingTeam.attackDir * 2.8
        goalie.place(gx, 0, defendingTeam.attackDir > 0 ? 0 : .pi)

        for s in w.allSkaters {
            if s !== shooter && s !== goalie {
                s.place(0, 250, 0)
            }
        }

        if w.isHuman(shooterTeamId) {
            w.controlled[shooterTeamId] = shooter.index
        } else {
            w.controlled[shooterTeamId] = -1
        }
        if w.isHuman(defendingTeamId) {
            w.controlled[defendingTeamId] = goalie.index
        } else {
            w.controlled[defendingTeamId] = -1
        }

        w.shootoutTimer = 15
        w.clock = 15
        w.phase = .play
        let roleStr = shooterRoleString(shooter.role)
        w.showBanner("ROUND \(w.shootoutRound)", "\(shooterTeam.info.abbr) SHOOTER: \(roleStr) #\(shooter.number)", 1.6)
        w.events.append(.whistle)
    }

    func cycleShootoutShooter(_ teamId: Int) {
        let w = world
        guard w.isShootout, w.shootoutTurn == teamId, w.phase == .play else { return }
        let shooterTeam = w.teams[teamId]
        w.shootoutShooterIndex[teamId] = (w.shootoutShooterIndex[teamId] + 1) % shooterTeam.skaters.count
        let shooterIdx = w.shootoutShooterIndex[teamId]
        let shooter = shooterTeam.skaters[shooterIdx]
        let defendingTeam = w.teams[1 - teamId]
        let goalie = defendingTeam.goalie

        w.puck.reset(0, 0)
        shooter.place(0, 0, shooterTeam.attackDir > 0 ? 0 : .pi)
        w.puck.carrier = shooter
        w.puck.lastTouchTeam = teamId

        for s in w.allSkaters {
            if s !== shooter && s !== goalie {
                s.place(0, 250, 0)
            }
        }

        if w.isHuman(teamId) {
            w.controlled[teamId] = shooter.index
        }
        let roleStr = shooterRoleString(shooter.role)
        w.showBanner("SHOOTER: \(roleStr) #\(shooter.number)", shooterTeam.info.fullName.uppercased(), 1.4)
        w.events.append(.whistle)
    }

    private func shooterRoleString(_ role: Role) -> String {
        switch role {
        case .c: return "CENTER"
        case .lw: return "LEFT WING"
        case .rw: return "RIGHT WING"
        case .ld: return "LEFT DEFENSE"
        case .rd: return "RIGHT DEFENSE"
        case .g: return "GOALIE"
        }
    }

    private func recordShootoutAttempt(_ goal: Bool) {
        let w = world
        guard w.phase == .play else { return }
        let shooterTeamId = w.shootoutTurn
        let roundIdx = min(max(w.shootoutRound - 1, 0), w.shootoutAttempts[shooterTeamId].count - 1)
        w.shootoutAttempts[shooterTeamId][roundIdx] = goal ? 1 : 2

        if goal {
            w.events.append(.goal)
            w.events.append(.horn)
            w.showBanner("GOAL!", "\(w.teams[shooterTeamId].info.fullName.uppercased()) SCORES!", 2.2)
        } else {
            w.events.append(.whistle)
            w.showBanner("NO GOAL", "ATTEMPT OVER", 1.8)
        }

        w.puck.carrier = nil
        w.puck.vx = 0; w.puck.vy = 0
        w.phase = .whistle
        w.phaseTimer = 2.0
    }

    private func advanceShootout() {
        let w = world
        let t0Goals = w.shootoutAttempts[0].filter { $0 == 1 }.count
        let t1Goals = w.shootoutAttempts[1].filter { $0 == 1 }.count

        if w.shootoutTurn == 0 {
            let t1Remaining = w.shootoutRound <= 5 ? (5 - w.shootoutRound + 1) : 1
            if w.shootoutRound <= 5 && t0Goals > t1Goals + t1Remaining {
                shootoutGameOver()
                return
            }
            w.shootoutTurn = 1
            w.shootoutShooterIndex[1] = (w.shootoutRound - 1) % w.teams[1].skaters.count
            setupShootoutAttempt()
        } else {
            let t0Remaining = w.shootoutRound < 5 ? (5 - w.shootoutRound) : 0
            let t1Remaining = w.shootoutRound < 5 ? (5 - w.shootoutRound) : 0

            if w.shootoutRound < 5 {
                if t0Goals > t1Goals + t1Remaining || t1Goals > t0Goals + t0Remaining {
                    shootoutGameOver()
                    return
                }
                w.shootoutRound += 1
                w.shootoutTurn = 0
                w.shootoutShooterIndex[0] = (w.shootoutRound - 1) % w.teams[0].skaters.count
                setupShootoutAttempt()
            } else {
                if t0Goals != t1Goals {
                    shootoutGameOver()
                } else {
                    w.shootoutRound += 1
                    w.shootoutTurn = 0
                    w.shootoutShooterIndex[0] = (w.shootoutRound - 1) % w.teams[0].skaters.count
                    setupShootoutAttempt()
                    w.showBanner("SUDDEN DEATH", "Round \(w.shootoutRound) • Next Lead Wins!", 2.0)
                }
            }
        }
    }

    private func shootoutGameOver() {
        let w = world
        w.phase = .gameOver
        w.shootoutOver = true
        let winner = w.teams[0].score > w.teams[1].score ? w.teams[0] : w.teams[1]
        w.showBanner("SHOOTOUT VICTORY!", "\(winner.info.fullName.uppercased()) WIN \(w.teams[0].score) - \(w.teams[1].score)", 9999)
        w.events.append(.horn)
        w.events.append(.gameOver)
    }
}
