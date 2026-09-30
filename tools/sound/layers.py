"""Sound layers: the physical ingredients the recipes mix together.

Each function returns a float array that starts at its own time zero; recipes
position layers with dsp.place(). Layers are not level-matched: recipes
normalise them with dsp.norm() and mix with explicit dB gains.
"""
from __future__ import annotations

import numpy as np
from scipy import signal

from dsp import (SR, TWO_PI, ad, band, fade, hp, hp_shape, lp, lp_shape, n_of, norm,
                 peak, phase_of, place, rise, shaped_noise, silence, timeline, wobble)

# Partial ratios of struck bodies.
GLASS = (1.0, 2.32, 4.25, 6.63)       # wine-glass rim modes: soft, glassy
BAR = (1.0, 2.756, 5.404, 8.933)      # free bar (glockenspiel, crystal rod): clear "ting"


def tail(x, share=0.15, longest=0.02):
    """Fade a layer's last `share` of its length (at most `longest` s) to exactly zero.

    Layers are rendered into fixed-length buffers; if one stopped while still
    ringing, dropping it into a longer mix would leave a hard edge (a click).
    """
    return fade(x, 0.0, min(longest, share * len(x) / SR))


# ---------------------------------------------------------------------------
# Struck resonators

def modes(seconds, freqs, amps, taus, beats=None, beat_mix=0.35, attack=0.0):
    """A struck resonant body: a sum of exponentially decaying sines.

    `beats` splits a mode into a slowly beating pair, the shimmer of real bells
    and glass. Every sine starts at phase 0, so the strike has no step and no
    click; `attack` softens it further, like a softer mallet.
    """
    t = timeline(seconds)
    out = np.zeros_like(t)
    for i, (f, a, tau) in enumerate(zip(freqs, amps, taus)):
        if a == 0 or f <= 0 or f >= 0.46 * SR:
            continue
        s = np.sin(TWO_PI * f * t)
        if beats is not None and beats[i]:
            s = (1.0 - beat_mix) * s + beat_mix * np.sin(TWO_PI * (f + beats[i]) * t)
        out += a * np.exp(-t / tau) * s
    if attack > 0:
        out *= rise(t, attack)
    return tail(out)


def chime(f0, seconds, ratios=GLASS, amps=(1.0, 0.25, 0.08, 0.03),
          taus=(0.12, 0.06, 0.03, 0.015), beats=(2.0, 3.0, 0.0, 0.0), attack=0.0):
    """A tuned glass or crystal chime on f0."""
    n = min(len(ratios), len(amps), len(taus))
    return modes(seconds, [f0 * q for q in ratios[:n]], amps[:n], taus[:n],
                 beats=list(beats[:n]) + [0.0] * (n - len(beats[:n])), attack=attack)


def glass_grain(r, f0, tau=0.04, n_modes=3, attack=0.0004, tick=0.25):
    """One small crystal fragment: a few random inharmonic modes plus a contact tick."""
    ratios = np.concatenate([[1.0], np.sort(r.uniform(1.35, 4.3, n_modes - 1))])
    amps = np.concatenate([[1.0], r.uniform(0.2, 0.6, n_modes - 1)])
    taus = tau * ratios ** -0.7
    seconds = min(7.0 * tau, 0.6) + 0.01
    x = norm(modes(seconds, f0 * ratios, amps, taus, attack=attack))
    if tick:
        place(x, click(r, 0.0005, 3000.0), 0.0, tick)
    return x


def shatter(r, seconds, n, f_lo=2500.0, f_hi=10000.0, burst_frac=0.35, burst_len=0.04,
            t_tau=0.15, amp_tau=0.25, tau_range=(0.015, 0.07)):
    """Breaking crystal: a dense burst of fragments, then sparser tinkles falling away."""
    out = silence(seconds)
    for _ in range(n):
        ti = r.uniform(0.0, burst_len) if r.random() < burst_frac else r.exponential(t_tau)
        if ti > seconds - 0.03:
            continue
        f0 = np.exp(r.uniform(np.log(f_lo), np.log(f_hi)))
        tau = np.exp(r.uniform(np.log(tau_range[0]), np.log(tau_range[1])))
        a = np.exp(-ti / amp_tau) * r.uniform(0.25, 1.0)
        place(out, glass_grain(r, f0, tau), ti, a)
    return out


# ---------------------------------------------------------------------------
# Transients and impacts

