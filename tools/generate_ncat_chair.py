import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/ncat_minecraft"
TEXTURES = ASSETS / "textures/block/chair"
MODELS = ASSETS / "models/block"
ITEMS = ASSETS / "models/item"
STATES = ASSETS / "blockstates"

PALETTES = {
    "leather_black": ((20, 23, 29), (38, 42, 49), (9, 11, 15)),
    "leather_dark": ((30, 34, 41), (51, 56, 64), (16, 19, 24)),
    "leather_seat": ((34, 38, 44), (55, 61, 68), (19, 22, 28)),
    "blue_trim": ((9, 111, 219), (30, 161, 248), (4, 65, 147)),
    "blue_bright": ((21, 162, 249), (79, 207, 255), (7, 101, 214)),
    "metal": ((31, 35, 41), (63, 70, 78), (12, 15, 20)),
    "wheel": ((10, 13, 18), (38, 44, 51), (5, 7, 10)),
    "cyan": ((52, 210, 254), (137, 238, 255), (7, 157, 226)),
    "white": ((232, 239, 244), (255, 255, 255), (174, 186, 199)),
    "pink": ((251, 164, 189), (255, 203, 216), (198, 106, 146)),
}


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def make_texture(name, colors):
    base, light, dark = colors
    image = Image.new("RGBA", (16, 16), (*base, 255))
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            noise = (x * 37 + y * 43 + x * y * 11 + len(name) * 7) % 23
            if noise == 0:
                draw.point((x, y), fill=(*light, 255))
            elif noise == 1:
                draw.point((x, y), fill=(*dark, 255))
    draw.line((0, 0, 15, 0), fill=(*light, 255))
    draw.line((0, 15, 15, 15), fill=(*dark, 255))
    draw.line((0, 0, 0, 15), fill=(*light, 255))
    draw.line((15, 0, 15, 15), fill=(*dark, 255))
    TEXTURES.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURES / f"{name}.png")


for texture_name, palette in PALETTES.items():
    make_texture(texture_name, palette)

textures = {name: f"ncat_minecraft:block/chair/{name}" for name in PALETTES}
textures["particle"] = textures["leather_black"]
lower = []
upper = []


def box(part, name, coords, material, shade=True):
    x1, y1, z1, x2, y2, z2 = coords
    assert 0 <= x1 < x2 <= 16 and 0 <= y1 < y2 <= 16 and 0 <= z1 < z2 <= 16, name
    part.append({
        "name": name,
        "from": list(coords[:3]),
        "to": list(coords[3:]),
        "shade": shade,
        "faces": {face: {"texture": f"#{material}"} for face in ("north", "east", "south", "west", "up", "down")},
    })


box(lower, "base transversal", (1, 1.6, 7.1, 15, 2.6, 8.9), "metal")
box(lower, "base longitudinal", (7.1, 1.6, 1, 8.9, 2.6, 15), "metal")
box(lower, "quinta haste", (10, 1.6, 3, 12.3, 2.6, 8), "metal")
for name, x, z in (("esquerda", 1.2, 8), ("direita", 13.8, 8), ("frente", 8, 1.2), ("traseira", 8, 13.8), ("diagonal", 11.7, 3.2)):
    box(lower, f"rodízio {name}", (x - .9, .2, z - .65, x + .9, 1.8, z + .65), "wheel")
    box(lower, f"centro azul rodízio {name}", (x - .45, .45, z - .7, x + .45, 1.35, z - .58), "blue_bright")
for x, z in ((2.2, 8), (13.8, 8), (8, 2.2), (8, 13.8), (11.5, 3.4)):
    box(lower, "marca azul base", (x - .32, 2.61, z - .32, x + .32, 2.73, z + .32), "blue_trim")
