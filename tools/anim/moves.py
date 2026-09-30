"""Choreography for the Cosmic Breach player animations.

Each entry: end (the last tick, equal to the move's length in ticks), the frame
data (startup, active ticks, contact tick) for the previewer, and keys:
[(tick, easing of the motion leaving this key, {pose changes})]. A key inherits
everything from the key before it; the first inherits from READY_POSE. The pose
vocabulary is documented in solver.py.

Frame data (1 tick = 0.05 s) comes from data/cosmicbreach/combat/moves/meridian/*.json.
Slash directions follow the "slash" blocks there (positive = the player's right).
"""

import math

READY_POSE = dict(lean=5.0, twist=0.0, tilt=0.0, fwd=0.0, side=0.0, rise=0.0, air=False,
                  legR=(9.0, 3.0, 6.0), legL=(-9.0, 3.0, 14.0),
                  hands=(1.0, -3.0, 6.0), sword=(-12.0, 44.0, 0.0), sword_world=False,
                  grip=1.0, left=(-20.0, 0.0, -8.0), rz=0.0, lz=0.0,
                  shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0))
READY = {k: READY_POSE[k] for k in ("lean", "twist", "tilt", "fwd", "side", "rise", "legR", "legL", "hands",
                                    "sword", "grip", "shR", "shL", "rz", "lz")}
# The ready guard every move starts from and returns to. "canonical" makes the solver settle it
# from scratch, so all moves share exactly the same channel values there.
READY_SYM = dict(READY, canonical=True)

# ----------------------------------------------------------------------------- light combo

L1 = dict(
    desc="Rising Cut: diagonal cut from low left to high right. 10 ticks, active 3-4.",
    end=10, startup=3, active=[3, 4], contact=3,
    keys=[
        (0, "OUTSINE", {}),
        # anticipation: coil left, weight on the left leg, blade low behind the left hip
        (2, "INQUAD", dict(twist=-34.0, lean=12.0, tilt=-4.0, hands=(0.0, -7.0, 4.5), sword=(-96.0, -30.0, "lead"),
                           legR=(12.0, 4.0, 4.0), legL=(-13.0, 5.0, 18.0), roll_ofs=20.0)),
        # contact: the hips have come through and the blade is already rising up the diagonal, hands
        # low and forward, so every view shows a cut on its way up rather than a blade pointing ahead
        (3, "LINEAR", dict(twist=8.0, lean=10.0, tilt=2.0, hands=(1.0, -3.5, 6.5), shR=(0.0, 0.0, -1.5),
                           shL=(0.0, 0.0, -1.5), sword=(18.0, 42.0, "lead"))),
        (4, "OUTQUAD", dict(twist=24.0, lean=7.0, tilt=4.0, hands=(0.5, -0.5, 6.5), sword=(46.0, 56.0, "lead"))),
        # follow-through high right, weight onto the right leg
        (5, "OUTSINE", dict(twist=36.0, lean=5.0, tilt=5.0, hands=(0.0, 0.5, 6.0), shR=(0.0, -0.5, -0.5),
                            shL=(0.0, -0.5, -0.5), sword=(66.0, 56.0, "lead"), legR=(11.0, 4.0, 8.0),
                            legL=(-13.0, 4.0, 12.0))),
        (6, "INOUTSINE", dict(twist=37.0, lean=5.0, tilt=5.0, hands=(0.0, -0.5, 6.0), sword=(69.0, 58.0, "prev"))),
        (10, "INOUTSINE", dict(READY_SYM, shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0))),
    ],
)

L2 = dict(
    desc="Horizon Sweep: flat sweep right to left at chest height, back foot pivoting. 11 ticks, active 3-4.",
    end=11, startup=3, active=[3, 4], contact=3,
    keys=[
        (0, "OUTSINE", {}),
        # wind-up: coiled right, blade back on the right, back (left) foot turned in and loaded
        (2, "INQUAD", dict(twist=46.0, lean=6.0, tilt=2.0, hands=(0.0, -3.0, 6.0), sword=(74.0, 2.0, "lead"),
                           roll_ofs=-30.0, legR=(10.0, 4.0, 6.0), legL=(-12.0, 4.0, 0.0))),
        (3, "LINEAR", dict(twist=18.0, lean=7.0, tilt=0.0, shR=(0.0, 0.0, -1.0), shL=(0.0, 0.0, -1.0),
                           sword=(24.0, 0.0, "lead"), legL=(-12.0, 4.0, 12.0))),
        # the back foot pivots toe-out as the hips come round
        (4, "OUTQUAD", dict(twist=-18.0, lean=7.0, tilt=-2.0, sword=(-30.0, -2.0, "lead"),
                            legL=(-13.0, 4.0, 28.0))),
        (5, "OUTSINE", dict(twist=-42.0, lean=5.0, tilt=-3.0, shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0),
                            sword=(-74.0, -5.0, "lead"), legL=(-14.0, 4.0, 38.0))),
        (6, "INOUTSINE", dict(twist=-46.0, sword=(-80.0, -7.0, "prev"))),
        (11, "INOUTSINE", READY_SYM),
    ],
)

HIGH_GUARD = dict(lean=-8.0, twist=12.0, hands=(0.0, 6.0, 3.0), shR=(0.0, -2.5, -1.0), shL=(0.0, -2.5, -1.0),
                  sword=(-4.0, 104.0, 0.0))

