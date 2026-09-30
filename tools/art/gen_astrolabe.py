"""The Choir Astrolabe: its inventory sprite, the textures of its 3D model in the hand, its effects' star and corona.

Writes (under src/main/resources/assets/cosmicbreach/)
    textures/item/choir_astrolabe.png         32x32, the inventory and item-frame sprite (the flat view)
    textures/item/choir_astrolabe_parts.png   64x64, for the hand's model (client AstrolabeItemRenderer): the enamel
                                              star-map face (a disc, top left), brass (for the rim, rings, collar, pommel),
                                              violet silk for the grip, gold for the beads and bands
    textures/fx/astrolabe_star.png            32x32, a four-point star with a hot core (bolts, marks, the heart)
    textures/fx/astrolabe_corona.png          64x64, a sun's corona with uneven rays (the Pocket Star)
    models/item/choir_astrolabe.json          the hand's 3D model (built in by the game, held like a handheld item) and
                                              the flat sprite for the inventory (neoforge:separate_transforms)

The layout of the 3D model follows the sprite: the handle rises from the lower left (a pommel at texel 4.6, 27.4) to a
disc at the upper right (centre 19.5, 12.5, radius 7), with three brass rings round the disc. So the handheld display
holds it by the handle, like a sword, and the weapon's blade data (guard 14.5, 17.5 to tip 25.5, 6.5) runs across
the disc for the effects that follow the weapon.

Run:  python tools/art/gen_astrolabe.py
"""
from __future__ import annotations

import json
import math

import numpy as np

from common import ASSETS, TEX, rel, save_png, supersample

BRASS = [(250, 226, 150), (233, 196, 106), (201, 156, 64), (160, 116, 42), (112, 78, 30), (70, 46, 20)]
GOLD = [(255, 244, 196), (255, 224, 138), (226, 170, 58), (170, 118, 36)]
ENAMEL = [(44, 66, 132), (30, 46, 100), (20, 30, 70), (12, 18, 44)]
SILK = [(110, 70, 170), (78, 44, 130), (56, 28, 96), (36, 16, 64)]
OUTLINE = (58, 36, 18)
STAR_WHITE = (255, 252, 236)

C = (19.5, 12.5)       # the disc's centre in the sprite (x right, y down)
DISC_R = 7.0
POMMEL = (4.6, 27.4)


