# Cosmic Breach art generators

Every texture, model and animation listed below is produced by these scripts,
so it can be regenerated and tuned. Change a script, rerun it, look at
`previews/`. Do not hand-edit the outputs: the next run overwrites them.

## Setup

Python 3.10 or newer with numpy and Pillow:

    pip install numpy pillow

(Built with Python 3.12, numpy 2.4, Pillow 11.3.)

## Rebuild everything

    python tools/art/build_all.py

Takes about ten seconds. Output is deterministic (identical bytes on every
run), so a rerun with no script changes leaves git clean. It exits non-zero if
the Shardling checks find a UV problem.

Each script also runs on its own, from any folder, for example
`python tools/art/gen_meridian.py`.

## What each script makes

Outputs go to `src/main/resources/assets/cosmicbreach/`.

| Script | Writes | Size |
|---|---|---|
| `gen_meridian.py` | `textures/item/meridian.png`, `meridian_glow1.png`, `meridian_glow2.png`, `meridian_glow3.png`, plus `previews/meridian_stages.png` | 32x32 each |
| `gen_binary_edges.py` | `textures/item/binary_edges.png` (the pair crossed, a star above: the inventory icon), `binary_edges_right.png` and `binary_edges_left.png` (one crescent sickle each, as the hands hold them: cyan star and edge light on the right, violet on the left), each with `_glow1` to `_glow3` (the Resonance light runs along the inside edge), plus `previews/binary_edges_stages.png` and `previews/binary_edges_sickles.png`. The sickle is a continuous shape sampled at pixel centres; its grip is Meridian's texel | 32x32 each |
| `gen_comet_maul.py` | `textures/item/comet_maul.png`, `comet_maul_glow1.png` to `glow3.png` (the meteorite head's fissures heat up), plus `previews/comet_maul_stages.png`. Same diagonal layout and grip texels as Meridian, so the shared animations hold both | 32x32 each |
| `gen_starshard.py` | `textures/item/starshard.png` | 16x16 |
| `gen_reverie_draught.py` | `textures/item/reverie_draught.png` | 16x16 |
| `gen_particles.py` | `textures/particle/star_glint.png`, `ring.png` | 32x32 |
| | `textures/particle/spark.png`, `shard_0.png`, `shard_1.png`, `shard_2.png` | 16x16 |
| `gen_fx.py` | `textures/fx/slash.png` | 128x32 |
| | `textures/fx/glow.png` | 64x64 |
| | `textures/fx/beam.png` | 16x128 |
| | `textures/fx/crack.png` (ground cracks radiating from a strike: drawn dark, then as embers) | 128x128 |
| `gen_shardling.py` | `geo/entity/shardling.geo.json`, `animations/entity/shardling.animation.json`, `textures/entity/shardling.png`, `textures/entity/shardling_glowmask.png`, and the telegraph variants `shardling_tell.png` (crest spines gold) and `shardling_spit.png` (eyes and jaw line red), each with its `_glowmask.png` | textures 64x64 |
| `gen_blocks_reach.py` | `textures/block/`: Starfall Stone (plain, polished, bricks), Glimmer Grass (top, side), Spire Quartz, Starsteel Ore and block, Meteorite, Halo Moss (tip, body), Starbloom and its four crop stages, Breach Frame (top, side) | 16x16 |
| `gen_blocks_drift.py` | `textures/block/`: Driftstone (plain, bricks), Nebulite Ore and block, Driftwood (log, top, stripped, planks, door, trapdoor), Rimeglass; `textures/item/driftwood_door.png` | 16x16 (the door is two) |
| `gen_blocks_deep.py` | `textures/block/`: Umbral Basalt (side, top, polished, bricks), Rift Glass, Eclipsium Ore and block, Neon Lichen (magenta, teal) | 16x16 |
| `gen_materials.py` | `textures/item/`: raw metals, ingots and nuggets (one template per shape, recoloured per metal), Heartstone, Gyre Core, Gyre Blade, Leviathan Scale and Pearl, Prism Heart, Silent Sigil, Solar Ember, Hymn Crystal, Solar Heart, Umbral Silk, Starbloom Seeds | 16x16 |
| `gen_provisions.py` | `textures/item/`: Halo Berries, Lumen Venison and its seared cut, Manta Fillet and its seared cut, Starhide, Rime Thread, the Starsteel and Nebulite Pickaxes; `textures/block/umbral_cap.png` (the item shows it too) (1.1) | 16x16 |
| `gen_vanguard.py` | the Starfall Vanguard (GDD 5.1): `geo/armor/starfall_vanguard.geo.json` (GeckoLib armor bones, one model for the four pieces), an empty `animations/armor/starfall_vanguard.animation.json`, `textures/armor/starfall_vanguard.png` and `_glowmask.png`, the icons `textures/item/starfall_vanguard_{helm,chestplate,greaves,boots}.png`, and the tier trim layers: `<item>_trim.png` for those four and for `meridian`, `comet_maul`, `binary_edges` and `binary_edges_right` (the sprite's outermost texels, white; the game tints them by tier) | 128x64, icons 16x16 |
| `preview_vanguard.py` | `previews/vanguard_views.png` (front, side, back, 3/4 on a plain mannequin), `vanguard_crest.png` (the crest at rest, running, falling), `vanguard_glow.png`, `vanguard_icons.png` (each icon with each tier's trim), `vanguard_uv.png` | |
| `armor_model.py` | shared by the sets after the Vanguard: cubes and bones, mirroring, `ring_cubes` (halos and orbit rings as polygons of thin bars in the XZ, XY or YZ plane), `thin_cube` (a side under a unit built a unit thick and shrunk by a negative inflate, so box UV keeps whole texels), UV packing, painting, the geo JSON, icons and trims | |
| `gen_driftweave.py` | the Driftweave (GDD 5.1): `geo/armor/driftweave.geo.json` (a hood with a visor band and a thin halo, slim Nebulite plates over star-flecked night cloth, a star-dust scarf whose two tails and a half-cape hang down the back in chains of bones the game streams with the wearer's velocity, ankle fins that flare on dashes), `textures/armor/driftweave.png` and `_glowmask.png` (star dust: the game adds it as light, brighter with dash charges ready), icons `driftweave_{hood,coat,leggings,boots}.png` and their trims | 128x128, icons 16x16 |
| `gen_regalia.py` | the Choir Regalia (GDD 5.1): `geo/armor/choir_regalia.geo.json` (a circlet with a crest, three small rings on a halo line behind the head, an ivory robe and plate engraved with orbit lines, a gold ring turning over each pauldron, a mantle down the back and long robe panels on the legs), `textures/armor/choir_regalia.png` and `_glowmask.png` (the engraved lines and glyphs, pulsed on Vesper's beat), icons `choir_regalia_{circlet,vestment,tassets,sabatons}.png` and their trims, `textures/fx/hymn_ring.png` (the Hymn's ring on the ground), `textures/fx/aligned_glyph.png` (over Aligned enemies), `textures/mob_effect/aligned.png` | 128x128, icons 16x16, ring 256x256 |
| `preview_sets.py` | `previews/{driftweave,choir_regalia}_views.png` (front, side, back, 3/4 on a plain mannequin), `_moving.png` (the poses the game gives them while moving), `_glow.png`, `_icons.png` | |
| `gen_onboarding.py` | `textures/item/`: Starfall Shard (a cool crystal with a gold star in its heart), Starfall Codex, Torn Codex Page; `textures/block/`: the Starfall Shard as it stands in its crater (three crystals, a cross model), `breach_frame_top_active` and `breach_frame_side_active` (W1's frame with its copper and star inlay lit; runs after `gen_blocks_reach.py`) | 16x16 |
| `gen_structures.py` | the first dungeons (W5): `textures/block/` for the Lens Array (pedestal, mirror and its fixed or gold loose rim, splitter, filters, Umbral block, focus, receptors in four colours dim and lit, Warden Eye asleep and awake, socket, Sun Aperture closed and open, core tile), the two vaults (front, ready front, side, top), Sunstone, the Nebulite Lamp, the Kinetic Emitter; `textures/item/` loose mirror and filters; and the hand-made block models in `models/block/` they need (the data run writes the block states). Reads the W1 textures, so it runs after `gen_blocks_*.py` | 16x16 |
| `gen_crypt.py` | The Hollow Crypt (W6): `textures/block/` resonance_floor, conductor_plinth/robe/hood/mask/baton, crypt_vault_front(_ready)/side/top, void_pocket_top, pocket_seal, starfall_chute, gravity_piston_head; `textures/fx/` choir_fill, choir_glyphs (the eight pads' glyphs in a row), choir_ring, rift_cracks, gravity_rings, chute_glint; and the block models of the Conductor, the vault, the tiles, the seal, the chute and the piston (the Void Rift tiles, the Gravity Sigil and the Gravity Piston reuse the Umbral Basalt textures on purpose: only careful eyes see them) | 16x16; FX 16 to 256 wide |
| `preview_blocks.py` | `previews/blocks.png` (every block texture at 8x with a 3x3 tiling at 2x beside it, so seams show), `previews/items2.png` (the W1 items at 8x, 2x and 1x, and at 4x on the Reach's sky blue) | |
| `preview_items.py` | `previews/items.png` (items and particles at 8x on mid grey, items also at 1x and 2x, particles also tinted gold and turquoise), `previews/fx.png` (FX at 4x on grey and on black) | |
| `preview_shardling.py` | `previews/shardling_side.png`, `_front`, `_top`, `_34`, `shardling_views.png` (all four), `shardling_glow.png` (only the glowmask lit), `shardling_uvcheck.png` (texture with every UV island boxed), `shardling_anim_<clip>.png` (key pose plus a six-frame filmstrip per clip) | |
| `gen_colossus.py` | the Prism Colossus (G3): `geo/entity/prism_colossus.geo.json` (built at half size, drawn at x2; free fists for the slams), `animations/entity/prism_colossus.animation.json` (idle, dormant, intro, refraction charge and fire, burst, break, rise, fracture, shattered, reform, dying), `textures/entity/prism_colossus{,_dormant,_cracked,_white}.png` with glowmasks; the Prism Shard (`prism_shard.geo.json`, its animations, red, green and blue textures); the Crown Spire's block textures, block models and states (crown crystal with a stepped tip, pillar stump, rising and falling light (animated), gilded bricks, prism glass, Prism Altar) and the Guardian Echo and Heart of a Dying Star icons | entity 256x256, shard 64x32, blocks 16x16 |
| `preview_colossus.py` | `previews/colossus_views.png` (front, side, back, three quarters), `colossus_poses.png` (dormant, idle, charge, burst tell, broken, roar), `shard_views.png` | |
| `gen_unsung.py` | the Unsung (G8, polished G8p): `geo/entity/unsung_{alto,tenor,bass}.geo.json` (each mask a porcelain shell of one-unit columns, runs merged, per-face UV so one painted front covers them and the steps' walls sample it; cut into seven shards along jagged lines that part when it breaks; two porcelain lips that part over the mouth's O; a plate of inner light behind the eyes and mouth; a cowl of void cloth from the mask's rim, flared and drooping back; a cloak of three panels fading into smoke), `animations/entity/unsung_{voice}.animation.json` (rest, rise, float, sing, inhale, drop, fall, fallen, shatter, broken; shroud on its own loop), `textures/entity/unsung_{voice}(_broken)(_glowmask).png` (glazed porcelain tinted per voice, most of its light in the glowmask), `textures/fx/note_ring.png`, the Rift Abyss's mote, the Hymnal Altar's, the lichen windows' and the circles of silence's textures, block models and states, the Choir Pendant's icon and model | entity 128x128 |
| `gen_sanctum.py` | the Breach Sanctum (W7): `textures/block/` sanctum_ivory, sanctum_ivory_bricks (ivory gone dark), sanctum_gilt (tarnished gold with an engraved sun), sanctum_ember (glowing fissures, emissive), sanctum_rift_lamp (Rift Glass lit from within, emissive), sanctum_umbral, choir_pillar_side/top, sanctum_gate and its translucent open veil, throne_seal, eclipse_lock(_lit), the Regent's Throne's parts and the Heart in its socket, sanctum_vault_front(_ready)/side/top; `textures/item/` dying_star_heart, event_horizon_lens, hourglass_of_vesper; `textures/mob_effect/voidsick.png`; every block model and state (the stairs' state follows vanilla's createStairs) and the three item models | 16x16; icon 18x18 |
| `gen_heliarch.py` | the Hollow Heliarch (G9a): `geo/entity/hollow_heliarch.geo.json` (free bones the game places: the core, six sun-ray plates with an enamel sun, two dark bronze gauntlets banded in gold with jointed fingers; built at a quarter size, drawn at x4), `animations/entity/hollow_heliarch.animation.json`, `textures/entity/hollow_heliarch.png` and its glowmask; `textures/block/` heliarch_monolith(_edge) (umbral stone, gold only as inlaid lines) and heliarch_monolith_inlay (those lines alone, an overlay the models light full-bright), the Reliquary's faces, lid and inside, with the monoliths' models by facing and pips left, the Reliquary's closed and open models and both states; `textures/fx/` heliarch_corona, heliarch_rift and its rim (the void tendrils), heliarch_sun, heliarch_seal | entity 256x128; monolith face 48x64; Reliquary 16x16; seal 256x256 |
| `preview_unsung.py` | `previews/unsung_views.png` (each mask singing and humming, front, three quarters, side and from below, in a dim light with its glow added) | |
| `preview_unsung.py` | `previews/unsung_views.png` (each mask singing, humming and broken, from the front, three quarters and the side, and the three together from afar; drawn like the game: the entity's two world lights, the lightmap, the render colour, alpha blending in model order, then the glowmask added) | |
| `gen_mounts.py` | the celestial mounts (G6b): `geo/entity/lumen_stag.geo.json` (a tall white stag: deep chest, slender jointed legs, a long neck, and crystal antlers of turned bars along a curved beam with five tines; gear bones for the Astral Saddle, both bardings, the Comet Bridle and the Halo Reins' collar), `drift_manta.geo.json` (a flat pale body, three-segment swept wings of slabs, rolled head lobes, a whip tail; gear bones for the Drift Harness, Nebulite plates, the Nebula Reins and the Gale Fins), their animations (stag: idle, walk, run, leap, glide, eat; manta: hover, swim, glide, rest), `textures/entity/{lumen_stag,drift_manta}.png` with `_glowmask.png` (the antlers, scaled by trust in game; the song spots, flared on each note) and `_gear_glowmask.png`, the nine gear icons, the spawn eggs' models and `textures/gui/sprites/container/mount/tack_slot.png`. Gear bones are named `gear_<item id>[_part]`; the game shows the worn ones | entity 128x128, icons 16x16 |
| `preview_mounts.py` | `previews/lumen_stag_views.png` (front, side, back, three quarters; wild, then with two sets of gear), `lumen_stag_poses.png`, `drift_manta_views.png` (plus from above), `drift_manta_poses.png` | |
| `gen_stable_crystal.py` | `textures/item/stable_crystal.png` and `stable_crystal_full.png`: the stow item for a tamed mount, empty (a quiet pale crystal in a gold band) and holding one (the same shape with a warm heart, lit from within) | 16x16 |
| `gen_familiars.py` | the combat familiars (G10): `geo/entity/{emberwisp,gravikin,prism_moth}.geo.json` and their animations (a tiny sun: a white-hot core in a spiked corona on three planes, two crowns the game turns against each other; a pebble golem: a round grey-blue boulder on stubby legs, arms of stacked pebbles, a flat pebble cap, a jagged violet crack painted down its chest and three pebbles orbiting it; a glass moth built at twice the size and drawn at half: an ivory body, feathery antennae, four see-through wings with rainbow edges and veins), `textures/entity/<kind>.png` and `_glowmask.png`, the Star Egg (plain and by kind), the Familiar Lantern (lit by kind, out, dark) with their item models and overrides, the Brazier of Solenne's textures, models and state, the Refract, Kindled and Gravity Drag icons | entity 64x64, items 16x16, icons 18x18 |
| `preview_familiars.py` | `previews/familiar_views.png` (each familiar front, side, three quarters and small) | |
| `gen_shrines.py` | the four boss shrines (1.1): `geo/block/shrine_<kind>.geo.json`, `animations/block/shrine_<kind>.animation.json` (one idle loop), `textures/block/shrine_<kind>.png` and `_glowmask.png`, for colossus, leviathan, unsung and heliarch. Shapes and idle loops from `tools/art/shrines/<kind>.cubes.json` (built in Blender by `tools/art/blender/shrine_models.py`), placeholders without them; paints from the palettes at the top of the script. `python tools/art/gen_shrines.py [kind ...]` | 128x128 |
| `blender/shrine_models.py` | (Blender 5.2, headless) the four shrines' designs as boxes in GeckoLib units, checked (render box, lean directions, no z-fighting faces, no sky showing through from the front or back, nothing passing through anything or leaving the render box over the idle loop), exported to `shrines/<kind>.cubes.json`, clay views into `C:\Users\puppy\Media\Aetheria\Shrines\<round>\` (`--names` gives the files neutral names for a blind round) | n/a |
| `blender/shrine_render.py` | (Blender 5.2, headless) textured EEVEE renders of the generated shrine models, day and dusk, near and 24 blocks off (`--names` as above) | 640x640, 160x160 |
| `preview_shrines.py` | contact sheets of those renders, the blind review sheet with its key, and `previews/shrines.png`. A blind round: `--deal` picks neutral names (shrine_w to shrine_z, shuffled) into `key_do_not_open.txt` and prints the Blender arguments, `--blind` builds the sheets from those files and scrubs the folder (no PNG text chunks, one file time) | 256 per view |
| `build_all.py` | runs all of the above in order | |

Shared code: `common.py` (paths, drawing helpers), `bedrock_model.py`
(Bedrock geometry and animation maths done the way GeckoLib does it) and
`blockart.py` (tileable noise, ramps by share, bricks, Voronoi facets, ASCII
sprites, for the W1 blocks and items).

For a quick look at a few textures while tuning:
`python tools/art/preview_blocks.py out.png starfall_stone raw_nebulite ...`

## Conventions

**Items** (`meridian*`, `starshard`): crisp pixel art, hard alpha (0 or 255),
small palettes (about 20 colours for the Meridian, 8 for the starshard), a
darker outline, light from the top left. Both lie on the vanilla sword
diagonal: grip or base bottom left, point top right.

**Meridian charge stages**: `glow1` is a gold thread up the lower half of the
fuller, `glow2` a brighter band up most of the blade with a pale halo, `glow3`
the whole blade lit white-gold with a four-point glint on the tip and two
white-hot sparks on the edges. The glint's arms break the tip's outline, so
stage 3 is the only stage whose silhouette changes.

**Blocks** (W1, `gen_blocks_*.py`): 16x16, tile seamlessly (every noise field
wraps), hard alpha except the two glasses, light from the top left. Each
material is a short ramp picked by hand, darkest first; noise maps to it by
share (the fraction of texels per shade), which is the knob to turn. Every
texture seeds its own random stream from its name, so output is identical on
every run and tuning one never reshuffles another. The layers follow GDD 2.2's
look rule: the Reach is daylight (white stone with gold flecks, pale gold
grass, turquoise crystal), the Drift silver and cyan with grey-blue stone, and
only the Deep is dark (black basalt with an indigo cast, magenta and teal
lichen). Ores show their layer's stone with nuggets rimmed dark on the shadow
side, so pale Starsteel still reads on pale Starfall Stone; Eclipsium is dark
nuggets rimmed in gold. Driftwood is petrified: silver wood with cyan veins.
First in-game look (the `blocks` autotest): the planks read as grey stone bricks
until the grain ran along the boards and the nail rows went, and Glimmer Grass
read as sand until the speckle got finer with olive shadows.

**W1 items** (`gen_materials.py`): the Meridian and Starshard style. Raw metal,
ingot and nugget are one template each, recoloured: Starsteel pale silver-gold,
Nebulite cyan on top and violet underneath, Eclipsium near-black with gold
edges. The ingot is a box rasterised in 2:1 projection (`ingot_rows`), the rest
ASCII sprites or shaded shapes.

**Particles and FX**: white RGB everywhere (including fully transparent
texels, so filtering never pulls in a dark fringe), shape in straight,
non-premultiplied alpha, zero alpha on the border. The three shard particles
are crisp greys (255, 214, 168 and a 128 edge) so they read as facets and
still tint cleanly. `slash.png` is laid out for a swept ribbon: u = 0 (left)
is the fading tail, u = 1 (right) is the crisp leading edge at the blade, v
runs across the ribbon with a soft falloff at both edges.

**Shardling** (GeckoLib 4, geometry 1.12.0, box UV, animation format 1.8.0):

- 16 units = 1 block, y up, the head points to -Z, the creature's left is +X.
- Bones: `root` (pivot on the ground), `body`, `neck`, `head`,
  `leg_front_left`, `leg_front_right`, `leg_back_left`, `leg_back_right`,
  `spines` with `spine_1` to `spine_6` (front to back), `tail`.
- Size at rest: 0.86 blocks nose to rump, 0.53 at the shoulder, the crest
  tops out at 0.88, 1.19 blocks long including the tail spines, 0.28 wide.
  The game draws it 1.25 times that size (`Shardling.SCALE`, with a hitbox
  to match), so it reads at a distance; build and preview it at 1.0.
- Revised after the first in-game look (Task 7): it read as a small crystal
  unicorn or pony. The snout crystal went (`SNOUT_CRYSTAL`, it stood up like a
  horn from the front), the shins lost a unit with everything above them
  dropping with them (`LEG_DROP`), the shins became dark "socks" with crystal
  claws instead of gold-banded hooves, the crest spines splay alternately left
  and right (`CREST_SPLAY`, so from the front the crest is a V, not one spike
  over the head), and the stone went from chalk to slate so the body reads on
  the arena's white calcite.
- Rotations are degrees. Positive X tips the front of a part down and swings
  a hanging leg back; the spines lean back with negative X. Animated rotation
  is added to the bind rotation in the geo file (GeckoLib does this).

| Clip | Length | Loop | What it does |
|---|---|---|---|
| `idle` | 2.0 s | loop | breathing, a slow wave down the crest, tail sway |
| `walk` | 0.6 s | loop | trot: diagonal leg pairs, body height solved so a foot is always down |
| `run` | 0.4 s | loop | bounding gait, body pitches and leaves the ground mid-stride |
| `crouch_tell` | 0.6 s | hold last frame | lunge warning: chest drops, head low, crest stands up and fans, holds |
| `lunge` | 0.2 s | hold last frame | from the crouch to a stretched leap, legs thrown fore and aft |
| `recover` | 0.8 s | play once | lands nose first, pushes up, stumbles on a lifted paw, settles to neutral |
| `spit_tell` | 0.5 s | hold last frame | rears back, head high with the snout still aimed forward, holds |
| `spit` | 0.25 s | play once | thrusts forward along the snout line, back to neutral |
| `stagger` | 0.5 s | play once | recoil and flinch, back to neutral |

Each posed clip starts on the previous clip's end pose (`lunge` starts on the
crouch, `recover` on the lunge, `spit` on the spit tell), so they chain
without a jump. `lunge` holds its last frame so the leap pose stays until the
game switches to `recover`.

**Glowmask**: only the spines (crest and tail) and the eyes (two texels on
each side of the head, plus the glints on its front face) are opaque;
everything else is exactly (0, 0, 0, 0). GeckoLib's auto-glowing
layer takes the colour from the base texture and only uses the mask to know
which texels glow, so the mask is a copy of those texels.

**Telegraph variants**: the game swaps the whole texture for the length of a
telegraph (GDD 4.1: gold means parryable, red means dodge).
`shardling_tell.png` paints the crest spines in a gold ramp for the Splinter
Lunge; `shardling_spit.png` paints the eyes and the jaw line red for the Shard
Spit, and its glowmask lights the jaw line too.

**Starfall Vanguard** (GeckoLib 4 armor, geometry 1.12.0, box UV, 1 texel a unit like the player skin):

- Bedrock humanoid coordinates: feet at y = 0, head 24 to 32, front to -Z, the wearer's right to -X. Bones are
  GeckoLib's armor bones (`armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`, `armorRightLeg`,
  `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot`) with the vanilla humanoid's pivots; GeoArmorRenderer copies
  the pose onto them and shows the ones of the slot being drawn.
- The crest is three bones (`crest_tail_1` to `_3`, chained, behind the helm). Their bind rotations are the
  rest droop (`CREST_REST`); the game sets them every frame from the wearer's speed (`VanguardCrest`), so keep
  the two in step. Negative X droops a segment that points back.
- Paint: dark meteoric iron with sparse thumbprint dimples (dark, lit on the lower rim), pale Starsteel bands
  (brow, collar, belt, cuffs, knees), rocky pauldrons with thin glowing cracks, orange seams. The glowmask holds
  the seams, cracks and crest in warm white (the crest fading toward its tip); the game draws it emissive in
  the tier's colour, turning white-hot with Heat.
- First in-game look: the plate was noisy (too many lit dimples and rust spots) and read as camouflage; the
  dimples went sparse and dark, the rust went, the leg seams were cut back, and the pauldrons grew so the
  shoulders read broad.

## The checks `preview_shardling.py` runs

1. Every cube's UV island is inside the 64x64 texture, fully painted, and no
   two islands overlap.
2. Texel by texel, the 3D point the painter meant to paint is the point
   GeckoLib actually maps that texel to (box UV rules and quad corner order
   read from the GeckoLib 4.9.3 jar with javap). This is what catches a facet
   painted on the wrong face or upside down.
3. The lowest point of the model over each clip, so feet do not sink into the
   ground. Posed clips name their planted legs and `gen_shardling.py` solves
   those leg angles against the real geometry, so this stays true after the
   legs are reshaped.

## Tuning knobs

- Meridian: palette and blade, guard and pommel geometry at the top of
  `gen_meridian.py` (`K` blade width, `TIP_TAPER`, `STAR_POINT`,
  `STAR_INNER`); charge stages in `build_glow`.
- Starshard: the key points and `FACETS` table in `gen_starshard.py`.
- Particles and FX: one function per sprite; sizes and falloffs are the
  arguments and the constants inside.
- Shardling: palette at the top (`STONE`, and `GOLD_SPINE`, `RED_*` for the
  telegraph variants), geometry in `build_model` and `CREST` (spine position,
  height, lean), `CREST_SPLAY`, `LEG_DROP`, `SPINE_STYLE` ("diamond" or
  "blade"), `EARS` (off: tried, they muddle the crest), `SNOUT_CRYSTAL` (off:
  it read as a horn), painting in `paint_texel`, clips in the `anim_*`
  functions and the `*_pose` functions.

## Not generated here

- Item models (`models/item/*.json`) and whatever selects `meridian_glowN`
  by charge level.
- Particle definitions (`particles/*.json`) that point the particle types at
  these sprites.
- Blending: the FX and particle alpha is straight. A pure additive
  `ONE, ONE` blend would ignore alpha; use `SRC_ALPHA, ONE` (or translucent)
  with these, or premultiply them first.
