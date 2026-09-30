
import math
import os

from PIL import Image

import gen_poles as poles
from gen_poles import box, clamp, disc, disc_z, element_json, mix, noise, split, tint, validate, \
    vnoise, write_json

ASSETS = poles.ASSETS
DATA = poles.DATA
TEX = os.path.join(ASSETS, "textures", "block", "electrical")
MODELS = os.path.join(ASSETS, "models")
SHAPES_JAVA = os.path.join(poles.ROOT, "src", "main", "java", "com", "netcattest", "ncatminecraft",
                           "block", "ElectricalShapes.java")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview_electrical")

C = 8.0
EPS = 0.01
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))

STEEL = "panel_steel"
STEEL_DARK = "panel_steel_dark"
INNER = "panel_inner"
DOOR = "panel_door"
RAIL = "din_rail"
BREAKER = "breaker"
BREAKER_DARK = "breaker_dark"
BREAKER_TOP = "breaker_top"
LEVER_ON = "lever_on"
LEVER_OFF = "lever_off"
BUSBAR = "busbar"
TERMINAL = "terminal_block"
SCHEDULE = "schedule"
PLATE_WHITE = "plate_white"
PLATE_IVORY = "plate_ivory"
PLATE_RED = "plate_red"
PLATE_BLUE = "plate_blue"
PLATE_GREY = "plate_grey"
RECESS = "recess"
HOLE = "pin_hole"
CONDUIT = "conduit"
CONDUIT_DARK = "conduit_dark"
LABEL_20A = "label_20a"
UPS_BODY = "ups_body"
UPS_DARK = "ups_dark"
CORD = "cord"
VENT = "vent_slot"
LED_GREEN_ON = "ncat_minecraft:block/pole/led_green_on"
LED_GREEN_OFF = "ncat_minecraft:block/pole/led_green_off"
LED_RED_ON = "ncat_minecraft:block/pole/led_red_on"
LED_RED_OFF = "ncat_minecraft:block/pole/led_red_off"
SWITCH_KEY = "switch_key"
LAMP_OFF = "lamp_off"
LAMP_ON = "lamp_on"
LAMP_RING = "lamp_ring"



def texture(painter):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            red, green, blue = painter(x, y)[:3]
            image.putpixel((x, y), (clamp(red), clamp(green), clamp(blue), 255))
    return image


def painted_steel(base, salt, speckle=0.94):
    def paint(x, y):
        delta = (vnoise(x, y, 8, 8, salt) - 0.5) * 9 + (noise(x, y, salt + 1) - 0.5) * 5
        if noise(x, y, salt + 2) > speckle:
            delta += 8
        return tint(base, delta)
    return paint


def moulded(base, salt, gloss=0.93):
    def paint(x, y):
        delta = (vnoise(x, y, 16, 16, salt) - 0.5) * 6 + (noise(x, y, salt + 1) - 0.5) * 3
        if noise(x, y, salt + 2) > gloss:
            delta += 7
        return tint(base, delta)
    return paint


def rail_paint(x, y):
    base = (176, 180, 184)
    delta = (vnoise(x, y, 4, 4, 201) - 0.5) * 14 + (noise(x, y, 202) - 0.5) * 6
    if 6 <= y <= 9 and x % 8 in (2, 3, 4):
        return tint((48, 50, 54), delta * 0.4)
    if y in (0, 15):
        delta -= 26
    return tint(base, delta)


def busbar_paint(x, y):
    base = (196, 186, 168)
    delta = (vnoise(x, y, 8, 4, 205) - 0.5) * 12 + (noise(x, y, 206) - 0.5) * 6
    if x % 4 == 1 and 5 <= y <= 10:
        return tint((42, 40, 38), 0)
    return tint(base, delta)


def terminal_paint(x, y):
    base = (58, 60, 66)
    delta = (noise(x, y, 208) - 0.5) * 8
    if x % 4 == 2 and 4 <= y <= 11:
        return tint((176, 176, 172), (noise(x, y, 209) - 0.5) * 14)
    if y in (0, 15):
        delta -= 14
    return tint(base, delta)


def breaker_paint(x, y):
    base = (66, 68, 74)
    delta = (vnoise(x, y, 8, 8, 211) - 0.5) * 7 + (noise(x, y, 212) - 0.5) * 4
    if 2 <= x <= 13 and 2 <= y <= 5:
        base = (226, 226, 220)
        if 4 <= x <= 11 and y == 3 and noise(x, y, 213) > 0.25:
            base = (36, 36, 40)
    if y in (0, 15) or x in (0, 15):
        delta -= 16
    return tint(base, delta)


def lever_paint(colour, salt):
    def paint(x, y):
        delta = (vnoise(x, y, 8, 8, salt) - 0.5) * 8 + (noise(x, y, salt + 1) - 0.5) * 4
        if y in (0, 15):
            delta -= 18
        return tint(colour, delta)
    return paint


def recess_paint(x, y):
    distance = math.hypot(x - 7.5, y - 7.5)
    colour = (44, 45, 48) if distance < 6.2 else (86, 88, 92)
    return tint(colour, (noise(x, y, 221) - 0.5) * 7)


def hole_paint(x, y):
    return tint((16, 16, 18), (noise(x, y, 223) - 0.5) * 5)


def schedule_paint(x, y):
    if x in (0, 15) or y in (0, 15):
        return tint((196, 192, 182), (noise(x, y, 231) - 0.5) * 6)
    colour = (238, 236, 228)
    if y in (2, 5, 8, 11, 14):
        colour = (168, 170, 176)
    elif 2 <= x <= 12 and y % 3 == 0 and noise(x, y, 232) > 0.4:
        colour = (52, 54, 60)
    return tint(colour, (noise(x, y, 233) - 0.5) * 6)


def label_paint(x, y):
    distance = math.hypot(x - 7.5, y - 7.5)
    if 5.4 < distance < 6.8:
        return tint((196, 58, 46), (noise(x, y, 241) - 0.5) * 10)
    return tint((238, 238, 234), (noise(x, y, 242) - 0.5) * 5)


def cord_paint(x, y):
    shade = 0.78 + 0.34 * max(0.0, math.cos((x / 16.0) * math.tau - 2.0))
    base = (38, 39, 42)
    if x % 5 == 2:
        base = (58, 60, 64)
    delta = (noise(x, y, 281) - 0.5) * 6
    return tint(tuple(component * shade for component in base), delta)


