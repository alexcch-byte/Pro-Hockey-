/// Body colour, trim colour and number/lettering colour of one jersey.
struct TeamKit {
    let primary: UInt32
    let secondary: UInt32
    let text: UInt32

    init(_ primary: UInt32, _ secondary: UInt32, _ text: UInt32) {
        self.primary = primary
        self.secondary = secondary
        self.text = text
    }
}

/// A selectable club. Timbits clubs and the fictional pro clubs are invented; the NHL group uses real
/// The NHL and GTHL groups use real club names with approximate team colours, but never real logos (crests are drawn procedurally
/// from the colours and the abbreviation).
struct TeamInfo {
    static let LEAGUE_PRO = "Pro League"
    static let LEAGUE_CALGARY = "Timbits U7"
    static let LEAGUE_NHL = "NHL"
    static let LEAGUE_GTHL = "GTHL"

    let city: String
    let name: String
    let abbr: String
    let primary: UInt32
    let secondary: UInt32
    let text: UInt32
    let league: String
    /// Road (white) kit for NHL clubs; primary/secondary/text above are the HOME kit. nil = single-kit club.
    let awayKit: TeamKit?

    init(_ city: String, _ name: String, _ abbr: String, _ primary: UInt32, _ secondary: UInt32, _ text: UInt32, _ league: String = TeamInfo.LEAGUE_PRO, awayKit: TeamKit? = nil) {
        self.city = city
        self.name = name
        self.abbr = abbr
        self.primary = primary
        self.secondary = secondary
        self.text = text
        self.league = league
        self.awayKit = awayKit
    }

