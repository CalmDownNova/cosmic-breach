"""Render, master, encode and check the Cosmic Breach sound set.

    python tools/sound/build.py                     # every event
    python tools/sound/build.py combat/hit          # only events whose names start with this

Writes Ogg Vorbis files to src/main/resources/assets/cosmicbreach/sounds/<category>/,
plus tools/sound/manifest.json and tools/sound/preview/sheet.png. Intermediate
WAVs go to tools/sound/build/, which the repo's .gitignore already ignores.
"""
from __future__ import annotations

import argparse
import importlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy.io import wavfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.dont_write_bytecode = True           # keep __pycache__ out of tools/sound

from analysis import edge_levels, integrated, loudness, seam_report, true_peak  # noqa: E402
from dsp import SR, db, fade, hp, limiter, n_of, to_db  # noqa: E402
from sheet import make_sheet  # noqa: E402

try:
    import soundfile as sf
except ImportError:  # checks fall back to ffmpeg decoding, see decode()
    sf = None

REPO = HERE.parents[1]
SOUNDS = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "sounds"
WORK = HERE / "build"
PREVIEW = HERE / "preview"
NAMESPACE = "cosmicbreach"

# Every module in this folder with an EVENTS list is a sound set, found by name, so a new set needs no
# edit here (and branches adding sets don't collide in this file).
HELPERS = {"analysis", "build", "dsp", "eleven", "event", "layers", "sheet"}
SETS = [importlib.import_module(p.stem) for p in sorted(HERE.glob("*.py"))
        if p.stem not in HELPERS and not p.stem.startswith("_")]
EVENTS = [e for m in SETS for e in getattr(m, "EVENTS", [])]

REFERENCE_LUFS = -15.0   # loudness of combat/hit; every event's `level` is an offset from this
CEILING_DBTP = -1.0      # nothing may peak above this (4x oversampled true peak)
KEEP_HEADROOM_DB = 1.0   # a voice kept at its own level is held this much lower: Vorbis lifted its worst peak 0.9 dB
LIMIT_DB = 6.0           # most the transient limiter may take off a peak...
SPEECH_LIMIT_DB = 4.0    # ...but off a spoken line only this much (the decision after the VP2 re-review)
TRIM_DB = -50.0          # tail below this, relative to the loudest moment, is cut
# Every spoken line must decode at or under CEILING_DBTP once it is encoded (the voice production plan's rule), except
# these four lines shipped before 1.1.0, which decode 0.01 to 0.13 dB over it. They are on players' disks and must stay
# byte for byte, so the build accepts exactly these by name and holds every other spoken line to the ceiling.
SHIPPED_OVER_CEILING = frozenset({"heliarch/voice_collapse", "heliarch/voice_death", "echo/first_shard", "echo/ring_open"})


def master(x: np.ndarray, ev) -> np.ndarray:
    """Remove DC, trim the silent tail, cap the length and fade both ends."""
    x = np.asarray(x, dtype=float)
    if ev.loop:
        want = n_of(ev.length)
        if len(x) != want:
            raise ValueError(f"{ev.name}: loop is {len(x)} samples, expected {want}")
        return x - np.mean(x)
    x = hp(x, 20.0, 2)
    w = n_of(0.005)
    env = np.sqrt(np.convolve(np.square(x), np.ones(w) / w, mode="same"))
    loud = np.nonzero(env > env.max() * db(TRIM_DB))[0]
    natural_end = int(loud[-1]) + w if len(loud) else len(x)
    limit = n_of(ev.length)
    if natural_end < limit:
        x, fade_out = x[:natural_end], 0.01
    else:
        x, fade_out = x[:limit], ev.fade_out
    return fade(x, fade_in=0.001, fade_out=min(fade_out, 0.3 * len(x) / SR))


def level_of(x: np.ndarray, ev) -> float:
    """What an event's `level` measures: its loudest 100 ms, or for a spoken line its gated programme loudness."""
    return integrated(x) if ev.speech else loudness(x, loop=ev.loop)


