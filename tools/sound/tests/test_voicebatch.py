"""Tests for voicebatch.py, the boss voice production driver. Offline: no take, no network.

    python -m unittest discover -s tools/sound/tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402

import voicebatch as vb  # noqa: E402

HEAD = "| Boss | Line id | Trigger | Condition | Priority | Repeat rule | Text | Delivery notes | Mask split |\n|---|---|---|---|---|---|---|---|---|\n"
COLOSSUS = "| colossus | fists | weapon:bare_hands | none | 45 | once per fight | [dry amusement] Fists? ...Bold. | Dry. Budget 2.2 s. | none |\n"
RELAY = ("| unsung | open_solo | fight_start | players:solo, first | 90 | once per fight | Who comes to the choir without a song? "
         "| On the lifts. Fragment budget 1.1 s. | m1: [hushed] Who comes / m2: [hushed] to the choir / m3: [low] without a song? |\n")
PAIR = ("| unsung | one_gone | hp_threshold:67 | none | 95 | once per fight | Who will sing its part now? | Shaken. Budget 2.4 s. "
        "| first: [shaken] Who will sing / second: its part now? |\n")
ALONE = ("| unsung | alone | hp_threshold:33 | none | 95 | once per fight | I cannot... finish it... alone. | Halting. Budget 3.2 s. "
         "| alone: [halting] I cannot... finish it... alone. |\n")
SHIPPED = ("| heliarch | nova | hp_threshold:40 | none | 95 | once per fight | [rising fury] Burn with me! | Shipped take, kept. "
           "| none |\n")


class MarginTest(unittest.TestCase):
    """The decision after the VP2 review: every line at least 6 LU over its boss's fight music, both measured over the
    same span. A line can start anywhere in the music, so the words are held against the music's loudest stretch of
    the same length, as the client plays it (the repo's own music files)."""

    def test_the_loudest_stretch_of_a_looping_music_runs_across_its_seam(self):
        from analysis import integrated
        t = np.arange(int(6.0 * vb.SR)) / vb.SR
        r = np.random.default_rng(1)
        x = 0.01 * r.standard_normal(len(t))
        burst = (t < 0.5) | (t >= 5.5)                     # one loud second, across the loop's seam
        x[burst] = 0.2 * r.standard_normal(int(burst.sum()))
        loud = integrated(np.concatenate([x[t >= 5.5], x[t < 0.5]]))
        self.assertAlmostEqual(vb.loudest(vb.music_blocks(x, loop=True), 1.0, loop=True), loud, delta=0.5)
        self.assertLess(vb.loudest(vb.music_blocks(x, loop=False), 1.0, loop=False), loud - 1.0)   # split in two

    def test_the_search_starts_a_stretch_anywhere_to_50_ms_as_a_meter_would_read_it(self):
        """VP2 re-review N3: a search stepping 100 ms read every margin up to 0.1 LU too high. Every stretch starting
        on a 50 ms step is read as a meter reads it (400 ms blocks every 100 ms from its start, gated)."""
        from analysis import integrated
        r = np.random.default_rng(7)
        t = np.arange(int(5.0 * vb.SR)) / vb.SR
        x = 0.01 * r.standard_normal(len(t))
        burst = (t >= 2.05) & (t < 3.05)                        # one loud second, off the 100 ms grid
        x[burst] = 0.2 * r.standard_normal(int(burst.sum()))
        step = int(0.05 * vb.SR)
        for seconds in (1.0, 0.7):
            span = int(seconds * vb.SR)
            brute = max(integrated(x[a:a + span]) for a in range(0, len(x) - span + 1, step))
            self.assertAlmostEqual(vb.loudest(vb.music_blocks(x, loop=False), seconds, loop=False), brute, delta=0.02)

    def test_the_unsung_sings_as_the_client_plays_her(self):
        """On each line of the song every living mask starts a block: the singer's sung at full volume, the others
        humming at 0.85 (rising at 0.95 through a Harmonize warning), as UnsungMusic.java plays them."""
        stems = {}
        for voice in ("alto", "tenor", "bass"):
            for how in ("sung", "hum"):
                for chord in range(4):
                    stems[f"music/unsung/{voice}_{how}_{chord}"] = np.full(10, float(len(stems) + 1))
            stems[f"music/unsung/{voice}_rise"] = np.full(10, 100.0)
        block = vb.unsung_block("13", 1, "hum", 2, stems.__getitem__)
        expect = stems["music/unsung/alto_sung_2"] + 0.85 * stems["music/unsung/bass_hum_2"]
        self.assertTrue(np.allclose(block, expect))
        block = vb.unsung_block("123", 2, "rise", 0, stems.__getitem__)
        expect = stems["music/unsung/tenor_sung_0"] + 0.95 * 100.0 * 2
        self.assertTrue(np.allclose(block, expect))

    def test_a_margin_is_the_words_over_the_loudest_music_of_the_same_length(self):
        from analysis import integrated
        r = np.random.default_rng(2)
        t = np.arange(int(8.0 * vb.SR)) / vb.SR
        music = 0.02 * r.standard_normal(len(t))
        music[(t > 2.0) & (t < 3.0)] *= 4.0                     # its loudest second
        blocks = vb.music_blocks(music, loop=True)
        event = np.zeros(int(4.0 * vb.SR))
        event[int(1.0 * vb.SR):int(2.0 * vb.SR)] = 0.3 * r.standard_normal(int(1.0 * vb.SR))
        margin = vb.margin(event, 1.0, 2.0, [("loop", blocks)])
        self.assertAlmostEqual(margin["lu"], integrated(event[int(1.0 * vb.SR):int(2.0 * vb.SR)]) - vb.loudest(blocks, 1.0, loop=True), delta=0.01)
        self.assertEqual(margin["music"], "loop")
        self.assertGreater(vb.margin(event, 0.0, 4.0, [("loop", blocks)])["lu"], margin["lu"] - 6.0)


class DeathChordTest(unittest.TestCase):
    """The Unsung's choir stops at her death, but the one soft chord the server plays at death tick 24 is still ringing
    when the kill line starts at tick 36 (the script's placement), so that line is held over the chord, from that
    moment on, like every other line is over its music (the playback's duck is not counted on)."""

    def chord(self) -> np.ndarray:
        t = np.arange(int(5.0 * vb.SR)) / vb.SR
        noise = np.random.default_rng(5).standard_normal(len(t))
        return 0.1 * noise * np.where(t < vb.CHORD_LAG, 8.0, 1.0) * np.exp(-t / 2.0)      # loud before the line starts

    def test_only_the_chord_from_where_the_line_starts_is_under_it(self):
        from analysis import integrated
        chord = self.chord()
        (state,) = vb.death_states(lambda stem: chord)
        self.assertEqual((state.name, state.loop), ("the death chord", False))
        start = int(vb.CHORD_LAG * vb.SR)
        self.assertAlmostEqual(vb.loudest(state.blocks, 1.0, loop=False), integrated(chord[start:start + vb.SR]), delta=0.7)
        self.assertLess(vb.loudest(state.blocks, 1.0, loop=False), integrated(chord[:vb.SR]) - 6.0)    # not its first moment

    def test_a_margin_over_a_music_that_does_not_loop_keeps_to_inside_it(self):
        t = np.arange(int(6.0 * vb.SR)) / vb.SR
        r = np.random.default_rng(1)
        x = 0.01 * r.standard_normal(len(t))
        burst = (t < 0.5) | (t >= 5.5)                     # one loud second, split by the end of the music
        x[burst] = 0.2 * r.standard_normal(int(burst.sum()))
        blocks = vb.music_blocks(x, loop=False)
        event = 0.05 * r.standard_normal(int(2.0 * vb.SR))
        loops = vb.margin(event, 0.0, 1.0, [("burst", blocks)])                  # a plain pair: a loop, its seam counted
        once = vb.margin(event, 0.0, 1.0, [vb.Music("burst", blocks, False)])
        self.assertGreater(once["lu"], loops["lu"] + 0.5)

    def test_the_kill_line_is_heard_over_the_chord_every_other_line_over_its_own_music(self):
        kill = {"boss": "unsung", "id": "finished", "trigger": "boss_kill"}
        self.assertEqual([s.name for s in vb.music_for(kill, None)], ["the death chord"])

    def test_the_music_to_duck_is_named_by_sound_event_id(self):
        """VP2 re-review N4: the handoff's duck entries are sound event ids, namespace included like every `event`, and
        every one is defined in sounds.json (the choir's 27 stems are listed, not a wildcard)."""
        import json
        defined = json.loads((vb.ASSETS / "cosmicbreach" / "sounds.json").read_text(encoding="utf-8"))
        kill = {"boss": "unsung", "id": "finished", "trigger": "boss_kill"}
        choir = vb.duck_of({"boss": "unsung", "id": "robes", "trigger": "armor:mod_full_set"})
        self.assertEqual(vb.duck_of(kill), ["cosmicbreach:unsung/last_chord"])
        self.assertEqual(vb.duck_of({"boss": "unsung", "id": "open_solo", "trigger": "fight_start"}), [])   # no choir yet
        self.assertEqual(len(choir), 27)
        for stem in ("alto_sung_0", "tenor_hum_3", "bass_rise"):
            self.assertIn(f"cosmicbreach:music/unsung/{stem}", choir)
        self.assertEqual(vb.duck_of({"boss": "colossus", "id": "fists", "trigger": "weapon:bare_hands"}),
                         ["cosmicbreach:music/colossus"])
        self.assertEqual(vb.duck_of({"boss": "leviathan", "id": "fork", "trigger": "weapon:trident"}),
                         ["cosmicbreach:music/leviathan"])
        heliarch = vb.duck_of({"boss": "heliarch", "id": "intro", "trigger": "fight_start"})
        self.assertEqual(heliarch, ["cosmicbreach:music/heliarch_regent", "cosmicbreach:music/heliarch_hollow",
                                    "cosmicbreach:music/heliarch_collapse"])
        for event in choir + heliarch + vb.duck_of(kill) + ["cosmicbreach:music/colossus", "cosmicbreach:music/leviathan"]:
            namespace, _, key = event.partition(":")
            self.assertEqual(namespace, "cosmicbreach")
            self.assertIn(key, defined, event)

    def test_an_opener_is_heard_over_the_first_notes_not_the_choir(self):
        """The openers are intro lines: each mask's first note sounds at its lift, one beat before that mask's fragment
        (Unsung.introTick), and her choir does not start until the fight does, after the last fragment (UnsungMusic).
        So the music under an opener is the three first notes, each placed at its lift."""
        stems = {f"unsung/first_{v}": np.full(vb.SR, float(i + 1)) for i, v in enumerate(("alto", "tenor", "bass"))}
        starts = [0.1, 1.3, 2.5]                                        # an opener's fragments, two beats apart
        under = vb.opener_notes(starts, int(5.0 * vb.SR), stems.__getitem__)
        self.assertEqual(float(under[int(0.2 * vb.SR)]), 1.0)             # the Alto's note rang from 0.0 (0.5 s early)
        self.assertEqual(float(under[int(0.6 * vb.SR)]), 1.0)             # the Tenor's lift is not until 0.7 s
        self.assertEqual(float(under[int(0.9 * vb.SR)]), 1.0 + 2.0)       # both ring at 0.9 s
        self.assertEqual(float(under[int(1.95 * vb.SR)]), 3.0)            # the Bass's lift is at 1.9 s; the others are done
        self.assertEqual(float(under[int(4.5 * vb.SR)]), 0.0)

    def test_an_openers_margin_is_read_over_the_notes_where_they_sound(self):
        from analysis import integrated
        r = np.random.default_rng(3)
        event = np.zeros(int(4.0 * vb.SR))
        event[:vb.SR] = 0.1 * r.standard_normal(vb.SR)                  # the words, 0.0 to 1.0 s
        under = np.zeros(len(event))
        under[:vb.SR] = 0.02 * r.standard_normal(vb.SR)                 # a note 14 dB under them, then silence
        m = vb.aligned_margin(event, 0.0, 1.0, under, "the first notes")
        self.assertAlmostEqual(m["lu"], integrated(event[:vb.SR]) - integrated(under[:vb.SR]), delta=0.01)
        self.assertEqual(m["music"], "the first notes")
        self.assertGreater(m["lu"], 13.0)

    def test_margins_name_the_fragments_the_peak_cap_holds_below_their_mask(self):
        """A mask whose own peaks need more than the cap is held below its loudness (bossvoice.unsung_parts): its
        margin is what its peaks allow, and `margins` says which fragments those are."""
        self.assertEqual(vb.held_fragments({"masks": [1, 2, 3], "held": [0.0, 3.2, 0.02]}), [("mask 2", 3.2)])
        self.assertEqual(vb.held_fragments({"words": None}), [])

    def test_the_production_margin_is_3_lu_because_the_playback_ducks_the_music_6_dB(self):
        """The decision after the VP2 re-review: at least 3 LU over the music under a line in the files, 9 LU or more
        once the playback pulls the music down 6 dB."""
        self.assertEqual(vb.MARGIN_LU, 3.0)


class LongLineTest(unittest.TestCase):
    """VP3: the Leviathan's scheduler guarantees a quiet window of 50 ticks after an attack, and its director needs a
    line's speech (speech_ticks - speech_start_ticks) plus 10 ticks of it: 40 ticks, 2.0 s, of speech. A line that
    speaks longer is marked long in the handoff, so the playback only plays it in a coil pause, the opening or the kill."""

    GAP = {"boss": "leviathan", "id": "fork", "delivery": "Mock offence. Quiet window only. Budget 1.95 s."}
    OPENER = {"boss": "leviathan", "id": "open_solo", "delivery": "Delighted. Intro tick 40. Budget 5.0 s."}

    def test_the_window_is_the_speech_plus_the_gate(self):
        self.assertEqual((vb.GAP_TICKS, vb.GATE_TICKS), (50, 10))
        self.assertEqual(vb.speech_window(3, 43), 50)
        self.assertEqual(vb.speech_window(2, 80), 88)

    def test_a_line_is_long_only_when_its_window_is_over_what_the_scheduler_guarantees(self):
        self.assertFalse(vb.is_long(3, 43))        # 40 ticks of speech: 2.0 s, a window of exactly 50
        self.assertTrue(vb.is_long(3, 44))         # one tick more
        self.assertFalse(vb.is_long(2, 41))
        self.assertTrue(vb.is_long(2, 100))

    def test_the_guaranteed_window_is_the_one_the_leviathans_scheduler_keeps(self):
        """VP3 review M2: GAP_TICKS restates LeviathanMoves.GAP (the ticks between the end of one attack and the choice of
        the next), so a change to the fight's pacing cannot leave every long flag silently wrong. GATE_TICKS is the
        director's rule 7 (the script: the speech plus 10 ticks); the code has no constant for it yet."""
        import re
        src = (vb.REPO / "src" / "main" / "java" / "com" / "cosmicbreach" / "guardian" / "leviathan"
               / "LeviathanMoves.java").read_text(encoding="utf-8")
        m = re.search(r"public static final int GAP = (\d+);", src)
        self.assertIsNotNone(m, "LeviathanMoves.GAP moved or was renamed: tie GAP_TICKS to it again")
        self.assertEqual(vb.GAP_TICKS, int(m.group(1)), "the fight's gap changed: every long flag has to be judged again")

    def test_only_the_leviathans_lines_carry_the_flag_and_the_window_each_needs(self):
        """VP3 review F1: the window a line needs is written down, counted both ways, so the playback does not work it
        out again its own way. Counted from the words (the plan's definition, what `long` is judged by) it is the speech
        plus the gate; counted from the file's start, which holds 2 to 4 ticks of lead before the words, it is more, and a
        director that gates on that number needs the file to start a window's lead earlier than the window."""
        self.assertEqual(vb.gap_fields(self.GAP, 3, 44), {"long": True, "window_ticks": 51, "file_window_ticks": 54})
        self.assertEqual(vb.gap_fields(self.GAP, 3, 43), {"long": False, "window_ticks": 50, "file_window_ticks": 53})
        for boss in ("colossus", "unsung", "heliarch"):
            self.assertEqual(vb.gap_fields({"boss": boss, "id": "x", "delivery": ""}, 3, 90), {})

    def test_a_quiet_window_line_that_is_long_is_a_problem_any_other_long_line_is_not(self):
        self.assertIn("41 ticks", vb.gap_problem(self.GAP, 3, 44))                  # its speech, 3 to 44
        self.assertIsNone(vb.gap_problem(self.GAP, 3, 43))
        self.assertIsNone(vb.gap_problem(self.OPENER, 3, 130))                      # an opener is long by design
        self.assertIsNone(vb.gap_problem({"boss": "colossus", "id": "x", "delivery": "Quiet window only."}, 3, 90))


class ScriptTest(unittest.TestCase):
    def test_parses_every_kind_of_row(self):
        rows = vb.parse_script("# Lines\n\n" + HEAD + COLOSSUS + RELAY + PAIR + ALONE + SHIPPED)
        self.assertEqual([r["id"] for r in rows], ["fists", "open_solo", "one_gone", "alone", "nova"])
        fists, relay, pair, alone, nova = rows
        self.assertEqual(fists["words"], "Fists? ...Bold.")
        self.assertEqual(fists["budget"], 2.2)
        self.assertIsNone(fists["split"])
        self.assertEqual(relay["split"]["kind"], "relay")
        self.assertEqual(relay["fragment_budget"], 1.1)
        self.assertEqual(pair["split"]["kind"], "pair")
        self.assertEqual(alone["split"]["kind"], "alone")
        self.assertTrue(nova["shipped"])

    def test_refuses_fragments_that_do_not_make_the_caption(self):
        bad = RELAY.replace("without a song?", "with a song?", 1)       # the caption, not the fragment
        with self.assertRaises(ValueError):
            vb.parse_script(HEAD + bad)

    def test_refuses_an_unknown_trigger_a_dash_and_a_missing_budget(self):
        for bad in (COLOSSUS.replace("weapon:bare_hands", "weapon:spoon"), COLOSSUS.replace("Bold.", "Bold \u2014 very."),
                    COLOSSUS.replace(" Budget 2.2 s.", "")):
            with self.assertRaises(ValueError):
                vb.parse_script(HEAD + bad)

    def test_ignores_other_tables(self):
        other = "| Column | Format |\n|---|---|\n| Boss | x |\n"
        self.assertEqual(len(vb.parse_script(other + "\n" + HEAD + COLOSSUS)), 1)


class JobTest(unittest.TestCase):
    def setUp(self):
        self.relay, self.pair, self.alone = vb.parse_script(HEAD + RELAY + PAIR + ALONE)

    def test_each_fragment_hears_its_neighbours_without_their_tags(self):
        jobs = vb.unsung_jobs(self.relay, whole=False)
        self.assertEqual([j.base for j in jobs], ["bossvoice/unsung/open_solo/f1_m1", "bossvoice/unsung/open_solo/f2_m2",
                                                  "bossvoice/unsung/open_solo/f3_m3"])
        middle = jobs[1]
        self.assertEqual((middle.role, middle.text, middle.prev, middle.next),
                         ("unsung_m2", "[hushed] to the choir", "Who comes", "without a song?"))
        self.assertEqual(middle.budget, 1.1)

    def test_a_pair_line_needs_both_fragments_in_every_voice_that_can_hold_them(self):
        self.assertEqual(vb.needs(self.pair), [(1, 1), (1, 2), (2, 2), (2, 3)])
        self.assertEqual(vb.needs(self.alone), [(1, 1), (1, 2), (1, 3)])

    def test_whole_mode_asks_each_mask_for_the_whole_sentence(self):
        jobs = vb.unsung_jobs(self.relay, whole=True)
        self.assertEqual([j.base for j in jobs], [f"bossvoice/unsung/open_solo/whole_m{m}" for m in (1, 2, 3)])
        self.assertEqual(jobs[0].text, "[hushed] Who comes [hushed] to the choir [low] without a song?")
        self.assertEqual(vb.unsung_jobs(self.alone, whole=True)[0].base, "bossvoice/unsung/alone/f1_m1")

    def test_the_colossus_budget_counts_its_slower_playback(self):
        job = vb.Job("colossus", "fists", "bossvoice/colossus/fists", "colossus", "x", budget=2.2)
        self.assertTrue(vb.fits(job, {"ok": True, "speech_s": 2.0}))
        self.assertFalse(vb.fits(job, {"ok": True, "speech_s": 2.1}))       # 2.29 s once 9% slower
        self.assertFalse(vb.fits(job, {"ok": False, "speech_s": 1.0}))


class PitchTest(unittest.TestCase):
    def test_finds_the_fundamental_of_a_harmonic_tone(self):
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        for f in (90.0, 140.0, 210.0):
            x = 0.3 * sum((0.6 ** k) * np.sin(2 * np.pi * f * (k + 1) * t) for k in range(8))
            self.assertAlmostEqual(vb.f0_median(x), f, delta=f * 0.02)

    def test_silence_has_no_pitch(self):
        self.assertEqual(vb.f0_median(np.zeros(vb.SR)), 0.0)

    def test_a_whisper_has_no_pitch(self):
        """Noise ringing through vowel formants, the way eleven_v4 renders a [hushed] fragment: periodic enough at the
        first formant to pass an autocorrelation test (it used to read as 300 to 390 Hz), but not voiced."""
        from scipy import signal
        from dsp import rbj
        r = np.random.default_rng(7)
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        noise = r.standard_normal(len(t))
        for first in (300.0, 380.0, 450.0):
            x = np.zeros(len(t))
            for f, q, g in ((first, 10.0, 1.0), (1200.0, 6.0, 0.6), (2600.0, 5.0, 0.3)):
                b, a = rbj("bandpass", f, q)
                x += g * signal.lfilter(b, a, noise)
            x *= 0.5 - 0.5 * np.cos(2 * np.pi * 3.0 * t)                  # three syllables a second
            self.assertEqual(vb.f0_median(0.3 * x / np.max(np.abs(x))), 0.0)

    def test_a_few_voiced_frames_in_a_whisper_are_not_its_pitch(self):
        """A whispered take with a 60 ms voiced blip (a few frames of a tone in 1.5 s, as eleven_v4's whispered
        fragments have): too little voicing to name a pitch, which used to come from those few frames alone."""
        from scipy import signal
        from dsp import rbj
        r = np.random.default_rng(5)
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        noise = r.standard_normal(len(t))
        x = np.zeros(len(t))
        for f, q, g in ((600.0, 8.0, 1.0), (1700.0, 6.0, 0.6), (2600.0, 5.0, 0.3)):
            b, a = rbj("bandpass", f, q)
            x += g * signal.lfilter(b, a, noise)
        x = 0.3 * x / np.max(np.abs(x))
        blip = (t > 0.70) & (t < 0.76)
        x[blip] += 0.3 * sum((0.6 ** k) * np.sin(2 * np.pi * 140.0 * (k + 1) * t[blip]) for k in range(8))
        self.assertEqual(vb.f0_median(x), 0.0)

    def test_a_voice_above_300_hz_keeps_its_pitch(self):
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        for f in (350.0, 380.0):
            x = 0.3 * sum((0.6 ** k) * np.sin(2 * np.pi * f * (k + 1) * t) for k in range(8))
            self.assertAlmostEqual(vb.f0_median(x), f, delta=f * 0.02)

    def test_a_rising_line_is_not_cut_off_at_300_hz(self):
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        phase = 2 * np.pi * np.cumsum(200.0 + 140.0 * t / 1.5) / vb.SR          # 200 Hz rising to 340 Hz
        x = 0.3 * sum((0.6 ** k) * np.sin((k + 1) * phase) for k in range(8))
        self.assertAlmostEqual(vb.f0_median(x), 270.0, delta=10.0)

    def test_a_breathy_voice_keeps_its_pitch(self):
        t = np.arange(int(1.5 * vb.SR)) / vb.SR
        x = 0.3 * sum((0.6 ** k) * np.sin(2 * np.pi * 140.0 * (k + 1) * t) for k in range(8))
        x = x + 0.25 * np.std(x) * np.random.default_rng(3).standard_normal(len(t))     # breath 12 dB under
        self.assertAlmostEqual(vb.f0_median(x), 140.0, delta=140.0 * 0.02)


if __name__ == "__main__":
    unittest.main()
