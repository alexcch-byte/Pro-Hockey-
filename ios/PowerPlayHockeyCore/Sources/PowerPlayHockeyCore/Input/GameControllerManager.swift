import Foundation
#if canImport(GameController)
import GameController
#endif

/// Physical Game Controller Manager supporting MFi, Xbox, PlayStation, and Switch gamepads
/// via Apple's GameController.framework (`GCController`).
/// Maps dual thumbsticks, face buttons, bumpers, and analog triggers to skater motion,
/// slapshot charging, bullet passing, monster body checks, and deke stick-handling.
public final class GameControllerManager {
    public static let shared = GameControllerManager()

    public private(set) var isControllerConnected: Bool = false
    public private(set) var connectedControllerName: String = "None"

    #if canImport(GameController)
    private var currentController: GCController?
    #endif

    // Shot charge tracking for gamepad buttons/triggers
    private var shotHoldTime: Double = 0
    private var wasShootPressed: Bool = false
    private let chargeDuration: Double = 0.75

    // Deke right-stick flick tracking
    private var lastRightStickX: Float = 0
    private var dekeCooldown: Double = 0

    private init() {
        #if canImport(GameController)
        setupNotificationObservers()
        checkForConnectedControllers()
        #endif
    }

    deinit {
        #if canImport(GameController)
        NotificationCenter.default.removeObserver(self)
        #endif
    }

    #if canImport(GameController)
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleControllerDidConnect(_:)),
            name: .GCControllerDidConnect,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleControllerDidDisconnect(_:)),
            name: .GCControllerDidDisconnect,
            object: nil
        )
    }

    @objc private func handleControllerDidConnect(_ notification: Notification) {
        checkForConnectedControllers()
    }

    @objc private func handleControllerDidDisconnect(_ notification: Notification) {
        checkForConnectedControllers()
    }

    private func checkForConnectedControllers() {
        let controllers = GCController.controllers()
        if let controller = controllers.first {
            currentController = controller
            isControllerConnected = true
            connectedControllerName = controller.vendorName ?? "Wireless Gamepad"
            configureController(controller)
        } else {
            currentController = nil
            isControllerConnected = false
            connectedControllerName = "None"
        }
    }

    private func configureController(_ controller: GCController) {
        // Prefer extended gamepad layout (Xbox, DualShock/DualSense, MFi)
        if let extended = controller.extendedGamepad {
            extended.leftThumbstick.deadzone = 0.15
            extended.rightThumbstick.deadzone = 0.2
        } else if let micro = controller.microGamepad {
            micro.allowsRotation = true
        }
    }
    #endif

    /// Polls physical gamepad state and integrates into the active PlayerInput.
    /// - Parameters:
    ///   - dt: Frame delta time in seconds
    ///   - input: The current local PlayerInput snapshot (will be merged with gamepad state)
    public func update(dt: Float, into input: PlayerInput) {
        #if canImport(GameController)
        guard let controller = currentController else { return }

        if let gamepad = controller.extendedGamepad {
            pollExtendedGamepad(gamepad, dt: Double(dt), into: input)
        } else if let micro = controller.microGamepad {
            pollMicroGamepad(micro, dt: Double(dt), into: input)
        }
        #endif
    }

    #if canImport(GameController)
    private func pollExtendedGamepad(_ pad: GCExtendedGamepad, dt: Double, into input: PlayerInput) {
        // 1. Movement: Left thumbstick or D-pad
        var moveX = pad.leftThumbstick.xAxis.value
        var moveY = -pad.leftThumbstick.yAxis.value // Invert Y: thumbstick up is +Y, screen coords +Y is down

        // Fallback to D-pad if stick is neutral
        if abs(moveX) < 0.1 && abs(moveY) < 0.1 {
            if pad.dpad.left.isPressed { moveX = -1.0 }
            else if pad.dpad.right.isPressed { moveX = 1.0 }
            if pad.dpad.up.isPressed { moveY = -1.0 }
            else if pad.dpad.down.isPressed { moveY = 1.0 }
        }

        // Clamp to unit circle if non-zero
        let mag = hypot(moveX, moveY)
        if mag > 0.01 {
            let clamped = min(mag, 1.0)
            input.moveX = (moveX / mag) * clamped
            input.moveY = (moveY / mag) * clamped
        }

        // 2. Shoot (Button X or Right Trigger)
        let isShootPressed = pad.buttonX.isPressed || pad.rightTrigger.isPressed || pad.rightTrigger.value > 0.3
        if isShootPressed {
            shotHoldTime += dt
            input.shootHeld = true
            input.shootCharge = Float(min(shotHoldTime / chargeDuration, 1.0))
        } else {
            if wasShootPressed {
                // Released: trigger shot
                input.shootRelease = true
                input.shootHeld = false
            }
            shotHoldTime = 0
        }
        wasShootPressed = isShootPressed

        // 3. Pass (Button A or Right Shoulder)
        if pad.buttonA.isPressed || pad.rightShoulder.isPressed {
            input.pass = true
        }

        // 4. Hit / Check / Poke / Goalie Save (Button B or Left Shoulder)
        if pad.buttonB.isPressed || pad.leftShoulder.isPressed {
            input.hit = true
        }

        // 5. Deke (Button Y, Left Trigger, Left Stick Click, or Right Stick quick flick)
        dekeCooldown = max(0, dekeCooldown - dt)
        let rightStickX = pad.rightThumbstick.xAxis.value
        let stickFlick = abs(rightStickX - lastRightStickX) / Float(max(dt, 0.001)) > 8.0
        lastRightStickX = rightStickX

        if pad.buttonY.isPressed || pad.leftTrigger.isPressed || pad.leftThumbstickButton?.isPressed == true || stickFlick {
            if dekeCooldown <= 0 {
                input.deke = true
                dekeCooldown = 0.35 // prevent spam
            }
        }
    }

    private func pollMicroGamepad(_ pad: GCMicroGamepad, dt: Double, into input: PlayerInput) {
        // Apple TV Siri Remote or simple controller
        let moveX = pad.dpad.xAxis.value
        let moveY = -pad.dpad.yAxis.value
        let mag = hypot(moveX, moveY)
        if mag > 0.01 {
            let clamped = min(mag, 1.0)
            input.moveX = (moveX / mag) * clamped
            input.moveY = (moveY / mag) * clamped
        }

        if pad.buttonA.isPressed {
            input.pass = true
        }
        if pad.buttonX.isPressed {
            input.hit = true
        }
    }
    #endif
}
