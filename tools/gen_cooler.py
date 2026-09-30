
import json
import math
import os
import struct
import zlib
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft")
BLOCK = os.path.join(ASSETS, "models", "block")
ITEM = os.path.join(ASSETS, "models", "item")
TEX = os.path.join(ASSETS, "textures", "block", "cooler")

P = "#porcelain"
PD = "#porcelain_dark"
PB = "#porcelain_bright"
METAL = "#metal"
JUG = "#jug"
JUG_LIGHT = "#jug_light"
JUG_PALE = "#jug_pale"
RED = "#tap_red"
BLUE = "#tap_blue"
WATER = "#water"


def write_png(path, width, height, rgba):
    raw = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def noise(x, y, salt):
    n = (x * 374761393 + y * 668265263 + salt * 1440662683) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 255.0


def shade_texture(path, width, height, painter, frames=1):
    pixels = bytearray(width * height * 4)
    frame_h = height // frames
    for y in range(height):
        frame = y // frame_h if frames > 1 else 0
        local_y = y % frame_h if frames > 1 else y
        for x in range(width):
            r, g, b, a = painter(x, local_y, frame, width, frame_h)
            i = (y * width + x) * 4
            pixels[i:i + 4] = bytes((r, g, b, a))
    write_png(path, width, height, pixels)


def ceramic(base, salt, spread):
    def paint(x, y, frame, w, h):
        d = int((noise(x, y, salt) - 0.5) * spread)
        spec = 12 if noise(x, y, salt + 4) > 0.88 else 0
        return tuple(max(0, min(255, base[i] + d + spec)) for i in range(3)) + (255,)
    return paint


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    shade_texture(os.path.join(TEX, "jug.png"), 16, 16, ceramic((58, 142, 214), 1, 18))
    shade_texture(os.path.join(TEX, "jug_light.png"), 16, 16, ceramic((126, 196, 242), 2, 14))
    shade_texture(os.path.join(TEX, "jug_pale.png"), 16, 16, ceramic((186, 214, 232), 3, 12))
    shade_texture(os.path.join(TEX, "jug_dark.png"), 16, 16, ceramic((18, 58, 102), 8, 10))
    shade_texture(os.path.join(TEX, "tap_red.png"), 16, 16, ceramic((176, 48, 52), 4, 16))
    shade_texture(os.path.join(TEX, "tap_blue.png"), 16, 16, ceramic((42, 92, 176), 5, 16))

    def water_paint(x, y, frame, w, h):
        band = (x + frame * 3) % w
        base = (168, 214, 242) if band < 3 else (46, 132, 204)
        n = int((noise(x, y + frame, 7) - 0.5) * 14)
        return tuple(max(0, min(255, c + n)) for c in base) + (255,)

    shade_texture(os.path.join(TEX, "water.png"), 16, 64, water_paint, frames=4)
    with open(os.path.join(TEX, "water.png.mcmeta"), "w", encoding="utf-8") as handle:
        json.dump({"animation": {"interpolate": True, "frametime": 4}}, handle, indent=2)


def face(texture, u0, v0, u1, v1):
    return {"texture": texture, "uv": [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]}


def add_box(elements, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None):
    if x1 - x0 < 0.04 or y1 - y0 < 0.04 or z1 - z0 < 0.04:
        return
    up = up or tex
    down = down or tex
    elements.append({
        "name": name,
        "from": [round(v, 3) for v in (x0, y0, z0)],
        "to": [round(v, 3) for v in (x1, y1, z1)],
        "shade": True,
        "faces": {
            "north": face(tex, x0, 16 - y1, x1, 16 - y0),
            "south": face(tex, x0, 16 - y1, x1, 16 - y0),
            "west": face(tex, z0, 16 - y1, z1, 16 - y0),
            "east": face(tex, z0, 16 - y1, z1, 16 - y0),
            "up": face(up, x0, z0, x1, z1),
            "down": face(down, x0, z0, x1, z1),
        },
    })


