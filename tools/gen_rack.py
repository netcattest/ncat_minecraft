import math
import os

from PIL import Image

import gen_poles as poles
from gen_poles import box, disc, element_json, mix, noise, split, tint, validate, vnoise, write_json

ASSETS = poles.ASSETS
DATA = poles.DATA
TEX = os.path.join(ASSETS, "textures", "block", "rack")
MODELS = os.path.join(ASSETS, "models")
SHAPES_JAVA = os.path.join(poles.ROOT, "src", "main", "java", "com", "netcattest", "ncatminecraft",
                           "block", "RackShapes.java")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview_rack")

C = 8.0
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))

STEEL = "cabinet_steel"
STEEL_DARK = "cabinet_steel_dark"
STEEL_EDGE = "cabinet_edge"
INTERIOR = "interior"
RAIL = "rail"
MESH = "mesh"
VENT = "vent"
FAN = "fan"
GLASS = "glass"
ALU = "alu"
HANDLE = "handle"
BADGE = "badge"
RUBBER = "rubber"
LED_GREEN_ON = "ncat_minecraft:block/pole/led_green_on"


def texture(painter, size=16):
    image = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            value = painter(x, y)
            image.putpixel((x, y), tuple(int(v) for v in value) if len(value) == 4
                           else tuple(int(v) for v in value) + (255,))
    return image


def brushed(base, salt, streak=0.55):
    def painter(x, y):
        grain = noise(x * 4, y, salt) * 0.10 - 0.05
        band = math.sin(y * 1.9 + salt) * 0.014 * streak
        return tint(base, 1.0 + grain + band)
    return painter


def edge_paint(x, y):
    base = (86, 94, 104)
    if y in (0, 15):
        base = (122, 132, 142)
    return tint(base, 0.97 + noise(x, y, 431) * 0.06)


def interior_paint(x, y):
    base = (26, 31, 38)
    if x % 8 == 0:
        base = (18, 22, 28)
    return tint(base, 0.94 + noise(x, y, 433) * 0.10)


def rail_paint(x, y):
    base = (74, 81, 91)
    row = y % 4
    if row == 1 and 4 <= x <= 6:
        base = (14, 17, 21)
    if row == 1 and 9 <= x <= 11:
        base = (14, 17, 21)
    if x in (1, 14):
        base = (104, 112, 122)
    return tint(base, 0.96 + noise(x, y, 437) * 0.07)


def mesh_paint(x, y):
    hole = (x % 3 == 1) and (y % 3 == 1)
    base = (12, 15, 19) if hole else (58, 64, 73)
    return tint(base, 0.95 + noise(x, y, 439) * 0.08)


def vent_paint(x, y):
    slot = (y % 3) == 1 and 1 <= x <= 14
    base = (22, 26, 32) if slot else (88, 96, 106)
    if slot and (x % 5) == 0:
        base = (80, 88, 98)
    return tint(base, 0.96 + noise(x, y, 441) * 0.07)


def fan_paint(x, y):
    dx, dy = x - 7.5, y - 7.5
    radius = math.sqrt(dx * dx + dy * dy)
    angle = math.atan2(dy, dx)
    blade = math.sin(angle * 7 + radius * 0.9) > 0.15
    if radius > 7.2:
        return tint((96, 104, 114), 1.0)
    if radius < 2.0:
        return tint((48, 53, 61), 1.0)
    base = (40, 45, 53) if blade else (20, 24, 30)
    return tint(base, 0.94 + noise(x, y, 443) * 0.10)


def alu_paint(x, y):
    base = (168, 174, 182)
    if x in (0, 15) or y in (0, 15):
        base = (198, 204, 212)
    return tint(base, 0.97 + noise(x * 3, y, 445) * 0.06)


def handle_paint(x, y):
    base = (146, 152, 160) if 4 <= x <= 11 else (108, 114, 122)
    return tint(base, 0.96 + vnoise(x, y, 8, 8, 447) * 0.08)


