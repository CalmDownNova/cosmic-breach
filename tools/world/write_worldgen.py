"""Writes the Aetheria worldgen data (task W2): dimension, type, noise settings, biomes, features,
advancements, damage type. Run from the repo root: python tools/world/write_worldgen.py. The output is
committed; edit the tables below and rerun rather than editing the JSON by hand."""
import json
import os

ROOT = os.path.join("src", "main", "resources", "data")
NS = "cosmicbreach"


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def state(name, **props):
    s = {"Name": name}
    if props:
        s["Properties"] = {k: str(v) for k, v in props.items()}
    return s


# ---------------------------------------------------------------- dimension

write(f"{NS}/dimension_type/aetheria.json", {
    "ultrawarm": False,
    "natural": True,
    "piglin_safe": False,
    "respawn_anchor_works": False,
    "bed_works": True,
    "has_raids": False,
    "has_skylight": True,
    "has_ceiling": False,
    "coordinate_scale": 1.0,
    "ambient_light": 0.1,
    "logical_height": 480,
    "effects": f"{NS}:aetheria",
    "infiniburn": "#minecraft:infiniburn_overworld",
    "min_y": 0,
    "height": 480,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
    "monster_spawn_block_light_limit": 0,
})

write(f"{NS}/dimension/aetheria.json", {
    "type": f"{NS}:aetheria",
    "generator": {
        "type": "minecraft:noise",
        "settings": f"{NS}:aetheria",
        "biome_source": {
            "type": f"{NS}:aetheria_layers",
            "shattered_spires": f"{NS}:shattered_spires",
            "sunfield_terraces": f"{NS}:sunfield_terraces",
            "drift_belt": f"{NS}:drift_belt",
            "rift_abyss": f"{NS}:rift_abyss",
        },
    },
})

write(f"{NS}/worldgen/noise/terrain_seed.json", {"firstOctave": -7, "amplitudes": [1.0]})
# patches of bare Starfall Stone breaking up the Reach's grass (about 40 blocks across)
write(f"{NS}/worldgen/noise/rock_patches.json", {"firstOctave": -5, "amplitudes": [1.0, 0.6, 0.3]})


def y_above(y):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False}


def block_rule(s):
    return {"type": "minecraft:block", "result_state": s}


floor_top = {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor", "add_surface_depth": False,
             "secondary_depth_range": 0}

surface_rule = {"type": "minecraft:sequence", "sequence": [
    {"type": "minecraft:condition", "if_true": y_above(300), "then_run": {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": floor_top, "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition",
             "if_true": {"type": "minecraft:noise_threshold", "noise": f"{NS}:rock_patches", "min_threshold": 0.3, "max_threshold": 1e9},
             "then_run": block_rule(state(f"{NS}:starfall_stone"))},
            block_rule(state(f"{NS}:glimmer_grass")),
        ]}},
        block_rule(state(f"{NS}:starfall_stone")),
    ]}},
    {"type": "minecraft:condition", "if_true": y_above(160), "then_run": block_rule(state(f"{NS}:driftstone"))},
    block_rule(state(f"{NS}:umbral_basalt", axis="y")),
]}

seed = f"{NS}:terrain_seed"
write(f"{NS}/worldgen/noise_settings/aetheria.json", {
    "sea_level": 0,
    "disable_mob_generation": False,
    "aquifers_enabled": False,
    "ore_veins_enabled": False,
    "legacy_random_source": False,
    "default_block": state(f"{NS}:starfall_stone"),
    "default_fluid": state("minecraft:air"),
    "noise": {"min_y": 0, "height": 480, "size_horizontal": 1, "size_vertical": 2},
    "noise_router": {
        "barrier": 0, "fluid_level_floodedness": 0, "fluid_level_spread": 0, "lava": 0,
        "temperature": {"type": f"{NS}:isle_style", "seed_noise": seed},
        "vegetation": 0, "continents": 0, "erosion": 0, "depth": 0, "ridges": 0,
        "initial_density_without_jaggedness": 0,
        "final_density": {"type": f"{NS}:aetheria_terrain", "seed_noise": seed},
        "vein_toggle": 0, "vein_ridged": 0, "vein_gap": 0,
    },
    "spawn_target": [],
    "surface_rule": surface_rule,
})


