"""The Hollow Heliarch (G9a, GDD 7.3): its GeckoLib model and textures, the monoliths its plates become, the Reliquary,
and the fight's FX textures.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/hollow_heliarch.geo.json              Bedrock geometry 1.12.0, box UV, built at a quarter size (drawn at x4)
    animations/entity/hollow_heliarch.animation.json  one idle loop (every piece is placed by the game each frame)
    textures/entity/hollow_heliarch.png (+ _glowmask)
    textures/block/heliarch_monolith.png             a plate stood on end: 48 x 64, painted across 3 x 4 blocks
    textures/block/heliarch_monolith_inlay.png       its gold inlaid lines alone (an overlay lit full-bright)
    textures/block/heliarch_monolith_edge.png
    textures/block/heliarch_reliquary_{side,front,top,lid,inside}.png
    blockstates/heliarch_monolith.json, heliarch_reliquary.json and their block models
    textures/fx/heliarch_seal.png      the seal over the Breach after the first kill (white, shape in alpha)
    textures/fx/heliarch_corona.png    the eclipse's burning rim (a ring with flame tongues)
    textures/fx/heliarch_rift.png      a void tendril: a torn, tapering rift (white, shape in alpha)
    textures/fx/heliarch_rift_rim.png  its burning edge only
    textures/fx/heliarch_sun.png       a sun glyph (pips, the parry's sunrise)

The silhouette the design asks for: a halo, a core and two hands, readable from anywhere in the arena. The six plates
are sun rays of burnished gold with a white enamel sun at their heart; closed they fold into a shell round the core,
open they stand as a ring behind it. The core is a white-gold sun. The hands are dark bronze gauntlets banded in
gold, so they read dark against the Sanctum's ivory floor and bright gold never sits on the floor's own colours.

Model space as gen_colossus.py: Bedrock units (16 a block as built; drawn at x4, so a unit is a quarter of a block),
y up, the front is -Z. Every piece is a free bone round the origin that the game moves (HeliarchModel): plates with
their ray along +Y and their face toward -Z; hands palm down (-Y), fingers toward -Z, the cuff toward +Z.

Run:  python tools/art/gen_heliarch.py
"""
from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass, field

import numpy as np

from blockart import fbm, rng_for
from common import ASSETS, TEX, rel, rgb, save_png, supersample, white_alpha

# ------------------------------------------------------------------ palette
GOLD = [rgb("#fff6d2"), rgb("#ffe08a"), rgb("#f2bd4a"), rgb("#d19232"), rgb("#a46a22"), rgb("#6e4416")]  # light .. dark
ENAMEL = [rgb("#ffffff"), rgb("#fbf4e2"), rgb("#efe0bb"), rgb("#d8c392")]
BRONZE = [rgb("#6b5436"), rgb("#4d3b27"), rgb("#382a1c"), rgb("#261c13"), rgb("#171009")]
# the gauntlets: a warm dark bronze, light enough to keep its shape against the night sky (not a black blob)
GAUNT = [rgb("#a2804e"), rgb("#826540"), rgb("#664e33"), rgb("#4e3b28"), rgb("#3a2b1d")]
# the monoliths: umbral stone, cool and dark, the gold on it only inlaid lines (G9p: no grain that reads as wood)
STONE = [rgb("#7a7488"), rgb("#625d6e"), rgb("#4d4958"), rgb("#3b3845"), rgb("#2b2933")]
INLAY = rgb("#ffcf5a")
EMBER = [rgb("#fff4c0"), rgb("#ffcf5a"), rgb("#ff9a2a"), rgb("#e0601a")]
CORE = [rgb("#ffffff"), rgb("#fff9e6"), rgb("#ffe9a8"), rgb("#ffc860")]
WHITE = (255, 255, 255)

UNIT = 4.0  # model units a block in game (built at a quarter size, drawn at x4)


@dataclass
class Cube:
    origin: tuple
    size: tuple
    paint: str
    rotation: tuple | None = None
    pivot: tuple | None = None
    uv: tuple | None = None
    inflate: float = 0.0

    @property
    def dims(self):
        return tuple(int(math.floor(s)) for s in self.size)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple | None = None
    cubes: list = field(default_factory=list)


def box(cx, cy, cz, w, h, d, paint, **kw):
    """A cube by its middle."""
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), paint, **kw)


