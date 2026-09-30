
import math
import os
import sys

import numpy as np
from PIL import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                      "ncat_minecraft", "textures", "block")
OUT = os.path.join(os.path.dirname(__file__), "_preview_cables")

SIDES = 10
LOBES = 5
LOBE_DEPTH = 0.17
LAY_LENGTH = 0.34
MIN_STEPS, MAX_STEPS = 8, 56
LEAD_LENGTH = 0.40
LEAD_RISE = 0.5
ARMOR_BACK = 0.10
ARMOR_LENGTH = 0.40
ARMOR_SCALE = 1.3
CLAMP_BACK = 0.07
CLAMP_LENGTH = 0.10
CLAMP_SCALE = 1.75
BAND_SCALE = 1.5
BAND_WIDTH = 0.035
TIE_BANDS = (0.16, 0.24)
CLAMP_TEXTURE = "pole/galvanized"
STRAND_PITCH = 0.8

COS = [math.cos(math.tau * i / SIDES) for i in range(SIDES)]
SIN = [math.sin(math.tau * i / SIDES) for i in range(SIDES)]

CABLES = {
    "high_voltage": ("acsr", 0.048, 0.018, True),
    "medium_voltage": ("aluminum", 0.034, 0.026, True),
    "low_voltage": ("insulated", 0.031, 0.034, False),
    "copper": ("copper", 0.029, 0.030, True),
}


def vec(x, y, z):
    return np.array([x, y, z], dtype=float)


def normalize(v):
    return v / np.linalg.norm(v)


def catenary(start, end, sag_ratio):
    length = float(np.linalg.norm(end - start))
    steps = max(MIN_STEPS, min(MAX_STEPS, math.ceil(length * 2.2)))
    sag = min(length * sag_ratio, 4.0)
    points = []
    for i in range(steps + 1):
        t = i / steps
        point = start + (end - start) * t
        points.append(vec(point[0], point[1] - 4.0 * sag * t * (1.0 - t), point[2]))
    return points


def termination(anchor, lead, radius):
    step = normalize(lead)
    quads = []
    armor = [anchor - step * ARMOR_BACK, anchor + step * ARMOR_LENGTH]
    quads += [(q, uv, n, None) for q, uv, n in tube(armor, radius * ARMOR_SCALE, 0.07)]
    clamp = [anchor - step * CLAMP_BACK, anchor + step * CLAMP_LENGTH]
    quads += [(q, uv, n, CLAMP_TEXTURE) for q, uv, n in tube(clamp, radius * CLAMP_SCALE, 0.0)]
    for at in TIE_BANDS:
        band = [anchor + step * at, anchor + step * (at + BAND_WIDTH)]
        quads += [(q, uv, n, CLAMP_TEXTURE) for q, uv, n in tube(band, radius * BAND_SCALE, 0.0)]
    return quads


def frame(points, radius):
    side, up = [], []
    for i in range(len(points)):
        ahead = points[min(i + 1, len(points) - 1)]
        behind = points[max(i - 1, 0)]
        tangent = ahead - behind
        if np.dot(tangent, tangent) < 1e-8:
            tangent = vec(1, 0, 0)
        tangent = normalize(tangent)
        lateral = np.cross(tangent, vec(0, 1, 0))
        if np.dot(lateral, lateral) < 1e-6:
            lateral = np.cross(tangent, vec(1, 0, 0))
        s = normalize(lateral) * radius
        side.append(s)
        up.append(normalize(np.cross(s, tangent)) * radius)
    return side, up


def tube(points, radius, lobe):
    side, up = frame(points, radius)
    along = [0.0]
    for i in range(1, len(points)):
        along.append(along[-1] + float(np.linalg.norm(points[i] - points[i - 1])))

    def ring(i, k):
        angle = math.tau * k / SIDES
        swell = 1.0 + lobe * math.cos(LOBES * angle + along[i] / LAY_LENGTH * math.tau)
        return points[i] + side[i] * (COS[k] * swell) + up[i] * (SIN[k] * swell)

    for i in range(len(points) - 1):
        v_a = along[i] / STRAND_PITCH
        v_b = along[i + 1] / STRAND_PITCH
        for k in range(SIDES):
            nxt = (k + 1) % SIDES
            u_a, u_b = k / SIDES, (k + 1) / SIDES
            normal = normalize(side[i] * (COS[k] + COS[nxt]) + up[i] * (SIN[k] + SIN[nxt]))
            quad = [ring(i, k), ring(i + 1, k), ring(i + 1, nxt), ring(i, nxt)]
            uvs = [(u_a, v_a), (u_a, v_b), (u_b, v_b), (u_b, v_a)]
            yield quad, uvs, normal


def lead_out(lead, axis):
    unit = normalize(lead)
    flat = unit - axis * float(np.dot(unit, axis))
    if float(np.dot(flat, flat)) < 1e-4:
        return axis
    return normalize(axis + normalize(flat) * LEAD_RISE)


