import Foundation

final class Puck {
    var x: Float = 0
    var y: Float = 0
    var vx: Float = 0
    var vy: Float = 0

    /// Skater currently carrying the puck on their stick, or nil when loose.
    var carrier: Skater?

    /// True while the puck is travelling as a shot (until it is touched again).
    var shot: Bool = false
    var shooter: Skater?
    var passTarget: Skater?
    var lastTouchTeam: Int = -1
    /// True while a shot is slipping through the goalie (difficulty leak);
    /// cleared once clear of the pads.
    var leaking: Bool = false

    // Network interpolation targets (client only)
    var netX: Float = 0
    var netY: Float = 0

    var speed: Float { hypot(vx, vy) }

    func reset(_ px: Float, _ py: Float) {
        x = px; y = py; vx = 0; vy = 0
        carrier = nil
        shot = false
        shooter = nil
        passTarget = nil
        leaking = false
        netX = px; netY = py
    }
}
