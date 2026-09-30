"""Astral Forge and Starfall Vanguard sounds (GDD 3.5 and 5.1).

    forge/craft              the anvil struck once: a bright inharmonic ring over a knock, a D major
                             pentatonic chime (A5 over D5) and a short sparkle of sparks
    forge/tier_up            a new ring joins the Forge: a reversed swell into a deep D, A and F# bell chord
                             with a slow shimmer
    vanguard/shockwave       Heavy Landing: a heavy boot-plate thud, crushed stone, a short rumble
    vanguard/meteor_incoming Meteor Call's warning, 1.5 s (the 30 ticks before it lands): a falling whistle and a
                             roar that swells to the moment of impact
    vanguard/meteor_impact   the meteor lands: a boom with a deep sub, rock thrown about, then fire crackling

Run:  python tools/sound/build.py forge vanguard
"""
from __future__ import annotations

import numpy as np

from dsp import SR, TWO_PI, ad, band, db, fade, hp_shape, lp_shape, n_of, norm, note, phase_of, place, rng, \
    shaped_noise, silence, softclip, timeline
from event import Event
from layers import burst, chime, click, crackle, granular_hiss, knock, modes, reverb, tail, thump


def craft(seed, length=1.6):
    """The anvil ring: a struck steel bar (inharmonic modes), the knock of the hammer, a chime and sparks."""
    r = rng(seed)
    out = silence(length)
    f0 = note("D5")
    ring = modes(1.4, [f0 * q for q in (1.0, 2.76, 5.40, 8.93)], [1.0, 0.55, 0.3, 0.14], [0.55, 0.3, 0.14, 0.07],
                 beats=[1.5, 2.2, 0.0, 0.0])
    place(out, norm(ring), 0.002, db(-2))
    place(out, knock(r, 420.0, tau=0.018, ratios=(1.0, 1.9), amps=(1.0, 0.5)), 0.0, db(-4))
    place(out, click(r, 0.0012, 1800.0), 0.0, db(-6))
    place(out, norm(chime(note("A5"), 1.2, taus=(0.5, 0.25, 0.1, 0.05))), 0.03, db(-7))
    place(out, norm(chime(note("D6"), 1.0, taus=(0.4, 0.2, 0.08, 0.04))), 0.07, db(-10))
    place(out, crackle(r, 14, 0.18, 3000.0, 11000.0, grain=0.0006, falloff=0.7), 0.01, db(-13))
    out = softclip(norm(out), 1.2)
    return reverb(out, r, wet=0.22, t60=1.3, damp_hz=7000.0, hp_hz=250.0)


def tier_up(seed=701, length=3.0):
    """A reversed swell (air rushing in) into a deep bell chord that shimmers as it fades."""
    r = rng(seed)
    out = silence(length)
    swell_len = 0.7
    st = timeline(swell_len)
    swell = shaped_noise(swell_len, lambda tt, f: band(f, 700.0 * (5.0 ** (tt / swell_len)), 1.3), r)
    place(out, tail(norm(swell * (st / swell_len) ** 3.0), longest=0.004), 0.0, db(-6))
    t0 = swell_len
    for name, gain, tau in (("D3", 0.0, 1.6), ("A3", -3.0, 1.3), ("F#4", -6.0, 1.0), ("D5", -9.0, 0.8)):
        f = note(name)
        bell = modes(2.2, [f * q for q in (1.0, 2.0, 2.76, 5.4)], [1.0, 0.35, 0.25, 0.08],
                     [tau, tau * 0.6, tau * 0.4, tau * 0.2], beats=[0.8, 1.3, 0.0, 0.0], attack=0.004)
        place(out, norm(bell), t0, db(gain))
    place(out, thump(0.6, 110.0, 55.0, 0.05, 0.25, drive=1.2), t0, db(-5))
    place(out, granular_hiss(r, 1.2, lo=5000.0, rate=35.0, attack=0.02, tau=0.5), t0 + 0.02, db(-18))
    out = softclip(norm(out), 1.1)
    return reverb(out, r, wet=0.3, t60=1.8, damp_hz=6000.0, hp_hz=150.0)


