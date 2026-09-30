"""Choreography for the Binary Edges (twin sickles, GDD 4.2): a sickle in each hand (dual=True, see solver.py for
the two-blade vocabulary: hands/sword for the right, handsL/swordL for the left).

Fast and flowing, the body low: a wide stance (rigid legs: the hips come down as the legs spread), a forward
lean, both sickles held up in front at chest height with their crescents leaning forward, like a boxer's guard.
Every move starts and ends on that guard (EDGES_READY_SYM). Frame data from
data/cosmicbreach/combat/moves/binary_edges/: the contact pose is on the move's first active tick.

Rolls: 45 on the right blade (-45 on the left) tips the crescent forward; a strike leads with its edge ("lead").
The engine's own moves (dashes, parry, stagger) have Edges versions (edges_dash_*, edges_parry*, edges_stagger)
because Meridian's put the left fist on a two-handed grip.
"""

from __future__ import annotations

from moves import READY_POSE

# ----------------------------------------------------------------------------- the guard

EDGES_READY_POSE = dict(READY_POSE, dual=True, lean=16.0, twist=0.0, tilt=0.0,
                        legR=(20.0, 16.0, 14.0), legL=(-22.0, 16.0, 22.0),
                        hands=(6.0, -3.0, 5.0), handsL=(-5.5, -3.5, 5.5),
                        sword=(15.0, 50.0, 45.0), swordL=(-15.0, 50.0, -45.0),
                        rz=None, rzL=None, shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0), true_edge=True)
EDGES_READY = {k: EDGES_READY_POSE[k] for k in ("dual", "lean", "twist", "tilt", "fwd", "side", "rise", "legR", "legL",
                                               "hands", "handsL", "sword", "swordL", "rz", "rzL", "shR", "shL")}
EDGES_READY_SYM = dict(EDGES_READY, canonical=True)


def mirrored(keys):
    """The mirror image of a move's keys (left for right): L2 is L1 mirrored."""
    out = []
    for tick, ease, ch in keys:
        m = dict(ch)
        for k in ("twist", "tilt", "side"):
            if k in ch:
                m[k] = -ch[k]
        _swap(m, ch, "hands", "handsL", lambda v: (-v[0], v[1], v[2]))
        _swap(m, ch, "sword", "swordL", _mirror_blade)
        _swap(m, ch, "legR", "legL", lambda v: v)
        _swap(m, ch, "shR", "shL", lambda v: (-v[0], v[1], v[2]))
        _swap(m, ch, "roll_ofs", "roll_ofsL", lambda v: -v)
        _swap(m, ch, "rz", "rzL", lambda v: None if v is None else -v)
        out.append((tick, ease, m))
    return out


def _swap(m, ch, a, b, f):
    if a in ch or b in ch:
        m.pop(a, None)
        m.pop(b, None)
        if b in ch:
            m[a] = f(ch[b])
        if a in ch:
            m[b] = f(ch[a])


def _mirror_blade(blade):
    y, p, r = blade
    return (-y, p, -r if isinstance(r, (int, float)) else r)


def move(desc, end, keys, startup=None, active=None, contact=None, **extra):
    d = dict(desc=desc, end=end, base=EDGES_READY_POSE, keys=keys, **extra)
    if startup is not None:
        d.update(startup=startup, active=active, contact=contact)
    return d


START = (0, "OUTSINE", {})


def settle(tick):
    return (tick, "INOUTSINE", EDGES_READY_SYM)


# ----------------------------------------------------------------------------- the light chain

