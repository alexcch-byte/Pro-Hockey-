package com.tablehockey.game.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.tablehockey.game.R
import com.tablehockey.game.model.Prefs

/**
 * Process-wide looping music (one MediaPlayer) plus the menu click blip.
 * Menu screens call [menuStarted] / [menuStopped] from onStart / onStop so the
 * theme keeps playing across menu screens and pauses when the app leaves them.
 * [userVolume] is the player's music slider (0 = off) applied on top of the
 * per-track mix level.
 */
object MusicManager {
    const val MENU_VOLUME = 0.85f
    const val GAME_VOLUME = 0.55f

    private var player: MediaPlayer? = null
    private var currentRes = 0
    private var trackVolume = 1f
    private var menuRefs = 0
    private var lastContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val restoreVolume = Runnable { applyVolume(trackVolume) }

    private var clickPool: SoundPool? = null
    private var clickId = 0

    @Volatile
    var userVolume = 1f
        private set

    val enabled: Boolean get() = userVolume > 0f

    /** Sets the music slider level (0..1). At 0 the music stops; above 0 it restarts when a screen asks for it. */
    fun setUserVolume(context: Context, v: Float) {
        userVolume = v.coerceIn(0f, 1f)
        if (userVolume <= 0f) {
            stop()
        } else {
            applyVolume(trackVolume)
            if (menuRefs > 0) play(context, R.raw.music_menu, MENU_VOLUME)
        }
    }

    @Synchronized
    fun play(context: Context, resId: Int, volume: Float) {
        lastContext = context.applicationContext
        if (!enabled) return
        val p = player
        if (p != null && currentRes == resId) {
            trackVolume = volume
            applyVolume(volume)
            try { if (!p.isPlaying) p.start() } catch (_: Exception) {}
            return
        }
        stop()
        val mp = try { MediaPlayer.create(context.applicationContext, resId) } catch (_: Exception) { null } ?: return
        try {
            mp.isLooping = true
            trackVolume = volume
            mp.setVolume(volume * userVolume, volume * userVolume)
            mp.start()
            player = mp
            currentRes = resId
        } catch (_: Exception) {
            try { mp.release() } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun pause() {
        try { player?.let { if (it.isPlaying) it.pause() } } catch (_: Exception) {}
    }

    @Synchronized
    fun resume() {
        if (!enabled) return
        try { player?.let { if (!it.isPlaying) it.start() } } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(restoreVolume)
        val p = player ?: return
        player = null
        currentRes = 0
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }

    /** Temporarily lowers the music, e.g. underneath a goal jingle. */
    fun duck(level: Float, millis: Long) {
        applyVolume(trackVolume * level)
        handler.removeCallbacks(restoreVolume)
        handler.postDelayed(restoreVolume, millis)
    }

    private fun applyVolume(v: Float) {
        val out = (v * userVolume).coerceIn(0f, 1f)
        try { player?.setVolume(out, out) } catch (_: Exception) {}
    }

    fun menuStarted(context: Context) {
        menuRefs++
        userVolume = Prefs.musicVolume(context) / 100f
        if (enabled) play(context, R.raw.music_menu, MENU_VOLUME) else stop()
    }

    fun menuStopped() {
        menuRefs--
        if (menuRefs <= 0) {
            menuRefs = 0
            pause()
        }
    }

    /** 8-bit blip for menu buttons, at the sound-FX slider level. */
    fun click(context: Context) {
        val level = Prefs.sfxVolume(context) / 100f
        if (level <= 0f) return
        val pool = clickPool ?: SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also {
                clickPool = it
                clickId = it.load(context.applicationContext, R.raw.button_click, 1)
            }
        pool.play(clickId, 0.6f * level, 0.6f * level, 0, 0, 1f)
    }
}
