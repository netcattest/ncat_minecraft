import math
import os

from PIL import Image, ImageDraw, ImageFont

import gen_poles as poles
from gen_poles import box, element_json, noise, split, tint, validate, vnoise, write_json

ASSETS = poles.ASSETS
TEX = os.path.join(ASSETS, "textures", "block", "network_switch")
MODELS = os.path.join(ASSETS, "models", "block")
SHAPES_JAVA = os.path.join(poles.ROOT, "src", "main", "java", "com", "netcattest", "ncatminecraft",
                           "block", "SwitchShapes.java")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview_switch")

CHASSIS = "chassis"
CHASSIS_DARK = "chassis_dark"
COVER = "cover"
BEZEL = "bezel"
SOCKET = "socket"
PIN = "pin"
BLACK = "black"
FOOT = "foot"
BUTTON = "button"
VENT = "vent"
SERIAL = "serial"
SFP = "sfp"
TOP = "top"
TOP_MANAGED = "top_managed"
SILK = "silk"
SILK_MANAGED = "silk_managed"
NUMBERS = "numbers"

BODY_Y0 = 0.35
BODY_Y1 = 5.60
COVER_Y1 = 6.20
FRONT = 2.95
BEZEL_BACK = 3.35
BACK = 12.90

PORT_FIRST = 12.20
PORT_PITCH = 1.545
PORT_WIDTH = 1.40
PORT_Y0 = 1.50
PORT_Y1 = 3.30
LED_Y = 3.62
LED_DX = 0.42

BUTTON_X0 = 13.95
BUTTON_X1 = 15.40
BUTTON_Y0 = 1.45
BUTTON_Y1 = 3.05


def texture(painter, size=16):
    image = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            value = painter(x, y)
            image.putpixel((x, y), tuple(int(v) for v in value) if len(value) == 4
                           else tuple(int(v) for v in value) + (255,))
    return image


def brushed(base, salt, horizontal=True):
    def painter(x, y):
        streak = noise(x * 5 if horizontal else x, y if horizontal else y * 5, salt)
        return tint(base, 0.95 + streak * 0.10)
    return painter


def bezel_paint(x, y):
    base = (196, 200, 205)
    if y in (0, 15):
        base = (150, 155, 162)
    return tint(base, 0.97 + noise(x, y, 531) * 0.06)


def socket_paint(x, y):
    base = (10, 12, 16)
    if 4 <= y <= 11 and 2 <= x <= 13:
        base = (6, 8, 11)
    return tint(base, 0.9 + noise(x, y, 533) * 0.14)


def pin_paint(x, y):
    base = (208, 160, 80)
    if y % 3 == 0:
        base = (240, 200, 120)
    return tint(base, 0.94 + noise(x, y, 535) * 0.10)


def foot_paint(x, y):
    return tint((22, 23, 26), 0.9 + vnoise(x, y, 8, 8, 537) * 0.16)


def button_paint(x, y):
    dx, dy = x - 7.5, y - 7.5
    radius = math.sqrt(dx * dx + dy * dy)
    if radius < 4.6:
        return tint((72, 78, 88), 1.0 + noise(x, y, 539) * 0.06)
    return tint((46, 50, 57), 0.96 + noise(x, y, 541) * 0.08)


def vent_paint(x, y):
    slot = (y % 3) == 1 and 1 <= x <= 14
    base = (10, 12, 15) if slot else (58, 63, 71)
    return tint(base, 0.96 + noise(x, y, 543) * 0.07)


def serial_paint(x, y):
    base = (44, 104, 160)
    if x in (0, 15) or y in (0, 15):
        base = (26, 62, 100)
    return tint(base, 0.95 + noise(x, y, 545) * 0.09)


def sfp_paint(x, y):
    base = (54, 60, 70)
    if 4 <= y <= 11:
        base = (14, 18, 23)
    return tint(base, 0.95 + noise(x, y, 547) * 0.09)


