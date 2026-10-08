"""The committed handoff (tools/sound/voice_lines.json) is complete and current.

Lane A reads it, with the script export (eleven/voice_script.json), to make the boss voice catalogs. These tests read only
committed files (no take, no network, no rebuild) and fail when the handoff has fallen behind what it was made from: a
re-export of the script, a rebuild of a line, a new tempo. The cure is always `python tools/sound/voicebatch.py manifest`,
run after the build (VP3 review N1: the handoff once carried the delivery text of a limit the export had since changed),
and then `integrate` for the sound events and captions that must say the same.

    python -m unittest discover -s tools/sound/tests -v
"""
import json
import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import soundfile as sf  # noqa: E402

import voicebatch as vb  # noqa: E402

SCRIPT_FIELDS = ("trigger", "condition", "priority", "repeat", "words", "delivery")
LONG_LEVIATHAN_LINES = ["kill", "moor_first", "open_again", "open_group", "open_solo"]   # VP3: they need more than a gap


class HandoffTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.handoff = json.loads(vb.HANDOFF.read_text(encoding="utf-8"))
        cls.script = json.loads(vb.SCRIPT.read_text(encoding="utf-8"))
        cls.rows = {(r["boss"], r["id"]): r for r in cls.script["lines"]}
        cls.entries = {(b, e["id"]): e for b, es in cls.handoff["bosses"].items() for e in es}
        cls.variants = [(b, e, key, v) for b, es in cls.handoff["bosses"].items() for e in es
                        for key, v in e["variants"].items()]

    @staticmethod
    def where(boss, entry, key):
        return f"{boss}/{entry['id']}/{key}"

    def test_it_names_its_source_and_the_gaps_between_lines(self):
        self.assertEqual(self.handoff["version"], 1)
        self.assertEqual(self.handoff["source"], self.script["source"])
        self.assertEqual(self.handoff["global_gap_s"], {"colossus": 25, "leviathan": 15, "unsung": 20, "heliarch": 12})
        self.assertEqual(self.handoff["global_gap_s"], vb.GAPS)

    def test_every_script_line_is_in_it_and_says_what_the_script_says_now(self):
        self.assertEqual(set(self.entries), set(self.rows), "a line in one and not the other")
        for key, row in self.rows.items():
            for field in SCRIPT_FIELDS:
                self.assertEqual(self.entries[key][field], row[field], f"{key[0]}/{key[1]} {field}: run manifest again")

    def test_every_line_has_the_variants_it_needs(self):
        for key, row in self.rows.items():
            split = row["split"]
            if row["boss"] == "unsung" and split["kind"] == "pair":
                want = {"12", "13", "23"}               # the two masks still singing after one breaks
            elif row["boss"] == "unsung" and split["kind"] == "alone":
                want = {"1", "2", "3"}                  # the last mask
            else:
                want = {"all"}
            self.assertEqual(set(self.entries[key]["variants"]), want, f"{key[0]}/{key[1]}")
        self.assertEqual(len(self.variants), 75)        # 71 lines: the Unsung's pair and alone lines have three each

    def test_every_variant_names_its_event_its_caption_and_its_duration(self):
        for boss, entry, key, v in self.variants:
            where = self.where(boss, entry, key)
            event = v["event"].split(":", 1)[1]
            self.assertTrue(v["event"].startswith("cosmicbreach:"), where)
            self.assertEqual(v["subtitle"], "subtitles.cosmicbreach." + event.replace("/", "."), where)
            stem = f"heliarch/voice_{entry['id']}" if boss == "heliarch" else f"bossvoice/{boss}_{entry['id']}"
            self.assertEqual(event, stem if key == "all" else f"{stem}_{key}", where)
            for field in ("length_ticks", "speech_start_ticks", "speech_ticks"):
                self.assertIsInstance(v[field], int, f"{where} {field}")
            self.assertTrue(0 <= v["speech_start_ticks"] < v["speech_ticks"] <= v["length_ticks"], where)
            self.assertIsInstance(v["margin_lu"], (int, float), where)
            self.assertTrue(all(d.startswith("cosmicbreach:music/") or d == vb.event_id(vb.CHORD) for d in v["duck"]), where)
            if not v["duck"]:                            # her choir has not started under an opener: nothing to duck
                self.assertEqual((boss, entry["trigger"]), ("unsung", "fight_start"), where)

    def test_the_lengths_are_the_audio_on_disk(self):
        """length_ticks is ceil(seconds x 20), the rule HeliarchLine.lengthTicks uses; a rebuilt line changes it."""
        sounds = vb.ASSETS / "cosmicbreach" / "sounds"
        for boss, entry, key, v in self.variants:
            where = self.where(boss, entry, key)
            ogg = sounds / (v["event"].split(":", 1)[1] + ".ogg")
            self.assertTrue(ogg.exists(), f"{where}: no file")
            self.assertEqual(v["length_ticks"], math.ceil(sf.info(str(ogg)).frames / 44100.0 * 20.0), where)

    def test_every_event_is_in_sounds_json_and_its_caption_in_the_lang(self):
        """`voicebatch.py integrate` must have run after the last manifest: the game finds each line's event in
        sounds.json, and its caption (the quoted words, the Unsung's the whole sentence) in the lang file."""
        read = lambda path: json.loads((vb.ASSETS / path).read_text(encoding="utf-8"))   # noqa: E731
        sounds = read("cosmicbreach/sounds.json")
        langs = {"heliarch": read("cosmicbreach_heliarch/lang/en_us.json"), "bossvoice": read("cosmicbreach_bossvoice/lang/en_us.json")}
        for boss, entry, key, v in self.variants:
            where = self.where(boss, entry, key)
            event = v["event"].split(":", 1)[1]
            self.assertEqual(sounds.get(event), {"sounds": [v["event"]], "subtitle": v["subtitle"]}, where)
            lang = langs["heliarch" if boss == "heliarch" else "bossvoice"]
            self.assertEqual(lang.get(v["subtitle"]), '"' + entry["words"] + '"', where)

    def test_every_leviathan_line_says_whether_it_is_long_and_the_window_it_needs(self):
        lev = [(entry, v) for boss, entry, key, v in self.variants if boss == "leviathan"]
        self.assertEqual(len(lev), 14)
        for entry, v in lev:
            where = f"leviathan/{entry['id']}"
            for field in ("long", "window_ticks", "file_window_ticks"):
                self.assertIn(field, v, where)
            self.assertEqual(v["window_ticks"], v["speech_ticks"] - v["speech_start_ticks"] + vb.GATE_TICKS, where)
            self.assertEqual(v["file_window_ticks"], v["speech_ticks"] + vb.GATE_TICKS, where)
            self.assertEqual(v["long"], v["window_ticks"] > vb.GAP_TICKS, where)
            if vb.QUIET.search(entry["delivery"]):       # made for the attack gaps: it must fit one
                self.assertFalse(v["long"], where)
        self.assertEqual(sorted(entry["id"] for entry, v in lev if v["long"]), LONG_LEVIATHAN_LINES)

    def test_no_other_boss_carries_the_leviathans_flag_and_windows(self):
        for boss, entry, key, v in self.variants:
            if boss != "leviathan":
                for field in ("long", "window_ticks", "file_window_ticks"):
                    self.assertNotIn(field, v, self.where(boss, entry, key))

    def test_a_sped_up_line_says_so_and_no_other_does(self):
        tempo = json.loads(vb.TEMPO.read_text(encoding="utf-8"))
        want = {name: rec["tempo"] for name, rec in tempo.items()}
        got = {f"{boss}/{entry['id']}": v["tempo"] for boss, entry, key, v in self.variants if "tempo" in v}
        self.assertEqual(got, want)
        self.assertTrue(all(1.0 < t <= 1.15 for t in got.values()))

    def test_the_unsungs_lines_carry_each_fragments_mask_and_start(self):
        for boss, entry, key, v in self.variants:
            where = self.where(boss, entry, key)
            if boss != "unsung":
                self.assertNotIn("fragments", v, where)
                continue
            split = self.rows[(boss, entry["id"])]["split"]
            if split["kind"] == "relay":
                want = [int(f["tag"][1]) for f in split["fragments"]]
            else:
                want = [int(c) for c in key]            # the living masks, in song order
            frags = v["fragments"]
            self.assertEqual([f["mask"] for f in frags], want, where)
            starts = [f["start_ticks"] for f in frags]
            self.assertEqual(starts, sorted(starts), where)
            self.assertTrue(all(isinstance(s, int) and 0 <= s < v["speech_ticks"] for s in starts), where)


if __name__ == "__main__":
    unittest.main()
