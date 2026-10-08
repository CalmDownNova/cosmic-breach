"""Tests for voicebatch.py's take lifecycle (run, check, pick, shelve) against the offline fake of the API
(tests/fake_api.py): no network, no credits, nothing written outside a temporary folder.

    python -m unittest discover -s tools/sound/tests -v
"""
import contextlib
import io
import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402

import eleven  # noqa: E402
import voicebatch as vb  # noqa: E402
from fake_api import FakeApi, sandbox, tone, words_for, write_wav  # noqa: E402

FISTS = {"boss": "colossus", "id": "fists", "trigger": "weapon:bare_hands", "condition": "none", "priority": 45,
         "repeat": "once per fight", "text": "[dry amusement] Fists? ...Bold.", "words": "Fists? ...Bold.",
         "delivery": "Dry. Budget 2.2 s.", "budget": 2.2, "fragment_budget": None, "shipped": False, "split": None}


def script(*rows) -> None:
    vb._save(vb.SCRIPT, {"source": "test", "lines": list(rows)})


def roles(**voices) -> None:
    vb._save(vb.PICKS, {"roles": {r: {"voice_id": v, "ref_f0": None} for r, v in voices.items()}})


def hear(api: FakeApi, take: str, text: str, step: float = 0.35) -> None:
    api.stt[f"{Path(take).name}.wav"] = (text, words_for(text, 0.1, step))


class RedoTest(unittest.TestCase):
    """The plan's repair paths (Task 12's "fix by retake", Task 11's late pick of another voice) remove a kept line
    and make it again: the second round must get new take names, be heard by Scribe, and leave the first round's
    takes in Media untouched."""

    def test_a_redo_makes_new_takes_checks_them_and_keeps_the_old_ones(self):
        api = FakeApi()
        with sandbox(api) as tmp:
            script(FISTS)
            roles(colossus="voice_one")
            base = "bossvoice/colossus/fists"
            for n in (1, 2):
                hear(api, f"{base}_take{n}", "Fists? Bold.")
            vb.run("colossus", 2, set(), False, False, False)
            self.assertEqual(vb.check("colossus", False), 0)
            self.assertEqual(vb.pick("colossus", False), 0)
            shelved = {p.name: p.read_bytes() for p in (tmp / "Media" / "takes" / "bossvoice" / "colossus").glob("*.wav")}
            self.assertEqual(sorted(shelved), ["fists_take1.wav", "fists_take2.wav"])

            for ext in (".wav", ".scribe.json"):                    # the redo: the kept line goes, a new voice comes
                (eleven.OUT / f"{base}{ext}").unlink()
            roles(colossus="voice_two")
            for n in (3, 4):
                hear(api, f"{base}_take{n}", "Fist? Old.")          # misread this time
            heard_before = sum(p["path"] == "/v1/speech-to-text" for p in api.posts)
            vb.run("colossus", 2, set(), False, False, False)
            self.assertEqual(vb.takes_of(base), [f"{base}_take3", f"{base}_take4"])
            self.assertEqual(vb.check("colossus", False), 1)          # one line with no passing take
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts) - heard_before, 2)
            self.assertEqual(vb.pick("colossus", False), 1)          # nothing passes, nothing is kept
            self.assertFalse((eleven.OUT / f"{base}.wav").exists())
            after = {p.name: p.read_bytes() for p in (tmp / "Media" / "takes" / "bossvoice" / "colossus").glob("*.wav")}
            for name, data in shelved.items():
                self.assertEqual(after[name], data)

    def test_check_reports_this_runs_takes_and_passes_once_every_line_has_a_good_take(self):
        """VP2 review M1: two misheard takes, then a good retake. check reports the retake, counts the two earlier
        failures apart, and exits 0: the line now has a take that passes."""
        api = FakeApi()
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            base = "bossvoice/colossus/fists"
            for n in (1, 2):
                hear(api, f"{base}_take{n}", "Fist? Old.")
            hear(api, f"{base}_take3", "Fists? Bold.")
            with contextlib.redirect_stdout(io.StringIO()):
                vb.run("colossus", 2, set(), False, False, False)
                self.assertEqual(vb.check("colossus", False), 1)
                vb.run("colossus", 2, set(), True, False, False)     # --retake: one more take
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(vb.check("colossus", False), 0)
            self.assertIn("fists_take3: heard", out.getvalue())
            self.assertNotIn("fists_take1", out.getvalue())
            self.assertIn("0 take(s) failed; 2 earlier take(s) still failing", out.getvalue())

    def test_a_take_whose_audio_changed_is_heard_again(self):
        api = FakeApi()
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            take = "bossvoice/colossus/fists_take1"
            hear(api, take, "Fists? Bold.")
            vb.run("colossus", 1, set(), False, False, False)
            vb.check("colossus", False)
            write_wav(eleven.OUT / f"{take}.wav", tone(120.0, 1.2, seed=9))     # the same name, other audio
            heard_before = sum(p["path"] == "/v1/speech-to-text" for p in api.posts)
            vb.check("colossus", False)
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts) - heard_before, 1)

    def test_pick_does_not_trust_a_check_made_on_other_audio(self):
        api = FakeApi()
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            take = "bossvoice/colossus/fists_take1"
            hear(api, take, "Fists? Bold.")
            vb.run("colossus", 1, set(), False, False, False)
            vb.check("colossus", False)
            write_wav(eleven.OUT / f"{take}.wav", tone(120.0, 1.2, seed=9))
            self.assertEqual(vb.pick("colossus", False), 1)
            self.assertFalse((eleven.OUT / "bossvoice" / "colossus" / "fists.wav").exists())


FINISHED = {"boss": "unsung", "id": "finished", "trigger": "boss_kill", "condition": "none", "priority": 100,
            "repeat": "once per fight", "text": "The hymn is finished, at last.", "words": "The hymn is finished, at last.",
            "delivery": "Ghosts. Budget 2.2 s.", "budget": 2.2, "fragment_budget": None, "shipped": False,
            "split": {"kind": "relay", "fragments": [{"tag": "m1", "text": "[whispers] The hymn"},
                                                     {"tag": "m2", "text": "[whispers] is finished,"},
                                                     {"tag": "m3", "text": "[whispers, at peace] at last."}]}}


def made(take: str, f0: float, seconds: float = 0.3, speech: float = 0.25, ok: bool = True, seed: int = 0) -> None:
    """A take on disk with its check, as `check` would leave them (pitch given, not measured). Scribe's transcript is
    the job's words when `ok`, other words when not: the words are judged from it, never from the verdict."""
    write_wav(eleven.OUT / f"{take}.wav", tone(f0 or 150.0, seconds, seed))
    checks = vb._load(vb.CHECKS, {})
    checks[take] = {"ok": ok, "speech_s": speech, "f0": f0, "sha": vb.audio_hash(take)}
    job = vb.known_jobs().get(re.sub(r"_take\d+$", "", take))
    if job is not None:
        checks[take]["heard"] = vb.plain(job.text) if ok else "not the words"
    vb._save(vb.CHECKS, checks)


