"""The Hollow Heliarch's sounds (G9a, GDD 7.3): its voice, the fight's effects, and its music by phase.

The voice. Six recorded takes (George, tools/sound/eleven/heliarch/, fetched once by eleven.py, never at build time)
shaped into one hollow, regal, resonant voice:

    heliarch/voice_intro        "Another singer. I poured a sun into the dark for you. It was not enough."
    heliarch/voice_hollowing    "The Hollow wants the rest of me. Let it have you instead."
    heliarch/voice_nova         "Burn with me!"
    heliarch/voice_collapse     "Everything falls. Everything always falls."
    heliarch/voice_death        "Ah... I can hear them again. Sing."
    heliarch/voice_player_down  "Silence."

  1. the words: high-passed at 85 Hz, the boxy low mids (380 Hz) pulled down, a little chest (160 Hz) and presence
     (3.2 kHz) so it stays clear under the boss loop; each whispered phrase brought part of the way up (voice.py's
     phrase leveller) and the loudest syllables held by a peak limiter;
  2. the hollow: a copy through a short metal comb (a helmet's ring, 5.8 ms), high-passed at 400 Hz, well under;
  3. the sub: a low D1 and D2 hum that swells with the words (their envelope, smoothed), low-passed at 160 Hz, far
     under the words: felt more than heard, so it never muddies them;
  4. the room: a dark, long reverb (T60 3.4 s, highs damped at 2.8 kHz, 45 ms before it starts), high-passed at
     180 Hz, a moderate send: a throne room, not a cave.
Every line levelled by its gated programme loudness (Event speech=True) to -16 LUFS, 7 LU over the boss loops.

The effects: in D, like the rest of the Sanctum. The two recorded effects: heliarch/nova_charge (a sun gathering
power) under Nova's last five seconds, heliarch/eclipse_drone (the sun going out) at the Collapse.

The music, three 8-bar loops at Vesper's 100 BPM, each starting on its downbeat at 0 s so the game retriggers it on
the bar lines (GuardianMusic), ringing 1.6 s past 19.2 s:
    music/heliarch_regent    regal and solar: D major (I, vi, IV, V), brass pads, timpani on 1 and 3, a proud bell
                             line over plucked eighths
    music/heliarch_hollow    hollow and eclipsed: D minor (i, VI, iv, V), a heartbeat sub on every beat, a distant
                             choir, a slow bell under a tolling low D, almost no drums
    music/heliarch_collapse  urgent: D minor (i, VI, VII, V), drums on every beat, sixteenth plucks, driving bass
                             eighths, a choir stab on every bar

Run:  python tools/sound/build.py heliarch music/heliarch
"""
from __future__ import annotations

from fractions import Fraction
from functools import lru_cache, partial

import numpy as np
from scipy import signal
from scipy.signal import resample_poly

import eleven
from aetheria import BEAT, bass, bell, chord_notes, choir, pad, pluck, render, sub
from analysis import integrated, loudness
from dsp import SR, TWO_PI, ad, at, band, db, eq, fade, hp, limiter, lp, n_of, norm, note, phase_of, place, rng, shaped_noise, \
    silence, smoothstep, softclip, timeline, wobble
from event import Event
from layers import BAR, GLASS, burst, chime, choir_stab, click, crackle, knock, reverb, reverb_ir, shatter, tail, thump, whoosh
from voice import first_onset, last_sound, level_phrases

D1, D2, A2, D3, FS3, A3, D4, FS4, A4, D5, FS5, A5, D6, FS6, A6 = (36.71, 73.42, 110.0, 146.83, 185.0, 220.0, 293.66, 369.99,
                                                                 440.0, 587.33, 739.99, 880.0, 1174.66, 1479.98, 1760.0)

# ---------------------------------------------------------------------------------------------------------- the voice
LINES = {
    "intro": "Another singer. I poured a sun into the dark for you. It was not enough.",
    "hollowing": "The Hollow wants the rest of me. Let it have you instead.",
    "nova": "Burn with me!",
    "collapse": "Everything falls. Everything always falls.",
    "death": "Ah... I can hear them again. Sing.",
    "player_down": "Silence.",
}
LEVEL_LUFS = -16.0
TAIL = 2.6
PEAK_OVER = 13.0


def comb(x: np.ndarray, delay: float, feedback: float) -> np.ndarray:
    """A feedback comb: y[n] = x[n] + g y[n - d] (a short metal tube's ring)."""
    d = n_of(delay)
    a = np.zeros(d + 1)
    a[0] = 1.0
    a[d] = -feedback
    return signal.lfilter([1.0], a, x)


def envelope(x: np.ndarray, window: float) -> np.ndarray:
    w = n_of(window)
    return np.sqrt(np.convolve(np.square(x), np.ones(w) / w, mode="same"))


@lru_cache(maxsize=None)
def voice_parts(name: str) -> dict:
    r = rng(2600 + sorted(LINES).index(name))
    take = eleven.sample(f"heliarch/{name}")
    onset, end = first_onset(take), last_sound(take)
    words = hp(take, 85.0, 2)
    words = eq(words, "peak", 380.0, 1.0, -3.0)
    words = eq(words, "lowshelf", 160.0, 0.7, 1.5)
    words = eq(words, "peak", 3200.0, 0.9, 2.0)
    words = level_phrases(words, share=0.5, most=4.0)
    words, _ = limiter(words, db(integrated(words) + PEAK_OVER), max_reduction_db=8.0)
    words = fade(words[at(max(onset - 0.06, 0.0)):at(end + 0.3)], 0.01, 0.2)
    words = norm(words)
    lead = 0.15
    length = lead + len(words) / SR + TAIL
    dry = place(silence(length), words, lead)
    env = envelope(dry, 0.05)
    speaking = env > env.max() * 0.1

    def under(layer: np.ndarray, below_db: float) -> np.ndarray:
        """`layer` scaled so its energy while the words sound is `below_db` under theirs."""
        e_words = np.sum(np.square(dry[speaking]))
        e_layer = np.sum(np.square(layer[speaking]))
        return layer * (np.sqrt(e_words / max(e_layer, 1e-12)) * db(-below_db) if e_layer > 0 else 0.0)

    hollow = under(hp(comb(dry, 0.0058, 0.55), 400.0, 2), 16.0)
    t = timeline(length)
    smooth = np.convolve(envelope(dry, 0.12), np.ones(n_of(0.2)) / n_of(0.2), mode="same")
    smooth /= max(1e-9, smooth.max())
    hum = (np.sin(TWO_PI * D2 * t) + 0.6 * np.sin(TWO_PI * D1 * t + 0.7) + 0.25 * np.sin(TWO_PI * A2 * t)) * smooth
    hum = under(lp(hum, 160.0, 2), 22.0)
    send = hp(dry + 0.5 * hollow, 180.0, 2)
    room = under(signal.fftconvolve(send, reverb_ir(r, 3.4, damp_hz=2800.0, hp_hz=180.0, predelay=0.045))[:len(dry)], 13.0)
    return {"words": dry, "hollow": hollow, "hum": hum, "room": room}


