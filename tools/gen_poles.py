
import json
import math
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "ncat_minecraft")
DATA = os.path.join(RES, "data", "ncat_minecraft")
TEX = os.path.join(ASSETS, "textures", "block", "pole")
MODELS = os.path.join(ASSETS, "models")
SHAPES_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "netcattest", "ncatminecraft", "block",
                           "UtilityPoleShapes.java")
ANCHORS_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "netcattest", "ncatminecraft", "block",
                            "UtilityPoleAnchors.java")
PREVIEW = os.path.join(os.path.dirname(__file__), "_preview_poles")

C = 8.0
EPS = 0.01
TAN_22 = math.tan(math.radians(22.5))
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))

CONCRETE = "concrete"
CONCRETE_LIGHT = "concrete_light"
CONCRETE_DARK = "concrete_dark"
CONCRETE_GRIME = "concrete_grime"
ROUND = "concrete_round"
ROUND_GRIME = "concrete_round_grime"
COLLAR = "collar"
HOLE = "hole"
WOOD = "wood_pole"
WOOD_GROUND = "wood_pole_ground"
WOOD_END = "wood_end"
ARM = "wood_arm"
ARM_TOP = "wood_arm_top"
ARM_END = "wood_arm_end"
STEEL = "steel"
STEEL_DARK = "steel_dark"
PORCELAIN = "porcelain"
PORCELAIN_BROWN = "porcelain_brown"
POLYMER = "polymer"
COPPER = "copper"
PVC = "pvc"
ALUMINUM = "aluminum"
BRASS = "brass"
FUSE = "fuse_tube"
TANK = "tank"
TANK_DARK = "tank_dark"
TANK_FRONT = "tank_front"
LUMINAIRE = "luminaire"
LUMINAIRE_DARK = "luminaire_dark"
LED_OFF = "led_off"
LED_ON = "led_on"
PHOTOCELL = "photocell"
PHOTOCELL_TOP = "photocell_top"
PLATE = "plate"
DANGER = "danger"
NAMEPLATE = "nameplate"
GALV = "galvanized"
GALV_DARK = "galvanized_dark"
CABINET = "cabinet"
CABINET_DARK = "cabinet_dark"
CABINET_TOP = "cabinet_top"
GREY = "cabinet_grey"
GREY_DARK = "cabinet_grey_dark"
GREY_TOP = "cabinet_grey_top"
LOUVER = "louver"
PANEL = "panel"
INNER_PANEL = "inner_panel"
RAIL_STEEL = "rail_steel"
BREAKER_BODY = "breaker_body"
BREAKER_TOP_TEX = "breaker_top"
LEVER_RED = "lever_red"
LED_GREEN_ON = "led_green_on"
LED_GREEN_OFF = "led_green_off"
LED_RED_ON = "led_red_on"
LED_RED_OFF = "led_red_off"
SOLAR_GLASS = "solar_glass"
SOLAR_GLASS_EDGE = "solar_glass_edge"
SOLAR_BACK = "solar_back"
SOLAR_FRAME = "solar_frame"
JUNCTION = "junction_box"
TOWER = "tower_white"
TOWER_DARK = "tower_white_dark"
NACELLE = "nacelle"
NACELLE_DARK = "nacelle_dark"
BLADE = "blade"
BLADE_TIP = "blade_tip"
GENSET = "genset_paint"
GENSET_DARK = "genset_dark"
GRILLE = "radiator_grille"
EXHAUST = "exhaust_steel"
FUEL_TANK = "fuel_tank"
BUNKER = "bunker"
COAL_PILE = "coal_pile"



def noise(x, y, salt):
    n = (x * 374761393 + y * 668265263 + salt * 1440662683) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 255) / 255.0


def vnoise(x, y, cx, cy, salt):
    nx, ny = 16 // cx, 16 // cy
    gx, gy = x / cx, y / cy
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(i, j):
        return noise(i % nx, j % ny, salt)

    top = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * fx
    bottom = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * fx
    return top + (bottom - top) * fy


def clamp(value):
    return max(0, min(255, int(round(value))))


def tint(rgb, delta, warm=0.0):
    return clamp(rgb[0] + delta + warm), clamp(rgb[1] + delta), clamp(rgb[2] + delta - warm)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def texture(painter):
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b = painter(x, y)[:3]
            image.putpixel((x, y), (clamp(r), clamp(g), clamp(b), 255))
    return image


def concrete_paint(base, salt, rough=1.0, streak=1.0):
    def paint(x, y):
        d = (vnoise(x, y, 4, 4, salt) - 0.5) * 16 * rough
        d += (noise(x, y, salt + 1) - 0.5) * 9 * rough
        d -= streak * 22 * max(0.0, vnoise(x, y, 2, 16, salt + 2) - 0.55)
        p = noise(x, y, salt + 3)
        if p > 0.95:
            d -= 30 * rough
        elif p > 0.91:
            d -= 13 * rough
        elif p < 0.05:
            d += 13 * rough
        return tint(base, d, warm=3 * (vnoise(x, y, 8, 8, salt + 4) - 0.5))
    return paint


def grimy(paint, dirt, salt, start=4, strength=0.8):
    def wrapped(x, y):
        color = paint(x, y)
        t = max(0.0, (y - start) / (15.0 - start)) ** 1.35 * strength
        t *= 0.75 + 0.5 * vnoise(x, y, 4, 4, salt)
        if y > 9 and noise(x, y, salt + 7) > 0.86:
            t += 0.3
        return mix(color, dirt, min(1.0, t))
    return wrapped


def hole_paint(x, y):
    edge = x in (0, 15) or y in (0, 15)
    return tint((30, 30, 32) if not edge else (46, 46, 48), (noise(x, y, 91) - 0.5) * 6)


def wood_grain(base, salt, vertical=True, checks=True):
    cracks = [(int(noise(k, 0, salt + 5) * 16), int(noise(k, 1, salt + 5) * 16), 4 + int(noise(k, 2, salt + 5) * 7))
              for k in range(2)]

    def paint(x, y):
        u, v = (x, y) if vertical else (y, x)
        grain = 0.55 * noise(u, 0, salt) + 0.45 * vnoise(u, v, 2, 16, salt + 1)
        d = (grain - 0.5) * 30
        d += (vnoise(u, v, 4, 8, salt + 2) - 0.5) * 14
        d += (noise(u, v, salt + 3) - 0.5) * 6
        if checks:
            for col, start, length in cracks:
                if (v - start) % 16 < length:
                    if u == col:
                        d -= 44
                    elif u == (col + 1) % 16:
                        d -= 12
        green = 6 * (vnoise(u, v, 8, 8, salt + 6) - 0.35)
        return base[0] + d - green * 0.6, base[1] + d + green, base[2] + d * 0.8
    return paint


def wood_end_paint(x, y):
    dx, dy = x - 7.5, y - 7.5
    d = math.hypot(dx, dy)
    ring = 0.5 + 0.5 * math.cos(d * 2.4 + 1.3 * (noise(x, y, 41) - 0.5))
    color = mix((156, 128, 92), (104, 80, 56), ring * 0.75)
    if d > 6.4:
        color = mix(color, (72, 58, 44), 0.65)
    if abs(math.atan2(dy, dx) - 0.9) < 0.14 and d > 0.9:
        color = mix(color, (46, 34, 24), 0.85)
    return tint(color, (noise(x, y, 42) - 0.5) * 8)


def arm_end_paint(x, y):
    d = max(abs(x - 7.5) * 1.15, abs(y - 7.5))
    ring = 0.5 + 0.5 * math.cos(d * 2.1 + noise(x, y, 43))
    color = mix((150, 104, 70), (104, 68, 44), ring * 0.7)
    return tint(color, (noise(x, y, 44) - 0.5) * 8)


def steel_paint(base, salt):
    def paint(x, y):
        d = (vnoise(x, y, 2, 2, salt) - 0.5) * 18 + (noise(x, y, salt + 1) - 0.5) * 8
        if noise(x, y, salt + 2) > 0.93:
            d += 16
        return tint(base, d, warm=-1)
    return paint


def glaze_paint(base, salt, spread=6, gloss=14):
    def paint(x, y):
        d = (vnoise(x, y, 4, 4, salt) - 0.5) * spread + (noise(x, y, salt + 1) - 0.5) * spread * 0.5
        if noise(x, y, salt + 2) > 0.9:
            d += gloss
        return tint(base, d)
    return paint


def copper_paint(x, y):
    base = mix((180, 110, 66), (116, 70, 44), vnoise(x, y, 4, 4, 62) * 0.8)
    return tint(base, (noise(x, y, 61) - 0.5) * 18)


def fuse_paint(x, y):
    base = (68, 46, 36)
    if x in (4, 5):
        base = (96, 70, 56)
    return tint(base, (noise(x, y, 51) - 0.5) * 8)


def tank_paint(x, y):
    d = (vnoise(x, y, 4, 4, 40) - 0.5) * 5 + (noise(x, y, 39) - 0.5) * 3
    if noise(x, y, 38) > 0.96:
        d -= 12
    return tint((152, 158, 161), d)


def galvanized_paint(base, salt):
    def paint(x, y):
        d = (vnoise(x, y, 8, 8, salt) - 0.5) * 20 + (vnoise(x, y, 4, 4, salt + 1) - 0.5) * 12
        d += (noise(x, y, salt + 2) - 0.5) * 5
        if noise(x, y, salt + 3) > 0.94:
            d += 14
        return tint(base, d, warm=-2)
    return paint


def louver_paint(base, salt):
    def paint(x, y):
        d = (vnoise(x, y, 4, 4, salt) - 0.5) * 8
        phase = y % 4
        if phase == 0:
            d -= 46
        elif phase == 1:
            d -= 20
        elif phase == 3:
            d += 12
        return tint(base, d)
    return paint


def painted_metal(base, salt):
    def paint(x, y):
        delta = (vnoise(x, y, 8, 8, salt) - 0.5) * 8 + (noise(x, y, salt + 1) - 0.5) * 4
        return tint(base, delta)
    return paint


def rail_metal(x, y):
    base = (176, 180, 184)
    delta = (vnoise(x, y, 4, 4, 391) - 0.5) * 14 + (noise(x, y, 392) - 0.5) * 6
    if 6 <= y <= 9 and x % 8 in (2, 3, 4):
        return tint((48, 50, 54), delta * 0.4)
    if y in (0, 15):
        delta -= 26
    return tint(base, delta)


def breaker_front(x, y):
    base = (66, 68, 74)
    delta = (vnoise(x, y, 8, 8, 393) - 0.5) * 7 + (noise(x, y, 394) - 0.5) * 4
    if 2 <= x <= 13 and 2 <= y <= 5:
        base = (226, 226, 220)
        if 4 <= x <= 11 and y == 3 and noise(x, y, 395) > 0.25:
            base = (36, 36, 40)
    if y in (0, 15) or x in (0, 15):
        delta -= 16
    return tint(base, delta)


def panel_paint(x, y):
    d = (vnoise(x, y, 16, 2, 92) - 0.5) * 10 + (noise(x, y, 93) - 0.5) * 5
    return tint((36, 39, 43), d)


