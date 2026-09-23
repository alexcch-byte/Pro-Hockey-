import Foundation
#if canImport(GameKit)
import GameKit
#endif
#if canImport(UIKit)
import UIKit
#endif

/// Leaderboard identifiers for Apple Game Center.
public enum LeaderboardID {
    public static let shootoutStreak = "com.powerplayhockey.shootout_streak"
    public static let cupChampionships = "com.powerplayhockey.championships"
    public static let careerGoals = "com.powerplayhockey.career_goals"
}

/// Achievement identifiers for Apple Game Center.
public enum AchievementID {
    public static let firstGoal = "com.powerplayhockey.first_goal"
    public static let hatTrick = "com.powerplayhockey.hat_trick"
    public static let shutout = "com.powerplayhockey.shutout"
    public static let glassShatter = "com.powerplayhockey.glass_shatter"
    public static let onFireSurge = "com.powerplayhockey.on_fire"
    public static let cupChampion = "com.powerplayhockey.cup_champion"
    public static let shootoutSniper = "com.powerplayhockey.shootout_sniper"
}

/// Apple Game Center integration handling player authentication, leaderboards,
/// and achievement tracking.
public final class GameCenterManager {
    public static let shared = GameCenterManager()

    public private(set) var isAuthenticated: Bool = false
    public private(set) var localPlayerAlias: String = "Player"

    private init() {}

    /// Authenticate local player with Game Center.
    /// - Parameter presentingViewController: UIKit view controller used if login prompt is required.
    public func authenticate(from presentingViewController: Any? = nil) {
        #if canImport(GameKit)
        let player = GKLocalPlayer.local
        player.authenticateHandler = { [weak self] viewController, error in
            if let vc = viewController {
                #if canImport(UIKit)
                if let presenter = presentingViewController as? UIViewController {
                    presenter.present(vc, animated: true)
                } else if let root = UIApplication.shared.windows.first?.rootViewController {
                    root.present(vc, animated: true)
                }
                #endif
            } else if player.isAuthenticated {
                self?.isAuthenticated = true
                self?.localPlayerAlias = player.alias
                print("GameCenter authenticated as: \(player.alias)")
            } else {
                self?.isAuthenticated = false
                if let err = error {
                    print("GameCenter authentication failed: \(err.localizedDescription)")
                }
            }
        }
        #endif
    }

    /// Report score to an active Game Center leaderboard.
    public func submitScore(_ score: Int, leaderboardID: String) {
        #if canImport(GameKit)
        guard isAuthenticated else { return }
        if #available(iOS 14.0, macOS 11.0, *) {
            GKLeaderboard.submitScore(score, context: 0, player: GKLocalPlayer.local, leaderboardIDs: [leaderboardID]) { error in
                if let error = error {
                    print("GameCenter submitScore error: \(error)")
                }
            }
        }
        #endif
    }

    /// Report achievement progress (0.0 to 100.0 percent).
    public func reportAchievement(id: String, percentComplete: Double = 100.0, showBanner: Bool = true) {
        #if canImport(GameKit)
        guard isAuthenticated else { return }
        let achievement = GKAchievement(identifier: id)
        achievement.percentComplete = min(max(percentComplete, 0.0), 100.0)
        achievement.showsCompletionBanner = showBanner
        GKAchievement.report([achievement]) { error in
            if let error = error {
                print("GameCenter reportAchievement error: \(error)")
            }
        }
        #endif
    }

    /// Show Game Center dashboard overlay.
    #if canImport(UIKit) && canImport(GameKit)
    public func showDashboard(from viewController: UIViewController) {
        guard isAuthenticated else { return }
        let gcVC = GKGameCenterViewController(state: .default)
        gcVC.gameCenterDelegate = GameCenterDismissDelegate.shared
        viewController.present(gcVC, animated: true)
    }
    #endif
}

#if canImport(UIKit) && canImport(GameKit)
private final class GameCenterDismissDelegate: NSObject, GKGameCenterControllerDelegate {
    static let shared = GameCenterDismissDelegate()
    func gameCenterViewControllerDidFinish(_ gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismiss(animated: true)
    }
}
#endif
