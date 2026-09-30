"""Combat sounds: the player's star-metal weapon, movement and feedback cues.

Tuning: every tuned chime in the set is drawn from D major pentatonic
(D E F# A B), so cues that overlap in play (crit, parry ring, charge ready,
dodge, resonance) never clash with each other.
"""
from __future__ import annotations

from functools import partial

import numpy as np

from dsp import (SR, TWO_PI, ad, band, bump, circular_filter, db, fade, hp_shape, lp, lp_shape,
                 n_of, norm, note, place, place_wrap, rng, shaped_noise, silence, smoothstep,
                 softclip, tilt, timeline, wobble)
from event import Event
from layers import (BAR, blade_ring, burst, chime, choir_stab, click, crackle, glass_grain,
                    granular_hiss, knock, modes, reverb, reverb_wrap, shatter, shepard_choir,
                    tail, thump, whoosh)


# ---------------------------------------------------------------------------
# Swings

def swing_light(seed, peak_at, f_lo, f_hi, ring_hz, length=0.19):
    """Quick sharp whoosh with a faint star-metal shimmer."""
    r = rng(seed)
    v = bump(timeline(length), 0.0, length, peak_at, 8.0)
    air = whoosh(r, v, f_lo, f_hi, width=0.75, whistle=1.2, whistle_width=0.055,
                 whistle_ratio=1.12, air=0.6, amp_pow=1.4)
    ring = blade_ring(r, v, ring_hz, linger=0.045)
    out = silence(length)
    place(out, norm(air))
    place(out, norm(ring), 0.0, db(-24))
    return out


def swing_heavy(seed, peak_at, f_lo, f_hi, ring_hz, length=0.31):
    """Longer, lower whoosh with the push of displaced air and some turbulence."""
    r = rng(seed)
    v = bump(timeline(length), 0.0, length, peak_at, 6.0)
    air = whoosh(r, v, f_lo, f_hi, width=0.95, whistle=0.7, whistle_width=0.07,
                 whistle_ratio=1.1, air=0.35, body=0.8, body_hz=130.0, flutter=0.3,
                 flutter_hz=30.0, amp_pow=1.2)
    ring = blade_ring(r, v, ring_hz, ratios=(1.0, 1.52, 2.23), amps=(1.0, 0.5, 0.25), linger=0.07)
    out = silence(length)
    place(out, norm(air))
    place(out, norm(ring), 0.0, db(-27))
    return out


# ---------------------------------------------------------------------------
# Hits

def hit_core(r, ring_hz, ring_ratios, thump_hz, knock_hz, length,
             crack_events=7, crack_hp=2400.0, bright=0.0):
    """The blade connecting: a crisp crystalline crack over a short low thump."""
    out = silence(length)
    t0 = 0.0015
    place(out, thump(0.2, thump_hz[0], thump_hz[1], 0.012, 0.026, attack=0.0015, drive=1.2),
          t0, db(-4))
    place(out, knock(r, knock_hz, tau=0.018), t0, db(-4))
    place(out, crackle(r, crack_events, 0.014, crack_hp), t0, db(0 + bright))
    place(out, click(r, 0.0012, 800.0), t0 + 0.0003, db(-6))
    ring = modes(0.25, [ring_hz * q for q in ring_ratios], (1.0, 0.7, 0.5, 0.35, 0.25),
                 (0.06, 0.045, 0.035, 0.025, 0.02))
    place(out, norm(ring), t0 + 0.0004, db(-9 + bright))
    place(out, granular_hiss(r, 0.08, lo=6000.0, rate=90.0, attack=0.0005, tau=0.012),
          t0, db(-13 + bright))
    return out


def hit(seed, ring_hz, ring_ratios, thump_hz, knock_hz, length=0.25):
    r = rng(seed)
    out = softclip(norm(hit_core(r, ring_hz, ring_ratios, thump_hz, knock_hz, length)), 1.3)
    return reverb(out, r, wet=0.10, t60=0.3, damp_hz=8000.0, hp_hz=300.0)


