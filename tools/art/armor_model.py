"""Shared pieces for the armor set generators after the Vanguard (gen_driftweave.py, gen_regalia.py).

The same conventions as gen_vanguard.py: one GeckoLib armor model for all four pieces, Bedrock's humanoid
coordinates (units of 1/16 block, feet at y = 0, the head from y = 24 to 32, front to -Z, the wearer's right to
-X), GeckoLib's armor bone names (armorHead, armorBody, armorRightArm, armorLeftArm, armorRightLeg, armorLeftLeg,
armorRightBoot, armorLeftBoot) whose pivots match the vanilla humanoid's, box UV packed on one texture, a
glowmask the game tints, and 16x16 icons with a white tier-trim layer.

What a generator supplies: its bones (cubes carry a `paint` name and optional `rules`), a painter
`paint(cube, face, w, h) -> (colours, glows)` (rows of (r, g, b) or None), its icon rows and palette.

Rings (halos, orbit rings, shoulder rings) are regular polygons of thin bars: `ring_cubes` places them in the
XZ (lying flat), XY (facing front and back) or YZ (facing the sides) plane, each bar turned about its own
centre so it lies along the circle (checked with bedrock_model.world_quads, GeckoLib's own maths).
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field

import numpy as np

from common import blank, rgb


@dataclass
class Cube:
    origin: tuple
    size: tuple
    paint: str
    rules: tuple = ()
    rotation: tuple | None = None
    pivot: tuple | None = None
    inflate: float = 0.0
    tag: str = ""
    uv: tuple | None = None

    @property
    def dims(self):
        """The box UV's sizes, as GeckoLib takes them: each side rounded down (a side under 1 maps to no texels)."""
        return tuple(int(math.floor(s + 1e-6)) for s in self.size)


@dataclass
class Bone:
    name: str
    parent: str | None
    pivot: tuple
    rotation: tuple | None = None
    cubes: list = field(default_factory=list)


def mirror_cube(c: Cube, tag: str) -> Cube:
    """The same cube on the wearer's left: x mirrored, rotations about Y and Z turned the other way."""
    ox, oy, oz = c.origin
    sx, sy, sz = c.size
    rot = None if c.rotation is None else (c.rotation[0], -c.rotation[1], -c.rotation[2])
    piv = None if c.pivot is None else (-c.pivot[0], c.pivot[1], c.pivot[2])
    rules = tuple(s.replace("xneg", "XPOS_").replace("xpos", "xneg").replace("XPOS_", "xpos") for s in c.rules)
    return Cube((-(ox + sx), oy, oz), c.size, c.paint, rules, rot, piv, c.inflate, tag)


def mirror_bone(b: Bone, name: str, parent: str | None, tag_from="_r", tag_to="_l") -> Bone:
    """A bone and its cubes on the wearer's left."""
    rot = None if b.rotation is None else (b.rotation[0], -b.rotation[1], -b.rotation[2])
    return Bone(name, parent, (-b.pivot[0], b.pivot[1], b.pivot[2]), rot,
                [mirror_cube(c, c.tag.replace(tag_from, tag_to)) for c in b.cubes])


def ring_cubes(centre, radius, n, thickness, plane, paint, tag, width=None, phase=0.0, overlap=1.08):
    """A ring of `n` bars around `centre` of `radius` in `plane` ("xz", "xy" or "yz").

    Each bar is `thickness` across the ring's plane and `width` (default thickness) out of it, and a little longer
    than a polygon side so the corners close. Bar k sits at angle phase + k * 360 / n. Bars thinner than a unit are
    built a unit thick and shrunk with a negative inflate, so their box UV keeps whole texels on every face.
    """
    width = thickness if width is None else width
    thin = min(thickness, width)
    shrink = min(0.0, (thin - 1.0) / 2.0)
    thickness, width = thickness - 2 * shrink, width - 2 * shrink
    side = 2.0 * radius * math.tan(math.pi / n) * overlap - 2 * shrink
    cx, cy, cz = centre
    cubes = []
    for k in range(n):
        a = math.radians(phase + k * 360.0 / n)
        if plane == "xz":
            # angle measured from +Z toward +X; the bar lies along X, turned about Y
            px, py, pz = cx + radius * math.sin(a), cy, cz + radius * math.cos(a)
            size = (side, width, thickness)
            rot = (0.0, math.degrees(a), 0.0)
        elif plane == "xy":
            # angle measured from +Y toward +X; the bar lies along X, turned about Z
            px, py, pz = cx + radius * math.sin(a), cy + radius * math.cos(a), cz
            size = (side, thickness, width)
            rot = (0.0, 0.0, math.degrees(a))
        else:
            # "yz": angle measured from +Y toward +Z; the bar lies along Z, turned about X
            px, py, pz = cx, cy + radius * math.cos(a), cz + radius * math.sin(a)
            size = (width, thickness, side)
            rot = (-math.degrees(a), 0.0, 0.0)
        origin = (px - size[0] / 2, py - size[1] / 2, pz - size[2] / 2)
        cubes.append(Cube(origin, size, paint, (), rot, (px, py, pz), shrink, f"{tag}{k}"))
    return cubes