# ------------------------------------------------------------------ geometry
# Shared with the game (HeliarchModel, HeliarchPose): a plate is 20 units (2.5 blocks) across, its ray 26 long from its
# inner lip to its point; a hand's palm middle is its origin; a finger's knuckle sits 7 units ahead of it.
PLATE_W = 20.0
PALM_H = 8.5
KNUCKLE_Z = -7.0
FINGER_X = (-5.7, -1.9, 1.9, 5.7)


def plate_cubes() -> list[Cube]:
    """A blade of the crown: a broad slab with a sun painted on its face, a bright spine down the middle from the lip
    to the point, a diamond point, gilt edges, and an inner lip where the core's fire shows through."""
    return [
        box(0, 0, 0, PLATE_W, 18, 3, "plate"),
        box(0, 10.5, 0, 10, 10, 2.4, "plate_tip", rotation=(0, 0, 45), pivot=(0, 10.5, 0)),
        box(0, 2.5, -1.9, 2.4, 23, 1.0, "plate_spine"),
        box(0, -10.2, 0.3, 14, 2.6, 2.4, "plate_lip"),
        box(-9.2, 0, -1.9, 1.8, 17, 1.0, "plate_rim"),
        box(9.2, 0, -1.9, 1.8, 17, 1.0, "plate_rim"),
        box(0, 0, 1.9, 10, 14, 1.0, "plate_rib"),
    ]


def core_cubes() -> list[Cube]:
    """A sun: three crossed boxes and a turned one, so it is round from any side."""
    return [
        box(0, 0, 0, 18, 12, 12, "core"),
        box(0, 0, 0, 12, 18, 12, "core"),
        box(0, 0, 0, 12, 12, 18, "core"),
        box(0, 0, 0, 14, 14, 14, "core_hot", rotation=(0, 45, 0), pivot=(0, 0, 0)),
    ]


def hand_bones(side: str) -> list[Bone]:
    """A gauntleted hand. In the game's (mirrored) space the right hand's thumb is toward -X; Bedrock mirrors X, so
    it is written at +X here for the right hand. A thick palm whose back is armoured in four ridged plates running to a
    knuckle over each finger, fingers that taper to gold points, a big thumb; the cuff plain dark bronze (a gold band
    round it and a seam of fire on the back read, from behind a fist, as a chest's lid band and latch; two rivets over
    a wide seam read as a face)."""
    s = 1.0 if side == "right" else -1.0
    top = PALM_H / 2.0
    cubes = [
        box(0, 0.5, 10.5, 13, 10, 9, "gauntlet"),                  # the cuff
        box(0, 0.0, -0.5, 16, PALM_H, 13, "gauntlet"),             # the palm
    ]
    for fx in FINGER_X:
        cubes.append(box(fx, top + 0.5, 1.2, 2.2, 1.0, 9.0, "gauntlet"))   # a ridged plate down the back of the hand
        cubes.append(box(fx, top + 1.2, -5.8, 3.4, 2.0, 3.2, "gauntlet"))  # its knuckle
    hand = Bone(f"hand_{side}", "root", (0.0, 0.0, 0.0), None, cubes)
    bones = [hand]
    for i, fx in enumerate(FINGER_X):
        a = Bone(f"finger_{side}_{i}", f"hand_{side}", (fx, 0.5, KNUCKLE_Z), None, [
            box(fx, 0.5, KNUCKLE_Z - 3.2, 3.6, 5.0, 6.4, "gauntlet"),
        ])
        b = Bone(f"finger_{side}_{i}_tip", f"finger_{side}_{i}", (fx, 0.5, KNUCKLE_Z - 6.4), None, [
            box(fx, 0.45, KNUCKLE_Z - 6.6, 3.7, 4.9, 1.0, "gauntlet_gold"),  # a gold ring at the joint
            box(fx, 0.4, KNUCKLE_Z - 8.8, 3.0, 4.1, 4.8, "gauntlet"),
            box(fx, 0.2, KNUCKLE_Z - 11.8, 2.2, 3.1, 1.8, "gauntlet_gold"),
            box(fx, 0.1, KNUCKLE_Z - 13.3, 1.2, 1.8, 1.4, "gauntlet_gold"),  # the point
        ])
        bones += [a, b]
    thumb = Bone(f"thumb_{side}", f"hand_{side}", (s * 7.0, -0.5, -1.0), (0, s * 34.0, 0), [
        box(s * 9.6, -0.5, -4.2, 5.2, 5.4, 9.0, "gauntlet"),
        box(s * 9.6, -0.6, -9.2, 4.0, 4.2, 1.8, "gauntlet_gold"),
        box(s * 9.6, -0.7, -10.7, 2.2, 2.4, 1.4, "gauntlet_gold"),
    ])
    bones.append(thumb)
    return bones


