#!/usr/bin/env python3
"""
The colour of the world: replacements for the game's grass and foliage colormaps, a little
drained and a little colder, so every biome looks like late autumn under a grey sky. Not a
shader: the game tints grass and leaves from these maps itself, so it works with everything
(Sodium included) on every version.

    python3 tools/generate_colormaps.py

Writes src/main/resources/assets/minecraft/textures/colormap/{grass,foliage}.png. The maps are
indexed by temperature (x) and rainfall (y) as the game's are; the corners are the game's own
lush, cold and dry colours, graded.
"""
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/minecraft/textures/colormap"

# lush (hot and wet), cold, dry (hot and dry): close to the game's own corners
GRASS = ((0x4F, 0xC4, 0x3A), (0x80, 0xB4, 0x97), (0xBF, 0xB7, 0x55))
FOLIAGE = ((0x30, 0xBB, 0x0B), (0x60, 0xA1, 0x7B), (0xAE, 0xA4, 0x2A))

DESATURATE = 0.38      # how much of the colour goes
DARKEN = 0.86          # and how much of the light
COLD = np.array([-0.012, 0.0, 0.03])   # a grey-blue cast


def colormap(corners):
    lush, cold, dry = (np.array(c, np.float32) / 255.0 for c in corners)
    y, x = np.mgrid[0:256, 0:256].astype(np.float32) / 255.0
    a = x                                   # towards cold
    b = np.clip(y - x, 0.0, 1.0)            # towards dry
    c = np.clip(1.0 - a - b, 0.0, 1.0)      # lush
    rgb = c[..., None] * lush + a[..., None] * cold + b[..., None] * dry
    lum = (rgb @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    rgb = lum + (rgb - lum) * (1.0 - DESATURATE)
    rgb = rgb * DARKEN + COLD
    return Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8), "RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    colormap(GRASS).save(OUT / "grass.png")
    colormap(FOLIAGE).save(OUT / "foliage.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
