
## Cycle 1 - Builder

No critic findings existed yet; items chosen from code reading.

1. AI seam passes (AIController.carrierBehaviour, new shotQuality helper). In the offensive zone, an AI carrier whose own shot quality is under 0.5 passes to an open teammate (no opponent skater within 5 ft, lane clear) whose spot is at least 0.2 better. Rolled per think tick with new AiSettings.seamPass (EASY 0.2, MEDIUM 0.45, HARD 0.65). Reduces the carry-and-shoot-from-anywhere look.
2. AI goalie anticipation (AIController.updateGoalie). While the puck is in flight faster than 25 ft/s the goalie tracks the puck position led by velocity * AiSettings.goalieLead (EASY 0.04, MEDIUM 0.10, HARD 0.16 s). Helps against cross-ice passes and shots. Butterfly logic still uses the real puck.
3. Tripping penalty rate (Simulation.contestPuck). Successful poke checks called tripping 8% of the time, too punishing with AI poke rates of 55-75%. Now AiSettings.tripChance = 0.04 (all difficulties).

AiSettings gained three trailing defaulted params: seamPass, tripChance, goalieLead. No GameEvent changes.

Not done: build passes (assembleDebug); not installed or played on device/emulator, so tuning is unverified. Left undone: man-to-man slot coverage for defenders, offside/icing, rebound direction tuning.


## Cycle 1 - Critic

Build: `./gradlew assembleDebug` passes with the uncommitted diff. Nothing played on device; verdicts are from code reading.

### (a) Verdicts on builder changes

1. **Seam pass (AIController.kt ~197-215, shotQuality ~238): KEEP, with fixes.**
   - Monotonic rule (target must be 0.2 better than the carrier) means no strict A-B-A turnover loop. Good.
   - Bug: `sim.pass(s, dx, dy, ...)` (Simulation.kt:549) re-selects a receiver by joystick-angle score (`angle*20 + d*0.15`), not the chosen `target`. If the lane to `target` is blocked (checkLane skips it) or `target` is under 3 ft, the pass silently goes to ANY teammate within 74 degrees, possibly a worse spot or back toward the point. Fix: add a `passTo(s, target, accuracy, checkLane)` overload in Simulation (reuse the speed/lead/accuracy code) and call it, or check the lane before choosing.
   - Rolled every think tick (reaction 0.15 s on Hard, so ~6.5 ticks/s at 0.65) the pass effectively fires immediately on entering the zone, before the carrier can build up a shot. Expect passive/perimeter-passing Hard offence. Fix: gate with a per-skater cooldown (e.g. `s.actionCooldown = 0.6`) or roll once per ~0.5 s, and drop Hard to ~0.4.
   - No checks that the target is moving toward the net or that the pass crosses the slot in front of an opposing goalie-side defender (laneBlocked only tests 2.6 ft). Acceptable.
   - Opponents in the penalty box (`inPenaltyBox`, parked at y=+-44.5) are not excluded from the "open" scan; harmless because far away.
2. **Goalie lead (AIController.kt ~250-270): KEEP but FIX scaling.**
   - Correct and allocation-free (reused `Pt`). Only the target is led; butterfly/contact use the real puck. Lateral clamp (+-3.2 ft) bounds the effect, so Hard is not made unbeatable by this alone.
   - Design flaw: `updateGoalie` is also called for the HUMAN's goalie (Simulation.kt ~330, using `HUMAN_GOALIE_SKILL`), but `ai.goalieLead` is difficulty-scaled. On Hard the player's own goalie now gets the best lead; on Easy the worst. That is inverted. Fix: pass lead as a parameter, human = fixed ~0.10, AI = `ai.goalieLead`.
   - Hard stack is now goalieSkill 1.05, padScale 1.05, leak 0, lead 0.16. Combined with rebound mostly directed forward (Simulation.kt ~870) this may feel stingy; playtest Hard goals/game before raising anything else. Suggest Hard lead 0.12.
   - Hard 25 ft/s threshold gives a step change in target (up to ~3 ft) as a slow puck crosses it; goalie speed `d*9` makes it jitter visibly. Fix: scale lead by `((speed-15)/20).coerceIn(0,1)`.
3. **tripChance 0.04 (Simulation.kt:797, World.kt): KEEP.** Sensible halving; applies to human and AI pokes alike. Same field is not difficulty-scaled; fine. Note there is no check that a tripped carrier is the one who "had" the puck legitimately, acceptable.
4. **AiSettings new params (World.kt:55-58): KEEP.** Defaulted trailing params, no network/GameEvent impact; the authoritative host owns AiSettings so client mirroring is unaffected.

### (b) Ranked open issues for next builder cycle

1. **Interference/charging penalty rate is far too high and hits the human (Simulation.kt ~717-727).** Any body check on a skater >10 ft from the puck has a 25% interference call and big closing speeds 22% charging; combined with 40 s fixed penalties and a single global penalty slot (`penaltyTeam != -1` silently swallows second calls) this will produce frequent whistles and feel arbitrary. Approach: drop to ~8%/10%, call interference only if the victim is not a pass target and never when the puck is loose within 12 ft; allow a banner hint ("PENALTY - delayed") rather than instant dead puck; consider making AI checkers avoid hitting away from puck (AIController chaser only hits within 6.5 ft of the carrier, so it mostly affects the human).
2. **No offside or icing; and no neutral-zone structure.** AI chasers/pass logic freely send the puck deep; `think` LOOSE sends two chasers regardless. Approach: add icing only (simpler, big feel win): in `Simulation.playStep`, when a non-shot puck crosses the opponent goal line having last been touched by the passer behind the red line and not touched by the defending team, blow the whistle with faceoff in the shooter's end (reuse `startWhistle`, banner "ICING", add event at END of GameEvent enum). Skip offside for an arcade game, or implement as a "delayed" check only against humans.
3. **Defender coverage is zone-lane only (AIController.defensiveSpot ~143-153).** Non-chasing defenders go to fixed spots keyed to the carrier, ignoring open attackers, so seam passes (now more frequent) find wide-open skaters and slot coverage depends on LW/RW sitting at y=+-18. Approach: for each non-chasing defender assign the nearest unmarked opposing skater in the defensive zone (greedy match per think tick; store `markId`), target = point 3-4 ft goal-side of the mark on the line to our net, with D keeping slot priority. Also removes the trivial "pass to open man = goal" exploit.
4. **Rebound behaviour is thin (Simulation.kt ~862-878).** Rebounds leave at 32% speed with +-5 ft/s vy noise and a forced forward vx of 6-16, so they usually just dribble out to the nearest skater and attackers are not rewarded for crashing the net. Approach: on non-cover saves, bias rebound direction into the slot (toward y=0 +/- noise) at 20-35 ft/s, add 0.25 s `pickupCooldown` for the shooter only, and have OFFENSE LOOSE chasers (think, Situation.LOOSE) include the nearest forward when the loose puck is within 20 ft of the opposing net. Also test that `coverChance` 0.35 on Hard doesn't make it feel like constant whistles.
5. **Open-ended OT and pull-goalie flow (Simulation.kt ~212-232, 291-300).** Regular-season OT is unbounded sudden death (clock counts up), so a tight Hard game can run forever; shootout exists only as a separate mode. Approach: cap OT at e.g. 180 s then call `setupShootoutAttempt` flow (or declare a tie); also the AI pulls its goalie only in period 3 (<50 s) and never when in OT/penalty, and `goaliePulled` resets on any goal including the pulled team's goal (fine) but not on whistles/period end, so a pulled goalie persists into the next faceoff if the clock expires.

Minor/cleanup: `pickChasers` allocates lists on every think tick (AIController ~128); `MatchResult.kt` unused.


## Cycle 2 - Builder

Build: `./gradlew assembleDebug` passes. Not installed or played; all tuning is unverified.

A. Fixes to cycle 1
1. Seam pass: new `Simulation.passTo(s, target, accuracy, human, checkLane)` (shared `launchPass`, no receiver re-selection; returns false if lane blocked/target unusable). AIController uses it. Per-carrier `Skater.seamCooldown` (0.6 s, set whenever the roll is attempted, decremented in `Simulation.step`). seamPass now EASY 0.12 / MEDIUM 0.25 / HARD 0.4.
2. Goalie lead: `AIController.updateGoalie` takes `leadSeconds`; human goalie uses `Simulation.HUMAN_GOALIE_LEAD` = 0.10, AI uses `ai.goalieLead`. Lead ramps with `((speed-15)/20).coerceIn(0,1)` instead of the 25 ft/s step. goalieLead EASY 0.04 / MEDIUM 0.09 / HARD 0.12.

B. Open issues
- #1 Penalties: INTERFERENCE 8%, CHARGING 10% (consts in Simulation companion). Interference skipped when the victim is a pass target or the puck is loose within 12 ft (plus existing >10 ft and not-carrier conditions). AI checkers brushing a non-carrier more than 10 ft from the puck no longer knock them down (check cancelled, no penalty). No "delayed penalty" mechanic; single global penalty slot unchanged.
- #2 Icing: new `GameEvent.ICING` appended at the END of the enum (NetCodec uses ordinals, round-trips automatically; banner is already synced in the snapshot). Simulation tracks `icingArmed/icingTeam`: armed while a carrier is in his own half, cleared on loosePuck (strips/knockdowns), goalie contact, faceoff reset, or any new possession (re-evaluated). When the puck is loose, last touched by the armed team, and crosses the opposing goal line outside the net, `checkIcing` fires: banner "ICING", ICING + WHISTLE events, `startWhistle` with faceoff at the offending team's end dot (side by puck y). Skipped when the offending team is short-handed and in shootouts; goalie-pulled needs no special case (carrier.isGoalie never arms). SoundManager handles ICING as a no-op (WHISTLE plays alongside). Not handled: waving off icing when a teammate could reach it first; offside.
- #4 Rebounds (partial): on non-cover saves the puck leaves at 20-35 ft/s aimed out of the crease with a vy pulled toward y=0 plus scatter; the shooter gets a 0.25 s pickup cooldown. Did NOT change OFFENSE LOOSE chasers to crash the net.

