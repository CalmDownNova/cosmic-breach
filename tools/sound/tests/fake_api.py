"""An offline stand-in for the ElevenLabs API, for the sound tool's tests: no network, no credits.

`FakeApi` is the session eleven._session() hands out inside `sandbox()`. It answers the endpoints eleven.py calls (the
subscription, speech, Voice Design, saving a voice, Scribe) from canned data, records every request, and moves the
balance by what each kind of call is set to charge. Audio answers are real MP3s that ffmpeg makes from harmonic tones,
so the decode path runs as it does for a real take. `sandbox()` points eleven.py (and voicebatch.py, once imported) at
a temporary folder, so a test never touches tools/sound/eleven/ or the Media folder.
"""
from __future__ import annotations

import base64
import json
import shutil
import subprocess
import sys
import tempfile
import time
from contextlib import contextmanager
from pathlib import Path

import numpy as np
from scipy.io import wavfile

SR = 44100
API = "https://api.elevenlabs.io"


def tone(f0: float, seconds: float, seed: int = 0) -> np.ndarray:
    """A voiced stand-in: a harmonic tone at `f0` with a breath of noise from `seed` (two seeds never match)."""
    t = np.arange(int(seconds * SR)) / SR
    x = sum((0.6 ** k) * np.sin(2 * np.pi * f0 * (k + 1) * t) for k in range(8))
    x = 0.3 * x / np.max(np.abs(x)) + 0.003 * np.random.default_rng(seed).standard_normal(len(t))
    return x * np.minimum(1.0, np.minimum(t, t[-1] - t) / 0.02)


def mp3(x: np.ndarray) -> bytes:
    """`x` as the MP3 bytes the API would send (libmp3lame, 128 kb/s)."""
    with tempfile.TemporaryDirectory() as d:
        wav, out = Path(d) / "x.wav", Path(d) / "x.mp3"
        wavfile.write(wav, SR, (np.clip(x, -1.0, 1.0) * 32767.0).astype(np.int16))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-codec:a", "libmp3lame", "-b:a", "128k",
                        str(out)], check=True)
        return out.read_bytes()


def write_wav(path: Path, x: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    wavfile.write(path, SR, (np.clip(x, -1.0, 1.0) * 32767.0).astype(np.int16))


def words_for(text: str, start: float, step: float) -> list:
    """Scribe's word list for `text` said one word every `step` seconds from `start`, with its spacing entries."""
    out = []
    for i, w in enumerate(text.split()):
        at = start + i * step
        if i:
            out.append({"text": " ", "type": "spacing", "start": round(at - 0.05, 3), "end": round(at, 3)})
        out.append({"text": w, "type": "word", "start": round(at, 3), "end": round(at + 0.8 * step, 3)})
    return out


class Response:
    def __init__(self, status: int = 200, payload=None, content: bytes = b"", headers=None):
        self.status_code, self._payload, self.content = status, payload, content
        self.headers = headers or {}
        self.text = json.dumps(payload) if payload is not None else ""

    def json(self):
        return self._payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")


class FakeApi:
    """A Starter account with `used` of `limit` credits spent. `charges` is what each kind of call really costs (the
    balance moves by it); `previews` the (pitch, seconds) of each Voice Design preview; `stt` Scribe's answer per
    uploaded file name, as (text, words). Speech: `deterministic` says whether one seed repeats a take exactly, and
    `context` what the model does with previous_text and next_text ("honoured", "ignored", "refused" or "spoken")."""

    def __init__(self, limit: int = 39455, used: int = 830, tier: str = "starter"):
        self.limit, self.used, self.tier = limit, used, tier
        self.charges = {"tts": 0, "design": 0, "stt": 0}
        self.previews = [(95.0, 6.0), (150.0, 6.0), (60.0, 6.0)]
        self.stt = {}
        self.deterministic, self.context = True, "honoured"
        self.posts, self.designs = [], []
        self.balance_fails_after_design = False      # the balance read right after a paid design answers 503
        self.account = {}                            # voice id: {"name", "category"}, the account's saved voices
        self.deleted = []

    def delete(self, url, timeout=None):
        voice_id = url.rsplit("/", 1)[1]
        if not url.startswith(f"{API}/v1/voices/") or voice_id not in self.account:
            return Response(404, {"detail": "voice not found"})
        self.deleted.append(voice_id)
        del self.account[voice_id]
        return Response(payload={"status": "ok"})

    def get(self, url, params=None, timeout=None):
        if url.startswith(f"{API}/v1/voices/"):
            voice_id = url.rsplit("/", 1)[1]
            if voice_id not in self.account:
                return Response(404, {"detail": "voice not found"})
            return Response(payload={"voice_id": voice_id, **self.account[voice_id]})
        if url == f"{API}/v1/user/subscription":
            if self.balance_fails_after_design and self.designs:
                return Response(503, {"detail": "service unavailable"})
            return Response(payload={"character_count": self.used, "character_limit": self.limit, "tier": self.tier,
                                     "next_character_count_reset_unix": 1793370000})
        raise AssertionError(f"unexpected GET {url}")

    def post(self, url, params=None, json=None, data=None, files=None, timeout=None):
        path = url[len(API):]
        self.posts.append({"path": path, "params": params, "json": json, "data": data,
                           "file": files["file"][0] if files else None})
        if path.startswith("/v1/text-to-speech/"):
            return self._speech(json)
        if path == "/v1/text-to-voice/design":
            previews = [{"audio_base_64": base64.b64encode(mp3(tone(f0, seconds, i))).decode("ascii"),
                         "generated_voice_id": f"gen{len(self.designs)}{'abcdefgh'[i]}", "media_type": "audio/mpeg",
                         "duration_secs": seconds, "language": "en"} for i, (f0, seconds) in enumerate(self.previews)]
            self.designs.append(previews)
            self.used += self.charges["design"]
            return Response(payload={"previews": previews, "text": json["text"]})
        if path == "/v1/text-to-voice":
            return Response(payload={"voice_id": f"voice_{json['generated_voice_id']}"})
        if path == "/v1/speech-to-text":
            text, words = self.stt.get(files["file"][0], ("", []))
            self.used += self.charges["stt"]
            return Response(payload={"text": text, "words": words})
        raise AssertionError(f"unexpected POST {path}")

    def _speech(self, body: dict) -> Response:
        context = bool(body.get("previous_text") or body.get("next_text"))
        if context and self.context == "refused":
            return Response(400, {"detail": "previous_text and next_text are not supported by this model"})
        if context and self.context == "unauthorised":                 # a failure that says nothing about context
            return Response(401, {"detail": {"status": "invalid_api_key", "message": "Invalid API key"}})
        seed = body.get("seed", 0) if self.deterministic else 1000 + len(self.posts)
        said = body["text"]
        if context and self.context == "spoken":
            said = f"{body.get('previous_text') or ''} {said} {body.get('next_text') or ''}"
        f0 = 150.0 + (12.0 if context and self.context == "honoured" else 0.0)
        if not self.deterministic:                 # a fresh performance every time: its pitch wanders too
            f0 *= 1.0 + 0.02 * (len(self.posts) % 5)
        self.used += self.charges["tts"]
        return Response(content=mp3(tone(f0, 0.3 + 0.05 * len(said), seed)), headers={"request-id": f"r{len(self.posts)}"})


def paid_spend(tmp: Path, characters: int) -> None:
    """A paid speech entry already in the sandbox's ledger, so our own count is under the API's balance (as it is in
    the repo: the API has charged about a fifth of what the ledger counts)."""
    entry = {"kind": "tts", "name": "earlier/line", "takes": 1, "text": "x" * characters, "tier": "paid",
             "plan": "starter", "time": time.strftime("%Y-%m-%d %H:%M:%S")}
    path = tmp / "eleven" / "ledger.jsonl"
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(entry) + "\n")