# L1 Left Hook, 2 / 1 / 3: the left sickle swings wide out to the left and hooks across the front to the right,
# crescent first, the hips turning with it, low; the right blade stays in its guard.
EDGES_L1 = move(
    "Left Hook: the left sickle hooks across from the left, body low. 6 ticks, active 2.", 6, [
        START,
        (1, "INQUAD", dict(twist=-34.0, lean=17.0, handsL=(-9.0, -1.5, 2.0), swordL=(-112.0, 8.0, "lead"),
                           hands=(4.0, -3.5, 4.5), sword=(12.0, 44.0, 45.0), legR=(19.0, 16.0, 10.0), legL=(-23.0, 16.0, 28.0))),
        (2, "LINEAR", dict(twist=4.0, lean=19.0, handsL=(-1.5, -2.5, 7.5), swordL=(-8.0, 6.0, "lead"), hands=(5.0, -3.5, 4.5))),
        (3, "OUTQUAD", dict(twist=28.0, lean=17.0, handsL=(5.0, -3.0, 6.0), swordL=(62.0, 2.0, "lead"),
                            legR=(21.0, 16.0, 20.0), legL=(-21.0, 16.0, 16.0))),
        (4, "INOUTSINE", dict(twist=27.0, swordL=(68.0, 6.0, "prev"))),
        settle(6),
    ], startup=2, active=[2, 2], contact=2)

EDGES_L2 = move(
    "Right Hook: the mirror of the Left Hook, the right sickle hooking across from the right. 6 ticks, active 2.", 6,
    [START] + mirrored(EDGES_L1["keys"][1:-1]) + [settle(6)], startup=2, active=[2, 2], contact=2)

# L3 Cross Cut, 2 / 2 / 4: arms crossed high, then both blades cut outward and down in an X, the right one a tick
# ahead of the left (two hits, two rings).
EDGES_L3 = move(
    "Cross Cut: both blades cross outward in an X, the right a tick ahead. 8 ticks, active 2-3.", 8, [
        START,
        (1, "INQUAD", dict(lean=8.0, twist=-4.0, hands=(-3.0, 2.5, 5.0), handsL=(3.0, 3.0, 5.0),
                           sword=(-34.0, 72.0, "lead"), swordL=(34.0, 72.0, "lead"), shR=(0.0, -1.0, -0.5), shL=(0.0, -1.0, -0.5))),
        (2, "LINEAR", dict(lean=18.0, twist=6.0, hands=(5.0, -2.5, 6.5), sword=(58.0, -6.0, "lead"),
                           handsL=(1.0, 0.5, 6.0), swordL=(12.0, 34.0, "lead"), shR=(0.0, 0.0, -1.0), shL=(0.0, -0.5, -1.0))),
        (3, "LINEAR", dict(lean=20.0, twist=-2.0, hands=(7.5, -4.0, 4.5), sword=(84.0, -22.0, "lead"),
                           handsL=(-6.5, -3.5, 6.0), swordL=(-62.0, -8.0, "lead"), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, -1.0))),
        (4, "OUTQUAD", dict(lean=19.0, handsL=(-8.0, -4.5, 4.5), swordL=(-86.0, -22.0, "lead"), shL=(0.0, 0.0, 0.0))),
        (5, "INOUTSINE", dict(sword=(86.0, -20.0, "prev"), swordL=(-88.0, -20.0, "prev"))),
        settle(8),
    ], startup=2, active=[2, 3], contact=2)

# L4 Lunge, 3 / 1 / 4, moves 2 blocks: both hands drawn back to the hips, then a long low step with both blades
# thrust forward together.
EDGES_L4 = move(
    "Lunge: both blades drawn back, then thrust forward together with a long low step. 8 ticks, active 3.", 8, [
        START,
        (1, "INQUAD", dict(lean=10.0, hands=(5.0, -6.0, -1.0), handsL=(-5.0, -6.5, -1.0), sword=(10.0, -10.0, 90.0),
                           swordL=(-10.0, -10.0, -90.0), legR=(12.0, 14.0, 12.0), legL=(-14.0, 14.0, 20.0))),
        (2, "INQUAD", dict(lean=16.0, hands=(4.5, -5.5, 2.0), handsL=(-4.5, -6.0, 2.0), fwd=1.0)),
        (3, "LINEAR", dict(lean=26.0, hands=(3.0, -1.5, 9.0), handsL=(-3.0, -2.0, 9.0), sword=(4.0, 6.0, 90.0),
                           swordL=(-4.0, 6.0, -90.0), shR=(0.0, 0.0, -1.5), shL=(0.0, 0.0, -1.5), fwd=3.0,
                           legR=(40.0, 8.0, 8.0), legL=(-36.0, 8.0, 16.0))),
        (4, "OUTQUAD", dict(lean=28.0, hands=(3.0, -2.0, 9.5), handsL=(-3.0, -2.5, 9.5), fwd=3.5)),
        (5, "INOUTSINE", dict(lean=24.0, fwd=3.0)),
        (8, "INOUTSINE", dict(EDGES_READY_SYM, fwd=0.0)),
    ], startup=3, active=[3, 3], contact=3)


