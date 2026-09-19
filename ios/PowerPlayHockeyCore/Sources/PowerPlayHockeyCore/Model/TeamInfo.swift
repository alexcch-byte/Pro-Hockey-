/// A selectable club. All names are fictional so the game can ship without
/// any league or club trademarks.
struct TeamInfo {
    static let LEAGUE_PRO = "Pro League"
    static let LEAGUE_CALGARY = "Timbits U7"

    let city: String
    let name: String
    let abbr: String
    let primary: UInt32
    let secondary: UInt32
    let text: UInt32
    let league: String

    init(_ city: String, _ name: String, _ abbr: String, _ primary: UInt32, _ secondary: UInt32, _ text: UInt32, _ league: String = TeamInfo.LEAGUE_PRO) {
        self.city = city
        self.name = name
        self.abbr = abbr
        self.primary = primary
        self.secondary = secondary
        self.text = text
        self.league = league
    }

    var fullName: String { city.isEmpty ? name : "\(city) \(name)" }

    /// Calgary-area Timbits (U7) clubs, one per minor hockey association.
    /// Colours are approximations; edit here to match real jerseys.
    private static let CALGARY: [TeamInfo] = [
        TeamInfo("Glenlake", "Hawks", "GLK", HexColor.argb("#0B6E3A"), HexColor.argb("#F5D130"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Bow Valley", "Flames", "BVF", HexColor.argb("#C8102E"), HexColor.argb("#F1BE48"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Crowfoot", "Coyotes", "CRO", HexColor.argb("#1C2E5A"), HexColor.argb("#C4432B"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Blackfoot", "Chiefs", "BFC", HexColor.argb("#7A1F1F"), HexColor.argb("#F3E5AB"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Springbank", "Rockies", "SPB", HexColor.argb("#4B2E83"), HexColor.argb("#C0C0C0"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Trails West", "Wolves", "TWW", HexColor.argb("#1F2937"), HexColor.argb("#14B8A6"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("McKnight", "Mustangs", "MCK", HexColor.argb("#1E3A8A"), HexColor.argb("#F59E0B"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Northwest", "Warriors", "NWW", HexColor.argb("#14532D"), HexColor.argb("#FDE047"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Simons Valley", "Storm", "SVS", HexColor.argb("#0F3D6E"), HexColor.argb("#9CA3AF"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Bow River", "Bruins", "BRB", HexColor.argb("#111111"), HexColor.argb("#FCB514"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Midnapore", "Mavericks", "MID", HexColor.argb("#7F1D1D"), HexColor.argb("#111827"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Lake Bonavista", "Breakers", "LBB", HexColor.argb("#1E40AF"), HexColor.argb("#7DD3FC"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Southwest", "Cougars", "SWC", HexColor.argb("#B91C1C"), HexColor.argb("#111827"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Crowchild", "Blackhawks", "CCB", HexColor.argb("#B91C1C"), HexColor.argb("#0B0B0B"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Calgary", "Knights", "KNI", HexColor.argb("#2F3E5C"), HexColor.argb("#E5E7EB"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Airdrie", "Lightning", "AIR", HexColor.argb("#1D4ED8"), HexColor.argb("#FACC15"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Okotoks", "Oilers", "OKO", HexColor.argb("#0F2A5C"), HexColor.argb("#F97316"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Chestermere", "Lakers", "CHE", HexColor.argb("#0E7490"), HexColor.argb("#FDE68A"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Foothills", "Flyers", "FTH", HexColor.argb("#EA580C"), HexColor.argb("#111827"), HexColor.white, LEAGUE_CALGARY),
        TeamInfo("Strathmore", "Storm", "STR", HexColor.argb("#374151"), HexColor.argb("#60A5FA"), HexColor.white, LEAGUE_CALGARY)
    ]

    static let ALL: [TeamInfo] = CALGARY + [
        TeamInfo("Denver", "Peaks", "DEN", HexColor.argb("#1D4ED8"), HexColor.argb("#F8FAFC"), HexColor.white),
        TeamInfo("Chicago", "Blizzard", "CHI", HexColor.argb("#B91C1C"), HexColor.argb("#FBBF24"), HexColor.white),
        TeamInfo("Montreal", "Royals", "MTL", HexColor.argb("#7C3AED"), HexColor.argb("#FDE68A"), HexColor.white),
        TeamInfo("Vancouver", "Orcas", "VAN", HexColor.argb("#0F766E"), HexColor.argb("#E2E8F0"), HexColor.white),
        TeamInfo("Boston", "Harbor", "BOS", HexColor.argb("#111827"), HexColor.argb("#F59E0B"), HexColor.white),
        TeamInfo("Minnesota", "Timber", "MIN", HexColor.argb("#166534"), HexColor.argb("#FCA5A5"), HexColor.white),
        TeamInfo("Dallas", "Longhorns", "DAL", HexColor.argb("#0E7490"), HexColor.argb("#F1F5F9"), HexColor.white),
        TeamInfo("Toronto", "Pilots", "TOR", HexColor.argb("#1E3A8A"), HexColor.argb("#93C5FD"), HexColor.white)
    ]

    static func byIndex(_ i: Int) -> TeamInfo {
        ALL[min(max(i, 0), ALL.count - 1)]
    }

    /// Index of the default home club (Glenlake Hawks).
    static let DEFAULT_HOME: Int = max(ALL.firstIndex { $0.city == "Glenlake" } ?? 0, 0)
    static let DEFAULT_AWAY: Int = max(ALL.firstIndex { $0.city == "Bow Valley" } ?? 1, 1)

    /// Picker label: club name plus its league.
    static func label(_ t: TeamInfo) -> String { t.fullName + "  ·  " + t.league }
}