def rubber_paint(x, y):
    return tint((26, 27, 30), 0.92 + noise(x, y, 449) * 0.14)


def glass_paint(x, y):
    dx = x - y * 0.4
    sheen = 0.0
    if 2 <= dx % 16 <= 3:
        sheen = 0.32
    base = mix((96, 124, 140), (186, 214, 228), sheen)
    alpha = 54 + int(sheen * 52)
    if x in (0, 15) or y in (0, 15):
        return tint(base, 1.18) + (168,)
    return tint(base, 1.0) + (alpha,)


def badge_paint(x, y):
    if 1 <= y <= 14 and 1 <= x <= 14:
        if 4 <= y <= 6 and 2 <= x <= 13:
            return tint((92, 198, 224), 1.0)
        if 9 <= y <= 10 and 2 <= x <= 9:
            return tint((150, 170, 184), 1.0)
        return tint((20, 28, 38), 1.0)
    return tint((44, 56, 68), 1.0)


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    painters = {
        STEEL: brushed((64, 70, 80), 401),
        STEEL_DARK: brushed((44, 49, 57), 403),
        STEEL_EDGE: edge_paint,
        INTERIOR: interior_paint,
        RAIL: rail_paint,
        MESH: mesh_paint,
        VENT: vent_paint,
        FAN: fan_paint,
        ALU: alu_paint,
        HANDLE: handle_paint,
        RUBBER: rubber_paint,
        BADGE: badge_paint,
    }
    for name, painter in painters.items():
        texture(painter).save(os.path.join(TEX, f"{name}.png"))
    texture(glass_paint).save(os.path.join(TEX, f"{GLASS}.png"))


def payload(label, elements, particle):
    pieces = [piece for element in elements for piece in split(element)]
    json_elements = [element_json(piece, lambda p: p) for piece in pieces]
    validate(label, json_elements)
    textures = {}
    for piece in pieces:
        for tex in piece["faces"].values():
            textures[tex] = tex if ":" in tex else f"ncat_minecraft:block/rack/{tex}"
    textures["particle"] = particle if ":" in particle else f"ncat_minecraft:block/rack/{particle}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": json_elements}


def write_model(name, elements, particle=STEEL):
    write_json(os.path.join(MODELS, "block", "rack", f"{name}.json"), payload(name, elements, particle))


DOOR_FRONT = 0.35
DOOR_BACK = 1.55
POST_IN = 2.45
POST_OUT = 0.55


def shell(out, y0, y1, top_cap, bottom_cap):
    for x0, x1 in ((POST_OUT, POST_OUT + 1.5), (16.0 - POST_OUT - 1.5, 16.0 - POST_OUT)):
        box(out, "montante frontal", x0, y0, DOOR_BACK, x1, y1, DOOR_BACK + 1.3, STEEL,
            STEEL_EDGE, STEEL_EDGE)
        box(out, "montante traseiro", x0, y0, 13.1, x1, y1, 14.4, STEEL, STEEL_EDGE, STEEL_EDGE)
    for x0, x1 in ((POST_OUT - 0.25, POST_OUT + 0.35), (16.0 - POST_OUT - 0.35, 16.0 - POST_OUT + 0.25)):
        box(out, "lateral", x0, y0, DOOR_BACK + 1.0, x1, y1, 13.4, VENT,
            sides={"north": STEEL, "south": STEEL})
    box(out, "fundo", POST_OUT + 0.3, y0, 14.35, 16.0 - POST_OUT - 0.3, y1, 15.3, MESH,
        sides={"east": STEEL_DARK, "west": STEEL_DARK})
    box(out, "aro da porta", POST_OUT, y0, DOOR_FRONT, 16.0 - POST_OUT, y0 + 0.75, DOOR_BACK,
        STEEL_EDGE)
    box(out, "aro da porta", POST_OUT, y1 - 0.75, DOOR_FRONT, 16.0 - POST_OUT, y1, DOOR_BACK,
        STEEL_EDGE)
    if bottom_cap:
        box(out, "piso", POST_OUT, y0, DOOR_FRONT, 16.0 - POST_OUT, y0 + 0.5, 15.3, STEEL_DARK)
    if top_cap:
        box(out, "teto", POST_OUT, y1 - 0.5, DOOR_FRONT, 16.0 - POST_OUT, y1, 15.3, STEEL_DARK)


