"""Boss voice production for Cosmic Breach 1.1.0 (vault: "Aetheria 1.1 Voice Script v1" holds the lines,
"Aetheria 1.1 Plan v1 Voice Production" the steps).

The script's tables are exported once into eleven/voice_script.json; every other command reads that file. Commands
marked $ spend ElevenLabs credits through eleven.py (which checks the balance first and logs every request in
eleven/ledger.jsonl); only the main session runs them. Nothing here runs at build time.

    python tools/sound/voicebatch.py export "<path to Aetheria 1.1 Voice Script v1.md>"
    python tools/sound/voicebatch.py plan [--boss colossus] [--takes 2] [--whole]     what is left to make, in credits
    python tools/sound/voicebatch.py probe [--text "to hear us" --previous-text "You came back"
                                           --next-text "forget again." --label voiced]   $ does eleven_v4 use the context?
    python tools/sound/voicebatch.py run --boss colossus [--takes 2] [--only a,b] [--retake] [--more 1] [--whole] [--dry-run]  $
    python tools/sound/voicebatch.py check --boss colossus [--whole]                $ Scribe on every unchecked take
    python tools/sound/voicebatch.py pick --boss colossus [--whole]                   the best passing take per line
    python tools/sound/voicebatch.py rank "<candidates folder>" --name colossus [--role colossus]   $ ranks previews
    python tools/sound/voicebatch.py keep "<candidates folder>" --name colossus --label "CB Colossus" [--only colossus_b]
    python tools/sound/voicebatch.py use colossus colossus_b --by Nate --folder "<candidates folder>"   a role's voice
    python tools/sound/voicebatch.py rebase unsung_m1 "<take.wav>" [...] [--by "test lines"]   its pitch from eleven_v4
    python tools/sound/voicebatch.py retire colossus_b [...]     deletes unused saved candidates (approved by name)
    python tools/sound/voicebatch.py unkeep bossvoice/unsung/thief/f3_m3 [...]     to choose or retake a kept file again
    python tools/sound/voicebatch.py accept <take> --reason "..." --by orchestrator   a take Scribe mishears, accepted
    python tools/sound/voicebatch.py manifest                                         voice_lines.json, after the build
    python tools/sound/voicebatch.py integrate                                        sounds.json and the subtitles
    python tools/sound/voicebatch.py audit                                            every kept take is from the paid plan
    python tools/sound/voicebatch.py margins                     each speaker over its boss's music, after the build

Files it keeps beside the takes (tools/sound/eleven/): voice_script.json (the export), voice_picks.json (which voice
plays which role), voice_checks.json (each take's transcript, spoken length and pitch), voice_takes.json (which take
became each kept file), voice_probe.json (the context probe's verdict). Once a take is kept (copied to the line's own
name), every take of that line moves to C:\\Users\\puppy\\Media\\Aetheria\\Voice\\takes\\ (kept, not deleted).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import shutil
import sys
import time
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import NamedTuple

import numpy as np
from scipy import signal
from scipy.io import wavfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.dont_write_bytecode = True

import eleven  # noqa: E402
from analysis import integrated, true_peak  # noqa: E402
from dsp import SR, at, db, hp, lp, n_of  # noqa: E402

REPO = HERE.parents[1]
OUT = eleven.OUT
SCRIPT = OUT / "voice_script.json"
PICKS = OUT / "voice_picks.json"
CHECKS = OUT / "voice_checks.json"
TAKES = OUT / "voice_takes.json"
PROBE = OUT / "voice_probe.json"
STITCH = OUT / "voice_stitch.json"       # the Unsung lines pick let overlap (bossvoice.gap_of reads it)
REFUSED = OUT / "voice_refused.json"     # the Unsung lines pick could not stitch in time: run --retake retakes them
TEMPO = OUT / "voice_tempo.json"         # the lines pick let speak faster (bossvoice.tempo_of reads it)
HANDOFF = HERE / "voice_lines.json"
ASSETS = REPO / "src" / "main" / "resources" / "assets"
MEDIA = Path("C:/Users/puppy/Media/Aetheria/Voice")
GEORGE = "JBFqnCBsd6RMkjVDRZzb"          # boss 4's premade voice, as shipped
BOSSES = ("colossus", "leviathan", "unsung", "heliarch")
HEADER = ["Boss", "Line id", "Trigger", "Condition", "Priority", "Repeat rule", "Text", "Delivery notes", "Mask split"]
TRIGGER = re.compile(r"(fight_start|hp_threshold:\d{1,3}|player_death|player_low_health|player_fell|player_left|"
                     r"player_returned|weapon:(forge_tier_[1-4]|vanilla_melee|bow|crossbow|trident|mod_weapon|bare_hands)|"
                     r"armor:(mod_full_set|mod_partial|vanilla|none)|gear:(over|under|even)|attack_gap|boss_kill|taunt)")
REPEAT = re.compile(r"once per fight|cooldown \d+ s")
STRETCH = {"colossus": 1.09}             # bossvoice.COLOSSUS_DOWN: the Colossus's words play this much slower
SLACK = 0.05                             # a take's built span may run this far over its budget...
BUILD_SLACK = 0.1                        # ...and the built line this far (manifest): what passes check builds in time
QUIET = re.compile(r"quiet window only", re.IGNORECASE)   # ...but never a line that must fit a quiet window
GAPS = {"colossus": 25, "leviathan": 15, "unsung": 20, "heliarch": 12}
# Voice Design targets from the casting briefs: median pitch band (Hz) and speaking rate band (words a second)
TARGETS = {"colossus": (70.0, 110.0, 1.4, 2.4), "leviathan": (85.0, 130.0, 2.0, 3.0),
           "leviathan_f": (130.0, 210.0, 2.2, 3.2),     # Nate's female Leviathan: a deep contralto, brisk for its gaps
           "unsung_m1": (175.0, 250.0, 1.8, 2.8), "unsung_m2": (115.0, 170.0, 1.8, 2.8),
           "unsung_m3": (75.0, 115.0, 1.8, 2.8)}
# Each boss's fight music, the repo's own files as the client plays them (the playback's duck needs these names too):
# a loop, one of the Heliarch's three phases, or the Unsung's choir, whose music is her masks singing (unsung_block).
MUSIC = {"colossus": ["music/colossus"], "leviathan": ["music/leviathan"],
         "heliarch": ["music/heliarch_regent", "music/heliarch_hollow", "music/heliarch_collapse"], "unsung": "choir"}
# The decision after the VP2 re-review: every line at least this far over the music under it in the files, measured
# over the same span. The playback pulls that music down about 6 dB while a line plays, so in the game the margin is 9 LU
# or more; production keeps 3 LU, and no limiter takes more than 4 dB off a line (bossvoice.LIMIT_MAX_DB), instead of the
# 6 LU and the heavy limiting it took to reach it.
MARGIN_LU = 3.0
# The Leviathan's scheduler guarantees a quiet window of GAP_TICKS after an attack, and its director needs a line's
# speech plus GATE_TICKS of it (Voice Script v1, director rule 7): 40 ticks, 2.0 s, of speech. A line that speaks longer
# is marked long in the handoff: the playback may only play it in a coil pause, in the opening or at the kill.
GAP_TICKS = 50
GATE_TICKS = 10
NAMESPACE = "cosmicbreach"
VOICES = {1: "alto", 2: "tenor", 3: "bass"}              # the masks' lines in the choir (unsung.py)
CHOIR = {"sung": 1.0, "hum": 0.85, "rise": 0.95}          # each block's volume in the fight (UnsungMusic.java)
TURN = 4.8               # the Unsung's blocks start every 8 beats and ring 0.9 s into the next
CHORD = "unsung/last_chord"     # her choir stops at the death; the server plays this one soft chord (a music sound)
CHORD_LAG = (36 - 24) / 20.0    # it starts at death tick 24 (UnsungMoves.LAST_CHORD), the kill line at tick 36 (the script)


# ------------------------------------------------------------------------------------------------ the script

def plain(text: str) -> str:
    """A line's words without its [audio tags]: the caption, and the context sent around a fragment."""
    return re.sub(r"\s+", " ", re.sub(r"\[[^\]]*\]", " ", text)).strip()


def _number(pattern: str, text: str):
    m = re.search(pattern, text)
    return float(m.group(1)) if m else None


def parse_split(cell: str) -> dict:
    """The Mask split cell: fragments separated by " / ", each "m1:", "m2:", "m3:", "first:", "second:" or "alone:"."""
    frags = []
    for part in cell.split(" / "):
        tag, _, text = part.partition(":")
        tag, text = tag.strip(), text.strip()
        if tag not in ("m1", "m2", "m3", "first", "second", "alone") or not text:
            raise ValueError(f"bad fragment {part!r}")
        frags.append({"tag": tag, "text": text})
    tags = [f["tag"] for f in frags]
    if all(t in ("m1", "m2", "m3") for t in tags):
        kind = "relay"
    elif tags == ["first", "second"]:
        kind = "pair"
    elif tags == ["alone"]:
        kind = "alone"
    else:
        raise ValueError(f"bad fragment tags {tags}")
    return {"kind": kind, "fragments": frags}


def _row(c: dict) -> dict:
    boss, line_id = c["Boss"], c["Line id"]
    where = f"{boss}/{line_id}"
    if boss not in BOSSES or not re.fullmatch(r"[a-z0-9_]+", line_id):
        raise ValueError(f"{where}: bad boss or id")
    if not TRIGGER.fullmatch(c["Trigger"]):
        raise ValueError(f"{where}: trigger {c['Trigger']!r} is not in the vocabulary")
    priority = int(c["Priority"])
    if not 1 <= priority <= 100 or not REPEAT.fullmatch(c["Repeat rule"]):
        raise ValueError(f"{where}: bad priority or repeat rule")
    if any("\u2014" in v or "\u2013" in v for v in c.values()):
        raise ValueError(f"{where}: a dash")
    delivery = c["Delivery notes"]
    shipped = delivery.startswith("Shipped take")
    split = None if c["Mask split"] == "none" else parse_split(c["Mask split"])
    if boss == "unsung":
        if split is None:
            raise ValueError(f"{where}: an Unsung line needs its Mask split")
        joined = " ".join(f["text"] for f in split["fragments"])
        if eleven.script_words(joined) != eleven.script_words(c["Text"]):
            raise ValueError(f"{where}: the fragments do not add up to the caption")
        words = c["Text"]
    elif split is not None:
        raise ValueError(f"{where}: only the Unsung has a Mask split")
    else:
        words = plain(c["Text"])
    budget = _number(r"\bBudget (\d+(?:\.\d+)?) s", delivery)
    fragment_budget = _number(r"Fragment budget (\d+(?:\.\d+)?) s", delivery)
    if not shipped and budget is None and fragment_budget is None:
        raise ValueError(f"{where}: no Budget in its delivery notes")
    return {"boss": boss, "id": line_id, "trigger": c["Trigger"], "condition": c["Condition"], "priority": priority,
            "repeat": c["Repeat rule"], "text": c["Text"], "words": words, "delivery": delivery, "budget": budget,
            "fragment_budget": fragment_budget, "shipped": shipped, "split": split}


def parse_script(md: str) -> list:
    """Every row of every table whose header is exactly HEADER."""
    rows, in_table = [], False
    for raw in md.splitlines():
        s = raw.strip()
        if not s.startswith("|"):
            in_table = False
            continue
        cells = [c.strip() for c in s.strip("|").split("|")]
        if cells == HEADER:
            in_table = True
            continue
        if not in_table or set("".join(cells)) <= set("-: "):
            continue
        if len(cells) != len(HEADER):
            raise ValueError(f"a row with {len(cells)} cells: {s[:70]}")
        rows.append(_row(dict(zip(HEADER, cells))))
    seen = set()
    for r in rows:
        key = (r["boss"], r["id"])
        if key in seen:
            raise ValueError(f"{key} twice")
        seen.add(key)
    return rows


def lines(boss: str | None = None) -> list:
    rows = json.loads(SCRIPT.read_text(encoding="utf-8"))["lines"]
    return [r for r in rows if boss is None or r["boss"] == boss]


# ------------------------------------------------------------------------------------------------ jobs and takes

