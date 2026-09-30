"""The Starfall's voice (A1): the Choir's last hum, the guide who speaks to the player at a few key moments.

    echo/first_shard  "You can hear it... can't you? Take me home."                 the first Starfall Shard picked up
    echo/ring_open    "The ground remembers the sky. Now... fall up."               the first Breach the player opens
    echo/arrival      "Home. Oh... it's all still singing."                         the first arrival in Aetheria
    echo/drift        "Do you hear that? One more voice. The Drift will carry you now."   the Colossus's first kill
    echo/deep         "Below us, the song goes silent. Bring your light."           the Leviathan's first kill (G5)
    echo/sanctum      "They remember how to sing. He is waiting, at the bottom of the Breach."   the Unsung's (G8)
    echo/sealed       "The Breach is sealed. Sing with us."                         the Heliarch's death (G9)

Each line is a recorded take (Lily, tools/sound/eleven/echo/, see eleven.py) shaped into one voice:

  1. the words: a gentle high-pass (110 Hz, 2nd order: breath rumble and plosive thumps go), a little air (+2 dB
     shelf at 7 kHz), a phrase leveller that brings each whispered phrase 60% of the way up to the line's loudest
     phrase (at most 5 dB, the gain moving only in the pauses, so no word's onset or dynamics change), and a
     peak limiter holding the loudest syllables to 14 dB over the line's programme loudness (a few 10 to 60 ms
     spots a line);
  2. the crystal hum: the shard's own sound (onboarding/shard_hum), a glassy D and A with slow beats, swelling in
     over the 0.7 s before the first word (19 dB under the words' loudest) and sinking 12 dB further as they start;
  3. the shimmer: the words an octave up (phase doubling in the STFT), doubled 2.5 Hz flat and sharp so the two
     beat, high-passed at 1.5 kHz and sent almost all into the reverb, far down;
  4. the reverb: a long bright tail (T60 2.8 s, highs kept to 10 kHz, 35 ms before it starts) at a low wet, so
     the words stay clear and the pauses ring.

Levels: every line is levelled by its gated programme loudness (Event speech=True) to -17 LUFS, 6 LU over the
loudest music track (music/drift, -23.2) and 11 or more over the ambient beds. Vorbis quality 6 (at 5 the breathy
takes' encoding overshot the -1 dBTP ceiling by up to 1.2 dB; at 6 by 0.3). `python tools/sound/voice.py`
measures the encoded files: loudness against the music and beds, peaks, and that every word's onset is intact.

Run:  python tools/sound/build.py echo
"""
from __future__ import annotations

import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from scipy import signal

import eleven
from analysis import integrated, loudness
from dsp import SR, at, db, eq, fade, hp, limiter, lp, n_of, norm, place, rng, rise, shaped_noise, silence, smoothstep, timeline, \
    to_db, wobble, band
from event import Event
from layers import reverb_ir

# The lines: the event name under echo/ and its words (the subtitle, in quotes; the Java EchoLine enum and the
# lang file carry the same words).
LINES = {
    "first_shard": "You can hear it... can't you? Take me home.",
    "ring_open": "The ground remembers the sky. Now... fall up.",
    "arrival": "Home. Oh... it's all still singing.",
    "drift": "Do you hear that? One more voice. The Drift will carry you now.",
    "deep": "Below us, the song goes silent. Bring your light.",
    "sanctum": "They remember how to sing. He is waiting, at the bottom of the Breach.",
    "sealed": "The Breach is sealed. Sing with us.",
}

LEVEL_LUFS = -17.0      # programme loudness of every line
LEAD = 0.9              # the hum swells for this long before the first word
TAIL = 3.0              # room for the reverb after the last word
HP_HZ = 110.0
T60 = 2.8
WET = 0.16              # the words' reverb send (unit-energy room)
SHIMMER_DB = -20.0      # the octave double, relative to the words, before its reverb
HUM_DB = -19.0          # the hum's peak (at the first word), relative to the words' loudest 100 ms
HUM_UNDER_DB = -12.0    # where the hum settles under the words, relative to its peak
HUM_EARLY = 0.2         # the hum peaks this long before the first word, then sinks under it
PEAK_OVER = 14.0        # the words' peaks are held to this many dB over their programme loudness

D4, A4, D5, A5 = 293.66, 440.0, 587.33, 880.0


