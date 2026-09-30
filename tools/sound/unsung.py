"""The Unsung's sounds (Unsung design v1, "Look and sound"): the choir's three lines, which are the boss's music,
and its effects.

The music is three voices in D minor at Vesper's 100 BPM, formant-synthesized (layered singers, each a sum of
harmonics shaped by a vowel, a little detuned, with vibrato that settles in after the onset and a breath of air):
the Alto sings "ah", the Tenor "oh", the Bass "oo". Each line is cut in 8-beat blocks, one per turn of the song and
chord of the progression (D minor, B flat, G minor, A: i VI iv V), so the client can start every living voice's
block together on each line of the Vesper clock: the singer's block sung, the others' hummed ("mm", closed and soft,
pulsing on the beat), and through a Harmonize warning a rising hum. Every block starts its first note exactly at 0 s
and rings 0.9 s past its 4.8 s into the next.

The attacks are notes in the lines: the Alto's four notes a turn start on beats 0, 2, 4 and 6 (a Homing Note forms
on each); the Tenor breathes in on beats 0 and 4 and swells an "oh" on 1 and 5 (the Sweeping Wave leaves on it); the
Bass strikes a low "oo" on 0 and 4 (the Ground Ripples), slides up through 5 and 6, and falls an octave on 7 (the Bass
Drop). The effects keep to the key: the note's glass ting on A, the drop's boom on D, the circles' bell chord and the
Harmonize stab on D minor, and at the death one soft D major chord.

Loudness: sung blocks -18 LUFS like the Colossus's loop, hums -27, the rising hum -23; the drop -12, the Harmonize
stab -10, the cry of a breaking mask -12, the tink -19.
"""
from __future__ import annotations

from functools import partial

import numpy as np

from dsp import SR, TWO_PI, ad, band, db, fade, hp, hp_shape, lp, n_of, norm, note, phase_of, place, rng, shaped_noise, timeline
from event import Event
from layers import BAR, GLASS, burst, chime, crackle, reverb, shatter, sung, thump, vowel_gain, whoosh

BEAT = 0.6
BLOCK = 8 * BEAT
TAIL = 0.9

# (centre Hz, bandwidth Hz, gain dB)
AH = ((800.0, 90.0, 0.0), (1150.0, 100.0, -4.0), (2800.0, 160.0, -12.0), (3400.0, 200.0, -15.0), (4400.0, 300.0, -24.0))
OH = ((450.0, 70.0, 0.0), (800.0, 80.0, -6.0), (2830.0, 180.0, -20.0), (3500.0, 220.0, -26.0), (4950.0, 300.0, -34.0))
OO = ((325.0, 60.0, 0.0), (700.0, 80.0, -10.0), (2530.0, 180.0, -28.0), (3500.0, 200.0, -34.0), (4950.0, 300.0, -40.0))
MM = ((260.0, 70.0, 0.0), (1400.0, 250.0, -26.0), (2400.0, 300.0, -34.0), (3300.0, 300.0, -40.0))

# the lines, one list per chord: D minor, B flat, G minor, A
ALTO_LINE = [["F5", "E5", "D5", "A4"], ["D5", "C5", "D5", "F5"], ["D5", "A#4", "G4", "A#4"], ["E5", "C#5", "A4", "E5"]]
TENOR_LINE = [["A3", "D4"], ["A#3", "F4"], ["A#3", "D4"], ["C#4", "E4"]]
BASS_LINE = ["D3", "A#2", "G2", "A2"]
BASS_FIFTH_BELOW = ["A2", "F2", "D2", "E2"]
HUM = {"alto": ["A4", "A#4", "A#4", "A4"], "tenor": ["F4", "F4", "D4", "E4"], "bass": ["D3", "A#2", "G2", "A2"]}
RISE = {"alto": ("A4", "D5"), "tenor": ("F4", "A4"), "bass": ("D3", "A3")}
VOWEL = {"alto": AH, "tenor": OH, "bass": OO}


