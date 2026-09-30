"""Material and drop items (16x16, in textures/item/), in the Meridian and Starshard style:
crisp pixel art, hard alpha, a small palette per item, a dark outline, light from the top left.

The three metals share one template each for the raw chunk, the ingot and the nugget, so
they read as one family and differ only in colour:

    starsteel   pale silver-gold (the Reach)
    nebulite    cyan on top, violet underneath (the Drift)
    eclipsium   near-black with gold rims (the Deep)

Templates use shade characters: 'o' outline, '1' (darkest) to '5' (lightest), 'w' the spark.

The drops each have their own sprite: heartstone, gyre_core, gyre_blade, leviathan_scale,
leviathan_pearl, prism_heart, silent_sigil, solar_ember, hymn_crystal, solar_heart,
umbral_silk, and the starbloom_seeds.

Run:  python tools/art/gen_materials.py
"""
from __future__ import annotations

import numpy as np

from blockart import sprite
from common import TEX, pixel_centres, point_in_poly, rel, rgb, save_png

OUT = TEX / "item"

METALS = {
    "starsteel": {"o": "#3b3226", "1": "#766a51", "2": "#a2956f", "3": "#c9bd97", "4": "#e6dfc6",
                  "5": "#faf6ea", "w": "#ffffff"},
    "nebulite": {"o": "#1b1540", "1": "#3b3192", "2": "#6650d2", "3": "#5a96ea", "4": "#6fd9ee",
                 "5": "#c6f7ff", "w": "#ffffff"},
    "eclipsium": {"o": "#07050b", "1": "#151020", "2": "#231a31", "3": "#342848", "4": "#c38a2c",
                  "5": "#f5cf6c", "w": "#fff3c6"},
}

RAW = [
    "................",
    "................",
    ".....oooo.......",
    "....o5544o......",
    "...o5w4443oo....",
    "..o544443322o...",
    "..o4444333221o..",
    ".o54443333221o..",
    ".o44433332211oo.",
    ".o4433322221o4o.",
    "..o332222211o43o",
    "..o32222111o432o",
    "...oo22111o3221o",
    ".....ooo11o2211o",
    "........ooooooo.",
    "................",
]

NUGGET = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......ooo.......",
    ".....o54ooo.....",
    "....o5w443o.....",
    "....o44332o.....",
    "....o33221o.....",
    ".....oo211o.....",
    ".......ooo......",
    "................",
    "................",
    "................",
    "................",
]


def ingot_rows() -> list[str]:
    """The ingot as a box in 2:1 projection, rasterised at texel centres.

    Top face A-B-C-D (lit), the left end A-D-D'-A' (half lit) and the long front face
    D-C-C'-D' (in shadow), then an outline round the lot.
    """
    A, B, C, D = (2.0, 7.5), (10.0, 3.5), (14.0, 5.5), (6.0, 9.5)
    h = 3.0
    A2, C2, D2 = (A[0], A[1] + h), (C[0], C[1] + h), (D[0], D[1] + h)
    px, py = pixel_centres(16, 16)
    top = point_in_poly(px, py, [A, B, C, D])
    end = point_in_poly(px, py, [A, D, D2, A2]) & ~top
    front = point_in_poly(px, py, [D, C, C2, D2]) & ~top & ~end
    grid = np.full((16, 16), ".", dtype="<U1")
    grid[front] = "2"
    grid[end] = "3"
    grid[top] = "4"
    # the top face's upper-left edge catches the light; its lower edge is a step darker
    ys, xs = np.mgrid[0:16, 0:16]
    for y in range(16):
        row = np.where(top[y])[0]
        if len(row):
            grid[y, row[0]] = "5"
    for x in range(16):
        col = np.where(top[:, x])[0]
        if len(col):
            grid[col[0], x] = "5"
            if col[-1] != col[0]:
                grid[col[-1], x] = "3"
        colf = np.where(front[:, x])[0]
        if len(colf):
            grid[colf[-1], x] = "1"
    # a bright streak along the top face, the metal's shine
    for x, y in ((6, 7), (7, 6), (8, 6), (9, 5), (10, 5), (11, 4)):
        if grid[y, x] == "4":
            grid[y, x] = "w" if x in (9, 10) else "5"
    solid = grid != "."
    ring = np.zeros_like(solid)
    ring[1:, :] |= solid[:-1, :]
    ring[:-1, :] |= solid[1:, :]
    ring[:, 1:] |= solid[:, :-1]
    ring[:, :-1] |= solid[:, 1:]
    grid[ring & ~solid] = "o"
    return ["".join(r) for r in grid]





