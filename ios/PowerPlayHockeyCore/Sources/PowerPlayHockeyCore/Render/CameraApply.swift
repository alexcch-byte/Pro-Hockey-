import CoreGraphics

/// The drawing-API-specific half of Camera left out of Game/Camera.swift on
/// purpose in phase 1 (that file is pure world<->screen math with no
/// rendering dependency). Matches Camera.apply(Canvas) on Android.
extension Camera {
    func apply(_ canvas: GCanvas) {
        canvas.translate(CGFloat(screenW) / 2, CGFloat(screenH) / 2)
        canvas.scale(CGFloat(scale), CGFloat(scale))
        canvas.translate(CGFloat(-x), CGFloat(-y))
    }
}
