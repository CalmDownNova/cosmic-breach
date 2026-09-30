"""Shardling sounds: a small crystal creature and the shards it leaves behind."""
from __future__ import annotations

from functools import partial

import numpy as np

from dsp import (TWO_PI, ad, band, bump, db, norm, note, phase_of, place, rise, rng,
                 shaped_noise, silence, softclip, timeline)
from event import Event
from layers import (BAR, GLASS, burst, chime, click, crackle, glass_grain, granular_hiss, knock,
                    modes, reverb, shatter, tail, thump, whoosh)


def step(seed, bead_hz, tock_hz, bounce, length=0.08):
    """A glass bead tapping stone: a tiny stony tock, a bright bead ring, maybe a small bounce."""
    r = rng(seed)
    out = silence(length)

    def tap(gain):
        x = silence(length)
        place(x, click(r, 0.0004, 1500.0), 0.0, db(-3))
        place(x, norm(modes(0.03, [tock_hz, tock_hz * 1.6], (1.0, 0.5), (0.004, 0.0025))), 0.0)
        place(x, norm(modes(0.06, [bead_hz * q for q in (1.0, 1.52, 2.4)], (1.0, 0.5, 0.25),
                            (0.018, 0.012, 0.008))), 0.0003, db(-4))
        place(x, norm(modes(0.03, [600.0], (1.0,), (0.005,))), 0.0, db(-12))
        return x * gain

    t0 = 0.001
    place(out, tap(1.0), t0)
    if bounce:
        place(out, tap(db(-10)), t0 + bounce)
    return out


def tell(seed=801, length=0.6):
    """Warning: a glassy ting that swells and rises a fifth, trembling faster as it builds."""
    r = rng(seed)
    t = timeline(length)
    end = 0.48                                     # the moment of the lunge
    glide = np.clip((t - 0.04) / (end - 0.04), 0.0, 1.0) ** 1.8
    ph = phase_of(1650.0 * 1.5 ** glide)
    tone = np.zeros_like(t)
    for q, a, beat in zip(GLASS[:3], (1.0, 0.3, 0.1), (3.0, 0.0, 0.0)):
        tone += a * np.sin(q * ph)
        if beat:
            tone += 0.3 * a * np.sin(q * ph + TWO_PI * beat * t)
    build = np.clip(t / end, 0.0, 1.0)
    strike = 0.45 * np.exp(-t / 0.05)             # the glint that grabs attention...
    swell = 0.3 + 0.7 * build ** 2                 # ...then a clear crescendo into the lunge
    release = np.exp(-np.maximum(t - end, 0.0) / 0.03)
    tremolo = 1.0 - 0.3 * (0.5 + 0.5 * np.sin(phase_of(10.0 + 14.0 * build)))
    tone *= (strike + swell) * release * tremolo * rise(t, 0.0015)

    def mag(tt, f):
        return band(f, 2000.0 * 3.0 ** np.clip(tt / end, 0.0, 1.0), 0.5)

    hiss = shaped_noise(length, mag, r) * build ** 3 * release
    out = silence(length)
    place(out, norm(tone))
    place(out, norm(hiss), 0.0, db(-11))
    place(out, click(r, 0.0006, 3000.0), 0.0, db(-12))
    return reverb(out, r, wet=0.12, t60=0.5, damp_hz=8000.0, hp_hz=600.0)


def lunge(seed=802, length=0.25):
    """A quick glassy rush: bright air with crystals rattling on it."""
    r = rng(seed)
    t = timeline(length)
    v = bump(t, 0.0, length, 0.3, 6.0)
    out = silence(length)
    place(out, norm(whoosh(r, v, 1200.0, 4800.0, width=0.8, whistle=0.6, whistle_ratio=1.1,
                           air=0.6, amp_pow=1.3)))
    for _ in range(10):
        when = r.uniform(0.01, 0.18)
        a = float(np.interp(when, t, v)) * r.uniform(0.5, 1.0)
        place(out, glass_grain(r, r.uniform(5000.0, 10000.0), tau=r.uniform(0.012, 0.03)),
              when, db(-10) * a)
    pt = timeline(0.12)
    push = shaped_noise(0.12, lambda tt, f: band(f, 300.0, 0.6), r) * bump(pt, 0.0, 0.12, 0.3, 4.0)
    place(out, norm(push), 0.0, db(-10))
    return out


def hurt(seed, ring_hz, ratios, knock_hz, length=0.2):
    """A brittle crack with a few splinters flaking off after it."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.0012
    place(out, crackle(r, 12, 0.03, 1800.0, grain=0.0009), t0, db(-3))
    place(out, click(r, 0.001, 1200.0), t0, db(-8))
    ring = modes(0.18, [ring_hz * q for q in ratios], (1.0, 0.7, 0.45, 0.3),
                 (0.06, 0.04, 0.03, 0.02))
    place(out, norm(ring), t0 + 0.0005, db(-4))
    place(out, knock(r, knock_hz, tau=0.012), t0, db(-3))
    for _ in range(4):                             # small flakes, fewer and quieter as they go
        when = r.uniform(0.025, 0.11)
        place(out, glass_grain(r, r.uniform(4000.0, 9000.0), tau=r.uniform(0.01, 0.025)),
              t0 + when, db(-12) * np.exp(-when / 0.06) * r.uniform(0.6, 1.0))
    out = softclip(norm(out), 1.5)
    return reverb(out, r, wet=0.08, t60=0.3, damp_hz=8000.0, hp_hz=500.0)


def death(seed=804, length=0.7):
    """A shatter burst: the body cracks, pops and rains down as fragments."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.0015
    place(out, thump(0.3, 190.0, 85.0, 0.02, 0.04, drive=1.5), t0, db(-4))
    place(out, burst(r, 0.3, 0.001, 0.05, hi=500.0), t0, db(-6))
    place(out, crackle(r, 12, 0.03, 1800.0), t0)
    place(out, click(r, 0.0015, 800.0), t0, db(-3))
    place(out, norm(shatter(r, length, 70, 2500.0, 11000.0, burst_frac=0.4, burst_len=0.04,
                            t_tau=0.17, amp_tau=0.26)), t0 + 0.002, db(-2))
    place(out, granular_hiss(r, 0.4, lo=4000.0, rate=70.0, attack=0.001, tau=0.07), t0, db(-9))
    ring = modes(0.5, [1480.0, 2093.0, 1480.0 * 2.32, 2093.0 * 2.32], (1.0, 0.8, 0.2, 0.15),
                 (0.18, 0.15, 0.06, 0.05), beats=(3.1, 2.3, 0.0, 0.0))
    place(out, norm(ring), t0, db(-14))
    return reverb(out, r, wet=0.15, t60=0.7, damp_hz=8000.0, hp_hz=400.0)


