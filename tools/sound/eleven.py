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
    # 429 means the request was not run, so retrying it (POSTs included) never bills twice
    s.mount("https://", HTTPAdapter(max_retries=Retry(total=6, status_forcelist=[429], backoff_factor=2,
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


def _left_after(c: dict, spent: float) -> int:
    return int(c["limit"] - ledger_spend(tier=plan_of(c)) - spent)


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
    after = _left_after(c, per * takes)
    _log({"kind": "sfx", "name": name, "takes": takes, "text": text, "seconds": seconds, "loop": loop,
          "influence": influence, "model": "eleven_text_to_sound_v2", "format": FORMAT,
          "credits": before - after, "left": after, "request_ids": ids}, c)
    print(f"{takes} take(s) of {name}: {before - after} credits, {after} left")
    for p in paths:
        print("  ", p.relative_to(HERE))
    return paths


def tts(name: str, text: str, voice: str, model: str = "eleven_v4", takes: int = 1, stability: float | None = None,
        similarity: float | None = None, style: float | None = None, reserve: int = 500) -> list:
    s = _session()
    before, c = _guard(s, len(text) * takes, reserve)
    paths, ids = [], []
    for stem in _take_names(name, takes):
        body = {"text": text, "model_id": model}
        settings = {k: v for k, v in (("stability", stability), ("similarity_boost", similarity), ("style", style))
                    if v is not None}
        if settings:
            body["voice_settings"] = settings
        r = s.post(f"{API}/v1/text-to-speech/{voice}", params={"output_format": FORMAT}, json=body, timeout=180)
        if r.status_code != 200:
            sys.exit(f"text-to-speech failed {r.status_code}: {r.text[:300]}")
        ids.append(r.headers.get("request-id"))
        paths.append(_save_mp3_as_wav(r.content, stem))
    after = _left_after(c, len(text) * takes)
    _log({"kind": "tts", "name": name, "takes": takes, "text": text, "voice": voice, "model": model,
          "settings": {"stability": stability, "similarity": similarity, "style": style}, "format": FORMAT,
          "credits": before - after, "left": after, "request_ids": ids}, c)
    print(f"{takes} take(s) of {name}: {before - after} credits for {len(text)} characters each, {after} left")
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


def design(name: str, description: str, text: str, reserve: int = 500, model: str = "eleven_ttv_v3") -> None:
    """Voice Design: three previews of a described voice, saved as WAVs; keep one with `keep`."""
    s = _session()
    before, c = _guard(s, len(text) * 3, reserve)
    if not 100 <= len(text) <= 1000:
        sys.exit(f"Voice design needs a preview text of 100 to 1000 characters, got {len(text)}")
    r = s.post(f"{API}/v1/text-to-voice/design",
               json={"voice_description": description, "text": text, "model_id": model},
               params={"output_format": FORMAT}, timeout=240)
    if r.status_code != 200:
        sys.exit(f"voice design failed {r.status_code}: {r.text[:300]}")
    previews = r.json().get("previews", [])
    for i, p in enumerate(previews):
        audio = base64.b64decode(p["audio_base_64"])
        stem = f"voices/{name}_preview{i + 1}"
        if "mpeg" in (p.get("media_type") or "") or audio[:3] == b"ID3":
            path = _save_mp3_as_wav(audio, stem)
        else:
            path = _save_pcm(audio, stem)
        print(f"preview {i + 1}: {p['generated_voice_id']}  {p.get('duration_secs', 0):.1f} s  {path.relative_to(HERE)}")
    after = credits(s)["left"]
    _log({"kind": "design", "name": name, "description": description, "text": text,
          "previews": [p["generated_voice_id"] for p in previews], "credits": before - after, "left": after}, c)
    print(f"{before - after} credits, {after} left")


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


def script_words(t: str | None) -> list:
    """A line's words for comparison: [tags], (audio events), punctuation and case dropped."""
    import re
    t = re.sub(r"\([^)]*\)", " ", re.sub(r"\[[^\]]*\]", " ", (t or "").lower()))
    return re.sub(r"[^a-z' ]", " ", t.replace("\u2019", "'")).split()


def transcribe(name: str, s=None) -> str:
    """Scribe's transcript of a kept take."""
    s = s or _session()
    with (OUT / f"{name}.wav").open("rb") as f:
        r = s.post(f"{API}/v1/speech-to-text", data={"model_id": "scribe_v2"},
                   files={"file": (f"{Path(name).name}.wav", f, "audio/wav")}, timeout=180)
    if r.status_code != 200:
        sys.exit(f"speech-to-text failed {r.status_code}: {r.text[:300]}")
    return r.json().get("text", "")


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
    p = sub.add_parser("voices"); p.add_argument("--search")
    p = sub.add_parser("design"); p.add_argument("name"); p.add_argument("description"); p.add_argument("text")
    p.add_argument("--reserve", type=int, default=500); p.add_argument("--model", default="eleven_ttv_v3")
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
        tts(a.name, a.text, a.voice, a.model, a.takes, a.stability, a.similarity, a.style, a.reserve)
    elif a.cmd == "voices":
        voices(a.search)
    elif a.cmd == "design":
        design(a.name, a.description, a.text, a.reserve, a.model)
    elif a.cmd == "check":
        results = [check(n) for n in a.names]
        sys.exit(0 if all(results) else 1)
    elif a.cmd == "keep":
        keep(a.name, a.generated_voice_id, a.label, a.description)


if __name__ == "__main__":
    main()
