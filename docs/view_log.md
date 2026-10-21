# View log

## View Cycle 1 - Builder

### Goal
A second, end-on "long view" (like Tape to Tape): rink long axis up the screen, far net near the top, home team always
at the bottom attacking up (also on WiFi/Bluetooth guests). The original side view stays available; the player chooses.

### Design decisions
- World, simulation, AI, physics and network protocol are unchanged. Only the view layer branches on `Camera.long`
  (`enum ViewMode { SIDE, LONG }`, `Prefs.viewMode`, default LONG, key `view_mode`).
- Mapping (LONG): pure 90-degree rotation, no mirroring: world +x -> screen UP (far), world +y -> screen RIGHT.
- LONG camera is a real pinhole: pitch T, distance D = cos(T)/0.0065, `den = D + Y cos T - z sin T`,
  `sx = W/2 + u*scale*D/den`, `sy = anchorY - (Y sin T + z cos T)*scale*D/den` (Y = wx - cam.x, u = wy). `resize()`
  picks T (0.5..0.97 sin) so ~87 ft of width fits the bottom row and ~80 ft of length is visible; scale follows the
  width (phone 2856x1280 gives about 30 px/ft at the focus, tablet about 12-14). Camera x follows the puck, y stays 0; x is clamped
  so the far end boards stay below 20% of the screen height and the near end boards above the bottom edge.
- Unified API: `px/py(wx, wy, z)` (z = true 3D lift, so walls, nets, lamp, glass have correct faces),
  `ppfAt(wx, wy)`, `vkAt(wx, wy)`, `depthAt`. SIDE implements the old formulas through the same API (identical output).
- Facing: sprite facing index uses `world angle - 90 deg` in LONG (`CharacterArt.facingIndexWorld`). Sprites bake with a
  per-mode ground squash (`CharacterArt(scale, vk)`). Depth sort key is -x (LONG) or y (SIDE).
- Input: `TouchControls.longView` rotates the stick (moveX = -dy, moveY = dx) for every team; deke detection stays in
  screen space (rotation keeps handedness). Sound panning uses world y in LONG.
- Rink bake: LONG warps only the visible x-range of the baked bitmap with `drawVertices` + BitmapShader (40x10 mesh);
  SIDE keeps the original `drawBitmapMesh`. Centre-ice logo text is rotated so it reads left to right in LONG.
- Walls/rake visibility: LONG uses the inward normal vs the real camera position; SIDE keeps the old test. Far-end
  tiers sit behind the far end (LONG) or far side (SIDE); LONG draws tiers then the raked bowl, SIDE the original order.
- Nets: LONG draws the far net before the players and the near net after them (its netting is in front of its goalie).
- Winter Pond landscape: trees/cabin rotated, mountains moved to the far end in LONG.

### Files changed
Camera.kt, Renderer.kt, WorldArt.kt, HudRenderer.kt (project/ellipse, scorer cone), CharacterArt.kt, TouchControls.kt,
GameView.kt (view mode at configure, camera follow args, skate pan), SoundManager.kt (pan axis), Prefs.kt,
MatchSettingsActivity.kt, activity_match_settings.xml, strings.xml, CLAUDE.md.

### Verified (Android emulator Pixel_9_Pro 2856x1280; the physical Pixel has a different signing key so a reinstall was not attempted)
LONG: faceoff, play in own zone, goal conceded with celebration, far end (temporary camera pin), HUD and controls,
stick up/right moves the home skater up/right. SIDE: selected via Match Settings, looks as before (net, boards, stands).
Screenshots in the session scratchpad (L1, L2, j2, far, S1, S2).
Note: the emulator's EGL driver SIGSEGVs in RenderThread when GameActivity is destroyed (Quit); this is the emulator's
eglDestroySurface, not game code.

### Known issues / left
- Side stands in LONG look streaky (crowd texture runs along the perimeter, which is vertical on screen); could bake a
  dedicated side-stand texture.
- Near end below the boards is plain black; phone-width sprites (13 px/ft cap) are upscaled about 2x and slightly soft.
- Ad panel text on the side boards is sideways/compressed (physically correct but hard to read).
- Not exercised: Tournament/WiFi/Bluetooth flows (they read the same saved preference), shootout controls in LONG,
  the Winter Pond landscape orientation in LONG beyond a first look (pond looked fine), HowToPlay diagram still shows the old orientation.

## View Cycle 1 - Critic

Build: `assembleDebug` passes. Run on Pixel 9 Pro emulator (2856x1280, LONG, Indoor Stadium, Timbits): menu -> Match Setup shows the Long/Side radio (Long default) -> match plays. Screenshots in the scratchpad (m0-m2, g1, g2). Not exercised by me: SIDE pixel diff, tablet 16:10, WiFi/BT, shootout, tournament (code-read only).

