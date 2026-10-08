"""The Prism Colossus's sounds (Prism Colossus design v1, "Look and sound"), and its boss loop.

A crystal giant: grinding crystal when it turns, a deep glass chime on the slam and a high ting at the parry
glint, a rising tone through the Refraction charge and a glassy hum while the beam burns, a roar of breaking
glass at the Fracture, a burst of breaking glass at the Shatter, the shards' chitter, and a major chord when it
dies. The boss loop is 8 bars at Vesper's 100 BPM in D minor (i, VI, VII, v), starting on its downbeat at 0 s
so the game can retrigger it on the Vesper clock's bar lines; it rings 1.6 s past its 19.2 s into the next.

Recorded takes (tools/sound/eleven/colossus, fetched once by eleven.py, never at build time) carry the voice of
the big moments: roar_take1 is the awakening's groan (slowed, deeper) and the intro's roar, roar_take2 the
Fracture, shatter.wav the Shatter's explosion and falling shards. The synthesis stays under them for weight: the
roar's low grinding growl and sub, a crack and a boom on the Shatter's first instant, its chimes.
"""
from __future__ import annotations

from fractions import Fraction
from functools import partial

import numpy as np
from scipy.signal import resample_poly

import eleven
from aetheria import BEAT, bass, bell, chord_notes, env, pad, pluck, render, sub
from dsp import TWO_PI, ad, band, db, fade, hp, lp, n_of, norm, note, phase_of, place, rise, rng, shaped_noise, silence, timeline
from event import Event
from layers import BAR, GLASS, burst, chime, click, crackle, glass_grain, granular_hiss, modes, reverb, shatter, thump, whoosh


def grind(seed=1101, length=1.0, pitch=1.0):
    """Crystal grinding on crystal: a scraped, grainy rasp with glassy squeaks, swelling and settling."""
    r = rng(seed)
    t = timeline(length)
    swell = np.sin(np.pi * np.clip(t / length, 0, 1)) ** 0.8

    def mag(tt, f):
        return band(f, 900.0 * pitch * (1 + 0.3 * np.sin(TWO_PI * 3.0 * tt)), 1.2) + 0.4 * band(f, 3200.0 * pitch, 0.6)

    rasp = shaped_noise(length, mag, r) * swell
    chatter = np.abs(np.sin(phase_of(38.0 + 20.0 * swell))) ** 6
    out = norm(rasp * (0.5 + 0.5 * chatter))
    for _ in range(9):
        when = r.uniform(0.05, length - 0.15)
        place(out, glass_grain(r, r.uniform(1800, 4200) * pitch, tau=0.03), when, 0.35 * r.uniform(0.4, 1.0))
    place(out, norm(thump(0.4, 90 * pitch, 55 * pitch, 0.08, 0.15)), 0.02, 0.4)
    return reverb(out, r, wet=0.18, t60=0.9)


def crystal_turn(seed=1111):
    """A crown crystal turns a step: a clack, a short grind, a bright settling ting."""
    r = rng(seed)
    out = silence(0.55)
    place(out, norm(click(r, 0.001, 800.0)), 0.0, 0.9)
    place(out, grind(seed + 1, 0.3, 1.4), 0.005, 0.7)
    place(out, norm(chime(note("A6"), 0.4, ratios=BAR, taus=(0.18, 0.08, 0.04, 0.02))), 0.22, 0.6)
    return out


def glint(seed=1121):
    """The parry cue: a high, clean crystal ting with a gold shimmer, unmistakable over the fight."""
    r = rng(seed)
    out = silence(0.55)
    place(out, norm(chime(note("D7"), 0.55, ratios=BAR, amps=(1.0, 0.35, 0.12, 0.05), taus=(0.22, 0.1, 0.05, 0.02),
                          beats=(3.0, 0.0, 0.0, 0.0))), 0.0)
    place(out, norm(chime(note("A7"), 0.35, ratios=BAR, taus=(0.12, 0.06, 0.03, 0.01))), 0.012, 0.45)
    place(out, norm(granular_hiss(r, 0.3, lo=7000.0, tau=0.06)), 0.0, 0.18)
    return reverb(out, r, wet=0.12, t60=0.6)


