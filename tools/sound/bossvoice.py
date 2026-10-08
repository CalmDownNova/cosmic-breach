"""The boss voices of 1.1.0 (vault: "Aetheria 1.1 Voice Script v1"): the Prism Colossus, the Thalassine Leviathan
and the three masks of the Unsung. Boss 4's new lines go through heliarch.py's own chain instead.

Every line is a kept take under tools/sound/eleven/bossvoice/ (made once by voicebatch.py through eleven.py, never at
build time), shaped here:

  the Colossus   the words resampled 9% slower and 1.5 semitones lower (a bigger body), cleaned and levelled like
                 boss 4's; a glass ring (the words through six tuned crystal resonators, D major pentatonic like the
                 Reach) 17 dB under; a stone rumble riding the words 24 dB under; open sky round the crown (a short slap
                 off two crystals and a faint bright tail) 15 dB under. "shatter" also splits into three voices.
  the Leviathan  the words cleaned and levelled, no pitch change; a slow chorus (two copies 1.8 Hz flat and sharp,
                 19 and 27 ms late) 9 dB under; its own whale song (eleven/leviathan/song_take1, low-passed) swelling
                 with the words 21 dB under; a long airy hall (T60 3.8 s) with a slow shimmer 12 dB under.
  the Unsung     each fragment in its mask's voice, trimmed and brought to one loudness, placed on Vesper's grid (the
                 openers 2 beats apart, one per mask's lift; the kill line a beat apart; the rest on the first half
                 beat at least 60 ms after the last fragment); then one chain so the three read as one choir: a
                 porcelain colour (2.6 kHz presence, a little 900 Hz box, a short bright comb 15 dB under), the other
                 living masks humming their notes of D minor under each fragment 23 dB down, the Nave (T60 4.2 s)
                 11 dB under (8 for the ghosts of the kill line).
Every line -16 LUFS programme loudness (speech=True), like boss 4, Vorbis quality 6, except the Unsung's: those play
over music, so each mask's words are set to its own loudness (SPEAKER_LUFS over her choir, GHOST_LUFS for the kill
line's ghosts over the soft chord that rings at the death) to sit at least 3 LU over it, and the build only holds their
peaks (Event.keep_level). No limiter in these chains takes more than LIMIT_MAX_DB (4 dB) off a line: a fragment whose
peaks would need more is held below its mask's level instead (unsung_parts). Events exist only for lines whose kept
takes exist, so the build never needs a take that is not there.

Run:  python tools/sound/build.py bossvoice
"""
from __future__ import annotations

import json
import re
import zlib
from functools import lru_cache
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.signal import resample_poly

import eleven
from analysis import integrated, true_peak
from dsp import SR, at, db, eq, fade, hp, limiter, lp, lp_shape, n_of, norm, place, rbj, rng, shaped_noise, silence, wobble
from event import Event
from heliarch import ENCODER_HEADROOM_DB, comb, envelope
from layers import reverb_ir
from unsung import MM, voice as sung
from voice import first_onset, freq_shift, last_sound, level_phrases

SCRIPT = Path(__file__).resolve().parent / "eleven" / "voice_script.json"
STITCH = Path(__file__).resolve().parent / "eleven" / "voice_stitch.json"   # the lines production let overlap
TEMPO = Path(__file__).resolve().parent / "eleven" / "voice_tempo.json"     # the lines production let speak faster
TEMPO_MAX = {"leviathan": 1.15}  # the decision: the Leviathan may speak up to 1.15x faster before any text is cut
LEVEL_LUFS = -16.0            # programme loudness of every line, like boss 4's voice...
# ...but the Unsung's lines over her music, which is her masks singing: each mask's words are set to its own loudness, so
# they sit at least MARGIN_LU (voicebatch.py: 3 LU, the decision after the VP2 re-review) over the loudest stretch of the
# choir of the same length (voicebatch.py margins measures it). A mask is only as loud as its own peaks allow: no limiter
# in these chains takes more than LIMIT_MAX_DB, so a fragment whose peaks would need more is held below its level
# (unsung_parts says by how much). These are the levels the masks are set to: the lowest margin of a fragment that
# nothing holds down is 3.3 to 3.5 LU.
SPEAKER_LUFS = {1: -15.5, 2: -16.0, 3: -16.2}
# The kill line is the one Unsung line her choir is not under: it stops at the death, but the soft chord the server
# plays at death tick 24 still rings when the ghosts start at tick 36, so each ghost is levelled the same way, to sit
# over that chord (voicebatch.py margins). They keep the loudness the line had before the masks were levelled.
GHOST_LUFS = {1: -17.0, 2: -17.0, 3: -17.0}
LIMIT_MAX_DB = 4.0            # the most any limiter in these chains takes off a peak (build.SPEECH_LIMIT_DB: the same cap)
HOLD_MAX_DB = LIMIT_MAX_DB    # ...the peak hold ahead of the levelling (PEAK_OVER) included. It was 8 dB: with 8 the two
                              # spikiest fragments (mask 1 of the solo opener, mask 3 of the returning opener) reach 3 LU
