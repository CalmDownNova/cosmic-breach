"""Comet Maul sounds: a meteor-forged greathammer (GDD 4.2).

    maul/swing     a deep whoosh: displaced air and turbulence, no whistle (a hammer head has no edge);
                   the game pitches a charged release by its charge (MoveSound.swingPitch)
    maul/slam      where the head meets the ground: a boom with a sub layer, crushed stone, a rumble tail
    maul/hit       a hit on a body: a heavy blunt crunch over a low thump
    maul/charge    the hold before the Impact Crater: a deep whoosh that turns like a slow wheel, embers
                   crackling in it; loops seamlessly, and the game raises its pitch with the charge
    maul/well      the Gravity Well: a reversed sweep into a hum that rises for the whole pull
    maul/collapse  the Collapse: the air sucked in, then a deep "thoom"

Run:  python tools/sound/build.py maul
"""
from __future__ import annotations

import numpy as np

from dsp import (SR, TWO_PI, ad, band, bump, circular_filter, db, fade, hp_shape, lp, lp_shape, n_of, norm,
                 phase_of, place, place_wrap, rng, shaped_noise, silence, softclip, timeline, wobble)
from event import Event
from layers import burst, click, crackle, granular_hiss, knock, reverb, reverb_wrap, tail, thump, whoosh


def swing(seed, peak_at, length=0.42):
    """Deep whoosh: a broad low band that opens up as the head speeds past, and the push of air."""
    r = rng(seed)
    v = bump(timeline(length), 0.0, length, peak_at, 5.0)
    air = whoosh(r, v, 70.0, 520.0, width=1.15, whistle=0.12, whistle_width=0.1, whistle_ratio=1.05,
                 air=0.12, air_hz=3500.0, body=1.3, body_hz=85.0, flutter=0.35, flutter_hz=17.0, amp_pow=1.1)
    out = silence(length)
    place(out, norm(air))
    return out


def slam(seed, length=1.0):
    """A boom with a sub layer: the head driven into stone. Crushed rock on top, a rumble after."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.002
    place(out, thump(0.9, 82.0, 30.0, 0.05, 0.16, attack=0.003, drive=1.8), t0)          # the boom
    sub = np.sin(phase_of(np.full(n_of(0.8), 38.0))) * ad(timeline(0.8), 0.006, 0.28)     # the sub layer
    place(out, tail(sub), t0, db(-3))
    place(out, thump(0.3, 190.0, 70.0, 0.012, 0.05, drive=1.4), t0, db(-5))              # the knock of the face
    place(out, burst(r, 0.35, 0.0006, 0.05, hi=900.0), t0, db(-4))                       # the crush
    place(out, crackle(r, 16, 0.05, 900.0, 5000.0, grain=0.0012, falloff=0.5), t0 + 0.004, db(-3))
    rt = timeline(0.9)
    rumble = shaped_noise(0.9, lambda tt, f: lp_shape(f, 140.0, 3) * hp_shape(f, 25.0, 2), r)
    place(out, tail(norm(rumble * ad(rt, 0.015, 0.3)), longest=0.12), t0, db(-7))
    place(out, crackle(r, 9, 0.35, 1200.0, 4500.0, grain=0.001, falloff=0.9), t0 + 0.06, db(-12))  # debris
    place(out, click(r, 0.002, 400.0), t0, db(-6))
    out = softclip(norm(out), 1.5)
    return reverb(out, r, wet=0.2, t60=1.0, damp_hz=5000.0, hp_hz=200.0)


def hit(seed, thump_hz, length=0.45):
    """A heavy blunt hit: a low thump under a crunch, shorter and drier than the slam."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.0015
    place(out, thump(0.4, thump_hz, 48.0, 0.025, 0.08, attack=0.0015, drive=2.0), t0)
    place(out, knock(r, 165.0, tau=0.03, ratios=(1.0, 1.6, 2.4), amps=(1.0, 0.5, 0.25)), t0, db(-3))
    place(out, burst(r, 0.2, 0.0004, 0.03, lo=500.0, hi=3500.0), t0, db(-5))
    place(out, crackle(r, 8, 0.02, 1500.0, 7000.0, grain=0.0008, falloff=0.6), t0 + 0.002, db(-6))
    place(out, click(r, 0.0015, 800.0), t0, db(-6))
    out = softclip(norm(out), 1.6)
    return reverb(out, r, wet=0.12, t60=0.7, damp_hz=6000.0, hp_hz=250.0)


