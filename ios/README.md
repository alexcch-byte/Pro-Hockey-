# iOS port — status

Phase 1 of the Android → iOS port: the platform-agnostic game engine, translated from
Kotlin (`app/src/main/java/com/tablehockey/game/`) to Swift with no UIKit/SpriteKit/Core
Graphics dependency. This is a Swift package, not an app yet — there is nothing to run
on a device or simulator until the renderer, input, audio, UI and networking layers
described in phase 2+ are built on top of it.

## What's here

`PowerPlayHockeyCore/` — a Swift package (`Package.swift`, iOS 15+ / macOS 12+) containing:

- `Model/` — `TeamInfo` (28-club roster), `MatchConfig`, `Prefs` (UserDefaults-backed
  audio settings). Ports of `model/`.
- `Game/` — `Rink` (geometry + board containment), `Skater`, `Puck`, `Team`, `World`
  (`Phase`, `GameEvent`, `PlayerInput`, `AiSettings`), `Camera` (world↔screen math only —
  no drawing), `PhysicsEngine` (movement, collisions, puck glide/boards/posts/nets,
  goalie capsule), `AIController` (skater/goalie decision-making), `Simulation`
  (authoritative match flow: faceoffs, whistles, periods, OT, shooting, passing, checks,
  saves, scoring). Ports of `game/Rink.kt` through `game/Simulation.kt`, i.e. everything
  except `Renderer`, `TouchControls`, `GameView`, `SoundManager`/`MusicManager`.

Each Swift file is a close, mostly line-for-line port of its Kotlin counterpart, with a
few idiomatic adjustments:

- Kotlin `object` singletons (`Rink`, `PhysicsEngine`) → Swift `enum` namespaces with
  static members.
- `FloatArray` scratch buffers for mutated positions/normals (`pos`, `normal` in
  `Rink.containCircle`, `PhysicsEngine.goalieContact`) → `inout Float` parameters.
- `kotlin.random.Random` (shared by reference between `Simulation` and `AIController`)
  → `SeededRandom`, a small reference-type SplitMix64 PRNG (`Game/SeededRandom.swift`).
  Not bit-identical to Kotlin's generator — doesn't need to be, since nothing here
  depends on cross-platform determinism.
- `android.graphics.Color` ints → `UInt32` ARGB packed the same way, via
  `HexColor.argb("#RRGGBB")` (`Game/HexColor.swift`), so `TeamInfo` colours carry
  straight over. The eventual Renderer converts these to `CGColor`/`UIColor`.
- `Prefs` drops the Android `Context` argument — `UserDefaults.standard` is process-wide
  on iOS, so it isn't needed.

## Known limitation: no Mac in this environment

This code was written and reviewed by reading, not compiled or type-checked — this
session has no Xcode/macOS toolchain available. Expect small Swift-compiler fixups on
first build (mainly literal/ternary type-inference spots in the arithmetic-heavy files
like `PhysicsEngine.swift` and `Simulation.swift`), not logic rewrites. Treat the first
`swift build` on a Mac as the real verification step; nothing here should be assumed
correct until that passes.

## What's deliberately not here yet

- **Renderer** — `Renderer.kt` (828 lines) draws every skater/goalie/rink/particle by
  hand via `android.graphics.Canvas`; there are no sprite assets to reuse. This is the
  single largest remaining piece and needs a rendering-API decision (Core Graphics vs.
  SpriteKit vs. Metal) before porting.
- **Input / game loop** — `TouchControls.kt`, `GameView.kt` → UIKit touch handling +
  `CADisplayLink`.
- **Audio** — `SoundManager.kt` / `MusicManager.kt` → `AVAudioEngine` / `AVAudioPlayer`.
  The underlying WAV assets from `tools/make_sounds.py` are reusable as-is.
- **UI screens** — `MainActivity`, `MatchSettingsActivity`, `HowToPlayActivity`,
  `WifiLobbyActivity`, `GameActivity` → SwiftUI views.
- **Networking** — WiFi (`GameServer`/`GameClient`, TCP + NSD) ports to
  `Network.framework` + Bonjour. **Bluetooth (`BluetoothLink.kt`) does not port**: it's
  classic RFCOMM, which iOS has no public API for. An iPad could never pair with this
  Fire tablet over Bluetooth even after a full rewrite; WiFi multiplayer is the only
  transport that can survive the port as-is.

See the root [`CLAUDE.md`](../CLAUDE.md) for the Android codebase map these files were
ported from.
