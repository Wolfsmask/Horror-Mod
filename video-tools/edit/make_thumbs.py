"""The thumbnails, painted the way the nightmare photos were (see painted.py): nothing of the game in them
but it, cut out of [tape:plates] frames shot against green; the wall, the woods, the light all painted;
then made into a bad photograph, and the words set in an old print face with one word the colour of old
blood, under the grain like the rest.
  Long video: an old board wall in a torch's beam, two boards broken out, and in the dark behind them
  its face. "IT WAS IN THE WALL".
  Short: in the woods at night, looking up: it bent down over you against the fog and the moon, its
  legs out to the trunks. "IT WAS BEHIND ME".
  And ten more scenes to choose from (VARIANTS: a window, a doorway, a campfire, a well, a camcorder's
  view; for the Short a doorway, its face too close, it over you in bed, a window, a mine tunnel).
Usage: python3 make_thumbs.py PLATES_FOLDER OUT_FOLDER [variant ...]
  (PLATES_FOLDER holds the [tape:plates] frames, plate-*.png; with no variants named, all are made,
  with long-choices.jpg and short-choices.jpg to compare them)"""
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


def wall(face, seed=11, centre=1210, face_y=515, face_h=330):
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
    lit = P.relight(face, top=0.35, bottom=0.08, gain=0.95, cold=0.3, spot=P.spot_on(box, face, 0.8, 0.55))
    lit = P.glints(lit, box)
    P.place_face(behind, lit, box, centre, face_y, face_h)
    inner = P.soften(hole, 22) ** 1.4
    c = c * (1 - hole[..., None]) + behind * (hole * inner)[..., None]
    # The torch: a hot pool on the hole, falling away to the dark.
    d = np.sqrt(((xx - centre) / (W * 0.36)) ** 2 + ((yy - H * 0.47) / (H * 0.62)) ** 2)
    beam = 0.05 + 1.3 * np.exp(-d ** 2 * 1.6) + 0.35 * np.exp(-(d / 0.35) ** 2)
    c *= beam[..., None] * np.array([1.0, 0.93, 0.8])
    return c


# ---------------------------------------------------------------- the short's: the woods

def woods(it, seed=21, face_at=(540, 660), face_h=380):
    """The woods at night, looking up from the ground: fog among the trunks, the moon in the fog, and
    it bent down over you, black against all that, only its face catching any light."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    c = np.zeros((H, W, 3), np.float32)
    mx, my = face_at[0], face_at[1] - face_h * 0.35
    dm = np.sqrt((xx - mx) ** 2 + ((yy - my) * 0.75) ** 2)
    fog = 0.03 + 0.58 * np.exp(-(dm / (W * 0.6)) ** 2) + 0.08 * P.noise(seed, 140) * np.exp(-(dm / W) ** 2)
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
    body = P.relight(it, top=0.2, bottom=0.04, gain=0.95, cold=0.4, spot=P.spot_on(box, it, 0.7, 1.6))
    body = P.glints(body, box)
    P.place_face(c, body, box, face_at[0], face_at[1], face_h)
    for x, wdt in ((-40, 110),):
        lean = (x - W / 2) / H * 0.6
        P.trunk(c, x, -20, H + 40, wdt, 0.022, 0.0, 500 + int(x), lean=lean)
    c *= (1 - 0.9 * np.clip((yy - H * 0.8) / (H * 0.2), 0, 1) ** 1.3)[..., None]
    c *= (1 - 0.5 * np.clip(1 - yy / (H * 0.1), 0, 1))[..., None]
    return c


# ---------------------------------------------------------------- more scenes, to choose from

def mask_rect(x0, y0, x1, y1):
    m = np.zeros((P.H, P.W), np.float32)
    m[max(0, int(y0)):max(0, int(y1)), max(0, int(x0)):max(0, int(x1))] = 1
    return m


def wallpaper(seed, lamp, level=0.11, reach=0.85, tone=(1.1, 1.0, 0.86)):
    """A room's wall at night, papered, stained, lit by one lamp at {lamp} and dark away from it."""
    W, H = P.W, P.H
    yy, xx = P.grid()
    d = np.sqrt(((xx - lamp[0]) / (W * reach)) ** 2 + ((yy - lamp[1]) / (H * 1.1)) ** 2)
    lit = 0.012 + level * np.exp(-d ** 2 * 2.2)
    paper = 0.86 + 0.14 * np.sin(xx / 9.0) ** 2
    stain = 0.82 + 0.3 * P.noise(seed, 90)
    return np.stack([lit * tone[0], lit * tone[1], lit * tone[2]], axis=2) * (paper * stain)[..., None]


