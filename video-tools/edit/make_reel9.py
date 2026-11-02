#!/usr/bin/env python3
"""
The short as one found tape: the reel's single recording ("found", a frame a tick, upright) cut the
way a camcorder cuts it, by being stopped and started, so it jumps from one stretch of the walk to
the next with the clock on the screen jumping with it. Graded cold and dark like the earlier
shorts. No music: only what the camcorder's microphone would have heard, the game's own sounds
where CI saved them (steps, the cave, a pick on stone), the person holding it (one voice
throughout), and it. The tape stops dead when it has them; then the title.

    python3 make_reel7.py <reel dir> <mod sounds dir> <voice dir> <out.mp4>
"""
import subprocess
import sys
import wave
from multiprocessing import Pool
from pathlib import Path

import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FF = imageio_ffmpeg.get_ffmpeg_exe()
W, H = 1080, 1920
FPS = 20
HERE = Path(__file__).resolve().parent
FONTS = HERE / "fonts"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
TITLE = str(FONTS / "BebasNeue.woff")
LINE = str(FONTS / "Oswald-Light.woff")

CLIP = "found"
DATE = "OCT 12 2026"
# The camcorder's clock at the start of each stretch of tape (h, m, s, pm): minutes go by between them.
CLOCKS = [(6, 52, 10, True), (6, 54, 41, True), (6, 57, 3, True)]
EXPOSURE = 1.7
WARMTH = 0.10
HEAD = 8        # frames of the tape starting up before the first picture
GAP = 3         # frames of nothing between two stretches, where the tape was stopped
DEAD = 18       # frames of black after it stops for good, before the title
END = 70        # frames of the title


# ------------------------------------------------------------------ what the recording wrote down

def marks_of(reel, name):
    out = []
    for line in (reel / f"{name}.txt").read_text().split("\n"):
        parts = line.split()
        if len(parts) == 2 and parts[0].lstrip("-").isdigit():
            out.append((int(parts[0]), parts[1]))
    return sorted(out)


def camera_of(reel, name):
    out = []
    for line in (reel / f"{name}.roll").read_text().split("\n"):
        v = [float(x) for x in line.split()]
        if len(v) == 3:
            out.append(tuple(v))
    return out


def stretches(marks, n):
    """The stretches of tape kept: from each "in" to the next "cut" (or the end)."""
    ins = [t for t, k in marks if k == "in"]
    outs = [t for t, k in marks if k in ("cut", "end")]
    out = []
    for a in ins:
        b = min([e for e in outs if e > a] or [n])
        out.append((a, min(b, n)))
    return out


def first(marks, kind):
    return next((t for t, k in marks if k == kind), None)


# ------------------------------------------------------------------ the picture

PX_PER_DEGREE = 20.0   # the game's lens at this size: about twenty pixels a degree near the middle


def skew(frame, dyaw):
    """A fast pan leans the picture: the sensor is read out from top to bottom while the camera turns."""
    lean = -dyaw * PX_PER_DEGREE * 0.35
    if abs(lean) < 3:
        return frame
    lean = max(-70.0, min(70.0, lean))
    out = np.empty_like(frame)
    h = frame.shape[0]
    for y0 in range(0, h, 16):
        out[y0:y0 + 16] = np.roll(frame[y0:y0 + 16], int(round(lean * (y0 / h - 0.5))), axis=1)
    return out


def blur(frame, dyaw, dpitch):
    """The smear of a camera turned fast while the shutter was open: half a frame's worth of the turn."""
    dx, dy = -dyaw * PX_PER_DEGREE * 0.25, dpitch * PX_PER_DEGREE * 0.25
    length = (dx * dx + dy * dy) ** 0.5
    if length < 4:
        return frame
    scale = min(1.0, 90.0 / length)
    dx, dy = dx * scale, dy * scale
    n = int(min(9, length * scale / 6 + 2))
    acc = np.zeros(frame.shape, np.float32)
    for k in range(n):
        u = k / (n - 1) - 0.5
        acc += np.roll(np.roll(frame, int(round(dy * u)), axis=0), int(round(dx * u)), axis=1)
    return (acc / n).astype(np.uint8)


def tilt(frame, angle):
    """The camera tipped over by {angle} degrees, and the picture cut back to upright. Recorded upright,
    zoomed in a little to hide the corners, not all of them; recorded wide (the camera on its side),
    turned and cut with nothing missing."""
    if abs(angle) < 0.05:
        angle = 0.05
    h, w = frame.shape[:2]
    r = np.radians(angle)
    c, s_ = abs(np.cos(r)), abs(np.sin(r))
    need = max((W * c + H * s_) / w, (W * s_ + H * c) / h)
    z = need if w > h else max(1.04, 1 + 0.55 * (need - 1))
    img = Image.fromarray(frame)
    big = img.resize((int(round(w * z)), int(round(h * z))), Image.BICUBIC).rotate(angle, resample=Image.BICUBIC, expand=True, fillcolor=(0, 0, 0))
    left, top = (big.width - W) // 2, (big.height - H) // 2
    return np.asarray(big.crop((left, top, left + W, top + H)))


def punch(frame, z, cx, cy):
    """In closer by {z} on (cx, cy) of the frame."""
    if z <= 1.001:
        return frame
    w, h = W / z, H / z
    x0 = min(max(cx * W - w / 2, 0), W - w)
    y0 = min(max(cy * H - h / 2, 0), H - h)
    img = Image.fromarray(frame).crop((int(x0), int(y0), int(x0 + w), int(y0 + h)))
    return np.asarray(img.resize((W, H), Image.BICUBIC))


# On the ground (on a stump): in a little closer, on them and it.
GROUND_ZOOM = (1.0, 0.5, 0.5)


def struck(rgb, rng, s):
    """A blow: the picture thrown sideways, blurred, and a flash."""
    dx, dy = int(rng.integers(-70, 70) * s), int(rng.integers(-70, 70) * s)
    out = np.roll(np.roll(rgb, dy, axis=0), dx, axis=1)
    out = out * 0.6 + np.roll(out, int(30 * s), axis=0) * 0.4
    return np.clip(out + (s ** 2) * 0.35, 0, 1)


def font(path, size):
    return ImageFont.truetype(path, size)


