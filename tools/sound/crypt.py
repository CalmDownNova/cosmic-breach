"""The Hollow Crypt's sounds (W6, GDD 6.3, 6.4).

    choir/pad_1 .. pad_8   the eight pads' notes, D major pentatonic from D4 (D4 E4 F#4 A4 B4 D5 E5 F#5): a warm
                           crystal bell with a soft sung body, so a phrase sounds like a tune; subtitles
                           "Chime, first pad" and so on
    choir/call             the Conductor beginning a phrase: a hushed choir swell on D and A
    choir/tick             the metronome: a small woody click with a crystal tick on A6
    choir/discord          a Discord: two chimes a semitone apart over a dull thud, buzzing against each other
    choir/round            a round answered: a quick rising D major arpeggio of glass
    choir/solve            the Choir resolved: a sung D major chord over a low D and a falling cascade of chimes
    choir/rotate           the ring of pads turning: a stone grind under a glassy sweep
    crypt/rift_crack       a Void Rift springing: a sharp crack in stone with a violet shimmer over it
    crypt/rift_fall        the patch giving way: a collapse of stone and a hollow, falling rush into the void
    crypt/seal_open        a Rift Seal dissolving: crystal tinkling apart into a rising chime
    crypt/plate_hum        a Gravity Plate's hum: a low, heavy, beating drone from the ceiling
    crypt/piston           the Gravity Piston's slam: a huge thud, an iron clang, grit
    crypt/chute_glint      a star-rock's glint before it drops: one tiny high twinkle
    crypt/chute_impact     a star-rock landing: a thump, cracked stone and bright crystal chips

Every tuned part is in D major pentatonic like the rest of the set, except the Discord, whose clash is the point.

Run:  python tools/sound/build.py choir crypt
"""
from __future__ import annotations

import numpy as np

from dsp import ad, band, db, fade, n_of, norm, phase_of, place, rng, shaped_noise, silence, smoothstep, softclip, timeline, wobble
from event import Event
from layers import BAR, burst, chime, choir_stab, click, crackle, knock, reverb, shatter, tail, thump, whoosh

PADS = (293.66, 329.63, 369.99, 440.0, 493.88, 587.33, 659.26, 739.99)
D3, A3, D4, A4, D5, FS5, A5, D6, FS6, A6, D7 = (146.83, 220.0, 293.66, 440.0, 587.33, 739.99, 880.0, 1174.66, 1479.98, 1760.0,
                                                2349.32)
ORDINAL = ("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth")


def pad(i, length=1.3):
    """A pad's note: a crystal bell (bar modes, a slow shimmer) over a soft sung body on the same pitch."""
    f0 = PADS[i]
    r = rng(1100 + i)
    out = silence(length)
    bell = chime(f0, length, ratios=BAR, amps=(1.0, 0.18, 0.05, 0.02), taus=(0.55, 0.16, 0.06, 0.03),
                 beats=(1.2, 0.0, 0.0, 0.0), attack=0.002)
    place(out, bell, 0.0, db(0))
    octave = chime(f0 * 2, length, amps=(1.0, 0.1, 0.0, 0.0), taus=(0.25, 0.08, 0.03, 0.01), beats=(2.0, 0.0, 0.0, 0.0))
    place(out, octave, 0.0, db(-12))
    t = timeline(length)
    body = (np.sin(2 * np.pi * f0 * t) + 0.3 * np.sin(2 * np.pi * f0 * 2.0 * t + 0.7)) * ad(t, 0.03, 0.35)
    place(out, tail(body, longest=0.05), 0.0, db(-9))
    place(out, click(r, 0.001, 3000.0), 0.0, db(-18))
    return reverb(softclip(norm(out), 1.05), r, wet=0.32, t60=1.6, damp_hz=6000.0, hp_hz=120.0)