PRISM_HEART = [
    "................",
    ".......oo.......",
    "......o5wo......",
    ".....o554ao.....",
    "....o55444ao....",
    "...o5544443ao...",
    "..o554g44433ao..",
    "..o44ggg43332o..",
    "..o444g433222o..",
    "...o44433221o...",
    "....o433221o....",
    ".....o3r21o.....",
    "......oyro......",
    ".......oo.......",
    "................",
    "................",
]
PRISM_HEART_PAL = {"o": "#0f3a47", "1": "#1b6d6c", "2": "#2aa89f", "3": "#5fd8cc", "4": "#b5f7ef",
                   "5": "#effffd", "w": "#ffffff", "a": "#f2c354", "g": "#f7dc86", "r": "#ff8a7a",
                   "y": "#ffe27a"}


SOLAR_EMBER = [
    "................",
    ".......o........",
    "......o5o.......",
    "......o5o.......",
    ".....o545o......",
    "....o5w443o.....",
    "....o54432o..o..",
    "...o544332o.o3o.",
    "..o5443322oo32o.",
    "..o4433221o321o.",
    "..o4332211221o..",
    "..o3322111110o..",
    "...o32211100o...",
    "....oo2100oo....",
    "......oooo......",
    "................",
]
SOLAR_EMBER_PAL = {"o": "#4a1406", "0": "#7a200b", "1": "#b8360f", "2": "#e8631c", "3": "#ff9d33",
                   "4": "#ffd35e", "5": "#fff2b3", "w": "#ffffff"}

HYMN_CRYSTAL = [
    "................",
    "............oo..",
    "...........o5wo.",
    "..........o54ao.",
    ".....n...o544ao.",
    "....n...o5443o..",
    "...n.n.o5443o...",
    "......o5443ao...",
    ".....o5443ao..n.",
    "....o4433ao..n..",
    "...o4332ao..n.n.",
    "..o4322ao.......",
    "..o322ao........",
    "..o21ao.........",
    "...ooo..........",
    "................",
]
HYMN_CRYSTAL_PAL = {"o": "#221643", "1": "#3c2a7a", "2": "#5b43b3", "3": "#7f67dc", "4": "#a996f2",
                    "5": "#e4dcff", "w": "#ffffff", "a": "#4fd8c8", "n": "#bff5ee"}


SEEDS = [
    "................",
    "................",
    "................",
    "................",
    "........o.......",
    ".......o5o......",
    "...o...o4o......",
    "..o5o..o3o..o...",
    "..o4o...o..o5o..",
    "..o3o......o4o..",
    "...o...o...o3o..",
    "......o5o...o...",
    "......o4o.......",
    "......o3o.......",
    ".......o........",
    "................",
]
SEEDS_PAL = {"o": "#5a4219", "5": "#fff0b8", "4": "#e8c867", "3": "#2aa89f"}


