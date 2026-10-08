"""Tests for bossvoice.py (the boss voice chains and the Unsung's stitching) and boss 4's new lines in heliarch.py.
The chains render from stand-in takes copied into a temporary folder (never the repo's eleven/ folder), so they are
tested before a single credit is spent.

    python -m unittest discover -s tools/sound/tests -v
"""
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402

import bossvoice  # noqa: E402
import eleven  # noqa: E402
import heliarch  # noqa: E402

PAIR = {"boss": "unsung", "id": "one_gone", "trigger": "hp_threshold:67", "words": "Who will sing its part now?",
        "split": {"kind": "pair", "fragments": [{"tag": "first", "text": "Who will sing"},
                                                {"tag": "second", "text": "its part now?"}]}}
ALONE = {"boss": "unsung", "id": "alone", "trigger": "hp_threshold:33", "words": "I cannot... finish it... alone.",
         "split": {"kind": "alone", "fragments": [{"tag": "alone", "text": "I cannot... finish it... alone."}]}}
KILL = {"boss": "unsung", "id": "finished", "trigger": "boss_kill", "words": "The hymn is finished, at last.",
        "split": {"kind": "relay", "fragments": [{"tag": "m1", "text": "[whispers] The hymn"},
                                                 {"tag": "m2", "text": "[whispers, quickly] is finished,"},
                                                 {"tag": "m3", "text": "[whispers, at peace] at last."}]}}
LINES = [
    {"boss": "colossus", "id": "shatter", "trigger": "hp_threshold:0", "words": "All three... or none.", "split": None},
    {"boss": "leviathan", "id": "fork", "trigger": "weapon:trident", "words": "A fork? I am no fish.", "split": None},
    PAIR,
    KILL,
]
STAND_INS = {                                  # kept file to make: shipped take to copy
    "bossvoice/colossus/shatter": "heliarch/nova",
    "bossvoice/leviathan/fork": "heliarch/player_down",
    "leviathan/song_take1": "leviathan/song_take1",
    "bossvoice/unsung/one_gone/f1_m1": "heliarch/nova",
    "bossvoice/unsung/one_gone/f1_m2": "heliarch/nova",
    "bossvoice/unsung/one_gone/f2_m2": "heliarch/player_down",
    "bossvoice/unsung/one_gone/f2_m3": "heliarch/player_down",
    "bossvoice/unsung/finished/f1_m1": "heliarch/nova",
    "bossvoice/unsung/finished/f2_m2": "heliarch/player_down",
    "bossvoice/unsung/finished/f3_m3": "heliarch/nova",
}