def vent_paint(x, y):
    if y % 3 == 0:
        return tint((22, 23, 25), (noise(x, y, 283) - 0.5) * 6)
    return tint((74, 76, 80), (vnoise(x, y, 8, 8, 284) - 0.5) * 8)


def conduit_paint(base, salt):
    def paint(x, y):
        shade = 0.82 + 0.3 * max(0.0, math.cos((x / 16.0) * math.tau - 2.0))
        delta = (vnoise(x, y, 16, 4, salt) - 0.5) * 6 + (noise(x, y, salt + 1) - 0.5) * 3
        return tint(tuple(component * shade for component in base), delta)
    return paint


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    painters = {
        STEEL: painted_steel((198, 198, 192), 101),
        STEEL_DARK: painted_steel((150, 150, 146), 103),
        INNER: painted_steel((226, 226, 220), 105, speckle=0.97),
        DOOR: painted_steel((206, 206, 200), 107),
        RAIL: rail_paint,
        BREAKER: breaker_paint,
        BREAKER_DARK: moulded((46, 48, 52), 215),
        BREAKER_TOP: moulded((78, 80, 86), 217),
        LEVER_ON: lever_paint((198, 62, 48), 219),
        LEVER_OFF: lever_paint((36, 38, 42), 220),
        BUSBAR: busbar_paint,
        TERMINAL: terminal_paint,
        SCHEDULE: schedule_paint,
        PLATE_WHITE: moulded((232, 232, 228), 251),
        PLATE_IVORY: moulded((228, 220, 198), 253),
        PLATE_RED: moulded((176, 52, 42), 255),
        PLATE_BLUE: moulded((44, 76, 148), 257),
        PLATE_GREY: moulded((132, 134, 138), 259),
        RECESS: recess_paint,
        HOLE: hole_paint,
        LABEL_20A: label_paint,
        UPS_BODY: moulded((52, 54, 58), 271),
        UPS_DARK: moulded((34, 35, 38), 273),
        CORD: cord_paint,
        VENT: vent_paint,
        CONDUIT: conduit_paint((196, 198, 196), 261),
        CONDUIT_DARK: conduit_paint((146, 148, 148), 263),
        SWITCH_KEY: moulded((238, 238, 233), 285),
        LAMP_OFF: moulded((226, 227, 220), 287, gloss=0.97),
        LAMP_ON: glow_paint,
        LAMP_RING: painted_steel((206, 208, 206), 289),
    }
    for name, painter in painters.items():
        texture(painter).save(os.path.join(TEX, f"{name}.png"))



def payload(label, elements, particle, parent="minecraft:block/block"):
    pieces = [piece for element in elements for piece in split(element)]
    json_elements = [element_json(piece, lambda p: p) for piece in pieces]
    validate(label, json_elements)
    textures = {}
    for piece in pieces:
        for tex in piece["faces"].values():
            textures[tex] = tex if ":" in tex else f"ncat_minecraft:block/electrical/{tex}"
    textures["particle"] = particle if ":" in particle else f"ncat_minecraft:block/electrical/{particle}"
    return {"parent": parent, "textures": textures, "elements": json_elements}


def write_block(name, elements, particle):
    write_json(os.path.join(MODELS, "block", "electrical", f"{name}.json"),
               payload(name, elements, particle), compact=True)
    return len(elements)


ITEM_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.86, 0.86, 0.86]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.36, 0.36, 0.36]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
    "thirdperson_righthand": {"rotation": [72, 40, 0], "translation": [0, 3.0, 0], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1.5, 0], "scale": [0.46, 0.46, 0.46]},
}


def write_item(name, elements, particle, span=None):
    pieces = [piece for element in elements for piece in split(element)]
    bounds = []
    for axis in range(3):
        values = [v for p in pieces for v in (p["from"][axis], p["to"][axis])]
        bounds.append((min(values), max(values)))
    scale = min(span or 13.0 / max(hi - lo for lo, hi in bounds), 1.6)
    mids = [(lo + hi) / 2 for lo, hi in bounds]

    def transform(point):
        return tuple((point[i] - mids[i]) * scale + 8.0 for i in range(3))

    json_elements = [element_json(piece, transform) for piece in pieces]
    validate("item " + name, json_elements)
    textures = {}
    for piece in pieces:
        for tex in piece["faces"].values():
            textures[tex] = tex if ":" in tex else f"ncat_minecraft:block/electrical/{tex}"
    textures["particle"] = particle if ":" in particle else f"ncat_minecraft:block/electrical/{particle}"
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": json_elements,
             "display": ITEM_DISPLAY}
    write_json(os.path.join(MODELS, "item", f"{name}.json"), model, compact=True)


def write_facing_blockstate(name, model):
    variants = {}
    for facing, angle in FACINGS:
        entry = {"model": f"ncat_minecraft:block/electrical/{model}"}
        if angle:
            entry["y"] = angle
        variants[f"facing={facing}"] = entry
    write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": variants})


def write_loot(name):
    block = f"ncat_minecraft:{name}"
    write_json(os.path.join(DATA, "loot_tables", "blocks", f"{name}.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": block}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })



def screw(out, x, y, z):
    disc_z(out, "parafuso", x, y, 0.42, z - 0.22, z + 0.02, STEEL_DARK)
    box(out, "fenda", x - 0.34, y - 0.07, z - 0.26, x + 0.34, y + 0.07, z - 0.16, BREAKER_DARK)


PLATE_FRONT = 13.6
PLATE_BACK = 15.4
RECESS_FLOOR = 14.95
HOLE_FRONT = 14.55


def plate_frame(out, plate, half, openings):
    edge = C - half
    for cy, size in openings:
        if cy - size > edge + 0.01:
            box(out, "espelho", C - half, edge, PLATE_FRONT, C + half, cy - size, PLATE_BACK, plate)
        box(out, "espelho", C - half, cy - size, PLATE_FRONT, C - size, cy + size, PLATE_BACK, plate)
        box(out, "espelho", C + size, cy - size, PLATE_FRONT, C + half, cy + size, PLATE_BACK, plate)
        edge = cy + size
    if edge < C + half - 0.01:
        box(out, "espelho", C - half, edge, PLATE_FRONT, C + half, C + half, PLATE_BACK, plate)
    for lo, hi, vertical in ((C - half, C - half + 0.6, False), (C + half - 0.6, C + half, False)):
        box(out, "aba do espelho", C - half, lo, PLATE_FRONT - 0.45, C + half, hi, PLATE_FRONT, plate)
    for lo, hi in ((C - half, C - half + 0.6), (C + half - 0.6, C + half)):
        box(out, "aba do espelho", lo, C - half + 0.6, PLATE_FRONT - 0.45, hi, C + half - 0.6,
            PLATE_FRONT, plate)