def build_heliarch() -> list[Bone]:
    bones = [Bone("root", None, (0.0, 0.0, 0.0)), Bone("core", "root", (0.0, 0.0, 0.0), None, core_cubes())]
    for k in range(6):
        bones.append(Bone(f"plate_{k}", "root", (0.0, 0.0, 0.0), None, plate_cubes()))
    bones += hand_bones("right")
    bones += hand_bones("left")
    return bones


# ------------------------------------------------------------------ UVs (shared between identical pieces)
def face_rects(c: Cube):
    w, h, d = c.dims
    u, v = c.uv
    return {
        "xneg": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "xpos": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
    }


def texel_point(c: Cube, face: str, col: int, row: int):
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    cc, rr = col + 0.5, row + 0.5
    return {
        "xneg": (x0, y1 - rr, z1 - cc),
        "front": (x0 + cc, y1 - rr, z0),
        "xpos": (x1, y1 - rr, z0 + cc),
        "back": (x1 - cc, y1 - rr, z1),
        "top": (x0 + cc, y1, z1 - rr),
        "bottom": (x0 + cc, y0, z1 - rr),
    }[face]


def pack_uvs(bones: list[Bone], tex_w: int, tex_h: int):
    """Packs each distinct cube once: the six plates share one plate's layout, the two hands one hand's (a cube's
    key is its paint, size and its place inside its piece)."""
    seen = {}
    unique = []
    for b in bones:
        base = re.sub(r"_(right|left)", "", re.sub(r"plate_\d", "plate", b.name))
        for i, c in enumerate(b.cubes):
            key = (base, i)
            if key in seen:
                c.uv = None
                seen[key].append(c)
            else:
                seen[key] = [c]
                unique.append(c)
    order = sorted(unique, key=lambda c: (-(c.dims[2] + c.dims[1]), -(2 * (c.dims[0] + c.dims[2]))))
    x = y = row_h = 0
    for c in order:
        w, h, d = c.dims
        fw, fh = 2 * (d + w), d + h
        if x + fw > tex_w:
            x = 0
            y += row_h + 1
            row_h = 0
        c.uv = (x, y)
        x += fw + 1
        row_h = max(row_h, fh)
    if y + row_h > tex_h:
        raise SystemExit(f"UV layout needs {y + row_h} rows, texture has {tex_h}")
    for group in seen.values():
        for c in group[1:]:
            c.uv = group[0].uv
    return [g[0] for g in seen.values()]


# ------------------------------------------------------------------ painting
FACE_TONE = {"top": 0, "front": 1, "xpos": 1, "xneg": 2, "back": 2, "bottom": 3}


def hash01(*vals) -> float:
    h = 2166136261
    for v in vals:
        h ^= int(round(v * 8)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10000) / 10000.0