def set_level(x: np.ndarray, ev):
    """Scale to the event's loudness target under the true-peak ceiling.

    One-shots go through a lookahead limiter that turns down only the
    milliseconds around a peak (at most LIMIT_DB, SPEECH_LIMIT_DB for a spoken line), so transient-heavy sounds
    reach their loudness without being turned down as a whole. Returns
    (signal, deepest limiter reduction in dB, whether the target was missed).
    """
    target = REFERENCE_LUFS + ev.level
    ceiling = db(CEILING_DBTP - ev.headroom_db)
    cap = SPEECH_LIMIT_DB if ev.speech else LIMIT_DB
    if ev.keep_level:                    # levelled speaker by speaker already: only its peaks are held, low
        ceiling *= db(-KEEP_HEADROOM_DB)  # enough that the encoded file stays under CEILING_DBTP too
        y, reduction = limiter(x, ceiling * db(-0.05), max_reduction_db=cap)
        tp = true_peak(y)
        return (y * ceiling / tp, reduction, True) if tp > ceiling else (y, reduction, False)
    if ev.loop:
        y = x * db(target - loudness(x, loop=True))
        tp = true_peak(y, loop=True)
        return (y * ceiling / tp, 0.0, True) if tp > ceiling else (y, 0.0, False)
    gain = db(target - level_of(x, ev))
    for _ in range(5):
        y, reduction = limiter(x * gain, ceiling * db(-0.05), max_reduction_db=cap)
        miss = target - level_of(y, ev)
        if abs(miss) < 0.05:
            break
        gain *= db(miss)
    tp = true_peak(y)
    if tp > ceiling:                     # limiter maxed out: fall back to turning it all down
        return y * ceiling / tp, reduction, True
    return y, reduction, False


def encode(wav: Path, ogg: Path, quality: int = 5):
    # bitexact: a fixed Ogg stream serial and no version-stamped tags, so a rebuild
    # only changes a file when its audio changes (the encode itself is the same).
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", str(wav), "-ac", "1", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", str(quality), "-fflags", "+bitexact",
                    "-flags:a", "+bitexact", str(ogg)], check=True)


def decode(ogg: Path) -> np.ndarray:
    """Decode an .ogg the way a spec-following player does.

    libsndfile (the soundfile package) wraps the reference libvorbis decoder and
    honours the end-of-stream granule position, so it returns exactly the
    encoded length. ffmpeg's built-in Vorbis decoder is only a fallback: on files
    that fit in one Ogg page (most of these) it drops or adds a partial block at
    the end, which would show up as a length mismatch and an unfaded end.
    """
    if sf is not None:
        y, _ = sf.read(str(ogg), dtype="float64", always_2d=True)
        return y[:, 0]
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", str(ogg), "-f", "f32le", "-ac", "1",
                          "-ar", "44100", "-"], check=True, capture_output=True)
    return np.frombuffer(out.stdout, dtype=np.float32).astype(float)


def build_event(ev) -> list:
    rows = []
    for stem, render in zip(ev.file_stems(), ev.variants):
        x, reduction, limited = set_level(master(render(), ev), ev)
        wav, ogg = WORK / f"{stem}.wav", SOUNDS / f"{stem}.ogg"
        wav.parent.mkdir(parents=True, exist_ok=True)
        ogg.parent.mkdir(parents=True, exist_ok=True)
        wavfile.write(wav, SR, x.astype(np.float32))
        encode(wav, ogg, ev.quality)
        y = decode(ogg)
        row = dict(stem=stem, seconds=len(y) / SR, wav_samples=len(x), ogg_samples=len(y),
                   peak=float(to_db(np.max(np.abs(y)))),
                   true_peak=float(to_db(true_peak(y, ev.loop))),
                   lufs=level_of(y, ev), target=level_of(y, ev) if ev.keep_level else REFERENCE_LUFS + ev.level,
                   limited=limited, reduction=reduction, edges=edge_levels(y), loop=ev.loop,
                   length=ev.length, speech=ev.speech)
        if ev.loop:
            row["seam_wav"] = seam_report(x)
            row["seam_ogg"] = seam_report(y, reference=x if len(x) == len(y) else None)
        rows.append(row)
    return rows


def warnings_for(row) -> list:
    w = []
    if row["ogg_samples"] != row["wav_samples"] and sf is not None:
        w.append(f"decoded length {row['ogg_samples']} != {row['wav_samples']}")
    if row["seconds"] > row["length"] + 0.001:
        w.append("longer than allowed")
    if row["true_peak"] > -0.3:
        w.append(f"true peak {row['true_peak']:+.2f} dBTP")
    elif row["speech"] and row["true_peak"] > CEILING_DBTP and row["stem"] not in SHIPPED_OVER_CEILING:
        w.append(f"spoken line decodes at {row['true_peak']:+.2f} dBTP, over the {CEILING_DBTP:.1f} ceiling")
    if row["lufs"] < -60:
        w.append("nearly silent")
    first, onset, last = row["edges"]
    # Vorbis smears a little of a hard onset back onto sample 0 (pre-echo). That
    # step is masked when a transient at least 25 dB louder follows within 5 ms.
    if not row["loop"] and ((first > -60 and onset - first < 25) or last > -60):
        w.append(f"edge not silent (first {first:.0f}, end {last:.0f} dBFS)")
    if row["limited"]:
        w.append("held down by the peak ceiling")
    if row["reduction"] > 0.05:
        w.append(f"limiter -{row['reduction']:.1f} dB")
    if row["loop"]:
        s = row["seam_ogg"]
        if s["jump_ratio"] > 1.0 or (s["hf_ratio"] > 3.0 and s["hf_seam_db"] > -45.0):
            w.append("loop seam suspicious")
        if "codec_err_seam_db" in s and s["codec_err_seam_db"] > s["codec_err_mid_db"] + 3.0:
            w.append("codec error is worse at the seam")
    return w