def shockwave(seed, length=1.0):
    """A plated landing: a deep thud, the clank of plate, stone crushed and a short rumble."""
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.6, 95.0, 36.0, 0.04, 0.14, attack=0.002, drive=2.0), 0.0)
    place(out, knock(r, 230.0, tau=0.03, ratios=(1.0, 2.3, 3.1), amps=(1.0, 0.45, 0.25)), 0.0, db(-4))
    place(out, burst(r, 0.3, 0.0006, 0.05, lo=300.0, hi=2500.0), 0.001, db(-5))
    place(out, crackle(r, 12, 0.06, 900.0, 5000.0, grain=0.0012, falloff=0.5), 0.004, db(-5))
    rt = timeline(0.8)
    rumble = shaped_noise(0.8, lambda tt, f: lp_shape(f, 160.0, 3) * hp_shape(f, 28.0, 2), r)
    place(out, tail(norm(rumble * ad(rt, 0.01, 0.22)), longest=0.1), 0.0, db(-6))
    out = softclip(norm(out), 1.6)
    return reverb(out, r, wet=0.18, t60=0.9, damp_hz=5000.0, hp_hz=180.0)


def meteor_incoming(seed=711, length=1.5):
    """The warning: a whistle falling from high to low as the meteor comes in, a roar swelling under it,
    everything climbing to the moment it lands (the end of the file)."""
    r = rng(seed)
    t = timeline(length)
    u = t / length
    f = 2600.0 * (0.28 ** u)                                           # the whistle falls about two octaves
    whistle = np.sin(phase_of(f)) + 0.3 * np.sin(phase_of(2.01 * f))
    whistle *= (0.25 + 0.75 * u ** 1.5)
    roar = shaped_noise(length, lambda tt, fq: band(fq, 900.0 * (0.35 ** (tt / length)), 1.8)
                        * lp_shape(fq, 5000.0, 2), r)
    roar = norm(roar) * (0.05 + 0.95 * u ** 2.5)
    crackles = silence(length)
    for i in range(10):
        when = length * (0.4 + 0.58 * r.uniform())
        place(crackles, crackle(r, 3, 0.02, 2000.0, 8000.0, grain=0.0006, falloff=0.5), when, 0.6)
    mix = db(-6) * norm(whistle) + norm(roar) + db(-14) * crackles
    # it peaks just before the impact, then drops away quickly: the impact sound takes over from there
    mix = fade(mix, 0.25, 0.12)
    return reverb(norm(mix), r, wet=0.2, t60=1.0, damp_hz=5000.0, hp_hz=120.0)


def meteor_impact(seed=721, length=2.4):
    """A boom with a sub under it, rock thrown about, then fire crackling in the crater."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.004
    place(out, thump(1.2, 75.0, 28.0, 0.06, 0.35, attack=0.002, drive=2.2), t0)
    sub = np.sin(phase_of(np.full(n_of(1.4), 32.0))) * ad(timeline(1.4), 0.008, 0.45)
    place(out, tail(sub), t0, db(-2))
    place(out, burst(r, 0.6, 0.0008, 0.12, hi=1500.0), t0, db(-3))
    place(out, crackle(r, 30, 0.25, 700.0, 5500.0, grain=0.0015, falloff=0.6), t0 + 0.005, db(-4))
    place(out, knock(r, 140.0, tau=0.05, ratios=(1.0, 1.7, 2.6), amps=(1.0, 0.5, 0.3)), t0, db(-5))
    fire_len = 1.7
    ft = timeline(fire_len)
    fire = shaped_noise(fire_len, lambda tt, fq: band(fq, 1600.0, 2.2), r) * ad(ft, 0.08, 0.45)
    place(out, tail(norm(fire), longest=0.3), 0.25, db(-14))
    place(out, crackle(r, 20, 1.0, 1500.0, 7000.0, grain=0.0008, falloff=0.8), 0.3, db(-13))
    place(out, click(r, 0.002, 500.0), t0, db(-6))
    out = softclip(norm(out), 1.7)
    return reverb(out, r, wet=0.25, t60=1.6, damp_hz=4500.0, hp_hz=120.0)


EVENTS = [
    Event("forge/craft", "Astral Forge rings", [lambda: craft(731), lambda: craft(732)], length=1.6, level=0.0,
          fade_out=0.3),
    Event("forge/tier_up", "Astral Forge grows", [tier_up], length=3.0, level=1.0, fade_out=0.5),
    Event("vanguard/shockwave", "Heavy landing", [lambda: shockwave(741), lambda: shockwave(742)], length=1.0, level=2.0,
          fade_out=0.2),
    Event("vanguard/meteor_incoming", "Meteor falls", [meteor_incoming], length=1.5, level=0.0, fade_out=0.12),
    Event("vanguard/meteor_impact", "Meteor strikes", [meteor_impact], length=2.4, level=4.0, fade_out=0.7),
]
