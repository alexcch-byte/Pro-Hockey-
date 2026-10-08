import Foundation

/// Drives every skater that a human isn't controlling, plus both goalies.
/// Positional logic is written in each team's "attack frame" (+ax toward the
/// net being attacked) so the same code serves both ends of the rink.
final class AIController {

    private final class Pt { var x: Float = 0; var y: Float = 0 }
    private let leadPuck = Pt()

    private enum Situation { case own, opp, loose }

    private static let MARK_GOALSIDE_FT: Float = 3
    private static let NET_CRASH_FT: Float = 20
    private static let HANG_ZONE_FT: Float = 55

    private let ai: AiSettings
    private let rng: SeededRandom

    private var chaserA: Skater?
    private var chaserB: Skater?

    init(ai: AiSettings, rng: SeededRandom) {
        self.ai = ai
        self.rng = rng
    }

    // ---------------------------------------------------------------- skaters

    func updateSkater(_ world: World, _ team: Team, _ s: Skater, speedMul: Float, dt: Float, sim: Simulation) {
        s.aiTimer -= dt
        if s.aiTimer <= 0 {
            s.aiTimer = ai.reaction * (0.7 + rng.nextFloat() * 0.6)
            think(world, team, s, sim)
        }
        let dx = s.aiTargetX - s.x
        let dy = s.aiTargetY - s.y
        let d = hypot(dx, dy)
        let maxSpeed = Skater.MAX_SPEED * speedMul
        if d < 0.6 {
            PhysicsEngine.moveSkater(s, desiredVx: 0, desiredVy: 0, maxSpeed: maxSpeed, dt: dt)
        } else {
            let arrive = min(1, d / 7 + 0.25)
            let sp = maxSpeed * arrive
            PhysicsEngine.moveSkater(s, desiredVx: dx / d * sp, desiredVy: dy / d * sp, maxSpeed: maxSpeed, dt: dt)
        }
    }

    private func think(_ world: World, _ team: Team, _ s: Skater, _ sim: Simulation) {
        let puck = world.puck
        let carrier = puck.carrier
        let situation: Situation
        if carrier == nil {
            situation = .loose
        } else if carrier?.team == team.id {
            situation = .own
        } else {
            situation = .opp
        }
        let humanIdx = world.controlled[team.id]

        switch situation {
        case .own:
            if carrier === s {
                s.markId = -1
                carrierBehaviour(world, team, s, sim)
            } else if guardHanger(world, team, s, puck: puck) {
                // One defenceman stays home on an opponent loitering near our net.
            } else {
                let ax = team.toAttackX(puck.x)
                let isPP = world.penaltyTeam == world.opponent(team.id).id
                let (tx, ty) = offensiveSpot(s.role, pax: ax, pay: puck.y, powerPlay: isPP)
                setTarget(team, s, tx, ty)
            }
        case .opp:
            guard let c = carrier else { break }
            let chaser = pickChaser(team, c.x + c.vx * 0.2, c.y + c.vy * 0.2, humanIdx)
            if chaser === s {
                s.aiChaser = true
                s.markId = -1
                setWorldTarget(s, c.x + c.vx * 0.25, c.y + c.vy * 0.25)
                let d = s.distanceTo(c.x, c.y)
                if d < 6.5 && s.actionCooldown <= 0 {
                    let roll = rng.nextFloat()
                    if roll < ai.hitChance { sim.startHit(s, c) }
                    else if roll < ai.hitChance + ai.pokeChance { sim.startPoke(s) }
                }
            } else {
                s.aiChaser = false
                let cax = team.toAttackX(c.x)
                // Penalty kill: a man down, stay in the zone formation (box) instead of chasing marks.
                if world.penaltyTeam == team.id || !assignMark(world, team, s, carrier: c) {
                    s.markId = -1
                    let (tx, ty) = defensiveSpot(s.role, cax, c.y)
                    setTarget(team, s, tx, ty)
                }
            }
        case .loose:
            if puck.passTarget === s && team.toAttackX(s.x) > -10 && rng.nextFloat() < ai.shootTendency * 0.75 {
                s.oneTimerArmed = true
            }
            let px = min(max(puck.x + puck.vx * 0.35, -97), 97)
            let py = min(max(puck.y + puck.vy * 0.35, -40), 40)
            let netCrash = team.toAttackX(px) > Rink.GOAL_LINE_X - Self.NET_CRASH_FT
            pickChasers(team, px, py, humanIdx, netCrash: netCrash)
            if s === chaserA || s === chaserB {
                s.aiChaser = true
                s.markId = -1
                setWorldTarget(s, px, py)
            } else {
                s.aiChaser = false
                if !guardHanger(world, team, s, puck: puck) {
                    let isPP = world.penaltyTeam == world.opponent(team.id).id
                    let (tx, ty) = offensiveSpot(s.role, pax: team.toAttackX(px), pay: py, powerPlay: isPP)
                    setTarget(team, s, tx, ty)
                }
            }
        }
        // Wingers stay clear of the net's frame.
        if abs(s.aiTargetX) > Rink.GOAL_LINE_X - 1.5 && abs(s.aiTargetY) < 5 {
            s.aiTargetY = s.aiTargetY < 0 ? -6 : 6
        }
        s.aiTargetX = min(max(s.aiTargetX, -96), 96)
        s.aiTargetY = min(max(s.aiTargetY, -39), 39)
    }