### Verdicts per area
- Camera.kt mapping: KEEP. LONG is a true rotation (det +1): x->up, y->right. `facingIndexWorld` = angle-90deg is correct; no mirroring bugs (LW/RW stay on their world side, as seen on screen from behind the home net). Sprite/stick direction is baked from the facing, so the stick follows.
- TouchControls.kt:226 (stick rotation moveX=-my, moveY=mx): KEEP. It is applied before input leaves the device, so GameView.predictLocal (GameView.kt ~line 490, uses localInput.moveX/Y) and the host both get world-axis input: consistent. Shoot/pass/check use the same world move vector, so aim by direction is right.
- FIX (high) guest orientation: nothing flips the view for the WiFi/BT guest (`localTeam == 1`, attacks -x). In LONG the guest's own team attacks DOWN the screen with its own goal at the top, contradicting the "home always at the bottom" goal in the Builder note. Camera needs a `flip` (180 degree rotation: x->down, y->left; px/py/den signs, clampX range mirrored, visibleXMin/Max swapped, depthKey, facing = angle+90deg, near/far net order, faceoff halves, wall/stand/rake visibility, logo rotation) and TouchControls `longFlip` (moveX=+my, moveY=-mx). Set from `localTeam == 1` in GameView.configure (GameView.kt ~line 99). Penalty-box side (y +-44.5), HUD scorer cone, siren and sound pan (GameView.kt ~656 pans on s.y) must use the same flip.
- Renderer.kt depth order: KEEP. `depthKey = -x`; far net before players, near net after. Faceoff half-arcs (drawArc 0/180 = lower half, owned by team with ownGoalX<0) are correct. Penalty-box y +-44.5 is now left/right of the rink; the box is not seen in my run, verify next cycle.
- Renderer.kt:~1204 `ovalPx` uses a field `ovalVk` that is only set in the puck draw (`ovalVk = camera.vkAt(p.x,p.y)`): skater-attached ovals (check/fire/etc. if any call ovalPx) use the puck's last vk. FIX: pass vk or set it per entity. Also several lift offsets still use screen-up `- 0.9f*k`, `- 5.4f*k`, `- 4f*k`, `- 5.5f*k` (breath, fire embers, glass, shards): approximate under pitch; replace with `py(wx,wy,z)`.
- WorldArt drawWarped (LONG): KEEP; buffers are preallocated, no per-frame allocation seen in the changed paths (`warpVerts`, `warpTex`, `warpIdx` are fields). Note 10 mesh columns means affine texture interpolation within a row, fine because rows are horizontal.
- HudRenderer: KEEP projection changes. FIX: scoreboard/"ON FIRE"/SHOTS pills sit on the far blue line and over players (g2): top ~16% of the screen is HUD while the camera shows play right up to it.
- GameView: view mode read once at configure (fine; tournament/WiFi reach the same configure path). Camera follow ignores puck y in LONG (correct for width fit). Pause/rematch not exercised.
- HowToPlay / ControlsDiagramView: still show the old orientation (not touched); low priority.
- SIDE path: code review shows the old formulas reproduced through the unified API (px/py/ppfAt/vkAt/depthAt SIDE branches match HEAD; `drawBitmapMesh` path kept). I did not pixel-diff.

### Visual comparison against the Tape to Tape reference (ranked)
1. FRAMING/ZOOM (biggest): only about 55-60 ft of the rink length is on screen and the camera sits in the home zone; the far net is never visible from the faceoff, so the "look down the whole rink at the far goal" feel is missing. The reference shows ~100+ ft with the far net near the top at all times. Players are huge (a skater is ~16% of screen height) and the phone-width screen forces ~30 px/ft. Cap the scale (e.g. min(width fit, height-based)) and pull the camera back / lower pitch so at least ~100-110 ft is visible; let the sides crop (rink width need not all fit at the near end) or pillarbox with crowd.
2. HUD collisions: scoreboard, banner ("GLK IS ON FIRE") and pills cover the top third of play, exactly where the far zone is. Move the score bug into a compact corner chip (left, above the stick) and shrink banners; keep the centre column clear.
3. Tilt/perspective is mild: side boards converge only slightly, circles are near-circular (vk about 0.78 near the bottom) instead of strongly flattened horizontal ellipses; raise the pitch (more top-down looks flat; reference is ~40-45 deg and far lines are much closer together), make LONG_PERSP larger (~0.010) and shrink far sprites accordingly.
4. Crowd/stands: side stands are motion-blur streaks (texture runs along the perimeter); far end stands are not visible at all (camera clamp keeps end boards at 20% from the top, and the black void at the bottom shows plain black). Bake a dedicated side-stand texture (rows of heads along vertical lines) and add a far-end tier above the far boards; fill the area below the near boards with the home-end glass/stands or darken with a gradient instead of flat black.
5. Net rendering: the near net is viewed straight from behind/front and clipped at the bottom edge; it reads as a flat grid rectangle and the near goalie overlaps it awkwardly. Draw the far net with visible depth and the near net lower and larger with its crossbar, or clip with the camera floor lowered so the whole goal mouth and crease are visible.
6. Sprites are upscaled (13 px/ft cap) and soft on the phone; at the reduced zoom from #1 this resolves itself, otherwise raise the cap to ~18 px/ft for LONG.
7. Ad panels on the side boards are sideways text, hard to read; use letter-free logo blocks or per-sponsor colour bands in LONG.
8. Lighting: flat; the reference has a soft vignette and darker far end; add a gradient overlay from the far boards to the top.
9. Touch controls (stick left, 3 buttons right) cover about 25% of the rink and the boards at phone width; make stick and buttons smaller / more transparent in LONG, or inset the playfield.

