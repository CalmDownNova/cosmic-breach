"""Driftweave and Choir Regalia sounds (GDD 5.1), tuned to D major pentatonic like every cue in the set.

    driftweave/afterimage        a perfect dodge leaves an Afterimage: star dust sizzling into shape over a soft swell,
                                 two glass notes (A6, then E7) ringing out of it
    driftweave/afterimage_strike the Afterimage repeats a blow: a thin airy slash, a glass "ting" (F#6) and its echo,
                                 lighter than a real hit (it lands at 40%)
    driftweave/drift_start       Drift begins: air rushing upward, a soft low release (gravity letting go), a rising
                                 D, F#, A glass arpeggio and sparkle
    driftweave/drift_end         Drift ends: the air sinking back down into a soft settling thump
    regalia/echo                 a cast echoes: a reversed swell into a distant sung "ah" (D, F#, A) with a bell and a
                                 long bright tail, ghostly
    regalia/hymn                 the Hymn of Alignment: a sustained sung chord (D3, A3, D4, F#4) swelling in over a
                                 deep bell, with a high shimmer and a long hall tail

Run:  python tools/sound/build.py driftweave regalia
"""
from __future__ import annotations

import numpy as np

from dsp import ad, band, bump, db, fade, hp, hp_shape, lp_shape, norm, note, place, rng, shaped_noise, silence, softclip, \
    timeline
from event import Event
from layers import AH_DISTANT, BAR, GLASS, burst, chime, choir_stab, glass_grain, granular_hiss, modes, reverb, tail, thump, \
    whoosh


def dust(r, seconds, count, lo=2600.0, hi=7800.0, tau=0.05):
    """Star dust: many tiny glass grains scattered over `seconds`, thickest in the middle."""
    out = silence(seconds)
    for i in range(count):
        when = seconds * float(np.clip(r.beta(2.0, 2.4), 0.0, 0.98))
        grain = glass_grain(r, r.uniform(lo, hi), tau=tau * r.uniform(0.6, 1.4))
        place(out, grain, when, db(r.uniform(-14.0, -6.0)))
    return out


def afterimage(seed=801, length=1.0):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.7)
    swell = shaped_noise(0.7, lambda tt, f: band(f, 1800.0 * (1.6 ** (tt / 0.7)), 1.4) * lp_shape(f, 9000.0, 2), r)
    place(out, tail(norm(swell * bump(t, 0.0, 0.7, 0.45, 5.0)), longest=0.05), 0.0, db(-8))
    place(out, norm(dust(r, 0.6, 26)), 0.02, db(-6))
    place(out, granular_hiss(r, 0.5, lo=5000.0, rate=45.0, attack=0.05, tau=0.2), 0.0, db(-14))
    place(out, norm(chime(note("A6"), 0.8, taus=(0.35, 0.18, 0.08, 0.04))), 0.12, db(-7))
    place(out, norm(chime(note("E7"), 0.7, taus=(0.3, 0.15, 0.06, 0.03))), 0.2, db(-11))
    out = softclip(norm(out), 1.1)
    return reverb(out, r, wet=0.3, t60=1.1, damp_hz=8000.0, hp_hz=400.0)


def afterimage_strike(seed, length=0.5):
    r = rng(seed)
    out = silence(length)
    v = bump(timeline(0.14), 0.0, 0.14, 0.4, 8.0)
    place(out, norm(whoosh(r, v, 2200.0, 8000.0, width=0.5, whistle=0.6, air=0.9, amp_pow=1.6)), 0.0, db(-4))
    ting = norm(modes(0.35, [note("F#6") * q for q in BAR], [1.0, 0.45, 0.2, 0.1], [0.09, 0.05, 0.025, 0.015],
                      beats=[3.0, 5.0, 0.0, 0.0], attack=0.0005))
    place(out, ting, 0.04, db(-5))
    place(out, ting, 0.13, db(-14))                       # the echo: the same ting, fainter, a beat behind
    place(out, burst(r, 0.03, 0.0005, 0.008, lo=5000.0), 0.035, db(-10))
    out = softclip(norm(out), 1.2)
    return reverb(out, r, wet=0.22, t60=0.6, damp_hz=9000.0, hp_hz=500.0)


def drift_start(seed=811, length=1.5):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.9)
    rush = shaped_noise(0.9, lambda tt, f: band(f, 500.0 * (6.0 ** (tt / 0.9)), 1.2) * lp_shape(f, 10000.0, 2), r)
    place(out, tail(norm(rush * bump(t, 0.0, 0.9, 0.55, 4.0)), longest=0.1), 0.0, db(-5))
    place(out, thump(0.7, 120.0, 60.0, 0.12, 0.3, attack=0.02, drive=0.4), 0.0, db(-9))
    for i, name in enumerate(("D6", "F#6", "A6")):
        place(out, norm(chime(note(name), 0.9, taus=(0.32, 0.16, 0.07, 0.03))), 0.15 + 0.11 * i, db(-8 - 1.5 * i))
    place(out, norm(dust(r, 0.8, 30, lo=3000.0, hi=9000.0)), 0.3, db(-9))
    out = softclip(norm(out), 1.1)
    return reverb(out, r, wet=0.28, t60=1.2, damp_hz=8000.0, hp_hz=250.0)


