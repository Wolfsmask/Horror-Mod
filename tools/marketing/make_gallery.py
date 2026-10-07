#!/usr/bin/env python3
"""
Turns the client test's stills into the images for the mod's page.

    pip install numpy pillow
    python3 tools/marketing/make_gallery.py

Reads docs/modrinth/stills/*.png (frames of the real game, picked from the client game test's
photographs in docs/client-test) and writes docs/modrinth/: graded, letterboxed gallery images
with a line on each, the featured image with the title, and a 512x512 icon. Nothing here is
painted: every image is a frame of the game, only cropped, graded (tools/marketing/film.py) and
lettered.

Fonts (in tools/marketing/fonts, with their licences): Bebas Neue and Oswald (SIL Open Font
Licence).
"""
from pathlib import Path

from PIL import Image, ImageDraw

import film

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs/modrinth/stills"
OUT = ROOT / "docs/modrinth"
W, H = 1920, 1080
BAR = 138                      # letterbox bars: what is left between them is 2.39:1

# still, file out, the line under it, exposure, focus (x, y: what the frame is built round)
SHOTS_LIST = [
    ("cinematic-treeline", "01-treeline", "It was standing there the whole time", 1.35, (0.5, 0.45)),
    ("cinematic-fog", "02-fog", "It waits where you can only just see it", 1.25, (0.5, 0.5)),
    ("cinematic-village", "03-village", "Everyone left. Something stayed", 1.5, (0.5, 0.5)),
    ("cinematic-ruin", "04-ruin", "Somebody held out here. For a while", 1.35, (0.5, 0.45)),
    ("cinematic-camp", "05-camp", "We were four. Then three", 1.45, (0.5, 0.45)),
    ("cinematic-graves", "06-graves", "It took the rest", 1.7, (0.5, 0.45)),
    ("cinematic-face", "07-face", "It is learning how to be you", 1.2, (0.5, 0.4)),
    ("occupant-gate", "08-the-first-screen", "There is only one way in", 1.0, (0.5, 0.5)),
]


def frame(src, focus):
    """Full HD, letterboxed to 2.39:1 round the part of the picture that matters."""
    inner = H - 2 * BAR
    picture = film.cover(src, (W, inner), focus)
    out = Image.new("RGB", (W, H), (0, 0, 0))
    out.paste(picture, (0, BAR))
    return out


def lettered(img, line):
    d = ImageDraw.Draw(img)
    f = film.line_font(30)
    text = line.upper()
    tw = film.tracked_width(d, text, f, 9)
    film.tracked(d, ((W - tw) / 2, H - BAR + (BAR - 44) // 2), text, f, (214, 206, 194), 9)
    s = film.title_font(30)
    name = "THE OCCUPANT"
    nw = film.tracked_width(d, name, s, 7)
    film.tracked(d, (W - 64 - nw, (BAR - 34) // 2), name, s, (120, 112, 104), 7)
    return img


def featured(src):
    """The first image anyone sees: the title over the treeline."""
    inner = H - 2 * BAR
    picture = film.grade(film.cover(src, (W, inner), (0.5, 0.42)), exposure=1.3, vignette=0.85)
    picture = film.darken_side(picture, "top", 0.45)
    img = Image.new("RGB", (W, H), (0, 0, 0))
    img.paste(picture, (0, BAR))
    title = "THE OCCUPANT"
    f = film.title_font(200)
    d = ImageDraw.Draw(img)
    tw = film.tracked_width(d, title, f, 26)
    film.shadowed(img, ((W - tw) / 2, BAR + inner * 0.07), title, f, film.WHITE, tracking=26, shadow=0.85)
    sub = "THERE IS SOMETHING IN THIS WORLD WITH YOU"
    fs = film.line_font(30)
    sw = film.tracked_width(d, sub, fs, 11)
    film.tracked(ImageDraw.Draw(img), ((W - sw) / 2, H - BAR + (BAR - 44) // 2), sub, fs, (214, 206, 194), 11)
    return img


def icon(src, centre, size=0.42):
    """512 x 512 from the close-up of its face; {centre} is where the face is, as fractions."""
    img = src.convert("RGB")
    side = int(min(img.size) * size)
    cx, cy = int(centre[0] * img.width), int(centre[1] * img.height)
    box = (max(0, cx - side // 2), max(0, cy - side // 2))
    box = (min(box[0], img.width - side), min(box[1], img.height - side))
    sq = img.crop((box[0], box[1], box[0] + side, box[1] + side)).resize((512, 512), Image.LANCZOS)
    return film.grade(sq, exposure=1.2, vignette=0.95, halation=0.3)


def main(face_centre=(0.5, 0.42)):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "gallery").mkdir(exist_ok=True)
    made = []
    for name, out, line, exposure, focus in SHOTS_LIST:
        path = SHOTS / (name + ".png")
        if not path.exists():
            print("missing", path.name)
            continue
        src = film.still(SHOTS, name)
        graded = film.grade(src, exposure=exposure) if name.startswith("cinematic") else src.convert("RGB")
        lettered(frame(graded, focus), line).save(OUT / "gallery" / (out + ".png"))
        made.append(out)
    tree = SHOTS / "cinematic-treeline.png"
    if tree.exists():
        featured(film.still(SHOTS, "cinematic-treeline")).save(OUT / "gallery" / "00-featured.png")
        made.append("00-featured")
    face = SHOTS / "cinematic-face.png"
    if face.exists():
        icon(film.still(SHOTS, "cinematic-face"), face_centre).save(OUT / "icon.png")
        made.append("icon")
    print("made", made)


if __name__ == "__main__":
    import sys
    if len(sys.argv) == 3:
        main((float(sys.argv[1]), float(sys.argv[2])))
    else:
        main()