L3 = dict(
    desc="Meridian Chop: both hands overhead, a small lunge step, a vertical chop that stops at knee height. "
         "16 ticks, active 5-6.",
    end=16, startup=5, active=[5, 6], contact=5,
    keys=[
        (0, "OUTSINE", {}),
        # lift: the blade comes up the right side with its edge to the front
        (2, "OUTQUAD", dict(lean=-2.0, twist=14.0, hands=(0.0, 3.0, 4.0), shR=(0.0, -1.0, 0.0), shL=(0.0, -1.0, 0.0),
                            sword=(10.0, 80.0, 0.0), legR=(7.0, 3.0, 6.0), legL=(-11.0, 3.0, 14.0))),
        # both hands overhead, blade towering, weight back
        (3, "INOUTSINE", dict(HIGH_GUARD, legR=(6.0, 3.0, 6.0), legL=(-12.0, 3.0, 14.0))),
        (4, "INCUBIC", dict(HIGH_GUARD, lean=-9.0, sword=(0.0, 110.0, 0.0))),
        # the chop, with the lunge step landing on contact
        (5, "LINEAR", dict(lean=12.0, twist=0.0, hands=(0.0, 0.0, 6.5), shR=(0.0, 0.0, -1.0), shL=(0.0, 0.0, -1.0),
                           sword=(0.0, 42.0, "lead"), roll_ofs=35.0, legR=(24.0, 4.0, 6.0), legL=(-24.0, 4.0, 18.0),
                           fwd=2.5)),
        (6, "OUTQUAD", dict(lean=24.0, hands=(0.0, -6.0, 5.5), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0),
                            sword=(0.0, -32.0, "lead"), legR=(26.0, 4.0, 6.0), legL=(-26.0, 4.0, 18.0))),
        (8, "INOUTSINE", dict(lean=25.0, sword=(0.0, -35.0, "prev"))),
        (11, "INOUTSINE", dict(lean=18.0, sword=(-4.0, -18.0, "prev"), hands=(0.0, -5.0, 5.0))),
        (16, "INOUTSINE", dict(READY_SYM, fwd=0.0)),
    ],
)

# ----------------------------------------------------------------------------- charge and release

CHARGE_POSE = dict(twist=30.0, lean=14.0, tilt=2.0, hands=(0.0, -6.0, 6.0), sword=(90.0, -46.0, 0.0),
                   legR=(12.0, 5.0, 10.0), legL=(-20.0, 5.0, 24.0), grip=1.0)


def _charge_keys():
    """1 s loop: a slow breath plus a small irregular tremble in the arms and blade."""
    keys = []
    rnd = [0.0, 0.8, -0.5, 0.6, -0.9, 0.3, 0.7, -0.6, 0.2, -0.8, 0.9, -0.3, 0.5, -0.7, 0.4, -0.2, 0.8, -0.9, 0.1, -0.4]
    rnd2 = [0.0, -0.6, 0.9, -0.2, 0.5, -0.8, 0.3, 0.6, -0.7, 0.4, -0.3, 0.8, -0.5, 0.1, -0.9, 0.7, -0.4, 0.2, 0.6, -0.5]
    for t in range(0, 21):
        k = t % 20
        breath = math.sin(2 * math.pi * k / 20.0)
        amp = 1.0 if k else 0.0
        p = dict(CHARGE_POSE)
        p["lean"] = CHARGE_POSE["lean"] + 1.2 * breath
        p["twist"] = CHARGE_POSE["twist"] + 0.6 * rnd2[k] * amp
        y, pi, r = CHARGE_POSE["sword"]
        p["sword"] = (y + 1.4 * rnd[k] * amp, pi + 1.1 * rnd2[k] * amp + 0.8 * breath, r + 2.0 * rnd[k] * amp)
        h = CHARGE_POSE["hands"]
        p["hands"] = (h[0], h[1] + 0.25 * rnd[k] * amp, h[2])
        keys.append((t, "LINEAR", p))
    return keys


CHARGE = dict(
    desc="Charge hold: blade drawn back low on the right, a slow breath and a tremble. 1 s loop.",
    end=19, loop=dict(ret=0), keys=_charge_keys(), tail_ease="LINEAR",
)

LINE = dict(
    desc="Meridian Line: from the charge, a huge vertical cleave ending with the tip near the ground. "
         "19 ticks, active 4-6.",
    end=19, startup=4, active=[4, 6], contact=4,
    base=dict(READY_POSE, **CHARGE_POSE),
    keys=[
        (0, "OUTSINE", {}),
        # the blade sweeps up the right side...
        (1, "OUTQUAD", dict(twist=22.0, lean=2.0, tilt=0.0, hands=(0.0, 1.0, 5.0), shR=(0.0, -1.0, 0.0),
                            shL=(0.0, -1.0, 0.0), sword=(46.0, 30.0, 0.0), legR=(8.0, 4.0, 8.0),
                            legL=(-14.0, 4.0, 20.0))),
        # ...to the high guard, both hands overhead, weight back
        (2, "INOUTSINE", dict(HIGH_GUARD)),
        (3, "INCUBIC", dict(HIGH_GUARD, lean=-10.0, sword=(0.0, 112.0, 0.0))),
        # the cleave
        (4, "LINEAR", dict(twist=0.0, lean=14.0, hands=(0.0, 0.5, 6.5), shR=(0.0, 0.0, -1.0), shL=(0.0, 0.0, -1.0),
                           sword=(0.0, 50.0, "lead"), roll_ofs=35.0, legR=(26.0, 5.0, 8.0), legL=(-28.0, 5.0, 18.0))),
        (5, "LINEAR", dict(lean=26.0, hands=(0.0, -3.5, 6.0), sword=(0.0, 4.0, "lead"),
                           legR=(32.0, 5.0, 8.0), legL=(-36.0, 5.0, 20.0))),
        (6, "OUTQUAD", dict(lean=34.0, hands=(0.0, -6.5, 5.5), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0),
                            sword=(0.0, -38.0, "lead"))),
        (9, "INOUTSINE", dict(lean=35.0, sword=(0.0, -41.0, "prev"))),
        (13, "INOUTSINE", dict(lean=22.0, hands=(0.0, -5.0, 5.5), sword=(-4.0, -22.0, "prev"),
                               legR=(20.0, 4.0, 7.0), legL=(-20.0, 4.0, 16.0))),
        (19, "INOUTSINE", READY_SYM),
    ],
)

# ----------------------------------------------------------------------------- plunge

DIVE = dict(air=True, lean=24.0, twist=0.0, tilt=0.0, hands=(0.0, -3.0, 5.0), sword_world=True,
            sword=(0.0, -90.0, 0.0), legR=(52.0, 6.0, 4.0), legL=(34.0, 6.0, 4.0))


def _dive_keys():
    keys = [(0, "OUTQUAD", dict(air=True)), (2, "LINEAR", dict(DIVE))]
    flutter = [0.0, 0.7, -0.4, 0.9, -0.8, 0.3, 0.6, -0.9, 0.5, -0.3, 0.8, -0.6]
    for i in range(1, 12):
        t = 2 + i
        f = flutter[i]
        p = dict(DIVE)
        p["legR"] = (DIVE["legR"][0] + 2.0 * f, DIVE["legR"][1], DIVE["legR"][2])
        p["legL"] = (DIVE["legL"][0] - 2.4 * f, DIVE["legL"][1], DIVE["legL"][2])
        p["tilt"] = 1.2 * f
        p["lean"] = DIVE["lean"] + 0.8 * f
        keys.append((t, "LINEAR", p))
    keys.append((14, "LINEAR", dict(DIVE)))  # = tick 2, closes the loop
    return keys


