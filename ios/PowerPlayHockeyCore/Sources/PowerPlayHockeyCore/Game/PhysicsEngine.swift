import Foundation

/// Arcade physics tuned for feel rather than realism: skater acceleration,
/// body collisions, puck glide, boards, posts and the nets.
enum PhysicsEngine {

    private static let PUCK_FRICTION: Float = 0.32     // fraction of speed lost per second
    private static let BOARD_RESTITUTION: Float = 0.62
    private static let POST_RESTITUTION: Float = 0.8
    private static let MAX_PUCK_SPEED: Float = 130

    /// Accelerates [s] toward the desired velocity and integrates. Facing turns
    /// toward the direction of travel while moving.
    static func moveSkater(_ s: Skater, desiredVx: Float, desiredVy: Float, maxSpeed: Float, dt: Float) {
        if s.stunTimer > 0 {
            let f = exp(-dt * 5)
            s.vx *= f; s.vy *= f
        } else {
            let responsiveness: Float = s.checkTimer > 0 ? 2 : 6
            let k = 1 - exp(-responsiveness * dt)
            s.vx += (desiredVx - s.vx) * k
            s.vy += (desiredVy - s.vy) * k
            let cap = maxSpeed * (s.checkTimer > 0 ? 1.35 : 1)
            let sp = s.speed
            if sp > cap { s.vx *= cap / sp; s.vy *= cap / sp }
        }
        s.x += s.vx * dt
        s.y += s.vy * dt

        let sp = s.speed
        if sp > 1.2 && s.stunTimer <= 0 {
            let want = atan2(s.vy, s.vx)
            s.facing = turnToward(s.facing, want, dt * (s.isGoalie ? 14 : 10))
            s.stride += sp * dt
        }
    }

    static func turnToward(_ current: Float, _ target: Float, _ maxStep: Float) -> Float {
        var diff = target - current
        while diff > .pi { diff -= 2 * .pi }
        while diff < -(.pi) { diff += 2 * .pi }
        let step = min(max(diff, -maxStep), maxStep)
        var out = current + step
        while out > .pi { out -= 2 * .pi }
        while out < -(.pi) { out += 2 * .pi }
        return out
    }

    /// Keeps skaters inside the boards and out of the nets.
    static func constrainSkater(_ s: Skater) {
        var px = s.x, py = s.y
        var nx: Float = 0, ny: Float = 0
        if Rink.containCircle(x: &px, y: &py, r: s.radius, normalX: &nx, normalY: &ny) {
            s.x = px; s.y = py
            let dot = s.vx * nx + s.vy * ny
            if dot < 0 {
                s.vx -= dot * nx
                s.vy -= dot * ny
            }
        }
        if !s.isGoalie {
            for e: Float in [-1, 1] {
                let left = Rink.GOAL_LINE_X - 0.3
                let right = Rink.GOAL_LINE_X + Rink.NET_DEPTH + 0.3
                let ax = s.x * e
                let halfW = Rink.NET_HALF_W + 0.2
                if ax + s.radius > left && ax - s.radius < right && abs(s.y) < halfW + s.radius {
                    // Push out along the axis of least penetration.
                    let penX = ax < (left + right) / 2 ? (ax + s.radius) - left : right - (ax - s.radius)
                    let penY = (halfW + s.radius) - abs(s.y)
                    if penX < penY {
                        let dir: Float = ax < (left + right) / 2 ? -1 : 1
                        s.x = (ax + dir * penX) * e
                        s.vx = 0
                    } else {
                        let sy: Float = s.y == 0 ? 1 : (s.y > 0 ? 1 : -1)
                        s.y = sy * (halfW + s.radius)
                        s.vy = 0
                    }
                }
            }
        }
    }

    /// Separates overlapping skaters and reports each contact with its closing
    /// speed so the simulation can turn hard contacts into body checks.
    static func resolveSkaterCollisions(_ skaters: [Skater], onContact: (Skater, Skater, Float) -> Void) {
        let n = skaters.count
        for i in 0..<n {
            let a = skaters[i]
            for j in (i + 1)..<n {
                let b = skaters[j]
                let dx = b.x - a.x
                let dy = b.y - a.y
                let minDist = a.radius + b.radius
                let distSq = dx * dx + dy * dy
                if distSq >= minDist * minDist { continue }
                let dist = sqrt(distSq)
                let nx: Float
                let ny: Float
                if dist > 0.0001 { nx = dx / dist; ny = dy / dist } else { nx = 1; ny = 0 }
                let overlap = minDist - dist
                let wa: Float = a.isGoalie ? 0.15 : 0.5
                let wb: Float = b.isGoalie ? 0.15 : 0.5
                let total = wa + wb
                a.x -= nx * overlap * (wa / total)
                a.y -= ny * overlap * (wa / total)
                b.x += nx * overlap * (wb / total)
                b.y += ny * overlap * (wb / total)

                let relVx = a.vx - b.vx
                let relVy = a.vy - b.vy
                let closing = relVx * nx + relVy * ny
                if closing > 0 {
                    // Soft inelastic bump so players do not glue together.
                    let impulse = closing * 0.55
                    a.vx -= impulse * nx * (wb / total) * 2
                    a.vy -= impulse * ny * (wb / total) * 2
                    b.vx += impulse * nx * (wa / total) * 2
                    b.vy += impulse * ny * (wa / total) * 2
                    onContact(a, b, closing)
                }
            }
        }
    }