def env(t, seconds, attack, release):
    a = np.clip(t / max(attack, 1e-4), 0.0, 1.0)
    r = np.clip((seconds - t) / max(release, 1e-4), 0.0, 1.0)
    return (a * a * (3 - 2 * a)) * (r * r * (3 - 2 * r))


def voice(r, seconds, pitch, formants, singers=3, detune=8.0, vib_cents=14.0, vib_hz=5.1, attack=0.05, release=0.2,
          f_max=6500.0, breath=0.05, rolloff=1.0, scoop=12.0):
    """A sung note: `pitch` is a frequency or a function of time (a glide); several slightly detuned singers."""
    t = timeline(seconds)
    base = pitch(t) if callable(pitch) else np.full_like(t, float(pitch))
    out = np.zeros_like(t)
    for s in range(singers):
        cents = detune * (s - (singers - 1) / 2.0) + r.normal(0.0, 2.0)
        settle = np.clip(t / 0.35, 0.0, 1.0)
        vib = vib_cents * settle * np.sin(TWO_PI * vib_hz * r.uniform(0.92, 1.08) * t + r.uniform(0.0, TWO_PI))
        dip = -scoop * np.exp(-t / 0.04)
        out += sung(base * 2.0 ** ((cents + vib + dip) / 1200.0), formants, r, rolloff=rolloff, f_max=f_max)
    out = norm(out)
    if breath:
        air = shaped_noise(seconds, lambda tt, f: vowel_gain(f, formants) * hp_shape(f, 500.0, 2), r)
        out = out + breath * norm(air)
    return out * env(t, seconds, attack, release)


def glide(f0, f1, seconds, curve=1.0):
    def fn(t):
        u = np.clip(t / seconds, 0.0, 1.0) ** curve
        return f0 * (f1 / f0) ** u
    return fn


def finish(x, seed, wet=0.24, t60=1.9):
    x = reverb(x, rng(seed), wet=wet, t60=t60, damp_hz=6500.0, hp_hz=150.0)
    return x[:n_of(BLOCK + TAIL)]


def blank():
    return np.zeros(n_of(BLOCK + TAIL))


# ------------------------------------------------------------------ the three lines

def alto_sung(chord, seed=2101):
    r = rng(seed + chord)
    out = blank()
    for i, name in enumerate(ALTO_LINE[chord]):
        n = voice(r, 2 * BEAT + 0.18, note(name), AH, singers=3, detune=7.0, attack=0.035, release=0.16)
        place(out, n, i * 2 * BEAT)
    return finish(out, seed + 50 + chord)


def tenor_sung(chord, seed=2201):
    r = rng(seed + chord)
    out = blank()
    for i, name in enumerate(TENOR_LINE[chord]):
        start = i * 4 * BEAT
        # the inhale: a breath drawn in over the beat before the note
        t = timeline(BEAT)
        air = shaped_noise(BEAT, lambda tt, f: vowel_gain(f, OH) * hp_shape(f, 700.0, 2) + 0.3 * hp_shape(f, 3000.0, 2), r)
        place(out, norm(air) * (t / BEAT) ** 2.2 * 0.22, start)
        # the "oh" swelling out on the beat (the wave leaves on it)
        seconds = 3 * BEAT + 0.12
        tt = timeline(seconds)
        swell = 0.75 + 0.25 * np.exp(-tt / 0.35) + 0.2 * np.clip(tt / 0.4, 0, 1)
        n = voice(r, seconds, note(name), OH, singers=3, detune=9.0, attack=0.03, release=0.3) * swell
        place(out, n, start + BEAT)
    return finish(out, seed + 50 + chord)


