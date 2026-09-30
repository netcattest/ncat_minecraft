
import json
import math
import os
import struct
import zlib
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft")
TEX = os.path.join(ASSETS, "textures", "block", "alta")

Y, YD, YL = "#shell", "#shell_dark", "#shell_light"
BLK, INK, GOLD = "#black", "#ink", "#gold"


def write_png(path, width, height, rgba):
    raw = b"".join(b"\x00" + rgba[y * width * 4:(y + 1) * width * 4] for y in range(height))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def noise(x, y, salt):
    n = (x * 374761393 + y * 668265263 + salt * 1274126177) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 255.0


def paint(base, salt, spread):
    pixels = bytearray(16 * 16 * 4)
    for y in range(16):
        for x in range(16):
            j = int((noise(x, y, salt) - 0.5) * spread)
            pixels[(y * 16 + x) * 4:(y * 16 + x) * 4 + 4] = bytes(tuple(max(0, min(255, c + j)) for c in base) + (255,))
    return pixels


def add_box(elements, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None):
    if x1 - x0 < 0.04 or y1 - y0 < 0.04 or z1 - z0 < 0.04:
        return
    elements.append({
        "name": name,
        "from": [round(v, 3) for v in (x0, y0, z0)],
        "to": [round(v, 3) for v in (x1, y1, z1)],
        "shade": True,
        "faces": {side: {"texture": tex if side not in ("up", "down") else (up if side == "up" else down) or tex}
                  for side in ("north", "south", "west", "east", "up", "down")},
    })


def rounded(elements, name, x0, y0, z0, x1, y1, z1, cut, tex, up=None, down=None):
    cut = min(cut, (x1 - x0) * 0.4, (z1 - z0) * 0.4)
    add_box(elements, name, x0 + cut, y0, z0, x1 - cut, y1, z1, tex, up, down)
    add_box(elements, name + " e", x0, y0, z0 + cut, x0 + cut, y1, z1 - cut, tex, up, down)
    add_box(elements, name + " d", x1 - cut, y0, z0 + cut, x1, y1, z1 - cut, tex, up, down)


FONT = {
    "A": [(0.0, 0.0, 0.28, 1.65), (0.82, 0.0, 1.1, 1.65), (0.18, 1.32, 0.92, 1.65), (0.18, 0.68, 0.92, 0.96)],
    "L": [(0.0, 0.0, 0.28, 1.65), (0.0, 0.0, 1.02, 0.3)],
    "T": [(0.0, 1.35, 1.12, 1.65), (0.4, 0.0, 0.7, 1.65)],
}


def place_letter(elements, char, left_x, bottom_z):
    for index, (u0, v0, u1, v1) in enumerate(FONT[char]):
        add_box(elements, f"{char}{index}", left_x - u1, 4.55, bottom_z + v0, left_x - u0, 4.82, bottom_z + v1, INK)


def chevron(elements, tip_z, span):
    add_box(elements, "ponta", 7.55, 4.55, tip_z, 8.45, 4.84, tip_z + 0.42, INK)
    add_box(elements, "braco e", 8.35, 4.55, tip_z + 0.32, 8.35 + span, 4.84, tip_z + 0.74, INK)
    add_box(elements, "braco d", 7.65 - span, 4.55, tip_z + 0.32, 7.65, 4.84, tip_z + 0.74, INK)
    add_box(elements, "ponta e", 8.25 + span, 4.55, tip_z + 0.64, 8.25 + span * 1.7, 4.84, tip_z + 1.08, INK)
    add_box(elements, "ponta d", 7.75 - span * 1.7, 4.55, tip_z + 0.64, 7.75 - span, 4.84, tip_z + 1.08, INK)


def antenna(elements, x, z):
    add_box(elements, "base", x - 0.95, 1.15, z - 0.95, x + 0.95, 3.15, z + 0.95, BLK)
    add_box(elements, "colar", x - 0.62, 2.9, z - 0.62, x + 0.62, 4.15, z + 0.62, "#black_dark")
    add_box(elements, "haste", x - 0.4, 3.9, z - 0.4, x + 0.4, 14.7, z + 0.4, BLK)
    add_box(elements, "ponta", x - 0.28, 14.5, z - 0.28, x + 0.28, 15.75, z + 0.28, "#black_dark")


