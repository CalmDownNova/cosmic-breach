"""The Unsung's three porcelain masks and the Silent Nave's blocks (G8, Unsung design v1; polish G8p), for GeckoLib 4
and the block models.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/unsung_{alto,tenor,bass}.geo.json        Bedrock geometry 1.12.0, per-face UV, built at full size
    animations/entity/unsung_{voice}.animation.json     rest, rise, float, sing, inhale, drop, fall, fallen,
                                                        shatter, broken (the pose) and shroud (the cloak, its own loop);
                                                        one per mask, as each breaks along its own shards
    textures/entity/unsung_{voice}.png (+ _glowmask)            the mask, its hood and cloak, its inner light
    textures/entity/unsung_{voice}_broken.png (+ _glowmask)     broken for good: grey, dark fissures, no light
    textures/fx/note_ring.png                                  the ring round a Homing Note
    textures/particle/abyss_mote.png, particles/abyss_mote.json the Rift Abyss's ambient mote
    textures/block/: hymnal_altar_{side,top,pages}, lichen_window_{magenta,teal}(_dim), silence_circle(_lit)
    blockstates/ and models/block/ for the Hymnal Altar, the lichen windows and the circles of silence
    textures/item/choir_pendant.png and models/item/choir_pendant.json

The mask. Model space as the other creatures: 16 units a block, y up, the face looks toward -Z, the entity's feet at
the origin. The face's middle is 2.6 blocks up (UnsungMoves.FACE_UP), the face about 2 blocks tall. It is a shell of
porcelain, not a head: a smooth dome of columns one unit square (runs of equal depth merged into one cube), two units
thick, stylised like a theatre mask: a soft nose ridge, gentle arches over the eyes, a rounded rise round the mouth,
and three real holes (two almond eyes, the mouth's O) with a plate of inner light behind them. The mouth's two lips
are porcelain that part: a slit while the mask hums, the full O while it sings.

Porcelain, not skin: the front is painted as one picture (2 texels a unit, per-face UV) with smooth shading from the
relief, a darker rim, a glaze highlight on the brow and cheek, and a white tinted by the voice (warm ivory for the
Alto, cool celadon for the Tenor, lilac for the Bass, so the three differ from afar). The walls of the relief's steps
sample the same picture, so no seams streak it. Much of the porcelain's light is in the glowmask (added full bright
over the shaded model): the game's entity shading alone leaves a white texture mid grey. The glowmask also carries
the fine cracks in the voice's colour, the light inside the holes and the hood's hem.

The face is cut into seven shards along jagged lines (their own bones): whole, they sit together; when a mask is
broken for good they part, two of them fly off and the hood dissolves, so a broken mask lies visibly in pieces.

The shroud: a hood of void cloth attached at the mask's rim (tucked behind it, so there is no gap from any side),
flaring wider than the mask and back like a cowl, with a peak; under it a cloak of three wide panels that fades into
smoke of the voice's colour (its glow fades with its alpha). The shroud animation sways the cloak slowly.

Run:  python tools/art/gen_unsung.py   (then preview_unsung.py)
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field

import numpy as np

from bedrock_model import rot_x, rot_y, rot_z
from blockart import rng_for
from common import ASSETS, TEX, save_png

FACE_UP = 2.6 * 16.0            # the face's middle over the feet, units
TEX_W = 128
TEX_H = 128
PX = 2                          # texels a unit on the painted front
FRONT = (0, 0)                  # the front picture's corner in the texture, covering x, y in [-16, 16]
FRONT_SPAN = 32                 # units
HOOD = (64, 0, 32, 32)          # u, v, w, h of the hood's cloth: its hem along the top row, its rim at the mask below
INNER = (96, 0, 32, 32)         # the inner light plate
DRAPE = (64, 32, 64, 64)        # the cloak, from its top down to where it is only smoke
PATCH = {                       # 2 x 2 texel swatches
    "hole": (0, 66), "inside": (4, 66), "edge": (8, 66), "fracture": (12, 66), "clear": (16, 66),
}
SHARDS = 7
LIGHT = np.array([-0.42, 0.62, 0.66]) / np.linalg.norm([-0.42, 0.62, 0.66])   # painted light: upper left, in front


@dataclass
class Voice:
    name: str
    W: float                    # face width, units
    H: float                    # face height
    taper: float                # how much the lower face narrows
    eye: tuple                  # (x of eye centres, y, half width, half height, tilt degrees: outer corners up if > 0)
    nose: tuple                 # (half width, length down from the eyes)
    mouth: tuple                # (y, half width, half height of the open O)
    jaw_square: float           # 0 round chin .. 1 square jaw
    tint: tuple                 # the porcelain, tinted by the voice
    glow: tuple                 # crack and inner light
    accent: tuple               # the hood's hem, the smoke
    cloth: tuple                # the void cloth's colour
    cracks: tuple               # the points of impact the cracks run from, face coordinates


VOICES = [
    Voice("alto", W=20.0, H=32.0, taper=0.46, eye=(4.4, 3.2, 3.6, 2.6, -7.0), nose=(1.0, 7.0), mouth=(-8.0, 2.5, 2.6),
          jaw_square=0.0, tint=(255, 238, 206), glow=(255, 214, 128), accent=(255, 186, 72), cloth=(30, 20, 14),
          cracks=((-6.2, 10.5), (7.0, 0.5))),
    Voice("tenor", W=22.0, H=31.0, taper=0.34, eye=(4.9, 3.3, 3.7, 2.5, 0.0), nose=(1.2, 7.4), mouth=(-8.0, 3.0, 2.7),
          jaw_square=0.35, tint=(218, 242, 250), glow=(150, 245, 235), accent=(64, 224, 208), cloth=(10, 22, 30),
          cracks=((6.8, 6.5), (-7.5, -8.5))),
    Voice("bass", W=25.0, H=30.0, taper=0.16, eye=(5.6, 3.0, 3.8, 2.4, -4.0), nose=(1.5, 7.6), mouth=(-8.3, 3.8, 2.7),
          jaw_square=0.85, tint=(236, 222, 255), glow=(200, 140, 255), accent=(255, 70, 190), cloth=(22, 12, 34),
          cracks=((-8.0, 2.0), (4.8, 10.0))),
]


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


# ------------------------------------------------------------------ the face as a field

def face_half_width(v: Voice, y: float) -> float:
    a = v.W / 2.0
    b = v.H / 2.0
    if y < 0:
        u = min(1.0, -y / b)
        round_chin = 1.0 - v.taper * u * u
        square = 1.0 - v.taper * 0.35 * u * u * u
        a *= round_chin * (1 - v.jaw_square) + square * v.jaw_square
    return a


def rim(v: Voice, x: float, y: float) -> float:
    """0 in the middle of the face, 1 on its outline, more outside."""
    b = v.H / 2.0
    a = max(face_half_width(v, y), 1e-6)
    p = 2.4 if y >= 0 else 2.0 + 1.4 * v.jaw_square
    return (abs(x) / a) ** p + (abs(y) / b) ** 2.2


def inside_face(v: Voice, x: float, y: float) -> bool:
    return face_half_width(v, y) > 0 and rim(v, x, y) <= 1.0


def in_eye(v: Voice, x: float, y: float, grow: float = 0.0) -> bool:
    ex, ey, ew, eh, tilt = v.eye
    for side in (-1, 1):
        dx = x - side * ex
        dy = y - ey
        t = math.radians(tilt) * side * -1.0
        rx = dx * math.cos(t) - dy * math.sin(t)
        ry = dx * math.sin(t) + dy * math.cos(t)
        # an almond: pointed at the ends
        w = ew + grow
        h = (eh + grow) * max(0.0, 1.0 - (abs(rx) / max(w, 1e-6)) ** 1.3)
        if abs(rx) <= w and abs(ry) <= h:
            return True
    return False


def in_mouth(v: Voice, x: float, y: float, grow: float = 0.0) -> bool:
    my, mw, mh = v.mouth
    w = mw + grow
    h = (mh + grow) * max(0.0, 1.0 - (abs(x) / max(w, 1e-6)) ** 2.0) + 0.001
    return abs(x) <= w and abs(y - my) <= h


def depth(v: Voice, x: float, y: float) -> float:
    """How far forward (units) the porcelain's front stands at (x, y): a smooth dome with soft features."""
    a = face_half_width(v, y) + 1.0
    b = v.H / 2.0 + 1.2
    u = min(1.0, abs(x) / a)
    w = min(1.0, abs(y) / b)
    d = 1.2 + 4.4 * math.sqrt(max(0.0, 1.0 - u ** 2.2)) * math.sqrt(max(0.0, 1.0 - w ** 2.4))
    ex, ey, ew, eh, _ = v.eye
    nw, nlen = v.nose
    # the nose: a narrow rounded ridge from between the eyes to its tip, rising toward the tip
    top = ey + 1.5
    tip = ey - nlen
    if tip - 1.6 <= y <= top:
        rise = 0.35 + 1.25 * (top - y) / (top - tip) if y >= tip else 1.6 * max(0.0, 1.0 - (tip - y) / 1.6)
        d += rise * math.exp(-(x / (nw + 0.35)) ** 2)
    for side in (-1, 1):
        dx = x - side * ex
        # a gentle arch over the eye, and a soft hollow round it
        d += 0.4 * math.exp(-(dx / (ew + 1.2)) ** 2 - ((y - (ey + eh + 1.9)) / 1.3) ** 2)
        d -= 0.8 * math.exp(-(dx / (ew + 1.5)) ** 2 - ((y - ey) / (eh + 1.5)) ** 2)
    # a rounded rise round the mouth (no lips)
    my, mw, mh = v.mouth
    d += 0.45 * math.exp(-(x / (mw + 2.6)) ** 2 - ((y - my) / (mh + 2.4)) ** 2)
    return d


