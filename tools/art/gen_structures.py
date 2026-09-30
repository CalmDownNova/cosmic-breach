"""The first dungeons' art (W5): the Lens Array's pieces, the vaults, the tripwire emitter, the light blocks, the
loose-piece icons, and every block model they need (models/block/, written here; the data run adds the block
states and item models that point at them).

    textures/block/
      lens_pedestal_top/_side        polished Starfall Stone, a gold ring and star on top, a gold band round
      lens_mirror                    the plate: bright silver, a cool gradient, a diagonal glint
      lens_mirror_frame(_loose)      its rim: pale stone for a fixed mirror, gold for a loose one you can lift
      lens_splitter                  pale crystal facets, lavender and cyan, half clear
      lens_filter_gold/teal/magenta  coloured glass lenses, half clear, a brighter rim
      lens_umbral                    black basalt with violet veins (the puzzle's light-drinker)
      lens_focus                     a gold crystal lens
      lens_receptor_<colour>(_lit)   crystal in its colour: dim and cloudy, or lit and white-hot at the core
      warden_eye(_awake), warden_eye_side
                                     a violet crystal eye in Umbral stone; awake, its iris burns red-magenta
      lens_socket_top                a plinth with a groove
      sun_aperture(_open)            a gold ring round a crystal oculus; open, white-gold light
      lens_core_top                  a floor tile with a gold sun sigil
      reliquary_vault_front(_ready)/_side/_top   white stone and gold; ready, its seam glows
      observatory_vault_front(_ready)/_side/_top Driftstone and Nebulite cyan
      sunstone                       pale gold glowing tiles
      nebulite_lamp                  a cyan light in a Driftstone frame
      kinetic_emitter                Driftstone with a dark housing and a cyan slit
    textures/item/
      loose_mirror, loose_gold_filter, loose_teal_filter, loose_magenta_filter
    textures/fx/lens_beam.png        the beams' texture: neutral white streaks (the vanilla beacon's is cyan, which
                                     turned every colour green)

Same style as the W1 blocks (tools/art/gen_blocks_*.py): 16x16, light from the top left, daylight palette; only
the Umbral pieces and the Eye are dark.

Run:  python tools/art/gen_structures.py   (after gen_blocks_*: it reads their textures)
"""
from __future__ import annotations

import json
import math

import numpy as np

from blockart import by_share, fbm, from_ramp, ramp, rng_for, voronoi
from common import ASSETS, TEX, load_png, rel, rgb, save_png

S = 16
BLOCK = TEX / "block"
ITEM = TEX / "item"
MODELS = ASSETS / "models" / "block"

GOLD = ramp("#9b6a24", "#c98f35", "#e8b94f", "#f7dc86", "#fff4c8")
SILVER = ramp("#6f7c8c", "#93a2b3", "#b8c6d4", "#dbe6ee", "#f4f9fc", "#ffffff")
CRYSTAL = ramp("#8f86c9", "#aba6e0", "#c9c6f5", "#e2e0ff", "#f5f4ff")
UMBRAL = ramp("#0d0b14", "#17131f", "#211a2c", "#2c2238", "#3a2d48")
VIOLET = ramp("#4a2a6e", "#6d3fa0", "#9a62d4", "#c79cf2")
CYAN = ramp("#14606b", "#1f8c98", "#38b8c3", "#7fe3ea", "#d4fbff")
COLOURS = {
    "white": ramp("#b9a26a", "#dcc68e", "#f2e3b6", "#fff6dc", "#ffffff"),
    "gold": ramp("#8a5a12", "#c28a26", "#eab23f", "#fbd978", "#fff3c4"),
    "teal": ramp("#0f5f5a", "#1c8a83", "#2fb8ab", "#6ee3d4", "#cffcf5"),
    "magenta": ramp("#6d1459", "#9c2482", "#cf3dad", "#f47dd6", "#ffd0f3"),
}

written: list = []