KEEP_CEILING_DB = -2.0        # where the build holds a levelled voice's peaks: its -1 dBTP ceiling less 1 dB for the encoder
# ENCODER_HEADROOM_DB (heliarch.py, 0.5): the Colossus's and the Leviathan's lines hold their peaks this far under that
# ceiling, as boss 4's new lines do. Vorbis lifted the worst of them 0.2 dB over it, and the plan wants -1 dBTP after encoding
FIT_PASSES = 6                # passes a mixed line is fitted to the cap in (unsung_parts)
FIT_MARGIN = 0.1              # dB under the cap that fit aims at: the limiter's own 0.05 dB, and a little more
PEAK_OVER = 13.0              # the words' loudest syllables held to this many dB over their programme loudness
COLOSSUS_DOWN = 1.09          # its resample: 9% slower and 1.5 semitones lower (voicebatch.STRETCH matches)
GLASS_HZ = (293.66, 440.0, 587.33, 739.99, 880.0, 1174.66)   # D4 A4 D5 F#5 A5 D6
BEAT = 0.6                    # Vesper at 100 BPM
MIN_GAP = 0.06                # the least silence between two masks' fragments...
MAX_OVERLAP = 0.16            # ...unless the line cannot fit: then two masks' words may overlap ("about 150 ms")
PAD = {False: (0.03, 0.12), True: (0.01, 0.04)}             # kept before and after a fragment's words: plain, whispered
LIFT_ROOM = 2 * BEAT - MIN_GAP                               # an opener's fragment, as placed, at most this long (1.14 s)
LEAD = 0.1                    # silence before the Unsung's first fragment
GRIDS = {"fight_start": "lift", "boss_kill": "beat"}         # every other Unsung line: "half"
WHISPER = re.compile(r"\[[^\]]*whisper[^\]]*\]", re.IGNORECASE)   # a fragment the script asks to be whispered
HUM_HZ = {1: 440.0, 2: 349.23, 3: 146.83}                    # the masks' D minor hum: Alto A4, Tenor F4, Bass D3
LIVING = {"relay": ["123"], "pair": ["12", "13", "23"], "alone": ["1", "2", "3"]}


def _lines() -> list:
    if not SCRIPT.exists():
        return []
    rows = json.loads(SCRIPT.read_text(encoding="utf-8"))["lines"]
    return [r for r in rows if r["boss"] in ("colossus", "leviathan", "unsung")]


def _line(boss: str, line_id: str) -> dict:
    for r in _lines():
        if r["boss"] == boss and r["id"] == line_id:
            return r
    raise KeyError(f"{boss}/{line_id}")


def _rng(key: str):
    """A seed from the line's own name, so adding a line never changes another line's file."""
    return rng(zlib.crc32(key.encode("utf-8")) % 1_000_000)


def _have(name: str) -> bool:
    return (eleven.OUT / f"{name}.wav").exists()


def late(x: np.ndarray, seconds: float) -> np.ndarray:
    return np.concatenate([np.zeros(n_of(seconds)), x])[:len(x)]


def under(layer: np.ndarray, dry: np.ndarray, below_db: float) -> np.ndarray:
    """`layer` scaled so its energy while the words sound is `below_db` under theirs."""
    env = envelope(dry, 0.05)
    speaking = env > env.max() * 0.1
    e_words, e_layer = np.sum(np.square(dry[speaking])), np.sum(np.square(layer[speaking]))
    return layer * (np.sqrt(e_words / e_layer) * db(-below_db)) if e_layer > 0 else layer * 0.0


def _level(x: np.ndarray) -> np.ndarray:
    try:
        return level_phrases(x, share=0.5, most=4.0)
    except ValueError:            # too short to hold a phrase: nothing to even out
        return x


