package com.tablehockey.game.game

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Which way the rink is seen. Purely a local, per-match view choice (never sent over the network). */
enum class ViewMode { SIDE, LONG }

/**
 * Broadcast-style follow camera with a real perspective projection, in two view modes.
 * The world (feet, origin at centre ice, +x along the rink, +y across it) is the same in both.
 *
 * SIDE view (original): the rink is seen from behind the near side boards. World +x is screen
 * right (home attacks right), +y is screen down. A world point projects as a homography:
 *
 *   f  = 1 / (1 - persp * (wy - y))            depth factor, < 1 far away, > 1 near
 *   sx = screenW/2 + (wx - x) * scale * f
 *   sy = anchorY   + (wy - y) * scale * vk * f
 *
 * LONG view: seen from behind the home net, looking down the length of the rink: a pure 90 degree
 * rotation (world +x is screen UP / far, world +y is screen RIGHT, home always at the bottom
 * attacking up). A pinhole camera pitched down by T at distance D from the focus. With
 * Y = wx - x, u = wy - y and z = height above the ice:
 *
 *   den = D + Y * cos(T) - z * sin(T)
 *   f   = D / den
 *   sx  = screenW/2 + u * scale * f
 *   sy  = anchorY  - (Y * sin(T) + z * cos(T)) * scale * f
 *
 * Both modes share the same API: [px]/[py] project a world point (optionally lifted by z feet),
 * [ppfAt] gives screen pixels per foot for upright things standing at that point, [vkAt] the
 * vertical squash of ground circles there (ry = rx * vk).
 */
class Camera {
    var mode: ViewMode = ViewMode.LONG
        private set
    /** Cached `mode == LONG` for the projection hot path. */
    var long = true
        private set

    /** Focus point on the ice. In LONG view only x moves (y stays 0, so the full width is always visible). */
    var x = 0f
    var y = 0f
    /** Screen pixels per foot (across the rink) at the focus depth. */
    var scale = 10f
    /** Fit scale without the dynamic zoom (sprites are baked from this, so zooming never rebuilds them). */
    var baseScale = 10f
        private set
    /** LONG view dynamic zoom multiplier (scale = baseScale * zoom). */
    var zoom = 1f
        private set
    private var zoomMax = 1.08f
    /** Close net zoom: how much higher (fraction of screen height) the far boards may sit, so the net can come down toward the middle. */
    private var farRelax = 0f
    private var panMax = 0f
    private var sideSlackFt = 0f
    var screenW = 1
    var screenH = 1
    /** Screen y of the focus point. */
    var anchorY = 0f

    /** LONG view: sin / cos of the downward pitch and the focus distance D in feet. */
    var sinT = 0.75f
        private set
    var cosT = 0.66f
        private set
    var dist = 100f
        private set

    private var minX = -80f
    private var maxX = 60f

