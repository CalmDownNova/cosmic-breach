"""The Stable Crystal (Aetheria 1.1, task B2.7): the stow item for a tamed mount, empty and holding one.

Writes textures/item/stable_crystal.png (a pale crystal in a gold band, quiet) and stable_crystal_full.png (the same
shape lit from within, its heart warm white). 16x16 in the item style of gen_materials.py: hard alpha, a dark outline, light
from the top left.

Run:  python tools/art/gen_stable_crystal.py
"""
from __future__ import annotations

from armor_model import palette, sprite
from common import TEX, save_png

ROWS = [
    "................",
    ".......oo.......",
    "......olho......",
    "......olho......",
    ".....ollhso.....",
    ".....ollhso.....",
    "....olllhsso....",
    "...gGGyyGGGGg...",
    "...gGyyGGGGGg...",
    "....olllhsso....",
    ".....ollhso.....",
    ".....ollhso.....",
    "......olso......",
    "......olso......",
    ".......oo.......",
    "................",
]

# the same crystal with a warm heart through its middle (the silhouette stays, so the two read as one item in two states)
FULL_ROWS = [
    "................",
    ".......oo.......",
    "......olho......",
    "......olho......",
    ".....olyyso.....",
    ".....olyyso.....",
    "....olyyyyso....",
    "...gGGyyyyGGg...",
    "...gGyyyyGGGg...",
    "....olyyyyso....",
    ".....olyyso.....",
    ".....olyyso.....",
    "......olso......",
    "......olso......",
    ".......oo.......",
    "................",
]

EMPTY = palette({"o": "#1E3A4A", "l": "#A9D9E6", "h": "#E3F6FA", "s": "#6FA6B8",
                 "g": "#7A5A1E", "G": "#C9963A", "y": "#F2D27A"})
FULL = palette({"o": "#1E3A4A", "l": "#D6F7FF", "h": "#FFF4C2", "s": "#9FE0F2",
                "g": "#8A6420", "G": "#E0AE48", "y": "#FFE9A8"})


def main() -> None:
    for rows in (ROWS, FULL_ROWS):
        assert all(len(r) == 16 for r in rows) and len(rows) == 16
    save_png(sprite(ROWS, EMPTY), TEX / "item" / "stable_crystal.png")
    save_png(sprite(FULL_ROWS, FULL), TEX / "item" / "stable_crystal_full.png")
    print("wrote textures/item/stable_crystal.png and stable_crystal_full.png")


if __name__ == "__main__":
    main()
