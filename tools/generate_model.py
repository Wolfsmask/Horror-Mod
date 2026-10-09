#!/usr/bin/env python3
"""
Builds the Occupant's body: both the Java geometry and the texture it is painted with.

    pip install numpy pillow
    python3 tools/generate_model.py

Writing both from one description means the texture coordinates in the model and the pixels in
the texture can never drift apart. It writes:

    src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java
    src/main/resources/assets/occupant/textures/entity/occupant.png
    src/main/resources/assets/occupant/textures/entity/occupant_glow.png

The animation lives in OccupantPose.java, which is written by hand. tools/preview_model.py draws
what this builds, as the game would, without starting the game.

What it is: Father Fester. A swollen bald head, pale as something kept from the light, with two
big round black eyes and a long gaping mouth ringed with needles that is most of the face, framed
by long thin hair the colour of dried blood, on a body far too tall and as thin as paper, carried on ten long pale
legs. The legs are not walked on. Each one reaches out to the nearest thing it can push against,
the ground, a wall, a tree, a ceiling, and the body is shoved along between them. Where each leg
is planted is worked out in game (OccupantRenderer / LegGait) and the joints are solved to reach
it (OccupantModel), so this only builds the parts at rest, hanging straight down.

Units are pixels; the ground is at y = 24 and up is -y (the same convention as vanilla models).
"""
import math
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
        x = float(np.sin(a)) * (4.7 + 0.5 * float(_S.random()))
        z = float(np.cos(a)) * (3.75 + 0.45 * float(_S.random()))
        if front < 0.0:
            # Beside the face the hair has to hang clear of the swollen brow, not through it.
            x = float(np.sign(np.sin(a))) * max(abs(x), 4.5)
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


# --------------------------------------------------------------------------- the face
#
# The face is what is looked at, from close, so it is built differently from the rest: rounded
# out of rows instead of being one box, with real hollows where the eyes are, and painted at eight
# texels to a pixel instead of one. The whole texture is SCALE times the size its offsets are
# written for, and the face's boxes take FACE_TS of the usual texture scale, which gives them
# 1 / FACE_TS times as much of it again. Every face dimension is a multiple of 1/8, so each one's
# texture lands on whole texels.
#
# What it should be: not a monster's face. A mask: white, smooth, rigid as porcelain, with nothing
# on it a face should have but two great black holes and a mouth, and nothing in those but, far
# back in each hole, one tiny white point that is always, exactly, on you.
SCALE = 4
FACE_TS = 0.5
FACE_KINDS = ("skin", "socket", "cavity", "pupil")

# Where the eyes are, in the skull's own space (x across, y down, the face at -z), and how far the
# black of them reaches. The hollow itself is smaller than the black, so its corners never show;
# the paint makes the round.
EYE_X, EYE_Y = 1.75, -5.675
EYE_RX, EYE_RY = 1.45, 1.8
# Where the back of each hollow is; and where the pupils hang, in the black between that and the
# face: near enough the front that the rim of the hollow never hides them, from wherever they are
# looked at.
EYE_BACK = -1.75
PUPIL_Z = -2.45

# The jaw, row by row (1 px each): how far the face reaches out, and the front of it, which is one
# flat plane like a mask's (every step back in it showed in game as a dark line across the face).
# Far too long: the mouth has pulled it down.
JAW_OUTER = [3.5, 3.5, 3.375, 3.375, 3.25, 3.25, 3.125, 3.0, 2.875, 2.75, 2.625, 2.375, 2.125]
FACE_FRONT = -3.0
# The jaw is a shell, in front of the neck: as deep as a mask is.
JAW_BACK = -0.875
# The mouth as it is seen: a long narrow oval, painted. Each row is open only inside it, so the
# steps of the rows never show; the paint is what makes it round.
MOUTH_Y, MOUTH_RX, MOUTH_RY = 6.6, 1.35, 5.85


def _opening(r):
    """How far the mouth opens either side of the middle in jaw row r: inside the oval, or shut."""
    widest = min(MOUTH_RX * math.sqrt(max(0.0, 1.0 - ((y - MOUTH_Y) / MOUTH_RY) ** 2)) for y in (r, r + 1))
    o = math.floor((widest - 0.0625) * 8.0) / 8.0
    return o if o >= 0.25 else 0.0


