"""The Hollow Crypt's art (W6): the Choir Floor, the traps, the crypt's vault, their block models, and the textures
the client draws the Choir Floor and the traps' tells with.

    textures/block/
      resonance_floor              black crystal-basalt tile, faint violet grain (the pads are drawn on it)
      conductor_plinth/_robe/_hood/_mask/_baton
                                   the Conductor: a plinth ringed in gold, a dark robe with folds and a violet hem,
                                   a hood with a pale mask and two violet slits, a gold baton
      crypt_vault_front(_ready)/_side/_top
                                   Umbral bricks and magenta; ready, its seam glows
      void_pocket_top              polished Umbral Basalt with a violet ring sigil (the pocket's floor)
      pocket_seal                  a wall of violet crystal, faceted, faintly lit
      starfall_chute               a ceiling stone with a star-shaped aperture rimmed in pale gold
      gravity_piston_head          dark blued metal with a rune grid and rivets (the slab that slams down)
      umbral_emitter               a Kinetic Tripwire emitter in Umbral bricks with a thin teal slit
    textures/fx/
      choir_fill                   plain white (the pads' colour fields)
      choir_glyphs                 the eight pads' glyphs in a row: ring, triangle, square, diamond, cross, star,
                                   crescent, waves (white in alpha, tinted in game)
      choir_ring                   a soft ring (the metronome and the Conductor's halo)
      rift_cracks                  hairline cracks over a 3 by 3 patch, 96 by 96 (tinted violet in game)
      gravity_rings                faint concentric rings over a 5 by 5 sigil
      chute_glint                  a four-pointed glint

The Void Rift tiles, the Gravity Sigil and the Gravity Piston reuse the Umbral Basalt textures on purpose: to a
careless eye they are the floor and the ceiling. Dark palette: these are the Deep's blocks.

Run:  python tools/art/gen_crypt.py   (after gen_blocks_deep: it reads their textures)
"""
from __future__ import annotations

import json
import math

import numpy as np

from blockart import by_share, fbm, from_ramp, ramp, rng_for
from common import ASSETS, TEX, load_png, rel, rgb, save_png, smoothstep, supersample, white_alpha

S = 16
BLOCK = TEX / "block"
FX = TEX / "fx"
MODELS = ASSETS / "models" / "block"

UMBRAL = ramp("#0b0911", "#14111c", "#1d1828", "#282036", "#352a46")
VIOLET = ramp("#3e2360", "#6a3a9c", "#9a62d4", "#c89cf5", "#efdcff")
MAGENTA = ramp("#5e1250", "#8e1f78", "#c43aa6", "#f07ad0", "#ffd2f1")
GOLD = ramp("#7a5418", "#a8792a", "#d4a444", "#f0cf7a", "#fff0c4")
STEEL = ramp("#12161f", "#1c2330", "#27303f", "#35415a", "#4d5b78")

written: list = []


def out(img, path):
    save_png(img, path)
    written.append(path)


def tex(name):
    return load_png(BLOCK / f"{name}.png").copy()


