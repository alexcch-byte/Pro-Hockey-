import CoreGraphics

/// The on-screen-controls state Renderer needs to draw the joystick and
/// SHOOT/PASS/HIT buttons. This is a read-only data contract, not the
/// gesture-handling logic -- that's TouchControls.kt's job, ported in the
/// (not yet started) input phase. The real touch-input type will conform to
/// this once it exists; for now it lets Renderer compile and be exercised on
/// its own.
protocol TouchControlsState: AnyObject {
    var joyRadius: CGFloat { get }
    var joyActive: Bool { get }
    var joyAnchorX: CGFloat { get }
    var joyAnchorY: CGFloat { get }
    var joyRestX: CGFloat { get }
    var joyRestY: CGFloat { get }
    var joyKnobX: CGFloat { get }
    var joyKnobY: CGFloat { get }

    var shootX: CGFloat { get }
    var shootY: CGFloat { get }
    var shootR: CGFloat { get }
    var shootDown: Bool { get }

    var passX: CGFloat { get }
    var passY: CGFloat { get }
    var passR: CGFloat { get }
    var passDown: Bool { get }

    var hitX: CGFloat { get }
    var hitY: CGFloat { get }
    var hitR: CGFloat { get }
    var hitDown: Bool { get }

    func currentCharge() -> Float
}
