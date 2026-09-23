import CoreGraphics
import Foundation
#if canImport(QuartzCore)
import QuartzCore
#endif

/// Virtual joystick (left half of the screen) plus SHOOT / PASS / HIT buttons
/// (bottom right). Platform-agnostic port of TouchControls.kt: driven by
/// abstract pointer down/move/up calls keyed by `AnyHashable` instead of a
/// MotionEvent's integer pointer IDs, so this file has no UIKit dependency of
/// its own. See TouchControls+UIKit.swift for the UITouch adapter that
/// drives it in a real app (there, a touch's identity is
/// `ObjectIdentifier(touch)`).
///
/// Touch callbacks land on the main thread while the game loop reads a
/// PlayerInput snapshot from a different thread (matching the background
/// loop in GameView.kt), so PlayerInput access goes through `lock`, the same
/// role Android's `synchronized(lock)` plays. The pure draw-state
/// properties below (joyActive, joyAnchorX, shootDown, ...) are read
/// directly by Renderer without the lock, matching Android's `@Volatile` on
/// those same fields there -- fine since a stale read only costs one frame
/// of visuals, never affects gameplay.
final class TouchControls: TouchControlsState {
    static let CHARGE_SECONDS: Double = 0.75

    private let density: CGFloat
    private let lock = NSLock()
    private let input = PlayerInput()

    /// Monotonic clock, matching Android's SystemClock.elapsedRealtime()
    /// usage here. Swappable for tests; defaults to the same clock
    /// CADisplayLink timestamps use.
    var now: () -> Double = {
        #if canImport(QuartzCore)
        return CACurrentMediaTime()
        #else
        return ProcessInfo.processInfo.systemUptime
        #endif
    }

    // Joystick geometry (screen points)
    private(set) var joyRadius: CGFloat
    private(set) var joyRestX: CGFloat = 0
    private(set) var joyRestY: CGFloat = 0
    private(set) var joyActive = false
    private(set) var joyAnchorX: CGFloat = 0
    private(set) var joyAnchorY: CGFloat = 0
    private(set) var joyKnobX: CGFloat = 0
    private(set) var joyKnobY: CGFloat = 0
    private var joyPointer: AnyHashable?

    // Buttons
    private(set) var shootX: CGFloat = 0
    private(set) var shootY: CGFloat = 0
    private(set) var shootR: CGFloat
    private(set) var passX: CGFloat = 0
    private(set) var passY: CGFloat = 0
    private(set) var passR: CGFloat
    private(set) var hitX: CGFloat = 0
    private(set) var hitY: CGFloat = 0
    private(set) var hitR: CGFloat
    private(set) var shootDown = false
    private(set) var passDown = false
    private(set) var hitDown = false
    private var shootPointer: AnyHashable?
    private var passPointer: AnyHashable?
    private var hitPointer: AnyHashable?
    private var shootDownTime: Double = 0

    // Automatic Deke detection on rapid joystick movement / flick
    private var prevJoyTime: Double = 0
    private var prevJoyMx: Float = 0
    private var prevJoyMy: Float = 0
    private var dekeCooldownUntil: Double = 0

    private var screenW: CGFloat = 1
    private var screenH: CGFloat = 1
    private var topExclusion: CGFloat = 0

    init(density: Float) {
        self.density = CGFloat(density)
        joyRadius = 64 * self.density
        shootR = 50 * self.density
        passR = 42 * self.density
        hitR = 42 * self.density
    }

    func layout(_ w: Int, _ h: Int) {
        screenW = CGFloat(w)
        screenH = CGFloat(h)
        topExclusion = 64 * density
        joyRadius = 64 * density
        joyRestX = 120 * density
        joyRestY = screenH - 120 * density
        shootX = screenW - 95 * density; shootY = screenH - 95 * density
        passX = screenW - 215 * density; passY = screenH - 80 * density
        hitX = screenW - 105 * density; hitY = screenH - 215 * density
    }

    func currentCharge() -> Float {
        if !shootDown { return 0 }
        let held = now() - shootDownTime
        return Float(min(max(held / Self.CHARGE_SECONDS, 0), 1))
    }

    /// Copies the current state into `dst` and clears one-shot presses.
    func snapshotInto(_ dst: PlayerInput) {
        lock.lock()
        input.shootHeld = shootDown
        input.shootCharge = shootDown ? currentCharge() : input.shootCharge
        dst.copyFrom(input)
        input.clearPulses()
        lock.unlock()
    }

    func reset() {
        lock.lock()
        input.moveX = 0; input.moveY = 0
        input.clearPulses()
        input.shootHeld = false
        lock.unlock()
        joyPointer = nil; shootPointer = nil; passPointer = nil; hitPointer = nil
        joyActive = false; shootDown = false; passDown = false; hitDown = false
        prevJoyTime = 0; prevJoyMx = 0; prevJoyMy = 0
    }