    companion object {
        // ---- SIDE view
        /** Feet of rink length visible across the screen at the focus depth. */
        const val VISIBLE_WIDTH_FT = 88f
        /** Vertical foreshortening of the ice plane. */
        const val VK = 0.62f
        /** Perspective strength per foot of depth. */
        const val PERSP = 0.007f
        /** SIDE fit: ft beyond the side boards that must stay on screen, height allowance for upright players, screen margin. */
        const val SIDE_EDGE_FT = 3f
        const val SIDE_HEAD_FT = 6.5f
        const val SIDE_MARGIN_FRAC = 0.03f

        // ---- LONG view
        /** Feet of rink width shown across the bottom row of the screen (rink is 85 ft wide). */
        const val BOTTOM_WIDTH_FT = 87f
        /** Rink length (ft) the pitch is tuned to show from the bottom to the top of the screen. */
        const val TARGET_LEN_FT = 80f
        const val LONG_PERSP = 0.011f
        /** Sine of the downward pitch (about 41 degrees). */
        const val LONG_SIN = 0.66f
        /** The focus point leads the puck up the rink so the far net is on screen from the faceoff. */
        const val LEAD_FT = 8f
        const val TOP_FRAC = 0.2f
        const val SCALE_CAP = 13.5f
        /** Dynamic zoom range (close play / faceoffs in, fast cross-ice play out). */
        const val ZOOM_IN = 1.19f
        const val ZOOM_OUT = 1.04f
        const val ZOOM_FACEOFF = 1.13f
        /** Close zoom at a net: extra zoom (fraction) when the puck is within NET_ZOOM_RANGE_FT of the attacked goal line. */
        const val NET_ZOOM_FAR = 0.35f
        const val NET_ZOOM_NEAR = 0.12f
        const val NET_ZOOM_NEAR_SHOOTOUT = 0.18f
        const val NET_ZOOM_RANGE_FT = 35f
        const val NET_LEAD_FT = 9f
        const val NET_ZOOM_MAX = 1.75f
        const val SMOOTH_NET_ZOOM = 0.7f
        /** Max lateral pan of the focus toward the puck's side (ft). */
        const val PAN_FT = 8f
        /** Look-ahead into the puck's travel (seconds of filtered velocity, capped in feet) and attack-end bias. */
        const val LOOK_S = 0.28f
        const val LOOK_MAX_FT = 16f
        const val ATTACK_BIAS_FT = 10f
        const val PUCK_BIAS_FT = 3f
        /** Critically damped smoothing times (s): along the rink, sideways, zoom, faceoff ease and the quick snap. */
        const val SMOOTH_X = 0.32f
        const val SMOOTH_Y = 0.55f
        const val SMOOTH_ZOOM = 0.9f
        const val SMOOTH_FACEOFF = 0.6f
        const val SMOOTH_SNAP = 0.14f
        /** Ground foreshortening used when baking the upright character sprites in LONG view. */
        const val LONG_SPRITE_VK = 0.72f
        const val ANCHOR_FRAC = 0.64f
        const val FAR_BOARD_FRAC = 0.28f
        const val NEAR_BOARD_FRAC = 0.93f

        val WORLD_HALF_W = Rink.HALF_L + Rink.WORLD_MARGIN
        val WORLD_HALF_H = Rink.HALF_W + Rink.WORLD_MARGIN
    }

    /** Ground foreshortening to bake the character sprites with. */
    val spriteVk: Float get() = if (long) LONG_SPRITE_VK else VK

    fun setMode(m: ViewMode) {
        mode = m
        long = m == ViewMode.LONG
        if (screenW > 1) resize(screenW, screenH)
    }

    fun resize(w: Int, h: Int) {
        screenW = max(1, w)
        screenH = max(1, h)
        if (!long) {
            // Fit the full rink width (near board to far board + margin + player height) into the screen
            // height; the length-based fit only wins if it is ever smaller. Focus y stays at 0.
            val d = Rink.HALF_W + SIDE_EDGE_FT
            val far = d * VK / (1f + PERSP * d)
            val near = d * VK / (1f - PERSP * d)
            val head = SIDE_HEAD_FT / (1f + PERSP * d)
            val usable = screenH * (1f - 2f * SIDE_MARGIN_FRAC)
            scale = min(screenW / VISIBLE_WIDTH_FT, usable / (far + near + head))
            anchorY = screenH * SIDE_MARGIN_FRAC + scale * (far + head)
            // Only when the length-based fit is the smaller scale is there spare height to follow the puck sideways.
            val slackPx = (usable - scale * (far + near + head)).coerceAtLeast(0f)
            sideSlackFt = slackPx * 0.5f / (scale * VK * (1f + PERSP * d))
            y = y.coerceIn(-sideSlackFt, sideSlackFt)
            x = x.coerceIn(-sideLimX(), sideLimX())
            return
        }
        val p = LONG_PERSP
        val s = LONG_SIN
        sinT = s
        cosT = sqrt(1f - s * s)
        dist = cosT / p
        // Fit: from the faceoff (focus leads the puck by LEAD_FT) the far end boards sit at TOP_FRAC of the
        // screen height and about 30 ft of ice behind the focus show at the bottom. Scale is capped so
        // sprites stay sharp on wide phones, and by the rink width at the near row (tablets).
        val yFarFace = Rink.HALF_L - LEAD_FT
        val aFar = yFarFace / (1f + p * yFarFace)
        val aNear = -30f / (1f - p * 30f)
        val kFit = screenH * (1f - TOP_FRAC) / (aFar - aNear)
        val scFit = kFit / s
        val scWidth = screenW * (1f - 30f * p) / (BOTTOM_WIDTH_FT)
        baseScale = min(min(scFit, SCALE_CAP), scWidth)
        scale = baseScale * zoom
        zoomMax = min(ZOOM_IN, scWidth / baseScale).coerceAtLeast(ZOOM_OUT)
        // Sideways slack (ft at the near row) before the rink edge leaves the screen: limits the lateral pan.
        val nearPpf = baseScale / (1f - 30f * p)
        panMax = ((screenW / 2f - 43f * nearPpf) / nearPpf * 0.7f).coerceIn(0f, PAN_FT)
        val k0 = baseScale * s
        anchorY = screenH * TOP_FRAC + k0 * aFar
        updateLimits()
        x = x.coerceIn(minX, maxX)
        y = y.coerceIn(-panMax, panMax)
    }

