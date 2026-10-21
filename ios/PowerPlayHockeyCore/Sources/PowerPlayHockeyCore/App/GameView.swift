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

    /// The pre-game anthem ceremony (audio + caption + tap to skip) is implemented.
    static let ANTHEM_READY = true

    private var anthemWanted = false
    private var anthemPlaying = false

    /// `allowAnthem` is false for restarts / rematches from the pause or game-over menus (fresh match starts only).
    public init(frame: CGRect, config: MatchConfig = .makeDefault(), allowAnthem: Bool = true) {
        self.config = config
        let screenScale = Float(UIScreen.main.scale)
        self.controls = TouchControls(density: screenScale)
        self.renderer = Renderer(density: screenScale)

        let kits = TeamInfo.matchTeams(config.homeTeam, config.awayTeam)   // home/away kits, road-kit fallback on a clash (same on host and guest)
        let homeInfo = kits.home
        let awayInfo = kits.away
        self.world = World(homeInfo: homeInfo, awayInfo: awayInfo, periodLength: config.periodLengthSeconds)
        self.world.arenaType = config.arenaType
        self.world.isShootout = (config.mode == .shootout)
        self.world.offsidesEnabled = (config.mode != .shootout) && Prefs.offsideEnabled()

        let aiSettings = AiSettings.forDifficulty(config.aiDifficulty)
        self.simulation = Simulation(world: world, ai: aiSettings)
        self.simulation.uncappedOvertime = config.isTournament

        super.init(frame: frame)
        self.isMultipleTouchEnabled = true
        self.backgroundColor = UIColor(red: 11/255.0, green: 20/255.0, blue: 36/255.0, alpha: 1.0)

        renderer.pullPillEnabled = config.mode != .wifiClient
        renderer.guest = config.mode == .wifiClient
        MusicManager.shared.play("music_game", volume: MusicManager.gameVolume)
        // Pre-game anthem ceremony: fresh match starts only (tournament: first game of the series), never in a
        // shootout, only when the setting is on and the music is audible.
        let wantAnthem = GameView.ANTHEM_READY && allowAnthem && Prefs.anthemEnabled() && Prefs.musicVolume() > 0
            && config.mode != .shootout
            && (!config.isTournament || (Prefs.getTournament()?.currentRound ?? 0) == 0)
        anthemWanted = wantAnthem
        simulation.start(anthem: wantAnthem)
        if wantAnthem {
            // Start the anthem right away (the loop is muted while it plays); the crowd bed starts after the ceremony.
            anthemPlaying = true
            if !MusicManager.shared.playAnthem() {
                // No audio to wait for: do not hold the players for 28 silent seconds.
                world.phaseTimer = min(world.phaseTimer, 2)
            }
        } else {
            SoundManager.shared.startCrowd()
        }
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

    /// Starts the anthem audio when the pre-game ceremony begins and fades it out when the phase ends.
    private func updateAnthem() {
        if world.phase == .anthem {
            if !anthemPlaying {
                anthemPlaying = true
                if anthemWanted && !MusicManager.shared.playAnthem() {
                    // No audio to wait for: do not hold the players for 28 silent seconds.
                    world.phaseTimer = min(world.phaseTimer, 2)
                }
            }
        } else if anthemPlaying {
            anthemPlaying = false
            MusicManager.shared.stopAnthem()
            SoundManager.shared.startCrowd()
        }
    }

    // ------------------------------------------------------------- Loop

    public func setPaused(_ paused: Bool) {
        isGamePaused = paused
        if paused {
            SoundManager.shared.stopCrowd()
            MusicManager.shared.pause()
        } else {
            lastTimestamp = 0
            if world.phase != .anthem { SoundManager.shared.startCrowd() }
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
        let controlled = world.controlledSkater(localTeamIndex)
        controls.carrying = (controlled != nil && world.puck.carrier === controlled)
        controls.snapshotInto(localPlayerInput)

        // Poll physical game controllers (Xbox, PlayStation, MFi, Switch)
        GameControllerManager.shared.update(dt: dt, into: localPlayerInput)

        var inputs: [PlayerInput?] = [nil, nil]
        if world.isHuman(localTeamIndex) {
            inputs[localTeamIndex] = localPlayerInput
        }

        // Run simulation step
        simulation.step(dt: dt, inputs: inputs)
        updateAnthem()

        // Audio and haptic dispatch & achievements
        for event in world.events {
            SoundManager.shared.handle(event)
            HapticManager.shared.handle(event)

            switch event {
            case .glassShatter:
                GameCenterManager.shared.reportAchievement(id: AchievementID.glassShatter)
            case .onFire:
                GameCenterManager.shared.reportAchievement(id: AchievementID.onFireSurge)
            case .goal:
                GameCenterManager.shared.reportAchievement(id: AchievementID.firstGoal)
            default:
                break
            }
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
            if winner == localTeamIndex && world.teams[1 - localTeamIndex].score == 0 {
                GameCenterManager.shared.reportAchievement(id: AchievementID.shutout)
            }
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
            // Tap anywhere skips the pre-game anthem ceremony.
            if world.phase == .anthem {
                simulation.skipAnthem()
                MusicManager.shared.stopAnthem()
                return
            }
            // PULL GOALIE pill: toggles the extra attacker (sound only if the state actually changed).
            if renderer.isPullHit(p.x, p.y, world, localTeamIndex) {
                let before = world.goaliePulled[localTeamIndex]
                let after = simulation.togglePullGoalie(localTeamIndex)
                if after != before { SoundManager.shared.playToggle() }
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
