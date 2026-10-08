"""Tests for voicebatch.py's casting commands (rank, keep, use and the context probe), the steps that spend credits
before any line is made, run against the offline fake of the API (tests/fake_api.py): no network, no credits, and
nothing written outside a temporary folder.

    python -m unittest discover -s tools/sound/tests -v
"""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import eleven  # noqa: E402
import voicebatch as vb  # noqa: E402
from fake_api import FakeApi, ledger, sandbox, words_for  # noqa: E402

SENTENCE = ("The river runs past the old mill, and every evening the bell in the tower rings once for each of the "
            "long grey hours.")
DESCRIPTION = "A very old man with a deep, slow, gravelly voice. Studio quality recording, no background noise."


def designed(api: FakeApi, tmp: Path, name: str = "colossus", heard: dict | None = None) -> Path:
    """Voice Design through the fake; Scribe hears the sentence (or `heard[n]` for preview n) at 2 words a second."""
    for i in range(1, len(api.previews) + 1):
        text = (heard or {}).get(i, SENTENCE)
        api.stt[f"{name}_preview{i}.wav"] = (text, words_for(text, 0.2, 0.5))
    folder = tmp / "candidates"
    eleven.design(name, DESCRIPTION, SENTENCE, mp3_dir=str(folder))
    return folder


def candidates(folder: Path, name: str) -> dict:
    entry = json.loads(eleven.index_path(folder).read_text(encoding="utf-8"))[name]
    return {c["candidate"]: c for c in entry["candidates"]}


class WordErrorsTest(unittest.TestCase):
    def test_counts_words_swapped_dropped_and_added_ignoring_tags_and_punctuation(self):
        self.assertEqual(vb.word_errors("The river runs past.", "[slowly] The river runs past"), 0)
        self.assertEqual(vb.word_errors("The river ran past", "The river runs past"), 1)
        self.assertEqual(vb.word_errors("The river past", "The river runs past"), 1)
        self.assertEqual(vb.word_errors("The old river runs past", "The river runs past"), 1)


class RankTest(unittest.TestCase):
    def test_ranks_the_preview_inside_the_briefs_pitch_band_first(self):
        api = FakeApi()
        api.previews = [(95.0, 3.0), (150.0, 3.0), (64.0, 3.0)]      # in the band, far above it, a little under it
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.rank(str(folder), "colossus")
            ranked = candidates(folder, "colossus")
            self.assertEqual([ranked[k]["rank"] for k in ("colossus_a", "colossus_b", "colossus_c")], [1, 3, 2])
            a = ranked["colossus_a"]["metrics"]
            self.assertAlmostEqual(a["f0"], 95.0, delta=2.0)
            self.assertAlmostEqual(a["words_per_s"], 24 / 11.9, delta=0.01)
            self.assertTrue(a["words_match"])

    def test_measures_loudness_and_counts_the_words_scribe_heard_differently(self):
        api = FakeApi()
        api.previews = [(95.0, 3.0), (95.0, 3.0), (95.0, 3.0)]
        with sandbox(api) as tmp:
            folder = designed(api, tmp, heard={2: SENTENCE.replace("mill", "hill")})
            vb.rank(str(folder), "colossus")
            m = {k: c["metrics"] for k, c in candidates(folder, "colossus").items()}
            self.assertEqual((m["colossus_a"]["word_errors"], m["colossus_b"]["word_errors"]), (0, 1))
            self.assertFalse(m["colossus_b"]["words_match"])
            self.assertTrue(-30.0 < m["colossus_a"]["lufs"] < -5.0)
            self.assertLess(m["colossus_a"]["true_peak_db"], 0.0)

    def test_ranking_again_reuses_the_transcripts(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.rank(str(folder), "colossus")
            vb.rank(str(folder), "colossus")
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts), 3)

    def test_a_rerun_is_ranked_against_its_roles_brief(self):
        api = FakeApi()
        api.previews = [(95.0, 3.0), (150.0, 3.0), (64.0, 3.0)]
        with sandbox(api) as tmp:
            folder = designed(api, tmp, name="colossus2")
            vb.rank(str(folder), "colossus2", role="colossus")
            self.assertEqual(candidates(folder, "colossus2")["colossus2_a"]["rank"], 1)


