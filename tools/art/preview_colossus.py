"""Previews of the Prism Colossus and the Prism Shard, rendered the way GeckoLib poses them (bedrock_model).

Writes tools/art/previews/colossus_views.png (front, side, back and three quarters, idle), colossus_poses.png
(dormant, idle, charging, hunched for a burst, broken, the roar) and shard_views.png (the three colours).

Run:  python tools/art/preview_colossus.py
"""
from __future__ import annotations

import math

import numpy as np

import preview_shardling as ps
from bedrock_model import load_anims, load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, load_png, rel, save_png

GEO = ASSETS / "geo" / "entity" / "prism_colossus.geo.json"
ANIM = ASSETS / "animations" / "entity" / "prism_colossus.animation.json"
TEXDIR = ASSETS / "textures" / "entity"
_BASE_CAMERA = ps.camera


def camera(view):
    if view == "back":
        f = np.array([0, 0, -1.0])
        up = np.array([0, 1.0, 0])
        r = np.cross(f, up)
        r /= np.linalg.norm(r)
        return r, np.cross(r, f), f
    return _BASE_CAMERA(view)


def render(quads, tex, view, glow=None, scale=5.0, size=(300, 330), centre=(0.0, 22.0, 0.0), add=None):
    old = ps.camera
    ps.camera = camera
    try:
        return ps.render(quads, tex, view, scale=scale, size=size, centre=centre, glow=glow, ground=False, add=add)
    finally:
        ps.camera = old


def flying_fist_pose(base_pose, target):
    """The right fist in flight at `target` (Java model units), turned as ColossusModel turns it: its down axis and
    the arm both along the line from the shoulder, so the knuckles lead and the band trails toward the arm."""
    pivot = np.array([16.0, 34.0, -3.0])
    d = np.array(target, float) - pivot
    d /= np.linalg.norm(d)
    a = -math.asin(max(-1.0, min(1.0, d[2])))
    b = math.atan2(d[0], -d[1])
    rot = [-math.degrees(a), 0.0, math.degrees(b)]
    pose = dict(base_pose)
    pose["shoulder_right"] = (rot, None, None)
    pose["free_fist_right"] = (rot, [-float(target[0]), float(target[1]), float(target[2])], None)
    return pose


def hidden_free(quads):
    return [q for q in quads if not q["bone"].startswith("free_fist")]


def main():
    desc, bones, order = load_geo(GEO)
    anims = load_anims(ANIM)
    tex = load_png(TEXDIR / "prism_colossus.png")
    glow = load_png(TEXDIR / "prism_colossus_glowmask.png")
    idle = pose_at(anims["idle"], 0.0)
    q = hidden_free(world_quads(desc, bones, order, idle))
    views = [ps.label(render(q, tex, v, glow=None, add=glow), v) for v in ("front", "side", "back", "34")]
    # a fist in flight, from the front and the side
    fly = flying_fist_pose(idle, (34.0, 14.0, -34.0))
    fq = [qq for qq in world_quads(desc, bones, order, fly) if qq["bone"] not in ("fist_right", "free_fist_left")]
    views += [ps.label(render(fq, tex, v, glow=None, add=glow, scale=4.0, centre=(-8.0, 22.0, -10.0)), "fist " + v)
              for v in ("front", "side")]
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = [save_png(ps.to_rgba(ps.hstack(views)), PREVIEWS / "colossus_views.png")]
    poses = []
    for name, anim, t, texname in (("dormant", "dormant", 0.0, "prism_colossus_dormant"), ("idle", "idle", 1.0, "prism_colossus"),
                                   ("charge", "refraction_charge", 2.0, "prism_colossus"), ("burst tell", "burst_tell", 0.8, "prism_colossus_white"),
                                   ("broken", "broken", 0.0, "prism_colossus"), ("roar", "fracture", 1.0, "prism_colossus_cracked")):
        pose = pose_at(anims[anim], t)
        qq = hidden_free(world_quads(desc, bones, order, pose))
        t_img = load_png(TEXDIR / f"{texname}.png")
        poses.append(ps.label(render(qq, t_img, "34"), name))
    out.append(save_png(ps.to_rgba(ps.hstack(poses)), PREVIEWS / "colossus_poses.png"))
    # the shard
    sd, sbones, sorder = load_geo(ASSETS / "geo" / "entity" / "prism_shard.geo.json")
    sq = world_quads(sd, sbones, sorder, {})
    shards = []
    st = load_png(TEXDIR / "prism_shard.png")
    for color in ("red", "green", "blue"):
        sg = load_png(TEXDIR / f"prism_shard_{color}_glowmask.png")
        shards.append(ps.label(render(sq, st, "34", scale=11, size=(260, 200), centre=(0.0, 6.0, -2.0), add=sg), color))
    shards.append(ps.label(render(sq, st, "side", scale=11, size=(260, 200), centre=(0.0, 6.0, -2.0),
                                  add=load_png(TEXDIR / "prism_shard_blue_glowmask.png")), "side"))
    out.append(save_png(ps.to_rgba(ps.hstack(shards)), PREVIEWS / "shard_views.png"))
    for p in out:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