### Top issues for the next builder cycle (ranked)
1. Zoom out / rework resize() so ~100 ft of length is visible and the far goal is visible from faceoff (Camera.kt resize loop: change TARGET_LEN_FT to ~105, cap `scale`, raise LONG_PERSP, lower ANCHOR_FRAC so the focus sits ~70% down). Re-tune the far/near board clamps.
2. Guest/away orientation flip for WiFi/BT guest (and the Prefs-driven per-device choice) as described under "FIX (high)".
3. HUD: reposition scoreboard/banners so they do not sit over the far zone; shrink controls.
4. Stands and void: side-stand texture, far-end crowd, replace plain black below the near boards.
5. Replace the remaining screen-up lift offsets (`- k*h`) in breath, ember, glass, shard effects with `py(..., z)`; fix `ovalVk` staleness.
6. Test: shootout in LONG, penalty box side, tablet 16:10 (resize() maths at 1280x800), tournament/WiFi entries, HowToPlay diagram.

## View Cycle 2 - Builder

### Changes (LONG only; SIDE verified unchanged, screenshot s1)
- Framing (Camera.resize): pitch fixed at sin 0.74 (~48 deg), LONG_PERSP 0.0065 -> 0.0085 (boards converge to ~47% width far vs near), scale fitted so
  the far boards sit at 20% of the height at the faceoff, then capped (SCALE_CAP 13.5 px/ft at the focus; also capped by rink width at the near row, so tablets
  fit the full width). Phone 2700x1280: 13.5 px/ft focus (~18 near bottom, was ~30); tablet 1280x800: ~10.7 px/ft. Sprites bake from `baseScale`
  (<= 13 px/ft) so zoom never rebuilds them. ~100-105 ft of length is on screen; far net, goal line and far crowd are visible from the faceoff.
  Anchor now ~0.6 of the height; near boards clamp at 93% of the height (a strip below for scenery), far boards no lower than 28%.
- Camera follow (new `Camera.track(world, dt)`, called from GameView.update; SIDE keeps the old follow): critically damped (SmoothDamp) springs.
  Parameters: x smoothing 0.32 s (omega 6.25/s), lateral 0.55 s, zoom 0.9 s, faceoff ease 0.6 s, quick snap 0.14 s for 0.9 s after a goal / period end / start.
  Look-ahead = filtered puck velocity (12 Hz low pass of position deltas, so it works with the interpolated guest puck) x 0.28 s, capped at 16 ft;
  puck bias +3 ft toward the far end; +up to 10 ft toward the attacking end when a team carries past 35 ft into its offensive zone (full at 65 ft);
  faceoff target = dot + 8 ft (far net visible). Lateral pan = 0.3 x puck y (+0.1 s of y velocity), limited to 8 ft and to the sideways slack (tablets ~0, phone 8 ft).
  Zoom 1.08 (faceoff / slow, <12 ft/s) to 0.93 (fast, >67 ft/s), clamped so the near row never exceeds the screen width. End-board clamps unchanged in spirit
  (recomputed each frame from the live zoom). No allocation (plain float state).
- HUD (HudRenderer.compact, set from Renderer.setViewMode): score bug scaled 0.78 into the top-left corner with its pills under it; banner 0.72x at the top
  centre (over the far crowd); goal / faceoff lower thirds scaled 0.8 toward the bottom; controls 0.78x (TouchControls.layout) drawn at 175/255 alpha via a layer.
- Scenery (WorldArt, Renderer): perimeter segments 8 ft -> 4 ft (removes the wavy affine warp of the stands; SIDE geometry identical); dedicated LONG side-stand
  texture with spectators baked 2.4x wide so the foreshortened rake shows round heads in rows that converge, darker tint; gradient background instead of black
  void; far-end vignette (top 42%) plus side-edge vignette; far-end tiers behind the far net visible. Ad panels kept as lettered panels: they are drawn along the board
  direction and read fine at the near sides (BLUE LINE, ARCTIC FUEL ...), compress at the far end only.
- Correctness: `ovalPx` takes the squash per call (default is the puck's, computed in drawPuck; skater rings/meter already used `groundOval` / per-item vkAt, so no
  stale squash was found there); breath, glass-shatter, shards and fire embers now use `py(wx, wy, z)` (SIDE output identical because py(z) = - z*scale*f).

### Verification
Pixel 9 Pro emulator 2856x1280 (screens d1 d2 d3 d5 e3 f1 f2 f3) and the same emulator resized to 1280x800 (t2) for 16:10; SIDE s1. Far end (e3), centre (f2),
near end (f3, d4) and side shifts (f2) were checked. `wm size/density` were reset afterwards and the View preference put back to Long.

### Remaining
- Near-end net still looks fairly flat; the faceoff lower third covers it at faceoffs. Stands close to the camera at the bottom corners are soft.
- Guest 180-degree flip intentionally not done (home always at the bottom). HowToPlay diagram unchanged. Banner/goal band LONG placement not screenshotted in LONG
  (only by code), shootout controls in LONG untested. WiFi/BT guest camera relies on the velocity estimate only (not run on two devices).

## View Cycle 2 - Critic

Build: `assembleDebug` passes. Run on the Pixel 9 Pro emulator (2856x1280, LONG, Indoor Stadium) and the same emulator at `wm size 800x1280` (1280x800 landscape; density NOT changed, so HUD/controls look oversized there and overlap more than they would on a real 16:10 device). `wm size` reset afterwards, app force-stopped. Screenshots in the scratchpad (f1-f3, m1-m6, mont.png, t2-t4). fps log on the emulator 52-58, update 0.2 ms, draw 1.3-5.5 ms. adb screencap takes about 1 s per frame, so camera smoothness could not be judged from a 0.5 s burst; it was judged from the code. Not exercised: shootout, penalty banner, goal celebration in LONG, two-device guest.