def hit_crit(seed=311, length=0.5):
    """A brighter hit with a high glassy chime ringing on top."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.0015
    place(out, hit_core(r, 3300.0, (1.0, 1.56, 2.27, 2.94, 3.71), (158.0, 50.0), 450.0, 0.3,
                        crack_events=9, crack_hp=2800.0, bright=2.0))
    place(out, norm(chime(note("D7"), length, amps=(1.0, 0.3, 0.12, 0.05),
                          taus=(0.14, 0.07, 0.035, 0.02), beats=(2.1, 3.3, 0.0, 0.0))),
          t0 + 0.001, db(-6))
    place(out, norm(chime(note("A7"), 0.35, amps=(1.0, 0.25, 0.08), taus=(0.1, 0.05, 0.025),
                          beats=(2.7, 0.0, 0.0))), t0 + 0.014, db(-12))
    for _ in range(4):
        place(out, glass_grain(r, r.uniform(6000.0, 10000.0), tau=r.uniform(0.02, 0.04)),
              t0 + r.uniform(0.004, 0.07), db(-20))
    out = softclip(norm(out), 1.3)
    return reverb(out, r, wet=0.16, t60=0.8, damp_hz=10000.0, hp_hz=500.0)


# ---------------------------------------------------------------------------
# Charged attack

def charge_loop(seed=401, length=2.0):
    """A distant choir "ahh" in parallel fifths that rises forever and loops seamlessly."""
    r = rng(seed)
    spacing, f_lo, f_hi = 1.5, 90.0, 1400.0
    choir = norm(shepard_choir(r, length, spacing=spacing, f_lo=f_lo, f_hi=f_hi))
    n = len(choir)
    T = n / SR
    # Sparkles two octaves above whichever voice is prominent at that moment,
    # wrapped around the loop so they never cut off at the seam.
    sparkle = np.zeros(n)
    lo2, hi2 = np.log2(f_lo), np.log2(f_hi)
    for _ in range(14):
        when = r.uniform(0.0, T)
        f_nom = f_lo * spacing ** (np.arange(8) + when / T)
        u = (np.log2(f_nom) - lo2) / (hi2 - lo2)
        w = np.where((u > 0.0) & (u < 1.0), 0.5 - 0.5 * np.cos(TWO_PI * u), 0.0)
        f0 = 4.0 * r.choice(f_nom, p=w / w.sum())
        g = chime(f0, 0.3, amps=(1.0, 0.2, 0.06), taus=(0.07, 0.035, 0.018),
                  beats=(1.5, 0.0, 0.0), attack=0.002)
        place_wrap(sparkle, norm(g), when, r.uniform(0.5, 1.0))
    mix = choir + db(-20) * sparkle
    mix = circular_filter(mix, lambda f: lp_shape(f, 5000.0, 2) * hp_shape(f, 90.0, 2))
    mix = reverb_wrap(mix, r, wet=0.35, t60=1.4, damp_hz=5000.0, hp_hz=150.0)
    return mix - np.mean(mix)


def charge_ready(seed=402, length=0.62):
    """One clear bell-like "ting" (A6, a free-bar chime)."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.001
    bell = chime(note("A6"), length, ratios=BAR, amps=(1.0, 0.28, 0.09, 0.03),
                 taus=(0.15, 0.065, 0.03, 0.015), beats=(1.9, 3.4, 0.0, 0.0))
    place(out, norm(bell), t0)
    place(out, click(r, 0.0006, 4000.0), t0, db(-16))
    return reverb(out, r, wet=0.2, t60=0.9, damp_hz=9000.0, hp_hz=800.0)


