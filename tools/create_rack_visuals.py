import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ncat_minecraft"
TEXTURES = ASSETS / "textures/block/rack"
BLOCKS = ASSETS / "models/block"
ITEMS = ASSETS / "models/item"
STATES = ASSETS / "blockstates"

FRAME_TYPES = ("rack_frame_12u", "rack_frame_18u")
MODULES = {
    "browser": ("WEB", (224, 57, 82), 1, 2),
    "ssh": ("SSH", (58, 147, 242), 1, 2),
    "log": ("LOG", (234, 151, 52), 1, 1),
    "devtools": ("DEVTOOLS", (125, 91, 225), 1, 1),
    "sftp": ("SFTP", (49, 185, 170), 1, 1),
    "terminal": ("TERMINAL", (47, 174, 99), 1, 2),
    "remote": ("REMOTE", (198, 118, 62), 1, 2),
    "proxy": ("PROXY", (206, 84, 196), 1, 2),
    "switch": ("SWITCH", (54, 183, 111), 2, 8),
    "managed_switch": ("SWITCH M", (53, 187, 213), 2, 8),
}


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
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tile(name, base, accent, stripe=False):
    image = Image.new("RGBA", (32, 32))
    pixels = image.load()
    for y in range(32):
        for x in range(32):
            grain = ((x * 17 + y * 11 + x * y * 3) % 9) - 4
            groove = -8 if stripe and x % 8 in (0, 1) else 0
            pixels[x, y] = tuple(max(0, min(255, c + grain + groove)) for c in base) + (255,)
    draw = ImageDraw.Draw(image)
    if accent:
        draw.line((0, 2, 31, 2), fill=accent, width=1)
        draw.line((0, 29, 31, 29), fill=(31, 52, 65), width=1)
    image.save(TEXTURES / name)


def frame_texture():
    tile("frame.png", (26, 37, 48), (75, 96, 112), True)
    tile("interior.png", (9, 18, 27), (21, 42, 54))
    tile("rail.png", (38, 52, 63), (82, 108, 124), True)
    tile("trim.png", (45, 62, 73), (110, 137, 148))
    tile("side.png", (23, 37, 46), (48, 102, 120), True)
    tile("socket.png", (11, 24, 31), (53, 163, 190))

    glass = Image.new("RGBA", (64, 64), (21, 59, 73, 40))
    draw = ImageDraw.Draw(glass)
    draw.line((9, 0, 0, 48), fill=(126, 200, 220, 38), width=6)
    draw.line((61, 4, 42, 63), fill=(112, 183, 211, 27), width=3)
    draw.line((0, 0, 63, 0), fill=(122, 172, 194, 120), width=1)
    draw.line((0, 63, 63, 63), fill=(24, 50, 65, 140), width=1)
    glass.save(TEXTURES / "glass.png")

    badge = Image.new("RGBA", (256, 48), (11, 21, 31, 255))
    draw = ImageDraw.Draw(badge)
    draw.rounded_rectangle((2, 2, 253, 45), 5, fill=(18, 34, 46), outline=(54, 97, 112), width=2)
    draw.text((12, 7), "NCAT", font=font(26, True), fill=(222, 242, 247))
    draw.text((96, 16), "RACK SYSTEM", font=font(15, True), fill=(94, 193, 216))
    draw.line((10, 39, 245, 39), fill=(58, 177, 208), width=2)
    badge.save(TEXTURES / "badge.png")