FALLING_STAR = dict(
    desc="Falling Star dive: flips to a reverse grip, blade straight down, body tucked (2 ticks), then loops "
         "the hold while falling.",
    end=13, loop=dict(ret=2), keys=_dive_keys(), tail_ease="LINEAR", fp_pitch=60.0,
)

FALLING_STAR_LAND = dict(
    desc="Falling Star landing: blade driven into the ground at the feet, knees bent, rising back to ready. "
         "10 ticks.",
    end=10, startup=0, active=[0, 0], contact=0,
    base=dict(READY_POSE, **DIVE),
    keys=[
        (0, "OUTQUAD", dict(air=False, plant2=True, lean=26.0, hands=(0.0, -6.0, 4.5), legR=(46.0, 6.0, 8.0),
                            legL=(-60.0, 5.0, 20.0))),
        (2, "INOUTSINE", dict(lean=30.0, legR=(50.0, 6.0, 8.0), legL=(-64.0, 5.0, 20.0), hands=(0.0, -6.5, 4.5))),
        (4, "INOUTSINE", dict(lean=29.0)),
        (7, "INOUTSINE", dict(lean=14.0, sword=(0.0, -45.0, 0.0), legR=(18.0, 4.0, 7.0), legL=(-24.0, 4.0, 16.0),
                              hands=(0.0, -5.0, 5.5))),
        (10, "INOUTSINE", dict(READY_SYM, sword_world=True)),
    ],
)

# ----------------------------------------------------------------------------- dash attack and ability

PASS = dict(
    desc="Pass: a drawn slash (right to left) while dashing through the target, ending with the blade held "
         "behind. 12 ticks, active 2-4.",
    end=12, startup=2, active=[2, 4], contact=2,
    keys=[
        (0, "OUTSINE", {}),
        (1, "INQUAD", dict(twist=40.0, lean=18.0, hands=(0.0, -2.0, 5.0), sword=(82.0, -8.0, "lead"),
                           legR=(22.0, 3.0, 4.0), legL=(-30.0, 3.0, 10.0))),
        (2, "LINEAR", dict(twist=12.0, lean=22.0, sword=(28.0, -2.0, "lead"))),
        (3, "LINEAR", dict(twist=-18.0, lean=22.0, sword=(-32.0, -2.0, "lead"))),
        (4, "OUTQUAD", dict(twist=-40.0, lean=20.0, sword=(-80.0, -8.0, "lead"))),
        # through and past: blade held behind on the left, low
        (6, "OUTSINE", dict(twist=-50.0, lean=12.0, hands=(0.0, -4.0, 4.0), sword=(-122.0, -15.0, "prev"),
                            legR=(16.0, 3.0, 6.0), legL=(-20.0, 3.0, 12.0))),
        (8, "INOUTSINE", dict(twist=-48.0, lean=10.0)),
        (12, "INOUTSINE", READY_SYM),
    ],
)

ZENITH = dict(
    desc="Zenith: a rising slash from low to high that launches, body rising onto the toes. 19 ticks, active 6-8.",
    end=19, startup=6, active=[6, 8], contact=6,
    keys=[
        (0, "OUTSINE", {}),
        # gather: drop into a wide crouch, blade scraping low on the left
        (3, "INOUTSINE", dict(lean=18.0, twist=-20.0, hands=(0.0, -8.0, 5.0), sword=(-25.0, -60.0, "lead"),
                              legR=(20.0, 5.0, 6.0), legL=(-22.0, 5.0, 16.0))),
        (5, "INCUBIC", dict(lean=22.0, twist=-24.0, sword=(-25.0, -65.0, "lead"),
                            legR=(24.0, 5.0, 6.0), legL=(-26.0, 5.0, 16.0))),
        # the rising slash: on the hit the blade is already sweeping up through the target, the body
        # uncoiling out of the crouch
        (6, "LINEAR", dict(lean=7.0, twist=-6.0, hands=(0.0, -2.0, 6.5), sword=(-6.0, 24.0, "lead"),
                           roll_ofs=30.0, legR=(18.0, 4.0, 6.0), legL=(-20.0, 4.0, 14.0))),
        (7, "LINEAR", dict(lean=-4.0, twist=8.0, hands=(0.0, 1.0, 6.5), sword=(4.0, 50.0, "lead"),
                           legR=(10.0, 3.0, 5.0), legL=(-12.0, 3.0, 12.0))),
        # up onto the toes, blade overhead to the right, clear of the eyes
        (8, "OUTQUAD", dict(lean=-10.0, twist=34.0, hands=(0.0, 1.5, 6.5), sword=(16.0, 58.0, "lead"),
                            legR=(-4.0, 2.0, 4.0), legL=(-10.0, 2.0, 8.0))),
        (10, "INOUTSINE", dict(lean=-12.0, twist=36.0, sword=(18.0, 63.0, "prev"))),
        (14, "INOUTSINE", dict(lean=-2.0, twist=16.0, hands=(0.0, 0.0, 6.0), sword=(6.0, 56.0, "prev"),
                               legR=(8.0, 3.0, 6.0), legL=(-10.0, 3.0, 12.0))),
        (19, "INOUTSINE", READY_SYM),
    ],
)

# ----------------------------------------------------------------------------- movement and defence

