"""The thumbnails, painted the way the nightmare photos were (see painted.py): nothing of the game in them
but it, cut out of [tape:plates] frames shot against green; the wall, the woods, the light all painted;
then made into a bad photograph, and the words set in an old print face with one word the colour of old
blood, under the grain like the rest.
  Long video: an old board wall in a torch's beam, two boards broken out, and in the dark behind them
  its face. "IT WAS IN THE WALL".
  Short: in the woods at night, looking up: it bent down over you against the fog and the moon, its
  legs out to the trunks. "IT WAS BEHIND ME".
Usage: python3 make_thumbs.py FACE_PLATE.png TOWER_PLATE.png OUT_FOLDER"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import painted as P

HERE = Path(__file__).resolve().parent
FELL = str(HERE / "fonts" / "IMFeENrm28P.ttf")
CINZEL = str(HERE / "fonts" / "Cinzel.ttf")
RED = (160, 14, 10, 255)
BONE = (230, 224, 212, 255)


# ---------------------------------------------------------------- the long video's: the wall

def boards(rng, x0, x1, widths=(118, 165)):
    """Where the boards' edges are, left to right."""
    xs = [x0]
    while xs[-1] < x1:
        xs.append(xs[-1] + rng.uniform(*widths))
    return xs


def snapped(rng, xa, xb, base, sign, W):
    """The line a board snapped along, from {xa} to {xb} at height {base}: ragged, with splinters
    running along the grain (up or down, by {sign}) into the hole. A height for every column."""
    xs = np.arange(int(xa), int(xb) + 8, 7, dtype=np.float32)
    ys = base + sign * (rng.exponential(10, len(xs)) + (rng.random(len(xs)) < 0.12) * rng.uniform(30, 95, len(xs)))
    ys += sign * np.cumsum(rng.normal(0, 3, len(xs)))
    out = np.full(W, np.nan, np.float32)
    cols = np.arange(int(xa), int(xb))
    out[cols] = np.interp(cols, xs, ys)
    return out