def status_led_paint(on, hue):
    bright = {"green": (74, 236, 118), "red": (246, 74, 58)}[hue]
    dim = {"green": (24, 54, 33), "red": (54, 24, 22)}[hue]
    def paint(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 6.6:
            return tint((26, 28, 31), (noise(x, y, 95) - 0.5) * 6)
        if d > 5.4:
            return tint((92, 96, 100), (noise(x, y, 94) - 0.5) * 10)
        if not on:
            return tint(mix(dim, (18, 20, 22), max(0.0, d - 3.0) / 3.0), (noise(x, y, 96) - 0.5) * 6)
        core = mix(bright, (255, 252, 236), max(0.0, 1.0 - d / 3.4) * 0.5)
        if d < 2.2 and x <= 7 and y <= 7:
            core = mix(core, (255, 255, 250), 0.45)
        return tint(core, (noise(x, y, 97) - 0.5) * 5)
    return paint


def solar_paint(x, y):
    cell = 4
    cx, cy = x % cell, y % cell
    base = (25, 36, 74)
    if cx == 0 or cy == 0:
        base = (14, 20, 44)
    elif cx == 2:
        base = (118, 128, 148)
    delta = (vnoise(x, y, 8, 8, 301) - 0.5) * 10 + (noise(x, y, 302) - 0.5) * 5
    return tint(base, delta)


def solar_edge_paint(x, y):
    if y < 4:
        return tint((176, 182, 190), (noise(x, y, 305) - 0.5) * 10)
    if y > 12:
        return tint((214, 216, 214), (noise(x, y, 306) - 0.5) * 8)
    return tint((38, 46, 76), (noise(x, y, 307) - 0.5) * 8)


def backsheet_paint(x, y):
    d = (vnoise(x, y, 8, 8, 311) - 0.5) * 8 + (noise(x, y, 312) - 0.5) * 4
    if y % 8 == 0:
        d -= 10
    return tint((226, 226, 222), d)


def composite_paint(base, salt, gloss=0.95):
    def paint(x, y):
        delta = (vnoise(x, y, 16, 16, salt) - 0.5) * 5 + (noise(x, y, salt + 1) - 0.5) * 3
        if noise(x, y, salt + 2) > gloss:
            delta += 6
        return tint(base, delta)
    return paint


def coal_paint(x, y):
    lump = vnoise(x, y, 4, 4, 371)
    base = mix((24, 23, 26), (58, 56, 62), lump)
    if noise(x, y, 372) > 0.88:
        base = mix(base, (108, 106, 112), 0.6)
    if noise(x, y, 373) < 0.06:
        base = (12, 11, 13)
    return tint(base, (noise(x, y, 374) - 0.5) * 10)


def grille_paint(x, y):
    if x % 3 == 0:
        return tint((72, 74, 78), (noise(x, y, 321) - 0.5) * 8)
    fin = (26, 28, 30) if y % 2 == 0 else (44, 46, 50)
    return tint(fin, (noise(x, y, 322) - 0.5) * 6)


def sooted(base, salt):
    def paint(x, y):
        soot = max(0.0, vnoise(x, y, 4, 4, salt) - 0.42) * 1.6
        colour = mix(base, (34, 32, 30), min(0.75, soot))
        return tint(colour, (noise(x, y, salt + 1) - 0.5) * 10)
    return paint


def led_paint(on):
    def paint(x, y):
        if on:
            color = (255, 255, 246) if x % 2 == 1 and y % 2 == 1 else (255, 240, 206)
            return tint(color, (noise(x, y, 71) - 0.5) * 4)
        color = (80, 86, 92) if x % 2 == 1 and y % 2 == 1 else (44, 48, 53)
        return tint(color, (noise(x, y, 72) - 0.5) * 6)
    return paint


DIGITS = {
    "0": ("###", "#.#", "#.#", "#.#", "###"),
    "1": (".#.", "##.", ".#.", ".#.", "###"),
    "2": ("###", "..#", "###", "#..", "###"),
    "4": ("#.#", "#.#", "###", "..#", "..#"),
    "5": ("###", "#..", "###", "..#", "###"),
    "7": ("###", "..#", "..#", ".#.", ".#."),
    "8": ("###", "#.#", "###", "#.#", "###"),
}


def draw_digits(image, text, left, top, color):
    for index, char in enumerate(text):
        for row, line in enumerate(DIGITS[char]):
            for col, cell in enumerate(line):
                if cell == "#":
                    image.putpixel((left + index * 4 + col, top + row), color + (255,))


def tank_front_image():
    image = texture(tank_paint)
    draw_digits(image, "75", 5, 8, (28, 28, 30))
    return image


def plate_image():
    image = texture(glaze_paint((198, 202, 204), 81, spread=5, gloss=6))
    for v in range(16):
        for u in range(3, 13):
            if u in (3, 12) or v in (0, 15):
                image.putpixel((u, v), (112, 118, 122, 255))
            elif 1 <= v <= 3:
                image.putpixel((u, v), (34, 76, 146, 255) if v != 2 or u not in (5, 6, 7) else (214, 224, 236, 255))
    draw_digits(image, "48", 4, 5, (30, 32, 34))
    draw_digits(image, "27", 4, 10, (30, 32, 34))
    return image


DANGER_ART = (
    "################",
    "#YYYYYYYYYYYYYY#",
    "#YYYYYYYYY##YYY#",
    "#YYYYYYYY##YYYY#",
    "#YYYYYYY##YYYYY#",
    "#YYYYYY##YYYYYY#",
    "#YYYYY######YYY#",
    "#YYYYYYYY##YYYY#",
    "#YYYYYYY##YYYYY#",
    "#YYYYYY##YYYYYY#",
    "#YYYYY##YYYYYYY#",
    "#YYYY###YYYYYYY#",
    "#YYYY##YYYYYYYY#",
    "#YYYY#YYYYYYYYY#",
    "#YYYYYYYYYYYYYY#",
    "################",
)


def danger_image():
    image = Image.new("RGBA", (16, 16))
    for y, line in enumerate(DANGER_ART):
        for x, cell in enumerate(line):
            color = (24, 22, 20) if cell == "#" else (238, 192, 22)
            image.putpixel((x, y), tint(color, (noise(x, y, 83) - 0.5) * 8) + (255,))
    return image


def nameplate_image():
    image = texture(glaze_paint((190, 194, 196), 85, spread=4, gloss=4))
    for v in range(16):
        for u in range(16):
            if u in (0, 15) or v in (0, 15):
                image.putpixel((u, v), (104, 110, 114, 255))
            elif v in (3, 6, 9, 12) and 2 <= u <= 13 and noise(u, v, 86) > 0.25:
                image.putpixel((u, v), (46, 48, 52, 255))
    return image


def make_textures():
    os.makedirs(TEX, exist_ok=True)
    dirt = (98, 90, 78)
    dt = concrete_paint((152, 153, 148), 11)
    smooth = concrete_paint((166, 166, 161), 15, rough=0.55, streak=0.7)
    wood = wood_grain((118, 98, 74), 21)
    painters = {
        CONCRETE: dt,
        CONCRETE_LIGHT: concrete_paint((172, 172, 167), 12, rough=0.7, streak=0.0),
        CONCRETE_DARK: concrete_paint((120, 121, 117), 13, rough=0.8, streak=0.4),
        CONCRETE_GRIME: grimy(dt, dirt, 14),
        ROUND: smooth,
        ROUND_GRIME: grimy(smooth, dirt, 16),
        COLLAR: grimy(concrete_paint((134, 132, 126), 17, rough=1.5, streak=0.0), (92, 86, 76), 18, 0, 0.5),
        HOLE: hole_paint,
        WOOD: wood,
        WOOD_GROUND: grimy(wood, (54, 43, 33), 22, 3, 0.9),
        WOOD_END: wood_end_paint,
        ARM: wood_grain((130, 88, 58), 23, vertical=False, checks=False),
        ARM_TOP: wood_grain((130, 88, 58), 23, vertical=True, checks=False),
        ARM_END: arm_end_paint,
        STEEL: steel_paint((150, 155, 158), 31),
        STEEL_DARK: steel_paint((88, 92, 96), 32),
        PORCELAIN: glaze_paint((226, 229, 226), 33, spread=5, gloss=10),
        PORCELAIN_BROWN: glaze_paint((112, 57, 34), 34, spread=8, gloss=28),
        POLYMER: glaze_paint((122, 128, 132), 35, spread=6, gloss=6),
        COPPER: copper_paint,
        PVC: glaze_paint((172, 174, 172), 36, spread=4, gloss=4),
        ALUMINUM: steel_paint((180, 184, 186), 37),
        BRASS: steel_paint((170, 138, 78), 38),
        FUSE: fuse_paint,
        TANK: tank_paint,
        TANK_DARK: glaze_paint((110, 116, 120), 41, spread=5, gloss=3),
        LUMINAIRE: glaze_paint((104, 110, 116), 42, spread=6, gloss=6),
        LUMINAIRE_DARK: glaze_paint((70, 75, 81), 43, spread=5, gloss=4),
        LED_OFF: led_paint(False),
        LED_ON: led_paint(True),
        PHOTOCELL: glaze_paint((58, 68, 80), 44, spread=6, gloss=10),
        PHOTOCELL_TOP: glaze_paint((122, 152, 172), 45, spread=8, gloss=30),
        GALV: galvanized_paint((158, 164, 169), 51),
        GALV_DARK: galvanized_paint((112, 118, 124), 53),
        CABINET: glaze_paint((62, 96, 72), 55, spread=6, gloss=5),
        CABINET_DARK: glaze_paint((44, 70, 53), 56, spread=5, gloss=4),
        CABINET_TOP: glaze_paint((74, 110, 84), 57, spread=6, gloss=8),
        GREY: glaze_paint((132, 136, 138), 58, spread=6, gloss=5),
        GREY_DARK: glaze_paint((98, 102, 105), 59, spread=5, gloss=4),
        GREY_TOP: glaze_paint((146, 150, 152), 60, spread=6, gloss=8),
        LOUVER: louver_paint((104, 110, 112), 61),
        PANEL: panel_paint,
        INNER_PANEL: painted_metal((228, 228, 222), 381),
        RAIL_STEEL: rail_metal,
        BREAKER_BODY: breaker_front,
        BREAKER_TOP_TEX: glaze_paint((78, 80, 86), 385, spread=5, gloss=4),
        LEVER_RED: glaze_paint((198, 62, 48), 387, spread=6, gloss=5),
        LED_GREEN_ON: status_led_paint(True, "green"),
        LED_GREEN_OFF: status_led_paint(False, "green"),
        LED_RED_ON: status_led_paint(True, "red"),
        LED_RED_OFF: status_led_paint(False, "red"),
        SOLAR_GLASS: solar_paint,
        SOLAR_GLASS_EDGE: solar_edge_paint,
        SOLAR_BACK: backsheet_paint,
        SOLAR_FRAME: steel_paint((196, 200, 204), 315),
        JUNCTION: glaze_paint((38, 40, 44), 317, spread=5, gloss=4),
        TOWER: composite_paint((228, 230, 232), 331),
        TOWER_DARK: composite_paint((188, 192, 196), 333),
        NACELLE: composite_paint((220, 223, 226), 335),
        NACELLE_DARK: composite_paint((158, 162, 168), 337),
        BLADE: composite_paint((236, 238, 240), 341),
        BLADE_TIP: composite_paint((196, 62, 48), 343),
        GENSET: glaze_paint((208, 170, 52), 351, spread=6, gloss=6),
        GENSET_DARK: glaze_paint((158, 126, 36), 353, spread=5, gloss=4),
        GRILLE: grille_paint,
        EXHAUST: sooted((132, 130, 128), 361),
        FUEL_TANK: glaze_paint((62, 64, 70), 363, spread=5, gloss=4),
        BUNKER: sooted((96, 94, 92), 367),
        COAL_PILE: coal_paint,
    }
    for name, painter in painters.items():
        texture(painter).save(os.path.join(TEX, f"{name}.png"))
    tank_front_image().save(os.path.join(TEX, f"{TANK_FRONT}.png"))
    plate_image().save(os.path.join(TEX, f"{PLATE}.png"))
    danger_image().save(os.path.join(TEX, f"{DANGER}.png"))
    nameplate_image().save(os.path.join(TEX, f"{NAMEPLATE}.png"))



def box(out, name, x0, y0, z0, x1, y1, z1, tex, up=None, down=None, sides=None, rot=None, fit=None, glow=False):
    if x1 - x0 < 0.02 or y1 - y0 < 0.02 or z1 - z0 < 0.02:
        return
    faces = {"north": tex, "south": tex, "west": tex, "east": tex, "up": up or tex, "down": down or tex}
    if sides:
        faces.update(sides)
    out.append({"name": name, "from": (x0, y0, z0), "to": (x1, y1, z1), "faces": faces,
                "rot": rot, "fit": dict(fit or {}), "glow": glow})


def rotation(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": tuple(origin)}


def disc(out, name, cx, cz, r, y0, y1, tex, up=None, down=None, rot=None, cap_top=True,
         cap_bottom=True, glow=False):
    if r < 0.3:
        box(out, name, cx - r, y0, cz - r, cx + r, y1, cz + r, tex, up, down, rot=rot, glow=glow)
        return
    big, mid, diag = r * 0.96, r * 0.40, r * 0.735
    top = EPS if cap_top else 0.0
    bottom = EPS if cap_bottom else 0.0
    box(out, name, cx - big, y0, cz - mid, cx + big, y1, cz + mid, tex, up, down, rot=rot, glow=glow)
    box(out, name + " b", cx - mid, y0 + bottom, cz - big, cx + mid, y1 - top, cz + big, tex, up, down,
        rot=rot, glow=glow)
    box(out, name + " c", cx - diag, y0 + 2 * bottom, cz - diag, cx + diag, y1 - 2 * top, cz + diag,
        tex, up, down, rot=rot, glow=glow)


def disc_z(out, name, cx, cy, r, z0, z1, tex, ends=None, rot=None):
    sides = {"north": ends or tex, "south": ends or tex}
    if r < 0.3:
        box(out, name, cx - r, cy - r, z0, cx + r, cy + r, z1, tex, sides=sides, rot=rot)
        return
    big, mid, diag = r * 0.96, r * 0.40, r * 0.735
    box(out, name, cx - big, cy - mid, z0, cx + big, cy + mid, z1, tex, sides=sides, rot=rot)
    box(out, name + " b", cx - mid, cy - big, z0 + EPS, cx + mid, cy + big, z1 - EPS, tex, sides=sides, rot=rot)
    box(out, name + " c", cx - diag, cy - diag, z0 + 2 * EPS, cx + diag, cy + diag, z1 - 2 * EPS,
        tex, sides=sides, rot=rot)


def disc_reach(r, offset):
    offset = abs(offset)
    if r < 0.3:
        return r if offset <= r else 0.0
    if offset <= r * 0.40:
        return r * 0.96
    if offset <= r * 0.735:
        return r * 0.735
    if offset <= r * 0.96:
        return r * 0.40
    return 0.0


def step_mid(y, top, step=8.0):
    return min(math.floor(y / step) * step + step / 2, top - step / 2)


def rotate_point(point, rot):
    if not rot:
        return tuple(point)
    ox, oy, oz = rot["origin"]
    angle = math.radians(rot["angle"])
    c, s = math.cos(angle), math.sin(angle)
    x, y, z = point[0] - ox, point[1] - oy, point[2] - oz
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return x + ox, y + oy, z + oz


class Pole:
    def __init__(self, key, short, const, sections, particle, icon):
        self.key = key
        self.short = short
        self.const = const
        self.sections = sections
        self.top = sections * 16.0
        self.particle = particle
        self.icon = icon
        self.base = []
        self.overlays = []
        self.icon_extra = []
        self.hits = []
        self.lamp_section = None
        self.hv = []
        self.mv = []
        self.lv = []
        self.extras = []
        self.bunker = None
        self.door = None
        self.display = None
        self.lever = None

    def hit(self, x0, y0, z0, x1, y1, z1):
        self.hits.append((min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1)))

    def line(self, x, y, z, shield=False):
        self.hv.append((x, y, z, 1 if shield else 0))

    def phase(self, x, y, z):
        self.mv.append((x, y, z, 0))

    def secondary(self, x, y, z, neutral=False):
        self.lv.append((x, y, z, 1 if neutral else 0))



def dt_shaft(out, top, b, a_at, f_at, web, grime_top=16.0, solid_bottom=6.0, cap=4.0):
    cuts = sorted({0.0, solid_bottom, top - cap, top} | {float(v) for v in range(8, int(top), 8)})
    x0, x1 = C - b / 2, C + b / 2
    for y0, y1 in zip(cuts, cuts[1:]):
        mid = (y0 + y1) / 2
        a, f = a_at(mid), f_at(mid)
        z0, z1 = C - a / 2, C + a / 2
        side = CONCRETE_GRIME if y1 <= grime_top else CONCRETE
        if y1 <= solid_bottom or y0 >= top - cap:
            box(out, "fuste macico", x0, y0, z0, x1, y1, z1, side, CONCRETE_LIGHT, side)
            continue
        box(out, "aba frontal", x0, y0, z0, x1, y1, z0 + f, side, CONCRETE_LIGHT, side)
        box(out, "aba traseira", x0, y0, z1 - f, x1, y1, z1, side, CONCRETE_LIGHT, side)
        box(out, "alma", C - web / 2, y0, z0 + f - EPS, C + web / 2, y1, z1 - f + EPS, CONCRETE_DARK)


def dt_holes(out, ys, web):
    for y in ys:
        box(out, "furo", C - web / 2 - 0.05, y - 0.24, C - 0.24, C + web / 2 + 0.05, y + 0.24, C + 0.24, HOLE)


def round_shaft(out, top, r_at, center_at, side, grime, top_tex, grime_top=16.0):
    y0 = 0.0
    while y0 < top - 1e-6:
        y1 = min(top, y0 + 8.0)
        mid = (y0 + y1) / 2
        cx, cz = center_at(mid)
        last = y1 >= top - 1e-6
        tex = grime if y1 <= grime_top else side
        disc(out, "fuste", cx, cz, r_at(mid), y0, y1, tex, top_tex if last else tex, tex,
             cap_top=last, cap_bottom=False)
        y0 = y1


def round_front(r_at, center_at, x, top):
    def front(y):
        mid = step_mid(y, top)
        cx, cz = center_at(mid)
        return cz - disc_reach(r_at(mid), x - cx)
    return front


def collar_rect(out, x0, x1, z0, z1):
    box(out, "base concretada", x0 - 1.7, 0, z0 - 1.7, x1 + 1.7, 0.45, z1 + 1.7, COLLAR)
    box(out, "base concretada topo", x0 - 1.0, 0.45, z0 - 1.0, x1 + 1.0, 0.95, z1 + 1.0, COLLAR)


def collar_round(out, cx, cz, r):
    disc(out, "base concretada", cx, cz, r + 1.7, 0, 0.45, COLLAR)
    disc(out, "base concretada topo", cx, cz, r + 1.0, 0.45, 0.95, COLLAR)


def crossarm(out, x0, x1, y0, y1, z0, z1, kind, axis="z"):
    if kind == "steel":
        box(out, "cruzeta", x0, y0, z0, x1, y1, z1, GALV, GALV, GALV_DARK)
        if axis == "z":
            box(out, "nervura da cruzeta", x0 + 0.3, y0 - 0.26, z0 + 0.5, x1 - 0.3, y0 + 0.02, z1 - 0.5,
                GALV_DARK)
        else:
            box(out, "nervura da cruzeta", x0 + 0.5, y0 - 0.26, z0 + 0.3, x1 - 0.5, y0 + 0.02, z1 - 0.3,
                GALV_DARK)
        return
    if kind == "wood":
        if axis == "z":
            sides = {"up": ARM_TOP, "down": ARM_TOP, "north": ARM_END, "south": ARM_END}
        else:
            sides = {"west": ARM_END, "east": ARM_END}
        box(out, "cruzeta", x0, y0, z0, x1, y1, z1, ARM, sides=sides)
        return
    box(out, "cruzeta", x0, y0, z0, x1, y1, z1, CONCRETE_LIGHT, CONCRETE_LIGHT, CONCRETE)
    if axis == "z":
        box(out, "nervura da cruzeta", x0 + 0.3, y0 - 0.28, z0 + 0.5, x1 - 0.3, y0 + 0.02, z1 - 0.5,
            CONCRETE, CONCRETE, CONCRETE_DARK)
    else:
        box(out, "nervura da cruzeta", x0 + 0.5, y0 - 0.28, z0 + 0.3, x1 - 0.5, y0 + 0.02, z1 - 0.3,
            CONCRETE, CONCRETE, CONCRETE_DARK)


def through_bolt_x(out, y, z, face_a, face_b):
    box(out, "parafuso", face_a - 0.5, y - 0.16, z - 0.16, face_b + 0.5, y + 0.16, z + 0.16, STEEL_DARK)
    box(out, "arruela", face_a - 0.14, y - 0.55, z - 0.55, face_a, y + 0.55, z + 0.55, STEEL)
    box(out, "porca", face_a - 0.44, y - 0.33, z - 0.33, face_a - 0.14, y + 0.33, z + 0.33, STEEL_DARK)
    box(out, "arruela", face_b, y - 0.55, z - 0.55, face_b + 0.14, y + 0.55, z + 0.55, STEEL)
    box(out, "porca", face_b + 0.14, y - 0.33, z - 0.33, face_b + 0.44, y + 0.33, z + 0.33, STEEL_DARK)


def through_bolt_z(out, x, y, face_a, face_b):
    box(out, "parafuso", x - 0.16, y - 0.16, face_a - 0.2, x + 0.16, y + 0.16, face_b + 0.5, STEEL_DARK)
    box(out, "arruela", x - 0.5, y - 0.5, face_b, x + 0.5, y + 0.5, face_b + 0.14, STEEL)
    box(out, "porca", x - 0.32, y - 0.32, face_b + 0.14, x + 0.32, y + 0.32, face_b + 0.44, STEEL_DARK)


def brace_yz(out, x0, x1, y_low, z_low, y_high, z_high, tex=STEEL, width=0.6):
    length = math.hypot(y_high - y_low, z_high - z_low) + 0.4
    my, mz = (y_low + y_high) / 2, (z_low + z_high) / 2
    angle = 45 if z_high > z_low else -45
    box(out, "mao francesa", x0, my - length / 2, mz - width / 2, x1, my + length / 2, mz + width / 2, tex,
        rot=rotation("x", angle, (x0, my, mz)))


def bar_xy(out, name, z0, z1, x_low, y_low, x_high, y_high, tex, width=0.6, extra=0.4):
    length = math.hypot(y_high - y_low, x_high - x_low) + extra
    my, mx = (y_low + y_high) / 2, (x_low + x_high) / 2
    angle = -45 if x_high > x_low else 45
    box(out, name, mx - width / 2, my - length / 2, z0, mx + width / 2, my + length / 2, z1, tex,
        rot=rotation("z", angle, (mx, my, z0)))


def pin_insulator(out, x, z, yb, body):
    box(out, "arruela do pino", x - 0.55, yb, z - 0.55, x + 0.55, yb + 0.18, z + 0.55, STEEL_DARK)
    disc(out, "pino", x, z, 0.32, yb + 0.18, yb + 0.95, STEEL)
    disc(out, "pescoco", x, z, 0.6, yb + 0.9, yb + 1.3, body)
    disc(out, "saia inferior", x, z, 1.08, yb + 1.3, yb + 1.66, body)
    disc(out, "corpo", x, z, 0.74, yb + 1.66, yb + 2.14, body)
    disc(out, "saia superior", x, z, 0.98, yb + 2.14, yb + 2.46, body)
    disc(out, "cabeca", x, z, 0.72, yb + 2.46, yb + 2.78, body)
    disc(out, "gola do condutor", x, z, 0.5, yb + 2.78, yb + 3.1, body)
    disc(out, "coroa", x, z, 0.7, yb + 3.1, yb + 3.46, body)
    disc(out, "topo do isolador", x, z, 0.46, yb + 3.46, yb + 3.66, body)


INSULATOR_HEIGHT = 3.66
INSULATOR_GROOVE = 2.94
SPOOL_GROOVE = 0.60


def mv_crossarm(pole, x_face, x_back, arm_top, z_center, kind, insulator, insulator_z):
    out = pole.base
    width, height = (1.5, 1.9) if kind == "concrete" else (1.45, 1.8)
    x0, x1 = x_face, x_face + width
    y0 = arm_top - height
    crossarm(out, x0, x1, y0, arm_top, -8.0, 24.0, kind)
    through_bolt_x(out, (y0 + arm_top) / 2, z_center, x_back, x1)
    y_low = y0 - 10.0
    for sign in (-1, 1):
        brace_yz(out, x_face + 0.02, x_face + 0.3, y_low, z_center, y0, z_center + sign * 10.0)
    through_bolt_x(out, y_low, z_center, x_back, x_face + 0.3)
    xc = (x0 + x1) / 2
    for z in insulator_z:
        pin_insulator(out, xc, z, arm_top, insulator)
        pole.phase(xc, arm_top + INSULATOR_GROOVE, z)
        pole.hit(xc - 1.05, arm_top, z - 1.05, xc + 1.05, arm_top + INSULATOR_HEIGHT, z + 1.05)
    pole.hit(x0, y0 - 0.3, -8.0, x1, arm_top, 24.0)
    return y0, y_low


def spool(out, x, z, yb, tex=PORCELAIN):
    disc(out, "roldana base", x, z, 0.62, yb, yb + 0.36, tex)
    disc(out, "roldana canal", x, z, 0.46, yb + 0.36, yb + 0.84, tex)
    disc(out, "roldana topo", x, z, 0.62, yb + 0.84, yb + 1.2, tex)


def secondary_rack(pole, x, zf, y_top, count, spacing=3.4, back=None):
    out = pole.base
    y_bottom = y_top - (count - 1) * spacing - 1.2
    box(out, "armacao secundaria", x - 0.55, y_bottom - 0.6, zf - 0.34, x + 0.55, y_top + 0.6, zf + 0.04, STEEL)
    for i in range(count):
        yb = y_top - i * spacing - 1.2
        box(out, "estribo inferior", x - 0.78, yb - 0.2, zf - 2.45, x + 0.78, yb, zf - 0.3, STEEL)
        box(out, "estribo superior", x - 0.78, yb + 1.2, zf - 2.45, x + 0.78, yb + 1.4, zf - 0.3, STEEL)
        spool(out, x, zf - 1.5, yb)
        pole.secondary(x, yb + SPOOL_GROOVE, zf - 1.5, neutral=count > 1 and i == 0)
    for y in (y_top + 0.25, y_bottom - 0.25):
        box(out, "cabeca do parafuso", x - 0.3, y - 0.3, zf - 0.56, x + 0.3, y + 0.3, zf - 0.3, STEEL_DARK)
        if back is not None:
            through_bolt_z(out, x, y, zf - 0.3, back)
    pole.hit(x - 0.8, y_bottom - 0.6, zf - 2.45, x + 0.8, y_top + 0.6, zf)
    return y_bottom


def ground_wire(out, x, front, y0, y1, clamp_every=16.0):
    y = y0
    while y < y1 - 1e-6:
        ya, yb = y, min(y1, (math.floor(y / 8.0 + 1e-9) + 1) * 8.0)
        zf = front((ya + yb) / 2)
        box(out, "fio terra", x - 0.12, ya, zf - 0.3, x + 0.12, yb, zf - 0.04, COPPER)
        y = yb
    k = math.ceil(y0 / clamp_every)
    while k * clamp_every + 4.0 < y1 - 1.0:
        yc = k * clamp_every + 4.0
        if yc > y0 + 1.0:
            zf = front(yc)
            box(out, "grampo", x - 0.32, yc - 0.16, zf - 0.38, x + 0.32, yc + 0.16, zf + 0.02, STEEL)
        k += 1


def conduit(out, x, front, top, r=0.42):
    y0 = 0.0
    while y0 < top - 1e-6:
        y1 = min(top, y0 + 8.0)
        zf = front((y0 + y1) / 2)
        disc(out, "eletroduto", x, zf - r - 0.02, r, y0, y1, PVC, cap_top=y1 >= top - 1e-6, cap_bottom=False)
        y0 = y1
    zf = front(top - 0.1)
    disc(out, "bucha do eletroduto", x, zf - r - 0.02, r + 0.1, top - 0.35, top + 0.3, PVC)
    for y in range(6, int(top) - 3, 12):
        zf = front(y + 0.2)
        disc(out, "abracadeira", x, zf - r - 0.02, r + 0.08, y, y + 0.35, STEEL)


def id_plate(out, x0, x1, y0, y1, zf):
    box(out, "plaqueta de identificacao", x0, y0, zf - 0.12, x1, y1, zf + 0.02, ALUMINUM,
        sides={"north": PLATE}, fit={"north": (3, 0, 13, 16)})


def luminaire(out, yc, rear):
    box(out, "carcaca traseira", 6.35, yc - 1.05, rear - 3.5, 9.65, yc + 1.15, rear, LUMINAIRE, down=LUMINAIRE_DARK)
    box(out, "tampa traseira", 6.6, yc - 0.85, rear, 9.4, yc + 0.95, rear + 0.25, LUMINAIRE)
    box(out, "junta da tampa", 6.33, yc + 0.2, rear - 3.52, 9.67, yc + 0.3, rear + 0.02, LUMINAIRE_DARK)
    box(out, "transicao", 6.1, yc - 0.85, rear - 4.7, 9.9, yc + 0.85, rear - 3.5, LUMINAIRE, down=LUMINAIRE_DARK)
    box(out, "corpo optico", 5.85, yc - 0.8, rear - 10.2, 10.15, yc + 0.6, rear - 4.7, LUMINAIRE, down=LUMINAIRE_DARK)
    box(out, "bico", 6.4, yc - 0.7, rear - 10.65, 9.6, yc + 0.5, rear - 10.2, LUMINAIRE, down=LUMINAIRE_DARK)
    for i in range(6):
        x = 6.45 + i * 0.62
        box(out, "aleta do dissipador", x - 0.1, yc + 0.58, rear - 9.8, x + 0.1, yc + 0.95, rear - 5.0, LUMINAIRE)
    pz = rear - 1.8
    disc(out, "soquete do rele", C, pz, 0.95, yc + 1.15, yc + 1.45, LUMINAIRE_DARK)
    disc(out, "rele fotoeletrico", C, pz, 0.82, yc + 1.45, yc + 2.1, PHOTOCELL)
    disc(out, "janela do rele", C, pz, 0.84, yc + 2.1, yc + 2.35, PHOTOCELL_TOP)
    disc(out, "tampa do rele", C, pz, 0.82, yc + 2.35, yc + 2.6, PHOTOCELL)
    disc(out, "topo do rele", C, pz, 0.62, yc + 2.6, yc + 2.85, PHOTOCELL, up=PHOTOCELL_TOP)
    return 6.3, yc - 0.92, rear - 9.9, 9.7, yc - 0.78, rear - 5.0


def fuse_cutout(out, cx, yc, zc, bracket_to):
    rot = rotation("x", 22.5, (cx, yc, zc))
    box(out, "suporte da chave", cx - 0.35, yc - 0.35, zc + 0.2, cx + 0.35, yc + 0.35, bracket_to + 0.05, STEEL)
    disc(out, "isolador da chave", cx, zc, 0.45, yc - 3.4, yc + 3.4, POLYMER, rot=rot)
    for i, dy in enumerate((-2.8, -1.9, -1.0, 1.0, 1.9, 2.8)):
        disc(out, f"aleta {i}", cx, zc, 0.88, yc + dy - 0.11, yc + dy + 0.11, POLYMER, rot=rot)
    box(out, "abracadeira da chave", cx - 0.62, yc - 0.42, zc - 0.62, cx + 0.62, yc + 0.42, zc + 0.62, STEEL, rot=rot)
    box(out, "contato superior", cx - 0.72, yc + 3.4, zc - 2.15, cx + 0.72, yc + 4.25, zc + 0.5, BRASS, rot=rot)
    box(out, "terminal superior", cx - 0.26, yc + 3.6, zc + 0.5, cx + 0.26, yc + 4.05, zc + 1.15, STEEL, rot=rot)
    box(out, "articulacao inferior", cx - 0.72, yc - 4.2, zc - 2.15, cx + 0.72, yc - 3.4, zc + 0.5, BRASS, rot=rot)
    box(out, "terminal inferior", cx - 0.26, yc - 4.0, zc + 0.5, cx + 0.26, yc - 3.55, zc + 1.15, STEEL, rot=rot)
    tz = zc - 1.55
    disc(out, "cartucho", cx, tz, 0.4, yc - 3.05, yc + 3.05, FUSE, rot=rot)
    disc(out, "tampa do cartucho", cx, tz, 0.5, yc + 3.05, yc + 3.5, BRASS, rot=rot)
    disc(out, "ferrolho", cx, tz, 0.48, yc - 3.45, yc - 3.05, BRASS, rot=rot)
    box(out, "olhal de manobra", cx - 0.32, yc + 3.1, tz - 0.75, cx + 0.32, yc + 3.45, tz - 0.45, STEEL, rot=rot)
    return rotate_point((cx, yc - 3.78, zc + 1.1), rot)


def hv_bushing(out, x, z, y0):
    disc(out, "base da bucha", x, z, 0.85, y0, y0 + 0.3, TANK)
    disc(out, "bucha de alta", x, z, 0.5, y0 + 0.3, y0 + 4.5, PORCELAIN_BROWN)
    for i, dy in enumerate((1.1, 2.2, 3.3)):
        disc(out, f"saia da bucha {i}", x, z, 0.95, y0 + dy, y0 + dy + 0.26, PORCELAIN_BROWN)
    disc(out, "capa da bucha", x, z, 0.42, y0 + 4.5, y0 + 4.9, BRASS)
    box(out, "conector da bucha", x - 0.35, y0 + 4.9, z - 0.35, x + 0.35, y0 + 5.8, z + 0.35, BRASS)
    return y0 + 5.8


def surge_arrester(out, x, z, y0):
    box(out, "base do para-raios", x - 0.6, y0, z - 0.6, x + 0.6, y0 + 0.25, z + 0.6, STEEL)
    disc(out, "desligador", x, z, 0.35, y0 + 0.25, y0 + 0.8, STEEL_DARK)
    disc(out, "para-raios", x, z, 0.45, y0 + 0.8, y0 + 4.3, POLYMER)
    for i, dy in enumerate((1.3, 2.1, 2.9, 3.7)):
        disc(out, f"saia do para-raios {i}", x, z, 0.85, y0 + dy, y0 + dy + 0.2, POLYMER)
    disc(out, "capa do para-raios", x, z, 0.38, y0 + 4.3, y0 + 4.7, STEEL)
    return y0 + 4.7


def lv_bushing(out, x, y, z_face):
    disc_z(out, "flange de baixa", x, y, 0.62, z_face - 0.25, z_face + 0.02, TANK)
    disc_z(out, "bucha de baixa", x, y, 0.42, z_face - 1.5, z_face - 0.25, PORCELAIN_BROWN)
    disc_z(out, "saia de baixa", x, y, 0.7, z_face - 0.95, z_face - 0.75, PORCELAIN_BROWN)
    box(out, "terminal de baixa", x - 0.3, y - 0.12, z_face - 2.5, x + 0.3, y + 0.12, z_face - 1.5, BRASS)


def steel_crossarm(out, x0, x1, y0, y1, z0, z1, webs=5):
    chord = (y1 - y0) * 0.26
    box(out, "banzo superior", x0, y1 - chord, z0, x1, y1, z1, GALV, GALV, GALV_DARK)
    box(out, "banzo inferior", x0, y0, z0, x1, y0 + chord, z1, GALV, GALV_DARK, GALV_DARK)
    span = z1 - z0
    for i in range(webs):
        zc = z0 + span * (i + 0.5) / webs
        box(out, f"montante {i}", x0 + 0.2, y0 + chord - EPS, zc - 0.28, x1 - 0.2, y1 - chord + EPS,
            zc + 0.28, GALV_DARK)
    for z in (z0, z1 - 0.35):
        box(out, "tampa da cruzeta", x0 - 0.1, y0 - 0.1, z, x1 + 0.1, y1 + 0.1, z + 0.35, GALV_DARK)


def suspension_string(out, x, z, y_top, discs=7, pitch=1.7):
    box(out, "manilha", x - 0.34, y_top - 1.0, z - 0.34, x + 0.34, y_top, z + 0.34, STEEL_DARK)
    y = y_top - 1.0
    for i in range(discs):
        disc(out, f"pino {i}", x, z, 0.3, y - pitch * 0.42, y, STEEL_DARK)
        disc(out, f"disco {i}", x, z, 1.08, y - pitch, y - pitch * 0.42, PORCELAIN,
             cap_top=False, cap_bottom=i == discs - 1)
        y -= pitch
    box(out, "garfo", x - 0.3, y - 0.9, z - 0.3, x + 0.3, y, z + 0.3, STEEL_DARK)
    box(out, "grampo de suspensao", x - 0.55, y - 1.5, z - 1.5, x + 0.55, y - 0.9, z + 1.5, ALUMINUM)
    box(out, "sapatilha", x - 0.4, y - 1.85, z - 0.9, x + 0.4, y - 1.5, z + 0.9, ALUMINUM)
    return y - 1.6


def step_bolts(out, front, back, y0, y1, every=12.0):
    y = y0
    side = 0
    while y < y1:
        if side == 0:
            box(out, "degrau", C - 0.24, y, front(y) - 2.6, C + 0.24, y + 0.48, front(y) + 0.2, STEEL_DARK)
        else:
            box(out, "degrau", C - 0.24, y, back(y) - 0.2, C + 0.24, y + 0.48, back(y) + 2.6, STEEL_DARK)
        y += every
        side ^= 1


def ribbed_panel(out, x0, x1, y0, y1, z, depth, tex, ribs, axis="x"):
    for i in range(ribs):
        if axis == "x":
            xc = x0 + (x1 - x0) * (i + 0.5) / ribs
            box(out, f"nervura {i}", xc - 0.32, y0, z, xc + 0.32, y1, z + depth, tex)
        else:
            yc = y0 + (y1 - y0) * (i + 0.5) / ribs
            box(out, f"nervura {i}", x0, yc - 0.32, z, x1, yc + 0.32, z + depth, tex)


def cabinet_door(out, x0, x1, y0, y1, zf, body, dark):
    box(out, "porta", x0, y0, zf - 0.35, x1, y1, zf, body, dark, dark)
    box(out, "batente inferior", x0 - 0.2, y0 - 0.3, zf - 0.38, x1 + 0.2, y0, zf + 0.02, dark)
    box(out, "batente superior", x0 - 0.2, y1, zf - 0.38, x1 + 0.2, y1 + 0.3, zf + 0.02, dark)
    box(out, "batente esquerdo", x0 - 0.3, y0 - 0.3, zf - 0.38, x0, y1 + 0.3, zf + 0.02, dark)
    box(out, "batente direito", x1, y0 - 0.3, zf - 0.38, x1 + 0.3, y1 + 0.3, zf + 0.02, dark)
    for y in (y0 + 1.4, y1 - 1.4):
        box(out, "dobradica", x0 - 0.36, y - 0.5, zf - 0.5, x0 + 0.1, y + 0.5, zf - 0.02, STEEL_DARK)
    ym = (y0 + y1) / 2
    box(out, "puxador", x1 - 2.2, ym - 0.8, zf - 0.62, x1 - 0.6, ym + 0.8, zf - 0.3, STEEL_DARK)
    box(out, "lingueta", x1 - 1.9, ym - 0.35, zf - 0.92, x1 - 1.1, ym + 0.35, zf - 0.6, STEEL)
    box(out, "porta-cadeado", x1 - 1.7, ym + 1.4, zf - 0.75, x1 - 0.9, ym + 2.6, zf - 0.35, STEEL)
    box(out, "cadeado", x1 - 1.6, ym + 2.0, zf - 1.25, x1 - 1.0, ym + 3.0, zf - 0.75, BRASS)


LED_PITCH = 1.8


def status_leds(base, on, off, x, y, zf, count=3):
    box(base, "painel de sinalizacao", x - 0.3, y - 1.5, zf - 0.28, x + count * LED_PITCH + 0.3, y + 1.5,
        zf + 0.02, PANEL)
    lens = {"north": (0, 0, 16, 16)}
    for i in range(count):
        cx = x + i * LED_PITCH + 0.5
        tex_on = LED_GREEN_ON if i else LED_RED_ON
        tex_off = LED_GREEN_OFF if i else LED_RED_OFF
        box(on, f"led aceso {i}", cx, y - 0.65, zf - 0.42, cx + 1.3, y + 0.65, zf - 0.2, tex_on,
            fit=lens, glow=True)
        box(off, f"led apagado {i}", cx, y - 0.65, zf - 0.42, cx + 1.3, y + 0.65, zf - 0.2, tex_off,
            fit=lens)


def mv_terminal(out, x, y, z_face):
    disc_z(out, "flange da bucha", x, y, 0.95, z_face - 0.3, z_face + 0.02, GREY_DARK)
    disc_z(out, "bucha de media", x, y, 0.52, z_face - 3.4, z_face - 0.3, PORCELAIN_BROWN)
    for dz in (1.0, 1.9, 2.8):
        disc_z(out, "saia", x, y, 1.0, z_face - dz - 0.22, z_face - dz, PORCELAIN_BROWN)
    disc_z(out, "capa", x, y, 0.45, z_face - 3.9, z_face - 3.4, BRASS)
    box(out, "terminal de media", x - 0.32, y - 0.32, z_face - 4.9, x + 0.32, y + 0.32, z_face - 3.9, BRASS)


def tall_hv_bushing(out, x, z, y0, height=12.0):
    disc(out, "turret", x, z, 1.45, y0, y0 + 1.4, GREY)
    disc(out, "flange", x, z, 1.7, y0 + 1.4, y0 + 2.0, GREY_DARK)
    disc(out, "coluna", x, z, 0.78, y0 + 2.0, y0 + height - 1.2, PORCELAIN_BROWN)
    sheds = max(4, int((height - 3.6) / 1.5))
    for i in range(sheds):
        y = y0 + 2.6 + i * (height - 4.4) / sheds
        disc(out, f"saia {i}", x, z, 1.42, y, y + 0.34, PORCELAIN_BROWN)
    disc(out, "capa", x, z, 0.72, y0 + height - 1.2, y0 + height - 0.55, BRASS)
    box(out, "conector", x - 0.42, y0 + height - 0.55, z - 0.42, x + 0.42, y0 + height + 0.5, z + 0.42, BRASS)
    return y0 + height + 0.5


def radiator_bank(out, x0, x1, z_center, y0, y1, fins=7, pitch=1.05, tex=TANK, dark=TANK_DARK):
    for i in range(fins):
        fz = z_center - (fins - 1) * pitch / 2 + i * pitch
        box(out, f"aleta {i}", x0, y0 + 1.0, fz - 0.13, x1, y1 - 1.0, fz + 0.13, tex, dark, dark)
    half = (fins - 1) * pitch / 2 + 0.6
    box(out, "coletor superior", x0 + 0.1, y1 - 1.4, z_center - half, x1 - 0.1, y1 - 0.8, z_center + half, dark)
    box(out, "coletor inferior", x0 + 0.1, y0 + 0.8, z_center - half, x1 - 0.1, y0 + 1.4, z_center + half, dark)



def build_concrete():
    pole = Pole("utility_pole_concrete", "concrete", "CONCRETE", 10, CONCRETE, (126.0, 162.0))
    out, top = pole.base, pole.top
    b, web = 2.6, 1.3

    def a_at(y):
        return 6.0 - 3.4 * y / top

    def f_at(y):
        return 0.8 - 0.2 * y / top

    def front(y):
        return C - a_at(step_mid(y, top)) / 2

    dt_shaft(out, top, b, a_at, f_at, web)
    collar_rect(out, C - b / 2, C + b / 2, C - a_at(0) / 2, C + a_at(0) / 2)
    _, brace_low = mv_crossarm(pole, C + b / 2, C - web / 2, top - 3.6, C, "concrete", PORCELAIN, (-6.3, 1.9, 22.3))
    dt_holes(out, [y for y in range(44, 152, 8) if abs(y - brace_low) > 1.0], web)

    rack_x, rack_top = C + 0.25, 125.0
    zf = front(119.0)
    secondary_rack(pole, rack_x, zf, rack_top, 4)
    wire_x = C - b / 2 + 0.24
    ground_wire(out, wire_x, front, 40.0, rack_top - 0.3)
    box(out, "ligacao do neutro", wire_x - 0.12, rack_top - 0.55, zf - 0.3, rack_x - 0.5, rack_top - 0.3, zf - 0.04,
        COPPER)
    conduit(out, wire_x, front, 40.0)
    id_plate(out, C - 0.6, C + 1.15, 45.0, 47.8, front(46.4))

    for s in range(pole.sections):
        y = s * 16.0
        pole.hit(C - b / 2, y, C - a_at(y) / 2, C + b / 2, y + 16.0, C + a_at(y) / 2)
    pole.hit(C - b / 2 - 1.7, 0, C - a_at(0) / 2 - 1.7, C + b / 2 + 1.7, 0.95, C + a_at(0) / 2 + 1.7)
    return pole


def build_street_light():
    pole = Pole("utility_pole_street_light", "street_light", "STREET_LIGHT", 10, ROUND, (104.0, 140.0))
    out, top = pole.base, pole.top

    def r_at(y):
        return 2.6 - 1.3 * y / top

    def center(_):
        return C, C

    def rs(y):
        return r_at(step_mid(y, top))

    round_shaft(out, top, r_at, center, ROUND, ROUND_GRIME, CONCRETE_LIGHT)
    collar_round(out, C, C, rs(0))
    disc(out, "tampa do topo", C, C, rs(top - 1) * 0.8, top, top + 0.3, CONCRETE_LIGHT)
    front = round_front(r_at, center, C, top)

    y_arm = 130.0
    zf = front(y_arm)
    for y0 in (125.0, 134.4):
        disc(out, "cinta", C, C, rs(y0) + 0.14, y0, y0 + 0.6, STEEL)
        zb = C + rs(y0) * 0.96
        box(out, "orelha da cinta", C - 0.45, y0 - 0.05, zb - 0.1, C + 0.45, y0 + 0.65, zb + 0.75, STEEL)
        box(out, "parafuso da cinta", C - 0.75, y0 + 0.18, zb + 0.28, C + 0.75, y0 + 0.42, zb + 0.52, STEEL_DARK)
    box(out, "suporte do braco", C - 0.7, 124.4, zf - 0.42, C + 0.7, 135.6, zf + 0.06, STEEL)
    disc_z(out, "luva do braco", C, y_arm, 0.66, zf - 1.9, zf - 0.4, STEEL)
    z_start = zf - 1.5
    run = 9.0
    z_end, y_end = z_start - run, y_arm + run * TAN_22
    mz, my = (z_start + z_end) / 2, (y_arm + y_end) / 2
    length = run / math.cos(math.radians(22.5)) + 0.3
    disc_z(out, "braco", C, my, 0.5, mz - length / 2, mz + length / 2, STEEL, rot=rotation("x", 22.5, (C, my, mz)))
    disc_z(out, "cotovelo", C, y_end, 0.56, z_end - 0.45, z_end + 0.45, STEEL)
    disc_z(out, "ponteira", C, y_end, 0.5, z_end - 2.9, z_end + 0.2, STEEL)
    rear = z_end - 0.9
    lens = luminaire(out, y_end, rear)
    on, off = [], []
    box(on, "lente led acesa", *lens, LED_ON, glow=True)
    box(off, "lente led", *lens, LED_OFF)
    pole.lamp_section = math.floor(y_end / 16.0)
    pole.overlays = [("street_light_lamp_on", pole.lamp_section, "lit", "true", on),
                     ("street_light_lamp_off", pole.lamp_section, "lit", "false", off)]
    pole.icon_extra = off

    rack_top = 100.0
    zf = front(97.0)
    secondary_rack(pole, C, zf, rack_top, 2)
    wire_x = C - 1.3
    wire_front = round_front(r_at, center, wire_x, top)
    ground_wire(out, wire_x, wire_front, 30.0, rack_top - 0.3, clamp_every=12.0)
    link_z = wire_front(rack_top - 0.4)
    box(out, "ligacao do neutro", wire_x - 0.12, rack_top - 0.55, min(link_z, zf) - 0.3, C - 0.5,
        rack_top - 0.3, max(link_z, zf) - 0.04, COPPER)
    conduit(out, wire_x, wire_front, 30.0)
    id_plate(out, C - 0.8, C + 0.8, 44.0, 46.8, front(45.4))

    for s in range(pole.sections):
        y = s * 16.0
        r = r_at(y) * 0.96
        pole.hit(C - r, y, C - r, C + r, y + 16.0, C + r)
    pole.hit(C - rs(0) - 1.7, 0, C - rs(0) - 1.7, C + rs(0) + 1.7, 0.95, C + rs(0) + 1.7)
    pole.hit(C - 0.7, 124.4, z_end - 0.5, C + 0.7, y_end + 0.6, zf + 0.06)
    pole.hit(5.85, y_end - 0.92, rear - 10.65, 10.15, y_end + 2.85, rear + 0.25)
    return pole


def build_wood():
    pole = Pole("utility_pole_wood", "wood", "WOOD", 9, WOOD, (118.0, 150.0))
    out, top = pole.base, pole.top

    def r_at(y):
        return 2.35 - 0.65 * y / top

    def center(y):
        return C + 0.13 * math.sin(y / 31.0 + 0.4), C + 0.1 * math.sin(y / 19.0 + 1.7)

    def rs(y):
        return r_at(step_mid(y, top))

    def cs(y):
        return center(step_mid(y, top))

    round_shaft(out, top, r_at, center, WOOD, WOOD_GROUND, WOOD_END)
    tx, tz = cs(top - 1)
    disc(out, "chapa do pino de topo", tx, tz, 1.05, top, top + 0.22, STEEL)
    pin_insulator(out, tx, tz, top + 0.22, PORCELAIN_BROWN)
    pole.phase(tx, top + 0.22 + INSULATOR_GROOVE, tz)
    pole.hit(tx - 1.05, top, tz - 1.05, tx + 1.05, top + 0.22 + INSULATOR_HEIGHT, tz + 1.05)

    arm_top = top - 6.8
    ym = arm_top - 0.9
    cx, cz = cs(ym)
    mv_crossarm(pole, cx + rs(ym) * 0.96, cx - rs(ym) * 0.96, arm_top, cz, "wood", PORCELAIN_BROWN, (-6.3, 22.3))

    rack_top = 115.0
    rack_x = cs(111.0)[0] + 0.1
    zf = round_front(r_at, center, rack_x, top)(111.0)
    zb = cs(111.0)[1] + rs(111.0) * 0.96
    secondary_rack(pole, rack_x, zf, rack_top, 3, back=zb)

    wire_x = C - 1.1
    wire_front = round_front(r_at, center, wire_x, top)
    ground_wire(out, wire_x, wire_front, 36.0, rack_top - 0.3, clamp_every=12.0)
    link_z = wire_front(rack_top - 0.4)
    box(out, "ligacao do neutro", wire_x - 0.12, rack_top - 0.55, min(link_z, zf) - 0.3, rack_x - 0.5,
        rack_top - 0.3, max(link_z, zf) - 0.04, COPPER)
    conduit(out, wire_x, wire_front, 36.0)
    id_plate(out, C - 0.7, C + 0.7, 42.0, 44.6, round_front(r_at, center, C, top)(43.3))

    for s in range(pole.sections):
        y = s * 16.0
        x, z = cs(y + 8.0)
        r = r_at(y) * 0.96
        pole.hit(x - r, y, z - r, x + r, y + 16.0, z + r)
    return pole


def build_transformer():
    pole = Pole("utility_pole_transformer", "transformer", "TRANSFORMER", 11, CONCRETE, (106.0, 158.0))
    out, top = pole.base, pole.top
    b, web = 3.0, 1.4

    def a_at(y):
        return 7.2 - 4.2 * y / top

    def f_at(y):
        return 0.95 - 0.3 * y / top

    def front(y):
        return C - a_at(step_mid(y, top)) / 2

    dt_shaft(out, top, b, a_at, f_at, web)
    collar_rect(out, C - b / 2, C + b / 2, C - a_at(0) / 2, C + a_at(0) / 2)
    _, brace_low = mv_crossarm(pole, C + b / 2, C - web / 2, top - 3.7, C, "concrete", PORCELAIN, (-6.3, 1.9, 22.3))

    tank_bottom, tank_top = 112.0, 124.4
    busy = [(brace_low - 1.0, brace_low + 1.0), (149.5, 152.5), (tank_bottom, tank_top + 0.5)]
    dt_holes(out, [y for y in range(40, 168, 8) if not any(lo <= y <= hi for lo, hi in busy)], web)

    ca_bottom, ca_top = 150.1, 152.0
    zf_ca = front(151.0)
    crossarm(out, -8.0, 24.0, ca_bottom, ca_top, zf_ca - 1.5, zf_ca, "concrete", axis="x")
    zb_ca = C + a_at(step_mid(151.0, top)) / 2
    box(out, "sela", C - b / 2 - 0.3, ca_bottom + 0.2, zf_ca - 0.05, C - b / 2, ca_top - 0.2, zb_ca + 0.3, STEEL)
    box(out, "sela", C + b / 2, ca_bottom + 0.2, zf_ca - 0.05, C + b / 2 + 0.3, ca_top - 0.2, zb_ca + 0.3, STEEL)
    box(out, "sela traseira", C - b / 2 - 0.3, ca_bottom + 0.2, zb_ca, C + b / 2 + 0.3, ca_top - 0.2, zb_ca + 0.3,
        STEEL)
    y_low = ca_bottom - 9.0
    zf_low = front(y_low)
    for sign in (-1, 1):
        bar_xy(out, "mao francesa", zf_low - 0.3, zf_low - 0.02, C, y_low, C + sign * 9.0, ca_bottom, STEEL)
    box(out, "parafuso da mao francesa", C - 0.3, y_low - 0.3, zf_low - 0.5, C + 0.3, y_low + 0.3, zf_low - 0.3,
        STEEL_DARK)
    pole.hit(-8.0, ca_bottom - 0.3, zf_ca - 1.5, 24.0, ca_top, zf_ca)

    bushing_x = (4.3, 8.0, 11.7)
    cutout_x = (-2.2, 8.0, 18.2)
    terminals = [fuse_cutout(out, x, 151.0, zf_ca - 2.9, zf_ca - 1.5) for x in cutout_x]
    for x in cutout_x:
        pole.hit(x - 0.9, 146.4, zf_ca - 5.2, x + 0.9, 155.6, zf_ca - 1.5)
    jz = terminals[0][2]

    x0, x1, z0, z1 = 3.2, 12.8, -3.4, 5.2
    cut = 0.55
    box(out, "tanque", x0 + cut, tank_bottom, z0, x1 - cut, tank_top, z1, TANK, sides={"north": TANK_FRONT})
    box(out, "tanque lateral", x0, tank_bottom, z0 + cut, x0 + cut + EPS, tank_top - EPS, z1 - cut, TANK)
    box(out, "tanque lateral", x1 - cut - EPS, tank_bottom, z0 + cut, x1, tank_top - EPS, z1 - cut, TANK)
    box(out, "base do tanque", x0 + 0.4, tank_bottom - 0.7, z0 + 0.4, x1 - 0.4, tank_bottom + 0.02, z1 - 0.4,
        TANK_DARK)
    box(out, "tampa do tanque", x0 - 0.3, tank_top, z0 - 0.3, x1 + 0.3, tank_top + 0.7, z1 + 0.3, TANK)
    for x in (4.2, 6.1, 8.0, 9.9, 11.8):
        box(out, "parafuso da tampa", x - 0.16, tank_top + 0.15, z0 - 0.38, x + 0.16, tank_top + 0.55, z0 - 0.3,
            STEEL_DARK)
    for side_x0, side_x1 in ((x0 - 1.4, x0 + 0.02), (x1 - 0.02, x1 + 1.4)):
        for i in range(7):
            fz = -2.5 + i * 1.05
            box(out, "aleta do radiador", side_x0, tank_bottom + 1.0, fz, side_x1, tank_top - 1.0, fz + 0.26, TANK,
                TANK_DARK, TANK_DARK)
        box(out, "coletor superior", side_x0 + 0.1, tank_top - 1.4, -2.7, side_x1 - 0.1, tank_top - 0.8, 4.3,
            TANK_DARK)
        box(out, "coletor inferior", side_x0 + 0.1, tank_bottom + 0.8, -2.7, side_x1 - 0.1, tank_bottom + 1.4, 4.3,
            TANK_DARK)
    box(out, "placa de dados", 4.4, tank_bottom + 0.8, z0 - 0.1, 6.0, tank_bottom + 1.9, z0 + 0.02, ALUMINUM,
        sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    on, off = [], []
    status_leds(out, on, off, 8.6, tank_bottom + 4.6, z0 - 0.1, count=2)
    status_section = math.floor((tank_bottom + 4.6) / 16.0)
    pole.lamp_section = status_section
    pole.overlays = [("transformer_status_on", status_section, "energized", "true", on),
                     ("transformer_status_off", status_section, "energized", "false", off)]
    pole.icon_extra = on
    for x in (11.5, 9.3, 7.1, 4.9):
        lv_bushing(out, x, tank_top - 2.4, z0)
    lid = tank_top + 0.7
    for x in bushing_x:
        bushing_top = hv_bushing(out, x, jz, lid)
        arrester_top = surge_arrester(out, x, -0.9, lid)
        box(out, "descida do para-raios", x - 0.12, arrester_top, -1.02, x + 0.12, bushing_top - 0.35, -0.78, ALUMINUM)
        box(out, "ligacao do para-raios", x - 0.12, bushing_top - 0.6, -1.02, x + 0.12, bushing_top - 0.35, jz - 0.3,
            ALUMINUM)
    box(out, "comutador", 11.2, lid, -3.0, 12.4, lid + 0.35, -1.9, STEEL)
    box(out, "manopla do comutador", 11.6, lid + 0.35, -2.7, 12.0, lid + 0.8, -2.2, STEEL_DARK)

    bushing_top = lid + 5.8
    for (tx, ty, _), bx in zip(terminals, bushing_x):
        if abs(tx - bx) < 1e-6:
            box(out, "jumper", tx - 0.15, bushing_top - 0.1, jz - 0.15, tx + 0.15, ty + 0.1, jz + 0.15, ALUMINUM)
            continue
        dx = abs(bx - tx)
        knee = ty - 2.3
        box(out, "jumper", tx - 0.15, knee - 0.1, jz - 0.15, tx + 0.15, ty + 0.1, jz + 0.15, ALUMINUM)
        bar_xy(out, "jumper", jz - 0.15, jz + 0.15, bx, knee - dx, tx, knee, ALUMINUM, width=0.3, extra=0.3)
        box(out, "jumper", bx - 0.15, bushing_top - 0.1, jz - 0.15, bx + 0.15, knee - dx + 0.1, jz + 0.15, ALUMINUM)

    for y_lo, y_hi in ((tank_top - 2.0, tank_top - 0.9), (tank_bottom + 1.2, tank_bottom + 2.2)):
        box(out, "perfil de suporte", 3.6, y_lo, z1, 12.4, y_hi, z1 + 0.65, STEEL)
    for px0, px1 in ((C - b / 2 - 0.4, C - b / 2), (C + b / 2, C + b / 2 + 0.4)):
        box(out, "chapa de suporte", px0, tank_bottom + 0.9, z1, px1, tank_top - 0.6, z1 + 3.6, STEEL)
    for y in (tank_top - 1.45, tank_bottom + 1.7):
        through_bolt_x(out, y, z1 + 2.1, C - b / 2 - 0.4, C + b / 2 + 0.4)

    wire_x = C - b / 2 + 0.22
    box(out, "terminal de aterramento", x0 + 0.2, tank_bottom + 0.2, z1, x0 + 0.7, tank_bottom + 0.8, z1 + 0.4, BRASS)
    zf_g = front(tank_bottom + 0.5)
    box(out, "fio terra", x0 + 0.45, tank_bottom + 0.35, zf_g - 0.3, wire_x + 0.12, tank_bottom + 0.6, zf_g - 0.04,
        COPPER)
    ground_wire(out, wire_x, front, 40.0, tank_bottom + 0.6)
    conduit(out, wire_x, front, 40.0)

    rack_x, rack_top = C + 0.25, 104.0
    zf = front(98.0)
    secondary_rack(pole, rack_x, zf, rack_top, 4)
    box(out, "ligacao do neutro", wire_x - 0.12, rack_top - 0.55, zf - 0.3, rack_x - 0.5, rack_top - 0.3, zf - 0.04,
        COPPER)

    zf_d = front(57.1)
    box(out, "placa de perigo", C - 0.8, 56.0, zf_d - 0.12, C + 1.4, 58.2, zf_d + 0.02, STEEL,
        sides={"north": DANGER}, fit={"north": (0, 0, 16, 16)})
    id_plate(out, C - 0.7, C + 1.1, 45.0, 47.8, front(46.4))

    for s in range(pole.sections):
        y = s * 16.0
        pole.hit(C - b / 2, y, C - a_at(y) / 2, C + b / 2, y + 16.0, C + a_at(y) / 2)
    pole.hit(C - b / 2 - 1.7, 0, C - a_at(0) / 2 - 1.7, C + b / 2 + 1.7, 0.95, C + a_at(0) / 2 + 1.7)
    pole.hit(x0 - 1.4, tank_bottom - 0.7, z0 - 0.3, x1 + 1.4, lid, z1 + 3.6)
    pole.hit(3.3, lid, -1.9, 12.7, bushing_top, jz + 1.0)
    pole.hit(4.4, tank_top - 2.9, z0 - 2.5, 12.0, tank_top - 1.9, z0)
    return pole



def build_low_voltage():
    pole = Pole("utility_pole_low_voltage", "low_voltage", "LOW_VOLTAGE", 9, CONCRETE, (120.0, 148.0))
    out, top = pole.base, pole.top
    b, web = 2.4, 1.2

    def a_at(y):
        return 5.4 - 2.9 * y / top

    def f_at(y):
        return 0.75 - 0.18 * y / top

    def front(y):
        return C - a_at(step_mid(y, top)) / 2

    dt_shaft(out, top, b, a_at, f_at, web)
    collar_rect(out, C - b / 2, C + b / 2, C - a_at(0) / 2, C + a_at(0) / 2)
    dt_holes(out, [y for y in range(40, 140, 8) if not 120.0 <= y <= 140.0], web)

    rack_x, rack_top = C + 0.25, 137.0
    zf = front(131.0)
    secondary_rack(pole, rack_x, zf, rack_top, 4)
    tap_top = 118.0
    zf_tap = front(117.0)
    secondary_rack(pole, rack_x, zf_tap, tap_top, 1)

    wire_x = C - b / 2 + 0.22
    ground_wire(out, wire_x, front, 34.0, rack_top - 0.3)
    for y_link, z_link in ((rack_top, zf), (tap_top, zf_tap)):
        box(out, "ligacao do neutro", wire_x - 0.12, y_link - 0.55, z_link - 0.3, rack_x - 0.5,
            y_link - 0.3, z_link - 0.04, COPPER)
    conduit(out, wire_x, front, 34.0)
    id_plate(out, C - 0.6, C + 1.15, 42.0, 44.8, front(43.4))

    for s in range(pole.sections):
        y = s * 16.0
        pole.hit(C - b / 2, y, C - a_at(y) / 2, C + b / 2, y + 16.0, C + a_at(y) / 2)
    pole.hit(C - b / 2 - 1.7, 0, C - a_at(0) / 2 - 1.7, C + b / 2 + 1.7, 0.95, C + a_at(0) / 2 + 1.7)
    return pole


def build_high_voltage():
    pole = Pole("utility_pole_high_voltage", "high_voltage", "HIGH_VOLTAGE", 14, GALV, (186.0, 232.0))
    out, top = pole.base, pole.top

    def r_at(y):
        return 3.5 - 1.7 * y / top

    def center(_):
        return C, C

    def rs(y):
        return r_at(step_mid(y, top))

    round_shaft(out, top, r_at, center, GALV, GALV_DARK, GALV, grime_top=8.0)
    front = round_front(r_at, center, C, top)

    def back(y):
        return C + disc_reach(rs(y), 0.0)

    collar_round(out, C, C, rs(0) + 0.8)
    disc(out, "flange da base", C, C, rs(0) + 1.5, 0.95, 1.75, GALV_DARK)
    for i in range(8):
        angle = i * math.pi / 4
        bx = C + math.cos(angle) * (rs(0) + 1.0)
        bz = C + math.sin(angle) * (rs(0) + 1.0)
        box(out, "chumbador", bx - 0.25, 1.75, bz - 0.25, bx + 0.25, 2.6, bz + 0.25, STEEL_DARK)
    step_bolts(out, front, back, 28.0, 196.0, 14.0)

    arm_bottom, arm_top = 206.0, 210.4
    x_face = C + rs(208.0) * 0.96
    x_back = C - rs(208.0) * 0.96
    steel_crossarm(out, x_face - 0.15, x_face + 2.35, arm_bottom, arm_top, -8.0, 24.0, webs=7)
    through_bolt_x(out, (arm_bottom + arm_top) / 2, C, x_back, x_face + 2.35)
    y_brace = arm_bottom - 11.0
    for sign in (-1, 1):
        brace_yz(out, x_face + 0.15, x_face + 0.55, y_brace, C, arm_bottom, C + sign * 11.0, GALV, 0.75)
    through_bolt_x(out, y_brace, C, x_back, x_face + 0.55)
    pole.hit(x_face - 0.2, arm_bottom - 0.4, -8.0, x_face + 2.5, arm_top, 24.0)

    xc = x_face + 1.1
    for z in (-6.0, 8.0, 22.0):
        y_clamp = suspension_string(out, xc, z, arm_bottom)
        pole.line(xc, y_clamp, z)
        pole.hit(xc - 1.15, y_clamp - 0.4, z - 1.6, xc + 1.15, arm_bottom, z + 1.6)

    disc(out, "ponteira", C, C, rs(top - 1) * 0.8, top, top + 1.4, GALV_DARK, GALV)
    box(out, "haste do cabo-guarda", C - 0.38, top + 1.4, C - 0.38, C + 0.38, top + 5.2, C + 0.38, GALV)
    box(out, "grampo do cabo-guarda", C - 0.72, top + 5.2, C - 1.0, C + 0.72, top + 6.0, C + 1.0, ALUMINUM)
    pole.line(C, top + 5.6, C, shield=True)
    pole.hit(C - 1.0, top, C - 1.0, C + 1.0, top + 6.0, C + 1.0)

    beacon_y = 214.0
    xb = C + rs(beacon_y) * 0.96
    box(out, "suporte do balizador", C, beacon_y + 0.25, C - 0.3, xb + 1.9, beacon_y + 0.85, C + 0.3, GALV_DARK)
    box(out, "base do balizador", xb + 1.1, beacon_y + 0.85, C - 0.7, xb + 2.5, beacon_y + 1.35, C + 0.7, GALV_DARK)
    lens = (xb + 1.25, beacon_y + 1.35, C - 0.55, xb + 2.35, beacon_y + 2.45, C + 0.55)
    on, off = [], []
    box(on, "lente do balizador", *lens, LED_RED_ON, glow=True)
    box(off, "lente do balizador", *lens, LED_RED_OFF)
    box(out, "capa do balizador", xb + 1.4, beacon_y + 2.45, C - 0.4, xb + 2.2, beacon_y + 2.75, C + 0.4, GALV_DARK)
    pole.lamp_section = math.floor((beacon_y + 1.9) / 16.0)
    pole.overlays = [("high_voltage_beacon_on", pole.lamp_section, "lit", "true", on),
                     ("high_voltage_beacon_off", pole.lamp_section, "lit", "false", off)]
    pole.icon_extra = on
    pole.hit(xb + 1.0, beacon_y + 0.25, C - 0.8, xb + 2.6, beacon_y + 2.75, C + 0.8)

    ub_top = 150.0
    y_mid = ub_top - 0.9
    mv_crossarm(pole, C + rs(y_mid) * 0.96, C - rs(y_mid) * 0.96, ub_top, C, "steel", PORCELAIN,
                (-6.3, 1.9, 22.3))

    wire_x = C - 1.4
    wire_front = round_front(r_at, center, wire_x, top)
    ground_wire(out, wire_x, wire_front, 2.6, 26.0, clamp_every=10.0)
    zf_d = front(57.2)
    box(out, "placa de perigo", C - 1.1, 56.0, zf_d - 0.14, C + 1.1, 58.2, zf_d + 0.02, STEEL,
        sides={"north": DANGER}, fit={"north": (0, 0, 16, 16)})
    id_plate(out, C - 0.8, C + 0.8, 44.0, 46.8, front(45.4))

    for s in range(pole.sections):
        y = s * 16.0
        r = r_at(y) * 0.96
        pole.hit(C - r, y, C - r, C + r, y + 16.0, C + r)
    pole.hit(C - rs(0) - 1.8, 0, C - rs(0) - 1.8, C + rs(0) + 1.8, 1.75, C + rs(0) + 1.8)
    return pole


def build_pad_transformer():
    pole = Pole("pad_transformer", "pad_transformer", "PAD_TRANSFORMER", 2, CABINET, (0.0, 34.0))
    out = pole.base
    x0, x1, z0, z1 = 0.8, 15.2, 3.0, 15.0
    pad_y, body_top = 1.9, 22.0
    lid_top = body_top + 1.3

    box(out, "base de concreto", x0 - 2.4, 0.0, z0 - 2.0, x1 + 2.4, 1.15, z1 + 2.0, COLLAR)
    box(out, "base de concreto topo", x0 - 1.4, 1.15, z0 - 1.2, x1 + 1.4, pad_y, z1 + 1.2, COLLAR)
    dx0, dx1, dy0, dy1 = x0 + 0.8, 7.4, pad_y + 1.2, body_top - 1.8
    bay_back = z0 + 6.6
    box(out, "carcaca", dx1, pad_y, z0, x1, body_top, z1, CABINET, CABINET_TOP, CABINET_DARK)
    box(out, "carcaca", x0, dy1, z0, dx1, body_top, z1, CABINET, CABINET_TOP, CABINET_DARK)
    box(out, "carcaca", x0, pad_y, z0, dx1, dy0, z1, CABINET, CABINET_TOP, CABINET_DARK)
    box(out, "carcaca", x0, dy0, bay_back, dx1, dy1, z1, CABINET, CABINET_TOP, CABINET_DARK)
    box(out, "carcaca", x0, dy0, z0, dx0, dy1, bay_back, CABINET, CABINET_TOP, CABINET_DARK)
    box(out, "forro do compartimento", dx0, dy1 - 0.4, z0 + 0.3, dx1, dy1, bay_back, CABINET_DARK)
    box(out, "piso do compartimento", dx0, dy0, z0 + 0.3, dx1, dy0 + 0.4, bay_back, CABINET_DARK)
    box(out, "fundo do compartimento", dx0, dy0, bay_back - 0.4, dx1, dy1, bay_back, CABINET_DARK)
    box(out, "lateral do compartimento", dx0, dy0, z0 + 0.3, dx0 + 0.4, dy1, bay_back, CABINET_DARK)

    core_x0, core_x1 = dx0 + 0.9, dx1 - 0.7
    core_y0, core_y1 = dy0 + 1.4, dy1 - 3.6
    core_z = (z0 + bay_back) / 2
    for lo, hi in ((core_y0, core_y0 + 1.0), (core_y1 - 1.0, core_y1)):
        box(out, "culatra", core_x0, lo, core_z - 1.5, core_x1, hi, core_z + 1.5, STEEL_DARK,
            STEEL, STEEL)
    box(out, "coluna do nucleo", (core_x0 + core_x1) / 2 - 0.55, core_y0 + 0.9,
        core_z - 1.2, (core_x0 + core_x1) / 2 + 0.55, core_y1 - 0.9, core_z + 1.2, STEEL)
    for cx in (core_x0 + 1.05, core_x1 - 1.05):
        disc(out, "enrolamento", cx, core_z, 1.0, core_y0 + 1.0, core_y1 - 1.0, COPPER)
        for band in (core_y0 + 2.0, (core_y0 + core_y1) / 2, core_y1 - 2.0):
            disc(out, "cinta do enrolamento", cx, core_z, 1.12, band - 0.2, band + 0.2, STEEL_DARK)
    box(out, "ligacao", core_x0 + 1.05, core_y1 - 0.9, core_z - 0.16, core_x1 - 1.05,
        core_y1 - 0.5, core_z + 0.16, COPPER)
    box(out, "bloco de bornes", dx0 + 0.8, dy1 - 2.6, z0 + 0.9, dx1 - 0.8, dy1 - 1.6, z0 + 2.1,
        BREAKER_BODY)
    for index in range(4):
        bx = dx0 + 1.3 + index * 1.3
        box(out, "borne", bx, dy1 - 2.4, z0 + 0.6, bx + 0.7, dy1 - 1.8, z0 + 1.0, BRASS)
    box(out, "placa interna", dx1 - 2.2, dy0 + 0.8, z0 + 0.6, dx1 - 0.7, dy0 + 2.3, z0 + 0.72,
        ALUMINUM, sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    pole.door = (dx0 - 0.6, dy0 - 0.4, dx1 + 0.4, dy1 + 0.4, dx0 - 0.35, z0 - 0.35)
    for face_x0, face_x1 in ((x0 - 0.24, x0 + 0.02), (x1 - 0.02, x1 + 0.24)):
        box(out, "veneziana", face_x0, pad_y + 3.0, z0 + 2.2, face_x1, body_top - 3.0, z1 - 2.2, LOUVER)
    ribbed_panel(out, x0 + 0.8, x1 - 0.8, pad_y + 1.0, body_top - 1.0, z1, 0.3, CABINET_DARK, 5)

    for y in (dy0 + 1.6, dy1 - 1.6):
        box(out, "dobradica", dx0 - 0.45, y - 0.9, z0 - 0.5, dx0 + 0.15, y + 0.9, z0 + 0.02,
            STEEL_DARK)
    leaf = []
    cabinet_door(leaf, dx0, dx1, dy0, dy1, z0, CABINET, CABINET_DARK)
    box(leaf, "veneziana da porta", dx0 + 1.0, dy1 - 5.4, z0 - 0.42, dx1 - 1.0, dy1 - 1.4,
        z0 - 0.28, LOUVER)
    pole.extras = [("pad_transformer_door", leaf)]
    box(out, "placa de perigo", x0 + 1.8, 17.0, z0 - 0.62, x0 + 4.0, 19.2, z0 - 0.46, STEEL,
        sides={"north": DANGER}, fit={"north": (0, 0, 16, 16)})
    box(out, "placa de dados", x0 + 1.8, 5.6, z0 - 0.62, x0 + 3.4, 7.2, z0 - 0.46, ALUMINUM,
        sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    box(out, "conector de aterramento", x0 + 0.2, pad_y + 1.0, z0 - 0.5, x0 + 1.3, pad_y + 2.0,
        z0 - 0.06, BRASS)

    px0, px1 = x0 + 7.4, x1 - 0.6
    box(out, "painel de baixa", px0, pad_y + 1.4, z0 - 0.32, px1, body_top - 2.0, z0 + 0.02, CABINET_DARK)
    box(out, "recesso de baixa", px0 + 0.35, pad_y + 2.2, z0 - 0.24, px1 - 0.35, body_top - 3.0, z0 + 0.04,
        PANEL)
    lv_y = 11.6
    for i in range(4):
        x = px0 + 1.2 + i * 1.35
        lv_bushing(out, x, lv_y, z0 - 0.24)
        pole.secondary(x, lv_y, z0 - 2.24)
    on, off = [], []
    status_leds(out, on, off, px0 + 0.7, body_top - 4.2, z0 - 0.32)
    pole.lamp_section = 1
    pole.overlays = [("pad_transformer_on", 1, "energized", "true", on),
                     ("pad_transformer_off", 1, "energized", "false", off)]
    pole.icon_extra = on

    box(out, "tampa", x0 - 0.55, body_top, z0 - 0.55, x1 + 0.55, lid_top, z1 + 0.55, CABINET_TOP,
        CABINET_TOP, CABINET_DARK)
    for x in (x0 + 1.4, x0 + 5.6, x0 + 9.8, x1 - 1.4):
        box(out, "parafuso da tampa", x - 0.2, lid_top, z0 - 0.3, x + 0.2, lid_top + 0.35, z0 + 0.1, STEEL_DARK)

    bushing_z, arrester_z = 6.0, 12.2
    for x in (3.6, 8.0, 12.4):
        bushing_top = hv_bushing(out, x, bushing_z, lid_top)
        arrester_top = surge_arrester(out, x, arrester_z, lid_top)
        box(out, "descida do para-raios", x - 0.12, arrester_top, arrester_z - 0.12, x + 0.12,
            bushing_top - 0.35, arrester_z + 0.12, ALUMINUM)
        box(out, "ligacao do para-raios", x - 0.12, bushing_top - 0.6, bushing_z - 0.12, x + 0.12,
            bushing_top - 0.35, arrester_z + 0.12, ALUMINUM)
        pole.phase(x, lid_top + 5.35, bushing_z)

    pole.hit(x0 - 2.4, 0.0, z0 - 2.0, x1 + 2.4, pad_y, z1 + 2.0)
    pole.hit(x0 - 0.6, pad_y, z0 - 0.7, x1 + 0.6, lid_top, z1 + 0.6)
    pole.hit(2.4, lid_top, 4.6, 13.6, lid_top + 5.9, 13.4)
    return pole


def build_substation():
    pole = Pole("substation_transformer", "substation", "SUBSTATION", 3, GREY, (0.0, 50.0))
    out = pole.base
    x0, x1, z0, z1 = 1.0, 15.0, 5.0, 16.6
    pad_y, body_top = 2.0, 30.0
    lid_top = body_top + 1.6

    box(out, "base de concreto", -2.0, 0.0, z0 - 2.4, 18.0, 1.2, z1 + 2.4, COLLAR)
    box(out, "base de concreto topo", -1.0, 1.2, z0 - 1.4, 17.0, pad_y, z1 + 1.4, COLLAR)
    box(out, "tanque", x0, pad_y, z0, x1, body_top, z1, GREY, GREY_TOP, GREY_DARK)
    for rx0, rx1 in ((x0 - 1.7, x0 + 0.02), (x1 - 0.02, x1 + 1.7)):
        radiator_bank(out, rx0, rx1, (z0 + z1) / 2, pad_y + 1.6, body_top - 1.6, fins=8, pitch=1.2,
                      tex=GREY, dark=GREY_DARK)
    ribbed_panel(out, x0 + 0.8, x1 - 0.8, pad_y + 1.0, body_top - 1.0, z1, 0.3, GREY_DARK, 5)
    box(out, "tampa", x0 - 0.6, body_top, z0 - 0.6, x1 + 0.6, lid_top, z1 + 0.6, GREY_TOP, GREY_TOP,
        GREY_DARK)
    for x in (x0 + 1.4, x0 + 5.6, x0 + 9.8, x1 - 1.4):
        box(out, "parafuso da tampa", x - 0.2, lid_top, z0 - 0.35, x + 0.2, lid_top + 0.4, z0 + 0.05,
            STEEL_DARK)

    for x in (3.6, 8.0, 12.4):
        tall_hv_bushing(out, x, z0 + 2.2, lid_top, height=13.0)
        pole.line(x, lid_top + 13.0, z0 + 2.2)

    mv_y = 24.0
    for x in (3.0, 6.2, 9.4):
        mv_terminal(out, x, mv_y, z0 - 0.02)
        pole.phase(x, mv_y, z0 - 4.4)

    cx0, cx1 = x1 - 6.6, x1 - 0.4
    box(out, "cubiculo de comando", cx0, pad_y + 0.4, z0 - 2.6, cx1, pad_y + 14.4, z0 + 0.02, GREY_DARK,
        GREY_TOP, GREY_DARK)
    cabinet_door(out, cx0 + 0.6, cx1 - 0.6, pad_y + 1.4, pad_y + 13.0, z0 - 2.6, GREY, GREY_DARK)
    on, off = [], []
    status_leds(out, on, off, cx0 + 0.5, pad_y + 13.0, z0 - 2.92)
    pole.lamp_section = 0
    pole.overlays = [("substation_on", 0, "energized", "true", on),
                     ("substation_off", 0, "energized", "false", off)]
    pole.icon_extra = on

    box(out, "placa de perigo", x0 + 0.9, 17.0, z0 - 0.62, x0 + 3.1, 19.2, z0 - 0.46, STEEL,
        sides={"north": DANGER}, fit={"north": (0, 0, 16, 16)})
    box(out, "placa de dados", x0 + 0.9, 5.6, z0 - 0.62, x0 + 2.5, 7.2, z0 - 0.46, ALUMINUM,
        sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    box(out, "conector de aterramento", x0 + 0.2, pad_y + 0.8, z0 - 0.5, x0 + 1.3, pad_y + 1.8,
        z0 - 0.06, BRASS)

    pole.hit(-2.0, 0.0, z0 - 2.4, 18.0, pad_y, z1 + 2.4)
    pole.hit(x0 - 1.8, pad_y, z0 - 2.8, x1 + 1.8, lid_top, z1 + 0.7)
    pole.hit(2.0, lid_top, z0 + 0.4, 14.0, lid_top + 13.6, z0 + 4.0)
    pole.hit(1.6, mv_y - 1.1, z0 - 5.0, 10.8, mv_y + 1.1, z0)
    return pole




MODULE_TILT = -22.5
MODULE_THICK = 0.64


def tilted_y(y, z, y_pivot, z_pivot, angle=MODULE_TILT):
    radians = math.radians(angle)
    return y_pivot + (y - y_pivot) * math.cos(radians) - (z - z_pivot) * math.sin(radians)


def solar_module(out, x0, x1, z0, z1, y, rot=None, frame=SOLAR_FRAME):
    top = y + MODULE_THICK
    rim = 0.45
    box(out, "moldura", x0, y, z0, x1, top, z0 + rim, frame, rot=rot)
    box(out, "moldura", x0, y, z1 - rim, x1, top, z1, frame, rot=rot)
    box(out, "moldura", x0, y, z0 + rim, x0 + rim, top, z1 - rim, frame, rot=rot)
    box(out, "moldura", x1 - rim, y, z0 + rim, x1, top, z1 - rim, frame, rot=rot)
    box(out, "laminado", x0 + rim - 0.02, y + 0.13, z0 + rim - 0.02, x1 - rim + 0.02, top - 0.05,
        z1 - rim + 0.02, SOLAR_GLASS, up=SOLAR_GLASS, down=SOLAR_BACK,
        sides={"north": SOLAR_GLASS_EDGE, "south": SOLAR_GLASS_EDGE,
               "east": SOLAR_GLASS_EDGE, "west": SOLAR_GLASS_EDGE}, rot=rot)


def junction_box(out, x, z, y, rot=None):
    box(out, "caixa de juncao", x - 1.2, y - 1.0, z - 0.8, x + 1.2, y, z + 0.8, JUNCTION, rot=rot)
    for side in (-1, 1):
        box(out, "cabo de saida", x + side * 0.9 - 0.16, y - 0.85, z + 0.8, x + side * 0.9 + 0.16,
            y - 0.53, z + 2.4, JUNCTION, rot=rot)


def build_solar_panel():
    pole = Pole("solar_panel", "solar_panel", "SOLAR_PANEL", 1, SOLAR_FRAME, (0.0, 18.0))
    out = pole.base
    y_mid = 8.6
    rot = rotation("x", MODULE_TILT, (C, y_mid, C))
    solar_module(out, 1.0, 15.0, 1.2, 14.8, y_mid, rot)
    junction_box(out, C, C + 3.4, y_mid, rot)
    front_leg = tilted_y(y_mid - 0.9, 3.0, y_mid, C) + 0.25
    rear_leg = tilted_y(y_mid - 0.9, 13.0, y_mid, C) + 0.25
    for x in (2.6, 13.4):
        box(out, "trilho", x - 0.45, y_mid - 0.9, 2.0, x + 0.45, y_mid - 0.05, 14.0, SOLAR_FRAME, rot=rot)
    for x in (2.6, 13.4):
        box(out, "pe dianteiro", x - 0.5, 0.6, 2.4, x + 0.5, front_leg, 3.6, SOLAR_FRAME)
        box(out, "pe traseiro", x - 0.5, 0.6, 12.4, x + 0.5, rear_leg, 13.6, SOLAR_FRAME)
        box(out, "sapata", x - 0.9, 0.0, 2.0, x + 0.9, 0.6, 4.0, STEEL_DARK)
        box(out, "sapata", x - 0.9, 0.0, 12.0, x + 0.9, 0.6, 14.0, STEEL_DARK)
    for z in (4.6, 11.4):
        box(out, "travessa", 2.2, 1.5, z - 0.45, 13.8, 2.3, z + 0.45, SOLAR_FRAME)
    box(out, "quadro de comando", 4.6, 2.6, 2.0, 9.6, 6.4, 3.4, JUNCTION)
    on, off = [], []
    status_leds(out, on, off, 5.2, 4.4, 1.98, count=2)
    pole.lamp_section = 0
    pole.overlays = [("solar_panel_on", 0, "energized", "true", on),
                     ("solar_panel_off", 0, "energized", "false", off)]
    pole.icon_extra = on
    pole.secondary(5.6, 3.2, 1.8)
    pole.secondary(8.6, 3.2, 1.8)
    pole.hit(0.6, 0.0, 1.0, 15.4, rear_leg + 1.6, 15.0)
    return pole


def build_solar_array():
    pole = Pole("solar_array", "solar_array", "SOLAR_ARRAY", 2, SOLAR_FRAME, (0.0, 34.0))
    out = pole.base
    y_mid = 20.0
    rot = rotation("x", MODULE_TILT, (C, y_mid, C))
    for index in range(3):
        x0 = -5.6 + index * 9.1
        solar_module(out, x0, x0 + 8.6, 0.8, 15.2, y_mid, rot)
        junction_box(out, x0 + 4.3, C + 3.6, y_mid, rot)
    for z in (4.2, 11.8):
        box(out, "terca", -6.2, y_mid - 1.0, z - 0.5, 22.2, y_mid - 0.06, z + 0.5, STEEL, rot=rot)
    front_post = tilted_y(y_mid - 1.0, 4.0, y_mid, C) + 0.3
    rear_post = tilted_y(y_mid - 1.0, 12.0, y_mid, C) + 0.3
    for x in (-2.4, 18.4):
        box(out, "montante dianteiro", x - 0.7, 1.0, 2.6, x + 0.7, front_post, 4.0, STEEL)
        box(out, "montante traseiro", x - 0.7, 1.0, 12.0, x + 0.7, rear_post, 13.4, STEEL)
        brace_yz(out, x - 0.35, x + 0.35, 6.0, 3.6, front_post - 1.4, 11.4, STEEL, 0.6)
        box(out, "sapata", x - 1.6, 0.0, 1.8, x + 1.6, 1.2, 4.8, COLLAR)
        box(out, "sapata", x - 1.6, 0.0, 11.2, x + 1.6, 1.2, 14.2, COLLAR)
    box(out, "caixa de juncao geral", 6.0, 6.0, 12.0, 11.0, 12.0, 14.2, JUNCTION)
    on, off = [], []
    status_leds(out, on, off, 6.6, 7.2, 11.9, count=3)
    pole.lamp_section = 0
    pole.overlays = [("solar_array_on", 0, "energized", "true", on),
                     ("solar_array_off", 0, "energized", "false", off)]
    pole.icon_extra = on
    for index, x in enumerate((6.8, 8.0, 9.2, 10.4)):
        pole.secondary(x, 6.6, 11.6, neutral=index == 0)
    pole.hit(-6.4, 0.0, 0.6, 22.4, 26.0, 15.4)
    pole.hit(-3.2, 0.0, 1.8, 20.0, 20.6, 14.2)
    return pole


def build_solar_tracker():
    pole = Pole("solar_tracker", "solar_tracker", "SOLAR_TRACKER", 3, STEEL, (24.0, 50.0))
    out = pole.base
    bearing_y = 38.0
    box(out, "sapata", C - 3.2, 0.0, C - 3.2, C + 3.2, 1.6, C + 3.2, COLLAR)
    box(out, "pedestal", C - 1.9, 1.6, C - 1.9, C + 1.9, 6.0, C + 1.9, STEEL_DARK)
    disc(out, "mastro", C, C, 1.55, 6.0, bearing_y - 2.2, STEEL)
    box(out, "mancal", C - 2.3, bearing_y - 2.2, C - 2.0, C + 2.3, bearing_y + 2.0, C + 2.0, STEEL_DARK)
    disc_z(out, "rolamento", C, bearing_y, 1.5, C - 2.6, C + 2.6, STEEL)
    box(out, "atuador", C + 2.0, bearing_y - 6.4, C - 0.7, C + 3.0, bearing_y - 1.0, C + 0.7, STEEL_DARK)
    box(out, "haste do atuador", C + 2.3, bearing_y - 1.4, C - 0.35, C + 2.7, bearing_y + 1.2,
        C + 0.35, STEEL)
    box(out, "quadro de comando", C - 3.0, 12.0, C - 4.6, C + 3.0, 20.0, C - 2.6, JUNCTION)
    on, off = [], []
    status_leds(out, on, off, C - 2.4, 18.2, C - 4.7, count=3)
    pole.lamp_section = 1
    pole.overlays = [("solar_tracker_on", 1, "energized", "true", on),
                     ("solar_tracker_off", 1, "energized", "false", off)]
    pole.icon_extra = on

    row = []
    disc_z(row, "tubo de torque", C, C, 1.25, C - 13.0, C + 13.0, STEEL)
    for side in (-1, 1):
        z0 = C + side * 1.4
        z1 = C + side * 11.8
        solar_module(row, C - 7.0, C + 7.0, min(z0, z1), max(z0, z1), C - 0.6)
        junction_box(row, C - 4.4, (z0 + z1) / 2, C - 0.6)
    for z in (C - 8.6, C - 3.6, C + 3.6, C + 8.6):
        box(row, "grampo", C - 5.4, C - 2.2, z - 0.5, C + 5.4, C - 0.62, z + 0.5, STEEL_DARK)
    pole.extras = [("solar_tracker_row", row)]

    pole.secondary(C - 2.0, 14.4, C - 4.9)
    pole.secondary(C + 2.0, 14.4, C - 4.9)
    pole.hit(C - 3.2, 0.0, C - 4.8, C + 3.2, bearing_y + 2.4, C + 3.2)
    pole.hit(C - 14.0, bearing_y - 2.0, C - 8.0, C + 14.0, bearing_y + 2.0, C + 8.0)
    return pole


def build_wind_turbine():
    pole = Pole("wind_turbine", "wind_turbine", "WIND_TURBINE", 14, TOWER, (196.0, 236.0))
    out, top = pole.base, pole.top

    def r_at(y):
        return 3.1 - 1.5 * y / top

    def center(_):
        return C, C

    def rs(y):
        return r_at(step_mid(y, top))

    round_shaft(out, top, r_at, center, TOWER, TOWER_DARK, TOWER, grime_top=6.0)
    front = round_front(r_at, center, C, top)
    collar_round(out, C, C, rs(0) + 0.9)
    disc(out, "flange da base", C, C, rs(0) + 1.4, 0.95, 1.7, TOWER_DARK)
    box(out, "porta da torre", C - 1.6, 2.0, front(6.0) - 0.3, C + 1.6, 9.0, front(6.0) + 0.5,
        TOWER_DARK)
    box(out, "macaneta", C + 1.0, 5.0, front(6.0) - 0.55, C + 1.4, 6.0, front(6.0) - 0.2, STEEL_DARK)

    box(out, "caixa de bornes", C - 3.4, 14.0, front(17.0) - 2.6, C + 3.4, 24.0, front(17.0) + 0.2,
        NACELLE_DARK)
    for index, x in enumerate((C - 2.0, C, C + 2.0)):
        mv_terminal_top = 24.0
        disc(out, "bucha", x, front(17.0) - 1.3, 0.62, mv_terminal_top, mv_terminal_top + 2.2,
             PORCELAIN_BROWN)
        box(out, "conector", x - 0.32, mv_terminal_top + 2.2, front(17.0) - 1.62, x + 0.32,
            mv_terminal_top + 2.9, front(17.0) - 0.98, BRASS)
        pole.phase(x, mv_terminal_top + 2.55, front(17.0) - 1.3)
    on, off = [], []
    status_leds(out, on, off, C - 2.6, 20.4, front(20.0) - 2.7, count=3)
    pole.hit(C - 3.6, 14.0, front(17.0) - 3.0, C + 3.6, 27.0, front(17.0) + 0.4)

    nac_y = top + 3.0
    box(out, "nacele", C - 3.2, nac_y - 3.0, C - 6.0, C + 3.2, nac_y + 3.0, C + 9.0, NACELLE,
        up=NACELLE, down=NACELLE_DARK)
    box(out, "carenagem traseira", C - 2.6, nac_y - 2.4, C + 9.0, C + 2.6, nac_y + 2.4, C + 11.0,
        NACELLE)
    box(out, "anemometro", C - 0.3, nac_y + 3.0, C + 7.4, C + 0.3, nac_y + 5.2, C + 8.0, NACELLE_DARK)
    box(out, "concha do anemometro", C - 1.6, nac_y + 5.0, C + 7.0, C + 1.6, nac_y + 5.6, C + 8.4,
        NACELLE_DARK)
    disc(out, "coroa de giro", C, C, 2.6, top, nac_y - 3.0, NACELLE_DARK)
    disc_z(out, "cubo", C, nac_y, 2.4, C - 11.6, C - 8.4, NACELLE)
    disc_z(out, "spinner", C, nac_y, 1.7, C - 12.9, C - 11.6, NACELLE)
    box(out, "carenagem do eixo", C - 2.2, nac_y - 2.2, C - 8.4, C + 2.2, nac_y + 2.2, C - 5.6, NACELLE_DARK)
    pole.hit(C - 3.4, nac_y - 3.2, C - 13.1, C + 3.4, nac_y + 5.8, C + 11.2)

    lens = (C - 0.7, nac_y + 3.0, C + 9.8, C + 0.7, nac_y + 4.2, C + 11.0)
    beacon_on, beacon_off = [], []
    box(beacon_on, "lente do balizador", *lens, LED_RED_ON, glow=True)
    box(beacon_off, "lente do balizador", *lens, LED_RED_OFF)
    pole.lamp_section = min(math.floor((nac_y + 3.6) / 16.0), pole.sections - 1)
    pole.overlays = [("wind_turbine_beacon_on", pole.lamp_section, "lit", "true", beacon_on),
                     ("wind_turbine_beacon_off", pole.lamp_section, "lit", "false", beacon_off),
                     ("wind_turbine_status_on", 1, "energized", "true", on),
                     ("wind_turbine_status_off", 1, "energized", "false", off)]
    pole.icon_extra = beacon_on

    blade = []
    _blade(blade)
    hub = []
    disc_z(hub, "cubo do rotor", C, C, 2.0, C - 1.3, C + 1.3, NACELLE)
    pole.extras = [("wind_turbine_blade", blade), ("wind_turbine_hub", hub)]

    for s in range(pole.sections):
        y = s * 16.0
        r = r_at(y) * 0.96
        pole.hit(C - r, y, C - r, C + r, y + 16.0, C + r)
    pole.hit(C - rs(0) - 1.6, 0, C - rs(0) - 1.6, C + rs(0) + 1.6, 1.7, C + rs(0) + 1.6)
    return pole


def _blade(out):
    def place(name, r0, r1, half0, half1, thick, tex):
        steps = 6
        for i in range(steps):
            a = r0 + (r1 - r0) * i / steps
            b = r0 + (r1 - r0) * (i + 1) / steps
            half = half0 + (half1 - half0) * (i + 0.5) / steps
            box(out, name, C - half, C + a, C - thick, C + half, C + b, C + thick, tex)

    place("pa", 1.4, 5.0, 0.95, 1.55, 0.62, BLADE)
    place("pa", 5.0, 18.0, 1.55, 0.95, 0.42, BLADE)
    place("ponta da pa", 18.0, 23.0, 0.95, 0.28, 0.28, BLADE_TIP)
    box(out, "raiz da pa", C - 1.1, C + 0.2, C - 0.78, C + 1.1, C + 2.6, C + 0.78, NACELLE_DARK)


def build_diesel_generator():
    pole = Pole("diesel_generator", "diesel_generator", "DIESEL_GENERATOR", 2, GENSET, (0.0, 34.0))
    out = pole.base
    x0, x1, z0, z1 = -4.4, 20.4, 1.0, 15.0
    skid_top, body_top = 5.6, 27.0

    box(out, "tanque de combustivel", x0, 0.8, z0, x1, skid_top, z1, FUEL_TANK)
    for x in (x0 + 1.2, x1 - 1.2):
        box(out, "patim", x - 1.4, 0.0, z0 - 0.4, x + 1.4, 1.0, z1 + 0.4, STEEL_DARK)
    box(out, "bocal", 2.0, skid_top, z0 - 0.1, 4.4, skid_top + 1.0, z0 + 2.3, STEEL_DARK)
    box(out, "tampa do bocal", 2.4, skid_top + 1.0, z0 + 0.3, 4.0, skid_top + 1.5, z0 + 1.9, BRASS)

    hole = (x0 + 1.1, skid_top + 0.9, 6.7, 20.9, z0 + 7.2)
    box(out, "carenagem", hole[2], skid_top, z0 + 0.4, x1 - 0.6, body_top, z1 - 0.4, GENSET,
        up=GENSET, down=GENSET_DARK)
    box(out, "carenagem", x0 + 0.6, hole[3], z0 + 0.4, hole[2], body_top, z1 - 0.4, GENSET,
        up=GENSET, down=GENSET_DARK)
    box(out, "carenagem", x0 + 0.6, skid_top, z0 + 0.4, hole[2], hole[1], z1 - 0.4, GENSET,
        up=GENSET, down=GENSET_DARK)
    box(out, "carenagem", x0 + 0.6, hole[1], hole[4], hole[2], hole[3], z1 - 0.4, GENSET,
        up=GENSET, down=GENSET_DARK)
    box(out, "carenagem", x0 + 0.6, hole[1], z0 + 0.4, hole[0], hole[3], hole[4], GENSET,
        up=GENSET, down=GENSET_DARK)
    box(out, "tampa", x0 + 0.2, body_top, z0, x1 - 0.2, body_top + 1.2, z1, GENSET_DARK)
    for x in (x0 + 2.0, x1 - 2.0):
        box(out, "olhal de icamento", x - 0.8, body_top + 1.2, C - 0.7, x + 0.8, body_top + 2.4,
            C + 0.7, STEEL_DARK)

    box(out, "grelha do radiador", x0 + 0.2, skid_top + 2.0, z0 + 2.2, x0 + 0.65, body_top - 2.0,
        z1 - 2.2, GRILLE)
    for y in (skid_top + 1.4, body_top - 1.4):
        box(out, "moldura da grelha", x0 + 0.1, y - 0.7, z0 + 1.6, x0 + 0.7, y + 0.7, z1 - 1.6,
            GENSET_DARK)

    box(out, "silencioso", x1 - 7.0, body_top + 1.2, C - 1.6, x1 - 2.6, body_top + 4.2, C + 1.6,
        EXHAUST)
    disc(out, "chamine", x1 - 4.8, C, 1.0, body_top + 4.2, body_top + 9.6, EXHAUST)
    disc(out, "chapeu da chamine", x1 - 4.8, C, 1.5, body_top + 9.6, body_top + 10.2, EXHAUST)

    bx0, bx1 = x0 + 1.6, 6.2
    by0, by1 = skid_top + 1.4, 20.4
    bz1 = z0 + 6.8
    for face in ((bx0 - 0.5, by0 - 0.5, bx0, by1 + 0.5), (bx1, by0 - 0.5, bx1 + 0.5, by1 + 0.5),
                 (bx0 - 0.5, by1, bx1 + 0.5, by1 + 0.5), (bx0 - 0.5, by0 - 0.5, bx1 + 0.5, by0)):
        box(out, "batente do paiol", face[0], face[1], z0 + 0.3, face[2], face[3], bz1, GENSET_DARK)
    box(out, "forro do paiol", bx0, by1 - 0.35, z0 + 0.35, bx1, by1, bz1, BUNKER)
    box(out, "piso do paiol", bx0, by0, z0 + 0.35, bx1, by0 + 0.35, bz1, BUNKER)
    box(out, "fundo do paiol", bx0, by0, bz1 - 0.4, bx1, by1, bz1, BUNKER)
    for wall in ((bx0, bx0 + 0.35), (bx1 - 0.35, bx1)):
        box(out, "lateral do paiol", wall[0], by0, z0 + 0.35, wall[1], by1, bz1, BUNKER)
    pole.bunker = (bx0 + 0.35, by0 + 0.35, z0 + 0.4, bx1 - 0.35, by1 - 0.4, bz1 - 0.4)
    for y in (skid_top + 2.6, body_top - 2.8):
        box(out, "dobradica", x0 + 1.2, y - 0.8, z0 - 0.35, x0 + 2.0, y + 0.8, z0 + 0.2, STEEL_DARK)
    box(out, "porta", 7.2, skid_top + 1.2, z0 - 0.1, x1 - 6.6, body_top - 1.4, z0 + 0.5, GENSET,
        up=GENSET_DARK, down=GENSET_DARK)
    box(out, "puxador", x1 - 8.2, 14.0, z0 - 0.5, x1 - 7.2, 17.2, z0 + 0.1, STEEL_DARK)
    for y in (skid_top + 2.6, body_top - 2.8):
        box(out, "dobradica", 7.0, y - 0.8, z0 - 0.35, 7.8, y + 0.8, z0 + 0.2, STEEL_DARK)

    leaf = []
    box(leaf, "porta do paiol", x0 + 1.4, skid_top + 1.2, z0 - 0.1, 6.4, body_top - 1.4, z0 + 0.5,
        GENSET, up=GENSET_DARK, down=GENSET_DARK)
    box(leaf, "puxador do paiol", 4.8, 14.0, z0 - 0.5, 5.8, 17.2, z0 + 0.1, STEEL_DARK)
    box(leaf, "veneziana da porta", x0 + 2.4, 19.0, z0 - 0.22, 5.4, 23.4, z0 - 0.06, GRILLE)
    pile = []
    box(pile, "carvao", 0.0, 0.0, 0.0, 16.0, 13.0, 16.0, COAL_PILE, up=COAL_PILE, down=COAL_PILE)
    box(pile, "monte de carvao", 2.4, 13.0, 2.4, 13.6, 16.0, 13.6, COAL_PILE, up=COAL_PILE,
        down=COAL_PILE)
    pole.extras = [("diesel_door", leaf), ("diesel_coal", pile)]
    px0, px1 = 15.4, x1 - 0.6
    box(out, "painel de comando", px0, 12.6, z0 - 0.5, px1, body_top - 1.6, z0 + 0.4, GENSET_DARK)
    box(out, "visor", px0 + 0.6, 19.0, z0 - 0.62, px1 - 0.6, 23.4, z0 - 0.42, PANEL)
    box(out, "chave de partida", 16.4, 13.6, z0 - 0.9, 17.6, 15.2, z0 - 0.4, STEEL_DARK)
    on, off = [], []
    status_leds(out, on, off, 16.2, 17.6, z0 - 0.52, count=2)
    pole.lamp_section = 1
    pole.overlays = [("diesel_generator_on", 1, "energized", "true", on),
                     ("diesel_generator_off", 1, "energized", "false", off)]
    pole.icon_extra = on

    box(out, "placa de dados", 8.0, 7.6, z0 - 0.5, 9.6, 9.2, z0 - 0.42, ALUMINUM,
        sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    box(out, "placa de perigo", 10.4, 7.6, z0 - 0.5, 12.6, 9.8, z0 - 0.42, STEEL,
        sides={"north": DANGER}, fit={"north": (0, 0, 16, 16)})

    for index in range(4):
        x = x1 - 5.4 + index * 1.35
        lv_bushing(out, x, 10.4, z0 - 0.1)
        pole.secondary(x, 10.4, z0 - 2.1, neutral=index == 0)

    pole.door = (x0 + 1.0, skid_top + 0.8, 6.8, body_top - 1.0)
    pole.hit(x0 - 1.4, 0.0, z0 - 0.6, x1 + 1.4, skid_top, z1 + 0.6)
    pole.hit(x0 + 0.2, skid_top, z0 - 1.0, x1 - 0.2, body_top + 2.4, z1)
    pole.hit(x1 - 7.2, body_top, C - 1.8, x1 - 2.4, body_top + 10.2, C + 1.8)
    return pole




def build_service_entrance():
    pole = Pole("service_entrance", "service_entrance", "SERVICE_ENTRANCE", 3, GREY, (6.0, 44.0))
    out = pole.base
    px0, px1, pz0, pz1 = 5.8, 10.2, 11.2, 15.6

    box(out, "poste", px0, 0.0, pz0, px1, 46.0, pz1, CONCRETE, CONCRETE_LIGHT, CONCRETE)
    collar_rect(out, px0, px1, pz0, pz1)
    box(out, "tampa do poste", px0 - 0.3, 46.0, pz0 - 0.3, px1 + 0.3, 46.8, pz1 + 0.3, CONCRETE_LIGHT)

    mx0, mx1, my0, my1 = 1.4, 14.6, 21.0, 35.4
    mz0, mz1 = 2.4, pz0 + 0.4
    box(out, "caixa de medicao", mx0, my0, mz0 + 0.5, mx1, my1, mz1, GREY, GREY_TOP, GREY_DARK)
    for lo, hi in ((mx0, mx0 + 0.7), (mx1 - 0.7, mx1)):
        box(out, "moldura", lo, my0 - 0.3, mz0, hi, my1 + 0.3, mz0 + 0.6, GREY_DARK)
    for lo, hi in ((my0 - 0.3, my0 + 0.4), (my1 - 0.4, my1 + 0.3)):
        box(out, "moldura", mx0 + 0.7, lo, mz0, mx1 - 0.7, hi, mz0 + 0.6, GREY_DARK)
    box(out, "visor", mx0 + 0.7, my0 + 0.4, mz0 + 0.18, mx1 - 0.7, my1 - 0.4, mz0 + 0.62, PANEL)
    box(out, "chapeu da caixa", mx0 - 0.5, my1, mz0 - 0.6, mx1 + 0.5, my1 + 1.1, mz1, GREY_TOP)
    box(out, "lacre", mx1 - 2.6, my0 - 1.0, mz0 + 0.1, mx1 - 1.4, my0 + 0.2, mz0 + 0.9, BRASS)
    box(out, "placa de dados", mx0 + 1.0, my0 + 0.6, mz0 + 0.12, mx0 + 2.6, my0 + 2.2, mz0 + 0.2,
        ALUMINUM, sides={"north": NAMEPLATE}, fit={"north": (0, 0, 16, 16)})
    pole.display = (mx0 + 1.4, my0 + 3.0, mz0 + 0.14, mx1 - 1.4, my1 - 1.6)

    bx0, bx1, by0, by1 = 3.4, 12.6, 7.0, 18.0
    bz0, bz1 = 3.6, pz0 + 0.4
    box(out, "caixa do disjuntor", bx0, by0, bz0 + 0.6, bx1, by1, bz1, GREY, GREY_TOP, GREY_DARK)
    for lo, hi in ((bx0, bx0 + 0.8), (bx1 - 0.8, bx1)):
        box(out, "batente", lo, by0, bz0, hi, by1, bz0 + 0.7, GREY_DARK)
    for lo, hi in ((by0, by0 + 0.8), (by1 - 0.8, by1)):
        box(out, "batente", bx0 + 0.8, lo, bz0, bx1 - 0.8, hi, bz0 + 0.7, GREY_DARK)
    box(out, "fundo do disjuntor", bx0 + 0.8, by0 + 0.8, bz0 + 0.55, bx1 - 0.8, by1 - 0.8,
        bz0 + 0.95, INNER_PANEL)
    box(out, "trilho", bx0 + 1.2, 11.6, bz0 + 0.2, bx1 - 1.2, 12.8, bz0 + 0.6, RAIL_STEEL)
    box(out, "disjuntor geral", 6.2, 9.4, bz0 - 1.4, 9.8, 15.0, bz0 + 0.3, BREAKER_BODY,
        up=BREAKER_TOP_TEX, down=BREAKER_TOP_TEX)
    on, off = [], []
    status_leds(out, on, off, 3.9, 19.6, bz0 - 0.3, count=2)
    pole.lamp_section = 1
    pole.overlays = [("service_entrance_on", 1, "energized", "true", on),
                     ("service_entrance_off", 1, "energized", "false", off)]
    pole.icon_extra = on

    tz = bz0 - 1.4
    ty = 12.9
    pole.lever = (8.0, ty, tz + 0.8)
    lever = []
    box(lever, "haste da alavanca", 7.45, ty - 0.55, tz + 0.4, 8.55, ty + 0.55, tz + 1.6,
        BREAKER_BODY)
    box(lever, "alavanca", 7.15, ty - 0.8, tz - 1.6, 8.85, ty + 0.8, tz + 0.7, LEVER_RED)
    box(lever, "ponta da alavanca", 6.95, ty - 1.0, tz - 1.95, 9.05, ty + 1.0, tz - 1.3, LEVER_RED)
    box(lever, "estria da alavanca", 7.3, ty + 0.8, tz - 1.5, 8.7, ty + 0.95, tz - 0.2,
        BREAKER_BODY)
    pole.extras = [("service_entrance_lever", lever)]

    riser_x = C
    box(out, "eletroduto de entrada", riser_x - 0.9, my1 + 1.1, 5.4, riser_x + 0.9, 49.0, 7.2, PVC)
    box(out, "curva", riser_x - 0.9, 49.0, 4.0, riser_x + 0.9, 50.8, 7.2, PVC)
    box(out, "bengala", riser_x - 0.9, 50.0, 2.2, riser_x + 0.9, 51.8, 4.6, PVC)
    box(out, "bucha", riser_x - 1.15, 51.4, 1.6, riser_x + 1.15, 52.2, 3.0, PVC)
    box(out, "armacao de entrada", riser_x - 3.6, 52.2, 1.8, riser_x + 3.6, 53.0, 2.8, STEEL)
    for index, x in enumerate((riser_x - 2.6, riser_x, riser_x + 2.6)):
        spool(out, x, 2.3, 53.0)
        pole.secondary(x, 53.6, 2.3, neutral=index == 0)
    pole.hit(riser_x - 3.8, 49.0, 1.4, riser_x + 3.8, 54.4, 7.4)

    box(out, "eletroduto de aterramento", px0 - 1.4, 0.0, pz0 + 0.6, px0 - 0.2, 20.0, pz0 + 1.8, PVC)
    box(out, "haste de aterramento", px0 - 1.1, 0.0, pz0 + 0.9, px0 - 0.5, 1.4, pz0 + 1.5, COPPER)

    pole.hit(px0 - 1.6, 0.0, pz0 - 0.2, px1 + 0.4, 46.8, pz1 + 0.4)
    pole.hit(mx0 - 0.6, my0 - 1.2, mz0 - 0.7, mx1 + 0.6, my1 + 1.2, mz1)
    pole.hit(bx0 - 0.2, by0 - 0.2, bz0 - 2.4, bx1 + 0.2, by1 + 0.2, bz1)
    pole.hit(riser_x - 1.2, my1, 2.0, riser_x + 1.2, 52.4, 7.4)
    return pole


def grid_cuts(lo, hi):
    cuts = [lo]
    k = math.floor(lo / 16.0) + 1
    while k * 16.0 < hi - 1e-6:
        if k * 16.0 > lo + 1e-6:
            cuts.append(k * 16.0)
        k += 1
    cuts.append(hi)
    return list(zip(cuts, cuts[1:]))


def split(element):
    lo, hi = element["from"], element["to"]
    pieces = []
    for xa, xb in grid_cuts(lo[0], hi[0]):
        for ya, yb in grid_cuts(lo[1], hi[1]):
            for za, zb in grid_cuts(lo[2], hi[2]):
                if min(xb - xa, yb - ya, zb - za) < 0.004:
                    continue
                piece = dict(element)
                piece["from"], piece["to"] = (xa, ya, za), (xb, yb, zb)
                piece["cell"] = (math.floor((xa + xb) / 32.0), math.floor((ya + yb) / 32.0),
                                 math.floor((za + zb) / 32.0))
                pieces.append(piece)
    if element["fit"] and len(pieces) != 1:
        raise SystemExit(f"decalque atravessa a grade: {element['name']}")
    return pieces


def center_of(piece):
    lo, hi = piece["from"], piece["to"]
    return rotate_point(((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2), piece["rot"])


def uv_for(face, lo, hi, cell):
    ox, oy, oz = cell[0] * 16.0, cell[1] * 16.0, cell[2] * 16.0
    x0, y0, z0 = lo[0] - ox, lo[1] - oy, lo[2] - oz
    x1, y1, z1 = hi[0] - ox, hi[1] - oy, hi[2] - oz
    return {
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0),
        "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0),
        "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
        "up": (x0, z0, x1, z1),
        "down": (x0, 16 - z1, x1, 16 - z0),
    }[face]


def r4(value):
    return round(value, 4) + 0.0


def element_json(piece, transform, cull=False):
    lo, hi = piece["from"], piece["to"]
    faces = {}
    for face, tex in piece["faces"].items():
        uv = piece["fit"].get(face) or uv_for(face, lo, hi, piece["cell"])
        entry = {"uv": [r4(v) for v in uv], "texture": "#" + tex}
        if cull and face == "down" and lo[1] < 1e-6 and not piece["rot"]:
            entry["cullface"] = "down"
        faces[face] = entry
    element = {"name": piece["name"], "from": [r4(v) for v in transform(lo)], "to": [r4(v) for v in transform(hi)],
               "faces": faces}
    if piece["rot"]:
        rot = piece["rot"]
        element["rotation"] = {"origin": [r4(v) for v in transform(rot["origin"])], "axis": rot["axis"],
                               "angle": rot["angle"]}
    if piece["glow"]:
        element["shade"] = False
        element["forge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def validate(label, elements):
    for element in elements:
        lo, hi = element["from"], element["to"]
        for axis in range(3):
            if lo[axis] > hi[axis]:
                raise SystemExit(f"{label}: caixa invertida em {element['name']}")
            if lo[axis] < -16 or hi[axis] > 32:
                raise SystemExit(f"{label}: {element['name']} fora de -16..32 ({lo} {hi})")
        rot = element.get("rotation")
        if rot and rot["angle"] not in (-45, -22.5, 0, 22.5, 45):
            raise SystemExit(f"{label}: angulo invalido em {element['name']}")
        for face in element["faces"].values():
            if any(v < -1e-6 or v > 16 + 1e-6 for v in face["uv"]):
                raise SystemExit(f"{label}: UV fora de 0..16 em {element['name']}")


def write_json(path, payload, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        if compact:
            json.dump(payload, handle, separators=(",", ":"))
        else:
            json.dump(payload, handle, indent=2)
        handle.write("\n")


def model_payload(label, pieces, transform, particle, cull=False):
    elements = [element_json(piece, transform, cull) for piece in pieces]
    validate(label, elements)
    textures = {tex: f"ncat_minecraft:block/pole/{tex}" for tex in sorted({t for p in pieces for t in p["faces"].values()})}
    textures["particle"] = f"ncat_minecraft:block/pole/{particle}"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def section_model(pole, section):
    return f"ncat_minecraft:block/pole/{pole.short}_{section}"


def write_pole(pole):
    by_section = {s: [] for s in range(pole.sections)}
    for element in pole.base:
        for piece in split(element):
            section = min(max(math.floor(center_of(piece)[1] / 16.0), 0), pole.sections - 1)
            by_section[section].append(piece)
    counts = []
    for section, pieces in by_section.items():
        shift = 16.0 * section
        payload = model_payload(f"{pole.short}_{section}", pieces, lambda p, d=shift: (p[0], p[1] - d, p[2]),
                                pole.particle, cull=section == 0)
        write_json(os.path.join(MODELS, "block", "pole", f"{pole.short}_{section}.json"), payload, compact=True)
        counts.append(len(pieces))
    for name, elements in pole.extras:
        pieces = [piece for element in elements for piece in split(element)]
        payload = model_payload(name, pieces, lambda p: p, pole.particle)
        write_json(os.path.join(MODELS, "block", "pole", f"{name}.json"), payload, compact=True)
    for name, section, _, _, elements in pole.overlays:
        pieces = [piece for element in elements for piece in split(element)]
        for piece in pieces:
            owner = min(max(math.floor(center_of(piece)[1] / 16.0), 0), pole.sections - 1)
            if owner != section:
                raise SystemExit(f"{name}: sobreposicao fora da secao {section}")
        shift = 16.0 * section
        payload = model_payload(name, pieces, lambda p, d=shift: (p[0], p[1] - d, p[2]), pole.particle)
        write_json(os.path.join(MODELS, "block", "pole", f"{name}.json"), payload, compact=True)
    write_blockstate(pole)
    write_item(pole)
    write_loot(pole)
    print(f"{pole.key}: {pole.sections} secoes, {sum(counts)} elementos (max {max(counts)} por secao)")


def write_blockstate(pole):
    parts = []
    for facing, y in FACINGS:
        for section in range(pole.sections):
            parts.append({"when": {"facing": facing, "section": str(section)},
                          "apply": {"model": section_model(pole, section), "y": y}})
        for name, section, prop, value, _ in pole.overlays:
            parts.append({"when": {"facing": facing, "section": str(section), prop: value},
                          "apply": {"model": f"ncat_minecraft:block/pole/{name}", "y": y}})
    write_json(os.path.join(ASSETS, "blockstates", f"{pole.key}.json"), {"multipart": parts})


ICON_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.16, 0.16, 0.16]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.32, 0.32, 0.32]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.24, 0.24, 0.24]},
}


def write_item(pole):
    y_lo, y_hi = pole.icon
    pieces = []
    for element in pole.base + pole.icon_extra:
        for piece in split(element):
            if piece["rot"]:
                if y_lo <= center_of(piece)[1] <= y_hi:
                    pieces.append(piece)
                continue
            ya, yb = max(piece["from"][1], y_lo), min(piece["to"][1], y_hi)
            if yb - ya < 0.004:
                continue
            clipped = dict(piece)
            clipped["from"] = (piece["from"][0], ya, piece["from"][2])
            clipped["to"] = (piece["to"][0], yb, piece["to"][2])
            pieces.append(clipped)
    bounds = []
    for axis in range(3):
        values = [v for p in pieces for v in (p["from"][axis], p["to"][axis])]
        bounds.append((min(values), max(values)))
    scale = min(44.0 / (hi - lo) for lo, hi in bounds)
    mids = [(lo + hi) / 2 for lo, hi in bounds]

    def transform(p):
        return tuple((p[i] - mids[i]) * scale + 8.0 for i in range(3))

    payload = model_payload(f"item {pole.short}", pieces, transform, pole.particle)
    payload["display"] = ICON_DISPLAY
    write_json(os.path.join(MODELS, "item", f"{pole.key}.json"), payload, compact=True)


def write_loot(pole):
    block = f"ncat_minecraft:{pole.key}"
    loot = {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:item",
                "name": block,
                "conditions": [{"condition": "minecraft:block_state_property", "block": block,
                                "properties": {"section": "0"}}],
            }],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }
    write_json(os.path.join(DATA, "loot_tables", "blocks", f"{pole.key}.json"), loot)


def write_shapes_java(poles):
    lines = [
        "package com.netcattest.ncatminecraft.block;",
        "",
        "public final class UtilityPoleShapes {",
    ]
    for pole in poles:
        lines.append(f"    public static final int {pole.const}_SECTIONS = {pole.sections};")
        if pole.bunker is not None:
            lines.append("    public static final double[] " + pole.const + "_BUNKER = {"
                         + ", ".join(f"{v:.3f}" for v in pole.bunker) + "};")
        if pole.door is not None:
            lines.append("    public static final double[] " + pole.const + "_DOOR = {"
                         + ", ".join(f"{v:.3f}" for v in pole.door) + "};")
        if pole.display is not None:
            lines.append("    public static final double[] " + pole.const + "_DISPLAY = {"
                         + ", ".join(f"{v:.3f}" for v in pole.display) + "};")
        if pole.lever is not None:
            lines.append("    public static final double[] " + pole.const + "_LEVER = {"
                         + ", ".join(f"{v:.3f}" for v in pole.lever) + "};")
        if pole.lamp_section is not None:
            lines.append(f"    public static final int {pole.const}_LAMP_SECTION = {pole.lamp_section};")
        lines.append(f"    public static final double[][] {pole.const} = {{")
        for hit in pole.hits:
            lines.append("            {" + ", ".join(f"{v:.3f}" for v in hit) + "},")
        lines.append("    };")
        lines.append("")
    lines += ["    private UtilityPoleShapes() {", "    }", "}", ""]
    with open(SHAPES_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))


