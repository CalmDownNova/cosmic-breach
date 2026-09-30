"""Choreography for the Choir Astrolabe (a catalyst held in the right hand, GDD 4.2): one-handed (grip 0), the free left
hand flicks bolts and shapes the gestures.

The guard (ASTRO_READY): the astrolabe held up before the right shoulder, its disc above the hand and tipped forward, the
left hand loose in front of the body. Every move starts and ends on it. Frame data from
data/cosmicbreach/combat/moves/choir_astrolabe/: the contact pose is on the move's first active tick.

  L1 Star Bolt (3/1/5): the free hand flicks the bolt out, a quick turn of the hips.
  L2 Star Bolt (3/1/5): the other hand: the astrolabe itself thrust forward.
  L3 Triad (5/3/9): the astrolabe raised high, the free arm flung wide as the rings flare.
  Charge (loop): the astrolabe held out, the free hand forward beside it, sighting.
  Constellation (4/4/12): both hands driven forward along the beam.
  Starfall (3/1/8, in the air): the rings flip under you, the astrolabe pointed down.
  Parallax (1/1/5): a turn out of the dash with the astrolabe forward (the afterimage keeps this pose).
  Pocket Star (6/1/10): raised overhead, then thrust at the aim.
  Supernova (2/1/8): the free hand closes into a fist, the astrolabe drawn in.
"""

from __future__ import annotations

from moves import READY_POSE

ASTRO_READY_POSE = dict(READY_POSE, grip=0.0, lean=3.0, twist=0.0, tilt=0.0,
                        legR=(8.0, 3.0, 6.0), legL=(-8.0, 3.0, 14.0),
                        hands=(4.5, -3.5, 5.5), sword=(5.0, 60.0, 0.0),
                        left=(-28.0, 12.0, -12.0), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0))
ASTRO_READY = {k: ASTRO_READY_POSE[k] for k in ("lean", "twist", "tilt", "fwd", "side", "rise", "legR", "legL", "hands",
                                               "sword", "grip", "left", "shR", "shL", "rz", "lz")}
ASTRO_READY_SYM = dict(ASTRO_READY, canonical=True)


def move(desc, end, keys, startup, active, contact, **extra):
    return dict(desc=desc, end=end, base=ASTRO_READY_POSE, keys=keys, startup=startup, active=active, contact=contact, **extra)


START = (0, "OUTSINE", {})


def settle(tick):
    return (tick, "INOUTSINE", ASTRO_READY_SYM)


L1 = move("Star Bolt: the free hand flicks the bolt out. 9 ticks, active 3.", 9, [
    START,
    (2, "INQUAD", dict(twist=12.0, left=(-58.0, 34.0, 12.0))),
    (3, "LINEAR", dict(twist=-10.0, lean=5.0, left=(-94.0, -8.0, -6.0))),
    (5, "OUTSINE", dict(twist=-12.0, left=(-82.0, -14.0, -14.0))),
    settle(9),
], startup=3, active=[3, 3], contact=3)

L2 = move("Star Bolt: the astrolabe thrust forward. 9 ticks, active 3.", 9, [
    START,
    (2, "INQUAD", dict(twist=-10.0, hands=(3.0, -2.0, 3.0), sword=(-4.0, 80.0, 0.0))),
    (3, "LINEAR", dict(twist=14.0, lean=8.0, hands=(3.5, -2.0, 9.0), sword=(6.0, 32.0, 0.0), left=(-20.0, 10.0, -20.0))),
    (5, "OUTSINE", dict(twist=12.0, hands=(3.5, -2.3, 8.5), sword=(8.0, 28.0, 0.0))),
    settle(9),
], startup=3, active=[3, 3], contact=3)

L3 = move("Triad: raised high, the free arm flung wide as the rings flare. 17 ticks, active 5-7.", 17, [
    START,
    (2, "OUTQUAD", dict(lean=-4.0, hands=(4.5, -1.5, 5.0), sword=(10.0, 95.0, 0.0), left=(-60.0, -20.0, -30.0))),
    (4, "INCUBIC", dict(lean=-8.0, hands=(5.0, -1.0, 5.5), sword=(18.0, 105.0, 0.0), left=(-125.0, -25.0, -50.0))),
    (5, "LINEAR", dict(lean=4.0, hands=(5.0, -2.0, 7.0), sword=(15.0, 70.0, 0.0), left=(-110.0, -40.0, -65.0))),
    (7, "OUTSINE", dict(lean=5.0, hands=(5.0, -2.2, 7.0), sword=(15.0, 68.0, 0.0), left=(-104.0, -40.0, -62.0))),
    (10, "INOUTSINE", dict(lean=4.0, hands=(4.0, -2.5, 6.5), sword=(8.0, 62.0, 0.0), left=(-50.0, 0.0, -20.0))),
    settle(17),
], startup=5, active=[5, 7], contact=5)

CHARGE_POSE = dict(lean=6.0, hands=(4.5, -2.5, 7.0), sword=(10.0, 58.0, 0.0), left=(-84.0, 22.0, 0.0))


