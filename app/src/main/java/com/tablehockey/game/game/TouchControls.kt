package com.tablehockey.game.game

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Virtual joystick (left half of the screen) plus SHOOT / PASS / HIT buttons
 * (bottom right). Deke is a stick flick (see [updateJoystick]). Touch events arrive on the UI thread; the game loop pulls a
 * snapshot with [snapshotInto], which also consumes the one-shot presses.
 */
class TouchControls(private val density: Float) {

    private val lock = Any()
    private val input = PlayerInput()

    // Joystick geometry (screen px)
    var joyRadius = 64f * density
    var joyRestX = 0f
    var joyRestY = 0f
    @Volatile var joyActive = false
    @Volatile var joyAnchorX = 0f
    @Volatile var joyAnchorY = 0f
    @Volatile var joyKnobX = 0f
    @Volatile var joyKnobY = 0f
    private var joyPointer = -1

    // Buttons
    var shootX = 0f; var shootY = 0f; var shootR = 50f * density
    var passX = 0f; var passY = 0f; var passR = 42f * density
    var hitX = 0f; var hitY = 0f; var hitR = 42f * density
    /** True while the controlled skater has the puck; the stick-flick auto-deke only fires then. */
    @Volatile var carrying = false
    @Volatile var shootDown = false
    @Volatile var passDown = false
    @Volatile var hitDown = false
    private var shootPointer = -1
    private var passPointer = -1
    private var hitPointer = -1
    private var shootDownTime = 0L

    // Automatic Deke detection on rapid joystick movement / flick
    private val histT = LongArray(HIST)
    private val histX = FloatArray(HIST)
    private val histY = FloatArray(HIST)
    private var histN = 0
    private var histHead = 0
    private var dekeCooldownUntil = 0L
    private var dekeFlashUntil = 0L

    private var screenW = 1
    private var screenH = 1
    private var topExclusion = 0f

    companion object {
        const val CHARGE_SECONDS = 0.75f
        private const val HIST = 12
    }

    fun layout(w: Int, h: Int) {
        screenW = w
        screenH = h
        topExclusion = 64f * density
        joyRadius = 64f * density
        joyRestX = 120f * density
        joyRestY = h - 120f * density
        shootX = w - 95f * density; shootY = h - 95f * density
        passX = w - 215f * density; passY = h - 80f * density
        hitX = w - 105f * density; hitY = h - 215f * density
    }

    /** 1 right after a stick-flick deke fires, fading to 0 (for a brief joystick-ring flash). */
    fun dekeFlash(): Float {
        val left = dekeFlashUntil - SystemClock.elapsedRealtime()
        return if (left <= 0L) 0f else (left / 220f).coerceIn(0f, 1f)
    }

    fun currentCharge(): Float {
        if (!shootDown) return 0f
        val held = (SystemClock.elapsedRealtime() - shootDownTime) / 1000f
        return (held / CHARGE_SECONDS).coerceIn(0f, 1f)
    }

    /** Copies the current state into [dst] and clears one-shot presses. */
    fun snapshotInto(dst: PlayerInput) {
        synchronized(lock) {
            input.shootHeld = shootDown
            input.shootCharge = if (shootDown) currentCharge() else input.shootCharge
            dst.copyFrom(input)
            input.clearPulses()
        }
    }

    fun reset() {
        synchronized(lock) {
            input.moveX = 0f; input.moveY = 0f
            input.clearPulses()
            input.shootHeld = false
        }
        joyPointer = -1; shootPointer = -1; passPointer = -1; hitPointer = -1
        joyActive = false; shootDown = false; passDown = false; hitDown = false
        histN = 0
    }

