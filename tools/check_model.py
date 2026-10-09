#!/usr/bin/env python3
"""
Checks the generated model for the things that make a model look broken in game, and for the
things that make it stop working at all.

    python3 tools/check_model.py

It reads the generated OccupantGeometry.java, so it checks what the game will actually build,
not what the generator meant to build. Three classes of problem:

1. Z-FIGHTING. Two faces from different bones lying in the same plane and overlapping. The
   renderer has no way to decide which is in front, so the surface flickers between them as
   the camera moves. This is the single most common reason a Minecraft model looks wrong.
2. BURIED GEOMETRY. A box completely inside another one. Harmless to look at, but it is
   invisible work: triangles drawn every frame that nobody can ever see.
3. BROKEN LOOKUPS. A bone the Java model asks for by name that the geometry does not define.
   This throws when the entity is first rendered, so the model is dead on arrival.

Exits non-zero if anything is wrong, so CI can run it.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GEOM = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantGeometry.java"
MODEL = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantModel.java"
# Where the bones are looked up by name, to be moved.
POSE = ROOT / "src/client/java/com/wolfsmask/occupant/client/render/OccupantPose.java"

# Faces closer together than this count as the same plane.
PLANE_EPS = 0.02
# Overlaps smaller than this in the other two axes are a shared edge, not a shared surface.
AREA_EPS = 0.05

PART_RE = re.compile(
    r'p = (\w+)\.addOrReplaceChild\("(\w+)", '
    r'(CubeListBuilder\.create\(\)(?:\s*\.texOffs\([^)]*\)\.addBox\([^)]*\))*), '
    r'(PartPose\.[^;]+)\);')
BOX_RE = re.compile(r'\.texOffs\(([^)]*)\)\.addBox\(([^)]*)\)')
NUM_RE = re.compile(r'-?\d+\.?\d*')


def load():
    """Every box in world space, in the rest pose: (bone, parent, (x0,y0,z0,x1,y1,z1))."""
    src = GEOM.read_text()
    parents, pivots, boxes = {}, {}, []
    for m in PART_RE.finditer(src):
        parent, name, cubes, pose = m.groups()
        parent = None if parent == "root" else parent[2:]
        parents[name] = parent
        vals = [float(v) for v in NUM_RE.findall(pose)] if "ZERO" not in pose else [0, 0, 0]
        pivots[name] = vals[:3]
        if len(vals) > 3 and any(abs(v) > 1e-6 for v in vals[3:6]):
            print("note: %s is built rotated; its check is approximate" % name)
        for _uv, box in BOX_RE.findall(cubes):
            x, y, z, w, h, d = [float(v) for v in NUM_RE.findall(box)][:6]
            boxes.append([name, (x, y, z, x + w, y + h, z + d)])

    def origin(name):
        ox = oy = oz = 0.0
        while name is not None:
            px, py, pz = pivots[name]
            ox, oy, oz = ox + px, oy + py, oz + pz
            name = parents[name]
        return ox, oy, oz

    out = []
    for name, (x0, y0, z0, x1, y1, z1) in boxes:
        ox, oy, oz = origin(name)
        out.append((name, parents[name], (x0 + ox, y0 + oy, z0 + oz, x1 + ox, y1 + oy, z1 + oz)))
    return out, parents


def related(a, b, parents):
    """Bones that meet at a joint are expected to share a boundary."""
    if a == b:
        return True
    for x, y in ((a, b), (b, a)):
        p = parents.get(x)
        depth = 0
        while p is not None and depth < 3:      # parent, grandparent, great-grandparent
            if p == y:
                return True
            p = parents.get(p)
            depth += 1
    return False


def is_leg(name):
    return re.match(r"leg\d+_", name) is not None


def span(a0, a1, b0, b1):
    return min(a1, b1) - max(a0, b0)


def check_planes(boxes, parents):
    """
    Two faces lying in the same plane, pointing the same way, and overlapping. Entity models
    are drawn without back-face culling, so there is no rule for which one wins: the surface
    flickers between them as the camera moves. This applies between any two boxes at all,
    including two in the same bone and a bone and its own parent.

    Two boxes that merely touch (one's right face against the other's left face) are a seam,
    not a flicker: the shared plane is enclosed by the two boxes and can never be seen.
    """
    bad = []
    for i in range(len(boxes)):
        na, pa, A = boxes[i]
        for j in range(i + 1, len(boxes)):
            nb, pb, B = boxes[j]
            # A leg is re-aimed every frame at wherever it is planted, so where it hangs at rest
            # says nothing about what it will touch in game. Only its own boxes are compared.
            if na != nb and (is_leg(na) or is_leg(nb)):
                continue
            for axis in range(3):
                u, v = (axis + 1) % 3, (axis + 2) % 3
                ou = span(A[u], A[u + 3], B[u], B[u + 3])
                ov = span(A[v], A[v + 3], B[v], B[v + 3])
                if ou <= AREA_EPS or ov <= AREA_EPS:
                    continue
                # Same side only: both minimum faces, or both maximum faces.
                for side in (0, 3):
                    if abs(A[axis + side] - B[axis + side]) < PLANE_EPS:
                        bad.append((na, nb, "xyz"[axis], A[axis + side], ou * ov))
    return bad


def check_buried(boxes, parents):
    """Boxes wholly inside another box: never visible, drawn anyway."""
    bad = []
    for i, (na, pa, A) in enumerate(boxes):
        for j, (nb, pb, B) in enumerate(boxes):
            if i == j or na == nb:
                continue
            inside = all(B[k] - 1e-6 <= A[k] and A[k + 3] <= B[k + 3] + 1e-6 for k in range(3))
            if inside:
                bad.append((na, nb))
    return bad


# HumanoidModel's own constructor looks these up. If any is missing the entity renderer throws
# the first time an Occupant is drawn, which takes the whole client down with it.
HUMANOID_REQUIRED = ("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")


def check_uvs():
    """
    Two boxes given the same patch of texture would wear each other's pixels. The packer is
    supposed to make that impossible, so this is checking the packer, not the model.
    """
    src = GEOM.read_text()
    rects = []
    for m in PART_RE.finditer(src):
        name = m.group(2)
        for uv, box in BOX_RE.findall(m.group(3)):
            u, v = [float(t) for t in NUM_RE.findall(uv)]
            nums = [float(t) for t in NUM_RE.findall(box)]
            _x, _y, _z, w, h, d = nums[:6]
            # A texture scale (the face's boxes) divides everything: offset and sizes alike.
            ts = nums[6] if len(nums) >= 8 else 1.0
            if ts == 1.0:
                w, h, d = [max(1, int(-(-v // 1))) for v in (w, h, d)]   # ceil, min 1
            rects.append((name, u / ts, v / ts, (u + 2 * d + 2 * w) / ts, (v + d + h) / ts))
    bad = []
    for i in range(len(rects)):
        na, ax0, ay0, ax1, ay1 = rects[i]
        for j in range(i + 1, len(rects)):
            nb, bx0, by0, bx1, by1 = rects[j]
            if span(ax0, ax1, bx0, bx1) > 0.5 and span(ay0, ay1, by0, by1) > 0.5:
                bad.append((na, nb))
    return bad


def check_lookups():
    """Every bone OccupantModel.java asks for has to exist, or the model throws on first draw."""
    defined = set(re.findall(r'addOrReplaceChild\("(\w+)"', GEOM.read_text()))
    src = MODEL.read_text() + POSE.read_text()
    wanted = set(re.findall(r'getChild\("(\w+)"\)', src))
    # Names built up in loops, e.g. SIDE[s] + "_upper" and "_finger" + i.
    for suffix in re.findall(r'getChild\(SIDE\[s\] \+ "(\w+)"\)', src):
        wanted.update(("right" + suffix, "left" + suffix))
    for suffix, count in re.findall(r'getChild\(SIDE\[s\] \+ "(\w+)" \+ (\w+)\)', src):
        for side in ("right", "left"):
            for k in range(4):
                wanted.add("%s%s%d" % (side, suffix, k))
    # Numbered bones, e.g. "leg" + i + "_upper", for every i up to OccupantGeometry.LEGS.
    legs = re.search(r'LEGS = (\d+);', GEOM.read_text())
    for prefix, suffix in re.findall(r'getChild\("(\w+)" \+ \w+ \+ "(\w+)"\)', src):
        for k in range(int(legs.group(1)) if legs else 0):
            wanted.add("%s%d%s" % (prefix, k, suffix))
    wanted.update(HUMANOID_REQUIRED)
    return sorted(w for w in wanted if w not in defined)


def check_ground(boxes, parents):
    """
    Minecraft draws model y = 24 at the entity's feet. The legs are planted in game, on whatever
    is really there, so what has to hold here is that they are long enough to reach the ground
    from where the hips stand, that the body itself stays clear of the ground, and that
    OccupantGeometry.HEIGHT (which the renderer scales by) is the height that was really built.
    """
    problems = []
    src = GEOM.read_text()
    body = [b for b in boxes if not is_leg(b[0])]
    highest = min(b[2][1] for b in body)
    lowest_body = max(b[2][4] for b in body)
    if lowest_body > 24.0 - 1.0:
        problems.append("the body reaches y = %.2f, into the ground (24)" % lowest_body)
    m = re.search(r'HEIGHT = (-?[\d.]+)f', src)
    if m is None:
        problems.append("OccupantGeometry.HEIGHT is missing")
    elif abs(float(m.group(1)) - (24.0 - highest)) > 0.3:
        problems.append("HEIGHT says %s px but the body is %.2f px tall" % (m.group(1), 24.0 - highest))

    hips = re.search(r'HIPS_HEIGHT = (-?[\d.]+)f', src)
    if hips is None:
        problems.append("OccupantGeometry.HIPS_HEIGHT is missing")
        return problems
    for b in boxes:
        name = b[0]
        if not name.endswith("_upper") or not is_leg(name):
            continue
        root_y = b[2][1] + 0.6                      # where the hip joint is
        chain = [x for x in boxes if x[0].startswith(name[:-len("upper")])]
        length = sum(x[2][4] - x[2][1] for x in chain) - 0.6 - 0.5 - 0.3
        drop = 24.0 - root_y
        if length < drop * 1.15:
            problems.append("%s is %.1f px long and cannot reach the ground %.1f px below its hip"
                            % (name[:-6], length, drop))
    return problems


def check_legs():
    """
    The legs are aimed in game by OccupantGeometry.LEG_UPPER, LEG_LENGTH and LEG_TIP, not by the
    bones themselves, so those have to be what was built: where the knee is, and where the hooked
    point is in the shin's frame. If they drift, every planted leg misses what it is planted on.
    """
    import math
    src = GEOM.read_text()
    problems = []

    def floats(name):
        m = re.search(name + r' = \{([^}]*)\}', src)
        return None if m is None else [float(v) for v in m.group(1).replace("f", "").split(",")]

    upper_len, length, tip = floats("LEG_UPPER"), floats("LEG_LENGTH"), floats("LEG_TIP")
    legs = re.search(r'LEGS = (\d+);', src)
    if None in (upper_len, length, tip) or legs is None:
        return ["OccupantGeometry is missing LEGS, LEG_UPPER, LEG_LENGTH or LEG_TIP"]
    n = int(legs.group(1))
    if len(upper_len) != n or len(length) != n or len(tip) != 3 * n:
        return ["LEG_UPPER, LEG_LENGTH and LEG_TIP do not have one entry (three for LEG_TIP) per leg"]
    poses, first_box = {}, {}
    for m in PART_RE.finditer(src):
        _parent, name, cubes, pose = m.groups()
        poses[name] = [float(v) for v in NUM_RE.findall(pose)] if "ZERO" not in pose else [0.0] * 3
        found = BOX_RE.search(cubes)
        if found:
            first_box[name] = [float(v) for v in NUM_RE.findall(found.group(2))][:6]
    for i in range(n):
        lower, claw = poses.get("leg%d_lower" % i), poses.get("leg%d_claw" % i)
        if lower is None or claw is None or ("leg%d_claw" % i) not in first_box:
            problems.append("leg%d has no shin or no point" % i)
            continue
        if abs(lower[1] - upper_len[i]) > 0.02:
            problems.append("leg%d: LEG_UPPER says %.2f but the knee is at %.2f" % (i, upper_len[i], lower[1]))
        rx, ry, rz = (claw[3:6] + [0.0, 0.0, 0.0])[:3]
        x, y, z, w, h, d = first_box["leg%d_claw" % i]
        v = [x + w / 2, y + h, z + d / 2]
        # Minecraft turns a part about Z, then Y, then X: X is applied to the point first.
        v = [v[0], v[1] * math.cos(rx) - v[2] * math.sin(rx), v[1] * math.sin(rx) + v[2] * math.cos(rx)]
        v = [v[0] * math.cos(ry) + v[2] * math.sin(ry), v[1], -v[0] * math.sin(ry) + v[2] * math.cos(ry)]
        v = [v[0] * math.cos(rz) - v[1] * math.sin(rz), v[0] * math.sin(rz) + v[1] * math.cos(rz), v[2]]
        real = [claw[0] + v[0], claw[1] + v[1], claw[2] + v[2]]
        said = tip[3 * i:3 * i + 3]
        if max(abs(a - b) for a, b in zip(real, said)) > 0.02:
            problems.append("leg%d: LEG_TIP says (%.2f, %.2f, %.2f) but the point is at (%.2f, %.2f, %.2f)"
                            % ((i,) + tuple(said) + tuple(real)))
        reach = lower[1] + math.sqrt(sum(c * c for c in real))
        if abs(reach - length[i]) > 0.03:
            problems.append("leg%d: LEG_LENGTH says %.2f but root to point is %.2f" % (i, length[i], reach))
    return problems


def check_tree(parents):
    """Every bone has to hang off something that exists, or the mesh cannot be built."""
    bad = []
    for name, parent in parents.items():
        if parent is not None and parent not in parents:
            bad.append((name, parent))
    return bad


def main():
    boxes, parents = load()
    print("%d boxes across %d bones" % (len(boxes), len(set(b[0] for b in boxes))))
    problems = 0

    missing = check_lookups()
    if missing:
        problems += len(missing)
        print("\nBROKEN LOOKUPS (the model will throw when it is first drawn):")
        for m in missing:
            print("  OccupantModel wants a bone called '%s', which does not exist" % m)

    ground = check_ground(boxes, parents)
    if ground:
        problems += len(ground)
        print("\nNOT STANDING ON THE GROUND:")
        for g in ground:
            print("  " + g)

    legs = check_legs()
    if legs:
        problems += len(legs)
        print("\nLEGS AIMED WRONG (what the game aims them by is not what was built):")
        for g in legs:
            print("  " + g)

    orphans = check_tree(parents)
    if orphans:
        problems += len(orphans)
        print("\nBROKEN TREE:")
        for name, parent in orphans:
            print("  '%s' hangs off '%s', which is not defined" % (name, parent))

    uvs = check_uvs()
    if uvs:
        problems += len(uvs)
        print("\nSHARED TEXTURE SPACE (parts would wear each other's pixels):")
        for na, nb in uvs[:20]:
            print("  %s and %s" % (na, nb))

    planes = check_planes(boxes, parents)
    if planes:
        problems += len(planes)
        print("\nZ-FIGHTING (%d coplanar overlapping faces):" % len(planes))
        for na, nb, axis, at, area in sorted(planes, key=lambda p: -p[4])[:20]:
            print("  %-16s and %-16s share the %s = %.2f plane over %.1f px2" % (na, nb, axis, at, area))

    buried = check_buried(boxes, parents)
    if buried:
        problems += len(buried)
        print("\nBURIED (%d boxes that can never be seen):" % len(buried))
        for na, nb in buried[:20]:
            print("  %s is entirely inside %s" % (na, nb))

    if problems:
        print("\n%d problem(s)." % problems)
        return 1
    print("No overlaps, no z-fighting, no missing bones.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
