"""The combat familiars' sounds (G10, GDD 8.2).

    familiar/summon        a familiar comes out of its lantern: a reversed glass shimmer swelling into a soft D and A chime
    familiar/dismiss       it goes back: a falling A to D chime into a small lantern click
    familiar/mode          a tap of G: a tiny latch click and one ting (the game pitches it by the stance)
    familiar/hatch         a Star Egg hatches: the shell crackling, then a bright D major burst of glass and a warm swell
    familiar/egg_set       an egg set in the brazier: a soft stone clunk and a low warm hum
    familiar/hurt          a familiar is hit: a short soft thump with a glassy tick
    familiar/death         a familiar fades: a descending shimmer and a soft breath of air
    familiar/wisp_strike   the Emberwisp's hit: a quick fiery "fff" with a crackle
    familiar/scorch        the Emberwisp throws Scorch: a small flame whoosh and sparks
    familiar/kindled       Kindled: a rising sparkle into a warm D and A ting
    familiar/hop           the Gravikin lands a hop: a small stone knock and a pebble clatter
    familiar/slam          the Gravikin's hit: a heavier stone thump and clatter
    familiar/taunt         the Gravikin taunts: a deep stomp, a stone gong and a rising low hum
    familiar/moth_strike   the Prism Moth's hit: a papery flutter and a glass tick
    familiar/refract       a Refract glint: a clear glass ting on D6 over a thin sparkle
    familiar/refract_break three stacks consumed: glass shattering over a D major chord
    familiar/cleanse       the Prism Moth lifts a status: a rising D, F#, A, D of glass and an airy sigh

Every tuned part in D. Levels (offsets from combat/hit's -15 LUFS): hatch and taunt +0 and -1, refract break -2,
summon, death and slam -3 to -4, kindled and cleanse -4, scorch -5, dismiss -5, egg set, hurt and the wisp's strike -6,
refract -6, the moth's strike -7, mode -8, hop -9.

Run:  python tools/sound/build.py familiar
"""
from __future__ import annotations

import numpy as np

from dsp import SR, ad, db, fade, n_of, norm, place, rng, silence, softclip, timeline
from event import Event
from layers import BAR, burst, chime, click, crackle, granular_hiss, knock, reverb, shatter, tail, thump, whoosh

D3, A3, D4, FS4, A4, D5, FS5, A5, D6, FS6, A6, D7 = (146.83, 220.0, 293.66, 369.99, 440.0, 587.33, 739.99, 880.0,
                                                    1174.66, 1479.98, 1760.0, 2349.32)


def ting(hz, seconds=0.9, level=0.0):
    """A clear crystal-rod ting on `hz`."""
    return tail(chime(hz, seconds, ratios=BAR, amps=(1.0, 0.18, 0.05, 0.0), taus=(0.35, 0.12, 0.05, 0.02))) * db(level)


def hum(hz, seconds, attack, tau):
    t = timeline(seconds)
    x = np.sin(2 * np.pi * hz * t) + 0.3 * np.sin(2 * np.pi * hz * 2.0 * t) + 0.12 * np.sin(2 * np.pi * hz * 3.0 * t)
    return tail(x * ad(t, attack, tau))


def summon(seed=10101, length=1.1):
    r = rng(seed)
    out = silence(length)
    sw = granular_hiss(r, 0.45, lo=4000.0, rate=70.0, attack=0.001, tau=0.2)[::-1].copy()
    place(out, sw, 0.0, db(-8))
    place(out, ting(D5, 0.8), 0.42, db(-2))
    place(out, ting(A5, 0.7), 0.47, db(-6))
    place(out, ting(D6, 0.6), 0.52, db(-10))
    return reverb(fade(softclip(norm(out), 1.1), 0.02, 0.25), r, wet=0.25, t60=1.1, damp_hz=7000.0, hp_hz=220.0)


def dismiss(seed=10111, length=0.9):
    r = rng(seed)
    out = silence(length)
    place(out, ting(A5, 0.5), 0.0, db(-3))
    place(out, ting(D5, 0.6), 0.1, db(-2))
    place(out, click(r, 0.002, 1800.0), 0.3, db(-9))
    place(out, ting(D6 * 1.5, 0.12), 0.3, db(-14))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.2), r, wet=0.2, t60=0.9, damp_hz=7000.0, hp_hz=220.0)


def mode(seed=10121, length=0.45):
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.0015, 2200.0), 0.0, db(-6))
    place(out, ting(A5, 0.4), 0.012, db(-1))
    return reverb(fade(norm(out), 0.001, 0.12), r, wet=0.15, t60=0.6, damp_hz=8000.0, hp_hz=300.0)


