"""The short with the user's voice in it: its voice-free master's sound with each recorded line laid in
where voice_at.json says it goes (the picture copied as it is).
Usage: python3 voice_short.py MASTER.mp4 voice_at.json VOICE_DIR OUT.mp4   (VOICE_DIR has short_map.json)"""
import json
import sys
from pathlib import Path

import voice as V

master, at_file, vdir, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), Path(sys.argv[4])
SR = 48000
times = json.loads(at_file.read_text())
placements = []
for e in json.loads((vdir / "short_map.json").read_text()):
    at = e["at"] if isinstance(e["at"], (int, float)) else times[e["at"]]
    raw = V.trim(V.squeeze(V.cut(V.decode(vdir / e["file"], SR), SR, e.get("from"), e.get("to")), SR, e.get("squeeze")), SR)
    snd = V.camcorder(raw, SR, e.get("gain", 1.0), e.get("ctx"))
    if e.get("echo"):
        snd = V.echo(snd, SR, float(e["echo"]))
    placements.append((at, snd))
    print("line", e["file"], "at", at)
V.mix_into(master, out, placements, SR)
print("made", out)