@dataclass
class Job:
    """One kept file to make: its name under eleven/, the role whose voice speaks it, and what is sent."""
    boss: str
    line_id: str
    base: str
    role: str
    text: str
    prev: str | None = None
    next: str | None = None
    budget: float | None = None
    whole: bool = False
    hard: bool = False             # its budget is a window it must fit ("Quiet window only"): no slack over it


def needs(line: dict) -> list:
    """(fragment number, mask) of every Unsung fragment take the line needs, over all its variants."""
    sp = line["split"]
    if sp["kind"] == "relay":
        return [(k + 1, int(f["tag"][1])) for k, f in enumerate(sp["fragments"])]
    if sp["kind"] == "pair":       # the first living mask is the Alto or the Tenor, the second the Tenor or the Bass
        return [(1, 1), (1, 2), (2, 2), (2, 3)]
    return [(1, 1), (1, 2), (1, 3)]


def unsung_jobs(line: dict, whole: bool) -> list:
    frags = line["split"]["fragments"]
    if whole and line["split"]["kind"] != "alone":
        text = " ".join(f["text"] for f in frags)
        return [Job("unsung", line["id"], f"bossvoice/unsung/{line['id']}/whole_m{m}", f"unsung_m{m}", text, whole=True)
                for m in sorted({m for _, m in needs(line)})]
    out = []
    for k, m in needs(line):
        before = " ".join(plain(f["text"]) for f in frags[:k - 1]) or None
        after = " ".join(plain(f["text"]) for f in frags[k:]) or None
        out.append(Job("unsung", line["id"], f"bossvoice/unsung/{line['id']}/f{k}_m{m}", f"unsung_m{m}",
                       frags[k - 1]["text"], before, after, line["fragment_budget"]))
    return out


def jobs(boss: str, whole: bool = False) -> list:
    out = []
    for line in lines(boss):
        if line["shipped"]:
            continue
        if boss == "unsung":
            out += unsung_jobs(line, whole)
        elif boss == "heliarch":
            out.append(Job(boss, line["id"], f"heliarch/{line['id']}", "heliarch", line["text"], budget=line["budget"],
                           hard=hard(line)))
        else:
            out.append(Job(boss, line["id"], f"bossvoice/{boss}/{line['id']}", boss, line["text"], budget=line["budget"],
                           hard=hard(line)))
    return out


def hard(line: dict) -> bool:
    """A line whose budget is a quiet window it must fit (its delivery says "Quiet window only"): no slack over it."""
    return bool(QUIET.search(line.get("delivery") or ""))


def takes_of(base: str) -> list:
    """The takes made for a kept file, oldest first: names under eleven/ without .wav."""
    found = []
    for p in (OUT / base).parent.glob(f"{Path(base).name}_take*.wav"):
        m = re.search(r"_take(\d+)$", p.stem)
        if m:
            found.append((int(m.group(1)), f"{base}_take{m.group(1)}"))
    return [n for _, n in sorted(found)]


def sent_texts() -> dict:
    """What the ledger says each speech take was sent: take name to text (its latest entry)."""
    out = {}
    if eleven.LEDGER.exists():
        for line in eleven.LEDGER.read_text(encoding="utf-8").splitlines():
            if line.strip():
                e = json.loads(line)
                if e.get("kind") == "tts":
                    out[e["name"]] = e.get("text")
    return out


def usable_takes(job: Job, sent: dict | None = None) -> tuple:
    """A job's takes in the repo, split by what the ledger says each was sent (VP3 review I1): (the takes sent the job's
    text as it reads now, the takes sent other text, each as (take, text)). The words check ignores tags, so a take made
    before a pacing or pitch tag changed would pass it and could be kept for a row that no longer asks for its delivery:
    check, pick and run --retake leave such a take out. A take with no ledger entry (one placed by hand, a test's
    stand-in) counts as the job's; audit is what asks every kept take for its entry."""
    sent = sent_texts() if sent is None else sent
    own, other = [], []
    for t in takes_of(job.base):
        was = sent.get(t)
        if was is None or was == job.text:
            own.append(t)
        else:
            other.append((t, was))
    return own, other


def other_text_note(job: Job, take: str, was: str) -> str:
    return (f"NOTE {take}: sent other text than the script's now ({was!r}, the script says {job.text!r}); it does not "
            f"count, a new take of the line will")


def next_take(base: str) -> int:
    """The number for a new take of `base`: one past every take number ever used for it, in the repo, in Media's
    takes, in voice_checks.json, voice_takes.json and the ledger. Takes leave the repo once a line is kept, so the
    repo alone would hand a redo the names of takes already checked and shelved."""
    own = re.compile(rf"^{re.escape(base)}_take(\d+)(?:_again\d+)?$")
    names = set(_load(CHECKS, {})) | {t.get("take", "") for t in _load(TAKES, {}).values()}
    if eleven.LEDGER.exists():
        names |= {json.loads(l).get("name", "") for l in eleven.LEDGER.read_text(encoding="utf-8").splitlines() if l.strip()}
    for folder in (OUT, MEDIA / "takes"):
        names |= {f"{Path(base).parent.as_posix()}/{p.stem}" for p in (folder / base).parent.glob(f"{Path(base).name}_take*")
                  if p.suffix == ".wav"}
    used = [int(m.group(1)) for m in map(own.match, names) if m]
    return max(used, default=0) + 1


def audio_hash(name: str) -> str:
    """A take's audio fingerprint: its check is only good for the audio it was made on."""
    return hashlib.sha256((OUT / f"{name}.wav").read_bytes()).hexdigest()[:16]


def checked(checks: dict, name: str) -> bool:
    """`name` has a check, made on the audio now under that name."""
    return name in checks and checks[name].get("sha") == audio_hash(name)


def _load(path: Path, empty):
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else empty


def _save(path: Path, data) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def voice_of(role: str) -> str:
    if role == "heliarch":
        return GEORGE
    roles = _load(PICKS, {}).get("roles", {})
    if role not in roles:
        sys.exit(f"No voice picked for {role}: see the plan's pick tasks (voice_picks.json)")
    return roles[role]["voice_id"]


WHISPER = re.compile(r"\[[^\]]*whisper[^\]]*\]", re.IGNORECASE)     # a fragment the script asks to be whispered


def pitch_key(job: Job, f0: float, ref: float | None) -> float:
    """How far a take's pitch sits from its voice's reference (pick keeps the nearest). A take with no measurable
    pitch (a whisper reads 0.0) comes after every voiced take, unless the script asks for a whisper."""
    if not ref:
        return 0.0
    if f0 > 0:
        return abs(math.log2(f0 / ref))
    return 0.0 if WHISPER.search(job.text) else math.inf


def words_ok(job: Job, check: dict) -> bool:
    """The take's words are right: Scribe's stored transcript matches the job's script under today's comparison, or
    someone accepted this take's audio although Scribe did not (voicebatch.py accept: the reason, who decided and
    when, for that audio only). The verdict stored beside the transcript is never trusted (VP2 re-review N1): it goes
    stale when the comparison changes, and a take already shelved is never heard again. A check with no transcript
    (an old one) has only its verdict left."""
    heard = check.get("heard")
    right = bool(check.get("ok")) if heard is None else eleven.script_words(heard) == eleven.script_words(job.text)
    accepted = check.get("accepted")
    return right or bool(accepted and accepted.get("sha") == check.get("sha"))


def fits(job: Job, check: dict) -> bool:
    """A take passes: Scribe heard the script's words (or the take was accepted, words_ok), and the words as the build
    will play them fit the budget. `span_s` is the built span (the same measure manifest uses, the Colossus's slower
    playback included); an Unsung fragment with a budget (the openers') is judged as placed: its words may run into
    the next mask's by bossvoice.MAX_OVERLAP at most; the opener's last fragment, which nothing follows, is judged on
    its words against its budget. A check from before span_s existed falls back to Scribe's span."""
    if not words_ok(job, check):
        return False
    if job.budget is None:
        return True
    if job.boss == "unsung" and "placed_s" in check:
        import bossvoice
        if job.next is None:                   # the line's last fragment: nothing waits for it, only its budget
            return check.get("span_s", check["speech_s"]) <= job.budget + SLACK
        return check["placed_s"] <= _lift_room(job)   # its words may meet the next mask's by 150 ms at most
    span = check.get("span_s", check["speech_s"] * STRETCH.get(job.boss, 1.0))
    return span <= job.budget + (0.0 if job.hard else SLACK)


def passes(job: Job, check: dict) -> bool:
    """What check and run --retake judge a take by: it fits as its line plays now (fits), or would fit at a tempo its
    boss may have (the check's tempo_fit; pick records the tempo), its words right either way. So a take that only a
    tempo can save is never retaken at a credit, and never failed for a length the build will not play."""
    return fits(job, check) or (words_ok(job, check) and check.get("tempo_fit") is not None)


def _lift_room(job: Job) -> float:
    """How long an opener's fragment may be as placed: two beats to the next mask's lift, then the overlap at which
    their words meet by bossvoice.MAX_OVERLAP (1.5 s plain, 1.4 s whispered)."""
    import bossvoice
    return 2 * bossvoice.BEAT - bossvoice.overlap_gap(bool(WHISPER.search(job.text)))


def over_note(job: Job, check: dict) -> str:
    """Why a take whose words are right fails fits, in the terms of the rule that failed it."""
    if job.boss == "unsung" and "placed_s" in check and job.next is not None:
        return f"  placed {check['placed_s']:.2f} s, room {_lift_room(job):.2f} s"
    note = f"  over budget: {check.get('span_s', check['speech_s']):.2f} s built against {job.budget} s"
    if may_speed_up(job) and "tempo_fit" in check:               # a tempo was tried, and is not enough
        import bossvoice
        note += f", and not within {bossvoice.TEMPO_MAX[job.boss]:.2f}x faster"
    return note


def built_words(job: Job, x: np.ndarray, tempo: float | None = None) -> np.ndarray:
    """A take as the build will place it: the Colossus's and the Leviathan's words through bossvoice.words_of (the
    Colossus resampled slower, the Leviathan at its line's tempo or `tempo`), boss 4's through heliarch.clean_words,
    an Unsung fragment through the stitcher's trim."""
    import bossvoice
    if job.boss == "colossus":
        return bossvoice.words_of(x, down=bossvoice.COLOSSUS_DOWN)
    if job.boss == "leviathan":
        return bossvoice.words_of(bossvoice.stretch(x, tempo or bossvoice.tempo_of(job.boss, job.line_id)))
    if job.boss == "heliarch":
        import heliarch
        return heliarch.clean_words(x)
    return bossvoice.trim_fragment(x, bool(WHISPER.search(job.text)))


def may_speed_up(job: Job) -> bool:
    """The job has a limit to fit and its boss may speak faster than its takes (bossvoice.TEMPO_MAX)."""
    import bossvoice
    return job.budget is not None and bossvoice.TEMPO_MAX.get(job.boss, 1.0) > 1.0


def least_tempo(job: Job, x: np.ndarray, floor: float = 1.0) -> float | None:
    """The decision for the Leviathan: the least tempo, in steps of 0.01 from `floor` up to bossvoice.TEMPO_MAX, at
    which this take fits the job's limit as the build will place it (its words stretched at that tempo, the pitch
    kept; a quiet window gets no slack): `floor` itself when it fits already. None when no allowed tempo fits, or the
    boss may not speak faster. The one search check (before it judges a take) and pick (before it refuses a line)
    both use, so they agree."""
    import bossvoice
    if not may_speed_up(job):
        return None
    most = bossvoice.TEMPO_MAX[job.boss]
    limit = job.budget + (0.0 if job.hard else SLACK)

    def span(tempo: float) -> float:
        start, end = bossvoice.spoken_span(built_words(job, x, tempo))
        return end - start

    now = span(floor)
    if now <= limit:
        return floor
    tempo = max(round(floor + 0.01, 2), math.ceil(100.0 * floor * now / limit - 1e-9) / 100.0)   # a straight stretch
    while tempo <= most + 1e-9:
        if span(tempo) <= limit:
            return tempo
        tempo = round(tempo + 0.01, 2)
    return None