def gradient(v: Voice, x: float, y: float):
    e = 0.3
    return ((depth(v, x + e, y) - depth(v, x - e, y)) / (2 * e), (depth(v, x, y + e) - depth(v, x, y - e)) / (2 * e))


def highlight(v: Voice, x: float, y: float) -> float:
    """The glaze's highlights, 0..1: a long one on the brow, one on the cheek, a line down the nose, one on the chin."""
    ex, ey, _, _, _ = v.eye
    spots = [(-3.2, v.H * 0.29, 4.0, 1.6, 0.30, 1.0), (-(ex + 0.6), ey - 6.0, 1.9, 1.0, 0.55, 0.65),
             (-0.35, ey - 3.0, 0.45, 2.2, 0.0, 0.55), (-1.0, -v.H / 2.0 + 3.6, 1.8, 0.7, 0.2, 0.35)]
    s = 0.0
    for cx, cy, rx, ry, ang, k in spots:
        dx, dy = x - cx, y - cy
        ca, sa = math.cos(ang), math.sin(ang)
        p = (dx * ca + dy * sa) / rx
        q = (-dx * sa + dy * ca) / ry
        s += k * math.exp(-((p * p + q * q) ** 1.3))
    return min(1.0, s)


# ------------------------------------------------------------------ the shards

def shard_seeds(v: Voice):
    rng = rng_for(f"unsung_shards_{v.name}")
    seeds = []
    tries = 0
    while len(seeds) < SHARDS and tries < 20000:
        tries += 1
        x = float(rng.uniform(-v.W / 2, v.W / 2))
        y = float(rng.uniform(-v.H / 2, v.H / 2))
        if not inside_face(v, x, y) or rim(v, x, y) > 0.6:
            continue
        if all((x - sx) ** 2 + (y - sy) ** 2 > 6.4 ** 2 for sx, sy in seeds):
            seeds.append((x, y))
    return seeds


def shard_of(seeds, x: float, y: float) -> int:
    """The shard (x, y) belongs to: the nearest seed, the borders made jagged."""
    best, bd = 0, 1e9
    for k, (sx, sy) in enumerate(seeds):
        jag = 1.1 * math.sin(1.3 * x + 2.1 * y + 1.7 * k) * math.cos(1.9 * y - 0.8 * x + 2.3 * k)
        d = math.hypot(x - sx, y - sy) + jag
        if d < bd:
            best, bd = k, d
    return best


# ------------------------------------------------------------------ cubes and bones

@dataclass
class Cube:
    origin: tuple
    size: tuple
    uv: dict
    rotation: tuple | None = None
    pivot: tuple | None = None


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple | None = None
    cubes: list = field(default_factory=list)


def patch(name, w=1.0, h=1.0):
    u, v = PATCH[name]
    return {"uv": [u + 0.5 - w / 2, v + 0.5 - h / 2], "uv_size": [w, h]}


def front_uv(x0, y0, x1, y1):
    """The front picture's rectangle for face coordinates [x0, x1] x [y0, y1] (y up)."""
    u = FRONT[0] + (x0 + FRONT_SPAN / 2) * PX
    vv = FRONT[1] + (FRONT_SPAN / 2 - y1) * PX
    return {"uv": [round(u, 4), round(vv, 4)], "uv_size": [round((x1 - x0) * PX, 4), round((y1 - y0) * PX, 4)]}


def strip_row(x0, x1, y):
    """Half a texel of the front picture's row at y, from x0 to x1: the wall of a step takes the colour beside it."""
    u = FRONT[0] + (x0 + FRONT_SPAN / 2) * PX
    vv = FRONT[1] + (FRONT_SPAN / 2 - y) * PX
    return {"uv": [round(u, 4), round(vv - 0.25, 4)], "uv_size": [round((x1 - x0) * PX, 4), 0.5]}


def strip_col(x, y0, y1):
    u = FRONT[0] + (x + FRONT_SPAN / 2) * PX
    vv = FRONT[1] + (FRONT_SPAN / 2 - y1) * PX
    return {"uv": [round(u - 0.25, 4), round(vv, 4)], "uv_size": [0.5, round((y1 - y0) * PX, 4)]}


MIRROR = np.diag([-1.0, 1.0, 1.0])


def euler_for(axes):
    """GeckoLib cube rotation (Bedrock degrees) that turns a cube's x, y, z edges onto the model-space unit vectors
    in the columns of `axes` (a proper rotation). GeckoLib mirrors x (Bedrock to Java) and builds the rotation as
    Rz(rz) Ry(-ry) Rx(-rx) in Java space (bedrock_model.gecko_rot)."""
    m = MIRROR @ axes @ MIRROR
    b = math.asin(max(-1.0, min(1.0, -m[2, 0])))
    a = math.atan2(m[2, 1], m[2, 2])
    c = math.atan2(m[1, 0], m[0, 0])
    rx, ry, rz = -math.degrees(a), -math.degrees(b), math.degrees(c)
    back = (rot_z(math.radians(rz)) @ rot_y(math.radians(-ry)) @ rot_x(math.radians(-rx)))[:3, :3]
    assert np.allclose(back, m, atol=1e-6), "no Euler angles for these axes"
    return (rx, ry, rz)


