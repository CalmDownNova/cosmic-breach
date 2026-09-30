"""Pose solver: turns a readable pose description into playerAnimator channel values.

A pose ("spec") says what an animator would say:

  lean, twist, tilt   whole body, degrees: forward lean +, twist to the right +, tilt to the right +
  fwd, side, rise     whole body offset in px (16 px = 1 block): forward +, right +, up +
  air                 True: do not stand the feet on the ground
  legR, legL          (swing, splay, turn) in degrees in the world: swing forward +,
                      splay outward +, toe turned out +. The legs are kept in the world frame
                      while the body leans and twists, and the body is lifted or lowered so
                      the lowest foot corner touches the ground (unless air).
  hands               (right, up, fwd) px from the point between the shoulders: where the
                      grip should be. Rigid arms cannot reach everywhere; with both hands on
                      the grip the solver takes the nearest point both fists can reach.
  sword               (yaw, pitch, roll) degrees, blade direction in the torso's frame:
                      yaw to the right +, pitch up + (pitch past 90 goes over the top and
                      behind). roll turns the edge about the blade; 0 = the edge on the
                      "downhill" side of the blade. At key level roll may also be
                      "lead" (edge leads the motion through this key), "prev" (keep the
                      previous key's roll) or ("flat", (r, u, f)) (flat of the blade faces
                      that direction in the torso frame); expand() resolves these.
  sword_world         True: sword yaw/pitch are in the world frame instead of the torso's
  grip                1 = both fists on the grip, 0 = left hand off (right arm reaches for
                      `hands` alone, left arm posed by `left`); values between blend
  left                (pitch, yaw, roll) model-space degrees of the free left arm
  rz, lz              twist (zRot) of the right/left arm about its own axis, degrees
  shR, shL            (dx, dy, dz) model px offset of the right/left shoulder pivot
  head_yaw            optional: the head's yaw on the body in degrees instead of facing ahead (a spin),
                      with head_follow (0..1) blending back toward facing ahead

A dual weapon (a blade in each hand, the Binary Edges) adds, with dual=True:

  handsL              (right, up, fwd) px from the point between the shoulders: where the left fist should be
                      (hands is the right fist's alone)
  swordL              (yaw, pitch, roll) of the left blade, as sword (roll symbols included); swordL_world too
  rzL                 twist of the left arm about its own axis (None: the least turn of the blade in the hand)

Each arm reaches for its own point and turns its blade in the hand (rightItem, leftItem); the blades are
single-edged, so rolls are taken as given (no flipping to the other edge).

Output: {part: {axis: value}} in the units the emotecraft format stores with
"degrees": true: body pitch/yaw/roll in degrees in the local frame and x/y/z in
blocks; every other part in model space (degrees, px).
"""

from __future__ import annotations

import cmath
import math

import numpy as np

import palib
import rig

R3 = lambda m: m[:3, :3]  # noqa: E731
DEG = math.degrees
RAD = math.radians
FLIP = np.diag([-1.0, -1.0, 1.0])  # local <-> model axes (a 180 degree turn about Z)
MIRROR = np.diag([-1.0, 1.0, 1.0])

SHOULDER = {"R": np.array([-5.0, 2.0, 0.0]), "L": np.array([5.0, 2.0, 0.0])}
FIST = {"R": rig.FIST["rightArm"], "L": rig.FIST["leftArm"]}
ARM_LEN = float(np.linalg.norm(FIST["R"]))
CHEST = np.array([0.0, 2.0, 0.0])  # between the shoulder pivots, model px