def _gyre_keys():
    """One full turn to the left with both arms out, the blades trailing (the whirl), then back to the guard."""
    out = [START,
           (2, "INQUAD", dict(twist=40.0, lean=12.0, hands=(9.0, -2.0, 1.0), handsL=(-9.0, -2.0, 1.0),
                              sword=(100.0, 4.0, "lead"), swordL=(-100.0, 4.0, "lead"),
                              legR=(18.0, 16.0, 54.0), legL=(-20.0, 16.0, 62.0), head_yaw=0.0, head_follow=1.0))]
    for t, twist in ((3, -40.0), (4, -150.0), (5, -260.0), (6, -345.0)):
        out.append((t, "LINEAR", dict(twist=twist, lean=12.0, hands=(9.5, -1.5, 1.5), handsL=(-9.5, -1.5, 1.5),
                                      sword=(108.0, 2.0, "lead"), swordL=(-108.0, 2.0, "lead"),
                                      legR=(18.0, 16.0, twist + 14.0), legL=(-20.0, 16.0, twist + 22.0),
                                      head_yaw=0.0, head_follow=0.0)))
    out.append((8, "OUTQUAD", dict(twist=-368.0, lean=16.0, hands=(8.0, -3.0, 3.0), handsL=(-8.0, -3.0, 3.0),
                                   sword=(80.0, 10.0, "lead"), swordL=(-80.0, 10.0, "lead"),
                                   legR=(20.0, 16.0, -368.0 + 14.0), legL=(-22.0, 16.0, -368.0 + 22.0),
                                   head_yaw=0.0, head_follow=0.6)))
    out.append((14, "INOUTSINE", dict(EDGES_READY_SYM, twist=-360.0, legR=(20.0, 16.0, -360.0 + 14.0),
                                      legL=(-22.0, 16.0, -360.0 + 22.0), head_yaw=0.0, head_follow=1.0)))
    return out


EDGES_L5 = move(
    "Gyre: a full turn with both arms out, the blades whirling round and trailing. 14 ticks, active 3-5.", 14,
    _gyre_keys(), startup=3, active=[3, 5], contact=3)

# ----------------------------------------------------------------------------- the charge and the Binary Orbit

EDGES_CHARGE_POSE = dict(lean=22.0, twist=0.0, hands=(2.0, -6.0, 5.0), handsL=(-2.0, -6.5, 5.0),
                         sword=(-40.0, -30.0, 0.0), swordL=(40.0, -30.0, 0.0), legR=(22.0, 18.0, 16.0), legL=(-24.0, 18.0, 24.0))


def _charge_keys():
    """1 s loop: crouched low, both sickles crossed down in front, a breath and a tremble in the hands."""
    keys = []
    rnd = [0.0, 0.7, -0.5, 0.8, -0.6, 0.4, 0.9, -0.7, 0.3, -0.9, 0.6, -0.2, 0.8, -0.5, 0.2, -0.8, 0.5, -0.4, 0.9, -0.6]
    import math
    for t in range(0, 21):
        k = t % 20
        breath = math.sin(2 * math.pi * k / 20.0)
        amp = 1.0 if k else 0.0
        p = dict(EDGES_CHARGE_POSE)
        p["lean"] = EDGES_CHARGE_POSE["lean"] + 1.2 * breath
        y, pi, r = EDGES_CHARGE_POSE["sword"]
        p["sword"] = (y + 1.5 * rnd[k] * amp, pi + 1.2 * rnd[(k + 7) % 20] * amp, r)
        y, pi, r = EDGES_CHARGE_POSE["swordL"]
        p["swordL"] = (y - 1.5 * rnd[(k + 3) % 20] * amp, pi + 1.2 * rnd[(k + 11) % 20] * amp, r)
        keys.append((t, "LINEAR", p))
    return keys