def panel(p_start, p_end, p_a, p_b, thick, face_uv, edge_uv, nudge=0.0, over=1.0):
    """A flat cube whose length runs from p_start to p_end and whose width spans p_a to p_b (times `over`), `thick`
    thick; its big faces take `face_uv` (the texture's top row at p_end), its edges `edge_uv`."""
    p_start, p_end, p_a, p_b = (np.array(p, dtype=np.float64) for p in (p_start, p_end, p_a, p_b))
    along = p_end - p_start
    length = float(np.linalg.norm(along))
    e_len = along / length
    across = p_b - p_a
    width = float(np.linalg.norm(across)) * over
    across = across - e_len * float(across @ e_len)
    e_w = across / np.linalg.norm(across)
    e_n = np.cross(e_w, e_len)
    centre = (p_start + p_end) / 2.0 + e_n * nudge
    rot = euler_for(np.stack([e_w, e_len, e_n], axis=1))
    size = (width, length, thick)
    origin = tuple(float(c) for c in centre - np.array(size) / 2.0)
    uv = {"north": face_uv, "south": face_uv, "east": edge_uv, "west": edge_uv, "up": edge_uv, "down": edge_uv}
    return Cube(origin, size, uv, rotation=rot, pivot=tuple(float(c) for c in centre))


def hood_rings(v: Voice):
    """The hood's rims, front to back, as functions of the angle round the face (0 the side, 90 the top): tucked
    behind the mask's edge; its lip, a little in front of the mask's rim and wider, so from the side the mask sits in
    it; the widest bulge; then narrowing and drooping behind like a cowl hanging back. A soft peak at the top."""
    fc = FACE_UP

    def at(a, b, z, peak, drop):
        return lambda th: (a * math.cos(th), fc - drop + b * math.sin(th) + peak * max(0.0, math.sin(th)) ** 4, z)
    return [at(v.W / 2.0 - 1.6, v.H / 2.0 - 1.6, 1.2, 0.0, 0.0),
            at(v.W / 2.0 + 2.6, v.H / 2.0 + 2.8, -1.6, 1.6, 0.0),
            at(v.W / 2.0 + 5.6, v.H / 2.0 + 5.0, 4.0, 2.6, 0.5),
            at(v.W / 2.0 + 4.6, v.H / 2.0 + 3.8, 9.5, 1.6, 3.0),
            at(v.W / 2.0 + 1.6, v.H / 2.0 + 1.0, 14.0, 0.4, 7.0)]


def build_mask(v: Voice):
    """The bones of one mask: seven shards of the face and the lips that close its mouth, the inner light, the hood and
    the cloak's three panels (two bones each)."""
    fc = FACE_UP
    my, mw, mh = v.mouth
    seeds = shard_seeds(v)
    cells = {}                              # (x, y) -> (front z, bone)
    members = {k: [] for k in range(len(seeds))}
    for yi in range(-17, 17):
        for xi in range(-14, 14):
            cx, cy = xi + 0.5, yi + 0.5
            if not inside_face(v, cx, cy) or in_eye(v, cx, cy):
                continue
            z = -round(depth(v, cx, cy) * 2.0) / 2.0
            if in_mouth(v, cx, cy):
                cells[(xi, yi)] = (z - 0.5, "lip_upper" if cy > my else "lip_lower")
            else:
                k = shard_of(seeds, cx, cy)
                cells[(xi, yi)] = (z, f"shard_{k}")
                members[k].append((cx, cy))
    mouth_shard = shard_of(seeds, 0.0, my)
    shards = [k for k in range(len(seeds)) if members[k]]
    centroid = {k: (float(np.mean([p[0] for p in members[k]])), float(np.mean([p[1] for p in members[k]]))) for k in shards}
    bones = {"mask": Bone("mask", None, (0.0, fc, 0.0))}
    for k in shards:
        cx, cy = centroid[k]
        bones[f"shard_{k}"] = Bone(f"shard_{k}", "mask", (round(cx, 3), round(fc + cy, 3), -2.0))
    lip_parent = f"shard_{mouth_shard}" if mouth_shard in shards else "mask"
    bones["lip_upper"] = Bone("lip_upper", lip_parent, (0.0, fc + my + mh, -2.0))
    bones["lip_lower"] = Bone("lip_lower", lip_parent, (0.0, fc + my - mh, -2.0))
    bones["inner"] = Bone("inner", "mask", (0.0, fc, 0.0))
    bones["hood"] = Bone("hood", "mask", (0.0, fc, 1.0))
    thick = 2.0

    def hole_side(xn, yn):
        """True if the neighbouring column is a hole inside the face (an eye or the mouth)."""
        return (xn, yn) not in cells and inside_face(v, xn + 0.5, yn + 0.5)

    def lip_gap(bone, xn, yn):
        """True on the edge where the two lips part (it opens onto the light inside)."""
        c = cells.get((xn, yn))
        return c is not None and c[1] != bone and c[1].startswith("lip") and bone.startswith("lip")

    # merge runs of equal depth along x, per row and bone
    for yi in range(-17, 17):
        xi = -14
        while xi < 14:
            if (xi, yi) not in cells:
                xi += 1
                continue
            z, bone = cells[(xi, yi)]
            x_end = xi
            while (x_end + 1, yi) in cells and cells[(x_end + 1, yi)] == (z, bone):
                x_end += 1
            x0, x1 = xi, x_end + 1
            y0, y1 = yi, yi + 1
            lo_hole = hole_side(xi - 1, yi)
            hi_hole = hole_side(x_end + 1, yi)
            up_hole = any(hole_side(k, yi + 1) or lip_gap(bone, k, yi + 1) for k in range(x0, x1))
            down_hole = any(hole_side(k, yi - 1) or lip_gap(bone, k, yi - 1) for k in range(x0, x1))
            uv = {
                "north": front_uv(x0, y0, x1, y1),
                "south": patch("inside"),
                # GeckoLib's "west" face is the geometry's +x side, "east" its -x side
                "west": patch("hole") if hi_hole else strip_col(x1 - 0.25, y0, y1),
                "east": patch("hole") if lo_hole else strip_col(x0 + 0.25, y0, y1),
                "up": patch("hole") if up_hole else strip_row(x0, x1, y1 - 0.25),
                "down": patch("hole") if down_hole else strip_row(x0, x1, y0 + 0.25),
            }
            bones[bone].cubes.append(Cube((x0, fc + y0, z), (x1 - x0, 1.0, thick), uv))
            xi = x_end + 1

    # the plate of inner light behind the holes
    ex, ey, ew, eh, _ = v.eye
    top = ey + eh + 1.5
    bottom = my - mh - 0.5
    width = max(ex + ew + 1.0, mw + 1.0)
    iu, iv, iw, ih = INNER
    bones["inner"].cubes.append(Cube((-width, fc + bottom, 0.8), (2 * width, top - bottom, 0.5), {
        "north": {"uv": [iu, iv], "uv_size": [iw, ih]}, "south": patch("inside"), "east": patch("inside"),
        "west": patch("inside"), "up": patch("inside"), "down": patch("inside")}))

    # the hood: rings of panels from behind the mask's edge to its lip, out to its bulge and back to the cowl's end
    rings = hood_rings(v)
    hu, hv, hw, hh = HOOD
    cloth_uv = {"uv": [hu, hv], "uv_size": [hw, hh]}                  # the hem along its top row: at the lip
    plain_uv = {"uv": [hu, hv + 8], "uv_size": [hw, hh - 8]}          # the same cloth without the hem
    edge = patch("edge")
    k_panels = 18
    th0, th1 = math.radians(-66.0), math.radians(246.0)
    step = (th1 - th0) / k_panels
    for k in range(k_panels):
        th = th0 + (k + 0.5) * step
        a, b = th - step / 2, th + step / 2
        nudge = 0.05 if k % 2 == 0 else -0.05
        tuck, lip, wide = rings[0], rings[1], rings[2]
        bones["hood"].cubes.append(panel(tuck(th), lip(th), lip(a), lip(b), 0.4, plain_uv, edge, nudge, 1.08))
        bones["hood"].cubes.append(panel(wide(th), lip(th), wide(a), wide(b), 0.5, cloth_uv, edge, nudge, 1.08))
        for i in range(2, len(rings) - 1):
            front, back = rings[i], rings[i + 1]
            bones["hood"].cubes.append(panel(back(th), front(th), back(a), back(b), 0.5, plain_uv, edge, -nudge if i % 2 else nudge, 1.1))
    last = rings[-1]
    cx0, cy0, cz0 = last(math.radians(90.0))
    bw, bh = v.W / 2.0 + 1.6, v.H / 2.0 + 1.0
    bones["hood"].cubes.append(Cube((-bw * 0.95, fc - 7.0 - bh * 0.85, cz0 - 0.2), (bw * 1.9, bh * 1.85, 0.6), {
        "north": plain_uv, "south": plain_uv, "east": edge, "west": edge, "up": edge, "down": edge}))

    # the cloak: three wide panels under the mask, two bones each, fading into smoke
    du, dv, dw, dh = DRAPE
    clear = patch("clear")
    order_drapes = []
    for name, x, top_y, z, width_d, yaw in (("l", -(v.W / 2.0 + 2.5), -v.H / 2.0 + 7.0, 4.5, 16.0, 55.0),
                                           ("c", 0.0, -v.H / 2.0 + 9.0, 4.2, 22.0, 0.0),
                                           ("r", v.W / 2.0 + 2.5, -v.H / 2.0 + 7.0, 4.5, 16.0, -55.0)):
        seg = 12.0
        yaw_r = math.radians(yaw)
        across = np.array([math.cos(yaw_r), 0.0, math.sin(yaw_r)]) * width_d / 2.0
        parent = "mask"
        for j in range(2):
            y_top = fc + top_y - j * seg
            p_top = np.array([x, y_top, z + j * 1.2])
            p_bot = np.array([x, y_top - seg, z + (j + 1) * 1.2])
            name_j = f"drape_{name}_{j}"
            b = Bone(name_j, parent, tuple(round(float(c), 4) for c in p_top))
            face = {"uv": [du, dv + j * dh / 2], "uv_size": [dw, dh / 2]}
            b.cubes.append(panel(p_bot, p_top, p_top - across, p_top + across, 0.4, face, clear, 0.0, 1.0))
            bones[name_j] = b
            order_drapes.append(name_j)
            parent = name_j
    order = (["mask"] + [f"shard_{k}" for k in shards] + ["lip_upper", "lip_lower", "inner", "hood"] + order_drapes)
    return [bones[n] for n in order], {"seeds": seeds, "shards": shards, "centroid": centroid, "mouth_shard": mouth_shard}


