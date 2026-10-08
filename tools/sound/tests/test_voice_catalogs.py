"""Offline tests for voice_catalogs.py (no takes, no network)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import voice_catalogs as vc  # noqa: E402

SETTINGS = {"wait_seconds": 8, "caption_color": "#E8E2F4", "reference": {"weapon_tier": 3}}
OLD = {b: dict(SETTINGS, boss=b, global_gap_seconds=1, lines=[]) for b in vc.BOSSES}


def take(event, length=40, frags=None):
    v = {"event": f"cosmicbreach:{event}", "subtitle": "subtitles.cosmicbreach." + event.replace("/", "."), "length_ticks": length,
         "speech_start_ticks": 2, "speech_ticks": length - 10, "over_music_lu": 6.0}
    if frags:
        v["fragments"] = [{"mask": m, "start_ticks": s} for m, s in frags]
    return v


HANDOFF = {
    "version": 1, "global_gap_s": {"colossus": 25, "leviathan": 15, "unsung": 20, "heliarch": 12},
    "bosses": {
        "colossus": [{"id": "fists", "trigger": "weapon:bare_hands", "condition": "none", "priority": 45, "repeat": "once per fight",
                      "words": "Fists? ...Bold.", "delivery": "Dry.", "variants": {"all": take("bossvoice/colossus_fists")}}],
        "unsung": [{"id": "one_gone", "trigger": "hp_threshold:67", "condition": "none", "priority": 95, "repeat": "once per fight",
                    "words": "Who will sing its part now?", "delivery": "Shaken.",
                    "variants": {"12": take("bossvoice/unsung_one_gone_12", 106, [(1, 2), (2, 38)]),
                                 "23": take("bossvoice/unsung_one_gone_23", 104, [(2, 2), (3, 37)])}}],
    },
}
SCRIPT = {"lines": [
    {"boss": "colossus", "id": "fists", "split": None},
    {"boss": "unsung", "id": "one_gone", "split": {"kind": "pair", "fragments": [
        {"tag": "first", "text": "[shaken] Who will sing"}, {"tag": "second", "text": "its part now?"}]}},
]}


class BuildTest(unittest.TestCase):
    def test_settings_stay_and_the_gap_comes_from_the_handoff(self):
        out = vc.build(HANDOFF, SCRIPT, OLD)
        self.assertEqual(out["colossus"]["global_gap_seconds"], 25)
        self.assertEqual(out["unsung"]["wait_seconds"], 8)
        self.assertEqual(out["heliarch"]["lines"], [])

    def test_lines_keep_their_order_triggers_and_takes(self):
        line = vc.build(HANDOFF, SCRIPT, OLD)["colossus"]["lines"][0]
        self.assertEqual((line["id"], line["trigger"], line["priority"], line["repeat"]), ("fists", "weapon:bare_hands", 45, "once per fight"))
        self.assertEqual(line["variants"]["all"]["length_ticks"], 40)
        self.assertNotIn("over_music_lu", line["variants"]["all"])
        self.assertNotIn("fragments", line["variants"]["all"])

    def test_relayed_fragments_get_their_words_without_tags(self):
        v = vc.build(HANDOFF, SCRIPT, OLD)["unsung"]["lines"][0]["variants"]["23"]
        self.assertEqual(v["fragments"], [{"mask": 2, "start_ticks": 2, "text": "Who will sing"},
                                          {"mask": 3, "start_ticks": 37, "text": "its part now?"}])

    def test_a_dash_or_a_fragment_count_mismatch_fails(self):
        bad = {**HANDOFF, "bosses": {"colossus": [dict(HANDOFF["bosses"]["colossus"][0], words="Fists" + chr(0x2014) + "bold.")]}}
        with self.assertRaises(ValueError):
            vc.build(bad, SCRIPT, OLD)
        short = {"lines": [{"boss": "unsung", "id": "one_gone", "split": {"kind": "alone", "fragments": [{"tag": "alone", "text": "x"}]}}]}
        with self.assertRaises(ValueError):
            vc.build(HANDOFF, short, OLD)

    def test_fragments_must_make_the_caption(self):
        wrong = {**HANDOFF, "bosses": {"unsung": [dict(HANDOFF["bosses"]["unsung"][0], words="Who will sing it now?")]}}
        with self.assertRaises(ValueError):
            vc.build(wrong, SCRIPT, OLD)
        vc.build(HANDOFF, SCRIPT, OLD)

    def test_plain_strips_tags(self):
        self.assertEqual(vc.plain("[slowly, deep] A climber. ...Come.  [x] Learn."), "A climber. ...Come. Learn.")


if __name__ == "__main__":
    unittest.main()
