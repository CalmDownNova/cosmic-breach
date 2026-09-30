"""The Umbra Cantor, the Heliarch's longbow (GDD 7.3): a dark bow with a violet string. Writes, for every draw stage
d (0 rest, 1 to 3 drawn further, an arrow of shadow nocked), the bow itself (textures/item/umbra_cantor.png at rest, the
inventory icon's base, and umbra_cantor_d1..3.png) and its light for every glow stage g (umbra_cantor_light_d{d}_g{g}.png:
the string, the limbs' inlay and the arrow's head, brighter with Resonance; the game draws it at full brightness so the
string glows in the dark), plus the item models (models/item/umbra_cantor*.json, NeoForge's item_layers loader) whose
overrides pick them from the cosmicbreach:draw and cosmicbreach:resonance properties.

Laid out like vanilla's bow, so vanilla's first-person display holds it the familiar way: the string runs corner to
corner from the lower left to the upper right, the limbs arc out to the upper left with the grip at the apex, and a
drawn string is pulled toward the lower right with the arrow pointing through the grip. The held third-person display
(1.1x) puts the grip texel where Meridian's grip lands, so the shared animations hold it by the grip.

Run:  python tools/art/gen_umbra_cantor.py
"""
from __future__ import annotations

import json
import math

import numpy as np

from common import ASSETS, PREVIEWS, TEX, blank, outline_mask, over, rel, rgb, save_png, supersample, upscale, white_alpha

S = 32
A = np.array([3.5, 28.5])   # lower tip (pixel centres)
B = np.array([28.5, 3.5])   # upper tip
MID = (A + B) / 2.0
DIAG = (B - A) / np.linalg.norm(B - A)          # along the string, toward the upper tip
OUT = np.array([-1.0, -1.0]) / math.sqrt(2.0)   # from the string toward the arc's apex (upper left)
DEPTH = 8.6                                     # the arc's apex, this far out from the string
APEX = MID + OUT * DEPTH
PULL = [0.0, 2.6, 4.6, 6.4]                     # how far the string is drawn back at each draw stage
SCALE = 1.1

LIMB = {"hi": rgb("#5a4870"), "mid": rgb("#382b4a"), "shade": rgb("#241a31"), "outline": rgb("#0e0a14")}
INLAY = [rgb("#6e5a98"), rgb("#8e74c8"), rgb("#b69cf0"), rgb("#e2d4ff")]
GRIP = {"light": rgb("#4b2f6b"), "mid": rgb("#2f1c46"), "band": rgb("#9a86c4")}
STRING = [rgb("#8e62d6"), rgb("#b184ff"), rgb("#d4b6ff"), rgb("#f4ecff")]
SHAFT = rgb("#2c2139")
ARROWHEAD = [rgb("#9d78e0"), rgb("#bb97ff"), rgb("#d9c2ff"), rgb("#ffffff")]
FLETCH = rgb("#6b4d9a")
GLINT = rgb("#efe2ff")


def bezier(t):
    """The arc from A to B through the apex (a quadratic with its control past the apex)."""
    c = 2.0 * APEX - MID
    t = np.asarray(t)[..., None]
    return (1 - t) ** 2 * A + 2 * t * (1 - t) * c + t ** 2 * B


def dist_to_polyline(px, py, pts):
    best = np.full(px.shape, np.inf)
    along = np.zeros(px.shape)
    total = 0.0
    lengths = [0.0]
    for a, b in zip(pts[:-1], pts[1:]):
        total += float(np.linalg.norm(b - a))
        lengths.append(total)
    for i, (a, b) in enumerate(zip(pts[:-1], pts[1:])):
        d = b - a
        l2 = float(d @ d)
        t = np.clip(((px - a[0]) * d[0] + (py - a[1]) * d[1]) / max(l2, 1e-9), 0.0, 1.0)
        qx, qy = a[0] + t * d[0] - px, a[1] + t * d[1] - py
        dist = np.sqrt(qx * qx + qy * qy)
        closer = dist < best
        best = np.where(closer, dist, best)
        along = np.where(closer, (lengths[i] + t * math.sqrt(l2)) / total, along)
    return best, along


