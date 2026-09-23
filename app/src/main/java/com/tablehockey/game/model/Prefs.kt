package com.tablehockey.game.model

import android.content.Context

/** Tiny wrapper over SharedPreferences for the audio settings (0..100, 0 = off). */
object Prefs {
    private const val NAME = "settings"
    private const val KEY_SFX_VOLUME = "sfx_volume"
    private const val KEY_MUSIC_VOLUME = "music_volume"

    const val DEFAULT_SFX = 100
    const val DEFAULT_MUSIC = 70

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun sfxVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_SFX_VOLUME, DEFAULT_SFX).coerceIn(0, 100)
    fun musicVolume(ctx: Context): Int = prefs(ctx).getInt(KEY_MUSIC_VOLUME, DEFAULT_MUSIC).coerceIn(0, 100)

    fun setSfxVolume(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt(KEY_SFX_VOLUME, v.coerceIn(0, 100)).apply()
    }

    fun setMusicVolume(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt(KEY_MUSIC_VOLUME, v.coerceIn(0, 100)).apply()
    }

    fun soundEnabled(ctx: Context): Boolean = sfxVolume(ctx) > 0
    fun musicEnabled(ctx: Context): Boolean = musicVolume(ctx) > 0

    private const val KEY_TOURNAMENT = "tournament_state"
    private const val KEY_ARENA = "arena_type"

    fun arenaType(ctx: Context): ArenaType {
        val name = prefs(ctx).getString(KEY_ARENA, ArenaType.INDOOR.name)
        return try { ArenaType.valueOf(name ?: ArenaType.INDOOR.name) } catch (e: Exception) { ArenaType.INDOOR }
    }

    fun setArenaType(ctx: Context, type: ArenaType) {
        prefs(ctx).edit().putString(KEY_ARENA, type.name).apply()
    }

    fun saveTournament(ctx: Context, state: TournamentState) {
        prefs(ctx).edit().putString(KEY_TOURNAMENT, state.toJson()).apply()
    }

    fun getTournament(ctx: Context): TournamentState? {
        val str = prefs(ctx).getString(KEY_TOURNAMENT, null) ?: return null
        return TournamentState.fromJson(str)
    }

    fun clearTournament(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TOURNAMENT).apply()
    }
}