def voice(name: str) -> np.ndarray:
    p = voice_parts(name)
    return fade(p["words"] + p["hollow"] + p["hum"] + p["room"], 0.005, 0.6)


def _voice_event(name: str) -> Event:
    return Event(f"heliarch/voice_{name}", f"\"{LINES[name]}\"", [lambda n=name: voice(n)], length=12.0,
                 level=LEVEL_LUFS + 15.0, fade_out=0.8, speech=True, quality=6)


# ---------------------------------------------------------------------------------------------------------- effects
def take(name, seconds, fade_out, speed=1.0, lowpass=None, highpass=None):
    """A recorded take as a layer: played at `speed`, filtered, cut to `seconds` with a fade out."""
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


def swish(r, seconds, f_lo, f_hi, **kw):
    """A whoosh over `seconds`: its speed rises and falls once (whoosh() takes a velocity curve)."""
    t = timeline(seconds)
    v = np.sin(np.pi * np.clip(t / seconds, 0.0, 1.0)) ** 1.5
    return tail(norm(whoosh(r, v, f_lo, f_hi, **kw)))


def grind(r, seconds, lo, hi):
    t = timeline(seconds)
    x = shaped_noise(seconds, lambda tt, f: band(f, lo + (hi - lo) * tt / seconds, 1.0), r)
    return tail(norm(x) * (0.7 + 0.3 * wobble(r, len(t), 17.0)))


def gong(f, seconds, tau=1.2):
    """A struck plate of armor: a gong-like chime with inharmonic partials and a slow beat."""
    return tail(chime(f, seconds, ratios=(1.0, 1.51, 2.27, 3.13, 4.07), amps=(1.0, 0.6, 0.4, 0.22, 0.12),
                      taus=(tau, tau * 0.7, tau * 0.45, tau * 0.3, tau * 0.2)))


def fire(r, seconds, lo=250.0, hi=3000.0, flutter=13.0):
    """Roaring fire: band noise with a fast flutter and crackle."""
    t = timeline(seconds)
    x = shaped_noise(seconds, lambda tt, f: band(f, np.sqrt(lo * hi), 1.6), r)
    x = norm(x) * (0.65 + 0.35 * np.abs(wobble(r, len(t), flutter)))
    out = x.copy()
    place(out, crackle(r, n_events=int(seconds * 30), spread=seconds * 0.9, hp_hz=1800.0), 0.02, 0.25)
    return out


def rising(r, seconds, f0, f1, cluster=(1.0, 1.5, 2.0), bright=4.0):
    """A cluster of saws sweeping from f0 to f1: a rising drone."""
    t = timeline(seconds)
    f = f0 * (f1 / f0) ** (t / seconds)
    out = np.zeros_like(t)
    for q in cluster:
        ph = phase_of(f * q * (1 + 0.003 * np.sin(TWO_PI * 5.3 * t + q)))
        for k in range(1, 9):
            out += np.sin(k * ph) / k * np.exp(-(k - 1) / bright)
    return norm(out)


def assemble(seed=2601):
    r = rng(seed)
    out = silence(4.0)
    t = timeline(3.2)
    place(out, grind(r, 3.2, 90.0, 420.0) * ad(t, 0.8, 1.4, hold=1.2), 0.0, db(-6))
    place(out, thump(3.5, 55.0, 38.0, 1.2, 1.6), 0.0, db(-8))
    for k in range(8):
        place(out, knock(r, 180.0 + 40 * k, tau=0.05, ratios=(1.0, 2.3), amps=(1.0, 0.5)), 0.3 + 0.32 * k, db(-10))
    swell = choir_stab(r, 2.4, (D3, A3, D4), singers=3, detune_cents=8.0, attack=1.2, hold=0.6, tau=0.5, breath=0.05)
    place(out, swell, 1.4, db(-8))
    return reverb(norm(out), r, wet=0.35, t60=2.4, damp_hz=5000.0, hp_hz=60.0)


def ignite(seed=2611):
    r = rng(seed)
    out = silence(2.4)
    t = timeline(1.2)
    whoomp = shaped_noise(1.2, lambda tt, f: band(f, 200.0 + 1800.0 * np.minimum(tt / 0.3, 1.0), 1.2), r) * ad(t, 0.04, 0.35)
    place(out, norm(whoomp), 0.0, db(-3))
    place(out, thump(0.8, 90.0, 45.0, 0.08, 0.3, drive=1.4), 0.0, db(-6))
    for i, f in enumerate((D5, FS5, A5, D6)):
        place(out, bell(f, 1.8), 0.08 + 0.03 * i, db(-12))
    place(out, crackle(r, n_events=30, spread=1.2, hp_hz=2000.0), 0.1, db(-14))
    return reverb(norm(out), r, wet=0.3, t60=2.0)


def halo_open(seed=2621):
    r = rng(seed)
    out = silence(1.0)
    for i, f in enumerate((D4, FS4, A4, D5, FS5, A5)):
        place(out, gong(f, 0.8, tau=0.35), 0.03 * i, db(-8 - i))
    place(out, swish(r, 0.6, 300.0, 2000.0), 0.0, db(-12))
    return reverb(norm(out), r, wet=0.2, t60=1.2)


def halo_close(seed=2631):
    r = rng(seed)
    out = silence(0.8)
    for i, f in enumerate((A4, FS4, D4)):
        place(out, gong(f, 0.5, tau=0.2), 0.04 * i, db(-8))
    place(out, thump(0.4, 110.0, 60.0, 0.04, 0.12, drive=1.2), 0.1, db(-6))
    return reverb(norm(out), r, wet=0.15, t60=0.9)


def clang(seed=2641):
    r = rng(seed)
    out = silence(0.45)
    place(out, gong(D5 * 1.02, 0.4, tau=0.12), 0.0, 1.0)
    place(out, click(r, 0.002, 2000.0), 0.0, 0.5)
    return norm(out)


