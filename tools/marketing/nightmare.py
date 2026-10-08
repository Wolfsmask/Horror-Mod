#!/usr/bin/env python3
"""
Painted gallery images: not frames of the game but scenes made from nothing, in the icon's way.
The only thing taken from the game is its face (from the client test's close frame of it, made
pale, hollow-eyed and lit from below, as for the icon); everything round it is painted here:
rooms, windows, woods, fog, static. Then the whole picture is made into a bad photograph:
smeared, cold, grained, fringed, the dark closing in.

    python3 tools/marketing/nightmare.py        (needs numpy and pillow)

Writes docs/modrinth/gallery-nightmare/.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
FRAME = ROOT / "docs/client-test/found-behind.png"
OUT = ROOT / "docs/modrinth/gallery-nightmare"
W, H = 1920, 1080


# ---------------------------------------------------------------- the face

def face_layer(glint=1.0, light_from_below=True, seed=1):
    """Its face, alone, as RGBA floats (h, w, 4) at four times the frame's size: pale, the eyes
    black hollows with a pinpoint of light far back in each, the mouth open down into dark red."""
    rng = np.random.default_rng(seed)
    x0, y0, x1, y1 = 884, 300, 1036, 616
    src = np.asarray(Image.open(FRAME).convert("RGB"), np.float32)[y0:y1, x0:x1] / 255.0
    h, w = src.shape[:2]
    Y, X = np.mgrid[y0:y1, x0:x1].astype(np.float32)

    def box(ax0, ay0, ax1, ay1, soft):
        dx = np.maximum(np.maximum(ax0 - X, X - ax1), 0)
        dy = np.maximum(np.maximum(ay0 - Y, Y - ay1), 0)
        return np.clip(1 - np.sqrt(dx * dx + dy * dy) / soft, 0, 1)

    lum = src.mean(axis=2)
    face = np.clip((lum - 0.05) / 0.23, 0, 1)
    skin = (face > 0.25).astype(np.uint8) * 255
    # The whole head's outline, its hollows filled in.
    shape = Image.fromarray(skin).filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.MinFilter(13))
    alpha = np.asarray(shape.filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255.0

    light = np.clip(0.46 + (Y - 330) / 240.0, 0.30, 1.3) if light_from_below else np.ones_like(Y)
    blot = rng.normal(0, 1, (h // 8 + 1, w // 8 + 1))
    blot = np.asarray(Image.fromarray(((blot * 0.5 + 0.5).clip(0, 1) * 255).astype(np.uint8))
                      .resize((w, h), Image.BICUBIC), np.float32) / 255.0
    shade = np.clip(face, 0.55, 1) * light * (0.86 + 0.24 * blot)
    teeth = box(936, 434, 988, 458, 2) * (face > 0.5)
    eyes = box(924, 362, 952, 396, 4) + box(972, 362, 1000, 396, 4)
    mouth = box(938, 456, 984, 602, 6)
    hollows = np.clip(eyes + mouth, 0, 1)
    shade = shade * (1 - 0.35 * teeth) * (1 - hollows)
    shade *= 1 - 0.6 * teeth * (face < 0.75)

    t = np.clip(shade, 0, 1)[..., None]
    cold = np.array([0.74, 0.79, 0.86], np.float32)
    sick = np.array([0.98, 0.93, 0.82], np.float32)
    rgb = t * (cold * (1 - t) + sick * t) * 1.15
    deep = mouth * np.clip((Y - 500) / 100.0, 0, 1)
    rgb[..., 0] += deep * 0.28
    rgb[..., 1] += deep * 0.01
    for ex in (938, 986):
        d = np.sqrt((X - ex - 1.5) ** 2 + (Y - 381) ** 2)
        rgb += (np.exp(-(d / 1.6) ** 2) * 0.95 * glint + np.exp(-(d / 7) ** 2) * 0.08 * glint)[..., None] * np.array([0.9, 0.95, 1.0])
    out = np.concatenate([np.clip(rgb, 0, 1), alpha[..., None]], axis=2)
    big = Image.fromarray((out * 255).astype(np.uint8), "RGBA").resize((w * 4, h * 4), Image.BICUBIC)
    return big


def place(canvas, layer, cx, cy, height, tilt=0.0, opacity=1.0, blur=0.0, under=None):
    """Draws an RGBA layer onto a float canvas, centred at (cx, cy), {height} pixels tall,
    tilted, faded, softened. {under} is an optional mask (H, W) of where it may show."""
    scale = height / layer.height
    img = layer.resize((max(1, int(layer.width * scale)), max(1, int(height))), Image.LANCZOS)
    if tilt:
        img = img.rotate(tilt, resample=Image.BICUBIC, expand=True)
    if blur:
        img = img.filter(ImageFilter.GaussianBlur(blur))
    a = np.asarray(img, np.float32) / 255.0
    x0, y0 = int(cx - img.width / 2), int(cy - img.height / 2)
    xa, ya = max(0, x0), max(0, y0)
    xb, yb = min(W, x0 + img.width), min(H, y0 + img.height)
    if xb <= xa or yb <= ya:
        return
    part = a[ya - y0:yb - y0, xa - x0:xb - x0]
    al = part[..., 3:4] * opacity
    if under is not None:
        al = al * under[ya:yb, xa:xb, None]
    canvas[ya:yb, xa:xb] = canvas[ya:yb, xa:xb] * (1 - al) + part[..., :3] * al


# ---------------------------------------------------------------- painting helpers

def noise(seed, cells, size=(H, W)):
    """Smooth noise in [0, 1]."""
    rng = np.random.default_rng(seed)
    small = rng.random((max(2, size[0] // cells), max(2, size[1] // cells)))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((size[1], size[0]), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255.0


def poly_mask(points, blur=0.0):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon(points, fill=255)
    if blur:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, np.float32) / 255.0


def lines_mask(segments, width, blur=1.5):
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    for seg in segments:
        d.line(seg, fill=255, width=width, joint="curve")
    if blur:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, np.float32) / 255.0


def paint(canvas, mask, colour, amount=1.0):
    m = (mask * amount)[..., None]
    canvas[:] = canvas * (1 - m) + np.asarray(colour, np.float32) * m


def legs(anchor, holds, rng, thick=7):
    """Its long legs, from {anchor} (the body) to each hold: thigh and shin, the joint pushed out
    sideways and a little up, like an arm braced against a wall."""
    segs = []
    ax, ay = anchor
    for hx, hy in holds:
        mx, my = (ax + hx) / 2, (ay + hy) / 2
        dx, dy = hx - ax, hy - ay
        length = np.hypot(dx, dy)
        nx, ny = -dy / length, dx / length
        if ny > 0:
            nx, ny = -nx, -ny                       # the joint goes up and out
        k = length * rng.uniform(0.18, 0.3)
        knee = (mx + nx * k, my + ny * k)
        segs.append([(ax, ay), knee, (hx, hy)])
    return segs


def body(canvas, top, bottom, width, colour=(0.035, 0.025, 0.025)):
    """The long dark body and the hair hanging round it, a narrow shape going down from the face."""
    (tx, ty), (bx, by) = top, bottom
    pts = [(tx - width * 0.6, ty), (tx + width * 0.6, ty), (bx + width * 0.35, by), (bx - width * 0.35, by)]
    paint(canvas, poly_mask(pts, blur=3), colour)


# ---------------------------------------------------------------- the photograph

def photograph(rgb, seed, fringe=6, ghost=0.25, vignette=0.68, grain=0.05):
    """Makes a painted scene into a bad photograph of it."""
    rng = np.random.default_rng(seed)
    img = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))
    soft = np.asarray(img.filter(ImageFilter.GaussianBlur(3.5)), np.float32) / 255.0
    sharp = np.asarray(img.filter(ImageFilter.GaussianBlur(1.0)), np.float32) / 255.0
    a = soft * 0.5 + sharp * 0.5
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    if ghost:
        a = np.maximum(a, np.roll(a, shift=(8, 30), axis=(0, 1)) * ghost)
    u, v = xx / W - 0.5, yy / H - 0.5
    edge = np.sqrt(u * u + (v * H / W) ** 2 * 1.8)
    w = np.clip(edge * 2.2, 0, 1)
    a[..., 0] = a[..., 0] * (1 - w) + np.roll(a[..., 0], fringe, axis=1) * w
    a[..., 2] = a[..., 2] * (1 - w) + np.roll(a[..., 2], -fringe, axis=1) * w
    vig = np.clip(1.0 - (np.sqrt(u * u + v * v * 1.35) / vignette) ** 2.3, 0, 1) ** 1.25
    a *= vig[..., None]
    g = rng.normal(0, grain, (H, W)).astype(np.float32)
    a += g[..., None] * (0.35 + 0.65 * np.clip(1 - a.mean(axis=2), 0, 1))[..., None]
    a -= ((np.sin(yy * np.pi / 3.0) * 0.5 + 0.5) * 0.028)[..., None]
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