def words_of(take: np.ndarray, down: float = 1.0) -> np.ndarray:
    """A take trimmed to its words (and 0.3 s after), optionally resampled `down` (slower and lower), cleaned and
    levelled as boss 4's words are."""
    onset, end = first_onset(take), last_sound(take)
    x = take[at(max(onset - 0.06, 0.0)):at(end + 0.3)]
    if down != 1.0:
        x = resample_poly(x, int(round(down * 100)), 100)
    x = hp(x, 70.0, 2)
    x = eq(x, "peak", 350.0, 1.0, -2.5)
    x = eq(x, "peak", 3000.0, 0.9, 1.5)
    x = _level(x)
    x, _ = limiter(x, db(integrated(x) + PEAK_OVER), max_reduction_db=HOLD_MAX_DB)
    return norm(fade(x, 0.01, 0.2))


def spoken_span(words: np.ndarray) -> tuple:
    """(start, end) in seconds of the words in a placed or built track: the one measure the take check, the take
    picker and the manifest all use, so a take that passes its check builds inside its budget."""
    return first_onset(words), last_sound(words, 35.0)


# ---------------------------------------------------------------------------------------------------- the Colossus

@lru_cache(maxsize=None)
def colossus_parts(line_id: str) -> dict:
    r = _rng(f"colossus/{line_id}")
    words = words_of(eleven.sample(f"bossvoice/colossus/{line_id}"), down=COLOSSUS_DOWN)
    lead = 0.12
    length = lead + len(words) / SR + 2.6
    dry = place(silence(length), words, lead)
    glass = np.zeros_like(dry)
    for f in GLASS_HZ:
        b, a = rbj("bandpass", f, 40.0)
        glass += signal.lfilter(b, a, dry)
    glass = under(hp(glass, 250.0, 2), dry, 17.0)
    smooth = np.convolve(envelope(dry, 0.08), np.ones(n_of(0.15)) / n_of(0.15), mode="same")
    rumble = shaped_noise(length, lambda tt, f: lp_shape(f, 110.0, 2), r)[:len(dry)]
    rumble = under(rumble * smooth / max(smooth.max(), 1e-9), dry, 24.0)
    slap = np.zeros(n_of(0.08))
    slap[n_of(0.045)], slap[n_of(0.071)] = 1.0, 0.6                  # two crown crystals answering
    send = dry + 0.5 * glass
    early = signal.fftconvolve(send, slap)[:len(dry)]
    tail = signal.fftconvolve(send, reverb_ir(r, 2.2, damp_hz=7000.0, hp_hz=220.0, predelay=0.03))[:len(dry)]
    parts = {"words": dry, "glass": glass, "rumble": rumble, "room": under(early + 0.8 * tail, dry, 15.0)}
    if line_id == "shatter":                                       # it splits in three, like its body
        parts["split"] = under(late(freq_shift(dry, 55.0), 0.012) + late(freq_shift(dry, -35.0), 0.023), dry, 6.0)
    return parts


# ---------------------------------------------------------------------------------------------------- the Leviathan

def leviathan_parts(line_id: str) -> dict:
    """The Leviathan's line as built: its words at the tempo production recorded for it (tempo_of), then its chorus,
    the song bed under it and its hall."""
    return _leviathan_parts(line_id, tempo_of("leviathan", line_id))


@lru_cache(maxsize=None)
def _leviathan_parts(line_id: str, tempo: float) -> dict:
    r = _rng(f"leviathan/{line_id}")
    words = words_of(stretch(eleven.sample(f"bossvoice/leviathan/{line_id}"), tempo))
    lead = 0.12
    length = lead + len(words) / SR + 3.4
    dry = place(silence(length), words, lead)
    chorus = under(hp(late(freq_shift(dry, 1.8), 0.019) + late(freq_shift(dry, -1.8), 0.027), 150.0, 2), dry, 9.0)
    bed = np.resize(lp(eleven.sample("leviathan/song_take1"), 900.0, 2), len(dry))
    smooth = np.convolve(envelope(dry, 0.1), np.ones(n_of(0.4)) / n_of(0.4), mode="same")
    bed = under(bed * smooth / max(smooth.max(), 1e-9), dry, 21.0)
    hall = signal.fftconvolve(dry + 0.5 * chorus, reverb_ir(r, 3.8, damp_hz=5000.0, hp_hz=160.0, predelay=0.06))[:len(dry)]
    hall *= 0.85 + 0.15 * wobble(r, len(hall), 0.3)
    return {"words": dry, "chorus": chorus, "bed": bed, "room": under(hall, dry, 12.0)}


