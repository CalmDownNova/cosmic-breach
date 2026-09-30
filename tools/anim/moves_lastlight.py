"""Choreography for Last Light, the solar glaive (GDD 7.3, G9b): a two-handed polearm held by Meridian's grip (1.5x, so
the blade reaches far), whose light combo is three thrusts. Every move starts and ends on Meridian's ready guard, so
the engine's shared animations (parry, its success) fade in and out cleanly; the glaive's own dashes and stagger keep
its long haft out of the legs. The blade is single-edged and curved: keys give its roll explicitly or as "lead"
(true_edge keeps the edge where it is put, never flipped to the other side).

Frame data from data/cosmicbreach/combat/moves/last_light/: the contact pose is on the move's first active tick.

  L1 Dawn Thrust (4/2/6): the hands draw back a little and drive the point straight ahead at chest height, from the
      right hip (the game shows thrusts from the eyes a little to the right and framed nearly level, so the glaive runs
      from the lower right toward a point beside the crosshair, never over it).
  L2 Rising Ray (4/2/7): from low on the left, a thrust rising up into the target, stopping short of the eye line.
  L3 Noon Lance (6/3/10): a deep coil, then a lunging thrust with a long step, the body behind it.
  Charge (loop): the glaive drawn back along the right side, point low and forward, light pooling in the blade.
  Sunspear (4/3/12): from the charge, the biggest lunge: the whole body behind the point.
  Sunfall (plunge): point down under the body, reverse grip; its landing drives it into the ground.
  Sunstreak (2/3/8): a running thrust out of the dash.
  Dawnguard (3/30/8): the glaive raised crosswise before the face, blade high: a guard held for the stance.
"""

from __future__ import annotations

from moves import READY_POSE, READY_SYM

GUARD = dict(READY_POSE)


def move(desc, end, keys, startup, active, contact, **extra):
    return dict(desc=desc, end=end, base=GUARD, keys=keys, startup=startup, active=active, contact=contact, **extra)


START = (0, "OUTSINE", dict(true_edge=True))


def settle(tick):
    return (tick, "INOUTSINE", dict(READY_SYM, fwd=0.0, shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0), true_edge=True))


L1 = move("Dawn Thrust: a short draw back, then the point driven straight ahead at chest height. 12 ticks, active 4-5.",
          12, [
              START,
              # draw back: hands to the chest, weight back, point level
              (2, "INQUAD", dict(twist=14.0, lean=2.0, hands=(1.0, -2.5, 3.0), sword=(-10.0, 4.0, 90.0),
                                 legR=(10.0, 4.0, 8.0), legL=(-12.0, 4.0, 16.0))),
              # contact: the arms and hips drive the point out level
              (4, "LINEAR", dict(twist=-8.0, lean=12.0, hands=(5.0, -2.0, 8.0), shR=(0.0, 0.0, -1.5), shL=(0.0, 0.0, -1.5),
                                 sword=(8.0, 10.0, 90.0), legR=(16.0, 4.0, 6.0), legL=(-18.0, 4.0, 16.0), fwd=1.0)),
              (5, "OUTQUAD", dict(twist=-10.0, lean=13.0, hands=(5.0, -2.0, 8.5), sword=(10.0, 11.0, 90.0))),
              (7, "INOUTSINE", dict(twist=-6.0, lean=10.0, hands=(4.0, -2.5, 7.0), sword=(7.0, 12.0, 90.0))),
              settle(12),
          ], startup=4, active=[4, 5], contact=4)

L2 = move("Rising Ray: from low on the left, a thrust that rises up through the target. 13 ticks, active 4-5.",
          13, [
              START,
              # gather low on the left
              (2, "INQUAD", dict(twist=-16.0, lean=14.0, hands=(-0.5, -6.0, 5.0), sword=(8.0, -16.0, 90.0),
                                 legR=(14.0, 4.0, 6.0), legL=(-16.0, 4.0, 18.0))),
              # contact: rising up the middle, the point climbing past the chest
              (4, "LINEAR", dict(twist=6.0, lean=9.0, hands=(4.5, -1.5, 8.0), shR=(0.0, -0.5, -1.5), shL=(0.0, -0.5, -1.5),
                                 sword=(-6.0, 13.0, 90.0), legR=(15.0, 4.0, 6.0), legL=(-17.0, 4.0, 16.0), fwd=1.0)),
              (5, "OUTQUAD", dict(twist=10.0, lean=6.0, hands=(4.5, 0.0, 8.0), sword=(-10.0, 19.0, 90.0))),
              (8, "INOUTSINE", dict(twist=8.0, lean=6.0, hands=(4.0, -1.0, 7.0), sword=(-8.0, 21.0, 90.0))),
              settle(13),
          ], startup=4, active=[4, 5], contact=4)

