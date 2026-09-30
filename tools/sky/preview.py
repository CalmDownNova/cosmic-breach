"""Renders Aetheria's sky offline with the game's own shaders and numbers, for tuning without the game.

    python tools/sky/preview.py                      # the standard sheet: every layer at noon, sunset, night
    python tools/sky/preview.py reach:6000 deep:18000 --views west,up

Needs moderngl, numpy and Pillow, and the compiled classes (./gradlew testClasses): the per-frame numbers
come from SkyModelDump (src/test, Java), so the preview shows exactly what the game computes.
The shaders are read from assets/cosmicbreach/shaders (with #moj_import resolved), the textures from
textures/sky. Draw order and blending match AetheriaSkyRenderer. Writes tools/sky/previews/sky_sheet.jpg.
"""
from __future__ import annotations

import json
import re
import struct
import subprocess
import sys
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
ASSETS = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach"
SHADERS = ASSETS / "shaders"
TEXTURES = ASSETS / "textures" / "sky"
BUILD = HERE / "build"
PREVIEWS = HERE / "previews"
JAVA = Path("C:/Program Files/Java/jdk-21/bin/java.exe")
W, H = 640, 360
FOV = 70.0

VIEWS = {
    "west": (90.0, -8.0),     # Thalassa on the horizon, the sunset
    "up": (0.0, -62.0),       # the high sky, south
    "north": (180.0, -28.0),  # Vesper, the clusters, the aurora
    "down": (60.0, 55.0),     # between the layers
}


