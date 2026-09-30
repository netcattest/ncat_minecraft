
import math
import os

from PIL import Image

import gen_poles as poles
from gen_poles import ICON_DISPLAY, box, clamp, disc_z, element_json, mix, noise, r4, split, tint, \
    validate, vnoise, write_json

ROOT = poles.ROOT
ASSETS = poles.ASSETS
TEX = os.path.join(ASSETS, "textures", "block", "power")
MODELS = os.path.join(ASSETS, "models")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview_cables")

C = 8.0

ACSR = "acsr"
ALUMINUM = "aluminum"
INSULATED = "insulated"
COPPER = "copper"
REEL = "reel_wood"
REEL_DARK = "reel_wood_dark"
REEL_END = "reel_end"
HUB = "reel_hub"
BAND = "reel_band"
LABEL = "reel_label"



def round_shade(u):
    angle = (u / 16.0) * math.tau
    lit = math.cos(angle - 2.2)
    return 0.52 + 0.62 * max(0.0, lit) + 0.10 * lit


def stranded(base, salt, lay=2.6, contrast=26, wires=7):
    def paint(x, y):
        shade = round_shade(x)
        phase = (x + y * lay) % (16.0 / wires)
        crown = math.cos(phase / (16.0 / wires) * math.tau)
        delta = crown * contrast
        delta += (vnoise(x, y, 4, 4, salt) - 0.5) * 10
        delta += (noise(x, y, salt + 1) - 0.5) * 6
        colour = tuple(component * shade for component in base)
        return tint(colour, delta)
    return paint


def jacketed(base, salt, rib=4.0):
    def paint(x, y):
        shade = round_shade(x) * 0.92
        delta = 0.0
        if abs((x % rib) - 0.0) < 0.9:
            delta -= 8
        delta += (vnoise(x, y, 8, 4, salt) - 0.5) * 7
        delta += (noise(x, y, salt + 1) - 0.5) * 4
        colour = tuple(component * shade for component in base)
        if 3.0 <= x < 4.0:
            colour = mix(colour, (150, 150, 152), 0.35)
        return tint(colour, delta)
    return paint


def patinated(paint, salt):
    def wrapped(x, y):
        colour = paint(x, y)
        green = max(0.0, vnoise(x, y, 4, 8, salt) - 0.55) * 1.7
        return mix(colour, (86, 132, 104), min(0.45, green))
    return wrapped



def plank(base, salt):
    def paint(x, y):
        grain = 0.5 * noise(y, 0, salt) + 0.5 * vnoise(x, y, 16, 2, salt + 1)
        delta = (grain - 0.5) * 26 + (noise(x, y, salt + 2) - 0.5) * 8
        if y % 5 == 0:
            delta -= 30
        return tint(base, delta, warm=4)
    return paint


def reel_end_paint(x, y):
    dx, dy = x - 7.5, y - 7.5
    distance = math.hypot(dx, dy)
    angle = math.atan2(dy, dx)
    colour = mix((148, 118, 84), (112, 86, 60), (0.5 + 0.5 * math.cos(angle * 6.0)) * 0.7)
    if distance > 7.0:
        colour = mix(colour, (78, 60, 42), 0.7)
    if distance < 2.1:
        colour = mix(colour, (70, 74, 78), 0.85)
    if 4.2 < distance < 5.0 and math.cos(angle * 4.0) > 0.86:
        colour = mix(colour, (62, 66, 70), 0.9)
    return tint(colour, (noise(x, y, 121) - 0.5) * 10)


def label_paint(x, y):
    if x in (0, 15) or y in (0, 15):
        return tint((96, 76, 54), (noise(x, y, 131) - 0.5) * 8)
    colour = (214, 210, 198)
    if 3 <= y <= 5 and 2 <= x <= 13 and noise(x, y, 132) > 0.3:
        colour = (44, 46, 50)
    if 8 <= y <= 10 and 2 <= x <= 10 and noise(x, y, 133) > 0.35:
        colour = (44, 46, 50)
    return tint(colour, (noise(x, y, 134) - 0.5) * 8)


