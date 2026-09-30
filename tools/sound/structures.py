"""The first dungeons' sounds (W5, GDD 6.1, 6.2, 6.4).

    structures/aperture_open  the Sun Aperture opening: stone grinding aside, air drawn in, a shimmer rising into a
                              warm D major chord as the sunbeam falls
    structures/mirror_turn    a mirror turned on its pedestal: a stone click and a short glassy ring (two takes)
    structures/receptor_hum   a receptor's hum: a soft D and A crystal tone with slow beats, a seamless 4 s loop;
                              the game raises its pitch as the light passes closer
    structures/receptor_lit   a receptor lit with its colour: a bright D major chord of crystal
    structures/warden_eye     the Warden Eye waking: a low growl swelling under a scraped, unsettled glass tone
    structures/vault_open     a vault unsealing: a heavy stone slide, a thump, a rising chime flourish
    structures/umbral_push    an Umbral block knocked a tile: a dull stone grind and thud
    structures/thread         a tripwire's thread glinting into sight: a faint, very high glass shimmer
    structures/bolt           a kinetic bolt firing along the thread: a snapping electric crack and fizz
    structures/updraft        stepping into a gravity lift: a rush of air sweeping upward

Tuned parts use D major pentatonic (D E F# A B), like the rest of the set. The Eye alone leans on a flat
second against its drone, so it reads as a warning.

Run:  python tools/sound/build.py structures
"""
from __future__ import annotations

import numpy as np

from dsp import (ad, band, db, fade, hp_shape, lp_shape, n_of, norm, phase_of, place, rng, shaped_noise, silence,
                 smoothstep, softclip, timeline, wobble)
from aetheria import loop_noise
from event import Event
from layers import burst, chime, click, crackle, knock, reverb, tail, thump, whoosh

D4, A4, D5, E5, FS5, A5, B5, D6, E6, FS6, A6, B6, D7, A7 = (293.66, 440.0, 587.33, 659.26, 739.99, 880.0, 987.77, 1174.66,
                                                            1318.51, 1479.98, 1760.0, 1975.53, 2349.32, 3520.0)


def aperture_open(seed=901, length=2.4):
    r = rng(seed)
    out = silence(length)
    t = timeline(1.1)
    grind = shaped_noise(1.1, lambda tt, f: band(f, 260.0 + 90.0 * np.sin(tt * 9.0), 1.2), r)
    place(out, tail(norm(grind) * ad(t, 0.05, 0.45), longest=0.05), 0.0, db(-8))
    place(out, knock(r, 180.0, tau=0.05), 0.0, db(-6))
    st = timeline(0.9)
    draw = shaped_noise(0.9, lambda tt, f: band(f, 700.0 * (6.0 ** np.clip(tt / 0.9, 0, 1)), 1.0), r)
    place(out, tail(norm(draw) * (st / 0.9) ** 1.6, longest=0.02), 0.25, db(-12))
    for i, hz in enumerate((A5, D6, E6, FS6, A6)):
        place(out, tail(chime(hz, 0.9, taus=(0.4, 0.2, 0.1, 0.04))), 0.55 + 0.08 * i, db(-13 - i))
    for hz, g in ((D5, -1.0), (FS5, -3.0), (A5, -4.0), (D6, -6.0)):
        c = chime(hz, 1.6, taus=(0.9, 0.45, 0.2, 0.08), beats=(1.0, 2.0, 0.0, 0.0))
        place(out, tail(c, longest=0.3), 0.95, db(-7 + g))
    return reverb(softclip(norm(out), 1.2), r, wet=0.32, t60=1.8, damp_hz=6500.0, hp_hz=140.0)


def _mirror_turn(seed, ring_hz, length=0.4):
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.0015, 1500.0), 0.0, db(-2))
    place(out, knock(r, 520.0, tau=0.018), 0.0, db(-6))
    place(out, tail(chime(ring_hz, 0.35, taus=(0.12, 0.06, 0.03, 0.015))), 0.004, db(-9))
    return reverb(norm(out), r, wet=0.12, t60=0.5, damp_hz=8000.0, hp_hz=300.0)


