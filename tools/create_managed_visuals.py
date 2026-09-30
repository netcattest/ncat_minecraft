import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ncat_minecraft"
MODELS = ASSETS / "models"
TEXTURES = ASSETS / "textures/block"


def font(size, bold=False):
    candidates = [
        Path("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
    ]
    for path in candidates:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def material(name, rgb):
    image = Image.new("RGBA", (16, 16))
    px = image.load()
    for y in range(16):
        for x in range(16):
            n = ((x * 37 + y * 19 + x * y * 7) % 11) - 5
            edge = -8 if x in (0, 15) or y in (0, 15) else 0
            px[x, y] = tuple(max(0, min(255, c + n + edge)) for c in rgb) + (255,)
    image.save(TEXTURES / name)


def cube(name, xyz0, xyz1, texture, top=None, front=None, rotation=None):
    faces = {face: {"texture": "#" + texture} for face in
             ("north", "south", "east", "west", "up", "down")}
    if top:
        faces["up"] = {"texture": "#" + top}
    if front:
        faces["north"] = {"texture": "#" + front}
    obj = {"name": name, "from": list(xyz0), "to": list(xyz1), "faces": faces}
    if rotation:
        obj["rotation"] = rotation
    return obj


def front_labels():
    result = []
    for index, x in enumerate((2.15, 5.7, 9.25, 12.8, 2.15, 5.7, 9.25, 12.8)):
        row = index // 4
        y = 6.93 if row == 0 else 3.70
        tex = [float((index % 4) * 4), float(row), float((index % 4 + 1) * 4), float(row + 1)]
        result.append({
            "name": f"eth{index} label",
            "from": [round(x - 1.23, 3), y, 0.09],
            "to": [round(x + 1.23, 3), y + 0.36, 0.13],
            "faces": {"north": {"texture": "#labels", "uv": tex}},
        })
    return result


def label_texture(path):
    image = Image.new("RGBA", (256, 256), (12, 20, 31, 255))
    d = ImageDraw.Draw(image)
    face = font(11, True)
    for index in range(8):
        col, row = index % 4, index // 4
        x, y = col * 64, row * 16
        d.rounded_rectangle((x + 2, y + 1, x + 62, y + 15), radius=2,
                            fill=(20, 33, 47), outline=(56, 109, 131))
        text = f"eth{index}"
        box = d.textbbox((0, 0), text, font=face)
        d.text((x + (64 - (box[2] - box[0])) / 2, y - 1), text,
               font=face, fill=(191, 225, 236))
    image.save(path)


def managed_top(path):
    image = Image.new("RGB", (128, 128), (13, 24, 37))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((3, 3, 124, 124), radius=7, fill=(17, 29, 44), outline=(69, 113, 145), width=2)
    d.line((11, 30, 117, 30), fill=(42, 177, 210), width=2)
    d.text((11, 8), "NCAT", font=font(18, True), fill=(229, 245, 250))
    d.text((12, 35), "ETH0-ETH7", font=font(13, True), fill=(69, 204, 233))
    d.text((12, 53), "SWITCH", font=font(13, True), fill=(217, 231, 243))
    d.text((12, 75), "8 x RJ45  /  SERIAL", font=font(7, True), fill=(137, 174, 195))
    for x in range(12, 117, 8):
        for y in (94, 99, 104):
            d.rectangle((x, y, x + 3, y + 1), fill=(41, 64, 79))
    d.line((12, 113, 116, 113), fill=(40, 80, 105), width=1)
    d.text((40, 115), "netcattest", font=font(7), fill=(111, 182, 203))
    image.save(path)


def tablet_screen(path):
    size = 256
    image = Image.new("RGB", (size, size), (5, 13, 22))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((2, 2, 253, 253), radius=9,
                        fill=(7, 20, 34), outline=(38, 129, 164), width=3)
    d.rectangle((6, 7, 250, 42), fill=(12, 34, 54))
    d.rectangle((6, 41, 250, 43), fill=(53, 186, 217))
    d.text((18, 13), "NCAT", font=font(20, True), fill=(232, 246, 250))
    d.ellipse((229, 20, 238, 29), fill=(50, 196, 220))
    for index in range(4):
        x = 15 + index * 59
        d.line((x, 67, x + 46, 67), fill=(36, 79, 102), width=2)
    d.rounded_rectangle((14, 78, 241, 108), radius=4,
                        fill=(15, 39, 56), outline=(38, 80, 102))
    d.text((23, 84), "ETHERNET PORTS", font=font(11, True), fill=(225, 240, 246))
    d.text((23, 99), "8 x RJ45  |  SERIAL CONSOLE", font=font(8), fill=(119, 157, 178))
    for index in range(8):
        col, row = index % 4, index // 4
        x, y = 16 + col * 59, 121 + row * 44
        d.rounded_rectangle((x, y, x + 52, y + 35), radius=4,
                            fill=(12, 31, 47), outline=(55, 103, 125))
        d.rectangle((x + 7, y + 7, x + 13, y + 13), fill=(44, 173, 204))
        d.text((x + 19, y + 5), f"eth{index}", font=font(10, True), fill=(217, 232, 240))
        d.line((x + 7, y + 25, x + 44, y + 25), fill=(32, 77, 100), width=2)
    d.line((17, 217, 238, 217), fill=(40, 99, 125), width=2)
    d.text((18, 225), "NCAT MINECRAFT", font=font(9, True), fill=(119, 190, 211))
    d.text((180, 225), "netcattest", font=font(8), fill=(113, 151, 170))
    image.save(path)


def create_switch():
    material("managed_switch/serial.png", (5, 13, 21))


def tablet_body(rotation):
    return [
        cube("tablet back", (1.75, 3.05, 1.9), (14.25, 3.42, 14.1), "casing", rotation=rotation),
        cube("tablet chassis", (1.45, 3.38, 1.6), (14.55, 3.80, 14.4), "edge", rotation=rotation),
        cube("tablet rim", (1.62, 3.78, 1.76), (14.38, 3.95, 14.24), "rim", rotation=rotation),
        cube("glass bezel", (1.95, 3.93, 2.10), (14.05, 4.05, 13.90), "rubber", rotation=rotation),
        cube("active display", (2.30, 4.04, 2.55), (13.70, 4.09, 13.45), "rubber",
             top="screen", rotation=rotation),
        cube("camera", (7.55, 4.02, 2.15), (8.45, 4.10, 2.48), "serial", rotation=rotation),
        cube("speaker", (6.20, 4.02, 13.58), (9.80, 4.08, 13.82), "edge", rotation=rotation),
        cube("power button", (11.10, 3.42, 1.50), (12.60, 3.74, 1.72), "rim", rotation=rotation),
        cube("volume button", (3.40, 3.42, 1.50), (5.60, 3.74, 1.72), "rim", rotation=rotation),
        cube("brand plate", (6.40, 2.96, 6.60), (9.60, 3.08, 9.40), "rim", rotation=rotation),
    ]


def flat_tablet():
    return [
        cube("tablet back", (1.75, 5.60, 1.70), (14.25, 6.05, 14.30), "casing"),
        cube("tablet chassis", (1.45, 5.95, 1.40), (14.55, 6.45, 14.60), "edge"),
        cube("tablet rim", (1.62, 6.42, 1.58), (14.38, 6.62, 14.42), "rim"),
        cube("glass bezel", (1.95, 6.58, 1.95), (14.05, 6.74, 14.05), "rubber"),
        cube("active display", (2.30, 6.72, 2.45), (13.70, 6.80, 13.55), "rubber", top="screen"),
        cube("camera", (7.55, 6.72, 2.02), (8.45, 6.82, 2.38), "serial"),
        cube("speaker", (6.20, 6.72, 13.62), (9.80, 6.80, 13.88), "edge"),
        cube("power button", (11.10, 6.02, 1.28), (12.60, 6.38, 1.48), "rim"),
        cube("volume button", (3.40, 6.02, 1.28), (5.60, 6.38, 1.48), "rim"),
        cube("brand plate", (6.40, 5.46, 6.60), (9.60, 5.60, 9.40), "rim"),
        cube("camera bump", (10.60, 5.30, 2.40), (12.80, 5.62, 4.60), "edge"),
        cube("camera lens", (11.20, 5.16, 3.00), (12.20, 5.34, 4.00), "serial"),
    ]


def create_tablet():
    rotation = {"origin": [8, 4, 8], "axis": "x", "angle": -22.5}
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "casing": "ncat_minecraft:block/managed_tablet/casing",
            "rim": "ncat_minecraft:block/managed_tablet/rim",
            "edge": "ncat_minecraft:block/managed_tablet/edge",
            "rubber": "ncat_minecraft:block/managed_tablet/rubber",
            "screen": "ncat_minecraft:block/managed_tablet/screen",
            "serial": "ncat_minecraft:block/managed_switch/serial",
            "pin": "ncat_minecraft:block/network_switch/pin",
            "particle": "ncat_minecraft:block/managed_tablet/casing",
        },
        "elements": [
            cube("weighted dock", (1.2, 0.23, 2.0), (14.8, 1.15, 14.0), "casing", top="rim"),
            cube("dock front lip", (1.4, 1.1, 2.0), (14.6, 1.42, 3.3), "edge"),
            cube("left support", (3.1, 1.0, 9.6), (4.25, 4.35, 10.6), "edge"),
            cube("right support", (11.75, 1.0, 9.6), (12.9, 4.35, 10.6), "edge"),
            cube("rear hinge", (2.7, 1.0, 11.45), (13.3, 2.0, 12.55), "rim"),
            *tablet_body(rotation),
            cube("console boss", (13.35, 1.10, 5.90), (14.95, 3.05, 10.10), "casing", top="rim"),
            cube("console bevel", (13.55, 2.95, 6.10), (14.95, 3.16, 9.90), "edge"),
            cube("serial jack rim", (14.90, 1.35, 6.40), (15.35, 2.85, 9.60), "rim"),
            cube("serial receptacle", (15.30, 1.55, 6.70), (15.52, 2.62, 9.30), "serial"),
            cube("serial jack tab", (15.48, 1.62, 7.45), (15.60, 1.92, 8.55), "edge"),
            cube("serial contacts", (15.46, 2.25, 7.10), (15.56, 2.48, 8.90), "pin"),
            cube("console label", (13.60, 3.10, 6.60), (14.85, 3.18, 9.40), "rubber"),
        ],
    }
    for x in (1.55, 13.65):
        for z in (2.0, 12.8):
            model["elements"].append(cube("rubber foot", (x, 0, z), (x + .8, .28, z + 1.15), "rubber"))
    for element in model["elements"]:
        if element["name"] == "active display":
            element["faces"]["up"]["uv"] = [16, 16, 0, 0]
            element["faces"]["up"].pop("rotation", None)
    save_json(MODELS / "block/managed_tablet.json", model)
    tablet_parts = {"tablet back", "tablet chassis", "tablet rim", "glass bezel", "active display",
                    "camera", "speaker", "power button", "volume button", "brand plate"}
    save_json(MODELS / "block/managed_tablet_dock.json", {
        **model, "elements": [element for element in model["elements"]
                               if element["name"] not in tablet_parts],
    })
    portable = flat_tablet()
    for element in portable:
        if element["name"] == "active display":
            element["faces"]["up"]["uv"] = [16, 16, 0, 0]
    save_json(MODELS / "block/managed_tablet_portable.json", {
        **model, "elements": portable,
    })
    state = json.loads((ASSETS / "blockstates/network_switch.json").read_text(encoding="utf-8"))
    variants = {}
    for key, variant in state["variants"].items():
        facing = key.split("=")[-1]
        variants[f"docked=true,facing={facing}"] = {
            **variant, "model": "ncat_minecraft:block/managed_tablet",
        }
        variants[f"docked=false,facing={facing}"] = {
            **variant, "model": "ncat_minecraft:block/managed_tablet_dock",
        }
    state["variants"] = variants
    save_json(ASSETS / "blockstates/managed_tablet.json", state)
    save_json(MODELS / "item/managed_tablet.json", {
        "parent": "ncat_minecraft:block/managed_tablet_portable",
        "display": {
            "gui": {"rotation": [32, 215, 0], "translation": [0, -1, 0], "scale": [.62, .62, .62]},
            "ground": {"translation": [0, 3, 0], "scale": [.55, .55, .55]},
            "fixed": {"rotation": [0, 180, 0], "scale": [.9, .9, .9]},
            "thirdperson_righthand": {"rotation": [62, 200, 8], "translation": [1, 3.5, 1.5],
                                      "scale": [.68, .68, .68]},
            "thirdperson_lefthand": {"rotation": [62, 160, -8], "translation": [-1, 3.5, 1.5],
                                     "scale": [.68, .68, .68]},
            "firstperson_righthand": {"rotation": [18, 195, 4], "translation": [1.5, 2.5, -1],
                                      "scale": [1.05, 1.05, 1.05]},
            "firstperson_lefthand": {"rotation": [18, 165, -4], "translation": [-1.5, 2.5, -1],
                                     "scale": [1.05, 1.05, 1.05]},
        },
    })
    (TEXTURES / "managed_tablet").mkdir(parents=True, exist_ok=True)
    material("managed_tablet/casing.png", (15, 25, 37))
    material("managed_tablet/rim.png", (56, 94, 118))
    material("managed_tablet/edge.png", (37, 56, 71))
    material("managed_tablet/rubber.png", (4, 11, 18))
    tablet_screen(TEXTURES / "managed_tablet/screen.png")


def create_serial_cable():
    directory = ASSETS / "textures/item"
    directory.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    points = [(7, 7), (8, 10), (13, 12), (21, 18), (24, 24)]
    d.line(points, fill=(5, 13, 23), width=6, joint="curve")
    d.line(points, fill=(28, 49, 66), width=3, joint="curve")
    for x, y, mirrored in ((3, 4, False), (23, 24, True)):
        d.rounded_rectangle((x, y, x + 6, y + 5), radius=1,
                            fill=(70, 111, 134), outline=(192, 213, 220))
        d.rectangle((x + 1, y + 1, x + 5, y + 3), fill=(20, 36, 49))
        for i in range(3):
            d.point((x + 2 + i, y + 2), fill=(228, 178, 87))
        plug_x = x - 2 if not mirrored else x + 7
        d.rectangle((plug_x, y + 1, plug_x + 1, y + 4), fill=(195, 205, 204))
    image.save(directory / "serial_cable.png")
    save_json(MODELS / "item/serial_cable.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "ncat_minecraft:item/serial_cable"},
    })


if __name__ == "__main__":
    create_switch()
    create_tablet()
    create_serial_cable()
