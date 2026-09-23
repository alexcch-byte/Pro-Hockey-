import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Native iOS haptic engine providing tactile feedback for hockey collisions,
/// slapshot releases, puck deflections, goal celebrations, and UI interactions.
public final class HapticManager {
    public static let shared = HapticManager()

    public var isEnabled: Bool {
        get { Prefs.vibrationEnabled() }
        set { Prefs.setVibrationEnabled(newValue) }
    }

    #if canImport(UIKit)
    private var lightImpact: UIImpactFeedbackGenerator?
    private var mediumImpact: UIImpactFeedbackGenerator?
    private var heavyImpact: UIImpactFeedbackGenerator?
    private var rigidImpact: UIImpactFeedbackGenerator?
    private var softImpact: UIImpactFeedbackGenerator?
    private var notificationFeedback: UINotificationFeedbackGenerator?
    private var selectionFeedback: UISelectionFeedbackGenerator?
    #endif

    private init() {
        #if canImport(UIKit)
        prepareGenerators()
        #endif
    }

    #if canImport(UIKit)
    private func prepareGenerators() {
        lightImpact = UIImpactFeedbackGenerator(style: .light)
        mediumImpact = UIImpactFeedbackGenerator(style: .medium)
        heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
        rigidImpact = UIImpactFeedbackGenerator(style: .rigid)
        softImpact = UIImpactFeedbackGenerator(style: .soft)
        notificationFeedback = UINotificationFeedbackGenerator()
        selectionFeedback = UISelectionFeedbackGenerator()

        lightImpact?.prepare()
        mediumImpact?.prepare()
        heavyImpact?.prepare()
        rigidImpact?.prepare()
        softImpact?.prepare()
        notificationFeedback?.prepare()
        selectionFeedback?.prepare()
    }
    #endif

    /// Trigger tactile feedback for in-game physics & event notifications.
    public func handle(_ event: GameEvent) {
        guard isEnabled else { return }

        #if canImport(UIKit)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            switch event {
            case .shot:
                self.mediumImpact?.impactOccurred(intensity: 0.85)
                self.mediumImpact?.prepare()

            case .oneTimer:
                self.heavyImpact?.impactOccurred(intensity: 0.95)
                self.heavyImpact?.prepare()

            case .pass:
                self.lightImpact?.impactOccurred(intensity: 0.5)
                self.lightImpact?.prepare()

            case .pickup:
                self.softImpact?.impactOccurred(intensity: 0.4)
                self.softImpact?.prepare()

            case .faceoffDrop:
                self.rigidImpact?.impactOccurred(intensity: 0.7)
                self.rigidImpact?.prepare()

            case .boards:
                self.mediumImpact?.impactOccurred(intensity: 0.75)
                self.mediumImpact?.prepare()

            case .post:
                self.rigidImpact?.impactOccurred(intensity: 1.0)
                self.rigidImpact?.prepare()

            case .poke:
                self.lightImpact?.impactOccurred(intensity: 0.45)
                self.lightImpact?.prepare()

            case .save:
                self.rigidImpact?.impactOccurred(intensity: 0.8)
                self.rigidImpact?.prepare()

            case .hit:
                self.heavyImpact?.impactOccurred(intensity: 1.0)
                self.heavyImpact?.prepare()

            case .whistle:
                self.notificationFeedback?.notificationOccurred(.warning)
                self.notificationFeedback?.prepare()

            case .penalty:
                self.notificationFeedback?.notificationOccurred(.error)
                self.notificationFeedback?.prepare()

            case .horn:
                self.heavyImpact?.impactOccurred(intensity: 1.0)

            case .goal:
                self.notificationFeedback?.notificationOccurred(.success)
                self.notificationFeedback?.prepare()

            case .periodEnd:
                self.notificationFeedback?.notificationOccurred(.warning)
                self.notificationFeedback?.prepare()

            case .gameOver:
                self.notificationFeedback?.notificationOccurred(.success)
                self.notificationFeedback?.prepare()

            case .faceoffSet:
                self.softImpact?.impactOccurred(intensity: 0.5)

            case .onFire:
                self.notificationFeedback?.notificationOccurred(.success)
                self.heavyImpact?.impactOccurred(intensity: 0.9)

            case .deke:
                self.lightImpact?.impactOccurred(intensity: 0.6)
                self.lightImpact?.prepare()

            case .glassShatter:
                // Double pulse for violent shatter
                self.heavyImpact?.impactOccurred(intensity: 1.0)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                    self.rigidImpact?.impactOccurred(intensity: 0.9)
                }

            case .goalieSaveMove:
                self.mediumImpact?.impactOccurred(intensity: 0.85)
                self.mediumImpact?.prepare()
            }
        }
        #endif
    }

    /// Trigger subtle selection click for UI menus and button taps.
    public func selection() {
        guard isEnabled else { return }
        #if canImport(UIKit)
        DispatchQueue.main.async { [weak self] in
            self?.selectionFeedback?.selectionChanged()
            self?.selectionFeedback?.prepare()
        }
        #endif
    }

    /// Custom impact intensity trigger.
    public func impact(intensity: Float) {
        guard isEnabled else { return }
        #if canImport(UIKit)
        DispatchQueue.main.async { [weak self] in
            if intensity > 0.8 {
                self?.heavyImpact?.impactOccurred(intensity: CGFloat(intensity))
            } else if intensity > 0.4 {
                self?.mediumImpact?.impactOccurred(intensity: CGFloat(intensity))
            } else {
                self?.lightImpact?.impactOccurred(intensity: CGFloat(intensity))
            }
        }
        #endif
    }
}