def glow(c, x, y, r, colour, power):
    yy, xx = P.grid()
    d = np.sqrt((xx - x) ** 2 + (yy - y) ** 2)
    c += (np.exp(-(d / r) ** 2) * power)[..., None] * np.asarray(colour, np.float32)


def outside(seed, x0, y0, x1, y1, trunks=9, sky=(0.07, 0.3)):
    """Night outside a window: the sky paler low down where the fog is, trunks pale in it."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    s = sky[0] + (sky[1] - sky[0]) * np.clip((yy - y0) / max(1, y1 - y0), 0, 1) ** 1.4
    c = np.stack([s * 0.78, s * 0.86, s * 1.05], axis=2)
    for i in range(trunks):
        P.trunk(c, rng.uniform(x0 - 40, x1 + 40), y0 - 60, y1 + 60, rng.uniform(14, 42), 0.03, 0.55, seed * 10 + i)
    return c


def glass(c, pane, seed, lamp=None):
    """The glass: misted, run with rain, the room's lamp caught in it."""
    rng = np.random.default_rng(seed)
    ys, xs = np.nonzero(pane)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    c += ((P.noise(seed, 60) * 0.05 + 0.012) * pane)[..., None] * np.array([0.8, 0.85, 0.95])
    streaks = []
    for _ in range(int((x1 - x0) * (y1 - y0) / 5000)):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1 - 60)
        streaks.append([(x, y), (x + rng.uniform(-3, 3), y + rng.uniform(30, 170))])
    c += (P.lines_mask(streaks, 2, blur=1.2) * pane)[..., None] * 0.09
    if lamp:
        yy, xx = P.grid()
        l = np.exp(-(((xx - lamp[0]) / 34.0) ** 2 + ((yy - lamp[1]) / 24.0) ** 2)) * pane
        c += l[..., None] * np.array([0.35, 0.25, 0.12])


def window_frame(c, x0, y0, x1, y1, t=30, colour=(0.045, 0.035, 0.03)):
    f = mask_rect(x0 - t, y0 - t, x1 + t, y1 + t) - mask_rect(x0, y0, x1, y1)
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    f += mask_rect(mx - t * 0.35, y0, mx + t * 0.35, y1) + mask_rect(x0, my - t * 0.35, x1, my + t * 0.35)
    P.paint(c, np.clip(f, 0, 1), colour)
    P.paint(c, mask_rect(x0 - t * 2.2, y1 + t, x1 + t * 2.2, y1 + t * 1.8), (0.085, 0.075, 0.065))


def lit_it(name, top=0.25, bottom=0.06, gain=0.9, cold=0.35, spot=0.9, radius=0.8):
    it = CUTS[name]
    box = P.face_box(it)
    return P.glints(P.relight(it, top=top, bottom=bottom, gain=gain, cold=cold, spot=P.spot_on(box, it, radius, spot)), box), box


def window(face_at, face_h, win, lamp, title_side_lamp):
    """Night, inside: just the other side of the glass, bent down to look in, it."""
    x0, y0, x1, y1 = win
    c = wallpaper(31, title_side_lamp, level=0.2)
    pane = mask_rect(x0, y0, x1, y1)
    out = outside(32, x0, y0, x1, y1)
    it, box = lit_it("face-wide", top=0.3, bottom=0.08, spot=1.5)
    P.place_face(out, it, box, face_at[0], face_at[1], face_h, blur=0.8)
    c = c * (1 - pane[..., None]) + out * pane[..., None]
    glass(c, pane, 33, lamp)
    window_frame(c, x0, y0, x1, y1)
    return c