GEOM = rig.load_item()
HAND_T_L = np.array([-1.0 / 16.0, 0.125, -0.625])
A_ITEM = np.array([1.0, 1.0, 0.0]) / math.sqrt(2.0)  # blade axis in item model space, towards the tip
E_ITEM = np.array([-1.0, 1.0, 0.0]) / math.sqrt(2.0)  # the edge that faces "down the blade" as vanilla holds it
C_DISP = R3(rig.rot_xyz(RAD(0), RAD(-90), RAD(55)))  # item/handheld thirdperson_righthand rotation
T_DISP = np.array([0.0, 4.0 / 16.0, 0.5 / 16.0])
DISP_SCALE = 0.85
HAND = R3(rig.Rx(RAD(-90)) @ rig.Ry(RAD(180)))  # arm frame -> hand frame (ItemInHandLayer)
HAND_T = np.array([1.0 / 16.0, 0.125, -0.625])
U_ITEM = np.column_stack([C_DISP @ A_ITEM, C_DISP @ E_ITEM, np.cross(C_DISP @ A_ITEM, C_DISP @ E_ITEM)])
GRIP_SEP = float(np.linalg.norm(GEOM.grip_r - GEOM.grip_l)) * DISP_SCALE * 16.0  # px between fist centres


def _q_fist():
    """Right fist centre in the hand frame (blocks), i.e. after translate(1/16, 0.125, -0.625)."""
    return HAND.T @ (FIST["R"] / 16.0) - HAND_T


Q_FIST = _q_fist()
Q_FIST_L = HAND.T @ (rig.FIST["leftArm"] / 16.0) - HAND_T_L  # the left fist centre in the left hand's frame

READY = dict(lean=4.0, twist=0.0, tilt=0.0, fwd=0.0, side=0.0, rise=0.0, air=False,
             legR=(9.0, 3.0, 6.0), legL=(-9.0, 3.0, 14.0),
             hands=(0.0, -1.0, 7.0), sword=(0.0, 38.0, 0.0), sword_world=False,
             grip=1.0, left=(-20.0, 0.0, -8.0), rz=None, lz=0.0,
             shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0), head=True)

# ----------------------------------------------------------------------------- small maths


def rot3(axis, a):
    return R3({"x": rig.Rx, "y": rig.Ry, "z": rig.Rz}[axis](a))


def zyx(x, y, z):
    return rot3("z", z) @ rot3("y", y) @ rot3("x", x)


def zyx_solutions(R):
    """Both (x, y, z) with R = Rz(z) Ry(y) Rx(x)."""
    b = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    if abs(math.cos(b)) > 1e-7:
        a = math.atan2(R[2, 1], R[2, 2])
        g = math.atan2(R[1, 0], R[0, 0])
    else:  # gimbal: only a -+ g is defined; keep a = 0
        a = 0.0
        g = math.atan2(-R[0, 1], R[1, 1])
    return [(a, b, g), (a + math.pi, math.pi - b, g + math.pi)]


def _near(v, ref):
    """v shifted by whole turns to be nearest ref."""
    return v + 2 * math.pi * round((ref - v) / (2 * math.pi))


def pick(sols, prev=None):
    """The Euler solution closest to prev (continuity) or, without prev, the least twisted one."""
    best, cost = None, None
    for s in sols:
        if prev is not None:
            s = tuple(_near(v, r) for v, r in zip(s, prev))
            c = sum((v - r) ** 2 for v, r in zip(s, prev))
        else:
            s = tuple(_near(v, 0.0) for v in s)
            c = sum(v * v for v in s)
        if cost is None or c < cost:
            best, cost = s, c
    return best


def rodrigues(v, k, a):
    k = k / np.linalg.norm(k)
    return v * math.cos(a) + np.cross(k, v) * math.sin(a) + k * (k @ v) * (1 - math.cos(a))


def trf(r, u, f):
    """(right, up, fwd) -> model axes."""
    return np.array([-r, -u, -f], dtype=float)


# ----------------------------------------------------------------------------- body and sword frames


def body_local(spec):
    """Channel values of playerAnimator's "body" part (local frame) and its rotation in model axes."""
    pitch, yaw, roll = -RAD(spec["lean"]), -RAD(spec["twist"]), -RAD(spec["tilt"])
    R_local = rot3("z", roll) @ rot3("y", yaw) @ rot3("x", pitch)
    return (pitch, yaw, roll), FLIP @ R_local @ FLIP


