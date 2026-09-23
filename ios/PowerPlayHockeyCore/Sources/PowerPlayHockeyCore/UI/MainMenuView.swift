import Foundation
#if canImport(SwiftUI)
import SwiftUI

/// Top-level arcade main menu for Power Play Hockey.
public struct MainMenuView: View {
    @State private var activeScreen: Screen? = nil
    @State private var activeMatchConfig: MatchConfig? = nil
    @State private var showingHowToPlay = false

    private enum Screen {
        case quickMatch
        case shootout
        case tournament
    }

    public init() {}

    public var body: some View {
        ZStack {
            Color(red: 11/255.0, green: 20/255.0, blue: 36/255.0)
                .ignoresSafeArea()

            if let config = activeMatchConfig {
                GameContainerView(
                    config: config,
                    onDismiss: {
                        activeMatchConfig = nil
                        MusicManager.shared.menuStarted()
                    }
                )
            } else if activeScreen == .quickMatch {
                MatchSettingsView(
                    defaultMode: .singlePlayer,
                    onStartGame: { config in
                        activeScreen = nil
                        activeMatchConfig = config
                    },
                    onBack: { activeScreen = nil }
                )
            } else if activeScreen == .shootout {
                MatchSettingsView(
                    defaultMode: .shootout,
                    onStartGame: { config in
                        activeScreen = nil
                        activeMatchConfig = config
                    },
                    onBack: { activeScreen = nil }
                )
            } else if activeScreen == .tournament {
                TournamentView(
                    onPlayMatch: { config in
                        activeScreen = nil
                        activeMatchConfig = config
                    },
                    onBack: { activeScreen = nil }
                )
            } else {
                // Main Menu Home
                VStack(spacing: 24) {
                    Spacer()

                    // Logo & Subtitle
                    VStack(spacing: 8) {
                        Text("POWER PLAY")
                            .font(.system(size: 40, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                            .tracking(2)

                        Text("HOCKEY")
                            .font(.system(size: 32, weight: .black, design: .rounded))
                            .foregroundColor(Color(red: 56/255.0, green: 189/255.0, blue: 248/255.0))
                            .tracking(6)

                        Text("PRO ARCADE ACTION • 60/120 FPS")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.gray)
                            .tracking(2)
                            .padding(.top, 4)
                    }

                    Spacer()

                    // Navigation buttons
                    VStack(spacing: 14) {
                        menuButton(title: "QUICK MATCH", subtitle: "Full 3-on-3 rink action", color: .blue) {
                            activeScreen = .quickMatch
                        }

                        menuButton(title: "SHOOTOUT SHOWDOWN", subtitle: "5-Round breakaway shootout", color: Color(red: 14/255.0, green: 165/255.0, blue: 233/255.0)) {
                            activeScreen = .shootout
                        }

                        menuButton(title: "PLAYOFF TOURNAMENT", subtitle: "8-Team Stanley Cup bracket", color: Color(red: 245/255.0, green: 158/255.0, blue: 11/255.0)) {
                            activeScreen = .tournament
                        }

                        menuButton(title: "HOW TO PLAY", subtitle: "Controls & Pro Moves", color: Color.white.opacity(0.12), isSecondary: true) {
                            showingHowToPlay = true
                        }
                    }
                    .frame(maxWidth: 340)

                    Spacer()

                    Text("v2.0 • iOS & iPadOS Pro Motion Edition")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.gray.opacity(0.6))
                        .padding(.bottom, 12)
                }
                .padding(.horizontal, 24)
            }
        }
        .onAppear {
            MusicManager.shared.menuStarted()
        }
        .sheet(isPresented: $showingHowToPlay) {
            HowToPlaySheet(onDismiss: { showingHowToPlay = false })
        }
    }

    private func menuButton(title: String, subtitle: String, color: Color, isSecondary: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: {
            SoundManager.shared.playClick()
            action()
        }) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Text(subtitle)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(isSecondary ? .gray : .white.opacity(0.8))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(isSecondary ? .gray : .white.opacity(0.8))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(color)
            .cornerRadius(14)
            .shadow(color: isSecondary ? .clear : color.opacity(0.3), radius: 6, x: 0, y: 3)
        }
    }
}

private struct HowToPlaySheet: View {
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color(red: 15/255.0, green: 23/255.0, blue: 42/255.0)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                HStack {
                    Text("HOW TO PLAY")
                        .font(.system(size: 24, weight: .black, design: .rounded))
                        .foregroundColor(.white)

                    Spacer()

                    Button("DONE") {
                        SoundManager.shared.playClick()
                        onDismiss()
                    }
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.blue)
                }
                .padding(.top, 24)
                .padding(.horizontal, 24)

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        guideSection(title: "OFFENSE CONTROLS", icon: "hockey.puck", items: [
                            ("Virtual Joystick", "Left side of screen: skate and aim shots/passes"),
                            ("SHOOT (Hold & Release)", "Charge up a rocket wrist shot or clapper"),
                            ("PASS", "Tap to pass to open teammate in stick direction"),
                            ("RAPID FLICK DEKE", "Flick joystick sharply to dodge checks and freeze goalie"),
                            ("ONE-TIMER", "Tap or hold SHOOT as a pass arrives for a 115+ mph blast")
                        ])

                        guideSection(title: "DEFENSE & GOALIE SAVES", icon: "shield.fill", items: [
                            ("POKE CHECK (Pass button)", "Poke stick forward to disrupt puck carrier"),
                            ("BODY CHECK (Hit button)", "Lunge forward to flatten the puck carrier"),
                            ("BUTTERFLY DROP (Goalie)", "Drop down to seal five-hole along ice"),
                            ("TWO-PAD STACK (Goalie)", "Dive sideways with pads stacked to rob breakaways"),
                            ("SWITCH DEFENDER", "Tap PASS without puck to switch to closest skater")
                        ])

                        guideSection(title: "GAME MODES & ARENAS", icon: "star.fill", items: [
                            ("Outdoor Winter Pond", "Alpine forest, falling snow, teal lake ice & breath vapor"),
                            ("Shootout Showdown", "5 rounds of 1-on-1 breakaways with active shot clock"),
                            ("On Fire Momentum", "String 3 good plays together for 25s speed and shot surge"),
                            ("Shattered Glass", "Crush opponents into the boards at high speed to explode plexiglass")
                        ])
                    }
                    .padding(24)
                }
            }
        }
    }

    private func guideSection(title: String, icon: String, items: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundColor(.blue)
                Text(title)
                    .font(.system(size: 16, weight: .black))
                    .foregroundColor(.white)
            }

            ForEach(items, id: \.0) { item in
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.0)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(red: 56/255.0, green: 189/255.0, blue: 248/255.0))

                    Text(item.1)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.gray)
                }
                .padding(.vertical, 2)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.06))
        .cornerRadius(14)
    }
}
#endif
