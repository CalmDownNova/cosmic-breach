"""The accessories' sounds (G6a, GDD 5.2).

    accessory/equip        an accessory fastened on: a small metal click and a bright crystal ting on D6 and A6
    accessory/comet_chain  the Twin Comet Band's level air dash: a quick bright rush and a rising glass sparkle
    accessory/leech_heal   the Leechstar Signet drinking a parry: a reversed shimmer swelling into a warm D and A hum
    accessory/gravity_well the Gravity Loop's well where a plunge lands: a reversed sweep into a low, rising violet hum
    accessory/nova         the Heart of a Dying Star bursting: a boom with a sub, a bright D major bell burst, embers
    accessory/halo_block   a Halo shard stopping a projectile: a tink of crystal breaking and its fragments falling
    accessory/halo_regrow  a Halo shard growing back: a soft rising A, D, F# of glass
    accessory/halo_cut     a Halo shard cutting: a thin quick slash and a high glass ting
    accessory/black_hole   the Event Horizon Lens's black hole: air sucked inward into a deep, soft thoom
    accessory/vesper_slow  the Hourglass of Vesper: a low bell whose pitch sinks as it rings, sand hissing through

Every tuned part in D (the mod's key). Levels (offsets from combat/hit's -15 LUFS): nova +2, black hole 0, well,
halo block and slow -2, equip, comet and leech -3, cut -5, regrow -8.

Run:  python tools/sound/build.py accessory
"""
from __future__ import annotations

import numpy as np

from dsp import SR, ad, band, db, fade, n_of, norm, place, rng, shaped_noise, silence, softclip, timeline, wobble
from event import Event
from layers import BAR, burst, chime, choir_stab, click, crackle, glass_grain, granular_hiss, reverb, shatter, tail, thump, whoosh

D2, A2, D3, A3, D4, FS4, A4, D5, FS5, A5, D6, FS6, A6, D7 = (73.42, 110.0, 146.83, 220.0, 293.66, 369.99, 440.0, 587.33,
                                                         739.99, 880.0, 1174.66, 1479.98, 1760.0, 2349.32)


def ting(hz, seconds=0.9, level=0.0):
    """A clear crystal-rod ting on `hz`."""
    return tail(chime(hz, seconds, ratios=BAR, amps=(1.0, 0.18, 0.05, 0.0), taus=(0.35, 0.12, 0.05, 0.02))) * db(level)


def equip(seed=6101, length=0.8):
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.002, 1500.0), 0.0, db(-4))
    place(out, tail(chime(D5 * 1.5, 0.12, ratios=(1.0, 2.7, 5.1), amps=(1.0, 0.5, 0.2), taus=(0.02, 0.012, 0.006))), 0.0, db(-8))
    place(out, ting(D6, 0.7), 0.03, db(-2))
    place(out, ting(A6, 0.6), 0.09, db(-7))
    return reverb(fade(softclip(norm(out), 1.1), 0.001, 0.2), r, wet=0.22, t60=1.0, damp_hz=7000.0, hp_hz=200.0)


def comet_chain(seed=6111, length=0.7):
    r = rng(seed)
    out = silence(length)
    n = n_of(0.35)
    v = np.clip(np.linspace(0.0, 1.6, n), 0.0, 1.0) * np.clip(np.linspace(1.4, 0.0, n), 0.0, 1.0)
    place(out, tail(whoosh(r, v, 700.0, 4200.0, width=0.7, whistle=0.8, air=0.7, body=0.1)), 0.0, db(-3))
    for i, hz in enumerate((A5, D6, FS6, A6)):
        place(out, ting(hz, 0.45), 0.05 + 0.045 * i, db(-10 - 1.5 * i))
    place(out, granular_hiss(r, 0.4, lo=5000.0, rate=90.0, tau=0.1), 0.04, db(-18))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.2), r, wet=0.25, t60=1.0, damp_hz=8000.0, hp_hz=250.0)


