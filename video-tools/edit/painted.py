"""Painting helpers for the thumbnails, in the way the nightmare photos (photos/nightmare-*.jpg) were made:
the scene is painted from nothing (walls, boards, woods, fog, light), the only thing taken from the game is
it, cut out of a frame shot against flat green ([tape:plates]), and then the whole picture is made into a
bad photograph: smeared, ghosted, fringed, grained, the dark closing in.
Call size(w, h) before painting; everything works on float RGB canvases of that size."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080


def size(w, h):
    global W, H
    W, H = w, h


def grid():
    return np.mgrid[0:H, 0:W].astype(np.float32)


def noise(seed, cells, shape=None, stretch=(1, 1)):
    """Smooth noise in [0, 1]; {stretch} (along y, along x) makes it streak, like wood grain."""
    h, w = shape or (H, W)
    rng = np.random.default_rng(seed)
    small = rng.random((max(2, int(h / cells / stretch[0])), max(2, int(w / cells / stretch[1]))))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, np.float32) / 255.0


def poly_mask(points, blur=0.0):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).polygon([(float(x), float(y)) for x, y in points], fill=255)
    if blur:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, np.float32) / 255.0


def lines_mask(segments, width, blur=1.5):
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    for seg in segments:
        d.line([(float(x), float(y)) for x, y in seg], fill=255, width=int(width), joint="curve")
    if blur:
        m = m.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(m, np.float32) / 255.0


def soften(mask, r):
    return np.asarray(Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255.0


def paint(canvas, mask, colour, amount=1.0):
    m = (mask * amount)[..., None]
    canvas[:] = canvas * (1 - m) + np.asarray(colour, np.float32) * m


def place(canvas, layer, cx, cy, height, tilt=0.0, opacity=1.0, blur=0.0, under=None, flip=False):
    """Draws an RGBA layer onto a float canvas, centred at ({cx}, {cy}), {height} pixels tall, tilted,
    faded, softened; {under} is an optional mask of where it may show."""
    if flip:
        layer = layer.transpose(Image.FLIP_LEFT_RIGHT)
    scale = height / layer.height
    img = layer.resize((max(1, int(layer.width * scale)), max(1, int(height))), Image.LANCZOS)
    if tilt:
        img = img.rotate(tilt, resample=Image.BICUBIC, expand=True)
    if blur:
        img = img.filter(ImageFilter.GaussianBlur(blur))
    a = np.asarray(img, np.float32) / 255.0
    x0, y0 = int(cx - img.width / 2), int(cy - img.height / 2)
    xa, ya = max(0, x0), max(0, y0)
    xb, yb = min(W, x0 + img.width), min(H, y0 + img.height)
    if xb <= xa or yb <= ya:
        return
    part = a[ya - y0:yb - y0, xa - x0:xb - x0]
    al = part[..., 3:4] * opacity
    if under is not None:
        al = al * under[ya:yb, xa:xb, None]
    canvas[ya:yb, xa:xb] = canvas[ya:yb, xa:xb] * (1 - al) + part[..., :3] * al


def plate(path, crop=None, clean=5):
    """It, cut out of a frame shot against flat emerald: RGBA, the green keyed away and its spill taken
    off the edges, only the one shape joined to it kept, its own hollows (eyes, mouth) filled back in,
    cropped tight. {crop} (left, top, right, bottom, fractions of the frame) takes only part of it;
    {clean} is the size of the median filter that takes the game's own static off it."""
    img = Image.open(path).convert("RGB")
    if clean:
        img = img.filter(ImageFilter.MedianFilter(clean))
    a = np.asarray(img, np.float32) / 255.0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    spill = g - np.maximum(r, b)
    share = g / (r + g + b + 0.03)
    alpha = 1.0 - np.maximum(np.clip((spill - 0.06) / 0.10, 0, 1), np.clip((share - 0.42) / 0.06, 0, 1))
    a[..., 1] = np.minimum(g, np.maximum(r, b) + 0.02)
    fg = (spill < 0.03) & (share < 0.40) & (a.max(axis=2) > 0.06)
    alpha = np.minimum(alpha, np.clip(1.0 - spill / 0.05, 0, 1))
    h, w = fg.shape
    m = Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3))
    me = np.asarray(m) == 255
    counts = me[:, w // 4:w * 3 // 4].sum(axis=0)
    sx = int(np.argmax(counts)) + w // 4
    rows = np.nonzero(me[:, sx])[0]
    seed = (int(rows[len(rows) // 2]), sx) if len(rows) else (h // 2, sx)
    ImageDraw.floodfill(m, (int(seed[1]), int(seed[0])), 128)
    grown = Image.fromarray(((np.asarray(m) == 128) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))
    body = (np.asarray(grown) == 255) & fg
    outside = Image.fromarray(np.pad(np.where(body, 0, 255), 1, constant_values=255).astype(np.uint8)).copy()
    ImageDraw.floodfill(outside, (0, 0), 128)
    holes = (np.asarray(outside)[1:-1, 1:-1] == 255) & (spill < 0.03) & (share < 0.40)
    keep = body | holes
    keep_soft = np.asarray(Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))
                           .filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255.0
    alpha = np.where(holes, 1.0, np.minimum(alpha, keep_soft))
    out = Image.fromarray((np.clip(np.concatenate([a, alpha[..., None]], axis=2), 0, 1) * 255).astype(np.uint8), "RGBA")
    if crop:
        out = out.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    box = out.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()
    return out.crop(box) if box else out


def relight(layer, top=0.45, bottom=1.0, gain=1.0, cold=0.15, spot=None):
    """Takes away the flat light it was shot in: a ramp from {top} to {bottom} down it, colder; and
    {spot} = (x, y, radius, strength), x, y and radius as fractions of the layer: a pool of light on it,
    added to the ramp."""
    a = np.asarray(layer, np.float32) / 255.0
    h, w = a.shape[:2]
    ramp = np.linspace(top, bottom, h, dtype=np.float32)[:, None] * np.ones((1, w), np.float32)
    if spot:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        d = np.sqrt(((xx / w - spot[0]) * w / h) ** 2 + (yy / h - spot[1]) ** 2)
        ramp = ramp + spot[3] * np.exp(-(d / spot[2]) ** 2)
    rgb = a[..., :3] * ramp[..., None] * gain
    lum = rgb.mean(axis=2, keepdims=True)
    rgb = rgb * (1 - cold) + lum * cold * np.array([0.9, 0.95, 1.05], np.float32)
    return Image.fromarray((np.clip(np.concatenate([rgb, a[..., 3:4]], axis=2), 0, 1) * 255).astype(np.uint8), "RGBA")


def fogged(layer, colour, amount):
    a = np.asarray(layer, np.float32) / 255.0
    a[..., :3] = a[..., :3] * (1 - amount) + np.asarray(colour, np.float32) * amount
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA")


def trunk(canvas, x, top, bottom, width, tone, fog, seed, lean=0.0):
    """A trunk: rounded (darker at its edges), barked, flaring at the foot, paler the further into the
    fog it stands."""
    rng = np.random.default_rng(seed)
    yy, xx = grid()
    flare = 1.0 + 0.9 * np.clip((yy - (bottom - 60)) / 60.0, 0, 1) ** 2
    cx = x + lean * (yy - bottom)
    half = width / 2 * flare
    d = (xx - cx) / np.maximum(half, 1)
    inside = np.clip((1 - np.abs(d)) * half / 1.5, 0, 1) * (yy >= top) * (yy <= bottom + 4)
    round_shade = 0.55 + 0.45 * np.sqrt(np.clip(1 - d * d, 0, 1))
    bark = noise(seed, 3) * 0.5 + 0.5
    streak = np.asarray(Image.fromarray((rng.random((H // 40 + 1, W // 2 + 1)) * 255).astype(np.uint8))
                        .resize((W, H), Image.BICUBIC), np.float32) / 255.0
    col = tone * round_shade * (0.75 + 0.35 * streak * bark)
    col = col * (1 - fog) + canvas.mean(axis=2) * fog
    paint(canvas, inside, (0, 0, 0), 1.0)
    canvas += (inside * col)[..., None] * np.array([1.0, 0.97, 0.95], np.float32)


def photograph(rgb, seed, fringe=6, ghost=0.25, vignette=0.68, grain=0.05, soft=3.5):
    """Makes a painted scene into a bad photograph of it."""
    rng = np.random.default_rng(seed)
    img = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))
    s = np.asarray(img.filter(ImageFilter.GaussianBlur(soft)), np.float32) / 255.0
    sharp = np.asarray(img.filter(ImageFilter.GaussianBlur(1.0)), np.float32) / 255.0
    a = s * 0.5 + sharp * 0.5
    yy, xx = grid()
    if ghost:
        a = np.maximum(a, np.roll(a, shift=(8, 30), axis=(0, 1)) * ghost)
    u, v = xx / W - 0.5, yy / H - 0.5
    ar = H / W
    edge = np.sqrt(u * u + (v * ar) ** 2 * 1.8)
    wgt = np.clip(edge * 2.2, 0, 1)
    a[..., 0] = a[..., 0] * (1 - wgt) + np.roll(a[..., 0], fringe, axis=1) * wgt
    a[..., 2] = a[..., 2] * (1 - wgt) + np.roll(a[..., 2], -fringe, axis=1) * wgt
    vig = np.clip(1.0 - (np.sqrt(u * u + v * v * 1.35) / vignette) ** 2.3, 0, 1) ** 1.25
    a *= vig[..., None]
    g = rng.normal(0, grain, (H, W)).astype(np.float32)
    a += g[..., None] * (0.35 + 0.65 * np.clip(1 - a.mean(axis=2), 0, 1))[..., None]
    a -= ((np.sin(yy * np.pi / 3.0) * 0.5 + 0.5) * 0.028)[..., None]
    return np.clip(a, 0, 1)


def face_box(layer, pale=0.33):
    """Where its face is in a cut-out: of the pale patches (its eyes and mouth closed over), the one
    that reaches highest, which is its head (its legs, as pale, start lower); as (left, top, right,
    bottom) pixels of the layer."""
    from scipy import ndimage
    a = np.asarray(layer, np.float32) / 255.0
    lum = a[..., :3].mean(-1)
    m = (ndimage.uniform_filter(lum, 5) > pale) & (a[..., 3] > 0.5)
    joined = ndimage.binary_closing(m, iterations=max(3, layer.height // 150))
    lab, n = ndimage.label(joined)
    if n == 0:
        return (0, 0, layer.width, layer.height)
    sizes = ndimage.sum(m, lab, range(1, n + 1))
    big = [i + 1 for i in range(n) if sizes[i] > m.size * 0.002]
    tops = {k: np.nonzero((lab == k).any(axis=1))[0][0] for k in big}
    k = min(tops, key=tops.get)
    ys, xs = np.nonzero(lab == k)
    return (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)


def glints(layer, box, strength=0.95):
    """A pinpoint of light far back in each of its eyes (the two biggest dark patches in the top
    half of its face), as the nightmare photos have."""
    from scipy import ndimage
    a = np.asarray(layer, np.float32) / 255.0
    x0, y0, x1, y1 = box
    sub = a[y0:y0 + (y1 - y0) * 3 // 5, x0:x1]
    dark = (sub[..., :3].mean(-1) < 0.22) & (sub[..., 3] > 0.5)
    lab, n = ndimage.label(dark)
    if n == 0:
        return layer
    sizes = ndimage.sum(dark, lab, range(1, n + 1))
    keep = [int(i) + 1 for i in np.argsort(sizes)[::-1][:2] if sizes[i] > dark.size * 0.004]
    yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]].astype(np.float32)
    r = max(1.5, (x1 - x0) / 90)
    for k in keep:
        cy, cx = ndimage.center_of_mass(lab == k)
        cx, cy = cx + x0 - r * 0.8, cy + y0 - r * 0.6
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        g = np.exp(-(d / r) ** 2) * strength + np.exp(-(d / (r * 4.5)) ** 2) * 0.07 * strength
        a[..., :3] += g[..., None] * np.array([0.9, 0.95, 1.0])
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA")


def place_face(canvas, layer, box, cx, cy, face_h, **kw):
    """Places a cut-out so its face ({box}, from face_box) is {face_h} pixels tall and centred on
    ({cx}, {cy})."""
    x0, y0, x1, y1 = box
    s = face_h / (y1 - y0)
    fx, fy = (x0 + x1) / 2, (y0 + y1) / 2
    place(canvas, layer, cx - (fx - layer.width / 2) * s, cy - (fy - layer.height / 2) * s, layer.height * s, **kw)


def spot_on(box, layer, radius=0.9, strength=0.75):
    """A relight spot (x, y, radius as fractions of the layer; strength) centred on its face."""
    x0, y0, x1, y1 = box
    return ((x0 + x1) / 2 / layer.width, (y0 + y1) / 2 / layer.height, radius * (y1 - y0) / layer.height, strength)