def oct_solid(elements, name, x0, y0, z0, x1, y1, z1, cut, tex, up=None, down=None):
    cut = min(cut, (x1 - x0) * 0.45, (z1 - z0) * 0.45)
    add_box(elements, name + " miolo", x0 + cut, y0, z0, x1 - cut, y1, z1, tex, up, down)
    add_box(elements, name + " oeste", x0, y0, z0 + cut, x0 + cut + 0.01, y1, z1 - cut, tex, up, down)
    add_box(elements, name + " leste", x1 - cut - 0.01, y0, z0 + cut, x1, y1, z1 - cut, tex, up, down)
    inset = cut * 0.42
    corners = (
        (x0 + inset, z0 + inset, x0 + cut + 0.01, z0 + cut + 0.01),
        (x1 - cut - 0.01, z0 + inset, x1 - inset, z0 + cut + 0.01),
        (x0 + inset, z1 - cut - 0.01, x0 + cut + 0.01, z1 - inset),
        (x1 - cut - 0.01, z1 - cut - 0.01, x1 - inset, z1 - inset),
    )
    for index, (cx0, cz0, cx1, cz1) in enumerate(corners):
        add_box(elements, f"{name} canto {index}", cx0, y0, cz0, cx1, y1, cz1, tex, up, down)


def jug_band(elements, name, x0, y0, z0, x1, y1, z1, wall, tex, window=True):
    add_box(elements, name + " tras", x0, y0, z1 - wall, x1, y1, z1, tex)
    add_box(elements, name + " oeste", x0, y0, z0, x0 + wall, y1, z1, tex)
    add_box(elements, name + " leste", x1 - wall, y0, z0, x1, y1, z1, tex)
    if not window:
        add_box(elements, name + " frente", x0, y0, z0, x1, y1, z0 + wall, tex)
        return
    add_box(elements, name + " frente e", x0, y0, z0, 7.0, y1, z0 + wall, tex)
    add_box(elements, name + " frente d", 9.0, y0, z0, x1, y1, z0 + wall, tex)
    add_box(elements, name + " visor", 7.0, y0, z0 + wall * .45, 9.0, y1, z0 + wall, JUG_PALE)


TEXTURES = {
    "porcelain": "ncat_minecraft:block/toilet/porcelain",
    "porcelain_dark": "ncat_minecraft:block/toilet/porcelain_dark",
    "porcelain_bright": "ncat_minecraft:block/toilet/porcelain_bright",
    "metal": "ncat_minecraft:block/toilet/metal",
    "jug": "ncat_minecraft:block/cooler/jug",
    "jug_light": "ncat_minecraft:block/cooler/jug_light",
    "jug_pale": "ncat_minecraft:block/cooler/jug_pale",
    "jug_dark": "ncat_minecraft:block/cooler/jug_dark",
    "tap_red": "ncat_minecraft:block/cooler/tap_red",
    "tap_blue": "ncat_minecraft:block/cooler/tap_blue",
    "water": "ncat_minecraft:block/cooler/water",
    "particle": "ncat_minecraft:block/toilet/porcelain",
}


def wrap(elements, particle="ncat_minecraft:block/toilet/porcelain"):
    textures = dict(TEXTURES)
    textures["particle"] = particle
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def paddles():
    return build_lever(5.25, 6.95, BLUE) + build_lever(9.05, 10.75, RED)