def px(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


def ring(img, cx, cy, r0, r1, colours):
    for y in range(img.shape[0]):
        for x in range(img.shape[1]):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1:
                shade = 0.5 - 0.5 * ((x - cx) + (y - cy)) / (r1 * 1.414)
                k = int(round(np.clip(shade, 0, 1) * (len(colours) - 1)))
                px(img, x, y, colours[k])


# ---------------------------------------------------------------- blocks

def resonance_floor():
    rng = rng_for("resonance_floor")
    img = from_ramp(by_share(fbm(rng), (3, 5, 5, 2, 1)), UMBRAL[:4])
    grain = fbm(rng, octaves=((4, 0.5), (8, 0.5)))
    for y in range(S):
        for x in range(S):
            if grain[y, x] > 0.86:
                img[y, x] = (*VIOLET[0], 255)
            if x in (0, 15) or y in (0, 15):
                img[y, x] = (*UMBRAL[4 if (x == 0 or y == 0) else 1], 255)
    out(img, BLOCK / "resonance_floor.png")


def conductor():
    plinth = tex("polished_umbral_basalt")
    ring(plinth, 8, 8, 5.2, 6.6, GOLD)
    out(plinth, BLOCK / "conductor_plinth.png")
    rng = rng_for("conductor_robe")
    robe = from_ramp(by_share(fbm(rng, aspect=(1, 3)), (2, 4, 5, 3, 1)), UMBRAL)
    for x in (3, 7, 11):
        for y in range(S):
            px(robe, x, y, UMBRAL[0])
            px(robe, x + 1, y, UMBRAL[3])
    for x in range(S):
        px(robe, x, 15, VIOLET[1])
        px(robe, x, 14, VIOLET[2] if x % 3 else VIOLET[1])
    out(robe, BLOCK / "conductor_robe.png")
    hood = from_ramp(by_share(fbm(rng_for("conductor_hood")), (3, 4, 4, 2, 1)), UMBRAL)
    for x in range(S):
        px(hood, x, 12, GOLD[2] if x % 4 else GOLD[3])
    out(hood, BLOCK / "conductor_hood.png")
    mask = hood.copy()
    for y in range(3, 12):
        for x in range(4, 12):
            e = ((x + 0.5 - 8) / 4.2) ** 2 + ((y + 0.5 - 7) / 5.0) ** 2
            if e <= 1.0:
                mask[y, x] = (*rgb("#e8e2ee" if e < 0.7 else "#c9c2d6"), 255)
    for x in (5, 6, 9, 10):
        px(mask, x, 6, VIOLET[3])
        px(mask, x, 7, VIOLET[2] if x in (6, 9) else rgb("#c9c2d6"))
    px(mask, 7, 10, rgb("#9a90aa"))
    px(mask, 8, 10, rgb("#9a90aa"))
    out(mask, BLOCK / "conductor_mask.png")
    baton = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            baton[y, x] = (*GOLD[2 + (x + y) % 2], 255)
    for y in range(S):
        px(baton, 7, y, GOLD[4])
    out(baton, BLOCK / "conductor_baton.png")


def vault():
    stone = "umbral_basalt_bricks"
    side = tex(stone)
    for x in range(S):
        px(side, x, 0, MAGENTA[2])
        px(side, x, 15, MAGENTA[0])
    out(side, BLOCK / "crypt_vault_side.png")
    top = tex(stone)
    ring(top, 8, 8, 3.0, 5.0, MAGENTA)
    out(top, BLOCK / "crypt_vault_top.png")
    glow = ramp("#ffb8ea", "#ffffff")
    for ready in (False, True):
        f = tex(stone)
        for y in range(2, 14):
            for x in range(3, 13):
                edge = x in (3, 12) or y in (2, 13)
                f[y, x] = (*(MAGENTA[3] if edge else UMBRAL[2]), 255)
        for y in range(3, 13):
            px(f, 7, y, glow[0] if ready else MAGENTA[0])
            px(f, 8, y, glow[1] if ready else MAGENTA[1])
        ring(f, 8, 8, 1.6, 3.0, MAGENTA if not ready else [glow[0], glow[1], glow[1]])
        out(f, BLOCK / ("crypt_vault_front_ready.png" if ready else "crypt_vault_front.png"))


def pocket():
    top = tex("polished_umbral_basalt")
    ring(top, 8, 8, 4.6, 6.0, VIOLET)
    for k in range(6):
        a = k * math.pi / 3
        for r in np.arange(1.5, 4.6, 0.5):
            px(top, int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r)), VIOLET[1 + k % 2])
    px(top, 7, 7, VIOLET[4])
    px(top, 8, 8, VIOLET[3])
    out(top, BLOCK / "void_pocket_top.png")
    rng = rng_for("pocket_seal")
    field = fbm(rng, octaves=((2, 0.5), (4, 0.5)))
    seal = from_ramp(by_share(field, (2, 4, 5, 3, 1)), VIOLET[:4] + [VIOLET[3]])
    for y in range(S):
        for x in range(S):
            if (x + 2 * y) % 11 == 0 or (2 * x - y) % 13 == 0:
                seal[y, x] = (*VIOLET[0], 255)
            elif (x + 2 * y) % 11 == 1:
                seal[y, x] = (*VIOLET[4], 255)
    out(seal, BLOCK / "pocket_seal.png")


