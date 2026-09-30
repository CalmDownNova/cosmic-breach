# Cosmic Breach player animations

Offline authoring and preview for the player's combat animations, played by
playerAnimator 2.0.4 (KosmX, `playeranimator-2.0.4+1.21.1-forge`). Nothing here
needs Gradle or the game: the loader, the playback maths and the vanilla model
are ported to Python from the real jars, so the previews show what the game
will draw.

## Rebuild and review

Python 3.10+ with numpy and Pillow.

    python tools/anim/author.py            # writes every animation (or name some: l1 dash_side ...)
    python tools/anim/review.py            # renders previews/ and runs every check (about 2 minutes)
    python tools/anim/preview.py <file.json> [--tick N] [--sheet 1,3,5] [--mirror] [--fade-from F.json@T]

`author.py` writes `src/main/resources/assets/cosmicbreach/player_animations/<name>.json`
and `timing.json` (frame data the previewer uses to colour ticks).
`review.py` writes to `previews/`:

| File | What it shows |
|---|---|
| `<name>_strip.png` | every game tick; rows: side, front, top, first person |
| `<name>_keys.png` | the key poses big: side, behind (F5-like), front, first person |
| `<name>_t<N>.png` | one tick from five cameras, with the blade tip's path (active ticks red) |
| `contact_sheet.png` | the contact frame of every animation side by side |
| `checks.txt` | per tick: fist distance from the grip, feet height, head yaw, clipping; then the between-tick and transition checks |

Colours: right limbs warm, left limbs cool, face side of the head dark. The
first-person row looks 15 degrees down (60 for the plunge, which needs it) and
draws only the arms and the sword, as the game does in `THIRD_PERSON_MODEL` mode.

## Files

| File | Role |
|---|---|
| `palib.py` | line-by-line port of the library: both JSON readers, `KeyframeAnimation`, `KeyframeAnimationPlayer`, easing, fade and mirror modifiers |
| `rig.py` | vanilla player model (Steve, wide arms) and the held Meridian, posed in the game's pose-stack order |
| `render.py` | z-buffered software rasteriser, orthographic and first-person cameras |
| `preview.py` | the preview CLI and the checks |
| `solver.py` | turns a readable pose (lean, twist, stance, grip point, blade direction) into channel values: two-hand arm IK, legs held in the world, feet on the ground, head facing ahead |
| `moves.py` | the choreography: key poses per move |
| `author.py` | samples the keys every tick, solves, writes the JSON |
| `review.py` | renders the review set and runs all checks |
| `blade.py` | where the sword's guard and tip are on every tick, from the feet and as the first-person camera sees them |

## What the library does (read from the jar, decompiled with Vineflower 1.10.1)

### Loading

- `PlayerAnimationRegistry` lists `assets/<ns>/player_animations/` (plural; the
  singular folder still loads, with a warning). For `.json` files it tries the
  emotecraft reader (`AnimationJson`) first; a file without an `"emote"` key is
  handed to `GeckoLibSerializer`.
- The animation id is `<namespace>:<name>`, where the name comes from inside
  the file (emotecraft: top-level `"name"`; GeckoLib: the key under
  `"animations"`), lowercased. The file name is not used. Ours match anyway.

### Emotecraft format (what we write)

    {"name": "meridian_l1", "version": 3, "author": "...", "description": "...",
     "emote": {"beginTick": 0, "endTick": 10, "stopTick": 13, "isLoop": false, "returnTick": 0,
               "degrees": true,
               "moves": [{"tick": 0, "easing": "LINEAR",
                          "rightArm": {"pitch": -70.1, "yaw": -47.8, "roll": 0.0}, ...}, ...]}}