def click(r, seconds=0.0015, hp_hz=1000.0, lp_hz=None):
    """A very short broadband tick (the instant of contact)."""
    n = n_of(seconds)
    burst_ = r.standard_normal(n) * np.exp(-np.arange(n) / max(n / 5.0, 1.0))
    x = np.concatenate([burst_, np.zeros(n_of(0.004))])
    x = hp(x, hp_hz, 2)
    if lp_hz:
        x = lp(x, lp_hz, 2)
    return norm(x)


def crackle(r, n_events=6, spread=0.015, hp_hz=2500.0, lp_hz=None, grain=0.0006, falloff=0.6):
    """A cluster of tiny sharp fractures: the texture of a crystal cracking."""
    g_n = n_of(grain)
    x = silence(spread + 8 * grain + 0.006)
    times = np.concatenate([[0.0], np.sort(spread * r.random(n_events - 1) ** 1.5)])
    shape = np.exp(-np.arange(g_n) / max(g_n / 4.0, 1.0))
    for k, tk in enumerate(times):
        a = 1.0 if k == 0 else r.uniform(0.3, 0.9) * np.exp(-tk / (spread * falloff))
        place(x, r.standard_normal(g_n) * shape, tk, a)
    x = hp(x, hp_hz, 4)
    if lp_hz:
        x = lp(x, lp_hz, 2)
    return norm(x)


def thump(seconds, f_start, f_end, sweep_tau, tau, attack=0.0012, drive=0.0):
    """A low body hit: a sine whose pitch drops quickly (like a kick drum), with saturation
    to add harmonics so the weight still reads on small speakers."""
    t = timeline(seconds)
    f = f_end + (f_start - f_end) * np.exp(-t / sweep_tau)
    x = np.sin(phase_of(f)) * ad(t, attack, tau)
    if drive > 0:
        x = np.tanh(drive * x) / np.tanh(drive)
    return tail(x)


def burst(r, seconds, attack, tau, lo=None, hi=None, order=2):
    """A filtered noise burst with an attack/decay envelope."""
    t = timeline(seconds)
    x = r.standard_normal(len(t))
    if lo and hi:
        x = signal.sosfilt(signal.butter(order, [lo, hi], "bandpass", fs=SR, output="sos"), x)
    elif lo:
        x = hp(x, lo, order)
    elif hi:
        x = lp(x, hi, order)
    return tail(norm(x * ad(t, attack, tau)))


def knock(r, f0, tau=0.02, ratios=(1.0, 1.72), amps=(1.0, 0.4)):
    """A short woody/stony body knock (gives an impact its mid-range weight)."""
    x = modes(8 * tau, [f0 * q for q in ratios], amps, [tau, tau * 0.6])
    place(x, click(r, 0.0008, 300.0), 0.0, 0.3)
    return norm(x)


def granular_hiss(r, seconds, lo=3500.0, rate=60.0, attack=0.001, tau=0.08):
    """Bright hiss chopped into grains: the sizzle of glass dust."""
    t = timeline(seconds)
    x = hp(r.standard_normal(len(t)), lo, 2)
    grains = np.abs(wobble(r, len(t), rate)) ** 1.5
    return tail(norm(x * grains * ad(t, attack, tau)))


# ---------------------------------------------------------------------------
# Air

def whoosh(r, v, f_lo, f_hi, width=0.8, whistle=1.0, whistle_width=0.06, whistle_ratio=1.15,
           air=0.4, air_hz=6000.0, body=0.0, body_hz=150.0, flutter=0.0, flutter_hz=25.0,
           amp_pow=1.3, nfft=1024):
    """Blade-through-air noise driven by a velocity curve v (0..1, one value per sample).

    Velocity sets loudness and the spectral centre together, the way a real swish
    brightens as the blade speeds past. `whistle` adds the narrow Aeolian tone of
    a thin edge, `air` the high hiss at top speed, `body` the low push of displaced air.
    """
    n = len(v)
    t = np.arange(n) / SR

    def mag(tt, f):
        vv = np.interp(tt, t, v)
        fc = f_lo * (f_hi / f_lo) ** vv
        m = band(f, fc, width)
        if whistle:
            m = m + whistle * band(f, fc * whistle_ratio, whistle_width)
        if air:
            m = m + air * vv * hp_shape(f, air_hz, 2) * lp_shape(f, 16000.0, 2)
        if body:
            m = m + body * band(f, body_hz, 0.6)
        return m

    x = shaped_noise(n / SR, mag, r, nfft)
    env = np.asarray(v) ** amp_pow
    if flutter:
        env = env * (1.0 + flutter * wobble(r, n, flutter_hz))
    return x * env