def chute():
    img = tex("umbral_basalt_top")

    def star(x, y):
        dx = x - 8.0
        dy = y - 8.0
        r = np.hypot(dx, dy)
        a = np.arctan2(dy, dx)
        edge = 3.2 + 2.4 * np.abs(np.cos(2 * a)) ** 6
        return (r < edge).astype(float)

    hole = supersample(star, S, S)
    rimmed = supersample(lambda x, y: ((np.hypot(x - 8, y - 8) < 6.9) & (np.hypot(x - 8, y - 8) > 5.8)).astype(float), S, S)
    for y in range(S):
        for x in range(S):
            if rimmed[y, x] > 0.4:
                img[y, x] = (*GOLD[1 + (x + y) % 3], 255)
            if hole[y, x] > 0.5:
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                img[y, x] = (*(rgb("#050308") if d < 3.2 else rgb("#1a1224")), 255)
    px(img, 8, 5, GOLD[4])
    out(img, BLOCK / "starfall_chute.png")


def emitter():
    """The Umbral emitter: W5's tripwire emitter in Umbral bricks, a dark housing and a thin teal slit."""
    img = tex("umbral_basalt_bricks")
    for y in range(5, 11):
        for x in range(2, 14):
            img[y, x] = (*UMBRAL[0], 255)
    for x in range(3, 13):
        px(img, x, 7, rgb("#2fb8ab"))
        px(img, x, 8, rgb("#1c6f69"))
    px(img, 8, 7, rgb("#9ff4ec"))
    out(img, BLOCK / "umbral_emitter.png")


def piston_head():
    rng = rng_for("gravity_piston_head")
    img = from_ramp(by_share(fbm(rng), (2, 4, 5, 3, 1)), STEEL)
    for i in range(S):
        px(img, i, 0, STEEL[4])
        px(img, 0, i, STEEL[4])
        px(img, i, 15, STEEL[0])
        px(img, 15, i, STEEL[0])
    for x in range(3, 13):
        px(img, x, 8, VIOLET[1])
    for y in range(3, 13):
        px(img, 8, y, VIOLET[1])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, STEEL[4])
        px(img, x + 1, y + 1, STEEL[0])
    out(img, BLOCK / "gravity_piston_head.png")


# ---------------------------------------------------------------- fx

