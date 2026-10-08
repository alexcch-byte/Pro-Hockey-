package com.tablehockey.game.model

import android.graphics.Color
import java.io.Serializable

/**
 * A selectable club. Timbits clubs and the fictional pro clubs are invented; the NHL group uses real
 * club names with approximate team colours, but never real logos (crests are drawn procedurally
 * from the colours and the abbreviation).
 */
data class TeamInfo(
    val city: String,
    val name: String,
    val abbr: String,
    val primary: Int,
    val secondary: Int,
    val text: Int,
    val league: String = LEAGUE_PRO,
    /** Extra cosmetics from the Customise Team screen; null for stock clubs. */
    val style: TeamStyle? = null
) : Serializable {
    val fullName: String get() = if (city.isEmpty()) name else "$city $name"

    // ---- Read-only kit API for renderers (never null; stock clubs fall back to their colours).
    // `primary` / `secondary` already carry the customised colours. Everything below is extra.
    // TeamInfo instances are created once per match by byIndex(); compare by identity (!==), not ==.
    /** Full uniform description (pattern, trim, helmet, socks, crest colours). */
    val look: TeamStyle get() = style ?: TeamStyle.defaultFor(this)
    val trimColor: Int get() = look.trim
    val helmetColor: Int get() = look.helmet
    val sockColor: Int get() = look.sock
    val jerseyPattern: JerseyPattern get() = look.pattern
    val crestType: CrestType get() = look.crest
    val hasCustomKit: Boolean get() = style != null

    companion object {
        const val LEAGUE_PRO = "Pro League"
        const val LEAGUE_CALGARY = "Timbits U7"
        const val LEAGUE_NHL = "NHL"

        /**
         * Calgary-area Timbits (U7) clubs, one per minor hockey association.
         * Colours are approximations; edit here to match real jerseys.
         */
        private val CALGARY: List<TeamInfo> = listOf(
            TeamInfo("Glenlake", "Hawks", "GLK", Color.parseColor("#0B6E3A"), Color.parseColor("#F5D130"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Bow Valley", "Flames", "BVF", Color.parseColor("#C8102E"), Color.parseColor("#F1BE48"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Crowfoot", "Coyotes", "CRO", Color.parseColor("#1C2E5A"), Color.parseColor("#C4432B"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Blackfoot", "Chiefs", "BFC", Color.parseColor("#7A1F1F"), Color.parseColor("#F3E5AB"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Springbank", "Rockies", "SPB", Color.parseColor("#4B2E83"), Color.parseColor("#C0C0C0"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Trails West", "Wolves", "TWW", Color.parseColor("#1F2937"), Color.parseColor("#14B8A6"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("McKnight", "Mustangs", "MCK", Color.parseColor("#1E3A8A"), Color.parseColor("#F59E0B"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Northwest", "Warriors", "NWW", Color.parseColor("#14532D"), Color.parseColor("#FDE047"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Simons Valley", "Storm", "SVS", Color.parseColor("#0F3D6E"), Color.parseColor("#9CA3AF"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Bow River", "Bruins", "BRB", Color.parseColor("#111111"), Color.parseColor("#FCB514"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Midnapore", "Mavericks", "MID", Color.parseColor("#7F1D1D"), Color.parseColor("#111827"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Lake Bonavista", "Breakers", "LBB", Color.parseColor("#1E40AF"), Color.parseColor("#7DD3FC"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Southwest", "Cougars", "SWC", Color.parseColor("#B91C1C"), Color.parseColor("#111827"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Crowchild", "Blackhawks", "CCB", Color.parseColor("#B91C1C"), Color.parseColor("#0B0B0B"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Calgary", "Knights", "KNI", Color.parseColor("#2F3E5C"), Color.parseColor("#E5E7EB"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Airdrie", "Lightning", "AIR", Color.parseColor("#1D4ED8"), Color.parseColor("#FACC15"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Okotoks", "Oilers", "OKO", Color.parseColor("#0F2A5C"), Color.parseColor("#F97316"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Chestermere", "Lakers", "CHE", Color.parseColor("#0E7490"), Color.parseColor("#FDE68A"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Foothills", "Flyers", "FTH", Color.parseColor("#EA580C"), Color.parseColor("#111827"), Color.WHITE, LEAGUE_CALGARY),
            TeamInfo("Strathmore", "Storm", "STR", Color.parseColor("#374151"), Color.parseColor("#60A5FA"), Color.WHITE, LEAGUE_CALGARY)
        )

        private val FICTIONAL: List<TeamInfo> = listOf(
            TeamInfo("Denver", "Peaks", "DNP", Color.parseColor("#1D4ED8"), Color.parseColor("#F8FAFC"), Color.WHITE),
            TeamInfo("Chicago", "Blizzard", "CHB", Color.parseColor("#B91C1C"), Color.parseColor("#FBBF24"), Color.WHITE),
            TeamInfo("Montreal", "Royals", "MRY", Color.parseColor("#7C3AED"), Color.parseColor("#FDE68A"), Color.WHITE),
            TeamInfo("Vancouver", "Orcas", "VAO", Color.parseColor("#0F766E"), Color.parseColor("#E2E8F0"), Color.WHITE),
            TeamInfo("Boston", "Harbor", "BOH", Color.parseColor("#111827"), Color.parseColor("#F59E0B"), Color.WHITE),
            TeamInfo("Minnesota", "Timber", "MNT", Color.parseColor("#166534"), Color.parseColor("#FCA5A5"), Color.WHITE),
            TeamInfo("Dallas", "Longhorns", "DLL", Color.parseColor("#0E7490"), Color.parseColor("#F1F5F9"), Color.WHITE),
            TeamInfo("Toronto", "Pilots", "TRP", Color.parseColor("#1E3A8A"), Color.parseColor("#93C5FD"), Color.WHITE)
        )

        /** The 32 NHL clubs (colours approximate; no logos). */
        private val NHL: List<TeamInfo> = listOf(
            TeamInfo("Anaheim", "Ducks", "ANA", Color.parseColor("#F47A38"), Color.parseColor("#111111"), Color.parseColor("#111111"), LEAGUE_NHL),
            TeamInfo("Boston", "Bruins", "BOS", Color.parseColor("#111111"), Color.parseColor("#FFB81C"), Color.parseColor("#FFB81C"), LEAGUE_NHL),
            TeamInfo("Buffalo", "Sabres", "BUF", Color.parseColor("#003087"), Color.parseColor("#FFB81C"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Calgary", "Flames", "CGY", Color.parseColor("#C8102E"), Color.parseColor("#F1BE48"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Carolina", "Hurricanes", "CAR", Color.parseColor("#CC0000"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Chicago", "Blackhawks", "CHI", Color.parseColor("#CF0A2C"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Colorado", "Avalanche", "COL", Color.parseColor("#6F263D"), Color.parseColor("#236192"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Columbus", "Blue Jackets", "CBJ", Color.parseColor("#002654"), Color.parseColor("#CE1126"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Dallas", "Stars", "DAL", Color.parseColor("#006847"), Color.parseColor("#8F8F8C"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Detroit", "Red Wings", "DET", Color.parseColor("#CE1126"), Color.parseColor("#FFFFFF"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Edmonton", "Oilers", "EDM", Color.parseColor("#041E42"), Color.parseColor("#FF4C00"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Florida", "Panthers", "FLA", Color.parseColor("#041E42"), Color.parseColor("#C8102E"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Los Angeles", "Kings", "LAK", Color.parseColor("#111111"), Color.parseColor("#A2AAAD"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Minnesota", "Wild", "MIN", Color.parseColor("#154734"), Color.parseColor("#A6192E"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Montreal", "Canadiens", "MTL", Color.parseColor("#AF1E2D"), Color.parseColor("#192168"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Nashville", "Predators", "NSH", Color.parseColor("#FFB81C"), Color.parseColor("#041E42"), Color.parseColor("#041E42"), LEAGUE_NHL),
            TeamInfo("New Jersey", "Devils", "NJD", Color.parseColor("#CE1126"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("New York", "Islanders", "NYI", Color.parseColor("#00539B"), Color.parseColor("#F47D30"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("New York", "Rangers", "NYR", Color.parseColor("#0038A8"), Color.parseColor("#CE1126"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Ottawa", "Senators", "OTT", Color.parseColor("#C52032"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Philadelphia", "Flyers", "PHI", Color.parseColor("#F74902"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Pittsburgh", "Penguins", "PIT", Color.parseColor("#111111"), Color.parseColor("#FCB514"), Color.parseColor("#FCB514"), LEAGUE_NHL),
            TeamInfo("San Jose", "Sharks", "SJS", Color.parseColor("#006D75"), Color.parseColor("#111111"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Seattle", "Kraken", "SEA", Color.parseColor("#001628"), Color.parseColor("#99D9D9"), Color.parseColor("#99D9D9"), LEAGUE_NHL),
            TeamInfo("St. Louis", "Blues", "STL", Color.parseColor("#002F87"), Color.parseColor("#FCB514"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Tampa Bay", "Lightning", "TBL", Color.parseColor("#002868"), Color.parseColor("#FFFFFF"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Toronto", "Maple Leafs", "TOR", Color.parseColor("#00205B"), Color.parseColor("#FFFFFF"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Utah", "Mammoth", "UTA", Color.parseColor("#6CACE4"), Color.parseColor("#010101"), Color.parseColor("#010101"), LEAGUE_NHL),
            TeamInfo("Vancouver", "Canucks", "VAN", Color.parseColor("#00205B"), Color.parseColor("#00843D"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Vegas", "Golden Knights", "VGK", Color.parseColor("#B4975A"), Color.parseColor("#333F42"), Color.parseColor("#111111"), LEAGUE_NHL),
            TeamInfo("Washington", "Capitals", "WSH", Color.parseColor("#C8102E"), Color.parseColor("#041E42"), Color.parseColor("#FFFFFF"), LEAGUE_NHL),
            TeamInfo("Winnipeg", "Jets", "WPG", Color.parseColor("#041E42"), Color.parseColor("#004C97"), Color.parseColor("#FFFFFF"), LEAGUE_NHL)
        )

        /**
         * Combined list. Indices are stable (Timbits 0-19, fictional pro 20-27, NHL 28-59) and are what
         * MatchConfig, the network config and the tournament store, so host and guest always agree.
         */
        val ALL: List<TeamInfo> = CALGARY + FICTIONAL + NHL

        /** Weighted RGB distance ("redmean"), 0 = identical, about 765 = black vs white. */
        fun colourDistance(a: Int, b: Int): Float {
            val rm = (Color.red(a) + Color.red(b)) / 2f
            val dr = (Color.red(a) - Color.red(b)).toFloat()
            val dg = (Color.green(a) - Color.green(b)).toFloat()
            val db = (Color.blue(a) - Color.blue(b)).toFloat()
            return Math.sqrt(((2f + rm / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rm) / 256f) * db * db).toDouble()).toFloat()
        }

        /** Primaries closer than this are treated as the same jersey colour. */
        const val CLASH_DISTANCE = 120f

        private fun luma(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

        /**
         * Alternate (road) kit for [t] against an opposing primary [other]: tries swapping primary and secondary,
         * then white, then the lighter of its two colours, and takes the first that is clear of [other]
         * (else the most distant). Pure function of the colours, so host and guest agree.
         */
        private fun alternateKit(t: TeamInfo, other: Int): TeamInfo {
            val white = Color.parseColor("#F5F5F5")
            val lighter = if (luma(t.primary) >= luma(t.secondary)) t.primary else t.secondary
            val prim = intArrayOf(t.secondary, white, lighter)
            var best = 0
            var bestD = -1f
            for (i in prim.indices) {
                val d = colourDistance(prim[i], other)
                if (d >= CLASH_DISTANCE) { best = i; bestD = d; break }
                if (d > bestD) { bestD = d; best = i }
            }
            val np = prim[best]
            val ns = if (colourDistance(np, t.primary) > 60f) t.primary else if (luma(np) > 0.5f) Color.parseColor("#111111") else white
            val text = if (luma(np) > 0.6f) Color.parseColor("#111111") else Color.WHITE
            return t.copy(primary = np, secondary = ns, text = text)
        }

        /**
         * Builds the two clubs of a match with any customisations applied, and resolves a jersey clash:
         * if the primaries are too close, the away club (or the home club when only the away club has a
         * custom kit) wears an alternate kit. If both have custom kits nothing is changed.
         */
        fun matchTeams(homeIdx: Int, awayIdx: Int): Array<TeamInfo> {
            var home = byIndex(homeIdx)
            var away = byIndex(awayIdx)
            if (colourDistance(home.primary, away.primary) < CLASH_DISTANCE) {
                if (!away.hasCustomKit) away = alternateKit(away, home.primary)
                else if (!home.hasCustomKit) home = alternateKit(home, away.primary)
            }
            return arrayOf(home, away)
        }

        /** The leagues offered in the pickers, in toggle order. */
        val LEAGUES: List<String> = listOf(LEAGUE_CALGARY, LEAGUE_NHL)

        /** Indices into [ALL] of the clubs in [league] (anything but NHL means Timbits). */
        fun indicesFor(league: String): IntArray {
            val l = if (league == LEAGUE_NHL) LEAGUE_NHL else LEAGUE_CALGARY
            return ALL.indices.filter { ALL[it].league == l }.toIntArray()
        }

        fun byIndex(i: Int): TeamInfo = TeamStyleStore.apply(ALL[i.coerceIn(0, ALL.size - 1)])

        /** Index of the default home club (Glenlake Hawks). */
        val DEFAULT_HOME: Int = ALL.indexOfFirst { it.city == "Glenlake" }.coerceAtLeast(0)
        val DEFAULT_AWAY: Int = ALL.indexOfFirst { it.city == "Bow Valley" }.coerceAtLeast(1)

        /** Spinner label: club name plus its league. */
        fun label(t: TeamInfo): String = t.fullName + "  ·  " + t.league
    }
}
