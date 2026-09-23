import Foundation

public let HOST_PORT: UInt16 = 8943
public let SERVICE_TYPE = "_powerplayhockey._tcp"
public let RELAY_BASE_URL = "https://powerplay-hockey-relay.example.workers.dev"

/// JSON line-delimited protocol codec matching the Android NetCodec:
///   host -> client  "cfg"  match setup (teams, period length)
///   client -> host  "in"   joystick + button state for the away team
///   host -> client  "st"   full authoritative snapshot, ~30 Hz
public enum NetCodec {

    private static let F_STUN = 1
    private static let F_POKE = 2
    private static let F_CHECK = 4
    private static let F_BUTTERFLY = 8
    private static let F_SWING = 16

    private static let allPhases: [Phase] = [.faceoff, .play, .whistle, .goal, .periodEnd, .gameOver]
    private static let allEvents: [GameEvent] = [
        .shot, .pass, .boards, .post, .goal, .hit, .poke, .save, .whistle, .horn,
        .faceoffDrop, .pickup, .periodEnd, .gameOver, .faceoffSet, .oneTimer,
        .penalty, .onFire, .deke, .glassShatter, .goalieSaveMove
    ]

    public static func configJson(home: Int, away: Int, periodLength: Int) -> String {
        let dict: [String: Any] = [
            "t": "cfg",
            "home": home,
            "away": away,
            "pl": periodLength
        ]
        return serialize(dict)
    }

    public static func inputJson(_ i: PlayerInput) -> String {
        let dict: [String: Any] = [
            "t": "in",
            "mx": Double(i.moveX),
            "my": Double(i.moveY),
            "sh": i.shootHeld,
            "sr": i.shootRelease,
            "sc": Double(i.shootCharge),
            "ps": i.pass,
            "ht": i.hit,
            "dk": i.deke
        ]
        return serialize(dict)
    }

    public static func applyInput(_ obj: [String: Any], into dst: PlayerInput) {
        dst.moveX = Float(obj["mx"] as? Double ?? 0.0)
        dst.moveY = Float(obj["my"] as? Double ?? 0.0)
        dst.shootHeld = obj["sh"] as? Bool ?? false
        if obj["sr"] as? Bool ?? false {
            dst.shootRelease = true
            dst.shootCharge = Float(obj["sc"] as? Double ?? 0.0)
        } else if dst.shootHeld {
            dst.shootCharge = Float(obj["sc"] as? Double ?? 0.0)
        }
        if obj["ps"] as? Bool ?? false { dst.pass = true }
        if obj["ht"] as? Bool ?? false { dst.hit = true }
        if obj["dk"] as? Bool ?? false { dst.deke = true }
    }

    public static func stateJson(_ w: World) -> String {
        var o: [String: Any] = [:]
        o["t"] = "st"
        o["ph"] = phaseIndex(w.phase)
        o["p"] = [Double(w.puck.x), Double(w.puck.y), Double(w.puck.vx), Double(w.puck.vy)]
        o["car"] = w.codeOf(w.puck.carrier)

        var sk: [[Any]] = []
        for s in w.allSkaters {
            var flags = 0
            if s.stunTimer > 0 { flags |= F_STUN }
            if s.pokeTimer > 0 { flags |= F_POKE }
            if s.checkTimer > 0 { flags |= F_CHECK }
            if s.butterfly { flags |= F_BUTTERFLY }
            if s.swingTimer > 0 { flags |= F_SWING }
            sk.append([
                (Double(s.x) * 100.0).rounded() / 100.0,
                (Double(s.y) * 100.0).rounded() / 100.0,
                (Double(s.facing) * 1000.0).rounded() / 1000.0,
                flags,
                (Double(s.stride) * 10.0).rounded() / 10.0
            ])
        }
        o["sk"] = sk
        o["c0"] = w.controlled[0]
        o["c1"] = w.controlled[1]
        o["s0"] = w.teams[0].score
        o["s1"] = w.teams[1].score
        o["h0"] = w.teams[0].shots
        o["h1"] = w.teams[1].shots
        o["per"] = w.period
        o["clk"] = Double(w.clock)
        o["ot"] = w.overtime
        o["ad"] = Double(w.teams[0].attackDir)
        o["fx"] = Double(w.faceoffX)
        o["fy"] = Double(w.faceoffY)
        if let ban = w.banner { o["ban"] = ban }
        if let bs = w.bannerSub { o["bs"] = bs }
        o["bt"] = Double(w.bannerTimer)
        o["ch"] = Double(w.shotCharge[1])
        o["pt"] = w.penaltyTeam
        o["ptm"] = Double(w.penaltyTimer)
        o["gp0"] = w.goaliePulled[0]
        o["gp1"] = w.goaliePulled[1]

        if !w.events.isEmpty {
            o["ev"] = w.events.compactMap { eventIndex($0) }
        }

        return serialize(o)
    }