def slam(seed=1131, pitch=1.0):
    """The slam: a deep glass chime struck by a falling crystal fist, a thump under it, shards skittering."""
    r = rng(seed)
    out = silence(1.5)
    place(out, norm(thump(0.8, 120 * pitch, 42 * pitch, 0.05, 0.3, drive=1.6)), 0.0, 1.0)
    place(out, norm(chime(note("D3") * pitch, 1.4, ratios=GLASS, amps=(1.0, 0.4, 0.15, 0.06), taus=(0.9, 0.4, 0.2, 0.1),
                          beats=(0.8, 1.7, 0.0, 0.0))), 0.004, 0.8)
    place(out, norm(chime(note("A3") * pitch, 1.0, ratios=GLASS, taus=(0.6, 0.3, 0.15, 0.08))), 0.006, 0.45)
    place(out, norm(shatter(r, 0.8, 30, f_lo=2200, f_hi=8000, amp_tau=0.2)), 0.01, 0.35)
    place(out, norm(burst(r, 0.3, 0.002, 0.07, lo=200.0, hi=2500.0)), 0.0, 0.4)
    return reverb(out, r, wet=0.22, t60=1.4, damp_hz=5000.0)


def sweep(seed=1141):
    """The Facet Sweep: a huge crystal arm cutting the air, a glassy ring riding its edge."""
    r = rng(seed)
    t = timeline(0.8)
    v = np.exp(-((t - 0.28) / 0.13) ** 2)
    out = norm(whoosh(r, v, 250.0, 1800.0, width=1.0, whistle=0.6, air=0.5, body=0.8, body_hz=120.0))
    ring = modes(0.8, [1320.0, 1320 * 2.32, 1320 * 4.25], (1.0, 0.3, 0.1), (0.3, 0.15, 0.07))
    place(out, norm(ring) * 0.3, 0.2)
    return reverb(out, r, wet=0.15, t60=0.8)


def charge(seed=1151, length=2.0):
    """Refraction's charge: a tone rising a fifth over 2 s, glass partials gathering, a shimmer building."""
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / length, 0, 1)
    f = note("D4") * 1.5 ** (u ** 1.4)
    ph = phase_of(f)
    tone = sum(a * np.sin(q * ph) for q, a in zip(GLASS, (1.0, 0.45, 0.2, 0.1)))
    trem = 1.0 - 0.35 * (0.5 + 0.5 * np.sin(phase_of(4.0 + 18.0 * u)))
    out = norm(tone * (0.2 + 0.8 * u ** 1.5) * trem * env(t, length, 0.08, 0.05))
    shimmer = shaped_noise(length, lambda tt, ff: band(ff, 3000.0 + 5000.0 * np.clip(tt / length, 0, 1), 0.5), r)
    place(out, norm(shimmer * u ** 2) * 0.3, 0.0)
    return reverb(out, r, wet=0.2, t60=1.0)


def beam(seed=1161, length=2.0):
    """The beam burning: a glassy hum on D with a buzzing edge and a bright crackle, steady for 2 s."""
    r = rng(seed)
    t = timeline(length)
    f = note("D3")
    ph = phase_of(f * (1 + 0.004 * np.sin(TWO_PI * 5.5 * t)))
    hum = sum(a * np.sin(k * ph) for k, a in ((1, 1.0), (2, 0.5), (3, 0.35), (5, 0.2), (8, 0.12)))
    glass = modes(length, [note("A5"), note("D6"), note("F#6")], (0.4, 0.3, 0.2), (length, length, length))
    out = norm(hum) * env(t, length, 0.05, 0.25)
    place(out, norm(glass) * env(t, length, 0.1, 0.3) * 0.35, 0.0)
    fizz = shaped_noise(length, lambda tt, ff: band(ff, 6000.0, 0.8), r) * (0.5 + 0.5 * np.abs(np.sin(phase_of(np.full(len(t), 23.0)))))
    place(out, norm(fizz) * env(t, length, 0.05, 0.3) * 0.25, 0.0)
    return reverb(out, r, wet=0.15, t60=0.8)


def core_hit(seed=1171):
    """The beam turned back into its core: a bright crystal crash and a cry of glass falling away."""
    r = rng(seed)
    out = silence(1.3)
    place(out, norm(thump(0.6, 160, 60, 0.04, 0.2, drive=2.0)), 0.0, 0.8)
    place(out, norm(shatter(r, 1.2, 60, f_lo=1800, f_hi=11000, amp_tau=0.4)), 0.0, 0.8)
    place(out, norm(chime(note("F#5"), 1.2, taus=(0.7, 0.3, 0.15, 0.07))), 0.005, 0.5)
    place(out, norm(chime(note("D5"), 1.2, taus=(0.7, 0.3, 0.15, 0.07))), 0.008, 0.45)
    return reverb(out, r, wet=0.25, t60=1.4)


