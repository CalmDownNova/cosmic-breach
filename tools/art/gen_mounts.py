"""The celestial mounts (G6b, GDD 8.1): the Lumen Stag and the Drift Manta, for GeckoLib 4, with their gear.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/lumen_stag.geo.json, animations/entity/lumen_stag.animation.json
    textures/entity/lumen_stag.png, lumen_stag_glowmask.png (the antlers; the game scales it by trust),
        lumen_stag_gear_glowmask.png (the Comet Bridle's comet, the Halo Reins' collar)
    geo/entity/drift_manta.geo.json, animations/entity/drift_manta.animation.json
    textures/entity/drift_manta.png, drift_manta_glowmask.png (the song spots; the game flares them on each note),
        drift_manta_gear_glowmask.png (the Nebula Reins' orb, the Gale Fins' edges)
    textures/item/<gear>.png and models/item/<gear>.json for the nine items, the two spawn eggs' models
    textures/gui/sprites/container/mount/tack_slot.png (the inventory's tack slot)

Conventions as the other GeckoLib generators: 16 units a block, y up, the head points to -Z, the left is +X, box UV.
Thin parts are cubes laid along a segment ({@code seg}: a cube built along +Y from p0, turned so its far end meets p1).
Every gear piece is a bone named gear_<item id>[_part] that the game shows only while that piece is worn.

The Stag: a tall white stag, deep chest, slender legs, a white flag of a tail, a long neck held high, and crystal
antlers (turquoise, faceted, lit by the glowmask) branching from a curved main beam: brow tine forward, two more
forward and up, a back tine and a crown, about 1.2 blocks across. Back at 1.44 blocks, the saddle's seat at 1.5, the
antler crowns at about 3.2.

The Manta: a pale, flat diamond body with wide swept wings in three segments per side (the animations ripple them
from root to tip), rolled cephalic fins, a whip tail in two segments, a white belly, and a row of song spots along
each wing and down the back (not in pairs near the front: nothing that reads as eyes). About 3.4 blocks across and
2.5 long; the rider sits at 0.62 blocks.

Run:  python tools/art/gen_mounts.py   (then preview_mounts.py)
"""
from __future__ import annotations

import math

import numpy as np

from common import ASSETS, TEX, rel, rgb, save_png
from gen_colossus import Bone, Cube, face_rects, geo_json, kf, loop_times, pack_uvs, pretty_json, texel_point, write_json, write_text

STAG_TEX = (128, 128)
MANTA_TEX = (128, 128)


# ------------------------------------------------------------------ geometry helpers
def box(cx, cy, cz, w, h, d, paint, **kw):
    """A cube by its middle."""
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), paint, **kw)


def span(x0, x1, y0, y1, z0, z1, paint, **kw):
    """A cube by its extents."""
    return Cube((min(x0, x1), min(y0, y1), min(z0, z1)), (abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)), paint, **kw)


def seg(p0, p1, t, paint, tz=None, inflate=0.0):
    """A cube t wide (tz deep) from p0 to p1: built along +Y from p0 and turned about p0 (GeckoLib's Z, Y, X order,
    x mirrored on load), so its far face's middle lands on p1. Checked against bedrock_model.world_quads."""
    p0 = np.array(p0, float)
    p1 = np.array(p1, float)
    d = p1 - p0
    length = float(np.linalg.norm(d))
    u = d / length
    a = math.acos(max(-1.0, min(1.0, u[1])))
    b = math.atan2(-u[0], u[2]) if math.sin(a) > 1e-6 else 0.0
    tz = t if tz is None else tz
    return Cube((p0[0] - t / 2, p0[1], p0[2] - tz / 2), (t, length, tz), paint,
                rotation=(-math.degrees(a), -math.degrees(b), 0.0), pivot=tuple(p0), inflate=inflate)


def mirror_x(c: Cube) -> Cube:
    """The same cube on the other side (x negated; a turned cube turns the other way about Y and Z)."""
    ox, oy, oz = c.origin
    sx, sy, sz = c.size
    rot = None
    piv = None
    if c.rotation:
        rx, ry, rz = c.rotation
        rot = (rx, -ry, -rz)
        piv = (-c.pivot[0], c.pivot[1], c.pivot[2])
    return Cube((-(ox + sx), oy, oz), (sx, sy, sz), c.paint, rotation=rot, pivot=piv, inflate=c.inflate)


def ring(cx, cy, cz, radius, bars, thick, paint, plane="xz", tilt=0.0):
    """A ring of bars round (cx, cy, cz). plane 'xz' lies flat; 'tilted' leans back by tilt degrees about X."""
    cubes = []
    side = 2 * radius * math.tan(math.pi / bars) + 0.3
    for k in range(bars):
        a = 2 * math.pi * k / bars
        px, pz = cx + radius * math.cos(a), cz + radius * math.sin(a)
        py = cy
        if tilt:
            t = math.radians(tilt)
            dz = pz - cz
            py, pz = cy + dz * math.sin(t), cz + dz * math.cos(t)
        cubes.append(Cube((px - thick / 2, py - thick / 2, pz - side / 2), (thick, thick, side), paint,
                          rotation=(tilt, -math.degrees(a), 0.0), pivot=(px, py, pz), inflate=-0.1))
    return cubes


# ------------------------------------------------------------------ the Lumen Stag
STAG_BACK = 23.0
SADDLE_TOP = 24.2


