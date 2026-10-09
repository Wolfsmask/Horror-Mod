#!/usr/bin/env python3
"""
Checks that the textures tools/generate_model.py just wrote are the ones committed, to within a
shade: the face is painted with sines and exponentials, and those can come out a hair different on
another machine's CPU, so byte-for-byte would fail on nothing. Puts the committed files back.
"""
import io
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

TEX = "src/main/resources/assets/occupant/textures/entity"
bad = []
for path in sorted(Path(TEX).glob("*.png")):
    committed = subprocess.run(["git", "show", "HEAD:%s" % path.as_posix()], capture_output=True, check=True).stdout
    old = np.asarray(Image.open(io.BytesIO(committed)).convert("RGBA")).astype(int)
    new = np.asarray(Image.open(path).convert("RGBA")).astype(int)
    if old.shape != new.shape:
        bad.append("%s is %s, but %s was committed" % (path.name, new.shape, old.shape))
    else:
        diff = np.abs(old - new)
        off = (diff.max(axis=2) > 3).mean()
        if diff.max() > 12 or off > 0.001:
            bad.append("%s: %.3f%% of texels differ (by up to %d)" % (path.name, 100 * off, diff.max()))
    subprocess.run(["git", "checkout", "--", path.as_posix()], check=True)
for line in bad:
    print(line)
sys.exit(1 if bad else 0)