def first_onset(x: np.ndarray, below_db: float = 40.0) -> float:
    """Seconds to where the take first comes within `below_db` of its loudest 20 ms."""
    env = _envelope_db(x, 0.02)
    return float(np.argmax(env > env.max() - below_db) / SR)


def last_sound(x: np.ndarray, below_db: float = 45.0) -> float:
    env = _envelope_db(x, 0.02)
    return float((len(env) - 1 - np.argmax(env[::-1] > env.max() - below_db)) / SR)


def _envelope_db(x: np.ndarray, window: float) -> np.ndarray:
    w = n_of(window)
    return to_db(np.sqrt(np.convolve(np.square(x), np.ones(w) / w, mode="same")))


def phrases(x: np.ndarray, below_db: float = 35.0, gap: float = 0.25) -> list:
    """(start, end) sample spans of the phrases: runs within `below_db` of the loudest 30 ms, split by `gap` s of quiet."""
    env = _envelope_db(x, 0.03)
    idx = np.nonzero(env > env.max() - below_db)[0]
    cuts = np.nonzero(np.diff(idx) > n_of(gap))[0]
    spans = zip(np.concatenate([[idx[0]], idx[cuts + 1]]), np.concatenate([idx[cuts], [idx[-1]]]))
    return [(int(a), int(b)) for a, b in spans if b - a > n_of(0.15)]


def level_phrases(x: np.ndarray, share: float = 0.6, most: float = 5.0) -> np.ndarray:
    """Brings a whispered phrase part of the way up to the line's loudest phrase (`share` of the difference in
    programme loudness, at most `most` dB). The gain changes only in the pauses, so nothing inside a phrase (its
    words' onsets, its dynamics) changes."""
    spans = phrases(x)
    levels = [integrated(x[a:b]) for a, b in spans]
    top = max(levels)
    boosts = [min(most, share * (top - l)) for l in levels]
    gain = np.full(len(x), boosts[0])
    for (a, b), (a2, _), g, g2 in zip(spans, spans[1:], boosts, boosts[1:]):
        lo, hi = b + n_of(0.02), a2 - n_of(0.02)            # ramp across the pause between the two phrases
        gain[b:] = g2
        if hi > lo:
            gain[lo:hi] = g + (g2 - g) * (0.5 - 0.5 * np.cos(np.pi * np.arange(hi - lo) / (hi - lo)))
            gain[b:lo] = g
    return x * 10.0 ** (gain / 20.0)