def picked(boss: str) -> tuple:
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        problems = vb.pick(boss, False)
    return problems, vb._load(vb.TAKES, {}), out.getvalue()


class PickPitchTest(unittest.TestCase):
    """A take with no measurable pitch (a whisper reads 0.0) is no evidence of the right voice: it must not win."""

    def test_a_voiced_take_beats_one_with_no_pitch(self):
        with sandbox(FakeApi()):
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            base = "bossvoice/colossus/fists"
            made(f"{base}_take1", 0.0, seed=1)
            made(f"{base}_take2", 130.0, seed=2)
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes[base]["take"]), (0, f"{base}_take2"))
            self.assertIn("CHECK: pitch 130 Hz", out)

    def test_a_kept_take_with_no_pitch_is_flagged(self):
        with sandbox(FakeApi()):
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            base = "bossvoice/colossus/fists"
            made(f"{base}_take1", 0.0)
            problems, takes, out = picked("colossus")
            self.assertEqual(takes[base]["take"], f"{base}_take1")
            self.assertIn("CHECK: no pitch", out)

    def test_a_fragment_asked_to_whisper_keeps_its_whisper_quietly(self):
        with sandbox(FakeApi()):
            script(FINISHED)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 120.0} for m in (1, 2, 3)}})
            for k, m in ((1, 1), (2, 2), (3, 3)):
                base = f"bossvoice/unsung/finished/f{k}_m{m}"
                made(f"{base}_take1", 0.0, seed=k)
                made(f"{base}_take2", 150.0, seed=10 + k)
            problems, takes, out = picked("unsung")
            self.assertEqual(takes["bossvoice/unsung/finished/f1_m1"]["take"], "bossvoice/unsung/finished/f1_m1_take1")
            self.assertNotIn("no pitch", out)


FORK_TEXT = "[amused] A fork? I am no fish, little one. Not me."      # the fake speaks 0.05 s a character
FORK = {"boss": "leviathan", "id": "fork", "trigger": "weapon:trident", "condition": "none", "priority": 45,
        "repeat": "once per fight", "text": FORK_TEXT, "words": vb.plain(FORK_TEXT), "delivery": "Budget 2.4 s.",
        "budget": 2.4, "fragment_budget": None, "shipped": False, "split": None}
FALLS = {"boss": "unsung", "id": "voice_falls", "trigger": "player_death", "condition": "masks:3", "priority": 70,
         "repeat": "once per fight", "text": "Another voice falls silent in the song.",
         "words": "Another voice falls silent in the song.", "delivery": "Budget 2.0 s.", "budget": 2.0,
         "fragment_budget": None, "shipped": False,
         "split": {"kind": "relay", "fragments": [{"tag": "m1", "text": "Another voice"}, {"tag": "m2", "text": "falls silent"},
                                                  {"tag": "m3", "text": "in the song."}]}}