def call(seed=1201, length=1.8):
    r = rng(seed)
    stab = choir_stab(r, length, (D4, A4, D5), singers=3, detune_cents=7.0, scoop_cents=10.0, attack=0.25, hold=0.55,
                      tau=0.45, breath=0.08, vib_cents=6.0)
    return reverb(fade(norm(stab), 0.02, 0.3), r, wet=0.4, t60=2.2, damp_hz=5000.0, hp_hz=150.0)


def tick(seed=1211, length=0.22):
    r = rng(seed)
    out = silence(length)
    place(out, knock(r, 1650.0, tau=0.008, ratios=(1.0, 2.3), amps=(1.0, 0.3)), 0.0, db(-2))
    place(out, tail(chime(A6, 0.18, ratios=BAR, amps=(1.0, 0.1, 0.0, 0.0), taus=(0.05, 0.02, 0.01, 0.01))), 0.002, db(-6))
    place(out, click(r, 0.0012, 2000.0), 0.0, db(-8))
    return reverb(norm(out), r, wet=0.12, t60=0.4, damp_hz=8000.0, hp_hz=400.0)


def discord(seed=1221, length=1.0):
    r = rng(seed)
    out = silence(length)
    for hz, g in ((622.25, 0.0), (659.26, -1.0), (311.13, -4.0), (329.63, -5.0)):
        c = chime(hz, 0.9, ratios=BAR, amps=(1.0, 0.3, 0.1, 0.04), taus=(0.4, 0.12, 0.05, 0.02), beats=(7.0, 0.0, 0.0, 0.0))
        place(out, c, 0.0, db(-3 + g))
    place(out, thump(0.4, 110.0, 50.0, 0.05, 0.14, drive=2.0), 0.0, db(-2))
    t = timeline(0.5)
    buzz = shaped_noise(0.5, lambda tt, f: band(f, 640.0, 0.3), r) * ad(t, 0.005, 0.18) * (0.6 + 0.4 * wobble(r, len(t), 31.0))
    place(out, tail(buzz), 0.0, db(-10))
    return reverb(softclip(norm(out), 1.8), r, wet=0.2, t60=0.9, damp_hz=5000.0, hp_hz=90.0)


def round_done(seed=1231, length=1.5):
    r = rng(seed)
    out = silence(length)
    for i, hz in enumerate((D5, FS5, A5, D6)):
        place(out, tail(chime(hz, 1.2, ratios=BAR, amps=(1.0, 0.15, 0.04, 0.0), taus=(0.6, 0.2, 0.07, 0.03))), 0.07 * i,
              db(-3 - 0.8 * i))
    place(out, burst(r, 0.06, 0.001, 0.02, lo=5000.0, hi=12000.0), 0.21, db(-16))
    return reverb(softclip(norm(out), 1.1), r, wet=0.35, t60=1.8, damp_hz=7000.0, hp_hz=150.0)


def solve(seed=1241, length=3.0):
    r = rng(seed)
    out = silence(length)
    sung = choir_stab(r, 2.6, (D4, FS5 / 2, A4, D5), singers=4, detune_cents=8.0, attack=0.08, hold=0.9, tau=0.7, breath=0.06)
    place(out, sung, 0.05, db(-2))
    t = timeline(2.6)
    low = np.sin(2 * np.pi * D3 * t) * ad(t, 0.1, 0.9, hold=0.6)
    place(out, tail(low, longest=0.1), 0.0, db(-6))
    for i, hz in enumerate((D7, A6, FS6, D6, A5, FS5, D5)):
        place(out, tail(chime(hz, 1.2, ratios=BAR, amps=(1.0, 0.12, 0.03, 0.0), taus=(0.5, 0.15, 0.05, 0.02))), 0.3 + 0.09 * i,
              db(-9 - 0.4 * i))
    return reverb(fade(softclip(norm(out), 1.1), 0.005, 0.4), r, wet=0.38, t60=2.6, damp_hz=6000.0, hp_hz=60.0)


