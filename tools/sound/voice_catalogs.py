"""The boss voice catalogs the game reads (Lane A, task A5.1): src/main/resources/assets/cosmicbreach/boss_voice/<boss>.json,
made from the production handoff (tools/sound/voice_lines.json, written by voicebatch.py manifest) and the exported script
(tools/sound/eleven/voice_script.json, for the words of each relayed fragment). Each boss's own settings already in its
catalog stay (wait_seconds, caption_color, reference); its global gap comes from the handoff. Words are the captions:
ASCII, no dashes. Run from the repo root:

    python tools/sound/voice_catalogs.py            writes the four catalogs
    python tools/sound/voice_catalogs.py --check    exits 1 if the catalogs on disk differ from what it would write

Tests: python -m unittest discover -s tools/sound/tests -v
"""
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True

REPO = Path(__file__).resolve().parents[2]
HANDOFF = REPO / "tools" / "sound" / "voice_lines.json"
SCRIPT = REPO / "tools" / "sound" / "eleven" / "voice_script.json"
CATALOGS = REPO / "src" / "main" / "resources" / "assets" / "cosmicbreach" / "boss_voice"
BOSSES = ["colossus", "leviathan", "unsung", "heliarch"]
SETTINGS = ("wait_seconds", "caption_color", "reference")


def plain(text: str) -> str:
    """Words without their [audio tags], spaces tidied."""
    return re.sub(r"\s+", " ", re.sub(r"\[[^\]]*\]", " ", text)).strip()


def checked(text: str, where: str) -> str:
    if chr(0x2014) in text or chr(0x2013) in text:
        raise ValueError(f"{where}: a dash in {text!r}")
    if not text.isascii():
        raise ValueError(f"{where}: not ASCII: {text!r}")
    return text


def fragments(script_line: dict, variant: dict, where: str) -> list:
    """The variant's fragments with their words: masks and starts from the handoff, words from the script's Mask split."""
    split = (script_line or {}).get("split")
    marks = variant.get("fragments", [])
    if not marks:
        return []
    if not split or len(split["fragments"]) != len(marks):
        raise ValueError(f"{where}: {len(marks)} fragments in the take, the script splits it {0 if not split else len(split['fragments'])} ways")
    out = []
    for mark, frag in zip(marks, split["fragments"]):
        out.append({"mask": int(mark["mask"]), "start_ticks": int(mark["start_ticks"]),
                    "text": checked(plain(frag["text"]), where)})
    return out


def build(handoff: dict, script: dict, old: dict) -> dict:
    """The four catalogs, by boss, from the handoff, the script export and the catalogs as they stand (their settings)."""
    by_id = {(r["boss"], r["id"]): r for r in script["lines"]}
    out = {}
    for boss in BOSSES:
        if boss not in old:
            raise ValueError(f"no catalog for {boss} to take its settings from")
        cat = {"boss": boss, "global_gap_seconds": int(handoff["global_gap_s"][boss])}
        for key in SETTINGS:
            cat[key] = old[boss][key]
        lines = []
        for e in handoff["bosses"].get(boss, []):
            where = f"{boss}/{e['id']}"
            if not e["variants"]:
                raise ValueError(f"{where}: no take")
            variants = {}
            for key, v in e["variants"].items():
                take = {"event": v["event"], "subtitle": v["subtitle"], "length_ticks": int(v["length_ticks"]),
                        "speech_start_ticks": int(v["speech_start_ticks"]), "speech_ticks": int(v["speech_ticks"])}
                frags = fragments(by_id.get((boss, e["id"])), v, where)
                if frags:
                    joined = " ".join(f["text"] for f in frags)
                    if joined != e["words"]:
                        raise ValueError(f"{where}/{key}: the fragments say {joined!r}, the caption {e['words']!r}")
                    take["fragments"] = frags
                variants[key] = take
            lines.append({"id": e["id"], "trigger": e["trigger"], "condition": e["condition"], "priority": int(e["priority"]),
                          "repeat": e["repeat"], "words": checked(e["words"], where), "variants": variants})
        cat["lines"] = lines
        out[boss] = cat
    return out


def text(catalog: dict) -> str:
    return json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"


def main(argv: list) -> int:
    handoff = json.loads(HANDOFF.read_text(encoding="utf-8"))
    script = json.loads(SCRIPT.read_text(encoding="utf-8"))
    old = {b: json.loads((CATALOGS / f"{b}.json").read_text(encoding="utf-8")) for b in BOSSES}
    new = build(handoff, script, old)
    if "--check" in argv:
        stale = [b for b in BOSSES if (CATALOGS / f"{b}.json").read_text(encoding="utf-8") != text(new[b])]
        print("catalogs up to date" if not stale else f"stale catalogs: {', '.join(stale)}")
        return 1 if stale else 0
    for b in BOSSES:
        (CATALOGS / f"{b}.json").write_text(text(new[b]), encoding="utf-8", newline="\n")
    counts = ", ".join(f"{b} {len(new[b]['lines'])}" for b in BOSSES)
    print(f"boss voice catalogs written: {counts} lines")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