def leech_heal(seed=6121, length=1.1):
    r = rng(seed)
    out = silence(length)
    shimmer = norm(granular_hiss(r, 0.55, lo=3000.0, rate=70.0, tau=0.12) + 0.4 * ting(A6, 0.55))
    place(out, tail(shimmer[::-1].copy(), longest=0.05), 0.0, db(-6))
    t = timeline(0.8)
    hum = (np.sin(2 * np.pi * D3 * 2 * t) + 0.6 * np.sin(2 * np.pi * A3 * 2 * t)
           + 0.25 * np.sin(2 * np.pi * D5 * t)) * ad(t, 0.08, 0.3)
    place(out, tail(hum, longest=0.1), 0.42, db(-3))
    place(out, ting(D6, 0.5), 0.45, db(-9))
    return reverb(fade(softclip(norm(out), 1.1), 0.02, 0.3), r, wet=0.3, t60=1.4, damp_hz=6000.0, hp_hz=120.0)


def gravity_well(seed=6131, length=1.5):
    r = rng(seed)
    out = silence(length)
    sweep = norm(shaped_noise(0.6, lambda tt, f: band(f, 400.0 + 2500.0 * (tt / 0.6), 0.9), r)) * ad(timeline(0.6), 0.02, 0.25)
    place(out, tail(sweep[::-1].copy(), longest=0.05), 0.0, db(-4))
    t = timeline(0.9)
    rise = np.sin(2 * np.pi * np.cumsum(D2 * (1.0 + 0.5 * t / 0.9)) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(A2 * (1.0 + 0.5 * t / 0.9)) / SR)
    place(out, tail(rise * ad(t, 0.05, 0.4, hold=0.2), longest=0.1), 0.5, db(-2))
    place(out, thump(0.4, 90.0, 45.0, 0.05, 0.15, drive=1.2), 0.55, db(-7))
    return reverb(fade(softclip(norm(out), 1.2), 0.01, 0.3), r, wet=0.3, t60=1.6, damp_hz=4000.0, hp_hz=40.0)


def nova(seed=6141, length=2.4):
    r = rng(seed)
    out = silence(length)
    place(out, thump(1.2, 120.0, 38.0, 0.06, 0.4, drive=2.0), 0.0, db(0))
    place(out, tail(burst(r, 0.6, 0.002, 0.12, lo=300.0, hi=6000.0)), 0.0, db(-5))
    for i, hz in enumerate((D5, FS5, A5, D6)):
        bell = chime(hz, 1.8, ratios=(1.0, 2.0, 2.76, 4.1), amps=(1.0, 0.4, 0.2, 0.08), taus=(0.8, 0.4, 0.2, 0.1),
                     beats=(1.2, 0.8, 0.0, 0.0), attack=0.002)
        place(out, tail(bell), 0.01 + 0.012 * i, db(-6 - i))
    for k in range(4):
        place(out, crackle(r, n_events=8, spread=0.2, hp_hz=2500.0), 0.15 + 0.3 * k, db(-18 - 2 * k))
    return reverb(fade(softclip(norm(out), 1.3), 0.001, 0.5), r, wet=0.35, t60=2.0, damp_hz=6000.0, hp_hz=35.0)


def halo_block(seed=6151, length=0.8):
    r = rng(seed)
    out = silence(length)
    place(out, click(r, 0.0015, 2500.0), 0.0, db(-3))
    place(out, shatter(r, 0.6, 14, f_lo=3000.0, f_hi=11000.0), 0.0, db(-3))
    place(out, ting(A6, 0.5), 0.0, db(-6))
    place(out, ting(D7, 0.35), 0.01, db(-11))
    return reverb(fade(softclip(norm(out), 1.1), 0.001, 0.2), r, wet=0.2, t60=0.9, damp_hz=9000.0, hp_hz=300.0)


def halo_regrow(seed=6161, length=0.9):
    r = rng(seed)
    out = silence(length)
    for i, hz in enumerate((A5, D6, FS6)):
        place(out, ting(hz, 0.6), 0.08 * i, db(-3 - 2 * i))
    place(out, granular_hiss(r, 0.35, lo=6000.0, rate=50.0, tau=0.12), 0.0, db(-20))
    return reverb(fade(norm(out), 0.004, 0.3), r, wet=0.35, t60=1.3, damp_hz=8000.0, hp_hz=300.0)


