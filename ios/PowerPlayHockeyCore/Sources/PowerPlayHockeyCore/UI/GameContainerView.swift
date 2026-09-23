import Foundation
#if canImport(SwiftUI) && canImport(UIKit)
import SwiftUI
import UIKit

/// SwiftUI wrapper for GameView with pause and game over overlays.
public struct GameContainerView: View {
    public let config: MatchConfig
    public var onDismiss: () -> Void

    @State private var isPaused = false
    @State private var isGameOver = false
    @State private var gameOverWinner = 0
    @State private var finalScore0 = 0
    @State private var finalScore1 = 0
    @State private var gameID = UUID()

    public init(config: MatchConfig, onDismiss: @escaping () -> Void) {
        self.config = config
        self.onDismiss = onDismiss
    }

    public var body: some View {
        ZStack {
            GameViewRepresentable(
                config: config,
                isPaused: isPaused,
                onPause: { isPaused = true },
                onGameOver: { winner, s0, s1 in
                    gameOverWinner = winner
                    finalScore0 = s0
                    finalScore1 = s1
                    isGameOver = true
                }
            )
            .id(gameID)
            .ignoresSafeArea()

            // Pause Overlay
            if isPaused && !isGameOver {
                Color.black.opacity(0.7)
                    .ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("GAME PAUSED")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.white)

                    Button(action: {
                        SoundManager.shared.playClick()
                        isPaused = false
                    }) {
                        Text("RESUME")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 220, height: 48)
                            .background(Color.blue)
                            .cornerRadius(12)
                    }

                    Button(action: {
                        SoundManager.shared.playClick()
                        isPaused = false
                        gameID = UUID()
                    }) {
                        Text("RESTART MATCH")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 220, height: 48)
                            .background(Color.orange)
                            .cornerRadius(12)
                    }

                    Button(action: {
                        SoundManager.shared.playClick()
                        onDismiss()
                    }) {
                        Text("EXIT TO MENU")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 220, height: 48)
                            .background(Color.red.opacity(0.85))
                            .cornerRadius(12)
                    }
                }
                .padding(32)
                .background(Color(red: 15/255.0, green: 23/255.0, blue: 42/255.0).opacity(0.95))
                .cornerRadius(20)
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.2), lineWidth: 1.5))
            }

            // Game Over Overlay
            if isGameOver {
                Color.black.opacity(0.75)
                    .ignoresSafeArea()

                VStack(spacing: 18) {
                    let team0 = TeamInfo.byIndex(config.homeTeam)
                    let team1 = TeamInfo.byIndex(config.awayTeam)
                    let winner = gameOverWinner == 0 ? team0 : team1
                    let won = gameOverWinner == 0

                    Text(won ? "VICTORY!" : "GAME OVER")
                        .font(.system(size: 32, weight: .black, design: .rounded))
                        .foregroundColor(won ? .yellow : .white)

                    Text("\(winner.fullName.uppercased()) WIN")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.gray)

                    Text("\(team0.abbr) \(finalScore0) - \(finalScore1) \(team1.abbr)")
                        .font(.system(size: 26, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                        .padding(.vertical, 8)

                    Button(action: {
                        SoundManager.shared.playClick()
                        isGameOver = false
                        gameID = UUID()
                    }) {
                        Text("PLAY AGAIN")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 220, height: 48)
                            .background(Color.green)
                            .cornerRadius(12)
                    }

                    Button(action: {
                        SoundManager.shared.playClick()
                        onDismiss()
                    }) {
                        Text("MAIN MENU")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 220, height: 48)
                            .background(Color.blue)
                            .cornerRadius(12)
                    }
                }
                .padding(32)
                .background(Color(red: 15/255.0, green: 23/255.0, blue: 42/255.0).opacity(0.96))
                .cornerRadius(20)
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.2), lineWidth: 1.5))
            }
        }
    }
}

private struct GameViewRepresentable: UIViewRepresentable {
    let config: MatchConfig
    let isPaused: Bool
    let onPause: () -> Void
    let onGameOver: (Int, Int, Int) -> Void

    func makeUIView(context: Context) -> GameView {
        let view = GameView(frame: .zero, config: config)
        view.onPause = onPause
        view.onGameOver = onGameOver
        return view
    }

    func updateUIView(_ uiView: GameView, context: Context) {
        uiView.setPaused(isPaused)
    }
}
#endif