EDGES_CHARGE = move("Charge hold: crouched low, both sickles crossed down in front, trembling. 1 s loop.", 19,
                    _charge_keys(), loop=dict(ret=0), tail_ease="LINEAR")

# The Orbit, 2 / 10 / 8: out of the crouch both arms fling the sickles wide (they leave the hands at tick 1.6 and
# circle on their chains, drawn by EdgesVisuals), the hands conduct them round, then catch them at tick 14.
EDGES_ORBIT = move(
    "Binary Orbit: both sickles flung out to circle the body on their chains, the arms wide, caught again. "
    "20 ticks, active 2-11.", 20, [
        (0, "OUTQUAD", {}),
        (1, "OUTQUAD", dict(lean=6.0, hands=(9.0, 0.0, 3.0), handsL=(-9.0, 0.0, 3.0), sword=(110.0, 20.0, 0.0),
                            swordL=(-110.0, 20.0, 0.0), legR=(18.0, 16.0, 14.0), legL=(-20.0, 16.0, 22.0))),
        (3, "INOUTSINE", dict(lean=8.0, hands=(9.5, 1.0, 1.0), handsL=(-9.5, 1.0, 1.0), twist=-8.0)),
        (6, "INOUTSINE", dict(lean=10.0, hands=(9.5, 0.0, 2.0), handsL=(-9.5, 0.0, 2.0), twist=8.0)),
        (9, "INOUTSINE", dict(lean=8.0, hands=(9.5, 1.0, 1.0), handsL=(-9.5, 1.0, 1.0), twist=-8.0)),
        (12, "INOUTSINE", dict(lean=10.0, twist=0.0, hands=(8.0, -1.0, 3.0), handsL=(-8.0, -1.0, 3.0))),
        (14, "OUTQUAD", dict(lean=14.0, hands=(6.5, -2.5, 4.5), handsL=(-6.0, -3.0, 5.0), sword=(20.0, 46.0, 45.0),
                             swordL=(-20.0, 46.0, -45.0))),
        settle(20),
    ], startup=2, active=[2, 11], contact=2, base_extra=EDGES_CHARGE_POSE)

# ----------------------------------------------------------------------------- the Twin Meteor

EDGES_DIVE = dict(air=True, lean=30.0, hands=(6.0, -3.0, 6.5), handsL=(-6.0, -3.0, 6.5), sword_world=True,
                  sword=(42.0, -62.0, 90.0), swordL_world=True, swordL=(-42.0, -62.0, -90.0),
                  legR=(58.0, 10.0, 4.0), legL=(66.0, 10.0, 4.0))


def _dive_keys():
    """A tuck, then a spin (two turns over twelve ticks, both blades down), held tucked until the landing."""
    keys = [(0, "OUTQUAD", dict(air=True)), (2, "LINEAR", dict(EDGES_DIVE, twist=0.0))]
    for i in range(1, 7):
        keys.append((2 + 2 * i, "LINEAR", dict(EDGES_DIVE, twist=-120.0 * i)))
    keys.append((40, "LINEAR", dict(EDGES_DIVE, twist=-720.0)))
    return keys


EDGES_METEOR = move("Twin Meteor dive: tucked, both blades down, spinning twice, held until the landing.", 40,
                    _dive_keys(), tail_ease="LINEAR", fp_pitch=60.0)

EDGES_METEOR_LAND = move(
    "Twin Meteor landing: both blades driven into the ground in front, a deep crouch, back to the guard. 8 ticks.", 8, [
        (0, "OUTQUAD", dict(air=False, plant2=True, lean=32.0, hands=(2.5, -7.5, 6.0), handsL=(-2.5, -7.5, 6.0),
                            sword_world=True, sword=(6.0, -70.0, 90.0), swordL_world=True, swordL=(-6.0, -70.0, -90.0),
                            legR=(30.0, 20.0, 12.0), legL=(-32.0, 20.0, 22.0))),
        (2, "INOUTSINE", dict(lean=35.0, hands=(2.5, -8.0, 6.0), handsL=(-2.5, -8.0, 6.0))),
        (5, "INOUTSINE", dict(lean=24.0, sword_world=False, swordL_world=False, sword=(10.0, 20.0, 45.0),
                              swordL=(-10.0, 20.0, -45.0), hands=(5.0, -4.5, 5.0), handsL=(-5.0, -5.0, 5.0))),
        (8, "INOUTSINE", dict(EDGES_READY_SYM, rise=0.0)),
    ], startup=0, active=[0, 0], contact=0, base_extra=EDGES_DIVE)

