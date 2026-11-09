"""The two thumbnails: python3 make_thumbs.py FOLDER video-tools/edit/fonts. FOLDER holds long_151.40.png (the long video at 2:31.40,
the face in the wall) and short_27.4.png (the short at 27.4 s, it standing over them); writes thumbnail-long.jpg (1280x720) and
thumbnail-short.jpg (1080x1920) there."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
Q = Path(sys.argv[1]); FONTS = Path(sys.argv[2])
BEBAS = str(FONTS / "BebasNeue.woff")

def grade(im, contrast=1.3):
    im = ImageEnhance.Contrast(im).enhance(contrast)
    im = ImageEnhance.Sharpness(im).enhance(1.6)
    a = np.asarray(im).astype(np.float32) / 255
    # cold, sickly shadows; a little warmth left in the light
    a[..., 0] = a[..., 0] * 0.97; a[..., 2] = np.clip(a[..., 2] * 1.04 + 0.01, 0, 1)
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))

def vignette(im, strength=0.65):
    w, h = im.size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((x - w / 2) / (w / 2)) ** 2 + ((y - h / 2) / (h / 2)) ** 2) / np.sqrt(2)
    m = 1 - strength * np.clip(d, 0, 1) ** 1.8
    a = np.asarray(im).astype(np.float32) * m[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

def text(im, lines, box_x, y0, size, align="right", fill=(245, 242, 235), glow=(170, 0, 0)):
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(BEBAS, size)
    # a red glow behind, then a hard black edge, then the letters
    glow_l = Image.new("L", im.size, 0)
    gd = ImageDraw.Draw(glow_l)
    y = y0
    pos = []
    for ln in lines:
        w = d.textlength(ln, font=f)
        x = box_x - w if align == "right" else (box_x - w / 2 if align == "center" else box_x)
        pos.append((x, y, ln))
        gd.text((x, y), ln, font=f, fill=255)
        y += int(size * 0.92)
    glow_l = glow_l.filter(ImageFilter.GaussianBlur(size * 0.12))
    red = Image.new("RGB", im.size, glow)
    im = Image.composite(red, im, glow_l.point(lambda v: int(v * 0.85)))
    d = ImageDraw.Draw(im)
    for x, y, ln in pos:
        d.text((x, y), ln, font=f, fill=fill, stroke_width=max(4, size // 28), stroke_fill=(0, 0, 0))
    return im

# The long video: the face in the wall, the words on the dark side.
src = Image.open(Q / "long_151.40.png").convert("RGB")
crop = src.resize((1280, 720), Image.LANCZOS)
g_ = np.asarray(grade(crop, 1.2)).astype(np.float32) / 255
im = vignette(Image.fromarray((np.clip(g_ ** 1.25 * 0.97, 0, 1) * 255).astype(np.uint8)), 0.5)
a = np.asarray(im).astype(np.float32)
w = im.size[0]
ramp = np.clip((np.arange(w) - w * 0.6) / (w * 0.22), 0, 1) ** 1.2 * 0.75
a *= (1 - ramp)[None, :, None]
im = Image.fromarray(a.astype(np.uint8))
im = text(im, ["IT WAS IN", "THE WALL"], 1240, 230, 150)
im.save(Q / "thumbnail-long.jpg", quality=92)

# The short: it standing over them, the words in the dark above its head.
src = Image.open(Q / "short_27.4.png").convert("RGB")
im = vignette(grade(src, 1.35), 0.6)
im = text(im, ["IT WAS", "BEHIND ME"], 540, 185, 158, align="center")
im.save(Q / "thumbnail-short.jpg", quality=92)
print("ok", Image.open(Q / "thumbnail-long.jpg").size, Image.open(Q / "thumbnail-short.jpg").size)
