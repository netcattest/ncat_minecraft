import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ncat_minecraft"
TEXTURES = ROOT / "textures/block/network_switch"
MODELS = ROOT / "models/block"
TEXTURES.mkdir(parents=True, exist_ok=True)
MODELS.mkdir(parents=True, exist_ok=True)


def grain(name, base, border=None):
    rng = random.Random(name)
    image = Image.new("RGBA", (16, 16), base + (255,))
    pixels = image.load()
    for y in range(16):
        for x in range(16):
            change = rng.choice((-3, -2, -1, 0, 0, 1, 2, 3))
            pixels[x, y] = tuple(max(0, min(255, channel + change)) for channel in base) + (255,)
    if border:
        draw = ImageDraw.Draw(image)
        draw.line((0, 0, 15, 0), fill=border)
    image.save(TEXTURES / f"{name}.png")


grain("casing", (33, 43, 54), (55, 68, 79))
grain("front", (25, 34, 43), (64, 82, 94))
grain("trim", (99, 115, 126), (146, 158, 166))
grain("socket", (10, 17, 22))
grain("pin", (200, 151, 74), (245, 202, 113))
grain("black", (14, 19, 24))
grain("foot", (21, 24, 25))
grain("power", (78, 109, 119), (134, 173, 176))

top = Image.new("RGBA", (128, 128), (35, 45, 56, 255))
draw = ImageDraw.Draw(top)
for y in range(7, 122, 7):
    draw.line((5, y, 123, y), fill=(42, 53, 64, 255), width=1)
draw.rounded_rectangle((3, 3, 124, 124), radius=5, outline=(86, 103, 114), width=2)
draw.line((12, 27, 116, 27), fill=(43, 178, 188), width=2)
draw.line((22, 98, 106, 98), fill=(74, 91, 103), width=1)
for x, y in ((10, 10), (117, 10), (10, 116), (117, 116)):
    draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=(108, 126, 135))
    draw.point((x, y), fill=(38, 49, 58))
font_main = ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", 28)
font_small = ImageFont.truetype("C:/Windows/Fonts/bahnschrift.ttf", 12)
font_micro = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 9)
draw.text((64, 43), "SWITCH", font=font_main, fill=(226, 236, 240), anchor="mm")
draw.text((64, 76), "netcattest", font=font_small, fill=(78, 191, 196), anchor="mm")
draw.text((64, 108), "8 PORT  /  UNMANAGED", font=font_micro, fill=(132, 151, 162), anchor="mm")
top.save(TEXTURES / "top.png")


def element(name, start, end, texture, faces=None):
    face_textures = {side: {"texture": texture} for side in ("north", "south", "east", "west", "up", "down")}
    if faces:
        for side, face_texture in faces.items():
            face_textures[side] = {"texture": face_texture}
    return {"name": name, "from": start, "to": end, "faces": face_textures}


parts = [
    element("chassis", [0.6, 0.65, 1.2], [15.4, 7.98, 14.8], "#casing"),
    element("top cover", [0.8, 7.98, 0.92], [15.2, 8.28, 15.1], "#trim", {"up": "#top"}),
    element("front panel", [0.4, 1.1, 0.78], [15.6, 7.68, 1.42], "#front"),
    element("lower front trim", [0.35, 0.83, 0.68], [15.65, 1.16, 1.45], "#trim"),
    element("left ear", [0.15, 1.0, 0.57], [0.75, 7.82, 2.25], "#trim"),
    element("right ear", [15.25, 1.0, 0.57], [15.85, 7.82, 2.25], "#trim"),
    element("left foot front", [1.8, 0, 2.45], [3.1, 0.67, 4.2], "#foot"),
    element("right foot front", [12.9, 0, 2.45], [14.2, 0.67, 4.2], "#foot"),
    element("left foot rear", [1.8, 0, 11.9], [3.1, 0.67, 13.65], "#foot"),
    element("right foot rear", [12.9, 0, 11.9], [14.2, 0.67, 13.65], "#foot"),
    element("power ring", [13.92, 6.16, 0.36], [15.22, 7.46, 0.79], "#trim"),
    element("power key", [14.16, 6.40, 0.19], [14.98, 7.22, 0.38], "#power"),
    element("status led frame", [14.2, 3.91, 0.18], [15.1, 4.8, 0.52], "#black"),
]
parts[1]["faces"]["up"]["uv"] = [0, 0, 16, 16]

port_x = (2.15, 5.7, 9.25, 12.8) * 2
port_y = (5.2,) * 4 + (2.45,) * 4
for index, (x, y) in enumerate(zip(port_x, port_y), start=1):
    parts.append(element(f"RJ45 {index} steel shell", [x - 1.38, y - 1.13, 0.33],
                         [x + 1.38, y + 1.13, 0.92], "#trim"))
    parts.append(element(f"RJ45 {index} socket", [x - 1.15, y - 0.90, 0.22],
                         [x + 1.15, y + 0.90, 0.37], "#socket"))
    parts.append(element(f"RJ45 {index} key", [x - 0.46, y - 0.87, 0.16],
                         [x + 0.46, y - 0.68, 0.27], "#black"))
    for pin in range(5):
        pin_x = x - 0.78 + pin * 0.39
        parts.append(element(f"RJ45 {index} contact {pin + 1}",
                             [pin_x, y + 0.44, 0.16], [pin_x + 0.17, y + 0.67, 0.25], "#pin"))
    parts.append(element(f"RJ45 {index} activity frame", [x - 0.22, y + 1.13, 0.18],
                         [x + 0.22, y + 1.56, 0.48], "#black"))

for side, x0, x1 in (("left", 0.41, 0.65), ("right", 15.35, 15.59)):
    for index in range(5):
        z = 5.15 + index * 1.65
        parts.append(element(f"{side} ventilation {index + 1}", [x0, 4.2, z],
                             [x1, 4.55, z + 0.92], "#black"))

model = {
    "parent": "minecraft:block/block",
    "textures": {name: f"ncat_minecraft:block/network_switch/{name}" for name in
                 ("casing", "front", "trim", "socket", "pin", "black", "foot", "power", "top")},
    "elements": parts,
}
model["textures"]["particle"] = model["textures"]["casing"]
(MODELS / "network_switch.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