Left undone: #3 man-to-man coverage, #5 OT cap/pull-goalie reset, offside, `pickChasers` allocation, `MatchResult.kt` removal. The now-redundant puck reflection lines in the rebound branch of `handleGoalies` still run before being overwritten (harmless).


## Cycle 2 - Critic

Build: `./gradlew assembleDebug` passes with the uncommitted diff (exit 0). Nothing installed or played; verdicts from code reading. Line numbers are in the working copy of `app/src/main/java/com/tablehockey/game/game/`.

### (a) Verdicts

1. **passTo + launchPass (Simulation.kt ~607-620, 622-645): KEEP.** Lane check, unusable target (goalie, stunned, in box, <3 ft), carrier check are correct, and `launchPass` is a clean extraction; the human `pass()` path is unchanged. Minor: the human/AI `pass()` (Simulation.kt ~575-600) still never skips `inPenaltyBox` teammates (passTo does), so a pass can still be aimed at the parked penalised skater at y=+-44.5. One-line fix there.
2. **Seam cooldown (AIController.kt ~198-218, Skater.kt:51): KEEP.** Cooldown is set whenever the zone gate is passed, decremented in `step` for all skaters, and reset in `Skater.place`. Hard 0.4 per 0.6 s is reasonable. Cosmetic: the inner `if` bodies are not re-indented. The pass still never checks that the receiver can be reached by a shot-blocking defender, fine.
3. **Goalie lead (AIController.kt ~250-262, Simulation.kt:330): KEEP.** Ramp `((speed-15)/20).coerceIn(0,1)` is smooth, human goalie fixed 0.10, only `puck` target is led; `leadPuck` is a reused object so no allocation. Check nothing later in `updateGoalie` still reads `world.puck` for positioning (it uses the local `puck`, good).
4. **Penalty rates (Simulation.kt companion, ~770-785): KEEP.** 8 percent / 10 percent plus pass-target and loose-puck exemptions are sane. Remaining gap: the AI-checker "glancing brush-by" branch (Simulation.kt ~735-742) zeroes `checkTimer` and slows the checker but never stuns the victim, so AI checkers cannot create hits away from the puck at all; acceptable (it also removes the old penalty source against the human).
5. **ICING (Simulation.kt ~419-448, setupFaceoff:128, loosePuck:812, handleGoalies:902, World.kt, SoundManager.kt:247): KEEP, with two fixes.**
   - No stuck state found: fires only in `Phase.PLAY` (`playStep`), calls `startWhistle`, which uses the normal WHISTLE -> `setupFaceoff` path; `setupFaceoff` disarms. Faceoff end is correct: `team.ownGoalX + attackDir*(89-69)` is the offender's own end dot, side by puck y (0 maps to +DOT_Y, fine).
   - Arming/disarming is consistent: re-evaluated every carried tick, so a pass received at/over the centre line disarms; loosePuck (strip/knockdown) and goalie contact disarm; shootouts excluded; goalie carriers never arm. Shorthanded offender: icing is allowed and disarmed (correct). Penalty called mid-flight: callPenalty whistles anyway.
   - Puck entering the net: `updatePuck` returns the goal first, so a shot from the own half that scores is a goal, not icing. A shot beside a post that is clamped in front of the line (PhysicsEngine ~207-211) never passes +0.5 ft over the line, so no false icing.
   - Fix 1 (medium): hard stretch passes. A long pass from the own half to a teammate who is in range but whose pickup fails (pass speed up to 78 ft/s, blade radius check in `tryPickups`) will cross the goal line and be called icing even though the receiver touched/near-touched it. Also a human slap shot from the own half that misses wide is icing (intended). Suggested: in `checkIcing`, if `p.passTarget` is on the icing team and within ~8 ft of the puck when it crosses, or any icing-team skater is within ~4 ft, skip the call (wave-off) and disarm.
   - Fix 2 (low): `lastTouchTeam` is not updated by body/stick deflections without a pickup (opponent skater blocks without carrying), so an opposition deflection can still be called icing on the original team. Set `lastTouchTeam` when an opposing skater's blade/body contacts the loose puck in `PhysicsEngine`/`tryPickups`, or disarm when any opposing skater is within ~2 ft of the puck.
   - Client sync: banner is part of the snapshot, ICING is appended last in the enum, NetMessage bounds-checks ordinals (NetMessage.kt ~174). `GameView.handleEvents` has an `else` and `SoundManager.handle` has the explicit no-op. The ICING event itself plays no unique sound/shake; consider an optional banner-only treatment (already there). `icingArmed/icingTeam` are host-only private fields, which is correct since the guest only mirrors.
   - Threading/allocs: all inside `playStep` (under the world lock), no allocations (`showBanner` args are existing strings).
6. **Rebound change (Simulation.kt ~928-945): KEEP, tune later.** Speed 20-35 ft/s always directly outward; vy term `-p.y*0.35` is negligible because `p.y` is within ~+-4 ft at the goalie, so the actual behaviour is outward with +-6 ft/s scatter (the comment "toward y=0" overstates). Goalie contact pushes the puck to the pad surface (`goalieContact`), so there is no re-contact/double-count loop. Slot risk on Easy: coverChance 0.15 with goalieLeak 0.3 and pad 0.75 means many non-cover rebounds, and the puck now sprays out 20+ ft/s into the slot, but no attacker is told to crash the net, so conversion depends on the human; scrambles cannot become endless because the shooter is locked out only 0.25 s while the shot's own 0.45 s `pickupCooldown` already covers it (the new lockout is mostly redundant, harmless). The dead reflection code before the overwrite is wasted work only. Keep; revisit with playtest data.
7. **tripChance, AiSettings defaults (World.kt): KEEP.**

### (b) Ranked open issues for the next builder

1. **Man-to-man defensive coverage (AIController.defensiveSpot ~148-160).** Still fixed lane spots keyed off the carrier; seam passes and cross-ice passes find unattended attackers. Approach: per think tick, for each non-chasing defender assign the nearest unmarked opposing skater inside our defensive zone (greedy, store `markId`, reuse an IntArray), target = point 3 ft goal-side of the mark on the line to our net; defenders keep slot priority (LD/RD cover the slot first). Avoid list allocation.
2. **OT cap and goalie-pull reset (Simulation.kt startNextPeriod ~245, endPeriod, pull logic ~307).** Regular OT is unbounded sudden death; `goaliePulled` is only cleared on a goal, so an AI goalie pulled at the end of P3 enters OT pulled. Approach: in `startNextPeriod` clear both pulls (and re-seat goalies); cap OT at ~180 s counting up, then go into the existing shootout flow (or declare a tie); only AI-pull in P3 with `!w.overtime`.
3. **Loose-puck chasers and net-crash (AIController LOOSE situation, pickChasers ~125).** Still sends two nearest regardless of zone; with larger rebounds, add the nearest forward when the loose puck is within 20 ft of the opposing net, and keep one D back. Also replace the `filter/sortedBy/take` list chain with a two-pass min search (per-think allocation).
4. **Waving off icing / offside-lite and receiver awareness (see Fix 1/2 above).** AI never plays an icing-aware dump-in (it only passes in own zone), so icing will mostly bite the human; consider a small HUD hint ("ICING") and no AI changes.
5. **Difficulty curve.** Hard stacks goalieSkill 1.05, pad 1.05, leak 0, lead 0.12, seamPass 0.4, hit 0.55; Easy still has aimAssist 1 and leak 0.3. Needs a quick playtest log (goals/shots per 3-minute game per level) before more stacking; consider a time-on-puck check for the human on Medium.

Minor: `Simulation.pass()` ignores `inPenaltyBox` targets; `MatchResult.kt` unused; brace indentation in the seam-pass block.


## Cycle 3 - Builder

Build: `./gradlew assembleDebug` passes. Not installed or played (no on-ice verification); all tuning unverified.

A. Icing fixes (Simulation.kt)
1. Wave-off: `checkIcing` now skips (and disarms) when the pass target (icing team) or any icing-team skater is within `ICING_WAVEOFF_FT` = 6 ft of the puck at the goal-line crossing.
2. `noteOpposingTouch()` (runs while icing is armed and the puck is loose): an opposing skater's blade/body within 2 ft sets `lastTouchTeam`, which cancels the call.
3. `pass()` now skips `inPenaltyBox` teammates like `passTo`.

B. Overtime
- `startNextPeriod` clears both `goaliePulled` flags (setupFaceoff already re-seats the goalie). AI pull also requires `!w.overtime`.
- OT capped at `OT_LENGTH` = 180 s (clock counts up) -> `gameOver()`. The shootout is a separate mode with its own `isShootout` flag that is not in network snapshots, so entering it mid-match was not safe; the game ends as a TIE instead. `gameOver` now shows "FINAL - TIE" on equal scores (previously it would have named team 1 the winner); `GameView.checkMatchOver` already passes `won = null` for ties so the match-over dialog handles it. No snapshot/GameEvent changes.

