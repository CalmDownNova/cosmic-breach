"""The boss shrines (Aetheria 1.1, task B4), in D (the mod's key):

    shrine/save   a shrine keeps your place: a struck crystal on D5, a rising D, F#, A, D of glass over a soft sung D
    shrine/kept   waking with everything: a reversed glass shimmer swelling into a warm D and A

Levels (offsets from combat/hit's -15 LUFS): save -4, kept -5.

Run:  python tools/sound/build.py shrine/
"""
from __future__ import annotations

import numpy as np

from dsp import ad, db, fade, norm, place, rng, silence, timeline
from event import Event
from layers import AH_BRIGHT, BAR, GLASS, chime, reverb, sung, tail

D4, A4, D5, FS5, A5, D6 = 293.66, 440.0, 587.33, 739.99, 880.0, 1174.66


def ting(hz, seconds=1.0):
    return tail(chime(hz, seconds, ratios=BAR, amps=(1.0, 0.18, 0.06, 0.0), taus=(0.35, 0.12, 0.05, 0.02)))


def save(seed=9201):
    r = rng(seed)
    length = 2.0
    t = timeline(length)
    out = silence(length)
    place(out, tail(chime(D5, 1.6, ratios=GLASS, amps=(1.0, 0.3, 0.1, 0.03), taus=(0.6, 0.25, 0.1, 0.04))), 0.0, 1.0)
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, ting(hz, 1.2), 0.12 + 0.09 * i, db(-4 - i))
    pad = sung(np.full_like(t, D4), AH_BRIGHT, r, rolloff=1.8, f_max=4000.0) * ad(t, 0.3, 0.7, hold=0.3)
    out = norm(out) + 0.2 * norm(pad)
    return reverb(fade(norm(out), 0.003, 0.4), r, wet=0.3, t60=1.8, damp_hz=7000.0)


def kept(seed=9211):
    r = rng(seed)
    length = 2.2
    shimmer = silence(1.2)
    for i in range(12):
        place(shimmer, ting(A5 * (1.0 + 0.06 * i), 0.4), 0.06 * i, db(-6 - 0.5 * i))
    out = silence(length)
    place(out, shimmer[::-1].copy(), 0.0, db(-4))
    for hz, g in ((D4, 0.0), (A4, -3.0), (D5, -5.0)):
        place(out, ting(hz, 1.2), 1.15, db(g))
    return reverb(fade(norm(out), 0.01, 0.4), r, wet=0.3, t60=1.6, damp_hz=7000.0)


EVENTS = [
    Event("shrine/save", "A shrine chimes", [save], length=2.0, level=-4.0, fade_out=0.4),
    Event("shrine/kept", "A shrine gives back what you carried", [kept], length=2.2, level=-5.0, fade_out=0.4),
]