def wall(face, seed=11, centre=1210, face_y=500, face_h=360):
    """An old board wall, close, in the beam of a torch held at the camera; one board gone and the
    two either side broken short, and in the black behind them, it, looking out at you."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    c = np.zeros((H, W, 3), np.float32)
    base = np.array([0.30, 0.235, 0.17], np.float32)
    edges = boards(rng, -60, W + 60)
    k = int(np.argmin([abs((edges[i] + edges[i + 1]) / 2 - centre) for i in range(len(edges) - 1)]))
    grain = P.noise(seed, 5, stretch=(16, 1))
    fine = P.noise(seed + 1, 1.6, stretch=(30, 1))
    rot = P.noise(seed + 2, 60)
    for i in range(len(edges) - 1):
        a, b = edges[i], edges[i + 1]
        m = ((xx >= a) & (xx < b)).astype(np.float32)
        tone = base * rng.uniform(0.7, 1.12) * np.array([1, rng.uniform(0.95, 1.03), rng.uniform(0.9, 1.05)])
        g = 0.72 + 0.28 * np.roll(grain, int(rng.integers(0, 400)), axis=0) + 0.18 * (fine - 0.5)
        g *= 1 - 0.35 * np.clip((rot - 0.55) * 3, 0, 1)
        d = np.clip(np.minimum(xx - a, b - xx) / 10.0, 0, 1)
        g *= 0.55 + 0.45 * d
        c += (m * g)[..., None] * tone
        for _ in range(rng.integers(0, 3)):
            kx, ky = rng.uniform(a + 20, b - 20), rng.uniform(80, H - 80)
            kn = np.exp(-(((xx - kx) / rng.uniform(7, 13)) ** 2 + ((yy - ky) / rng.uniform(14, 26)) ** 2))
            c *= (1 - 0.6 * kn)[..., None]
        for ny in (H * 0.13, H * 0.87):
            for nx in (a + (b - a) * 0.28, a + (b - a) * 0.72):
                n = np.exp(-(((xx - nx) ** 2 + (yy - ny + rng.uniform(-4, 4)) ** 2) / 26.0))
                c = c * (1 - 0.7 * n[..., None]) + n[..., None] * np.array([0.09, 0.08, 0.07]) * 0.6
    for e in edges:
        crack = np.exp(-((xx - e) / 3.0) ** 2)
        shade = np.clip((xx - e) / 24.0, 0, 1) * np.clip(1 - (xx - e) / 24.0, 0, 1) * 4 * (xx > e)
        c *= (1 - 0.92 * crack)[..., None]
        c *= (1 - 0.25 * shade)[..., None]
    # The hole: board {k} gone from high to low, the ones either side snapped off over a shorter span.
    top = np.full(W, np.nan, np.float32)
    bot = np.full(W, np.nan, np.float32)
    spans = [(k - 1, 300, 860), (k, 150, 960), (k + 1, 280, 840)]
    for j, t, bt in spans:
        a, b = edges[j], edges[j + 1]
        if j != k:          # a snapped neighbour keeps a little of itself next to the wall it is nailed to
            a, b = (a + 30, b) if j < k else (a, b - 30)
        t1 = snapped(rng, a, b, t + rng.uniform(-30, 30), 1, W)
        b1 = snapped(rng, a, b, bt + rng.uniform(-30, 30), -1, W)
        top = np.where(np.isnan(t1), top, t1)
        bot = np.where(np.isnan(b1), bot, b1)
    tt = np.where(np.isnan(top), H * 2, top)[None, :]
    bb = np.where(np.isnan(bot), -1, bot)[None, :]
    hole = ((yy > tt) & (yy < bb)).astype(np.float32)
    hole = P.soften(hole, 0.9)
    # The raw wood where they snapped: a thin pale lip over and under the hole, and a dark one inside.
    above = np.clip(np.roll(hole, -6, axis=0) - hole, 0, 1)
    below = np.clip(np.roll(hole, 6, axis=0) - hole, 0, 1)
    c += (above + below)[..., None] * np.array([0.30, 0.22, 0.14])
    behind = np.zeros_like(c) + np.array([0.004, 0.004, 0.005])
    # It, back in the dark of the wall, the torch on its face.
    box = P.face_box(face)
    lit = P.glints(P.relight(face, top=0.9, bottom=0.25, gain=1.1, cold=0.22, spot=P.spot_on(box, face, 1.1)), box)
    P.place_face(behind, lit, box, centre, face_y, face_h)
    inner = P.soften(hole, 22) ** 1.4
    c = c * (1 - hole[..., None]) + behind * (hole * inner)[..., None]
    # The torch: a hot pool on the hole, falling away to the dark.
    d = np.sqrt(((xx - centre) / (W * 0.36)) ** 2 + ((yy - H * 0.47) / (H * 0.62)) ** 2)
    beam = 0.05 + 1.3 * np.exp(-d ** 2 * 1.6) + 0.35 * np.exp(-(d / 0.35) ** 2)
    c *= beam[..., None] * np.array([1.0, 0.93, 0.8])
    return c


# ---------------------------------------------------------------- the short's: the woods

def woods(it, seed=21, face_at=(540, 640), face_h=210):
    """The woods at night, looking up from the ground: fog among the trunks, the moon in the fog, and
    it bent down over you, black against all that, only its face catching any light."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    c = np.zeros((H, W, 3), np.float32)
    mx, my = W * 0.5, H * 0.17
    dm = np.sqrt((xx - mx) ** 2 + ((yy - my) * 0.8) ** 2)
    fog = 0.03 + 0.34 * np.exp(-(dm / (W * 0.62)) ** 2) + 0.06 * P.noise(seed, 140) * np.exp(-(dm / W) ** 2)
    c[:] = np.stack([fog * 0.8, fog * 0.88, fog * 1.0], axis=2)
    c += (np.exp(-(dm / 34.0) ** 2) * 0.9 + np.exp(-(dm / 130.0) ** 2) * 0.2)[..., None] * np.array([0.85, 0.9, 1.0])
    # Trunks going up into it, leaning in (we are looking up): the far ones pale in the fog.
    for i in range(16):
        x = rng.uniform(-60, W + 60)
        if abs(x - W / 2) < 120:
            continue
        lean = (x - W / 2) / H * 0.5
        P.trunk(c, x, -20, H + 40, rng.uniform(16, 36), 0.05, 0.62, 400 + i, lean=lean)
    # It, black against the fog but for its face, which the last of the light finds.
    box = P.face_box(it)
    body = P.relight(it, top=0.5, bottom=0.12, gain=0.75, cold=0.45, spot=P.spot_on(box, it, 1.2))
    body = P.glints(body, box)
    P.place_face(c, body, box, face_at[0], face_at[1], face_h)
    for x, wdt in ((-10, 170), (W + 10, 190)):
        lean = (x - W / 2) / H * 0.6
        P.trunk(c, x, -20, H + 40, wdt, 0.022, 0.0, 500 + int(x), lean=lean)
    c *= (1 - 0.6 * np.clip((yy - H * 0.86) / (H * 0.14), 0, 1))[..., None]
    c *= (1 - 0.5 * np.clip(1 - yy / (H * 0.1), 0, 1))[..., None]
    return c


