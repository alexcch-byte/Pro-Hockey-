enum GameMode {
    case singlePlayer
    case wifiHost
    case wifiClient
}

enum AiDifficulty {
    case easy
    case medium
    case hard
}

/// Immutable description of how a match should be set up. Passed between
/// screens (settings -> lobby -> game) the way MatchConfig crosses Android
/// Intent extras.
struct MatchConfig {
    let mode: GameMode
    let homeTeam: Int          // index into TeamInfo.ALL - the local player's club (host in WiFi)
    let awayTeam: Int          // index into TeamInfo.ALL - AI or the WiFi client
    let periodLengthSeconds: Int
    let aiDifficulty: AiDifficulty
    let soundEnabled: Bool
    let musicEnabled: Bool

    init(mode: GameMode, homeTeam: Int, awayTeam: Int, periodLengthSeconds: Int, aiDifficulty: AiDifficulty, soundEnabled: Bool, musicEnabled: Bool = true) {
        self.mode = mode
        self.homeTeam = homeTeam
        self.awayTeam = awayTeam
        self.periodLengthSeconds = periodLengthSeconds
        self.aiDifficulty = aiDifficulty
        self.soundEnabled = soundEnabled
        self.musicEnabled = musicEnabled
    }

    static func makeDefault() -> MatchConfig {
        MatchConfig(mode: .singlePlayer, homeTeam: TeamInfo.DEFAULT_HOME, awayTeam: TeamInfo.DEFAULT_AWAY, periodLengthSeconds: 120, aiDifficulty: .medium, soundEnabled: true)
    }
}
