import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/ncat_minecraft"
COLORS = {
    "leather_black": "#14171d",
    "leather_dark": "#30343c",
    "leather_seat": "#383d43",
    "blue_trim": "#0b78df",
    "blue_bright": "#24b5fc",
    "metal": "#2b3138",
    "wheel": "#0d1014",
    "cyan": "#68ddff",
    "white": "#f4f8ff",
    "pink": "#f7aac8",
}


def cuboid_faces(element, offset_y):
    x1, y1, z1 = element["from"]
    x2, y2, z2 = element["to"]
    y1 += offset_y
    y2 += offset_y
    vertices = [
        (x1, z1, y1), (x2, z1, y1), (x2, z2, y1), (x1, z2, y1),
        (x1, z1, y2), (x2, z1, y2), (x2, z2, y2), (x1, z2, y2),
    ]
    return [[vertices[i] for i in ids] for ids in ((0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7), (4, 5, 6, 7))]


fig = plt.figure(figsize=(8, 10), dpi=180, facecolor="#111319")
ax = fig.add_subplot(projection="3d", facecolor="#111319")
faces = []
colors = []
for half, offset in (("lower", 0), ("upper", 16)):
    path = ASSETS / f"models/block/gaming_chair_{half}.json"
    model = json.loads(path.read_text(encoding="utf-8"))
    for element in model["elements"]:
        material = element["faces"]["north"]["texture"].lstrip("#")
        color = COLORS[material]
        element_faces = cuboid_faces(element, offset)
        faces.extend(element_faces)
        colors.extend([color] * len(element_faces))

ax.add_collection3d(Poly3DCollection(faces, facecolors=colors, edgecolors="none", zsort="average"))

ax.set_xlim(0, 16)
ax.set_ylim(0, 16)
ax.set_zlim(0, 32)
ax.set_box_aspect((16, 16, 32), zoom=.9)
ax.view_init(elev=18, azim=-58)
ax.set_axis_off()
fig.subplots_adjust(left=0, right=1, top=1, bottom=0)
output = ROOT / "build/preview/gaming_chair.png"
output.parent.mkdir(parents=True, exist_ok=True)
fig.savefig(output, facecolor=fig.get_facecolor(), bbox_inches="tight", pad_inches=.03)
print(output)
