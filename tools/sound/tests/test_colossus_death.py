"""The Colossus's kill line stays audible over its own death sound (1.1.0 final pass).

The kill line starts on death tick 5 (its take a few ticks earlier, by its lead-in) under the death sound, which cannot wait
for it or be ducked: the engine clamps an effect played at volume 2 to 6 to a gain of 1. So the sound itself is built quieter.
The rule is the voice production's: the words at least bossvoice's 3 LU over what plays under them, here measured over
the words' span with the death sound placed anywhere the line's file can start (death tick 0 to 5). Reads committed files.

    python -m unittest discover -s tools/sound/tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

import voicebatch as vb  # noqa: E402
from analysis import integrated  # noqa: E402
from dsp import at  # noqa: E402

DEATH_START_TICKS = (0, 1, 2, 3, 4, 5)       # where on the death sound the kill line's file starts, in game ticks


def sound(stem: str) -> np.ndarray:
    y, _ = sf.read(str(vb.ASSETS / "cosmicbreach" / "sounds" / f"{stem}.ogg"), always_2d=True)
    return y[:, 0]


class ColossusDeathTest(unittest.TestCase):
    def test_kill_line_is_three_lu_over_the_death_sound(self):
        line = next(l for l in vb.lines("colossus") if l["id"] == "kill")
        (key, event), = vb._events_of(line)
        voice, death = sound(event), sound("colossus/death")
        who, start, end = vb.speaker_spans(line, key, vb._parts(line, key))[0]
        words = integrated(voice[at(start):at(end)])
        for tick in DEATH_START_TICKS:
            shift = at(tick / 20.0)
            under = np.zeros(len(voice))
            n = min(len(voice), len(death) - shift)
            under[:n] = death[shift:shift + n]
            margin = words - integrated(under[at(start):at(end)])
            self.assertGreaterEqual(margin, vb.MARGIN_LU,
                                    f"the kill line is only {margin:.2f} LU over the death sound when its file starts on tick {tick}")


if __name__ == "__main__":
    unittest.main()
