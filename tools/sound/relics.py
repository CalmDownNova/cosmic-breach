"""The Heliarch's relics (G9b, GDD 7.3): Last Light's sounds (lastlight/) and the Umbra Cantor's (cantor/).

Last Light is a glaive of white-gold light: its thrusts are a bright airy rush with a short ring of star metal; its hits a
crack under a bright chime; the Sunspear's charge a sung D and A swell that the game pitches up as the charge fills; the
release a big bright rush and a sung "ah"; the Sunfall a boom with a D major chime; Dawnguard's stance a rising sung
D, F#, A; every parry tolls a bell (a D4 church bell: hum, prime, tierce, quint and nominal, ringing three seconds);
Daybreak's counter-slash a long sweeping rush and shimmer, its hit brighter than the thrusts'; a Sunlight charge a small
high chime (the game pitches it up by the count).

The Umbra Cantor is a dark bow with a violet string: a loose is a low string's twang under a dark arrow rush; the draw
a seamless 1 s loop of the string's strain over a low violet hum; the charged shot a heavy twang and a held tone; an
arrow landing a dull thunk and a dark hiss; a resonant note one bowed glass tone on D5 that the game pitches to every
note of D major pentatonic (CantorRules); a chord the triad D, F#, A, bowed and sung, with a long tail; its pulse a soft
low thrum; Cadence a twang and a quick rising shimmer; a silenced shot fizzling out a muffled hush.

Run:  python tools/sound/build.py lastlight cantor
"""
from __future__ import annotations

import numpy as np

from dsp import TWO_PI, ad, band, db, fade, hp, lp, n_of, norm, note, phase_of, place, rng, shaped_noise, silence, softclip, timeline
from event import Event
from layers import AH_BRIGHT, AH_DISTANT, BAR, GLASS, blade_ring, burst, chime, choir_stab, click, crackle, knock, modes, reverb, thump, whoosh

CHARGE_SECONDS = 1.0
DRAW_SECONDS = 1.0


def _v(seconds, rise, tau):
    """A swing's velocity: up in `rise`, dying away with `tau`."""
    t = timeline(seconds)
    return np.clip(t / rise, 0.0, 1.0) ** 1.5 * np.exp(-np.maximum(0.0, t - rise) / tau)


# ---------------------------------------------------------------------------------------------- Last Light

def thrust(seed=9101, length=0.38, rise=0.05, tau=0.07, ring=note("D6")):
    """A thrust: a bright airy rush that peaks fast, the glaive's star metal ringing briefly as it goes."""
    r = rng(seed)
    out = silence(length)
    v = _v(0.3, rise, tau)
    place(out, norm(whoosh(r, v, 1400.0, 7000.0, width=0.5, whistle=0.7, air=0.6)), 0.0, db(-1))
    place(out, norm(blade_ring(r, v, ring)), 0.0, db(-12))
    place(out, norm(chime(ring * 1.5, 0.25, ratios=BAR, taus=(0.06, 0.03, 0.015, 0.01))), rise, db(-16))
    return reverb(norm(out), r, wet=0.12, t60=0.5, damp_hz=8000.0, hp_hz=300.0)


def thrust_heavy(seed=9111):
    """The lunging third thrust: longer and lower, with more body."""
    r = rng(seed)
    out = silence(0.55)
    v = _v(0.45, 0.08, 0.12)
    place(out, norm(whoosh(r, v, 900.0, 5500.0, width=0.6, whistle=0.8, air=0.5, body=0.4)), 0.0, db(-1))
    place(out, norm(blade_ring(r, v, note("A5"))), 0.0, db(-11))
    place(out, norm(chime(note("D6"), 0.3, ratios=BAR, taus=(0.08, 0.04, 0.02, 0.01))), 0.08, db(-14))
    return reverb(norm(out), r, wet=0.14, t60=0.6, damp_hz=8000.0, hp_hz=250.0)