DASH_FWD = dict(
    desc="Dash forward: low fast lean, arms and blade trailing. 8 ticks.",
    end=8,
    keys=[
        (0, "OUTQUAD", {}),
        # the sword arm swings out and back past the right hip, the left hand lets go and trails
        (1, "OUTQUAD", dict(lean=20.0, grip=0.3, hands=(7.0, -6.0, 2.0), sword=(90.0, -20.0, 0.0),
                            left=(0.0, 0.0, -30.0), legR=(18.0, 3.0, 4.0), legL=(-24.0, 3.0, 10.0))),
        (2, "LINEAR", dict(lean=32.0, grip=0.0, hands=(5.5, -9.0, -6.0), sword=(160.0, -35.0, 0.0),
                           left=(35.0, 0.0, -22.0), legR=(26.0, 3.0, 4.0), legL=(-34.0, 3.0, 10.0))),
        (5, "INOUTSINE", dict(lean=30.0)),
        (6, "INOUTSINE", dict(lean=22.0, grip=0.1, hands=(12.0, -6.0, -1.0), sword=(112.0, -14.0, 0.0),
                              left=(15.0, 0.0, -30.0))),
        (7, "INOUTSINE", dict(lean=14.0, grip=0.4, hands=(5.0, -6.0, 6.0), sword=(40.0, -45.0, 0.0),
                              left=(-10.0, 0.0, -30.0), legR=(14.0, 3.0, 5.0), legL=(-16.0, 3.0, 12.0))),
        (8, "INOUTSINE", READY_SYM),
    ],
)

DASH_BACK = dict(
    desc="Dash back: leans back into the backstep, guard held low in front. 8 ticks.",
    end=8,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "LINEAR", dict(lean=-20.0, hands=(0.0, -5.0, 6.0), sword=(-6.0, -8.0, 0.0),
                           legR=(22.0, 3.0, 4.0), legL=(-30.0, 3.0, 12.0))),
        (5, "INOUTSINE", dict(lean=-17.0)),
        (8, "INOUTSINE", READY_SYM),
    ],
)

DASH_SIDE = dict(
    desc="Dash to the right (mirrored by the game for the left): body tilts into the dash, blade trails. 8 ticks.",
    end=8, mirror_safe=True,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "LINEAR", dict(tilt=20.0, lean=8.0, twist=-8.0, hands=(0.0, -6.0, 4.0), sword=(-105.0, -22.0, 0.0),
                           legR=(4.0, 14.0, 6.0), legL=(-4.0, 26.0, 8.0))),
        (5, "INOUTSINE", dict(tilt=18.0)),
        (8, "INOUTSINE", READY_SYM),
    ],
)

GUARD = dict(twist=10.0, lean=1.0, hands=(0.0, 0.0, 6.0), sword=(-48.0, 52.0, ("flat", (0.0, 0.0, 1.0))),
             legR=(10.0, 4.0, 6.0), legL=(-12.0, 4.0, 14.0))

PARRY = dict(
    desc="Parry: blade snaps up across the body into a guard in 2 ticks, holds to tick 4, then a slow "
         "10-tick return (the whiff). 14 ticks.",
    end=14, startup=0, active=[0, 4], contact=2,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "LINEAR", dict(GUARD)),
        (4, "INOUTSINE", dict(GUARD, hands=(0.0, 0.5, 6.5))),
        (8, "INOUTSINE", dict(twist=6.0, lean=7.0, hands=(1.0, -4.0, 5.0), sword=(-30.0, 25.0, "prev"))),
        (14, "INOUTSINE", READY_SYM),
    ],
)

PARRY_SUCCESS = dict(
    desc="Parry success: a sharp flick of the blade outward from the guard. 6 ticks.",
    end=6, startup=0, active=[0, 2], contact=1, continue_from=("combat_parry", 3),
    base=dict(READY_POSE, **GUARD),
    keys=[
        (0, "OUTQUAD", dict(GUARD)),
        (2, "OUTSINE", dict(twist=26.0, lean=4.0, hands=(0.0, 0.0, 6.5), sword=(72.0, 18.0, "lead"))),
        (3, "INOUTSINE", dict(twist=27.0, sword=(74.0, 16.0, "prev"))),
        (6, "INOUTSINE", READY_SYM),
    ],
)

STAGGER = dict(
    desc="Stagger: recoil, torso thrown back, arms loose, recovering to ready. 10 ticks.",
    end=10,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "OUTSINE", dict(lean=-22.0, tilt=-6.0, twist=10.0, grip=0.0, hands=(4.0, -9.0, 3.0),
                            sword=(70.0, -50.0, 0.0), left=(-30.0, 0.0, -38.0),
                            legR=(4.0, 4.0, 6.0), legL=(-24.0, 5.0, 14.0))),
        (4, "INOUTSINE", dict(lean=-18.0, tilt=-4.0)),
        (6, "INOUTSINE", dict(lean=-4.0, tilt=-1.0, twist=6.0, grip=0.5, hands=(4.0, -6.0, 5.5),
                              sword=(38.0, -8.0, 0.0), left=(-45.0, 10.0, -20.0))),
        (7, "INOUTSINE", dict(lean=4.0, tilt=0.0, twist=4.0, grip=1.0, hands=(1.0, -4.0, 5.0),
                              sword=(-8.0, 30.0, 0.0))),
        (10, "INOUTSINE", READY_SYM),
    ],
)


# ============================================================================= the Comet Maul
# Frame data from data/cosmicbreach/combat/moves/comet_maul/*.json. The Maul is held by the same grip as
# Meridian (rig.WEAPONS), so the same vocabulary works: "sword" is the haft's direction toward the head,
# "lead" rolls the head so its striking face leads the motion. Weight: long anticipation, the hips move
# before the head, the head drags behind and lands hard, and the body sinks into every impact.

MAUL_RAISED = dict(lean=-9.0, twist=10.0, hands=(5.0, 5.0, 3.0), shR=(0.0, -2.5, -1.0), shL=(0.0, -2.5, -1.0),
                   sword=(-70.0, 142.0, 0.0))