def _charge_keys():
    """One slow breath a second, looped: the charge pose from its first tick (the engine fades into it)."""
    import math
    keys = []
    for t in range(0, 21, 2):
        b = math.sin(2 * math.pi * t / 20.0)
        p = dict(CHARGE_POSE)
        p["lean"] = CHARGE_POSE["lean"] + 0.8 * b
        h = CHARGE_POSE["hands"]
        p["hands"] = (h[0], h[1] + 0.3 * b, h[2])
        lf = CHARGE_POSE["left"]
        p["left"] = (lf[0] + 1.5 * b, lf[1], lf[2])
        keys.append((t, "LINEAR", p))
    return keys


CHARGE = dict(desc="Charging the Constellation: held out and sighting, a slow breath. 1 s loop.", end=19,
              base=ASTRO_READY_POSE, keys=_charge_keys(), loop=dict(ret=0), tail_ease="LINEAR")

CONSTELLATION = move("Constellation: both hands driven forward along the beam. 20 ticks, active 4-7.", 20, [
    (0, "OUTSINE", dict(CHARGE_POSE)),
    (3, "INQUAD", dict(lean=2.0, hands=(4.5, -1.5, 6.0), sword=(8.0, 75.0, 0.0), left=(-100.0, 10.0, 0.0))),
    (4, "LINEAR", dict(lean=9.0, hands=(4.0, -2.5, 9.0), sword=(5.0, 40.0, 0.0), left=(-96.0, -18.0, -8.0))),
    (7, "OUTSINE", dict(lean=10.0, hands=(4.0, -2.7, 9.0), sword=(5.0, 38.0, 0.0), left=(-94.0, -20.0, -10.0))),
    (12, "INOUTSINE", dict(lean=5.0, hands=(4.0, -2.5, 7.0), sword=(6.0, 55.0, 0.0), left=(-50.0, 5.0, -12.0))),
    settle(20),
], startup=4, active=[4, 7], contact=4)

STARFALL = move("Starfall: in the air the rings flip under you, the astrolabe pointed down. 12 ticks, active 3.", 12, [
    (0, "OUTSINE", dict(air=True)),
    (2, "INQUAD", dict(air=True, lean=18.0, hands=(3.0, -4.0, 6.0), sword=(0.0, -30.0, 0.0), left=(-40.0, -10.0, -40.0),
                       legR=(40.0, 3.0, 5.0), legL=(20.0, 3.0, 12.0))),
    (3, "LINEAR", dict(air=True, lean=22.0, hands=(2.0, -6.0, 7.0), sword=(0.0, -52.0, 0.0), left=(-30.0, -10.0, -50.0))),
    (6, "OUTSINE", dict(air=True, lean=16.0, hands=(2.5, -4.0, 6.5), sword=(0.0, -35.0, 0.0))),
    (12, "INOUTSINE", dict(ASTRO_READY_SYM, air=True)),
], startup=3, active=[3, 3], contact=3)

PARALLAX = move("Parallax: out of the dash, a turn with the astrolabe forward. 7 ticks, active 1.", 7, [
    START,
    (1, "LINEAR", dict(twist=-24.0, lean=10.0, hands=(4.0, -1.0, 7.0), sword=(-20.0, 45.0, 0.0), left=(-30.0, 30.0, -50.0))),
    (3, "OUTSINE", dict(twist=-20.0, lean=8.0, hands=(3.5, -1.5, 6.5), sword=(-16.0, 48.0, 0.0))),
    settle(7),
], startup=1, active=[1, 1], contact=1)

POCKET_STAR = move("Pocket Star: raised overhead, then thrust at the aim. 17 ticks, active 6.", 17, [
    START,
    (3, "OUTQUAD", dict(lean=-6.0, hands=(5.0, -1.0, 5.5), sword=(25.0, 105.0, 0.0), left=(-40.0, -20.0, -30.0))),
    (5, "INCUBIC", dict(lean=-7.0, hands=(5.0, -0.5, 5.0), sword=(25.0, 112.0, 0.0))),
    (6, "LINEAR", dict(lean=10.0, hands=(4.0, -2.0, 9.0), sword=(8.0, 28.0, 0.0), left=(-60.0, -30.0, -40.0))),
    (9, "OUTSINE", dict(lean=9.0, hands=(4.0, -2.2, 8.5), sword=(8.0, 30.0, 0.0))),
    settle(17),
], startup=6, active=[6, 6], contact=6)

SUPERNOVA = move("Supernova: the free hand closes into a fist, the astrolabe drawn in. 11 ticks, active 2.", 11, [
    START,
    (1, "INQUAD", dict(hands=(2.5, -1.5, 4.0), sword=(0.0, 70.0, 0.0), left=(-100.0, 5.0, 0.0))),
    (2, "LINEAR", dict(lean=-4.0, hands=(2.0, -2.0, 3.5), sword=(0.0, 72.0, 0.0), left=(-96.0, 10.0, 4.0))),
    (5, "OUTSINE", dict(lean=2.0, left=(-90.0, 10.0, 4.0))),
    settle(11),
], startup=2, active=[2, 2], contact=2)

ASTROLABE_MOVES = {
    "astrolabe_l1": L1,
    "astrolabe_l2": L2,
    "astrolabe_l3": L3,
    "astrolabe_charge": CHARGE,
    "astrolabe_constellation": CONSTELLATION,
    "astrolabe_starfall": STARFALL,
    "astrolabe_parallax": PARALLAX,
    "astrolabe_pocket_star": POCKET_STAR,
    "astrolabe_supernova": SUPERNOVA,
}
