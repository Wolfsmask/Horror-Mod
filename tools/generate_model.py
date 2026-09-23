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

What it is: something far too tall and far too thin, the colour of old bone. Small blank head,
two black pits where eyes should be, a neck that is much too long, a ribcage you can count, and
legs that take up more than half of it. Early in the story it is under a dark shroud, so from a
distance you cannot tell what it is. Later the shroud is gone.

Units are pixels; the ground is at y = 24 and up is -y (the same convention as vanilla models).
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/occupant/textures/entity"
JAVA = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java"
TEX_W = TEX_H = 128

rng = np.random.default_rng(909)

SIDES = ("front", "back", "left", "right")

# Heights, in pixels above the ground (the model is built upside down: up is -y).
HIP = 27.0        # more than half of it is leg
CHEST = 43.0
SHOULDER = 42.0
NECK_TOP = 50.0
# Bones that only exist to be hidden: the shroud it wears early on.
SHROUD = ("cloak", "hood")


def parts():
    """Every bone, in order: name, parent, pivot, rotation, [(kind, x, y, z, w, h, d)]."""
    p = [
        # HumanoidModel looks these up by name. They draw nothing; the head anchor is only used
        # to find out where the viewer is.
        ("head", None, (0, -NECK_TOP, 0), (0, 0, 0), []),
        ("hat", "head", (0, 0, 0), (0, 0, 0), []),
        ("body", None, (0, 0, 0), (0, 0, 0), []),
        ("right_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("left_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("right_leg", None, (0, 0, 0), (0, 0, 0), []),
        ("left_leg", None, (0, 0, 0), (0, 0, 0), []),

        # Hips, then a long spine with a ribcage you can count.
        ("hips", None, (0, -HIP, 0), (0, 0, 0), [
            ("bone", -2.5, -1.0, -1.5, 5, 5, 3),
        ]),
        ("spine", "hips", (0, -1.0, 0), (0, 0, 0), [
            ("bone", -1.5, -15.0, -1.0, 3, 15, 2),          # the spine itself, thin
        ]),
        ("ribs", "spine", (0, -13.0, 0), (0, 0, 0), [
            ("ribs", -3.0, -1.0, -2.0, 6, 11, 4),
        ]),
        # Shoulders: a thin yoke, wider than the chest, with nothing on it.
        ("yoke", "spine", (0, -15.0, 0), (0, 0, 0), [
            ("bone", -5.5, -1.5, -1.5, 11, 3, 3),
        ]),

        # A neck much too long for a person, in two pieces so it can crane.
        ("neck", "yoke", (0, -1.0, 0), (0, 0, 0), [
            ("bone", -1.0, -4.0, -1.0, 2, 4, 2),
        ]),
        ("neck2", "neck", (0, -4.0, 0), (0, 0, 0), [
            ("bone", -1.0, -4.0, -1.0, 2, 4, 2),
        ]),
        # A small, smooth, blank head. No hair, no ears, no expression.
        ("skull", "neck2", (0, -4.0, 0), (0, 0, 0), [
            ("face", -2.5, -5.0, -2.0, 5, 5, 4),
        ]),
        # The mouth only exists when it opens: a black slot down the middle of the face.
        ("maw", "skull", (0, -1.6, -2.0), (0, 0, 0), [
            ("maw", -1.0, 0.0, -0.4, 2, 4, 1),
        ]),
        # The shroud: a hood and a cloak, worn while it is still pretending to be a shape.
        ("cloak", "yoke", (0, -1.5, 0), (0, 0, 0), [
            ("cloth", -6.5, 0.0, -3.5, 13, 9, 7),           # over the shoulders
            ("cloth", -5.0, 8.0, -3.0, 10, 14, 6),          # falling away, narrower
            ("cloth", -3.5, 21.0, -2.5, 7, 10, 5),          # and trailing to nothing
        ]),
        ("hood", "neck2", (0, -4.0, 0), (0, 0, 0), [
            ("cloth", -4.0, -6.5, -3.5, 8, 9, 7),
        ]),
    ]

    # Arms: upper, fore, and a hand of four long fingers. They hang past the knees.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_upper", "yoke", (sx * 4.5, 0.5, 0.0), (0, 0, 0),
                  [("bone", -1.2, -1.2, -1.2, 2.4, 14, 2.4)]))
        p.append((side + "_fore", side + "_upper", (0, 12.8, 0), (0, 0, 0),
                  [("bone", -1.0, 0, -1.0, 2, 13, 2)]))
        p.append((side + "_hand", side + "_fore", (0, 13.0, 0), (0, 0, 0),
                  [("bone", -1.0, 0, -0.6, 2, 2, 1.2)]))
        for i in range(4):
            outer = i in (0, 3)
            a, b = (3.5, 3.0) if outer else (4.5, 4.0)
            p.append((f"{side}_finger{i}", side + "_hand", (-0.75 + i * 0.5, 2.0, 0.0), (0, 0, 0),
                      [("bone", -0.2, 0, -0.2, 0.4, a, 0.4)]))
            p.append((f"{side}_tip{i}", f"{side}_finger{i}", (0, a, 0), (0, 0, 0),
                      [("bone", -0.18, 0, -0.18, 0.36, b, 0.36)]))

    # Legs: thigh, shin, and a long flat foot. Most of its height is here.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_thigh", "hips", (sx * 2.0, 3.5, 0.0), (0, 0, 0),
                  [("bone", -1.3, 0, -1.3, 2.6, 15, 2.6)]))
        p.append((side + "_shin", side + "_thigh", (0, 15.0, 0), (0, 0, 0),
                  [("bone", -1.1, 0, -1.1, 2.2, 12, 2.2)]))
        p.append((side + "_foot", side + "_shin", (0, 12.0, 0), (0, 0, 0),
                  [("bone", -1.1, 0, -3.2, 2.2, 1.6, 5)]))
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