def mirror_turn_a():
    return _mirror_turn(911, FS6)


def mirror_turn_b():
    return _mirror_turn(912, A6)


def receptor_hum(length=4.0):
    """Every partial is a multiple of 0.25 Hz, so 4 s is a whole number of cycles of each: a seamless loop."""
    t = timeline(length)
    hum = np.zeros_like(t)
    for hz, g in ((293.5, 1.0), (293.75, 0.7), (440.0, 0.55), (440.5, 0.35), (587.0, 0.22), (880.0, 0.08)):
        hum += g * np.sin(2 * np.pi * hz * t)
    breath = 0.8 + 0.2 * np.sin(2 * np.pi * 0.25 * t)
    # a faint crystalline air over it, itself a seamless loop, so the wrap hides in texture
    shimmer = loop_noise(length, lambda tt, f: band(f, 5200.0, 0.7) + 0.4 * band(f, 2600.0, 0.5), rng(991), xfade=1.0)
    return norm(norm(hum * breath) + db(-15) * norm(shimmer))


def receptor_lit(seed=921, length=1.8):
    r = rng(seed)
    out = silence(length)
    place(out, burst(r, 0.05, 0.0005, 0.012, lo=4000.0, hi=12000.0), 0.0, db(-14))
    for i, (hz, g) in enumerate(((D6, 0.0), (FS6, -2.0), (A6, -3.0), (D7, -6.0))):
        c = chime(hz, 1.6, taus=(0.8, 0.4, 0.18, 0.07), beats=(1.5, 2.5, 0.0, 0.0))
        place(out, tail(c, longest=0.3), 0.015 * i, db(-4 + g))
    return reverb(softclip(norm(out), 1.1), r, wet=0.3, t60=1.6, damp_hz=7000.0, hp_hz=200.0)


def warden_eye(seed=931, length=1.9):
    r = rng(seed)
    t = timeline(length)
    swell = smoothstep(np.clip(t / 0.5, 0, 1)) * (1.0 - smoothstep(np.clip((t - 1.3) / 0.6, 0, 1)))
    f = 73.4 * (1.0 + 0.06 * smoothstep(np.clip(t / 1.2, 0, 1)))
    drone = np.sin(phase_of(f)) + 0.5 * np.sin(phase_of(f * 2.0)) + 0.25 * np.sin(phase_of(f * 3.0))
    growl = shaped_noise(length, lambda tt, fq: band(fq, 140.0, 0.8), r) * (0.6 + 0.4 * wobble(r, len(t), 11.0))
    # a scraped glass tone a flat second over the drone's octave: the one unsettled sound in the set
    scrape = shaped_noise(length, lambda tt, fq: band(fq, 1245.0, 0.05) + 0.6 * band(fq, 1318.5, 0.05), r)
    out = norm(drone) * swell + db(-6) * norm(growl) * swell + db(-12) * norm(scrape) * swell
    place(out, thump(0.5, 90.0, 40.0, 0.06, 0.2, drive=1.5), 0.0, db(-4))
    return reverb(fade(softclip(norm(out), 1.5), 0.01, 0.2), r, wet=0.25, t60=1.4, damp_hz=4000.0, hp_hz=40.0)


def vault_open(seed=941, length=2.2):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.9)
    slide = shaped_noise(0.9, lambda tt, f: band(f, 320.0, 1.3) + 0.3 * band(f, 1200.0, 0.8), r)
    place(out, tail(norm(slide) * ad(t, 0.08, 0.35, hold=0.3), longest=0.05), 0.0, db(-6))
    place(out, thump(0.5, 120.0, 45.0, 0.05, 0.16, drive=1.3), 0.72, db(-3))
    for i, hz in enumerate((D5, FS5, A5, D6, FS6, A6)):
        place(out, tail(chime(hz, 1.0, taus=(0.45, 0.22, 0.1, 0.04))), 0.85 + 0.07 * i, db(-10 - 0.6 * i))
    return reverb(softclip(norm(out), 1.2), r, wet=0.28, t60=1.5, damp_hz=6500.0, hp_hz=90.0)


