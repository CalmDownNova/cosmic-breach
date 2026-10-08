"""Shared pieces for the shrine Blender scripts (Aetheria 1.1, task B5).

Runs inside Blender 5.2 (bpy, bmesh, mathutils and numpy; no Pillow), headless only:
  "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python <script> -- <args>

Geometry always goes through tools/art/bedrock_model.py, GeckoLib 4.9.3's own maths (x mirrored on load, rotations Z
then Y then X), so Blender shows exactly what the game draws. Game model space (y up, the front toward -Z, 16 units a
block) maps into Blender's Z-up space as (x, y, z) -> (x, -z, y) / 16: a rotation, so nothing is mirrored, and the
shrine's front faces +Y in Blender. Set explicitly because Blender's defaults break this kind of render: the Standard
view transform, colours converted from sRGB to linear, the EEVEE id found by trying both names, Closest filtering.
"""
from __future__ import annotations

import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector

ART = Path(__file__).resolve().parents[1]
if str(ART) not in sys.path:
    sys.path.insert(0, str(ART))
import bedrock_model as bedrock  # noqa: E402  (numpy only)

REPO = ART.parents[1]
SHAPES = ART / "shrines"
ASSETS = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach"
MEDIA = Path("C:/Users/puppy/Media/Aetheria/Shrines")
KINDS = ["colossus", "leviathan", "unsung", "heliarch"]
TARGET = (0.0, 0.0, 1.3)
# orthographic views: eye (Blender space, blocks) and ortho scale; the front is +Y
VIEWS = {
    "front": ((0.0, 9.0, 1.4), 3.6),
    "side": ((9.0, 0.0, 1.4), 3.6),
    "back": ((0.0, -9.0, 1.4), 3.6),
    "three_quarter": ((6.4, 6.4, 3.4), 3.8),
    "top": ((0.0, 0.01, 9.0), 3.6),
}
# a player 24 blocks in front, eyes 1.62 above the floor
FAR_EYE = (0.0, 24.0, 1.62)


def args() -> dict:
    """Arguments after Blender's own '--', as {name: value} for --name value (values may hold spaces)."""
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out, key = {}, None
    for a in argv:
        if a.startswith("--"):
            key = a[2:]
            out[key] = ""
        elif key is not None:
            out[key] = (out[key] + " " + a).strip()
    return out


def out_names(a, kinds):
    """The names the render files carry: --names (one per kind, in order: a blind round's neutral names, so no file
    names its boss), or else the kinds themselves."""
    names = a.get("names", "").split()
    if not names:
        return list(kinds)
    if len(names) != len(kinds):
        raise SystemExit(f"--names gives {len(names)} names for {len(kinds)} kinds")
    return names


def to_blender(v):
    return (float(v[0]) / 16.0, -float(v[2]) / 16.0, float(v[1]) / 16.0)


def linear(hex_colour: str):
    h = hex_colour.lstrip("#")
    out = []
    for i in (0, 2, 4):
        c = int(h[i:i + 2], 16) / 255.0
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return tuple(out)


def clear():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights, bpy.data.images):
        for item in list(block):
            block.remove(item)


def quads(shape, pose=None):
    """Every face of every cube of a shape (the cubes.json form), at rest or in `pose` ({bone: (rotation offset,
    position, scale)}, bedrock_model's form), in game model space."""
    bones, order = {}, []
    for b in shape["bones"]:
        bones[b["name"]] = dict(b, cubes=[dict(c, uv=c.get("uv", [0, 0])) for c in b["cubes"]])
        order.append(b["name"])
    return bedrock.world_quads({}, bones, order, pose or {})


def mesh(name, faces, uv_size=None):
    """A mesh object from GeckoLib quads; uv_size (w, h) maps their texel UVs onto a texture of that size."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap") if uv_size else None
    for q in faces:
        f = bm.faces.new([bm.verts.new(to_blender(v)) for v in q["verts"]])
        if uv_layer is not None:
            for loop, uv in zip(f.loops, q["uvs"]):
                loop[uv_layer].uv = (float(uv[0]) / uv_size[0], 1.0 - float(uv[1]) / uv_size[1])
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def camera(name, eye, target, ortho=None, lens=50.0):
    data = bpy.data.cameras.new(name)
    if ortho:
        data.type = "ORTHO"
        data.ortho_scale = ortho
    else:
        data.lens = lens
    data.clip_end = 500.0
    obj = bpy.data.objects.new(name, data)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = Vector(eye)
    obj.rotation_euler = (Vector(target) - Vector(eye)).to_track_quat("-Z", "Y").to_euler()
    return obj


def render(scene, cam, path, size):
    scene.camera = cam
    scene.render.resolution_x, scene.render.resolution_y = size
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def standard_view(scene):
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"


def node_tree(idblock):
    if getattr(idblock, "node_tree", None) is None and hasattr(idblock, "use_nodes"):
        idblock.use_nodes = True
    return idblock.node_tree


def use_eevee(scene):
    for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scene.render.engine = engine
            return engine
        except TypeError:
            continue
    raise SystemExit("this Blender has no EEVEE engine")
