"""
The look shared by the gallery and the creator thumbnails: a film grade and trailer type.

Grade: lifted out of the dark, a strong S-curve, teal shadows and warm highlights, halation (the
red glow film gives round the sun and fires), a little colour fringing towards the edges, a heavy
vignette and fine grain. Type: Bebas Neue for titles, Oswald for the small lines, widely spaced,
with a soft shadow and nothing else: no outlines, no glow.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS = Path(__file__).resolve().parent / "fonts"
WHITE = (238, 232, 222)
RED = (200, 34, 26)
_rng = np.random.default_rng(1666)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def title_font(size):
    return font("BebasNeue.woff", size)


def line_font(size, heavy=False):
    return font("Oswald-Medium.woff" if heavy else "Oswald-Light.woff", size)


def grade(img, exposure=1.3, saturation=0.85, warmth=1.0, vignette=0.7, halation=0.45, grain=0.03):
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    # Out of the dark: night frames are mostly shadow, and a phone screen shows none of it.
    a = np.clip(a, 0, 1) ** (1.0 / exposure)
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    a = lum + (a - lum) * saturation
    # S-curve: deep blacks, bright highlights, the middle left alone.
    a = np.clip(a, 0, 1)
    s = a * a * (3 - 2 * a)
    a = a * 0.45 + s * 0.55
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    # Teal in the shadows, amber in the light.
    a += ((1 - lum) ** 2.2) * np.array([-0.03, 0.025, 0.045], np.float32)
    a += (lum ** 2.0) * warmth * np.array([0.05, 0.015, -0.035], np.float32)
    a = np.clip(a, 0, 1)
    # Halation: the brightest things bleed red into the dark round them.
    if halation > 0:
        bright = np.clip((lum - 0.68) / 0.32, 0, 1)
        glow_img = Image.fromarray((bright[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(max(6, w // 90)))
        glow = np.asarray(glow_img).astype(np.float32)[..., None] / 255.0
        a = 1 - (1 - a) * (1 - glow * halation * np.array([1.0, 0.42, 0.25], np.float32))
    # Colour fringing, more towards the edges, as a real lens does.
    out = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    r, g, b = out.split()
    r = r.resize((int(w * 1.004), int(h * 1.004)), Image.BICUBIC).crop((int(w * 0.002), int(h * 0.002), int(w * 0.002) + w, int(h * 0.002) + h))
    out = Image.merge("RGB", (r, g, b))
    a = np.asarray(out).astype(np.float32) / 255.0
    # The dark closes in from the edges.
    yy, xx = np.mgrid[0:h, 0:w]
    rr = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2 * 0.75)
    a *= (1.0 - vignette * np.clip(rr - 0.35, 0, 1) ** 1.4)[..., None]
    # Grain, more in the mid-tones, as film has it.
    lum = (a @ np.array([0.299, 0.587, 0.114], np.float32))[..., None]
    a += _rng.normal(0.0, grain, size=(h, w, 1)).astype(np.float32) * (0.5 + lum * (1 - lum) * 2)
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def cover(src, size, focus=(0.5, 0.5), zoom=1.0):
    """{size} cut from {src} around {focus}, scaled to cover the frame."""
    img = src.convert("RGB")
    tw, th = size
    scale = max(tw / img.width, th / img.height) * zoom
    img = img.resize((max(tw, round(img.width * scale)), max(th, round(img.height * scale))), Image.LANCZOS)
    left = int(np.clip(focus[0] * img.width - tw / 2, 0, img.width - tw))
    top = int(np.clip(focus[1] * img.height - th / 2, 0, img.height - th))
    return img.crop((left, top, left + tw, top + th))


def tracked_width(draw, text, f, tracking):
    return sum(draw.textlength(c, font=f) for c in text) + tracking * max(0, len(text) - 1)


def tracked(draw, xy, text, f, fill, tracking):
    x, y = xy
    for c in text:
        draw.text((x, y), c, font=f, fill=fill)
        x += draw.textlength(c, font=f) + tracking


def shadowed(img, xy, text, f, fill, tracking=0, shadow=0.75, blur=None):
    """Text with a soft shadow under it, the way a title sits on a poster."""
    d = ImageDraw.Draw(img)
    size = f.size
    blur = blur if blur is not None else max(3, size // 14)
    mask = Image.new("L", img.size, 0)
    tracked(ImageDraw.Draw(mask), (xy[0] + size * 0.03, xy[1] + size * 0.05), text, f, 255, tracking)
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    img.paste(Image.new("RGB", img.size, (0, 0, 0)), (0, 0), mask.point(lambda v: int(v * shadow)))
    tracked(ImageDraw.Draw(img), xy, text, f, fill, tracking)
    return tracked_width(d, text, f, tracking)


def darken_side(img, side, strength=0.65):
    """A gradient from one side, so words over the picture can be read."""
    w, h = img.size
    a = np.asarray(img).astype(np.float32)
    x = np.linspace(0, 1, w)[None, :, None]
    if side == "left":
        g = np.clip(1 - x / 0.62, 0, 1)
    elif side == "right":
        g = np.clip((x - 0.38) / 0.62, 0, 1)
    elif side == "top":
        y = np.linspace(0, 1, h)[:, None, None]
        g = np.clip(1 - y / 0.55, 0, 1)
    else:
        y = np.linspace(0, 1, h)[:, None, None]
        g = np.clip((y - 0.45) / 0.55, 0, 1)
    a *= 1 - strength * g ** 1.3
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def _largest_part(mask, keep=0.25):
    """The biggest joined-up shape in a small boolean mask, and anything near its size (its legs,
    which can come apart from it at a distance). Specks (smoke, a bird, a flicker) are dropped."""
    h, w = mask.shape
    label = np.zeros((h, w), np.int32)
    sizes = [0]
    for y in range(h):
        for x in range(w):
            if not mask[y, x] or label[y, x]:
                continue
            n = len(sizes)
            stack = [(y, x)]
            label[y, x] = n
            count = 0
            while stack:
                cy, cx = stack.pop()
                count += 1
                for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not label[ny, nx]:
                        label[ny, nx] = n
                        stack.append((ny, nx))
            sizes.append(count)
    if len(sizes) == 1:
        return mask
    biggest = max(sizes)
    keepers = [i for i, c in enumerate(sizes) if i and c >= biggest * keep]
    return np.isin(label, keepers)


def composite(still, plate, depth_blur=None, fog=0.3, darken=0.82):
    """
    A film still from two frames of the game taken from the same camera, one with it and one
    without (the plate). The difference between them is exactly where it is; the world behind it
    is taken from the plate, softened as a long lens would (shallow focus), darkened and pushed
    back into the fog, and a little shadow gathers round it, so it stands out from everything.
    """
    s = np.asarray(still.convert("RGB")).astype(np.float32) / 255.0
    p = np.asarray(plate.convert("RGB").resize(still.size)).astype(np.float32) / 255.0
    h, w = s.shape[:2]
    diff = np.abs(s - p).sum(axis=2)
    small_w = 480
    small_h = int(h * small_w / w)
    d = Image.fromarray((np.clip(diff * 4, 0, 1) * 255).astype(np.uint8)).resize((small_w, small_h), Image.BILINEAR)
    m = np.asarray(d) > 40
    # Close the gaps between thin legs and body, then keep only it.
    mi = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))
    m = _largest_part(np.asarray(mi) > 127)
    mask = Image.fromarray((m * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
    # Only where it really changed, at full size; softened at the edge.
    fine = Image.fromarray((np.clip(diff * 6, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))
    mask = Image.fromarray(np.minimum(np.asarray(mask), np.asarray(fine))).filter(ImageFilter.GaussianBlur(1.2))
    m = np.asarray(mask).astype(np.float32)[..., None] / 255.0
    if m.max() < 0.5:
        return still.convert("RGB")                          # it is not in the picture: nothing to do
    # The world behind: soft, darker, further into the fog.
    blur = depth_blur if depth_blur is not None else max(3, w // 300)
    bg = np.asarray(Image.fromarray((p * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur))).astype(np.float32) / 255.0
    horizon = p[int(h * 0.35):int(h * 0.55)].reshape(-1, 3).mean(axis=0)
    bg = bg * (1 - fog) + horizon * fog
    bg *= darken
    # A shadow gathering round it.
    halo = np.asarray(mask.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(max(8, w // 70)))).astype(np.float32)[..., None] / 255.0
    bg *= 1 - 0.35 * halo
    out = bg * (1 - m) + s * m
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def still(folder, name):
    """A still by name from {folder}, finished against its plate if it has one."""
    shot = Image.open(Path(folder) / (name + ".png"))
    plate = Path(folder) / (name + "-plate.png")
    return composite(shot, Image.open(plate)) if plate.exists() else shot.convert("RGB")
