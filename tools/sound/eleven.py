"""ElevenLabs as a source of raw audio for the sound set: sound effects, speech and designed voices.

The build never calls the network. This tool fetches takes once and keeps them as WAV files under
tools/sound/eleven/<category>/, with every request logged in tools/sound/eleven/ledger.jsonl; event
modules then load a take with `sample("category/name")` and shape it like any synthesized layer, so
loudness, trimming and encoding stay in build.py.

    python tools/sound/eleven.py credits
    python tools/sound/eleven.py sfx  colossus/roar "a giant crystal golem roars, glass grinding" --seconds 3 [--takes 2] [--loop] [--influence 0.5]
    python tools/sound/eleven.py tts  heliarch/intro "[slowly] You came." --voice <voice_id> [--model eleven_v4]
    python tools/sound/eleven.py voices [--search deep]
    python tools/sound/eleven.py design heliarch "an ancient regal voice, hollow and resonant" "Sample line to preview."
    python tools/sound/eleven.py keep heliarch <generated_voice_id> "Heliarch" "description"

The API key is read from the ELEVENLABS_API_KEY environment variable, else from a .env file (the repo root's,
which git ignores, or the path in COSMIC_BREACH_ENV). It is never printed or logged. Credits: every
paid call checks the balance first and refuses to run if it would leave less than --reserve credits.

Every ledger entry records the plan it was made under ("tier": "free" or "paid", "plan": the API's tier name),
a UTC time and the API's request ids. Takes made under a paid plan carry ElevenLabs' commercial licence; the
2026-09-28 free-plan takes were all regenerated under the Starter plan on 2026-09-30 and none of them ship.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
from pathlib import Path

import numpy as np
from scipy.io import wavfile

HERE = Path(__file__).resolve().parent
OUT = HERE / "eleven"
LEDGER = OUT / "ledger.jsonl"
VOICES = OUT / "voices.json"
API = "https://api.elevenlabs.io"
SR = 44100
MAIN_ENV = Path(os.environ.get("COSMIC_BREACH_ENV") or HERE.parents[1] / ".env")
SFX_CREDITS_PER_SECOND = 40          # the documented price when a duration is given
FORMAT = "mp3_44100_128"             # what the pipeline expects (decoded to WAV with ffmpeg), on any plan


def _key() -> str:
    key = os.environ.get("ELEVENLABS_API_KEY", "").strip()
    if key:
        return key
    for env in (HERE.parents[1] / ".env", MAIN_ENV):
        if env.exists():
            for line in env.read_text(encoding="utf-8").splitlines():
                if line.startswith("ELEVENLABS_API_KEY="):
                    key = line.split("=", 1)[1].strip().strip('"').strip("'")
                    if key:
                        return key
    sys.exit("No ElevenLabs key: set ELEVENLABS_API_KEY or put it in the repo's .env")


def _session():
    import requests
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
    s = requests.Session()
    s.headers.update({"xi-api-key": _key()})
    # 429 means the request was not run, so retrying it (POSTs included) never bills twice; so are connection errors.
    # A read error (or any other after the request went out) may follow a request that ran and billed: never resend.
    s.mount("https://", HTTPAdapter(max_retries=Retry(total=6, read=0, other=0, status_forcelist=[429], backoff_factor=2,
                                                      allowed_methods=None, raise_on_status=False)))
    return s


def credits(s=None) -> dict:
    """The subscription's used and total credits, and when they reset."""
    s = s or _session()
    r = s.get(f"{API}/v1/user/subscription", timeout=30)
    r.raise_for_status()
    j = r.json()
    used, limit = j.get("character_count", 0), j.get("character_limit", 0)
    return {"used": used, "limit": limit, "left": limit - used, "tier": j.get("tier"),
            "reset_unix": j.get("next_character_count_reset_unix")}


def plan_of(c: dict) -> str:
    """The ledger's tier label for a subscription: "free" or "paid" (starter and up)."""
    return "free" if (c.get("tier") or "free") == "free" else "paid"


