import Foundation

/// Drives every skater that a human isn't controlling, plus both goalies.
/// Positional logic is written in each team's "attack frame" (+ax toward the
/// net being attacked) so the same code serves both ends of the rink.
final class AIController {
    private enum Situation { case own, opp, loose }

    private let ai: AiSettings
    private let rng: SeededRandom

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
        } else if carrier!.team == team.id {
            situation = .own
        } else {
            situation = .opp
        }
        let humanIdx = world.controlled[team.id]

        switch situation {
        case .own:
            if carrier === s {
                carrierBehaviour(world, team, s, sim)
            } else {
                let ax = team.toAttackX(puck.x)
                let (tx, ty) = offensiveSpot(s.role, ax, puck.y)
                setTarget(team, s, tx, ty)
            }
        case .opp:
            let c = carrier!
            let chaser = pickChaser(team, c.x + c.vx * 0.2, c.y + c.vy * 0.2, humanIdx)
            if chaser === s {
                s.aiChaser = true
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
                let (tx, ty) = defensiveSpot(s.role, cax, c.y)
                setTarget(team, s, tx, ty)
            }
        case .loose:
            let px = min(max(puck.x + puck.vx * 0.35, -97), 97)
            let py = min(max(puck.y + puck.vy * 0.35, -40), 40)
            let chasers = pickChasers(team, px, py, humanIdx, 2)
            if chasers.contains(where: { $0 === s }) {
                s.aiChaser = true
                setWorldTarget(s, px, py)
            } else {
                s.aiChaser = false
                let (tx, ty) = offensiveSpot(s.role, team.toAttackX(px), py)
                setTarget(team, s, tx, ty)
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

    private func pickChasers(_ team: Team, _ x: Float, _ y: Float, _ humanIdx: Int, _ count: Int) -> [Skater] {
        let sorted = team.skaters
            .filter { !$0.isGoalie && $0.index != humanIdx && $0.stunTimer <= 0 }
            .sorted { a, b in
                (a.distanceTo(x, y) - (a.aiChaser ? 3.5 : 0)) < (b.distanceTo(x, y) - (b.aiChaser ? 3.5 : 0))
            }
        return Array(sorted.prefix(count))
    }

    /// Attack-frame spot for a supporting skater while the team has (or is chasing) the puck.
    private func offensiveSpot(_ role: Role, _ pax: Float, _ pay: Float) -> (Float, Float) {
        let inZone = pax > Rink.BLUE_LINE_X
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
            let p = ai.shootTendency * (0.2 + 0.8 * closeness) * (0.45 + 0.55 * angleQuality)
            if rng.nextFloat() < p {
                let corner = (1.4 + rng.nextFloat() * 1.2) * (rng.nextBool() ? 1 : -1)
                sim.shoot(s, power: 0.55 + rng.nextFloat() * 0.45, aimY: corner, accuracy: ai.shotAccuracy)
                return
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

    // ---------------------------------------------------------------- goalies

    func updateGoalie(_ world: World, _ team: Team, _ g: Skater, skill: Float, padScale: Float, dt: Float) {
        g.padScale = padScale
        let puck = world.puck
        let a = team.attackDir
        let gx = team.ownGoalX
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
        let pd = hypot(puck.x - g.x, puck.y - g.y)
        let carrier = puck.carrier
        g.butterfly = pd < 18 && (puck.shot || puck.speed > 30 || (carrier != nil && carrier!.team != team.id && pd < 12))
    }
}
