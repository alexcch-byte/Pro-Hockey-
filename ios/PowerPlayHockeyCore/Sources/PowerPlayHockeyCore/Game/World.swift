import Foundation

enum Phase {
    case faceoff, play, whistle, goal, periodEnd, gameOver
}

/// One-shot things that happened this tick; the view turns them into sounds / effects.
enum GameEvent {
    case shot, pass, boards, post, goal, hit, poke, save, whistle, horn, faceoffDrop, pickup, periodEnd, gameOver, faceoffSet
    case oneTimer, penalty, onFire, deke, glassShatter, goalieSaveMove
}

/// Per-frame command state from a human controller (touch or network).
final class PlayerInput {
    var moveX: Float = 0
    var moveY: Float = 0
    var shootHeld: Bool = false
    var shootRelease: Bool = false
    var shootCharge: Float = 0
    var pass: Bool = false
    var hit: Bool = false
    var deke: Bool = false

    var moveMagnitude: Float { hypot(moveX, moveY) }

    /// Clears edge-triggered buttons after the simulation consumed them.
    func clearPulses() {
        shootRelease = false
        pass = false
        hit = false
        deke = false
    }

    func copyFrom(_ o: PlayerInput) {
        moveX = o.moveX; moveY = o.moveY
        shootHeld = o.shootHeld; shootRelease = o.shootRelease; shootCharge = o.shootCharge
        pass = o.pass; hit = o.hit; deke = o.deke
    }
}

/// Difficulty knobs consumed by the AI and the goalies.
struct AiSettings {
    let speedMul: Float
    let reaction: Float         // seconds between AI decisions
    let shotAccuracy: Float     // 0..1
    let passAccuracy: Float
    let shootTendency: Float
    let pokeChance: Float       // per decision tick when in range
    let hitChance: Float
    let goalieSkill: Float      // scales the AI goalie's speed / positioning
    let coverChance: Float      // chance a save is frozen for a faceoff
    let faceoffHumanBias: Float // probability the human wins a draw
    let goalieLeak: Float       // chance an AI-goalie save lets the puck through (five-hole)
    let goaliePadScale: Float   // size of the AI goalie's blocking area
    let aimAssist: Float        // 0..1 how strongly human shots steer to the open side

    static func forDifficulty(_ d: AiDifficulty) -> AiSettings {
        switch d {
        case .easy:
            return AiSettings(
                speedMul: 0.72, reaction: 0.6, shotAccuracy: 0.5, passAccuracy: 0.65, shootTendency: 0.45,
                pokeChance: 0.18, hitChance: 0.06, goalieSkill: 0.5, coverChance: 0.15, faceoffHumanBias: 0.65,
                goalieLeak: 0.3, goaliePadScale: 0.75, aimAssist: 1
            )
        case .medium:
            return AiSettings(
                speedMul: 0.95, reaction: 0.28, shotAccuracy: 0.72, passAccuracy: 0.85, shootTendency: 0.7,
                pokeChance: 0.55, hitChance: 0.35, goalieSkill: 0.9, coverChance: 0.3, faceoffHumanBias: 0.5,
                goalieLeak: 0.1, goaliePadScale: 0.95, aimAssist: 0.5
            )
        case .hard:
            return AiSettings(
                speedMul: 1.06, reaction: 0.15, shotAccuracy: 0.88, passAccuracy: 0.95, shootTendency: 0.85,
                pokeChance: 0.75, hitChance: 0.55, goalieSkill: 1.05, coverChance: 0.35, faceoffHumanBias: 0.4,
                goalieLeak: 0, goaliePadScale: 1.05, aimAssist: 0
            )
        }
    }
}

/// Complete match state: both clubs, the puck, the clock and the flow phase.
/// The host simulates it; a WiFi client just mirrors it for rendering.
final class World {
    let teams: [Team]
    let puck = Puck()
    let periodLength: Int

    var period = 1
    var clock: Float
    var overtime = false

    var phase: Phase = .faceoff
    var phaseTimer: Float = 0
    var faceoffX: Float = 0
    var faceoffY: Float = 0

    var banner: String?
    var bannerSub: String?
    var bannerTimer: Float = 0

    /// Index of the skater each human team controls; -1 when the team is AI.
    var controlled: [Int] = [-1, -1]
    var isHumanTeam: [Bool] = [true, false]
    var switchLock: [Float] = [0, 0]
    /// Charge level of the human's held shot, for the HUD meter.
    var shotCharge: [Float] = [0, 0]

    // Power play / penalties (-1 = none)
    var penaltyTeam = -1
    var penaltyTimer: Float = 0
    var penaltyPlayerIndex = -1

    // Goalie pulled (extra attacker)
    var goaliePulled: [Bool] = [false, false]

    // "On Fire" Momentum System (0..3 momentum points; fireTimer > 0 means ON FIRE)
    var momentum: [Int] = [0, 0]
    var fireTimer: [Float] = [0, 0]
    func isOnFire(_ teamId: Int) -> Bool { (0...1).contains(teamId) && fireTimer[teamId] > 0 }

    // Shootout Mode (5 rounds + sudden death, 1-on-1 breakaways)
    var isShootout = false
    var shootoutRound = 1
    var shootoutTurn = 0           // 0 = Team 0, 1 = Team 1
    var shootoutTimer: Float = 15  // 15-second shot clock
    var shootoutOver = false
    var shootoutAttempts: [[Int]] = [Array(repeating: 0, count: 15), Array(repeating: 0, count: 15)]
    var shootoutShooterIndex: [Int] = [0, 0]

    // Arena & Environment (Indoor Stadium vs Outdoor Winter Pond)
    var arenaType: ArenaType = .indoor

    // Glass shatter effect from monster board checks
    var glassShatterX: Float = 0
    var glassShatterY: Float = 0
    var glassShatterTimer: Float = 0

    var events: [GameEvent] = []

    init(homeInfo: TeamInfo, awayInfo: TeamInfo, periodLength: Int) {
        self.periodLength = periodLength
        self.teams = [Team(id: 0, info: homeInfo, attackDir: 1), Team(id: 1, info: awayInfo, attackDir: -1)]
        self.clock = Float(periodLength)
    }

    var allSkaters: [Skater] { teams[0].skaters + teams[1].skaters }

    func team(_ id: Int) -> Team { teams[id] }
    func opponent(_ id: Int) -> Team { teams[1 - id] }

    func skaterByCode(_ code: Int) -> Skater? {
        if code < 0 { return nil }
        let t = code / 6
        let i = code % 6
        guard (0...1).contains(t), (0...5).contains(i) else { return nil }
        return teams[t].skaters[i]
    }

    func codeOf(_ s: Skater?) -> Int {
        guard let s = s else { return -1 }
        return s.team * 6 + s.index
    }

    func isHuman(_ teamId: Int) -> Bool { (0...1).contains(teamId) && isHumanTeam[teamId] }

    func controlledSkater(_ teamId: Int) -> Skater? {
        let i = controlled[teamId]
        return i >= 0 ? teams[teamId].skaters[i] : nil
    }

    func showBanner(_ text: String, _ sub: String? = nil, _ seconds: Float) {
        banner = text
        bannerSub = sub
        bannerTimer = seconds
    }

    func clockText() -> String {
        let total = overtime ? Int(clock) : Int(clock.rounded(.up))
        let m = total / 60
        let s = total % 60
        return String(format: "%d:%02d", m, s)
    }

    func periodText() -> String {
        if overtime { return "OT" }
        switch period {
        case 1: return "1ST"
        case 2: return "2ND"
        default: return "3RD"
        }
    }
}
