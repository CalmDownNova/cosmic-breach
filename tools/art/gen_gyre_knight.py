"""The Gyre Knight and the Gravity Loop, for GeckoLib 4.

Writes (under src/main/resources/assets/cosmicbreach/)
    geo/entity/gyre_knight.geo.json                Bedrock geometry 1.12.0, box UV, full size (16 units a block)
    animations/entity/gyre_knight.animation.json   hover, sweep, lance, stunned
    textures/entity/gyre_knight.png                silver-blue armour, steel blades
    textures/entity/gyre_knight_glowmask.png       the gyroscope's white-cyan heart and its gimbal rings
    textures/entity/gyre_knight_blades_glowmask.png  the blades (the game tints them gold for a sweep, red for lances)
    textures/item/gravity_loop.png and its model

GDD 7.1: an empty suit of silver-blue armor around a spinning gyroscope core, three blades orbiting on tilted rings.
Nothing is inside the armour: a helm open at the front on dark emptiness (no visor light, no crest: armour worn by
no one, not a robot), pauldrons, an open cage of a cuirass (a collar, a belt, four
ribs, a back plate, two front plates either side of a window), faulds, floating gauntlets and floating greaves, all
apart, so it reads as armour worn by nothing. In the cage's window a bright orb turns inside three gimbal rings on
three axes (the game spins them). The three blades are bones of their own the game places on their orbits (built
pointing -Z, their middle at the pivot, 1.4 blocks long, GyreOrbit.BLADE_LENGTH). The gyroscope's middle is 1.35
blocks over the feet (GyreKnight.CORE_Y).

Run:  python tools/art/gen_gyre_knight.py   (then preview_gyre_knight.py)
"""
from __future__ import annotations

import math

import numpy as np

from common import ASSETS, TEX, rel, rgb, save_png
from gen_colossus import Bone, Cube, face_rects, geo_json, kf, loop_times, pack_uvs, pretty_json, texel_point, write_json, write_text

TEX_W, TEX_H = 128, 128
CORE_Y = 21.6

ARMOR = [rgb("#e4ecf6"), rgb("#c3d1e3"), rgb("#9fb3cc"), rgb("#7b91b0"), rgb("#56698a"), rgb("#3a4a66")]
TRIM = [rgb("#f7fbff"), rgb("#d9e6f4")]
HOLLOW = rgb("#05070c")      # the helm's empty inside: no light in it
STEEL = [rgb("#f4f8fc"), rgb("#d3dfeb"), rgb("#a9bacd"), rgb("#7d90a8")]
GRIP = rgb("#3a3f55")
CORE = [rgb("#ffffff"), rgb("#e4fdff")]
GIMBAL = [rgb("#bff4ff"), rgb("#6fd9f0")]
FACE_TONE = {"top": 0, "front": 1, "xpos": 1, "xneg": 2, "back": 2, "bottom": 3}


def cbox(cx, cy, cz, w, h, d, paint, **kw):
    return Cube((cx - w / 2, cy - h / 2, cz - d / 2), (w, h, d), paint, **kw)


def ring(radius, axis, bars=8, paint="gimbal", thick=1.0):
    """A ring of `bars` bars round the core's middle, in the plane square to `axis` ('x', 'y' or 'z')."""
    cubes = []
    side = 2 * radius * math.tan(math.pi / bars) + 0.4
    for k in range(bars):
        a = 2 * math.pi * k / bars
        if axis == "y":      # horizontal ring: bars in the XZ plane, turned about Y
            c = cbox(radius * math.cos(a), CORE_Y, radius * math.sin(a), thick, thick, side, paint,
                     rotation=(0, -math.degrees(a), 0), pivot=(radius * math.cos(a), CORE_Y, radius * math.sin(a)))
        elif axis == "z":    # the XY plane, turned about Z
            c = cbox(radius * math.cos(a), CORE_Y + radius * math.sin(a), 0, thick, side, thick, paint,
                     rotation=(0, 0, math.degrees(a)), pivot=(radius * math.cos(a), CORE_Y + radius * math.sin(a), 0))
        else:                # the YZ plane, turned about X
            c = cbox(0, CORE_Y + radius * math.cos(a), radius * math.sin(a), thick, thick, side, paint,
                     rotation=(-math.degrees(a), 0, 0), pivot=(0, CORE_Y + radius * math.cos(a), radius * math.sin(a)))
        cubes.append(c)
    return cubes


