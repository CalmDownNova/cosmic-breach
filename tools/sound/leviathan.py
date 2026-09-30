"""The Thalassine Leviathan's sounds (Thalassine Leviathan design v1, "Look and sound"), and its boss loop.

A sky whale: its song is the fight's clock, low and slow while it orbits, swelling before each attack. The recorded
whale song takes (tools/sound/eleven/leviathan: song_take1, song_take2, call; never fetched at build time) carry its
voice; synthesis gives it body: layered sines gliding with slow vibrato and a formant sweep under the takes, a
rushing roar on the dive, rings of sound for the Song of Pulling, crystal pops for the scales, a deep bell for the
altar, a long falling tone at its death. The boss loop is 8 bars at Vesper's 100 BPM in D minor round the song takes,
starting on its downbeat at 0 s so the game can retrigger it on the Vesper clock's bar lines, ringing 1.6 s past its
19.2 s into the next.
"""
from __future__ import annotations

from fractions import Fraction
from functools import partial

import numpy as np
from scipy.signal import resample_poly

import eleven
from aetheria import BEAT, bass, bell, chord_notes, pad, pluck, render, sub
from dsp import TWO_PI, band, db, fade, hp, lp, n_of, norm, note, phase_of, place, rng, shaped_noise, silence, timeline
from event import Event
from layers import BAR, GLASS, chime, click, crackle, glass_grain, reverb, shatter, thump, whoosh

PHRASE_BARS = 8
TAIL = 1.6


def take(name, seconds, fade_out, speed=1.0, start=0.0, lowpass=None, highpass=None):
    """A recorded take as a layer: from `start` s, at `speed` (under 1: lower and slower), filtered, `seconds` long."""
    x = eleven.sample(name)[n_of(start):]
    if speed != 1.0:
        f = Fraction(speed).limit_denominator(24)
        x = resample_poly(x, f.denominator, f.numerator)
    if highpass:
        x = hp(x, highpass, 2)
    if lowpass:
        x = lp(x, lowpass, 2)
    n = n_of(seconds)
    x = np.concatenate([x[:n], np.zeros(max(0, n - len(x)))])
    return fade(x, 0.01, fade_out)


def glide_voice(seconds, f0, f1, seed, vibrato=5.0, formant=(600.0, 1400.0)):
    """A synthesized whale voice: a few detuned sines gliding f0 to f1 (a slow S), slow vibrato, a formant sweep."""
    r = rng(seed)
    t = timeline(seconds)
    u = np.clip(t / seconds, 0, 1)
    shape = u * u * (3 - 2 * u)
    f = f0 * (f1 / f0) ** shape
    vib = 1.0 + 0.012 * np.sin(TWO_PI * vibrato * t / 4.0 + r.uniform(0, 6))
    env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 0.7
    out = np.zeros_like(t)
    for k, (mult, amp) in enumerate(((1.0, 1.0), (2.0, 0.45), (3.0, 0.22), (1.005, 0.6), (0.5, 0.35))):
        ph = phase_of(f * mult * vib, r.uniform(0, 6))
        out += amp * np.sin(ph)
    fc = formant[0] + (formant[1] - formant[0]) * np.sin(np.pi * u)
    shaped = lp(out * env, 2400.0, 2) + 0.35 * np.sin(phase_of(fc)) * 0.0
    return norm(shaped)


def song(seed, start=0.0, speed=0.85, low=1.0):
    """The orbiting song: a stretch of a recorded take a little lower and slower over a gliding synthesized voice."""
    r = rng(seed)
    out = silence(4.2)
    place(out, norm(take("leviathan/song_take1" if seed % 2 else "leviathan/song_take2", 4.0, 1.2, speed=speed, start=start,
                         lowpass=5000.0)), 0.0, 1.0)
    place(out, glide_voice(3.6, 150.0 * low, 110.0 * low, seed), 0.2, 0.35)
    return reverb(out, r, wet=0.35, t60=2.6)


def swell(seed=2101):
    """Before an attack: the song swells and rises."""
    r = rng(seed)
    out = silence(1.8)
    x = take("leviathan/song_take2", 1.7, 0.4, speed=0.95, start=1.2, lowpass=6000.0)
    t = timeline(1.7)
    place(out, norm(x * (0.3 + 0.7 * np.clip(t / 1.3, 0, 1))), 0.0, 1.0)
    place(out, glide_voice(1.6, 140.0, 260.0, seed), 0.05, 0.55)
    return reverb(out, r, wet=0.3, t60=2.0)