def rotate(seed=1251, length=0.9):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.7)
    grind = shaped_noise(0.7, lambda tt, f: band(f, 260.0 + 120.0 * tt, 1.1), r)
    place(out, tail(norm(grind) * ad(t, 0.08, 0.3, hold=0.2), longest=0.05), 0.0, db(-5))
    st = timeline(0.6)
    sweep = np.sin(phase_of(880.0 * 2.0 ** (st / 0.6))) * ad(st, 0.05, 0.25)
    place(out, tail(sweep), 0.1, db(-12))
    place(out, knock(r, 180.0, tau=0.05), 0.55, db(-6))
    return reverb(softclip(norm(out), 1.2), r, wet=0.25, t60=1.2, damp_hz=5000.0, hp_hz=80.0)


def rift_crack(seed=1301, length=0.7):
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.002, 900.0), 0.0, db(0))
    place(out, crackle(r, 18, 0.09, 1200.0, 7000.0, grain=0.0009, falloff=0.55), 0.0, db(-2))
    place(out, knock(r, 240.0, tau=0.04), 0.0, db(-5))
    for i, hz in enumerate((1174.66 * 2, 1479.98 * 2)):
        place(out, tail(chime(hz, 0.5, taus=(0.25, 0.1, 0.04, 0.02), beats=(6.0, 0.0, 0.0, 0.0))), 0.03 + 0.03 * i, db(-14))
    return reverb(softclip(norm(out), 1.4), r, wet=0.22, t60=1.0, damp_hz=6000.0, hp_hz=120.0)


def rift_fall(seed=1311, length=1.6):
    r = rng(seed)
    out = silence(length)
    place(out, crackle(r, 40, 0.35, 600.0, 5000.0, grain=0.0012, falloff=0.8), 0.0, db(-4))
    place(out, thump(0.6, 80.0, 35.0, 0.08, 0.25, drive=1.5), 0.02, db(-2))
    t = timeline(1.3)
    v = 1.0 - smoothstep(np.clip(t / 1.3, 0, 1)) * 0.8
    rush = whoosh(r, v, 150.0, 1400.0, width=1.1, whistle=0.2, air=0.5, air_hz=2500.0, body=0.6, body_hz=90.0)
    place(out, tail(norm(rush) * (1.0 - smoothstep(np.clip((t - 0.9) / 0.4, 0, 1))), longest=0.05), 0.12, db(-5))
    return reverb(fade(softclip(norm(out), 1.3), 0.002, 0.3), r, wet=0.3, t60=1.8, damp_hz=3500.0, hp_hz=40.0)


def seal_open(seed=1321, length=1.5):
    r = rng(seed)
    out = silence(length)
    place(out, shatter(r, 0.9, 40, f_lo=2000.0, f_hi=8000.0), 0.0, db(-8))
    for i, hz in enumerate((A5, D6, FS6, A6)):
        place(out, tail(chime(hz, 1.0, taus=(0.45, 0.2, 0.08, 0.03))), 0.2 + 0.1 * i, db(-6 - i))
    return reverb(softclip(norm(out), 1.1), r, wet=0.35, t60=1.6, damp_hz=7000.0, hp_hz=200.0)


def plate_hum(seed=1331, length=1.8):
    r = rng(seed)
    t = timeline(length)
    hum = np.zeros_like(t)
    for hz, g in ((55.0, 1.0), (55.6, 0.8), (110.0, 0.5), (110.9, 0.35), (165.0, 0.18)):
        hum += g * np.sin(2 * np.pi * hz * t)
    grit = shaped_noise(length, lambda tt, f: band(f, 180.0, 0.6), r)
    env = smoothstep(np.clip(t / 0.3, 0, 1)) * (1.0 - smoothstep(np.clip((t - 1.2) / 0.6, 0, 1)))
    out = (norm(hum) + db(-14) * norm(grit)) * env
    return fade(norm(out), 0.01, 0.2)


