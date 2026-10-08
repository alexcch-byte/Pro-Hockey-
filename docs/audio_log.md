
## Audio Cycle 1 - Builder

Audit (rendered to a scratch dir, analysed with numpy): no clipping in any file (no run of >3 samples at full scale); loop seams of music_menu/music_game/crowd_loop/crowd_roar/wind_loop are within normal sample-to-sample movement (seam step 0.015-0.21 vs 99th-percentile step 0.07-0.40), so no clicks expected. Event coverage gaps found: ICING had no sound of its own beyond the shared whistle, PENALTY had a whistle+horn but no crowd reaction, and the only in-play music was a single 22 s loop (music_game) for the whole match with nothing for overtime/close finishes.

Changes
- tools/make_sounds.py: new `music_game_2` ("Breakaway", D minor, 168 BPM, 16 bars, 22.86 s, -13.0 dB), `music_clutch` ("Sudden Death", E minor, 192 BPM, 16 bars, 20.0 s, -12.4 dB), `crowd_boo` (formant-voice falling "boooo", 3.05 s, pinned to -13.0 dB loudness). All original melodies. The three new renders save/restore the global RNG state so every existing sound renders byte-identical to before (verified with cmp against res/raw).
- res/raw: added music_game_2.wav (984 KB), music_clutch.wav (861 KB), boo.wav (131 KB). res/raw total 5844 KB -> about 7.8 MB.
- MusicManager: `gameTrackVolume(resId)` per-track trim (music_game_2 x1.15, music_clutch x1.1 of GAME_VOLUME 0.55) to level the loops.
- GameView (minimal wiring): one in-play loop is picked at random per match (music_game or music_game_2); overtime, or period 3 with under 60 s left and a goal difference of at most 1, switches to music_clutch (the existing 1 s music check handles the switch).
- SoundManager: PENALTY now also plays the crowd boo after 450 ms (0.75 indoor, 0.3 pond); ICING plays a quieter boo (0.5 / 0.2, pitched up slightly so it differs) after 350 ms and bumps crowd excitement 0.1.

Unverified (cannot listen)
- All of it by ear: tune quality, whether the clutch loop transition at 60 s feels abrupt (it hard-cuts rather than crossfades), boo realism, and the relative level of the new loops (measured -13.0 / -12.4 dB vs -11.5 dB for music_game, trimmed by the volume factors above; not auditioned).
- Built (assembleDebug) but not installed or run on a device.

Not done / ideas
- Boo plays for either team's penalty/icing (SoundManager does not know which team the player controls); a team-aware boo vs cheer would be better.
- No shootout events exist; no crossfade between music tracks; no goal-against groan; menu music still a single loop; the iOS port's audio copies are not updated.

## Audio Cycle 1 - Critic

Method: numpy/scipy analysis of the WAVs in res/raw plus a parse of the note data in `tools/make_sounds.py` (scratch scripts outside the project). Nothing could be auditioned. `./gradlew assembleDebug` passes (APK 15.3 MB; res/raw 7.7 MB).

### Measured
| file | dur | peak | loudness (250 Hz HP, loudest 100 ms) | power <250 Hz | 250-2k | >2k | seam step vs p99 step |
|---|---|---|---|---|---|---|---|
| music_menu | 24.0 s | -1.9 dB | -10.2 | 49% | 44% | 7% | 0.18 vs 0.40 |
| music_game | 21.8 s | -1.9 dB | -11.9 | 67% | 26% | 7% | 0.21 vs 0.37 |
| music_game_2 | 22.9 s | -1.9 dB | -13.1 | 75% | 17% | 8% | 0.19 vs 0.35 |
| music_clutch | 20.0 s | -1.9 dB | -12.4 | 70% | 20% | 10% | 0.28 vs 0.41 |
| boo | 3.05 s | -0.7 dB | -13.0 | 40% | 59% | 0% | one-shot |