leviathan_parts.cache_clear = _leviathan_parts.cache_clear


# ---------------------------------------------------------------------------------------------------- the Unsung

def assign(line: dict, living: str) -> list:
    """(fragment number, mask) for each fragment as spoken with these masks alive ("123", "13", "2", ...)."""
    sp = line["split"]
    if sp["kind"] == "relay":
        return [(k + 1, int(f["tag"][1])) for k, f in enumerate(sp["fragments"])]
    alive = [int(c) for c in living]
    if sp["kind"] == "pair":
        return [(1, alive[0]), (2, alive[1])]
    return [(1, alive[0])]


def onsets(lengths: list, grid: str, gap: float = MIN_GAP) -> list:
    """Where each fragment starts, in seconds from the first. "lift": 2 beats apart (one per mask's lift); "beat" and
    "half": the first beat (or half beat) at least `gap` after the previous fragment ends. A fragment never starts
    before the previous one has ended plus `gap`: MIN_GAP of silence, or -MAX_OVERLAP for a line production let
    overlap (gap_of). "lockstep": every fragment on its own beat, a beat apart, however long the one before."""
    starts, end = [], 0.0
    for i, n in enumerate(lengths):
        if i == 0:
            s = 0.0
        elif grid == "lockstep":
            s = BEAT * i
        elif grid == "lift":
            s = max(2 * BEAT * i, end + gap)
        else:
            step = BEAT if grid == "beat" else BEAT / 2.0
            s = float(np.ceil((end + gap) / step - 1e-9) * step)
        starts.append(round(s, 6))
        end = s + n
    return starts


def grid_of(line: dict) -> str:
    """The Unsung line's grid: "lift" for the openers, "beat" for the kill line, "half" for the rest."""
    return GRIDS.get(line["trigger"], "half")


def overlap_gap(whispered: bool) -> float:
    """The gap between two placed fragments (negative: an overlap) at which the masks' words overlap by MAX_OVERLAP:
    the "about 150 ms" the VP2 decision allows (160 ms at most), plus the padding each placed fragment carries around
    its words (trim_fragment pads a take cut closer), which is no voice speaking (-0.31 s for plain fragments, -0.21
    s for whispered ones)."""
    pre, post = PAD[whispered]
    return -(MAX_OVERLAP + pre + post)


def gap_of(line_id: str) -> float:
    """The least gap between two of the line's fragments: MIN_GAP, or the overlap voicebatch.py's pick chose for a
    relayed line that cannot fit its limit otherwise (eleven/voice_stitch.json), so the build places it the same way."""
    if not STITCH.exists():
        return MIN_GAP
    return float(json.loads(STITCH.read_text(encoding="utf-8")).get(line_id, {}).get("gap", MIN_GAP))


def tempo_of(boss: str, line_id: str) -> float:
    """How much faster than its take a line plays: 1.0, or the tempo voicebatch.py's pick chose for a line that cannot
    fit its limit otherwise (eleven/voice_tempo.json, "<boss>/<line id>"), so check and the build measure the same."""
    if not TEMPO.exists():
        return 1.0
    return float(json.loads(TEMPO.read_text(encoding="utf-8")).get(f"{boss}/{line_id}", {}).get("tempo", 1.0))


def stretch(x: np.ndarray, tempo: float) -> np.ndarray:
    """`x` played `tempo` times as fast with its pitch kept (WSOLA): 40 ms Hann frames laid half a frame apart, each
    read from within 10 ms of where the tempo puts it, wherever it best continues the frame before (its waveform
    lines up, so no pitch moves and no phase smears). A tempo of 1.0 returns `x` itself."""
    if abs(tempo - 1.0) < 1e-9:
        return x
    n = n_of(0.04)
    hop = n // 2
    tol = n_of(0.01)
    window = 0.5 - 0.5 * np.cos(2.0 * np.pi * np.arange(n) / n)          # periodic Hann: halves overlap-add to 1
    length = int(round(len(x) / tempo))
    frames = length // hop + 1
    src = np.concatenate([np.zeros(tol), x, np.zeros(2 * n + 2 * tol + int(np.ceil(hop * tempo)))])
    out = np.zeros(frames * hop + n)
    prev = None
    for k in range(frames):
        ideal = tol + int(round(k * hop * tempo))
        if prev is None:
            at_ = ideal
        else:
            natural = src[prev + hop:prev + hop + n]                    # how the frame before would carry on
            region = src[ideal - tol:ideal + tol + n]
            at_ = ideal - tol + int(np.argmax(signal.correlate(region, natural, mode="valid", method="fft")))
        out[k * hop:k * hop + n] += window * src[at_:at_ + n]
        prev = at_
    return out[:length]