# ----------------------------------------------------------------------------- the Scissor

EDGES_SCISSOR = move(
    "Scissor: out of the dash both arms open wide and snap shut through the target in an X. 8 ticks, active 1-2.", 8, [
        (0, "LINEAR", dict(lean=22.0, hands=(9.0, -0.5, 2.5), handsL=(-9.0, -0.5, 2.5), sword=(104.0, 12.0, "lead"),
                           swordL=(-104.0, 12.0, "lead"), legR=(28.0, 12.0, 8.0), legL=(-30.0, 12.0, 16.0))),
        (1, "LINEAR", dict(lean=24.0, hands=(3.5, 0.5, 8.5), handsL=(-3.5, 0.5, 8.5), sword=(-10.0, 12.0, "lead"),
                           swordL=(10.0, 12.0, "lead"), fwd=2.0)),
        (2, "OUTQUAD", dict(lean=22.0, hands=(-3.0, -1.0, 7.0), handsL=(3.0, -1.5, 7.0), sword=(-72.0, 6.0, "lead"),
                            swordL=(72.0, 6.0, "lead"), fwd=2.5)),
        (4, "INOUTSINE", dict(lean=20.0, sword=(-76.0, 4.0, "prev"), swordL=(76.0, 4.0, "prev"))),
        (8, "INOUTSINE", dict(EDGES_READY_SYM, fwd=0.0)),
    ], startup=1, active=[1, 2], contact=1)

# ----------------------------------------------------------------------------- the Tether and the blink

# The throw, 3 / 1 / 7: turned right with the left arm cocked back high, then a whipping throw across the front;
# the left sickle leaves the hand on tick 3 (the off hand is empty from then on).
EDGES_TETHER = move(
    "Tether throw: the left arm cocked back high, then a whipping throw; the sickle leaves the hand on tick 3. "
    "11 ticks, active 3.", 11, [
        START,
        (1, "INQUAD", dict(twist=30.0, lean=10.0, handsL=(-8.5, 4.0, -1.5), swordL=(-95.0, 100.0, -45.0),
                           shL=(0.0, -1.5, 0.0), hands=(4.0, -3.0, 5.0), legR=(18.0, 16.0, 10.0), legL=(-22.0, 16.0, 30.0))),
        (2, "INQUAD", dict(twist=24.0, lean=12.0, handsL=(-8.0, 5.0, 0.5), swordL=(-80.0, 96.0, -45.0))),
        (3, "LINEAR", dict(twist=-18.0, lean=20.0, handsL=(-1.0, 1.0, 9.0), swordL=(0.0, 20.0, -45.0), shL=(0.0, 0.0, -1.5))),
        (5, "OUTQUAD", dict(twist=-26.0, lean=22.0, handsL=(3.0, -3.0, 7.0), shL=(0.0, 0.0, -1.0),
                            legR=(22.0, 16.0, 20.0), legL=(-22.0, 16.0, 16.0))),
        (7, "INOUTSINE", dict(twist=-20.0, lean=20.0, handsL=(-1.0, -3.5, 6.0), shL=(0.0, 0.0, 0.0))),
        settle(11),
    ], startup=3, active=[3, 3], contact=3)

