import Foundation
#if canImport(AVFoundation)
import AVFoundation
#endif

/// In-match audio effects: puck, boards, posts, hits, saves, whistle, horn,
/// crowd, skate scrapes, organ, and 8-bit jingles.
/// Driven by GameEvent notifications from Simulation.
final class SoundManager {
    static let shared = SoundManager()

    var volume: Float {
        get { Float(Prefs.sfxVolume()) / 100.0 }
        set {
            Prefs.setSfxVolume(Int((newValue * 100).rounded()))
            if newValue <= 0 { stopCrowd() }
        }
    }

    var enabled: Bool { volume > 0 }

    #if canImport(AVFoundation)
    private var players: [String: [AVAudioPlayer]] = [:]
    private var crowdPlayer: AVAudioPlayer?
    #endif

    private var skateToggle = false
    private let rng = SeededRandom(seed: UInt64(Date().timeIntervalSince1970))

    init() {
        #if canImport(AVFoundation)
        preloadSounds()
        #endif
    }

    #if canImport(AVFoundation)
    private func preloadSounds() {
        let soundNames = [
            "puck_hit", "pass", "pickup", "faceoff", "wall_bounce", "post", "body_hit",
            "save", "whistle", "horn", "crowd_loop", "cheer", "skate1", "skate2",
            "organ_rally", "jingle_goal", "jingle_period", "jingle_win", "jingle_lose",
            "button_click", "one_timer", "gasp", "penalty", "fire", "deke", "glass", "pad_stack"
        ]
        for name in soundNames {
            if let url = findSoundURL(name: name) {
                var pool: [AVAudioPlayer] = []
                for _ in 0..<3 {
                    if let player = try? AVAudioPlayer(contentsOf: url) {
                        player.prepareToPlay()
                        pool.append(player)
                    }
                }
                if !pool.isEmpty {
                    players[name] = pool
                }
            }
        }
    }

    private func findSoundURL(name: String) -> URL? {
        let extensions = ["wav", "mp3", "caf", "m4a", "ogg"]
        for ext in extensions {
            if let url = Bundle.main.url(forResource: name, withExtension: ext) {
                return url
            }
            #if SWIFT_PACKAGE
            if let url = Bundle.module.url(forResource: name, withExtension: ext) {
                return url
            }
            #endif
        }
        return nil
    }

    private func playSound(_ name: String, level: Float, rate: Float = 1.0) {
        guard enabled else { return }
        guard let pool = players[name] else { return }
        let player = pool.first { !$0.isPlaying } ?? pool[0]
        player.volume = min(max(level * volume, 0), 1)
        player.enableRate = true
        player.rate = rate
        player.currentTime = 0
        player.play()
    }
    #else
    private func playSound(_ name: String, level: Float, rate: Float = 1.0) {}
    #endif

    private func jitter(_ amount: Float) -> Float {
        1.0 + (rng.nextFloat() * 2.0 - 1.0) * amount
    }

    func handle(_ event: GameEvent) {
        guard enabled else { return }
        switch event {
        case .shot:
            playSound("puck_hit", level: 1.0, rate: jitter(0.08))
        case .oneTimer:
            playSound("one_timer", level: 1.0, rate: jitter(0.06))
        case .pass:
            playSound("pass", level: 0.75, rate: jitter(0.1))
        case .pickup:
            playSound("pickup", level: 0.35, rate: jitter(0.15))
        case .faceoffDrop:
            playSound("faceoff", level: 0.8)
        case .boards:
            playSound("wall_bounce", level: 0.85, rate: jitter(0.12))
        case .post:
            playSound("post", level: 0.95)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [weak self] in
                self?.playSound("gasp", level: 0.85)
            }
        case .poke:
            playSound("pass", level: 0.5, rate: 0.8)
        case .save:
            playSound("save", level: 0.9, rate: jitter(0.1))
            if rng.nextFloat() < 0.3 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.16) { [weak self] in
                    self?.playSound("gasp", level: 0.7)
                }
            }
        case .hit:
            playSound("body_hit", level: 1.0, rate: jitter(0.1))
        case .whistle:
            playSound("whistle", level: 0.9)
        case .penalty:
            playSound("penalty", level: 1.0)
        case .horn:
            playSound("horn", level: 1.0)
        case .goal:
            playSound("cheer", level: 1.0)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
                self?.playSound("jingle_goal", level: 0.9)
            }
        case .periodEnd:
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { [weak self] in
                self?.playSound("jingle_period", level: 0.85)
            }
        case .gameOver:
            break
        case .faceoffSet:
            if rng.nextFloat() < 0.4 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
                    self?.playSound("organ_rally", level: 0.7)
                }
            }
        case .onFire:
            playSound("fire", level: 1.0)
        case .deke:
            playSound("deke", level: 0.85, rate: jitter(0.1))
        case .glassShatter:
            playSound("glass", level: 1.0, rate: jitter(0.05))
        case .goalieSaveMove:
            playSound("pad_stack", level: 0.9, rate: jitter(0.08))
        }
    }

    func playSkate(intensity: Float) {
        skateToggle = !skateToggle
        let name = skateToggle ? "skate1" : "skate2"
        let v = min(max(0.12 + 0.3 * intensity, 0.1), 0.45)
        playSound(name, level: v, rate: jitter(0.12))
    }

    func playResult(won: Bool?) {
        switch won {
        case true: playSound("jingle_win", level: 0.95)
        case false: playSound("jingle_lose", level: 0.9)
        case nil: playSound("jingle_period", level: 0.85)
        }
    }

    func startCrowd() {
        #if canImport(AVFoundation)
        guard enabled, crowdPlayer == nil else { return }
        if let url = findSoundURL(name: "crowd_loop"), let player = try? AVAudioPlayer(contentsOf: url) {
            player.numberOfLoops = -1
            player.volume = 0.3 * volume
            player.play()
            crowdPlayer = player
        }
        #endif
    }

    func stopCrowd() {
        #if canImport(AVFoundation)
        crowdPlayer?.stop()
        crowdPlayer = nil
        #endif
    }

    func playClick() {
        playSound("button_click", level: 0.6)
    }

    func release() {
        stopCrowd()
        #if canImport(AVFoundation)
        players.removeAll()
        #endif
    }
}