JAW_ROWS = [(outer, _opening(r), FACE_FRONT) for r, outer in enumerate(JAW_OUTER)]


def tex_scale(kind):
    return FACE_TS if kind in FACE_KINDS else 1.0


def skull_boxes():
    """
    Above the mouth: a bald, swollen crown, wider than the face below it, like an egg; under it
    two big round hollows for eyes either side of a broad flat bridge, a pixel deep; then nothing
    at all where a nose should be.
    """
    return [
        ("skin", -1.75, -11.05, -2.0, 3.5, 0.5, 4.25),
        ("skin", -2.75, -10.55, -2.5, 5.5, 0.5, 5.125),
        ("skin", -3.375, -10.05, -2.75, 6.75, 0.5, 5.625),
        ("skin", -3.75, -9.55, -2.875, 7.5, 0.5, 5.75),
        ("skin", -3.875, -9.05, -3.0, 7.75, 2.25, 6.0),             # the forehead, at its widest
        ("skin", -3.875, -6.8, -3.0, 1.25, 2.25, 6.0),              # beside the left eye
        ("socket", -2.625, -6.8, EYE_BACK, 1.75, 2.25, 4.75),       # the left eye: a hollow
        ("skin", -0.875, -6.8, -3.0, 1.75, 2.25, 6.0),              # the bridge between them
        ("socket", 0.875, -6.8, EYE_BACK, 1.75, 2.25, 4.75),        # the right eye
        ("skin", 2.625, -6.8, -3.0, 1.25, 2.25, 6.0),
        ("skin", -3.75, -4.55, -3.0, 7.5, 2.25, 5.875),             # under the eyes: nothing
    ]


def pupil_boxes():
    """One tiny white point, hanging in the black of a hollow. It is moved in game."""
    return [("pupil", -0.1875, -0.1875, -0.155, 0.375, 0.375, 0.125)]


def jaw_boxes():
    """
    The face carries on down far too far, narrowing a little, and down the middle of it the mouth
    is open: long, narrow, empty, black all the way in.
    """
    out = []
    for r, (outer, opening, front) in enumerate(JAW_ROWS):
        if opening == 0.0:
            out.append(("skin", -outer, float(r), front, 2 * outer, 1.0, JAW_BACK - front))
            continue
        out.append(("skin", -outer, float(r), front, outer - opening, 1.0, JAW_BACK - front))
        out.append(("skin", opening, float(r), front, outer - opening, 1.0, JAW_BACK - front))
    rows = float(len(JAW_ROWS))
    # The chin: rounding off, long and narrow but never a point.
    out.append(("skin", -1.75, rows, FACE_FRONT, 3.5, 0.75, JAW_BACK - FACE_FRONT))
    out.append(("skin", -1.25, rows + 0.75, FACE_FRONT, 2.5, 0.625, JAW_BACK - FACE_FRONT))
    out.append(("skin", -0.75, rows + 1.375, FACE_FRONT, 1.5, 0.375, JAW_BACK - FACE_FRONT))
    # Inside: a hollow behind it all, reaching into the face either side so its walls are never
    # seen, and from just under the top of the mouth to into the shut row under the bottom of it.
    widest = max(o for _out, o, _f in JAW_ROWS) + 0.125
    last = max(r for r, (_o, o, _f) in enumerate(JAW_ROWS) if o > 0.0)
    out.append(("cavity", -widest, 1.0625, -1.5, 2 * widest, last + 1.0, 0.5))
    return out


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

        # The face is built in tools/generate_model.py's FACE section: rounded, with real hollows
        # for the eyes and a mouth ringed with needles, at eight times the texture of the rest.
        ("skull", "neck", (0, -11.0 + FACE_MID, -0.4), (0, 0, 0), skull_boxes()),
        ("jaw", "skull", (0, -FACE_MID, 0), (0, 0, 0), jaw_boxes()),
        # The pupils: their own bones, so that in game they can be moved to stay on you.
        ("left_pupil", "skull", (EYE_X, EYE_Y, PUPIL_Z), (0, 0, 0), pupil_boxes()),
        ("right_pupil", "skull", (-EYE_X, EYE_Y, PUPIL_Z), (0, 0, 0), pupil_boxes()),
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
        lo = (round(x, 3) + ox, round(y, 3) + oy, round(z, 3) + oz)
        return lo, (lo[0] + round(w, 3), lo[1] + round(h, 3), lo[2] + round(d, 3))

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
                # And a little further each time, so it cannot stay caught among close planes.
                step = 0.09 * (1.0 + k / 150.0)
                dx = step * (((k * 0.6180339887) % 1.0) - 0.5)
                dz = step * (((k * 0.7548776662) % 1.0) - 0.5)
                dy = step * (((k * 0.5698402910) % 1.0) - 0.5)
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


