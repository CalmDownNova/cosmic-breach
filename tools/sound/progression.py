"""Progression sounds: Attunement cues.

Tuning follows the rest of the set (D major pentatonic, D E F# A B), so a
level-up that lands during a fight never clashes with the combat chimes.
"""
from __future__ import annotations

import numpy as np

from dsp import TWO_PI, ad, db, norm, note, place, rng, silence, timeline
from event import Event
from layers import BAR, chime, click, reverb


def level_up(seed=501, length=1.5):
    """A bright rising chime: D6 F#6 A6 D7 struck in quick succession, a sparkle on top, a long glassy tail."""
    r = rng(seed)
    out = silence(length)
    steps = (("D6", 0.000, 0.0), ("F#6", 0.075, -1.0), ("A6", 0.150, -1.5), ("D7", 0.225, 0.0))
    for name, when, gain in steps:
        f0 = note(name)
        tail = length - when
        body = chime(f0, tail, amps=(1.0, 0.22, 0.07, 0.02), taus=(0.42, 0.16, 0.06, 0.03),
                     beats=(1.3, 2.4, 0.0, 0.0), attack=0.0015)
        bar = chime(f0 * 2.0, tail, ratios=BAR, amps=(0.35, 0.1, 0.03, 0.01),
                    taus=(0.12, 0.05, 0.025, 0.012), beats=(2.1, 0.0, 0.0, 0.0), attack=0.001)
        place(out, norm(body + bar), when, db(gain))
        place(out, click(r, 0.0005, 5000.0), when, db(gain - 20))
    t = timeline(length)
    sparkle_start = 0.26
    for when, name in ((sparkle_start, "A7"), (sparkle_start + 0.05, "D8"), (sparkle_start + 0.1, "F#8")):
        c = chime(note(name), 0.5, amps=(1.0, 0.18, 0.04), taus=(0.1, 0.045, 0.02),
                  beats=(2.6, 0.0, 0.0), attack=0.001)
        place(out, norm(c), when, db(-11))
    shimmer = np.sin(TWO_PI * note("A8") * t) + 0.5 * np.sin(TWO_PI * note("D9") * t + r.uniform(0.0, TWO_PI))
    shimmer *= (1.0 + 0.5 * np.sin(TWO_PI * 11.0 * t)) * ad(t - sparkle_start, 0.08, 0.35) * (t >= sparkle_start)
    place(out, norm(shimmer), 0.0, db(-24))
    return reverb(out, r, wet=0.28, t60=1.4, damp_hz=9000.0, hp_hz=500.0)


EVENTS = [
    Event("progression/level_up", "Attunement rises", [level_up], length=1.5, level=-2.0, fade_out=0.3),
]