### Verdicts (code)
- Camera.track() spring (Camera.kt:218-287): KEEP. SmoothDamp form is stable for any dt; dt is clamped 0.001..0.1 so pause/resume and frame spikes cannot overshoot; first frame has no velocity (havePuck) and prevPhase==null triggers the 0.9 s snap; no NaN path found (smooth times are positive constants, coerceIn ranges are always ordered: zoomMax >= 1 > ZOOM_OUT, panMax >= 0, minX<=maxX forced at :175). The velocity estimate is clamped to +-150 ft/s and reset on entering FACEOFF, so goal/whistle teleports do not poison the look-ahead.
- FIX (low) Camera.kt:239-246: puck velocity is derived from position deltas per render frame. On the host the sim steps in a fixed tick, so when render dt and tick do not line up the delta alternates 0 / 2x; the 0.12 s low pass hides most of it, but a carried puck (stick sway) still adds a few ft/s of look-ahead noise (x0.28). Use `world.puck.vx/vy` on the host and keep the delta estimate for the guest only.
- FIX (medium, SIDE regression) Camera.kt:236: the SIDE branch calls `follow(puck.x + vx*lead, 0f, dt0)`. HEAD passed `p.y * 0.85f + p.vy * lead * 0.5f` as the y target (GameView.kt diff). SIDE now loses its vertical follow. Restore the old y argument.
- KEEP sprite cache: Renderer.kt:618-635 keys on `min(baseScale, 13)`, not on the animated `scale`, so zoom never rebuilds sprites; baseScale changes only on resize.
- KEEP end-board clamp: updateLimits (Camera.kt:164) recomputes from the live scale each frame and track() calls it after the zoom update and before the x clamp. Seen correct at 2856x1280 and 1280x800 (near boards stop above the bottom edge; far boards not above 28%).
- FIX (medium) HudRenderer.kt:816-818: `canvas.saveLayerAlpha(0,0,sw,sh,175)` every frame is a FULL-screen offscreen layer (2856x1280 phone, 1280x800 Fire) only to make the stick/buttons translucent. That is fill/bandwidth on the weakest device. Use tight bounds (two layers: stick rect and button cluster) or multiply the buttons' paint alphas.
- KEEP TouchControls layout (TouchControls.kt:63-75): hit centres/radii and the drawn buttons come from the same fields (shootR/passR/hitR/joyRadius), so buttons are where they are drawn (checked in t2/m1). Left-half joystick zone is still `x < screenW*0.5 && y > topExclusion`; the pause icon (top right, unchanged, topExclusion 64*density unscaled) is reachable. `longView` is set in configure() (GameView.kt:102) before the first surfaceChanged/layout, so the 0.78 scale applies; layout is not re-run if it changed later, fine since it is set once.
- KEEP guest: the puck is exponentially interpolated (k=18) so the delta velocity is smooth; the predicted-skater carry fix keeps the puck on the stick and the camera follows it.
- WATCH shootout: if it runs as phase PLAY with one skater vs the goalie, the camera uses the generic puck branch (+3 ft bias, no attack-end bias without a carrier past 35 ft); at the near net (away team shooting at the bottom net) the net/goalie may sit against the 93% near clamp. Untested.

### Visual comparison to Tape to Tape (ranked remaining differences)
1. Stands are the dominant visual noise (f3, m1-m6): huge saturated spectator tiles (green/white/purple/red blocks) with moire and radial blur at the sides, brighter than the ice, so the eye goes to the crowd. The reference crowd is a dark desaturated mass of small heads. Bake spectators ~40% smaller, desaturate/darken (alpha ~0.55 over near-black), restrict the palette, and push the side vignette harder. The tier mesh is also too close: the near rows fill ~35% of screen width.
2. Far end: far boards/net are visible from the faceoff (good, f1/t2), but the crowd behind them is a thin strip clipped by the top edge. The reference has a tall wall of bleachers/roof lights across the top 15-20%. Draw far tiers taller or lower FAR_BOARD_FRAC use.
3. Perspective still reads as a tilted flat map more than a 3/4 broadcast: side boards converge to about 55% of the near width, circles are ellipses (vk ~0.7 near) but not strongly flattened. Try LONG_PERSP ~0.011 and pitch ~41 deg (sin 0.66) while keeping the far net in frame; far skaters should shrink more.
4. Players are upright and cartoon-like (good) but small at the focus (~6% of screen height vs ~9-10% in the reference); about 40% of the phone width is crowd. Zoom in 10-15% and let rink sides crop (use pan on tablets too; panMax is 0 there).
5. Lighting is flat: ice stays bright white to the far end. Add a soft light pool on the focus and distance darkening on the ice, not only on the crowd.
6. Near end: in zoomed frames the near boards touch/leave the bottom edge (m2, m4) and the faceoff lower third ("1ST PERIOD 0-0", m4) sits on top of the home net and goalie. Move it to the top or to a small side pill.
7. HUD: compact score bug and pills work; the SAVE/COVERED banner overlaps the far blue line/net on the phone (m1, m3). Narrow it and move it to ~14% of the height, or onto the crowd.
8. Near sprites render at ~18+ px/ft from a 13 px/ft bake: still a bit soft; raise the LONG cap to ~16 or bake two tiers.
9. Side ad text is sideways but legible (BLUE LINE, ARCTIC FUEL, FROSTBITE): fine.
10. Controls (0.78x, translucent) acceptable; buttons overlap the right boards on the phone.