def awaken(seed=2111):
    """Its song rises from below: the take slowed and dark, opening up, over a deep swell."""
    r = rng(seed)
    out = silence(5.0)
    x = take("leviathan/song_take2", 4.8, 1.2, speed=0.72, lowpass=2500.0)
    t = timeline(4.8)
    place(out, norm(x * np.clip(t / 2.0, 0, 1)), 0.0, 1.0)
    place(out, glide_voice(4.5, 70.0, 140.0, seed), 0.3, 0.6)
    place(out, norm(sub(40.0, 3.5, attack=1.5, tau=1.5)), 0.0, 0.5)
    return reverb(out, r, wet=0.4, t60=3.0)


def dive(seed=2121):
    """The Breach Dive: a rushing roar, water-heavy air torn open, its call under it."""
    r = rng(seed)
    out = silence(2.2)
    v = np.clip(np.sin(np.pi * np.clip(timeline(2.0) / 2.0, 0, 1)), 0, 1) ** 0.6
    place(out, norm(whoosh(r, v, 120.0, 1400.0, width=1.2, whistle=0.2)), 0.0, 1.0)
    place(out, norm(take("leviathan/call", 2.0, 0.6, speed=0.75, lowpass=3000.0)), 0.1, 0.8)
    place(out, norm(thump(0.8, 90, 40, 0.08, 0.3, drive=1.2)), 0.0, 0.5)
    return reverb(out, r, wet=0.2, t60=1.4)


def pull(seed=2131):
    """The Song of Pulling: its song pulsing out in rings, eight a second, rising."""
    r = rng(seed)
    out = silence(3.2)
    t = timeline(3.0)
    x = take("leviathan/song_take1", 3.0, 0.5, speed=1.05, start=0.8)
    rings = 0.45 + 0.55 * np.abs(np.sin(np.pi * 3.0 * t)) ** 2
    place(out, norm(x * rings), 0.0, 1.0)
    place(out, glide_voice(3.0, 180.0, 300.0, seed) * rings, 0.0, 0.5)
    return reverb(out, r, wet=0.3, t60=1.8)


def bite(seed=2141):
    r = rng(seed)
    out = silence(0.9)
    place(out, norm(thump(0.6, 140, 45, 0.04, 0.2, drive=2.0)), 0.0, 1.0)
    place(out, norm(crackle(r, n_events=10, spread=0.03, hp_hz=900.0)), 0.01, 0.5)
    place(out, norm(take("leviathan/call", 0.8, 0.3, speed=1.2, start=0.4)), 0.0, 0.45)
    return reverb(out, r, wet=0.15, t60=0.9)


def flick_tell(seed=2151):
    """The tail curls back: a creaking groan and a rising whistle."""
    r = rng(seed)
    out = silence(1.1)
    t = timeline(1.0)
    ph = phase_of(90.0 * (1.0 + 0.8 * np.clip(t, 0, 1)))
    place(out, norm(np.sin(ph) * np.sin(np.pi * np.clip(t, 0, 1)) + 0.2 * np.sin(2 * ph)), 0.0, 0.6)
    v = np.clip(timeline(1.0) / 1.0, 0, 1)
    place(out, norm(whoosh(r, v, 600.0, 2400.0, width=0.5, whistle=1.0)), 0.0, 0.5)
    return reverb(out, r, wet=0.2, t60=1.0)


def glint(seed=2161):
    """The parry cue: a clean gold ting."""
    r = rng(seed)
    out = silence(0.6)
    place(out, norm(chime(note("A6"), 0.5, ratios=BAR, taus=(0.3, 0.12, 0.06, 0.03))), 0.0, 1.0)
    place(out, norm(chime(note("E7"), 0.4, ratios=BAR, taus=(0.2, 0.08, 0.04, 0.02))), 0.02, 0.5)
    return reverb(out, r, wet=0.2, t60=0.8)


def flick(seed=2171):
    r = rng(seed)
    out = silence(0.8)
    v = np.exp(-timeline(0.5) / 0.12)
    place(out, norm(whoosh(r, v, 300.0, 2600.0, width=0.9, whistle=0.4)), 0.0, 1.0)
    place(out, norm(thump(0.5, 120, 50, 0.05, 0.15)), 0.05, 0.6)
    return reverb(out, r, wet=0.15, t60=0.8)


def droop(seed=2181):
    """A parried flick: a low sighing groan as the tail droops."""
    r = rng(seed)
    out = silence(1.4)
    place(out, norm(take("leviathan/call", 1.3, 0.5, speed=0.6, start=0.6, lowpass=1800.0)), 0.0, 1.0)
    place(out, glide_voice(1.2, 120.0, 70.0, seed), 0.05, 0.6)
    return reverb(out, r, wet=0.25, t60=1.4)


