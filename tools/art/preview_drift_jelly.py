"""Previews of the drift jelly, rendered the way the game draws it (bedrock_model's maths, per-face UV, back faces culled,
entityTranslucent alpha blended in model order with depth writes, then the glowmask added unshaded).

Writes tools/art/previews/drift_jelly_views.png (front, three quarters, side, from below, from above) and
drift_jelly_anim_<clip>.png (a filmstrip per clip). With --media also writes the same renders to
C:\\Users\\puppy\\Media\\Aetheria\\Generated\\DriftJelly\\geckolib_*.png for comparing with the concept and the Meshy views.

Run:  python tools/art/preview_drift_jelly.py [--media]
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import preview_unsung as pu
from bedrock_model import load_anims, pose_at
from common import ASSETS, PREVIEWS, load_png, save_png

MEDIA = Path(r"C:\Users\puppy\Media\Aetheria\Generated\DriftJelly")
BG = np.array([38.0, 38.0, 42.0])
LOOK = ((1.0, 1.0, 1.0), (1.0, 1.0, 1.0), (1.0, 1.0, 1.0))


def load():
    geo = json.loads((ASSETS / "geo" / "entity" / "drift_jelly.geo.json").read_text(encoding="utf-8"))
    anims = load_anims(ASSETS / "animations" / "entity" / "drift_jelly.animation.json")
    tex = load_png(ASSETS / "textures" / "entity" / "drift_jelly.png").astype(np.float64)
    glow = load_png(ASSETS / "textures" / "entity" / "drift_jelly_glowmask.png").astype(np.float64)
    return geo, anims, tex, glow


def render(qs, tex, glow, az, el, size=(520, 760), scale=5.0, centre=(0.0, -20.0, 0.0), look=LOOK, ground_y=None):
    """pu.render with the game's face culling (a translucent bell must not show its far side) and a fixed background."""
    lightmap, colour, gcol = (np.array(a, dtype=np.float64) for a in look)
    W, H = size
    r, u, f = pu.basis(az, el)
    img = np.empty((H, W, 3))
    img[:] = BG
    depth = np.full((H, W), np.inf)
    c = np.array(centre, dtype=np.float64)
    c[0] = -c[0]
    th, tw = tex.shape[:2]
    ys, xs = np.mgrid[0:H, 0:W]

    def project(p):
        q = p - c
        return np.stack([W / 2 + (q @ r) * scale, H / 2 - (q @ u) * scale, q @ f], axis=-1)

    def spans(q):
        P = project(q["verts"])
        for tri in ((0, 1, 2), (0, 2, 3)):
            p = P[list(tri)]
            uv = q["uvs"][list(tri)]
            xmin = max(int(math.floor(p[:, 0].min())), 0)
            xmax = min(int(math.ceil(p[:, 0].max())), W - 1)
            ymin = max(int(math.floor(p[:, 1].min())), 0)
            ymax = min(int(math.ceil(p[:, 1].max())), H - 1)
            if xmax < xmin or ymax < ymin:
                continue
            (x0, y0, _), (x1, y1, _), (x2, y2, _) = p
            den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(den) < 1e-9:
                continue
            sx = xs[ymin:ymax + 1, xmin:xmax + 1] + 0.5
            sy = ys[ymin:ymax + 1, xmin:xmax + 1] + 0.5
            w0 = ((y1 - y2) * (sx - x2) + (x2 - x1) * (sy - y2)) / den
            w1 = ((y2 - y0) * (sx - x2) + (x0 - x2) * (sy - y2)) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
            tu = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
            tv = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
            ti = np.clip(np.floor(tu - 1e-4 * np.sign(tu - uv[:, 0].mean())).astype(int), 0, tw - 1)
            tj = np.clip(np.floor(tv - 1e-4 * np.sign(tv - uv[:, 1].mean())).astype(int), 0, th - 1)
            yield (slice(ymin, ymax + 1), slice(xmin, xmax + 1)), inside, z, tj, ti

    world = []
    for q in qs:
        v = q["verts"]
        nrm = np.cross(v[1] - v[0], v[3] - v[0])
        nn = np.linalg.norm(nrm)
        if nn < 1e-9:
            continue
        nrm /= nn
        if nrm @ f > 0:                      # facing away: the game culls it
            continue
        world.append(dict(q, light=min(1.0, 0.6 * (max(0.0, nrm @ pu.L0) + max(0.0, nrm @ pu.L1)) + 0.4)))
    for q in world:
        for (sy_, sx_), inside, z, tj, ti in spans(q):
            texel = tex[tj, ti].astype(np.float64)
            a = texel[..., 3] / 255.0
            dsub = depth[sy_, sx_]
            upd = inside & (a >= 0.04) & (z < dsub - 1e-6)
            if not upd.any():
                continue
            col = texel[..., :3] * q["light"] * lightmap * colour
            sub = img[sy_, sx_]
            sub[upd] = col[upd] * a[upd, None] + sub[upd] * (1.0 - a[upd, None])
            if (a[upd] > 0.9).all():
                dsub[upd] = z[upd]
            else:
                dsub[upd] = np.where(a[upd] > 0.9, z[upd], dsub[upd])
    for q in world:
        for (sy_, sx_), inside, z, tj, ti in spans(q):
            add = glow[tj, ti][..., :3].astype(np.float64) * gcol
            dsub = depth[sy_, sx_]
            upd = inside & (z <= dsub + 0.05)
            sub = img[sy_, sx_]
            sub[upd] = np.minimum(255.0, sub[upd] + add[upd])
    return np.clip(img, 0, 255).astype(np.uint8)


