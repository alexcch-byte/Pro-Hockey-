# iOS Port — Power Play Hockey

The Android → iOS port of Power Play Hockey, translated from Kotlin (`app/src/main/java/com/tablehockey/game/`) to Swift 5.9.

Packaged as a multiplatform Swift Package (`Package.swift`, targeting iOS 15+ and macOS 12+), fully synchronized with Android `main` commit `f2b5130`.

## Architecture & Structure

```
ios/PowerPlayHockeyCore/
├── Package.swift
└── Sources/PowerPlayHockeyCore/
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
    │   └── TouchControls+UIKit.swift (UITouch adapter for UIViews)
    ├── Render/
    │   ├── GraphicsCompat.swift     (CoreGraphics GPaint, GPath shim mirroring Android Canvas)
    │   ├── GCanvas.swift            (Hardware-accelerated CGContext draw calls)
    │   ├── Renderer.swift           (Winter Pond teal rink, alpine background, snow, breath, shattered glass)
    │   ├── Renderer+Players.swift   (Sprite overhaul: breezers, TUUK holders, 4-roll gloves, visors, stick flex, pad stack sprawl)
    │   └── Renderer+HUD.swift       (Shootout dot indicators, shot clock, goalie buttons, power play badges)
    ├── App/
    │   ├── GameView.swift           (CADisplayLink 60/120 Hz game loop driving Simulation & Renderer)
    │   ├── SoundManager.swift       (AVFoundation 27-effect sound board with volume control)
    │   └── MusicManager.swift       (AVAudioPlayer looping music engine with ducking)
    └── UI/
        ├── MainMenuView.swift       (Top-level arcade menu: Quick Match, Shootout, Playoffs, How to Play)
        ├── MatchSettingsView.swift  (Club selection, Arena toggle, Period length, Difficulty, Audio sliders)
        ├── TournamentView.swift     (8-team single elimination Stanley Cup playoff bracket)
        └── GameContainerView.swift  (SwiftUI UIViewRepresentable wrapper with Pause & Game Over overlays)
```

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
| **CADisplayLink 60/120 Hz Game Loop** | SurfaceView / Ch議er | CADisplayLink |
| **AVFoundation Audio & Music Engine** | SoundPool / MediaPlayer | AVAudioPlayer / AVFoundation |
| **SwiftUI Menus & Navigation Shell** | Jetpack Activity / Views | SwiftUI 3+ Views |

## Building & Running

Open `ios/PowerPlayHockeyCore/` in Xcode or add it as a Swift Package dependency to any iOS project targeting iOS 15.0 or later.
The root UI view is `MainMenuView()`.