def lift_problem(starts: list) -> str | None:
    """An opener's fragments must start on the masks' lifts, 2 beats apart from the first; why not, or None."""
    import bossvoice
    lifts = [2 * bossvoice.BEAT * i for i in range(len(starts))]
    if all(abs(s - lift) < 0.001 for s, lift in zip(starts, lifts)):
        return None
    return (f"fragments start at {', '.join(f'{s:.2f}' for s in starts)} s, not on the lifts at "
            f"{', '.join(f'{lift:.2f}' for lift in lifts)} s")


def word_errors(heard: str, script: str) -> int:
    """How many words Scribe heard differently from the script (swapped, dropped or added; tags, case and punctuation
    ignored): the edit distance between the two word lists. 0 is a match."""
    a, b = eleven.script_words(heard), eleven.script_words(script)
    row = list(range(len(b) + 1))
    for i, w in enumerate(a, start=1):
        diag, row[0] = row[0], i
        for j, v in enumerate(b, start=1):
            diag, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, diag + (w != v))
    return row[-1]


# ------------------------------------------------------------------------------------------------ measuring

def f0_median(x: np.ndarray, lo: float = 60.0, hi: float = 420.0) -> float:
    """Median pitch (Hz) over the voiced 40 ms frames: each frame's normalized autocorrelation, taking the shortest lag
    within 10% of the best one (which avoids octave-down errors), where that peak is above 0.5 and the frame is within
    30 dB of the loudest. A frame also needs 30% of its energy under 300 Hz: a whisper's formants ring periodically
    enough to pass the first test (eleven_v4's [hushed] takes read as 250 to 420 Hz), but a whisper keeps 20% or less
    of its energy down there, while every voiced take measured, rough or breathy, keeps 75% or more. A frame pitched
    at 300 Hz or more instead needs 30% of its energy under 1.5 times its pitch and a peak of 0.8 (a voice that high
    is clearly periodic; a whisper's formant rings at 0.6 or so). 0.0 when nothing is voiced (a whisper), or
    when under 10% of the loud frames (or under 5) are: a few frames are a breath or a blip, not the take's voice
    (voiced takes measured keep 23% to 90%, eleven_v4's whispered ones 4% at most)."""
    base = hp(np.asarray(x, dtype=float), 50.0, 2)
    x = lp(base, 1000.0, 4)
    n, hop = n_of(0.04), n_of(0.01)
    lmin, lmax = int(SR / hi), int(SR / lo)
    if len(x) < n + lmax + 1:
        return 0.0
    starts = range(0, len(x) - n - lmax, hop)
    rms = np.array([np.sqrt(np.mean(np.square(x[i:i + n]))) for i in starts])
    floor = rms.max() * db(-30.0)
    bins = np.fft.rfftfreq(n, 1.0 / SR)
    out, loud = [], 0
    for i, e in zip(starts, rms):
        if e < floor:
            continue
        loud += 1
        a, seg = x[i:i + n], x[i:i + n + lmax]
        r = signal.correlate(seg, a, mode="valid")
        energy = np.sqrt(np.convolve(np.square(seg), np.ones(n), mode="valid") * np.sum(np.square(a)))
        nr = r / np.maximum(energy, 1e-12)
        band = nr[lmin:lmax + 1]
        best = band.max()
        if best < 0.5:
            continue
        peaks = [k for k in range(1, len(band) - 1) if band[k] >= band[k - 1] and band[k] >= band[k + 1]
                 and band[k] >= 0.9 * best]
        k = (peaks[0] if peaks else int(np.argmax(band))) + lmin
        power = np.square(np.abs(np.fft.rfft(base[i:i + n])))
        f = SR / k
        if f < 300.0 and np.sum(power[bins < 300.0]) < 0.3 * np.sum(power):
            continue                               # a whisper's formant, not a voice
        if f >= 300.0 and (best < 0.8 or np.sum(power[bins < 1.5 * f]) < 0.3 * np.sum(power)):
            continue                               # above 300 Hz only a clearly periodic voice counts
        out.append(SR / k)
    return float(np.median(out)) if len(out) >= max(5, 0.1 * loud) else 0.0


def scribe_words(name: str) -> list:
    data = json.loads((OUT / f"{name}.scribe.json").read_text(encoding="utf-8"))
    return [w for w in data.get("words", []) if w.get("type", "word") == "word"]


def speech_seconds(words: list) -> float:
    return float(words[-1]["end"] - words[0]["start"]) if words else 0.0


def _write_wav(name: str, x: np.ndarray) -> None:
    path = OUT / f"{name}.wav"
    path.parent.mkdir(parents=True, exist_ok=True)
    wavfile.write(path, SR, (np.clip(x, -1.0, 1.0) * 32767.0).astype(np.int16))


# ------------------------------------------------------------------------------------------------ commands

def export(note: str) -> None:
    rows = parse_script(Path(note).read_text(encoding="utf-8"))
    _save(SCRIPT, {"source": Path(note).stem, "exported_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                   "lines": rows})
    for b in BOSSES:
        own = [r for r in rows if r["boss"] == b]
        print(f"{b:10s} {len(own):3d} lines, {sum(1 for r in own if r['shipped'])} shipped")
    print(f"{len(rows)} lines in {SCRIPT.relative_to(HERE)}")


def plan(boss: str | None, takes: int, whole: bool) -> None:
    """What is still to make, and what it costs by our count (1 credit a character, tags included, context not)."""
    total = 0
    for b in [boss] if boss else BOSSES:
        todo = [j for j in jobs(b, whole) if not (OUT / f"{j.base}.wav").exists()]
        chars = sum(len(j.text) * max(0, takes - len(usable_takes(j)[0])) for j in todo)
        total += chars
        print(f"{b:10s} {len(todo):3d} files to make, {chars:5d} credits for {takes} take(s) each")
    print(f"total {total} credits by our count (the API has charged about a fifth of this so far)")


def run(boss: str, takes: int, only: set, retake: bool, whole: bool, dry: bool, more: int = 0) -> None:
    """Makes the takes still missing ($). With --retake, one more take of every job whose checked takes all fail (a
    Leviathan take that only a tempo up to 1.15x can save does not fail: passes), and of the longest fragment of every
    Unsung line pick could not stitch in time (voice_refused.json). With --more N, N new takes of every job left (with
    --only, of those lines' jobs), however many each has."""
    checks, kept, sent = _load(CHECKS, {}), _load(TAKES, {}), sent_texts()
    again = {r["retake"] for r in _load(REFUSED, {}).values()} if retake else set()
    todo, notes = [], []
    for j in jobs(boss, whole):
        if only and j.line_id not in only:
            continue
        if (OUT / f"{j.base}.wav").exists():                    # kept: a failing kept take must be unkept to be retaken
            c = checks.get(kept.get(j.base, {}).get("take"))
            if retake and c is not None and not words_ok(j, c):
                notes.append(f"NOTE {j.base}: its kept take fails the word check (Scribe heard {c.get('heard')!r}); "
                             f"python tools/sound/voicebatch.py unkeep {j.base}, then run --retake")
            continue
        made, other = usable_takes(j, sent)      # a take sent other text than the row has now is not one of its takes
        notes += [other_text_note(j, t, was) for t, was in other]
        if more:
            count = more
        elif retake:
            unchecked = [t for t in made if not checked(checks, t)]
            if unchecked:
                notes.append(f"NOTE {j.base}: {len(unchecked)} take(s) not checked yet: run check first")
                continue
            if not made and not other:               # nothing was ever made: that is no retake
                continue
            if j.base not in again and any(passes(j, checks[t]) for t in made):
                continue
            count = 1
        else:
            count = max(0, takes - len(made))
        first = next_take(j.base)
        todo += [(j, f"{j.base}_take{first + i}") for i in range(count)]
    chars = sum(len(j.text) for j, _ in todo)
    print(f"{boss}: {len(todo)} take(s), {chars} credits by our count")
    for j, name in todo:
        print(f"  {name}  {j.text!r}" + (f"  after {j.prev!r}" if j.prev else "") + (f"  before {j.next!r}" if j.next else ""))
    for note in notes:
        print(note)
    if dry:
        return
    for j, name in todo:
        eleven.tts(name, j.text, voice_of(j.role), previous_text=j.prev, next_text=j.next)


CUT_DB = 20.0            # a take that starts or ends within this of its loudest 20 ms was cut while sounding
FRICATIVE_END = re.compile(r"(s|z|x|ce|se|ze|sh|ch|ge|f|fe|ph|th|the|ve)$")   # a last word that may end on a hiss


def _edges(x: np.ndarray) -> dict:
    """A take's first and last 20 ms against its loudest 20 ms (dB), and the share of its last 50 ms above 4 kHz."""
    w = n_of(0.02)
    rms = np.sqrt(np.maximum(np.convolve(np.square(x), np.ones(w) / w, mode="valid"), 1e-24))
    tail = x[-n_of(0.05):]
    power = np.abs(np.fft.rfft(tail)) ** 2
    hf = power[np.fft.rfftfreq(len(tail), 1.0 / SR) >= 4000.0].sum() / max(power.sum(), 1e-24)
    return {"start_db": round(float(20.0 * np.log10(rms[0] / rms.max())), 1),
            "end_db": round(float(20.0 * np.log10(rms[-1] / rms.max())), 1), "end_hf": round(float(hf), 2)}


def cut_note(job: Job, check: dict) -> str | None:
    """Why a take looks cut at its seam, or None (VP2 review I2): with the words after it as context, eleven_v4 stops
    a fragment at its own boundary, sometimes mid-sound, and the words check cannot hear a clipped "ng" or the next
    word's s. Flags a fragment that starts within CUT_DB of its peak after another mask (already sounding), and a
    take that ends within CUT_DB of its peak on a sound its last word does not end with: a hiss after a word that
    does not end in one (the next word's s), or a voice cut off. A take measured before these fields has no flag."""
    if "end_db" not in check:
        return None
    notes = []
    if job.prev and check["start_db"] > -CUT_DB:
        notes.append(f"starts {-check['start_db']:.0f} dB under its peak, already sounding")
    if check["end_db"] > -CUT_DB:
        last = (plain(job.text).lower().replace("\u2019", "'").strip(" .,!?;:'\"").split() or [""])[-1]
        if check["end_hf"] < 0.5:
            how = "still sounding" if WHISPER.search(job.text) else "while voiced"          # a whisper has no voice
            notes.append(f"ends {-check['end_db']:.0f} dB under its peak {how}")
        elif not FRICATIVE_END.search(last):
            notes.append(f"ends {-check['end_db']:.0f} dB under its peak on a hiss its last word does not end with")
    return "; ".join(notes) or None


def _measured(job: Job, x: np.ndarray) -> dict:
    """What a take measures as the build places it: the span of its built words and, for an Unsung fragment, its
    length as placed (padding included); its edges (_edges), for cut_note; and, for a boss that may speak faster, the
    least tempo at which it fits (least_tempo). Local and free, so check takes it again whenever the build or these
    measures change."""
    import bossvoice
    built = built_words(job, x)
    start, end = bossvoice.spoken_span(built)
    out = {"span_s": round(end - start, 3), **_edges(x)}
    if job.boss == "unsung":
        out["placed_s"] = round(len(built) / SR, 3)
    if may_speed_up(job):                  # what a tempo can do for it (from 1.0, whatever the line plays at now)
        out["tempo_fit"] = least_tempo(job, x)
    return out


def known_jobs() -> dict:
    """Every job the script can make, by the name of its kept file (fragments, and whole sentences too)."""
    return {j.base: j for b in BOSSES for whole in (False, True) for j in jobs(b, whole)}


def rejudge_stored(checks: dict) -> list:
    """Judges the words of every stored check again on its transcript, today's comparison against its job's text, and
    updates the stored verdict (VP2 re-review N1). Returns (take, was, now, heard, script words) for each that changed.
    Free: no Scribe call, and it reaches takes that left the repo for Media."""
    by_base, changed = known_jobs(), []
    for take, entry in checks.items():
        job = by_base.get(re.sub(r"_take\d+(?:_again\d+)?$", "", take))
        if job is None or "heard" not in entry:
            continue
        now = eleven.script_words(entry["heard"]) == eleven.script_words(job.text)
        if bool(entry.get("ok")) != now:
            changed.append((take, bool(entry.get("ok")), now, entry["heard"], plain(job.text)))
            entry["ok"] = now
    return changed


def check(boss: str, whole: bool) -> int:
    """Scribe on every take not yet checked (or whose audio changed since): its words against the script, its spoken
    length, its median pitch, and the fingerprint of the audio it heard. A take heard already is judged on its stored
    transcript and measured again as today's build places it, both free. A Leviathan take is judged at the tempo it
    may have (up to 1.15x, least_tempo), so one that only a tempo can fit to its limit passes, and says which tempo.
    Reports the takes heard or changed in this run, counts earlier failures apart, and returns how many jobs with takes
    have none that passes (0: pick can go). Every stored verdict, of a take in the repo or not, is first judged again on
    its transcript (rejudge_stored): a take already shelved is never heard again, so its verdict would go stale. A take
    the ledger says was sent other text than its job's text now (usable_takes) is named and left alone: it costs no
    Scribe call, and a job with only such takes has no passing take."""
    checks = _load(CHECKS, {})
    flipped = rejudge_stored(checks)
    if flipped:
        _save(CHECKS, checks)
        for take, was, now, heard, text in flipped:
            print(f"{'OK  ' if now else 'FAIL'} {take}: its stored verdict was a {'pass' if was else 'fail'}, judged again on "
                  f"its transcript: Scribe heard {heard!r}, the script says {text!r}")
        print(f"{len(flipped)} stored verdict(s) changed on today's comparison")
    failed = earlier = 0
    stuck = []
    sent = sent_texts()
    for j in jobs(boss, whole):
        made, other = usable_takes(j, sent)         # a take sent other text than the row has now is neither heard nor judged
        for t, was in other:
            print(other_text_note(j, t, was))
        passing = False
        for t in made:
            if checked(checks, t):                 # heard already: judge its transcript against today's script, free
                entry = checks[t]
                ok = eleven.script_words(entry["heard"]) == eleven.script_words(j.text)
                changed = {k: v for k, v in _measured(j, eleven.sample(t)).items() if entry.get(k) != v}
                good = passes(j, {**entry, "ok": ok, **changed})
                if ok != entry["ok"] or changed:
                    entry.update(ok=ok, **changed)
                    _save(CHECKS, checks)
                    again = f" and measured again ({', '.join(f'{k} {v}' for k, v in changed.items())})" if changed else ""
                    print(f"{'OK  ' if good else 'FAIL'} {t}: judged again on its stored transcript{again}")
                    failed += 0 if good else 1
                else:
                    earlier += 0 if good else 1
                passing |= good
                continue
            heard = eleven.transcribe(t)
            words = scribe_words(t)
            x = eleven.sample(t)
            entry = {"ok": eleven.script_words(heard) == eleven.script_words(j.text), "heard": heard,
                     "speech_s": round(speech_seconds(words), 3), **_measured(j, x),
                     "f0": round(f0_median(x), 1), "sha": audio_hash(t)}
            checks[t] = entry
            _save(CHECKS, checks)
            good = passes(j, entry)
            failed += 0 if good else 1
            passing |= good
            note = "" if entry["ok"] else f"  script {plain(j.text)!r}"
            if entry["ok"] and not good:
                note = over_note(j, entry)
            elif good and not fits(j, entry):
                note = f"  fits at {entry['tempo_fit']:.2f}x faster, its pitch kept (pick records the tempo)"
            cut = cut_note(j, entry)
            if cut:
                note += f"  CHECK: cut ({cut})"
            print(f"{'OK  ' if good else 'FAIL'} {t}: heard {heard!r}, {entry['speech_s']:.2f} s heard, "
                  f"{entry['span_s']:.2f} s built, {entry['f0']:.0f} Hz{note}")
        if (made or other) and not passing:
            stuck.append(j.base)
    print(f"{failed} take(s) failed" + (f"; {earlier} earlier take(s) still failing" if earlier else ""))
    for base in stuck:
        print(f"NO PASSING TAKE {base}")
    return len(stuck)


def _keep(job: Job, chosen: str, takes: dict, derived_from: str | None = None) -> None:
    for ext in (".wav", ".scribe.json"):
        src = OUT / f"{chosen}{ext}"
        if src.exists():
            shutil.copyfile(src, OUT / f"{job.base}{ext}")
    takes[job.base] = {"take": chosen, "derived_from": derived_from}


def _shelve(names: list) -> dict:
    """Moves takes out of the repo, into Media (kept, never deleted, never overwritten: a name already there moves in
    as <name>_again2, _again3, ...); voice_takes.json and the ledger say which was used. Returns where each take went
    (its path without an extension)."""
    moved = {}
    for t in names:
        if not any((OUT / f"{t}{ext}").exists() for ext in (".wav", ".scribe.json")):
            continue
        stem, n = MEDIA / "takes" / t, 1
        while any(Path(f"{stem}{ext}").exists() for ext in (".wav", ".scribe.json")):
            n += 1
            stem = MEDIA / "takes" / f"{t}_again{n}"
        for ext in (".wav", ".scribe.json"):
            src = OUT / f"{t}{ext}"
            if src.exists():
                dst = Path(f"{stem}{ext}")
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(src), str(dst))
        moved[t] = str(stem)
    return moved


def unkeep(base: str) -> None:
    """Undoes a keep so the fragment or line can be chosen again (the plan's redo paths): removes the kept copy (each
    kept file is a copy of a take, which is shelved in Media) and its voice_takes.json record, and moves the job's
    takes back from Media into the repo unchanged, so check still trusts them; never over a file already there."""
    takes = _load(TAKES, {})
    for ext in (".wav", ".scribe.json"):
        (OUT / f"{base}{ext}").unlink(missing_ok=True)
    takes.pop(base, None)
    _save(TAKES, takes)
    line = re.fullmatch(r"(?:bossvoice/)?([a-z0-9_]+)/([a-z0-9_]+)", base)       # a whole line: its tempo goes too
    tempos = _load(TEMPO, {})
    if line and tempos.pop(f"{line.group(1)}/{line.group(2)}", None) is not None:
        _save(TEMPO, tempos)
    shelf = (MEDIA / "takes" / base).parent
    back = []
    for p in sorted(shelf.glob(f"{Path(base).name}_take*.wav")):
        if not re.fullmatch(rf"{re.escape(Path(base).name)}_take\d+", p.stem):
            continue                                   # an _againN copy stays shelved
        for ext in (".wav", ".scribe.json"):
            src, dst = shelf / f"{p.stem}{ext}", (OUT / base).parent / f"{p.stem}{ext}"
            if src.exists() and not dst.exists():
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(src), str(dst))
        back.append(p.stem)
    print(f"{base}: kept copy removed, {len(back)} take(s) back from Media: {', '.join(back)}")


