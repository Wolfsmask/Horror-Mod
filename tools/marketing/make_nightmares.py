#!/usr/bin/env python3
"""
The painted gallery: each scene painted from nothing round it, in the icon's way (see
nightmare.py). Its face, and its whole body cut out of the client test's frames of it against
flat green, are the only things taken from the game.

    cd tools/marketing && python3 make_nightmares.py        (needs numpy and pillow)
"""
import numpy as np
from PIL import Image, ImageFilter

from nightmare import (H, OUT, W, face_layer, lines_mask, noise, paint, photograph, place, plate,
                       poly_mask, relight, trunk)

YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def fogged(layer, colour, amount):
    """The layer, sunk into fog or dark by {amount}."""
    a = np.asarray(layer, np.float32) / 255.0
    a[..., :3] = a[..., :3] * (1 - amount) + np.asarray(colour, np.float32) * amount
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA")


def too_close(face, _):
    """Too close: its face, filling everything, tilted over you."""
    c = np.zeros((H, W, 3), np.float32) + 0.006
    place(c, face, W * 0.5, H * 0.86, H * 2.35, tilt=11, blur=2.0)
    return photograph(c, 1, vignette=0.74)


def over_you(_, cut):
    """In bed, at night, looking up: it is bent over you, its legs braced to both walls."""
    c = np.zeros((H, W, 3), np.float32)
    # The ceiling, seen from the bed: boards running away from you, the walls either side.
    vy = H * 0.12
    ceil = poly_mask([(0, H), (W, H), (W * 0.78, vy), (W * 0.22, vy)])
    left = poly_mask([(0, 0), (W * 0.22, vy), (0, H)])
    right = poly_mask([(W, 0), (W * 0.78, vy), (W, H)])
    top = poly_mask([(0, 0), (W, 0), (W * 0.78, vy), (W * 0.22, vy)])
    paint(c, ceil, (0.045, 0.045, 0.05))
    paint(c, left, (0.03, 0.03, 0.035))
    paint(c, right, (0.05, 0.05, 0.058))
    paint(c, top, (0.02, 0.02, 0.025))
    for k in range(1, 14):
        t = (k / 14.0) ** 1.7
        y = vy + (H - vy) * t
        xl, xr = W * 0.22 * (1 - t), W - W * 0.22 * (1 - t)
        paint(c, lines_mask([[(xl, y), (xr, y)]], 2, blur=1.0) * ceil, (0.02, 0.02, 0.022))
    # Moonlight through a window you cannot see, a pale shape on the right wall.
    moon = poly_mask([(W * 0.86, H * 0.18), (W * 0.97, H * 0.10), (W * 0.97, H * 0.62), (W * 0.86, H * 0.66)], blur=24)
    c += (moon * 0.09)[..., None] * np.array([0.75, 0.82, 1.0])
    # It, over you, lit only by that, from the side.
    it = relight(cut["below"], top=1.15, bottom=0.45, gain=1.0, cold=0.35)
    place(c, it, W * 0.5, H * 0.62, H * 1.05)
    return photograph(c, 2, vignette=0.72)