def piston(seed=1341, length=1.1):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.9, 70.0, 28.0, 0.05, 0.35, drive=2.5), 0.0, db(0))
    place(out, knock(r, 95.0, tau=0.2, ratios=(1.0, 2.76, 5.4), amps=(1.0, 0.5, 0.2)), 0.0, db(-4))
    place(out, crackle(r, 30, 0.2, 800.0, 6000.0, grain=0.001, falloff=0.7), 0.01, db(-8))
    place(out, burst(r, 0.3, 0.001, 0.08, lo=200.0, hi=3000.0), 0.0, db(-6))
    return reverb(softclip(norm(out), 1.8), r, wet=0.25, t60=1.4, damp_hz=3000.0, hp_hz=30.0)


def chute_glint(seed=1351, length=0.6):
    r = rng(seed)
    out = silence(length)
    place(out, tail(chime(D7 * 2, 0.5, ratios=BAR, amps=(1.0, 0.1, 0.0, 0.0), taus=(0.2, 0.05, 0.01, 0.01),
                          beats=(9.0, 0.0, 0.0, 0.0))), 0.0, db(0))
    place(out, tail(chime(A6 * 2, 0.4, taus=(0.12, 0.04, 0.01, 0.01))), 0.04, db(-6))
    return reverb(norm(out), r, wet=0.35, t60=1.0, damp_hz=12000.0, hp_hz=2000.0)


def chute_impact(seed=1361, length=0.9):
    r = rng(seed)
    out = silence(length)
    place(out, thump(0.5, 130.0, 50.0, 0.04, 0.15, drive=2.0), 0.0, db(0))
    place(out, crackle(r, 24, 0.12, 900.0, 6000.0, grain=0.0008, falloff=0.6), 0.0, db(-3))
    place(out, shatter(r, 0.5, 18, f_lo=3000.0, f_hi=9000.0), 0.01, db(-9))
    place(out, tail(chime(FS6, 0.5, ratios=BAR, taus=(0.2, 0.06, 0.02, 0.01))), 0.02, db(-12))
    return reverb(softclip(norm(out), 1.6), r, wet=0.18, t60=0.9, damp_hz=5000.0, hp_hz=60.0)


EVENTS = [Event(f"choir/pad_{i + 1}", f"Chime, {ORDINAL[i]} pad", [lambda i=i: pad(i)], length=1.3, level=-6.0, fade_out=0.25)
          for i in range(8)] + [
    Event("choir/call", "The Conductor sings", [call], length=1.8, level=-9.0, fade_out=0.3),
    Event("choir/tick", "Metronome ticks", [tick], length=0.22, level=-12.0),
    Event("choir/discord", "Discord", [discord], length=1.0, level=-3.0, fade_out=0.2),
    Event("choir/round", "The Choir answers", [round_done], length=1.5, level=-4.0, fade_out=0.3),
    Event("choir/solve", "The Choir Floor resolves", [solve], length=3.0, level=-2.0, fade_out=0.4),
    Event("choir/rotate", "The ring of pads turns", [rotate], length=0.9, level=-6.0, fade_out=0.2),
    Event("crypt/rift_crack", "Floor cracks", [rift_crack], length=0.7, level=-3.0, fade_out=0.15),
    Event("crypt/rift_fall", "The floor gives way", [rift_fall], length=1.6, level=-2.0, fade_out=0.3),
    Event("crypt/seal_open", "Rift Seal opens", [seal_open], length=1.5, level=-4.0, fade_out=0.3),
    Event("crypt/plate_hum", "The ceiling hums", [plate_hum], length=1.8, level=-10.0, fade_out=0.2),
    Event("crypt/piston", "Gravity Piston slams", [piston], length=1.1, level=0.0, fade_out=0.2),
    Event("crypt/chute_glint", "Something glints above", [chute_glint], length=0.6, level=-12.0, fade_out=0.15),
    Event("crypt/chute_impact", "Star-rock lands", [chute_impact], length=0.9, level=-3.0, fade_out=0.2),
]