def blade_axes(yaw_deg, pitch_deg):
    """Blade direction d and the roll-0 edge e0 in model axes (+X left, +Y down, -Z forward)."""
    y, p = RAD(yaw_deg), RAD(pitch_deg)
    d = np.array([-math.sin(y) * math.cos(p), -math.sin(p), -math.cos(y) * math.cos(p)])
    e0 = np.array([-math.sin(y) * math.sin(p), math.cos(p), -math.cos(y) * math.sin(p)])
    return d, e0


def sword_frame(yaw_deg, pitch_deg, roll_deg):
    d, e0 = blade_axes(yaw_deg, pitch_deg)
    return d, rodrigues(e0, d, RAD(roll_deg))


def roll_of(yaw_deg, pitch_deg, e_target):
    """Roll (degrees) that turns the edge as close to e_target (model axes) as possible."""
    d, e0 = blade_axes(yaw_deg, pitch_deg)
    t = e_target - d * (d @ e_target)
    if np.linalg.norm(t) < 1e-9:
        return 0.0
    return DEG(math.atan2(np.cross(e0, t) @ d, e0 @ t))


# ----------------------------------------------------------------------------- arms


def aim_arm(f, t, z, prev=None):
    """Euler (x, y, z) with Rz(z) Ry(y) Rx(x) f = t, for |t| = |f| and a given twist z."""
    tp = rot3("z", -z) @ t
    c = max(-1.0, min(1.0, tp[1] / f[1]))
    best, cost = None, None
    for x in (math.acos(c), -math.acos(c)):
        v = rot3("x", x) @ f
        y = cmath.phase(complex(tp[2], tp[0])) - cmath.phase(complex(v[2], v[0]))
        if prev is not None:
            s = (_near(x, prev[0]), _near(y, prev[1]), z)
            c2 = (s[0] - prev[0]) ** 2 + (s[1] - prev[1]) ** 2
        else:
            s = (_near(x, -1.2), _near(y, 0.0), z)
            c2 = s[1] ** 2 + 0.2 * (s[0] + 1.2) ** 2
        if cost is None or c2 < cost:
            best, cost = s, c2
    return best


def two_hand_grip(hint, d, shR, shL):
    """Right fist centre P with |P - S_R| = L and |P - GRIP_SEP d - S_L| = L, nearest to hint."""
    c1 = SHOULDER["R"] + shR
    c2 = SHOULDER["L"] + shL + GRIP_SEP * d
    ax = c2 - c1
    D = float(np.linalg.norm(ax))
    a = ax / D
    m = (c1 + c2) / 2.0
    if D >= 2 * ARM_LEN:
        return m, False
    r = math.sqrt(ARM_LEN ** 2 - (D / 2.0) ** 2)
    h = hint - m
    h = h - a * (a @ h)
    n = float(np.linalg.norm(h))
    if n < 1e-6:
        h = np.array([0.0, 0.0, -1.0]) - a * (a @ np.array([0.0, 0.0, -1.0]))
        n = float(np.linalg.norm(h))
    return m + r * h / n, True


def one_hand_reach(hint, shR, side="R"):
    c = SHOULDER[side] + shR
    v = hint - c
    return c + ARM_LEN * v / np.linalg.norm(v)


def _hand_cost(arm, d, e):
    """How far the blade has to turn in the hand for this arm pose (radians)."""
    Rh = zyx(*arm) @ HAND
    v1, v2 = Rh.T @ d, Rh.T @ e
    v2 = v2 - v1 * (v1 @ v2)
    v2 /= np.linalg.norm(v2)
    B = np.column_stack([v1, v2, np.cross(v1, v2)]) @ U_ITEM.T
    return math.acos(max(-1.0, min(1.0, (np.trace(B) - 1.0) / 2.0)))


