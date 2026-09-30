"""The Gyre Knight's sounds (GDD 7.1, "Sound and particles"): a gyroscopic hum that changes pitch with the mode (one
seamless loop; the game sets its pitch), metal scraping, a rising whine as its rings expand, the lances' tells, flights
and hits, the Recall Crash, a blade breaking on a parry, a deflected arrow, its stun, hurt and death.
"""
from __future__ import annotations

from functools import partial

import numpy as np

from dsp import TWO_PI, band, n_of, norm, note, phase_of, place, rng, shaped_noise, silence, timeline
from event import Event
from layers import BAR, blade_ring, chime, click, crackle, knock, reverb, shatter, thump, whoosh

HUM_SECONDS = 2.0


def hum():
    """The gyroscope: a steady tone with a slow beat and a whirr, every partial a whole number of cycles in the loop."""
    t = timeline(HUM_SECONDS)
    x = np.zeros_like(t)
    for f, a, ph in ((110.0, 1.0, 0.7), (111.5, 0.6, 2.9), (220.0, 0.35, 4.4), (330.0, 0.12, 1.3), (55.0, 0.4, 5.6)):
        x += a * np.sin(TWO_PI * f * t + ph)       # phases spread so the loop's seam is no special moment
    whirr = 0.5 + 0.5 * np.sin(TWO_PI * 16.0 * t + 1.1)
    r = rng(3101)
    spec = np.fft.rfft(r.standard_normal(n_of(HUM_SECONDS)))
    freqs = np.fft.rfftfreq(n_of(HUM_SECONDS), 1 / 44100)
    spec *= np.exp(-((np.log2(np.maximum(freqs, 1)) - np.log2(1200.0)) ** 2) / 0.5)
    noise = np.fft.irfft(spec, n_of(HUM_SECONDS))
    x = norm(x) + 0.18 * norm(noise) * whirr
    return x


def whine(seed=3111):
    r = rng(seed)
    t = timeline(1.0)
    ph = phase_of(600.0 * 3 ** np.clip(t / 0.9, 0, 1))
    x = (np.sin(ph) + 0.4 * np.sin(2 * ph)) * np.clip(t / 0.8, 0, 1) * np.clip((1.0 - t) / 0.08, 0, 1)
    out = silence(1.0)
    place(out, norm(x), 0.0, 0.8)
    place(out, norm(shaped_noise(1.0, lambda tt, f: band(f, 2500.0 + 3000 * tt, 0.6), r)) * 0.3, 0.0)
    return reverb(out, r, wet=0.15, t60=0.8)


def sweep(seed=3121):
    r = rng(seed)
    out = silence(0.9)
    for k in range(3):
        v = np.exp(-timeline(0.35) / 0.1)
        place(out, norm(whoosh(r, v, 800.0, 5000.0, width=0.6, whistle=0.8)), 0.05 + 0.18 * k, 0.6)
    place(out, norm(blade_ring(r, np.exp(-timeline(0.6) / 0.2), note("E6"))), 0.0, 0.4)
    return reverb(out, r, wet=0.15, t60=0.8)


def lance_tell(seed=3131):
    r = rng(seed)
    out = silence(0.5)
    place(out, norm(chime(note("B6"), 0.4, ratios=BAR, taus=(0.2, 0.08, 0.04, 0.02))), 0.0, 1.0)
    t = timeline(0.4)
    place(out, norm(np.sin(phase_of(900.0 + 900 * t))) * 0.3, 0.0)
    return reverb(out, r, wet=0.15, t60=0.6)


def lance_fire(seed=3141):
    r = rng(seed)
    out = silence(0.5)
    v = np.exp(-timeline(0.4) / 0.1)
    place(out, norm(whoosh(r, v, 1500.0, 7000.0, width=0.5, whistle=1.2)), 0.0, 1.0)
    return out


def lance_hit(seed=3151):
    r = rng(seed)
    out = silence(0.6)
    place(out, norm(knock(r, 800.0, tau=0.05)), 0.0, 0.8)
    place(out, norm(blade_ring(r, np.exp(-timeline(0.5) / 0.15), note("C#6"))), 0.0, 0.6)
    place(out, norm(thump(0.3, 180, 90, 0.02, 0.08)), 0.0, 0.4)
    return reverb(out, r, wet=0.15, t60=0.6)


def recall_tell(seed=3161):
    """Its blades take aim back at it: a metal scrape rising, reversed."""
    r = rng(seed)
    x = norm(shaped_noise(0.9, lambda tt, f: band(f, 1500.0 + 2500 * tt, 0.5), r))
    t = timeline(0.9)
    x = x * np.clip(t / 0.8, 0, 1) ** 2
    out = silence(0.9)
    place(out, x, 0.0, 0.8)
    place(out, norm(np.sin(phase_of(500.0 * 2 ** np.clip(t / 0.8, 0, 1)))) * 0.4, 0.0)
    return reverb(out, r, wet=0.15, t60=0.7)


