# iOS port — status

The Android → iOS port, translated from Kotlin (`app/src/main/java/com/tablehockey/game/`)
to Swift. This is a Swift package, not an app yet — there is nothing to run on a device
or simulator until the game loop (a `CADisplayLink`-driven view), audio, UI screens and
networking described below are built on top of it.

- **Phase 1** — the platform-agnostic engine core (`Game/`): world state, physics, AI,
  match flow. No UIKit/Core Graphics dependency at all.
- **Phase 2** — the renderer (`Render/`) and touch input (`Input/`): everything needed
  to draw a frame and turn raw touches into game commands, still with no app shell
  wrapping them yet.

## What's here

`PowerPlayHockeyCore/` — a Swift package (`Package.swift`, iOS 15+ / macOS 12+) containing:

- `Model/` — `TeamInfo` (28-club roster), `MatchConfig`, `Prefs` (UserDefaults-backed
  audio settings). Ports of `model/`.
- `Game/` — `Rink` (geometry + board containment), `Skater`, `Puck`, `Team`, `World`
  (`Phase`, `GameEvent`, `PlayerInput`, `AiSettings`), `Camera` (world↔screen math only —
  no drawing), `PhysicsEngine` (movement, collisions, puck glide/boards/posts/nets,
  goalie capsule), `AIController` (skater/goalie decision-making), `Simulation`
  (authoritative match flow: faceoffs, whistles, periods, OT, shooting, passing, checks,
  saves, scoring). Ports of `game/Rink.kt` through `game/Simulation.kt`.
- `Render/` — `GraphicsCompat.swift`/`GCanvas.swift` (a `Paint`/`Path`/`Canvas`-shaped
  shim over Core Graphics, described below), `Renderer` (+`Renderer+Players`,
  `Renderer+HUD`) — a close port of `Renderer.kt`'s ~300 draw calls — and `CameraApply`
  (the `Canvas`-dependent half of `Camera.apply()` Phase 1 deliberately left out).
  `TouchControlsState` is the read-only contract `Renderer` draws the on-screen controls
  from, satisfied by `Input/TouchControls`.
- `Input/` — `TouchControls`, a port of `TouchControls.kt`: the joystick + SHOOT/PASS/HIT
  button logic, driven by pointer down/move/up calls keyed by `AnyHashable` (Android's
  integer pointer IDs) rather than any UIKit type, so the file itself has no UIKit
  dependency. `TouchControls+UIKit.swift` is the thin adapter that feeds it from a
  `UIView`'s real `touchesBegan`/`Moved`/`Ended`/`Cancelled`, using
  `ObjectIdentifier(touch)` as that same pointer identity.

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

This code was written and reviewed by reading, not compiled or type-checked — no session
that has worked on this port so far has had an Xcode/macOS toolchain available. Expect
small Swift-compiler fixups on first build (mainly literal/ternary type-inference spots
in the arithmetic-heavy files like `PhysicsEngine.swift` and `Simulation.swift`), not
logic rewrites. Treat the first `swift build` on a Mac as the real verification step;
nothing here should be assumed correct until that passes. Two spots in `Renderer.swift`
carry above-average risk and are flagged in its own header comment: `drawArc`'s angle
convention and the crowd bitmap's coordinate flip — check those first if the rendered
frame looks wrong. `Input/TouchControls.swift` has one spot flagged the same way, in
`pointerUp` — written as explicit `if/else` specifically to avoid a Swift pattern-match
question (switching a non-optional value against optional `case` bindings) that couldn't
be checked without a compiler.

## What's deliberately not here yet

- **Game loop / app shell** — `GameView.kt` → a `UIView` subclass driving `Simulation` +
  `Renderer` off a `CADisplayLink`, plus the `.xcodeproj`/App target this package needs
  to actually run: none of this repo's sessions have had Xcode to create one. Once that
  exists, wiring it to `Render/` and `Input/` is mostly mechanical — both were written
  with exactly this integration in mind (see their own doc comments).
- **Audio** — `SoundManager.kt` / `MusicManager.kt` → `AVAudioEngine` / `AVAudioPlayer`.
  The underlying WAV assets from `tools/make_sounds.py` are reusable as-is.
- **UI screens** — `MainActivity`, `MatchSettingsActivity`, `HowToPlayActivity`,
  `WifiLobbyActivity`, `GameActivity` → SwiftUI views.
- **Networking** — WiFi (`GameServer`/`GameClient`, TCP + NSD) ports to
  `Network.framework` + Bonjour. **Bluetooth (`BluetoothLink.kt`) does not port**: it's
  classic RFCOMM, which iOS has no public API for. An iPad could never pair with this
  Fire tablet over Bluetooth even after a full rewrite; WiFi multiplayer is the only
  transport that can survive the port as-is. The newer internet relay transport
  (`RelayLink.kt`, see the root README) is plain WebSocket + JSON and ports cleanly too,
  once WiFi's `Network.framework` work exists to model it on.

See the root [`CLAUDE.md`](../CLAUDE.md) for the Android codebase map these files were
ported from.