def thin_cube(origin, size, paint, rules=(), thickness_axis=None, thin=0.5, **kw) -> Cube:
    """A cube `thin` thick along one axis (0 x, 1 y, 2 z) built a unit thick and shrunk by a negative inflate, so
    every face keeps whole texels; the other two sides are grown by the same amount so they come out as given."""
    shrink = (thin - 1.0) / 2.0
    size = list(size)
    origin = list(origin)
    for axis in range(3):
        if axis == thickness_axis:
            centre = origin[axis] + size[axis] / 2.0
            size[axis] = 1.0
            origin[axis] = centre - 0.5
        else:
            size[axis] -= 2 * shrink
            origin[axis] += shrink
    return Cube(tuple(origin), tuple(size), paint, tuple(rules), inflate=shrink, **kw)


def check_uv(bones: list[Bone]):
    """Every cube needs at least one texel on each side, or GeckoLib maps some face to no texels."""
    bad = [c.tag for b in bones for c in b.cubes if min(c.dims) < 1]
    if bad:
        raise SystemExit(f"cubes under a unit on some side (use thin_cube or a negative inflate): {bad}")


# ------------------------------------------------------------------ box UV
def face_rects(c: Cube):
    """Box UV rectangles (u, v, w, h) per face, as GeckoLib's BakedModelFactory picks them."""
    w, h, d = c.dims
    u, v = c.uv
    return {
        "xneg": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "xpos": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
    }


def texel_point(c: Cube, face: str, col: int, row: int):
    """Cube-local Bedrock point at the centre of a face texel."""
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    w, h, d = c.dims
    fx = (col + 0.5) / max(1, {"xneg": d, "xpos": d, "front": w, "back": w, "top": w, "bottom": w}[face])
    fy = (row + 0.5) / max(1, {"top": d, "bottom": d}.get(face, h))
    if face == "xneg":
        return (x0, y1 - fy * sy, z1 - fx * sz)
    if face == "front":
        return (x0 + fx * sx, y1 - fy * sy, z0)
    if face == "xpos":
        return (x1, y1 - fy * sy, z0 + fx * sz)
    if face == "back":
        return (x1 - fx * sx, y1 - fy * sy, z1)
    if face == "top":
        return (x0 + fx * sx, y1, z1 - fy * sz)
    return (x0 + fx * sx, y0, z1 - fy * sz)


def pack_uvs(bones: list[Bone], tex_w: int, tex_h: int):
    """Shelf-packs every cube's box UV onto the texture (largest first); identical small cubes share a spot."""
    cubes = [c for b in bones for c in b.cubes]
    order = sorted(cubes, key=lambda c: (-(c.dims[2] + c.dims[1]), -(2 * (c.dims[0] + c.dims[2]))))
    shared = {}
    gap = 1
    x = y = 0
    row_h = 0
    for c in order:
        key = (c.dims, c.paint, c.rules) if c.paint in SHAREABLE else None
        if key is not None and key in shared:
            c.uv = shared[key]
            continue
        w, h, d = c.dims
        fw, fh = 2 * (d + w), d + h
        if x + fw > tex_w:
            x = 0
            y += row_h + gap
            row_h = 0
        c.uv = (x, y)
        if key is not None:
            shared[key] = c.uv
        x += fw + gap
        row_h = max(row_h, fh)
    if y + row_h > tex_h:
        raise SystemExit(f"UV layout needs {y + row_h} rows, texture has {tex_h}")


