"""Contact sheet of a clip's frames: python3 qa.py <dir> <scene> <out.jpg> t1 t2 ..."""
import subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw
d, name, out = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
ts = [int(x) for x in sys.argv[4:]]
tiles = []
for t in ts:
    k, j = t // 300, t % 300
    src = d / f"{name}.{k}.mp4"
    tmp = Path(out).with_suffix(".tmp.png")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-vf", f"select=eq(n\\,{j})", "-vframes", "1", str(tmp)], check=True)
    im = Image.open(tmp).convert("RGB").resize((640, 360))
    ImageDraw.Draw(im).text((8, 6), f"{name} {t}", fill=(255, 255, 0))
    tiles.append(im)
cols = 3
rows = (len(tiles) + cols - 1) // cols
sheet = Image.new("RGB", (640 * cols, 360 * rows))
for i, im in enumerate(tiles):
    sheet.paste(im, ((i % cols) * 640, (i // cols) * 360))
sheet.save(out, quality=88)