def build_body():
    elements = []
    for fx, fz in ((2.0, 2.0), (11.6, 2.0), (2.0, 11.6), (11.6, 11.6)):
        add_box(elements, "pe", fx, 0, fz, fx + 2.4, 1.2, fz + 2.4, METAL, PD, METAL)
    oct_solid(elements, "saia", 1.55, 0.85, 1.55, 14.45, 2.35, 14.45, 0.7, METAL, PD, METAL)
    add_box(elements, "painel traseiro", 1.9, 2.15, 5.0, 14.1, 14.85, 14.15, P, PB, PD)
    add_box(elements, "lateral esquerda", 1.9, 2.15, 2.55, 3.7, 14.85, 14.15, P, PB, PD)
    add_box(elements, "lateral direita", 12.3, 2.15, 2.55, 14.1, 14.85, 14.15, P, PB, PD)
    add_box(elements, "frente inferior", 3.7, 2.15, 1.75, 12.3, 4.35, 14.15, P, PB, PD)
    add_box(elements, "frente superior", 3.7, 11.55, 1.75, 12.3, 14.85, 14.15, P, PB, PD)
    add_box(elements, "moldura esquerda", 1.6, 2.15, 1.4, 3.7, 14.95, 3.0, P, PB, PD)
    add_box(elements, "moldura direita", 12.3, 2.15, 1.4, 14.4, 14.95, 3.0, P, PB, PD)
    add_box(elements, "moldura topo", 3.7, 11.55, 1.4, 12.3, 12.1, 3.0, P, PB, PD)
    add_box(elements, "moldura base", 3.7, 3.95, 1.4, 12.3, 4.35, 3.0, P, PB, PD)
    add_box(elements, "fundo do nicho", 3.55, 4.2, 4.75, 12.45, 11.7, 5.05, PD, PD, PD)
    add_box(elements, "bandeja", 3.95, 4.35, 1.5, 12.05, 4.7, 5.1, METAL, PD, METAL)
    for index, x in enumerate((4.6, 5.9, 7.2, 8.5, 9.8, 11.1)):
        add_box(elements, f"ranhura bandeja {index}", x, 4.71, 1.8, x + 0.35, 4.8, 4.75, PD, PD, PD)
    for name, x0, color in (("azul", 4.95, BLUE), ("vermelha", 8.75, RED)):
        add_box(elements, f"torneira {name}", x0, 8.05, 3.75, x0 + 2.3, 10.15, 5.35, METAL, PD, METAL)
        add_box(elements, f"bico {name}", x0 + 0.45, 7.15, 2.85, x0 + 1.85, 8.2, 3.95, METAL, PD, METAL)
        add_box(elements, f"aro torneira {name}", x0 + 0.2, 9.95, 4.05, x0 + 2.1, 10.25, 5.25, color, color, color)
    for index, y in enumerate((5.7, 7.15, 8.6)):
        add_box(elements, f"vent {index}", 1.35, y, 5.4, 2.05, y + 0.7, 10.6, PD, PD, PD)
        add_box(elements, f"vent direita {index}", 13.95, y, 5.4, 14.55, y + 0.7, 10.6, PD, PD, PD)
    add_box(elements, "faixa superior", 3.8, 12.35, 1.63, 12.2, 12.75, 1.8, METAL, PB, METAL)
    oct_solid(elements, "tampa", 1.45, 14.7, 1.45, 14.55, 15.85, 14.55, 0.55, P, PB, PD)
    add_box(elements, "colar", 5.9, 15.55, 5.9, 10.1, 16.0, 10.1, METAL, PD, METAL)
    return elements


def build_gallon():
    elements = []
    add_box(elements, "gargalo", 6.15, 0, 6.15, 9.85, 2.15, 9.85, "#jug_dark", JUG_LIGHT, "#jug_dark")
    oct_solid(elements, "anel do gargalo", 5.7, 1.65, 5.7, 10.3, 2.35, 10.3, .45, JUG_PALE, JUG_LIGHT, JUG)
    jug_band(elements, "reservatorio", 3.55, 2.2, 3.55, 12.45, 12.65, 12.45, .7, JUG_LIGHT)
    add_box(elements, "fundo translucido", 7.0, 2.3, 10.8, 9.0, 12.5, 11.25, JUG_PALE)
    for index, y in enumerate((3.15, 5.35, 7.55, 9.75, 11.75)):
        inset = .12 if index in (0, 4) else 0.0
        jug_band(elements, f"nervura {index}", 3.34 + inset, y, 3.34 + inset,
                 12.66 - inset, y + .38, 12.66 - inset, .72, JUG, True)
    oct_solid(elements, "ombros", 4.0, 12.55, 4.0, 12.0, 13.95, 12.0, .9, JUG_LIGHT, JUG_PALE, JUG)
    oct_solid(elements, "tampa superior", 5.25, 13.8, 5.25, 10.75, 15.0, 10.75, .7, JUG_PALE, JUG_LIGHT, JUG)
    add_box(elements, "reflexo esquerdo", 4.05, 4.0, 3.38, 4.5, 10.9, 3.48, JUG_PALE)
    add_box(elements, "reflexo direito", 11.5, 4.0, 3.38, 11.9, 10.9, 3.48, JUG_PALE)
    return elements