def hurt(seed, f0):
    r = rng(seed)
    out = silence(0.4)
    place(out, burst(r, 0.3, 0.002, 0.08, lo=900.0, hi=5000.0), 0.0, 0.8)
    place(out, gong(f0, 0.35, tau=0.1), 0.0, db(-6))
    return norm(out)


def hand_rise(seed=2651):
    r = rng(seed)
    out = silence(1.3)
    place(out, swish(r, 1.0, 120.0, 900.0, width=1.0, body=0.4), 0.0, 1.0)
    place(out, grind(r, 1.0, 70.0, 160.0) * ad(timeline(1.0), 0.2, 0.4, hold=0.3), 0.0, db(-9))
    return norm(out)


def glint(seed=2661):
    r = rng(seed)
    out = silence(0.7)
    place(out, bell(A6, 0.6, taus=(0.5, 0.2, 0.08, 0.03)), 0.0, 1.0)
    place(out, bell(D6 * 2, 0.4, taus=(0.3, 0.1, 0.05, 0.02)), 0.02, db(-6))
    return reverb(norm(out), r, wet=0.2, t60=0.8)


def slam(seed=2671):
    r = rng(seed)
    out = silence(2.2)
    place(out, thump(1.4, 70.0, 32.0, 0.06, 0.5, drive=1.8), 0.0, 1.0)
    place(out, burst(r, 0.4, 0.001, 0.06, lo=200.0, hi=4000.0), 0.0, db(-5))
    place(out, shatter(r, 1.0, 22, f_lo=900.0, f_hi=4000.0, amp_tau=0.3), 0.02, db(-12))
    place(out, gong(D3, 1.8, tau=0.9), 0.0, db(-8))
    return reverb(softclip(norm(out), 1.2), r, wet=0.3, t60=1.8, damp_hz=4000.0, hp_hz=50.0)


def parried(seed=2681):
    r = rng(seed)
    out = silence(2.0)
    place(out, gong(D4, 1.8, tau=1.1), 0.0, 1.0)
    place(out, bell(A5, 1.4), 0.0, db(-6))
    place(out, click(r, 0.003, 1500.0), 0.0, db(-4))
    for i, f in enumerate((D6, FS6, A6)):
        place(out, bell(f, 1.0, taus=(0.6, 0.2, 0.08, 0.03)), 0.12 + 0.07 * i, db(-14))
    return reverb(norm(out), r, wet=0.3, t60=2.0)


def sweep_tell(seed=2691):
    r = rng(seed)
    out = silence(1.6)
    place(out, rising(r, 1.5, D3, D4) * ad(timeline(1.5), 1.0, 0.2, hold=0.2), 0.0, db(-4))
    place(out, crackle(r, n_events=24, spread=1.4, hp_hz=2500.0), 0.1, db(-14))
    return norm(out)


def sweep(seed=2701):
    r = rng(seed)
    out = silence(1.6)
    place(out, fire(r, 1.3, 180.0, 2600.0) * ad(timeline(1.3), 0.04, 0.6, hold=0.5), 0.0, 1.0)
    place(out, thump(0.5, 80.0, 50.0, 0.05, 0.2), 0.0, db(-6))
    return reverb(norm(out), r, wet=0.2, t60=1.0)


def flare_tell(seed=3021):
    """Corona Flare's warning: the core draws in light for a second, a rising A under a brightening hiss, a D and A ringing."""
    r = rng(seed)
    out = silence(1.2)
    t = timeline(1.0)
    place(out, rising(r, 1.0, A3, A4, cluster=(1.0, 1.5), bright=5.0) * ad(t, 0.8, 0.15, hold=0.05), 0.0, db(-5))
    hiss = shaped_noise(1.0, lambda tt, f: band(f, 700.0 + 3300.0 * np.minimum(tt / 1.0, 1.0), 1.0), r) * ad(t, 0.9, 0.1)
    place(out, norm(hiss), 0.0, db(-10))
    for i, f in enumerate((D5, A5)):
        place(out, bell(f, 0.6), 0.55 + 0.2 * i, db(-15))
    return norm(out)


def flare(seed=3031):
    """Corona Flare: a burst of solar wind out of the core over a short thump, a bright D major ringing in it."""
    r = rng(seed)
    out = silence(1.6)
    t = timeline(1.0)
    gust = shaped_noise(1.0, lambda tt, f: band(f, 3200.0 - 2500.0 * np.minimum(tt / 0.6, 1.0), 1.1), r) * ad(t, 0.01, 0.4)
    place(out, norm(gust), 0.0, db(-2))
    place(out, thump(0.7, 110.0, 50.0, 0.04, 0.25, drive=1.3), 0.0, db(-5))
    for i, f in enumerate((D5, FS5, A5)):
        place(out, bell(f, 1.2), 0.02 * i, db(-13))
    return reverb(norm(out), r, wet=0.25, t60=1.4)


def lance_track(seed=2711):
    r = rng(seed)
    t = timeline(1.2)
    f = 1800.0 * (1 + 0.004 * np.sin(TWO_PI * 7.0 * t))
    x = np.sin(phase_of(f)) * 0.6 + 0.4 * np.sin(phase_of(f * 1.5))
    return reverb(norm(x * ad(t, 0.15, 0.3, hold=0.7)), r, wet=0.15, t60=0.8)


def lance_lock(seed=2721):
    r = rng(seed)
    out = silence(0.45)
    place(out, click(r, 0.002, 1200.0), 0.0, 1.0)
    t = timeline(0.35)
    place(out, np.sin(TWO_PI * 2400.0 * t) * ad(t, 0.004, 0.12), 0.01, db(-4))
    return norm(out)


def lance_fire(seed=2731):
    r = rng(seed)
    out = silence(1.1)
    t = timeline(0.8)
    buzz = np.zeros_like(t)
    for k in range(1, 12):
        buzz += np.sin(k * phase_of(D3 * 2 * (1 + 0.02 * np.exp(-t / 0.1)))) / k
    place(out, norm(buzz) * ad(t, 0.005, 0.3, hold=0.2), 0.0, db(-4))
    place(out, burst(r, 0.8, 0.003, 0.3, lo=1500.0, hi=9000.0), 0.0, db(-3))
    place(out, thump(0.4, 110.0, 60.0, 0.03, 0.12, drive=1.4), 0.0, db(-6))
    return reverb(norm(out), r, wet=0.2, t60=1.0)