def recess(out, cy, size, floor=RECESS_FLOOR):
    box(out, "fundo do alojamento", C - size, cy - size, floor, C + size, cy + size, PLATE_BACK + 0.4,
        RECESS)


def pin_hole(out, x, y, width=0.5, height=None, round_hole=True):
    height = width if height is None else height
    if round_hole:
        disc_z(out, "alveolo", x, y, width, HOLE_FRONT, RECESS_FLOOR + 0.05, HOLE)
    else:
        box(out, "alveolo", x - width, y - height, HOLE_FRONT, x + width, y + height,
            RECESS_FLOOR + 0.05, HOLE)


def socket_nbr(out, cy, amps):
    pin = 0.46 if amps == 10 else 0.58
    recess(out, cy, 2.9)
    pin_hole(out, C - 1.5, cy + 0.62, pin)
    pin_hole(out, C + 1.5, cy + 0.62, pin)
    pin_hole(out, C, cy - 1.5, pin)


def socket_nema(out, cy):
    recess(out, cy, 2.9)
    pin_hole(out, C - 1.45, cy + 0.7, 0.24, 0.95, round_hole=False)
    pin_hole(out, C + 1.45, cy + 0.7, 0.36, 0.95, round_hole=False)
    pin_hole(out, C, cy - 1.6, 0.44)


def socket_schuko(out, cy):
    recess(out, cy, 3.1)
    pin_hole(out, C - 1.45, cy, 0.52)
    pin_hole(out, C + 1.45, cy, 0.52)
    for sign in (-1, 1):
        box(out, "contato de terra", C - 1.3, cy + sign * 2.35, HOLE_FRONT - 0.15, C + 1.3,
            cy + sign * 2.95, RECESS_FLOOR, STEEL_DARK)


def socket_industrial(out, cy):
    recess(out, cy, 3.3)
    disc_z(out, "corpo", C, cy, 3.25, PLATE_FRONT - 2.2, PLATE_FRONT + 0.4, PLATE_RED)
    disc_z(out, "gola", C, cy, 3.6, PLATE_FRONT - 0.9, PLATE_FRONT - 0.3, PLATE_RED)
    disc_z(out, "alojamento", C, cy, 2.4, PLATE_FRONT - 2.55, PLATE_FRONT - 2.15, RECESS)
    for angle in (90, 210, 330):
        px = C + math.cos(math.radians(angle)) * 1.35
        py = cy + math.sin(math.radians(angle)) * 1.35
        disc_z(out, "alveolo", px, py, 0.5, PLATE_FRONT - 2.75, PLATE_FRONT - 2.1, HOLE)
    disc_z(out, "alveolo de terra", C, cy, 0.5, PLATE_FRONT - 2.75, PLATE_FRONT - 2.1, HOLE)
    box(out, "chaveta", C - 0.45, cy - 3.5, PLATE_FRONT - 2.7, C + 0.45, cy - 2.6,
        PLATE_FRONT - 2.0, PLATE_RED)
    box(out, "tampa", C - 3.3, cy + 2.6, PLATE_FRONT - 3.4, C + 3.3, cy + 4.9,
        PLATE_FRONT - 2.1, PLATE_RED)
    box(out, "dobradica da tampa", C - 1.3, cy + 4.6, PLATE_FRONT - 3.0, C + 1.3, cy + 5.5,
        PLATE_FRONT - 2.4, STEEL_DARK)


SOCKETS = {
    "outlet_nbr_10a": dict(plate=PLATE_WHITE, half=4.8, openings=[(C, 2.9)],
                           build=lambda out: socket_nbr(out, C, 10)),
    "outlet_nbr_20a": dict(plate=PLATE_WHITE, half=4.8, openings=[(C, 2.9)], label=LABEL_20A,
                           build=lambda out: socket_nbr(out, C, 20)),
    "outlet_nbr_double": dict(plate=PLATE_WHITE, half=6.6, openings=[(C - 3.2, 2.7), (C + 3.2, 2.7)],
                              build=lambda out: (socket_nbr(out, C - 3.2, 10),
                                                 socket_nbr(out, C + 3.2, 10))),
    "outlet_nema": dict(plate=PLATE_IVORY, half=4.8, openings=[(C, 2.9)],
                        build=lambda out: socket_nema(out, C)),
    "outlet_schuko": dict(plate=PLATE_WHITE, half=5.2, openings=[(C, 3.1)],
                          build=lambda out: socket_schuko(out, C)),
    "outlet_industrial": dict(plate=PLATE_GREY, half=5.6, openings=[(C, 3.3)],
                              build=lambda out: socket_industrial(out, C)),
}


def glow_paint(x, y):
    dx, dy = x - 7.5, y - 7.5
    fall = min(1.0, max(0.0, 1.0 - math.sqrt(dx * dx + dy * dy) / 11.0))
    base = mix((255, 236, 186), (255, 252, 238), fall * fall)
    return tint(base, noise(x, y, 291) * 0.03 + 0.985)


SWITCH_HALF = 4.8
SWITCH_KEY_HALF = 2.7
KEY_TILT = 1.15


def switch_key_face(out, cy, on):
    box(out, "alojamento da tecla", C - SWITCH_KEY_HALF - 0.3, cy - SWITCH_KEY_HALF - 0.3,
        RECESS_FLOOR - 0.5, C + SWITCH_KEY_HALF + 0.3, cy + SWITCH_KEY_HALF + 0.3,
        PLATE_BACK + 0.2, RECESS)
    z0, z1 = PLATE_FRONT - 0.5, RECESS_FLOOR - 0.1
    rot = poles.rotation("x", -22.5 if on else 22.5, (C, cy, (z0 + z1) / 2))
    box(out, "tecla", C - SWITCH_KEY_HALF, cy - SWITCH_KEY_HALF + 0.2, z0,
        C + SWITCH_KEY_HALF, cy + SWITCH_KEY_HALF - 0.2, z1, SWITCH_KEY, rot=rot)
    box(out, "ponto da tecla", C - 0.8, cy - SWITCH_KEY_HALF + 0.65, z0 - 0.12, C + 0.8,
        cy - SWITCH_KEY_HALF + 1.45, z0 + 0.02, PLATE_RED if on else SWITCH_KEY, rot=rot)