def stag_antler(side: int) -> list[Cube]:
    """One crystal antler (built for the left, +X, and mirrored), shaped like a red deer's: a main beam that sweeps up,
    out and back and turns forward at the top, the tines rising forward off its front (brow, bez, trez), and a crown of
    two points. Each tine thins to a pale point."""
    s = side

    def p(x, y, z):
        return (s * x, y, z)

    beam = [p(1.4, 33.6, -15.2), p(3.8, 37.4, -14.0), p(6.2, 41.2, -12.6), p(8.0, 45.0, -11.6), p(8.8, 48.4, -12.2)]
    widths = [1.6, 1.4, 1.25, 1.1]
    cubes = []
    for i in range(len(beam) - 1):
        cubes.append(seg(beam[i], beam[i + 1], widths[i], "antler", inflate=0.05))

    def tine(a, b, t):
        # the shaft and a thinner, paler point beyond it
        a = np.array(a, float)
        b = np.array(b, float)
        mid = a + (b - a) * 0.72
        return [seg(tuple(a), tuple(mid), t, "antler"), seg(tuple(mid), tuple(b), t * 0.7, "antler_tip")]

    cubes += tine(p(2.2, 34.8, -15.0), p(4.2, 37.0, -19.4), 1.0)    # brow tine, forward over the brow
    cubes += tine(p(3.6, 37.0, -14.2), p(6.4, 40.2, -18.0), 0.95)   # bez tine, forward and up
    cubes += tine(p(6.0, 41.0, -12.8), p(9.6, 44.4, -15.8), 0.9)    # trez tine
    cubes += tine(p(8.2, 46.2, -11.8), p(11.6, 49.6, -13.8), 0.85)  # the crown: forward and out
    cubes += tine(p(8.2, 46.4, -11.8), p(10.0, 50.0, -8.6), 0.85)    # the crown: back
    cubes += tine(p(8.8, 48.4, -12.2), p(9.0, 51.4, -12.6), 0.9)    # the beam's own tip
    return cubes


def stag_leg(name: str, side: int, front: bool) -> list[Bone]:
    x = side * 2.9
    if front:
        top, knee, foot = (x, 16.5, -8.4), (x, 8.2, -8.2), (x, 1.3, -8.5)
        upper = [seg(top, knee, 2.5, "coat", tz=3.0), box(x, 14.5, -8.4, 3.0, 4.0, 3.4, "coat")]
        lower = [seg(knee, foot, 1.5, "leg", tz=1.7), box(x, 0.7, -8.7, 2.0, 1.4, 2.4, "hoof")]
        return [Bone(f"leg_{name}", "body", top, None, upper), Bone(f"shin_{name}", f"leg_{name}", knee, None, lower)]
    hip, stifle, hock, foot = (x, 19.0, 9.2), (x, 11.2, 10.4), (x, 7.2, 12.6), (x, 1.3, 11.9)
    upper = [seg(hip, stifle, 3.1, "coat", tz=4.6), seg(stifle, hock, 2.0, "coat", tz=2.4)]
    lower = [seg(hock, foot, 1.5, "leg", tz=1.7), box(x, 0.7, 11.7, 2.0, 1.4, 2.4, "hoof")]
    return [Bone(f"leg_{name}", "body", hip, None, upper), Bone(f"shin_{name}", f"leg_{name}", hock, None, lower)]


def stag_barding(metal: str, gid: str) -> list[Bone]:
    """Barding in one metal: chest plate and shoulder guards, a crupper over the rump, a crinet on the neck, a chanfron."""
    body = [
        span(-4.9, 4.9, 14.2, 21.4, -13.2, -12.1, metal),                      # peytral over the chest
        span(4.3, 4.9, 15.6, 21.8, -12.0, -3.0, metal), span(-4.9, -4.3, 15.6, 21.8, -12.0, -3.0, metal),
        span(-4.4, 4.4, 23.0, 23.7, 7.2, 13.4, metal),                        # crupper over the rump
        span(4.0, 4.6, 16.4, 22.8, 7.2, 13.2, metal), span(-4.6, -4.0, 16.4, 22.8, 7.2, 13.2, metal),
        span(-1.2, 1.2, 17.0, 19.4, -13.6, -13.1, metal + "_trim"),           # a boss on the peytral
    ]
    neck = [seg((0, 21.6, -6.4), (0, 30.2, -12.6), 5.2, metal, tz=1.2)]
    head = [span(-2.1, 2.1, 34.0, 34.8, -20.4, -14.0, metal), span(-0.8, 0.8, 34.7, 35.4, -18.6, -16.4, metal + "_trim")]
    return [Bone(f"gear_{gid}_body", "body", (0, 18, 0), None, body),
            Bone(f"gear_{gid}_neck", "neck", (0, 20, -9.5), None, neck),
            Bone(f"gear_{gid}_head", "head", (0, 30, -15.5), None, head)]


