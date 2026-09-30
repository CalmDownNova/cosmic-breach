"""The way in's art (W4): the Starfall Shard, the Codex and its torn page, and the lit Breach Frame.

    textures/item/starfall_shard.png        a faceted white-gold crystal with a star burning in its heart
    textures/block/starfall_shard.png       the fallen shard as it stands in its crater: a cluster of
                                            three glowing crystals (a cross model, drawn full bright)
    textures/item/starfall_codex.png        the Starfall Codex: a deep blue book, gold corners and a gold
                                            star on the cover, cream page edges
    textures/item/torn_codex_page.png       a torn page of it: cream parchment, violet ink lines, a gold star
    textures/block/breach_frame_top_active.png
    textures/block/breach_frame_side_active.png
                                            the Breach Frame while its ring is open: W1's textures with
                                            the star inlay and the rivets lit (the block is drawn full
                                            bright then, so the copper keeps its own colour)

Same style as the W1 items (tools/art/gen_materials.py, gen_starshard.py): crisp pixels, a dark outline,
four or five steps per ramp, light from the upper left.

Run:  python tools/art/gen_onboarding.py
"""
from __future__ import annotations

import math

import numpy as np

from blockart import sprite
from common import TEX, blank, load_png, outline_mask, pixel_centres, point_in_poly, rel, rgb, save_png

S = 16
R2 = math.sqrt(2)

# ---------------------------------------------------------------- the Starfall Shard (item)

WHITE = rgb("#ffffff")
PALE_GOLD = rgb("#fff0b8")
PALE_GOLD_EDGE = rgb("#f2d98a")
CYAN_PALE = rgb("#c9f6f1")
CYAN_EDGE = rgb("#7fdcd2")
GOLD = rgb("#f5c95a")
GOLD_DARK = rgb("#c98d2a")
GOLD_HI = rgb("#ffe79a")
NAVY = rgb("#1f2a4f")
# the item's cool crystal, so the gold star inside it stands out
ICE = rgb("#eefcff")
ICE_EDGE = rgb("#bfe9f2")
TEAL = rgb("#8fe0e6")
TEAL_EDGE = rgb("#4fb8c6")
LAVENDER = rgb("#c9c6f5")
LAVENDER_EDGE = rgb("#9a95dc")

TIP = (10.2, 0.0)
UPPER = (2.8, -5.0)
LOWER = (0.2, 5.3)
BREAK_A = (-6.0, -3.0)
BREAK_NOTCH = (-4.8, -0.4)
BREAK_B = (-6.8, 1.6)
HEART = (1.8, 0.2)

SHARD_FACETS = [
    ("upper_front", ICE, ICE_EDGE, [TIP, UPPER, HEART]),
    ("lower_front", TEAL, TEAL_EDGE, [TIP, HEART, LOWER]),
    ("upper_back", LAVENDER, LAVENDER_EDGE, [HEART, UPPER, BREAK_A, BREAK_NOTCH]),
    ("lower_back", TEAL_EDGE, NAVY, [HEART, BREAK_NOTCH, BREAK_B, LOWER]),
]


def to_px(p):
    s, t = p
    return (S / 2 + (s + t) / R2 - 0.3, S / 2 + (-s + t) / R2 + 0.3)


def shard_item() -> np.ndarray:
    img = blank(S, S)
    px, py = pixel_centres(S, S)
    col = np.zeros((S, S, 3), dtype=int)
    body = np.zeros((S, S), dtype=bool)
    masks = {}
    for name, fill, _, poly in SHARD_FACETS:
        m = point_in_poly(px, py, [to_px(p) for p in poly]) & ~body
        masks[name] = m
        col[m] = fill
        body |= m
    ol = outline_mask(body)
    ys, xs = np.mgrid[0:S, 0:S]
    shadow_side = (xs + ys) > (S - 1)
    touching = np.zeros_like(body)
    for dy, dx in ((0, 1), (1, 0)):
        touching |= np.roll(np.roll(ol, -dy, axis=0), -dx, axis=1)
    for name, _, edge, _ in SHARD_FACETS:
        col[masks[name] & touching & shadow_side] = edge
    img[body, :3] = col[body]
    img[body, 3] = 255
    img[ol, :3] = NAVY
    img[ol, 3] = 255
    # the star in its heart: a white core in a gold four-pointed star, its points reaching the facets' edges
    hx, hy = to_px(HEART)
    cx, cy = int(hx), int(hy)
    star = [(0, 0, WHITE), (1, 0, WHITE), (-1, 0, WHITE), (0, 1, WHITE), (0, -1, WHITE),
            (2, 0, GOLD_HI), (-2, 0, GOLD_HI), (0, 2, GOLD_HI), (0, -2, GOLD_HI),
            (3, 0, GOLD), (-3, 0, GOLD), (0, 3, GOLD), (0, -3, GOLD),
            (1, 1, GOLD), (-1, -1, GOLD), (1, -1, GOLD), (-1, 1, GOLD)]
    for dx, dy, c in star:
        x, y = cx + dx, cy + dy
        if 0 <= x < S and 0 <= y < S and body[y, x]:
            img[y, x, :3] = c
    return img


# ---------------------------------------------------------------- the shard in its crater (block, cross model)

