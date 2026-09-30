"""Low-level DSP building blocks for the Cosmic Breach sound set.

Plain numpy and scipy at 44.1 kHz, mono, float64. Nothing here touches files.
layers.py builds sound layers (whooshes, cracks, bells, choirs) from these,
and combat.py / shardling.py mix layers into finished sounds.
"""
from __future__ import annotations

import numpy as np
from scipy import ndimage, signal

SR = 44100
TWO_PI = 2.0 * np.pi


# ---------------------------------------------------------------------------
# Time, gain and mixing

def n_of(seconds: float) -> int:
    """Number of samples in a duration (at least one)."""
    return max(1, int(round(seconds * SR)))


def at(seconds: float) -> int:
    """Sample index of a time offset."""
    return max(0, int(round(seconds * SR)))


def timeline(seconds: float) -> np.ndarray:
    """Sample times, in seconds, for a buffer of the given length."""
    return np.arange(n_of(seconds)) / SR


def silence(seconds: float) -> np.ndarray:
    return np.zeros(n_of(seconds))


def rng(seed: int) -> np.random.Generator:
    return np.random.default_rng(seed)


def db(gain_db: float) -> float:
    """Decibels to a linear amplitude factor."""
    return float(10.0 ** (gain_db / 20.0))


def to_db(x) -> np.ndarray:
    return 20.0 * np.log10(np.maximum(np.abs(x), 1e-12))


def peak(x: np.ndarray) -> float:
    return float(np.max(np.abs(x))) if len(x) else 0.0


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(x)))) if len(x) else 0.0


def norm(x: np.ndarray, to: float = 1.0) -> np.ndarray:
    """Scale to a given peak."""
    p = peak(x)
    return x * (to / p) if p > 0 else x


def place(dst: np.ndarray, src: np.ndarray, when: float = 0.0, gain: float = 1.0) -> np.ndarray:
    """Add src into dst starting at `when` seconds (anything past the end is dropped)."""
    i = at(when)
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += gain * src[:n]
    return dst


def place_wrap(dst: np.ndarray, src: np.ndarray, when: float = 0.0, gain: float = 1.0) -> np.ndarray:
    """Add src into a loop buffer, wrapping past the end back to the start."""
    pos = (at(when) + np.arange(len(src))) % len(dst)
    np.add.at(dst, pos, gain * src)
    return dst


_SEMITONE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6,
             "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def note(name: str) -> float:
    """Equal-tempered frequency of a note name such as 'D6' or 'F#5' (A4 = 440 Hz)."""
    pitch, octave = name[:-1], int(name[-1])
    midi = 12 * (octave + 1) + _SEMITONE[pitch]
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


# ---------------------------------------------------------------------------
# Envelopes

def ramp(n: int) -> np.ndarray:
    """Raised-cosine rise over n samples: exactly 0 first, exactly 1 last."""
    if n <= 1:
        return np.ones(max(n, 0))
    return 0.5 - 0.5 * np.cos(np.pi * np.arange(n) / (n - 1))


def fade(x: np.ndarray, fade_in: float = 0.0, fade_out: float = 0.0) -> np.ndarray:
    """Raised-cosine fades at both ends (first and last samples become exactly 0)."""
    y = np.array(x, dtype=float)
    a = min(at(fade_in), len(y))
    b = min(at(fade_out), len(y))
    if a > 1:
        y[:a] *= ramp(a)
    if b > 1:
        y[len(y) - b:] *= ramp(b)[::-1]
    return y


def rise(t: np.ndarray, attack: float) -> np.ndarray:
    """0 before t = 0, then a raised-cosine rise to 1 over `attack` seconds."""
    if attack <= 0:
        return (t >= 0).astype(float)
    u = np.clip(t / attack, 0.0, 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * u)


def ad(t: np.ndarray, attack: float, tau: float, hold: float = 0.0) -> np.ndarray:
    """Raised-cosine attack, optional hold, then exponential decay with time constant tau."""
    return rise(t, attack) * np.exp(-np.maximum(t - attack - hold, 0.0) / tau)