### Verdict per change
- **music_game_2 (make_sounds.py:625-651): KEEP, minor tweak.** Builder's level claim confirmed (-13.1 vs -11.9 for music_game, so the x1.15 trim = +1.2 dB lands it level). Seam is fine (step 0.19 < p99 0.35; tails wrap). Scale check of all lead notes: everything is D natural minor except B5 over the C chord (bars 4, 8, 16), a Dorian/Lydian colour that reads as intentional; bar 16's G-major arpeggio over C turns around into Dm on loop. No real clashes, 16/16 bars distinct, so not repetitive. Weakness: only 17% of power is in 250 Hz-2 kHz (lowest of the three game tracks) and 75% is below 250 Hz, where a Fire HD 8 speaker is nearly silent. The -1 octave 50% duty lead doubling (line 645) lands in 233-700 Hz on top of the octave-4 arpeggio (262-523 Hz): likely muddy midrange; try gain 0.04 or drop it.
- **music_clutch (make_sounds.py:653-687): KEEP, minor tweak.** Measured -12.4; trim x1.1 = +0.8 dB, so 0.3 dB louder than music_game, fine. Seam step 0.28 vs p99 0.41, fine. Every note is E natural minor (+F#/D chord tones); strong-beat non-chord tones (F#6, A6, E6, G6 over Em/D/Am) are passing or suspension tones, not clashes. Fatigue is highest here: 192 BPM, constant 16th arpeggio, busy drums, lead up to B6 plus an octave-up duty-0.125 doubling (10% of power >2 kHz, the most of any file). OK for a short finish; halve that doubling's gain (0.06 -> 0.03) to reduce shrillness.
- **boo.wav / crowd_boo (make_sounds.py:1048-1060): KEEP, trim the tail.** Reads as voice not noise: spectral flatness 0.05 (tonal), band energy 100-250 Hz -4 dB, 250-500 -4, 500-900 -7, 900-1500 -13.5, 3-6 kHz -38 dB; centroid 388 Hz (darker than gasp 507 / cheer 819, suits a groan). Envelope: peak at 0.67 s, audible (>-20 dB) 0.09-1.78 s, then about 1.2 s at -43..-60 dB (dead bytes, trim to about 2.3 s). The pitch fall is mild in the data (centroid 422 -> 354 Hz over 1.5 s): expect a low "ooooh" more than a descending "boooo". 40% of its power is below 250 Hz, so on the tablet it will sound thinner than the -13.0 pin suggests (the pin itself is measured above 250 Hz, so the level is right).
- **MusicManager.gameTrackVolume (MusicManager.kt:23-28): KEEP.** Trims verified above.
- **GameView track pick + clutch trigger (GameView.kt:443, 492-497): FIX.**
  1. `w.period >= 3 && w.clock < 60f`: period lengths are 60/120/180/300 s (MatchSettingsActivity.kt:51-55). With the 60 s option, all of period 3 is "the last minute", so clutch plays from its first second.
  2. Flapping: `close` is re-evaluated every 1 s. A goal making the diff 2 drops back to the normal track (new MediaPlayer, hard cut), and the next goal switches again, each time during the celebration.
  3. `MusicManager.play` builds the MediaPlayer synchronously on the game thread (MusicManager.kt:70): a one-off hitch on each switch (size not measured on device).
- **SoundManager PENALTY boo (SoundManager.kt:222): KEEP with caveat.** No handler leak: `release()` clears all callbacks (SoundManager.kt:377-378) and `later` re-checks `enabled`. It plays for either team's penalty (SoundManager has no notion of the human's team), so an opponent's penalty also gets a boo; should be a cheer/"ooh" for the human's power play.
- **SoundManager ICING boo (SoundManager.kt:249-252): REVERT the boo (keep bump(0.1f)).** Crowds do not boo icing, a routine stoppage; the same sample at x1.05 pitch would be heard as a repeated boo on every clear (icings counted per team, Simulation.kt:501).

### Existing bugs affecting the new tracks
1. **Ducking under jingles is mostly cancelled.** `MusicManager.play` with the same resId calls `applyVolume(volume)` (MusicManager.kt:62-66) and GameView calls it every second (GameView.kt:492-497). `duck(0.2, 4000)` after a goal therefore lasts 0-1 s, not 4 s. Pre-existing, but now more audible with louder tracks.
2. A new track started while ducked (e.g. clutch drop right after a goal) is created at full volume (MusicManager.kt:73-75), over the goal jingle.
3. Safe: pause/resume (update skipped while `paused`, GameView.kt:309), user volume 0 stops and re-enables, `stop()` always releases before create, so no MediaPlayer leak.
4. Goal is always a full cheer + goal jingle (SoundManager.kt:225-229) regardless of who scored: no goal-against groan.

