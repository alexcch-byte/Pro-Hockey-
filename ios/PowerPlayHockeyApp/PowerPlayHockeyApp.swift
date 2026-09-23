import SwiftUI
#if canImport(PowerPlayHockeyCore)
import PowerPlayHockeyCore
#endif

/// Main application entry point for Power Play Hockey on iOS.
/// Initializes audio, authenticates Apple Game Center, and hosts the top-level MainMenuView.
@main
struct PowerPlayHockeyApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        #if canImport(PowerPlayHockeyCore)
        // Authenticate with Apple Game Center on app launch
        GameCenterManager.shared.authenticate()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            #if canImport(PowerPlayHockeyCore)
            MainMenuView()
                .statusBar(hidden: true)
                .onAppear {
                    MusicManager.shared.menuStarted()
                }
            #else
            Text("Power Play Hockey Core required")
            #endif
        }
        .onChange(of: scenePhase) { newPhase in
            #if canImport(PowerPlayHockeyCore)
            switch newPhase {
            case .active:
                MusicManager.shared.resume()
            case .inactive, .background:
                MusicManager.shared.pause()
            @unknown default:
                break
            }
            #endif
        }
    }
}