def ledger_spend(days: int = 30, tier: str = "paid") -> float:
    """Credits spent in the last `days` under one tier by our own count: 1 a character of speech, 40 a second
    of effect. The API's own balance lags and flips between stale values, so the guard trusts the smaller."""
    if not LEDGER.exists():
        return 0.0
    cutoff = time.time() - days * 86400
    total = 0.0
    for line in LEDGER.read_text(encoding="utf-8").splitlines():
        e = json.loads(line)
        when = time.mktime(time.strptime(e.get("time", "1970-01-02 00:00:00"), "%Y-%m-%d %H:%M:%S"))
        if when < cutoff or e.get("tier", "free") != tier:
            continue
        if e.get("kind") == "tts":
            total += len(e.get("text", "")) * e.get("takes", 1)
        elif e.get("kind") == "sfx":
            total += (e.get("seconds") or 5.0) * SFX_CREDITS_PER_SECOND * e.get("takes", 1)
        elif e.get("kind") == "design":
            total += len(e.get("text", "")) * 3
    return total


def _guard(s, estimate: float, reserve: int):
    """The smaller of the API's balance and our own count, and a refusal if this call would dig into the reserve.
    Returns (credits left, the subscription)."""
    c = credits(s)
    left = min(c["left"], c["limit"] - ledger_spend(tier=plan_of(c)))
    if left - estimate < reserve:
        sys.exit(f"Refusing: about {left:.0f} credits left, this needs about {estimate:.0f} and {reserve} are held in reserve")
    return int(left), c