class StitchTest(unittest.TestCase):
    def test_half_beat_grid_leaves_at_least_60_ms(self):
        self.assertEqual(bossvoice.onsets([0.50, 0.70, 0.40], "half"), [0.0, 0.6, 1.5])

    def test_lift_grid_is_two_beats_unless_a_fragment_runs_over(self):
        self.assertEqual(bossvoice.onsets([0.8, 0.9, 1.0], "lift"), [0.0, 1.2, 2.4])
        self.assertEqual(bossvoice.onsets([1.3, 0.5], "lift"), [0.0, 1.36])

    def test_beat_grid(self):
        self.assertEqual(bossvoice.onsets([0.5, 0.2, 0.6], "beat"), [0.0, 0.6, 1.2])

    def test_a_slight_overlap_lets_a_fragment_start_on_the_next_beat(self):
        self.assertEqual(bossvoice.onsets([0.7, 0.7, 0.5], "beat"), [0.0, 1.2, 2.4])
        self.assertEqual(bossvoice.onsets([0.7, 0.7, 0.5], "beat", gap=-bossvoice.MAX_OVERLAP), [0.0, 0.6, 1.2])
        self.assertEqual(bossvoice.onsets([1.3, 1.0, 0.5], "lift", gap=-bossvoice.MAX_OVERLAP), [0.0, 1.2, 2.4])

    def test_an_overlap_lets_the_masks_words_meet_by_about_150_ms_whatever_the_trim(self):
        """The decision's "up to about 150 ms", read as 160 ms at most."""
        self.assertAlmostEqual(bossvoice.MAX_OVERLAP, 0.16)
        self.assertAlmostEqual(bossvoice.overlap_gap(False), -(0.16 + 0.03 + 0.12))
        self.assertAlmostEqual(bossvoice.overlap_gap(True), -(0.16 + 0.01 + 0.04))

    def test_a_take_cut_close_to_its_words_is_padded_so_the_overlap_holds(self):
        """eleven_v4 often ends a take on its last sound (and may start on its first): the trim pads the fragment
        with silence to PAD, so the overlap gap lets two masks' words meet by MAX_OVERLAP at most, never by the
        padding the take lacked as well (a 0.9 s take used to overlap the next by 0.3 s)."""
        sr = bossvoice.SR
        t = np.arange(int(0.9 * sr)) / sr
        x = 0.3 * np.sin(2 * np.pi * 150.0 * t)                  # sound from its first sample to its last
        for whispered in (False, True):
            pre, post = bossvoice.PAD[whispered]
            below = (30.0, 30.0) if whispered else (40.0, 45.0)
            piece = bossvoice.trim_fragment(x, whispered)
            self.assertAlmostEqual(len(piece) / sr, pre + 0.9 + post, delta=0.002)
            gap = bossvoice.overlap_gap(whispered)
            starts = bossvoice.onsets([len(piece) / sr] * 2, "half", gap)
            first_end = bossvoice.last_sound(piece, below[1])
            second_start = starts[1] + bossvoice.first_onset(piece, below[0])
            self.assertLessEqual(first_end - second_start, bossvoice.MAX_OVERLAP + 0.02)

    def test_the_build_places_a_line_with_the_overlap_production_chose(self):
        saved = bossvoice.STITCH
        tmp = Path(tempfile.mkdtemp())
        try:
            bossvoice.STITCH = tmp / "voice_stitch.json"
            self.assertEqual(bossvoice.gap_of("voice_falls"), bossvoice.MIN_GAP)
            bossvoice.STITCH.write_text(json.dumps({"voice_falls": {"gap": -0.15}}), encoding="utf-8")
            self.assertEqual(bossvoice.gap_of("voice_falls"), -0.15)
            self.assertEqual(bossvoice.gap_of("one_gone"), bossvoice.MIN_GAP)
        finally:
            bossvoice.STITCH = saved
            shutil.rmtree(tmp, ignore_errors=True)

    def test_lockstep_starts_every_fragment_on_its_own_beat(self):
        self.assertEqual(bossvoice.onsets([0.9, 0.9, 0.9], "lockstep"), [0.0, 0.6, 1.2])

    def test_a_whispered_fragment_is_trimmed_to_its_words(self):
        """A whisper's breath before and after its words would eat the beat: the trim keeps the words."""
        from scipy import signal
        from dsp import rbj
        r = np.random.default_rng(4)
        t = np.arange(int(1.2 * bossvoice.SR)) / bossvoice.SR
        x = np.zeros(len(t))
        for f, q, g in ((700.0, 6.0, 1.0), (1800.0, 5.0, 0.6), (2800.0, 5.0, 0.4)):
            b, a = rbj("bandpass", f, q)
            x += g * signal.lfilter(b, a, r.standard_normal(len(t)))
        words = (t > 0.30) & (t < 0.72)                                   # 0.42 s of whispered words
        breath = ((t > 0.20) & (t <= 0.30)) | ((t >= 0.72) & (t < 0.90))  # breath 30 dB under them
        x = 0.3 * x / np.max(np.abs(x)) * (words + 0.03 * breath)
        plain, whisper = bossvoice.trim_fragment(x), bossvoice.trim_fragment(x, whispered=True)
        self.assertLessEqual(len(whisper) / bossvoice.SR, 0.42 + 0.08)
        self.assertLess(len(whisper), len(plain) - 0.08 * bossvoice.SR)
        self.assertLessEqual(len(whisper) / bossvoice.SR, bossvoice.BEAT - bossvoice.MIN_GAP)   # the next beat stays free

    def test_survivors_take_the_fragments_in_song_order(self):
        self.assertEqual(bossvoice.assign(PAIR, "13"), [(1, 1), (2, 3)])
        self.assertEqual(bossvoice.assign(PAIR, "23"), [(1, 2), (2, 3)])
        self.assertEqual(bossvoice.assign(ALONE, "2"), [(1, 2)])


class ChainsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp())
        for name, src in STAND_INS.items():
            (cls.tmp / name).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(eleven.OUT / f"{src}.wav", cls.tmp / f"{name}.wav")
        script = cls.tmp / "voice_script.json"
        script.write_text(json.dumps({"source": "test", "lines": LINES}), encoding="utf-8")
        cls.saved = (eleven.OUT, bossvoice.SCRIPT)
        eleven.OUT, bossvoice.SCRIPT = cls.tmp, script
        for f in (bossvoice.colossus_parts, bossvoice.leviathan_parts, bossvoice.unsung_parts):
            f.cache_clear()

    @classmethod
    def tearDownClass(cls):
        eleven.OUT, bossvoice.SCRIPT = cls.saved
        for f in (bossvoice.colossus_parts, bossvoice.leviathan_parts, bossvoice.unsung_parts):
            f.cache_clear()
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def sound(self, x):
        self.assertTrue(np.all(np.isfinite(x)))
        self.assertGreater(np.max(np.abs(x)), 0.01)

    def test_the_colossus_is_slower_and_splits_into_three_on_the_shatter(self):
        p = bossvoice.colossus_parts("shatter")
        self.assertIn("split", p)
        take = eleven.sample("bossvoice/colossus/shatter")
        ratio = len(bossvoice.words_of(take, down=bossvoice.COLOSSUS_DOWN)) / len(bossvoice.words_of(take))
        self.assertAlmostEqual(ratio, bossvoice.COLOSSUS_DOWN, delta=0.01)
        self.sound(bossvoice.render(p))

    def test_the_leviathan_sings_under_its_words(self):
        p = bossvoice.leviathan_parts("fork")
        self.assertEqual(set(p), {"words", "chorus", "bed", "room"})
        self.sound(bossvoice.render(p))

    def test_check_and_the_build_measure_a_sped_up_line_alike(self):
        import voicebatch
        saved = bossvoice.TEMPO
        try:
            bossvoice.TEMPO = self.tmp / "voice_tempo.json"
            bossvoice.TEMPO.write_text(json.dumps({"leviathan/fork": {"tempo": 1.1}}), encoding="utf-8")
            bossvoice.leviathan_parts.cache_clear()
            job = voicebatch.Job("leviathan", "fork", "bossvoice/leviathan/fork", "leviathan", "A fork? I am no fish.",
                                 budget=2.4)
            x = eleven.sample("bossvoice/leviathan/fork")
            a, b = bossvoice.spoken_span(voicebatch.built_words(job, x))
            c, d = bossvoice.spoken_span(bossvoice.leviathan_parts("fork")["words"])
            self.assertAlmostEqual(b - a, d - c, delta=0.002)
            e, f = bossvoice.spoken_span(bossvoice.words_of(x))
            self.assertAlmostEqual((b - a) * 1.1, f - e, delta=0.06)
        finally:
            bossvoice.TEMPO = saved
            bossvoice.leviathan_parts.cache_clear()

    def test_a_line_over_the_music_holds_each_mask_at_its_own_loudness_less_what_its_peaks_need(self):
        from analysis import integrated
        from dsp import at
        p = bossvoice.unsung_parts("one_gone", "13")
        for (k, mask), start, held in zip(bossvoice.assign(PAIR, "13"), p["starts"], p["held"]):
            piece = bossvoice.fragment("one_gone", k, mask)
            words = p["words"][at(start):at(start) + len(piece)]
            self.assertAlmostEqual(integrated(words), bossvoice.SPEAKER_LUFS[mask] - held, delta=0.2)
        ev = next(e for e in bossvoice._events() if e.name == "bossvoice/unsung_one_gone_13")
        self.assertTrue(ev.keep_level)

    def test_the_kill_lines_ghosts_are_held_at_their_loudness_over_the_death_chord(self):
        """Her choir has stopped at the death, but the soft chord still rings when the ghosts start: each is levelled
        like every mask, to GHOST_LUFS, and the build keeps that level."""
        from analysis import integrated
        from dsp import at
        p = bossvoice.unsung_parts("finished", "123")
        for (k, mask), start, held in zip(bossvoice.assign(KILL, "123"), p["starts"], p["held"]):
            piece = bossvoice.fragment("finished", k, mask)
            words = p["words"][at(start):at(start) + len(piece)]
            self.assertAlmostEqual(integrated(words), bossvoice.GHOST_LUFS[mask] - held, delta=0.2)
        ev = next(e for e in bossvoice._events() if e.name == "bossvoice/unsung_finished")
        self.assertTrue(ev.keep_level)

    def test_every_unsung_event_fits_the_cap_without_being_held_down(self):
        """VP2 re-review N2: each fragment is held below its mask's loudness just far enough that the mixed event needs
        no more than the cap from the build's limiter, so nothing is limited harder and no line is turned down whole."""
        import build
        events = [e for e in bossvoice._events() if e.name.startswith("bossvoice/unsung_")]
        self.assertEqual(len(events), 4)
        for ev in events:
            y, reduction, limited = build.set_level(build.master(ev.variants[0](), ev), ev)
            self.assertLessEqual(reduction, bossvoice.LIMIT_MAX_DB + 1e-6, ev.name)
            self.assertFalse(limited, ev.name)

    def test_a_spiky_fragment_is_held_below_its_mask_and_a_calm_one_is_not(self):
        from fake_api import tone
        from analysis import integrated
        from dsp import at
        calm, spiky = tone(150.0, 0.9, seed=5), tone(150.0, 0.9, seed=6)
        spiky[int(0.4 * bossvoice.SR)] += 2.5                   # a plosive's burst, far over its words
        pieces, real = {(1, 1): calm, (2, 3): spiky}, bossvoice.fragment
        bossvoice.fragment = lambda line_id, k, mask: pieces[(k, mask)]
        bossvoice.unsung_parts.cache_clear()
        try:
            p = bossvoice.unsung_parts("one_gone", "13")
        finally:
            bossvoice.fragment = real
            bossvoice.unsung_parts.cache_clear()
        self.assertEqual(p["held"][0], 0.0)
        self.assertGreater(p["held"][1], 3.0)
        first = p["words"][at(p["starts"][0]):at(p["starts"][0]) + len(calm)]
        self.assertAlmostEqual(integrated(first), bossvoice.SPEAKER_LUFS[1], delta=0.2)     # the calm one is at its mask's level

    def test_two_survivors_share_the_sentence_on_the_half_beat(self):
        half = bossvoice.BEAT / 2.0
        for living in ("12", "13", "23"):
            p = bossvoice.unsung_parts("one_gone", living)
            self.assertEqual(p["masks"], [int(living[0]), int(living[1])])
            steps = (p["starts"][1] - p["starts"][0]) / half
            self.assertAlmostEqual(steps, round(steps), delta=1e-6)
            self.sound(bossvoice.render(p))

    def test_the_colossus_and_the_leviathan_events_keep_room_for_the_encoder(self):
        events = {e.name: e for e in bossvoice._events()}
        for name in ("bossvoice/colossus_shatter", "bossvoice/leviathan_fork"):
            self.assertEqual(events[name].headroom_db, bossvoice.ENCODER_HEADROOM_DB, name)
        for name, ev in events.items():
            if name.startswith("bossvoice/unsung_"):
                self.assertEqual(ev.headroom_db, 0.0, name)         # hers are held by keep_level's own headroom

    def test_events_exist_only_for_kept_takes(self):
        names = {e.name for e in bossvoice._events()}
        self.assertEqual(names, {"bossvoice/colossus_shatter", "bossvoice/leviathan_fork", "bossvoice/unsung_one_gone_12",
                                 "bossvoice/unsung_one_gone_13", "bossvoice/unsung_one_gone_23",
                                 "bossvoice/unsung_finished"})


