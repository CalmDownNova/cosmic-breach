"""The Hollow Stalker's sounds (GDD 7.1, "Sound and particles"): silent footsteps (none here), whispers that position in 3D,
a short shriek when the mask flares, a sigh and a glass crack on death; plus its Rend's rising whisper and gold ting, the
Rend itself, the Grasp, a Shadow Step's hush and its hurt.

The whispers are the committed takes in tools/sound/eleven/stalker/ (never fetched at build time): behind_you, dont_turn
and stay for the Grasp's whisper behind you, so_quiet, light_dying and there_you_are for the rare whispers while it
stalks. Each is made darker and breathier: a little slower and lower, high-passed at 140 Hz and low-passed at 5 kHz, a
breath of noise riding the words' own envelope, a short dark room. Levelled as speech (gated programme loudness) well
under the Starfall's voice: -25 LUFS, never too loud.
"""
from __future__ import annotations

from functools import partial

import numpy as np
from scipy.signal import resample_poly

import eleven
from dsp import band, fade, hp, lp, n_of, norm, note, phase_of, place, rng, shaped_noise, silence, timeline
from event import Event
from layers import GLASS, chime, crackle, reverb, shatter, thump, whoosh

WHISPER_LEVEL = -10.0   # LU from combat/hit's -15 LUFS: -25 LUFS programme loudness


def envelope(x, window=0.02):
    w = max(1, n_of(window))
    return np.sqrt(np.convolve(np.square(x), np.ones(w) / w, mode="same"))


def whisper(name: str, seed: int):
    """A take made darker and breathier, with a short dark room."""
    r = rng(seed)
    x = eleven.sample(f"stalker/{name}")
    x = resample_poly(x, 17, 16)                     # 6% slower and lower
    x = hp(x, 140.0, 2)
    x = lp(x, 5000.0, 2)
    env = envelope(x)
    air = shaped_noise(len(x) / 44100.0, lambda t, f: band(f, 3200.0, 1.3), r)[:len(x)]
    air = np.concatenate([air, np.zeros(max(0, len(x) - len(air)))])
    x = x + 0.45 * norm(air) * norm(env) * np.max(np.abs(x))
    x = np.concatenate([silence(0.05), x])
    x = reverb(x, r, wet=0.22, t60=1.3, damp_hz=3500.0)
    return fade(x, 0.01, 0.2)


def rising_whisper(seed=7101):
    """The Rend's tell: breath rising in pitch over a second, sharpening toward the strike."""
    r = rng(seed)
    t = timeline(1.0)
    out = silence(1.1)
    breath = shaped_noise(1.0, lambda tt, f: band(f, 700.0 + 3200.0 * np.clip(tt, 0, None) ** 1.5, 0.7), r)
    swell = np.clip(t / 0.9, 0, 1) ** 1.6
    place(out, norm(breath) * swell, 0.0, 0.8)
    hiss = np.sin(phase_of(900.0 * 2 ** (1.5 * t))) * swell * 0.15
    place(out, hiss, 0.0)
    return reverb(out, r, wet=0.2, t60=1.0, damp_hz=4000.0)


def glint(seed=7111):
    """The gold glint's rising ting (GDD 4.1: parryable), small and bright."""
    out = silence(0.6)
    place(out, norm(chime(note("A6"), 0.55, taus=(0.22, 0.09, 0.04, 0.02))), 0.0, 1.0)
    place(out, norm(chime(note("E7"), 0.4, taus=(0.12, 0.05, 0.03, 0.01))), 0.05, 0.45)
    return out


def rend(seed=7121):
    """A tearing swipe through the air, a dull void thump under it."""
    r = rng(seed)
    out = silence(0.8)
    v = np.exp(-timeline(0.3) / 0.06)
    place(out, norm(whoosh(r, v, 300.0, 4200.0, width=0.9, whistle=0.2)), 0.0, 0.9)
    tear = shaped_noise(0.25, lambda tt, f: band(f, 2200.0 - 1200.0 * tt, 1.2), r) * np.exp(-timeline(0.25) / 0.07)
    place(out, norm(tear), 0.03, 0.55)
    place(out, norm(thump(0.5, 110.0, 45.0, 0.05, 0.14)), 0.04, 0.7)
    return reverb(out, r, wet=0.15, t60=0.9, damp_hz=3000.0)


def grasp(seed=7131):
    """Cold fingers closing: cloth drawn tight and a low thrum."""
    r = rng(seed)
    out = silence(1.0)
    cloth = shaped_noise(0.35, lambda tt, f: band(f, 1400.0 + 900.0 * tt, 1.0), r) * np.exp(-timeline(0.35) / 0.12)
    place(out, norm(cloth), 0.0, 0.7)
    t = timeline(0.8)
    thrum = (np.sin(phase_of(58.0 + 6 * np.sin(2 * np.pi * 7 * t))) + 0.4 * np.sin(phase_of(np.full_like(t, 116.0)))) * np.exp(-t / 0.3)
    place(out, norm(thrum), 0.02, 0.8)
    return reverb(out, r, wet=0.18, t60=1.1, damp_hz=2500.0)


