"""Links each scene's clips into WORK/clips from the newest download in WORK/runs that has it: only as
many pieces as the scene has frames (a folder kept between runs can still hold a longer take's last pieces).
Usage: python3 link.py WORK"""
import math, os, sys
from pathlib import Path
V4 = Path(sys.argv[1])
runs = sorted((d for d in (V4 / "runs").iterdir() if d.is_dir() and (d / "reel").is_dir() and len(d.name) == 40), key=lambda d: d.stat().st_mtime)
out = V4 / "clips"
out.mkdir(exist_ok=True)
for f in out.iterdir():
    if f.is_symlink():
        f.unlink()
for name in ["home", "camp", "pasture", "well", "field", "intruder", "visitor", "doorway", "radio", "house", "chapel", "graves", "knock",
             "morning", "mine", "found"]:
    have = [r for r in runs if (r / "reel" / f"{name}.0.roll").exists()]
    if not have:
        print(name, "missing"); continue
    src = have[-1] / "reel"
    roll = src / f"{name}.0.roll"
    n = sum(1 for l in roll.read_text().split("\n") if l.strip())
    marks = (src / f"{name}.0.txt").read_text().split()
    hit = next((int(marks[i - 1]) for i in range(1, len(marks)) if marks[i] == "hit"), None)
    if hit is not None:
        n = hit + 4
    pieces = math.ceil(n / 300)
    for ext in ("0.roll", "0.txt"):
        os.symlink(src / f"{name}.{ext}", out / f"{name}.{ext}")
    for k in range(pieces):
        os.symlink(src / f"{name}.{k}.mp4", out / f"{name}.{k}.mp4")
    if hit is not None and (src / f"{name}-wide.mp4").exists():
        os.symlink(src / f"{name}-wide.mp4", out / f"{name}-wide.mp4")
    print(name, n, "frames,", pieces, "pieces", "(+wide)" if hit is not None else "", "from", src.parent.name[:8])
s = V4 / "runs" / "reel" / "sounds"
if s.exists():
    os.symlink(s, out / "sounds")