def texel(c: Cube, face: str, col: int, row: int, w: int, h: int):
    """(colour, added light or None) of one texel. The glowmask is light added over the lit model, full bright."""
    kind = c.paint
    tone = FACE_TONE[face]
    u = (col + 0.5) / max(1, w)
    v = (row + 0.5) / max(1, h)
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    p = texel_point(c, face, col, row)
    n = hash01(*p)
    if kind == "core_hot":
        return WHITE, WHITE
    if kind == "core":
        r = math.hypot(u - 0.5, v - 0.5)
        idx = 0 if r < 0.28 else 1 if r < 0.42 else 2
        swirl = math.sin(p[0] * 0.7 + p[1] * 0.5) + math.sin(p[2] * 0.6 - p[1] * 0.8)
        if swirl > 1.2:
            idx = min(3, idx + 1)
        col_ = CORE[idx]
        return col_, (255, 250, 230) if idx < 2 else (255, 214, 120)
    if kind == "plate" and face == "front":
        # the face: a sun painted in white enamel and bright gold in the middle, its rays out to a bevelled border
        x, y = u - 0.5, (v - 0.44) * (h / w)
        r = math.hypot(x, y)
        a = math.atan2(y, x)
        if col == 0 or col == w - 1 or row == 0 or row == h - 1:
            return GOLD[4], None
        if r < 0.16:
            return ENAMEL[0], (255, 236, 170)
        if r < 0.23:
            return GOLD[0], (170, 120, 40)
        if r < 0.44 and abs(math.sin(4 * a)) > 0.9:
            return GOLD[1], (70, 50, 16)
        return GOLD[2 + (1 if v > 0.8 else 0)], None
    if kind == "plate_spine":
        idx = 0 if face in ("front", "top") else 1
        return GOLD[idx], (110, 84, 30) if face == "front" else None
    if kind in ("plate", "plate_tip"):
        if face == "back" and kind == "plate":
            # the back: dark bronze-gold, smooth, a bevelled border (ribs of shadow read as crate slats)
            if edge:
                return GOLD[3], None
            return GOLD[4 if v < 0.8 else 5], None
        if face in ("xneg", "xpos", "top", "bottom") and kind == "plate":
            # the plate's edges: shaded metal, a lit bevel on the face side (a flat bright edge read as unlit)
            idx = 3 + (1 if face in ("xneg", "bottom") else 0)
            if col == 0:
                idx = 1
            return GOLD[idx], None
        if kind == "plate_tip" and face != "front":
            # the point's sides: shaded metal (edge-on in flight they showed as flat bright blocks)
            return GOLD[3 if face in ("top", "xpos") else 4], None
        # burnished gold: light at the top-left, smooth, bright bevelled edges
        idx = tone + (1 if v > 0.75 else 0)
        if edge and face != "bottom":
            idx = max(0, idx - 2)
        idx = max(0, min(5, idx))
        glow = None
        if kind == "plate_tip" and face == "front" and (u - 0.5) ** 2 + (v - 0.5) ** 2 < 0.05:
            glow = (90, 70, 30)
        return GOLD[idx], glow
    if kind == "plate_rim":
        idx = 0 if face in ("front", "top") else 3
        return GOLD[idx], (70, 55, 20) if face == "front" else None
    if kind == "plate_lip":
        # the inner edge, toward the core: where the fire shows through, a warm orange (a strong glow over it made a
        # flat lemon-yellow slab of it when a plate flies)
        if face in ("bottom", "front", "back"):
            return EMBER[3], (110, 44, 8) if face != "bottom" else (70, 28, 6)
        return BRONZE[1], None
    if kind == "plate_sun":
        if face == "front":
            # a white enamel sun: a disc and eight short rays in gold, the disc's middle white-hot
            x, y = u - 0.5, v - 0.5
            r = math.hypot(x, y)
            a = math.atan2(y, x)
            ray = abs(math.sin(4 * a)) > 0.86 and 0.3 < r < 0.5
            if r < 0.2:
                return ENAMEL[0], (255, 244, 200)
            if r < 0.3:
                return GOLD[1], (200, 150, 60)
            if ray:
                return GOLD[1], (160, 120, 40)
            return ENAMEL[2], (40, 34, 20)
        return GOLD[2], None
    if kind == "plate_rib":
        return (GOLD[3] if edge else GOLD[4]), None
    if kind == "gauntlet":
        # dark bronze metal: smooth (speckles read as wood), lighter toward the top of each face, a lit bevel on the
        # upper edge; no rivets (two gold dots over the seam read as eyes over a mouth)
        idx = min(4, tone + (1 if v > 0.6 else 0))
        if row == 0 and face in ("top", "front", "xneg", "xpos", "back"):
            idx = max(0, idx - 1)
        return GAUNT[idx], None
    if kind == "gauntlet_gold":
        idx = 1 + tone + (1 if edge else 0)
        return GOLD[min(5, idx)], (60, 46, 16)
    if kind == "gauntlet_ember":
        # a seam where the fire inside shows: orange, not white (white read as a window)
        return EMBER[2 + (1 if edge else 0)], (150, 60, 12)
    return (255, 0, 255), None


def paint(bones, tex_w, tex_h, unique):
    tex = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    glow = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    for c in unique:
        for face, (ru, rv, rw, rh) in face_rects(c).items():
            for r in range(rh):
                for col in range(rw):
                    colour, g = texel(c, face, col, r, rw, rh)
                    tex[rv + r, ru + col, :3] = colour
                    tex[rv + r, ru + col, 3] = 255
                    if g is not None:
                        glow[rv + r, ru + col, :3] = g
                        glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ------------------------------------------------------------------ geo and animation json
def r4(v):
    return [round(float(a), 4) for a in v]