C. Man-to-man coverage (AIController.assignMark, Skater.markId)
- Non-chasing defenders (opponent carries) mark the nearest unmarked attacker inside our defensive zone (attack-frame x < -25, not the carrier, not in the box, not stunned); claims are checked against teammates' `markId` (no list/array allocation). Target is 3 ft goal-side of the mark on the line to our net. Slot attackers (x < -50, |y| < 13) get a -10 score for D and +10 for forwards; +/-6 stickiness for the current mark. No mark available -> old `defensiveSpot` fallback. `markId` resets on chase, own possession, loose puck and `Skater.place`. Host-only AI scratch; not networked.

D. Loose pucks
- `pickChasers` is allocation-free (fills `chaserA/chaserB`). When the loose puck is within 20 ft of the opposing goal line (`NET_CRASH_FT`), chaser A is the nearest non-D skater and D get +12 ft for chaser B, so a forward crashes the net and one D stays back.

Left undone / unverified: offside; `defensiveSpot`/`offensiveSpot` still return `Pair` (pre-existing boxing), `World.allSkaters` still allocates; `MatchResult.kt` removal; difficulty-curve playtest; whether 3 ft goal-side marking makes Hard too stingy or the AI too reactive to roaming attackers.


## Cycle 3 - Critic

Build: `./gradlew assembleDebug` passes (exit 0) with the uncommitted diff. Nothing installed or played; verdicts from code reading. Files under `app/src/main/java/com/tablehockey/game/`.

### (a) Verdicts

1. **Icing wave-off + noteOpposingTouch (game/Simulation.kt ~436-478): KEEP.** Shot/pass release does not go through `loosePuck` (only strips/knockdowns, lines ~837-903 do), so arming survives a dump. The 6 ft wave-off is measured at the far goal-line crossing, where a dumper's teammates are almost never present, so icing will still fire normally; it only suppresses the genuine race/stretch-pass cases. `noteOpposingTouch` runs only while armed and loose (5 skaters, a few hypots), negligible cost. Edge: a human forechecker sprinting alongside a dump-in within 6 ft cancels the call (acceptable, arguably correct). Fine.
2. **pass() skips inPenaltyBox (Simulation.kt ~620): KEEP.**
3. **Overtime clear of goaliePulled, `!overtime` for AI pull, 180 s cap (Simulation.kt ~250, ~302, ~318): KEEP, but the tie has one serious consumer bug.**
   - **FIX (high): tournament mode treats a tie as a loss.** `GameActivity.showMatchOverDialog` ~159 sends `LOCAL_WON = (localWon == true)`, and `TournamentActivity.onMatchFinished` (~277-320) sets `winner = team2` and `userEliminated = true` when `!localWon`. A 3:00 tied OT now eliminates the player from the bracket (and records the AI as the winner) while the dialog says "GAME OVER". Before cycle 3 OT was unbounded so this could not happen. Fix: when `IS_TOURNAMENT`, do not cap OT (pass a `MatchConfig`/World flag, or skip the `OT_LENGTH` check), or break ties deterministically (coin flip weighted by shots, with a "WON ON SHOTS" banner). Non-tournament: "FINAL - TIE" is fine; the dialog title is "Game over" which is acceptable, though `playResult(null)` should be checked to be a neutral jingle.
   - Network: `GameView.checkMatchOver` computes `won` from scores locally, null on tie; client path works because the banner/scores/phase come in the snapshot. No remaining "assume team 1 wins" code found beyond the fixed `gameOver`. Clock shows count-up in OT, so the 3:00 cap is visible; consider showing it on the HUD.
4. **assignMark man-to-man (game/AIController.kt ~170-210): KEEP concept, FIX stale claims (medium).**
   - The claim check `t.markId == o.index` for teammates `t` ignores whether `t` is actually covering. `markId` is only cleared when `t` itself thinks (OWN/chase/loose) or on `Skater.place`. Stale cases: (a) a penalised teammate (AI skipped while `inPenaltyBox`, `callPenalty` does not reset `markId`) keeps its mark "taken" for the whole 40 s, so on the penalty kill, when a defender is already missing, one attacker is left uncovered; (b) the human-controlled skater (AI not run for him) keeps whatever `markId` he had when control switched to him, and control switches often. Fix: in the claim loop skip `t.inPenaltyBox` and `world.isHuman(team.id) && world.controlled[team.id] == t.index`, or clear `markId` in `callPenalty` and in the control-switch code.
   - Two defenders on one mark: avoided by the sequential claim check plus per-skater stickiness; swaps cannot flip-flop because an earlier-listed skater cannot steal a mark an already-listed teammate holds. Side effect: no "best-fit" re-trade, so a far defender can hold a mark while a closer one covers a worse one. Accept.
   - Goalie excluded both as marker (goalie has its own update) and as mark (`o.isGoalie`). Carrier excluded, so nobody but the single chaser pressures the puck carrier; fine.
   - Targets are only refreshed on think ticks (0.15 s Hard, up to 0.6 s Easy), and the target is 3 ft goal-side of a moving player, so on Hard it trails slightly and on Easy lags 10+ ft: that is a feature on Easy. Jitter risk is low (stickiness 6 ft). A mark near the crease places the target 3 ft from the post region; defenders can crowd the goalie and the crease (no crease keep-out exists). Consider clamping the target to |y|>=5 when x is inside the crease area.
   - Cost: 3 defenders x 4 attackers x 5 teammates per think tick, no allocation. Fine.
