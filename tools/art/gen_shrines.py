"""The boss shrines (Aetheria 1.1, tasks B4 and B5): one GeckoLib block model per guardian, its texture, its glowmask
and its idle animation, through the pipeline every guardian uses (armor_model.py's cubes, box UV packing and painting;
GuardianGlowLayer adds the glowmask in game, unshaded and full bright).

Each shrine's shape comes from tools/art/shrines/<kind>.cubes.json when that file exists (task B5 builds them in
Blender: tools/art/blender/shrine_models.py), otherwise from the placeholder below: a plinth, a column with a band and
a glowing cap that bobs and turns. Model units: 16 to a block, feet at y = 0, the front toward -Z (it looks along the
block's facing), the block's middle at x = z = 0.

A cube's paint names an entry in its kind's palette: "ramp" (hex colours, dark to light), and optionally "glow" (0 to 1:
how much of its colour the glowmask adds), "spots" ([rate, hex]: that share of its texels takes the colour and glows
with it), "speckle" (the share of texels a step darker, 0.18 unless given), "facets" (true: shaded as a cut gem, see
facet_index, instead of lighter top rows and darker bottom rows), "radial" ([inner, outer]: on front and back faces the
step follows the distance from the face's middle, lightest inside `inner` and darkest from `outer`, as fractions of the
way to a corner), "core" ({"paint": name, "radius": r}: on front and back faces, texels nearer the middle than r, in
the same fractions, painted as that paint), "faces" ({face: paint}: those faces painted as another paint of the
palette), "edge" ([hex, rows, [faces]]: the top rows of those faces in that colour) and "dots" ({"colour": hex,
"glow": hex, "size": n, "at": [[face, col, row] or [face, col, row, size], ...]}: squares of texels in that colour,
lit by the glowmask in "glow", or in their own colour without it). Faces are named as
armor_model.face_rects names them (xneg, front, xpos, back, top, bottom); rows count down from a face's top. The shape
file's "anim" gives the idle loop (see animation()).

Writes, per kind (colossus, leviathan, unsung, heliarch), under src/main/resources/assets/cosmicbreach/:
  geo/block/shrine_<kind>.geo.json, animations/block/shrine_<kind>.animation.json,
  textures/block/shrine_<kind>.png and textures/block/shrine_<kind>_glowmask.png

Run:  python tools/art/gen_shrines.py [kind ...]
"""
from __future__ import annotations

import json
import math
import sys

import armor_model as am
from armor_model import Bone, Cube
from common import ART_DIR, ASSETS, rgb, save_png

KINDS = ["colossus", "leviathan", "unsung", "heliarch"]
SHAPES = ART_DIR / "shrines"
TEX_SIZE = 128
LOOP_SECONDS = 6.0

# per kind, per paint. Placeholders until task B5 themes each kind (B5 replaces a kind's whole entry).
_PLACEHOLDER = {"stone": {"ramp": ["#8e8c9c", "#b9b7c6", "#dedce8"]}, "gold": {"ramp": ["#7a5a1e", "#c9963a", "#f2d27a"]},
                "glow": {"ramp": ["#2fbfb0", "#7feadf", "#e3fffb"], "glow": 1.0}}
