"""A small z-buffered software rasteriser for the posed player and the held sword.

Views are orthographic (side, front, top, back) or a perspective first-person
camera at the eye. Colour code: right-side limbs warm, left-side limbs cool,
face side of the head marked, sword in its sprite colours.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import rig

BG = np.array([0.93, 0.93, 0.9])
COL = {
    "head": (0.87, 0.70, 0.55),
    "face": (0.55, 0.36, 0.28),
    "torso": (0.45, 0.62, 0.62),
    "rightArm": (0.93, 0.47, 0.30),
    "leftArm": (0.42, 0.52, 0.92),
    "rightLeg": (0.72, 0.33, 0.25),
    "leftLeg": (0.30, 0.38, 0.70),
}
PART_ID = {"head": 1, "torso": 2, "rightArm": 3, "leftArm": 4, "rightLeg": 5, "leftLeg": 6, "item": 7}

try:
    FONT = ImageFont.truetype("arial.ttf", 12)
    FONT_S = ImageFont.truetype("arial.ttf", 10)
    FONT_L = ImageFont.truetype("arial.ttf", 16)
except OSError:  # pragma: no cover
    FONT = FONT_S = FONT_L = ImageFont.load_default()

# box faces: corner index lists (corners ordered x-major, then y, then z as in rig.box_corners)
_FACES = {
    "-x": (0, 1, 3, 2), "+x": (4, 6, 7, 5),
    "-y": (0, 4, 5, 1), "+y": (2, 3, 7, 6),
    "-z": (0, 2, 6, 4), "+z": (1, 5, 7, 3),
}


@dataclass
class Scene:
    tris: list = field(default_factory=list)  # (3x3 local points, rgb, id)
    lines: list = field(default_factory=list)  # (p0, p1, rgb, width_px, depth_test)

    def box(self, m, corners, rgb, pid, face_rgb=None):
        """corners: 8 points in the box's own frame (blocks), ordered like rig.box_corners."""
        w = rig.xf(m, corners)
        centre = w.mean(axis=0)
        for fi, (name, idx) in enumerate(_FACES.items()):
            q = w[list(idx)]
            c = face_rgb.get(name, rgb) if face_rgb else rgb
            n = np.cross(q[1] - q[0], q[2] - q[0])
            if np.dot(n, q.mean(axis=0) - centre) < 0:
                n = -n
            nn = np.linalg.norm(n)
            n = n / nn if nn > 1e-12 else n
            fid = pid * 16 + fi
            self.tris.append((q[[0, 1, 2]], c, fid, n))
            self.tris.append((q[[0, 2, 3]], c, fid, n))

    def line(self, p0, p1, rgb, width=1.5, depth_test=True):
        self.lines.append((np.asarray(p0, float), np.asarray(p1, float), rgb, width, depth_test))


def box8(mn, mx):
    xs, ys, zs = (mn[0], mx[0]), (mn[1], mx[1]), (mn[2], mx[2])
    return np.array([[x, y, z] for x in xs for y in ys for z in zs], dtype=float)


def build_scene(posed: rig.Posed, geom: rig.ItemGeom, *, parts=None, item=True) -> Scene:
    sc = Scene()
    for name in parts or ("head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg"):
        face = {"-z": COL["face"]} if name == "head" else None
        sc.box(posed.parts[name], rig.box_corners(name), COL[name], PART_ID[name], face)
    if item and posed.item is not None:
        for mn, mx, rgb in geom.boxes:
            sc.box(posed.item, box8(mn, mx), tuple(rgb), PART_ID["item"])
        pts = rig.item_points(posed, geom)
        sc.line(pts["guard"], pts["tip"], (0.80, 0.84, 0.95), 1.6)
        sc.line(pts["pommel"], pts["guard"], (0.55, 0.40, 0.15), 1.6)
    if item and posed.item_left is not None:
        left = _left_geom()
        for mn, mx, rgb in left.boxes:
            sc.box(posed.item_left, box8(mn, mx), tuple(rgb), PART_ID["item"])
        pts = rig.item_points(posed, left, left=True)
        sc.line(pts["guard"], pts["tip"], (0.86, 0.76, 0.96), 1.6)
        sc.line(pts["pommel"], pts["guard"], (0.55, 0.40, 0.15), 1.6)
    return sc


