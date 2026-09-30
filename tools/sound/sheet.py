"""Contact sheet: the waveform and spectrogram of every sound, labelled, in one PNG."""
from __future__ import annotations

from typing import List, Tuple

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import signal

from dsp import SR, to_db

TILE_W = 480
LABEL_H = 36
WAVE_H = 64
SPEC_H = 120
GAP = 14
COLS = 3
F_LO, F_HI, RANGE_DB = 30.0, 20000.0, 80.0

BG = (18, 20, 26)
PANEL = (10, 11, 15)
TEXT = (228, 232, 240)
DIM = (140, 148, 165)
WAVE = (130, 215, 255)
GUIDE = (70, 76, 92)
CLIP = (255, 70, 70)

# Magma-like colour ramp (position, RGB).
_RAMP = [(0.0, (0, 0, 4)), (0.13, (28, 16, 68)), (0.25, (79, 18, 123)), (0.38, (129, 37, 129)),
         (0.5, (181, 54, 122)), (0.63, (229, 80, 100)), (0.75, (251, 135, 97)),
         (0.88, (254, 194, 135)), (1.0, (252, 253, 191))]


def _colour(v: np.ndarray) -> np.ndarray:
    xs = [p for p, _ in _RAMP]
    cols = np.array([c for _, c in _RAMP], dtype=float)
    return np.stack([np.interp(v, xs, cols[:, i]) for i in range(3)], axis=-1).astype(np.uint8)


def _font(size: int, bold: bool = False):
    names = ("consolab.ttf", "arialbd.ttf") if bold else ("consola.ttf", "arial.ttf")
    for name in names:
        try:
            return ImageFont.truetype(f"C:/Windows/Fonts/{name}", size)
        except OSError:
            continue
    return ImageFont.load_default()


def spectrogram(x: np.ndarray, width: int, height: int, loop: bool = False) -> Image.Image:
    """Log-frequency spectrogram (F_LO..F_HI, top = high) scaled to width x height.

    A loop is padded with its own wrapped audio instead of silence, so its
    edges show what actually plays across the seam.
    """
    # Blackman-Harris: its -92 dB sidelobes keep an 80 dB display honest (Hann
    # leakage would paint a haze under every loud component). Zero-padding to
    # nfft only smooths the picture; resolution comes from the window length.
    nper = 256 if len(x) < 0.12 * SR else 512 if len(x) < 0.4 * SR else 1024
    nfft = 4 * nper
    hop = max(nper // 8, 1)
    if loop:
        padded = np.concatenate([x[-(nper // 2):], x, x[:nper]])
    else:
        padded = np.concatenate([np.zeros(nper // 2), x, np.zeros(nper)])
    _, _, z = signal.stft(padded, fs=SR, window="blackmanharris", nperseg=nper,
                          noverlap=nper - hop, nfft=nfft, boundary=None, padded=False)
    mag = to_db(np.abs(z))                                     # rows: bins, cols: frames
    frame_t = np.arange(mag.shape[1]) * hop / SR               # frame centres in x's time
    # frequency rows (log spaced), linear interpolation between bins
    rows = np.geomspace(F_HI, F_LO, height)
    pos = rows / (SR / nfft)
    i0 = np.clip(np.floor(pos).astype(int), 0, mag.shape[0] - 2)
    frac = (pos - i0)[:, None]
    m = mag[i0] * (1.0 - frac) + mag[i0 + 1] * frac
    # time columns
    col_t = (np.arange(width) + 0.5) / width * (len(x) / SR)
    img = np.stack([np.interp(col_t, frame_t, m[i]) for i in range(height)])
    top = img.max()
    v = np.clip((img - (top - RANGE_DB)) / RANGE_DB, 0.0, 1.0)
    return Image.fromarray(_colour(v), "RGB")


def waveform(draw: ImageDraw.ImageDraw, x: np.ndarray, box: Tuple[int, int, int, int]):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    mid = y0 + h / 2
    draw.rectangle(box, fill=PANEL)
    guide = 10 ** (-1.0 / 20.0)                                # -1 dBFS
    for s in (-1, 1):
        y = mid - s * guide * (h / 2 - 1)
        for gx in range(x0, x1, 6):
            draw.line([(gx, y), (gx + 2, y)], fill=GUIDE)
    draw.line([(x0, mid), (x1 - 1, mid)], fill=GUIDE)
    edges = np.linspace(0, len(x), w + 1).astype(int)
    for i in range(w):
        seg = x[edges[i]:max(edges[i + 1], edges[i] + 1)]
        lo, hi = float(seg.min()), float(seg.max())
        colour = CLIP if max(abs(lo), abs(hi)) >= 0.999 else WAVE
        draw.line([(x0 + i, mid - hi * (h / 2 - 1)), (x0 + i, mid - lo * (h / 2 - 1))], fill=colour)


def make_sheet(items: List[dict], path, title: str):
    """items: dicts with name, audio (float array), and a stats line."""
    rows = (len(items) + COLS - 1) // COLS
    tile_h = LABEL_H + WAVE_H + 2 + SPEC_H + GAP
    header = 64
    width = COLS * (TILE_W + GAP) + GAP
    height = header + rows * tile_h + GAP
    sheet = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(sheet)
    big, small, tiny = _font(22, bold=True), _font(13), _font(11)
    draw.text((GAP, 12), title, font=big, fill=TEXT)
    draw.text((GAP, 40), "Each tile: waveform (full scale, dotted = -1 dBFS, red = clipped) over a "
              "spectrogram (30 Hz to 20 kHz, log, 80 dB range; ticks every 0.1 s). "
              "Time axes are per tile.", font=tiny, fill=DIM)
    for k, item in enumerate(items):
        x0 = GAP + (k % COLS) * (TILE_W + GAP)
        y0 = header + (k // COLS) * tile_h
        draw.text((x0, y0), item["name"], font=_font(15, bold=True), fill=TEXT)
        draw.text((x0, y0 + 18), item["stats"], font=small, fill=DIM)
        wy = y0 + LABEL_H
        waveform(draw, item["audio"], (x0, wy, x0 + TILE_W, wy + WAVE_H))
        sy = wy + WAVE_H + 2
        sheet.paste(spectrogram(item["audio"], TILE_W, SPEC_H, item.get("loop", False)), (x0, sy))
        for f, label in ((100.0, "100"), (1000.0, "1k"), (10000.0, "10k")):
            fy = sy + SPEC_H * np.log(F_HI / f) / np.log(F_HI / F_LO)
            draw.line([(x0 + TILE_W - 22, fy), (x0 + TILE_W - 1, fy)], fill=(200, 200, 210))
            draw.text((x0 + TILE_W - 22, fy - 12), label, font=tiny, fill=(200, 200, 210))
        dur = len(item["audio"]) / SR
        step = 0.1
        for i in range(int(dur / step) + 1):
            tx = x0 + (i * step / dur) * TILE_W
            draw.line([(tx, sy + SPEC_H - 5), (tx, sy + SPEC_H - 1)], fill=(230, 230, 230))
    sheet.save(path)
    return path