MAUL_L1 = dict(
    desc="Overhead Smash: the hammer hauled up behind the head, a full-body drop into the smash, the hips "
         "sinking as the head lands. 19 ticks, active 7-8.",
    end=19, startup=7, active=[7, 8], contact=7,
    keys=[
        (0, "OUTSINE", {}),
        # the haul: the head swings up past the face, weight onto the back leg
        (2, "INOUTSINE", dict(lean=-2.0, twist=12.0, hands=(0.0, 3.5, 4.5), shR=(0.0, -1.0, 0.0), shL=(0.0, -1.0, 0.0),
                              sword=(-24.0, 100.0, 0.0), legR=(8.0, 4.0, 6.0), legL=(-12.0, 4.0, 14.0))),
        # raised behind the head over the right shoulder, arched back: the long wait before the drop
        (4, "INOUTSINE", dict(MAUL_RAISED, legR=(6.0, 4.0, 6.0), legL=(-14.0, 4.0, 14.0))),
        (5, "INCUBIC", dict(MAUL_RAISED, lean=-11.0, sword=(-70.0, 148.0, 0.0))),
        # the drop starts at the hips: the body pitches forward before the head comes over
        (6, "INQUAD", dict(lean=6.0, twist=4.0, hands=(0.0, 5.0, 4.5), shR=(0.0, -1.5, -1.0), shL=(0.0, -1.5, -1.0),
                           sword=(6.0, 88.0, "lead"), roll_ofs=55.0, legR=(16.0, 5.0, 6.0), legL=(-18.0, 5.0, 16.0))),
        # contact: the head in the ground well ahead, the whole body dropped behind it in a wide stance
        (7, "LINEAR", dict(lean=30.0, twist=0.0, hands=(0.0, -3.5, 7.0), shR=(0.0, 0.0, -2.0), shL=(0.0, 0.0, -2.0),
                           sword=(0.0, -4.0, "lead"), roll_ofs=55.0, legR=(32.0, 8.0, 6.0), legL=(-34.0, 8.0, 18.0),
                           fwd=2.0)),
        (8, "OUTQUAD", dict(lean=34.0, hands=(0.0, -4.5, 7.0), sword=(0.0, -9.0, "prev"), legR=(35.0, 9.0, 6.0),
                            legL=(-37.0, 9.0, 18.0))),
        (11, "INOUTSINE", dict(lean=33.0, sword=(0.0, -10.0, "prev"))),
        (14, "INOUTSINE", dict(lean=18.0, hands=(0.0, -3.0, 7.0), sword=(-6.0, -4.0, "prev"), shR=(0.0, 0.0, -1.5),
                               shL=(0.0, 0.0, -1.5), legR=(16.0, 5.0, 6.0), legL=(-18.0, 5.0, 16.0))),
        (19, "INOUTSINE", dict(READY_SYM, fwd=0.0)),
    ],
)

MAUL_L2 = dict(
    desc="Side Swing: the hips lead, the head drags through a flat arc from the right, momentum carries a half "
         "step. 20 ticks, active 6-8.",
    end=20, startup=6, active=[6, 8], contact=6,
    keys=[
        (0, "OUTSINE", {}),
        # the head swung out to the right, then back: the coil
        (1, "LINEAR", dict(twist=18.0, lean=6.0, hands=(1.0, -2.5, 5.5), sword=(28.0, 36.0, "lead"))),
        (2, "INQUAD", dict(twist=38.0, lean=8.0, tilt=2.0, sword=(66.0, 22.0, "lead"))),
        # coil: body turned right, the head hanging back on the right, weight on the back foot
        (3, "INOUTSINE", dict(twist=52.0, lean=9.0, tilt=3.0, hands=(1.0, -2.0, 5.0), sword=(100.0, 12.0, "lead"),
                              roll_ofs=-20.0, legR=(10.0, 5.0, 6.0), legL=(-13.0, 5.0, 0.0))),
        # the hips turn first; the head stays back
        (5, "INQUAD", dict(twist=28.0, lean=9.0, tilt=1.0, sword=(92.0, 8.0, "lead"), legL=(-13.0, 5.0, 10.0))),
        # contact: hips already past centre, the head only now swinging through in front
        (6, "LINEAR", dict(twist=-6.0, lean=8.0, tilt=-1.0, hands=(0.0, -2.5, 6.5), shR=(0.0, 0.0, -1.0),
                           shL=(0.0, 0.0, -1.0), sword=(26.0, 2.0, "lead"), legL=(-14.0, 5.0, 22.0))),
        (7, "LINEAR", dict(twist=-30.0, sword=(-28.0, 0.0, "lead"), legL=(-14.0, 5.0, 32.0))),
        (8, "OUTQUAD", dict(twist=-50.0, tilt=-3.0, sword=(-78.0, -2.0, "lead"), legL=(-15.0, 5.0, 40.0))),
        # the head's momentum drags the body round and a half step forward
        (10, "OUTSINE", dict(twist=-60.0, lean=6.0, sword=(-104.0, -6.0, "prev"), shR=(0.0, 0.0, 0.0),
                             shL=(0.0, 0.0, 0.0), fwd=4.0, legR=(18.0, 4.0, 10.0), legL=(-16.0, 4.0, 40.0))),
        (13, "INOUTSINE", dict(twist=-48.0, lean=5.0, sword=(-86.0, -4.0, "prev"))),
        (20, "INOUTSINE", dict(READY_SYM, fwd=0.0)),
    ],
)


def _spin_keys():
    """One full turn to the left with the hammer held out, ending in an overhead slam in front. The feet
    pivot with the body (their turn follows the twist) and the head rides along, then snaps back ahead."""
    out = [(0, "OUTSINE", dict(head_yaw=0.0, head_follow=1.0))]
    # load: turned right, hammer out low on the right
    out.append((2, "INOUTSINE", dict(twist=40.0, lean=10.0, hands=(3.0, -3.0, 5.0), sword=(92.0, -6.0, "lead"),
                                     legR=(8.0, 5.0, 46.0), legL=(-10.0, 5.0, 40.0), head_yaw=0.0, head_follow=1.0)))
    # the turn: body round through the back, hammer flung out, head riding with the body
    for t, twist, pitch in ((4, -60.0, 4.0), (5, -140.0, 10.0), (6, -220.0, 24.0), (7, -290.0, 60.0)):
        out.append((t, "LINEAR", dict(twist=twist, lean=6.0, hands=(2.0, -1.0, 6.0), sword=(90.0 - (t - 4) * 8.0, pitch, "lead"),
                                      legR=(10.0, 5.0, twist + 46.0), legL=(-10.0, 5.0, twist + 40.0),
                                      head_yaw=0.0, head_follow=0.0)))
    # coming out of the turn the hammer goes up over the head
    out.append((8, "INQUAD", dict(twist=-345.0, lean=8.0, hands=(0.0, 3.5, 5.0), shR=(0.0, -1.5, -1.0), shL=(0.0, -1.5, -1.0),
                                  sword=(-4.0, 64.0, "lead"), roll_ofs=30.0, legR=(14.0, 5.0, -345.0 + 6.0), legL=(-16.0, 5.0, -345.0 + 16.0),
                                  head_yaw=0.0, head_follow=0.5)))
    # contact: the slam in front, a deep drop
    out.append((9, "LINEAR", dict(twist=-360.0, lean=32.0, hands=(0.0, -4.5, 6.5), shR=(0.0, 0.0, -1.0), shL=(0.0, 0.0, -1.0),
                                  sword=(0.0, -6.0, "lead"), roll_ofs=55.0, legR=(32.0, 8.0, -360.0 + 6.0), legL=(-34.0, 8.0, -360.0 + 18.0),
                                  fwd=2.0, head_yaw=0.0, head_follow=1.0)))
    out.append((11, "OUTQUAD", dict(lean=35.0, hands=(0.0, -4.5, 7.0), shR=(0.0, 0.0, -2.0), shL=(0.0, 0.0, -2.0),
                                    sword=(0.0, -11.0, "prev"), legR=(35.0, 9.0, -360.0 + 6.0), legL=(-37.0, 9.0, -360.0 + 18.0))))
    out.append((16, "INOUTSINE", dict(lean=32.0, sword=(0.0, -10.0, "prev"))))
    out.append((21, "INOUTSINE", dict(lean=16.0, hands=(0.0, -3.0, 7.0), sword=(-6.0, -4.0, "prev"), shR=(0.0, 0.0, -1.5),
                                      shL=(0.0, 0.0, -1.5), legR=(16.0, 5.0, -360.0 + 6.0), legL=(-18.0, 5.0, -360.0 + 16.0))))
    out.append((27, "INOUTSINE", dict(READY_SYM, twist=-360.0, legR=(9.0, 3.0, -360.0 + 6.0), legL=(-9.0, 3.0, -360.0 + 14.0),
                                      fwd=0.0, head_yaw=0.0, head_follow=1.0)))
    return out