    public static func applyState(_ obj: [String: Any], into w: World, events: inout [GameEvent]) {
        if let phIdx = obj["ph"] as? Int, phIdx >= 0 && phIdx < allPhases.count {
            w.phase = allPhases[phIdx]
        }

        if let p = obj["p"] as? [Double], p.count >= 4 {
            w.puck.netX = Float(p[0])
            w.puck.netY = Float(p[1])
            w.puck.vx = Float(p[2])
            w.puck.vy = Float(p[3])
        }

        if let car = obj["car"] as? Int {
            w.puck.carrier = w.skaterByCode(car)
        }

        if let sk = obj["sk"] as? [[Any]] {
            let all = w.allSkaters
            for i in 0..<min(sk.count, all.count) {
                let a = sk[i]
                guard a.count >= 4 else { continue }
                let s = all[i]
                s.netX = Float(a[0] as? Double ?? Double(a[0] as? Int ?? 0))
                s.netY = Float(a[1] as? Double ?? Double(a[1] as? Int ?? 0))
                s.netFacing = Float(a[2] as? Double ?? Double(a[2] as? Int ?? 0))
                let flags = a[3] as? Int ?? 0
                s.stunTimer = (flags & F_STUN != 0) ? 1.0 : 0.0
                s.pokeTimer = (flags & F_POKE != 0) ? 1.0 : 0.0
                s.checkTimer = (flags & F_CHECK != 0) ? 1.0 : 0.0
                s.butterfly = (flags & F_BUTTERFLY != 0)
                if flags & F_SWING != 0 {
                    if s.swingTimer <= 0 { s.swingTimer = 0.35 }
                } else {
                    s.swingTimer = 0
                }
                if a.count > 4 {
                    s.stride = Float(a[4] as? Double ?? Double(a[4] as? Int ?? 0))
                }
            }
        }

        w.controlled[0] = obj["c0"] as? Int ?? -1
        w.controlled[1] = obj["c1"] as? Int ?? -1
        w.teams[0].score = obj["s0"] as? Int ?? 0
        w.teams[1].score = obj["s1"] as? Int ?? 0
        w.teams[0].shots = obj["h0"] as? Int ?? 0
        w.teams[1].shots = obj["h1"] as? Int ?? 0
        w.period = obj["per"] as? Int ?? 1
        w.clock = Float(obj["clk"] as? Double ?? 0.0)
        w.overtime = obj["ot"] as? Bool ?? false

        let ad = Float(obj["ad"] as? Double ?? 1.0)
        w.teams[0].attackDir = ad
        w.teams[1].attackDir = -ad
        w.faceoffX = Float(obj["fx"] as? Double ?? 0.0)
        w.faceoffY = Float(obj["fy"] as? Double ?? 0.0)

        w.banner = obj["ban"] as? String
        w.bannerSub = obj["bs"] as? String
        w.bannerTimer = Float(obj["bt"] as? Double ?? 0.0)
        w.shotCharge[1] = Float(obj["ch"] as? Double ?? 0.0)
        w.penaltyTeam = obj["pt"] as? Int ?? -1
        w.penaltyTimer = Float(obj["ptm"] as? Double ?? 0.0)
        w.goaliePulled[0] = obj["gp0"] as? Bool ?? false
        w.goaliePulled[1] = obj["gp1"] as? Bool ?? false

        if let ev = obj["ev"] as? [Int] {
            for idx in ev {
                if idx >= 0 && idx < allEvents.count {
                    events.append(allEvents[idx])
                }
            }
        }
    }

    private static func phaseIndex(_ ph: Phase) -> Int {
        allPhases.firstIndex(of: ph) ?? 0
    }

    private static func eventIndex(_ ev: GameEvent) -> Int? {
        allEvents.firstIndex(of: ev)
    }

    public static func serialize(_ dict: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: dict, options: []),
              let str = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return str
    }

    public static func parse(_ string: String) -> [String: Any]? {
        guard let data = string.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data, options: []),
              let dict = json as? [String: Any] else {
            return nil
        }
        return dict
    }
}