def hit(seed=9121, bright=note("A5")):
    """A solar strike: a crisp crack, a thump of body and a bright chime ringing out of it."""
    r = rng(seed)
    out = silence(0.5)
    place(out, burst(r, 0.08, 0.0006, 0.02, lo=2000.0, hi=12000.0), 0.001, db(-1))
    place(out, thump(0.16, 240.0, 90.0, 0.01, 0.035, drive=1.2), 0.001, db(-6))
    place(out, knock(r, 480.0, tau=0.014), 0.001, db(-11))
    place(out, norm(chime(bright, 0.4, ratios=BAR, taus=(0.16, 0.07, 0.03, 0.015))), 0.004, db(-7))
    place(out, norm(chime(bright * 2.0, 0.2, taus=(0.06, 0.03, 0.015, 0.01))), 0.004, db(-13))
    place(out, click(r, 0.001, 3000.0), 0.0, db(-5))
    return reverb(softclip(norm(out), 1.2), r, wet=0.12, t60=0.6, damp_hz=8000.0, hp_hz=300.0)


def charge():
    """The Sunspear's charge: a sung D and A with a warm shimmer, swelling once a second (1 s, whole cycles)."""
    t = timeline(CHARGE_SECONDS)
    tone = np.zeros_like(t)
    for f, a, ph in ((294.0, 1.0, 0.0), (441.0, 0.7, 1.1), (588.0, 0.45, 2.3), (882.0, 0.2, 0.7), (1176.0, 0.1, 1.9)):
        tone += a * np.sin(TWO_PI * f * t + ph)
    r = rng(9131)
    n = n_of(CHARGE_SECONDS)
    spec = np.fft.rfft(r.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / 44100)
    spec *= np.exp(-((np.log2(np.maximum(freqs, 1)) - np.log2(5000.0)) ** 2) / 0.5)
    air = np.fft.irfft(spec, n)
    swell = 0.75 + 0.25 * np.sin(TWO_PI * 1.0 * t)
    shimmer = 0.6 + 0.4 * np.sin(TWO_PI * 8.0 * t)
    return (norm(tone) * swell + 0.25 * norm(air) * shimmer)


def sunspear(seed=9141):
    """The charged thrust: a big bright rush, a sung "ah" on D and A, the glaive ringing long."""
    r = rng(seed)
    out = silence(1.1)
    v = _v(0.6, 0.07, 0.16)
    place(out, norm(whoosh(r, v, 800.0, 7500.0, width=0.6, whistle=0.9, air=0.7, body=0.5)), 0.0, db(-1))
    place(out, norm(choir_stab(r, 0.9, [note("D5"), note("A5")], formants=AH_BRIGHT, singers=3, tau=0.25, hold=0.05)), 0.03, db(-7))
    place(out, norm(chime(note("D6"), 0.9, ratios=BAR, taus=(0.35, 0.12, 0.05, 0.02))), 0.07, db(-10))
    return reverb(norm(out), r, wet=0.2, t60=1.2, damp_hz=9000.0)


def sunfall(seed=9151):
    """The Sunfall's landing: a boom with a sub under it, then a D major chime ringing out."""
    r = rng(seed)
    out = silence(1.6)
    place(out, thump(0.9, 120.0, 38.0, 0.05, 0.3, drive=1.5), 0.0, db(0))
    place(out, burst(r, 0.2, 0.001, 0.05, lo=200.0, hi=4000.0), 0.0, db(-6))
    for f, g in ((note("D5"), -8), (note("F#5"), -10), (note("A5"), -10), (note("D6"), -13)):
        place(out, norm(chime(f, 1.2, ratios=BAR, taus=(0.5, 0.18, 0.06, 0.02))), 0.01, db(g))
    return reverb(softclip(norm(out), 1.1), r, wet=0.22, t60=1.5, damp_hz=8000.0)


def dawnguard(seed=9161):
    """Dawnguard's stance: a rising sung D, F#, A under a brightening shimmer, as if a sun were coming up."""
    r = rng(seed)
    out = silence(1.5)
    t = timeline(1.2)
    choir = choir_stab(r, 1.2, [note("D4"), note("F#4"), note("A4")], formants=AH_DISTANT, singers=3, attack=0.25, hold=0.3,
                       tau=0.35, breath=0.06)
    place(out, norm(choir), 0.0, db(-2))
    shimmer = shaped_noise(1.2, lambda tt, f: band(f, 2500.0 + 5000.0 * tt, 0.5), r) * np.clip(t / 0.5, 0, 1) ** 2 * np.exp(-t / 0.6)
    place(out, norm(shimmer), 0.0, db(-12))
    place(out, norm(chime(note("A5"), 0.9, taus=(0.4, 0.15, 0.05, 0.02))), 0.18, db(-12))
    return reverb(norm(out), r, wet=0.25, t60=1.6, damp_hz=8000.0)