def font(size, name="bahnschrift.ttf"):
    for path in (f"C:/Windows/Fonts/{name}", "C:/Windows/Fonts/segoeui.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def top_plate(managed):
    image = Image.new("RGBA", (128, 128), (44, 46, 50, 255))
    draw = ImageDraw.Draw(image)
    for y in range(0, 128, 2):
        draw.line((0, y, 127, y), fill=(41, 43, 47, 255))
    draw.rectangle((0, 0, 127, 5), fill=(62, 65, 70))
    draw.rectangle((0, 122, 127, 127), fill=(30, 32, 35))
    for x in (16, 60, 104):
        draw.rectangle((x, 0, x + 14, 4), fill=(78, 82, 88))
    draw.text((64, 62), "netcattest", font=font(20), fill=(212, 217, 224), anchor="mm")
    draw.line((26, 76, 102, 76), fill=(70, 74, 80), width=1)
    draw.text((64, 88), "Gigabit Switch", font=font(12), fill=(124, 130, 138), anchor="mm")
    return image


def silk_band(managed):
    image = Image.new("RGBA", (256, 32), (40, 42, 46, 255))
    draw = ImageDraw.Draw(image)
    draw.text((8, 16), "netcattest", font=font(15), fill=(230, 234, 240), anchor="lm")
    model = "NC-SG108E" if managed else "NC-SG108"
    draw.text((248, 16), model, font=font(13), fill=(206, 212, 220), anchor="rm")
    return image


def number_band():
    image = Image.new("RGBA", (256, 32), (40, 42, 46, 255))
    draw = ImageDraw.Draw(image)
    draw.text((14, 16), "Power", font=font(11), fill=(198, 204, 212), anchor="lm")
    for index in range(8):
        x = 61 + index * 24.6
        draw.text((x, 16), str(index + 1), font=font(13), fill=(214, 219, 226), anchor="mm")
    return image


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    painters = {
        CHASSIS: brushed((58, 61, 67), 551),
        CHASSIS_DARK: brushed((40, 43, 48), 553),
        COVER: brushed((48, 51, 56), 555),
        BEZEL: bezel_paint,
        SOCKET: socket_paint,
        PIN: pin_paint,
        BLACK: brushed((16, 18, 22), 557),
        FOOT: foot_paint,
        BUTTON: button_paint,
        VENT: vent_paint,
        SERIAL: serial_paint,
        SFP: sfp_paint,
    }
    for name, painter in painters.items():
        texture(painter).save(os.path.join(TEX, f"{name}.png"))
    top_plate(False).save(os.path.join(TEX, f"{TOP}.png"))
    top_plate(True).save(os.path.join(TEX, f"{TOP_MANAGED}.png"))
    silk_band(False).save(os.path.join(TEX, f"{SILK}.png"))
    silk_band(True).save(os.path.join(TEX, f"{SILK_MANAGED}.png"))
    number_band().save(os.path.join(TEX, f"{NUMBERS}.png"))


def payload(label, elements, particle):
    pieces = [piece for element in elements for piece in split(element)]
    json_elements = [element_json(piece, lambda p: p) for piece in pieces]
    validate(label, json_elements)
    textures = {}
    for piece in pieces:
        for tex in piece["faces"].values():
            textures[tex] = tex if ":" in tex else f"ncat_minecraft:block/network_switch/{tex}"
    textures["particle"] = f"ncat_minecraft:block/network_switch/{particle}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": json_elements}


def port_x(index):
    return PORT_FIRST - index * PORT_PITCH


def rj45(out, x):
    x0, x1 = x, x + PORT_WIDTH
    box(out, "blindagem", x0, PORT_Y0, FRONT - 0.18, x1, PORT_Y1, BEZEL_BACK, BEZEL)
    box(out, "alojamento", x0 + 0.16, PORT_Y0 + 0.18, FRONT - 0.28, x1 - 0.16, PORT_Y1 - 0.2,
        FRONT + 0.22, SOCKET)
    box(out, "trava", x0 + 0.48, PORT_Y0 + 0.2, FRONT - 0.34, x1 - 0.48, PORT_Y0 + 0.52,
        FRONT - 0.12, BLACK)
    box(out, "contatos", x0 + 0.3, PORT_Y1 - 0.74, FRONT - 0.34, x1 - 0.3, PORT_Y1 - 0.42,
        FRONT - 0.12, PIN)


