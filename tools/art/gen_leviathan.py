"""The Thalassine Leviathan, its Rift's bell and the Halo of Nine, for GeckoLib 4 and the block models.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/thalassine_leviathan.geo.json                Bedrock geometry 1.12.0, box UV, built at half size
    animations/entity/thalassine_leviathan.animation.json   swim, sleep, moor (the plates lift), moored
    textures/entity/thalassine_leviathan.png (+ _glowmask)            awake: dark blue hide, silver bands, cyan spots
    textures/entity/thalassine_leviathan_dormant.png (+ _glowmask)    asleep: duller, the spots faint
    textures/entity/thalassine_leviathan_glands_glowmask.png          the four song glands (the game sets their strength)
    textures/entity/thalassine_leviathan_fan_glowmask.png             the tail's fan, gold (through a flick's telegraph)
    textures/block/rift_bell.png, rift_bell_frame.png, singing_rimeglass.png; models and states for rift_bell,
        leviathan_coil, rift_bridge, singing_rimeglass
    textures/item/halo_of_nine.png and its model

The body is six bones the game places one by one along the path the head swam (LeviathanModel): head, seg1 to seg4
and tail, each with its pivot at its middle and facing -Z. Built at half size (the game draws it x2), so a unit is an
eighth of a block: the head is 8 blocks long and 5 wide, the body tapers along each segment from 4.5 blocks behind
the head to 1 at the tail stock, the fan's flukes are 9 blocks across, about 40 blocks nose to fluke tip with the body's
spacing (6 blocks, LeviathanMoves.FOLLOW). The middle sizes are the hit parts' (LeviathanMoves.PART_WIDTH, PART_HEIGHT).

Shape and colour (Thalassine Leviathan design v1, "The Leviathan", "Look and sound"; art review G5p): a baleen whale's
blunt, broad head with a wide gape edged by a pale lip, a comb of pale crystal baleen in it at the sides and across
the front, a broad lower jaw with a pleated pale throat, small eyes over the corners of the mouth, and long pale-edged
flippers sweeping back and down; a body round in section and countershaded, a mid blue back well clear of the
charcoal rock and a pale silver-blue belly meeting it along a wavy line, one row of cyan photophores along each
flank; a single arrow-headed scute on the spine over each of the four warm white-gold song glands (it lifts on its
front hinge in a Moorage); horizontal whale flukes.

Run:  python tools/art/gen_leviathan.py   (then preview_leviathan.py)
"""
from __future__ import annotations

import json
import math

import numpy as np

from common import ASSETS, TEX, rel, rgb, save_png
from gen_colossus import Bone, Cube, face_rects, geo_json, hash01, kf, loop_times, pack_uvs, pretty_json, texel_point, write_json, write_text

TEX_W, TEX_H = 512, 512

# ------------------------------------------------------------------ palette
# A mid blue whale, countershaded: a darker back, mid blue flanks, a pale silver-blue belly (well clear of the charcoal
# Driftstone it coils round, 30 to 37 of 255 in the game's light).
HIDE_TOP = rgb("#3a67a6")
HIDE = [rgb("#5e90cf"), rgb("#4c7dbe"), rgb("#4270ae"), rgb("#375f98")]      # light .. dark flank
BELLY = [rgb("#e3ecf6"), rgb("#cddcee"), rgb("#b4c8e2"), rgb("#95adcc")]    # light .. groove
LIP = rgb("#eef4fa")
MOUTH = rgb("#10182c")
SILVER = [rgb("#f1f6fb"), rgb("#d7e2ee"), rgb("#b9c8da"), rgb("#8ea3bd"), rgb("#62789a")]
PLATE = [rgb("#9fb7d6"), rgb("#7f9cc4"), rgb("#6282ae"), rgb("#4a6995")]   # the dorsal scutes: steel blue, no bright rim
SPOT = rgb("#a8f7ff")
SPOT_GLOW = (70, 225, 255)
GLAND = [rgb("#fff4d0"), rgb("#ffe39a"), rgb("#f2c265")]
GLAND_GLOW = (255, 236, 176)
BALEEN = [rgb("#f2fbff"), rgb("#d2ecfa"), rgb("#aed7ef")]
BALEEN_GLOW = (96, 196, 232)
EYE = rgb("#081225")
GOLD_GLOW = (255, 196, 64)