    /// Integrates a loose puck. Returns +1 / -1 when it crossed the goal line
    /// from the front, between the posts, at that end of the rink; else 0.
    /// Uses the pre-step position for a swept test so a fast puck can never
    /// tunnel through the netting and register from behind or from the side.
    /// Emits .boards / .post events.
    @discardableResult
    static func updatePuck(_ puck: Puck, dt: Float, events: inout [GameEvent]) -> Int {
        let prevX = puck.x
        let prevY = puck.y
        puck.x += puck.vx * dt
        puck.y += puck.vy * dt

        let f = pow(1 - PUCK_FRICTION, dt)
        puck.vx *= f
        puck.vy *= f
        let sp = puck.speed
        if sp > MAX_PUCK_SPEED { puck.vx *= MAX_PUCK_SPEED / sp; puck.vy *= MAX_PUCK_SPEED / sp }

        let r = Rink.PUCK_R
        let backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        for e: Float in [-1, 1] {
            let gx = e * Rink.GOAL_LINE_X
            // Posts.
            for py: Float in [-Rink.GOAL_HALF_W, Rink.GOAL_HALF_W] {
                let dx = puck.x - gx
                let dy = puck.y - py
                let d = hypot(dx, dy)
                let minD = Rink.POST_R + r
                if d < minD && d > 0.0001 {
                    let nx = dx / d; let ny = dy / d
                    puck.x = gx + nx * minD
                    puck.y = py + ny * minD
                    let dot = puck.vx * nx + puck.vy * ny
                    if dot < 0 {
                        puck.vx -= (1 + POST_RESTITUTION) * dot * nx
                        puck.vy -= (1 + POST_RESTITUTION) * dot * ny
                        events.append(.post)
                    }
                }
            }

            let pax = prevX * e
            let nax = puck.x * e

            // A goal is only a goal when the puck crosses the goal line from
            // the front, and the crossing point lies between the posts.
            if pax <= Rink.GOAL_LINE_X && nax > Rink.GOAL_LINE_X {
                let t = min(max((Rink.GOAL_LINE_X - pax) / (nax - pax), 0), 1)
                let yCross = prevY + (puck.y - prevY) * t
                if abs(yCross) < Rink.GOAL_HALF_W - r * 0.3 {
                    return Int(e)
                }
                if abs(yCross) < Rink.NET_HALF_W + r {
                    // Into the goal frame beside a post: stays in front.
                    puck.x = e * (Rink.GOAL_LINE_X - r)
                    puck.vx = -puck.vx * 0.4
                    continue
                }
            }

            // Anything now inside the net box that did not enter legitimately
            // came through the side or back netting (or was carried in):
            // push it back out the way it came.
            if nax > Rink.GOAL_LINE_X && nax < backX + r && abs(puck.y) < Rink.NET_HALF_W + r {
                if pax >= backX {
                    puck.x = e * (backX + r)
                    if puck.vx * e < 0 { puck.vx = -puck.vx * 0.3 }
                } else if abs(prevY) >= Rink.NET_HALF_W + r * 0.5 {
                    let sy: Float = prevY == 0 ? 1 : (prevY > 0 ? 1 : -1)
                    puck.y = sy * (Rink.NET_HALF_W + r)
                    if puck.vy * sy < 0 { puck.vy = -puck.vy * 0.3 }
                } else if pax <= Rink.GOAL_LINE_X {
                    puck.x = e * (Rink.GOAL_LINE_X - r)
                    if puck.vx * e > 0 { puck.vx = -puck.vx * 0.4 }
                } else {
                    ejectFromNet(puck, Int(e))
                }
            }
        }

        var px = puck.x, py = puck.y
        var nx: Float = 0, ny: Float = 0
        if Rink.containCircle(x: &px, y: &py, r: r, normalX: &nx, normalY: &ny) {
            puck.x = px; puck.y = py
            let dot = puck.vx * nx + puck.vy * ny
            if dot < 0 {
                puck.vx -= (1 + BOARD_RESTITUTION) * dot * nx
                puck.vy -= (1 + BOARD_RESTITUTION) * dot * ny
                // A little tangential scrub along the boards.
                puck.vx *= 0.92
                puck.vy *= 0.92
                if abs(dot) > 12 { events.append(.boards) }
            }
        }
        return 0
    }