def charge_release(seed=403, length=0.72):
    """A big whoosh with a bright sung chord hit (D major) and a low boom under it."""
    r = rng(seed)
    out = silence(length)
    t = timeline(length)
    v = bump(t, 0.0, 0.38, 0.3, 5.0)
    air = whoosh(r, v, 250.0, 2600.0, width=1.0, whistle=0.6, whistle_ratio=1.1, air=0.6,
                 body=0.7, body_hz=120.0, flutter=0.25, amp_pow=1.2)
    place(out, norm(air), 0.0, db(-3))
    hit_t = 0.075
    chord = [note(n) for n in ("D3", "A3", "D4", "F#4", "A4", "D5", "A5")]
    place(out, norm(choir_stab(r, 0.6, chord)), hit_t)
    place(out, norm(chime(note("D7"), 0.5, amps=(1.0, 0.3, 0.1), taus=(0.12, 0.06, 0.03),
                          beats=(2.5, 0.0, 0.0))), hit_t, db(-7))
    place(out, norm(chime(note("A7"), 0.4, amps=(1.0, 0.25, 0.08), taus=(0.09, 0.045, 0.02))),
          hit_t + 0.012, db(-15))
    place(out, thump(0.4, 100.0, 45.0, 0.03, 0.09, drive=1.4), hit_t, db(-6))
    place(out, crackle(r, 8, 0.012, 3000.0), hit_t, db(-8))
    return reverb(out, r, wet=0.25, t60=1.1, damp_hz=7000.0, hp_hz=250.0)


# ---------------------------------------------------------------------------
# Plunge

def plunge_whistle(seed=404, length=0.8):
    """Air whistling past a diving blade: a breathy tone falling in pitch over rising wind."""
    r = rng(seed)
    t = timeline(length)
    u = t / length

    def whistle_mag(tt, f):
        c = 2300.0 * (950.0 / 2300.0) ** (np.clip(tt / length, 0.0, 1.0) ** 1.3)
        return band(f, c, 0.035) + 0.35 * band(f, 2.0 * c, 0.03)

    def wind_mag(tt, f):
        uu = np.clip(tt / length, 0.0, 1.0)
        return lp_shape(f, 500.0 + 2500.0 * uu ** 2, 2) * hp_shape(f, 120.0, 2) * tilt(f, -2.0)

    whistle = shaped_noise(length, whistle_mag, r, nfft=2048)
    wind = shaped_noise(length, wind_mag, r) * (1.0 + 0.3 * wobble(r, len(t), 12.0))
    tail = 0.85

    def shape(p):
        return np.where(u < tail, (u / tail) ** p,
                        0.5 + 0.5 * np.cos(np.pi * (u - tail) / (1.0 - tail)))

    return norm(whistle * shape(1.5)) + db(-3) * norm(wind * shape(1.1))


def plunge_impact(seed=405, length=0.9):
    """A heavy ground boom with a crystalline shatter on top."""
    r = rng(seed)
    out = silence(length)
    t0 = 0.0015
    place(out, thump(0.7, 100.0, 36.0, 0.04, 0.1, attack=0.002, drive=1.6), t0)
    place(out, thump(0.3, 170.0, 75.0, 0.015, 0.045, drive=1.5), t0, db(-4))
    place(out, burst(r, 0.3, 0.0005, 0.035, hi=350.0), t0, db(-3))
    rt = timeline(0.8)
    rumble = shaped_noise(0.8, lambda tt, f: lp_shape(f, 160.0, 3) * hp_shape(f, 30.0, 2), r)
    place(out, tail(norm(rumble * ad(rt, 0.01, 0.22)), longest=0.1), t0, db(-8))
    place(out, crackle(r, 10, 0.025, 1500.0), t0, db(-1))
    place(out, click(r, 0.0015, 600.0), t0, db(-4))
    place(out, norm(shatter(r, length, 80, 2200.0, 10500.0, burst_frac=0.4, burst_len=0.05,
                            t_tau=0.16, amp_tau=0.22, tau_range=(0.02, 0.09))),
          t0 + 0.002, db(-4))
    place(out, granular_hiss(r, 0.5, lo=3500.0, rate=60.0, attack=0.001, tau=0.09), t0, db(-10))
    out = softclip(norm(out), 1.4)
    return reverb(out, r, wet=0.18, t60=0.9, damp_hz=7000.0, hp_hz=350.0)