def _outline(img, colour):
    solid = img[..., 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    img[grown & ~solid] = (*rgb(colour), 255)
    return img


def gyre_blade():
    """A crescent blade (it orbits the Gyre Knight): a steel spine, a cyan cutting edge, a mount hole."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)
    outer = np.hypot(px - 3.0, py - 13.0) < 11.6
    inner = np.hypot(px - 0.2, py - 15.6) < 9.8
    blade = outer & ~inner & (px > 1.2) & (py < 14.6)
    d_out = 11.6 - np.hypot(px - 3.0, py - 13.0)          # distance in from the cutting edge
    steel = ["#45535f", "#6f7d89", "#9aa7b1", "#c3ced6", "#e8eff3"]
    idx = np.clip((d_out * 1.6).astype(int), 0, 3)
    idx = 4 - idx
    for i, c in enumerate(steel):
        img[blade & (idx == i)] = (*rgb(c), 255)
    edge = blade & (d_out < 0.9)
    img[edge] = (*rgb("#5fe0ee"), 255)
    img[edge & (px + py < 15)] = (*rgb("#aef6ff"), 255)
    spine = blade & ~(outer & ~(np.hypot(px - 0.2, py - 15.6) < 10.7))
    img[spine] = (*rgb("#45535f"), 255)
    # the mount: a dark hole with a lit rim near the middle of the spine
    img[9, 8] = (*rgb("#1d2a36"), 255)
    img[8, 8] = (*rgb("#e8eff3"), 255)
    return _outline(img, "#1d2a36")


def leviathan_scale():
    """A teardrop scale, teal at the top shading to violet at the tip, with two raised ridges."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)

    def inside(scale):
        x = (px - 8.0) / scale
        y = (py - 2.0) / scale + 2.0
        top = (y <= 7.5) & (np.hypot(x, y - 7.5) <= 6.2)
        bottom = (y > 7.5) & (y < 14.4) & (np.abs(x) <= 6.2 * np.sqrt(np.clip((14.4 - y) / 6.9, 0, 1)))
        return top | bottom

    body = inside(1.0)
    grad = ["#a6f1e6", "#45c1cf", "#2f93c7", "#2f63b3", "#3f3a9e", "#5a48b0"]
    band = np.clip(((py - 1.5) / 2.2).astype(int), 0, 5)
    for i, c in enumerate(grad):
        img[body & (band == i)] = (*rgb(c), 255)
    for scale in (0.72, 0.46):
        m = inside(scale)
        er = m.copy()
        er[1:, :] &= m[:-1, :]
        er[:-1, :] &= m[1:, :]
        er[:, 1:] &= m[:, :-1]
        er[:, :-1] &= m[:, 1:]
        ridge = m & ~er & (py > 5.0)
        img[ridge] = (*rgb("#1d3570"), 255)
        lit = np.zeros_like(ridge)
        lit[:-1, :] = ridge[1:, :]
        img[lit & body & ~ridge] = (*rgb("#8fe6e6"), 255)
    img[3, 5] = (*rgb("#ffffff"), 255)
    img[4, 4] = (*rgb("#e6fffb"), 255)
    return _outline(img, "#10233f")


def silent_sigil():
    """An indigo medallion rimmed in gold, a porcelain mask on it: eye slits, the mouth sealed."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)
    dx, dy = px - 8.0, py - 8.0
    d = np.hypot(dx, dy)
    disc = d < 6.9
    img[disc] = (*rgb("#2c2150"), 255)
    img[disc & (dx + dy < -2)] = (*rgb("#3b2c6a"), 255)
    rim = disc & (d > 5.8)
    img[rim] = (*rgb("#c99532"), 255)
    img[rim & (dx + dy < -1)] = (*rgb("#f2c354"), 255)
    img[rim & (dx + dy > 4)] = (*rgb("#8a5f1e"), 255)
    mask = ((dx / 3.3) ** 2 + ((dy + 0.3) / 4.4) ** 2) < 1.0
    img[mask] = (*rgb("#f6f1e6"), 255)
    img[mask & (dx > 1.2)] = (*rgb("#d9d2c1"), 255)
    img[mask & (dy > 2.6)] = (*rgb("#c9c1ae"), 255)
    for x, y in ((6, 7), (7, 7), (9, 7), (10, 7)):
        img[y, x] = (*rgb("#231a3d"), 255)
    for x in (7, 8, 9):
        img[10, x] = (*rgb("#c42aa8"), 255)
    img[4, 7] = (*rgb("#ffffff"), 255)
    return _outline(img, "#171030")



def heartstone():
    """A round-cut red gem seen from above: a table in the middle, eight crown facets round it,
    each shaded by where it faces (lit top left, dark bottom right)."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)
    dx, dy = px - 8.0, py - 8.0
    octagon = (np.abs(dx) <= 5.6) & (np.abs(dy) <= 5.6) & (np.abs(dx) + np.abs(dy) <= 7.9)
    table = (np.abs(dx + 0.4) <= 2.6) & (np.abs(dy + 0.4) <= 2.6) & (np.abs(dx + 0.4) + np.abs(dy + 0.4) <= 3.7)
    sector = np.round(np.arctan2(dy, dx) / (np.pi / 4)).astype(int) % 8   # 0 east, 2 south, 4 west, 6 north
    shade = {5: "#ff9e98", 6: "#f27082", 4: "#e0506a", 7: "#cf3653", 3: "#b8284a", 0: "#9e1c3b", 2: "#7e1430",
             1: "#6b1029"}
    for k, c in shade.items():
        img[octagon & ~table & (sector == k)] = (*rgb(c), 255)
    img[table] = (*rgb("#e2485f"), 255)
    img[table & (dx + dy < -2.2)] = (*rgb("#ff8f8a"), 255)
    img[table & (dx + dy > 1.6)] = (*rgb("#c42c48"), 255)
    img[5, 5] = (*rgb("#ffffff"), 255)
    img[6, 5] = (*rgb("#ffd3d0"), 255)
    img[5, 6] = (*rgb("#ffd3d0"), 255)
    return _outline(img, "#3a0818")


def umbral_silk():
    """A rolled bolt of dark silk lying on the diagonal: violet-black cloth with a magenta sheen
    along its lit side, the rolled end at the top right showing the wound layers."""
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)
    r2 = np.sqrt(2.0)
    sx = ((px - 8.0) - (py - 8.0)) / r2           # along the roll, towards the top right
    tx = ((px - 8.0) + (py - 8.0)) / r2           # across it, towards the bottom right
    radius = 3.1
    body = (np.abs(tx) <= radius) & (sx >= -6.2) & (sx <= 3.9)
    cap = ((sx - 3.9) / 1.5) ** 2 + (tx / radius) ** 2 <= 1.0
    tail = ((sx + 6.2) / 1.5) ** 2 + (tx / radius) ** 2 <= 1.0
    across = ["#8b72c4", "#5d4a8c", "#3b2e5c", "#2c2342", "#1a1428"]
    band = np.clip(((tx + radius) / (2 * radius) * 5).astype(int), 0, 4)
    for i, c in enumerate(across):
        img[(body | tail) & (band == i)] = (*rgb(c), 255)
    sheen = (body | tail) & (np.abs(tx + 1.6) < 0.5)
    img[sheen] = (*rgb("#c86bd8"), 255)
    img[sheen & (sx > 0.5)] = (*rgb("#ff9ce8"), 255)
    # the rolled end: rings of cloth wound round the core
    d = np.sqrt(((sx - 3.9) / 1.5) ** 2 + (tx / radius) ** 2)
    img[cap] = (*rgb("#4a3a6c"), 255)
    img[cap & (d > 0.35) & (d < 0.6)] = (*rgb("#241c38"), 255)
    img[cap & (d <= 0.2)] = (*rgb("#130e1e"), 255)
    img[cap & (d >= 0.8)] = (*rgb("#6d56a0"), 255)
    return _outline(img, "#08060d")


