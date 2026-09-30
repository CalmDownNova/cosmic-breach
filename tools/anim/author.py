"""Writes the Cosmic Breach player animations (playerAnimator emotecraft JSON, version 3).

    python tools/anim/author.py            # every animation
    python tools/anim/author.py l1 l2      # only these (names without the meridian_/combat_ prefix work)

Each move is a list of key poses (see solver.py for the pose vocabulary) at the
ticks that matter: anticipation, contact on the active ticks, follow-through,
settle. Every game tick between them is solved (arms by IK so both fists stay on
the grip, legs held in the world, head facing ahead) and written as its own
keyframe with LINEAR easing, so the game shows exactly the solved pose on each
tick; the easing named on a key shapes the motion from that key to the next.
"""

from __future__ import annotations

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import palib  # noqa: E402
import rig  # noqa: E402
import solver  # noqa: E402
from moves import MOVES, READY_POSE  # noqa: E402

OUT_DIR = os.environ.get("ANIM_OUT") or os.path.join(rig.REPO, "src", "main", "resources", "assets",
                                                      "cosmicbreach", "player_animations")
TIMING_FILE = os.path.join(HERE, "timing.json")

PARTS = ("body", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem")


def mirror_item(frames):
    """leftItem = the rightItem the mirrored animation needs, run back through MirrorModifier's
    vector transform, so a mirrored play (the left dash) still holds the sword properly."""
    prev = None
    for t, ch in frames:
        rot, pos, prev = solver.mirrored_item(ch, prev)
        ch["leftItem"] = {"pitch": rot[0], "yaw": -rot[1], "roll": -rot[2],
                          "x": -pos[0], "y": pos[1], "z": pos[2]}


def _item_R(v):
    return solver.zyx(math.radians(v["pitch"]), math.radians(v["yaw"]), math.radians(v["roll"]))


def _other_branch(v):
    return {"pitch": v["pitch"] + 180.0, "yaw": 180.0 - v["yaw"], "roll": v["roll"] + 180.0}


def _same_values(a, b, tol=0.5):
    return all(abs(((a[k] - b[k] + 180.0) % 360.0) - 180.0) < tol for k in ("pitch", "yaw", "roll"))


def _mid_error(a, b):
    """Degrees between the rotation the game draws halfway between two ticks (linear Euler blend,
    after the library wraps every angle into [-180, 180)) and the true halfway rotation."""
    wrap = lambda x: ((x + 180.0) % 360.0) - 180.0  # noqa: E731
    mid = {k: wrap(a[k]) + (wrap(b[k]) - wrap(a[k])) * 0.5 for k in ("pitch", "yaw", "roll")}
    Ra, Rb, Rm = _item_R(a), _item_R(b), _item_R(mid)
    # true halfway: rotate Ra half way towards Rb about their relative axis
    rel = Ra.T @ Rb
    ang = math.acos(max(-1.0, min(1.0, (np.trace(rel) - 1.0) / 2.0)))
    if ang < 1e-6:
        half = Ra
    else:
        axis = np.array([rel[2, 1] - rel[1, 2], rel[0, 2] - rel[2, 0], rel[1, 0] - rel[0, 1]]) / (2 * math.sin(ang))
        half = Ra @ solver.R3(np.eye(4)) @ _axis_angle(axis, ang / 2.0)
    d = half.T @ Rm
    return math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(d) - 1.0) / 2.0))))


def _axis_angle(k, a):
    k = k / np.linalg.norm(k)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * K @ K


def reconcile_item_branch(frames, keys, last_spec, part="rightItem"):
    """Every move has to end on the same rightItem numbers it (and every other move) starts
    from, or the game's fades spin the sword. When the solved path crosses gimbal lock the
    per-tick values end on the other, equivalent Euler branch; switch the tail back at the
    tick boundary where that costs the least. A dual weapon's leftItem the same way."""
    canon = solver.solve(last_spec)[part]
    last = frames[-1][1][part]
    if _same_values(last, canon):
        return None
    if not _same_values(_other_branch(last), canon):
        return f"{part}: end pose differs from canonical beyond a branch flip"
    best = None
    for c in range(1, len(frames)):
        prev_v = frames[c - 1][1][part]
        flipped = _other_branch(frames[c][1][part])
        err = _mid_error(prev_v, flipped)
        if best is None or err < best[0]:
            best = (err, c)
    err, c = best
    for t, ch in frames[c:]:
        ri = ch[part]
        ri.update(_other_branch(ri))
    return f"{part} branch switched at tick {frames[c][0]} (between-tick error {err:.1f} deg)"


def _off_canonical(frames, last_spec, part):
    """True if the last frame's item is not the canonical end pose, even allowing an Euler branch flip."""
    canon = solver.solve(last_spec)[part]
    last = frames[-1][1][part]
    return not _same_values(last, canon) and not _same_values(_other_branch(last), canon)


