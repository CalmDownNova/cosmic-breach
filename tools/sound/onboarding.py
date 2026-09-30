"""The way in's sounds (W4, GDD 1.3).

    onboarding/starfall_streak  3 s across the sky: rushing air brightening and swelling as the star comes,
                                a high glassy whistle falling a little, sparkles of D pentatonic glass
                                shed along the way, a low rumble arriving at the end
    onboarding/starfall_boom    the landing: a recorded distant meteor impact (eleven/onboarding/meteor_boom:
                                its deep boom and rumbling earth) under a synthesized crack, debris and punch,
                                and then the star's own ring, a D major chord of crystal ringing out over it
    onboarding/shard_hum        the shard where it lies: a soft glassy D and A with slow beats and a
                                faint shimmer, 4 s (the block plays it every 4 s)
    onboarding/ring_activate    a ring opening: a thump, air sucked upward, then a rising arpeggio into a
                                bright D major chord of bells
    onboarding/breach_wind      wind rising out of an open Breach: a recorded hollow, howling draught
                                (eleven/onboarding/breach_wind), a seamless 7.75 s loop the client keeps
                                playing while the player is near an open Breach
    onboarding/fall_up          falling up: a rush sweeping upward in pitch and a chime flourish at its top

Tuned parts use D major pentatonic (D E F# A B), like the rest of the set. The recorded takes come from
tools/sound/eleven/onboarding/ (see eleven.py); nothing here calls the network.

Run:  python tools/sound/build.py onboarding
"""
from __future__ import annotations

import numpy as np

import eleven
from dsp import (ad, band, circular_filter, db, fade, hp, hp_shape, lp_shape, n_of, norm, phase_of, place, rng,
                 shaped_noise, silence, smoothstep, softclip, timeline, to_db, wobble)
from event import Event
from layers import burst, chime, click, crackle, reverb, tail, thump, whoosh

D5, FS5, A5, B5, D6, E6, FS6, A6, B6, D7 = 587.33, 739.99, 880.0, 987.77, 1174.66, 1318.51, 1479.98, 1760.0, 1975.53, 2349.32


def starfall_streak(seed=801, length=3.1):
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 3.0, 0.0, 1.0)
    v = 0.12 + 0.88 * u ** 1.6
    rush = whoosh(r, v, 380.0, 3200.0, width=0.9, whistle=0.8, whistle_width=0.05, air=0.6, air_hz=5500.0,
                  body=0.3, body_hz=160.0, flutter=0.12, flutter_hz=9.0)
    # the glassy whistle of the star itself, falling from B6 towards F#6
    f = B6 * (FS6 / B6) ** u
    whistle = np.sin(phase_of(f * (1.0 + 0.003 * wobble(r, len(t), 6.0)))) * (0.25 + 0.75 * u ** 1.2)
    out = norm(rush) + db(-12) * whistle
    # sparkles shed along the way
    notes = (D6, E6, FS6, A6, B6, D7)
    for k in range(18):
        when = 0.2 + 2.7 * (k / 18.0) + r.uniform(-0.05, 0.05)
        c = chime(notes[r.integers(len(notes))], 0.35)
        place(out, tail(c), when, db(-22 + 8 * min(1.0, when / 3.0)))
    rumble = shaped_noise(length, lambda tt, fq: lp_shape(fq, 140.0, 3) * hp_shape(fq, 25.0, 2), r)
    out += db(-6) * norm(rumble) * smoothstep((t - 1.6) / 1.4) ** 2
    out = out * (1.0 - smoothstep((t - 3.02) / 0.08))
    return reverb(fade(softclip(norm(out), 1.2), 0.25, 0.01), r, wet=0.22, t60=1.4, damp_hz=6000.0, hp_hz=150.0)