def chassis(out, managed):
    box(out, "base", 0.55, BODY_Y0, 3.25, 15.45, BODY_Y0 + 0.35, BACK, CHASSIS_DARK)
    for x in (2.2, 13.8):
        for z in (4.4, 11.6):
            box(out, "pe", x - 0.9, 0.0, z - 0.8, x + 0.9, BODY_Y0, z + 0.8, FOOT)
    box(out, "carcaca", 0.5, BODY_Y0, FRONT, 15.5, BODY_Y1, BACK, CHASSIS,
        sides={"east": VENT, "west": VENT})
    box(out, "tampa", 0.4, BODY_Y1, FRONT - 0.1, 15.6, COVER_Y1, BACK + 0.15, COVER,
        up=TOP_MANAGED if managed else TOP, fit={"up": (16, 16, 0, 0)})
    for x in (3.2, 8.0, 12.8):
        box(out, "aba da tampa", x - 0.9, BODY_Y1 - 0.12, FRONT - 0.14, x + 0.9, COVER_Y1,
            FRONT + 0.1, CHASSIS_DARK)
    box(out, "faixa serigrafada", 0.5, 3.95, FRONT - 0.06, 15.5, 5.35, FRONT + 0.02,
        SILK_MANAGED if managed else SILK, sides={"north": SILK_MANAGED if managed else SILK},
        fit={"north": (0, 0, 16, 16)})
    box(out, "faixa numerica", 0.5, 0.80, FRONT - 0.06, 15.5, 1.42, FRONT + 0.02, NUMBERS,
        sides={"north": NUMBERS}, fit={"north": (0, 0, 16, 16)})
    box(out, "moldura das portas", 1.20, 1.34, FRONT - 0.08, 13.78, 3.86, FRONT + 0.02, BEZEL)
    box(out, "aba inferior", 0.5, BODY_Y0 + 0.3, FRONT - 0.12, 15.5, 0.82, FRONT + 0.02,
        CHASSIS_DARK)
    box(out, "traseira", 0.6, BODY_Y0 + 0.2, BACK - 0.25, 15.4, BODY_Y1 - 0.15, BACK + 0.2,
        CHASSIS_DARK, sides={"south": VENT})
    box(out, "tomada", 2.6, 1.4, BACK + 0.1, 5.4, 3.9, BACK + 0.45, BLACK)
    box(out, "terra", 6.1, 1.6, BACK + 0.1, 7.2, 2.6, BACK + 0.35, PIN)


def controls(out, managed):
    box(out, "alojamento do botao", BUTTON_X0 - 0.30, BUTTON_Y0 - 0.30, FRONT - 0.10,
        BUTTON_X1 + 0.30, BUTTON_Y1 + 0.30, BEZEL_BACK, BLACK)
    box(out, "aro do botao", BUTTON_X0 - 0.22, BUTTON_Y0 - 0.22, FRONT - 0.34,
        BUTTON_X1 + 0.22, BUTTON_Y1 + 0.22, FRONT - 0.04, BEZEL)
    box(out, "botao", BUTTON_X0, BUTTON_Y0, FRONT - 0.56, BUTTON_X1, BUTTON_Y1, FRONT - 0.26, BUTTON)
    box(out, "chanfro do botao", BUTTON_X0 + 0.16, BUTTON_Y0 + 0.16, FRONT - 0.66,
        BUTTON_X1 - 0.16, BUTTON_Y1 - 0.16, FRONT - 0.52, BUTTON)
    centre_x = (BUTTON_X0 + BUTTON_X1) / 2
    centre_y = (BUTTON_Y0 + BUTTON_Y1) / 2
    box(out, "simbolo anel", centre_x - 0.42, centre_y - 0.46, FRONT - 0.70,
        centre_x + 0.42, centre_y + 0.34, FRONT - 0.64, BEZEL)
    box(out, "simbolo interno", centre_x - 0.20, centre_y - 0.28, FRONT - 0.72,
        centre_x + 0.20, centre_y + 0.16, FRONT - 0.66, BUTTON)
    box(out, "simbolo haste", centre_x - 0.11, centre_y - 0.06, FRONT - 0.72,
        centre_x + 0.11, centre_y + 0.52, FRONT - 0.64, BEZEL)
    box(out, "moldura do led de forca", (BUTTON_X0 + BUTTON_X1) / 2 - 0.42, LED_Y - 0.34,
        FRONT - 0.1, (BUTTON_X0 + BUTTON_X1) / 2 + 0.42, LED_Y + 0.34, FRONT + 0.02, BLACK)
    for index in range(8):
        centre = port_x(index) + PORT_WIDTH / 2
        for offset in (-LED_DX, LED_DX):
            box(out, "moldura do led", centre + offset - 0.26, LED_Y - 0.26, FRONT - 0.08,
                centre + offset + 0.26, LED_Y + 0.26, FRONT + 0.02, BLACK)
    if managed:
        box(out, "aro do console", 15.22, 2.70, 5.45, 15.56, 5.20, 8.75, BEZEL)
        box(out, "console", 15.52, 3.00, 5.80, 15.74, 4.90, 8.40, SERIAL)
        box(out, "trava do console", 15.72, 3.08, 6.60, 15.84, 3.42, 7.60, BLACK)
        box(out, "contatos do console", 15.70, 4.30, 6.20, 15.80, 4.62, 8.00, PIN)
        box(out, "etiqueta console", 15.20, 2.30, 5.80, 15.30, 2.62, 8.40, BLACK)
        box(out, "gaiola sfp", 0.6, 4.05, BACK - 0.05, 2.8, 5.25, BACK + 0.4, SFP)


