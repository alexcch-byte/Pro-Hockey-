package com.tablehockey.game.network

import com.tablehockey.game.game.GameEvent
import com.tablehockey.game.game.Phase
import com.tablehockey.game.game.PlayerInput
import com.tablehockey.game.game.World
import org.json.JSONArray
import org.json.JSONObject

/** Fixed TCP port the host listens on for LAN play (also advertised via NSD). */
const val HOST_PORT = 8943
const val SERVICE_TYPE = "_powerplayhockey._tcp."

/**
 * Line-delimited JSON protocol.
 *
 *  host -> client  "cfg"  match set-up (teams, period length)
 *  client -> host  "in"   joystick + button state for the away team
 *  host -> client  "st"   full authoritative snapshot, ~30 Hz
 */
object NetCodec {

    fun configJson(home: Int, away: Int, periodLength: Int): String = JSONObject().apply {
        put("t", "cfg")
        put("home", home)
        put("away", away)
        put("pl", periodLength)
    }.toString()

    fun inputJson(i: PlayerInput): String = JSONObject().apply {
        put("t", "in")
        put("mx", i.moveX.toDouble())
        put("my", i.moveY.toDouble())
        put("sh", i.shootHeld)
        put("sr", i.shootRelease)
        put("sc", i.shootCharge.toDouble())
        put("ps", i.pass)
        put("ht", i.hit)
    }.toString()

    /** Merges a received input into [dst]; one-shot presses accumulate until consumed. */
    fun applyInput(obj: JSONObject, dst: PlayerInput) {
        dst.moveX = obj.optDouble("mx", 0.0).toFloat()
        dst.moveY = obj.optDouble("my", 0.0).toFloat()
        dst.shootHeld = obj.optBoolean("sh", false)
        if (obj.optBoolean("sr", false)) {
            dst.shootRelease = true
            dst.shootCharge = obj.optDouble("sc", 0.0).toFloat()
        } else if (dst.shootHeld) {
            dst.shootCharge = obj.optDouble("sc", 0.0).toFloat()
        }
        if (obj.optBoolean("ps", false)) dst.pass = true
        if (obj.optBoolean("ht", false)) dst.hit = true
    }

    private const val F_STUN = 1
    private const val F_POKE = 2
    private const val F_CHECK = 4
    private const val F_BUTTERFLY = 8
    private const val F_SWING = 16

    fun stateJson(w: World): String {
        val o = JSONObject()
        o.put("t", "st")
        o.put("ph", w.phase.ordinal)
        o.put("p", JSONArray().apply {
            put(w.puck.x.toDouble()); put(w.puck.y.toDouble()); put(w.puck.vx.toDouble()); put(w.puck.vy.toDouble())
        })
        o.put("car", w.codeOf(w.puck.carrier))
        val sk = JSONArray()
        for (s in w.allSkaters) {
            var flags = 0
            if (s.stunTimer > 0f) flags = flags or F_STUN
            if (s.pokeTimer > 0f) flags = flags or F_POKE
            if (s.checkTimer > 0f) flags = flags or F_CHECK
            if (s.butterfly) flags = flags or F_BUTTERFLY
            if (s.swingTimer > 0f) flags = flags or F_SWING
            sk.put(JSONArray().apply {
                put(Math.round(s.x * 100.0) / 100.0)
                put(Math.round(s.y * 100.0) / 100.0)
                put(Math.round(s.facing * 1000.0) / 1000.0)
                put(flags)
                put(Math.round(s.stride * 10.0) / 10.0)
            })
        }
        o.put("sk", sk)
        o.put("c0", w.controlled[0]); o.put("c1", w.controlled[1])
        o.put("s0", w.teams[0].score); o.put("s1", w.teams[1].score)
        o.put("h0", w.teams[0].shots); o.put("h1", w.teams[1].shots)
        o.put("per", w.period)
        o.put("clk", w.clock.toDouble())
        o.put("ot", w.overtime)
        o.put("ad", w.teams[0].attackDir.toDouble())
        o.put("fx", w.faceoffX.toDouble()); o.put("fy", w.faceoffY.toDouble())
        o.put("ban", w.banner ?: JSONObject.NULL)
        o.put("bs", w.bannerSub ?: JSONObject.NULL)
        o.put("bt", w.bannerTimer.toDouble())
        o.put("ch", w.shotCharge[1].toDouble())
        if (w.events.isNotEmpty()) {
            o.put("ev", JSONArray().apply { for (e in w.events) put(e.ordinal) })
        }
        return o.toString()
    }

    /**
     * Applies a snapshot to a mirrored world. Positions go into the net*
     * targets so the client can interpolate; everything else is copied.
     */
    fun applyState(obj: JSONObject, w: World, events: MutableList<GameEvent>) {
        val phases = Phase.values()
        w.phase = phases[obj.optInt("ph", 0).coerceIn(0, phases.size - 1)]
        val p = obj.getJSONArray("p")
        w.puck.netX = p.getDouble(0).toFloat()
        w.puck.netY = p.getDouble(1).toFloat()
        w.puck.vx = p.getDouble(2).toFloat()
        w.puck.vy = p.getDouble(3).toFloat()
        w.puck.carrier = w.skaterByCode(obj.optInt("car", -1))
        val sk = obj.getJSONArray("sk")
        val all = w.allSkaters
        for (i in 0 until minOf(sk.length(), all.size)) {
            val a = sk.getJSONArray(i)
            val s = all[i]
            s.netX = a.getDouble(0).toFloat()
            s.netY = a.getDouble(1).toFloat()
            s.netFacing = a.getDouble(2).toFloat()
            val flags = a.getInt(3)
            s.stunTimer = if (flags and F_STUN != 0) 1f else 0f
            s.pokeTimer = if (flags and F_POKE != 0) 1f else 0f
            s.checkTimer = if (flags and F_CHECK != 0) 1f else 0f
            s.butterfly = flags and F_BUTTERFLY != 0
            if (flags and F_SWING != 0) { if (s.swingTimer <= 0f) s.swingTimer = 0.35f } else s.swingTimer = 0f
            s.stride = a.optDouble(4, 0.0).toFloat()
        }
        w.controlled[0] = obj.optInt("c0", -1)
        w.controlled[1] = obj.optInt("c1", -1)
        w.teams[0].score = obj.optInt("s0", 0)
        w.teams[1].score = obj.optInt("s1", 0)
        w.teams[0].shots = obj.optInt("h0", 0)
        w.teams[1].shots = obj.optInt("h1", 0)
        w.period = obj.optInt("per", 1)
        w.clock = obj.optDouble("clk", 0.0).toFloat()
        w.overtime = obj.optBoolean("ot", false)
        val ad = obj.optDouble("ad", 1.0).toFloat()
        w.teams[0].attackDir = ad
        w.teams[1].attackDir = -ad
        w.faceoffX = obj.optDouble("fx", 0.0).toFloat()
        w.faceoffY = obj.optDouble("fy", 0.0).toFloat()
        w.banner = if (obj.isNull("ban")) null else obj.getString("ban")
        w.bannerSub = if (obj.isNull("bs")) null else obj.getString("bs")
        w.bannerTimer = obj.optDouble("bt", 0.0).toFloat()
        w.shotCharge[1] = obj.optDouble("ch", 0.0).toFloat()
        val ev = obj.optJSONArray("ev")
        if (ev != null) {
            val values = GameEvent.values()
            for (i in 0 until ev.length()) {
                val idx = ev.getInt(i)
                if (idx in values.indices) events.add(values[idx])
            }
        }
    }
}