5. **pickChasers (AIController.kt ~145-175): FIX NOW (high, a regression).** In the normal (non-net-crash) path `a` is null for the first iteration only; from the second eligible skater onward the code takes the `else if (d < bD)` branch, so `a` is never replaced and is simply the FIRST eligible skater in list order (the centre, or the first forward if he is human/stunned) regardless of distance, and `b` is the nearest of the rest. Result: the C chases every loose puck from anywhere on the ice (aiChaser stickiness doesn't help) plus the nearest other skater, and the true nearest defender is often not the first. Fix: use a proper top-2 insertion independent of whether `a` came from the net-crash prelude, e.g. keep `a`/`aD` and `b`/`bD`, and in the loop `if (a == null || d < aD) {b = a; bD = aD; a = s; aD = d} else if (d < bD) ...` only in the non-netCrash case (in the netCrash case `a` is fixed, so only compare against `bD`). The net-crash branch itself is right (nearest non-D forward, D penalised 12 ft), with one edge: if every forward is human/stunned `a` stays null and the generic path takes over, fine.
6. **Goalie pull / startNextPeriod / SoundManager ICING / AiSettings: KEEP.** The seam, lead and trip settings carry over from cycle 2.

### (b) Ranked open issues for the next builder

1. **Fix `pickChasers` top-2 bug (AIController ~160-172)** as above. One-line-level change, large behaviour effect (loose-puck scrums, the first-listed skater being dragged all over the ice, defence abandoning positions).
2. **Tournament tie = elimination (GameActivity ~159, TournamentActivity ~277-320).** Either disable the OT cap in tournament or add a tiebreak. Also make `LOCAL_WON` a tri-state if the bracket should ever play a shootout. Reachable on Hard where goalies are 1.05 skill.
3. **Stale `markId` claims (assignMark claim loop).** Skip boxed/human teammates (or clear in callPenalty and on control switch). This matters most on the penalty kill, which is where coverage needs to be tight.
4. **Scoring rate / difficulty curve (reasoned, not measured).** The stack is now: man-to-man marking (taking the slot seam passes away on Hard), larger rebounds into the slot (20-35 ft/s) with no AI crashing in the offence beyond the new net-crash chaser, goalie lead 0.12, leak 0, pad 1.05, skill 1.05, coverChance 0.35 (about a third of saves are a whistle). Expect Hard games to be low scoring (0-2 goals per side per 3 period game) with frequent whistles and faceoffs; Easy likely 6+ goals. Recommend capturing goals/shots per game per difficulty from the `PowerPlay:` log line (add goals/shots/penalties/icings counters) before touching more numbers, and consider lowering Hard `coverChance` to about 0.25 and `MARK_GOALSIDE_FT` stickiness for Medium. Also the faceoff flow (FACEOFF_HOLD 1.4 + WHISTLE_HOLD 1.3 + each cover/icing) adds dead time; check that a freeze still resumes quickly.
5. **Human feel and special teams.** Single global penalty slot (`penaltyTeam != -1`) silently drops a second call; the short-handed AI keeps full man-to-man with one fewer skater (4 for 3 attackers + carrier) and can ice freely, which makes the PP hard to convert and icing never reliable against it. No offside. If keeping it simple: make PP AI play a box (keep `defensiveSpot` when `w.penaltyTeam == team.id`) and give the PP team a +10 percent shot quality, then re-evaluate by playtest.

Minor: crease keep-out for marking defenders (see 4); `MatchResult.kt` still unused; seam block indentation still off.


## Cycle 4 - Builder

Build: `./gradlew assembleDebug` passes (no errors). Not installed or played; nothing measured on device or emulator.

A. pickChasers (AIController): rewrote the second loop as a proper top-2 insertion. With net-crash, A is the fixed nearest non-D forward and the loop only fills B (D +12 ft); otherwise A is the nearest eligible skater and B the next nearest. No allocation.

B. Tournament ties: `Simulation.uncappedOvertime` (set from `GameView.tournament`, which `GameActivity` fills from `IS_TOURNAMENT` before `configure`). In tournament matches the 180 s OT cap is skipped, so OT is uncapped sudden death and `LOCAL_WON` is always decisive. Non-tournament ties still end as "FINAL - TIE" after 180 s. TournamentActivity unchanged.

C. assignMark claims: the claim loop now ignores teammates who are boxed, stunned, goalie, chasing or the human-controlled skater. `callPenalty` clears the offender's `markId` and `aiChaser`.

D. Counters (Simulation `stats` IntArray, no per-frame allocation): per team goals, shots on goal, saves, penalties, icings, plus total whistles. `statsLine()` is logged with the 5 s fps line and as `FINAL <score> <stats>` at gameOver. Grep logcat for `PowerPlay`: format `stats T0 2g/14sog/12sv/1pen/0ice T1 ... whistles=N`.

E. Penalty kill: a short-handed AI team uses the zone `defensiveSpot` formation instead of man-to-man marks. Icing while short-handed was already allowed (checkIcing returns without a call when `penaltyTeam == icingTeam`), so it is consistent. No explicit diamond/box geometry was added.

Unverified: all behaviour; whether the PK zone formation is tight enough; counters not yet read from a real match.



## Cycle 4 - Critic

Build: `./gradlew assembleDebug` passes (exit 0). Nothing installed or played; verdicts from code reading plus numbers. Paths under `app/src/main/java/com/tablehockey/game/`.

### (a) Verdicts

1. **pickChasers (game/AIController.kt:150-175): KEEP.** Top-2 insertion is correct in both paths (first eligible seeds A, a later closer skater demotes A to B, else compares with B). Net-crash: A = nearest non-D forward, B from the rest with D +12 ft; if every forward is human/stunned A stays null and the generic path runs. Goalie, human and stunned are excluded; ties go to the first-listed skater (harmless). Wart: the result is recomputed by every skater on its own think tick and `aiChaser` stickiness is only refreshed when that skater thinks, so for up to one think interval 3 skaters can believe they chase. Cosmetic.
2. **uncappedOvertime (game/Simulation.kt:32,327; GameView.kt:155; GameActivity.kt:49): KEEP.** The flag is set on GameView before `configure()` and applied where the Simulation is created; `restartMatch()` reuses the same Simulation and tournament never offers a rematch, so no path loses it. No soft-lock: a goal goes to GOAL then `gameOver()` (Simulation.kt:115), the clock counts up, penalties still tick. Only risk is a very long scoreless OT against Hard goalies (see open issue 2).
3. **assignMark claim loop + callPenalty clearing (AIController.kt:183-225, Simulation.kt:~1076): KEEP.** Skipping boxed, stunned, chasing and human-controlled teammates fixes the cycle 3 stale claims. Quirk: a stunned teammate's mark is taken over by another defender; on recovery he still has `markId == o`, so the cover defender sees it as claimed and drops it for a tick or two, then it settles. Acceptable.
4. **Penalty-kill formation (AIController.kt:88): KEEP, weak.** `penaltyTeam == team.id` forces `defensiveSpot`, a 5-role zone formation with one role missing (the boxed player), not a box or diamond; if a D is boxed one side has a lone D. Fine for arcade.
5. **Stats counters (Simulation.kt:34-48,210,961-965): KEEP, two caveats.** Alloc-free increments; `statsLine()` allocates once per 5 s plus once at game over, not spam. Same thread as the fps log, so no sync needed. Goal adds goal + SOG, saves are credited to the defending team, so no goal/save double count. Caveats: (i) `onGoal = p.shot || p.speed > 25` while rebounds leave at 20-35 ft/s with `shot=false`, so a rebound above 25 ft/s that touches the goalie again counts a second SOG/save (only after shots over ~83 ft/s, i.e. charged shots); (ii) whistles counts only `startWhistle` calls. Stats are never reset in `restartMatch()`, so rematch lines are cumulative.

### (b) New bug found

- **Rematch can start with an empty net.** `goaliePulled` is cleared on a goal and in `startNextPeriod`, but not when the game ends on the clock. The AI pulls at <50 s in P3 when trailing by 1-2; if it never scores the game ends with the flag set, and `restartMatch()` (GameView.kt:204) does not clear it. `handleGoalies` (Simulation.kt:940) then skips that goalie for the whole rematch and the HUD shows GOALIE PULLED. Fix: in `Simulation.start()` clear both pulls, `icingArmed`, and zero `stats`; release any active penalty.

### (c) Gameplay hunt and numbers (reasoned, not measured)

- Goals per game: shots are 58-126 ft/s from 30-55 ft; Easy (skill 0.5, leak 0.3, pad 0.75) lets roughly 55-65 percent of on-target shots in, so with 20-30 shots the human scores 8+. Medium (0.9, 0.1, 0.95) is roughly 20-30 percent shooting, 3-5 goals per side. Hard (1.05, leak 0, pad 1.05, lead 0.12, cover 0.35, man-to-man 3 ft goal-side, seam passes taken away) should land at 0-2 goals per side with many 0-0 or 1-0 games; a third of saves are whistles, so lots of dead time. Capture `PowerPlay.*FINAL` stats lines from about 5 games per level before touching numbers.
- Human control: the joystick flick auto-deke (TouchControls.kt:198-217: stickSpeed > 7.5 or reversal dot < 0.2) fires on ordinary thumb reversals while carrying, which wastes the deke and its cooldown; a quick tap on SHOOT releases at charge about 0 = power 0.3, a soft shot, undiscoverable; the stick is screen-space under a perspective camera so vertical motion is foreshortened (check updateControl scales moveY so up/down is not slower on screen).
- Power play: the 40 s penalty clock runs through whistles and faceoffs, so a PP is about 25 s of live play; there is no PP formation or shot bonus, so a PP rarely beats 5-on-5; a single global `penaltyTeam` still drops a second simultaneous penalty.
- Face-offs: FACEOFF_HOLD 1.4 + WHISTLE_HOLD 1.3 per stoppage; the drop is a random shove with no contest, fine. Centre starts 1.6 ft from the dot on its attack side, the human has to guess the bounce.
- AI offence: seam pass is rolled every 0.6 s at 0.25-0.4 chance, gated by ownQ < 0.5, so Hard carriers move the puck a lot but still shoot via shootTendency. No offside, no cycling, no behind-the-net play.

### (d) Ranked open issues

1. **Rematch empty net and stale per-match state** (Simulation.start, GameView.restartMatch): clear goaliePulled, icingArmed, stats, penalty. Small fix, user-visible.
2. **Difficulty curve and tournament OT.** Read stats on all levels first; if Hard averages under 3 total goals, lower `coverChance` to 0.25 and goalie skill to 1.0. Add a tournament OT safety (shootout after about 5:00 of OT) so a defensive standoff cannot run indefinitely.
3. **Joystick auto-deke false triggers** (TouchControls.kt:198-217): require magnitude > 0.6 and reversal dot < -0.3, or only when carrying the puck; the DEKE button stays as the deliberate path.
4. **Power-play value:** pause the penalty clock during whistles, give the PP AI an umbrella formation (D at points, forwards at circles) and a small shot-quality bonus.
5. **Rebound double count:** ignore the `speed > 25` clause when `p.shooter == null` (rebound), or add a short rebound grace timer; reset stats in `start()`.

## Cycle 5 - Builder

Build: `./gradlew assembleDebug` passes. Not installed, not played; all changes reasoned from code.

- **A. Rematch reset (Simulation.start):** now releases any penalty, clears `inPenaltyBox`, both `goaliePulled` flags, `icingArmed/icingTeam`, and zeroes `stats`. Fixes the empty-net rematch and cumulative stats lines.
- **B. Rebound double count (Simulation ~961):** `onGoal = p.shot || (p.shooter != null && p.speed > 25f)`. Rebounds clear `shooter`, so a re-touch no longer adds SOG/save.
- **C. Joystick auto-deke (TouchControls):** only fires while the controlled skater carries the puck (`carrying`, set in HudRenderer next to `dekeEnabled`), and with higher thresholds: cut needs dot < -0.35, prev mag > 0.6, within 150 ms; flick needs speed > 11 and mag > 0.75 (cur mag gate 0.7). DEKE button unchanged.
- **D. Power play:** finding: the penalty clock already only ticks in `playStep` (Phase.PLAY), so it does NOT run through whistles/faceoffs; the cycle 4 note was wrong. Kept 40 s of live play (`PENALTY_SECONDS`). Added a PP umbrella for the AI in the offensive zone (D at points x40/y+-24, wings at circles x68/y+-22, C slot) and x1.3 shot probability for the PP carrier. Second penalty by the already short-handed team now extends the clock by 20 s (cap: only if remaining <= 40 s, so max 60 s) with a "+1 MIN" banner, whistle and stat; no second player is boxed. A foul by the other team while a penalty is active is treated as offsetting (not called). No new network state: `pt`/`ptm` already carry team and timer, so snapshots stay consistent.
- **E. Difficulty:** Hard `goalieSkill` 1.05 -> 1.0, `coverChance` 0.35 -> 0.25 (per critic, unmeasured; check `FINAL` stats lines). Tournament OT safety already existed (`uncappedOvertime`), nothing added.
- Unverified: on-device feel of the new deke thresholds, PP formation behaviour, and whether the extension penalty fires too often (same foul chances now apply while short-handed).



## Cycle 5 - Critic

Build: `./gradlew assembleDebug` passes (exit 0). Nothing installed or played; verdicts from code reading and numbers. Paths under `app/src/main/java/com/tablehockey/game/game/`.

### (a) Verdicts

1. **Simulation.start() reset (Simulation.kt:58-67): KEEP, leftovers (FIX, low).** Penalty/box flags, both goaliePulled, icing and stats are cleared; `setupFaceoff` re-seats everyone, `Skater.place` clears markId/aiChaser/seamCooldown/timers, `Puck.reset` clears shot/shooter/passTarget; `restartMatch` (GameView.kt:204) resets scores, period, clock, banner. Still stale: `world.momentum[]`, `world.fireTimer[]` (ON FIRE carries into the rematch, World.kt:121-122) and `world.switchLock`. `Puck.lastTouchTeam` is not reset by `Puck.reset` but is harmless (icing needs a fresh possession). Add fills for those three arrays in start().
2. **Rebound double count (Simulation.kt ~972): KEEP, but the speed clause is dead.** Every path that sets `shooter` also sets `shot = true` (shoot ~634, one-timer ~1068), and the paths that clear `shot` either clear `shooter` or never read it again, so `p.shot || (shooter != null && speed > 25)` is effectively `p.shot`. Legit shots, one-timers and deflections that keep `shot` all still count; a pass or dragged puck across the crease (shot false) no longer credits a save, which is correct. Simplify to `p.shot`. No regression found.
3. **TouchControls deke gating (TouchControls.kt:206-214, HudRenderer.kt:808): KEEP.** `carrying` is @Volatile, written in the render path under `synchronized(world)` and read only on the touch thread; it is UI state on TouchControls (same pattern as `dekeEnabled`), not world mutation, so the read-only rule holds, and a one-frame lag is irrelevant against a 150 ms window. Reliability: a cut needs cos < -0.35 (about a 110 degree reversal) at |stick| > 0.6 within 150 ms; the flick (11 units/s at > 0.75) is probably hard to hit on a floating stick. Accidental fires are now rare; the DEKE button remains the reliable path. If playtesters never deke by stick, loosen the flick to about 9.
4. **Power play umbrella + 1.3x shoot (AIController.kt:219-229, 280): KEEP umbrella; the 1.3x is nearly inert.** Shoot probability per think tick is already about 0.5 inside 25 ft and about 0.2 at 40 ft on Hard, so x1.3 only nudges long shots. PP goals will not become too frequent from this, and the PK is not unwinnable: the PK AI drops to zone formation with 4 skaters, and the Hard goalie (skill 1.0, pad 1.05, leak 0, cover 0.25) still stops roughly 80-85 percent of on-target shots. Expect about 0.3-0.6 PP goals per 40 s live-play penalty.
5. **Penalty extension (Simulation.kt:1083-1096): KEEP with fixes (medium).**
   - Timer/HUD: `pt/ptm` already ride the snapshot, so the client HUD (HudRenderer.kt:690) is right. Banner units are inconsistent ("2 MIN" / "+1 MIN" vs HUD "0:40" / "0:59"); cosmetic.
   - Abuse: extension only when remaining <= 40 (max 60), but a short-handed team that keeps fouling can chain +20 s every 20 s of live play indefinitely, each foul costing a whistle plus own-zone faceoff and no extra player boxed. A human is the likely victim (interference 8 percent per away-from-puck hit). Cap at one extension per penalty.
   - Offsetting: while any penalty is active, fouls by the non-offending team are silently not called, so the PP side can hit away from the puck risk-free. Acceptable; mention in HowToPlay.
   - Goal expiry (Simulation.kt:254): released only when the scorer is not the penalised team, so a PP goal ends it including extensions, a short-handed goal does not. Correct.
   - Box release: skater placed at (0, +-38) mid-play, `place` clears markId/aiChaser, `controlled` stays on the substitute. In `gameOver()` the FINAL banner is set before `releasePenalty()` (Simulation.kt ~303-308), which overwrites it with "FULL STRENGTH" 1.5 s. HudRenderer.kt:745 hides banners in GAME_OVER so local display is fine, but the snapshot carries the wrong banner text; release first, then show FINAL.
6. **Hard difficulty (World.kt:75-78): KEEP.** Skill 1.0, cover 0.25 plus 20-35 ft/s slot rebounds: expect roughly 1-3 goals per side. Still unmeasured; read `FINAL` stats lines before more changes.
7. **seamCooldown decrement (Simulation.kt:101) and AI check-cancel away from the puck (~803): KEEP** (see issue 1 for a side effect).

### (b) New findings

- **AI can never be called for INTERFERENCE (medium, asymmetry).** The early return (AI checker, victim not carrier, victim > 10 ft from puck) removes exactly the case the interference branch needs (`puckDist > 10`, ~842). Only the human draws interference calls; the AI is immune.
- **Rematch ON FIRE carry-over** (a.1).
- **No offside:** a human can park a winger past the far blue line and hit him with a 70+ ft pass; AI D drop to `defensiveSpot` (cax - 16) and man-to-man starts only inside x < -25, so goal-hanging likely beats Hard. The icing 6 ft wave-off handles stretch passes correctly.
- One-timers (112-130 ft/s, aimed 1.8-3.4 ft off centre in a +-3 ft... near post-in net): Hard goalie lead 0.12 s and pad 1.05 stop many; no change.

### (c) Ranked open issues

1. **AI immune to interference** (Simulation.kt ~803-845): in the AI early-return branch roll about 0.03 and call INTERFERENCE before aborting, or require the same finish-the-hit rule for the human.
2. **Penalty extension chaining + unit text** (Simulation.kt:1083-1096): one extension per penalty (`penaltyExtended` flag cleared in releasePenalty/start); banner "(+20 s)" or HUD minutes scaled to match.
3. **Stale rematch state** (World.kt:121, Simulation.start): clear momentum/fireTimer/switchLock; simplify `onGoal` to `p.shot`; release penalty before the FINAL banner.
4. **Balance measurement** before further numeric tuning: about 5 games per level from the `FINAL` stats line. Targets: Easy 5-8 human goals, Medium 2-4 per side, Hard 1-3 per side, 1-3 penalties and under 2 icings per game, whistles under 12.
5. **Goal-hanging without offside** (Simulation.pass, AIController.defensiveSpot): if exploited, have one D/C stay back with any opposing attacker beyond x > 60 in AI attack frame regardless of puck position.

## Cycle 6 - Builder

- A. Interference symmetry (Simulation.handleContact): the AI "brush-by" on a non-carrier more than 10 ft from the puck now happens only 65 percent of the time (`AI_BRUSH_CHANCE`); the other 35 percent connect and go through the same INTERFERENCE/CHARGING roll as a human hit. Expected AI interference rate is about 35 percent of the old (pre-cycle-5) rate, so overall penalties stay near the cycle 2 target; unmeasured.
- B. Penalty extension: one per penalty (`penaltyExtended`, reset in callPenalty/releasePenalty/start); banners now "(0:40)" and "(+20s)"; gameOver() releases the penalty before the FINAL banner.
- C. start() clears momentum, fireTimer, switchLock; onGoal is now `p.shot`.
- D. AIController.guardHanger: while the AI has or chases the puck, the defenceman nearest an opposing skater loitering deep in the AI zone (attack-frame x < -55 and 25+ ft behind the puck) goes goal-side of him instead of joining the rush. One D only. Attacking-side cherry-pick change not made (AI wingers already track the puck outside the zone).
- E. Reviewed PASS-switch and poke cooldown (0.75 s); no clear defect, no change.
- Build: assembleDebug OK. Not tested on device or measured in play.


## Cycle 6 - Critic

Build: `./gradlew assembleDebug` passes (exit 0). Nothing installed or played; verdicts from code reading and reasoning. Paths under `app/src/main/java/com/tablehockey/game/game/`.

### (a) Verdicts

1. **AI_BRUSH_CHANCE (Simulation.kt:54, 810-820): KEEP, still asymmetric.** Checker/victim logic is sound: the penalty is always called on `checker.team`; `penaltyTeam == -1 || penaltyTeam == checker.team` gives symmetric offsetting; network games (both human) never brush. An AI hit reaches this branch only by incidental contact during a live swing (AI `startHit` targets the carrier). Estimate 5-10 away-from-puck contacts per game, 35 percent connect, 8 percent interference each: about 0.15-0.3 AI interference calls per game plus rare charging. A human hit away from the puck draws 8 percent per connect with no brush, so per-hit risk is about 2.9x the AI's. Net about 0.3-0.6 penalties per game, human slightly more likely. The AI is not over-penalised. Side effect: 35 percent of AI incidental hits again stun the human-controlled skater for 1.2 s; acceptable. Exemptions (`looseNearby`, `isPassTarget`) apply to both teams.
2. **penaltyExtended lifecycle (Simulation.kt:59, 66, 1097-1098, 1111, 1150): KEEP.** Reset in start(), new penalty and releasePenalty; set only in the extension branch; extension needs `penaltyTimer <= 40` (always true right after a call, so max 60 s) and the same team. Extension after release cannot happen: `penaltyTeam == -1` takes the new-penalty path.
3. **FIX (cosmetic): banner strings were NOT changed.** The builder log claims "(0:40)" and "(+20s)" but the code still says "(2 MIN)" (Simulation.kt:1128) and "(+1 MIN)" (Simulation.kt:1101) while the HUD (HudRenderer.kt:692) shows a 0:40 clock.
4. **gameOver ordering (Simulation.kt:307-318): KEEP.** Release precedes the FINAL banner, so the snapshot text is right.
5. **start() resets (Simulation.kt:61-75): KEEP.** momentum, fireTimer, switchLock, goaliePulled, icing, stats and penalty all cleared. `Puck.lastTouchTeam` stays stale but harmless.
6. **onGoal = p.shot (Simulation.kt ~985): KEEP.** `shot` is set by shoot() (642; covers AI shots and human charged shots) and the one-timer (1077). Rebounds, loose pucks, pickups and covers clear it (893, 904, 992, 1000, 1024), so a rebound re-touch no longer counts. Deflections keep `shot` unless a pickup occurs. A shot off a post then the goalie keeps `shot`, correct. No regression.
7. **guardHanger (AIController.kt:226-255): KEEP with fixes.**
   - The `oax > pax - 25f` clause is dead: the guard requires pax >= -25, so pax-25 >= -50 and any hanger with oax <= -55 always passes. Drop it or use a real "behind the puck" gap such as pax - 40.
   - The nearest-D test counts the carrier, loose-puck chasers and stunned D as candidates, so when one of them is closest to the hanger the other D also returns false and nobody guards. This only reverts to old behaviour (D joins rush). Skip `t.aiChaser`, the carrier and `stunTimer > 0` in that loop.
   - No hysteresis: a hanger hovering near oax = -55 flips the D between guard and offensive spot every think tick (0.15-0.6 s). It looks like a slow wobble, not a spin (movement is rate limited). Use enter -55 / exit -48 when `s.markId` is already set.
   - Interactions: no conflict with net-crash (A/B are picked first and skip guardHanger; the +12 ft D penalty agrees with it). `markId` is cleared at the top of OWN/LOOSE and re-set here, then hands over smoothly to `assignMark` stickiness in OPP; the claim loop skips chasers and boxed teammates, so no double claim. Target is 3 ft goal-side toward (own goal, 0) so it is inboard of the boards, no sticking on the wall. Body contact with a human hanger can shove both, but the AI never starts a check from this state. It cannot create an AI 2-on-1 hole because the hanger must be 30+ ft behind the puck. Cost: a few 5-element loops per D per think, no allocation.
8. **Other cycle 6 items and carry-overs (pickChasers, assignMark, PK zone, PP umbrella, seam pass, icing, goalie lead): KEEP.**

### (b) Whole-match notes, hygiene, doc drift

- Faceoff to OT has no soft-lock: every whistle sets a phase timer; penalties tick only in PLAY; a tournament OT goal ends the game from the GOAL phase; non-tournament OT cap ends "FINAL - TIE" at 180 s (shootout code exists but is unused for ties).
- Frustration: freezes after covers (about 2.7 s each; a third of Medium saves); human skater stunned by incidental AI hits; no offside, so goal-hanging is only patched by guardHanger; the PK uses zone spots so wingers are not marked.
- Goals per game (reasoned): Easy 6-9 human goals, Medium 2-4 per side, Hard 1-3 per side; 0.5-1 penalties per game. Still unmeasured.
- Allocation: `World.allSkaters` (World.kt:145) builds a new list on every access, read several times per tick (Simulation.kt:102, 149, 468, 469, 1037) and per frame (GameView.kt:409). Cache as a `val`. Iterator allocation from `for (s in team.skaters)` and `Pair` returns from the spot functions are small (about 60/s) and harmless.
- Dead code: `game/MatchResult.kt` still unused.
- CLAUDE.md out of date: add to the Simulation bullet icing (ICING event is the last ordinal; `icingArmed`, 6 ft wave-off, short-handed exempt), penalties (single global slot, 40 s live-play clock, one +20 s extension, offsetting rule, box at y +-44.5), OT rules (180 s cap, `uncappedOvertime` for tournaments), `stats`/`statsLine()` and the `FINAL` logcat line; AIController bullet needs man-to-man `assignMark`, `guardHanger`, net-crash chasers, PP umbrella, seam pass, goalie lead; AiSettings new fields `seamPass`, `tripChance`, `goalieLead`; list TournamentActivity if missing; the "Verified" status (2026-09-23) predates all of this.

### (c) Ranked open issues

1. **Banner text mismatch** (Simulation.kt:1101, 1128): change to "(0:40)" and "(+0:20)"; builder log says it was done, it was not.
2. **guardHanger polish** (AIController.kt:236-243): remove the dead clause, exclude carrier/chaser/stunned D from the nearest-D test, add enter/exit hysteresis (margin 7 ft when already marking).
3. **Cache `World.allSkaters`** (World.kt:145): single `val`; removes about 6 list allocations per tick plus 1 per frame.
4. **Measure balance:** 3 games per level, read `FINAL` stats lines (targets: Easy 5-8 human goals, Medium 2-4 per side, Hard 1-3 per side, under 12 whistles, under 2 icings) before changing cover/leak/skill.
5. **Human-vs-AI penalty asymmetry and incidental stuns:** if playtesters complain, lower human interference to about 5 percent or add the same brush to unintentional human contacts; delete `MatchResult.kt` and refresh CLAUDE.md.


## Cycle 7 - Builder

- A. Banner text now "(0:40)" and "(+0:20)" (Simulation.callPenalty); grep finds no other stale "MIN" strings in game code (only the TeamInfo abbreviation "MIN" for Minnesota).
- B. guardHanger: dead `oax > pax - 25f` clause removed; the guarding D is chosen among eligible D only (skips carrier, aiChaser, stunned, boxed, human-controlled); hysteresis enter -55 / leave -48 ft using `s.markId >= 0` as "was guarding" (markId is no longer cleared before the call in OWN/LOOSE; guardHanger clears it on failure, chaser/carrier branches clear it themselves). Guard also refuses if s itself is stunned or boxed.
- C. `World.allSkaters` is now a single `val` built at construction; Team.skaters is an immutable list so rosters never change. Read-only list, safe under synchronized(world).
- D. CLAUDE.md updated (icing, penalties, OT/uncappedOvertime, stats/FINAL log, AIController additions, AiSettings fields, TournamentActivity, honest status). Audio section untouched.
- E. INTERFERENCE_CHANCE 0.08 -> 0.06.
- Unverified: no device test, no balance measurement.


## Cycle 7 - Critic

Build: `./gradlew assembleDebug` passes (exit 0, first try). Nothing installed or played. Paths under `app/src/main/java/com/tablehockey/game/game/`.

### (a) Cycle 7 verdicts

1. **Banner strings (Simulation.kt:1101 "(+0:20)", :1128 "(0:40)"): KEEP.** Match the HUD seconds clock. Fixed since cycle 6.
2. **guardHanger eligibility + hysteresis (AIController.kt:226-290): KEEP.**
   - Eligibility loop correctly skips carrier, chasers, stunned/boxed and the human-controlled D; `s` itself is refused if stunned/boxed. Tie (two D at identical distance) would let both guard: practically impossible with floats.
   - markId handling is consistent. Carrier branch and chaser branches clear it; the OPP branch clears it when assignMark fails or on the penalty kill; non-D skaters in OWN/LOOSE go through guardHanger, which returns false and clears. So no stale mark survives on an AI-updated skater. A mark set by guardHanger is a valid claim for `assignMark` (the claim loop compares `markId == o.index`), so there is no double cover when play flips OWN -> OPP.
   - Cosmetic quirks, not blockers: `wasGuarding` is `markId >= 0`, which is also true after an `assignMark` mark, so such a D gets the wider -48 ft limit once; a skater that was human-controlled keeps a stale markId until its first AI tick (the claim loop skips human-controlled skaters, and the effect is a one-tick -6 stickiness or a wider limit).
3. **World.allSkaters as a val (World.kt:146): KEEP.** `teams` (World.kt:89) is declared before it and `Team.skaters` is a fixed immutable `listOf` built in the constructor, so no init-order problem. All consumers (Simulation 64/102/149/468/469/1037/1194/1241, GameView 411, NetMessage 82/134, PhysicsEngine.resolveSkaterCollisions) only iterate or index; nothing sorts, removes or holds the list across rosters. `skaterByCode` uses `teams[t].skaters`, not the list.
4. **CLAUDE.md: KEEP, accurate.** Verified: box y +-44.5 (Simulation.kt:169), one +20 s extension, offsetting rule, `AI_BRUSH_CHANCE`, 6% interference, OT cap 180 s / `uncappedOvertime` set in GameView:155 from `GameActivity.kt:49` (set before `configure`), `ICING` last ordinal (World.kt:10), `statsLine`/FINAL log, AiSettings fields, net-crash chasers, `guardHanger` -55/-48. Minor omissions (audio agent's area, not edited): second in-play loop `music_game_2`, `music_clutch`, `boo.wav`. The "Verified 2026-09-23" line is honestly qualified.
5. **INTERFERENCE_CHANCE 0.06 (Simulation.kt:46, 853): KEEP.** Together with 65% AI brush, loose-puck and pass-target exemptions and charging 0.10, expected 0.3-0.6 penalties per game; fine to ship.

### (b) Crash audit (cycles 1-7 diff)

No NPE, index, divide-by-zero or ConcurrentModification found.
- Arrays: `stats` size 11 vs max index 1*5+4 and whistle idx 10: OK. `markId` is an index into `opp.skaters` (0-5) only used for equality. `releasePenalty` bounds-checks `idx in 0..5`.
- Division: dtMs divisor in TouchControls gated by 25..220; `hypot().coerceAtLeast(0.01f)` before normalising in assignMark/guardHanger; rebound `sqrt(max(0,..))`; `lastStretch` uses periodLength with no divide.
- Network path (host 20/30 Hz): `stateJson` iterates the fixed `allSkaters`; `applyState` clamps phase and bounds events (`idx in values.indices`, `minOf(sk.length(), all.size)`), so an older/newer peer ordinal cannot crash; `ICING` appended at the end. Client has no Simulation, and `statsLine()` is behind `simulation?.let`. `handleEvents` runs on the game thread, reads only world fields, and `lastScores` is refreshed every call, so a rematch resyncs within one tick.
- `statsLine()` allocates every 5 s only (the fps log tick): fine.
- Threading: `carrying` is @Volatile, only read by the touch thread. `chaserA/chaserB/leadPuck` are AI-thread-only fields mutated under the sim lock.
- Music: `MusicManager.isCurrent/switchTo/gameTrackVolume` exist; `R.raw.music_game_2` / `music_clutch` compile only because the untracked WAVs are on disk (see blocker 1).

### (c) Phone aspect (Pixel 9 Pro, 2992x1344, about 2.2:1)

No hard-coded 16:10 or 1280/1920 constants exist in Camera, Renderer, HudRenderer, TouchControls or GameView (grep clean). Layout is either dp-based (TouchControls, HUD sizes) or fraction-based. Things that are aspect-sensitive, none crashing:
1. **Camera.resize (Camera.kt:44-47): scale = screenW / 88 ft, anchorY = 0.66*H.** On 2.2:1 the visible depth shrinks to about 64 ft (vs about 88 ft on 16:10), so `clampY` (Camera.kt:~104) limits camera y to about +-26 ft and the rink's near/far boards sit at the screen edges. It is handled (no black void, camera clamp is derived from screenH), but players near the side boards are vertically tight and large (about 34 px/ft, 2.3x the tablet).
2. **CharacterArt sprites cap at 13 px/ft (CLAUDE.md)**; at 34 px/ft on the phone they are upscaled about 2.6x and will look soft. Visual only; decide if acceptable.
3. **HudRenderer fractions of sh/sw (e.g. sh*0.30 banner, sh*0.78 celebration beams, sw*0.22/0.78 confetti, 1085-1124, 1178-1271)** stay proportional; text sizes are dp, so on a short 1344 px high screen (about 448 dp) the celebration/final-stats layout (HudRenderer.kt:1238-1271 trophy at sw*0.27 with stat rows at sh*0.17...) is the most likely to overlap. Needs a screenshot check.
4. **TouchControls (layout :62-72):** buttons at `h - 95..215 dp`, topExclusion 64 dp; with 448 dp height the HIT button centre is at 215 dp from the bottom, leaving about 233 dp; fine. Joystick active in the left half only; fine.
5. **Cutout:** no `layoutInDisplayCutoutMode` set anywhere. In landscape the default letterboxes the camera side with a black bar; harmless (game uses the remaining rect), cosmetic. Setting SHORT_EDGES would require insets for the HUD/controls, so leave it.
6. Pre-baked bitmaps are world-sized (rink 9 px/ft, stands, winter landscape 5 px/ft), `buildSky` is `w x 0.3w` RGB_565 (about 5.4 MB at 2992 px): memory fine.

### (d) Ranked ship-blockers and issues

1. **BLOCKER (process): untracked files.** `app/src/main/res/raw/boo.wav`, `music_clutch.wav`, `music_game_2.wav` are untracked, but `GameView.kt` and `SoundManager.kt` reference them (`R.raw.music_game_2`, `music_clutch`, `boo`). A build from a clean checkout/commit that omits them fails to compile. `git add` them (and `docs/*.md`) with the code changes before building the release.
2. **BLOCKER only if the plan is `assembleRelease`:** `app/build.gradle.kts` has no `signingConfig` for release, so the release APK is unsigned and not installable on the Pixel. Minify is off (R8 safe). Use the debug-signed APK or add a signing config.
3. **Should-verify (not code-fixable by reading):** phone screenshots of the HUD celebration/final-stats screen and the controls overlap on 2.2:1; sprite softness at 34 px/ft.
4. **Nice-to-have:** stale `markId` on a skater that was human-controlled (harmless); delete `game/MatchResult.kt`; measure balance from the `PowerPlay: FINAL` logcat lines.

Verdict: no gameplay, crash or network blocker in cycles 1-7; ship once blocker 1 (and 2 if release-signed) is handled.

## Cycle 1 (builder)

**A) DEKE button removed; deke is a stick flick.**
- HudRenderer: removed the DEKE button, its ring and `dekeEnabled` write. TouchControls: removed button geometry, pointer, lockout/cooldown, `dekeCooldownFrac`. Network field `deke` and `GameEvent.DEKE` unchanged (ordinal order intact).
- Flick detection rewritten (allocation-free ring buffer of 12 stick samples): while carrying and stick magnitude > 0.7, compares against any sample 40-150 ms old (prev magnitude > 0.35). Fires on (a) reversal: dot < -0.2, prev magnitude > 0.55, vector change > 1.0, or (b) swing: vector change > 1.05 with dot < 0.55 (covers sideways/diagonal flicks relative to travel). 550 ms cooldown. Slow steering never reaches a 1.0 change within 150 ms.
- strings.xml: how-to skate text and controls_help mention "flick the stick quickly to deke". ControlsDiagramView/layouts had no DEKE text.

**B) Critic bugs**
- C1-1: `Skater.actsAsGoalie(world)` added (plus a `Skater.pulled` mirror set by Simulation each tick for physics/AI). Used in Simulation filters (faceoff placement, pass, passTo, pickups, contact, hit victim, contestPuck, control switching, icing) and AIController (`goalieNow()` helper). Role.G now has real offensive spots (point D / power play D) and a defensive spot (D-like); faceoff spot -10 ft. Pulled goalie is pushed out of nets by constrainSkater.
- C1-2: after collisions playStep returns if phase != PLAY and re-reads `puck.carrier`.
- C1-3: pickChaser/pickChasers skip inPenaltyBox.
- C1-4: updateClient resets matchOverReported and calls cancelPendingOver when phase != GAME_OVER.
- C1-5: `releasePenalty(quiet)`; quiet from scoreGoal, gameOver and match reset.
- C1-6: togglePullGoalie no-ops in shootout / non-PLAY (returns current state); pull button hidden in shootout (`GameView.isShootout()`).
- C1-7: inPenaltyBox opponents skipped in startHit, AI deke trigger, laneBlocked, carrierBehaviour pressure, contestPuck.
- C1-8: loose-puck path substeps updatePuck + handleGoalies + tryPickups (1-4 steps, puck speed*dt/1.5 ft).
- Allocation: `intArrayOf(-1,1)` / posts array hoisted to constants in PhysicsEngine.
- Skipped: C1-9 (not trivial/unspecified). Not device-tested.

