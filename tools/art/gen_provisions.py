"""The levels' provisions (1.1, the Twilight Forest rule): food, Starhide, Rime Thread, the two pickaxes and the Umbral Cap.

Item textures 16x16 in textures/item/, and the Umbral Cap's block texture in textures/block/ (the item shows the same
picture). Crisp pixel art in the Meridian and materials style: ASCII sprites, a dark outline, light from the top left.

    halo_berries           three glowing cyan berries on a strand of Halo Moss
    lumen_venison          a pale pink cut with a white fat band; seared_lumen_venison the same cut browned
    manta_fillet           a tilted blue-grey fillet with flake seams, a scaled skin strip and its tail; seared_manta_fillet
                           the same, golden brown, with two grill marks
    starhide               a pale cream pelt (neck, four legs, a tail) with gold star specks (the stags' and the mantas' leather)
    rime_thread            a spool of ice-blue thread wound in bands, a loose end trailing from it
    starsteel_pickaxe      a pickaxe with a pale silver-gold head (Starsteel), Driftwood handle
    nebulite_pickaxe       the same shape, cyan over violet (Nebulite)
    block/umbral_cap       a violet mushroom with a pale glowing rim under its cap

Run:  python tools/art/gen_provisions.py
"""
from __future__ import annotations

from blockart import sprite
from common import TEX, rel, save_png

HALO_BERRIES = [
    "................",
    "........oo......",
    ".......o11o.....",
    "........o1o.....",
    "....oo...o1o....",
    "...o45o..o1o....",
    "..o4w54o.o1oo...",
    "..o5543o.o1o4o..",
    "...o32o.o45w54o.",
    "....oo..o55543o.",
    ".....oo..o332o..",
    "....o45o..ooo...",
    "...o4w54o.......",
    "...o5543o.......",
    "....o32o........",
    ".....oo.........",
]
BERRY = {"o": "#0f3a44", "1": "#3d8f8a", "2": "#1f7f97", "3": "#33aac4", "4": "#7fe6f0", "5": "#4fd0e6", "w": "#e8ffff"}

CUT = [
    "................",
    "................",
    ".....oooooo.....",
    "...oo433333oo...",
    "..o4433333332o..",
    ".o44w33333332o..",
    ".o4w3333331332o.",
    ".o433333111332o.",
    ".o433333111322o.",
    "..o43333333322o.",
    "..offff33332o...",
    "...offfff22o....",
    "....ooffffo.....",
    "......oooo......",
    "................",
    "................",
]
RAW_VENISON = {"o": "#4a1c22", "1": "#e8a9ae", "2": "#a8424d", "3": "#c95a66", "4": "#e47a85", "w": "#ffd2d6", "f": "#f4ece2"}
SEARED_VENISON = {"o": "#3a1d12", "1": "#d99a63", "2": "#6e3a1f", "3": "#94532d", "4": "#c27a43", "w": "#efc28f", "f": "#e8c99a"}

# The level 2 food: a fillet of the stingray, tilted, with its tail on (so it reads as fish, not as a feather or a claw):
# 'o' outline, '5' to '3' the flesh lit from the top left, '2' (raw) the flake seams, 'g' (seared) the grill marks,
# 's' 'S' 'k' the scaled skin along the belly and into the tail.
FILLET = [
    "................",
    "................",
    "..........ooo...",
    "........oo555o..",
    "......oo554445o.",
    "oo..oo55424424o.",
    "ksoo5544442442o.",
    "osko54244442435o",
    ".osk5442443333ko",
    "..os5443233SsSko",
    ".osko533SsSskko.",
    "osko.okSsSkkoo..",
    "kko...okkkoo....",
    "oo.....ooo......",
    "................",
    "................",
]
SEARED_FILLET_ROWS = [
    "................",
    "................",
    "..........ooo...",
    "........oo555o..",
    "......oo554g45o.",
    "oo..oo5g4444g4o.",
    "ksoo5544g4444go.",
    "osko54444g444g5o",
    ".osk544444g333ko",
    "..os5443333SsSko",
    ".osko533SsSskko.",
    "osko.okSsSkkoo..",
    "kko...okkkoo....",
    "oo.....ooo......",
    "................",
    "................",
]
RAW_FILLET = {"o": "#26323d", "5": "#e8f1f7", "4": "#c3d3e0", "3": "#9db3c4", "2": "#6a85a0", "s": "#4f6a82", "S": "#bcd0df", "k": "#2f4558"}
SEARED_FILLET = {"o": "#3a2412", "5": "#ffe2a4", "4": "#efbf70", "3": "#cf9446", "g": "#6a3a18", "s": "#8e5a2a", "S": "#d29a52", "k": "#5c3719"}