class TempoTest(unittest.TestCase):
    """The decision for the Leviathan: up to 1.15x tempo before any text is cut, its pitch kept, applied before the
    limit check and recorded per line (eleven/voice_tempo.json)."""

    def test_a_stretch_keeps_the_pitch_and_shortens_by_the_tempo(self):
        from fake_api import tone
        import voicebatch
        x = tone(150.0, 2.0, seed=3)
        y = bossvoice.stretch(x, 1.15)
        self.assertAlmostEqual(len(y) / len(x), 1 / 1.15, delta=0.005)
        self.assertAlmostEqual(voicebatch.f0_median(y), voicebatch.f0_median(x), delta=1.5)
        self.assertIs(bossvoice.stretch(x, 1.0), x)

    def test_the_tempo_is_read_per_line_and_only_the_leviathan_may_have_one(self):
        saved = bossvoice.TEMPO
        tmp = Path(tempfile.mkdtemp())
        try:
            bossvoice.TEMPO = tmp / "voice_tempo.json"
            self.assertEqual(bossvoice.tempo_of("leviathan", "fork"), 1.0)
            bossvoice.TEMPO.write_text(json.dumps({"leviathan/fork": {"tempo": 1.1}}), encoding="utf-8")
            self.assertEqual((bossvoice.tempo_of("leviathan", "fork"), bossvoice.tempo_of("leviathan", "crab")), (1.1, 1.0))
            self.assertEqual(bossvoice.TEMPO_MAX, {"leviathan": 1.15})
        finally:
            bossvoice.TEMPO = saved
            shutil.rmtree(tmp, ignore_errors=True)


