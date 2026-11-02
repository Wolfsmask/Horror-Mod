import math, sys
import numpy as np

def boxes_cabin(state):
    B = []
    for x in range(-3, 4):
        for z in range(-3, 5):
            wall = x in (-3, 3) or z in (-3, 4)
            if wall:
                for y in range(0, 3):
                    if x == 0 and z == -3 and y <= 1 and state != "night":
                        continue  # the open door (a thin panel at one side; checked below)
                    B.append((x, y, z, x + 1, y + 1, z + 1))
            B.append((x, 3, z, x + 1, 4, z + 1))
    if state != "night":
        B.append((0, 0, -3, 0.19, 2, -2))  # the open door's panel, against one side
    B += [(2, 0, 2, 3, 0.5625, 4), (-2, 0, 3, -1, 1, 4), (-1, 0, 3, 0, 1, 4), (-2, 0, 2, -1, 1, 3)]
    B += [(-4, 0, 2, -3, 1, 4), (-4, 1, 3, -3, 2, 4), (-1, 0, -5, 0, 2, -4)]
    if state == "morning":
        B += [(8, 0, 1, 9, 1, 3), (7, 0, 3, 8, 1, 4)]
    return B

def boxes_house():
    B = []
    def h(X, Y, Z, y1=1.0):
        B.append((X - 6, Y - 1, Z - 5, X - 5, Y - 1 + y1, Z - 4))
    for X in range(0, 12):
        for Z in range(0, 11):
            edge = X in (0, 11) or Z in (0, 10)
            for Y in (1, 2, 3):
                if edge and not (X == 4 and Z == 0 and Y <= 2):
                    h(X, Y, Z)
            h(X, 4, Z)
    for Z in range(1, 10):
        if Z in (2, 3):
            continue
        for Y in (1, 2, 3):
            h(8, Y, Z)
    for (X, Z) in [(1, 9), (3, 6), (2, 6), (5, 9), (6, 9), (7, 1), (9, 9)]:
        h(X, 1, Z)
    h(1, 1, 8, 0.5)
    return B

def boxes_mine():
    # Everything solid but the tunnels: express as the air instead.
    return None

def boxes_camp():
    B = [(0, 0, 0, 1, 0.45, 1), (-2, 0, -1, -1, 1, 1), (-1, 0, 2, 1, 1, 3), (-3, 0, 2, -2, 1, 3), (0, 0, -4, 1, 1.0, -3)]
    for z in (-2, -1, 0):
        B += [(1, 0, z, 2, 1, z + 1), (5, 0, z, 6, 1, z + 1), (2, 1, z, 3, 2, z + 1), (4, 1, z, 5, 2, z + 1), (3, 2, z, 4, 2.5, z + 1)]
    B += [(2, 0, 0, 5, 1, 1), (3, 1, 0, 4, 2, 1), (3, 0, -1, 4, 1, 0)]
    return B

def boxes_graves():
    B = []
    for x in range(-5, 6):
        for z in range(-4, 5):
            edge = x in (-5, 5) or z in (-4, 4)
            if edge and not ((x, z) in [(0, -4), (-5, 1), (3, 4), (5, -2)]):
                B.append((x + 0.375, 0, z + 0.375, x + 0.625, 1.5, z + 0.625))
    for z in (-2, 1):
        for x in (-3, 0, 3):
            B.append((x + 0.25, 0, z - 1 + 0.25, x + 0.75, 1.5, z - 1 + 0.75))
    B.append((0.25, 0, -5 + 0.25, 0.75, 1.0, -5 + 0.75))
    return B

def dist_box(p, b):
    dx = max(b[0] - p[0], 0, p[0] - b[3])
    dy = max(b[1] - p[1], 0, p[1] - b[4])
    dz = max(b[2] - p[2], 0, p[2] - b[5])
    return math.sqrt(dx * dx + dy * dy + dz * dz)

def mine_dist(p):
    # distance to the nearest solid, the air being the two tunnels
    x, y, z = p
    def inside_air(bx, by, bz):
        return (0 <= bx <= 1 and 0 <= by <= 2 and -32 <= bz <= 56) or (2 <= bx <= 14 and 0 <= by <= 1 and bz == 26)
    best = 9
    for bx in range(math.floor(x) - 2, math.floor(x) + 3):
        for by in range(math.floor(y) - 2, math.floor(y) + 3):
            for bz in range(math.floor(z) - 2, math.floor(z) + 3):
                if not inside_air(bx, by, bz):
                    best = min(best, dist_box(p, (bx, by, bz, bx + 1, by + 1, bz + 1)))
    return best

