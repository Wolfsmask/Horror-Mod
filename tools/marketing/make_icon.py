"""The icon, second pass: features placed by hand, lit from below, tilted, smeared."""
import sys
import numpy as np
from PIL import Image, ImageFilter

SRC = '/home/user/Horror-Mod/docs/client-test/found-behind.png'
OUT = sys.argv[1]
SIDE = int(sys.argv[2])
CY = int(sys.argv[3])
TILT = float(sys.argv[4])
GLINT = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0
CX = 942
rng = np.random.default_rng(11)

frame = np.asarray(Image.open(SRC).convert('RGB')).astype(np.float32) / 255.0
H, W = frame.shape[:2]
fy, fx = np.mgrid[0:H, 0:W].astype(np.float32)
lum = frame.mean(axis=2)

def box(x0, y0, x1, y1, soft):
    """A soft-edged rectangle in frame pixels, 1 inside."""
    dx = np.maximum(np.maximum(x0 - fx, fx - x1), 0)
    dy = np.maximum(np.maximum(y0 - fy, fy - y1), 0)
    d = np.sqrt(dx * dx + dy * dy)
    return np.clip(1 - d / soft, 0, 1)

# Work only on the region round the face.
x0, x1 = CX - SIDE, CX + SIDE
y0, y1 = CY - SIDE, CY + SIDE
sl = (slice(max(0, y0), min(H, y1)), slice(max(0, x0), min(W, x1)))
f = frame[sl]
L = lum[sl]
X = fx[sl]
Y = fy[sl]

def boxl(ax0, ay0, ax1, ay1, soft):
    dx = np.maximum(np.maximum(ax0 - X, X - ax1), 0)
    dy = np.maximum(np.maximum(ay0 - Y, Y - ay1), 0)
    return np.clip(1 - np.sqrt(dx * dx + dy * dy) / soft, 0, 1)

