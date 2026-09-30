import json
from pathlib import Path


ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ncat_minecraft"
FACES = ("north", "south", "east", "west", "up", "down")


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def box(start, end, texture, name):
    return {
        "name": name,
        "from": start,
        "to": end,
        "faces": {face: {"texture": f"#{texture}"} for face in FACES},
    }


def keyboard_model(half, theme):
    keys = {"browser": "light_gray_concrete", "ssh": "light_blue_concrete", "devtools": "white_concrete"}[theme]
    accent = {"browser": "red_concrete", "ssh": "blue_concrete", "devtools": "purple_concrete"}[theme]
    textures = {
        "body": "minecraft:block/black_concrete",
        "deck": {"browser": "minecraft:block/gray_concrete", "ssh": "minecraft:block/blue_terracotta", "devtools": "minecraft:block/purple_terracotta"}[theme],
        "key": f"minecraft:block/{keys}",
        "dark": "minecraft:block/gray_concrete" if theme == "browser" else "minecraft:block/black_concrete",
        "accent": f"minecraft:block/{accent}",
        "led": {"browser": "minecraft:block/lime_concrete", "ssh": "minecraft:block/cyan_concrete", "devtools": "minecraft:block/magenta_concrete"}[theme],
        "particle": "minecraft:block/black_concrete",
    }
    elements = [
        box([0, 0, 3], [16, 0.7, 16], "body", "carcaça"),
        box([0.3, 0.7, 3.3], [15.7, 0.95, 15.7], "deck", "superfície"),
        box([0.3, 0.95, 3.3], [15.7, 1.18, 3.58], "accent", "linha_frontal"),
        box([0.3, 0.95, 15.4], [15.7, 1.14, 15.7], "accent", "linha_traseira"),
    ]
    for row in range(4):
        z = 4.2 + row * 2.35
        for column in range(10):
            x = 0.48 + column * 1.52
            top = "key"
            if row == 0 or (half == "right" and column >= 7):
                top = "dark"
            if half == "left" and row == 0 and column == 0:
                top = "accent"
            if half == "right" and row == 2 and column == 9:
                top = "accent"
            elements.append(box([x, 0.95, z], [x + 1.25, 1.26, z + 1.53], "body", f"base_{row}_{column}"))
            elements.append(box([x + 0.07, 1.26, z + 0.07], [x + 1.18, 1.62, z + 1.42], top, f"tecla_{row}_{column}"))

    if half == "left":
        bottom = [(0.48, 2.2), (2.95, 1.5), (4.72, 1.5), (6.49, 5.85), (12.62, 1.1), (14.05, 1.1)]
    else:
        bottom = [(0.48, 1.5), (2.26, 1.5), (4.04, 2.9), (7.2, 1.5), (8.98, 1.5), (10.76, 1.5), (12.54, 2.9)]
    for index, (x, width) in enumerate(bottom):
        elements.append(box([x, 0.95, 13.65], [x + width, 1.26, 15.1], "body", f"base_inferior_{index}"))
        elements.append(box([x + 0.07, 1.26, 13.72], [x + width - 0.07, 1.58, 15.03], "accent" if half == "right" and index == len(bottom) - 1 else "key", f"tecla_inferior_{index}"))
    for index in range(3):
        x = 13.3 + index * 0.6
        elements.append(box([x, 1.0, 15.42], [x + 0.3, 1.1, 15.64], "led" if index == 0 else "accent", f"led_{index}"))
    return {"ambientocclusion": True, "textures": textures, "elements": elements}


def screen_item(theme):
    accent = {"browser": "red_concrete", "ssh": "blue_concrete", "log": "orange_concrete", "devtools": "purple_concrete"}[theme]
    textures = {
        "body": "minecraft:block/black_concrete",
        "accent": f"minecraft:block/{accent}",
        "panel": "minecraft:block/gray_concrete",
        "particle": f"minecraft:block/{accent}",
    }
    return {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": [
            box([0, 1, 12], [16, 15, 14], "body", "corpo"),
            box([0, 1, 14], [16, 15, 15], "accent", "moldura"),
            box([1, 2, 15], [15, 14, 16], "panel", "visor"),
            box([7, 0, 12], [9, 1, 15], "body", "base"),
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.58, 0.58, 0.58]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def keyboard_item(parent):
    return {
        "parent": f"ncat_minecraft:block/{parent}",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def keyboard_blockstates(left, right):
    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        model = {"model": f"ncat_minecraft:block/{left}"}
        if angle:
            model["y"] = angle
        for peripheral in ("keyboard", "remotectrl", "redstonectrl", "server"):
            variants[f"facing={facing},type=default_peripheral_{peripheral}"] = model
    save(ASSETS / "blockstates" / f"{left}.json", {"variants": variants})
    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        model = {"model": f"ncat_minecraft:block/{right}"}
        if angle:
            model["y"] = angle
        variants[f"facing={facing}"] = model
    save(ASSETS / "blockstates" / f"{right}.json", {"variants": variants})


for theme, left, right, item in (
    ("browser", "kb_left", "kb_right", "keyboard"),
    ("ssh", "ssh_kb_left", "ssh_kb_right", "ssh_keyboard"),
    ("devtools", "devtools_kb_left", "devtools_kb_right", "devtools_keyboard"),
):
    save(ASSETS / "models/block" / f"{left}.json", keyboard_model("left", theme))
    save(ASSETS / "models/block" / f"{right}.json", keyboard_model("right", theme))
    save(ASSETS / "models/item" / f"{item}.json", keyboard_item(left))
    keyboard_blockstates(left, right)

save(ASSETS / "models/item/screen.json", screen_item("browser"))
save(ASSETS / "models/item/ssh_screen.json", screen_item("ssh"))
save(ASSETS / "models/item/log_screen.json", screen_item("log"))
save(ASSETS / "models/item/devtools_screen.json", screen_item("devtools"))