def aim_blade(side, P, d, e, twist_deg, sh, prev_arm):
    """The arm of {side} ("R" or "L") with its fist at P, twisted about its own axis by twist_deg or (None) by
    whatever turns its blade (d, e) least in the hand, moving little from prev_arm."""
    f = FIST[side]
    to = P - (SHOULDER[side] + sh)
    if twist_deg is not None:
        return aim_arm(f, to, RAD(twist_deg), prev_arm)
    z0 = prev_arm[2] if prev_arm is not None else 0.0
    best = None
    for zd in np.arange(DEG(z0) - 170.0, DEG(z0) + 171.0, 5.0):
        arm = aim_arm(f, to, RAD(zd), prev_arm)
        cost = _hand_cost(arm, d, e) + 0.35 * abs(RAD(zd) - z0) + 0.15 * abs(RAD(zd))
        if best is None or cost < best[0]:
            best = (cost, zd)
    lo = best[1]
    for zd in np.arange(lo - 5.0, lo + 5.01, 0.5):
        arm = aim_arm(f, to, RAD(zd), prev_arm)
        cost = _hand_cost(arm, d, e) + 0.35 * abs(RAD(zd) - z0) + 0.15 * abs(RAD(zd))
        if cost < best[0]:
            best = (cost, zd)
    return aim_arm(f, to, RAD(best[1]), prev_arm)


def item_channels(R_arm, d, e, grip_pt, prev=None, left=False):
    """rightItem (or, left, leftItem) rotation (x, y, z radians) and position (px) that put the blade along d,
    its edge along e and the item point grip_pt (item model space) in that fist. The left hand's item is drawn
    with the same display turned back for the left hand (see rig.pose_from), so only the fist differs."""
    Rh = R_arm @ HAND
    v1, v2 = Rh.T @ d, Rh.T @ e
    v2 = v2 - v1 * (v1 @ v2)
    v2 /= np.linalg.norm(v2)
    V = np.column_stack([v1, v2, np.cross(v1, v2)])
    B = V @ U_ITEM.T
    rot = pick(zyx_solutions(B), prev)
    pos = (Q_FIST_L if left else Q_FIST) - zyx(*rot) @ (T_DISP + DISP_SCALE * C_DISP @ (grip_pt - 0.5))
    return rot, pos * 16.0


# ----------------------------------------------------------------------------- legs, head, ground


def leg_world(swing, splay, turn, side):
    s = 1.0 if side == "R" else -1.0
    return rot3("z", s * RAD(splay)) @ rot3("y", s * RAD(turn)) @ rot3("x", -RAD(swing))


class DictSource:
    """Stands in for an IAnimation: fixed channel values (radians / px / blocks)."""

    KEYS = {"position": ("x", "y", "z"), "rotation": ("pitch", "yaw", "roll"),
            "scale": ("scaleX", "scaleY", "scaleZ")}

    def __init__(self, ch):
        self.ch = ch

    def get(self, part, kind, value0):
        c = self.ch.get(part)
        if not c:
            return tuple(value0)
        return tuple(c.get(k, v0) for k, v0 in zip(self.KEYS[kind], value0))


def to_source_units(ch):
    """Degrees -> radians for the DictSource (positions are already px / blocks)."""
    out = {}
    for part, axes in ch.items():
        if part.startswith("_"):
            continue
        out[part] = {k: (RAD(v) if k in ("pitch", "yaw", "roll") else v) for k, v in axes.items()}
    return out


# ----------------------------------------------------------------------------- the solve


