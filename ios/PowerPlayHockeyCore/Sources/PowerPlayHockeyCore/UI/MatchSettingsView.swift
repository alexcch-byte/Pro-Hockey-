import Foundation
#if canImport(SwiftUI)
import SwiftUI

/// Settings and match configuration screen for Quick Match and Shootout modes.
public struct MatchSettingsView: View {
    public var defaultMode: GameMode
    public var onStartGame: (MatchConfig) -> Void
    public var onBack: () -> Void

    @State private var selectedMode: GameMode
    @State private var homeTeamIndex: Int = TeamInfo.DEFAULT_HOME
    @State private var awayTeamIndex: Int = TeamInfo.DEFAULT_AWAY
    @State private var periodLength: Int = 120
    @State private var difficulty: AiDifficulty = .medium
    @State private var arena: ArenaType = .indoor
    @State private var sfxVol: Double = Double(Prefs.sfxVolume())
    @State private var musicVol: Double = Double(Prefs.musicVolume())

    public init(
        defaultMode: GameMode = .singlePlayer,
        onStartGame: @escaping (MatchConfig) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.defaultMode = defaultMode
        self._selectedMode = State(initialValue: defaultMode)
        self.onStartGame = onStartGame
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
                            Text("BACK")
                        }
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.white.opacity(0.12))
                        .cornerRadius(8)
                    }

                    Spacer()

                    Text(selectedMode == .shootout ? "SHOOTOUT SETUP" : "MATCH SETUP")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Spacer()

                    Color.clear.frame(width: 80, height: 32)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                ScrollView {
                    VStack(spacing: 20) {
                        // Team Selectors
                        HStack(spacing: 16) {
                            teamCard(title: "YOUR TEAM", teamIndex: $homeTeamIndex)
                            teamCard(title: "OPPONENT", teamIndex: $awayTeamIndex)
                        }

                        // Arena Selection
                        VStack(alignment: .leading, spacing: 10) {
                            Text("ARENA & ENVIRONMENT")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color.gray)

                            HStack(spacing: 12) {
                                arenaButton(type: .indoor, label: "INDOOR ARENA", icon: "building.2.fill")
                                arenaButton(type: .winterPond, label: "WINTER POND", icon: "snowflake")
                            }
                        }
                        .padding(16)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(14)

                        // Difficulty & Period Length
                        HStack(spacing: 16) {
                            if selectedMode != .shootout {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text("PERIOD LENGTH")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(Color.gray)

                                    HStack(spacing: 8) {
                                        periodButton(seconds: 60, label: "1 MIN")
                                        periodButton(seconds: 120, label: "2 MIN")
                                        periodButton(seconds: 180, label: "3 MIN")
                                    }
                                }
                                .padding(16)
                                .background(Color.white.opacity(0.06))
                                .cornerRadius(14)
                            }

                            VStack(alignment: .leading, spacing: 10) {
                                Text("AI DIFFICULTY")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color.gray)

                                HStack(spacing: 8) {
                                    diffButton(diff: .easy, label: "EASY")
                                    diffButton(diff: .medium, label: "MED")
                                    diffButton(diff: .hard, label: "HARD")
                                }
                            }
                            .padding(16)
                            .background(Color.white.opacity(0.06))
                            .cornerRadius(14)
                        }

                        // Audio Settings
                        VStack(alignment: .leading, spacing: 12) {
                            Text("AUDIO SETTINGS")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color.gray)

                            HStack {
                                Image(systemName: "speaker.wave.3.fill")
                                    .foregroundColor(.white)
                                    .frame(width: 24)
                                Text("SFX Volume")
                                    .foregroundColor(.white)
                                Slider(value: $sfxVol, in: 0...100, step: 1) { _ in
                                    Prefs.setSfxVolume(Int(sfxVol))
                                }
                                Text("\(Int(sfxVol))%")
                                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                                    .foregroundColor(.gray)
                                    .frame(width: 44)
                            }

                            HStack {
                                Image(systemName: "music.note")
                                    .foregroundColor(.white)
                                    .frame(width: 24)
                                Text("Music Volume")
                                    .foregroundColor(.white)
                                Slider(value: $musicVol, in: 0...100, step: 1) { _ in
                                    MusicManager.shared.userVolume = Float(musicVol) / 100.0
                                }
                                Text("\(Int(musicVol))%")
                                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                                    .foregroundColor(.gray)
                                    .frame(width: 44)
                            }
                        }
                        .padding(16)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(14)

                        // Start Button
                        Button(action: {
                            SoundManager.shared.playClick()
                            Prefs.setArenaType(arena)
                            let config = MatchConfig(
                                mode: selectedMode,
                                homeTeam: homeTeamIndex,
                                awayTeam: awayTeamIndex,
                                periodLengthSeconds: periodLength,
                                aiDifficulty: difficulty,
                                soundEnabled: sfxVol > 0,
                                musicEnabled: musicVol > 0,
                                arenaType: arena
                            )
                            onStartGame(config)
                        }) {
                            Text(selectedMode == .shootout ? "START SHOOTOUT" : "DROP THE PUCK")
                                .font(.system(size: 20, weight: .black, design: .rounded))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 54)
                                .background(LinearGradient(colors: [Color.blue, Color.cyan], startPoint: .leading, endPoint: .trailing))
                                .cornerRadius(14)
                                .shadow(color: Color.blue.opacity(0.4), radius: 8, x: 0, y: 4)
                        }
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    private func teamCard(title: String, teamIndex: Binding<Int>) -> some View {
        let team = TeamInfo.byIndex(teamIndex.wrappedValue)
        let primaryColor = Color(
            red: Double(HexColor.red(team.primary)) / 255.0,
            green: Double(HexColor.green(team.primary)) / 255.0,
            blue: Double(HexColor.blue(team.primary)) / 255.0
        )

        return VStack(spacing: 8) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.gray)

            Circle()
                .fill(primaryColor)
                .frame(width: 48, height: 48)
                .overlay(
                    Text(team.abbr)
                        .font(.system(size: 16, weight: .black))
                        .foregroundColor(.white)
                )

            Text(team.name)
                .font(.system(size: 16, weight: .black, design: .rounded))
                .foregroundColor(.white)

            Text(team.city)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.gray)

            HStack {
                Button(action: {
                    SoundManager.shared.playClick()
                    teamIndex.wrappedValue = (teamIndex.wrappedValue - 1 + TeamInfo.ALL.count) % TeamInfo.ALL.count
                }) {
                    Image(systemName: "chevron.left.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(.white.opacity(0.7))
                }

                Spacer()

                Button(action: {
                    SoundManager.shared.playClick()
                    teamIndex.wrappedValue = (teamIndex.wrappedValue + 1) % TeamInfo.ALL.count
                }) {
                    Image(systemName: "chevron.right.circle.fill")
                        .font(.system(size: 22))
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 4)
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .background(Color.white.opacity(0.06))
        .cornerRadius(14)
    }

    private func arenaButton(type: ArenaType, label: String, icon: String) -> some View {
        let isSelected = arena == type
        return Button(action: {
            SoundManager.shared.playClick()
            arena = type
        }) {
            HStack {
                Image(systemName: icon)
                Text(label)
                    .font(.system(size: 13, weight: .bold))
            }
            .foregroundColor(isSelected ? .white : .gray)
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .background(isSelected ? Color.blue : Color.white.opacity(0.08))
            .cornerRadius(10)
        }
    }

    private func periodButton(seconds: Int, label: String) -> some View {
        let isSelected = periodLength == seconds
        return Button(action: {
            SoundManager.shared.playClick()
            periodLength = seconds
        }) {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(isSelected ? .white : .gray)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(isSelected ? Color.blue : Color.white.opacity(0.08))
                .cornerRadius(8)
        }
    }

    private func diffButton(diff: AiDifficulty, label: String) -> some View {
        let isSelected = difficulty == diff
        return Button(action: {
            SoundManager.shared.playClick()
            difficulty = diff
        }) {
            Text(label)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(isSelected ? .white : .gray)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(isSelected ? Color.blue : Color.white.opacity(0.08))
                .cornerRadius(8)
        }
    }
}
#endif
