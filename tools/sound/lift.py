"""The rising air (Aetheria 1.1, tasks B3 and B6): lift/stream, a seamless 4 s loop for the boss 2 arena's air vents and
the rising currents under the shrines. Rushing air with one slow swell a loop and a soft hollow whistle on D that comes
and goes twice; every partial is a whole number of cycles in 4 s and the noise is circular (made in the frequency
domain), so the loop has no seam. Heard from 48 blocks (attenuation_distance in sounds.json).

Run:  python tools/sound/build.py lift/
"""
from __future__ import annotations

import numpy as np

from dsp import SR, TWO_PI, norm, rng, timeline
from event import Event

SECONDS = 4.0


def stream():
    t = timeline(SECONDS)
    n = len(t)
    r = rng(9101)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    logf = np.log2(np.maximum(freqs, 20.0))
    shape = np.exp(-((logf - np.log2(900.0)) ** 2) / 1.6) + 0.25 * np.exp(-((logf - np.log2(3500.0)) ** 2) / 0.8)
    air = np.fft.irfft(np.fft.rfft(r.standard_normal(n)) * shape, n)
    swell = 0.75 + 0.25 * np.sin(TWO_PI * t / SECONDS)
    whistle = np.zeros_like(t)
    for f, a in ((587.25, 1.0), (880.0, 0.5), (1174.5, 0.25)):   # D5, A5, D6: whole cycles in 4 s
        whistle += a * np.sin(TWO_PI * f * t + r.uniform(0.0, TWO_PI))
    whistle *= 0.5 + 0.5 * np.sin(TWO_PI * 2.0 * t / SECONDS) ** 2
    return norm(norm(air) * swell + 0.06 * norm(whistle))


EVENTS = [
    Event("lift/stream", "Air rushes upward", [stream], length=SECONDS, level=-9.0, loop=True, quality=6),
]