def cbox(cx, cy, cz, w, h, d, paint, **kw):
    """A cube by its middle."""
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), paint, **kw)


# ------------------------------------------------------------------ the body
# Sizes in units (an eighth of a block at the game's x2). The head is 40 wide and 34 tall (LeviathanMoves.PART_WIDTH and
# PART_HEIGHT[0]); each segment tapers along its length from its front size to its back size, so the body narrows from
# 4.5 blocks behind the head to 1 at the tail stock; its middle size is the hit part's (PART_WIDTH, PART_HEIGHT).
SEGS = {1: (36, 32, 31, 28), 2: (30, 27, 25, 23), 3: (24, 22, 19, 18), 4: (17, 16, 12, 12)}
TAIL = [(11, 10), (9, 8.5), (7, 7)]
SEG_LEN = 52


def mid_size(i: int):
    fw, fh, bw, bh = SEGS[i]
    return (fw + bw) / 2.0, (fh + bh) / 2.0


def round_section(z0, z1, w, h, paint="hide"):
    """A round cross-section from z0 to z1: a wide box and a tall one."""
    zc, d = (z0 + z1) / 2.0, z1 - z0
    return [cbox(0, 0, zc, w, h * 0.7, d, paint), cbox(0, 0, zc, w * 0.7, h, d - 0.4, paint)]


def head_bone() -> list[Bone]:
    """A baleen whale's head, blunt and broad: the rostrum highest behind the eyes and falling to a rounded snout, a
    pale lip along its whole wide gape, a dark mouth behind a comb of pale crystal baleen that shows at the sides and
    across the front, small eyes over the corners of the mouth, and long pale pectoral fins behind. The lower jaw is its
    own bone (the Song opens it)."""
    cubes = [
        cbox(0, 6, 4, 40, 18, 52, "hide"),            # the rostrum: y -3 to 15
        cbox(0, 5, -25, 34, 16, 6, "hide"),           # rounding to the snout
        cbox(0, 3.5, -30, 24, 13, 4, "hide"),         # the snout's tip
        cbox(0, 16, 8, 32, 2, 40, "hide"),            # the crown's curve
        cbox(0, -5, 1, 28, 4, 52, "mouth"),           # the mouth, dark behind the baleen
        cbox(-20.2, 0.5, 20, 0.8, 3, 3, "eye"),       # small eyes over the corners of the mouth
        cbox(20.2, 0.5, 20, 0.8, 3, 3, "eye"),
    ]
    for side in (-1, 1):                              # the baleen: a close fringe of crystal along each side of the gape
        for k in range(17):
            h = 4.4 + 0.8 * math.sin(k * 2.1)         # ragged ends, a fringe rather than a row of teeth
            cubes.append(cbox(side * 17.4, -3 - h / 2, -23 + 2.9 * k, 1.4, h, 2.8, "baleen"))
    for k in range(8):                                # and across the front, following the snout's curve
        x = -10.15 + 2.9 * k
        h = 4.4 + 0.8 * math.sin(k * 2.7 + 1.0)
        cubes.append(cbox(x, -3 - h / 2, -27.0 + 0.05 * x * x, 2.8, h, 1.4, "baleen"))
    head = Bone("head", None, (0, 0, 0), None, cubes)
    jaw = Bone("jaw", "head", (0, -5, 27), None, [
        cbox(0, -12, 2, 40, 10, 52, "jaw"),           # a broad lower jaw, as wide as the head: y -17 to -7
        cbox(0, -11.5, -27, 32, 9, 6, "jaw"),
        cbox(0, -11, -31.5, 22, 8, 3, "jaw"),
    ])
    fins = []
    for side, name in ((1, "fin_left"), (-1, "fin_right")):
        # a humpback's long flipper, low behind the head, sweeping back and down, narrowing to its tip along its
        # leading edge (the front, -Z, as built)
        root = (side * 18.0, -12.0, 25.0)
        rot = (0, side * -52, side * -28)
        pieces = [(0, 20, 14, 2.6), (20, 16, 10, 2.2), (36, 12, 6, 1.8)]     # (from the root, length, width, thickness)
        cubes = []
        for start, length, width, thick in pieces:
            x0 = root[0] + start if side > 0 else root[0] - start - length
            cubes.append(Cube((x0, -12 - thick / 2, 18), (length, thick, width), "fin", rotation=rot, pivot=root))
        fins.append(Bone(name, "head", root, None, cubes))
    return [head, jaw, *fins]


