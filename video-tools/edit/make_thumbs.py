"""The thumbnails, made from the painted nightmare photos in photos/ (the user wanted those, not stills from
the game): the photo kept as it is, lifted a little so it reads on a phone, the Short's cut down to a tall
frame, and the words set in an old print face with one word the colour of old blood, the grain over them.
Usage: python3 make_thumbs.py OUT_FOLDER   (writes thumbnail-long.jpg and thumbnail-short.jpg)"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
PHOTOS = HERE.parents[1] / "photos"
FELL = str(HERE / "fonts" / "IMFeENrm28P.ttf")
CINZEL = str(HERE / "fonts" / "Cinzel.ttf")
RED = (168, 16, 12, 255)
BONE = (232, 226, 214, 255)


def blur(a, r):
    return np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255


def lift(a, gamma=0.8, black=0.012):
    """The photos are made dark; a thumbnail is seen small, so the mid-tones come up a little."""
    return np.clip((a - black) / (1 - black), 0, 1) ** gamma


def grain(a, amount=0.035, seed=7):
    rng = np.random.default_rng(seed)
    h, w, _ = a.shape
    g = rng.normal(0, 1, (h, w)).astype(np.float32)
    g = blur(g * 0.25 + 0.5, 0.7) * 4 - 2
    return np.clip(a + g[..., None] * amount, 0, 1)


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


def title(a, lines, cx, y0, size, tracking, red_word, credit_y, credit_size):
    """The words, each line centred on {cx}: a soft dark halo behind so they read, a faint glow, the
    one red word; and the mod's name small underneath."""
    h, w, _ = a.shape
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert("RGBA")
    shadow = Image.new("L", (w, h), 0)
    sd = ImageDraw.Draw(shadow)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    f = ImageFont.truetype(FELL, size)
    gap = 2 * ld.textlength(" ", font=f) + 2 * tracking
    y = y0
    for ln in lines:
        words = ln.split(" ")
        widths = [sum(ld.textlength(c, font=f) for c in wd) + tracking * (len(wd) - 1) for wd in words]
        x = cx - (sum(widths) + gap * (len(words) - 1)) / 2
        for wd, wdt in zip(words, widths):
            spaced(ld, (x, y), wd, f, tracking, RED if wd == red_word else BONE, anchor="left")
            spaced(sd, (x, y), wd, f, tracking, 255, anchor="left")
            x += wdt + gap
        y += int(size * 1.08)
    fc = ImageFont.truetype(CINZEL, credit_size)
    spaced(ld, (cx, credit_y), "THE OCCUPANT", fc, credit_size * 0.55, (200, 196, 188, 220))
    spaced(sd, (cx, credit_y), "THE OCCUPANT", fc, credit_size * 0.55, 200)
    halo = shadow.filter(ImageFilter.GaussianBlur(size * 0.35)).point(lambda v: int(v * 0.8))
    img = Image.composite(Image.new("RGBA", (w, h), (0, 0, 0, 255)), img, halo)
    glow = layer.filter(ImageFilter.GaussianBlur(size * 0.06))
    img = Image.alpha_composite(img, Image.eval(glow, lambda v: int(v * 0.45)))
    img = Image.alpha_composite(img, layer)
    return np.asarray(img.convert("RGB")).astype(np.float32) / 255


def make(photo, out, lines, red_word, at, size, credit_y, credit_size, tracking=0.1, tall=None, zoom=1.0):
    """{tall}: the centre (a fraction of the width) of a 9:16 window cut out of the top of the photo
    for a Short, {zoom} times closer than the photo's full height, scaled up to 1080x1920. {at} is where
    the words go, as fractions: (centre x, top y); {size} the letters' height, a fraction of the frame's."""
    im = Image.open(PHOTOS / photo).convert("RGB")
    if tall is not None:
        w, h = im.size
        ch = h / zoom
        cw = ch * 9 / 16
        x0 = float(np.clip(tall * w - cw / 2, 0, w - cw))
        im = im.crop((int(x0), 0, int(x0 + cw), int(ch))).resize((1080, 1920), Image.LANCZOS)
        im = im.filter(ImageFilter.UnsharpMask(radius=2.4, percent=60, threshold=2))
    a = lift(np.asarray(im).astype(np.float32) / 255)
    h, w, _ = a.shape
    a = title(a, lines, int(at[0] * w), int(at[1] * h), int(size * h), size * h * tracking, red_word,
              int(credit_y * h), int(credit_size * h))
    a = grain(a)
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(out, quality=93)


if __name__ == "__main__":
    outdir = Path(sys.argv[1])
    # Its face right up against the lens, tilted, the pupils lit; the words in the dark beside it.
    make("nightmare-01-too-close.jpg", outdir / "thumbnail-long.jpg", ["IT", "FOUND", "ME"], "FOUND",
         (0.832, 0.22), 0.125, 0.79, 0.027, tracking=0.05)
    # It over you, close, its legs crossing the bottom of the frame; the words in the dark above its head.
    make("nightmare-02-over-you.jpg", outdir / "thumbnail-short.jpg", ["IT WAS", "BEHIND ME"], "BEHIND",
         (0.5, 0.045), 0.066, 0.955, 0.02, tall=0.495, zoom=1.35)
    print("made", outdir / "thumbnail-long.jpg", outdir / "thumbnail-short.jpg")
