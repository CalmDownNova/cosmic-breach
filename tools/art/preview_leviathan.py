"""Previews of the Thalassine Leviathan, placed bone by bone along its body the way LeviathanModel does in the game.

Writes tools/art/previews/leviathan_views.png (side, front, top, three quarters, rear three quarters: swimming an arc of
its orbit) and leviathan_poses.png (the jaw open for the Song, the plates lifted over the glowing glands in a Moorage,
asleep, the fan curled for a flick).

Run:  python tools/art/preview_leviathan.py
"""
from __future__ import annotations

import math

import numpy as np

import preview_shardling as ps
from bedrock_model import load_anims, load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, load_png, rel, save_png

GEO = ASSETS / "geo" / "entity" / "thalassine_leviathan.geo.json"
ANIM = ASSETS / "animations" / "entity" / "thalassine_leviathan.animation.json"
TEXDIR = ASSETS / "textures" / "entity"
FOLLOW = [7.0, 13.0, 19.0, 25.0, 31.0]
SCALE = 2.0
BONES = ["head", "seg1", "seg2", "seg3", "seg4", "tail"]


def body_points(curve: float, rise: float = 0.0):
    """World points (blocks, relative to the head) of the head and the followers along an arc of radius `curve`
    (0: straight), the body swimming toward -Z... in the entity's yaw 0 it swims toward +Z (south)."""
    pts = [np.array([0.0, 0.0, 0.0])]
    for d in FOLLOW:
        if curve <= 0:
            pts.append(np.array([0.0, -rise * d, -d]))
        else:
            a = d / curve
            pts.append(np.array([curve * (1 - math.cos(a)), -rise * d, -curve * math.sin(a)]))
    return pts


def pose_for(pts, extra=None, aim=None):
    pose = dict(extra or {})
    for i, name in enumerate(BONES):
        p = pts[i]
        local = np.array([-p[0], p[1], -p[2]]) / SCALE          # toModel with the body yaw held at 0
        if i == 0:
            d = aim if aim is not None else pts[0] - pts[1]
        elif i == len(BONES) - 1:
            d = pts[i - 1] - pts[i]
        else:
            d = pts[i - 1] - pts[i + 1]
        d = d / np.linalg.norm(d)
        m = np.array([-d[0], d[1], -d[2]])
        rx = math.asin(max(-1.0, min(1.0, m[1])))
        ry = math.atan2(-m[0], -m[2])
        base_rot = pose.get(name, (None, None, None))[0] or [0, 0, 0]
        pose[name] = ([-math.degrees(rx) + base_rot[0], -math.degrees(ry) + base_rot[1], base_rot[2]],
                      [-local[0] * 16.0, local[1] * 16.0, local[2] * 16.0], None)
    return pose


def fit(q, view, size, margin=0.9):
    """The centre (Bedrock x) and scale that fit every quad of `q` in an image of `size` seen from `view`."""
    r, u, f = ps.camera(view)
    pts = np.concatenate([qq["verts"] for qq in q])
    xs, ys = pts @ r, pts @ u
    mid = np.array([(xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2])
    centre = r * mid[0] + u * mid[1]
    scale = margin * min(size[0] / max(1e-6, xs.max() - xs.min()), size[1] / max(1e-6, ys.max() - ys.min()))
    return (-centre[0], centre[1], centre[2]), scale


def render(q, tex, view, glow, scale=None, size=(760, 420), centre=None):
    c, s = fit(q, view, size)
    return ps.render(q, tex, view, scale=s, size=size, centre=c, glow=None, ground=False, add=glow)


def main():
    desc, bones, order = load_geo(GEO)
    anims = load_anims(ANIM)
    tex = load_png(TEXDIR / "thalassine_leviathan.png")
    spots = load_png(TEXDIR / "thalassine_leviathan_glowmask.png")
    glands = load_png(TEXDIR / "thalassine_leviathan_glands_glowmask.png")
    fans = load_png(TEXDIR / "thalassine_leviathan_fan_glowmask.png")
    dim_glands = (glands.astype(np.float64) * 0.16).astype(np.uint8)
    swim = pose_at(anims["swim"], 0.4)
    straight = world_quads(desc, bones, order, pose_for(body_points(0), swim))
    arc = world_quads(desc, bones, order, pose_for(body_points(22.0, 0.05), swim))
    glow = np.clip(spots.astype(np.int32) + dim_glands, 0, 255).astype(np.uint8)
    views = [ps.label(render(straight, tex, "side", glow, centre=(0.0, 0.0, 128.0)), "side"),
             ps.label(render(straight, tex, "front", glow, scale=3.2, size=(420, 420), centre=(0.0, 0.0, 0.0)), "front"),
             ps.label(render(arc, tex, "top", glow, centre=(-60.0, 0.0, 110.0)), "top (an arc of its orbit)"),
             ps.label(render(arc, tex, "34", glow, centre=(-60.0, 0.0, 110.0)), "three quarters"),
             ps.label(render(arc, tex, "rear34", glow, centre=(-60.0, 0.0, 110.0)), "rear three quarters")]
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = [save_png(ps.to_rgba(ps.vstack([ps.hstack(views[:2]), ps.hstack(views[2:4]), views[4]])), PREVIEWS / "leviathan_views.png")]
    # poses
    song = dict(swim)
    song["jaw"] = ([-36.0, 0.0, 0.0], None, None)
    sq = world_quads(desc, bones, order, pose_for(body_points(0), song))
    moored = pose_at(anims["moored"], 0.3)
    mq = world_quads(desc, bones, order, pose_for(body_points(15.0), moored))
    mglow = np.clip(spots.astype(np.int32) + glands, 0, 255).astype(np.uint8)
    sleep = pose_at(anims["sleep"], 0.0)
    dq = world_quads(desc, bones, order, pose_for(body_points(14.0), sleep))
    dtex = load_png(TEXDIR / "thalassine_leviathan_dormant.png")
    dglow = load_png(TEXDIR / "thalassine_leviathan_dormant_glowmask.png")
    flick = dict(swim)
    flick["fan"] = ([-50.0, 0.0, 0.0], None, None)
    fq = world_quads(desc, bones, order, pose_for(body_points(0), flick))
    fglow = np.clip(spots.astype(np.int32) + fans, 0, 255).astype(np.uint8)
    poses = [ps.label(render(sq, tex, "34", glow, scale=4.0, size=(520, 420), centre=(0.0, 0.0, 20.0)), "song: the jaw open"),
             ps.label(render(mq, tex, "34", mglow, centre=(-60.0, 0.0, 110.0)), "moored: plates up, glands lit"),
             ps.label(render(dq, dtex, "34", dglow, centre=(-60.0, 0.0, 110.0)), "asleep"),
             ps.label(render(fq, tex, "rear34", fglow, scale=3.2, size=(520, 420), centre=(0.0, 0.0, 250.0)), "flick: the fan gold")]
    out.append(save_png(ps.to_rgba(ps.vstack([ps.hstack(poses[:2]), ps.hstack(poses[2:])])), PREVIEWS / "leviathan_poses.png"))
    for p in out:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