# ---------------------------------------------------------------- the words

def spaced(draw, xy, text, font, tracking, fill, anchor="center"):
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    x, y = xy
    x = x - total / 2 if anchor == "center" else (x - total if anchor == "right" else x)
    for ch, wd in zip(text, widths):
        draw.text((x, y), ch, font=font, fill=fill)
        x += wd + tracking
    return total


def title(a, lines, cx, y0, size, tracking, red_word, credit_y, credit_size):
    """The words, each line centred on {cx}, a soft dark halo behind so they read, one word red; the
    mod's name small underneath."""
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
        widths = [sum(ld.textlength(ch, font=f) for ch in wd) + tracking * (len(wd) - 1) for wd in words]
        x = cx - (sum(widths) + gap * (len(words) - 1)) / 2
        for wd, wdt in zip(words, widths):
            spaced(ld, (x, y), wd, f, tracking, RED if wd == red_word else BONE, anchor="left")
            spaced(sd, (x, y), wd, f, tracking, 255, anchor="left")
            x += wdt + gap
        y += int(size * 1.08)
    fc = ImageFont.truetype(CINZEL, credit_size)
    spaced(ld, (cx, credit_y), "THE OCCUPANT", fc, credit_size * 0.55, (196, 192, 184, 210))
    spaced(sd, (cx, credit_y), "THE OCCUPANT", fc, credit_size * 0.55, 200)
    halo = shadow.filter(ImageFilter.GaussianBlur(size * 0.35)).point(lambda v: int(v * 0.85))
    img = Image.composite(Image.new("RGBA", (w, h), (0, 0, 0, 255)), img, halo)
    glow = layer.filter(ImageFilter.GaussianBlur(size * 0.06))
    img = Image.alpha_composite(img, Image.eval(glow, lambda v: int(v * 0.4)))
    img = Image.alpha_composite(img, layer)
    return np.asarray(img.convert("RGB")).astype(np.float32) / 255


def grain(a, amount=0.03, seed=7):
    """The film's grain over everything, the words too, so they sit in the picture."""
    rng = np.random.default_rng(seed)
    g = rng.normal(0, amount, a.shape[:2]).astype(np.float32)
    return np.clip(a + g[..., None], 0, 1)


def save(a, path):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(path, quality=93)


if __name__ == "__main__":
    face_plate, tower_plate, outdir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    P.size(1920, 1080)
    a = P.photograph(wall(P.plate(face_plate)), 1, fringe=5, ghost=0.18, vignette=0.78, grain=0.045, soft=2.4)
    a = title(a, ["IT WAS IN", "THE WALL"], 470, 300, 118, 9, "WALL", 840, 30)
    save(grain(a), outdir / "thumbnail-long.jpg")
    P.size(1080, 1920)
    a = P.photograph(woods(P.plate(tower_plate)), 2, fringe=5, ghost=0.18, vignette=0.8, grain=0.045, soft=2.4)
    a = title(a, ["IT WAS", "BEHIND ME"], 540, 90, 124, 10, "BEHIND", 1830, 34)
    save(grain(a), outdir / "thumbnail-short.jpg")
    print("made", outdir / "thumbnail-long.jpg", outdir / "thumbnail-short.jpg")