def span(start, end, lead_start, lead_end, radius, sag_ratio, stranded):
    length = float(np.linalg.norm(end - start))
    axis = (end - start) / length
    out_start = lead_out(lead_start, axis)
    out_end = lead_out(lead_end, -axis)
    lead = min(LEAD_LENGTH, length * 0.14)
    head = start + out_start * lead
    tail = end + out_end * lead
    points = [start] + catenary(head, tail, sag_ratio) + [end]
    lobe = LOBE_DEPTH if stranded else 0.0
    quads = [(q, uv, n, None) for q, uv, n in tube(points, radius, lobe)]
    quads += termination(start, out_start, radius)
    quads += termination(end, out_end, radius)
    return quads


def check_winding(quads):
    for quad, _, normal, _tex in quads:
        cross = np.cross(quad[1] - quad[0], quad[2] - quad[0])
        if np.dot(cross, normal) <= 0.0:
            return False
    return True



def load(name):
    if name not in LOADED:
        LOADED[name] = np.asarray(
            Image.open(os.path.join(ASSETS, name + ".png")).convert("RGB"), dtype=np.float32)
    return LOADED[name]


LOADED = {}


def render(quads, texture, yaw, pitch, scale, size, centre):
    width, height = size
    rows = np.linspace(0.0, 1.0, height)[:, None, None]
    sky = np.array([120, 166, 212]) * (1 - rows) + np.array([208, 224, 238]) * rows
    image = np.broadcast_to(sky, (height, width, 3)).astype(np.float32).copy()
    depth = np.full((height, width), -1e9, dtype=np.float32)
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    forward = vec(math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
    right = normalize(np.cross(forward, vec(0, 1, 0)))
    up = np.cross(right, forward)
    toward = -forward

    def raster(a, b, c, ta, tb, tc, shade, pixels):
        xs, ys = (a[0], b[0], c[0]), (a[1], b[1], c[1])
        x0, x1 = max(int(min(xs)), 0), min(int(max(xs)) + 1, width - 1)
        y0, y1 = max(int(min(ys)), 0), min(int(max(ys)) + 1, height - 1)
        if x0 > x1 or y0 > y1:
            return
        area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
        if abs(area) < 1e-9:
            return
        px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area
        w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        region = depth[y0:y1 + 1, x0:x1 + 1]
        mask = inside & (z > region)
        if not mask.any():
            return
        u = (w0 * ta[0] + w1 * tb[0] + w2 * tc[0]) % 1.0
        v = (w0 * ta[1] + w1 * tb[1] + w2 * tc[1]) % 1.0
        iu = np.clip((u * pixels.shape[1]).astype(int), 0, pixels.shape[1] - 1)
        iv = np.clip((v * pixels.shape[0]).astype(int), 0, pixels.shape[0] - 1)
        region[mask] = z[mask]
        image[y0:y1 + 1, x0:x1 + 1][mask] = pixels[iv, iu][mask] * shade

    light = normalize(vec(-0.4, 0.9, -0.3))
    for quad, uvs, normal, override in quads:
        cross = np.cross(quad[1] - quad[0], quad[2] - quad[0])
        if np.dot(cross, toward) <= 0.0:
            continue
        pixels = load(override if override else texture)
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(normal, light)))
        screen = []
        for point in quad:
            local = point - centre
            screen.append((width / 2 + float(np.dot(local, right)) * scale,
                           height / 2 - float(np.dot(local, up)) * scale,
                           float(np.dot(local, toward))))
        raster(screen[0], screen[1], screen[2], uvs[0], uvs[1], uvs[2], shade, pixels)
        raster(screen[0], screen[2], screen[3], uvs[0], uvs[2], uvs[3], shade, pixels)
    return Image.fromarray(np.clip(image, 0, 255).astype(np.uint8))


def main():
    os.makedirs(OUT, exist_ok=True)
    distance = 26.0
    start = vec(0.0, 10.0, 0.0)
    end = vec(distance, 10.4, 0.0)
    sheet = Image.new("RGB", (460 * len(CABLES), 660), (16, 18, 22))
    ok = True
    lead_start = vec(0.0, 1.0, 0.0)
    lead_end = normalize(vec(0.55, 1.0, 0.0))
    for index, (name, (texture, radius, sag, stranded)) in enumerate(CABLES.items()):
        quads = span(start, end, lead_start, lead_end, radius, sag, stranded)
        if not check_winding(quads):
            print(f"{name}: WINDING IS INSIDE OUT", file=sys.stderr)
            ok = False
        texture = "power/" + texture
        centre = vec(distance / 2, 10.0 - distance * sag * 0.5, 0.0)
        sheet.paste(render(quads, texture, 0, 4, 460 / (distance * 1.25), (460, 330), centre), (index * 460, 0))
        sheet.paste(render(quads, texture, 34, 16, 320.0, (460, 330), start), (index * 460, 330))
        print(f"{name}: {len(quads)} quads, sag {distance * sag:.2f} m over {distance:.0f} m")
    sheet.save(os.path.join(OUT, "spans.png"))
    print("preview em", OUT)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
