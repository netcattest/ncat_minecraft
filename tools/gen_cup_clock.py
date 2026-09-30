
import json
import os
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft")
BLOCK = os.path.join(ASSETS, "models", "block")
ITEM = os.path.join(ASSETS, "models", "item")
TEX_CLOCK = os.path.join(ASSETS, "textures", "block", "clock")
TEX_CUP = os.path.join(ASSETS, "textures", "block", "cup")


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
    n = (x * 374761393 + y * 668265263 + salt * 1440662683) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 255.0


def make_textures():
    def stone(dark):
        def paint(x, y):
            n = noise(x // 2, y // 2, 11 if dark else 19)
            if dark:
                base = (78, 78, 82) if n > 0.72 else (46, 46, 50) if n > 0.38 else (30, 30, 34)
            else:
                base = (236, 236, 234) if n > 0.7 else (214, 216, 214) if n > 0.35 else (196, 198, 196)
            j = int((noise(x, y, 4) - 0.5) * 10)
            return tuple(max(0, min(255, c + j)) for c in base) + (255,)
        return paint

    for name, painter in (("case_black", stone(True)), ("case_white", stone(False))):
        pixels = bytearray(16 * 16 * 4)
        for y in range(16):
            for x in range(16):
                pixels[(y * 16 + x) * 4:(y * 16 + x) * 4 + 4] = bytes(painter(x, y))
        write_png(os.path.join(TEX_CLOCK, name + ".png"), 16, 16, pixels)
    screen = bytearray(16 * 16 * 4)
    for i in range(16 * 16):
        screen[i * 4:i * 4 + 4] = bytes((12, 12, 14, 255))
    write_png(os.path.join(TEX_CLOCK, "screen.png"), 16, 16, screen)
    led = bytearray(16 * 16 * 4)
    for i in range(16 * 16):
        led[i * 4:i * 4 + 4] = bytes((255, 36, 28, 255))
    write_png(os.path.join(TEX_CLOCK, "led.png"), 16, 16, led)
    os.makedirs(TEX_CUP, exist_ok=True)
    for name, base in (("paper", (231, 237, 240)), ("rim", (249, 252, 252)),
                       ("inside", (176, 187, 193)), ("blue_band", (28, 126, 194))):
        pixels = bytearray(16 * 16 * 4)
        for y in range(16):
            for x in range(16):
                value = int((noise(x, y, 41 + len(name)) - .5) * 13)
                pixels[(y * 16 + x) * 4:(y * 16 + x) * 4 + 4] = bytes(
                    tuple(max(0, min(255, c + value)) for c in base) + (255,))
        write_png(os.path.join(TEX_CUP, name + ".png"), 16, 16, pixels)


def face(texture):
    return {"texture": texture}


def add_box(elements, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None):
    if x1 - x0 < 0.04 or y1 - y0 < 0.04 or z1 - z0 < 0.04:
        return
    elements.append({
        "name": name,
        "from": [round(v, 3) for v in (x0, y0, z0)],
        "to": [round(v, 3) for v in (x1, y1, z1)],
        "shade": True,
        "faces": {
            "north": face(tex), "south": face(tex), "west": face(tex), "east": face(tex),
            "up": face(up or tex), "down": face(down or tex),
        },
    })


def cup_elements(water):
    elements = []

    def octagonal_wall(name, y0, y1, x0, x1, z0, z1, material, wall=.48, cut=.86):
        add_box(elements, name + " frente", x0 + cut, y0, z0, x1 - cut, y1, z0 + wall, material)
        add_box(elements, name + " trás", x0 + cut, y0, z1 - wall, x1 - cut, y1, z1, material)
        add_box(elements, name + " esquerda", x0, y0, z0 + cut, x0 + wall, y1, z1 - cut, material)
        add_box(elements, name + " direita", x1 - wall, y0, z0 + cut, x1, y1, z1 - cut, material)
        near = wall * .85
        far = cut + wall * .15
        for label, ax0, ax1, az0, az1 in (
                ("noroeste", x0 + near, x0 + far, z0 + near, z0 + far),
                ("nordeste", x1 - far, x1 - near, z0 + near, z0 + far),
                ("sudoeste", x0 + near, x0 + far, z1 - far, z1 - near),
                ("sudeste", x1 - far, x1 - near, z1 - far, z1 - near)):
            add_box(elements, name + " canto " + label, ax0, y0, az0, ax1, y1, az1, material)

    paper = "#paper"
    rim = "#rim"
    steps = 8
    low, high = 4.90, 3.05
    for index in range(steps):
        t0 = index / steps
        t1 = (index + 1) / steps
        y0 = 0.35 + t0 * 10.0
        y1 = 0.35 + t1 * 10.0 + .06
        x0 = low + (high - low) * t0
        x1 = 16.0 - x0
        z0 = x0 + .22
        z1 = 16.0 - z0
        material = "#blue_band" if index in (3, 4) else paper
        octagonal_wall(f"faixa {index}", y0, y1, x0, x1, z0, z1, material, .42, .92)
    octagonal_wall("labio", 10.30, 10.95, high - .28, 16.0 - high + .28,
                   high - .06, 16.0 - high + .06, rim, .58, 1.0)
    add_box(elements, "parede interna frontal", 3.35, 8.20, 3.62, 12.65, 10.35, 3.92, "#inside")
    add_box(elements, "parede interna traseira", 3.35, 8.20, 12.08, 12.65, 10.35, 12.38, "#inside")
    add_box(elements, "parede interna esquerda", 3.35, 8.20, 3.92, 3.65, 10.35, 12.08, "#inside")
    add_box(elements, "parede interna direita", 12.35, 8.20, 3.92, 12.65, 10.35, 12.08, "#inside")
    add_box(elements, "fundo", 4.95, 0.0, 5.15, 11.05, 0.52, 10.85, paper)
    if water:
        add_box(elements, "agua", 3.62, 9.35, 3.88, 12.38, 9.62, 12.12, "#water")
    return elements


CLOCK_Y = 1.34


def scale_clock(elements):
    scaled = []
    for element in elements:
        copy = json.loads(json.dumps(element))
        copy["from"][1] = round(copy["from"][1] * CLOCK_Y, 3)
        copy["to"][1] = round(copy["to"][1] * CLOCK_Y, 3)
        scaled.append(copy)
    return scaled


def clock_elements(case, case_dark, button):
    elements = []
    for fx, fz in ((2.3, 4.4), (12.2, 4.4), (2.3, 10.2), (12.2, 10.2)):
        add_box(elements, "pe", fx, 0, fz, fx + 1.6, 1.05, fz + 1.5, button, button, button)
    add_box(elements, "corpo", 1.15, 0.9, 3.7, 14.85, 6.45, 12.35, case, case, case_dark)
    add_box(elements, "moldura e", 1.15, 1.15, 2.85, 2.55, 6.15, 3.95, case, case, case_dark)
    add_box(elements, "moldura d", 13.45, 1.15, 2.85, 14.85, 6.15, 3.95, case, case, case_dark)
    add_box(elements, "moldura topo", 2.55, 5.45, 2.85, 13.45, 6.15, 3.95, case, case, case_dark)
    add_box(elements, "moldura base", 2.55, 1.15, 2.85, 13.45, 2.2, 3.95, case, case, case_dark)
    add_box(elements, "tela", 2.4, 2.05, 3.55, 13.6, 5.6, 4.15, "#screen", "#screen", "#screen")
    add_box(elements, "botao e", 3.7, 6.35, 6.5, 5.35, 7.35, 8.35, button, button, case_dark)
    add_box(elements, "botao meio", 6.55, 6.45, 6.15, 9.45, 7.55, 8.7, button, button, case_dark)
    add_box(elements, "botao d", 10.65, 6.35, 6.5, 12.3, 7.35, 8.35, button, button, case_dark)
    return elements


def clock_item_digits():
    elements = []
    glyphs = (0b1110111, 0b0010010, 0b1011101)
    digit_height = 3.05 * CLOCK_Y
    base = 2.28 * CLOCK_Y
    thickness = .30

    def segment(name, x0, y0, x1, y1):
        add_box(elements, name, x0, y0, 3.05, x1, y1, 3.2, "#led")

    def digit(x, value):
        original = glyphs[value]
        mask = original & 0b1001001
        mask |= (original & 0b0100000) >> 1
        mask |= (original & 0b0010000) << 1
        mask |= (original & 0b0000100) >> 1
        mask |= (original & 0b0000010) << 1
        w = 1.82
        h = digit_height
        if mask & 0b1000000:
            segment("topo", x + .22, base + h - thickness, x + w - .22, base + h)
        if mask & 0b0100000:
            segment("alto esquerdo", x, base + h * .52, x + thickness, base + h - .08)
        if mask & 0b0010000:
            segment("alto direito", x + w - thickness, base + h * .52, x + w, base + h - .08)
        if mask & 0b0001000:
            segment("meio", x + .22, base + h * .5 - thickness * .5, x + w - .22, base + h * .5 + thickness * .5)
        if mask & 0b0000100:
            segment("baixo esquerdo", x, base + .08, x + thickness, base + h * .48)
        if mask & 0b0000010:
            segment("baixo direito", x + w - thickness, base + .08, x + w, base + h * .48)
        if mask & 0b0000001:
            segment("base", x + .22, base, x + w - .22, base + thickness)

    for x, value in ((10.48, 1), (8.35, 2), (5.55, 0), (3.42, 0)):
        digit(x, value)
    for y in (base + digit_height * .28, base + digit_height * .62):
        segment("dois pontos", 7.55, y, 7.84, y + .29)
    return elements


def wrap(elements, textures, particle):
    data = dict(textures)
    data["particle"] = particle
    return {"parent": "minecraft:block/block", "textures": data, "elements": elements}


PAPER = {
    "paper": "ncat_minecraft:block/cup/paper",
    "rim": "ncat_minecraft:block/cup/rim",
    "inside": "ncat_minecraft:block/cup/inside",
    "blue_band": "ncat_minecraft:block/cup/blue_band",
    "water": "ncat_minecraft:block/cooler/water",
}
CLOCK_BLACK = {
    "case": "ncat_minecraft:block/clock/case_black",
    "case_dark": "ncat_minecraft:block/clock/case_black",
    "button": "ncat_minecraft:block/toilet/metal",
    "screen": "ncat_minecraft:block/clock/screen",
    "led": "ncat_minecraft:block/clock/led",
}
CLOCK_WHITE = {
    "case": "ncat_minecraft:block/clock/case_white",
    "case_dark": "ncat_minecraft:block/toilet/porcelain_dark",
    "button": "ncat_minecraft:block/toilet/porcelain_bright",
    "screen": "ncat_minecraft:block/clock/screen",
    "led": "ncat_minecraft:block/clock/led",
}
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.45, 0.45, 0.45]},
}


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def blockstate(name):
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variants[f"facing={facing}"] = {"model": f"ncat_minecraft:block/{name}", "y": y}
    write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": variants})


