"""The accessories' art (G6a, GDD 5.2): the four accessories no other task drew, the Halo's shard, the Hourglass's slow.

Writes (under src/main/resources/assets/cosmicbreach/)
    textures/item/twin_comet_band.png       a silver-blue band, two white-cyan comets chasing each other round it
    textures/item/leechstar_signet.png      a dark gold band under a crimson signet with a pale four-point star
    textures/item/perihelion_loop.png       a thin gold band, a small white sun at its top and a faint orbit grazing it
    textures/item/sunshard_compass.png      a round gilt case, a dusk-violet face, a sun-shard needle pointing up
    textures/item/sunshard_compass_NN.png   the same with the needle turned NN sixteenths of a turn clockwise (00 to 15)
    models/item/<each>.json                 item/generated; the compass's model picks a needle frame by the item
                                            property cosmicbreach:needle (a share of a turn, 0 straight up)
    textures/fx/halo_shard.png              16 by 32: one of the Halo of Nine's shards, a pale gold crystal (the game
                                            draws it as two crossed planes, with a glow)
    textures/mob_effect/vesper_slow.png     18 by 18: an hourglass running with dusk-gold sand

Same conventions as the other item icons: 16 by 16, hard alpha, a few tones per material, light from the top left.

Run:  python tools/art/gen_curios.py
"""
from __future__ import annotations

import json
import math

import numpy as np

from common import ASSETS, TEX, rel, rgb, save_png

S = 16
ITEM = TEX / "item"
MODELS = ASSETS / "models" / "item"
FRAMES = 16

SILVER = [rgb("#f2f6fc"), rgb("#c9d5e6"), rgb("#9fb1ca"), rgb("#71839f"), rgb("#4b5a74")]
GOLD = [rgb("#fff3c4"), rgb("#f2cf6e"), rgb("#c9993a"), rgb("#8f6423"), rgb("#553812")]
DARK_GOLD = [rgb("#e8c066"), rgb("#b88a33"), rgb("#86601f"), rgb("#5a3d12"), rgb("#33210a")]
CRIMSON = [rgb("#ff9fb0"), rgb("#e0405e"), rgb("#a3203d"), rgb("#661227"), rgb("#3a0816")]
CYAN = [rgb("#ffffff"), rgb("#d9fbff"), rgb("#8feaff"), rgb("#44b8e0")]
FACE = [rgb("#3d2d63"), rgb("#2a1f47"), rgb("#1b1430")]
SUN = [rgb("#ffffff"), rgb("#fff4c2"), rgb("#ffd66b")]


def blank(w=S, h=S):
    return np.zeros((h, w, 4), dtype=np.uint8)


