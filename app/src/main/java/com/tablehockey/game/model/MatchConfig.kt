package com.tablehockey.game.model

import java.io.Serializable

enum class GameMode {
    SINGLE_PLAYER,
    WIFI_HOST,
    WIFI_CLIENT,
    SHOOTOUT
}

enum class ArenaType {
    INDOOR,
    WINTER_POND
}

enum class AiDifficulty {
    EASY,
    MEDIUM,
    HARD
}

/**
 * Immutable description of how a match should be set up. Passed between
 * activities via Intent extras (Serializable keeps it simple across the
 * settings -> lobby -> game hand-off).
 */
data class MatchConfig(
    val mode: GameMode,
    val homeTeam: Int,          // index into TeamInfo.ALL - the local player's club (host in WiFi)
    val awayTeam: Int,          // index into TeamInfo.ALL - AI or the WiFi client
    val periodLengthSeconds: Int,
    val aiDifficulty: AiDifficulty,
    val soundEnabled: Boolean,
    val musicEnabled: Boolean = true,
    val arenaType: ArenaType = ArenaType.INDOOR
) : Serializable {

    companion object {
        const val EXTRA_KEY = "match_config"

        fun default() = MatchConfig(GameMode.SINGLE_PLAYER, TeamInfo.DEFAULT_HOME, TeamInfo.DEFAULT_AWAY, 120, AiDifficulty.MEDIUM, true, true, ArenaType.INDOOR)
    }
}
