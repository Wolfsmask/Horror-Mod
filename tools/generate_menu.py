#!/usr/bin/env python3
"""
Paints the title screen and the in-game atmosphere textures.

    pip install numpy pillow
    python3 tools/generate_menu.py

Writes into src/main/resources/assets/occupant/textures/gui/:

    title_forest.png   a night wood, layer by layer, under a sick moon        (1024 x 512)
    title_fog.png      a band of mist that drifts across it, tiles sideways   (512 x 128)
    title_figure.png   it, far back between the trees, almost nothing         (64 x 128)
    title_logo.png     THE OCCUPANT, in worn pixel letters                      (512 x 96)
    vignette.png       the dark that closes in from the edges                  (256 x 256)

Everything is generated, so it can be changed by changing this file and nothing else.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/occupant/textures/gui"
OUT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(1666)


def forest():
    w, h = 1024, 512
    y = np.linspace(0.0, 1.0, h)[:, None]
    # A sky that is not quite black: a low bruised glow near the horizon.
    sky = np.zeros((h, w, 3), np.float32)
    sky[..., 0] = 6 + 22 * y ** 2.2
    sky[..., 1] = 7 + 16 * y ** 2.4
    sky[..., 2] = 12 + 22 * y ** 2.0
    img = Image.fromarray(sky.clip(0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img, "RGBA")

    # The moon, low and dirty, half behind the trees.
    for r, a in ((58, 10), (40, 18), (26, 40)):
        d.ellipse((760 - r, 120 - r, 760 + r, 120 + r), fill=(150, 150, 140, a))
    d.ellipse((744, 104, 776, 136), fill=(178, 176, 160, 255))

    # Rows of trunks, far to near: each row darker, wider, and less in the fog.
    rows = [(330, 260, (26, 30, 38), 3, 7, 70), (360, 300, (17, 19, 25), 5, 12, 46),
            (400, 340, (9, 10, 13), 9, 22, 26), (440, 380, (3, 3, 5), 16, 38, 14)]
    for ground, crown, colour, wmin, wmax, count in rows:
        xs = np.sort(rng.uniform(-40, w + 40, count))
        for x in xs:
            tw = rng.uniform(wmin, wmax)
            lean = rng.uniform(-6, 6)
            top = crown - rng.uniform(150, 330)
            d.polygon([(x - tw / 2, h), (x + tw / 2, h), (x + tw * 0.35 + lean, top), (x - tw * 0.35 + lean, top)],
                      fill=colour + (255,))
            # A few dead branches.
            for _ in range(rng.integers(1, 4)):
                by = rng.uniform(top, ground - 40)
                dirn = rng.choice([-1, 1])
                length = rng.uniform(14, 60) * (wmax / 20)
                d.line([(x, by), (x + dirn * length, by - length * rng.uniform(0.3, 0.8))],
                       fill=colour + (255,), width=max(1, int(tw / 5)))
        # The ground for this row, and mist lying on it.
        d.rectangle((0, ground + 60, w, h), fill=colour + (255,))
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    # Grain, baked in.
    a = np.asarray(img).astype(np.int16)
    a += rng.integers(-5, 6, size=a.shape[:2])[..., None]
    Image.fromarray(a.clip(0, 255).astype(np.uint8)).save(OUT / "title_forest.png")


def fog():
    w, h = 512, 128
    # Soft noise, wrapped sideways so it scrolls forever without a seam.
    base = rng.random((h // 8, w // 8))
    base = np.concatenate([base, base[:, :1]], axis=1)
    img = Image.fromarray((base * 255).astype(np.uint8)).resize((w + 8, h), Image.BICUBIC).crop((0, 0, w, h))
    img = img.filter(ImageFilter.GaussianBlur(6))
    a = np.asarray(img).astype(np.float32) / 255.0
    yy = np.linspace(0, 1, h)[:, None]
    band = np.exp(-((yy - 0.55) / 0.28) ** 2)                 # thickest across the middle
    alpha = (a * 0.8 + 0.2) * band * 150
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., :3] = (120, 126, 134)
    rgba[..., 3] = alpha.clip(0, 255).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(OUT / "title_fog.png")


def figure():
    """It, very small and very far back: a pale face over a thin dark shape on long thin legs."""
    w, h = 64, 128
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = 32
    # Legs, from the body out to the ground, thin and pale and too many.
    for i, (dx, top) in enumerate([(-26, 64), (-18, 58), (-11, 70), (-5, 66), (5, 62), (12, 72), (19, 60), (27, 67)]):
        kx = cx + dx * 0.55
        ky = top - 14
        d.line([(cx + (i - 4) * 0.6, top), (kx, ky), (cx + dx, h - 2)], fill=(150, 140, 132, 230), width=1)
    # The body, as thin as paper, under the hair.
    d.rectangle((cx - 2, 34, cx + 2, 72), fill=(30, 18, 16, 255))
    # Hair, down past the shoulders.
    d.polygon([(cx - 7, 14), (cx + 7, 14), (cx + 8, 50), (cx - 8, 50)], fill=(46, 26, 22, 255))
    # The face: long, pale, mostly mouth.
    d.rectangle((cx - 4, 16, cx + 3, 36), fill=(200, 184, 172, 255))
    d.point([(cx - 2, 20), (cx + 1, 20)], fill=(8, 5, 5, 255))
    d.rectangle((cx - 1, 24, cx, 34), fill=(10, 6, 6, 255))
    d.point([(cx - 1, 34), (cx, 34)], fill=(112, 40, 36, 255))
    img.save(OUT / "title_figure.png")


def logo():
    w, h = 512, 96
    # Draw small, then blow up without smoothing: blocky, like the rest of the game.
    small = Image.new("L", (w // 4, h // 4), 0)
    d = ImageDraw.Draw(small)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    text = "THE OCCUPANT"
    tw = d.textlength(text, font=font)
    d.text(((w // 4 - tw) / 2, 2), text, fill=255, font=font)
    big = np.asarray(small.resize((w, h), Image.NEAREST)).astype(np.float32) / 255.0
    # Worn: pixels knocked out of the letters, and a few runs dripping down from them.
    holes = rng.random(big.shape) < 0.05
    big[holes] *= 0.15
    for _ in range(26):
        ys, xs = np.nonzero(big > 0.5)
        if len(xs) == 0:
            break
        i = rng.integers(len(xs))
        x, y = xs[i], ys[i]
        length = rng.integers(4, 22)
        big[y:min(h, y + length), x:x + 4] = np.maximum(big[y:min(h, y + length), x:x + 4],
                                                       np.linspace(0.9, 0.0, min(h, y + length) - y)[:, None])
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., 0] = 206
    rgba[..., 1] = 196
    rgba[..., 2] = 184
    rgba[..., 3] = (big * 255).clip(0, 255).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(OUT / "title_logo.png")


def vignette():
    s = 256
    yy, xx = np.mgrid[0:s, 0:s] / (s - 1) * 2 - 1
    r = np.sqrt(xx ** 2 + (yy * 1.05) ** 2)
    a = np.clip((r - 0.45) / 0.85, 0, 1) ** 1.6
    rgba = np.zeros((s, s, 4), np.uint8)
    rgba[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(OUT / "vignette.png")


def main():
    forest()
    fog()
    figure()
    logo()
    vignette()
    print("menu textures written to", OUT)


if __name__ == "__main__":
    main()