def _falloff():
    """How much light reaches each part of the picture through the lens: all of it in the middle,
    less and less towards the edges, smoothly, with no edge to it anywhere."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    u, v = xx / W - 0.5, yy / H - 0.5
    r = np.sqrt(u * u * 1.5 + v * v)
    return 1.0 / (1.0 + (r / 0.42) ** 2.2 * 0.85)


FALLOFF = _falloff()


def film(x):
    """Scene light to picture: the shadows sunk deep, and the bright rolled off gently towards white."""
    y = np.clip(x, 0, None) ** 1.5
    knee, room = 0.6, 0.55
    return np.where(y < knee, y, knee + room * (1 - np.exp(-(y - knee) / room)))


def grade(frame, lo, hi, exposure, warmth, prev, rng):
    """One frame of footage, the way the found photographs are made, plus a smear of the one before."""
    img = Image.fromarray(frame)
    soft = img.filter(ImageFilter.GaussianBlur(3.2))
    sharp = img.filter(ImageFilter.GaussianBlur(0.9))
    a = (np.asarray(soft, np.float32) * 0.45 + np.asarray(sharp, np.float32) * 0.55) / 255.0
    lum = a.mean(axis=2)
    t = film((lum - lo) / (hi - lo) * exposure * FALLOFF)
    cold = np.array([0.72, 0.78, 0.86], np.float32)
    sick = np.array([0.97, 0.93, 0.83], np.float32)
    rgb = t[..., None] * (cold * (1 - t[..., None]) + sick * t[..., None])
    rgb += (a - lum[..., None]) * warmth * 1.6 * t[..., None]
    red = np.clip((a[..., 0] - a[..., 2]) * 5 - 0.1, 0, 1) * np.clip(1 - t * 1.5, 0, 1)
    rgb[..., 0] += red * 0.10
    if prev is not None:
        rgb = np.maximum(rgb, prev * 0.55)
        rgb = rgb * 0.88 + prev * 0.12
    return rgb


YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
_U, _V = XX / W - 0.5, YY / H - 0.5
VIGNETTE = (1.0 / (1.0 + 0.7 * (np.sqrt(_U * _U * 1.5 + _V * _V) / 0.58) ** 3.0)).astype(np.float32)
SCANLINES = ((np.sin(YY * np.pi / 3.0) * 0.5 + 0.5) * 0.03).astype(np.float32)
# How far each colour is spread from the middle of the lens: red a little out, blue a little in.
ABERRATION = 0.0045


def up(a, w=W, h=H):
    """A small float picture smoothly enlarged to the frame."""
    return np.asarray(Image.fromarray(a.astype(np.float32), mode="F").resize((w, h), Image.BILINEAR))


def spread(channel, k):
    """One colour of the picture scaled about the middle by 1 + k: nothing moves at the centre, the edges most."""
    if abs(k) < 1e-6:
        return channel
    w2, h2 = int(round(W * (1 + k))), int(round(H * (1 + k)))
    img = Image.fromarray(channel.astype(np.float32), mode="F").resize((w2, h2), Image.BILINEAR)
    if k > 0:
        x0, y0 = (w2 - W) // 2, (h2 - H) // 2
        return np.asarray(img.crop((x0, y0, x0 + W, y0 + H)))
    out = channel.copy()
    x0, y0 = (W - w2) // 2, (H - h2) // 2
    out[y0:y0 + h2, x0:x0 + w2] = np.asarray(img)
    return out


def sensor(out, rng, gain):
    """A small sensor with its gain turned right up in the dark: fine grain over everything, much
    heavier in the shadows, with blotches of colour in them, and the black never quite black."""
    lum = out.mean(axis=2)
    dark = np.clip(1.0 - lum / 0.45, 0, 1) ** 1.6
    amp = 0.75 + 0.9 * max(0.0, gain - 1.0) / 0.6
    out = out * 0.975 + 0.014
    # Clumped, as the camera's own processing leaves it, so it is still there after compression.
    grain = (rng.normal(0, 1, (H, W)).astype(np.float32) * 0.3 + up(rng.normal(0, 1, (H // 2, W // 2))) * 0.9
             + up(rng.normal(0, 1, (H // 3, W // 3))) * 0.45)
    out = out + (grain * (0.028 + 0.12 * dark) * amp)[..., None]
    colour = np.stack([up(rng.normal(0, 1, (H // 4, W // 4))) for _ in range(3)], axis=2)
    colour -= colour.mean(axis=2, keepdims=True)
    out = out + colour * (0.085 * dark * amp)[..., None]
    return out


def finish(rgb, rng, k, gain=1.0):
    """The lens and the tape: colour fringing towards the edges, the dark closing in, the sensor's
    noise, scanlines, and now and then the tape slipping."""
    out = np.empty_like(rgb)
    out[..., 0] = spread(rgb[..., 0], ABERRATION)
    out[..., 1] = spread(rgb[..., 1], ABERRATION * 0.35)
    out[..., 2] = spread(rgb[..., 2], -ABERRATION * 0.4)
    out *= VIGNETTE[..., None]
    out = sensor(out, rng, gain)
    out -= SCANLINES[..., None]
    if rng.random() < 0.10:
        y0 = int(rng.integers(H // 2, H - 60))
        hgt = int(rng.integers(8, 40))
        out[y0:y0 + hgt] = np.roll(out[y0:y0 + hgt], int(rng.integers(-40, 40)), axis=1) * 1.15
    band_ = ((YY - (k * 23) % (H + 300) + 150) / 150.0)
    out += (np.exp(-band_ ** 2) * 0.025)[..., None]
    return np.clip(out, 0, 1)


def glitch(rgb, rng, strength):
    """The picture failing: rows torn sideways in a wave, the colours split apart, a flash of snow."""
    out = rgb.copy()
    phase = rng.random() * 6.28
    shifts = (np.sin(np.arange(H) * 0.013 + phase) * 60 * strength + rng.normal(0, 12 * strength, H)).astype(int)
    for y0 in range(0, H, 8):
        out[y0:y0 + 8] = np.roll(rgb[y0:y0 + 8], int(shifts[y0]), axis=1)
    split = int(14 * strength)
    out[..., 0] = np.roll(out[..., 0], split, axis=1)
    out[..., 2] = np.roll(out[..., 2], -split, axis=1)
    snow_ = rng.random((H // 4, W // 4)).astype(np.float32)
    snow_ = np.repeat(np.repeat(snow_, 4, axis=0), 4, axis=1)[..., None]
    return np.clip(out * (1 - 0.35 * strength) + snow_ * 0.35 * strength, 0, 1)


def osd(img, k, clock, date):
    """The camcorder's own writing over the picture: REC, the battery, the date and time."""
    d = ImageDraw.Draw(img)
    f = font(MONO, 50)
    small = font(MONO, 40)
    white = (236, 236, 228)
    if (k // 10) % 2 == 0:
        d.ellipse((76, 112, 112, 148), fill=(222, 36, 30))
    d.text((128, 102), "REC", font=f, fill=white)
    d.rectangle((W - 196, 112, W - 92, 150), outline=white, width=4)
    d.rectangle((W - 92, 122, W - 84, 140), fill=white)
    d.rectangle((W - 188, 120, W - 166, 142), fill=white)
    h, m, s, pm = clock
    secs = s + k // FPS
    mm = m + secs // 60
    text = f"{(h + mm // 60 - 1) % 12 + 1:2d}:{mm % 60:02d}:{secs % 60:02d} {'PM' if pm else 'AM'}"
    d.text((76, H - 238), date, font=small, fill=white)
    d.text((76, H - 182), text, font=f, fill=white)
    d.text((W - 220, H - 182), "SP", font=f, fill=white)
    return img


def end_card(rng, k):
    img = Image.new("RGB", (W, H), (6, 6, 7))
    d = ImageDraw.Draw(img)
    flick = 1.0 if rng.random() > 0.08 else 0.55
    c = int(232 * flick)
    tf = font(TITLE, 210)
    title = "THE OCCUPANT"
    tw = d.textlength(title, font=tf)
    d.text(((W - tw) / 2, H * 0.40), title, font=tf, fill=(c, int(c * 0.97), int(c * 0.92)))
    lf = font(LINE, 58)
    line = "it's already in your world."
    lw = d.textlength(line, font=lf)
    a = 1.0 if k >= 16 else 0.0
    d.text(((W - lw) / 2, H * 0.40 + 240), line, font=lf, fill=(int(200 * a),) * 3)
    sf_ = font(LINE, 44)
    note = "Minecraft  ·  Fabric  ·  on Modrinth"
    nw = d.textlength(note, font=sf_)
    a = 1.0 if k >= 30 else 0.0
    d.text(((W - nw) / 2, H * 0.40 + 420), note, font=sf_, fill=(int(150 * a),) * 3)
    return finish(np.asarray(img, np.float32) / 255.0, rng, k)


def gain_for(mean):
    """How far the camcorder turns its gain up for a picture this bright on average."""
    return min(1.6, max(1.0, (20.0 / max(mean, 1.0)) ** 0.5))


def on_the_path(seed=5):
    """What is right in front of a camera lying on a dirt path: the ragged edge of the ground along
    the bottom and a pebble or two, far too near to be in focus. A darkening mask."""
    rng = np.random.default_rng(seed)
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    pts = [(0, H)]
    for x in range(0, W + 60, 60):
        pts.append((x, int(H * 0.91 + rng.normal(0, 14) - 40 * np.sin(x / W * 2.6 + 0.4))))
    pts.append((W, H))
    d.polygon(pts, fill=255)
    for _ in range(3):
        cx, cy = rng.uniform(0.05, 0.95) * W, H * rng.uniform(0.86, 0.93)
        r = rng.uniform(40, 90)
        d.ellipse((cx - r * 1.4, cy - r, cx + r * 1.4, cy + r), fill=int(rng.uniform(150, 230)))
    img = img.filter(ImageFilter.GaussianBlur(30))
    return np.asarray(img, np.float32)[..., None] / 255.0


def failures(marks, n):
    """Where the picture breaks up, by recorded frame: for a moment as it is struck, and as it hits
    the ground. Nothing at the end: the tape just stops."""
    g = {}
    hit, land = first(marks, "hit"), first(marks, "land")
    if hit is not None:
        g[hit] = 0.6
        g[hit + 1] = 0.3
    if land is not None:
        g[land] = 0.35
        g[land + 1] = 0.15
    return g


def blows(marks):
    s = {}
    for t, kind in marks:
        strength = {"hit": 1.0, "land": 0.45, "stab": 0.3}.get(kind)
        if strength:
            for i in range(6):
                s[t + i] = max(s.get(t + i, 0.0), strength * (1 - i / 6))
    return s


# From this recorded frame on, the camera is on its side and the game window was the other way up.
WIDE_FROM = None


def path_of(frames_dir, t):
    """Where recorded frame t is: in the clip of the wide frames, if CI made one, else the main one."""
    wide = Path(str(frames_dir) + "-wide")
    if WIDE_FROM is not None and t >= WIDE_FROM and wide.exists():
        return wide / f"{t - WIDE_FROM + 1:04d}.png"
    return Path(frames_dir) / f"{t + 1:04d}.png"


def read(frames_dir, t):
    img = Image.open(path_of(frames_dir, t)).convert("RGB")
    if WIDE_FROM is not None and t >= WIDE_FROM and img.width < img.height:
        # A wide frame squeezed into the upright size: put back to its own shape.
        img = img.resize((H, W), Image.BICUBIC)
    if img.width > img.height:
        # Recorded the other way up, wide: turned upright by tilt().
        return np.asarray(img)
    return np.asarray(img.resize((W, H), Image.BICUBIC))


def work(args):
    """A run of recorded frames graded and written out, each to its own place in the edit."""
    frames_dir, todo, lo, hi, gains, marks, camera = args
    frames_dir = Path(frames_dir)
    global WIDE_FROM
    hit = first(marks, "hit")
    WIDE_FROM = hit + 4 if hit is not None else None
    rng = np.random.default_rng(todo[0][0] * 7 + 1)
    fails = failures(marks, len(camera))
    hits = blows(marks)
    land = first(marks, "land")
    prev = None
    t0 = todo[0][0]
    for t in range(max(0, t0 - 2), t0):
        f = read(frames_dir, t)
        if t < len(camera):
            f = tilt(f, camera[t][2])
        prev = grade(f, lo, hi, EXPOSURE, WARMTH, prev, rng)
    last_t = None
    for t, out_path, k, clock, restart in todo:
        if last_t is not None and t != last_t + 1:
            prev = None
        last_t = t
        f = read(frames_dir, t)
        if 0 < t < len(camera):
            dyaw = (camera[t][0] - camera[t - 1][0] + 180) % 360 - 180
            f = skew(blur(f, dyaw, camera[t][1] - camera[t - 1][1]), dyaw)
        lying = land is not None and t >= land
        if lying:
            # The autofocus hunting after it lands, then finding them.
            soft = [6.0, 9.0, 7.0, 4.0, 5.0, 2.5, 1.2]
            if t - land < len(soft):
                f = np.asarray(Image.fromarray(f).filter(ImageFilter.GaussianBlur(soft[t - land])))
        if t < len(camera):
            f = tilt(f, camera[t][2])
        if lying:
            f = punch(f, *GROUND_ZOOM)
        g = gains[t]
        if g > 1.01:
            f = np.clip(f.astype(np.float32) * g, 0, 255).astype(np.uint8)
        exposure = EXPOSURE * (0.95 if lying else 1.0)
        rgb = grade(f, lo, hi, exposure, WARMTH, prev, rng)
        prev = rgb
        done = finish(rgb, rng, k, g)
        if t in hits:
            done = struck(done, rng, hits[t])
        if restart is not None:
            # The first frames after the tape is started again: the heads finding the track.
            done = glitch(done, rng, restart)
        if t in fails:
            done = glitch(done, rng, fails[t])
        img = Image.fromarray((done * 255).astype(np.uint8))
        img = osd(img, k, clock, DATE)
        img.save(out_path)
    return len(todo)


def snow(rng, k, strength=1.0, dark=0.0):
    """Tape with nothing on it: snow, bands rolling through it."""
    s = rng.random((H // 4, W // 4)).astype(np.float32)
    s = np.repeat(np.repeat(s, 4, axis=0), 4, axis=1)
    yy = np.arange(H, dtype=np.float32)[:, None]
    s *= 0.7 + 0.3 * np.sin(yy * 0.02 + k * 1.7)
    out = np.repeat(s[..., None], 3, axis=2) * 0.6 * strength
    return np.clip(out * (1 - dark), 0, 1)


def main():
    reel, mod_sounds, voice, out_mp4 = (Path(a) for a in sys.argv[1:5])
    only = sys.argv[5].split(",") if len(sys.argv) > 5 else None
    frames_dir = reel / f"frames-{CLIP}"
    frames_dir.mkdir(exist_ok=True)
    if not any(frames_dir.iterdir()):
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(reel / f"{CLIP}.mp4"), str(frames_dir / "%04d.png")], check=True)
    wide_dir = reel / f"frames-{CLIP}-wide"
    if (reel / f"{CLIP}-wide.mp4").exists():
        wide_dir.mkdir(exist_ok=True)
        if not any(wide_dir.iterdir()):
            subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(reel / f"{CLIP}-wide.mp4"), str(wide_dir / "%04d.png")], check=True)
    marks = marks_of(reel, CLIP)
    global WIDE_FROM
    WIDE_FROM = first(marks, "hit") + 4 if first(marks, "hit") is not None else None
    n = len(list(frames_dir.glob("*.png")))
    if wide_dir.exists():
        n = WIDE_FROM + len(list(wide_dir.glob("*.png")))
    camera = camera_of(reel, CLIP)
    parts = stretches(marks, n)
    print("frames", n, "stretches", parts)
    work_dir = reel / "out7"
    work_dir.mkdir(exist_ok=True)

    # Levels for the whole tape, from frames across all of it, so it never flickers between cuts.
    keep = [t for a, b in parts for t in range(a, b)]
    sample = keep[:: max(1, len(keep) // 24)]
    lums = np.concatenate([np.asarray(Image.open(path_of(frames_dir, t)).convert("L").resize((135, 240)), np.float32).ravel() / 255.0
                           for t in sample])
    lo = float(np.percentile(lums, 10))
    hi = float(max(np.percentile(lums, 99.7), lo + 0.06))
    # The camcorder's gain riding up slowly in the dark stretches.
    gains, g = {}, 1.0
    for a, b in parts:
        for t in range(a, b):
            mean = float(np.asarray(Image.open(path_of(frames_dir, t)).convert("RGB").resize((68, 120)), np.float32).mean())
            g += (gain_for(mean) - g) * 0.12
            gains[t] = g
    gains_list = [gains.get(t, 1.0) for t in range(n)]
    print("levels", round(lo, 3), round(hi, 3))

    # The edit: where each recorded frame goes, and what goes between.
    plan = []
    k = HEAD
    starts = []
    for i, (a, b) in enumerate(parts):
        starts.append((k, a, b))
        for t in range(a, b):
            j = t - a
            restart = [0.55, 0.3, 0.12][j] if i > 0 and j < 3 else (0.35 if i == 0 and j == 0 else None)
            plan.append((t, str(work_dir / f"{k:05d}.png"), k - starts[-1][0], CLOCKS[min(i, len(CLOCKS) - 1)], restart))
            k += 1
        if i < len(parts) - 1:
            k += GAP
    tape_end = k
    total = k + DEAD + END
    if only is not None:
        # Just these recorded frames, to look at.
        want = {int(x) for x in only}
        plan = [p for p in plan if p[0] in want]
        with Pool(4) as pool:
            pool.map(work, [(str(frames_dir), [p], lo, hi, gains_list, marks, camera) for p in plan])
        for p in plan:
            print(p[0], p[1])
        return
    for f in work_dir.glob("*.png"):
        f.unlink()
    chunks = [plan[i:i + 40] for i in range(0, len(plan), 40)]
    with Pool(4) as pool:
        pool.map(work, [(str(frames_dir), c, lo, hi, gains_list, marks, camera) for c in chunks])
    rng = np.random.default_rng(9)
    for j in range(HEAD):
        img = snow(rng, j, 1.0 - j / HEAD, dark=0.5 if j < 3 else 0.0)
        Image.fromarray((img * 255).astype(np.uint8)).save(work_dir / f"{j:05d}.png")
    for k0, a, b in starts[1:]:
        for j in range(GAP):
            img = snow(rng, j, 0.35, dark=0.6) if j == 1 else np.zeros((H, W, 3), np.float32)
            Image.fromarray((img * 255).astype(np.uint8)).save(work_dir / f"{k0 - GAP + j:05d}.png")
    for j in range(DEAD):
        Image.fromarray(np.zeros((H, W, 3), np.uint8)).save(work_dir / f"{tape_end + j:05d}.png")
    for j in range(END):
        Image.fromarray((end_card(rng, j) * 255).astype(np.uint8)).save(work_dir / f"{tape_end + DEAD + j:05d}.png")
    print("edit", total, "frames,", total / FPS, "seconds", "starts", starts)

    audio = reel / "audio7.wav"
    make_audio(reel, mod_sounds, voice, marks, starts, tape_end, total, audio)
    import json
    (reel / "voice_at.json").write_text(json.dumps(VOICE_AT, indent=1))
    print("voice lines at", {k: round(v, 2) for k, v in VOICE_AT.items()})
    secs = total / FPS
    kbps = int(27 * 1024 * 8 / secs - 200)
    vf = "minterpolate=fps=30:mi_mode=blend,format=yuv420p"
    common = [FF, "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(work_dir / "%05d.png")]
    log = str(reel / "x264pass7")
    subprocess.run(common + ["-vf", vf, "-c:v", "libx264", "-preset", "slow", "-tune", "grain", "-b:v", f"{kbps}k", "-pass", "1",
                             "-passlogfile", log, "-an", "-f", "mp4", "/dev/null"], check=True)
    subprocess.run(common + ["-i", str(audio), "-vf", vf, "-c:v", "libx264", "-preset", "slow", "-tune", "grain", "-b:v", f"{kbps}k", "-pass", "2",
                             "-passlogfile", log, "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(out_mp4)], check=True)
    print("made", out_mp4)


# ------------------------------------------------------------------ sound

SR = 48000


def load(path, tempo=1.0):
    af = ["-af", f"atempo={tempo}"] if tempo != 1.0 else []
    raw = subprocess.run([FF, "-loglevel", "error", "-i", str(path), *af, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def lowpass(x, cut):
    spec = np.fft.rfft(x, axis=-1)
    f = np.fft.rfftfreq(x.shape[-1], 1 / SR)
    return np.fft.irfft(spec / (1 + 1j * f / cut), x.shape[-1], axis=-1).astype(np.float32)


def band(x, lo, hi):
    return lowpass(x, hi) - lowpass(x, lo)


def tone(freq, secs, decay):
    t = np.arange(int(SR * secs)) / SR
    return (np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)).astype(np.float32)


def noise(rng, secs):
    return rng.normal(0, 1, int(SR * secs)).astype(np.float32)


def env(n, attack, decay):
    t = np.arange(n) / SR
    return (np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)).astype(np.float32)


def wind(rng, secs):
    """Wind moving in the trees: a hiss of leaves rising and falling in slow gusts."""
    leaves = band(noise(rng, secs), 400, 3000)
    t = np.arange(len(leaves)) / SR
    gusts = 0.55 + 0.45 * np.sin(t * 0.7 + rng.random() * 6) * np.sin(t * 0.23 + 0.4)
    return (leaves * gusts).astype(np.float32)


def footstep(rng):
    n = int(SR * 0.11)
    e = np.exp(-np.arange(n) / (SR * 0.022))
    thump = np.sin(2 * np.pi * (62 + rng.random() * 18) * np.arange(n) / SR) * e
    grit = lowpass(rng.normal(0, 1, n).astype(np.float32), 900) * np.exp(-np.arange(n) / (SR * 0.035))
    return (thump * 0.9 + grit * 2.2).astype(np.float32)


def thud(rng, weight=1.0):
    n = int(SR * 0.45)
    t = tone(48 + rng.random() * 10, 0.45, 0.11) * 1.2
    dirt = lowpass(noise(rng, 0.45), 1400) * env(n, 0.001, 0.06) * 2.0
    return ((t + dirt) * weight).astype(np.float32)


def scrape(rng):
    """Dragged over the ground: earth and grit and sticks under them."""
    n = int(SR * 0.55)
    grit = band(noise(rng, 0.55), 250, 2600)
    grains = (rng.random(n) < 0.004).astype(np.float32)
    crackle = band(np.convolve(grains * rng.normal(0, 1, n).astype(np.float32), np.ones(40, np.float32), "same"), 1500, 6000)
    shape = np.minimum(1, np.arange(n) / (SR * 0.05)) * np.exp(-np.maximum(0, np.arange(n) - SR * 0.3) / (SR * 0.08))
    return ((grit * 1.3 + crackle * 3.0) * shape).astype(np.float32)


# The stretches of tape in the edit, in seconds (start, end): a sound stops dead where its stretch
# of tape stops, as everything on a tape does when the camera is stopped.
STRETCHES = []


def tape_end_of(at):
    """Where the stretch of tape playing at {at} seconds stops, or None if no tape is playing then."""
    inside = [b for a, b in STRETCHES if a <= at < b]
    return inside[0] if inside else None


def put(buf, snd, at, gain, pan=0.0, until=None):
    """Into the stereo mix at {at} seconds; {pan} from -1 (left) to 1 (right). Cut off where the
    stretch of tape it starts in ends (or at {until}, the end of the stretch of whatever it belongs
    to); not at all if it starts where there is no tape, or after {until}."""
    if snd is None or at is None:
        return
    if STRETCHES:
        end = tape_end_of(at)
        if end is None or (until is not None and at >= until):
            return
        if until is not None:
            end = min(end, until)
        room = int((end - at) * SR)
        if len(snd) > room:
            snd = snd[:room].copy()
            fade = min(len(snd), int(SR * 0.004))
            if fade > 0:
                snd[-fade:] *= np.linspace(1, 0, fade)
    i = int(at * SR)
    if i >= buf.shape[1] or i + len(snd) <= 0:
        return
    j = min(buf.shape[1], i + len(snd))
    piece = snd[max(0, -i):j - i] * gain
    a = (pan + 1) * np.pi / 4
    buf[0, max(0, i):j] += piece * np.cos(a) * 1.4142
    buf[1, max(0, i):j] += piece * np.sin(a) * 1.4142


def speed(x, rate):
    """Played faster or slower, the way the game pitches its sounds."""
    if x is None or abs(rate - 1) < 1e-3:
        return x
    pos = np.arange(0, len(x) - 1, rate)
    return np.interp(pos, np.arange(len(x)), x).astype(np.float32)


def echo(x, taps):
    """Heard from somewhere enclosed: delayed, fainter copies of it."""
    out = np.concatenate([x, np.zeros(int(SR * (max(d for d, _ in taps) + 0.05)), np.float32)])
    for d, g in taps:
        i = int(SR * d)
        out[i:i + len(x)] += x * g
    return out


def wav(path):
    import soundfile as sf
    if not path.exists():
        return None
    y, sr = sf.read(str(path), dtype="float32")
    if y.ndim > 1:
        y = y.mean(axis=1)
    if sr != SR:
        y = np.interp(np.arange(0, len(y), sr / SR), np.arange(len(y)), y).astype(np.float32)
    return y


class Game:
    """The game's own sounds, where CI saved them; otherwise made here."""

    def __init__(self, root, rng):
        self.root, self.rng, self.cache = root, rng, {}

    def get(self, rel):
        if rel not in self.cache:
            p = self.root / rel
            self.cache[rel] = load(p) if p.exists() else None
        return self.cache[rel]

    def pick(self, folder, stem, count):
        for _ in range(4):
            x = self.get(f"{folder}/{stem}{int(self.rng.integers(1, count + 1))}.ogg")
            if x is not None:
                return x
        return None

    def step(self, ground="gravel"):
        x = self.pick("step", ground, 4 if ground == "gravel" else 6)
        if x is None:
            return footstep(self.rng)
        return speed(x, 0.85 + self.rng.random() * 0.2)

    def stone_hit(self):
        x = self.pick("step", "stone", 6)
        return speed(x, 0.5 * (0.9 + self.rng.random() * 0.2)) if x is not None else thud(self.rng, 0.5)

    def stone_break(self):
        x = self.pick("dig", "stone", 4)
        return speed(x, 0.8) if x is not None else thud(self.rng, 0.8)

    def cloth(self):
        """Clothes against the ground, a body moving on it."""
        x = self.pick("step", "cloth", 4)
        return speed(x, 0.8 + self.rng.random() * 0.2) if x is not None else None

    def dig(self, ground="gravel"):
        """Hands dug into the ground."""
        x = self.pick("dig", ground, 4)
        return speed(x, 0.85 + self.rng.random() * 0.2) if x is not None else footstep(self.rng)

    def cave(self, which):
        return self.get(f"ambient/cave/cave{which}.ogg")


def breath(rng, inhale, secs, voiced=0.0, pitch=180.0):
    """A breath near the microphone: air through the mouth, in or out, with a little voice in it if frightened."""
    n = int(SR * secs)
    t = np.arange(n) / SR
    air = band(rng.normal(0, 1, n).astype(np.float32), 500, 3800)
    shape = np.sin(np.pi * np.clip(t / secs, 0, 1)) ** (1.4 if inhale else 0.8)
    if not inhale:
        shape *= np.exp(-t / (secs * 0.7))
    out = air * shape
    if voiced > 0:
        f0 = pitch * (1 + 0.04 * np.sin(2 * np.pi * 6 * t))
        ph = 2 * np.pi * np.cumsum(f0) / SR
        v = sum(np.sin(ph * h) / h for h in range(1, 8))
        v = band(v.astype(np.float32), 300, 2500)
        out = out + v * shape * voiced
    return out.astype(np.float32)


def gasp(rng):
    """Breath snatched in, sharp, at what they see."""
    n = int(SR * 0.42)
    t = np.arange(n) / SR
    air = band(rng.normal(0, 1, n).astype(np.float32), 900, 5200)
    shape = np.minimum(1, t / 0.05) * np.exp(-np.maximum(0, t - 0.12) / 0.09)
    return (air * shape * 1.4).astype(np.float32)


def clatter(rng):
    """The camcorder hitting the ground: plastic knocking, the tape door rattling."""
    n = int(SR * 0.5)
    out = thud(rng, 0.9)[:n] * 0.8
    for k in range(5):
        i = int(SR * (0.045 * k + rng.random() * 0.02))
        c = band(noise(rng, 0.06), 1500, 6500) * env(int(SR * 0.06), 0.0005, 0.01) * (1.0 - 0.17 * k)
        out[i:i + len(c)] += c[:max(0, min(len(c), n - i))]
    return out.astype(np.float32)


def handling(rng, secs):
    """The microphone rubbed and buffeted as the camera tumbles."""
    n = int(SR * secs)
    x = lowpass(rng.normal(0, 1, n).astype(np.float32), 500) * 3.0
    x *= 0.5 + 0.5 * np.abs(np.sin(np.arange(n) / SR * 23))
    return x.astype(np.float32)


def blow(rng):
    """It hitting them: a deep blunt thud, through the body holding the camera."""
    n = int(SR * 0.9)
    sub = tone(44, 0.9, 0.16) * 1.6 + tone(70, 0.9, 0.07)
    body = lowpass(noise(rng, 0.9), 700) * env(n, 0.001, 0.09) * 3.2
    crack = band(noise(rng, 0.9), 1200, 6000) * env(n, 0.0003, 0.012) * 1.6
    return (sub + body + crack).astype(np.float32)


def into_them(rng):
    """A leg coming down into a body on the ground: dull, heavy, and something giving."""
    n = int(SR * 0.6)
    sub = tone(52, 0.6, 0.1) * 1.4
    body = lowpass(noise(rng, 0.6), 900) * env(n, 0.001, 0.06) * 3.0
    give = band(noise(rng, 0.6), 400, 2200) * env(n, 0.004, 0.05) * 1.6
    return (sub + body + give).astype(np.float32)


def drift(rng, n, every, lo, hi):
    """A slow, smooth wandering between lo and hi, a new place to go every {every} seconds or so."""
    k = int(n / SR / every) + 3
    pts = lo + (hi - lo) * rng.random(k)
    x = np.arange(n) / SR / every
    i = np.floor(x).astype(int)
    u = x - i
    u = u * u * (3 - 2 * u)
    return (pts[i] * (1 - u) + pts[i + 1] * u).astype(np.float32)


def wind_in_trees(rng, n):
    """Wind going through the canopy far overhead: a soft, low rush that rises and falls in slow gusts.
    Kept out of the high frequencies, so there is no hiss in it."""
    secs = n / SR
    out = np.zeros((2, n), np.float32)
    a, b = noise(rng, secs)[:n], noise(rng, secs)[:n]
    gust = drift(rng, n, 3.5, 0.3, 1.0) ** 1.5
    for c, x in enumerate((a + 0.35 * b, b + 0.35 * a)):
        body = lowpass(lowpass(x, 520), 520)
        body = body - lowpass(body, 140)
        out[c] = body * gust
    return out / max(1e-9, np.sqrt((out ** 2).mean()))


def cricket(rng, n, freq, every, pan):
    """One cricket somewhere off in the dark: a short chirp of three or four pulses, over and over,
    never quite in time; heard through the trees, a faint copy of each chirp just after it."""
    one = np.zeros(n, np.float32)
    t = rng.random() * every
    while t < n / SR - 0.3:
        f0 = freq * (1 + 0.004 * rng.normal())
        for p in range(int(rng.integers(3, 5))):
            dur = 0.018 + 0.006 * rng.random()
            m = int(dur * SR)
            tt = np.arange(m) / SR
            pulse = (np.sin(2 * np.pi * f0 * (1 - 0.01 * tt / dur) * tt) + 0.2 * np.sin(4 * np.pi * f0 * tt)) * np.sin(np.pi * tt / dur) ** 2
            i = int((t + p * 0.037) * SR)
            one[i:i + m] += pulse[:max(0, min(m, n - i))]
        t += every * (0.85 + 0.3 * rng.random())
    one = lowpass(one, 7000)
    wet = one.copy()
    for d, g in ((0.023, 0.35), (0.061, 0.18), (0.11, 0.08)):
        i = int(d * SR)
        wet[i:] += one[:-i] * g
    a = (pan + 1) * np.pi / 4
    return np.stack([wet * np.cos(a), wet * np.sin(a)]) * 1.4142


def night(rng, n, marks, starts, when):
    """The wood at night, very quiet under everything: wind high in the trees and a few crickets. The
    crickets stop when something is near: as the steps start behind them, and for good once something
    is moving in the leaves at their back. Only while the tape is running."""
    t = np.arange(n) / SR
    running = np.zeros(n, np.float32)
    for k0, a, b in starts:
        running[int(k0 / FPS * SR):int((k0 + b - a) / FPS * SR)] = 1.0
    ramp = int(SR * 0.005)
    running = np.convolve(running, np.ones(ramp, np.float32) / ramp, "same")
    crickets = cricket(rng, n, 4500, 0.72, -0.5) + cricket(rng, n, 4150, 1.05, 0.55) * 0.7 + cricket(rng, n, 4900, 0.9, 0.1) * 0.35
    crickets /= max(1e-9, np.sqrt((crickets ** 2).mean()))
    # How loud the crickets are, over the edit.
    chirp = np.ones(n, np.float32)
    echoes = [when(tt) for tt, kd in marks if kd == "echo" and when(tt) is not None]
    rustle = when(first(marks, "rustle")) if first(marks, "rustle") is not None else None
    third = starts[2][0] / FPS if len(starts) > 2 else None

    def fade_out(at, over):
        return np.clip(1 - (t - at) / over, 0, 1)

    if echoes:
        hush = fade_out(min(echoes) - 0.4, 1.2)
        if third is not None:
            hush = np.where(t >= third, 0.75, hush)
        chirp *= hush
    if rustle is not None:
        chirp *= np.where(t < rustle, 1.0, fade_out(rustle, 0.6))
    # The wind a little lower once it is right behind them.
    breath_at = when(first(marks, "breath")) if first(marks, "breath") is not None else None
    gust = np.ones(n, np.float32)
    if breath_at is not None:
        gust = np.where(t < breath_at, 1.0, 0.75).astype(np.float32)
    bed = wind_in_trees(rng, n) * 0.0026 * gust + crickets * 0.0012 * chirp
    return (bed * running).astype(np.float32)


# Where each voice line goes in the edit, in seconds, as worked out from the recording's marks.
VOICE_AT = {}

CAVE = 22   # which of the game's cave sounds plays: low, six seconds, over before the breathing


def make_audio(reel, mod_sounds, voice, marks, starts, tape_end, total, out):
    rng = np.random.default_rng(21)
    game = Game(reel / "sounds", rng)
    secs = total / FPS
    buf = np.zeros((2, int(secs * SR) + SR), np.float32)
    lines = {name: wav(voice / f"{name}.wav") for name in ("hello", "what", "ah", "no1", "no2", "no3", "scream")}
    creature = load(mod_sounds / "breath.ogg")

    def when(t):
        """Seconds into the edit of recorded frame t, or None if it was cut."""
        for k0, a, b in starts:
            if a <= t < b:
                return (k0 + t - a) / FPS
        return None

    STRETCHES[:] = [(k0 / FPS, (k0 + b - a) / FPS) for k0, a, b in starts]
    land, hit, stab = first(marks, "land"), first(marks, "hit"), first(marks, "stab")
    seen, held = first(marks, "seen"), first(marks, "breath")
    # Their own steps on the path.
    for t, kd in marks:
        if kd not in ("step", "stride") or (hit is not None and t >= hit - 2):
            continue
        at = when(t)
        if kd == "stride":
            # Running: each foot slammed down, the gravel kicked.
            put(buf, game.step("gravel"), at, 0.52 + rng.random() * 0.1, pan=rng.uniform(-0.1, 0.1))
            put(buf, speed(game.dig("gravel"), 0.9 + rng.random() * 0.2), at + 0.02, 0.16, pan=rng.uniform(-0.1, 0.1))
        else:
            put(buf, game.step("gravel"), at, 0.34 + rng.random() * 0.1, pan=rng.uniform(-0.1, 0.1))

    echoes = [tt for tt, kd in marks if kd == "echo"]
    for t, kind in marks:
        at = when(t)
        if at is None:
            continue
        if kind == "mine":
            # Somewhere below, in the dark: a pick on stone, three blocks of it; it stops dead with
            # the rest of the sound when the tape is stopped.
            end = tape_end_of(at)
            k = at
            for block in range(3):
                for _ in range(5 + block):
                    h = echo(game.stone_hit(), [(0.09, 0.45), (0.21, 0.3), (0.37, 0.18), (0.6, 0.1)])
                    put(buf, lowpass(h, 1100), k, 0.55, pan=0.35, until=end)
                    k += 0.25
                put(buf, lowpass(echo(game.stone_break(), [(0.1, 0.4), (0.25, 0.25), (0.5, 0.12)]), 1100), k, 0.6, pan=0.35, until=end)
                k += 0.55
            VOICE_AT["hello"] = min(k + 0.35, end - 0.9) if end is not None else k + 0.35
        elif kind == "echo":
            # A step behind theirs, a moment late; the last one alone, after they have stopped.
            last = t == max(echoes)
            put(buf, lowpass(game.step("gravel"), 2600), at, 0.26 if last else 0.18, pan=0.25)
        elif kind == "what":
            VOICE_AT["what"] = at
        elif kind == "cave":
            c = game.cave(CAVE)
            if c is not None:
                put(buf, c * np.minimum(1, np.arange(len(c)) / (SR * 0.3)), at, 0.55)
        elif kind == "rustle":
            # Something in the leaves behind them.
            for j in range(3):
                put(buf, lowpass(game.step("grass"), 3200), at + j * 0.13 + rng.random() * 0.03, 0.2, pan=-0.3)
            put(buf, band(noise(rng, 0.6), 1500, 6000) * env(int(SR * 0.6), 0.05, 0.2), at, 0.1, pan=-0.3)
        elif kind == "breath":
            # Breathing, right behind them: slow, deep, not theirs.
            put(buf, creature, at, 0.5)
            put(buf, creature, at + len(creature) / SR * 0.95, 0.42)
        elif kind == "seen":
            VOICE_AT["seen"] = at
        elif kind == "hit":
            VOICE_AT["hit"] = at - 0.16
            put(buf, blow(rng), at, 0.95)
        elif kind == "land":
            put(buf, clatter(rng), at, 0.8)
            end_at = when(stab) if stab is not None and when(stab) is not None else at + 5
            pass
        elif kind == "scoot":
            # Heels dug into the dirt and dragged: the game's own steps on it, slowed.
            put(buf, speed(game.step("gravel"), 0.75), at, 0.3, pan=-0.15)
            put(buf, speed(game.step("gravel"), 0.65), at + 0.12, 0.22, pan=-0.15)
        elif kind == "thump":
            # It, coming after them: a heavy step in the dirt.
            put(buf, lowpass(speed(game.step("gravel"), 0.55), 900), at, 0.42, pan=0.15)
            put(buf, thud(rng, 0.5), at, 0.25, pan=0.15)
        elif kind == "lunge":
            # It comes at them: its breath, sharp and close, and its weight on the ground.
            put(buf, speed(creature, 1.15), at, 0.5, pan=0.0)
            put(buf, lowpass(speed(game.step("gravel"), 0.5), 900), at + 0.15, 0.45, pan=0.05)
            put(buf, thud(rng, 0.6), at + 0.15, 0.3, pan=0.05)
        elif kind == "growl":
            put(buf, creature * 0.8, at, 0.32, pan=0.15)
        elif kind == "stomp":
            # Its leg driven into the dirt beside them.
            put(buf, thud(rng, 0.8), at, 0.45, pan=0.05)
            put(buf, speed(game.dig("gravel"), 0.6), at, 0.4, pan=0.05)
        elif kind == "skid":
            # A heel skidding out through the dirt, getting them nowhere.
            put(buf, speed(game.dig("gravel"), 0.65), at, 0.32, pan=-0.1)
            put(buf, game.cloth(), at + 0.05, 0.22, pan=-0.1)
        elif kind == "swept":
            # Its leg hooking the foot out from under them: a knock on bone, the foot raked back.
            put(buf, speed(game.dig("wood"), 0.7), at, 0.35, pan=0.0)
            put(buf, speed(game.dig("gravel"), 0.55), at + 0.04, 0.35, pan=-0.05)
        elif kind == "slipclaw":
            # A hand that will not hold, dragged back through the dirt.
            put(buf, speed(game.dig("gravel"), 0.6), at, 0.32, pan=-0.1)
            put(buf, speed(game.dig("grass"), 0.7), at + 0.1, 0.22, pan=-0.1)
        elif kind == "yank":
            # Hauled back by the ankle: the body dragged over the ground, hands raking it.
            for j in range(5):
                put(buf, speed(game.dig("gravel" if j % 2 else "grass"), 0.6 + 0.1 * rng.random()), at + j * 0.05,
                    0.34 * (1 - j / 7), pan=-0.05)
            put(buf, game.cloth(), at, 0.35, pan=-0.05)
        elif kind == "seen1":
            VOICE_AT["seen1"] = at
        elif kind == "kick":
            # A heel against its leg: a dry knock on bone, and the body shoved back on the ground.
            put(buf, speed(game.dig("wood"), 0.8), at, 0.3, pan=0.0)
            put(buf, thud(rng, 0.45), at, 0.3, pan=-0.1)
            put(buf, game.cloth(), at + 0.03, 0.28, pan=-0.1)
        elif kind == "roll":
            # Over onto their front: clothes and body dragged round on the dirt.
            for j in range(3):
                put(buf, game.cloth(), at + j * 0.11, 0.32, pan=-0.1)
            put(buf, speed(game.dig("grass"), 0.7), at + 0.2, 0.22, pan=-0.1)
        elif kind == "slip":
            # The foot going out from under them, scraping back through the dirt.
            put(buf, speed(game.dig("gravel"), 0.55), at, 0.35, pan=-0.1)
        elif kind == "fall":
            # Down flat on their front.
            put(buf, thud(rng, 0.9), at, 0.5, pan=-0.1)
            put(buf, game.cloth(), at + 0.02, 0.35, pan=-0.1)
            put(buf, game.dig("grass"), at + 0.04, 0.25, pan=-0.1)
        elif kind == "claw":
            # Fingers dug into the earth, and the body pulled up to them.
            put(buf, game.dig("gravel"), at, 0.3, pan=-0.1)
            put(buf, game.cloth(), at + 0.12, 0.28, pan=-0.1)
            put(buf, speed(game.dig("grass"), 0.75), at + 0.2, 0.2, pan=-0.1)
        elif kind == "grab":
            # Its leg closing round the ankle.
            put(buf, speed(game.dig("wood"), 0.6), at, 0.3, pan=-0.05)
            put(buf, game.cloth(), at + 0.02, 0.3, pan=-0.05)
        elif kind in ("no1", "no2", "no3"):
            VOICE_AT[kind] = at
        elif kind == "stab":
            VOICE_AT["scream"] = at - 0.28
            put(buf, into_them(rng), at, 0.9)
            put(buf, into_them(rng), at + 0.16, 0.75)
    buf = buf[:, :int(secs * SR)]
    # Clean: only levelled to leave headroom, nothing else done to it.
    peak = np.abs(buf).max()
    if peak > 0.89:
        buf = buf * (0.89 / peak)
    # Under it all, very quiet: the wood at night, while the tape runs.
    buf += night(rng, buf.shape[1], marks, starts, when)
    # Everything stops with the tape.
    cut = int(tape_end / FPS * SR)
    buf[:, cut - int(SR * 0.004):cut] *= np.linspace(1, 0, int(SR * 0.004))
    buf[:, cut:] = 0.0
    VOICE_AT["title"] = (tape_end + DEAD) / FPS
    peak = np.abs(buf).max()
    if peak > 0.89:
        buf = buf * (0.89 / peak)
    pcm = (np.clip(buf.T, -1, 1) * 30000).astype(np.int16)
    with wave.open(str(out), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    main()
