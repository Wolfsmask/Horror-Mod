#!/usr/bin/env python3
"""
Draws the Occupant as the game would, from the generated OccupantGeometry.java and its texture,
without starting the game: for looking at a change to the body or the face straight away.

    python3 tools/preview_model.py OUT_DIR

It reads what the game reads (the bones, boxes and texture offsets in the Java, and the PNGs),
maps every face's texture the way Minecraft's ModelPart.Cube does, and rasterises the boxes with
a depth buffer and Minecraft's flat per-face shading. Writes face.png (close, from the front),
face34.png (close, three-quarters), body.png, and night.png (the face in the dark, with the glow
layer over it). The legs are only splayed roughly; in game they are planted by LegGait.
"""
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GEOM = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java"
TEX = ROOT / "src/main/resources/assets/occupant/textures/entity"

PART_RE = re.compile(r'p = (\w+)\.addOrReplaceChild\("(\w+)", (CubeListBuilder\.create\(\)(?:\s*\.texOffs\([^)]*\)\.addBox\((?:[^()]|\([^()]*\))*\))*), (PartPose\.[^;]+)\);')
BOX_RE = re.compile(r'\.texOffs\(([^)]*)\)\.addBox\(((?:[^()]|\([^()]*\))*)\)')
NUM_RE = re.compile(r'-?\d+\.?\d*')


def load():
    src = GEOM.read_text()
    size = re.search(r'LayerDefinition\.create\(mesh, (\d+), (\d+)\)', src)
    tex_w, tex_h = int(size.group(1)), int(size.group(2))
    bones = {}
    order = []
    for m in PART_RE.finditer(src):
        parent, name, cubes, pose = m.groups()
        parent = None if parent == "root" else parent[2:]
        vals = [float(v) for v in NUM_RE.findall(pose)] if "ZERO" not in pose else [0.0] * 3
        pivot = vals[:3]
        rot = vals[3:6] if len(vals) >= 6 else [0.0, 0.0, 0.0]
        boxes = []
        for uv, box in BOX_RE.findall(cubes):
            u, v = [float(t) for t in NUM_RE.findall(uv)]
            nums = [float(t) for t in NUM_RE.findall(box)]
            x, y, z, w, h, d = nums[:6]
            su = sv = 1.0
            if len(nums) >= 8:                       # (..., CubeDeformation.NONE, texScaleU, texScaleV)
                su, sv = nums[6], nums[7]
            boxes.append((u, v, x, y, z, w, h, d, su, sv))
        bones[name] = {"parent": parent, "pivot": pivot, "rot": rot, "boxes": boxes}
        order.append(name)
    return bones, order, tex_w, tex_h


def rot_zyx(zr, yr, xr):
    cz, sz = math.cos(zr), math.sin(zr)
    cy, sy = math.cos(yr), math.sin(yr)
    cx, sx = math.cos(xr), math.sin(xr)
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    return rz @ ry @ rx


def world_transforms(bones, order, pose):
    """Each bone's (rotation, translation) in model space, with extra rotations from `pose`."""
    out = {}
    for name in order:
        b = bones[name]
        extra = pose.get(name, (0.0, 0.0, 0.0))
        r = rot_zyx(b["rot"][2] + extra[2], b["rot"][1] + extra[1], b["rot"][0] + extra[0])
        t = np.array(b["pivot"], dtype=float)
        if b["parent"] is not None:
            pr, pt = out[b["parent"]]
            out[name] = (pr @ r, pt + pr @ t)
        else:
            out[name] = (r, t)
    return out


def quads(bones, order, transforms):
    """Every face as (4 model-space corners, 4 uv pairs in texture units, shade)."""
    faces = []
    for name in order:
        r, t = transforms[name]
        for (u, v, x, y, z, w, h, d, su, sv) in bones[name]["boxes"]:
            x1, y1, z1 = x + w, y + h, z + d
            c = lambda a, b_, c_: r @ np.array([a, b_, c_]) + t
            v1, v2, v3, v4 = c(x, y, z), c(x1, y, z), c(x1, y1, z), c(x, y1, z)
            v5, v6, v7, v8 = c(x, y, z1), c(x1, y, z1), c(x1, y1, z1), c(x, y1, z1)
            # The game divides the whole region, offset and sizes, by the box's texture scale.
            i_, j_, k_, l_ = u / su, (u + d) / su, (u + d + w) / su, (u + d + 2 * w) / su
            m_, n_ = (u + 2 * d + w) / su, (u + 2 * d + 2 * w) / su
            o_, p_, q_ = v / sv, (v + d) / sv, (v + d + h) / sv

            def poly(vs, u1, v1_, u2, v2_, shade):
                uvs = [(u2, v1_), (u1, v1_), (u1, v2_), (u2, v2_)]
                faces.append((vs, uvs, shade))

            # Model y is down: Minecraft's DOWN face is the top one on screen.
            poly([v6, v5, v1, v2], j_, o_, k_, p_, 1.0)     # top (y0)
            poly([v3, v4, v8, v7], k_, p_, l_, o_, 0.5)     # bottom (y1)
            poly([v1, v5, v8, v4], i_, p_, j_, q_, 0.6)     # x0
            poly([v2, v1, v4, v3], j_, p_, k_, q_, 0.8)     # front (z0)
            poly([v6, v2, v3, v7], k_, p_, m_, q_, 0.6)     # x1
            poly([v5, v6, v7, v8], m_, p_, n_, q_, 0.8)     # back (z1)
    return faces