def geo_json(bones, name):
    out = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = [round(float(a), 4) for a in b.pivot]
        if b.rotation:
            d["rotation"] = [round(float(a), 4) for a in b.rotation]
        if b.cubes:
            cl = []
            for c in b.cubes:
                cd = {"origin": [round(float(a), 4) for a in c.origin], "size": [round(float(a), 4) for a in c.size]}
                if c.rotation:
                    cd["pivot"] = [round(float(a), 4) for a in c.pivot]
                    cd["rotation"] = [round(float(a), 4) for a in c.rotation]
                cd["uv"] = c.uv
                cl.append(cd)
            d["cubes"] = cl
        out.append(d)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{name}",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4.5,
                "visible_bounds_offset": [0, 2.2, 0],
            },
            "bones": out,
        }],
    }


# ------------------------------------------------------------------ painting

def crack_network(v: Voice, rng, lines=11):
    """Fine cracks in face coordinates, like struck porcelain: from two points of impact they run outward, wandering
    and forking, to the rim; a few loose hairlines elsewhere. None crosses the mouth's surround or the nose's foot
    (a crack there reads as a moustache)."""
    my, mw, mh = v.mouth
    ex, ey, _, _, _ = v.eye
    nose_tip = ey - v.nose[1]

    def blocked(x, y):
        if not inside_face(v, x, y) or in_eye(v, x, y, 0.4) or in_mouth(v, x, y, 0.6):
            return True
        return abs(x) < mw + 2.8 and my - mh - 1.5 < y < nose_tip + 1.5

    paths = []
    origins = v.cracks
    for k in range(lines):
        if k < lines - 2:
            ox, oy = origins[k % len(origins)]
        else:
            ox, oy = rng.uniform(-v.W / 3, v.W / 3), rng.uniform(0, v.H / 3)
        x, y = ox + rng.normal(0, 0.4), oy + rng.normal(0, 0.4)
        if blocked(x, y):
            continue
        # away from the middle of the face, toward the rim
        out = math.atan2(oy, ox)
        heading = out + rng.normal(0, 0.9)
        pts = [(x, y)]
        for _ in range(int(rng.integers(8, 24))):
            heading += rng.normal(0, 0.30)
            x += math.cos(heading) * 0.7
            y += math.sin(heading) * 0.7
            if blocked(x, y):
                break
            pts.append((x, y))
            if rng.random() < 0.10:
                fh = heading + rng.choice([-1, 1]) * rng.uniform(0.5, 1.0)
                fx, fy = x, y
                fork = [(fx, fy)]
                for _ in range(int(rng.integers(3, 8))):
                    fh += rng.normal(0, 0.3)
                    fx += math.cos(fh) * 0.7
                    fy += math.sin(fh) * 0.7
                    if blocked(fx, fy):
                        break
                    fork.append((fx, fy))
                if len(fork) > 1:
                    paths.append(fork)
        if len(pts) > 1:
            paths.append(pts)
    return paths


def raster_lines(paths, size, px, span):
    """A float mask of the polylines on the front picture (1 on the line), and a soft halo."""
    h = w = size
    line = np.zeros((h, w))
    for pts in paths:
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            steps = int(max(abs(x1 - x0), abs(y1 - y0)) * px * 2) + 1
            for s in range(steps + 1):
                t = s / steps
                x = x0 + (x1 - x0) * t
                y = y0 + (y1 - y0) * t
                i = int((x + span / 2) * px)
                j = int((span / 2 - y) * px)
                if 0 <= i < w and 0 <= j < h:
                    line[j, i] = 1.0
    halo = np.zeros_like(line)
    for dj in (-1, 0, 1):
        for di in (-1, 0, 1):
            if di == 0 and dj == 0:
                continue
            halo = np.maximum(halo, np.roll(np.roll(line, dj, 0), di, 1) * (0.42 if di == 0 or dj == 0 else 0.22))
    return line, np.maximum(halo - line, 0.0)