def out(img, path):
    save_png(img, path)
    written.append(path)


def tex(name):
    return load_png(BLOCK / f"{name}.png").copy()


def px(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


def ring(img, cx, cy, r0, r1, colours, light=True):
    """A ring between radii r0 and r1, shaded from the top left."""
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                shade = 0.5 - 0.5 * ((x - cx) + (y - cy)) / (r1 * 1.414) if light else 0.5
                k = int(round(np.clip(shade, 0, 1) * (len(colours) - 1)))
                px(img, x, y, colours[k])


def star(img, cx, cy, c):
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        px(img, cx + dx, cy + dy, c)


# ---------------------------------------------------------------- the grid

def pedestal():
    top = tex("polished_starfall_stone")
    ring(top, 8, 8, 5.0, 6.2, GOLD)
    star(top, 7, 7, GOLD[4])
    px(top, 8, 8, GOLD[2])
    out(top, BLOCK / "lens_pedestal_top.png")
    side = tex("polished_starfall_stone")
    for x in range(S):
        px(side, x, 2, GOLD[3] if x % 4 else GOLD[4])
        px(side, x, 3, GOLD[1])
    out(side, BLOCK / "lens_pedestal_side.png")
    top = tex("polished_starfall_stone")
    for i in range(4, 12):
        px(top, i, 7, rgb("#b7b0a2"))
        px(top, i, 8, rgb("#e8e3d8"))
    out(top, BLOCK / "lens_socket_top.png")


def mirror():
    img = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            t = (x + y) / 30.0
            k = int(round((1.0 - t) * (len(SILVER) - 2))) + (1 if abs((x - y) - 3) <= 1 else 0)
            img[y, x] = (*SILVER[min(len(SILVER) - 1, k)], 255)
    for i in range(3, 8):
        px(img, i, 10 - i, SILVER[5])
    px(img, 12, 3, (255, 255, 255))
    out(img, BLOCK / "lens_mirror.png")
    for name, pal in (("lens_mirror_frame", ramp("#9a958b", "#bdb8ad", "#d9d5cb", "#eeebe4")), ("lens_mirror_frame_loose", GOLD)):
        f = np.zeros((S, S, 4), dtype=np.uint8)
        rng = rng_for(name)
        idx = by_share(fbm(rng), (2, 4, 6, 4)[:len(pal)] if len(pal) == 4 else (2, 3, 5, 4, 2))
        f[:] = from_ramp(idx, pal)
        out(f, BLOCK / f"{name}.png")


def crystal(name, pal, alpha, rng_name=None):
    rng = rng_for(rng_name or name)
    ids, _, _, _ = voronoi(rng, 7, 4.0)
    shades = rng.integers(1, len(pal), size=64)
    img = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            k = int(shades[ids[y, x] % 64])
            edge = any(0 <= x + dx < S and 0 <= y + dy < S and ids[y + dy, x + dx] != ids[y, x] for dx, dy in ((1, 0), (0, 1)))
            if edge:
                k = len(pal) - 1
            img[y, x] = (*pal[k], alpha)
    return img


def splitter():
    img = crystal("lens_splitter", CRYSTAL + [rgb("#bff4f2")], 215)
    out(img, BLOCK / "lens_splitter.png")


def filters():
    for colour in ("gold", "teal", "magenta"):
        pal = COLOURS[colour]
        img = np.zeros((S, S, 4), dtype=np.uint8)
        for y in range(S):
            for x in range(S):
                rim = x in (0, 15) or y in (0, 15)
                d = math.hypot(x - 5.5, y - 5.5) / 14.0
                k = 4 if rim else int(round(np.clip(3.2 - 3.0 * d, 1, 3)))
                img[y, x] = (*pal[k], 255 if rim else 165)
        px(img, 4, 3, (255, 255, 255))
        px(img, 3, 4, (255, 255, 255))
        out(img, BLOCK / f"lens_filter_{colour}.png")


def umbral():
    rng = rng_for("lens_umbral")
    img = from_ramp(by_share(fbm(rng), (3, 4, 5, 3, 1)), UMBRAL)
    vein = fbm(rng, octaves=((3, 0.6), (6, 0.4)))
    for y in range(S):
        for x in range(S):
            if abs(vein[y, x] - 0.5) < 0.035:
                img[y, x] = (*VIOLET[1 + (x + y) % 2], 255)
    out(img, BLOCK / "lens_umbral.png")


def focus():
    img = crystal("lens_focus", GOLD, 255)
    for y in range(5, 11):
        for x in range(5, 11):
            if math.hypot(x - 7.5, y - 7.5) < 2.6:
                img[y, x] = (*GOLD[4], 255)
    px(img, 7, 7, (255, 255, 255))
    out(img, BLOCK / "lens_focus.png")


def receptors():
    for colour, pal in COLOURS.items():
        dim = [tuple(int(c * 0.72 + 40) for c in col) for col in pal[:4]]
        out(crystal(f"lens_receptor_{colour}", dim, 255, "lens_receptor"), BLOCK / f"lens_receptor_{colour}.png")
        lit = crystal(f"lens_receptor_{colour}_lit", pal[1:] + [(255, 255, 255)], 255, "lens_receptor")
        for y in range(6, 11):
            for x in range(6, 11):
                if math.hypot(x - 8, y - 8) < 2.2:
                    lit[y, x] = (255, 255, 255, 255)
        out(lit, BLOCK / f"lens_receptor_{colour}_lit.png")


def warden_eye():
    side = tex("umbral_basalt_bricks")
    out(side, BLOCK / "warden_eye_side.png")
    for awake in (False, True):
        img = tex("polished_umbral_basalt")
        iris = ramp("#8a1f5c", "#c2307a", "#f0579c", "#ffb0d0") if awake else VIOLET
        for y in range(S):
            for x in range(S):
                e = ((x - 7.5) / 6.2) ** 2 + ((y - 7.5) / (3.6 if not awake else 4.4)) ** 2
                if e <= 1.0:
                    img[y, x] = (*(rgb("#e9e2f5") if not awake else rgb("#ffe3ee")), 255)
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 3.0 and e <= 1.0:
                    k = int(np.clip((3.0 - d) / 3.0 * (len(iris) - 1), 0, len(iris) - 1))
                    img[y, x] = (*iris[k], 255)
                if d <= (1.0 if awake else 1.4) and e <= 1.0:
                    img[y, x] = (*rgb("#12040c"), 255)
        px(img, 6, 6, (255, 255, 255))
        out(img, BLOCK / ("warden_eye_awake.png" if awake else "warden_eye.png"))


def aperture():
    for open_ in (False, True):
        img = tex("starfall_stone_bricks")
        ring(img, 8, 8, 4.5, 7.4, GOLD)
        core = ramp("#fff1c2", "#fffaf0", "#ffffff") if open_ else ramp("#7fa9b8", "#a9cfd8", "#d4eef2")
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if d < 4.5:
                    k = int(np.clip((4.5 - d) / 4.5 * len(core), 0, len(core) - 1))
                    img[y, x] = (*core[k], 255)
        out(img, BLOCK / ("sun_aperture_open.png" if open_ else "sun_aperture.png"))


def core_tile():
    img = tex("polished_starfall_stone")
    for k in range(8):
        a = k * math.pi / 4
        for r in np.arange(2.5, 7.2, 0.5):
            px(img, int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r)), GOLD[2 + (k % 2)])
    ring(img, 8, 8, 1.2, 2.6, GOLD)
    out(img, BLOCK / "lens_core_top.png")