def shriek(seed=7141):
    """The mask flares: a short breathy shriek, up then away, a glassy edge on it."""
    r = rng(seed)
    t = timeline(0.45)
    out = silence(0.6)
    rise = np.minimum(t / 0.08, 1.0)
    fall = np.exp(-np.maximum(t - 0.08, 0) / 0.12)
    f0 = 1500.0 + 1300.0 * np.sin(np.pi * np.clip(t / 0.4, 0, 1))
    voice = np.zeros_like(t)
    for k, a in ((1, 1.0), (1.007, 0.8), (2.01, 0.35)):
        voice += a * np.sin(phase_of(f0 * k))
    place(out, norm(voice) * rise * fall, 0.0, 0.45)
    breath = shaped_noise(0.45, lambda tt, f: band(f, 3000.0 + 2000.0 * np.sin(np.pi * np.clip(tt / 0.4, 0, 1)), 0.8), r)
    place(out, norm(breath) * rise * fall, 0.0, 0.8)
    return reverb(out, r, wet=0.2, t60=1.0, damp_hz=5000.0)


def step(seed=7151):
    """A Shadow Step: an indrawn hush, the dark folding."""
    r = rng(seed)
    t = timeline(0.5)
    out = silence(0.6)
    env = np.sin(np.pi * np.clip(t / 0.45, 0, 1)) ** 2
    hush = shaped_noise(0.5, lambda tt, f: band(f, 500.0 + 1500.0 * tt, 1.4), r)
    place(out, norm(hush) * env, 0.0, 1.0)
    return reverb(out, r, wet=0.25, t60=1.2, damp_hz=2500.0)


def hurt(seed, f0):
    """A brittle porcelain tick and a hiss."""
    r = rng(seed)
    out = silence(0.4)
    place(out, norm(chime(f0, 0.3, ratios=GLASS, taus=(0.05, 0.03, 0.02, 0.01))), 0.0, 0.7)
    place(out, norm(crackle(r, n_events=4, spread=0.02, hp_hz=3000.0)), 0.006, 0.5)
    hiss = shaped_noise(0.3, lambda tt, f: band(f, 2600.0, 1.0), r) * np.exp(-timeline(0.3) / 0.08)
    place(out, norm(hiss), 0.01, 0.6)
    return fade(out, 0.004, 0.0)


def death(seed=7161):
    """A long sigh, then the mask cracks and shatters."""
    r = rng(seed)
    out = silence(2.2)
    t = timeline(1.6)
    env = np.minimum(t / 0.15, 1.0) * np.exp(-t / 0.6)
    sigh = shaped_noise(1.6, lambda tt, f: band(f, 900.0 - 350.0 * tt, 1.1) + 0.5 * band(f, 2500.0 - 600.0 * tt, 0.8), r)
    place(out, norm(sigh) * env, 0.0, 0.8)
    place(out, norm(crackle(r, n_events=10, spread=0.05, hp_hz=2500.0)), 0.55, 0.6)
    place(out, norm(shatter(r, 0.9, 40, f_lo=2500.0, f_hi=9000.0)), 0.62, 0.9)
    place(out, norm(chime(note("C#7"), 0.7, taus=(0.25, 0.1, 0.05, 0.02))), 0.62, 0.35)
    return reverb(out, r, wet=0.25, t60=1.4, damp_hz=4500.0)


EVENTS = [
    Event("stalker/whisper", "Something whispers", [partial(whisper, "so_quiet", 7001), partial(whisper, "light_dying", 7002),
                                                    partial(whisper, "there_you_are", 7003)],
          length=3.2, level=WHISPER_LEVEL, fade_out=0.3, speech=True, quality=6),
    Event("stalker/grasp_whisper", "A whisper behind you", [partial(whisper, "behind_you", 7011), partial(whisper, "dont_turn", 7012),
                                                            partial(whisper, "stay", 7013)],
          length=3.2, level=WHISPER_LEVEL, fade_out=0.3, speech=True, quality=6),
    Event("stalker/rend_tell", "Hollow Stalker hisses", [rising_whisper], length=1.3, level=-6.0, fade_out=0.15),
    Event("stalker/glint", "Hollow Stalker's claw glints", [glint], length=0.6, level=-5.0, fade_out=0.1),
    Event("stalker/rend", "Hollow Stalker rends", [rend], length=1.0, level=-1.0, fade_out=0.2),
    Event("stalker/grasp", "Hollow Stalker grasps", [grasp], length=1.2, level=-4.0, fade_out=0.2),
    Event("stalker/shriek", "Mask shrieks", [shriek], length=0.8, level=-4.0, fade_out=0.15),
    Event("stalker/step", "Something shifts in the dark", [step], length=0.8, level=-12.0, fade_out=0.15),
    Event("stalker/hurt", "Hollow Stalker hurts", [partial(hurt, 7171, 2300.0), partial(hurt, 7172, 2600.0)], length=0.4,
          level=-6.0, fade_out=0.08),
    Event("stalker/death", "Hollow Stalker sighs and cracks", [death], length=2.6, level=-2.0, fade_out=0.4),
]