def geo_json(bones, name, tex_w, tex_h, bounds_w, bounds_h):
    out = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = r4(b.pivot)
        if b.rotation and any(b.rotation):
            d["rotation"] = r4(b.rotation)
        if b.cubes:
            cl = []
            for c in b.cubes:
                cd = {"origin": r4(c.origin), "size": r4(c.size), "uv": list(c.uv)}
                if c.inflate:
                    cd["inflate"] = c.inflate
                if c.rotation and any(c.rotation):
                    cd["pivot"] = r4(c.pivot)
                    cd["rotation"] = r4(c.rotation)
                cl.append(cd)
            d["cubes"] = cl
        out.append(d)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": tex_w, "texture_height": tex_h,
                        "visible_bounds_width": bounds_w, "visible_bounds_height": bounds_h,
                        "visible_bounds_offset": [0, bounds_h / 2, 0]},
        "bones": out}]}


def animations():
    # one quiet loop: the core breathes (the game places every other piece each frame)
    return {"format_version": "1.8.0", "animations": {"idle": {"loop": True, "animation_length": 2.4, "bones": {
        "core": {"scale": {"0.0": [1, 1, 1], "1.2": [1.05, 1.05, 1.05], "2.4": [1, 1, 1]}}}}}}


NUMBER_LIST = re.compile(r"\[\s*(-?[\d.eE+-]+(?:,\s*-?[\d.eE+-]+)*)\s*\]")


def pretty_json(data) -> str:
    text = json.dumps(data, indent=2)
    return NUMBER_LIST.sub(lambda m: "[" + ", ".join(v.strip() for v in m.group(1).split(",")) + "]", text) + "\n"