def value_noise(rng, h, w, cell_y, cell_x):
    """Smooth value noise, 0..1, cells cell_y x cell_x texels."""
    gy, gx = h // cell_y + 2, w // cell_x + 2
    g = rng.random((gy, gx))
    yy, xx = np.mgrid[0:h, 0:w]
    fy, fx = yy / cell_y, xx / cell_x
    iy, ix = fy.astype(int), fx.astype(int)
    ty, tx = fy - iy, fx - ix
    ty, tx = ty * ty * (3 - 2 * ty), tx * tx * (3 - 2 * tx)
    a = g[iy, ix] * (1 - tx) + g[iy, ix + 1] * tx
    b = g[iy + 1, ix] * (1 - tx) + g[iy + 1, ix + 1] * tx
    return a * (1 - ty) + b * ty


def paint(v: Voice, broken=False):
    """The texture and the glowmask of a mask."""
    rng = rng_for(f"unsung_{v.name}{'_broken' if broken else ''}")
    tex = np.zeros((TEX_H, TEX_W, 4), dtype=np.float64)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.float64)
    n = FRONT_SPAN * PX
    tint = np.array(v.tint, dtype=np.float64)
    gcol = np.array(v.glow, dtype=np.float64)
    acol = np.array(v.accent, dtype=np.float64)
    cloth = np.array(v.cloth, dtype=np.float64)
    white = np.array((255.0, 252.0, 246.0))
    seeds = shard_seeds(v)
    shard_map = np.full((n, n), -1)
    # --- the front picture: glazed porcelain, shaded by its relief, darker to the rim
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / PX - FRONT_SPAN / 2
            y = FRONT_SPAN / 2 - (j + 0.5) / PX
            if not inside_face(v, x, y):
                continue
            gx, gy = gradient(v, x, y)
            nrm = np.array([-gx, -gy, 1.0])
            nrm /= np.linalg.norm(nrm)
            lam = max(0.0, float(nrm @ LIGHT))
            edge = smoothstep(0.45, 1.0, rim(v, x, y))
            ao = 0.0
            if in_eye(v, x, y, 0.9):
                ao += 0.12
            elif in_eye(v, x, y, 2.0):
                ao += 0.05
            if in_mouth(v, x, y, 1.0) and not in_mouth(v, x, y):
                ao += 0.08
            spec = highlight(v, x, y)
            shade = (0.70 + 0.30 * lam) * (1.0 - 0.32 * edge) * (1.0 - ao)
            tex[j, i, :3] = tint * shade + 34.0 * spec + rng.normal(0, 1.0, 3)
            tex[j, i, 3] = 255
            glaze = tint * 0.42 * (0.55 + 0.45 * lam) * (1.0 - 0.7 * edge) * (1.0 - ao)
            glow[j, i, :3] = glaze + white * 0.8 * spec
            glow[j, i, 3] = 255
            shard_map[j, i] = shard_of(seeds, x, y)
    face_mask = tex[:n, :n, 3] > 0
    # cracks: hairlines in the porcelain, the voice's light in the glow
    paths = crack_network(v, rng, lines=16 if broken else 9)
    line, halo = raster_lines(paths, n, PX, FRONT_SPAN)
    line *= face_mask
    halo *= face_mask
    if not broken:
        for c in range(3):
            tex[:n, :n, c] = np.where(line > 0, tint[c] * 0.70, tex[:n, :n, c] * (1.0 - 0.08 * halo))
            g = glow[:n, :n, c]
            glow[:n, :n, c] = np.where(line > 0, gcol[c], np.maximum(g, g * (1 - halo) + halo * gcol[c] * 0.55))
        core = (line > 0) & (np.roll(line, 1, 0) + np.roll(line, -1, 0) + np.roll(line, 1, 1) + np.roll(line, -1, 1) >= 3)
        glow[:n, :n, :3][core] = np.minimum(255.0, gcol * 0.6 + 140.0)
    else:
        # broken for good: grey and cold, the cracks dark fissures, the shards' borders open
        grey = tex[:n, :n, :3].mean(axis=-1, keepdims=True)
        tex[:n, :n, :3] = (grey * 0.55 + tex[:n, :n, :3] * 0.25) * np.array([0.92, 0.96, 1.06])
        glow[:n, :n, :3] *= 0.10
        border = np.zeros((n, n), dtype=bool)
        for dj, di in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = np.roll(np.roll(shard_map, dj, 0), di, 1)
            border |= (nb != shard_map) & (nb >= 0) & (shard_map >= 0)
        dark = (line > 0) | border
        tex[:n, :n, :3][dark] = tint * 0.16
        glow[:n, :n, :3][dark] = 0.0
    # --- the inner light: dark in the texture, the voice's colour burning in the glow
    iu, iv, iw, ih = INNER
    yy, xx = np.mgrid[0:ih, 0:iw]
    tex[iv:iv + ih, iu:iu + iw, :3] = cloth * 0.6
    tex[iv:iv + ih, iu:iu + iw, 3] = 255
    ex, ey, ew, eh, _ = v.eye
    my, mw, mh = v.mouth
    width = max(ex + ew + 1.0, mw + 1.0)
    top = ey + eh + 1.5
    bottom = my - mh - 0.5
    fx = (xx + 0.5) / iw * 2 * width - width
    fy = top - (yy + 0.5) / ih * (top - bottom)
    heat = np.zeros((ih, iw))
    for side in (-1, 1):
        heat = np.maximum(heat, np.exp(-((fx - side * ex) ** 2 / (ew * 0.95) ** 2 + (fy - ey) ** 2 / (eh * 1.2) ** 2)))
    heat = np.maximum(heat, np.exp(-(fx ** 2 / (mw * 1.1) ** 2 + (fy - my) ** 2 / (mh * 1.3) ** 2)))
    heat = np.clip(0.12 + heat, 0.0, 1.0)
    hot = np.clip((heat - 0.85) / 0.15, 0.0, 1.0) * 0.5
    for c in range(3):
        glow[iv:iv + ih, iu:iu + iw, c] = 0.0 if broken else gcol[c] * heat + 255.0 * hot * 0.5
    glow[iv:iv + ih, iu:iu + iw, 3] = 255
    # --- the hood: void cloth, softly pleated, a hem of the voice's colour along its flared edge (the top row)
    hu, hv, hw, hh = HOOD
    yy, xx = np.mgrid[0:hh, 0:hw]
    folds = 0.5 + 0.5 * np.sin(xx * (2 * math.pi / hw) + np.sin(yy * 0.2) * 0.6)
    s = yy / (hh - 1)                                   # 0 the hem .. 1 the rim at the mask
    shade = (0.9 + 0.2 * folds) * (1.0 - 0.3 * s)
    hem = np.clip(1.0 - yy / 2.0, 0.0, 1.0)
    for c in range(3):
        tex[hv:hv + hh, hu:hu + hw, c] = cloth[c] * shade + acol[c] * 0.25 * hem
        glow[hv:hv + hh, hu:hu + hw, c] = acol[c] * (0.7 * hem + 0.05 * (1.0 - s) * folds)
    tex[hv:hv + hh, hu:hu + hw, 3] = 255
    glow[hv:hv + hh, hu:hu + hw, 3] = 255
    # --- the cloak: dark cloth at its top thinning into smoke that glows faintly in the voice's colour
    du, dv, dw, dh = DRAPE
    yy, xx = np.mgrid[0:dh, 0:dw]
    s = yy / (dh - 1)                                   # 0 its top .. 1 its end
    xs = (xx + 0.5) / dw
    folds = 0.5 + 0.5 * np.sin(xs * 2 * math.pi * 1.5 + np.sin(s * 4.0) * 0.7)
    puffs = 0.55 * value_noise(rng, dh, dw, 9, 9) + 0.3 * value_noise(rng, dh, dw, 5, 5) + 0.15 * value_noise(rng, dh, dw, 3, 3)
    body = 1.0 - np.clip((s - 0.22) / 0.7, 0.0, 1.0) ** 1.2
    density = np.clip(body + (puffs - 0.5) * 1.1 * np.clip((s - 0.15) / 0.35, 0.0, 1.0), 0.0, 1.0)
    sides = np.clip(np.minimum(xs, 1.0 - xs) / 0.22, 0.0, 1.0)
    density *= sides ** (0.6 + 1.2 * s)
    density = np.where(density < 0.12, 0.0, density)
    smoke = np.clip((s - 0.2) / 0.55, 0.0, 1.0)
    for c in range(3):
        tex[dv:dv + dh, du:du + dw, c] = cloth[c] * (0.85 + 0.25 * folds) * (1.0 - 0.3 * smoke) + acol[c] * 0.28 * smoke
        glow[dv:dv + dh, du:du + dw, c] = acol[c] * (0.03 * (1.0 - smoke) + 0.36 * smoke * (0.5 + 0.5 * puffs)) * density
    tex[dv:dv + dh, du:du + dw, 3] = 255.0 * density
    glow[dv:dv + dh, du:du + dw, 3] = 255
    # --- swatches
    sw = {"hole": cloth * 0.5, "inside": cloth * 0.9, "edge": cloth * 0.8, "fracture": tint * 0.72, "clear": np.zeros(3)}
    sg = {"hole": gcol * 0.55, "inside": np.zeros(3), "edge": np.zeros(3), "fracture": tint * 0.06, "clear": np.zeros(3)}
    for name, (u, vv) in PATCH.items():
        tex[vv:vv + 2, u:u + 2, :3] = sw[name]
        tex[vv:vv + 2, u:u + 2, 3] = 0 if name == "clear" else 255
        glow[vv:vv + 2, u:u + 2, :3] = sg[name] * (0.2 if broken else 1.0)
        glow[vv:vv + 2, u:u + 2, 3] = 255
    return np.clip(tex, 0, 255), np.clip(glow, 0, 255)


