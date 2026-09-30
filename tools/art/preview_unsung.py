"""Previews of the Unsung's masks, rendered the way the game draws them (bedrock_model's maths, per-face UV).

Writes tools/art/previews/unsung_views.png. Each mask as the singer (full light, its glow at full strength), as a
humming mask (the light a humming mask gets, tinted cool grey the way UnsungMaskRenderer tints it) and broken, from
the front, three quarters and the side; and the three together from afar. Like the game: the entity's two fixed
world lights (0.6 each, 0.4 ambient) on the model turned toward the viewer, the lightmap's colour, the render colour,
alpha blended in model order with depth writes (entityTranslucent), then the glowmask added unshaded (the eyes pass).

Run:  python tools/art/preview_unsung.py
"""
from __future__ import annotations

import json
import math

import numpy as np

import preview_shardling as ps
from bedrock_model import gecko_rot, load_anims, pose_at, trans
from common import ASSETS, PREVIEWS, load_png, save_png

VOICES = ("alto", "tenor", "bass")
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
BG = np.array([14.0, 11.0, 22.0])

# (lightmap, render colour, glow colour) as UnsungMaskRenderer gives them
SINGER = ((1.0, 1.0, 1.0), (1.0, 1.0, 1.0), (0.95, 0.95, 0.95))
HUMMER = ((0.64, 0.56, 0.40), (0.66, 0.76, 1.0), (0.30, 0.36, 0.48))
BROKEN = ((0.45, 0.40, 0.32), (1.0, 1.0, 1.0), (0.10, 0.10, 0.10))


def face_verts(cube):
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    x0, y0, z0 = -(ox + sx), oy, oz
    x1, y1, z1 = -ox, oy + sy, oz + sz
    blb = (x0, y0, z0); brb = (x0, y0, z1); tlb = (x0, y1, z0); trb = (x0, y1, z1)
    tlf = (x1, y1, z0); trf = (x1, y1, z1); blf = (x1, y0, z0); brf = (x1, y0, z1)
    return {
        "west": [trb, tlb, blb, brb],
        "east": [tlf, trf, brf, blf],
        "north": [tlb, tlf, blf, blb],
        "south": [trf, trb, brb, brf],
        "up": [trb, trf, tlf, tlb],
        "down": [blb, blf, brf, brb],
    }


def bone_matrix(bones, name, pose, cache):
    if name in cache:
        return cache[name]
    b = bones[name]
    px, py, pz = b.get("pivot", [0, 0, 0])
    rx, ry, rz = b.get("rotation", [0, 0, 0])
    pos = [0.0, 0.0, 0.0]
    scl = [1.0, 1.0, 1.0]
    anim = pose.get(name)
    if anim:
        r, p, s = anim
        if r:
            rx, ry, rz = rx + r[0], ry + r[1], rz + r[2]
        if p:
            pos = p
        if s:
            scl = s
    pj = (-px, py, pz)
    local = (trans(-pos[0], pos[1], pos[2]) @ trans(*pj) @ gecko_rot(rx, ry, rz) @ np.diag([*scl, 1.0]) @ trans(*[-c for c in pj]))
    parent = b.get("parent")
    m = bone_matrix(bones, parent, pose, cache) @ local if parent else local
    cache[name] = m
    return m


def quads(geo, pose):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    cache = {}
    out = []
    for b in g["bones"]:
        m = bone_matrix(bones, b["name"], pose, cache)
        for ci, cube in enumerate(b.get("cubes", [])):
            mc = m
            if "rotation" in cube:
                cpx, cpy, cpz = cube["pivot"]
                pj = (-cpx, cpy, cpz)
                mc = m @ trans(*pj) @ gecko_rot(*cube["rotation"]) @ trans(*[-c for c in pj])
            verts = face_verts(cube)
            for face, uv in cube["uv"].items():
                u, v = uv["uv"]
                us, vs = uv["uv_size"]
                uvs = np.array([(u + us, v), (u, v), (u, v + vs), (u + us, v + vs)], dtype=np.float64)
                wv = (mc @ np.c_[np.array(verts[face], dtype=np.float64), np.ones(4)].T).T[:, :3]
                out.append(dict(bone=b["name"], cube=ci, face=face, verts=wv, uvs=uvs))
    return out


def basis(az_deg, el_deg):
    """Camera (right, up, forward) looking at a face that looks toward -Z, from az degrees round it, el above."""
    az, el = math.radians(az_deg), math.radians(el_deg)
    f = np.array([math.sin(az) * math.cos(el), -math.sin(el), math.cos(az) * math.cos(el)])
    up = np.array([0.0, 1.0, 0.0]) if abs(el_deg) < 80 else np.array([0.0, 0.0, 1.0])
    r = np.cross(f, up)
    r /= np.linalg.norm(r)
    return r, np.cross(r, f), f