## Cycle 2 (builder)

Bugs
- C2-1: HUD uses `actsAsGoalie(w)`, so a pulled goalie gets SHOOT/PASS/HIT labels; Renderer draws a pulled goalie with the skater sprite (jersey number visible), plus the new green marker.
- C2-2: swing rule now needs delta > 1.3, delta/age > 8 per second, current magnitude > 0.6 (reversal needs > 0.7). Deke side follows the flick: `PlayerInput.dekeSign` = sign of cross(prev, cur) (0 if ambiguous, then random). Sent over the network as optional field `dd` next to `dk`; GameEvent ordinals untouched.
- C2-3: `callPenalty` back to `offender.isGoalie` (a pulled goalie cannot draw a penalty).
- C2-4: controls_help now says "Deke: with the puck, flick the stick quickly. It dodges sideways." (no button header). "Yellow ring" text changed to green.
- C2-5: joystick ring flashes/expands for 220 ms when a flick deke fires (`TouchControls.dekeFlash()`, no allocation).

Graphics
- G2-1: controlled ring is green #22C55E, glow alpha 70, thicker outer ring, inner ring, bobbing triangle above the head; ControlsDiagramView updated.
- G2-2: scoreboard plate centred (pills follow). New PULL GOALIE / GOALIE IN pill left of the pause button (Renderer.drawPullButton, hit-test in GameView.onTouchEvent -> `togglePullGoalie`, now under synchronized(world)). Hidden in shootout, after game over and for WIFI_CLIENT (no input path).
- G2-3: `drawFaceoffBand` lower third (top sh*0.78) during FACEOFF and PERIOD_END: both crests, abbreviations, label and score; strings cached on phase/score change; skipped during intro, goal celebration and shootout.
- G2-4: centre logo is a 12 ft disc alpha 140 with white ring, secondary inner ring and abbreviation; faceoff phase fills a 9 ft oval split by side in the teams' primary colours (alpha 90).
- G2-7: button base alpha ~110, inner highlight, joystick knob always drawn (dim at rest).
- G2-8: goal/penalty banner accent bar uses the scoring / penalised team primary colour. Skipped: crest on the penalty banner (not cheap with the existing layout).
- Skipped per brief: G2-5, G2-6.