# The blink, 6 / 2 / 6: the arms fly out wide with the blades trailing behind them (six ticks of travel leaning
# hard forward), then on arrival both blades close through the front like the Scissor: the arrival slash.
EDGES_BLINK = move(
    "Blink: arms flung wide, blades trailing, six ticks of travel leaning hard forward, then both blades snap shut "
    "through the front on arrival. 14 ticks, active 6-7.", 14, [
        START,
        (1, "OUTQUAD", dict(lean=28.0, hands=(8.5, -2.0, 1.5), handsL=(-8.5, -2.0, 1.5), sword=(118.0, -8.0, 90.0),
                            swordL=(-118.0, -8.0, -90.0), legR=(24.0, 10.0, 6.0), legL=(-36.0, 10.0, 12.0))),
        (2, "OUTQUAD", dict(lean=34.0, hands=(8.5, -2.5, 0.5), handsL=(-8.5, -2.5, 0.5), sword=(128.0, -14.0, 90.0),
                            swordL=(-128.0, -14.0, -90.0), legR=(26.0, 8.0, 6.0), legL=(-40.0, 8.0, 12.0))),
        (4, "INOUTSINE", dict(lean=35.0)),
        (5, "INQUAD", dict(lean=26.0, hands=(8.5, -1.0, 2.5), handsL=(-8.5, -1.0, 2.5), sword=(100.0, 10.0, "lead"),
                           swordL=(-100.0, 10.0, "lead"), legR=(24.0, 14.0, 10.0), legL=(-26.0, 14.0, 18.0))),
        (6, "LINEAR", dict(lean=22.0, hands=(4.0, -0.5, 8.0), handsL=(-4.0, -0.5, 8.0), sword=(-12.0, 12.0, "lead"),
                           swordL=(12.0, 12.0, "lead"), fwd=1.0)),
        (7, "OUTQUAD", dict(lean=20.0, hands=(1.5, -1.5, 8.5), handsL=(-1.5, -2.0, 8.5), sword=(-60.0, 8.0, "lead"),
                            swordL=(60.0, 8.0, "lead"), fwd=1.5)),
        (9, "INOUTSINE", dict(lean=18.0, sword=(-64.0, 12.0, "prev"), swordL=(64.0, 12.0, "prev"))),
        (14, "INOUTSINE", dict(EDGES_READY_SYM, fwd=0.0)),
    ], startup=6, active=[6, 7], contact=6)

# ----------------------------------------------------------------------------- the engine's moves, Edges versions

EDGES_DASH_FWD = move("Dash forward: a low fast lean, both sickles swept back. 8 ticks.", 8, [
    (0, "OUTQUAD", {}),
    (2, "LINEAR", dict(lean=32.0, hands=(6.5, -5.0, -4.0), handsL=(-6.5, -5.0, -4.0), sword=(150.0, -22.0, 90.0),
                       swordL=(-150.0, -22.0, -90.0), legR=(26.0, 8.0, 6.0), legL=(-36.0, 8.0, 12.0))),
    (5, "INOUTSINE", dict(lean=30.0)),
    (8, "INOUTSINE", EDGES_READY_SYM),
])

EDGES_DASH_BACK = move("Dash back: leaning back into the backstep, both sickles crossed in front. 8 ticks.", 8, [
    (0, "OUTQUAD", {}),
    (2, "LINEAR", dict(lean=-14.0, hands=(-1.5, -2.0, 6.0), handsL=(1.5, -1.5, 6.0), sword=(-30.0, 55.0, 45.0),
                       swordL=(30.0, 55.0, -45.0), legR=(24.0, 10.0, 6.0), legL=(-30.0, 10.0, 12.0))),
    (5, "INOUTSINE", dict(lean=-12.0)),
    (8, "INOUTSINE", EDGES_READY_SYM),
])

EDGES_DASH_SIDE = move("Dash to the right (mirrored for the left): tilted into it, both sickles trailing. 8 ticks.", 8, [
    (0, "OUTQUAD", {}),
    (2, "LINEAR", dict(tilt=18.0, lean=14.0, twist=-6.0, hands=(-2.0, -4.0, 4.0), handsL=(-8.0, -3.0, 1.0),
                       sword=(-90.0, -10.0, 90.0), swordL=(-120.0, -10.0, -90.0), legR=(6.0, 18.0, 8.0), legL=(-6.0, 28.0, 10.0))),
    (5, "INOUTSINE", dict(tilt=16.0)),
    (8, "INOUTSINE", EDGES_READY_SYM),
])

