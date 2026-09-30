"""The Breach Sanctum's sounds (W7, GDD 6.1 and 7.3).

    sanctum/gate_open     the Gate knowing a song: gilded leaves grinding apart over a low D, a hushed choir swell on
                          D and A rising into a bright crystal shimmer
    sanctum/gate_refuse   the Gate staying shut: a dull, damped knock on metal and a low minor second that sours out
    sanctum/lock_lit      an Eclipse Lock lighting: a deep struck bell (D2 with its hum an octave down), then a rising
                          D, F#, A of glass as the corona catches
    sanctum/stair_open    the Throne Seal dissolving: stone giving way in a long descending rumble, a sung D major chord
                          swelling through it and embers crackling apart
    sanctum/throne_hum    the throne holding a Heart nothing answers: a low D hum swelling and fading with a slow beat,
                          a thin glassy overtone, an unsettled flutter (about 3 s)
    sanctum/fall_rescue   the Breach throwing a player back: a rushing sweep up out of the void, a soft thump on the
                          rim, a violet shimmer falling away

Every tuned part in D (the set's key); the refusal's minor second is the point. Levels: stair open -12, lock -14, gate
open -15, rescue -15, refuse -18, hum -19 LUFS (offsets from combat/hit's -15).

Run:  python tools/sound/build.py sanctum
"""
from __future__ import annotations

import numpy as np

from dsp import ad, band, db, fade, n_of, norm, phase_of, place, rng, shaped_noise, silence, softclip, timeline, wobble
from event import Event
from layers import BAR, burst, chime, choir_stab, click, crackle, knock, reverb, tail, thump, whoosh

D2, D3, A3, D4, FS4, A4, D5, FS5, A5, D6, FS6, A6 = (73.42, 146.83, 220.0, 293.66, 369.99, 440.0, 587.33, 739.99,
                                                     880.0, 1174.66, 1479.98, 1760.0)


def grind(r, seconds, lo, hi):
    """Heavy stone or metal sliding: band noise whose centre drifts, roughened."""
    t = timeline(seconds)
    x = shaped_noise(seconds, lambda tt, f: band(f, lo + (hi - lo) * tt / seconds, 1.0), r)
    return tail(norm(x) * (0.7 + 0.3 * wobble(r, len(t), 17.0)))


def gate_open(seed=2101, length=2.6):
    r = rng(seed)
    out = silence(length)
    t = timeline(1.6)
    place(out, tail(grind(r, 1.6, 180.0, 90.0) * ad(t, 0.12, 0.6, hold=0.5), longest=0.08), 0.0, db(-6))
    low = np.sin(2 * np.pi * D2 * 2 * timeline(2.2)) * ad(timeline(2.2), 0.3, 0.8, hold=0.6)
    place(out, tail(low, longest=0.1), 0.0, db(-8))
    swell = choir_stab(r, 2.2, (D4, A4, D5), singers=3, detune_cents=7.0, scoop_cents=8.0, attack=0.55, hold=0.6,
                       tau=0.5, breath=0.07, vib_cents=6.0)
    place(out, swell, 0.25, db(-3))
    for i, hz in enumerate((A5, D6, FS6, A6)):
        place(out, tail(chime(hz, 1.2, ratios=BAR, amps=(1.0, 0.12, 0.03, 0.0), taus=(0.5, 0.15, 0.05, 0.02))), 0.9 + 0.11 * i,
              db(-12 - i))
    return reverb(fade(softclip(norm(out), 1.1), 0.01, 0.4), r, wet=0.4, t60=2.6, damp_hz=6000.0, hp_hz=60.0)


def gate_refuse(seed=2111, length=1.1):
    r = rng(seed)
    out = silence(length)
    place(out, knock(r, 120.0, tau=0.06, ratios=(1.0, 2.1), amps=(1.0, 0.35)), 0.0, db(-2))
    place(out, thump(0.4, 90.0, 55.0, 0.05, 0.16, drive=1.6), 0.0, db(-4))
    t = timeline(0.9)
    sour = (np.sin(2 * np.pi * D3 * t) + 0.8 * np.sin(2 * np.pi * D3 * 2 ** (1 / 12) * t)) * ad(t, 0.04, 0.3)
    place(out, tail(sour, longest=0.06), 0.05, db(-9))
    return reverb(softclip(norm(out), 1.4), r, wet=0.3, t60=1.6, damp_hz=3000.0, hp_hz=50.0)


