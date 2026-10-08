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
    #endif
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
        if menuRefs == 0 {
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
        p.volume = min(max(volume * userVolume * duckFactor, 0), 1)
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
        #if canImport(AVFoundation)
        player?.pause()
        #endif
    }

    public func resume() {
        #if canImport(AVFoundation)
        if enabled { player?.play() }
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

    private func applyVolume(_ volume: Float) {
        #if canImport(AVFoundation)
        player?.volume = min(max(volume * userVolume * duckFactor, 0), 1)
        #endif
    }
}