def segment_bones(i: int) -> list[Bone]:
    """Body segment i (1 to 4): round in section and tapering along its length in three steps, with its song gland on
    the spine under one armoured scute, hinged at its front edge (it lifts in a Moorage)."""
    fw, fh, bw, bh = SEGS[i]
    mw, mh = mid_size(i)
    name = f"seg{i}"
    third = SEG_LEN / 3.0
    cubes = (round_section(-SEG_LEN / 2, -SEG_LEN / 2 + third, fw, fh)
             + round_section(-SEG_LEN / 2 + third, SEG_LEN / 2 - third, mw, mh)
             + round_section(SEG_LEN / 2 - third, SEG_LEN / 2, bw, bh))
    top = mh / 2.0
    sw = min(10.0, mw * 0.36)
    cubes.append(cbox(0, top + 0.3, -1, min(6.0, sw - 1.2), 2.2, 10, "gland"))    # the song gland, under its scute
    seg = Bone(name, None, (0, 0, 0), None, cubes)
    scute = Bone(f"plate{i}", name, (0, top, -9.5), None, [
        cbox(0, top + 1.3, -1, sw, 2, 14, "plate"),                                 # an arrow-headed scute pointing forward
        cbox(0, top + 1.3, -9.5, sw * 0.55, 2, 3, "plate"),
        cbox(0, top + 1.3, -12, sw * 0.22, 2, 2, "plate"),
        cbox(0, top + 2.6, -3, 1.2, 0.8, 14, "ridge"),                             # its keel
    ])
    return [seg, scute]


def tail_bones() -> list[Bone]:
    cubes = []
    for k, (w, h) in enumerate(TAIL):
        cubes += round_section(-22 + 14 * k, -22 + 14 * (k + 1) + (0.5 if k < 2 else 0), w, h)
    tail = Bone("tail", None, (0, 0, 0), None, cubes)
    fan = Bone("fan", "tail", (0, 0, 16), None, [
        cbox(0, 0, 25, 14, 3, 18, "fin"),
        Cube((-38, -1.5, 16), (38, 3, 16), "fin", rotation=(0, -22, 0), pivot=(0, 0, 22)),
        Cube((0, -1.5, 16), (38, 3, 16), "fin", rotation=(0, 22, 0), pivot=(0, 0, 22)),
        Cube((-38, -1.2, 31), (24, 2.4, 3), "fin_edge", rotation=(0, -22, 0), pivot=(0, 0, 22)),
        Cube((14, -1.2, 31), (24, 2.4, 3), "fin_edge", rotation=(0, 22, 0), pivot=(0, 0, 22)),
    ])
    return [tail, fan]


def build_leviathan() -> list[Bone]:
    bones = head_bone()
    for i in range(1, 5):
        bones += segment_bones(i)
    bones += tail_bones()
    return bones


# ------------------------------------------------------------------ painting
def waterline(bone: str, c: Cube, z: float) -> float:
    """Where the dark back meets the pale belly on a flank (a gentle wave a little under the middle)."""
    seed = sum(ord(ch) for ch in bone)
    tall = max(c.size[1], 1.0)
    return -0.12 * tall + 1.1 * math.sin(z * 0.21 + seed)