def cut_fragments(line: dict, mask: int, takes: dict) -> list:
    """Whole-sentence fallback: cuts this mask's fragments out of its kept whole take by Scribe's word timings."""
    base = f"bossvoice/unsung/{line['id']}/whole_m{mask}"
    words = scribe_words(base)
    frags = line["split"]["fragments"]
    counts = [len(eleven.script_words(f["text"])) for f in frags]
    if len(words) != sum(counts):
        return [f"{base}: Scribe found {len(words)} words, the script has {sum(counts)}"]
    x = eleven.sample(base)
    i = 0
    for k, n in enumerate(counts, start=1):
        if (k, mask) in needs(line):
            a = max(0.0, words[i]["start"] - 0.04)
            b = words[i + n - 1]["end"] + 0.06
            if i + n < len(words):
                b = min(b, words[i + n]["start"] - 0.01)
            name = f"bossvoice/unsung/{line['id']}/f{k}_m{mask}"
            _write_wav(name, x[at(a):at(b)])
            takes[name] = {"take": takes[base]["take"], "derived_from": base, "cut_s": [round(a, 3), round(b, 3)]}
        i += n
    return []


def _passing(job: Job, checks: dict, problems: list, faster: bool = False) -> list | None:
    """A job's passing takes, or None (the reason added to `problems`) when it cannot be picked yet: a take not
    checked on its current audio (never keep on a transcript of other audio), or no take passing. With `faster`, a
    line none of whose takes fits may speak faster, up to its boss's bossvoice.TEMPO_MAX (_faster). A take the ledger
    says was sent other text than the job's now (usable_takes) is named and never chosen (VP3 review I1)."""
    made, other = usable_takes(job)
    for t, was in other:
        print(other_text_note(job, t, was))
    stale = [t for t in made if not checked(checks, t)]
    if stale:
        problems.append(f"{job.base}: {len(stale)} take(s) not checked on their current audio (run check)")
        return None
    good = [t for t in made if fits(job, checks[t])]
    if not good and faster:
        good = _faster(job, checks, made)
    if not good:
        problems.append(f"{job.base}: no passing take among {len(made)}"
                        + (f" ({len(other)} more sent other text than the script's now)" if other else ""))
        return None
    return good


def _faster(job: Job, checks: dict, made: list) -> list:
    """The decision for the Leviathan: a line none of whose takes fits may speak up to bossvoice.TEMPO_MAX faster,
    its pitch kept, before any text is cut. Finds the least tempo, in steps of 0.01, at which a take with the right
    words fits its budget, measured on the stretched words as the build places them; records it for the line
    (voice_tempo.json, which the build reads), measures the takes at it, and returns those that fit. [] when even the
    most allowed does not fit (or the boss may not speak faster)."""
    import bossvoice
    most = bossvoice.TEMPO_MAX.get(job.boss, 1.0)
    now = bossvoice.tempo_of(job.boss, job.line_id)
    if job.budget is None or most <= now:
        return []
    least = {}
    for t in made:
        if not words_ok(job, checks[t]):
            continue
        tempo = least_tempo(job, eleven.sample(t), floor=now)            # the same search check judged the take by
        if tempo is not None and tempo > now:
            least[t] = tempo
    if not least:
        return []
    tempo = min(least.values())
    spoken = checks[min(least, key=least.get)]["span_s"] * now
    tempos = _load(TEMPO, {})
    tempos[f"{job.boss}/{job.line_id}"] = {"tempo": tempo, "why": f"its best take speaks {spoken:.2f} s at 1.00x against "
                                                               f"{job.budget} s{' (a quiet window, no slack)' if job.hard else ''}"}
    _save(TEMPO, tempos)
    for t in least:                                    # measured again as the build will now place them
        checks[t].update(_measured(job, eleven.sample(t)))
    _save(CHECKS, checks)
    print(f"{job.base}: speaks {tempo:.2f}x faster, its pitch kept, to fit {job.budget} s")
    return [t for t in least if fits(job, checks[t])]


def _keep_and_shelve(job: Job, best: str, checks: dict, takes: dict, ref: float | None) -> None:
    """Keeps `best` under the job's own name and moves every take of the job, `best` too, to Media."""
    _keep(job, best, takes)
    moved = _shelve(takes_of(job.base))
    takes[job.base]["shelved_as"] = moved.get(best)
    # a designed voice can drift between takes; George's shipped lines span too wide a pitch range to judge by
    f0 = checks[best]["f0"]
    off = abs(f0 / ref - 1.0) if ref and f0 > 0 and job.role != "heliarch" else 0.0
    note = f"  CHECK: pitch {f0:.0f} Hz, {off:.0%} from {ref:.0f}" if off > 0.15 else ""
    if f0 <= 0 and not WHISPER.search(job.text):
        note = "  CHECK: no pitch (whispered or unvoiced; the script does not ask for a whisper)"
    cut = cut_note(job, checks[best])
    if cut:
        note += f"  CHECK: cut ({cut})"
    print(f"{job.base} <- {best}{note}")


def _stitch_problem(line: dict, names: dict, piece, seen: dict, gap: float) -> tuple | None:
    """Why the Unsung line, stitched from these takes ((fragment, mask) to take name) with this least gap between
    fragments, would not build as the script asks in one of its living sets: an opener off its lifts, or the line over
    its budget (as manifest measures it), as (how far: the seconds it speaks, or infinity off the lifts; the reason).
    None when every living set builds in time. `piece` trims a take as the stitcher does; `seen` caches by set and
    gap."""
    import bossvoice
    grid = bossvoice.grid_of(line)
    for living in bossvoice.LIVING[line["split"]["kind"]]:
        plan = bossvoice.assign(line, living)
        key = (living, gap, tuple(names[kf] for kf in plan))
        if key not in seen:
            dry, starts = bossvoice.stitch([piece(names[kf]) for kf in plan], grid, gap)
            why = lift_problem([s - starts[0] for s in starts]) if grid == "lift" else None
            far = math.inf
            start, end = bossvoice.spoken_span(bossvoice.colour(dry))
            if not why and line["budget"] and end - start > line["budget"] + (0.0 if hard(line) else SLACK):
                why, far = f"speaks {end - start:.2f} s on the {grid} grid, budget {line['budget']} s", end - start
            seen[key] = (far, f"living {living}: {why}") if why else None
        if seen[key]:
            return seen[key]
    return None