### Top issues for the next builder (ranked)
1. Crowd readability (issue 1): WorldArt LONG side-stand bake + tint/vignette; biggest single move toward the reference.
2. Retune pitch/perspective (issue 3) and re-run the resize fit math (aFar/aNear) for the new constants; check the 13.5 px/ft cap.
3. Zoom the play in (issue 4); allow some pan/crop on tablets.
4. Fix the SIDE regression at Camera.kt:236 (restore `p.y * 0.85f + p.vy * lead * 0.5f` as the y target).
5. Replace the full-screen saveLayerAlpha (HudRenderer.kt:817); measure fps on the Fire HD 8 before and after.
6. Faceoff lower third / goal band / banner placement in LONG (issues 6-7); screenshot a goal celebration, a penalty banner and a shootout in LONG.
7. Use puck.vx/vy on the host for look-ahead (Camera.kt:239); keep the delta filter for the guest.

## Side View Fit Fix
- Problem: SIDE scale was width-only (screenW/88) with a strong perspective (0.016) and sideways follow, so on a 2.2:1 phone (Pixel 9 Pro) the near/far boards and players at the top/bottom were cut off.
- Camera.resize (SIDE branch): scale = min(screenW/88, usable height / projected extent of the full rink width). Extent = +-(42.5+3 ft) through VK and perspective, plus 6.5 ft of player height at the far board, 3% screen margin top and bottom; anchorY is solved so both edges sit inside the margins. SIDE PERSP lowered 0.016 -> 0.007 (the old value makes the near board 3x scale, which cannot fit the full width at a usable zoom).
- Follow: original SIDE vertical follow restored in track() (`p.y*0.85 + p.vy*lead*0.5`), but clampY only allows the slack left over when the length fit is the smaller scale (`sideSlackFt`, ~0 in practice) so the side boards never leave the screen.
- End-board clamp (`sideLimX`): min(HALF_L-20, HALF_L+WORLD_MARGIN - visible half length) so no void past the end stands when more length is visible (phone: ~35 ft, tablet ~56 ft, was fixed 80).
- Result: phone 2856x1280 shows ~whole width top to bottom (p1, p2 screenshots), roughly 130+ ft of length; 1280x800 (t2, t3) is zoomed out (about 14.5 -> ~11 px/ft) and flatter perspective, so it does NOT look identical to before. Extra width on phones shows more rink/stands (background fills; small black wedge below the corner stands at the bottom-left remains, as before).
- Verified on the Pixel 9 Pro emulator; wm size/density reset afterwards. The View preference was left on Side.

## View Cycle 3 - Builder

### Far-net close zoom (Camera.track, LONG only)
- Proximity = smoothstep over the puck's distance to a goal line: 0 at 35 ft (NET_ZOOM_RANGE_FT), 1 at the goal line and behind it.
  Far (attacked, top) net: zoom x (1 + 0.35) (NET_ZOOM_FAR); near (bottom) net: x (1 + 0.12), shootouts x (1 + 0.18). Works for host, guest, tournament and shootout
  (it only reads puck.x and w.isShootout). Zoom spring 0.7 s while boosting (SMOOTH_NET_ZOOM, else 0.9 s); the speed zoom-out is reduced by up to 80% near a net so shots do not pull the camera out.
- Total zoom cap NET_ZOOM_MAX 1.65 (phone: 1.12 x 1.35 = about 1.5; the old width cap is bypassed for the boost, only the near row crops). Far-board clamp is relaxed
  the other way: FAR_BOARD_FRAC 0.28 + 0.09 x proximity so the net sits lower on screen (about 30% of height) with the tall crowd wall above it; near clamp unchanged (net fully visible at the bottom, screenshot d5).
- Sprites: LONG bakes at min(baseScale x 1.2, 14.5) px/ft (was <= 13), so the max upscale at the net is about 1.4x; baked once per resize (no per-frame rebuild).
### Other changes
1. Crowd: LONG tiers are now 4 (taller wall, heights 9/11/13/16 ft, dims 0.62-0.5), spectators desaturated (WorldArt.mute 0.5) and the side rake bakes spectators at 0.62x size, dim 0.62.
2. Perspective: LONG_PERSP 0.011, LONG_SIN 0.66 (~41 deg). Zoom range 0.98..1.12 (faceoff 1.06) = a bit closer than before.
3. Lighting: one elliptical radial vignette (clear around the focus, dark to the sides/corners) replaces the side-edge gradient (same one full-screen draw), top vignette alpha 150 over 45%.
4. HudRenderer.drawControls compact: two tight saveLayerAlpha layers (stick and button cluster) instead of a full-screen layer.
5. Camera look-ahead uses puck.vx/vy on the host (Camera.useWorldVelocity set in GameView.configure, false on the WiFi/BT guest).
6. HUD: faceoff lower third in LONG moved to the top (scaled 0.7, top about 11.5% of height); banner (SAVE / COVERED / penalty) scaled 0.6 at 6.6% of height.
### Verification
Pixel 9 Pro emulator 2856x1280: normal play (c5, c6), far-net zoom via a temporary pinned puck x=70 (d4), near-net zoom with x=-70 (d5; hack reverted, grep TEMPHACK = 0),
and a 1280x800 run (e2, e3: goal lower third, controls). wm size/density reset. fps on the emulator ~39 at 1280x800 (oversized UI), 52-58 at phone size.
### Remaining
- Real puck-driven far-net zoom and shootout framing at the near net were verified by the pinned-puck hack, not by an actual attack. Side crowd still fairly busy close to the camera.
- No Fire HD 8 measurement of the HUD layer change.