def octave_up(x: np.ndarray, nfft: int = 2048, hop: int = 256) -> np.ndarray:
    """The signal an octave up at the same speed: each STFT bin moved to twice its frequency with its phase
    doubled (for a steady partial that keeps the frames coherent)."""
    _, _, z = signal.stft(x, SR, nperseg=nfft, noverlap=nfft - hop)
    out = np.zeros_like(z)
    k = np.arange(z.shape[0] // 2 + 1)
    out[2 * k, :] = np.abs(z[k, :]) * np.exp(2j * np.angle(z[k, :]))
    _, y = signal.istft(out, SR, nperseg=nfft, noverlap=nfft - hop)
    y = y[:len(x)]
    return np.concatenate([y, np.zeros(len(x) - len(y))])


def freq_shift(x: np.ndarray, hz: float) -> np.ndarray:
    """Every partial moved by `hz` (single-sideband): a copy a few Hz off beats against the original."""
    t = np.arange(len(x)) / SR
    return np.real(signal.hilbert(x) * np.exp(2j * np.pi * hz * t))


def crystal_hum(length: float, first_word: float, last_word: float, r) -> np.ndarray:
    """The shard's hum (a glassy D and A with slow beats and a faint high shimmer), swelling in until just before
    the first word, then sinking 12 dB under the words and dying away with the reverb."""
    t = timeline(length)
    hum = np.zeros_like(t)
    for hz, g in ((D4, 1.0), (A4, 0.55), (D5, 0.35), (A5, 0.12)):
        hum += g * (np.sin(2 * np.pi * hz * t) + 0.7 * np.sin(2 * np.pi * hz * 1.0025 * t + 1.3))
    air = shaped_noise(length, lambda tt, f: band(f, 6200.0, 0.6), r)
    x = norm(hum) + db(-24) * norm(air) * (0.6 + 0.4 * wobble(r, len(t), 3.0))
    under, peak_at = db(HUM_UNDER_DB), first_word - HUM_EARLY
    after = under + (1.0 - under) * np.exp(-np.maximum(t - peak_at, 0.0) / 0.25)
    env = np.where(t < peak_at, rise(t, peak_at) ** 1.5, after)
    env *= 1.0 - smoothstep((t - last_word) / 2.2)
    return norm(x) * env


@lru_cache(maxsize=None)
def parts(name: str) -> dict:
    """The line's layers, each as long as the finished line: words (dry), room (their reverb), shimmer, hum."""
    r = rng(1400 + sorted(LINES).index(name))
    take = eleven.sample(f"echo/{name}")
    onset, end = first_onset(take), last_sound(take)
    words = level_phrases(eq(hp(take, HP_HZ, 2), "highshelf", 7000.0, 0.7, 2.0))
    words, _ = limiter(words, db(integrated(words) + PEAK_OVER), max_reduction_db=8.0)
    words = fade(words[at(max(onset - 0.08, 0.0)):at(end + 0.25)], 0.01, 0.15)
    words = norm(words)
    start = LEAD - min(onset, 0.08)            # the first word lands at LEAD
    length = start + len(words) / SR + TAIL
    first_word, last_word = LEAD, start + len(words) / SR

    dry = place(silence(length), words, start)
    room = WET * signal.fftconvolve(dry, reverb_ir(r, T60, damp_hz=10000.0, hp_hz=250.0, predelay=0.035))[:len(dry)]
    up = octave_up(words)
    up = lp(hp(up, 1500.0, 2), 9000.0, 2)
    double = norm(freq_shift(up, -2.5) + freq_shift(up, 2.5))
    words_rms = np.sqrt(np.mean(np.square(words[np.abs(words) > 0.01])))
    double *= words_rms / np.sqrt(np.mean(np.square(double[np.abs(double) > 1e-4]))) * db(SHIMMER_DB)
    shim = place(silence(length), double, start)
    shimmer = 0.15 * shim + signal.fftconvolve(shim, reverb_ir(r, T60 * 1.2, damp_hz=12000.0, hp_hz=800.0,
                                                               predelay=0.06))[:len(shim)]
    hum = crystal_hum(length, first_word, last_word, r)
    hum *= db(loudness(dry) + HUM_DB - loudness(hum))
    return {"words": dry, "room": room, "shimmer": shimmer, "hum": hum, "first_word": first_word,
            "last_word": last_word, "start": start, "take_onset": onset}


def render(name: str) -> np.ndarray:
    p = parts(name)
    return fade(p["words"] + p["room"] + p["shimmer"] + p["hum"], 0.005, 0.5)


def _event(name: str) -> Event:
    return Event(f"echo/{name}", f"\"{LINES[name]}\"", [lambda n=name: render(n)], length=12.0,
                 level=LEVEL_LUFS + 15.0, fade_out=0.8, speech=True, quality=6)


EVENTS = [_event(n) for n in LINES]


# ---------------------------------------------------------------------------------------------------------- checks

def word_onsets(x: np.ndarray, rise_db: float = 12.0, floor_db: float = 20.0) -> list:
    """Word and phrase starts: times where the 10 ms level rises at least `rise_db` within 40 ms to within
    `floor_db` of the loudest (quieter rises are breaths)."""
    env = _envelope_db(x, 0.01)
    top = env.max()
    hop, look = n_of(0.005), n_of(0.04)
    out, last = [], -1.0
    for i in range(look, len(env) - look, hop):
        before, after = env[i - look:i].min(), env[i:i + look].max()
        if after > top - floor_db and after - before >= rise_db and i / SR - last > 0.15:
            out.append(i / SR)
            last = i / SR
    return out


def _contrast(x: np.ndarray, t: float) -> float:
    """dB from the quietest 10 ms in the 60 ms before `t` to the loudest in the 60 ms after."""
    env = _envelope_db(x, 0.01)
    i, w = at(t), n_of(0.06)
    return float(env[i:i + w].max() - env[max(0, i - w):i].min())


def onset_report(name: str) -> dict:
    """How the words' onsets come through the chain, from the layers (no encoding involved):

    lag      samples between the take and the finished words (every step is zero-latency, so 0)
    kept     the smallest rise any word onset still has in the finished words, in dB (the take's rise less what
             the peak limiter took)
    over     the least any word onset stands over everything else sounding in its first 60 ms (room, shimmer,
             hum), loudest 10 ms against loudest 10 ms, in dB
    clarity  the words' energy over the rest's while the words are within 20 dB of their loudest, in dB
    """
    p = parts(name)
    take = hp(eleven.sample(f"echo/{name}"), HP_HZ, 2)
    shift = p["start"] - (p["take_onset"] - min(p["take_onset"], 0.08))
    words, rest = p["words"], p["room"] + p["shimmer"] + p["hum"]
    a = take[at(1.0):at(2.0)]
    b = words[at(1.0 + shift) - 200:at(2.0 + shift) + 200]
    lag = int(np.argmax(signal.correlate(b, a, mode="valid"))) - 200
    ew, er = _envelope_db(words, 0.01), _envelope_db(rest, 0.01)
    onsets = word_onsets(take)
    kept, over = [], []
    for t in onsets:
        kept.append(_contrast(words, t + shift))
        j, m = at(t + shift), n_of(0.06)
        over.append(float(ew[j:j + m].max() - er[j:j + m].max()))
    env = _envelope_db(words, 0.05)
    speaking = env > env.max() - 20.0
    clarity = 10.0 * np.log10(np.sum(np.square(words[speaking])) / np.sum(np.square(rest[speaking])))
    return dict(onsets=len(onsets), lag=lag, kept=min(kept), over=min(over), over_median=float(np.median(over)),
                clarity=float(clarity))


def report() -> int:
    """Measures the encoded lines (run the build first) against the music and beds, and their onsets. Returns
    the number of problems."""
    import soundfile as sf
    from analysis import true_peak
    sounds = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "sounds"
    beds = {}
    for stem in ("music/reach", "music/drift", "music/deep", "music/arrival", "music/colossus", "ambient/reach",
                 "ambient/drift", "ambient/deep"):
        y, _ = sf.read(str(sounds / f"{stem}.ogg"))
        beds[stem] = integrated(y if y.ndim == 1 else y.mean(axis=1))
    music = max(v for k, v in beds.items() if k.startswith("music/"))
    ambient = max(v for k, v in beds.items() if k.startswith("ambient/"))
    print(f"programme loudness: loudest music {music:.1f} LUFS, loudest ambient bed {ambient:.1f} LUFS")
    print(f"{'line':12s} {'sec':>5s} {'words at':>11s} {'LUFS':>6s} {'max100':>6s} {'tpeak':>6s} {'music+':>6s} "
          f"{'quiet+':>6s} {'bed+':>5s} {'clarity':>7s} {'onsets':>6s} {'lag':>3s} {'kept':>5s} {'over':>5s}")
    problems = 0
    for name in LINES:
        y, _ = sf.read(str(sounds / "echo" / f"{name}.ogg"))
        p = parts(name)
        o = onset_report(name)
        prog, tp = integrated(y), float(to_db(true_peak(y)))
        # the quietest phrase (a whisper) as the file plays: the words' phrases, at the gain the build gave the line
        gain = prog - integrated(render(name))
        quiet = min(integrated(p["words"][a:b]) for a, b in phrases(p["words"])) + gain
        notes = []
        if abs(prog - LEVEL_LUFS) > 0.5:
            notes.append(f"programme loudness {prog:.1f}")
        if tp > -0.3:
            notes.append("true peak over -0.3 dBTP")
        if prog - music < 5.0:
            notes.append("less than 5 LU over the music")
        if quiet - music < 3.0:
            notes.append(f"a phrase only {quiet - music:.1f} LU over the music")
        if o["clarity"] < 11.0:
            notes.append("the words less than 11 dB over the rest")
        if o["lag"] != 0:
            notes.append(f"the words moved {o['lag']} samples")
        if o["kept"] < 10.0:
            notes.append(f"an onset rises only {o['kept']:.1f} dB")
        if o["over"] < 6.0:
            notes.append(f"an onset only {o['over']:.1f} dB over the rest")
        problems += len(notes)
        print(f"{name:12s} {len(y) / SR:5.2f} {p['first_word']:5.2f}-{p['last_word']:5.2f} {prog:6.1f} {loudness(y):6.1f} "
              f"{tp:6.1f} {prog - music:6.1f} {quiet - music:6.1f} {prog - ambient:5.1f} {o['clarity']:7.1f} "
              f"{o['onsets']:6d} {o['lag']:3d} {o['kept']:5.1f} {o['over']:5.1f}  {'; '.join(notes)}")
    print(f"\n{problems} problem(s)")
    return problems


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(1 if report() else 0)