def doorway(door, face_at, face_h, lamp, floor_y):
    """A room at night; the door open on a dark hall, and in the doorway it, its legs out round the
    frame onto this side of the wall."""
    W, H = P.W, P.H
    yy, xx = P.grid()
    x0, y0, x1, y1 = door
    c = wallpaper(41, lamp, level=0.3, reach=1.0)
    floor = (yy > floor_y).astype(np.float32)
    P.paint(c, floor, (0.022, 0.018, 0.015))
    c += (floor * np.clip(1 - np.abs(np.sin(xx / 70.0)) * 6, 0, 1) * 0.006)[..., None]
    hall = mask_rect(x0, y0, x1, y1)
    far = np.exp(-(((xx - (x0 + x1) / 2) / ((x1 - x0) * 0.5)) ** 2 + ((yy - (y0 + (y1 - y0) * 0.45)) / ((y1 - y0) * 0.5)) ** 2))
    P.paint(c, hall, (0.004, 0.004, 0.005))
    c += (hall * far * 0.05)[..., None] * np.array([0.75, 0.82, 1.0])
    casing = mask_rect(x0 - 40, y0 - 40, x1 + 40, y1) - hall
    P.paint(c, casing, (0.07, 0.052, 0.038))
    P.paint(c, mask_rect(x0 - 40, y0 - 40, x0 - 32, y1), (0.12, 0.09, 0.07))
    it, box = lit_it("face", top=0.45, bottom=0.1, spot=1.5)
    fade = np.clip((y1 + 30 - yy) / 60.0, 0, 1)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h, under=fade)
    return c


def campfire(face_at, face_h, fire, seed=51):
    """The woods at night, a fire just out of the bottom of the picture; back among the trunks, where
    the light gives out, it, standing, looking at you."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    fogc = 0.05 + 0.2 * np.exp(-((yy - face_at[1] * 1.3) / (H * 0.35)) ** 2) + 0.04 * P.noise(seed, 90)
    c = np.stack([fogc * 0.82, fogc * 0.9, fogc * 1.0], axis=2)
    for i in range(16):
        x = rng.uniform(-50, W + 50)
        if abs(x - face_at[0]) < 110:
            continue
        P.trunk(c, x, -10, H * 0.9, rng.uniform(12, 26), 0.06, 0.6, 600 + i)
    it, box = lit_it("tower", top=0.3, bottom=0.7, spot=1.3, cold=0.15)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h)
    for i in range(7):
        x = rng.uniform(-50, W + 50)
        if abs(x - face_at[0]) < 160:
            continue
        P.trunk(c, x, -10, H + 30, rng.uniform(40, 90), 0.08, 0.1, 700 + i, lean=rng.uniform(-0.02, 0.02))
    d = np.sqrt(((xx - fire[0]) / 1.0) ** 2 + ((yy - fire[1]) * 1.3) ** 2)
    light = 0.45 + 3.2 * np.exp(-(d / (W * 0.5)) ** 2)
    warm = np.clip(np.exp(-(d / (W * 0.5)) ** 2) * 1.2, 0, 1)[..., None]
    c *= light[..., None] * (warm * np.array([1.25, 0.82, 0.5]) + (1 - warm) * np.array([0.8, 0.9, 1.05]))
    for _ in range(40):
        ex, ey = rng.normal(fire[0], 160), rng.uniform(H * 0.55, H)
        glow(c, ex, ey, rng.uniform(1.5, 3.5), (1.0, 0.5, 0.15), rng.uniform(0.3, 0.9))
    return c


def well(centre, rim, bottom, face_h, seed=61):
    """Looking down a stone well by torchlight: the rings of stone going down into the dark, the
    water at the bottom black, and in it, its face, looking up."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    cx, cy = centre
    r = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    ang = np.arctan2(yy - cy, xx - cx)
    t = np.clip((rim - r) / (rim - bottom), 0, 1)                       # 0 at the rim, 1 at the water
    depth = np.log1p(t * 12) / np.log(13)
    rows = depth * 14
    row = np.floor(rows)
    stones = (ang / (2 * np.pi) * (28 - row * 0.6) + row * 0.5) % 1.0
    mortar = np.clip(1 - np.minimum(rows % 1.0, 1 - rows % 1.0) * 22, 0, 1) + np.clip(1 - np.minimum(stones, 1 - stones) * 26, 0, 1)
    rough = 0.7 + 0.5 * P.noise(seed, 6)
    idx = row * 97 + np.floor(stones * 1000) // 1 * 0 + np.floor((ang / (2 * np.pi) * (28 - row * 0.6) + row * 0.5))
    tone = 0.7 + 0.45 * ((np.sin(idx * 12.9898) * 43758.5453) % 1.0)
    shaft = (r < rim) & (r > bottom)
    light = 0.95 * (1 - t) ** 1.8 + 0.02
    stone = light * rough * tone * (1 - 0.75 * np.clip(mortar, 0, 1))
    c = np.zeros((H, W, 3), np.float32)
    c += (stone * shaft)[..., None] * np.array([0.8, 0.78, 0.72])
    ring = (r >= rim) & (r < rim + 70)
    cap = 0.55 * (0.7 + 0.5 * P.noise(seed + 1, 8)) * np.clip(1 - (r - rim) / 90, 0.3, 1)
    c += (cap * ring)[..., None] * np.array([0.75, 0.72, 0.66])
    ground = (r >= rim + 70)
    g = 0.05 * (0.6 + 0.8 * P.noise(seed + 2, 12)) * np.exp(-((r - rim) / (W * 0.5)) ** 2)
    c += (g * ground)[..., None] * np.array([0.55, 0.6, 0.45])
    water = P.soften((r <= bottom).astype(np.float32), 2)
    under = np.zeros_like(c) + 0.006
    it, box = lit_it("crouch", top=0.3, bottom=0.05, spot=1.2, cold=0.5)
    P.place_face(under, P.fogged(it, (0.0, 0.01, 0.01), 0.25), box, cx, cy - face_h * 0.05, face_h, blur=2.2)
    ripples = (0.5 + 0.5 * np.sin(r / 9.0)) * np.exp(-((r - bottom * 0.75) / (bottom * 0.3)) ** 2) * 0.03
    under += ripples[..., None] * np.array([0.7, 0.8, 0.85])
    c = c * (1 - water[..., None]) + under * water[..., None]
    return c