def rails(out, y0, y1):
    for x0 in (POST_IN, 16.0 - POST_IN - 1.0):
        box(out, "trilho 19", x0, y0, DOOR_BACK + 0.45, x0 + 1.0, y1, DOOR_BACK + 1.25, RAIL,
            sides={"north": RAIL}, fit={"north": (0, 0, 16, 16)})
        box(out, "trilho traseiro", x0, y0, 12.2, x0 + 1.0, y1, 13.0, RAIL,
            sides={"north": RAIL}, fit={"north": (0, 0, 16, 16)})


def plinth(out):
    box(out, "rodape", POST_OUT + 0.2, 0.0, DOOR_FRONT + 0.2, 16.0 - POST_OUT - 0.2, 1.1, 15.1,
        STEEL_DARK, STEEL_EDGE)
    for x in (2.6, 13.4):
        for z in (2.4, 13.4):
            disc(out, "pe nivelador", x, z, 0.55, 0.0, 0.55, RUBBER)


def crown(out, y):
    box(out, "tampo", POST_OUT - 0.15, y, DOOR_FRONT - 0.1, 16.0 - POST_OUT + 0.15, y + 1.0, 15.45,
        STEEL, STEEL_EDGE, STEEL_DARK)
    for x in (4.9, 11.1):
        disc(out, "exaustor", x, 8.6, 2.2, y + 0.98, y + 1.02, FAN, up=FAN, down=FAN)
    box(out, "passa cabos", 6.0, y + 0.2, 13.9, 10.0, y + 1.02, 15.1, INTERIOR)


def badge_strip(out, y):
    box(out, "etiqueta", 5.2, y, DOOR_BACK - 0.12, 10.8, y + 1.1, DOOR_BACK - 0.02, BADGE,
        sides={"north": BADGE}, fit={"north": (0, 0, 16, 16)})


def build_cabinet(units, base, top):
    out = []
    y0 = 1.1 if base else 0.0
    y1 = 15.0 if top else 16.0
    shell(out, y0, y1, top, base)
    rails(out, y0 + 0.6, y1 - 0.6)
    if base:
        plinth(out)
    if top:
        crown(out, y1)
    badge_strip(out, y0 + 0.4 if base else 0.35)
    return out


def build_wall_door():
    return door_leaf(WALL_DOOR["y0"], WALL_DOOR["y1"])


def build_door(units, base, top):
    return door_leaf(1.35 if base else 0.1, 14.75 if top else 15.9)


