"""Binary Edges, the twin sickles (GDD 4.2): two crescent blades like a binary star.

    textures/item/binary_edges.png          the pair, crossed, for the inventory (32x32)
    textures/item/binary_edges_glow1..3     the pair as Resonance fills
    textures/item/binary_edges_right.png    the right sickle as the main hand holds it (cyan star)
    textures/item/binary_edges_right_glow1..3
    textures/item/binary_edges_left.png     the left sickle the off hand holds (violet star)
    textures/item/binary_edges_left_glow1..3

Meridian's style (gen_meridian.py): steel shaded in bands with light from the top left, Meridian's gold and
its blue cord, hard alpha, outlines coloured by the part they wrap. One sickle: a short corded handle on the
sprite diagonal (bottom left, held where Meridian is held), a gold ferrule with its star gem, and a crescent
blade that leaves the ferrule up and to the left, arches over the top and comes down on the right to a
point: the sharp edge is the inside of the arch, where the Resonance light runs (cyan on the right sickle,
violet on the left, the two colours of the trails). A Rimeglass crystal caps the handle.

The shape is continuous (a band around a circle, tapering to the point), sampled at pixel centres, so the
pair icon can draw the same sickle smaller and mirrored.

Run:  python tools/art/gen_binary_edges.py
"""
from __future__ import annotations

import math

import numpy as np

from common import PREVIEWS, TEX, blank, outline_mask, over, rel, rgb, save_png, upscale

S = 32

# ---------------------------------------------------------------- palette
STEEL = {  # Meridian's steel
    "outline_lit": rgb("#4d5878"),
    "outline_dark": rgb("#262b45"),
    "white": rgb("#fbfdff"),
    "light": rgb("#e3eaf6"),
    "mid": rgb("#c7d2e6"),
    "shade": rgb("#a5b3cf"),
    "deep": rgb("#8391b3"),
}
GOLD = {
    "hi": rgb("#fff3b8"),
    "light": rgb("#f6cf62"),
    "mid": rgb("#d9a23a"),
    "dark": rgb("#a8701f"),
    "outline": rgb("#5a3714"),
}
GRIP = {
    "cord": rgb("#6b77b0"),
    "wrap": rgb("#454c82"),
    "deep": rgb("#2d3160"),
    "outline": rgb("#191a36"),
}
RIME = {  # Rimeglass: pale ice crystal
    "hi": rgb("#ffffff"),
    "light": rgb("#d8f4ff"),
    "mid": rgb("#9fd8f0"),
    "dark": rgb("#5c9ec8"),
    "outline": rgb("#20405e"),
}
# The two stars: the right sickle's cyan and the left one's violet (the trails' colours).
ACCENT = {
    "right": {"hi": rgb("#e8fdff"), "light": rgb("#8cf0ff"), "mid": rgb("#3cc8e8"), "dark": rgb("#1f7fa8"),
              "rest": rgb("#7ea6c4"), "rest_dark": rgb("#5f7fa6"),
              "glow": [rgb("#6fb4cc"), rgb("#7feaff"), rgb("#c4f8ff"), rgb("#ffffff")],
              "halo": [rgb("#b9d4e6"), rgb("#b8f0ff"), rgb("#8cecff"), rgb("#5fe0ff")]},
    "left": {"hi": rgb("#f6ecff"), "light": rgb("#c9a2ff"), "mid": rgb("#9a62f0"), "dark": rgb("#5d34b0"),
             "rest": rgb("#9a90c4"), "rest_dark": rgb("#766aa6"),
             "glow": [rgb("#a28cc8"), rgb("#c79cff"), rgb("#e8d4ff"), rgb("#ffffff")],
             "halo": [rgb("#cbbfe0"), rgb("#dcc4ff"), rgb("#c49aff"), rgb("#a878ff")]},
}
SPARK = rgb("#ffffff")