# ---------------------------------------------------------------------------
# Ability, movement, defence, feedback

def zenith(seed=406, length=0.62):
    """An upward shimmering sweep: rising air plus a fast glass run up the pentatonic scale."""
    r = rng(seed)
    out = silence(length)
    t = timeline(length)
    sweep = 0.5

    def mag(tt, f):
        s = smoothstep(tt / sweep)
        fc = 350.0 * (4800.0 / 350.0) ** s
        return band(f, fc, 0.8) + 0.5 * s * hp_shape(f, 7000.0, 2) * lp_shape(f, 16000.0, 2)

    swish = shaped_noise(length, mag, r) * bump(t, 0.0, 0.56, 0.62, 5.0)
    place(out, norm(swish), 0.0, db(-2))
    names = ["D6", "E6", "F#6", "A6", "B6", "D7", "E7", "F#7", "A7", "B7"]
    run = silence(length)
    for i, name in enumerate(names):
        frac = i / (len(names) - 1)
        when = 0.03 + 0.36 * frac ** 0.75
        ring = 1.8 if i == len(names) - 1 else 1.0
        c = chime(note(name), length - when, amps=(1.0, 0.22, 0.07),
                  taus=(0.09 * ring, 0.045, 0.022), beats=(2.0 + 0.3 * i, 0.0, 0.0), attack=0.0008)
        place(run, norm(c), when, 0.55 + 0.45 * frac)
    place(out, norm(run))
    for k in range(12):
        when = 0.1 + 0.4 * (k / 11) ** 0.8 + r.uniform(-0.02, 0.02)
        f0 = r.uniform(5000.0, 7000.0) * (1.0 + 0.5 * k / 11)
        place(out, glass_grain(r, f0, tau=r.uniform(0.02, 0.04), tick=0.0), when,
              db(-14) * r.uniform(0.5, 1.0))
    lt = timeline(0.3)
    lift = shaped_noise(0.3, lambda tt, f: band(f, 150.0, 0.7), r) * bump(lt, 0.0, 0.3, 0.35, 4.0)
    place(out, norm(lift), 0.0, db(-9))
    return reverb(out, r, wet=0.25, t60=0.9, damp_hz=10000.0, hp_hz=500.0)


def dash(seed, f_start, f_end, width, body, length=0.2):
    """A short rush of air with a fast attack; the band falls as the air streams past."""
    r = rng(seed)
    t = timeline(length)

    def mag(tt, f):
        ts = np.maximum(tt, 0.0)
        fc = f_end + (f_start - f_end) * np.exp(-ts / 0.06)
        hiss = 0.6 * np.exp(-ts / 0.03) * hp_shape(f, 5000.0, 2) * lp_shape(f, 15000.0, 2)
        push = body * band(f, 140.0, 0.6) * np.exp(-ts / 0.05)
        return band(f, fc, width) + hiss + push

    x = shaped_noise(length, mag, r)
    env = ad(t, 0.008, 0.055, hold=0.015) * (1.0 + 0.25 * wobble(r, len(t), 35.0))
    return x * env


