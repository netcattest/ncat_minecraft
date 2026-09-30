import json
from pathlib import Path

from PIL import Image, ImageDraw

import gen_cooler


ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "src/main/resources/assets/ncat_minecraft/models/block/paper_cup.json"
OUTPUT = ROOT / "build/preview/water_cooler_cups.png"
gen_cooler.COLORS.update({
    "#paper": (231, 237, 240),
    "#rim": (249, 252, 252),
    "#inside": (176, 187, 193),
    "#blue_band": (28, 126, 194),
})


def cup_at(side):
    center = 9.9 if side > 0 else 6.1
    elements = json.loads(MODEL.read_text(encoding="utf-8"))["elements"]
    moved = []
    for source in elements:
        element = json.loads(json.dumps(source))
        for key in ("from", "to"):
            x, y, z = element[key]
            element[key] = [center + (x - 8) * .29, 5.65 + y * .29, 3.15 + (z - 8) * .29]
        moved.append(element)
    return moved


body = gen_cooler.resize_height(gen_cooler.build_body(), 1.27, 0)
levers = gen_cooler.resize_height(gen_cooler.paddles(), 1.27, 0)
gallon = gen_cooler.resize_height(gen_cooler.build_gallon(), .78, 20.15)
size = 440
sheet = Image.new("RGB", (size * 2, size * 2), (8, 8, 10))
draw = ImageDraw.Draw(sheet)
for row, side in enumerate((-1, 1)):
    elements = body + levers + gallon + cup_at(side)
    for col, angle in enumerate((180, 145)):
        tile = gen_cooler.render_view(elements, angle, 14, size)
        sheet.paste(tile, (col * size, row * size))
    draw.text((12, row * size + 12), "Azul" if side < 0 else "Vermelha", fill=(230, 240, 248))
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
sheet.save(OUTPUT)
print(OUTPUT)
