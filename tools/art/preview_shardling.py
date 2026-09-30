"""Preview renderer for the Shardling GeckoLib model.

Reads geo/entity/shardling.geo.json, animations/entity/shardling.animation.json
and the textures, and renders orthographic views with a small software
rasteriser (numpy only). It follows GeckoLib 4.9's own code path, checked
against the 4.9.3 jar with javap:

* model x is mirrored on load (Bedrock -> Java space), bone and cube rotations
  become (-rx, -ry, rz) in radians, pivots and animated positions get x negated
* bone matrix = translate(position) * translate(pivot) * Rz * Ry * Rx * scale
  * translate(-pivot); cubes rotate the same way about their own pivot
* animated rotations are ADDED to the bind rotation from the geo file
* box UV: each face samples the rectangle GeckoLib's BakedModelFactory picks,
  with the same corner order as GeoQuad.build, so a texel painted on the wrong
  face shows up on the wrong face here too

Outputs (tools/art/previews/):
    shardling_side.png, shardling_front.png, shardling_top.png, shardling_34.png
    shardling_views.png           the four views on one sheet, textured
    shardling_glow.png            3/4 and side view with only the glowmask lit
    shardling_uvcheck.png         the texture with every cube's UV islands boxed
    shardling_anim_<name>.png     key pose large + a filmstrip across the clip

Checks (printed, and the exit code is 1 if 1 or 2 fail):
 1. every cube's UV island fits inside the texture, is fully painted, and no
    two islands overlap (unless both cubes carry the same "uv_share" key)
 2. texel by texel, the painter in gen_shardling.py and GeckoLib's box UV put
    the texel on the same 3D point (catches facets on the wrong face)
 3. the lowest point of the model over each clip (feet in the ground)

Run:  python tools/art/preview_shardling.py
"""
from __future__ import annotations

import math
import sys

import numpy as np
from PIL import Image, ImageDraw

from bedrock_model import load_anims as _load_anims, load_geo as _load_geo, pose_at, world_quads
from common import ASSETS, PREVIEWS, load_png, rel, save_png

GEO = ASSETS / "geo" / "entity" / "shardling.geo.json"
ANIM = ASSETS / "animations" / "entity" / "shardling.animation.json"
TEX = ASSETS / "textures" / "entity" / "shardling.png"
GLOW = ASSETS / "textures" / "entity" / "shardling_glowmask.png"

BG = np.array([92, 98, 108], dtype=np.float64)


# ------------------------------------------------------------------ loading
def load_geo(path=GEO):
    return _load_geo(path)


def load_anims(path=ANIM):
    return _load_anims(path)


# ------------------------------------------------------------------ rasteriser
def camera(view):
    """Orthonormal camera basis in Java model space: (right, up, forward)."""
    if view == "side":       # from the creature's left side, head points left
        f = np.array([1.0, 0, 0]); up = np.array([0, 1.0, 0])
    elif view == "front":    # looking at its face
        f = np.array([0, 0, 1.0]); up = np.array([0, 1.0, 0])
    elif view == "top":      # from above, head at the top of the image
        f = np.array([0, -1.0, 0]); up = np.array([0, 0, -1.0])
    elif view == "34":       # from the front-left, a little above
        el, az = math.radians(24), math.radians(40)
        f = np.array([math.sin(az) * math.cos(el), -math.sin(el), math.cos(az) * math.cos(el)])
        up = np.array([0, 1.0, 0])
    elif view == "rear34":   # from behind on the left, a little above
        el, az = math.radians(24), math.radians(140)
        f = np.array([math.sin(az) * math.cos(el), -math.sin(el), math.cos(az) * math.cos(el)])
        up = np.array([0, 1.0, 0])
    else:
        raise ValueError(view)
    f = f / np.linalg.norm(f)
    r = np.cross(f, up)
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return r, u, f


LIGHT = np.array([-0.45, 0.8, -0.4])   # Java space: from the left, above, in front
LIGHT /= np.linalg.norm(LIGHT)