PALETTES = {kind: dict(_PLACEHOLDER) for kind in KINDS}
# 1, the Prism Shrine: the Crown Spire's starfall stone, the dark turquoise of the Colossus's own pillar, the gold of
# its wrist rings, and its turquoise crystal cut in facets as its own body is (gen_colossus.py's CRYSTAL and DEEP
# ramps), the widest band and the point lit so the gem stays the brightest thing at dusk
PALETTES["colossus"] = {
    "stone": {"ramp": ["#a79f90", "#c2bbac", "#d6d0c3", "#e4dfd4", "#efece3"]},
    "pillar": {"ramp": ["#10403f", "#185c5a", "#217b76", "#2f9c93", "#57c1b6"], "facets": True, "speckle": 0.0},
    "gold": {"ramp": ["#9b6a24", "#c98f35", "#e8b94f", "#f7dc86", "#fff4c8"], "facets": True, "speckle": 0.0},
    "crystal": {"ramp": ["#259790", "#3db6ab", "#6dd6ca", "#aaf0e7", "#e6fffb"], "glow": 0.45, "facets": True, "speckle": 0.0},
    "crystal_band": {"ramp": ["#259790", "#3db6ab", "#6dd6ca", "#aaf0e7", "#e6fffb"], "glow": 0.8, "facets": True,
                     "speckle": 0.0},
    "tip": {"ramp": ["#3db6ab", "#6dd6ca", "#aaf0e7"], "glow": 0.7, "facets": True, "speckle": 0.0},
}
# 2, the Tide Shrine: charcoal driftstone, the whale's blues, six 2x2 spots scattered along the trunk's flanks (a
# saturated cyan by day so the trunk reads whole, the boss's bright spot cyan in the glowmask only), flukes blue on
# every upward face with the boss's mid silver on the top row of their broad faces (their trailing edges), the warm
# song gland (gen_leviathan.py)
PALETTES["leviathan"] = {
    "rock": {"ramp": ["#24262c", "#2f3239", "#3b3f47", "#494e57", "#585e68"]},
    "hide": {"ramp": ["#2c4f82", "#375f98", "#4270ae", "#4c7dbe", "#5e90cf"],
             "dots": {"colour": "#3fb6d0", "glow": "#a8f7ff", "size": 2,
                      "at": [["front", 0, 3], ["front", 4, 6], ["xneg", 1, 1], ["xneg", 3, 4], ["xpos", 2, 2],
                             ["xpos", 0, 5]]}},
    "fluke": {"ramp": ["#375f98", "#4270ae", "#4c7dbe"], "edge": ["#b4c8e2", 1, ["front", "back"]]},
    "gland": {"ramp": ["#f2c265", "#ffe39a", "#fff4d0"], "glow": 1.0},
}
# 3, the Choir Shrine: umbral basalt, dark rift glass with clumps of lichen in a deep magenta, dimly lit so they never
# outshine the mask (the pane's front only), the window's point one solid softly lit teal panel, the glass's back faces
# plain basalt, cream porcelain with a faint glow so the mask stays the brightest thing at dusk, warm inner light
# (gen_unsung.py)
PALETTES["unsung"] = {
    "basalt": {"ramp": ["#15131c", "#1c1a26", "#2c2938", "#403b52", "#544d6a"]},
    "glass": {"ramp": ["#160d1e", "#1f1229", "#2a1838", "#3a2150"], "speckle": 0.1, "faces": {"back": "basalt"},
              "dots": {"colour": "#9c3aa6", "glow": "#4a1a50", "size": 3,
                       "at": [["front", 0, 18], ["front", 1, 17, 2], ["front", 6, 19], ["front", 5, 20, 2],
                              ["front", 3, 15, 2]]}},
    "glass_point": {"ramp": ["#1d5c5a", "#267a74", "#2f948c"], "glow": 0.45, "speckle": 0.05, "faces": {"back": "basalt"}},
    "porcelain": {"ramp": ["#9c9284", "#b8ae9c", "#c9c0b0", "#ded6c6", "#f0eadc"], "glow": 0.25, "speckle": 0.05},
    "inner": {"ramp": ["#ffba48", "#ffd680", "#ffeab0"], "glow": 1.0, "speckle": 0.0},
}
# 4, the Eclipse Shrine: ivory, the dark bronze of the gauntlets, gilt, and the sun: on both faces a round near black
# core in a corona painted as flame, pale gold against the core and ember at the points, without speckle, its edges in
# the same fire (gen_heliarch.py's GOLD, BRONZE and EMBER ramps; the corona is the eclipse locks' in gen_sanctum.py)
PALETTES["heliarch"] = {
    "ivory": {"ramp": ["#c2b799", "#cfc4aa", "#e0d7c0", "#efe8d6", "#f8f4ea"], "speckle": 0.1},
    "bronze": {"ramp": ["#171009", "#261c13", "#382a1c", "#4d3b27", "#6b5436"]},
    "gilt": {"ramp": ["#6e4416", "#a46a22", "#d19232", "#f2bd4a", "#ffe08a"], "facets": True, "speckle": 0.0},
    "eclipse": {"ramp": ["#0b0910", "#15121c", "#211c2a"], "speckle": 0.1},
    "sun": {"ramp": ["#e0601a", "#ff9a2a", "#ffcf5a", "#fff4c0"], "glow": 1.0, "speckle": 0.0, "radial": [0.68, 1.0],
            "core": {"paint": "eclipse", "radius": 0.66}},
}