def build_stag() -> list[Bone]:
    bones = [Bone("root", None, (0, 0, 0))]
    body = [
        span(-4.25, 4.25, 13.6, 23.0, -12.4, -2.0, "coat"),        # the deep chest
        span(-3.8, 3.8, 14.6, 22.6, -3.0, 8.0, "coat"),            # the barrel
        span(-4.0, 4.0, 14.6, 23.0, 7.0, 13.2, "coat"),            # the rump
        span(-3.0, 3.0, 23.0, 23.8, -11.6, -5.6, "coat"),          # the withers
    ]
    bones.append(Bone("body", "root", (0, 18, 0), None, body))
    bones.append(Bone("tail", "body", (0, 21.5, 13.0), None, [seg((0, 20.6, 12.8), (0, 24.6, 15.4), 2.6, "tail", tz=1.6)]))
    bones.append(Bone("neck", "body", (0, 20, -9.5), None, [
        seg((0, 19.2, -8.0), (0, 30.2, -15.0), 4.6, "coat", tz=5.0),
        seg((0, 17.4, -10.6), (0, 26.6, -16.2), 5.0, "ruff", tz=2.6),
    ]))
    head = [
        span(-2.5, 2.5, 29.0, 34.0, -19.4, -13.4, "coat"),        # the skull
        span(-1.8, 1.8, 28.7, 32.1, -24.2, -19.2, "muzzle"),      # the long muzzle
        span(-1.5, 1.5, 29.8, 31.6, -24.8, -24.2, "nose"),
        box(2.6, 32.0, -17.6, 1, 1, 1, "eye", inflate=-0.22), box(-2.6, 32.0, -17.6, 1, 1, 1, "eye", inflate=-0.22),
    ]
    for side in (1, -1):
        head.append(seg((side * 2.0, 33.2, -14.8), (side * 5.8, 35.4, -13.2), 1.2, "ear", tz=2.4))
    bones.append(Bone("head", "neck", (0, 30, -15.5), None, head))
    bones.append(Bone("antler_left", "head", (1.4, 33.6, -15.4), None, stag_antler(1)))
    bones.append(Bone("antler_right", "head", (-1.4, 33.6, -15.4), None, stag_antler(-1)))
    for name, side, front in (("front_left", 1, True), ("front_right", -1, True), ("back_left", 1, False), ("back_right", -1, False)):
        bones += stag_leg(name, side, front)
    # gear: the Astral Saddle
    saddle = [
        span(-3.9, 3.9, STAG_BACK, SADDLE_TOP, -6.2, 2.2, "saddle"),
        span(-1.5, 1.5, 23.4, 25.8, -7.0, -5.4, "gold"),                 # pommel
        span(-2.6, 2.6, 23.4, 26.0, 1.6, 2.9, "gold"),                   # cantle
        span(4.0, 4.6, 17.6, 23.6, -5.2, 1.2, "flap"), span(-4.6, -4.0, 17.6, 23.6, -5.2, 1.2, "flap"),       # flaps
        span(4.5, 5.1, 14.6, 17.4, -2.8, -1.4, "gold"), span(-5.1, -4.5, 14.6, 17.4, -2.8, -1.4, "gold"),     # stirrups
        span(-3.95, 3.95, 17.0, 17.8, -3.6, -1.8, "strap"),              # girth under the belly... (sides)
    ]
    bones.append(Bone("gear_astral_saddle", "body", (0, 18, 0), None, saddle))
    bones += stag_barding("starsteel", "starsteel_barding")
    bones += stag_barding("nebulite", "nebulite_barding")
    # the Comet Bridle: noseband, cheek straps, browband and a comet on the brow
    bridle = [
        span(-2.1, 2.1, 30.2, 31.0, -22.4, -21.6, "strap"), span(-2.1, 2.1, 28.5, 29.2, -22.4, -21.6, "strap"),
        span(1.8, 2.3, 28.5, 31.0, -22.4, -18.8, "strap"), span(-2.3, -1.8, 28.5, 31.0, -22.4, -18.8, "strap"),
        span(2.45, 2.9, 30.0, 34.2, -15.6, -14.8, "strap"), span(-2.9, -2.45, 30.0, 34.2, -15.6, -14.8, "strap"),
        span(-2.9, 2.9, 33.4, 34.2, -15.6, -14.8, "strap"),
        box(0, 33.0, -19.6, 1.6, 1.6, 1.0, "comet"),
        seg((0, 33.6, -19.2), (0, 35.4, -15.6), 0.8, "comet_tail", tz=0.6),
    ]
    bones.append(Bone("gear_comet_bridle", "head", (0, 30, -15.5), None, bridle))
    # the Halo Reins: a halo of gold round the neck's middle, reins from the muzzle back to it
    neck_mid = (0, 24.8, -11.6)
    halo_cubes = []
    ang = math.degrees(math.atan2(30.2 - 19.2, 15.0 - 8.0))      # the neck's climb, about 57 degrees
    for k in range(12):
        a = 2 * math.pi * k / 12
        # a point on a circle square to the neck's axis
        axis = np.array([0.0, math.sin(math.radians(ang)), -math.cos(math.radians(ang))])
        e1 = np.array([1.0, 0.0, 0.0])
        e2 = np.cross(axis, e1)
        c0 = np.array(neck_mid) + 3.6 * (math.cos(a) * e1 + math.sin(a) * e2)
        c1 = np.array(neck_mid) + 3.6 * (math.cos(a + 2 * math.pi / 12) * e1 + math.sin(a + 2 * math.pi / 12) * e2)
        halo_cubes.append(seg(tuple(c0), tuple(c1), 0.8, "halo", inflate=0.05))
    bones.append(Bone("gear_halo_reins_collar", "neck", (0, 20, -9.5), None, halo_cubes))
    reins = []
    for side in (1, -1):
        reins.append(seg((side * 2.0, 30.0, -21.6), (side * 3.0, 25.6, -12.0), 0.5, "gold_strap", tz=0.5))
    bones.append(Bone("gear_halo_reins_lines", "head", (0, 30, -15.5), None, reins))
    return bones


def stag_texel(c: Cube, face: str, col: int, row: int, w: int, h: int):
    """(colour, antler glow or None, gear glow or None)."""
    kind = c.paint
    top, bottom = face == "top", face == "bottom"
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    px, py, pz = texel_point(c, face, col, row)
    n = hash_noise(px, py, pz)
    if kind in ("coat", "ruff", "tail"):
        base = [rgb("#FBF9F3"), rgb("#F1EDE3"), rgb("#E3DDD0"), rgb("#CFC8B8")]
        idx = 0 if top else 3 if bottom else 1
        if not top and not bottom and n > 0.9:
            idx = 2
        if kind == "ruff":
            idx = max(0, idx - 1)
        colour = base[idx]
        if kind == "coat" and not bottom and n > 0.993:
            colour = rgb("#E8D39A")          # a gold fleck (the Reach's white stone with gold)
        return colour, None, None
    if kind == "leg":
        return ([rgb("#EAE5DA"), rgb("#DCD5C8"), rgb("#C9C1B2")][0 if top else 2 if bottom else 1]), None, None
    if kind == "hoof":
        return (rgb("#F2D58A") if top else rgb("#9C7A3C") if bottom else rgb("#D6AE5C") if n < 0.6 else rgb("#C39A48")), None, None
    if kind == "muzzle":
        return (rgb("#F4F0E8") if top else rgb("#DAD3C6") if bottom else rgb("#EAE4D8")), None, None
    if kind == "nose":
        return rgb("#6F6C69") if not top else rgb("#85817C"), None, None
    if kind == "eye":
        return rgb("#1E1F26"), None, None
    if kind == "ear":
        inner = face in ("front",)
        return (rgb("#D9C4BC") if inner else rgb("#F1EDE3") if not bottom else rgb("#DCD5C8")), None, None
    if kind == "antler_tip":
        return rgb("#EFFFFD"), (140, 250, 245), None
    if kind == "antler":
        # faceted crystal: bands along the length, bright edges, lit from within
        band = (int(py * 1.5 + pz * 0.7) % 3)
        ramp = [rgb("#E4FFFB"), rgb("#A5F0EA"), rgb("#70D8D8"), rgb("#4BB8C4")]
        idx = 0 if (edge and not bottom) else 1 + band
        colour = ramp[min(3, idx)]
        glow = (120, 245, 240) if idx == 0 else (70, 215, 220) if band == 0 else (40, 170, 195)   # cyan light, not white
        return colour, glow, None
    if kind == "saddle":
        if edge:
            return rgb("#D8B25A"), None, None
        return (rgb("#F6EFE2") if top else rgb("#E6DCC8") if not bottom else rgb("#CFC3AC")), None, None
    if kind == "gold":
        return (rgb("#F4D784") if top else rgb("#D4A94E") if not bottom else rgb("#A67E34")), None, None
    if kind in ("strap", "gold_strap"):
        if kind == "gold_strap":
            return rgb("#E2BD62"), None, (120, 96, 40)
        return (rgb("#3E4C6E") if not top else rgb("#52618A")), None, None
    if kind == "comet":
        return rgb("#FFFFFF"), None, (255, 255, 255)
    if kind == "comet_tail":
        return rgb("#CDEFFF"), None, (150, 210, 255)
    if kind == "halo":
        return rgb("#FFE9A6"), None, (255, 214, 120)
    if kind.startswith("starsteel") or kind.startswith("nebulite"):
        return plate_texel(kind, face, col, row, w, h)
    if kind == "flap":
        if row == h - 1 and face not in ("top", "bottom"):
            return rgb("#D6AE5C"), None, None
        return (rgb("#E9D8B4") if not bottom else rgb("#CDB78E")), None, None
    return rgb("#FF00FF"), None, None


