"""Meridian, the two-handed starblade: textures/item/meridian.png (32x32)
plus meridian_glow1..3.png, the same sprite with a charge light running along
the blade's centre line (stage 1 faint, stage 3 white hot with sparks).

Layout works in diagonal coordinates, because the sprite lies on the 45 degree
diagonal like every vanilla sword (grip bottom left, tip top right):

    u = x - y        position along the weapon (+ towards the tip)
    v = x + y - 31   position across the weapon (0 is the centre line,
                     negative is the upper-left edge, positive the lower-right)

Every pixel on one v line of the blade gets the same tone, which is how the
vanilla swords are shaded. Light comes from the top left.

Run:  python tools/art/gen_meridian.py
"""
from __future__ import annotations

import math

import numpy as np

from common import PREVIEWS, TEX, blank, outline_mask, over, point_in_poly, rel, rgb, save_png, upscale

S = 32

# ---------------------------------------------------------------- palette
STEEL = {
    "outline_lit": rgb("#4d5878"),   # upper-left outline (lit side)
    "outline_dark": rgb("#262b45"),  # lower-right outline (shadow side)
    "white": rgb("#fbfdff"),
    "light": rgb("#e3eaf6"),
    "mid": rgb("#c7d2e6"),
    "shade": rgb("#a5b3cf"),
    "deep": rgb("#8391b3"),
    "fuller": rgb("#b1bfda"),        # centre line: a slightly cool groove the light runs in
}
GOLD = {
    "hi": rgb("#fff3b8"),
    "light": rgb("#f6cf62"),
    "mid": rgb("#d9a23a"),
    "dark": rgb("#a8701f"),
    "outline": rgb("#5a3714"),
}
GEM = {
    "hi": rgb("#e6fffb"),
    "light": rgb("#6fe8dc"),
    "mid": rgb("#22b8b0"),
    "dark": rgb("#127a80"),
}
GRIP = {
    "cord": rgb("#6b77b0"),
    "wrap": rgb("#454c82"),
    "deep": rgb("#2d3160"),
    "outline": rgb("#191a36"),
}
# Charge light. Neighbouring diagonal lines must change gradually, or the
# 1 pixel stripes read as a checkerboard, so the light spreads as a band:
# a core line plus a halo that widens with the stage.
CORE = [rgb("#ecd08a"), rgb("#ffe08f"), rgb("#fff0bf"), rgb("#ffffff")]
HALO = [rgb("#efe4c8"), rgb("#ffedbd"), rgb("#ffe39c"), rgb("#ffd46e")]
SPARK = rgb("#ffffff")
SPARK_ARM = rgb("#fff0bf")

# ---------------------------------------------------------------- geometry
K = 3                # blade half width in diagonal lines (7 body lines)
U_TIP = 29           # tip of the centre line
TIP_TAPER = 3.0      # u lost per v line at the tip; bigger is sharper
U_BLADE0 = -7        # blade starts (hidden under the guard)
U_GUARD = -9         # centre of the star crossguard (odd: sits on a pixel)
U_GRIP1 = -12        # grip runs from here ...
U_GRIP0 = -24        # ... down to here
POMMEL = (3, 28)     # pixel centre of the pommel gem

# star crossguard: a four-pointed star with its points on the image axes (up,
# right, down, left), centred on the hilt. The blade leaves it between the up
# and right points, the grip between the down and left points.
STAR_POINT = 6.0     # centre to tip, pixels
STAR_INNER = 2.6     # radius of the concave corners between the points
STAR_RIDGE = 1.2     # pyramid height used to light the facets

R2 = math.sqrt(2)


def uv_grid():
    ys, xs = np.mgrid[0:S, 0:S]
    return xs - ys, xs + ys - (S - 1), xs, ys


def blade_body():
    u, v, _, _ = uv_grid()
    av = np.abs(v)
    return (av <= K) & (u >= U_BLADE0) & (u <= U_TIP - TIP_TAPER * av)


def blade_colour_for_line(v: int) -> tuple:
    # upper-left edge is the gold line; the fuller is the centre
    return {
        -3: GOLD["light"],
        -2: STEEL["white"],
        -1: STEEL["light"],
        0: STEEL["fuller"],
        1: STEEL["mid"],
        2: STEEL["shade"],
        3: STEEL["deep"],
    }[v]


def guard_centre():
    """Pixel centre of the hilt, on the centre line at U_GUARD."""
    return (U_GUARD + 31) // 2, (31 - U_GUARD) // 2


def star_polygon():
    """Star outline in image pixel coordinates (x right, y down)."""
    cx, cy = guard_centre()
    cx, cy = cx + 0.5, cy + 0.5
    pts = []
    for i in range(4):
        a = math.radians(90 * i - 90)          # up, right, down, left
        pts.append((cx + STAR_POINT * math.cos(a), cy + STAR_POINT * math.sin(a)))
        b = a + math.radians(45)
        pts.append((cx + STAR_INNER * math.cos(b), cy + STAR_INNER * math.sin(b)))
    return pts


