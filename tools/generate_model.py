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
HIP = 26.0
SHOULDER = 46.0
NECK_TOP = 52.0
HEAD_TOP = 62.0
# Nothing is ever taken off. What it is wearing is most of what it is.
SHROUD = ()

# The cowl is built from many thin strands rather than a few plates, because a hood made of
# plates reads as a box on a head, and this has to read as something hanging.
_S = np.random.default_rng(31)


def _cowl_strands():
    """Strands hanging around the skull, longest at the back, shortest beside the face."""
    out = []
    for i in range(22):
        a = (i / 22.0) * 2.0 * np.pi
        # Leave the front open: the face has to be the only thing you can see in there.
        front = np.cos(a)
        if front < -0.62 and abs(np.sin(a)) < 0.55:
            continue
        r = 4.6 + 0.5 * float(_S.random())
        x = float(np.sin(a)) * r
        z = float(np.cos(a)) * r
        # Longer at the back and sides, so the face sits in a gap in the middle of it.
        length = 11.0 + 13.0 * max(0.0, (front + 0.6) / 1.6) + 5.0 * float(_S.random())
        w = 1.0 + 0.5 * float(_S.random())
        out.append(("strand", x - w / 2, -7.5, z - w / 2, w, length, w))
    return out


def _mantle_strands():
    """The same stuff, longer, lying over the shoulders and down the back."""
    out = []
    for i in range(16):
        a = (i / 16.0) * 2.0 * np.pi
        r = 5.2 + 1.2 * float(_S.random())
        x = float(np.sin(a)) * r
        z = float(np.cos(a)) * r
        length = 14.0 + 16.0 * float(_S.random())
        w = 1.2 + 0.7 * float(_S.random())
        out.append(("strand", x - w / 2, 0.0, z - w / 2, w, length, w))
    return out


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

        ("hips", None, (0, -HIP, 0), (0, 0, 0), [
            ("drape", -3.5, -1.0, -2.0, 7, 6, 4),
        ]),
        ("spine", "hips", (0, -1.0, 0), (0, 0, 0), [
            ("drape", -3.0, -20.0, -1.75, 6, 20, 3.5),
        ]),
        ("yoke", "spine", (0, -20.0, 0), (0, 0, 0), [
            ("drape", -5.5, -2.0, -2.5, 11, 4, 5),
        ]),

        # The robe: everything below the shoulders, hanging straight and going to pieces.
        ("robe", "yoke", (0, -1.0, 0), (0, 0, 0), [
            ("drape", -6.0, 0.0, -3.0, 12, 13, 6),
            ("drape", -5.0, 12.0, -2.75, 10, 14, 5.5),
            ("drape", -4.0, 25.0, -2.5, 8, 12, 5),
        ]),
        ("mantle", "yoke", (0, -1.5, 0), (0, 0, 0), _mantle_strands()),

        ("neck", "yoke", (0, -1.5, 0), (0, 0, 0), [
            ("drape", -1.75, -5.0, -1.75, 3.5, 5, 3.5),
        ]),

        # The mask. Broad, smooth, the colour of old ivory, and far too still.
        ("skull", "neck", (0, -4.5, 0), (0, 0, 0), [
            ("mask", -4.0, -8.5, -3.0, 8, 9, 5.5),
        ]),
        # What is under it: a throat that goes down much further than a throat should.
        ("maw", "skull", (0, -1.0, -2.0), (0, 0, 0), [
            ("maw", -1.5, 0.0, -1.0, 3, 13, 3),
        ]),
        # The cowl, hanging off the back and sides of the mask.
        ("cowl", "skull", (0, -1.0, 0), (0, 0, 0), _cowl_strands()),
    ]

    # Arms: thin, under the robe, ending in pale hands with far too much finger.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_upper", "yoke", (sx * 4.8, 0.5, 0.0), (0, 0, 0),
                  [("drape", -1.4, -1.4, -1.4, 2.8, 15, 2.8)]))
        p.append((side + "_fore", side + "_upper", (0, 14.0, 0), (0, 0, 0),
                  [("drape", -1.1, 0, -1.1, 2.2, 14, 2.2)]))
        p.append((side + "_hand", side + "_fore", (0, 14.0, 0), (0, 0, 0),
                  [("pale", -1.1, 0, -0.7, 2.2, 2.5, 1.4)]))
        for i in range(4):
            outer = i in (0, 3)
            a, b = (3.5, 3.0) if outer else (4.5, 4.0)
            p.append((f"{side}_finger{i}", side + "_hand", (-0.8 + i * 0.55, 2.5, 0.0), (0, 0, 0),
                      [("pale", -0.22, 0, -0.22, 0.44, a, 0.44)]))
            p.append((f"{side}_tip{i}", f"{side}_finger{i}", (0, a, 0), (0, 0, 0),
                      [("pale", -0.2, 0, -0.2, 0.4, b, 0.4)]))

    # Legs, mostly hidden under the robe.
    for side, sx in (("right", -1), ("left", 1)):
        p.append((side + "_thigh", "hips", (sx * 2.2, 4.0, 0.0), (0, 0, 0),
                  [("drape", -1.4, 0, -1.4, 2.8, 14, 2.8)]))
        p.append((side + "_shin", side + "_thigh", (0, 14.0, 0), (0, 0, 0),
                  [("drape", -1.2, 0, -1.2, 2.4, 12, 2.4)]))
        p.append((side + "_foot", side + "_shin", (0, 12.0, 0), (0, 0, 0),
                  [("pale", -1.2, 0, -3.0, 2.4, 1.6, 4.5)]))
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

