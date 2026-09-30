"""Last Light, the Heliarch's solar glaive (GDD 7.3): textures/item/last_light.png (32x32, the whole glaive: the icon and
the lit layer), last_light_light0..3.png (the blade and its sun disc alone, the layer the game draws at full brightness,
so the blade shines as light whatever the light around it; it fills with light as Resonance rises, stage 3 nearly white
with glints thrown off the edge) and last_light_sun1..3.png (one, two or three of the sun gems on the haft lit, the
Sunlight charges held; also full bright), plus every item model (models/item/last_light*.json: the base with its held
displays, one per glow stage and gem count through NeoForge's item_layers loader, and the overrides that pick them from
the cosmicbreach:resonance and cosmicbreach:sunlight properties).

Same diagonal layout as Meridian (gen_meridian.py): butt bottom left, blade top right, light from the top left.

    u = x - y        along the haft (+ towards the blade)
    v = x + y - 31   across it (0 is the haft's centre line, negative the upper-left side: the cutting edge)

The grip is where Meridian's is (texels (8.2, 22.8) and (5.6, 25.4)); the held display is 1.5x (a glaive nearly two
blocks long) and moved so that grip lands where Meridian's does, so the engine's shared animations hold it by the grip.

    butt     a small gold sun-cap
    haft     white-gold metal, three pixels wide
    grip     ivory cord bound with gold, u -21 to -11, gold bands at both ends
    gems     three sun gems on the haft (u -5, -1, 3): dim amber, lit white-gold by Sunlight
    collar   a sun disc where the blade is seated, its rim gold, its face bright
    blade    a curved glaive blade from u 11 to the point at u 30: the cutting edge bellies out on the upper left, a small
             spur on the back; white-gold with a seam of light inside the edge that brightens with Resonance

Run:  python tools/art/gen_last_light.py
"""
from __future__ import annotations

import json

import numpy as np

from common import ASSETS, PREVIEWS, TEX, blank, outline_mask, over, rel, rgb, save_png, supersample, upscale, white_alpha

S = 32

METAL = {
    "white": rgb("#ffffff"),
    "hi": rgb("#fff6da"),
    "light": rgb("#ffe7a6"),
    "mid": rgb("#f2c96e"),
    "shade": rgb("#d19b45"),
    "deep": rgb("#a8702c"),
    "outline": rgb("#4f3213"),
}
CORD = {"light": rgb("#f3ead6"), "mid": rgb("#d6c7a6"), "shade": rgb("#a8977a"), "thread": rgb("#f0c35a"),
        "outline": rgb("#3d3020")}
GEM_DIM = rgb("#8a5620")
GEM_DIM_EDGE = rgb("#6a3c14")
GEM_LIT = rgb("#ffffff")
GEM_HALO = rgb("#fff0b8")
# the seam of light inside the edge, by glow stage
SEAM = [rgb("#ffe39a"), rgb("#fff4d0"), rgb("#ffffff"), rgb("#ffffff")]
GLINT = rgb("#fffbe8")

U_BUTT0, U_BUTT1 = -29, -25
U_HAFT0, U_HAFT1 = -24, 9
U_GRIP0, U_GRIP1 = -21, -11
BANDS = [(-23, -22), (-10, -9)]
GEMS = [-7, -3, 1]
DISC_CENTRE = (18.0, 13.0)   # pixel centre of the sun disc (u 5)
DISC_R = 2.7
U_BLADE0, U_TIP = 7, 30
SCALE = 1.5                  # the held display, against Meridian's rig at 0.85


def uv_grid():
    ys, xs = np.mgrid[0:S, 0:S]
    return xs - ys, xs + ys - (S - 1), xs, ys


def edge_profile(u):
    """How far the cutting edge reaches across (-v) at u along the blade: a long belly curving into the point."""
    t = np.clip((np.asarray(u, dtype=float) - U_BLADE0) / (U_TIP - U_BLADE0), 0.0, 1.0)
    return 2.4 * (1 - t) ** 2 + 2 * 11.5 * t * (1 - t) + 0.2 * t ** 2


