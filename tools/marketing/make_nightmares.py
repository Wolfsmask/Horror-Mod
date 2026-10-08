#!/usr/bin/env python3
"""The painted gallery: each scene painted from nothing, round its face. See nightmare.py."""
import numpy as np

from nightmare import (H, OUT, W, body, face_layer, legs, lines_mask, noise, paint, photograph,
                       place, poly_mask)

YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
PALE = (0.80, 0.80, 0.78)


def too_close(face):
    """Too close: its face, filling everything, tilted over you."""
    c = np.zeros((H, W, 3), np.float32) + 0.006
    place(c, face, W * 0.5, H * 0.86, H * 2.35, tilt=11)
    return photograph(c, 1, vignette=0.74)


def window(face):
    """Night, inside. A window. Just behind the glass, bent down to look in, its face."""
    rng = np.random.default_rng(2)
    c = np.zeros((H, W, 3), np.float32)
    # The room: a dark wall, faintly lit from low down by something on the floor.
    wall = 0.025 + 0.03 * np.clip(1 - np.abs(XX - W / 2) / W, 0, 1) + 0.025 * (YY / H) * noise(3, 40)[..., None].squeeze()
    c[:] = np.stack([wall * 1.05, wall * 0.98, wall * 0.92], axis=2)
    # The window: four panes, the night outside a cold blue-grey, fog lying low.
    wx0, wy0, wx1, wy1 = 640, 170, 1280, 900
    outside = np.zeros_like(c)
    sky = 0.06 + 0.10 * np.clip((YY - wy0) / (wy1 - wy0), 0, 1) ** 1.6
    outside[:] = np.stack([sky * 0.80, sky * 0.88, sky * 1.05], axis=2)
    # Trees out there, darker than the fog.
    for i in range(14):
        tx = wx0 + rng.uniform(-60, 700)
        tw = rng.uniform(10, 34)
        trunk = np.clip(1 - np.abs(XX - tx) / tw, 0, 1) * (YY > wy0 + rng.uniform(-100, 120))
        outside *= (1 - 0.55 * trunk)[..., None]
    pane = np.zeros((H, W), np.float32)
    pane[wy0:wy1, wx0:wx1] = 1
    paint(c, pane, (0, 0, 0))
    c += outside * pane[..., None]
    # Its face, a step back from the glass, bent down, looking in.
    under = pane.copy()
    place(c, face, 905, 560, 860, tilt=-9, blur=1.6, under=under)
    # The glass: misted, streaked with rain, a faint reflection of the room.
    mist = noise(5, 60) * 0.06 + 0.02
    c += (mist * pane)[..., None] * np.array([0.8, 0.85, 0.95])
    streaks = []
    for i in range(70):
        x = rng.uniform(wx0, wx1)
        y = rng.uniform(wy0, wy1 - 60)
        streaks.append([(x, y), (x + rng.uniform(-3, 3), y + rng.uniform(30, 160))])
    rain = lines_mask(streaks, 2, blur=1.2) * pane
    c += rain[..., None] * 0.10
    # The frame and the bars across it.
    frame = np.zeros((H, W), np.float32)
    frame[wy0 - 34:wy1 + 34, wx0 - 34:wx1 + 34] = 1
    frame[wy0:wy1, wx0:wx1] = 0
    frame[wy0:wy1, (wx0 + wx1) // 2 - 12:(wx0 + wx1) // 2 + 12] = 1
    frame[(wy0 + wy1) // 2 - 12:(wy0 + wy1) // 2 + 12, wx0:wx1] = 1
    paint(c, frame, (0.05, 0.04, 0.035))
    # The sill, catching a little light.
    sill = np.zeros((H, W), np.float32)
    sill[wy1 + 34:wy1 + 58, wx0 - 70:wx1 + 70] = 1
    paint(c, sill, (0.11, 0.10, 0.09))
    return photograph(c, 2)


def doorway(face):
    """A dark doorway in a dark room. Round the edge of its frame, high up, it is looking at you,
    and its legs have hold of the frame."""
    rng = np.random.default_rng(3)
    c = np.zeros((H, W, 3), np.float32)
    # A hallway wall, lit faintly from the left (a lamp out of shot).
    lit = 0.02 + 0.10 * np.clip(1 - XX / (W * 0.8), 0, 1) ** 2
    paper = 0.85 + 0.15 * np.sin(XX / 9.0) ** 2
    c[:] = np.stack([lit * 1.1, lit * 1.0, lit * 0.88], axis=2) * paper[..., None]
    floor = YY > 930
    c[floor] = np.array([0.03, 0.025, 0.02])
    # The doorway: black, and the frame round it.
    dx0, dx1, dy0 = 820, 1160, 150
    door = np.zeros((H, W), np.float32)
    door[dy0:930, dx0:dx1] = 1
    paint(c, door, (0.0, 0.0, 0.0))
    frame = np.zeros((H, W), np.float32)
    frame[dy0 - 40:930, dx0 - 40:dx0] = 1
    frame[dy0 - 40:930, dx1:dx1 + 40] = 1
    frame[dy0 - 40:dy0, dx0 - 40:dx1 + 40] = 1
    paint(c, frame, (0.08, 0.06, 0.045))
    edge = np.zeros((H, W), np.float32)
    edge[dy0 - 40:930, dx0 - 40:dx0 - 32] = 1
    paint(c, edge, (0.14, 0.11, 0.085))
    # Its face, just round the left of the frame, high up, tilted over: most of it still in the dark.
    reveal = np.zeros((H, W), np.float32)
    reveal[:, dx0 - 4:] = 1
    reveal = np.clip(reveal * 1.0, 0, 1)
    place(c, face, dx0 + 70, 330, 520, tilt=-24, under=reveal)
    # Its legs, come out of the dark and taken hold of the frame, and the wall.
    holds = [(dx0 - 44, 260), (dx0 - 44, 520), (dx0 - 140, 700), (dx1 + 44, 230)]
    segs = legs((dx0 + 110, 520), holds, rng)
    m = lines_mask(segs, 9, blur=1.8)
    paint(c, m, PALE, 0.85)
    shade = lines_mask(segs, 3, blur=1.0)
    paint(c, shade, (0.5, 0.48, 0.46), 0.5)
    return photograph(c, 3)


def treeline(face):
    """Dusk, the fog in. Between the trunks, further back than you would look, something tall."""
    rng = np.random.default_rng(4)
    c = np.zeros((H, W, 3), np.float32)
    fog = 0.05 + 0.30 * np.exp(-((YY - 700) / 260.0) ** 2) + 0.05 * noise(6, 90)
    c[:] = np.stack([fog * 0.86, fog * 0.92, fog * 1.0], axis=2)
    ground = np.clip((YY - 760) / 80.0, 0, 1)
    c *= (1 - 0.8 * ground)[..., None]
    # It, far back: the long dark body, the pale face, the legs braced out to the trunks beside it.
    fx, fy = 1180, 470
    body(c, (fx, fy + 30), (fx + 6, 690), 34, colour=(0.08, 0.075, 0.08))
    segs = legs((fx, 600), [(fx - 120, 790), (fx - 62, 780), (fx + 70, 785), (fx + 128, 792), (fx - 150, 520), (fx + 160, 500)], rng)
    paint(c, lines_mask(segs, 4, blur=1.2), (0.60, 0.60, 0.60), 0.7)
    place(c, face, fx, fy, 150, tilt=-12, opacity=0.85, blur=0.8)
    # The trunks, in layers: the far ones pale in the fog, the near ones black.
    for layer, (count, dark, wmin, wmax) in enumerate([(26, 0.35, 8, 18), (16, 0.65, 18, 40), (9, 0.95, 50, 120)]):
        for i in range(count):
            x = rng.uniform(-80, W + 80)
            if layer < 2 and abs(x - fx) < 70:
                continue
            if layer == 2 and abs(x - fx) < 260:
                continue
            w = rng.uniform(wmin, wmax)
            trunk = np.clip((w / 2 - np.abs(XX - x - 0.02 * (YY - 500))) / 2.0, 0, 1)
            paint(c, trunk, (0.02, 0.02, 0.025), dark)
    return photograph(c, 4)


def corridor(face):
    """A long corridor, one bulb halfway down. Past it, at the far end, folded down, it is waiting."""
    rng = np.random.default_rng(5)
    c = np.zeros((H, W, 3), np.float32)
    vx, vy = W / 2, H * 0.47
    far = (vx - 150, vy - 120, vx + 150, vy + 140)
    def quad(pts, colour, amt=1.0):
        paint(c, poly_mask(pts, blur=1), colour, amt)
    quad([(0, 0), (W, 0), (far[2], far[1]), (far[0], far[1])], (0.05, 0.05, 0.045))       # ceiling
    quad([(0, H), (W, H), (far[2], far[3]), (far[0], far[3])], (0.07, 0.06, 0.05))        # floor
    quad([(0, 0), (far[0], far[1]), (far[0], far[3]), (0, H)], (0.08, 0.075, 0.07))       # left
    quad([(W, 0), (far[2], far[1]), (far[2], far[3]), (W, H)], (0.075, 0.07, 0.065))      # right
    quad([(far[0], far[1]), (far[2], far[1]), (far[2], far[3]), (far[0], far[3])], (0, 0, 0))
    # Doors down the walls, darker.
    for t in (0.18, 0.42, 0.62):
        for side in (-1, 1):
            xa = vx + side * (vx - (vx - 150)) * 1 + side * (W / 2 - 150) * (1 - t) ** 1.0
            xb = vx + side * 150 + side * (W / 2 - 150) * (1 - (t + 0.08)) ** 1.0
            top_a = vy - 120 - (vy - 120) * (1 - t)
            top_b = vy - 120 - (vy - 120) * (1 - t - 0.08)
            bot_a = vy + 140 + (H - vy - 140) * (1 - t)
            bot_b = vy + 140 + (H - vy - 140) * (1 - t - 0.08)
            quad([(xa, top_a + (bot_a - top_a) * 0.12), (xb, top_b + (bot_b - top_b) * 0.12), (xb, bot_b), (xa, bot_a)], (0.02, 0.018, 0.016))
    # The bulb, halfway down: a pool of light under it, and the dark beyond.
    bx, by = vx, vy - 230
    d = np.sqrt((XX - bx) ** 2 + ((YY - by) * 1.3) ** 2)
    c *= (0.35 + 1.8 * np.exp(-(d / 520.0) ** 2))[..., None] * np.array([1.05, 1.0, 0.86])
    c += (np.exp(-(d / 14.0) ** 2) * 0.9 + np.exp(-(d / 60.0) ** 2) * 0.15)[..., None] * np.array([1.0, 0.95, 0.8])
    depth = np.clip(1 - np.sqrt(((XX - vx) / 700) ** 2 + ((YY - vy) / 420) ** 2), 0, 1)
    c *= (1 - 0.75 * depth ** 1.5)[..., None]
    # It, at the end, folded down into the corridor, its legs braced to both walls.
    fx, fy = vx + 8, vy + 30
    segs = legs((fx, fy + 70), [(far[0] + 4, fy - 60), (far[0] + 4, fy + 90), (far[2] - 4, fy - 70), (far[2] - 4, fy + 100), (fx - 60, far[3]), (fx + 60, far[3])], rng)
    paint(c, lines_mask(segs, 4, blur=1.0), (0.55, 0.54, 0.52), 0.8)
    place(c, face, fx, fy, 165, tilt=14, opacity=0.95)
    return photograph(c, 5)


def tape(face):
    """Static, the tracking gone, and out of it, its face."""
    rng = np.random.default_rng(6)
    snow = rng.random((H // 2, W // 2)).astype(np.float32)
    from PIL import Image
    snow = np.asarray(Image.fromarray((snow * 255).astype(np.uint8)).resize((W, H), Image.NEAREST), np.float32) / 255.0
    rows = rng.random(H).astype(np.float32)
    bands = np.convolve(rows, np.ones(9) / 9, mode="same")[:, None]
    c = np.repeat((0.10 + 0.22 * snow * (0.6 + 0.6 * bands))[..., None], 3, axis=2)
    c *= np.array([0.92, 0.96, 1.0])
    place(c, face, W * 0.5, H * 0.66, H * 1.45, tilt=-6, opacity=0.55, blur=2.5)
    # A tracking tear across it.
    for y0 in (rng.integers(200, 300), rng.integers(700, 820)):
        band = slice(int(y0), int(y0) + 26)
        c[band] = np.roll(c[band], int(rng.integers(40, 140)), axis=1) * 1.25
    return photograph(c, 6, grain=0.07, vignette=0.80)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    face = face_layer()
    scenes = [("01-too-close", too_close), ("02-the-window", window), ("03-the-doorway", doorway),
              ("04-the-treeline", treeline), ("05-the-corridor", corridor), ("06-the-tape", tape)]
    for name, scene in scenes:
        scene(face).save(OUT / (name + ".png"))
        print("made", name)


if __name__ == "__main__":
    main()