def plate_texel(kind: str, face: str, col: int, row: int, w: int, h: int):
    """Barding plate: metal in lames (a darker line every third row), the lower edge trimmed; no frame."""
    neb = kind.startswith("nebulite")
    trim = kind.endswith("_trim")
    if neb:
        top_c, face_c, lame_c, trim_c = rgb("#A6ECF6"), rgb("#7FB8EA"), rgb("#6A86D6"), rgb("#C8F8FF")
    else:
        top_c, face_c, lame_c, trim_c = rgb("#F2F5F9"), rgb("#D3DBE5"), rgb("#AEB9C7"), rgb("#E6C66C")
    if trim:
        return trim_c, None, ((70, 180, 210) if neb else None)
    if face == "top":
        return top_c, None, None
    if face == "bottom":
        return lame_c, None, None
    if row == h - 1:
        return trim_c, None, None
    if row % 3 == 2:
        return lame_c, None, None
    return face_c, None, None


def hash_noise(x, y, z) -> float:
    h = math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453
    return h - math.floor(h)


# ------------------------------------------------------------------ the Drift Manta
BODY_Y = 7.4          # the wings' middle plane
RIDE_Y = 10.0         # the dorsal pad the rider sits on (0.62 blocks)


WING_ROOT = 7.0
WING_TIP = 28.0


def wing_edges(x: float):
    """Leading and trailing edge (z) and thickness of the wing at distance x from the middle."""
    f = max(0.0, min(1.0, (x - WING_ROOT) / (WING_TIP - WING_ROOT)))
    lead = -11.5 + 12.3 * f ** 1.25
    trail = 7.4 - 6.2 * f ** 0.8
    thick = 3.0 - 2.1 * f
    return lead, trail, thick


def manta_wing(side: int) -> list[Bone]:
    """Three segments of slabs 1.75 wide following a swept, thinning wing. Built for the left (+X) and mirrored."""
    s = side
    name = "left" if side > 0 else "right"
    parts = {1: [], 2: [], 3: []}
    step = 1.75
    x = WING_ROOT
    while x < WING_TIP - 1e-6:
        x1 = min(WING_TIP, x + step)
        lead, trail, thick = wing_edges((x + x1) / 2)
        k = 1 if x < 14.0 - 1e-6 else 2 if x < 21.0 - 1e-6 else 3
        parts[k].append(span(s * x, s * x1, BODY_Y - thick / 2, BODY_Y + thick / 2, lead, trail, "wing"))
        x = x1
    bones = [
        Bone(f"wing_{name}_1", "body", (s * WING_ROOT, BODY_Y, -2.0), None, parts[1]),
        Bone(f"wing_{name}_2", f"wing_{name}_1", (s * 14.0, BODY_Y, -2.0), None, parts[2]),
        Bone(f"wing_{name}_3", f"wing_{name}_2", (s * 21.0, BODY_Y, 0.0), None, parts[3]),
    ]
    # gear on the wings: Nebulite leading-edge guards, the Gale Fins at the tips
    lead, _, thick = wing_edges(10.5)
    bones.append(Bone(f"gear_nebulite_barding_wing_{name}", f"wing_{name}_1", (s * WING_ROOT, BODY_Y, -2.0), None, [
        span(s * 7.2, s * 13.8, BODY_Y - thick / 2 - 0.2, BODY_Y + thick / 2 + 0.3, lead - 1.2, lead + 0.6, "nebulite"),
    ]))
    bones.append(Bone(f"gear_gale_fins_{name}", f"wing_{name}_3", (s * 21.0, BODY_Y, 0.0), None, [
        span(s * 27.4, s * 31.4, BODY_Y - 0.4, BODY_Y + 0.4, 0.8, 4.6, "fin"),
        span(s * 24.6, s * 27.8, BODY_Y - 0.35, BODY_Y + 0.35, 1.6, 5.8, "fin"),
    ]))
    return bones