MAUL_L3 = dict(
    desc="Spin Slam: one full turn with the hammer flung out, ending in a slam in front; the camera dips. "
         "27 ticks, active 9-12.",
    end=27, startup=9, active=[9, 12], contact=9, keys=_spin_keys(),
)

MAUL_CHARGE_POSE = dict(MAUL_RAISED, lean=-7.0, twist=8.0, sword=(-72.0, 140.0, 0.0), legR=(12.0, 6.0, 8.0),
                        legL=(-16.0, 6.0, 20.0))


def _maul_charge_keys():
    """1 s loop: hoisted overhead, a slow heavy breath and a tremble in the arms holding it up."""
    keys = []
    rnd = [0.0, 0.7, -0.5, 0.8, -0.9, 0.4, 0.6, -0.7, 0.3, -0.8, 0.9, -0.2, 0.5, -0.6, 0.4, -0.3, 0.8, -0.9, 0.2, -0.4]
    for t in range(0, 21):
        k = t % 20
        breath = math.sin(2 * math.pi * k / 20.0)
        amp = 1.0 if k else 0.0
        p = dict(MAUL_CHARGE_POSE)
        p["lean"] = MAUL_CHARGE_POSE["lean"] + 1.0 * breath
        y, pi, r = MAUL_CHARGE_POSE["sword"]
        p["sword"] = (y + 1.2 * rnd[k] * amp, pi + 1.5 * rnd[(k + 5) % 20] * amp + 1.2 * breath, r)
        h = MAUL_CHARGE_POSE["hands"]
        p["hands"] = (h[0], h[1] + 0.2 * rnd[k] * amp, h[2])
        keys.append((t, "LINEAR", p))
    return keys


MAUL_CHARGE = dict(
    desc="Maul charge hold: hoisted overhead, a heavy breath and a tremble while embers gather. 1 s loop.",
    end=19, loop=dict(ret=0), keys=_maul_charge_keys(), tail_ease="LINEAR",
)

MAUL_CRATER = dict(
    desc="Impact Crater: from the hold, a one-block leap and a slam that cracks the ground, the body driven "
         "down into it. 24 ticks, active 5-7.",
    end=24, startup=5, active=[5, 7], contact=5,
    base=dict(READY_POSE, **MAUL_CHARGE_POSE),
    keys=[
        (0, "OUTQUAD", {}),
        # the leap: up off both feet, the hammer drawn further back
        (2, "OUTQUAD", dict(air=True, rise=12.0, lean=-12.0, sword=(-74.0, 150.0, 0.0), legR=(34.0, 6.0, 8.0),
                            legL=(10.0, 6.0, 20.0))),
        (3, "INQUAD", dict(air=True, rise=15.0, lean=-4.0, hands=(4.0, 5.5, 3.5), sword=(-56.0, 118.0, "lead"),
                           legR=(30.0, 6.0, 8.0), legL=(6.0, 6.0, 20.0))),
        (4, "INCUBIC", dict(air=True, rise=8.0, lean=14.0, hands=(0.0, 2.0, 5.5), sword=(0.0, 40.0, "lead"), roll_ofs=55.0,
                            legR=(24.0, 6.0, 8.0), legL=(-12.0, 6.0, 18.0))),
        # contact: landed in a deep split stance, the head driven into the ground ahead
        (5, "LINEAR", dict(air=False, rise=0.0, lean=34.0, hands=(0.0, -3.5, 7.0), shR=(0.0, 0.0, -2.0),
                           shL=(0.0, 0.0, -2.0), sword=(0.0, -6.0, "lead"), roll_ofs=55.0, legR=(34.0, 9.0, 6.0),
                           legL=(-36.0, 9.0, 18.0), fwd=1.5)),
        (7, "OUTQUAD", dict(lean=37.0, hands=(0.0, -4.5, 7.0), sword=(0.0, -12.0, "prev"), legR=(37.0, 10.0, 6.0),
                            legL=(-39.0, 10.0, 18.0))),
        (12, "INOUTSINE", dict(lean=35.0)),
        (17, "INOUTSINE", dict(lean=18.0, hands=(0.0, -3.0, 7.0), sword=(-6.0, -4.0, "prev"),
                               shR=(0.0, 0.0, -1.5), shL=(0.0, 0.0, -1.5), legR=(16.0, 5.0, 6.0), legL=(-18.0, 5.0, 16.0))),
        (24, "INOUTSINE", dict(READY_SYM, fwd=0.0, rise=0.0)),
    ],
)