def hatch(seed=10131, length=1.8):
    r = rng(seed)
    out = silence(length)
    for i in range(3):
        place(out, crackle(r, n_events=7, spread=0.03, hp_hz=2200.0), 0.05 * i + r.uniform(0, 0.02), db(-4 - 2 * i))
    place(out, shatter(r, 0.6, 26, f_lo=2000.0, f_hi=8000.0), 0.17, db(-10))
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, ting(hz, 1.0), 0.2 + 0.03 * i, db(-2 - 2 * i))
    place(out, hum(D4, 1.4, 0.15, 0.5), 0.2, db(-10))
    return reverb(fade(softclip(norm(out), 1.15), 0.001, 0.4), r, wet=0.28, t60=1.4, damp_hz=7000.0, hp_hz=180.0)


def egg_set(seed=10141, length=1.0):
    r = rng(seed)
    out = silence(length)
    place(out, knock(r, 320.0, tau=0.03), 0.0, db(-2))
    place(out, hum(D3 * 2, 0.9, 0.08, 0.35), 0.03, db(-8))
    place(out, ting(D6, 0.4), 0.02, db(-16))
    return reverb(fade(softclip(norm(out), 1.1), 0.001, 0.25), r, wet=0.18, t60=0.8, damp_hz=6000.0, hp_hz=120.0)


def hurt(seed=10151, length=0.35):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.2, 320.0, 160.0, 0.02, 0.05), 0.0, db(-3))
    place(out, ting(A6, 0.2), 0.0, db(-8))
    place(out, click(r, 0.001, 2500.0), 0.0, db(-10))
    return fade(norm(out), 0.001, 0.08)


def death(seed=10161, length=1.4):
    r = rng(seed)
    out = silence(length)
    for i, hz in enumerate((D6, A5, FS5, D5)):
        place(out, ting(hz, 0.8), 0.07 * i, db(-2 - i))
    t = timeline(0.9)
    breath = burst(r, 0.9, 0.08, 0.3, lo=500.0, hi=3000.0)
    place(out, breath, 0.1, db(-12))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.4), r, wet=0.3, t60=1.3, damp_hz=6500.0, hp_hz=200.0)


def wisp_strike(seed=10171, length=0.35):
    r = rng(seed)
    out = silence(length)
    place(out, burst(r, 0.2, 0.004, 0.05, lo=900.0, hi=5000.0), 0.0, db(-1))
    place(out, crackle(r, n_events=5, spread=0.04, hp_hz=3000.0), 0.02, db(-7))
    place(out, thump(0.12, 240.0, 120.0, 0.015, 0.04), 0.0, db(-10))
    return fade(norm(out), 0.001, 0.08)


def scorch(seed=10181, length=0.8):
    r = rng(seed)
    out = silence(length)
    n = n_of(0.45)
    v = np.clip(np.linspace(0.0, 1.8, n), 0.0, 1.0) * np.clip(np.linspace(1.3, 0.0, n), 0.0, 1.0)
    place(out, tail(whoosh(r, v, 500.0, 3000.0, width=0.9, whistle=0.2, air=0.8, body=0.3)), 0.0, db(-2))
    for i in range(4):
        place(out, crackle(r, n_events=6, spread=0.05, hp_hz=2500.0), 0.12 + 0.09 * i, db(-8 - i))
    return reverb(fade(softclip(norm(out), 1.1), 0.003, 0.2), r, wet=0.15, t60=0.7, damp_hz=7000.0, hp_hz=200.0)


def kindled(seed=10191, length=1.0):
    r = rng(seed)
    out = silence(length)
    place(out, granular_hiss(r, 0.35, lo=5000.0, rate=90.0, tau=0.12)[::-1].copy(), 0.0, db(-10))
    place(out, ting(D5, 0.8), 0.3, db(-1))
    place(out, ting(A5, 0.7), 0.33, db(-4))
    place(out, crackle(r, n_events=5, spread=0.05), 0.3, db(-12))
    return reverb(fade(softclip(norm(out), 1.1), 0.01, 0.25), r, wet=0.22, t60=1.0, damp_hz=7500.0, hp_hz=220.0)


def hop(seed=10201, length=0.4):
    r = rng(seed)
    out = silence(length)
    place(out, knock(r, 210.0, tau=0.025), 0.0, db(-1))
    for i in range(4):
        place(out, knock(r, r.uniform(700.0, 1500.0), tau=0.012), 0.02 + 0.03 * i + r.uniform(0, 0.015), db(-9 - 2 * i))
    return fade(norm(out), 0.001, 0.1)


def slam(seed=10211, length=0.6):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.35, 150.0, 60.0, 0.03, 0.1, drive=1.6), 0.0, db(-1))
    place(out, knock(r, 260.0, tau=0.03), 0.0, db(-4))
    for i in range(6):
        place(out, knock(r, r.uniform(600.0, 1600.0), tau=0.012), 0.03 + 0.03 * i + r.uniform(0, 0.02), db(-10 - i))
    return fade(softclip(norm(out), 1.2), 0.001, 0.15)