def solve(spec, prev=None):
    """spec -> channels in file units (plus "_rad" and "_info" for continuity and checks)."""
    s = dict(READY)
    s.update(spec)
    pv = (prev or {}).get("_rad", {})
    (bp, by, br), R_bm = body_local(s)

    legs = {}
    for side, key in (("R", "legR"), ("L", "legL")):
        legs[side] = pick(zyx_solutions(R_bm.T @ leg_world(*s[key], side)), pv.get("leg" + side))

    hy = math.atan2(-R_bm[0, 2], R_bm[0, 0])  # head yaw that faces straight ahead in the world
    if (R_bm @ np.array([-math.sin(hy), 0.0, -math.cos(hy)]))[2] > 0:
        hy += math.pi
    hy = _near(hy, pv.get("head", 0.0))
    if s.get("head_yaw") is not None:
        # an explicit head yaw on the body (degrees, the model's own), for a spin where facing ahead would
        # wring the neck round: blended toward facing ahead by head_follow (0 = the explicit yaw, 1 = ahead)
        own = RAD(s["head_yaw"])
        k = float(s.get("head_follow", 0.0))
        hy = own + (_near(hy, own) - own) * k

    d, e = sword_frame(*s["sword"])
    if s.get("sword_world"):
        d, e = R_bm.T @ d, R_bm.T @ e

    shR, shL = np.array(s["shR"], float), np.array(s["shL"], float)
    hint = CHEST + trf(*s["hands"])
    g = float(s["grip"]) if not isinstance(s["grip"], str) else (1.0 if s["grip"] == "two" else 0.0)
    lz = RAD(s["lz"])
    ok = True

    def aim_right(P, prev_arm):
        """Right arm aimed at P. rz None: twist the arm about its own axis so the sword needs the
        least rotation in the hand (keeps rightItem near vanilla and far from gimbal lock)."""
        if s["rz"] is not None:
            return aim_arm(FIST["R"], P - (SHOULDER["R"] + shR), RAD(s["rz"]), prev_arm)
        best = None
        z0 = prev_arm[2] if prev_arm is not None else 0.0
        zc = DEG(z0)
        for zd in np.arange(zc - 170.0, zc + 171.0, 5.0):
            z = RAD(zd)
            arm = aim_arm(FIST["R"], P - (SHOULDER["R"] + shR), z, prev_arm)
            Rh = zyx(*arm) @ HAND
            v1, v2 = Rh.T @ d, Rh.T @ e
            v2 = v2 - v1 * (v1 @ v2)
            v2 /= np.linalg.norm(v2)
            B = np.column_stack([v1, v2, np.cross(v1, v2)]) @ U_ITEM.T
            ang = math.acos(max(-1.0, min(1.0, (np.trace(B) - 1.0) / 2.0)))
            cost = ang + 0.35 * abs(z - z0) + 0.15 * abs(z)
            if best is None or cost < best[0]:
                best = (cost, zd)
        lo = best[1]
        for zd in np.arange(lo - 5.0, lo + 5.01, 0.5):  # refine
            z = RAD(zd)
            arm = aim_arm(FIST["R"], P - (SHOULDER["R"] + shR), z, prev_arm)
            Rh = zyx(*arm) @ HAND
            v1, v2 = Rh.T @ d, Rh.T @ e
            v2 = v2 - v1 * (v1 @ v2)
            v2 /= np.linalg.norm(v2)
            B = np.column_stack([v1, v2, np.cross(v1, v2)]) @ U_ITEM.T
            ang = math.acos(max(-1.0, min(1.0, (np.trace(B) - 1.0) / 2.0)))
            cost = ang + 0.35 * abs(z - z0) + 0.15 * abs(z)
            if cost < best[0]:
                best = (cost, zd)
        return aim_arm(FIST["R"], P - (SHOULDER["R"] + shR), RAD(best[1]), prev_arm)

    dual = bool(s.get("dual"))
    if dual:
        # a blade in each hand: each arm reaches for its own point and turns its own blade
        dL, eL = sword_frame(*s["swordL"])
        if s.get("swordL_world"):
            dL, eL = R_bm.T @ dL, R_bm.T @ eL
        armR = aim_blade("R", one_hand_reach(hint, shR), d, e, s["rz"], shR, pv.get("armR"))
        hintL = CHEST + trf(*s["handsL"])
        armL = aim_blade("L", one_hand_reach(hintL, shL, "L"), dL, eL, s.get("rzL"), shL, pv.get("armL"))
    else:
        if g > 0.0:
            P2, ok = two_hand_grip(hint, d, shR, shL)
            armR2 = aim_right(P2, pv.get("armR"))
            armL2 = aim_arm(FIST["L"], P2 - GRIP_SEP * d - (SHOULDER["L"] + shL), lz, pv.get("armL"))
        if g < 1.0:
            P1 = one_hand_reach(hint, shR)
            armR1 = aim_right(P1, pv.get("armR"))
            armL1 = pick([tuple(RAD(v) for v in s["left"])], pv.get("armL"))
        if g >= 1.0:
            armR, armL = armR2, armL2
        elif g <= 0.0:
            armR, armL = armR1, armL1
        else:
            armR = tuple(a + (b - a) * g for a, b in zip(armR1, (_near(v, r) for v, r in zip(armR2, armR1))))
            armL = tuple(a + (b - a) * g for a, b in zip(armL1, (_near(v, r) for v, r in zip(armL2, armL1))))
    irot, ipos = item_channels(zyx(*armR), d, e, GEOM.grip_r, pv.get("item"))
    if dual:
        lrot, lpos = item_channels(zyx(*armL), dL, eL, GEOM.grip_r, pv.get("itemL"), left=True)

    ch = {
        "body": {"pitch": DEG(bp), "yaw": DEG(by), "roll": DEG(br), "x": s["side"] / 16.0, "y": 0.0,
                 "z": -s["fwd"] / 16.0},
        "rightArm": {"pitch": DEG(armR[0]), "yaw": DEG(armR[1]), "roll": DEG(armR[2])},
        "leftArm": {"pitch": DEG(armL[0]), "yaw": DEG(armL[1]), "roll": DEG(armL[2])},
        "rightLeg": {"pitch": DEG(legs["R"][0]), "yaw": DEG(legs["R"][1]), "roll": DEG(legs["R"][2])},
        "leftLeg": {"pitch": DEG(legs["L"][0]), "yaw": DEG(legs["L"][1]), "roll": DEG(legs["L"][2])},
        "rightItem": {"pitch": DEG(irot[0]), "yaw": DEG(irot[1]), "roll": DEG(irot[2]),
                      "x": float(ipos[0]), "y": float(ipos[1]), "z": float(ipos[2])},
    }
    if dual:
        ch["leftItem"] = {"pitch": DEG(lrot[0]), "yaw": DEG(lrot[1]), "roll": DEG(lrot[2]),
                          "x": float(lpos[0]), "y": float(lpos[1]), "z": float(lpos[2])}
    if s.get("head", True):
        ch["head"] = {"yaw": DEG(hy)}
    if s.get("_arm_pos"):
        ch["rightArm"].update({"x": -5.0 + shR[0], "y": 2.0 + shR[1], "z": 0.0 + shR[2]})
        ch["leftArm"].update({"x": 5.0 + shL[0], "y": 2.0 + shL[1], "z": 0.0 + shL[2]})

    def lows(c):
        posed = rig.pose_from(DictSource(to_source_units(c)))
        return {l: float(rig.xf(posed.parts[l], rig.box_corners(l))[:, 1].min()) for l in ("rightLeg", "leftLeg")}

    lo = lows(ch)
    ch["body"]["y"] = (0.0 if s["air"] else -min(lo.values())) + s["rise"] / 16.0
    if s.get("plant2") and not s["air"]:
        # both feet on the ground: straighten the higher leg (scale its swing and splay) until it touches
        hi_leg = max(lo, key=lo.get)
        side = "R" if hi_leg == "rightLeg" else "L"
        key = "leg" + side
        sw, sp_, tn = s[key]
        target = min(lo.values()) + ch["body"]["y"]  # where the planted foot is after grounding
        a, b = 0.0, 1.0
        for _ in range(30):
            k = (a + b) / 2.0
            e = pick(zyx_solutions(R_bm.T @ leg_world(sw * k, sp_ * k, tn, side)), legs[side])
            trial = dict(ch)
            trial[hi_leg] = {"pitch": DEG(e[0]), "yaw": DEG(e[1]), "roll": DEG(e[2])}
            if lows(trial)[hi_leg] > target:
                b = k
            else:
                a = k
        e = pick(zyx_solutions(R_bm.T @ leg_world(sw * a, sp_ * a, tn, side)), legs[side])
        legs[side] = e
        ch[hi_leg] = {"pitch": DEG(e[0]), "yaw": DEG(e[1]), "roll": DEG(e[2])}
    ch["_rad"] = {"legR": legs["R"], "legL": legs["L"], "head": hy, "armR": armR, "armL": armL, "item": irot}
    ch["_info"] = {"reach_ok": ok, "d": d, "e": e, "grip": g}
    if dual:
        ch["_rad"]["itemL"] = lrot
        ch["_info"].update({"dL": dL, "eL": eL, "dual": True})
    return ch


