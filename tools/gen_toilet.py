
import json
import math
import os
import struct
import zlib
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft")
MODEL = os.path.join(ASSETS, "models")
TEX = os.path.join(ASSETS, "textures", "block", "toilet")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview")

OVERLAP = 0.05


def write_png(path, width, height, rgba):
    raw = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def shade_texture(path, width, height, painter, animated_frames=1):
    pixels = bytearray(width * height * 4)
    for y in range(height):
        for x in range(width):
            frame = y // (height // animated_frames) if animated_frames > 1 else 0
            local_y = y % (height // animated_frames) if animated_frames > 1 else y
            r, g, b, a = painter(x, local_y, frame, width, height // animated_frames)
            i = (y * width + x) * 4
            pixels[i:i + 4] = bytes((r, g, b, a))
    write_png(path, width, height, pixels)


def noise(x, y, salt):
    n = (x * 374761393 + y * 668265263 + salt * 1440662683) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 255.0


def make_textures():
    os.makedirs(TEX, exist_ok=True)

    def ceramic(base, salt, spread):
        def paint(x, y, frame, w, h):
            n = noise(x, y, salt)
            d = int((n - 0.5) * spread)
            spec = 10 if noise(x, y, salt + 3) > 0.86 else 0
            return (
                max(0, min(255, base[0] + d + spec)),
                max(0, min(255, base[1] + d + spec)),
                max(0, min(255, base[2] + d + spec // 2)),
                255,
            )
        return paint

    shade_texture(os.path.join(TEX, "porcelain.png"), 16, 16, ceramic((232, 234, 231), 1, 14))
    shade_texture(os.path.join(TEX, "porcelain_dark.png"), 16, 16, ceramic((188, 194, 196), 2, 12))
    shade_texture(os.path.join(TEX, "porcelain_bright.png"), 16, 16, ceramic((246, 247, 245), 3, 8))
    shade_texture(os.path.join(TEX, "bowl.png"), 16, 16, ceramic((150, 158, 164), 4, 16))
    shade_texture(os.path.join(TEX, "bowl_deep.png"), 16, 16, ceramic((78, 86, 94), 5, 10))
    shade_texture(os.path.join(TEX, "metal.png"), 16, 16, ceramic((104, 108, 112), 6, 18))

    def water_paint(x, y, frame, w, h):
        shift = frame * 3
        band = (x + shift) % w
        wave = abs(y - (5 + (frame % 3))) 
        base = (118, 156, 172)
        if band < 3 or wave == 0:
            base = (186, 214, 222)
        elif (x + y + frame) % 7 == 0:
            base = (92, 132, 150)
        n = int((noise(x, y + frame, 9) - 0.5) * 12)
        return tuple(max(0, min(255, c + n)) for c in base) + (255,)

    shade_texture(os.path.join(TEX, "water.png"), 16, 64, water_paint, animated_frames=4)
    with open(os.path.join(TEX, "water.png.mcmeta"), "w", encoding="utf-8") as handle:
        json.dump({"animation": {"interpolate": True, "frametime": 5}}, handle, indent=2)

    def shine_paint(x, y, frame, w, h):
        band = (x * 2 + frame * 4) % w
        if band < 4:
            color = (214, 236, 240)
        else:
            color = (150, 188, 200)
        return color + (255,)

    shade_texture(os.path.join(TEX, "water_shine.png"), 16, 64, shine_paint, animated_frames=4)
    with open(os.path.join(TEX, "water_shine.png.mcmeta"), "w", encoding="utf-8") as handle:
        json.dump({"animation": {"interpolate": True, "frametime": 4}}, handle, indent=2)

    shade_texture(os.path.join(TEX, "water_deep.png"), 16, 16, ceramic((64, 104, 120), 8, 10))


def face(texture, u0, v0, u1, v1):
    return {"texture": texture, "uv": [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]}


def add_box(elements, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None, rotation=None):
    if x1 - x0 < 0.04 or y1 - y0 < 0.04 or z1 - z0 < 0.04:
        return
    up = up or tex
    down = down or tex
    element = {
        "name": name,
        "from": [round(x0, 3), round(y0, 3), round(z0, 3)],
        "to": [round(x1, 3), round(y1, 3), round(z1, 3)],
        "shade": True,
        "faces": {
            "north": face(tex, x0, 16 - y1, x1, 16 - y0),
            "south": face(tex, x0, 16 - y1, x1, 16 - y0),
            "west": face(tex, z0, 16 - y1, z1, 16 - y0),
            "east": face(tex, z0, 16 - y1, z1, 16 - y0),
            "up": face(up, x0, z0, x1, z1),
            "down": face(down, x0, z0, x1, z1),
        },
    }
    if rotation:
        element["rotation"] = rotation
    elements.append(element)


def oct_solid(elements, name, x0, y0, z0, x1, y1, z1, cut, tex, up=None, down=None):
    cut = min(cut, (x1 - x0) * 0.45, (z1 - z0) * 0.45)
    add_box(elements, name + " miolo", x0 + cut, y0, z0, x1 - cut, y1, z1, tex, up, down)
    add_box(elements, name + " oeste", x0, y0, z0 + cut, x0 + cut + OVERLAP, y1, z1 - cut, tex, up, down)
    add_box(elements, name + " leste", x1 - cut - OVERLAP, y0, z0 + cut, x1, y1, z1 - cut, tex, up, down)
    inset = cut * 0.42
    corners = (
        (x0 + inset, z0 + inset, x0 + cut + OVERLAP, z0 + cut + OVERLAP),
        (x1 - cut - OVERLAP, z0 + inset, x1 - inset, z0 + cut + OVERLAP),
        (x0 + inset, z1 - cut - OVERLAP, x0 + cut + OVERLAP, z1 - inset),
        (x1 - cut - OVERLAP, z1 - cut - OVERLAP, x1 - inset, z1 - inset),
    )
    for index, (cx0, cz0, cx1, cz1) in enumerate(corners):
        add_box(elements, f"{name} canto {index}", cx0, y0, cz0, cx1, y1, cz1, tex, up, down)


def oct_ring(elements, name, x0, y0, z0, x1, y1, z1, ix0, iz0, ix1, iz1, cut, tex, up=None, down=None):
    cut = min(cut, (x1 - x0) * 0.4, (z1 - z0) * 0.4, ix0 - x0, x1 - ix1, iz0 - z0, z1 - iz1)
    add_box(elements, name + " frente", x0 + cut, y0, z0, x1 - cut, y1, iz0 + OVERLAP, tex, up, down)
    add_box(elements, name + " tras", x0 + cut, y0, iz1 - OVERLAP, x1 - cut, y1, z1, tex, up, down)
    add_box(elements, name + " oeste", x0, y0, z0 + cut, ix0 + OVERLAP, y1, z1 - cut, tex, up, down)
    add_box(elements, name + " leste", ix1 - OVERLAP, y0, z0 + cut, x1, y1, z1 - cut, tex, up, down)
    inset = cut * 0.42
    corners = (
        (x0 + inset, z0 + inset, x0 + cut + OVERLAP, z0 + cut + OVERLAP),
        (x1 - cut - OVERLAP, z0 + inset, x1 - inset, z0 + cut + OVERLAP),
        (x0 + inset, z1 - cut - OVERLAP, x0 + cut + OVERLAP, z1 - inset),
        (x1 - cut - OVERLAP, z1 - cut - OVERLAP, x1 - inset, z1 - inset),
    )
    for index, (cx0, cz0, cx1, cz1) in enumerate(corners):
        add_box(elements, f"{name} canto {index}", cx0, y0, cz0, cx1, y1, cz1, tex, up, down)


def rect_ring(elements, name, x0, y0, z0, x1, y1, z1, ix0, iz0, ix1, iz1, tex, up=None, down=None):
    add_box(elements, name + " frente", x0, y0, z0, x1, y1, iz0 + OVERLAP, tex, up, down)
    add_box(elements, name + " tras", x0, y0, iz1 - OVERLAP, x1, y1, z1, tex, up, down)
    add_box(elements, name + " oeste", x0, y0, iz0, ix0 + OVERLAP, y1, iz1, tex, up, down)
    add_box(elements, name + " leste", ix1 - OVERLAP, y0, iz0, x1, y1, iz1, tex, up, down)


P = "#porcelain"
PD = "#porcelain_dark"
PB = "#porcelain_bright"
BOWL = "#bowl"
DEEP = "#bowl_deep"
METAL = "#metal"
WATER = "#water"
WDEEP = "#water_deep"
SHINE = "#water_shine"


def build_body():
    elements = []
    layers = [
        (0.00, 1.05, 2.9, 13.1, 3.3, 11.7, 1.9),
        (0.95, 2.20, 3.6, 12.4, 3.9, 11.1, 1.7),
        (2.10, 4.90, 4.7, 11.3, 4.55, 10.35, 1.5),
        (4.75, 6.15, 4.1, 11.9, 3.8, 10.7, 1.6),
    ]
    for index, (y0, y1, x0, x1, z0, z1, cut) in enumerate(layers):
        oct_solid(elements, f"pedestal {index}", x0, y0, z0, x1, y1, z1, cut, P, PB if index == 0 else P, PD)

    rings = [
        (5.95, 6.75, 3.8, 2.7, 12.2, 10.3, 6.2, 4.6, 9.8, 8.2, 0.75),
        (6.75, 7.50, 2.5, 1.7, 13.5, 10.55, 5.6, 3.8, 10.4, 8.35, 0.9),
        (7.50, 8.25, 1.7, 1.05, 14.3, 10.7, 5.0, 3.15, 11.0, 8.4, 1.0),
        (8.25, 8.78, 1.35, 0.75, 14.65, 10.55, 3.7, 2.15, 12.3, 7.55, 1.05),
    ]
    for index, spec in enumerate(rings):
        y0, y1, x0, z0, x1, z1, ix0, iz0, ix1, iz1, cut = spec
        oct_ring(elements, f"bacia {index}", x0, y0, z0, x1, y1, z1, ix0, iz0, ix1, iz1, cut, P, PB, PD)

    add_box(elements, "friso frontal da bacia", 2.1, 7.62, 1.0, 13.9, 8.2, 2.0, PB, PB, P)

    oct_ring(
        elements, "assento",
        1.45, 8.7, 0.85, 14.55, 9.38, 8.15,
        3.85, 2.35, 12.15, 6.85,
        1.05, P, PB, PD,
    )
    rect_ring(
        elements, "garganta",
        3.55, 7.7, 2.05, 12.45, 9.1, 7.15,
        4.55, 2.95, 11.45, 6.35,
        BOWL, BOWL, DEEP,
    )
    rect_ring(
        elements, "parede media",
        4.5, 7.25, 2.9, 11.5, 7.8, 6.4,
        5.15, 3.45, 10.85, 5.95,
        BOWL, BOWL, DEEP,
    )
    add_box(elements, "fundo da bacia", 5.05, 7.05, 3.4, 10.95, 7.42, 6.05, DEEP, BOWL, DEEP)
    add_box(elements, "ralo", 7.45, 7.0, 4.45, 8.55, 7.85, 5.4, DEEP, DEEP, DEEP)
    add_box(elements, "ralo interno", 7.7, 6.95, 4.7, 8.3, 7.55, 5.15, DEEP, DEEP, DEEP)

    add_box(elements, "ligacao", 3.8, 6.5, 7.6, 12.2, 9.15, 10.4, P, P, PD)

    oct_solid(elements, "caixa", 1.9, 7.15, 9.35, 14.1, 14.35, 15.45, 0.75, P, P, PD)
    oct_solid(elements, "tampa da caixa", 1.45, 14.18, 9.05, 14.55, 15.62, 15.8, 0.9, P, PB, PD)
    add_box(elements, "friso da caixa", 2.05, 14.0, 9.2, 13.95, 14.28, 15.35, PD, PD, PD)

    add_box(elements, "dobradica esquerda", 1.55, 8.95, 7.15, 3.45, 10.55, 9.05, METAL, METAL, METAL)
    add_box(elements, "dobradica direita", 12.55, 8.95, 7.15, 14.45, 10.55, 9.05, METAL, METAL, METAL)
    add_box(elements, "eixo", 3.3, 9.45, 8.15, 12.7, 10.05, 8.95, METAL, METAL, METAL)

    add_box(elements, "base da alavanca", 0.85, 10.7, 11.55, 2.15, 12.15, 13.45, METAL, METAL, METAL)
    return elements


def build_handle(pressed):
    elements = []
    rotation = None
    y0, y1 = 10.95, 11.82
    if pressed:
        rotation = {"origin": [1.35, 11.4, 12.5], "axis": "z", "angle": 22.5, "rescale": True}
    add_box(elements, "alavanca", 0.05, y0, 11.75, 1.4, y1, 13.2, METAL, METAL, METAL, rotation)
    add_box(elements, "ponta da alavanca", 0.05, y0 - 0.1, 11.9, 0.48, y1 + 0.1, 13.05, PD, PD, PD, rotation)
    return elements


def build_lid_closed():
    elements = []
    oct_solid(elements, "tampa plana", 2.05, 9.23, 1.1, 13.95, 9.96, 7.7, 0.8, P, PB, PD)
    add_box(elements, "borda frontal", 3.05, 9.2, 1.0, 12.95, 9.76, 1.25, PB, PB, P)
    return elements


def upright_frame(elements, name, x0, y0, z0, x1, y1, z1, ix0, iy0, ix1, iy1, tex, up=None, down=None):
    add_box(elements, name + " baixo", x0, y0, z0, x1, iy0, z1, tex, up, down)
    add_box(elements, name + " alto", x0, iy1, z0, x1, y1, z1, tex, up, down)
    add_box(elements, name + " esquerda", x0, iy0, z0, ix0, iy1, z1, tex, up, down)
    add_box(elements, name + " direita", ix1, iy0, z0, x1, iy1, z1, tex, up, down)


def build_lid_open():
    elements = []
    add_box(elements, "painel plano", 3.05, 9.25, 7.45, 12.95, 15.18, 8.36, P, PB, PD)
    add_box(elements, "borda esquerda", 2.65, 9.8, 7.55, 3.05, 14.7, 8.28, P, PB, PD)
    add_box(elements, "borda direita", 12.95, 9.8, 7.55, 13.35, 14.7, 8.28, P, PB, PD)
    add_box(elements, "linha interna", 3.5, 9.7, 7.35, 12.5, 10.0, 7.5, PD, PD, PD)
    add_box(elements, "batente esquerdo", 4.05, 9.25, 7.15, 4.85, 9.9, 7.5, METAL, METAL, METAL)
    add_box(elements, "batente direito", 11.15, 9.25, 7.15, 11.95, 9.9, 7.5, METAL, METAL, METAL)
    return elements


def build_water_full():
    elements = []
    add_box(elements, "agua funda", 4.7, 7.35, 3.1, 11.3, 7.95, 6.2, WDEEP, WDEEP, WDEEP)
    add_box(elements, "agua", 4.6, 7.95, 3.0, 11.4, 8.38, 6.28, WATER, WATER, WDEEP)
    add_box(elements, "brilho", 5.3, 8.38, 3.35, 8.4, 8.52, 5.15, SHINE, SHINE, SHINE)
    return elements


def build_water_low():
    elements = []
    add_box(elements, "agua baixa", 4.9, 7.15, 3.25, 11.1, 7.55, 5.95, WATER, WATER, WDEEP)
    add_box(elements, "jato esquerdo", 4.7, 7.5, 5.95, 5.2, 8.7, 6.4, WATER, SHINE, WDEEP)
    add_box(elements, "jato meio", 7.55, 7.5, 6.05, 8.45, 8.85, 6.45, WATER, SHINE, WDEEP)
    add_box(elements, "jato direito", 10.8, 7.5, 5.95, 11.3, 8.7, 6.4, WATER, SHINE, WDEEP)
    return elements


TEXTURES = {
    "porcelain": "ncat_minecraft:block/toilet/porcelain",
    "porcelain_dark": "ncat_minecraft:block/toilet/porcelain_dark",
    "porcelain_bright": "ncat_minecraft:block/toilet/porcelain_bright",
    "bowl": "ncat_minecraft:block/toilet/bowl",
    "bowl_deep": "ncat_minecraft:block/toilet/bowl_deep",
    "metal": "ncat_minecraft:block/toilet/metal",
    "water": "ncat_minecraft:block/toilet/water",
    "water_deep": "ncat_minecraft:block/toilet/water_deep",
    "water_shine": "ncat_minecraft:block/toilet/water_shine",
    "particle": "ncat_minecraft:block/toilet/porcelain",
}


def wrap(elements):
    return {
        "parent": "minecraft:block/block",
        "textures": TEXTURES,
        "elements": elements,
    }


def write_model(path, elements):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(wrap(elements), handle, indent=2)
        handle.write("\n")


def write_blockstate():
    parts = []
    facings = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
    pieces = (
        ("toilet_body", None),
        ("toilet_lid_closed", ("open", "false")),
        ("toilet_lid_open", ("open", "true")),
        ("toilet_water_full", ("flushing", "false")),
        ("toilet_water_low", ("flushing", "true")),
        ("toilet_handle_rest", ("flushing", "false")),
        ("toilet_handle_flush", ("flushing", "true")),
    )

    def add(when, model, y):
        parts.append({"when": when, "apply": {"model": model, "y": y}})

    for facing, y in facings:
        for half in ("lower", "upper"):
            for name, extra in pieces:
                when = {"facing": facing, "half": half}
                if extra:
                    when[extra[0]] = extra[1]
                add(when, f"ncat_minecraft:block/{name}_{half}", y)

    path = os.path.join(ASSETS, "blockstates", "toilet.json")
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({"multipart": parts}, handle, indent=2)
        handle.write("\n")


COLORS = {
    "#porcelain": (232, 234, 231),
    "#porcelain_dark": (188, 194, 196),
    "#porcelain_bright": (246, 247, 245),
    "#bowl": (150, 158, 164),
    "#bowl_deep": (78, 86, 94),
    "#metal": (104, 108, 112),
    "#water": (118, 156, 172),
    "#water_deep": (64, 104, 120),
    "#water_shine": (186, 216, 224),
}
LIGHT = {"up": 1.05, "down": 0.45, "north": 0.82, "south": 0.62, "east": 0.72, "west": 0.56}


def iter_faces(element):
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    rotation = element.get("rotation")
    corners = {
        "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
        "south": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
        "west": [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
        "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
        "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
        "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
    }
    for direction, quad in corners.items():
        texture = element["faces"][direction]["texture"]
        yield texture, direction, rotate_quad(quad, rotation)


def rotate_quad(quad, rotation):
    if not rotation:
        return quad
    origin = rotation["origin"]
    angle = math.radians(rotation["angle"])
    axis = rotation["axis"]
    cos = math.cos(angle)
    sin = math.sin(angle)
    rotated = []
    for x, y, z in quad:
        x -= origin[0]
        y -= origin[1]
        z -= origin[2]
        if axis == "x":
            y, z = y * cos - z * sin, y * sin + z * cos
        elif axis == "y":
            x, z = x * cos + z * sin, -x * sin + z * cos
        else:
            x, y = x * cos - y * sin, x * sin + y * cos
        rotated.append((x + origin[0], y + origin[1], z + origin[2]))
    return rotated


def render_view(elements, yaw_deg, pitch_deg, size):
    yaw = math.radians(yaw_deg)
    pitch = math.radians(pitch_deg)
    scale = size * 0.78 / 16.0
    ox = oy = size / 2
    color = bytearray([12, 12, 14]) * (size * size)
    depth_buf = [-1e9] * (size * size)
    cos_y, sin_y = math.cos(yaw), math.sin(yaw)
    cos_p, sin_p = math.cos(pitch), math.sin(pitch)

    def project(point):
        x, y, z = point[0] - 8.0, point[1] - 7.4, point[2] - 8.0
        xz = x * cos_y - z * sin_y
        zz = x * sin_y + z * cos_y
        yy = y * cos_p - zz * sin_p
        depth = y * sin_p + zz * cos_p
        return ox + xz * scale, oy - yy * scale, depth

    for element in elements:
        for texture, direction, quad in iter_faces(element):
            projected = [project(point) for point in quad]
            gain = LIGHT[direction]
            shade = tuple(max(0, min(255, int(channel * gain))) for channel in COLORS[texture])
            raster_tri(color, depth_buf, size, projected[0], projected[1], projected[2], shade)
            raster_tri(color, depth_buf, size, projected[0], projected[2], projected[3], shade)
    return Image.frombytes("RGB", (size, size), bytes(color))


def raster_tri(color, depth_buf, size, a, b, c, shade):
    min_x = max(int(math.floor(min(a[0], b[0], c[0]))), 0)
    max_x = min(int(math.ceil(max(a[0], b[0], c[0]))), size - 1)
    min_y = max(int(math.floor(min(a[1], b[1], c[1]))), 0)
    max_y = min(int(math.ceil(max(a[1], b[1], c[1]))), size - 1)
    area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
    if abs(area) < 1e-4 or min_x > max_x or min_y > max_y:
        return
    for y in range(min_y, max_y + 1):
        py = y + 0.5
        row = y * size
        for x in range(min_x, max_x + 1):
            px = x + 0.5
            w0 = (b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)
            w1 = (c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)
            w2 = (a[0] - px) * (b[1] - py) - (b[0] - px) * (a[1] - py)
            inside = (w0 <= 0 and w1 <= 0 and w2 <= 0) if area < 0 else (w0 >= 0 and w1 >= 0 and w2 >= 0)
            if not inside:
                continue
            depth = (w0 * a[2] + w1 * b[2] + w2 * c[2]) / area
            index = row + x
            if depth <= depth_buf[index]:
                continue
            depth_buf[index] = depth
            pixel = index * 3
            color[pixel:pixel + 3] = bytes(shade)


def save_previews(groups):
    os.makedirs(PREVIEW, exist_ok=True)
    views = (
        ("frente", 0, 22),
        ("costas", 180, 18),
        ("lado", -90, 18),
        ("tres-quartos-e", -42, 32),
        ("tres-quartos-d", 40, 30),
        ("cima", 20, 72),
    )
    for label, elements in groups.items():
        tiles = [render_view(elements, yaw, pitch, 300) for _, yaw, pitch in views]
        sheet = Image.new("RGB", (300 * 3, 300 * 2), (0, 0, 0))
        for index, tile in enumerate(tiles):
            sheet.paste(tile, ((index % 3) * 300, (index // 3) * 300))
        sheet.save(os.path.join(PREVIEW, f"{label}.png"))


Y_SCALE = 1.48
XZ_SCALE = 1.14


def scale_axis(value, center):
    return round(center + (value - center) * XZ_SCALE, 3)


def scale_elements(elements):
    scaled = []
    for element in elements:
        copy = json.loads(json.dumps(element))
        x0, y0, z0 = copy["from"]
        x1, y1, z1 = copy["to"]
        copy["from"] = [scale_axis(x0, 8), round(y0 * Y_SCALE, 3), scale_axis(z0, 8)]
        copy["to"] = [scale_axis(x1, 8), round(y1 * Y_SCALE, 3), scale_axis(z1, 8)]
        if "rotation" in copy:
            origin = copy["rotation"]["origin"]
            copy["rotation"]["origin"] = [
                scale_axis(origin[0], 8),
                round(origin[1] * Y_SCALE, 3),
                scale_axis(origin[2], 8),
            ]
        scaled.append(copy)
    return scaled


def split_elements(elements, y0, y1):
    pieces = []
    for element in elements:
        copy = json.loads(json.dumps(element))
        start, end = copy["from"][1], copy["to"][1]
        lo, hi = max(start, y0), min(end, y1)
        if hi - lo < 0.05:
            continue
        span = end - start
        for side in ("north", "south", "west", "east"):
            uv = copy["faces"][side].get("uv")
            if not uv or span < 0.05:
                continue
            def v_at(y, uv=uv, start=start, span=span):
                return uv[3] + (uv[1] - uv[3]) * ((y - start) / span)
            copy["faces"][side]["uv"] = [uv[0], round(v_at(hi), 3), uv[2], round(v_at(lo), 3)]
        copy["from"][1] = round(lo - y0, 3)
        copy["to"][1] = round(hi - y0, 3)
        if "rotation" in copy:
            copy["rotation"]["origin"][1] = round(copy["rotation"]["origin"][1] - y0, 3)
        pieces.append(copy)
    if not pieces:
        pieces.append({
            "name": "vazio",
            "from": [7.8, 0, 7.8],
            "to": [8.2, 0.15, 8.2],
            "shade": True,
            "faces": {side: {"texture": "#porcelain"} for side in ("north", "south", "east", "west", "up", "down")},
        })
    return pieces


def validate(name, elements):
    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        if x0 >= x1 or y0 >= y1 or z0 >= z1:
            raise SystemExit(f"caixa invalida em {name}: {element['name']}")
        for value in (x0, y0, z0, x1, y1, z1):
            if value < -2 or value > 32:
                raise SystemExit(f"fora do bloco em {name}: {element['name']} {value}")
    print(f"{name}: {len(elements)} elementos")


def main():
    make_textures()
    body = build_body()
    lid_closed = build_lid_closed()
    lid_open = build_lid_open()
    water_full = build_water_full()
    water_low = build_water_low()
    handle_rest = build_handle(False)
    handle_flush = build_handle(True)
    groups = {
        "toilet_body": scale_elements(body),
        "toilet_lid_closed": scale_elements(lid_closed),
        "toilet_lid_open": scale_elements(lid_open),
        "toilet_water_full": scale_elements(water_full),
        "toilet_water_low": scale_elements(water_low),
        "toilet_handle_rest": scale_elements(handle_rest),
        "toilet_handle_flush": scale_elements(handle_flush),
    }
    block = os.path.join(MODEL, "block")
    for name, elements in groups.items():
        validate(name, elements)
        write_model(os.path.join(block, f"{name}_lower.json"), split_elements(elements, 0, 16))
        write_model(os.path.join(block, f"{name}_upper.json"), split_elements(elements, 16, 32))

    item_elements = groups["toilet_body"] + groups["toilet_lid_open"] + groups["toilet_water_full"] + groups["toilet_handle_rest"]
    item = wrap(item_elements)
    item["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2.17, 0], "scale": [0.521, 0.521, 0.521]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.28, 0.28, 0.28]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    item_path = os.path.join(MODEL, "item", "toilet.json")
    with open(item_path, "w", encoding="utf-8") as handle:
        json.dump(item, handle, indent=2)
        handle.write("\n")
    write_blockstate()

    if os.environ.get("TOILET_PREVIEW") != "1":
        print("preview ignorado")
        return
    save_previews({
        "aberto": body + lid_open + water_full + handle_rest,
        "fechado": body + lid_closed + water_full + handle_rest,
        "descarga": body + lid_open + water_low + handle_flush,
    })
    print("modelos gravados")


if __name__ == "__main__":
    main()
