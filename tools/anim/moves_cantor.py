"""Choreography for the Umbra Cantor, the Heliarch's longbow (GDD 7.3, G9b): held in the right hand by the apex of its
arc (grip 0), the free left hand drawing the string. The bow's "sword" is its length (the string's line, up to the upper
tip) and its edge side (the solver's E) is the way the arrow flies, so an aimed bow is nearly upright with its edge
forward; true_edge keeps it from being flipped to the other side.

The guard (CANTOR_READY): the bow held low at the right side, upright, the left hand loose. Every move starts and ends
on it. Frame data from data/cosmicbreach/combat/moves/umbra_cantor/: the loose is on the move's first active tick.

  L1, L2 Staccato (4/1/7): the bow comes up, the left hand draws and looses: a quick shot.
  L3 Tenuto (7/1/10): a longer draw, the body turned further, a heavier loose.
  Draw (loop): the charged shot's hold, at full draw, a tremble in the string hand.
  Fermata (2/1/10): the release from the full draw: the string hand flung back and open.
  Grace Note (3/1/8, in the air): the bow tipped down, two quick arrows loosed below.
  Syncopation (1/1/6): a turn out of the dash with a snap shot.
  Cadence (2/11/6): three draws and looses in a row (active ticks 0, 5, 10), the bow held up throughout.
"""

from __future__ import annotations

from moves import READY_POSE

CANTOR_READY_POSE = dict(READY_POSE, grip=0.0, lean=3.0, twist=0.0, tilt=0.0,
                         legR=(8.0, 3.0, 6.0), legL=(-8.0, 3.0, 14.0),
                         hands=(5.0, -6.0, 3.5), sword=(8.0, 72.0, 0.0),
                         left=(-18.0, 6.0, -10.0), shR=(0.0, 0.0, 0.0), shL=(0.0, 0.0, 0.0), true_edge=True)
CANTOR_READY = {k: CANTOR_READY_POSE[k] for k in ("lean", "twist", "tilt", "fwd", "side", "rise", "legR", "legL", "hands",
                                                 "sword", "grip", "left", "shR", "shL", "rz", "lz", "true_edge")}
CANTOR_READY_SYM = dict(CANTOR_READY, canonical=True)

# The aim: turned so the bow arm points at the target, the bow upright before it, the left hand at the string.
AIM = dict(twist=-55.0, lean=4.0, hands=(8.0, 1.5, 4.5), sword=(55.0, 86.0, 0.0), left=(-76.0, 79.0, 30.0),
           legR=(6.0, 4.0, 30.0), legL=(-10.0, 4.0, 40.0))
# at full draw the string hand is anchored under the chin
DRAWN = dict(AIM, left=(-118.0, 94.0, 0.0), lean=2.0)
LOOSED = (-60.0, 30.0, -40.0)


def move(desc, end, keys, startup, active, contact, **extra):
    return dict(desc=desc, end=end, base=CANTOR_READY_POSE, keys=keys, startup=startup, active=active, contact=contact, **extra)


START = (0, "OUTSINE", {})


def settle(tick):
    return (tick, "INOUTSINE", CANTOR_READY_SYM)


def shot(desc, end, startup, recovery_hold, heavy=False):
    """Up, draw, loose on the first active tick, the string hand flung back, then down to the guard."""
    drawn = dict(DRAWN, left=(-121.0, 100.0, 0.0)) if heavy else DRAWN
    keys = [
        START,
        (max(1, startup - 2), "OUTQUAD", dict(AIM)),
        (startup - 1 if startup > 2 else startup, "INQUAD", dict(drawn)),
        (startup, "LINEAR", dict(drawn, left=LOOSED, lean=5.0)),
        (startup + 2, "OUTSINE", dict(AIM, left=(-45.0, 10.0, -45.0))),
        (startup + 2 + recovery_hold, "INOUTSINE", dict(AIM, left=(-30.0, 10.0, -30.0))),
        settle(end),
    ]
    return move(desc, end, keys, startup=startup, active=[startup, startup], contact=startup)


L1 = shot("Staccato: up, a quick draw, loosed; the string hand flies back. 12 ticks, active 4.", 12, 4, 2)
L2 = shot("Staccato again (the chain's second quick shot). 12 ticks, active 4.", 12, 4, 2)
L3 = shot("Tenuto: a longer draw, turned further, a heavier loose. 18 ticks, active 7.", 18, 7, 3, heavy=True)