EDGES_GUARD = dict(lean=8.0, twist=0.0, hands=(-1.5, 3.5, 6.0), handsL=(1.5, 4.0, 6.0), sword=(-36.0, 74.0, 45.0),
                   swordL=(36.0, 74.0, -45.0), shR=(0.0, -1.0, -0.5), shL=(0.0, -1.0, -0.5))

EDGES_PARRY = move("Parry: both sickles snap up crossed before the face in 2 ticks, hold to tick 4, a slow return. "
                   "14 ticks.", 14, [
    (0, "OUTQUAD", {}),
    (2, "LINEAR", dict(EDGES_GUARD)),
    (4, "INOUTSINE", dict(EDGES_GUARD, hands=(-1.5, 4.0, 6.5), handsL=(1.5, 4.5, 6.5))),
    (8, "INOUTSINE", dict(lean=12.0, hands=(3.0, -1.0, 5.0), handsL=(-3.0, -1.5, 5.0), sword=(0.0, 50.0, 45.0),
                          swordL=(0.0, 50.0, -45.0), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0))),
    (14, "INOUTSINE", EDGES_READY_SYM),
], startup=0, active=[0, 4], contact=2)

EDGES_PARRY_SUCCESS = move("Parry success: both sickles flick outward from the cross. 6 ticks.", 6, [
    (0, "OUTQUAD", dict(EDGES_GUARD)),
    (2, "OUTSINE", dict(lean=12.0, hands=(7.0, 1.0, 5.0), handsL=(-7.0, 1.0, 5.0), sword=(84.0, 40.0, "lead"),
                        swordL=(-84.0, 40.0, "lead"), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0))),
    (3, "INOUTSINE", dict(sword=(88.0, 38.0, "prev"), swordL=(-88.0, 38.0, "prev"))),
    (6, "INOUTSINE", EDGES_READY_SYM),
], startup=0, active=[0, 2], contact=1, continue_from=("edges_parry", 3), base_extra=EDGES_GUARD)

EDGES_STAGGER = move("Stagger: rocked back, both arms flung out, back to the guard. 10 ticks.", 10, [
    (0, "OUTQUAD", {}),
    (2, "OUTSINE", dict(lean=-14.0, tilt=-4.0, twist=-8.0, hands=(8.0, -4.0, 2.0), handsL=(-8.0, -3.0, 2.0),
                        sword=(110.0, -30.0, 45.0), swordL=(-110.0, -20.0, -45.0), legR=(4.0, 12.0, 8.0), legL=(-24.0, 12.0, 16.0))),
    (4, "INOUTSINE", dict(lean=-10.0, tilt=-2.0)),
    (7, "INOUTSINE", dict(lean=8.0, tilt=0.0, twist=0.0, hands=(6.0, -3.0, 4.0), handsL=(-6.0, -3.5, 4.0),
                          sword=(40.0, 30.0, 45.0), swordL=(-40.0, 30.0, -45.0))),
    (10, "INOUTSINE", EDGES_READY_SYM),
])

EDGES_MOVES = {
    "edges_l1": EDGES_L1,
    "edges_l2": EDGES_L2,
    "edges_l3": EDGES_L3,
    "edges_l4": EDGES_L4,
    "edges_l5": EDGES_L5,
    "edges_charge": EDGES_CHARGE,
    "edges_orbit": EDGES_ORBIT,
    "edges_meteor": EDGES_METEOR,
    "edges_meteor_land": EDGES_METEOR_LAND,
    "edges_scissor": EDGES_SCISSOR,
    "edges_tether": EDGES_TETHER,
    "edges_blink": EDGES_BLINK,
    "edges_dash_forward": EDGES_DASH_FWD,
    "edges_dash_back": EDGES_DASH_BACK,
    "edges_dash_side": EDGES_DASH_SIDE,
    "edges_parry": EDGES_PARRY,
    "edges_parry_success": EDGES_PARRY_SUCCESS,
    "edges_stagger": EDGES_STAGGER,
}

# moves that start from another pose than the guard: their base is the guard with that pose on top
for _mv in EDGES_MOVES.values():
    extra = _mv.pop("base_extra", None)
    if extra:
        _mv["base"] = dict(EDGES_READY_POSE, **extra)
