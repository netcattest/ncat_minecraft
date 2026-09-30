import os

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "ncat_minecraft", "textures", "item")

BODY = (44, 50, 60, 255)
BODY_DARK = (28, 33, 41, 255)
BODY_LIGHT = (72, 80, 92, 255)
TRIM = (150, 160, 172, 255)
AMBER = (232, 156, 62, 255)
AMBER_DIM = (150, 96, 34, 255)
GLASS = (18, 24, 32, 255)
BEAM = (255, 196, 96, 255)
BEAM_SOFT = (255, 214, 140, 190)
SCREEN = (36, 196, 150, 255)


def log_inspector():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    draw.rectangle((4, 5, 11, 14), fill=BODY)
    draw.rectangle((4, 5, 4, 14), fill=BODY_LIGHT)
    draw.rectangle((11, 5, 11, 14), fill=BODY_DARK)
    draw.rectangle((4, 14, 11, 14), fill=BODY_DARK)

    draw.rectangle((5, 6, 10, 8), fill=GLASS)
    draw.rectangle((6, 7, 7, 7), fill=SCREEN)
    draw.rectangle((9, 7, 9, 7), fill=SCREEN)

    draw.rectangle((5, 10, 10, 10), fill=BODY_DARK)
    draw.rectangle((5, 12, 10, 12), fill=BODY_DARK)
    draw.rectangle((5, 10, 6, 10), fill=AMBER)
    draw.rectangle((5, 12, 7, 12), fill=AMBER_DIM)

    draw.rectangle((6, 2, 9, 4), fill=TRIM)
    draw.rectangle((7, 1, 8, 2), fill=AMBER)
    draw.rectangle((7, 3, 8, 3), fill=BEAM)

    draw.rectangle((12, 8, 14, 9), fill=BEAM)
    draw.rectangle((14, 8, 15, 8), fill=BEAM_SOFT)
    draw.point((13, 7), fill=BEAM_SOFT)
    draw.point((13, 10), fill=BEAM_SOFT)

    image.save(os.path.join(TEX, "log_inspector.png"))
    print("log_inspector icon written")


def main():
    os.makedirs(TEX, exist_ok=True)
    log_inspector()


if __name__ == "__main__":
    main()
