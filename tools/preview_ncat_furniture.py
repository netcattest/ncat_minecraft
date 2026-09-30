import json
from pathlib import Path

from PIL import Image, ImageDraw

import gen_cooler


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ncat_minecraft/models/item"
OUTPUT = ROOT / "build/preview/furniture.png"

gen_cooler.COLORS.update({
    "#yellow": (244, 196, 28),
    "#yellow_dark": (214, 154, 16),
    "#yellow_light": (255, 224, 86),
    "#beak": (236, 112, 28),
    "#beak_dark": (186, 72, 18),
    "#white": (246, 248, 252),
    "#black": (22, 18, 24),
    "#paper": (231, 237, 240),
    "#rim": (249, 252, 252),
    "#inside": (176, 187, 193),
    "#blue_band": (28, 126, 194),
    "#case": (35, 35, 39),
    "#case_dark": (23, 23, 27),
    "#button": (104, 108, 112),
    "#screen": (12, 12, 14),
    "#led": (255, 36, 28),
})


def load(name):
    data = json.loads((ASSETS / f"{name}.json").read_text(encoding="utf-8"))
    elements = data["elements"]
    highest = max(element["to"][1] for element in elements)
    shift = (32 - highest) / 2
    positioned = []
    for element in elements:
        copy = {**element, "from": element["from"].copy(), "to": element["to"].copy()}
        copy["from"][1] += shift
        copy["to"][1] += shift
        positioned.append(copy)
    return positioned


names = (
    ("Patinho", "rubber_duck"),
    ("Copo vazio", "paper_cup"),
    ("Copo com água", "paper_cup_water"),
    ("Relógio preto", "digital_clock"),
    ("Relógio branco", "digital_clock_white"),
    ("Galão", "water_gallon"),
)
angles = ((0, 15), (-35, 20), (35, 20), (180, 15))
size = 240
sheet = Image.new("RGB", (size * 4, size * len(names)), (8, 8, 10))
draw = ImageDraw.Draw(sheet)
for row, (label, name) in enumerate(names):
    elements = load(name)
    if name == "digital_clock_white":
        gen_cooler.COLORS.update({"#case": (215, 217, 217), "#case_dark": (185, 190, 191), "#button": (235, 239, 241)})
    for col, (yaw, pitch) in enumerate(angles):
        tile = gen_cooler.render_view(elements, yaw, pitch, size)
        sheet.paste(tile, (col * size, row * size))
    draw.text((12, row * size + 12), label, fill=(225, 234, 241))
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUTPUT)
print(OUTPUT)