def main():
    make_textures()
    cup = wrap(cup_elements(False), PAPER, PAPER["paper"])
    filled = wrap(cup_elements(True), PAPER, PAPER["water"])
    write_json(os.path.join(BLOCK, "paper_cup.json"), cup)
    for model in (cup, filled):
        model["display"] = DISPLAY
    write_json(os.path.join(ITEM, "paper_cup.json"), cup)
    write_json(os.path.join(ITEM, "paper_cup_water.json"), filled)
    clock_body = scale_clock(clock_elements("#case", "#case_dark", "#button"))
    black = wrap(clock_body, CLOCK_BLACK, CLOCK_BLACK["case"])
    white = wrap(clock_body, CLOCK_WHITE, CLOCK_WHITE["case"])
    write_json(os.path.join(BLOCK, "digital_clock.json"), black)
    write_json(os.path.join(BLOCK, "digital_clock_white.json"), white)
    black_item = wrap(clock_body + clock_item_digits(), CLOCK_BLACK, CLOCK_BLACK["case"])
    white_item = wrap(clock_body + clock_item_digits(), CLOCK_WHITE, CLOCK_WHITE["case"])
    black_item["display"] = DISPLAY
    white_item["display"] = DISPLAY
    write_json(os.path.join(ITEM, "digital_clock.json"), black_item)
    write_json(os.path.join(ITEM, "digital_clock_white.json"), white_item)
    blockstate("digital_clock")
    blockstate("digital_clock_white")
    print("cup", len(cup["elements"]), "clock", len(black_item["elements"]))


if __name__ == "__main__":
    main()