### Top open issues (ranked)
1. **Fix ducking.** In the same-resId branch only set `trackVolume`, and apply `trackVolume * duckFactor` (a `duckFactor` field reset by `restoreVolume`); create new players with the current duck factor. A few lines, biggest audible win.
2. **Smooth, non-flapping track switching.** Latch clutch (stay until GAME_OVER) once overtime, or P3 with clock < min(60, 0.33 * periodLength) and diff <= 1. Switch only in FACEOFF/WHISTLE/GOAL phases where the jingle masks it. For a real crossfade add a second MediaPlayer in MusicManager: prepare it off-thread, start at 0, ramp both with a 20 Hz handler over about 1.2 s, release the old one.
3. **Team-aware crowd.** Pass the human's team to `SoundManager.handle`: own penalty -> boo, opponent penalty -> cheer/"ooh"; goal-against -> groan (boo at 0.8, pitch 0.95, shorter or no goal jingle) instead of full cheer. Shootout (World.kt:127+) has no tension/save sounds yet.
4. **Tablet-speaker spectrum.** 67-75% of game-music power is below 250 Hz; the triangle bass at C2-D3 (65-147 Hz) is largely inaudible on the Fire HD 8. Move driving-track bass up an octave (or add an octave-up layer) and re-pin levels; same check for music_menu.
5. **APK size and variety.** res/raw is 7.7 MB; four music WAVs are about 3.9 MB. Cheap cuts: trim the boo tail (-20 KB); `pip install soundfile` (ships libsndfile with Vorbis) and re-encode music to OGG, about 10x smaller, but verify seamless MediaPlayer looping on the Fire first. Menu music is still a single loop and UI sounds are only `button_click`; low priority.

## Audio Cycle 2 - Builder

Changes
- MusicManager (rewritten internals): `duckFactor` is applied inside one `applyAll()` used by play (same-track branch and new player), setUserVolume, fades and duck, so the per-second `play()` no longer cancels the 4 s goal duck, and a track created during a duck starts ducked. New `switchTo()` builds the next MediaPlayer on a background thread and crossfades over 1.2 s (50 ms handler steps) with generation tokens so stale/overlapping requests are released; `isCurrent()`; pause/resume/stop cover the outgoing player too and stop() cancels fades. `play()` stays synchronous (first start / menu).
- GameView: clutch latches once overtime or (period >= 3 and clock < min(60, 0.33 x period length) and goal diff <= 1) until GAME_OVER; track swaps only when phase != PLAY (via `switchTo`); `activeTrack` is reset at GAME_OVER. Wiring for team-aware crowd: `soundManager.localTeam`, scorer inferred from score deltas, offender from `world.penaltyTeam`.
- SoundManager: `handle(event, team = -1)`. Own penalty: boo; opponent penalty: soft cheer (0.4 indoor / 0.15 pond). Goal against the human: quiet cheer (0.3), groan (boo at 0.8, pitch 0.93) after 250 ms, shorter hold/excitement, no goal jingle. Unknown team keeps old behaviour. Icing boo removed (bump 0.1 kept). The horn (HORN event) is unchanged.
- make_sounds.py: `add_bass(..., octave=2)`; driving tracks (music_game, music_game_2, music_clutch) use octave 3. music_game_2 -1 octave doubling 0.07 -> 0.04; music_clutch octave-up doubling 0.06 -> 0.03. The three tracks are now pinned with `save(level=)` to their old loudness (-11.5/-13.0/-12.4) so gameTrackVolume trims are unchanged. Boo trimmed to 2.3 s with a 0.3 s fade.
- Re-rendered all; only boo, music_game, music_game_2, music_clutch differ from the previous res/raw (cmp on all others identical, RNG streams unchanged); those four copied into res/raw. music_menu untouched (49% < 250 Hz).

Measured (old -> new, power share <250 Hz / 250-2k Hz)
- music_game 66% / 25% -> 46% / 45%; music_game_2 75% / 17% -> 59% / 32%; music_clutch 69% / 19% -> 59% / 31%. Unpinned, the new renders measured +2.4/+2.6/+1.9 dB louder on the 250 Hz-HP scale, hence the re-pin. Peaks now -4.4/-4.5/-3.8 dB; loop seam steps 0.16/0.14/0.22 vs p99 step 0.28/0.25/0.31 (fine). boo 3.05 s -> 2.3 s, level -13.0.
- assembleDebug passes.