- Values are the library's internal values: no sign flips, and positions are
  absolute (they include the part's pivot, so right arm x -5, y 2 is its rest).
- `"degrees"` defaults to true. Better Combat writes radians with `"degrees": false`.
- Axes: `x y z pitch yaw roll scaleX scaleY scaleZ bend axis`, any subset per move.
  One move object may carry several parts.
- `version` below 3 renames a part called `torso` to `body` (the whole-player
  transform). Better Combat's files rely on this.
- Every key in a move other than `tick`, `comment`, `easing`, `turn` is read as a
  part name. `"easingArg"` is not skipped, so a number there makes the whole file
  fail to load (only a log line says so). `"turn": n` adds n full turns.

### GeckoLib format (read, not used; documented for completeness)

- `animation_length` in seconds; endTick = ceil(length x 20). `"loop": true` and
  `"loop": false` both subtract one tick; `"loop": "hold_on_last_frame"` loops on
  the last frame; no `loop` key keeps the full length.
- Keyframe times: tick = (int)(float(key) x 20f), truncated in float32.
- Bone names are snake_case turned camelCase (`right_arm` to `rightArm`).
- `body`: rotation x and y negated, position divided by 16 and x negated.
  Other bones: position y negated and added to the rest pivot. Degrees.
- A keyframe given as a plain array gets CONSTANT easing for rotation (a step,
  not a blend) and LINEAR for position and scale. Objects take `vector`, `pre`,
  `post` (each adds a keyframe at that tick), `easing` or `lerp_mode`, `easingArgs`.
- `<bone>_bend` needs bendylib.

### Parts, frames and signs

Local frame: origin at the feet, +Y up, the player faces -Z, +X is the
player's right, blocks. This is the pose stack after the vanilla body-yaw turn.

- `body` moves the whole player, before the model is drawn:
  translate(x, y + 0.7, z), rotZ(roll), rotY(yaw), rotX(pitch), translate(0, -0.7, 0).
  So it pivots at hip height, 0.7 blocks up. Position is in blocks (+x right,
  +y up, +z back). Positive pitch leans back, positive yaw turns left, positive
  roll leans left.
- Then the model: scale(-1, -1, 1), scale(0.9375), translate(0, -1.501, 0).
  Model space: pixels, +Y down, forward is -Z, +X is the player's left.
- `head`, `torso`, `rightArm`, `leftArm`, `rightLeg`, `leftLeg` are the vanilla
  parts: translate(pivot / 16), then rotationZYX(roll, yaw, pitch), so pitch acts
  first. Pivots: head and torso (0, 0, 0) at the neck, right arm (-5, 2, 0), left
  arm (5, 2, 0), right leg (-1.9, 12, 0), left leg (1.9, 12, 0).
- On a limb: negative pitch swings it forward and up (arm straight ahead at -90,
  straight up at -180); positive yaw swings a forward-pointing limb towards the
  player's right; positive roll moves the far end towards the player's right
  (right arm out, left arm in).
- The arms and head are not children of the torso: turning `torso` spins only
  the body box. Twists and leans use `body`, with the legs counter-rotated so
  they stay planted and the head counter-yawed so it keeps facing ahead.
- `rightItem` / `leftItem` apply just before the held item renders, inside the
  hand: translateToHand, rotX(-90), rotY(180), translate(+-1/16, 0.125, -0.625),
  then the channel: scale, translate(pos / 16), rotZ, rotY, rotX; then the item's
  own display transform (`item/handheld` third person: translate(0, 4, 0.5)/16,
  rotationXYZ(0, -90, 55), scale 0.85).
- A channel with no keyframes keeps the vanilla value: head pitch follows the
  camera, the torso does vanilla's crouch lean, and so on.
- Bends only work with bendylib, which this project does not have.

### Playback

- The tick counter starts at 0 and advances once per client tick (the stack
  ticks at the head of `Player.tick()`); frames in between blend by tickDelta.
- The easing named on a keyframe shapes the segment after it (Blockbench is the
  other way round). `"easeBeforeKeyframe": true` flips it.
- Easing names are case-insensitive with an optional `EASE` prefix: LINEAR,
  CONSTANT, IN/OUT/INOUT + SINE, CUBIC, QUAD, QUART, QUINT, EXPO, CIRC, BACK,
  ELASTIC, BOUNCE, and STEP. Anything else is LINEAR. CATMULLROM is broken in
  2.0.4 (it evaluates to n + 2).
- Before a channel's first keyframe it blends from vanilla (before beginTick) or
  from its default. After the last keyframe it holds until endTick, then blends
  back to vanilla by stopTick with the last keyframe's easing, and stops.
  A stopTick not after endTick becomes endTick + 3. `getLength()` returns
  stopTick, not endTick.
- Loops jump to returnTick after endTick; the last keyframe blends into the
  first keyframe at or after returnTick. Loops never stop on their own.
- Rotation outputs are wrapped into [-180, 180). Keyframe values themselves
  interpolate unwrapped.

### Modifiers (for the Java side)

- `replaceAnimationWithFade(standardFadeIn(n, ease), anim)` blends the old
  animation's wrapped outputs into the new one's, channel by channel. Two poses
  that look alike but use different Euler numbers will spin the sword during a
  fade, so every move here starts and ends on exactly the same ready-guard numbers.
- `MirrorModifier` swaps left and right arms, legs and items and negates
  position x and rotation yaw and roll of every part.
- First person: `THIRD_PERSON_MODEL` draws the real model from the camera,
  showing what `FirstPersonConfiguration` allows. `NONE` and `VANILLA` leave the
  vanilla first-person hand.

## Calibration

Better Combat's `two_handed_slam` renders as an overhead raise (arms up and
behind the head) then a bow with the blade driven down in front, and
`two_handed_slash_horizontal_right` as a flat sweep from the player's right to
left at chest height, with the torso turning and the legs and head
counter-rotated. That fixed the signs above.

