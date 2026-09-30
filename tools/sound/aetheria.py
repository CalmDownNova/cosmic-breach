"""Aetheria's ambience and music (task W3a).

Beds (seamless loops the biomes play): the Reach, high wind and crystal chimes; the Drift, a low drone
and a distant whale song; the Deep, sub-bass and whispers. Every loop is built to wrap: noise is
cross-faded round the seam, events are placed with wrap-around, tones have whole cycles per loop,
filters and reverb are circular.

Music (the biomes' music entries, at Vesper's 100 BPM, pentatonic, calm): the Reach in D major
pentatonic (bells, harp, pad), the Drift in A minor pentatonic (marimba, flute, pad, a far glide),
the Deep in D minor pentatonic (low bells, a distant choir, a slow heartbeat). Each is 28 bars
(67 s). The Arrival cue plays once per player on first entering Aetheria: a shimmer, a harp
glissando and the Reach's opening phrase over a swelling chord.
"""
from __future__ import annotations

import numpy as np

from dsp import (SR, TWO_PI, band, db, fade, hp_shape, lp_shape, n_of, norm, note, phase_of, place, place_wrap, rng,
                 shaped_noise, silence, timeline)
from event import Event
from layers import AH_DISTANT, BAR, GLASS, chime, reverb, reverb_wrap, sung, vowel_gain

BPM = 100
BEAT = 60.0 / BPM          # 0.6 s: Vesper's beat
BAR_S = 4 * BEAT           # 2.4 s
BARS = 28


# ------------------------------------------------------------------------------------------ instruments

def env(t, seconds, attack, release):
    """Smooth attack from 0 and release to exactly 0 at `seconds`."""
    a = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    rel = np.clip((seconds - t) / max(release, 1e-4), 0.0, 1.0)
    return (a * a * (3 - 2 * a)) * (rel * rel * (3 - 2 * rel))


def pad(freqs, seconds, attack=1.2, release=1.6, bright=5.0, voices=3, detune=7.0, seed=1):
    """A soft string-synth pad: detuned, gently filtered saws, slow in and out."""
    r = rng(seed)
    t = timeline(seconds)
    out = np.zeros_like(t)
    for f in freqs:
        for v in range(voices):
            cents = detune * (v - (voices - 1) / 2.0) + r.normal(0, 1.5)
            fv = f * 2 ** (cents / 1200.0)
            ph = r.uniform(0, TWO_PI)
            k_max = int(min(14, 9000.0 / fv))
            for k in range(1, k_max + 1):
                out += np.sin(TWO_PI * k * fv * t + ph * k) * (1.0 / k) * np.exp(-(k - 1) / bright)
    trem = 1.0 + 0.05 * np.sin(TWO_PI * 0.23 * t + r.uniform(0, TWO_PI))
    return out * env(t, seconds, attack, release) * trem / max(1, len(freqs) * voices)


def pluck(f, seconds=2.2, bright=1.0, tau=1.1, seed=2):
    """A harp-like pluck: harmonics that die faster the higher they are."""
    r = rng(seed)
    t = timeline(seconds)
    out = np.zeros_like(t)
    for k in range(1, 13):
        fk = k * f * (1 + 0.0004 * k * k)
        if fk > 0.45 * SR:
            break
        amp = (1.0 / k ** 1.5) * np.exp(-(k - 1) * 0.12 / bright)
        out += amp * np.exp(-t * (1 + 0.7 * (k - 1)) / tau) * np.sin(TWO_PI * fk * t + r.uniform(0, 0.3))
    return out * env(t, seconds, 0.003, 0.08)


def bell(f, seconds=3.0, ratios=GLASS, taus=(1.4, 0.55, 0.22, 0.09), amps=(1.0, 0.28, 0.09, 0.03), attack=0.002):
    return chime(f, seconds, ratios=ratios, amps=amps, taus=taus, beats=(1.1, 1.9, 0.0, 0.0), attack=attack)