def mirrored_item(ch, prev=None):
    """For a mirror-safe animation: the rightItem values the MirrorModifier'd play needs (the
    mirrored right arm is the original left arm), so the sword is the mirror image of the
    original and sits in that fist at its lower grip. Returns (rot degrees, pos px)."""
    la = ch["leftArm"]
    R_arm_m = zyx(RAD(la["pitch"]), -RAD(la["yaw"]), -RAD(la["roll"]))
    d, e = ch["_info"]["d"], ch["_info"]["e"]
    grip_pt = GEOM.grip_l if ch["_info"]["grip"] >= 0.5 else GEOM.grip_r
    rot, pos = item_channels(R_arm_m, MIRROR @ d, MIRROR @ e, grip_pt, prev)
    return tuple(DEG(v) for v in rot), tuple(float(v) for v in pos), rot


# ----------------------------------------------------------------------------- timelines


def _lerp(a, b, f):
    if isinstance(a, bool) or isinstance(b, bool) or isinstance(a, str) or isinstance(b, str):
        return a if f < 1.0 else b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return a + (b - a) * f
    if isinstance(a, tuple) and isinstance(b, tuple) and len(a) == len(b):
        return tuple(_lerp(x, y, f) for x, y in zip(a, b))
    return a if f < 1.0 else b