# ---------------------------------------------------------------- the canonical sickle (pixel units, y down)
GRIP_R = (8.2, 22.8)            # texel where the fist holds it: Meridian's (the rig and engine animations share it)
POMMEL = (3.5, 28.5)            # pixel centre of the Rimeglass cap
HANDLE_HALF = 1.35              # the handle's half width across the diagonal
FERRULE = (-1.2, 1.2)           # along the handle from its top: the gold band
CENTRE = (20.25, 14.6)          # the crescent's circle
RADIUS = 9.6                    # its outer edge (the spine)
THETA0 = math.radians(150.0)    # where the blade leaves the ferrule (lower left of the circle)
THETA1 = math.radians(364.0)    # the point (right, a little below the centre)
# The blade's width along the arc (t 0 at the ferrule, 1 at the point): a crescent, widest over the top left.
WIDTHS = [(0.0, 3.3), (0.22, 4.7), (0.45, 4.4), (0.7, 2.9), (0.88, 1.5), (1.0, 0.55)]
WIDTH0 = WIDTHS[0][1]
WIDTH1 = WIDTHS[-1][1]

AXIS = np.array([1.0, -1.0]) / math.sqrt(2.0)   # along the handle, toward the blade
ACROSS = np.array([1.0, 1.0]) / math.sqrt(2.0)  # across it


def _blade_base():
    """The middle of the blade where it leaves the ferrule: the top of the handle."""
    mid = RADIUS - WIDTH0 / 2.0
    return np.array([CENTRE[0] + mid * math.cos(THETA0), CENTRE[1] + mid * math.sin(THETA0)])


HANDLE_TOP = _blade_base()
POMMEL_C = np.array([POMMEL[0], POMMEL[1]])


def width_at(t):
    """Blade width at a fraction t of the arc (0 at the ferrule, 1 at the point), smooth through WIDTHS."""
    t = np.clip(np.asarray(t, dtype=float), 0.0, 1.0)
    ts = np.array([k for k, _ in WIDTHS])
    ws = np.array([w for _, w in WIDTHS])
    i = np.clip(np.searchsorted(ts, t, side="right") - 1, 0, len(ts) - 2)
    f = (t - ts[i]) / (ts[i + 1] - ts[i])
    f = f * f * (3.0 - 2.0 * f)
    return ws[i] + (ws[i + 1] - ws[i]) * f


def classify(x, y):
    """Part ids at canonical points (x, y) (pixel units), and for blade points the arc fraction and the depth
    into the blade from the spine (0) to the edge (1).

    parts: 0 none, 1 Rimeglass cap, 2 cord, 3 ferrule, 4 star gem, 5 blade
    """
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    part = np.zeros(x.shape, dtype=int)
    # the handle, from the pommel to the top, 1.35 either side of its line
    rel_x, rel_y = x - HANDLE_TOP[0], y - HANDLE_TOP[1]
    along = rel_x * AXIS[0] + rel_y * AXIS[1]            # 0 at the top, negative toward the pommel
    across = rel_x * ACROSS[0] + rel_y * ACROSS[1]
    length = float(np.linalg.norm(HANDLE_TOP - POMMEL_C))
    handle = (np.abs(across) <= HANDLE_HALF) & (along <= 0.2) & (along >= -length + 1.2)
    part[handle] = 2
    ferrule = (np.abs(across) <= 2.05) & (along >= FERRULE[0] - 1.2) & (along <= FERRULE[1] - 1.0)
    part[ferrule] = 3
    gem = (np.abs(across) + np.abs(along - (FERRULE[0] - 0.1)) <= 1.05)
    part[gem] = 4
    # the Rimeglass cap: a small diamond at the end
    cap = (np.abs(x - POMMEL_C[0]) + np.abs(y - POMMEL_C[1]) <= 2.05)
    part[cap] = 1
    # the blade: a band inside the circle, tapering from the ferrule to the point
    dx, dy = x - CENTRE[0], y - CENTRE[1]
    r = np.hypot(dx, dy)
    theta = np.arctan2(dy, dx)
    theta = np.where(theta < THETA0 - 1e-9, theta + 2 * math.pi, theta)   # unwrap past the ferrule
    t = (theta - THETA0) / (THETA1 - THETA0)
    w = width_at(t)
    depth = (RADIUS - r) / np.maximum(w, 1e-6)
    blade = (t >= -0.02) & (t <= 1.0) & (r <= RADIUS) & (depth <= 1.0)
    part[blade & (part == 0)] = 5
    part[blade & (part == 2)] = 5
    return part, t, depth, theta