## View Cycle 3 - Critic
Build: `./gradlew assembleDebug` passes. Emulator Pixel 9 Pro (2856x1280) and 2560x1600 (16:10) screenshots k4-k7 in the scratchpad; wm size/density reset. No Fire tablet connected: NO device fps measured (emulator 28-32 fps, post-bound = emulator GPU, not representative; first frame had a 4.7 s lock/record hitch on the emulator = sprite bake/warm at start).

### Verdicts
- KEEP far-net zoom ramp (Camera.kt:310-327): smoothstep of puck distance to the goal line is continuous (no step at 35 ft; the zoom spring is critically damped so hovering at 35 ft only gives tiny ripples). Depends only on pk.x, so host/guest symmetric, and no state to get stuck: after a goal/faceoff the FACEOFF branch ignores netBoost (netBoost=0, farRelax=0) and the zoom eases back. Behind the goal line the clamp (coerceIn 0..1) holds full zoom: fine.
- WATCH (low) shootout: at the dot the puck is at centre (zoom 1x) and the zoom rises only for the last 35 ft; ramp is spring-limited (0.7 s) so a fast shot may arrive before the zoom finishes. Not tested with a real shootout.
- WATCH (low) Camera.kt:326-327: tz is clamped to zoomMax BEFORE the boost and to NET_ZOOM_MAX (1.65) after. On tablets/16:10 (zoomMax ~ scWidth ratio) 1.12*1.35=1.51 is under the cap, so the width crop is bypassed only on the near row as intended. In k7 (16:10) the near-row faceoff circle and side boards crop at the bottom, acceptable. End boards: updateLimits uses live scale and farRelax so no void seen at 16:10 or 2.2:1 in normal play. Not verified in a real far-net attack (hack-only in cycle 3).
- KEEP SIDE branch: track() SIDE path and clampY/sideLimX match the Side View Fit Fix (y target 0.85 restored, Camera.kt ~line 289).
- FIX (medium, perf) HudRenderer.kt:823-828: the button-cluster layer spans (bl,bt)-(sw,sh) = about 20% x 45% of the screen, much more than the three circles; still an offscreen layer every frame on Mali. Replace both layers with per-paint alpha (drawControlsInner uses pFill/pStroke/text: multiply their alpha by 175/255; with glow shaders handled by paint alpha), or at minimum bound each button separately. HudRenderer.kt:1275 faceoff/banner layer is only active while a banner shows: fine.
- FIX (medium, perf) Renderer.kt:352-357 drawLongVignette: two full-screen draws per frame (vigTop 42% + vigSide radial with a local matrix = full screen alpha blend over the whole frame, overdraw on 2856x1280 / 1280x800). Bake both into a single ARGB_8888 (or ALPHA_8-tinted) bitmap at 1/2 resolution on resize and drawBitmap scaled (bilinear): one cheap textured quad, no gradient maths. Rebuilds happen only when the size changes (vigW/vigH check is correct).
- RISK (medium) sprite memory, Renderer.kt:626: LONG bake cap 13 -> min(baseScale*1.2, 14.5). Sprite bytes scale with scale^2: if the Fire HD 8 (1280x800) baseScale is ~11-12, the bake is ~13.5-14.5 px/ft vs 13 before: about x1.1-1.25 memory, i.e. the ~43 MB pre-warm becomes ~47-54 MB, plus the 16-facing x 8-pose x per-team cache, on a 1.5 GB device with a 256-512 MB heap class. No OOM seen on the emulator (large heap). Pre-warm runs on the "SpriteWarm" thread, but the first-frame draw hitch (4.7 s lock on the emulator) shows the lazy path can stall. Suggest: keep 14.5 only when screenH >= 1000 px (phones), cap 13 on <= 800 px tablets (the zoomed sprites are only ~1.4x upscaled there anyway), and log charArt bytes.
- KEEP cache invalidation: ensureArt compares charArtScale with rScale (>0.01) so a resize rebuilds sprites once; zoom never rebakes.
- Per-frame allocations: none found in the new Camera/vignette/HUD code (shaders built on resize; RectF reused; saveLayerAlpha float overload allocates nothing).

### Visual (k6 phone 2.2:1, k7 16:10)
Reads clearly as a 3/4 broadcast view from behind our goal: far net, strongly elliptical circles, converging sides, dark desaturated crowd now mostly good, players upright and shrinking with distance, HUD placed well (no overlap in k6/k7).
Ranked remaining differences from Tape to Tape:
1. Crowd tiles on the sides are still large and noisy near the camera (k6, k7 left/right 30%), with visible moire; far wall is good. Shrink/blur the near side tiers further or darken with a depth gradient.
2. The play frame sits low: at the faceoff the far net is only ~25% of the screen height with a lot of empty ice between the far blue line and the pack; zoom in 5-8% (or raise the focus) so players are bigger (reference ~9-10% of height; now ~8%).
3. Near boards/bottom corners: circle and boards cropped by the bottom edge and the joystick/buttons sit on the boards (k6): acceptable but busy; controls could be 0.7x and more transparent.
4. Lighting: ice is brighter than the reference and uniformly white in the near half; vignette helps at sides only. Darken ice toward the far end slightly.
5. Sprite sharpness at the near row is fine now (k7 numbers legible); do not raise the bake further (see memory risk).
6. Top-edge/status bar black band on 16:10 is the emulator system bar, not the game.