def umbral_push(seed=951, length=0.7):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.45)
    grind = shaped_noise(0.45, lambda tt, f: band(f, 210.0, 1.4), r)
    place(out, tail(norm(grind) * ad(t, 0.02, 0.18)), 0.0, db(-5))
    place(out, thump(0.4, 95.0, 40.0, 0.04, 0.12, drive=1.6), 0.12, db(-2))
    place(out, crackle(r, 10, 0.12, 900.0, 4500.0, grain=0.001, falloff=0.7), 0.12, db(-12))
    return reverb(softclip(norm(out), 1.3), r, wet=0.15, t60=0.8, damp_hz=4000.0, hp_hz=60.0)


def thread(seed=961, length=1.1):
    r = rng(seed)
    out = silence(length)
    for i, hz in enumerate((D7, A7, 4698.63)):
        place(out, tail(chime(hz, 0.8, taus=(0.35, 0.15, 0.06, 0.02), beats=(3.0, 0.0, 0.0, 0.0))), 0.03 * i, db(-3 * i))
    t = timeline(length)
    air = shaped_noise(length, lambda tt, f: band(f, 9000.0, 0.5), r) * ad(t, 0.05, 0.3)
    out = norm(out) + db(-18) * norm(air)
    return reverb(fade(norm(out), 0.02, 0.2), r, wet=0.35, t60=1.2, damp_hz=9000.0, hp_hz=1500.0)


def bolt(seed=971, length=0.55):
    r = rng(seed)
    out = silence(length)
    t0 = 0.004
    place(out, click(r, 0.002, 800.0), t0, db(0))
    place(out, crackle(r, 30, 0.08, 1500.0, 9000.0, grain=0.0007, falloff=0.5), t0, db(-3))
    t = timeline(0.3)
    zap = np.sin(phase_of(2400.0 * (0.25 ** np.clip(t / 0.18, 0, 1)))) * ad(t, 0.001, 0.07)
    place(out, tail(zap), t0, db(-8))
    place(out, burst(r, 0.25, 0.0005, 0.06, lo=2500.0, hi=11000.0), t0 + 0.01, db(-7))
    return reverb(softclip(norm(out), 1.6), r, wet=0.12, t60=0.6, damp_hz=9000.0, hp_hz=300.0)


def updraft(seed=981, length=1.5):
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 1.1, 0.0, 1.0)
    v = 0.15 + 0.85 * np.sin(np.pi * u) ** 0.8
    rush = whoosh(r, v, 300.0, 3800.0, width=1.0, whistle=0.25, air=0.7, air_hz=5000.0, body=0.4, body_hz=140.0)
    out = norm(rush) * (1.0 - smoothstep(np.clip((t - 1.15) / 0.35, 0, 1)))
    return fade(norm(out), 0.04, 0.2)


EVENTS = [
    Event("structures/aperture_open", "Sun Aperture opens", [aperture_open], length=2.4, level=-2.0, fade_out=0.3),
    Event("structures/mirror_turn", "Lens mirror turns", [mirror_turn_a, mirror_turn_b], length=0.4, level=-8.0),
    Event("structures/receptor_hum", "Receptor crystal hums", [receptor_hum], length=4.0, level=-16.0, loop=True, quality=8),
    Event("structures/receptor_lit", "Receptor crystal sings", [receptor_lit], length=1.8, level=-4.0, fade_out=0.3),
    Event("structures/warden_eye", "Warden Eye wakes", [warden_eye], length=1.9, level=-1.0, fade_out=0.2),
    Event("structures/vault_open", "Vault opens", [vault_open], length=2.2, level=-2.0, fade_out=0.3),
    Event("structures/umbral_push", "Umbral block grinds", [umbral_push], length=0.7, level=-5.0),
    Event("structures/thread", "Tripwire thread glints", [thread], length=1.1, level=-14.0, fade_out=0.2),
    Event("structures/bolt", "Kinetic bolt fires", [bolt], length=0.55, level=-3.0),
    Event("structures/updraft", "Updraft rushes", [updraft], length=1.5, level=-6.0, fade_out=0.2),
]