_LEFT_GEOMS = {}


def _left_geom():
    """The current dual weapon's left-hand item (its own sprite), loaded once."""
    w = rig.current_weapon()
    if w not in _LEFT_GEOMS:
        _LEFT_GEOMS[w] = rig.load_item(left=True)
    return _LEFT_GEOMS[w]


# ----------------------------------------------------------------------------- views


@dataclass
class View:
    name: str
    w: int
    h: int
    kind: str = "ortho"  # or "persp"
    right: np.ndarray = None
    up: np.ndarray = None
    fwd: np.ndarray = None  # into the screen
    centre: np.ndarray = None  # ortho: world point at the image centre; persp: eye
    scale: float = 50.0  # ortho px per block
    fov: float = 70.0  # persp vertical degrees
    near: float = 0.05

    def to_cam(self, p):
        d = np.asarray(p, float) - self.centre
        return np.stack([d @ self.right, d @ self.up, d @ self.fwd], axis=-1)

    def project_cam(self, c):
        """camera coords (n,3) -> screen x, y, depth (n,3)."""
        if self.kind == "ortho":
            sx = self.w / 2 + c[:, 0] * self.scale
            sy = self.h / 2 - c[:, 1] * self.scale
            return np.stack([sx, sy, c[:, 2]], axis=-1)
        f = (self.h / 2) / math.tan(math.radians(self.fov) / 2)
        z = c[:, 2]
        sx = self.w / 2 + f * c[:, 0] / z
        sy = self.h / 2 - f * c[:, 1] / z
        return np.stack([sx, sy, z], axis=-1)


def ortho(name, w, h, scale, right, up, centre):
    right, up = np.array(right, float), np.array(up, float)
    fwd = np.cross(up, right)  # into the screen for a right-handed camera
    return View(name, w, h, "ortho", right, up, fwd, np.array(centre, float), scale)


def standard_views(w=176, h=176, scale=44.0, cy=1.15):
    """side: from the player's right (player faces screen-right). front: facing the viewer.
    top: from above, forward is screen-up. back: behind the player (the F5 direction)."""
    return {
        "side": ortho("side", w, h, scale, (0, 0, -1), (0, 1, 0), (0, cy, -0.35)),
        "front": ortho("front", w, h, scale, (-1, 0, 0), (0, 1, 0), (0, cy, 0)),
        "top": ortho("top", w, h, scale, (1, 0, 0), (0, 0, -1), (0, 1.0, -0.35)),
        "back": ortho("back", w, h, scale, (1, 0, 0), (0, 1, 0), (0, cy, 0)),
    }


def first_person(w=256, h=144, pitch_deg=0.0, fov=70.0):
    """Camera at the eye looking along -Z (yaw 0), pitch positive = looking down."""
    p = math.radians(pitch_deg)
    fwd = np.array([0.0, -math.sin(p), -math.cos(p)])
    up = np.array([0.0, math.cos(p), -math.sin(p)])
    right = np.cross(fwd, up)
    return View("fp", w, h, "persp", right, up, fwd, np.array([0.0, rig.EYE_HEIGHT, 0.0]), fov=fov)


def three_quarter(w=320, h=320, scale=70.0, cy=1.15):
    """From behind-right and a little above, like a raised F5 camera turned 35 degrees."""
    yaw, pitch = math.radians(35.0), math.radians(20.0)
    # camera sits behind (+Z) and to the right (+X), looking at the player
    fwd = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), -math.cos(yaw) * math.cos(pitch)])
    upw = np.array([0.0, 1.0, 0.0])
    right = np.cross(fwd, upw)
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    return View("3/4", w, h, "ortho", right, up, fwd, np.array([0.0, cy, -0.2]), scale)