def unfolded(w, h, d, ts=1.0):
    """
    Size of a cube's unwrapped texture region, in the units the offsets are written in. A box with
    a texture scale below one is given that much more of the texture: the game divides by it.
    """
    if ts == 1.0:
        return 2 * _texels(d) + 2 * _texels(w), _texels(d) + _texels(h)
    return int(np.ceil((2 * d + 2 * w) / ts - 1e-6)), int(np.ceil((d + h) / ts - 1e-6))


def pack(boxes):
    """
    Shelf-packs every cube into the texture. Returns {index: (u, v)}, the offsets as written in
    the Java: for a box with its own texture scale, that is where its region starts times the
    scale, so its region has to start where that comes out whole.
    """
    size = {i: unfolded(*boxes[i][5:8], tex_scale(boxes[i][1])) for i in range(len(boxes))}
    order = sorted(range(len(boxes)), key=lambda i: (-size[i][1], i))
    placed = {}
    x = y = shelf = 0
    for i in order:
        ts = tex_scale(boxes[i][1])
        step = int(round(1.0 / ts))
        bw, bh = max(size[i][0], 1), max(size[i][1], 1)
        x = -(-x // step) * step
        if x + bw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        y_at = -(-y // step) * step
        if y_at + bh > TEX_H:
            raise SystemExit("texture is full: make it bigger")
        if y_at != y:
            shelf = max(shelf, bh + (y_at - y))
        placed[i] = (int(round(x * ts)), int(round(y_at * ts)))
        x += bw
        shelf = max(shelf, bh + (y_at - y))
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
        if kind in FACE_KINDS:
            continue                                  # painted afterwards, at full detail
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

    # Everything but the face, at the texture's full size: each texel of it becomes SCALE x SCALE.
    img = np.repeat(np.repeat(img, SCALE, axis=0), SCALE, axis=1)
    dark_mask = np.repeat(np.repeat(dark_mask, SCALE, axis=0), SCALE, axis=1)
    limb_mask = np.repeat(np.repeat(limb_mask, SCALE, axis=0), SCALE, axis=1)

    # A faint sheen, drawn full-bright, so the face is the one thing still visible in the dark.
    # The game draws this layer BLENDED over the body, not added to it: an opaque pixel here
    # replaces the lit face underneath. So the sheen is the face's own colour at low opacity.
    # (It used to be a dimmed copy at full opacity, which painted the face dark grey in daylight.)
    glow = img.copy()
    glow[:, :, 3] = np.where(img[:, :, 3] > 0, SHEEN, 0)
    glow[limb_mask, 3] = SHEEN // 2                      # the legs, fainter than the face
    glow[dark_mask] = 0

    paint_face_boxes(img, glow, boxes, placed)

    Image.fromarray(img, "RGBA").save(TEX / "occupant.png")
    Image.fromarray(glow, "RGBA").save(TEX / "occupant_glow.png")


# --------------------------------------------------------------------------- painting the face

PORCELAIN = np.array([236, 234, 229], float)   # white, cold, with no life in it
PORCELAIN_SHADE = np.array([178, 178, 180], float)
PORCELAIN_GLAZE = np.array([250, 250, 247], float)
VOID = np.array([3, 3, 4], float)              # the eyes, and the mouth: nothing
CRACK = np.array([34, 32, 36], float)          # hairline fractures, running from the eyes
CRACK_EDGE = np.array([206, 206, 210], float)  # the chipped glaze either side of one
PUPIL = np.array([255, 255, 252], float)

_NOISE = np.random.default_rng(77).random((24, 24, 24))


def vnoise(p, freq):
    """Smooth value noise in 0..1 at the points p (N x 3)."""
    q = p * freq + 37.0
    i = np.floor(q).astype(int)
    f = q - i
    f = f * f * (3 - 2 * f)
    n = _NOISE.shape[0]

    def g(dx, dy, dz):
        return _NOISE[(i[:, 0] + dx) % n, (i[:, 1] + dy) % n, (i[:, 2] + dz) % n]

    x0 = g(0, 0, 0) * (1 - f[:, 0]) + g(1, 0, 0) * f[:, 0]
    x1 = g(0, 1, 0) * (1 - f[:, 0]) + g(1, 1, 0) * f[:, 0]
    x2 = g(0, 0, 1) * (1 - f[:, 0]) + g(1, 0, 1) * f[:, 0]
    x3 = g(0, 1, 1) * (1 - f[:, 0]) + g(1, 1, 1) * f[:, 0]
    y0 = x0 * (1 - f[:, 1]) + x1 * f[:, 1]
    y1 = x2 * (1 - f[:, 1]) + x3 * f[:, 1]
    return y0 * (1 - f[:, 2]) + y1 * f[:, 2]


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(col, target, amount):
    a = np.clip(np.broadcast_to(np.asarray(amount, float), (len(col),)), 0.0, 1.0)[:, None]
    return col * (1 - a) + np.asarray(target, float) * a


def _polyline_distance(px, py, pts):
    """Distance from each point to a polyline."""
    best = np.full(px.shape, np.inf)
    for a, b in zip(pts, pts[1:]):
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        t = np.clip(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0, 1)
        best = np.minimum(best, np.hypot(px - (ax + t * dx), py - (ay + t * dy)))
    return best


def _fracture(seed, start, heading, length, depth=0):
    """
    A hairline crack: a run of short straight lengths, each turning a little, as glaze breaks, with
    now and then a finer one branching off it. Returns a list of (polyline, width).
    """
    r = np.random.default_rng(seed)
    x, y = start
    pts = [(x, y)]
    walked = 0.0
    out = []
    while walked < length:
        step = 0.22 + 0.25 * r.random()
        heading += r.normal(0, 0.45)
        x += math.sin(heading) * step
        y += math.cos(heading) * step
        walked += step
        pts.append((x, y))
        if depth < 1 and r.random() < 0.07:
            side = 1 if r.random() < 0.5 else -1
            out += _fracture(seed * 7 + len(pts), (x, y), heading + side * (0.6 + 0.5 * r.random()),
                             length * (0.25 + 0.2 * r.random()), depth + 1)
    out.append((pts, 0.05 if depth == 0 else 0.035))
    return out


# Where it has cracked: down from the bottom of each eye, over the cheek, the way tears would run;
# one up from the left eye into the brow; one from the bottom of the mouth. In face space (x, y).
FRACTURES = []
for side, seed in ((-1, 11), (1, 12)):
    FRACTURES += _fracture(seed, (side * (EYE_X + 0.25), EYE_Y + EYE_RY * 0.98), side * 0.22, 5.5)
FRACTURES += _fracture(52, (EYE_X + 0.95, EYE_Y + EYE_RY * 0.7), 0.55, 2.2)
FRACTURES += _fracture(41, (-(EYE_X + 0.3), EYE_Y - EYE_RY * 0.97), math.pi - 0.35, 2.4)


def skin_colour(p, n, owner):
    """The porcelain at points p (skull space), on a face whose outward direction is n."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    ax = np.abs(x)
    # Barely any grain at all: it should look made, not grown.
    col = PORCELAIN * (1.0 + 0.018 * (vnoise(p, 0.45) - 0.5))[:, None]
    front = n[2] < -0.5

    if front:
        # Lit the way glaze is: a soft shine down the middle, falling away at the edges.
        col = mix(col, PORCELAIN_SHADE, 0.30 * smooth(2.6, 3.9, ax) ** 1.4)
        col = mix(col, PORCELAIN_SHADE, 0.22 * smooth(-9.6, -11.0, y))
        col = mix(col, PORCELAIN_GLAZE, 0.5 * np.exp(-((x / 1.6) ** 2 + ((y + 8.0) / 1.0) ** 2)))
    elif n[1] < -0.5:
        col = mix(col, PORCELAIN_SHADE, 0.12)
    elif n[1] > 0.5:
        col = mix(col, PORCELAIN_SHADE * 0.6, 0.6)
    else:
        col = mix(col, PORCELAIN_SHADE, 0.25 + 0.15 * smooth(-3.0, 0.0, z))

    # The eyes: black, with a hard edge, like holes cut in a mask.
    ex = (ax - EYE_X) / EYE_RX
    ey = (y - EYE_Y) / EYE_RY
    r = np.sqrt(ex * ex + ey * ey)
    col = mix(col, PORCELAIN_SHADE * 0.75, 0.6 * smooth(1.12, 1.0, r))
    col = mix(col, VOID, smooth(1.0, 0.985, r))
    # Inside the hollows (their walls, the brow over them, the ledge under them): black.
    hollow = (ax > 0.86) & (ax < 2.64) & (y > -6.82) & (y < -4.53) & (z > -2.98)
    col[hollow] = np.tile(VOID, (int(hollow.sum()), 1))

    if front:
        # Hairline fractures.
        for pts, width in FRACTURES:
            d = _polyline_distance(x, y, pts)
            col = mix(col, CRACK_EDGE, 0.55 * smooth(width * 2.6, width * 1.2, d))
            col = mix(col, CRACK, smooth(width, width * 0.35, d))

    if owner == "jaw":
        jy = y + FACE_MID
        if front:
            # The mouth: a hard-edged hole, like the eyes, and nothing inside it.
            e = np.sqrt((x / MOUTH_RX) ** 2 + ((jy - MOUTH_Y) / MOUTH_RY) ** 2)
            col = mix(col, PORCELAIN_SHADE * 0.75, 0.6 * smooth(1.08, 1.0, e))
            col = mix(col, VOID, smooth(1.0, 0.99, e))
        elif not (n[2] > 0.5):
            # Facing into the mouth: black.
            near = ax < MOUTH_RX + 0.05
            col[near] = np.tile(VOID, (int(near.sum()), 1))
    elif n[1] > 0.5:
        # The roof of the mouth, seen from below through it.
        roof = (ax < MOUTH_RX) & (y > -2.35)
        col[roof] = np.tile(VOID, (int(roof.sum()), 1))
    return col


def face_texels(kind, owner, box, p, n):
    """Colour and glow (0-255) for the points p of one face of a box of the face."""
    if kind == "pupil":
        # White, and always shining: in the dark they are all there is to see of it.
        return np.tile(PUPIL, (len(p), 1)), np.full(len(p), 255.0)
    if kind in ("socket", "cavity"):
        return np.tile(VOID, (len(p), 1)), np.zeros(len(p))
    col = skin_colour(p, n, owner)
    # The mask shows faintly in the dark; what is black does not.
    glow = SHEEN * np.clip(col.mean(axis=1) / 200.0, 0.0, 1.0) ** 1.5
    return col, glow


def paint_face_boxes(img, glow, boxes, placed):
    """
    Paints the face's boxes at full detail. Every texel is worked out from where it is on the
    face in three dimensions, so the paint runs on across the edges between boxes, and round the
    corners of them, as if the face were one surface.
    """
    for i, (owner, kind, bx, by, bz, w, h, d) in enumerate(boxes):
        if kind not in FACE_KINDS:
            continue
        u, v = placed[i]
        k = SCALE / FACE_TS
        # Where each face's region is, in texels (the game's own arithmetic: offset plus sizes).
        cols = [u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w]
        rows = [v, v + d, v + d + h]
        cu = [int(round(c * k)) for c in cols]
        rv = [int(round(r_ * k)) for r_ in rows]
        x0, y0, z0, x1, y1, z1 = bx, by, bz, bx + w, by + h, bz + d
        off = np.array([0.0, -FACE_MID, 0.0]) if owner == "jaw" else np.zeros(3)
        faces = {
            # name: (texel rect, normal, function from (s, t) in 0..1 to a point)
            "top": ((cu[1], rv[0], cu[2], rv[1]), (0, -1, 0), lambda s, t: (x0 + s * w, y0 + 0 * s, z1 - t * d)),
            "bottom": ((cu[2], rv[0], cu[3], rv[1]), (0, 1, 0), lambda s, t: (x0 + s * w, y1 + 0 * s, z1 - t * d)),
            "right": ((cu[0], rv[1], cu[1], rv[2]), (-1, 0, 0), lambda s, t: (x0 + 0 * s, y0 + t * h, z1 - s * d)),
            "front": ((cu[1], rv[1], cu[2], rv[2]), (0, 0, -1), lambda s, t: (x0 + s * w, y0 + t * h, z0 + 0 * s)),
            "left": ((cu[2], rv[1], cu[4], rv[2]), (1, 0, 0), lambda s, t: (x1 + 0 * s, y0 + t * h, z0 + s * d)),
            "back": ((cu[4], rv[1], cu[5], rv[2]), (0, 0, 1), lambda s, t: (x1 - s * w, y0 + t * h, z1 + 0 * s)),
        }
        box_skull = (kind, bx, by + off[1], bz, w, h, d)
        for name, ((rx0, ry0, rx1, ry1), normal, at) in faces.items():
            if rx1 <= rx0 or ry1 <= ry0:
                continue
            gx, gy = np.meshgrid(np.arange(rx0, rx1) + 0.5, np.arange(ry0, ry1) + 0.5)
            s_ = ((gx - rx0) / (rx1 - rx0)).ravel()
            t_ = ((gy - ry0) / (ry1 - ry0)).ravel()
            px, py, pz = at(s_, t_)
            pts = np.stack([px, py, pz], axis=1) + off
            col, gl = face_texels(kind, owner, box_skull, pts, np.array(normal, float))
            col = np.clip(col, 0, 255).astype(np.uint8).reshape(ry1 - ry0, rx1 - rx0, 3)
            gl = np.clip(gl, 0, 255).astype(np.uint8).reshape(ry1 - ry0, rx1 - rx0)
            img[ry0:ry1, rx0:rx1, :3] = col
            img[ry0:ry1, rx0:rx1, 3] = 255
            glow[ry0:ry1, rx0:rx1, :3] = col
            glow[ry0:ry1, rx0:rx1, 3] = gl

HEADER = """// GENERATED by tools/generate_model.py -- do not edit by hand.
// The texture assets/occupant/textures/entity/occupant.png is written by the same script,
// so these texture offsets and that image can never disagree.
package com.wolfsmask.occupant.client.render;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
\t/** Each leg's thigh, root to knee, in model pixels: where the knee is when the leg is solved. */
\tpublic static final float[] LEG_UPPER = {%s};

\tprivate OccupantGeometry() {
\t}

\tpublic static LayerDefinition create() {
\t\tMeshDefinition mesh = new MeshDefinition();
\t\tPartDefinition root = mesh.getRoot();
\t\tPartDefinition p;
"""


def num(v):
    """A float as the Java is written: to the nearest 1/1000, which every face size is exact in."""
    text = ("%.3f" % v).rstrip("0")
    if text.endswith("."):
        text += "0"
    if text in ("-0.0",):
        text = "0.0"
    return text + "f"


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
                       ", ".join(num(leg_root_height(r)) for _a, r, _u, _l, _c in layout),
                       ", ".join(num(u) for _a, _r, u, _l, _c in layout))]
    box_at = {}
    for i, (owner, *_rest) in enumerate(boxes):
        box_at.setdefault(owner, []).append(i)

    var = {}
    for name, parent, pivot, rot, own in ps:
        cubes = "CubeListBuilder.create()"
        for i in box_at.get(name, []):
            _, kind, x, y, z, w, h, d = boxes[i]
            u, v = placed[i]
            ts = tex_scale(kind)
            extra = "" if ts == 1.0 else ", CubeDeformation.NONE, %s, %s" % (num(ts), num(ts))
            cubes += "\n\t\t\t\t.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s%s)" % (
                u, v, num(x), num(y), num(z), num(w), num(h), num(d), extra)
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
    used = max(placed[i][1] / tex_scale(boxes[i][1]) + unfolded(*boxes[i][5:8], tex_scale(boxes[i][1]))[1]
               for i in placed)
    print("%d bones, %d cubes, texture %dx%d at %dx (%d of %d rows used)"
          % (len(ps), len(boxes), TEX_W, TEX_H, SCALE, used, TEX_H))


if __name__ == "__main__":
    main()