def shed_flash(seed=2741):
    r = rng(seed)
    out = silence(1.2)
    for i in range(6):
        place(out, gong(A4 * (1 + 0.06 * i), 0.7, tau=0.3), 0.1 * i, db(-6))
    return reverb(norm(out), r, wet=0.25, t60=1.2)


def shed_out(seed=2751):
    r = rng(seed)
    out = silence(1.2)
    for i in range(6):
        place(out, swish(r, 0.5, 200.0 + 60 * i, 1400.0), 0.05 * i, db(-4))
    return norm(out)


def shed_return(seed=2761):
    r = rng(seed)
    out = silence(1.2)
    t = timeline(1.0)
    whirr = shaped_noise(1.0, lambda tt, f: band(f, 500.0 + 800.0 * tt, 1.0), r) * (0.5 + 0.5 * np.abs(np.sin(TWO_PI * 11.0 * t)))
    place(out, norm(whirr) * ad(t, 0.4, 0.2, hold=0.35), 0.0, 1.0)
    place(out, thump(0.3, 120.0, 70.0, 0.03, 0.1), 0.85, db(-4))
    return norm(out)


def break_(seed=2771):
    r = rng(seed)
    out = silence(2.4)
    for k in range(6):
        place(out, gong(D4 * (1.2 - 0.05 * k), 0.9, tau=0.4), 0.09 * k, db(-5 - k))
    place(out, thump(1.2, 80.0, 40.0, 0.08, 0.4, drive=1.4), 0.4, db(-2))
    t = timeline(1.8)
    fall = np.sin(phase_of(A4 * 2 ** (-t / 1.2))) * ad(t, 0.01, 0.8)
    place(out, fall, 0.0, db(-12))
    return reverb(norm(out), r, wet=0.3, t60=1.8)


def tear(seed=2781):
    r = rng(seed)
    out = silence(2.6)
    t = timeline(2.2)
    screech = shaped_noise(2.2, lambda tt, f: band(f, 2500.0 - 1500.0 * tt / 2.2, 0.3), r) * ad(t, 0.1, 0.8, hold=1.0)
    place(out, norm(screech), 0.0, db(-4))
    place(out, grind(r, 2.2, 60.0, 140.0) * ad(t, 0.3, 0.8, hold=0.8), 0.0, db(-5))
    place(out, thump(2.0, 50.0, 30.0, 1.0, 0.9), 0.1, db(-6))
    return reverb(norm(out), r, wet=0.35, t60=2.4)


def monolith_slam(seed=2791):
    r = rng(seed)
    out = silence(2.6)
    for k in range(6):
        place(out, thump(0.9, 75.0, 34.0, 0.06, 0.35, drive=1.8), 0.05 * k, db(-3))
        place(out, knock(r, 140.0 + 13 * k, tau=0.08), 0.05 * k, db(-9))
    place(out, shatter(r, 1.4, 30, f_lo=700.0, f_hi=3500.0, amp_tau=0.4), 0.05, db(-12))
    return reverb(softclip(norm(out), 1.3), r, wet=0.3, t60=2.2, damp_hz=4000.0)


def pip(seed=2801):
    r = rng(seed)
    out = silence(0.9)
    place(out, crackle(r, n_events=10, spread=0.2, hp_hz=1500.0), 0.0, 1.0)
    place(out, knock(r, 220.0, tau=0.05), 0.0, db(-4))
    place(out, gong(A4, 0.7, tau=0.25), 0.02, db(-8))
    return norm(out)


def monolith_shatter(seed=2811):
    r = rng(seed)
    out = silence(1.9)
    place(out, shatter(r, 1.6, 40, f_lo=600.0, f_hi=5000.0, amp_tau=0.5), 0.0, 1.0)
    place(out, thump(0.8, 90.0, 40.0, 0.05, 0.3, drive=1.6), 0.0, db(-3))
    place(out, gong(D4, 1.4, tau=0.6), 0.0, db(-9))
    return reverb(norm(out), r, wet=0.25, t60=1.6)


def beam_charge(seed=2821):
    r = rng(seed)
    out = silence(2.4)
    t = timeline(2.1)
    place(out, rising(r, 2.1, D2, D3, cluster=(1.0, 1.5, 2.01), bright=3.0) * ad(t, 1.5, 0.15, hold=0.4), 0.0, 1.0)
    place(out, np.sin(TWO_PI * D1 * t) * ad(t, 1.8, 0.2, hold=0.1), 0.0, db(-6))
    return norm(out)


def beam(seed=2831):
    r = rng(seed)
    out = silence(3.6)
    t = timeline(3.2)
    dark = shaped_noise(3.2, lambda tt, f: band(f, 220.0, 1.4), r) * (0.7 + 0.3 * np.sin(TWO_PI * 3.3 * t))
    place(out, norm(dark) * ad(t, 0.05, 0.4, hold=2.6), 0.0, 1.0)
    place(out, np.sin(TWO_PI * D2 * t) * (0.6 + 0.4 * np.sin(TWO_PI * 1.66 * t)) * ad(t, 0.05, 0.4, hold=2.6), 0.0, db(-4))
    sear = shaped_noise(3.2, lambda tt, f: band(f, 4500.0, 0.6), r)
    place(out, norm(sear) * ad(t, 0.02, 0.3, hold=2.7), 0.0, db(-12))
    return reverb(norm(out), r, wet=0.25, t60=1.8)


def lash_tell(seed=2841):
    r = rng(seed)
    out = silence(1.1)
    place(out, crackle(r, n_events=40, spread=1.0, hp_hz=1200.0), 0.0, 1.0)
    t = timeline(1.0)
    place(out, np.sin(TWO_PI * 60.0 * t) * ad(t, 0.6, 0.2, hold=0.2), 0.0, db(-6))
    fizz = shaped_noise(1.0, lambda tt, f: band(f, 6000.0, 0.5), r) * ad(t, 0.8, 0.1)
    place(out, norm(fizz), 0.0, db(-14))
    return norm(out)


def lash(seed=2851):
    r = rng(seed)
    out = silence(0.8)
    place(out, burst(r, 0.12, 0.0005, 0.02, lo=1500.0), 0.0, 1.0)
    place(out, thump(0.5, 120.0, 45.0, 0.03, 0.2, drive=1.6), 0.01, db(-2))
    place(out, crackle(r, n_events=14, spread=0.3, hp_hz=2000.0), 0.02, db(-10))
    return reverb(norm(out), r, wet=0.15, t60=0.8)