def dump(frames: list[str]) -> list[dict]:
    classes = REPO / "build" / "classes" / "java"
    cp = f"{classes / 'main'};{classes / 'test'}"
    out = subprocess.run([str(JAVA), "-cp", cp, "com.cosmicbreach.client.sky.SkyModelDump",
                          "--geometry", str(BUILD), *frames], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def source(path: Path) -> str:
    text = path.read_text(encoding="utf-8")

    def include(m):
        ns, name = m.group(1), m.group(2)
        inc = (REPO / "src" / "main" / "resources" / "assets" / ns / "shaders" / "include" / name).read_text(encoding="utf-8")
        return re.sub(r"^#version.*$", "", inc, flags=re.M)

    return re.sub(r"#moj_import <(\w+):([\w./]+)>", include, text)


def program(ctx, name: str):
    return ctx.program(vertex_shader=source(SHADERS / "core" / f"{name}.vsh"),
                       fragment_shader=source(SHADERS / "core" / f"{name}.fsh"))


def texture(ctx, name: str, repeat: bool):
    img = np.array(Image.open(TEXTURES / name).convert("RGBA"))
    tex = ctx.texture((img.shape[1], img.shape[0]), 4, img.tobytes())
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    tex.repeat_x = repeat
    tex.repeat_y = repeat
    return tex


def mesh(ctx, prog, file: Path):
    raw = file.read_bytes()
    quads = struct.unpack_from("<i", raw, 0)[0]
    data = np.frombuffer(raw, dtype=np.uint8, offset=4).reshape(quads * 4, 24)
    pos_uv = data[:, :20].copy().view(np.float32).reshape(-1, 5)
    argb = data[:, 20:24].copy().view(np.uint32).reshape(-1)
    col = np.stack([(argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255], -1).astype(np.float32) / 255.0
    return quads_vao(ctx, prog, pos_uv, col)


def quads_vao(ctx, prog, pos_uv: np.ndarray, col: np.ndarray | None):
    n = pos_uv.shape[0] // 4
    idx = np.array([[4 * i, 4 * i + 1, 4 * i + 2, 4 * i, 4 * i + 2, 4 * i + 3] for i in range(n)], dtype=np.int32).reshape(-1)
    content = [(ctx.buffer(pos_uv[:, :3].astype("f4").tobytes()), "3f", "Position")]
    if "UV0" in prog:
        content.append((ctx.buffer(pos_uv[:, 3:5].astype("f4").tobytes()), "2f", "UV0"))
    if col is not None and "Color" in prog:
        content.append((ctx.buffer(col.astype("f4").tobytes()), "4f", "Color"))
    return ctx.vertex_array(prog, content, ctx.buffer(idx.tobytes()))


def cube(ctx, prog):
    s = 50.0
    faces = []
    for axis in range(3):
        for sign in (-1, 1):
            u, v = [a for a in range(3) if a != axis]
            quad = []
            for cu, cv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                p = [0.0, 0.0, 0.0]
                p[axis] = sign * s
                p[u] = cu * s
                p[v] = cv * s
                quad.append(p + [0.0, 0.0])
            faces.extend(quad)
    return quads_vao(ctx, prog, np.array(faces, dtype=np.float32), None)


def view_matrix(yaw: float, pitch: float) -> np.ndarray:
    y, p = np.radians(yaw), np.radians(pitch)
    f = np.array([-np.sin(y) * np.cos(p), -np.sin(p), np.cos(y) * np.cos(p)])
    r = np.cross(f, [0.0, 1.0, 0.0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = r, u, -f
    return m


def projection() -> np.ndarray:
    t = 1.0 / np.tan(np.radians(FOV) / 2)
    near, far = 0.05, 768.0
    m = np.zeros((4, 4))
    m[0, 0] = t / (W / H)
    m[1, 1] = t
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = 2 * far * near / (near - far)
    m[3, 2] = -1
    return m


def setu(prog, name, value):
    if name in prog:
        if isinstance(value, np.ndarray) and value.shape == (4, 4):
            prog[name].write(value.T.astype("f4").tobytes())   # column-major
        else:
            prog[name].value = value


def rot4(rows9) -> np.ndarray:
    m = np.eye(4)
    m[:3, :3] = np.array(rows9).reshape(3, 3)
    return m


def common(prog, f, mv, proj):
    setu(prog, "ModelViewMat", mv)
    setu(prog, "ProjMat", proj)
    setu(prog, "ColorModulator", (1.0, 1.0, 1.0, 1.0))
    for key, name in (("zenith", "ZenithColor"), ("mid", "MidColor"), ("haze", "HazeColor"), ("horizon", "HorizonColor"),
                      ("low", "LowColor"), ("nadir", "NadirColor"), ("sunDir", "SunDir")):
        setu(prog, name, tuple(f[key]))
    setu(prog, "SunGlow", tuple(f["glow"]))


class Renderer:
    def __init__(self):
        self.ctx = moderngl.create_standalone_context(require=330)
        ctx = self.ctx
        self.fbo = ctx.simple_framebuffer((W, H), components=4)
        self.p_dome = program(ctx, "sky_dome")
        self.p_stars = program(ctx, "sky_stars")
        self.p_glow = program(ctx, "sky_glow")
        self.p_body = program(ctx, "sky_body")
        self.p_aurora = program(ctx, "sky_aurora")
        self.dome = cube(ctx, self.p_dome)
        self.stars = mesh(ctx, self.p_stars, BUILD / "stars.bin")
        self.clusters = mesh(ctx, self.p_stars, BUILD / "clusters.bin")
        self.aurora = mesh(ctx, self.p_aurora, BUILD / "aurora.bin")
        self.nebula = [texture(ctx, f"nebula_{n}.png", False) for n in ("reach", "drift", "deep")]
        self.bands = texture(ctx, "thalassa_bands.png", True)
        self.ring = texture(ctx, "thalassa_ring.png", False)
        self.aurora_tex = texture(ctx, "aurora.png", True)

    def render(self, f: dict, yaw: float, pitch: float) -> np.ndarray:
        ctx = self.ctx
        self.fbo.use()
        self.fbo.clear(0, 0, 0, 1)
        ctx.disable(moderngl.DEPTH_TEST | moderngl.CULL_FACE)
        mv = view_matrix(yaw, pitch)
        proj = projection()

        # 1. dome and nebula (opaque)
        ctx.disable(moderngl.BLEND)
        p = self.p_dome
        common(p, f, mv, proj)
        setu(p, "CelestialMat", rot4(f["skyRot"]).T)  # world to celestial: the inverse (transpose)
        setu(p, "NebulaParams", (f["nebulaMix"], f["nebula"][0], f["nebula"][1], f["nebula"][2]))
        setu(p, "NebulaVeil", f["nebula"][3])
        self.nebula[f["nebulaA"]].use(0)
        self.nebula[f["nebulaB"]].use(1)
        setu(p, "Sampler0", 0)
        setu(p, "Sampler1", 1)
        self.dome.render()

        # 2. stars and clusters (additive)
        ctx.enable(moderngl.BLEND)
        ctx.blend_func = moderngl.ONE, moderngl.ONE
        p = self.p_stars
        common(p, f, mv, proj)
        setu(p, "SkyRot", rot4(f["skyRot"]))
        setu(p, "StarParams", (f["stars"][0], f["stars"][1], f["seconds"], f["stars"][2]))
        self.stars.render()
        self.clusters.render()

        # 3. Vesper's beams and star (additive)
        p = self.p_glow
        common(p, f, mv, proj)
        for key in ("beam0", "beam1"):
            q = np.array(f[key], dtype=np.float32).reshape(-1, 5)
            col = np.tile([0.55, 0.82, 1.0, 1.0], (q.shape[0], 1)).astype(np.float32)
            setu(p, "GlowMode", 2)
            setu(p, "GlowParams", (0.0, 0.0, 0.0, f["vesperBeam"]))
            quads_vao(ctx, p, q, col).render()
        q = np.array(f["vesperQuad"], dtype=np.float32).reshape(-1, 5)
        col = np.tile([0.78, 0.9, 1.0, 1.0], (4, 1)).astype(np.float32)
        setu(p, "GlowMode", 1)
        setu(p, "GlowParams", (0.07, 0.55, f["beamAngle"], f["vesperCore"]))
        quads_vao(ctx, p, q, col).render()

        # 4. Solenne (additive)
        q = np.array(f["sunQuad"], dtype=np.float32).reshape(-1, 5)
        col = np.tile([1.0, 1.0, 1.0, 1.0], (4, 1)).astype(np.float32)
        setu(p, "GlowMode", 0)
        setu(p, "GlowParams", (f["sunDisc"], f["coronaStrength"], f["seconds"] * 0.05, f["discStrength"] * f["sunVisible"]))
        setu(p, "GlowColor2", (1.0, 0.86, 0.6))
        quads_vao(ctx, p, q, col).render()

        # 5. Thalassa (premultiplied)
        ctx.blend_func = moderngl.ONE, moderngl.ONE_MINUS_SRC_ALPHA
        p = self.p_body
        common(p, f, mv, proj)
        d = np.array(f["planetDir"])
        setu(p, "PlanetPos", tuple(d * 80.0))
        setu(p, "PlanetGeom", (f["planetR"], 1.35, 2.25, f["bandDrift"]))
        setu(p, "RingAxis", tuple(f["ringAxis"]))
        setu(p, "LightDir", tuple(f["planetLightDir"]))
        setu(p, "PlanetLight", tuple(f["planetLight"]))
        setu(p, "PlanetParams", tuple(f["planetParams"]))
        setu(p, "PlanetAmbient", tuple(f["planetAmbient"]))
        self.bands.use(0)
        self.ring.use(1)
        setu(p, "Sampler0", 0)
        setu(p, "Sampler1", 1)
        q = np.array(f["planetQuad"], dtype=np.float32).reshape(-1, 5)
        quads_vao(ctx, p, q, None).render()

        # 6. aurora (additive)
        if f["aurora"] > 0.001:
            ctx.blend_func = moderngl.ONE, moderngl.ONE
            p = self.p_aurora
            common(p, f, mv, proj)
            setu(p, "AuroraParams", (f["seconds"], f["aurora"], 0.0, 0.0))
            self.aurora_tex.use(0)
            setu(p, "Sampler0", 0)
            self.aurora.render()

        img = np.frombuffer(self.fbo.read(components=3), dtype=np.uint8).reshape(H, W, 3)
        return img[::-1]


def main(argv: list[str]) -> int:
    views = list(VIEWS)
    if "--views" in argv:
        i = argv.index("--views")
        views = argv[i + 1].split(",")
        argv = argv[:i] + argv[i + 2:]
    out_name = "sky_sheet.jpg"
    if "--out" in argv:
        i = argv.index("--out")
        out_name = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
    frames = argv or ["reach:6000", "reach:11600", "reach:12600", "reach:18000",
                      "drift:6000", "drift:18000", "deep:6000", "deep:18000"]
    data = dump(frames)
    r = Renderer()
    rows = []
    for f in data:
        tiles = []
        for v in views:
            img = Image.fromarray(r.render(f, *VIEWS[v]))
            ImageDraw.Draw(img).text((6, 4), f"{f['label']} {v}", fill=(255, 255, 255))
            tiles.append(np.array(img))
        rows.append(np.concatenate(tiles, axis=1))
    sheet = np.concatenate(rows, axis=0)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    Image.fromarray(sheet).save(PREVIEWS / out_name, quality=88)
    print(PREVIEWS / out_name, sheet.shape)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
