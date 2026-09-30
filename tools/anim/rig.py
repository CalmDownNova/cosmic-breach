"""Forward kinematics of the vanilla 1.21.1 player model (Steve, wide arms) as the
game renders it with playerAnimator active, plus the held Meridian item.

Every step mirrors a pose-stack call in the real code, in the same order:

  LivingEntityRenderer.render      scale(entityScale=1), setupRotations, scale(-1,-1,1),
                                   PlayerRenderer.scale 0.9375, translate(0,-1.501,0)
  playerAnimator PlayerRendererMixin (at the end of setupRotations, i.e. before the flip):
                                   scale(body.scale); translate(body.pos + (0,0.7,0));
                                   rotZ(body.roll) rotY(body.yaw) rotX(body.pitch); translate(0,-0.7,0)
  ModelPart.translateAndRotate      translate(x/16,y/16,z/16); rotationZYX(zRot,yRot,xRot); scale
  ItemInHandLayer.renderArmWithItem translateToHand (the arm's translateAndRotate); rotX(-90); rotY(180);
                                   translate(+-1/16, 0.125, -0.625)
  playerAnimator HeldItemMixin      scale(item.scale); translate(item.pos/16); rotZ; rotY; rotX
  ItemTransform.apply (item/handheld thirdperson_righthand)
                                   translate(0,4,0.5)/16; rotationXYZ(0,-90,55); scale 0.85
  ItemRenderer.render               translate(-0.5,-0.5,-0.5), then the generated item model:
                                   the 32x32 sprite spread over x,y in [0,1], z in [7.5,8.5]/16

Frame used everywhere below ("local"): origin at the feet, +Y up, the player faces -Z,
the player's right is +X, units are blocks. That is the pose stack right after
setupRotations' yaw, before playerAnimator's body transform.

Model space (inside the flip): +Y down, the face is on -Z, the player's right is -X,
units are pixels (1/16 block). ModelPart pivots and Euler angles live there.
"""

from __future__ import annotations

import math
import os
from dataclasses import dataclass

import numpy as np

from palib import POSITION, ROTATION, SCALE, clamp_to_radian

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
ITEM_TEX = os.path.join(REPO, "src", "main", "resources", "assets", "cosmicbreach", "textures", "item")
SPRITE = os.path.join(ITEM_TEX, "meridian.png")

EYE_HEIGHT = 1.62  # Player standing eye height, blocks
PLAYER_SCALE = 0.9375
MODEL_DROP = 1.501

# ----------------------------------------------------------------------------- matrices (column vectors, right-handed)


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1.0]])


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1.0]])


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])


def rot_zyx(z, y, x):
    """Quaternionf().rotationZYX(z, y, x) = Rz * Ry * Rx (the vector sees X first)."""
    return Rz(z) @ Ry(y) @ Rx(x)


def rot_xyz(x, y, z):
    """Quaternionf().rotationXYZ(x, y, z) = Rx * Ry * Rz."""
    return Rx(x) @ Ry(y) @ Rz(z)


D = math.radians

# ----------------------------------------------------------------------------- model geometry (PlayerModel.createMesh, wide arms)

# pivot (model px) and box (min corner, size) in model px, as in HumanoidModel/PlayerModel.createMesh
BOXES = {
    "head": ((-4, -8, -4), (8, 8, 8)),
    "torso": ((-4, 0, -2), (8, 12, 4)),
    "rightArm": ((-3, -2, -2), (4, 12, 4)),
    "leftArm": ((-1, -2, -2), (4, 12, 4)),
    "rightLeg": ((-2, 0, -2), (4, 12, 4)),
    "leftLeg": ((-2, 0, -2), (4, 12, 4)),
}
MODEL_PART_OF = {"head": "head", "torso": "body", "rightArm": "right_arm", "leftArm": "left_arm",
                 "rightLeg": "right_leg", "leftLeg": "left_leg"}

# Fist centres in each arm's own frame (px): the lower part of the 4x12x4 arm box.
FIST = {"rightArm": np.array([-1.0, 8.0, 0.0]), "leftArm": np.array([1.0, 8.0, 0.0])}


