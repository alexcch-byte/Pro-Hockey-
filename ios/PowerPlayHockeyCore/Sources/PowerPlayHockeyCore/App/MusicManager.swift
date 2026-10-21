import Foundation
#if canImport(AVFoundation)
import AVFoundation
#endif

/// Looping background music engine with menu/game tracks, volume management, and ducking.
public final class MusicManager {
    public static let shared = MusicManager()

    public static let menuVolume: Float = 0.85
    public static let gameVolume: Float = 0.55

    public var userVolume: Float {
        get { Float(Prefs.musicVolume()) / 100.0 }
        set {
            Prefs.setMusicVolume(Int((newValue * 100).rounded()))
            if newValue <= 0 {
                releaseAnthem()
                stop()
            } else {
                applyVolume(trackVolume)
                if menuRefs > 0 { play("music_menu", volume: Self.menuVolume) }
            }
        }
    }

    public var enabled: Bool { userVolume > 0 }

    #if canImport(AVFoundation)
    private var player: AVAudioPlayer?
    private var anthemPlayer: AVAudioPlayer?
    #endif
    private var anthemLevel: Float = 0
    private var anthemFadeLeft: Double = 0
    private var anthemFadeItem: DispatchWorkItem?
    private var pausedByUser = false
    private var currentTrack = ""
    public private(set) var activeTrack = ""
    private var trackVolume: Float = 1.0
    private var duckFactor: Float = 1.0
    private var duckWorkItem: DispatchWorkItem?
    private var menuRefs = 0

    public init() {}

    public static func trackDefaultVolume(_ track: String) -> Float {
        switch track {
        case "music_menu": return menuVolume
        case "music_clutch": return gameVolume * 1.1
        default: return gameVolume
        }
    }

    public func menuStarted() {
        menuRefs += 1
        if menuRefs == 1 {
            play("music_menu", volume: Self.menuVolume)
        }
    }

    public func menuStopped() {
        menuRefs = max(0, menuRefs - 1)
        // A menu screen disappearing after the game screen opened must not kill the game loop or the anthem.
        if menuRefs == 0 && currentTrack == "music_menu" {
            stop()
        }
    }

    public func play(_ name: String, volume: Float) {
        guard enabled else { return }
        trackVolume = volume
        activeTrack = name
        if currentTrack == name {
            #if canImport(AVFoundation)
            if let p = player, !p.isPlaying { p.play() }
            #endif
            applyVolume(volume)
            return
        }

        stop()
        currentTrack = name
        activeTrack = name

        #if canImport(AVFoundation)
        let extensions = ["mp3", "m4a", "wav", "ogg", "caf"]
        var soundURL: URL?
        for ext in extensions {
            if let url = Bundle.main.url(forResource: name, withExtension: ext) {
                soundURL = url
                break
            }
            #if SWIFT_PACKAGE
            if let url = Bundle.module.url(forResource: name, withExtension: ext) {
                soundURL = url
                break
            }
            #endif
        }

        guard let url = soundURL, let p = try? AVAudioPlayer(contentsOf: url) else { return }
        p.numberOfLoops = -1
        p.volume = min(max(volume * effectiveFactor, 0), 1)
        p.play()
        self.player = p
        #endif
    }

    public func switchTo(_ name: String, volume: Float) {
        guard enabled else { return }
        if activeTrack == name && currentTrack == name {
            applyVolume(volume)
            return
        }
        play(name, volume: volume)
    }

    public func pause() {
        pausedByUser = true
        #if canImport(AVFoundation)
        player?.pause()
        anthemPlayer?.pause()
        #endif
    }

    public func resume() {
        pausedByUser = false
        #if canImport(AVFoundation)
        if enabled { player?.play() }
        anthemPlayer?.play()
        #endif
    }

    public func stop() {
        duckWorkItem?.cancel()
        duckWorkItem = nil
        duckFactor = 1.0
        #if canImport(AVFoundation)
        player?.stop()
        player = nil
        #endif
        currentTrack = ""
        activeTrack = ""
    }

    public func duck(level: Float, duration: Double) {
        duckWorkItem?.cancel()
        duckFactor = level
        applyVolume(trackVolume)
        let item = DispatchWorkItem { [weak self] in
            guard let self = self else { return }
            self.duckFactor = 1.0
            self.applyVolume(self.trackVolume)
        }
        duckWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + duration, execute: item)
    }

    /// Multiplier applied to the background loop: it is fully silent while the pre-game anthem plays.
    private var effectiveFactor: Float {
        #if canImport(AVFoundation)
        if anthemPlayer != nil { return 0 }
        #endif
        return userVolume * duckFactor
    }

    private func applyVolume(_ volume: Float) {
        #if canImport(AVFoundation)
        player?.volume = min(max(volume * effectiveFactor, 0), 1)
        #endif
    }

    // ------------------------------------------------------------ pre-game anthem

    /// True while the anthem is loaded (it releases itself once it has finished playing).
    public var anthemActive: Bool {
        #if canImport(AVFoundation)
        if let p = anthemPlayer, !p.isPlaying, !pausedByUser { releaseAnthem() }
        return anthemPlayer != nil
        #else
        return false
        #endif
    }

    private func releaseAnthem() {
        anthemFadeItem?.cancel()
        anthemFadeItem = nil
        #if canImport(AVFoundation)
        guard let p = anthemPlayer else { return }
        anthemPlayer = nil
        p.stop()
        #endif
        // give the game music back
        applyVolume(trackVolume)
    }

    /// Plays the pre-game anthem once at the user's music level (x 0.9); the background loop is silent meanwhile.
    /// Returns false when it cannot play (music off, or the file will not load) so the caller can shorten the hold.
    @discardableResult
    public func playAnthem() -> Bool {
        releaseAnthem()
        guard userVolume > 0 else { return false }
        #if canImport(AVFoundation)
        var url: URL?
        for ext in ["wav", "mp3", "m4a", "caf", "ogg"] {
            if let u = Bundle.main.url(forResource: "anthem_ocanada", withExtension: ext) { url = u; break }
            #if SWIFT_PACKAGE
            if let u = Bundle.module.url(forResource: "anthem_ocanada", withExtension: ext) { url = u; break }
            #endif
        }
        guard let fileURL = url, let p = try? AVAudioPlayer(contentsOf: fileURL) else { return false }
        anthemLevel = min(max(userVolume * 0.9, 0), 1)
        p.volume = anthemLevel
        anthemPlayer = p
        applyVolume(trackVolume)   // mutes the loop
        if !pausedByUser {
            if !p.play() { releaseAnthem(); return false }
        }
        return true
        #else
        return false
        #endif
    }

    /// Fades the anthem out over about 400 ms (tap to skip, or the ceremony ended) and gives the loop back.
    public func stopAnthem() {
        #if canImport(AVFoundation)
        guard anthemPlayer != nil else { return }
        anthemFadeLeft = 0.4
        scheduleAnthemFade()
        #endif
    }

    private func scheduleAnthemFade() {
        anthemFadeItem?.cancel()
        let item = DispatchWorkItem { [weak self] in self?.anthemFadeStep() }
        anthemFadeItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: item)
    }

    private func anthemFadeStep() {
        #if canImport(AVFoundation)
        guard let p = anthemPlayer else { return }
        anthemFadeLeft -= 0.05
        if anthemFadeLeft <= 0 {
            releaseAnthem()
        } else {
            p.volume = anthemLevel * Float(anthemFadeLeft / 0.4)
            scheduleAnthemFade()
        }
        #endif
    }
}