# ---------------------------------------------------------------- features

def configured(name, ftype, config=None):
    write(f"{NS}/worldgen/configured_feature/{name}.json", {"type": ftype, "config": config or {}})


def placed(name, feature, placement):
    write(f"{NS}/worldgen/placed_feature/{name}.json", {"feature": feature, "placement": placement})


def count(n):
    return {"type": "minecraft:count", "count": n}


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}


def height_range(lo, hi):
    return {"type": "minecraft:height_range",
            "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
SURFACE = {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}


def rarity(n):
    return {"type": "minecraft:rarity_filter", "chance": n}


def scan_up_to_ceiling(steps):
    return {"type": "minecraft:environment_scan", "direction_of_search": "up",
            "target_condition": {"type": "minecraft:has_sturdy_face", "direction": "down"},
            "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            "max_steps": steps}


def scan_down_to_basalt(steps):
    """Down through air to the air block standing on Umbral Basalt (the Deep's floors)."""
    return {"type": "minecraft:environment_scan", "direction_of_search": "down",
            "target_condition": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": f"{NS}:umbral_basalt"}]},
            "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            "max_steps": steps}


ONE_DOWN = {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1}

# once per chunk, each drawing its own slice of big things
configured("spire_field", f"{NS}:spire_field")
placed("spire_field", f"{NS}:spire_field", [])
configured("asteroid_decor", f"{NS}:asteroid_decor")
placed("asteroid_decor", f"{NS}:asteroid_decor", [])

# the Reach
configured("crystal_stalactite", f"{NS}:crystal_stalactite")
placed("crystal_stalactite", f"{NS}:crystal_stalactite",
       [count(4), IN_SQUARE, height_range(323, 372), scan_up_to_ceiling(32), ONE_DOWN, BIOME])

configured("quartz_outcrop", f"{NS}:quartz_outcrop")
placed("quartz_outcrop", f"{NS}:quartz_outcrop", [rarity(3), IN_SQUARE, SURFACE, BIOME])
placed("quartz_outcrop_sparse", f"{NS}:quartz_outcrop", [rarity(7), IN_SQUARE, SURFACE, BIOME])

configured("terrace_pool", f"{NS}:terrace_pool")
placed("terrace_pool", f"{NS}:terrace_pool", [rarity(3), IN_SQUARE, SURFACE, BIOME])

configured("halo_moss", "minecraft:block_column", {
    "direction": "down",
    "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
    "prioritize_tip": True,
    "layers": [
        {"height": uniform(0, 4), "provider": {"type": "minecraft:simple_state_provider", "state": state(f"{NS}:halo_moss_plant")}},
        {"height": 1, "provider": {"type": "minecraft:simple_state_provider", "state": state(f"{NS}:halo_moss", age=25)}},
    ],
})
placed("halo_moss", f"{NS}:halo_moss",
       [count(12), IN_SQUARE, height_range(323, 372), scan_up_to_ceiling(24), ONE_DOWN, BIOME])

starbloom_patch = {
    "tries": 20, "xz_spread": 6, "y_spread": 2,
    "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {
            "to_place": {"type": "minecraft:simple_state_provider", "state": state(f"{NS}:starbloom")}}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:would_survive", "state": state(f"{NS}:starbloom")},
        ]}}],
    },
}
configured("starbloom_patch", "minecraft:random_patch", starbloom_patch)
placed("starbloom_patch", f"{NS}:starbloom_patch", [rarity(3), IN_SQUARE, SURFACE, BIOME])
placed("starbloom_meadow", f"{NS}:starbloom_patch", [count(2), IN_SQUARE, SURFACE, BIOME])