    private func setTarget(_ team: Team, _ s: Skater, _ ax: Float, _ ay: Float) {
        s.aiTargetX = team.toWorldX(ax)
        s.aiTargetY = ay
    }

    private func setWorldTarget(_ s: Skater, _ x: Float, _ y: Float) {
        s.aiTargetX = x
        s.aiTargetY = y
    }

    private func pickChaser(_ team: Team, _ x: Float, _ y: Float, _ humanIdx: Int) -> Skater? {
        var best: Skater?
        var bestD = Float.greatestFiniteMagnitude
        for s in team.skaters {
            if s.isGoalie || s.index == humanIdx || s.stunTimer > 0 { continue }
            let d = s.distanceTo(x, y) - (s.aiChaser ? 3.5 : 0)
            if d < bestD { bestD = d; best = s }
        }
        return best
    }

    /// Fills chaserA / chaserB with the two best loose-puck chasers (no allocation).
    /// Near the opposing net (netCrash) the first chaser is the nearest forward and
    /// defencemen are penalised for the second, so one D stays back.
    private func pickChasers(_ team: Team, _ x: Float, _ y: Float, _ humanIdx: Int, netCrash: Bool) {
        var a: Skater?
        var aD = Float.greatestFiniteMagnitude
        if netCrash {
            for s in team.skaters {
                if s.isGoalie || s.role == .ld || s.role == .rd || s.index == humanIdx || s.stunTimer > 0 { continue }
                let d = s.distanceTo(x, y) - (s.aiChaser ? 3.5 : 0)
                if d < aD { aD = d; a = s }
            }
        }
        var b: Skater?
        var bD = Float.greatestFiniteMagnitude
        let aFixed = (a != nil) // net-crash forward already chosen as A
        for s in team.skaters {
            if aFixed && s === a { continue }
            if s.isGoalie || s.index == humanIdx || s.stunTimer > 0 { continue }
            var d = s.distanceTo(x, y) - (s.aiChaser ? 3.5 : 0)
            if netCrash && (s.role == .ld || s.role == .rd) { d += 12 }
            if aFixed {
                if d < bD { bD = d; b = s }
            } else if a == nil || d < aD {
                b = a; bD = aD; a = s; aD = d
            } else if d < bD {
                bD = d; b = s
            }
        }
        chaserA = a
        chaserB = b
    }