def starfall_boom(seed=811, length=4.0):
    """The recorded impact carries the weight: a deep, distant boom and the ground rumbling for three seconds
    (nearly all of it under 200 Hz, so it takes the place of the old synthesized sub and rumble). Over it the
    synthesis keeps what the recording lacks: the punch of the first instant, the crack and the debris, and the
    star's D major crystal chord. The recording joins after the synthesis's soft clipper (clipping its rumble
    would smear harmonics over the chord)."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.002
    place(out, thump(1.4, 110.0, 24.0, 0.08, 0.3, attack=0.002, drive=2.4), t0)
    place(out, burst(r, 0.3, 0.0004, 0.045, lo=800.0, hi=7500.0), t0, db(-2))
    place(out, click(r, 0.002, 500.0), t0, db(-3))
    place(out, crackle(r, 26, 0.1, 700.0, 5500.0, grain=0.0012, falloff=0.55), t0 + 0.006, db(-5))
    place(out, crackle(r, 16, 0.9, 1400.0, 6000.0, grain=0.0009, falloff=0.9), t0 + 0.15, db(-14))
    # the star rings out as the dust settles: D, F#, A and D over it
    for i, (hz, g) in enumerate(((D5, 0.0), (FS5, -2.0), (A5, -3.0), (D6, -5.0))):
        c = chime(hz, 2.6, taus=(0.9, 0.5, 0.25, 0.1), beats=(1.5, 2.5, 0.0, 0.0))
        place(out, tail(c, longest=0.3), 0.25 + 0.06 * i, db(-12 + g))
    out = softclip(norm(out), 1.6)
    impact = hp(eleven.sample("onboarding/meteor_boom"), 28.0, 2)      # nothing useful below 28 Hz, only headroom
    place(out, norm(fade(impact[:n_of(length - t0)], 0.003, 0.4)), t0, db(BOOM_TAKE_DB))
    return reverb(out, r, wet=0.3, t60=2.2, damp_hz=4500.0, hp_hz=120.0)


def shard_hum(seed=821, length=4.0):
    r = rng(seed)
    t = timeline(length)
    hum = np.zeros_like(t)
    for hz, g in ((293.66, 1.0), (440.0, 0.6), (587.33, 0.3), (880.0, 0.12)):
        hum += g * (np.sin(phase_of(np.full(len(t), hz))) + 0.7 * np.sin(phase_of(np.full(len(t), hz * 1.0025))))
    breath = 0.75 + 0.25 * np.sin(2 * np.pi * t / length * 2.0 - np.pi / 2)
    shimmer = shaped_noise(length, lambda tt, f: band(f, 6200.0, 0.6), r)
    out = norm(hum) * breath + db(-24) * norm(shimmer) * (0.6 + 0.4 * wobble(r, len(t), 3.0))
    return fade(norm(out), 0.6, 0.8)


def ring_activate(seed=831, length=2.6):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.6, 140.0, 45.0, 0.05, 0.18, drive=1.4), 0.0, db(-3))
    st = timeline(0.5)
    suck = shaped_noise(0.5, lambda tt, f: band(f, 500.0 * (10.0 ** np.clip(tt / 0.5, 0, 1)), 0.9), r)
    place(out, tail(norm(suck) * (st / 0.5) ** 2.0, longest=0.01), 0.02, db(-8))
    for i, hz in enumerate((D5, FS5, A5, D6, FS6)):
        place(out, tail(chime(hz, 1.2, taus=(0.5, 0.25, 0.12, 0.05))), 0.45 + 0.07 * i, db(-8 - i))
    for hz, g in ((D6, 0.0), (FS6, -2.0), (A6, -3.0), (D7, -6.0)):
        c = chime(hz, 2.0, taus=(0.8, 0.4, 0.2, 0.08), beats=(1.0, 2.0, 0.0, 0.0))
        place(out, tail(c, longest=0.3), 0.85, db(-6 + g))
    out = softclip(norm(out), 1.2)
    return reverb(out, r, wet=0.3, t60=1.8, damp_hz=7000.0, hp_hz=150.0)


BOOM_TAKE_DB = 2.0      # the recorded impact's peak against the synthesis's
WIND_CROSSFADE = 0.25


def breach_wind():
    """The recorded draught as a seamless loop. The take was asked for as a loop but its wrap still clicked
    faintly (energy above 8 kHz at the seam 168 times the loop's median there), so its last 0.25 s are
    crossfaded (equal power) into its first 0.25 s and the loop is 7.75 s. Then, all circular so the seam stays
    exact: rumble under 50 Hz off, and the gusts evened out (the 400 ms level brought 70% of the way to its
    median, at most 10 dB), so one gust doesn't stand out every 8 s."""
    x = eleven.sample("onboarding/breach_wind")
    n, k = len(x), n_of(WIND_CROSSFADE)
    th = np.pi * np.arange(k) / (2 * k)
    loop = x[:n - k].copy()
    loop[:k] = x[:k] * np.sin(th) + x[n - k:] * np.cos(th)
    loop = circular_filter(loop - loop.mean(), lambda f: hp_shape(f, 50.0, 2))
    env = np.sqrt(np.maximum(_circular_mean(loop * loop, n_of(0.4)), 1e-12))
    gain = _circular_mean(np.clip(-0.7 * to_db(env / np.median(env)), -10.0, 10.0), n_of(0.6))
    return loop * 10.0 ** (gain / 20.0)


def _circular_mean(v, w):
    """Centred moving average around a loop."""
    kernel = np.zeros(len(v))
    kernel[:w] = 1.0 / w
    return np.roll(np.real(np.fft.ifft(np.fft.fft(v) * np.fft.fft(kernel))), -(w // 2))


def fall_up(seed=851, length=1.8):
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / 1.3, 0.0, 1.0)
    v = 0.1 + 0.9 * smoothstep(u)
    rush = whoosh(r, v, 250.0, 5200.0, width=1.0, whistle=0.5, air=0.8, air_hz=6000.0, body=0.5, body_hz=120.0)
    out = norm(rush) * (1.0 - smoothstep((t - 1.35) / 0.4))
    for i, hz in enumerate((A5, D6, FS6, A6, D7)):
        place(out, tail(chime(hz, 0.6, taus=(0.3, 0.15, 0.08, 0.03))), 0.95 + 0.06 * i, db(-10 - i))
    return reverb(fade(softclip(norm(out), 1.2), 0.05, 0.1), r, wet=0.25, t60=1.2, damp_hz=7000.0, hp_hz=150.0)


EVENTS = [
    Event("onboarding/starfall_streak", "A star streaks across the sky", [starfall_streak], length=3.2, level=-1.0, fade_out=0.05),
    Event("onboarding/starfall_boom", "A Starfall lands", [starfall_boom], length=4.0, level=2.0, fade_out=0.4),
    Event("onboarding/shard_hum", "Starfall Shard hums", [shard_hum], length=4.0, level=-12.0, fade_out=0.6),
    Event("onboarding/ring_activate", "Breach Ring opens", [ring_activate], length=2.6, level=-1.0, fade_out=0.3),
    Event("onboarding/breach_wind", "Wind rises from the Breach", [breach_wind], length=8.0 - WIND_CROSSFADE, level=-6.0,
          loop=True),
    Event("onboarding/fall_up", "Falling up", [fall_up], length=1.8, level=-2.0, fade_out=0.1),
]