# ----------------------------------------------------------------------------- raster


def _clip_near(cam, near):
    """Sutherland-Hodgman against z >= near in camera space. cam: (3,3) -> list of triangles."""
    inside = cam[:, 2] >= near
    if inside.all():
        return [cam]
    if not inside.any():
        return []
    poly = []
    for i in range(3):
        a, b = cam[i], cam[(i + 1) % 3]
        ia, ib = a[2] >= near, b[2] >= near
        if ia:
            poly.append(a)
        if ia != ib:
            t = (near - a[2]) / (b[2] - a[2])
            poly.append(a + t * (b - a))
    return [np.array([poly[0], poly[i], poly[i + 1]]) for i in range(1, len(poly) - 1)]


class Raster:
    def __init__(self, view: View, ss: int = 2, bg=BG):
        self.v = view
        self.ss = ss
        self.W, self.H = view.w * ss, view.h * ss
        self.col = np.empty((self.H, self.W, 3))
        self.col[:] = bg
        self.z = np.full((self.H, self.W), np.inf)
        self.id = np.zeros((self.H, self.W), dtype=np.int32)
        # camera space (x right, y up, z into the screen): light from upper left, towards the scene
        self.light = np.array([-0.45, 0.6, -0.65])
        self.light /= np.linalg.norm(self.light)

    def _screen(self, cam):
        s = self.v.project_cam(cam)
        s[:, 0] *= self.ss
        s[:, 1] *= self.ss
        return s

    def tri(self, pts, rgb, fid, normal):
        cam = self.v.to_cam(pts)
        n_cam = np.array([normal @ self.v.right, normal @ self.v.up, normal @ self.v.fwd])
        shade = 0.58 + 0.42 * max(0.0, float(n_cam @ self.light))
        colour = np.clip(np.array(rgb) * shade, 0, 1)
        for c in (_clip_near(cam, self.v.near) if self.v.kind == "persp" else [cam]):
            self._fill(self._screen(c), colour, fid)

    def _fill(self, s, colour, fid):
        x0 = max(int(math.floor(s[:, 0].min())), 0)
        x1 = min(int(math.ceil(s[:, 0].max())), self.W - 1)
        y0 = max(int(math.floor(s[:, 1].min())), 0)
        y1 = min(int(math.ceil(s[:, 1].max())), self.H - 1)
        if x1 < x0 or y1 < y0:
            return
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = s
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        xs = np.arange(x0, x1 + 1) + 0.5
        ys = np.arange(y0, y1 + 1) + 0.5
        X, Y = np.meshgrid(xs, ys)
        l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        l3 = 1.0 - l1 - l2
        m = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
        if not m.any():
            return
        if self.v.kind == "persp":  # perspective-correct depth
            zi = 1.0 / (l1 / az + l2 / bz + l3 / cz)
        else:
            zi = l1 * az + l2 * bz + l3 * cz
        zb = self.z[y0:y1 + 1, x0:x1 + 1]
        w = m & (zi < zb)
        zb[w] = zi[w]
        self.col[y0:y1 + 1, x0:x1 + 1][w] = colour
        self.id[y0:y1 + 1, x0:x1 + 1][w] = fid

    def line(self, p0, p1, rgb, width=1.5, depth_test=True, bias=0.02):
        cam = self.v.to_cam(np.array([p0, p1]))
        if self.v.kind == "persp":
            n = self.v.near
            a, b = cam[0].copy(), cam[1].copy()
            if a[2] < n and b[2] < n:
                return
            if a[2] < n:
                a = a + (n - a[2]) / (b[2] - a[2]) * (b - a)
            elif b[2] < n:
                b = b + (n - b[2]) / (a[2] - b[2]) * (a - b)
            cam = np.array([a, b])
        s = self._screen(cam)
        n = int(max(2, np.hypot(*(s[1, :2] - s[0, :2])) * 2))
        t = np.linspace(0, 1, n)[:, None]
        pts = s[0] + t * (s[1] - s[0])
        r = max(0.5, width * self.ss / 2)
        rr = int(math.ceil(r))
        for dx in range(-rr, rr + 1):
            for dy in range(-rr, rr + 1):
                if dx * dx + dy * dy > r * r + 0.25:
                    continue
                xi = (pts[:, 0] + dx).astype(int)
                yi = (pts[:, 1] + dy).astype(int)
                ok = (xi >= 0) & (xi < self.W) & (yi >= 0) & (yi < self.H)
                xi, yi, zz = xi[ok], yi[ok], pts[ok, 2]
                if depth_test:
                    vis = zz - bias <= self.z[yi, xi]
                    xi, yi = xi[vis], yi[vis]
                self.col[yi, xi] = rgb

    def outline(self, rgb=(0.12, 0.12, 0.14)):
        idb = self.id
        edge = np.zeros_like(idb, dtype=bool)
        d1 = idb[:, 1:] != idb[:, :-1]
        d2 = idb[1:, :] != idb[:-1, :]
        # only draw where at least one side is a solid (non-background) pixel
        edge[:, 1:] |= d1 & ((idb[:, 1:] > 0) | (idb[:, :-1] > 0))
        edge[1:, :] |= d2 & ((idb[1:, :] > 0) | (idb[:-1, :] > 0))
        # thin part-internal edges lighter than silhouettes
        part = idb // 16
        sil = np.zeros_like(edge)
        sil[:, 1:] |= (part[:, 1:] != part[:, :-1])
        sil[1:, :] |= (part[1:, :] != part[:-1, :])
        col = self.col
        col[edge & ~sil] = col[edge & ~sil] * 0.72
        col[edge & sil] = rgb

    def image(self):
        img = Image.fromarray((np.clip(self.col, 0, 1) * 255).astype(np.uint8), "RGB")
        if self.ss > 1:
            img = img.resize((self.v.w, self.v.h), Image.LANCZOS)
        return img


