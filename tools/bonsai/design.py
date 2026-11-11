"""Bonsai voxel shapes: the same algorithm as BonsaiShapes.java, previewed. x right, y up (0 = soil), z front."""
import math
from PIL import Image, ImageDraw

LOG, LEAF = 1, 2

def h3(x, y, z, s=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (s * 2654435761)
    n &= 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0

class Shape:
    def __init__(self): self.v = {}
    def log(self, x, y, z):
        self.v[(x, y, z)] = LOG
    def leaf(self, x, y, z):
        if (x, y, z) not in self.v: self.v[(x, y, z)] = LEAF
    def limb(self, pts, r0, r1):
        """a trunk or branch through points (x,y,z floats), radius from r0 to r1"""
        total = sum(math.dist(pts[i], pts[i+1]) for i in range(len(pts)-1))
        done = 0.0
        for i in range(len(pts)-1):
            a, b = pts[i], pts[i+1]
            L = math.dist(a, b); steps = max(2, int(L * 4))
            for k in range(steps + 1):
                t = k / steps
                p = [a[j] + (b[j]-a[j]) * t for j in range(3)]
                f = (done + L * t) / max(total, 1e-6)
                r = r0 + (r1 - r0) * f
                R = int(math.ceil(r)) + 1
                for ox in range(-R, R+1):
                    for oy in range(-R, R+1):
                        for oz in range(-R, R+1):
                            bx, by, bz = math.floor(p[0]+ox), math.floor(p[1]+oy), math.floor(p[2]+oz)
                            cx, cy, cz = bx+.5-p[0], by+.5-p[1], bz+.5-p[2]
                            if cx*cx + cy*cy + cz*cz <= r*r + 0.15:
                                self.log(bx, by, bz)
            done += L
    def pad(self, cx, cy, cz, rx, rz, h, seed=0):
        """a foliage pad: flat-ish underneath, domed on top, its edge a little uneven"""
        for k in range(h):
            y = math.floor(cy) + k
            shrink = 1.0 - (k / max(1, h)) ** 1.6 * 0.75
            ax, az = rx * shrink, rz * shrink
            for x in range(math.floor(cx - rx - 1), math.ceil(cx + rx + 1) + 1):
                for z in range(math.floor(cz - rz - 1), math.ceil(cz + rz + 1) + 1):
                    ex, ez = (x + .5 - cx) / ax, (z + .5 - cz) / az
                    d = ex*ex + ez*ez
                    edge = 0.78 + 0.35 * h3(x, y, z, seed)
                    if d <= edge:
                        self.leaf(x, y, z)
    def root_flare(self, r):
        for x in range(-2, 2):
            for z in range(-2, 2):
                cx, cz = x + .5, z + .5
                if cx*cx + cz*cz <= r*r: self.log(x, 0, z)

def upright():
    s = Shape()
    s.root_flare(2.0)
    s.limb([(0, 0, 0), (0, 6, 0), (0.2, 11, 0)], 1.35, 0.55)
    s.limb([(0, 3.5, 0), (-2.5, 4.5, 0.5), (-4, 5, 0.5)], 0.55, 0.5)
    s.limb([(0, 5.5, 0), (2.5, 6.6, -0.5), (3.8, 7, -0.5)], 0.5, 0.5)
    s.limb([(0, 7.5, 0), (-1.5, 8.6, -1.5)], 0.5, 0.5)
    s.pad(-4.2, 5, 0.6, 3.0, 2.4, 2, 1)
    s.pad(3.8, 7, -0.6, 2.7, 2.2, 2, 2)
    s.pad(-1.6, 8.8, -1.6, 2.0, 1.8, 2, 3)
    s.pad(0.2, 10.6, 0, 2.4, 2.2, 3, 4)
    return s

def round_():
    s = Shape()
    s.root_flare(2.0)
    s.limb([(0, 0, 0), (0, 4, 0)], 1.35, 1.0)
    for ang, h in [(0, 7), (2.1, 7.5), (4.2, 7)]:
        s.limb([(0, 3.5, 0), (math.cos(ang)*2.2, h, math.sin(ang)*2.2)], 0.75, 0.5)
    s.pad(0, 6.5, 0, 4.6, 4.0, 4, 5)
    s.pad(0, 9.5, 0, 3.2, 2.8, 2, 6)
    return s

def windswept():
    s = Shape()
    s.root_flare(2.0)
    s.limb([(-0.5, 0, 0), (0.5, 3.5, 0), (2.8, 6.5, 0.2), (5.2, 8.6, 0.2)], 1.4, 0.55)
    s.limb([(0.5, 3.5, 0), (-1.6, 5.2, 0.8), (-3.0, 5.6, 0.8)], 0.55, 0.5)
    s.limb([(2.8, 6.5, 0.2), (3.4, 7.4, -1.8)], 0.5, 0.5)
    s.limb([(2.0, 5.4, 0.2), (3.6, 5.6, 1.8)], 0.5, 0.5)
    s.pad(5.8, 8.4, 0.2, 3.4, 2.5, 3, 7)
    s.pad(3.6, 7.2, -2.0, 2.6, 1.9, 2, 8)
    s.pad(3.9, 5.4, 2.0, 2.4, 1.8, 2, 16)
    s.pad(-3.0, 5.6, 0.8, 2.0, 1.7, 2, 9)
    return s

def cascade():
    s = Shape()
    s.root_flare(1.8)
    s.limb([(0, 0, -1), (0.3, 3, -0.3), (0.4, 3.6, 2.2), (0.3, 2, 4.6), (0, -0.5, 5.6), (-0.2, -1.6, 6.0)], 1.25, 0.5)
    s.pad(0.4, 4, 0.2, 2.6, 2.4, 2, 10)
    s.pad(0.3, 1.2, 4.9, 2.4, 1.8, 2, 11)
    s.pad(-0.2, -2.6, 6.3, 2.6, 2.0, 2, 12)
    return s

def clouds():
    s = Shape()
    s.root_flare(2.0)
    s.limb([(0, 0, 0), (1.3, 3, 0), (-1.1, 6.2, 0.3), (0.5, 9.4, 0)], 1.35, 0.55)
    s.limb([(1.3, 3.2, 0), (3.4, 4.6, 0.6), (4.2, 5.2, 0.6)], 0.55, 0.5)
    s.limb([(-1.1, 6.2, 0.3), (-3.4, 7.2, -0.6)], 0.5, 0.5)
    s.limb([(-0.2, 7.6, 0.1), (1.4, 8.0, -2.2)], 0.5, 0.5)
    s.pad(4.3, 5.2, 0.6, 2.1, 2.1, 3, 13)
    s.pad(-3.7, 7.2, -0.6, 2.1, 2.1, 3, 14)
    s.pad(1.5, 8.0, -2.4, 1.7, 1.7, 2, 17)
    s.pad(0.5, 9.6, 0, 2.5, 2.5, 3, 15)
    return s

SHAPES = [("upright", upright), ("round", round_), ("windswept", windswept), ("cascade", cascade), ("clouds", clouds)]

def preview(path):
    S = 7  # pixels per voxel edge in the projection
    tiles = []
    for name, f in SHAPES:
        v = f().v
        im = Image.new("RGB", (260, 260), (190, 210, 235)); d = ImageDraw.Draw(im)
        # the pot (x -5..5, z -4..4, y -3.5..0.5)
        def proj(x, y, z):
            return (130 + (x - z) * S * 0.87, 170 - y * S + (x + z) * S * 0.5)
        pot = [(-5,.5,-4),(5,.5,-4),(5,.5,4),(-5,.5,4)]
        d.polygon([proj(*p) for p in pot], fill=(60,70,88))
        d.polygon([proj(-5,-3.5,4),proj(5,-3.5,4),proj(5,.5,4),proj(-5,.5,4)], fill=(48,56,72))
        d.polygon([proj(5,-3.5,-4),proj(5,-3.5,4),proj(5,.5,4),proj(5,.5,-4)], fill=(40,46,60))
        d.line([proj(-8,-3.5,6), proj(8,-3.5,6)], fill=(90,140,70))
        order = sorted(v.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1]))
        for (x, y, z), t in order:
            base = (110, 78, 48) if t == LOG else (70, 140, 60)
            top = tuple(min(255, int(c * 1.15)) for c in base)
            left = tuple(int(c * 0.75) for c in base); right = tuple(int(c * 0.6) for c in base)
            d.polygon([proj(x,y+1,z),proj(x+1,y+1,z),proj(x+1,y+1,z+1),proj(x,y+1,z+1)], fill=top)
            d.polygon([proj(x,y,z+1),proj(x+1,y,z+1),proj(x+1,y+1,z+1),proj(x,y+1,z+1)], fill=left)
            d.polygon([proj(x+1,y,z),proj(x+1,y,z+1),proj(x+1,y+1,z+1),proj(x+1,y+1,z)], fill=right)
        lo = min(p[1] for p in v); hi = max(p[1] for p in v)
        xs = [p[0] for p in v]; zs = [p[2] for p in v]
        d.text((4, 4), "%s %d blocks y%d..%d x%d..%d z%d..%d" % (name, len(v), lo, hi, min(xs), max(xs), min(zs), max(zs)), fill=(0,0,0))
        tiles.append(im)
    out = Image.new("RGB", (260 * len(tiles), 260))
    for i, t in enumerate(tiles): out.paste(t, (260 * i, 0))
    out.save(path)

if __name__ == "__main__":
    import sys
    preview(sys.argv[1])