def shed(seed=2191):
    """Scales peel off its flanks: brittle cracks and a shimmer."""
    r = rng(seed)
    out = silence(1.3)
    for k in range(6):
        place(out, norm(crackle(r, n_events=6, spread=0.02, hp_hz=1500.0)), 0.05 + 0.15 * k, 0.5)
        place(out, norm(glass_grain(r, r.uniform(2400, 4200), tau=0.06)), 0.08 + 0.15 * k, 0.4)
    return reverb(out, r, wet=0.25, t60=1.2)


def scale_pop(seed):
    r = rng(seed)
    out = silence(0.45)
    place(out, norm(click(r, 0.0015, hp_hz=1200.0)), 0.0, 0.8)
    place(out, norm(shatter(r, 0.35, 16, f_lo=2500, f_hi=9000, amp_tau=0.1)), 0.0, 1.0)
    place(out, norm(chime(note("D6") * r.uniform(0.95, 1.08), 0.3, ratios=GLASS, taus=(0.15, 0.06, 0.03, 0.02))), 0.005, 0.5)
    return out


def coil(seed=2201):
    """It settles round the core: a long settling groan and rock rumbling under it."""
    r = rng(seed)
    out = silence(2.4)
    place(out, norm(take("leviathan/song_take1", 2.2, 0.8, speed=0.7, start=3.0, lowpass=2200.0)), 0.0, 1.0)
    place(out, norm(thump(1.2, 70, 35, 0.2, 0.6, drive=1.0)), 0.2, 0.6)
    place(out, norm(crackle(r, n_events=14, spread=0.3, hp_hz=300.0, lp_hz=2000.0)), 0.25, 0.3)
    return reverb(out, r, wet=0.3, t60=1.8)


def ripple(seed=2211):
    """A shudder's telegraph: a ring of light runs along it, a rising shimmer."""
    r = rng(seed)
    out = silence(0.9)
    t = timeline(0.8)
    ph = phase_of(note("D5") * 2 ** np.clip(t / 0.8, 0, 1))
    place(out, norm(np.sin(ph) * np.clip(t / 0.8, 0, 1) + 0.3 * np.sin(2 * ph)), 0.0, 0.7)
    place(out, norm(shaped_noise(0.8, lambda tt, f: band(f, 3000.0 + 3000 * tt, 0.8), r)) * 0.3, 0.0)
    return reverb(out, r, wet=0.3, t60=1.2)


def shudder(seed=2221):
    r = rng(seed)
    out = silence(1.0)
    place(out, norm(thump(0.9, 80, 30, 0.1, 0.35, drive=2.0)), 0.0, 1.0)
    place(out, norm(crackle(r, n_events=10, spread=0.1, hp_hz=400.0, lp_hz=3000.0)), 0.0, 0.4)
    return reverb(out, r, wet=0.2, t60=1.0)


def tear_free(seed=2231):
    """It tears free: its full call, a roar, and a burst of dust."""
    r = rng(seed)
    out = silence(3.0)
    place(out, norm(take("leviathan/call", 2.8, 0.8, speed=0.85)), 0.0, 1.0)
    v = np.exp(-timeline(1.5) / 0.4)
    place(out, norm(whoosh(r, v, 200.0, 3000.0, width=1.4, whistle=0.1)), 0.0, 0.6)
    place(out, norm(thump(1.0, 110, 40, 0.06, 0.4, drive=1.5)), 0.0, 0.6)
    return reverb(out, r, wet=0.3, t60=2.0)


def break_(seed=2241):
    r = rng(seed)
    out = silence(1.8)
    place(out, norm(take("leviathan/call", 1.6, 0.6, speed=0.7, start=0.2, lowpass=2600.0)), 0.0, 1.0)
    place(out, norm(crackle(r, n_events=16, spread=0.06, hp_hz=700.0)), 0.0, 0.5)
    place(out, norm(thump(0.8, 100, 40, 0.06, 0.3, drive=1.4)), 0.0, 0.6)
    return reverb(out, r, wet=0.25, t60=1.5)


def hurt(seed, start):
    r = rng(seed)
    out = silence(0.6)
    place(out, norm(take("leviathan/call", 0.55, 0.25, speed=1.1, start=start, lowpass=4000.0)), 0.0, 1.0)
    return reverb(out, r, wet=0.15, t60=0.8)


