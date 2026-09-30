"""Binary Edges sounds: twin star-metal sickles (GDD 4.2). Paired metallic "tsching"s, a whirr for the throw,
a "shff" and a chime for the blink.

    edges/swing        a quick hook: a thin swish and a paired "tsching" (the two blades ringing a hair apart)
    edges/cross        both blades crossing: two clear "tsching"s, the right one higher
    edges/gyre         the whirl: four "tsching"s coming faster round a spinning swish
    edges/hit          a clean cut: a sharp slice over a light body tick, a short bright ring
    edges/throw        the Tether: a spinning whirr flying away (throbbing at the spin, falling in pitch)
    edges/stick        the blade biting into what it hit: a crisp "tchk" and a short ring
    edges/return       the chain of light reeling it home: a rising whirr into a clink
    edges/blink        the blink's "shff": air rushing past, cut off hard
    edges/chime        the blink's arrival: a two-note glass chime (A and E, D major pentatonic)
    edges/blink_slash  the arrival slash: a bright double "tsching" with a shimmer
    edges/orbit        the Binary Orbit: both blades flung out whirring round, rising
    edges/meteor       the Twin Meteor landing: two blades driven into the ground, a thump and a ring

The rings sit on D major pentatonic like every tuned cue in the set: the right blade F#6, the left D6.

Run:  python tools/sound/build.py edges
"""
from __future__ import annotations

import numpy as np

from dsp import (SR, TWO_PI, ad, band, bump, db, fade, hp, hp_shape, lp_shape, n_of, norm, note, phase_of, place, rng,
                 shaped_noise, silence, softclip, timeline, wobble)
from event import Event
from layers import burst, chime, click, knock, modes, reverb, tail, thump, whoosh

RIGHT = note("F#6")   # the right blade's ring (the cyan one)
LEFT = note("D6")     # the left blade's (violet)
METAL = (1.0, 2.76, 5.40, 8.93)   # an inharmonic bar: star metal, not a bell


def tsching(r, f0, length=0.34, bright=1.0, decay=1.0):
    """One metallic "tsching": a hiss of contact ("ts") running into a short inharmonic ring ("ching")."""
    out = silence(length)
    place(out, click(r, 0.0012, 2500.0), 0.0, db(-6))
    place(out, burst(r, 0.05, 0.0008, 0.012, lo=4500.0), 0.0, db(-3 + 3 * (bright - 1.0)))
    ring = modes(length, [f0 * q for q in METAL], [1.0, 0.55, 0.32, 0.18],
                 [0.10 * decay, 0.055 * decay, 0.03 * decay, 0.018 * decay],
                 beats=[r.uniform(3.0, 6.0), r.uniform(4.0, 9.0), 0.0, 0.0], attack=0.0006)
    place(out, norm(ring), 0.003, db(-2))
    return out


def swish(r, length, peak_at, f_lo=900.0, f_hi=5200.0):
    v = bump(timeline(length), 0.0, length, peak_at, 9.0)
    return norm(whoosh(r, v, f_lo, f_hi, width=0.6, whistle=0.9, whistle_width=0.05, whistle_ratio=1.18,
                       air=0.7, amp_pow=1.5))


def swing(seed, peak_at, gap, length=0.3):
    """A quick hook: a thin swish, and as it peaks both blades ring, the second a hair later."""
    r = rng(seed)
    out = silence(length)
    place(out, swish(r, 0.16, peak_at), 0.0, db(-2))
    when = 0.16 * peak_at
    place(out, norm(tsching(r, RIGHT * r.uniform(0.985, 1.015), 0.22, bright=0.8, decay=0.8)), when, db(-7))
    place(out, norm(tsching(r, LEFT * r.uniform(0.985, 1.015), 0.22, bright=0.7, decay=0.8)), when + gap, db(-10))
    return reverb(norm(out), r, wet=0.12, t60=0.5, damp_hz=7000.0, hp_hz=400.0)


def cross(seed, gap=0.048, length=0.42):
    """Both blades crossing outward: two clear rings, the right one higher, over one swish."""
    r = rng(seed)
    out = silence(length)
    place(out, swish(r, 0.2, 0.45, 800.0, 4600.0), 0.0, db(-3))
    place(out, norm(tsching(r, RIGHT, 0.32)), 0.07, db(-4))
    place(out, norm(tsching(r, LEFT, 0.32)), 0.07 + gap, db(-5))
    return reverb(norm(out), r, wet=0.14, t60=0.6, damp_hz=7000.0, hp_hz=400.0)


def gyre(seed=711, length=0.6):
    """The Gyre: a spinning swish with four rings coming faster and faster."""
    r = rng(seed)
    out = silence(length)
    t = timeline(0.34)
    v = np.clip(0.4 + 0.6 * np.abs(np.sin(TWO_PI * 6.5 * t * (1.0 + 0.8 * t))), 0, 1) * ad(t, 0.02, 0.25)
    spin = whoosh(r, v, 700.0, 4200.0, width=0.7, whistle=0.8, whistle_width=0.05, air=0.6, amp_pow=1.3)
    place(out, norm(spin), 0.0, db(-4))
    for k, when in enumerate((0.06, 0.13, 0.18, 0.22)):
        place(out, norm(tsching(r, (RIGHT if k % 2 == 0 else LEFT) * r.uniform(0.99, 1.01), 0.3, bright=0.9)), when,
              db(-6 - 0.5 * k))
    return reverb(norm(out), r, wet=0.16, t60=0.7, damp_hz=6500.0, hp_hz=400.0)