def _pick_unsung(checks: dict, takes: dict, refs: dict, problems: list) -> None:
    """The Unsung's fragments are kept a line at a time: of every set of passing takes that builds as the script asks
    (the openers on their lifts, the line inside its budget, in every living set), the one whose voices sit nearest
    their references. A relayed line that no set fits with MIN_GAP between its fragments may let its masks overlap by
    up to bossvoice.MAX_OVERLAP (the decision for VP2), recorded in voice_stitch.json so the build places it the same
    way. When nothing fits, the line stays unkept, its takes stay in the repo, and voice_refused.json names its
    longest fragment, which run --retake then retakes."""
    import itertools
    import bossvoice
    trims, whispered = {}, {}
    stitched, refused = _load(STITCH, {}), _load(REFUSED, {})

    def piece(name: str) -> np.ndarray:
        if name not in trims:
            trims[name] = bossvoice.trim_fragment(eleven.sample(name), whispered[name])
        return trims[name]

    def kf(job: Job) -> tuple:
        k, m = re.search(r"/f(\d+)_m(\d+)$", job.base).groups()
        return int(k), int(m)

    by_line = {}
    for j in jobs("unsung"):
        by_line.setdefault(j.line_id, []).append(j)
    for line in lines("unsung"):
        own = by_line.get(line["id"], [])
        todo = [j for j in own if not (OUT / f"{j.base}.wav").exists()]
        options = [_passing(j, checks, problems) for j in todo]
        if not todo or any(o is None for o in options):
            continue
        for j in own:                                                   # the stitcher trims a whisper tighter
            for name in [j.base] + takes_of(j.base):
                whispered[name] = bool(WHISPER.search(j.text))
        fixed = {kf(j): j.base for j in own if j not in todo}          # fragments kept earlier stay as they are
        seen, best, miss = {}, None, {}                                  # miss: each gap's closest miss
        relayed = line["split"]["kind"] in ("relay", "pair")
        overlap = bossvoice.overlap_gap(all(WHISPER.search(j.text) for j in own))
        gaps = [bossvoice.MIN_GAP] + ([overlap] if relayed else [])
        for gap in gaps:
            for combo in itertools.product(*options):
                names = {**fixed, **{kf(j): t for j, t in zip(todo, combo)}}
                problem = _stitch_problem(line, names, piece, seen, gap)
                if problem:
                    if gap not in miss or problem[0] < miss[gap][0]:
                        miss[gap] = problem
                    continue
                cost = (sum(bool(cut_note(j, checks[t])) for j, t in zip(todo, combo)),      # fewest cut seams,
                        sum(pitch_key(j, checks[t]["f0"], refs.get(j.role)) for j, t in zip(todo, combo)))   # then pitch
                if best is None or cost < best[0]:
                    best = (cost, combo, gap)
            if best is not None:
                break
        why = miss.get(bossvoice.MIN_GAP, (0.0, None))[1]           # how close it came without the overlap
        closest = miss.get(gaps[-1], (0.0, None))[1]                  # and under the most generous rule
        if best is None:
            longest = max(todo, key=lambda j: min(len(piece(t)) for t in usable_takes(j)[0]))   # its shortest, as placed
            refused[line["id"]] = {"why": closest, "retake": longest.base}
            problems.append(f"unsung/{line['id']}: no set of passing takes builds as the script asks, even with the masks "
                            f"overlapping {bossvoice.MAX_OVERLAP * 1000:.0f} ms (closest: {closest}). Retake its longest fragment with: "
                            f"python tools/sound/voicebatch.py run --boss unsung --retake (or a new take of every "
                            f"fragment: run --boss unsung --only {line['id']} --more 1)")
            continue
        refused.pop(line["id"], None)
        if best[2] != bossvoice.MIN_GAP:
            stitched[line["id"]] = {"gap": best[2], "why": why}
            print(f"unsung/{line['id']}: fits with its masks' words overlapping by up to "
                  f"{bossvoice.MAX_OVERLAP * 1000:.0f} ms ({why} without)")
        else:
            stitched.pop(line["id"], None)
        for j, t in zip(todo, best[1]):
            _keep_and_shelve(j, t, checks, takes, refs.get(j.role))
    _save(STITCH, stitched)
    _save(REFUSED, refused)


def pick(boss: str, whole: bool) -> int:
    checks, takes = _load(CHECKS, {}), _load(TAKES, {})
    refs = {r: v.get("ref_f0") for r, v in _load(PICKS, {}).get("roles", {}).items()}
    if boss == "heliarch":                       # new takes should sound like the six shipped ones
        import heliarch
        refs["heliarch"] = float(np.median([f0_median(eleven.sample(f"heliarch/{n}")) for n in heliarch.LINES]))
    problems = []
    if boss == "unsung" and not whole:          # fragments are chosen a line at a time, as they will be stitched
        _pick_unsung(checks, takes, refs, problems)
    else:
        for j in jobs(boss, whole):
            if (OUT / f"{j.base}.wav").exists():
                continue
            good = _passing(j, checks, problems, faster=True)
            if good is not None:
                ref = refs.get(j.role)
                best = min(good, key=lambda t: (bool(cut_note(j, checks[t])), pitch_key(j, checks[t]["f0"], ref)))
                _keep_and_shelve(j, best, checks, takes, ref)
    if boss == "unsung" and whole:              # cut each mask's fragments out of its kept whole take
        for line in lines("unsung"):
            if line["split"]["kind"] == "alone":
                continue
            for m in sorted({m for _, m in needs(line)}):
                done = all((OUT / f"bossvoice/unsung/{line['id']}/f{k}_m{m}.wav").exists() for k, mm in needs(line) if mm == m)
                if not done and (OUT / f"bossvoice/unsung/{line['id']}/whole_m{m}.wav").exists():
                    problems += cut_fragments(line, m, takes)
    _save(TAKES, takes)
    for p in problems:
        print("PROBLEM", p)
    return len(problems)


def _index(folder: str) -> Path:
    """A candidates folder's index: beside the folder (eleven.index_path), or inside it as the first designs left it."""
    new = eleven.index_path(folder)
    return new if new.exists() or not (Path(folder) / "candidates.json").exists() else Path(folder) / "candidates.json"


NEUTRAL = "A voice designed with ElevenLabs Voice Design for the Cosmic Breach game."   # what the account shows


def rank(folder: str, name: str, role: str | None = None) -> None:
    """Measures each Voice Design preview of `name` (pitch, speaking rate, programme loudness and true peak, Scribe's
    words) and ranks them against the casting brief's targets for `role` (default: the same as `name`); writes the
    metrics and ranks into the folder's index. The score is a guide for the pick, not the pick. A preview Scribe has
    already heard is not sent again: its transcript is kept beside it."""
    idx_path = _index(folder)
    data = json.loads(idx_path.read_text(encoding="utf-8"))
    entry = data[name]
    f_lo, f_hi, r_lo, r_hi = TARGETS[role or name]
    f_mid, r_mid = math.sqrt(f_lo * f_hi), (r_lo + r_hi) / 2.0
    rows = []
    for c in entry["candidates"]:
        take = c["preview_wav"][:-4]                      # voices/<name>_previewN under eleven/
        x = eleven.sample(take)
        saved = OUT / f"{take}.scribe.json"
        heard = json.loads(saved.read_text(encoding="utf-8"))["text"] if saved.exists() else eleven.transcribe(take)
        words = scribe_words(take)
        speech = speech_seconds(words)
        rate = len(words) / speech if speech > 0 else 0.0
        f0 = f0_median(x)
        clipped = int(np.sum(np.abs(x) > 0.999))
        out_f = max(0.0, math.log2(f_lo / f0), math.log2(f0 / f_hi)) if f0 > 0 else 2.0
        out_r = max(0.0, r_lo - rate, rate - r_hi) / (r_hi - r_lo)
        match = eleven.script_words(heard) == eleven.script_words(entry["text"])
        score = (4.0 * out_f + 0.5 * abs(math.log2(f0 / f_mid) if f0 > 0 else 2.0) + out_r
                 + 0.25 * abs(rate - r_mid) / (r_hi - r_lo) + (0.0 if match else 1.0) + (0.5 if clipped > 10 else 0.0))
        peak_db = 20.0 * math.log10(max(true_peak(x), 1e-9))
        c["metrics"] = {"f0": round(f0, 1), "words_per_s": round(rate, 2), "speech_s": round(speech, 2),
                        "words_match": match, "word_errors": word_errors(heard, entry["text"]), "heard": heard,
                        "lufs": round(integrated(x), 1), "true_peak_db": round(peak_db, 1),
                        "clipped_samples": clipped, "score": round(score, 3)}
        rows.append(c)
    for r, c in enumerate(sorted(rows, key=lambda c: c["metrics"]["score"]), start=1):
        c["rank"] = r
    _save(idx_path, data)
    print(f"{name}: target {f_lo:.0f} to {f_hi:.0f} Hz, {r_lo} to {r_hi} words a second")
    for c in sorted(rows, key=lambda c: c["rank"]):
        m = c["metrics"]
        said = "match" if m["words_match"] else f"DIFFER by {m['word_errors']}"
        print(f"  {c['rank']}. {c['file']}: {m['f0']:.0f} Hz, {m['words_per_s']:.2f} w/s, {m['lufs']:.1f} LUFS, "
              f"words {said}, score {m['score']:.2f}")


def keep(folder: str, name: str, label: str, only: str | None) -> None:
    """Saves Voice Design previews of `name` as voices in the account (eleven.py keep), each under its candidate name
    (colossus_a, ...) in voices.json; skips any already kept. A saved voice takes one of the plan's 10 slots. The account
    gets a neutral description (NEUTRAL), never the casting brief: Nate may open his voice list."""
    entry = json.loads(_index(folder).read_text(encoding="utf-8"))[name]
    known = json.loads(eleven.VOICES.read_text(encoding="utf-8")) if eleven.VOICES.exists() else {}
    for c in entry["candidates"]:
        if (only and c["candidate"] != only) or c["candidate"] in known:
            continue
        eleven.keep(c["candidate"], c["generated_voice_id"], f"{label} {c['candidate'][-1].upper()}", NEUTRAL)


