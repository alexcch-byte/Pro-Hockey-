import Foundation

/// Broadcast-style follow camera. Zoom is fixed so the full rink width plus
/// the boards is visible; the view pans to follow the puck along the ice.
///
/// This is the platform-agnostic half of the Android Camera: world<->screen
/// math only. Applying the transform to a drawing context (Canvas.apply on
/// Android) belongs in the Renderer port, since it depends on the graphics
/// API chosen for iOS (Core Graphics / SpriteKit / Metal).
final class Camera {
    static let VISIBLE_HEIGHT_FT: Float = 84
    static let WORLD_HALF_W: Float = Rink.HALF_L + Rink.WORLD_MARGIN
    static let WORLD_HALF_H: Float = Rink.HALF_W + Rink.WORLD_MARGIN

    var x: Float = 0
    var y: Float = 0
    var scale: Float = 10
    var screenW: Int = 1
    var screenH: Int = 1

    func resize(_ w: Int, _ h: Int) {
        screenW = max(1, w)
        screenH = max(1, h)
        scale = Float(screenH) / Camera.VISIBLE_HEIGHT_FT
        if Float(screenW) / scale > Camera.WORLD_HALF_W * 2 {
            scale = Float(screenW) / (Camera.WORLD_HALF_W * 2)
        }
    }

    func snapTo(_ tx: Float, _ ty: Float) {
        x = clampX(tx)
        y = clampY(ty)
    }

    func follow(_ tx: Float, _ ty: Float, _ dt: Float) {
        let k = 1 - exp(-dt * 4.5)
        x += (clampX(tx) - x) * k
        y += (clampY(ty) - y) * k
    }

    private func clampX(_ v: Float) -> Float {
        let halfVis = Float(screenW) / scale / 2
        let lim = Camera.WORLD_HALF_W - halfVis
        return lim <= 0 ? 0 : min(max(v, -lim), lim)
    }

    private func clampY(_ v: Float) -> Float {
        let halfVis = Float(screenH) / scale / 2
        let lim = Camera.WORLD_HALF_H - halfVis
        return lim <= 0 ? 0 : min(max(v, -lim), lim)
    }

    func toScreenX(_ wx: Float) -> Float { (wx - x) * scale + Float(screenW) / 2 }
    func toScreenY(_ wy: Float) -> Float { (wy - y) * scale + Float(screenH) / 2 }

    func visibleLeft() -> Float { x - Float(screenW) / scale / 2 }
    func visibleRight() -> Float { x + Float(screenW) / scale / 2 }
    func visibleTop() -> Float { y - Float(screenH) / scale / 2 }
    func visibleBottom() -> Float { y + Float(screenH) / scale / 2 }

    func minScale() -> Float {
        min(Float(screenW) / (Camera.WORLD_HALF_W * 2), Float(screenH) / (Camera.WORLD_HALF_H * 2))
    }
}