    /** LONG view: x range of the focus so neither end board leaves the screen band (uses the current zoom). */
    private fun updateLimits() {
        val p = LONG_PERSP
        // Camera limits: the far end boards never rise above FAR_BOARD_FRAC of the screen height
        // and the near end boards never sink below NEAR_BOARD_FRAC (so no empty space past the rink ends).
        val k = scale * sinT
        val qFar = (anchorY - (FAR_BOARD_FRAC - farRelax) * screenH) / k
        val yFar = if (p * qFar < 0.85f) qFar / (1f - p * qFar) else 120f
        val qNear = (NEAR_BOARD_FRAC * screenH - anchorY) / k
        val yNear = qNear / (1f + p * qNear)
        maxX = Rink.HALF_L - yFar
        minX = -Rink.HALF_L + yNear
        if (minX > maxX) { val m = (minX + maxX) / 2f; minX = m; maxX = m }
    }

    var shakeTrauma = 0f
    private var shakeTime = 0f
    private var shakeX = 0f
    private var shakeY = 0f

    fun snapTo(tx: Float, ty: Float) {
        x = clampX(tx)
        y = clampY(ty)
    }

    fun addShake(amount: Float) {
        shakeTrauma = (shakeTrauma + amount).coerceIn(0f, 1f)
    }

    /** Follows a target (tx, ty). LONG view ignores ty: only the position along the rink is tracked. */
    fun follow(tx: Float, ty: Float, dt: Float) {
        val k = 1f - exp(-dt * 6f)
        x += (clampX(tx) - x) * k
        y += (clampY(ty) - y) * k

        if (shakeTrauma > 0f) {
            shakeTime += dt
            shakeTrauma = max(0f, shakeTrauma - dt * 2.2f)
        }
    }

    // ---- LONG view tracking state (plain floats, no allocation)
    private var vx = 0f
    private var vy = 0f
    private var vz = 0f
    private var dampVel = 0f
    private var lastPx = 0f
    private var lastPy = 0f
    private var puckVx = 0f
    private var puckVy = 0f
    private var havePuck = false
    /** Host: use the simulation's puck velocity for the look-ahead; guest keeps the position-delta estimate. */
    var useWorldVelocity = false
    private var prevPhase: Phase? = null
    private var snapTimer = 0f

    /** Critically damped spring toward [target] (SmoothDamp); the new velocity goes through [dampVel]. */
    private fun damp(cur: Float, target: Float, vel: Float, smooth: Float, dt: Float): Float {
        val om = 2f / smooth
        val xx = om * dt
        val e = 1f / (1f + xx + 0.48f * xx * xx + 0.235f * xx * xx * xx)
        val ch = cur - target
        val tmp = (vel + om * ch) * dt
        dampVel = (vel - om * tmp) * e
        return target + (ch + tmp) * e
    }

