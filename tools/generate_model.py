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

What it is: Father Fester. A long pale face that is mostly mouth, framed by long thin hair the
colour of dried blood, on a body far too tall and as thin as paper, carried on ten long pale
legs. The legs are not walked on. Each one reaches out to the nearest thing it can push against,
the ground, a wall, a tree, a ceiling, and the body is shoved along between them. Where each leg
is planted is worked out in game (OccupantRenderer / LegGait) and the joints are solved to reach
it (OccupantModel), so this only builds the parts at rest, hanging straight down.

Units are pixels; the ground is at y = 24 and up is -y (the same convention as vanilla models).
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/occupant/textures/entity"
JAVA = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java"
TEX_W = TEX_H = 256

rng = np.random.default_rng(909)

SIDES = ("front", "back", "left", "right")

# Minecraft draws model y = 24 at the entity's feet (up is -y). Everything was once built as
# though the ground were y = 0, which left the whole body floating a block in the air in game,
# so the hips are now placed wherever puts the soles exactly on y = 24, and check_model.py
# fails the build if they ever leave it again.
GROUND = 24.0
# How high the hips stand when it is upright. The legs are far longer than this: they are
# planted out to the sides, never straight down.
HIPS_HEIGHT = 44.0
HIPS_Y = GROUND - HIPS_HEIGHT
LEGS = 10
# Each leg: thigh, shin and a short pale point. Lengths differ a little from leg to leg.
LEG_UPPER = 28.0
LEG_LOWER = 30.0
LEG_CLAW = 6.0
# From the bottom of the eyes to the middle of the face (eyes to chin), which is what it tilts about.
FACE_MID = 2.3
# How far up the trunk the highest legs leave it, above the hips.
LEG_RISE = 26.0
# Nothing is ever taken off. What it is wearing is most of what it is.
SHROUD = ()

# The cowl is built from many thin strands rather than a few plates, because a hood made of
# plates reads as a box on a head, and this has to read as something hanging.
_S = np.random.default_rng(31)

# Strands are placed off the grid the rest of the body is built on. Two faces from different
# bones that land in exactly the same plane flicker against each other in game, and with ~40
# strands draped over a robe that would otherwise happen constantly. Offsetting every strand by
# a fraction of a pixel makes it impossible by construction rather than by luck.
# Every strand gets its own fractional position and thickness, stepped by irrational ratios so
# the sequence never repeats. No two strands can share a face plane, and none of them can land
# on the round coordinates the robe and the arms are built on.
def _strand(seq, x, z, y, length, thin=False):
    fx = 0.11 + 0.78 * ((seq * 0.6180339887) % 1.0)
    fz = 0.07 + 0.78 * ((seq * 0.4142135624) % 1.0)
    span = (0.55, 0.6) if thin else (0.85, 0.75)
    w = span[0] + span[1] * ((seq * 0.2360679775) % 1.0)
    kind = "hair" if thin else "strand"
    return (kind, np.floor(x - w / 2) + fx, y, np.floor(z - w / 2) + fz, w, length, w)


def _cowl_strands():
    """
    Long hair: thin strands falling from the crown down both sides of the face and down the
    back, leaving the front open so the face is the one thing you can see in it.
    """
    out = []
    for i in range(30):
        a = (i / 30.0) * 2.0 * np.pi
        front = np.cos(a)
        if front < -0.5 and abs(np.sin(a)) < 0.62:
            continue                                   # the face
        x = float(np.sin(a)) * (4.25 + 0.5 * float(_S.random()))
        z = float(np.cos(a)) * (3.55 + 0.45 * float(_S.random()))
        if front < 0.0:
            # Beside the face the hair has to hang clear of the cheeks, not through them.
            x = float(np.sign(np.sin(a))) * max(abs(x), 4.05)
        # Long enough at the sides to frame the face past the chin; longest down the back.
        length = 20.0 + 9.0 * max(0.0, (front + 1.0) / 2.0) + 6.0 * float(_S.random())
        out.append(_strand(i + 1, x, z, -6.9, length, thin=True))
    return out


def _mantle_strands():
    """The same stuff, longer, lying over the shoulders and down the back."""
    out = []
    for i in range(16):
        a = ((i + 0.37) / 16.0) * 2.0 * np.pi   # out of phase with the cowl above it
        x = float(np.sin(a)) * (4.35 + 0.5 * float(_S.random()))
        z = float(np.cos(a)) * (2.25 + 0.4 * float(_S.random()))
        out.append(_strand(i + 41, x, z, 0.11, 16.0 + 18.0 * float(_S.random())))
    return out