def burst_tell(seed=1181, length=0.8):
    """A Prism Burst building: a white-hot hiss and a tone climbing fast."""
    r = rng(seed)
    t = timeline(length)
    u = np.clip(t / length, 0, 1)
    ph = phase_of(note("A4") * 2 ** (u * 1.2))
    tone = np.sin(ph) + 0.4 * np.sin(2.32 * ph)
    hiss = shaped_noise(length, lambda tt, ff: band(ff, 2500.0 + 7000.0 * np.clip(tt / length, 0, 1), 0.7), r)
    out = norm(tone * u ** 1.6) + 0.5 * norm(hiss * u ** 2)
    return out * rise(t, 0.02)


def burst_blast(seed=1191):
    """The Prism Burst: a crystal blast pushing out, a flash of shards."""
    r = rng(seed)
    out = silence(1.0)
    place(out, norm(thump(0.6, 140, 50, 0.05, 0.2, drive=2.0)), 0.0, 0.9)
    place(out, norm(burst(r, 0.5, 0.002, 0.12, lo=300, hi=6000)), 0.0, 0.6)
    place(out, norm(shatter(r, 0.8, 40, f_lo=2500, f_hi=9000, amp_tau=0.25)), 0.005, 0.5)
    return reverb(out, r, wet=0.2, t60=1.0)


def break_(seed=1201):
    """A Break: a deep crack through the crystal and a chime falling a fourth as it slumps."""
    r = rng(seed)
    out = silence(1.6)
    place(out, norm(crackle(r, n_events=14, spread=0.05, hp_hz=800.0)), 0.0, 0.8)
    place(out, norm(thump(0.7, 100, 40, 0.06, 0.25, drive=1.5)), 0.01, 0.9)
    t = timeline(1.4)
    ph = phase_of(note("E4") * 0.75 ** np.clip(t / 0.8, 0, 1))
    fall = sum(a * np.sin(q * ph) for q, a in zip(GLASS, (1.0, 0.3, 0.1))) * np.exp(-t / 0.5)
    place(out, norm(fall) * 0.6, 0.05)
    return reverb(out, r, wet=0.25, t60=1.2)


def take(name, seconds, fade_out, speed=1.0, lowpass=None, highpass=None):
    """A recorded take shaped as a layer: played at `speed` (under 1: lower and slower), filtered, cut to `seconds`
    with a fade out (5 ms in, so it starts clean)."""
    x = eleven.sample(name)
    if speed != 1.0:
        f = Fraction(speed).limit_denominator(24)
        x = resample_poly(x, f.denominator, f.numerator)
    if highpass:
        x = hp(x, highpass, 2)
    if lowpass:
        x = lp(x, lowpass, 2)
    n = n_of(seconds)
    x = np.concatenate([x[:n], np.zeros(max(0, n - len(x)))])
    return fade(x, 0.005, fade_out)


def growl(seed, length, low=1.0):
    """The synthesized roar's low end alone, its grinding growl and a sub, to give a recorded roar its weight."""
    r = rng(seed)
    t = timeline(length)
    shape = np.clip(t / 0.25, 0, 1) * np.exp(-np.maximum(t - 1.2, 0) / 0.6)

    def mag(tt, f):
        return band(f, 160.0 * low * (1 + 0.4 * np.sin(TWO_PI * 7.0 * tt)), 1.0) + 0.5 * band(f, 700.0 * low, 0.8)

    out = norm(shaped_noise(length, mag, r) * shape)
    place(out, norm(sub(45.0 * low, 1.4)), 0.0, 0.6)
    return out


def voiced_roar(seed, length, name, low=1.0, speed=1.0, lowpass=8000.0, waves=2):
    """A roar with a recorded voice: the take over the synthesized growl, a few waves of breaking glass between."""
    r = rng(seed)
    out = silence(length)
    place(out, norm(take(name, length, 0.6, speed=speed, lowpass=lowpass)), 0.0, 1.0)
    place(out, growl(seed, length, low), 0.0, 0.55)
    for k in range(waves):
        place(out, norm(shatter(r, 1.0, 28, f_lo=2000, f_hi=9000, amp_tau=0.3)), 0.15 + 0.45 * k, 0.25)
    return reverb(out, r, wet=0.2, t60=1.6)


def awaken(seed=1301):
    """The statue wakes: the recorded roar slowed to a deeper, longer groan over the growl."""
    return voiced_roar(seed, 3.0, "colossus/roar_take1", low=0.7, speed=0.78, lowpass=4500.0, waves=1)