def glyph_shapes():
    """Eight glyphs as alpha functions on a 32 by 32 cell, centred at (16, 16)."""
    def ring_(x, y):
        r = np.hypot(x - 16, y - 16)
        return ((r < 12) & (r > 8)).astype(float)

    def poly(points):
        def f(x, y):
            inside = np.ones_like(x, dtype=bool)
            n = len(points)
            for i in range(n):
                x0, y0 = points[i]
                x1, y1 = points[(i + 1) % n]
                inside &= (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0) >= 0
            return inside.astype(float)
        return f

    def hollow(outer, inner):
        return lambda x, y: np.clip(outer(x, y) - inner(x, y), 0, 1)

    tri = hollow(poly([(16, 4), (29, 27), (3, 27)]), poly([(16, 11.5), (23.5, 23.5), (8.5, 23.5)]))
    square = hollow(poly([(5, 5), (27, 5), (27, 27), (5, 27)]), poly([(9.5, 9.5), (22.5, 9.5), (22.5, 22.5), (9.5, 22.5)]))
    diamond = poly([(16, 3), (29, 16), (16, 29), (3, 16)])
    cross = lambda x, y: (((np.abs(x - 16) < 3.2) & (np.abs(y - 16) < 12.5)) | ((np.abs(y - 16) < 3.2) & (np.abs(x - 16) < 12.5))).astype(float)

    def star5(x, y):
        a = np.arctan2(y - 16, x - 16) + math.pi / 2
        r = np.hypot(x - 16, y - 16)
        k = (np.cos(5 * a) + 1) / 2
        return (r < 5.5 + 7.5 * k ** 2).astype(float)

    def crescent(x, y):
        return ((np.hypot(x - 16, y - 16) < 12) & (np.hypot(x - 21, y - 13) > 9.5)).astype(float)

    def waves(x, y):
        out = np.zeros_like(x, dtype=float)
        for row in (9, 16, 23):
            out = np.maximum(out, (np.abs(y - row - 2.2 * np.sin((x - 4) / 24 * 2 * math.pi)) < 1.9) & (np.abs(x - 16) < 12.5))
        return out.astype(float)

    return [ring_, tri, square, diamond, cross, star5, crescent, waves]


def fx():
    save_fx(white_alpha(np.ones((S, S))), "choir_fill")
    cells = []
    for shape in glyph_shapes():
        a = supersample(shape, 32, 32)
        # a soft glow round each glyph, so it reads on a lit pad
        glow = np.zeros_like(a)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                glow = np.maximum(glow, np.roll(np.roll(a, dy, 0), dx, 1) * (0.35 if abs(dx) + abs(dy) > 1 else 0.6))
        cells.append(np.maximum(a, glow))
    save_fx(white_alpha(np.concatenate(cells, axis=1)), "choir_glyphs")
    ring_a = supersample(lambda x, y: np.exp(-((np.hypot(x - 32, y - 32) - 24.0) / 2.6) ** 2), 64, 64)
    save_fx(white_alpha(ring_a), "choir_ring")
    save_fx(white_alpha(cracks()), "rift_cracks")
    rings = supersample(lambda x, y: np.maximum.reduce([np.exp(-((np.hypot(x - 40, y - 40) - r) / 1.1) ** 2) for r in (8, 17, 26, 35)])
                        * (np.hypot(x - 40, y - 40) < 38.5), 80, 80)
    save_fx(white_alpha(0.85 * rings), "gravity_rings")

    def glint(x, y):
        dx = np.abs(x - 16)
        dy = np.abs(y - 16)
        core = np.exp(-(dx ** 2 + dy ** 2) / 10.0)
        rays = np.exp(-dx / 1.1) * np.exp(-dy / 7.0) + np.exp(-dy / 1.1) * np.exp(-dx / 7.0)
        return np.clip(core + 0.9 * rays, 0, 1)

    save_fx(white_alpha(supersample(glint, 32, 32)), "chute_glint")


def cracks():
    """Hairline cracks wandering across 96 by 96 (a 3 by 3 patch, 32 texels a block), branching, a faint glow along them."""
    rng = np.random.default_rng(606)
    n = 96
    img = np.zeros((n, n))
    starts = [(8, 20), (88, 12), (48, 88), (12, 80), (80, 60), (44, 4)]
    for sx, sy in starts:
        x, y = float(sx), float(sy)
        a = math.atan2(n / 2 - sy, n / 2 - sx) + rng.normal(0, 0.4)
        for step in range(120):
            a += rng.normal(0, 0.25)
            x += math.cos(a)
            y += math.sin(a)
            if not (0 <= x < n and 0 <= y < n):
                break
            img[int(y), int(x)] = 1.0
            if rng.random() < 0.04:
                bx, by, ba = x, y, a + rng.choice((-1, 1)) * 0.9
                for _ in range(18):
                    ba += rng.normal(0, 0.25)
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    if 0 <= bx < n and 0 <= by < n:
                        img[int(by), int(bx)] = 0.75
    glow = np.zeros_like(img)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx or dy:
                glow = np.maximum(glow, np.roll(np.roll(img, dy, 0), dx, 1) * 0.12)
    return np.maximum(img, glow)