def build(draw: int, glow: int, with_light: bool = False):
    img = blank(S, S)
    ys, xs = np.mgrid[0:S, 0:S]
    px, py = xs + 0.5, ys + 0.5
    part = np.zeros((S, S), dtype=int)  # 1 limb, 2 grip, 3 string, 4 arrow
    col = np.zeros((S, S, 3), dtype=int)

    # ---- the limbs: thick at the grip, thin at the tips, lit on their outer (upper-left) side
    arc = bezier(np.linspace(0.0, 1.0, 64))
    d, t = dist_to_polyline(px, py, arc)
    half = 0.55 + 0.95 * np.sin(np.pi * t) ** 0.8
    limb = d <= half
    side = (px - MID[0]) * OUT[0] + (py - MID[1]) * OUT[1] - DEPTH * np.sin(np.pi * t) * 0.95
    col[limb] = LIMB["mid"]
    col[limb & (side > 0.25)] = LIMB["hi"]
    col[limb & (side < -0.6)] = LIMB["shade"]
    inlay = limb & (np.abs(side) < 0.35) & (np.abs(t - 0.5) > 0.12) & (np.abs(t - 0.5) < 0.42) & ((xs + ys) % 3 == 0)
    col[inlay] = INLAY[glow]
    part[limb] = 1

    # ---- the grip at the apex: violet silk with a pale band either side
    grip = limb & (np.abs(t - 0.5) <= 0.085)
    col[grip] = np.where(((xs + ys) % 2 == 0)[..., None], GRIP["light"], GRIP["mid"])[grip]
    band = limb & (np.abs(np.abs(t - 0.5) - 0.1) < 0.018)
    col[band] = GRIP["band"]
    part[grip | band] = 2

    # ---- the string: straight at rest; drawn back to a nock point
    nock = MID - OUT * PULL[draw]
    string_pts = np.array([A, nock, B]) if draw else np.array([A, B])
    sd, _ = dist_to_polyline(px, py, string_pts)
    string = (sd <= 0.5) & ~limb
    col[string] = STRING[glow]
    part[string] = 3

    # ---- the nocked arrow of shadow, from the nock through the grip and past it
    glowing_tip = np.zeros((S, S), dtype=bool)
    if draw:
        head = APEX + OUT * 4.6
        ad, at = dist_to_polyline(px, py, np.array([nock, head]))
        arrow = ad <= 0.5
        col[arrow] = SHAFT
        hd = np.sqrt((px - head[0]) ** 2 + (py - head[1]) ** 2)
        tip = (hd <= 1.6) & (at > 0.8)
        col[tip] = ARROWHEAD[glow]
        glowing_tip = tip
        fl = (ad <= 1.1) & (at < 0.1) & ~limb
        col[fl] = FLETCH
        part[arrow | tip | fl] = 4

    filled = part > 0
    img[filled, :3] = col[filled]
    img[filled, 3] = 255
    ol = outline_mask(filled) & ~(outline_mask(part == 3) & ~outline_mask(part != 3))
    ol = outline_mask(filled)
    string_only = np.zeros_like(filled)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        string_only |= np.roll(np.roll(part == 3, dy, axis=0), dx, axis=1)
    near_other = np.zeros_like(filled)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near_other |= np.roll(np.roll((part == 1) | (part == 2) | (part == 4), dy, axis=0), dx, axis=1)
    ol &= near_other  # the string glows bare: only the bow and arrow get the dark outline
    img[ol, :3] = LIMB["outline"]
    img[ol, 3] = 255
    light = string | inlay | glowing_tip
    if glow == 3:
        for (x, y) in ((6, 5), (11, 3), (3, 13)):
            if img[y, x, 3] == 0:
                img[y, x, :3] = GLINT
                img[y, x, 3] = 255
                light[y, x] = True
    return (img, light) if with_light else img


def grip_texel():
    return (float(APEX[0]), float(APEX[1]))


def display_translation(scale: float, grip):
    """The third-person display translation (pixels) that puts the grip texel where Meridian's rig puts its own."""
    x = grip[0] / 32.0 - 0.5
    y = (1.0 - grip[1] / 32.0) - 0.5
    c, s = math.cos(math.radians(55)), math.sin(math.radians(55))
    rx, ry = x * c - y * s, x * s + y * c
    mx, my = 8.2 / 32.0 - 0.5, (1.0 - 22.8 / 32.0) - 0.5
    mrx, mry = mx * c - my * s, mx * s + my * c
    ref_y, ref_z = 4 / 16.0 + 0.85 * mry, 0.5 / 16.0 + 0.85 * mrx
    return (0.0, round((ref_y - scale * ry) * 16.0, 3), round((ref_z - scale * rx) * 16.0, 3))


DRAW = [0.0, 0.25, 0.6, 0.95]
GLOW = [0.0, 0.34, 0.67, 1.0]