def intro_roar(seed=1311):
    return voiced_roar(seed, 2.2, "colossus/roar_take1")


def fracture(seed=1221):
    return voiced_roar(seed, 3.0, "colossus/roar_take2", low=0.9, lowpass=7500.0, waves=3)


def shatter_all(seed=1231):
    """Shatter, the peak of the fight: the recorded glass explosion and its falling shards, with a sharp crack and a
    deep boom on its first instant, synthesized glass under it and three bright notes flying out (the game ducks
    the boss loop under it, so it sounds alone)."""
    r = rng(seed)
    out = silence(3.2)
    t0 = 0.003  # a breath of silence first, so the file starts clean
    place(out, norm(take("colossus/shatter", 2.7, 1.1)), t0, 1.0)
    place(out, norm(crackle(r, n_events=12, spread=0.025, hp_hz=1800.0, falloff=0.75)), t0, 0.5)
    place(out, norm(click(r, 0.002, hp_hz=600.0)), t0, 0.45)
    place(out, norm(thump(1.0, 170, 36, 0.06, 0.45, drive=2.8)), t0 + 0.012, 0.7)
    place(out, norm(shatter(r, 2.0, 120, f_lo=1500, f_hi=12000, burst_frac=0.6, burst_len=0.08, amp_tau=0.5)), t0 + 0.004, 0.35)
    for i, n in enumerate(("D6", "F#6", "A6")):
        place(out, norm(chime(note(n), 1.2, ratios=BAR, taus=(0.5, 0.2, 0.1, 0.05))), 0.05 + 0.07 * i, 0.3)
    return reverb(out, r, wet=0.2, t60=1.8)


def reform(seed=1241):
    """The shards re-merge: glass swept up backwards into a swelling chord."""
    r = rng(seed)
    length = 2.5
    glass = shatter(r, 1.6, 60, f_lo=2000, f_hi=9000, amp_tau=0.5)[::-1]
    out = silence(length)
    place(out, norm(glass), 0.0, 0.7)
    chord = sum(norm(chime(note(n), 1.4, taus=(0.8, 0.3, 0.15, 0.07))) for n in ("D4", "A4", "F5"))
    place(out, norm(chord), 1.5, 0.8)
    place(out, norm(thump(0.5, 120, 50, 0.05, 0.2)), 1.5, 0.6)
    return reverb(out, r, wet=0.3, t60=1.6)


def death(seed=1251):
    """The Colossus dies: the pillar's hum sinks away and a D major chord rises, bells over a choir-like pad."""
    r = rng(seed)
    length = 5.0
    out = silence(length)
    t = timeline(1.2)
    ph = phase_of(note("D3") * 0.8 ** np.clip(t / 1.0, 0, 1))
    place(out, norm(np.sin(ph) * np.exp(-t / 0.5)) * 0.5, 0.0)
    place(out, norm(pad(chord_notes(["D3", "A3", "D4", "F#4"]), 4.4, attack=1.4, release=1.6, seed=31)), 0.5, 0.8)
    for i, n in enumerate(("D5", "F#5", "A5", "D6")):
        place(out, norm(bell(note(n), 3.5)), 0.9 + 0.35 * i, 0.55)
    return reverb(out, r, wet=0.35, t60=2.4)


def hurt(seed, f0):
    r = rng(seed)
    out = silence(0.35)
    place(out, norm(modes(0.3, [f0, f0 * 1.52, f0 * 2.43], (1.0, 0.5, 0.2), (0.12, 0.07, 0.04))), 0.0)
    place(out, norm(crackle(r, n_events=5, spread=0.02, hp_hz=2000.0)), 0.0, 0.5)
    place(out, norm(thump(0.2, 180, 90, 0.03, 0.06)), 0.0, 0.5)
    return out


def chitter(seed):
    """The shards' chitter: a quick run of glass clicks, like claws on crystal."""
    r = rng(seed)
    out = silence(0.4)
    t = 0.0
    for i in range(int(r.integers(5, 9))):
        t += r.uniform(0.02, 0.05)
        place(out, glass_grain(r, r.uniform(2500, 6000), tau=0.012, tick=0.6), t, r.uniform(0.5, 1.0))
    return out