def render(quads, tex, view, scale=16, size=(420, 300), centre=(0.0, 7.0, 0.0), glow=None,
           shade=True, outline=True, ground=True, add=None):
    """Rasterise quads (Java space). Returns an RGB uint8 image. `add`: a glowmask added over the lit colour."""
    W, H = size
    r, u, f = camera(view)
    img = np.empty((H, W, 3), dtype=np.float64)
    img[:] = BG
    depth = np.full((H, W), np.inf)
    cover = np.zeros((H, W), dtype=bool)
    th, tw = tex.shape[:2]
    c = np.array(centre, dtype=np.float64)
    c[0] = -c[0]

    def project(p):
        q = p - c
        return np.stack([W / 2 + (q @ r) * scale, H / 2 - (q @ u) * scale, q @ f], axis=-1)

    if ground:
        # ground grid at y = 0, one line per block (16 units)
        gl = []
        for k in range(-2, 3):
            gl.append(((k * 16, 0, -32), (k * 16, 0, 32)))
            gl.append(((-32, 0, k * 16), (32, 0, k * 16)))
        pil = Image.fromarray(img.astype(np.uint8))
        dr = ImageDraw.Draw(pil)
        for a, b in gl:
            pa, pb = project(np.array(a, float)), project(np.array(b, float))
            dr.line([(pa[0], pa[1]), (pb[0], pb[1])], fill=(80, 86, 96), width=1)
        img = np.array(pil).astype(np.float64)

    ys, xs = np.mgrid[0:H, 0:W]
    for q in quads:
        P = project(q["verts"])
        e1 = q["verts"][1] - q["verts"][0]
        e2 = q["verts"][3] - q["verts"][0]
        n = np.cross(e1, e2)
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        # face normal points outward for GeckoLib's winding; draw both sides anyway
        # (entity cutout no-cull), but light with the outward normal
        lam = 0.6 + 0.4 * max(0.0, float(n @ LIGHT)) if shade else 1.0
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
            texel = tex[tj, ti].astype(np.float64)
            alpha = texel[..., 3] > 0
            dsub = depth[ymin:ymax + 1, xmin:xmax + 1]
            upd = inside & alpha & (z < dsub - 1e-6)
            if not upd.any():
                continue
            col = texel[..., :3] * lam
            if add is not None:
                col = np.minimum(255.0, col + add[tj, ti][..., :3])
            if glow is not None:
                g = glow[tj, ti]
                lit = g[..., 3] > 0
                col = np.where(lit[..., None], texel[..., :3], col * 0.35)   # emissive: full colour, unlit
            sub = img[ymin:ymax + 1, xmin:xmax + 1]
            sub[upd] = col[upd]
            dsub[upd] = z[upd]
            cover[ymin:ymax + 1, xmin:xmax + 1] |= upd
    if outline:
        edge = np.zeros_like(cover)
        edge[1:, :] |= cover[:-1, :] & ~cover[1:, :]
        edge[:-1, :] |= cover[1:, :] & ~cover[:-1, :]
        edge[:, 1:] |= cover[:, :-1] & ~cover[:, 1:]
        edge[:, :-1] |= cover[:, 1:] & ~cover[:, :-1]
        img[edge] = (28, 30, 36)
    return np.clip(img, 0, 255).astype(np.uint8)


def label(img: np.ndarray, text: str, xy=(6, 4), colour=(235, 235, 235)) -> np.ndarray:
    pil = Image.fromarray(img)
    ImageDraw.Draw(pil).text(xy, text, fill=colour)
    return np.array(pil)


def to_rgba(img: np.ndarray) -> np.ndarray:
    return np.dstack([img, np.full(img.shape[:2], 255, np.uint8)])


def hstack(imgs, pad=6, colour=(60, 64, 72)):
    h = max(i.shape[0] for i in imgs)
    parts = []
    for i in imgs:
        if i.shape[0] < h:
            extra = np.zeros((h - i.shape[0], i.shape[1], 3), np.uint8)
            extra[:] = colour
            i = np.vstack([i, extra])
        parts.append(i)
        sep = np.zeros((h, pad, 3), np.uint8)
        sep[:] = colour
        parts.append(sep)
    return np.hstack(parts[:-1])


def vstack(imgs, pad=6, colour=(60, 64, 72)):
    w = max(i.shape[1] for i in imgs)
    parts = []
    for i in imgs:
        if i.shape[1] < w:
            extra = np.zeros((i.shape[0], w - i.shape[1], 3), np.uint8)
            extra[:] = colour
            i = np.hstack([i, extra])
        parts.append(i)
        sep = np.zeros((pad, w, 3), np.uint8)
        sep[:] = colour
        parts.append(sep)
    return np.vstack(parts[:-1])