class LoudnessTest(unittest.TestCase):
    """The decisions after the VP2 review and re-review: the Unsung's lines that play over her music are levelled mask
    by mask (SPEAKER_LUFS), loud enough to sit 3 LU over the choir (the playback pulls the music down 6 dB more), no
    limiter takes more than 4 dB off a line, and the build keeps each mask's level instead of setting the whole line to
    one loudness."""

    def test_a_fragment_is_set_to_its_masks_loudness_and_nothing_is_limited(self):
        """VP2 re-review N2: the fast limiter that shaved a fragment's spikes (up to 10.6 dB, 10 ms release) is gone: a
        fragment is its coloured self at its mask's loudness, and the one limiter left on it is the build's."""
        from fake_api import tone
        from analysis import integrated
        x = tone(150.0, 1.0, seed=2)
        x[int(0.5 * bossvoice.SR)] += 0.9                       # one spike, far over the words
        y = bossvoice.level_fragment(x, -15.0)
        self.assertAlmostEqual(integrated(y), -15.0, delta=0.05)
        c = bossvoice.colour(x)
        self.assertTrue(np.allclose(y, c * (np.max(np.abs(y)) / np.max(np.abs(c))), atol=1e-9))   # only scaled

    def test_no_limiter_in_the_chains_takes_more_than_the_cap(self):
        """The decision after the VP2 re-review: no limiter takes more than 4 dB off a line. Every limiter the chains
        call is capped at LIMIT_MAX_DB, and the build's is capped at the same for a spoken line."""
        import build
        from fake_api import tone
        seen, real = [], bossvoice.limiter

        def spy(x, ceiling, **kw):
            seen.append(kw.get("max_reduction_db", 6.0))
            return real(x, ceiling, **kw)

        x = tone(150.0, 1.5, seed=3)
        x[int(0.7 * bossvoice.SR)] += 2.0
        bossvoice.limiter = spy
        try:
            bossvoice.words_of(x)
            bossvoice.trim_fragment(x)
            bossvoice.trim_fragment(x, whispered=True)
        finally:
            bossvoice.limiter = real
        self.assertEqual(len(seen), 3)
        self.assertEqual(max(seen), 4.0)
        self.assertEqual((bossvoice.LIMIT_MAX_DB, bossvoice.HOLD_MAX_DB), (4.0, 4.0))
        self.assertEqual(build.SPEECH_LIMIT_DB, bossvoice.LIMIT_MAX_DB)
        self.assertEqual(bossvoice.KEEP_CEILING_DB, build.CEILING_DBTP - build.KEEP_HEADROOM_DB)

    def test_the_build_takes_at_most_the_cap_off_a_spoken_line(self):
        import build
        from event import Event
        t = np.arange(int(2.0 * bossvoice.SR)) / bossvoice.SR
        x = 0.05 * np.sin(2 * np.pi * 220.0 * t)
        x[int(1.0 * bossvoice.SR)] = 1.0                        # a spike 26 dB over the body
        for keep in (True, False):
            speech = Event("x", "x", [], length=5.0, level=0.0, speech=True, keep_level=keep)
            y, reduction, limited = build.set_level(x * (3.0 if keep else 1.0), speech)   # a kept level is not scaled
            self.assertLessEqual(reduction, build.SPEECH_LIMIT_DB + 1e-6)
            self.assertTrue(limited)                            # the cap held: the line is turned down whole, not squashed
        y, reduction, limited = build.set_level(x, Event("x", "x", [], length=5.0, level=0.0))
        self.assertGreater(reduction, build.SPEECH_LIMIT_DB)    # any other sound may still take more

    def test_a_boss_line_keeps_room_under_the_ceiling_for_the_encoder(self):
        """VP3: Vorbis lifted the Colossus's and the Leviathan's loudest lines 0.2 dB over the build's -1 dBTP ceiling
        once encoded, and the plan wants every voice line at or under -1 dBTP after encoding. Their events hold
        ENCODER_HEADROOM_DB more room before encoding; any other event, by default, holds none."""
        import build
        from event import Event
        from analysis import true_peak
        t = np.arange(int(2.0 * bossvoice.SR)) / bossvoice.SR
        x = 0.05 * np.sin(2 * np.pi * 220.0 * t)
        x[int(1.0 * bossvoice.SR)] = 1.0                        # a spike far over the body: the ceiling decides
        plain = Event("x", "x", [], length=5.0, level=0.0, speech=True)
        roomy = Event("x", "x", [], length=5.0, level=0.0, speech=True, headroom_db=bossvoice.ENCODER_HEADROOM_DB)
        self.assertEqual(plain.headroom_db, 0.0)
        self.assertGreater(bossvoice.ENCODER_HEADROOM_DB, 0.0)
        a, _, _ = build.set_level(x, plain)
        b, _, _ = build.set_level(x, roomy)
        self.assertLessEqual(true_peak(b), 10 ** ((build.CEILING_DBTP - bossvoice.ENCODER_HEADROOM_DB) / 20.0) + 1e-6)
        self.assertAlmostEqual(20.0 * np.log10(true_peak(a) / true_peak(b)), bossvoice.ENCODER_HEADROOM_DB, delta=0.05)

    def test_a_mask_is_set_to_its_level_over_the_choir_or_over_the_death_chord(self):
        """Every Unsung line is levelled by mask. Her choir stops at the death, so the kill line is over the soft chord
        that rings under its ghosts instead: they are set to GHOST_LUFS, quieter than the masks over the choir, which is
        louder than that chord (voicebatch.py margins holds both at least MARGIN_LU over the music)."""
        for mask in (1, 2, 3):
            self.assertEqual(bossvoice.speaker_lufs({"trigger": "hp_threshold:67"}, mask), bossvoice.SPEAKER_LUFS[mask])
            self.assertEqual(bossvoice.speaker_lufs({"trigger": "boss_kill"}, mask), bossvoice.GHOST_LUFS[mask])
            self.assertGreater(bossvoice.SPEAKER_LUFS[mask], bossvoice.GHOST_LUFS[mask])

    def test_the_build_keeps_a_levelled_voice_at_its_level(self):
        import build
        from event import Event
        from analysis import integrated, true_peak
        ev = Event("x", "x", [], length=5.0, level=0.0, speech=True, keep_level=True)
        t = np.arange(int(2.0 * bossvoice.SR)) / bossvoice.SR
        quiet = 0.05 * np.sin(2 * np.pi * 220.0 * t)
        y, reduction, limited = build.set_level(quiet, ev)
        self.assertAlmostEqual(integrated(y), integrated(quiet), delta=0.01)
        self.assertFalse(limited)
        y, reduction, limited = build.set_level(quiet * 30.0, ev)       # over the ceiling: held, never clipped,
        ceiling = build.CEILING_DBTP - build.KEEP_HEADROOM_DB              # with room for the encoder's overshoot
        self.assertLessEqual(true_peak(y), 10 ** (ceiling / 20.0) + 1e-6)