def back_profile(u):
    """How far the back (the spine) reaches across (+v): nearly straight, with a small spur near the base."""
    u = np.asarray(u, dtype=float)
    t = np.clip((u - U_BLADE0) / (U_TIP - U_BLADE0), 0.0, 1.0)
    back = 2.4 * (1 - t) ** 1.3 + 0.2
    spur = np.where((u >= 8) & (u <= 12), 4.6 - np.abs(u - 9.0) * 1.2, 0.0)
    return np.maximum(back, spur)


def blade_mask():
    u, v, _, _ = uv_grid()
    return (u >= U_BLADE0) & (u <= U_TIP) & (v >= -edge_profile(u)) & (v <= back_profile(u))


def disc_mask():
    _, _, xs, ys = uv_grid()
    return (xs - DISC_CENTRE[0]) ** 2 + (ys - DISC_CENTRE[1]) ** 2 <= DISC_R ** 2 + 0.3


def gem_pixels():
    """Pixel (x, y) of each sun gem, bottom to top."""
    return [((u + 31) // 2, (31 - u) // 2) for u in GEMS]


def build(stage: int) -> np.ndarray:
    img = blank(S, S)
    u, v, xs, ys = uv_grid()
    part = np.zeros((S, S), dtype=int)  # 1 metal, 2 cord, 3 blade, 4 disc
    col = np.zeros((S, S, 3), dtype=int)

    # ---- haft and butt: white-gold metal, lit on the upper-left line
    haft = (np.abs(v) <= 1) & (u >= U_HAFT0) & (u <= U_HAFT1)
    col[haft & (v == -1)] = METAL["hi"]
    col[haft & (v == 0)] = METAL["mid"]
    col[haft & (v == 1)] = METAL["shade"]
    part[haft] = 1
    butt = (u >= U_BUTT0) & (u <= U_BUTT1) & (np.abs(v) <= np.where(u <= -28, 0, 2)) & ~((u == U_BUTT1) & (np.abs(v) == 2))
    col[butt] = METAL["mid"]
    col[butt & (v < 0)] = METAL["light"]
    col[butt & (v > 0)] = METAL["deep"]
    part[butt] = 1
    for b0, b1 in BANDS:
        band = (u >= b0) & (u <= b1) & (np.abs(v) <= 2)
        col[band] = METAL["mid"]
        col[band & (v < 0)] = METAL["light"]
        col[band & (v == -2)] = METAL["hi"]
        col[band & (v == 2)] = METAL["deep"]
        part[band] = 1

    # ---- grip: ivory cord with a gold thread
    grip = (np.abs(v) <= 1) & (u >= U_GRIP0) & (u <= U_GRIP1)
    phase = (u + v) % 4
    gc = np.where(phase[..., None] < 2, CORD["light"], CORD["mid"])
    gc = np.where(((v == 1) & (phase >= 2))[..., None], CORD["shade"], gc)
    gc = np.where(((u % 5) == 0)[..., None], CORD["thread"], gc)
    col[grip] = gc[grip]
    part[grip] = 2

    # ---- the blade: white-gold, its body brightening with the stage, the seam of light inside the edge
    blade = blade_mask()
    across = (v + edge_profile(u)) / np.maximum(0.8, edge_profile(u) + back_profile(u))  # 0 at the edge, 1 at the back
    tones = [METAL["white"], METAL["hi"], METAL["light"], METAL["mid"], METAL["shade"]]
    lift = [0.0, 0.1, 0.22, 0.4][stage]
    idx = np.clip(np.floor((across - lift) * 5.0), 0, 4).astype(int)
    for i, t in enumerate(tones):
        col[blade & (idx == i)] = t
    edge = blade & (v <= -edge_profile(u) + 0.9)
    col[edge] = METAL["white"]
    seam_w = [0.7, 0.8, 1.2, 1.6][stage]
    seam = blade & (v > -edge_profile(u) + 0.9) & (v <= -edge_profile(u) + 0.9 + 2 * seam_w) & (u < U_TIP - 1)
    col[seam] = SEAM[stage]
    back = blade & (v >= back_profile(u) - 0.9)
    col[back] = METAL["deep"] if stage < 2 else METAL["shade"]
    part[blade] = 3

    # ---- the sun disc the blade is seated in
    disc = disc_mask()
    d2 = (xs - DISC_CENTRE[0]) ** 2 + (ys - DISC_CENTRE[1]) ** 2
    col[disc] = METAL["light"]
    col[disc & (d2 <= 2.3)] = METAL["white"] if stage >= 1 else METAL["hi"]
    col[disc & (d2 > 5.5)] = METAL["mid"]
    col[disc & (d2 > 5.5) & (xs + ys > DISC_CENTRE[0] + DISC_CENTRE[1] + 1)] = METAL["shade"]
    part[disc] = 4

    # ---- sun gems on the haft: dim at rest (the second layer lights them)
    for (gx, gy) in gem_pixels():
        col[gy, gx] = GEM_DIM
        for dx, dy in ((1, 0), (0, 1)):
            if part[gy + dy, gx + dx] == 1:
                col[gy + dy, gx + dx] = GEM_DIM_EDGE

    filled = part > 0
    img[filled, :3] = col[filled]
    img[filled, 3] = 255

    # ---- outline by the part it wraps
    ol = outline_mask(filled)
    part_of = np.zeros((S, S), dtype=int)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        part_of = np.maximum(part_of, np.roll(np.roll(part, -dy, axis=0), -dx, axis=1))
    img[ol & (part_of == 2), :3] = CORD["outline"]
    img[ol & (part_of != 2), :3] = METAL["outline"]
    img[ol, 3] = 255

    # ---- stage 3: glints thrown off the edge and the point
    if stage == 3:
        for (x, y) in ((24, 1), (18, 3), (29, 7), (14, 6), (26, 11)):
            if img[y, x, 3] == 0:
                img[y, x, :3] = GLINT
                img[y, x, 3] = 255
    return img


def light_layer(stage: int) -> np.ndarray:
    """The blade and the sun disc of stage {stage} alone (and stage 3's glints): the full-bright layer."""
    full = build(stage)
    base = build(0)
    img = blank(S, S)
    mask = (blade_mask() | disc_mask()) & (full[..., 3] > 0)
    mask |= (full[..., 3] > 0) & (base[..., 3] == 0)  # glints thrown off past the outline
    img[mask] = full[mask]
    return img


def build_sun(count: int) -> np.ndarray:
    """The second layer: {count} of the three gems lit (from the one nearest the blade), each with its halo."""
    img = blank(S, S)
    base = build(0)
    for (gx, gy) in list(reversed(gem_pixels()))[:count]:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            x, y = gx + dx, gy + dy
            if base[y, x, 3] and tuple(base[y, x, :3]) != METAL["outline"]:
                img[y, x, :3] = GEM_HALO
                img[y, x, 3] = 255
        img[gy, gx, :3] = GEM_LIT
        img[gy, gx, 3] = 255
    return img


def key_points():
    """Texel coordinates (x right, y down) the rig and the weapon data use."""
    def t(u, v):
        return ((u + v + 31) / 2.0, (v - u + 31) / 2.0)
    return {"butt": t(-28, 0), "guard": t(U_BLADE0, 0), "tip": t(U_TIP, 0), "half_width": 6.6 * 0.7071 * 0.85}


def display_translation(scale: float, grip=(8.2, 22.8)):
    """The third-person display translation (pixels) that puts the grip texel where Meridian's rig puts its own:
    the handheld display at 0.85x, translation (0, 4, 0.5)."""
    import math
    x = grip[0] / 32.0 - 0.5
    y = (1.0 - grip[1] / 32.0) - 0.5
    c, s = math.cos(math.radians(55)), math.sin(math.radians(55))
    rx, ry = x * c - y * s, x * s + y * c  # rotZ(55)
    ref_y, ref_z = 4 / 16.0 + 0.85 * ry, 0.5 / 16.0 + 0.85 * rx  # rotY(-90) turns (rx, ry, 0) into (0, ry, rx)
    return (0.0, round((ref_y - scale * ry) * 16.0, 3), round((ref_z - scale * rx) * 16.0, 3))


GLOW = [0.0, 0.34, 0.67, 1.0]
SUN = [0.0, 0.33, 0.66, 0.99]


FULL_BRIGHT = {"block_light": 15, "sky_light": 15}


def write_models():
    models = ASSETS / "models" / "item"
    for stale in list(models.glob("last_light_*.json")):
        stale.unlink()  # every variant is rewritten below
    t = display_translation(SCALE)
    base = {
        "parent": "minecraft:item/handheld",
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": list(t), "scale": [SCALE] * 3},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": list(t), "scale": [SCALE] * 3},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.0, 0.7], "scale": [0.82] * 3},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.0, 0.7], "scale": [0.82] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.7] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.1] * 3},
        },
    }
    paths = [_write(models / "last_light_base.json", base)]

    def doc(g, s_):
        textures = {"layer0": "cosmicbreach:item/last_light", "layer1": f"cosmicbreach:item/last_light_light{g}"}
        layers = {"1": FULL_BRIGHT}
        if s_:
            textures["layer2"] = f"cosmicbreach:item/last_light_sun{s_}"
            layers["2"] = FULL_BRIGHT
        return {"parent": "cosmicbreach:item/last_light_base", "loader": "neoforge:item_layers", "textures": textures,
                "neoforge_data": {"layers": layers}}

    overrides = []
    for g in range(4):
        for s_ in range(4):
            if g == 0 and s_ == 0:
                continue
            name = f"last_light_g{g}_s{s_}"
            paths.append(_write(models / (name + ".json"), doc(g, s_)))
            overrides.append({"predicate": {"cosmicbreach:resonance": GLOW[g], "cosmicbreach:sunlight": SUN[s_]},
                              "model": "cosmicbreach:item/" + name})
    top = dict(doc(0, 0), overrides=overrides)
    paths.append(_write(models / "last_light.json", top))
    return paths