def tendril_cut(seed=2861):
    r = rng(seed)
    out = silence(1.1)
    t = timeline(0.9)
    shriek = shaped_noise(0.9, lambda tt, f: band(f, 3200.0 * 2 ** (-2.0 * tt), 0.35), r) * ad(t, 0.01, 0.35)
    place(out, norm(shriek), 0.0, 1.0)
    place(out, burst(r, 0.2, 0.001, 0.05, lo=300.0, hi=3000.0), 0.0, db(-4))
    return reverb(norm(out), r, wet=0.3, t60=1.4)


def inversion(seed=2871):
    r = rng(seed)
    out = silence(1.9)
    t = timeline(1.6)
    fall = np.sin(phase_of(D4 * 2 ** (-2.5 * t / 1.6))) * ad(t, 0.02, 0.9)
    place(out, fall, 0.0, db(-4))
    place(out, thump(1.2, 50.0, 28.0, 0.5, 0.6), 0.0, db(-3))
    place(out, swish(r, 1.2, 600.0, 3000.0, width=1.2), 0.0, db(-8))
    return reverb(norm(out), r, wet=0.4, t60=2.2)


def seed_launch(seed=2881):
    r = rng(seed)
    out = silence(0.9)
    t = timeline(0.8)
    place(out, np.sin(phase_of(600.0 * 2 ** (t / 0.8))) * ad(t, 0.02, 0.4), 0.0, 1.0)
    place(out, bell(D6, 0.6, taus=(0.4, 0.15, 0.05, 0.02)), 0.0, db(-8))
    return reverb(norm(out), r, wet=0.25, t60=1.0)


def seed_burst(seed=2891):
    r = rng(seed)
    out = silence(0.7)
    place(out, burst(r, 0.2, 0.001, 0.05, lo=500.0, hi=6000.0), 0.0, 1.0)
    place(out, bell(A5, 0.5, taus=(0.3, 0.12, 0.05, 0.02)), 0.0, db(-8))
    return reverb(norm(out), r, wet=0.2, t60=0.8)


def nova_charge(seed=2901):
    r = rng(seed)
    x = take("heliarch/nova_charge", 5.0, 0.3)
    out = silence(5.2)
    place(out, norm(x), 0.0, 1.0)
    t = timeline(5.0)
    place(out, rising(r, 5.0, D2, D4, cluster=(1.0, 1.5), bright=5.0) * ad(t, 3.8, 0.12, hold=0.4), 0.0, db(-12))
    return fade(norm(out), 0.005, 0.4)


def nova_blast(seed=2911):
    r = rng(seed)
    out = silence(3.4)
    place(out, thump(2.4, 60.0, 25.0, 0.15, 0.9, drive=2.0), 0.0, 1.0)
    place(out, burst(r, 2.0, 0.002, 0.6, lo=100.0, hi=6000.0), 0.0, db(-2))
    place(out, shatter(r, 2.2, 50, f_lo=800.0, f_hi=6000.0, amp_tau=0.7), 0.05, db(-12))
    for i, f in enumerate((D3, A3, D4)):
        place(out, gong(f, 2.6, tau=1.4), 0.0, db(-10 - 2 * i))
    return reverb(softclip(norm(out), 1.4), r, wet=0.35, t60=2.8, damp_hz=3500.0)


def nova_break(seed=2921):
    r = rng(seed)
    out = silence(2.2)
    place(out, shatter(r, 1.4, 60, f_lo=2000.0, f_hi=9000.0, amp_tau=0.4), 0.0, 1.0)
    for i, f in enumerate((D5, FS5, A5, D6)):
        place(out, bell(f, 1.8), 0.04 * i, db(-8))
    place(out, thump(0.6, 100.0, 50.0, 0.04, 0.2), 0.0, db(-6))
    return reverb(norm(out), r, wet=0.3, t60=2.0)


def eclipse_drone(seed=2931):
    r = rng(seed)
    x = take("heliarch/eclipse_drone", 5.0, 0.8)
    out = silence(5.6)
    place(out, norm(x), 0.0, 1.0)
    t = timeline(5.4)
    place(out, np.sin(TWO_PI * D1 * t) * ad(t, 0.6, 2.0, hold=1.8), 0.0, db(-9))
    return reverb(norm(out), r, wet=0.2, t60=2.4)


def crack(seed=2941):
    r = rng(seed)
    out = silence(1.5)
    place(out, crackle(r, n_events=50, spread=1.2, hp_hz=800.0), 0.0, 1.0)
    t = timeline(1.3)
    place(out, grind(r, 1.3, 50.0, 90.0) * ad(t, 0.1, 0.5, hold=0.5), 0.0, db(-3))
    return reverb(norm(out), r, wet=0.25, t60=1.4)


def fall(seed=2951):
    r = rng(seed)
    out = silence(3.0)
    t = timeline(2.6)
    place(out, grind(r, 2.6, 110.0, 40.0) * ad(t, 0.05, 1.2, hold=0.8), 0.0, 1.0)
    place(out, thump(1.6, 60.0, 28.0, 0.3, 0.8, drive=1.6), 0.0, db(-2))
    place(out, shatter(r, 2.0, 30, f_lo=500.0, f_hi=3000.0, amp_tau=0.8), 0.1, db(-10))
    return reverb(norm(out), r, wet=0.4, t60=2.6, damp_hz=3000.0)


def rain_tell(seed=2961):
    r = rng(seed)
    out = silence(1.2)
    t = timeline(1.0)
    for i, f in enumerate((A5, D6, FS6, A6)):
        place(out, np.sin(phase_of(f * (1 + 0.1 * t))) * ad(t, 0.6, 0.2, hold=0.2), 0.0, db(-6 - 2 * i))
    place(out, shaped_noise(1.0, lambda tt, f: band(f, 5000.0, 0.7), r) * ad(t, 0.8, 0.1), 0.0, db(-10))
    return norm(out)


def rain(seed=2971):
    r = rng(seed)
    out = silence(1.1)
    place(out, thump(0.7, 95.0, 45.0, 0.04, 0.25, drive=1.6), 0.0, 1.0)
    place(out, fire(r, 0.6, 400.0, 3500.0) * ad(timeline(0.6), 0.005, 0.25), 0.0, db(-4))
    return reverb(norm(out), r, wet=0.2, t60=1.0)


