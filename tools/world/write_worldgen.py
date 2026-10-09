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
            "lichen_gardens": f"{NS}:lichen_gardens",
            "hanging_wood": f"{NS}:hanging_wood",
            "shattered_field": f"{NS}:shattered_field",
        },
    },
})

write(f"{NS}/worldgen/noise/terrain_seed.json", {"firstOctave": -7, "amplitudes": [1.0]})
# patches of bare Starfall Stone breaking up the Reach's grass (about 40 blocks across)
write(f"{NS}/worldgen/noise/rock_patches.json", {"firstOctave": -5, "amplitudes": [1.0, 0.6, 0.3]})
# the Shattered Field's glowing seams on undersides (about 12 blocks across)
write(f"{NS}/worldgen/noise/glow_patches.json", {"firstOctave": -3, "amplitudes": [1.0, 0.5]})


def y_above(y):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False}


def block_rule(s):
    return {"type": "minecraft:block", "result_state": s}


floor_top = {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor", "add_surface_depth": False,
             "secondary_depth_range": 0}

def biome_is(*names):
    return {"type": "minecraft:biome", "biome_is": [f"{NS}:{n}" for n in names]}


def patches(threshold):
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:rock_patches", "min_threshold": threshold, "max_threshold": 1e9}


BASALT = block_rule(state(f"{NS}:umbral_basalt", axis="y"))
ceiling_under = {"type": "minecraft:stone_depth", "offset": 1, "surface_type": "ceiling", "add_surface_depth": False,
                 "secondary_depth_range": 0}


def glow_band(lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:glow_patches", "min_threshold": lo, "max_threshold": hi}


def glow(threshold):
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:glow_patches", "min_threshold": threshold, "max_threshold": 1e9}


# the Deep's ground per zone (Aetheria 1.2): violet moss on the Lichen Gardens' floors, pale rootstone through the
# Hanging Wood, ember-veined basalt through the Shattered Field, ashen patches among the Spans' umbral basalt
DEEP_SURFACE = {"type": "minecraft:sequence", "sequence": [
    {"type": "minecraft:condition", "if_true": biome_is("lichen_gardens"), "then_run": {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": floor_top, "then_run": block_rule(state(f"{NS}:lichen_moss"))},
        BASALT]}},
    {"type": "minecraft:condition", "if_true": biome_is("hanging_wood"), "then_run": block_rule(state(f"{NS}:rootstone"))},
    # the Shattered Field: dark veined rock throughout, the glow only in thin seams and in clusters on the undersides
    {"type": "minecraft:condition", "if_true": biome_is("shattered_field"), "then_run": {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": ceiling_under, "then_run": {"type": "minecraft:condition", "if_true": glow(0.45),
                                                                            "then_run": block_rule(state(f"{NS}:rift_ember"))}},
        {"type": "minecraft:condition", "if_true": glow_band(-0.035, 0.035), "then_run": block_rule(state(f"{NS}:rift_ember"))},
        block_rule(state(f"{NS}:ember_basalt"))]}},
    # the Spans: the lighter stone over most of it, the umbral basalt showing through
    {"type": "minecraft:condition", "if_true": patches(-0.45), "then_run": block_rule(state(f"{NS}:ashen_basalt"))},
    BASALT,
]}

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
    DEEP_SURFACE,
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
        # the Deep's zone (Aetheria 1.2): AetheriaBiomeSource reads it to pick the zone's biome
        "vegetation": {"type": f"{NS}:deep_zone", "seed_noise": seed},
        "continents": 0, "erosion": 0, "depth": 0, "ridges": 0,
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
                {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": f"{NS}:deep_stone"}]},
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
            {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": f"{NS}:deep_stone"},
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
configured("ore_eclipsium", "minecraft:ore", {
    "size": 5, "discard_chance_on_air_exposure": 0.3,
    "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:deep_stone"}, "state": state(f"{NS}:eclipsium_ore")}],
})
placed("ore_eclipsium", f"{NS}:ore_eclipsium", [count(16), IN_SQUARE, height_range(1, 143), BIOME])

# the Deep
configured("crystal_chandelier", f"{NS}:crystal_chandelier")
placed("crystal_chandelier", f"{NS}:crystal_chandelier",
       [count(7), IN_SQUARE, height_range(40, 140), scan_up_to_ceiling(24), ONE_DOWN, BIOME])


def lichen(name, block):
    configured(name, "minecraft:multiface_growth", {
        "block": block, "search_range": 20, "can_place_on_floor": True, "can_place_on_ceiling": True,
        "can_place_on_wall": True, "chance_of_spreading": 0.6, "can_be_placed_on": f"#{NS}:deep_stone",
    })