def camcorder(face_at, face_h, seed=71):
    """Through the camcorder, the woods at night in its own light: between the trunks, half behind
    one, it."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    fogc = 0.05 + 0.1 * np.exp(-((yy - H * 0.5) / (H * 0.35)) ** 2) + 0.04 * P.noise(seed, 80)
    c = np.stack([fogc * 0.85, fogc * 0.95, fogc * 0.9], axis=2)
    for i in range(18):
        x = rng.uniform(-50, W + 50)
        if abs(x - face_at[0]) < 100:
            continue
        P.trunk(c, x, -10, H + 20, rng.uniform(14, 34), 0.06, 0.55, 800 + i)
    it, box = lit_it("tower-far", top=0.3, bottom=0.1, spot=0.9, cold=0.4)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h)
    P.trunk(c, face_at[0] - face_h * 1.05, -10, H + 30, face_h * 1.6, 0.07, 0.15, 899)
    for x, wdt in ((150, 110), (W - 120, 140)):
        P.trunk(c, x, -10, H + 30, wdt, 0.06, 0.05, 900 + x)
    d = np.sqrt(((xx - W * 0.55) / (W * 0.5)) ** 2 + ((yy - H * 0.48) / (H * 0.6)) ** 2)
    c *= (0.25 + 1.5 * np.exp(-d ** 2 * 1.4))[..., None]
    lum = c.mean(axis=2, keepdims=True)
    return lum * 0.55 + c * 0.45 * np.array([0.9, 1.05, 0.92])


def rec_overlay(a, day="DAY 4", clock="03:12 AM"):
    """The camcorder's own words on the picture: REC, the battery, the tape's day and time, the
    corners of the frame."""
    h, w, _ = a.shape
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert("RGBA")
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    try:
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", int(h * 0.045))
    except OSError:
        f = ImageFont.load_default()
    m, s = int(h * 0.06), int(h * 0.045)
    col = (235, 235, 225, 230)
    d.ellipse((m, m, m + s * 0.7, m + s * 0.7), fill=(220, 20, 20, 240))
    d.text((m + s, m - s * 0.12), "REC", font=f, fill=col)
    bx = w - m - s * 2.4
    d.rectangle((bx, m, bx + s * 2.1, m + s * 0.8), outline=col, width=3)
    d.rectangle((bx + s * 2.1, m + s * 0.25, bx + s * 2.3, m + s * 0.55), fill=col)
    d.rectangle((bx + 6, m + 6, bx + s * 0.6, m + s * 0.8 - 6), fill=(220, 40, 30, 230))
    d.text((m, h - m - s), day + "   " + clock, font=f, fill=col)
    k = int(h * 0.07)
    for (x, y, dx, dy) in ((m * 0.5, m * 0.5, 1, 1), (w - m * 0.5, m * 0.5, -1, 1), (m * 0.5, h - m * 0.5, 1, -1), (w - m * 0.5, h - m * 0.5, -1, -1)):
        d.line([(x, y + dy * k), (x, y), (x + dx * k, y)], fill=col, width=3)
    img = Image.alpha_composite(img, layer.filter(ImageFilter.GaussianBlur(0.8)))
    return np.asarray(img.convert("RGB")).astype(np.float32) / 255


def too_close(face_at, face_h, tilt=7, seed=81):
    """Static, the tracking gone, and out of it, its face, far too close."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    snow = rng.random((H // 2, W // 2)).astype(np.float32)
    snow = np.asarray(Image.fromarray((snow * 255).astype(np.uint8)).resize((W, H), Image.NEAREST), np.float32) / 255.0
    rows = rng.random(H).astype(np.float32)
    bands = np.convolve(rows, np.ones(9) / 9, mode="same")[:, None]
    c = np.repeat((0.03 + 0.09 * snow * (0.6 + 0.6 * bands))[..., None], 3, axis=2) * np.array([0.92, 0.96, 1.0])
    it, box = lit_it("face-wide", top=0.6, bottom=0.15, spot=0.7, radius=1.0, cold=0.3)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h, tilt=tilt, blur=2.5, opacity=0.92)
    for y0 in (int(rng.integers(int(H * 0.25), int(H * 0.35))), int(rng.integers(int(H * 0.7), int(H * 0.8)))):
        band = slice(y0, y0 + 26)
        c[band] = np.roll(c[band], int(rng.integers(40, 140)), axis=1) * 1.25
    return c