# ------------------------------------------------------------------ animations

def _t(t):
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def keys(times, fn):
    return {_t(t): [round(float(a), 3) for a in fn(t)] for t in times}


def loop(length, steps):
    return [length * i / steps for i in range(steps + 1)]


def anim(bones: dict, length: float, looping):
    return {"loop": looping, "animation_length": length, "bones": bones}


BEAT = 0.6
DRAPES = [f"drape_{n}_0" for n in ("l", "c", "r")]
HUM_OPEN = 0.32                 # how far a humming (or silent) mask's lips part: a slit onto the light inside


def lips(open_amount_fn, times):
    """The two lips closing the mouth's O: open 0 shut (they meet on the mouth line), 1 wide open (each shrunk to its rim)."""
    return {
        "lip_upper": {"scale": keys(times, lambda t: (1.0, max(0.08, 1.0 - 0.9 * open_amount_fn(t)), 1.0))},
        "lip_lower": {"scale": keys(times, lambda t: (1.0, max(0.08, 1.0 - 0.9 * open_amount_fn(t)), 1.0))},
    }


def cloak(fn, times):
    """The cloak's length (1 hanging, less crumpled on the floor) and the hood's size, through a pose."""
    out = {d: {"scale": keys(times, lambda t: (1.0, fn(t), 1.0))} for d in DRAPES}
    out["hood"] = {"scale": keys(times, lambda t: (0.9 + 0.1 * fn(t),) * 3)}
    return out


def ease(u):
    u = max(0.0, min(1.0, u))
    return u * u * (3 - 2 * u)


def shard_moves(meta):
    """Where each shard lies when the mask is broken: pulled apart from the middle, turned a little; two gone."""
    rng = rng_for("unsung_shard_moves")
    shards = meta["shards"]
    out = {}
    order = sorted(shards, key=lambda k: -meta["centroid"][k][1])
    gone = {order[1], order[-2]} if len(order) > 3 else set()
    for k in shards:
        cx, cy = meta["centroid"][k]
        r = math.hypot(cx, cy) + 1e-6
        push = 1.4 + 1.2 * float(rng.random())
        pos = (cx / r * push, cy / r * push, -0.6 - 1.2 * float(rng.random()))
        rot = tuple(float(rng.normal(0, 9.0)) for _ in range(3))
        out[k] = (pos, rot, k in gone)
    return out


def animations(meta):
    A = {}
    two = 2 * BEAT
    lie = 0.3                   # the cloak crumpled on the floor
    A["float"] = anim({
        "mask": {"rotation": keys(loop(2 * two, 8), lambda t: (2.5 * math.sin(2 * math.pi * t / (2 * two)), 0, 1.5 * math.sin(2 * math.pi * t / (2 * two) + 1))),
                 "position": keys(loop(2 * two, 8), lambda t: (0, 0.6 * math.sin(2 * math.pi * t / (2 * two)), 0))},
        **lips(lambda t: HUM_OPEN, [0, 2 * two]),
    }, 2 * two, True)
    A["sing"] = anim({
        "mask": {"rotation": keys(loop(two, 8), lambda t: (-9 + 2.5 * math.sin(2 * math.pi * t / two), 0, 0)),
                 "position": keys(loop(two, 8), lambda t: (0, 0.5 * math.sin(2 * math.pi * t / two), -0.5))},
        **lips(lambda t: 0.74 + 0.26 * math.cos(2 * math.pi * t / BEAT), loop(two, 8)),
    }, two, True)
    A["inhale"] = anim({
        "mask": {"rotation": keys([0, BEAT], lambda t: (-15 * t / BEAT, 0, 0)),
                 "scale": keys([0, BEAT], lambda t: (1 + 0.07 * t / BEAT,) * 3)},
        **lips(lambda t: HUM_OPEN + (0.6 - HUM_OPEN) * t / BEAT, [0, BEAT]),
    }, BEAT, "hold_on_last_frame")
    A["drop"] = anim({
        "mask": {"rotation": keys([0, 0.3], lambda t: (28 * t / 0.3, 0, 0))},
        **lips(lambda t: HUM_OPEN + (0.85 - HUM_OPEN) * t / 0.3, [0, 0.3]),
    }, 0.3, "hold_on_last_frame")
    rest_rot = (-72.0, 0.0, 6.0)
    A["rest"] = anim({
        "mask": {"rotation": keys(loop(4.8, 4), lambda t: (rest_rot[0] + 1.5 * math.sin(2 * math.pi * t / 4.8), 0, rest_rot[2]))},
        **lips(lambda t: 0.12, [0, 4.8]),
        **cloak(lambda t: lie, [0, 4.8]),
    }, 4.8, True)
    A["rise"] = anim({
        "mask": {"rotation": keys(loop(two, 6), lambda t: tuple(r * (1 - ease(t / two)) for r in rest_rot))},
        **lips(lambda t: max(HUM_OPEN, math.sin(math.pi * t / two)), loop(two, 6)),
        **cloak(lambda t: lie + (1.0 - lie) * ease(t / two), loop(two, 6)),
    }, two, False)
    A["fall"] = anim({
        "mask": {"rotation": keys(loop(0.4, 4), lambda t: (-55 * ease(t / 0.4), 0, 14 * ease(t / 0.4)))},
        **lips(lambda t: HUM_OPEN, [0, 0.4]),
        **cloak(lambda t: 1.0 - (1.0 - lie) * ease(t / 0.4), loop(0.4, 4)),
    }, 0.4, False)
    A["fallen"] = anim({
        "mask": {"rotation": keys(loop(1.2, 4), lambda t: (-55 + 1.5 * math.sin(2 * math.pi * t / 1.2), 0, 14))},
        **lips(lambda t: HUM_OPEN, [0, 1.2]),
        **cloak(lambda t: lie, [0, 1.2]),
    }, 1.2, True)
    # broken: the shards part, two fly off, the light inside goes out and the hood and cloak dissolve
    moves = shard_moves(meta)

    def broken_bones(u, times):
        bones = {"mask": {"rotation": keys(times, lambda t: (-84 * ease(u(t)), 0, -9 * ease(u(t))))}}
        for k, (pos, rot, gone) in moves.items():
            far = 3.5 if gone else 1.0
            bones[f"shard_{k}"] = {
                "position": keys(times, lambda t, pos=pos, far=far: tuple(p * far * ease(u(t)) for p in pos)),
                "rotation": keys(times, lambda t, rot=rot, far=far: tuple(r * far * ease(u(t)) for r in rot)),
            }
            if gone:
                bones[f"shard_{k}"]["scale"] = keys(times, lambda t: (max(0.0, 1.0 - ease(u(t) * 1.25)),) * 3)
        fade = keys(times, lambda t: (max(0.0, 1.0 - ease(u(t) * 1.4)),) * 3)
        for b in ["inner", "hood"] + DRAPES:
            bones[b] = {"scale": fade}
        bones.update(lips(lambda t: 0.6, [times[0], times[-1]]))
        return bones
    A["shatter"] = anim(broken_bones(lambda t: t / 0.5, loop(0.5, 5)), 0.5, False)
    A["broken"] = anim(broken_bones(lambda t: 1.0, [0.0, 1.0]), 1.0, True)
    # the shroud: the cloak sways, slow as smoke in water
    period = 4 * BEAT
    shroud = {}
    for i, name in enumerate(("l", "c", "r")):
        for j in range(2):
            ph = i * 1.3 + j * 1.1

            def rot(t, ph=ph, j=j):
                return (-(4.0 + 3.0 * j) - (3.0 + 4.0 * j) * math.sin(2 * math.pi * t / period - ph),
                        0.0,
                        (2.0 + 2.5 * j) * math.sin(2 * math.pi * t / (2 * period) + ph))
            shroud[f"drape_{name}_{j}"] = {"rotation": keys(loop(2 * period, 16), rot)}
    A["shroud"] = anim(shroud, 2 * period, True)
    return {"format_version": "1.8.0", "animations": {k: A[k] for k in sorted(A)}}