def vanilla_pose():
    """What HumanoidModel/PlayerModel.setupAnim leaves in the parts before playerAnimator overrides
    them, for a player standing still, looking level, main hand holding an item (ArmPose.ITEM),
    offhand empty. These are the value0 arguments: every channel an animation does not key keeps
    them. Arm bob (bobModelPart) is taken at its mean, zRot +-0.05."""
    return {
        "head": ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0)),
        "torso": ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0)),
        "rightArm": ((-5.0, 2.0, 0.0), (-math.pi / 10, 0.0, 0.05)),
        "leftArm": ((5.0, 2.0, 0.0), (0.0, 0.0, -0.05)),
        "rightLeg": ((-1.9, 12.0, 0.0), (0.0, 0.005, 0.005)),
        "leftLeg": ((1.9, 12.0, 0.0), (0.0, -0.005, -0.005)),
    }


# ----------------------------------------------------------------------------- the held item


@dataclass
class ItemGeom:
    boxes: list  # (min xyz, max xyz, rgb) in item model space (blocks, 0..1)
    pommel: np.ndarray  # key points in item model space
    grip_r: np.ndarray  # where the right fist sits (upper grip, by the guard)
    grip_l: np.ndarray  # where the left fist sits (lower grip, by the pommel)
    guard: np.ndarray
    tip: np.ndarray


def _texel(tx, ty):
    """Texel centre (x right, y down in the png) to item model space (blocks)."""
    return np.array([(tx + 0.5) / 32.0, 1.0 - (ty + 0.5) / 32.0, 0.5])


# The weapons the animations are made for. Each: its sprite, key points on it (texel x right, y down),
# and the third-person display transform of its item model (translation in blocks, scale). Meridian's is
# item/handheld's, which the animations were solved with. The Comet Maul (models/item/comet_maul_base.json)
# is 1.25x, moved so its grip (the same texels as Meridian's) lands exactly where Meridian's does: every
# rightItem value holds either weapon by the same point, so the shared animations (dash, parry, stagger)
# hold the Maul too. "guard" and "tip" are its head's near and far sides along the haft.
WEAPONS = {
    "meridian": dict(sprite=SPRITE, pommel=(3, 28), grip_r=(8.2, 22.8), grip_l=(5.6, 25.4), guard=(11, 20),
                     tip=(31.2, 0.8), translation=(0.0, 4.0 / 16.0, 0.5 / 16.0), scale=0.85),
    "comet_maul": dict(sprite=os.path.join(ITEM_TEX, "comet_maul.png"), pommel=(3.5, 27.5), grip_r=(8.2, 22.8),
                       grip_l=(5.6, 25.4), guard=(20.0, 11.0), tip=(26.0, 5.0),
                       translation=(0.0, 6.03 / 16.0, 0.14 / 16.0), scale=1.25),
    # The Binary Edges: a sickle in each hand (dual). Each is held by the same grip texel as Meridian and its
    # display (models/item/binary_edges_sickle.json, 0.8x) is moved so that grip lands where Meridian's does,
    # so the solver's hand maths holds either. "guard" is the ferrule and "tip" the crescent's apex (what leads a
    # hook); "point" the crescent's point. The left hand's item is drawn with the display vanilla gives the
    # left hand (ItemTransform.apply(leftHand): the model's thirdperson_lefthand turned back), here the same.
    "binary_edges": dict(sprite=os.path.join(ITEM_TEX, "binary_edges_right.png"),
                         sprite_left=os.path.join(ITEM_TEX, "binary_edges_left.png"), pommel=(3.0, 28.0),
                         grip_r=(8.2, 22.8), grip_l=(8.2, 22.8), guard=(12.9, 18.1), tip=(22.5, 6.5),
                         point=(28.7, 15.5), translation=(0.0, 3.75 / 16.0, 0.54 / 16.0), scale=0.8, dual=True),
    # Last Light (G9b), a glaive: Meridian's grip texels, a 1.5x display moved so the grip lands where Meridian's does
    # (tools/art/gen_last_light.py computes it). "guard" is where the blade is seated, "tip" its point.
    "last_light": dict(sprite=os.path.join(ITEM_TEX, "last_light.png"), pommel=(1.5, 29.5), grip_r=(8.2, 22.8),
                       grip_l=(5.6, 25.4), guard=(19.0, 12.0), tip=(30.5, 0.5),
                       translation=(0.0, 7.344 / 16.0, 0.144 / 16.0), scale=1.5),
    # The Umbra Cantor (G9b), a bow held one-handed by the apex of its arc (the grip, tools/art/gen_umbra_cantor.py),
    # its 1.1x display moved so that grip lands where Meridian's does. The sprite's diagonal ("sword") runs along the
    # string to the upper tip; its "edge" side (the solver's E) is the way the arrow flies. "tip" is the upper tip.
    "umbra_cantor": dict(sprite=os.path.join(ITEM_TEX, "umbra_cantor_d2.png"), pommel=(3.5, 28.5),
                         grip_r=(9.92, 9.92), grip_l=(9.92, 9.92), guard=(15.5, 15.5), tip=(28.5, 3.5),
                         translation=(0.0, 0.448 / 16.0, 5.624 / 16.0), scale=1.1),
}
_weapon = "meridian"


