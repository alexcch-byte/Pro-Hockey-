import Foundation
#if canImport(SwiftUI)
import SwiftUI

/// 8-Team single-elimination playoff bracket tournament view with persistent state.
public struct TournamentView: View {
    public var onPlayMatch: (MatchConfig) -> Void
    public var onBack: () -> Void

    @State private var state: TournamentState?
    @State private var selectedTeamIdx: Int = TeamInfo.DEFAULT_HOME
    @State private var showingNewDialog = false

    public init(onPlayMatch: @escaping (MatchConfig) -> Void, onBack: @escaping () -> Void) {
        self.onPlayMatch = onPlayMatch
        self.onBack = onBack
    }

    public var body: some View {
        ZStack {
            Color(red: 11/255.0, green: 20/255.0, blue: 36/255.0)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack {
                    Button(action: {
                        SoundManager.shared.playClick()
                        onBack()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("MENU")
                        }
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.12))
                        .cornerRadius(8)
                    }

                    Spacer()

                    Text("PLAYOFF TOURNAMENT")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: {
                        SoundManager.shared.playClick()
                        resetTournament()
                    }) {
                        Text("RESET")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.orange)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(Color.white.opacity(0.12))
                            .cornerRadius(8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                if let state = state {
                    ScrollView {
                        VStack(spacing: 24) {
                            // User team header banner
                            let userTeam = TeamInfo.byIndex(state.userTeam)
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(teamColor(userTeam.primary))
                                    .frame(width: 40, height: 40)
                                    .overlay(
                                        Text(userTeam.abbr)
                                            .font(.system(size: 14, weight: .black))
                                            .foregroundColor(.white)
                                    )

                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(userTeam.city.uppercased()) \(userTeam.name.uppercased())")
                                        .font(.system(size: 16, weight: .black, design: .rounded))
                                        .foregroundColor(.white)

                                    Text(statusSubtitle(state: state))
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(state.trophyWon ? .yellow : (state.userEliminated ? .red : .green))
                                }

                                Spacer()

                                if state.trophyWon {
                                    Text("🏆 CHAMPION")
                                        .font(.system(size: 13, weight: .black))
                                        .foregroundColor(.yellow)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.yellow.opacity(0.15))
                                        .cornerRadius(8)
                                }
                            }
                            .padding(16)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(14)

                            // Bracket rounds
                            bracketRound(title: "QUARTERFINALS", matches: state.qf, roundIndex: 0)
                            if !state.sf.isEmpty {
                                bracketRound(title: "SEMIFINALS", matches: state.sf, roundIndex: 1)
                            }
                            if let finals = state.finals {
                                bracketRound(title: "STANLEY CUP FINALS", matches: [finals], roundIndex: 2)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 32)
                    }
                } else {
                    // New tournament team selection
                    VStack(spacing: 24) {
                        Spacer()

                        Text("SELECT YOUR PLAYOFF CLUB")
                            .font(.system(size: 18, weight: .black, design: .rounded))
                            .foregroundColor(.white)

                        let team = TeamInfo.byIndex(selectedTeamIdx)
                        VStack(spacing: 10) {
                            Circle()
                                .fill(teamColor(team.primary))
                                .frame(width: 64, height: 64)
                                .overlay(
                                    Text(team.abbr)
                                        .font(.system(size: 22, weight: .black))
                                        .foregroundColor(.white)
                                    )

                            Text(team.fullName)
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(.white)

                            Text(team.league.uppercased())
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.gray)

                            HStack(spacing: 32) {
                                Button(action: {
                                    SoundManager.shared.playClick()
                                    selectedTeamIdx = (selectedTeamIdx - 1 + TeamInfo.ALL.count) % TeamInfo.ALL.count
                                }) {
                                    Image(systemName: "chevron.left.circle.fill")
                                        .font(.system(size: 32))
                                        .foregroundColor(.white.opacity(0.8))
                                }

                                Button(action: {
                                    SoundManager.shared.playClick()
                                    selectedTeamIdx = (selectedTeamIdx + 1) % TeamInfo.ALL.count
                                }) {
                                    Image(systemName: "chevron.right.circle.fill")
                                        .font(.system(size: 32))
                                        .foregroundColor(.white.opacity(0.8))
                                }
                            }
                            .padding(.top, 8)
                        }
                        .padding(24)
                        .frame(maxWidth: 320)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(18)

                        Button(action: {
                            SoundManager.shared.playClick()
                            startNewTournament(userTeamIdx: selectedTeamIdx)
                        }) {
                            Text("ENTER PLAYOFFS")
                                .font(.system(size: 18, weight: .black))
                                .foregroundColor(.white)
                                .frame(width: 240, height: 50)
                                .background(Color.blue)
                                .cornerRadius(12)
                        }

                        Spacer()
                    }
                }
            }
        }
        .onAppear {
            loadOrCreate()
        }
    }

    private func loadOrCreate() {
        if let saved = Prefs.getTournament() {
            self.state = saved
        }
    }

    private func startNewTournament(userTeamIdx: Int) {
        var rng = SeededRandom(seed: UInt64(Date().timeIntervalSince1970))
        let newState = TournamentState.createNew(userTeamIdx: userTeamIdx, rng: &rng)
        Prefs.saveTournament(newState)
        self.state = newState
    }

    private func resetTournament() {
        Prefs.clearTournament()
        self.state = nil
    }

    private func teamColor(_ color: UInt32) -> Color {
        Color(
            red: Double(HexColor.red(color)) / 255.0,
            green: Double(HexColor.green(color)) / 255.0,
            blue: Double(HexColor.blue(color)) / 255.0
        )
    }

    private func statusSubtitle(state: TournamentState) -> String {
        if state.trophyWon { return "Stanley Cup Champions!" }
        if state.userEliminated { return "Eliminated from Playoffs" }
        switch state.currentRound {
        case 0: return "Round 1: Quarterfinals"
        case 1: return "Round 2: Conference Semifinals"
        case 2: return "Championship: Stanley Cup Finals"
        default: return "Playoffs Complete"
        }
    }

    private func bracketRound(title: String, matches: [TournamentMatch], roundIndex: Int) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.gray)

            ForEach(0..<matches.count, id: \.self) { idx in
                matchCard(match: matches[idx], roundIndex: roundIndex, matchIndex: idx)
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.06))
        .cornerRadius(14)
    }

    private func matchCard(match: TournamentMatch, roundIndex: Int, matchIndex: Int) -> some View {
        guard let state = state else { return AnyView(EmptyView()) }
        let t1 = TeamInfo.byIndex(match.team1)
        let t2 = TeamInfo.byIndex(match.team2)
        let isUserMatch = (match.team1 == state.userTeam || match.team2 == state.userTeam)
        let canPlay = isUserMatch && !match.isPlayed && !state.userEliminated && state.currentRound == roundIndex

        return AnyView(
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    teamRow(team: t1, score: match.score1, isWinner: match.winner == match.team1)
                    teamRow(team: t2, score: match.score2, isWinner: match.winner == match.team2)
                }

                Spacer()

                if canPlay {
                    Button(action: {
                        SoundManager.shared.playClick()
                        let opp = match.team1 == state.userTeam ? match.team2 : match.team1
                        let config = MatchConfig(
                            mode: .singlePlayer,
                            homeTeam: state.userTeam,
                            awayTeam: opp,
                            periodLengthSeconds: 120,
                            aiDifficulty: .medium,
                            soundEnabled: Prefs.soundEnabled(),
                            musicEnabled: Prefs.musicEnabled(),
                            arenaType: Prefs.arenaType()
                        )
                        onPlayMatch(config)
                    }) {
                        Text("PLAY")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.blue)
                            .cornerRadius(8)
                    }
                } else if match.isPlayed {
                    Text("FINAL")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.gray)
                }
            }
            .padding(12)
            .background(Color.white.opacity(isUserMatch ? 0.1 : 0.04))
            .cornerRadius(10)
        )
    }

    private func teamRow(team: TeamInfo, score: Int, isWinner: Bool) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(teamColor(team.primary))
                .frame(width: 20, height: 20)
                .overlay(Text(team.abbr.prefix(1)).font(.system(size: 10, weight: .bold)).foregroundColor(.white))

            Text(team.fullName)
                .font(.system(size: 14, weight: isWinner ? .black : .regular))
                .foregroundColor(isWinner ? .white : .gray)

            Spacer()

            if score >= 0 {
                Text("\(score)")
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundColor(isWinner ? .white : .gray)
            }
        }
    }
}
#endif
