#if canImport(UIKit)
import UIKit

/// UIKit glue for TouchControls: translates a UIView's touch callbacks into
/// the pointer-identity calls TouchControls itself is written against.
/// A UITouch instance is stable for the lifetime of one finger's contact, so
/// `ObjectIdentifier(touch)` stands in for Android's integer pointer ID.
///
/// Usage from the eventual GameView's UIView subclass:
/// ```
/// override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
///     controls.handleTouches(touches, in: self, phase: .began)
/// }
/// // ...same one-line call in touchesMoved/touchesEnded, and
/// // controls.pointerCancelled() from touchesCancelled.
/// ```
extension TouchControls {
    enum TouchPhase { case began, moved, ended }

    /// `view` supplies the coordinate space (`touch.location(in: view)`),
    /// matching how Android's MotionEvent coordinates already arrive in the
    /// GameView's own local space.
    func handleTouches(_ touches: Set<UITouch>, in view: UIView, phase: TouchPhase) {
        for touch in touches {
            let id = ObjectIdentifier(touch)
            let p = touch.location(in: view)
            switch phase {
            case .began: pointerDown(id, p.x, p.y)
            case .moved: pointerMoved(id, p.x, p.y)
            case .ended: pointerUp(id)
            }
        }
    }
}
#endif