def build_manta() -> list[Bone]:
    bones = [Bone("root", None, (0, 0, 0)), Bone("tilt", "root", (0, BODY_Y, -1.0))]
    body = [
        span(-7.0, 7.0, 4.8, 9.6, -12.0, 8.0, "body"),               # the broad body disc
        span(-5.8, 5.8, 5.2, 9.2, -15.0, -12.0, "head"),             # the head, broad and blunt
        span(-4.2, 4.2, 9.6, RIDE_Y, -9.0, 5.0, "body"),             # the dorsal rise the rider sits on
        span(-4.6, 4.6, 5.4, 8.8, 8.0, 11.4, "body"),                # tapering to the tail
        box(5.9, 8.0, -13.2, 1, 1, 1, "eye", inflate=-0.25), box(-5.9, 8.0, -13.2, 1, 1, 1, "eye", inflate=-0.25),
    ]
    bones.append(Bone("body", "tilt", (0, BODY_Y, -1.0), None, body))
    for side in (1, -1):
        nm = "left" if side > 0 else "right"
        bones.append(Bone(f"cephalic_{nm}", "body", (side * 4.6, 7.0, -14.8), None, [
            seg((side * 4.8, 7.0, -14.6), (side * 4.2, 5.6, -18.6), 2.4, "cephalic", tz=1.0),
        ]))
        bones.append(Bone(f"pelvic_{nm}", "body", (side * 5.0, 6.2, 8.0), None, [
            seg((side * 5.2, 6.4, 7.6), (side * 8.0, 6.0, 11.8), 1.0, "wing", tz=2.6, inflate=-0.1),
        ]))
        bones += manta_wing(side)
    bones.append(Bone("tail_1", "body", (0, 7.0, 11.4), None, [seg((0, 7.0, 11.0), (0, 7.2, 19.4), 1.3, "tail", inflate=-0.1)]))
    bones.append(Bone("tail_2", "tail_1", (0, 7.2, 19.4), None, [seg((0, 7.2, 19.4), (0, 7.6, 30.4), 1.0, "tail", inflate=-0.25)]))
    # gear: the Drift Harness (a pad and a girth), Nebulite barding on the head, the Nebula Reins with their orb
    harness = [
        span(-3.4, 3.4, RIDE_Y, RIDE_Y + 0.9, -8.0, 3.0, "pad"),
        span(-0.8, 0.8, RIDE_Y + 0.6, RIDE_Y + 1.4, -8.4, -7.6, "harness"),         # a low handhold at the front
        span(-7.4, 7.4, 4.4, 5.0, -3.6, -2.4, "harness"),                          # girth under the body
        span(7.0, 7.6, 4.4, 9.8, -3.6, -2.4, "harness"), span(-7.6, -7.0, 4.4, 9.8, -3.6, -2.4, "harness"),
        span(3.5, 7.4, 9.5, 10.1, -3.6, -2.4, "harness"), span(-7.4, -3.5, 9.5, 10.1, -3.6, -2.4, "harness"),
        span(3.5, 4.1, 9.9, 10.8, -4.0, -2.0, "buckle"), span(-4.1, -3.5, 9.9, 10.8, -4.0, -2.0, "buckle"),
    ]
    bones.append(Bone("gear_drift_harness", "body", (0, BODY_Y, -1.0), None, harness))
    bones.append(Bone("gear_nebulite_barding_head", "body", (0, BODY_Y, -1.0), None, [
        span(-5.4, 5.4, 9.2, 9.9, -15.2, -10.0, "nebulite"), span(-5.5, 5.5, 8.8, 9.3, -15.5, -15.0, "nebulite_trim"),
    ]))
    reins = [box(0, 10.4, -13.4, 1.4, 1.4, 1.4, "orb")]                            # the orb rides on the brow
    for side in (1, -1):
        reins.append(seg((side * 4.4, 7.0, -17.8), (side * 0.9, RIDE_Y + 1.2, -8.0), 0.5, "rein", tz=0.5))
    bones.append(Bone("gear_nebula_reins", "body", (0, BODY_Y, -1.0), None, reins))
    return bones


def on_spot_line(x: float, z: float) -> bool:
    """The song spots: round spots two to three units across in a row along each wing, curving back with the sweep
    (none on the body or near the head: nothing that reads as eyes or a letter)."""
    ax = abs(x)
    if ax > 8.0:
        lead, trail, _ = wing_edges(ax)
        zc = lead + 0.45 * (trail - lead)
        k = round((ax - 9.5) / 3.6)
        cx = 9.5 + 3.6 * k
        size = 1.45 - 0.08 * k
        return cx < WING_TIP - 2.0 and (ax - cx) ** 2 + (z - zc) ** 2 < size ** 2
    return False


def near_spot(x: float, z: float) -> bool:
    """A thin darker ring round each song spot, so the spots stand out on the pale wing."""
    ax = abs(x)
    if ax <= 8.0:
        return False
    lead, trail, _ = wing_edges(ax)
    zc = lead + 0.45 * (trail - lead)
    k = round((ax - 9.5) / 3.6)
    cx = 9.5 + 3.6 * k
    size = 1.45 - 0.08 * k
    d2 = (ax - cx) ** 2 + (z - zc) ** 2
    return cx < WING_TIP - 2.0 and size ** 2 <= d2 < (size + 0.75) ** 2


def manta_texel(c: Cube, face: str, col: int, row: int, w: int, h: int):
    kind = c.paint
    top, bottom = face == "top", face == "bottom"
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    px, py, pz = texel_point(c, face, col, row)
    n = hash_noise(px, py, pz)
    if kind in ("body", "wing", "head"):
        if bottom:
            return rgb("#F7FBFD") if n < 0.8 else rgb("#EAF3F8"), None, None
        if kind == "head" and face == "front" and row >= h - 2:
            return rgb("#A9B7C3"), None, None       # the wide mouth, a soft shadow under the head
        if not top:
            return rgb("#BCCCD8") if kind == "wing" else rgb("#D2DDE6"), None, None
        # the back: pale silver, a little cooler toward the wingtips, faint dapples
        reach = min(1.0, abs(px) / 28.0)
        colour = np.array(rgb("#EEF4F8")) * (1 - reach) + np.array(rgb("#C6D6E2")) * reach
        if n > 0.9:
            colour = colour * 0.96
        if kind == "head" and pz < -13.4:
            colour = np.array(rgb("#DCE6EE"))
        if on_spot_line(px, pz):
            return rgb("#8FE6F2"), (150, 245, 255), None
        if near_spot(px, pz):
            return rgb("#B9C9D6"), None, None
        return tuple(int(v) for v in colour), None, None
    if kind == "cephalic":
        return (rgb("#C3D0DB") if top else rgb("#AEBDCA")), None, None      # the head's lobes, a shade under the body
    if kind == "tail":
        return (rgb("#D3DEE7") if top else rgb("#B7C6D2")), None, None
    if kind == "eye":
        return rgb("#2B3340"), None, None
    if kind == "pad":
        return (rgb("#5E6E86") if not edge else rgb("#C8D2DE")), None, None
    if kind == "harness":
        return rgb("#46536A"), None, None
    if kind == "buckle":
        return rgb("#D8E2EC") if top else rgb("#AEB9C6"), None, None
    if kind == "rein":
        return rgb("#3E4A62"), None, None
    if kind == "orb":
        return (rgb("#A07CE6") if top else rgb("#8A63D2")), None, (110, 60, 190)   # violet, lit, not white
    if kind == "fin":
        return (rgb("#B6F2FF") if not edge else rgb("#E8FDFF")), None, (70, 190, 220) if edge else (30, 110, 140)
    if kind.startswith("nebulite"):
        return plate_texel(kind, face, col, row, w, h)
    return rgb("#FF00FF"), None, None


