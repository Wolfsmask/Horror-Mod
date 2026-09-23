#!/usr/bin/env python3
"""
Generates the sounds, the screen static and the mod icon, so nothing is borrowed from anyone else.

The Occupant's own body and its texture come from tools/generate_model.py instead, because those
two have to be built together.

    pip install numpy pillow soundfile
    python3 tools/generate_assets.py

Output goes to src/main/resources/assets/occupant. Re-running gives the same result (fixed seed).
Replace any file with your own art or recordings and it will be picked up as-is.
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "occupant"
SR = 44100
rng = np.random.default_rng(1337)


# --------------------------------------------------------------------------- textures

def static_noise(size=128):
    noise = rng.integers(0, 256, size=(size, size)).astype(np.float32)
    noise[1::2, :] *= 0.55  # scanlines
    # A few brighter horizontal tears.
    for _ in range(6):
        y = rng.integers(0, size)
        noise[y, :] = np.clip(noise[y, :] * 1.6 + 40, 0, 255)
    g = noise.astype(np.uint8)
    img = np.stack([g, g, g, np.full_like(g, 255)], axis=-1)
    return Image.fromarray(img, "RGBA")


def icon(size=128):
    img = Image.new("RGBA", (size, size), (6, 6, 8, 255))
    d = ImageDraw.Draw(img)
    s = size / 32
    # A figure standing in the dark, slightly too tall.
    d.rectangle([12 * s, 5 * s, 20 * s - 1, 13 * s - 1], fill=(14, 14, 17, 255))    # head
    d.rectangle([12 * s, 13 * s, 20 * s - 1, 25 * s - 1], fill=(12, 12, 15, 255))   # body
    d.rectangle([8.5 * s, 13 * s, 12 * s - 1, 27 * s - 1], fill=(11, 11, 14, 255))  # long arms
    d.rectangle([20 * s, 13 * s, 23.5 * s - 1, 27 * s - 1], fill=(11, 11, 14, 255))
    d.rectangle([12 * s, 25 * s, 20 * s - 1, 32 * s - 1], fill=(10, 10, 13, 255))   # legs
    for ex in (14, 17):
        d.rectangle([ex * s, 9 * s, (ex + 1) * s - 1, 10 * s - 1], fill=(236, 238, 242, 255))
    return img


# --------------------------------------------------------------------------- audio helpers

def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def bandpass(x, lo, hi, soft=0.15):
    """Smooth FFT band-pass (no ringing, no dependencies)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    lo_edge = np.clip((f - lo * (1 - soft)) / (lo * soft * 2 + 1e-9), 0, 1) if lo > 0 else np.ones_like(f)
    hi_edge = np.clip((hi * (1 + soft) - f) / (hi * soft * 2 + 1e-9), 0, 1)
    return np.fft.irfft(spec * lo_edge * hi_edge, n=len(x))


def peak(x, level):
    m = np.max(np.abs(x))
    return x * (level / m) if m > 0 else x


def fade(x, fade_in, fade_out):
    n_in, n_out = int(fade_in * SR), int(fade_out * SR)
    env = np.ones(len(x))
    if n_in:
        env[:n_in] = np.linspace(0, 1, n_in) ** 2
    if n_out:
        env[-n_out:] = np.linspace(1, 0, n_out) ** 2
    return x * env


def saw(freq, t):
    return 2.0 * ((freq * t) % 1.0) - 1.0


# --------------------------------------------------------------------------- sounds

def stinger():
    t = t_axis(2.4)
    # A deep hit with a falling pitch.
    boom_freq = 40 + 50 * np.exp(-t * 6)
    boom = np.sin(2 * np.pi * np.cumsum(boom_freq) / SR) * np.exp(-t * 3.0)
    # A dissonant, detuned cluster: the "wrong" chord.
    cluster = np.zeros_like(t)
    for f in (196.0, 207.65, 277.18, 293.66, 415.3, 440.0, 622.25):
        vib = 1 + 0.004 * np.sin(2 * np.pi * 6.3 * t + rng.random() * 6)
        cluster += saw(f * vib, t)
    cluster = bandpass(cluster, 80, 5000) * np.exp(-t * 1.9) * (1 - np.exp(-t * 400))
    # Noise burst for the physical impact.
    burst = bandpass(rng.standard_normal(len(t)), 800, 9000) * np.exp(-t * 11)
    # A thin, high screech gliding down.
    screech = np.sin(2 * np.pi * np.cumsum(2600 - 700 * t / t[-1]) / SR) * np.exp(-t * 2.2) * 0.25
    mix = 1.0 * boom + 0.55 * peak(cluster, 1) + 0.6 * peak(burst, 1) + screech
    return peak(np.tanh(mix * 1.8), 0.95)