def gland_hit(seed=2251):
    """A song gland struck: a warm bell-like ping."""
    r = rng(seed)
    out = silence(0.8)
    place(out, norm(chime(note("F#5"), 0.7, ratios=GLASS, taus=(0.4, 0.15, 0.07, 0.03))), 0.0, 1.0)
    place(out, norm(chime(note("A5"), 0.6, ratios=GLASS, taus=(0.3, 0.12, 0.05, 0.02))), 0.01, 0.5)
    place(out, norm(thump(0.3, 200, 90, 0.03, 0.1)), 0.0, 0.4)
    return reverb(out, r, wet=0.2, t60=0.9)


def death(seed=2261):
    """Its death: the song fading, a long falling tone under it."""
    r = rng(seed)
    out = silence(6.0)
    t = timeline(5.5)
    ph = phase_of(420.0 * (60.0 / 420.0) ** np.clip(t / 5.0, 0, 1))
    place(out, norm((np.sin(ph) + 0.3 * np.sin(2 * ph)) * np.exp(-t / 2.5)), 0.0, 0.7)
    x = take("leviathan/song_take2", 5.0, 2.0, speed=0.8, start=0.5, lowpass=3500.0)
    place(out, norm(x * np.exp(-timeline(5.0) / 2.2)), 0.2, 0.9)
    return reverb(out, r, wet=0.45, t60=3.5)


def pearl(seed=2271):
    """A pearl of light rises: a D major arpeggio climbing into a shimmer."""
    r = rng(seed)
    out = silence(3.0)
    for i, n in enumerate(("D5", "F#5", "A5", "D6", "F#6", "A6")):
        place(out, norm(chime(note(n), 1.6, ratios=BAR, taus=(0.8, 0.3, 0.12, 0.05))), 0.12 * i, 0.6)
    place(out, norm(shaped_noise(2.5, lambda tt, f: band(f, 6000.0, 0.7), r)) * 0.15, 0.3)
    return reverb(out, r, wet=0.4, t60=2.4)


def rift_bell(seed=2281):
    """The Rimeglass bell: a deep bell on D2 with a bright crystal strike."""
    r = rng(seed)
    out = silence(4.0)
    place(out, norm(bell(note("D2"), 4.0, taus=(2.6, 1.2, 0.5, 0.2))), 0.0, 1.0)
    place(out, norm(bell(note("A3"), 3.0, taus=(1.8, 0.8, 0.3, 0.1))), 0.0, 0.4)
    place(out, norm(chime(note("D6"), 1.0, ratios=GLASS, taus=(0.5, 0.2, 0.1, 0.05))), 0.0, 0.25)
    return reverb(out, r, wet=0.35, t60=3.0)


def grow(seed=2291):
    """Driftwood grows out to the coil: creaking wood and soft cracks."""
    r = rng(seed)
    out = silence(1.2)
    for k in range(8):
        place(out, norm(crackle(r, n_events=4, spread=0.03, hp_hz=500.0, lp_hz=4000.0)), 0.05 + 0.12 * k, 0.6)
    t = timeline(1.0)
    place(out, norm(np.sin(phase_of(140.0 + 40 * t)) * np.sin(np.pi * np.clip(t, 0, 1))), 0.0, 0.3)
    return reverb(out, r, wet=0.2, t60=0.9)


def drum(seconds, f0=60.0, seed=0):
    r = rng(seed)
    x = norm(thump(seconds, f0 * 1.5, f0, 0.04, 0.3, drive=1.2))
    place(x, norm(shaped_noise(0.06, lambda tt, f: band(f, 1800.0, 1.0), r)), 0.0, 0.15)
    return x