class CouplingTest(unittest.TestCase):
    """voicebatch.py keeps its own copies so it can run without importing the build's sets: they must match."""

    def test_the_driver_and_the_chains_agree(self):
        import voicebatch
        self.assertEqual(voicebatch.STRETCH["colossus"], bossvoice.COLOSSUS_DOWN)
        self.assertEqual(voicebatch.WHISPER.pattern, bossvoice.WHISPER.pattern)
        self.assertEqual(voicebatch.WHISPER.flags, bossvoice.WHISPER.flags)


class SameMeasureTest(unittest.TestCase):
    """voicebatch's take check measures the words exactly as the build places them."""

    def test_boss_4s_take_check_sees_the_words_its_build_places(self):
        from dsp import at
        heliarch.voice_parts.cache_clear()
        words = heliarch.clean_words(eleven.sample("heliarch/player_down"))
        placed = heliarch.voice_parts("player_down")["words"]
        self.assertTrue(np.array_equal(placed[at(0.15):at(0.15) + len(words)], words))

    def test_an_unsung_fragment_is_trimmed_for_the_check_as_for_the_stitch(self):
        from dsp import at
        piece = bossvoice.trim_fragment(eleven.sample("heliarch/nova"))
        dry, starts = bossvoice.stitch([piece], "half")
        self.assertTrue(np.array_equal(dry[at(starts[0]):at(starts[0]) + len(piece)], piece))