# fallen Driftwood on the Reach's ground (1.1): its first planks, sticks, table and fuel
configured("fallen_driftwood", f"{NS}:fallen_driftwood")
placed("fallen_driftwood", f"{NS}:fallen_driftwood", [rarity(2), IN_SQUARE, SURFACE, BIOME])
placed("fallen_driftwood_sparse", f"{NS}:fallen_driftwood", [rarity(4), IN_SQUARE, SURFACE, BIOME])

# Umbral Caps on the Deep's basalt floors (1.1): the Deep's food
umbral_cap_patch = {
    "tries": 16, "xz_spread": 4, "y_spread": 2,
    "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {
            "to_place": {"type": "minecraft:simple_state_provider", "state": state(f"{NS}:umbral_cap")}}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": f"{NS}:umbral_basalt"},
            {"type": "minecraft:would_survive", "state": state(f"{NS}:umbral_cap")},
        ]}}],
    },
}
configured("umbral_cap_patch", "minecraft:random_patch", umbral_cap_patch)
placed("umbral_cap_patch", f"{NS}:umbral_cap_patch", [count(3), IN_SQUARE, height_range(1, 143), scan_down_to_basalt(24), BIOME])


def ore(name, host, ore_block, size, air_discard):
    configured(name, "minecraft:ore", {
        "size": size, "discard_chance_on_air_exposure": air_discard,
        "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": host}, "state": state(ore_block)}],
    })


ore("ore_starsteel", f"{NS}:starfall_stone", f"{NS}:starsteel_ore", 7, 0.0)
placed("ore_starsteel", f"{NS}:ore_starsteel", [count(16), IN_SQUARE, height_range(323, 385), BIOME])
ore("ore_nebulite", f"{NS}:driftstone", f"{NS}:nebulite_ore", 6, 0.0)
placed("ore_nebulite", f"{NS}:ore_nebulite", [count(14), IN_SQUARE, height_range(162, 298), BIOME])
ore("ore_eclipsium", f"{NS}:umbral_basalt", f"{NS}:eclipsium_ore", 5, 0.3)
placed("ore_eclipsium", f"{NS}:ore_eclipsium", [count(16), IN_SQUARE, height_range(1, 143), BIOME])

# the Deep
configured("crystal_chandelier", f"{NS}:crystal_chandelier")
placed("crystal_chandelier", f"{NS}:crystal_chandelier",
       [count(7), IN_SQUARE, height_range(40, 140), scan_up_to_ceiling(24), ONE_DOWN, BIOME])


def lichen(name, block):
    configured(name, "minecraft:multiface_growth", {
        "block": block, "search_range": 20, "can_place_on_floor": True, "can_place_on_ceiling": True,
        "can_place_on_wall": True, "chance_of_spreading": 0.6, "can_be_placed_on": [f"{NS}:umbral_basalt"],
    })


lichen("neon_lichen_magenta", f"{NS}:magenta_neon_lichen")
lichen("neon_lichen_teal", f"{NS}:teal_neon_lichen")
placed("neon_lichen_magenta", f"{NS}:neon_lichen_magenta", [count(uniform(40, 70)), IN_SQUARE, height_range(1, 143), BIOME])
placed("neon_lichen_teal", f"{NS}:neon_lichen_teal", [count(uniform(40, 70)), IN_SQUARE, height_range(1, 143), BIOME])

# ---------------------------------------------------------------- biomes

MOOD = {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
         "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration",
         "top_layer_modification"]
GOLD_MOTES = {"options": {"type": "minecraft:dust", "color": [1.0, 0.86, 0.45], "scale": 0.6}, "probability": 0.004}


def features(**by_step):
    out = []
    for step in STEPS:
        out.append([f"{NS}:{name}" for name in by_step.get(step, [])])
    while out and not out[-1]:
        out.pop()
    return out


def audio(layer):
    """The layer's ambient bed and music (task W3a, sounds in tools/sound/aetheria.py)."""
    return {"ambient_sound": f"{NS}:ambient/{layer}",
            "music": {"sound": f"{NS}:music/{layer}", "min_delay": 2400, "max_delay": 6000,
                      "replace_current_music": False}}