def marimba(f, seconds=1.6):
    return chime(f, seconds, ratios=BAR, amps=(1.0, 0.18, 0.05, 0.01), taus=(0.55, 0.12, 0.05, 0.02),
                 beats=(0.0, 0.0, 0.0, 0.0), attack=0.004)


def flute(f, seconds, seed=3):
    """A breathy wooden flute: soft attack, vibrato that grows in."""
    r = rng(seed)
    t = timeline(seconds)
    vib = 1 + 0.004 * np.sin(TWO_PI * 5.1 * t + r.uniform(0, TWO_PI)) * np.clip((t - 0.25) / 0.4, 0, 1)
    ph = phase_of(f * vib)
    tone = np.sin(ph) + 0.22 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
    breath = shaped_noise(seconds, lambda tt, ff: band(ff, f * 2.0, 0.7), r) * 0.05
    return (tone + breath) * env(t, seconds, 0.09, min(0.35, seconds * 0.4))


def bass(f, seconds, seed=4):
    t = timeline(seconds)
    return (np.sin(TWO_PI * f * t) + 0.25 * np.sin(TWO_PI * 2 * f * t)) * np.exp(-t / 3.5) * env(t, seconds, 0.04, 0.5)


def sub(f, seconds, attack=0.02, tau=0.35):
    t = timeline(seconds)
    ph = phase_of(f * (1 + 0.5 * np.exp(-t / 0.04)))
    return np.sin(ph) * np.exp(-t / tau) * env(t, seconds, attack, 0.05)


def choir(freqs, seconds, seed=5):
    """A distant "ooh" choir: formant singers, slow in and out."""
    r = rng(seed)
    t = timeline(seconds)
    out = np.zeros_like(t)
    for f in freqs:
        for s in range(2):
            vib = 1 + 0.003 * np.sin(TWO_PI * r.uniform(4.6, 5.4) * t + r.uniform(0, TWO_PI))
            out += sung(f * 2 ** (r.normal(0, 6) / 1200.0) * vib, AH_DISTANT, r, rolloff=1.3, f_max=5000.0)
    return norm(out) * env(t, seconds, 1.4, 1.8)


# ------------------------------------------------------------------------------------------ scores

def render(score, seconds, seed):
    """score: (start in beats, length in beats, voice function of seconds, gain dB)."""
    out = silence(seconds)
    for start, length, voice, gain in score:
        place(out, voice(length * BEAT), start * BEAT, db(gain))
    return out


def chord_notes(names):
    return [note(n) for n in names]


def phrase(notes, offset_beats, voice, gain, extra=2.5):
    """(name, start beat, length in beats) list into score events for a struck or held voice."""
    return [(offset_beats + s, max(ln, 1) + extra, (lambda sec, f=note(n): voice(f, sec)), gain) for n, s, ln in notes]


REACH_M1 = [("A5", 0, 1), ("B5", 1, 1), ("D6", 2, 2), ("F#5", 4, 3), ("E5", 7, 1), ("E5", 8, 1), ("F#5", 9, 1),
            ("A5", 10, 2), ("B5", 12, 4), ("D6", 16, 1), ("B5", 17, 1), ("A5", 18, 1), ("F#5", 19, 1),
            ("E5", 20, 2), ("F#5", 22, 1), ("A5", 23, 1), ("B5", 24, 2), ("A5", 26, 1), ("E5", 27, 1), ("D5", 28, 4)]
REACH_M2 = [("F#6", 0, 2), ("E6", 2, 2), ("D6", 4, 4), ("B5", 8, 2), ("D6", 10, 2), ("E6", 12, 4),
            ("F#6", 16, 1), ("E6", 17, 1), ("D6", 18, 2), ("B5", 20, 2), ("A5", 22, 2), ("B5", 24, 1),
            ("D6", 25, 1), ("E6", 26, 2), ("D6", 28, 4)]