class SpanTest(unittest.TestCase):
    """The take gate and the build gate measure the same span: what the build will place and play."""

    def test_check_records_the_span_the_build_will_measure(self):
        import bossvoice
        api = FakeApi()
        with sandbox(api):
            script(dict(FORK, budget=2.0, delivery="Budget 2.0 s."))
            roles(leviathan="whale")
            take = "bossvoice/leviathan/fork_take1"
            hear(api, take, FORK["words"], step=0.18)                 # Scribe's span 1.76 s: inside 2.0 s
            vb.run("leviathan", 1, set(), False, False, False)
            self.assertEqual(vb.check("leviathan", False), 1)         # the built words run about 2.8 s: over, even at 1.15x
            entry = vb._load(vb.CHECKS, {})[take]
            start, end = bossvoice.spoken_span(bossvoice.words_of(eleven.sample(take)))
            self.assertAlmostEqual(entry["span_s"], end - start, delta=0.005)
            self.assertLess(entry["speech_s"], 2.0)

    def test_fits_judges_the_built_span_and_reads_old_checks_by_scribe(self):
        job = vb.Job("leviathan", "fork", "bossvoice/leviathan/fork", "leviathan", FORK_TEXT, budget=2.4)
        self.assertFalse(vb.fits(job, {"ok": True, "speech_s": 2.0, "span_s": 2.6}))
        self.assertTrue(vb.fits(job, {"ok": True, "speech_s": 2.0, "span_s": 2.4}))
        self.assertTrue(vb.fits(job, {"ok": True, "speech_s": 2.0}))

    def test_an_opener_fragment_may_overlap_the_next_masks_words_by_150_ms_at_most(self):
        """150 ms of words: as placed, a fragment also carries 30 ms before its words and 120 ms after them, so it
        may run 300 ms past the next lift (1.5 s placed) before the two masks' words overlap by more than 150 ms."""
        job = vb.Job("unsung", "open_solo", "bossvoice/unsung/open_solo/f1_m1", "unsung_m1", "[softly] Who comes",
                     None, "to the choir without a song?", 1.1)
        self.assertTrue(vb.fits(job, {"ok": True, "speech_s": 0.9, "span_s": 1.2, "placed_s": 1.47}))
        self.assertFalse(vb.fits(job, {"ok": True, "speech_s": 0.9, "span_s": 1.3, "placed_s": 1.55}))

    def test_check_names_the_rule_an_opener_fragment_failed(self):
        job = vb.Job("unsung", "open_solo", "bossvoice/unsung/open_solo/f1_m1", "unsung_m1", "[softly] Who comes",
                     None, "to the choir without a song?", 1.1)
        last = vb.Job("unsung", "open_solo", "bossvoice/unsung/open_solo/f3_m3", "unsung_m3",
                      "[low, softly] without a song?", "Who comes to the choir", None, 1.1)
        self.assertIn("room 1.51 s", vb.over_note(job, {"ok": True, "speech_s": 1.0, "span_s": 1.3, "placed_s": 1.55}))
        self.assertIn("1.30 s built against 1.1 s", vb.over_note(last, {"ok": True, "speech_s": 1.0, "span_s": 1.3,
                                                                    "placed_s": 1.45}))

    def test_an_openers_last_fragment_is_judged_on_its_words_not_a_lift(self):
        """Nothing follows the last fragment, so only its own budget holds it (review N2)."""
        job = vb.Job("unsung", "open_solo", "bossvoice/unsung/open_solo/f3_m3", "unsung_m3",
                     "[low, softly] without a song?", "Who comes to the choir", None, 1.1)
        self.assertTrue(vb.fits(job, {"ok": True, "speech_s": 1.0, "span_s": 1.1, "placed_s": 1.45}))
        self.assertFalse(vb.fits(job, {"ok": True, "speech_s": 1.0, "span_s": 1.2, "placed_s": 1.45}))

    def test_pick_refuses_a_line_whose_stitch_runs_over_its_budget(self):
        with sandbox(FakeApi()):
            script(FALLS)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 150.0} for m in (1, 2, 3)}})
            for k in (1, 2, 3):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=0.9, seed=k)
            problems, takes, out = picked("unsung")
            self.assertEqual(problems, 1)
            self.assertIn("budget 2.0", out)
            self.assertEqual(takes, {})
            self.assertEqual(len(vb.takes_of("bossvoice/unsung/voice_falls/f1_m1")), 1)    # left for a retake

    def test_pick_keeps_the_takes_that_build_in_time_when_some_do(self):
        with sandbox(FakeApi()):
            script(FALLS)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 150.0} for m in (1, 2, 3)}})
            made("bossvoice/unsung/voice_falls/f1_m1_take1", 150.0, seconds=1.5, seed=1)    # on pitch, too long
            made("bossvoice/unsung/voice_falls/f1_m1_take2", 180.0, seconds=0.3, seed=2)    # off pitch, short
            for k in (2, 3):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=0.3, seed=k + 2)
            problems, takes, out = picked("unsung")
            self.assertEqual(problems, 0)
            self.assertEqual(takes["bossvoice/unsung/voice_falls/f1_m1"]["take"], "bossvoice/unsung/voice_falls/f1_m1_take2")

    def test_a_whispered_fragment_is_checked_as_the_stitcher_trims_it(self):
        import bossvoice
        x = tone(150.0, 0.6, seed=3)
        whisper = vb.Job("unsung", "finished", "bossvoice/unsung/finished/f1_m1", "unsung_m1", "[whispers] The hymn")
        plain = vb.Job("unsung", "voice_falls", "bossvoice/unsung/voice_falls/f1_m1", "unsung_m1", "Another voice")
        self.assertTrue(np.array_equal(vb.built_words(whisper, x), bossvoice.trim_fragment(x, whispered=True)))
        self.assertTrue(np.array_equal(vb.built_words(plain, x), bossvoice.trim_fragment(x)))

    def test_a_relayed_line_that_fits_only_with_a_slight_overlap_gets_one(self):
        """The decision for VP2: a relayed line over its limit may let its masks overlap by about 150 ms; pick tries
        without overlap first, and records the overlap so the build places the line the same way."""
        import bossvoice
        with sandbox(FakeApi()):
            script(FALLS)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 150.0} for m in (1, 2, 3)}})
            for k in (1, 2, 3):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=0.6, seed=k)
            problems, takes, out = picked("unsung")
            self.assertEqual(problems, 0)
            self.assertEqual(len(takes), 3)
            stitch = vb._load(vb.STITCH, {})
            self.assertEqual(stitch["voice_falls"]["gap"], bossvoice.overlap_gap(False))
            self.assertAlmostEqual(bossvoice.overlap_gap(False), -0.31)          # about 150 ms of words, plus the padding
            self.assertIn("overlap", out)

    def test_a_line_that_fits_without_overlap_gets_none(self):
        with sandbox(FakeApi()):
            script(FALLS)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 150.0} for m in (1, 2, 3)}})
            for k in (1, 2, 3):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=0.3, seed=k)
            problems, takes, out = picked("unsung")
            self.assertEqual((problems, vb._load(vb.STITCH, {})), (0, {}))

    def test_a_refused_line_names_the_retake_that_works_and_retake_makes_it(self):
        """Review N1: a line refused at the stitch has fragments that each pass, so --retake used to plan nothing."""
        with sandbox(FakeApi()):
            script(FALLS)
            roles(unsung_m1="v1", unsung_m2="v2", unsung_m3="v3")
            for k, seconds in ((1, 0.9), (2, 1.2), (3, 0.9)):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=seconds, seed=k)
            problems, takes, out = picked("unsung")
            self.assertEqual(problems, 1)
            self.assertIn("run --boss unsung --retake", out)
            import bossvoice                                # the report gives the closest try, with the overlap
            names = {(k, k): f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1" for k in (1, 2, 3)}
            piece = lambda n: bossvoice.trim_fragment(eleven.sample(n))
            closest = vb._stitch_problem(FALLS, names, piece, {}, bossvoice.overlap_gap(False))[1]
            self.assertIn(closest, out)
            self.assertIn("run --boss unsung --only voice_falls --more 1", out)      # review M5: 3 takes, not 7
            self.assertNotIn("--takes", out)
            planned = io.StringIO()
            with contextlib.redirect_stdout(planned):
                vb.run("unsung", 2, set(), True, False, True)
            self.assertIn("unsung: 1 take(s)", planned.getvalue())
            self.assertIn("voice_falls/f2_m2_take2", planned.getvalue())             # its longest fragment

    def test_the_overlap_record_gives_the_closest_miss_without_it(self):
        """VP2 review M4: voice_stitch.json says how far the line was from fitting without the overlap: its closest
        set, not the first set tried."""
        import bossvoice
        with sandbox(FakeApi()):
            script(FALLS)
            vb._save(vb.PICKS, {"roles": {f"unsung_m{m}": {"voice_id": f"v{m}", "ref_f0": 150.0} for m in (1, 2, 3)}})
            made("bossvoice/unsung/voice_falls/f1_m1_take1", 150.0, seconds=0.9, seed=1)     # tried first, misses most
            made("bossvoice/unsung/voice_falls/f1_m1_take2", 150.0, seconds=0.6, seed=2)
            for k in (2, 3):
                made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", 150.0, seconds=0.6, seed=k + 2)
            names = {(1, 1): "bossvoice/unsung/voice_falls/f1_m1_take2",
                     **{(k, k): f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1" for k in (2, 3)}}
            piece = lambda n: bossvoice.trim_fragment(eleven.sample(n))
            seconds, why = vb._stitch_problem(FALLS, names, piece, {}, bossvoice.MIN_GAP)
            problems, takes, out = picked("unsung")
            self.assertEqual(problems, 0)
            self.assertEqual(vb._load(vb.STITCH, {})["voice_falls"]["why"], why)

    def test_more_adds_that_many_takes_to_each_fragment_of_a_line(self):
        """VP2 review M5: a new take of every fragment is one each, whatever each already has."""
        with sandbox(FakeApi()):
            script(FALLS)
            roles(unsung_m1="v1", unsung_m2="v2", unsung_m3="v3")
            for k, count in ((1, 2), (2, 2), (3, 4)):
                for n in range(1, count + 1):
                    made(f"bossvoice/unsung/voice_falls/f{k}_m{k}_take{n}", 150.0, seed=10 * k + n)
            planned = io.StringIO()
            with contextlib.redirect_stdout(planned):
                vb.run("unsung", 2, {"voice_falls"}, False, False, True, more=1)
            self.assertIn("unsung: 3 take(s)", planned.getvalue())
            self.assertIn("voice_falls/f3_m3_take5", planned.getvalue())

    def test_the_openers_must_sit_on_the_lifts(self):
        self.assertIsNone(vb.lift_problem([0.0, 1.2, 2.4]))
        self.assertIn("1.38", vb.lift_problem([0.0, 1.38, 2.55]))


QUIET_FORK = dict(FORK, delivery="Mock offence. Quiet window only. Budget 2.4 s.")


class TempoPickTest(unittest.TestCase):
    """VP2 review I3: the Leviathan may speak up to 1.15x faster before any text is cut (the least tempo that fits,
    recorded per line, applied before the limit check), and a quiet-window line gets no slack over its limit."""

    def kept_over(self, seconds: float, row: dict = QUIET_FORK) -> str:
        boss, line = row["boss"], row["id"]
        script(row)
        roles(**{boss: "voice"})
        take = f"bossvoice/{boss}/{line}_take1"
        made(take, 150.0, seconds=seconds, seed=4)
        job = next(j for j in vb.jobs(boss) if j.line_id == line)
        checks = vb._load(vb.CHECKS, {})
        checks[take].update(vb._measured(job, eleven.sample(take)))
        vb._save(vb.CHECKS, checks)
        return take

    def test_a_quiet_window_line_gets_no_slack(self):
        hard = vb.Job("leviathan", "fork", "bossvoice/leviathan/fork", "leviathan", "x", budget=2.4, hard=True)
        soft = vb.Job("leviathan", "fork", "bossvoice/leviathan/fork", "leviathan", "x", budget=2.4)
        self.assertFalse(vb.fits(hard, {"ok": True, "speech_s": 2.0, "span_s": 2.43}))
        self.assertTrue(vb.fits(soft, {"ok": True, "speech_s": 2.0, "span_s": 2.43}))

    def test_jobs_mark_the_quiet_window_lines(self):
        with sandbox(FakeApi()):
            script(QUIET_FORK, FISTS)
            self.assertEqual({j.line_id: j.hard for j in vb.jobs("leviathan") + vb.jobs("colossus")},
                             {"fork": True, "fists": False})

    def test_pick_speeds_a_leviathan_line_up_by_the_least_tempo_that_fits(self):
        import bossvoice
        with sandbox(FakeApi()):
            take = self.kept_over(2.6)
            problems, takes, out = picked("leviathan")
            self.assertEqual((problems, takes["bossvoice/leviathan/fork"]["take"]), (0, take))
            tempo = bossvoice.tempo_of("leviathan", "fork")
            self.assertTrue(1.0 < tempo <= 1.15, tempo)
            job = vb.jobs("leviathan")[0]
            x = eleven.sample("bossvoice/leviathan/fork")
            a, b = bossvoice.spoken_span(vb.built_words(job, x))
            self.assertLessEqual(b - a, 2.4)                                     # no slack: a quiet window
            slower = round(tempo - 0.01, 2)
            a, b = bossvoice.spoken_span(bossvoice.words_of(bossvoice.stretch(x, slower)))
            self.assertGreater(b - a, 2.4)                                       # the least tempo that fits
            self.assertLessEqual(vb._load(vb.CHECKS, {})[take]["span_s"], 2.4)  # its check measured at that tempo
            self.assertIn(f"{tempo:.2f}x", out)

    def test_pick_refuses_a_line_that_would_need_more_than_115(self):
        with sandbox(FakeApi()):
            self.kept_over(3.1)
            problems, takes, out = picked("leviathan")
            self.assertEqual((problems, takes), (1, {}))
            self.assertEqual(vb._load(vb.TEMPO, {}), {})

    def test_the_colossus_is_never_sped_up(self):
        with sandbox(FakeApi()):
            self.kept_over(2.2, FISTS)                    # 2.2 s of words play 2.4 s slowed: over its 2.2 s
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, vb._load(vb.TEMPO, {})), (1, {}))

    def test_unkeep_forgets_the_lines_tempo(self):
        with sandbox(FakeApi()):
            self.kept_over(2.6)
            picked("leviathan")
            with contextlib.redirect_stdout(io.StringIO()):
                vb.unkeep("bossvoice/leviathan/fork")
            self.assertEqual(vb._load(vb.TEMPO, {}), {})


class TempoCheckTest(unittest.TestCase):
    """VP2 review I3, "applied before the limit check": a Leviathan take is judged on its words as the build will play
    them at a tempo the boss may have (up to 1.15x), so check passes a take that only a tempo can save, and run
    --retake never pays for another take of a line pick can still save the same way."""

    def take(self, api: FakeApi, seconds: float, row: dict = QUIET_FORK, n: int = 1) -> str:
        boss, line = row["boss"], row["id"]
        script(row)
        roles(**{boss: "voice"})
        name = f"bossvoice/{boss}/{line}_take{n}"
        write_wav(eleven.OUT / f"{name}.wav", tone(150.0, seconds, seed=4 + n))
        hear(api, name, vb.plain(row["text"]))                     # Scribe hears the script's words
        return name

    def checked(self, boss: str = "leviathan") -> tuple:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            stuck = vb.check(boss, False)
        return stuck, out.getvalue()

    def retakes(self, boss: str = "leviathan") -> str:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            vb.run(boss, 2, set(), True, False, True)
        return out.getvalue()

    def job(self, row: dict = QUIET_FORK) -> vb.Job:
        return next(j for j in vb.jobs(row["boss"]) if j.line_id == row["id"])

    def test_the_least_tempo_is_the_one_a_take_first_fits_at(self):
        import bossvoice
        with sandbox(FakeApi()):
            script(QUIET_FORK)
            job, x = self.job(), tone(150.0, 2.6, seed=4)
            tempo = vb.least_tempo(job, x)
            self.assertTrue(1.0 < tempo <= 1.15, tempo)
            a, b = bossvoice.spoken_span(vb.built_words(job, x, tempo))
            self.assertLessEqual(b - a, 2.4)                                     # no slack: a quiet window
            a, b = bossvoice.spoken_span(vb.built_words(job, x, round(tempo - 0.01, 2)))
            self.assertGreater(b - a, 2.4)
            self.assertEqual(vb.least_tempo(job, tone(150.0, 2.0, seed=4)), 1.0)     # fits as it is: no tempo
            self.assertIsNone(vb.least_tempo(job, tone(150.0, 3.1, seed=4)))         # more than 1.15x would be needed

    def test_only_a_boss_that_may_speed_up_gets_a_tempo(self):
        with sandbox(FakeApi()):
            script(FISTS)
            job = self.job(FISTS)
            self.assertIsNone(vb.least_tempo(job, tone(150.0, 2.0, seed=4)))
            self.assertNotIn("tempo_fit", vb._measured(job, tone(150.0, 2.0, seed=4)))

    def test_check_passes_a_take_that_a_tempo_saves(self):
        api = FakeApi()
        with sandbox(api):
            name = self.take(api, 2.6)
            stuck, out = self.checked()
            self.assertEqual(stuck, 0)
            entry = vb._load(vb.CHECKS, {})[name]
            self.assertTrue(1.0 < entry["tempo_fit"] <= 1.15, entry["tempo_fit"])
            self.assertIn(f"OK   {name}", out)
            self.assertIn(f"{entry['tempo_fit']:.2f}x", out)
            self.assertIn("0 take(s) failed", out)
            self.assertFalse(vb.fits(self.job(), entry))                    # it still takes a tempo: pick sets it

    def test_check_fails_a_take_that_would_need_more_than_the_boss_may_have(self):
        api = FakeApi()
        with sandbox(api):
            name = self.take(api, 3.1)
            stuck, out = self.checked()
            self.assertEqual(stuck, 1)
            self.assertIsNone(vb._load(vb.CHECKS, {})[name]["tempo_fit"])
            self.assertIn(f"FAIL {name}", out)
            self.assertIn("1.15x", out)
            self.assertIn("NO PASSING TAKE bossvoice/leviathan/fork", out)

    def test_a_retake_is_not_paid_for_when_a_tempo_can_save_the_line(self):
        api = FakeApi()
        with sandbox(api):
            self.take(api, 2.6)
            self.checked()
            self.assertIn("leviathan: 0 take(s)", self.retakes())

    def test_a_retake_is_made_when_no_tempo_can(self):
        api = FakeApi()
        with sandbox(api):
            self.take(api, 3.1)
            self.checked()
            self.assertIn("leviathan: 1 take(s)", self.retakes())

    def test_a_line_check_passed_by_tempo_is_picked_with_it(self):
        import bossvoice
        api = FakeApi()
        with sandbox(api):
            name = self.take(api, 2.6)
            self.checked()
            problems, takes, out = picked("leviathan")
            self.assertEqual((problems, takes["bossvoice/leviathan/fork"]["take"]), (0, name))
            tempo = bossvoice.tempo_of("leviathan", "fork")
            self.assertEqual(tempo, vb._load(vb.CHECKS, {})[name]["tempo_fit"])      # check and pick agree on it


class RejudgeTest(unittest.TestCase):
    def test_check_rejudges_a_stored_transcript_for_free(self):
        """A check is kept with its transcript; when the script's words or the comparison change, check judges the
        stored transcript again without paying Scribe."""
        api = FakeApi()
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            take = "bossvoice/colossus/fists_take1"
            hear(api, take, "Fists? Bold.")
            vb.run("colossus", 1, set(), False, False, False)
            vb.check("colossus", False)
            checks = vb._load(vb.CHECKS, {})
            checks[take]["ok"] = False                                  # as judged under an older rule
            vb._save(vb.CHECKS, checks)
            heard_before = sum(p["path"] == "/v1/speech-to-text" for p in api.posts)
            self.assertEqual(vb.check("colossus", False), 0)
            self.assertTrue(vb._load(vb.CHECKS, {})[take]["ok"])
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts), heard_before)

    def test_check_measures_a_stored_take_again_when_the_build_changes(self):
        """The span and the placed length come from the build's own trim, not from Scribe: when the trim changes (a
        fragment padded to its full PAD), check measures a stored take again, still without paying Scribe."""
        api = FakeApi()
        with sandbox(api):
            script(FALLS)
            roles(unsung_m1="v1", unsung_m2="v2", unsung_m3="v3")
            for k, words in ((1, "Another voice"), (2, "falls silent"), (3, "in the song.")):
                hear(api, f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", words)
            vb.run("unsung", 1, set(), False, False, False)
            vb.check("unsung", False)
            take = "bossvoice/unsung/voice_falls/f2_m2_take1"
            checks = vb._load(vb.CHECKS, {})
            measured = {n: checks[take][n] for n in ("span_s", "placed_s")}
            checks[take].update(span_s=0.1, placed_s=0.2)              # as measured under an older trim
            vb._save(vb.CHECKS, checks)
            heard_before = sum(p["path"] == "/v1/speech-to-text" for p in api.posts)
            with contextlib.redirect_stdout(io.StringIO()):
                vb.check("unsung", False)
            entry = vb._load(vb.CHECKS, {})[take]
            self.assertEqual({n: entry[n] for n in measured}, measured)
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts), heard_before)