class KeepAndUseTest(unittest.TestCase):
    def test_keeps_every_candidate_once_under_its_own_letter(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.keep(str(folder), "colossus", "CB Colossus", None)
            vb.keep(str(folder), "colossus", "CB Colossus", None)
            voices = json.loads(eleven.VOICES.read_text(encoding="utf-8"))
            self.assertEqual({k: v["label"] for k, v in voices.items()},
                             {"colossus_a": "CB Colossus A", "colossus_b": "CB Colossus B", "colossus_c": "CB Colossus C"})
            self.assertEqual(sum(p["path"] == "/v1/text-to-voice" for p in api.posts), 3)
            self.assertEqual([e["kind"] for e in ledger(tmp)].count("keep"), 3)

    def test_a_kept_voice_carries_a_neutral_description_not_its_brief(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.keep(str(folder), "colossus", "CB Colossus", "colossus_a")
            sent = [p["json"] for p in api.posts if p["path"] == "/v1/text-to-voice"]
            self.assertEqual(len(sent), 1)
            self.assertNotIn("gravelly", sent[0]["voice_description"])
            self.assertGreaterEqual(len(sent[0]["voice_description"]), 20)
            self.assertNotIn("gravelly", eleven.VOICES.read_text(encoding="utf-8"))

    def test_keeps_only_the_one_asked_for(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.keep(str(folder), "colossus", "CB Colossus", "colossus_b")
            self.assertEqual(list(json.loads(eleven.VOICES.read_text(encoding="utf-8"))), ["colossus_b"])

    def test_gives_a_role_its_kept_voice_with_the_previews_pitch_as_reference(self):
        api = FakeApi()
        api.previews = [(95.0, 3.0), (150.0, 3.0), (64.0, 3.0)]
        with sandbox(api) as tmp:
            folder = designed(api, tmp)
            vb.rank(str(folder), "colossus")
            vb.keep(str(folder), "colossus", "CB Colossus", "colossus_a")
            vb.use("colossus", "colossus_a", "rank", str(folder))
            role = json.loads(vb.PICKS.read_text(encoding="utf-8"))["roles"]["colossus"]
            self.assertEqual((role["voice_id"], role["voices_key"], role["picked_by"]), ("voice_gen0a", "colossus_a", "rank"))
            self.assertAlmostEqual(role["ref_f0"], 95.0, delta=2.0)
            self.assertEqual(vb.voice_of("colossus"), "voice_gen0a")


class RebaseTest(unittest.TestCase):
    """A role's reference pitch comes from the production model's own takes, not the design model's preview."""

    def test_rebases_a_role_on_its_voiced_takes_and_keeps_the_preview_value(self):
        from fake_api import tone, write_wav
        with sandbox(FakeApi()) as tmp:
            vb._save(vb.PICKS, {"roles": {"unsung_m1": {"voice_id": "alto", "ref_f0": 168.3, "picked_by": "rank"}}})
            voiced, whispered = tmp / "plain.wav", tmp / "hushed.wav"
            write_wav(voiced, tone(219.0, 1.5))
            write_wav(whispered, 0.05 * __import__("numpy").random.default_rng(1).standard_normal(66150))
            vb.rebase("unsung_m1", [str(voiced), str(whispered)], "test lines")
            role = json.loads(vb.PICKS.read_text(encoding="utf-8"))["roles"]["unsung_m1"]
            self.assertAlmostEqual(role["ref_f0"], 219.0, delta=3.0)
            self.assertEqual((role["preview_f0"], role["voice_id"], role["picked_by"]), (168.3, "alto", "rank"))
            self.assertEqual(role["ref_source"]["used"], [str(voiced)])
            self.assertEqual(role["ref_source"]["model"], "eleven_v4")

    def test_records_take_names_not_paths_that_move(self):
        """VP2 review M7: pick shelves the takes rebase measured, so their repo paths stop existing; names last."""
        from fake_api import tone, write_wav
        with sandbox(FakeApi()):
            vb._save(vb.PICKS, {"roles": {"unsung_m1": {"voice_id": "alto", "ref_f0": 168.3}}})
            take = eleven.OUT / "bossvoice" / "unsung" / "robes" / "f1_m1_take1.wav"
            write_wav(take, tone(219.0, 1.5))
            vb.rebase("unsung_m1", [str(take)], "production takes")
            source = json.loads(vb.PICKS.read_text(encoding="utf-8"))["roles"]["unsung_m1"]["ref_source"]
            self.assertEqual((source["takes"], source["used"]), (["bossvoice/unsung/robes/f1_m1_take1"],) * 2)

    def test_refuses_when_no_take_has_a_pitch(self):
        from fake_api import write_wav
        with sandbox(FakeApi()) as tmp:
            vb._save(vb.PICKS, {"roles": {"unsung_m1": {"voice_id": "alto", "ref_f0": 168.3}}})
            whispered = tmp / "hushed.wav"
            write_wav(whispered, 0.05 * __import__("numpy").random.default_rng(1).standard_normal(66150))
            with self.assertRaises(SystemExit):
                vb.rebase("unsung_m1", [str(whispered)], "test lines")
            self.assertEqual(json.loads(vb.PICKS.read_text(encoding="utf-8"))["roles"]["unsung_m1"]["ref_f0"], 168.3)


class RetireTest(unittest.TestCase):
    """Deleting a saved voice cannot be undone: retire checks, for each voice, that it is a designed voice the account
    holds under our label, that no role plays it and that it never made a take, before it deletes anything."""

    def account(self, api, tmp):
        voices = {"colossus_a": {"voice_id": "va", "label": "CB Colossus A"},
                  "colossus_b": {"voice_id": "vb", "label": "CB Colossus B"},
                  "leviathan_a": {"voice_id": "vl", "label": "CB Leviathan A"}}
        eleven.VOICES.write_text(json.dumps(voices), encoding="utf-8")
        api.account = {"va": {"name": "CB Colossus A", "category": "generated"},
                       "vb": {"name": "CB Colossus B", "category": "generated"},
                       "vl": {"name": "CB Leviathan A", "category": "generated"},
                       "george": {"name": "George", "category": "premade"}}
        vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "va"}}})

    def test_deletes_an_unused_candidate_logs_it_and_marks_it(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)
            vb.retire(["colossus_b"])
            self.assertEqual(api.deleted, ["vb"])
            self.assertIn("deleted_utc", json.loads(eleven.VOICES.read_text(encoding="utf-8"))["colossus_b"])
            self.assertEqual([(e["kind"], e["name"], e["voice_id"]) for e in ledger(tmp) if e["kind"] == "delete"],
                             [("delete", "colossus_b", "vb")])

    def test_a_deletion_is_on_record_before_anything_else_can_fail(self):
        """VP2 review M6: the deletion cannot be undone, so it is logged as soon as the API confirms it, before
        voices.json is touched."""
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)

            class Stuck(type(eleven.VOICES)):
                def write_text(self, *args, **kwargs):
                    raise OSError("disk full")

            eleven.VOICES = Stuck(eleven.VOICES)
            with self.assertRaises(OSError):
                eleven.delete_voice("colossus_b", "vb")
            self.assertEqual(api.deleted, ["vb"])
            self.assertEqual([(e["kind"], e["voice_id"]) for e in ledger(tmp)][-1], ("delete", "vb"))

    def test_refuses_a_voice_a_role_plays(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)
            with self.assertRaises(SystemExit):
                vb.retire(["colossus_a"])
            self.assertEqual(api.deleted, [])

    def test_refuses_a_voice_that_made_a_take(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)
            with eleven.LEDGER.open("a", encoding="utf-8") as f:
                f.write(json.dumps({"kind": "tts", "name": "x/y_take1", "text": "x", "voice": "vl"}) + "\n")
            with self.assertRaises(SystemExit):
                vb.retire(["leviathan_a"])
            self.assertEqual(api.deleted, [])

    def test_refuses_a_voice_the_account_holds_under_another_name_or_as_premade(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)
            api.account["vb"]["name"] = "Someone Else"
            with self.assertRaises(SystemExit):
                vb.retire(["colossus_b"])
            api.account["vb"] = {"name": "CB Colossus B", "category": "premade"}
            with self.assertRaises(SystemExit):
                vb.retire(["colossus_b"])
            self.assertEqual(api.deleted, [])

    def test_checks_every_voice_before_deleting_any(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            self.account(api, tmp)
            with self.assertRaises(SystemExit):
                vb.retire(["colossus_b", "colossus_a"])        # the second fails its checks: nothing is deleted
            self.assertEqual(api.deleted, [])


class ProbeTest(unittest.TestCase):
    def probe(self, deterministic: bool = True, context: str = "honoured", heard_context: str = "To the choir."):
        """Runs the probe on the fake; returns its verdict file, the takes moved to Media and the logged speech."""
        api = FakeApi()
        api.deterministic, api.context = deterministic, context
        for n in ("seed_a", "seed_b"):
            api.stt[f"{n}.wav"] = ("To the choir.", words_for("To the choir.", 0.1, 0.25))
        api.stt["context.wav"] = (heard_context, words_for(heard_context, 0.1, 0.25))
        with sandbox(api) as tmp:
            vb._save(vb.PICKS, {"roles": {"unsung_m2": {"voice_id": "tenor", "ref_f0": 150.0}}})
            vb.probe()
            result = json.loads(vb.PROBE.read_text(encoding="utf-8"))
            self.assertEqual(result["label"], "run1")
            moved = sorted(p.name for p in (tmp / "Media" / "takes" / "bossvoice" / "probe" / "run1").glob("*.wav"))
            left = sorted(p.name for p in (eleven.OUT / "bossvoice" / "probe").rglob("*.wav"))
            speech = [e for e in ledger(tmp) if e["kind"] == "tts"]
        self.assertEqual(left, [])
        return result, moved, speech

    def test_a_repeatable_seed_shows_the_context_changing_the_take(self):
        result, moved, speech = self.probe()
        self.assertEqual((result["verdict"], result["seed_deterministic"], result["accepted"]), ("honoured", True, True))
        self.assertEqual(moved, ["context.wav", "seed_a.wav", "seed_b.wav"])
        self.assertEqual([e["seed"] for e in speech], [4242, 4242, 4242])
        self.assertEqual([e["previous_text"] for e in speech], [None, None, "Who comes"])
        self.assertTrue(all(t["words_ok"] for t in result["takes"].values()))

    def test_context_that_changes_nothing_is_ignored(self):
        self.assertEqual(self.probe(context="ignored")[0]["verdict"], "ignored")

    def test_a_seed_that_does_not_repeat_leaves_it_unknown(self):
        result = self.probe(deterministic=False)[0]
        self.assertEqual((result["verdict"], result["seed_deterministic"]), ("unknown", False))

    def test_a_refused_context_is_refused_and_not_logged_as_spent(self):
        result, moved, speech = self.probe(context="refused")
        self.assertEqual((result["verdict"], result["accepted"]), ("refused", False))
        self.assertEqual(len(speech), 2)
        self.assertEqual(moved, ["seed_a.wav", "seed_b.wav"])

    def test_a_context_read_aloud_is_caught_by_scribe(self):
        result = self.probe(context="spoken", heard_context="Who comes to the choir without a song?")[0]
        self.assertEqual(result["verdict"], "spoken")
        self.assertEqual(result["takes"]["context"]["leaked"], ["who", "comes", "without", "a", "song"])

    def test_a_second_probe_with_its_own_words_keeps_the_first_runs_evidence(self):
        api = FakeApi()
        for n in ("seed_a", "seed_b", "context"):
            api.stt[f"{n}.wav"] = ("To the choir.", words_for("To the choir.", 0.1, 0.25))
        with sandbox(api) as tmp:
            vb._save(vb.PICKS, {"roles": {"unsung_m2": {"voice_id": "tenor", "ref_f0": 150.0}}})
            vb.probe()
            first = {p.name: p.read_bytes() for p in (tmp / "Media" / "takes" / "bossvoice" / "probe" / "run1").glob("*.wav")}
            for n in ("seed_a", "seed_b", "context"):
                api.stt[f"{n}.wav"] = ("to hear us", words_for("to hear us", 0.1, 0.25))
            vb.probe(text="to hear us", before="You came back", after="forget again.", label="voiced")
            result = json.loads(vb.PROBE.read_text(encoding="utf-8"))
            self.assertEqual((result["label"], result["text"], result["previous_text"]), ("voiced", "to hear us", "You came back"))
            self.assertEqual([r["label"] for r in result["earlier"]], ["run1"])
            self.assertEqual(sorted(p.name for p in (tmp / "Media" / "takes" / "bossvoice" / "probe" / "voiced").glob("*.wav")),
                             ["context.wav", "seed_a.wav", "seed_b.wav"])
            for name, data in first.items():
                self.assertEqual((tmp / "Media" / "takes" / "bossvoice" / "probe" / "run1" / name).read_bytes(), data)

    def test_a_failure_that_is_not_about_the_context_stops_without_a_verdict(self):
        api = FakeApi()
        api.context = "unauthorised"
        with sandbox(api) as tmp:
            vb._save(vb.PICKS, {"roles": {"unsung_m2": {"voice_id": "tenor", "ref_f0": 150.0}}})
            with self.assertRaises(SystemExit):
                vb.probe()
            self.assertFalse(vb.PROBE.exists())
            self.assertEqual(sorted(p.name for p in (eleven.OUT / "bossvoice" / "probe").rglob("*.wav")), [])
            self.assertEqual(len(list((tmp / "Media" / "takes" / "bossvoice" / "probe" / "run1").glob("*.wav"))), 2)


if __name__ == "__main__":
    unittest.main()
