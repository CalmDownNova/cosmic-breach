"""Offline previewer for playerAnimator player animations.

    python tools/anim/preview.py <animation.json> [options]

Loads the file exactly as playerAnimator 2.0.4 does (palib), plays it tick by
tick like KeyframeAnimationPlayer, poses the vanilla player model and the held
Meridian (rig) and renders:

  previews/<name>_strip.png   one frame per game tick; rows: side, front, top,
                              first person (only arms and sword, as the game draws
                              them in THIRD_PERSON_MODEL mode)
  previews/<name>_t<N>.png    one large frame at tick N (--tick, default: the first
                              active tick) from five cameras, with the blade tip's
                              path over the whole animation (active ticks red)

and prints a per-tick table of checks: how far each fist is from the grip,
whether the sword or an arm passes through the body, where the feet are.

Options:
  --tick N           large frame tick (default: first active tick, or 0)
  --ticks A:B        filmstrip range (default: every tick until the animation stops)
  --sheet 1,3,5      also write <name>_keys.png: these ticks big, from side, behind, front
                     and first person
  --fp-pitch DEG     first-person camera pitch, looking down (default 15)
  --mirror           play through MirrorModifier (what the game does for a left dash)
  --fade-from F@T    start the animation mid-way through another one: F at its tick T,
                     faded over --fade ticks (default 2) with INOUTSINE, as
                     ModifierLayer.replaceAnimationWithFade does
  --out DIR          output folder (default tools/anim/previews)
  --no-strip / --no-frame / --quiet

Checks printed per tick: fistR / fistL = how far each fist centre is from the grip
(pommel to guard segment), in model pixels; feetR / feetL = lowest point of each leg
above the ground; headYaw = where the face points (0 = straight ahead); clips = sword
length inside the torso, head or legs, and arm centre-line samples inside the torso.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import palib  # noqa: E402
import render  # noqa: E402
import rig  # noqa: E402

PX = 16.0 / rig.PLAYER_SCALE  # local blocks -> player-model pixels
FP_PITCH = 15.0  # first-person camera pitch: looking 15 degrees down, at a mob's body 2.5 blocks away
TIMING_FILE = os.path.join(HERE, "timing.json")
PHASE_RGB = {"move": (70, 120, 90), "startup": (110, 110, 110), "active": (205, 30, 30), "recovery": (40, 90, 190),
             "loop": (120, 60, 160), "blend": (150, 150, 150), "vanilla": (170, 170, 170)}


def load_timing():
    if os.path.exists(TIMING_FILE):
        with open(TIMING_FILE, encoding="utf-8") as fh:
            return json.load(fh)
    return {}


def phase_of(t, timing, anim):
    if timing and "active" not in timing:
        return "move" if t < anim.end else "blend"
    if timing:
        s, a = timing.get("startup", 0), timing.get("active", [])
        if a and a[0] <= t <= a[1]:
            return "active"
        if t < s:
            return "startup"
        if t < anim.end:
            return "recovery"
        return "blend"
    if anim.infinite:
        return "loop"
    return "blend" if t >= anim.end else "startup"


# ----------------------------------------------------------------------------- simulation


class Playback:
    """Steps a source like the game: frame for tick t is drawn at tickDelta 0 after t tick() calls."""

    def __init__(self, source):
        self.src = source

    def frames(self, n, delta=0.0):
        out = []
        for i in range(n):
            active = self.src.active()
            self.src.setup(delta)
            out.append((i, active, rig.pose_from(self.src if active else None)))
            self.src.tick()
        return out


def make_source(path, anim_name=None, mirror=False, fade_from=None, fade=2):
    anims = palib.load(path)
    anim = anims[0] if anim_name is None else next(a for a in anims if a.name == anim_name)
    src = palib.Player(anim)
    if mirror:
        src = palib.Mirror(src)
    if fade_from:
        fpath, ftick = fade_from.rsplit("@", 1)
        fa = palib.load(fpath)[0]
        prev = palib.Player(fa)
        for _ in range(int(ftick)):
            prev.tick()
        src = palib.Fade(prev, src, fade)
    return anim, src


# ----------------------------------------------------------------------------- checks


def _seg_point_dist(p, a, b):
    ab = b - a
    t = float(np.clip((p - a) @ ab / (ab @ ab), 0.0, 1.0))
    return float(np.linalg.norm(a + t * ab - p)), t


def _inside_box(posed, part, pts, shrink_px=0.5):
    """How many of pts (local blocks) are inside a part's box, shrunk by shrink_px on each side."""
    inv = np.linalg.inv(posed.parts[part])
    q = rig.xf(inv, pts) * 16.0
    (x0, y0, z0), (w, h, d) = rig.BOXES[part]
    s = shrink_px
    m = ((q[:, 0] > x0 + s) & (q[:, 0] < x0 + w - s) & (q[:, 1] > y0 + s) & (q[:, 1] < y0 + h - s)
         & (q[:, 2] > z0 + s) & (q[:, 2] < z0 + d - s))
    return m