def framed(body: np.ndarray, before: float = 0.2, after: float = 0.3) -> np.ndarray:
    return np.concatenate([np.zeros(int(before * vb.SR)), body, np.zeros(int(after * vb.SR))])


def hiss(seconds: float, seed: int = 0) -> np.ndarray:
    from dsp import hp as highpass
    return 0.3 * highpass(np.random.default_rng(seed).standard_normal(int(seconds * vb.SR)), 4500.0, 4)


class CutTest(unittest.TestCase):
    """VP2 review I2: eleven_v4 cuts a fragment at its own boundary with the words after it. check records each take's
    start and end levels against its loudest 20 ms, and how much of its last 50 ms is hiss, and flags a take that
    starts already sounding after another mask, or ends still sounding on something its last word does not end
    with: a hiss after "not", or a voice cut off. pick prefers a take with no such flag."""

    def job(self, text: str, prev: str | None = None, nxt: str | None = "next words") -> vb.Job:
        return vb.Job("unsung", "robes", "bossvoice/unsung/robes/f2_m2", "unsung_m2", text, prev, nxt)

    def note(self, job: vb.Job, x: np.ndarray) -> str | None:
        return vb.cut_note(job, vb._measured(job, x))

    def test_a_clean_take_has_no_flag(self):
        x = framed(tone(150.0, 0.6, seed=1) * np.hanning(int(0.6 * vb.SR)))
        self.assertIsNone(self.note(self.job("but you do not", prev="You wear our robes,"), x))

    def test_a_take_cut_off_while_voiced(self):
        t = np.arange(int(0.6 * vb.SR)) / vb.SR
        x = np.concatenate([np.zeros(int(0.2 * vb.SR)), 0.3 * np.sin(2 * np.pi * 150.0 * t)])
        self.assertIn("while voiced", self.note(self.job("Who will sing"), x))

    def test_a_hiss_after_a_word_that_does_not_end_in_one(self):
        x = np.concatenate([framed(tone(150.0, 0.5, seed=2), after=0.02), hiss(0.12)])
        self.assertIn("hiss", self.note(self.job("but you do not"), x))
        self.assertIsNone(self.note(self.job("You wear our robes,"), x))          # its own final s, still sounding

    def test_a_whispered_take_cut_off_is_still_sounding_not_voiced(self):
        """Cosmetic, VP2 re-review: a whisper has no voice, so its note says it is still sounding."""
        job = vb.Job("unsung", "finished", "bossvoice/unsung/finished/f1_m1", "unsung_m1", "[whispers] The hymn", None,
                     "is finished,")
        note = vb.cut_note(job, {"start_db": -60.0, "end_db": -4.0, "end_hf": 0.1})
        self.assertIn("still sounding", note)
        self.assertNotIn("voiced", note)

    def test_a_take_that_starts_already_sounding_after_another_mask(self):
        t = np.arange(int(0.6 * vb.SR)) / vb.SR
        x = np.concatenate([0.3 * np.sin(2 * np.pi * 150.0 * t) * np.linspace(1.0, 0.0, len(t)) ** 2, np.zeros(int(0.3 * vb.SR))])
        self.assertIn("starts", self.note(self.job("sing.", prev="but you do not", nxt=None), x))
        self.assertIsNone(self.note(self.job("sing.", prev=None, nxt=None), x))  # a line's first words may start at once

    def test_pick_prefers_a_take_with_no_flag(self):
        with sandbox(FakeApi()):
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            base = "bossvoice/colossus/fists"
            made(f"{base}_take1", 100.0, seed=1)                  # right on pitch, but cut off
            made(f"{base}_take2", 120.0, seed=2)
            checks = vb._load(vb.CHECKS, {})
            checks[f"{base}_take1"].update(start_db=-60.0, end_db=-4.0, end_hf=0.1)
            checks[f"{base}_take2"].update(start_db=-60.0, end_db=-60.0, end_hf=0.1)
            vb._save(vb.CHECKS, checks)
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes[base]["take"]), (0, f"{base}_take2"))

    def test_a_kept_take_with_a_flag_says_so(self):
        with sandbox(FakeApi()):
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            base = "bossvoice/colossus/fists"
            made(f"{base}_take1", 100.0, seed=1)
            checks = vb._load(vb.CHECKS, {})
            checks[f"{base}_take1"].update(start_db=-60.0, end_db=-4.0, end_hf=0.1)
            vb._save(vb.CHECKS, checks)
            problems, takes, out = picked("colossus")
            self.assertIn("CHECK: cut", out)

    def test_audit_hands_the_ear_check_its_list(self):
        api = FakeApi()                                  # the fake's takes stop on a 20 ms ramp: cut off, voiced
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            hear(api, "bossvoice/colossus/fists_take1", "Fists? Bold.")
            with contextlib.redirect_stdout(io.StringIO()):
                vb.run("colossus", 1, set(), False, False, False)
                vb.check("colossus", False)
                vb.pick("colossus", False)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(vb.audit(), 0)                 # a flag is for the ear, not a failure
            self.assertIn("CUT bossvoice/colossus/fists: ends", out.getvalue())