def solidify(bones):
    """Box UV needs every side a whole texel: a side under one unit is built one unit and the cube shrunk back by a
    negative inflate (the same on every side, so the others grow to match). The drawn shape is unchanged."""
    for b in bones:
        for c in b.cubes:
            thin = min(c.size)
            if thin >= 1.0:
                continue
            k = (1.0 - thin) / 2.0
            c.origin = tuple(o - k for o in c.origin)
            c.size = tuple(sz + 2 * k for sz in c.size)
            c.inflate = round(c.inflate - k, 4)


def paint(bones, size, texel):
    tw, th = size
    tex = np.zeros((th, tw, 4), np.uint8)
    glow = np.zeros_like(tex)
    gear = np.zeros_like(tex)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(max(1, rh)):
                    for col in range(max(1, rw)):
                        colour, g, gg = texel(c, face, col, r, max(1, rw), max(1, rh))
                        y, x = rv + r, ru + col
                        if not (0 <= y < th and 0 <= x < tw):
                            continue
                        tex[y, x, :3] = colour
                        tex[y, x, 3] = 255
                        if g is not None:
                            glow[y, x, :3] = g
                            glow[y, x, 3] = 255
                        if gg is not None:
                            gear[y, x, :3] = gg
                            gear[y, x, 3] = 255
    return tex, glow, gear


# ------------------------------------------------------------------ animations
def stag_animations():
    out = {}
    L = 3.0
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0, per=L: math.sin(2 * math.pi * t / per + ph)
    out["idle"] = {"loop": True, "animation_length": L, "bones": {
        "body": {"position": kf(ts, lambda t: (0, 0.12 * s(t), 0))},
        "neck": {"rotation": kf(ts, lambda t: (1.5 * s(t, 0.8), 0, 0))},
        "tail": {"rotation": kf(ts, lambda t: (-6 + 8 * max(0.0, s(t, 0.0, 1.5)) ** 4, 0, 0))},
    }}
    # walk: a four-beat gait, legs 25 degrees each way, the lifted shin folding
    W = 1.0
    tw = loop_times(W, 16)
    walk = {}
    phases = {"front_left": 0.0, "back_right": 0.25, "front_right": 0.5, "back_left": 0.75}
    for leg, ph in phases.items():
        front = leg.startswith("front")
        walk[f"leg_{leg}"] = {"rotation": kf(tw, lambda t, ph=ph: (-22 * math.sin(2 * math.pi * (t / W + ph)), 0, 0))}
        walk[f"shin_{leg}"] = {"rotation": kf(tw, lambda t, ph=ph, front=front: (
            (18 if front else -14) * max(0.0, math.sin(2 * math.pi * (t / W + ph) + math.pi / 2)), 0, 0))}
    walk["body"] = {"position": kf(tw, lambda t: (0, 0.25 * math.sin(4 * math.pi * t / W), 0))}
    walk["neck"] = {"rotation": kf(tw, lambda t: (3 * math.sin(4 * math.pi * t / W + 0.6), 0, 0))}
    out["walk"] = {"loop": True, "animation_length": W, "bones": walk}
    # run: a bounding gallop; the front pair and the hind pair swing together, the body pitching between
    R = 0.55
    tr = loop_times(R, 16)
    run = {}
    for leg in ("front_left", "front_right", "back_left", "back_right"):
        front = leg.startswith("front")
        lag = 0.06 if leg.endswith("right") else 0.0
        ph = 0.0 if front else 0.5
        run[f"leg_{leg}"] = {"rotation": kf(tr, lambda t, ph=ph, lag=lag, front=front: (
            (-48 if front else -40) * math.sin(2 * math.pi * (t / R + ph + lag)), 0, 0))}
        run[f"shin_{leg}"] = {"rotation": kf(tr, lambda t, ph=ph, lag=lag, front=front: (
            (40 if front else -30) * max(0.0, math.sin(2 * math.pi * (t / R + ph + lag) - 0.9)), 0, 0))}
    run["body"] = {"rotation": kf(tr, lambda t: (5 * math.sin(2 * math.pi * t / R + 1.2), 0, 0)),
                   "position": kf(tr, lambda t: (0, 1.0 * max(0.0, math.sin(2 * math.pi * t / R + 0.4)), 0))}
    run["neck"] = {"rotation": kf(tr, lambda t: (-8 + 6 * math.sin(2 * math.pi * t / R + 2.6), 0, 0))}
    run["tail"] = {"rotation": kf(tr, lambda t: (-18, 0, 0))}
    out["run"] = {"loop": True, "animation_length": R, "bones": run}
    # leap: legs tucked, rising
    out["leap"] = {"loop": True, "animation_length": 1.0, "bones": {
        "leg_front_left": {"rotation": [-62, 0, 0]}, "leg_front_right": {"rotation": [-58, 0, 0]},
        "shin_front_left": {"rotation": [95, 0, 0]}, "shin_front_right": {"rotation": [90, 0, 0]},
        "leg_back_left": {"rotation": [35, 0, 0]}, "leg_back_right": {"rotation": [38, 0, 0]},
        "shin_back_left": {"rotation": [-60, 0, 0]}, "shin_back_right": {"rotation": [-55, 0, 0]},
        "neck": {"rotation": [-10, 0, 0]}, "tail": {"rotation": [-20, 0, 0]},
    }}
    # glide: legs stretched fore and aft like a deer's leap held in the air, a slow float
    G = 2.0
    tg = loop_times(G, 8)
    out["glide"] = {"loop": True, "animation_length": G, "bones": {
        "leg_front_left": {"rotation": kf(tg, lambda t: (-72 + 3 * s(t, 0, G), 0, 0))},
        "leg_front_right": {"rotation": kf(tg, lambda t: (-66 + 3 * s(t, 0.5, G), 0, 0))},
        "shin_front_left": {"rotation": [12, 0, 0]}, "shin_front_right": {"rotation": [16, 0, 0]},
        "leg_back_left": {"rotation": kf(tg, lambda t: (58 + 3 * s(t, 1.0, G), 0, 0))},
        "leg_back_right": {"rotation": kf(tg, lambda t: (54 + 3 * s(t, 1.5, G), 0, 0))},
        "shin_back_left": {"rotation": [-12, 0, 0]}, "shin_back_right": {"rotation": [-10, 0, 0]},
        "neck": {"rotation": kf(tg, lambda t: (-14 + 2 * s(t, 0, G), 0, 0))},
        "tail": {"rotation": [-26, 0, 0]},
        "body": {"position": kf(tg, lambda t: (0, 0.3 * s(t, 0, G), 0))},
    }}
    # eat: the neck lowered to the hand, a chewing nod
    E = 1.0
    te = loop_times(E, 8)
    out["eat"] = {"loop": True, "animation_length": E, "bones": {
        "neck": {"rotation": kf(te, lambda t: (52 + 3 * math.sin(4 * math.pi * t / E), 0, 0))},
    }}
    return {"format_version": "1.8.0", "animations": out}


