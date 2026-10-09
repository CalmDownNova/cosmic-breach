"""Regenerate every Cosmic Breach art asset, then the review previews.

Run from anywhere:  python tools/art/build_all.py
Exits non-zero if the Shardling preview finds a UV or mapping problem.
"""
from __future__ import annotations

import importlib
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

STEPS = [
    "gen_meridian",      # textures/item/meridian.png + meridian_glow1..3.png
    "gen_binary_edges",  # textures/item/binary_edges*.png: the pair, each sickle, glow stages
    "gen_starshard",     # textures/item/starshard.png
    "gen_reverie_draught",  # textures/item/reverie_draught.png
    "gen_particles",     # textures/particle/*.png
    "gen_fx",            # textures/fx/*.png
    "gen_weather",       # textures/fx/meteor_*.png, textures/gui/weather_vignette.png (W3b)
    "gen_shardling",     # geo, animations, textures/entity/shardling*.png
    "gen_blocks_reach",  # textures/block/: the Upper Reach (W1)
    "gen_blocks_drift",  # textures/block/: the Drift, plus item/driftwood_door.png
    "gen_blocks_deep",   # textures/block/: the Deep
    "gen_blocks_zones",  # textures/block/: the layer 3 zones' ground, caps, stems, curtains (1.2)
    "gen_materials",     # textures/item/: metals, gems, drops, seeds
    "gen_provisions",    # textures/item/: the levels' food, Starhide, Rime Thread, two pickaxes; block/umbral_cap (1.1)
    "gen_vanguard",      # the Starfall Vanguard: geo, textures/armor/, icons; the tier trims of every weapon and piece
    "gen_driftweave",    # the Driftweave: geo, textures/armor/, icons and trims (G2b)
    "gen_regalia",       # the Choir Regalia: geo, textures/armor/, icons and trims, the Hymn's ring, the Aligned glyph and icon
    "gen_onboarding",    # the Starfall Shard, the Codex, the torn page, the lit Breach Frame (W4; after gen_blocks_reach)
    "gen_colossus",      # the Prism Colossus and Prism Shards (geo, animations, textures) and the Crown Spire's blocks and items
    "gen_structures",    # the Lens Array's pieces, vaults, emitter, light blocks, loose pieces, their block models (W5)
    "gen_crypt",         # the Choir Floor, the crypt's traps and vault, their models, the Choir and tell FX textures (W6)
    "gen_unsung",        # the Unsung's three masks (geo, animations, textures) and the Silent Nave's blocks and item (G8)
    "gen_stalker",       # the Hollow Stalker (geo, animations, textures) and the Mask Shard block (G7)
    "gen_astrolabe",     # the Choir Astrolabe: sprite, the hand model's texture, its star and corona FX, its item model (G7)
    "gen_sanctum",       # the Breach Sanctum's stone, Gate, locks, throne, vault, the Heart and charms, the Voidsick icon (W7)
    "gen_last_light",    # Last Light: sprite, glow stages, Sunlight gem layers, item models (G9b)
    "gen_umbra_cantor",  # the Umbra Cantor: sprites by draw and glow stage, item models (G9b)
    "gen_heliarchs_crown",  # the Heliarch's Crown: textures, block model, state, item model, loot (G9b; after gen_blocks_reach)
    "gen_heliarch",      # the Hollow Heliarch (geo, animations, textures, glowmask), its monoliths, the Reliquary, its FX textures (G9a)
    "gen_curios",        # four accessories' icons, the Sunshard Compass's 16 needle frames, the Halo's shard, the Vesper slow icon (G6a)
    "gen_status_icons",  # the Rift, Scorch and Silenced effect icons, missing until playtest 3
    "gen_mounts",        # the Lumen Stag and the Drift Manta (geo, animations, textures, glowmasks), their gear's icons, the tack slot (G6b)
    "gen_stable_crystal",  # the Stable Crystal, empty and full (1.1 task B2.7)
    "gen_shrines",  # the four boss shrines: geo, idle animation, texture and glowmask (1.1 tasks B4 and B5)
    "gen_familiars",     # the Emberwisp, Gravikin and Prism Moth (geo, animations, textures), the Star Egg, the lantern, the brazier (G10)
    "gen_satchel",       # textures/item/satchel.png (1.2)
    "gen_drift_jelly",   # the drift jelly (geo, animations, textures, glowmask), the two gels and the Skim icon (1.2 Lane C)
    "preview_items",     # previews/items.png, previews/fx.png
    "preview_shardling", # previews/shardling_*.png and the model checks
    "preview_blocks",    # previews/blocks.png, previews/items2.png
    "preview_vanguard",  # previews/vanguard_*.png
    "preview_sets",      # previews/driftweave_*.png, previews/choir_regalia_*.png
    "preview_colossus",  # previews/colossus_*.png, previews/shard_views.png
    "preview_unsung",    # previews/unsung_views.png
    "preview_mounts",    # previews/lumen_stag_*.png, previews/drift_manta_*.png
    "preview_familiars", # previews/familiar_views.png
    "preview_drift_jelly",  # previews/drift_jelly_views.png, drift_jelly_anim_*.png (python tools/art/preview_drift_jelly.py --media also writes the Media renders)
]


def main() -> int:
    status = 0
    for name in STEPS:
        t0 = time.time()
        print(f"== {name}")
        result = importlib.import_module(name).main()
        if isinstance(result, int) and result:
            status = result
        print(f"   ({time.time() - t0:.1f}s)")
    return status


if __name__ == "__main__":
    sys.exit(main())