class AcceptTest(unittest.TestCase):
    """VP2 review I1: a mismatch Scribe keeps making is decided take by take (the plan's Task 9 Step 4), with its
    reason, who decided and when, never by loosening the comparison for every line."""

    def _falls(self, api: FakeApi) -> str:
        script(dict(FALLS, budget=4.0))
        roles(unsung_m1="v1", unsung_m2="v2", unsung_m3="v3")
        for k, words in ((1, "Another voice"), (2, "fall silent"), (3, "in the song.")):
            hear(api, f"bossvoice/unsung/voice_falls/f{k}_m{k}_take1", words)
        with contextlib.redirect_stdout(io.StringIO()):
            vb.run("unsung", 1, set(), False, False, False)
            vb.check("unsung", False)
        return "bossvoice/unsung/voice_falls/f2_m2_take1"

    def test_an_accepted_take_passes_on_that_audio_only(self):
        api = FakeApi()
        with sandbox(api):
            take = self._falls(api)
            job = next(j for j in vb.jobs("unsung") if j.base == "bossvoice/unsung/voice_falls/f2_m2")
            self.assertFalse(vb.fits(job, vb._load(vb.CHECKS, {})[take]))
            with contextlib.redirect_stdout(io.StringIO()):
                vb.accept(take, "the plural s merges into silent; heard as falls by ear", "orchestrator")
            entry = vb._load(vb.CHECKS, {})[take]
            self.assertTrue(vb.fits(job, entry))
            self.assertEqual((entry["accepted"]["by"], entry["accepted"]["sha"]), ("orchestrator", entry["sha"]))
            self.assertRegex(entry["accepted"]["date"], r"^\d{4}-\d{2}-\d{2}$")
            self.assertFalse(vb.fits(job, {**entry, "sha": "other audio"}))
            with contextlib.redirect_stdout(io.StringIO()):
                vb.check("unsung", False)                        # judged again: the acceptance stays
            self.assertIn("accepted", vb._load(vb.CHECKS, {})[take])
            problems, takes, out = picked("unsung")
            self.assertEqual((problems, takes["bossvoice/unsung/voice_falls/f2_m2"]["take"]), (0, take))

    def test_audit_lists_accepted_takes_and_kept_takes_that_fail_todays_comparison(self):
        api = FakeApi()
        with sandbox(api):
            take = self._falls(api)
            with contextlib.redirect_stdout(io.StringIO()):
                vb.accept(take, "heard as falls by ear", "orchestrator")
                vb.pick("unsung", False)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(vb.audit(), 0)
            self.assertIn("ACCEPTED bossvoice/unsung/voice_falls/f2_m2", out.getvalue())
            self.assertIn("heard as falls by ear", out.getvalue())
            checks = vb._load(vb.CHECKS, {})
            del checks[take]["accepted"]                         # as if nobody had decided
            vb._save(vb.CHECKS, checks)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                self.assertEqual(vb.audit(), 1)
            self.assertIn("FAILS bossvoice/unsung/voice_falls/f2_m2", out.getvalue())

    def test_accept_refuses_a_take_never_heard(self):
        with sandbox(FakeApi()):
            script(FISTS)
            with self.assertRaises(SystemExit):
                vb.accept("bossvoice/colossus/fists_take1", "no", "orchestrator")


