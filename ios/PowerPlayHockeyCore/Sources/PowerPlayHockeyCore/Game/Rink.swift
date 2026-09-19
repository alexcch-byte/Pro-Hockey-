import Foundation

/// Regulation-sized rink geometry in feet. World origin is centre ice,
/// +x runs along the length of the rink, +y runs "down" the screen (same
/// handedness as the Android canvas so the renderer port needs no flip).
enum Rink {
    static let LENGTH: Float = 200
    static let WIDTH: Float = 85
    static let HALF_L: Float = 100
    static let HALF_W: Float = 42.5
    static let CORNER_R: Float = 28

    static let GOAL_LINE_X: Float = 89     // distance from centre to each goal line
    static let BLUE_LINE_X: Float = 25
    static let GOAL_HALF_W: Float = 3      // posts at y = +-3
    static let NET_DEPTH: Float = 4        // net runs from 89 to 93
    static let NET_HALF_W: Float = 3.4     // outside of the net frame
    static let CREASE_R: Float = 6
    static let FACEOFF_R: Float = 15
    static let END_DOT_X: Float = 69
    static let NEUTRAL_DOT_X: Float = 20
    static let DOT_Y: Float = 22
    static let POST_R: Float = 0.35

    static let PUCK_R: Float = 0.5         // physical puck radius (exaggerated for feel)

    /// How far outside the boards the drawable world extends (stands, glass).
    static let WORLD_MARGIN: Float = 14

    /// Pushes a circle of radius [r] at (x, y) back inside the rounded-
    /// rectangle boards. Returns true when a wall was touched, in which case
    /// (normalX, normalY) receives the inward-facing unit normal of that wall.
    @discardableResult
    static func containCircle(x: inout Float, y: inout Float, r: Float, normalX: inout Float, normalY: inout Float) -> Bool {
        let ax = abs(x)
        let ay = abs(y)
        let cx = HALF_L - CORNER_R
        let cy = HALF_W - CORNER_R
        var hit = false
        if ax > cx && ay > cy {
            let dx = ax - cx
            let dy = ay - cy
            let d = hypot(dx, dy)
            let maxD = CORNER_R - r
            if d > maxD && d > 0.0001 {
                let nx = dx / d
                let ny = dy / d
                let sx = signOrOne(x)
                let sy = signOrOne(y)
                x = sx * (cx + nx * maxD)
                y = sy * (cy + ny * maxD)
                normalX = -nx * sx
                normalY = -ny * sy
                hit = true
            }
        } else {
            normalX = 0
            normalY = 0
            if ax > HALF_L - r {
                let s = signOrOne(x)
                x = s * (HALF_L - r)
                normalX = -s
                hit = true
            }
            if ay > HALF_W - r {
                let s = signOrOne(y)
                y = s * (HALF_W - r)
                normalY = -s
                hit = true
            }
            if hit {
                let len = hypot(normalX, normalY)
                if len > 0 { normalX /= len; normalY /= len }
            }
        }
        return hit
    }

    private static func signOrOne(_ v: Float) -> Float { v < 0 ? -1 : 1 }

    /// True when the point is inside the playing surface (ignoring radius).
    static func isInside(x: Float, y: Float, margin: Float = 0) -> Bool {
        var px = x, py = y
        var nx: Float = 0, ny: Float = 0
        let hit = containCircle(x: &px, y: &py, r: margin, normalX: &nx, normalY: &ny)
        return !hit
    }
}