def drift_end(seed=821, length=1.0):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.6)
    sink = shaped_noise(0.6, lambda tt, f: band(f, 3000.0 * (0.18 ** (tt / 0.6)), 1.3) * lp_shape(f, 8000.0, 2), r)
    place(out, tail(norm(sink * bump(t, 0.0, 0.6, 0.35, 4.0)), longest=0.1), 0.0, db(-5))
    place(out, thump(0.5, 95.0, 55.0, 0.05, 0.16, attack=0.004, drive=0.8), 0.5, db(-6))
    place(out, norm(chime(note("A5"), 0.6, taus=(0.25, 0.12, 0.05, 0.02))), 0.02, db(-12))
    place(out, norm(dust(r, 0.4, 12, lo=2000.0, hi=6000.0)), 0.0, db(-12))
    out = softclip(norm(out), 1.1)
    return reverb(out, r, wet=0.2, t60=0.8, damp_hz=7000.0, hp_hz=200.0)


def echo(seed=831, length=1.8):
    r = rng(seed)
    out = silence(length)
    pre = 0.35
    t = timeline(pre)
    swell = shaped_noise(pre, lambda tt, f: band(f, 1200.0 * (2.5 ** (tt / pre)), 1.5), r)
    place(out, tail(norm(swell * (t / pre) ** 3.0), longest=0.004), 0.0, db(-9))
    voices = choir_stab(r, 1.2, [note("D5"), note("F#5"), note("A5")], formants=AH_DISTANT, singers=3, detune_cents=12.0,
                        attack=0.03, hold=0.12, tau=0.35, breath=0.08)
    place(out, norm(voices), pre, db(-3))
    bell = modes(1.2, [note("D6") * q for q in (1.0, 2.0, 2.76, 5.4)], [1.0, 0.3, 0.2, 0.06], [0.6, 0.35, 0.2, 0.08],
                 beats=[0.9, 1.4, 0.0, 0.0], attack=0.002)
    place(out, norm(bell), pre, db(-9))
    place(out, granular_hiss(r, 0.8, lo=6000.0, rate=30.0, attack=0.02, tau=0.35), pre, db(-18))
    out = softclip(norm(out), 1.05)
    return reverb(out, r, wet=0.4, t60=2.0, damp_hz=7000.0, hp_hz=200.0, predelay=0.03)


def hymn(seed=841, length=3.2):
    r = rng(seed)
    out = silence(length)
    chord = [note("D3"), note("A3"), note("D4"), note("F#4")]
    voices = choir_stab(r, 2.8, chord, formants=AH_DISTANT, singers=4, detune_cents=10.0, scoop_cents=20.0,
                        attack=0.35, hold=1.1, tau=0.7, breath=0.06)
    place(out, norm(voices), 0.0, db(-2))
    for i, name in enumerate(("D5", "A5")):
        f = note(name)
        bell = modes(2.4, [f * q for q in (1.0, 2.0, 2.76, 5.4)], [1.0, 0.35, 0.25, 0.08], [1.2, 0.7, 0.4, 0.15],
                     beats=[0.7, 1.1, 0.0, 0.0], attack=0.003)
        place(out, norm(bell), 0.02 + 0.6 * i, db(-8 - 3 * i))
    place(out, thump(1.2, 70.0, 45.0, 0.2, 0.6, attack=0.05, drive=0.2), 0.0, db(-12))
    place(out, norm(dust(r, 2.2, 36, lo=4000.0, hi=9500.0, tau=0.08)), 0.3, db(-16))
    out = fade(softclip(norm(out), 1.05), 0.02, 0.6)
    return reverb(out, r, wet=0.45, t60=2.6, damp_hz=6500.0, hp_hz=150.0, predelay=0.03)


EVENTS = [
    Event("driftweave/afterimage", "Afterimage appears", [afterimage], length=1.2, level=-3.0, fade_out=0.25),
    Event("driftweave/afterimage_strike", "Afterimage strikes", [lambda: afterimage_strike(851), lambda: afterimage_strike(852)],
          length=0.6, level=-4.0, fade_out=0.15),
    Event("driftweave/drift_start", "Drift begins", [drift_start], length=1.8, level=-2.0, fade_out=0.3),
    Event("driftweave/drift_end", "Drift ends", [drift_end], length=1.2, level=-5.0, fade_out=0.25),
    Event("regalia/echo", "Ability echoes", [echo], length=2.4, level=-2.0, fade_out=0.5),
    Event("regalia/hymn", "Hymn rings out", [hymn], length=4.0, level=-1.0, fade_out=0.8),
]
