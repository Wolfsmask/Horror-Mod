#!/usr/bin/env python3
"""
The long video: the tape's scenes (one clip each, recorded wide, in pieces) cut together the way
the short is, a camcorder stopped and started over four days, with the same grade, grain, lens and
on-screen writing, landscape. Sound: what the camcorder's microphone would hear, the game's own
sounds and the mod's (its knocks, its whispers, the radio's static, its quiet score under it all),
and room left for the voice, which is recorded separately (see the voice sheet this writes).

    python3 make_tape.py <dir with the clips> <mod sounds dir> <voice dir> <out.mp4>
"""
import json
import subprocess
import sys
import types
import wave
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
BASE = HERE / "make_reel9.py"
R = types.ModuleType("reel")
R.__dict__["__file__"] = str(BASE)
exec(compile(BASE.read_text().replace("W, H = 1080, 1920", "W, H = 1920, 1080"), str(BASE), "exec"), R.__dict__)
W, H, FPS, SR = R.W, R.H, R.FPS, R.SR
sys.path.insert(0, str(HERE))
import voice as V  # noqa: E402
RECORDED = set()  # (scene, line) of every line there is a recording of
VOICE_GAIN = 0.45  # the voice over the normalised mix: with the camera held out from their face, its loud parts about -25 dBFS
AMBIENCE_GAIN = 1.0  # the air and the tape hiss over the normalised mix (their levels are set in ambience())
FF = R.FF
SIM = HERE.parent / "sim"

# ------------------------------------------------------------------ the tape, in order

# name, date, the clock at each stretch's start, exposure, warmth, where the steps fall, the air, score level
SCENES = [
    dict(name="home", date="OCT 12 2026", clocks=[(6, 41, 8, True)], exposure=0.31, warmth=0.16, act=1, day=0.25),
    dict(name="camp", date="OCT 13 2026", clocks=[(3, 12, 40, True)], exposure=0.32, warmth=0.16, act=1, day=0.45),
    dict(name="pasture", date="OCT 13 2026", clocks=[(4, 5, 22, True)], exposure=0.34, warmth=0.16, act=2, day=0.5),
    dict(name="well", date="OCT 13 2026", clocks=[(5, 2, 31, True)], exposure=0.33, warmth=0.16, act=2, day=0.4),
    dict(name="field", date="OCT 13 2026", clocks=[(6, 22, 10, True)], exposure=0.29, warmth=0.16, act=2, day=0.2),
    dict(name="intruder", date="OCT 13 2026", clocks=[(9, 31, 55, True)], exposure=0.25, warmth=0.16, act=2, day=0.1),
    dict(name="visitor", date="OCT 14 2026", clocks=[(6, 24, 3, False)], exposure=0.32, warmth=0.16, act=2, day=0.35),
    dict(name="doorway", date="OCT 14 2026", clocks=[(10, 8, 40, False)], exposure=0.33, warmth=0.16, act=2, day=0.5),
    dict(name="radio", date="OCT 14 2026", clocks=[(1, 2, 47, True)], exposure=0.33, warmth=0.16, act=2, day=0.45),
    dict(name="house", date="OCT 14 2026", clocks=[(4, 31, 18, True)], exposure=0.32, warmth=0.16, act=3, day=0.25),
    dict(name="chapel", date="OCT 14 2026", clocks=[(6, 27, 44, True)], exposure=0.29, warmth=0.16, act=3, day=0.15),
    dict(name="graves", date="OCT 14 2026", clocks=[(7, 9, 12, True)], exposure=0.21, warmth=0.16, act=3, day=0.05),
    dict(name="knock", date="OCT 14 2026", clocks=[(11, 47, 2, True)], exposure=0.27, warmth=0.16, act=3, day=0.15),
    dict(name="morning", date="OCT 15 2026", clocks=[(5, 21, 40, False), (5, 38, 12, False)], exposure=0.32, warmth=0.16, act=3, day=0.3),
    dict(name="mine", date="OCT 15 2026", clocks=[(2, 26, 15, True), (2, 41, 3, True)], exposure=0.25, warmth=0.16, act=3, day=0.2),
    dict(name="found", date="OCT 15 2026", clocks=[(6, 52, 10, True), (6, 54, 41, True), (6, 57, 3, True)], exposure=0.13,
         warmth=0.16, act=4),
]
BY = {s["name"]: s for s in SCENES}

HEAD = 10        # frames of snow as the tape starts
GAP = 7          # between two clips: black, a flash of snow
INNER_GAP = 3    # between two stretches of one clip
COLD = 44        # the cold open: the face at the window
TITLE = 56       # the title after it
DEAD = 18
END = 80


def marks_of(clips, name):
    out = []
    for line in (clips / f"{name}.0.txt").read_text().split("\n"):
        p = line.split()
        if len(p) == 2 and p[0].lstrip("-").isdigit():
            out.append((int(p[0]), p[1]))
    return sorted(out)


def camera_of(clips, name):
    out = []
    for line in (clips / f"{name}.0.roll").read_text().split("\n"):
        v = [float(x) for x in line.split()]
        if len(v) >= 3:
            out.append((v[0], v[1], v[2], v[3] if len(v) > 3 else 55.0))
    # The picture lags the camera by a frame (what is drawn is where it was put the frame before), in
    # the clips recorded with the world let on a tick at a time; the camera is put back in step with it.
    if name != "found" and out:
        out = [out[0]] + out[:-1]
    return out


def first(marks, kind):
    return next((t for t, k in marks if k == kind), None)


def stretches(marks, n):
    ins = [t for t, k in marks if k == "in"]
    if not ins:
        cuts = [0] + sorted(t + 1 for t, k in marks if k == "cut" and 0 < t + 1 < n) + [n]
        return [(a, b) for a, b in zip(cuts, cuts[1:]) if b > a]
    outs = [t for t, k in marks if k in ("cut", "end")]
    return [(a, min(min([e for e in outs if e > a] or [n]), n)) for a in ins]


# ------------------------------------------------------------------ frames

def frames_dir(work, name):
    return work / "frames" / name


def extract(clips, work, name):
    d = frames_dir(work, name)
    d.mkdir(parents=True, exist_ok=True)
    if any(d.iterdir()):
        return
    k = 0
    while (clips / f"{name}.{k}.mp4").exists():
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(clips / f"{name}.{k}.mp4"), "-q:v", "2",
                        "-start_number", str(k * 300), str(d / "%05d.jpg")], check=True)
        k += 1
    if (clips / f"{name}-wide.mp4").exists():
        wd = frames_dir(work, name + "-wide")
        wd.mkdir(parents=True, exist_ok=True)
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(clips / f"{name}-wide.mp4"), "-q:v", "2",
                        "-start_number", "0", str(wd / "%05d.jpg")], check=True)


def count(work, name, wide_from):
    n = len(list(frames_dir(work, name).glob("*.jpg")))
    wd = frames_dir(work, name + "-wide")
    if wide_from is not None and wd.exists():
        n = wide_from + len(list(wd.glob("*.jpg")))
    return n


def path_of(work, name, t, wide_from):
    if wide_from is not None and t >= wide_from:
        return frames_dir(work, name + "-wide") / f"{t - wide_from:05d}.jpg"
    return frames_dir(work, name) / f"{t:05d}.jpg"


def tilt(frame, angle):
    """As the short's, for this frame: recorded the other way up (on its side) it is turned and cut
    with nothing missing; recorded the same way up, zoomed in a little to hide the corners."""
    if abs(angle) < 0.05:
        angle = 0.05
    h, w = frame.shape[:2]
    r = np.radians(angle)
    c, s_ = abs(np.cos(r)), abs(np.sin(r))
    need = max((W * c + H * s_) / w, (W * s_ + H * c) / h)
    z = need if (w > h) != (W > H) else max(1.04, 1 + 0.55 * (need - 1))
    img = Image.fromarray(frame)
    big = img.resize((int(round(w * z)), int(round(h * z))), Image.BICUBIC).rotate(angle, resample=Image.BICUBIC, expand=True,
                                                                                   fillcolor=(0, 0, 0))
    left, top = (big.width - W) // 2, (big.height - H) // 2
    return np.asarray(big.crop((left, top, left + W, top + H)))


def read(work, name, t, wide_from):
    img = Image.open(path_of(work, name, t, wide_from)).convert("RGB")
    if (img.width > img.height) == (W > H) and img.size != (W, H):
        img = img.resize((W, H), Image.BICUBIC)
    return np.asarray(img)


