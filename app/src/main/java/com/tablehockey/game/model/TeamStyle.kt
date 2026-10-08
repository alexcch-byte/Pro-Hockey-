package com.tablehockey.game.model

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.Serializable

enum class JerseyPattern(val label: String) {
    SOLID("Solid"), HOOPS("Hoops"), CHEST_BAND("Chest band"), SASH("Sash"),
    SPLIT("Split"), CHEVRON("Chevron"), YOKE("Yoke")
}

/** Emblem drawn on the chest crest. All are procedural paths in ui/TeamArt. */
enum class CrestType(val label: String) {
    LETTERS("Letters"), STAR("Star"), BOLT("Bolt"), PEAKS("Peaks"), FLAME("Flame"),
    CROWN("Crown"), WAVES("Waves"), PAW("Paw"), WINGS("Wings")
}

/** Badge shape behind the emblem. */
enum class CrestFrame(val label: String) { ROUND("Round"), SHIELD("Shield"), DIAMOND("Diamond"), BADGE("Badge") }

/**
 * Cosmetic uniform + crest description for one club. [primary]/[secondary] also overwrite the
 * club colours in [TeamInfo] (which is what Renderer sprites read); the rest is carried in
 * [TeamInfo.style] for the preview art and for any renderer that wants to use it.
 */
data class TeamStyle(
    val primary: Int,
    val secondary: Int,
    val trim: Int,
    val helmet: Int,
    val sock: Int,
    val pattern: JerseyPattern = JerseyPattern.SOLID,
    val crest: CrestType = CrestType.LETTERS,
    val frame: CrestFrame = CrestFrame.ROUND,
    val crestFg: Int = primary,
    val crestBg: Int = secondary,
    val crestOutline: Int = trim
) : Serializable {

    fun toJson(): String = JSONObject().apply {
        put("p", primary); put("s", secondary); put("t", trim); put("h", helmet); put("k", sock)
        put("pat", pattern.name); put("c", crest.name); put("f", frame.name)
        put("cf", crestFg); put("cb", crestBg); put("co", crestOutline)
    }.toString()

    companion object {
        fun defaultFor(t: TeamInfo) = TeamStyle(
            primary = t.primary, secondary = t.secondary, trim = Color.WHITE,
            helmet = t.primary, sock = t.primary
        )

        fun fromJson(s: String, fallback: TeamStyle): TeamStyle = try {
            val o = JSONObject(s)
            TeamStyle(
                primary = o.optInt("p", fallback.primary),
                secondary = o.optInt("s", fallback.secondary),
                trim = o.optInt("t", fallback.trim),
                helmet = o.optInt("h", fallback.helmet),
                sock = o.optInt("k", fallback.sock),
                pattern = enumOr(o.optString("pat"), fallback.pattern),
                crest = enumOr(o.optString("c"), fallback.crest),
                frame = enumOr(o.optString("f"), fallback.frame),
                crestFg = o.optInt("cf", fallback.crestFg),
                crestBg = o.optInt("cb", fallback.crestBg),
                crestOutline = o.optInt("co", fallback.crestOutline)
            )
        } catch (e: Exception) {
            fallback
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String, d: E): E =
            try { enumValueOf<E>(name) } catch (e: Exception) { d }
    }
}

/**
 * Per-club uniform overrides, persisted in their own SharedPreferences file. Overrides are
 * mirrored in memory so [TeamInfo.byIndex] (called from the engine, without a Context) can apply them.
 * Call [ensureLoaded] from any Activity that can start a match.
 */
object TeamStyleStore {
    private const val NAME = "team_styles"
    private const val KEY_FAVOURITE = "favourite_club"
    private const val PREFIX = "style_"

    @Volatile private var overrides: Map<String, TeamStyle> = emptyMap()
    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun key(t: TeamInfo) = t.city + "|" + t.name

    fun ensureLoaded(ctx: Context) {
        if (!loaded) reload(ctx)
    }

    @Synchronized
    fun reload(ctx: Context) {
        val m = HashMap<String, TeamStyle>()
        for (t in TeamInfo.ALL) {
            val s = prefs(ctx).getString(PREFIX + key(t), null) ?: continue
            m[key(t)] = TeamStyle.fromJson(s, TeamStyle.defaultFor(t))
        }
        overrides = m
        loaded = true
    }

    fun isCustom(t: TeamInfo) = overrides.containsKey(key(t))

    /** The stored style, or the club's stock look. [t] must be the stock [TeamInfo.ALL] entry. */
    fun styleFor(t: TeamInfo): TeamStyle = overrides[key(t)] ?: TeamStyle.defaultFor(t)

    @Synchronized
    fun save(ctx: Context, t: TeamInfo, style: TeamStyle) {
        prefs(ctx).edit().putString(PREFIX + key(t), style.toJson()).apply()
        overrides = overrides + (key(t) to style)
    }

    @Synchronized
    fun reset(ctx: Context, t: TeamInfo) {
        prefs(ctx).edit().remove(PREFIX + key(t)).apply()
        overrides = overrides - key(t)
    }

    /** Returns [t] with any saved colours and style applied. */
    fun apply(t: TeamInfo): TeamInfo {
        val s = overrides[key(t)] ?: return t
        return t.copy(primary = s.primary, secondary = s.secondary, style = s)
    }

    /** Index into [TeamInfo.ALL] of the player's favourite club (menu hero, Match Setup default). */
    fun favourite(ctx: Context): Int {
        val k = prefs(ctx).getString(KEY_FAVOURITE, null) ?: return TeamInfo.DEFAULT_HOME
        val i = TeamInfo.ALL.indexOfFirst { key(it) == k }
        return if (i >= 0) i else TeamInfo.DEFAULT_HOME
    }

    fun setFavourite(ctx: Context, index: Int) {
        prefs(ctx).edit().putString(KEY_FAVOURITE, key(TeamInfo.ALL[index])).apply()
    }
}