def spot_at(bone: str, p, line: float) -> float:
    """How much of a cyan photophore lands at point p (0 none .. 1 its heart): one row along each flank just over the
    waterline, about 14 units apart, jittered by a hash so no two segments repeat."""
    x, y, z = p
    best = 0.0
    seed = sum(ord(ch) for ch in bone)
    k0 = math.floor(z / 14.0)
    for k in (k0 - 1, k0, k0 + 1):
        cz = k * 14.0 + 7.0 + (hash01(seed, k) - 0.5) * 5.0
        cy = line + 2.2 + (hash01(seed, k, 7) - 0.5) * 1.5
        r = 1.2 + 0.7 * hash01(seed, k, 3)
        d = math.hypot(z - cz, y - cy)
        best = max(best, 1.0 - d / r if d < r else 0.0)
    return best


def texel(bone: str, c: Cube, face: str, col: int, row: int, w: int, h: int, variant: str):
    """(colour, spot glow or None, gland glow or None, fan glow or None) of one texel."""
    p = texel_point(c, face, col, row)
    kind = c.paint
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    x, y, z = p
    spot = None
    gland = None
    fan = None
    side = face in ("xpos", "xneg", "front", "back")
    if kind == "eye":
        colour = EYE
        if face in ("xpos", "xneg"):
            colour = LIP if edge else EYE
    elif kind == "mouth":
        colour = MOUTH
    elif kind == "gland":
        colour = GLAND[0] if face == "top" else GLAND[1] if not edge else GLAND[2]
        gland = GLAND_GLOW if face == "top" or not edge else (150, 120, 70)
    elif kind == "baleen":
        # pale crystal, clearest where it hangs from the jaw, bluer and dimmer toward its ragged tips
        f = row / max(1, h - 1)
        colour = BALEEN[0] if f < 0.34 else BALEEN[1] if f < 0.67 else BALEEN[2]
        spot = tuple(int(ch * (0.5 - 0.3 * f)) for ch in BALEEN_GLOW)
    elif kind == "plate":
        colour = PLATE[1] if face == "top" else PLATE[2]
        if edge:
            colour = PLATE[3]
    elif kind == "ridge":
        colour = PLATE[0] if face == "top" else PLATE[1]
    elif kind in ("fin", "fin_edge"):
        # pale beneath and along the leading edge, mid blue above: a humpback's long flippers, the flukes
        if face == "bottom":
            colour = BELLY[1]
        elif kind == "fin_edge":
            colour = BELLY[0]
        elif face == "front" and bone == "head":
            colour = LIP                                           # the flipper's pale leading edge
        elif face != "top":
            colour = HIDE[2]
        else:
            lead = row >= h - 2 if bone == "head" else False
            colour = LIP if lead else HIDE[1] if not edge else HIDE[2]
        if bone == "fan":
            fan = GOLD_GLOW if face in ("top", "bottom") or kind == "fin_edge" else (180, 130, 40)
    elif kind == "jaw":
        if face == "top":
            colour = LIP if edge else MOUTH                        # the lower lip round the mouth's floor
        elif face == "bottom":
            colour = BELLY[1] if int(math.floor(x)) % 3 else BELLY[3]   # the throat's pleats
        else:
            if y > -8.0:
                colour = LIP                                       # the lower lip
            elif y > -10.0:
                colour = HIDE[2]
            else:
                colour = BELLY[1] if int(math.floor(y)) % 2 else BELLY[2]   # pleats down the throat
    else:  # hide
        if face == "top":
            colour = HIDE_TOP
            if hash01(x, y, z, 11) < 0.01:
                colour = HIDE[3]
        elif face == "bottom":
            colour = MOUTH if bone == "head" else BELLY[1]
        elif bone == "head":
            # the rostrum: mid blue down to the pale lip along the whole gape; no spots on its face
            if y < -1.2:
                colour = LIP
            else:
                colour = HIDE[1] if not edge else HIDE[2]
                if face in ("xpos", "xneg") and z > 4:
                    s = spot_at("head", (x, y, z), 1.0)
                    if s > 0.0 and z < 26:
                        colour = SPOT
                        spot = tuple(int(ch * (0.55 + 0.45 * s)) for ch in SPOT_GLOW)
        else:
            line = waterline(bone, c, z)
            if y < line - 0.6:
                colour = BELLY[0] if y > line - 3 else BELLY[1]
            elif y < line + 0.6:
                colour = BELLY[2]
            else:
                colour = HIDE[1] if y < line + 3 else HIDE[2]
                if edge and face in ("front", "back"):
                    colour = HIDE[3]
                if face in ("xpos", "xneg") and bone != "tail":
                    s = spot_at(bone, p, line)
                    if s > 0.0:
                        colour = SPOT
                        spot = tuple(int(ch * (0.55 + 0.45 * s)) for ch in SPOT_GLOW)
    if variant == "dormant":
        grey = sum(colour) / 3.0
        colour = tuple(int(0.62 * (0.55 * ch + 0.45 * grey)) for ch in colour)
        spot = tuple(int(ch * 0.3) for ch in spot) if spot else None
    return colour, spot, gland, fan