def door_leaf(y0, y1):
    out = []
    x0, x1 = POST_OUT + 0.15, 16.0 - POST_OUT - 0.15
    z0, z1 = DOOR_FRONT + 0.05, DOOR_BACK - 0.05
    box(out, "moldura", x0, y0, z0, x0 + 0.9, y1, z1, ALU)
    box(out, "moldura", x1 - 0.9, y0, z0, x1, y1, z1, ALU)
    box(out, "moldura", x0 + 0.9, y0, z0, x1 - 0.9, y0 + 0.9, z1, ALU)
    box(out, "moldura", x0 + 0.9, y1 - 0.9, z0, x1 - 0.9, y1, z1, ALU)
    box(out, "vidro", x0 + 0.85, y0 + 0.85, z0 + 0.38, x1 - 0.85, y1 - 0.85, z0 + 0.62, GLASS)
    mid = (y0 + y1) / 2.0
    box(out, "puxador", x1 - 1.9, mid - 2.1, z0 - 0.75, x1 - 1.3, mid + 2.1, z0 + 0.05, HANDLE)
    box(out, "base do puxador", x1 - 2.0, mid - 2.3, z0 - 0.2, x1 - 1.2, mid - 1.7, z0 + 0.1, ALU)
    box(out, "base do puxador", x1 - 2.0, mid + 1.7, z0 - 0.2, x1 - 1.2, mid + 2.3, z0 + 0.1, ALU)
    disc(out, "fechadura", x1 - 1.6, (z0 + z1) / 2.0, 0.5, mid + 3.0, mid + 3.6, ALU)
    for y in (y0 + 1.6, y1 - 1.6):
        box(out, "dobradica", x0 - 0.35, y - 0.8, z0 + 0.1, x0 + 0.5, y + 0.8, z1, STEEL_EDGE)
    return out


def build_wall_cabinet():
    out = []
    back = 10.4
    y0, y1 = 1.4, 14.6
    for x0, x1 in ((POST_OUT, POST_OUT + 1.4), (16.0 - POST_OUT - 1.4, 16.0 - POST_OUT)):
        box(out, "montante", x0, y0, DOOR_BACK, x1, y1, DOOR_BACK + 1.2, STEEL, STEEL_EDGE,
            STEEL_EDGE)
    for x0, x1 in ((POST_OUT - 0.2, POST_OUT + 0.3), (16.0 - POST_OUT - 0.3, 16.0 - POST_OUT + 0.2)):
        box(out, "lateral", x0, y0, DOOR_BACK + 0.9, x1, y1, back, VENT,
            sides={"north": STEEL, "south": STEEL})
    box(out, "fundo", POST_OUT, y0, back - 0.9, 16.0 - POST_OUT, y1, back, MESH,
        sides={"east": STEEL_DARK, "west": STEEL_DARK})
    box(out, "teto", POST_OUT - 0.2, y1, DOOR_FRONT - 0.1, 16.0 - POST_OUT + 0.2, y1 + 0.9, back + 0.2,
        STEEL, STEEL_EDGE, STEEL_DARK)
    box(out, "piso", POST_OUT - 0.2, y0 - 0.9, DOOR_FRONT - 0.1, 16.0 - POST_OUT + 0.2, y0, back + 0.2,
        STEEL, STEEL_EDGE, STEEL_DARK)
    disc(out, "exaustor", 8.0, back - 3.4, 2.0, y1 + 0.86, y1 + 0.92, FAN, up=FAN, down=FAN)
    for x0 in (POST_IN, 16.0 - POST_IN - 1.0):
        box(out, "trilho 19", x0, y0 + 0.5, DOOR_BACK + 0.4, x0 + 1.0, y1 - 0.5, DOOR_BACK + 1.2,
            RAIL, sides={"north": RAIL}, fit={"north": (0, 0, 16, 16)})
    for y in (y0 + 1.2, y1 - 1.2):
        box(out, "suporte de parede", 2.2, y - 0.6, back, 13.8, y + 0.6, back + 1.1, STEEL_DARK)
    badge_strip(out, y0 + 0.2)
    return out


RACKS = {
    "rack_frame_12u": dict(units=12, base=True, top=False),
    "rack_frame_18u": dict(units=18, base=False, top=True),
}

WALL_DOOR = dict(y0=1.65, y1=14.35)


HINGE = (POST_OUT + 0.15, 0.0, DOOR_FRONT + 0.05)