def label(img, text):
    pil = Image.fromarray(img)
    ImageDraw.Draw(pil).text((8, 6), text, fill=(235, 235, 235))
    return np.array(pil)


def hstack(imgs, pad=6):
    h = max(i.shape[0] for i in imgs)
    parts = []
    for i in imgs:
        if i.shape[0] < h:
            extra = np.zeros((h - i.shape[0], i.shape[1], 3), np.uint8)
            extra[:] = 60
            i = np.vstack([i, extra])
        parts.append(i)
        parts.append(np.full((h, pad, 3), 60, np.uint8))
    return np.hstack(parts[:-1])


def save_rgb(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img).save(path)


def main():
    media = "--media" in sys.argv
    geo, anims, tex, glow = load()
    rest = pu.quads(geo, {})
    views = [("front", 0, 6), ("34", 38, 18), ("side", 90, 6), ("below", 30, -35), ("above", 30, 55)]
    shots = {}
    for name, az, el in views:
        shots[name] = render(rest, tex, glow, az, el)
    sheet = hstack([label(shots[n], n) for n, _, _ in views[:3]])
    sheet2 = hstack([label(shots[n], n) for n, _, _ in views[3:]])
    save_rgb(sheet, PREVIEWS / "drift_jelly_views.png")
    save_rgb(sheet2, PREVIEWS / "drift_jelly_views_extra.png")
    for clip, n, tmax in (("idle", 6, None), ("hurt", 5, None), ("squish", 5, None), ("death", 5, None)):
        a = anims[clip]
        length = a["animation_length"]
        frames = []
        for i in range(n):
            t = length * (i / n if clip == "idle" else i / (n - 1))
            frames.append(label(render(pu.quads(geo, pose_at(a, t)), tex, glow, 38, 14, size=(300, 520), scale=3.7, centre=(0, -22, 0)),
                                f"{clip} {t:.2f}s"))
        save_rgb(hstack(frames), PREVIEWS / f"drift_jelly_anim_{clip}.png")
    if media:
        for name in ("front", "34", "side"):
            save_rgb(shots[name], MEDIA / f"geckolib_{name}.png")
        save_rgb(sheet, MEDIA / "geckolib_views.png")
        save_rgb(hstack([label(shots["34"], "geckolib 3/4")]), MEDIA / "geckolib_34_labeled.png")
    print("wrote previews")


if __name__ == "__main__":
    main()
