"""Bedrock geometry + animation maths, done the way GeckoLib 4.9 does it.

Shared by gen_shardling.py (to solve leg angles that keep the feet on the
ground) and preview_shardling.py (to render). Checked against the GeckoLib
4.9.3 jar with javap:

* BakedModelFactory mirrors x on load: cube origin x -> -(x + size_x), pivots
  x -> -x, and rotations (rx, ry, rz) -> (-rx, -ry, rz) in radians
* RenderUtil.prepMatrixForBone: translate(position with x negated), translate
  to pivot, rotate Z then Y then X, scale, translate back; cubes rotate the
  same way about their own pivot
* AnimationProcessor adds animated rotation to the bone's bind rotation;
  animated position and scale replace the (zero, one) defaults
* box UV face rectangles and corner order as in BakedModelFactory.buildQuad
  and GeoQuad.build (not mirrored)

Everything here works in model units (16 per block), Java space (x mirrored).
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np


# ------------------------------------------------------------------ loading
def load_geo(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return split_geo(data)


def split_geo(data: dict):
    assert data["format_version"] == "1.12.0", "GeckoLib 4 wants geometry 1.12.0"
    geo = data["minecraft:geometry"][0]
    desc = geo["description"]
    bones = {b["name"]: b for b in geo["bones"]}
    order = [b["name"] for b in geo["bones"]]
    return desc, bones, order


def load_anims(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    assert data["format_version"] == "1.8.0"
    return data["animations"]


# ------------------------------------------------------------------ matrices
def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=np.float64)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=np.float64)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=np.float64)


def trans(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale_m(x, y, z):
    return np.diag([x, y, z, 1.0])


def gecko_rot(rx_deg, ry_deg, rz_deg):
    """Rotation exactly as GeckoLib builds it from Bedrock degrees (Java space)."""
    rx, ry, rz = math.radians(-rx_deg), math.radians(-ry_deg), math.radians(rz_deg)
    return rot_z(rz) @ rot_y(ry) @ rot_x(rx)


# ------------------------------------------------------------------ animation sampling
def _kf_value(v, which="post"):
    if isinstance(v, dict):
        if "vector" in v:
            return v["vector"]
        return v.get(which, v.get("post", v.get("pre")))
    return v


def sample_channel(ch, t):
    """Linear interpolation (GeckoLib's default easing), clamped at the ends."""
    if ch is None:
        return None
    if isinstance(ch, list):
        return [float(c) for c in ch]
    keys = sorted((float(k), val) for k, val in ch.items())
    if t <= keys[0][0]:
        return [float(c) for c in _kf_value(keys[0][1], "pre")]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            a = [float(c) for c in _kf_value(v0, "post")]
            b = [float(c) for c in _kf_value(v1, "pre")]
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [x + (y - x) * f for x, y in zip(a, b)]
    return [float(c) for c in _kf_value(keys[-1][1], "post")]


def pose_at(anim, t):
    """{bone: (rotation offset, position, scale)} for an animation at time t."""
    out = {}
    if anim is None:
        return out
    for bone, chans in anim.get("bones", {}).items():
        out[bone] = (sample_channel(chans.get("rotation"), t),
                     sample_channel(chans.get("position"), t),
                     sample_channel(chans.get("scale"), t))
    return out


# ------------------------------------------------------------------ geometry
FACE_ORDER = ["west", "east", "north", "south", "up", "down"]


def cube_quads(cube):
    """Six quads of a cube in Java model space: (face, verts[4], uvs[4] in texels)."""
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    inf = cube.get("inflate", 0.0)
    x0, y0, z0 = -(ox + sx) - inf, oy - inf, oz - inf
    x1, y1, z1 = -ox + inf, oy + sy + inf, oz + sz + inf
    blb = (x0, y0, z0); brb = (x0, y0, z1); tlb = (x0, y1, z0); trb = (x0, y1, z1)
    tlf = (x1, y1, z0); trf = (x1, y1, z1); blf = (x1, y0, z0); brf = (x1, y0, z1)
    verts = {
        "west": [trb, tlb, blb, brb],
        "east": [tlf, trf, brf, blf],
        "north": [tlb, tlf, blf, blb],
        "south": [trf, trb, brb, brf],
        "up": [trb, trf, tlf, tlb],
        "down": [blb, blf, brf, brb],
    }
    u, v = cube["uv"]
    w, h, d = math.floor(sx), math.floor(sy), math.floor(sz)
    rects = {
        "west": (u + d + w, v + d, d, h),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "south": (u + d + w + d, v + d, w, h),
        "up": (u + d, v, w, d),
        "down": (u + d + w, v + d, w, -d),
    }
    quads = []
    for face in FACE_ORDER:
        ru, rv, us, vs = rects[face]
        uvs = [(ru + us, rv), (ru, rv), (ru, rv + vs), (ru + us, rv + vs)]
        quads.append((face, np.array(verts[face], dtype=np.float64), np.array(uvs, dtype=np.float64)))
    return quads


def bone_local_matrix(bone, pose):
    px, py, pz = bone.get("pivot", [0, 0, 0])
    rx, ry, rz = bone.get("rotation", [0, 0, 0])
    pos = [0.0, 0.0, 0.0]
    scl = [1.0, 1.0, 1.0]
    anim = pose.get(bone["name"])
    if anim:
        r, p, s = anim
        if r:
            rx, ry, rz = rx + r[0], ry + r[1], rz + r[2]
        if p:
            pos = p
        if s:
            scl = s
    pivot_j = (-px, py, pz)
    return (trans(-pos[0], pos[1], pos[2]) @ trans(*pivot_j) @ gecko_rot(rx, ry, rz)
            @ scale_m(*scl) @ trans(*[-c for c in pivot_j]))


def world_quads(desc, bones, order, pose):
    """Every face of every cube, posed, in Java model space."""
    world = {}

    def get_world(name):
        if name not in world:
            b = bones[name]
            local = bone_local_matrix(b, pose)
            parent = b.get("parent")
            world[name] = (get_world(parent) @ local) if parent else local
        return world[name]

    out = []
    for name in order:
        b = bones[name]
        m_bone = get_world(name)
        for ci, cube in enumerate(b.get("cubes", [])):
            m = m_bone
            if "rotation" in cube:
                cpx, cpy, cpz = cube.get("pivot", [0, 0, 0])
                pj = (-cpx, cpy, cpz)
                m = m @ trans(*pj) @ gecko_rot(*cube["rotation"]) @ trans(*[-c for c in pj])
            for face, verts, uvs in cube_quads(cube):
                wv = (m @ np.c_[verts, np.ones(4)].T).T[:, :3]
                out.append(dict(bone=name, cube=ci, face=face, verts=wv, uvs=uvs))
    return out


def bone_world_matrix(bones, name, pose):
    """World matrix of one bone, walking up its parents (no other bones touched)."""
    m = np.eye(4)
    chain = []
    while name:
        chain.append(bones[name])
        name = bones[name].get("parent")
    for b in reversed(chain):
        m = m @ bone_local_matrix(b, pose)
    return m


def bone_lowest(bones, pose, name):
    """Lowest y of the cubes of one bone (children not included)."""
    m_bone = bone_world_matrix(bones, name, pose)
    low = math.inf
    for cube in bones[name].get("cubes", []):
        m = m_bone
        if "rotation" in cube:
            cpx, cpy, cpz = cube.get("pivot", [0, 0, 0])
            pj = (-cpx, cpy, cpz)
            m = m @ trans(*pj) @ gecko_rot(*cube["rotation"]) @ trans(*[-c for c in pj])
        ox, oy, oz = cube["origin"]
        sx, sy, sz = cube["size"]
        inf = cube.get("inflate", 0.0)
        xs = (-(ox + sx) - inf, -ox + inf)
        ys = (oy - inf, oy + sy + inf)
        zs = (oz - inf, oz + sz + inf)
        corners = np.array([[x, y, z, 1.0] for x in xs for y in ys for z in zs])
        low = min(low, float((m @ corners.T)[1].min()))
    return low


def lowest_point(desc, bones, order, pose, bone=None):
    if bone is not None:
        return bone_lowest(bones, pose, bone)
    qs = world_quads(desc, bones, order, pose)
    return min(float(q["verts"][:, 1].min()) for q in qs)


def plant_leg(desc, bones, order, pose, leg, lo, hi, target=0.0, prefer=None):
    """X rotation (degrees, within [lo, hi]) that puts the leg's lowest point on
    y = target, given the rest of the pose. Keeps the leg's own Y/Z rotation.

    A leg usually has two answers (swung forward or back). The one nearest to
    `prefer` wins (default: the leg's current X rotation in the pose), so a
    rough angle in a hand-written pose picks the branch and consecutive frames
    stay continuous instead of flipping sides."""
    import copy
    base = copy.deepcopy(pose)
    rot, pos, scl = base.get(leg, ([0, 0, 0], None, None))
    rot = list(rot or [0, 0, 0])
    if prefer is None:
        prefer = rot[0]

    def err(a):
        base[leg] = ([a, rot[1], rot[2]], pos, scl)
        return abs(bone_lowest(bones, base, leg) - target)

    grid = np.arange(lo, hi + 1e-9, 1.0)
    errs = np.array([err(a) for a in grid])
    near = grid[errs < max(0.12, float(errs.min()) + 1e-6)]
    start_a = float(min(near, key=lambda a: abs(a - prefer)))
    fine = [a for a in np.arange(start_a - 1.5, start_a + 1.5 + 1e-9, 0.1) if lo <= a <= hi]
    best = min(fine, key=lambda a: (round(err(a), 3), abs(a - prefer)))
    return round(float(best), 2), err(best)