class SentTextTest(unittest.TestCase):
    """VP3: a take is kept for its script row as the row reads now, so the text the ledger says it was sent (tags
    included) must be that row's text: a take made before a tag change keeps a delivery the script no longer asks for,
    and the words check cannot see it (it ignores tags)."""

    def kept(self, api: FakeApi) -> None:
        script(FISTS)
        roles(colossus="voice_one")
        hear(api, "bossvoice/colossus/fists_take1", "Fists? Bold.")
        with contextlib.redirect_stdout(io.StringIO()):
            vb.run("colossus", 1, set(), False, False, False)
            vb.check("colossus", False)
            vb.pick("colossus", False)

    def audited(self) -> tuple:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            problems = vb.audit()
        return problems, out.getvalue()

    def test_audit_reports_a_kept_take_made_from_other_text_than_the_script_now_has(self):
        api = FakeApi()
        with sandbox(api):
            self.kept(api)
            problems, out = self.audited()
            self.assertEqual(problems, 0)                                     # made from the script's own text
            self.assertIn("0 made from other text than the script's", out)
            script(dict(FISTS, text="[slowly, dry amusement] Fists? ...Bold."))   # the row's tags change afterwards
            problems, out = self.audited()
            self.assertEqual(problems, 1)
            self.assertIn("STALE bossvoice/colossus/fists <- bossvoice/colossus/fists_take1", out)
            self.assertIn("[dry amusement]", out)                              # what it was sent
            self.assertIn("[slowly, dry amusement]", out)                      # what the script says now
            self.assertIn("1 made from other text than the script's", out)

    def cut_from_whole(self, sent_text: str) -> None:
        """The whole-sentence fallback: mask 1 was sent the whole sentence, and its fragment was cut out of that take."""
        script(FALLS)
        whole_base, whole = "bossvoice/unsung/voice_falls/whole_m1", "bossvoice/unsung/voice_falls/whole_m1_take1"
        sent(whole, sent_text)
        vb._save(vb.CHECKS, {whole: {"ok": True, "heard": "Another voice falls silent in the song.", "speech_s": 2.0,
                                     "f0": 150.0, "sha": "x"}})
        vb._save(vb.TAKES, {"bossvoice/unsung/voice_falls/f1_m1": {"take": whole, "derived_from": whole_base},
                            whole_base: {"take": whole, "derived_from": None}})

    def test_a_fragment_cut_from_a_whole_sentence_is_judged_by_the_whole_take_it_was_cut_from(self):
        """VP3 review M1: not skipped. The take was sent the sentence, so it is the sentence's job it is judged by: its
        text against the ledger's, and Scribe's words (the whole sentence) against the whole sentence, not against the
        fragment, which would fail every cut fragment."""
        api = FakeApi()
        with sandbox(api):
            self.cut_from_whole("Another voice falls silent in the song.")
            problems, out = self.audited()
            self.assertEqual(problems, 0, out)                                # sent the sentence it is judged by
            self.assertNotIn("STALE", out)
            self.assertNotIn("FAILS", out)
        with sandbox(api):
            self.cut_from_whole("[slowly] Another voice falls silent in the song.")    # the sentence's tags changed since
            problems, out = self.audited()
            self.assertEqual(problems, 2, out)                                # the cut fragment and the whole take itself
            self.assertIn("STALE bossvoice/unsung/voice_falls/f1_m1", out)
            self.assertIn("STALE bossvoice/unsung/voice_falls/whole_m1", out)