lichen("neon_lichen_magenta", f"{NS}:magenta_neon_lichen")
lichen("neon_lichen_teal", f"{NS}:teal_neon_lichen")
placed("neon_lichen_magenta", f"{NS}:neon_lichen_magenta", [count(uniform(40, 70)), IN_SQUARE, height_range(1, 143), BIOME])
placed("neon_lichen_teal", f"{NS}:neon_lichen_teal", [count(uniform(40, 70)), IN_SQUARE, height_range(1, 143), BIOME])

# the layer 3 zones (Aetheria 1.2)
# the Lichen Gardens: giant umbral caps as trees, lichen carpets, dense cap patches
configured("giant_umbral_cap", f"{NS}:giant_umbral_cap")
placed("giant_umbral_cap", f"{NS}:giant_umbral_cap", [count(3), IN_SQUARE, height_range(60, 140), scan_down_to_basalt(32), BIOME])
configured("lichen_carpet", f"{NS}:lichen_carpet")
placed("lichen_carpet", f"{NS}:lichen_carpet", [count(10), IN_SQUARE, height_range(60, 140), scan_down_to_basalt(32), BIOME])
# lichen mats on the Spans' decks and platforms
placed("lichen_carpet_spans", f"{NS}:lichen_carpet", [count(3), IN_SQUARE, height_range(70, 142), scan_down_to_basalt(32), BIOME])
placed("umbral_cap_patch_dense", f"{NS}:umbral_cap_patch", [count(7), IN_SQUARE, height_range(1, 143), scan_down_to_basalt(24), BIOME])
# the Hanging Wood: chandeliers dense, teal lichen up the roots
placed("crystal_chandelier_dense", f"{NS}:crystal_chandelier",
       [count(16), IN_SQUARE, height_range(30, 140), scan_up_to_ceiling(24), ONE_DOWN, BIOME])
# curtains hang in ragged sheets of strands of every length (TealCurtainSheetFeature)
configured("teal_curtain", f"{NS}:teal_curtain_sheet")
placed("teal_curtain", f"{NS}:teal_curtain", [count(14), IN_SQUARE, height_range(40, 140), scan_up_to_ceiling(30), ONE_DOWN, BIOME])
placed("neon_lichen_teal_dense", f"{NS}:neon_lichen_teal", [count(uniform(70, 100)), IN_SQUARE, height_range(30, 143), BIOME])
placed("neon_lichen_magenta_sparse", f"{NS}:neon_lichen_magenta", [count(uniform(15, 25)), IN_SQUARE, height_range(1, 143), BIOME])
# the Shattered Field: eclipsium on the chunks' undersides, rift glass shards, magenta lichen
configured("underside_ore", f"{NS}:underside_ore")
placed("underside_ore", f"{NS}:underside_ore", [count(14), IN_SQUARE, height_range(40, 135), scan_up_to_ceiling(20), ONE_DOWN, BIOME])
configured("rift_glass_shards", f"{NS}:rift_glass_shards")
placed("rift_glass_shards", f"{NS}:rift_glass_shards", [count(3), IN_SQUARE, height_range(50, 140), scan_down_to_basalt(30), BIOME])
placed("neon_lichen_magenta_dense", f"{NS}:neon_lichen_magenta", [count(uniform(70, 100)), IN_SQUARE, height_range(1, 143), BIOME])
placed("neon_lichen_teal_sparse", f"{NS}:neon_lichen_teal", [count(uniform(15, 25)), IN_SQUARE, height_range(1, 143), BIOME])

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
    vegetal_decoration=["lichen_carpet_spans", "neon_lichen_magenta", "neon_lichen_teal", "neon_lichen_magenta_sparse",
                        "neon_lichen_teal_sparse", "umbral_cap_patch"],
))

# the layer 3 zones (Aetheria 1.2): the Rift Abyss above is the Spans. The fog colours tint the Deep's fog (the sky
# code reads them against the Rift Abyss's: client/sky/ZoneFog), so the Rift Abyss's stays the reference.
DEEP_SKY = 0x2A1B4A
biome("lichen_gardens", {
    "sky_color": DEEP_SKY, "fog_color": 0x5A2E94, "water_color": 0x5A2A7A, "water_fog_color": 0x1E0F33,
    "grass_color": 0x6B4590, "foliage_color": 0x5A3A80,
    "particle": {"options": {"type": f"{NS}:abyss_mote"}, "probability": 0.016},
    "mood_sound": MOOD,
    **audio("deep"),
}, features(
    underground_ores=["ore_eclipsium"],
    underground_decoration=["crystal_chandelier"],
    vegetal_decoration=["giant_umbral_cap", "lichen_carpet", "neon_lichen_magenta", "neon_lichen_teal", "umbral_cap_patch_dense"],
))