## How these animations are built

- Emotecraft format, version 3, degrees. One move object per game tick with
  every channel and LINEAR easing, so the game shows the solved pose on each
  tick; the last tick uses INOUTSINE, which eases the library's 3-tick blend
  back to vanilla.
- endTick = the move's length in ticks; stopTick = endTick + 3. The charge loops
  ticks 0 to 19 (returnTick 0). The dive plays ticks 0 to 1, then loops 2 to 13.
- Channels: `body` (rotation and position), `head` yaw only, both arms
  (rotation, plus pivots where a move lifts the shoulders), both legs (rotation),
  `rightItem` (rotation and position). `leftItem` only in `combat_dash_side`,
  set so the game's mirrored left dash still holds the sword properly. Never
  keyed: torso, head pitch and roll, leg pivots, scales, bends.
- Both fists sit on the grip: the solver puts the right fist on the upper grip
  and the left fist 2.3 px down the handle, and turns the sword in the hand
  (`rightItem`) to point where the pose says. With rigid 12 px arms the two
  hands can only meet on a circle about 6 px in front of the upper chest, so
  reach comes from the body lean and twist and small shoulder shifts.
- The blade is double-edged and the sprite two-sided, so each key takes the
  edge that needs the smaller turn in the hand. That keeps `rightItem` near
  vanilla, away from gimbal lock and from the wrap that fades blend across.
- Checks run on every tick, at quarter ticks (what 60+ fps shows), through the
  mirrored side dash, and through 47 fades the combat rules allow (light attack
  into charge at ticks 6 to 8, cancels from recovery, parry into success, dash
  into Pass, dive into landing, combos). At the time of writing: no clipping
  anywhere, left fist within 1 px of the grip in every two-handed move.

## The Comet Maul

`maul_*` animations are made for the Comet Maul (`rig.WEAPONS`, picked by name through
`rig.weapon_for`): its sprite, key points and its item model's display transform. The Maul is held
by the same grip texels as Meridian and its display (1.25x) is moved so that grip lands exactly where
Meridian's does, so every `rightItem` value holds either weapon by the same point: the solver needs
no change, and the engine's own animations hold the Maul too. Where Meridian's versions put the
Maul's long head through the legs (the forward and back dashes, the stagger), the Maul has its own
(`maul_dash_forward`, `maul_dash_back`, `maul_stagger`), swapped in by the weapon's data
(`"animations"` in `weapons/comet_maul.json`). "guard" and "tip" are the head's near and far sides
along the haft, so `blade.py` gives the head's path. The spin of the Spin Slam uses `head_yaw`
(the head rides the turn instead of facing ahead) and legs turned with the body. Vertical slams turn
the head's flat a little toward the camera (`roll_ofs` 55) so first person sees a hammer, not an edge.
Measured: the head lands about 1.2 blocks ahead on the swings' contact frames (the moves' slam
`ahead` and L1's ring offset match it), 0.8 for the Gravity Well's plant.

