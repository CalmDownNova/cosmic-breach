"""The drift jelly (1.2, Lane C): geo, animations, textures and icons.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/drift_jelly.geo.json                  Bedrock geometry 1.12.0, per-face UV, 16 units a block
    animations/entity/drift_jelly.animation.json     idle, hurt, squish (a bounce), death
    textures/entity/drift_jelly.png                  deep violet cubes, magenta and teal markings, a translucent bell
    textures/entity/drift_jelly_glowmask.png         the core and every marking (the game adds it as light)
    textures/item/drift_gel.png, candied_gel.png     16x16
    textures/mob_effect/skim.png                     18x18

The look (concept 4 of the blocky sheet, Meshy 7 as the 3D reference; proportions read off its GLB in Blender):
bell 3 blocks wide with a stepped dome on top of a translucent glass body, a glowing core inside it, a ring of square
frilled flaps round the rim, two simpler frilled ribbons under the bell, and eight tendrils of four jointed bones each
that hang 3.7 to 5.3 blocks and sway. Magenta and teal pixels on deep violet.

Conventions as the other GeckoLib generators: Bedrock model space, y up, 16 units a block, the creature's left is +X.
y = 0 is the lowest point of the flaps (the entity's feet). Bones are written in drawing order (GeckoLib draws a bone
and then its children): the opaque parts first, the glass last, so the translucent glass blends over what it holds.

Run:  python tools/art/gen_drift_jelly.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np

from common import ASSETS, TEX, rel, save_png

TEX_W, TEX_H = 256, 256
GLOW_K = 0.7              # the glowmask is added over the lit model: a little under the marking's own colour, so it never burns out to white

# palette
V_DARK = (30, 18, 84)
V_BASE = (58, 34, 142)
V_MID = (84, 50, 184)
V_LIGHT = (122, 84, 232)
GLASS = (132, 84, 255)
LILAC = (170, 132, 244)
MAGENTA = (238, 62, 230)
MAGENTA_DIM = (176, 38, 176)
TEAL = (48, 238, 222)
TEAL_DIM = (30, 154, 160)
PINK = (255, 132, 250)
WHITE = (236, 226, 255)


class Cube:
    def __init__(self, origin, size, kind, faces=None, pivot=None, rotation=None, tag=0):
        self.origin = origin
        self.size = size
        self.kind = kind
        self.faces = faces or ["north", "south", "east", "west", "up", "down"]
        self.pivot = pivot
        self.rotation = rotation
        self.tag = tag            # a number the painter varies the markings by
        self.uv = {}


class Bone:
    def __init__(self, name, parent, pivot, cubes=(), rotation=None):
        self.name = name
        self.parent = parent
        self.pivot = pivot
        self.cubes = list(cubes)
        self.rotation = rotation


def box(cx, y0, cz, w, h, d, kind, **kw):
    return Cube((cx - w / 2, y0, cz - d / 2), (w, h, d), kind, **kw)


# ------------------------------------------------------------------ the model
FLAP_W, FLAP_H = 6, 8
RING = 23.5                       # half the rim, to the flaps' middle
FLAP_TILT = 20                    # degrees outward
TENDRILS = [                      # x, z, segment lengths (units); 4 to 5.3 blocks of hang below the flaps
    (-8, -14, (22, 22, 20, 18)),
    (6, -15, (18, 18, 16, 16)),
    (15, -7, (24, 22, 20, 18)),
    (14, 8, (20, 18, 18, 16)),
    (7, 15, (22, 20, 20, 18)),
    (-7, 14, (24, 22, 20, 18)),
    (-15, 7, (18, 18, 16, 16)),
    (-14, -8, (22, 20, 18, 16)),
]
KINK = (0, 1, 2, 1)              # each joint of a tendril steps a little sideways: the blocky wave of the concept's
FRILLS = [(-5, -1, 0, (13, 13, 12)), (5, 2, 1, (13, 12, 12))]   # x, z, tag, segment lengths
COLLAR_Y, COLLAR_H = 4, 3
GLASS_Y, GLASS_H = 7, 13
TIERS = [(40, 3), (38, 3), (35, 3), (30, 3), (23, 2), (14, 2)]                    # width, height; stacked on the glass
CORE = 10
TENDRIL_TOP = 4


def build():
    bones = [Bone("root", None, (0, 0, 0))]
    # the glowing core first: everything translucent is drawn after it
    bones.append(Bone("core", "root", (0, GLASS_Y + GLASS_H / 2, 0), [
        box(0, GLASS_Y + (GLASS_H - CORE) / 2, 0, CORE, CORE, CORE, "core")]))
    # the two inner frills: chains of three, translucent lilac ribbons
    for fi, (fx, fz, tag, lens) in enumerate(FRILLS):
        parent, top = "root", TENDRIL_TOP
        for j, ln in enumerate(lens):
            name = f"frill_{fi}_{j}"
            width = 6 - 0.5 * j
            off = (0, 3, -2)[j] * (1 if tag == 0 else -1)         # each joint kinks: a wavy ribbon
            bones.append(Bone(name, parent, (fx, top, fz), [
                box(fx + off, top - ln, fz, width, ln, 3, "frill", faces=["north", "south", "east", "west", "down"], tag=tag * 8 + j)]))
            parent, top = name, top - ln
    # the tendrils: four jointed segments each
    for ti, (tx, tz, lens) in enumerate(TENDRILS):
        parent, top, along = "root", TENDRIL_TOP, 0
        for j, ln in enumerate(lens):
            name = f"t{ti}_{j}"
            kx = KINK[j] * (1 if ti % 2 == 0 else -1)
            kz = KINK[(j + 1) % 4] * (1 if ti % 3 == 0 else -1)
            bones.append(Bone(name, parent, (tx, top, tz), [
                box(tx + kx, top - ln, tz + kz, 2, ln, 2, "tendril", tag=ti * 16 + j, faces=["north", "south", "east", "west", "down"])]))
            parent, top, along = name, top - ln, along + ln
    # the rim: a dark collar and four rows of square flaps, each tilted out from its top edge
    bones.append(Bone("collar", "root", (0, COLLAR_Y, 0), [
        box(0, COLLAR_Y, 0, 46, COLLAR_H, 46, "collar")]))
    sides = {
        "n": ([(x, -RING) for x in (-20, -12, -4, 4, 12, 20)], (-FLAP_TILT, 0, 0), "north"),
        "s": ([(x, RING) for x in (-20, -12, -4, 4, 12, 20)], (FLAP_TILT, 0, 0), "south"),
        "e": ([(RING, z) for z in (-16, -8, 0, 8, 16)], (0, 0, FLAP_TILT), "east"),
        "w": ([(-RING, z) for z in (-16, -8, 0, 8, 16)], (0, 0, -FLAP_TILT), "west"),
    }
    for key, (spots, tilt, _) in sides.items():
        cubes = []
        for k, (fx, fz) in enumerate(spots):
            if key in ("n", "s"):
                size = (FLAP_W, FLAP_H, 1)
            else:
                size = (1, FLAP_H, FLAP_W)
            tag = {"n": 0, "s": 8, "e": 16, "w": 24}[key] + k
            cubes.append(Cube((fx - size[0] / 2, 0, fz - size[2] / 2), size, "flap",
                              pivot=(fx, FLAP_H, fz), rotation=tilt, tag=tag))
        px = 0 if key in ("n", "s") else (RING if key == "e" else -RING)
        pz = 0 if key in ("e", "w") else (-RING if key == "n" else RING)
        bones.append(Bone(f"rim_{key}", "root", (px, FLAP_H, pz), cubes))
    # the bell: the stepped dome, then the glass last
    cubes, y = [], GLASS_Y + GLASS_H
    for i, (w, h) in enumerate(TIERS):
        cubes.append(box(0, y, 0, w, h, w, "tier", faces=["north", "south", "east", "west", "up"], tag=i))
        y += h
    bones.append(Bone("bell", "root", (0, GLASS_Y, 0), cubes))
    bones.append(Bone("glass", "bell", (0, GLASS_Y, 0), [
        box(0, GLASS_Y, 0, 42, GLASS_H, 42, "glass", faces=["north", "south", "east", "west", "up"])]))
    return bones


# ------------------------------------------------------------------ UV layout (per face)
FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def layout(bones):
    rects = []
    for b in bones:
        for c in b.cubes:
            for f in c.faces:
                a, bb = FACE_DIMS[f]
                rects.append([c, f, 0, 0, max(1, int(round(c.size[a]))), max(1, int(round(c.size[bb])))])
    order = sorted(rects, key=lambda r: (-r[5], -r[4]))
    x = y = row = 0
    for r in order:
        if x + r[4] > TEX_W:
            x = 0
            y += row + 1
            row = 0
        r[2], r[3] = x, y
        x += r[4] + 1
        row = max(row, r[5])
    if y + row > TEX_H:
        raise SystemExit(f"UV layout needs {y + row} rows, the texture has {TEX_H}")
    for c, f, x, y, w, h in rects:
        c.uv[f] = {"uv": [x, y], "uv_size": [w, h]}
    return rects


def hash01(*v):
    h = 2166136261
    for x in v:
        h ^= int(round(x * 97)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10007) / 10007.0


def shade(rgb, k):
    return tuple(int(max(0, min(255, c * k))) for c in rgb)


def paint(rects):
    tex = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    glow = np.zeros_like(tex)
    for c, f, x0, y0, w, h in rects:
        for r in range(h):
            for col in range(w):
                colour, alpha, g = texel(c, f, col, r, w, h)
                tex[y0 + r, x0 + col, :3] = colour
                tex[y0 + r, x0 + col, 3] = alpha
                if g is not None:
                    glow[y0 + r, x0 + col, :3] = [int(a * GLOW_K) for a in g]
                    glow[y0 + r, x0 + col, 3] = 255
    return tex, glow


def texel(c, face, col, row, w, h):
    n = hash01(col, row, c.tag, len(face), c.size[0])
    edge = row == 0 or col == 0 or row == h - 1 or col == w - 1
    k = c.kind
    if k == "glass":
        return glass_texel(c, face, col, row, w, h, n, edge)
    if k == "tier":
        return tier_texel(c, face, col, row, w, h, n, edge)
    if k == "flap":
        return flap_texel(c, face, col, row, w, h, n)
    if k == "collar":
        return collar_texel(c, face, col, row, w, h, n, edge)
    if k == "core":
        return core_texel(face, col, row, w, h, n)
    if k == "frill":
        return frill_texel(c, face, col, row, w, h, n)
    return tendril_texel(c, face, col, row, w, h, n)


def glass_texel(c, face, col, row, w, h, n, edge):
    # a violet body you can see into: nearly clear in the middle, brighter on its edges, a few drifting blotches
    base = shade(GLASS, 0.8 + 0.3 * n)
    alpha = int(140 + 45 * n)
    if face == "up":
        alpha += 40
    if edge:
        base, alpha = shade(GLASS, 1.05), 215
    # blotches: soft 2x2 patches, magenta mostly, teal now and then
    bx, by = (col + 3) // 4, (row + 2) // 3
    b = hash01(bx, by, ord(face[0]), 7)
    if b > 0.8 and not edge and (col % 4) in (1, 2) and (row % 3) in (0, 1):
        return (MAGENTA if b < 0.93 else TEAL_DIM), 220, None
    return base, alpha, None


def tier_texel(c, face, col, row, w, h, n, edge):
    base = shade(V_BASE, 0.78 + 0.5 * n)
    if face == "up":
        cx, cy = (w - 1) / 2, (h - 1) / 2
        if c.tag == 5:          # the top step: a magenta cross over a teal fleck at each corner
            if abs(col - cx) < 1.1 or abs(row - cy) < 1.1:
                return (PINK if abs(col - cx) < 0.6 and abs(row - cy) < 0.6 else MAGENTA), 255, MAGENTA
            if col in (1, w - 2) and row in (1, h - 2):
                return TEAL, 255, TEAL
        if c.tag == 4 and (col in (1, w - 2) and row in (1, h - 2)):
            return TEAL, 255, TEAL
        if edge:
            return shade(V_LIGHT, 0.85 + 0.2 * n), 255, None
        return base, 255, None
    # the sides: short teal dashes slanting down across a step, magenta flecks between
    period = 12
    p = (col + c.tag * 3) % period
    if h >= 3 and p in (2, 3) and row in (1, 2):
        return TEAL, 255, TEAL
    if h >= 3 and p == 8 and row == 1:
        return MAGENTA, 255, MAGENTA
    if row == 0:
        return shade(V_LIGHT, 0.8 + 0.3 * n), 255, None
    if row == h - 1:
        return shade(V_DARK, 1.0 + 0.4 * n), 255, None
    return base, 255, None


def flap_texel(c, face, col, row, w, h, n):
    base = shade(V_BASE, 0.7 + 0.45 * n - 0.07 * row)
    big = face in ("north", "south", "east", "west") and w >= 5
    if not big:
        return shade(V_DARK, 1.0 + 0.5 * n), 255, None
    # the marking: two columns of lit squares, as the concept's flaps carry; which squares varies by flap
    t = int(c.tag)
    left = 2 + (t % 2)
    pattern = ((MAGENTA, MAGENTA_DIM), (TEAL, MAGENTA), (MAGENTA_DIM, TEAL))[t % 3]
    rows = (1, 3, 5) if t % 2 == 0 else (2, 4, 6)
    for i, rr in enumerate(rows):
        if row == rr and col in (left, left + 1):
            colour = pattern[(col - left + i) % 2]
            return colour, 255, colour
    if row == 0:
        return shade(V_LIGHT, 0.75 + 0.3 * n), 255, None
    return base, 255, None


def collar_texel(c, face, col, row, w, h, n, edge):
    if face == "up":
        if edge or col in (1, w - 2) or row in (1, h - 2):
            return shade(V_LIGHT, 0.8 + 0.25 * n), 255, None
        return shade(V_BASE, 0.7 + 0.4 * n), 255, None
    if face in ("north", "south", "east", "west"):
        p = col % 6
        if row == 1 and p in (1, 4) and col > 1 and col < w - 2:
            colour = TEAL if (col // 6) % 2 == 0 else MAGENTA
            return colour, 255, colour
        return shade(V_DARK if row == h - 1 else V_BASE, 0.8 + 0.4 * n), 255, None
    return shade(V_DARK, 1.0 + 0.5 * n), 255, None


def core_texel(face, col, row, w, h, n):
    # a lit cube, pink at its edges, with teal squares in its heart (the concept's core)
    cx, cy = (w - 1) / 2, (h - 1) / 2
    d = max(abs(col - cx), abs(row - cy))
    if d < 1.6 and (face in ("north", "south", "east", "west")):
        sq = (int(col - (cx - 1.5)) + int(row - (cy - 1.5))) % 2
        colour = TEAL if sq == 0 else WHITE
        return colour, 255, colour
    if d < 3.2:
        colour = shade(MAGENTA, 1.0 + 0.12 * n)
        return colour, 255, colour
    colour = shade(MAGENTA if d > 4 else MAGENTA_DIM, 0.9 + 0.2 * n)
    return colour, 255, colour


def frill_texel(c, face, col, row, w, h, n):
    # a ribbon of lilac glass: cool violet, a lighter streak down it, a few lit specks
    base = shade((138, 104, 255), 0.8 + 0.3 * n)
    alpha = int(205 + 40 * n)
    if (col + row // 3 + int(c.tag)) % 5 == 0:
        return shade((196, 170, 255), 1.0), 235, None
    if n > 0.93:
        return TEAL_DIM, 240, None
    if n < 0.05:
        return MAGENTA_DIM, 240, None
    if row == h - 1:
        alpha = 150
    return base, alpha, None


def tendril_texel(c, face, col, row, w, h, n):
    ti, j = divmod(int(c.tag), 16)
    along = sum(TENDRILS[ti][2][:j]) + row           # units down the tendril
    # bands of two lit texels every eight units, alternating magenta and teal along the length, brighter toward the tip
    total = sum(TENDRILS[ti][2])
    gap = 13 if along > total * 0.55 else 19         # the upper part is mostly dark; the accents crowd toward the tip
    band, p = divmod(along + ti * 5, gap)
    if p in (0, 1) and along > 12:
        lit = MAGENTA if (band + ti) % 2 == 0 else TEAL
        dim = MAGENTA_DIM if lit == MAGENTA else TEAL_DIM
        colour = lit if (col + p) % 2 == 0 or along > total * 0.55 else dim
        return colour, 255, colour
    if j == 3 and row >= h - 3:
        return MAGENTA, 255, MAGENTA                  # a lit tip
    k = 0.75 + 0.5 * n + 0.25 * (along / total)
    return shade(V_MID if along % 3 else V_BASE, k), 255, None


# ------------------------------------------------------------------ geometry JSON
def r4(v):
    return [round(float(a), 4) for a in v]


def geo(bones):
    out = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = r4(b.pivot)
        if b.rotation:
            d["rotation"] = r4(b.rotation)
        if b.cubes:
            cubes = []
            for c in b.cubes:
                cd = {"origin": r4(c.origin), "size": r4(c.size), "uv": c.uv}
                if c.rotation:
                    cd["pivot"] = r4(c.pivot)
                    cd["rotation"] = r4(c.rotation)
                cubes.append(cd)
            d["cubes"] = cubes
        out.append(d)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.drift_jelly", "texture_width": TEX_W, "texture_height": TEX_H,
                        "visible_bounds_width": 4.5, "visible_bounds_height": 8.5, "visible_bounds_offset": [0, -1.5, 0]},
        "bones": out}]}


# ------------------------------------------------------------------ animations
def kf(times, fn):
    return {f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0": [round(a, 3) for a in fn(t)] for t in times}


def steps(length, n):
    return [length * i / n for i in range(n + 1)]


def anims():
    out = {}
    L = 4.0
    ts = steps(L, 16)
    tau = 2 * math.pi

    def pulse(t):                       # two bell beats in the loop, a soft contraction then a slow release
        return math.sin(tau * 2 * t / L)

    bones = {}
    bones["root"] = {"position": kf(ts, lambda t: (0, 1.6 * math.sin(tau * t / L), 0))}
    bones["bell"] = {"scale": kf(ts, lambda t: (1 + 0.035 * pulse(t), 1 - 0.07 * pulse(t), 1 + 0.035 * pulse(t)))}
    bones["glass"] = {"scale": kf(ts, lambda t: (1, 1, 1))}
    bones["core"] = {"scale": kf(ts, lambda t: (1 + 0.1 * pulse(t + 0.25), 1 + 0.1 * pulse(t + 0.25), 1 + 0.1 * pulse(t + 0.25)))}
    bones["collar"] = {"scale": kf(ts, lambda t: (1 + 0.02 * pulse(t), 1, 1 + 0.02 * pulse(t)))}
    for key, axis in (("n", 0), ("s", 0), ("e", 2), ("w", 2)):
        sign = {"n": -1, "s": 1, "e": 1, "w": -1}[key]

        def flare(t, axis=axis, sign=sign):
            a = sign * 5.0 * -pulse(t + 0.15)       # the flaps swing in as the bell contracts
            return (a, 0, 0) if axis == 0 else (0, 0, a)
        bones[f"rim_{key}"] = {"rotation": kf(ts, flare)}
    for ti, (tx, tz, lens) in enumerate(TENDRILS):
        for j in range(len(lens)):
            amp = (3.5, 6, 8, 10)[j]
            ph = ti * 0.9 + j * 0.8
            bones[f"t{ti}_{j}"] = {"rotation": kf(ts, lambda t, amp=amp, ph=ph, ti=ti: (
                amp * math.sin(tau * t / L - ph) - 2.0 * pulse(t - 0.1) * (1 if j else 0.4),
                0,
                amp * 0.8 * math.sin(tau * t / L - ph * 1.3 + 1.7)))}
    for fi, (_, _, _, lens) in enumerate(FRILLS):
        for j in range(len(lens)):
            amp = (6, 9, 12)[j]
            ph = fi * 2.1 + j * 0.9
            bones[f"frill_{fi}_{j}"] = {"rotation": kf(ts, lambda t, amp=amp, ph=ph: (
                amp * math.sin(tau * t / L - ph), 0, amp * 0.7 * math.sin(tau * t / L - ph * 1.2 + 0.8)))}
    out["idle"] = {"loop": True, "animation_length": L, "bones": bones}

    # hurt: a flinch (the bell squashes, the tendrils flick up and back, the flaps flare)
    H = 0.5
    hb = {
        "root": {"position": {"0.0": [0, 0, 0], "0.08": [0, -2.5, 0], "0.5": [0, 0, 0]}},
        "bell": {"scale": {"0.0": [1, 1, 1], "0.08": [1.1, 0.8, 1.1], "0.22": [0.96, 1.06, 0.96], "0.5": [1, 1, 1]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.08": [1.35, 1.35, 1.35], "0.5": [1, 1, 1]}},
    }
    for key, axis in (("n", 0), ("s", 0), ("e", 2), ("w", 2)):
        sign = {"n": -1, "s": 1, "e": 1, "w": -1}[key]
        a = lambda v, axis=axis, sign=sign: ([sign * v, 0, 0] if axis == 0 else [0, 0, sign * v])  # noqa: E731
        hb[f"rim_{key}"] = {"rotation": {"0.0": a(0), "0.08": a(-20), "0.3": a(6), "0.5": a(0)}}
    for ti, (tx, tz, lens) in enumerate(TENDRILS):
        for j in range(len(lens)):
            ang = (8, 16, 24, 30)[j]
            side = 1 if ti % 2 == 0 else -1
            hb[f"t{ti}_{j}"] = {"rotation": {"0.0": [0, 0, 0], "0.1": [-ang * 0.5, 0, side * ang * 0.4],
                                             "0.3": [ang * 0.4, 0, -side * ang * 0.3], "0.5": [0, 0, 0]}}
    out["hurt"] = {"loop": False, "animation_length": H, "bones": hb}

    # squish: landed on, a firm press and a rebound
    S = 0.55
    sb = {
        "root": {"position": {"0.0": [0, 0, 0], "0.07": [0, -3.5, 0], "0.28": [0, 1.2, 0], "0.55": [0, 0, 0]}},
        "bell": {"scale": {"0.0": [1, 1, 1], "0.07": [1.14, 0.7, 1.14], "0.28": [0.96, 1.1, 0.96], "0.42": [1.02, 0.98, 1.02], "0.55": [1, 1, 1]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.07": [1.4, 1.4, 1.4], "0.4": [1, 1, 1]}},
    }
    for ti, (tx, tz, lens) in enumerate(TENDRILS):
        for j in range(len(lens)):
            ang = (5, 10, 16, 22)[j]
            sb[f"t{ti}_{j}"] = {"rotation": {"0.0": [0, 0, 0], "0.07": [0, 0, 0], "0.3": [-ang * 0.6, 0, 0], "0.55": [0, 0, 0]}}
    out["squish"] = {"loop": False, "animation_length": S, "bones": sb}

    # death: deflates and sinks, the bell sagging wide and flat, the core gutters, the tendrils slump outward
    D = 1.2
    db = {
        "root": {"position": {"0.0": [0, 0, 0], "0.5": [0, -8, 0], "1.2": [0, -18, 0]}},
        "bell": {"scale": {"0.0": [1, 1, 1], "0.25": [1.12, 0.72, 1.12], "1.2": [1.3, 0.32, 1.3]}},
        "core": {"scale": {"0.0": [1, 1, 1], "0.2": [1.4, 1.4, 1.4], "1.2": [0.05, 0.05, 0.05]}},
    }
    for key, axis in (("n", 0), ("s", 0), ("e", 2), ("w", 2)):
        sign = {"n": -1, "s": 1, "e": 1, "w": -1}[key]
        db[f"rim_{key}"] = {"rotation": {"0.0": [0, 0, 0], "1.2": ([sign * -45, 0, 0] if axis == 0 else [0, 0, sign * -45])}}
    for ti, (tx, tz, lens) in enumerate(TENDRILS):
        # outward = away from the middle of the bell: x for a tendril at the side, z for one at the front or back
        ox = 1 if tx > 4 else (-1 if tx < -4 else 0)
        oz = 1 if tz > 4 else (-1 if tz < -4 else 0)
        for j in range(len(lens)):
            ang = (10, 18, 26, 34)[j]
            db[f"t{ti}_{j}"] = {"rotation": {"0.0": [0, 0, 0], "0.6": [-oz * ang * 0.8, 0, -ox * ang * 0.8],
                                             "1.2": [-oz * ang * 1.4, 0, -ox * ang * 1.4]}}
    out["death"] = {"loop": "hold_on_last_frame", "animation_length": D, "bones": db}
    return {"format_version": "1.8.0", "animations": out}


# ------------------------------------------------------------------ items and the effect icon
def blank(n):
    return np.zeros((n, n, 4), np.uint8)


def put(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


def gel_icon(candied):
    """A dollop of gel: raw is a glassy violet blob with a lit speck; candied is amber-violet with sugar on it."""
    img = blank(16)
    body = (150, 110, 238) if not candied else (150, 78, 150)
    deep = (96, 62, 186) if not candied else (96, 44, 108)
    light = (206, 178, 255) if not candied else (236, 150, 190)
    for y in range(16):
        for x in range(16):
            # a blob wider at the bottom, its top a little to the left
            dx, dy = (x + 0.5 - 8.0) / 6.4, (y + 0.5 - 9.4) / 5.3
            wob = 1.0 + 0.12 * math.sin(x * 1.3) - 0.1 * (y < 6)
            r = math.hypot(dx, dy * (1.1 if y < 9 else 1.0))
            if r < 1.0 * wob:
                k = 0.5 * (x + 0.5) / 16 + 0.7 * (y + 0.5) / 16
                col = deep if k > 0.9 else (body if k > 0.55 else light)
                put(img, x, y, col, 235 if not candied else 255)
    # outline in the darkest tone
    for y in range(16):
        for x in range(16):
            if img[y, x, 3] == 0:
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < 16 and 0 <= ny < 16 and img[ny, nx, 3] > 0 and img[ny, nx, :3].tolist() != [22, 14, 62]:
                        put(img, x, y, (46, 28, 104))
                        break
    # a bright glint, and the lit specks
    for (x, y) in ((5, 7), (6, 7), (5, 8)):
        put(img, x, y, WHITE)
    if not candied:
        put(img, 10, 10, TEAL)
        put(img, 8, 12, MAGENTA)
        put(img, 11, 8, MAGENTA_DIM)
    else:
        for (x, y) in ((9, 9), (11, 11), (7, 12), (10, 7), (6, 10), (12, 9)):     # sugar
            put(img, x, y, (255, 245, 252))
        put(img, 8, 11, (255, 220, 236))
    return img


def skim_icon():
    """18x18: a bell skimming right, three streaks of air behind it."""
    img = blank(18)
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - 9, y + 0.5 - 9
            if math.hypot(dx, dy) <= 8.4:
                put(img, x, y, (24, 16, 58))
    # the bell: a half disc, with a lit core
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - 11.2, y + 0.5 - 9.2
            if dy <= 1.2 and math.hypot(dx, dy * 1.25) <= 4.6:
                put(img, x, y, (116, 84, 232) if dy < -2 or abs(dx) > 3 else (150, 110, 244))
    for (x, y) in ((10, 7), (11, 7), (12, 7), (10, 8), (11, 8), (12, 8)):
        put(img, x, y, MAGENTA)
    put(img, 11, 7, PINK)
    for x in range(7, 16):                                # the frill under it
        put(img, x, 10, (96, 66, 196))
    for (x, y) in ((8, 11), (11, 11), (14, 11), (8, 12), (11, 13), (14, 12)):
        put(img, x, y, TEAL_DIM)
    # streaks of air to the left
    for y, x0, x1, c in ((6, 2, 6, TEAL), (9, 1, 5, WHITE), (12, 3, 7, TEAL)):
        for x in range(x0, x1 + 1):
            put(img, x, y, c if x > x0 else shade(c, 0.6))
    # a thin ring
    for y in range(18):
        for x in range(18):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 9)
            if 8.4 < d <= 9.2 and img[y, x, 3] == 0:
                put(img, x, y, (92, 60, 190))
    return img


# ------------------------------------------------------------------ main
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=1) + "\n").encode("utf-8"))
    return path


def main():
    written = []
    bones = build()
    rects = layout(bones)
    written.append(write_json(ASSETS / "geo" / "entity" / "drift_jelly.geo.json", geo(bones)))
    written.append(write_json(ASSETS / "animations" / "entity" / "drift_jelly.animation.json", anims()))
    tex, glow = paint(rects)
    written.append(save_png(tex, TEX / "entity" / "drift_jelly.png"))
    written.append(save_png(glow, TEX / "entity" / "drift_jelly_glowmask.png"))
    written.append(save_png(gel_icon(False), TEX / "item" / "drift_gel.png"))
    written.append(save_png(gel_icon(True), TEX / "item" / "candied_gel.png"))
    written.append(save_png(skim_icon(), TEX / "mob_effect" / "skim.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