def over_you(face_at, face_h):
    """In bed, at night, looking up: the ceiling boards, a pale shape of moonlight on the wall, and it
    bent over you, its legs out across the room."""
    W, H = P.W, P.H
    yy, xx = P.grid()
    c = np.zeros((H, W, 3), np.float32)
    vy = H * 0.1
    ceil = P.poly_mask([(0, H), (W, H), (W * 0.82, vy), (W * 0.18, vy)])
    P.paint(c, ceil, (0.1, 0.1, 0.11))
    P.paint(c, P.poly_mask([(0, 0), (W * 0.18, vy), (0, H)]), (0.05, 0.05, 0.058))
    P.paint(c, P.poly_mask([(W, 0), (W * 0.82, vy), (W, H)]), (0.11, 0.11, 0.125))
    P.paint(c, P.poly_mask([(0, 0), (W, 0), (W * 0.82, vy), (W * 0.18, vy)]), (0.02, 0.02, 0.025))
    for k in range(1, 16):
        t = (k / 16.0) ** 1.7
        y = vy + (H - vy) * t
        xl, xr = W * 0.18 * (1 - t), W - W * 0.18 * (1 - t)
        P.paint(c, P.lines_mask([[(xl, y), (xr, y)]], 2, blur=1.0) * ceil, (0.02, 0.02, 0.022))
    moon = P.poly_mask([(W * 0.86, H * 0.2), (W * 0.99, H * 0.13), (W * 0.99, H * 0.55), (W * 0.86, H * 0.6)], blur=24)
    c += (moon * 0.25)[..., None] * np.array([0.75, 0.82, 1.0])
    it, box = lit_it("crouch", top=0.5, bottom=0.2, spot=1.4, cold=0.4)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h)
    return c


