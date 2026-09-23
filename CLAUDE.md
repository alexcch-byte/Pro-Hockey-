# Power Play Hockey

NHL-style 5-on-5 arcade ice hockey for Amazon Fire tablets (Fire OS = Android, no Google Play
Services). Kotlin, single Android module, landscape only. Package id is still
`com.tablehockey.game` from the original table-hockey prototype; the app label is "Power Play Hockey".

- minSdk 26, targetSdk/compileSdk 34, Kotlin 1.9, AGP 8.3.2, Gradle 8.13 (wrapper checked in).
- The project folder lives on Google Drive (`G:\My Drive\...`); file sizes can report 0 for a moment after writes.

## Build, install, test

No `java` or `gradle` on PATH. Use Android Studio's bundled JDK:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

adb: `C:/Users/strid/AppData/Local/Android/Sdk/platform-tools/adb.exe`

- Test device: Fire HD 8 (KFRAWI, Fire OS 8 / API 30, 1280x800). It has two user profiles, and the
  APK is only launchable when installed with an explicit user:
  `adb -s GCC23103239204X9 install -r --user 0 app-debug.apk` then
  `adb shell am start --user 0 -n com.tablehockey.game/.MainActivity`.
- Emulator: hand-written AVD `FireTablet` (1920x1200, android-37 x86_64) under `~/.android/avd`;
  there is no `avdmanager` on this machine. Start with `emulator -avd FireTablet -no-snapshot`.
  It has no Bluetooth, and non-exported activities can't be started from the shell there.
- The game logs `PowerPlay: fps=..` every 5 s; grep logcat for `PowerPlay|FATAL|AndroidRuntime`.
  Screenshots: `adb exec-out screencap -p > file.png`.
- If the user is mid-match on the tablet (focus on GameActivity in `dumpsys window`), don't reinstall.

## Code map (`app/src/main/java/com/tablehockey/game`)

- `MainActivity`, `MatchSettingsActivity` (teams, period length, difficulty, audio sliders),
  `HowToPlayActivity` + `ControlsDiagramView`, `WifiLobbyActivity` (WiFi and Bluetooth lobby),
  `GameActivity` (immersive, pause/match-over dialogs), `AudioSliders` (shared slider binding).
- `model/`: `TeamInfo` (club list: 20 Calgary-area Timbits U7 clubs first, then 8 fictional pro clubs;
  colours are approximations), `MatchConfig` (Serializable intent extra), `Prefs` (sfx/music volume 0-100).
- `game/`: the engine.
  - `Rink` geometry constants and board containment. Units are feet, origin at centre ice,
    +x along the rink, +y down the screen. Each team has `attackDir` (+1/-1); AI logic works in the
    team's "attack frame" (`Team.toAttackX`).
  - Entities: `World` (teams, puck, clock, phase, banner, controlled skater per team, events),
    `Team`, `Skater` (C/LW/RW/LD/RD/G, timers for stun/poke/check/swing), `Puck`.
  - `Simulation`: authoritative match flow (faceoffs, whistles, periods, sudden-death OT),
    possession, shooting (aim assist by difficulty), passing, poke/body checks, goalie saves
    (cover vs rebound, difficulty leak), scoring. `AiSettings` in `World.kt` holds all difficulty numbers.
  - `AIController`: skater formations/chasing/carrier decisions and goalie positioning.
  - `PhysicsEngine`: skater movement/collisions, puck glide, boards, posts, nets. Goals use a swept
    test (must cross the goal line from the front between the posts); carried pucks are kept out of nets.
  - `Renderer` + `Camera`: hardware-canvas drawing in world units (rink, crowd bitmap, detailed
    skaters/goalies, snow spray particles, puck) then HUD/controls in screen space. Avoid
    allocations in draw paths; paints and paths are reused.
  - `TouchControls`: floating joystick (left half) + SHOOT / PASS / HIT buttons. Without the puck,
    SHOOT = poke check and PASS = switch to nearest skater; SHOOT charges while held.
  - `GameView`: game thread, host vs client update paths, camera follow, sound/music hooks.
  - `SoundManager` (SoundPool effects, jingles, organ, skate scrapes) and `MusicManager`
    (MediaPlayer loops: menu theme and in-game theme, ducking under jingles, user volume).
- `network/`: `Links.kt` (`HostLink` / `GuestLink` interfaces), `NetCodec` (line-delimited JSON:
  `cfg`, `in`, `st`), `GameServer`/`GameClient` (TCP port 8943 + NSD, 30 Hz),
  `BluetoothLink.kt` (RFCOMM host/guest/scanner, 20 Hz), `NetworkSession` (hand-off singleton).
  The host simulates; the guest only mirrors snapshots and sends input. `GameMode.WIFI_HOST/WIFI_CLIENT`
  are used for both transports.

## Audio

Every file in `app/src/main/res/raw` is synthesized by `python tools/make_sounds.py` (numpy + scipy).
Music is original NES-style chiptune written in that script (no copyrighted game music). Re-run the
script after editing a sound, then rebuild. No ffmpeg here, so music ships as 22 kHz WAV.

- Impacts and skates render at 44.1 kHz (modal synthesis); music, horn, crowd and ambience at 22.05 kHz.
  Crowd sounds are formant-synthesised voices plus synthetic arena reverb; loops are built to be seamless.
- `save(..., level=)` pins a sound's loudness (loudest 100 ms RMS after a 250 Hz high-pass, i.e. what a
  tablet speaker can play), so redesigning a sound doesn't upset `SoundManager`'s mix.
  `SOUND_OUT=<dir> python tools/make_sounds.py` renders somewhere else for auditioning.
- `SoundManager` rotates takes (`puck_hit`, `_2`, `_3`, ...), pans puck sounds by screen position, scales
  shots/passes/boards by puck speed, adds `tail_bright`/`tail_dark` reverb indoors, and drives two crowd
  loops (`crowd_loop` murmur + `crowd_roar`) from an excitement level updated in `updateMix()` every frame.
  The Winter Pond arena plays `wind_loop` instead and skips the organ.
- The iOS port keeps its own copies of the audio under `ios/.../Resources`; they are not regenerated.

## Conventions and gotchas

- Keep gameplay changes in `Simulation`/`AIController`/`PhysicsEngine`; the renderer must stay
  read-only with respect to world state (it runs under `synchronized(world)` with the sim).
- `GameEvent` values are sent by ordinal over the network; append new ones at the end.
- Player art is scaled by `Renderer.BODY_SCALE` / `GOALIE_SCALE`; the stick blade is drawn at
  `stickReach` so it lines up with the carried puck.
- The app theme is forced dark (`Theme.MaterialComponents.NoActionBar`) so dialogs stay readable.
- Deleting files via shell was blocked in this environment; obsolete files were overwritten instead.
  `game/MatchResult.kt` is unused and can be removed.

## Status

- Verified on the Fire HD 8: single player, all menus, audio, 55-60 fps.
- Not verified: WiFi and Bluetooth matches between two real devices (only one tablet available).