def parry(seed=408, length=0.82):
    """A bright metallic clang, a punch, and a star-metal bell ringing on.

    The bell's partials spell D major (hum D5, prime D6, major-third tierce,
    quint, nominal D7), so the ring is sweet rather than sour, and its nominal
    rings longest so the tail stays bright.
    """
    r = rng(seed)
    out = silence(length)
    t0 = 0.0015
    n = 40
    freqs = np.exp(r.uniform(np.log(1500.0), np.log(12000.0), n))
    amps = r.uniform(0.3, 1.0, n) * (freqs / 2000.0) ** -0.2
    taus = 0.045 * (2500.0 / freqs) ** 0.5 * r.uniform(0.7, 1.3, n)
    place(out, norm(modes(0.4, freqs, amps, taus)), t0)
    ratios = np.array([0.5, 1.0, 1.25, 1.5, 2.0, 2.52, 3.0, 4.0]) * r.uniform(0.997, 1.003, 8)
    bell = modes(length, note("D6") * ratios,
                 (0.3, 0.8, 0.35, 0.3, 1.0, 0.4, 0.3, 0.2),
                 (0.18, 0.26, 0.16, 0.15, 0.3, 0.14, 0.12, 0.09),
                 beats=(0.0, 1.3, 0.0, 2.1, 1.7, 2.9, 0.0, 3.3))
    place(out, norm(bell), t0, db(-1))
    place(out, thump(0.3, 170.0, 70.0, 0.015, 0.05, drive=1.8), t0, db(-4))
    place(out, burst(r, 0.1, 0.0005, 0.012, lo=3000.0), t0, db(-8))
    place(out, click(r, 0.0012, 1500.0), t0, db(-6))
    out = softclip(norm(out), 1.6)
    return reverb(out, r, wet=0.14, t60=1.0, damp_hz=9000.0, hp_hz=500.0)


def perfect_dodge(seed=409, length=0.52):
    """A soft glass dyad (E6 + B6) that a short reversed swell sucks into."""
    r = rng(seed)
    out = silence(length)
    swell = 0.13

    def dyad(seconds):
        a = chime(note("E6"), seconds, amps=(1.0, 0.12, 0.03), taus=(0.13, 0.06, 0.03),
                  beats=(1.7, 0.0, 0.0), attack=0.003)
        b = chime(note("B6"), seconds, amps=(1.0, 0.1, 0.02), taus=(0.11, 0.05, 0.025),
                  beats=(2.3, 0.0, 0.0), attack=0.003)
        return norm(a + db(-3) * b)

    k = n_of(swell)
    rev = dyad(0.3)[::-1][-k:] * np.linspace(0.0, 1.0, k) ** 1.5
    st = timeline(swell)
    hiss = shaped_noise(swell, lambda tt, f: band(f, 3000.0 + 30000.0 * np.clip(tt, 0.0, None), 0.6), r)
    hiss = fade(hiss * (st / swell) ** 2.5, 0.0, 0.004)
    place(out, norm(rev), 0.0, db(-5))
    place(out, norm(hiss), 0.0, db(-12))
    place(out, dyad(length - swell), swell)
    return reverb(out, r, wet=0.3, t60=0.9, damp_hz=7000.0, hp_hz=600.0)


def resonance_full(seed=410, length=0.8):
    """A quiet warm hum (D3, slowly beating) with a small sparkle of high chimes."""
    r = rng(seed)
    t = timeline(length)
    out = silence(length)
    f0 = note("D3")
    hum = np.zeros_like(t)
    for h, a in zip((1, 2, 3, 4), (1.0, 0.55, 0.22, 0.1)):
        hum += a * np.sin(TWO_PI * h * f0 * t + r.uniform(0.0, TWO_PI))
        hum += 0.6 * a * np.sin(TWO_PI * h * (f0 + 1.3) * t + r.uniform(0.0, TWO_PI))
    hum = lp(hum, 1500.0) * ad(t, 0.12, 0.2, hold=0.15)
    place(out, norm(hum))
    for when, name in ((0.05, "D8"), (0.085, "A7"), (0.12, "F#8")):
        c = chime(note(name), 0.35, amps=(1.0, 0.2, 0.05), taus=(0.07, 0.035, 0.018),
                  beats=(2.2, 0.0, 0.0), attack=0.001)
        place(out, norm(c), when, db(-8))
    shimmer = (np.sin(TWO_PI * note("A8") * t) + 0.6 * np.sin(TWO_PI * note("D9") * t))
    shimmer *= (1.0 + 0.5 * np.sin(TWO_PI * 13.0 * t)) * ad(t, 0.05, 0.15)
    place(out, norm(shimmer), 0.0, db(-22))
    return reverb(out, r, wet=0.2, t60=0.8, damp_hz=6000.0, hp_hz=200.0)