def build_lever(x0, x1, texture):
    elements = []
    add_box(elements, "eixo da alavanca", x0 + .45, 10.1, 3.55, x1 - .45, 10.75, 4.45, METAL, METAL, METAL)
    add_box(elements, "haste da alavanca", x0 + .55, 10.2, 2.45, x1 - .55, 10.63, 4.05, METAL, PB, PD)
    add_box(elements, "pegador da alavanca", x0, 10.05, 1.55, x1, 10.62, 2.85, texture, texture, texture)
    return elements


def build_unit_cube():
    return [{
        "name": "cubo",
        "from": [0, 0, 0],
        "to": [16, 16, 16],
        "shade": True,
        "faces": {side: {"texture": "#water", "uv": [0, 0, 16, 16]} for side in ("north", "south", "east", "west", "up", "down")},
    }]


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def display_block():
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -1.09, 0], "scale": [0.574, 0.574, 0.574]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }


def write_blockstate():
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for gallon in ("false", "true"):
            variants[f"facing={facing},gallon={gallon},half=lower"] = {
                "model": "ncat_minecraft:block/water_cooler_body",
                "y": y,
            }
            model = "water_cooler_gallon" if gallon == "true" else "water_cooler_empty"
            variants[f"facing={facing},gallon={gallon},half=upper"] = {
                "model": f"ncat_minecraft:block/{model}",
                "y": y,
            }
    write_json(os.path.join(ASSETS, "blockstates", "water_cooler.json"), {"variants": variants})


COLORS = {
    "#porcelain": (232, 234, 231),
    "#porcelain_dark": (188, 194, 196),
    "#porcelain_bright": (246, 247, 245),
    "#metal": (104, 108, 112),
    "#jug": (58, 142, 214),
    "#jug_light": (126, 196, 242),
    "#jug_pale": (186, 214, 232),
    "#jug_dark": (18, 58, 102),
    "#tap_red": (176, 48, 52),
    "#tap_blue": (42, 92, 176),
    "#water": (46, 150, 214),
}
LIGHT = {"up": 1.05, "down": 0.46, "north": 0.84, "south": 0.62, "east": 0.72, "west": 0.56}


def render_view(elements, yaw_deg, pitch_deg, size):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    scale = size * 0.46 / 16.0
    color = bytearray([8, 8, 10]) * (size * size)
    depth = [-1e9] * (size * size)
    cos_y, sin_y = math.cos(yaw), math.sin(yaw)
    cos_p, sin_p = math.cos(pitch), math.sin(pitch)

    def project(x, y, z):
        x, y, z = x - 8, y - 16, z - 8
        xz = x * cos_y - z * sin_y
        zz = x * sin_y + z * cos_y
        yy = y * cos_p - zz * sin_p
        return size / 2 + xz * scale, size / 2 - yy * scale, y * sin_p + zz * cos_p

    quads = []
    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        faces = {
            "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
            "south": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
            "west": [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
            "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
            "up": [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
            "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
        }
        for direction, face in element["faces"].items():
            quad = faces[direction]
            base = COLORS[face["texture"]]
            gain = LIGHT[direction]
            quads.append((quad, tuple(max(0, min(255, int(c * gain))) for c in base)))
    for quad, shade in quads:
        pts = [project(*point) for point in quad]
        for tri in ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3])):
            raster(color, depth, size, tri, shade)
    return Image.frombytes("RGB", (size, size), bytes(color))


def raster(color, depth, size, tri, shade):
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
    min_x = max(int(math.floor(min(ax, bx, cx))), 0)
    max_x = min(int(math.ceil(max(ax, bx, cx))), size - 1)
    min_y = max(int(math.floor(min(ay, by, cy))), 0)
    max_y = min(int(math.ceil(max(ay, by, cy))), size - 1)
    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(area) < 1e-3 or min_x > max_x or min_y > max_y:
        return
    shade_b = bytes(shade)
    for y in range(min_y, max_y + 1):
        py = y + 0.5
        row = y * size
        for x in range(min_x, max_x + 1):
            px = x + 0.5
            w0 = (bx - px) * (cy - py) - (cx - px) * (by - py)
            w1 = (cx - px) * (ay - py) - (ax - px) * (cy - py)
            w2 = (ax - px) * (by - py) - (bx - px) * (ay - py)
            inside = w0 <= 0 and w1 <= 0 and w2 <= 0 if area < 0 else w0 >= 0 and w1 >= 0 and w2 >= 0
            if not inside:
                continue
            z = (w0 * az + w1 * bz + w2 * cz) / area
            index = row + x
            if z <= depth[index]:
                continue
            depth[index] = z
            color[index * 3:index * 3 + 3] = shade_b