def ground(r: Raster, view: View):
    """Ground plane y=0: a grid on top views, a line elsewhere."""
    g = (0.72, 0.72, 0.68)
    if view.name == "top" or view.kind == "persp" or view.name == "3/4":
        for k in np.arange(-3, 3.01, 0.5):
            r.line((k, 0, -3), (k, 0, 3), g, 1.0, depth_test=True, bias=0.0)
            r.line((-3, 0, k), (3, 0, k), g, 1.0, depth_test=True, bias=0.0)
    else:
        a = view.centre - view.right * 5
        b = view.centre + view.right * 5
        a[1] = b[1] = 0.0
        r.line(a, b, (0.45, 0.45, 0.42), 1.2, depth_test=False)


def render(scene: Scene, view: View, *, trails=(), marks=(), ss=2, bg=BG, grid=True) -> Image.Image:
    """trails: list of (points (n,3), rgb, width); marks: list of (point, rgb, radius_px)."""
    r = Raster(view, ss=ss, bg=bg)
    if grid:
        ground(r, view)
        r.z[:] = np.inf  # ground never hides anything
    for pts, rgb, fid, n in scene.tris:
        r.tri(pts, rgb, fid, n)
    r.outline()
    for p0, p1, rgb, wdt, dt in scene.lines:
        r.line(p0, p1, rgb, wdt, dt)
    for pts, rgb, wdt in trails:
        for a, b in zip(pts[:-1], pts[1:]):
            r.line(a, b, rgb, wdt, depth_test=False)
    for p, rgb, rad in marks:
        r.line(p, p, rgb, rad, depth_test=False)
    return r.image()


def label(img: Image.Image, text, xy=(3, 2), rgb=(20, 20, 20), font=None):
    d = ImageDraw.Draw(img)
    d.text(xy, text, fill=rgb, font=font or FONT)
    return img