def lerp_spec(a, b, f):
    return {k: _lerp(a.get(k, b.get(k)), b.get(k, a.get(k)), f) for k in set(a) | set(b)}


def blade_world(spec, key="sword"):
    """Blade direction in world model axes for a (resolved or unresolved) spec (key "swordL": the left blade)."""
    s = dict(READY)
    s.update(spec)
    _, R_bm = body_local(s)
    d, _ = blade_axes(s[key][0], s[key][1])
    return d if s.get(key + "_world") else R_bm @ d


def expand(keys, base=None):
    """keys: [(tick, ease, {changes})...]; each key inherits the previous one's values. Resolves
    the symbolic rolls ("lead", "prev", ("flat", dir), ("sym", deg)) and unwraps rolls so they
    turn the short way from key to key."""
    cur = dict(READY if base is None else base)
    out = []
    for tick, ease, ch in keys:
        cur = dict(cur)
        cur.update(ch)
        out.append([tick, ease, cur])
    n = len(out)
    keys_to_resolve = ["sword"] + (["swordL"] if any(k[2].get("dual") for k in out) else [])
    for key in keys_to_resolve:
        ofs_key = "roll_ofs" if key == "sword" else "roll_ofsL"
        for i, (tick, ease, sp) in enumerate(out):
            yaw, pitch, roll = sp[key]
            if isinstance(roll, (int, float)):
                continue
            single = sp.get("true_edge") or sp.get("dual")  # a sickle has one edge: no flipping to the other
            if roll == "lead":
                a = blade_world(out[max(i - 1, 0)][2], key)  # motion through this key: previous key to next key
                b = blade_world(out[min(i + 1, n - 1)][2], key)
                mv = b - a
                _, R_bm = body_local(dict(READY, **sp))
                m_t = mv if sp.get(key + "_world") else R_bm.T @ mv
                r = roll_of(yaw, pitch, m_t) if np.linalg.norm(m_t) > 1e-6 else 0.0
                r += sp.get(ofs_key, 0.0)
                if i > 0 and not single:
                    # the blade is double-edged: lead with whichever edge needs the least turn of the wrist
                    pr = out[i - 1][2][key][2]
                    pr = pr if isinstance(pr, (int, float)) else 0.0
                    alt = r + 180.0
                    if abs(((alt - pr + 180) % 360) - 180) < abs(((r - pr + 180) % 360) - 180):
                        r = alt
            elif roll == "prev":
                r = out[i - 1][2][key][2]
                r = r if isinstance(r, (int, float)) else 0.0
            elif isinstance(roll, tuple) and roll[0] == "flat":
                dv, _ = blade_axes(yaw, pitch)
                nrm = trf(*roll[1])
                ev = np.cross(nrm, dv)
                r = roll_of(yaw, pitch, ev)
            elif isinstance(roll, tuple) and roll[0] == "sym":
                r = float(roll[1])
                if i > 0 and not single:
                    pr = out[i - 1][2][key][2]
                    alt = r + 180.0
                    if abs(((alt - pr + 180) % 360) - 180) < abs(((r - pr + 180) % 360) - 180):
                        r = alt
            else:
                raise ValueError(f"bad roll {roll!r}")
            sp[key] = (yaw, pitch, r)
    for tick, ease, sp in out:
        # double-edged blade, two-sided sprite: roll r and r + 180 look alike, so take the one that
        # needs the smaller rotation in the hand. That keeps rightItem near vanilla, away from
        # gimbal lock and from the +-180 wrap that the game's fades blend across.
        if sp.get("true_edge") or sp.get("dual"):
            continue
        y, p, r = sp["sword"]
        best = None
        for cand in (r, r + 180.0):
            ch = solve(dict(sp, sword=(y, p, cand)))
            a = ch["_rad"]["item"]
            ang = math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(zyx(*a)) - 1.0) / 2.0))))
            if best is None or ang < best[0] - 1e-6:
                best = (ang, cand)
        sp["sword"] = (y, p, best[1])
    for key in keys_to_resolve:
        prev_r = None
        for tick, ease, sp in out:  # rolls turn the short way between keys
            y, p, r = sp[key]
            if prev_r is not None:
                r = r + 360.0 * round((prev_r - r) / 360.0)
            sp[key] = (y, p, r)
            prev_r = r
    return [tuple(k) for k in out]