IVORY = (214, 202, 182)    # the mask: old ivory, warm, not white
IVORY_LOW = (172, 160, 144)
DRAPE = (31, 25, 23)       # everything it is wearing: a brown so dark it reads as black
DRAPE_LIT = (52, 43, 39)
PIT = (6, 5, 6)            # the eye holes, and the back of the throat
MEAT = (96, 44, 38)        # inside the mouth, and only there


def paint_texture(boxes, placed):
    img = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    dark_mask = np.zeros((TEX_H, TEX_W), dtype=bool)

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

        if kind in ("drape", "strand"):
            for side in f.values():
                fill(side, DRAPE, 5)
                cx0, cy0, cx1, cy1 = side
                dark_mask[cy0:cy1, cx0:cx1] = True
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                # Nothing it wears has a hem; it all just stops.
                for x in range(x0, x1):
                    cut = int(rng.integers(0, 4))
                    if cut:
                        img[y1 - cut:y1, x, 3] = 0
                # A little length in the folds, so it does not read as flat black.
                for _ in range(max(1, (x1 - x0))):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0, max(y0 + 1, y1 - 4)))
                    img[fy:fy + 4, fx, :3] = DRAPE_LIT

        elif kind == "maw":
            for side in f.values():
                fill(side, PIT, 2)
            # The throat: red at the rim where it tears into the mask, black all the way down.
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                img[y0, x0:x1, :3] = MEAT
                img[y0 + 1, x0:x1, :3] = (54, 24, 22)
                for _ in range(max(1, (x1 - x0) // 2)):
                    fx = int(rng.integers(x0, x1))
                    img[y0 + 2:y0 + 4, fx, :3] = (44, 20, 19)
                # Threads of something pale still bridging the gap.
                for _ in range(2):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0 + 2, max(y0 + 3, y1 - 1)))
                    img[fy, fx, :3] = (150, 136, 120)

        elif kind == "pale":
            for side in f.values():
                fill(side, IVORY_LOW, 7)
            x0, y0, x1, y1 = f["front"]
            img[max(y0, y1 - 3):y1, x0:x1, :3] = (58, 48, 44)   # dark at the fingertips

        elif kind == "mask":
            paint_mask(img, f)

        else:
            for side in f.values():
                fill(side, IVORY, 6)

    # A faint sheen, drawn full-bright, so the mask is the one thing still visible in the dark.
    lit = img.astype(np.float32)
    lit[:, :, :3] *= 0.17
    lit[dark_mask] = 0
    glow = lit.astype(np.uint8)
    glow[:, :, 3] = np.where(img[:, :, 3] > 0, 255, 0)
    glow[dark_mask] = 0

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_glow.png")


def paint_mask(img, f):
    """
    A smooth ivory face with two holes in it. The holes are the whole design: they are far too
    large, perfectly round-edged, and there is nothing behind them, so there is no way to tell
    where it is looking or whether it is looking at all.
    """
    for side in f.values():
        x0, y0, x1, y1 = side
        n = rng.integers(-5, 6, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(IVORY) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    # The sides and back curve away from the light, and the cowl lies over them.
    for side in ("back", "left", "right", "top"):
        x0, y0, x1, y1 = f[side]
        img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 30, 0, 255)

    x0, y0, x1, y1 = f["front"]
    w, h = x1 - x0, y1 - y0

    # Two holes, set wide in a face that is otherwise entirely smooth. They are not eyes: there
    # is nothing behind them, which is why you cannot tell where it is looking.
    ex0, ex1 = x0 + 1, x1 - 3
    ey = y0 + 3
    for ex in (ex0, ex1):
        img[ey:ey + 2, ex:ex + 2, :3] = PIT
        img[ey + 2, ex:ex + 2, :3] = (162, 150, 134)     # the cheekbone under them
    img[ey:ey + 2, x0 + w // 2 - 1:x0 + w // 2 + 1, :3] = (224, 213, 194)

    # The brow: one flat plane, no expression in it at all.
    img[y0, x0:x1, :3] = (186, 174, 156)
    # Where the mask ends and the mouth begins: it does not end cleanly.
    img[y1 - 1, x0 + 1:x1 - 1, :3] = (120, 96, 86)
    img[y1 - 2, x0 + 2:x1 - 2, :3] = (146, 124, 110)



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
