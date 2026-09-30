"""Cosmic weather sounds (W3b, GDD 2.5). All play in the game's Weather volume.

    weather/flare_hum       the Solar Flare's 15 s warning: a hum on D that climbs a fifth to A while
                            its overtones brighten and a high white shimmer grows over it, peaking as
                            the Flare breaks
    weather/meteor_whistle  a meteor's last 2 s: a falling whistle of torn air over a rumble that
                            swells as it comes, ending where the impact starts
    weather/meteor_impact   the impact: a deep boom with a sub layer, a crack, stone and ember debris
                            raining down, a rumble tail
    weather/tide_drop       the Gravity Tide's start: a sucked-in breath, then a sub-bass drop that
                            falls out from under you (120 Hz to 28 Hz) with a soft rumble
    weather/surge_swell     the Eclipse Surge's 20 s warning: a dark cluster of low voices and wind
                            swelling slowly, a reversed rush in its last seconds, then a hard stop
                            into silence as Vesper stops

Tuned parts sit on D (the hum's D to A, the swell's D minor cluster), like the rest of the set.

Run:  python tools/sound/build.py weather
"""
from __future__ import annotations

import numpy as np

from dsp import (SR, TWO_PI, ad, band, db, fade, hp_shape, lp_shape, n_of, norm, phase_of, place, rng,
                 shaped_noise, silence, smoothstep, softclip, timeline, wobble)
from event import Event
from layers import burst, click, crackle, reverb, tail, thump


def flare_hum(seed=701, length=15.4):
    """A hum on D3 climbing a fifth over the warning, overtones opening up, a white shimmer on top."""
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 15.0, 0.0, 1.0)
    f0 = 146.83 * (1.5 ** smoothstep(u))                 # D3 up to A3
    amp = 0.12 + 0.88 * u ** 2.2                          # quiet at first, full as it breaks
    bright = 0.15 + 0.85 * u ** 1.5
    beat = 0.6 + 5.0 * u ** 2                             # the beating speeds up
    hum = np.zeros_like(t)
    for k, a in ((1, 1.0), (2, 0.55), (3, 0.35), (4, 0.22), (5, 0.16), (6, 0.1)):
        g = a * (bright ** (k - 1) if k > 1 else 1.0)
        hum += g * (np.sin(phase_of(f0 * k)) + 0.6 * np.sin(phase_of(f0 * k * 1.003 + beat / k)))
    hum = norm(hum) * amp
    shimmer = shaped_noise(length, lambda tt, f: band(f, 5200.0, 0.7) * hp_shape(f, 2500.0, 2), r)
    shimmer = norm(shimmer) * smoothstep((t - 7.0) / 8.0) ** 1.5 * (0.7 + 0.3 * wobble(r, len(t), 7.0))
    air = shaped_noise(length, lambda tt, f: band(f, 900.0 + 1400.0 * np.clip(tt / 15.0, 0, 1), 1.2), r)
    air = norm(air) * (0.1 + 0.5 * u ** 2)
    out = hum + db(-13) * shimmer + db(-20) * air
    out = out * (1.0 - smoothstep((t - 15.0) / 0.4))      # it breaks: a quick fall away
    out = softclip(norm(out), 1.2)
    return reverb(fade(out, 0.3, 0.05), r, wet=0.25, t60=1.6, damp_hz=5000.0, hp_hz=120.0)


def meteor_whistle(seed=711, length=2.05):
    """Torn air falling in pitch as the meteor comes down, over a rumble that swells to the impact."""
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 2.0, 0.0, 1.0)
    fc = 2600.0 * (0.2 ** u)                               # 2.6 kHz down to about 520 Hz
    tone = np.sin(phase_of(fc)) * (0.35 + 0.65 * u ** 1.5)
    tear = shaped_noise(length, lambda tt, f: band(f, 2600.0 * (0.2 ** np.clip(tt / 2.0, 0, 1)), 0.18), r)
    tear = norm(tear) * (0.3 + 0.7 * u ** 1.4)
    rumble = shaped_noise(length, lambda tt, f: lp_shape(f, 180.0, 3) * hp_shape(f, 30.0, 2), r)
    rumble = norm(rumble) * u ** 2.5
    out = db(-4) * tone + tear + db(-3) * rumble
    out = out * (1.0 - smoothstep((t - 1.97) / 0.06))
    return reverb(fade(norm(out), 0.15, 0.01), r, wet=0.18, t60=1.0, damp_hz=5000.0, hp_hz=200.0)