def build_wall_switch():
    shell = []
    box(shell, "caixa embutida", C - SWITCH_HALF + 1.2, C - SWITCH_HALF + 1.2, PLATE_BACK + 0.2,
        C + SWITCH_HALF - 1.2, C + SWITCH_HALF - 1.2, 16.0, STEEL_DARK)
    plate_frame(shell, PLATE_WHITE, SWITCH_HALF, [(C, SWITCH_KEY_HALF + 0.4)])
    screw(shell, C, C + SWITCH_HALF - 0.9, PLATE_FRONT - 0.45)
    screw(shell, C, C - SWITCH_HALF + 0.9, PLATE_FRONT - 0.45)
    on, off = [], []
    switch_key_face(on, C, True)
    switch_key_face(off, C, False)
    hit = (C - SWITCH_HALF, C - SWITCH_HALF, PLATE_FRONT - KEY_TILT - 0.2, C + SWITCH_HALF,
           C + SWITCH_HALF, 16.0)
    return shell, on, off, hit


def build_ceiling_light(on):
    out = []
    lamp = LAMP_ON if on else LAMP_OFF
    box(out, "base", C - 5.4, 15.2, C - 5.4, C + 5.4, 16.0, C + 5.4, LAMP_RING)
    box(out, "corpo", C - 5.0, 13.9, C - 5.0, C + 5.0, 15.3, C + 5.0, LAMP_RING)
    box(out, "moldura", C - 5.3, 13.3, C - 5.3, C + 5.3, 14.0, C + 5.3, LAMP_RING)
    box(out, "difusor", C - 4.8, 12.8, C - 4.8, C + 4.8, 13.5, C + 4.8, lamp, glow=on)
    return out


def build_pendant_lamp(on):
    out = []
    box(out, "canopla", C - 1.7, 15.0, C - 1.7, C + 1.7, 16.0, C + 1.7, LAMP_RING)
    box(out, "fio", C - 0.32, 9.4, C - 0.32, C + 0.32, 15.1, C + 0.32, CORD)
    disc(out, "soquete", C, C, 1.35, 7.4, 9.5, LAMP_RING)
    disc(out, "bulbo", C, C, 2.5, 3.9, 7.6, LAMP_ON if on else LAMP_OFF, glow=on)
    disc(out, "ponta do bulbo", C, C, 1.25, 3.1, 4.1, LAMP_ON if on else LAMP_OFF, glow=on)
    return out


LAMPS = {
    "ceiling_light": dict(build=build_ceiling_light, hit=(2.6, 12.8, 2.6, 13.4, 16.0, 13.4)),
    "pendant_lamp": dict(build=build_pendant_lamp, hit=(5.5, 3.1, 5.5, 10.5, 16.0, 10.5)),
}


def indicator(x, y):
    on, off = [], []
    front = PLATE_FRONT - 0.5
    box(on, "led aceso", x - 0.55, y - 0.55, front, x + 0.55, y + 0.55, front + 0.4,
        LED_GREEN_ON, fit={"north": (0, 0, 16, 16)}, glow=True)
    box(off, "led apagado", x - 0.55, y - 0.55, front, x + 0.55, y + 0.55, front + 0.4,
        LED_GREEN_OFF, fit={"north": (0, 0, 16, 16)})
    return on, off


def build_socket(name):
    spec = SOCKETS[name]
    plate, half, openings = spec["plate"], spec["half"], spec["openings"]
    out = []
    box(out, "caixa embutida", C - half + 1.2, C - half + 1.2, PLATE_BACK + 0.2, C + half - 1.2,
        C + half - 1.2, 16.0, STEEL_DARK)
    plate_frame(out, plate, half, openings)
    spec["build"](out)
    if spec.get("label"):
        for sign in (-1, 1):
            box(out, "marcacao", C - half + 0.7, C + sign * (half - 1.0) - 0.35, PLATE_FRONT - 0.5,
                C + half - 0.7, C + sign * (half - 1.0) + 0.35, PLATE_FRONT - 0.44, PLATE_RED)
    screw(out, C, C + half - 0.9, PLATE_FRONT - 0.45)
    screw(out, C, C - half + 0.9, PLATE_FRONT - 0.45)
    front = PLATE_FRONT - 3.5 if name == "outlet_industrial" else PLATE_FRONT - 0.5
    on, off = indicator(C + half - 1.6, C - half + 1.5)
    hit = (C - half, C - half, front, C + half, C + half, 16.0)
    return out, on, off, hit



BREAKER_WIDTH = 1.8
BREAKER_GAP = 0.18
RAIL_Y = 7.9
BREAKER_BOTTOM, BREAKER_TOP_Y = 5.6, 10.6
PANEL_FRONT = 11.4
BREAKER_SLOTS = 6


def breaker_x(slot):
    return 2.4 + slot * (BREAKER_WIDTH + BREAKER_GAP)


def breaker_module(out, x0, width):
    box(out, "disjuntor", x0, BREAKER_BOTTOM, 12.5, x0 + width, BREAKER_TOP_Y, 14.6, BREAKER,
        up=BREAKER_TOP, down=BREAKER_TOP, sides={"west": BREAKER_DARK, "east": BREAKER_DARK})
    box(out, "recorte da alavanca", x0 + 0.3, RAIL_Y + 0.9, 12.3, x0 + width - 0.3, RAIL_Y + 2.5,
        12.56, BREAKER_DARK)
    box(out, "borne superior", x0 + 0.35, BREAKER_TOP_Y - 0.05, 13.0, x0 + width - 0.35,
        BREAKER_TOP_Y + 0.5, 14.2, TERMINAL)
    box(out, "borne inferior", x0 + 0.35, BREAKER_BOTTOM - 0.5, 13.0, x0 + width - 0.35,
        BREAKER_BOTTOM + 0.05, 14.2, TERMINAL)


