#!/usr/bin/env python3
"""
Builds the Occupant's body: both the Java geometry and the texture it is painted with.

    pip install numpy pillow
    python3 tools/generate_model.py

Writing both from one description means the texture coordinates in the model and the pixels in
the texture can never drift apart. It writes:

    src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java
    src/main/resources/assets/occupant/textures/entity/occupant.png
    src/main/resources/assets/occupant/textures/entity/occupant_eyes.png

The animation lives in OccupantModel.java, which is written by hand.

What it is: a mass of faces. Not one creature with a head, but many people fused into one
hunched column of wet dark flesh, each face still trying to move on its own. Units are pixels;
the ground is at y = 24 and up is -y (the same convention as vanilla models).
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/occupant/textures/entity"
JAVA = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java"
TEX_W = TEX_H = 128

rng = np.random.default_rng(404)

# --------------------------------------------------------------------------- the body

# A face and the jaw hanging under it. Each one is a separate bone so it can move on its own.
# name, parent, pivot, rotation (radians), face size (w, h, d), jaw height
FACES = [
    # The head of the mass: the largest face, pushed forward, looking at you.
    ("face_main", "crown", (0.0, -3.0, -3.0), (0.05, 0.0, 0.05), (7, 8, 5), 3),
    # Four more growing out of the same skull, at angles no neck could make.
    ("face_high", "crown", (-1.5, -9.5, 0.5), (-0.5, -0.3, 0.7), (5, 6, 4), 2),
    ("face_right", "crown", (-6.0, -2.5, -1.0), (0.1, -1.3, -0.55), (5, 7, 4), 2),
    ("face_left", "crown", (6.0, -4.5, 0.0), (-0.2, 1.4, 0.65), (5, 6, 4), 2),
    ("face_back", "crown", (0.5, -5.5, 3.0), (0.2, 2.9, 0.3), (6, 6, 4), 2),
    # And more down the body, pressing out through it. One of them is upside down.
    ("face_chest", "mass", (-2.0, -13.0, -5.5), (0.2, -0.35, -0.3), (6, 7, 4), 2),
    ("face_side", "mass", (7.0, -16.0, 0.5), (0.0, 1.5, 0.85), (5, 6, 3), 2),
    ("face_low", "mass", (3.0, -6.5, -4.5), (-0.25, 0.4, 0.2), (5, 6, 4), 2),
    ("face_under", "mass", (-4.0, -1.5, -3.5), (0.3, -0.55, 3.05), (4, 6, 3), 2),
    ("face_deep", "mass", (-6.5, -9.5, 1.5), (0.0, -1.9, -0.65), (4, 5, 3), 1),
]
# The faces that catch the light. Any more than this and it stops being frightening.
GLOWING = {"face_main", "face_chest"}

SIDES = ("front", "back", "left", "right")


def parts():
    """Every bone, in order: name, parent, pivot, rotation, [(kind, x, y, z, w, h, d)]."""
    p = [
        # HumanoidModel looks these up by name. They draw nothing; the head anchor is only used
        # to find out where the viewer is.
        ("head", None, (0, -24, 0), (0, 0, 0), []),
        ("hat", "head", (0, 0, 0), (0, 0, 0), []),
        ("body", None, (0, 0, 0), (0, 0, 0), []),
        ("right_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("left_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("right_leg", None, (0, 0, 0), (0, 0, 0), []),
        ("left_leg", None, (0, 0, 0), (0, 0, 0), []),

        # The column. It has no legs: it ends in a hanging shroud that drags on the ground.
        ("mass", None, (0, 0, 0), (0, 0, 0), [
            ("flesh", -6.5, -21, -4, 13, 9, 8),     # shoulders, where most of them are
            ("flesh", -5.5, -12, -3.5, 11, 9, 7),   # ribs
            ("flesh", -4, -3, -2.5, 8, 7, 5),       # waist, narrow
        ]),
        ("shroud", "mass", (0, 4, 0), (0, 0, 0), [
            ("cloth", -5.5, 0, -4.0, 11, 12, 8),
            ("cloth", -7.0, 11, -5.0, 14, 10, 10),  # it widens where it drags on the ground
        ]),
        ("crown", "mass", (0, -21, 0), (0, 0, 0), []),
    ]

    for name, parent, pivot, rot, (w, h, d), jh in FACES:
        p.append((name, parent, pivot, rot, [("face", -w / 2.0, -h, -d / 2.0, w, h, d)]))
        p.append((name + "_jaw", name, (0, 0, 0), (0, 0, 0),
                  [("jaw", -w / 2.0 + 0.5, 0, -d / 2.0, w - 1, jh, d - 1)]))

    # Two long arms out of the shoulders: upper arm, forearm, a palm, four two-jointed fingers.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_shoulder", "mass", (sx * 6.5, -18.5, 0.0), (0, 0, 0),
                  [("flesh", -1.5, -1.5, -1.5, 3, 11, 3)]))
        p.append((side + "_forearm", side + "_shoulder", (0, 9.5, 0), (0, 0, 0),
                  [("bone", -1.0, 0, -1.0, 2, 10, 2)]))
        p.append((side + "_palm", side + "_forearm", (0, 10.0, 0), (0, 0, 0),
                  [("bone", -1.0, 0, -0.75, 2, 2, 1.5)]))
        for i in range(4):
            outer = i in (0, 3)
            a, b = (3.0, 2.5) if outer else (4.5, 3.5)
            p.append((f"{side}_finger{i}", side + "_palm", (-0.75 + i * 0.5, 2.0, 0.0), (0, 0, 0),
                      [("bone", -0.2, 0, -0.2, 0.4, a, 0.4)]))
            p.append((f"{side}_tip{i}", f"{side}_finger{i}", (0, a, 0), (0, 0, 0),
                      [("bone", -0.18, 0, -0.18, 0.36, b, 0.36)]))
    return p


# --------------------------------------------------------------------------- texture packing

def unfolded(w, h, d):
    """Size of a cube's unwrapped texture region."""
    return int(2 * round(d) + 2 * round(w)), int(round(d) + round(h))