def disc(radius, centre, colours, light=(-0.55, -0.6, 0.58), spec=True, outline="#000000"):
    """A shaded sphere: ramp picked by the lit fraction, hard edge, one-texel outline."""
    px, py = pixel_centres(16, 16)
    dx = (px - centre[0]) / radius
    dy = (py - centre[1]) / radius
    r2 = dx * dx + dy * dy
    inside = r2 <= 1.0
    dz = np.sqrt(np.clip(1 - r2, 0, 1))
    lam = np.clip(dx * light[0] + dy * light[1] + dz * light[2], 0, 1)
    idx = np.clip((lam * (len(colours) - 0.01)).astype(int), 0, len(colours) - 1)
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    pal = np.array([(*rgb(c), 255) for c in colours], dtype=np.uint8)
    img[inside] = pal[idx[inside]]
    if spec:
        sx = int(np.floor(centre[0] - radius * 0.45))
        sy = int(np.floor(centre[1] - radius * 0.45))
        img[sy, sx] = (255, 255, 255, 255)
    solid = img[..., 3] > 0
    ring = np.zeros_like(solid)
    ring[1:, :] |= solid[:-1, :]
    ring[:-1, :] |= solid[1:, :]
    ring[:, 1:] |= solid[:, :-1]
    ring[:, :-1] |= solid[:, 1:]
    img[ring & ~solid] = (*rgb(outline), 255)
    return img


def leviathan_pearl():
    img = disc(5.6, (8.0, 8.0), ["#4c4394", "#6d69c0", "#9aa1e0", "#c9cff4", "#eef0ff"], outline="#241f55")
    # an iridescent teal crescent on the shadow side
    px, py = pixel_centres(16, 16)
    d = np.hypot(px - 8.0, py - 8.0)
    rim = (d > 4.3) & (d <= 5.6) & ((px - 8) + (py - 8) > 3.5)
    img[rim] = (*rgb("#6fe3d6"), 255)
    img[4, 6] = (*rgb("#ffffff"), 255)
    img[5, 5] = (*rgb("#ffffff"), 255)
    return img