def shard_tell(seed=1261):
    """A shard readies its lunge: a small gold ting climbing a fourth."""
    r = rng(seed)
    t = timeline(0.5)
    ph = phase_of(note("E6") * (4 / 3) ** np.clip(t / 0.45, 0, 1))
    tone = sum(a * np.sin(q * ph) for q, a in zip(BAR, (1.0, 0.25, 0.08)))
    return norm(tone * (0.4 + 0.6 * np.clip(t / 0.45, 0, 1)) * np.exp(-np.maximum(t - 0.45, 0) / 0.02) * rise(t, 0.002))


def shard_lunge(seed=1271):
    r = rng(seed)
    t = timeline(0.25)
    v = np.exp(-((t - 0.08) / 0.05) ** 2)
    return norm(whoosh(r, v, 600.0, 3000.0, whistle=0.8, air=0.4))


def shard_death(seed=1281):
    r = rng(seed)
    out = silence(0.6)
    place(out, norm(shatter(r, 0.55, 40, f_lo=2500, f_hi=10000, amp_tau=0.2)), 0.0)
    place(out, norm(thump(0.2, 200, 100, 0.03, 0.06)), 0.0, 0.4)
    return reverb(out, r, wet=0.12, t60=0.6)


def echo(seed=1291):
    """A Guardian Echo on the altar: a chime answered by itself, again and fainter, and a low swell."""
    r = rng(seed)
    out = silence(2.2)
    for i, g in enumerate((1.0, 0.55, 0.3, 0.16)):
        place(out, norm(chime(note("A5"), 0.8, ratios=BAR, taus=(0.4, 0.15, 0.06, 0.02))), 0.01 + 0.3 * i, g)
        place(out, norm(chime(note("D6"), 0.8, ratios=BAR, taus=(0.4, 0.15, 0.06, 0.02))), 0.01 + 0.3 * i + 0.15, g * 0.6)
    t = timeline(2.0)
    place(out, norm(np.sin(TWO_PI * note("D3") * t) * env(t, 2.0, 0.8, 0.8)) * 0.4, 0.01)
    return reverb(out, r, wet=0.35, t60=2.0)


# ------------------------------------------------------------------ the boss loop
PHRASE_BARS = 8
TAIL = 1.6


def drum(seconds, f0=70.0, seed=0):
    """A deep taiko-like hit: a pitched thump and a skin slap."""
    r = rng(seed)
    x = norm(thump(seconds, f0 * 1.6, f0, 0.03, 0.22, drive=1.4))
    place(x, norm(burst(r, 0.08, 0.001, 0.02, lo=300.0, hi=3000.0)), 0.0, 0.25)
    return x