# ------------------------------------------------------------------ checks
def uv_check(desc, bones, order, tex):
    tw, th = desc["texture_width"], desc["texture_height"]
    assert tex.shape[1] == tw and tex.shape[0] == th, f"texture is {tex.shape[1]}x{tex.shape[0]}, geo says {tw}x{th}"
    problems = []
    islands = []
    for name in order:
        for ci, cube in enumerate(bones[name].get("cubes", [])):
            w, h, d = (math.floor(s) for s in cube["size"])
            u, v = cube["uv"]
            fw, fh = 2 * (d + w), d + h
            if u < 0 or v < 0 or u + fw > tw or v + fh > th:
                problems.append(f"{name}[{ci}] island {u},{v} {fw}x{fh} leaves the {tw}x{th} texture")
            # used texels: the two top rects and the four side rects (the corners stay free)
            used = [(u + d, v, 2 * w, d), (u, v + d, fw, h)]
            islands.append((name, ci, used, cube.get("uv_share")))
            # every face that will be seen must be painted
            for (ru, rv, rw, rh) in used:
                reg = tex[rv:rv + rh, ru:ru + rw, 3]
                if reg.size and (reg == 0).any():
                    problems.append(f"{name}[{ci}] has transparent texels inside its island at {ru},{rv} {rw}x{rh}")
    # overlaps
    occ = {}
    for name, ci, used, share in islands:
        for (ru, rv, rw, rh) in used:
            for y in range(rv, rv + rh):
                for x in range(ru, ru + rw):
                    key = (x, y)
                    if key in occ:
                        other = occ[key]
                        if not (share and other[2] == share):
                            problems.append(f"UV overlap at {x},{y}: {name}[{ci}] and {other[0]}[{other[1]}]")
                    else:
                        occ[key] = (name, ci, share)
    # de-duplicate overlap spam
    seen, out = set(), []
    for p in problems:
        k = p.split(" at ")[0] + (p.split(": ")[1] if p.startswith("UV overlap") else "")
        if k not in seen:
            seen.add(k)
            out.append(p)
    return out


def painter_mapping_check(bones, order):
    """For every texel of every face: the 3D point the painter in gen_shardling
    thinks it paints must be the point GeckoLib actually puts that texel on
    (face rectangles and corner order from bedrock_model.cube_quads, which
    mirrors BakedModelFactory/GeoQuad). Returns the number of mismatches."""
    from bedrock_model import cube_quads
    from gen_shardling import Cube, texel_point
    names = {"east": "xneg", "west": "xpos", "north": "front", "south": "back", "up": "top", "down": "bottom"}
    bad = 0
    total = 0
    for name in order:
        for cube in bones[name].get("cubes", []):
            flat = dict(cube)
            flat.pop("inflate", None)
            c = Cube(tuple(cube["origin"]), tuple(cube["size"]), "", uv=tuple(cube["uv"]))
            for face, verts, uvs in cube_quads(flat):
                (u_a, v_a), (u_b, v_b), (u_c, v_c) = uvs[0], uvs[1], uvs[2]
                us, vs = u_a - u_b, v_c - v_b        # vertex1 is (u, v); 0 is +us, 2 is +vs
                if us == 0 or vs == 0:
                    continue
                cols, rows = int(abs(us)), int(abs(vs))
                for r in range(rows):
                    for col in range(cols):
                        # texel centre in the atlas
                        U = min(u_a, u_b) + col + 0.5
                        V = min(v_b, v_c) + r + 0.5
                        s_ = (U - u_b) / us
                        t_ = (V - v_b) / vs
                        pj = verts[1] + s_ * (verts[0] - verts[1]) + t_ * (verts[2] - verts[1])
                        gecko = np.array([-pj[0], pj[1], pj[2]])          # back to Bedrock x
                        # the painter indexes texels from its own rectangle origin
                        ru = min(u_a, u_b)
                        rv = min(v_b, v_c)
                        mine = np.array(texel_point(c, names[face], int(U - ru), int(V - rv)))
                        total += 1
                        if np.abs(gecko - mine).max() > 1e-6:
                            bad += 1
    return bad, total