def taunt(seed=10221, length=1.6):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.6, 110.0, 45.0, 0.05, 0.2, drive=2.0), 0.0, db(0))
    place(out, chime(D3, 1.3, ratios=(1.0, 2.76, 5.4), amps=(1.0, 0.35, 0.12), taus=(0.6, 0.25, 0.1)), 0.02, db(-5))
    t = timeline(1.2)
    rising = np.sin(2 * np.pi * np.cumsum(70.0 + 60.0 * t / 1.2) / SR) * ad(t, 0.3, 0.6)
    place(out, tail(rising), 0.1, db(-8))
    for i in range(5):
        place(out, knock(r, r.uniform(500.0, 1300.0), tau=0.015), 0.05 + 0.04 * i, db(-12 - i))
    return reverb(fade(softclip(norm(out), 1.2), 0.001, 0.4), r, wet=0.22, t60=1.2, damp_hz=5000.0, hp_hz=60.0)


def moth_strike(seed=10231, length=0.35):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.18)
    flutter = r.standard_normal(len(t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 45.0 * t)) * ad(t, 0.01, 0.06)
    place(out, tail(norm(flutter)), 0.0, db(-6))
    place(out, ting(FS6, 0.25), 0.03, db(-2))
    place(out, click(r, 0.001, 3000.0), 0.03, db(-9))
    return fade(norm(out), 0.001, 0.08)


def refract(seed=10241, length=0.8):
    r = rng(seed)
    out = silence(length)
    place(out, ting(D6, 0.7), 0.0, db(0))
    place(out, ting(A6, 0.5), 0.02, db(-9))
    place(out, granular_hiss(r, 0.4, lo=6000.0, rate=110.0, tau=0.12), 0.0, db(-14))
    return reverb(fade(norm(out), 0.001, 0.2), r, wet=0.25, t60=1.0, damp_hz=9000.0, hp_hz=400.0)


def refract_break(seed=10251, length=1.4):
    r = rng(seed)
    out = silence(length)
    place(out, shatter(r, 0.9, 40, f_lo=2500.0, f_hi=10000.0), 0.0, db(-2))
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, ting(hz, 1.0), 0.01 + 0.02 * i, db(-4 - 2 * i))
    place(out, thump(0.2, 200.0, 90.0, 0.02, 0.06), 0.0, db(-9))
    return reverb(fade(softclip(norm(out), 1.15), 0.001, 0.35), r, wet=0.25, t60=1.2, damp_hz=8000.0, hp_hz=200.0)


def cleanse(seed=10261, length=1.3):
    r = rng(seed)
    out = silence(length)
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, ting(hz, 0.8), 0.08 * i, db(-2 - i))
    place(out, burst(r, 0.8, 0.2, 0.3, lo=2000.0, hi=9000.0), 0.05, db(-14))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.35), r, wet=0.3, t60=1.3, damp_hz=8000.0, hp_hz=250.0)


EVENTS = [
    Event("familiar/summon", "Familiar appears", [summon], length=1.1, level=-3.0, fade_out=0.25),
    Event("familiar/dismiss", "Familiar returns to its lantern", [dismiss], length=0.9, level=-5.0, fade_out=0.2),
    Event("familiar/mode", "Familiar changes stance", [mode], length=0.45, level=-8.0, fade_out=0.12),
    Event("familiar/hatch", "Star Egg hatches", [hatch], length=1.8, level=0.0, fade_out=0.4),
    Event("familiar/egg_set", "Star Egg settles in the brazier", [egg_set], length=1.0, level=-6.0, fade_out=0.25),
    Event("familiar/hurt", "Familiar hurts", [hurt], length=0.35, level=-6.0, fade_out=0.08),
    Event("familiar/death", "Familiar fades", [death], length=1.4, level=-3.0, fade_out=0.4),
    Event("familiar/wisp_strike", "Emberwisp strikes", [wisp_strike], length=0.35, level=-6.0, fade_out=0.08),
    Event("familiar/scorch", "Emberwisp scorches", [scorch], length=0.8, level=-5.0, fade_out=0.2),
    Event("familiar/kindled", "Kindled", [kindled], length=1.0, level=-4.0, fade_out=0.25),
    Event("familiar/hop", "Gravikin lands", [hop], length=0.4, level=-9.0, fade_out=0.1),
    Event("familiar/slam", "Gravikin slams", [slam], length=0.6, level=-4.0, fade_out=0.15),
    Event("familiar/taunt", "Gravikin taunts", [taunt], length=1.6, level=-1.0, fade_out=0.4),
    Event("familiar/moth_strike", "Prism Moth strikes", [moth_strike], length=0.35, level=-7.0, fade_out=0.08),
    Event("familiar/refract", "Prism Moth glints", [refract], length=0.8, level=-6.0, fade_out=0.2),
    Event("familiar/refract_break", "Refract shatters", [refract_break], length=1.4, level=-2.0, fade_out=0.35),
    Event("familiar/cleanse", "Prism Moth cleanses", [cleanse], length=1.3, level=-4.0, fade_out=0.35),
]