LIGHT = np.array([-0.6, -0.8, 1.1])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def star_facets(px, py):
    """Mask of the star and a 0..1 lighting value from a raised four-point pyramid."""
    poly = star_polygon()
    cx, cy = guard_centre()
    cx, cy = cx + 0.5, cy + 0.5
    inside = point_in_poly(px, py, poly)
    shade = np.zeros(px.shape)
    n = len(poly)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        tri = point_in_poly(px, py, [(cx, cy), a, b])
        p0 = np.array([cx, cy, STAR_RIDGE])
        nrm = np.cross(np.array([a[0], a[1], 0.0]) - p0, np.array([b[0], b[1], 0.0]) - p0)
        if nrm[2] < 0:
            nrm = -nrm
        nrm /= np.linalg.norm(nrm)
        shade[tri] = float(np.dot(nrm, LIGHT))
    return inside, shade


def shift(mask: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """out[y, x] = mask[y + dy, x + dx] (False outside)."""
    out = np.zeros_like(mask)
    h, w = mask.shape
    ys = slice(max(0, -dy), h - max(0, dy))
    xs = slice(max(0, -dx), w - max(0, dx))
    ys2 = slice(max(0, dy), h - max(0, -dy))
    xs2 = slice(max(0, dx), w - max(0, -dx))
    out[ys, xs] = mask[ys2, xs2]
    return out


def edge_lit(mask: np.ndarray):
    """Light/dark split for a flat metal part lit from the top left."""
    up_out = ~shift(mask, 0, -1)
    left_out = ~shift(mask, -1, 0)
    down_out = ~shift(mask, 0, 1)
    right_out = ~shift(mask, 1, 0)
    lit = mask & (up_out | left_out) & ~(down_out | right_out)
    dark = mask & (down_out | right_out) & ~(up_out | left_out)
    return lit, dark


def build_base() -> np.ndarray:
    img = blank(S, S)
    u, v, xs, ys = uv_grid()
    part = np.zeros((S, S), dtype=int)  # 0 empty, 1 grip, 2 blade, 3 guard, 4 pommel
    col = np.zeros((S, S, 3), dtype=int)

    # ---- grip: three lines wide, cord wound in a spiral
    grip = (np.abs(v) <= 1) & (u >= U_GRIP0) & (u <= U_GRIP1)
    phase = (u + v) % 4
    grip_col = np.where(phase[..., None] < 2, GRIP["cord"], GRIP["wrap"])
    grip_col = np.where(((v == 1) & (phase >= 2))[..., None], GRIP["deep"], grip_col)
    col[grip] = grip_col[grip]
    part[grip] = 1

    # ---- blade
    body = blade_body()
    for line in range(-K, K + 1):
        col[body & (v == line)] = blade_colour_for_line(line)
    part[body] = 2

    # ---- star crossguard: faceted gold, each point split into a lit and a shaded half
    px_, py_ = xs + 0.5, ys + 0.5
    star, shade = star_facets(px_, py_)
    col[star] = GOLD["mid"]
    col[star & (shade > 0.78)] = GOLD["light"]
    col[star & (shade < 0.45)] = GOLD["dark"]
    cx, cy = guard_centre()
    col[star & (xs == cx) & (ys == cy)] = GOLD["hi"]
    col[star & (np.abs(xs - cx) + np.abs(ys - cy) == 1) & ((xs < cx) | (ys < cy))] = GOLD["hi"]
    part[star] = 3

    # ---- pommel: turquoise gem in a gold ring
    px, py = POMMEL
    d = np.abs(xs - px) + np.abs(ys - py)
    ring = d == 2
    gem = d <= 1
    col[ring] = GOLD["mid"]
    col[ring & ((xs - px) + (ys - py) < 0)] = GOLD["light"]
    col[ring & ((xs - px) + (ys - py) > 0)] = GOLD["dark"]
    col[gem] = GEM["mid"]
    col[gem & ((xs - px) + (ys - py) < 0)] = GEM["light"]
    col[gem & ((xs - px) + (ys - py) > 0)] = GEM["dark"]
    col[(xs == px) & (ys == py)] = GEM["light"]
    col[(xs == px - 1) & (ys == py)] = GEM["hi"]
    part[ring | gem] = 4

    filled = part > 0
    img[filled, :3] = col[filled]
    img[filled, 3] = 255

    # ---- outline: colour by the part it wraps, and by the light side for the blade
    ol = outline_mask(filled)
    part_of = np.zeros((S, S), dtype=int)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nb = np.zeros_like(part)
        h, w = part.shape
        ys0 = slice(max(0, -dy), h - max(0, dy))
        xs0 = slice(max(0, -dx), w - max(0, dx))
        ys1 = slice(max(0, dy), h - max(0, -dy))
        xs1 = slice(max(0, dx), w - max(0, -dx))
        nb[ys0, xs0] = part[ys1, xs1]
        part_of = np.maximum(part_of, nb)
    ol_blade = ol & (part_of == 2)
    img[ol_blade & (v < 0), :3] = STEEL["outline_lit"]
    img[ol_blade & (v >= 0), :3] = STEEL["outline_dark"]
    img[ol & ((part_of == 3) | (part_of == 4)), :3] = GOLD["outline"]
    img[ol & (part_of == 1), :3] = GRIP["outline"]
    img[ol, 3] = 255

    # close the point: the pixel diagonally beyond the tip joins the outline
    tip = body & (v == 0) & (u == U_TIP)
    for y, x in zip(*np.nonzero(tip)):
        if x + 1 < S and y - 1 >= 0:
            img[y - 1, x + 1] = (*STEEL["outline_dark"], 255)
    return img


def build_glow(base: np.ndarray, stage: int) -> np.ndarray:
    """Charge stage 1..3: warm light along the fuller, longer and hotter each stage.

    stage 1: a gold thread up the lower half of the fuller
    stage 2: brighter core up most of the blade with a pale warm halo
    stage 3: white-hot core the full length, gold halo across the blade face,
             brighter gold edge and a four-point glint on the tip
    """
    img = base.copy()
    u, v, xs, ys = uv_grid()
    body = blade_body()
    star, _ = star_facets(xs + 0.5, ys + 0.5)
    visible = body & ~star

    blade_len = U_TIP - U_BLADE0
    frac = (u - U_BLADE0) / blade_len          # 0 at the hilt, 1 at the tip

    def put(mask, colour):
        img[mask, :3] = colour

    if stage == 1:
        reach = 0.55
        put(visible & (v == 0) & (frac <= reach), CORE[0])
        put(visible & (v == 0) & (frac <= reach - 0.12), CORE[1])
    elif stage == 2:
        reach = 0.84
        put(visible & (v == 0) & (frac <= reach), CORE[1])
        put(visible & (v == 0) & (frac <= reach - 0.18), CORE[2])
        halo = visible & (np.abs(v) == 1) & (frac <= reach - 0.12)
        put(halo, HALO[0])
        put(halo & (frac <= reach - 0.40), HALO[1])
    else:
        # symmetric bloom across the blade face: gold, pale, WHITE, pale, gold
        core = visible & (v == 0)
        put(core, CORE[2])
        put(core & (frac < 0.9), CORE[3])
        put(visible & (np.abs(v) == 1), CORE[2])
        put(visible & (np.abs(v) == 2), HALO[2])
        put(visible & (v == 2) & (frac < 0.55), HALO[3])
        # a four-point glint just below the tip; its arms break the outline so
        # the point itself looks like it is shining
        tx, ty = tip_pixel()
        gx, gy = tx - 1, ty + 1
        arms = {(0, 0): SPARK, (-1, 0): SPARK_ARM, (1, 0): SPARK_ARM, (0, -1): SPARK_ARM,
                (0, 1): SPARK_ARM, (2, 0): SPARK_ARM, (0, -2): SPARK_ARM}
        for (dx, dy), c in arms.items():
            x, y = gx + dx, gy + dy
            if 0 <= x < S and 0 <= y < S:
                img[y, x, :3] = c
                img[y, x, 3] = 255
        # two white-hot pixels further down the edges
        for (uu, vv) in ((12, -K), (0, K)):
            put(visible & (u == uu) & (v == vv), SPARK)
    return img


def tip_pixel():
    u, v, xs, ys = uv_grid()
    m = blade_body() & (v == 0) & (u == U_TIP)
    y, x = np.argwhere(m)[0]
    return int(x), int(y)


def debug_strip(images, path):
    """8x and 1x/2x views side by side, on mid grey, for tuning by eye."""
    k = 8
    pad = 10
    cell = S * k + pad
    h = S * k + pad * 2 + S * 2 + pad
    w = pad + cell * len(images)
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[..., :3] = 110
    out[..., 3] = 255
    for i, im in enumerate(images):
        x0 = pad + i * cell
        over(out, upscale(im, k), x0, pad)
        over(out, im, x0, pad * 2 + S * k)
        over(out, upscale(im, 2), x0 + S + pad, pad * 2 + S * k)
    save_png(out, path)


def main():
    out = TEX / "item"
    base = build_base()
    stages = [build_glow(base, st) for st in (1, 2, 3)]
    paths = [save_png(base, out / "meridian.png")]
    for st, im in zip((1, 2, 3), stages):
        paths.append(save_png(im, out / f"meridian_glow{st}.png"))
    debug_strip([base] + stages, PREVIEWS / "meridian_stages.png")
    for p in paths:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