biome("hanging_wood", {
    "sky_color": DEEP_SKY, "fog_color": 0x0E4A52, "water_color": 0x2A6A7A, "water_fog_color": 0x0E1F2A,
    "grass_color": 0x2F6A70, "foliage_color": 0x285E66,
    "particle": {"options": {"type": "minecraft:warped_spore"}, "probability": 0.03},
    "mood_sound": MOOD,
    **audio("deep"),
}, features(
    underground_ores=["ore_eclipsium"],
    underground_decoration=["crystal_chandelier_dense"],
    vegetal_decoration=["teal_curtain", "neon_lichen_teal_dense", "neon_lichen_magenta_sparse", "umbral_cap_patch"],
))

biome("shattered_field", {
    "sky_color": DEEP_SKY, "fog_color": 0x6A1450, "water_color": 0x7A2A6A, "water_fog_color": 0x2A0F26,
    "grass_color": 0x7A3D6A, "foliage_color": 0x6E2F60,
    "particle": {"options": {"type": "minecraft:dust_color_transition", "from_color": [1.0, 0.55, 0.25],
                             "to_color": [0.95, 0.2, 0.6], "scale": 1.0}, "probability": 0.02},
    "mood_sound": MOOD,
    **audio("deep"),
}, features(
    underground_ores=["ore_eclipsium", "underside_ore"],
    underground_decoration=["crystal_chandelier"],
    vegetal_decoration=["rift_glass_shards", "neon_lichen_magenta_dense", "neon_lichen_teal_sparse", "umbral_cap_patch"],
))

# where the Deep's structures may stand: the crypt in every zone; the boss arena wherever void lies beside a platform
# (not the Lichen Gardens' merged landmasses); the centre in every zone (the Spans are forced around the Breach)
DEEP_BIOMES = [f"{NS}:rift_abyss", f"{NS}:lichen_gardens", f"{NS}:hanging_wood", f"{NS}:shattered_field"]
write(f"{NS}/tags/worldgen/biome/has_structure/hollow_crypt.json", {"values": DEEP_BIOMES})


def patch(path, edit):
    full = os.path.join(ROOT, path)
    with open(full, encoding="utf-8") as f:
        data = json.load(f)
    edit(data)
    write(path, data)


patch(f"{NS}/worldgen/structure/silent_nave.json",
      lambda d: d.__setitem__("biomes", [f"{NS}:rift_abyss", f"{NS}:hanging_wood", f"{NS}:shattered_field"]))
patch(f"{NS}/worldgen/structure/breach_sanctum.json", lambda d: d.__setitem__("biomes", DEEP_BIOMES))
# fewer starts find a site now that only part of the layer suits each structure: spacing set so the count per area
# stays what it was (rates from SiteRateProbe, plan: Aetheria 1.2 Plan v1 Lane B)
patch(f"{NS}/worldgen/structure_set/silent_nave.json",
      lambda d: d["placement"].update({"spacing": 30, "separation": 11}))
patch(f"{NS}/worldgen/structure_set/hollow_crypt.json",
      lambda d: d["placement"].update({"spacing": 38, "separation": 13}))

# the Hollow Stalker in each zone: the Spans and the Hanging Wood as before, half in the Gardens (their lit carpets
# keep it off much of the ground anyway), fewer in the Shattered Field
for name, biomes, weight in (("hollow_stalker_spawns", [f"{NS}:rift_abyss", f"{NS}:hanging_wood"], 20),
                             ("hollow_stalker_spawns_gardens", f"{NS}:lichen_gardens", 10),
                             ("hollow_stalker_spawns_shattered", f"{NS}:shattered_field", 12)):
    write(f"{NS}/neoforge/biome_modifier/{name}.json", {
        "type": "neoforge:add_spawns", "biomes": biomes,
        "spawners": {"type": f"{NS}:hollow_stalker", "weight": weight, "minCount": 1, "maxCount": 1},
    })

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
    "no_knockback": ["shear", "scorch", "meteor", "prism_beam", "prism_core", "chord", "drift_sting"],
}
for tag, types in DAMAGE_TAGS.items():
    write(f"minecraft/tags/damage_type/{tag}.json", {"replace": False, "values": [f"{NS}:{t}" for t in types]})

print("worldgen data written")