    /// Man-to-man: marks the nearest unmarked attacker inside our defensive zone and heads for
    /// a point MARK_GOALSIDE_FT goal-side of him. Defencemen weight slot attackers first.
    /// Returns false when there is nobody to cover (caller falls back to zone spots).
    private func assignMark(_ world: World, _ team: Team, _ s: Skater, carrier: Skater) -> Bool {
        let opp = world.opponent(team.id)
        var best: Skater?
        var bestScore = Float.greatestFiniteMagnitude
        let isD = s.role == .ld || s.role == .rd
        for o in opp.skaters {
            if o.isGoalie || o === carrier || o.inPenaltyBox || o.stunTimer > 0 { continue }
            let oax = team.toAttackX(o.x)
            if oax > -Rink.BLUE_LINE_X { continue } // only attackers already in (or entering) our zone
            // Claimed by a teammate who is still covering him?
            var taken = false
            for t in team.skaters {
                if t === s || t.isGoalie || t.aiChaser || t.inPenaltyBox || t.stunTimer > 0 { continue }
                if world.isHuman(team.id) && t.index == world.controlled[team.id] { continue }
                if t.markId == o.index { taken = true; break }
            }
            if taken { continue }
            let inSlot = oax < -50 && abs(o.y) < 13
            var score = s.distanceTo(o.x, o.y)
            if inSlot { score += isD ? -10 : 10 }
            if s.markId == o.index { score -= 6 } // stickiness
            if score < bestScore { bestScore = score; best = o }
        }
        guard let m = best else { return false }
        s.markId = m.index
        let gx = team.ownGoalX
        let dx = gx - m.x
        let dy = -m.y
        let d = max(hypot(dx, dy), 0.01)
        setWorldTarget(s, m.x + dx / d * Self.MARK_GOALSIDE_FT, m.y + dy / d * Self.MARK_GOALSIDE_FT)
        return true
    }

    /// Anti goal-hanging: while we have (or are chasing) the puck, the defenceman nearest to an
    /// opposing skater loitering deep in our zone (and well behind the puck) stays goal-side of him.
    /// Only one defenceman does this, so the attack keeps its numbers. Returns true if [s] took the job.
    private func guardHanger(_ world: World, _ team: Team, _ s: Skater, puck: Puck) -> Bool {
        let wasGuarding = s.markId >= 0
        if guardHangerInner(world, team, s, puck: puck, wasGuarding: wasGuarding) { return true }
        s.markId = -1
        return false
    }

    private func guardHangerInner(_ world: World, _ team: Team, _ s: Skater, puck: Puck, wasGuarding: Bool) -> Bool {
        if s.role != .ld && s.role != .rd { return false }
        if s.inPenaltyBox || s.stunTimer > 0 { return false }
        let pax = team.toAttackX(puck.x)
        if pax < -Rink.BLUE_LINE_X { return false } // puck is back in our zone anyway; normal defence applies
        let opp = world.opponent(team.id)
        let limit = wasGuarding ? -(Self.HANG_ZONE_FT - 7) : -Self.HANG_ZONE_FT // enter at -55, leave at -48
        var hanger: Skater?
        var hx: Float = 0
        for o in opp.skaters {
            if o.isGoalie || o.inPenaltyBox { continue }
            let oax = team.toAttackX(o.x)
            if oax > limit { continue }
            if hanger == nil || oax < hx { hanger = o; hx = oax }
        }
        guard let h = hanger else { return false }
        // Only defencemen who could actually take the job compete: not the carrier, a loose-puck chaser,
        // a stunned/boxed D, or the human-controlled skater.
        let humanTeam = world.isHuman(team.id)
        let carrier = puck.carrier
        for t in team.skaters {
            if t === s || t.isGoalie || (t.role != .ld && t.role != .rd) { continue }
            if t.inPenaltyBox || t.stunTimer > 0 || t.aiChaser || t === carrier { continue }
            if humanTeam && t.index == world.controlled[team.id] { continue }
            if t.distanceTo(h.x, h.y) < s.distanceTo(h.x, h.y) { return false }
        }
        s.markId = h.index
        let dx = team.ownGoalX - h.x
        let dy = -h.y
        let d = max(hypot(dx, dy), 0.01)
        setWorldTarget(s, h.x + dx / d * Self.MARK_GOALSIDE_FT, h.y + dy / d * Self.MARK_GOALSIDE_FT)
        return true
    }