def meteor_impact(seed, boom_hz, length=1.9):
    """A deep boom with a sub layer, a crack, debris and embers raining down, and a rumble."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.002
    place(out, thump(1.2, boom_hz, 26.0, 0.07, 0.22, attack=0.002, drive=2.0), t0)
    sub = np.sin(phase_of(np.full(n_of(1.1), 34.0))) * ad(timeline(1.1), 0.006, 0.4)
    place(out, tail(sub), t0, db(-2))
    place(out, burst(r, 0.25, 0.0004, 0.035, lo=900.0, hi=7000.0), t0, db(-3))       # the crack
    place(out, click(r, 0.002, 600.0), t0, db(-4))
    place(out, crackle(r, 22, 0.08, 800.0, 5500.0, grain=0.0012, falloff=0.55), t0 + 0.005, db(-4))
    place(out, crackle(r, 14, 0.7, 1500.0, 6000.0, grain=0.0009, falloff=0.9), t0 + 0.12, db(-13))  # debris falling
    rt = timeline(1.5)
    rumble = shaped_noise(1.5, lambda tt, f: lp_shape(f, 120.0, 3) * hp_shape(f, 22.0, 2), r)
    place(out, tail(norm(rumble * ad(rt, 0.02, 0.45)), longest=0.2), t0, db(-6))
    out = softclip(norm(out), 1.6)
    return reverb(out, r, wet=0.25, t60=1.4, damp_hz=4000.0, hp_hz=150.0)


def tide_drop(seed=731, length=3.2):
    """A short in-breath, then the bottom falling out: a saturated sine from 120 Hz down to 28 Hz."""
    r = rng(seed)
    out = silence(length)
    inhale = 0.35
    it = timeline(inhale)
    breath = shaped_noise(inhale, lambda tt, f: band(f, 400.0 * (6.0 ** (tt / inhale)), 1.2), r)
    place(out, tail(norm(breath) * (it / inhale) ** 2.2, longest=0.003), 0.0, db(-6))
    dt = timeline(length - inhale)
    f = 28.0 + 92.0 * np.exp(-dt / 0.55)
    drop = np.sin(phase_of(f)) * ad(dt, 0.01, 1.1)
    drop = np.tanh(2.2 * drop) / np.tanh(2.2)
    place(out, tail(drop, longest=0.2), inhale)
    rumble = shaped_noise(length - inhale, lambda tt, fq: lp_shape(fq, 160.0, 3) * hp_shape(fq, 25.0, 2), r)
    place(out, tail(norm(rumble) * ad(dt, 0.05, 0.9), longest=0.2), inhale, db(-9))
    place(out, thump(0.5, 90.0, 40.0, 0.04, 0.12, drive=1.5), inhale, db(-5))
    return reverb(fade(norm(out), 0.0, 0.2), r, wet=0.2, t60=1.8, damp_hz=3000.0, hp_hz=40.0)


def surge_swell(seed=741, length=20.2):
    """Low voices on a D minor cluster and a dark wind swelling for 20 s, a reversed rush at the end,
    then nothing."""
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 20.0, 0.0, 1.0)
    swell = 0.06 + 0.94 * u ** 2.4
    voices = np.zeros_like(t)
    for hz, g in ((73.42, 1.0), (87.31, 0.7), (110.0, 0.6), (146.83, 0.35), (174.61, 0.25)):
        for detune in (0.997, 1.0, 1.004):
            vib = 1.0 + 0.004 * wobble(r, len(t), 4.0)
            voices += g * np.sin(phase_of(hz * detune * vib))
    # breathy formant noise over the voices (an "oo" darkening to "oh")
    breath = shaped_noise(length, lambda tt, f: band(f, 320.0, 0.35) + 0.6 * band(f, 760.0, 0.3), r)
    wind = shaped_noise(length, lambda tt, f: band(f, 180.0 + 320.0 * np.clip(tt / 20.0, 0, 1), 1.1), r)
    rush_len = 3.0
    rt = np.clip((t - (20.0 - rush_len)) / rush_len, 0.0, 1.0)
    rush = shaped_noise(length, lambda tt, f: band(f, 1500.0 + 3000.0 * np.clip((tt - 17.0) / 3.0, 0, 1), 1.0), r)
    out = norm(voices) * swell + db(-8) * norm(breath) * swell + db(-10) * norm(wind) * (0.3 + 0.7 * u)
    out += db(-6) * norm(rush) * rt ** 3.0
    out = out * (1.0 - smoothstep((t - 19.88) / 0.12))     # the hard stop
    out = softclip(norm(out), 1.1)
    return fade(out, 0.5, 0.0)


EVENTS = [
    Event("weather/flare_hum", "Solenne hums, rising", [flare_hum], length=15.6, level=-4.0, fade_out=0.3, quality=3),
    Event("weather/meteor_whistle", "Meteor whistles down", [meteor_whistle], length=2.4, level=-3.0, fade_out=0.05),
    Event("weather/meteor_impact", "Meteor strikes",
          [lambda: meteor_impact(721, 68.0), lambda: meteor_impact(722, 60.0)], length=1.9, level=3.0, fade_out=0.3),
    Event("weather/tide_drop", "Gravity lets go", [tide_drop], length=3.2, level=0.0, fade_out=0.3),
    Event("weather/surge_swell", "The dark swells into silence", [surge_swell], length=20.3, level=-3.0, fade_out=0.1, quality=3),
]
