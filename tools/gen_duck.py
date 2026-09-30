
import json
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft")
TEX = os.path.join(ASSETS, "textures", "block", "duck")


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
            color = tuple(max(0, min(255, c + j)) for c in base) + (255,)
            pixels[(y * 16 + x) * 4:(y * 16 + x) * 4 + 4] = bytes(color)
    return pixels


def add_box(elements, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None):
    if x1 - x0 < 0.04 or y1 - y0 < 0.04 or z1 - z0 < 0.04:
        return
    elements.append({
        "name": name,
        "from": [round(v, 3) for v in (x0, y0, z0)],
        "to": [round(v, 3) for v in (x1, y1, z1)],
        "shade": True,
        "faces": {
            "north": {"texture": tex},
            "south": {"texture": tex},
            "west": {"texture": tex},
            "east": {"texture": tex},
            "up": {"texture": up or tex},
            "down": {"texture": down or tex},
        },
    })


def add_front_detail(elements, name, x0, y0, z0, x1, y1, texture):
    elements.append({
        "name": name,
        "from": [round(x0, 3), round(y0, 3), round(z0, 3)],
        "to": [round(x1, 3), round(y1, 3), round(z0 + 0.06, 3)],
        "shade": False,
        "faces": {"north": {"texture": texture}},
    })


OVERLAP = 0.01


def rounded(elements, name, x0, y0, z0, x1, y1, z1, cut, tex, up=None, down=None):
    cut = min(cut, (x1 - x0) * 0.45, (z1 - z0) * 0.45)
    add_box(elements, name, x0 + cut, y0, z0, x1 - cut, y1, z1, tex, up, down)
    add_box(elements, name + " o", x0, y0, z0 + cut, x0 + cut + OVERLAP, y1, z1 - cut, tex, up, down)
    add_box(elements, name + " l", x1 - cut - OVERLAP, y0, z0 + cut, x1, y1, z1 - cut, tex, up, down)
    inset = cut * 0.42
    corners = (
        (x0 + inset, z0 + inset, x0 + cut + OVERLAP, z0 + cut + OVERLAP),
        (x1 - cut - OVERLAP, z0 + inset, x1 - inset, z0 + cut + OVERLAP),
        (x0 + inset, z1 - cut - OVERLAP, x0 + cut + OVERLAP, z1 - inset),
        (x1 - cut - OVERLAP, z1 - cut - OVERLAP, x1 - inset, z1 - inset),
    )
    for index, (cx0, cz0, cx1, cz1) in enumerate(corners):
        add_box(elements, f"{name} canto {index}", cx0, y0, cz0, cx1, y1, cz1, tex, up, down)