def uv_sheet(desc, bones, order, tex, k=8):
    big = np.repeat(np.repeat(tex, k, axis=0), k, axis=1)
    bg = np.zeros_like(big)
    ys, xs = np.mgrid[0:big.shape[0], 0:big.shape[1]]
    chk = ((xs // k + ys // k) % 2 == 0)
    bg[..., :3] = np.where(chk[..., None], 70, 84)
    bg[..., 3] = 255
    a = big[..., 3:4] / 255.0
    comp = (big[..., :3] * a + bg[..., :3] * (1 - a)).astype(np.uint8)
    pil = Image.fromarray(comp)
    dr = ImageDraw.Draw(pil)
    for name in order:
        for ci, cube in enumerate(bones[name].get("cubes", [])):
            w, h, d = (math.floor(s) for s in cube["size"])
            u, v = cube["uv"]
            dr.rectangle([u * k, (v + d) * k, (u + 2 * (d + w)) * k - 1, (v + d + h) * k - 1], outline=(255, 80, 200))
            dr.rectangle([(u + d) * k, v * k, (u + d + 2 * w) * k - 1, (v + d) * k - 1], outline=(255, 80, 200))
            dr.text((u * k + 2, (v + d) * k + 1), f"{name}{ci}", fill=(255, 255, 255))
    return np.array(pil)


# ------------------------------------------------------------------ main
ANIM_KEY_TIME = {
    "idle": 0.5, "walk": 0.15, "run": 0.1, "crouch_tell": 0.6, "lunge": 0.2,
    "recover": 0.3, "spit_tell": 0.5, "spit": 0.1, "stagger": 0.12,
}


def main():
    desc, bones, order = load_geo()
    tex = load_png(TEX)
    glow = load_png(GLOW)
    anims = load_anims()

    problems = uv_check(desc, bones, order, tex)
    assert glow.shape == tex.shape, "glowmask must match the texture size"
    print(f"texture {tex.shape[1]}x{tex.shape[0]}, {sum(len(bones[n].get('cubes', [])) for n in order)} cubes, "
          f"{len(order)} bones")
    if problems:
        print("UV problems:")
        for p in problems:
            print("  ", p)
    else:
        print("UV check: every island inside the texture, fully painted, no unintended overlaps")

    bad, total = painter_mapping_check(bones, order)
    print(f"painter vs GeckoLib box UV: {total - bad}/{total} texels land where the painter meant"
          + ("" if bad == 0 else f"  ({bad} MISMATCHES)"))
    rest = world_quads(desc, bones, order, {})
    views = {}
    for v, size in (("side", (440, 300)), ("front", (300, 300)), ("top", (300, 440)), ("34", (440, 320))):
        centre = (0.0, 7.0, 0.0) if v != "top" else (0.0, 0.0, 1.0)
        im = render(rest, tex, v, scale=16, size=size, centre=centre)
        views[v] = label(im, {"side": "side (left)", "front": "front", "top": "top", "34": "three-quarter"}[v])
        save_png(to_rgba(views[v]), PREVIEWS / f"shardling_{v}.png")
    sheet = vstack([hstack([views["side"], views["front"]]), hstack([views["34"], views["top"]])])
    save_png(to_rgba(sheet), PREVIEWS / "shardling_views.png")

    g1 = render(rest, tex, "34", scale=16, size=(440, 320), glow=glow)
    g2 = render(rest, tex, "side", scale=16, size=(440, 300), glow=glow)
    save_png(to_rgba(hstack([label(g1, "glowmask lit, rest dimmed"), g2])), PREVIEWS / "shardling_glow.png")
    save_png(to_rgba(uv_sheet(desc, bones, order, tex)), PREVIEWS / "shardling_uvcheck.png")

    print("lowest point per clip (y = 0 is the ground; airborne clips may float):")
    for name, anim in anims.items():
        length = float(anim.get("animation_length", 1.0))
        lows = []
        for i in range(21):
            t = length * i / 20
            qs = world_quads(desc, bones, order, pose_at(anim, t))
            lows.append(min(float(q["verts"][:, 1].min()) for q in qs))
        print(f"   {name:12s} min {min(lows):+.2f}  max {max(lows):+.2f}  end {lows[-1]:+.2f}")
    for name, anim in anims.items():
        length = float(anim.get("animation_length", 1.0))
        tkey = min(ANIM_KEY_TIME.get(name, length), length)
        key = world_quads(desc, bones, order, pose_at(anim, tkey))
        big_side = label(render(key, tex, "side", scale=16, size=(440, 330), centre=(0.0, 8.5, 0.0)),
                         f"{name} @ {tkey:.2f}s  (length {length}s, loop {anim.get('loop', False)})")
        big_34 = render(key, tex, "34", scale=16, size=(440, 330), centre=(0.0, 8.5, 0.0))
        frames = []
        n = 6
        for i in range(n):
            t = length * i / (n - 1)
            fq = world_quads(desc, bones, order, pose_at(anim, t))
            frames.append(label(render(fq, tex, "side", scale=7, size=(190, 130), outline=True), f"{t:.2f}s"))
        strip = hstack(frames, pad=4)
        sheet = vstack([hstack([big_side, big_34]), strip])
        save_png(to_rgba(sheet), PREVIEWS / f"shardling_anim_{name}.png")
    print("wrote previews to", rel(PREVIEWS))
    return 1 if (problems or bad) else 0


if __name__ == "__main__":
    sys.exit(main())
