package com.tablehockey.game.model

import android.graphics.Color
import java.io.Serializable

/**
 * A selectable club. All names are fictional so the game can ship on the
 * Amazon Appstore without any league or club trademarks.
 */
data class TeamInfo(
    val city: String,
    val name: String,
    val abbr: String,
    val primary: Int,
    val secondary: Int,
    val text: Int,
    val league: String = LEAGUE_PRO
) : Serializable {
    val fullName: String get() = if (city.isEmpty()) name else "$city $name"

    companion object {
        const val LEAGUE_PRO = "Pro League"
        const val LEAGUE_CALGARY = "Timbits U7"

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

        val ALL: List<TeamInfo> = CALGARY + listOf(
            TeamInfo("Denver", "Peaks", "DEN", Color.parseColor("#1D4ED8"), Color.parseColor("#F8FAFC"), Color.WHITE),
            TeamInfo("Chicago", "Blizzard", "CHI", Color.parseColor("#B91C1C"), Color.parseColor("#FBBF24"), Color.WHITE),
            TeamInfo("Montreal", "Royals", "MTL", Color.parseColor("#7C3AED"), Color.parseColor("#FDE68A"), Color.WHITE),
            TeamInfo("Vancouver", "Orcas", "VAN", Color.parseColor("#0F766E"), Color.parseColor("#E2E8F0"), Color.WHITE),
            TeamInfo("Boston", "Harbor", "BOS", Color.parseColor("#111827"), Color.parseColor("#F59E0B"), Color.WHITE),
            TeamInfo("Minnesota", "Timber", "MIN", Color.parseColor("#166534"), Color.parseColor("#FCA5A5"), Color.WHITE),
            TeamInfo("Dallas", "Longhorns", "DAL", Color.parseColor("#0E7490"), Color.parseColor("#F1F5F9"), Color.WHITE),
            TeamInfo("Toronto", "Pilots", "TOR", Color.parseColor("#1E3A8A"), Color.parseColor("#93C5FD"), Color.WHITE)
        )

        fun byIndex(i: Int): TeamInfo = ALL[i.coerceIn(0, ALL.size - 1)]

        /** Index of the default home club (Glenlake Hawks). */
        val DEFAULT_HOME: Int = ALL.indexOfFirst { it.city == "Glenlake" }.coerceAtLeast(0)
        val DEFAULT_AWAY: Int = ALL.indexOfFirst { it.city == "Bow Valley" }.coerceAtLeast(1)

        /** Spinner label: club name plus its league. */
        fun label(t: TeamInfo): String = t.fullName + "  ·  " + t.league
    }
}