def write_manifest():
    data = {}
    for ev in EVENTS:
        entry = {"files": [f"{NAMESPACE}:{s}" for s in ev.file_stems()], "subtitle": ev.subtitle}
        if ev.loop:
            entry["loop"] = True
        data[ev.name] = entry
    (HERE / "manifest.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_sheet():
    items = []
    for ev in EVENTS:
        for stem in ev.file_stems():
            ogg = SOUNDS / f"{stem}.ogg"
            if not ogg.exists():
                continue
            y = decode(ogg)
            stats = (f"{len(y) / SR:.3f} s   peak {float(to_db(np.max(np.abs(y)))):+.1f} dBFS   "
                     f"{loudness(y, loop=ev.loop):.1f} LUFS")
            if ev.loop:
                stats += f"   seam step x{seam_report(y)['jump_ratio']:.2f}"
            items.append(dict(name=stem, audio=y, stats=stats, loop=ev.loop))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    make_sheet(items, PREVIEW / "sheet.png", f"Cosmic Breach sounds ({len(items)} files)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("only", nargs="*", help="build only events whose names start with these")
    parser.add_argument("--no-sheet", action="store_true", help="skip the contact sheet")
    args = parser.parse_args()
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg is not on PATH; it is needed to encode Ogg Vorbis.")

    chosen = [ev for ev in EVENTS if not args.only or any(ev.name.startswith(p) for p in args.only)]
    if not chosen:
        sys.exit(f"no events match {args.only}")

    print(f"{'file':32s} {'sec':>6s} {'peak':>6s} {'tpeak':>6s} {'LUFS':>6s} {'target':>6s}  notes")
    problems = 0
    for ev in chosen:
        rows = build_event(ev)
        spread = max(r["lufs"] for r in rows) - min(r["lufs"] for r in rows)
        for row in rows:
            notes = warnings_for(row)
            if len(rows) > 1 and spread > 0.5:
                notes.append(f"variants differ by {spread:.1f} LU")
            if row["loop"]:
                s1, s2 = row["seam_wav"], row["seam_ogg"]
                seam = (f"seam: wav jump {s1['jump_ratio']:.2f} hf x{s1['hf_ratio']:.2f} "
                        f"({s1['hf_seam_db']:.0f} dB); ogg jump {s2['jump_ratio']:.2f} "
                        f"hf x{s2['hf_ratio']:.2f} ({s2['hf_seam_db']:.0f} dB)")
                if "codec_err_seam_db" in s2:
                    seam += (f"; codec error {s2['codec_err_seam_db']:.1f} dB at seam vs "
                             f"{s2['codec_err_mid_db']:.1f} dB mid")
                notes.insert(0, seam)
            problems += sum(1 for n in notes if not n.startswith(("seam:", "limiter")))
            print(f"{row['stem']:32s} {row['seconds']:6.3f} {row['peak']:6.1f} {row['true_peak']:6.1f} "
                  f"{row['lufs']:6.1f} {row['target']:6.1f}  {'; '.join(notes)}")

    write_manifest()
    if not args.no_sheet:
        write_sheet()
    print("\nLoudness targets by category (loudest 100 ms, LUFS):")
    for category in sorted({e.name.split("/")[0] for e in EVENTS}):
        evs = sorted((e for e in EVENTS if e.name.startswith(category + "/")), key=lambda e: -e.level)
        print(f"  {category}: " + ", ".join(f"{e.name.split('/')[1]} {REFERENCE_LUFS + e.level:.1f}"
                                            + (" (programme)" if e.speech else "") for e in evs))
    print(f"\n{problems} warning(s). Manifest: {HERE / 'manifest.json'}")


if __name__ == "__main__":
    main()