    /// Attack-frame spot for a supporting skater while the team has (or is chasing) the puck.
    private func offensiveSpot(_ role: Role, pax: Float, pay: Float, powerPlay: Bool = false) -> (Float, Float) {
        let inZone = pax > Rink.BLUE_LINE_X
        // Power play umbrella: D on the points, wings at the circles, centre in the slot.
        if powerPlay && inZone {
            switch role {
            case .c: return (60, 0)
            case .lw: return (68, -22)
            case .rw: return (68, 22)
            case .ld: return (40, -24)
            case .rd: return (40, 24)
            case .g: return (0, 0)
            }
        }
        switch role {
        case .c: return inZone ? (56, -pay * 0.4) : (pax + 6, pay * 0.3)
        case .lw: return inZone ? (74, -13) : (min(pax + 12, 23), -24)
        case .rw: return inZone ? (74, 13) : (min(pax + 12, 23), 24)
        case .ld: return inZone ? (36, -18) : (pax - 20, -14)
        case .rd: return inZone ? (36, 18) : (pax - 20, 14)
        case .g: return (0, 0)
        }
    }

    /// Attack-frame spot for a defender while the opponent carries the puck at (cax, cay).
    private func defensiveSpot(_ role: Role, _ cax: Float, _ cay: Float) -> (Float, Float) {
        let behindNet = cax < -Rink.GOAL_LINE_X
        switch role {
        case .c: return behindNet ? (-70, cay * 0.5) : (max(cax - 8, -80), cay * 0.6)
        case .lw: return (min(cax - 4, 15), -18)
        case .rw: return (min(cax - 4, 15), 18)
        case .ld: return behindNet ? (-80, -6) : (min(max(cax - 16, -82), 5), -9 + cay * 0.25)
        case .rd: return behindNet ? (-80, 6) : (min(max(cax - 16, -82), 5), 9 + cay * 0.25)
        case .g: return (0, 0)
        }
    }

    private func carrierBehaviour(_ world: World, _ team: Team, _ s: Skater, _ sim: Simulation) {
        let sax = team.toAttackX(s.x)
        let say = s.y
        let goalDx = Rink.GOAL_LINE_X - sax
        let dist = hypot(goalDx, say)
        let opp = world.opponent(team.id)

        // Nearest opponent in front of us.
        var pressure: Skater?
        var pressureD: Float = 7
        let fx = cos(s.facing) * team.attackDir
        let fy = sin(s.facing)
        for o in opp.skaters {
            if o.isGoalie { continue }
            let d = s.distanceTo(o.x, o.y)
            if d < pressureD {
                let ox = team.toAttackX(o.x) - sax
                let oy = o.y - say
                if ox * fx + oy * fy > -1 { pressureD = d; pressure = o }
            }
        }

        // Shooting.
        let angleQuality = 1 - min(max(abs(say) / 40, 0), 1)
        if dist < 44 && sax < Rink.GOAL_LINE_X - 2 {
            let closeness = 1 - dist / 44
            var p = ai.shootTendency * (0.2 + 0.8 * closeness) * (0.45 + 0.55 * angleQuality)
            if world.penaltyTeam == world.opponent(team.id).id { p *= 1.3 } // power play: shoot more
            if rng.nextFloat() < p {
                let corner = (1.4 + rng.nextFloat() * 1.2) * (rng.nextBool() ? 1 : -1)
                sim.shoot(s, power: 0.55 + rng.nextFloat() * 0.45, aimY: corner, accuracy: ai.shotAccuracy)
                return
            }
        }

        // Seam pass: in the offensive zone, move the puck to an open teammate in a clearly better shooting spot.
        if sax > Rink.BLUE_LINE_X && s.seamCooldown <= 0 {
            s.seamCooldown = 0.6
            if rng.nextFloat() < ai.seamPass {
                let ownQ = shotQuality(sax, say)
                if ownQ < 0.5 {
                    var target: Skater?
                    var bestQ = ownQ + 0.2
                    for m in team.skaters {
                        if m === s || m.isGoalie || m.stunTimer > 0 || m.inPenaltyBox { continue }
                        let q = shotQuality(team.toAttackX(m.x), m.y)
                        if q <= bestQ { continue }
                        var open = true
                        for o in opp.skaters {
                            if !o.isGoalie && o.distanceTo(m.x, m.y) < 5 { open = false; break }
                        }
                        if open { bestQ = q; target = m }
                    }
                    if let target = target, sim.passTo(s, target: target, accuracy: ai.passAccuracy, human: false, checkLane: true) {
                        return
                    }
                }
            }
        }

        // Passing when pressured, or occasionally to move the puck up ice.
        let wantPass = (pressure != nil && rng.nextFloat() < 0.75) || (dist > 55 && rng.nextFloat() < 0.15)
        if wantPass && sim.pass(s, mx: 0, my: 0, accuracy: ai.passAccuracy, human: false, checkLane: true) { return }

        // Skate toward the slot, steering around pressure.
        var tx: Float = 78
        var ty: Float = say * 0.35
        if let pressure = pressure {
            let side: Float = pressure.y > say ? -1 : 1
            tx = sax + 12
            ty = say + side * 11
        }
        if sax > 76 && abs(say) > 7 {
            // Too deep with a bad angle: curl back toward the slot.
            tx = 62
            ty = say * 0.4
        }
        setTarget(team, s, tx, ty)
    }