def render_sickle(side: str, stage: int = 0, transform=None, size: int = S) -> np.ndarray:
    """One sickle, {side} 'right' or 'left' (its accent), at Resonance glow {stage} 0..3. {transform} maps
    output pixel centres to canonical ones (the pair icon); None is the canonical sprite."""
    acc = ACCENT[side]
    img = blank(size, size)
    ys, xs = np.mgrid[0:size, 0:size]
    px, py = xs + 0.5, ys + 0.5
    cx, cy = (px, py) if transform is None else transform(px, py)
    part, t, depth, theta = classify(cx, cy)
    col = np.zeros((size, size, 3), dtype=int)

    # ---- cord: Meridian's spiral wrap, by the diagonal position
    cord = part == 2
    k = np.floor((cx - cy) / 1.0).astype(int)
    phase = k % 4
    col[cord] = GRIP["cord"]
    col[cord & (phase >= 2)] = GRIP["wrap"]
    rel_x, rel_y = cx - HANDLE_TOP[0], cy - HANDLE_TOP[1]
    across = rel_x * ACROSS[0] + rel_y * ACROSS[1]
    col[cord & (across > 0.45) & (phase >= 2)] = GRIP["deep"]

    # ---- ferrule and star gem
    fer = part == 3
    col[fer] = GOLD["mid"]
    col[fer & (across < -0.5)] = GOLD["light"]
    col[fer & (across > 0.9)] = GOLD["dark"]
    gem = part == 4
    col[gem] = acc["mid"]
    col[gem & (across < 0)] = acc["light"]
    col[gem & (across > 0.6)] = acc["dark"]

    # ---- Rimeglass cap
    cap = part == 1
    d = (cx - POMMEL_C[0]) + (cy - POMMEL_C[1])
    col[cap] = RIME["mid"]
    col[cap & (d < -0.5)] = RIME["light"]
    col[cap & (d > 0.8)] = RIME["dark"]
    col[cap & (np.abs(cx - POMMEL_C[0] + 0.7) < 0.5) & (np.abs(cy - POMMEL_C[1] + 0.7) < 0.5)] = RIME["hi"]

    # ---- blade: bands from the spine to the edge; the spine is lit where it faces the top left
    blade = part == 5
    facing = -(np.cos(theta) * -0.707 + np.sin(theta) * -0.707)   # 1 where the outward normal points to the top left
    lit = -facing
    spine = blade & (depth < 0.30)
    body = blade & (depth >= 0.30) & (depth < 0.62)
    groove = blade & (depth >= 0.62) & (depth < 0.82)
    edge = blade & (depth >= 0.82)
    col[spine] = STEEL["mid"]
    col[spine & (lit > 0.35)] = STEEL["light"]
    col[spine & (lit < -0.45)] = STEEL["shade"]
    col[body] = STEEL["shade"]
    col[body & (lit > 0.2)] = STEEL["mid"]
    col[body & (lit < -0.5)] = STEEL["deep"]
    # the groove next to the edge carries a hint of the star's colour: the Resonance light runs in it
    col[groove] = acc["rest_dark"]
    col[groove & (lit > -0.2)] = acc["rest"]
    col[edge] = STEEL["white"]
    col[edge & (lit < -0.6)] = STEEL["light"]
    # thin tips have no room for bands: steel with a white edge pixel
    thin = blade & (width_at(t) < 1.6)
    col[thin] = STEEL["light"]
    col[thin & (depth > 0.5)] = STEEL["white"]

    if stage > 0:
        glow_run(col, blade, groove | (thin & (depth > 0.2)), edge, t, stage, acc)

    filled = part > 0
    img[filled, :3] = col[filled]
    img[filled, 3] = 255

    # ---- outline by the part it wraps
    ol = outline_mask(filled)
    part_of = np.zeros((size, size), dtype=int)
    for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        shifted = np.zeros_like(part)
        h, w_ = part.shape
        ys0 = slice(max(0, -ddy), h - max(0, ddy))
        xs0 = slice(max(0, -ddx), w_ - max(0, ddx))
        ys1 = slice(max(0, ddy), h - max(0, -ddy))
        xs1 = slice(max(0, ddx), w_ - max(0, -ddx))
        shifted[ys0, xs0] = part[ys1, xs1]
        part_of = np.maximum(part_of, shifted)
    # outline positions in canonical space, to light the blade's outline on its top-left side
    ocx, ocy = cx, cy
    out_theta = np.arctan2(ocy - CENTRE[1], ocx - CENTRE[0])
    out_r = np.hypot(ocx - CENTRE[0], ocy - CENTRE[1])
    outer_side = out_r > RADIUS - 1.0
    out_lit = (np.cos(out_theta) * -0.707 + np.sin(out_theta) * -0.707) > 0.2
    ob = ol & (part_of == 5)
    img[ob, :3] = STEEL["outline_dark"]
    img[ob & outer_side & out_lit, :3] = STEEL["outline_lit"]
    img[ol & ((part_of == 3) | (part_of == 4)), :3] = GOLD["outline"]
    img[ol & (part_of == 2), :3] = GRIP["outline"]
    img[ol & (part_of == 1), :3] = RIME["outline"]
    img[ol, 3] = 255
    if stage == 3:
        tip_glint(img, side, transform, size)
    return img


