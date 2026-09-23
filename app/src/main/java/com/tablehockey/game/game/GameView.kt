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
        val w = World(TeamInfo.byIndex(home), TeamInfo.byIndex(away), periodLength)
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
            sim.start()
            simulation = sim
        }
        matchOverReported = false
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
        }
        musicCheckTimer = 0f
    }

    fun release() {
        renderer.release()
    }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (renderer.isPauseHit(event.x, event.y)) {
                listener?.onPauseRequested()
                return true
            }
            val w = world
            if (w != null && renderer.isSwitchShooterHit(event.x, event.y, w, localTeam)) {
                simulation?.cycleShootoutShooter(localTeam)
                return true
            }
        }
        return controls.onTouch(event)
    }

    // ----------------------------------------------------------------- loop

    private fun loop() {
        var lastTime = System.nanoTime()
        var fpsFrames = 0
        var fpsWindowStart = lastTime
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now
            dt = min(dt, 0.05f)
            fpsFrames++
            if (now - fpsWindowStart >= 5_000_000_000L) {
                val fps = fpsFrames * 1_000_000_000.0 / (now - fpsWindowStart)
                android.util.Log.d("PowerPlay", String.format("fps=%.1f", fps))
                fpsFrames = 0
                fpsWindowStart = now
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
                if (!paused) update(w, dt)
                // lockHardwareCanvas() routes through ThreadedRenderer/RenderThread,
                // which on this device can hit a native (uncatchable) abort when
                // something else briefly contends for the same SurfaceView's buffer
                // queue -- observed reliably during the WiFi join flow. The plain
                // software canvas skips that pipeline entirely; this is simple 2D
                // drawing, not GPU-bound, so the perf cost should be small.
                val canvas: Canvas? = try {
                    holder.lockCanvas()
                } catch (_: Exception) {
                    null
                }
                if (canvas != null) {
                    try {
                        synchronized(w) { renderer.draw(canvas, w, localTeam, controls, dt) }
                    } finally {
                        try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                    }
                }
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

            val frameMs = (System.nanoTime() - now) / 1_000_000L
            val sleepMs = max(0L, 16L - frameMs)
            try { Thread.sleep(sleepMs) } catch (_: InterruptedException) {}
        }
    }

    private fun update(w: World, dt: Float) {
        controls.snapshotInto(localInput)
        if (config.mode == GameMode.WIFI_CLIENT) {
            updateClient(w, dt)
        } else {
            updateHost(w, dt)
        }
        updateAmbience(w, dt)
        // Camera follows the puck (leading slightly into its travel).
        val p = w.puck
        val lead = if (p.carrier == null) 0.18f else 0.1f
        renderer.camera.follow(p.x + p.vx * lead, p.y * 0.85f + p.vy * lead * 0.5f, dt)
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
            synchronized(w) { sim.step(dt, stepInputs) }
        }
        handleEvents(w.events)

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
        checkMatchOver(w)
    }

    private fun handleEvents(events: List<GameEvent>) {
        val sm = soundManager
        for (e in events) {
            sm?.handle(e)
            when (e) {
                GameEvent.GOAL -> {
                    MusicManager.duck(0.2f, 4000)
                    renderer.camera.addShake(0.75f)
                }
                GameEvent.PERIOD_END -> MusicManager.duck(0.25f, 3500)
                GameEvent.GAME_OVER -> MusicManager.stop()
                GameEvent.ONE_TIMER -> renderer.camera.addShake(0.6f)
                GameEvent.POST -> renderer.camera.addShake(0.45f)
                GameEvent.HIT -> renderer.camera.addShake(0.35f)
                else -> {}
            }
        }
    }

    fun togglePullGoalie(): Boolean {
        return simulation?.togglePullGoalie(localTeam) ?: false
    }

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
        if (sm != null && !crowdStarted && w.phase != Phase.GAME_OVER) {
            sm.startCrowd()
            crowdStarted = true
        }
        musicCheckTimer -= dt
        if (musicCheckTimer <= 0f) {
            musicCheckTimer = 1f
            if (w.phase != Phase.GAME_OVER) MusicManager.play(context, R.raw.music_game, MusicManager.GAME_VOLUME)
        }
        if (sm != null && w.phase == Phase.PLAY) {
            val s = w.controlledSkater(localTeam)
            val speed = s?.speed ?: 0f
            if (s != null && speed > 8f && s.stunTimer <= 0f) {
                skateTimer -= dt
                if (skateTimer <= 0f) {
                    val intensity = (speed / Skater.MAX_SPEED).coerceIn(0f, 1f)
                    sm.playSkate(intensity)
                    skateTimer = 0.44f - 0.14f * intensity
                }
            } else {
                skateTimer = 0.1f
            }
        }
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
            MusicManager.stop()
            soundManager?.playResult(won)
            post { listener?.onMatchOver(home, away, won) }
        }
    }
}