def texture(painter):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            red, green, blue = painter(x, y)[:3]
            image.putpixel((x, y), (clamp(red), clamp(green), clamp(blue), 255))
    return image


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    copper = stranded((196, 118, 66), 41, lay=2.2, contrast=30, wires=7)
    painters = {
        ACSR: stranded((166, 170, 175), 11, lay=3.0, contrast=24, wires=6),
        ALUMINUM: stranded((192, 196, 200), 21, lay=2.4, contrast=28, wires=7),
        INSULATED: jacketed((40, 41, 44), 31),
        COPPER: patinated(copper, 42),
        REEL: plank((146, 116, 82), 101),
        REEL_DARK: plank((104, 82, 58), 103),
        HUB: plank((118, 94, 66), 105),
        BAND: stranded((132, 137, 141), 107, lay=0.0, contrast=10, wires=4),
    }
    for name, painter in painters.items():
        texture(painter).save(os.path.join(TEX, f"{name}.png"))
    texture(reel_end_paint).save(os.path.join(TEX, f"{REEL_END}.png"))
    texture(label_paint).save(os.path.join(TEX, f"{LABEL}.png"))



def drum(conductor):
    out = []
    for z0, z1 in ((1.2, 3.0), (13.0, 14.8)):
        disc_z(out, "flange", C, C, 6.9, z0, z1, REEL, ends=REEL_END)
        disc_z(out, "aro", C, C, 7.1, z0 + 0.35, z0 + 0.75, BAND)
        disc_z(out, "aro", C, C, 7.1, z1 - 0.75, z1 - 0.35, BAND)
    disc_z(out, "nucleo", C, C, 3.1, 3.0 - 0.02, 13.0 + 0.02, HUB)
    disc_z(out, "cabo enrolado", C, C, 5.5, 3.2, 12.8, conductor)
    disc_z(out, "cabo enrolado externo", C, C, 5.9, 4.2, 11.8, conductor)
    disc_z(out, "eixo", C, C, 0.9, 0.2, 15.8, BAND)
    box(out, "ponta do cabo", C - 6.2, C - 1.0, 6.4, C - 4.6, C - 0.3, 9.6, conductor)
    box(out, "ponta do cabo solta", C - 6.9, C - 3.4, 7.2, C - 6.2, C - 0.6, 8.8, conductor)
    box(out, "etiqueta", C - 2.0, C + 2.6, 0.9, C + 2.0, C + 5.6, 1.25, REEL_DARK,
        sides={"north": LABEL}, fit={"north": (0, 0, 16, 16)})
    return out


def payload(label, elements, particle):
    pieces = [piece for element in elements for piece in split(element)]
    json_elements = [element_json(piece, lambda p: p) for piece in pieces]
    validate(label, json_elements)
    textures = {tex: f"ncat_minecraft:block/power/{tex}"
                for tex in sorted({t for p in pieces for t in p["faces"].values()})}
    textures["particle"] = f"ncat_minecraft:block/power/{particle}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": json_elements}


DISPLAY = {
    "gui": {"rotation": [28, 215, 0], "translation": [0, -1, 0], "scale": [0.82, 0.82, 0.82]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.36, 0.36, 0.36]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.68, 0.68, 0.68]},
    "thirdperson_righthand": {"rotation": [72, 40, 0], "translation": [0, 3.5, 0], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1.5, 0], "scale": [0.48, 0.48, 0.48]},
}

CABLES = (
    ("power_cable_high_voltage", ACSR),
    ("power_cable_medium_voltage", ALUMINUM),
    ("power_cable_low_voltage", INSULATED),
    ("power_cable_copper", COPPER),
)


def write_items():
    for key, conductor in CABLES:
        model = payload(key, drum(conductor), conductor)
        model["display"] = DISPLAY
        write_json(os.path.join(MODELS, "item", f"{key}.json"), model, compact=True)
        print(f"{key}: {len(model['elements'])} elementos")


def save_preview():
    os.makedirs(PREVIEW, exist_ok=True)
    original = poles.TEX
    poles.TEX = TEX
    try:
        sheet = Image.new("RGB", (240 * len(CABLES), 240), (16, 18, 22))
        for index, (key, _) in enumerate(CABLES):
            elements = poles.load_elements(os.path.join(MODELS, "item", f"{key}.json"), 0.0)
            sheet.paste(poles.render(elements, 215, 28, 12.0, (240, 240), (C, C, C)), (index * 240, 0))
        sheet.save(os.path.join(PREVIEW, "drums.png"))
    finally:
        poles.TEX = original
    print("previews em", PREVIEW)


def main():
    make_textures()
    write_items()
    if os.environ.get("CABLE_PREVIEW") == "1":
        save_preview()


if __name__ == "__main__":
    main()
