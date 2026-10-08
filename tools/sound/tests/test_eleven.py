"""Tests for eleven.py's speech context, Voice Design candidates and Scribe transcripts, against an offline fake of
the API (tests/fake_api.py): no network, no credits.

    python -m unittest discover -s tools/sound/tests -v
"""
import base64
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import eleven  # noqa: E402
from fake_api import FakeApi, ledger, paid_spend, sandbox, tone, words_for, write_wav  # noqa: E402

SENTENCE = ("The river runs past the old mill, and every evening the bell in the tower rings once for each of the "
            "long grey hours.")
DESCRIPTION = "A very old man with a deep, slow, gravelly voice. Studio quality recording, no background noise."


class SpeechTest(unittest.TestCase):
    def test_sends_the_words_around_a_fragment_and_a_seed_and_logs_them(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            eleven.tts("probe/frag", "[hushed] to the choir", "v1", previous_text="Who comes",
                       next_text="without a song?", seed=4242)
            body = api.posts[-1]["json"]
            self.assertEqual((body["previous_text"], body["next_text"], body["seed"]),
                             ("Who comes", "without a song?", 4242))
            entry = ledger(tmp)[-1]
            self.assertEqual((entry["previous_text"], entry["next_text"], entry["seed"]),
                             ("Who comes", "without a song?", 4242))
            self.assertTrue((eleven.OUT / "probe" / "frag.wav").exists())

    def test_sends_no_context_or_seed_unless_asked(self):
        api = FakeApi()
        with sandbox(api):
            eleven.tts("probe/plain", "[hushed] Who comes", "v1")
            self.assertFalse({"previous_text", "next_text", "seed"} & set(api.posts[-1]["json"]))


class DesignTest(unittest.TestCase):
    def test_keeps_each_preview_as_the_mp3_the_api_sent_with_an_index(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = tmp / "candidates"
            eleven.design("colossus", DESCRIPTION, SENTENCE, mp3_dir=str(folder))
            sent = [base64.b64decode(p["audio_base_64"]) for p in api.designs[-1]]
            for letter, audio in zip("abc", sent):
                self.assertEqual((folder / f"colossus_{letter}.mp3").read_bytes(), audio)
            self.assertEqual(sorted(f.name for f in folder.iterdir()), ["colossus_a.mp3", "colossus_b.mp3", "colossus_c.mp3"])
            self.assertEqual(eleven.index_path(folder), tmp / "index" / "candidates.json")     # not where Nate listens
            index = json.loads(eleven.index_path(folder).read_text(encoding="utf-8"))["colossus"]
            self.assertEqual([c["candidate"] for c in index["candidates"]], ["colossus_a", "colossus_b", "colossus_c"])
            self.assertEqual([c["generated_voice_id"] for c in index["candidates"]], ["gen0a", "gen0b", "gen0c"])
            self.assertEqual((index["text"], index["description"]), (SENTENCE, DESCRIPTION))

    def test_names_each_preview_wav_the_way_sample_reads_it(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            eleven.design("leviathan", DESCRIPTION, SENTENCE, mp3_dir=str(tmp / "candidates"))
            index = json.loads(eleven.index_path(tmp / "candidates").read_text(encoding="utf-8"))["leviathan"]
            for c in index["candidates"]:
                self.assertGreater(len(eleven.sample(c["preview_wav"][:-len(".wav")])), eleven.SR)

    def test_previews_can_be_numbered_instead_of_lettered(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = tmp / "candidates"
            eleven.design("leviathan_f", DESCRIPTION, SENTENCE, mp3_dir=str(folder), numbered=True)
            self.assertEqual(sorted(f.name for f in folder.iterdir()),
                             ["leviathan_f1.mp3", "leviathan_f2.mp3", "leviathan_f3.mp3"])
            index = json.loads(eleven.index_path(folder).read_text(encoding="utf-8"))["leviathan_f"]
            self.assertEqual([(c["candidate"], c["generated_voice_id"]) for c in index["candidates"]],
                             [("leviathan_f1", "gen0a"), ("leviathan_f2", "gen0b"), ("leviathan_f3", "gen0c")])
            self.assertFalse([p for p in api.posts if p["path"] == "/v1/text-to-voice"])        # nothing saved

    def test_a_second_design_joins_the_same_index(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            folder = str(tmp / "candidates")
            eleven.design("colossus", DESCRIPTION, SENTENCE, mp3_dir=folder)
            eleven.design("colossus2", DESCRIPTION, SENTENCE, mp3_dir=folder)
            index = json.loads(eleven.index_path(tmp / "candidates").read_text(encoding="utf-8"))
            self.assertEqual(sorted(index), ["colossus", "colossus2"])

    def test_the_ledger_counts_a_design_by_our_own_count_and_keeps_the_api_balance_either_side(self):
        api = FakeApi(used=830)
        api.charges["design"] = 120
        with sandbox(api) as tmp:
            paid_spend(tmp, 4000)                      # our count (35455 left) is under the API's (38625 left)
            eleven.design("leviathan", DESCRIPTION, SENTENCE)
            entry, balance = ledger(tmp)[-2:]           # the design, then the balance read after it
            self.assertEqual((entry["kind"], entry["tier"], entry["plan"]), ("design", "paid", "starter"))
            self.assertEqual(entry["credits"], len(SENTENCE) * 3)
            self.assertEqual(entry["left"], 39455 - 4000 - len(SENTENCE) * 3)
            self.assertEqual((entry["api_left_before"], balance["kind"], balance["api_left"]), (38625, "balance", 38505))
            self.assertEqual(entry["previews"], ["gen0a", "gen0b", "gen0c"])


class ScriptWordsTest(unittest.TestCase):
    def test_words_scribe_cannot_hear_apart_count_as_one(self):
        """Homophones and spelling variants a transcript cannot tell apart by sound (seen in production)."""
        self.assertEqual(eleven.script_words("My son broken again"), eleven.script_words("[stunned] My sun... broken. Again."))
        self.assertEqual(eleven.script_words("the long gray hours"), eleven.script_words("the long grey hours"))
        self.assertEqual(eleven.script_words("it's part now"), eleven.script_words("its part now?"))
        self.assertNotEqual(eleven.script_words("in the song"), eleven.script_words("in the sun"))

    def test_a_missing_plural_s_is_a_different_word_even_before_an_s(self):
        """VP2 review I1: "fall" and "falls" are different words, so the comparison never drops a final s; a take
        Scribe hears that way is accepted one by one (voicebatch.py accept), by whoever decides, never by the rule."""
        self.assertNotEqual(eleven.script_words("Fall silent"), eleven.script_words("[slowly] falls silent"))
        self.assertEqual(eleven.script_words("No one has sung"), ["no", "one", "has", "sung"])

    def test_other_words_still_differ(self):
        self.assertNotEqual(eleven.script_words("my moon"), eleven.script_words("my sun"))


class LedgerTest(unittest.TestCase):
    def test_a_design_stays_on_record_when_the_balance_read_after_it_fails(self):
        api = FakeApi()
        api.balance_fails_after_design = True
        with sandbox(api) as tmp:
            eleven.design("colossus", DESCRIPTION, SENTENCE, mp3_dir=str(tmp / "candidates"))
            designs = [e for e in ledger(tmp) if e["kind"] == "design"]
            self.assertEqual([e["previews"] for e in designs], [["gen0a", "gen0b", "gen0c"]])
            self.assertTrue((tmp / "candidates" / "colossus_c.mp3").exists())

    def test_spend_is_logged_as_our_estimate_even_when_the_api_reads_lower(self):
        api = FakeApi(used=830)                       # a fresh ledger: our count (39455 left) is over the API's (38625)
        with sandbox(api) as tmp:
            eleven.tts("probe/x", "[hushed] Who comes", "v1")
            eleven.design("leviathan", DESCRIPTION, SENTENCE)
            tts, design = [e for e in ledger(tmp) if e["kind"] in ("tts", "design")]
            self.assertEqual((tts["credits"], tts["left"]), (18, 38625 - 18))              # never negative again
            self.assertEqual((design["credits"], design["left"]), (len(SENTENCE) * 3, 38625 - len(SENTENCE) * 3))


class RetryTest(unittest.TestCase):
    def test_a_paid_request_is_never_sent_twice_after_a_read_error(self):
        saved = eleven._key
        eleven._key = lambda: "dummy"
        try:
            retry = eleven._session().get_adapter(f"{eleven.API}/v1/text-to-speech/x").max_retries
        finally:
            eleven._key = saved
        self.assertEqual((retry.read, retry.other), (0, 0))      # the request may have run: never resend it
        self.assertIn(429, retry.status_forcelist)               # a 429 was not run: resending is safe
        self.assertGreater(retry.total, 0)


class TranscribeTest(unittest.TestCase):
    def test_keeps_scribes_word_timings_beside_the_take_and_logs_the_seconds(self):
        api = FakeApi()
        api.stt["frag.wav"] = ("Who comes.", words_for("Who comes.", 0.12, 0.3))
        with sandbox(api) as tmp:
            write_wav(eleven.OUT / "probe" / "frag.wav", tone(150.0, 1.5))
            self.assertEqual(eleven.transcribe("probe/frag"), "Who comes.")
            saved = json.loads((eleven.OUT / "probe" / "frag.scribe.json").read_text(encoding="utf-8"))
            self.assertEqual(saved["text"], "Who comes.")
            self.assertEqual([(w["text"], w["start"]) for w in saved["words"] if w["type"] == "word"],
                             [("Who", 0.12), ("comes.", 0.42)])
            entry = ledger(tmp)[-1]
            self.assertEqual((entry["kind"], entry["name"], entry["model"]), ("stt", "probe/frag", "scribe_v2"))
            self.assertAlmostEqual(entry["seconds"], 1.5, places=2)


class CommandLineTest(unittest.TestCase):
    def setUp(self):
        self.saved = (eleven.tts, eleven.design, sys.argv)
        self.calls = []
        eleven.tts = lambda *a, **k: self.calls.append(("tts", a, k))
        eleven.design = lambda *a, **k: self.calls.append(("design", a, k))

    def tearDown(self):
        eleven.tts, eleven.design, sys.argv = self.saved

    def test_speech_takes_the_context_and_a_seed(self):
        sys.argv = ["eleven.py", "tts", "probe/frag", "[hushed] to the choir", "--voice", "v1",
                    "--previous-text", "Who comes", "--next-text", "without a song?", "--seed", "7"]
        eleven.main()
        kind, args, kwargs = self.calls[-1]
        self.assertEqual(kind, "tts")
        self.assertEqual(list(args[-3:]), ["Who comes", "without a song?", 7])

    def test_design_takes_a_folder_for_the_mp3s(self):
        sys.argv = ["eleven.py", "design", "colossus", DESCRIPTION, SENTENCE, "--mp3-dir", "C:/candidates"]
        eleven.main()
        kind, args, kwargs = self.calls[-1]
        self.assertEqual((kind, args[5], args[6]), ("design", "C:/candidates", False))      # mp3_dir, numbered


if __name__ == "__main__":
    unittest.main()