def hit(seed, ring_hz, length=0.32):
    """A clean cut: a sharp slice (bright noise, fast), a light tick of body, a short ring of the blade."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    place(out, burst(r, 0.07, 0.0006, 0.018, lo=2200.0, hi=11000.0), t0, db(-1))
    place(out, thump(0.12, 260.0, 120.0, 0.008, 0.02, attack=0.001, drive=1.1), t0, db(-7))
    place(out, knock(r, 520.0, tau=0.012, ratios=(1.0, 1.9), amps=(1.0, 0.4)), t0, db(-10))
    place(out, norm(modes(0.25, [ring_hz * q for q in METAL], [1.0, 0.5, 0.25, 0.1], [0.05, 0.03, 0.015, 0.01],
                           attack=0.0005)), t0 + 0.003, db(-8))
    place(out, click(r, 0.001, 3000.0), t0, db(-4))
    out = softclip(norm(out), 1.3)
    return reverb(out, r, wet=0.1, t60=0.45, damp_hz=7000.0, hp_hz=300.0)


def throw(seed=721, length=0.8):
    """A blade spinning away: noise throbbing at the spin (about 18 turns a second), its pitch and level
    falling as it flies off, a ring riding on each turn."""
    r = rng(seed)
    t = timeline(length)
    spin_hz = 18.0 - 4.0 * t / length
    phase = phase_of(spin_hz)
    throb = 0.35 + 0.65 * (0.5 + 0.5 * np.cos(phase)) ** 3
    v = throb * ad(t, 0.015, 0.4)

    def mag(tt, f):
        fc = 2600.0 * (0.62 ** (tt / length))
        return band(f, fc, 0.9) + 0.35 * band(f, fc * 2.1, 0.4)

    air = shaped_noise(length, mag, r) * v
    ring = np.sin(phase_of(RIGHT * 0.5 * (1.0 - 0.06 * t / length))) * throb * ad(t, 0.01, 0.25)
    out = norm(air) + db(-14) * norm(ring)
    return reverb(fade(norm(out), 0.0, 0.08), r, wet=0.15, t60=0.6, damp_hz=6000.0, hp_hz=300.0)


def stick(seed, length=0.35):
    """The blade biting in: a crisp "tchk" (a click and a short high burst), a small thud, a short ring."""
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.0012, 1800.0), 0.0, db(-2))
    place(out, burst(r, 0.04, 0.0005, 0.008, lo=1800.0, hi=9000.0), 0.0, db(-3))
    place(out, thump(0.1, 200.0, 110.0, 0.01, 0.025, attack=0.001, drive=1.3), 0.0, db(-6))
    place(out, norm(modes(0.3, [LEFT * q for q in METAL], [1.0, 0.45, 0.2, 0.1], [0.07, 0.035, 0.02, 0.012])),
          0.004, db(-9))
    return reverb(softclip(norm(out), 1.2), r, wet=0.12, t60=0.5, damp_hz=6000.0, hp_hz=300.0)


def reel(seed=741, length=0.45):
    """Reeled home: a whirr rising toward the hand, and the clink of the blade caught."""
    r = rng(seed)
    t = timeline(0.34)
    spin = phase_of(10.0 + 20.0 * t / 0.34)
    v = (0.3 + 0.7 * (0.5 + 0.5 * np.cos(spin)) ** 2) * (t / 0.34) ** 1.5

    def mag(tt, f):
        return band(f, 1500.0 * (2.2 ** (tt / 0.34)), 0.8)

    out = silence(length)
    place(out, tail(norm(shaped_noise(0.34, mag, r) * v), longest=0.004), 0.0, db(-4))
    place(out, norm(tsching(r, LEFT * 1.5, 0.12, bright=0.6, decay=0.5)), 0.33, db(-6))
    return reverb(norm(out), r, wet=0.12, t60=0.5, damp_hz=6000.0, hp_hz=400.0)


def blink(seed=751, length=0.24):
    """The blink's "shff": air rushing past, swelling fast and cut off hard as the body arrives."""
    r = rng(seed)
    t = timeline(length)
    env = (t / length) ** 1.8 * (t < length * 0.92)
    env = np.convolve(env, np.ones(40) / 40.0, mode="same")

    def mag(tt, f):
        return band(f, 1800.0 + 5200.0 * (tt / length), 1.3) + 0.4 * hp_shape(f, 6000.0) * lp_shape(f, 15000.0)

    x = shaped_noise(length, mag, r) * env
    return fade(norm(x), 0.002, 0.006)