def _settle_backward(frames, keys):
    """Re-solves the last key segment (the settle into the guard) backward from the canonical guard, so the move
    ends on exactly the guard's channel values; the switch of solution falls inside the moving segment."""
    end_spec = keys[-1][2]
    start = keys[-2][0]
    prev = solver.solve(end_spec)
    out = dict(frames)
    for t in range(keys[-1][0], start, -1):
        ch = solver.solve(solver.sample(keys, t), prev) if t != keys[-1][0] else prev
        out[t] = ch
        prev = ch
    return [(t, out[t]) for t, _ in frames]


def build(name, mv):
    rig.use_weapon(rig.weapon_for(name))
    keys = solver.resolve_twist(solver.expand(mv["keys"], mv.get("base", READY_POSE)))
    if any(any(k[2]["shR"]) or any(k[2]["shL"]) for k in keys):
        for k in keys:  # a shoulder moves somewhere: key the arm pivots on every tick
            k[2]["_arm_pos"] = True
    end = mv["end"]
    ticks = list(range(0, end + 1))
    prev = None
    if mv.get("continue_from"):
        # entered by a fade from another move's pose: start from that move's exact channel values
        # (same Euler branch), or the fade would swing the sword between equivalent numbers
        src_name, src_tick = mv["continue_from"]
        _, src_frames = build(src_name, MOVES[src_name])
        prev = dict(src_frames[src_tick][1])
    frames = solver.timeline(keys, ticks, prev)
    if mv.get("mirror_safe"):
        mirror_item(frames)
    solver.unwrap(frames)
    note = None
    if not mv.get("loop"):
        parts = ("rightItem", "leftItem") if rig.is_dual() else ("rightItem",)
        if rig.is_dual() and any(_off_canonical(frames, keys[-1][2], part) for part in parts):
            # the arms came round onto the other IK solution: solve the settle back from the guard itself
            frames = _settle_backward(frames, keys)
            solver.unwrap(frames)
        for part in parts:
            note = reconcile_item_branch(frames, keys, keys[-1][2], part)
            solver.unwrap(frames)
            if note:
                print(f"  {name}: {note}")
        # the library blends out from the raw last value: no leftover whole turns at the end
        for part, axes in frames[-1][1].items():
            if part.startswith("_"):
                continue
            for k in ("pitch", "yaw", "roll"):
                if k in axes:
                    shift = 360.0 * round(axes[k] / 360.0)
                    if shift:
                        for t, ch in frames:
                            ch[part][k] -= shift
    moves = []
    last_t = ticks[-1]
    for t, ch in frames:
        m = {"tick": t, "easing": mv.get("tail_ease", "INOUTSINE") if t == last_t else "LINEAR"}
        for part in PARTS:
            if part in ch:
                m[part] = {k: round(float(v), 4 if part == "body" and k in "xyz" else 3) for k, v in ch[part].items()}
        moves.append(m)
    loop = mv.get("loop")
    if loop:
        ret = loop["ret"]
        doc = palib.emotecraft_json(name, end, moves, loop=True, ret=ret, stop=end + 1,
                                    description=mv.get("desc", ""))
    else:
        doc = palib.emotecraft_json(name, end, moves, stop=end + mv.get("blend_out", 3),
                                    description=mv.get("desc", ""))
    return doc, frames


def write(names=None):
    os.makedirs(OUT_DIR, exist_ok=True)
    timing = {}
    if names and os.path.exists(TIMING_FILE):  # partial run: keep the other entries
        with open(TIMING_FILE, encoding="utf-8") as fh:
            timing = json.load(fh)
    for name, mv in MOVES.items():
        short = name.replace("meridian_", "").replace("combat_", "")
        if names and name not in names and short not in names:
            continue
        doc, frames = build(name, mv)
        path = os.path.join(OUT_DIR, name + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(dump(doc))
        # the file must load back exactly the way the library would load it
        back = palib.load(path)[0]
        assert back.name == name, (back.name, name)
        timing[name] = {k: mv[k] for k in ("startup", "active", "contact") if k in mv}
        timing[name]["end"] = mv["end"]
        bad = [t for t, ch in frames if not ch["_info"]["reach_ok"]]
        print(f"{name:32s} end {doc['emote']['endTick']:3d} stop {doc['emote']['stopTick']:3d} "
              f"loop {doc['emote']['isLoop']!s:5s} moves {len(doc['emote']['moves']):3d}"
              + (f"  UNREACHABLE grip at ticks {bad}" if bad else ""))
    with open(TIMING_FILE, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(timing, fh, indent=1, sort_keys=True)
        fh.write("\n")


def dump(doc):
    """Compact but diff-friendly: one move per line."""
    e = doc["emote"]
    head = {k: v for k, v in doc.items() if k != "emote"}
    lines = ["{"]
    for k, v in head.items():
        lines.append(f"  {json.dumps(k)}: {json.dumps(v)},")
    lines.append('  "emote": {')
    for k, v in e.items():
        if k != "moves":
            lines.append(f"    {json.dumps(k)}: {json.dumps(v)},")
    lines.append('    "moves": [')
    for i, m in enumerate(e["moves"]):
        lines.append("      " + json.dumps(m, separators=(", ", ": ")) + ("," if i < len(e["moves"]) - 1 else ""))
    lines.append("    ]")
    lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    write(sys.argv[1:] or None)