def blend(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


# ------------------------------------------------------------------ the sprite
def sprite():
    S = 32
    img = np.zeros((S, S, 4), np.float64)

    def put(mask, colour, alpha=1.0):
        a = np.clip(mask * alpha, 0, 1)[..., None]
        img[..., :3] = img[..., :3] * (1 - a) + np.array(colour, float)[None, None, :] * a
        img[..., 3:] = np.maximum(img[..., 3:], a)

    def ellipse_ring(cx, cy, rx, ry, rot, width):
        def f(x, y):
            dx, dy = x - cx, y - cy
            c, s = math.cos(rot), math.sin(rot)
            u = (dx * c + dy * s) / rx
            v = (-dx * s + dy * c) / ry
            d = np.sqrt(u * u + v * v)
            return (np.abs(d - 1.0) * min(rx, ry) < width).astype(float)
        return supersample(f, S, S, 4)

    def disc(cx, cy, r):
        return supersample(lambda x, y: ((x - cx) ** 2 + (y - cy) ** 2 <= r * r).astype(float), S, S, 4)

    def segment(ax, ay, bx, by, half):
        def f(x, y):
            px, py = x - ax, y - ay
            vx, vy = bx - ax, by - ay
            t = np.clip((px * vx + py * vy) / (vx * vx + vy * vy), 0, 1)
            dx, dy = px - t * vx, py - t * vy
            return (dx * dx + dy * dy <= half * half).astype(float)
        return supersample(f, S, S, 4)

    top = (C[0] - DISC_R * 0.72, C[1] + DISC_R * 0.72)
    # the back halves of the rings (behind the disc): darker brass
    rings = [(10.4, 4.2, -0.78), (11.2, 6.5, 0.9), (12.2, 12.2, 0.0)]
    for rx, ry, rot in rings:
        put(ellipse_ring(C[0], C[1], rx, ry, rot, 0.55), BRASS[4])
    # the handle: violet silk, gold bands, a brass collar and pommel
    put(segment(POMMEL[0], POMMEL[1], top[0], top[1], 1.55), OUTLINE)
    put(segment(POMMEL[0], POMMEL[1], top[0], top[1], 1.0), SILK[1])
    put(segment(POMMEL[0] + 0.4, POMMEL[1] - 0.8, top[0] - 0.4, top[1] + 0.2, 0.35), SILK[0])
    for f in (0.3, 0.62):
        x = POMMEL[0] + (top[0] - POMMEL[0]) * f
        y = POMMEL[1] + (top[1] - POMMEL[1]) * f
        put(segment(x - 0.5, y + 0.5, x + 0.5, y - 0.5, 1.2), GOLD[2])
    put(disc(POMMEL[0], POMMEL[1], 2.0), OUTLINE)
    put(disc(POMMEL[0], POMMEL[1], 1.4), BRASS[2])
    put(disc(POMMEL[0] - 0.4, POMMEL[1] - 0.4, 0.6), BRASS[0])
    # the disc: outline, brass rim, enamel face with gold stars and a rete
    put(disc(C[0], C[1], DISC_R + 1.1), OUTLINE)
    put(disc(C[0], C[1], DISC_R + 0.4), BRASS[2])
    put(disc(C[0] - 0.4, C[1] - 0.4, DISC_R), BRASS[1])
    put(disc(C[0], C[1], DISC_R - 0.9), ENAMEL[1])
    put(disc(C[0] - 1.5, C[1] - 1.5, DISC_R - 3.2), ENAMEL[0], 0.6)
    put(ellipse_ring(C[0], C[1], DISC_R - 2.6, DISC_R - 2.6, 0, 0.32), GOLD[2], 0.8)
    for sx, sy in ((C[0] - 3.2, C[1] - 1.5), (C[0] + 2.4, C[1] - 3.4), (C[0] + 3.6, C[1] + 1.4), (C[0] - 1.2, C[1] + 3.8),
                   (C[0] + 0.8, C[1] + 1.0)):
        put(disc(sx, sy, 0.55), GOLD[1])
    # the heart: a bright star
    put(disc(C[0], C[1], 1.35), STAR_WHITE)
    put(segment(C[0] - 2.6, C[1], C[0] + 2.6, C[1], 0.32), STAR_WHITE, 0.9)
    put(segment(C[0], C[1] - 2.6, C[0], C[1] + 2.6, 0.32), STAR_WHITE, 0.9)
    # the front halves of the rings: bright brass, gold beads
    for i, (rx, ry, rot) in enumerate(rings):
        def front(x, y, rx=rx, ry=ry, rot=rot):
            dx, dy = x - C[0], y - C[1]
            c, s = math.cos(rot), math.sin(rot)
            v = -dx * s + dy * c
            return (v > 0).astype(float)
        mask = ellipse_ring(C[0], C[1], rx, ry, rot, 0.55) * supersample(front, S, S, 2)
        put(mask, BRASS[1])
        for k in range(2):
            a = math.pi * (0.25 + 0.5 * k) + i * 0.7
            bx = C[0] + rx * math.cos(a) * math.cos(rot) - ry * math.sin(a) * math.sin(rot)
            by = C[1] + rx * math.cos(a) * math.sin(rot) + ry * math.sin(a) * math.cos(rot)
            put(disc(bx, by, 0.8), GOLD[1])
    out = np.zeros((S, S, 4), np.uint8)
    out[..., :3] = np.clip(np.round(img[..., :3]), 0, 255).astype(np.uint8)
    out[..., 3] = np.where(img[..., 3] > 0.35, 255, 0).astype(np.uint8)
    return out


# ------------------------------------------------------------------ the 3D model's texture
def parts():
    S = 64
    img = np.zeros((S, S, 4), np.uint8)
    # the face: a 32x32 enamel disc with its engraving
    ys, xs = np.mgrid[0:32, 0:32] + 0.5
    d = np.hypot(xs - 16, ys - 16)
    face = d <= 15.8
    t = np.clip(d / 15.8, 0, 1)
    for ch in range(3):
        img[0:32, 0:32, ch] = np.where(face, (ENAMEL[0][ch] * (1 - t) + ENAMEL[2][ch] * t).astype(np.uint8), 255)
    img[0:32, 0:32, 3] = np.where(face, 255, 0)
    ring = face & (np.abs(d - 13.6) < 0.6)
    inner = face & (np.abs(d - 7.2) < 0.45)
    img[0:32, 0:32][ring] = (*GOLD[1], 255)
    img[0:32, 0:32][inner] = (*GOLD[2], 255)
    # the rete: two arcs and a pointer
    ang = np.arctan2(ys - 16, xs - 16)
    arc1 = face & (np.abs(np.hypot(xs - 19, ys - 13) - 8.5) < 0.45) & (d < 13)
    arc2 = face & (np.abs(np.hypot(xs - 12, ys - 19) - 9.0) < 0.45) & (d < 13)
    img[0:32, 0:32][arc1 | arc2] = (*GOLD[2], 255)
    rng = np.random.default_rng(7)
    for _ in range(14):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(3, 12.5)
        x, y = int(16 + r * math.cos(a)), int(16 + r * math.sin(a))
        img[y, x] = (*GOLD[0], 255)
    tick = face & (np.abs(d - 14.6) < 0.7) & (np.abs(((ang + math.pi) / (2 * math.pi) * 24) % 1 - 0.5) > 0.38)
    img[0:32, 0:32][tick] = (*BRASS[0], 255)
    # brass: bands across v (the ring faces map to v bands), a highlight line
    for y in range(16):
        band = y / 15.0
        tone = blend(BRASS[1], BRASS[3], abs(math.sin(band * math.pi * 2)) * 0.8)
        for x in range(16):
            n = ((x * 7 + y * 13) % 5) / 40.0
            img[y, 32 + x] = (*blend(tone, BRASS[0], n), 255)
        if y in (3, 7, 11, 15):
            img[y, 32:48] = (*BRASS[0], 255)
    # silk: violet with a diagonal wrap
    for y in range(16):
        for x in range(16):
            wrap = (x + y) % 4
            img[y, 48 + x] = (*(SILK[0] if wrap == 0 else SILK[1] if wrap == 1 else SILK[2]), 255)
    # gold
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x - 7.5, y - 7.5) / 10.6
            img[16 + y, 32 + x] = (*blend(GOLD[0], GOLD[3], min(1.0, dd)), 255)
    return img


