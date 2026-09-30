"""The Breach Sanctum's art (W7): the Choir's last hall, its ivory and gold gone dark, lit by Rift Glass and the last
embers of Solenne; the Gate, the Throne Seal and the Eclipse Locks, the Regent's Throne, the wings' vault; the Dying
Star Heart, the Event Horizon Lens and the Hourglass of Vesper; the Voidsick icon.

    textures/block/
      sanctum_ivory                smooth ivory gone dark: warm grey, soot running down it, a few gold flecks
      sanctum_ivory_bricks         the same stone in long courses, dark mortar
      sanctum_gilt                 tarnished gold plate, brushed, a bevelled edge, a little green-brown patina
      sanctum_ember                an ember of Solenne: dark crust split by glowing orange-gold fissures (emissive)
      sanctum_rift_lamp            Rift Glass lit from within: violet panes in a lead lattice round a warm core (emissive)
      sanctum_umbral               voidscorched stone: near-black tiles with a violet glint in the joints
      choir_pillar_side/_top       a fluted ivory column, its end a ringed disc
      sanctum_gate                 gilded doors: tall panels, rivets, a thread of ember light at the leaves' seam
      sanctum_gate_open            the open Gate's veil: pale gold light in falling streaks, translucent
      throne_seal                  black stone with a violet eclipse sigil, the seal over the stair
      eclipse_lock / _lit          a black disc in a thin grey ring; lit, a blazing gold corona round it (emissive)
      sanctum_throne_*             the Regent's Throne: gilt base, ivory seat and back, the sun on the back, the socket,
                                   the Heart glowing in it
      sanctum_vault_front(_ready)/_side/_top
    textures/item/ dying_star_heart, event_horizon_lens, hourglass_of_vesper
    textures/mob_effect/ voidsick (18 by 18)
    blockstates/ and models/block/ for every block; models/item/ for the three items

Palette: ivory and gold darkened toward the Deep's violet, ember light the only warm bright thing; the Deep's blocks are
the dark ones, and the embers must be the first thing the eye finds.

Run:  python tools/art/gen_sanctum.py
"""
from __future__ import annotations

import json
import math

import numpy as np

from blockart import brick_layout, by_share, fbm, from_ramp, periodic_noise, ramp, rng_for
from common import ASSETS, TEX, rel, rgb, save_png

S = 16
BLOCK = TEX / "block"
ITEM = TEX / "item"
MODELS = ASSETS / "models"
STATES = ASSETS / "blockstates"

IVORY = ramp("#4d4538", "#6e6451", "#8f836a", "#ada07f", "#c9bc98")
GILT = ramp("#35260f", "#5f451a", "#8a6828", "#b28a3a", "#d9b565")
EMBER = ramp("#4a1404", "#9a360a", "#e06e14", "#ffac3c", "#fff0b0")
RIFT = ramp("#120920", "#221134", "#361a52", "#52287a", "#7c48aa")
UMBRAL = ramp("#0a090d", "#131118", "#1c1923", "#26212f", "#342c40")
PATINA = rgb("#4e5a3a")

written: list = []


def out(img, path):
    save_png(np.asarray(img, dtype=np.uint8), path)
    written.append(path)