def sent(take: str, text: str) -> None:
    """The ledger entry eleven.tts writes for a take: what it was sent."""
    eleven._log({"kind": "tts", "name": take, "takes": 1, "text": text, "voice": "voice_one", "model": "eleven_v4",
                 "tier": "paid", "plan": "starter"})


class OtherTextTakesTest(unittest.TestCase):
    """VP3 review I1: the words check ignores tags, so nothing between run and audit noticed a take sent an older tag.
    A take the ledger says was sent other text than its job's text now never counts: not as made, not as passing, not
    for pick, and run --retake plans a new take instead of trusting it (the old take comes back into the repo when a
    line is unkept, which is how a retake round meets it)."""

    OLD = FISTS["text"]
    NEW = "[slowly, dry amusement] Fists? ...Bold."
    BASE = "bossvoice/colossus/fists"

    def start(self, take_f0: dict) -> None:
        """The row now has NEW; each take is on disk with a check, and sent OLD unless its name says otherwise."""
        script(dict(FISTS, text=self.NEW))
        vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
        for n, f0 in take_f0.items():
            made(f"{self.BASE}_take{n}", f0, seed=n)

    def test_pick_keeps_the_take_sent_the_rows_text_not_one_sent_an_older_tag(self):
        with sandbox(FakeApi()):
            self.start({1: 100.0, 2: 120.0})                    # take 1 is right on pitch, but sent the old tags
            sent(f"{self.BASE}_take1", self.OLD)
            sent(f"{self.BASE}_take2", self.NEW)
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes[self.BASE]["take"]), (0, f"{self.BASE}_take2"))
            self.assertIn(f"NOTE {self.BASE}_take1", out)       # and says which take it left out, and why
            self.assertIn(self.OLD, out)

    def test_pick_refuses_a_line_whose_only_take_was_sent_other_text(self):
        with sandbox(FakeApi()):
            self.start({1: 100.0})
            sent(f"{self.BASE}_take1", self.OLD)
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes), (1, {}))
            self.assertIn("1 more sent other text", out)

    def test_a_take_with_no_ledger_entry_still_counts(self):
        with sandbox(FakeApi()):
            self.start({1: 100.0})                              # a take placed by hand, or a test's stand-in
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes[self.BASE]["take"]), (0, f"{self.BASE}_take1"))

    def test_a_retake_is_planned_when_the_only_take_left_was_sent_other_text(self):
        """The reviewer's replay: keep a take, change the row's tags, unkeep, run --retake."""
        with sandbox(FakeApi()):
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            made(f"{self.BASE}_take1", 100.0, seed=1)
            sent(f"{self.BASE}_take1", self.OLD)
            picked("colossus")                                  # kept; its take goes to Media
            script(dict(FISTS, text=self.NEW))                  # the row's tags change
            with contextlib.redirect_stdout(io.StringIO()):
                vb.unkeep(self.BASE)                            # the old take comes back, to be chosen or retaken
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                vb.run("colossus", 2, set(), True, False, True)  # --retake --dry-run
            self.assertIn("colossus: 1 take(s)", out.getvalue())
            self.assertIn(f"{self.BASE}_take2", out.getvalue())
            self.assertIn("sent other text", out.getvalue())

    def test_a_take_sent_other_text_does_not_count_toward_the_takes_wanted(self):
        with sandbox(FakeApi()):
            self.start({1: 100.0})
            sent(f"{self.BASE}_take1", self.OLD)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                vb.run("colossus", 1, set(), False, False, True)  # one take wanted, dry run
            self.assertIn("colossus: 1 take(s)", out.getvalue())

    def test_check_does_not_pay_for_a_take_sent_other_text_and_says_nothing_passes(self):
        api = FakeApi()
        with sandbox(api):
            script(dict(FISTS, text=self.NEW))
            roles(colossus="voice_one")
            take = f"{self.BASE}_take1"
            write_wav(eleven.OUT / f"{take}.wav", tone(150.0, 1.2, seed=3))
            sent(take, self.OLD)
            hear(api, take, "Fists? Bold.")
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                stuck = vb.check("colossus", False)
            self.assertEqual(stuck, 1)
            self.assertEqual(sum(p["path"] == "/v1/speech-to-text" for p in api.posts), 0)
            self.assertIn(f"NOTE {take}", out.getvalue())
            self.assertIn(f"NO PASSING TAKE {self.BASE}", out.getvalue())