def blade_ring(r, v, f0, ratios=(1.0, 1.47, 2.09, 2.83), amps=(1.0, 0.6, 0.35, 0.2), linger=0.05):
    """The faint shimmer of star metal, excited by the swing and ringing on briefly after it."""
    n = len(v)
    t = np.arange(n) / SR
    k = np.exp(-1.0 / (linger * SR))
    drive = signal.lfilter([1.0 - k], [1.0, -k], np.asarray(v) ** 2)
    drive = drive / peak(drive)
    out = np.zeros(n)
    for q, a in zip(ratios, amps):
        f = f0 * q
        if f > 0.45 * SR:
            continue
        trem = 1.0 + 0.4 * np.sin(TWO_PI * r.uniform(9.0, 17.0) * t + r.uniform(0.0, TWO_PI))
        out += a * trem * np.sin(TWO_PI * f * t + r.uniform(0.0, TWO_PI))
    return out * drive


# ---------------------------------------------------------------------------
# Space

def reverb_ir(r, t60=0.8, damp_hz=6000.0, hp_hz=200.0, predelay=0.006, seconds=None):
    """Synthetic room: decaying noise whose highs die faster (unit energy)."""
    seconds = seconds or min(1.2 * t60, 3.0)

    def mag(tt, f):
        t60f = t60 / np.sqrt(1.0 + (f / damp_hz) ** 2)
        decay = 10.0 ** (-3.0 * np.maximum(tt, 0.0) / np.maximum(t60f, 0.03))
        return decay * hp_shape(f, hp_hz, 2)

    ir = shaped_noise(seconds, mag, r, nfft=512)
    ir = fade(ir, fade_in=0.004, fade_out=min(0.1, 0.2 * seconds))
    ir /= np.sqrt(np.sum(ir ** 2))
    return np.concatenate([np.zeros(n_of(predelay)), ir])


def reverb(x, r, wet=0.15, t60=0.8, **kw):
    """Dry signal plus a synthetic room tail (the output is longer than the input)."""
    wet_sig = signal.fftconvolve(x, reverb_ir(r, t60, **kw))
    out = wet * wet_sig
    out[:len(x)] += x
    return out


def reverb_wrap(x, r, wet=0.3, t60=1.2, **kw):
    """Reverb for a loop: circular convolution, so the tail wraps into the start."""
    ir = reverb_ir(r, t60, **kw)
    n = len(x)
    folded = np.zeros(n)
    for s in range(0, len(ir), n):
        chunk = ir[s:s + n]
        folded[:len(chunk)] += chunk
    wet_sig = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(folded), n=n)
    return x + wet * wet_sig


# ---------------------------------------------------------------------------
# Voices (formant synthesis of a sung "ahh")

# (centre Hz, bandwidth Hz, gain dB): an "ah" vowel for a mixed choir.
AH_BRIGHT = ((800.0, 90.0, 0.0), (1150.0, 100.0, -4.0), (2800.0, 160.0, -9.0),
             (3400.0, 200.0, -11.0), (4400.0, 300.0, -20.0))
AH_DISTANT = ((720.0, 100.0, 0.0), (1100.0, 110.0, -5.0), (2700.0, 170.0, -17.0),
              (3300.0, 220.0, -23.0), (4200.0, 300.0, -34.0))


def vowel_gain(f, formants, floor=0.012):
    """Amplitude response of the vocal tract for a vowel, at frequency f."""
    g = np.full(np.shape(f), floor, dtype=float)
    for fc, bw, gdb in formants:
        g = g + 10.0 ** (gdb / 20.0) / np.sqrt(1.0 + ((f - fc) / (0.5 * bw)) ** 2)
    return g


def top_taper(f, f_max, width=0.25):
    """1 below (1 - width) * f_max, easing to exactly 0 at f_max (no partial pops in or out)."""
    u = np.clip((f_max - f) / (width * f_max), 0.0, 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * u)


def sung(freq, formants, r, rolloff=1.0, f_max=9000.0):
    """One singer: additive harmonics of a pitch track, shaped by the vowel."""
    ph = phase_of(freq)
    out = np.zeros_like(freq)
    n_max = int(f_max / np.min(freq))
    for h in range(1, n_max + 1):
        fh = h * freq
        a = vowel_gain(fh, formants) * top_taper(fh, f_max) / h ** rolloff
        out += a * np.sin(h * ph + r.uniform(0.0, TWO_PI))
    return out