def save_fx(img, name):
    out(img, FX / f"{name}.png")


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


def model(name, m):
    path = MODELS / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8", newline="\n")
    written.append(path)


def cube_all(name, texture):
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": t(texture)}})


def models():
    cube_all("resonance_floor", "resonance_floor")
    cube_all("void_rift_tile", "polished_umbral_basalt")
    cube_all("gravity_sigil", "polished_umbral_basalt")
    cube_all("pocket_seal", "pocket_seal")
    model("gravity_piston", {"parent": "minecraft:block/cube_column",
                             "textures": {"end": t("umbral_basalt_top"), "side": t("umbral_basalt")}})
    model("void_pocket", {"parent": "minecraft:block/cube_bottom_top",
                          "textures": {"top": t("void_pocket_top"), "bottom": t("polished_umbral_basalt"),
                                       "side": t("polished_umbral_basalt")}})
    model("starfall_chute", {"parent": "minecraft:block/block", "textures": {
        "particle": t("umbral_basalt"), "side": t("umbral_basalt"), "top": t("umbral_basalt_top"), "hole": t("starfall_chute")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", "#top", "#hole")}]})
    model("stalker_marker", {"textures": {"particle": t("umbral_basalt")}})
    model("umbral_emitter", {"parent": "minecraft:block/block", "textures": {
        "particle": t("umbral_basalt_bricks"), "front": t("umbral_emitter"), "side": t("umbral_basalt_bricks")},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#side", north="#front")}]})
    tx = {"particle": t("conductor_robe"), "plinth": t("conductor_plinth"), "robe": t("conductor_robe"),
          "hood": t("conductor_hood"), "mask": t("conductor_mask"), "baton": t("conductor_baton")}
    model("conductor", {"parent": "minecraft:block/block", "textures": tx, "elements": [
        {"from": [1, 0, 1], "to": [15, 3, 15], "faces": faces("#plinth")},
        {"from": [3, 3, 3], "to": [13, 6, 13], "faces": faces("#robe", "#plinth")},
        {"from": [4, 6, 4], "to": [12, 16, 12], "faces": faces("#robe", "#robe")},
    ]})
    model("conductor_top", {"parent": "minecraft:block/block", "textures": tx, "elements": [
        {"from": [4.5, 0, 4.5], "to": [11.5, 6, 11.5], "faces": faces("#robe")},
        {"from": [3.5, 5, 5], "to": [12.5, 7, 11], "faces": faces("#robe")},
        {"from": [5, 7, 5], "to": [11, 14, 11], "faces": faces("#hood", north="#mask")},
        {"from": [11.5, 4, 6.5], "to": [13.5, 11, 8.5], "faces": faces("#robe")},
        {"from": [12, 11, 7], "to": [13, 16, 8], "faces": faces("#baton")},
    ]})
    for ready in (False, True):
        front = t("crypt_vault_front_ready" if ready else "crypt_vault_front")
        model(f"crypt_vault{'_ready' if ready else ''}", {"parent": "minecraft:block/block", "textures": {
            "particle": t("crypt_vault_side"), "front": front, "side": t("crypt_vault_side"), "top": t("crypt_vault_top")},
            "elements": [{"from": [1, 0, 1], "to": [15, 15, 15], "faces": faces("#side", "#top", "#top", north="#front")}]})


def main() -> int:
    written.clear()
    resonance_floor()
    conductor()
    vault()
    pocket()
    chute()
    piston_head()
    emitter()
    fx()
    models()
    for p in written:
        print("wrote", rel(p))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