# ---------------------------------------------------------------- vaults, light, traps

def vaults():
    for name, stone, trim, glow in (("reliquary_vault", "starfall_stone_bricks", GOLD, ramp("#fff1c2", "#ffffff")),
                                    ("observatory_vault", "driftstone_bricks", CYAN, ramp("#9ff4f8", "#ffffff"))):
        side = tex(stone)
        for x in range(S):
            px(side, x, 0, trim[3])
            px(side, x, 15, trim[1])
        out(side, BLOCK / f"{name}_side.png")
        top = tex(stone)
        ring(top, 8, 8, 3.0, 5.0, trim)
        out(top, BLOCK / f"{name}_top.png")
        for ready in (False, True):
            f = tex(stone)
            for y in range(2, 14):
                for x in range(3, 13):
                    edge = x in (3, 12) or y in (2, 13)
                    f[y, x] = (*(trim[3] if edge else trim[1]), 255)
            for y in range(3, 13):
                px(f, 7, y, glow[0] if ready else trim[0])
                px(f, 8, y, glow[1] if ready else trim[0])
            ring(f, 8, 8, 1.6, 3.0, trim if not ready else [glow[0], glow[1], glow[1]])
            out(f, BLOCK / (f"{name}_front_ready.png" if ready else f"{name}_front.png"))


