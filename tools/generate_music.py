#!/usr/bin/env python3
"""
The Occupant's score: three seamless loops the client mixes live, by how far the story has gone
and how close it is. Synthesised, like the mod's other sounds, so nothing here is borrowed.

    python3 tools/generate_music.py

  dread_low    a low drone with a slow, uneven beat in it: always there once the story starts
  dread_high   high, bowed-glass dissonance that swells and fades: from the second act
  dread_pulse  a muffled pulse and a far-off metal scrape: when it is close
"""
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/occupant/sounds"
SR = 44100
LEN = 32.0
rng = np.random.default_rng(1666)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def bandpass(x, lo, hi):
    f = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    f[(freqs < lo) | (freqs > hi)] = 0
    return np.fft.irfft(f, len(x))


def peak(x, level):
    return x / (np.max(np.abs(x)) + 1e-9) * level


def loop(x, cross=2.0):
    """Seamless: the last {cross} seconds are faded into the first."""
    n = int(cross * SR)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    ramp = np.linspace(0, 1, n)
    return np.concatenate([tail * (1 - ramp) + head * ramp, body])


def dread_low():
    t = t_axis(LEN + 2)
    x = np.zeros_like(t)
    # Periods that fit the loop exactly, so it never jumps.
    for f, a in ((36.0, 1.0), (36.75, 0.8), (54.0, 0.35), (72.5, 0.25)):
        x += a * np.sin(2 * np.pi * f * t + rng.random() * 6)
    x *= 0.7 + 0.3 * np.sin(2 * np.pi * t / 8.0) * np.sin(2 * np.pi * t / 5.33)
    rumble = bandpass(rng.standard_normal(len(t)), 18, 140)
    return peak(loop(peak(x, 1) + peak(rumble, 0.4)), 0.8)


def dread_high():
    t = t_axis(LEN + 2)
    x = np.zeros_like(t)
    for f in (1046.5, 1108.7, 1567.9, 1661.2, 2217.4):
        vib = 1 + 0.003 * np.sin(2 * np.pi * (0.3 + rng.random() * 0.4) * t)
        x += np.sin(2 * np.pi * f * np.cumsum(vib) / SR) * (0.5 + 0.5 * np.sin(2 * np.pi * t / (4 + rng.random() * 6) + rng.random() * 6))
    bow = bandpass(rng.standard_normal(len(t)), 900, 3200) * 0.25
    swell = 0.35 + 0.65 * np.sin(np.pi * (t % 16.0) / 16.0) ** 2
    return peak(loop((x + bow) * swell), 0.5)


def dread_pulse():
    t = t_axis(LEN + 2)
    x = np.zeros_like(t)
    beat = 1.6
    for start in np.arange(0, LEN + 2, beat):
        i0 = int(start * SR)
        tt = t[: len(t) - i0]
        thud = np.sin(2 * np.pi * 48 * tt) * np.exp(-tt * 9) + 0.5 * np.sin(2 * np.pi * 70 * tt) * np.exp(-tt * 14)
        x[i0:] += thud
    scrape = np.zeros_like(t)
    for start in (5.0, 19.0, 27.5):
        i0, n = int(start * SR), int(2.4 * SR)
        seg = bandpass(rng.standard_normal(n), 1500, 6000) * np.sin(np.linspace(0, np.pi, n)) ** 2
        scrape[i0:i0 + n] += seg * 0.5
    return peak(loop(peak(x, 1) + scrape), 0.85)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("dread_low", dread_low), ("dread_high", dread_high), ("dread_pulse", dread_pulse)):
        data = fn().astype(np.float32)
        sf.write(ROOT / f"{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
        print(f"{name}.ogg  {len(data) / SR:.1f}s")


if __name__ == "__main__":
    main()