def trim_fragment(take: np.ndarray, whispered: bool = False) -> np.ndarray:
    """A take as it is placed: from just before its first sound to just after its last, high-passed, its peaks held,
    brought to one loudness so the masks match. A fragment the script asks to be whispered is cut to its words (30
    dB down, 10 ms before and 40 ms after, not 40 and 45 dB with 30 and 120 ms): a whisper's breath around its words
    would otherwise eat the beat the next mask speaks on. A take that holds less than that before its first sound or
    after its last (eleven_v4 often ends a take on its last sound) gets the rest as silence, so every placed fragment
    carries its full PAD and overlap_gap means what it says."""
    pre, post = PAD[whispered]
    if whispered:
        onset, end, out = first_onset(take, 30.0), last_sound(take, 30.0), 0.03
    else:
        onset, end, out = first_onset(take), last_sound(take), 0.06
    x = hp(take[at(max(onset - pre, 0.0)):at(end + post)], 90.0, 2)
    x, _ = limiter(x, db(integrated(x) + PEAK_OVER), max_reduction_db=HOLD_MAX_DB)
    x = fade(x, 0.008, out)
    x = x * db(-20.0 - integrated(x))
    before, after = at(max(pre - onset, 0.0)), at(max(end + post - len(take) / SR, 0.0))
    return np.concatenate([np.zeros(before), x, np.zeros(after)])


def speaker_lufs(line: dict, mask: int) -> float:
    """The loudness a mask's words are held at in a line: over her choir (SPEAKER_LUFS), or for the kill line's ghosts
    over the soft chord that rings at the death (GHOST_LUFS)."""
    return (GHOST_LUFS if line["trigger"] == "boss_kill" else SPEAKER_LUFS)[mask]


def level_fragment(piece: np.ndarray, target: float) -> np.ndarray:
    """A trimmed fragment in its mask's own voice level: the porcelain colour on it, its loudness set to `target` LUFS.
    Nothing is limited here (VP2 re-review N2: shaving the spikes of a voice made this loud took up to 10.6 dB off
    them, fast, on the low voice): the build's limiter, capped at LIMIT_MAX_DB, holds the peaks, so a mask is only as
    loud as its own peaks allow."""
    x = colour(piece)
    return x * db(target - integrated(x))


def fragment(line_id: str, k: int, mask: int) -> np.ndarray:
    text = _line("unsung", line_id)["split"]["fragments"][k - 1]["text"]
    return trim_fragment(eleven.sample(f"bossvoice/unsung/{line_id}/f{k}_m{mask}"), bool(WHISPER.search(text)))


def stitch(pieces: list, grid: str, gap: float = MIN_GAP) -> tuple:
    """The fragments placed on their grid after LEAD of silence, with room for the Nave: (the dry track, where each
    fragment starts in it, in seconds)."""
    starts = onsets([len(p) / SR for p in pieces], grid, gap)
    dry = silence(LEAD + starts[-1] + len(pieces[-1]) / SR + 3.8)
    for p, s in zip(pieces, starts):
        place(dry, p, LEAD + s)
    return dry, [LEAD + s for s in starts]


def colour(dry: np.ndarray) -> np.ndarray:
    """The porcelain colour on the stitched words (2.6 kHz presence, a little 900 Hz box)."""
    return eq(eq(dry, "peak", 2600.0, 3.0, 3.5), "peak", 900.0, 2.0, 2.0)