MAUL_DIVE = dict(air=True, lean=58.0, twist=0.0, tilt=0.0, hands=(0.0, -1.5, 6.5), sword_world=True,
                 sword=(0.0, -66.0, 0.0), legR=(34.0, 6.0, 4.0), legL=(18.0, 6.0, 4.0))


def _maul_dive_keys():
    keys = [(0, "OUTQUAD", dict(air=True)), (3, "LINEAR", dict(MAUL_DIVE))]
    flutter = [0.0, 0.7, -0.4, 0.9, -0.8, 0.3, 0.6, -0.9, 0.5, -0.3, 0.8, -0.6]
    for i in range(1, 12):
        t = 3 + i
        f = flutter[i]
        p = dict(MAUL_DIVE)
        p["legR"] = (MAUL_DIVE["legR"][0] + 2.0 * f, MAUL_DIVE["legR"][1], MAUL_DIVE["legR"][2])
        p["legL"] = (MAUL_DIVE["legL"][0] - 2.4 * f, MAUL_DIVE["legL"][1], MAUL_DIVE["legL"][2])
        p["tilt"] = 1.0 * f
        p["lean"] = MAUL_DIVE["lean"] + 0.8 * f
        keys.append((t, "LINEAR", p))
    keys.append((15, "LINEAR", dict(MAUL_DIVE)))  # = tick 3, closes the loop
    return keys


MAUL_METEORFALL = dict(
    desc="Meteorfall dive: head first, both hands on the haft, the hammer driven down ahead (3 ticks), then "
         "loops the hold while falling.",
    end=14, loop=dict(ret=3), keys=_maul_dive_keys(), tail_ease="LINEAR", fp_pitch=60.0,
)

MAUL_METEORFALL_LAND = dict(
    desc="Meteorfall landing: the hammer driven into the ground in front, a crouch that takes the fall, "
         "rising back to ready. 14 ticks.",
    end=14, startup=0, active=[0, 0], contact=0,
    base=dict(READY_POSE, **MAUL_DIVE),
    keys=[
        (0, "OUTQUAD", dict(air=False, plant2=True, lean=36.0, hands=(0.0, -4.0, 7.0), shR=(0.0, 0.0, -2.0),
                            shL=(0.0, 0.0, -2.0), sword_world=True, sword=(0.0, -44.0, 0.0), legR=(40.0, 8.0, 8.0),
                            legL=(-44.0, 8.0, 20.0))),
        (2, "INOUTSINE", dict(lean=39.0, legR=(44.0, 9.0, 8.0), legL=(-48.0, 9.0, 20.0), hands=(0.0, -4.5, 7.0),
                              sword=(0.0, -48.0, 0.0))),
        (5, "INOUTSINE", dict(lean=37.0)),
        (9, "INOUTSINE", dict(lean=18.0, sword=(0.0, -24.0, 0.0), legR=(18.0, 5.0, 7.0), legL=(-24.0, 5.0, 16.0),
                              hands=(0.0, -3.0, 7.0), shR=(0.0, 0.0, -1.5), shL=(0.0, 0.0, -1.5))),
        (14, "INOUTSINE", dict(READY_SYM, sword_world=False, rise=0.0)),
    ],
)

MAUL_RAM = dict(
    desc="Ram: a shoulder charge with the haft braced across the chest, head out to the left, out of the "
         "Maul's dash (which carries it the same way). 17 ticks, active 3-6.",
    end=17, startup=3, active=[3, 6], contact=3,
    keys=[
        (0, "OUTSINE", {}),
        (2, "INQUAD", dict(twist=36.0, lean=18.0, hands=(-1.0, -1.0, 4.0), sword=(-84.0, 6.0, ("flat", (0.0, 0.0, 1.0))),
                           legR=(24.0, 3.0, 20.0), legL=(-30.0, 3.0, 30.0))),
        (3, "LINEAR", dict(twist=44.0, lean=28.0, hands=(-1.0, -1.0, 6.0), legR=(30.0, 3.0, 20.0), legL=(-34.0, 3.0, 30.0))),
        (5, "LINEAR", dict(twist=46.0, lean=30.0, hands=(-1.0, -0.5, 6.5), legR=(-20.0, 3.0, 20.0), legL=(26.0, 3.0, 30.0))),
        (6, "OUTQUAD", dict(twist=40.0, lean=26.0)),
        (9, "OUTSINE", dict(twist=24.0, lean=14.0, hands=(0.0, -2.0, 5.0), legR=(12.0, 3.0, 10.0), legL=(-14.0, 3.0, 16.0))),
        (17, "INOUTSINE", READY_SYM),
    ],
)

# The engine's own animations in the Maul's version (WeaponDef "animations"): Meridian's dash trails the
# blade past the hip and its stagger flings it back, which puts the Maul's long head through the legs.

MAUL_DASH_FWD = dict(
    desc="Maul dash forward: a low fast lean with the hammer carried across the body, head low on the "
         "left, both hands on it. 8 ticks.",
    end=8,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "LINEAR", dict(lean=28.0, twist=26.0, hands=(-0.5, -3.0, 5.0), sword=(-70.0, -12.0, ("flat", (0.0, 0.0, 1.0))),
                           legR=(26.0, 3.0, 12.0), legL=(-34.0, 3.0, 20.0))),
        (5, "INOUTSINE", dict(lean=27.0, twist=24.0)),
        (8, "INOUTSINE", READY_SYM),
    ],
)

MAUL_DASH_BACK = dict(
    desc="Maul dash back: leans back into the backstep, the hammer held across in front. 8 ticks.",
    end=8,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "LINEAR", dict(lean=-16.0, twist=10.0, hands=(0.0, -2.0, 6.0), sword=(-58.0, 14.0, ("flat", (0.0, 0.0, 1.0))),
                           legR=(22.0, 3.0, 4.0), legL=(-30.0, 3.0, 12.0))),
        (5, "INOUTSINE", dict(lean=-14.0)),
        (8, "INOUTSINE", READY_SYM),
    ],
)

