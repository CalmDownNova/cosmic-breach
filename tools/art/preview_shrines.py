"""Contact sheets of the shrine renders (Aetheria 1.1, task B5), from the PNGs the Blender scripts wrote.

A named round (r1), files named by kind:
  python tools/art/preview_shrines.py <render dir> [kind ...]
writes sheet_<kind>.png (its clay views above its textured views) and, once all four are rendered, blind.png (each
shrine close and from 24 blocks, in a shuffled order labelled A to D only), blind_key.json and
tools/art/previews/shrines.png (the four front views side by side).

A blind round (r2 on), where no file names a boss:
  python tools/art/preview_shrines.py <render dir> --deal
picks neutral names, shrine_w to shrine_z, for the four kinds in an order shuffled by the folder's name, writes them to
key_do_not_open.txt in that folder and prints the --kinds and --names to give both Blender scripts;
  python tools/art/preview_shrines.py <render dir> --blind
then writes sheet_shrine_<w..z>.png, blind.png (columns W to Z, each close over 24 blocks) and
tools/art/previews/shrines.png, and scrubs the folder (see scrub()). The key stays in key_do_not_open.txt only.
"""
from __future__ import annotations

import json
import os
import random
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from common import PREVIEWS

KINDS = ["colossus", "leviathan", "unsung", "heliarch"]
CLAY = ["front", "side", "back", "three_quarter", "top", "far"]
LIT = ["front_day", "side_day", "back_day", "front_dusk", "far_day"]
CELL = 256
NEUTRAL = ["shrine_w", "shrine_x", "shrine_y", "shrine_z"]
KEY = "key_do_not_open.txt"


def tile(path):
    img = Image.open(path).convert("RGB")
    return img.resize((CELL, CELL), Image.NEAREST if img.width < CELL else Image.LANCZOS)


def row(paths):
    present = [p for p in paths if p.exists()]
    out = Image.new("RGB", (CELL * max(1, len(present)), CELL), (40, 40, 40))
    for i, p in enumerate(present):
        out.paste(tile(p), (i * CELL, 0))
    return out


def stack(images):
    out = Image.new("RGB", (max(i.width for i in images), sum(i.height for i in images)), (40, 40, 40))
    y = 0
    for i in images:
        out.paste(i, (0, y))
        y += i.height
    return out


def label_font():
    try:
        return ImageFont.load_default(size=28)
    except TypeError:  # Pillow before 10.1 has only the small bitmap font
        return ImageFont.load_default()


def sheet(src, name):
    stack([row([src / f"clay_{name}_{v}.png" for v in CLAY]), row([src / f"{name}_{v}.png" for v in LIT])]).save(src / f"sheet_{name}.png")


def blind_sheet(src, names, letters):
    blind = Image.new("RGB", (CELL * len(names), CELL * 2), (40, 40, 40))
    font = label_font()
    for i, (letter, name) in enumerate(zip(letters, names)):
        col = stack([tile(src / f"{name}_front_day.png"), tile(src / f"{name}_far_day.png")])
        ImageDraw.Draw(col).text((10, 6), letter, fill=(255, 255, 255), font=font, stroke_width=2, stroke_fill=(0, 0, 0))
        blind.paste(col, (i * CELL, 0))
    blind.save(src / "blind.png")


def deal(src):
    order = KINDS[:]
    random.Random(src.name).shuffle(order)
    kind_of = dict(zip(NEUTRAL, order))
    src.mkdir(parents=True, exist_ok=True)
    lines = [f"Blind round {src.name}: which shrine is which. Open this only after every match is written down.", ""]
    lines += [f"{n[-1].upper()} = {n} = {k}" for n, k in kind_of.items()]
    (src / KEY).write_text("\n".join(lines) + "\n", encoding="utf-8")
    name_of = {k: n for n, k in kind_of.items()}
    print(f"--kinds {' '.join(KINDS)} --names {' '.join(name_of[k] for k in KINDS)}")


def read_key(src):
    kind_of = {}
    for line in (src / KEY).read_text(encoding="utf-8").splitlines():
        m = re.match(r"^[W-Z] = (shrine_[w-z]) = (\w+)$", line.strip())
        if m:
            kind_of[m.group(1)] = m.group(2)
    if sorted(kind_of) != NEUTRAL or sorted(kind_of.values()) != sorted(KINDS):
        raise SystemExit(f"{src / KEY} does not map shrine_w to shrine_z onto the four kinds")
    return kind_of


def scrub(src):
    """Nothing in a blind round's folder may order the shrines: Blender stamps render times into its PNGs and the
    files' own times follow the render order (boss order), so every PNG is re-saved without its text chunks and every
    file gets the same modification time."""
    for p in src.glob("*.png"):
        with Image.open(p) as img:
            img.load()
            clean = img.copy()
        clean.save(p)
    stamp = max(p.stat().st_mtime for p in src.iterdir())
    for p in src.iterdir():
        os.utime(p, (stamp, stamp))


def blind_round(src):
    kind_of = read_key(src)
    missing = [f"{n}_{v}.png" for n in NEUTRAL for v in LIT if not (src / f"{n}_{v}.png").exists()]
    missing += [f"clay_{n}_{v}.png" for n in NEUTRAL for v in CLAY if not (src / f"clay_{n}_{v}.png").exists()]
    if missing:
        raise SystemExit(f"renders missing in {src}: {missing[:4]}")
    for n in NEUTRAL:
        sheet(src, n)
    blind_sheet(src, NEUTRAL, [n[-1].upper() for n in NEUTRAL])
    name_of = {k: n for n, k in kind_of.items()}
    row([src / f"{name_of[k]}_front_day.png" for k in KINDS]).save(PREVIEWS / "shrines.png")
    scrub(src)


def named_round(src, kinds):
    for kind in kinds:
        sheet(src, kind)
    if all((src / f"{k}_front_day.png").exists() and (src / f"{k}_far_day.png").exists() for k in KINDS):
        order = KINDS[:]
        random.Random(src.name).shuffle(order)
        blind_sheet(src, order, "ABCD")
        (src / "blind_key.json").write_text(json.dumps(dict(zip("ABCD", order)), indent=2) + "\n", encoding="utf-8")
        row([src / f"{k}_front_day.png" for k in KINDS]).save(PREVIEWS / "shrines.png")


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    src = Path(argv[0])
    if argv[1:] == ["--deal"]:
        deal(src)
        return 0
    if argv[1:] == ["--blind"]:
        blind_round(src)
    else:
        named_round(src, argv[1:] or KINDS)
    print(f"sheets in {src}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