@lru_cache(maxsize=None)
def unsung_parts(line_id: str, living: str = "123") -> dict:
    """The Unsung line's layers: each fragment coloured and set to its mask's loudness, stitched, hummed under by the
    other living masks, with the Nave round it. A fragment whose own peaks would need more than LIMIT_MAX_DB from the
    build's limiter (a plosive's burst on a low voice) is held below its mask's loudness just far enough that the
    mixed line fits the cap: `held` says by how many dB for each (VP2 re-review N2: loudness comes from the level, not
    from limiting, so the line is never squashed and never turned down whole)."""
    line = _line("unsung", line_id)
    plan = assign(line, living)
    leveled = [level_fragment(fragment(line_id, k, m), speaker_lufs(line, m)) for k, m in plan]
    grid, gap = grid_of(line), gap_of(line_id)
    dry, starts = stitch(leveled, grid, gap)
    r = _rng(f"unsung/{line_id}/{living}")
    hum = np.zeros_like(dry)
    alive = [int(c) for c in living]
    for (k, m), x, s in zip(plan, leveled, starts):
        for other in alive:                        # the other living masks hum under this one's words
            if other != m:
                h = sung(r, len(x) / SR + 0.35, HUM_HZ[other], MM, singers=2, detune=8.0, vib_cents=8.0, attack=0.12,
                         release=0.25, breath=0.02, f_max=3500.0, scoop=0.0)
                place(hum, h, max(0.0, s - 0.08))
    nave = reverb_ir(r, 4.2, damp_hz=4500.0, hp_hz=200.0, predelay=0.04)
    wet = 8.0 if line["trigger"] == "boss_kill" else 11.0

    def assemble(pieces: list) -> dict:
        words, _ = stitch(pieces, grid, gap)
        ring = under(hp(comb(words, 0.0023, 0.35), 1200.0, 2), words, 15.0)
        room = signal.fftconvolve(words + 0.5 * ring, nave)[:len(words)]
        return {"words": words, "ring": ring, "hum": under(hum, words, 23.0), "room": under(room, words, wet),
                "starts": starts, "masks": [m for _, m in plan]}

    held = [0.0] * len(plan)
    for _ in range(FIT_PASSES):
        scaled = [x * db(-h) for x, h in zip(leveled, held)]
        parts = assemble(scaled)
        mix = hp(render(parts), 20.0, 2)                                    # as the build's master filters it
        peak = np.abs(signal.resample_poly(mix, 4, 1))[:4 * len(mix)].reshape(len(mix), 4).max(axis=1)   # true peak
        loud = []                                  # which fragment is loudest at each moment: its peaks are its own
        for x, s in zip(scaled, starts):
            alone = np.zeros(len(mix))
            place(alone, x, s)
            loud.append(np.convolve(np.abs(alone), np.ones(n_of(0.01)) / n_of(0.01), mode="same"))
        owner = np.argmax(loud, axis=0)
        over = [max(0.0, float(20.0 * np.log10(peak[owner == i].max() + 1e-12)) - (KEEP_CEILING_DB + LIMIT_MAX_DB - FIT_MARGIN))
                if (owner == i).any() else 0.0 for i in range(len(plan))]
        if max(over) < 0.02:
            break
        held = [h + o for h, o in zip(held, over)]
    else:                                          # still over after every pass: the last cut must still be heard
        parts = assemble([x * db(-h) for x, h in zip(leveled, held)])
    parts["held"] = [round(h, 2) for h in held]
    return parts


# ---------------------------------------------------------------------------------------------------- events

def render(parts: dict) -> np.ndarray:
    return fade(sum(v for k, v in parts.items() if k not in ("starts", "masks", "held")), 0.005, 0.6)


def _event(name: str, words: str, fn, keep_level: bool = False, headroom_db: float = 0.0) -> Event:
    return Event(name, f"\"{words}\"", [fn], length=14.0, level=LEVEL_LUFS + 15.0, fade_out=0.8, speech=True, quality=6,
                 keep_level=keep_level, headroom_db=headroom_db)


def _events() -> list:
    out = []
    for line in _lines():
        b, i = line["boss"], line["id"]
        if b == "colossus" and _have(f"bossvoice/colossus/{i}"):
            out.append(_event(f"bossvoice/colossus_{i}", line["words"], lambda i=i: render(colossus_parts(i)),
                              headroom_db=ENCODER_HEADROOM_DB))
        elif b == "leviathan" and _have(f"bossvoice/leviathan/{i}"):
            out.append(_event(f"bossvoice/leviathan_{i}", line["words"], lambda i=i: render(leviathan_parts(i)),
                              headroom_db=ENCODER_HEADROOM_DB))
        elif b == "unsung":
            kind = line["split"]["kind"]
            for living in LIVING[kind]:
                if all(_have(f"bossvoice/unsung/{i}/f{k}_m{m}") for k, m in assign(line, living)):
                    name = f"bossvoice/unsung_{i}" + ("" if kind == "relay" else f"_{living}")
                    out.append(_event(name, line["words"], lambda i=i, lv=living: render(unsung_parts(i, lv)),
                                      keep_level=True))
    return out


EVENTS = _events()