def leg_layout():
    """
    (angle, rise, thigh, shin, point) for every leg. They do not all leave the body at the hips
    like a spider's: they come out all the way up the trunk, like far too many arms, and the
    higher a leg starts the longer it is. Angles go all the way round, but never evenly.
    """
    out = []
    for i in range(LEGS):
        angle = 2.0 * np.pi * (i + 0.5) / LEGS + 0.22 * np.sin(i * 2.399)
        rise = round(float(LEG_RISE * ((i * 0.6180339887 + 0.05) % 1.0)), 2)
        upper = LEG_UPPER + 0.4 * rise + 3.0 * (((i * 0.7548776662) % 1.0) - 0.5)
        lower = LEG_LOWER + 0.45 * rise + 3.0 * (((i * 0.5698402910) % 1.0) - 0.5)
        out.append((float(angle), rise, round(float(upper), 2), round(float(lower), 2), LEG_CLAW))
    return out


def leg_root_height(rise):
    """Height of a leg's root above the ground, standing upright, in model pixels."""
    return HIPS_HEIGHT + 1.0 - 2.0 + rise


def parts():
    """Every bone, in order: name, parent, pivot, rotation, [(kind, x, y, z, w, h, d)]."""
    p = [
        # HumanoidModel looks these up by name. They draw nothing; the head anchor is only used
        # to find out where the viewer is.
        ("head", None, (0, HIPS_Y - 44.0, 0), (0, 0, 0), []),
        ("hat", "head", (0, 0, 0), (0, 0, 0), []),
        ("body", None, (0, 0, 0), (0, 0, 0), []),
        ("right_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("left_arm", None, (0, 0, 0), (0, 0, 0), []),
        ("right_leg", None, (0, 0, 0), (0, 0, 0), []),
        ("left_leg", None, (0, 0, 0), (0, 0, 0), []),

        ("hips", None, (0, HIPS_Y, 0), (0, 0, 0), [
            ("drape", -2.5, -1.0, -1.0, 5, 6, 2),
        ]),
        # The body: very long and as thin as paper, mostly hidden under the hair.
        ("spine", "hips", (0, -1.0, 0), (0, 0, 0), [
            ("drape", -2.25, -30.0, -0.8, 4.5, 30, 1.6),
        ]),
        ("yoke", "spine", (0, -30.0, 0), (0, 0, 0), [
            ("drape", -3.5, -2.0, -1.2, 7, 3, 2.4),
        ]),
        ("mantle", "yoke", (0, -1.5, 0), (0, 0, 0), _mantle_strands()),

        # A long neck, set back and hidden in the hair, so the face seems to hang there.
        ("neck", "yoke", (0, -1.5, 0.4), (0, 0, 0), [
            ("drape", -1.2, -11.0, -1.2, 2.4, 11, 2.4),
        ]),

        # The top of the face: a broad, rounded brow and two small round holes set close
        # together over a narrow bridge.
        # The skull turns about the middle of the whole face, eyes to chin, not about the top of
        # the neck: turned about the neck, a tilt swung the long jaw out sideways like a pendulum.
        ("skull", "neck", (0, -11.0 + FACE_MID, -0.4), (0, 0, 0), [
            ("face", -3.5, -7.0 - FACE_MID, -3.0, 7, 7, 6),
            ("crown", -2.75, -8.1 - FACE_MID, -2.4, 5.5, 1.1, 4.8),       # rounds off the top of it
        ]),
        # The rest of the face is the mouth. The skin carries on down both sides of it, much
        # too far, to a small pointed chin; between them it is open, with a row of small teeth
        # along the top and something red at the bottom.
        ("jaw", "skull", (0, -FACE_MID, 0), (0, 0, 0), [
            ("cheek", 1.75, 0.0, -2.95, 1.7, 6.0, 3.35),       # cheeks, either side
            ("cheek", -3.45, 0.0, -2.95, 1.7, 6.0, 3.35),
            ("cheek", 1.35, 6.0, -2.8, 1.4, 4.6, 3.0),         # narrowing towards the chin
            ("cheek", -2.75, 6.0, -2.8, 1.4, 4.6, 3.0),
            ("chin", -1.85, 9.55, -2.6, 3.7, 2.1, 2.55),
            ("mouth", -1.74, 0.02, -2.0, 3.48, 9.56, 1.9),     # set back: the inside of it
            # A row of small, separate teeth along the top, each its own tiny box so the gaps
            # between them are real; one painted gap on a single box read as a grey block.
            ("tooth", -1.13, 0.05, -2.72, 0.42, 0.95, 0.66),
            ("tooth", -0.55, 0.05, -2.70, 0.43, 0.78, 0.64),
            ("tooth", 0.04, 0.05, -2.71, 0.41, 0.92, 0.65),
            ("tooth", 0.62, 0.05, -2.69, 0.44, 0.74, 0.63),
            # The skin folds in at the corners, top and bottom, so the opening is long and
            # rounded rather than a slot cut out of the face.
            ("lip", -1.74, 0.0, -2.86, 0.53, 1.55, 0.79),
            ("lip", 1.21, 0.0, -2.86, 0.53, 1.55, 0.79),
            ("lip", -1.33, 8.15, -2.84, 0.48, 1.42, 0.77),
            ("lip", 0.85, 8.15, -2.84, 0.48, 1.42, 0.77),
        ]),
        # No loose hairs standing up off the crown: in blocks, anything sticking up off a head
        # reads as horns or antennae, however short it is.
        ("hair", "skull", (0, -FACE_MID, 0), (0, 0, 0), _cowl_strands()),
    ]

    # Ten legs, coming out all round the bottom of the body. Each is three bones hanging straight
    # down at rest; in game every one of them is aimed at somewhere real to push against.
    for i, (angle, rise, upper, lower, claw) in enumerate(leg_layout()):
        rx = float(np.sin(angle)) * 2.3
        rz = float(np.cos(angle)) * 0.75
        w1 = 1.45 + 0.3 * ((i * 0.6180339887) % 1.0)
        w2 = 1.1 + 0.25 * ((i * 0.4142135624) % 1.0)
        w3 = 0.7 + 0.2 * ((i * 0.2360679775) % 1.0)
        p.append((f"leg{i}_upper", "spine", (rx, 2.0 - rise, rz), (0, 0, 0),
                  [("limb", -w1 / 2, -0.6, -w1 / 2, w1, upper + 0.6, w1)]))
        p.append((f"leg{i}_lower", f"leg{i}_upper", (0, upper, 0), (0, 0, 0),
                  [("limb", -w2 / 2, -0.5, -w2 / 2, w2, lower + 0.5, w2)]))
        p.append((f"leg{i}_claw", f"leg{i}_lower", (0, lower, 0), (0, 0, 0),
                  [("claw", -w3 / 2, -0.3, -w3 / 2, w3, claw + 0.3, w3)]))
    return p



def _world_origins(ps):
    """Where each bone sits once the whole tree is assembled."""
    pivot = {n: piv for n, _p, piv, _r, _b in ps}
    parent = {n: p for n, p, _piv, _r, _b in ps}
    out = {}

    def walk(name):
        if name in out:
            return out[name]
        px, py, pz = pivot[name]
        if parent[name] is not None:
            qx, qy, qz = walk(parent[name])
            px, py, pz = px + qx, py + qy, pz + qz
        out[name] = (px, py, pz)
        return out[name]

    for n, _p, _piv, _r, _b in ps:
        walk(n)
    return out


def avoid_coplanar(ps, tries=400):
    """
    Moves each strand until none of its six faces lies in the same plane as a face of any box
    it overlaps. Entity models are drawn without back-face culling, so two coplanar overlapping
    faces have no defined order: the surface flickers between them as the camera moves. With
    sixty-odd strands of hair laid over a robe that happens by luck rather than design, so it is
    removed here, on every regeneration, instead of relying on the shape staying as it is.

    Positions are compared after rounding to the precision the Java is written with, so what is
    checked here is exactly what the game will build.
    """
    origins = _world_origins(ps)

    def world(name, box):
        ox, oy, oz = origins[name]
        _k, x, y, z, w, h, d = box
        lo = (round(x, 2) + ox, round(y, 2) + oy, round(z, 2) + oz)
        return lo, (lo[0] + round(w, 2), lo[1] + round(h, 2), lo[2] + round(d, 2))

    def clashes(name, i, box):
        lo, hi = world(name, box)
        for other_name, _p, _piv, _r, others in ps:
            for j, other in enumerate(others):
                if other_name == name and j == i:
                    continue
                olo, ohi = world(other_name, other)
                for axis in range(3):
                    u, v = (axis + 1) % 3, (axis + 2) % 3
                    if min(hi[u], ohi[u]) - max(lo[u], olo[u]) <= 0.02:
                        continue
                    if min(hi[v], ohi[v]) - max(lo[v], olo[v]) <= 0.02:
                        continue
                    for a_ in (lo[axis], hi[axis]):
                        for b_ in (olo[axis], ohi[axis]):
                            if abs(a_ - b_) < 0.03:
                                return True
        return False

    moved = 0
    for name, _p, _piv, _r, boxes in ps:
        for i, box in enumerate(boxes):
            if box[0] not in ("strand", "hair"):
                continue
            k = 0
            while clashes(name, i, box) and k < tries:
                # A small step in a direction that never repeats, so it cannot oscillate
                # between two bad positions.
                k += 1
                dx = 0.09 * (((k * 0.6180339887) % 1.0) - 0.5)
                dz = 0.09 * (((k * 0.7548776662) % 1.0) - 0.5)
                dy = 0.09 * (((k * 0.5698402910) % 1.0) - 0.5)
                kind, x, y, z, w, h, d = box
                box = (kind, x + dx, y + dy, z + dz, w, h, d)
                boxes[i] = box
                moved += 1
            if k >= tries:
                raise SystemExit("could not place a strand of %s clear of everything else" % name)
    if moved:
        print("moved strands %d times to keep their faces out of each other's planes" % moved)
    return ps


# --------------------------------------------------------------------------- texture packing

def _texels(v):
    """
    Texels a box dimension needs. Rounded UP, never below one: the fingers are thinner than a
    pixel, and rounding those to zero made the packer hand out slots smaller than the faces
    that go in them, so neighbouring parts ended up sharing texture and wearing each other's
    pixels.
    """
    return max(1, int(np.ceil(v - 1e-6)))


def unfolded(w, h, d):
    """Size of a cube's unwrapped texture region."""
    return 2 * _texels(d) + 2 * _texels(w), _texels(d) + _texels(h)


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
    w, h, d = _texels(w), _texels(h), _texels(d)
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# --------------------------------------------------------------------------- painting

SKIN = (198, 180, 168)      # pale, faintly pink, like something kept out of the light
SKIN_HI = (219, 205, 193)
SKIN_LO = (156, 134, 124)
PIT = (8, 5, 5)             # the eye holes, and the inside of the mouth
RED = (112, 40, 36)         # the bottom of the mouth, and only there
TOOTH = (206, 193, 176)
HAIR = (58, 32, 27)         # long, thin, the brown of old dried blood
HAIR_LIT = (90, 54, 45)
HAIR_DEEP = (33, 19, 17)
DRAPE = (40, 24, 21)        # the robe: the same colour as the hair, so the two run together
LIMB = (170, 154, 144)      # the legs
# How strongly the face shows through in the dark, as the glow layer's opacity (0-255).
SHEEN = 56


def paint_texture(boxes, placed):
    img = np.zeros((TEX_H, TEX_W, 4), dtype=np.uint8)
    dark_mask = np.zeros((TEX_H, TEX_W), dtype=bool)
    limb_mask = np.zeros((TEX_H, TEX_W), dtype=bool)

    def fill(region, rgb, jitter=6):
        x0, y0, x1, y1 = region
        if x1 <= x0 or y1 <= y0:
            return
        n = rng.integers(-jitter, jitter + 1, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(rgb) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255

    def mark_dark(f):
        for side in f.values():
            cx0, cy0, cx1, cy1 = side
            dark_mask[cy0:cy1, cx0:cx1] = True

    def skin(f, shade_sides=24):
        """Pale skin, blotched, darker where it turns away from the light."""
        for side in f.values():
            fill(side, SKIN, 6)
        for name in ("left", "right", "back"):
            x0, y0, x1, y1 = f[name]
            img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - shade_sides, 0, 255)
        for name in SIDES:
            x0, y0, x1, y1 = f[name]
            for _ in range(max(1, (x1 - x0) * (y1 - y0) // 10)):
                bx, by = int(rng.integers(x0, x1)), int(rng.integers(y0, y1))
                img[by, bx, :3] = (184, 158, 150)                 # faint blotches

    for i, (owner, kind, _x, _y, _z, w, h, d) in enumerate(boxes):
        u, v = placed[i]
        f = faces_of(u, v, w, h, d)

        if kind in ("drape", "strand", "hair"):
            base = DRAPE if kind == "drape" else HAIR
            for side in f.values():
                fill(side, base, 5)
            mark_dark(f)
            for side in SIDES:
                x0, y0, x1, y1 = f[side]
                for x in range(x0, x1):
                    cut = int(rng.integers(0, 4))
                    if cut:
                        img[y1 - cut:y1, x, 3] = 0                # it frays out, never a hem
                for _ in range(max(1, (x1 - x0) * 2)):
                    fx = int(rng.integers(x0, x1))
                    fy = int(rng.integers(y0, max(y0 + 1, y1 - 3)))
                    img[fy:fy + 3, fx, :3] = HAIR_LIT if rng.random() < 0.5 else HAIR_DEEP

        elif kind == "face":
            paint_face(img, f)

        elif kind == "crown":
            skin(f, shade_sides=18)
            x0, y0, x1, y1 = f["top"]
            img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 14, 0, 255)

        elif kind == "cheek":
            skin(f)
            # The edge that faces into the mouth is in its shadow.
            for name in ("left", "right"):
                x0, y0, x1, y1 = f[name]
                img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 40, 0, 255)
            x0, y0, x1, y1 = f["front"]
            img[y0:y1, x0, :3] = SKIN_LO
            img[y0:y1, x1 - 1, :3] = SKIN_LO

        elif kind == "chin":
            skin(f)
            x0, y0, x1, y1 = f["front"]
            img[y0, x0:x1, :3] = RED                              # the lower lip, wet
            x0, y0, x1, y1 = f["top"]
            img[y0:y1, x0:x1, :3] = RED

        elif kind == "mouth":
            for side in f.values():
                fill(side, PIT, 2)
            x0, y0, x1, y1 = f["front"]
            h_ = y1 - y0
            # Black all the way in, deepening slowly to a wet dark red at the bottom.
            ramp = [(26, 8, 8), (52, 14, 13), (78, 22, 20), (100, 32, 29), (112, 40, 36)]
            for k, colour in enumerate(ramp):
                row = y1 - len(ramp) + k
                if row >= y0:
                    img[row, x0:x1, :3] = colour
            # A little darker down the middle, so it reads as a hollow and not a panel.
            mid = x0 + (x1 - x0) // 2
            img[y1 - 3:y1, mid - 1:mid + 1, :3] = np.clip(img[y1 - 3:y1, mid - 1:mid + 1, :3].astype(int) - 18, 0, 255)

        elif kind == "tooth":
            for side in f.values():
                fill(side, TOOTH, 5)
            x0, y0, x1, y1 = f["front"]
            img[y1 - 1, x0:x1, :3] = (176, 160, 140)               # the worn tip

        elif kind == "lip":
            skin(f, shade_sides=30)
            x0, y0, x1, y1 = f["front"]
            img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 12, 0, 255)

        elif kind in ("limb", "claw"):
            # Pale like the face but greyer and dirtier, darker towards each joint.
            base = LIMB if kind == "limb" else (88, 70, 64)
            for side in f.values():
                fill(side, base, 7)
            for name in SIDES:
                x0, y0, x1, y1 = f[name]
                hh = y1 - y0
                for k in range(min(3, hh)):
                    shade = 30 - 10 * k
                    for row in (y0 + k, y1 - 1 - k):
                        img[row, x0:x1, :3] = np.clip(img[row, x0:x1, :3].astype(int) - shade, 0, 255)
                for _ in range(max(1, hh // 4)):
                    by = int(rng.integers(y0, y1))
                    img[by, x0:x1, :3] = np.clip(img[by, x0:x1, :3].astype(int) - 18, 0, 255)
            for side in f.values():
                x0, y0, x1, y1 = side
                limb_mask[y0:y1, x0:x1] = True

        elif kind == "pale":
            for side in f.values():
                fill(side, SKIN_LO, 7)
            x0, y0, x1, y1 = f["front"]
            img[max(y0, y1 - 3):y1, x0:x1, :3] = (70, 48, 44)     # dark at the fingertips

        else:
            for side in f.values():
                fill(side, SKIN, 6)

    # A faint sheen, drawn full-bright, so the face is the one thing still visible in the dark.
    # The game draws this layer BLENDED over the body, not added to it: an opaque pixel here
    # replaces the lit face underneath. So the sheen is the face's own colour at low opacity.
    # (It used to be a dimmed copy at full opacity, which painted the face dark grey in daylight.)
    glow = img.copy()
    glow[:, :, 3] = np.where(img[:, :, 3] > 0, SHEEN, 0)
    glow[limb_mask, 3] = SHEEN // 2                      # the legs, fainter than the face
    glow[dark_mask] = 0

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_glow.png")


def paint_face(img, f):
    """
    The top of the face: a broad pale brow, two small round black holes set close over a
    narrow bridge, and nothing else. The rest of the face is the mouth, built separately.
    """
    for side in f.values():
        x0, y0, x1, y1 = side
        n = rng.integers(-5, 6, size=(y1 - y0, x1 - x0, 1))
        img[y0:y1, x0:x1, :3] = np.clip(np.array(SKIN) + n, 0, 255)
        img[y0:y1, x0:x1, 3] = 255
    for side in ("back", "left", "right", "top"):
        x0, y0, x1, y1 = f[side]
        img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3].astype(int) - 24, 0, 255)

    x0, y0, x1, y1 = f["front"]
    w = x1 - x0
    # The brow catches the most light.
    img[y0:y0 + 2, x0 + 1:x1 - 1, :3] = SKIN_HI
    # Two small round holes, close together, low on the brow.
    ey = y0 + 3
    for ex in (x0 + 1, x0 + w - 3):
        img[ey:ey + 2, ex:ex + 2, :3] = PIT
        # A ring of shadow, so the hole reads round rather than square.
        img[ey - 1, ex:ex + 2, :3] = SKIN_LO
        img[ey + 2, ex:ex + 2, :3] = SKIN_LO
        img[ey:ey + 2, ex - 1 if ex > x0 else ex, :3] = np.minimum(
            img[ey:ey + 2, ex - 1 if ex > x0 else ex, :3], np.array(SKIN_LO))
    # The narrow bridge between them, and the shadow under it where the mouth begins.
    img[ey:ey + 3, x0 + w // 2, :3] = SKIN_HI
    img[y1 - 1, x0 + 2:x1 - 2, :3] = SKIN_LO



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

/** The Occupant's body: a long pale face on a paper-thin body, carried on ten legs. */
public final class OccupantGeometry {
\t/** The bones of the shroud it wears while it is still only a shape. */
\tpublic static final List<String> SHROUD = List.of(%s);
\t/** Height of the built body in model pixels (16 = one block). */
\tpublic static final float HEIGHT = %sf;
\t/** How high the hips stand above the ground when it is upright, in model pixels. */
\tpublic static final float HIPS_HEIGHT = %sf;
\tpublic static final int LEGS = %d;
\t/** Each leg's direction out from the body, in radians (model x = sin, model z = cos). */
\tpublic static final float[] LEG_ANGLE = {%s};
\t/** Each leg's full length, root to point, in model pixels. */
\tpublic static final float[] LEG_LENGTH = {%s};
\t/** How high each leg leaves the body, above the ground, standing upright, in model pixels. */
\tpublic static final float[] LEG_ROOT_HEIGHT = {%s};

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
    # Measured, not declared: the renderer scales the body by this, so it has to be what was built.
    origins = _world_origins(ps)
    top = min(origins[name][1] + y for name, _p, _piv, _r, own in ps for (_k, _x, y, _z, _w, _h, _d) in own
              if not name.startswith("leg"))
    height = GROUND - top
    layout = leg_layout()
    lines = [HEADER % (", ".join('"%s"' % n for n in SHROUD), num(height).rstrip("f"),
                       num(HIPS_HEIGHT).rstrip("f"), LEGS,
                       ", ".join("%.4ff" % a for a, *_ in layout),
                       ", ".join(num(u + l + c) for _a, _y, u, l, c in layout),
                       ", ".join(num(leg_root_height(r)) for _a, r, _u, _l, _c in layout))]
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
    ps = avoid_coplanar(parts())
    boxes = [(name, *b) for name, _p, _piv, _r, own in ps for b in own]
    placed = pack(boxes)
    paint_texture(boxes, placed)
    write_java(ps, boxes, placed)
    used = max(placed[i][1] + unfolded(*boxes[i][5:8])[1] for i in placed)
    print("%d bones, %d cubes, texture %dx%d (%d rows used)" % (len(ps), len(boxes), TEX_W, TEX_H, used))


if __name__ == "__main__":
    main()
