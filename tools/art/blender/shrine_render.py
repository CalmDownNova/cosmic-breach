"""Textured renders of the generated shrines (Aetheria 1.1, task B5): the geo, texture and glowmask gen_shrines.py
wrote, placed with GeckoLib's maths, rendered with EEVEE on a 3 by 3 floor of grey stone: front three quarter, side
and back three quarter in daylight, the front at dusk (where the glow carries), and from 24 blocks off in daylight.
The sun comes from the front right and above, so the faces a player walks up to are the lit ones.

  blender.exe --background --factory-startup --python tools/art/blender/shrine_render.py -- --kinds colossus --out <dir>

With --names (one per kind) the files carry those names instead of the kinds (a blind round; preview_shrines.py
--deal picks them).
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
import shrine_common as sc  # noqa: E402

TEX = 128


def image_node(nt, path):
    node = nt.nodes.new("ShaderNodeTexImage")
    node.image = bpy.data.images.load(str(path))
    node.interpolation = "Closest"
    return node


def material(kind):
    mat = bpy.data.materials.new(f"shrine_{kind}")
    nt = sc.node_tree(mat)
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    base = image_node(nt, sc.ASSETS / "textures" / "block" / f"shrine_{kind}.png")
    glow = image_node(nt, sc.ASSETS / "textures" / "block" / f"shrine_{kind}_glowmask.png")
    nt.links.new(base.outputs["Color"], bsdf.inputs["Base Color"])
    emission = bsdf.inputs.get("Emission Color")
    if emission is None:
        emission = bsdf.inputs["Emission"]
    nt.links.new(glow.outputs["Color"], emission)
    for name, value in (("Emission Strength", 1.5), ("Roughness", 0.85)):
        if bsdf.inputs.get(name) is not None:
            bsdf.inputs[name].default_value = value
    return mat


def floor(scene):
    me = bpy.data.meshes.new("floor")
    me.from_pydata([(-1.5, -1.5, 0.0), (1.5, -1.5, 0.0), (1.5, 1.5, 0.0), (-1.5, 1.5, 0.0)], [], [(0, 1, 2, 3)])
    mat = bpy.data.materials.new("floor")
    bsdf = next(n for n in sc.node_tree(mat).nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = (*sc.linear("#6f6b66"), 1.0)
    bsdf.inputs["Roughness"].default_value = 0.95  # matte stone: no sun glare across the floor
    me.materials.append(mat)
    obj = bpy.data.objects.new("floor", me)
    scene.collection.objects.link(obj)


def scene_for(kind, dusk):
    sc.clear()
    scene = bpy.context.scene
    sc.use_eevee(scene)
    sc.standard_view(scene)
    scene.world = scene.world or bpy.data.worlds.new("sky")
    bg = next(n for n in sc.node_tree(scene.world).nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (*sc.linear("#1c2340" if dusk else "#a9c8ec"), 1.0)
    bg.inputs["Strength"].default_value = 0.4 if dusk else 1.0
    sun = bpy.data.lights.new("sun", "SUN")
    sun.energy = 0.5 if dusk else 3.0
    sun_obj = bpy.data.objects.new("sun", sun)
    scene.collection.objects.link(sun_obj)
    # tilted 55 degrees from straight down, then turned so the light travels toward -X and -Y: it comes from the front
    # right (+Y is the shrine's front), like the front three quarter camera
    sun_obj.rotation_euler = (math.radians(55.0), 0.0, math.radians(150.0))
    desc, bones, order = sc.bedrock.load_geo(sc.ASSETS / "geo" / "block" / f"shrine_{kind}.geo.json")
    obj = sc.mesh(f"shrine_{kind}", sc.bedrock.world_quads(desc, bones, order, {}), uv_size=(TEX, TEX))
    obj.data.materials.append(material(kind))
    floor(scene)
    return scene


def main():
    a = sc.args()
    kinds = a.get("kinds", " ".join(sc.KINDS)).split()
    names = sc.out_names(a, kinds)
    out = Path(a.get("out", str(sc.MEDIA / "latest")))
    for kind, name in zip(kinds, names):
        day = scene_for(kind, False)
        sc.render(day, sc.camera("front", (3.2, 6.4, 2.6), sc.TARGET), out / f"{name}_front_day.png", (640, 640))
        sc.render(day, sc.camera("side", (7.0, 0.0, 1.8), sc.TARGET), out / f"{name}_side_day.png", (640, 640))
        sc.render(day, sc.camera("back", (-3.2, -6.4, 2.6), sc.TARGET), out / f"{name}_back_day.png", (640, 640))
        sc.render(day, sc.camera("far", sc.FAR_EYE, (0.0, 0.0, 1.0)), out / f"{name}_far_day.png", (160, 160))
        dusk = scene_for(kind, True)
        sc.render(dusk, sc.camera("front", (3.2, 6.4, 2.6), sc.TARGET), out / f"{name}_front_dusk.png", (640, 640))
        print(f"rendered shrine_{kind} into {out}")


main()