def bass_sung(chord, seed=2301):
    r = rng(seed + chord)
    out = blank()
    root = note(BASS_LINE[chord])
    below = note(BASS_FIFTH_BELOW[chord])
    # the ripples: a struck "oo" on beats 0 and 4
    for start, length in ((0.0, 1.5 * BEAT), (4 * BEAT, 1.0 * BEAT)):
        n = voice(r, length, root, OO, singers=3, detune=10.0, attack=0.02, release=0.22, breath=0.03)
        tt = timeline(length)
        n = n * (0.7 + 0.5 * np.exp(-tt / 0.18))
        place(out, n, start)
        place(out, norm(thump(0.5, root * 1.6, root * 0.5, 0.03, 0.18, drive=1.2)), start, db(-10))
    # the rise over the target: sliding up from a fourth below through beats 5 and 6
    slide = 2 * BEAT
    n = voice(r, slide, glide(below, root, slide, 0.8), OO, singers=3, detune=10.0, attack=0.08, release=0.1, breath=0.04)
    place(out, n * np.linspace(0.45, 1.0, len(n)), 5 * BEAT)
    # the drop on beat 7: the octave below, heavy
    drop = voice(r, BEAT + 0.3, root / 2.0, OO, singers=4, detune=12.0, attack=0.015, release=0.3, breath=0.02, scoop=40.0)
    place(out, drop * 1.2, 7 * BEAT)
    place(out, norm(thump(0.8, root, root / 4.0, 0.05, 0.35, drive=1.6)), 7 * BEAT, db(-4))
    return finish(out, seed + 50 + chord, wet=0.2, t60=1.6)


def hum(name, chord, seed=2401):
    r = rng(seed + chord + 17 * len(name))
    seconds = BLOCK + 0.5
    t = timeline(seconds)
    n = voice(r, seconds, note(HUM[name][chord]), MM, singers=3, detune=9.0, vib_cents=8.0, attack=0.25, release=0.5, breath=0.02,
              f_max=3500.0, scoop=0.0)
    beat_phase = (t % BEAT) / BEAT
    pulse = 0.82 + 0.18 * np.exp(-beat_phase * 5.0)
    out = blank()
    place(out, n * pulse, 0.0)
    return finish(out, seed + 90 + chord, wet=0.3, t60=2.2)


def rise(name, seed=2501):
    r = rng(seed + len(name))
    seconds = BLOCK + 0.3
    f0, f1 = (note(n) for n in RISE[name])
    n = voice(r, seconds, glide(f0, f1, BLOCK, 1.4), MM, singers=4, detune=11.0, vib_cents=10.0, attack=0.4, release=0.3,
              breath=0.03, f_max=4200.0, scoop=0.0)
    t = timeline(seconds)
    n = n * (0.35 + 0.65 * np.clip(t / BLOCK, 0, 1) ** 1.3)
    out = blank()
    place(out, n, 0.0)
    return finish(out, seed + 7, wet=0.35, t60=2.4)


# ------------------------------------------------------------------ one-shots

def first_note(name, pitch, seed):
    r = rng(seed)
    n = voice(r, 1.6 * BEAT + 0.4, note(pitch), VOWEL[name], singers=3, attack=0.05, release=0.45)
    out = np.zeros(n_of(3.0))
    place(out, n, 0.0)
    return reverb(out, rng(seed + 1), wet=0.35, t60=2.4)[:n_of(3.0)]


def choir_chord(r, seconds, notes, formants, attack, release, singers=3, detune=9.0):
    t = timeline(seconds)
    out = np.zeros_like(t)
    for f in notes:
        out += voice(r, seconds, f, formants, singers=singers, detune=detune, attack=attack, release=release)
    return norm(out)


def awaken(seed=2601):
    r = rng(seed)
    seconds = 3.2
    t = timeline(seconds)
    drone = choir_chord(r, seconds, [note("D2"), note("A2"), note("D3")], MM, attack=1.6, release=0.8)
    rising = voice(r, seconds, glide(note("A3"), note("D4"), seconds, 1.2), OO, singers=4, attack=1.4, release=0.9, scoop=0.0)
    out = drone * 0.8 + rising * 0.5 * np.clip(t / seconds, 0, 1)
    return reverb(out, rng(seed + 1), wet=0.4, t60=2.6)[:n_of(4.0)]