def write_shapes(entries):
    lines = ["package com.netcattest.ncatminecraft.block;", "",
             "public final class RackShapes {", "",
             "    private RackShapes() {", "    }", ""]
    for name, hinge_point in entries:
        values = ", ".join(f"{v:.3f}" for v in hinge_point)
        lines.append(f"    public static final double[] {name} = {{{values}}};")
    lines.append("}")
    with open(SHAPES_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


def item_display():
    return {
        "gui": {"rotation": [24, 215, 0], "translation": [0, 0, 0], "scale": [.62, .62, .62]},
        "ground": {"translation": [0, 2, 0], "scale": [.50, .50, .50]},
        "fixed": {"rotation": [0, 180, 0], "scale": [.82, .82, .82]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [.48, .48, .48]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [.65, .65, .65]},
    }


def main():
    make_textures()
    shapes = []
    builds = {name: (build_cabinet(**spec), build_door(**spec)) for name, spec in RACKS.items()}
    builds["rack_wall_6u"] = (build_wall_cabinet(), build_wall_door())
    for name, (cabinet, door) in builds.items():
        write_model(name, cabinet)
        write_model(name + "_door", door, ALU)
        write_json(os.path.join(MODELS, "item", f"{name}.json"), {
            "parent": f"ncat_minecraft:block/rack/{name}",
            "display": item_display(),
        })
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": {
            f"facing={facing}": ({"model": f"ncat_minecraft:block/rack/{name}"} if not angle
                                 else {"model": f"ncat_minecraft:block/rack/{name}", "y": angle})
            for facing, angle in FACINGS
        }})
        shapes.append((name.upper() + "_HINGE", HINGE))
        print(f"{name}: {len(cabinet)} elementos + porta {len(door)}")
    write_shapes(shapes)
    if os.environ.get("RACK_PREVIEW") == "1":
        save_preview()


def save_preview():
    import shutil
    import tempfile
    os.makedirs(PREVIEW, exist_ok=True)
    merged = tempfile.mkdtemp(prefix="ncat_rack_tex_")
    for folder in (poles.TEX, TEX):
        for entry in os.listdir(folder):
            if entry.endswith(".png"):
                shutil.copyfile(os.path.join(folder, entry), os.path.join(merged, entry))
    original = poles.TEX
    poles.TEX = merged
    try:
        stack = poles.load_elements(os.path.join(MODELS, "block", "rack", "rack_frame_12u.json"), 0.0)
        stack += poles.load_elements(os.path.join(MODELS, "block", "rack", "rack_frame_18u.json"), 16.0)
        closed = stack + door_at("rack_frame_12u_door", 0.0, 0.0) + door_at("rack_frame_18u_door", 16.0, 0.0)
        opened = stack + door_at("rack_frame_12u_door", 0.0, 108.0) + door_at("rack_frame_18u_door", 16.0, 108.0)
        wall = poles.load_elements(os.path.join(MODELS, "block", "rack", "rack_wall_6u.json"), 0.0)
        wall_open = wall + door_at("rack_wall_6u_door", 0.0, 108.0)
        tiles = [
            poles.render(closed, -30, 12, 9.0, (380, 620), (C, 16.0, C)),
            poles.render(opened, -30, 12, 9.0, (380, 620), (C, 16.0, C)),
            poles.render(opened, -72, 6, 9.0, (380, 620), (C, 16.0, C)),
            poles.render(wall_open, -32, 14, 18.0, (380, 620), (C, 8.0, C)),
        ]
        sheet = Image.new("RGB", (380 * len(tiles), 620), (16, 18, 22))
        for index, tile in enumerate(tiles):
            sheet.paste(tile, (index * 380, 0))
        sheet.save(os.path.join(PREVIEW, "rack.png"))
        print("preview em", PREVIEW)
    finally:
        poles.TEX = original
        shutil.rmtree(merged, ignore_errors=True)


def door_at(name, dy, angle):
    elements = poles.load_elements(os.path.join(MODELS, "block", "rack", f"{name}.json"), dy)
    if angle:
        origin = HINGE
        for element in elements:
            element["rotation"] = {"origin": list(origin), "axis": "y", "angle": angle}
    return elements


if __name__ == "__main__":
    main()