box(lower, "coluna de elevação", (6.8, 2.6, 6.8, 9.2, 7.2, 9.2), "metal")
box(lower, "pistão", (7.2, 4.1, 7.2, 8.8, 8, 8.8), "leather_dark")
box(lower, "carcaça inferior", (3, 7.5, 2.4, 13, 9.2, 13.6), "metal")
box(lower, "friso inferior", (2.8, 8.3, 2.2, 13.2, 9.1, 13.8), "blue_trim")
box(lower, "assento estofado", (2.3, 9.1, 2.1, 13.7, 11.4, 13.5), "leather_seat")
box(lower, "superfície do assento", (2.7, 11.4, 2.6, 13.3, 11.75, 13.1), "leather_dark")
box(lower, "borda frontal", (2.2, 9.4, 1.85, 13.8, 11.4, 2.35), "leather_black")
for x1, x2, label in ((2.4, 3.1, "esquerdo"), (12.9, 13.6, "direito")):
    box(lower, f"lateral azul assento {label}", (x1, 9.5, 2.05, x2, 11.45, 12.8), "blue_trim")
    box(lower, f"costura azul assento {label}", (x1, 11.76, 2.55, x2, 11.84, 11.4), "blue_bright")
box(lower, "bordado esquerdo", (4.25, 11.76, 3.15, 4.85, 11.85, 5), "blue_trim")
box(lower, "bordado direito", (11.15, 11.76, 3.15, 11.75, 11.85, 5), "blue_trim")

for side, x1, x2 in (("esquerdo", .45, 2.7), ("direito", 13.3, 15.55)):
    box(lower, f"haste apoio {side}", (x1 + .45, 9, 8.8, x2 - .45, 14, 10.3), "metal")
    box(lower, f"apoio de braço {side}", (x1, 13.1, 3.6, x2, 14.55, 11.2), "leather_black")
    box(lower, f"superfície apoio {side}", (x1 + .12, 14.55, 3.8, x2 - .12, 14.72, 10.9), "leather_dark")
    box(lower, f"friso azul apoio {side}", (x1 + .15, 14.73, 3.85, x2 - .15, 14.82, 4.5), "blue_bright")
    box(lower, f"conector {side}", (x1 + .55, 9, 10.3, x2 - .3, 11.5, 13.8), "metal")

box(lower, "estrutura inferior encosto", (2.1, 10, 12.5, 13.9, 16, 15.45), "leather_black")
box(lower, "espuma inferior encosto", (3.1, 11, 11.65, 12.9, 16, 12.65), "leather_dark")
box(lower, "lateral esquerda inferior", (1.55, 10.2, 11.6, 3.05, 16, 15.35), "blue_trim")
box(lower, "lateral direita inferior", (12.95, 10.2, 11.6, 14.45, 16, 15.35), "blue_trim")
for side, x1, x2 in (("esquerda", 1.4, 1.55), ("direita", 14.45, 14.6)):
    box(lower, f"selo pata {side}", (x1, 10.8, 12.75, x2, 11.65, 13.75), "blue_bright", False)
    for z in (12.55, 13.15, 13.75):
        box(lower, f"dedo pata {side} {z}", (x1, 11.8, z, x2, 12.2, z + .3), "cyan", False)

box(upper, "estrutura alta", (2.2, 0, 12.35, 13.8, 14.7, 15.55), "leather_black")
box(upper, "revestimento frente", (3, 0, 11.6, 13, 13.1, 12.55), "leather_dark")
box(upper, "painel central", (4.1, .5, 11.45, 11.9, 11.9, 11.7), "leather_black")
for side, x1, x2 in (("esquerda", 1.5, 3.2), ("direita", 12.8, 14.5)):
    box(upper, f"asa lateral {side}", (x1, 1, 10.65, x2, 12.7, 15.3), "blue_trim")
    box(upper, f"borda preta {side}", (x1 + .42, 1.2, 10.2, x2 - .15, 12.8, 12.8), "leather_black")
    box(upper, f"luz azul {side}", (x1 + .08, 1.7, 10.45, x1 + .42, 12, 10.9), "blue_bright")
box(upper, "arco superior", (3.1, 12.7, 12.7, 12.9, 15.2, 15.3), "leather_black")
box(upper, "costura azul topo", (3.5, 14.35, 12.55, 12.5, 14.55, 14.9), "blue_trim")
box(upper, "marca gato costas esquerda", (5, 9.1, 15.56, 6.2, 10.55, 15.7), "cyan", False)
box(upper, "marca gato costas direita", (9.8, 9.1, 15.56, 11, 10.55, 15.7), "cyan", False)
box(upper, "marca nariz costas", (7.4, 8.2, 15.56, 8.6, 8.75, 15.7), "pink", False)
for x1, x2, side in ((4.1, 6.3, "esquerda"), (9.7, 11.9, "direita")):
    box(upper, f"marca orelha costas {side}", (x1, 11.1, 15.56, x2, 12.25, 15.7), "blue_bright", False)