def bell(seed=9171):
    """The bell that tolls on every parry: a D4 church bell (hum, prime, tierce, quint, nominal, superquint), ringing long."""
    r = rng(seed)
    f = note("D4")
    ratios = (0.5, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0)
    amps = (0.55, 0.8, 0.5, 0.25, 1.0, 0.18, 0.25, 0.1)
    taus = (2.6, 1.6, 1.3, 0.9, 1.1, 0.35, 0.3, 0.15)
    beats = (0.6, 0.9, 1.1, 0.0, 1.4, 0.0, 0.0, 0.0)
    body = modes(3.2, [f * q for q in ratios], amps, taus, beats=beats, beat_mix=0.3, attack=0.0015)
    out = silence(3.4)
    place(out, norm(body), 0.0, db(0))
    place(out, knock(r, 900.0, tau=0.006), 0.0, db(-18))
    return reverb(out, r, wet=0.25, t60=2.4, damp_hz=6000.0)


def daybreak(seed=9181):
    """Daybreak's counter-slash: a long sweeping rush round the bearer, a bright shimmer and a sung rise."""
    r = rng(seed)
    out = silence(1.0)
    v = _v(0.7, 0.12, 0.22)
    place(out, norm(whoosh(r, v, 700.0, 6500.0, width=0.7, whistle=0.8, air=0.7, body=0.4)), 0.0, db(-1))
    place(out, norm(blade_ring(r, v, note("D6"))), 0.0, db(-12))
    place(out, norm(choir_stab(r, 0.7, [note("A4"), note("D5"), note("F#5")], singers=2, tau=0.2)), 0.1, db(-12))
    return reverb(norm(out), r, wet=0.2, t60=1.0, damp_hz=9000.0)


def sunlight(seed=9191):
    """A Sunlight charge: a small high chime and a sparkle (the game pitches it up by the count)."""
    r = rng(seed)
    out = silence(0.7)
    place(out, norm(chime(note("D6"), 0.6, ratios=BAR, taus=(0.25, 0.1, 0.04, 0.02))), 0.0, db(-2))
    place(out, norm(chime(note("A6"), 0.4, taus=(0.12, 0.05, 0.02, 0.01))), 0.03, db(-8))
    place(out, norm(crackle(r, n_events=6, spread=0.05, hp_hz=5000.0)), 0.0, db(-14))
    return reverb(out, r, wet=0.15, t60=0.8)


# ---------------------------------------------------------------------------------------------- the Umbra Cantor

def string(f0, seconds, bright=1.0):
    """A plucked bowstring: harmonics of f0, the upper ones dying fast."""
    k = np.arange(1, 13)
    return modes(seconds, list(f0 * k), list(bright ** (k - 1) / k), list(0.35 / k ** 0.8))


def loose(seed=9201, f0=note("A2"), length=0.5, rush=0.3):
    """A loose: a low string's twang, a knock of the limbs, a dark arrow rush."""
    r = rng(seed)
    out = silence(length)
    place(out, norm(string(f0, 0.45, bright=0.8)), 0.0, db(-2))
    place(out, knock(r, 220.0, tau=0.018), 0.0, db(-10))
    v = _v(rush, 0.03, 0.06)
    place(out, norm(whoosh(r, v, 500.0, 3500.0, width=0.7, whistle=0.3, air=0.2, body=0.3)), 0.01, db(-4))
    return reverb(norm(out), r, wet=0.12, t60=0.6, damp_hz=5000.0, hp_hz=120.0)


def loose_heavy(seed=9211):
    return loose(seed, f0=note("F#2"), length=0.65, rush=0.45)


def draw():
    """The draw: the string's strain (a slow creak of filtered noise) over a low violet hum (1 s, whole cycles)."""
    t = timeline(DRAW_SECONDS)
    hum = np.sin(TWO_PI * 147.0 * t) + 0.5 * np.sin(TWO_PI * 220.0 * t + 1.0) + 0.25 * np.sin(TWO_PI * 294.0 * t + 2.0)
    r = rng(9221)
    n = n_of(DRAW_SECONDS)
    spec = np.fft.rfft(r.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / 44100)
    spec *= np.exp(-((np.log2(np.maximum(freqs, 1)) - np.log2(900.0)) ** 2) / 0.6)
    creak = np.fft.irfft(spec, n)
    grain = 0.5 + 0.5 * np.sin(TWO_PI * 23.0 * t) ** 2
    swell = 0.8 + 0.2 * np.sin(TWO_PI * 1.0 * t)
    return norm(hum) * swell + 0.4 * norm(creak) * grain


