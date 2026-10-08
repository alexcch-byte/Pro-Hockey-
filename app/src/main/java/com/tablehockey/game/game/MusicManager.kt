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

    /** Per-track trim so the alternate in-play loops sit at the same loudness as music_game. */
    fun gameTrackVolume(resId: Int): Float = when (resId) {
        R.raw.music_game_2 -> GAME_VOLUME * 1.15f
        R.raw.music_clutch -> GAME_VOLUME * 1.1f
        else -> GAME_VOLUME
    }

    private var player: MediaPlayer? = null
    private var currentRes = 0
    private var trackVolume = 1f
    /** Temporary duck multiplier (1 = none), applied on every volume change so repeated play() calls cannot cancel it. */
    private var duckFactor = 1f
    /** 0..1 level of the incoming [player] during a crossfade (1 when not fading). */
    private var fadeIn = 1f
    private var outgoing: MediaPlayer? = null
    private var outgoingVolume = 1f
    private var fadeElapsed = 0
    private var generation = 0
    private var pendingRes = 0
    private var paused = false
    private var menuRefs = 0
    private var lastContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())
    private val restoreVolume = Runnable { synchronized(this) { duckFactor = 1f; applyAll() } }
    private val fadeStep = object : Runnable {
        override fun run() {
            synchronized(this@MusicManager) {
                fadeElapsed += FADE_STEP_MS
                if (fadeElapsed >= FADE_MS) {
                    fadeIn = 1f
                    releaseOutgoing()
                } else {
                    fadeIn = fadeElapsed.toFloat() / FADE_MS
                    handler.postDelayed(this, FADE_STEP_MS.toLong())
                }
                applyAll()
            }
        }
    }

    private const val FADE_MS = 1200
    private const val FADE_STEP_MS = 50

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
            synchronized(this) { applyAll() }
            if (menuRefs > 0) play(context, R.raw.music_menu, MENU_VOLUME)
        }
    }

    /** True when [resId] is the loop currently playing (or arriving). */
    @Synchronized
    fun isCurrent(resId: Int): Boolean = (player != null && currentRes == resId) || pendingRes == resId

    @Synchronized
    fun play(context: Context, resId: Int, volume: Float) {
        lastContext = context.applicationContext
        if (!enabled) return
        val p = player
        if (p != null && currentRes == resId) {
            trackVolume = volume
            applyAll()
            paused = false
            try { if (!p.isPlaying) p.start() } catch (_: Exception) {}
            return
        }
        stop()
        val mp = try { MediaPlayer.create(context.applicationContext, resId) } catch (_: Exception) { null } ?: return
        try {
            mp.isLooping = true
            trackVolume = volume
            paused = false
            player = mp
            currentRes = resId
            fadeIn = 1f
            applyAll()
            mp.start()
        } catch (_: Exception) {
            player = null
            currentRes = 0
            try { mp.release() } catch (_: Exception) {}
        }
    }

    /**
     * Gentle track change for in-play music: the next loop is built on a background thread
     * (no hitch on the game thread) and faded in over ~1.2 s while the old one fades out.
     * Falls back to [play] when nothing is playing yet.
     */
    @Synchronized
    fun switchTo(context: Context, resId: Int, volume: Float) {
        lastContext = context.applicationContext
        if (!enabled) return
        if (player == null) { play(context, resId, volume); return }
        if (currentRes == resId) { play(context, resId, volume); return }
        if (pendingRes == resId) return
        pendingRes = resId
        val gen = ++generation
        val app = context.applicationContext
        Thread {
            val mp = try { MediaPlayer.create(app, resId) } catch (_: Exception) { null }
            handler.post { onNextReady(gen, resId, volume, mp) }
        }.start()
    }

    @Synchronized
    private fun onNextReady(gen: Int, resId: Int, volume: Float, mp: MediaPlayer?) {
        if (gen != generation || mp == null || !enabled) {
            try { mp?.release() } catch (_: Exception) {}
            if (gen == generation) pendingRes = 0
            return
        }
        pendingRes = 0
        val old = player
        // A crossfade already in flight: drop its outgoing player, the half-faded one becomes outgoing.
        releaseOutgoing()
        handler.removeCallbacks(fadeStep)
        try {
            mp.isLooping = true
            val oldLevel = trackVolume * fadeIn
            player = mp
            currentRes = resId
            trackVolume = volume
            if (old != null && !paused) {
                outgoing = old
                outgoingVolume = oldLevel
                fadeIn = 0f
                fadeElapsed = 0
                applyAll()
                mp.start()
                handler.postDelayed(fadeStep, FADE_STEP_MS.toLong())
            } else {
                try { old?.release() } catch (_: Exception) {}
                fadeIn = 1f
                applyAll()
                if (!paused) mp.start()
            }
        } catch (_: Exception) {
            try { mp.release() } catch (_: Exception) {}
        }
    }

    private fun releaseOutgoing() {
        val o = outgoing ?: return
        outgoing = null
        try { o.stop() } catch (_: Exception) {}
        try { o.release() } catch (_: Exception) {}
    }

    @Synchronized
    fun pause() {
        paused = true
        try { player?.let { if (it.isPlaying) it.pause() } } catch (_: Exception) {}
        try { outgoing?.let { if (it.isPlaying) it.pause() } } catch (_: Exception) {}
    }

    @Synchronized
    fun resume() {
        paused = false
        if (!enabled) return
        try { player?.let { if (!it.isPlaying) it.start() } } catch (_: Exception) {}
        try { outgoing?.let { if (!it.isPlaying) it.start() } } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(restoreVolume)
        handler.removeCallbacks(fadeStep)
        generation++
        pendingRes = 0
        duckFactor = 1f
        fadeIn = 1f
        releaseOutgoing()
        val p = player ?: return
        player = null
        currentRes = 0
        try { p.stop() } catch (_: Exception) {}
        try { p.release() } catch (_: Exception) {}
    }

    /** Temporarily lowers the music, e.g. underneath a goal jingle. */
    @Synchronized
    fun duck(level: Float, millis: Long) {
        duckFactor = level
        applyAll()
        handler.removeCallbacks(restoreVolume)
        handler.postDelayed(restoreVolume, millis)
    }

    private fun applyAll() {
        val k = duckFactor * userVolume
        val inV = (trackVolume * fadeIn * k).coerceIn(0f, 1f)
        try { player?.setVolume(inV, inV) } catch (_: Exception) {}
        val outV = (outgoingVolume * (1f - fadeIn) * k).coerceIn(0f, 1f)
        try { outgoing?.setVolume(outV, outV) } catch (_: Exception) {}
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