def build_panel():
    out = []
    back, inner = 15.2, 14.6
    box(out, "fundo da caixa", 1.0, 0.6, back, 15.0, 15.4, 16.0, STEEL_DARK)
    box(out, "lateral esquerda", 1.0, 0.6, PANEL_FRONT, 1.9, 15.4, back, STEEL, STEEL_DARK, STEEL_DARK)
    box(out, "lateral direita", 14.1, 0.6, PANEL_FRONT, 15.0, 15.4, back, STEEL, STEEL_DARK, STEEL_DARK)
    box(out, "travessa superior", 1.9, 14.5, PANEL_FRONT, 14.1, 15.4, back, STEEL, STEEL, STEEL_DARK)
    box(out, "travessa inferior", 1.9, 0.6, PANEL_FRONT, 14.1, 1.5, back, STEEL, STEEL_DARK, STEEL)
    box(out, "espelho interno", 1.9, 1.5, inner, 14.1, 14.5, back, INNER)
    for lo, hi in ((0.6, 1.0), (15.0, 15.4)):
        box(out, "batente", lo, 0.2, PANEL_FRONT - 0.4, hi, 15.8, PANEL_FRONT, STEEL_DARK)
    for lo, hi in ((0.2, 0.6), (15.4, 15.8)):
        box(out, "batente", 0.6, lo, PANEL_FRONT - 0.4, 15.4, hi, PANEL_FRONT, STEEL_DARK)
    box(out, "trilho din", 2.0, RAIL_Y - 0.7, inner - 0.75, 14.0, RAIL_Y + 0.7, inner, RAIL)
    breaker_module(out, breaker_x(0), BREAKER_WIDTH * 2 + BREAKER_GAP)
    for slot in range(2, BREAKER_SLOTS):
        breaker_module(out, breaker_x(slot), BREAKER_WIDTH)
    box(out, "barramento de neutro", 2.2, 3.1, inner - 0.7, 13.8, 4.1, inner, BUSBAR)
    box(out, "barramento de terra", 2.2, 1.8, inner - 0.7, 13.8, 2.8, inner, BUSBAR)
    box(out, "canaleta superior", 2.0, 12.3, inner - 0.9, 14.0, 13.7, inner, STEEL_DARK)
    box(out, "etiqueta de circuitos", 2.4, 12.5, inner - 0.96, 13.6, 13.5, inner - 0.9, INNER,
        sides={"north": SCHEDULE}, fit={"north": (0, 0, 16, 16)})
    for slot in range(2, BREAKER_SLOTS):
        x = breaker_x(slot) + BREAKER_WIDTH / 2
        box(out, "alimentador", x - 0.17, BREAKER_TOP_Y + 0.45, inner - 0.55, x + 0.17, 12.3,
            inner - 0.21, BREAKER_DARK)
    for slot in range(2, BREAKER_SLOTS):
        x = breaker_x(slot) + BREAKER_WIDTH / 2
        box(out, "retorno do circuito", x - 0.17, 4.1, inner - 0.55, x + 0.17,
            BREAKER_BOTTOM - 0.45, inner - 0.21, BREAKER_DARK)
    return out


def build_panel_door():
    out = []
    box(out, "porta", 0.6, 0.2, PANEL_FRONT - 1.3, 15.4, 15.8, PANEL_FRONT - 0.3, DOOR, STEEL_DARK,
        STEEL_DARK)
    box(out, "reforco da porta", 1.2, 0.8, PANEL_FRONT - 0.3, 14.8, 15.2, PANEL_FRONT - 0.05, STEEL_DARK)
    for y in (2.4, 13.4):
        box(out, "dobradica", 0.35, y - 0.9, PANEL_FRONT - 1.1, 0.95, y + 0.9, PANEL_FRONT - 0.1,
            STEEL_DARK)
    box(out, "puxador", 13.6, 6.6, PANEL_FRONT - 2.1, 14.6, 9.4, PANEL_FRONT - 1.2, STEEL_DARK)
    box(out, "fecho", 14.1, 7.6, PANEL_FRONT - 2.6, 14.5, 8.4, PANEL_FRONT - 2.0, BREAKER_DARK)
    return out


def build_lever(texture_name):
    out = []
    box(out, "alavanca", 0.0, 0.0, 0.0, 1.0, 1.85, 0.95, texture_name)
    box(out, "ponta da alavanca", 0.12, 1.7, 0.08, 0.88, 2.15, 0.87, texture_name)
    return out


def build_led(texture_name):
    out = []
    box(out, "led", 0.0, 0.0, 0.0, 1.1, 1.1, 0.4, texture_name, fit={"north": (0, 0, 16, 16)},
        glow=texture_name.endswith("_on"))
    return out



CONDUIT_CORE = 2.6
CONDUIT_ARM = 2.1

ARMS = {
    "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
    "west": (-1, 0, 0), "east": (1, 0, 0),
}


def build_conduit_core():
    out = []
    box(out, "caixa de passagem", C - CONDUIT_CORE, C - CONDUIT_CORE, C - CONDUIT_CORE,
        C + CONDUIT_CORE, C + CONDUIT_CORE, C + CONDUIT_CORE, CONDUIT, CONDUIT_DARK, CONDUIT_DARK)
    return out


def build_conduit_arm(direction):
    dx, dy, dz = ARMS[direction]
    out = []
    lo = [C - CONDUIT_ARM] * 3
    hi = [C + CONDUIT_ARM] * 3
    axis = 0 if dx else (1 if dy else 2)
    step = dx or dy or dz
    if step > 0:
        lo[axis] = C + CONDUIT_CORE - EPS
        hi[axis] = 16.0
    else:
        lo[axis] = 0.0
        hi[axis] = C - CONDUIT_CORE + EPS
    box(out, "eletroduto", lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], CONDUIT, CONDUIT_DARK, CONDUIT_DARK)
    collar_lo = list(lo)
    collar_hi = list(hi)
    for other in range(3):
        if other != axis:
            collar_lo[other] -= 0.32
            collar_hi[other] += 0.32
    if step > 0:
        collar_lo[axis] = C + CONDUIT_CORE + 1.4
        collar_hi[axis] = C + CONDUIT_CORE + 2.5
    else:
        collar_lo[axis] = C - CONDUIT_CORE - 2.5
        collar_hi[axis] = C - CONDUIT_CORE - 1.4
    box(out, "luva", collar_lo[0], collar_lo[1], collar_lo[2], collar_hi[0], collar_hi[1],
        collar_hi[2], CONDUIT_DARK)
    return out


