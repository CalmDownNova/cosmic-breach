"""The Choir Astrolabe's sounds (GDD 4.2, "VFX and sound"): every bolt is a chime from a pentatonic scale, so any combo sounds
like a phrase; a warm hum from the Pocket Star; a choir hit on the Supernova.

The chime is one crystal-and-bell note on D5 (587.33 Hz) that the game plays at the pitch of each note of D major
pentatonic (AstrolabeTunes: A4 to D6, pitch 0.75 to 2), ringing long enough (0.4 s) that a chain's notes overlap into a
phrase. The rest: the rings' flick, a bolt's landing sparkle, the star kindling, its hum (a seamless 2 s loop on D3,
A3, D4 with a slow beat and a shimmer), its pulse, a swallowed shot, a mark's ting, the Constellation's beam, the
charge (a seamless 1 s loop of the rings spinning up; the game raises its pitch and volume with the charge) and the
Supernova: a sung D major chord over a deep bell and a soft boom, with a long bright tail.
"""
from __future__ import annotations

import numpy as np

from dsp import TWO_PI, band, fade, n_of, norm, note, phase_of, place, rng, shaped_noise, silence, timeline
from event import Event
from layers import AH_BRIGHT, BAR, GLASS, chime, choir_stab, crackle, reverb, reverb_wrap, thump, whoosh

D5 = 587.33
HUM_SECONDS = 2.0
CHARGE_SECONDS = 1.0


def star_chime(seed=7201):
    """The note: a glass chime and a softer bell an octave down, a touch of shimmer, a small room."""
    r = rng(seed)
    out = silence(1.3)
    place(out, norm(chime(D5, 1.2, ratios=GLASS, amps=(1.0, 0.3, 0.1, 0.04), taus=(0.42, 0.16, 0.07, 0.03),
                          beats=(1.5, 2.5, 0.0, 0.0), attack=0.0015)), 0.0, 1.0)
    place(out, norm(chime(D5 / 2, 1.0, ratios=BAR, amps=(1.0, 0.2, 0.05, 0.0), taus=(0.35, 0.1, 0.04, 0.02))), 0.0, 0.3)
    place(out, norm(chime(D5 * 4, 0.3, taus=(0.08, 0.04, 0.02, 0.01))), 0.0, 0.12)
    return reverb(out, r, wet=0.2, t60=1.1, damp_hz=7000.0)


def flick(seed=7211):
    """Brass rings flicked round: three quick ticks and a small whirr."""
    r = rng(seed)
    out = silence(0.3)
    for k, f in enumerate((2400.0, 2900.0, 3300.0)):
        place(out, norm(chime(f, 0.08, ratios=BAR, taus=(0.03, 0.015, 0.01, 0.005))), 0.02 * k, 0.4)
    v = np.exp(-timeline(0.2) / 0.05)
    place(out, norm(whoosh(r, v, 1500.0, 6000.0, width=0.5, whistle=0.3)), 0.0, 0.5)
    return out


def bolt_hit(seed=7221):
    """A star bursting on something: a crisp sparkle over a soft thump."""
    r = rng(seed)
    out = silence(0.45)
    place(out, norm(crackle(r, n_events=7, spread=0.03, hp_hz=4000.0)), 0.0, 0.7)
    place(out, norm(chime(note("A6"), 0.3, taus=(0.08, 0.04, 0.02, 0.01))), 0.0, 0.4)
    place(out, norm(thump(0.25, 160.0, 70.0, 0.03, 0.06)), 0.0, 0.6)
    return reverb(out, r, wet=0.1, t60=0.6)


def star_place(seed=7231):
    """A star kindling: a soft low bell and a shimmer rising into it."""
    r = rng(seed)
    out = silence(1.4)
    t = timeline(0.4)
    shimmer = shaped_noise(0.4, lambda tt, f: band(f, 3000.0 + 4000.0 * tt, 0.6), r) * np.clip(t / 0.4, 0, 1) ** 2
    place(out, norm(shimmer), 0.0, 0.35)
    place(out, norm(chime(note("D4"), 1.0, taus=(0.5, 0.2, 0.08, 0.03))), 0.35, 0.9)
    place(out, norm(chime(note("A5"), 0.8, taus=(0.3, 0.1, 0.05, 0.02))), 0.36, 0.35)
    return reverb(out, r, wet=0.22, t60=1.3)


def hum():
    """The warm hum: D3, A3 and D4 (every partial a whole number of cycles in 2 s), a slow beat and a soft shimmer."""
    t = timeline(HUM_SECONDS)
    x = np.zeros_like(t)
    for f, a, ph in ((147.0, 1.0, 0.3), (147.5, 0.7, 2.2), (220.0, 0.55, 4.1), (293.5, 0.35, 1.7), (441.0, 0.12, 5.0),
                     (73.5, 0.3, 3.3)):
        x += a * np.sin(TWO_PI * f * t + ph)
    r = rng(7241)
    n = n_of(HUM_SECONDS)
    spec = np.fft.rfft(r.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / 44100)
    spec *= np.exp(-((np.log2(np.maximum(freqs, 1)) - np.log2(3200.0)) ** 2) / 0.4)
    air = np.fft.irfft(spec, n)
    swell = 0.6 + 0.4 * np.sin(TWO_PI * 1.0 * t)
    return norm(x) + 0.08 * norm(air) * swell