    /**
     * Camera tracking. SIDE view keeps the original puck follow. LONG view: critically damped follow of the
     * action, looking ahead along the puck's filtered velocity, leaning toward the attacking end when a team
     * carries in its offensive zone, shifting a little toward the puck's side, zooming in for slow close play and
     * faceoffs and out for fast play. Faceoffs ease to the dot; after a goal / period change it snaps quickly.
     * Read-only on the world; on the guest it only needs the (interpolated) puck position, velocity is estimated here.
     */
    fun track(w: World, dt0: Float) {
        if (!long) {
            val lead = if (w.puck.carrier == null) 0.18f else 0.1f
            follow(w.puck.x + w.puck.vx * lead, w.puck.y * 0.85f + w.puck.vy * lead * 0.5f, dt0); return
        }
        val dt = dt0.coerceIn(0.001f, 0.1f)
        val pk = w.puck
        if (havePuck) {
            val ivx = (if (useWorldVelocity) pk.vx else (pk.x - lastPx) / dt).coerceIn(-150f, 150f)
            val ivy = (if (useWorldVelocity) pk.vy else (pk.y - lastPy) / dt).coerceIn(-150f, 150f)
            val a = 1f - exp(-dt / 0.12f)
            puckVx += (ivx - puckVx) * a
            puckVy += (ivy - puckVy) * a
        }
        lastPx = pk.x; lastPy = pk.y; havePuck = true
        val phase = w.phase
        if (phase != prevPhase) {
            if (phase == Phase.FACEOFF && (prevPhase == Phase.GOAL || prevPhase == Phase.PERIOD_END || prevPhase == Phase.ANTHEM || prevPhase == null)) snapTimer = 0.9f
            if (phase == Phase.FACEOFF) { puckVx = 0f; puckVy = 0f }
            prevPhase = phase
        }
        if (snapTimer > 0f) snapTimer -= dt
        val speed = sqrt(puckVx * puckVx + puckVy * puckVy)
        var tx: Float
        val ty: Float
        var tz: Float
        val stX: Float
        var zoomSmooth = SMOOTH_ZOOM
        var netBoost = 0f
        farRelax = 0f
        if (phase == Phase.FACEOFF || phase == Phase.ANTHEM) {
            tx = w.faceoffX + LEAD_FT
            ty = 0f
            tz = ZOOM_FACEOFF
            stX = if (snapTimer > 0f) SMOOTH_SNAP else SMOOTH_FACEOFF
        } else {
            val look = (puckVx * LOOK_S).coerceIn(-LOOK_MAX_FT, LOOK_MAX_FT)
            var bias = PUCK_BIAS_FT
            val c = pk.carrier
            if (c != null) {
                val dir = w.teams[c.team].attackDir
                val zone = c.x * dir
                if (zone > 35f) bias += dir * ((zone - 35f) / 30f).coerceIn(0f, 1f) * ATTACK_BIAS_FT
            }
            tx = pk.x + look + bias
            ty = pk.y * 0.3f + puckVy * 0.1f
            // Close zoom at the net the play is heading for: smooth 0..1 as the puck gets within NET_ZOOM_RANGE_FT
            // of a goal line (full at the goal line and behind it). The far (attacked) net gets the full boost;
            // the near net a smaller one (it sits against the bottom of the screen).
            val dFar = Rink.GOAL_LINE_X - pk.x
            val dNear = Rink.GOAL_LINE_X + pk.x
            val sFar = 1f - (dFar / NET_ZOOM_RANGE_FT).coerceIn(0f, 1f)
            val sNear = 1f - (dNear / NET_ZOOM_RANGE_FT).coerceIn(0f, 1f)
            val sf = sFar * sFar * (3f - 2f * sFar)
            val sn = sNear * sNear * (3f - 2f * sNear)
            netBoost = sf * NET_ZOOM_FAR + sn * (if (w.isShootout) NET_ZOOM_NEAR_SHOOTOUT else NET_ZOOM_NEAR)
            farRelax = -sf * 0.02f
            tx += sf * NET_LEAD_FT  // raise the far net on screen as the attack reaches it
            val sMax = max(sf, sn)
            tz = ZOOM_IN - (ZOOM_IN - ZOOM_OUT) * ((speed - 12f) / 55f).coerceIn(0f, 1f) * (1f - 0.8f * sMax)
            if (sMax > 0.01f) zoomSmooth = SMOOTH_NET_ZOOM
            stX = if (snapTimer > 0f) SMOOTH_SNAP else SMOOTH_X
        }
        tz = tz.coerceIn(ZOOM_OUT, zoomMax) * (1f + netBoost)
        zoom = damp(zoom, tz.coerceIn(ZOOM_OUT, NET_ZOOM_MAX), vz, zoomSmooth, dt); vz = dampVel
        scale = baseScale * zoom
        updateLimits()
        x = damp(x, tx.coerceIn(minX, maxX), vx, stX, dt).coerceIn(minX, maxX); vx = dampVel
        y = damp(y, ty.coerceIn(-panMax, panMax), vy, if (snapTimer > 0f) SMOOTH_SNAP else SMOOTH_Y, dt).coerceIn(-panMax, panMax); vy = dampVel
        if (shakeTrauma > 0f) {
            shakeTime += dt
            shakeTrauma = max(0f, shakeTrauma - dt * 2.2f)
        }
    }