def fermata(seed=9231):
    """The charged shot: a heavy twang and a held violet tone that rings out under the arrow's rush."""
    r = rng(seed)
    out = silence(1.2)
    place(out, norm(string(note("D2"), 0.8, bright=0.85)), 0.0, db(-2))
    place(out, knock(r, 180.0, tau=0.02), 0.0, db(-9))
    v = _v(0.5, 0.03, 0.1)
    place(out, norm(whoosh(r, v, 400.0, 4000.0, width=0.7, whistle=0.5, air=0.3, body=0.4)), 0.01, db(-4))
    tone = chime(note("D4"), 1.1, ratios=GLASS, amps=(1.0, 0.2, 0.05, 0.02), taus=(0.6, 0.2, 0.08, 0.03), attack=0.03)
    place(out, norm(tone), 0.02, db(-9))
    return reverb(norm(out), r, wet=0.2, t60=1.2, damp_hz=6000.0)


def arrow_hit(seed=9241):
    """An arrow landing: a dull thunk and a dark hiss of shadow."""
    r = rng(seed)
    out = silence(0.45)
    place(out, thump(0.18, 200.0, 80.0, 0.01, 0.04, drive=1.2), 0.0, db(-2))
    place(out, knock(r, 330.0, tau=0.016), 0.0, db(-8))
    t = timeline(0.3)
    hiss = shaped_noise(0.3, lambda tt, f: band(f, 2500.0 - 1500.0 * tt, 0.9), r) * np.exp(-t / 0.08)
    place(out, norm(hiss), 0.005, db(-8))
    return reverb(norm(out), r, wet=0.1, t60=0.5, damp_hz=5000.0)


def resonant_note(seed=9251):
    """A resonant note on D5: a bowed glass tone that swells in, wavers a little and rings for a second."""
    r = rng(seed)
    t = timeline(1.5)
    f = note("D5")
    vib = 1.0 + 0.004 * np.sin(TWO_PI * 5.0 * t) * np.clip(t / 0.3, 0, 1)
    body = np.zeros_like(t)
    for k, a, tau in ((1, 1.0, 0.9), (2, 0.35, 0.5), (3, 0.12, 0.3), (4, 0.06, 0.2)):
        body += a * np.sin(phase_of(f * k * vib)) * np.exp(-t / tau)
    body *= np.clip(t / 0.04, 0, 1)
    out = silence(1.7)
    place(out, norm(body), 0.0, db(-1))
    place(out, norm(chime(f * 2.0, 0.5, taus=(0.15, 0.06, 0.03, 0.01))), 0.0, db(-14))
    return reverb(fade(out, 0.0, 0.05), r, wet=0.22, t60=1.4, damp_hz=7000.0)


def chord(seed=9261):
    """A chord: the triad D, F#, A (and D above), bowed glass and a soft sung swell, a long bright tail."""
    r = rng(seed)
    t = timeline(2.2)
    out = silence(2.6)
    for f, g in ((note("D4"), 0), (note("F#4"), -1), (note("A4"), -1), (note("D5"), -4)):
        tone = np.zeros_like(t)
        for k, a, tau in ((1, 1.0, 1.4), (2, 0.3, 0.7), (3, 0.1, 0.4)):
            tone += a * np.sin(TWO_PI * f * k * t + r.uniform(0, TWO_PI)) * np.exp(-t / tau)
        place(out, norm(tone * np.clip(t / 0.08, 0, 1)), 0.0, db(g - 3))
    place(out, norm(choir_stab(r, 1.6, [note("D4"), note("F#4"), note("A4")], formants=AH_DISTANT, singers=2, attack=0.1,
                               hold=0.2, tau=0.5)), 0.0, db(-8))
    return reverb(norm(out), r, wet=0.3, t60=2.0, damp_hz=7000.0)


def pulse(seed=9271):
    """The chord's pulse: a soft low thrum and a breath of hush."""
    r = rng(seed)
    out = silence(0.7)
    place(out, thump(0.5, 110.0, 55.0, 0.04, 0.14), 0.0, db(-1))
    t = timeline(0.4)
    hush = shaped_noise(0.4, lambda tt, f: band(f, 700.0, 1.0), r) * np.exp(-t / 0.12)
    place(out, norm(hush), 0.0, db(-12))
    place(out, norm(chime(note("D4"), 0.5, taus=(0.2, 0.08, 0.03, 0.01))), 0.0, db(-14))
    return reverb(out, r, wet=0.15, t60=0.8, damp_hz=4000.0)