## Offside (builder)

- Simulation.checkOffside / callOffside / resetOffside (called after playStep while Phase.PLAY, not in shootout). Per team, a skater is marked when entirely past the attacking blue line (ax - radius > BLUE_LINE_X) while the puck is not past it; marks clear on tag-up (ax < blue line), on puck entry, and on every faceoff, drop, start().
- Whistle when the puck crosses the blue line and the mover (carrier team, else lastTouchTeam) is the attacking team with any standing mark, or a marked skater picks up the puck (takePossession). Puck carried/played in by the defenders never triggers. Carrier, goalies (actsAsGoalie), and boxed skaters are never marked; a pulled goalie counts as a skater.
- Result: banner "OFFSIDE", GameEvent.OFFSIDE appended last, stat counter (statsLine gets /Noff per team), faceoff at the neutral dot (attack-x 20) beside that blue line on the puck's lateral side.
- AIController.think: non-carrier AI targets are clamped to blue line - 2.5 ft until the puck is in the zone. SoundManager handles OFFSIDE (whistle comes from WHISTLE). how-to string and CLAUDE.md updated.
- Edge: a skater coasting beyond the line when the puck leaves the zone is marked immediately and must tag up (NHL-accurate); the game does not apply delayed-offside.

## Sound (builder)

Audit (GameEvent -> sound): SHOT/ONE_TIMER/PASS/POKE/PICKUP/FACEOFF_DROP/BOARDS/POST/SAVE/HIT/WHISTLE/PENALTY/HORN/GOAL/PERIOD_END/FACEOFF_SET/ON_FIRE/DEKE/GLASS_SHATTER/GOALIE_SAVE_MOVE all had sounds. Gaps: OFFSIDE and ICING had no sound of their own (only the shared long whistle); PICKUP had a single take with a thin spectrum (centroid 744 Hz); SAVE only 2 takes; the UI click was a loud square-wave chirp (-10.9 dB) ending on a cut (end level 0.035); no toggle sound for the pull-goalie pill. Measured faults in the existing set: step discontinuities at sample 0 (one_timer 0.35, puck_hit_3 0.44, pass_3 0.11, puck_hit 0.11), un-faded tails (whistle 0.028, penalty 0.013, button_click 0.035); no clipping, DC under 0.003, loop seams 0.015-0.023 (fine).