Unverified (not auditioned, not installed)
- By ear: bass octave change feel, crossfade smoothness, groan/cheer balance, whether a 1.2 s fade-in on switch overlaps the goal jingle well. MediaPlayer.create on a background thread on Fire OS. Client (WiFi guest) goal/penalty attribution relies on scores and penaltyTeam being applied before events.
- Menu music still a single loop; no new UI sounds; no OGG conversion (as instructed). iOS audio copies not updated.

Undone / ideas
- Extra penalty-on-opponent "ooh" is just the existing cheer sample at low level (no new sample).

## Audio Cycle 2 - Critic

Method: numpy/scipy on the working-tree WAVs (scratch scripts outside the project), `chord_notes` imported from make_sounds.py for the bass data, code read of the git diff. Nothing auditioned. `./gradlew assembleDebug` passes. `git diff --stat` on res/raw: only music_game.wav modified, boo/music_game_2/music_clutch new, every other file unchanged.

### Measured (working tree)
| file | dur | peak | loud (250 Hz HP) | <250 | 250-2k | >2k | seam step vs p99 |
|---|---|---|---|---|---|---|---|
| music_game | 21.8 s | -4.4 | -11.7 (old -11.9; pin said -11.5) | 47% | 46% | 8% | 0.16 vs 0.28 |
| music_game_2 | 22.9 s | -4.5 | -13.0 | 60% | 32% | 8% | 0.14 vs 0.25 |
| music_clutch | 20.0 s | -3.8 | -12.4 | 59% | 31% | 10% | 0.22 vs 0.31 |
| boo | 2.3 s | -0.7 | -13.0 | 40% | 59% | 0% | fades to -58 dB, audible 0.09-1.78 s |

No clipping (0 samples >0.999). Builder numbers confirmed; gameTrackVolume trims still level (music_game within 0.2 dB of its pin).