def px(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


# ---------------------------------------------------------------- stone

def ivory(name="sanctum_ivory"):
    rng = rng_for(name)
    img = from_ramp(by_share(fbm(rng), (1, 4, 6, 4, 1)), IVORY)
    # soot running down in streaks
    streak = periodic_noise(rng, 3, aspect=(1, 5))
    for y in range(S):
        for x in range(S):
            if streak[y, x] > 0.72:
                img[y, x, :3] = (img[y, x, :3] * 0.72).astype(np.uint8)
    for x, y in ((3, 5), (11, 12), (13, 2)):
        px(img, x, y, GILT[4])
    return img


def ivory_bricks():
    rng = rng_for("sanctum_ivory_bricks")
    ids, mortar, iy, ix = brick_layout(4, 8, (0, 4, 2, 6))
    base = by_share(fbm(rng), (1, 3, 6, 4, 2))
    img = from_ramp(base, IVORY)
    for y in range(S):
        for x in range(S):
            if mortar[y, x]:
                img[y, x] = (*UMBRAL[3], 255)
            elif iy[y, x] == 0:
                img[y, x] = (*IVORY[min(4, base[y, x] + 1)], 255)
            elif iy[y, x] == 2 and base[y, x] < 2:
                img[y, x] = (*IVORY[0], 255)
    return img


def gilt(engraved=False):
    """Tarnished gold: brushed plate, a bevelled edge, a little patina; the engraved sun only where asked (the
    plate tiles over large walls, where a motif per block reads as a quilt)."""
    rng = rng_for("sanctum_gilt_engraved" if engraved else "sanctum_gilt")
    brushed = periodic_noise(rng, 2, aspect=(4, 1)) * 0.6 + fbm(rng) * 0.4
    img = from_ramp(by_share(brushed, (1, 4, 7, 3, 1)), GILT)
    for i in range(S):
        px(img, i, 0, GILT[4])
        px(img, 0, i, GILT[3])
        px(img, i, 15, GILT[0])
        px(img, 15, i, GILT[1])
    if engraved:
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                a = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                if 2.3 <= d <= 3.2:
                    img[y, x] = (*GILT[1], 255)
                elif d < 2.3:
                    img[y, x] = (*GILT[3], 255)
                elif 3.6 <= d <= 5.6 and abs(math.sin(a * 4)) > 0.92:
                    img[y, x] = (*GILT[1], 255)
    patina = fbm(rng_for("sanctum_gilt_patina"))
    for y in range(1, 15):
        for x in range(1, 15):
            if patina[y, x] > 0.93:
                img[y, x] = (*PATINA, 255)
    return img


def ember():
    rng = rng_for("sanctum_ember")
    crust = from_ramp(by_share(fbm(rng), (3, 4, 2)), [UMBRAL[2], UMBRAL[4], (70, 30, 16)])
    img = crust.copy()
    heat = fbm(rng, octaves=((2, 0.6), (4, 0.3), (8, 0.1)))
    fiss = periodic_noise(rng, 4) - periodic_noise(rng, 4)
    for y in range(S):
        for x in range(S):
            f = abs(fiss[y, x])
            if f < 0.12:
                k = 4 if heat[y, x] > 0.6 else 3
                img[y, x] = (*EMBER[k], 255)
            elif f < 0.26:
                img[y, x] = (*EMBER[3 if heat[y, x] > 0.5 else 2], 255)
            elif heat[y, x] > 0.7:
                img[y, x] = (*EMBER[2], 255)
            elif heat[y, x] > 0.45:
                img[y, x] = (*EMBER[1], 255)
    return img


def rift_lamp():
    rng = rng_for("sanctum_rift_lamp")
    img = from_ramp(by_share(fbm(rng), (2, 4, 4, 2)), RIFT[1:])
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            glow = max(0.0, 1.0 - d / 6.5)
            if glow > 0:
                c = np.array(img[y, x, :3], dtype=float) * (1 - glow) + np.array(EMBER[3 if glow > 0.55 else 2], dtype=float) * glow
                img[y, x, :3] = np.clip(c, 0, 255).astype(np.uint8)
            if d < 1.6:
                img[y, x] = (*EMBER[4], 255)
            lead = (x + y) % 8 == 0 or (x - y) % 8 == 0
            if lead and d > 2.2:
                img[y, x] = (*UMBRAL[1], 255)
            if x in (0, 15) or y in (0, 15):
                img[y, x] = (*GILT[1], 255)
    return img


def umbral():
    rng = rng_for("sanctum_umbral")
    img = from_ramp(by_share(fbm(rng), (2, 5, 5, 3, 1)), UMBRAL)
    for i in range(S):
        for j in (0, 8):
            px(img, i, j, UMBRAL[0])
            px(img, j, i, UMBRAL[0])
    for x, y in ((0, 3), (8, 11), (4, 8), (12, 0)):
        px(img, x, y, RIFT[4])
    return img


def pillar_side():
    img = ivory("choir_pillar_side")
    for x in range(S):
        flute = x % 4
        for y in range(S):
            if flute == 0:
                img[y, x, :3] = (img[y, x, :3] * 0.62).astype(np.uint8)
            elif flute == 1:
                img[y, x, :3] = np.minimum(255, img[y, x, :3].astype(int) + 18).astype(np.uint8)
    return img


def pillar_top():
    img = ivory("choir_pillar_top")
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.6 <= d <= 6.6 or 2.4 <= d <= 3.1:
                img[y, x] = (*GILT[3], 255)
            elif d > 7.3:
                img[y, x] = (*IVORY[0], 255)
    return img


# ---------------------------------------------------------------- the Gate, the seal, the locks

def gate(open_):
    if open_:
        rng = rng_for("sanctum_gate_open")
        img = np.zeros((S, S, 4), dtype=np.uint8)
        streak = periodic_noise(rng, 2, aspect=(1, 6))
        for y in range(S):
            for x in range(S):
                v = streak[y, x]
                c = EMBER[4] if v > 0.75 else EMBER[3] if v > 0.45 else (255, 214, 140)
                img[y, x] = (*c, int(95 + 110 * v))
        return img
    img = gilt()
    for y in range(S):
        for x in range(S):
            # tall panels, dark seams between them, rivets down the seams
            if x in (0, 7, 8, 15):
                img[y, x] = (*GILT[0], 255)
            elif x in (1, 9):
                img[y, x] = (*GILT[4], 255)
            if x in (3, 12) and y % 5 == 2:
                img[y, x] = (*GILT[4], 255)
    # a thread of ember light where the two leaves meet (the doors tile, so nothing round on them)
    for y in range(S):
        px(img, 7, y, EMBER[2] if y % 4 else EMBER[3])
        px(img, 8, y, GILT[0])
    return img


def seal():
    img = umbral()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 4.4 <= d <= 5.4:
                img[y, x] = (*RIFT[4], 255)
            elif 2.2 <= d <= 2.9:
                img[y, x] = (*RIFT[3], 255)
            elif d < 2.2:
                img[y, x] = (*UMBRAL[0], 255)
    return img


def lock_front(lit):
    img = gilt() if not lit else gilt()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 4.2:
                img[y, x] = (*UMBRAL[0], 255)
            elif d <= 5.6:
                img[y, x] = (*(EMBER[4] if lit and d <= 4.9 else EMBER[3] if lit else IVORY[1]), 255)
            elif d <= 7.2:
                if lit:
                    k = 2 if (int(math.degrees(math.atan2(y - 7.5, x - 7.5))) // 20) % 2 == 0 else 3
                    img[y, x] = (*EMBER[k], 255)
                else:
                    img[y, x] = (*UMBRAL[3], 255)
    return img


# ---------------------------------------------------------------- the throne and the vault

def throne_textures():
    base = gilt(engraved=True)
    back = ivory("sanctum_throne_back")
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 6)
            a = math.atan2(y + 0.5 - 6, x + 0.5 - 8)
            if d < 2.6:
                back[y, x] = (*EMBER[3], 255)
            elif d < 3.4:
                back[y, x] = (*GILT[4], 255)
            elif d < 6.0 and abs(math.sin(a * 6)) > 0.9:
                back[y, x] = (*GILT[2], 255)
            if x in (0, 15):
                back[y, x] = (*GILT[1], 255)
    seat = ivory("sanctum_throne_seat")
    for i in range(S):
        px(seat, i, 0, GILT[3])
        px(seat, i, 15, GILT[1])
    socket = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            socket[y, x] = (*(UMBRAL[0] if d < 5 else GILT[2] if d < 6.5 else GILT[1]), 255)
    heart = heart_texture(block=True)
    return {"sanctum_throne_base": base, "sanctum_throne_back": back, "sanctum_throne_seat": seat,
            "sanctum_throne_socket": socket, "sanctum_throne_heart": heart}


def vault_textures():
    side = ivory_bricks()
    for x in range(S):
        px(side, x, 0, GILT[3])
        px(side, x, 15, GILT[1])
    top = gilt()
    fronts = {}
    for ready in (False, True):
        f = ivory_bricks()
        for y in range(2, 14):
            for x in range(3, 13):
                edge = x in (3, 12) or y in (2, 13)
                f[y, x] = (*(GILT[3] if edge else UMBRAL[2]), 255)
        for y in range(3, 13):
            px(f, 7, y, EMBER[4] if ready else GILT[0])
            px(f, 8, y, EMBER[3] if ready else GILT[1])
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if 1.6 <= d <= 3.0:
                    f[y, x] = (*(EMBER[3] if ready else GILT[2]), 255)
        fronts["sanctum_vault_front_ready" if ready else "sanctum_vault_front"] = f
    return {"sanctum_vault_side": side, "sanctum_vault_top": top, **fronts}


# ---------------------------------------------------------------- items and the effect icon

def heart_texture(block=False):
    """The Dying Star Heart: a dark orb with a dying gold core, cracks of light, a faint violet rim."""
    img = np.zeros((S, S, 4), dtype=np.uint8)
    cx, cy, r = 7.5, 8.0, 6.4
    rng = rng_for("dying_star_heart")
    cracks = np.abs(periodic_noise(rng, 3) - periodic_noise(rng, 3))
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy)
            if d > r:
                continue
            k = d / r
            if k < 0.32:
                c = EMBER[4] if k < 0.16 else EMBER[3]
            elif cracks[y, x] < 0.07 and k < 0.9:
                c = EMBER[2]
            else:
                shade = 0.5 - 0.5 * ((x - cx) + (y - cy)) / (r * 1.4)
                c = UMBRAL[int(np.clip(1 + shade * 3, 0, 4))]
            if k > 0.86:
                c = RIFT[3] if (x + y) % 2 else RIFT[2]
            img[y, x] = (*c, 255)
    px(img, 5, 5, (255, 255, 255))
    if block:
        img[img[..., 3] == 0] = (*UMBRAL[0], 255)
    return img