def hymnal(seed=2611):
    r = rng(seed)
    out = np.zeros(n_of(1.6))
    for k in range(4):
        place(out, burst(r, 0.12, 0.004, 0.05, lo=1500.0, hi=9000.0), 0.08 + k * 0.11, db(-2 - k))
    breath = voice(r, 1.2, note("D4"), AH, singers=3, attack=0.3, release=0.6, breath=0.2)
    place(out, breath, 0.25, db(-8))
    return reverb(out, rng(seed + 1), wet=0.3, t60=1.6)[:n_of(1.8)]


def dim(seed=2621):
    r = rng(seed)
    seconds = 2.2
    t = timeline(seconds)
    hush = shaped_noise(seconds, lambda tt, f: band(f, 3000.0 * (0.4 ** (tt / seconds)), 1.2), r) * env(t, seconds, 0.4, 1.2)
    low = voice(r, seconds, glide(note("A3"), note("D3"), seconds), MM, singers=3, attack=0.5, release=1.0, scoop=0.0)
    return reverb(norm(hush) * 0.5 + low * 0.7, rng(seed + 1), wet=0.3, t60=2.0)[:n_of(2.8)]


def note_form(seed=2701):
    r = rng(seed)
    x = norm(chime(note("A5"), 0.9, ratios=BAR, amps=(1.0, 0.3, 0.08, 0.03), taus=(0.35, 0.12, 0.05, 0.02)))
    place(x, voice(r, 0.5, note("A5"), AH, singers=2, attack=0.02, release=0.3), 0.0, 0.35)
    return reverb(x, rng(seed + 1), wet=0.2, t60=1.0)


def note_burst(seed=2711):
    r = rng(seed)
    out = np.zeros(n_of(0.9))
    place(out, norm(chime(note("D6"), 0.6, ratios=GLASS)), 0.0)
    place(out, voice(r, 0.25, note("D5"), AH, singers=2, attack=0.005, release=0.15), 0.0, db(-3))
    place(out, shatter(r, 0.3, 14, f_lo=3000.0, f_hi=9000.0), 0.0, db(-8))
    return fade(reverb(out, rng(seed + 1), wet=0.2, t60=0.8), 0.003, 0.0)


def note_break(seed=2721):
    r = rng(seed)
    out = np.zeros(n_of(0.7))
    place(out, shatter(r, 0.5, 26, f_lo=2500.0, f_hi=10000.0), 0.0)
    place(out, norm(chime(note("E6"), 0.4, ratios=BAR)), 0.0, db(-6))
    return out


def inhale(seed=2731):
    r = rng(seed)
    seconds = BEAT + 0.05
    t = timeline(seconds)
    air = shaped_noise(seconds, lambda tt, f: vowel_gain(f, OH) * hp_shape(f, 600.0, 2) + 0.4 * hp_shape(f, 2500.0, 2), r)
    return norm(air) * (t / seconds) ** 2.0 * env(t, seconds, 0.05, 0.04)


def wave(seed=2741):
    r = rng(seed)
    seconds = 1.0
    t = timeline(seconds)
    v = np.clip(np.exp(-((t - 0.12) / 0.14) ** 2), 0, 1)
    swish = whoosh(r, v, 900.0, 5000.0, width=0.9, whistle=0.6, air=0.5)
    sing = voice(r, 0.7, note("D4"), OH, singers=3, attack=0.01, release=0.4)
    out = np.zeros(n_of(seconds))
    place(out, norm(swish), 0.0)
    place(out, sing, 0.0, db(-4))
    place(out, norm(chime(note("A6"), 0.5, ratios=GLASS)), 0.02, db(-12))
    return reverb(out, rng(seed + 1), wet=0.25, t60=1.2)