def window(_, cut):
    """Night, inside. Just the other side of the glass, bent down to look in, it; its legs have
    hold of the house."""
    rng = np.random.default_rng(3)
    c = np.zeros((H, W, 3), np.float32)
    wall = 0.02 + 0.025 * np.clip(1 - np.abs(XX - W / 2) / W, 0, 1) + 0.02 * (YY / H) * noise(3, 40)
    c[:] = np.stack([wall * 1.05, wall * 0.98, wall * 0.92], axis=2)
    wx0, wy0, wx1, wy1 = 700, 150, 1220, 870
    pane = np.zeros((H, W), np.float32)
    pane[wy0:wy1, wx0:wx1] = 1
    outside = np.zeros_like(c)
    sky = 0.05 + 0.09 * np.clip((YY - wy0) / (wy1 - wy0), 0, 1) ** 1.5
    outside[:] = np.stack([sky * 0.78, sky * 0.86, sky * 1.05], axis=2)
    for i in range(10):
        trunk(outside, rng.uniform(wx0 - 40, wx1 + 40), wy0 - 50, wy1, rng.uniform(14, 40), 0.03, 0.55, 40 + i)
    paint(c, pane, (0, 0, 0))
    c += outside * pane[..., None]
    # It, a step back from the glass, lit a little by the room.
    it = relight(cut["loom"], top=1.2, bottom=0.4, gain=1.0, cold=0.25)
    it = fogged(it, (0.05, 0.06, 0.08), 0.08)
    place(c, it, 975, 760, 900, tilt=-4, under=pane, blur=1.2)
    # The glass: misted, run with rain, a lamp in the room caught in it.
    c += ((noise(5, 60) * 0.05 + 0.015) * pane)[..., None] * np.array([0.8, 0.85, 0.95])
    streaks = []
    for i in range(80):
        x, y = rng.uniform(wx0, wx1), rng.uniform(wy0, wy1 - 60)
        streaks.append([(x, y), (x + rng.uniform(-3, 3), y + rng.uniform(30, 170))])
    c += (lines_mask(streaks, 2, blur=1.2) * pane)[..., None] * 0.09
    lamp = np.exp(-(((XX - 790) / 34.0) ** 2 + ((YY - 780) / 24.0) ** 2)) * pane
    c += lamp[..., None] * np.array([0.35, 0.25, 0.12])
    frame = np.zeros((H, W), np.float32)
    frame[wy0 - 34:wy1 + 34, wx0 - 34:wx1 + 34] = 1
    frame[wy0:wy1, wx0:wx1] = 0
    frame[wy0:wy1, (wx0 + wx1) // 2 - 11:(wx0 + wx1) // 2 + 11] = 1
    frame[(wy0 + wy1) // 2 - 11:(wy0 + wy1) // 2 + 11, wx0:wx1] = 1
    paint(c, frame, (0.045, 0.035, 0.03))
    sill = np.zeros((H, W), np.float32)
    sill[wy1 + 34:wy1 + 58, wx0 - 70:wx1 + 70] = 1
    paint(c, sill, (0.09, 0.08, 0.07))
    return photograph(c, 3)


def treeline(_, cut):
    """Dusk, the fog in. Between the trunks, further back than you would look, something tall."""
    rng = np.random.default_rng(4)
    c = np.zeros((H, W, 3), np.float32)
    fogc = 0.06 + 0.26 * np.exp(-((YY - 690) / 250.0) ** 2) + 0.04 * noise(6, 90)
    c[:] = np.stack([fogc * 0.86, fogc * 0.92, fogc * 1.0], axis=2)
    ground = np.clip((YY - 770) / 90.0, 0, 1)
    c *= (1 - 0.82 * ground)[..., None]
    # Far trunks, pale in the fog.
    for i in range(22):
        x = rng.uniform(-50, W + 50)
        if abs(x - 1180) < 90:
            continue
        trunk(c, x, -10, 780, rng.uniform(10, 22), 0.05, 0.75, 100 + i)
    # It.
    it = relight(cut["stand"], top=0.9, bottom=0.35, gain=0.9, cold=0.3)
    it = fogged(it, (0.20, 0.22, 0.24), 0.38)
    place(c, it, 1180, 600, 360)
    # Nearer trunks, darker; the nearest black, framing it.
    for i in range(10):
        x = rng.uniform(-50, W + 50)
        if abs(x - 1180) < 130:
            continue
        trunk(c, x, -10, 800, rng.uniform(26, 48), 0.04, 0.35, 200 + i, lean=rng.uniform(-0.02, 0.02))
    for x, wdt in ((260, 150), (930, 110), (1560, 190)):
        trunk(c, x, -10, 1100, wdt, 0.03, 0.0, 300 + int(x), lean=0.01)
    return photograph(c, 4)


def doorway(_, cut):
    """A dark doorway at the end of a room. In it, half behind the frame, looking at you."""
    c = np.zeros((H, W, 3), np.float32)
    lit = 0.015 + 0.11 * np.clip(1 - XX / (W * 0.85), 0, 1) ** 2.2
    paper = 0.85 + 0.15 * np.sin(XX / 9.0) ** 2
    c[:] = np.stack([lit * 1.1, lit * 1.0, lit * 0.86], axis=2) * paper[..., None]
    c[YY > 930] = np.array([0.025, 0.02, 0.018])
    dx0, dx1, dy0 = 760, 1120, 140
    door = np.zeros((H, W), np.float32)
    door[dy0:930, dx0:dx1] = 1
    paint(c, door, (0.0, 0.0, 0.0))
    # It, in the doorway: lit from the left by the same lamp, the rest of it in the dark.
    it = relight(cut["three"], top=1.15, bottom=0.25, gain=0.95, cold=0.2)
    place(c, it, dx1 - 150, 640, 860, tilt=-8, under=door)
    frame = np.zeros((H, W), np.float32)
    frame[dy0 - 40:930, dx0 - 40:dx0] = 1
    frame[dy0 - 40:930, dx1:dx1 + 40] = 1
    frame[dy0 - 40:dy0, dx0 - 40:dx1 + 40] = 1
    paint(c, frame, (0.075, 0.055, 0.04))
    edge = np.zeros((H, W), np.float32)
    edge[dy0 - 40:930, dx0 - 40:dx0 - 32] = 1
    paint(c, edge, (0.13, 0.10, 0.08))
    return photograph(c, 5)


def corridor(_, cut):
    """A long corridor, one bulb halfway down. Past it, at the far end, it is waiting."""
    c = np.zeros((H, W, 3), np.float32)
    vx, vy = W / 2, H * 0.47
    far = (vx - 150, vy - 120, vx + 150, vy + 140)

    def quad(pts, colour):
        paint(c, poly_mask(pts, blur=1), colour)

    quad([(0, 0), (W, 0), (far[2], far[1]), (far[0], far[1])], (0.06, 0.06, 0.055))
    quad([(0, H), (W, H), (far[2], far[3]), (far[0], far[3])], (0.09, 0.08, 0.065))
    quad([(0, 0), (far[0], far[1]), (far[0], far[3]), (0, H)], (0.10, 0.095, 0.085))
    quad([(W, 0), (far[2], far[1]), (far[2], far[3]), (W, H)], (0.095, 0.09, 0.08))
    quad([(far[0], far[1]), (far[2], far[1]), (far[2], far[3]), (far[0], far[3])], (0.008, 0.008, 0.008))
    # Doors down both walls.
    for t in (0.25, 0.5, 0.7):
        for side in (-1, 1):
            def at(u):
                x = vx + side * (150 + (W / 2 - 150) * (1 - u))
                top = far[1] - far[1] * (1 - u)
                bot = far[3] + (H - far[3]) * (1 - u)
                return x, top, bot
            xa, ta, ba = at(t)
            xb, tb, bb = at(t + 0.07)
            quad([(xa, ta + (ba - ta) * 0.15), (xb, tb + (bb - tb) * 0.15), (xb, bb), (xa, ba)], (0.025, 0.022, 0.02))
    bx, by = vx, vy - 210
    d = np.sqrt((XX - bx) ** 2 + ((YY - by) * 1.3) ** 2)
    c *= (0.3 + 1.9 * np.exp(-(d / 560.0) ** 2))[..., None] * np.array([1.05, 1.0, 0.85])
    c += (np.exp(-(d / 12.0) ** 2) * 0.9 + np.exp(-(d / 55.0) ** 2) * 0.12)[..., None] * np.array([1.0, 0.95, 0.8])
    # It, at the end, under the last of the light.
    it = relight(cut["stand"], top=1.05, bottom=0.3, gain=0.95, cold=0.15)
    place(c, it, vx + 6, far[3] - 150, 300)
    return photograph(c, 6)


def tape(face, _):
    """Static, the tracking gone, and out of it, its face."""
    rng = np.random.default_rng(7)
    snow = rng.random((H // 2, W // 2)).astype(np.float32)
    snow = np.asarray(Image.fromarray((snow * 255).astype(np.uint8)).resize((W, H), Image.NEAREST), np.float32) / 255.0
    rows = rng.random(H).astype(np.float32)
    bands = np.convolve(rows, np.ones(9) / 9, mode="same")[:, None]
    c = np.repeat((0.08 + 0.2 * snow * (0.6 + 0.6 * bands))[..., None], 3, axis=2) * np.array([0.92, 0.96, 1.0])
    place(c, face, W * 0.5, H * 0.66, H * 1.45, tilt=-6, opacity=0.6, blur=5.0)
    for y0 in (int(rng.integers(200, 300)), int(rng.integers(700, 820))):
        band = slice(y0, y0 + 26)
        c[band] = np.roll(c[band], int(rng.integers(40, 140)), axis=1) * 1.25
    return photograph(c, 8, grain=0.07, vignette=0.80)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    face = face_layer()
    cut = {"stand": plate("plate-stand"), "loom": plate("plate-loom"),
           "three": plate("plate-three-quarter"), "below": plate("plate-below")}
    scenes = [("01-too-close", too_close), ("02-over-you", over_you), ("03-the-window", window),
              ("04-the-treeline", treeline), ("05-the-doorway", doorway), ("06-the-corridor", corridor),
              ("07-the-tape", tape)]
    for name, scene in scenes:
        scene(face, cut).save(OUT / (name + ".png"))
        print("made", name)


if __name__ == "__main__":
    main()