def manta_animations():
    out = {}

    def wings(period, amps, lag, base=(0.0, 0.0, 0.0), steps=16):
        ts = loop_times(period, steps)
        bones = {}
        for side, sign in (("left", -1), ("right", 1)):  # a positive angle lifts both tips
            for k in (1, 2, 3):
                a, b0 = amps[k - 1], base[k - 1]
                bones[f"wing_{side}_{k}"] = {"rotation": kf(ts, lambda t, a=a, b0=b0, k=k, sign=sign: (
                    0, 0, sign * (b0 + a * math.sin(2 * math.pi * t / period - lag * (k - 1)))))}
        return bones, ts

    bones, ts = wings(2.4, (6, 9, 12), 0.9)
    bones["body"] = {"position": kf(ts, lambda t: (0, 0.4 * math.sin(2 * math.pi * t / 2.4 - 1.2), 0))}
    bones["tail_1"] = {"rotation": kf(ts, lambda t: (0, 8 * math.sin(2 * math.pi * t / 2.4 - 1.5), 0))}
    bones["tail_2"] = {"rotation": kf(ts, lambda t: (0, 12 * math.sin(2 * math.pi * t / 2.4 - 2.4), 0))}
    out["hover"] = {"loop": True, "animation_length": 2.4, "bones": bones}

    bones, ts = wings(1.4, (13, 19, 24), 1.0)
    bones["body"] = {"position": kf(ts, lambda t: (0, 0.7 * math.sin(2 * math.pi * t / 1.4 - 1.4), 0))}
    bones["tail_1"] = {"rotation": kf(ts, lambda t: (0, 10 * math.sin(2 * math.pi * t / 1.4 - 1.6), 0))}
    bones["tail_2"] = {"rotation": kf(ts, lambda t: (0, 16 * math.sin(2 * math.pi * t / 1.4 - 2.6), 0))}
    bones["cephalic_left"] = {"rotation": kf(ts, lambda t: (0, -6 * math.sin(2 * math.pi * t / 1.4), 0))}
    bones["cephalic_right"] = {"rotation": kf(ts, lambda t: (0, 6 * math.sin(2 * math.pi * t / 1.4), 0))}
    out["swim"] = {"loop": True, "animation_length": 1.4, "bones": bones}

    bones, ts = wings(3.0, (2, 3, 4), 0.8, base=(4, 5, 6))
    bones["tail_2"] = {"rotation": kf(ts, lambda t: (0, 5 * math.sin(2 * math.pi * t / 3.0), 0))}
    out["glide"] = {"loop": True, "animation_length": 3.0, "bones": bones}

    bones, ts = wings(4.0, (1, 1.5, 2), 0.6, base=(-6, -9, -12), steps=8)
    bones["tilt"] = {"position": [0, -4.6, 0]}
    bones["tail_1"] = {"rotation": [6, 0, 0]}
    out["rest"] = {"loop": True, "animation_length": 4.0, "bones": bones}
    return {"format_version": "1.8.0", "animations": out}


# ------------------------------------------------------------------ item icons
from blockart import outline, sprite  # noqa: E402