face = np.clip((L - 0.05) / 0.23, 0, 1)
skin = (face > 0.25).astype(np.float32)
skin_soft = np.asarray(Image.fromarray((skin * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(5))).astype(np.float32) / 255

# Lit from below and a little to one side, the way a face is by a torch held low.
light = np.clip(0.46 + (Y - 330) / 240.0, 0.30, 1.3) * (1 + (X - CX) / 900.0)
# Mottled, uneven skin.
blot = rng.normal(0, 1, (f.shape[0] // 8 + 1, f.shape[1] // 8 + 1)).astype(np.float32)
blot = np.asarray(Image.fromarray(((blot * 0.5 + 0.5).clip(0, 1) * 255).astype(np.uint8)).resize((f.shape[1], f.shape[0]), Image.BICUBIC)).astype(np.float32) / 255
shade = face * light * (0.86 + 0.24 * blot)

# The teeth: not white, a dull old yellow, and dimmer.
teeth = boxl(936, 434, 988, 458, 2) * (face > 0.5)
shade = shade * (1 - 0.35 * teeth)

# The hollows, placed by hand: the eyes, and the mouth, all the way black.
eyes = boxl(924, 362, 952, 396, 7) + boxl(972, 362, 1000, 396, 7)
mouth = boxl(938, 456, 984, 602, 9)
# From the bottom of each eye, dark runs down the face: thin, uneven, fading out.
drips = np.zeros_like(X)
for x_start, x_end in ((924, 952), (972, 1000)):
    for k in range(2):
        dx = x_start + 5 + rng.uniform(0, x_end - x_start - 10)
        length = rng.uniform(22, 70)
        phase = rng.uniform(0, 6.3)
        along = np.clip((Y - 394) / length, 0, 1)
        # Thin, wandering, swelling into a drop near the end, then gone.
        wob = dx + 2.2 * np.sin((Y - 394) / rng.uniform(5, 9) + phase) + 1.2 * np.sin((Y - 394) / 3.1 + phase * 2)
        width = 0.9 + 1.4 * along ** 3
        core = np.exp(-((X - wob) / width) ** 2) * (Y > 394) * np.clip(1.15 - along, 0, 1) ** 0.6
        drips = np.maximum(drips, core)
hollows = np.clip(eyes + mouth, 0, 1)
shade = shade * (1 - hollows)

# Colour: bloodless, cold in the shadow, a sick yellow-grey in the light.
cold = np.stack([0.74, 0.79, 0.86])
sick = np.stack([0.98, 0.93, 0.82])
t = np.clip(shade, 0, 1)[..., None]
rgb = t * (cold * (1 - t) + sick * t) * 1.15
rgb = rgb * (1 - teeth[..., None] * np.array([0.0, 0.03, 0.10]))
# The gaps between the teeth, darker.
rgb *= (1 - 0.6 * teeth * (face < 0.75))[..., None]
# Nothing out in the dark but it: the stars and the specks go.
rgb *= (0.15 + 0.85 * skin_soft + 0.85 * np.clip((f[..., 0] - f[..., 2]) * 8, 0, 1))[..., None]
# The hair and the dark: dried-blood brown, very low.
reddish = np.clip((f[..., 0] - f[..., 2]) * 8, 0, 1) * (1 - skin_soft)
rgb[..., 0] += reddish * 0.09
rgb[..., 1] += reddish * 0.025
# The red down in its mouth.
deep = mouth * np.clip((Y - 500) / 100.0, 0, 1)
rgb[..., 0] += deep * 0.26
rgb[..., 1] += deep * 0.01
# Pinpoints of light, deep in each eye: it is looking at you.
for ex in (938, 986):
    d = np.sqrt((X - ex - 1.5) ** 2 + (Y - 381) ** 2)
    rgb += (np.exp(-(d / 1.6) ** 2) * 0.95 * GLINT + np.exp(-(d / 7) ** 2) * 0.08 * GLINT)[..., None] * np.array([0.9, 0.95, 1.0])

img = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))
# Tilted, the way its head goes over when it looks at you.
cx_local, cy_local = CX - max(0, x0), CY - max(0, y0)
img = img.rotate(TILT, resample=Image.BICUBIC, center=(cx_local, cy_local))
half = SIDE // 2
img = img.crop((cx_local - half, cy_local - half, cx_local + half, cy_local + half))

N = 1024
soft = img.resize((N, N), Image.BICUBIC).filter(ImageFilter.GaussianBlur(5.5))
sharp = img.resize((N, N), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1.6))
a = np.asarray(soft).astype(np.float32) / 255 * 0.52 + np.asarray(sharp).astype(np.float32) / 255 * 0.48

yy, xx = np.mgrid[0:N, 0:N] / N
# A ghost: as if it moved while the shutter was open.
ghost = np.roll(a, shift=(10, 26), axis=(0, 1))
a = np.maximum(a, ghost * 0.28)
# Fringing at the edges.
edge = np.sqrt((xx - 0.5) ** 2 + (yy - 0.5) ** 2)
w = np.clip(edge * 2.0, 0, 1)
a[..., 0] = a[..., 0] * (1 - w) + np.roll(a[..., 0], 5, axis=1) * w
a[..., 2] = a[..., 2] * (1 - w) + np.roll(a[..., 2], -5, axis=1) * w
# The dark all round it.
vig = np.clip(1.0 - (edge / 0.70) ** 2.4, 0, 1) ** 1.2
a *= vig[..., None]
# Grain, heavier in the dark; faint scanlines.
grain = rng.normal(0, 0.05, (N, N)).astype(np.float32)
a += grain[..., None] * (0.35 + 0.65 * np.clip(1 - a.mean(axis=2), 0, 1))[..., None]
a -= ((np.sin(yy * N * np.pi / 3.0) * 0.5 + 0.5) * 0.03)[..., None]
a = np.clip(a, 0, 1)

out = Image.fromarray((a * 255).astype(np.uint8)).resize((512, 512), Image.LANCZOS)
out.save(OUT)
out.resize((96, 96), Image.LANCZOS).save(OUT.replace('.png', '-96.png'))