def placeholder():
    root = {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": [
        {"origin": [-7, 0, -7], "size": [14, 4, 14], "paint": "stone"},
        {"origin": [-3, 4, -3], "size": [6, 16, 6], "paint": "stone"},
        {"origin": [-3.5, 12, -3.5], "size": [7, 2, 7], "paint": "gold"},
    ]}
    glow = {"name": "glow", "parent": "root", "pivot": [0, 24, 0], "cubes": [
        {"origin": [-3, 21, -3], "size": [6, 6, 6], "paint": "glow"}]}
    return {"bones": [root, glow], "anim": {"length": LOOP_SECONDS, "bones": {"glow": {"bob": 1.5, "spin_y": 90.0}}}}


def load_shape(kind):
    path = SHAPES / f"{kind}.cubes.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else placeholder()


def to_bones(kind, shape):
    palette = PALETTES[kind]
    bones = []
    for b in shape["bones"]:
        cubes = []
        for i, c in enumerate(b["cubes"]):
            if c["paint"] not in palette:
                raise SystemExit(f"shrine_{kind}: paint {c['paint']!r} is not in its palette {sorted(palette)}")
            cubes.append(Cube(tuple(c["origin"]), tuple(c["size"]), c["paint"],
                              rotation=tuple(c["rotation"]) if "rotation" in c else None,
                              pivot=tuple(c["pivot"]) if "pivot" in c else None,
                              tag=f"{b['name']}_{i}"))
        bones.append(Bone(b["name"], b.get("parent"), tuple(b["pivot"]), cubes=cubes))
    return bones


def facet_index(face, r, c, h, w, base):
    """A ramp step for a cut-gem texel (gen_colossus.py's crystal): top faces a step lighter and bottoms a step darker,
    each face split on its diagonal into two facets a step apart, and a lit rim along its edges (not on the bottom)."""
    k = base + (1 if face == "top" else -1 if face == "bottom" else 0)
    u = (c + 0.5) / w
    v = (r + 0.5) / h
    if (u < v) if face in ("top", "bottom") else (u + v > 1.0):
        k -= 1
    if face != "bottom" and (r == 0 or c == 0 or c == w - 1):
        k += 1
    return k


def from_middle(r, c, h, w):
    """How far a texel is from its face's middle, as a fraction of the way to a corner."""
    return math.hypot((c + 0.5) / w - 0.5, (r + 0.5) / h - 0.5) / math.sqrt(0.5)


def radial_index(r, c, h, w, steps, band):
    """A ramp step by distance from the face's middle: the lightest step inside band[0], the darkest from band[1]
    (fractions of the way from the middle to a corner)."""
    t = min(1.0, max(0.0, (from_middle(r, c, h, w) - band[0]) / (band[1] - band[0])))
    return int(round((1.0 - t) * (steps - 1)))


def dot_mask(dots, face, w, h):
    """Which texels of a face the paint's dots cover."""
    mask = [[False] * w for _ in range(h)]
    for spot in dots["at"]:
        if spot[0] != face:
            continue
        size = int(spot[3]) if len(spot) > 3 else int(dots.get("size", 2))
        for r in range(int(spot[2]), min(h, int(spot[2]) + size)):
            for c in range(int(spot[1]), min(w, int(spot[1]) + size)):
                mask[r][c] = True
    return mask