ICONS = {
    "astral_saddle": (["................",
                       "................",
                       "................",
                       "....GG....GG....",
                       "...GWWG..GWWG...",
                       "...GWWWGGWWWG...",
                       "..GWWWWWWWWWWG..",
                       "..GWWWWSSWWWWG..",
                       "..GWWWSYYSWWWG..",
                       "...GWWWSSWWWG...",
                       "...GLWWWWWWLG...",
                       "....L.GGGG.L....",
                       "....L......L....",
                       "...YY......YY...",
                       "................",
                       "................"],
                      {"G": "#C9A04C", "W": "#F4EDE0", "S": "#E6C66C", "Y": "#FFE9A6", "L": "#8E6F34"}),
    "starsteel_barding": (["................",
                           "................",
                           "..........GG....",
                           ".........GSSG...",
                           "........GSSSG...",
                           ".......GSSSG....",
                           "..GGGGGSSSSG....",
                           ".GSSSSSSSSSG....",
                           ".GSLLSSSSSSG....",
                           ".GSSSSSSSSG.....",
                           "..GSSSYSSG......",
                           "..GSSSSSSG......",
                           "...GG..GG.......",
                           "................",
                           "................",
                           "................"],
                          {"G": "#C9A04C", "S": "#D5DDE7", "L": "#F4F7FB", "Y": "#FFE9A6"}),
    "comet_bridle": (["................",
                      ".......CW.......",
                      "......CWWC......",
                      "...S...CW...S...",
                      "...SS......SS...",
                      "...S.S....S.S...",
                      "...S..SSSS..S...",
                      "...S........S...",
                      "...S........S...",
                      "...SMMMMMMMMS...",
                      "...S........S...",
                      "...S........S...",
                      "..MMM......MMM..",
                      "..M.M......M.M..",
                      "..MMM......MMM..",
                      "................"],
                     {"S": "#4A5B82", "M": "#D8E2EC", "C": "#9FE3FF", "W": "#FFFFFF"}),
    "halo_reins": (["................",
                    "................",
                    ".....YYYYYY.....",
                    "....Y......Y....",
                    "...Y........Y...",
                    "...Y........Y...",
                    "....Y......Y....",
                    ".....YYYYYY.....",
                    "......R..R......",
                    ".....R....R.....",
                    "....R......R....",
                    "...R........R...",
                    "..GR........RG..",
                    "................",
                    "................",
                    "................"],
                   {"Y": "#FFE08A", "R": "#D2A64E", "G": "#8E6F34"}),
    "drift_harness": (["................",
                       "................",
                       "..MM........MM..",
                       "..MHH......HHM..",
                       "...HHH....HHH...",
                       "....HPPPPPPH....",
                       "....PLLLLLLP....",
                       "....PLPPPPLP....",
                       "....PLPPPPLP....",
                       "....PLLLLLLP....",
                       "....HPPPPPPH....",
                       "...HHH....HHH...",
                       "..MHH......HHM..",
                       "..MM........MM..",
                       "................",
                       "................"],
                      {"H": "#3A4558", "P": "#5E6E86", "L": "#8FA2BC", "M": "#D8E2EC"}),
    "nebula_reins": (["................",
                      "................",
                      "................",
                      "......VVVV......",
                      ".....VWWVVV.....",
                      ".....VWVVVV.....",
                      ".....VVVVVV.....",
                      "......VVVV......",
                      ".....R....R.....",
                      "....R......R....",
                      "...R........R...",
                      "..R..........R..",
                      "..M..........M..",
                      "................",
                      "................",
                      "................"],
                     {"V": "#A98CF0", "W": "#F2E8FF", "R": "#3E4A62", "M": "#C8D2DE"}),
    "gale_fins": (["................",
                   "................",
                   "..E.........E...",
                   "..EF.......FE...",
                   "..EFF.....FFE...",
                   "...EFF...FFE....",
                   "...EFFF.FFFE....",
                   "....EFFFFFE.....",
                   "....EFFFFFE.....",
                   ".....EFFFE......",
                   ".....EFFFE......",
                   "......EFE.......",
                   "......SSS.......",
                   "................",
                   "................",
                   "................"],
                  {"E": "#E8FDFF", "F": "#8FDDF0", "S": "#6C7E92"}),
    "resonance_chime": (["................",
                         "......C..C......",
                         ".....CLC.CLC....",
                         ".....CLC.CLC....",
                         ".....CLC.CLC....",
                         ".....CLC.CLC....",
                         ".....CLC.CLC....",
                         ".....CLC.CLC....",
                         ".....CLCCCLC....",
                         "......CSSSC.....",
                         ".......SSS......",
                         "........S.......",
                         "........S.......",
                         ".......GSG......",
                         "........G.......",
                         "................"],
                        {"C": "#5FC8D6", "L": "#D8FFFB", "S": "#C9D2DD", "G": "#C9A04C"}),
}


def icons():
    out = {}
    for name, (rows, pal) in ICONS.items():
        img = sprite(rows, pal)
        out[name] = outline(img, rgb("#26303F"))
    # the Nebulite barding: the Starsteel's shape in Nebulite's colours
    rows, pal = ICONS["starsteel_barding"]
    neb = {"G": "#9FE8F6", "S": "#6C9EE0", "L": "#B8F6FF", "Y": "#E6FAFF"}
    out["nebulite_barding"] = outline(sprite(rows, neb), rgb("#26303F"))
    return out


def tack_slot():
    """18 by 18, vanilla's slot look (dark top-left, light bottom-right) with a faint ring and strap: the tack."""
    img = np.zeros((18, 18, 4), np.uint8)
    img[:, :, :3] = rgb("#8B8B8B")
    img[:, :, 3] = 255
    img[0, :, :3] = rgb("#373737")
    img[:, 0, :3] = rgb("#373737")
    img[17, :, :3] = rgb("#FFFFFF")
    img[:, 17, :3] = rgb("#FFFFFF")
    ys, xs = np.mgrid[0:18, 0:18] + 0.5
    d = np.hypot(xs - 9, ys - 7.5)
    ringm = (d > 3.2) & (d < 4.6)
    img[ringm, :3] = rgb("#727272")
    for y in range(11, 15):
        img[y, 6, :3] = rgb("#727272")
        img[y, 11, :3] = rgb("#727272")
    return img


def main():
    written = []
    stag = build_stag()
    solidify(stag)
    pack_uvs(stag, *STAG_TEX)
    written.append(write_text(ASSETS / "geo" / "entity" / "lumen_stag.geo.json", pretty_json(geo_json(stag, "lumen_stag", *STAG_TEX, 4, 4))))
    written.append(write_text(ASSETS / "animations" / "entity" / "lumen_stag.animation.json", pretty_json(stag_animations())))
    tex, glow, gear = paint(stag, STAG_TEX, stag_texel)
    ent = TEX / "entity"
    written += [save_png(tex, ent / "lumen_stag.png"), save_png(glow, ent / "lumen_stag_glowmask.png"),
                save_png(gear, ent / "lumen_stag_gear_glowmask.png")]

    manta = build_manta()
    solidify(manta)
    pack_uvs(manta, *MANTA_TEX)
    written.append(write_text(ASSETS / "geo" / "entity" / "drift_manta.geo.json", pretty_json(geo_json(manta, "drift_manta", *MANTA_TEX, 5, 3))))
    written.append(write_text(ASSETS / "animations" / "entity" / "drift_manta.animation.json", pretty_json(manta_animations())))
    tex, glow, gear = paint(manta, MANTA_TEX, manta_texel)
    written += [save_png(tex, ent / "drift_manta.png"), save_png(glow, ent / "drift_manta_glowmask.png"),
                save_png(gear, ent / "drift_manta_gear_glowmask.png")]

    for name, img in icons().items():
        written.append(save_png(img, TEX / "item" / f"{name}.png"))
        written.append(write_json(ASSETS / "models" / "item" / f"{name}.json",
                                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"cosmicbreach:item/{name}"}}))
    for egg in ("lumen_stag_spawn_egg", "drift_manta_spawn_egg"):
        written.append(write_json(ASSETS / "models" / "item" / f"{egg}.json", {"parent": "minecraft:item/template_spawn_egg"}))
    written.append(save_png(tack_slot(), TEX / "gui" / "sprites" / "container" / "mount" / "tack_slot.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