def death(seed=2981):
    r = rng(seed)
    out = silence(5.2)
    place(out, shatter(r, 2.0, 40, f_lo=1500.0, f_hi=8000.0, amp_tau=0.8), 0.0, db(-6))
    place(out, thump(1.4, 70.0, 30.0, 0.1, 0.6, drive=1.4), 0.0, db(-4))
    swell = choir_stab(r, 4.0, (D3, FS3, A3, D4, FS4), singers=3, detune_cents=7.0, attack=1.6, hold=1.2, tau=1.0, breath=0.05)
    place(out, swell, 0.6, db(-2))
    for i, f in enumerate((D5, FS5, A5, D6, FS6)):
        place(out, bell(f, 2.6), 1.4 + 0.18 * i, db(-10))
    return reverb(norm(out), r, wet=0.4, t60=3.0, damp_hz=6000.0)


def reliquary_appear(seed=2991):
    r = rng(seed)
    out = silence(2.4)
    for i, f in enumerate((D5, FS5, A5, D6)):
        place(out, bell(f, 1.8), 0.12 * i, db(-4 - i))
    place(out, shaped_noise(1.5, lambda tt, f: band(f, 7000.0, 0.6), r) * ad(timeline(1.5), 0.3, 0.8), 0.0, db(-16))
    return reverb(norm(out), r, wet=0.35, t60=2.0)


def reliquary_open(seed=3001):
    r = rng(seed)
    out = silence(2.0)
    place(out, grind(r, 0.6, 400.0, 900.0) * ad(timeline(0.6), 0.05, 0.25, hold=0.2), 0.0, db(-8))
    place(out, gong(D4, 1.4, tau=0.7), 0.35, db(-4))
    for i, f in enumerate((A5, D6, FS6, A6)):
        place(out, bell(f, 1.2, taus=(0.8, 0.3, 0.1, 0.04)), 0.4 + 0.07 * i, db(-9))
    return reverb(norm(out), r, wet=0.3, t60=1.6)


def seal(seed=3011):
    r = rng(seed)
    out = silence(6.0)
    t = timeline(5.2)
    place(out, np.sin(TWO_PI * D2 * t) * ad(t, 1.5, 2.5, hold=1.0), 0.0, db(-6))
    swell = choir_stab(r, 5.0, (D3, A3, D4, FS4, A4), singers=3, detune_cents=6.0, attack=2.0, hold=1.5, tau=1.2, breath=0.04)
    place(out, swell, 0.2, 1.0)
    for i, f in enumerate((D5, A5, D6, FS6, A6)):
        place(out, bell(f, 3.0), 1.2 + 0.25 * i, db(-10))
    return reverb(norm(out), r, wet=0.45, t60=3.4, damp_hz=7000.0)


# ---------------------------------------------------------------------------------------------------------- music
PHRASE_BARS = 8
MUSIC_TAIL = 1.6
MUSIC_SECONDS = PHRASE_BARS * 4 * BEAT + MUSIC_TAIL


def drum(seconds, f0=70.0, seed=0, slap=0.25):
    r = rng(seed)
    x = norm(thump(seconds, f0 * 1.6, f0, 0.03, 0.22, drive=1.4))
    place(x, norm(burst(r, 0.08, 0.001, 0.02, lo=300.0, hi=3000.0)), 0.0, slap)
    return x


def snare(seconds, seed=0):
    r = rng(seed)
    x = norm(burst(r, min(seconds, 0.25), 0.001, 0.07, lo=900.0, hi=7000.0))
    place(x, norm(thump(0.2, 220.0, 160.0, 0.02, 0.06)), 0.0, 0.5)
    return np.concatenate([x, np.zeros(max(0, n_of(seconds) - len(x)))])[:n_of(seconds)]


def brass(freqs, seconds, seed=1):
    """A regal brass-like pad: bright saws swelling in, a slow vibrato."""
    return pad(freqs, seconds, attack=0.25, release=0.8, bright=9.0, voices=3, detune=5.0, seed=seed)


