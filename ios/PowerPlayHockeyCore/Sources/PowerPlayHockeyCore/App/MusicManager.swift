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
    private var trackVolume: Float = 1.0
    private var menuRefs = 0

    public init() {}

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
        if currentTrack == name {
            #if canImport(AVFoundation)
            if let p = player, !p.isPlaying { p.play() }
            #endif
            applyVolume(volume)
            return
        }

        stop()
        currentTrack = name

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
        p.volume = volume * userVolume
        p.play()
        self.player = p
        #endif
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
        #if canImport(AVFoundation)
        player?.stop()
        player = nil
        #endif
        currentTrack = ""
    }

    public func duck(level: Float, duration: Double) {
        applyVolume(trackVolume * level)
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
            guard let self = self else { return }
            self.applyVolume(self.trackVolume)
        }
    }

    private func applyVolume(_ volume: Float) {
        #if canImport(AVFoundation)
        player?.volume = min(max(volume * userVolume, 0), 1)
        #endif
    }
}