def _write(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(doc, indent=2) + "\n")
    return path


def debug_strip(images, path):
    k = 8
    pad = 10
    cell = S * k + pad
    h = S * k + pad * 2 + S * 2 + pad
    w = pad + cell * len(images)
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = 110
    out[..., 3] = 255
    for i, im in enumerate(images):
        x0 = pad + i * cell
        over(out, upscale(im, k), x0, pad)
        over(out, im, x0, pad * 2 + S * k)
        over(out, upscale(im, 2), x0 + S + pad, pad * 2 + S * k)
    save_png(out, path)


def rays_texture(size: int = 128, count: int = 12) -> np.ndarray:
    """textures/fx/lastlight_rays.png: a sunburst, white with its shape in alpha: a soft core and {count} tapering rays,
    every other one shorter. The flare of a parry, the Daybreak's burst and the Crown's little sun use it."""
    def f(x, y):
        dx, dy = x / size - 0.5, y / size - 0.5
        r = np.sqrt(dx * dx + dy * dy) * 2.0
        a = np.arctan2(dy, dx)
        k = (np.cos(a * count) + 1.0) / 2.0
        long = (np.cos(a * count / 2.0) + 1.0) / 2.0
        reach = 0.55 + 0.45 * long
        ray = np.clip(1.0 - r / reach, 0.0, 1.0) * k ** 12
        core = np.clip(1.0 - r / 0.32, 0.0, 1.0) ** 1.6
        return np.clip(np.maximum(ray, core), 0.0, 1.0)
    return white_alpha(supersample(f, size, size, 4))


def main():
    out = TEX / "item"
    for stale in list(out.glob("last_light_glow*.png")):
        stale.unlink()  # the glow stages are the light layers now
    stages = [build(st) for st in range(4)]
    suns = [build_sun(n) for n in (1, 2, 3)]
    paths = [save_png(stages[0], out / "last_light.png")]
    for st in range(4):
        paths.append(save_png(light_layer(st), out / f"last_light_light{st}.png"))
    for n, im in zip((1, 2, 3), suns):
        paths.append(save_png(im, out / f"last_light_sun{n}.png"))
    lit = stages[3].copy()
    over(lit, suns[2], 0, 0)
    debug_strip(stages + [lit], PREVIEWS / "last_light_stages.png")
    paths += write_models()
    paths.append(save_png(rays_texture(), TEX / "fx" / "lastlight_rays.png"))
    for p in paths:
        print("wrote", rel(p))
    print("key points:", key_points(), "display translation:", display_translation(SCALE))


if __name__ == "__main__":
    main()