def whiff(seed=411, length=0.2):
    """A dull short swish: low, narrow and muffled, no ring."""
    r = rng(seed)
    v = bump(timeline(length), 0.0, length, 0.38, 6.0)
    x = whoosh(r, v, 380.0, 950.0, width=0.85, whistle=0.3, whistle_ratio=1.1, air=0.05, amp_pow=1.3)
    return lp(x, 2200.0, 2)


# ---------------------------------------------------------------------------

EVENTS = [
    Event("combat/swing_light", "Blade swings",
          [partial(swing_light, 101, 0.40, 700.0, 3300.0, 5600.0),
           partial(swing_light, 102, 0.46, 800.0, 3800.0, 6300.0),
           partial(swing_light, 103, 0.36, 620.0, 2900.0, 5100.0)],
          length=0.19, level=-3.0, fade_out=0.02),
    Event("combat/swing_heavy", "Blade swings heavily",
          [partial(swing_heavy, 201, 0.45, 320.0, 1700.0, 3400.0),
           partial(swing_heavy, 202, 0.50, 280.0, 1500.0, 3050.0)],
          length=0.31, level=-2.0, fade_out=0.035),
    Event("combat/hit", "Blade strikes",
          [partial(hit, 301, 3100.0, (1.0, 1.53, 2.21, 2.87, 3.64), (150.0, 52.0), 430.0),
           partial(hit, 302, 3500.0, (1.0, 1.47, 2.34, 3.02, 3.90), (140.0, 50.0), 390.0),
           partial(hit, 303, 2800.0, (1.0, 1.61, 2.13, 2.95, 3.52), (160.0, 55.0), 470.0)],
          length=0.25, level=0.0, fade_out=0.04),
    Event("combat/hit_crit", "Critical strike", [hit_crit], length=0.5, level=2.5, fade_out=0.12),
    Event("combat/charge_loop", "Blade charges", [charge_loop], length=2.0, level=-9.0, loop=True,
          quality=8),
    Event("combat/charge_ready", "Charge ready", [charge_ready], length=0.6, level=-2.0, fade_out=0.15),
    Event("combat/charge_release", "Charged strike", [charge_release], length=0.7, level=2.0,
          fade_out=0.15),
    Event("combat/plunge_whistle", "Air whistles", [plunge_whistle], length=0.8, level=-4.0,
          fade_out=0.02),
    Event("combat/plunge_impact", "Heavy impact", [plunge_impact], length=0.9, level=3.0, fade_out=0.2),
    Event("combat/zenith", "Blade sweeps upward", [zenith], length=0.6, level=-1.0, fade_out=0.12),
    Event("combat/dash", "Air rushes",
          [partial(dash, 601, 2100.0, 650.0, 1.1, 0.6),
           partial(dash, 602, 1700.0, 520.0, 1.2, 0.8)],
          length=0.2, level=-4.0, fade_out=0.03),
    Event("combat/parry", "Blade parries", [parry], length=0.8, level=3.0, fade_out=0.2),
    Event("combat/perfect_dodge", "Perfect dodge", [perfect_dodge], length=0.5, level=-3.0,
          fade_out=0.12),
    Event("combat/resonance_full", "Blade resonates", [resonance_full], length=0.8, level=-8.0,
          fade_out=0.2),
    Event("combat/whiff", "Parry misses", [whiff], length=0.2, level=-7.0, fade_out=0.03),
]