def paint(bones, variant="base"):
    tex = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    spots = np.zeros_like(tex)
    glands = np.zeros_like(tex)
    fans = np.zeros_like(tex)
    for b in bones:
        root = b.name if b.parent is None else ("head" if b.name in ("jaw", "fin_left", "fin_right") else
                                                 "fan" if b.name == "fan" else b.parent)
        name = "fan" if b.name == "fan" else root
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(rh):
                    for col in range(rw):
                        colour, s, g, f = texel(name, c, face, col, r, rw, rh, variant)
                        tex[rv + r, ru + col, :3] = colour
                        tex[rv + r, ru + col, 3] = 255
                        for layer, v in ((spots, s), (glands, g), (fans, f)):
                            if v is not None:
                                layer[rv + r, ru + col, :3] = v
                                layer[rv + r, ru + col, 3] = 255
    return tex, spots, glands, fans


# ------------------------------------------------------------------ animations (the child bones only: the game places the body)
SCUTE_LIFT = 62.0   # degrees each scute swings up on its front hinge in a Moorage


def anims():
    out = {}
    L = 3.0
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    swim = {
        "fin_left": {"rotation": kf(ts, lambda t: (0, 0, 12 * s(t)))},
        "fin_right": {"rotation": kf(ts, lambda t: (0, 0, -12 * s(t)))},
        "fan": {"rotation": kf(ts, lambda t: (14 * s(t, 1.2), 0, 0))},
        "jaw": {"rotation": kf(ts, lambda t: (2.0 + 1.5 * s(t, 0.5), 0, 0))},
    }
    out["swim"] = {"loop": True, "animation_length": L, "bones": swim}
    L2 = 6.0
    ts2 = loop_times(L2, 12)
    s2 = lambda t, ph=0.0: math.sin(2 * math.pi * t / L2 + ph)
    out["sleep"] = {"loop": True, "animation_length": L2, "bones": {
        "fan": {"rotation": kf(ts2, lambda t: (4 * s2(t), 0, 0))},
        "fin_left": {"rotation": kf(ts2, lambda t: (0, 0, 16))},
        "fin_right": {"rotation": kf(ts2, lambda t: (0, 0, -16))},
    }}
    lift = {}
    for i in range(1, 5):
        lift[f"plate{i}"] = {"rotation": kf([0.0, 0.5, 0.75], lambda t: (SCUTE_LIFT * min(1.0, t / 0.5), 0, 0))}
    out["moor"] = {"loop": False, "animation_length": 0.75, "bones": lift}
    held = {}
    L3 = 2.4
    ts3 = loop_times(L3, 8)
    for i in range(1, 5):
        held[f"plate{i}"] = {"rotation": kf(ts3, lambda t, i=i: (SCUTE_LIFT + 4 * math.sin(2 * math.pi * t / L3 + i), 0, 0))}
    held["fan"] = {"rotation": kf(ts3, lambda t: (6 * math.sin(2 * math.pi * t / L3), 0, 0))}
    out["moored"] = {"loop": True, "animation_length": L3, "bones": held}
    return {"format_version": "1.8.0", "animations": out}


