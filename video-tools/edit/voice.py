"""The voice lines the user records, put into a video as the camcorder's own microphone hears the one
holding it: band-limited, close, a little of the room, every line at an even level.

A voice folder holds the recordings and a map saying which line each one is:

  voice_map.json   [{"scene": "home", "line": "Home sweet home.", "file": "home-sweet-home.mp3"}, ...]
                   "line" is the line's text exactly as the edit's sheet has it; "gain" (optional) scales it.
  short_map.json   [{"at": "hello", "file": "...mp3"}, ...]   for the short: a key of its voice_at.json, or seconds.
"""
import json
import subprocess
from pathlib import Path

import numpy as np

LEVEL = 0.12  # the loud part of every line, as RMS
BASE_DISTANCE = 0.4  # how far the camera's microphone always is from their mouth (0 at it, 1 well away)


def decode(path, sr, ff="ffmpeg"):
    raw = subprocess.run([ff, "-loglevel", "error", "-i", str(path), "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def cut(y, sr, a=None, b=None):
    """Only part of a recording: from {a} to {b} seconds into it (a map entry's "from" and "to")."""
    i = int((a or 0) * sr)
    j = len(y) if b is None else int(b * sr)
    return y[i:j]


def squeeze(y, sr, most=None):
    """The pauses in a line made no longer than {most} seconds (a map entry's "squeeze"), so it fits."""
    if not most:
        return y
    k = max(1, int(sr * 0.01))
    env = np.convolve(np.abs(y), np.ones(k, np.float32) / k, "same")
    quiet = env < 0.05 * env.max()
    keep = np.ones(len(y), bool)
    i, n, m = 0, len(y), int(most * sr)
    while i < n:
        if quiet[i]:
            j = i
            while j < n and quiet[j]:
                j += 1
            if j - i > m and i > 0 and j < n:
                keep[i + m // 2:j - m // 2] = False
            i = j
        else:
            i += 1
    return y[keep]


def trim(y, sr):
    """The line without the silence round it (a breath's worth kept either side)."""
    k = max(1, int(sr * 0.01))
    env = np.convolve(np.abs(y), np.ones(k, np.float32) / k, "same")
    on = np.nonzero(env > 0.03 * env.max())[0]
    if len(on) == 0:
        return y
    return y[max(0, on[0] - int(sr * 0.03)):min(len(y), on[-1] + int(sr * 0.15))]


def _peak(y, sr, f, db, q):
    """A bell boost or cut of {db} at {f} Hz (RBJ peaking filter)."""
    from scipy import signal
    A = 10 ** (db / 40)
    w = 2 * np.pi * f / sr
    al = np.sin(w) / (2 * q)
    b = [1 + al * A, -2 * np.cos(w), 1 - al * A]
    a = [1 + al / A, -2 * np.cos(w), 1 - al / A]
    return signal.lfilter(b, a, y).astype(np.float32)


def _room(sr, secs, seed, tone):
    """A small room's (or the open air's) answer: decaying, darkened noise."""
    from scipy import signal
    rng = np.random.default_rng(seed)
    n = int(sr * secs)
    t = np.arange(n) / sr
    ir = rng.normal(0, 1, n).astype(np.float32) * np.exp(-6.9 * t / secs)
    ir[:int(sr * 0.004)] = 0
    ir = signal.sosfilt(signal.butter(2, tone, "low", fs=sr, output="sos"), ir).astype(np.float32)
    return ir / np.sqrt((ir ** 2).sum())


def _mic(y, sr, dist, covered):
    """A line through the camcorder's microphone, {dist} (0..1) off: thin and boxy (darker the
    further), the camera's automatic level, a little distortion, the tape's wow. Mono, at LEVEL."""
    from scipy import signal
    frame = max(1, int(sr * 0.05))

    def loudness(x):
        r = np.sqrt(np.convolve(x * x, np.ones(frame, np.float32) / frame, "same"))
        return np.percentile(r, 90) if len(r) else 0.0

    l0 = loudness(y)
    if l0 > 1e-6:
        y = y * (LEVEL / l0)
    n = len(y)
    t = np.arange(n) / sr
    d = (0.0007 * np.sin(2 * np.pi * 0.6 * t) + 0.00012 * np.sin(2 * np.pi * 7.1 * t + 1.3)) * sr
    y = np.interp(np.arange(n) - d - 40, np.arange(n), y, left=0, right=0).astype(np.float32)
    top = 5000 * (1 - 0.42 * dist) * (1 - 0.65 * covered)
    y = signal.sosfilt(signal.butter(4, 180 + 140 * dist, "high", fs=sr, output="sos"), y)
    y = signal.sosfilt(signal.butter(4, max(900, top), "low", fs=sr, output="sos"), y).astype(np.float32)
    y = _peak(y, sr, 2000, 5.0, 0.9)
    y = _peak(y, sr, 450, 2.5, 1.2)
    # The camera's automatic level: down at once on anything loud, back up slowly after.
    att, rel = np.exp(-1 / (sr * 0.003)), np.exp(-1 / (sr * 0.4))
    env = np.empty(n, np.float32)
    e = 0.0
    for i, v in enumerate(np.abs(y).tolist()):
        e = att * e + (1 - att) * v if v > e else rel * e + (1 - rel) * v
        env[i] = e
    thr = LEVEL * 1.2
    y = y * ((np.maximum(env, thr) / thr) ** (1 / 4 - 1)).astype(np.float32)
    l1 = loudness(y)
    if l1 > 1e-6:
        y = y * (LEVEL / l1)
    y = (np.tanh(y * 2.2) / 2.2).astype(np.float32)
    l2 = loudness(y)
    if l2 > 1e-6:
        y = y * (LEVEL * (1 - 0.3 * covered) / l2)
    return y.astype(np.float32)


def _space(y, sr, indoor, extra=0.0):
    """The room's (or the open air's) answer to {y}, stereo: a small room, short and faint; outside,
    next to none."""
    from scipy import signal
    secs, tone, wet = (0.16, 3000, 0.07) if indoor else (0.1, 2500, 0.03)
    wet += extra
    pad = int(sr * secs)
    out = np.zeros((2, len(y) + pad), np.float32)
    for c in range(2):
        r = signal.fftconvolve(y, _room(sr, secs, 11 + c, tone)).astype(np.float32)[:out.shape[1]]
        out[c, :len(r)] += r * wet
    return out


def camcorder(y, sr, gain=1.0, ctx=None):
    """Stereo, as a camcorder's own little microphone hears a line said {ctx["far"]} (0..1) further
    off than a camera held out from the face, maybe with a hand over it ("covered"), indoors or out."""
    ctx = ctx or {}
    far = float(np.clip(ctx.get("far", 0.0), 0, 1))
    covered = float(np.clip(ctx.get("covered", 0.0), 0, 1))
    dist = float(np.clip(BASE_DISTANCE + far, 0, 1))
    y = _mic(y, sr, dist, covered) * gain * (1 - 0.45 * dist)
    out = _space(y, sr, bool(ctx.get("indoor", False)), 0.1 * far)
    out[:, :len(y)] += y * (1 - 0.3 * far)
    return out


HELD = 0.32  # how far their mouth is from the camera held out in front of them, in blocks (the level a line is set to)


def spatial(y, sr, gain, path, fps, indoor=False, covered=0.0):
    """A line said by them, heard at the camera: {path} gives, for each frame from the line's start,
    how far their head is from the camera (blocks) and which side of it (-1 left .. 1 right). The
    further off, the quieter and darker the voice, the room staying as it is; panned to their side."""
    near = _mic(y, sr, BASE_DISTANCE, covered)
    far_ = _mic(y, sr, 1.0, covered)
    n = min(len(near), len(far_))
    near, far_ = near[:n], far_[:n]
    k = np.arange(n) / sr * fps
    idx = np.arange(len(path))
    d = np.interp(k, idx, [p[0] for p in path]).astype(np.float32)
    pan = np.interp(k, idx, [p[1] for p in path]).astype(np.float32)
    f = np.clip((d - HELD) / 1.4, 0, 1)
    g = np.clip(HELD / np.maximum(d, 0.15), 0.25, 1.3)  # never more than 12 dB down: still heard across the room
    dry = (near * (1 - f) + far_ * f) * g * gain
    th = (np.clip(pan, -1, 1) * 0.7 + 1) * np.pi / 4
    out = _space(near * gain, sr, indoor, 0.0)
    out[0, :n] += dry * np.cos(th) * np.sqrt(2)
    out[1, :n] += dry * np.sin(th) * np.sqrt(2)
    return out.astype(np.float32)


def place(bus, snd, at, sr):
    i = int(round(at * sr))
    if i >= bus.shape[1]:
        return
    j = min(bus.shape[1], i + snd.shape[1])
    bus[:, max(0, i):j] += snd[:, max(0, -i):j - i]


GAP = 0.8      # silence between one line and the next, at least
EARLY = 0.4    # how much earlier than its moment a line may start, to finish before the cut


def long_bus(voice, sheet, n, sr, stretches, ff="ffmpeg", ctx=None):
    """The long video's voice: each mapped line where the sheet puts it. Never over another line (a
    line waits for the one before, plus a gap), never cut off by the tape stopping (it may start a
    little early to fit); a line that cannot be fitted at its moment is left out, and said so."""
    bus = np.zeros((2, n), np.float32)
    done = set()
    mp = Path(voice) / "voice_map.json"
    if not mp.exists():
        return bus, done
    entries = json.loads(mp.read_text())
    timed = []
    for e in entries:
        hits = [at for at, nm, tx in sheet if nm == e["scene"] and tx == e["line"] and at is not None]
        for at in hits:
            timed.append((at, e))
    timed.sort(key=lambda r: r[0])
    free = -1.0
    for want, e in timed:
        raw = trim(squeeze(cut(decode(Path(voice) / e["file"], sr, ff), sr, e.get("from"), e.get("to")), sr, e.get("squeeze")), sr)
        secs = len(raw) / sr
        st = [(a, b) for a, b in stretches if a <= want < b]
        a0, b0 = st[0] if st else (want, want + secs + 1)
        at = max(want, free + GAP)
        if at + secs > b0 - 0.08:
            at = b0 - 0.08 - secs
        if at < want - EARLY or at < free + GAP or at < a0:
            print(f"voice: LEFT OUT {e['scene']}: {e['line'][:50]} ({secs:.1f}s at {want:.1f}, tape stops {b0:.1f})")
            continue
        c = dict(ctx(at, secs)) if ctx else {}
        c.update(e.get("ctx", {}))
        if c.get("path"):
            snd = spatial(raw, sr, e.get("gain", 1.0), c["path"], c.get("fps", 20), bool(c.get("indoor")), float(c.get("covered", 0.0)))
        else:
            snd = camcorder(raw, sr, e.get("gain", 1.0), c)
        place(bus, snd, at, sr)
        free = at + secs
        done.add((e["scene"], e["line"]))
        path = c.get("path") or []
        where = f"head {path[0][0]:.2f} to {path[-1][0]:.2f} blocks, pan {path[0][1]:+.2f} to {path[-1][1]:+.2f}" if path else str(c)
        print(f"voice: {e['scene']:9s} {at:7.2f}-{free:7.2f} (moment {want:7.2f})  {e['line'][:40]:40s} {where}")
    return bus, done


def mix_into(video_in, video_out, placements, sr=48000, ff="ffmpeg"):
    """Adds lines to a finished video's sound (the picture copied as it is): placements are
    (seconds, sound) pairs, sound being stereo at {sr}. Peaks kept under the ceiling."""
    raw = subprocess.run([ff, "-loglevel", "error", "-i", str(video_in), "-vn", "-ac", "2", "-ar", str(sr), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    a = np.frombuffer(raw, np.float32).reshape(-1, 2).T.copy()
    for at, snd in placements:
        place(a, snd * 0.55, at, sr)
    peak = np.abs(a).max()
    if peak > 0.95:
        a *= 0.95 / peak
    tmp = Path(str(video_out) + ".wav")
    pcm = (np.clip(a.T, -1, 1) * 32000).astype(np.int16)
    import wave
    with wave.open(str(tmp), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    subprocess.run([ff, "-y", "-loglevel", "error", "-i", str(video_in), "-i", str(tmp), "-map", "0:v", "-map", "1:a", "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(video_out)], check=True)
    tmp.unlink()
