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
Nothing is painted: every picture is a frame of the game, cropped, graded (film.py) and lettered.
"""
from pathlib import Path

from PIL import Image, ImageDraw

import film

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs/modrinth/stills"
OUT = ROOT / "docs/creators"

# still, file out, words (one line each), which line is red, exposure, where it stands in the still
# (x, y as fractions); the words go down the left, and the frame is cut so it stands to the right of them
THUMBS = [
    ("cinematic-treeline", "01-it-was-there-the-whole-time", ("IT WAS THERE", "THE WHOLE TIME"), 1, 1.35, (0.5, 0.55)),
    ("cinematic-face", "02-dont-look-up", ("DON'T", "LOOK UP"), 1, 1.2, (0.5, 0.45)),
    ("cinematic-fog", "03-something-in-my-world", ("SOMETHING IS", "IN MY WORLD"), 1, 1.3, (0.5, 0.58)),
    ("cinematic-village", "04-everyone-left", ("EVERYONE LEFT.", "IT STAYED."), 1, 1.45, (0.49, 0.55)),
    ("cinematic-camp", "05-we-were-four", ("WE WERE FOUR.", "THEN THREE."), 1, 1.5, (0.61, 0.5)),
    ("cinematic-graves", "06-it-took-the-rest", ("IT TOOK", "THE REST"), 1, 1.8, (0.48, 0.55)),
    ("cinematic-ruin", "07-17-feet-tall", ("17 FEET", "TALL"), 0, 1.3, (0.5, 0.55)),
]
ZOOM = 1.6


def words(img, lines, red, side):
    """Huge condensed type down one side, one line in red, a soft shadow under it."""
    w, h = img.size
    portrait = h > w
    img = film.darken_side(img, "top" if portrait else side, 0.72)
    d = ImageDraw.Draw(img)
    size = int(w * 0.17) if portrait else int(h * 0.24)
    f = film.title_font(size)
    longest = max(film.tracked_width(d, t, f, size * 0.02) for t in lines)
    limit = w * (0.86 if portrait else 0.47)
    if longest > limit:
        size = int(size * limit / longest)
        f = film.title_font(size)
    gap = int(size * 0.92)
    margin = int(w * 0.055)
    y = int(h * 0.07) if portrait else int((h - gap * len(lines)) / 2 - h * 0.03)
    for i, t in enumerate(lines):
        tw = film.tracked_width(d, t, f, size * 0.02)
        if portrait:
            x = (w - tw) / 2
        else:
            x = margin if side == "left" else w - margin - tw
        film.shadowed(img, (x, y + i * gap), t, f, film.RED if i == red else film.WHITE, tracking=size * 0.02, shadow=0.9)
    # Whose it is, small and spaced, under the words.
    s = film.line_font(max(18, int(size * 0.17)), heavy=True)
    name = "THE OCCUPANT"
    nw = film.tracked_width(d, name, s, s.size * 0.45)
    ny = y + len(lines) * gap + int(size * 0.08)
    nx = (w - nw) / 2 if portrait else (margin if side == "left" else w - margin - nw)
    film.tracked(ImageDraw.Draw(img), (nx, ny), name, s, (196, 188, 176), s.size * 0.45)
    return img


def main():
    for sub in ("thumbnails", "blank", "vertical"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    made = 0
    for name, out, lines, red, exposure, at in THUMBS:
        path = SHOTS / (name + ".png")
        if not path.exists():
            print("missing", path.name)
            continue
        src = film.grade(film.still(SHOTS, name), exposure=exposure, vignette=0.6)
        # Cut so that it stands two thirds of the way across, clear of the words on the left.
        centre = (at[0] - (0.73 - 0.5) / ZOOM, at[1])
        base = film.cover(src, (1280, 720), centre, ZOOM)
        base.save(OUT / "blank" / (out + ".jpg"), quality=92)
        words(base.copy(), lines, red, "left").save(OUT / "thumbnails" / (out + ".jpg"), quality=92)
        tall = film.cover(src, (1080, 1920), (at[0], min(0.7, at[1] + 0.1)))
        words(tall, lines, red, "left").save(OUT / "vertical" / (out + ".jpg"), quality=92)
        made += 1
    print("made", made, "sets in", OUT)


if __name__ == "__main__":
    main()