## The Binary Edges

`edges_*` animations are made for the twin sickles (`rig.WEAPONS["binary_edges"]`, `dual=True`), in
`moves_edges.py`. A dual weapon holds a blade in each hand: the solver's `dual` keys give the left fist its own
target (`handsL`) and blade (`swordL`, with `swordL_world`, `roll_ofsL` and the auto twist `rzL`), each arm
reaches for its own point and turns its own sickle in the hand, and `leftItem` is written beside `rightItem`.
The left hand's item is drawn the way `ItemInHandLayer` draws it (translate(-1/16, ...), then the display that
`ItemTransform.apply(leftHand)` turns back), so the rig renders both. The sickle is single-edged: rolls are taken
as given (no flip to the other edge). Each sickle is held by Meridian's grip texel with a 0.8x display moved to
put that grip where Meridian's lands, so the hand maths is the same. `mirrored()` makes L2 from L1. A move whose
arms come round onto the other IK solution (the Gyre's spin, blades swept back) has its settle solved backward
from the guard (`author._settle_backward`), so every move still ends on the guard's exact numbers. The engine's
dashes, parry and stagger have Edges versions (the weapon's `animations`), since Meridian's put the left fist on
a two-handed grip. The dive spins its body, so its first person keeps vanilla's hands.

## In the game

`client/anim/PlayerAnimations` plays these with one layer per player (priority
3000, above Better Combat's 2000). Its chain, outside in: `FirstPersonView`,
`WalkingLegs`, `CombatClock` (the hit-stop speed modifier), the cross-fades
`play()` adds (2 ticks, from whatever shows), then the animation, wrapped in its
own `MirrorModifier` when mirrored. Per-animation choices live in `AnimationStyle`.

- Timing: the library ticks animations at the head of `Player.tick()`, and the
  combat runtime starts the local player's from `PlayerTickEvent.Pre` right
  after, so tick t shows during the move's tick t. `scripts/autotest.sh anim`
  checks it on the first active tick of every move.
- First person shows the blade alone (the arms are huge from inside the head and
  a high guard's fists fill the view), 0.3 blocks further ahead and 0.1 lower,
  framed as if looking 35 degrees down whatever the pitch, so a chest-high swing
  crosses the view near the crosshair where its slash arc is. The dive and its
  landing are shown where they really are (the player looks down on purpose).
  The charge and the dashes keep vanilla's first-person sword: the charge's glow
  shows, and a dash's blade trails out of view.
- Walking: once the player moves (vanilla's walk speed over 0.15), the legs hand
  over to vanilla's walk in 3 ticks, kept upright in the world under the leaning,
  twisting body and turned toward the movement, and the body stands at full
  height. Dashes, lunges and the dive plant their own legs for their first ticks.
- While a layer plays, the body faces where the head looks (every frame in
  first person).
- Contacts changed in the game review: L1's (it read as a thrust from the side
  and in first person) now has the blade already rising up the diagonal, and
  Zenith's now sweeps up through the target instead of pointing flat ahead.

## Known limits

- Rigid arms: "both hands overhead" is a high guard with the fists in front of
  the forehead. Hands behind the head would clip the hilt through the head
  between ticks. In first person the grip of `meridian_l3` and `meridian_line`
  hangs from the top of the view for about three ticks before the chop.
- The charge hold keeps the blade behind the right hip, out of first-person
  view; it plays with `FirstPersonMode.NONE`, so the vanilla first-person sword
  (with its glow stages) shows instead.

## The Choir Astrolabe

`astrolabe_*` animations (`moves_astrolabe.py`) are one-handed (`grip=0`): the right hand holds the instrument up before the shoulder, disc above the hand, and the free left arm (`left`) flicks bolts and shapes the gestures. They keep the instrument to the right of and below the eye line, since the animated first person draws it close to the camera. The previewer draws Meridian in its place (the grip is the same texel); the game draws the 3D astrolabe with its spinning rings.