def osd(img, k, clock, date, fov):
    """The camcorder's own writing: REC, the battery, the date and the time, and when it is zoomed in,
    how far."""
    d = ImageDraw.Draw(img)
    f = R.font(R.MONO, 44)
    small = R.font(R.MONO, 36)
    white = (236, 236, 228)
    if (k // 10) % 2 == 0:
        d.ellipse((70, 66, 102, 98), fill=(222, 36, 30))
    d.text((116, 56), "REC", font=f, fill=white)
    d.rectangle((W - 186, 64, W - 92, 98), outline=white, width=4)
    d.rectangle((W - 92, 73, W - 84, 89), fill=white)
    d.rectangle((W - 178, 71, W - 150, 91), fill=white)
    d.rectangle((W - 146, 71, W - 118, 91), fill=white)
    h, m, s, pm = clock
    secs = s + k // FPS
    mm = m + secs // 60
    text = f"{(h + mm // 60 - 1) % 12 + 1:2d}:{mm % 60:02d}:{secs % 60:02d} {'PM' if pm else 'AM'}"
    d.text((70, H - 168), date, font=small, fill=white)
    d.text((70, H - 122), text, font=f, fill=white)
    d.text((W - 150, H - 122), "SP", font=f, fill=white)
    zoom = np.tan(np.radians(27.5)) / np.tan(np.radians(fov / 2))
    if zoom >= 1.1:
        # The zoom: W at one end, T at the other, where it is between them, and how many times.
        x0, x1, y = W // 2 - 220, W // 2 + 220, 92
        d.text((x0 - 48, y - 26), "W", font=small, fill=white)
        d.text((x1 + 16, y - 26), "T", font=small, fill=white)
        d.rectangle((x0, y - 6, x1, y + 6), outline=white, width=3)
        u = min(1.0, (zoom - 1) / 1.6)
        d.rectangle((x0, y - 6, x0 + int((x1 - x0) * u), y + 6), fill=white)
        d.text((W // 2 - 40, y + 18), f"x{zoom:.1f}", font=small, fill=white)
    return img


def title_card(rng, k, full):
    """THE OCCUPANT, as the short ends; {full}: with the line under it and where to get it."""
    img = Image.new("RGB", (W, H), (6, 6, 7))
    d = ImageDraw.Draw(img)
    flick = 1.0 if rng.random() > 0.08 else 0.55
    c = int(232 * flick)
    tf = R.font(R.TITLE, 200)
    title = "THE OCCUPANT"
    tw = d.textlength(title, font=tf)
    top = H * (0.30 if full else 0.37)
    d.text(((W - tw) / 2, top), title, font=tf, fill=(c, int(c * 0.97), int(c * 0.92)))
    if full:
        lf = R.font(R.LINE, 54)
        line = "it's already in your world."
        lw = d.textlength(line, font=lf)
        a = 1.0 if k >= 16 else 0.0
        d.text(((W - lw) / 2, top + 236), line, font=lf, fill=(int(200 * a),) * 3)
        sf_ = R.font(R.LINE, 40)
        note = "Minecraft  ·  Fabric  ·  on Modrinth"
        nw = d.textlength(note, font=sf_)
        a = 1.0 if k >= 30 else 0.0
        d.text(((W - nw) / 2, top + 330), note, font=sf_, fill=(int(150 * a),) * 3)
    return R.finish(np.asarray(img, np.float32) / 255.0, rng, k)


# ------------------------------------------------------------------ one clip's frames

GROUND_ZOOM = (1.0, 0.5, 0.5)
# Each clip's exposure, worked out from its own frames (see levels()); "exposure" in SCENES is how
# bright the picture is to come out on average.
EXPOSURE = {}


def film(x):
    """Scene light to picture, as the short has it, with the shadows not sunk quite so deep: these
    are places to be looked round, not only a path in the dark."""
    y = np.clip(x, 0, None) ** 1.3
    knee, room = 0.6, 0.55
    return np.where(y < knee, y, knee + room * (1 - np.exp(-(y - knee) / room)))


def blur(frame, dyaw, dpitch):
    """The smear of a camera turned fast while the shutter was open: half a frame's worth of the turn,
    in enough steps that it is a smear and not copies."""
    dx, dy = -dyaw * R.PX_PER_DEGREE * 0.25, dpitch * R.PX_PER_DEGREE * 0.25
    length = (dx * dx + dy * dy) ** 0.5
    if length < 3:
        return frame
    scale = min(1.0, 90.0 / length)
    dx, dy = dx * scale, dy * scale
    n = int(min(28, length * scale / 2.0 + 3))
    acc = np.zeros(frame.shape, np.float32)
    total = 0.0
    for k in range(n):
        u = k / (n - 1) - 0.5
        w = 1.0 - abs(u) * 1.2
        acc += np.roll(np.roll(frame, int(round(dy * u)), axis=0), int(round(dx * u)), axis=1).astype(np.float32) * w
        total += w
    return (acc / total).astype(np.uint8)


def grade(frame, lo, hi, exposure, warmth, prev):
    """The short's grade, without its push of every brown towards red (the cabins are all wood):
    shadows cold, the light a sick yellow, a little of the true colour, and only real reds kept red."""
    img = Image.fromarray(frame)
    soft = img.filter(ImageFilter.GaussianBlur(3.2))
    sharp = img.filter(ImageFilter.GaussianBlur(0.9))
    a = (np.asarray(soft, np.float32) * 0.45 + np.asarray(sharp, np.float32) * 0.55) / 255.0
    lum = a.mean(axis=2)
    t = film((lum - lo) / (hi - lo) * exposure * R.FALLOFF)
    cold = np.array([0.72, 0.78, 0.86], np.float32)
    sick = np.array([0.97, 0.93, 0.83], np.float32)
    rgb = t[..., None] * (cold * (1 - t[..., None]) + sick * t[..., None])
    rgb += (a - lum[..., None]) * warmth * 1.6 * t[..., None]
    red = np.clip((a[..., 0] - np.maximum(a[..., 1], a[..., 2])) * 4 - 0.35, 0, 1) * np.clip(1 - t * 1.2, 0, 1)
    rgb[..., 0] += red * 0.08
    if prev is not None:
        # The sensor's lag: the brightest things trail a little behind a fast turn.
        rgb = np.maximum(rgb, prev * 0.35)
        rgb = rgb * 0.93 + prev * 0.07
    return rgb

# The camcorder's digital zoom on top of the lens, by clip: (mark, from, to, how far, in over, out over), in frames.
# A mark may be "name#2" (its second time), or a recorded frame by number.
PUNCH = {
    "camp": [(92, 0, 30, 1.45, 14, 8)],
    "pasture": [("weird", 26, 90, 1.35, 22, 8)],
    "well": [("clack", 2, 22, 1.35, 10, 5)],
    "intruder": [("zoomin", 4, 42, 1.3, 18, 8)],
    "radio": [(94, 0, 28, 1.4, 14, 8)],
    "chapel": [("seen", 2, 46, 1.25, 16, 8)],
    "graves": [(82, 0, 30, 1.35, 14, 8), ("zoomin", 0, 80, 1.3, 30, 8)],
    "knock": [("seen", 8, 46, 1.3, 24, 8)],
    "morning": [("lower", -60, -2, 1.3, 20, 6)],
    "mine": [("seen", 4, 49, 1.8, 26, 6)],
}


def smooth(x):
    c = min(max(x, 0.0), 1.0)
    return c * c * (3 - 2 * c)


def nth(marks, mark):
    """A mark's recorded frame: "name" its first time, "name#2" its second; a number is itself."""
    if isinstance(mark, int):
        return mark
    kind, _, k = mark.partition("#")
    ts = [t for t, kd in marks if kd == kind]
    k = int(k or 1)
    return ts[k - 1] if len(ts) >= k else None


def punch_at(name, marks, t):
    z = 1.0
    for mark, a, b, far, rin, rout in PUNCH.get(name, []):
        m = nth(marks, mark)
        if m is None:
            continue
        u = smooth((t - (m + a)) / rin) - smooth((t - (m + b)) / rout)
        z = max(z, 1.0 + (far - 1.0) * u)
    return z


def hand(frame, u, seed):
    """A hand over the lens, from below and to one side, out of focus: a dark warm shape filling the
    picture, lit a little by the room, the light coming red through the edges of the fingers at the
    last, until there is nothing."""
    h, w = frame.shape[:2]
    e = min(1.0, max(0.0, u)) ** 1.5
    small = Image.new("L", (w // 8, h // 8), 0)
    d = ImageDraw.Draw(small)
    sw, sh = small.size
    cx, cy = sw * (1.12 - 0.6 * e), sh * (1.55 - 1.0 * e)
    rx, ry = sw * (0.26 + 0.85 * e), sh * (0.38 + 1.0 * e)
    d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
    for k_, (ox, oy) in enumerate(((-0.62, -0.78), (-0.25, -0.98), (0.12, -1.0))):
        fx, fy = cx + rx * ox, cy + ry * oy
        d.ellipse((fx - rx * 0.16, fy - ry * 0.3, fx + rx * 0.16, fy + ry * 0.3), fill=255)
    small = small.filter(ImageFilter.GaussianBlur(max(2.0, sw * (0.035 + 0.05 * e))))
    m = np.asarray(small.resize((w, h), Image.BILINEAR), np.float32)[..., None] / 255.0
    lit = float(frame.mean()) / 255.0
    skin = np.array([1.0, 0.72, 0.6], np.float32) * (40 + 90 * lit) * (1 - 0.85 * e)
    edge = np.clip(m * (1 - m) * 4, 0, 1) * e
    glow = np.array([70, 18, 10], np.float32)
    pic = np.asarray(Image.fromarray(frame).filter(ImageFilter.GaussianBlur(0.5 + 6 * e)), np.float32)
    out = pic * (1 - m) + (skin + glow * edge) * m
    return np.clip(out * (1 - 0.3 * e * e), 0, 255).astype(np.uint8)


def stop_frame(rng, k):
    """The tape stopped: the picture gone to a bright band of noise squeezed into the middle, rolling."""
    s_ = rng.random((H // 4, W // 4)).astype(np.float32)
    s_ = np.repeat(np.repeat(s_, 4, axis=0), 4, axis=1)
    yy = (np.arange(H, dtype=np.float32)[:, None] - H * (0.5 + 0.08 * k)) / (H * (0.09 + 0.05 * k))
    band = np.exp(-yy * yy)
    out = np.repeat((s_ * 0.55 + 0.45 * band * (0.7 + 0.3 * s_)) [..., None], 3, axis=2) * (0.95 - 0.35 * k)
    return np.clip(out * np.array([0.92, 0.95, 1.0], np.float32), 0, 1)


def work(args):
    """A run of one clip's recorded frames graded and written out, each to its own place in the edit."""
    wdir, name, todo, lo, hi, gains, marks, camera = args
    wdir = Path(wdir)
    sc = BY[name]
    hit = first(marks, "hit")
    wide_from = hit + 4 if hit is not None else None
    land = first(marks, "land")
    rng = np.random.default_rng(abs(hash((name, todo[0][0]))) % (2 ** 32))
    fails = R.failures(marks, len(camera))
    hits = R.blows(marks)
    prev = None
    t0 = todo[0][0]
    for t in range(max(0, t0 - 2), t0):
        f = tilt(read(wdir, name, t, wide_from), camera[t][2])
        prev = grade(f, lo, hi, EXPOSURE[name], sc["warmth"], prev)
    last = None
    cover = first(marks, "cover")
    end = first(marks, "end")
    blinks = {}
    for b_ in [t_ for t_, k_ in marks if k_ == "blink"]:
        blinks.update({b_ + 1: 0.8, b_ + 2: 1.0, b_ + 3: 1.0, b_ + 4: 0.55})
    for t, out_path, k, clock, restart, stopping in todo:
        if last is not None and t != last + 1:
            prev = None
        last = t
        f = read(wdir, name, t, wide_from)
        fov = camera[t][3] if t < len(camera) else 55.0
        # How many pixels a degree is, at this lens (on its side: upright, 86 degrees over 1920).
        on_side = wide_from is not None and t >= wide_from
        R.PX_PER_DEGREE = 18.0 if on_side else (H / 2) / np.tan(np.radians(fov / 2)) * np.pi / 180
        if on_side:
            fov = 55.0
        if 0 < t < len(camera):
            dyaw = (camera[t][0] - camera[t - 1][0] + 180) % 360 - 180
            f = R.skew(blur(f, dyaw, camera[t][1] - camera[t - 1][1]), dyaw)
        lying = land is not None and t >= land
        if lying:
            soft = [6.0, 9.0, 7.0, 4.0, 5.0, 2.5, 1.2]
            if t - land < len(soft):
                f = np.asarray(Image.fromarray(f).filter(ImageFilter.GaussianBlur(soft[t - land])))
        if t < len(camera):
            f = tilt(f, camera[t][2])
        if lying:
            f = R.punch(f, *GROUND_ZOOM)
        z = punch_at(name, marks, t)
        if z > 1.001:
            f = R.punch(f, z, 0.5, 0.5)
            fov = float(np.degrees(2 * np.arctan(np.tan(np.radians(fov / 2)) / z)))
        g = gains[t]
        if abs(g - 1) > 0.01:
            f = np.clip(f.astype(np.float32) * g, 0, 255).astype(np.uint8)
        rgb = grade(f, lo, hi, EXPOSURE[name] * (0.95 if lying else 1.0), sc["warmth"], prev)
        prev = rgb
        d = sc.get("day", 0.0)
        if d > 0:
            # By day the camcorder sees colour: some of the picture as it was, a little cold and flat.
            a_ = (f.astype(np.float32) / 255.0) ** 1.12
            l_ = a_.mean(axis=2, keepdims=True)
            base = (l_ + (a_ - l_) * 0.72) * np.array([0.93, 0.97, 1.0], np.float32) * (0.75 + 0.6 * float(rgb.mean()) / max(0.05, float(l_.mean())))
            rgb = rgb * (1 - d) + np.clip(base, 0, 1) * d
        if cover is not None and end is not None and t >= cover:
            u_ = min(1.0, max(0.0, (t - cover + 1) / max(1, end - cover) * 1.6 - 0.6))
            rgb = hand((np.clip(rgb, 0, 1) * 255).astype(np.uint8), u_, t).astype(np.float32) / 255.0 * (1 - 0.6 * u_ ** 2)
        # The gain turned up shows as grain, up to a point.
        done = R.finish(rgb, rng, k, min(1.0 + (g - 1.0) * 0.6, 1.6) if g > 1 else 1.0)
        if t in hits:
            done = R.struck(done, rng, hits[t])
        if restart is not None:
            done = R.glitch(done, rng, restart)
        if t in fails:
            done = R.glitch(done, rng, fails[t])
        if stopping:
            done = R.glitch(done, rng, stopping)
        if t in blinks:
            # The tape dropping out: torn, then black with a little snow in it, then torn coming back.
            sb = blinks[t]
            done = R.snow(rng, k, 0.22, dark=0.75) if sb >= 1.0 else R.glitch(done * (1 - 0.5 * sb), rng, sb)
        img = Image.fromarray((done * 255).astype(np.uint8))
        img = osd(img, k, clock, sc["date"], fov)
        img.save(out_path, quality=95)
    return len(todo)


def levels(wdir, name, parts, wide_from):
    """The clip's black and white points; its gain each frame, as a camcorder's auto exposure rides it
    (slowly up in the dark, down again in the light, never all the way); and the exposure that
    makes the clip come out as bright as it should on average."""
    keep = [t for a, b in parts for t in range(a, b)]
    means = {}
    for t in keep:
        means[t] = float(np.asarray(Image.open(path_of(wdir, name, t, wide_from)).convert("L").resize((96, 54)), np.float32).mean()) + 1.0
    ref = float(np.percentile(list(means.values()), 70))
    gains, g = {}, 1.0
    for t in keep:
        want = min(3.2, max(0.8, (ref / means[t]) ** 0.85))
        g += (want - g) * 0.08
        gains[t] = g
    sample = keep[:: max(1, len(keep) // 40)]
    lums = np.concatenate([np.clip(np.asarray(Image.open(path_of(wdir, name, t, wide_from)).convert("L").resize((192, 108)), np.float32).ravel()
                                   * gains[t], 0, 255) / 255.0 for t in sample])
    lo = float(np.percentile(lums, 3))
    hi = float(max(np.percentile(lums, 99.7), lo + 0.06))
    fall = np.tile(np.asarray(Image.fromarray(R.FALLOFF.astype(np.float32), mode="F").resize((192, 108)), np.float32).ravel(), len(sample))
    e0, e1 = 0.3, 8.0
    for _ in range(30):
        e = (e0 + e1) / 2
        m = float(film(np.clip((lums - lo) / (hi - lo), 0, None) * e * fall).mean())
        e0, e1 = (e, e1) if m < BY[name]["exposure"] else (e0, e)
    return lo, hi, gains, e


# ------------------------------------------------------------------ the edit

def main():
    clips, mod_sounds, voice, out_mp4 = (Path(a) for a in sys.argv[1:5])
    only = sys.argv[5] if len(sys.argv) > 5 and not sys.argv[5].startswith("--") else None
    wdir = clips / "work"
    out = wdir / "out"
    out.mkdir(parents=True, exist_ok=True)
    graded = wdir / "graded"
    # Clips recorded again: their frames and their graded frames made afresh.
    for a_ in sys.argv:
        if a_.startswith("--fresh="):
            import shutil
            for nm in a_.split("=", 1)[1].split(","):
                for d in (frames_dir(wdir, nm), frames_dir(wdir, nm + "-wide"), graded / nm, graded / ("cold" if nm == "knock" else nm)):
                    shutil.rmtree(d, ignore_errors=True)
    scenes = [s for s in SCENES if (clips / f"{s['name']}.0.mp4").exists()]
    print("scenes", [s["name"] for s in scenes])
    info = {}
    for s in scenes:
        name = s["name"]
        extract(clips, wdir, name)
        marks = marks_of(clips, name)
        hit = first(marks, "hit")
        wide_from = hit + 4 if hit is not None else None
        n = min(count(wdir, name, wide_from), len(camera_of(clips, name)))
        parts = stretches(marks, n)
        lo, hi, gains, EXPOSURE[name] = levels(wdir, name, parts, wide_from)
        info[name] = dict(marks=marks, camera=camera_of(clips, name), n=n, parts=parts, lo=lo, hi=hi,
                          gains=[gains.get(t, 1.0) for t in range(n)], wide_from=wide_from)
        print(name, "frames", n, "stretches", parts, "levels", round(lo, 3), round(hi, 3), "exposure", round(EXPOSURE[name], 2))

    # Where everything goes: (clip, recorded frame, output frame, frame of the clip, clock, restart).
    plan = {s["name"]: [] for s in scenes}
    links = {}
    starts = []            # (output frame, clip, a, b)
    k = 0
    cold = []
    if False:  # (no teaser before the title: the tape starts at the start)
        seen = first(info["knock"]["marks"], "seen")
        for j in range(COLD):
            t = seen - 6 + j
            cold.append(("knock", t, k))
            k += 1
        k += 4
    title_at = k
    k += TITLE + 8
    head_at = k
    k += HEAD
    for i, s in enumerate(scenes):
        name = s["name"]
        for p, (a, b) in enumerate(info[name]["parts"]):
            starts.append((k, name, a, b))
            for t in range(a, b):
                j = t - a
                restart = [0.55, 0.3, 0.12][j] if j < 3 else None
                stopping = {b - 2: 0.25, b - 1: 0.6}.get(t) if name != "found" else None
                plan[name].append((t, str(graded / name / f"{t:05d}.jpg"), k - starts[-1][0], s["clocks"][min(p, len(s["clocks"]) - 1)], restart,
                                   stopping))
                links[k] = graded / name / f"{t:05d}.jpg"
                k += 1
            if p < len(info[name]["parts"]) - 1:
                k += INNER_GAP
        if i < len(scenes) - 1:
            k += GAP
    tape_end = k
    total = k + DEAD + END
    print("edit", total, "frames,", round(total / FPS, 1), "seconds")
    cold_items = [(c[1], str(graded / "cold" / f"{j:05d}.jpg"), c[1], BY[c[0]]["clocks"][0], 0.45 if j < 2 else None, None)
                  for j, c in enumerate(cold)]
    for j, c in enumerate(cold):
        links[c[2]] = graded / "cold" / f"{j:05d}.jpg"
    for name in plan:
        (graded / name).mkdir(parents=True, exist_ok=True)
    (graded / "cold").mkdir(parents=True, exist_ok=True)

    if only:
        want = only.split(",")
        jobs = [(str(wdir), n_, [it], info[n_]["lo"], info[n_]["hi"], info[n_]["gains"], info[n_]["marks"], info[n_]["camera"])
                for n_ in want[:1] for it in plan[n_] if str(it[0]) in want[1:]]
        with Pool(4) as pool:
            pool.map(work, jobs)
        for j in jobs:
            print(j[2][0][0], j[2][0][1])
        return

    jobs = []
    for name, items in plan.items():
        for i in range(0, len(items), 30):
            jobs.append((str(wdir), name, items[i:i + 30], info[name]["lo"], info[name]["hi"], info[name]["gains"],
                         info[name]["marks"], info[name]["camera"]))
    if cold_items:
        i_ = info["knock"]
        jobs.append((str(wdir), "knock", cold_items, i_["lo"], i_["hi"], i_["gains"], i_["marks"], i_["camera"]))
    # Graded frames are kept between runs: only what is not graded yet is graded now.
    jobs = [j for j in jobs if not all(Path(it[1]).exists() for it in j[2])]
    with Pool(4) as pool:
        for i, _ in enumerate(pool.imap_unordered(work, jobs)):
            if i % 20 == 0:
                print("graded", i, "of", len(jobs), flush=True)
    if "--grade-only" in sys.argv:
        return
    remux = next((a_.split("=", 1)[1] for a_ in sys.argv if a_.startswith("--remux=")), None)
    import os
    for f in ([] if remux else out.glob("*.jpg")):
        f.unlink()
    for k_, src in ({} if remux else links).items():
        os.link(src, out / f"{k_:05d}.jpg")
    rng = np.random.default_rng(9)
    black = np.zeros((H, W, 3), np.uint8)

    def save(at, rgb):
        Image.fromarray(rgb if rgb.dtype == np.uint8 else (rgb * 255).astype(np.uint8)).save(out / f"{at:05d}.jpg", quality=95)

    for at in range(0 if remux else total):
        if not (out / f"{at:05d}.jpg").exists():
            save(at, black)
    for j in range(0 if remux else TITLE):
        save(title_at + j, title_card(rng, j, False))
    for j in range(0 if remux else HEAD):
        save(head_at + j, R.snow(rng, j, 1.0 - j / HEAD, dark=0.5 if j < 3 else 0.0))
    for k0, name, a, b in ([] if remux else starts[1:]):
        save(k0 - 2, R.snow(rng, 1, 0.35, dark=0.6))
    for k0, name, a, b in ([] if remux else starts):
        if name != "found":
            save(k0 + b - a, stop_frame(rng, 0))
            save(k0 + b - a + 1, stop_frame(rng, 1))
    for j in range(0 if remux else END):
        save(tape_end + DEAD + j, title_card(rng, j, True))

    # Chapters for the video's description, where each clip starts.
    names = {"home": "Day 1 - The cabin", "camp": "Day 2 - The camp", "pasture": "Day 2 - The pen", "well": "Day 2 - The well",
             "field": "Day 2 - The clearing", "doorway": "Day 3 - The door",
             "intruder": "Night 2 - Someone in the cabin", "visitor": "Day 3 - A visitor", "radio": "Day 3 - The radio shack",
             "house": "Day 3 - A house in the woods", "chapel": "Day 3 - The chapel", "graves": "Day 3 - The graveyard",
             "knock": "Night 3 - Something on the roof", "morning": "Day 4 - The morning after", "mine": "Day 4 - The mine",
             "found": "Night 4 - The last tape"}
    # (YouTube wants the first at 0:00 and none shorter than ten seconds: the first clip's covers the open,
    # and one too short is put together with the one after it.)
    firsts = []
    for k0, name, a, b in starts:
        if name not in [n for _, n in firsts]:
            firsts.append((k0 / FPS, name))
    chap = []
    i = 0
    while i < len(firsts):
        at, name = firsts[i]
        title = names.get(name, name)
        nxt = firsts[i + 1][0] if i + 1 < len(firsts) else tape_end / FPS
        if nxt - at < 10 and i + 1 < len(firsts):
            title += " / " + names.get(firsts[i + 1][1], firsts[i + 1][1]).split(" - ", 1)[1]
            i += 1
        at = 0 if not chap else at
        chap.append(f"{int(at // 60)}:{int(at % 60):02d} {title}")
        i += 1
    (out_mp4.parent / "chapters.txt").write_text("\n".join(chap) + "\n")
    print("\n".join(chap))

    audio = wdir / "tape.wav"
    sheet = make_audio(clips, mod_sounds, voice, info, starts, cold, tape_end, total, audio)
    (out_mp4.parent / "voice_sheet.txt").write_text(sheet)
    print(sheet)
    if "--audio-only" in sys.argv:
        return
    # Loud enough to hear (about -23 LUFS, room left over it for the voice), the peaks held under -1.5 dB.
    r = subprocess.run([FF, "-i", str(audio), "-af", "loudnorm=I=-23:LRA=18:TP=-1.5:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True).stderr
    m = json.loads(r[r.rfind("{"):r.rfind("}") + 1])
    norm = wdir / "tape_norm.wav"
    subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(audio), "-af",
                    f"loudnorm=I=-23:LRA=18:TP=-1.5:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
                    f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true", "-ar", "48000", str(norm)], check=True)
    audio = norm
    # The air of each place and the tape's hiss, then the voice, over the rest at its loudness; the
    # peaks held under -1 dB.
    vpath, apath = audio.with_name("voice.npy"), audio.with_name("ambience.npy")
    with wave.open(str(norm)) as w:
        sr_ = w.getframerate()
        a = np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, 2).T.astype(np.float32) / 32768
    for path, gain in ((apath, AMBIENCE_GAIN), (vpath, VOICE_GAIN)):
        if path.exists():
            extra = np.load(str(path))
            if sr_ != SR:
                extra = np.stack([np.interp(np.arange(0, extra.shape[1], SR / sr_), np.arange(extra.shape[1]), c) for c in extra]).astype(np.float32)
            n_ = min(a.shape[1], extra.shape[1])
            a[:, :n_] += extra[:, :n_] * gain
    mixed = wdir / "tape_voice.wav"
    with wave.open(str(mixed), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr_)
        w.writeframes((np.clip(a.T, -1, 1) * 32767).astype(np.int16).tobytes())
    final = wdir / "tape_final.wav"
    subprocess.run([FF, "-y", "-loglevel", "error", "-i", str(mixed), "-af", "alimiter=limit=0.89:level=false", str(final)], check=True)
    audio = final
    if remux:
        # Only the sound made again: the picture taken as it is from the video already made.
        subprocess.run([FF, "-y", "-loglevel", "error", "-i", remux, "-i", str(audio), "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                        "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(out_mp4)], check=True)
        print("made", out_mp4, "(sound only, picture from", remux + ")")
        return
    secs = total / FPS
    kbps = 6500
    vf = "minterpolate=fps=30:mi_mode=blend,format=yuv420p"
    common = [FF, "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(out / "%05d.jpg")]
    log = str(wdir / "x264pass")
    subprocess.run(common + ["-vf", vf, "-c:v", "libx264", "-preset", "slow", "-tune", "grain", "-b:v", f"{kbps}k", "-pass", "1",
                             "-passlogfile", log, "-an", "-f", "mp4", "/dev/null"], check=True)
    subprocess.run(common + ["-i", str(audio), "-vf", vf, "-c:v", "libx264", "-preset", "slow", "-tune", "grain", "-b:v", f"{kbps}k",
                             "-pass", "2", "-passlogfile", log, "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart",
                             str(out_mp4)], check=True)
    print("made", out_mp4, round(secs, 1), "s")


# ------------------------------------------------------------------ sound

def sim_positions(name):
    """Where the camera is each frame (local to the set), worked out the way the game test does."""
    p = SIM / f"{name}.csv"
    if not p.exists():
        return None
    a = np.loadtxt(p, delimiter=",")
    return np.stack([a[:, 0] - 0.5, a[:, 2] - 0.5], axis=1)


def inside(name, x, z):
    if name in ("home", "morning", "knock", "intruder", "visitor"):
        return -2.6 < x < 2.6 and -2.6 < z < 3.6
    if name == "house":
        return -5.6 < x < 5.6 and -4.6 < z < 4.6
    if name == "radio":
        return -1.6 < x < 1.6 and -1.6 < z < 2.6
    if name == "chapel":
        return -2.6 < x < 2.6 and -4.6 < z < 6.6
    return False


def ground(name, x, z):
    if name == "mine":
        return "stone"
    if inside(name, x, z):
        return "stone" if name == "chapel" else "wood"
    if name in ("pasture", "field"):
        return "gravel"
    if name == "well" and -2.0 < x < 3.0 and -2.0 < z < 3.0:
        return "stone"
    if name in ("home", "morning", "knock", "intruder"):
        return "grass" if (x - 1) ** 2 + (z - 0.5) ** 2 < 9.5 ** 2 else "gravel"
    if name == "radio" and -2.6 < x < 2.6 and -3.6 < z < -2.4:
        return "wood"
    if name in ("camp", "graves", "chapel", "house", "radio"):
        return "gravel" if z < -6.0 else "grass"
    return "gravel"


NIGHT_AIR = ("home", "field", "intruder", "chapel", "graves", "knock", "found")


def sim_camera(name):
    """The camera each frame from the sim: x, y, z, yaw, pitch (or None for a scene the sim lacks)."""
    p = SIM / f"{name}.csv"
    return np.loadtxt(p, delimiter=",") if p.exists() else None


def frame_at(starts, at):
    """Which clip and recorded frame are on screen at {at} seconds into the edit."""
    k = at * FPS
    for k0, name, a, b in starts:
        if k0 <= k < k0 + (b - a):
            return name, int(a + k - k0)
    return None, None


# Where their head is when the camera is out of their hand, by scene (in the sim's world): in the house,
# sat on the floor where they went over.
HEAD_DOWN = {"house": (0.15, 64.95, -2.1)}


def voice_context(info, starts):
    """For a line said from {at} for {secs}: where their head is, frame by frame, from the camera that
    hears it (how far, which side), worked out from the sim's camera and pose: held out in front of
    their face, the head just behind it (a little further back when it points down at their feet);
    dropped or put down, the head where they are, got to over the fall. Indoors or not; a hand over
    the camera."""
    cams = {}

    def ctx(at, secs):
        name, t = frame_at(starts, at)
        if name is None:
            return {}
        if name not in cams:
            raw = SIM / f"{name}.raw"
            cams[name] = (sim_camera(name), np.loadtxt(raw, delimiter=",") if raw.exists() else None)
        P, Rw = cams[name]
        marks = info[name]["marks"]
        c = {"indoor": name == "mine", "fps": FPS}
        cv = first(marks, "cover")
        if cv is not None and t >= cv:
            c["covered"] = round(min(1.0, (t - cv) / 8), 2)
        if P is None or Rw is None or not len(P):
            hit = first(marks, "hit")
            far = 1.5 if hit is not None and t >= hit else HELD_DIST
            c["path"] = [(far, 0.0)] * (int(secs * FPS) + 2)
            return c
        t0 = min(t, len(P) - 1)
        c["indoor"] = c["indoor"] or bool(inside(name, P[t0, 0] - 0.5, P[t0, 2] - 0.5))
        held_at, held_head = None, None
        for tt in range(max(0, t0 - 40), t0):
            if Rw[min(tt, len(Rw) - 1), 11] > 0:
                held_at, held_head = tt, head_held(P[tt], Rw[min(tt, len(Rw) - 1)])
        path = []
        for tt in range(t0, t0 + int(secs * FPS) + 2):
            q = min(tt, len(P) - 1)
            cam, r = P[q], Rw[min(q, len(Rw) - 1)]
            if r[11] > 0:
                head = head_held(cam, r)
                held_at, held_head = q, head
            else:
                there = np.array(HEAD_DOWN.get(name, (cam[0], 64.5, cam[2] + 1.5)))
                u = 1.0 if held_at is None else min(1.0, (q - held_at) / 9)
                head = there if held_head is None else held_head * (1 - u) + there * u
            v = head - cam[:3]
            dist = float(np.linalg.norm(v))
            yr = np.radians(cam[3])
            right = np.array([-np.cos(yr), 0.0, -np.sin(yr)])
            pan = float(np.dot(v, right) / max(dist, 1e-6))
            path.append((max(dist, 0.15), pan))
        c["path"] = path
        return c
    return ctx


HELD_DIST = 0.32


def head_held(cam, r):
    """Their head, with the camera in their hand: just behind it and level with their eyes; further
    back (the arm out) when it points down."""
    yr = np.radians(cam[3])
    fwd = np.array([-np.sin(yr), 0.0, np.cos(yr)])
    down = float(np.clip((cam[4] - 20) / 50, 0, 1))
    # Pointed down at their feet, the camera is held down and out at arm's length, well below and in
    # front of their mouth (the picture is from higher, but that is where the microphone would be).
    back = 0.3 + 0.45 * down
    hy = cam[1] + 0.05 + 0.45 * down if r[9] > 0 else cam[1] + 0.05
    return np.array([cam[0], hy, cam[2]]) - fwd * back


def ambience(info, starts, n, vo):
    """The air round them, so nothing is ever quite silent: wind in the trees outside, crickets after
    dark; indoors the room's own low hum and the wind through the walls; the tunnel's rumble below
    ground; the wind on the microphone when they move fast outside; and over everything the tape's
    own hiss. Down under the voice. Nothing after the pen goes quiet but the hiss."""
    rng = np.random.default_rng(31)
    out = np.zeros((2, n), np.float32)
    first_at = starts[0][0] / FPS
    for k0, name, a, b in [(0, "title", 0, starts[0][0])] + list(starts):
        at0 = k0 / FPS
        m = int((b - a) / FPS * SR)
        i0 = int(at0 * SR)
        if m <= 0 or i0 >= n:
            continue
        m = min(m, n - i0)
        if name == "title":
            # Under the title: the wind, coming up out of nothing.
            w = wind_in_trees(rng, m) * 0.012 * np.linspace(0, 1, m, dtype=np.float32) ** 1.5
            out[:, i0:i0 + m] += w
            continue
        marks = info[name]["marks"]
        P = sim_camera(name)
        ind = np.zeros(m, np.float32)
        spd = np.zeros(m, np.float32)
        if P is not None and len(P):
            for t in range(a, b):
                t_ = min(t, len(P) - 1)
                j0, j1 = max(0, t_ - 3), min(len(P) - 1, t_ + 3)
                s0 = int((t - a) / FPS * SR)
                s1 = min(m, int((t - a + 1) / FPS * SR))
                ind[s0:s1] = 1.0 if inside(name, P[t_, 0] - 0.5, P[t_, 2] - 0.5) else 0.0
                spd[s0:s1] = np.hypot(P[j1, 0] - P[j0, 0], P[j1, 2] - P[j0, 2]) / max(1, j1 - j0)
            k_ = int(SR * 0.4)
            ind = np.convolve(ind, np.ones(k_, np.float32) / k_, "same")
            spd = np.convolve(spd, np.ones(k_, np.float32) / k_, "same")
        night = name in NIGHT_AIR
        if name == "mine":
            bed = bed_for(rng, m, "cave", False) * 5.0
        else:
            wind = wind_in_trees(rng, m)
            outside = wind * (0.011 if night else 0.016)
            if night:
                c = R.cricket(rng, m, 4500, 0.72, -0.5) + R.cricket(rng, m, 4150, 1.05, 0.55) * 0.7
                outside = outside + c / max(1e-9, float(np.sqrt((c ** 2).mean()))) * 0.005
            room = R.lowpass(R.lowpass(rng.normal(0, 1, m).astype(np.float32), 160), 160)
            room = room / max(1e-9, float(np.sqrt((room ** 2).mean()))) * 0.004
            inside_ = np.stack([room, np.roll(room, 900)]) + np.stack([R.lowpass(ch, 350) for ch in wind]) * 0.007
            bed = outside * (1 - ind) + inside_ * ind
            # The wind on the microphone, moving fast outside.
            gust = np.clip((spd - 0.08) / 0.12, 0, 1) * (1 - ind)
            if gust.max() > 0:
                buff = R.lowpass(R.lowpass(rng.normal(0, 1, m).astype(np.float32), 170), 170)
                buff = buff / max(1e-9, float(np.sqrt((buff ** 2).mean())))
                buff *= R.drift(rng, m, 0.35, 0.3, 1.0)[:m]
                bed = bed + np.stack([buff, buff * 0.85]) * gust * 0.05
        if name == "pasture" and first(marks, "hush") is not None:
            h = int((first(marks, "hush") - a) / FPS * SR)
            fade = np.ones(m, np.float32)
            fade[max(0, h):] = 0.12
            bed = bed * np.convolve(fade, np.ones(int(SR * 0.4), np.float32) / int(SR * 0.4), "same")
        if name == "found":
            bed = bed * 0.6
        out[:, i0:i0 + m] += bed[:, :m]
    # The tape's hiss, from the first frame to the last.
    end = int(starts[-1][0] / FPS * SR + (starts[-1][3] - starts[-1][2]) / FPS * SR)
    hiss = np.stack([R.band(rng.normal(0, 1, n).astype(np.float32), 2500, 11000) for _ in range(2)])
    hiss = hiss / max(1e-9, float(np.sqrt((hiss ** 2).mean()))) * 0.0016
    hiss[:, end:] = 0
    hiss[:, :int(first_at * SR)] *= np.linspace(0, 1, int(first_at * SR), dtype=np.float32)
    out += hiss
    # Everything stops with the tape at each cut (but the title's wind).
    running = np.zeros(n, np.float32)
    running[:int(first_at * SR)] = 1.0
    for a_, b_ in R.STRETCHES:
        running[int(a_ * SR):int(b_ * SR)] = 1.0
    ramp = int(SR * 0.004)
    out *= np.convolve(running, np.ones(ramp, np.float32) / ramp, "same")[:n]
    # Under the voice, the air drops away a little.
    v = np.abs(vo[:, :n]).mean(axis=0) if vo.shape[1] else np.zeros(n, np.float32)
    k_ = int(SR * 0.15)
    v = np.convolve(v, np.ones(k_, np.float32) / k_, "same")
    vv = np.zeros(n, np.float32)
    vv[:len(v)] = v[:n]
    out *= 1 - 0.5 * np.clip(vv / 0.03, 0, 1)
    return out


def wind_in_trees(rng, n):
    """The short's wind high in the canopy (a soft, low rush in slow gusts), exactly n samples long."""
    out = np.zeros((2, n), np.float32)
    a = rng.normal(0, 1, n).astype(np.float32)
    b = rng.normal(0, 1, n).astype(np.float32)
    gust = R.drift(rng, n, 3.5, 0.3, 1.0)[:n] ** 1.5
    for c, x in enumerate((a + 0.35 * b, b + 0.35 * a)):
        body = R.lowpass(R.lowpass(x, 520), 520)
        body = body - R.lowpass(body, 140)
        out[c] = body[:n] * gust
    return out / max(1e-9, float(np.sqrt((out ** 2).mean())))


def bed_for(rng, n, kind, night_):
    """The air of a place, very quiet: wind in the trees outside (and crickets, after dark); indoors,
    the same through the walls; underground, the rock's own low rumble and water dripping somewhere."""
    if kind == "cave":
        rumble = R.lowpass(R.lowpass(R.noise(rng, n / SR + 0.1)[:n], 140), 140)
        rumble /= max(1e-9, np.sqrt((rumble ** 2).mean()))
        out = np.stack([rumble, np.roll(rumble, 2400)]) * 0.0022
        t = rng.random() * 2
        while t < n / SR - 1:
            m = int(SR * 0.05)
            f0 = 1400 + rng.random() * 1400
            drip = np.sin(2 * np.pi * f0 * np.arange(m) / SR * (1 + 0.6 * np.arange(m) / m)) * np.exp(-np.arange(m) / (SR * 0.012))
            drip = R.echo(drip.astype(np.float32), [(0.07, 0.4), (0.16, 0.25), (0.3, 0.12)])
            i = int(t * SR)
            pan = rng.uniform(-0.7, 0.7)
            a = (pan + 1) * np.pi / 4
            j = min(n, i + len(drip))
            out[0, i:j] += drip[:j - i] * 0.006 * np.cos(a)
            out[1, i:j] += drip[:j - i] * 0.006 * np.sin(a)
            t += 1.6 + rng.random() * 3.5
        return out
    wind = wind_in_trees(rng, n) * 0.0026
    crick = 0
    if night_:
        c = R.cricket(rng, n, 4500, 0.72, -0.5) + R.cricket(rng, n, 4150, 1.05, 0.55) * 0.7 + R.cricket(rng, n, 4900, 0.9, 0.1) * 0.35
        crick = c / max(1e-9, np.sqrt((c ** 2).mean())) * 0.0012
    bed = wind + crick
    if kind == "in":
        bed = R.lowpass(bed, 380) * 0.7
    return bed.astype(np.float32)


def loop(x, n):
    reps = int(np.ceil(n / len(x)))
    return np.tile(x, reps)[:n]


def radio_voice(rng, x):
    """Through the set: squeezed into a narrow band, clipped, chopped up by the static."""
    y = R.band(x, 450, 2600) * 3.0
    y = np.tanh(y * 2.5) * 0.5
    gate = (R.drift(rng, len(y), 0.12, 0.0, 1.0) > 0.35).astype(np.float32)
    gate = np.convolve(gate, np.ones(200, np.float32) / 200, "same")
    return (y * gate + R.band(rng.normal(0, 1, len(y)).astype(np.float32), 1500, 6000) * 0.08 * (1 - gate)).astype(np.float32)


def zoom_motor(rng, secs):
    """The camcorder's zoom motor, right by the microphone: a thin whine and the gears."""
    n = int(SR * secs)
    t = np.arange(n) / SR
    whine = np.sin(2 * np.pi * (1150 + 40 * np.sin(t * 9)) * t) * 0.25 + np.sin(2 * np.pi * 2300 * t) * 0.08
    gears = R.band(R.noise(rng, secs)[:n], 2500, 7000) * (0.6 + 0.4 * np.sin(2 * np.pi * 37 * t))
    shape = np.minimum(1, t / 0.04) * np.minimum(1, (secs - t) / 0.05)
    return ((whine + gears * 0.5) * shape).astype(np.float32)


# Suggested lines for the voice, at the moments they belong to (seconds into the video).
LINES = {
    "home": [(0.8, "Day one. Cabin's finally done."), ("chestshut", "...did that chest just close by itself?")],
    "camp": [(4.8, "(reading) 'Don't follow the tall one.'"), ("whisper+0.9", "That's... okay. I'm going.")],
    "pasture": [("hush+0.6", "...why are they all looking at me?"), ("weird+1.0", "(breathing hard) They're dead. They're ALL dead.")],
    "well": [("what", "Something threw it back up.")],
    "field": [("name", "WHISPER (optional, record as name.wav, quiet): 'vR4xxs_...' — leave it out and the edit uses a whisper"),
              ("name+1.8", "Hey! ...HEY!")],
    "intruder": [("zoomin+1.4", "(reading) 'Go to sleep... vR4xxs_.'"), ("door+2.6", "I shut that door. I know I shut it.")],
    "visitor": [("seen+0.5", "...why is it just staring at me?")],
    "doorway": [("rap-1.3", "(nervous laugh) Knock knock."), ("knock+1.2", "(whisper) ...no way.")],
    "radio": [("radio", "RADIO VOICE (garbled, record as radio.wav): '...vR4xxs_... I can see you...'"),
              ("end-3.4", "Nothing's even plugged in. No. No no no.")],
    "house": [(5.6, "Hello? Anyone home?"), ("drop-0.1", "(a yell as you fall)")],
    "chapel": [("seen", "...those weren't lit. Those were NOT lit.")],
    "graves": [("seen", "Hold on, there's someone standing at the trees.")],
    "knock": [("roof+1.0", "(whisper) ...something's on the roof."), ("what", "It saw me. It saw me!")],
    "morning": [("lower-1.5", "That's... that's my name.")],
    "mine": [("dig+1", "I didn't dig this.")],
    "found": [("mine", "Hello?"), ("seen", "(scream)"), ("no2", "NO!"), ("stab", "(scream, cut off)")],
}


def make_audio(clips, mod_sounds, voice, info, starts, cold, tape_end, total, out):
    rng = np.random.default_rng(21)
    game = R.Game(clips / "sounds", rng)
    secs = total / FPS
    N = int(secs * SR) + SR
    fx = np.zeros((2, N), np.float32)
    beds = np.zeros((2, N), np.float32)
    score = np.zeros((2, N), np.float32)
    mod = {k: R.load(mod_sounds / f"{k}.ogg") for k in ("breath", "knock", "whisper", "static", "drone", "dread_low", "dread_high", "dread_pulse")}
    R.STRETCHES[:] = [(k0 / FPS, (k0 + b - a) / FPS) for k0, name, a, b in starts]
    if cold:
        R.STRETCHES.append((cold[0][2] / FPS, (cold[-1][2] + 1) / FPS))
    put = R.put
    sheet = []

    def when_in(name):
        def when(t):
            for k0, nm, a, b in starts:
                if nm == name and a <= t < b:
                    return (k0 + t - a) / FPS
            return None
        return when

    def door_sound(kind="open"):
        for rel in (f"block/wooden_door/{kind}1.ogg", f"block/wooden_door/{kind}.ogg", f"random/door_{kind}.ogg"):
            x = game.get(rel)
            if x is not None:
                return x
        return R.thud(rng, 0.3)

    for k0, name, a, b in starts:
        at0, at1 = k0 / FPS, (k0 + b - a) / FPS
        n = int((at1 - at0) * SR)
        i0 = int(at0 * SR)
        marks = info[name]["marks"]
        when = when_in(name)
        pos = sim_positions(name)
        dark = name in ("graves", "knock", "found", "chapel", "intruder")
        night_air = name in ("home", "field", "intruder", "chapel", "graves", "knock", "found")
        # The air: outdoors or in, by where the camera is; underground in the mine.
        if name == "mine":
            beds[:, i0:i0 + n] += bed_for(rng, n, "cave", False)
        else:
            outdoor = bed_for(rng, n, "out", night_air)
            indoor = bed_for(rng, n, "in", night_air)
            mix = np.zeros(n, np.float32)
            if pos is not None:
                for t in range(a, b):
                    if t < len(pos) and inside(name, *pos[t]):
                        j = int((t - a) / FPS * SR)
                        mix[j:j + int(SR / FPS) + 1] = 1.0
                k_ = int(SR * 0.35)
                mix = np.convolve(mix, np.ones(k_, np.float32) / k_, "same")
            beds[:, i0:i0 + n] += outdoor * (1 - mix) + indoor * mix
        # The mod's score, quietly, as it plays in the game: the low drone, louder as the days go on
        # and in the dark; the high glass from the second act, swelling when it is near; the pulse
        # when it is close.
        act = BY[name]["act"]
        low = loop(mod["dread_low"], n) * (0.05 + 0.03 * act + (0.04 if dark else 0.0))
        score[:, i0:i0 + n] += low
        for t, kd in marks:
            if kd != "seen" or when(t) is None:
                continue
            j = int((when(t) - at0) * SR)
            env_ = np.zeros(n, np.float32)
            rise, hold, fall = int(SR * 1.2), int(SR * 3.0), int(SR * 5.0)
            seg = np.concatenate([np.linspace(0, 1, rise), np.ones(hold), np.linspace(1, 0, fall)]).astype(np.float32)
            env_[j:j + len(seg)] = seg[:max(0, min(len(seg), n - j))]
            if act >= 2:
                score[:, i0:i0 + n] += loop(mod["dread_high"], n) * env_ * 0.22
            if act >= 3 and name in ("knock", "mine", "found"):
                score[:, i0:i0 + n] += loop(mod["dread_pulse"], n) * env_ * 0.3
        # The pen: the animals, until they all stop; then nothing at all, the score gone with them.
        if name == "pasture":
            hush = when(first(marks, "hush")) if first(marks, "hush") is not None else at1
            tt = at0 + 0.4
            kinds = [("cow", 4, 0.9), ("sheep", 3, 1.0), ("pig", 3, 1.0), ("chicken", 3, 1.0)]
            while tt < hush - 0.6:
                kind, cnt, sp = kinds[int(rng.integers(0, len(kinds)))]
                x = game.pick(f"mob/{kind}", "say", cnt)
                if x is not None:
                    put(fx, R.lowpass(R.speed(x, sp * (0.95 + rng.random() * 0.1)), 4500), tt, 0.16 + rng.random() * 0.12,
                        pan=-0.35 + rng.uniform(-0.25, 0.2), until=hush)
                tt += 1.1 + rng.random() * 1.8
            j = int((hush - at0) * SR)
            fade = np.ones(n, np.float32)
            fade[j:] = 0.2
            fade[j:j + int(SR * 0.5)] = np.linspace(1, 0.2, len(fade[j:j + int(SR * 0.5)]))
            beds[:, i0:i0 + n] *= fade
            score[:, i0:i0 + n] *= fade * fade
        # Their steps, on whatever is underfoot.
        hit = first(marks, "hit")
        for t, kd in marks:
            if kd not in ("step", "stride") or (hit is not None and t >= hit - 2):
                continue
            at = when(t)
            g = ground(name, *pos[min(t, len(pos) - 1)]) if pos is not None else "gravel"
            g_ = {"wood": "wood", "stone": "stone", "grass": "grass", "gravel": "gravel"}[g]
            gain = 0.34 if kd == "step" else 0.52
            if g_ == "wood":
                gain *= 0.8
            x = R.speed(game.pick("step", g_, 6 if g_ != "gravel" else 4), 0.85 + rng.random() * 0.2)
            put(fx, x, at, gain + rng.random() * 0.08, pan=rng.uniform(-0.1, 0.1))
            # Underground, every step comes back a beat late, from where they just were.
            if name == "mine" and first(marks, "cut") is not None and t < first(marks, "cut"):
                put(fx, R.lowpass(x, 1500), at + 0.42, 0.18, pan=rng.uniform(-0.3, 0.3), until=R.tape_end_of(at))
        if name == "found":
            found_sounds(fx, game, rng, mod, marks, when, sheet)
        # What happens in each place.
        for t, kd in marks:
            at = when(t)
            if at is None:
                continue
            if kd == "door":
                put(fx, door_sound("open"), at, 0.5, pan=0.0)
            elif kd == "knock":
                put(fx, R.lowpass(mod["knock"], 2600), at, 0.75, pan=-0.05)
            elif kd == "rap":
                # Their own knuckles on the door, light, close to the microphone.
                x = game.pick("step", "wood", 6)
                put(fx, R.lowpass(R.speed(x, 1.3), 3200) if x is not None else R.thud(rng, 0.15), at, 0.5, pan=0.05)
            elif kd == "slam":
                put(fx, R.lowpass(door_sound("close"), 2400), at, 1.0, pan=0.0)
                put(fx, R.lowpass(R.thud(rng, 0.9), 400), at, 0.55, pan=0.0)
            elif kd == "name":
                v = R.wav(voice / "name.wav")
                put(fx, R.lowpass(v if v is not None else R.speed(mod["whisper"], 0.95), 3000), at, 0.5, pan=-0.75)
            elif kd == "pebble":
                put(fx, R.handling(rng, 0.25), at, 0.12, pan=0.0)
            elif kd == "clink":
                h_ = R.echo(game.stone_hit(), [(0.11, 0.5), (0.26, 0.32), (0.44, 0.18), (0.7, 0.08)])
                put(fx, R.lowpass(h_, 1100), at, 0.14 if (t % 2) else 0.09, pan=0.0)
            elif kd == "up":
                # Coming up the shaft: off the walls, quicker and louder, and out past them.
                for k_ in range(5):
                    h_ = R.echo(game.stone_hit(), [(0.06, 0.4), (0.14, 0.2)])
                    put(fx, R.lowpass(h_, 900 + 500 * k_), at + 0.05 + 0.1 * k_, 0.08 + 0.1 * k_, pan=0.0)
                rush = R.band(R.noise(rng, 0.4)[:int(SR * 0.4)], 400, 3000)
                env_ = np.sin(np.linspace(0, np.pi, len(rush))).astype(np.float32) ** 2
                put(fx, (rush * env_).astype(np.float32), at + 0.5, 0.25, pan=0.0)
            elif kd == "clack":
                put(fx, game.stone_hit(), at, 0.75, pan=0.0)
                put(fx, R.lowpass(game.stone_hit(), 2500), at + 0.13, 0.35, pan=0.05)
            elif kd == "tap":
                x = game.pick("step", "stone", 6)
                put(fx, R.speed(x, 0.7) if x is not None else R.thud(rng, 0.2), at, 0.45, pan=-0.6)
            elif kd == "shovel":
                for rel, d_, gn in (("item/shovel/flatten1.ogg", 0.0, 0.45), ("dig/gravel1.ogg", 0.7, 0.35)):
                    x = game.get(rel)
                    if x is None:
                        x = game.pick("dig", "gravel", 4)
                    put(fx, R.lowpass(R.speed(x, 0.6), 1200), at + d_, gn, pan=0.4)
            elif kd == "whisper":
                put(fx, mod["whisper"], at, 0.55, pan=-0.45)
            elif kd == "static":
                st = mod["static"]
                put(fx, np.concatenate([st, st[::-1], st]) * np.linspace(1, 0.6, len(st) * 3), at, 0.6, pan=0.05)
            elif kd == "radio":
                v = R.wav(voice / "radio.wav")
                if v is None:
                    v = R.speed(mod["whisper"], 0.92)
                put(fx, radio_voice(rng, v), at, 0.6, pan=0.05)
            elif kd == "light":
                x = game.get("fire/ignite.ogg")
                if x is not None:
                    put(fx, R.lowpass(x, 3000), at, 0.18, pan=0.0)
            elif kd == "seen" and name != "found":
                put(fx, R.lowpass(mod["drone"], 900), at, 0.22, pan=0.0)
            elif kd == "mine" and name == "mine":
                end = R.tape_end_of(at)
                kk = at
                for block in range(3):
                    for _ in range(5 + block):
                        h_ = R.echo(game.stone_hit(), [(0.09, 0.45), (0.21, 0.3), (0.37, 0.18), (0.6, 0.1)])
                        put(fx, R.lowpass(h_, 900), kk, 0.5, pan=0.3, until=end)
                        kk += 0.25
                    kk += 0.55
            elif kd == "dig":
                x = game.pick("dig", "stone", 4)
                if x is not None:
                    put(fx, R.lowpass(R.echo(R.speed(x, 0.9), [(0.08, 0.4), (0.19, 0.25), (0.33, 0.12)]), 2200), at, 0.42, pan=0.55)
            elif kd == "blink":
                st = mod["static"][:int(SR * 0.22)]
                put(fx, st * np.linspace(1, 0.3, len(st)), at + 0.05, 0.4, pan=0.0)
            elif kd in ("zoomin", "zoomout"):
                put(fx, zoom_motor(rng, 1.5 if kd == "zoomin" else 0.45), at, 0.05, pan=0.2)
            elif kd in ("chest", "chestshut"):
                x = None
                for rel in ((["block/chest/open.ogg", "random/chestopen.ogg"]) if kd == "chest" else
                            ["block/chest/close1.ogg", "block/chest/close.ogg", "random/chestclosed.ogg"]):
                    x = x if x is not None else game.get(rel)
                if x is not None:
                    put(fx, R.lowpass(x, 3500), at, 0.42, pan=-0.45)
            elif kd == "weird":
                # Soft, and wrong: a breath drawn backwards, slowed, over something too low to hear.
                w_ = R.lowpass(R.speed(mod["whisper"][::-1], 0.62), 1400)
                sub = R.tone(41, 2.2, 0.9) * 0.6
                m_ = max(len(w_), len(sub))
                snd = np.zeros(m_, np.float32)
                snd[:len(w_)] += w_
                snd[:len(sub)] += sub
                put(fx, snd, at, 0.32, pan=-0.2)
            elif kd == "behind":
                x = game.pick("step", "grass", 6)
                if x is not None:
                    put(fx, R.lowpass(R.speed(x, 0.9), 2400), at, 0.2, pan=0.45 if (t // 8) % 2 else -0.4)
            elif kd == "roof":
                x = game.pick("step", "wood", 6)
                if x is not None:
                    put(fx, R.lowpass(R.speed(x, 0.5), 900), at, 0.75, pan=0.0)
                put(fx, R.lowpass(R.thud(rng, 0.7), 500), at, 0.35, pan=0.0)
            elif kd == "moo":
                x = game.pick("mob/cow", "say", 4)
                if x is not None:
                    put(fx, R.speed(x, 0.82), at, 0.55, pan=-0.3)
            elif kd == "cover":
                put(fx, R.handling(rng, 0.7), at, 0.16, pan=0.1, until=R.tape_end_of(at))
            elif kd == "lower":
                put(fx, R.handling(rng, 0.5), at, 0.1, pan=0.1, until=R.tape_end_of(at))
            elif kd == "drop":
                put(fx, R.handling(rng, 0.45), at, 0.3, pan=0.0)
                put(fx, R.lowpass(R.thud(rng, 1.0), 700), at + 0.3, 0.6, pan=0.2)
            elif kd == "land" and name != "found":
                put(fx, R.clatter(rng), at, 0.55, pan=-0.1)

    # The voice sheet: each clip's lines, from where it starts to where it ends in the edit.
    for name in dict.fromkeys(nm for _, nm, _, _ in starts):
        mine_ = [(k0, a, b) for k0, nm, a, b in starts if nm == name]
        c0 = mine_[0][0] / FPS
        c1 = (mine_[-1][0] + mine_[-1][2] - mine_[-1][1]) / FPS
        for when_, text in LINES.get(name, []):
            sheet.append((resolve(when_, info[name]["marks"], when_in(name), c0, c1), name, text))

    # The cold open: the face at the window, in the quiet of the room.
    if cold:
        at0, at1 = cold[0][2] / FPS, (cold[-1][2] + 1) / FPS
        n = int((at1 - at0) * SR)
        i0 = int(at0 * SR)
        beds[:, i0:i0 + n] += bed_for(rng, n, "in", True)
        env_ = np.minimum(1, np.arange(n) / (SR * 0.4)).astype(np.float32)
        score[:, i0:i0 + n] += (loop(mod["dread_high"], n) * 0.3 + loop(mod["dread_pulse"], n) * 0.3 + loop(mod["dread_low"], n) * 0.2) * env_
        put(fx, R.lowpass(mod["drone"], 900), at0 + 0.2, 0.25)
    # The voice: every line there is a recording of (voice/voice_map.json), where the sheet puts it.
    vo, done = V.long_bus(voice, sheet, int(secs * SR) + SR, SR, R.STRETCHES, FF, voice_context(info, starts))
    RECORDED.update(done)
    print("voice lines in:", len(done))
    buf = fx[:, :int(secs * SR)]
    peak = np.abs(buf).max()
    if peak > 0.89:
        buf = buf * (0.89 / peak)
    buf = buf + beds[:, :buf.shape[1]] + score[:, :buf.shape[1]] * 0.09
    # (The voice is kept apart, to go on after the rest is brought to its loudness: see main.)
    vo = vo[:, :buf.shape[1]]
    np.save(str(out.with_name("voice.npy")), vo)
    np.save(str(out.with_name("ambience.npy")), ambience(info, starts, buf.shape[1], vo))
    # Everything stops with the tape at each cut.
    running = np.zeros(buf.shape[1], np.float32)
    for a_, b_ in R.STRETCHES:
        running[int(a_ * SR):int(b_ * SR)] = 1.0
    ramp = int(SR * 0.004)
    running = np.convolve(running, np.ones(ramp, np.float32) / ramp, "same")
    buf = buf * running
    peak = np.abs(buf).max()
    if peak > 0.89:
        buf = buf * (0.89 / peak)
    pcm = (np.clip(buf.T, -1, 1) * 30000).astype(np.int16)
    with wave.open(str(out), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    sheet.sort(key=lambda r: (r[0] is None, r[0] or 0))
    lines = ["THE OCCUPANT — the long video: where each voice line goes", "(times are minutes:seconds into the video; record each line as its own file)", ""]
    for at, name, text in sheet:
        if at is None:
            continue
        have = "   [recorded]" if (name, text) in RECORDED else ""
        lines.append(f"{int(at // 60)}:{at % 60:05.2f}  [{name}]  {text}{have}")
    return "\n".join(lines) + "\n"


def resolve(when_, marks, when, at0, at1):
    """A line's moment: seconds after the clip starts, or a mark's name, give or take seconds."""
    if isinstance(when_, (int, float)):
        return at0 + when_
    base, off = when_, 0.0
    for sign in ("+", "-"):
        if sign in when_[1:]:
            base, o = when_.split(sign, 1)
            off = float(o) * (1 if sign == "+" else -1)
    if base == "end":
        return at1 + off
    t = nth(marks, base)
    w = when(t) if t is not None else None
    return None if w is None else w + off


def found_sounds(buf, game, rng, mod, marks, when, sheet):
    """The ending, as the short has it."""
    creature = mod["breath"]
    put = R.put
    stab = first(marks, "stab")
    echoes = [tt for tt, kd in marks if kd == "echo"]
    for t, kind in marks:
        at = when(t)
        if at is None:
            continue
        if kind == "mine":
            end = R.tape_end_of(at)
            k = at
            for block in range(3):
                for _ in range(5 + block):
                    h = R.echo(game.stone_hit(), [(0.09, 0.45), (0.21, 0.3), (0.37, 0.18), (0.6, 0.1)])
                    put(buf, R.lowpass(h, 1100), k, 0.55, pan=0.35, until=end)
                    k += 0.25
                put(buf, R.lowpass(R.echo(game.stone_break(), [(0.1, 0.4), (0.25, 0.25), (0.5, 0.12)]), 1100), k, 0.6, pan=0.35, until=end)
                k += 0.55
        elif kind == "echo":
            last = t == max(echoes)
            put(buf, R.lowpass(game.step("gravel"), 2600), at, 0.26 if last else 0.18, pan=0.25)
        elif kind == "cave":
            c = game.cave(R.CAVE)
            if c is not None:
                put(buf, c * np.minimum(1, np.arange(len(c)) / (SR * 0.3)), at, 0.55)
        elif kind == "rustle":
            for j in range(3):
                put(buf, R.lowpass(game.step("grass"), 3200), at + j * 0.13 + rng.random() * 0.03, 0.2, pan=-0.3)
            put(buf, R.band(R.noise(rng, 0.6), 1500, 6000) * R.env(int(SR * 0.6), 0.05, 0.2), at, 0.1, pan=-0.3)
        elif kind == "breath":
            put(buf, creature, at, 0.5)
            put(buf, creature, at + len(creature) / SR * 0.95, 0.42)
        elif kind == "hit":
            put(buf, R.blow(rng), at, 0.95)
        elif kind == "land":
            put(buf, R.clatter(rng), at, 0.8)
        elif kind == "scoot":
            put(buf, R.speed(game.step("gravel"), 0.75), at, 0.3, pan=-0.15)
            put(buf, R.speed(game.step("gravel"), 0.65), at + 0.12, 0.22, pan=-0.15)
        elif kind == "thump":
            put(buf, R.lowpass(R.speed(game.step("gravel"), 0.55), 900), at, 0.42, pan=0.15)
            put(buf, R.thud(rng, 0.5), at, 0.25, pan=0.15)
        elif kind == "lunge":
            put(buf, R.speed(creature, 1.15), at, 0.5, pan=0.0)
            put(buf, R.lowpass(R.speed(game.step("gravel"), 0.5), 900), at + 0.15, 0.45, pan=0.05)
            put(buf, R.thud(rng, 0.6), at + 0.15, 0.3, pan=0.05)
        elif kind == "growl":
            put(buf, creature * 0.8, at, 0.32, pan=0.15)
        elif kind == "stomp":
            put(buf, R.thud(rng, 0.8), at, 0.45, pan=0.05)
            put(buf, R.speed(game.dig("gravel"), 0.6), at, 0.4, pan=0.05)
        elif kind == "skid":
            put(buf, R.speed(game.dig("gravel"), 0.65), at, 0.32, pan=-0.1)
            put(buf, game.cloth(), at + 0.05, 0.22, pan=-0.1)
        elif kind == "swept":
            put(buf, R.speed(game.dig("wood"), 0.7), at, 0.35, pan=0.0)
            put(buf, R.speed(game.dig("gravel"), 0.55), at + 0.04, 0.35, pan=-0.05)
        elif kind == "slipclaw":
            put(buf, R.speed(game.dig("gravel"), 0.6), at, 0.32, pan=-0.1)
            put(buf, R.speed(game.dig("grass"), 0.7), at + 0.1, 0.22, pan=-0.1)
        elif kind == "yank":
            for j in range(5):
                put(buf, R.speed(game.dig("gravel" if j % 2 else "grass"), 0.6 + 0.1 * rng.random()), at + j * 0.05, 0.34 * (1 - j / 7), pan=-0.05)
            put(buf, game.cloth(), at, 0.35, pan=-0.05)
        elif kind == "kick":
            put(buf, R.speed(game.dig("wood"), 0.8), at, 0.3, pan=0.0)
            put(buf, R.thud(rng, 0.45), at, 0.3, pan=-0.1)
            put(buf, game.cloth(), at + 0.03, 0.28, pan=-0.1)
        elif kind == "roll":
            for j in range(3):
                put(buf, game.cloth(), at + j * 0.11, 0.32, pan=-0.1)
            put(buf, R.speed(game.dig("grass"), 0.7), at + 0.2, 0.22, pan=-0.1)
        elif kind == "fall":
            put(buf, R.thud(rng, 0.9), at, 0.5, pan=-0.1)
            put(buf, game.cloth(), at + 0.02, 0.35, pan=-0.1)
            put(buf, game.dig("grass"), at + 0.04, 0.25, pan=-0.1)
        elif kind == "claw":
            put(buf, game.dig("gravel"), at, 0.3, pan=-0.1)
            put(buf, game.cloth(), at + 0.12, 0.28, pan=-0.1)
            put(buf, R.speed(game.dig("grass"), 0.75), at + 0.2, 0.2, pan=-0.1)
        elif kind == "grab":
            put(buf, R.speed(game.dig("wood"), 0.6), at, 0.3, pan=-0.05)
            put(buf, game.cloth(), at + 0.02, 0.3, pan=-0.05)
        elif kind == "stab":
            put(buf, R.into_them(rng), at, 0.9)
            put(buf, R.into_them(rng), at + 0.16, 0.75)


if __name__ == "__main__":
    main()