L3 = move("Noon Lance: a deep coil, then a lunging thrust with a long step, the body behind the point. 19 ticks, "
          "active 6-8.", 19, [
              START,
              # coil: the glaive drawn back along the right side, weight on the back leg
              (3, "OUTQUAD", dict(twist=30.0, lean=0.0, hands=(2.5, -3.0, 2.0), sword=(-22.0, 2.0, 90.0),
                                  legR=(8.0, 5.0, 10.0), legL=(-14.0, 5.0, 22.0))),
              (5, "INCUBIC", dict(twist=34.0, lean=-2.0, hands=(3.0, -3.0, 1.5), sword=(-26.0, 0.0, 90.0))),
              # contact: the lunge lands with the point far out
              (6, "LINEAR", dict(twist=-14.0, lean=20.0, hands=(5.0, -2.0, 8.5), shR=(0.0, 0.0, -2.0), shL=(0.0, 0.0, -2.0),
                                 sword=(14.0, 17.0, 90.0), legR=(30.0, 5.0, 6.0), legL=(-32.0, 5.0, 18.0), fwd=3.0)),
              (8, "OUTQUAD", dict(twist=-18.0, lean=22.0, hands=(5.0, -2.0, 9.0), sword=(18.0, 19.0, 90.0),
                                  legR=(32.0, 5.0, 6.0), legL=(-34.0, 5.0, 18.0))),
              (11, "INOUTSINE", dict(twist=-14.0, lean=18.0, hands=(4.0, -2.5, 7.5), sword=(14.0, 18.0, 90.0))),
              (14, "INOUTSINE", dict(twist=-6.0, lean=12.0, hands=(1.0, -3.0, 6.5), sword=(8.0, 16.0, 90.0),
                                     legR=(16.0, 4.0, 6.0), legL=(-18.0, 4.0, 16.0))),
              settle(19),
          ], startup=6, active=[6, 8], contact=6)

CHARGE_POSE = dict(twist=32.0, lean=10.0, tilt=2.0, hands=(3.0, -5.0, 2.5), sword=(-22.0, -2.0, 90.0),
                   legR=(10.0, 5.0, 10.0), legL=(-18.0, 5.0, 24.0), true_edge=True)


def _charge_keys():
    """1 s loop: a slow breath, the point trembling a little as the light gathers."""
    import math
    keys = []
    rnd = [0.0, 0.7, -0.5, 0.6, -0.8, 0.3, 0.6, -0.6, 0.2, -0.7, 0.8, -0.3, 0.5, -0.6, 0.4, -0.2, 0.7, -0.8, 0.1, -0.4]
    for t in range(0, 21):
        k = t % 20
        breath = math.sin(2 * math.pi * k / 20.0)
        amp = 1.0 if k else 0.0
        p = dict(CHARGE_POSE)
        p["lean"] = CHARGE_POSE["lean"] + 1.0 * breath
        y, pi, r = CHARGE_POSE["sword"]
        p["sword"] = (y + 1.0 * rnd[k] * amp, pi + 0.8 * breath, r)
        keys.append((t, "LINEAR", p))
    return keys


CHARGE = dict(desc="Charging the Sunspear: the glaive drawn back along the right side, point low and forward. 1 s loop.",
              end=19, base=dict(GUARD, **CHARGE_POSE), keys=_charge_keys(), loop=dict(ret=0), tail_ease="LINEAR")

SUNSPEAR = move("Sunspear: out of the charge, the biggest lunge, the whole body behind the point. 19 ticks, active 4-6.",
                19, [
                    (0, "OUTSINE", dict(CHARGE_POSE)),
                    (2, "INQUAD", dict(twist=36.0, lean=6.0, hands=(3.0, -4.0, 1.5), sword=(-26.0, -2.0, 90.0))),
                    (4, "LINEAR", dict(twist=-16.0, lean=24.0, hands=(5.0, -2.0, 9.0), shR=(0.0, 0.0, -2.0), shL=(0.0, 0.0, -2.0),
                                       sword=(16.0, 22.0, 90.0), legR=(34.0, 5.0, 6.0), legL=(-36.0, 5.0, 18.0), fwd=4.0)),
                    (6, "OUTQUAD", dict(twist=-20.0, lean=26.0, hands=(5.0, -2.0, 9.5), sword=(20.0, 24.0, 90.0))),
                    (10, "INOUTSINE", dict(twist=-14.0, lean=20.0, hands=(4.0, -2.5, 7.5), sword=(14.0, 20.0, 90.0))),
                    (14, "INOUTSINE", dict(twist=-6.0, lean=12.0, hands=(1.0, -3.0, 6.5), sword=(8.0, 16.0, 90.0),
                                           legR=(16.0, 4.0, 6.0), legL=(-18.0, 4.0, 16.0))),
                    settle(19),
                ], startup=4, active=[4, 6], contact=4)
