import Foundation

enum Role {
    case c, lw, rw, ld, rd, g

    var label: String {
        switch self {
        case .c: return "C"
        case .lw: return "LW"
        case .rw: return "RW"
        case .ld: return "LD"
        case .rd: return "RD"
        case .g: return "G"
        }
    }
}

enum GoalieAction {
    case none
    case butterfly
    case padStack
}

/// One player on the ice - five skaters and a goalie per team. Pure state;
/// all behaviour lives in Simulation / AIController and drawing in Renderer.
final class Skater {
    static let STICK_REACH: Float = 2.6
    static let POKE_REACH: Float = 4.6
    static let MAX_SPEED: Float = 25      // ft/s
    static let GOALIE_SPEED: Float = 20

    let team: Int
    let index: Int
    let role: Role
    let number: Int

    var isGoalie: Bool { role == .g }

    var x: Float = 0
    var y: Float = 0
    var vx: Float = 0
    var vy: Float = 0
    /// Facing direction in radians (world frame).
    var facing: Float = 0

    let radius: Float

    /// > 0 while knocked down after a body check.
    var stunTimer: Float = 0
    /// > 0 while the stick is extended for a poke check.
    var pokeTimer: Float = 0
    /// > 0 while lunging into a body check.
    var checkTimer: Float = 0
    /// Generic cooldown between poke/hit attempts.
    var actionCooldown: Float = 0
    /// > 0 while performing a deke (lateral dodge/juke).
    var dekeTimer: Float = 0
    var dekeDir: Float = 1
    /// Can't re-collect the puck until this expires (after shooting / passing).
    var pickupCooldown: Float = 0
    /// Shot / pass animation.
    var swingTimer: Float = 0
    /// Advances with distance skated; drives the stride animation.
    var stride: Float = 0

    // AI scratch state
    var aiTimer: Float = 0
    var aiTargetX: Float = 0
    var aiTargetY: Float = 0
    var aiChaser: Bool = false

    // Goalie active save moves
    var butterfly: Bool = false
    var goalieAction: GoalieAction = .none
    var goalieActionTimer: Float = 0
    var padStackDir: Float = 1
    /// Multiplier on the goalie's blocking area (difficulty).
    var padScale: Float = 1

    // One-timer & penalty state
    var oneTimerArmed: Bool = false
    var inPenaltyBox: Bool = false
    var breathTimer: Float = 0

    // Network interpolation targets (client only)
    var netX: Float = 0
    var netY: Float = 0
    var netFacing: Float = 0

    init(team: Int, index: Int, role: Role, number: Int) {
        self.team = team
        self.index = index
        self.role = role
        self.number = number
        self.radius = role == .g ? 2.1 : 1.5
    }

    var speed: Float { hypot(vx, vy) }

    var stickReach: Float { radius + (pokeTimer > 0 ? Skater.POKE_REACH : Skater.STICK_REACH) }

    func bladeX() -> Float { x + cos(facing) * stickReach }
    func bladeY() -> Float { y + sin(facing) * stickReach }

    func distanceTo(_ px: Float, _ py: Float) -> Float { hypot(px - x, py - y) }

    func place(_ px: Float, _ py: Float, _ face: Float) {
        x = px; y = py; vx = 0; vy = 0
        facing = face
        stunTimer = 0; pokeTimer = 0; checkTimer = 0; dekeTimer = 0
        actionCooldown = 0; pickupCooldown = 0; swingTimer = 0
        aiTimer = 0; aiTargetX = px; aiTargetY = py; aiChaser = false
        butterfly = false; goalieAction = .none; goalieActionTimer = 0
        netX = px; netY = py; netFacing = face
    }
}