MAUL_STAGGER = dict(
    desc="Maul stagger: rocked back, the hammer's weight dragging the arms down and aside, recovering to "
         "ready. 10 ticks.",
    end=10,
    keys=[
        (0, "OUTQUAD", {}),
        (2, "OUTSINE", dict(lean=-18.0, tilt=-5.0, twist=-10.0, hands=(1.0, -5.0, 5.5), sword=(-40.0, -30.0, 0.0),
                            legR=(4.0, 4.0, 6.0), legL=(-24.0, 5.0, 14.0))),
        (4, "INOUTSINE", dict(lean=-14.0, tilt=-3.0)),
        (7, "INOUTSINE", dict(lean=2.0, tilt=0.0, twist=0.0, hands=(1.0, -3.5, 6.0), sword=(-24.0, 10.0, 0.0))),
        (10, "INOUTSINE", READY_SYM),
    ],
)


def _maul_well_keys():
    """Raise, drive the hammer head-down into the ground (the plant), then lean on the haft, braced and
    trembling, while the well pulls; the Collapse jolts it; the hammer is wrenched out."""
    keys = [
        (0, "OUTSINE", {}),
        (3, "INOUTSINE", dict(lean=-6.0, twist=8.0, hands=(4.5, 5.0, 3.5), shR=(0.0, -2.0, -1.0), shL=(0.0, -2.0, -1.0),
                              sword=(-62.0, 124.0, 0.0), legR=(8.0, 5.0, 6.0), legL=(-12.0, 5.0, 14.0))),
        (6, "INCUBIC", dict(lean=-8.0, sword=(-70.0, 136.0, 0.0), hands=(5.0, 5.0, 3.0))),
        # coming over the front on its way down
        (7, "LINEAR", dict(lean=8.0, twist=4.0, hands=(1.5, 3.0, 6.0), shR=(0.0, -1.0, -1.5), shL=(0.0, -1.0, -1.5),
                           sword=(-24.0, 70.0, "lead"), roll_ofs=40.0, legR=(16.0, 6.0, 6.0), legL=(-18.0, 6.0, 16.0))),
        # the plant: head down into the ground in front, the whole weight on the haft
        (8, "LINEAR", dict(lean=26.0, twist=0.0, hands=(0.0, -3.0, 6.5), shR=(0.0, 0.0, -1.0), shL=(0.0, 0.0, -1.0),
                           sword_world=True, sword=(0.0, -72.0, "lead"), roll_ofs=55.0, legR=(26.0, 8.0, 6.0),
                           legL=(-28.0, 8.0, 18.0))),
        (10, "OUTQUAD", dict(lean=29.0, hands=(0.0, -3.5, 6.5), sword=(0.0, -76.0, "prev"), legR=(28.0, 9.0, 6.0),
                             legL=(-30.0, 9.0, 18.0))),
    ]
    # the hold: braced on the haft, a tremble that grows as the well deepens
    shake = [0.0, 0.6, -0.4, 0.8, -0.7, 0.3, 0.9, -0.8, 0.5, -0.9, 0.7, -0.3, 0.9, -0.6, 0.4, -0.8]
    for i, t in enumerate(range(13, 38, 2)):
        grow = 0.4 + 0.6 * i / 12.0
        f = shake[i % len(shake)] * grow
        keys.append((t, "LINEAR", dict(lean=29.0 + 0.8 * f, tilt=0.8 * f, sword=(0.6 * f, -76.0 + 1.0 * f, "prev"))))
    keys += [
        # the Collapse: a jolt down onto the haft
        (38, "OUTQUAD", dict(lean=33.0, tilt=0.0, sword=(0.0, -80.0, "prev"), legR=(31.0, 10.0, 6.0), legL=(-33.0, 10.0, 18.0))),
        (41, "INOUTSINE", dict(lean=24.0, legR=(24.0, 7.0, 6.0), legL=(-26.0, 7.0, 18.0))),
        # wrench it out and back to ready
        (45, "INOUTSINE", dict(lean=12.0, hands=(0.0, -2.0, 5.5), sword_world=False, sword=(-4.0, 10.0, 0.0),
                               shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0), legR=(12.0, 4.0, 6.0), legL=(-14.0, 4.0, 14.0),
                               rise=0.0)),
        (51, "INOUTSINE", dict(READY_SYM, rise=0.0)),
    ]
    return keys


MAUL_WELL = dict(
    desc="Gravity Well: the hammer raised and planted head-down in front (startup 8), then leaned on while the "
         "well pulls for 30 ticks, a jolt at the Collapse, wrenched out. 51 ticks, active 8-38.",
    end=51, startup=8, active=[8, 38], contact=8, keys=_maul_well_keys(),
)


MOVES = {
    "meridian_l1": L1,
    "meridian_l2": L2,
    "meridian_l3": L3,
    "meridian_charge": CHARGE,
    "meridian_line": LINE,
    "meridian_falling_star": FALLING_STAR,
    "meridian_falling_star_land": FALLING_STAR_LAND,
    "meridian_pass": PASS,
    "meridian_zenith": ZENITH,
    "combat_dash_forward": DASH_FWD,
    "combat_dash_back": DASH_BACK,
    "combat_dash_side": DASH_SIDE,
    "combat_parry": PARRY,
    "combat_parry_success": PARRY_SUCCESS,
    "combat_stagger": STAGGER,
    "maul_l1": MAUL_L1,
    "maul_l2": MAUL_L2,
    "maul_l3": MAUL_L3,
    "maul_charge": MAUL_CHARGE,
    "maul_crater": MAUL_CRATER,
    "maul_meteorfall": MAUL_METEORFALL,
    "maul_meteorfall_land": MAUL_METEORFALL_LAND,
    "maul_ram": MAUL_RAM,
    "maul_well": MAUL_WELL,
    "maul_dash_forward": MAUL_DASH_FWD,
    "maul_dash_back": MAUL_DASH_BACK,
    "maul_stagger": MAUL_STAGGER,
}

# The Binary Edges' animations (a sickle in each hand) live in their own file.
from moves_edges import EDGES_MOVES  # noqa: E402

MOVES.update(EDGES_MOVES)

# The Choir Astrolabe's (one-handed, G7).
from moves_astrolabe import ASTROLABE_MOVES  # noqa: E402

MOVES.update(ASTROLABE_MOVES)

# Last Light's (the Heliarch's glaive, G9b).
from moves_lastlight import LASTLIGHT_MOVES  # noqa: E402

MOVES.update(LASTLIGHT_MOVES)

# The Umbra Cantor's (the Heliarch's longbow, one-handed, G9b).
from moves_cantor import CANTOR_MOVES  # noqa: E402

MOVES.update(CANTOR_MOVES)
