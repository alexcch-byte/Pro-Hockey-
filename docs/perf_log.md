# Performance log

## Perf Cycle 1 - Builder

### Baseline: NOT collected (blocked)
- The Fire HD 8 (GCC23103239204X9) went to sleep (screen timeout 60 s) while I was starting the app and
  is now behind a PIN lock screen (`deviceLocked=1`). I cannot enter a PIN, so no on-device fps, update/lock/record/post
  split, GC or gfxinfo numbers exist for this cycle. The last known baseline is CLAUDE.md's 55-59 fps (2026-09-23).
- The emulators attached (emulator-5554/5556) are phone images (1280x2856) and 5554 is out of storage, so they were not used.
- To finish: unlock the tablet, keep it awake (Developer options "Stay awake" while charging), then
  `adb install -r --user 0 app-debug.apk`, start a match and read `adb logcat -d | grep PowerPlay`.

### Diagnosis (code reading only, unmeasured)
- Sim/AI paths are allocation-light. The cycle 1-7 additions (assignMark, guardHanger, seam pass, stats) allocate nothing per frame.
  Only `offensiveSpot`/`defensiveSpot` return a `Pair` per think tick (about 10 skaters every ~0.3 s), which is negligible.
- `World.allSkaters` is already cached. HUD strings are cached. `SoundManager.updateMix` is a few float ops per frame plus
  `applyLoops` every 50 ms. `MusicManager.play` is only called once per second.
- Update and draw run on the same thread, so `synchronized(world)` is uncontended.
- Strongest suspect: CharacterArt sprite cache misses on the game thread. Each skater/goalie sprite is about 215x195 ARGB
  (~170 KB) and costs ~60 vector draws, two gradient shaders and a bitmap copy (`finishLook`) to build. They were built lazily on first
  use of each (team, facing, stride frame) combination, i.e. 192+ skater sprites over the first minutes of a match, each a likely dropped frame
  on the Fire's CPU, plus a texture upload on first draw.

### Changes
1. `Renderer.kt`: background sprite pre-warm (`startSpriteWarm`). A low-priority daemon thread with its own `CharacterArt` fills
   the stride-frame skater sprites (6 frames x 16 facings x 2 teams) and goalie stances 0 and 1 right after teams/scale change.
   Lazy path unchanged as fallback. `spriteLock` + `warmGen` make clears and warm writes safe (stale warm work is dropped and recycled).
   Action frames (wind/follow/poke/fallen) and referee are still lazy to bound memory.
   Expected: removes most of the early-match multi-frame hitches; steady-state fps unchanged.
2. `GameView.kt`: fps log now also prints `hitches: N frames over 25ms, worst X ms` each 5 s so dips can be compared before/after.

### After numbers
Not measured (device locked). Build: `./gradlew assembleDebug` succeeded.

### Unverified / left undone
- Everything above is unmeasured on hardware. Memory effect of pre-warm (~32 MB native bitmaps for skaters + ~11 MB goalies) not checked on the Fire.
- Not done: reducing per-frame draw cost (overdraw of stands/walls, HUD gradients), GC/gfxinfo analysis, `Pair` removal in AIController.

### Perf Cycle 1 - Builder: device results (tablet unlocked later; supersedes "NOT collected" above)
Test: Pro, 2 min periods, indoor, single player, ~80 s live play with scripted joystick swipes and SHOOT/PASS taps via adb
(script in scratchpad play.sh). Note adb input spawns steal some CPU, so absolute numbers are slightly pessimistic.

| | Baseline (cycle 1-7 build, no warm-up) | With sprite warm-up |
|---|---|---|
| fps per 5 s window | 52.9 52.9 49.9 58.4 56.7 57.7 57.9 56.6 53.5 48.9 53.6 53.5 53.8 (mean ~54.2, min 48.9) | 52.3 55.3 57.4 58.5 56.6 58.6 55.9 54.3 55.0 56.6 54.2 57.9 57.3 (mean ~56.0, min 52.3) |
| update ms | 1.0-2.9 | 0.8-2.6 |
| record ms | 1.4-2.0 | 1.6-2.5 |
| post ms (GPU/compositor wait) | 4.7-14.5 | 2.6-13.0 |
| gfxinfo frames / janky | 5563 / 9 (0.16%) | 5797 / 6 (0.10%) |
| gfxinfo p50/p90/p95/p99 | 16/19/21/25 ms | 15/18/19/24 ms |
| gfxinfo GPU p50/p99 | 6 / 12 ms | 6 / 13 ms |
| GC lines in logcat | 1 | 0 |
| crashes | none | none |

- Hitch counter (only in new build): 2-15 frames over 25 ms per 5 s, worst 38-174 ms, so hitches remain after warm-up. The first window
  (match start) had 15 hitches. Baseline has no hitch counter, so hitch counts are not comparable; gfxinfo janky frames are
  (9 -> 6) but the difference is within noise.
- Conclusion: warm-up gives a small gain (~+1.8 fps mean, higher minimum), not a proven large one. Frame cost is dominated by `post`,
  i.e. GPU-bound (it swings 3-14 ms with the camera view, likely overdraw of stands/wall/rink meshes and full-screen HUD layers);
  CPU side (update ~1-3 ms, record ~2 ms) is small. Remaining hitches of 40-170 ms are not explained by sprite building and need
  a systrace/GPU profile (candidates: texture uploads of first-drawn bitmaps, music MediaPlayer.create at match start, event sounds).
- Not done: overdraw reduction in stands/walls, HUD layer cost, hitch root-causing.
