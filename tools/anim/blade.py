"""Where the blade is: the held sword's guard and tip on every tick of an animation.

    python tools/anim/blade.py                    # every animation, as a table
    python tools/anim/blade.py l1 charge --json   # these, as JSON

Points are (right, up, forward) in blocks from the player's feet, in the frame of the
body's yaw (vanilla's yBodyRot, which follows the head while an animation plays).
In the world: feet + right * (-cos(yaw), 0, -sin(yaw)) + up * (0, 1, 0)
+ forward * (-sin(yaw), 0, cos(yaw)), with yaw in Minecraft degrees (0 = south).
First person draws the same model moved and turned about the eye (see FirstPersonView
in client/anim): add --first-person to get the points as the camera sees them there, in
the camera's frame (right, up, forward from the eye) at a given --pitch.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import preview  # noqa: E402
import rig  # noqa: E402
from moves import MOVES  # noqa: E402

SRC = os.path.join(rig.REPO, "src", "main", "resources", "assets", "cosmicbreach", "player_animations")
HIP = 0.7


def first_person(point, push, drop, frame_pitch, pitch):
    """A local point as the first-person camera sees it (FirstPersonView.recompose, then the camera):
    moved forward and down, turned about the eye as if looking frame_pitch down, then seen from the
    eye looking pitch down. Returns (right, up, forward) from the eye in the camera frame."""
    eye = np.array([0.0, rig.EYE_HEIGHT, 0.0])
    p = np.asarray(point, dtype=float) + np.array([0.0, -drop, -push]) - eye
    if frame_pitch is not None:
        t = math.radians(pitch - frame_pitch)  # the model turns with the camera, less the framing
        c, s = math.cos(-t), math.sin(-t)
        p = np.array([p[0], c * p[1] - s * p[2], s * p[1] + c * p[2]])
    a = math.radians(pitch)  # camera looking pitch down: undo it
    c, s = math.cos(a), math.sin(a)
    q = np.array([p[0], c * p[1] - s * p[2], s * p[1] + c * p[2]])
    return q[0], q[1], -q[2]


def points(name, fp=None):
    path = os.path.join(SRC, name + ".json")
    anim, src = preview.make_source(path)
    rig.use_weapon(rig.weapon_for(name))
    n = anim.end + 1 if anim.infinite else anim.stop + 1
    geom = rig.load_item()
    rows = []
    for t, active, posed in preview.Playback(src).frames(n):
        if not active or posed.item is None:
            continue
        ip = rig.item_points(posed, geom)
        row = {"tick": t}
        for k in ("guard", "tip"):
            x, y, z = ip[k]
            row[k] = [round(float(x), 3), round(float(y), 3), round(float(-z), 3)]
            if fp is not None:
                row[k + "_fp"] = [round(float(v), 3) for v in first_person(ip[k], *fp)]
        rows.append(row)
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--first-person", action="store_true", help="also the first-person camera frame")
    ap.add_argument("--push", type=float, default=0.3)
    ap.add_argument("--drop", type=float, default=0.1)
    ap.add_argument("--frame-pitch", type=float, default=35.0, help="framing pitch; negative for none")
    ap.add_argument("--pitch", type=float, default=10.0, help="camera pitch, down positive")
    a = ap.parse_args()
    fp = None
    if a.first_person:
        fp = (a.push, a.drop, None if a.frame_pitch < 0 else a.frame_pitch, a.pitch)
    names = [n for n in MOVES if not a.names or n in a.names or n.split("_", 1)[1] in a.names]
    out = {n: points(n, fp) for n in names}
    if a.json:
        print(json.dumps(out, indent=1))
        return
    for n, rows in out.items():
        print(f"== {n}: guard and tip, (right, up, forward) blocks from the feet")
        for r in rows:
            line = f"  t{r['tick']:>2}  guard {r['guard']}  tip {r['tip']}"
            if fp is not None:
                line += f"  | first person from the eye: guard {r['guard_fp']}  tip {r['tip_fp']}"
            print(line)


if __name__ == "__main__":
    main()