def write_text(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8"))
    return path


# ------------------------------------------------------------------ the monolith (a plate stood on end, 3 x 4 blocks)
def monolith_inlay(x: int, y: int, w: int, h: int) -> bool:
    """True where the monolith's face has a gold inlaid line: a double border, a sun in outline high on it."""
    if x in (2, w - 3) and 2 <= y <= h - 3 or y in (2, h - 3) and 2 <= x <= w - 3:
        return True
    if x in (5, w - 6) and 5 <= y <= h - 6 or y in (5, h - 6) and 5 <= x <= w - 6:
        return True
    # the sun low on the face, clear of the pips over it (right under the middle pip, it read as a figure under a head)
    cx, cy = 24, 39
    r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
    a = math.degrees(math.atan2(y + 0.5 - cy, x + 0.5 - cx))
    if r < 2.6 or 5.2 <= r < 6.4:
        return True
    # sixteen even rays, tapering: dense enough to read as a sun, never as limbs
    off = abs((a + 11.25) % 22.5 - 11.25)
    return 7.2 <= r < 11.8 and math.radians(off) * r < 0.85 - 0.35 * (r - 7.2) / 4.6


def monolith_texture(inlay_only=False):
    """48 x 64: dark umbral stone, mottled (never streaked: grain read as a wooden wardrobe), with gold only as inlaid
    lines, a double border and a sun in outline high on it, and the Hollow's violet cracks creeping up from the floor.
    inlay_only: just the gold lines, for the overlay the game lights full-bright."""
    w, h = 48, 64
    rng = rng_for("heliarch_monolith")
    noise = fbm(rng, size=64)[:h, :w]
    img = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            if monolith_inlay(x, y, w, h):
                img[y, x, :3] = INLAY
                img[y, x, 3] = 255
                continue
            if inlay_only:
                continue
            m = noise[y, x]
            idx = 2 if m > 0.55 else 3 if m > 0.35 else 4 if m > 0.2 else 2
            if (x * 7 + y * 13) % 29 == 0:
                idx = 1
            if x < 2 or x >= w - 2 or y < 2 or y >= h - 2:
                idx = 1
            img[y, x, :3] = STONE[idx]
            img[y, x, 3] = 255
    if inlay_only:
        return img
    # violet cracks rising from the floor
    for start in (7, 22, 39):
        x = start
        for y in range(h - 1, 30, -1):
            if 1 < x < w - 2 and not monolith_inlay(x, y, w, h):
                img[y, x, :3] = rgb("#3c1a5c") if y % 3 else rgb("#6a2fa0")
            if rng.random() < 0.45:
                x += int(rng.choice([-1, 1]))
    return img


def monolith_edge():
    """The slab's sides: the same stone, one gold inlaid line down the middle."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            idx = 2 if (x * 5 + y * 3) % 11 else 3
            img[y, x, :3] = INLAY if x in (7, 8) else STONE[idx]
            img[y, x, 3] = 255
    return img


def monolith_models():
    """One model per cell: the slab runs along X; its north and south faces show the cell's part of the painting (both
    read the right way round from their own side), the rest are the bronze edge."""
    written = []
    models = ASSETS / "models" / "block"
    for col in range(3):
        for row in range(4):
            v0 = (3 - row) * 4.0
            north = [round((2 - col) * 16 / 3, 4), v0, round((3 - col) * 16 / 3, 4), v0 + 4.0]
            south = [round(col * 16 / 3, 4), v0, round((col + 1) * 16 / 3, 4), v0 + 4.0]
            faces = {
                "north": {"uv": north, "texture": "#face", "cullface": "north"},
                "south": {"uv": south, "texture": "#face", "cullface": "south"},
            }
            for f in ("east", "west", "up", "down"):
                faces[f] = {"uv": [0, 0, 16, 16], "texture": "#edge", "cullface": f}
            # the gold inlay again over the face, lit full-bright: it glows in the eclipse's dark
            inlay = {"north": {"uv": north, "texture": "#inlay", "cullface": "north"},
                     "south": {"uv": south, "texture": "#inlay", "cullface": "south"}}
            model = {"parent": "minecraft:block/block",
                     "render_type": "minecraft:cutout",
                     "textures": {"face": "cosmicbreach:block/heliarch_monolith", "edge": "cosmicbreach:block/heliarch_monolith_edge",
                                  "inlay": "cosmicbreach:block/heliarch_monolith_inlay",
                                  "particle": "cosmicbreach:block/heliarch_monolith_edge"},
                     "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
                                  {"from": [0, 0, 0], "to": [16, 16, 16], "faces": inlay, "shade": False,
                                   "neoforge_data": {"block_light": 15, "sky_light": 15}}]}
            written.append(write_json(models / f"heliarch_monolith_{col}_{row}.json", model))
    variants = {}
    for axis, y in (("x", 0), ("z", 90)):
        for col in range(3):
            for row in range(4):
                v = {"model": f"cosmicbreach:block/heliarch_monolith_{col}_{row}"}
                if y:
                    v["y"] = y
                variants[f"axis={axis},column={col},row={row}"] = v
    written.append(write_json(ASSETS / "blockstates" / "heliarch_monolith.json", {"variants": variants}))
    return written


# ------------------------------------------------------------------ the Reliquary
LAPIS = [rgb("#3b4fa8"), rgb("#2c3a82"), rgb("#1f2a62")]


def reliquary_textures():
    """Gilded casket panels: deep lapis enamel inset in gold, a white enamel sun on the front; the lid's top a gold dome
    of rays round a white sun; the posts and feet plain gold."""
    out = {}
    for name in ("side", "front", "top", "lid", "inside", "gold"):
        img = np.zeros((16, 16, 4), dtype=np.uint8)
        for y in range(16):
            for x in range(16):
                frame = x < 2 or x > 13 or y < 2 or y > 13
                colour = GOLD[2 if (x * 3 + y) % 7 else 3] if frame else LAPIS[1 if (x + y) % 3 else 2]
                if name == "gold":
                    colour = GOLD[1 + (1 if y > 10 else 0) + (1 if x in (0, 15) else 0)]
                if name == "inside":
                    colour = EMBER[1] if not frame else GOLD[3]
                if name in ("top", "lid") and not frame:
                    a = math.atan2(y - 7.5, x - 7.5)
                    colour = GOLD[1] if abs(math.sin(6 * a)) > 0.6 else GOLD[3]
                    if math.hypot(x - 7.5, y - 7.5) < 2.5:
                        colour = ENAMEL[0]
                if name == "front" and not frame:
                    r = math.hypot(x - 7.5, y - 7.5)
                    a = math.degrees(math.atan2(y - 7.5, x - 7.5))
                    off = abs((a + 22.5) % 45.0 - 22.5)
                    if r < 2.0:
                        colour = ENAMEL[0]
                    elif r < 3.0:
                        colour = GOLD[0]
                    elif r < 5.6 and math.radians(off) * r < 0.6:
                        colour = GOLD[1]
                if name != "gold" and (y in (0, 15) or x in (0, 15)):
                    colour = GOLD[4]
                img[y, x, :3] = colour
                img[y, x, 3] = 255
        out[name] = img
    return out


def reliquary_models():
    written = []
    models = ASSETS / "models" / "block"

    def el(f, t, tex, rot=None):
        e = {"from": f, "to": t, "faces": {}}
        for face in ("north", "south", "east", "west", "up", "down"):
            key = tex.get(face, tex.get("all"))
            e["faces"][face] = {"texture": key}
        if rot:
            e["rotation"] = rot
        return e

    gold = {"all": "#gold"}
    feet = [el([x, 0, z], [x + 3, 2, z + 3], gold) for x, z in ((1.5, 2.5), (11.5, 2.5), (1.5, 10.5), (11.5, 10.5))]
    plinth = el([2, 2, 3], [14, 3, 13], gold)
    body = el([2.5, 3, 3.5], [13.5, 10, 12.5], {"all": "#side", "north": "#front", "up": "#inside", "down": "#gold"})
    posts = [el([x, 3, z], [x + 2, 10.5, z + 2], gold) for x, z in ((2, 3), (12, 3), (2, 11), (12, 11))]
    lid_parts = [
        ([1.5, 10.5, 2.5], [14.5, 12, 13.5], {"all": "#gold", "up": "#lid"}),
        ([3.5, 12, 4.5], [12.5, 13.5, 11.5], {"all": "#gold", "up": "#lid"}),
        ([6.5, 13.5, 6.5], [9.5, 15.5, 9.5], {"all": "#top"}),
    ]
    closed_lid = [el(f, t, tex) for f, t, tex in lid_parts]
    open_lid = [el(f, t, tex, rot={"angle": -45, "axis": "x", "origin": [8, 10.5, 13.5]}) for f, t, tex in lid_parts]
    tex = {"side": "cosmicbreach:block/heliarch_reliquary_side", "front": "cosmicbreach:block/heliarch_reliquary_front",
           "top": "cosmicbreach:block/heliarch_reliquary_top", "lid": "cosmicbreach:block/heliarch_reliquary_lid",
           "inside": "cosmicbreach:block/heliarch_reliquary_inside", "gold": "cosmicbreach:block/heliarch_reliquary_gold",
           "particle": "cosmicbreach:block/heliarch_reliquary_gold"}
    base = feet + [plinth, body] + posts
    written.append(write_json(models / "heliarch_reliquary.json", {"parent": "minecraft:block/block", "textures": tex,
                                                                  "elements": base + closed_lid}))
    written.append(write_json(models / "heliarch_reliquary_open.json", {"parent": "minecraft:block/block", "textures": tex,
                                                                       "elements": base + open_lid}))
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for opened in (False, True):
            v = {"model": "cosmicbreach:block/heliarch_reliquary" + ("_open" if opened else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},open={'true' if opened else 'false'}"] = v
    written.append(write_json(ASSETS / "blockstates" / "heliarch_reliquary.json", {"variants": variants}))
    return written


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))
    return path


# ------------------------------------------------------------------ FX textures (white, shape in alpha)
def seal_texture(size=256):
    """The seal: rings of light round a sun, eight rays to the Sanctum's eight segments, a ring of dots and arcs (no
    letters, no faces), soft at every edge so it glows rather than sits."""
    c = size / 2.0

    def fn(xs, ys):
        x = (xs - c) / c
        y = (ys - c) / c
        r = np.hypot(x, y)
        a = np.arctan2(y, x)
        ring = lambda r0, w: np.exp(-((r - r0) / w) ** 2)
        out = 0.95 * ring(0.93, 0.018) + 0.7 * ring(0.86, 0.01) + 0.55 * ring(0.5, 0.012) + 0.8 * ring(0.3, 0.02)
        # eight long rays between the rings
        rays = np.exp(-((np.abs(np.sin(4 * a)) * r) / 0.012) ** 2) * ((r > 0.32) & (r < 0.84))
        out += 0.6 * rays * (1 - np.abs(r - 0.58) / 0.3)
        # dots round the outer band, arcs between them
        dots = np.exp(-(((np.mod(a * 12 / np.pi + 0.5, 1.0) - 0.5) * 12 * np.pi / 24 * 0.68) ** 2 + (r - 0.72) ** 2) / 0.0006)
        out += 0.9 * dots
        arcs = ring(0.72, 0.008) * (np.abs(np.sin(12 * a)) > 0.55)
        out += 0.5 * arcs
        # the sun in the middle
        out += np.clip(1.0 - r / 0.18, 0, 1) ** 1.5
        return np.clip(out, 0, 1) * (r < 0.99)

    return white_alpha(supersample(fn, size, size, 3))


def corona_texture(size=128):
    """The eclipse's rim: a thin bright ring with flame tongues outward, fading softly."""
    c = size / 2.0

    def fn(xs, ys):
        x = (xs - c) / c
        y = (ys - c) / c
        r = np.hypot(x, y)
        a = np.arctan2(y, x)
        tongues = 0.5 + 0.5 * np.sin(9 * a) * np.sin(5 * a + 1.3) * np.sin(3 * a - 0.7)
        rim = np.exp(-((r - 0.62) / 0.035) ** 2)
        flame = np.clip((0.62 + 0.3 * tongues - r) / (0.3 * tongues + 1e-3), 0, 1) * (r > 0.62) * 0.8
        halo = np.clip(1 - (r - 0.62) / 0.36, 0, 1) ** 2 * 0.35 * (r > 0.6)
        return np.clip(rim + flame * tongues + halo, 0, 1)

    return white_alpha(supersample(fn, size, size, 3))