class HeliarchSeedsTest(unittest.TestCase):
    def test_the_six_shipped_lines_keep_their_seeds(self):
        for n in heliarch.LINES:
            self.assertEqual(heliarch._seed(n), 2600 + sorted(heliarch.LINES).index(n))

    def test_new_lines_never_borrow_a_shipped_seed(self):
        shipped = {heliarch._seed(n) for n in heliarch.LINES}
        for n in ("open_group", "nova_again", "give_it", "dim_me"):
            self.assertNotIn(heliarch._seed(n), shipped)
            self.assertTrue(2700 <= heliarch._seed(n) < 3700)


class PeakRuleTest(unittest.TestCase):
    """VP3 review M3, finished in VP4: every spoken line decodes at or under the build's -1 dBTP ceiling once it is
    encoded, except the four lines shipped before 1.1.0 that decode a little over it (-0.87 to -0.99 dBTP). Those are on
    players' disks and must stay byte for byte, so the build accepts them by name and no other. Boss 4's 24 new lines
    keep ENCODER_HEADROOM_DB more room, as the Colossus's and the Leviathan's do."""

    SHIPPED_OVER = {"heliarch/voice_collapse", "heliarch/voice_death", "echo/first_shard", "echo/ring_open"}

    @staticmethod
    def row(**changes):
        row = dict(stem="bossvoice/colossus_x", seconds=2.0, wav_samples=88200, ogg_samples=88200, peak=-3.0,
                   true_peak=-1.5, lufs=-16.0, target=-16.0, limited=False, reduction=0.0, edges=(-90.0, -90.0, -90.0),
                   loop=False, length=14.0, speech=True)
        row.update(changes)
        return row

    def test_a_spoken_line_over_the_ceiling_is_a_warning(self):
        import build
        self.assertEqual(build.warnings_for(self.row(true_peak=-1.0)), [])        # at the ceiling is fine
        self.assertEqual(build.warnings_for(self.row(true_peak=-1.2)), [])
        found = build.warnings_for(self.row(true_peak=-0.9))
        self.assertEqual(len(found), 1)
        self.assertIn("-0.90", found[0])

    def test_only_spoken_lines_are_held_to_it(self):
        import build
        self.assertEqual(build.warnings_for(self.row(true_peak=-0.9, speech=False)), [])     # an effect may sit at -0.9

    def test_exactly_four_shipped_lines_are_accepted_by_name(self):
        import build
        self.assertEqual(set(build.SHIPPED_OVER_CEILING), self.SHIPPED_OVER)
        for stem in self.SHIPPED_OVER:
            self.assertEqual(build.warnings_for(self.row(stem=stem, true_peak=-0.9)), [], stem)
        self.assertEqual(len(build.warnings_for(self.row(stem="heliarch/voice_arrows", true_peak=-0.9))), 1)

    def test_the_accepted_four_are_shipped_spoken_events_that_keep_building_as_they_did(self):
        import build
        by_name = {e.name: e for e in build.EVENTS}
        for stem in self.SHIPPED_OVER:
            self.assertIn(stem, by_name)
            self.assertTrue(by_name[stem].speech, stem)
            self.assertEqual(by_name[stem].headroom_db, 0.0, stem)                # no headroom: same level, same bytes

    def test_the_build_reports_each_rows_speech_flag(self):
        import build
        from event import Event
        t = np.arange(int(1.0 * bossvoice.SR)) / bossvoice.SR
        for speech in (True, False):
            ev = Event("peakrule/x", "x", [lambda: 0.2 * np.sin(2 * np.pi * 220.0 * t)], length=2.0, level=0.0, speech=speech)
            saved = build.SOUNDS, build.WORK
            tmp = Path(tempfile.mkdtemp())
            try:
                build.SOUNDS, build.WORK = tmp / "sounds", tmp / "work"
                rows = build.build_event(ev)
            finally:
                build.SOUNDS, build.WORK = saved
                shutil.rmtree(tmp, ignore_errors=True)
            self.assertEqual(rows[0]["speech"], speech)

    def test_boss_4s_new_lines_keep_room_for_the_encoder_and_the_shipped_six_do_not(self):
        new = [n for n in heliarch.ALL_LINES if n not in heliarch.LINES]
        self.assertEqual(len(new), 24)
        self.assertEqual(len(heliarch.LINES), 6)
        for name in heliarch.ALL_LINES:
            ev = heliarch._voice_event(name)
            self.assertEqual(ev.headroom_db, 0.0 if name in heliarch.LINES else bossvoice.ENCODER_HEADROOM_DB, name)
            self.assertTrue(ev.speech, name)


if __name__ == "__main__":
    unittest.main()
