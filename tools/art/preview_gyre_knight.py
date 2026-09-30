"""Previews of the Gyre Knight with its blades placed on their orbits the way GyreKnightModel does in the game.

Writes tools/art/previews/gyre_knight_views.png (front, side, back, three quarters in its Shield Orbit) and
gyre_knight_modes.png (a Sweep Orbit's wide rings with gold blades, a Lance Volley's blades aimed ahead, stunned).

Run:  python tools/art/preview_gyre_knight.py
"""
from __future__ import annotations

import math

import numpy as np

import preview_shardling as ps
from bedrock_model import load_anims, load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, load_png, rel, save_png

GEO = ASSETS / "geo" / "entity" / "gyre_knight.geo.json"
ANIM = ASSETS / "animations" / "entity" / "gyre_knight.animation.json"
TEXDIR = ASSETS / "textures" / "entity"
CORE_Y = 1.35
TILT = math.radians(24.0)


def offset(i, r, spin):
    az = i * 2 * math.pi / 3
    n = np.array([math.sin(TILT) * math.cos(az), math.cos(TILT), math.sin(TILT) * math.sin(az)])
    u = np.cross(np.array([0, 0, 1.0]), n)
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    v /= np.linalg.norm(v)
    a = spin + az
    return u * r * math.cos(a) + v * r * math.sin(a)


def blade_pose(pose, i, world, direction):
    """world: the blade's middle from the feet (blocks, yaw 0); direction: where it points."""
    local = np.array([-world[0], world[1], -world[2]])
    m = np.array([-direction[0], direction[1], -direction[2]])
    m /= np.linalg.norm(m)
    rx = math.asin(max(-1, min(1, m[1])))
    ry = math.atan2(-m[0], -m[2])
    pose[f"blade{i}"] = ([-math.degrees(rx), -math.degrees(ry), 0.0], [-local[0] * 16, local[1] * 16, local[2] * 16], None)


def direction(i, r, spin):
    radial = offset(i, 1.0, spin)
    tangent = offset(i, 1.0, spin + 0.01) - radial
    tangent /= np.linalg.norm(tangent)
    f = max(0.0, min(1.0, (r - 1.6) / 1.8))
    d = tangent * (1 - f) + radial * f
    return d / np.linalg.norm(d)


def orbit_pose(base, r, spin):
    pose = dict(base)
    for i in range(3):
        o = offset(i, r, spin)
        blade_pose(pose, i, np.array([0, CORE_Y, 0]) + o, direction(i, r, spin))
    return pose


def fit(q, view, size, margin=0.86):
    import preview_colossus as pc
    r, u, f = pc.camera(view)
    pts = np.concatenate([qq["verts"] for qq in q])
    xs, ys = pts @ r, pts @ u
    mid = np.array([(xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2])
    centre = r * mid[0] + u * mid[1]
    scale = margin * min(size[0] / max(1e-6, xs.max() - xs.min()), size[1] / max(1e-6, ys.max() - ys.min()))
    return (-centre[0], centre[1], centre[2]), scale


def render(q, tex, view, add, size=(340, 400)):
    c, s = fit(q, view, size)
    old = ps.camera
    import preview_colossus as pc
    ps.camera = pc.camera
    try:
        return ps.render(q, tex, view, scale=s, size=size, centre=c, glow=None, ground=False, add=add)
    finally:
        ps.camera = old


def main():
    desc, bones, order = load_geo(GEO)
    anims = load_anims(ANIM)
    tex = load_png(TEXDIR / "gyre_knight.png")
    glow = load_png(TEXDIR / "gyre_knight_glowmask.png")
    bglow = load_png(TEXDIR / "gyre_knight_blades_glowmask.png").astype(np.float64)
    hover = pose_at(anims["hover"], 0.3)
    shield = world_quads(desc, bones, order, orbit_pose(hover, 1.5, 0.8))
    views = [ps.label(render(shield, tex, v, glow), v) for v in ("front", "side", "back", "34")]
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = [save_png(ps.to_rgba(ps.hstack(views)), PREVIEWS / "gyre_knight_views.png")]
    gold = np.clip(glow.astype(np.float64) + bglow * np.array([1.0, 0.8, 0.25, 1.0]), 0, 255).astype(np.uint8)
    red = np.clip(glow.astype(np.float64) + bglow * np.array([0.8, 0.15, 0.1, 1.0]), 0, 255).astype(np.uint8)
    sweep = world_quads(desc, bones, order, orbit_pose(pose_at(anims["sweep"], 0.0), 4.0, 0.4))
    lance_pose = dict(pose_at(anims["lance"], 0.0))
    for i in range(3):
        o = offset(i, 2.5, 0.3)
        blade_pose(lance_pose, i, np.array([0, CORE_Y, 0]) + o, np.array([0.0, -0.1, 1.0]))
    lance = world_quads(desc, bones, order, lance_pose)
    stun_pose = dict(pose_at(anims["stunned"], 0.5))
    for i in range(3):
        blade_pose(stun_pose, i, np.array([(i - 1) * 0.9, 0.1, 0.8 + 0.2 * i]), np.array([math.cos(i), 0.0, math.sin(i)]))
    stunned = world_quads(desc, bones, order, stun_pose)
    modes = [ps.label(render(sweep, tex, "34", gold, size=(420, 400)), "sweep: rings wide, blades gold"),
             ps.label(render(lance, tex, "34", red), "lance: blades aimed, red"),
             ps.label(render(stunned, tex, "34", glow), "stunned")]
    out.append(save_png(ps.to_rgba(ps.hstack(modes)), PREVIEWS / "gyre_knight_modes.png"))
    for p in out:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