def ripple_tell(seed=2751):
    r = rng(seed)
    seconds = 0.45
    t = timeline(seconds)
    rumble = lp(r.standard_normal(len(t)), 180.0, 2)
    sub = np.sin(phase_of(np.full_like(t, 46.0)))
    return norm(norm(rumble) * 0.6 + sub * 0.8) * (t / seconds) ** 1.5 * env(t, seconds, 0.05, 0.03)


def ripple(seed=2761):
    r = rng(seed)
    out = np.zeros(n_of(1.2))
    place(out, norm(thump(1.0, 120.0, 40.0, 0.04, 0.3, drive=1.8)), 0.0)
    thrum = voice(r, 0.8, note("D2"), OO, singers=4, detune=18.0, attack=0.01, release=0.5, breath=0.0)
    place(out, thrum, 0.0, db(-3))
    place(out, crackle(r, n_events=10, spread=0.05, hp_hz=800.0), 0.0, db(-12))
    return reverb(out, rng(seed + 1), wet=0.15, t60=0.9)


def drop_rise(seed=2771):
    r = rng(seed)
    seconds = 2 * BEAT
    t = timeline(seconds)
    rise_v = voice(r, seconds, glide(note("A1"), note("D2"), seconds), OO, singers=4, attack=0.2, release=0.3, scoop=0.0)
    air = whoosh(r, np.clip(t / seconds, 0, 1) ** 1.5, 200.0, 1500.0, width=1.0, whistle=0.0, air=0.2)
    return fade(norm(rise_v * 0.8 + norm(air) * 0.35), 0.0, 0.3)


def glint(seed=2781):
    x = chime(note("D7"), 0.7, ratios=BAR, amps=(1.0, 0.4, 0.12, 0.05), taus=(0.28, 0.1, 0.05, 0.02))
    return reverb(norm(x), rng(seed), wet=0.2, t60=0.9)


def drop(seed=2791):
    r = rng(seed)
    out = np.zeros(n_of(2.0))
    place(out, norm(thump(1.6, 150.0, 34.0, 0.05, 0.5, drive=2.2)), 0.0)
    place(out, voice(r, 1.1, note("D2"), OO, singers=4, detune=14.0, attack=0.008, release=0.6, scoop=60.0), 0.0, db(-2))
    place(out, burst(r, 0.25, 0.002, 0.06, lo=400.0, hi=4000.0), 0.0, db(-8))
    place(out, crackle(r, n_events=16, spread=0.12, hp_hz=600.0), 0.02, db(-10))
    return reverb(out, rng(seed + 1), wet=0.22, t60=1.4)


def tink(seed, f0):
    r = rng(seed)
    ratios = (1.0, 2.41, 3.87, 5.62)      # porcelain: a stiff, slightly inharmonic plate
    x = norm(chime(f0, 0.45, ratios=ratios, amps=(1.0, 0.5, 0.22, 0.1), taus=(0.09, 0.05, 0.03, 0.015), beats=(3.0, 5.0, 0.0, 0.0)))
    place(x, burst(r, 0.02, 0.0005, 0.004, lo=4000.0), 0.0, 0.05)
    return x


def hurt(seed, f0):
    r = rng(seed)
    out = np.zeros(n_of(0.5))
    place(out, crackle(r, n_events=5, spread=0.02, hp_hz=2000.0), 0.0)
    place(out, norm(tink(seed + 1, f0)), 0.0, db(-6))
    return fade(out, 0.003, 0.0)


def break_all(seed=2811):
    r = rng(seed)
    out = np.zeros(n_of(2.2))
    for k, f0 in enumerate((2300.0, 1900.0, 2700.0, 2100.0, 2500.0)):
        place(out, tink(seed + k, f0), 0.05 + k * 0.07 + r.uniform(0, 0.03), db(-2 - k))
    gasp = voice(r, 1.4, glide(note("A4"), note("D4"), 1.4), AH, singers=5, attack=0.03, release=0.9, breath=0.15)
    place(out, gasp, 0.0, db(-4))
    place(out, norm(thump(0.8, 100.0, 45.0, 0.04, 0.25)), 0.35, db(-6))
    return reverb(out, rng(seed + 1), wet=0.3, t60=1.8)