SUNSPEAR["base"] = dict(GUARD, **CHARGE_POSE)

DIVE = dict(air=True, lean=24.0, twist=0.0, tilt=0.0, hands=(0.0, -3.0, 5.0), sword_world=True,
            sword=(0.0, -90.0, 90.0), legR=(52.0, 6.0, 4.0), legL=(34.0, 6.0, 4.0), true_edge=True)


def _dive_keys():
    keys = [(0, "OUTQUAD", dict(air=True, true_edge=True)), (2, "LINEAR", dict(DIVE))]
    flutter = [0.0, 0.7, -0.4, 0.9, -0.8, 0.3, 0.6, -0.9, 0.5, -0.3, 0.8, -0.6]
    for i in range(1, 12):
        t = 2 + i
        f = flutter[i]
        p = dict(DIVE)
        p["legR"] = (DIVE["legR"][0] + 2.0 * f, DIVE["legR"][1], DIVE["legR"][2])
        p["legL"] = (DIVE["legL"][0] - 2.4 * f, DIVE["legL"][1], DIVE["legL"][2])
        p["tilt"] = 1.2 * f
        keys.append((t, "LINEAR", p))
    keys.append((14, "LINEAR", dict(DIVE)))
    return keys


SUNFALL = dict(desc="Sunfall dive: point down under the body, a reverse grip, then the hold looping while it falls.",
               end=13, loop=dict(ret=2), keys=_dive_keys(), base=GUARD, tail_ease="LINEAR", fp_pitch=60.0)

SUNFALL_LAND = dict(
    desc="Sunfall landing: the point driven into the ground at the feet, knees bent, rising back to the guard. 12 ticks.",
    end=12, startup=0, active=[0, 0], contact=0,
    base=dict(GUARD, **DIVE),
    keys=[
        (0, "OUTQUAD", dict(air=False, plant2=True, lean=28.0, hands=(0.0, -6.0, 4.5), legR=(46.0, 6.0, 8.0),
                            legL=(-60.0, 5.0, 20.0), true_edge=True)),
        (2, "INOUTSINE", dict(lean=31.0, legR=(50.0, 6.0, 8.0), legL=(-64.0, 5.0, 20.0), hands=(0.0, -6.5, 4.5))),
        (5, "INOUTSINE", dict(lean=29.0)),
        (8, "INOUTSINE", dict(lean=14.0, sword=(0.0, -45.0, 90.0), legR=(18.0, 4.0, 7.0), legL=(-24.0, 4.0, 16.0),
                              hands=(0.0, -5.0, 5.5))),
        (12, "INOUTSINE", dict(READY_SYM, sword_world=True, true_edge=True)),
    ],
)

SUNSTREAK = move("Sunstreak: a running thrust out of the dash, low and far. 13 ticks, active 2-4.", 13, [
    START,
    (1, "INQUAD", dict(twist=18.0, lean=20.0, hands=(1.5, -3.0, 4.0), sword=(-10.0, 16.0, 90.0),
                       legR=(22.0, 3.0, 4.0), legL=(-30.0, 3.0, 10.0))),
    (2, "LINEAR", dict(twist=-10.0, lean=26.0, hands=(5.0, -2.5, 8.5), shR=(0.0, 0.0, -2.0), shL=(0.0, 0.0, -2.0),
                       sword=(10.0, 23.0, 90.0), legR=(28.0, 3.0, 4.0), legL=(-34.0, 3.0, 12.0), fwd=2.0)),
    (4, "OUTQUAD", dict(twist=-14.0, lean=26.0, hands=(5.0, -2.5, 9.0), sword=(14.0, 23.0, 90.0))),
    (7, "INOUTSINE", dict(twist=-8.0, lean=16.0, hands=(4.0, -3.0, 7.0), sword=(8.0, 18.0, 90.0),
                          legR=(16.0, 3.0, 6.0), legL=(-20.0, 3.0, 12.0))),
    settle(13),
], startup=2, active=[2, 4], contact=2)