def boss_loop():
    """8 bars at 100 BPM in D minor: i - VI - VII - v, two bars each. Drums on 1 and 3 with a pickup, a pulsing
    eighth-note bass, a crystal ostinato in sixteenths, a pad swelling each chord, a bell line over the last four."""
    seconds = PHRASE_BARS * 4 * BEAT + TAIL
    chords = [(["D3", "A3", "D4", "F4"], "D2"), (["A#2", "F3", "A#3", "D4"], "A#1"),
              (["C3", "G3", "C4", "E4"], "C2"), (["A2", "E3", "A3", "C4"], "A1")]
    ostinato = {
        0: ["D5", "A5", "F5", "A5"], 1: ["D5", "A#5", "F5", "A#5"], 2: ["C5", "G5", "E5", "G5"], 3: ["A4", "E5", "C5", "E5"]}
    score = []
    for bar in range(PHRASE_BARS):
        c = (bar // 2) % 4
        chord, root = chords[c]
        b0 = bar * 4
        if bar % 2 == 0:
            score.append((b0, 8.4, (lambda sec, ch=chord, s=bar: pad(chord_notes(ch), sec, attack=0.3, release=0.9, bright=7.0, seed=40 + s)), -12))
        for e in range(8):
            score.append((b0 + e * 0.5, 0.5, (lambda sec, f=note(root): bass(f, sec)), -10 if e % 2 == 0 else -13))
        score.append((b0, 1.2, (lambda sec: drum(sec, 62.0, 1)), -6))
        score.append((b0 + 2, 1.2, (lambda sec: drum(sec, 70.0, 2)), -8))
        if bar % 2 == 1:
            score.append((b0 + 3.5, 0.8, (lambda sec: drum(sec, 80.0, 3)), -11))
        for s in range(16):
            n = ostinato[c][s % 4]
            score.append((b0 + s * 0.25, 0.9, (lambda sec, f=note(n): pluck(f, sec, bright=1.6, tau=0.35)), -18 if s % 4 else -15))
    melody = [("A5", 16, 2), ("F5", 18, 1), ("G5", 19, 1), ("A5", 20, 2), ("C6", 22, 2), ("A#5", 24, 3), ("A5", 27, 1),
              ("G5", 28, 2), ("E5", 30, 2)]
    for n, s, ln in melody:
        score.append((s, ln + 2.0, (lambda sec, f=note(n): bell(f, sec)), -10))
    x = render(score, seconds, 7)
    x = reverb(x, rng(77), wet=0.22, t60=1.6, damp_hz=7000.0, hp_hz=120.0)[:n_of(seconds)]
    return x


EVENTS = [
    Event("colossus/awaken", "Prism Colossus wakes", [awaken], length=3.0, level=1.0, fade_out=0.6),
    Event("colossus/roar", "Prism Colossus roars", [intro_roar], length=2.2, level=2.0, fade_out=0.5),
    Event("colossus/grind", "Crystal grinds", [partial(grind, 1101), partial(grind, 1102, 1.0, 0.85)], length=1.0, level=-7.0,
          fade_out=0.2),
    Event("colossus/crystal_turn", "Crown crystal turns", [crystal_turn], length=0.55, level=-1.0, fade_out=0.1),
    Event("colossus/glint", "Gold glint", [glint], length=0.55, level=1.0, fade_out=0.1),
    Event("colossus/slam", "Prism Slam", [partial(slam, 1131), partial(slam, 1132, 0.94)], length=1.5, level=3.0, fade_out=0.4),
    Event("colossus/sweep", "Facet Sweep", [sweep], length=0.8, level=0.0, fade_out=0.15),
    Event("colossus/charge", "Refraction charges", [charge], length=2.0, level=-1.0, fade_out=0.15),
    Event("colossus/beam", "Refraction beam hums", [beam], length=2.0, level=-1.0, fade_out=0.3),
    Event("colossus/core_hit", "Beam strikes the core", [core_hit], length=1.3, level=3.0, fade_out=0.4),
    Event("colossus/burst_tell", "Prism Colossus glows white", [burst_tell], length=0.8, level=-2.0, fade_out=0.03),
    Event("colossus/burst", "Prism Burst", [burst_blast], length=1.0, level=2.0, fade_out=0.3),
    Event("colossus/break", "Prism Colossus breaks", [break_], length=1.6, level=2.0, fade_out=0.4),
    Event("colossus/fracture", "Prism Colossus fractures", [fracture], length=3.0, level=3.0, fade_out=0.7),
    Event("colossus/shatter", "Prism Colossus shatters", [shatter_all], length=3.2, level=6.0, fade_out=0.9),
    Event("colossus/reform", "Prism Shards re-merge", [reform], length=2.5, level=1.0, fade_out=0.5),
    # level 2.5 dB down from 2.0 (1.1.0 final pass): the kill line sits under this sound and must stay 3 LU over it (test_colossus_death.py)
    Event("colossus/death", "Prism Colossus falls", [death], length=5.0, level=-0.5, fade_out=1.2),
    Event("colossus/hurt", "Prism Colossus hurts", [partial(hurt, 1321, 1300.0), partial(hurt, 1322, 1550.0),
                                                     partial(hurt, 1323, 1100.0)], length=0.35, level=-4.0, fade_out=0.08),
    Event("colossus/shard_chitter", "Prism Shard chitters", [partial(chitter, 1331), partial(chitter, 1332), partial(chitter, 1333)],
          length=0.4, level=-9.0, fade_out=0.05),
    Event("colossus/shard_tell", "Prism Shard readies a lunge", [shard_tell], length=0.5, level=-2.0, fade_out=0.03),
    Event("colossus/shard_lunge", "Prism Shard lunges", [shard_lunge], length=0.25, level=-5.0, fade_out=0.03),
    Event("colossus/shard_hurt", "Prism Shard hurts", [partial(hurt, 1341, 2600.0), partial(hurt, 1342, 3100.0)], length=0.35,
          level=-5.0, fade_out=0.06),
    Event("colossus/shard_death", "Prism Shard shatters", [shard_death], length=0.6, level=0.0, fade_out=0.15),
    Event("colossus/echo", "Guardian Echo resounds", [echo], length=2.2, level=-1.0, fade_out=0.5),
    Event("music/colossus", "Music: the Prism Colossus", [boss_loop], length=PHRASE_BARS * 4 * BEAT + TAIL, level=-3.0,
          fade_out=1.2, quality=2),
]