def event_horizon_lens():
    img = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 4.4:
                img[y, x] = (*UMBRAL[0], 255)
            elif d <= 5.3:
                img[y, x] = (*(EMBER[4] if (x + y) % 3 else (255, 255, 255)), 255)
            elif d <= 6.4:
                img[y, x] = (*GILT[2 if x + y < 16 else 1], 255)
            elif d <= 7.3:
                img[y, x] = (*RIFT[3], 150)
    px(img, 6, 6, RIFT[4])
    return img


def hourglass_of_vesper():
    rows = [
        "................",
        "...gggggggggg...",
        "....G......G....",
        "....G.vvvv.G....",
        "....G.vvvv.G....",
        ".....G.vv.G.....",
        "......G..G......",
        ".......GG.......",
        ".......GG.......",
        "......G..G......",
        ".....G.v..G.....",
        "....G..vv..G....",
        "....G.vvvv.G....",
        "....GvvvvvvG....",
        "...gggggggggg...",
        "................",
    ]
    pal = {"g": GILT[3], "G": (196, 220, 240), "v": (164, 104, 230)}
    img = np.zeros((S, S, 4), dtype=np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img[y, x] = (*pal[ch], 255 if ch != "G" else 200)
    for x in range(3, 13):
        px(img, x, 1, GILT[4])
        px(img, x, 14, GILT[1])
    return img


def voidsick_icon():
    img = np.zeros((18, 18, 4), dtype=np.uint8)
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - 9, y + 0.5 - 9
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            if d > 8.2:
                continue
            spiral = math.sin(a * 2 + d * 0.9)
            if d < 2.0:
                img[y, x] = (*UMBRAL[0], 255)
            elif spiral > 0.35:
                img[y, x] = (*RIFT[4 if d < 5 else 3], 255)
            elif d > 7.2:
                img[y, x] = (*RIFT[1], 255)
    return img


# ---------------------------------------------------------------- models and states

def t(name):
    return f"cosmicbreach:block/{name}"


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    written.append(path)


def faces(tex_all, up=None, down=None, **sides):
    f = {d: {"texture": tex_all} for d in ("north", "south", "east", "west")}
    for d, v in sides.items():
        f[d] = {"texture": v}
    f["up"] = {"texture": up or tex_all}
    f["down"] = {"texture": down or tex_all}
    return f


def cube(name, texture, emissive=False, render=None):
    if not emissive:
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": t(texture)}}
    else:
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False,
              "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": d}
                        for d in ("north", "south", "east", "west", "up", "down")},
              "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}}
        m = {"parent": "minecraft:block/block", "textures": {"all": t(texture), "particle": t(texture)}, "elements": [el]}
    if render:
        m["render_type"] = render
    write_json(MODELS / "block" / f"{name}.json", m)