def ledger(tmp: Path) -> list:
    path = tmp / "eleven" / "ledger.jsonl"
    if not path.exists():
        return []
    return [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]


@contextmanager
def sandbox(api: FakeApi):
    """eleven.py (and voicebatch.py, if it is imported) working in a fresh temporary folder and talking to `api`."""
    import eleven
    tmp = Path(tempfile.mkdtemp())
    out = tmp / "eleven"
    out.mkdir()
    saved = {n: getattr(eleven, n) for n in ("HERE", "OUT", "LEDGER", "VOICES", "_session")}
    eleven.HERE, eleven.OUT, eleven.LEDGER, eleven.VOICES = tmp, out, out / "ledger.jsonl", out / "voices.json"
    eleven._session = lambda: api
    vb = sys.modules.get("voicebatch")
    vb_saved = {}
    if vb is not None:
        names = ("OUT", "SCRIPT", "PICKS", "CHECKS", "TAKES", "PROBE", "STITCH", "REFUSED", "TEMPO", "MEDIA")
        vb_saved = {n: getattr(vb, n) for n in names}
        vb.OUT, vb.MEDIA = out, tmp / "Media"
        for n in names[1:-1]:
            setattr(vb, n, out / vb_saved[n].name)
    bv = sys.modules.get("bossvoice")
    bv_saved = {n: getattr(bv, n) for n in ("SCRIPT", "STITCH", "TEMPO")} if bv is not None else {}
    if bv is not None:
        bv.SCRIPT, bv.STITCH, bv.TEMPO = out / "voice_script.json", out / "voice_stitch.json", out / "voice_tempo.json"
        bv.unsung_parts.cache_clear()
        bv.leviathan_parts.cache_clear()
    try:
        yield tmp
    finally:
        for n, v in saved.items():
            setattr(eleven, n, v)
        for n, v in vb_saved.items():
            setattr(vb, n, v)
        for n, v in bv_saved.items():
            setattr(bv, n, v)
        if bv is not None:
            bv.unsung_parts.cache_clear()
            bv.leviathan_parts.cache_clear()
        shutil.rmtree(tmp, ignore_errors=True)