def shard_block() -> np.ndarray:
    """Three crystals from one base: a tall one in the middle, two shorter ones leaning out."""
    img = blank(S, S)
    px, py = pixel_centres(S, S)
    crystals = [
        # base x, base width, height, lean (x shift at the tip); the one drawn last is in front
        (4.2, 4.6, 9.0, -2.2),
        (11.8, 4.2, 7.5, 2.0),
        (8.0, 6.2, 15.0, 0.3),
    ]
    for bx, w, h, lean in crystals:
        top = 16.0 - h
        tip = (bx + lean, top)
        left_base = (bx - w / 2, 16.0)
        right_base = (bx + w / 2, 16.0)
        left_shoulder = (bx - w / 2 + lean * 0.75, top + w * 0.9)
        right_shoulder = (bx + w / 2 + lean * 0.75, top + w * 0.9)
        poly = [left_base, left_shoulder, tip, right_shoulder, right_base]
        m = point_in_poly(px, py, poly)
        # the crystal's axis runs from the base centre to the tip: left of it lit, right of it gold
        ax0, ay0 = bx, 16.0
        ax1, ay1 = tip
        side = (px - ax0) * (ay1 - ay0) - (py - ay0) * (ax1 - ax0)
        axis_d = np.abs(side) / math.hypot(ax1 - ax0, ay1 - ay0)
        colour = np.zeros((S, S, 3), dtype=int)
        colour[:] = PALE_GOLD
        colour[side > 0] = CYAN_PALE
        colour[(side < 0) & (axis_d > w * 0.3)] = GOLD
        colour[axis_d < 0.55] = WHITE
        edge = outline_mask(m) & ~m
        img[m, :3] = colour[m]
        img[m, 3] = 255
        # a darker rim so the facets read, without a black outline (the block glows)
        inner_edge = m & np.roll(edge, 1, axis=1) | m & np.roll(edge, -1, axis=1)
        img[inner_edge & (side < 0), :3] = GOLD_DARK
        img[inner_edge & (side > 0), :3] = CYAN_EDGE
    # sparkles
    for x, y in ((7, 1), (2, 7), (13, 8)):
        img[y, x] = (*WHITE, 255)
    return img


# ---------------------------------------------------------------- the Codex and its torn page

CODEX = [
    "................",
    "...ooooooooooo..",
    "..osYGCCCCCCGGo.",
    "..osGkkkkkkkCpo.",
    "..osCkCCYCCCCpo.",
    "..osCCCCGCCCCpo.",
    "..osCCYGWGYCCpo.",
    "..osCCCCGCCCCpo.",
    "..osCCCCYCCCCpo.",
    "..osCCCCCCCCCpo.",
    "..osCCCCCCCCCpo.",
    "..osGCCCCCCCGqo.",
    "..osggcccccggqo.",
    "..oooooooooooqo.",
    "...oqqqqqqqqqqo.",
    "....oooooooooo..",
]
CODEX_PAL = {
    "o": "#15173d", "s": "#23246a", "C": "#3a3d9c", "c": "#2c2e7a", "k": "#5a5dcc",
    "G": "#f5c95a", "g": "#c98d2a", "Y": "#ffe9a0", "W": "#ffffff", "p": "#f4ead0", "q": "#d6c69a",
}

TORN_PAGE = [
    "................",
    "..oooooooo......",
    "..oppppppoo.....",
    "..opllllpppo....",
    "..oppppppppoo...",
    "..oplllllpppo...",
    "..opppppppppo...",
    "..opllllGlppo...",
    "..oppppGYGppo...",
    "..opllllGllpo...",
    "..oppppppppppo..",
    "..opllllllqqo...",
    "..oppppqqoo.....",
    "..oqqpoo.o......",
    "..ooo...........",
    "................",
]
TORN_PAL = {
    "o": "#6b5a3a", "p": "#f4ead0", "q": "#d9c89c", "l": "#8e7fb0", "G": "#f5c95a", "Y": "#fff4c8",
}


# ---------------------------------------------------------------- the lit Breach Frame

STAR_LIT = rgb("#fff6d8")
STAR_GLOW = rgb("#9ff5ec")


def lit(name: str) -> np.ndarray:
    """W1's frame texture with the star inlay burning pale gold in a turquoise rim and the rivets lit."""
    img = load_png(TEX / "block" / f"{name}.png").copy()
    rgbv = img[..., :3].astype(int)
    r, g, b = rgbv[..., 0], rgbv[..., 1], rgbv[..., 2]
    copper = (r > g + 25) & (r > b + 40)
    if name == "breach_frame_side":
        # the rivets catch the light; the copper keeps its colour (the block is drawn full bright when lit)
        rivets = copper & ((r + g + b) > 560)
        img[rivets, :3] = STAR_LIT
    if name == "breach_frame_top":
        # the star inlay in the middle (texels 5 to 10) burns; its rim glows turquoise
        star = np.zeros((S, S), dtype=bool)
        star[4:12, 4:12] = copper[4:12, 4:12]
        img[star, :3] = STAR_LIT
        rim = outline_mask(star, diagonal=False) & ~star
        rim[:3, :] = False
        rim[13:, :] = False
        rim[:, :3] = False
        rim[:, 13:] = False
        img[rim, :3] = STAR_GLOW
    return img


def build() -> dict[str, tuple[str, np.ndarray]]:
    return {
        "starfall_shard_item": ("item/starfall_shard", shard_item()),
        "starfall_shard_block": ("block/starfall_shard", shard_block()),
        "starfall_codex": ("item/starfall_codex", sprite(CODEX, CODEX_PAL)),
        "torn_codex_page": ("item/torn_codex_page", sprite(TORN_PAGE, TORN_PAL)),
        "breach_frame_top_active": ("block/breach_frame_top_active", lit("breach_frame_top")),
        "breach_frame_side_active": ("block/breach_frame_side_active", lit("breach_frame_side")),
    }


def main():
    built = build()
    for _, (path, img) in built.items():
        save_png(img, TEX / f"{path}.png")
    print("wrote", len(built), "onboarding textures under", rel(TEX))


if __name__ == "__main__":
    main()
