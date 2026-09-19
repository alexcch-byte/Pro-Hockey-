package com.tablehockey.game.game

import com.tablehockey.game.model.AiDifficulty
import com.tablehockey.game.model.TeamInfo

enum class Phase { FACEOFF, PLAY, WHISTLE, GOAL, PERIOD_END, GAME_OVER }

/** One-shot things that happened this tick; the view turns them into sounds / effects. */
enum class GameEvent { SHOT, PASS, BOARDS, POST, GOAL, HIT, POKE, SAVE, WHISTLE, HORN, FACEOFF_DROP, PICKUP, PERIOD_END, GAME_OVER, FACEOFF_SET }

/** Per-frame command state from a human controller (touch or network). */
class PlayerInput {
    var moveX = 0f
    var moveY = 0f
    var shootHeld = false
    var shootRelease = false
    var shootCharge = 0f
    var pass = false
    var hit = false

    val moveMagnitude: Float get() = kotlin.math.hypot(moveX, moveY)

    /** Clears edge-triggered buttons after the simulation consumed them. */
    fun clearPulses() {
        shootRelease = false
        pass = false
        hit = false
    }

    fun copyFrom(o: PlayerInput) {
        moveX = o.moveX; moveY = o.moveY
        shootHeld = o.shootHeld; shootRelease = o.shootRelease; shootCharge = o.shootCharge
        pass = o.pass; hit = o.hit
    }
}

/** Difficulty knobs consumed by the AI and the goalies. */
class AiSettings(
    val speedMul: Float,
    val reaction: Float,        // seconds between AI decisions
    val shotAccuracy: Float,    // 0..1
    val passAccuracy: Float,
    val shootTendency: Float,
    val pokeChance: Float,      // per decision tick when in range
    val hitChance: Float,
    val goalieSkill: Float,     // scales the AI goalie's speed / positioning
    val coverChance: Float,     // chance a save is frozen for a faceoff
    val faceoffHumanBias: Float, // probability the human wins a draw
    val goalieLeak: Float,      // chance an AI-goalie save lets the puck through (five-hole)
    val goaliePadScale: Float,  // size of the AI goalie's blocking area
    val aimAssist: Float        // 0..1 how strongly human shots steer to the open side
) {
    companion object {
        fun forDifficulty(d: AiDifficulty) = when (d) {
            AiDifficulty.EASY -> AiSettings(
                speedMul = 0.72f, reaction = 0.6f, shotAccuracy = 0.5f, passAccuracy = 0.65f, shootTendency = 0.45f,
                pokeChance = 0.18f, hitChance = 0.06f, goalieSkill = 0.5f, coverChance = 0.15f, faceoffHumanBias = 0.65f,
                goalieLeak = 0.3f, goaliePadScale = 0.75f, aimAssist = 1f
            )
            AiDifficulty.MEDIUM -> AiSettings(
                speedMul = 0.95f, reaction = 0.28f, shotAccuracy = 0.72f, passAccuracy = 0.85f, shootTendency = 0.7f,
                pokeChance = 0.55f, hitChance = 0.35f, goalieSkill = 0.9f, coverChance = 0.3f, faceoffHumanBias = 0.5f,
                goalieLeak = 0.1f, goaliePadScale = 0.95f, aimAssist = 0.5f
            )
            AiDifficulty.HARD -> AiSettings(
                speedMul = 1.06f, reaction = 0.15f, shotAccuracy = 0.88f, passAccuracy = 0.95f, shootTendency = 0.85f,
                pokeChance = 0.75f, hitChance = 0.55f, goalieSkill = 1.05f, coverChance = 0.35f, faceoffHumanBias = 0.4f,
                goalieLeak = 0f, goaliePadScale = 1.05f, aimAssist = 0f
            )
        }
    }
}

/**
 * Complete match state: both clubs, the puck, the clock and the flow phase.
 * The host simulates it; a WiFi client just mirrors it for rendering.
 */
class World(homeInfo: TeamInfo, awayInfo: TeamInfo, val periodLength: Int) {
    val teams = arrayOf(Team(0, homeInfo, 1f), Team(1, awayInfo, -1f))
    val puck = Puck()

    var period = 1
    var clock = periodLength.toFloat()
    var overtime = false

    var phase = Phase.FACEOFF
    var phaseTimer = 0f
    var faceoffX = 0f
    var faceoffY = 0f

    var banner: String? = null
    var bannerSub: String? = null
    var bannerTimer = 0f

    /** Index of the skater each human team controls; -1 when the team is AI. */
    val controlled = intArrayOf(-1, -1)
    val switchLock = floatArrayOf(0f, 0f)
    /** Charge level of the human's held shot, for the HUD meter. */
    val shotCharge = floatArrayOf(0f, 0f)

    val events = ArrayList<GameEvent>()

    val allSkaters: List<Skater> = teams[0].skaters + teams[1].skaters

    fun team(id: Int) = teams[id]
    fun opponent(id: Int) = teams[1 - id]

    fun skaterByCode(code: Int): Skater? {
        if (code < 0) return null
        val t = code / 6
        val i = code % 6
        if (t !in 0..1 || i !in 0..5) return null
        return teams[t].skaters[i]
    }

    fun codeOf(s: Skater?): Int = if (s == null) -1 else s.team * 6 + s.index

    fun isHuman(teamId: Int) = controlled[teamId] >= 0

    fun controlledSkater(teamId: Int): Skater? {
        val i = controlled[teamId]
        return if (i >= 0) teams[teamId].skaters[i] else null
    }

    fun showBanner(text: String, sub: String? = null, seconds: Float) {
        banner = text
        bannerSub = sub
        bannerTimer = seconds
    }

    fun clockText(): String {
        val total = if (overtime) clock.toInt() else kotlin.math.ceil(clock.toDouble()).toInt()
        val m = total / 60
        val s = total % 60
        return String.format("%d:%02d", m, s)
    }

    fun periodText(): String = when {
        overtime -> "OT"
        period == 1 -> "1ST"
        period == 2 -> "2ND"
        else -> "3RD"
    }
}