def build_knight() -> list[Bone]:
    bones = [Bone("root", None, (0, 0, 0))]
    # the helm: a shell of plates open at the front on an empty dark inside (nobody in it), a low crown, no crest
    bones.append(Bone("helm", "root", (0, 29, 0), None, [
        cbox(0, 36.5, 0.5, 8, 1, 7, "armor"),             # the crown's plate
        cbox(0, 37.4, 0.8, 6, 0.8, 5.4, "armor"),         # rounding it
        cbox(0, 33, 3.5, 8, 7, 1, "armor"),               # the back
        cbox(-3.5, 33, 0.5, 1, 7, 7, "armor"),            # the sides
        cbox(3.5, 33, 0.5, 1, 7, 7, "armor"),
        cbox(0, 35.2, -2.6, 8, 2.6, 1.4, "armor"),        # the brow over the opening
        cbox(-2.9, 32.0, -2.6, 2.2, 5.0, 1.4, "armor"),   # the cheek plates either side of it
        cbox(2.9, 32.0, -2.6, 2.2, 5.0, 1.4, "armor"),
        cbox(0, 29.9, -2.8, 3.8, 1.6, 1.6, "armor"),      # the chin guard
        cbox(0, 32.6, 1.0, 6.0, 6.2, 5.0, "hollow"),      # the empty dark inside, seen through the opening
        cbox(-4.3, 31.5, -0.5, 1, 5, 6, "trim"),          # cheek guards
        cbox(4.3, 31.5, -0.5, 1, 5, 6, "trim"),
    ]))
    for side, name in ((1, "left"), (-1, "right")):
        bones.append(Bone(f"pauldron_{name}", "root", (side * 6.5, 27.5, 0), None, [
            cbox(side * 7.2, 27.6, 0, 6, 4, 7, "armor"),
            cbox(side * 7.4, 25.6, 0, 6.6, 1, 7.6, "trim"),
            cbox(side * 8.4, 30.2, 0, 3, 2, 5, "armor"),
        ]))
        bones.append(Bone(f"hand_{name}", "root", (side * 9.2, 15, -1), None, [
            cbox(side * 9.2, 17.5, -1, 3, 5, 3, "armor"),
            cbox(side * 9.2, 15.4, -1, 3.4, 1, 3.4, "trim"),
            cbox(side * 9.2, 13.4, -1.6, 3, 2.6, 3.6, "armor"),
        ]))
        bones.append(Bone(f"leg_{name}", "root", (side * 2.4, 11, 0), None, [
            cbox(side * 2.4, 6.8, 0.2, 2.8, 7, 2.8, "armor"),
            cbox(side * 2.4, 10.6, -0.6, 3.2, 2, 2.6, "trim"),
            cbox(side * 2.4, 1.3, -0.8, 3, 2.6, 4.8, "armor"),
        ]))
    cage = [
        cbox(0, 26.3, -3.2, 8, 1.4, 1.4, "armor"), cbox(0, 26.3, 3.2, 8, 1.4, 1.4, "armor"),      # the collar
        cbox(-3.8, 26.3, 0, 1.4, 1.4, 6, "armor"), cbox(3.8, 26.3, 0, 1.4, 1.4, 6, "armor"),
        cbox(0, 16.6, -2.8, 7, 1.4, 1.4, "trim"), cbox(0, 16.6, 2.8, 7, 1.4, 1.4, "trim"),        # the belt
        cbox(-3.4, 16.6, 0, 1.4, 1.4, 5, "trim"), cbox(3.4, 16.6, 0, 1.4, 1.4, 5, "trim"),
        cbox(0, 21.5, 3.4, 7, 8, 1, "armor"),                                                   # the back plate
        cbox(-3.2, 21.5, -3.4, 2, 7, 1, "armor"), cbox(3.2, 21.5, -3.4, 2, 7, 1, "armor"),       # front plates round a window
    ]
    for sx in (-1, 1):
        for sz in (-1, 1):
            cage.append(cbox(sx * 3.7, 21.5, sz * 2.9, 1.2, 9, 1.2, "armor"))                   # the ribs
    bones.append(Bone("cuirass", "root", (0, CORE_Y, 0), None, cage))
    bones.append(Bone("faulds", "root", (0, 14.5, 0), None, [
        cbox(0, 14.6, 0, 6.5, 1.6, 4.6, "armor"),
        cbox(-3.1, 13.2, -0.4, 2, 2.6, 4.2, "armor"), cbox(3.1, 13.2, -0.4, 2, 2.6, 4.2, "armor"),
    ]))
    bones.append(Bone("core", "root", (0, CORE_Y, 0), None, [
        cbox(0, CORE_Y, 0, 3, 3, 3, "core"),
        cbox(0, CORE_Y, 0, 4, 2, 2, "core"), cbox(0, CORE_Y, 0, 2, 4, 2, "core"), cbox(0, CORE_Y, 0, 2, 2, 4, "core"),
    ]))
    bones.append(Bone("gimbal0", "core", (0, CORE_Y, 0), None, ring(3.3, "y")))
    bones.append(Bone("gimbal1", "core", (0, CORE_Y, 0), None, ring(2.7, "z")))
    bones.append(Bone("gimbal2", "core", (0, CORE_Y, 0), None, ring(2.2, "x", bars=6)))
    for i in range(3):
        bones.append(Bone(f"blade{i}", None, (0, 0, 0), None, [
            cbox(0, 0, -1, 3.5, 1, 14, "blade", inflate=-0.3),
            cbox(0, 0, -9.5, 2, 1, 3, "blade", inflate=-0.3),
            cbox(0, 0, -11.6, 1, 1, 1.4, "blade", inflate=-0.2),
            cbox(0, 0, 6.8, 5, 1.5, 1.5, "trim"),
            cbox(0, 0, 8.8, 1.5, 1.5, 2.6, "grip"),
            cbox(0, 0, 10.6, 2, 2, 1.2, "trim"),
        ]))
    return bones


