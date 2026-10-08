"""What the build needs to know about each sound event."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, List

import numpy as np


@dataclass
class Event:
    name: str                                  # "combat/swing_light": folder/event under sounds/
    subtitle: str                              # plain words for the subtitle line
    variants: List[Callable[[], np.ndarray]]   # one renderer per file (_1, _2, ...)
    length: float                              # longest allowed duration in seconds
    level: float                               # loudness target in LU relative to combat/hit
    fade_out: float = 0.02                     # fade length at the (possibly trimmed) end
    loop: bool = False                         # seamless loop: never trimmed or faded
    quality: int = 5                           # libvorbis -q:a (a loop gets more, see README)
    speech: bool = False                       # a spoken line: `level` is its gated programme loudness, not the loudest 100 ms
    keep_level: bool = False                   # a voice levelled speaker by speaker (bossvoice): the build only holds its peaks
    headroom_db: float = 0.0                   # room left under the true-peak ceiling before encoding (Vorbis lifts a peak a little)

    def file_stems(self) -> List[str]:
        """Names of the files for this event, without extension, relative to sounds/."""
        if len(self.variants) == 1:
            return [self.name]
        return [f"{self.name}_{i + 1}" for i in range(len(self.variants))]
