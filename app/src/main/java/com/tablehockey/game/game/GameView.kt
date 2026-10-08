package com.tablehockey.game.game

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.tablehockey.game.R
import com.tablehockey.game.model.GameMode
import com.tablehockey.game.model.MatchConfig
import com.tablehockey.game.model.TeamInfo
import com.tablehockey.game.network.GuestLink
import com.tablehockey.game.network.HostLink
import com.tablehockey.game.network.NetCodec
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Surface that runs the match. In SINGLE_PLAYER and WIFI_HOST modes it owns
 * the authoritative [Simulation]; in WIFI_CLIENT mode it mirrors snapshots
 * from the host and only sends controller input.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    interface GameListener {
        fun onPauseRequested()
        fun onMatchOver(homeScore: Int, awayScore: Int, localWon: Boolean?)
        fun onOpponentConnectionLost()
    }

    var listener: GameListener? = null
    var soundManager: SoundManager? = null
    /** Tournament match: ties are not allowed, overtime is uncapped. Set before [configure]. */
    var tournament = false
    /** Pre-game anthem ceremony wanted for this match (setting on, music audible, not a shootout, first tournament game). */
    var anthem = false
    private var anthemPlaying = false

    private lateinit var config: MatchConfig
    private var configured = false

    private val renderer = Renderer(resources.displayMetrics.density)
    private val controls = TouchControls(resources.displayMetrics.density)

    @Volatile private var world: World? = null
    private var simulation: Simulation? = null
    private val localInput = PlayerInput()
    private val remoteInput = PlayerInput()
    private val remoteLock = Any()
    private val stepInputs = arrayOfNulls<PlayerInput>(2)
    private var localTeam = 0

    private var networkServer: HostLink? = null
    private var networkClient: GuestLink? = null
    private var netAccumulator = 0f
    private val clientEvents = ArrayList<GameEvent>()
    // The client and host each race independently from the lobby into this
    // screen after the socket connects; if the client's real "cfg" listener
    // isn't attached yet when the host's one-shot send arrives, it's dropped
    // and the client never builds a World. Resend a few times as insurance.
    private var cfgResendsLeft = 4
    private var cfgResendTimer = 0.4f

    @Volatile private var running = false
    @Volatile private var paused = false
    private var gameThread: Thread? = null
    private var matchOverReported = false
    private var pendingOver: Runnable? = null
    private var pendingOverAt = 0L

    /** True while the finished match is on screen (used to drop stale result-dialog retries). */
    fun isMatchOver(): Boolean = matchOverReported && world?.phase == Phase.GAME_OVER

    private fun cancelPendingOver() {
        pendingOver?.let { removeCallbacks(it) }
        pendingOver = null
    }
    private var crowdStarted = false
    private var musicCheckTimer = 0f
    private var skateTimer = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun configure(matchConfig: MatchConfig, server: HostLink?, client: GuestLink?) {
        config = matchConfig
        networkServer = server
        networkClient = client
        configured = true
        localTeam = if (config.mode == GameMode.WIFI_CLIENT) 1 else 0
        renderer.pullPillEnabled = config.mode != GameMode.WIFI_CLIENT
        renderer.guest = config.mode == GameMode.WIFI_CLIENT

        if (config.mode != GameMode.WIFI_CLIENT) {
            createWorld(config.homeTeam, config.awayTeam, config.periodLengthSeconds)
        }

        networkServer?.listener = object : HostLink.Listener {
            override fun onClientConnected() {
                networkServer?.send(NetCodec.configJson(config.homeTeam, config.awayTeam, config.periodLengthSeconds))
            }
            override fun onClientDisconnected() {
                post { listener?.onOpponentConnectionLost() }
            }
            override fun onMessage(obj: JSONObject) {
                if (obj.optString("t") == "in") {
                    synchronized(remoteLock) { NetCodec.applyInput(obj, remoteInput) }
                }
            }
        }
        // The client may already be connected when the game screen opens.
        if (config.mode == GameMode.WIFI_HOST) {
            networkServer?.send(NetCodec.configJson(config.homeTeam, config.awayTeam, config.periodLengthSeconds))
        }

        networkClient?.listener = object : GuestLink.Listener {
            override fun onConnected() {}
            override fun onConnectFailed(reason: String) { post { listener?.onOpponentConnectionLost() } }
            override fun onDisconnected() { post { listener?.onOpponentConnectionLost() } }
            override fun onMessage(obj: JSONObject) {
                when (obj.optString("t")) {
                    "cfg" -> {
                        if (world == null) {
                            createWorld(obj.optInt("home", 0), obj.optInt("away", 1), obj.optInt("pl", 120))
                        }
                    }
                    "st" -> {
                        val w = world ?: return
                        synchronized(w) {
                            NetCodec.applyState(obj, w, clientEvents)
                        }
                    }
                }
            }
        }
    }

    private fun createWorld(home: Int, away: Int, periodLength: Int) {
        val kits = TeamInfo.matchTeams(home, away)   // away/home road kit if the jerseys clash (same on host and guest)
        val w = World(kits[0], kits[1], periodLength)
        w.isShootout = (config.mode == GameMode.SHOOTOUT)
        w.arenaType = config.arenaType
        w.isHumanTeam[0] = true
        w.isHumanTeam[1] = (config.mode == GameMode.WIFI_HOST || config.mode == GameMode.WIFI_CLIENT)
        if (config.mode == GameMode.WIFI_CLIENT) {
            w.controlled[0] = 0
            w.controlled[1] = 0
            simulation = null
        } else {
            w.controlled[0] = 0
            w.controlled[1] = if (config.mode == GameMode.WIFI_HOST) 0 else -1
            val sim = Simulation(w, AiSettings.forDifficulty(config.aiDifficulty))
            sim.uncappedOvertime = tournament
            sim.start(anthem && config.mode != GameMode.SHOOTOUT)
            simulation = sim
        }
        matchOverReported = false
        cancelPendingOver()
        renderer.camera.snapTo(0f, 0f)
        world = w
    }

    // ------------------------------------------------------------- lifecycle

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.resize(width, height)
        controls.layout(width, height)
        // surfaceCreated() can fire before the surface is actually usable on
        // some OEM builds; surfaceChanged() (which always carries real
        // dimensions) is the more reliable "ready to render" signal, so the
        // loop starts here instead, once, rather than in surfaceCreated().
        if (gameThread == null) {
            gameThread = Thread({ loop() }, "GameLoop").also { it.start() }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { gameThread?.join(800) } catch (_: InterruptedException) {}
        gameThread = null
        soundManager?.stopCrowd()
        crowdStarted = false
    }

    fun pause() {
        paused = true
        controls.reset()
        soundManager?.stopCrowd()
        crowdStarted = false
        MusicManager.pause()
    }

    fun resume() {
        paused = false
        MusicManager.resume()
    }

    fun restartMatch() {
        val w = world ?: return
        if (config.mode == GameMode.WIFI_CLIENT) return
        synchronized(w) {
            for (t in w.teams) { t.score = 0; t.shots = 0 }
            w.teams[0].attackDir = 1f
            w.teams[1].attackDir = -1f
            w.period = 1
            w.overtime = false
            w.clock = w.periodLength.toFloat()
            w.banner = null
            simulation?.start()
            matchOverReported = false
            cancelPendingOver()
        }
        clutchLatched = false
        audio { activeTrack = 0 }
        musicCheckTimer = 0f
    }

    fun release() {
        renderer.release()
    }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Once the match is over: a tap skips the trophy window; pause is not available.
            if (world?.phase == Phase.GAME_OVER) {
                val r = pendingOver
                if (r != null && android.os.SystemClock.uptimeMillis() - pendingOverAt > 700L) {
                    removeCallbacks(r)
                    r.run()
                }
                return true
            }
            if (renderer.isPauseHit(event.x, event.y)) {
                listener?.onPauseRequested()
                return true
            }
            if (world?.phase == Phase.ANTHEM) {
                // Tap anywhere skips the ceremony. Only the host can: a guest waits for the host's timer or tap.
                if (config.mode != GameMode.WIFI_CLIENT) {
                    simulation?.skipAnthem()
                    audio { MusicManager.stopAnthem() }
                }
                return true
            }
            val w = world
            if (w != null && renderer.isPullHit(event.x, event.y, w, localTeam)) {
                togglePullGoalie()
                return true
            }
            if (w != null && renderer.isSwitchShooterHit(event.x, event.y, w, localTeam)) {
                simulation?.cycleShootoutShooter(localTeam)
                return true
            }
        }
        return controls.onTouch(event)
    }

    // ----------------------------------------------------------------- loop

    /**
     * Single-device matches draw through the GPU (lockHardwareCanvas); on a
     * Fire HD 8 the software canvas managed only 15-18 fps. Network matches
     * keep the software canvas: see the note in loop() about the RenderThread
     * abort seen during the WiFi join flow.
     */
    private val gpuCanvas: Boolean
        get() = configured && (config.mode == GameMode.SINGLE_PLAYER || config.mode == GameMode.SHOOTOUT) &&
            !gpuCanvasFailed
    @Volatile private var gpuCanvasFailed = false

    private var dbgSim = 0L; private var dbgEv = 0L; private var dbgU = 0L; private var dbgL = 0L; private var dbgR = 0L; private var dbgP = 0L

    private fun loop() {
        var lastTime = System.nanoTime()
        var fpsFrames = 0
        var fpsWindowStart = lastTime
        var updateNs = 0L
        var drawNs = 0L
        var lockNs = 0L
        var recordNs = 0L
        var slowFrames = 0
        var worstNs = 0L
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now
            dt = min(dt, 0.05f)
            fpsFrames++
            if (now - fpsWindowStart >= 5_000_000_000L) {
                val fps = fpsFrames * 1_000_000_000.0 / (now - fpsWindowStart)
                // draw = lock the surface + record the frame + post it (post waits for
                // the GPU/compositor, so a big post means the GPU side is the bottleneck).
                val ms = 1e6 * fpsFrames
                android.util.Log.d("PowerPlay", String.format(
                    "fps=%.1f update=%.1fms draw=%.1fms (lock %.1f, record %.1f, post %.1f; %s canvas)", fps,
                    updateNs / ms, drawNs / ms, lockNs / ms, recordNs / ms, (drawNs - lockNs - recordNs) / ms,
                    if (gpuCanvas) "gpu" else "cpu"))
                android.util.Log.d("PowerPlay", String.format("hitches: %d frames over 25ms, worst %.1fms",
                    slowFrames, worstNs / 1e6))
                slowFrames = 0
                worstNs = 0L
                simulation?.let { android.util.Log.d("PowerPlay", it.statsLine()) }
                fpsFrames = 0
                fpsWindowStart = now
                updateNs = 0L
                drawNs = 0L
                lockNs = 0L
                recordNs = 0L
            }

            val w = world
            // The surface can briefly be not-yet-attached (or mid-teardown) around
            // activity/window transitions; submitting a hardware-canvas frame to it
            // anyway is a native, uncatchable abort ("drawRenderNode called on a
            // context with no surface!"), not a Java exception, so it must be
            // avoided rather than caught. Skip the frame instead.
            if (!holder.surface.isValid) {
                try { Thread.sleep(16) } catch (_: InterruptedException) {}
                continue
            }
            if (w != null && configured) {
                val t0 = System.nanoTime()
                if (!paused) update(w, dt)
                val t1 = System.nanoTime()
                dbgU = t1 - t0
                updateNs += t1 - t0
                // lockHardwareCanvas() routes through ThreadedRenderer/RenderThread,
                // which on this device can hit a native (uncatchable) abort when
                // something else briefly contends for the same SurfaceView's buffer
                // queue -- observed reliably during the WiFi join flow. The plain
                // software canvas skips that pipeline entirely, so network matches
                // use it. It is much slower on the Fire tablets, though, so
                // single-device matches keep the GPU.
                val gpu = gpuCanvas
                val canvas: Canvas? = try {
                    if (gpu) holder.lockHardwareCanvas() else holder.lockCanvas()
                } catch (e: Exception) {
                    if (gpu) {
                        android.util.Log.w("PowerPlay", "GPU canvas unavailable, using software", e)
                        gpuCanvasFailed = true
                    }
                    null
                }
                val t2 = System.nanoTime()
                dbgL = t2 - t1
                if (canvas != null) {
                    try {
                        renderer.hudFrozen = paused
                        synchronized(w) { renderer.draw(canvas, w, localTeam, controls, dt) }
                    } finally {
                        val t3 = System.nanoTime()
                        recordNs += t3 - t2
                        dbgR = t3 - t2
                        try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                        dbgP = System.nanoTime() - t3
                    }
                }
                lockNs += t2 - t1
                drawNs += System.nanoTime() - t1
            } else {
                val canvas = try { holder.lockCanvas() } catch (_: Exception) { null }
                if (canvas != null) {
                    try {
                        canvas.drawColor(android.graphics.Color.parseColor("#0B1424"))
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            }

            val frameNs = System.nanoTime() - now
            if (frameNs > 25_000_000L) {
                slowFrames++
                android.util.Log.d("PowerPlay", String.format("SLOW frame %.1f: update %.1f lock %.1f record %.1f post %.1f dt=%.1f phase=%s", frameNs / 1e6, dbgU / 1e6, dbgL / 1e6, dbgR / 1e6, dbgP / 1e6, dt * 1000, world?.phase))
            }
            if (frameNs > worstNs) worstNs = frameNs
            val frameMs = frameNs / 1_000_000L
            val sleepMs = max(0L, 16L - frameMs)
            try { Thread.sleep(sleepMs) } catch (_: InterruptedException) {}
        }
    }

    private fun update(w: World, dt: Float) {
        controls.snapshotInto(localInput)
        val ta = System.nanoTime()
        if (config.mode == GameMode.WIFI_CLIENT) {
            updateClient(w, dt)
        } else {
            updateHost(w, dt)
        }
        val tb = System.nanoTime()
        updateAmbience(w, dt)
        updateAnthem(w)
        val tc = System.nanoTime()
        if (tc - ta > 15_000_000L) android.util.Log.d("PowerPlay", String.format("SLOWUPD host %.1f (sim %.1f ev %.1f) ambience %.1f", (tb - ta) / 1e6, dbgSim / 1e6, dbgEv / 1e6, (tc - tb) / 1e6))
        // Camera follows the puck (leading slightly into its travel).
        val p = w.puck
        val lead = if (p.carrier == null) 0.18f else 0.1f
        renderer.camera.follow(p.x + p.vx * lead, p.y * 0.85f + p.vy * lead * 0.5f, dt)
    }

    /** Starts the anthem audio when the pre-game ceremony begins and fades it out when the phase ends. */
    private fun updateAnthem(w: World) {
        if (w.phase == Phase.ANTHEM) {
            if (!anthemPlaying) {
                anthemPlaying = true
if (anthem) {
                    val ctx = context
                    audio {
                        if (!MusicManager.playAnthem(ctx)) {
                            // No audio to wait for: don't hold the players for 24 silent seconds.
                            synchronized(w) { if (simulation != null && w.phase == Phase.ANTHEM && w.phaseTimer > 2f) w.phaseTimer = 2f }
                        }
                    }
                }
            }
        } else if (anthemPlaying) {
            anthemPlaying = false
            audio { MusicManager.stopAnthem() }
        }
    }

    private fun updateHost(w: World, dt: Float) {
        val sim = simulation ?: return
        stepInputs[0] = localInput
        if (config.mode == GameMode.WIFI_HOST) {
            synchronized(remoteLock) {
                stepInputs[1] = remoteInput
                synchronized(w) { sim.step(dt, stepInputs) }
            }
        } else {
            stepInputs[1] = null
            val ts = System.nanoTime()
            synchronized(w) { sim.step(dt, stepInputs) }
            dbgSim = System.nanoTime() - ts
        }
        val te = System.nanoTime()
        handleEvents(w.events)
        dbgEv = System.nanoTime() - te

        if (config.mode == GameMode.WIFI_HOST) {
            if (cfgResendsLeft > 0) {
                cfgResendTimer -= dt
                if (cfgResendTimer <= 0f) {
                    cfgResendTimer = 0.4f
                    cfgResendsLeft--
                    networkServer?.send(NetCodec.configJson(config.homeTeam, config.awayTeam, config.periodLengthSeconds))
                }
            }
            netAccumulator += dt
            val hz = networkServer?.stateHz ?: 30
            if (netAccumulator >= 1f / hz || w.events.isNotEmpty()) {
                netAccumulator = 0f
                networkServer?.send(NetCodec.stateJson(w))
            }
        }
        checkMatchOver(w)
    }

    private fun updateClient(w: World, dt: Float) {
        synchronized(w) {
            // Interpolate toward the latest snapshot; derive velocities from the motion.
            val k = 1f - exp(-dt * 18f)
            for (s in w.allSkaters) {
                val ox = s.x
                val oy = s.y
                s.x += (s.netX - s.x) * k
                s.y += (s.netY - s.y) * k
                if (dt > 0f) { s.vx = (s.x - ox) / dt; s.vy = (s.y - oy) / dt }
                s.facing = PhysicsEngine.turnToward(s.facing, s.netFacing, dt * 14f)
                if (s.swingTimer > 0f) s.swingTimer = max(0f, s.swingTimer - dt)
                if (s.speed > 1.2f) s.stride += s.speed * dt
            }
            val p = w.puck
            p.x += (p.netX - p.x) * k
            p.y += (p.netY - p.y) * k
            if (w.bannerTimer > 0f) {
                w.bannerTimer = max(0f, w.bannerTimer - dt)
                if (w.bannerTimer <= 0f) { w.banner = null; w.bannerSub = null }
            }
            if (w.phase == Phase.PLAY && !w.overtime) w.clock = max(0f, w.clock - dt)
            handleEvents(clientEvents)
            clientEvents.clear()
        }

        netAccumulator += dt
        val hasPulse = localInput.shootRelease || localInput.pass || localInput.hit
        val hz = networkClient?.inputHz ?: 30
        if (netAccumulator >= 1f / hz || hasPulse) {
            netAccumulator = 0f
            networkClient?.send(NetCodec.inputJson(localInput))
            localInput.clearPulses()
        }
        if (w.phase != Phase.GAME_OVER && matchOverReported) {
            // Host started a rematch: allow the next game over to be reported.
            matchOverReported = false
            cancelPendingOver()
        }
        checkMatchOver(w)
    }

    /** In-play music loop for this match, picked once so it does not change mid-game. */
    private val gameTrack = if (kotlin.random.Random.nextBoolean()) R.raw.music_game else R.raw.music_game_2

    private val lastScores = intArrayOf(0, 0)
    private var clutchLatched = false
    private var activeTrack = 0

    private fun handleEvents(events: List<GameEvent>) {
        val sm = soundManager
        val wd = world
        sm?.localTeam = localTeam
        for (e in events) {
            val about = when {
                wd == null -> -1
                e == GameEvent.GOAL -> when {
                    wd.teams[0].score > lastScores[0] -> 0
                    wd.teams[1].score > lastScores[1] -> 1
                    else -> -1
                }
                e == GameEvent.PENALTY -> wd.penaltyTeam
                else -> -1
            }
            sm?.handle(e, about)
            when (e) {
                GameEvent.GOAL -> {
                    audio { MusicManager.duck(0.2f, 4000) }
                    renderer.camera.addShake(0.75f)
                }
                GameEvent.PERIOD_END -> audio { MusicManager.duck(0.25f, 3500) }
                GameEvent.GAME_OVER -> audio { MusicManager.stop() }
                GameEvent.ONE_TIMER -> renderer.camera.addShake(0.6f)
                GameEvent.POST -> renderer.camera.addShake(0.45f)
                GameEvent.HIT -> renderer.camera.addShake(0.35f)
                else -> {}
            }
        }
        if (wd != null) { lastScores[0] = wd.teams[0].score; lastScores[1] = wd.teams[1].score }
    }

    fun togglePullGoalie(): Boolean {
        val w = world ?: return false
        val before = w.goaliePulled[localTeam]
        val after = synchronized(w) { simulation?.togglePullGoalie(localTeam) ?: before }
        if (after != before) soundManager?.playToggle()
        return after
    }

    fun isShootout(): Boolean = world?.isShootout ?: false

    fun isGoaliePulled(): Boolean {
        return world?.goaliePulled?.getOrNull(localTeam) ?: false
    }

    fun getArenaType(): com.tablehockey.game.model.ArenaType = world?.arenaType ?: com.tablehockey.game.model.ArenaType.INDOOR

    fun toggleArena(): com.tablehockey.game.model.ArenaType {
        val w = world ?: return com.tablehockey.game.model.ArenaType.INDOOR
        val next = if (w.arenaType == com.tablehockey.game.model.ArenaType.INDOOR) com.tablehockey.game.model.ArenaType.WINTER_POND else com.tablehockey.game.model.ArenaType.INDOOR
        w.arenaType = next
        return next
    }

    /** Crowd bed, looping game music and the controlled skater's stride scrapes. */
    private fun updateAmbience(w: World, dt: Float) {
        val sm = soundManager
        sm?.updateMix(w, renderer.camera, dt)
        if (sm != null && !crowdStarted && w.phase != Phase.GAME_OVER && w.phase != Phase.ANTHEM) {
            sm.startCrowd()
            crowdStarted = true
        }
        musicCheckTimer -= dt
        if (musicCheckTimer <= 0f) {
            musicCheckTimer = 1f
            if (w.phase == Phase.GAME_OVER) {
                clutchLatched = false
                audio { activeTrack = 0 }
            } else {
                // Two rotating in-play loops; overtime and a tight last stretch get the faster "clutch" loop.
                // Clutch latches for the rest of the match, and the swap only happens while the puck is dead.
                val close = kotlin.math.abs(w.teams[0].score - w.teams[1].score) <= 1
                val lastStretch = minOf(60f, 0.33f * w.periodLength)
                if (w.overtime || (w.period >= 3 && w.clock < lastStretch && close)) clutchLatched = true
                val wanted = if (clutchLatched) R.raw.music_clutch else gameTrack
                val dead = w.phase != Phase.PLAY
                // MediaPlayer calls (create/start/setVolume are binder round trips) stay off the game thread.
                audio { musicStep(wanted, dead) }
            }
        }
        if (sm != null && w.phase == Phase.PLAY) {
            val s = w.controlledSkater(localTeam)
            val speed = s?.speed ?: 0f
            if (s != null && speed > 8f && s.stunTimer <= 0f) {
                skateTimer -= dt
                if (skateTimer <= 0f) {
                    val intensity = (speed / Skater.MAX_SPEED).coerceIn(0f, 1f)
                    sm.playSkate(intensity, s.x)
                    skateTimer = 0.44f - 0.14f * intensity
                }
            } else {
                skateTimer = 0.1f
            }
        }
    }

    /** Runs on the audio thread; [activeTrack] is only touched there (and reset via [audio]). */
    private fun musicStep(wanted: Int, dead: Boolean) {
        if (activeTrack == 0 || !MusicManager.isCurrent(activeTrack)) {
            activeTrack = if (activeTrack == 0) wanted else activeTrack
            MusicManager.play(context, activeTrack, MusicManager.gameTrackVolume(activeTrack))
        } else if (wanted != activeTrack && dead) {
            activeTrack = wanted
            MusicManager.switchTo(context, wanted, MusicManager.gameTrackVolume(wanted))
        } else {
            MusicManager.play(context, activeTrack, MusicManager.gameTrackVolume(activeTrack))
        }
    }

    private fun audio(block: () -> Unit) {
        val sm = soundManager
        if (sm != null) sm.audio.post(block) else block()
    }

    private fun checkMatchOver(w: World) {
        if (w.phase == Phase.GAME_OVER && !matchOverReported) {
            matchOverReported = true
            val home = w.teams[0].score
            val away = w.teams[1].score
            val localScore = if (localTeam == 0) home else away
            val otherScore = if (localTeam == 0) away else home
            val won: Boolean? = if (localScore == otherScore) null else localScore > otherScore
            soundManager?.stopCrowd()
            crowdStarted = false
            audio { MusicManager.stop() }
            soundManager?.playResult(won)
            // Delay the result dialog so the trophy / confetti finish is visible first.
            val r = Runnable { pendingOver = null; listener?.onMatchOver(home, away, won) }
            pendingOver = r
            pendingOverAt = android.os.SystemClock.uptimeMillis()
            postDelayed(r, 3800L)
        }
    }
}