def texel(c: Cube, face: str, col: int, row: int, w: int, h: int):
    """(colour, glow or None, blade glow or None)."""
    kind = c.paint
    tone = FACE_TONE[face]
    edge = col == 0 or row == 0 or col == w - 1 or row == h - 1
    if kind == "core":
        return CORE[0 if face in ("front", "top") else 1], (235, 255, 255), None
    if kind == "gimbal":
        return GIMBAL[0 if edge else 1], (90, 225, 255), None
    if kind == "hollow":
        return HOLLOW, None, None
    if kind == "blade":
        idx = 0 if (face == "top" and (col == 0 or col == w - 1)) else 1 if face == "top" else 2 if face != "bottom" else 3
        fuller = face in ("top", "bottom") and w >= 3 and col == w // 2
        colour = STEEL[3] if fuller else STEEL[idx]
        return colour, None, (255, 255, 255) if face in ("top", "bottom") else (200, 200, 200)
    if kind == "grip":
        return GRIP, None, None
    if kind == "trim":
        return TRIM[0 if face == "top" else 1], None, None
    idx = 1 + tone + (1 if edge and face != "top" else 0)
    return ARMOR[min(5, idx)], None, None


def paint(bones):
    tex = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    glow = np.zeros_like(tex)
    blades = np.zeros_like(tex)
    for b in bones:
        for c in b.cubes:
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                for r in range(rh):
                    for col in range(rw):
                        colour, g, bl = texel(c, face, col, r, rw, rh)
                        tex[rv + r, ru + col, :3] = colour
                        tex[rv + r, ru + col, 3] = 255
                        if g is not None:
                            glow[rv + r, ru + col, :3] = g
                            glow[rv + r, ru + col, 3] = 255
                        if bl is not None:
                            blades[rv + r, ru + col, :3] = bl
                            blades[rv + r, ru + col, 3] = 255
    return tex, glow, blades