def write_conduit():
    write_block("conduit_core", build_conduit_core(), CONDUIT)
    parts = [{"apply": {"model": "ncat_minecraft:block/electrical/conduit_core"}}]
    for direction in ARMS:
        write_block(f"conduit_arm_{direction}", build_conduit_arm(direction), CONDUIT)
        parts.append({"when": {direction: "true"},
                      "apply": {"model": f"ncat_minecraft:block/electrical/conduit_arm_{direction}"}})
    write_json(os.path.join(ASSETS, "blockstates", "conduit.json"), {"multipart": parts})
    item = build_conduit_core() + build_conduit_arm("east") + build_conduit_arm("west")
    write_item("conduit", item, CONDUIT, span=1.05)
    write_loot("conduit")



def panel_layout():
    slots = [(breaker_x(0), breaker_x(0) + BREAKER_WIDTH * 2 + BREAKER_GAP)]
    slots += [(breaker_x(s), breaker_x(s) + BREAKER_WIDTH) for s in range(2, BREAKER_SLOTS)]
    levers = [((x0 + x1) / 2 - 0.5, RAIL_Y + 0.7, 12.2) for x0, x1 in slots]
    leds = [((x0 + x1) / 2 - 0.55, BREAKER_BOTTOM - 1.9, 14.0) for x0, x1 in slots[1:]]
    return slots, levers, leds




SKIN = 0.65
WELL = 0.75


def socket_row(out, x0, x1, z0, z1, top, centres, half, pin, body=PLATE_WHITE, edge=PLATE_GREY):
    floor = top - SKIN - WELL
    box(out, "corpo", x0, 0.0, z0, x1, floor, z1, body, body, edge)
    box(out, "fundo dos alojamentos", x0 + 0.5, floor, z0 + 0.5, x1 - 0.5, top - SKIN, z1 - 0.5,
        RECESS)
    front = C - half
    back = C + half
    box(out, "tampo", x0, top - SKIN, z0, x1, top, front, body, body, edge)
    box(out, "tampo", x0, top - SKIN, back, x1, top, z1, body, body, edge)
    cut = x0
    for cx in centres:
        if cx - half > cut + 0.01:
            box(out, "tampo", cut, top - SKIN, front, cx - half, top, back, body, body, edge)
        cut = cx + half
    if cut < x1 - 0.01:
        box(out, "tampo", cut, top - SKIN, front, x1, top, back, body, body, edge)
    for cx in centres:
        for dx, dz in ((-half * 0.46, half * 0.2), (half * 0.46, half * 0.2), (0.0, -half * 0.44)):
            box(out, "alveolo", cx + dx - pin, top - SKIN - 0.5, C + dz - pin, cx + dx + pin,
                top - SKIN + 0.02, C + dz + pin, HOLE)


def rocker(out, x0, x1, y0, y1, zf, lit):
    box(out, "interruptor", x0, y0, zf - 0.55, x1, y1, zf, BREAKER_DARK)
    box(out, "tecla", x0 + 0.25, y0 + 0.25, zf - 0.85, x1 - 0.25, y1 - 0.25, zf - 0.45,
        PLATE_RED if lit else BREAKER_DARK)


def radius_plug():
    return 2.3


def coil(out, cx, cy, cz, radius=2.3):
    for index in range(8):
        angle = math.tau * index / 8
        px = cx + math.cos(angle) * radius
        py = cy + math.sin(angle) * radius
        box(out, f"volta {index}", px - 0.42, py - 0.42, cz - 0.62, px + 0.42, py + 0.42,
            cz + 0.62, CORD)
    box(out, "amarracao", cx - 0.5, cy - radius - 1.0, cz - 0.75, cx + 0.5, cy - radius + 0.6,
        cz + 0.75, BREAKER_DARK)


def build_extension():
    out = []
    x0, x1, z0, z1 = 1.4, 14.6, 5.2, 10.8
    top = 3.4
    centres = [x0 + 2.4 + index * 2.9 for index in range(4)]
    socket_row(out, x0, x1, z0, z1, top, centres, 1.25, 0.28)
    box(out, "pe", x0 + 0.4, 0.0, z0 + 0.4, x1 - 0.4, 0.4, z1 - 0.4, PLATE_GREY)
    rocker(out, x1 - 2.2, x1 - 0.7, 0.9, 2.3, z0, False)
    box(out, "saida do cabo", x0 - 1.1, 0.8, C - 0.7, x0, 2.2, C + 0.7, CORD)
    on, off = [], []
    box(on, "led aceso", x0 + 0.6, 1.2, z0 - 0.12, x0 + 1.6, 2.2, z0 + 0.04, LED_GREEN_ON,
        fit={"north": (0, 0, 16, 16)}, glow=True)
    box(off, "led apagado", x0 + 0.6, 1.2, z0 - 0.12, x0 + 1.6, 2.2, z0 + 0.04, LED_GREEN_OFF,
        fit={"north": (0, 0, 16, 16)})
    return out, on, off, (x0 - 1.2, 0.0, z0 - 0.2, x1, top, z1), (x0 - 1.0, 1.5, C)


def build_power_strip():
    out = []
    x0, x1, z0, z1 = 0.8, 15.2, 4.6, 11.4
    top = 3.8
    centres = [x0 + 1.9 + index * 2.35 for index in range(6)]
    socket_row(out, x0, x1, z0, z1, top, centres, 1.05, 0.24)
    box(out, "pe", x0 + 0.4, 0.0, z0 + 0.4, x1 - 0.4, 0.4, z1 - 0.4, PLATE_GREY)
    box(out, "faixa", x0, top - SKIN - 0.45, z0 - 0.02, x1, top - SKIN - 0.05, z0 + 0.3, PLATE_BLUE)
    box(out, "botao", x1 - 3.3, 0.8, z0 - 0.7, x1 - 0.9, 2.9, z0 + 0.1, BREAKER_DARK)
    box(out, "disjuntor de rearme", x0 + 0.7, 1.1, z0 - 0.5, x0 + 2.0, 2.4, z0 + 0.1, PLATE_GREY)
    box(out, "saida do cabo", x0 - 1.1, 0.8, C - 0.7, x0, 2.2, C + 0.7, CORD)
    on, off = [], []
    box(on, "botao aceso", x1 - 3.0, 1.1, z0 - 1.0, x1 - 1.2, 2.6, z0 - 0.58, LED_RED_ON,
        fit={"north": (0, 0, 16, 16)}, glow=True)
    box(on, "led de protecao", x0 + 2.6, 1.3, z0 - 0.62, x0 + 3.6, 2.3, z0 - 0.46, LED_GREEN_ON,
        fit={"north": (0, 0, 16, 16)}, glow=True)
    box(off, "botao apagado", x1 - 3.0, 1.1, z0 - 1.0, x1 - 1.2, 2.6, z0 - 0.58, LED_RED_OFF,
        fit={"north": (0, 0, 16, 16)})
    box(off, "led de protecao", x0 + 2.6, 1.3, z0 - 0.62, x0 + 3.6, 2.3, z0 - 0.46, LED_GREEN_OFF,
        fit={"north": (0, 0, 16, 16)})
    return out, on, off, (x0 - 1.2, 0.0, z0 - 1.1, x1, top, z1), (x0 - 1.0, 1.5, C)