def reach_track():
    seconds = BARS * BAR_S + 4.0
    cycle = [(["D3", "A3", "E4", "F#4"], "D2", ["D4", "A4", "E5", "F#5", "A5", "F#5", "E5", "A4"]),
             (["B2", "F#3", "D4", "A4"], "B1", ["B3", "F#4", "A4", "D5", "F#5", "D5", "A4", "F#4"]),
             (["E3", "B3", "E4", "A4"], "E2", ["E4", "B4", "E5", "A5", "B5", "A5", "E5", "B4"]),
             (["A2", "E3", "B3", "E4"], "A1", ["A3", "E4", "B4", "E5", "A5", "E5", "B4", "E4"])]
    score = []
    for bar in range(BARS):
        chord, root, arp = cycle[bar % 4]
        b0 = bar * 4
        last = bar == BARS - 1
        score.append((b0, 4.6 if not last else 8, (lambda sec, c=chord, s=bar: pad(chord_notes(c), sec, seed=10 + s % 4)), -9))
        score.append((b0, 4.0, (lambda sec, f=note(root): bass(f, sec)), -12))
        if 10 <= bar < 26:
            for i, n in enumerate(arp):
                score.append((b0 + i * 0.5, 3.5, (lambda sec, f=note(n): pluck(f, sec)), -15 if i % 2 else -13))
    score += phrase(REACH_M1, 2 * 4, bell, -8)
    score += phrase(REACH_M2, 10 * 4, bell, -9)
    score += phrase(REACH_M1, 18 * 4, bell, -8)
    score += [(18 * 4 + s, ln, (lambda sec, f=note(n) / 2: flute(f, sec)), -17) for n, s, ln in REACH_M1]
    score += [(26 * 4, 6, (lambda sec: bell(note("D6"), sec)), -8), (26 * 4 + 0.02, 6, (lambda sec: bell(note("A5"), sec)), -12),
              (26 * 4 + 0.04, 6, (lambda sec: bell(note("F#5"), sec)), -13)]
    x = render(score, seconds, 1)
    return reverb(x, rng(21), wet=0.32, t60=2.6, damp_hz=7000.0, hp_hz=180.0)[:n_of(seconds)]


DRIFT_M = [("E5", 0, 2), ("D5", 2, 2), ("C5", 4, 4), ("D5", 8, 2), ("E5", 10, 2), ("G5", 12, 4), ("A5", 16, 2),
           ("G5", 18, 1), ("E5", 19, 1), ("D5", 20, 4), ("C5", 24, 2), ("D5", 26, 2), ("A4", 28, 4)]


def glide(seconds, f0, f1, f2, seed=6):
    """A far glide rising then falling, sung through an "oo": the Drift's whale, once."""
    r = rng(seed)
    t = timeline(seconds)
    u = t / seconds
    f = f0 * (f1 / f0) ** np.sin(np.clip(u * 1.6, 0, 1) * np.pi / 2) * (f2 / f1) ** np.clip((u - 0.6) / 0.4, 0, 1)
    oo = ((350.0, 90.0, 0.0), (800.0, 120.0, -8.0), (2600.0, 200.0, -25.0))
    return sung(f, oo, r, rolloff=1.2, f_max=4000.0) * env(t, seconds, 0.5, 0.9)