def drone():
    t = t_axis(7.0)
    x = np.zeros_like(t)
    for f, a in ((41.2, 1.0), (43.65, 0.8), (61.7, 0.45), (82.4, 0.3), (87.3, 0.2)):
        x += a * np.sin(2 * np.pi * f * t + rng.random() * 6)
    x *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.18 * t)
    rumble = bandpass(rng.standard_normal(len(t)), 20, 180) * 0.6
    x = x / np.max(np.abs(x)) + peak(rumble, 0.5)
    return peak(fade(x, 1.6, 2.2), 0.85)


def breath():
    t = t_axis(3.2)
    noise = rng.standard_normal(len(t))
    inhale = bandpass(noise, 500, 2600)
    exhale = bandpass(noise, 220, 1400)
    x = np.zeros_like(t)

    def env(start, length, shape_pow):
        e = np.zeros_like(t)
        i0, n = int(start * SR), int(length * SR)
        e[i0:i0 + n] = np.sin(np.linspace(0, np.pi, n)) ** shape_pow
        return e

    x += inhale * env(0.05, 0.95, 1.4) * 0.55
    x += exhale * env(1.35, 1.35, 0.9) * 1.0
    x *= 1 + 0.12 * np.sin(2 * np.pi * 23 * t)  # a slight wet rasp
    return peak(x, 0.9)


def whisper():
    t = t_axis(2.8)
    out = np.zeros_like(t)
    pos = int(0.1 * SR)
    while pos < len(t) - int(0.3 * SR):
        n = int(rng.uniform(0.09, 0.22) * SR)
        seg = rng.standard_normal(n)
        center = rng.uniform(1800, 4200)
        seg = bandpass(seg, center * 0.6, center * 1.4)
        if rng.random() < 0.3:  # a sibilant "s"
            seg += bandpass(rng.standard_normal(n), 5500, 9500) * 0.8
        seg *= np.hanning(n)
        out[pos:pos + n] += seg * rng.uniform(0.5, 1.0)
        pos += n + int(rng.uniform(0.02, 0.12) * SR)
    return peak(fade(out, 0.05, 0.3), 0.8)


def knock():
    t = t_axis(1.5)
    x = np.zeros_like(t)
    for start, strength in ((0.0, 1.0), (0.33, 0.9), (0.66, 1.0)):
        i0 = int(start * SR)
        tt = t[: len(t) - i0]
        hit = (np.sin(2 * np.pi * 108 * tt) * np.exp(-tt * 26)
               + 0.6 * np.sin(2 * np.pi * 231 * tt) * np.exp(-tt * 38)
               + 0.3 * np.sin(2 * np.pi * 517 * tt) * np.exp(-tt * 70))
        click = bandpass(rng.standard_normal(len(tt)), 200, 3000) * np.exp(-tt * 260) * 0.8
        x[i0:] += (hit + click) * strength
    return peak(np.tanh(x * 1.3), 0.95)


def static_burst():
    t = t_axis(1.2)
    noise = bandpass(rng.standard_normal(len(t)), 400, 9000)
    seg = int(0.03 * SR)
    gate = np.repeat(rng.uniform(0.25, 1.0, size=len(t) // seg + 1), seg)[: len(t)]
    crackle = np.zeros_like(t)
    idx = rng.integers(0, len(t), size=120)
    crackle[idx] = rng.uniform(-1, 1, size=len(idx)) * 4
    hum = 0.15 * np.sin(2 * np.pi * 60 * t)
    return peak(fade(noise * gate + crackle + hum, 0.02, 0.25), 0.7)


# --------------------------------------------------------------------------- main

def main():
    tex = ROOT / "textures"
    (tex / "entity").mkdir(parents=True, exist_ok=True)
    (tex / "misc").mkdir(parents=True, exist_ok=True)
    static_noise().save(tex / "misc" / "static.png")
    icon().save(ROOT / "icon.png")

    snd = ROOT / "sounds"
    snd.mkdir(parents=True, exist_ok=True)
    for name, fn in (("stinger", stinger), ("drone", drone), ("breath", breath),
                     ("whisper", whisper), ("knock", knock), ("static", static_burst)):
        data = fn().astype(np.float32)  # mono: required for positional sound in Minecraft
        sf.write(snd / f"{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
        print(f"{name}.ogg  {len(data) / SR:.1f}s")
    print("done:", ROOT)


if __name__ == "__main__":
    main()