FULL_BRIGHT = {"block_light": 15, "sky_light": 15}


def body_name(d):
    return "umbra_cantor" if d == 0 else f"umbra_cantor_d{d}"


def light_name(d, g):
    return f"umbra_cantor_light_d{d}_g{g}"


def write_models():
    models = ASSETS / "models" / "item"
    for stale in list(models.glob("umbra_cantor_*.json")):
        stale.unlink()  # every variant is rewritten below
    t = display_translation(SCALE, grip_texel())
    base = {
        "parent": "minecraft:item/generated",
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": list(t), "scale": [SCALE] * 3},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": list(t), "scale": [SCALE] * 3},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
        },
    }
    paths = [_write(models / "umbra_cantor_base.json", base)]

    def doc(d, g):
        return {"parent": "cosmicbreach:item/umbra_cantor_base", "loader": "neoforge:item_layers",
                "textures": {"layer0": "cosmicbreach:item/" + body_name(d), "layer1": "cosmicbreach:item/" + light_name(d, g)},
                "neoforge_data": {"layers": {"1": FULL_BRIGHT}}}

    overrides = []
    for d in range(4):
        for g in range(4):
            if d == 0 and g == 0:
                continue
            name = f"umbra_cantor_d{d}_g{g}"
            paths.append(_write(models / (name + ".json"), doc(d, g)))
            overrides.append({"predicate": {"cosmicbreach:draw": DRAW[d], "cosmicbreach:resonance": GLOW[g]},
                              "model": "cosmicbreach:item/" + name})
    paths.append(_write(models / "umbra_cantor.json", dict(doc(0, 0), overrides=overrides)))
    return paths


def _write(path, doc):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(doc, indent=2) + "\n")
    return path


def debug_sheet(images, path, cols=4):
    k = 6
    pad = 8
    cell = S * k + pad
    rows = (len(images) + cols - 1) // cols
    out = np.zeros((pad + rows * cell, pad + cols * cell, 4), dtype=np.uint8)
    out[..., :3] = 110
    out[..., 3] = 255
    for i, im in enumerate(images):
        over(out, upscale(im, k), pad + (i % cols) * cell, pad + (i // cols) * cell)
    save_png(out, path)


def note_texture(size: int = 64) -> np.ndarray:
    """textures/fx/cantor_note.png: a quaver (a filled oval head, a stem, a flag), white with its shape in alpha, soft at
    the rim; the resonant notes and the chord's corners draw it tinted."""
    def f(x, y):
        x, y = x / size, y / size
        # head: a tilted oval low on the left
        hx, hy = (x - 0.38) / 0.17, (y - 0.72) / 0.12
        c, s_ = np.cos(-0.45), np.sin(-0.45)
        rx, ry = hx * c - hy * s_, hx * s_ + hy * c
        head = np.clip(1.25 - np.sqrt(rx * rx + ry * ry), 0.0, 1.0) * 4.0
        stem = np.clip(1.0 - np.abs(x - 0.535) / 0.035, 0.0, 1.0) * ((y > 0.18) & (y < 0.7))
        flag_x = 0.535 + 0.22 * np.sin(np.clip((y - 0.18) / 0.34, 0, 1) * np.pi * 0.9)
        flag = np.clip(1.0 - np.abs(x - flag_x) / 0.05, 0.0, 1.0) * ((y > 0.18) & (y < 0.52)) * (x >= 0.52)
        return np.clip(np.maximum(np.maximum(head, stem), flag), 0.0, 1.0)
    return white_alpha(supersample(f, size, size, 4))


def main():
    out = TEX / "item"
    for stale in list(out.glob("umbra_cantor_d*_g*.png")):
        stale.unlink()  # the glow stages are light layers now
    paths = []
    sheet = []
    for d in range(4):
        paths.append(save_png(build(d, 0), out / (body_name(d) + ".png")))
        for g in range(4):
            full, light = build(d, g, with_light=True)
            layer = blank(S, S)
            layer[light] = full[light]
            sheet.append(full)
            paths.append(save_png(layer, out / (light_name(d, g) + ".png")))
    debug_sheet(sheet, PREVIEWS / "umbra_cantor_stages.png")
    paths += write_models()
    paths.append(save_png(note_texture(), TEX / "fx" / "cantor_note.png"))
    for p in paths:
        print("wrote", rel(p))
    print("grip texel:", grip_texel(), "display translation:", display_translation(SCALE, grip_texel()))


if __name__ == "__main__":
    main()
