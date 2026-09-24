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
HIP = 30.0        # most of it is leg
SHOULDER = 47.0
NECK_TOP = 56.0
HEAD_TOP = 63.0
# It wears nothing, so there is nothing to take off.
SHROUD = ()


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

        # Narrow hips, a thin spine, and a ribcage you can count from across a field.
        ("hips", None, (0, -HIP, 0), (0, 0, 0), [
            ("bone", -2.5, -1.0, -1.5, 5, 5, 3),
        ]),
        ("spine", "hips", (0, -1.0, 0), (0, 0, 0), [
            ("bone", -1.5, -16.0, -1.0, 3, 16, 2),
        ]),
        ("ribs", "spine", (0, -14.0, 0), (0, 0, 0), [
            ("ribs", -3.5, -1.0, -2.25, 7, 13, 4.5),
        ]),
        ("yoke", "spine", (0, -16.0, 0), (0, 0, 0), [
            ("bone", -5.0, -1.5, -1.25, 10, 2.5, 2.5),
        ]),

        # A neck far too long for a person, in two pieces so it can crane.
        ("neck", "yoke", (0, -1.0, 0), (0, 0, 0), [
            ("bone", -1.0, -4.5, -1.0, 2, 5, 2),
        ]),
        ("neck2", "neck", (0, -4.5, 0), (0, 0, 0), [
            ("bone", -1.0, -4.5, -1.0, 2, 5, 2),
        ]),

        # A small, smooth, blank head. Two eyes, and nothing else at all.
        ("skull", "neck2", (0, -4.5, 0), (0, 0, 0), [
            ("face", -3.0, -6.5, -2.5, 6, 6.5, 5),
        ]),
    ]

    # Arms: long enough that the hands hang level with the knees.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_upper", "yoke", (sx * 4.2, 0.5, 0.0), (0, 0, 0),
                  [("bone", -1.1, -1.1, -1.1, 2.2, 15, 2.2)]))
        p.append((side + "_fore", side + "_upper", (0, 13.9, 0), (0, 0, 0),
                  [("bone", -0.9, 0, -0.9, 1.8, 15, 1.8)]))
        p.append((side + "_hand", side + "_fore", (0, 15.0, 0), (0, 0, 0),
                  [("bone", -1.0, 0, -0.6, 2, 2, 1.2)]))
        for i in range(4):
            outer = i in (0, 3)
            a, b = (3.5, 3.0) if outer else (4.5, 4.0)
            p.append((f"{side}_finger{i}", side + "_hand", (-0.75 + i * 0.5, 2.0, 0.0), (0, 0, 0),
                      [("bone", -0.2, 0, -0.2, 0.4, a, 0.4)]))
            p.append((f"{side}_tip{i}", f"{side}_finger{i}", (0, a, 0), (0, 0, 0),
                      [("bone", -0.18, 0, -0.18, 0.36, b, 0.36)]))

    # Legs: thigh, shin, a long flat foot. Half its height is here.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_thigh", "hips", (sx * 2.0, 3.5, 0.0), (0, 0, 0),
                  [("bone", -1.3, 0, -1.3, 2.6, 17, 2.6)]))
        p.append((side + "_shin", side + "_thigh", (0, 17.0, 0), (0, 0, 0),
                  [("bone", -1.1, 0, -1.1, 2.2, 13, 2.2)]))
        p.append((side + "_foot", side + "_shin", (0, 13.0, 0), (0, 0, 0),
                  [("bone", -1.1, 0, -3.4, 2.2, 1.6, 5.2)]))
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

BONE = (233, 230, 222)     # bleached, not glossy: pure white plastic reads as a toy
SHADOW = (176, 172, 164)
CLOTH = (13, 12, 14)
PIT = (5, 5, 7)            # the eye sockets, and the back of the throat
GUM = (74, 12, 14)         # the only colour anywhere on it


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

        if kind in ("cloth", "strand"):
            for side in f.values():
                fill(side, CLOTH, 3)
                cx0, cy0, cx1, cy1 = side
                cloth_mask[cy0:cy1, cx0:cx1] = True
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                # Everything it wears is coming apart along its bottom edge.
                for x in range(x0, x1):
                    cut = int(rng.integers(0, 4 if kind == "cloth" else 3))
                    if cut:
                        img[y1 - cut:y1, x, 3] = 0
                for _ in range(max(1, (x1 - x0) // 2)):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0, max(y0 + 1, y1 - 3)))
                    img[fy:fy + 3, fx, :3] = (27, 25, 28)

        elif kind == "ribs":
            for side in f.values():
                fill(side, SHADOW, 5)
            # Ribs, front and back, with the gaps between them almost black.
            for side in ("front", "back"):
                x0, y0, x1, y1 = f[side]
                for k, y in enumerate(range(y0 + 1, y1 - 1)):
                    if k % 2 == 0:
                        img[y, x0:x1, :3] = np.clip(
                            np.array(BONE) + rng.integers(-5, 6, size=(x1 - x0, 1)), 0, 255)
                        img[y, x0, :3] = SHADOW
                        img[y, x1 - 1, :3] = SHADOW
                    else:
                        img[y, x0 + 1:x1 - 1, :3] = (126, 123, 119)
                cx = (x0 + x1) // 2
                img[y0 + 1:y1 - 1, cx, :3] = (150, 147, 142)   # the hollow down the middle
            for side in ("left", "right"):
                x0, y0, x1, y1 = f[side]
                for k, y in enumerate(range(y0 + 1, y1 - 1)):
                    img[y, x0:x1, :3] = BONE if k % 2 == 0 else (138, 135, 130)

        elif kind == "face":
            paint_face(img, f)

        else:  # bone
            for side in f.values():
                fill(side, BONE, 6)
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                if x1 - x0 > 1:
                    img[y0:y1, x0, :3] = SHADOW
                    img[y0:y1, x1 - 1, :3] = SHADOW
                for _ in range(max(1, (x1 - x0) * (y1 - y0) // 14)):
                    fx, fy = int(rng.integers(x0, x1)), int(rng.integers(y0, y1))
                    img[fy, fx, :3] = (200, 196, 189)

    # A faint sheen of the body, drawn full-bright over it. Without this it is invisible in
    # real darkness, and the whole story depends on you being able to almost see it.
    lit = img.astype(np.float32)
    lit[:, :, :3] *= 0.15
    lit[cloth_mask] = 0                     # what it wears stays dark: that is what it is for
    glow = lit.astype(np.uint8)
    glow[:, :, 3] = np.where(img[:, :, 3] > 0, 255, 0)
    glow[cloth_mask] = 0

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_glow.png")


def paint_face(img, f):
    """
    Two eyes in a blank white face. No mouth, no nose, no brow, no expression: there is nothing
    in it to appeal to, and nothing to tell you what it is about to do.
    """
    for side in f.values():
        x0, y0, x1, y1 = side
        n = rng.integers(-4, 5, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(BONE) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    # The back and sides of the head are a shade lower, so the face reads as the front.
    for side in ("back", "left", "right"):
        x0, y0, x1, y1 = f[side]
        img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 18, 0, 255)

    x0, y0, x1, y1 = f["front"]
    w = x1 - x0
    # Two small black eyes, set high and close together. They are the only marks on it.
    ey = y0 + 2
    mid = x0 + w // 2
    img[ey:ey + 2, mid - 2, :3] = PIT
    img[ey:ey + 2, mid + 1, :3] = PIT


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
    height = HEAD_TOP
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