### Verdict per change
- **MusicManager duck (MusicManager.kt:218-231): KEEP.** `applyAll()` multiplies duckFactor * userVolume in play (same track and new), setUserVolume, fadeStep and duck, so the 1 s `play()` no longer cancels the goal duck. The `restoreVolume` lambda's `this` is MusicManager (correct). Remaining gap: `stop()` (called inside `play()` for a new track) resets duckFactor and cancels restoreVolume, so a hard `play()` swap during a duck starts at full volume; the GameView swap uses switchTo during dead phases, which keeps the duck. Fine.
- **switchTo background MediaPlayer.create (MusicManager.kt:126-176): KEEP.** create() works off the main thread (MediaPlayer falls back to the main looper when the thread has none; no listeners are used, so no looper dependence). Exceptions give null; onNextReady releases and clears pendingRes. Rapid requests: the generation token releases stale players; stop()/onDestroy bumps generation so a late mp is released (no leak). All state is under one monitor and handler posts run on the main thread. isLooping is set on the new player (old keeps its own). Volume math ends right: fadeIn 0 -> 1 linearly, final level = trackVolume * duck * user, outgoing released at 1.2 s. Nits: (a) linear (not equal-power) fade dips about 3 dB at the midpoint; use sqrt curves. (b) A second switch mid-fade releases the previous outgoing at partial volume (small click, rare). (c) If `mp.isLooping` throws after releaseOutgoing()/removeCallbacks(fadeStep) (lines 151-152) fadeIn may stay below 1 and the track stays quiet; negligible probability, set fadeIn = 1f in the catch.
- **GameView clutch latch (GameView.kt:506-530): KEEP, one fix.** lastStretch = min(60, 0.33 * period) gives 19.8 / 39.6 / 59.4 / 60 s for the 60/120/180/300 s options; overtime latches, uncapped tournament OT stays latched; reset on the GAME_OVER tick (509-511). The `phase != PLAY` gate is reachable (whistle, faceoff, goal, period end all occur; worst case the swap is late). Muted music: `play()` returns early on !enabled, harmless; unmuting restarts the right track via the `!isCurrent` branch. FIX: `restartMatch()` (GameView.kt:204-220) does not reset `clutchLatched`/`activeTrack`; they clear only if a music tick runs in GAME_OVER (normally true during the 3.8 s result delay, but a pause or fast Rematch in that window starts the rematch on music_clutch). Add `clutchLatched = false; activeTrack = 0` there. `gameTrack` is a `val`, so a rematch repeats the same loop; make it var and re-roll.
- **SoundManager team-aware crowd (SoundManager.kt:226-250, GameView.kt:449-478): KEEP, minor fixes.** Host scorer inference is correct (scoreGoal increments before the event; handleEvents runs each tick and refreshes lastScores, including after rematch resets). Guest: NetCodec.applyState sets s0/s1 and penaltyTeam before pushing events (NetMessage.kt:151-177) and localTeam is 1 for WIFI_CLIENT, so sides are right on both ends. Shootout goals and the penalty-extension path also carry the right team. Issues: (1) the `team < 0 || localTeam < 0` branch duplicates the own-penalty branch (harmless). (2) GameView still does `MusicManager.duck(0.2f, 4000)` on every GOAL (line 467): on a goal against there is no jingle but the music drops for 4 s. (3) Goal-against = cheer sample at 0.3 plus crowd bump 0.5, so the roar loop stays up; plausible as "visitors cheer, home groans", untestable by ear. (4) Pond levels fine. Delayed `later()` posts check `enabled` and release() clears callbacks: no leaks. No double-trigger source found.
- **ICING boo removal (SoundManager.kt:266-268): KEEP.** Only bump(0.1) remains.
- **Bass up an octave (make_sounds.py:317-322, calls at ~620/651/684): KEEP, tune.** `chord_notes(chord, 3)` roots: C 131, Dm/D 147, Em 165, F 175, Am 220, Bb 233 Hz; the driving pattern's `root*2` step lands at 262-466 Hz, exactly the lowest arpeggio note (octave-4 root) at the same instant (Dm 294/294, Bb 466/466). Chords are built upward from the note name, so the bass leaps up to an octave between chords (C 131 -> Bb 233) instead of walking. All notes are in key, so no scale clash; the 250 Hz-2 kHz share rose 25 -> 46% (game), 17 -> 32% (game_2), 19 -> 31% (clutch), as intended, but 130-470 Hz is now crowded (bass, arpeggio, game_2's -1 octave lead doubling): mud risk on a mono tablet speaker. Suggest dropping Bb/Am roots an octave and replacing `root*2` with the fifth or a rest.
- **Pins (make_sounds.py:1238-1242): KEEP.** music_game -11.7 vs requested -11.5 (0.2 dB, ignore). RNG save/restore keeps other files byte-identical (git confirms).
- **boo.wav (make_sounds.py:1048-1065): KEEP.** Level -13.0, ends at -58 dB with the 0.3 s fade, no click. About 0.5 s of near-silence remains after 1.8 s (could trim to 2.0 s). Still a low "ooh" (40% <250 Hz) more than a falling "boooo" on a tablet.

### Open issues (ranked)
1. **Rematch state reset** (GameView.kt:204, 446-447): reset clutchLatched/activeTrack and re-roll gameTrack in restartMatch. Two lines; avoids a rematch starting on the clutch loop.
2. **Goal-against design**: skip the 4 s music duck when the goal is against the human (GameView.kt:467) and add a distinct short descending sting so a loss reads on a tablet speaker.
3. **Mid-range mud / bass line** in the three driving tracks: walk the bass (root octave nearest 130-200 Hz), replace `root*2` with the fifth, re-pin levels, check game_2's doubling by ear.
4. **Missing moments**: no shootout sounds (round start, save vs goal stingers), no OT-start stinger, menu music is one loop, UI sounds are only button_click, and the horn/whistle mix was never auditioned against the louder music (music ducks only on goal/period end). Approach: shootout stingers in make_sounds.py, duck 0.6 for 1 s on WHISTLE/HORN, second menu loop chosen randomly like gameTrack.
5. **APK size / OGG**: res/raw about 7.7 MB (four music WAVs about 3.9 MB). OGG would cut about 10x but MediaPlayer on Fire OS may gap at the loop point, which would be audible in the crossfade design; test on the Fire HD 8 with WAV as fallback. Low urgency (APK 15 MB). Also cheap: equal-power fade curve and the fadeIn reset in the catch (MusicManager.kt:155-170).