def biome(name, effects, feats, spawners=None):
    write(f"{NS}/worldgen/biome/{name}.json", {
        "has_precipitation": False,
        "temperature": 0.8,
        "downfall": 0.0,
        "effects": effects,
        "spawners": spawners or {},
        "spawn_costs": {},
        "carvers": {},
        "features": feats,
    })


biome("shattered_spires", {
    "sky_color": 0x7FB2F0, "fog_color": 0xF3E6C4, "water_color": 0x8FC7E8, "water_fog_color": 0x6FA8C8,
    "grass_color": 0xD9C46E, "foliage_color": 0xC9B870, "particle": GOLD_MOTES, "mood_sound": MOOD,
    **audio("reach"),
}, features(
    local_modifications=["spire_field", "quartz_outcrop"],
    underground_ores=["ore_starsteel"],
    underground_decoration=["crystal_stalactite"],
    vegetal_decoration=["halo_moss", "starbloom_patch", "fallen_driftwood_sparse"],
), {"monster": [{"type": f"{NS}:shardling", "weight": 5, "minCount": 3, "maxCount": 3}]})

biome("sunfield_terraces", {
    "sky_color": 0x86B8F2, "fog_color": 0xF6E7C0, "water_color": 0xA6D8F0, "water_fog_color": 0x7FB6D0,
    "grass_color": 0xE8C45A, "foliage_color": 0xD8B85A, "particle": GOLD_MOTES, "mood_sound": MOOD,
    **audio("reach"),
}, features(
    lakes=["terrace_pool"],
    local_modifications=["spire_field", "quartz_outcrop_sparse"],
    underground_ores=["ore_starsteel"],
    underground_decoration=["crystal_stalactite"],
    vegetal_decoration=["halo_moss", "starbloom_meadow", "fallen_driftwood"],
))

biome("drift_belt", {
    "sky_color": 0x9FC4D8, "fog_color": 0xA8D8E8, "water_color": 0x9FDDEB, "water_fog_color": 0x6FB0C8,
    "grass_color": 0xA9C9C2, "foliage_color": 0x9BBFB8,
    "particle": {"options": {"type": "minecraft:white_ash"}, "probability": 0.01},
    "mood_sound": MOOD,
    **audio("drift"),
}, features(
    local_modifications=["asteroid_decor"],
    underground_ores=["ore_nebulite"],
))

biome("rift_abyss", {
    "sky_color": 0x2A1B4A, "fog_color": 0x25123F, "water_color": 0x5A2A7A, "water_fog_color": 0x1E0F33,
    "grass_color": 0x5B3D7A, "foliage_color": 0x4A2F6E,
    "particle": {"options": {"type": f"{NS}:abyss_mote"}, "probability": 0.004},
    "mood_sound": MOOD,
    **audio("deep"),
}, features(
    underground_ores=["ore_eclipsium"],
    underground_decoration=["crystal_chandelier"],
    vegetal_decoration=["neon_lichen_magenta", "neon_lichen_teal", "umbral_cap_patch"],
))

# ---------------------------------------------------------------- attunement and the Shear

for layer in ("drift", "deep"):
    write(f"{NS}/advancement/attunement/{layer}.json", {
        "criteria": {"attuned": {"trigger": "minecraft:impossible"}},
        "requirements": [["attuned"]],
    })

write(f"{NS}/damage_type/shear.json", {"message_id": "cosmicbreach.shear", "exhaustion": 0.0, "scaling": "never"})
# every damage type of the mod in these tags (the guardians' joined after W2: keep each list whole)
DAMAGE_TAGS = {
    "bypasses_armor": ["shear", "scorch", "prism_core", "hollow_grasp"],
    "bypasses_cooldown": ["shear"],
    "no_knockback": ["shear", "scorch", "meteor", "prism_beam", "prism_core", "chord"],
}
for tag, types in DAMAGE_TAGS.items():
    write(f"minecraft/tags/damage_type/{tag}.json", {"replace": False, "values": [f"{NS}:{t}" for t in types]})

print("worldgen data written")