    // ------------------------------------------------------------- pointers

    func pointerDown(_ id: AnyHashable, _ x: CGFloat, _ y: CGFloat) {
        if hypot(x - shootX, y - shootY) <= shootR * 1.15 && shootPointer == nil {
            shootPointer = id
            shootDown = true
            shootDownTime = now()
            lock.lock(); input.shootCharge = 0; lock.unlock()
            return
        }
        if hypot(x - passX, y - passY) <= passR * 1.15 && passPointer == nil {
            passPointer = id
            passDown = true
            lock.lock(); input.pass = true; lock.unlock()
            return
        }
        if hypot(x - hitX, y - hitY) <= hitR * 1.15 && hitPointer == nil {
            hitPointer = id
            hitDown = true
            lock.lock(); input.hit = true; lock.unlock()
            return
        }
        if x < screenW * 0.5 && y > topExclusion && joyPointer == nil {
            joyPointer = id
            joyActive = true
            joyAnchorX = x
            joyAnchorY = y
            joyKnobX = x
            joyKnobY = y
            prevJoyTime = now()
            prevJoyMx = 0
            prevJoyMy = 0
            lock.lock(); input.moveX = 0; input.moveY = 0; lock.unlock()
        }
    }

    func pointerMoved(_ id: AnyHashable, _ x: CGFloat, _ y: CGFloat) {
        guard id == joyPointer else { return }
        updateJoystick(x, y)
    }

    private func updateJoystick(_ x: CGFloat, _ y: CGFloat) {
        var dx = x - joyAnchorX
        var dy = y - joyAnchorY
        let d = hypot(dx, dy)
        if d > joyRadius {
            dx *= joyRadius / d
            dy *= joyRadius / d
        }
        joyKnobX = joyAnchorX + dx
        joyKnobY = joyAnchorY + dy
        var mx = Float(dx / joyRadius)
        var my = Float(dy / joyRadius)
        let mag = hypot(mx, my)
        if mag < 0.12 {
            mx = 0; my = 0
        } else {
            // Rescale so the dead zone doesn't eat range.
            let scaled = min(max((mag - 0.12) / 0.88, 0), 1)
            mx = mx / mag * scaled
            my = my / mag * scaled
        }

        // Detect rapid flick / sharp direction change for automatic deke move
        let currentTime = now()
        let dt = currentTime - prevJoyTime
        if dt >= 0.025 && dt <= 0.220 {
            let prevMag = hypot(prevJoyMx, prevJoyMy)
            let curMag = hypot(mx, my)
            if curMag > 0.42 && currentTime > dekeCooldownUntil {
                let deltaDist = hypot(mx - prevJoyMx, my - prevJoyMy)
                let stickSpeed = deltaDist / Float(dt)
                let dot: Float = (prevMag > 0.28 && curMag > 0.28)
                    ? (mx * prevJoyMx + my * prevJoyMy) / (curMag * prevMag)
                    : 1.0
                let isSharpCut = (dot < 0.2 && prevMag > 0.38 && dt <= 0.180)
                let isFastFlick = (stickSpeed > 7.5 && curMag > 0.5)
                if isSharpCut || isFastFlick {
                    lock.lock(); input.deke = true; lock.unlock()
                    dekeCooldownUntil = currentTime + 0.5
                }
            }
        }
        if dt >= 0.025 {
            prevJoyMx = mx
            prevJoyMy = my
            prevJoyTime = currentTime
        }

        lock.lock(); input.moveX = mx; input.moveY = my; lock.unlock()
    }

    func pointerUp(_ id: AnyHashable) {
        // Explicit if/else rather than switching on the optional pointer
        // properties: unambiguous regardless of how Optional's `~=` pattern
        // matching resolves for AnyHashable, and there's no compiler here to
        // check the alternative.
        if joyPointer != nil && id == joyPointer! {
            joyPointer = nil
            joyActive = false
            prevJoyTime = 0
            prevJoyMx = 0
            prevJoyMy = 0
            lock.lock(); input.moveX = 0; input.moveY = 0; lock.unlock()
        } else if shootPointer != nil && id == shootPointer! {
            shootPointer = nil
            let charge = currentCharge()
            shootDown = false
            lock.lock()
            input.shootRelease = true
            input.shootCharge = charge
            input.shootHeld = false
            lock.unlock()
        } else if passPointer != nil && id == passPointer! {
            passPointer = nil; passDown = false
        } else if hitPointer != nil && id == hitPointer! {
            hitPointer = nil; hitDown = false
        }
    }

    /// Matches Android's ACTION_CANCEL: drop every active pointer at once
    /// (the system interrupted the gesture -- an incoming call, a system
    /// gesture stealing the touch, etc).
    func pointerCancelled() {
        reset()
    }
}