for x1, x2, name in ((3.65, 4.3, "esquerda"), (11.7, 12.35, "direita")):
    box(upper, f"costura vertical {name}", (x1, 1.1, 11.31, x2, 9.2, 11.45), "blue_trim")
box(upper, "almofada lombar", (4.4, .7, 9.25, 11.6, 4.1, 11.65), "leather_black")
box(upper, "faixa lombar esquerda", (4.5, .7, 9.13, 5.15, 4.1, 9.35), "blue_trim")
box(upper, "faixa lombar direita", (10.85, .7, 9.13, 11.5, 4.1, 9.35), "blue_trim")
box(upper, "cabeça do gato", (4.1, 9.1, 8.55, 11.9, 14.2, 11.85), "leather_black")
box(upper, "orelha esquerda preta", (4.15, 13.55, 8.7, 6.3, 15.65, 11.45), "leather_black")
box(upper, "orelha direita preta", (9.7, 13.55, 8.7, 11.85, 15.65, 11.45), "leather_black")
box(upper, "orelha esquerda azul", (4.65, 14, 8.48, 5.6, 15.15, 8.75), "blue_bright")
box(upper, "orelha direita azul", (10.4, 14, 8.48, 11.35, 15.15, 8.75), "blue_bright")
box(upper, "contorno superior almofada", (4.3, 13.95, 8.55, 11.7, 14.18, 11.9), "leather_dark")


def cat_face(part, prefix, x, y, front):
    box(part, f"{prefix} olho esquerdo", (x + .9, y + 1.55, front - .11, x + 1.7, y + 2.65, front), "cyan", False)
    box(part, f"{prefix} olho direito", (x + 4.3, y + 1.55, front - .11, x + 5.1, y + 2.65, front), "cyan", False)
    box(part, f"{prefix} brilho olho esquerdo", (x + 1.05, y + 2.2, front - .14, x + 1.35, y + 2.5, front - .1), "white", False)
    box(part, f"{prefix} brilho olho direito", (x + 4.45, y + 2.2, front - .14, x + 4.75, y + 2.5, front - .1), "white", False)
    box(part, f"{prefix} nariz", (x + 2.65, y + 1.1, front - .14, x + 3.35, y + 1.55, front), "pink", False)
    for iy in (y + .62, y + .94):
        box(part, f"{prefix} bigodes esquerda {iy}", (x + .4, iy, front - .12, x + 1.9, iy + .17, front), "white", False)
        box(part, f"{prefix} bigodes direita {iy}", (x + 4.1, iy, front - .12, x + 5.6, iy + .17, front), "white", False)


cat_face(upper, "rosto cabeça", 5, 9.5, 8.55)
cat_face(upper, "rosto lombar", 5, .8, 9.25)

for x1, x2, side in ((3.4, 4.3, "esquerdo"), (11.7, 12.6, "direito")):
    box(upper, f"faixa azul encosto {side}", (x1, 2.2, 11.28, x2, 8.25, 11.6), "blue_trim")
    box(upper, f"detalhe superior {side}", (x1, 8.6, 11.2, x2, 9.4, 11.65), "blue_bright")


def model(elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


save(MODELS / "gaming_chair_lower.json", model(lower))
save(MODELS / "gaming_chair_upper.json", model(upper))

item_elements = lower + [
    {**element, "from": [element["from"][0], element["from"][1] + 16, element["from"][2]],
     "to": [element["to"][0], element["to"][1] + 16, element["to"][2]]}
    for element in upper
]
save(ITEMS / "gaming_chair.json", {
    **model(item_elements),
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -3.05, 0], "scale": [.432, .432, .432]},
        "ground": {"translation": [0, 2, 0], "scale": [.30, .30, .30]},
        "fixed": {"translation": [0, -3, 0], "scale": [.45, .45, .45]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [.24, .24, .24]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [.30, .30, .30]},
    },
})

variants = {}
for direction, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for half in ("lower", "upper"):
        variants[f"facing={direction},half={half}"] = {"model": f"ncat_minecraft:block/gaming_chair_{half}", "y": angle}
save(STATES / "gaming_chair.json", {"variants": variants})

print(f"Cadeira: {len(lower)} cubos inferiores, {len(upper)} superiores, {len(PALETTES)} texturas")