def use_weapon(name):
    """Every later load_item() and pose_from() draws this weapon."""
    global _weapon
    if name not in WEAPONS:
        raise ValueError(f"unknown weapon {name!r}")
    _weapon = name


def weapon_for(anim_name):
    """The weapon an animation is made for: the Comet Maul's are named maul_*, the Binary Edges' edges_*."""
    if anim_name.startswith("maul_"):
        return "comet_maul"
    if anim_name.startswith("edges_"):
        return "binary_edges"
    if anim_name.startswith("lastlight_"):
        return "last_light"
    if anim_name.startswith("cantor_"):
        return "umbra_cantor"
    return "meridian"


def is_dual(name=None):
    """True if the weapon (this one, or the current) holds a blade in each hand."""
    return bool(WEAPONS[name or _weapon].get("dual"))


def current_weapon():
    return _weapon


def load_item(path=None, left=False) -> ItemGeom:
    from PIL import Image

    weapon = WEAPONS[_weapon]
    im = Image.open(path or weapon["sprite_left" if left and "sprite_left" in weapon else "sprite"]).convert("RGBA")
    w, h = im.size
    px = np.array(im)
    boxes = []
    z0, z1 = 7.5 / 16.0, 8.5 / 16.0
    for ty in range(h):
        tx = 0
        while tx < w:
            if px[ty, tx, 3] == 0:
                tx += 1
                continue
            start = tx
            cols = []
            while tx < w and px[ty, tx, 3] > 0:
                cols.append(px[ty, tx, :3])
                tx += 1
            rgb = np.mean(cols, axis=0) / 255.0
            boxes.append((np.array([start / w, 1.0 - (ty + 1) / h, z0]),
                          np.array([tx / w, 1.0 - ty / h, z1]), rgb))
    # Key points read off the 32x32 sprite (see previews/meridian_big.png): the teal pommel gem,
    # the wrapped grip, the star guard and the blade tip, all on the sprite diagonal.
    return ItemGeom(boxes=boxes, pommel=_texel(*weapon["pommel"]), grip_r=_texel(*weapon["grip_r"]),
                    grip_l=_texel(*weapon["grip_l"]), guard=_texel(*weapon["guard"]), tip=_texel(*weapon["tip"]))


# ----------------------------------------------------------------------------- posing


@dataclass
class Posed:
    """World (local frame) matrices of everything, plus the channel values that produced them."""
    body: np.ndarray  # playerAnimator body transform
    model: np.ndarray  # model space -> local (px scaled to blocks is inside, see part())
    parts: dict  # name -> 4x4, maps part-local px/16 to local blocks
    item: np.ndarray  # item model space (0..1 blocks) -> local, right hand
    values: dict  # name -> (pos, rot) actually used
    item_left: np.ndarray = None  # the left hand's item (a dual weapon), else None


