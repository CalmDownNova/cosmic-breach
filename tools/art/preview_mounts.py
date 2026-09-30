"""Previews of the celestial mounts, drawn the way GeckoLib poses them (bedrock_model), with the glow added.

Writes tools/art/previews/lumen_stag_views.png (front, side, back, three quarters; bare, then saddled with Nebulite
barding and the Comet Bridle; then the Starsteel barding and the Halo Reins), lumen_stag_poses.png (walk, run, leap,
glide, eat), drift_manta_views.png (front, side, back, top, three quarters; bare, then in its harness with Nebulite and
the Gale Fins) and drift_manta_poses.png (hover, swim, glide, rest).

Run:  python tools/art/preview_mounts.py
"""
from __future__ import annotations

import numpy as np

import preview_colossus as pc
import preview_shardling as ps
from bedrock_model import load_anims, load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, load_png, rel, save_png

TEXDIR = ASSETS / "textures" / "entity"


def quads(name, anim, t, gear=()):
    desc, bones, order = load_geo(ASSETS / "geo" / "entity" / f"{name}.geo.json")
    anims = load_anims(ASSETS / "animations" / "entity" / f"{name}.animation.json")
    pose = pose_at(anims.get(anim), t) if anim else {}
    q = world_quads(desc, bones, order, pose)

    def shown(bone):
        b = bone
        while b:
            if b.startswith("gear_"):
                return any(b.startswith("gear_" + g) for g in gear)
            b = bones[b].get("parent")
        return True
    return [qq for qq in q if shown(qq["bone"])]


def fit(q, view, size, margin=0.86):
    r, u, f = pc.camera(view)
    pts = np.concatenate([qq["verts"] for qq in q])
    xs, ys = pts @ r, pts @ u
    mid = np.array([(xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2])
    centre = r * mid[0] + u * mid[1]
    scale = margin * min(size[0] / max(1e-6, xs.max() - xs.min()), size[1] / max(1e-6, ys.max() - ys.min()))
    return (-centre[0], centre[1], centre[2]), scale


def render(q, tex, view, add, size=(300, 300), scale=None, centre=None):
    c, s = fit(q, view, size)
    if scale is not None:
        s = scale
    if centre is not None:
        c = centre
    old = ps.camera
    ps.camera = pc.camera
    try:
        return ps.render(q, tex, view, scale=s, size=size, centre=c, glow=None, ground=False, add=add)
    finally:
        ps.camera = old


def glow_sum(*masks, scales):
    out = np.zeros_like(masks[0], dtype=np.float64)
    for m, k in zip(masks, scales):
        out += m.astype(np.float64) * k
    return np.clip(out, 0, 255)


def main():
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    out = []
    # the Stag
    tex = load_png(TEXDIR / "lumen_stag.png")
    antl = load_png(TEXDIR / "lumen_stag_glowmask.png")
    gear = load_png(TEXDIR / "lumen_stag_gear_glowmask.png")
    lit = glow_sum(antl, gear, scales=(1.0, 1.0))
    dim = glow_sum(antl, gear, scales=(0.12, 1.0))
    rows = []
    for label, gearset, add in (("wild, trust 0", (), dim), ("saddle, Nebulite, Comet Bridle", ("astral_saddle", "nebulite_barding", "comet_bridle"), lit),
                                ("Starsteel, Halo Reins", ("astral_saddle", "starsteel_barding", "halo_reins"), lit)):
        q = quads("lumen_stag", "idle", 0.4, gearset)
        rows.append(ps.hstack([ps.label(render(q, tex, v, add), f"{v}: {label}") for v in ("front", "side", "back", "34")]))
    out.append(save_png(ps.to_rgba(ps.vstack(rows)), PREVIEWS / "lumen_stag_views.png"))
    poses = []
    for anim, t in (("walk", 0.3), ("run", 0.15), ("run", 0.42), ("leap", 0.0), ("glide", 0.5), ("eat", 0.2)):
        q = quads("lumen_stag", anim, t, ("astral_saddle",))
        poses.append(ps.label(render(q, tex, "side", lit, size=(280, 280)), f"{anim} {t}"))
    out.append(save_png(ps.to_rgba(ps.hstack(poses)), PREVIEWS / "lumen_stag_poses.png"))
    # the Manta
    tex = load_png(TEXDIR / "drift_manta.png")
    spots = load_png(TEXDIR / "drift_manta_glowmask.png")
    mgear = load_png(TEXDIR / "drift_manta_gear_glowmask.png")
    lit = glow_sum(spots, mgear, scales=(1.0, 1.0))
    soft = glow_sum(spots, mgear, scales=(0.3, 1.0))
    rows = []
    for label, gearset, add in (("wild", (), soft), ("harness, Nebulite, Gale Fins", ("drift_harness", "nebulite_barding", "gale_fins", "nebula_reins"), lit)):
        q = quads("drift_manta", "hover", 0.6, gearset)
        rows.append(ps.hstack([ps.label(render(q, tex, v, add, size=(320, 240)), f"{v}: {label}") for v in ("front", "side", "back", "top", "34")]))
    out.append(save_png(ps.to_rgba(ps.vstack(rows)), PREVIEWS / "drift_manta_views.png"))
    poses = []
    for anim, t in (("hover", 0.0), ("hover", 1.2), ("swim", 0.0), ("swim", 0.35), ("swim", 0.7), ("glide", 0.5), ("rest", 0.0)):
        q = quads("drift_manta", anim, t, ())
        poses.append(ps.label(render(q, tex, "34", soft, size=(300, 240)), f"{anim} {t}"))
    out.append(save_png(ps.to_rgba(ps.hstack(poses)), PREVIEWS / "drift_manta_poses.png"))
    for p in out:
        print("wrote", rel(p))


if __name__ == "__main__":
    main()