# Starhide: a pale pelt (neck, four legs, a tail), light from the top left, a darker belly, gold star specks
STARHIDE = [
    "......oooo......",
    ".....o5553o.....",
    ".oo.o544443o.oo.",
    "o53o54444433o53o",
    "o34544g44333532o",
    ".o54444433g332o.",
    ".o344443333332o.",
    "..o5443333332o..",
    "..o54g333g322o..",
    ".o543333332223o.",
    ".o53333g322222o.",
    "o5331333222g223o",
    "o531o132221o122o",
    "o11o.o1221o.o11o",
    ".oo...o11o...oo.",
    ".......oo.......",
]
HIDE = {"o": "#3a3025", "5": "#fcf8ee", "4": "#f1e8d3", "3": "#dfd2b4", "2": "#c2b28f", "1": "#94846a", "g": "#f4c14a"}

# Rime Thread: a spool of ice-blue thread wound in bands, pale caps, a loose thread trailing from it
RIME_THREAD = [
    "................",
    "................",
    "oooooooooooo....",
    "o5544444433o....",
    "o4322222211o....",
    "..o433332o......",
    "..o222111o......",
    "..o433332c4.....",
    "..o222111oo34...",
    "..o433332o.oo3..",
    "..o222111o...o4o",
    "o5544444433o.o3o",
    "o4322222211o..o.",
    "oooooooooooo....",
    "................",
    "................",
]
THREAD = {"o": "#1d3b52", "1": "#3f7fa6", "2": "#6db7d6", "3": "#9fd8ee", "4": "#cdeefa", "5": "#f1fbff", "c": "#e9f6fb"}

PICKAXE = [
    "................",
    "....ooooooo.....",
    "...o5554443o....",
    "..o44ooo33322o..",
    "..o3o..oo3322o..",
    "..oo..o.o3322o..",
    ".......oho322o..",
    "......ohho.o2o..",
    ".....ohho...oo..",
    "....ohho........",
    "...ohho.........",
    "..ohho..........",
    ".ohho...........",
    ".oho............",
    "..o.............",
    "................",
]
HANDLE = {"h": "#9aa3ad"}  # petrified Driftwood: pale grey
STARSTEEL = {"o": "#3b3226", "2": "#766a51", "3": "#a2956f", "4": "#c9bd97", "5": "#faf6ea", **HANDLE}
NEBULITE = {"o": "#1b1540", "2": "#3b3192", "3": "#6650d2", "4": "#5a96ea", "5": "#c6f7ff", **HANDLE}

# The stem's foot sits on the last row: the cross model draws a block texture's bottom edge on the floor, so an empty
# last row floats the plant one pixel above it (vanilla's mushrooms and flowers all reach row 15).
UMBRAL_CAP = [
    "................",
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "...oo445544oo...",
    "..o4453355344o..",
    ".o445333333544o.",
    ".o433333333334o.",
    ".oggggggggggggo.",
    "..oooo2112oooo..",
    "......o12o......",
    "......o12o......",
    "......o12o......",
    ".....o1112o.....",
    ".....oooooo.....",
]
CAP = {"o": "#160a24", "1": "#d8cce8", "2": "#a493bd", "3": "#5a2f86", "4": "#7d4bb0", "5": "#a77ad6", "g": "#c8a7ff"}

ITEMS = {
    "halo_berries": (HALO_BERRIES, BERRY),
    "lumen_venison": (CUT, RAW_VENISON),
    "seared_lumen_venison": (CUT, SEARED_VENISON),
    "manta_fillet": (FILLET, RAW_FILLET),
    "seared_manta_fillet": (SEARED_FILLET_ROWS, SEARED_FILLET),
    "starhide": (STARHIDE, HIDE),
    "rime_thread": (RIME_THREAD, THREAD),
    "starsteel_pickaxe": (PICKAXE, STARSTEEL),
    "nebulite_pickaxe": (PICKAXE, NEBULITE),
}


def main():
    for name, (rows, palette) in ITEMS.items():
        p = save_png(sprite(rows, palette), TEX / "item" / f"{name}.png")
        print("wrote", rel(p))
    p = save_png(sprite(UMBRAL_CAP, CAP), TEX / "block" / "umbral_cap.png")
    print("wrote", rel(p))


if __name__ == "__main__":
    main()