### Top issues for the final builder (ranked)
1. Replace the two saveLayerAlpha layers in drawControls with per-paint alpha (HudRenderer.kt:823-828) and bake the vignette to one half-res bitmap (Renderer.kt:335-357): both are per-frame GPU costs on the Fire HD 8; measure fps on the tablet (baseline ~54-56) in LONG and SIDE.
2. Cap the LONG sprite bake at 13 on screenH <= 800 devices (Renderer.kt:626) or measure heap; check the startup hitch.
3. Tone down near-side crowd tiles and add depth fade (WorldArt LONG side rake).
4. Real far-net attack and shootout verification (not a pinned-puck hack): framing at the near net and the zoom lag on fast shots.
5. Zoom/focus tweak so players are larger and the far net sits a bit higher.

## View Cycle 4 - Builder
Build passes. Only the Pixel 9 Pro emulator was connected (no Fire HD 8), so the Fire fps number is MISSING. Emulator (2856x1280) is GPU-limited at ~30-31 fps (post ~28 ms) in both LONG and SIDE, before and after (cycle 3 critic also saw 28-32), so it cannot show the gain of the GPU changes; expect the Fire to benefit (no offscreen layers, 1 tiny textured quad instead of 2 full-screen gradients).
### Changes
1. PERF: HudRenderer.drawControls compact: the two saveLayerAlpha layers are gone; the pFill/pStroke/pTextC paints get a ColorMatrixColorFilter (alpha x 175/255) while the controls are drawn, then cleared. Renderer vignette: top gradient (alpha 150 over 50%) + elliptical radial pool are baked once per size into one half-resolution ARGB bitmap (`vigBmp`), drawn as one scaled quad per frame. No other per-frame full-screen layers/gradients found (bgPaint gradient is one opaque rect).
2. Sprites: LONG bake cap = min(baseScale*1.2, 13) when screenH <= 800, else 14.5. Logged once: "PowerPlay: sprites warmed: 53 MB at 14.5 px/ft" (emulator, 2856x1280); scaling by px^2 gives ~43 MB at 13 px/ft = the old Fire figure. Pre-warm was already off the game thread; the first frame is still ~1 s on the emulator (record 965 ms: stand/wall/rink bakes + lazy sprites on the game thread), not fixed; later hitches of 50-380 ms in the first second are texture uploads.
3. Crowd: LONG side rake spectators baked at 0.42x (was 0.62), dim 0.5, then blurred by a down/up-sample, tint darker (0x8088A0). Near stands are now a calm dark mass (g3).
4. Framing: ZOOM_IN 1.12->1.19, ZOOM_OUT 0.98->1.04, ZOOM_FACEOFF 1.06->1.13, NET_ZOOM_MAX 1.75; players ~7% larger, less empty ice before the pack.
5. Real tests (no pinned puck): real shootout from the main menu, both attacking the far net (our skater, u3/t6) and defending the near net (opponent attack, n/t frames). Near net is fully visible with goalie and crease; the zoom ramp is smooth. Far-net found the net low (~40% of height) with empty dark crowd above, so Camera adds `NET_LEAD_FT` 9 ft toward the far end scaled by net proximity and farRelax 0.09 -> 0.02: net now ~30% of height with boards under the crowd wall. Normal-play far-net attack could not be reached by scripted input (AI kept the puck in our end), so the far-net zoom in open play was only seen via the shootout.
6. HowToPlay diagram: not changed (skipped).
### Screens
g3 (faceoff, LONG), h2 (near-net defence), t6/u3 (far-net shootout), s1/n/r/t montages in the scratchpad.
### Remaining
Fire fps before/after unmeasured; first-frame ~1 s bake stall; controls overlap the right boards at the near row; far crowd is very dark at the net; HowToPlay diagram orientation.

## View Cycle 4 - Critic
Build: `./gradlew assembleDebug` passes. Run on the Pixel 9 Pro emulator (2856x1280, no wm size/density changes): LONG match ~60 s (play, shoot, penalty/power-play, pause dialog, Restart), SIDE match ~50 s (view switched via Match Setup radio). Screens c4a-c4l in the scratchpad. fps 30-32 (post 29 ms = emulator GPU, same in LONG and SIDE, not representative). No FATAL/AndroidRuntime Java exception in either match.