def pose_from(source, vanilla=None, item_in_hand=True) -> Posed:
    """source.get(part, kind, value0) -> tuple, like IAnimation.get3DTransform. source may be None
    (plain vanilla pose)."""
    v0 = vanilla or vanilla_pose()

    def get(part, kind, value0):
        if source is None:
            return tuple(value0)
        return source.get(part, kind, value0)

    bs = get("body", SCALE, (1.0, 1.0, 1.0))
    bp = get("body", POSITION, (0.0, 0.0, 0.0))
    br = get("body", ROTATION, (0.0, 0.0, 0.0))
    body = S(*bs) @ T(bp[0], bp[1] + 0.7, bp[2]) @ Rz(br[2]) @ Ry(br[1]) @ Rx(br[0]) @ T(0, -0.7, 0)
    model = body @ S(-1, -1, 1) @ S(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE) @ T(0, -MODEL_DROP, 0)
    parts, values = {}, {"body": (bp, br)}
    for name, (p0, r0) in v0.items():
        pos = get(name, POSITION, p0)
        rot = get(name, ROTATION, tuple(clamp_to_radian(a) for a in r0))
        sc = get(name, SCALE, (1.0, 1.0, 1.0))
        parts[name] = model @ T(pos[0] / 16, pos[1] / 16, pos[2] / 16) @ rot_zyx(rot[2], rot[1], rot[0]) @ S(*sc)
        values[name] = (pos, rot)
    item = None
    if item_in_hand:
        isc = get("rightItem", SCALE, (1.0, 1.0, 1.0))
        irot = get("rightItem", ROTATION, (0.0, 0.0, 0.0))
        ipos = [c * 0.0625 for c in get("rightItem", POSITION, (0.0, 0.0, 0.0))]
        values["rightItem"] = (tuple(c * 16 for c in ipos), irot)
        hand = parts["rightArm"] @ Rx(D(-90)) @ Ry(D(180)) @ T(1 / 16, 0.125, -0.625)
        held = hand @ S(*isc) @ T(*ipos) @ Rz(irot[2]) @ Ry(irot[1]) @ Rx(irot[0])
        w = WEAPONS[_weapon]
        k = w["scale"]
        display = T(*w["translation"]) @ rot_xyz(D(0), D(-90), D(55)) @ S(k, k, k)
        item = held @ display @ T(-0.5, -0.5, -0.5)
    item_left = None
    if item_in_hand and WEAPONS[_weapon].get("dual"):
        # ItemInHandLayer for the left arm: translate(-1/16, ...), then the leftItem channel, then the display
        # as ItemTransform.apply(leftHand) turns it (rotation y and z negated back, translation x negated)
        isc = get("leftItem", SCALE, (1.0, 1.0, 1.0))
        irot = get("leftItem", ROTATION, (0.0, 0.0, 0.0))
        ipos = [c * 0.0625 for c in get("leftItem", POSITION, (0.0, 0.0, 0.0))]
        values["leftItem"] = (tuple(c * 16 for c in ipos), irot)
        hand = parts["leftArm"] @ Rx(D(-90)) @ Ry(D(180)) @ T(-1 / 16, 0.125, -0.625)
        held = hand @ S(*isc) @ T(*ipos) @ Rz(irot[2]) @ Ry(irot[1]) @ Rx(irot[0])
        w = WEAPONS[_weapon]
        k = w["scale"]
        tx, ty, tz = w["translation"]
        display = T(-tx, ty, tz) @ rot_xyz(D(0), D(-90), D(55)) @ S(k, k, k)
        item_left = held @ display @ T(-0.5, -0.5, -0.5)
    return Posed(body=body, model=model, parts=parts, item=item, values=values, item_left=item_left)


def xf(m, p):
    """Transform a point (3,) or points (n,3)."""
    p = np.asarray(p, dtype=float)
    if p.ndim == 1:
        return (m @ np.append(p, 1.0))[:3]
    return (np.c_[p, np.ones(len(p))] @ m.T)[:, :3]


def part_point(posed: Posed, part: str, px):
    """A point given in a part's own frame (model px) to local blocks."""
    return xf(posed.parts[part], np.asarray(px, dtype=float) / 16.0)


def box_corners(part):
    (x0, y0, z0), (w, h, d) = BOXES[part]
    xs, ys, zs = (x0, x0 + w), (y0, y0 + h), (z0, z0 + d)
    return np.array([[x, y, z] for x in xs for y in ys for z in zs], dtype=float) / 16.0


def item_points(posed: Posed, geom: ItemGeom, left=False):
    """Key points of the held sword (the left hand's blade if asked) in local blocks."""
    m = posed.item_left if left else posed.item
    return {k: xf(m, getattr(geom, k)) for k in ("pommel", "grip_r", "grip_l", "guard", "tip")}
