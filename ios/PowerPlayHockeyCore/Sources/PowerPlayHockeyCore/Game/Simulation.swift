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
        setupFaceoff(0, 0)
        world.showBanner(world.periodText() + " PERIOD", world.teams[0].info.fullName + " vs " + world.teams[1].info.fullName, 2.2)
    }

    /// Advances the match by [dt] seconds. [inputs] holds one entry per team; nil = AI.
    func step(dt: Float, inputs: [PlayerInput?]) {
        let w = world
        w.events.removeAll()
        if w.bannerTimer > 0 {
            w.bannerTimer -= dt
            if w.bannerTimer <= 0 { w.banner = nil; w.bannerSub = nil }
        }
        for t in 0...1 { w.switchLock[t] = max(0, w.switchLock[t] - dt) }
        for s in w.allSkaters {
            s.stunTimer = max(0, s.stunTimer - dt)
            s.pokeTimer = max(0, s.pokeTimer - dt)
            s.checkTimer = max(0, s.checkTimer - dt)
            s.actionCooldown = max(0, s.actionCooldown - dt)
            s.pickupCooldown = max(0, s.pickupCooldown - dt)
            s.swingTimer = max(0, s.swingTimer - dt)
        }

        switch w.phase {
        case .faceoff:
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 { dropPuck(inputs) }
        case .whistle:
            coastAll(dt)
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 { setupFaceoff(w.faceoffX, w.faceoffY) }
        case .goal:
            coastAll(dt)
            w.phaseTimer -= dt
            if w.phaseTimer <= 0 {
                if w.overtime { gameOver() } else { setupFaceoff(0, 0) }
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
        let scorer = w.teams.first { Int($0.attackDir) == end }!
        scorer.score += 1
        scorer.shots += 1
        w.puck.vx = 0; w.puck.vy = 0
        w.puck.carrier = nil
        w.puck.shot = false
        w.phase = .goal
        w.phaseTimer = Simulation.GOAL_HOLD
        w.showBanner("GOAL!", scorer.info.fullName.uppercased(), Simulation.GOAL_HOLD)
        w.events.append(.goal)
        w.events.append(.horn)
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
        setupFaceoff(0, 0)
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
        if w.overtime {
            w.clock += dt
        } else {
            w.clock -= dt
            if w.clock <= 0 { endPeriod(); return }
        }

        for t in 0...1 {
            if let input = inputs[t], w.isHuman(t) { updateControl(t, input) }
        }

        for team in w.teams {
            let humanIdx = w.controlled[team.id]
            let speedMul: Float = w.isHuman(team.id) ? 1 : ai.speedMul
            let goalieSkill: Float = w.isHuman(team.id) ? Simulation.HUMAN_GOALIE_SKILL : ai.goalieSkill
            let padScale: Float = w.isHuman(team.id) ? 1 : ai.goaliePadScale
            for s in team.skaters {
                if s.isGoalie {
                    aiController.updateGoalie(w, team, s, skill: goalieSkill, padScale: padScale, dt: dt)
                } else if s.index == humanIdx {
                    let input = inputs[team.id]
                    var mx = input?.moveX ?? 0
                    var my = input?.moveY ?? 0
                    let mag = hypot(mx, my)
                    if mag > 1 { mx /= mag; my /= mag }
                    PhysicsEngine.moveSkater(s, desiredVx: mx * Skater.MAX_SPEED, desiredVy: my * Skater.MAX_SPEED, maxSpeed: Skater.MAX_SPEED, dt: dt)
                } else {
                    aiController.updateSkater(w, team, s, speedMul: speedMul, dt: dt, sim: self)
                }
            }
        }

        for t in 0...1 {
            if let input = inputs[t], w.isHuman(t) { handleActions(t, input) }
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
                if s.isGoalie || s === cur { continue }
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
            if s.isGoalie || s.stunTimer > 0 { continue }
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
            if input.shootRelease { startPoke(s) }
            if input.hit { startHit(s, nil) }
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
        let speed = 58 + 68 * min(max(power, 0), 1)
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
            if m === s || m.isGoalie || m.stunTimer > 0 { continue }
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
                if o.isGoalie { continue }
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
            knockDown(victim, checker, 1.2)
            checker.checkTimer = 0
            checker.vx *= 0.4
            checker.vy *= 0.4
            world.events.append(.hit)
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
            loosePuck(victim, by.vx / d * 9 + (rng.nextFloat() - 0.5) * 6, by.vy / d * 9 + (rng.nextFloat() - 0.5) * 6, 0.6)
        }
    }

    private func loosePuck(_ from: Skater, _ vx: Float, _ vy: Float, _ cooldown: Float) {
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
                    loosePuck(carrier, o.vx * 0.5 + (p.x - o.x) * 3, o.vy * 0.5 + (p.y - o.y) * 3, 0.4)
                    return
                }
                continue
            }
            let bx = o.bladeX()
            let by = o.bladeY()
            let bladeD = hypot(bx - p.x, by - p.y)
            if o.pokeTimer > 0 && bladeD < 1.7 {
                loosePuck(carrier, cos(o.facing) * 15 + o.vx * 0.3, sin(o.facing) * 15 + o.vy * 0.3, 0.55)
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
                loosePuck(carrier, o.vx * 0.5 + (p.x - o.x) * 4, o.vy * 0.5 + (p.y - o.y) * 4, 0.35)
                return
            }
        }
    }

    /// Returns true when a goalie dealt with the puck this tick.
    private func handleGoalies() -> Bool {
        let w = world
        let p = w.puck
        for team in w.teams {
            let g = team.goalie
            let beforeX = p.x
            let beforeY = p.y
            let contact = PhysicsEngine.goalieContact(g, p, outX: &normalX, outY: &normalY)
            if contact && p.shot && !w.isHuman(team.id) && (p.leaking || rng.nextFloat() < ai.goalieLeak) {
                // Beaten cleanly: the puck slips through the AI goalie until it is clear of the pads.
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
            if s.isGoalie || s.stunTimer > 0 || s.pickupCooldown > 0 { continue }
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
        if let best = best { takePossession(best) }
    }
}