def render(faces, tex, tex_w, tex_h, eye, target, size=(900, 900), fov=30.0, light=1.0, bg=(40, 40, 46),
           glow=None):
    W_, H_ = size
    forward = np.array(target, float) - np.array(eye, float)
    forward /= np.linalg.norm(forward)
    up_hint = np.array([0.0, -1.0, 0.0])                       # model up is -y
    right = np.cross(forward, up_hint)
    right /= np.linalg.norm(right)
    up = np.cross(right, forward)
    f = (H_ / 2) / math.tan(math.radians(fov) / 2)
    img = np.zeros((H_, W_, 3), float)
    img[:] = bg
    zbuf = np.full((H_, W_), np.inf)
    th, tw = tex.shape[:2]

    def project(p):
        d = p - eye
        x, y, z = d @ right, d @ up, d @ forward
        return np.array([W_ / 2 + f * x / z, H_ / 2 - f * y / z]), z

    for vs, uvs, shade in faces:
        pts, zs = zip(*[project(p) for p in vs])
        if min(zs) <= 0.05:
            continue
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = np.array([pts[k] for k in tri])
            Z = np.array([zs[k] for k in tri])
            UV = np.array([uvs[k] for k in tri], float)
            x0, y0 = np.floor(P.min(0)).astype(int)
            x1, y1 = np.ceil(P.max(0)).astype(int)
            x0, y0 = max(x0, 0), max(y0, 0)
            x1, y1 = min(x1, W_ - 1), min(y1, H_ - 1)
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, ay), (bx, by), (cx, cy) = P
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-9:
                continue
            w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
            w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            iz = w0 / Z[0] + w1 / Z[1] + w2 / Z[2]
            z = 1 / iz
            uu = (w0 * UV[0, 0] / Z[0] + w1 * UV[1, 0] / Z[1] + w2 * UV[2, 0] / Z[2]) * z
            vv = (w0 * UV[0, 1] / Z[0] + w1 * UV[1, 1] / Z[1] + w2 * UV[2, 1] / Z[2]) * z
            tx = np.clip((uu / tex_w * tw).astype(int), 0, tw - 1)
            ty = np.clip((vv / tex_h * th).astype(int), 0, th - 1)
            texel = tex[ty, tx]
            ok = inside & (texel[..., 3] > 25) & (z < zbuf[y0:y1 + 1, x0:x1 + 1])
            if not ok.any():
                continue
            col = texel[..., :3].astype(float) * shade * light
            if glow is not None:
                g = glow[ty, tx].astype(float)
                a = g[..., 3:4] / 255.0
                col = col * (1 - a) + g[..., :3] * a
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[ok] = col[ok]
            zbuf[y0:y1 + 1, x0:x1 + 1][ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def splayed(bones):
    """A rough standing pose: each leg out to its side and down, as LegGait would plant it."""
    src = GEOM.read_text()
    angles = [float(a) for a in re.search(r'LEG_ANGLE = \{([^}]*)\}', src).group(1).replace("f", "").split(",")]
    pose = {}
    for i, a in enumerate(angles):
        out = 1.05
        pose[f"leg{i}_upper"] = (-math.cos(a) * out, 0.0, math.sin(a) * out)
        pose[f"leg{i}_lower"] = (math.cos(a) * 1.6 * 0.95, 0.0, -math.sin(a) * 1.6 * 0.95)
    return pose


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out.mkdir(parents=True, exist_ok=True)
    bones, order, tw, th = load()
    tex = np.array(Image.open(TEX / "occupant.png").convert("RGBA"))
    glow = np.array(Image.open(TEX / "occupant_glow.png").convert("RGBA"))
    rest = world_transforms(bones, order, {})
    face = quads(bones, order, rest)
    skull = rest["skull"][1]
    head = skull + np.array([0.0, -1.0, 0.0])
    render(face, tex, tw, th, head + np.array([0.0, 1.5, -26.0]), head, fov=40).save(out / "face.png")
    render(face, tex, tw, th, head + np.array([-17.0, 3.0, -20.0]), head, fov=40).save(out / "face34.png")
    render(face, tex, tw, th, head + np.array([0.0, 1.5, -26.0]), head, fov=40, light=0.12, bg=(6, 6, 9),
           glow=glow).save(out / "night.png")
    body = quads(bones, order, world_transforms(bones, order, splayed(bones)))
    centre = np.array([0.0, -10.0, 0.0])
    render(body, tex, tw, th, centre + np.array([-40.0, 6.0, -150.0]), centre, size=(700, 900),
           fov=45).save(out / "body.png")
    print("wrote", ", ".join(str(out / n) for n in ("face.png", "face34.png", "night.png", "body.png")))


if __name__ == "__main__":
    main()