def choir_stab(r, seconds, notes, formants=AH_BRIGHT, singers=3, detune_cents=9.0,
               scoop_cents=30.0, scoop_tau=0.03, vib_cents=10.0, vib_hz=5.3,
               attack=0.012, hold=0.04, tau=0.15, breath=0.05):
    """A short sung chord ("HAH!"), several slightly detuned singers per note."""
    t = timeline(seconds)
    out = np.zeros_like(t)
    for f in notes:
        for s in range(singers):
            cents = detune_cents * (s - (singers - 1) / 2.0) + r.normal(0.0, 2.0)
            scoop = -scoop_cents * np.exp(-t / scoop_tau)
            vib = vib_cents * np.sin(TWO_PI * vib_hz * r.uniform(0.9, 1.1) * t + r.uniform(0.0, TWO_PI))
            freq = f * 2.0 ** ((cents + scoop + vib * np.clip(t / 0.12, 0.0, 1.0)) / 1200.0)
            out += sung(freq, formants, r)
    out = norm(out)
    if breath:
        air = shaped_noise(seconds, lambda tt, f: vowel_gain(f, formants) * hp_shape(f, 600.0, 2), r)
        out += breath * norm(air)
    return tail(out * ad(t, attack, tau, hold), longest=0.05)


def shepard_choir(r, seconds=2.0, spacing=1.5, f_lo=90.0, f_hi=1400.0,
                  singers=((-7.0, 9, 0.006), (0.0, 10, 0.007), (6.0, 11, 0.006)),
                  formants=AH_DISTANT, f_max=6500.0, rolloff=1.0):
    """An endlessly rising sung chord that loops seamlessly (a Shepard-Risset glissando).

    Voices sit `spacing` apart (1.5 = stacked fifths, like parallel organum) and all
    glide up by one step per loop, fading in at f_lo and out at f_hi under a fixed
    envelope, so after one loop every voice has moved into its neighbour's place.
    Voice k+1 starts with exactly the phase voice k ends with, and vibrato runs a
    whole number of cycles per loop, so the last sample flows into the first.
    Each singer entry is (detune in cents, vibrato cycles per loop, vibrato depth).
    """
    N = n_of(seconds)
    extra = 64                                  # render a little past the end to prove the seam
    t = np.arange(N + extra) / SR
    T = N / SR
    lo2, hi2 = np.log2(f_lo), np.log2(f_hi)
    n_voices = int(np.ceil(np.log(f_hi / f_lo) / np.log(spacing)))
    out = np.zeros(N + extra)

    def envelope(f_nominal):
        u = (np.log2(f_nominal) - lo2) / (hi2 - lo2)
        return np.where((u > 0.0) & (u < 1.0), 0.5 - 0.5 * np.cos(TWO_PI * u), 0.0)

    for cents, cycles, depth in singers:
        detune = 2.0 ** (cents / 1200.0)
        vib = depth * np.sin(TWO_PI * cycles * t / T + r.uniform(0.0, TWO_PI))
        base = detune * f_lo * spacing ** (t / T) * (1.0 + vib)     # voice 0's pitch track
        big_phi = phase_of(base)
        phi_loop = TWO_PI * np.sum(base[:N]) / SR                     # voice 0 phase after one loop
        harmonic_phase = r.uniform(0.0, TWO_PI, 512)                  # shared by all voices of a singer
        theta0 = r.uniform(0.0, TWO_PI)
        for k in range(n_voices):
            g = spacing ** k
            env = envelope(f_lo * spacing ** (k + t / T))
            if env.any():
                freq = g * base
                theta = theta0 + g * big_phi
                n_max = int(f_max / np.min(freq[:N]))
                for h in range(1, n_max + 1):
                    fh = h * freq
                    a = env * vowel_gain(fh, formants) * top_taper(fh, f_max) / h ** rolloff
                    out += a * np.sin(h * theta + harmonic_phase[h])
            theta0 += g * phi_loop                                    # hand the phase to voice k+1

    seam_error = np.max(np.abs(out[N:] - out[:extra])) / peak(out)
    if seam_error > 1e-5:
        raise AssertionError(f"shepard_choir is not periodic (seam error {seam_error:.2e})")
    return out[:N]