def build(managed):
    out = []
    chassis(out, managed)
    for index in range(8):
        rj45(out, port_x(index))
    controls(out, managed)
    return out


def write_shapes():
    centres = [port_x(index) + PORT_WIDTH / 2 for index in range(8)]
    middle = (PORT_Y0 + PORT_Y1) / 2
    lines = ["package com.netcattest.ncatminecraft.block;", "",
             "public final class SwitchShapes {", "",
             "    private SwitchShapes() {", "    }", "",
             "    public static final double[] PORT_X = {"
             + ", ".join(f"{x:.3f}D" for x in centres) + "};",
             "    public static final double[] PORT_Y = {"
             + ", ".join(f"{middle:.3f}D" for _ in centres) + "};",
             f"    public static final double LED_Y = {LED_Y:.3f}D;",
             f"    public static final double LED_DX = {LED_DX:.3f}D;",
             f"    public static final double LED_Z = {FRONT - 0.14:.3f}D;",
             f"    public static final double PORT_Z = {FRONT - 0.22:.3f}D;",
             "    public static final double[] POWER_LED = {"
             f"{(BUTTON_X0 + BUTTON_X1) / 2:.3f}D, {LED_Y:.3f}D}};",
             f"    public static final double PORT_HALF_X = {PORT_WIDTH / 2:.3f}D;",
             f"    public static final double PORT_HALF_Y = {(PORT_Y1 - PORT_Y0) / 2:.3f}D;",
             f"    public static final double[] POWER_BUTTON = {{{BUTTON_X0 - 0.2:.3f}D, "
             f"{BUTTON_Y0 - 0.2:.3f}D, {BUTTON_X1 + 0.2:.3f}D, {BUTTON_Y1 + 0.2:.3f}D}};",
             "}"]
    with open(SHAPES_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


def main():
    make_textures()
    for name, managed in (("network_switch", False), ("managed_switch", True)):
        elements = build(managed)
        write_json(os.path.join(MODELS, f"{name}.json"), payload(name, elements, CHASSIS))
        print(f"{name}: {len(elements)} elementos")
    write_shapes()
    if os.environ.get("SWITCH_PREVIEW") == "1":
        save_preview()


def save_preview():
    import shutil
    import tempfile
    os.makedirs(PREVIEW, exist_ok=True)
    merged = tempfile.mkdtemp(prefix="ncat_switch_tex_")
    for folder in (poles.TEX, TEX):
        for entry in os.listdir(folder):
            if entry.endswith(".png"):
                shutil.copyfile(os.path.join(folder, entry), os.path.join(merged, entry))
    original = poles.TEX
    poles.TEX = merged
    try:
        tiles = []
        for name in ("network_switch", "managed_switch"):
            elements = poles.load_elements(os.path.join(MODELS, f"{name}.json"), 0.0)
            for yaw, pitch in ((0, 6), (-30, 24), (-78, 12)):
                tiles.append(poles.render(elements, yaw, pitch, 27.0, (460, 340), (8.0, 3.2, 7.0)))
        sheet = Image.new("RGB", (460 * 3, 340 * 2), (16, 18, 22))
        for index, tile in enumerate(tiles):
            sheet.paste(tile, ((index % 3) * 460, (index // 3) * 340))
        sheet.save(os.path.join(PREVIEW, "switches.png"))
        print("preview em", PREVIEW)
    finally:
        poles.TEX = original
        shutil.rmtree(merged, ignore_errors=True)


if __name__ == "__main__":
    main()