def halo_cut(seed=6171, length=0.4):
    r = rng(seed)
    out = silence(length)
    n = n_of(0.12)
    v = np.sin(np.linspace(0.0, np.pi, n)) ** 2
    place(out, tail(whoosh(r, v, 2500.0, 7000.0, width=0.5, whistle=0.6, air=0.8)), 0.0, db(-4))
    place(out, ting(FS6, 0.3), 0.05, db(-5))
    place(out, glass_grain(r, 5200.0, tau=0.03), 0.05, db(-12))
    return reverb(fade(softclip(norm(out), 1.1), 0.001, 0.1), r, wet=0.15, t60=0.6, damp_hz=9000.0, hp_hz=400.0)


def black_hole(seed=6181, length=1.6):
    r = rng(seed)
    out = silence(length)
    t = timeline(0.9)
    suck = norm(shaped_noise(0.9, lambda tt, f: band(f, 3000.0 - 2600.0 * (tt / 0.9), 1.0), r))
    suck *= np.clip(t / 0.9, 0.0, 1.0) ** 2
    place(out, tail(suck, longest=0.03), 0.0, db(-3))
    place(out, thump(0.9, 70.0, 32.0, 0.1, 0.35, drive=1.4), 0.86, db(0))
    t2 = timeline(0.7)
    hum = (np.sin(2 * np.pi * D2 * t2) + 0.5 * np.sin(2 * np.pi * D3 * t2)) * ad(t2, 0.01, 0.25)
    place(out, tail(hum, longest=0.1), 0.86, db(-5))
    return reverb(fade(softclip(norm(out), 1.2), 0.01, 0.3), r, wet=0.3, t60=1.5, damp_hz=3500.0, hp_hz=30.0)


def vesper_slow(seed=6191, length=1.8):
    r = rng(seed)
    out = silence(length)
    t = timeline(1.6)
    sink = 1.0 - 0.18 * (1.0 - np.exp(-t / 0.5))  # the bell's pitch sinks as it rings, like a clock running down
    phase = 2 * np.pi * np.cumsum(sink) / SR
    bell = (np.sin(D4 * phase) + 0.5 * np.sin(D4 * 2.0 * phase) + 0.3 * np.sin(D4 * 2.76 * phase)
            + 0.15 * np.sin(D4 * 4.1 * phase)) * ad(t, 0.003, 0.7)
    place(out, tail(bell, longest=0.1), 0.0, db(-1))
    place(out, click(r, 0.002, 900.0), 0.0, db(-12))
    sand = granular_hiss(r, 1.2, lo=2500.0, rate=140.0, tau=0.5) * (0.6 + 0.4 * wobble(r, n_of(1.2), 4.0))
    place(out, tail(sand, longest=0.2), 0.05, db(-16))
    return reverb(fade(softclip(norm(out), 1.1), 0.002, 0.4), r, wet=0.35, t60=2.2, damp_hz=5000.0, hp_hz=60.0)


EVENTS = [
    Event("accessory/equip", "Accessory fastens", [equip], length=0.8, level=-3.0, fade_out=0.2),
    Event("accessory/comet_chain", "Comet trail flares", [comet_chain], length=0.7, level=-3.0, fade_out=0.2),
    Event("accessory/leech_heal", "Leechstar drinks", [leech_heal], length=1.1, level=-3.0, fade_out=0.3),
    Event("accessory/gravity_well", "Gravity Well opens", [gravity_well], length=1.5, level=-2.0, fade_out=0.3),
    Event("accessory/nova", "Dying star bursts", [nova], length=2.4, level=2.0, fade_out=0.5),
    Event("accessory/halo_block", "Halo shard shatters", [halo_block], length=0.8, level=-2.0, fade_out=0.2),
    Event("accessory/halo_regrow", "Halo shard grows back", [halo_regrow], length=0.9, level=-8.0, fade_out=0.3),
    Event("accessory/halo_cut", "Halo shard cuts", [halo_cut], length=0.4, level=-5.0, fade_out=0.1),
    Event("accessory/black_hole", "Black hole opens", [black_hole], length=1.6, level=0.0, fade_out=0.3),
    Event("accessory/vesper_slow", "Hourglass sand falls", [vesper_slow], length=1.8, level=-2.0, fade_out=0.4),
]
