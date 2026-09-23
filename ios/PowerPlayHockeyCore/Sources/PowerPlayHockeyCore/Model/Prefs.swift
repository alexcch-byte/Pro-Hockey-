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
    private static let keyArena = "arena_type"
    private static let keyTournament = "tournament_state"

    static func arenaType() -> ArenaType {
        guard let raw = UserDefaults.standard.string(forKey: keyArena),
              let type = ArenaType(rawValue: raw) else {
            return .indoor
        }
        return type
    }

    static func setArenaType(_ type: ArenaType) {
        UserDefaults.standard.set(type.rawValue, forKey: keyArena)
    }

    static func saveTournament(_ state: TournamentState) {
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: keyTournament)
        }
    }

    static func getTournament() -> TournamentState? {
        guard let data = UserDefaults.standard.data(forKey: keyTournament),
              let state = try? JSONDecoder().decode(TournamentState.self, from: data) else {
            return nil
        }
        return state
    }

    static func clearTournament() {
        UserDefaults.standard.removeObject(forKey: keyTournament)
    }

    private static func clamp(_ v: Int) -> Int { min(max(v, 0), 100) }
}