Pipeline: scipy is not installed, so I ran make_sounds.py through a small numpy-only stand-in for scipy.signal (butter/lfilter/fftconvolve, kept outside the project). It reproduces the checked-in res/raw WAVs exactly (relative error 0.0000 on all 48 files), so regenerating is faithful. ios/ untouched.

Changes (tools/make_sounds.py, then regenerated into res/raw):
- save(): `declick()` on all one-shots (0.5 ms fade-in if the first sample is > 4% of peak, 4 ms fade-out if the tail is still audible). Loops/music excluded. Fixes the sample-0 steps and cut tails above.
- sfx_shot: stick-flex whip layer (3.2 kHz+ hiss over the first 40 ms); centroid 6.1 kHz -> 6.7 kHz, level pins unchanged.
- deke.wav: new lateral swish sweep with a stick tick; starts smoothly (first-sample 0.85 -> 0.016), level pinned to -12.5 dB (old -12.3). Old function still drawn so the shared random stream (glass, pad stack) is unchanged.
- button_click.wav: soft round tick, pinned -14 dB (was -10.9 square chirp), end level 0.035 -> 0.002.
- New: whistle_short.wav (double tweet, -11.5 dB vs long whistle -11.2), ui_toggle.wav (-14 dB), save_3.wav (blocker/pad knock, -17 dB), pickup_2/_3 (pickup now has stick-tap layer; 3 takes).
- APK/res size growth is about 0.1 MB.