def save_preview(groups):
    folder = os.path.join(os.path.dirname(__file__), "_preview_cooler")
    os.makedirs(folder, exist_ok=True)
    views = (("frente", 0, 18), ("lado", -90, 16), ("tres-quartos", -40, 28), ("costas", 180, 16))
    for label, elements in groups.items():
        tiles = [render_view(elements, yaw, pitch, 260) for _, yaw, pitch in views]
        sheet = Image.new("RGB", (260 * len(tiles), 260), (0, 0, 0))
        for index, tile in enumerate(tiles):
            sheet.paste(tile, (index * 260, 0))
        sheet.save(os.path.join(folder, f"{label}.png"))


def water_preview(fill):
    top = 21.95 + 8.05 * fill
    return [{
        "name": "agua",
        "from": [4.85, 21.95, 4.5],
        "to": [11.15, round(top, 3), 10.6],
        "shade": True,
        "faces": {side: {"texture": "#water"} for side in ("north", "south", "east", "west", "up", "down")},
    }]


def resize_height(elements, scale, offset):
    resized = []
    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        add_box(resized, element["name"], x0, y0 * scale + offset, z0, x1, y1 * scale + offset, z1,
                element["faces"]["north"]["texture"],
                element["faces"]["up"]["texture"], element["faces"]["down"]["texture"])
    return resized


def split_height(elements):
    lower = []
    upper = []
    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        texture = element["faces"]["north"]["texture"]
        up = element["faces"]["up"]["texture"]
        down = element["faces"]["down"]["texture"]
        if y0 < 16:
            add_box(lower, element["name"], x0, y0, z0, x1, min(y1, 16), z1, texture, up, down)
        if y1 > 16:
            add_box(upper, element["name"], x0, max(y0, 16) - 16, z0, x1, y1 - 16, z1,
                    texture, up, down)
    return lower, upper


def main():
    make_textures()
    body = resize_height(build_body(), 1.27, 0)
    gallon = build_gallon()
    installed_gallon = resize_height(gallon, .78, 20.15)
    body_lower, body_upper = split_height(body)
    _, gallon_upper = split_height(installed_gallon)
    write_json(os.path.join(BLOCK, "water_cooler_body.json"), wrap(body_lower))
    write_json(os.path.join(BLOCK, "water_cooler_gallon.json"), wrap(body_upper + gallon_upper, "ncat_minecraft:block/cooler/jug"))
    write_json(os.path.join(BLOCK, "water_cooler_empty.json"), wrap(body_upper))
    write_json(os.path.join(BLOCK, "water_cooler_lever_red.json"), wrap(resize_height(build_lever(9.05, 10.75, "#tap_red"), 1.27, 0)))
    write_json(os.path.join(BLOCK, "water_cooler_lever_blue.json"), wrap(resize_height(build_lever(5.25, 6.95, "#tap_blue"), 1.27, 0)))
    write_json(os.path.join(BLOCK, "water_cooler_cube.json"), wrap(build_unit_cube(), "ncat_minecraft:block/cooler/water"))
    item_cooler = wrap(body + resize_height(paddles(), 1.27, 0))
    item_cooler["display"] = display_block()
    item_gallon = wrap(gallon, "ncat_minecraft:block/cooler/jug")
    item_gallon["display"] = {
        **display_block(),
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.67, .67, .67]},
    }
    write_json(os.path.join(ITEM, "water_cooler.json"), item_cooler)
    write_json(os.path.join(ITEM, "water_gallon.json"), item_gallon)
    write_blockstate()
    print(f"corpo {len(body)} galao {len(gallon)}")
    if os.environ.get("COOLER_PREVIEW") != "1":
        return
    save_preview({
        "sem-galao": body + resize_height(paddles(), 1.27, 0),
        "cheio": body + resize_height(paddles(), 1.27, 0) + installed_gallon + water_preview(1),
        "baixo": body + resize_height(paddles(), 1.27, 0) + installed_gallon + water_preview(0.18),
    })


if __name__ == "__main__":
    main()