# ------------------------------------------------------------------ the bell, the coil, the grown bridge, the halo
RIME = [rgb("#f4fbff"), rgb("#d9f0fb"), rgb("#b4dcef"), rgb("#86bfdc"), rgb("#5b97bd")]
WOOD = [rgb("#c9b89a"), rgb("#a99a7e"), rgb("#8a7d65"), rgb("#6b604e")]


def bell_texture():
    """The Rimeglass bell: pale ice-blue crystal, lit from inside at the lip, faceted."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    ys, xs = np.mgrid[0:16, 0:16]
    idx = np.where((xs + ys) % 5 == 0, 1, 2)
    idx = np.where(ys < 3, 1, idx)
    idx = np.where(ys > 12, 3, idx)
    idx = np.where((xs == 0) | (xs == 15), 3, idx)
    img[..., :3] = np.array(RIME)[idx]
    img[13:15, 2:14, :3] = RIME[0]
    return img


SONG_CRYSTAL = [rgb("#f2ffff"), rgb("#c3f6ff"), rgb("#86e6fb"), rgb("#4cc8ec"), rgb("#2a9ccd")]


def singing_rimeglass_texture():
    """Singing Rimeglass: Rimeglass alight from within, pale cyan facets round a white-hot heart (drawn full bright)."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    ys, xs = np.mgrid[0:16, 0:16]
    d = np.hypot(xs - 7.5, ys - 7.5)
    idx = np.clip((d / 3.2).astype(int), 0, 4)
    facet = ((xs + 2 * ys) % 7 == 0) | ((2 * xs - ys) % 9 == 0)
    idx = np.where(facet, np.maximum(idx - 1, 0), idx)
    idx = np.where((xs == 0) | (xs == 15) | (ys == 0) | (ys == 15), 3, idx)
    img[..., :3] = np.array(SONG_CRYSTAL)[idx]
    return img