def simple_state(name, model=None):
    write_json(STATES / f"{name}.json", {"variants": {"": {"model": t(model or name)}}})


def facing_state(name, model_of, extra=None):
    """Variants over facing (the model faces north) and, if given, one boolean property."""
    variants = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        values = [None] if extra is None else [False, True]
        for v in values:
            key = f"facing={f}" if v is None else f"facing={f},{extra}={'true' if v else 'false'}"
            entry = {"model": t(model_of(v))}
            if y:
                entry["y"] = y
            variants[key] = entry
    write_json(STATES / f"{name}.json", {"variants": variants})


def stairs_and_slab():
    base = t("sanctum_ivory")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write_json(MODELS / "block" / f"sanctum_ivory_stairs{suffix}.json", {"parent": f"minecraft:block/{parent}",
                   "textures": {"bottom": base, "side": base, "top": base}})
    write_json(MODELS / "block" / "sanctum_ivory_slab.json", {"parent": "minecraft:block/slab",
               "textures": {"bottom": base, "side": base, "top": base}})
    write_json(MODELS / "block" / "sanctum_ivory_slab_top.json", {"parent": "minecraft:block/slab_top",
               "textures": {"bottom": base, "side": base, "top": base}})
    write_json(STATES / "sanctum_ivory_slab.json", {"variants": {
        "type=bottom": {"model": t("sanctum_ivory_slab")}, "type=double": {"model": t("sanctum_ivory")},
        "type=top": {"model": t("sanctum_ivory_slab_top")}}})
    # the vanilla stairs state (BlockModelGenerators.createStairs), for our three models
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in rot.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = "sanctum_ivory_stairs" + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = y0
                if half == "bottom" and shape.endswith("left"):
                    y = (y0 + 270) % 360
                if half == "top" and shape.endswith("right"):
                    y = (y0 + 90) % 360
                entry = {"model": t(model)}
                if half == "top":
                    entry["x"] = 180
                if y:
                    entry["y"] = y
                if half == "top" or y:
                    entry["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = entry
    write_json(STATES / "sanctum_ivory_stairs.json", {"variants": variants})


def models():
    for name in ("sanctum_ivory", "sanctum_ivory_bricks", "sanctum_gilt", "sanctum_umbral", "throne_seal"):
        cube(name, name)
        simple_state(name)
    cube("sanctum_ember", "sanctum_ember", emissive=True)
    simple_state("sanctum_ember")
    cube("sanctum_rift_lamp", "sanctum_rift_lamp", emissive=True)
    simple_state("sanctum_rift_lamp")
    stairs_and_slab()
    write_json(MODELS / "block" / "choir_pillar.json", {"parent": "minecraft:block/cube_column",
               "textures": {"end": t("choir_pillar_top"), "side": t("choir_pillar_side")}})
    write_json(MODELS / "block" / "choir_pillar_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
               "textures": {"end": t("choir_pillar_top"), "side": t("choir_pillar_side")}})
    write_json(STATES / "choir_pillar.json", {"variants": {
        "axis=y": {"model": t("choir_pillar")},
        "axis=z": {"model": t("choir_pillar_horizontal"), "x": 90},
        "axis=x": {"model": t("choir_pillar_horizontal"), "x": 90, "y": 90}}})
    cube("sanctum_gate", "sanctum_gate")
    cube("sanctum_gate_open", "sanctum_gate_open", render="minecraft:translucent")
    write_json(STATES / "sanctum_gate.json", {"variants": {"open=false": {"model": t("sanctum_gate")},
                                                           "open=true": {"model": t("sanctum_gate_open")}}})
    for lit in (False, True):
        name = "eclipse_lock_lit" if lit else "eclipse_lock"
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", north="#front")}
        if lit:
            el["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        write_json(MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/block", "textures": {
            "particle": t("sanctum_gilt"), "side": t("sanctum_gilt"), "front": t(name)}, "elements": [el]})
    facing_state("eclipse_lock", lambda lit: "eclipse_lock_lit" if lit else "eclipse_lock", "lit")
    for ready in (False, True):
        front = t("sanctum_vault_front_ready" if ready else "sanctum_vault_front")
        write_json(MODELS / "block" / f"sanctum_vault{'_ready' if ready else ''}.json", {"parent": "minecraft:block/block",
                   "textures": {"particle": t("sanctum_vault_side"), "front": front, "side": t("sanctum_vault_side"),
                                "top": t("sanctum_vault_top")},
                   "elements": [{"from": [1, 0, 1], "to": [15, 15, 15], "faces": faces("#side", "#top", "#top", north="#front")}]})
    facing_state("sanctum_vault", lambda ready: "sanctum_vault_ready" if ready else "sanctum_vault", "ready")
    # the throne faces north in the model: its back along the south side
    tx = {"particle": t("sanctum_throne_base"), "base": t("sanctum_throne_base"), "back": t("sanctum_throne_back"),
          "seat": t("sanctum_throne_seat"), "socket": t("sanctum_throne_socket"), "heart": t("sanctum_throne_heart")}
    els = [
        {"from": [0, 0, 0], "to": [16, 4, 16], "faces": faces("#base")},
        {"from": [1, 4, 1], "to": [15, 9, 15], "faces": faces("#seat", "#seat", "#base")},
        {"from": [1, 9, 12], "to": [15, 28, 15], "faces": faces("#seat", "#base", "#base", north="#back", south="#seat")},
        {"from": [3, 28, 12.5], "to": [13, 31, 14.5], "faces": faces("#base")},
        {"from": [7, 31, 13], "to": [9, 32, 14], "faces": faces("#base")},
        {"from": [1, 9, 2], "to": [3, 13, 12], "faces": faces("#base")},
        {"from": [13, 9, 2], "to": [15, 13, 12], "faces": faces("#base")},
        {"from": [5.5, 9, 5.5], "to": [10.5, 9.5, 10.5], "faces": faces("#socket")},
    ]
    write_json(MODELS / "block" / "sanctum_throne.json", {"parent": "minecraft:block/block", "textures": tx, "elements": els})
    heart = {"from": [6, 9.5, 6], "to": [10, 13.5, 10], "faces": faces("#heart"),
             "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}}
    write_json(MODELS / "block" / "sanctum_throne_heart.json", {"parent": "minecraft:block/block", "textures": tx,
                                                                "elements": els + [heart]})
    facing_state("sanctum_throne", lambda h: "sanctum_throne_heart" if h else "sanctum_throne", "heart")
    for item in ("dying_star_heart", "event_horizon_lens", "hourglass_of_vesper"):
        write_json(MODELS / "item" / f"{item}.json", {"parent": "minecraft:item/generated",
                                                     "textures": {"layer0": f"cosmicbreach:item/{item}"}})


def main() -> int:
    written.clear()
    out(ivory(), BLOCK / "sanctum_ivory.png")
    out(ivory_bricks(), BLOCK / "sanctum_ivory_bricks.png")
    out(gilt(), BLOCK / "sanctum_gilt.png")
    out(ember(), BLOCK / "sanctum_ember.png")
    out(rift_lamp(), BLOCK / "sanctum_rift_lamp.png")
    out(umbral(), BLOCK / "sanctum_umbral.png")
    out(pillar_side(), BLOCK / "choir_pillar_side.png")
    out(pillar_top(), BLOCK / "choir_pillar_top.png")
    out(gate(False), BLOCK / "sanctum_gate.png")
    out(gate(True), BLOCK / "sanctum_gate_open.png")
    out(seal(), BLOCK / "throne_seal.png")
    out(lock_front(False), BLOCK / "eclipse_lock.png")
    out(lock_front(True), BLOCK / "eclipse_lock_lit.png")
    for name, img in {**throne_textures(), **vault_textures()}.items():
        out(img, BLOCK / f"{name}.png")
    out(heart_texture(), ITEM / "dying_star_heart.png")
    out(event_horizon_lens(), ITEM / "event_horizon_lens.png")
    out(hourglass_of_vesper(), ITEM / "hourglass_of_vesper.png")
    out(voidsick_icon(), TEX / "mob_effect" / "voidsick.png")
    models()
    for p in written:
        print("wrote", rel(p))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
