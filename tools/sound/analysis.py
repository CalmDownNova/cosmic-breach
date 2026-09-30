"""Level and integrity measurements shared by the build and the contact sheet."""
from __future__ import annotations

import numpy as np
from scipy import signal

from dsp import SR, hp, n_of, rbj, to_db

# BS.1770 K-weighting (the shelf and high-pass of LUFS meters), computed for 44.1 kHz.
_K_STAGES = (("highshelf", 1681.974450955533, 0.7071752369554196, 3.999843853973347),
             ("highpass", 38.13547087602444, 0.5003270373253953, 0.0))


def k_weight(x: np.ndarray) -> np.ndarray:
    for kind, f0, q, g in _K_STAGES:
        b, a = rbj(kind, f0, q, g)
        x = signal.lfilter(b, a, x)
    return x


def loudness_curve(x: np.ndarray, window: float = 0.1, hop: float = 0.005, loop: bool = False):
    """K-weighted loudness (LUFS scale) in sliding windows.

    The window is 100 ms rather than the 400 ms of a momentary meter because
    most of these sounds are shorter than 400 ms; 100 ms is close to the ear's
    loudness integration time for short sounds.
    """
    w = n_of(window)
    if loop:
        y = k_weight(np.tile(x, 3))[len(x):2 * len(x)]      # settled filter state, wrapped
        y = np.concatenate([y, y[:w]])
    else:
        y = k_weight(np.concatenate([x, np.zeros(w)]))
    p = np.square(y)
    c = np.concatenate([[0.0], np.cumsum(p)])
    starts = np.arange(0, len(p) - w + 1, max(1, n_of(hop)))
    ms = (c[starts + w] - c[starts]) / w
    return -0.691 + 10.0 * np.log10(np.maximum(ms, 1e-20))


def loudness(x: np.ndarray, loop: bool = False) -> float:
    """Loudest 100 ms of the sound, in LUFS (K-weighted)."""
    return float(np.max(loudness_curve(x, loop=loop)))


def integrated(x: np.ndarray) -> float:
    """Gated programme loudness in LUFS (ITU-R BS.1770-4, what a loudness meter reads for speech and music).

    K-weighted mean square in 400 ms blocks every 100 ms; blocks under -70 LUFS are dropped, then blocks more
    than 10 LU under the mean of the rest. Spoken lines are levelled by this (their loudest 100 ms is one
    stressed syllable and says little about how loud the words sound).
    """
    b, h = n_of(0.4), n_of(0.1)
    y = k_weight(np.concatenate([x, np.zeros(max(0, b - len(x)))]))
    c = np.concatenate([[0.0], np.cumsum(np.square(y))])
    starts = np.arange(0, len(y) - b + 1, h)
    ms = (c[starts + b] - c[starts]) / b
    lufs = -0.691 + 10.0 * np.log10(np.maximum(ms, 1e-20))
    keep = lufs > -70.0
    if not keep.any():
        return -70.0
    relative = -0.691 + 10.0 * np.log10(np.mean(ms[keep])) - 10.0
    keep &= lufs > relative
    return float(-0.691 + 10.0 * np.log10(np.mean(ms[keep])))


def true_peak(x: np.ndarray, loop: bool = False) -> float:
    """Peak of the 4x oversampled signal (catches peaks between samples)."""
    y = np.tile(x, 2) if loop else np.concatenate([x, np.zeros(64)])
    return float(np.max(np.abs(signal.resample_poly(y, 4, 1))))


def edge_levels(x: np.ndarray, n: int = 32):
    """(first sample, loudest of the first 5 ms, loudest of the last n samples) in dBFS.

    The first sample is where playback steps in from silence and the last n
    samples are where it steps out. A small first sample is harmless when a much
    louder transient follows within a few milliseconds (it is masked); it would
    be an audible tick only in front of a quiet start.
    """
    return (float(to_db(abs(x[0]))), float(to_db(np.max(np.abs(x[:n_of(0.005)])))),
            float(to_db(np.max(np.abs(x[-n:])))))


def seam_report(x: np.ndarray, reference: np.ndarray = None) -> dict:
    """How the wrap point of a loop compares with the rest of it.

    jump_ratio: the step from the last sample to the first, divided by the 99th
    percentile of ordinary sample-to-sample steps (under 1 means unremarkable).
    hf_ratio: energy above 8 kHz in a 5 ms window centred on the seam, divided by
    the median of the same measure elsewhere (a click would be many times 1).
    hf_seam_db: that seam energy relative to the loop's overall energy. When the
    loop has almost nothing above 8 kHz, codec noise alone can raise hf_ratio,
    so a high ratio only matters if this level is also high (above about -45 dB).
    With `reference` (the pre-encoding loop), codec error near the seam versus
    mid-loop is reported too, relative to the loop's energy.
    """
    steps = np.abs(np.diff(np.concatenate([x, x[:1]])))
    jump_ratio = steps[-1] / np.percentile(steps[:-1], 99)
    n = len(x)
    w = n_of(0.005)
    y2 = np.square(hp(np.tile(x, 3), 8000.0, 4))              # filter has settled by copy two
    s = 2 * n                                                  # seam between copies two and three
    seam = np.mean(y2[s - w // 2:s + w // 2])
    e = np.convolve(y2[n:2 * n], np.ones(w) / w, mode="valid")  # same measure across copy two
    energy = np.mean(np.square(x))
    out = {"jump_ratio": float(jump_ratio), "hf_ratio": float(seam / np.median(e)),
           "hf_seam_db": float(10 * np.log10(seam / energy + 1e-30))}
    if reference is not None:
        err = np.square(x - reference)
        k = n_of(0.01)
        at_seam = np.mean(np.concatenate([err[:k], err[-k:]]))
        out["codec_err_seam_db"] = float(10 * np.log10(at_seam / energy + 1e-30))
        out["codec_err_mid_db"] = float(10 * np.log10(np.mean(err[k:-k]) / energy + 1e-30))
    return out