def arrival_chime(seed=761, length=0.9):
    """A two-note glass chime: A then E above it, the second a breath later."""
    r = rng(seed)
    out = silence(length)
    place(out, norm(chime(note("A6"), 0.8, amps=(1.0, 0.3, 0.1, 0.03), taus=(0.35, 0.16, 0.06, 0.025))), 0.0, db(-2))
    place(out, norm(chime(note("E7"), 0.7, amps=(1.0, 0.25, 0.08, 0.02), taus=(0.3, 0.12, 0.05, 0.02))), 0.045, db(-5))
    return reverb(norm(out), r, wet=0.3, t60=1.2, damp_hz=8000.0, hp_hz=500.0)


def blink_slash(seed=771, length=0.5):
    """The arrival slash: a fast bright swish and both blades ringing together, a shimmer after."""
    r = rng(seed)
    out = silence(length)
    place(out, swish(r, 0.14, 0.4, 1200.0, 6500.0), 0.0, db(-3))
    place(out, norm(tsching(r, RIGHT, 0.38, bright=1.2)), 0.04, db(-3))
    place(out, norm(tsching(r, LEFT * 1.5, 0.38, bright=1.1)), 0.065, db(-5))
    shimmer = hp(r.standard_normal(n_of(0.3)), 6000.0, 2) * np.abs(wobble(r, n_of(0.3), 50.0)) * ad(timeline(0.3), 0.01, 0.1)
    place(out, tail(norm(shimmer)), 0.06, db(-16))
    return reverb(norm(out), r, wet=0.18, t60=0.7, damp_hz=7000.0, hp_hz=400.0)


def orbit(seed=781, length=0.7):
    """Both blades flung round on their chains: two whirrs a half turn apart, rising, with rings on each pass."""
    r = rng(seed)
    t = timeline(length)
    turn_hz = 6.0 + 3.0 * t / length
    ph = phase_of(turn_hz)
    v = (0.35 + 0.65 * np.maximum(np.cos(ph), np.cos(ph + np.pi)) ** 4) * ad(t, 0.04, 0.45)

    def mag(tt, f):
        return band(f, 1300.0 * (1.6 ** (tt / length)), 0.8) + 0.3 * band(f, 4200.0, 0.5)

    out = norm(shaped_noise(length, mag, r) * v)
    for k in range(5):
        when = 0.05 + k * 0.1
        place(out, norm(tsching(r, (RIGHT if k % 2 == 0 else LEFT), 0.16, bright=0.6, decay=0.6)), when, db(-13))
    return reverb(fade(norm(out), 0.0, 0.1), r, wet=0.16, t60=0.7, damp_hz=6000.0, hp_hz=300.0)


def meteor(seed, length=0.8):
    """The Twin Meteor landing: both blades driven into the ground (a thump and a crack), their rings after."""
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.45, 110.0, 45.0, 0.03, 0.1, attack=0.002, drive=1.6), 0.0)
    place(out, burst(r, 0.2, 0.0005, 0.03, lo=700.0, hi=5000.0), 0.0, db(-5))
    place(out, knock(r, 180.0, tau=0.03), 0.0, db(-6))
    place(out, norm(tsching(r, RIGHT, 0.5, decay=1.6)), 0.01, db(-7))
    place(out, norm(tsching(r, LEFT, 0.5, decay=1.6)), 0.03, db(-8))
    out = softclip(norm(out), 1.4)
    return reverb(out, r, wet=0.2, t60=0.9, damp_hz=5000.0, hp_hz=150.0)


EVENTS = [
    Event("edges/swing", "Sickles ring",
          [lambda: swing(701, 0.55, 0.035), lambda: swing(702, 0.5, 0.042), lambda: swing(703, 0.6, 0.03)],
          length=0.3, level=-3.0, fade_out=0.05),
    Event("edges/cross", "Sickles cross", [lambda: cross(704), lambda: cross(705, 0.056)], length=0.42, level=-2.0,
          fade_out=0.06),
    Event("edges/gyre", "Sickles whirl", [gyre], length=0.6, level=-2.0, fade_out=0.08),
    Event("edges/hit", "Sickle cuts", [lambda: hit(712, RIGHT), lambda: hit(713, LEFT), lambda: hit(714, note("A6"))],
          length=0.32, level=-1.0, fade_out=0.05),
    Event("edges/throw", "Sickle whirs away", [throw], length=0.8, level=-4.0, fade_out=0.1),
    Event("edges/stick", "Sickle bites", [lambda: stick(731), lambda: stick(732)], length=0.35, level=-4.0, fade_out=0.05),
    Event("edges/return", "Sickle returns", [reel], length=0.45, level=-6.0, fade_out=0.06),
    Event("edges/blink", "Blink rushes", [blink], length=0.24, level=-3.0, fade_out=0.006),
    Event("edges/chime", "Blink chimes", [arrival_chime], length=0.9, level=-5.0, fade_out=0.15),
    Event("edges/blink_slash", "Sickles flash", [blink_slash], length=0.5, level=0.0, fade_out=0.08),
    Event("edges/orbit", "Sickles orbit", [orbit], length=0.7, level=-3.0, fade_out=0.1),
    Event("edges/meteor", "Twin Meteor lands", [lambda: meteor(791), lambda: meteor(792)], length=0.8, level=1.0,
          fade_out=0.15),
]