    /** Computes this frame's shake offset; call once per frame before projecting. */
    fun beginFrame() {
        if (shakeTrauma > 0.01f) {
            val mag = shakeTrauma * shakeTrauma * (screenH * 0.035f)
            shakeX = kotlin.math.cos(shakeTime * 48f) * mag
            shakeY = kotlin.math.sin(shakeTime * 42f) * mag
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }

    private fun clampX(v: Float): Float {
        if (long) return v.coerceIn(minX, maxX)
        val lim = sideLimX()
        return v.coerceIn(-lim, lim)
    }

    /** SIDE view: how far the focus may move along the rink so the scenery past the end boards never leaves a void. */
    private fun sideLimX(): Float {
        val halfVis = screenW / 2f / scale
        return min(Rink.HALF_L - 20f, Rink.HALF_L + Rink.WORLD_MARGIN - halfVis).coerceAtLeast(0f)
    }

    /** SIDE view: keeps the near boards from rising above the bottom of the screen. LONG view: always centred. */
    private fun clampY(v: Float): Float {
        if (long) return v.coerceIn(-panMax, panMax)
        return v.coerceIn(-sideSlackFt, sideSlackFt)  // SIDE: ~0 when the width fills the height
    }

    private fun den(wx: Float, z: Float): Float =
        max(0.3f * dist, dist + (wx - x) * cosT - z * sinT)

    private fun sideDepth(wy: Float): Float = 1f / (1f - PERSP * (wy - y)).coerceAtLeast(0.25f)

    /** Depth factor at a ground point: 1 at the focus, smaller farther away. */
    fun depthAt(wx: Float, wy: Float): Float = if (long) dist / den(wx, 0f) else sideDepth(wy)

    /** Screen pixels per foot (across the rink) for things standing at (wx, wy). */
    fun ppfAt(wx: Float, wy: Float): Float = scale * depthAt(wx, wy)

    /** Vertical squash of ground circles at (wx, wy) (ry = rx * vk). */
    fun vkAt(wx: Float, wy: Float): Float = if (long) sinT * dist / den(wx, 0f) else VK

    /** Screen x of the world point (wx, wy) lifted [z] feet above the ice. */
    fun px(wx: Float, wy: Float, z: Float = 0f): Float =
        if (long) screenW / 2f + shakeX + (wy - y) * scale * dist / den(wx, z)
        else screenW / 2f + shakeX + (wx - x) * scale * sideDepth(wy)

    /** Screen y of the world point (wx, wy) lifted [z] feet above the ice. */
    fun py(wx: Float, wy: Float, z: Float = 0f): Float =
        if (long) anchorY + shakeY - ((wx - x) * sinT + z * cosT) * scale * dist / den(wx, z)
        else {
            val f = sideDepth(wy)
            anchorY + shakeY + (wy - y) * scale * VK * f - z * scale * f
        }

    fun toScreenX(wx: Float, wy: Float) = px(wx, wy)
    fun toScreenY(wx: Float, wy: Float) = py(wx, wy)

    /** LONG view: world x range the screen currently shows on the ice (with a margin), for culling meshes. */
    fun visibleXMin(): Float {
        val q = (screenH + 20f - anchorY) / (scale * sinT)
        return x - q / (1f + LONG_PERSP * q) - 6f
    }

    fun visibleXMax(): Float {
        val q = (anchorY + 20f) / (scale * sinT)
        return x + (if (LONG_PERSP * q < 0.85f) q / (1f - LONG_PERSP * q) else 400f) + 6f
    }
}