def module_texture(key, label, accent, ports):
    image = Image.new("RGBA", (128, 64), (18, 24, 32, 255))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((1, 1, 126, 62), radius=4, fill=(26, 34, 44), outline=(72, 92, 104), width=2)
    draw.rectangle((4, 4, 12, 59), fill=accent)
    draw.text((20, 17), "NCAT", font=font(17, True), fill=(220, 236, 242))
    draw.text((20, 40), label, font=font(12, True), fill=accent)
    draw.line((20, 36, 120, 36), fill=tuple(int(c * .5) for c in accent), width=1)
    for index in range(6):
        x = 96 + (index % 3) * 9
        y = 44 + (index // 3) * 7
        draw.rectangle((x, y, x + 5, y + 4), fill=(44, 58, 68))
    image.save(TEXTURES / f"module_{key}.png")


def faces(name):
    return {face: {"texture": "#" + name}
            for face in ("north", "south", "east", "west", "up", "down")}


def element(name, start, end, texture):
    return {"name": name, "from": list(start), "to": list(end), "faces": faces(texture)}


def frame_model(kind):
    accent = "trim"
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "frame": "ncat_minecraft:block/rack/frame",
            "interior": "ncat_minecraft:block/rack/interior",
            "rail": "ncat_minecraft:block/rack/rail",
            "trim": "ncat_minecraft:block/rack/trim",
            "side": "ncat_minecraft:block/rack/side",
            "socket": "ncat_minecraft:block/rack/socket",
            "particle": "ncat_minecraft:block/rack/frame",
        },
        "elements": [
            element("back panel", (.9, .45, 14.6), (15.1, 15.55, 15.92), "interior"),
            element("left side front", (.3, .4, .4), (1.05, 15.6, 11.1), "side"),
            element("left side rear", (.3, .4, 13.4), (1.05, 15.6, 15.75), "side"),
            element("right side front", (14.95, .4, .4), (15.7, 15.6, 11.1), "side"),
            element("right side rear", (14.95, .4, 13.4), (15.7, 15.6, 15.75), "side"),
            element("left front upright", (.15, 0, .12), (1.45, 16, 1.55), "frame"),
            element("right front upright", (14.55, 0, .12), (15.85, 16, 1.55), "frame"),
            element("top flange", (.15, 15.25, .12), (15.85, 16, 15.85), "trim"),
            element("bottom flange", (.15, 0, .12), (15.85, .75, 15.85), "trim"),
            element("left rail", (1.28, .75, 1.28), (1.75, 15.25, 2.28), "rail"),
            element("right rail", (14.25, .75, 1.28), (14.72, 15.25, 2.28), "rail"),
            element("left foot", (.45, 0, 1.8), (2.5, .28, 4.2), "frame"),
            element("right foot", (13.5, 0, 1.8), (15.55, .28, 4.2), "frame"),
        ],
    }
    for y in (2.2, 5.7, 10.3, 13.8):
        model["elements"].append(element(f"left mount {y}", (1.5, y, 1.08),
                                         (1.85, y + .25, 2.6), accent))
        model["elements"].append(element(f"right mount {y}", (14.15, y, 1.08),
                                         (14.5, y + .25, 2.6), accent))
    for y in (3.1, 6.6, 10.1, 13.6):
        model["elements"].append(element(f"left vent {y}", (.14, y, 4),
                                         (.31, y + .18, 12), "socket"))
        model["elements"].append(element(f"right vent {y}", (15.69, y, 4),
                                         (15.86, y + .18, 12), "socket"))
    for side, x0, x1 in (("left", .08, .3), ("right", 15.7, 15.92)):
        for y in (1.15, 13.15):
            model["elements"].append(element(f"{side} cable exit {y}",
                                             (x0, y, 11.3), (x1, y + 1.1, 13.25), "socket"))
    if kind == "rack_frame_18u":
        for x in (1.9, 13.9):
            model["elements"].append(element(f"extension rib {x}", (x, 8, 14.15),
                                             (x + .2, 8.3, 15.5), "trim"))
    return model


def module_model(key, height):
    texture = f"ncat_minecraft:block/rack/module_{key}"
    y0, y1 = (6.8, 9.2) if height == 1 else (6.1, 9.9)
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "body": "ncat_minecraft:block/rack/cabinet_steel",
            "front": texture,
            "particle": texture,
        },
        "elements": [
            element("module chassis", (.3, y0, 1), (15.7, y1, 14.8), "body"),
            {"name": "front panel", "from": [.45, y0 + .15, .82],
             "to": [15.55, y1 - .15, 1.02],
             "faces": {"north": {"texture": "#front", "uv": [0, 0, 16, 16]},
                       "south": {"texture": "#body"},
                       "east": {"texture": "#body"},
                       "west": {"texture": "#body"},
                       "up": {"texture": "#body"},
                       "down": {"texture": "#body"}}},
        ],
    }
    return model


def item_display():
    return {
        "gui": {"rotation": [24, 215, 0], "translation": [0, 0, 0], "scale": [.62, .62, .62]},
        "ground": {"translation": [0, 2, 0], "scale": [.50, .50, .50]},
        "fixed": {"rotation": [0, 180, 0], "scale": [.82, .82, .82]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [.48, .48, .48]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [.65, .65, .65]},
    }


def generate():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for key, (label, accent, height, ports) in MODULES.items():
        module_texture(key, label, accent, ports)
        save_json(BLOCKS / f"rack_module_{key}.json", module_model(key, height))
        save_json(ITEMS / f"rack_module_{key}.json",
                  {"parent": f"ncat_minecraft:block/rack_module_{key}", "display": item_display()})


if __name__ == "__main__":
    generate()