def crack_cry(seed, pitch, vowel):
    r = rng(seed)
    out = np.zeros(n_of(2.4))
    place(out, crackle(r, n_events=14, spread=0.06, hp_hz=1500.0), 0.0)
    place(out, shatter(r, 0.6, 30, f_lo=2200.0, f_hi=9000.0), 0.02, db(-4))
    f = note(pitch)
    cry = voice(r, 1.8, glide(f, f / 2.0, 1.8, 0.7), vowel, singers=3, attack=0.02, release=0.8, breath=0.1, vib_cents=30.0)
    place(out, cry, 0.05, db(-1))
    return reverb(out, rng(seed + 1), wet=0.35, t60=2.2)


def crack(seed=2821):
    """One cry for any mask (its line is gone): the three voices' cries share it, pitched by the game."""
    return crack_cry(seed, "A4", AH)


def circles(seed=2831):
    r = rng(seed)
    out = np.zeros(n_of(1.8))
    for k, name in enumerate(("D5", "F5", "A5", "D6")):
        place(out, norm(chime(note(name), 1.4, ratios=BAR, amps=(1.0, 0.3, 0.1, 0.04), taus=(0.8, 0.3, 0.1, 0.05))), k * 0.03, db(-2 * k))
    place(out, shaped_noise(1.0, lambda tt, f: hp_shape(f, 6000.0, 2) * np.exp(-tt / 0.4), r) * 0.05, 0.0)
    return reverb(out, rng(seed + 1), wet=0.3, t60=1.8)


def harmonize(seed=2841):
    r = rng(seed)
    out = np.zeros(n_of(3.0))
    stab = choir_chord(r, 1.6, [note("D3"), note("A3"), note("F4"), note("D5")], AH, attack=0.008, release=1.0, singers=4)
    place(out, stab, 0.0)
    place(out, norm(thump(1.2, 90.0, 30.0, 0.05, 0.45, drive=2.0)), 0.0, db(-7))
    place(out, shaped_noise(1.4, lambda tt, f: hp_shape(f, 4000.0, 2) * np.exp(-tt / 0.3), r) * 0.2, 0.0)
    return reverb(out, rng(seed + 1), wet=0.4, t60=2.6)[:n_of(3.2)]


def last_chord(seed=2851):
    r = rng(seed)
    seconds = 4.2
    chord = choir_chord(r, seconds, [note("D3"), note("A3"), note("F#4"), note("D5")], AH, attack=0.5, release=2.6, singers=3, detune=7.0)
    out = np.zeros(n_of(6.0))
    place(out, chord, 0.0)
    place(out, norm(chime(note("D6"), 3.0, ratios=GLASS, amps=(1.0, 0.2, 0.06, 0.02), taus=(1.6, 0.6, 0.2, 0.1))), 0.3, db(-14))
    return reverb(out, rng(seed + 1), wet=0.45, t60=3.0)[:n_of(6.5)]


# ------------------------------------------------------------------ the event list