def spit(seed=805, length=0.2):
    """A sharp crystalline spit: a hissed "tss", a glass tik, and a needle zipping away."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    place(out, burst(r, 0.2, 0.0015, 0.03, lo=2500.0, hi=9000.0), t0)
    place(out, norm(modes(0.1, [4500.0, 7200.0], (1.0, 0.6), (0.02, 0.012))), t0, db(-6))
    zt = timeline(0.18)
    # the needle leaving: a breathy band that sinks only a little (a narrow,
    # steeply falling band would read as a laser "pew")
    zip_ = shaped_noise(0.18, lambda tt, f: band(f, 3800.0 * (3000.0 / 3800.0) ** np.clip(tt / 0.15, 0.0, 1.0), 0.12),
                        r, nfft=512) * ad(zt, 0.004, 0.04)
    place(out, norm(tail(zip_)), t0, db(-12))
    place(out, burst(r, 0.08, 0.001, 0.015, lo=150.0, hi=600.0), t0, db(-10))
    return out


def needle_hit(seed=806, length=0.1):
    """A small glass tick."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    place(out, click(r, 0.0006, 2500.0), t0, db(-2))
    place(out, norm(modes(0.09, [5900.0, 8350.0, 10400.0], (1.0, 0.5, 0.3), (0.022, 0.014, 0.009))), t0)
    place(out, burst(r, 0.03, 0.0003, 0.004, lo=1500.0, hi=3500.0), t0, db(-8))
    return out


def shard_tick(seed=807, length=0.15):
    """A small ticking chime (E7), clear enough to count."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    place(out, norm(chime(note("E7"), length, ratios=BAR, amps=(1.0, 0.15), taus=(0.045, 0.02),
                          beats=(0.0, 0.0), attack=0.0005)), t0)
    place(out, click(r, 0.0005, 3000.0), t0, db(-8))
    return out


def shard_burst(seed=808, length=0.35):
    """A small pop, then a tinkle of fragments."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    place(out, thump(0.15, 240.0, 110.0, 0.01, 0.022, drive=1.4), t0, db(-4))
    place(out, burst(r, 0.1, 0.0005, 0.008, lo=400.0, hi=2500.0), t0, db(-3))
    place(out, click(r, 0.001, 1000.0), t0, db(-6))
    tinkle = silence(length)
    for _ in range(14):
        when = 0.01 + r.exponential(0.08)
        if when > 0.29:
            continue
        f0 = np.exp(r.uniform(np.log(3000.0), np.log(9500.0)))
        place(tinkle, glass_grain(r, f0, tau=r.uniform(0.015, 0.05)), when,
              np.exp(-when / 0.15) * r.uniform(0.4, 1.0))
    place(out, norm(tinkle), t0, db(-3))
    return reverb(out, r, wet=0.1, t60=0.4, damp_hz=8000.0, hp_hz=500.0)


EVENTS = [
    Event("shardling/step", "Shardling steps",
          [partial(step, 701, 6200.0, 2100.0, 0.019),
           partial(step, 702, 7100.0, 2500.0, 0.0),
           partial(step, 703, 5400.0, 1800.0, 0.023)],
          length=0.08, level=-12.0, fade_out=0.015),
    Event("shardling/tell", "Shardling readies a lunge", [tell], length=0.6, level=-1.0, fade_out=0.05),
    Event("shardling/lunge", "Shardling lunges", [lunge], length=0.25, level=-4.0, fade_out=0.03),
    Event("shardling/hurt", "Shardling hurts",
          [partial(hurt, 901, 2300.0, (1.0, 1.58, 2.46, 3.3), 950.0),
           partial(hurt, 902, 2750.0, (1.0, 1.43, 2.19, 3.05), 1150.0)],
          length=0.2, level=-1.0, fade_out=0.04),
    Event("shardling/death", "Shardling shatters", [death], length=0.7, level=1.0, fade_out=0.18),
    Event("shardling/spit", "Shardling spits", [spit], length=0.2, level=-4.0, fade_out=0.03),
    Event("shardling/needle_hit", "Needle hits", [needle_hit], length=0.1, level=-8.0, fade_out=0.02),
    Event("shardling/shard_tick", "Shards tick", [shard_tick], length=0.15, level=-6.0, fade_out=0.04),
    Event("shardling/shard_burst", "Shards burst", [shard_burst], length=0.35, level=-3.0,
          fade_out=0.08),
]
