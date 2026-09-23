import CoreGraphics
import Foundation
#if canImport(UIKit)
import UIKit
#endif
#if canImport(QuartzCore)
import QuartzCore
#endif

#if canImport(UIKit)
/// Interactive hockey game view running the authoritative simulation and renderer loop.
public final class GameView: UIView {
    public var config: MatchConfig
    public var onPause: (() -> Void)?
    public var onGameOver: ((_ winnerTeam: Int, _ score0: Int, _ score1: Int) -> Void)?

    private var world: World
    private var simulation: Simulation
    private var renderer: Renderer
    private let controls: TouchControls
    private let localPlayerInput = PlayerInput()

    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private var isGamePaused = false
    private var localTeamIndex = 0

    public init(frame: CGRect, config: MatchConfig = .makeDefault()) {
        self.config = config
        let screenScale = Float(UIScreen.main.scale)
        self.controls = TouchControls(density: screenScale)
        self.renderer = Renderer(density: screenScale)

        let homeInfo = TeamInfo.byIndex(config.homeTeam)
        let awayInfo = TeamInfo.byIndex(config.awayTeam)
        self.world = World(homeInfo: homeInfo, awayInfo: awayInfo, periodLength: config.periodLengthSeconds)
        self.world.arenaType = config.arenaType
        self.world.isShootout = (config.mode == .shootout)

        let aiSettings = AiSettings.forDifficulty(config.aiDifficulty)
        self.simulation = Simulation(world: world, ai: aiSettings)

        super.init(frame: frame)
        self.isMultipleTouchEnabled = true
        self.backgroundColor = UIColor(red: 11/255.0, green: 20/255.0, blue: 36/255.0, alpha: 1.0)

        SoundManager.shared.startCrowd()
        MusicManager.shared.play("music_game", volume: MusicManager.gameVolume)
        simulation.start()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public override func willMove(toSuperview newSuperview: UIView?) {
        super.willMove(toSuperview: newSuperview)
        if newSuperview == nil {
            stopLoop()
            SoundManager.shared.stopCrowd()
        } else {
            startLoop()
        }
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        let w = Int(bounds.width)
        let h = Int(bounds.height)
        guard w > 0, h > 0 else { return }
        renderer.resize(w, h)
        controls.layout(w, h)
    }

    // ------------------------------------------------------------- Loop

    public func setPaused(_ paused: Bool) {
        isGamePaused = paused
        if paused {
            SoundManager.shared.stopCrowd()
            MusicManager.shared.pause()
        } else {
            lastTimestamp = 0
            SoundManager.shared.startCrowd()
            MusicManager.shared.resume()
        }
    }

    private func startLoop() {
        stopLoop()
        let link = CADisplayLink(target: self, selector: #selector(displayTick))
        if #available(iOS 15.0, *) {
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        }
        link.add(to: .main, forMode: .common)
        self.displayLink = link
        self.lastTimestamp = 0
    }

    private func stopLoop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    @objc private func displayTick(link: CADisplayLink) {
        guard !isGamePaused else { return }

        let now = link.timestamp
        var dt = Float(lastTimestamp > 0 ? now - lastTimestamp : 1.0 / 60.0)
        lastTimestamp = now

        // Clamp dt to avoid physics spiral-of-death on frame hitch
        dt = min(max(dt, 0.001), 0.05)

        // Snapshot human touch inputs
        controls.snapshotInto(localPlayerInput)

        var inputs: [PlayerInput?] = [nil, nil]
        if world.isHuman(localTeamIndex) {
            inputs[localTeamIndex] = localPlayerInput
        }

        // Run simulation step
        simulation.step(dt: dt, inputs: inputs)

        // Audio dispatch
        for event in world.events {
            SoundManager.shared.handle(event)
        }

        // Skate sounds during high acceleration / speed
        if world.phase == .play {
            var maxIntensity: Float = 0
            for s in world.allSkaters {
                if s.speed > 14 {
                    let intensity = (s.speed - 14) / 11.0
                    if intensity > maxIntensity { maxIntensity = intensity }
                }
            }
            if maxIntensity > 0 {
                SoundManager.shared.playSkate(intensity: maxIntensity)
            }
        }

        if world.phase == .gameOver {
            let winner = world.teams[0].score > world.teams[1].score ? 0 : 1
            onGameOver?(winner, world.teams[0].score, world.teams[1].score)
        }

        setNeedsDisplay()
    }

    // ------------------------------------------------------------- Rendering

    public override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let canvas = GCanvas(context: ctx, bounds: bounds)
        let dt = Float(1.0 / 60.0)
        renderer.draw(canvas, world, localTeamIndex, controls, dt)
    }

    // ------------------------------------------------------------- Touches

    public override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let p = touch.location(in: self)
            // Pause button hit test
            if renderer.isPauseHit(p.x, p.y) {
                SoundManager.shared.playClick()
                onPause?()
                return
            }
            // Shootout change shooter hit test
            if renderer.isSwitchShooterHit(p.x, p.y, world, localTeamIndex) {
                SoundManager.shared.playClick()
                simulation.cycleShootoutShooter(localTeamIndex)
                return
            }
        }
        controls.handleTouches(touches, in: self, phase: .began)
    }

    public override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        controls.handleTouches(touches, in: self, phase: .moved)
    }

    public override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        controls.handleTouches(touches, in: self, phase: .ended)
    }

    public override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        controls.pointerCancelled()
    }
}
#endif