# paints whose identical cubes may share one UV island (ring bars): set by the generators
SHAREABLE: set[str] = set()


def paint_texture(bones: list[Bone], painter, tex_w: int, tex_h: int):
    tex = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    glow = np.zeros((tex_h, tex_w, 4), dtype=np.uint8)
    done = set()
    for b in bones:
        for c in b.cubes:
            if c.uv in done and c.paint in SHAREABLE:
                continue
            done.add(c.uv)
            for face, (ru, rv, rw, rh) in face_rects(c).items():
                colours, glows = painter(c, face, rw, rh)
                for r in range(rh):
                    for col in range(rw):
                        if colours[r][col] is None:
                            continue  # a cut-out texel (the armor renders with alpha cutout)
                        tex[rv + r, ru + col, :3] = colours[r][col]
                        tex[rv + r, ru + col, 3] = 255
                        g = glows[r][col]
                        if g is not None:
                            glow[rv + r, ru + col, :3] = g
                            glow[rv + r, ru + col, 3] = 255
    return tex, glow


# ------------------------------------------------------------------ json
def r4(v):
    return [round(float(a), 4) for a in v]


def geo_json(bones: list[Bone], name: str, tex_w: int, tex_h: int) -> dict:
    out = []
    for b in bones:
        d = {"name": b.name}
        if b.parent:
            d["parent"] = b.parent
        d["pivot"] = r4(b.pivot)
        if b.rotation and any(b.rotation):
            d["rotation"] = r4(b.rotation)
        if b.cubes:
            cl = []
            for c in b.cubes:
                cd = {"origin": r4(c.origin), "size": r4(c.size), "uv": list(c.uv)}
                if c.inflate:
                    cd["inflate"] = c.inflate
                if c.rotation and any(abs(a) > 1e-9 for a in c.rotation):
                    cd["pivot"] = r4(c.pivot)
                    cd["rotation"] = r4(c.rotation)
                cl.append(cd)
            d["cubes"] = cl
        out.append(d)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + name,
                "texture_width": tex_w,
                "texture_height": tex_h,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.75, 0],
            },
            "bones": out,
        }],
    }


def pretty(data) -> str:
    return json.dumps(data, indent=2) + "\n"


# ------------------------------------------------------------------ painting helpers
def hash01(*vals) -> float:
    h = 2166136261
    for v in vals:
        if isinstance(v, str):
            for ch in v.encode():
                h ^= ch
                h = (h * 16777619) & 0xFFFFFFFF
        else:
            h ^= int(round(v * 8)) & 0xFFFFFFFF
            h = (h * 16777619) & 0xFFFFFFFF
    return (h % 10007) / 10007.0


def grid(w, h, value=None):
    return [[value] * w for _ in range(h)]


def shade_index(face: str, row: int, h: int, base: int, top=1, bottom=-1) -> int:
    """A palette step for a texel: lighter on top faces and upper rows, darker below."""
    side = face in ("xneg", "xpos", "front", "back")
    k = base
    if face == "top" or (side and row == 0):
        k += top
    elif face == "bottom" or (side and row == h - 1 and h > 2):
        k += bottom
    return k


# ------------------------------------------------------------------ icons
def sprite(rows, palette) -> np.ndarray:
    img = blank(16, 16)
    for y, line in enumerate(rows):
        assert len(line) == 16, (y, line)
        for x, ch in enumerate(line):
            if ch == ".":
                continue
            img[y, x, :3] = palette[ch]
            img[y, x, 3] = 255
    return img


def trim_of(img: np.ndarray) -> np.ndarray:
    """The tier trim: the sprite's outermost texels (opaque ones touching transparency), white; the game tints it."""
    a = img[..., 3] > 0
    pad = np.pad(a, 1)
    inner = pad[1:-1, 1:-1]
    edge = inner & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    out = np.zeros_like(img)
    out[edge, :3] = 255
    out[edge, 3] = 255
    return out


def palette(mapping: dict) -> dict:
    return {k: rgb(v) if isinstance(v, str) else v for k, v in mapping.items()}