def gyre_core():
    img = disc(4.6, (8.0, 8.0), ["#2b3440", "#4a5663", "#6f7c89", "#9aa6b1", "#cdd6dd"], outline="#10161d")
    # the glowing eye
    for (x, y), c in {(7, 7): "#ffffff", (8, 7): "#aef6ff", (7, 8): "#aef6ff", (8, 8): "#5fe0ee",
                      (6, 7): "#5fe0ee", (7, 6): "#5fe0ee", (9, 8): "#2a9fb0", (8, 9): "#2a9fb0"}.items():
        img[y, x] = (*rgb(c), 255)
    # an orbit ring, tilted, passing in front of the core at the bottom
    px, py = pixel_centres(16, 16)
    u = (px - 8.0) * 0.866 + (py - 8.0) * 0.5
    v = -(px - 8.0) * 0.5 + (py - 8.0) * 0.866
    e = (u / 7.2) ** 2 + (v / 2.6) ** 2
    ring = (e > 0.72) & (e < 1.12)
    front = ring & (v > -0.2)
    back = ring & (v <= -0.2) & (img[..., 3] == 0)
    img[back] = (*rgb("#b7892f"), 255)
    img[front] = (*rgb("#f2c354"), 255)
    img[front & (u < -3)] = (*rgb("#fbe08a"), 255)
    solid = img[..., 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    img[grown & ~solid] = (*rgb("#10161d"), 255)
    return img


def solar_heart():
    img = np.zeros((16, 16, 4), dtype=np.uint8)
    px, py = pixel_centres(16, 16)
    dx, dy = px - 8.0, py - 8.0
    d = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    # eight rays, alternating long and short, pointed
    k = np.round(ang / (np.pi / 4)) * (np.pi / 4)
    off = np.abs(ang - k)
    long_ray = (np.round(ang / (np.pi / 4)).astype(int) % 2 == 0)
    reach = np.where(long_ray, 7.4, 6.0)
    ray = (d < reach) & (off * d < (reach - d) * 0.42 + 0.3)
    img[ray] = (*rgb("#e8631c"), 255)
    img[ray & (d < reach - 1.6)] = (*rgb("#ff9d33"), 255)
    core = d < 4.2
    img[core] = (*rgb("#ffd35e"), 255)
    img[(d < 3.0)] = (*rgb("#fff2b3"), 255)
    img[(d < 1.6)] = (*rgb("#ffffff"), 255)
    img[core & (dx + dy > 3.2)] = (*rgb("#f2a93a"), 255)
    solid = img[..., 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    img[grown & ~solid] = (*rgb("#5a1c05"), 255)
    return img


def build() -> dict[str, np.ndarray]:
    out = {}
    ingot = ingot_rows()
    for metal, pal in METALS.items():
        out[f"raw_{metal}"] = sprite(RAW, pal)
        out[f"{metal}_ingot"] = sprite(ingot, pal)
    out["starsteel_nugget"] = sprite(NUGGET, METALS["starsteel"])
    out["eclipsium_nugget"] = sprite(NUGGET, METALS["eclipsium"])
    out["heartstone"] = heartstone()
    out["gyre_core"] = gyre_core()
    out["gyre_blade"] = gyre_blade()
    out["leviathan_scale"] = leviathan_scale()
    out["leviathan_pearl"] = leviathan_pearl()
    out["prism_heart"] = sprite(PRISM_HEART, PRISM_HEART_PAL)
    out["silent_sigil"] = silent_sigil()
    out["solar_ember"] = sprite(SOLAR_EMBER, SOLAR_EMBER_PAL)
    out["hymn_crystal"] = sprite(HYMN_CRYSTAL, HYMN_CRYSTAL_PAL)
    out["solar_heart"] = solar_heart()
    out["umbral_silk"] = umbral_silk()
    out["starbloom_seeds"] = sprite(SEEDS, SEEDS_PAL)
    return out


def main():
    built = build()
    for name, img in built.items():
        save_png(img, OUT / f"{name}.png")
    print("wrote", len(built), "material item textures to", rel(OUT))


if __name__ == "__main__":
    main()