MUSIC_LEN = BLOCK + TAIL
EVENTS = [
    Event("unsung/awaken", "The Unsung wake", [awaken], length=4.0, level=1.0, fade_out=1.0),
    Event("unsung/hymnal", "Hymnal pages turn", [hymnal], length=1.8, level=-6.0, fade_out=0.5),
    Event("unsung/dim", "Lichen dims", [dim], length=2.8, level=-6.0, fade_out=0.8),
    Event("unsung/first_alto", "Alto sings", [partial(first_note, "alto", "D5", 2901)], length=3.0, level=-2.0, fade_out=0.8),
    Event("unsung/first_tenor", "Tenor sings", [partial(first_note, "tenor", "A3", 2911)], length=3.0, level=-2.0, fade_out=0.8),
    Event("unsung/first_bass", "Bass sings", [partial(first_note, "bass", "D3", 2921)], length=3.0, level=-2.0, fade_out=0.8),
    Event("unsung/note_form", "A note forms", [note_form], length=1.2, level=-6.0, fade_out=0.3),
    Event("unsung/note_burst", "A note strikes", [note_burst], length=1.0, level=-2.0, fade_out=0.3),
    Event("unsung/note_break", "A note shatters", [note_break], length=0.7, level=-4.0, fade_out=0.2),
    Event("unsung/inhale", "Tenor inhales", [inhale], length=0.7, level=-4.0, fade_out=0.03),
    Event("unsung/wave", "A sound wave sweeps", [wave], length=1.5, level=0.0, fade_out=0.4),
    Event("unsung/ripple_tell", "The floor darkens", [ripple_tell], length=0.5, level=-6.0, fade_out=0.03),
    Event("unsung/ripple", "Ground ripples", [ripple], length=1.4, level=0.0, fade_out=0.4),
    Event("unsung/drop_rise", "Bass rises", [drop_rise], length=1.3, level=-2.0, fade_out=0.1),
    Event("unsung/glint", "Gold glint", [glint], length=0.9, level=1.0, fade_out=0.2),
    Event("unsung/drop", "Bass Drop", [drop], length=2.0, level=3.0, fade_out=0.6),
    Event("unsung/tink", "Porcelain tinks", [partial(tink, 2941, 2300.0), partial(tink, 2942, 2650.0), partial(tink, 2943, 2050.0)],
          length=0.45, level=-4.0, fade_out=0.08),
    Event("unsung/hurt", "Porcelain cracks", [partial(hurt, 2951, 1700.0), partial(hurt, 2952, 1500.0), partial(hurt, 2953, 1900.0)],
          length=0.5, level=-3.0, fade_out=0.1),
    Event("unsung/break", "Masks fall", [break_all], length=2.2, level=2.0, fade_out=0.6),
    Event("unsung/crack", "A mask shatters with a cry", [crack], length=2.4, level=3.0, fade_out=0.7),
    Event("unsung/circles", "Circles of silence light", [circles], length=1.8, level=-2.0, fade_out=0.5),
    Event("unsung/harmonize", "The choir harmonizes", [harmonize], length=3.2, level=5.0, fade_out=0.9),
    Event("unsung/last_chord", "The last chord", [last_chord], length=6.5, level=-3.0, fade_out=2.0),
]
for _chord in range(4):
    EVENTS.append(Event(f"music/unsung/alto_sung_{_chord}", "Music: the Unsung", [partial(alto_sung, _chord)], length=MUSIC_LEN, level=-3.0,
                        fade_out=0.6, quality=3))
    EVENTS.append(Event(f"music/unsung/tenor_sung_{_chord}", "Music: the Unsung", [partial(tenor_sung, _chord)], length=MUSIC_LEN, level=-3.0,
                        fade_out=0.6, quality=3))
    EVENTS.append(Event(f"music/unsung/bass_sung_{_chord}", "Music: the Unsung", [partial(bass_sung, _chord)], length=MUSIC_LEN, level=-3.0,
                        fade_out=0.6, quality=3))
    for _v in ("alto", "tenor", "bass"):
        EVENTS.append(Event(f"music/unsung/{_v}_hum_{_chord}", "Music: the Unsung", [partial(hum, _v, _chord)], length=MUSIC_LEN, level=-12.0,
                            fade_out=0.6, quality=3))
for _v in ("alto", "tenor", "bass"):
    EVENTS.append(Event(f"music/unsung/{_v}_rise", "Music: the Unsung", [partial(rise, _v)], length=MUSIC_LEN, level=-8.0, fade_out=0.5,
                        quality=3))