def pack(boxes):
    """Shelf-packs every cube into the texture. Returns {index: (u, v)}."""
    order = sorted(range(len(boxes)), key=lambda i: -unfolded(*boxes[i][5:8])[1])
    placed = {}
    x = y = shelf = 0
    for i in order:
        bw, bh = unfolded(*boxes[i][5:8])
        bw, bh = max(bw, 1), max(bh, 1)
        if x + bw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        if y + bh > TEX_H:
            raise SystemExit("texture is full: make it bigger")
        placed[i] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    return placed


def faces_of(u, v, w, h, d):
    w, h, d = max(round(w), 1), max(round(h), 1), max(round(d), 1)
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# --------------------------------------------------------------------------- painting

FLESH = (26, 22, 21)       # wet, almost black
CLOTH = (11, 10, 11)
SKIN = (110, 102, 93)      # the faces: grey, drained
BONE = (150, 142, 128)


def paint_texture(boxes, placed):
    img = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    glow = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)

    def fill(region, rgb, jitter=6):
        x0, y0, x1, y1 = region
        if x1 <= x0 or y1 <= y0:
            return
        n = rng.integers(-jitter, jitter + 1, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(rgb) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    for i, (owner, kind, _x, _y, _z, w, h, d) in enumerate(boxes):
        u, v = placed[i]
        f = faces_of(u, v, w, h, d)
        if kind == "cloth":
            for side in f.values():
                fill(side, CLOTH, 3)
            # The shroud ends in tatters, and something shows through the folds.
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                for x in range(x0, x1):
                    cut = int(rng.integers(0, 5))
                    if cut:
                        img[y1 - cut:y1, x, 3] = 0
                for _ in range((x1 - x0) // 3):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0, y1 - 2))
                    img[fy:fy + 2, fx, :3] = (30, 26, 24)
        elif kind == "flesh":
            for side in f.values():
                fill(side, FLESH, 7)
            # Wet highlights, and the shapes of more faces pressing out from the inside.
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                for _ in range(max(1, (x1 - x0) * (y1 - y0) // 14)):
                    fx, fy = int(rng.integers(x0, x1)), int(rng.integers(y0, y1))
                    img[fy, fx, :3] = (48, 42, 40)
                for _ in range(max(1, (x1 - x0) // 4)):
                    cx, cy = int(rng.integers(x0 + 1, x1 - 1)), int(rng.integers(y0 + 1, y1 - 2))
                    img[cy, cx - 1:cx + 2, :3] = (14, 11, 12)   # a hollow
                    img[cy + 1, cx, :3] = (40, 34, 32)
        elif kind == "bone":
            for side in f.values():
                fill(side, (74, 68, 62), 8)
            x0, y0, x1, y1 = f["front"]
            img[max(y1 - 3, y0):y1, x0:x1, :3] = (38, 33, 30)   # dark at the fingertips
        elif kind == "jaw":
            for side in f.values():
                fill(side, SKIN, 8)
            x0, y0, x1, y1 = f["front"]
            img[y0, x0:x1, :3] = (58, 12, 12)                   # gum line
            for x in range(x0, x1, 2):
                img[y0, x, :3] = (196, 188, 168)                # lower teeth
            img[y1 - 1, x0:x1, :3] = (70, 64, 58)
        elif kind == "face":
            paint_face(img, glow, f, owner)

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_eyes.png")


def paint_face(img, glow, f, owner):
    """One human face: hollow sockets, a nose, and a mouth open far too wide."""
    for side in ("top", "bottom", "left", "right", "back"):
        x0, y0, x1, y1 = f[side]
        n = rng.integers(-7, 8, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(FLESH) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255
        # Wisps of hair over the sides and back of the skull.
        for _ in range((x1 - x0)):
            hx = int(rng.integers(x0, x1))
            hy = int(rng.integers(y0, min(y0 + 3, y1)))
            img[hy, hx, :3] = (8, 7, 8)

    x0, y0, x1, y1 = f["front"]
    w, h = x1 - x0, y1 - y0
    n = rng.integers(-9, 10, size=(h, w, 1))
    img[y0:y1, x0:x1, :3] = np.clip(np.array(SKIN) + n, 0, 255)
    img[y0:y1, x0:x1, 3] = 255

    # A hairline, and shadow where the skull shows through above the brow.
    img[y0, x0:x1, :3] = (16, 14, 15)
    img[y0 + 1, x0, :3] = (58, 52, 48)
    img[y0 + 1, x1 - 1, :3] = (58, 52, 48)

    # Sockets: deep enough that you cannot tell whether anything is inside them.
    ey = y0 + 1
    left, right = x0 + 1, x1 - 2
    socket_h = 2 if h >= 6 else 1
    img[ey:ey + socket_h, left, :3] = (5, 5, 6)
    img[ey:ey + socket_h, right, :3] = (5, 5, 6)
    if w >= 6:
        img[ey, left + 1, :3] = (12, 11, 12)
        img[ey, right - 1, :3] = (12, 11, 12)
    img[ey + socket_h, left, :3] = (66, 60, 55)          # hollow cheeks
    img[ey + socket_h, right, :3] = (66, 60, 55)

    # A thin nose between them.
    mid = x0 + w // 2
    img[ey + socket_h - 1:ey + socket_h + 1, mid, :3] = (92, 85, 78)

    # The mouth: stretched open far wider than a jaw goes, teeth along the top.
    mouth_h = 2 if h >= 6 else 1
    my = y1 - mouth_h
    img[my:y1, x0 + 1:x1 - 1, :3] = (22, 5, 7)
    for x in range(x0 + 1, x1 - 1, 2):
        img[my, x, :3] = (112, 105, 94)
    img[y1 - 1, x0, :3] = (44, 38, 36)                   # the corners tear upward
    img[y1 - 1, x1 - 1, :3] = (44, 38, 36)

    if owner in GLOWING:
        glow[ey, left] = (214, 220, 228, 255)
        glow[ey, right] = (214, 220, 228, 255)


# --------------------------------------------------------------------------- java

HEADER = """// GENERATED by tools/generate_model.py -- do not edit by hand.
// The texture assets/occupant/textures/entity/occupant.png is written by the same script,
// so these texture offsets and that image can never disagree.
package com.wolfsmask.occupant.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.List;

/** The Occupant's body: a hunched column of wet flesh with {@value #FACE_COUNT} faces in it. */
public final class OccupantGeometry {
\tpublic static final int FACE_COUNT = %d;
\t/** Every face bone, largest first. Each one has a child named {@code <name>_jaw}. */
\tpublic static final List<String> FACES = List.of(%s);

\tprivate OccupantGeometry() {
\t}

\tpublic static LayerDefinition create() {
\t\tMeshDefinition mesh = new MeshDefinition();
\t\tPartDefinition root = mesh.getRoot();
\t\tPartDefinition p;
"""


def num(v):
    return ("%.2ff" % v).replace(".00f", ".0f")


def write_java(ps, boxes, placed):
    lines = [HEADER % (len(FACES), ", ".join('"%s"' % f[0] for f in FACES))]
    box_at = {}
    for i, (owner, *_rest) in enumerate(boxes):
        box_at.setdefault(owner, []).append(i)

    var = {}
    for name, parent, pivot, rot, own in ps:
        cubes = "CubeListBuilder.create()"
        for i in box_at.get(name, []):
            _, _, x, y, z, w, h, d = boxes[i]
            u, v = placed[i]
            cubes += "\n\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (
                u, v, num(x), num(y), num(z), num(w), num(h), num(d))
        if any(rot):
            pose = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                num(pivot[0]), num(pivot[1]), num(pivot[2]), num(rot[0]), num(rot[1]), num(rot[2]))
        elif any(pivot):
            pose = "PartPose.offset(%s, %s, %s)" % (num(pivot[0]), num(pivot[1]), num(pivot[2]))
        else:
            pose = "PartPose.ZERO"
        target = "root" if parent is None else var[parent]
        lines.append('\t\tp = %s.addOrReplaceChild("%s", %s, %s);' % (target, name, cubes, pose))
        var[name] = "p_" + name
        lines.append("\t\tPartDefinition %s = p;" % var[name])

    lines.append("\n\t\treturn LayerDefinition.create(mesh, %d, %d);" % (TEX_W, TEX_H))
    lines.append("\t}")
    lines.append("}")
    JAVA.write_text("\n".join(lines) + "\n")


def main():
    ps = parts()
    boxes = [(name, *b) for name, _p, _piv, _r, own in ps for b in own]
    placed = pack(boxes)
    paint_texture(boxes, placed)
    write_java(ps, boxes, placed)
    used = max(placed[i][1] + unfolded(*boxes[i][5:8])[1] for i in placed)
    print("%d bones, %d cubes, texture %dx%d (%d rows used)" % (len(ps), len(boxes), TEX_W, TEX_H, used))


if __name__ == "__main__":
    main()