# ------------------------------------------------------------------ effect textures (white, shape in alpha)
def white_alpha(alpha):
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    return out


def star():
    S = 32

    def f(x, y):
        dx, dy = (x - 16) / 16, (y - 16) / 16
        r = np.sqrt(dx * dx + dy * dy)
        core = np.exp(-(r / 0.16) ** 2)
        cross = np.exp(-(np.abs(dx) / 0.05) ** 2) * np.clip(1 - np.abs(dy), 0, 1) ** 2.2
        cross += np.exp(-(np.abs(dy) / 0.05) ** 2) * np.clip(1 - np.abs(dx), 0, 1) ** 2.2
        u, v = (dx + dy) / math.sqrt(2), (dx - dy) / math.sqrt(2)
        diag = 0.45 * (np.exp(-(np.abs(u) / 0.04) ** 2) * np.clip(1 - 1.6 * np.abs(v), 0, 1) ** 2
                       + np.exp(-(np.abs(v) / 0.04) ** 2) * np.clip(1 - 1.6 * np.abs(u), 0, 1) ** 2)
        halo = 0.35 * np.exp(-(r / 0.45) ** 2)
        return np.clip(core + cross + diag + halo, 0, 1)
    return white_alpha(supersample(f, S, S, 4))


def corona():
    S = 64
    rng = np.random.default_rng(11)
    rays = rng.uniform(0.55, 1.0, 14)
    widths = rng.uniform(0.05, 0.12, 14)

    def f(x, y):
        dx, dy = (x - 32) / 32, (y - 32) / 32
        r = np.sqrt(dx * dx + dy * dy)
        a = np.arctan2(dy, dx)
        glow = np.exp(-(r / 0.42) ** 2)
        ray = np.zeros_like(r)
        for k in range(14):
            ang = 2 * math.pi * k / 14 + 0.2 * math.sin(k * 3.1)
            da = np.angle(np.exp(1j * (a - ang)))
            ray += np.exp(-(da / widths[k]) ** 2) * np.clip(1 - r / rays[k], 0, 1) ** 1.5 * 0.6
        return np.clip(glow + ray * (r > 0.15), 0, 1) * np.clip((1 - r) / 0.1, 0, 1)
    return white_alpha(supersample(f, S, S, 3))


def item_model():
    handheld = {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.4, 3.0, 1.13], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.4, 3.0, 1.13], "scale": [0.55, 0.55, 0.55]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }
    flat = {"parent": "minecraft:item/generated", "textures": {"layer0": "cosmicbreach:item/choir_astrolabe"}}
    return {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": "minecraft:builtin/entity", "gui_light": "front",
                 "textures": {"particle": "cosmicbreach:item/choir_astrolabe"}, "display": handheld},
        "perspectives": {"gui": flat, "fixed": flat},
    }


def main():
    written = [
        save_png(sprite(), TEX / "item" / "choir_astrolabe.png"),
        save_png(parts(), TEX / "item" / "choir_astrolabe_parts.png"),
        save_png(star(), TEX / "fx" / "astrolabe_star.png"),
        save_png(corona(), TEX / "fx" / "astrolabe_corona.png"),
    ]
    path = ASSETS / "models" / "item" / "choir_astrolabe.json"
    path.write_bytes((json.dumps(item_model(), indent=2) + "\n").encode("utf-8"))
    written.append(path)
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