def write_anchors_java(poles):
    lines = [
        "package com.netcattest.ncatminecraft.block;",
        "",
        "public final class UtilityPoleAnchors {",
    ]
    for pole in poles:
        for suffix, points in (("HV", pole.hv), ("MV", pole.mv), ("LV", pole.lv)):
            lines.append(f"    public static final double[][] {pole.const}_{suffix} = {{")
            for point in points:
                lines.append("            {" + ", ".join(f"{v:.3f}" for v in point[:3]) +
                             f", {point[3]}}},")
            lines.append("    };")
        lines.append("")
    lines += ["    private UtilityPoleAnchors() {", "    }", "}", ""]
    with open(ANCHORS_JAVA, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))



NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


def face_quads(lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


def load_elements(path, dy):
    with open(path, encoding="utf-8") as handle:
        model = json.load(handle)
    textures = model["textures"]
    elements = []
    for element in model["elements"]:
        element = json.loads(json.dumps(element))
        element["from"][1] += dy
        element["to"][1] += dy
        if "rotation" in element:
            element["rotation"]["origin"][1] += dy
        for face in element["faces"].values():
            face["path"] = textures[face["texture"][1:]].split("/")[-1]
        elements.append(element)
    return elements


def render(elements, yaw, pitch, scale, size, center, night=False):
    import numpy as np

    width, height = size
    rows = np.linspace(0.0, 1.0, height)[:, None, None]
    if night:
        sky = np.array([10, 14, 28]) * (1 - rows) + np.array([24, 30, 48]) * rows
    else:
        sky = np.array([122, 168, 214]) * (1 - rows) + np.array([206, 222, 236]) * rows
    image = np.broadcast_to(sky, (height, width, 3)).astype(np.float32).copy()
    depth = np.full((height, width), -1e9, dtype=np.float32)
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    forward = np.array([math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.cross(forward, [0.0, 1.0, 0.0])
    right /= np.linalg.norm(right)
    up = np.cross(right, forward)
    toward = -forward
    center = np.array(center, dtype=float)
    cache = {}

    def tex(name):
        if name not in cache:
            cache[name] = np.asarray(Image.open(os.path.join(TEX, f"{name}.png")).convert("RGB"), dtype=np.float32)
        return cache[name]

    def raster(a, b, c, ta, tb, tc, pixels, shade):
        xs, ys = (a[0], b[0], c[0]), (a[1], b[1], c[1])
        x0, x1 = max(int(math.floor(min(xs))), 0), min(int(math.ceil(max(xs))), width - 1)
        y0, y1 = max(int(math.floor(min(ys))), 0), min(int(math.ceil(max(ys))), height - 1)
        if x0 > x1 or y0 > y1:
            return
        area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
        if abs(area) < 1e-9:
            return
        px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area
        w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        region = depth[y0:y1 + 1, x0:x1 + 1]
        mask = inside & (z > region)
        if not mask.any():
            return
        u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0]
        v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1]
        th, tw = pixels.shape[:2]
        iu = np.clip((u * tw / 16.0).astype(int), 0, tw - 1)
        iv = np.clip((v * th / 16.0).astype(int), 0, th - 1)
        region[mask] = z[mask]
        image[y0:y1 + 1, x0:x1 + 1][mask] = pixels[iv, iu][mask] * shade

    for element in elements:
        rot = element.get("rotation")
        quads = face_quads(element["from"], element["to"])
        glow = "forge_data" in element
        for face, spec in element["faces"].items():
            if rot:
                n = np.array(rotate_point(NORMALS[face], {**rot, "origin": (0, 0, 0)}))
            else:
                n = np.array(NORMALS[face], dtype=float)
            if n.dot(toward) <= 1e-6:
                continue
            shade = 1.0 if glow else n[0] ** 2 * 0.6 + n[2] ** 2 * 0.8 + n[1] ** 2 * (1.0 if n[1] > 0 else 0.5)
            if night and not glow:
                shade *= 0.3
            pts = []
            for point in quads[face]:
                p = np.array(rotate_point(point, rot)) - center
                pts.append((width / 2 + p.dot(right) * scale, height / 2 - p.dot(up) * scale, p.dot(toward)))
            u0, v0, u1, v1 = spec["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            pixels = tex(spec["path"])
            raster(pts[0], pts[1], pts[2], uvs[0], uvs[1], uvs[2], pixels, shade)
            raster(pts[0], pts[2], pts[3], uvs[0], uvs[2], uvs[3], pixels, shade)
    return Image.fromarray(np.clip(image, 0, 255).astype(np.uint8))


def pole_elements(pole, lit=None):
    elements = []
    for section in range(pole.sections):
        path = os.path.join(MODELS, "block", "pole", f"{pole.short}_{section}.json")
        elements += load_elements(path, 16.0 * section)
    for name, section, _, value, _unused in pole.overlays:
        if value == lit:
            elements += load_elements(os.path.join(MODELS, "block", "pole", f"{name}.json"), 16.0 * section)
    return elements


def save_preview(pole):
    os.makedirs(PREVIEW, exist_ok=True)
    elements = pole_elements(pole, "false" if pole.overlays else None)
    views = ((0, 8), (90, 8), (-38, 16), (150, 12))
    tall = int(pole.top * 3.0) + 60
    tiles = [render(elements, yaw, pitch, 3.0, (230, tall), (C, pole.top / 2 + 4, C)) for yaw, pitch in views]
    y_lo, y_hi = pole.icon
    focus = (C, (y_lo + y_hi) / 2, C - (4.0 if pole.overlays else 0.0))
    detail = [render(elements, yaw, pitch, 10.0, (460, 460), focus) for yaw, pitch in ((-38, 18), (52, 14))]
    if pole.overlays:
        night = pole_elements(pole, "true")
        detail.append(render(night, -38, -22, 10.0, (460, 460), focus, night=True))
    else:
        detail.append(render(elements, 200, 22, 10.0, (460, 460), focus))
    item = load_elements(os.path.join(MODELS, "item", f"{pole.key}.json"), 0.0)
    detail.append(render(item, 45, 30, 4.4, (230, 230), (8, 8, 8)))
    width = max(230 * len(tiles), 460 * 3 + 230)
    sheet = Image.new("RGB", (width, tall + 460), (16, 18, 22))
    for index, tile in enumerate(tiles):
        sheet.paste(tile, (index * 230, 0))
    for index, tile in enumerate(detail):
        sheet.paste(tile, (index * 460, tall))
    sheet.save(os.path.join(PREVIEW, f"{pole.short}.png"))


def main():
    make_textures()
    poles = [build_concrete(), build_street_light(), build_wood(), build_transformer(),
             build_low_voltage(), build_high_voltage(), build_pad_transformer(), build_substation(),
             build_solar_panel(), build_solar_array(), build_solar_tracker(), build_wind_turbine(),
             build_diesel_generator(), build_service_entrance()]
    for pole in poles:
        write_pole(pole)
    write_shapes_java(poles)
    write_anchors_java(poles)
    if os.environ.get("POLE_PREVIEW") == "1":
        for pole in poles:
            save_preview(pole)
        print("previews em", PREVIEW)


if __name__ == "__main__":
    raise SystemExit(
        "Os postes e transformadores agora vivem em mod-energia. "
        "Aqui este arquivo serve apenas de biblioteca para gen_rack.py e gen_switch.py.")
