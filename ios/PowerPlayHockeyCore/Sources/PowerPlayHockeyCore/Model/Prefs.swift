import Foundation

/// Tiny wrapper over UserDefaults for the audio settings (0...100, 0 = off).
/// UserDefaults.standard is process-global, so unlike the Android version
/// this needs no Context argument.
enum Prefs {
    private static let keySfxVolume = "sfx_volume"
    private static let keyMusicVolume = "music_volume"

    static let defaultSfx = 100
    static let defaultMusic = 70

    static func sfxVolume() -> Int {
        clamp(UserDefaults.standard.object(forKey: keySfxVolume) as? Int ?? defaultSfx)
    }

    static func musicVolume() -> Int {
        clamp(UserDefaults.standard.object(forKey: keyMusicVolume) as? Int ?? defaultMusic)
    }

    static func setSfxVolume(_ v: Int) {
        UserDefaults.standard.set(clamp(v), forKey: keySfxVolume)
    }

    static func setMusicVolume(_ v: Int) {
        UserDefaults.standard.set(clamp(v), forKey: keyMusicVolume)
    }

    static func soundEnabled() -> Bool { sfxVolume() > 0 }
    static func musicEnabled() -> Bool { musicVolume() > 0 }

    private static func clamp(_ v: Int) -> Int { min(max(v, 0), 100) }
}
