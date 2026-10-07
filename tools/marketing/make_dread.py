#!/usr/bin/env python3
"""
The gallery, the way the icon is made: not film stills but bad photographs of something that
should not be there. Every image starts as a real frame of the game with the mod (from the client
game test, docs/client-test), cropped round it, then made cold, bloodless and smeared: crushed
blacks, a ghost of it as if it moved while the shutter was open, colour fringing, grain, faint
scanlines, and the dark closing in at the edges. Where its eyes are big enough, a pinpoint of
light deep in each: it is looking at you.

    python3 tools/marketing/make_dread.py      (needs numpy and pillow)

Writes docs/modrinth/gallery-dread/.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/client-test"
OUT = ROOT / "docs/modrinth/gallery-dread"
W, H = 1920, 1080

# name, source, crop (x0, y0, width) in source pixels (16:9), exposure, warmth kept, tilt, eyes
# (each eye: x, y in source pixels, and the glint's size), seed
# (black point percentile) and whether the game's crosshair (in the middle of the source) is painted out.
SHOTS = [
    ("01-behind-you", "found-behind", (560, 230, 800), 1.0, 0.0, 4, [(938, 380, 2.4), (986, 380, 2.4)], 3, 55, False),
    ("02-the-mine", "found-cave", (540, 345, 840), 2.1, 0.30, 0, [], 5, 6, False),
    ("03-the-birches", "found-treeline", (620, 300, 1100), 1.05, 0.0, 0, [], 11, 12, False),
    ("04-the-corridor", "occupant-corridor", (147, 50, 560), 1.1, 0.0, 0, [], 13, 12, True),
    ("05-between-the-trees", "occupant-trees", (157, 40, 540), 0.62, 0.0, 0, [], 17, 12, True),
    ("06-the-graves", "cinematic-graves", (506, 287, 900), 1.5, 0.3, 0, [], 23, 12, False),
]


def dread(src, crop, exposure, warmth, tilt, eyes, seed, black, crosshair):
    rng = np.random.default_rng(seed)
    x0, y0, cw = crop
    ch = int(round(cw * H / W))
    full = Image.open(src).convert("RGB")
    sw, sh = full.size
    if crosshair:
        # The crosshair, painted out with what is round it.
        cx, cy, rad = sw // 2, sh // 2, max(8, sw // 80)
        box = (cx - rad, cy - rad, cx + rad, cy + rad)
        patch = full.crop((box[0] - rad, box[1] - rad, box[2] + rad, box[3] + rad)).filter(ImageFilter.MedianFilter(2 * rad - 1 if rad % 2 else 2 * rad + 1))
        full.paste(patch.crop((rad, rad, rad * 3, rad * 3)), box[:2])
    # Eyes are given in the source's pixels; scale them with the crop.
    scale = W / cw
    # No stars, no specks: small bright points out in the dark are taken away; only it is out there.
    opened = full.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    near = np.asarray(full.convert("L").filter(ImageFilter.GaussianBlur(14)), np.float32) / 255.0
    dark = np.clip((0.10 - near) / 0.05, 0, 1)[..., None]
    full = Image.fromarray((np.asarray(full, np.float32) * (1 - dark) + np.asarray(opened, np.float32) * dark).astype(np.uint8))
    img = full.crop((x0, y0, x0 + cw, y0 + ch))
    if tilt:
        img = img.rotate(tilt, resample=Image.BICUBIC, fillcolor=(0, 0, 0))
    soft = img.resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(4.5))
    sharp = img.resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1.4))
    a = (np.asarray(soft, np.float32) * 0.5 + np.asarray(sharp, np.float32) * 0.5) / 255.0

    lum = a.mean(axis=2)
    # Levels from the picture itself: the darkest tenth goes black, its brightest a little short of white.
    lo = np.percentile(lum, black)
    hi = max(np.percentile(lum, 99.6), lo + 0.05)
    t = np.clip((lum - lo) / (hi - lo) * exposure, 0, 1.2) ** 1.45
    # Cold, bloodless, the light gone sick; a little of any fire left in, if asked.
    cold = np.array([0.72, 0.78, 0.86], np.float32)
    sick = np.array([0.97, 0.93, 0.83], np.float32)
    rgb = t[..., None] * (cold * (1 - t[..., None]) + sick * t[..., None])
    sat = a - lum[..., None]
    rgb += sat * warmth * 1.6 * t[..., None]
    # Reds survive, darkly: hair, the mouth, blood.
    red = np.clip((a[..., 0] - a[..., 2]) * 5 - 0.1, 0, 1) * np.clip(1 - t * 1.5, 0, 1)
    rgb[..., 0] += red * 0.10

    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for ex, ey, size in eyes:
        cx, cy = (ex - x0) * scale, (ey - y0) * scale
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        rgb += (np.exp(-(d / (size * scale * 0.55)) ** 2) * 0.9
                + np.exp(-(d / (size * scale * 2.6)) ** 2) * 0.07)[..., None] * np.array([0.9, 0.95, 1.0])

    # A ghost: it moved while the shutter was open.
    ghost = np.roll(rgb, shift=(8, 30), axis=(0, 1))
    rgb = np.maximum(rgb, ghost * 0.25)
    # Fringing, strongest at the edges.
    u, v = xx / W - 0.5, yy / H - 0.5
    edge = np.sqrt(u * u + (v * H / W) ** 2 * 1.8)
    w = np.clip(edge * 2.2, 0, 1)
    rgb[..., 0] = rgb[..., 0] * (1 - w) + np.roll(rgb[..., 0], 6, axis=1) * w
    rgb[..., 2] = rgb[..., 2] * (1 - w) + np.roll(rgb[..., 2], -6, axis=1) * w
    # The dark, closing in.
    vig = np.clip(1.0 - (np.sqrt(u * u * 1.0 + v * v * 1.35) / 0.68) ** 2.3, 0, 1) ** 1.25
    rgb *= vig[..., None]
    # Grain, heavier in the dark; faint scanlines.
    grain = rng.normal(0, 0.05, (H, W)).astype(np.float32)
    rgb += grain[..., None] * (0.35 + 0.65 * np.clip(1 - rgb.mean(axis=2), 0, 1))[..., None]
    rgb -= ((np.sin(yy * np.pi / 3.0) * 0.5 + 0.5) * 0.028)[..., None]
    return Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    for name, src, crop, exposure, warmth, tilt, eyes, seed, black, crosshair in SHOTS:
        path = SRC / (src + ".png")
        if not path.exists():
            print("missing", path.name)
            continue
        dread(path, crop, exposure, warmth, tilt, eyes, seed, black, crosshair).save(OUT / (name + ".png"))
        print("made", name)


if __name__ == "__main__":
    main()
