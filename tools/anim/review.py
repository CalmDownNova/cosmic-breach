"""Renders the review set for every animation (or the ones named):

    python tools/anim/review.py [names...] [--src DIR] [--out DIR]

For each: <name>_strip.png (every tick), <name>_t<contact>.png (large contact
frame), <name>_keys.png (the key poses big), plus the check table. Ends with
previews/contact_sheet.png: the contact frame of every animation side by side.
"""

from __future__ import annotations

import argparse
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import preview  # noqa: E402
import render  # noqa: E402
import rig  # noqa: E402
from moves import MOVES  # noqa: E402

SRC = os.path.join(rig.REPO, "src", "main", "resources", "assets", "cosmicbreach", "player_animations")


def key_ticks(mv):
    ticks = sorted({k[0] for k in mv["keys"] if k[0] <= mv["end"]})
    if len(ticks) > 7:
        step = max(1, len(ticks) // 7)
        ticks = ticks[::step][:7]
    return ticks


def contact_tick(name, mv):
    if "contact" in mv:
        return mv["contact"]
    if mv.get("loop"):
        return mv["loop"]["ret"]
    return 2


def contact_sheet(src, out, names):
    """One column per animation: its contact frame from the side, from behind and in first person."""
    sz = 170
    cols = []
    for name in names:
        mv = MOVES[name]
        rig.use_weapon(rig.weapon_for(name))
        geom = rig.load_item()
        anim, source = preview.make_source(os.path.join(src, name + ".json"))
        t = contact_tick(name, mv)
        frames = preview.Playback(source).frames(t + 1)
        posed = frames[t][2]
        views = render.standard_views(sz, sz, scale=sz / 3.6)
        sc = render.build_scene(posed, geom)
        a = render.render(sc, views["side"])
        b = render.render(sc, render.three_quarter(sz, sz, sz / 3.6))
        c = render.render(sc, views["front"])
        f = render.render(render.build_scene(posed, geom, parts=("rightArm", "leftArm")),
                          render.first_person(sz, int(sz * 0.62), pitch_deg=mv.get("fp_pitch", preview.FP_PITCH)),
                          bg=np.array([0.80, 0.86, 0.93]))
        cols.append((name, t, [a, b, c, f]))
    per_row = 8
    rows = (len(cols) + per_row - 1) // per_row
    col_h = 34 + sz * 3 + int(sz * 0.62) + 8
    img = Image.new("RGB", (40 + per_row * sz, 30 + rows * col_h), (250, 250, 247))
    d = ImageDraw.Draw(img)
    d.text((8, 6), "Contact frames (side / behind / front / first person). Contact = first active tick; "
                   "loops show their hold pose.", fill=(0, 0, 0), font=render.FONT_L)
    for i, (name, t, ims) in enumerate(cols):
        r, c = divmod(i, per_row)
        x, y = 40 + c * sz, 30 + r * col_h
        d.text((x + 3, y + 2), name.replace("meridian_", "m_").replace("combat_", "c_").replace("maul_", "cm_"), fill=(0, 0, 0),
               font=render.FONT)
        d.text((x + 3, y + 17), f"tick {t}", fill=(90, 90, 90), font=render.FONT_S)
        yy = y + 34
        for im in ims:
            img.paste(im, (x, yy))
            yy += im.size[1]
    for r in range(rows):
        y = 30 + r * col_h + 34
        for lab, h in (("side", sz), ("behind", sz), ("front", sz), ("fp", int(sz * 0.62))):
            d.text((4, y + h // 2 - 6), lab, fill=(60, 60, 60), font=render.FONT_S)
            y += h
    img.save(os.path.join(out, "contact_sheet.png"))


# Transitions the combat rules allow (CombatRules: a light attack held 6 ticks flows into the charge;
# an ability cancels from recovery tick 0; dash and parry from recovery tick 2; parry success comes
# out of the guard; the dash attack out of a dash; the landing out of the dive). Checked the way
# ModifierLayer.replaceAnimationWithFade(standardFadeIn(2, INOUTSINE)) blends them.
LIGHTS = {"meridian_l1": 5, "meridian_l2": 5, "meridian_l3": 7}  # recovery start tick
MAUL_LIGHTS = {"maul_l1": 9, "maul_l2": 9, "maul_l3": 13}  # the Comet Maul's (startup + active)


def transition_cases():
    cases = []
    for n, r in LIGHTS.items():
        cases += [(n, t, "meridian_charge") for t in range(6, 9)]
        cases.append((n, r, "meridian_zenith"))
        cases += [(n, r + 2, b) for b in ("combat_dash_forward", "combat_dash_back", "combat_dash_side", "combat_parry")]
        cases.append((n, 3, "combat_stagger"))
    cases += [("combat_parry", t, "combat_parry_success") for t in (1, 2, 3, 4)]
    cases += [("meridian_charge", t, "meridian_line") for t in (0, 7, 15)]
    cases += [("meridian_falling_star", t, "meridian_falling_star_land") for t in (2, 7, 12)]
    cases += [(d, t, "meridian_pass") for d in ("combat_dash_forward", "combat_dash_side") for t in (4, 6, 8)]
    cases += [("meridian_line", 5, "combat_stagger"), ("meridian_l1", 10, "meridian_l2"),
              ("meridian_l2", 11, "meridian_l3"), ("meridian_l3", 16, "meridian_l1")]
    # the Comet Maul: its chain, charge, dash-in Ram, landing, and the engine's shared moves out of its recovery
    # (the Maul plays its own dash forward, dash back and stagger: WeaponDef "animations")
    for n, r in MAUL_LIGHTS.items():
        cases += [(n, t, "maul_charge") for t in (r, r + 1, r + 2)]  # the charge takes over in recovery
        cases.append((n, r, "maul_well"))
        cases += [(n, r + 2, b) for b in ("maul_dash_forward", "maul_dash_back", "combat_dash_side", "combat_parry")]
        cases.append((n, 3, "maul_stagger"))
    cases += [("maul_charge", t, "maul_crater") for t in (0, 7, 15)]
    cases += [("maul_meteorfall", t, "maul_meteorfall_land") for t in (3, 8, 12)]
    cases += [(d, t, "maul_ram") for d in ("maul_dash_forward", "combat_dash_side") for t in (4, 6, 8)]
    cases += [("maul_well", 20, "maul_dash_forward"), ("maul_crater", 6, "maul_stagger"),
              ("maul_l1", 19, "maul_l2"), ("maul_l2", 20, "maul_l3"), ("maul_l3", 27, "maul_l1")]
    # the Binary Edges: the chain (each move pressed in the last ticks of the one before), the charge out of a
    # light attack, dashes, parry, stagger and the Tether out of recovery, the Weave into L4, the Scissor out of
    # a dash, the Orbit out of the charge, the dive into its landing, the blink after the throw
    edges_chain = [("edges_l1", 6), ("edges_l2", 6), ("edges_l3", 8), ("edges_l4", 8), ("edges_l5", 14)]
    for (a, end), (b, _) in zip(edges_chain, edges_chain[1:] + edges_chain[:1]):
        cases += [(a, end - 1, b), (a, end, b)]
    for n, end in edges_chain:
        cases.append((n, 5, "edges_charge"))
        cases += [(n, min(end, 5), x) for x in ("edges_dash_forward", "edges_dash_back", "edges_dash_side", "edges_parry",
                                                "edges_tether", "edges_stagger")]
    cases += [("edges_charge", t, "edges_orbit") for t in (0, 7, 15)]
    cases += [("edges_dash_forward", t, "edges_scissor") for t in (4, 6, 8)]
    cases += [("edges_dash_side", t, "edges_scissor") for t in (4, 6, 8)]
    cases += [(d, 8, "edges_l4") for d in ("edges_dash_forward", "edges_dash_back", "edges_dash_side")]
    cases += [("edges_meteor", t, "edges_meteor_land") for t in (3, 8, 20)]
    cases += [("edges_parry", t, "edges_parry_success") for t in (1, 2, 3, 4)]
    cases += [("edges_tether", 11, "edges_blink"), ("edges_blink", 14, "edges_l1"), ("edges_orbit", 20, "edges_l1")]
    return cases


def check_transitions(src, fade_ticks=2):
    import palib
    cache = {}

    def load(n):
        if n not in cache:
            cache[n] = palib.load(os.path.join(src, n + ".json"))[0]
        return cache[n]

    bad = []
    for a, at, b in transition_cases():
        rig.use_weapon("comet_maul" if a.startswith("maul_") or b.startswith("maul_")
                       else "binary_edges" if a.startswith("edges_") or b.startswith("edges_") else "meridian")
        geom = rig.load_item()
        prev = palib.Player(load(a))
        for _ in range(at):
            prev.tick()
        f = palib.Fade(prev, palib.Player(load(b)), fade_ticks)
        worst = 0.0
        for t in range(fade_ticks + 1):
            for d in (0.0, 0.25, 0.5, 0.75):
                f.setup(d)
                c = preview.checks(rig.pose_from(f), geom)
                worst = max(worst, sum(c["clip"].values()) + sum(c["armIn"].values()))
            f.setup(0.0)
            f.tick()
        if worst > 0.5:
            bad.append((a, at, b, worst))
    return len(transition_cases()), bad


def check_all(src):
    """Between-tick clipping and grip, blade smoothness, then the transitions."""
    print("\n== between ticks (what the game draws at 60+ fps)")
    print("   left fist = farthest the left fist gets from the grip; moves that let go on purpose are marked")
    for name, mv in MOVES.items():
        path = os.path.join(src, name + ".json")
        runs = [(False, path)] + ([(True, path)] if mv.get("mirror_safe") else [])
        lets_go = any(k[2].get("grip", 1.0) < 1.0 for k in mv["keys"])
        for mirror, pth in runs:
            w = preview.half_tick_report(pth, mirror=mirror)
            body = [x for x in w if x[0] < mv["end"]]
            clips = [x for x in body if x[3] > 0.3]
            grip = max((x[1] for x in body), default=0.0)
            fl = preview.flip_report(pth, mirror=mirror)
            tag = name + (" (mirrored)" if mirror else "")
            print(f"  {tag:34s} clips {len(clips):2d}  left fist {grip:4.1f}px{' (lets go)' if lets_go else '          '}  "
                  f"blade path worst {fl[0][1] if fl else 0:4.1f} deg at t{fl[0][0] if fl else 0}")
    n, bad = check_transitions(src)
    print(f"\n== transitions: {n} fades checked, {len(bad)} clip")
    for a, at, b, w in bad:
        print(f"  {a}@{at} -> {b}: {w:.1f}px")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--src", default=SRC)
    ap.add_argument("--out", default=os.path.join(HERE, "previews"))
    ap.add_argument("--no-sheet", action="store_true")
    a = ap.parse_args()
    names = [n for n in MOVES if not a.names or n in a.names or n.split("_", 1)[1] in a.names]
    os.makedirs(a.out, exist_ok=True)
    for name in names:
        mv = MOVES[name]
        path = os.path.join(a.src, name + ".json")
        args = [path, "--out", a.out, "--tick", str(contact_tick(name, mv)),
                "--sheet", ",".join(str(t) for t in key_ticks(mv)), "--fp-pitch", str(mv.get("fp_pitch", 15.0))]
        preview.main(args)
    if not a.no_sheet and not a.names:
        contact_sheet(a.src, a.out, list(MOVES))
        check_all(a.src)


if __name__ == "__main__":
    main()
