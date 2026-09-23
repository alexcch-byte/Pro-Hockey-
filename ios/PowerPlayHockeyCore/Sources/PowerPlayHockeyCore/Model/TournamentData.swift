import Foundation

struct TournamentMatch: Codable, Equatable {
    let team1: Int
    let team2: Int
    var score1: Int = -1
    var score2: Int = -1
    var winner: Int = -1

    var isPlayed: Bool { winner != -1 }

    init(team1: Int, team2: Int, score1: Int = -1, score2: Int = -1, winner: Int = -1) {
        self.team1 = team1
        self.team2 = team2
        self.score1 = score1
        self.score2 = score2
        self.winner = winner
    }

    enum CodingKeys: String, CodingKey {
        case team1 = "t1"
        case team2 = "t2"
        case score1 = "s1"
        case score2 = "s2"
        case winner = "w"
    }
}

final class TournamentState: Codable {
    let userTeam: Int
    let league: String
    var currentRound: Int // 0 = QF, 1 = SF, 2 = Final, 3 = Complete
    var qf: [TournamentMatch]
    var sf: [TournamentMatch]
    var finals: TournamentMatch?
    var userEliminated: Bool
    var trophyWon: Bool

    init(
        userTeam: Int,
        league: String,
        currentRound: Int = 0,
        qf: [TournamentMatch] = [],
        sf: [TournamentMatch] = [],
        finals: TournamentMatch? = nil,
        userEliminated: Bool = false,
        trophyWon: Bool = false
    ) {
        self.userTeam = userTeam
        self.league = league
        self.currentRound = currentRound
        self.qf = qf
        self.sf = sf
        self.finals = finals
        self.userEliminated = userEliminated
        self.trophyWon = trophyWon
    }

    static func createNew(userTeamIdx: Int, rng: inout SeededRandom) -> TournamentState {
        let userTeam = TeamInfo.byIndex(userTeamIdx)
        let league = userTeam.league
        var allInLeague = TeamInfo.ALL.indices.filter { TeamInfo.byIndex($0).league == league && $0 != userTeamIdx }
        
        // Fisher-Yates shuffle with SeededRandom
        for i in (1..<allInLeague.count).reversed() {
            let j = Int(rng.nextFloat() * Float(i + 1))
            if i != j { allInLeague.swapAt(i, j) }
        }
        let opponents = Array(allInLeague.prefix(7))

        let state = TournamentState(userTeam: userTeamIdx, league: league)
        if opponents.count >= 7 {
            state.qf.append(TournamentMatch(team1: userTeamIdx, team2: opponents[0]))
            state.qf.append(TournamentMatch(team1: opponents[1], team2: opponents[2]))
            state.qf.append(TournamentMatch(team1: opponents[3], team2: opponents[4]))
            state.qf.append(TournamentMatch(team1: opponents[5], team2: opponents[6]))
        }
        return state
    }

    static func simulateAiMatch(match: inout TournamentMatch, rng: inout SeededRandom) {
        var s1 = Int(rng.nextFloat() * 4) + 1
        var s2 = Int(rng.nextFloat() * 4) + 1
        if s1 == s2 {
            if rng.nextBool() { s1 += 1 } else { s2 += 1 }
        }
        match.score1 = s1
        match.score2 = s2
        match.winner = s1 > s2 ? match.team1 : match.team2
    }
}