def regent_loop():
    """Regal and solar: D major, I - vi - IV - V two bars each; timpani on 1 and 3, brass pads, plucked eighths in
    arpeggio, a proud bell line over the last four bars."""
    chords = [(["D3", "A3", "D4", "F#4"], "D2"), (["B2", "F#3", "B3", "D4"], "B1"), (["G2", "D3", "G3", "B3"], "G1"),
              (["A2", "E3", "A3", "C#4"], "A1")]
    arps = {0: ["D5", "F#5", "A5", "F#5"], 1: ["B4", "D5", "F#5", "D5"], 2: ["G4", "B4", "D5", "B4"], 3: ["A4", "C#5", "E5", "C#5"]}
    score = []
    for bar in range(PHRASE_BARS):
        c = (bar // 2) % 4
        chord, root = chords[c]
        b0 = bar * 4
        if bar % 2 == 0:
            score.append((b0, 8.4, (lambda sec, ch=chord, s=bar: brass(chord_notes(ch), sec, seed=60 + s)), -11))
        for e in range(4):
            score.append((b0 + e, 1.0, (lambda sec, f=note(root): bass(f, sec)), -10 if e % 2 == 0 else -13))
        score.append((b0, 1.4, (lambda sec: drum(sec, 58.0, 1)), -5))
        score.append((b0 + 2, 1.4, (lambda sec: drum(sec, 66.0, 2)), -8))
        if bar % 2 == 1:
            score.append((b0 + 3.5, 0.8, (lambda sec: drum(sec, 78.0, 3)), -12))
        for s in range(8):
            n = arps[c][s % 4]
            score.append((b0 + s * 0.5, 1.1, (lambda sec, f=note(n): pluck(f, sec, bright=1.4, tau=0.5)), -17 if s % 2 else -14))
    melody = [("A5", 16, 2), ("D6", 18, 2), ("C#6", 20, 1), ("B5", 21, 1), ("A5", 22, 2), ("G5", 24, 2), ("B5", 26, 2),
              ("A5", 28, 1.5), ("E5", 29.5, 0.5), ("A5", 30, 2)]
    for n, s, ln in melody:
        score.append((s, ln + 2.0, (lambda sec, f=note(n): bell(f, sec)), -9))
    x = render(score, MUSIC_SECONDS, 11)
    return reverb(x, rng(78), wet=0.24, t60=1.8, damp_hz=7000.0, hp_hz=120.0)[:n_of(MUSIC_SECONDS)]


def hollow_loop():
    """Hollow and eclipsed: D minor, i - VI - iv - V; a heartbeat sub on every beat, a distant choir, a slow bell over
    a low D tolling on each bar, a clock tick on the off-beats, no drums."""
    chords = [(["D3", "A3", "D4", "F4"], "D2"), (["A#2", "F3", "A#3", "D4"], "A#1"), (["G2", "D3", "G3", "A#3"], "G1"),
              (["A2", "E3", "A3", "C#4"], "A1")]
    score = []
    for bar in range(PHRASE_BARS):
        c = (bar // 2) % 4
        chord, root = chords[c]
        b0 = bar * 4
        if bar % 2 == 0:
            score.append((b0, 8.6, (lambda sec, ch=chord: choir(chord_notes(ch), sec, seed=5)), -9))
            score.append((b0, 8.4, (lambda sec, ch=chord, s=bar: pad(chord_notes(ch), sec, attack=1.0, release=1.2, bright=2.5, seed=90 + s)), -15))
        score.append((b0, 3.5, (lambda sec: gong(D2, sec, tau=1.6)), -12))
        for e in range(4):
            score.append((b0 + e, 0.9, (lambda sec, f=note(root): sub(f * 2, sec, attack=0.01, tau=0.18)), -9 if e == 0 else -12))
            score.append((b0 + e + 0.5, 0.2, (lambda sec, s=bar * 4 + e: tail(click(rng(900 + s), 0.003, 3000.0))), -26))
    melody = [("D5", 4, 3), ("C#5", 7, 1), ("D5", 8, 2), ("F5", 10, 2), ("E5", 12, 4), ("A4", 20, 3), ("A#4", 23, 1),
              ("A4", 24, 2), ("G4", 26, 2), ("A4", 28, 4)]
    for n, s, ln in melody:
        score.append((s, ln + 2.5, (lambda sec, f=note(n): bell(f, sec, taus=(2.0, 0.8, 0.3, 0.1))), -12))
    x = render(score, MUSIC_SECONDS, 12)
    return reverb(x, rng(79), wet=0.34, t60=2.6, damp_hz=4500.0, hp_hz=100.0)[:n_of(MUSIC_SECONDS)]


def collapse_loop():
    """Urgent: D minor, i - VI - VII - V; drums on every beat and snares on 2 and 4, sixteenth plucks, driving bass
    eighths, a choir stab on every downbeat, a rising pad."""
    chords = [(["D3", "A3", "D4", "F4"], "D2"), (["A#2", "F3", "A#3", "D4"], "A#1"), (["C3", "G3", "C4", "E4"], "C2"),
              (["A2", "E3", "A3", "C#4"], "A1")]
    arps = {0: ["D5", "A5", "F5", "A5"], 1: ["D5", "A#5", "F5", "A#5"], 2: ["C5", "G5", "E5", "G5"], 3: ["A4", "E5", "C#5", "E5"]}
    score = []
    for bar in range(PHRASE_BARS):
        c = (bar // 2) % 4
        chord, root = chords[c]
        b0 = bar * 4
        if bar % 2 == 0:
            score.append((b0, 8.4, (lambda sec, ch=chord, s=bar: pad(chord_notes(ch), sec, attack=0.2, release=0.6, bright=8.0, seed=120 + s)), -13))
        score.append((b0, 1.2, (lambda sec, ch=chord, s=bar: choir_stab(rng(300 + s), sec, chord_notes(ch)[1:], singers=3, tau=0.35)), -9))
        for e in range(8):
            score.append((b0 + e * 0.5, 0.5, (lambda sec, f=note(root): bass(f, sec)), -9 if e % 2 == 0 else -12))
        for e in range(4):
            score.append((b0 + e, 1.0, (lambda sec, s=e: drum(sec, 62.0 + 4 * s, 10 + s)), -6 if e == 0 else -8))
            if e % 2 == 1:
                score.append((b0 + e, 0.3, (lambda sec, s=bar * 4 + e: snare(sec, 400 + s)), -12))
        for s in range(16):
            n = arps[c][s % 4]
            score.append((b0 + s * 0.25, 0.8, (lambda sec, f=note(n): pluck(f, sec, bright=1.8, tau=0.3)), -18 if s % 4 else -15))
    x = render(score, MUSIC_SECONDS, 13)
    return reverb(x, rng(80), wet=0.2, t60=1.4, damp_hz=7000.0, hp_hz=120.0)[:n_of(MUSIC_SECONDS)]


EVENTS = [
    *[_voice_event(n) for n in LINES],
    Event("heliarch/assemble", "Debris rises out of the Breach", [assemble], length=4.0, level=0.0, fade_out=0.8),
    Event("heliarch/ignite", "A sun ignites", [ignite], length=2.4, level=2.0, fade_out=0.6),
    Event("heliarch/open", "The halo opens", [halo_open], length=1.0, level=-6.0, fade_out=0.2),
    Event("heliarch/close", "The halo closes", [halo_close], length=0.8, level=-7.0, fade_out=0.2),
    Event("heliarch/clang", "The halo turns the blow", [clang], length=0.45, level=-5.0, fade_out=0.1),
    Event("heliarch/hurt", "The Heliarch's core flares", [partial(hurt, 2642, A4), partial(hurt, 2643, D5)], length=0.4, level=-6.0,
          fade_out=0.08),
    Event("heliarch/hand_rise", "A giant hand rises", [hand_rise], length=1.3, level=-3.0, fade_out=0.2),
    Event("heliarch/glint", "Gold glint", [glint], length=0.7, level=1.0, fade_out=0.15),
    Event("heliarch/slam", "Sunderfall", [slam], length=2.2, level=4.0, fade_out=0.5),
    Event("heliarch/parried", "Sunderfall turned aside", [parried], length=2.0, level=3.0, fade_out=0.5),
    Event("heliarch/sweep_tell", "Solar fire gathers", [sweep_tell], length=1.6, level=-2.0, fade_out=0.1),
    Event("heliarch/sweep", "Corona Sweep", [sweep], length=1.6, level=2.0, fade_out=0.3),
    Event("heliarch/flare_tell", "The core draws in light", [flare_tell], length=1.2, level=-3.0, fade_out=0.15),
    Event("heliarch/flare", "Corona Flare", [flare], length=1.6, level=2.0, fade_out=0.3),
    Event("heliarch/lance_track", "Solar Lance aims", [lance_track], length=1.2, level=-6.0, fade_out=0.15),
    Event("heliarch/lance_lock", "Solar Lance locks", [lance_lock], length=0.45, level=-2.0, fade_out=0.08),
    Event("heliarch/lance_fire", "Solar Lance", [lance_fire], length=1.1, level=2.0, fade_out=0.25),
    Event("heliarch/shed_flash", "The halo's plates ring", [shed_flash], length=1.2, level=-2.0, fade_out=0.3),
    Event("heliarch/shed_out", "Plates fly outward", [shed_out], length=1.2, level=-2.0, fade_out=0.2),
    Event("heliarch/shed_return", "Plates boomerang back", [shed_return], length=1.2, level=1.0, fade_out=0.2),
    Event("heliarch/break", "The Heliarch Breaks", [break_], length=2.4, level=3.0, fade_out=0.5),
    Event("heliarch/tear", "The halo tears free", [tear], length=2.6, level=2.0, fade_out=0.6),
    Event("heliarch/monolith_slam", "Monoliths slam down", [monolith_slam], length=2.6, level=4.0, fade_out=0.6),
    Event("heliarch/pip", "A monolith cracks", [pip], length=0.9, level=-1.0, fade_out=0.2),
    Event("heliarch/monolith_shatter", "A monolith shatters", [monolith_shatter], length=1.9, level=2.0, fade_out=0.4),
    Event("heliarch/beam_charge", "The eclipse draws breath", [beam_charge], length=2.4, level=-1.0, fade_out=0.15),
    Event("heliarch/beam", "Eclipse Beam", [beam], length=3.6, level=1.0, fade_out=0.5),
    Event("heliarch/lash_tell", "The floor cracks", [lash_tell], length=1.1, level=-6.0, fade_out=0.15),
    Event("heliarch/lash", "Tendril Lash", [lash], length=0.8, level=1.0, fade_out=0.15),
    Event("heliarch/tendril_cut", "A tendril is cut", [tendril_cut], length=1.1, level=0.0, fade_out=0.25),
    Event("heliarch/inversion", "Gravity turns thin", [inversion], length=1.9, level=0.0, fade_out=0.4),
    Event("heliarch/seed", "A Star Seed flies", [seed_launch], length=0.9, level=-6.0, fade_out=0.2),
    Event("heliarch/seed_burst", "A Star Seed bursts", [seed_burst], length=0.7, level=-3.0, fade_out=0.15),
    Event("heliarch/nova_charge", "Nova builds", [nova_charge], length=5.2, level=2.0, fade_out=0.3),
    Event("heliarch/nova_blast", "Nova detonates", [nova_blast], length=3.4, level=4.0, fade_out=0.8),
    Event("heliarch/nova_break", "The Corona Shield shatters", [nova_break], length=2.2, level=4.0, fade_out=0.5),
    Event("heliarch/eclipse_drone", "The Breach widens", [eclipse_drone], length=5.6, level=1.0, fade_out=1.0),
    Event("heliarch/crack", "The floor cracks apart", [crack], length=1.5, level=0.0, fade_out=0.3),
    Event("heliarch/fall", "The floor falls away", [fall], length=3.0, level=3.0, fade_out=0.8),
    Event("heliarch/rain_tell", "Solar Rain gathers", [rain_tell], length=1.2, level=-6.0, fade_out=0.15),
    Event("heliarch/rain", "Solar Rain strikes", [rain], length=1.1, level=-1.0, fade_out=0.25),
    Event("heliarch/death", "The eclipse breaks", [death], length=5.2, level=3.0, fade_out=1.2),
    Event("heliarch/reliquary", "Reliquaries rise", [reliquary_appear], length=2.4, level=-2.0, fade_out=0.6),
    Event("heliarch/reliquary_open", "A Reliquary opens", [reliquary_open], length=2.0, level=-2.0, fade_out=0.5),
    Event("heliarch/seal", "The Breach is sealed", [seal], length=6.0, level=2.0, fade_out=1.4),
    Event("music/heliarch_regent", "Music: the Regent", [regent_loop], length=MUSIC_SECONDS, level=-3.0, fade_out=1.2, quality=2),
    Event("music/heliarch_hollow", "Music: the Hollow", [hollow_loop], length=MUSIC_SECONDS, level=-4.0, fade_out=1.2, quality=2),
    Event("music/heliarch_collapse", "Music: the Collapse", [collapse_loop], length=MUSIC_SECONDS, level=-2.5, fade_out=1.2, quality=2),
]


# ---------------------------------------------------------------------------------------------------------- checks

def report() -> int:
    """Measures the encoded voice lines against the loudest boss loop: loudness, peaks, the words over the rest."""
    import soundfile as sf
    from analysis import true_peak
    from dsp import to_db
    from pathlib import Path
    sounds = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "sounds"
    music = max(integrated(sf.read(str(sounds / "music" / f"heliarch_{m}.ogg"))[0]) for m in ("regent", "hollow", "collapse"))
    print(f"loudest boss loop {music:.1f} LUFS")
    problems = 0
    for name in LINES:
        y, _ = sf.read(str(sounds / "heliarch" / f"voice_{name}.ogg"))
        p = voice_parts(name)
        rest = p["hollow"] + p["hum"] + p["room"]
        speaking = envelope(p["words"], 0.05) > envelope(p["words"], 0.05).max() * 0.1
        clarity = 10 * np.log10(np.sum(np.square(p["words"][speaking])) / max(1e-12, np.sum(np.square(rest[speaking]))))
        prog, tp = integrated(y), float(to_db(true_peak(y)))
        notes = []
        if abs(prog - LEVEL_LUFS) > 0.6:
            notes.append(f"programme loudness {prog:.1f}")
        if tp > -0.3:
            notes.append("true peak over -0.3 dBTP")
        if prog - music < 5.0:
            notes.append("less than 5 LU over the music")
        if clarity < 8.0:
            notes.append(f"the words only {clarity:.1f} dB over the rest")
        problems += len(notes)
        print(f"{name:12s} {len(y) / SR:5.2f} s  {prog:6.1f} LUFS  tp {tp:5.1f}  over music {prog - music:5.1f}  clarity {clarity:5.1f}  "
              f"{'; '.join(notes)}")
    print(f"{problems} problem(s)")
    return problems


if __name__ == "__main__":
    import sys
    sys.dont_write_bytecode = True
    sys.exit(1 if report() else 0)
