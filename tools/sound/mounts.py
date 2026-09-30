"""The celestial mounts' sounds (G6b, GDD 8.1).

    mount/stag_hoof    the Lumen Stag's hooves on crystal: a hoof's knock and a small glass ting (three variants)
    mount/stag_call    the Stag's call: a bugle that climbs and falls through an open "ah", breath, a shimmer of the antlers
    mount/stag_hurt    a short bleat and a crystal tick
    mount/manta_note   one note of the Drift Manta's song, recorded on D4 (the game pitches it to the others of the
                       phrase, D4 to D5): a sung "oo" that scoops up into the note like whale song, a glass overtone
    mount/manta_hurt   a low groan, falling
    mount/manta_sour   a missed answer: the song souring, D against E flat sagging, a soft thud
    mount/chime        the Resonance Chime, recorded on D5 (pitched to the note it answers, an octave over the manta's)
    mount/phase_blink  Phase Blink: air torn open and a reversed glitter snapping shut on a bright ting
    mount/tamed        a mount's trust given: a D major arpeggio of glass over a soft sung D

Every tuned part in D (the mod's key). Levels (offsets from combat/hit's -15 LUFS): call -1, blink -2, the rest -3 to
-4, hooves -7.

Run:  python tools/sound/build.py mount
"""
from __future__ import annotations

from functools import partial

import numpy as np

from dsp import TWO_PI, ad, db, fade, hp_shape, n_of, norm, phase_of, place, rng, shaped_noise, silence, softclip, timeline
from event import Event
from layers import BAR, GLASS, chime, click, glass_grain, knock, reverb, sung, tail, thump, whoosh, AH_BRIGHT

D4, E4, FS4, A4, B4, D5, FS5, A5, D6, A6 = 293.66, 329.63, 369.99, 440.0, 493.88, 587.33, 739.99, 880.0, 1174.66, 1760.0
EF4 = 311.13
OO = ((310.0, 70.0, 0.0), (820.0, 100.0, -9.0), (2300.0, 160.0, -24.0), (3000.0, 220.0, -30.0))


def ting(hz, seconds=0.6, level=0.0):
    return tail(chime(hz, seconds, ratios=BAR, amps=(1.0, 0.16, 0.05, 0.0), taus=(0.22, 0.08, 0.04, 0.02))) * db(level)


def stag_hoof(seed=7101, note=D6):
    r = rng(seed)
    out = silence(0.45)
    place(out, click(r, 0.002, 1800.0), 0.0, db(-6))
    place(out, tail(knock(r, 170.0 + r.uniform(-15, 15), tau=0.03, ratios=(1.0, 1.9, 3.1), amps=(1.0, 0.5, 0.2))), 0.0, db(-1))
    place(out, tail(thump(0.12, 120.0, 70.0, 0.03, 0.04)), 0.0, db(-8))
    place(out, ting(note, 0.4), 0.004, db(-9))
    return reverb(fade(norm(out), 0.001, 0.08), r, wet=0.1, t60=0.5, damp_hz=7000.0, hp_hz=150.0)


def glide_pitch(t, points):
    """A pitch track through (time, Hz) points, eased between them."""
    ts = [p[0] for p in points]
    hz = [p[1] for p in points]
    return np.exp(np.interp(t, ts, np.log(hz)))


def stag_call(seed=7111, top=D6):
    r = rng(seed)
    length = 1.5
    t = timeline(length)
    track = glide_pitch(t, [(0.0, A4), (0.18, D5), (0.45, top), (0.7, top * 0.97), (1.05, A5), (1.4, D5)])
    vib = 1.0 + 0.012 * np.sin(TWO_PI * 5.5 * t) * np.clip((t - 0.3) / 0.3, 0, 1)
    voice = sung(track * vib, AH_BRIGHT, r, rolloff=1.3, f_max=8000.0)
    env = ad(t, 0.06, 0.5, hold=0.55)
    out = norm(voice * env)
    air = shaped_noise(length, lambda tt, f: hp_shape(f, 1200.0, 2) / (1 + (f / 5000.0) ** 2), r)
    out = out + 0.18 * norm(air) * env
    shimmer = silence(length)
    for i, hz in enumerate((D6, FS5 * 2, A6)):
        place(shimmer, ting(hz, 0.9), 0.35 + 0.12 * i, db(-4 - 2 * i))
    out = norm(out) + 0.35 * norm(shimmer)
    return reverb(fade(softclip(norm(out), 1.1), 0.01, 0.2), r, wet=0.3, t60=1.6, damp_hz=7000.0, hp_hz=180.0)


def stag_hurt(seed=7121):
    r = rng(seed)
    length = 0.6
    t = timeline(length)
    track = glide_pitch(t, [(0.0, 700.0), (0.25, 520.0), (0.5, 430.0)])
    out = norm(sung(track, AH_BRIGHT, r, rolloff=1.1) * ad(t, 0.015, 0.14, hold=0.08))
    crack = silence(length)
    place(crack, tail(glass_grain(r, 3100.0, tau=0.03)), 0.01, 1.0)
    out = out + 0.4 * norm(crack)
    return reverb(fade(norm(out), 0.002, 0.1), r, wet=0.15, t60=0.7)