    /// Moves a puck that is somehow inside the net box at end [e] out through the nearest wall.
    private static func ejectFromNet(_ puck: Puck, _ e: Int) {
        let r = Rink.PUCK_R
        let ef = Float(e)
        let ax = puck.x * ef
        let backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        let penFront = ax - (Rink.GOAL_LINE_X - r)
        let penBack = (backX + r) - ax
        let penSide = (Rink.NET_HALF_W + r) - abs(puck.y)
        if penSide <= penFront && penSide <= penBack {
            let sy: Float = puck.y == 0 ? 1 : (puck.y > 0 ? 1 : -1)
            puck.y = sy * (Rink.NET_HALF_W + r)
            puck.vy = abs(puck.vy) * sy * 0.5
        } else if penBack <= penFront {
            puck.x = ef * (backX + r)
            puck.vx = abs(puck.vx) * ef * 0.5
        } else {
            puck.x = ef * (Rink.GOAL_LINE_X - r)
            puck.vx = -abs(puck.vx) * ef * 0.5
        }
    }

    /// Places a carried puck on the carrier's blade and matches its velocity.
    /// The puck is kept out of both net boxes, so a skater behind or beside
    /// the net cannot hold it inside the goal.
    static func carryPuck(_ puck: Puck, _ s: Skater, _ dt: Float) {
        let tx = s.x + cos(s.facing) * (s.radius + 1.9)
        let ty = s.y + sin(s.facing) * (s.radius + 1.9)
        let k = 1 - exp(-dt * 22)
        puck.x += (tx - puck.x) * k
        puck.y += (ty - puck.y) * k
        puck.vx = s.vx
        puck.vy = s.vy
        var px = puck.x, py = puck.y
        var nx: Float = 0, ny: Float = 0
        if Rink.containCircle(x: &px, y: &py, r: Rink.PUCK_R, normalX: &nx, normalY: &ny) {
            puck.x = px; puck.y = py
        }
        keepCarriedPuckOutOfNets(puck, s)
    }

    private static func keepCarriedPuckOutOfNets(_ puck: Puck, _ s: Skater) {
        let r = Rink.PUCK_R
        let backX = Rink.GOAL_LINE_X + Rink.NET_DEPTH
        for e: Float in [-1, 1] {
            let ax = puck.x * e
            if ax <= Rink.GOAL_LINE_X - r || ax >= backX + r || abs(puck.y) >= Rink.NET_HALF_W + r { continue }
            let sax = s.x * e
            if sax >= backX {
                puck.x = e * (backX + r)                          // carrier behind the net
            } else if abs(s.y) >= Rink.NET_HALF_W {
                let sy: Float = s.y == 0 ? 1 : (s.y > 0 ? 1 : -1)  // carrier beside the net
                puck.y = sy * (Rink.NET_HALF_W + r)
            } else if sax <= Rink.GOAL_LINE_X {
                puck.x = e * (Rink.GOAL_LINE_X - r)                // carrier in front: must shoot it in
            } else {
                ejectFromNet(puck, Int(e))
            }
        }
    }

    /// Goalie capsule: a pad-wide segment perpendicular to the goalie's facing.
    /// On contact the puck is pushed out and (outX, outY) receives the contact
    /// normal (pointing from the goalie toward the puck).
    @discardableResult
    static func goalieContact(_ g: Skater, _ puck: Puck, outX: inout Float, outY: inout Float) -> Bool {
        let half = (g.butterfly ? Float(2.9) : Float(1.9)) * g.padScale
        let thick = (g.butterfly ? Float(1.4) : Float(1.7)) * g.padScale
        let lx = -sin(g.facing)
        let ly = cos(g.facing)
        let px = puck.x - g.x
        let py = puck.y - g.y
        let t = min(max(px * lx + py * ly, -half), half)
        let cx = g.x + lx * t
        let cy = g.y + ly * t
        let dx = puck.x - cx
        let dy = puck.y - cy
        let d = hypot(dx, dy)
        let minD = thick + Rink.PUCK_R
        if d >= minD { return false }
        if d > 0.0001 { outX = dx / d; outY = dy / d } else { outX = cos(g.facing); outY = sin(g.facing) }
        puck.x = cx + outX * minD
        puck.y = cy + outY * minD
        return true
    }

    static func gaussian(_ rng: SeededRandom) -> Float {
        let u1 = max(rng.nextFloat(), 1e-6)
        let u2 = rng.nextFloat()
        return Float(sqrt(-2.0 * log(Double(u1))) * cos(2.0 * Double.pi * Double(u2)))
    }
}