BONE = (228, 224, 214)     # old bone, not white: white looks like plastic
SHADOW = (168, 164, 156)
CLOTH = (13, 12, 14)
PIT = (6, 6, 8)            # the eyes, and the inside of the mouth


def paint_texture(boxes, placed):
    img = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    cloth_mask = np.zeros((TEX_H, TEX_W), dtype=bool)

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
                cx0, cy0, cx1, cy1 = side
                cloth_mask[cy0:cy1, cx0:cx1] = True
            # The shroud hangs in strands and ends in tatters.
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                for x in range(x0, x1):
                    cut = int(rng.integers(0, 5))
                    if cut:
                        img[y1 - cut:y1, x, 3] = 0
                for _ in range(max(1, (x1 - x0) // 2)):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0, max(y0 + 1, y1 - 3)))
                    img[fy:fy + 3, fx, :3] = (26, 24, 27)

        elif kind == "maw":
            for side in f.values():
                fill(side, PIT, 2)
            x0, y0, x1, y1 = f["front"]
            img[y1 - 1, x0:x1, :3] = (54, 10, 12)      # a little colour, far down it

        elif kind == "ribs":
            for side in f.values():
                fill(side, SHADOW, 5)
            # Ribs you can count, front and back, with the gaps between them dark.
            for side in ("front", "back"):
                x0, y0, x1, y1 = f[side]
                for k, y in enumerate(range(y0 + 1, y1 - 1)):
                    if k % 2 == 0:
                        img[y, x0:x1, :3] = np.clip(np.array(BONE) + rng.integers(-6, 7, size=(x1 - x0, 1)), 0, 255)
                        img[y, x0, :3] = SHADOW
                        img[y, x1 - 1, :3] = SHADOW
                    else:
                        img[y, x0 + 1:x1 - 1, :3] = (44, 42, 42)
                # A hollow down the middle, where the chest should be.
                cx = (x0 + x1) // 2
                img[y0 + 1:y1 - 1, cx, :3] = (34, 32, 33)
            for side in ("left", "right"):
                x0, y0, x1, y1 = f[side]
                for k, y in enumerate(range(y0 + 1, y1 - 1)):
                    img[y, x0:x1, :3] = BONE if k % 2 == 0 else (48, 46, 46)

        elif kind == "face":
            paint_face(img, f)

        else:  # bone
            for side in f.values():
                fill(side, BONE, 7)
            # Thin and dry: the edges darken, and the surface is not smooth.
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                if x1 - x0 > 1:
                    img[y0:y1, x0, :3] = SHADOW
                    img[y0:y1, x1 - 1, :3] = SHADOW
                for _ in range(max(1, (x1 - x0) * (y1 - y0) // 12)):
                    fx, fy = int(rng.integers(x0, x1)), int(rng.integers(y0, y1))
                    img[fy, fx, :3] = (176, 171, 162)

    # A faint sheen of the body, drawn full-bright over it. Without this it is invisible in
    # real darkness, and the whole point is that you can just make something out over the trees.
    lit = img.astype(np.float32)
    lit[:, :, :3] *= 0.13
    lit[cloth_mask] = 0                     # the shroud stays dark: it is meant to hide the shape
    glow = lit.astype(np.uint8)
    glow[:, :, 3] = np.where(img[:, :, 3] > 0, 255, 0)
    glow[cloth_mask] = 0

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_glow.png")


def paint_face(img, f):
    """
    A head with nothing on it. Smooth bone, and two black pits set too far apart. No nose, no
    mouth, no expression to read, which is the point: there is nothing there to appeal to.
    """
    for side in f.values():
        x0, y0, x1, y1 = side
        n = rng.integers(-5, 6, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(BONE) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    # The back and top of the skull are a little darker, so the face reads as the front.
    for side in ("back", "top"):
        x0, y0, x1, y1 = f[side]
        img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 26, 0, 255)

    x0, y0, x1, y1 = f["front"]
    w, h = x1 - x0, y1 - y0
    # Two pits, deep and a little too far apart, set high in a face with nothing else in it.
    for ex in (x0 + 1, x1 - 2):
        img[y0 + 1:y0 + 3, ex, :3] = PIT
    img[y0 + 1, x0 + 2, :3] = (28, 27, 29)                  # they run together across the bridge
    img[y0 + 3, x0 + 1, :3] = (74, 71, 70)                  # and weep down the cheek a little
    img[y0 + 3, x1 - 2, :3] = (96, 93, 92)

    # A heavy brow above them and a flat, featureless lower half.
    img[y0, x0:x1, :3] = (146, 142, 135)
    img[y1 - 1, x0 + 1:x1 - 1, :3] = (200, 196, 187)
    # The seam the mouth opens along, closed: barely a line.
    img[y0 + 4:y1, (x0 + x1) // 2, :3] = (170, 166, 158)

    # The jaw line, on the sides only: from the front it is a smooth, blank face.
    for side in ("left", "right"):
        sx0, sy0, sx1, sy1 = f[side]
        img[sy1 - 2, sx0:sx1, :3] = (166, 161, 152)


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

/** The Occupant's body. Too tall, too thin, and the colour of old bone. */
public final class OccupantGeometry {
\t/** The bones of the shroud it wears while it is still only a shape. */
\tpublic static final List<String> SHROUD = List.of(%s);
\t/** Height of the built body in model pixels (16 = one block). */
\tpublic static final float HEIGHT = %sf;

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
    height = NECK_TOP + 5.0  # the top of the skull
    lines = [HEADER % (", ".join('"%s"' % n for n in SHROUD), num(height).rstrip("f"))]
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