def drift_track():
    seconds = BARS * BAR_S + 4.0
    cycle = [(["A2", "E3", "C4", "G4"], "A1", ["A3", "E4", "G4", "C5", "E5", "C5", "G4", "E4"]),
             (["C3", "G3", "D4", "E4"], "C2", ["C4", "G4", "D5", "E5", "G5", "E5", "D5", "G4"]),
             (["D3", "A3", "D4", "G4"], "D2", ["D4", "A4", "D5", "G5", "A5", "G5", "D5", "A4"]),
             (["G2", "D3", "A3", "D4"], "G1", ["G3", "D4", "A4", "D5", "G5", "D5", "A4", "D4"])]
    score = []
    for bar in range(BARS):
        chord, root, arp = cycle[bar % 4]
        b0 = bar * 4
        score.append((b0, 4.8 if bar < BARS - 1 else 8, (lambda sec, c=chord, s=bar: pad(chord_notes(c), sec, bright=3.5, seed=30 + s % 4)), -9))
        score.append((b0, 4.0, (lambda sec, f=note(root): bass(f, sec)), -11))
        if 2 <= bar < 26:
            for i, n in enumerate(arp):
                if i % 2 == 0 or bar >= 10:
                    score.append((b0 + i * 0.5, 2.0, (lambda sec, f=note(n): marimba(f, sec)), -14 if i % 2 else -12))
    score += [(4 * 4 + s, ln + 0.4, (lambda sec, f=note(n): flute(f, sec)), -10) for n, s, ln in DRIFT_M]
    score += [(20 * 4 + s, ln + 0.4, (lambda sec, f=note(n): flute(f, sec)), -10) for n, s, ln in DRIFT_M]
    score += [(12 * 4, 12, (lambda sec: glide(sec, 150.0, 330.0, 170.0, seed=7)), -12),
              (16 * 4 + 2, 10, (lambda sec: glide(sec, 190.0, 290.0, 140.0, seed=8)), -14)]
    x = render(score, seconds, 2)
    return reverb(x, rng(22), wet=0.4, t60=3.2, damp_hz=5500.0, hp_hz=150.0)[:n_of(seconds)]