    /** Returns true when the event was consumed by a control. */
    fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                val x = e.getX(idx)
                val y = e.getY(idx)
                onDown(id, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (joyPointer != -1) {
                    val idx = e.findPointerIndex(joyPointer)
                    if (idx != -1) updateJoystick(e.getX(idx), e.getY(idx))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                onUp(id)
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (hypot(x - shootX, y - shootY) <= shootR * 1.15f && shootPointer == -1) {
            shootPointer = id
            shootDown = true
            shootDownTime = SystemClock.elapsedRealtime()
            synchronized(lock) { input.shootCharge = 0f }
            return
        }
        if (hypot(x - passX, y - passY) <= passR * 1.15f && passPointer == -1) {
            passPointer = id
            passDown = true
            synchronized(lock) { input.pass = true }
            return
        }
        if (hypot(x - hitX, y - hitY) <= hitR * 1.15f && hitPointer == -1) {
            hitPointer = id
            hitDown = true
            synchronized(lock) { input.hit = true }
            return
        }
        if (x < screenW * 0.5f && y > topExclusion && joyPointer == -1) {
            joyPointer = id
            joyActive = true
            joyAnchorX = x
            joyAnchorY = y
            joyKnobX = x
            joyKnobY = y
            histN = 0
            synchronized(lock) { input.moveX = 0f; input.moveY = 0f }
        }
    }

    private fun updateJoystick(x: Float, y: Float) {
        var dx = x - joyAnchorX
        var dy = y - joyAnchorY
        val d = hypot(dx, dy)
        if (d > joyRadius) {
            dx *= joyRadius / d
            dy *= joyRadius / d
        }
        joyKnobX = joyAnchorX + dx
        joyKnobY = joyAnchorY + dy
        var mx = dx / joyRadius
        var my = dy / joyRadius
        val mag = hypot(mx, my)
        if (mag < 0.12f) { mx = 0f; my = 0f } else {
            // Rescale so the dead zone doesn't eat range.
            val scaled = ((mag - 0.12f) / 0.88f).coerceIn(0f, 1f)
            mx = mx / mag * scaled
            my = my / mag * scaled
        }

        // Flick detection: compare against the stick position ~60-150 ms ago (ring buffer, no allocation).
        val now = SystemClock.elapsedRealtime()
        if (carrying && now > dekeCooldownUntil && hypot(mx, my) > 0.6f) {
            var i = 0
            while (i < histN) {
                val k = (histHead - 1 - i + HIST) % HIST
                val age = now - histT[k]
                if (age > 150L) break
                if (age >= 40L) {
                    val px = histX[k]; val py = histY[k]
                    val pm = hypot(px, py)
                    val cm = hypot(mx, my)
                    if (pm > 0.35f) {
                        val dot = (mx * px + my * py) / (cm * pm)
                        val delta = hypot(mx - px, my - py)
                        val rate = delta / (age / 1000f)
                        // Reversal: thumb swung from one side of the pad to the other.
                        val reversal = dot < -0.2f && pm > 0.55f && cm > 0.7f && delta > 1.0f
                        // Sideways/diagonal flick: large AND fast swing (ordinary hard turns are slower).
                        val swing = delta > 1.3f && rate > 8f && dot < 0.55f
                        if (reversal || swing) {
                            val cross = px * my - py * mx
                            val sign = if (cross > 0.15f * pm * cm) 1f else if (cross < -0.15f * pm * cm) -1f else 0f
                            synchronized(lock) { input.deke = true; input.dekeSign = sign }
                            dekeCooldownUntil = now + 550L
                            dekeFlashUntil = now + 220L
                            break
                        }
                    }
                }
                i++
            }
        }
        // Record sample (at most one per ~12 ms to cover the history window with HIST slots).
        val last = (histHead - 1 + HIST) % HIST
        if (histN == 0 || now - histT[last] >= 12L) {
            histT[histHead] = now; histX[histHead] = mx; histY[histHead] = my
            histHead = (histHead + 1) % HIST
            if (histN < HIST) histN++
        }

        synchronized(lock) { input.moveX = mx; input.moveY = my }
    }

    private fun onUp(id: Int) {
        when (id) {
            joyPointer -> {
                joyPointer = -1
                joyActive = false
                histN = 0
                synchronized(lock) { input.moveX = 0f; input.moveY = 0f }
            }
            shootPointer -> {
                shootPointer = -1
                val charge = currentCharge()
                shootDown = false
                synchronized(lock) {
                    input.shootRelease = true
                    input.shootCharge = charge
                    input.shootHeld = false
                }
            }
            passPointer -> { passPointer = -1; passDown = false }
            hitPointer -> { hitPointer = -1; hitDown = false }
        }
    }
}
