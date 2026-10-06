#!/usr/bin/env python3
"""
Turns the client test's stills into the images for the mod's page.

    pip install numpy pillow
    python3 tools/marketing/make_gallery.py

Reads docs/client-test/*.png (the real game, photographed by the client game test) and writes
docs/modrinth/: graded, letterboxed gallery images with a line on each, the featured image with
the title, and a 512x512 icon. Nothing here is painted: every image is a frame of the game, only
cropped, graded and captioned.

Fonts (in tools/marketing/fonts, with their licences): IM Fell English and Cinzel (SIL Open Font
Licence), Special Elite (Apache 2.0).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs/client-test"
OUT = ROOT / "docs/modrinth"
FONTS = Path(__file__).resolve().parent / "fonts"
W, H = 1920, 1080
BAR = 138                      # letterbox bars: what is left between them is 2.39:1
BONE = (216, 204, 188)
BLOOD = (150, 28, 22)
rng = np.random.default_rng(1666)

# name in docs/client-test, file out, the line under it, grade (saturation, warmth, exposure),
# focus (0 top .. 1 bottom)
SHOTS_LIST = [
    ("cinematic-treeline", "01-treeline", "It was standing there the whole time.", (0.62, 0.15, 1.0), 0.45),
    ("cinematic-hallway", "02-hallway", "Don't go down the hallway.", (0.55, 0.05, 2.4), 0.5),
    ("cinematic-village", "03-village", "Everyone left. Something stayed.", (0.6, 0.25, 1.1), 0.5),
    ("cinematic-ruin", "04-ruin", "Somebody held out here. For a while.", (0.7, 0.45, 1.0), 0.5),
    ("cinematic-camp", "05-camp", "WE WERE FOUR. THEN THREE.", (0.8, 0.55, 1.1), 0.55),
    ("cinematic-graves", "06-graves", "It took the rest.", (0.5, -0.1, 1.25), 0.55),
    ("cinematic-face", "07-face", "It is learning how to be you.", (0.6, 0.3, 1.0), 0.5),
    ("occupant-gate", "08-the-first-screen", "There is only one way in.", (0.9, 0.0, 1.0), 0.5),
]


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def grade(img, saturation, warmth, vignette=0.55, exposure=1.0):
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255.0
    if exposure != 1.0:
        # Brought up out of the dark, shadows most, so a night shot reads on a phone screen.
        a = np.clip(a, 0, 1) ** (1.0 / exposure)
    # Crush the blacks a little, so the dark is dark.
    a = np.clip((a - 0.025) / 0.975, 0.0, 1.0) ** 1.12
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    a = lum + (a - lum) * saturation
    # Split tone: cold in the shadows; the warmth (sunset, fire) only in the highlights.
    shadow = (1.0 - lum) ** 2
    a += shadow * np.array([-0.01, 0.012, 0.035], np.float32)
    a += (lum ** 2) * warmth * np.array([0.06, 0.025, -0.04], np.float32)
    # The dark closes in from the edges.
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2 * 0.8)
    a *= (1.0 - vignette * np.clip(r - 0.45, 0, 1) ** 1.6)[..., None]
    # Grain.
    a += rng.normal(0.0, 0.018, size=(h, w, 1)).astype(np.float32)
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def frame(src, focus):
    """Full HD, letterboxed to 2.39:1 around the part of the picture that matters."""
    img = src.convert("RGB")
    if img.size != (W, H):
        img = img.resize((W, int(img.height * W / img.width)), Image.LANCZOS)
    inner = H - 2 * BAR
    top = int(np.clip(focus * img.height - inner / 2, 0, img.height - inner))
    out = Image.new("RGB", (W, H), (0, 0, 0))
    out.paste(img.crop((0, top, W, top + inner)), (0, BAR))
    return out


def caption(img, line, small="THE OCCUPANT"):
    d = ImageDraw.Draw(img)
    typed = line.isupper()
    f = font("SpecialElite-Regular.ttf", 50) if typed else font("IMFeENit28P.ttf", 62)
    tw = d.textlength(line, font=f)
    y = H - BAR + (BAR - 62) // 2 - 4
    d.text(((W - tw) / 2, y), line, font=f, fill=BLOOD if typed else BONE)
    s = font("Cinzel.ttf", 26)
    d.text((W - 60 - d.textlength(small, font=s), (BAR - 26) // 2), small, font=s, fill=(120, 112, 104))
    return img


def featured(src):
    """The first image anyone sees: the title over the treeline."""
    img = grade(frame(src, 0.45), 0.6, 0.15, vignette=0.75)
    d = ImageDraw.Draw(img)
    title = "THE OCCUPANT"
    f = font("Cinzel.ttf", 150)
    tw = d.textlength(title, font=f)
    glow = Image.new("L", img.size, 0)
    ImageDraw.Draw(glow).text(((W - tw) / 2, BAR + 40), title, font=f, fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    img = Image.composite(Image.new("RGB", img.size, (0, 0, 0)), img, glow.point(lambda v: int(v * 0.8)))
    d = ImageDraw.Draw(img)
    d.text(((W - tw) / 2 + 3, BAR + 40), title, font=f, fill=(110, 20, 16))
    d.text(((W - tw) / 2, BAR + 37), title, font=f, fill=BONE)
    sub = "There is something in this world with you."
    fs = font("IMFeENit28P.ttf", 58)
    d.text(((W - d.textlength(sub, font=fs)) / 2, H - BAR + 34), sub, font=fs, fill=BONE)
    return img


def icon(src, centre, size=0.5):
    """512 x 512 from the close-up of its face; {centre} is where the face is, as fractions."""
    img = src.convert("RGB")
    side = int(min(img.size) * size)
    cx, cy = int(centre[0] * img.width), int(centre[1] * img.height)
    box = (max(0, cx - side // 2), max(0, cy - side // 2))
    box = (min(box[0], img.width - side), min(box[1], img.height - side))
    sq = img.crop((box[0], box[1], box[0] + side, box[1] + side)).resize((512, 512), Image.LANCZOS)
    return grade(sq, 0.55, 0.2, vignette=0.9)


def main(face_centre=(0.5, 0.45)):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "gallery").mkdir(exist_ok=True)
    made = []
    for name, out, line, (sat, warm, exposure), focus in SHOTS_LIST:
        path = SHOTS / (name + ".png")
        if not path.exists():
            print("missing", path.name)
            continue
        src = Image.open(path)
        img = caption(grade(frame(src, focus), sat, warm, exposure=exposure), line)
        img.save(OUT / "gallery" / (out + ".png"))
        made.append(out)
    tree = SHOTS / "cinematic-treeline.png"
    if tree.exists():
        featured(Image.open(tree)).save(OUT / "gallery" / "00-featured.png")
        made.append("00-featured")
    face = SHOTS / "cinematic-face.png"
    if face.exists():
        icon(Image.open(face), face_centre).save(OUT / "icon.png")
        made.append("icon")
    print("made", made)


if __name__ == "__main__":
    import sys
    if len(sys.argv) == 3:
        main((float(sys.argv[1]), float(sys.argv[2])))
    else:
        main()