def boss_loop():
    """8 bars at 100 BPM in D minor round the whale song: i - VI - III - VII. A slow pad, a deep drum on 1 and a softer
    one on 3, a pulsing sub, a sparse crystal line, and the recorded song in two long phrases across the loop (the
    first a little lower, the second answering), as if it sings over its own music."""
    seconds = PHRASE_BARS * 4 * BEAT + TAIL
    chords = [(["D3", "A3", "D4", "F4"], "D2"), (["A#2", "F3", "A#3", "D4"], "A#1"),
              (["F3", "C4", "F4", "A4"], "F2"), (["C3", "G3", "C4", "E4"], "C2")]
    score = []
    for bar in range(PHRASE_BARS):
        c = (bar // 2) % 4
        chord, root = chords[c]
        b0 = bar * 4
        if bar % 2 == 0:
            score.append((b0, 8.6, (lambda sec, ch=chord, s=bar: pad(chord_notes(ch), sec, attack=0.8, release=1.4, bright=4.0, seed=60 + s)), -11))
        score.append((b0, 1.4, (lambda sec: drum(sec, 55.0, 1)), -6))
        score.append((b0 + 2, 1.2, (lambda sec: drum(sec, 62.0, 2)), -11))
        for e in range(4):
            score.append((b0 + e, 1.0, (lambda sec, f=note(root): bass(f, sec)), -12 if e % 2 == 0 else -15))
        for k, n in enumerate((("D5", "A5"), ("F5", "A#5"), ("F5", "C6"), ("E5", "G5"))[c]):
            score.append((b0 + 1.5 + k * 2, 2.0, (lambda sec, f=note(n): pluck(f, sec, bright=1.2, tau=0.8)), -19))
    x = render(score, seconds, 11)
    t1 = take("leviathan/song_take1", 6.0, 1.5, speed=0.9)
    t2 = take("leviathan/song_take2", 6.0, 1.5, speed=0.95)
    place(x, norm(t1) * db(-9), 2 * BEAT)
    place(x, norm(t2) * db(-10), 16 * BEAT)
    x = reverb(x, rng(78), wet=0.28, t60=2.4, damp_hz=6000.0, hp_hz=90.0)[:n_of(seconds)]
    return x


EVENTS = [
    Event("leviathan/song", "Leviathan sings", [partial(song, 2001, 0.0), partial(song, 2002, 1.5), partial(song, 2003, 2.0, 0.8, 0.9)],
          length=4.2, level=-3.0, fade_out=1.0),
    Event("leviathan/swell", "Leviathan's song swells", [swell], length=1.8, level=-1.0, fade_out=0.4),
    Event("leviathan/awaken", "Leviathan wakes", [awaken], length=5.0, level=1.0, fade_out=1.0),
    Event("leviathan/dive", "Leviathan dives", [dive], length=2.2, level=3.0, fade_out=0.5),
    Event("leviathan/pull", "Song of Pulling", [pull], length=3.2, level=1.0, fade_out=0.5),
    Event("leviathan/bite", "Leviathan bites", [bite], length=0.9, level=3.0, fade_out=0.2),
    Event("leviathan/flick_tell", "Leviathan's tail curls", [flick_tell], length=1.1, level=-1.0, fade_out=0.1),
    Event("leviathan/glint", "Gold glint", [glint], length=0.6, level=1.0, fade_out=0.1),
    Event("leviathan/flick", "Tail Flick", [flick], length=0.8, level=2.0, fade_out=0.15),
    Event("leviathan/droop", "Leviathan's tail droops", [droop], length=1.4, level=0.0, fade_out=0.4),
    Event("leviathan/shed", "Leviathan sheds scales", [shed], length=1.3, level=-1.0, fade_out=0.3),
    Event("leviathan/scale_pop", "Scale bursts", [partial(scale_pop, 2301), partial(scale_pop, 2302), partial(scale_pop, 2303)],
          length=0.45, level=-3.0, fade_out=0.08),
    Event("leviathan/coil", "Leviathan coils", [coil], length=2.4, level=1.0, fade_out=0.6),
    Event("leviathan/ripple", "Leviathan ripples", [ripple], length=0.9, level=-1.0, fade_out=0.15),
    Event("leviathan/shudder", "Leviathan shudders", [shudder], length=1.0, level=2.0, fade_out=0.3),
    Event("leviathan/tear_free", "Leviathan tears free", [tear_free], length=3.0, level=3.0, fade_out=0.8),
    Event("leviathan/break", "Leviathan breaks", [break_], length=1.8, level=2.0, fade_out=0.5),
    Event("leviathan/hurt", "Leviathan hurts", [partial(hurt, 2311, 0.2), partial(hurt, 2312, 1.0), partial(hurt, 2313, 1.8)],
          length=0.6, level=-4.0, fade_out=0.15),
    Event("leviathan/gland_hit", "Song gland rings", [gland_hit], length=0.8, level=-1.0, fade_out=0.2),
    Event("leviathan/death", "Leviathan falls silent", [death], length=6.0, level=2.0, fade_out=1.5),
    Event("leviathan/pearl", "A pearl of light rises", [pearl], length=3.0, level=0.0, fade_out=0.8),
    Event("leviathan/bell", "Rimeglass bell rings", [rift_bell], length=4.0, level=1.0, fade_out=1.0),
    Event("leviathan/grow", "Driftwood grows", [grow], length=1.2, level=-3.0, fade_out=0.2),
    Event("music/leviathan", "Music: the Thalassine Leviathan", [boss_loop], length=PHRASE_BARS * 4 * BEAT + TAIL, level=-3.0,
          fade_out=1.2, quality=2),
]