def checks(posed, geom, active=True):
    """Numbers that answer the review questions for one frame."""
    if posed.item is None:
        return {}
    ip = rig.item_points(posed, geom)
    fr = rig.part_point(posed, "rightArm", rig.FIST["rightArm"])
    fl = rig.part_point(posed, "leftArm", rig.FIST["leftArm"])
    dr, tr = _seg_point_dist(fr, ip["pommel"], ip["guard"])
    blades = [ip]
    if posed.item_left is not None:
        ipl = rig.item_points(posed, geom, left=True)  # a dual weapon: the left fist holds its own blade
        dl, tl = _seg_point_dist(fl, ipl["pommel"], ipl["guard"])
        blades.append(ipl)
    else:
        dl, tl = _seg_point_dist(fl, ip["pommel"], ip["guard"])
    # sword through the body: sample the whole sword (each blade of a pair)
    clip = {}
    s = np.linspace(0, 1, 60)[:, None]
    for bp in blades:
        sword = bp["pommel"] + s * (bp["tip"] - bp["pommel"])
        seg_len = np.linalg.norm(bp["tip"] - bp["pommel"]) * PX / 59
        for part in ("torso", "head", "rightLeg", "leftLeg"):
            n = int(_inside_box(posed, part, sword, 0.3).sum())
            if n:
                clip[part] = round(clip.get(part, 0.0) + n * seg_len, 1)
    # arms through the torso: sample each arm's centre line from shoulder to fist
    arm_in = {}
    for arm in ("rightArm", "leftArm"):
        cx = -1.0 if arm == "rightArm" else 1.0
        line = np.array([[cx, y, 0.0] for y in np.linspace(0, 10, 21)])
        pts = rig.part_point(posed, arm, line)
        n = int(_inside_box(posed, "torso", pts, 0.8).sum())
        if n:
            arm_in[arm] = n
    # feet: lowest point of each leg box above the ground, in px
    feet = {}
    for leg in ("rightLeg", "leftLeg"):
        c = rig.xf(posed.parts[leg], rig.box_corners(leg))
        feet[leg] = round(float(c[:, 1].min()) * PX, 1)
    # head: where the face points (yaw from forward, + = to the player's right) and pitch
    hm = posed.parts["head"]
    f = hm[:3, :3] @ np.array([0, 0, -1.0])
    yaw = math.degrees(math.atan2(f[0], -f[2]))
    return {"fistR": round(dr * PX, 1), "fistL": round(dl * PX, 1), "clip": clip, "armIn": arm_in,
            "feet": feet, "headYaw": round(yaw, 0), "tip": ip["tip"], "guard": ip["guard"]}


def half_tick_report(path, anim_name=None, mirror=False):
    rig.use_weapon(rig.weapon_for(os.path.basename(path).split('.')[0]))
    """What the game draws between ticks: the library blends every channel linearly, so a fast
    swing can separate the hands, flip the blade or cut through the body mid-tick. Checks
    tick + 0.25, 0.5, 0.75 and returns the worst values."""
    geom = rig.load_item()
    anim, src = make_source(path, anim_name, mirror)
    worst = []
    n = anim.stop if not anim.infinite else anim.end + 1
    for t in range(n):
        for d in (0.25, 0.5, 0.75):
            src.setup(d)
            if not src.active():
                continue
            posed = rig.pose_from(src)
            c = checks(posed, geom)
            clip = sum(c["clip"].values()) + sum(c["armIn"].values())
            worst.append((t + d, c["fistL"], c["fistR"], clip, c["clip"], c["armIn"]))
        src.setup(0.0)
        src.tick()
    return worst


