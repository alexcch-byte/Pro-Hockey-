package com.tablehockey.game.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.tablehockey.game.model.TeamInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/**
 * Procedural upright characters (skaters, goalies, referee) rendered to bitmaps.
 *
 * Each body part is a capsule, disc or ring placed in 3D (feet at z = 0, +x forward,
 * +y to the character's left, +z up), rotated about the vertical axis by the facing
 * and projected with the same ground foreshortening as the rink, then painted back to
 * front. The result is a billboard that reads like a 3/4-view cartoon player from the
 * broadcast camera: you see the back and the jersey number when a skater faces away,
 * the face and chest when he skates toward you.
 *
 * Sprites are built lazily (one per facing and pose) at [pxPerFt] pixels per foot, with
 * the feet at ([anchorX], [anchorY]). The renderer scales them by (screen px per foot at
 * that depth) / [pxPerFt]. Nothing here is called per frame except the cheap number
 * placement helpers.
 */
class CharacterArt(val pxPerFt: Float) {

    companion object {
        const val FACINGS = 16
        const val STRIDE_FRAMES = 6
        const val F_WIND = 6
        const val F_FOLLOW = 7
        const val F_POKE = 8
        const val F_FALLEN = 9
        const val FRAMES = 10
        const val GOALIE_STANCES = 4

        private const val ABOVE_FT = 9.4f
        private const val BELOW_FT = 5.8f
        private const val WIDE_FT = 16.5f
        private const val BODY_SC = 1.15f
        private const val GOALIE_SC = 1.25f

        private const val CAP = 0
        private const val BAND = 1
        private const val DISC = 2
        private const val RING = 3
        private const val LINE = 4
        private const val FLAT = 5

        /** Index (0 until FACINGS) of the sprite nearest to a facing angle in radians. */
        fun facingIndex(angle: Float): Int {
            val t = angle / (2f * PI.toFloat()) * FACINGS
            val i = Math.round(t) % FACINGS
            return if (i < 0) i + FACINGS else i
        }
    }

    val width = ceil(WIDE_FT * pxPerFt).toInt()
    val height = ceil((ABOVE_FT + BELOW_FT) * pxPerFt).toInt()
    val anchorX = width / 2f
    val anchorY = ABOVE_FT * pxPerFt

    private class Part(
        val kind: Int,
        val x1: Float, val y1: Float, val z1: Float,
        val x2: Float, val y2: Float, val z2: Float,
        val w: Float, val color: Int, val depth: Float
    )

    private val parts = ArrayList<Part>(64)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // current transform
    private var cs = 1f
    private var sn = 0f
    private var sc = 1f

    private fun begin(facing: Int, scale: Float) {
        val a = facing * 2f * PI.toFloat() / FACINGS
        cs = cos(a)
        sn = sin(a)
        sc = scale
        parts.clear()
    }

    private fun sx(lx: Float, ly: Float) = anchorX + (lx * cs + ly * sn) * sc * pxPerFt
    private fun sy(lx: Float, ly: Float, lz: Float) =
        anchorY + (lx * sn - ly * cs) * sc * pxPerFt * Camera.VK - lz * sc * pxPerFt

    private fun add(kind: Int, x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, w: Float, color: Int) {
        val my = ((y1 + y2) * 0.5f)
        val mx = ((x1 + x2) * 0.5f)
        parts.add(Part(kind, x1, y1, z1, x2, y2, z2, w, color, mx * sn - my * cs))
    }

    private fun cap(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, w: Float, color: Int) =
        add(CAP, x1, y1, z1, x2, y2, z2, w, color)

    /** A band around part of a limb or torso: a butt-capped stroke over [t0, t1] of the segment. */
    private fun band(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, t0: Float, t1: Float, w: Float, color: Int) =
        add(BAND, x1 + (x2 - x1) * t0, y1 + (y2 - y1) * t0, z1 + (z2 - z1) * t0, x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1, z1 + (z2 - z1) * t1, w, color)

    private fun disc(x: Float, y: Float, z: Float, d: Float, color: Int) = add(DISC, x, y, z, x, y, z, d, color)
    private fun flat(x: Float, y: Float, z: Float, d: Float, color: Int) = add(FLAT, x, y, z, x, y, z, d, color)
    private fun ring(x: Float, y: Float, z: Float, d: Float, color: Int) = add(RING, x, y, z, x, y, z, d, color)
    private fun line(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, w: Float, color: Int) =
        add(LINE, x1, y1, z1, x2, y2, z2, w, color)