def build_ups():
    out = []
    x0, x1, z0, z1 = 2.4, 13.6, 3.6, 12.4
    top = 10.6
    centres = [x0 + 1.8 + index * 2.5 for index in range(4)]
    socket_row(out, x0, x1, z0, z1, top, centres, 1.15, 0.26, UPS_BODY, UPS_DARK)
    box(out, "pe", x0 + 0.6, 0.0, z0 + 0.6, x1 - 0.6, 0.5, z1 - 0.6, UPS_DARK)
    box(out, "frente", x0 + 0.5, 0.8, z0 - 0.35, x1 - 0.5, top - 1.6, z0 + 0.02, UPS_DARK)
    box(out, "visor", x0 + 1.2, 5.2, z0 - 0.52, x1 - 4.6, 8.4, z0 - 0.3, BREAKER_DARK)
    box(out, "botao", x1 - 3.8, 5.6, z0 - 0.72, x1 - 1.4, 7.8, z0 - 0.3, BREAKER_DARK)
    for side in ((x0 - 0.22, x0 + 0.02), (x1 - 0.02, x1 + 0.22)):
        box(out, "veneziana", side[0], 2.0, z0 + 1.4, side[1], top - 2.2, z1 - 1.4, VENT)
    box(out, "saida do cabo", x0 - 1.1, 1.4, C - 0.7, x0, 2.8, C + 0.7, CORD)
    box(out, "etiqueta", x0 + 1.2, 1.2, z0 - 0.5, x0 + 2.8, 2.8, z0 - 0.42, PLATE_GREY)
    on, off = [], []
    box(on, "botao aceso", x1 - 3.5, 5.9, z0 - 0.95, x1 - 1.7, 7.5, z0 - 0.68, LED_GREEN_ON,
        fit={"north": (0, 0, 16, 16)}, glow=True)
    box(off, "botao apagado", x1 - 3.5, 5.9, z0 - 0.95, x1 - 1.7, 7.5, z0 - 0.68, LED_GREEN_OFF,
        fit={"north": (0, 0, 16, 16)})
    for index in range(4):
        bx = x0 + 1.4 + index * 1.5
        box(on, f"barra {index}", bx, 3.2, z0 - 0.52, bx + 1.0, 4.2, z0 - 0.36, LED_GREEN_ON,
            fit={"north": (0, 0, 16, 16)}, glow=True)
        box(off, f"barra {index}", bx, 3.2, z0 - 0.52, bx + 1.0, 4.2, z0 - 0.36, LED_GREEN_OFF,
            fit={"north": (0, 0, 16, 16)})
    return out, on, off, (x0 - 1.2, 0.0, z0 - 1.0, x1 + 0.3, top, z1), (x0 - 1.0, 2.1, C)


APPLIANCES = {
    "extension_cord": build_extension,
    "power_strip": build_power_strip,
    "ups": build_ups,
}