def recall(seed=3171):
    r = rng(seed)
    out = silence(0.8)
    v = np.exp(-timeline(0.5) / 0.1)
    place(out, norm(whoosh(r, v, 900.0, 6000.0, width=0.7, whistle=0.6)), 0.0, 0.9)
    for k in range(3):
        place(out, norm(knock(r, 700.0 + 90 * k, tau=0.04)), 0.12 + 0.05 * k, 0.5)
    return reverb(out, r, wet=0.15, t60=0.7)


def blade_break(seed=3181):
    r = rng(seed)
    out = silence(0.8)
    place(out, norm(click(r, 0.002, hp_hz=800.0)), 0.0, 0.8)
    place(out, norm(shatter(r, 0.5, 20, f_lo=2000, f_hi=8000, amp_tau=0.15)), 0.0, 0.6)
    place(out, norm(blade_ring(r, np.exp(-timeline(0.7) / 0.3), note("D6"))), 0.0, 0.7)
    return reverb(out, r, wet=0.2, t60=0.9)


def deflect(seed):
    r = rng(seed)
    out = silence(0.35)
    place(out, norm(knock(r, 1200.0 * r.uniform(0.9, 1.1), tau=0.03)), 0.0, 0.8)
    place(out, norm(blade_ring(r, np.exp(-timeline(0.3) / 0.1), note("A6") * r.uniform(0.95, 1.05))), 0.0, 0.6)
    return out


def stun(seed=3191):
    """It drops: its hum sinks away and the armour clatters."""
    r = rng(seed)
    out = silence(1.2)
    t = timeline(1.0)
    place(out, norm(np.sin(phase_of(220.0 * 0.4 ** np.clip(t, 0, 1))) * np.exp(-t / 0.6)), 0.0, 0.7)
    for k in range(5):
        place(out, norm(knock(r, r.uniform(500, 900), tau=0.05)), 0.2 + 0.1 * k + r.uniform(0, 0.04), 0.5)
    place(out, norm(crackle(r, n_events=10, spread=0.2, hp_hz=800.0)), 0.2, 0.3)
    return reverb(out, r, wet=0.2, t60=0.8)


def hurt(seed, f0):
    r = rng(seed)
    out = silence(0.4)
    place(out, norm(knock(r, f0, tau=0.04)), 0.0, 1.0)
    place(out, norm(blade_ring(r, np.exp(-timeline(0.35) / 0.1), f0 * 3.1)), 0.0, 0.4)
    return out


def death(seed=3201):
    r = rng(seed)
    out = silence(2.0)
    t = timeline(1.6)
    place(out, norm(np.sin(phase_of(300.0 * 0.25 ** np.clip(t / 1.4, 0, 1))) * np.exp(-t / 0.8)), 0.0, 0.6)
    for k in range(8):
        place(out, norm(knock(r, r.uniform(400, 1000), tau=0.06)), 0.1 + 0.12 * k + r.uniform(0, 0.05), 0.5)
    place(out, norm(chime(note("D6"), 1.0, ratios=BAR, taus=(0.4, 0.15, 0.07, 0.03))), 0.0, 0.5)
    place(out, norm(shatter(r, 0.6, 24, f_lo=2500, f_hi=9000, amp_tau=0.2)), 0.0, 0.4)
    return reverb(out, r, wet=0.25, t60=1.2)


EVENTS = [
    Event("gyre/hum", "Gyroscope hums", [hum], length=HUM_SECONDS, level=-8.0, loop=True, quality=6),
    Event("gyre/whine", "Gyre Knight's rings whine", [whine], length=1.0, level=-1.0, fade_out=0.1),
    Event("gyre/sweep", "Gyre blades sweep", [sweep], length=0.9, level=0.0, fade_out=0.2),
    Event("gyre/lance_tell", "Gyre blade takes aim", [lance_tell], length=0.5, level=-2.0, fade_out=0.1),
    Event("gyre/lance_fire", "Gyre blade fires", [lance_fire], length=0.5, level=-2.0, fade_out=0.1),
    Event("gyre/lance_hit", "Gyre blade strikes", [lance_hit], length=0.6, level=-1.0, fade_out=0.15),
    Event("gyre/recall_tell", "Gyre blades take aim", [recall_tell], length=0.9, level=-1.0, fade_out=0.1),
    Event("gyre/recall", "Recall Crash", [recall], length=0.8, level=0.0, fade_out=0.2),
    Event("gyre/blade_break", "Gyre blade breaks", [blade_break], length=0.8, level=1.0, fade_out=0.2),
    Event("gyre/deflect", "Blade deflects", [partial(deflect, 3211), partial(deflect, 3212)], length=0.35, level=-2.0, fade_out=0.08),
    Event("gyre/stun", "Gyre Knight drops", [stun], length=1.2, level=0.0, fade_out=0.3),
    Event("gyre/hurt", "Gyre Knight hurts", [partial(hurt, 3221, 700.0), partial(hurt, 3222, 820.0), partial(hurt, 3223, 610.0)],
          length=0.4, level=-4.0, fade_out=0.08),
    Event("gyre/death", "Gyre Knight falls apart", [death], length=2.0, level=1.0, fade_out=0.4),
]