def bump(t, start: float, length: float, peak_at: float = 0.4, sharp: float = 6.0):
    """Smooth one-humped envelope: 0 at both ends, exactly 1 at start + peak_at * length.

    A beta-distribution shape; `sharp` (a + b) sets how pointed the hump is.
    """
    a = peak_at * sharp
    b = (1.0 - peak_at) * sharp
    u = np.clip((np.asarray(t, dtype=float) - start) / length, 0.0, 1.0)
    top = peak_at ** a * (1.0 - peak_at) ** b
    return (u ** a) * ((1.0 - u) ** b) / top


def smoothstep(u):
    u = np.clip(u, 0.0, 1.0)
    return u * u * (3.0 - 2.0 * u)


def wobble(r: np.random.Generator, n: int, rate_hz: float) -> np.ndarray:
    """Slow random modulation in [-1, 1] (low-passed noise)."""
    pad = 4 * int(SR / max(rate_hz, 1.0))
    x = lp(r.standard_normal(n + pad), rate_hz, 2)[pad:]
    return x / peak(x)


# ---------------------------------------------------------------------------
# Spectral shapes, used by shaped_noise magnitude functions (f in Hz)

def band(f, fc, width_oct):
    """Log-frequency Gaussian bump centred on fc; width is one sigma in octaves."""
    return np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / fc) / width_oct) ** 2)


def hp_shape(f, fc, order: int = 2):
    return 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1.0)) ** (2 * order))


def lp_shape(f, fc, order: int = 2):
    return 1.0 / np.sqrt(1.0 + (np.maximum(f, 1.0) / fc) ** (2 * order))


def tilt(f, db_per_oct: float, ref: float = 1000.0):
    return (np.maximum(f, 1.0) / ref) ** (db_per_oct / 6.0206)


# ---------------------------------------------------------------------------
# Noise

def shaped_noise(seconds: float, mag, r: np.random.Generator, nfft: int = 1024) -> np.ndarray:
    """Noise whose spectrum follows mag(t, f) over time.

    mag receives frame-centre times with shape (frames, 1) and bin frequencies
    with shape (1, bins) and returns magnitudes. Random-phase frames are
    overlap-added with a Hann window at 75 % overlap, which keeps the variance
    constant, so the result is smooth noise with a moving spectral envelope
    (unit variance for mag = 1). Sharp amplitude edges belong in the time
    domain afterwards; this only has frame resolution (nfft / 4 samples).
    """
    n = n_of(seconds)
    hop = nfft // 4
    win = np.hanning(nfft + 1)[:-1]
    n_frames = int(np.ceil(n / hop)) + 3
    starts = (np.arange(n_frames) - 3) * hop
    centres = (starts + nfft / 2) / SR
    freqs = np.fft.rfftfreq(nfft, 1.0 / SR)
    m = np.asarray(mag(centres[:, None], freqs[None, :]), dtype=float)
    m = np.broadcast_to(m, (n_frames, freqs.size))
    z = (r.standard_normal(m.shape) + 1j * r.standard_normal(m.shape)) * m
    z[:, 0] = 0.0
    frames = np.fft.irfft(z, n=nfft, axis=1) * win
    off = 3 * hop
    out = np.zeros(n + 2 * nfft)
    for k in range(n_frames):
        a = starts[k] + off
        out[a:a + nfft] += frames[k]
    return out[off:off + n] * np.sqrt(nfft / 3.0)


# ---------------------------------------------------------------------------
# Filters