def _log(entry: dict, c: dict | None = None) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    if c is not None:
        entry["tier"], entry["plan"] = plan_of(c), c.get("tier")
    entry["time"] = time.strftime("%Y-%m-%d %H:%M:%S")
    entry["time_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    with LEDGER.open("a", encoding="utf-8") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def _save_pcm(raw: bytes, name: str) -> Path:
    """Raw 16-bit little-endian mono PCM at 44.1 kHz, as returned for output_format pcm_44100."""
    path = OUT / f"{name}.wav"
    path.parent.mkdir(parents=True, exist_ok=True)
    data = np.frombuffer(raw, dtype="<i2")
    wavfile.write(path, SR, data)
    return path


def _save_mp3_as_wav(audio: bytes, name: str) -> Path:
    """An MP3 answer decoded to a 44.1 kHz mono WAV with ffmpeg."""
    import subprocess
    import tempfile
    path = OUT / f"{name}.wav"
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as f:
        f.write(audio)
        tmp = f.name
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-ac", "1", "-ar", str(SR), str(path)], check=True)
    os.unlink(tmp)
    return path


def _take_names(name: str, takes: int):
    if takes == 1:
        return [name]
    return [f"{name}_take{i + 1}" for i in range(takes)]


def sfx(name: str, text: str, seconds: float | None, takes: int = 1, loop: bool = False,
        influence: float = 0.5, reserve: int = 500) -> list:
    s = _session()
    per = (seconds or 5.0) * SFX_CREDITS_PER_SECOND
    before, c = _guard(s, per * takes, reserve)
    paths, ids = [], []
    for stem in _take_names(name, takes):
        body = {"text": text, "prompt_influence": influence, "model_id": "eleven_text_to_sound_v2"}
        if seconds:
            body["duration_seconds"] = seconds
        if loop:
            body["loop"] = True
        r = s.post(f"{API}/v1/sound-generation", params={"output_format": FORMAT}, json=body, timeout=180)
        if r.status_code != 200:
            sys.exit(f"sound-generation failed {r.status_code}: {r.text[:300]}")
        ids.append(r.headers.get("request-id"))
        paths.append(_save_mp3_as_wav(r.content, stem))
    spent = int(round(per * takes))
    _log({"kind": "sfx", "name": name, "takes": takes, "text": text, "seconds": seconds, "loop": loop,
          "influence": influence, "model": "eleven_text_to_sound_v2", "format": FORMAT,
          "credits": spent, "left": before - spent, "request_ids": ids}, c)
    print(f"{takes} take(s) of {name}: {spent} credits by our count, {before - spent} left")
    for p in paths:
        print("  ", p.relative_to(HERE))
    return paths


def tts(name: str, text: str, voice: str, model: str = "eleven_v4", takes: int = 1, stability: float | None = None,
        similarity: float | None = None, style: float | None = None, reserve: int = 500,
        previous_text: str | None = None, next_text: str | None = None, seed: int | None = None) -> list:
    s = _session()
    before, c = _guard(s, len(text) * takes, reserve)
    paths, ids = [], []
    for stem in _take_names(name, takes):
        body = {"text": text, "model_id": model}
        settings = {k: v for k, v in (("stability", stability), ("similarity_boost", similarity), ("style", style))
                    if v is not None}
        if settings:
            body["voice_settings"] = settings
        # the words around this text, so its prosody carries on from them and into them (an Unsung mask's fragment)
        if previous_text:
            body["previous_text"] = previous_text
        if next_text:
            body["next_text"] = next_text
        if seed is not None:
            body["seed"] = seed
        r = s.post(f"{API}/v1/text-to-speech/{voice}", params={"output_format": FORMAT}, json=body, timeout=180)
        if r.status_code != 200:
            sys.exit(f"text-to-speech failed {r.status_code}: {r.text[:300]}")
        ids.append(r.headers.get("request-id"))
        paths.append(_save_mp3_as_wav(r.content, stem))
    spent = len(text) * takes
    _log({"kind": "tts", "name": name, "takes": takes, "text": text, "voice": voice, "model": model,
          "settings": {"stability": stability, "similarity": similarity, "style": style}, "format": FORMAT,
          "previous_text": previous_text, "next_text": next_text, "seed": seed,
          "credits": spent, "left": before - spent, "request_ids": ids}, c)
    print(f"{takes} take(s) of {name}: {spent} credits by our count ({len(text)} characters each), {before - spent} left")
    for p in paths:
        print("  ", p.relative_to(HERE))
    return paths


def voices(search: str | None = None) -> None:
    s = _session()
    r = s.get(f"{API}/v2/voices", params={"search": search} if search else None, timeout=30)
    r.raise_for_status()
    for v in r.json().get("voices", []):
        labels = ", ".join(f"{k} {w}" for k, w in (v.get("labels") or {}).items())
        print(f"{v['voice_id']}  {v['name']}  ({labels})")


def index_path(mp3_dir) -> Path:
    """Where the index of a folder of Voice Design MP3s lives: beside the folder, never in it (index/<folder>.json),
    because the folder is what Nate opens to listen and the index carries the casting briefs and measurements."""
    return Path(mp3_dir).parent / "index" / f"{Path(mp3_dir).name}.json"


def design(name: str, description: str, text: str, reserve: int = 500, model: str = "eleven_ttv_v3",
           mp3_dir: str | None = None, numbered: bool = False) -> None:
    """Voice Design: three previews of a described voice, saved as WAVs; keep one with `keep`. With `mp3_dir` the
    previews are also saved there as the MP3s the API sent (<name>_a.mp3 to <name>_c.mp3), and indexed beside that
    folder (index_path) with each one's generated voice id, for ranking and for `keep`. `numbered` names them
    <name>1 to <name>3 instead (a second set of candidates beside a lettered one)."""
    import subprocess
    if not 20 <= len(description) <= 1000:
        sys.exit(f"Voice design needs a description of 20 to 1000 characters, got {len(description)}")
    if not 100 <= len(text) <= 1000:
        sys.exit(f"Voice design needs a preview text of 100 to 1000 characters, got {len(text)}")
    s = _session()
    estimate = len(text) * 3
    before, c = _guard(s, estimate, reserve)
    r = s.post(f"{API}/v1/text-to-voice/design",
               json={"voice_description": description, "text": text, "model_id": model},
               params={"output_format": FORMAT}, timeout=240)
    if r.status_code != 200:
        sys.exit(f"voice design failed {r.status_code}: {r.text[:300]}")
    previews = r.json().get("previews", [])
    # On record before anything else (decoding, reading the balance), so a paid call is logged whatever fails after
    # it. Counted like every other kind (our estimate, as the guard counts); the API's balance before it is kept, and
    # its balance after goes in its own entry when the read succeeds (Voice Design's real price is still measured).
    _log({"kind": "design", "name": name, "description": description, "text": text, "model": model,
          "previews": [p["generated_voice_id"] for p in previews], "credits": estimate, "left": before - estimate,
          "api_left_before": c["left"]}, c)
    try:
        api_after = credits(s)["left"]
        _log({"kind": "balance", "after": "design", "name": name, "api_left": api_after})
    except Exception as e:                        # the design is paid and logged; only the reading failed
        api_after = None
        print(f"the balance read after the design failed ({e}); the design is logged")
    index = []
    for i, p in enumerate(previews):
        audio = base64.b64decode(p["audio_base_64"])
        stem = f"voices/{name}_preview{i + 1}"
        is_mp3 = ("mpeg" in (p.get("media_type") or "") or audio[:3] == b"ID3"
                  or (len(audio) > 1 and audio[0] == 0xFF and audio[1] & 0xE0 == 0xE0))
        path = _save_mp3_as_wav(audio, stem) if is_mp3 else _save_pcm(audio, stem)
        print(f"preview {i + 1}: {p['generated_voice_id']}  {p.get('duration_secs', 0):.1f} s  {path.relative_to(HERE)}")
        if mp3_dir:
            candidate = f"{name}{i + 1}" if numbered else f"{name}_{'abcdefgh'[i]}"
            out = Path(mp3_dir) / f"{candidate}.mp3"
            out.parent.mkdir(parents=True, exist_ok=True)
            if is_mp3:
                out.write_bytes(audio)
            else:
                subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-codec:a", "libmp3lame",
                                "-b:a", "128k", str(out)], check=True)
            index.append({"candidate": candidate, "file": out.name, "generated_voice_id": p["generated_voice_id"],
                          "seconds": round(float(p.get("duration_secs", 0.0)), 2),
                          "preview_wav": path.relative_to(OUT).as_posix()})
    if mp3_dir:
        idx = index_path(mp3_dir)
        idx.parent.mkdir(parents=True, exist_ok=True)
        data = json.loads(idx.read_text(encoding="utf-8")) if idx.exists() else {}
        data[name] = {"description": description, "text": text, "model": model,
                      "made_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "candidates": index}
        idx.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        print(f"candidates: {idx}")
    real = "" if api_after is None else f" ({c['left'] - api_after} by the API's balance so far)"
    print(f"{estimate} credits by our count{real}, {before - estimate} left")


def keep(name: str, generated_voice_id: str, label: str, description: str) -> None:
    """Saves a designed preview as a voice in the account and records its id in voices.json."""
    s = _session()
    r = s.post(f"{API}/v1/text-to-voice", json={"voice_name": label, "voice_description": description,
                                                  "generated_voice_id": generated_voice_id}, timeout=60)
    if r.status_code != 200:
        sys.exit(f"saving the voice failed {r.status_code}: {r.text[:300]}")
    voice_id = r.json()["voice_id"]
    known = json.loads(VOICES.read_text(encoding="utf-8")) if VOICES.exists() else {}
    known[name] = {"voice_id": voice_id, "label": label, "description": description}
    OUT.mkdir(parents=True, exist_ok=True)
    VOICES.write_text(json.dumps(known, indent=2) + "\n", encoding="utf-8")
    _log({"kind": "keep", "name": name, "voice_id": voice_id})
    print(f"{name}: voice {voice_id}")


def voice_info(voice_id: str, s=None) -> dict:
    """The account's record of one saved voice (its name and category: generated, premade, cloned...)."""
    s = s or _session()
    r = s.get(f"{API}/v1/voices/{voice_id}", timeout=30)
    if r.status_code != 200:
        sys.exit(f"reading voice {voice_id} failed {r.status_code}: {r.text[:300]}")
    return r.json()


def delete_voice(name: str, voice_id: str, s=None) -> None:
    """Deletes a saved voice from the account, which frees its slot; cannot be undone, so voicebatch.py retire checks
    first. Logged the moment the API confirms it, before anything else can fail; voices.json keeps the entry,
    marked with the time it was deleted."""
    s = s or _session()
    r = s.delete(f"{API}/v1/voices/{voice_id}", timeout=60)
    if r.status_code != 200:
        sys.exit(f"deleting voice {voice_id} failed {r.status_code}: {r.text[:300]}")
    _log({"kind": "delete", "name": name, "voice_id": voice_id})
    known = json.loads(VOICES.read_text(encoding="utf-8")) if VOICES.exists() else {}
    if name in known:
        known[name]["deleted_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        VOICES.write_text(json.dumps(known, indent=2) + "\n", encoding="utf-8")
    print(f"{name}: voice {voice_id} deleted")


# Words a transcript cannot tell apart by sound, as Scribe has written them in production: one spelling stands for both
SAME_WORD = {"son": "sun", "gray": "grey", "it's": "its"}


def script_words(t: str | None) -> list:
    """A line's words for comparison: [tags], (audio events), punctuation and case dropped, and homophones or spelling
    variants Scribe cannot hear apart (SAME_WORD) taken as one word. Nothing else is forgiven: a word Scribe keeps
    mishearing is accepted take by take (voicebatch.py accept), by whoever decides, not by loosening this."""
    import re
    t = re.sub(r"\([^)]*\)", " ", re.sub(r"\[[^\]]*\]", " ", (t or "").lower()))
    return [SAME_WORD.get(w, w) for w in re.sub(r"[^a-z' ]", " ", t.replace("\u2019", "'")).split()]


def transcribe(name: str, s=None) -> str:
    """Scribe's transcript of a kept take. The whole answer, with each word's start and end, is saved beside the take
    as <name>.scribe.json, so speech lengths and fragment cuts read it later without paying again."""
    s = s or _session()
    with (OUT / f"{name}.wav").open("rb") as f:
        r = s.post(f"{API}/v1/speech-to-text", data={"model_id": "scribe_v2"},
                   files={"file": (f"{Path(name).name}.wav", f, "audio/wav")}, timeout=180)
    if r.status_code != 200:
        sys.exit(f"speech-to-text failed {r.status_code}: {r.text[:300]}")
    j = r.json()
    (OUT / f"{name}.scribe.json").write_text(json.dumps({"text": j.get("text", ""), "words": j.get("words", [])},
                                                        ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    rate, data = wavfile.read(OUT / f"{name}.wav")
    _log({"kind": "stt", "name": name, "model": "scribe_v2", "seconds": round(len(data) / rate, 2)})
    return j.get("text", "")


def check(name: str, expected: str | None = None) -> bool:
    """Transcribes a take with Scribe and compares the words to the script (tags and punctuation ignored)."""
    heard = transcribe(name)
    if expected is None:
        for line in LEDGER.read_text(encoding="utf-8").splitlines()[::-1]:
            e = json.loads(line)
            if e.get("kind") == "tts" and e.get("name") == name.split("_take")[0]:
                expected = e["text"]
                break
    ok = expected is not None and script_words(heard) == script_words(expected)
    print(f"{'OK  ' if ok else 'DIFF'} {name}: heard {heard!r}" + ("" if ok else f" | script {expected!r}"))
    return ok


def sample(name: str) -> np.ndarray:
    """A kept take as float mono at the build's rate, for an event's renderer."""
    rate, data = wavfile.read(OUT / f"{name}.wav")
    x = data.astype(np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    x /= 32768.0
    if rate != SR:
        from scipy.signal import resample_poly
        x = resample_poly(x, SR, rate)
    return x


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("credits")
    p = sub.add_parser("sfx")
    p.add_argument("name"); p.add_argument("text")
    p.add_argument("--seconds", type=float); p.add_argument("--takes", type=int, default=1)
    p.add_argument("--loop", action="store_true"); p.add_argument("--influence", type=float, default=0.5)
    p.add_argument("--reserve", type=int, default=500)
    p = sub.add_parser("tts")
    p.add_argument("name"); p.add_argument("text"); p.add_argument("--voice", required=True)
    p.add_argument("--model", default="eleven_v4"); p.add_argument("--takes", type=int, default=1)
    p.add_argument("--stability", type=float); p.add_argument("--similarity", type=float)
    p.add_argument("--style", type=float); p.add_argument("--reserve", type=int, default=500)
    p.add_argument("--previous-text"); p.add_argument("--next-text"); p.add_argument("--seed", type=int)
    p = sub.add_parser("voices"); p.add_argument("--search")
    p = sub.add_parser("design"); p.add_argument("name"); p.add_argument("description"); p.add_argument("text")
    p.add_argument("--reserve", type=int, default=500); p.add_argument("--model", default="eleven_ttv_v3")
    p.add_argument("--mp3-dir"); p.add_argument("--numbered", action="store_true")
    p = sub.add_parser("check"); p.add_argument("names", nargs="+")
    p = sub.add_parser("keep"); p.add_argument("name"); p.add_argument("generated_voice_id")
    p.add_argument("label"); p.add_argument("description")
    a = ap.parse_args()
    if a.cmd == "credits":
        c = credits()
        spent = ledger_spend(tier=plan_of(c))
        print(f"API says {c['left']} of {c['limit']} left (tier {c['tier']}); our ledger counts "
              f"{spent:.0f} spent on this plan in 30 days, so about {c['limit'] - spent:.0f} left")
    elif a.cmd == "sfx":
        sfx(a.name, a.text, a.seconds, a.takes, a.loop, a.influence, a.reserve)
    elif a.cmd == "tts":
        tts(a.name, a.text, a.voice, a.model, a.takes, a.stability, a.similarity, a.style, a.reserve,
            a.previous_text, a.next_text, a.seed)
    elif a.cmd == "voices":
        voices(a.search)
    elif a.cmd == "design":
        design(a.name, a.description, a.text, a.reserve, a.model, a.mp3_dir, a.numbered)
    elif a.cmd == "check":
        results = [check(n) for n in a.names]
        sys.exit(0 if all(results) else 1)
    elif a.cmd == "keep":
        keep(a.name, a.generated_voice_id, a.label, a.description)


if __name__ == "__main__":
    main()