def lights():
    img = np.zeros((S, S, 4), dtype=np.uint8)
    warm = ramp("#d9a64a", "#f0c96a", "#fbe29a", "#fff3cc", "#fffdf2")
    rng = rng_for("sunstone")
    field = fbm(rng)
    for y in range(S):
        for x in range(S):
            mortar = x % 8 == 7 or y % 8 == 7
            cx, cy = (x // 8) * 8 + 3.5, (y // 8) * 8 + 3.5
            d = math.hypot(x - cx, y - cy)
            k = 0 if mortar else int(np.clip(4.2 - d * 0.9 + (field[y, x] - 0.5), 1, 4))
            img[y, x] = (*warm[k], 255)
    out(img, BLOCK / "sunstone.png")
    lamp = tex("driftstone_bricks")
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            k = int(np.clip(4.5 - d * 0.8, 0, 4))
            lamp[y, x] = (*CYAN[k], 255)
    out(lamp, BLOCK / "nebulite_lamp.png")
    em = tex("driftstone_bricks")
    for y in range(5, 11):
        for x in range(2, 14):
            em[y, x] = (*rgb("#2a3440"), 255)
    for x in range(3, 13):
        px(em, x, 7, CYAN[3])
        px(em, x, 8, CYAN[2])
    px(em, 8, 7, CYAN[4])
    out(em, BLOCK / "kinetic_emitter.png")


# ---------------------------------------------------------------- the beam

def beam():
    """The Lens Array's beam texture: neutral white with soft streaks along the beam (v), so any colour tints true."""
    rng = rng_for("lens_beam")
    img = np.zeros((S, S, 4), dtype=np.uint8)
    streak = rng.random(S)
    for y in range(S):
        for x in range(S):
            wave = 0.5 + 0.5 * math.sin((y / S) * 2 * math.pi * 2 + streak[x] * 6.0)
            v = int(round(200 + 55 * (0.55 * wave + 0.45 * streak[x])))
            img[y, x] = (v, v, v, 255)
    out(img, TEX / "fx" / "lens_beam.png")


# ---------------------------------------------------------------- items

def icons():
    outline = rgb("#3b3326")
    for name, face in (("loose_mirror", None), ("loose_gold_filter", "gold"), ("loose_teal_filter", "teal"),
                       ("loose_magenta_filter", "magenta")):
        img = np.zeros((S, S, 4), dtype=np.uint8)
        # a gold stand
        for x in range(5, 11):
            px(img, x, 14, GOLD[1])
            px(img, x, 13, GOLD[3])
        for y in range(10, 13):
            px(img, 7, y, GOLD[2])
            px(img, 8, y, GOLD[1])
        # the round mirror or lens, rimmed gold
        pal = SILVER if face is None else COLOURS[face]
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 6)
                if d <= 5.2:
                    if d > 4.2:
                        img[y, x] = (*GOLD[3 if (x + y) < 14 else 1], 255)
                    else:
                        k = int(np.clip(4 - (x + y - 6) / 4.0, 1, len(pal) - 1))
                        img[y, x] = (*pal[k], 255)
        px(img, 6, 4, (255, 255, 255))
        px(img, 7, 3, (255, 255, 255))
        # outline
        solid = img[..., 3] > 0
        for y in range(S):
            for x in range(S):
                if not solid[y, x] and any(0 <= x + dx < S and 0 <= y + dy < S and solid[y + dy, x + dx]
                                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    img[y, x] = (*outline, 255)
        out(img, ITEM / f"{name}.png")


# ---------------------------------------------------------------- models

def t(name):
    return f"cosmicbreach:block/{name}"


def faces(tex_all, up=None, down=None, **sides):
    f = {d: {"texture": tex_all} for d in ("north", "south", "east", "west")}
    for d, v in sides.items():
        f[d] = {"texture": v}
    f["up"] = {"texture": up or tex_all}
    f["down"] = {"texture": down or tex_all}
    return f


def base_elements():
    return [
        {"from": [3, 0, 3], "to": [13, 5, 13], "faces": faces("#pedestal_side", "#pedestal_top", "#pedestal_top")},
        {"from": [2, 5, 2], "to": [14, 7, 14], "faces": faces("#pedestal_side", "#pedestal_top", "#pedestal_top")},
    ]


PEDESTAL_TEX = {"pedestal_side": t("lens_pedestal_side"), "pedestal_top": t("lens_pedestal_top")}


def model(name, textures, elements, parent="block/block", ao=True, render=None):
    m = {"parent": parent, "textures": dict(textures)}
    if render:
        m["render_type"] = render
    if elements is not None:
        m["elements"] = elements
    if not ao:
        m["ambientocclusion"] = False
    path = MODELS / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8", newline="\n")
    written.append(path)


def models():
    model("lens_pedestal", {"particle": t("lens_pedestal_top"), **PEDESTAL_TEX}, base_elements())
    for loose in (False, True):
        frame = t("lens_mirror_frame_loose" if loose else "lens_mirror_frame")
        texs = {"particle": t("lens_mirror"), "mirror": t("lens_mirror"), "frame": frame, **PEDESTAL_TEX}
        plate = {"from": [1, 7, 7.5], "to": [15, 16, 8.5], "faces": faces("#frame", north="#mirror", south="#mirror")}
        foot = {"from": [6, 7, 6], "to": [10, 8, 10], "faces": faces("#frame")}
        suffix = "_loose" if loose else ""
        model(f"lens_mirror_straight{suffix}", texs, base_elements() + [foot, plate])
        diag = dict(plate)
        diag["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": -45}
        model(f"lens_mirror_diagonal{suffix}", texs, base_elements() + [foot, diag])
    # the splitter: a crystal turned 45 degrees, its point toward the light it takes (north in this model)
    model("lens_splitter", {"particle": t("lens_splitter"), "crystal": t("lens_splitter"), **PEDESTAL_TEX}, render="minecraft:translucent", elements=base_elements() + [
        {"from": [4.5, 7, 4.5], "to": [11.5, 14, 11.5], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
         "faces": faces("#crystal")},
        {"from": [6.5, 8, 1.5], "to": [9.5, 13, 5], "faces": faces("#crystal")},
    ])
    for colour in ("gold", "teal", "magenta"):
        for loose in (False, True):
            texs = {"particle": t(f"lens_filter_{colour}"), "glass": t(f"lens_filter_{colour}"),
                    "band": t("lens_mirror_frame_loose" if loose else "lens_mirror_frame"), **PEDESTAL_TEX}
            model(f"lens_filter_{colour}{'_loose' if loose else ''}", texs, render="minecraft:translucent", elements=base_elements() + [
                {"from": [3.5, 7, 3.5], "to": [12.5, 8, 12.5], "faces": faces("#band")},
                {"from": [4.5, 8, 4.5], "to": [11.5, 15, 11.5], "faces": faces("#glass")},
            ])
    model("lens_umbral", {"particle": t("lens_umbral"), "umbral": t("lens_umbral"), **PEDESTAL_TEX}, base_elements() + [
        {"from": [2, 6, 2], "to": [14, 15, 14], "faces": faces("#umbral")},
    ])
    model("lens_focus", {"particle": t("lens_focus"), "crystal": t("lens_focus"), **PEDESTAL_TEX}, base_elements() + [
        {"from": [5, 7, 5], "to": [11, 13, 11], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
         "faces": faces("#crystal")},
        {"from": [6, 9, 1], "to": [10, 13, 3], "faces": faces("#crystal")},
    ])
    for colour in COLOURS:
        for lit in (False, True):
            name = f"lens_receptor_{colour}{'_lit' if lit else ''}"
            texs = {"particle": t(name), "crystal": t(name), "mount": t("polished_starfall_stone"),
                    "mount_top": t("lens_socket_top")}
            model(name, texs, [
                {"from": [3, 0, 3], "to": [13, 3, 13], "faces": faces("#mount", "#mount_top", "#mount")},
                {"from": [6, 3, 6], "to": [10, 15, 10], "faces": faces("#crystal")},
                {"from": [3.5, 3, 8.5], "to": [6.5, 10, 11.5], "faces": faces("#crystal")},
                {"from": [9.5, 3, 4.5], "to": [12.5, 9, 7.5], "faces": faces("#crystal")},
            ])
    for awake in (False, True):
        front = t("warden_eye_awake" if awake else "warden_eye")
        model(f"warden_eye{'_awake' if awake else ''}", {"particle": front, "front": front, "side": t("warden_eye_side")}, [
            {"from": [2, 0, 2], "to": [14, 16, 14], "faces": faces("#side", north="#front")},
        ])
    model("lens_socket", {"particle": t("polished_starfall_stone"), "side": t("polished_starfall_stone"),
                          "top": t("lens_socket_top")}, [
        {"from": [3, 0, 3], "to": [13, 12, 13], "faces": faces("#side", "#top", "#side")},
    ])
    for open_ in (False, True):
        lens = t("sun_aperture_open" if open_ else "sun_aperture")
        model(f"sun_aperture{'_open' if open_ else ''}", {"particle": lens, "lens": lens, "side": t("starfall_stone_bricks")}, [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", "#side", "#lens")},
        ], ao=not open_)
    model("lens_core", {"particle": t("lens_core_top"), "top": t("lens_core_top"), "side": t("polished_starfall_stone")}, [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", "#top", "#side")},
    ])
    for vault in ("reliquary_vault", "observatory_vault"):
        for ready in (False, True):
            front = t(f"{vault}_front_ready" if ready else f"{vault}_front")
            model(f"{vault}{'_ready' if ready else ''}", {"particle": t(f"{vault}_side"), "front": front,
                                                          "side": t(f"{vault}_side"), "top": t(f"{vault}_top")}, [
                {"from": [1, 0, 1], "to": [15, 15, 15], "faces": faces("#side", "#top", "#top", north="#front")},
            ])
    model("kinetic_emitter", {"particle": t("driftstone_bricks"), "front": t("kinetic_emitter"),
                              "side": t("driftstone_bricks")}, [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", north="#front")},
    ])
    for invisible in ("kinetic_thread", "updraft", "guard_marker"):
        model(invisible, {"particle": t("rimeglass")}, None)


def main() -> int:
    written.clear()
    pedestal()
    mirror()
    splitter()
    filters()
    umbral()
    focus()
    receptors()
    warden_eye()
    aperture()
    core_tile()
    vaults()
    lights()
    beam()
    icons()
    models()
    for p in written:
        print("  wrote", rel(p))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
