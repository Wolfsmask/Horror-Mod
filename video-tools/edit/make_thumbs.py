"""The thumbnails, from stills staged for them in the game ([tape:thumbs] in a CI run: thumb-long-N.png
1920x1080, thumb-short-N.png 1080x1920, in client-test/ on ci-results), finished like a film poster: a
cold grade with the light warm, the light blooming, the lens's colour fringes, grain, a vignette, and
the words set small and spaced in an old print face, the grain over them too.
Usage: python3 make_thumbs.py LONG.png SHORT.png OUT_FOLDER"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FELL = str(HERE / "fonts" / "IMFeENrm28P.ttf")
CINZEL = str(HERE / "fonts" / "Cinzel.ttf")


def blur(a, r):
    return np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255


def grade(a):
    """Cold, deep shadows; the light warm; the colour drained a little; blacks never quite black."""
    lum = (a * [0.299, 0.587, 0.114]).sum(-1, keepdims=True)
    a = lum + (a - lum) * 0.75
    s = np.clip(lum, 0, 1)
    curve = s * s * (3 - 2 * s)
    a = np.clip((a - 0.02) * 1.25, 0, 1) ** 1.12
    shadow = np.array([0.62, 0.8, 1.0]) * 0.06
    warm = np.array([1.0, 0.93, 0.84])
    w = np.clip((lum - 0.35) / 0.5, 0, 1)
    a = a * (1 - w) * [0.9, 0.98, 1.06] + a * w * warm + shadow * (1 - lum)
    return np.clip(a, 0, 1)


def bloom(a, r, amount=0.32):
    hi = np.clip((a - 0.6) / 0.4, 0, 1) * a
    return np.clip(a + blur(hi, r) * amount + blur(hi, r * 3) * amount * 0.5, 0, 1)


def fringe(a, k=0.0025):
    """The lens's colour fringes, more towards the edges: red out, blue in."""
    h, w, _ = a.shape
    out = a.copy()
    for c, s in ((0, 1 + k), (2, 1 - k)):
        img = Image.fromarray((a[..., c] * 255).astype(np.uint8))
        big = img.resize((int(w * s), int(h * s)), Image.BICUBIC)
        x0, y0 = (big.size[0] - w) // 2, (big.size[1] - h) // 2
        if s > 1:
            ch = big.crop((x0, y0, x0 + w, y0 + h))
        else:
            ch = Image.new("L", (w, h))
            ch.paste(big, (-x0, -y0))
        out[..., c] = np.asarray(ch).astype(np.float32) / 255
    return out


def vignette(a, strength=0.7, roundness=1.6):
    h, w, _ = a.shape
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((x - w / 2) / (w / 2)) ** 2 + ((y - h / 2) / (h / 2)) ** 2) / np.sqrt(2)
    return a * (1 - strength * np.clip(d, 0, 1) ** roundness)[..., None]


def grain(a, amount=0.045, seed=7):
    rng = np.random.default_rng(seed)
    h, w, _ = a.shape
    g = rng.normal(0, 1, (h, w)).astype(np.float32)
    g = blur(g * 0.25 + 0.5, 0.7) * 4 - 2
    lum = (a * [0.299, 0.587, 0.114]).sum(-1, keepdims=True)
    return np.clip(a + g[..., None] * amount * (0.4 + 0.6 * (1 - np.abs(lum - 0.45) * 1.6)).clip(0.2, 1), 0, 1)


def spaced(draw, xy, text, font, tracking, fill, anchor="center"):
    """Text with its letters spaced out, as a film poster sets a title."""
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    x, y = xy
    x = x - total / 2 if anchor == "center" else (x - total if anchor == "right" else x)
    for ch, wd in zip(text, widths):
        draw.text((x, y), ch, font=font, fill=fill)
        x += wd + tracking
    return total


def title(a, lines, cx, y0, size, tracking, red_word=None, credit=None, credit_y=None, credit_size=None):
    """The words: an old print face, spaced, a soft dark halo behind so they read, one word the colour
    of old blood; under the grain like the rest."""
    h, w, _ = a.shape
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert("RGBA")
    shadow = Image.new("L", (w, h), 0)
    sd = ImageDraw.Draw(shadow)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    f = ImageFont.truetype(FELL, size)
    y = y0
    for ln in lines:
        words = ln.split(" ")
        # Whole line's width, to centre it, words spaced too.
        parts = []
        for k, wd in enumerate(words):
            parts.append(wd)
        full = "  ".join(parts)
        widths = [ld.textlength(ch, font=f) for ch in full]
        total = sum(widths) + tracking * (len(full) - 1)
        x = cx - total / 2
        for wd_i, wd in enumerate(parts):
            col = (150, 14, 10, 255) if red_word and wd == red_word else (236, 230, 220, 255)
            wdt = spaced(ld, (x, y), wd, f, tracking, col, anchor="left")
            spaced(sd, (x, y), wd, f, tracking, 255, anchor="left")
            x += wdt + tracking + 2 * ld.textlength(" ", font=f) + tracking
        y += int(size * 1.08)
    if credit:
        fc = ImageFont.truetype(CINZEL, credit_size)
        spaced(ld, (cx, credit_y), credit, fc, credit_size * 0.55, (200, 196, 188, 230))
        spaced(sd, (cx, credit_y), credit, fc, credit_size * 0.55, 200)
    halo = shadow.filter(ImageFilter.GaussianBlur(size * 0.35)).point(lambda v: int(v * 0.85))
    dark = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    img = Image.composite(dark, img, halo)
    glow = layer.filter(ImageFilter.GaussianBlur(size * 0.06))
    img = Image.alpha_composite(img, Image.eval(glow, lambda v: int(v * 0.5)))
    img = Image.alpha_composite(img, layer)
    return np.asarray(img.convert("RGB")).astype(np.float32) / 255


def finish(src, out, lines, cx, y0, size, tracking, red_word, credit_y, credit_size, crop=None):
    im = Image.open(src).convert("RGB")
    if crop:
        im = im.crop(crop)
    a = np.asarray(im).astype(np.float32) / 255
    w = a.shape[1]
    a = grade(a)
    a = bloom(a, w * 0.012)
    a = fringe(a)
    a = vignette(a, 0.72)
    a = title(a, lines, cx, y0, size, tracking, red_word, "THE OCCUPANT", credit_y, credit_size)
    a = grain(a)
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(out, quality=93)


if __name__ == "__main__":
    long_src, short_src, outdir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    lw, lh = Image.open(long_src).size
    finish(long_src, outdir / "thumbnail-long.jpg", ["IT WAS IN", "THE WALL"], int(lw * 0.77), int(lh * 0.30), int(lh * 0.115),
           lh * 0.012, "WALL", int(lh * 0.86), int(lh * 0.03))
    sw, sh = Image.open(short_src).size
    finish(short_src, outdir / "thumbnail-short.jpg", ["IT WAS", "BEHIND ME"], sw // 2, int(sh * 0.07), int(sw * 0.13),
           sw * 0.012, "BEHIND", int(sh * 0.93), int(sw * 0.035))
    print("made", outdir / "thumbnail-long.jpg", outdir / "thumbnail-short.jpg")