def glow_run(col, blade, groove, edge, t, stage, acc):
    """Resonance light along the groove from the ferrule toward the point: further and hotter each stage."""
    reach = {1: 0.5, 2: 0.85, 3: 1.01}[stage]
    core = groove & (t <= reach)
    col[core] = acc["mid"] if stage == 1 else acc["glow"][stage - 1]
    col[groove & (t <= reach - 0.25)] = acc["light"] if stage == 1 else acc["glow"][stage]
    if stage >= 2:
        col[edge & (t <= reach - 0.15)] = acc["halo"][stage]
    if stage == 3:
        col[groove & (t <= 0.9)] = acc["glow"][3]
        col[edge] = acc["light"]


def tip_glint(img, side, transform, size):
    """A four-point glint at the point of the blade (stage 3)."""
    t_tip = THETA1 - math.radians(8.0)
    rr = RADIUS - WIDTH1
    canon = (CENTRE[0] + rr * math.cos(t_tip), CENTRE[1] + rr * math.sin(t_tip))
    # find the output pixel whose canonical centre is nearest
    ys, xs = np.mgrid[0:size, 0:size]
    px, py = xs + 0.5, ys + 0.5
    cx, cy = (px, py) if transform is None else transform(px, py)
    d = (cx - canon[0]) ** 2 + (cy - canon[1]) ** 2
    gy, gx = np.unravel_index(np.argmin(d), d.shape)
    arm = ACCENT[side]["hi"]
    for (dx, dy), c in {(0, 0): SPARK, (-1, 0): arm, (1, 0): arm, (0, -1): arm, (0, 1): arm, (0, -2): arm}.items():
        x, y = gx + dx, gy + dy
        if 0 <= x < size and 0 <= y < size:
            img[y, x, :3] = c
            img[y, x, 3] = 255


# ---------------------------------------------------------------- the pair icon