def tunnel(face_at, face_h, seed=91):
    """A mine tunnel, its props going away into the dark, a lantern on the nearest; at the end of the
    light, filling the tunnel, it."""
    W, H = P.W, P.H
    rng = np.random.default_rng(seed)
    yy, xx = P.grid()
    vx, vy = W / 2, face_at[1] + face_h * 1.2
    far = (vx - W * 0.16, vy - H * 0.13, vx + W * 0.16, vy + H * 0.1)
    c = np.zeros((H, W, 3), np.float32)
    rock = 1.2 * (0.6 + 0.6 * P.noise(seed, 14) * P.noise(seed + 1, 50))
    for pts, tone in (([(0, 0), (W, 0), (far[2], far[1]), (far[0], far[1])], 0.07), ([(0, H), (W, H), (far[2], far[3]), (far[0], far[3])], 0.09),
                      ([(0, 0), (far[0], far[1]), (far[0], far[3]), (0, H)], 0.1), ([(W, 0), (far[2], far[1]), (far[2], far[3]), (W, H)], 0.095)):
        m = P.poly_mask(pts, blur=1)
        c += (m * rock * tone)[..., None] * np.array([1.0, 0.9, 0.78])
    P.paint(c, P.soften(mask_rect(*far), 40), (0.004, 0.004, 0.004))

    def at(u):
        """The tunnel's outline at depth {u} (0 here, 1 the far end)."""
        return (far[0] * u, far[1] * u, W - (W - far[2]) * u, H - (H - far[3]) * u)

    for u in (0.08, 0.38, 0.6, 0.76):
        x0, y0, x1, y1 = at(u)
        t = 70 * (1 - u) + 8
        for pts in ([(x0, y0), (x0 + t, y0), (x0 + t, y1), (x0, y1)], [(x1 - t, y0), (x1, y0), (x1, y1), (x1 - t, y1)],
                    [(x0, y0), (x1, y0), (x1, y0 + t * 0.9), (x0, y0 + t * 0.9)]):
            m = P.poly_mask(pts, blur=1)
            P.paint(c, m, (0.14 * (1 - u * 0.6), 0.1 * (1 - u * 0.6), 0.06 * (1 - u * 0.6)))
    it, box = lit_it("tower", top=0.35, bottom=0.1, spot=0.9, cold=0.25)
    P.place_face(c, it, box, face_at[0], face_at[1], face_h)
    lx, ly = at(0.08)[0] + 120, H * 0.42
    d = np.sqrt((xx - lx) ** 2 + ((yy - ly) * 1.1) ** 2)
    c *= (0.3 + 2.8 * np.exp(-(d / (W * 0.9)) ** 2))[..., None] * np.array([1.1, 0.92, 0.7])
    glow(c, lx, ly, 14, (1.0, 0.8, 0.45), 0.9)
    glow(c, lx, ly, 70, (1.0, 0.7, 0.35), 0.12)
    return c


CUTS = {}


def load_cuts(folder):
    for n in ("crouch", "face", "face-wide", "tower", "tower-far", "side"):
        CUTS[n] = P.plate(Path(folder) / ("plate-%s.png" % n))


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


def title(a, lines, cx, y0, size, tracking, red_word, credit_y, credit_size, max_w=None):
    """The words, each line centred on {cx}, a soft dark halo behind so they read, one word red; the
    mod's name small underneath."""
    h, w, _ = a.shape
    img = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert("RGBA")
    shadow = Image.new("L", (w, h), 0)
    sd = ImageDraw.Draw(shadow)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    f = ImageFont.truetype(FELL, size)
    if max_w:
        widest = max(sum(ld.textlength(ch, font=f) for ch in ln) + tracking * (len(ln) - 1) for ln in lines)
        if widest > max_w:
            tracking *= max_w / widest
            size = int(size * max_w / widest)
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


LONG = dict(cx=470, y0=300, size=118, tracking=9, credit_y=840, credit_size=30)
SHORT = dict(cx=540, y0=90, size=124, tracking=10, credit_y=1830, credit_size=34)