def put(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


def band(img, cx, cy, r_in, r_out, tones):
    """A band seen face on, like the Gravity Loop's: lit from the top left, its outer lower edge in shadow."""
    for y in range(img.shape[0]):
        for x in range(img.shape[1]):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if r_in < d < r_out:
                light = -(px * 0.6 + py * 0.8) / r_out
                t = 2 - light * 1.8 + (1.0 if d > r_out - 0.7 and py > 0 else 0.0) - (0.8 if d < r_in + 0.8 and py > 0 else 0.0)
                img[y, x] = (*tones[int(np.clip(round(t), 0, len(tones) - 1))], 255)


def twin_comet_band():
    img = blank()
    band(img, 8, 8, 3.9, 6.3, SILVER)
    # two comets chasing each other clockwise round the band: a white head, a cyan tail behind it
    tail = [rgb("#ffffff"), rgb("#8feaff"), rgb("#44b8e0"), rgb("#2f8fc0"), rgb("#2a6f9a")]
    for head_angle in (-2.35, 0.8):
        for k in range(5):
            a = head_angle - k * 0.24
            x, y = int(8 + 5.1 * math.cos(a)), int(8 + 5.1 * math.sin(a))
            put(img, x, y, tail[k])
        hx, hy = int(8 + 5.9 * math.cos(head_angle)), int(8 + 5.9 * math.sin(head_angle))
        put(img, hx, hy, tail[1])
    return img


def leechstar_signet():
    img = blank()
    band(img, 8, 9.2, 3.4, 5.6, DARK_GOLD)
    # the signet: a crimson oval set on the band's top, a pale four-point star in it
    for y in range(1, 9):
        for x in range(4, 12):
            px, py = x + 0.5 - 8, y + 0.5 - 4.6
            d = (px / 3.8) ** 2 + (py / 3.3) ** 2
            if d <= 1.0:
                t = 1 if d < 0.5 and px + py < 0.3 else 2 if d < 0.82 else 3
                img[y, x] = (*CRIMSON[t], 255)
    for x, y in ((7, 4), (8, 4), (7, 5), (8, 5)):
        put(img, x, y, (255, 238, 242))
    for x, y in ((7, 2), (8, 7), (5, 4), (10, 5)):
        put(img, x, y, CRIMSON[0])
    return img


def perihelion_loop():
    img = blank()
    band(img, 8, 9, 4.4, 6.0, GOLD)
    # a small white sun at the band's top, where the orbit comes closest, and two glints beside it
    for x, y, t in ((8, 2, 0), (7, 2, 0), (8, 1, 1), (7, 1, 1), (8, 3, 1), (7, 3, 1), (6, 2, 1), (9, 2, 1),
                    (6, 1, 2), (9, 3, 2), (9, 1, 2), (6, 3, 2)):
        put(img, x, y, SUN[t])
    put(img, 11, 1, SUN[1])
    put(img, 4, 4, SUN[2])
    return img


def compass(turns):
    """The Sunshard Compass with its needle `turns` of a turn clockwise from straight up."""
    img = blank()
    c = 7.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 7.0:
                if d > 5.6:
                    light = -((x + 0.5 - 8) * 0.6 + (y + 0.5 - 8) * 0.8) / 7.0
                    img[y, x] = (*GOLD[int(np.clip(round(2 - light * 2.4), 0, 4))], 255)
                else:
                    img[y, x] = (*FACE[0 if d < 2.5 else 1 if d < 4.6 else 2], 255)
    for x, y in ((8, 3), (8, 12), (3, 8), (12, 8)):
        put(img, x - (1 if x > 8 else 0), y - (1 if y > 8 else 0), GOLD[2])
    # the needle: a shard of sunlight from the middle out 4.6 px, a dim stub behind
    a = turns * 2 * math.pi
    dx, dy = math.sin(a), -math.cos(a)
    ss = 4
    for y in range(S):
        for x in range(S):
            hit_tip = 0
            hit_tail = 0
            for sy in range(ss):
                for sx in range(ss):
                    px = x + (sx + 0.5) / ss - 8.0
                    py = y + (sy + 0.5) / ss - 8.0
                    along = px * dx + py * dy
                    across = abs(-px * dy + py * dx)
                    if 0.0 <= along <= 4.8 and across <= 0.95 * (1.0 - along / 5.4):
                        hit_tip += 1
                    elif -2.2 <= along < 0.0 and across <= 0.55:
                        hit_tail += 1
            if hit_tip >= ss * ss * 0.4:
                along_c = (x + 0.5 - 8) * dx + (y + 0.5 - 8) * dy
                img[y, x] = (*SUN[0 if along_c > 3.2 else 1 if along_c > 1.4 else 2], 255)
            elif hit_tail >= ss * ss * 0.45:
                img[y, x] = (*rgb("#8a7aa8"), 255)
    put(img, 7, 7, GOLD[1])
    return img


def halo_shard():
    """16 by 32: a long crystal, pointed at both ends, its left facet catching the light."""
    w, h = 16, 32
    img = np.zeros((h, w, 4), dtype=np.uint8)
    for y in range(h):
        for x in range(w):
            px = (x + 0.5 - 8) / 7.5
            py = (y + 0.5 - 16) / 15.5
            if abs(px) + abs(py) <= 1.0:
                if abs(px) + abs(py) > 0.86:
                    col, a = rgb("#e8b85a"), 255
                elif px < -0.05:
                    col, a = (rgb("#fffdf2") if py < 0 else rgb("#fff0c4")), 255
                else:
                    col, a = (rgb("#f7dc98") if py < 0 else rgb("#e6c070")), 255
                img[y, x] = (*col, a)
    img[6:14, 6, :3] = (255, 255, 255)
    return img


def vesper_slow_icon():
    rows = [
        "..................",
        "...GGGGGGGGGGGG...",
        "....g........g....",
        "....g.ssssss.g....",
        "....g.ssssss.g....",
        ".....g.ssss.g.....",
        "......g.ss.g......",
        ".......g..g.......",
        "........gg........",
        "........gg........",
        ".......g..g.......",
        "......g.s..g......",
        ".....g..ss..g.....",
        "....g..ssss..g....",
        "....g.ssssss.g....",
        "....gssssssssg....",
        "...GGGGGGGGGGGG...",
        "..................",
    ]
    pal = {"G": GOLD[2], "g": rgb("#dce6f2"), "s": rgb("#f0c878")}
    img = np.zeros((18, 18, 4), dtype=np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img[y, x] = (*pal[ch], 255)
    return img


def generated(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2) + "\n")
    return path


def main():
    written = []
    for name, fn in (("twin_comet_band", twin_comet_band), ("leechstar_signet", leechstar_signet),
                     ("perihelion_loop", perihelion_loop)):
        written.append(save_png(fn(), ITEM / f"{name}.png"))
        written.append(write_json(MODELS / f"{name}.json", generated(f"cosmicbreach:item/{name}")))
    written.append(save_png(compass(0.0), ITEM / "sunshard_compass.png"))
    overrides = []
    for k in range(FRAMES):
        stem = f"sunshard_compass_{k:02d}"
        written.append(save_png(compass(k / FRAMES), ITEM / f"{stem}.png"))
        written.append(write_json(MODELS / f"{stem}.json", generated(f"cosmicbreach:item/{stem}")))
        threshold = 0.0 if k == 0 else (k - 0.5) / FRAMES
        overrides.append({"predicate": {"cosmicbreach:needle": round(threshold, 6)}, "model": f"cosmicbreach:item/{stem}"})
    overrides.append({"predicate": {"cosmicbreach:needle": round((FRAMES - 0.5) / FRAMES, 6)},
                      "model": "cosmicbreach:item/sunshard_compass_00"})
    compass_model = generated("cosmicbreach:item/sunshard_compass")
    compass_model["overrides"] = overrides
    written.append(write_json(MODELS / "sunshard_compass.json", compass_model))
    written.append(save_png(halo_shard(), TEX / "fx" / "halo_shard.png"))
    written.append(save_png(vesper_slow_icon(), TEX / "mob_effect" / "vesper_slow.png"))
    for p in written:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