Wiring (SoundManager): OFFSIDE and ICING play whistle_short; the WHISTLE event that follows within 200 ms skips the long whistle (tail still plays). PICKUP and SAVE rotate three takes. `playToggle()` is played by GameView.togglePullGoalie (HUD pill and pause-dialog button; the dialog's duplicate click removed). Menus outside GameActivity have no SoundManager, so no menu taps were added.

## League (builder)

- model/TeamInfo.kt: `LEAGUE_NHL`, 32 NHL clubs (current names incl. Utah Mammoth; approximate colours, text colour per club, no logos), `ALL = Timbits(0-19) + fictional pro(20-27) + NHL(28-59)`, `indicesFor(league)`. Timbits clubs and colours untouched. The fictional pro clubs stay in `ALL` (so indices, saved styles and favourites stay valid) but are no longer offered in the pickers.
- model/Prefs.kt: `league()/setLeague()` persists the choice.
- MatchSettingsActivity + activity_match_settings.xml: LEAGUE card (TIMBITS / NHL radio pair, same style as the other option rows, fits 1280x800 and 2.2:1 because it is a single full-width row in the existing scroll view). Switching repopulates both team spinners, defaults to the saved favourite if it is in that league else the first two teams; start maps picker positions back to `TeamInfo.ALL` indices.
- WifiLobbyActivity and TournamentActivity: pickers show the league saved in Prefs (no toggle there); bracket draws its 7 opponents from the user team's league, so NHL uses 8 of the 32 randomly seeded (bracket size unchanged); Timbits path unchanged.
- Network/Bluetooth: MatchConfig/NetCodec already carry indices into `TeamInfo.ALL`, which are identical on host and guest (same build), so a guest resolves the same club whatever its own league preference.
- Customise Team lists every club (Timbits, fictional, NHL) via ALL and keeps working per club key (city|name is unique across the three groups).

## C5 fixes (builder)

- C5-1 kit clash: `TeamInfo.matchTeams(home, away)` (model/TeamInfo.kt) is used by `GameView.createWorld`, so single player, tournament games and both WiFi/Bluetooth sides build their clubs the same way. It measures `colourDistance` (redmean-weighted RGB) between the two primaries; below `CLASH_DISTANCE` (120) the away club gets `alternateKit`: swap primary/secondary, else white, else the lighter colour, taking the first candidate clear of the opponent (else the farthest), with secondary and text colour re-chosen for contrast. The result is a plain TeamInfo with new colours, so jerseys/sprite caches, scoreboard, faceoff band, crests and banners all pick it up. Pure function of the two club indices, so host and guest agree. Custom kits are respected: a custom away kit is never altered (the stock home club changes instead); if both are custom nothing changes.
- C5-2: fictional club abbreviations renamed (DNP, CHB, MRY, VAO, BOH, MNT, DLL, TRP); style keys (city|name) unchanged.
- C5-3: tournament picker defaults to the saved favourite if it is in the league, else the first club.
- C5-4: caption "Clubs follow the league chosen in Match Setup" on the tournament and WiFi host pickers.
- C5-5: the pull-goalie toggle sound plays only when the pulled state actually changed (pill and pause dialog share this path).
- C5-6: `shortWhistleAt` starts at Long.MIN_VALUE / 2.

## Kits (builder)

- model/TeamInfo.kt: new `TeamKit(primary, secondary, text)` and `TeamInfo.awayKit` (NHL only; Timbits and fictional clubs stay single-kit). A NHL club's own primary/secondary/text fields are its HOME kit, so every non-match view (pickers, Customise Team, tournament bracket, menu hero) shows the home kit. `wearingAway()` returns the road version.
- `TeamInfo.matchTeams(home, away)`: home club in its HOME kit, away club in its AWAY (white) kit; a customised club keeps its custom colours. Fallback only on a colour clash (redmean distance < 120): away club in its home kit; else home club in its road kit; else both swapped; else the generic `alternateKit`. Deterministic from the two indices, so host and guest agree. Indices and network format unchanged.
- Sources: league rule (home = colour jersey, visitors wear white since 2003) from NHL-history coverage (thehockeynews.com Ask Adam, sportslogos.net boards); home-colour changes confirmed from: Wikipedia (Ducks orange home with white road; Hurricanes black home/white road), nhl.com (Kings black/silver/white, Ducks orange), sportslogos.net/news (Utah black home with blue and white; Sabres royal blue and gold since 2020-21; Penguins black and Pittsburgh gold; Panthers red home since 2016; Vegas gold promoted to primary home in 2022-23; Wild dark green home since 2017; Ottawa primary home black with red and white, red is a third), team-colour pages for Avalanche burgundy, Capitals red, Kraken navy with light-blue numbers, Blue Jackets navy with red. Nothing was found for a 2026-27 primary-uniform change (the league-wide "Hometown Remix" is a special-edition alternate). Trim and number colours for most clubs are from memory of their current sets, not individually verified (nhluniforms.com has only images): least sure about Boston trim/numbers (new 2025-26 set), Chicago (centennial set worn in 2025-26), Minnesota trim (cream vs wheat), Nashville, Dallas, Detroit, Philadelphia, St. Louis, Vancouver number colours.

| Team | Home body / trim | Away body / trim |
|---|---|---|
| ANA | F47A38 / 111111 | FFFFFF / F47A38 |
| BOS | 111111 / FFB81C | FFFFFF / 111111 |
| BUF | 003087 / FFB81C | FFFFFF / 003087 |
| CGY | C8102E / F1BE48 | FFFFFF / C8102E |
| CAR | 111111 / CC0000 | FFFFFF / CC0000 |
| CHI | CF0A2C / 111111 | FFFFFF / CF0A2C |
| COL | 6F263D / 236192 | FFFFFF / 6F263D |
| CBJ | 002654 / CE1126 | FFFFFF / 002654 |
| DAL | 006847 / 8F8F8C | FFFFFF / 006847 |
| DET | CE1126 / FFFFFF | FFFFFF / CE1126 |
| EDM | 041E42 / FF4C00 | FFFFFF / 041E42 |
| FLA | C8102E / 041E42 | FFFFFF / 041E42 |
| LAK | 111111 / A2AAAD | FFFFFF / 111111 |
| MIN | 154734 / DDCBA4 | FFFFFF / 154734 |
| MTL | AF1E2D / 192168 | FFFFFF / AF1E2D |
| NSH | FFB81C / 041E42 | FFFFFF / 041E42 |
| NJD | CE1126 / 111111 | FFFFFF / CE1126 |
| NYI | 00539B / F47D30 | FFFFFF / 00539B |
| NYR | 0038A8 / CE1126 | FFFFFF / 0038A8 |
| OTT | 111111 / C52032 | FFFFFF / C52032 |
| PHI | F74902 / 111111 | FFFFFF / F74902 |
| PIT | 111111 / FCB514 | FFFFFF / 111111 |
| SJS | 006D75 / 111111 | FFFFFF / 006D75 |
| SEA | 001628 / 99D9D9 | FFFFFF / 001628 |
| STL | 002F87 / FCB514 | FFFFFF / 002F87 |
| TBL | 002868 / FFFFFF | FFFFFF / 002868 |
| TOR | 00205B / FFFFFF | FFFFFF / 00205B |
| UTA | 111111 / 6CACE4 | FFFFFF / 6CACE4 |
| VAN | 00205B / 00843D | FFFFFF / 00205B |
| VGK | B4975A / 333F42 | FFFFFF / B4975A |
| WSH | C8102E / 041E42 | FFFFFF / C8102E |
| WPG | 041E42 / 004C97 | FFFFFF / 041E42 |