def boxes_radio():
    B = []
    for x in range(-2, 3):
        for z in range(-2, 4):
            edge = x in (-2, 2) or z in (-2, 3)
            if edge:
                for y in range(0, 3):
                    if x == 0 and z == -2 and y <= 1: continue
                    B.append((x, y, z, x + 1, y + 1, z + 1))
            B.append((x, 3, z, x + 1, 3.5, z + 1))
    for x in (-2, 2):
        B.append((x + 0.375, 0, -3 + 0.375, x + 0.625, 3, -3 + 0.625))
    for x in range(-2, 3): B.append((x, 3, -3, x + 1, 3.5, -2))
    B += [(-1, 0, 2, 0, 1, 3), (0, 0, 2, 1, 1, 3), (1, 0, -1, 2, 1, 0), (-1, 0, -1, 0, 1, 0), (3, 0, 0, 4, 2, 2), (1.25, 0, -4 + 0.25, 1.75, 1, -4 + 0.75)]
    return B

def boxes_chapel():
    B = []
    for x in range(-3, 4):
        for z in range(-5, 8):
            if x in (-3, 3) or z in (-5, 7):
                for y in range(0, 5):
                    if x == 0 and z == -5 and y <= 1: continue
                    B.append((x, y, z, x + 1, y + 1, z + 1))
    for z in (-3, -1, 1, 3):
        for x in (-2, -1, 1, 2): B.append((x, 0, z, x + 1, 1, z + 1))
    for x in (-2, -1, 1, 2): B.append((x, 0, 5, x + 1, 1.5, 6))
    B += [(-1, 0, 6, 2, 1, 7), (-1, 1, 6, 0, 1.4, 7), (1, 1, 6, 2, 1.4, 7)]
    B.append((-4, 5, -6, 5, 6, 9))  # roof underside (lowest)
    return B


def boxes_pen():
    B = []
    for x in range(3, 12):
        for z in range(-5, 8):
            if x in (3, 11) or z in (-5, 7):
                B.append((x + 0.375, 0, z + 0.375, x + 0.625, 1.5, z + 0.625))
    return B
def boxes_intruder():
    return boxes_cabin("night") + [(3.25, 0, -17 + 0.25, 3.75, 1, -17 + 0.75)]
def boxes_well():
    B = []
    for x in range(-1, 3):
        for z in range(0, 3):
            if not (0 <= x <= 1 and 0 <= z <= 1):
                B.append((x, 0, z, x + 1, 1, z + 1))
    for (x, z) in [(-1, -1), (2, -1), (-1, 2), (2, 2)]:
        B.append((x + 0.375, 0, z + 0.375, x + 0.625, 4, z + 0.625))
    B.append((-2, 4, -2, 4, 4.5, 4))
    B += [(-2, 0, -2, -1, 1, -1), (3, 0, -1, 4, 0.5, 0)]
    return B
def boxes_doorway():
    return [(-0.625, 0, 0.375, -0.375, 2, 0.625), (1.375, 0, 0.375, 1.625, 2, 0.625), (-1, 2, 0, 2, 2.5, 1), (0, 0, 0, 1, 2, 1)]
scenes = {"home": boxes_cabin("dusk"), "camp": boxes_camp(), "pasture": boxes_pen(), "well": boxes_well(), "doorway": boxes_doorway(), "field": [], "intruder": boxes_intruder(),
          "visitor": boxes_cabin("night"), "radio": boxes_radio(), "house": boxes_house(), "chapel": boxes_chapel(),
          "graves": boxes_graves(), "knock": boxes_cabin("night"), "morning": boxes_cabin("morning"), "mine": None}
for name, B in scenes.items():
    P = np.loadtxt(f"{name}.csv", delimiter=",")
    worst, at = 9, -1
    bad = []
    for t, p in enumerate(P):
        q = (p[0], p[1] - 64, p[2])
        if B is None: d = mine_dist(q)
        elif not B: d = 9
        else: d = min(dist_box(q, b) for b in B)
        if d < worst:
            worst, at = d, t
        if d < 0.15:
            bad.append(t)
    print(f"{name}: {len(P)} frames, nearest solid {worst:.2f} at frame {at} ({P[at][0]:.2f},{P[at][1]-64:.2f},{P[at][2]:.2f}); frames under 0.15: {len(bad)}"
          + (f" e.g. {bad[:6]}..{bad[-3:]}" if bad else ""))