def frame_texture():
    """The bell's frame: Driftwood, grey-brown grain."""
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    ys, xs = np.mgrid[0:16, 0:16]
    idx = np.where((ys + (xs // 4)) % 4 == 0, 2, 1)
    idx = np.where((xs == 0) | (xs == 15) | (ys == 0) | (ys == 15), 3, idx)
    img[..., :3] = np.array(WOOD)[idx]
    return img


def bell_model():
    def box(f, t, tex, emissive=False):
        e = {"from": f, "to": t, "faces": {k: {"texture": tex} for k in ("north", "south", "east", "west", "up", "down")}}
        if emissive:
            e["neoforge_data"] = {"block_light": 12, "sky_light": 15}
        return e
    return {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
            "textures": {"bell": "cosmicbreach:block/rift_bell", "frame": "cosmicbreach:block/rift_bell_frame",
                         "particle": "cosmicbreach:block/rift_bell"},
            "elements": [box([1, 0, 1], [15, 2, 15], "#frame"),          # the base
                         box([1, 2, 7], [3, 16, 9], "#frame"), box([13, 2, 7], [15, 16, 9], "#frame"),   # posts
                         box([1, 14, 6.5], [15, 16, 9.5], "#frame"),      # the beam
                         box([6, 12, 6], [10, 14, 10], "#bell"),          # the bell's crown
                         box([4.5, 6, 4.5], [11.5, 12, 11.5], "#bell"),  # its waist
                         box([3.5, 4, 3.5], [12.5, 6, 12.5], "#bell", True),   # its lip, lit
                         box([7.5, 3, 7.5], [8.5, 5, 8.5], "#bell", True)]}    # the clapper


def halo_icon():
    """The Halo of Nine: a ring of nine small pale stars round a silver circlet on nothing."""
    img = np.zeros((16, 16, 4), np.uint8)
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, (ys - 8) * 1.25)
    ring = (d > 4.2) & (d < 5.6)
    img[ring, :3] = SILVER[1]
    img[ring, 3] = 255
    img[ring & (ys > 8), :3] = SILVER[3]
    for k in range(9):
        a = 2 * math.pi * k / 9 - math.pi / 2
        sx = int(round(8 + 6.4 * math.cos(a) - 0.5))
        sy = int(round(8 + 5.1 * math.sin(a) - 0.5))
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            x, y = sx + dx, sy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                img[y, x, :3] = (255, 250, 214) if (dx, dy) == (0, 0) else (150, 230, 255)
                img[y, x, 3] = 255
    return img


def write_blocks():
    written = []
    models = ASSETS / "models" / "block"
    states = ASSETS / "blockstates"
    items = ASSETS / "models" / "item"
    b = "cosmicbreach:block/"
    written.append(save_png(bell_texture(), TEX / "block" / "rift_bell.png"))
    written.append(save_png(frame_texture(), TEX / "block" / "rift_bell_frame.png"))
    written.append(write_json(models / "rift_bell.json", bell_model()))
    written.append(write_json(states / "rift_bell.json", {"variants": {"": {"model": b + "rift_bell"}}}))
    written.append(write_json(items / "rift_bell.json", {"parent": b + "rift_bell"}))
    # the coil's back is invisible (the body is drawn by the entity); a model for its particles only
    written.append(write_json(models / "leviathan_coil.json", {"textures": {"particle": b + "rimeglass"}}))
    written.append(write_json(states / "leviathan_coil.json", {"variants": {"": {"model": b + "leviathan_coil"}}}))
    written.append(write_json(models / "rift_bridge.json", {"parent": "minecraft:block/cube_all", "textures": {"all": b + "driftwood_planks"}}))
    written.append(write_json(states / "rift_bridge.json", {"variants": {"": {"model": b + "rift_bridge"}}}))
    written.append(save_png(singing_rimeglass_texture(), TEX / "block" / "singing_rimeglass.png"))
    written.append(write_json(models / "singing_rimeglass.json", {"parent": "minecraft:block/cube_all",
                                                                  "textures": {"all": b + "singing_rimeglass"}}))
    written.append(write_json(states / "singing_rimeglass.json", {"variants": {"": {"model": b + "singing_rimeglass"}}}))
    written.append(write_json(items / "singing_rimeglass.json", {"parent": b + "singing_rimeglass"}))
    written.append(save_png(halo_icon(), TEX / "item" / "halo_of_nine.png"))
    written.append(write_json(items / "halo_of_nine.json", {"parent": "minecraft:item/generated",
                                                            "textures": {"layer0": "cosmicbreach:item/halo_of_nine"}}))
    return written


def main():
    written = []
    bones = build_leviathan()
    pack_uvs(bones, TEX_W, TEX_H)
    geo = geo_json(bones, "thalassine_leviathan", TEX_W, TEX_H, 12, 8)
    written.append(write_text(ASSETS / "geo" / "entity" / "thalassine_leviathan.geo.json", pretty_json(geo)))
    written.append(write_text(ASSETS / "animations" / "entity" / "thalassine_leviathan.animation.json", pretty_json(anims())))
    ent = TEX / "entity"
    tex, spots, glands, fans = paint(bones, "base")
    written.append(save_png(tex, ent / "thalassine_leviathan.png"))
    written.append(save_png(spots, ent / "thalassine_leviathan_glowmask.png"))
    written.append(save_png(glands, ent / "thalassine_leviathan_glands_glowmask.png"))
    written.append(save_png(fans, ent / "thalassine_leviathan_fan_glowmask.png"))
    tex, spots, _, _ = paint(bones, "dormant")
    written.append(save_png(tex, ent / "thalassine_leviathan_dormant.png"))
    written.append(save_png(spots, ent / "thalassine_leviathan_dormant_glowmask.png"))
    written += write_blocks()
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
