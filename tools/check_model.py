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
            x, y, z, w, h, d = [float(v) for v in NUM_RE.findall(box)]
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


def span(a0, a1, b0, b1):
    return min(a1, b1) - max(a0, b0)


def check_planes(boxes, parents):
    """Coplanar, overlapping faces from unrelated bones: the cause of flickering surfaces."""
    bad = []
    for i in range(len(boxes)):
        na, pa, A = boxes[i]
        for j in range(i + 1, len(boxes)):
            nb, pb, B = boxes[j]
            if related(na, nb, parents):
                continue
            for axis in range(3):
                u, v = (axis + 1) % 3, (axis + 2) % 3
                ou = span(A[u], A[u + 3], B[u], B[u + 3])
                ov = span(A[v], A[v + 3], B[v], B[v + 3])
                if ou <= AREA_EPS or ov <= AREA_EPS:
                    continue
                for fa in (A[axis], A[axis + 3]):
                    for fb in (B[axis], B[axis + 3]):
                        if abs(fa - fb) < PLANE_EPS:
                            bad.append((na, nb, "xyz"[axis], fa, ou * ov))
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
            _x, _y, _z, w, h, d = [float(t) for t in NUM_RE.findall(box)]
            w, h, d = [max(1, int(-(-v // 1))) for v in (w, h, d)]   # ceil, min 1
            rects.append((name, u, v, u + 2 * d + 2 * w, v + d + h))
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
    src = MODEL.read_text()
    wanted = set(re.findall(r'getChild\("(\w+)"\)', src))
    # Names built up in loops, e.g. SIDE[s] + "_upper" and "_finger" + i.
    for suffix in re.findall(r'getChild\(SIDE\[s\] \+ "(\w+)"\)', src):
        wanted.update(("right" + suffix, "left" + suffix))
    for suffix, count in re.findall(r'getChild\(SIDE\[s\] \+ "(\w+)" \+ (\w+)\)', src):
        for side in ("right", "left"):
            for k in range(4):
                wanted.add("%s%s%d" % (side, suffix, k))
    wanted.update(HUMANOID_REQUIRED)
    return sorted(w for w in wanted if w not in defined)


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
