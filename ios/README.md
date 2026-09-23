# iOS Port — Power Play Hockey

The Android → iOS port of Power Play Hockey, translated from Kotlin (`app/src/main/java/com/tablehockey/game/`) to Swift 5.9.

Packaged as a multiplatform Swift Package (`Package.swift`, targeting iOS 15+ and macOS 12+) with an accompanying standalone SwiftUI App target (`PowerPlayHockeyApp`), fully synchronized with Android `main`.

---

## Architecture & Structure

```
ios/
├── PowerPlayHockeyApp/
│   ├── PowerPlayHockeyApp.swift     (@main SwiftUI app entry point, lifecycle & GameCenter init)
│   ├── Info.plist                   (120 Hz ProMotion, landscape lock, Bonjour services, Game Controller keys)
│   ├── LaunchScreen.storyboard      (Native launch screen)
│   └── Assets.xcassets/             (App icons & hockey ice accent color set)
│
└── PowerPlayHockeyCore/
    ├── Package.swift                (Swift Package manifest with bundled audio resources)
    └── Sources/PowerPlayHockeyCore/
        ├── Resources/               (30 audio files: sound effects, crowd loops, and chiptune music tracks)
        ├── Model/
        │   ├── MatchConfig.swift        (ArenaType: Indoor/Winter Pond, GameMode: Quick/Shootout/WiFi)
        │   ├── Prefs.swift              (UserDefaults persistence: SFX, Music, Arena, Tournament)
        │   ├── TeamInfo.swift           (28-club roster with primary/secondary/text hex colors)
        │   └── TournamentData.swift     (8-team playoff bracket, AI simulation, Codable)
        ├── Game/
        │   ├── World.swift              (Momentum, On Fire, Shootout state, Shattered Glass, Penalties)
        │   ├── Skater.swift             (GoalieAction: Butterfly/Pad Stack, Deke timers, Breath)
        │   ├── Puck.swift               (Glide physics, supersonic trails)
        │   ├── Rink.swift               (Geometry & board containment)
        │   ├── PhysicsEngine.swift      (Kinematics, collision resolution, goalie capsule)
        │   ├── AIController.swift       (Positional logic, aggressive puck pursuit, goalie squaring)
        │   ├── Simulation.swift         (Match flow, Shootout breakaway logic, Deke evasion, Penalties)
        │   └── SeededRandom.swift       (SplitMix64 deterministic PRNG)
        ├── Input/
        │   ├── TouchControls.swift      (Virtual joystick, buttons, rapid flick/juke Deke detection)
        │   ├── TouchControls+UIKit.swift (UITouch adapter for UIViews)
        │   └── GameControllerManager.swift (GameController.framework: Xbox, DualSense, MFi gamepads)
        ├── Network/
        │   ├── Links.swift              (HostLink and GuestLink abstract transport interfaces)
        │   ├── NetCodec.swift           (Authoritative JSON line protocol matching Android 1-to-1)
        │   ├── NetworkSession.swift     (Global session coordinator for active multiplayer matches)
        │   ├── NWLocalLink.swift        (Apple Network.framework: Bonjour _powerplayhockey._tcp on port 8943)
        │   └── RelayLink.swift          (WebSocket internet multiplayer client using Cloudflare relay)
        ├── Render/
        │   ├── GraphicsCompat.swift     (CoreGraphics GPaint, GPath shim mirroring Android Canvas)
        │   ├── GCanvas.swift            (Hardware-accelerated CGContext draw calls)
        │   ├── Renderer.swift           (Winter Pond teal rink, alpine background, snow, breath, shattered glass)
        │   ├── Renderer+Players.swift   (Sprite overhaul: breezers, TUUK holders, 4-roll gloves, visors, stick flex, pad stack sprawl)
        │   └── Renderer+HUD.swift       (Shootout dot indicators, shot clock, goalie buttons, power play badges)
        ├── App/
        │   ├── GameView.swift           (CADisplayLink 60/120 Hz game loop driving Simulation & Renderer)
        │   ├── SoundManager.swift       (AVFoundation 27-effect sound board with volume control)
        │   ├── MusicManager.swift       (AVAudioPlayer looping music engine with ducking)
        │   ├── HapticManager.swift      (UIImpactFeedbackGenerator & UINotificationFeedbackGenerator)
        │   └── GameCenterManager.swift  (Apple GameKit leaderboards & achievement reporting)
        └── UI/
            ├── MainMenuView.swift       (Top-level arcade menu: Quick Match, Shootout, Playoffs, How to Play)
            ├── MatchSettingsView.swift  (Club selection, Arena toggle, Period length, Difficulty, Audio sliders)
            ├── TournamentView.swift     (8-team single elimination Stanley Cup playoff bracket)
            └── GameContainerView.swift  (SwiftUI UIViewRepresentable wrapper with Pause & Game Over overlays)
```

---

## Feature Parity Matrix with Android `main`

| Feature | Android (`Kotlin`) | iOS (`Swift`) |
| :--- | :---: | :---: |
| **Shootout Showdown** | ✅ | ✅ |
| **Playoff Tournament Bracket** | ✅ | ✅ |
| **Outdoor Winter Pond Arena** | ✅ | ✅ |
| **Snowfall Weather & Breath Vapor** | ✅ | ✅ |
| **Shattered Plexiglass on Hits** | ✅ | ✅ |
| **Rapid-Stick Toe-Drag / Deke** | ✅ | ✅ |
| **"On Fire" Momentum System** | ✅ | ✅ |
| **Active Goalie Saves (Butterfly / Pad Stack)** | ✅ | ✅ |
| **Sprite Overhaul (Pants, TUUK, 4-Roll, Visor)** | ✅ | ✅ |
| **Composite Stick Flex on Wind-up** | ✅ | ✅ |
| **Knurled Textured Puck with Comet Glow** | ✅ | ✅ |
| **CADisplayLink 60/120 Hz Game Loop** | SurfaceView / Choreographer | CADisplayLink (ProMotion 120Hz) |
| **Audio & Looping Music Engine** | SoundPool / MediaPlayer | AVAudioPlayer / AVFoundation |
| **Haptic Feedback Engine** | Android Vibrator / HapticFeedback | UIImpactFeedbackGenerator / UINotificationFeedbackGenerator |
| **Physical Gamepads (Xbox / PS5 / MFi)** | Generic Gamepad Event | GameController.framework (`GCController`) |
| **Multiplayer Networking** | NSD / TCP & OkHttp WebSocket | Apple `Network.framework` (Bonjour) & `URLSessionWebSocketTask` |
| **Online Social Platform** | Google Play Games | Apple Game Center (`GameKit`) |
| **Arcade Menus & UI** | Jetpack Compose / XML | SwiftUI 3+ declarative views |

---

## Building & Running

1. Open `ios/PowerPlayHockeyCore/` directly in Xcode, or open a project containing both `PowerPlayHockeyApp` and `PowerPlayHockeyCore`.
2. Target iOS 15.0 or later on any iPhone or iPad.
3. For physical gamepad testing, connect any MFi, Xbox Wireless, or PlayStation DualSense controller via Bluetooth.