# ------------------------------------------------------------------ blocks, the item, the FX

def noise_tile(rng, size=16, lo=0.0, hi=1.0):
    base = rng.random((size // 4 + 1, size // 4 + 1))
    up = np.kron(base, np.ones((4, 4)))[:size, :size]
    fine = rng.random((size, size))
    return lo + (hi - lo) * (0.6 * up + 0.4 * fine)


def solid(rgb_arr):
    img = np.zeros(rgb_arr.shape[:2] + (4,), dtype=np.float64)
    img[..., :3] = rgb_arr
    img[..., 3] = 255
    return img


def basalt(rng, cracked=True):
    n = noise_tile(rng)
    col = np.stack([28 + 22 * n, 24 + 18 * n, 36 + 26 * n], axis=-1)
    if cracked:
        x, y = 3, 2
        for _ in range(14):
            col[y % 16, x % 16] = (12, 10, 18)
            x += int(rng.integers(-1, 2)) + 1
            y += int(rng.integers(0, 2))
    return solid(col)


def lichen_window(rng, hue, lit):
    n = noise_tile(rng)
    glass = np.stack([22 + 16 * n, 12 + 10 * n, 34 + 22 * n], axis=-1)
    # lead lines: a diamond lattice
    yy, xx = np.mgrid[0:16, 0:16]
    lead = ((xx + yy) % 8 == 0) | ((xx - yy) % 8 == 0)
    glass[lead] = (14, 10, 18)
    colour = np.array((255, 70, 200) if hue == "magenta" else (70, 240, 220), dtype=np.float64)
    # lichen tendrils glowing behind the glass
    tend = np.zeros((16, 16))
    for k in range(5):
        x = rng.uniform(0, 16)
        y = 16.0
        h = -math.pi / 2 + rng.normal(0, 0.4)
        for _ in range(22):
            i, j = int(x) % 16, int(y) % 16
            tend[j, i] = max(tend[j, i], 1.0 - _ / 26.0)
            h += rng.normal(0, 0.5)
            x += math.cos(h) * 0.8
            y += math.sin(h) * 0.8
            if y < 0:
                break
    blob = noise_tile(rng) > 0.72
    tend = np.maximum(tend, blob * 0.55)
    if lit:
        col = glass * 0.7 + tend[..., None] * colour * 0.95 + (1 - tend[..., None]) * colour * 0.10
    else:
        col = glass * 0.8 + tend[..., None] * colour * 0.16
    col[lead] = (14, 10, 18)
    return solid(col)


def silence_circle(rng, lit):
    n = noise_tile(rng)
    if lit:
        # the glass still dark, the light in it: the round glow and its ring are drawn over it by the client
        col = np.stack([70 + 40 * n, 74 + 42 * n, 104 + 46 * n], axis=-1)
        spark = rng.random((16, 16)) > 0.9
        col[spark] = (235, 240, 255)
        img = solid(col)
        img[0, :, :3] = img[0, :, :3] * 0.8
        img[:, 0, :3] = img[:, 0, :3] * 0.8
        return img
    # at rest a pale inlay of nacre in the dark floor, faintly veined, seamless (the eight read as discs)
    yy, xx = np.mgrid[0:16, 0:16]
    vein = 0.5 + 0.5 * np.sin((xx + yy * 0.6) * 0.8 + np.sin(yy * 0.9) * 1.6)
    col = np.stack([84 + 14 * n + 8 * vein, 80 + 12 * n + 6 * vein, 104 + 16 * n + 10 * vein], axis=-1)
    return solid(col)


def abyss_mote():
    """The Rift Abyss's ambient mote: a soft round dot, white (the particle tints it)."""
    size = 8
    yy, xx = np.mgrid[0:size, 0:size]
    r = np.hypot(xx + 0.5 - size / 2, yy + 0.5 - size / 2) / (size / 2)
    img = np.zeros((size, size, 4))
    img[..., :3] = 255
    img[..., 3] = np.clip(1.0 - r, 0.0, 1.0) ** 1.3 * 255
    return img


def pages(rng):
    n = noise_tile(rng, lo=0.0, hi=1.0)
    col = np.stack([226 + 16 * n, 220 + 16 * n, 204 + 16 * n], axis=-1)
    col[:, 7:9] = col[:, 7:9] * 0.78          # the book's spine in the middle
    col[0, :] *= 0.85
    col[15, :] *= 0.85
    return solid(col)


def pendant():
    img = np.zeros((16, 16, 4))
    # a thin silver chain round the top, a porcelain drop with three gems of the voices
    chain = [(4, 1), (5, 1), (6, 0), (7, 0), (8, 0), (9, 0), (10, 1), (11, 1), (3, 2), (12, 2), (3, 3), (12, 3), (4, 4), (11, 4),
             (5, 5), (10, 5), (6, 6), (9, 6)]
    for x, y in chain:
        img[y, x] = (176, 182, 196, 255)
    for y in range(6, 15):
        half = [2, 3, 3, 4, 4, 4, 3, 3, 2][y - 6]
        for x in range(8 - half, 8 + half):
            shade = 236 - (abs(x - 7.5) * 9) - (y - 6) * 3
            img[y, x] = (shade, shade - 4, shade - 10, 255)
        img[y, 8 - half] = (120, 118, 130, 255)
        img[y, 8 + half - 1] = (120, 118, 130, 255)
    img[14, 6:10] = (120, 118, 130, 255)
    img[9, 6] = (255, 206, 110, 255)
    img[10, 8] = (64, 224, 208, 255)
    img[12, 7] = (255, 70, 190, 255)
    img[8, 7] = (255, 255, 255, 255)
    return img


def note_ring():
    size = 64
    yy, xx = np.mgrid[0:size, 0:size]
    r = np.hypot(xx + 0.5 - size / 2, yy + 0.5 - size / 2) / (size / 2)
    a = np.clip(1.0 - np.abs(r - 0.82) / 0.07, 0.0, 1.0) ** 1.5 + 0.35 * np.clip(1.0 - np.abs(r - 0.82) / 0.2, 0.0, 1.0)
    img = np.zeros((size, size, 4))
    img[..., :3] = 255
    img[..., 3] = np.clip(a, 0, 1) * 255
    return img


def block_model_cube(name, texture, emissive=False):
    face = {"uv": [0, 0, 16, 16], "texture": "#all"}
    el = {"from": [0, 0, 0], "to": [16, 16, 16], "shade": not emissive,
          "faces": {d: dict(face, cullface=d) for d in ("north", "south", "east", "west", "up", "down")}}
    if emissive:
        el["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return {"parent": "minecraft:block/block", "textures": {"all": texture, "particle": texture}, "elements": [el]}


def box(fr, to, tex_map, uv_map=None, shade=True):
    faces = {}
    for d, t in tex_map.items():
        f = {"texture": t}
        if uv_map and d in uv_map:
            f["uv"] = uv_map[d]
        faces[d] = f
    return {"from": fr, "to": to, "shade": shade, "faces": faces}


def altar_model():
    s, t, p = "#side", "#top", "#pages"
    all6 = lambda tex: {d: tex for d in ("north", "south", "east", "west", "up", "down")}
    base = box([2, 0, 2], [14, 2, 14], all6(s))
    stem = box([5, 2, 5], [11, 12, 11], all6(s))
    # the slanted reading board, and the open hymnal on it (its readable side toward the reader: south in the model)
    board = box([1, 11, 2], [15, 13, 14], all6(t))
    board["rotation"] = {"angle": 22.5, "axis": "x", "origin": [8, 12, 8]}
    book_l = box([1.5, 13, 3.5], [7.8, 13.8, 12.5], {"up": p, "north": p, "south": p, "east": p, "west": p, "down": p},
                 {"up": [0, 0, 8, 16]})
    book_l["rotation"] = {"angle": 22.5, "axis": "x", "origin": [8, 12, 8]}
    book_r = box([8.2, 13, 3.5], [14.5, 13.8, 12.5], {"up": p, "north": p, "south": p, "east": p, "west": p, "down": p},
                 {"up": [8, 0, 16, 16]})
    book_r["rotation"] = {"angle": 22.5, "axis": "x", "origin": [8, 12, 8]}
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"side": "cosmicbreach:block/hymnal_altar_side", "top": "cosmicbreach:block/hymnal_altar_top",
                         "pages": "cosmicbreach:block/hymnal_altar_pages", "particle": "cosmicbreach:block/hymnal_altar_side"},
            "elements": [base, stem, board, book_l, book_r]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    out = []
    old_shared = ASSETS / "animations" / "entity" / "unsung_mask.animation.json"
    if old_shared.exists():
        old_shared.unlink()                 # G8's one file for all three; each mask now has its own (its shards)
    for v in VOICES:
        bones, meta = build_mask(v)
        write_json(ASSETS / "geo" / "entity" / f"unsung_{v.name}.geo.json", geo_json(bones, f"unsung_{v.name}"))
        write_json(ASSETS / "animations" / "entity" / f"unsung_{v.name}.animation.json", animations(meta))
        cubes = sum(len(b.cubes) for b in bones)
        for broken in (False, True):
            tex, glow = paint(v, broken)
            suffix = "_broken" if broken else ""
            save_png(tex, TEX / "entity" / f"unsung_{v.name}{suffix}.png")
            save_png(glow, TEX / "entity" / f"unsung_{v.name}{suffix}_glowmask.png")
        out.append(f"unsung_{v.name}: {cubes} cubes")
    rng = rng_for("unsung_blocks")
    save_png(basalt(rng), TEX / "block" / "hymnal_altar_side.png")
    save_png(basalt(rng, cracked=False), TEX / "block" / "hymnal_altar_top.png")
    save_png(pages(rng), TEX / "block" / "hymnal_altar_pages.png")
    for hue in ("magenta", "teal"):
        r = rng_for(f"lichen_window_{hue}")
        save_png(lichen_window(r, hue, True), TEX / "block" / f"lichen_window_{hue}.png")
        r = rng_for(f"lichen_window_{hue}")
        save_png(lichen_window(r, hue, False), TEX / "block" / f"lichen_window_{hue}_dim.png")
    save_png(silence_circle(rng_for("silence_circle"), False), TEX / "block" / "silence_circle.png")
    save_png(silence_circle(rng_for("silence_circle"), True), TEX / "block" / "silence_circle_lit.png")
    save_png(pendant(), TEX / "item" / "choir_pendant.png")
    save_png(note_ring(), TEX / "fx" / "note_ring.png")
    save_png(abyss_mote(), TEX / "particle" / "abyss_mote.png")
    write_json(ASSETS / "particles" / "abyss_mote.json", {"textures": ["cosmicbreach:abyss_mote"]})
    # models and states
    models = ASSETS / "models"
    states = ASSETS / "blockstates"
    write_json(models / "block" / "hymnal_altar.json", altar_model())
    write_json(states / "hymnal_altar.json", {"variants": {
        f"facing={f}": {"model": "cosmicbreach:block/hymnal_altar", **({"y": y} if y else {})}
        for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))}})
    variants = {}
    for hue in ("magenta", "teal"):
        write_json(models / "block" / f"lichen_window_{hue}.json", block_model_cube(hue, f"cosmicbreach:block/lichen_window_{hue}", emissive=True))
        write_json(models / "block" / f"lichen_window_{hue}_dim.json", block_model_cube(hue, f"cosmicbreach:block/lichen_window_{hue}_dim"))
        variants[f"hue={hue},lit=true"] = {"model": f"cosmicbreach:block/lichen_window_{hue}"}
        variants[f"hue={hue},lit=false"] = {"model": f"cosmicbreach:block/lichen_window_{hue}_dim"}
    write_json(states / "lichen_window.json", {"variants": variants})
    write_json(models / "block" / "silence_circle.json", block_model_cube("c", "cosmicbreach:block/silence_circle"))
    write_json(models / "block" / "silence_circle_lit.json", block_model_cube("c", "cosmicbreach:block/silence_circle_lit", emissive=True))
    write_json(states / "silence_circle.json", {"variants": {
        "lit=false": {"model": "cosmicbreach:block/silence_circle"}, "lit=true": {"model": "cosmicbreach:block/silence_circle_lit"}}})
    write_json(models / "item" / "choir_pendant.json", {"parent": "minecraft:item/generated",
                                                       "textures": {"layer0": "cosmicbreach:item/choir_pendant"}})
    for line in out:
        print("  ", line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
