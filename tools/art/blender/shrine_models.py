"""The four boss shrines' shapes (Aetheria 1.1, task B5), built in Blender from boxes in GeckoLib's own units.

Each design is bones of boxes (Bedrock units: 16 a block, feet at y 0, the front toward -Z, the block's middle at
x = z = 0), each box naming a paint of gen_shrines.py's palette, plus the idle loop. This script places them with
GeckoLib's maths (shrine_common.quads), checks them (the render box's limits; parts marked to lean out or rise do;
no two faces of different boxes lie in one plane facing the same way and overlapping, which would z-fight in game;
no sky shows through it from the front or back; over the idle loop no moving part passes through a still one and
nothing leaves the render box), writes tools/art/shrines/<kind>.cubes.json and renders
clay views (Workbench, one clay colour, outlines) from five sides and from 24 blocks off, to judge the silhouette
before any paint.

  blender.exe --background --factory-startup --python tools/art/blender/shrine_models.py -- --kinds colossus --out <dir>

With --names (one per kind) the clay files carry those names instead of the kinds (a blind round; preview_shrines.py
--deal picks them).

Rotation signs, worked out from bedrock_model.gecko_rot (GeckoLib negates x and y rotations and mirrors x on load):
a part on the front (-Z) leans out with a positive x rotation, on the back with a negative one; a part on the +X side
(Bedrock) leans out with a positive z rotation, on the -X side with a negative one; a flat arm reaching to +X rises
with a negative z rotation, to -X with a positive one. The checks prove it on every run.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import shrine_common as sc  # noqa: E402

LIMIT = 20.0      # x and z: at most three quarters of a block past the cell (whose half is 8), at rest and in the loop
BASE = 8.0        # anything under y 4 stays inside the cell
TOP = 44.0        # under 2.75 blocks at rest
RENDER_TOP = 48.0  # the renderer's box is three blocks tall (and reaches 24 each way): nothing leaves it in the loop
MAX_CUBES = 40


def box(x, y, z, sx, sy, sz, paint, rot=None, pivot=None, check=None):
    c = {"origin": [x, y, z], "size": [sx, sy, sz], "paint": paint}
    if rot is not None:
        c["rotation"] = list(rot)
        c["pivot"] = list(pivot) if pivot is not None else [x + sx / 2.0, y + sy / 2.0, z + sz / 2.0]
    if check:
        c["check"] = check  # "out": its top leans away from the axis; "up": its far end is higher than its near end
    return c


def stack(y0, levels, paint):
    """A gem of boxes turned 45 degrees about Y, stacked up from y0; levels are (side, height) from the bottom point."""
    out, y = [], y0
    for s, h in levels:
        out.append(box(-s / 2, y, -s / 2, s, h, s, paint, rot=(0, 45, 0), pivot=(0, y + h / 2, 0)))
        y += h
    return out


def flukes(y, inner, outer, a_in, a_out, depth=2.0):
    """A whale's two flukes spreading along X from a notch at x = +-1: an inner blade (length, height) rising a_in
    degrees and an outer tip (length, height) rising a_out degrees from where the inner blade's middle line ends, a
    unit back so the two overlap; the tip a half unit thinner so no face of it lies in a face of the blade."""
    (li, hi), (lo, ho) = inner, outer
    out = []
    for s in (1, -1):
        x0 = 1.0 if s > 0 else -1.0 - li
        out.append(box(x0, y, -depth / 2, li, hi, depth, "fluke", rot=(0, 0, -s * a_in), pivot=(s * 1.0, y + hi / 2, 0),
                       check="up"))
        ex = s * (1.0 + (li - 1.0) * math.cos(math.radians(a_in)))
        ey = y + hi / 2 + (li - 1.0) * math.sin(math.radians(a_in))
        x1 = ex if s > 0 else ex - lo
        out.append(box(x1, ey - ho / 2, -depth / 2 + 0.25, lo, ho, depth - 0.5, "fluke", rot=(0, 0, -s * a_out),
                       pivot=(ex, ey, 0), check="up"))
    return out


def lancet(foot_y, spring_y, half_out, pillar_w, apex_y, bar_t, depth, paint):
    """Two pillars and a pointed arch of two slanted bars meeting at the apex, each bar running from above its pillar's
    middle to the apex and a bar's thickness longer, so both joints close. The bars sit half a unit shallower than the
    pillars, and the second bar half a unit shallower again, so no front or back faces share a plane."""
    cubes = [
        box(-half_out, foot_y, -depth / 2, pillar_w, spring_y - foot_y, depth, paint),
        box(half_out - pillar_w, foot_y, -depth / 2, pillar_w, spring_y - foot_y, depth, paint),
    ]
    mid = half_out - pillar_w / 2.0
    dy = apex_y - spring_y
    length = math.hypot(mid, dy) + bar_t
    angle = math.degrees(math.atan2(dy, mid))
    for s, d in ((1, depth - 0.5), (-1, depth - 1.0)):
        cx, cy = s * mid / 2.0, (spring_y + apex_y) / 2.0
        # a bar along X turned so its far end comes down onto its pillar: reaching to +X it falls with a positive z
        # rotation, to -X with a negative one (the arm rule above, the other way)
        cubes.append(box(cx - length / 2.0, cy - bar_t / 2.0, -d / 2.0, length, bar_t, d, paint,
                         rot=(0, 0, s * angle), pivot=(cx, cy, 0)))
    return cubes


def mask(cy, w, h, z_front):
    """A porcelain mask facing front: a face, a narrower brow and chin (an oval at a glance), two wide lit eyes and a
    small open mouth standing a half unit proud of it. The brow and chin sit a quarter unit back from the face's front,
    so their faces never share its planes."""
    zf = z_front
    return [
        box(-w / 2, cy - h / 2, zf, w, h, 2, "porcelain"),
        box(-w / 2 + 1, cy + h / 2, zf + 0.25, w - 2, 2, 1.5, "porcelain"),
        box(-w / 2 + 1.5, cy - h / 2 - 2, zf + 0.25, w - 3, 2, 1.5, "porcelain"),
        box(-w / 2 + 0.75, cy + 0.5, zf - 0.5, 2.5, 1.5, 1, "inner"),
        box(w / 2 - 3.25, cy + 0.5, zf - 0.5, 2.5, 1.5, 1, "inner"),
        box(-1, cy - 3.5, zf - 0.5, 2, 2, 1, "inner"),
    ]


def star(cy, side, z_front, depth, paint, shrink, n=3):
    """A star of 4n points facing front: n squares turned 90/n degrees apart (their union, so each square's corners are
    the points), centred at height cy. Each square sits a twentieth of a unit further back than the last; with
    `shrink` it is also a tenth thinner, so its back face moves forward (a disc in front of a ring), otherwise it keeps
    its depth (and so a unit of texels on every side). No two of them share a plane."""
    cubes = []
    for i in range(n):
        a = 90.0 / n * i
        dz = 0.05 * i
        d = depth - 2 * dz if shrink else depth
        cubes.append(box(-side / 2, cy - side / 2, z_front + dz, side, side, d, paint,
                         rot=(0, 0, a) if a else None, pivot=(0, cy, z_front + depth / 2) if a else None))
    return cubes


DESIGNS = {}

# 1, the Prism Shrine (the Colossus): a stepped plinth of starfall stone, a short pillar of the Colossus's own dark
# turquoise crystal (it grows out of one) under a gold collar (the gold of its wrist rings), and over it the Colossus's
# turquoise crystal, a cut gem taller than the stand, turning and bobbing, its widest band and its top point lit; the
# gap under its lower point shows it floats. Round 3: the four crown points came off (the one set of radiating repeated
# parts) and the column became the crystal pillar, which also sets it apart from shrine 4's pale stand.
DESIGNS["colossus"] = {
    "bones": [
        {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": [
            box(-8, 0, -8, 16, 2, 16, "stone"),
            box(-6, 2, -6, 12, 2, 12, "stone"),
            box(-4, 4, -4, 8, 8, 8, "pillar"),
            box(-6, 12, -6, 12, 2, 12, "gold"),
        ]},
        {"name": "glow", "parent": "root", "pivot": [0, 29, 0], "cubes":
            stack(17, [(2, 2), (5, 3)], "crystal") + stack(22, [(8, 4), (11, 5), (8, 4)], "crystal_band")
            + stack(35, [(5, 3)], "crystal") + stack(38, [(2, 3)], "tip")},
    ],
    "anim": {"length": 6.0, "bones": {"glow": {"bob": 1.5, "spin_y": 90.0}}},
}

# 2, the Tide Shrine (the Leviathan): a whale's tail rising out of a driftstone rock, its last dive turned to stone: a
# rock of lumps set off square (so it reads as rock, not a plinth), one short thick trunk (a diving whale shows little
# body), and the flukes as the focal element, a wide crescent nearly two blocks across, each an inner blade and a tip
# rising more steeply; their top rows silver as the Leviathan's trailing edges are, their upward faces blue (white on an
# upward face reads as snow); six spots along the trunk's flanks, a warm song gland in the notch; the tail rocks slowly,
# as if still swimming. Round 2 of the blind review: the trunk merged into one box, the flukes 1.35 times larger.
DESIGNS["leviathan"] = {
    "bones": [
        {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": [
            box(-8, 0, -8, 16, 3, 16, "rock"),
            box(-6, 2.5, -5, 12, 3.5, 10, "rock", rot=(0, 18, 0), pivot=(0, 4, 0)),
            box(-3.5, 5.5, -3, 7, 2.5, 6, "rock", rot=(0, -14, 0), pivot=(0, 7, 0)),
            box(4, 2.25, -7.5, 3.5, 2.75, 3.5, "rock"),
        ]},
        {"name": "tail", "parent": "root", "pivot": [0, 7, 0], "cubes": [
            box(-3, 5, -2.5, 6, 11, 5, "hide"),
        ] + flukes(14, (9.5, 6.5), (6, 5.5), 16, 34) + [
            box(-1.5, 16, -1.5, 3, 2, 3, "gland"),
        ]},
    ],
    "anim": {"length": 6.0, "bones": {"tail": {"sway_z": 5.0}}},
}

# 3, the Choir Shrine (the Unsung): a basalt lancet arch (two pillars and two slanted bars, a true pointed arch) six
# units deep over a low plinth, so it is a stone niche from every side (a pillar from the side, a stone back); at the
# back of the niche a dark window of rift glass with clumps of the Nave's lichen (magenta in the pane, teal in its
# point), and hanging in the niche one porcelain mask, its eyes and open mouth lit from within, pale against the dark
# glass as the masks are against their cowls; the mask breathes and turns a little, listening. Round 2 of the blind
# review: the arch deepened from 3 to 6, the pane moved to the back, its back face stone, the lichen in clumps. Round 3:
# the window's point a solid soft teal panel, lifted (and the pane's top with it, its corners hidden in the arch bars)
# so it reaches the arch's inner apex: the sliver of sky under the apex is closed.
DESIGNS["unsung"] = {
    "bones": [
        {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": [
            box(-7.5, 0, -5, 15, 2, 10, "basalt"),
        ] + lancet(2, 24, 7.0, 2.5, 33, 2.5, 6.0, "basalt") + [
            box(-4.5, 2, 2.0, 9, 24, 1, "glass"),
            box(-3.2, 23.0, 1.9, 6.4, 6.4, 1, "glass_point", rot=(0, 0, 45), pivot=(0, 26.2, 2.4)),
        ]},
        {"name": "glow", "parent": "root", "pivot": [0, 16, -1.5], "cubes": mask(16, 8, 10, -2.5)},
    ],
    "anim": {"length": 8.0, "bones": {"glow": {"bob": 1.0, "sway_y": 8.0}}},
}

# 4, the Eclipse Shrine (boss 4): an ivory monument on a bronze step under a gilt cradle, and hovering half a block
# over it an eclipsed sun: one deep eight-pointed slab (two squares turned 45 degrees apart) with the eclipse painted
# on both faces, a round near black core in a flame corona, pale gold against the core and ember at the points, its
# edges burning, the Sanctum's eclipse in its own colours, turning slowly (a half turn in 12 s; the eight points repeat
# every 45 degrees). The points sweep a circle of 9.9 units; the cradle stays nine under it. The plan's six gold rays
# came off (a busy outline). Round 2: the eclipse on both sides, the corona thinner and painted as flame, the column
# broader without its band. Round 3: the separate dark discs and corona became one slab (the ring whole from every
# angle), eight points instead of twelve (the boss's own sun has eight; less like a gear), the hover clear.
DESIGNS["heliarch"] = {
    "bones": [
        {"name": "root", "parent": None, "pivot": [0, 0, 0], "cubes": [
            box(-8, 0, -8, 16, 2, 16, "ivory"),
            box(-6, 2, -6, 12, 2, 12, "bronze"),
            box(-4, 4, -4, 8, 8, 8, "ivory"),
            box(-4.5, 12, -4.5, 9, 2, 9, "gilt"),
        ]},
        {"name": "glow", "parent": "root", "pivot": [0, 33, 0], "cubes": star(33, 14, -2.0, 4.0, "sun", shrink=True, n=2)},
    ],
    "anim": {"length": 12.0, "bones": {"glow": {"spin_z": 180.0}}},
}


def check_limits(kind, design, faces):
    pts = np.concatenate([np.asarray(q["verts"], dtype=float) for q in faces])
    problems = []
    if abs(pts[:, 1].min()) > 1e-6:
        problems.append(f"its feet are at y {pts[:, 1].min():.2f}, not 0")
    if np.abs(pts[:, [0, 2]]).max() > LIMIT + 1e-6:
        problems.append(f"it reaches {np.abs(pts[:, [0, 2]]).max():.2f} from the middle (limit {LIMIT})")
    if pts[:, 1].max() > TOP + 1e-6:
        problems.append(f"its top is at {pts[:, 1].max():.2f} (limit {TOP})")
    low = pts[pts[:, 1] < 4.0 - 1e-6]
    if len(low) and np.abs(low[:, [0, 2]]).max() > BASE + 1e-6:
        problems.append("something under y 4 leaves the cell")
    cubes = [c for b in design["bones"] for c in b["cubes"]]
    if len(cubes) > MAX_CUBES:
        problems.append(f"{len(cubes)} cubes (at most {MAX_CUBES})")
    for c in cubes:
        if min(math.floor(s + 1e-6) for s in c["size"]) < 1:
            problems.append(f"a cube under one unit on a side: {c['origin']} {c['size']}")
    return problems


def check_leans(design, faces):
    centres = {}
    for q in faces:
        centres.setdefault((q["bone"], q["cube"]), {})[q["face"]] = np.asarray(q["verts"], dtype=float).mean(axis=0)
    problems = []
    for b in design["bones"]:
        for i, c in enumerate(b["cubes"]):
            want = c.get("check")
            f = centres[(b["name"], i)]
            if want == "out" and math.hypot(f["up"][0], f["up"][2]) <= math.hypot(f["down"][0], f["down"][2]):
                problems.append(f"{b['name']} cube {i} should lean out")
            if want == "up":
                sides = [f[k] for k in ("west", "east", "north", "south")]
                far = max(sides, key=lambda p: math.hypot(p[0], p[2]))
                near = min(sides, key=lambda p: math.hypot(p[0], p[2]))
                if far[1] <= near[1]:
                    problems.append(f"{b['name']} cube {i} should rise toward its far end")
    return problems


def _overlap(va, vb, n):
    """True if two convex quads in one plane share area (separating axes)."""
    helper = np.array([1.0, 0.0, 0.0]) if abs(n[0]) < 0.9 else np.array([0.0, 1.0, 0.0])
    e1 = np.cross(n, helper)
    e1 /= np.linalg.norm(e1)
    e2 = np.cross(n, e1)
    pa = np.stack([va @ e1, va @ e2], axis=1)
    pb = np.stack([vb @ e1, vb @ e2], axis=1)
    for poly in (pa, pb):
        for k in range(len(poly)):
            edge = poly[(k + 1) % len(poly)] - poly[k]
            axis = np.array([-edge[1], edge[0]])
            length = np.linalg.norm(axis)
            if length < 1e-9:
                continue
            ra, rb = pa @ axis, pb @ axis
            if min(ra.max(), rb.max()) - max(ra.min(), rb.min()) <= 1e-3 * length:
                return False
    return True


def check_coplanar(faces):
    """Faces of different cubes in one plane, facing the same way (GeckoLib's quads all wind outward), overlapping."""
    info = []
    for q in faces:
        v = np.asarray(q["verts"], dtype=float)
        n = np.cross(v[1] - v[0], v[3] - v[0])
        if np.linalg.norm(n) > 1e-9:
            info.append((q, v, n / np.linalg.norm(n)))
    problems = []
    for i in range(len(info)):
        qa, va, na = info[i]
        for j in range(i + 1, len(info)):
            qb, vb, nb = info[j]
            if (qa["bone"], qa["cube"]) == (qb["bone"], qb["cube"]):
                continue
            if float(na @ nb) < 0.999 or abs(float(na @ (vb[0] - va[0]))) > 1e-3:
                continue
            if _overlap(va, vb, na):
                problems.append(f"z-fight: {qa['bone']} cube {qa['cube']} {qa['face']} and {qb['bone']} cube {qb['cube']} {qb['face']}")
    return problems


def check_holes(faces, res=0.1, allowed=0.05):
    """No see-through holes from the front or the back: every face projected onto the front plane (an orthographic
    view along Z), then a flood fill from the border; whatever is uncovered and unreached is sky showing through the
    model (a window that stops short of its arch, say)."""
    pts = np.concatenate([np.asarray(q["verts"], dtype=float) for q in faces])
    xs = np.arange(pts[:, 0].min() - 1, pts[:, 0].max() + 1, res) + res / 2
    ys = np.arange(pts[:, 1].min() - 1, pts[:, 1].max() + 1, res) + res / 2
    gx, gy = np.meshgrid(xs, ys)
    covered = np.zeros(gx.shape, dtype=bool)
    for q in faces:
        v = np.asarray(q["verts"], dtype=float)[:, :2]
        area = 0.5 * sum(v[k][0] * v[(k + 1) % 4][1] - v[(k + 1) % 4][0] * v[k][1] for k in range(4))
        if abs(area) < 1e-6:
            continue  # seen edge on
        inside = np.ones(gx.shape, dtype=bool)
        for k in range(4):
            a, b = v[k], v[(k + 1) % 4]
            inside &= ((b[0] - a[0]) * (gy - a[1]) - (b[1] - a[1]) * (gx - a[0])) * np.sign(area) >= -1e-9
        covered |= inside
    outside = np.zeros(gx.shape, dtype=bool)
    outside[[0, -1], :] = ~covered[[0, -1], :]
    outside[:, [0, -1]] = ~covered[:, [0, -1]]
    while True:
        grown = outside.copy()
        grown[1:, :] |= outside[:-1, :]
        grown[:-1, :] |= outside[1:, :]
        grown[:, 1:] |= outside[:, :-1]
        grown[:, :-1] |= outside[:, 1:]
        grown &= ~covered
        if (grown == outside).all():
            break
        outside = grown
    hole = float((~covered & ~outside).sum()) * res * res
    return [f"sky shows through it from the front: {hole:.2f} square units"] if hole > allowed else []


def pose_at(anim, t):
    """The idle loop's pose at time t, as gen_shrines.animation() samples it into its keys."""
    length = float(anim.get("length", 6.0))
    w = math.sin(2.0 * math.pi * t / length)
    pose = {}
    for name, s in anim.get("bones", {}).items():
        rot = [0.0, s.get("sway_y", 0.0) * w + s.get("spin_y", 0.0) * t / length,
               s.get("sway_z", 0.0) * w + s.get("spin_z", 0.0) * t / length]
        pose[name] = (rot, [0.0, s.get("bob", 0.0) * w, 0.0], None)
    return pose


def _probes(cube_quads):
    """A cube's corners, the middles of its edges and the middles of its faces."""
    pts = []
    for q in cube_quads:
        v = np.asarray(q["verts"], dtype=float)
        pts.extend(v)
        pts.extend((v + np.roll(v, -1, axis=0)) / 2.0)
        pts.append(v.mean(axis=0))
    return np.unique(np.round(np.asarray(pts), 4), axis=0)


def _inside(points, cube_quads, eps=0.05):
    """Which points lie inside a cube: more than eps behind every one of its faces (they all wind outward)."""
    inside = np.ones(len(points), dtype=bool)
    for q in cube_quads:
        v = np.asarray(q["verts"], dtype=float)
        n = np.cross(v[1] - v[0], v[3] - v[0])
        length = np.linalg.norm(n)
        if length > 1e-9:
            inside &= (points - v[0]) @ (n / length) < -eps
    return inside


def check_sweep(design, samples=48):
    """Over the idle loop a moving part either always meets a still part (a joint, sunk into it) or never does; one
    that meets it only part of the time passes through it on screen."""
    anim = design["anim"]
    moving = set(anim.get("bones", {}))
    length = float(anim.get("length", 6.0))
    met = {}
    for k in range(samples):
        cubes = {}
        for q in sc.quads(design, pose_at(anim, length * k / samples)):
            cubes.setdefault((q["bone"], q["cube"]), []).append(q)
        for a, qa in cubes.items():
            if a[0] not in moving:
                continue
            pa = _probes(qa)
            for b, qb in cubes.items():
                if b[0] in moving:
                    continue
                if _inside(pa, qb).any() or _inside(_probes(qb), qa).any():
                    met.setdefault((a, b), set()).add(k)
    return [f"{a[0]} cube {a[1]} passes through {b[0]} cube {b[1]} during the idle loop ({len(ks)} of {samples} samples)"
            for (a, b), ks in sorted(met.items()) if len(ks) < samples]


def check_loop_bounds(design, samples=48):
    """Over the idle loop nothing reaches past LIMIT across or above RENDER_TOP (a bob or a sway can carry a part
    further than it rests)."""
    anim = design["anim"]
    length = float(anim.get("length", 6.0))
    reach, top = 0.0, 0.0
    for k in range(samples):
        pts = np.concatenate([np.asarray(q["verts"], dtype=float)
                              for q in sc.quads(design, pose_at(anim, length * k / samples))])
        reach = max(reach, float(np.abs(pts[:, [0, 2]]).max()))
        top = max(top, float(pts[:, 1].max()))
    problems = []
    if reach > LIMIT + 1e-6:
        problems.append(f"in the idle loop it reaches {reach:.2f} from the middle (limit {LIMIT})")
    if top > RENDER_TOP + 1e-6:
        problems.append(f"in the idle loop its top rises to {top:.2f} (limit {RENDER_TOP})")
    return problems


def export(kind, design):
    shape = {"kind": kind, "anim": design["anim"], "bones": [
        dict(b, cubes=[{k: v for k, v in c.items() if k != "check"} for c in b["cubes"]]) for b in design["bones"]]}
    path = sc.SHAPES / f"{kind}.cubes.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(shape, indent=2) + "\n", encoding="utf-8", newline="\n")
    return path


def clay(kind, design, out, name=None):
    """Clay views into `out`, files named clay_<name>_<view>.png (name: the kind, or a blind round's neutral name)."""
    name = name or kind
    sc.clear()
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_WORKBENCH"
    shading = scene.display.shading
    shading.light = "STUDIO"
    shading.color_type = "SINGLE"
    shading.single_color = sc.linear("#c9c2b4")
    for attr, value in (("show_object_outline", True), ("show_cavity", True)):
        try:
            setattr(shading, attr, value)
        except (AttributeError, TypeError):
            pass
    sc.standard_view(scene)
    scene.world = scene.world or bpy.data.worlds.new("backdrop")
    scene.world.color = sc.linear("#8fa3b8")
    sc.mesh(f"shrine_{kind}", sc.quads(design))
    for view, (eye, ortho) in sc.VIEWS.items():
        sc.render(scene, sc.camera(view, eye, sc.TARGET, ortho=ortho), out / f"clay_{name}_{view}.png", (512, 512))
    sc.render(scene, sc.camera("far", sc.FAR_EYE, (0.0, 0.0, 1.0)), out / f"clay_{name}_far.png", (160, 160))


def main():
    a = sc.args()
    kinds = a.get("kinds", " ".join(sorted(DESIGNS))).split()
    names = sc.out_names(a, kinds)
    out = Path(a.get("out", str(sc.MEDIA / "latest")))
    failed = False
    for kind, name in zip(kinds, names):
        design = DESIGNS[kind]
        faces = sc.quads(design)
        problems = (check_limits(kind, design, faces) + check_leans(design, faces) + check_coplanar(faces)
                    + check_holes(faces) + check_sweep(design) + check_loop_bounds(design))
        cubes = sum(len(b["cubes"]) for b in design["bones"])
        if problems:
            failed = True
            print(f"shrine_{kind}: FAIL")
            for p in problems:
                print(f"  {p}")
            continue
        path = export(kind, design)
        clay(kind, design, out, name)
        print(f"shrine_{kind}: ok, {cubes} cubes, {path.name}, clay views in {out}")
    sys.exit(1 if failed else 0)


main()
