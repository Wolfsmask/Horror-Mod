#!/usr/bin/env python3
"""
The Creator Pack's pictures: YouTube thumbnails, Shorts/TikTok covers, and blank backgrounds to
put your own face and words on.

    pip install numpy pillow
    python3 tools/marketing/make_creator_kit.py

Reads the same frames of the real game as make_gallery.py (docs/modrinth/stills) and writes
docs/creators/:
  thumbnails/   1280 x 720, with big words on them, ready to upload
  blank/        1280 x 720, graded, no words: make your own
  vertical/     1080 x 1920, for Shorts, TikTok and Reels covers
Nothing is painted: every picture is a frame of the game, cropped, graded and lettered.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs/modrinth/stills"
OUT = ROOT / "docs/creators"
FONTS = Path(__file__).resolve().parent / "fonts"
BONE = (232, 222, 206)
BLOOD = (196, 30, 22)
rng = np.random.default_rng(2017)

THUMBS = [
    # still, file out, words (top line, bottom line), which line is red, zoom, focus (x, y as fractions)
    ("cinematic-treeline", "01-it-was-there-the-whole-time", ("IT WAS THERE", "THE WHOLE TIME"), 1, 1.5, (0.46, 0.47)),
    ("cinematic-face", "02-dont-look-at-it", ("DON'T", "LOOK AT IT"), 1, 1.3, (0.5, 0.52)),
    ("cinematic-fog", "03-something-in-my-world", ("SOMETHING IS", "IN MY WORLD"), 1, 1.8, (0.5, 0.42)),
    ("cinematic-village", "04-everyone-left", ("EVERYONE LEFT.", "IT STAYED."), 1, 1.8, (0.53, 0.42)),
    ("cinematic-camp", "05-we-were-four", ("WE WERE FOUR.", "THEN THREE."), 1, 1.6, (0.45, 0.43)),
    ("cinematic-graves", "06-it-took-the-rest", ("IT TOOK", "THE REST"), 1, 1.6, (0.56, 0.41)),
    ("cinematic-ruin", "07-17-feet-tall", ("17 FEET", "TALL"), 0, 1.5, (0.44, 0.44)),
]


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def grade(img, exposure=1.35, saturation=0.75, vignette=0.6):
    """Brighter and punchier than the gallery: a thumbnail is seen small, on a phone, in a list."""
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255.0
    a = np.clip(a, 0, 1) ** (1.0 / exposure)
    a = np.clip((a - 0.03) / 0.97, 0.0, 1.0) ** 1.08
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    a = lum + (a - lum) * saturation
    a += ((1.0 - lum) ** 2) * np.array([-0.01, 0.01, 0.04], np.float32)
    # Contrast: a gentle S.
    a = np.clip(a, 0, 1)
    a = a * a * (3 - 2 * a) * 0.35 + a * 0.65
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2 * 0.8)
    a *= (1.0 - vignette * np.clip(r - 0.4, 0, 1) ** 1.5)[..., None]
    a += rng.normal(0.0, 0.012, size=(h, w, 1)).astype(np.float32)
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def crop(src, size, focus, zoom=1.0):
    """{size} cut from {src} around {focus}, scaled to cover."""
    img = src.convert("RGB")
    tw, th = size
    scale = max(tw / img.width, th / img.height) * zoom
    img = img.resize((max(tw, round(img.width * scale)), max(th, round(img.height * scale))), Image.LANCZOS)
    left = int(np.clip(focus[0] * img.width - tw / 2, 0, img.width - tw))
    top = int(np.clip(focus[1] * img.height - th / 2, 0, img.height - th))
    return img.crop((left, top, left + tw, top + th))


def words(img, lines, red, top=False):
    """Big words with a black edge and a soft shadow, so they read at any size."""
    w, h = img.size
    d = ImageDraw.Draw(img)
    size = int(h * (0.16 if w > h else 0.075))
    f = font("Cinzel.ttf", size)
    while max(d.textlength(t, font=f) for t in lines) > w * 0.9:
        size -= 4
        f = font("Cinzel.ttf", size)
    gap = int(size * 1.08)
    y0 = int(h * 0.08) if top else h - int(h * 0.08) - gap * len(lines)
    shadow = Image.new("L", img.size, 0)
    sd = ImageDraw.Draw(shadow)
    for i, t in enumerate(lines):
        x = (w - d.textlength(t, font=f)) / 2
        sd.text((x, y0 + i * gap), t, font=f, fill=255, stroke_width=max(6, size // 10), stroke_fill=255)
    shadow = shadow.filter(ImageFilter.GaussianBlur(size // 6))
    img.paste(Image.new("RGB", img.size, (0, 0, 0)), (0, 0), shadow.point(lambda v: int(v * 0.85)))
    d = ImageDraw.Draw(img)
    for i, t in enumerate(lines):
        x = (w - d.textlength(t, font=f)) / 2
        d.text((x, y0 + i * gap), t, font=f, fill=BLOOD if i == red else BONE,
               stroke_width=max(3, size // 22), stroke_fill=(0, 0, 0))
    return img


def tag(img):
    """THE OCCUPANT, small, in a corner: where the video's name is from."""
    d = ImageDraw.Draw(img)
    w, h = img.size
    f = font("Cinzel.ttf", max(20, h // 30))
    t = "THE OCCUPANT"
    d.text((w - 24 - d.textlength(t, font=f), 18), t, font=f, fill=(170, 160, 150), stroke_width=2, stroke_fill=(0, 0, 0))
    return img


def main():
    for sub in ("thumbnails", "blank", "vertical"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    made = 0
    for name, out, lines, red, zoom, focus in THUMBS:
        path = SHOTS / (name + ".png")
        if not path.exists():
            print("missing", path.name)
            continue
        src = Image.open(path)
        base = grade(crop(src, (1280, 720), focus, zoom))
        base.save(OUT / "blank" / (out + ".jpg"), quality=90)
        tag(words(base.copy(), lines, red, top=True)).save(OUT / "thumbnails" / (out + ".jpg"), quality=90)
        tall = grade(crop(src, (1080, 1920), focus), vignette=0.45)
        tag(words(tall, lines, red, top=True)).save(OUT / "vertical" / (out + ".jpg"), quality=90)
        made += 1
    print("made", made, "sets in", OUT)


if __name__ == "__main__":
    main()