def build():
    elements = []
    y, yd, yl = "#yellow", "#yellow_dark", "#yellow_light"

    body = [
        (0.00, 1.10, 4.70, 11.30, 4.60, 11.60, 1.30, yd),
        (1.10, 2.60, 3.55, 12.45, 3.40, 12.60, 1.70, y),
        (2.60, 4.60, 3.10, 12.90, 2.95, 13.00, 1.90, y),
        (4.60, 6.10, 3.75, 12.25, 3.55, 12.45, 1.70, y),
        (6.10, 7.20, 5.00, 11.00, 4.70, 11.20, 1.30, y),
    ]
    for index, (y0, y1, x0, x1, z0, z1, cut, tex) in enumerate(body):
        rounded(elements, f"corpo {index}", x0, y0, z0, x1, y1, z1, cut, tex,
                yl if index >= 3 else y, yd)

    rounded(elements, "peito", 4.60, 2.20, 2.40, 11.40, 5.60, 4.40, 1.10, y, yl, y)
    rounded(elements, "pescoco", 5.60, 7.00, 3.30, 10.40, 8.30, 8.60, 1.10, y, yl, y)

    head = [
        (8.30, 9.60, 5.05, 10.95, 2.55, 8.45, 1.55),
        (9.60, 11.40, 4.85, 11.15, 2.35, 8.65, 1.70),
        (11.40, 12.50, 5.45, 10.55, 3.05, 7.95, 1.40),
        (12.50, 13.10, 6.40, 9.60, 4.05, 6.95, 0.90),
    ]
    for index, (y0, y1, x0, x1, z0, z1, cut) in enumerate(head):
        rounded(elements, f"cabeca {index}", x0, y0, z0, x1, y1, z1, cut, y,
                yl if index >= 2 else y, y)

    add_box(elements, "bico superior", 6.10, 9.55, 0.45, 9.90, 10.45, 2.85, "#beak", "#beak", "#beak_dark")
    add_box(elements, "ponta do bico", 6.65, 9.62, 0.10, 9.35, 10.32, 0.55, "#beak", "#beak", "#beak_dark")
    add_box(elements, "bico inferior", 6.45, 8.95, 0.70, 9.55, 9.60, 2.70, "#beak_dark", "#beak", "#beak_dark")
    add_box(elements, "narina", 7.05, 10.30, 1.05, 8.95, 10.44, 1.85, "#beak_dark", "#beak_dark", "#beak_dark")

    for side, x0, x1 in (("e", 2.70, 3.75), ("d", 12.25, 13.30)):
        rounded(elements, f"asa {side}", x0, 2.85, 5.10, x1, 5.25, 10.30, 0.55, y, yl, yd)
        add_box(elements, f"pena {side}", x0 + 0.10, 3.40, 5.90, x1 - 0.10, 4.05, 9.60, yl, yl, y)

    rounded(elements, "cauda", 6.45, 4.05, 11.70, 9.55, 6.20, 13.60, 0.85, y, yl, yd)
    rounded(elements, "cauda ponta", 6.95, 5.70, 12.60, 9.05, 7.30, 14.10, 0.65, yl, yl, y)

    for side, x0 in (("e", 5.65), ("d", 8.95)):
        add_front_detail(elements, f"olho {side}", x0, 10.75, 2.45, x0 + 1.40, 12.25, "#white")
        add_front_detail(elements, f"pupila {side}", x0 + 0.35, 10.95, 2.38, x0 + 1.10, 11.80, "#black")
        add_front_detail(elements, f"brilho {side}", x0 + 0.75, 11.55, 2.32, x0 + 1.05, 11.90, "#white")
    return elements


def main():
    os.makedirs(TEX, exist_ok=True)
    write_png(os.path.join(TEX, "yellow.png"), 16, 16, paint((244, 196, 28), 1, 16))
    write_png(os.path.join(TEX, "yellow_dark.png"), 16, 16, paint((214, 154, 16), 2, 12))
    write_png(os.path.join(TEX, "yellow_light.png"), 16, 16, paint((255, 224, 86), 3, 10))
    write_png(os.path.join(TEX, "beak.png"), 16, 16, paint((236, 112, 28), 4, 12))
    write_png(os.path.join(TEX, "beak_dark.png"), 16, 16, paint((186, 72, 18), 5, 8))
    write_png(os.path.join(TEX, "white.png"), 16, 16, paint((246, 248, 252), 6, 6))
    write_png(os.path.join(TEX, "black.png"), 16, 16, paint((22, 18, 24), 7, 6))
    textures = {
        "yellow": "ncat_minecraft:block/duck/yellow",
        "yellow_dark": "ncat_minecraft:block/duck/yellow_dark",
        "yellow_light": "ncat_minecraft:block/duck/yellow_light",
        "beak": "ncat_minecraft:block/duck/beak",
        "beak_dark": "ncat_minecraft:block/duck/beak_dark",
        "white": "ncat_minecraft:block/duck/white",
        "black": "ncat_minecraft:block/duck/black",
        "particle": "ncat_minecraft:block/duck/yellow",
    }
    model = {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": build(),
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0.5, 0], "scale": [0.7, 0.7, 0.7]},
            "ground": {"translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "fixed": {"scale": [0.55, 0.55, 0.55]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.45, 0.45, 0.45]},
        },
    }
    block_path = os.path.join(ASSETS, "models", "block", "rubber_duck.json")
    item_path = os.path.join(ASSETS, "models", "item", "rubber_duck.json")
    state_path = os.path.join(ASSETS, "blockstates", "rubber_duck.json")
    os.makedirs(os.path.dirname(block_path), exist_ok=True)
    for path, payload in (
        (block_path, {k: v for k, v in model.items() if k != "display"}),
        (item_path, model),
    ):
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2)
            handle.write("\n")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for water in ("false", "true"):
            variants[f"facing={facing},waterlogged={water}"] = {"model": "ncat_minecraft:block/rubber_duck", "y": y}
    with open(state_path, "w", encoding="utf-8") as handle:
        json.dump({"variants": variants}, handle, indent=2)
        handle.write("\n")
    print("duck", len(model["elements"]))


if __name__ == "__main__":
    main()