class StaleVerdictTest(unittest.TestCase):
    """VP2 re-review N1: a verdict stored beside a transcript goes stale when the comparison changes, and check only
    judges again the takes still in the repo (a shelved take is never heard again). So nothing trusts the stored
    verdict: the words are judged from the stored transcript whenever they are used, and check refreshes the
    stored verdicts of every take it knows."""

    def stale(self, api: FakeApi) -> tuple:
        """A line whose take Scribe misheard, with the pass an older, looser comparison stored beside it."""
        script(FISTS)
        roles(colossus="voice_one")
        take = "bossvoice/colossus/fists_take1"
        hear(api, take, "Fist? Old.")
        with contextlib.redirect_stdout(io.StringIO()):
            vb.run("colossus", 1, set(), False, False, False)
            vb.check("colossus", False)
        checks = vb._load(vb.CHECKS, {})
        checks[take]["ok"] = True
        vb._save(vb.CHECKS, checks)
        return take, vb.jobs("colossus")[0]

    def retakes(self) -> str:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            vb.run("colossus", 2, set(), True, False, True)
        return out.getvalue()

    def test_a_stored_pass_does_not_make_a_take_pass(self):
        api = FakeApi()
        with sandbox(api):
            take, job = self.stale(api)
            entry = vb._load(vb.CHECKS, {})[take]
            self.assertTrue(entry["ok"])
            self.assertFalse(vb.words_ok(job, entry))
            self.assertFalse(vb.fits(job, entry))
            self.assertFalse(vb.passes(job, entry))

    def test_the_retake_command_makes_a_new_take_when_the_stored_pass_is_stale(self):
        api = FakeApi()
        with sandbox(api):
            self.stale(api)
            self.assertIn("colossus: 1 take(s)", self.retakes())

    def test_the_picker_never_keeps_a_take_that_fails_todays_comparison(self):
        api = FakeApi()
        with sandbox(api):
            self.stale(api)
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes), (1, {}))
            self.assertFalse((eleven.OUT / "bossvoice" / "colossus" / "fists.wav").exists())

    def test_check_judges_the_stored_verdict_of_a_shelved_take_again(self):
        api = FakeApi()
        with sandbox(api):
            take, job = self.stale(api)
            vb._shelve([take])                                   # out of the repo: check never hears it again
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                vb.check("colossus", False)
            self.assertFalse(vb._load(vb.CHECKS, {})[take]["ok"])
            self.assertIn(take, out.getvalue())
            self.assertIn("stored verdict", out.getvalue())

    def test_an_accepted_take_still_passes(self):
        api = FakeApi()
        with sandbox(api):
            take, job = self.stale(api)
            with contextlib.redirect_stdout(io.StringIO()):
                vb.accept(take, "heard as fists by ear", "orchestrator")
            self.assertTrue(vb.words_ok(job, vb._load(vb.CHECKS, {})[take]))

    def test_a_check_without_a_transcript_falls_back_to_its_verdict(self):
        job = vb.Job("colossus", "fists", "bossvoice/colossus/fists", "colossus", "[dry amusement] Fists? ...Bold.")
        self.assertTrue(vb.words_ok(job, {"ok": True}))
        self.assertFalse(vb.words_ok(job, {"ok": False}))

    def test_the_retake_command_says_why_it_leaves_a_kept_line_whose_take_fails(self):
        api = FakeApi()
        with sandbox(api):
            take, job = self.stale(api)
            with contextlib.redirect_stdout(io.StringIO()):
                vb.accept(take, "heard as fists by ear", "orchestrator")
                vb.pick("colossus", False)                       # kept, on that acceptance
            checks = vb._load(vb.CHECKS, {})
            del checks[take]["accepted"]                          # as if nobody had decided: its take now fails
            vb._save(vb.CHECKS, checks)
            out = self.retakes()
            self.assertIn("colossus: 0 take(s)", out)
            self.assertIn("NOTE bossvoice/colossus/fists", out)
            self.assertIn("unkeep", out)

    def test_the_retake_command_says_when_a_line_has_takes_not_checked_yet(self):
        api = FakeApi()
        with sandbox(api):
            script(FISTS)
            roles(colossus="voice_one")
            hear(api, "bossvoice/colossus/fists_take1", "Fists? Bold.")
            with contextlib.redirect_stdout(io.StringIO()):
                vb.run("colossus", 1, set(), False, False, False)
            out = self.retakes()
            self.assertIn("colossus: 0 take(s)", out)
            self.assertIn("NOTE bossvoice/colossus/fists", out)
            self.assertIn("run check first", out)


class UnkeepTest(unittest.TestCase):
    def test_unkeep_brings_a_kept_fragments_takes_back_to_be_chosen_again(self):
        """The plan's redo paths: the kept file is a copy of a take shelved in Media, so unkeep removes the copy and
        its record and moves the job's takes back, unchanged, so check still trusts them and pick can choose again."""
        with sandbox(FakeApi()) as tmp:
            script(FISTS)
            vb._save(vb.PICKS, {"roles": {"colossus": {"voice_id": "v", "ref_f0": 100.0}}})
            base = "bossvoice/colossus/fists"
            made(f"{base}_take1", 100.0, seed=1)
            made(f"{base}_take2", 130.0, seed=2)
            picked("colossus")
            shelved = {p.name: p.read_bytes() for p in (tmp / "Media" / "takes" / "bossvoice" / "colossus").glob("*.wav")}
            vb.unkeep(base)
            self.assertFalse((eleven.OUT / f"{base}.wav").exists())
            self.assertNotIn(base, vb._load(vb.TAKES, {}))
            self.assertEqual(vb.takes_of(base), [f"{base}_take1", f"{base}_take2"])
            for name, data in shelved.items():
                self.assertEqual((eleven.OUT / "bossvoice" / "colossus" / name).read_bytes(), data)
            checks = vb._load(vb.CHECKS, {})
            self.assertTrue(all(vb.checked(checks, t) for t in vb.takes_of(base)))
            problems, takes, out = picked("colossus")
            self.assertEqual((problems, takes[base]["take"]), (0, f"{base}_take1"))


class ShelveTest(unittest.TestCase):
    def test_shelving_never_overwrites_a_take_already_in_media(self):
        with sandbox(FakeApi()) as tmp:
            name = "bossvoice/probe/seed_a"
            write_wav(eleven.OUT / f"{name}.wav", tone(150.0, 0.5, seed=1))
            vb._shelve([name])
            first = (tmp / "Media" / "takes" / f"{name}.wav").read_bytes()
            write_wav(eleven.OUT / f"{name}.wav", tone(150.0, 0.5, seed=2))
            moved = vb._shelve([name])
            self.assertEqual((tmp / "Media" / "takes" / f"{name}.wav").read_bytes(), first)
            self.assertTrue(Path(moved[name] + ".wav").exists())
            self.assertNotEqual(Path(moved[name] + ".wav").read_bytes(), first)
            self.assertFalse((eleven.OUT / f"{name}.wav").exists())


if __name__ == "__main__":
    unittest.main()