def manta_note(seed=7131):
    """D4 sung like a whale: scoops up a third into the note, holds with a slow vibrato, fades; a glass D5 above."""
    r = rng(seed)
    length = 1.2
    t = timeline(length)
    scoop = 2.0 ** ((-300.0 * np.exp(-t / 0.08)) / 1200.0)
    vib = 1.0 + 0.008 * np.sin(TWO_PI * 4.2 * t) * np.clip((t - 0.15) / 0.3, 0, 1)
    voice = sung(D4 * scoop * vib, OO, r, rolloff=1.6, f_max=5000.0)
    env = ad(t, 0.05, 0.35, hold=0.35)
    out = norm(voice * env)
    glass = tail(chime(D5, length, ratios=GLASS, amps=(1.0, 0.2, 0.05, 0.0), taus=(0.45, 0.2, 0.08, 0.03), attack=0.02))
    out = out + 0.22 * norm(glass)
    return reverb(fade(norm(out), 0.004, 0.2), r, wet=0.35, t60=2.0, damp_hz=5000.0, hp_hz=120.0)


def manta_hurt(seed=7141):
    r = rng(seed)
    length = 0.8
    t = timeline(length)
    track = glide_pitch(t, [(0.0, 260.0), (0.4, 180.0), (0.8, 150.0)])
    out = norm(sung(track, OO, r, rolloff=1.4, f_max=4000.0) * ad(t, 0.02, 0.2, hold=0.1))
    return reverb(fade(out, 0.003, 0.15), r, wet=0.25, t60=1.2, damp_hz=4000.0)


def manta_sour(seed=7151):
    r = rng(seed)
    length = 1.1
    t = timeline(length)
    sag = 2.0 ** ((-60.0 * np.clip(t / 0.8, 0, 1)) / 1200.0)
    a = sung(D4 * sag, OO, r, rolloff=1.5, f_max=4500.0)
    b = sung(EF4 * sag * 1.004, OO, r, rolloff=1.5, f_max=4500.0)
    env = ad(t, 0.02, 0.3, hold=0.25)
    out = norm((a + b) * env)
    place(out, tail(thump(0.3, 110.0, 55.0, 0.06, 0.1)), 0.0, db(-6))
    return reverb(fade(softclip(norm(out), 1.1), 0.003, 0.2), r, wet=0.3, t60=1.4, damp_hz=4500.0)


def resonance_chime(seed=7161):
    r = rng(seed)
    out = silence(1.3)
    place(out, click(r, 0.0015, 2500.0), 0.0, db(-12))
    place(out, tail(chime(D5, 1.3, ratios=BAR, amps=(1.0, 0.3, 0.12, 0.04), taus=(0.55, 0.2, 0.08, 0.03), beats=(1.5, 2.5, 0, 0))), 0.0, 1.0)
    place(out, tail(chime(D6, 0.9, ratios=GLASS, amps=(1.0, 0.2, 0.05, 0.0), taus=(0.3, 0.1, 0.04, 0.02))), 0.002, db(-9))
    return reverb(fade(norm(out), 0.001, 0.2), r, wet=0.25, t60=1.4, damp_hz=8000.0, hp_hz=200.0)


def phase_blink(seed=7171):
    r = rng(seed)
    length = 0.9
    out = silence(length)
    n = n_of(0.3)
    v = np.clip(np.linspace(0.0, 1.8, n), 0.0, 1.0) * np.clip(np.linspace(1.3, 0.0, n), 0.0, 1.0)
    place(out, tail(whoosh(r, v, 500.0, 5200.0, width=0.8, whistle=0.6, air=0.8, body=0.2)), 0.0, db(-2))
    glitter = silence(0.3)
    for i in range(10):
        place(glitter, tail(glass_grain(r, 2600.0 + 380.0 * i, tau=0.02)), 0.025 * i, db(-4 + 0.3 * i))
    place(out, glitter[::-1].copy(), 0.0, db(-6))                       # reversed: glitter rushing in
    place(out, ting(A6, 0.5), 0.28, db(-4))
    place(out, ting(D6, 0.6), 0.29, db(-8))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.2), r, wet=0.25, t60=1.1, damp_hz=8000.0, hp_hz=250.0)


def tamed(seed=7181):
    r = rng(seed)
    length = 1.8
    t = timeline(length)
    out = silence(length)
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, ting(hz, 1.2), 0.1 * i, db(-2 - i))
    pad = sung(np.full_like(t, D4), AH_BRIGHT, r, rolloff=1.8, f_max=4000.0) * ad(t, 0.25, 0.6, hold=0.4)
    out = norm(out) + 0.25 * norm(pad)
    return reverb(fade(norm(out), 0.005, 0.3), r, wet=0.3, t60=1.8, damp_hz=7000.0)


EVENTS = [
    Event("mount/stag_hoof", "Crystal hooves clatter",
          [partial(stag_hoof, 7101, D6), partial(stag_hoof, 7102, FS5 * 2), partial(stag_hoof, 7103, A5 * 2)],
          length=0.45, level=-7.0, fade_out=0.08),
    Event("mount/stag_call", "Lumen Stag calls", [partial(stag_call, 7111, D6), partial(stag_call, 7112, A5 * 1.5)],
          length=1.8, level=-1.0, fade_out=0.3),
    Event("mount/stag_hurt", "Lumen Stag hurts", [stag_hurt], length=0.7, level=-3.0, fade_out=0.1),
    Event("mount/manta_note", "Drift Manta sings", [manta_note], length=1.6, level=-3.0, fade_out=0.3),
    Event("mount/manta_hurt", "Drift Manta groans", [manta_hurt], length=1.0, level=-4.0, fade_out=0.2),
    Event("mount/manta_sour", "Manta's song sours", [manta_sour], length=1.4, level=-3.0, fade_out=0.3),
    Event("mount/chime", "Resonance Chime rings", [resonance_chime], length=1.6, level=-3.0, fade_out=0.3),
    Event("mount/phase_blink", "Phase Blink", [phase_blink], length=1.1, level=-2.0, fade_out=0.2),
    Event("mount/tamed", "A mount trusts you", [tamed], length=2.2, level=-4.0, fade_out=0.4),
]