def pair_transform(mirror: bool, scale: float, shift, turn_deg: float = 0.0):
    """Output pixel centre -> canonical: the canonical sickle scaled by {scale} about its grip and turned
    {turn_deg} clockwise, mirrored left to right if asked, its grip moved to {shift}."""
    gx, gy = GRIP_R[0] + 0.5, GRIP_R[1] + 0.5
    c, s_ = math.cos(math.radians(turn_deg)), math.sin(math.radians(turn_deg))

    def f(px, py):
        x = (px - shift[0]) / scale
        y = (py - shift[1]) / scale
        if mirror:
            x = -x
        # undo a clockwise turn (y is down, so clockwise is +angle)
        xr = c * x + s_ * y
        yr = -s_ * x + c * y
        return xr + gx, yr + gy

    return f


def render_pair(stage: int = 0) -> np.ndarray:
    """The two sickles crossed at their handles, crescents out to either side above, a small star between."""
    right = render_sickle("right", stage, pair_transform(False, 0.8, (11.0, 25.0), 9.0))
    left = render_sickle("left", stage, pair_transform(True, 0.8, (21.0, 25.0), 9.0))
    img = blank(S, S)
    over(img, left, 0, 0)
    over(img, right, 0, 0)
    # the binary star: a small four-point glint above where the blades cross
    cx, cy = 16, 3
    star = {(0, 0): SPARK, (-1, 0): rgb("#c4f8ff"), (1, 0): rgb("#e8d4ff"), (0, -1): rgb("#fff3b8"), (0, 1): rgb("#fff3b8")}
    if stage >= 2:
        star.update({(-2, 0): ACCENT["right"]["light"], (2, 0): ACCENT["left"]["light"]})
    for (dx, dy), c in star.items():
        if img[cy + dy, cx + dx, 3] == 0:
            img[cy + dy, cx + dx, :3] = c
            img[cy + dy, cx + dx, 3] = 255
    return img


# ---------------------------------------------------------------- key points


def key_points():
    """Texel coordinates (x right, y down, centres at .5 minus .5) of the points the rig and the effects use:
    the grip, the ferrule (the blade's root) and the crescent's apex (the part that leads a swing), and the
    blade's half width for the trails."""
    ferrule = HANDLE_TOP + AXIS * 0.0
    apex_theta = math.radians(290.0)   # the top of the arch, a little right of centre: first into a hook
    mid = RADIUS - width_at((apex_theta - THETA0) / (THETA1 - THETA0)) / 2.0
    apex = (CENTRE[0] + mid * math.cos(apex_theta), CENTRE[1] + mid * math.sin(apex_theta))
    point = (CENTRE[0] + (RADIUS - WIDTH1) * math.cos(THETA1 - 0.05), CENTRE[1] + (RADIUS - WIDTH1) * math.sin(THETA1 - 0.05))
    def tex(p):
        return (round(float(p[0]) - 0.5, 2), round(float(p[1]) - 0.5, 2))
    return {"grip": GRIP_R, "pommel": tex(POMMEL_C), "ferrule": tex(ferrule), "apex": tex(apex), "point": tex(point),
            "half_width": 3.0}


def debug_strip(images, path):
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
    paths = []
    sets = {}
    for name, make in (("binary_edges", render_pair), ("binary_edges_right", lambda st: render_sickle("right", st)),
                       ("binary_edges_left", lambda st: render_sickle("left", st))):
        images = [make(st) for st in (0, 1, 2, 3)]
        sets[name] = images
        paths.append(save_png(images[0], out / f"{name}.png"))
        for st in (1, 2, 3):
            paths.append(save_png(images[st], out / f"{name}_glow{st}.png"))
    debug_strip(sets["binary_edges"] + sets["binary_edges_right"][:1] + sets["binary_edges_left"][:1],
                PREVIEWS / "binary_edges_stages.png")
    debug_strip(sets["binary_edges_right"] + sets["binary_edges_left"][3:], PREVIEWS / "binary_edges_sickles.png")
    for p in paths:
        print("wrote", rel(p))
    print("key points:", key_points())


if __name__ == "__main__":
    main()