def render(qs, tex, glow, look, yaw_deg=40.0, az=0.0, el=6.0, size=(230, 300), scale=5.2, centre=(0.0, 30.0, 0.0), img=None,
           depth=None, offset=(0.0, 0.0)):
    """Rasterise quads like the game. The model is turned yaw_deg in the world (the lights stay), the camera az round
    the face."""
    lightmap, colour, gcol = (np.array(a, dtype=np.float64) for a in look)
    W, H = size
    r, u, f = basis(az + yaw_deg, el)
    if img is None:
        img = np.empty((H, W, 3))
        img[:] = BG
        depth = np.full((H, W), np.inf)
    yaw = math.radians(yaw_deg)
    turn = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    c = np.array(centre, dtype=np.float64)
    c[0] = -c[0]
    th, tw = tex.shape[:2]
    ys, xs = np.mgrid[0:H, 0:W]

    def project(p):
        q = p - c
        return np.stack([W / 2 + offset[0] + (q @ r) * scale, H / 2 + offset[1] - (q @ u) * scale, q @ f], axis=-1)

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
        v = q["verts"] @ turn.T
        e1 = v[1] - v[0]
        e2 = v[3] - v[0]
        nrm = np.cross(e1, e2)
        nn = np.linalg.norm(nrm)
        if nn < 1e-9:
            continue
        nrm /= nn
        world.append(dict(q, verts=v, light=min(1.0, 0.6 * (max(0.0, nrm @ L0) + max(0.0, nrm @ L1)) + 0.4)))
    for q in world:
        for (sy_, sx_), inside, z, tj, ti in spans(q):
            texel = tex[tj, ti].astype(np.float64)
            a = texel[..., 3] / 255.0
            dsub = depth[sy_, sx_]
            upd = inside & (a >= 0.1) & (z < dsub - 1e-6)
            if not upd.any():
                continue
            col = texel[..., :3] * q["light"] * lightmap * colour
            sub = img[sy_, sx_]
            sub[upd] = col[upd] * a[upd, None] + sub[upd] * (1.0 - a[upd, None])
            dsub[upd] = z[upd]
    for q in world:
        for (sy_, sx_), inside, z, tj, ti in spans(q):
            add = glow[tj, ti][..., :3].astype(np.float64) * gcol
            dsub = depth[sy_, sx_]
            upd = inside & (z <= dsub + 0.05)
            sub = img[sy_, sx_]
            sub[upd] = np.minimum(255.0, sub[upd] + add[upd])
    return img, depth


def load(name, broken=False):
    geo = json.loads((ASSETS / "geo" / "entity" / f"unsung_{name}.geo.json").read_text(encoding="utf-8"))
    anims = load_anims(ASSETS / "animations" / "entity" / f"unsung_{name}.animation.json")
    sfx = "_broken" if broken else ""
    tex = load_png(ASSETS / "textures" / "entity" / f"unsung_{name}{sfx}.png").astype(np.float64)
    glow = load_png(ASSETS / "textures" / "entity" / f"unsung_{name}{sfx}_glowmask.png").astype(np.float64)
    return geo, anims, tex, glow


def main():
    rows = []
    far_img, far_depth = None, None
    for i, name in enumerate(VOICES):
        geo, anims, tex, glow = load(name)
        sing = quads(geo, pose_at(anims["sing"], 0.3))
        hum = quads(geo, pose_at(anims["float"], 0.3))
        cells = []
        for label, qs, look, az, el in (("sings front", sing, SINGER, 0, 6), ("sings 3/4", sing, SINGER, 38, 12),
                                        ("sings side", sing, SINGER, 90, 4), ("hums front", hum, HUMMER, 0, 6),
                                        ("hums 3/4", hum, HUMMER, -38, 12)):
            img, _ = render(qs, tex, glow, look, az=az, el=el, centre=(0.0, 26.0, 0.0), scale=4.2)
            cells.append(ps.label(np.clip(img, 0, 255).astype(np.uint8), f"{name} {label}"))
        bgeo, banims, btex, bglow = load(name, broken=True)
        broken = quads(bgeo, pose_at(banims["broken"], 0.5))
        img, _ = render(broken, btex, bglow, BROKEN, az=0, el=60, centre=(0.0, 36.0, -8.0), scale=4.2)
        cells.append(ps.label(np.clip(img, 0, 255).astype(np.uint8), f"{name} broken"))
        rows.append(ps.hstack(cells))
        # afar: the Alto sings, the others hum
        look = SINGER if name == "alto" else HUMMER
        qs = sing if name == "alto" else hum
        far_img, far_depth = render(qs, tex, glow, look, az=0, el=4, size=(420, 180), scale=1.6, centre=(0.0, 26.0, 0.0),
                                    img=far_img, depth=far_depth, offset=((i - 1) * 120.0, 0.0))
    rows.append(ps.label(np.clip(far_img, 0, 255).astype(np.uint8), "afar: the Alto sings, Tenor and Bass hum"))
    sheet = ps.vstack(rows)
    save_png(ps.to_rgba(sheet), PREVIEWS / "unsung_views.png")
    print("  previews/unsung_views.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