def deep_track():
    seconds = BARS * BAR_S + 4.0
    cycle = [(["D2", "A2", "F3", "C4"], "D1"), (["C2", "G2", "D3", "G3"], "C1"),
             (["F2", "C3", "G3", "A3"], "F1"), (["G1", "D2", "G2", "C3"], "G1")]
    score = []
    for bar in range(0, BARS, 2):
        chord, root = cycle[(bar // 2) % 4]
        b0 = bar * 4
        score.append((b0, 9.0 if bar < BARS - 2 else 12, (lambda sec, c=chord, s=bar: pad(chord_notes(c), sec, bright=2.2, attack=2.0, release=2.4, seed=50 + s % 8)), -8))
        score.append((b0, 8.0, (lambda sec, f=note(root) * 2: bass(f, sec)), -13))
    for beat in range(0, BARS * 4, 2):
        if 2 * 4 <= beat < 26 * 4:
            score.append((beat, 1.0, (lambda sec: sub(46.0, sec)), -12 if beat % 4 == 0 else -16))
    bells = [("A4", 0), ("F4", 8), ("G4", 16), ("C5", 18), ("D4", 24), ("A4", 32), ("C5", 40), ("G4", 42), ("F4", 48), ("D4", 56)]
    for rep in (8, 72):
        score += [(rep + s, 7, (lambda sec, f=note(n): bell(f, sec, taus=(2.2, 0.8, 0.3, 0.1))), -10) for n, s in bells if rep + s < 27 * 4]
    score += [(10 * 4, 8 * 4, (lambda sec: choir(chord_notes(["D3", "A3", "F4"]), sec, seed=9)), -14),
              (18 * 4, 6 * 4, (lambda sec: choir(chord_notes(["C3", "G3", "D4"]), sec, seed=10)), -15)]
    x = render(score, seconds, 3)
    return reverb(x, rng(23), wet=0.5, t60=4.0, damp_hz=3500.0, hp_hz=60.0)[:n_of(seconds)]


def arrival_cue():
    seconds = 22.0
    out = silence(seconds)
    shimmer = ["D5", "E5", "F#5", "A5", "B5", "D6", "E6", "F#6", "A6", "B6", "D7"]
    for i, n in enumerate(shimmer):
        place(out, bell(note(n), 4.0, taus=(1.8, 0.6, 0.2, 0.08)), 0.1 + i * 0.075, db(-14 + i * 0.4))
    place(out, pad(chord_notes(["D3", "A3", "E4", "F#4", "A4"]), 17.0, attack=2.6, release=4.5, seed=70), 0.3, db(-6))
    place(out, bass(note("D2"), 14.0), 1.0, db(-11))
    gliss = ["D4", "E4", "F#4", "A4", "B4", "D5", "E5", "F#5", "A5", "B5", "D6"]
    for i, n in enumerate(gliss):
        place(out, pluck(note(n), 3.0), BAR_S + i * 0.055, db(-13))
    for n, s, ln in REACH_M1[:10]:
        place(out, bell(note(n), 4.0), 2 * BAR_S + s * BEAT, db(-7))
    for n in ("D6", "A5", "F#5", "D5"):
        place(out, bell(note(n), 6.5, taus=(2.6, 0.8, 0.25, 0.1)), 6 * BAR_S, db(-8 if n == "D6" else -12))
    out = reverb(out, rng(24), wet=0.42, t60=3.4, damp_hz=7500.0, hp_hz=160.0)[:n_of(seconds)]
    return fade(out, 0.002, 3.0)


# ------------------------------------------------------------------------------------------ beds (loops)

def loop_noise(seconds, mag, r, xfade=1.5):
    """shaped_noise that loops: the extra second is cross-faded (equal power) over the start."""
    n = n_of(seconds)
    x = n_of(xfade)
    raw = shaped_noise(seconds + xfade, mag, r)
    head, tail_ = raw[:x], raw[n:n + x]
    w = np.linspace(0.0, np.pi / 2, x)
    out = raw[:n].copy()
    out[:x] = head * np.sin(w) + tail_ * np.cos(w)
    return out


def periodic(seconds, cycles, phase=0.0):
    """A sine with a whole number of cycles in the loop, 0..1."""
    t = timeline(seconds)
    return 0.5 + 0.5 * np.sin(TWO_PI * cycles * t / seconds + phase)


def whole(f, seconds):
    """f nudged to a whole number of cycles per loop."""
    return round(f * seconds) / seconds


def reach_bed():
    seconds = 24.0
    r = rng(401)
    t = timeline(seconds)
    gust = 0.45 + 0.55 * periodic(seconds, 2, 0.4) * (0.6 + 0.4 * periodic(seconds, 5, 1.9))
    whistle_f = lambda tt: 1900.0 * 2 ** (0.35 * np.sin(TWO_PI * 3 * tt / seconds) + 0.15 * np.sin(TWO_PI * 7 * tt / seconds))
    wind = loop_noise(seconds, lambda tt, f: band(f, 900.0, 1.2) * hp_shape(f, 250.0, 2), r)
    whistle = loop_noise(seconds, lambda tt, f: band(f, whistle_f(tt), 0.12), r)
    bed = norm(wind) * gust * 0.8 + norm(whistle) * (0.25 + 0.2 * periodic(seconds, 3, 2.2)) * gust
    chimes = silence(seconds)
    names = ["D6", "E6", "F#6", "A6", "B6", "D7", "E7"]
    when = 0.4
    while when < seconds - 0.2:
        cluster = 1 + int(r.integers(0, 3))
        for c in range(cluster):
            n = names[int(r.integers(0, len(names)))]
            place_wrap(chimes, bell(note(n), 4.0, taus=(1.6, 0.5, 0.18, 0.07)), when + c * r.uniform(0.09, 0.16),
                       db(r.uniform(-4, 0)))
        when += r.uniform(2.2, 4.6)
    out = bed * 0.55 + norm(chimes) * 0.5
    return reverb_wrap(out, r, wet=0.35, t60=2.2, damp_hz=8000.0, hp_hz=300.0)


def drift_bed():
    seconds = 32.0
    r = rng(402)
    t = timeline(seconds)
    drone = np.zeros_like(t)
    for f, a, beat in ((73.42, 1.0, 3), (110.0, 0.6, 5), (146.83, 0.45, 4), (220.0, 0.18, 7)):
        f1 = whole(f, seconds)
        f2 = f1 + beat / seconds
        drone += a * (np.sin(TWO_PI * f1 * t) + 0.7 * np.sin(TWO_PI * f2 * t + 1.3))
    drone *= 0.75 + 0.25 * periodic(seconds, 2, 0.8)
    air = loop_noise(seconds, lambda tt, f: lp_shape(f, 500.0, 2) * hp_shape(f, 60.0, 2), r)
    calls = silence(seconds)
    for start, (f0, f1, f2), length in ((3.0, (160.0, 300.0, 150.0), 5.5), (17.5, (200.0, 340.0, 170.0), 6.5)):
        place_wrap(calls, glide(length, f0, f1, f2, seed=int(f0)), start)
    calls = np.fft.irfft(np.fft.rfft(calls) * lp_shape(np.fft.rfftfreq(len(calls), 1 / SR), 1400.0, 2), n=len(calls))
    calls = reverb_wrap(norm(calls), r, wet=0.8, t60=4.5, damp_hz=2500.0, hp_hz=90.0)
    out = norm(drone) * 0.6 + norm(air) * 0.25 + norm(calls) * 0.35
    return reverb_wrap(out, r, wet=0.25, t60=2.8, damp_hz=4000.0, hp_hz=40.0)


def deep_bed():
    seconds = 28.0
    r = rng(403)
    t = timeline(seconds)
    s1, s2 = whole(36.0, seconds), whole(43.0, seconds)
    subbass = (np.sin(TWO_PI * s1 * t) * (0.4 + 0.6 * periodic(seconds, 3, 0.2))
               + 0.7 * np.sin(TWO_PI * s2 * t + 0.7) * (0.4 + 0.6 * periodic(seconds, 2, 2.4)))
    rumble = loop_noise(seconds, lambda tt, f: lp_shape(f, 120.0, 3) * hp_shape(f, 25.0, 2), r)
    whispers = silence(seconds)
    vowels = [((600.0, 120.0, 0.0), (1000.0, 140.0, -4.0), (2500.0, 250.0, -10.0)),
              ((300.0, 90.0, 0.0), (2300.0, 200.0, -6.0), (3000.0, 250.0, -9.0)),
              ((450.0, 100.0, 0.0), (800.0, 130.0, -3.0), (2600.0, 250.0, -12.0))]
    when = 1.0
    while when < seconds - 0.5:
        length = r.uniform(0.9, 1.7)
        formants = vowels[int(r.integers(0, len(vowels)))]
        hiss = shaped_noise(length, lambda tt, f: vowel_gain(f, formants) * hp_shape(f, 900.0, 2) * lp_shape(f, 6500.0, 2), r)
        tt = timeline(length)
        syllables = np.clip(np.sin(TWO_PI * r.uniform(3.5, 5.5) * tt + r.uniform(0, 1)), 0, 1) ** 1.5
        place_wrap(whispers, norm(hiss) * syllables * np.sin(np.pi * tt / length), when, db(r.uniform(-5, 0)))
        when += r.uniform(3.5, 6.5)
    whispers = reverb_wrap(norm(whispers), r, wet=0.7, t60=3.0, damp_hz=5000.0, hp_hz=500.0)
    return norm(subbass) * 0.7 + norm(rumble) * 0.3 + norm(whispers) * 0.28


EVENTS = [
    Event("ambient/reach", "Wind sings through crystal", [reach_bed], length=24.0, level=-10.0, loop=True, quality=3),
    Event("ambient/drift", "A drone hums, something vast sings far off", [drift_bed], length=32.0, level=-10.0, loop=True, quality=5),
    Event("ambient/deep", "Whispers in the deep", [deep_bed], length=28.0, level=-11.0, loop=True, quality=3),
    Event("music/reach", "Music: the Upper Reach", [reach_track], length=72.0, level=-3.0, fade_out=2.5, quality=1),
    Event("music/drift", "Music: the Drift", [drift_track], length=72.0, level=-3.5, fade_out=2.5, quality=1),
    Event("music/deep", "Music: the Deep", [deep_track], length=72.0, level=-4.0, fade_out=3.0, quality=1),
    Event("music/arrival", "Music: Arrival", [arrival_cue], length=22.0, level=-2.5, fade_out=3.0, quality=2),
]