def rift_texture(w=64, h=128, rim=False):
    """A torn rift of void, standing: broad at its foot, tapering to a ragged point, its edges jagged (a tear, not a
    rope). rim=True gives only its burning edge."""
    rng = np.random.default_rng(907)
    jag = rng.random(64)

    def fn(xs, ys):
        v = 1.0 - ys / h                       # 0 at the foot, 1 at the tip
        half = 0.46 * (1.0 - v) ** 0.8 + 0.02
        k = np.clip((v * 63).astype(int), 0, 63)
        wobble = 0.06 * (jag[k] - 0.5) + 0.03 * np.sin(v * 31.0)
        x = (xs / w - 0.5) + wobble * (1 - v)
        inside = np.abs(x) < half
        if not rim:
            return inside.astype(float)
        edge = np.clip(1 - (half - np.abs(x)) / 0.07, 0, 1) * inside
        return edge

    return white_alpha(supersample(fn, w, h, 3))


def sun_texture(size=64):
    c = size / 2.0

    def fn(xs, ys):
        x = (xs - c) / c
        y = (ys - c) / c
        r = np.hypot(x, y)
        a = np.arctan2(y, x)
        disc = np.clip((0.34 - r) / 0.04, 0, 1)
        rays = np.clip((0.95 - r) / 0.2, 0, 1) * (np.abs(np.sin(4 * a)) > 0.92) * (r > 0.36)
        glow = np.clip(1 - r, 0, 1) ** 3 * 0.5
        return np.clip(disc + rays + glow, 0, 1)

    return white_alpha(supersample(fn, size, size, 3))