def flip_report(path, anim_name=None, mirror=False):
    rig.use_weapon(rig.weapon_for(os.path.basename(path).split('.')[0]))
    """Does the blade turn smoothly between ticks? The game blends Euler angles linearly, which can
    swing the sword the long way round. For each tick t -> t+1 this compares the blade and edge
    direction drawn at t + 0.5 with the halfway rotation between the two tick poses; returns
    [(t, degrees off)] worst first."""
    geom = rig.load_item()
    anim, src = make_source(path, anim_name, mirror)
    n = anim.stop if not anim.infinite else anim.end + 1

    def frame(posed):
        ip = rig.item_points(posed, geom)
        d = ip["tip"] - ip["guard"]
        d /= np.linalg.norm(d)
        m = posed.item[:3, :3]
        nrm = m @ np.array([0.0, 0.0, 1.0])
        nrm -= d * (d @ nrm)
        nrm /= np.linalg.norm(nrm)
        return d, nrm

    out = []
    for t in range(n - 1):
        src.setup(0.0)
        if not src.active():
            break
        a = frame(rig.pose_from(src))
        src.setup(0.5)
        mid = frame(rig.pose_from(src))
        src.setup(0.0)
        src.tick()
        if not src.active():
            break
        b = frame(rig.pose_from(src))
        dh = a[0] + b[0]
        nh = a[1] + b[1]
        err = 0.0
        if np.linalg.norm(dh) > 1e-3:
            dh /= np.linalg.norm(dh)
            err = max(err, math.degrees(math.acos(max(-1.0, min(1.0, float(dh @ mid[0]))))))
        if np.linalg.norm(nh) > 1e-3:
            nh /= np.linalg.norm(nh)
            err = max(err, math.degrees(math.acos(max(-1.0, min(1.0, abs(float(nh @ mid[1])))))))
        out.append((t, round(err, 1)))
    return sorted(out, key=lambda x: -x[1])


def print_table(name, frames, geom, timing, anim):
    print(f"== {name}: begin {anim.begin} end {anim.end} stop {anim.stop} loop {anim.infinite}"
          f"{' ret ' + str(anim.ret) if anim.infinite else ''}")
    print(" tick phase    fistR fistL  feetR feetL headYaw  clips")
    worst = {"fistL": 0.0, "clip": 0}
    for t, active, posed in frames:
        c = checks(posed, geom, active)
        if not c:
            continue
        ph = phase_of(t, timing, anim) if active else "vanilla"
        clips = ", ".join([f"sword/{k} {v}px" for k, v in c["clip"].items()]
                          + [f"{k} in torso x{v}" for k, v in c["armIn"].items()])
        print(f" {t:4d} {ph:8s} {c['fistR']:5.1f} {c['fistL']:5.1f}  {c['feet']['rightLeg']:5.1f} "
              f"{c['feet']['leftLeg']:5.1f} {c['headYaw']:6.0f}   {clips}")
        if active:
            worst["fistL"] = max(worst["fistL"], c["fistL"])
            worst["clip"] += len(c["clip"]) + len(c["armIn"])
    return worst


# ----------------------------------------------------------------------------- images