### Verdicts
- ColorMatrixColorFilter (HudRenderer.kt:816-835): SHIP. Filter is set on pFill/pStroke/pTextC, both inner passes run, then all three are nulled on the same call path (no early return between set and clear; drawControlsInner's `return` is inside the inner function). Nothing else draws between, so no leak. Stick, HIT, POKE, SWITCH all translucent and legible (c4e). Minor: any exception inside would leave the filter set; not realistic.
- Vignette bitmap (Renderer.kt:335-375): SHIP. Built lazily on the draw/game thread, recycled+recreated only on size change (half-res 1428x640 ARGB = 3.6 MB). Not recycled in release() (Renderer.kt:1642): small, GC reclaims it; add `vigBmp?.recycle(); vigBmp=null` if touched. Rotation: activity is landscape-only so no config reload.
- Sprite memory: SHIP. Fire HD 8 (screenH 800): cap 13 px/ft => ~43 MB (same as before cycle 3); the cap is on rScale, keyed by size only, so zoom never grows memory. Emulator logs 53 MB at 14.5. Phones/large tablets (>800 px high) pay 53 MB: acceptable. First frame still hitches ~1 s (bake on game thread).
- Camera/zoom clamps: SHIP by code review (clamp ranges ordered, dt clamped); seen correct at 2.2:1 (this run) and at 16:10 in cycles 2-3. Split-screen/small windows not run; the same resize fit math applies. Shootout framing seen by builder, not re-run.
- Guest/LONG input: SHIP. TouchControls maps stick to world axes (`moveX=-my, moveY=mx`) when longView, and GameView.predictLocal consumes `localInput.moveX/Y` as world axes, so prediction and host input agree; guest sees its own team attacking down (no flip), consistent. Two-device WiFi/BT still untested.
- Touch zones vs drawn controls: SHIP (same fields drive hit test and drawing; pause icon at top right unobscured; PULL GOALIE pill sits beside it).
- Prefs.viewMode: SHIP. Fresh/unknown value -> LONG (Prefs.kt:38-40); radio persists on change; GameView reads it per match start (configure), so switching between matches needs no restart by code. In my run the setting persisted to the next process (SIDE came up after selecting it).
- SIDE look: SHIP, matches the Side View Fit Fix (c4k: full width, controls fine). HUD pills (ON FIRE/SHOTS) tight but fine.
- Leftover debug: none. grep TEMP/HACK/wm size = 0 in app/src/main. `SLOW frame`/`SLOWUPD` Log.d lines exist at HEAD and only fire on >15 ms frames (spam on slow GPUs, same as before).

### Ship-blocker candidates (ranked)
1. NATIVE CRASH ON QUIT FROM GAME (emulator, reproduced 2/2: LONG after Restart, SIDE). After Pause > QUIT the process dies ~1-2 s later: `SIGSEGV in RenderThread: egl_window_surface_t::~egl_window_surface_t -> Surface::hook_cancelBuffer -> GraphicBuffer::from` (libEGL_emulation, destroying the old GameActivity window surface; no Java FATAL). The stack is entirely inside the emulator EGL/gralloc driver and CLAUDE.md already records a RenderThread abort with the hardware canvas, so it is most likely an emulator-driver bug triggered by tearing down the SurfaceView that uses lockHardwareCanvas; I did not build HEAD to prove it is not a regression (no source edits allowed). Action before release: on the Fire HD 8, play LONG, Pause > Quit and check `adb logcat -b crash`. If it reproduces there, make GameView.surfaceDestroyed stop/join the game thread (and skip unlockAndPost after destroy) before the activity finishes. Because of this crash I could not test an in-process view switch (my switch test was across a process restart; code path reads Prefs per match so it should work).
2. None else.

### Known remaining issues
- No Fire HD 8 fps/heap measurement of cycles 3-4 changes; first-frame ~1 s bake stall.
- Controls overlap the right boards at the near row in LONG; far crowd very dark at the net.
- WiFi/Bluetooth guest in LONG, split-screen sizes and HowToPlay diagram orientation untested/unchanged.
- vigBmp not recycled in Renderer.release() (3.6 MB, GC-managed).

## View Cycle 5 - Void and Controls
Verified on the Fire HD 8 (GCC1XE0323260B49). Screens a_side_before / a_side_after2 / a_pond / a_long_after in the scratchpad.
### Changes
1. SIDE void (Renderer.kt): the black clear is replaced, for non-pond SIDE, by `buildSurround()`: a dim blurry dark-arena crowd bitmap baked once per size (w/14 x h/14, ~91x57 px, seeded noise over the navy arena gradient, jersey-colour speckle at ~22%), drawn as one opaque scaled quad before the stands/rink. Fills the band under the near boards and the bottom-left/right corners. Camera SIDE untouched; no per-frame allocation; cost = one opaque blit (replaces the drawColor clear).
2. LONG controls (TouchControls.layout): LONG branch tucked into the corners (shoot 68u/68u from the corner, pass 172u/56u, hit 76u/168u, stick rest 90u/88u from edges; u = density*0.78; radii unchanged 39/33/33 x density, so >= 44dp diameter; button gaps >= original). SIDE branch values identical to before. HudRenderer.drawControls reads the same fields (verified, buttons draw at the new spots); hit testing uses the same fields.
### gemma4 use
gemma4:26b (think=false) drafted both. Layout draft changed the button arrangement (hit moved beside pass), so it was discarded and I wrote the layout myself, keeping the original arrangement. The surround-bitmap draft had syntax/logic errors (val reassignment, stray `basePSS` line); I rewrote it using its approach (IntArray + setPixels, 8-colour palette, seeded Random). First version at w/4 looked like TV static, so the resolution was lowered to w/14 and intensity to 0.22.
### Before/after
SIDE before: near-black band under the near boards and a black wedge bottom-right/left. After: dim crowd mass all around, no void. LONG: HIT/SHOOT/PASS and stick now sit at the screen corners with the near boards and corner ice clear of the SHOOT button's old spot; ice no longer covered except at the very corner. Winter Pond SIDE: bottom is snow bank (fine); a dark-navy wedge at the upper left under the sky remains (reads as night landscape, left unchanged).
### FPS (Fire HD 8, ~70 s scripted play each)
LONG avg 55.6 (min 50.3), SIDE avg 57.5 (min 52.6) - no regression vs LONG ~56 / SIDE ~58. No FATAL.