def charge(seed=611, length=2.0):
    """A deep whoosh turning like a slow wheel (two swells a second), a low hum under it and embers
    crackling; built on the loop's length so it repeats with no seam."""
    r = rng(seed)
    n = n_of(length)
    t = np.arange(n) / SR
    noise = r.standard_normal(n)
    # the wheel: the band's centre and the level turn together, 4 times a loop (periodic, so seamless)
    turn = 0.5 - 0.5 * np.cos(TWO_PI * 4.0 * t / length)
    low = circular_filter(noise, lambda f: band(f, 95.0, 1.2))
    mid = circular_filter(noise, lambda f: band(f, 260.0, 1.0))
    air = low * (0.55 + 0.45 * turn) + mid * (0.15 + 0.5 * turn)
    hum = (np.sin(TWO_PI * 55.0 * t) + 0.45 * np.sin(TWO_PI * 110.0 * t + 0.7) + 0.2 * np.sin(TWO_PI * 165.0 * t + 1.9))
    hum *= 0.8 + 0.2 * np.cos(TWO_PI * 2.0 * t / length)
    embers = np.zeros(n)
    for _ in range(18):
        when = r.uniform(0.0, length)
        g = crackle(r, 3, 0.02, 2500.0, 9000.0, grain=0.0006, falloff=0.5)
        place_wrap(embers, norm(g), when, r.uniform(0.3, 1.0))
    mix = norm(air) + db(-9) * norm(hum) + db(-17) * embers
    mix = circular_filter(mix, lambda f: lp_shape(f, 6000.0, 2) * hp_shape(f, 40.0, 2))
    mix = reverb_wrap(mix, r, wet=0.25, t60=1.1, damp_hz=4000.0, hp_hz=120.0)
    return mix - np.mean(mix)


def well(seed=621, length=1.75):
    """A reversed sweep (noise swelling up to the plant... backwards, so it sucks in) running into a
    hum that climbs for the whole pull and throbs faster as it goes."""
    r = rng(seed)
    out = silence(length)
    sweep_len = 0.55
    st = timeline(sweep_len)
    sweep = shaped_noise(sweep_len, lambda tt, f: band(f, 300.0 * (8.0 ** (tt / sweep_len)), 1.4), r)
    sweep = sweep * (st / sweep_len) ** 2.5          # a crash played backwards: swelling to a hard stop
    place(out, tail(norm(sweep), longest=0.004), 0.0, db(-2))
    ht = timeline(length - 0.3)
    f = 58.0 * (1.9 ** (ht / ht[-1]))                 # 58 Hz up to 110 Hz over the pull
    throb = 0.7 + 0.3 * np.sin(phase_of(3.0 + 9.0 * (ht / ht[-1])))
    hum = (np.sin(phase_of(f)) + 0.5 * np.sin(phase_of(2.0 * f) + 0.3) + 0.25 * np.sin(phase_of(3.01 * f)))
    hum = hum * throb * np.clip(ht / 0.15, 0, 1) * (0.55 + 0.45 * ht / ht[-1])
    place(out, norm(hum), 0.3, db(-3))
    dust = shaped_noise(length - 0.3, lambda tt, fq: band(fq, 900.0 + 1800.0 * tt / (length - 0.3), 1.6), r)
    place(out, norm(dust * (0.3 + 0.7 * ht / ht[-1])), 0.3, db(-15))
    out = fade(norm(out), 0.0, 0.06)
    return reverb(out, r, wet=0.22, t60=1.3, damp_hz=4500.0, hp_hz=150.0)


def collapse(seed=631, length=1.1):
    """A sucked-in "thoom": a short in-breath of air rising into a deep, round, soft-edged boom."""
    r = rng(seed)
    out = silence(length)
    inhale = 0.14
    it = timeline(inhale)
    breath = shaped_noise(inhale, lambda tt, f: band(f, 400.0 * (6.0 ** (tt / inhale)), 1.2), r)
    place(out, tail(norm(breath * (it / inhale) ** 2.0), longest=0.003), 0.0, db(-5))
    t0 = inhale
    place(out, thump(0.95, 95.0, 32.0, 0.07, 0.22, attack=0.012, drive=1.2), t0)             # the "thoom"
    sub = np.sin(phase_of(np.full(n_of(0.9), 34.0))) * ad(timeline(0.9), 0.02, 0.32)
    place(out, tail(sub), t0, db(-3))
    place(out, burst(r, 0.4, 0.004, 0.08, hi=600.0), t0, db(-8))
    place(out, granular_hiss(r, 0.45, lo=2500.0, rate=40.0, attack=0.003, tau=0.12), t0 + 0.01, db(-16))
    out = softclip(norm(out), 1.3)
    return reverb(out, r, wet=0.25, t60=1.4, damp_hz=4000.0, hp_hz=150.0)


EVENTS = [
    Event("maul/swing", "Maul whooshes",
          [lambda: swing(601, 0.62), lambda: swing(602, 0.55)], length=0.45, level=-1.0, fade_out=0.06),
    Event("maul/slam", "Maul slams the ground",
          [lambda: slam(603), lambda: slam(604)], length=1.0, level=3.0, fade_out=0.2),
    Event("maul/hit", "Maul crushes",
          [lambda: hit(605, 125.0), lambda: hit(606, 112.0)], length=0.45, level=1.0, fade_out=0.08),
    Event("maul/charge", "Maul charges", [charge], length=2.0, level=-8.0, loop=True, quality=7),
    Event("maul/well", "Gravity Well pulls", [well], length=1.75, level=-2.0, fade_out=0.06),
    Event("maul/collapse", "Gravity Well collapses", [collapse], length=1.1, level=3.0, fade_out=0.2),
]