# ------------------------------------------------------------------ main
def main():
    written = []
    bones = build_heliarch()
    unique = pack_uvs(bones, 256, 128)
    geo = geo_json(bones, "hollow_heliarch", 256, 128, 16, 12)
    written.append(write_text(ASSETS / "geo" / "entity" / "hollow_heliarch.geo.json", pretty_json(geo)))
    written.append(write_text(ASSETS / "animations" / "entity" / "hollow_heliarch.animation.json", pretty_json(animations())))
    tex, glow = paint(bones, 256, 128, unique)
    written.append(save_png(tex, TEX / "entity" / "hollow_heliarch.png"))
    written.append(save_png(glow, TEX / "entity" / "hollow_heliarch_glowmask.png"))
    blk = TEX / "block"
    written.append(save_png(monolith_texture(), blk / "heliarch_monolith.png"))
    written.append(save_png(monolith_texture(inlay_only=True), blk / "heliarch_monolith_inlay.png"))
    written.append(save_png(monolith_edge(), blk / "heliarch_monolith_edge.png"))
    for name, img in reliquary_textures().items():
        written.append(save_png(img, blk / f"heliarch_reliquary_{name}.png"))
    written += monolith_models()
    written += reliquary_models()
    fx = TEX / "fx"
    written.append(save_png(seal_texture(), fx / "heliarch_seal.png"))
    written.append(save_png(corona_texture(), fx / "heliarch_corona.png"))
    written.append(save_png(rift_texture(), fx / "heliarch_rift.png"))
    written.append(save_png(rift_texture(rim=True), fx / "heliarch_rift_rim.png"))
    written.append(save_png(sun_texture(), fx / "heliarch_sun.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