def pulse(seed=7251):
    """A pulse of light: a soft whump and a bright ping."""
    r = rng(seed)
    out = silence(0.6)
    place(out, norm(thump(0.4, 130.0, 60.0, 0.04, 0.1)), 0.0, 0.6)
    place(out, norm(chime(note("D6"), 0.4, taus=(0.14, 0.06, 0.03, 0.01))), 0.0, 0.35)
    return reverb(out, r, wet=0.15, t60=0.8)


def swallow(seed=7261):
    """A shot swallowed: sucked in and snuffed with a small chime."""
    r = rng(seed)
    out = silence(0.5)
    t = timeline(0.25)
    suck = shaped_noise(0.25, lambda tt, f: band(f, 5000.0 - 3800.0 * tt, 0.8), r) * np.clip(t / 0.22, 0, 1) ** 2
    place(out, norm(suck), 0.0, 0.7)
    place(out, norm(chime(note("F#6"), 0.3, taus=(0.1, 0.05, 0.02, 0.01))), 0.22, 0.4)
    return out


def mark(seed=7271):
    """A star over a target: a small high ting."""
    out = silence(0.5)
    place(out, norm(chime(note("A6"), 0.45, taus=(0.18, 0.07, 0.03, 0.01))), 0.0, 1.0)
    return out


def beam(seed=7281):
    """The Constellation's beam: a bright sweep and a sung shimmer."""
    r = rng(seed)
    out = silence(1.0)
    v = np.exp(-timeline(0.5) / 0.15)
    place(out, norm(whoosh(r, v, 1200.0, 8000.0, width=0.5, whistle=0.6)), 0.0, 0.7)
    place(out, norm(choir_stab(r, 0.7, [note("D5"), note("A5")], tau=0.25, singers=2)), 0.0, 0.35)
    return reverb(out, r, wet=0.2, t60=1.2)


def charge():
    """The rings spinning up: a shimmering whirr on D5 and A5 (1 s, every partial whole cycles)."""
    t = timeline(CHARGE_SECONDS)
    whirr = 0.55 + 0.45 * np.sin(TWO_PI * 12.0 * t)
    tone = np.sin(TWO_PI * 587.0 * t) + 0.6 * np.sin(TWO_PI * 880.0 * t + 1.0) + 0.3 * np.sin(TWO_PI * 1174.0 * t + 2.0)
    r = rng(7291)
    n = n_of(CHARGE_SECONDS)
    spec = np.fft.rfft(r.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / 44100)
    spec *= np.exp(-((np.log2(np.maximum(freqs, 1)) - np.log2(4500.0)) ** 2) / 0.5)
    air = np.fft.irfft(spec, n)
    return norm(tone) * (0.5 + 0.5 * whirr) + 0.3 * norm(air) * whirr


def supernova(seed=7301):
    """The choir hit: a sung D major over a deep bell, a soft boom, a bright tail."""
    r = rng(seed)
    out = silence(2.6)
    place(out, norm(choir_stab(r, 1.6, [note("D4"), note("F#4"), note("A4"), note("D5")], formants=AH_BRIGHT, singers=3,
                               tau=0.45, hold=0.12)), 0.0, 1.0)
    place(out, norm(chime(note("D3"), 2.2, taus=(1.0, 0.5, 0.2, 0.08))), 0.0, 0.6)
    place(out, norm(thump(0.8, 90.0, 35.0, 0.06, 0.25)), 0.0, 0.7)
    t = timeline(1.4)
    sparkle = shaped_noise(1.4, lambda tt, f: band(f, 7000.0, 0.8), r) * np.exp(-t / 0.4)
    place(out, norm(sparkle), 0.02, 0.2)
    return reverb(out, r, wet=0.3, t60=1.8, damp_hz=8000.0)


EVENTS = [
    Event("astrolabe/chime", "Astrolabe chimes", [star_chime], length=1.4, level=-4.0, fade_out=0.3),
    Event("astrolabe/flick", "Astrolabe rings flick", [flick], length=0.3, level=-11.0, fade_out=0.05),
    Event("astrolabe/bolt_hit", "Star bolt strikes", [bolt_hit], length=0.5, level=-5.0, fade_out=0.1),
    Event("astrolabe/star_place", "Pocket Star kindles", [star_place], length=1.6, level=-4.0, fade_out=0.3),
    Event("astrolabe/hum", "Pocket Star hums", [hum], length=HUM_SECONDS, level=-13.0, loop=True, quality=6),
    Event("astrolabe/pulse", "Pocket Star pulses", [pulse], length=0.7, level=-7.0, fade_out=0.15),
    Event("astrolabe/swallow", "Pocket Star swallows a shot", [swallow], length=0.6, level=-6.0, fade_out=0.1),
    Event("astrolabe/mark", "Star marks a target", [mark], length=0.5, level=-9.0, fade_out=0.1),
    Event("astrolabe/beam", "Constellation beam", [beam], length=1.2, level=-3.0, fade_out=0.25),
    Event("astrolabe/charge", "Astrolabe rings spin up", [charge], length=CHARGE_SECONDS, level=-10.0, loop=True, quality=6),
    Event("astrolabe/supernova", "Supernova", [supernova], length=3.0, level=0.0, fade_out=0.6),
]