# name: (size, painter, words, red word, where the words go (changes to LONG/SHORT), the camcorder's overlay)
VARIANTS = {
    "long-1-wall": ((1920, 1080), lambda: wall(CUTS["crouch"]), ["IT WAS IN", "THE WALL"], "WALL", {}, False),
    "long-2-window": ((1920, 1080), lambda: window((1135, 380), 230, (960, 130, 1640, 900), (1490, 790), (250, 720)),
                      ["IT WAS", "WATCHING ME"], "WATCHING", {}, False),
    "long-3-doorway": ((1920, 1080), lambda: doorway((1090, 80, 1470, 975), (1280, 330), 200, (120, 600), 975),
                       ["I LEFT THE", "DOOR OPEN"], "OPEN", {}, False),
    "long-4-campfire": ((1920, 1080), lambda: campfire((1330, 330), 110, (760, 1180)), ["IT", "FOLLOWED ME"], "FOLLOWED", {}, False),
    "long-5-well": ((1920, 1080), lambda: well((1300, 540), 500, 165, 230), ["IT WAS", "DOWN THERE"], "DOWN", dict(cx=440), False),
    "long-6-camcorder": ((1920, 1080), lambda: camcorder((1330, 360), 85), ["THE", "LAST TAPE"], "LAST", dict(cx=520), True),
    "short-1-woods": ((1080, 1920), lambda: woods(CUTS["face"]), ["IT WAS", "BEHIND ME"], "BEHIND", {}, False),
    "short-2-doorway": ((1080, 1920), lambda: doorway((300, 620, 780, 1760), (540, 900), 250, (80, 1500), 1760),
                        ["IT WAS IN", "MY HOUSE"], "HOUSE", {}, False),
    "short-3-close": ((1080, 1920), lambda: too_close((560, 1180), 880), ["DON'T TURN", "AROUND"], "AROUND", {}, False),
    "short-4-over": ((1080, 1920), lambda: over_you((540, 880), 300), ["IT WAS", "ALREADY THERE"], "ALREADY", dict(size=100), False),
    "short-5-window": ((1080, 1920), lambda: window((360, 880), 240, (170, 520, 910, 1700), (760, 1550), (540, 1850)),
                       ["IT WAS", "WATCHING ME"], "WATCHING", dict(size=110), False),
    "short-6-tunnel": ((1080, 1920), lambda: tunnel((540, 760), 140), ["IT WAS", "DIGGING"], "DIGGING", {}, False),
}


def render(name, outdir):
    (w, h), painter, words, red, where, rec = VARIANTS[name]
    P.size(w, h)
    a = P.photograph(painter(), 1 + sum(map(ord, name)) % 97, fringe=5, ghost=0.18, vignette=0.78 if w > h else 0.8,
                     grain=0.045, soft=2.4)
    if rec:
        a = rec_overlay(a)
    t = dict(LONG if w > h else SHORT, **where)
    fit = 2 * (t["cx"] - 60) if w > h else w - 110
    a = title(a, words, t["cx"], t["y0"], t["size"], t["tracking"], red, t["credit_y"], t["credit_size"], max_w=fit)
    save(grain(a), outdir / (name + ".jpg"))
    print("made", name)


def sheets(outdir):
    """Every one of each shape side by side, numbered, to choose from."""
    for kind, cols, tw in (("long", 3, 640), ("short", 6, 320)):
        names = [n for n in VARIANTS if n.startswith(kind) and (outdir / (n + ".jpg")).exists()]
        if not names:
            continue
        ims = [Image.open(outdir / (n + ".jpg")).convert("RGB") for n in names]
        th = int(tw * ims[0].height / ims[0].width)
        rows = (len(ims) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (tw + 12) + 12, rows * (th + 52) + 12), (24, 24, 24))
        d = ImageDraw.Draw(sheet)
        f = ImageFont.truetype(CINZEL, 26)
        for i, (n, im) in enumerate(zip(names, ims)):
            x, y = 12 + (i % cols) * (tw + 12), 12 + (i // cols) * (th + 52)
            sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
            d.text((x, y + th + 8), n, font=f, fill=(220, 215, 205))
        sheet.save(outdir / (kind + "-choices.jpg"), quality=90)


if __name__ == "__main__":
    plates, outdir = Path(sys.argv[1]), Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)
    load_cuts(plates)
    for name in (sys.argv[3:] or VARIANTS):
        render(name, outdir)
    sheets(outdir)