    // Stock clubs keep the original look; only customised kits use the Customise Team colours.
    private fun trimOf(i: TeamInfo) = if (i.hasCustomKit) i.trimColor else i.secondary
    private fun sockOf(i: TeamInfo, stock: Int) = if (i.hasCustomKit) i.sockColor else stock
    private fun helmetOf(i: TeamInfo) = if (i.hasCustomKit) i.helmetColor else darken(i.primary, 0.55f)

    private fun darken(c: Int, f: Float) = Color.argb(
        Color.alpha(c),
        (Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    private fun lighten(c: Int, f: Float) = Color.argb(
        Color.alpha(c),
        (Color.red(c) + (255 - Color.red(c)) * f).toInt().coerceIn(0, 255),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt().coerceIn(0, 255)
    )

    private fun finish(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        parts.sortBy { it.depth }
        val outline = max(1.3f, pxPerFt * 0.09f)
        for (p in parts) {
            val x1 = sx(p.x1, p.y1)
            val y1 = sy(p.x1, p.y1, p.z1)
            val x2 = sx(p.x2, p.y2)
            val y2 = sy(p.x2, p.y2, p.z2)
            val wpx = p.w * sc * pxPerFt
            when (p.kind) {
                CAP, BAND -> {
                    stroke.strokeCap = if (p.kind == BAND) Paint.Cap.BUTT else Paint.Cap.ROUND
                    if (p.kind == CAP) {
                        stroke.strokeWidth = wpx + outline
                        stroke.color = darken(p.color, 0.38f)
                        c.drawLine(x1, y1, x2, y2, stroke)
                    }
                    stroke.strokeWidth = wpx
                    stroke.color = p.color
                    c.drawLine(x1, y1, x2, y2, stroke)
                    if (p.kind == CAP && wpx > 5f) {
                        // Shaded underside, lit top and a thin rim light along the upper edge.
                        stroke.strokeWidth = wpx * 0.38f
                        stroke.color = darken(p.color, 0.6f)
                        val so = wpx * 0.2f
                        c.drawLine(x1 + so, y1 + so * 0.75f, x2 + so, y2 + so * 0.75f, stroke)
                        stroke.strokeWidth = wpx * 0.28f
                        stroke.color = lighten(p.color, 0.42f)
                        val o = wpx * 0.17f
                        c.drawLine(x1 - o, y1 - o * 0.9f, x2 - o, y2 - o * 0.9f, stroke)
                        stroke.strokeWidth = max(1f, wpx * 0.08f)
                        stroke.color = Color.argb(150, 255, 255, 255)
                        val ro = wpx * 0.41f
                        c.drawLine(x1 - ro, y1 - ro * 0.9f, x2 - ro, y2 - ro * 0.9f, stroke)
                    }
                }
                DISC -> {
                    fill.color = darken(p.color, 0.38f)
                    c.drawCircle(x1, y1, wpx / 2f + outline * 0.5f, fill)
                    fill.color = p.color
                    c.drawCircle(x1, y1, wpx / 2f - outline * 0.35f, fill)
                    fill.color = lighten(p.color, 0.45f)
                    c.drawCircle(x1 - wpx * 0.17f, y1 - wpx * 0.2f, wpx * 0.14f, fill)
                }
                FLAT -> {
                    fill.color = p.color
                    c.drawCircle(x1, y1, wpx / 2f, fill)
                }
                RING -> {
                    stroke.strokeCap = Paint.Cap.BUTT
                    stroke.strokeWidth = max(1.1f, wpx * 0.1f)
                    stroke.color = p.color
                    c.drawCircle(x1, y1, wpx / 2f, stroke)
                }
                LINE -> {
                    stroke.strokeCap = Paint.Cap.ROUND
                    stroke.strokeWidth = max(1f, wpx)
                    stroke.color = p.color
                    c.drawLine(x1, y1, x2, y2, stroke)
                }
            }
        }
        parts.clear()
        finishLook(bmp, c)
        return bmp
    }

    private val atop = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val dstOut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val dstOver = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
    private val rimTint = Paint().apply { colorFilter = PorterDuffColorFilter(Color.argb(150, 226, 238, 255), PorterDuff.Mode.SRC_IN) }

    /** Baked form shading, a directional rim light from the upper left, and a soft contact shadow underneath. */
    private fun finishLook(bmp: Bitmap, c: Canvas) {
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        // Light from the upper left: lighter on that side, darker toward the lower right and the ground.
        atop.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.argb(46, 255, 255, 255), Color.argb(0, 0, 0, 0), Color.argb(80, 8, 16, 44)),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, atop)
        atop.shader = LinearGradient(
            0f, anchorY - 4f * pxPerFt, 0f, anchorY + 0.5f * pxPerFt,
            Color.argb(0, 0, 0, 0), Color.argb(70, 8, 16, 44), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, atop)
        atop.shader = null
        // Rim: the silhouette minus itself shifted down-right leaves a thin lit edge on the upper left.
        val rim = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val off = max(1.4f, pxPerFt * 0.11f)
        Canvas(rim).drawBitmap(bmp, off, off * 0.9f, dstOut)
        c.drawBitmap(rim, 0f, 0f, rimTint)
        rim.recycle()
        // Soft contact shadow behind everything.
        val rx = 1.55f * sc * pxPerFt
        dstOver.shader = RadialGradient(0f, 0f, rx, intArrayOf(Color.argb(120, 8, 18, 40), Color.argb(50, 8, 18, 40), Color.argb(0, 8, 18, 40)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.save()
        c.translate(anchorX + 0.2f * pxPerFt, anchorY + 0.12f * pxPerFt)
        c.scale(1f, Camera.VK)
        c.drawCircle(0f, 0f, rx, dstOver)
        c.restore()
        dstOver.shader = null
    }

    // ------------------------------------------------------------------ number placement

    /** Pixel offset (relative to the feet anchor) where the jersey number sits for a facing. */
    fun numberDx(facing: Int): Float {
        begin(facing, BODY_SC)
        return sx(-0.1f, 0f) - anchorX
    }

    fun numberDy(facing: Int): Float {
        begin(facing, BODY_SC)
        return sy(-0.1f, 0f, 3.95f) - anchorY
    }

    /** 0 when the player faces the camera, up to 1 when his back is square to it. */
    fun backness(facing: Int): Float {
        val a = facing * 2f * PI.toFloat() / FACINGS
        val s = sin(a)
        return if (s < -0.2f) -s else 0f
    }

    // ------------------------------------------------------------------ skaters

    fun skater(info: TeamInfo, facing: Int, frame: Int, referee: Boolean): Bitmap {
        begin(facing, if (referee) 1.08f else BODY_SC)
        val bsc = sc
        val prim = if (referee) Color.parseColor("#F1F5F9") else info.primary
        val sec = if (referee) Color.parseColor("#0B0F17") else trimOf(info)
        val pants = if (referee) Color.parseColor("#0B0F17") else Color.parseColor("#1B2333")
        val sock = if (referee) Color.parseColor("#0B0F17") else sockOf(info, sec)
        val helmet = if (referee) Color.parseColor("#0B0F17") else helmetOf(info)
        val glove = if (referee) Color.parseColor("#0B0F17") else darken(info.primary, 0.42f)
        val skin = Color.parseColor("#E8B994")
        val reach = (1.5f + Skater.STICK_REACH) / bsc

        if (frame == F_FALLEN) {
            fallen(prim, sec, pants, sock, helmet, glove, skin, referee)
            return finish()
        }

        // Legs.
        val a = frame.coerceAtMost(STRIDE_FRAMES - 1) * (2f * PI.toFloat() / STRIDE_FRAMES) + 0.6f
        for (s in intArrayOf(1, -1)) {
            val sw = sin(a) * s
            val cw = cos(a) * s
            val fx = 0.15f + 0.95f * sw
            val lift = 0.5f * max(0f, cw)
            val push = max(0f, -cw)
            val fy = s * (0.55f + 0.45f * push)
            val fz = lift
            val hy = s * 0.5f
            val hz = 2.75f
            val ax = fx - 0.1f
            val az = fz + 0.55f
            val kx = (ax + 0.0f) * 0.5f + 0.55f
            val ky = (hy + fy) * 0.5f
            val kz = (hz + az) * 0.5f
            cap(0f, hy, hz, kx, ky, kz, 1.05f, pants)
            cap(kx, ky, kz, ax, fy, az, 0.78f, sock)
            cap(fx - 0.25f, fy, fz + 0.4f, fx + 0.65f, fy, fz + 0.3f, 0.7f, Color.parseColor("#12161F"))
            cap(fx - 0.5f, fy, fz + 0.14f, fx + 0.5f, fy, fz + 0.14f, 0.22f, Color.parseColor("#F8FAFC"))
            line(fx - 0.8f, fy, fz + 0.04f, fx + 0.95f, fy, fz + 0.04f, 0.1f, Color.parseColor("#D5DEE8"))
        }

        // Torso, jersey bands.
        val tx1 = 0.1f; val tz1 = 2.85f
        val tx2 = 0.65f; val tz2 = 4.75f
        cap(tx1, 0f, tz1, tx2, 0f, tz2, 1.95f, prim)
        band(tx1, 0f, tz1, tx2, 0f, tz2, 0.16f, 0.3f, 2.02f, sec)
        band(tx1, 0f, tz1, tx2, 0f, tz2, 0.35f, 0.41f, 2.02f, if (referee) prim else lighten(sec, 0.6f))
        if (referee) {
            band(tx1, 0f, tz1, tx2, 0f, tz2, 0.5f, 0.62f, 2.02f, sec)
            band(tx1, 0f, tz1, tx2, 0f, tz2, 0.78f, 0.9f, 2.02f, sec)
        } else {
            cap(0.65f, -0.8f, 4.62f, 0.65f, 0.8f, 4.62f, 0.95f, prim)
        }

        // Stick and hands.
        var heelX = reach - 0.55f; var heelY = 0f; var heelZ = 0.05f
        var thX = 1.15f; var thY = 0.3f; var thZ = 3.35f
        when (frame) {
            F_WIND -> { heelX = reach - 2.4f; heelY = 1.1f; heelZ = 0.5f; thX = 0.5f; thY = 0.9f; thZ = 3.4f }
            F_FOLLOW -> { heelX = reach + 0.6f; heelY = -0.9f; heelZ = 0.9f; thX = 1.5f; thY = -0.1f; thZ = 3.2f }
            F_POKE -> { heelX = (1.5f + Skater.POKE_REACH) / bsc - 0.55f; thX = 2.0f; thZ = 3.1f }
        }
        val lowX = thX + (heelX - thX) * 0.42f
        val lowY = thY + (heelY - thY) * 0.42f
        val lowZ = thZ + (heelZ - thZ) * 0.42f
        for (s in intArrayOf(1, -1)) {
            val shX = 0.65f; val shY = s * 0.95f; val shZ = 4.55f
            val hx = if (s == 1) thX else lowX
            val hy = if (s == 1) thY else lowY
            val hz = if (s == 1) thZ else lowZ
            val ex: Float; val ey: Float; val ez: Float
            if (referee) {
                // Arms hang loose and swing with the stride.
                val sw = sin(a) * s * 0.5f
                val hx2 = 0.7f + sw; val hy2 = s * 1.15f; val hz2 = 2.9f
                ex = (shX + hx2) * 0.5f; ey = s * 1.25f; ez = (shZ + hz2) * 0.5f
                cap(shX, shY, shZ, ex, ey, ez, 0.8f, prim)
                cap(ex, ey, ez, hx2, hy2, hz2, 0.66f, prim)
                band(shX, shY, shZ, ex, ey, ez, 0.45f, 0.8f, 0.84f, Color.parseColor("#F97316"))
                disc(hx2, hy2, hz2, 0.7f, glove)
            } else {
                ex = (shX + hx) * 0.5f; ey = (shY + hy) * 0.5f + s * 0.45f; ez = (shZ + hz) * 0.5f - 0.25f
                cap(shX, shY, shZ, ex, ey, ez, 0.8f, prim)
                cap(ex, ey, ez, hx, hy, hz, 0.66f, prim)
                disc(hx, hy, hz, 0.82f, glove)
            }
        }
        if (!referee) {
            val dxs = thX - heelX; val dys = thY - heelY; val dzs = thZ - heelZ
            cap(thX + dxs * 0.1f, thY + dys * 0.1f, thZ + dzs * 0.1f, heelX, heelY, heelZ, 0.2f, Color.parseColor("#A0642F"))
            cap(heelX, heelY, heelZ, heelX + 1.1f, heelY, heelZ, 0.34f, Color.parseColor("#E8E8E8"))
        }

        head(helmet, skin, 0.85f, 0f, 5.8f, 1.5f, 0.32f)
        return finish()
    }

    /** Helmet disc, face and tinted visor; [fwd] is how far the face sits in front of the helmet centre. */
    private fun head(helmet: Int, skin: Int, x: Float, y: Float, z: Float, d: Float, fwd: Float) {
        disc(x, y, z, d, helmet)
        disc(x + fwd, y, z - 0.08f, d * 0.62f, skin)
        flat(x + fwd + 0.1f, y, z - 0.02f, d * 0.66f, Color.argb(120, 125, 211, 252))
    }

    private fun fallen(prim: Int, sec: Int, pants: Int, sock: Int, helmet: Int, glove: Int, skin: Int, referee: Boolean) {
        // Lying on the ice along the facing direction: legs behind, head ahead.
        for (s in intArrayOf(1, -1)) {
            cap(-0.3f, s * 0.5f, 0.75f, -1.8f, s * 0.8f, 0.55f, 1.0f, pants)
            cap(-1.8f, s * 0.8f, 0.55f, -2.9f, s * 1.0f, 0.45f, 0.75f, sock)
            cap(-2.9f, s * 1.0f, 0.4f, -3.6f, s * 1.1f, 0.4f, 0.65f, Color.parseColor("#12161F"))
        }
        cap(-0.3f, 0f, 0.95f, 1.15f, 0f, 1.05f, 1.9f, prim)
        band(-0.3f, 0f, 0.95f, 1.15f, 0f, 1.05f, 0.2f, 0.34f, 1.96f, sec)
        for (s in intArrayOf(1, -1)) {
            cap(1.0f, s * 0.9f, 1.0f, 1.9f, s * 1.9f, 0.5f, 0.78f, prim)
            disc(1.95f, s * 2.0f, 0.5f, 0.8f, glove)
        }
        if (!referee) {
            cap(0.2f, 1.5f, 0.2f, 3.0f, -0.4f, 0.08f, 0.2f, Color.parseColor("#A0642F"))
        }
        head(helmet, skin, 1.85f, 0f, 1.2f, 1.5f, 0.3f)
    }

    // ------------------------------------------------------------------ goalie

    /** stance: 0 upright, 1 butterfly, 2 and 3 pad stack toward world +y / -y. */
    fun goalie(info: TeamInfo, facing: Int, stance: Int): Bitmap {
        begin(facing, GOALIE_SC)
        val prim = info.primary
        val sec = trimOf(info)
        val padW = Color.parseColor("#F3F4F6")
        val pantsC = Color.parseColor("#1B2333")
        val leather = Color.parseColor("#8B5A2B")
        val leatherL = Color.parseColor("#C58B4A")
        val wood = Color.parseColor("#A0642F")
        val tape = Color.parseColor("#E8E8E8")
        val steel = Color.parseColor("#CBD5E1")
        val reach = (2.1f + Skater.STICK_REACH) / GOALIE_SC

        if (stance >= 2) {
            val sg = if (stance == 2) 1f else -1f
            val dx = sn * sg
            val dy = -cs * sg
            // Pads stacked toward the shot side.
            for (lvl in 0..1) {
                val z = 0.55f + lvl * 0.85f
                val fo = lvl * 0.25f
                cap(fo + dx * 0.7f, dy * 0.7f, z, fo + dx * 3.6f, dy * 3.6f, z, 1.05f, padW)
                band(fo + dx * 0.7f, dy * 0.7f, z, fo + dx * 3.6f, dy * 3.6f, z, 0.35f, 0.58f, 1.1f, prim)
                disc(fo + dx * 3.7f, dy * 3.7f, z, 0.8f, Color.parseColor("#12161F"))
            }
            // Torso and head lying back toward the other side.
            cap(dx * -0.1f, dy * -0.1f, 1.25f, dx * -1.9f, dy * -1.9f, 1.75f, 2.3f, prim)
            band(dx * -0.1f, dy * -0.1f, 1.25f, dx * -1.9f, dy * -1.9f, 1.75f, 0.25f, 0.38f, 2.38f, sec)
            disc(0.55f + dx * -2.9f, dy * -2.9f, 1.8f, 1.7f, sec)
            ring(0.9f + dx * -2.9f, dy * -2.9f, 1.8f, 1.15f, steel)
            // Arms reach out front: blocker and catcher.
            disc(1.8f + dx * -1.0f, dy * -1.0f + 0.0f, 1.25f, 1.4f, leather)
            cap(1.5f + dx * -1.7f, dy * -1.7f, 0.9f, 1.6f + dx * -1.7f, dy * -1.7f, 1.8f, 0.9f, padW)
            cap(1.6f + dx * -1.2f, dy * -1.2f, 0.9f, reach - 0.6f, 0f, 0.05f, 0.45f, wood)
            cap(reach - 0.6f, 0f, 0.05f, reach + 0.7f, 0f, 0.05f, 0.5f, tape)
            return finish()
        }

        val low = if (stance == 1) 1.0f else 0f   // butterfly drops the torso
        if (stance == 1) {
            for (s in intArrayOf(1, -1)) {
                cap(0f, s * 0.45f, 1.85f, 0.3f, s * 0.35f, 1.2f, 1.3f, pantsC)
                cap(0.3f, s * 0.35f, 1.2f, -0.15f, s * 2.5f, 0.45f, 1.2f, padW)
                band(0.3f, s * 0.35f, 1.2f, -0.15f, s * 2.5f, 0.45f, 0.3f, 0.55f, 1.25f, prim)
                disc(-0.15f, s * 2.55f, 0.45f, 0.75f, Color.parseColor("#12161F"))
            }
        } else {
            for (s in intArrayOf(1, -1)) {
                cap(0.1f, s * 0.62f, 0.2f, 0.9f, s * 0.62f, 0.2f, 0.6f, Color.parseColor("#12161F"))
                cap(0.2f, s * 0.62f, 0.3f, 0.3f, s * 0.62f, 2.7f, 1.25f, padW)
                band(0.2f, s * 0.62f, 0.3f, 0.3f, s * 0.62f, 2.7f, 0.35f, 0.58f, 1.3f, prim)
                band(0.2f, s * 0.62f, 0.3f, 0.3f, s * 0.62f, 2.7f, 0.78f, 0.84f, 1.3f, Color.parseColor("#9CA3AF"))
                cap(0.2f, s * 0.55f, 2.7f, 0.1f, s * 0.5f, 3.1f, 1.3f, pantsC)
            }
        }
        // Chest protector.
        val tz1 = 3.0f - low * 1.1f
        val tz2 = 5.0f - low * 1.0f
        cap(0.05f, 0f, tz1, 0.45f, 0f, tz2, 2.5f, prim)
        band(0.05f, 0f, tz1, 0.45f, 0f, tz2, 0.2f, 0.32f, 2.56f, sec)
        cap(0.45f, -1.1f, tz2 - 0.2f, 0.45f, 1.1f, tz2 - 0.2f, 1.1f, prim)
        // Catcher and blocker.
        val chz = 3.7f - low * 0.9f
        cap(0.45f, 1.2f, tz2 - 0.3f, 1.5f, 1.35f, chz, 0.8f, prim)
        disc(1.55f, 1.4f, chz, 1.5f, leather)
        disc(1.7f, 1.42f, chz + 0.05f, 0.85f, leatherL)
        cap(0.45f, -1.2f, tz2 - 0.3f, 1.4f, -1.2f, 3.0f - low * 0.9f, 0.8f, prim)
        cap(1.45f, -1.2f, 2.6f - low * 0.9f, 1.65f, -1.2f, 3.6f - low * 0.9f, 1.0f, padW)
        // Stick: shaft, paddle, blade.
        val bz = 3.05f - low * 0.9f
        cap(1.4f, -1.05f, bz, reach * 0.6f, -0.5f, bz * 0.4f, 0.4f, wood)
        cap(reach * 0.6f, -0.5f, bz * 0.4f, reach - 0.6f, 0f, 0.05f, 0.75f, wood)
        cap(reach - 0.6f, 0f, 0.05f, reach + 0.75f, 0f, 0.05f, 0.5f, tape)
        // Mask.
        val hz = 6.1f - low * 0.95f
        disc(0.7f, 0f, hz, 1.75f, sec)
        ring(1.05f, 0f, hz - 0.05f, 1.15f, steel)
        return finish()
    }
}