def cadence(seed=9281):
    """Cadence: a twang and a quick rising shimmer of D, F#, A."""
    r = rng(seed)
    out = silence(0.9)
    place(out, norm(loose(9282)), 0.0, db(-1))
    for i, n in enumerate(("D6", "F#6", "A6")):
        place(out, norm(chime(note(n), 0.35, ratios=BAR, taus=(0.12, 0.05, 0.02, 0.01))), 0.02 + 0.06 * i, db(-12))
    return reverb(norm(out), r, wet=0.18, t60=1.0)


def fizzle(seed=9291):
    """A silenced shot dying: a muffled puff and a small falling tone."""
    r = rng(seed)
    out = silence(0.5)
    t = timeline(0.3)
    puff = shaped_noise(0.3, lambda tt, f: band(f, 1200.0 - 800.0 * tt, 1.0), r) * ad(t, 0.01, 0.07)
    place(out, norm(puff), 0.0, db(-1))
    fall = np.sin(phase_of(880.0 * np.exp(-t / 0.12))) * np.exp(-t / 0.08)
    place(out, norm(fall), 0.0, db(-12))
    return lp(out, 3000.0)


EVENTS = [
    Event("lastlight/thrust", "Glaive thrusts", [thrust, lambda: thrust(9102, ring=note("E6"))], length=0.4, level=-3.0, fade_out=0.08),
    Event("lastlight/thrust_heavy", "Glaive lunges", [thrust_heavy], length=0.6, level=-2.0, fade_out=0.1),
    Event("lastlight/hit", "Sunlit blade strikes", [hit, lambda: hit(9122, note("B5"))], length=0.55, level=0.0, fade_out=0.1),
    Event("lastlight/charge", "Glaive gathers light", [charge], length=CHARGE_SECONDS, level=-10.0, loop=True, quality=6),
    Event("lastlight/sunspear", "Sunspear", [sunspear], length=1.2, level=0.0, fade_out=0.25),
    Event("lastlight/sunfall", "Sunfall lands", [sunfall], length=1.8, level=2.0, fade_out=0.3),
    Event("lastlight/dawnguard", "Dawnguard rises", [dawnguard], length=1.6, level=-3.0, fade_out=0.3),
    Event("lastlight/bell", "A bell tolls", [bell], length=3.4, level=-1.0, fade_out=0.6),
    Event("lastlight/daybreak", "Daybreak", [daybreak], length=1.1, level=-1.0, fade_out=0.2),
    Event("lastlight/daybreak_hit", "Daybreak strikes", [lambda: hit(9125, note("D6"))], length=0.55, level=1.0, fade_out=0.1),
    Event("lastlight/sunlight", "Sunlight gathers", [sunlight], length=0.8, level=-8.0, fade_out=0.15),
    Event("cantor/loose", "Bowstring twangs", [loose, lambda: loose(9202, f0=note("G#2"))], length=0.55, level=-3.0, fade_out=0.1),
    Event("cantor/loose_heavy", "Heavy bowstring twangs", [loose_heavy], length=0.7, level=-1.0, fade_out=0.12),
    Event("cantor/draw", "Bowstring draws", [draw], length=DRAW_SECONDS, level=-12.0, loop=True, quality=6),
    Event("cantor/fermata", "Charged arrow looses", [fermata], length=1.3, level=0.0, fade_out=0.25),
    Event("cantor/arrow_hit", "Shadow arrow lands", [arrow_hit], length=0.45, level=-3.0, fade_out=0.08),
    Event("cantor/note", "A note resonates", [resonant_note], length=1.7, level=-5.0, fade_out=0.3),
    Event("cantor/chord", "A chord rings", [chord], length=2.8, level=-2.0, fade_out=0.5),
    Event("cantor/pulse", "Chord pulses", [pulse], length=0.75, level=-8.0, fade_out=0.15),
    Event("cantor/cadence", "Cadence", [cadence], length=1.0, level=-2.0, fade_out=0.2),
    Event("cantor/fizzle", "Silenced shot fizzles", [fizzle], length=0.5, level=-8.0, fade_out=0.1),
]