def strip(frames, geom, timing, anim, title, per_row=10, fw=150, fh=150):
    views = render.standard_views(fw, fh, scale=fw / 4.2)
    rows = [("side", views["side"]), ("front", views["front"]), ("top", views["top"]),
            ("fp", render.first_person(fw, int(fh * 0.75), pitch_deg=FP_PITCH))]
    tips = [(t, checks(p, geom)["tip"]) for t, a, p in frames if a and p.item is not None]
    n = len(frames)
    blocks = math.ceil(n / per_row)
    label_h = 16
    row_h = [fh, fh, fh, int(fh * 0.75)]
    block_h = label_h + sum(row_h) + 6
    W = 60 + per_row * fw
    img = Image.new("RGB", (W, 28 + blocks * block_h), (250, 250, 247))
    d = ImageDraw.Draw(img)
    d.text((6, 6), title, fill=(0, 0, 0), font=render.FONT_L)
    for bi in range(blocks):
        y0 = 28 + bi * block_h
        y = y0 + label_h
        for (vname, _), rh in zip(rows, row_h):
            d.text((6, y + rh // 2 - 6), vname, fill=(60, 60, 60), font=render.FONT)
            y += rh
        for k in range(per_row):
            i = bi * per_row + k
            if i >= n:
                break
            t, active, posed = frames[i]
            ph = phase_of(t, timing, anim) if active else "vanilla"
            x = 60 + k * fw
            d.rectangle([x, y0, x + fw - 2, y0 + label_h - 2], fill=PHASE_RGB[ph])
            d.text((x + 4, y0 + 1), f"t{t} {ph}", fill=(255, 255, 255), font=render.FONT)
            sc = render.build_scene(posed, geom)
            y = y0 + label_h
            for (vname, view), rh in zip(rows, row_h):
                if vname == "fp":
                    sc_fp = render.build_scene(posed, geom, parts=("rightArm", "leftArm"))
                    im = render.render(sc_fp, view, bg=np.array([0.80, 0.86, 0.93]))
                else:
                    trail = [p for tt, p in tips]
                    tr = [(np.array(trail), (0.75, 0.55, 0.55), 1.0)] if len(trail) > 1 else []
                    im = render.render(sc, view, trails=tr)
                img.paste(im, (x, y))
                if active and ph == "active":
                    d.rectangle([x, y, x + fw - 1, y + rh - 1], outline=(205, 30, 30), width=2)
                y += rh
    return img


def big_frame(frames, geom, timing, anim, tick, title):
    sel = [f for f in frames if f[0] == tick]
    if not sel:
        raise SystemExit(f"tick {tick} is outside the rendered range")
    t, active, posed = sel[0]
    sc = render.build_scene(posed, geom)
    sz = 300
    views = render.standard_views(sz, sz, scale=sz / 4.0)
    cams = [("side (player faces right)", views["side"]), ("front", views["front"]),
            ("top (forward is up)", views["top"]), ("behind, 3/4", render.three_quarter(sz, sz, sz / 4.0))]
    tips = [(tt, checks(p, geom)["tip"], phase_of(tt, timing, anim)) for tt, a, p in frames
            if a and p.item is not None]
    W = sz * 3
    img = Image.new("RGB", (W, 30 + sz * 2 + 20), (250, 250, 247))
    d = ImageDraw.Draw(img)
    ph = phase_of(t, timing, anim) if active else "vanilla"
    d.text((8, 7), f"{title}   tick {t} ({ph})", fill=(0, 0, 0), font=render.FONT_L)
    for i, (nm, v) in enumerate(cams):
        trails = []
        pts = np.array([p for _, p, _ in tips])
        if len(pts) > 1:
            trails.append((pts, (0.55, 0.55, 0.75), 1.4))
            act = np.array([p for _, p, pp in tips if pp == "active"])
            if len(act) > 1:
                trails.append((act, (0.85, 0.1, 0.1), 2.2))
        marks = [(p, (0.85, 0.1, 0.1) if pp == "active" else (0.35, 0.35, 0.6), 2.5) for _, p, pp in tips]
        im = render.render(sc, v, trails=trails, marks=marks, ss=3)
        x, y = (i % 3) * sz, 30 + (i // 3) * sz
        img.paste(im, (x, y))
        d.text((x + 6, y + 4), nm, fill=(40, 40, 40), font=render.FONT)
    fpv = render.first_person(sz * 2 - 20, sz - 10, pitch_deg=FP_PITCH)
    scf = render.build_scene(posed, geom, parts=("rightArm", "leftArm"))
    im = render.render(scf, fpv, ss=2, bg=np.array([0.80, 0.86, 0.93]))
    img.paste(im, (sz + 10, 30 + sz + 5))
    d.text((sz + 16, 30 + sz + 9), f"first person (FOV 70, looking {FP_PITCH:.0f} deg down): what the player sees", fill=(40, 40, 40),
           font=render.FONT)
    return img


def key_sheet(frames, geom, timing, anim, ticks, title, sz=210):
    """Chosen ticks side by side at a readable size: side, behind 3/4, front, first person."""
    sel = [f for f in frames if f[0] in ticks]
    views = render.standard_views(sz, sz, scale=sz / 3.6)
    rows = [("side", views["side"]), ("behind", render.three_quarter(sz, sz, sz / 3.6)),
            ("front", views["front"]), ("fp", render.first_person(sz, int(sz * 0.62), pitch_deg=FP_PITCH))]
    tips = [(tt, checks(p, geom)["tip"], phase_of(tt, timing, anim)) for tt, a, p in frames
            if a and p.item is not None]
    heights = [sz, sz, sz, int(sz * 0.62)]
    W = 50 + len(sel) * sz
    H = 30 + 16 + sum(heights)
    img = Image.new("RGB", (W, H), (250, 250, 247))
    d = ImageDraw.Draw(img)
    d.text((6, 6), title, fill=(0, 0, 0), font=render.FONT_L)
    y = 46
    for (nm, _), h in zip(rows, heights):
        d.text((4, y + h // 2 - 6), nm, fill=(60, 60, 60), font=render.FONT)
        y += h
    pts = np.array([p for _, p, _ in tips])
    act = np.array([p for _, p, pp in tips if pp == "active"])
    for i, (t, active, posed) in enumerate(sel):
        x = 50 + i * sz
        ph = phase_of(t, timing, anim) if active else "vanilla"
        d.rectangle([x, 30, x + sz - 2, 44], fill=PHASE_RGB[ph])
        d.text((x + 4, 31), f"t{t} {ph}", fill=(255, 255, 255), font=render.FONT)
        sc = render.build_scene(posed, geom)
        y = 46
        for (nm, v), h in zip(rows, heights):
            if nm == "fp":
                im = render.render(render.build_scene(posed, geom, parts=("rightArm", "leftArm")), v,
                                   bg=np.array([0.80, 0.86, 0.93]))
            else:
                tr = []
                if len(pts) > 1:
                    tr.append((pts, (0.62, 0.62, 0.8), 1.2))
                if len(act) > 1:
                    tr.append((act, (0.85, 0.1, 0.1), 2.0))
                im = render.render(sc, v, trails=tr)
            img.paste(im, (x, y))
            y += h
    return img


# ----------------------------------------------------------------------------- main


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("file")
    ap.add_argument("--anim")
    ap.add_argument("--tick", type=int)
    ap.add_argument("--ticks")
    ap.add_argument("--mirror", action="store_true")
    ap.add_argument("--fade-from")
    ap.add_argument("--fade", type=int, default=2)
    ap.add_argument("--out", default=os.path.join(HERE, "previews"))
    ap.add_argument("--suffix", default="")
    ap.add_argument("--no-strip", action="store_true")
    ap.add_argument("--no-frame", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    ap.add_argument("--sheet", help="comma separated ticks for a key sheet")
    ap.add_argument("--fp-pitch", type=float, help="first-person camera pitch (default 15)")
    a = ap.parse_args(argv)

    global FP_PITCH
    if a.fp_pitch is not None:
        FP_PITCH = a.fp_pitch
    anim, src = make_source(a.file, a.anim, a.mirror, a.fade_from, a.fade)
    rig.use_weapon(rig.weapon_for(anim.name))
    timing = load_timing().get(anim.name, {})
    n = anim.stop + 1 if not anim.infinite else max(anim.end + 1, 20) + (anim.ret if anim.infinite else 0)
    if a.ticks:
        lo, hi = (int(x) for x in a.ticks.split(":"))
    else:
        lo, hi = 0, n - 1
    frames = [f for f in Playback(src).frames(hi + 1) if f[0] >= lo]
    geom = rig.load_item()
    name = anim.name + ("_mirror" if a.mirror else "") + (f"_from_{os.path.basename(a.fade_from).split('.')[0]}"
                                                          if a.fade_from else "") + a.suffix
    os.makedirs(a.out, exist_ok=True)
    worst = print_table(name, frames, geom, timing, anim) if not a.quiet else None
    if not a.no_strip:
        title = f"{name}   ({os.path.basename(a.file)}; red = active ticks; faint line = blade tip path)"
        strip(frames, geom, timing, anim, title).save(os.path.join(a.out, f"{name}_strip.png"))
    if a.sheet:
        ticks = [int(x) for x in a.sheet.split(",")]
        key_sheet(frames, geom, timing, anim, ticks, f"{name} key poses").save(os.path.join(a.out, f"{name}_keys.png"))
    if not a.no_frame:
        tick = a.tick
        if tick is None:
            tick = timing.get("contact", (timing.get("active") or [0])[0])
        big_frame(frames, geom, timing, anim, tick, name).save(os.path.join(a.out, f"{name}_t{tick}.png"))
    return worst


if __name__ == "__main__":
    main()