def write_shapes_java(entries):
    lines = [
        "package com.netcattest.ncatminecraft.block;",
        "",
        "public final class ElectricalShapes {",
    ]
    slots, levers, leds = panel_layout()
    lines.append(f"    public static final double PANEL_BREAKER_BOTTOM = {BREAKER_BOTTOM:.3f};")
    lines.append(f"    public static final double PANEL_BREAKER_TOP = {BREAKER_TOP_Y:.3f};")
    lines.append(f"    public static final double PANEL_FRONT = {PANEL_FRONT:.3f};")
    for const, rows in (("PANEL_SLOTS", slots), ("PANEL_LEVERS", levers), ("PANEL_LEDS", leds)):
        lines.append(f"    public static final double[][] {const} = {{")
        for row in rows:
            lines.append("            {" + ", ".join(f"{v:.3f}" for v in row) + "},")
        lines.append("    };")
    lines.append("")
    for const, boxes in entries:
        if const.endswith("_PLUG"):
            row = boxes[0]
            lines.append("    public static final double[] " + const + " = {"
                         + ", ".join(f"{v:.3f}" for v in row[:3]) + "};")
            lines.append("")
            continue
        lines.append(f"    public static final double[][] {const} = {{")
        for values in boxes:
            lines.append("            {" + ", ".join(f"{v:.3f}" for v in values) + "},")
        lines.append("    };")
        lines.append("")
    lines += ["    private ElectricalShapes() {", "    }", "}", ""]
    with open(SHAPES_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


def build_wire_puller():
    out = []
    disc_z(out, "carretel", C, C, 5.2, 4.6, 9.0, PLATE_BLUE)
    disc_z(out, "aro do carretel", C, C, 5.6, 5.4, 8.2, PLATE_BLUE)
    disc_z(out, "nucleo", C, C, 2.2, 4.4, 9.2, BREAKER_DARK)
    disc_z(out, "fita enrolada", C, C, 4.3, 5.6, 8.0, RAIL)
    box(out, "guia", C - 1.1, C - 7.4, 5.6, C + 1.1, C - 4.8, 8.0, BREAKER_DARK)
    box(out, "fita", C - 0.45, C - 11.0, 6.3, C + 0.45, C - 7.2, 7.3, RAIL)
    box(out, "ponteira", C - 0.7, C - 12.2, 6.2, C + 0.7, C - 10.8, 7.4, STEEL_DARK)
    box(out, "manopla", C + 4.4, C - 1.4, 6.0, C + 7.4, C + 1.4, 7.6, BREAKER_DARK)
    return out


def main():
    make_textures()
    shapes = []

    for name in SOCKETS:
        body, on, off, hit = build_socket(name)
        write_block(name, body + off, PLATE_WHITE)
        write_block(name + "_on", body + on, PLATE_WHITE)
        variants = {}
        for facing, angle in FACINGS:
            for lit, suffix in (("false", ""), ("true", "_on")):
                entry = {"model": f"ncat_minecraft:block/electrical/{name}{suffix}"}
                if angle:
                    entry["y"] = angle
                variants[f"facing={facing},lit={lit}"] = entry
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": variants})
        write_item(name, body + on, PLATE_WHITE)
        write_loot(name)
        shapes.append((name.upper(), [hit]))
        print(f"{name}: {len(body)} elementos")

    for name, builder in APPLIANCES.items():
        body, on, off, hit, cord = builder()
        write_block(name, body + off, PLATE_WHITE)
        write_block(name + "_on", body + on, PLATE_WHITE)
        variants = {}
        for facing, angle in FACINGS:
            for lit, suffix in (("false", ""), ("true", "_on")):
                entry = {"model": f"ncat_minecraft:block/electrical/{name}{suffix}"}
                if angle:
                    entry["y"] = angle
                variants[f"facing={facing},lit={lit}"] = entry
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": variants})
        write_item(name, body + on, PLATE_WHITE)
        write_loot(name)
        shapes.append((name.upper(), [hit]))
        shapes.append((name.upper() + "_PLUG", [cord + cord]))
        print(f"{name}: {len(body)} elementos")

    shell, key_on, key_off, hit = build_wall_switch()
    write_block("wall_switch", shell + key_off, PLATE_WHITE)
    write_block("wall_switch_on", shell + key_on, PLATE_WHITE)
    variants = {}
    for facing, angle in FACINGS:
        for lit, suffix in (("false", ""), ("true", "_on")):
            entry = {"model": f"ncat_minecraft:block/electrical/wall_switch{suffix}"}
            if angle:
                entry["y"] = angle
            variants[f"facing={facing},lit={lit}"] = entry
    write_json(os.path.join(ASSETS, "blockstates", "wall_switch.json"), {"variants": variants})
    write_item("wall_switch", shell + key_on, PLATE_WHITE)
    write_loot("wall_switch")
    shapes.append(("WALL_SWITCH", [hit]))
    print(f"wall_switch: {len(shell) + len(key_on)} elementos")

    for name, spec in LAMPS.items():
        write_block(name, spec["build"](False), LAMP_RING)
        write_block(name + "_on", spec["build"](True), LAMP_RING)
        write_json(os.path.join(ASSETS, "blockstates", f"{name}.json"), {"variants": {
            "lit=false": {"model": f"ncat_minecraft:block/electrical/{name}"},
            "lit=true": {"model": f"ncat_minecraft:block/electrical/{name}_on"},
        }})
        write_item(name, spec["build"](True), LAMP_RING)
        write_loot(name)
        shapes.append((name.upper(), [spec["hit"]]))
        print(f"{name}: {len(spec['build'](True))} elementos")

    coil_model = []
    coil(coil_model, C, C, C)
    write_block("cord_coil", coil_model, CORD)
    plug = list(coil_model)
    box(plug, "plugue", C - 1.5, C - radius_plug(), C - 1.9, C + 1.5, C - radius_plug() + 2.6,
        C + 1.9, BREAKER_DARK)
    for dx in (-0.7, 0.7):
        box(plug, "pino", C + dx - 0.3, C - radius_plug() - 1.4, C - 0.3, C + dx + 0.3,
            C - radius_plug() + 0.1, C + 0.3, BUSBAR)
    write_item("power_cord", plug, CORD, span=1.15)

    panel = build_panel()
    write_block("distribution_panel", panel, STEEL)
    write_facing_blockstate("distribution_panel", "distribution_panel")
    write_block("panel_door", build_panel_door(), DOOR)
    write_block("panel_lever_on", build_lever(LEVER_ON), LEVER_ON)
    write_block("panel_lever_off", build_lever(LEVER_OFF), LEVER_OFF)
    for name, tex in (("panel_led_on", LED_GREEN_ON), ("panel_led_off", LED_GREEN_OFF),
                      ("panel_led_tripped", LED_RED_ON)):
        write_block(name, build_led(tex), LED_GREEN_OFF)
    write_item("distribution_panel", panel + build_panel_door(), STEEL)
    write_loot("distribution_panel")
    shapes.append(("DISTRIBUTION_PANEL", [(0.6, 0.2, PANEL_FRONT - 1.4, 15.4, 15.8, 16.0)]))
    print(f"distribution_panel: {len(panel)} elementos")

    write_item("wire_puller", build_wire_puller(), PLATE_BLUE)
    print("wire_puller: modelo de item")

    write_conduit()
    print("conduit: multipart com 6 bracos")

    write_shapes_java(shapes)
    if os.environ.get("ELECTRICAL_PREVIEW") == "1":
        save_preview()



def save_preview():
    import shutil
    import tempfile
    os.makedirs(PREVIEW, exist_ok=True)
    original = poles.TEX
    merged = tempfile.mkdtemp(prefix="ncat_electrical_tex_")
    for folder in (poles.TEX, TEX):
        for entry in os.listdir(folder):
            if entry.endswith(".png"):
                shutil.copyfile(os.path.join(folder, entry), os.path.join(merged, entry))
    poles.TEX = merged
    try:
        names = list(SOCKETS) + ["distribution_panel"]
        sheet = Image.new("RGB", (320 * len(names), 640), (16, 18, 22))
        for index, name in enumerate(names):
            model = os.path.join(MODELS, "block", "electrical",
                                 f"{name}{'_on' if name in SOCKETS else ''}.json")
            elements = poles.load_elements(model, 0.0)
            if name == "distribution_panel":
                elements += poles.load_elements(
                    os.path.join(MODELS, "block", "electrical", "panel_door.json"), 0.0)
            sheet.paste(poles.render(elements, -14, 10, 18.0, (320, 320), (C, C, 13.5)), (index * 320, 0))
            sheet.paste(poles.render(elements, -42, 26, 14.0, (320, 320), (C, C, 13.5)), (index * 320, 320))
        sheet.save(os.path.join(PREVIEW, "accessories.png"))
    finally:
        poles.TEX = original
        shutil.rmtree(merged, ignore_errors=True)
    print("previews em", PREVIEW)


if __name__ == "__main__":
    main()