def use(role: str, key: str, by: str, folder: str | None) -> None:
    """Gives a role (colossus, leviathan, unsung_m1 to unsung_m3) the kept voice `key` from voices.json, with the
    preview's measured pitch (from the folder's index) as the reference the take picker aims for (rebase replaces it
    with the production model's own)."""
    voices = json.loads(eleven.VOICES.read_text(encoding="utf-8"))
    if key not in voices:
        sys.exit(f"{key} is not in {eleven.VOICES.name}: keep it first (eleven.py keep)")
    f0 = None
    if folder:
        for entry in json.loads(_index(folder).read_text(encoding="utf-8")).values():
            for c in entry["candidates"]:
                if c["candidate"] == key and "metrics" in c:
                    f0 = c["metrics"]["f0"]
    picks = _load(PICKS, {"roles": {}})
    picks.setdefault("roles", {})[role] = {"voice_id": voices[key]["voice_id"], "voices_key": key, "ref_f0": f0,
                                           "picked_by": by, "time_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    _save(PICKS, picks)
    print(f"{role}: {key} ({voices[key]['voice_id']}), reference pitch {f0} Hz, picked by {by}")


def retire(keys: list) -> None:
    """Deletes saved candidate voices nobody uses, to free account slots: Nate approves exactly which, by name. Every
    voice is checked before any is deleted: it is in voices.json and not deleted yet, no role in voice_picks.json plays
    it, it never made a take (the ledger), and the account holds it as a designed (generated) voice under the label
    voices.json gave it. Each deletion is logged, and voices.json keeps the entry, marked deleted."""
    voices = json.loads(eleven.VOICES.read_text(encoding="utf-8")) if eleven.VOICES.exists() else {}
    roles = {v.get("voice_id") for v in _load(PICKS, {}).get("roles", {}).values()}
    spoke = set()
    if eleven.LEDGER.exists():
        spoke = {json.loads(l).get("voice") for l in eleven.LEDGER.read_text(encoding="utf-8").splitlines() if l.strip()}
    checked_ids = []
    for key in keys:
        entry = voices.get(key)
        if entry is None or entry.get("deleted_utc"):
            sys.exit(f"{key}: not a saved voice in {eleven.VOICES.name} (or deleted already); nothing deleted")
        vid = entry["voice_id"]
        if vid in roles:
            sys.exit(f"{key}: plays a role in {PICKS.name}; nothing deleted")
        if vid in spoke:
            sys.exit(f"{key}: made takes (the ledger); nothing deleted")
        info = eleven.voice_info(vid)
        if info.get("category") != "generated" or info.get("name") != entry.get("label"):
            sys.exit(f"{key}: the account holds {vid} as {info.get('name')!r} ({info.get('category')}), not our "
                     f"{entry.get('label')!r}; nothing deleted")
        checked_ids.append((key, vid, info.get("name")))
    for key, vid, name in checked_ids:
        eleven.delete_voice(key, vid)
        print(f"  {key} ({name}): checked unused, deleted")


def _audio(source: str) -> np.ndarray:
    """A take as float mono at the build's rate: a WAV path, or a name under eleven/ (as eleven.sample takes it)."""
    p = Path(source)
    if p.suffix.lower() == ".wav" and p.exists():
        rate, data = wavfile.read(p)
        x = data.astype(np.float64)
        x = (x.mean(axis=1) if x.ndim > 1 else x) / 32768.0
        return x if rate == SR else signal.resample_poly(x, SR, rate)
    return eleven.sample(source)


def _take_name(source: str) -> str:
    """A take's name under eleven/ (as the ledger and the checks know it) for a WAV path inside it: pick shelves takes
    to Media, so a path stops existing while the name still finds the take. Any other source stays as given."""
    try:
        return Path(source).resolve().relative_to(OUT.resolve()).with_suffix("").as_posix()
    except ValueError:
        return source


def rebase(role: str, sources: list, by: str) -> None:
    """Gives a role the reference pitch pick aims for, measured on takes from the production model: eleven_v4 speaks
    the designed voices 10 to 30% higher than their Voice Design previews, so a preview's pitch would steer pick to
    the deepest takes and flag most voiced ones. The median of the voiced takes among `sources` (WAV paths or names
    under eleven/); takes with no pitch (whispers) are skipped. The preview's value stays as preview_f0."""
    picks = _load(PICKS, {"roles": {}})
    entry = picks.get("roles", {}).get(role)
    if entry is None:
        sys.exit(f"No voice picked for {role}: see the plan's pick tasks (voice_picks.json)")
    measured = [(_take_name(s), round(f0_median(_audio(s)), 1)) for s in sources]
    used = [(s, f) for s, f in measured if f > 0]
    if not used:
        sys.exit(f"{role}: none of the {len(sources)} take(s) has a pitch (whispered?); its reference stays as it was")
    entry.setdefault("preview_f0", entry.get("ref_f0"))
    entry["ref_f0"] = round(float(np.median([f for _, f in used])), 1)
    entry["ref_source"] = {"model": "eleven_v4", "by": by, "takes": [s for s, _ in measured],
                           "f0": [f for _, f in measured], "used": [s for s, _ in used],
                           "time_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    _save(PICKS, picks)
    print(f"{role}: reference pitch {entry['ref_f0']} Hz from {len(used)} take(s) (the preview read {entry['preview_f0']})")


def _similar(a: str, b: str) -> bool:
    x, y = eleven.sample(a), eleven.sample(b)
    if abs(len(x) - len(y)) > 0.01 * SR:
        return False
    n = min(len(x), len(y))
    return float(np.corrcoef(x[:n], y[:n])[0, 1]) > 0.98


def _refused_context(e: SystemExit) -> bool:
    """eleven.tts stopped with a 400 or 422 that names the context fields: the model refuses previous and next text.
    Any other failure (a bad key, a server error) says nothing about the context."""
    m = re.match(r"text-to-speech failed (\d+): (.*)", str(e.code), re.S)
    return bool(m) and m.group(1) in ("400", "422") and bool(re.search(r"previous_text|next_text", m.group(2)))


def probe(text: str = "[hushed] to the choir", before: str = "Who comes", after: str = "without a song?",
          role: str = "unsung_m2", seed: int = 4242, label: str | None = None) -> None:
    """Does eleven_v4 use previous_text and next_text? Three takes of one Unsung fragment with one seed: two without
    the context (is the seed deterministic?), one with it (does the context change the take?). Scribe then hears every
    take: a context take that also says words of its context has read it aloud ("spoken"), which rules the fragment
    method out as surely as "ignored" or "refused". The fragment, its context, the voice and the seed are parameters (a
    whispered fragment never repeats sample for sample, so a voiced one answers better). Each run's takes go to their
    own folder, bossvoice/probe/<label>/ (run1, run2, ... by default), and voice_probe.json keeps the earlier runs, so
    a second probe never overwrites the first one's evidence. Only a 400 or 422 naming the context counts as refused;
    any other failure stops with no verdict, and the takes made so far still go to Media."""
    old = _load(PROBE, {})
    earlier = old.get("earlier", []) + ([{k: v for k, v in old.items() if k != "earlier"}] if old.get("verdict") else [])
    label = label or f"run{len(earlier) + 1}"
    names = {k: f"bossvoice/probe/{label}/{k}" for k in ("seed_a", "seed_b", "context")}
    voice = voice_of(role)
    try:
        eleven.tts(names["seed_a"], text, voice, seed=seed)
        eleven.tts(names["seed_b"], text, voice, seed=seed)
        try:
            eleven.tts(names["context"], text, voice, previous_text=before, next_text=after, seed=seed)
            accepted = True
        except SystemExit as e:
            if not _refused_context(e):
                raise
            print(f"the context was refused: {e}")
            accepted = False
        around = set(eleven.script_words(f"{before} {after}")) - set(eleven.script_words(text))
        takes = {}
        for key, n in names.items():
            if not (OUT / f"{n}.wav").exists():
                continue
            heard = eleven.transcribe(n)
            said = eleven.script_words(heard)
            takes[key] = {"heard": heard, "words_ok": said == eleven.script_words(text),
                          "leaked": [w for w in said if w in around],
                          "speech_s": round(speech_seconds(scribe_words(n)), 3),
                          "f0": round(f0_median(eleven.sample(n)), 1)}
        deterministic = _similar(names["seed_a"], names["seed_b"])
        if not accepted:
            verdict = "refused"
        elif takes["context"]["leaked"]:
            verdict = "spoken"
        elif not deterministic:
            verdict = "unknown"
        else:
            verdict = "ignored" if _similar(names["seed_a"], names["context"]) else "honoured"
        _save(PROBE, {"verdict": verdict, "seed_deterministic": deterministic, "accepted": accepted, "role": role,
                      "label": label, "text": text, "previous_text": before, "next_text": after, "seed": seed,
                      "takes": takes, "time_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                      "earlier": earlier})
    finally:
        _shelve(list(names.values()))
    for key, t in takes.items():
        print(f"  {key}: heard {t['heard']!r}, {t['speech_s']:.2f} s, {t['f0']:.0f} Hz" +
              (f", context read aloud: {t['leaked']}" if t["leaked"] else ""))
    print(f"context: {verdict} (seed deterministic: {deterministic}); "
          f"{'use --whole for the Unsung' if verdict in ('refused', 'ignored', 'spoken') else 'use the fragment method'}")


def _events_of(line: dict) -> list:
    """(variant key, sound event) for a line: one event, or one per living set for the Unsung's break lines."""
    b, i = line["boss"], line["id"]
    if b == "heliarch":
        return [("", f"heliarch/voice_{i}")]
    if b != "unsung" or line["split"]["kind"] == "relay":
        return [("", f"bossvoice/{b}_{i}")]
    import bossvoice
    return [(lv, f"bossvoice/unsung_{i}_{lv}") for lv in bossvoice.LIVING[line["split"]["kind"]]]


# ------------------------------------------------------------------------------------------- voice over music

BLOCK_HOP = 0.05         # music_blocks: a block every 50 ms (a meter's are every 100 ms), so a stretch can start anywhere


def music_blocks(x: np.ndarray, loop: bool) -> np.ndarray:
    """The K-weighted mean square of `x` in 400 ms blocks every BLOCK_HOP (BS.1770's blocks, found at twice a meter's
    rate so loudest() can start a stretch at any 50 ms), around the seam for a loop."""
    from analysis import k_weight
    b, h = n_of(0.4), n_of(BLOCK_HOP)
    y = k_weight(np.tile(x, 3))[len(x):] if loop else k_weight(np.concatenate([x, np.zeros(max(0, b - len(x)))]))
    c = np.concatenate([[0.0], np.cumsum(np.square(y))])
    starts = np.arange(len(x) // h if loop else (len(y) - b) // h + 1) * h
    return (c[starts + b] - c[starts]) / b


def _gated_rows(ms: np.ndarray) -> np.ndarray:
    """Programme loudness (LUFS) of each row of block mean squares, gated as analysis.integrated gates them: blocks
    under -70 LUFS dropped, then blocks more than 10 LU under the mean of the rest."""
    lufs = -0.691 + 10.0 * np.log10(np.maximum(ms, 1e-20))
    keep = lufs > -70.0
    mean = (ms * keep).sum(axis=1) / np.maximum(keep.sum(axis=1), 1)
    keep &= lufs > (-0.691 + 10.0 * np.log10(np.maximum(mean, 1e-20)) - 10.0)[:, None]
    out = -0.691 + 10.0 * np.log10(np.maximum((ms * keep).sum(axis=1) / np.maximum(keep.sum(axis=1), 1), 1e-20))
    return np.where(lufs.max(axis=1) > -70.0, out, -70.0)


def _gated(ms: np.ndarray) -> float:
    """Programme loudness (LUFS) of a run of blocks (_gated_rows)."""
    return float(_gated_rows(np.asarray(ms, dtype=float)[None, :])[0])


def loudest(blocks: np.ndarray, seconds: float, loop: bool) -> float:
    """The loudest stretch of `seconds` of a music, in LUFS: a line may start anywhere in it (to BLOCK_HOP). A stretch
    is read as a meter reads it: 400 ms blocks every 100 ms from its start, gated (VP2 re-review N3: stepping 100 ms
    read the music up to 0.1 LU quieter than the true loudest stretch)."""
    k = max(1, int(round((max(seconds, 0.4) - 0.4) / 0.1)) + 1)              # a meter's blocks in the stretch
    step = int(round(0.1 / BLOCK_HOP))                                         # ours between them
    span = step * (k - 1) + 1
    if loop:
        blocks = np.concatenate([blocks, blocks[:span]])
    if len(blocks) < span:                                                     # shorter than the stretch: all of it
        return _gated(blocks[::step])
    windows = np.lib.stride_tricks.sliding_window_view(blocks, span)[:, ::step]
    return float(_gated_rows(windows).max())


def _music_file(stem: str) -> np.ndarray:
    import soundfile as sf
    y, _ = sf.read(str(ASSETS / "cosmicbreach" / "sounds" / f"{stem}.ogg"), always_2d=True)
    return y[:, 0]


def unsung_block(living: str, singer: int, mode: str, chord: int, load=_music_file) -> np.ndarray:
    """One line of the Unsung's song as the client plays it: every living mask's block started together, the
    singer's sung, the others humming (or rising, through a Harmonize warning), at the fight's volumes."""
    mix = np.zeros(0)
    for mask in (int(c) for c in living):
        how = "sung" if mask == singer else mode
        stem = f"music/unsung/{VOICES[mask]}_{how}" + ("" if how == "rise" else f"_{chord}")
        x = CHOIR[how] * load(stem)
        mix = np.pad(mix, (0, max(0, len(x) - len(mix))))
        mix[:len(x)] += x
    return mix


class Music(NamedTuple):
    """One thing that can play under a line: its K-weighted blocks (music_blocks), and whether it loops (a boss's loop
    does; the death chord plays once)."""
    name: str
    blocks: np.ndarray
    loop: bool = True


@lru_cache(maxsize=None)
def music_states(boss: str, living: str = "123") -> tuple:
    """What can play under a boss's line, as Music: each loop the fight plays, or for the Unsung every singer with
    the others humming or rising, the four chords' blocks laid every TURN seconds as one loop (the same singer on, so
    each block's tail rings into the next)."""
    if boss != "unsung":
        return tuple(Music(stem, music_blocks(_music_file(stem), loop=True)) for stem in MUSIC[boss])
    out = []
    alive = [int(c) for c in living]
    for singer in alive:
        for mode in ("hum", "rise") if len(alive) > 1 else ("hum",):
            cycle = np.zeros(n_of(4 * TURN))
            for chord in range(4):
                x = unsung_block(living, singer, mode, chord)
                np.add.at(cycle, (n_of(chord * TURN) + np.arange(len(x))) % len(cycle), x)
            out.append(Music(f"mask {singer} sings, the others {mode}", music_blocks(cycle, loop=True)))
    return tuple(out)


def death_states(load=_music_file) -> tuple:
    """What plays under the Unsung's kill line: her choir has stopped, but the one soft chord the server plays at death
    tick 24 is still ringing when the line starts at tick 36 (CHORD_LAG). Only the chord from that moment on is under
    the line, and it plays once, so no loop."""
    chord = load(CHORD)
    return (Music("the death chord", music_blocks(chord[at(CHORD_LAG):], loop=False), False),)


def music_for(line: dict, key: str | None) -> tuple:
    """What a built line is heard over: the death chord for the Unsung's kill line, her choir for her other lines (the
    living set of the variant `key`), the boss's own loops for the rest. An Unsung opener is the exception, read over
    the first notes instead (line_margins); the choir here is only its conservative stand-in."""
    if line["boss"] != "unsung":
        return music_states(line["boss"], "123")
    return death_states() if line["trigger"] == "boss_kill" else music_states("unsung", key or "123")


def event_id(stem: str) -> str:
    """A sound under sounds/ as the sound event id the game knows it by."""
    return f"{NAMESPACE}:{stem}"


@lru_cache(maxsize=None)
def choir_events() -> list:
    """Every music block of her choir (UnsungMusic starts one per living mask on each line of the song, and one
    rising block through a Harmonize warning), as the sound event ids sounds.json defines."""
    defined = json.loads((ASSETS / NAMESPACE / "sounds.json").read_text(encoding="utf-8"))
    return sorted(event_id(k) for k in defined if k.startswith("music/unsung/"))


def duck_of(line: dict) -> list:
    """The music the playback should pull down while this line speaks, as sound event ids (VP2 re-review N4): the
    boss's loop, or loops (one per phase for boss 4), her choir's blocks, or for her kill line the one soft chord."""
    if line["boss"] != "unsung":
        return [event_id(stem) for stem in MUSIC[line["boss"]]]
    if line["trigger"] == "fight_start":             # an opener: her choir has not started (it starts with the fight)
        return []
    return [event_id(CHORD)] if line["trigger"] == "boss_kill" else list(choir_events())


def opener_notes(starts: list, length: int, load=_music_file) -> np.ndarray:
    """What plays under an Unsung opener: Unsung.introTick sounds each mask's first note (unsung/first_<voice>) at its
    lift, one beat before that mask's fragment, and her choir does not start until the fight does (UnsungMusic), after
    the last fragment. `starts` are the fragments' starts in the event; the notes are placed at their lifts, `length`
    samples long."""
    import bossvoice
    under = np.zeros(length)
    for voice, start in zip(("alto", "tenor", "bass"), starts):
        note = load(f"unsung/first_{voice}")
        a = at(max(0.0, start - bossvoice.BEAT))
        n = max(0, min(len(note), length - a))
        under[a:a + n] += note[:n]
    return under


def aligned_margin(event: np.ndarray, start: float, end: float, under: np.ndarray, name: str) -> dict:
    """How far the words between `start` and `end` sit over what plays under them at that very moment (`under`, laid on
    the event's own timeline: music whose timing against the line is fixed, so no search for its loudest stretch)."""
    voice, music = integrated(event[at(start):at(end)]), integrated(under[at(start):at(end)])
    return {"lu": round(voice - music, 2), "voice": round(voice, 2), "music_lufs": round(music, 2), "music": name}


def margin(event: np.ndarray, start: float, end: float, states) -> dict:
    """How far the words between `start` and `end` (seconds into the built event) sit over the loudest stretch of the
    same length of any of the music `states` (Music, or a (name, blocks) pair for a loop), in LU."""
    voice = integrated(event[at(start):at(end)])
    music, name = max((loudest(m.blocks, end - start, loop=m.loop), m.name) for m in map(lambda s: Music(*s), states))
    return {"lu": round(voice - music, 2), "voice": round(voice, 2), "music_lufs": round(music, 2), "music": name}


def speaker_spans(line: dict, key: str | None, parts: dict) -> list:
    """(speaker, start, end) of the words each speaker says in a built line: the boss for a whole line, each mask for
    an Unsung fragment (its words inside the padding the stitcher keeps)."""
    import bossvoice
    if line["boss"] != "unsung":
        start, end = bossvoice.spoken_span(parts["words"])
        return [(line["boss"], start, end)]
    out = []
    frags = line["split"]["fragments"]
    for (k, mask), s in zip(bossvoice.assign(line, key or "123"), parts["starts"]):
        pre, post = bossvoice.PAD[bool(WHISPER.search(frags[k - 1]["text"]))]
        out.append((f"mask {mask}", s + pre, s + len(bossvoice.fragment(line["id"], k, mask)) / SR - post))
    return out


def line_margins(line: dict, key: str | None, parts: dict, event: np.ndarray) -> list:
    """Each speaker's margin over the music in one built line (music_for): the boss's whole line, or each mask's
    fragment. An Unsung opener plays in the intro, before her choir starts, over the three first notes at their lifts
    (opener_notes), which sound at fixed moments against it: read where they sound, not at their loudest."""
    spans = speaker_spans(line, key, parts)
    if line["boss"] == "unsung" and line["trigger"] == "fight_start":
        under = opener_notes(parts["starts"], len(event))
        return [{"speaker": who, **aligned_margin(event, a, b, under, "the first notes")} for who, a, b in spans]
    states = music_for(line, key)
    return [{"speaker": who, **margin(event, a, b, states)} for who, a, b in spans]


def held_fragments(parts: dict) -> list:
    """(speaker, dB) of each Unsung fragment its own peaks hold below its mask's loudness (bossvoice.unsung_parts)."""
    return [(f"mask {m}", h) for m, h in zip(parts.get("masks", []), parts.get("held", [])) if h > 0.05]


def margins() -> int:
    """Every built line's speakers against the music under them (free; the Unsung's kill line against the death
    chord): each speaker's lowest margin, its median and where it is lowest, and every span under MARGIN_LU. Returns
    how many spans fall short."""
    import soundfile as sf
    import bossvoice
    rows, held = [], []
    for line in lines():
        for key, event in _events_of(line):
            ogg = ASSETS / "cosmicbreach" / "sounds" / f"{event}.ogg"
            if not ogg.exists():
                continue
            y, _ = sf.read(str(ogg), always_2d=True)
            parts = _parts(line, key)
            found = line_margins(line, key, parts, y[:, 0])
            for m in found:
                rows.append({**m, "event": event})
            held += [(event, who, h) for who, h in held_fragments(parts)]
    short = [r for r in rows if r["lu"] < MARGIN_LU]
    for who in sorted({r["speaker"] for r in rows}):
        own = sorted((r for r in rows if r["speaker"] == who), key=lambda r: r["lu"])
        low = own[0]
        print(f"{who:10s} {len(own):3d} spans, lowest {low['lu']:.2f} LU ({low['event']}: words {low['voice']:.1f} LUFS over "
              f"{low['music']} {low['music_lufs']:.1f}), median {float(np.median([r['lu'] for r in own])):.2f}")
    for event, who, h in sorted(held, key=lambda t: -t[2]):
        print(f"HELD {event} {who}: its peaks keep it {h:.1f} dB under its mask's loudness (the {bossvoice.LIMIT_MAX_DB:.0f} dB cap)")
    for r in short:
        print(f"SHORT {r['event']} {r['speaker']}: {r['lu']:.2f} LU over {r['music']}")
    return len(short)


def _parts(line: dict, key: str | None) -> dict:
    import bossvoice
    import heliarch
    b = line["boss"]
    if b == "heliarch":
        return heliarch.voice_parts(line["id"])
    if b == "colossus":
        return bossvoice.colossus_parts(line["id"])
    if b == "leviathan":
        return bossvoice.leviathan_parts(line["id"])
    return bossvoice.unsung_parts(line["id"], key or "123")


def speech_window(speech_start_ticks: int, speech_ticks: int) -> int:
    """The quiet window the Leviathan's director needs for a line (Voice Script v1, director rule 7): its speech, from
    speech_start_ticks to speech_ticks, plus GATE_TICKS."""
    return speech_ticks - speech_start_ticks + GATE_TICKS


def is_long(speech_start_ticks: int, speech_ticks: int) -> bool:
    """The line needs more than the GAP_TICKS the scheduler guarantees after an attack: it cannot go in an attack gap."""
    return speech_window(speech_start_ticks, speech_ticks) > GAP_TICKS


def gap_fields(line: dict, speech_start_ticks: int, speech_ticks: int) -> dict:
    """What the handoff says about a line against the Leviathan's quiet window, for a Leviathan line (nothing for any
    other boss): `long` (the playback plays a long one only in a coil pause, in the opening or at the kill), and the
    window it needs counted both ways so nobody works it out again (VP3 review F1). `window_ticks` counts from the words:
    the speech plus GATE_TICKS, what `long` is judged by and what the plan defines; start the file speech_start_ticks
    before the window opens. `file_window_ticks` counts from the file's start (it holds 2 to 4 ticks of lead before the
    words): speech_ticks plus GATE_TICKS, the number a director needs that gates and places by the file."""
    if line["boss"] != "leviathan":
        return {}
    return {"long": is_long(speech_start_ticks, speech_ticks), "window_ticks": speech_window(speech_start_ticks, speech_ticks),
            "file_window_ticks": speech_ticks + GATE_TICKS}


def gap_problem(line: dict, speech_start_ticks: int, speech_ticks: int) -> str | None:
    """Why a Leviathan line made for the attack gaps (its delivery says "Quiet window only") is not one: it is long.
    Any other long line (an opener, a Moorage line, the kill) is long by design."""
    if line["boss"] == "leviathan" and hard(line) and is_long(speech_start_ticks, speech_ticks):
        return (f"a quiet-window line speaks {speech_ticks - speech_start_ticks} ticks, the {GAP_TICKS}-tick window the "
                f"scheduler guarantees leaves {GAP_TICKS - GATE_TICKS} of speech")
    return None


def manifest() -> int:
    """voice_lines.json for Lane A: every line with its events, subtitle keys, length and spoken length in ticks, and
    for the Unsung each fragment's mask and start, the lowest margin over the music under it and the music to duck; for
    the Leviathan whether the line is `long` (it needs more than the quiet window its scheduler guarantees after an
    attack) and the window it needs, from its words and from its file's start (gap_fields). Run after the build. Also checks each line's budget on the built words, each speaker's margin
    over that music (MARGIN_LU, the same span, line_margins) and that a line made for the attack gaps fits one
    (gap_problem)."""
    import soundfile as sf
    import bossvoice
    import heliarch
    sounds = ASSETS / "cosmicbreach" / "sounds"
    out = {"version": 1, "source": json.loads(SCRIPT.read_text(encoding="utf-8"))["source"],
           "made_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "global_gap_s": GAPS, "bosses": {}}
    problems = []
    for line in lines():
        b = line["boss"]
        entry = {k: line[k] for k in ("id", "trigger", "condition", "priority", "repeat", "words", "delivery")}
        entry["variants"] = {}
        for key, event in _events_of(line):
            ogg = sounds / f"{event}.ogg"
            if not ogg.exists():
                problems.append(f"{event}: not built")
                continue
            parts = _parts(line, key)
            start, end = bossvoice.spoken_span(parts["words"])         # the measure check judged each take by
            seconds = sf.info(str(ogg)).frames / 44100.0
            y, _ = sf.read(str(ogg), always_2d=True)
            found = line_margins(line, key, parts, y[:, 0])
            v = {"event": f"cosmicbreach:{event}", "subtitle": "subtitles.cosmicbreach." + event.replace("/", "."),
                 "length_ticks": math.ceil(seconds * 20.0), "speech_start_ticks": int(start * 20.0),
                 "speech_ticks": math.ceil(end * 20.0),
                 "margin_lu": min(m["lu"] for m in found),
                 "duck": duck_of(line)}
            tempo = bossvoice.tempo_of(b, line["id"])
            if tempo != 1.0:
                v["tempo"] = tempo                                # spoken this much faster, pitch kept
            v.update(gap_fields(line, v["speech_start_ticks"], v["speech_ticks"]))
            gap = gap_problem(line, v["speech_start_ticks"], v["speech_ticks"])
            if gap:
                problems.append(f"{event}: {gap}")
            if "starts" in parts:
                v["fragments"] = [{"mask": m, "start_ticks": int(round(s * 20.0))}
                                  for m, s in zip(parts["masks"], parts["starts"])]
                why = lift_problem([s - parts["starts"][0] for s in parts["starts"]])
                if bossvoice.grid_of(line) == "lift" and why:
                    problems.append(f"{event}: {why}")
            entry["variants"][key or "all"] = v
            if line["budget"] and end - start > line["budget"] + (0.0 if hard(line) else BUILD_SLACK):
                problems.append(f"{event}: speaks {end - start:.2f} s, budget {line['budget']} s")
            for m in found:
                if m["lu"] < MARGIN_LU:
                    problems.append(f"{event}: {m['speaker']} only {m['lu']:.1f} LU over {m['music']}, {MARGIN_LU} wanted")
        out["bosses"].setdefault(b, []).append(entry)
    _save(HANDOFF, out)
    for p in problems:
        print("PROBLEM", p)
    print(f"{sum(len(v) for v in out['bosses'].values())} lines in {HANDOFF.relative_to(REPO)}; {len(problems)} problem(s)")
    return len(problems)


def integrate() -> None:
    """Adds every built voice event to sounds.json, the bosses 1 to 3 subtitles to a new lang namespace
    (cosmicbreach_bossvoice), and boss 4's new subtitles beside its shipped ones. Idempotent."""
    data = json.loads(HANDOFF.read_text(encoding="utf-8"))
    sounds_path = ASSETS / "cosmicbreach" / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    hel_path = ASSETS / "cosmicbreach_heliarch" / "lang" / "en_us.json"
    hel = json.loads(hel_path.read_text(encoding="utf-8"))
    ours_path = ASSETS / "cosmicbreach_bossvoice" / "lang" / "en_us.json"
    ours = {}
    new_hel_sounds, new_hel_lang, new_sounds = {}, {}, {}
    for boss, entries in data["bosses"].items():
        for e in entries:
            for v in e["variants"].values():
                key = v["event"].split(":", 1)[1]
                spec = {"sounds": [v["event"]], "subtitle": v["subtitle"]}
                caption = f"\"{e['words']}\""
                if boss == "heliarch":
                    if key not in sounds:
                        new_hel_sounds[key] = spec
                    if v["subtitle"] not in hel:
                        new_hel_lang[v["subtitle"]] = caption
                else:
                    new_sounds[key] = spec
                    ours[v["subtitle"]] = caption

    def after_last(d: dict, prefix: str, extra: dict) -> dict:
        """d with `extra` inserted after its last key starting with `prefix` (at the end if none does)."""
        keys = [k for k in d if k.startswith(prefix)]
        if not keys or not extra:
            return {**d, **extra}
        out = {}
        for k, val in d.items():
            out[k] = val
            if k == keys[-1]:
                out.update(extra)
        return out

    sounds = after_last(sounds, "heliarch/voice_", new_hel_sounds)
    sounds = {k: v for k, v in sounds.items() if not k.startswith("bossvoice/")}
    sounds.update(new_sounds)
    hel = after_last(hel, "subtitles.cosmicbreach.heliarch.voice_", new_hel_lang)
    for path, d in ((sounds_path, sounds), (hel_path, hel), (ours_path, ours)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    count = sum(1 for k in sounds if k.startswith("heliarch/") or k.startswith("music/heliarch"))
    print(f"sounds.json: {len(new_sounds)} boss voice events, {len(new_hel_sounds)} new boss 4 lines; "
          f"lang: {len(ours)} + {len(new_hel_lang)} subtitles")
    print(f"HeliarchResourcesTest must now count {count} heliarch sounds")


def accept(take: str, reason: str, by: str) -> None:
    """Accepts one take whose words Scribe keeps mishearing (the plan's Task 9 Step 4), as `by` decided after
    listening: recorded on its check with the reason, the date and the audio it holds, so fits passes that audio
    only (a new take, or the same name with other audio, is judged afresh) and audit lists it. Note it in the Log."""
    checks = _load(CHECKS, {})
    if take not in checks:
        sys.exit(f"{take}: check has never heard it, so there is nothing to accept")
    entry = checks[take]
    entry["accepted"] = {"reason": reason, "by": by, "date": time.strftime("%Y-%m-%d", time.gmtime()),
                         "sha": entry.get("sha"), "heard": entry.get("heard")}
    _save(CHECKS, checks)
    print(f"{take}: accepted by {by} ({reason}); Scribe heard {entry.get('heard')!r}")


def audit() -> int:
    """Every kept take traces to a ledger entry made on the paid plan, its stored transcript matches today's
    script under today's comparison or the take was accepted (listed with who decided), and it was sent the text its
    script row has now, tags included (STALE: a take made before a tag change). Prints the counts CREDITS.md needs;
    returns the number of problems."""
    ledger = [json.loads(l) for l in eleven.LEDGER.read_text(encoding="utf-8").splitlines() if l.strip()]
    tts = {e["name"]: e for e in ledger if e.get("kind") == "tts"}
    takes, checks = _load(TAKES, {}), _load(CHECKS, {})
    by_base, known = {j.base: j for b in BOSSES for j in jobs(b)}, known_jobs()
    bad, failing, stale, voices = [], [], [], set()
    for kept, info in takes.items():
        e = tts.get(info["take"])
        if e is None or e.get("tier") != "paid":
            bad.append(kept)
        else:
            voices.add(e["voice"])
        j = by_base.get(kept)
        # the job the take was made for: a fragment cut from a whole sentence (the whole-sentence fallback) was sent the
        # sentence, so it is judged by the sentence's job (VP3 review M1), and so is the whole take itself
        job = known.get(info.get("derived_from") or kept)
        # a take is kept for its row as the row reads now: what the ledger says it was sent, tags included, must be that text
        if e is not None and job is not None and e.get("text") != job.text:
            stale.append(kept)
            print(f"STALE {kept} <- {info['take']}: sent {e.get('text')!r}, the script now says {job.text!r}")
        if j is not None and (OUT / f"{kept}.wav").exists():       # the ear check's list: seams that look cut
            cut = cut_note(j, _edges(eleven.sample(kept)))
            if cut:
                print(f"CUT {kept}: {cut}")
        c, text = checks.get(info["take"]), (job.text if job else None)
        if c is None or text is None or eleven.script_words(c["heard"]) == eleven.script_words(text):
            continue
        acc = c.get("accepted")
        if words_ok(job, c):
            print(f"ACCEPTED {kept} <- {info['take']}: Scribe heard {c['heard']!r}; {acc['reason']} "
                  f"({acc['by']}, {acc['date']})")
        else:
            failing.append(kept)
            print(f"FAILS {kept} <- {info['take']}: Scribe heard {c['heard']!r}, the script says {plain(text)!r}, "
                  f"and nobody has accepted it")
    script = lines()
    new = [l for l in script if not l["shipped"]]
    print(f"{len(takes)} kept takes for {len(new)} new lines ({len(script)} lines in the script), "
          f"{len(voices)} voices, {len(bad)} without a paid ledger entry, {len(failing)} failing the word check, "
          f"{len(stale)} made from other text than the script's")
    for k in bad:
        print("UNPAID", k)
    return len(bad) + len(failing) + len(stale)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("export"); p.add_argument("note")
    p = sub.add_parser("plan"); p.add_argument("--boss", choices=BOSSES); p.add_argument("--takes", type=int, default=2)
    p.add_argument("--whole", action="store_true")
    p = sub.add_parser("probe"); p.add_argument("--text", default="[hushed] to the choir")
    p.add_argument("--previous-text", default="Who comes"); p.add_argument("--next-text", default="without a song?")
    p.add_argument("--role", default="unsung_m2", choices=sorted(TARGETS)); p.add_argument("--seed", type=int, default=4242)
    p.add_argument("--label")
    p = sub.add_parser("run"); p.add_argument("--boss", choices=BOSSES, required=True)
    p.add_argument("--takes", type=int, default=2); p.add_argument("--only", default="")
    p.add_argument("--retake", action="store_true"); p.add_argument("--whole", action="store_true")
    p.add_argument("--more", type=int, default=0)
    p.add_argument("--dry-run", action="store_true")
    for name in ("check", "pick"):
        p = sub.add_parser(name); p.add_argument("--boss", choices=BOSSES, required=True)
        p.add_argument("--whole", action="store_true")
    p = sub.add_parser("rank"); p.add_argument("folder"); p.add_argument("--name", required=True)
    p.add_argument("--role", choices=sorted(TARGETS))
    p = sub.add_parser("keep"); p.add_argument("folder"); p.add_argument("--name", required=True)
    p.add_argument("--label", required=True); p.add_argument("--only")
    p = sub.add_parser("use"); p.add_argument("role", choices=sorted(TARGETS)); p.add_argument("key")
    p.add_argument("--by", required=True); p.add_argument("--folder")
    p = sub.add_parser("unkeep"); p.add_argument("bases", nargs="+")
    p = sub.add_parser("accept"); p.add_argument("take"); p.add_argument("--reason", required=True)
    p.add_argument("--by", required=True)
    p = sub.add_parser("retire"); p.add_argument("keys", nargs="+")
    p = sub.add_parser("rebase"); p.add_argument("role", choices=sorted(TARGETS)); p.add_argument("takes", nargs="+")
    p.add_argument("--by", default="eleven_v4 takes")
    for name in ("manifest", "integrate", "audit", "margins"):
        sub.add_parser(name)
    a = ap.parse_args()
    if a.cmd == "export":
        export(a.note)
    elif a.cmd == "plan":
        plan(a.boss, a.takes, a.whole)
    elif a.cmd == "probe":
        probe(a.text, a.previous_text, a.next_text, a.role, a.seed, a.label)
    elif a.cmd == "run":
        run(a.boss, a.takes, {s for s in a.only.split(",") if s}, a.retake, a.whole, a.dry_run, a.more)
    elif a.cmd == "check":
        sys.exit(1 if check(a.boss, a.whole) else 0)
    elif a.cmd == "pick":
        sys.exit(1 if pick(a.boss, a.whole) else 0)
    elif a.cmd == "rank":
        rank(a.folder, a.name, a.role)
    elif a.cmd == "keep":
        keep(a.folder, a.name, a.label, a.only)
    elif a.cmd == "use":
        use(a.role, a.key, a.by, a.folder)
    elif a.cmd == "unkeep":
        for b in a.bases:
            unkeep(b)
    elif a.cmd == "accept":
        accept(a.take, a.reason, a.by)
    elif a.cmd == "retire":
        retire(a.keys)
    elif a.cmd == "rebase":
        rebase(a.role, a.takes, a.by)
    elif a.cmd == "manifest":
        sys.exit(1 if manifest() else 0)
    elif a.cmd == "integrate":
        integrate()
    elif a.cmd == "audit":
        sys.exit(1 if audit() else 0)
    elif a.cmd == "margins":
        sys.exit(1 if margins() else 0)


if __name__ == "__main__":
    main()