def _draw_keys():
    """1 s loop at full draw: a slow breath and a small tremble in the string hand."""
    import math
    keys = []
    rnd = [0.0, 0.6, -0.4, 0.7, -0.8, 0.2, 0.6, -0.5, 0.3, -0.7, 0.8, -0.2, 0.4, -0.6, 0.5, -0.3, 0.7, -0.8, 0.2, -0.4]
    for t in range(0, 21):
        k = t % 20
        breath = math.sin(2 * math.pi * k / 20.0)
        amp = 1.0 if k else 0.0
        p = dict(DRAWN, left=(-118.0 + 0.8 * rnd[k] * amp, 94.0 + 0.8 * rnd[(k + 5) % 20] * amp, 0.0))
        p["lean"] = DRAWN["lean"] + 0.8 * breath
        keys.append((t, "LINEAR", p))
    return keys


DRAW = dict(desc="Drawing the charged shot: at full draw, a slow breath and a tremble in the string hand. 1 s loop.",
            end=19, base=dict(CANTOR_READY_POSE, **DRAWN), keys=_draw_keys(), loop=dict(ret=0), tail_ease="LINEAR")

FERMATA = move("Fermata: loosed from the full draw, the string hand flung back and open. 13 ticks, active 2.", 13, [
    (0, "OUTSINE", dict(DRAWN)),
    (1, "INQUAD", dict(DRAWN, left=(-120.0, 96.0, 0.0))),
    (2, "LINEAR", dict(DRAWN, left=LOOSED, lean=6.0)),
    (5, "OUTSINE", dict(AIM, left=(-45.0, 10.0, -45.0), lean=5.0)),
    (8, "INOUTSINE", dict(AIM, left=(-30.0, 10.0, -30.0))),
    settle(13),
], startup=2, active=[2, 2], contact=2)
FERMATA["base"] = dict(CANTOR_READY_POSE, **DRAWN)

GRACE_AIM = dict(twist=-45.0, lean=24.0, hands=(7.0, -1.0, 5.0), sword=(45.0, 48.0, 0.0), left=(-70.0, 70.0, 20.0),
                 legR=(40.0, 3.0, 5.0), legL=(20.0, 3.0, 12.0), air=True)
GRACE_NOTE = move("Grace Note: in the air the bow tips down and two quick arrows go below. 12 ticks, active 3.", 12, [
    (0, "OUTSINE", dict(air=True)),
    (2, "INQUAD", dict(GRACE_AIM)),
    (3, "LINEAR", dict(GRACE_AIM, left=LOOSED)),
    (6, "OUTSINE", dict(GRACE_AIM, left=(-45.0, 10.0, -45.0), lean=18.0)),
    (12, "INOUTSINE", dict(CANTOR_READY_SYM, air=True)),
], startup=3, active=[3, 3], contact=3)

SYNCOPATION = move("Syncopation: out of the dash a quick turn and a snap shot. 8 ticks, active 1.", 8, [
    START,
    (1, "LINEAR", dict(AIM, twist=-60.0, lean=10.0, left=LOOSED)),
    (3, "OUTSINE", dict(AIM, twist=-58.0, lean=8.0, left=(-45.0, 10.0, -45.0))),
    settle(8),
], startup=1, active=[1, 1], contact=1)


def _cadence_keys():
    keys = [START, (1, "OUTQUAD", dict(AIM)), (2, "LINEAR", dict(DRAWN, left=LOOSED))]
    for i, t in enumerate((2, 7, 12)):
        if i > 0:
            keys.append((t - 2, "INQUAD", dict(DRAWN)))
            keys.append((t, "LINEAR", dict(DRAWN, left=LOOSED, twist=-55.0 - 3.0 * i)))
        keys.append((t + 1, "OUTSINE", dict(AIM, twist=-55.0 - 3.0 * i)))
    keys.append((15, "INOUTSINE", dict(AIM, left=(-30.0, 10.0, -30.0))))
    keys.append(settle(19))
    return keys


CADENCE = move("Cadence: three draws and looses in a row (active ticks 0, 5, 10), the bow held up throughout. 19 ticks.",
               19, _cadence_keys(), startup=2, active=[2, 12], contact=2)

CANTOR_MOVES = {
    "cantor_l1": L1,
    "cantor_l2": L2,
    "cantor_l3": L3,
    "cantor_draw": DRAW,
    "cantor_fermata": FERMATA,
    "cantor_grace_note": GRACE_NOTE,
    "cantor_syncopation": SYNCOPATION,
    "cantor_cadence": CADENCE,
}