def _butter(x, kind, fc, order):
    sos = signal.butter(order, fc, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def hp(x, fc, order: int = 2):
    return _butter(x, "highpass", fc, order)


def lp(x, fc, order: int = 2):
    return _butter(x, "lowpass", fc, order)


def bp(x, lo, hi, order: int = 2):
    return _butter(x, "bandpass", [lo, hi], order)


def rbj(kind: str, f0: float, q: float = 0.7071, gain_db: float = 0.0):
    """Biquad coefficients from the RBJ audio EQ cookbook."""
    A = 10.0 ** (gain_db / 40.0)
    w0 = TWO_PI * f0 / SR
    cw, sw = np.cos(w0), np.sin(w0)
    al = sw / (2.0 * q)
    sA = np.sqrt(A)
    if kind == "peak":
        b = [1 + al * A, -2 * cw, 1 - al * A]
        a = [1 + al / A, -2 * cw, 1 - al / A]
    elif kind == "bandpass":  # 0 dB peak gain
        b = [al, 0.0, -al]
        a = [1 + al, -2 * cw, 1 - al]
    elif kind == "highshelf":
        b = [A * ((A + 1) + (A - 1) * cw + 2 * sA * al),
             -2 * A * ((A - 1) + (A + 1) * cw),
             A * ((A + 1) + (A - 1) * cw - 2 * sA * al)]
        a = [(A + 1) - (A - 1) * cw + 2 * sA * al,
             2 * ((A - 1) - (A + 1) * cw),
             (A + 1) - (A - 1) * cw - 2 * sA * al]
    elif kind == "lowshelf":
        b = [A * ((A + 1) - (A - 1) * cw + 2 * sA * al),
             2 * A * ((A - 1) - (A + 1) * cw),
             A * ((A + 1) - (A - 1) * cw - 2 * sA * al)]
        a = [(A + 1) + (A - 1) * cw + 2 * sA * al,
             -2 * ((A - 1) + (A + 1) * cw),
             (A + 1) + (A - 1) * cw - 2 * sA * al]
    elif kind == "highpass":
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
        a = [1 + al, -2 * cw, 1 - al]
    elif kind == "lowpass":
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
        a = [1 + al, -2 * cw, 1 - al]
    else:
        raise ValueError(kind)
    a0 = a[0]
    return np.array(b) / a0, np.array(a) / a0


def eq(x, kind: str, f0: float, q: float = 0.7071, gain_db: float = 0.0):
    b, a = rbj(kind, f0, q, gain_db)
    return signal.lfilter(b, a, x)


def circular_filter(x: np.ndarray, shape) -> np.ndarray:
    """Zero-phase filter applied around a loop (the result stays seamlessly periodic)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    return np.fft.irfft(spec * shape(f), n=len(x))


def softclip(x: np.ndarray, drive: float) -> np.ndarray:
    """tanh saturation normalised so that full scale stays full scale."""
    return np.tanh(drive * x) / np.tanh(drive)


def limiter(x: np.ndarray, ceiling: float, lookahead: float = 0.0015, release: float = 0.04,
            max_reduction_db: float = 6.0):
    """Lookahead true-peak limiter for one-shot sounds.

    Only the few milliseconds around a peak are turned down (the needle of a
    crack), so a transient-heavy sound can reach its loudness target without
    lowering everything else. Gain dips start `lookahead` before a peak, hold
    across it, and recover with a `release` time constant. Reduction is capped at
    `max_reduction_db`; returns (limited signal, deepest reduction in dB).
    """
    up = np.abs(signal.resample_poly(np.concatenate([x, np.zeros(16)]), 4, 1))
    tp = up[:4 * len(x)].reshape(len(x), 4).max(axis=1)        # per-sample true peak
    need = np.minimum(1.0, ceiling / np.maximum(tp, 1e-12))
    need = np.maximum(need, 10.0 ** (-max_reduction_db / 20.0))
    k = max(1, int(round(lookahead * SR)))
    held = ndimage.minimum_filter1d(need, size=2 * k + 1, mode="nearest")
    rel = np.exp(-1.0 / (release * SR))
    g = np.empty_like(held)
    prev = 1.0
    for i, h in enumerate(held):                                # recover slowly, drop at once
        prev = min(h, 1.0 - (1.0 - prev) * rel)
        g[i] = prev
    # Round the corners. A centred average narrower than the hold keeps every
    # sample at or under its own requirement: all values it averages were
    # min-filtered over a span that includes that sample.
    g = np.convolve(np.pad(g, k, mode="edge"), np.ones(k + 1) / (k + 1), mode="same")[k:k + len(g)]
    return x * g, float(-20.0 * np.log10(g.min()))


# ---------------------------------------------------------------------------
# Oscillators

def phase_of(freq, phase0: float = 0.0) -> np.ndarray:
    """Running phase in radians for a per-sample frequency track (sample 0 = phase0)."""
    freq = np.asarray(freq, dtype=float)
    ph = np.empty_like(freq)
    ph[0] = 0.0
    np.cumsum(freq[:-1], out=ph[1:])
    return phase0 + TWO_PI * ph / SR