HIGH_CROSS = dict(twist=6.0, lean=-2.0, hands=(0.0, 4.5, 4.5), shR=(0.0, -2.0, -1.0), shL=(0.0, -2.0, -1.0),
                  sword=(-62.0, 34.0, 90.0), legR=(12.0, 5.0, 8.0), legL=(-14.0, 5.0, 16.0), true_edge=True)


def _dawnguard_keys():
    """Raise the glaive crosswise before the face (3 ticks), hold it through the stance with a slow breath, lower it."""
    import math
    keys = [START, (3, "OUTQUAD", dict(HIGH_CROSS))]
    for t in range(5, 33, 2):
        b = math.sin(2 * math.pi * (t - 3) / 14.0)
        p = dict(HIGH_CROSS)
        p["lean"] = HIGH_CROSS["lean"] + 1.2 * b
        h = HIGH_CROSS["hands"]
        p["hands"] = (h[0], h[1] + 0.3 * b, h[2])
        keys.append((t, "LINEAR", p))
    keys.append((33, "INOUTSINE", dict(HIGH_CROSS)))
    keys.append(settle(41))
    return keys


DAWNGUARD = move("Dawnguard: the glaive raised crosswise before the face, blade high, held through the stance. "
                 "41 ticks, active 3-32.", 41, _dawnguard_keys(), startup=3, active=[3, 32], contact=3)

# The engine's dashes and stagger with the glaive's long haft kept out of the legs.
DASH_FWD = dict(
    desc="Dash forward with the glaive: a low fast lean, the glaive trailed back along the right side. 8 ticks.",
    end=8, base=GUARD,
    keys=[
        (0, "OUTQUAD", dict(true_edge=True)),
        (2, "LINEAR", dict(lean=28.0, hands=(3.0, -4.0, 2.5), sword=(20.0, 8.0, 90.0), legR=(26.0, 3.0, 4.0),
                           legL=(-34.0, 3.0, 10.0))),
        (5, "INOUTSINE", dict(lean=26.0)),
        (7, "INOUTSINE", dict(lean=14.0, hands=(1.5, -3.5, 5.0), sword=(4.0, 20.0, 90.0), legR=(14.0, 3.0, 5.0),
                              legL=(-16.0, 3.0, 12.0))),
        settle(8),
    ],
)

DASH_BACK = dict(
    desc="Dash back with the glaive: leaning back into the backstep, the point held low in front. 8 ticks.",
    end=8, base=GUARD,
    keys=[
        (0, "OUTQUAD", dict(true_edge=True)),
        (2, "LINEAR", dict(lean=-18.0, hands=(0.5, -4.0, 6.0), sword=(-4.0, 6.0, 90.0), legR=(22.0, 3.0, 4.0),
                           legL=(-30.0, 3.0, 12.0))),
        (5, "INOUTSINE", dict(lean=-15.0)),
        settle(8),
    ],
)

STAGGER = dict(
    desc="Stagger with the glaive: recoil, the torso thrown back, the glaive swung up and away, recovering. 10 ticks.",
    end=10, base=GUARD,
    keys=[
        (0, "OUTQUAD", dict(true_edge=True)),
        (2, "OUTSINE", dict(lean=-20.0, tilt=-6.0, twist=10.0, hands=(2.0, -2.0, 4.0), sword=(40.0, 30.0, 90.0),
                            legR=(4.0, 4.0, 6.0), legL=(-24.0, 5.0, 14.0))),
        (4, "INOUTSINE", dict(lean=-16.0, tilt=-4.0)),
        (7, "INOUTSINE", dict(lean=2.0, tilt=0.0, twist=6.0, hands=(1.0, -3.0, 5.5), sword=(0.0, 30.0, 90.0))),
        settle(10),
    ],
)

LASTLIGHT_MOVES = {
    "lastlight_l1": L1,
    "lastlight_l2": L2,
    "lastlight_l3": L3,
    "lastlight_charge": CHARGE,
    "lastlight_sunspear": SUNSPEAR,
    "lastlight_sunfall": SUNFALL,
    "lastlight_sunfall_land": SUNFALL_LAND,
    "lastlight_sunstreak": SUNSTREAK,
    "lastlight_dawnguard": DAWNGUARD,
    "lastlight_dash_forward": DASH_FWD,
    "lastlight_dash_back": DASH_BACK,
    "lastlight_stagger": STAGGER,
}
