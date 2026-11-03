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


def decode(path, sr, ff="ffmpeg"):
    raw = subprocess.run([ff, "-loglevel", "error", "-i", str(path), "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def trim(y, sr):
    """The line without the silence round it (a breath's worth kept either side)."""
    k = max(1, int(sr * 0.01))
    env = np.convolve(np.abs(y), np.ones(k, np.float32) / k, "same")
    on = np.nonzero(env > 0.03 * env.max())[0]
    if len(on) == 0:
        return y
    return y[max(0, on[0] - int(sr * 0.03)):min(len(y), on[-1] + int(sr * 0.15))]


def camcorder(y, sr, gain=1.0):
    """Stereo, as the camera hears it: 110 Hz to 7 kHz, evened out, a touch of the space round them."""
    f = np.fft.rfftfreq(len(y), 1 / sr)
    shape = 1 / (1 + (110 / np.maximum(f, 1)) ** 4) / (1 + (f / 7000) ** 4)
    y = np.fft.irfft(np.fft.rfft(y) * shape, len(y)).astype(np.float32)
    frame = max(1, int(sr * 0.05))
    rms = np.sqrt(np.convolve(y * y, np.ones(frame, np.float32) / frame, "same"))
    loud = np.percentile(rms, 90) if len(rms) else 0
    if loud > 1e-6:
        y = y * (LEVEL * gain / loud)
    y = (np.tanh(y / 0.8) * 0.8).astype(np.float32)
    out = np.stack([y.copy(), y.copy()])
    for c, d in ((0, 0.011), (1, 0.017)):
        n = int(sr * d)
        out[c, n:] += y[:-n] * 0.12
    return out


def place(bus, snd, at, sr):
    i = int(round(at * sr))
    if i >= bus.shape[1]:
        return
    j = min(bus.shape[1], i + snd.shape[1])
    bus[:, max(0, i):j] += snd[:, max(0, -i):j - i]


GAP = 0.8      # silence between one line and the next, at least
EARLY = 0.4    # how much earlier than its moment a line may start, to finish before the cut


def long_bus(voice, sheet, n, sr, stretches, ff="ffmpeg"):
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
        snd = camcorder(trim(decode(Path(voice) / e["file"], sr, ff), sr), sr, e.get("gain", 1.0))
        secs = snd.shape[1] / sr
        st = [(a, b) for a, b in stretches if a <= want < b]
        a0, b0 = st[0] if st else (want, want + secs + 1)
        at = max(want, free + GAP)
        if at + secs > b0 - 0.08:
            at = b0 - 0.08 - secs
        if at < want - EARLY or at < free + GAP or at < a0:
            print(f"voice: LEFT OUT {e['scene']}: {e['line'][:50]} ({secs:.1f}s at {want:.1f}, tape stops {b0:.1f})")
            continue
        place(bus, snd, at, sr)
        free = at + secs
        done.add((e["scene"], e["line"]))
        print(f"voice: {e['scene']:9s} {at:7.2f}-{free:7.2f} (moment {want:7.2f})  {e['line'][:50]}")
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