    /// 0..1 scoring chance from an attack-frame spot: closer to the net and nearer the slot is better.
    private func shotQuality(_ ax: Float, _ ay: Float) -> Float {
        let dist = hypot(Rink.GOAL_LINE_X - ax, ay)
        let closeness = min(max(1 - dist / 44, 0), 1)
        let angle = min(max(1 - abs(ay) / 40, 0), 1)
        return closeness * (0.45 + 0.55 * angle)
    }

    // ---------------------------------------------------------------- goalies

    func updateGoalie(_ world: World, _ team: Team, _ g: Skater, skill: Float, padScale: Float, leadSeconds: Float, dt: Float) {
        g.padScale = padScale
        let realPuck = world.puck
        let a = team.attackDir
        let gx = team.ownGoalX
        // Anticipate a puck in flight (shot or cross-ice pass) by leading it along its velocity.
        let lead = realPuck.carrier == nil ? leadSeconds * min(max((realPuck.speed - 15) / 20, 0), 1) : 0
        leadPuck.x = realPuck.x + realPuck.vx * lead
        leadPuck.y = realPuck.y + realPuck.vy * lead
        let puck = leadPuck
        let depthOfPuck = a * (puck.x - gx)
        let tx: Float
        let ty: Float
        if depthOfPuck < 0 {
            // Puck behind the goal line: hug the near post.
            tx = gx + a * 2.6
            ty = abs(puck.y) < 0.6 ? 0 : (puck.y > 0 ? 1 : -1) * Float(2.4)
        } else {
            let dx = puck.x - gx
            let dy = puck.y
            let d = max(hypot(dx, dy), 0.01)
            var depth: Float = 2.6 + min(max(d / 35, 0), 1) * 1.7
            depth *= 0.8 + 0.2 * skill
            var px = gx + dx / d * depth
            var py = dy / d * depth
            py = min(max(py, -3.2), 3.2)
            let dep = min(max(a * (px - gx), 2.5), 5.5)
            px = gx + a * dep
            tx = px
            ty = py
        }
        let maxSpeed = Skater.GOALIE_SPEED * skill
        let dx = tx - g.x
        let dy = ty - g.y
        let d = hypot(dx, dy)
        if d < 0.15 {
            PhysicsEngine.moveSkater(g, desiredVx: 0, desiredVy: 0, maxSpeed: maxSpeed, dt: dt)
        } else {
            let sp = min(maxSpeed, d * 9)
            PhysicsEngine.moveSkater(g, desiredVx: dx / d * sp, desiredVy: dy / d * sp, maxSpeed: maxSpeed, dt: dt)
        }
        // Goalies square up to the puck rather than facing where they skate.
        let want = atan2(puck.y - g.y, puck.x - g.x)
        g.facing = PhysicsEngine.turnToward(g.facing, want, dt * 12)
        let pd = hypot(realPuck.x - g.x, realPuck.y - g.y)
        let carrier = realPuck.carrier
        g.butterfly = pd < 18 && (realPuck.shot || realPuck.speed > 30 || (carrier != nil && carrier?.team != team.id && pd < 12))
    }
}