LED_SPECS = (("pwr", 4.55), ("2g", 6.35), ("5g", 8.15), ("lan", 9.95))


def leds(elements, lit):
    add_box(elements, "painel de leds", 4.05, 4.5, 3.25, 11.95, 4.62, 4.6, "#black_dark")
    for name, x in LED_SPECS:
        element_tex = "#led_on" if lit else "#led_off"
        entry = {
            "name": f"led {name}",
            "from": [round(x, 3), 4.6, 3.55],
            "to": [round(x + 1.3, 3), 4.72, 4.3],
            "shade": not lit,
            "faces": {side: {"texture": element_tex} for side in
                      ("north", "south", "east", "west", "up", "down")},
        }
        if lit:
            entry["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        elements.append(entry)


def build(lit=False):
    elements = []
    rounded(elements, "base", 2.15, 0.0, 3.05, 13.85, 1.7, 12.55, 0.7, BLK, "#black_dark", BLK)
    rounded(elements, "corpo", 2.45, 1.35, 3.35, 13.55, 4.55, 12.15, 0.55, Y, YL, YD)
    for groove, y0 in enumerate((2.15, 3.05, 3.85)):
        add_box(elements, f"risco e {groove}", 2.2, y0, 4.4, 2.55, y0 + 0.22, 11.2, YD)
        add_box(elements, f"risco d {groove}", 13.45, y0, 4.4, 13.8, y0 + 0.22, 11.2, YD)
    add_box(elements, "usb caixa", 6.45, 1.65, 2.55, 9.55, 3.35, 3.5, BLK)
    add_box(elements, "usb lingua", 7.15, 2.0, 2.25, 8.85, 3.0, 2.85, GOLD)
    add_box(elements, "rj caixa", 6.55, 1.45, 12.15, 9.45, 3.45, 13.15, BLK)
    add_box(elements, "rj boca", 7.15, 1.85, 12.85, 8.85, 3.1, 13.45, "#black_dark")
    add_box(elements, "rj pinos", 7.4, 2.15, 13.15, 8.6, 2.4, 13.55, GOLD)
    for index, tip in enumerate((6.35, 7.85, 9.35)):
        chevron(elements, tip, 1.15 - index * 0.12)
    add_box(elements, "marca larga", 6.9, 4.55, 5.55, 9.1, 4.86, 6.05, INK)
    add_box(elements, "marca media", 7.3, 4.55, 5.15, 8.7, 4.86, 5.6, INK)
    add_box(elements, "marca ponta", 7.65, 4.55, 4.8, 8.35, 4.86, 5.2, INK)
    left = 11.15
    for char in "ALTA":
        place_letter(elements, char, left, 3.55)
        left -= 1.55
    for x in (3.7, 6.55, 9.45, 12.3):
        antenna(elements, x, 12.35)
    leds(elements, lit)
    return elements


def render_preview(elements):
    size = 280
    color = bytearray([8, 8, 10]) * (size * size)
    depth = [-1e9] * (size * size)
    yaw, pitch = math.radians(-35), math.radians(48)
    scale = size * 0.72 / 16
    cos_y, sin_y, cos_p, sin_p = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    palette = {
        "#shell": (214, 218, 222), "#shell_dark": (168, 174, 180), "#shell_light": (236, 238, 240),
        "#black": (28, 30, 34), "#black_dark": (16, 17, 20), "#ink": (18, 18, 20), "#gold": (196, 146, 48),
        "#led_on": (96, 236, 148), "#led_off": (38, 62, 48),
    }

    def project(x, y, z):
        x, y, z = x - 8, y - 6, z - 8
        xz = x * cos_y - z * sin_y
        zz = x * sin_y + z * cos_y
        yy = y * cos_p - zz * sin_p
        return size / 2 + xz * scale, size / 2 - yy * scale, y * sin_p + zz * cos_p

    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        shade = palette[element["faces"]["up"]["texture"]]
        quads = [
            [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],
            [(x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (x0, y0, z1)],
        ]
        for quad in quads:
            pts = [project(*p) for p in quad]
            raster(color, depth, size, pts, shade)
    path = os.path.join(os.path.dirname(__file__), "_alta_preview.png")
    Image.frombytes("RGB", (size, size), bytes(color)).save(path)


def raster(color, depth, size, pts, shade):
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = pts[0], pts[1], pts[2]
    (dx, dy, dz) = pts[3]
    for tri in (((ax, ay, az), (bx, by, bz), (cx, cy, cz)), ((ax, ay, az), (cx, cy, cz), (dx, dy, dz))):
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
        min_x = max(int(math.floor(min(ax, bx, cx))), 0)
        max_x = min(int(math.ceil(max(ax, bx, cx))), size - 1)
        min_y = max(int(math.floor(min(ay, by, cy))), 0)
        max_y = min(int(math.ceil(max(ay, by, cy))), size - 1)
        area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if abs(area) < 1e-3 or min_x > max_x or min_y > max_y:
            continue
        shade_b = bytes(shade)
        for y in range(min_y, max_y + 1):
            py = y + 0.5
            for x in range(min_x, max_x + 1):
                px = x + 0.5
                w0 = (bx - px) * (cy - py) - (cx - px) * (by - py)
                w1 = (cx - px) * (ay - py) - (ax - px) * (cy - py)
                w2 = (ax - px) * (by - py) - (bx - px) * (ay - py)
                inside = w0 <= 0 and w1 <= 0 and w2 <= 0 if area < 0 else w0 >= 0 and w1 >= 0 and w2 >= 0
                if not inside:
                    continue
                z = (w0 * az + w1 * bz + w2 * cz) / area
                index = y * size + x
                if z <= depth[index]:
                    continue
                depth[index] = z
                color[index * 3:index * 3 + 3] = shade_b


def main():
    os.makedirs(TEX, exist_ok=True)
    write_png(os.path.join(TEX, "shell.png"), 16, 16, paint((214, 218, 222), 1, 14))
    write_png(os.path.join(TEX, "shell_dark.png"), 16, 16, paint((160, 166, 172), 2, 10))
    write_png(os.path.join(TEX, "shell_light.png"), 16, 16, paint((238, 240, 242), 3, 8))
    write_png(os.path.join(TEX, "black.png"), 16, 16, paint((32, 34, 38), 4, 10))
    write_png(os.path.join(TEX, "black_dark.png"), 16, 16, paint((16, 17, 20), 5, 6))
    write_png(os.path.join(TEX, "ink.png"), 16, 16, paint((18, 18, 20), 6, 4))
    write_png(os.path.join(TEX, "gold.png"), 16, 16, paint((206, 154, 52), 7, 12))
    textures = {
        "shell": "ncat_minecraft:block/alta/shell",
        "shell_dark": "ncat_minecraft:block/alta/shell_dark",
        "shell_light": "ncat_minecraft:block/alta/shell_light",
        "black": "ncat_minecraft:block/alta/black",
        "black_dark": "ncat_minecraft:block/alta/black_dark",
        "ink": "ncat_minecraft:block/alta/ink",
        "gold": "ncat_minecraft:block/alta/gold",
        "led_on": "ncat_minecraft:block/pole/led_green_on",
        "led_off": "ncat_minecraft:block/pole/led_green_off",
        "particle": "ncat_minecraft:block/alta/shell",
    }
    elements = build(False)
    lit_elements = build(True)
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    lit_model = {"parent": "minecraft:block/block", "textures": textures, "elements": lit_elements}
    display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 2, 0], "scale": [0.28, 0.28, 0.28]},
        "fixed": {"scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    }
    block_path = os.path.join(ASSETS, "models", "block", "alta_wifi.json")
    item_path = os.path.join(ASSETS, "models", "item", "alta_wifi.json")
    os.makedirs(os.path.dirname(block_path), exist_ok=True)
    lit_path = os.path.join(ASSETS, "models", "block", "alta_wifi_on.json")
    for path, payload in ((block_path, model), (lit_path, lit_model),
                          (item_path, {**lit_model, "display": display})):
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2)
            handle.write("\n")
    variants = {}
    for facing, yrot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit, suffix in (("false", ""), ("true", "_on")):
            variants[f"facing={facing},lit={lit}"] = {
                "model": f"ncat_minecraft:block/alta_wifi{suffix}", "y": yrot}
    with open(os.path.join(ASSETS, "blockstates", "alta_wifi.json"), "w", encoding="utf-8") as handle:
        json.dump({"variants": variants}, handle, indent=2)
        handle.write("\n")
    render_preview(lit_elements)
    print("alta", len(elements))


if __name__ == "__main__":
    main()