def lock_lit(seed=2121, length=3.0):
    r = rng(seed)
    out = silence(length)
    bell = chime(D2 * 2, 2.6, ratios=(1.0, 2.0, 2.4, 3.0, 4.2), amps=(1.0, 0.6, 0.35, 0.25, 0.12),
                 taus=(1.4, 0.9, 0.6, 0.4, 0.25), beats=(0.8, 1.3, 0.0, 0.0, 0.0), attack=0.003)
    place(out, bell, 0.0, db(0))
    hum = np.sin(2 * np.pi * D2 * timeline(2.6)) * ad(timeline(2.6), 0.02, 1.2)
    place(out, tail(hum, longest=0.1), 0.0, db(-6))
    place(out, click(r, 0.002, 800.0), 0.0, db(-14))
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, tail(chime(hz, 1.4, ratios=BAR, amps=(1.0, 0.15, 0.04, 0.0), taus=(0.6, 0.2, 0.07, 0.03))), 0.35 + 0.12 * i,
              db(-8 - 0.8 * i))
    return reverb(fade(softclip(norm(out), 1.1), 0.003, 0.4), r, wet=0.42, t60=2.8, damp_hz=5500.0, hp_hz=40.0)


def stair_open(seed=2131, length=4.0):
    r = rng(seed)
    out = silence(length)
    t = timeline(3.2)
    rumble = shaped_noise(3.2, lambda tt, f: band(f, 140.0 - 80.0 * tt / 3.2, 1.3), r)
    place(out, tail(norm(rumble) * ad(t, 0.2, 1.2, hold=1.0), longest=0.1), 0.0, db(-3))
    place(out, thump(1.2, 70.0, 38.0, 0.2, 0.6, drive=1.5), 0.05, db(-6))
    sung = choir_stab(r, 3.2, (D3 * 2, FS4, A4, D5), singers=4, detune_cents=8.0, attack=0.7, hold=1.0, tau=0.9,
                      breath=0.06, vib_cents=7.0)
    place(out, sung, 0.4, db(-4))
    for k in range(5):
        place(out, crackle(r, n_events=10, spread=0.25, hp_hz=2000.0), 0.6 + 0.4 * k, db(-18))
    return reverb(fade(softclip(norm(out), 1.2), 0.02, 0.5), r, wet=0.45, t60=3.0, damp_hz=5000.0, hp_hz=35.0)


def throne_hum(seed=2141, length=3.2):
    r = rng(seed)
    t = timeline(length)
    env = np.sin(np.pi * np.clip(t / length, 0.0, 1.0)) ** 1.2
    beat = 0.75 + 0.25 * np.sin(2 * np.pi * 2.2 * t)
    x = (np.sin(2 * np.pi * D3 * t) + 0.5 * np.sin(2 * np.pi * (D3 * 2 + 1.1) * t) + 0.25 * np.sin(2 * np.pi * A3 * 2 * t)) * env * beat
    glass = np.sin(2 * np.pi * A5 * t) * env * (0.5 + 0.5 * wobble(r, len(t), 3.0))
    flutter = shaped_noise(length, lambda tt, f: band(f, 900.0, 0.6), r) * env * (0.5 + 0.5 * np.abs(wobble(r, len(t), 9.0)))
    out = norm(x) + db(-16) * norm(glass) + db(-20) * norm(flutter)
    return reverb(fade(softclip(norm(out), 1.2), 0.05, 0.4), r, wet=0.35, t60=2.2, damp_hz=4000.0, hp_hz=40.0)


def fall_rescue(seed=2151, length=1.8):
    r = rng(seed)
    out = silence(length)
    n = n_of(0.8)
    v = np.clip(np.linspace(0.0, 1.2, n), 0.0, 1.0) ** 1.5 * np.clip(np.linspace(1.6, 0.0, n), 0.0, 1.0)
    place(out, tail(whoosh(r, v, 250.0, 2200.0, width=0.9, whistle=0.5, air=0.6, body=0.4)), 0.0, db(-2))
    place(out, thump(0.4, 110.0, 60.0, 0.04, 0.12, drive=1.4), 0.62, db(-5))
    for i, hz in enumerate((A6, FS6, D6, A5)):
        place(out, tail(chime(hz, 0.9, ratios=BAR, amps=(1.0, 0.1, 0.02, 0.0), taus=(0.35, 0.12, 0.05, 0.02))), 0.66 + 0.07 * i,
              db(-12 - i))
    place(out, burst(r, 0.3, 0.01, 0.1, lo=3000.0, hi=9000.0), 0.64, db(-18))
    return reverb(fade(softclip(norm(out), 1.2), 0.01, 0.3), r, wet=0.3, t60=1.6, damp_hz=6000.0, hp_hz=80.0)


EVENTS = [
    Event("sanctum/gate_open", "Sanctum Gate opens", [gate_open], length=2.6, level=0.0, fade_out=0.4),
    Event("sanctum/gate_refuse", "Sanctum Gate stays shut", [gate_refuse], length=1.1, level=-3.0, fade_out=0.2),
    Event("sanctum/lock_lit", "Eclipse Lock lights", [lock_lit], length=3.0, level=1.0, fade_out=0.4),
    Event("sanctum/stair_open", "Throne Seal dissolves", [stair_open], length=4.0, level=3.0, fade_out=0.5),
    Event("sanctum/throne_hum", "Throne hums", [throne_hum], length=3.2, level=-4.0, fade_out=0.4),
    Event("sanctum/fall_rescue", "The Breach throws someone back", [fall_rescue], length=1.8, level=0.0, fade_out=0.3),
]