def painter(kind):
    palette = PALETTES[kind]

    def paint(cube, face, w, h):
        p = palette[cube.paint]
        if face in p.get("faces", {}):
            p = palette[p["faces"][face]]
        core = p.get("core") if face in ("front", "back") else None
        if core is not None:
            inner = paint(Cube(cube.origin, cube.size, core["paint"], tag=cube.tag), face, w, h)
        ramp = [rgb(c) for c in p["ramp"]]
        strength = float(p.get("glow", 0.0))
        spots = p.get("spots")
        speckle = float(p.get("speckle", 0.18))
        facets = bool(p.get("facets", False))
        radial = p.get("radial") if face in ("front", "back") else None
        edge = p.get("edge") if p.get("edge") and face in p["edge"][2] else None
        dots = dot_mask(p["dots"], face, w, h) if p.get("dots") else None
        base = (len(ramp) - 1) // 2
        colours = am.grid(w, h)
        glows = am.grid(w, h)
        for r in range(h):
            for c in range(w):
                if radial:
                    k = radial_index(r, c, h, w, len(ramp), radial)
                elif facets:
                    k = facet_index(face, r, c, h, w, base)
                else:
                    k = am.shade_index(face, r, h, base, top=1, bottom=-1)
                if am.hash01(cube.tag, face, r, c) < speckle:
                    k -= 1
                colour = ramp[max(0, min(len(ramp) - 1, k))]
                glow = None
                if core is not None and from_middle(r, c, h, w) < float(core["radius"]):
                    colour, glow = inner[0][r][c], inner[1][r][c]
                elif dots is not None and dots[r][c]:
                    colour = rgb(p["dots"]["colour"])
                    glow = rgb(p["dots"].get("glow", p["dots"]["colour"]))
                elif spots and am.hash01(cube.tag, face, c, r, 7) < spots[0]:
                    colour = rgb(spots[1])
                    glow = colour
                else:
                    if edge is not None and r < int(edge[1]):
                        colour = rgb(edge[0])
                    if strength > 0.0:
                        glow = tuple(int(round(v * strength)) for v in colour)
                colours[r][c] = colour
                glows[r][c] = glow
        return colours, glows

    return paint


def _tkey(t: float) -> str:
    s = f"{t:.4f}".rstrip("0")
    return s + "0" if s.endswith(".") else s


def kf(times, fn):
    return {_tkey(t): [round(float(a), 3) for a in fn(t)] for t in times}


def animation(anim):
    """The idle loop, {"length": seconds, "bones": {bone: spec}}. A spec may hold "bob" (model units up and down),
    "sway_y" and "sway_z" (degrees each way about Y or Z) and "spin_y" and "spin_z" (degrees turned in one loop: a
    multiple of the shape's own symmetry, or 360, or the loop shows a seam). Sampled 24 times a loop (GeckoLib eases
    linearly between keys)."""
    length = float(anim.get("length", LOOP_SECONDS))
    ts = [length * i / 24 for i in range(25)]

    def wave(t):
        return math.sin(2.0 * math.pi * t / length)

    bones = {}
    for name, s in anim.get("bones", {}).items():
        chans = {}
        if s.get("bob"):
            chans["position"] = kf(ts, lambda t, s=s: (0.0, s["bob"] * wave(t), 0.0))
        if any(s.get(k) for k in ("sway_y", "sway_z", "spin_y", "spin_z")):
            chans["rotation"] = kf(ts, lambda t, s=s: (
                0.0,
                s.get("sway_y", 0.0) * wave(t) + s.get("spin_y", 0.0) * t / length,
                s.get("sway_z", 0.0) * wave(t) + s.get("spin_z", 0.0) * t / length))
        if chans:
            bones[name] = chans
    return {"format_version": "1.8.0", "animations": {"idle": {"loop": True, "animation_length": length, "bones": bones}}}


def build(kind):
    shape = load_shape(kind)
    bones = to_bones(kind, shape)
    am.check_uv(bones)
    am.pack_uvs(bones, TEX_SIZE, TEX_SIZE)
    tex, glow = am.paint_texture(bones, painter(kind), TEX_SIZE, TEX_SIZE)
    save_png(tex, ASSETS / "textures" / "block" / f"shrine_{kind}.png")
    save_png(glow, ASSETS / "textures" / "block" / f"shrine_{kind}_glowmask.png")
    geo = ASSETS / "geo" / "block" / f"shrine_{kind}.geo.json"
    geo.parent.mkdir(parents=True, exist_ok=True)
    geo.write_text(am.pretty(am.geo_json(bones, f"shrine_{kind}", TEX_SIZE, TEX_SIZE)), encoding="utf-8", newline="\n")
    an = ASSETS / "animations" / "block" / f"shrine_{kind}.animation.json"
    an.parent.mkdir(parents=True, exist_ok=True)
    an.write_text(am.pretty(animation(shape.get("anim", {}))), encoding="utf-8", newline="\n")
    print(f"shrine_{kind}: {sum(len(b.cubes) for b in bones)} cubes")


def main(kinds=None):
    for kind in kinds or KINDS:
        build(kind)


if __name__ == "__main__":
    main(sys.argv[1:])