    /// This club in its road kit (single-kit clubs return themselves).
    func wearingAway() -> TeamInfo {
        guard let k = awayKit else { return self }
        return TeamInfo(city, name, abbr, k.primary, k.secondary, k.text, league, awayKit: awayKit)
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

    private static let FICTIONAL: [TeamInfo] = [
        TeamInfo("Denver", "Peaks", "DNP", HexColor.argb("#1D4ED8"), HexColor.argb("#F8FAFC"), HexColor.white),
        TeamInfo("Chicago", "Blizzard", "CHB", HexColor.argb("#B91C1C"), HexColor.argb("#FBBF24"), HexColor.white),
        TeamInfo("Montreal", "Royals", "MRY", HexColor.argb("#7C3AED"), HexColor.argb("#FDE68A"), HexColor.white),
        TeamInfo("Vancouver", "Orcas", "VAO", HexColor.argb("#0F766E"), HexColor.argb("#E2E8F0"), HexColor.white),
        TeamInfo("Boston", "Harbor", "BOH", HexColor.argb("#111827"), HexColor.argb("#F59E0B"), HexColor.white),
        TeamInfo("Minnesota", "Timber", "MNT", HexColor.argb("#166534"), HexColor.argb("#FCA5A5"), HexColor.white),
        TeamInfo("Dallas", "Longhorns", "DLL", HexColor.argb("#0E7490"), HexColor.argb("#F1F5F9"), HexColor.white),
        TeamInfo("Toronto", "Pilots", "TRP", HexColor.argb("#1E3A8A"), HexColor.argb("#93C5FD"), HexColor.white)
    ]

    /// The 32 NHL clubs (colours approximate; no logos).
    private static let NHL: [TeamInfo] = [
        TeamInfo("Anaheim", "Ducks", "ANA", HexColor.argb("#F47A38"), HexColor.argb("#111111"), HexColor.argb("#111111"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#F47A38"), HexColor.argb("#111111"))),
        TeamInfo("Boston", "Bruins", "BOS", HexColor.argb("#111111"), HexColor.argb("#FFB81C"), HexColor.argb("#FFB81C"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#111111"), HexColor.argb("#111111"))),
        TeamInfo("Buffalo", "Sabres", "BUF", HexColor.argb("#003087"), HexColor.argb("#FFB81C"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#003087"), HexColor.argb("#003087"))),
        TeamInfo("Calgary", "Flames", "CGY", HexColor.argb("#C8102E"), HexColor.argb("#F1BE48"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#C8102E"), HexColor.argb("#C8102E"))),
        TeamInfo("Carolina", "Hurricanes", "CAR", HexColor.argb("#111111"), HexColor.argb("#CC0000"), HexColor.argb("#CC0000"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#CC0000"), HexColor.argb("#CC0000"))),
        TeamInfo("Chicago", "Blackhawks", "CHI", HexColor.argb("#CF0A2C"), HexColor.argb("#111111"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#CF0A2C"), HexColor.argb("#CF0A2C"))),
        TeamInfo("Colorado", "Avalanche", "COL", HexColor.argb("#6F263D"), HexColor.argb("#236192"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#6F263D"), HexColor.argb("#6F263D"))),
        TeamInfo("Columbus", "Blue Jackets", "CBJ", HexColor.argb("#002654"), HexColor.argb("#CE1126"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#002654"), HexColor.argb("#002654"))),
        TeamInfo("Dallas", "Stars", "DAL", HexColor.argb("#006847"), HexColor.argb("#8F8F8C"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#006847"), HexColor.argb("#006847"))),
        TeamInfo("Detroit", "Red Wings", "DET", HexColor.argb("#CE1126"), HexColor.argb("#FFFFFF"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#CE1126"), HexColor.argb("#CE1126"))),
        TeamInfo("Edmonton", "Oilers", "EDM", HexColor.argb("#041E42"), HexColor.argb("#FF4C00"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#041E42"), HexColor.argb("#041E42"))),
        TeamInfo("Florida", "Panthers", "FLA", HexColor.argb("#C8102E"), HexColor.argb("#041E42"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#041E42"), HexColor.argb("#041E42"))),
        TeamInfo("Los Angeles", "Kings", "LAK", HexColor.argb("#111111"), HexColor.argb("#A2AAAD"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#111111"), HexColor.argb("#111111"))),
        TeamInfo("Minnesota", "Wild", "MIN", HexColor.argb("#154734"), HexColor.argb("#DDCBA4"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#154734"), HexColor.argb("#154734"))),
        TeamInfo("Montreal", "Canadiens", "MTL", HexColor.argb("#AF1E2D"), HexColor.argb("#192168"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#AF1E2D"), HexColor.argb("#AF1E2D"))),
        TeamInfo("Nashville", "Predators", "NSH", HexColor.argb("#FFB81C"), HexColor.argb("#041E42"), HexColor.argb("#041E42"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#041E42"), HexColor.argb("#041E42"))),
        TeamInfo("New Jersey", "Devils", "NJD", HexColor.argb("#CE1126"), HexColor.argb("#111111"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#CE1126"), HexColor.argb("#CE1126"))),
        TeamInfo("New York", "Islanders", "NYI", HexColor.argb("#00539B"), HexColor.argb("#F47D30"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#00539B"), HexColor.argb("#00539B"))),
        TeamInfo("New York", "Rangers", "NYR", HexColor.argb("#0038A8"), HexColor.argb("#CE1126"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#0038A8"), HexColor.argb("#0038A8"))),
        TeamInfo("Ottawa", "Senators", "OTT", HexColor.argb("#111111"), HexColor.argb("#C52032"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#C52032"), HexColor.argb("#111111"))),
        TeamInfo("Philadelphia", "Flyers", "PHI", HexColor.argb("#F74902"), HexColor.argb("#111111"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#F74902"), HexColor.argb("#111111"))),
        TeamInfo("Pittsburgh", "Penguins", "PIT", HexColor.argb("#111111"), HexColor.argb("#FCB514"), HexColor.argb("#FCB514"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#111111"), HexColor.argb("#111111"))),
        TeamInfo("San Jose", "Sharks", "SJS", HexColor.argb("#006D75"), HexColor.argb("#111111"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#006D75"), HexColor.argb("#006D75"))),
        TeamInfo("Seattle", "Kraken", "SEA", HexColor.argb("#001628"), HexColor.argb("#99D9D9"), HexColor.argb("#99D9D9"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#001628"), HexColor.argb("#001628"))),
        TeamInfo("St. Louis", "Blues", "STL", HexColor.argb("#002F87"), HexColor.argb("#FCB514"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#002F87"), HexColor.argb("#002F87"))),
        TeamInfo("Tampa Bay", "Lightning", "TBL", HexColor.argb("#002868"), HexColor.argb("#FFFFFF"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#002868"), HexColor.argb("#002868"))),
        TeamInfo("Toronto", "Maple Leafs", "TOR", HexColor.argb("#00205B"), HexColor.argb("#FFFFFF"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#00205B"), HexColor.argb("#00205B"))),
        TeamInfo("Utah", "Mammoth", "UTA", HexColor.argb("#111111"), HexColor.argb("#6CACE4"), HexColor.argb("#6CACE4"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#6CACE4"), HexColor.argb("#111111"))),
        TeamInfo("Vancouver", "Canucks", "VAN", HexColor.argb("#00205B"), HexColor.argb("#00843D"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#00205B"), HexColor.argb("#00205B"))),
        TeamInfo("Vegas", "Golden Knights", "VGK", HexColor.argb("#B4975A"), HexColor.argb("#333F42"), HexColor.argb("#111111"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#B4975A"), HexColor.argb("#111111"))),
        TeamInfo("Washington", "Capitals", "WSH", HexColor.argb("#C8102E"), HexColor.argb("#041E42"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#C8102E"), HexColor.argb("#C8102E"))),
        TeamInfo("Winnipeg", "Jets", "WPG", HexColor.argb("#041E42"), HexColor.argb("#004C97"), HexColor.argb("#FFFFFF"), LEAGUE_NHL,
            awayKit: TeamKit(HexColor.argb("#FFFFFF"), HexColor.argb("#041E42"), HexColor.argb("#041E42")))
    ]

    /// Combined list. Indices are stable (Timbits 0-19, fictional pro 20-27, NHL 28-59) and are what
    /// MatchConfig, the network config and the tournament store, so host and guest always agree.
    /// The GTHL (Greater Toronto Hockey League) competitive clubs: real club names, approximate jersey
    /// colours (sources and confidence in docs/gthl_teams.md), never real logos. Every club also gets a white road kit.
    private static func gthl(_ city: String, _ name: String, _ abbr: String, _ primary: String, _ secondary: String, _ text: String) -> TeamInfo {
        let p = HexColor.argb(primary)
        return TeamInfo(city, name, abbr, p, HexColor.argb(secondary), HexColor.argb(text), LEAGUE_GTHL,
                        awayKit: TeamKit(HexColor.white, p, p))
    }

    private static let GTHL: [TeamInfo] = [
        gthl("Greater Toronto", "Capitals", "GTC", "#1B2A5C", "#C8102E", "#FFFFFF"), // approx
        gthl("Toronto", "Marlboros", "TMB", "#1F4E9E", "#FFFFFF", "#FFFFFF"),
        gthl("Toronto", "Jr. Canadiens", "TJC", "#ED1C22", "#023273", "#FFFFFF"),
        gthl("Toronto", "Young Nationals", "TYN", "#C8102E", "#14213D", "#FFFFFF"), // approx
        gthl("Don Mills", "Flyers", "DMF", "#F26522", "#111111", "#FFFFFF"), // approx
        gthl("Vaughan", "Kings", "VKG", "#111111", "#B0B7BD", "#FFFFFF"), // approx
        gthl("Reps", "Hockey Club", "REP", "#1E3A8A", "#FFC72C", "#FFFFFF"), // approx
        gthl("Markham", "Islanders", "MKI", "#00539B", "#F47D30", "#FFFFFF"), // approx
        gthl("Markham", "Majors", "MKM", "#019034", "#000000", "#FFFFFF"),
        gthl("Mississauga", "Senators", "MSN", "#111111", "#C52032", "#FFFFFF"), // approx
        gthl("Mississauga", "Rebels", "MRB", "#B91C1C", "#111111", "#FFFFFF"), // approx
        gthl("Mississauga", "Jets", "MJT", "#0A2A66", "#5BA9E0", "#FFFFFF"), // approx
        gthl("Mississauga", "North Stars", "MNS", "#006847", "#111111", "#FFFFFF"), // approx
        gthl("Mississauga", "Beast", "MBE", "#111111", "#F59E0B", "#FFFFFF"), // approx
        gthl("Mississauga", "Rattlers", "MRT", "#1A1A1A", "#7CB342", "#FFFFFF"), // approx
        gthl("Mississauga", "Terriers", "MTT", "#7A1F1F", "#E5E7EB", "#FFFFFF"), // approx
        gthl("Leaside", "Flames", "LSF", "#D32F2F", "#111111", "#FFFFFF"), // approx
        gthl("Avenue Road", "Ducks", "ARD", "#0F5C3A", "#F5C518", "#FFFFFF"), // approx
        gthl("Duffield", "Devils", "DUF", "#CE1126", "#111111", "#FFFFFF"), // approx
        gthl("Faustina", "Toros", "FTT", "#7B1E3A", "#F2C94C", "#FFFFFF"), // approx
        gthl("Hillcrest", "Canadiens", "HCC", "#1E2F6B", "#C8102E", "#FFFFFF"), // approx
        gthl("Humber Valley", "Sharks", "HVS", "#00858A", "#111111", "#FFFFFF"), // approx
        gthl("Goulding Park", "Rangers", "GPR", "#1D4ED8", "#DC2626", "#FFFFFF"), // approx
        gthl("North York", "Knights", "NYK", "#1F2A44", "#A8B2BD", "#FFFFFF"), // approx
        gthl("North York", "Rangers", "NYX", "#0038A8", "#CE1126", "#FFFFFF"),
        gthl("Scarborough", "Young Bruins", "SYB", "#111111", "#FFB81C", "#FFFFFF"), // approx
        gthl("Scarborough", "Ice Raiders", "SIR", "#0A3D62", "#7DD3FC", "#FFFFFF"), // approx
        gthl("Toronto", "Red Wings", "TRW", "#CE1126", "#FFFFFF", "#FFFFFF"), // approx
        gthl("Toronto", "Titans", "TTT", "#23395B", "#C0C0C0", "#FFFFFF"), // approx
        gthl("Toronto", "Aeros", "TAE", "#111111", "#C8102E", "#FFFFFF"),
        gthl("Toronto", "Eagles", "TEG", "#00693E", "#F2C94C", "#FFFFFF"), // approx
        gthl("Toronto", "Royals", "TRY", "#5B2C83", "#D4AF37", "#FFFFFF") // approx
    ]

    /// Combined list. Indices are stable (Timbits 0-19, fictional pro 20-27, NHL 28-59, GTHL 60-91; new groups are only ever appended) and are what
    /// MatchConfig, the network config and the tournament store, so host and guest always agree.
    static let ALL: [TeamInfo] = CALGARY + FICTIONAL + NHL + GTHL

    /// The leagues offered in the pickers, in toggle order.
    static let LEAGUES: [String] = [LEAGUE_CALGARY, LEAGUE_NHL, LEAGUE_GTHL]

    /// Normalize league string; defaults to Timbits.
    static func normalizeLeague(_ league: String) -> String {
        (league == LEAGUE_NHL || league == LEAGUE_GTHL) ? league : LEAGUE_CALGARY
    }

    /// Indices into ALL of the clubs in the league (anything but NHL means Timbits).
    static func indicesFor(_ league: String) -> [Int] {
        let l = normalizeLeague(league)
        return ALL.indices.filter { ALL[$0].league == l }
    }

    // ---------------------------------------------------------------- kits

    private static func red(_ c: UInt32) -> Float { Float((c >> 16) & 0xFF) }
    private static func green(_ c: UInt32) -> Float { Float((c >> 8) & 0xFF) }
    private static func blue(_ c: UInt32) -> Float { Float(c & 0xFF) }

    /// Weighted RGB distance ("redmean"), 0 = identical, about 765 = black vs white.
    static func colourDistance(_ a: UInt32, _ b: UInt32) -> Float {
        let rm = (red(a) + red(b)) / 2
        let dr = red(a) - red(b)
        let dg = green(a) - green(b)
        let db = blue(a) - blue(b)
        return (((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) as Float).squareRoot()
    }

    /// Primaries closer than this are treated as the same jersey colour.
    static let CLASH_DISTANCE: Float = 120

    private static func luma(_ c: UInt32) -> Float { (0.299 * red(c) + 0.587 * green(c) + 0.114 * blue(c)) / 255 }

    /// Alternate (road) kit for `t` against an opposing primary `other`: tries swapping primary and secondary,
    /// then white, then the lighter of its two colours, and takes the first that is clear of `other`
    /// (else the most distant). Pure function of the colours, so host and guest agree.
    private static func alternateKit(_ t: TeamInfo, other: UInt32) -> TeamInfo {
        let white = HexColor.argb("#F5F5F5")
        let lighter = luma(t.primary) >= luma(t.secondary) ? t.primary : t.secondary
        let prim: [UInt32] = [t.secondary, white, lighter]
        var best = 0
        var bestD: Float = -1
        for i in 0..<prim.count {
            let d = colourDistance(prim[i], other)
            if d >= CLASH_DISTANCE { best = i; bestD = d; break }
            if d > bestD { bestD = d; best = i }
        }
        let np = prim[best]
        let ns: UInt32 = colourDistance(np, t.primary) > 60 ? t.primary : (luma(np) > 0.5 ? HexColor.argb("#111111") : white)
        let text: UInt32 = luma(np) > 0.6 ? HexColor.argb("#111111") : HexColor.white
        return TeamInfo(t.city, t.name, t.abbr, np, ns, text, t.league, awayKit: t.awayKit)
    }

    private static func clash(_ x: TeamInfo, _ y: TeamInfo) -> Bool {
        colourDistance(x.primary, y.primary) < CLASH_DISTANCE
    }

    /// Builds the two clubs of a match. NHL clubs wear their real kits: the home club its HOME kit (the
    /// colour jersey) and the away club its AWAY kit (white). If the two jerseys still clash, fall back in
    /// order to: the away club in its home kit, the home club in its road kit against that, both swapped,
    /// and finally a generic alternate kit for the away club. Pure function of the two indices, so host and
    /// guest agree. (The Android build also keeps a club's Customise Team colours; the Customise screen is
    /// not ported to iOS yet.)
    static func matchTeams(_ homeIdx: Int, _ awayIdx: Int) -> (home: TeamInfo, away: TeamInfo) {
        let homeStock = byIndex(homeIdx)
        let awayStock = byIndex(awayIdx)
        var home = homeStock
        var away = awayStock.wearingAway()
        if clash(home, away) {
            let homeAlt = homeStock.wearingAway()
            if !clash(home, awayStock) {
                away = awayStock
            } else if !clash(homeAlt, away) {
                home = homeAlt
            } else if !clash(homeAlt, awayStock) {
                home = homeAlt
                away = awayStock
            } else {
                away = alternateKit(away, other: home.primary)
            }
        }
        return (home, away)
    }

    static func byIndex(_ i: Int) -> TeamInfo {
        ALL[min(max(i, 0), ALL.count - 1)]
    }

    /// Index of the default home club (Glenlake Hawks).
    static let DEFAULT_HOME: Int = max(ALL.firstIndex { $0.city == "Glenlake" } ?? 0, 0)
    static let DEFAULT_AWAY: Int = max(ALL.firstIndex { $0.city == "Bow Valley" } ?? 1, 1)

    /// Picker label: club name plus its league.
    static func label(_ t: TeamInfo) -> String { t.fullName + "  ·  " + t.league }
}