def resolve_twist(keys):
    """Auto arm twist (rz None) is settled per key, not per tick: each such key is solved once,
    continuing from the key before it, and its chosen twist is written back as a number, so the
    twist then interpolates smoothly between keys like any other value. Keys marked canonical
    (the ready pose) are solved from scratch, so every move starts and ends on the very same
    channel values and the game's fades between moves have nothing to blend."""
    prev = None
    out = []
    for tick, ease, sp in keys:
        sp = dict(sp)
        auto_r = sp.get("rz") is None
        auto_l = sp.get("dual") and sp.get("rzL") is None
        ch = solve(sp, None if sp.get("canonical") else prev)
        if auto_r:
            sp["rz"] = DEG(ch["_rad"]["armR"][2])
        if auto_l:
            sp["rzL"] = DEG(ch["_rad"]["armL"][2])
        prev = ch
        out.append((tick, ease, sp))
    for key in ("rz", "rzL"):
        last = None
        for i, (tick, ease, sp) in enumerate(out):  # twist turns the short way from key to key
            if sp.get(key) is None:
                continue
            if last is not None:
                sp[key] = sp[key] + 360.0 * round((last - sp[key]) / 360.0)
            last = sp[key]
    return out


def sample(keys, t):
    """Spec at tick t: the ease named on the key a segment starts from shapes that segment."""
    if t <= keys[0][0]:
        return dict(keys[0][2])
    for (t0, e0, s0), (t1, e1, s1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            return lerp_spec(s0, s1, palib.ease_invoke(palib.ease_from_string(e0), f))
    return dict(keys[-1][2])


def timeline(keys, ticks, prev=None):
    out = []
    for t in ticks:
        ch = solve(sample(keys, t), prev)
        out.append((t, ch))
        prev = ch
    return out


def unwrap(frames):
    """Keep every angle channel continuous across ticks (no 360 jumps)."""
    last = {}
    for t, ch in frames:
        for part, axes in ch.items():
            if part.startswith("_"):
                continue
            for k, v in axes.items():
                if k in ("pitch", "yaw", "roll"):
                    key = (part, k)
                    if key in last:
                        v = v + 360.0 * round((last[key] - v) / 360.0)
                    axes[k] = v
                    last[key] = v
    return frames