def anims():
    out = {}
    L = 2.4
    ts = loop_times(L, 12)
    s = lambda t, ph=0.0: math.sin(2 * math.pi * t / L + ph)
    out["hover"] = {"loop": True, "animation_length": L, "bones": {
        "helm": {"position": kf(ts, lambda t: (0, 0.3 * s(t, 0.6), 0))},
        "pauldron_left": {"position": kf(ts, lambda t: (0, 0.25 * s(t, 0.3), 0))},
        "pauldron_right": {"position": kf(ts, lambda t: (0, 0.25 * s(t, 0.9), 0))},
        "hand_left": {"position": kf(ts, lambda t: (0, 0.4 * s(t, 1.4), 0)), "rotation": kf(ts, lambda t: (4 * s(t), 0, 0))},
        "hand_right": {"position": kf(ts, lambda t: (0, 0.4 * s(t, 2.0), 0)), "rotation": kf(ts, lambda t: (4 * s(t, 1), 0, 0))},
        "leg_left": {"position": kf(ts, lambda t: (0, 0.3 * s(t, 2.4), 0))},
        "leg_right": {"position": kf(ts, lambda t: (0, 0.3 * s(t, 2.9), 0))},
        "faulds": {"position": kf(ts, lambda t: (0, 0.2 * s(t, 1.8), 0))},
    }}
    out["sweep"] = {"loop": True, "animation_length": 1.0, "bones": {
        "hand_left": {"position": [2.5, 1.5, 0], "rotation": [0, 0, -30]},
        "hand_right": {"position": [-2.5, 1.5, 0], "rotation": [0, 0, 30]},
        "pauldron_left": {"position": [1, 0.5, 0]},
        "pauldron_right": {"position": [-1, 0.5, 0]},
        "leg_left": {"position": [0.8, -0.5, 1], "rotation": [-12, 0, 0]},
        "leg_right": {"position": [-0.8, -0.5, 1], "rotation": [-12, 0, 0]},
    }}
    out["lance"] = {"loop": True, "animation_length": 1.0, "bones": {
        "hand_left": {"position": [-1.5, 3, -3], "rotation": [-60, 0, 0]},
        "hand_right": {"position": [1.5, 3, -3], "rotation": [-60, 0, 0]},
        "helm": {"rotation": [8, 0, 0]},
    }}
    out["stunned"] = {"loop": False, "animation_length": 0.5, "bones": {
        "helm": {"rotation": kf([0, 0.5], lambda t: (28 * t / 0.5, 0, 18 * t / 0.5)), "position": kf([0, 0.5], lambda t: (0, -5 * t / 0.5, -1))},
        "pauldron_left": {"position": kf([0, 0.5], lambda t: (1.5 * t / 0.5, -6 * t / 0.5, 0)), "rotation": kf([0, 0.5], lambda t: (0, 0, -30 * t / 0.5))},
        "pauldron_right": {"position": kf([0, 0.5], lambda t: (-1.5 * t / 0.5, -7 * t / 0.5, 0)), "rotation": kf([0, 0.5], lambda t: (0, 0, 35 * t / 0.5))},
        "hand_left": {"position": kf([0, 0.5], lambda t: (1, -10 * t / 0.5, 0))},
        "hand_right": {"position": kf([0, 0.5], lambda t: (-1, -10 * t / 0.5, 0))},
        "cuirass": {"position": kf([0, 0.5], lambda t: (0, -4 * t / 0.5, 0)), "rotation": kf([0, 0.5], lambda t: (15 * t / 0.5, 0, 0))},
        "core": {"position": kf([0, 0.5], lambda t: (0, -4 * t / 0.5, 0))},
        "faulds": {"position": kf([0, 0.5], lambda t: (0, -8 * t / 0.5, 0))},
    }}
    return {"format_version": "1.8.0", "animations": out}


def gravity_loop_icon():
    """The Gravity Loop: a ring of Nebulite round a tiny turning orb, a silver-blue band with three blade-like facets."""
    img = np.zeros((16, 16, 4), np.uint8)
    ys, xs = np.mgrid[0:16, 0:16] + 0.5
    d = np.hypot(xs - 8, ys - 8)
    band = (d > 4.0) & (d < 6.2)
    img[band, :3] = ARMOR[2]
    img[band & (ys < 8), :3] = ARMOR[1]
    img[band & (d > 5.6), :3] = ARMOR[4]
    img[band, 3] = 255
    for k in range(3):
        a = 2 * math.pi * k / 3 - math.pi / 2
        x, y = int(8 + 5.1 * math.cos(a)), int(8 + 5.1 * math.sin(a))
        img[y, x, :3] = TRIM[0]
    orb = d < 2.0
    img[orb, :3] = (190, 245, 255)
    img[orb, 3] = 255
    img[7:9, 7:9, :3] = (255, 255, 255)
    return img


def main():
    written = []
    bones = build_knight()
    pack_uvs(bones, TEX_W, TEX_H)
    written.append(write_text(ASSETS / "geo" / "entity" / "gyre_knight.geo.json", pretty_json(geo_json(bones, "gyre_knight", TEX_W, TEX_H, 6, 5))))
    written.append(write_text(ASSETS / "animations" / "entity" / "gyre_knight.animation.json", pretty_json(anims())))
    tex, glow, blades = paint(bones)
    ent = TEX / "entity"
    written.append(save_png(tex, ent / "gyre_knight.png"))
    written.append(save_png(glow, ent / "gyre_knight_glowmask.png"))
    written.append(save_png(blades, ent / "gyre_knight_blades_glowmask.png"))
    written.append(save_png(gravity_loop_icon(), TEX / "item" / "gravity_loop.png"))
    written.append(write_json(ASSETS / "models" / "item" / "gravity_loop.json",
                              {"parent": "minecraft:item/generated", "textures": {"layer0": "cosmicbreach:item/gravity_loop"}}))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
