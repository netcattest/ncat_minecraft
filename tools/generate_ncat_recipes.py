import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
RECIPES = ROOT / "data/ncat_minecraft/recipes"


def ingredient(item):
    return {"item": item if ":" in item else f"minecraft:{item}"}


def recipe(pattern, keys, result, count=1):
    return {
        "type": "minecraft:crafting_shaped",
        "pattern": pattern,
        "key": {symbol: ingredient(item) for symbol, item in keys.items()},
        "result": {"item": f"ncat_minecraft:{result}", "count": count},
    }


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


active = {
    "gaming_chair": recipe(["BLB", "LWL", "ISI"], {"B": "blue_wool", "L": "black_wool", "W": "leather", "I": "iron_ingot", "S": "stone_slab"}, "gaming_chair"),
    "screen": recipe(["IGI", "GRG", "IDI"], {"I": "iron_ingot", "G": "glass_pane", "R": "redstone", "D": "red_dye"}, "screen", 4),
    "ssh_screen": recipe(["IGI", "GRG", "IDI"], {"I": "iron_ingot", "G": "glass_pane", "R": "redstone", "D": "blue_dye"}, "ssh_screen", 4),
    "log_screen": recipe(["IGI", "GRG", "IDI"], {"I": "iron_ingot", "G": "glass_pane", "R": "redstone", "D": "orange_dye"}, "log_screen", 4),
    "devtools_screen": recipe(["IGI", "GRG", "IDI"], {"I": "iron_ingot", "G": "glass_pane", "R": "redstone", "D": "purple_dye"}, "devtools_screen", 4),
    "keyboard": recipe(["III", "PPP", "DRD"], {"I": "iron_ingot", "P": "stone_pressure_plate", "D": "red_dye", "R": "redstone"}, "keyboard"),
    "ssh_keyboard": recipe(["III", "PPP", "DRD"], {"I": "iron_ingot", "P": "stone_pressure_plate", "D": "blue_dye", "R": "redstone"}, "ssh_keyboard"),
    "linker": recipe([" R ", " I ", " S "], {"R": "redstone", "I": "iron_ingot", "S": "stick"}, "linker"),
    "laserpointer": recipe([" G ", " R ", " C "], {"G": "glass_pane", "R": "redstone", "C": "copper_ingot"}, "laserpointer"),
    "screencfg": recipe([" G ", "IRI", " S "], {"G": "glass_pane", "I": "iron_ingot", "R": "redstone", "S": "stick"}, "screencfg"),
}

for name, value in active.items():
    save(RECIPES / f"{name}.json", value)

for name in (
    "backlight", "batcell", "batpack", "extcard", "laserdiode", "minepad",
    "minepad2", "peripheral", "rctrl", "redctrl1", "redctrl2", "server",
    "stonekey", "upgrade", "upgrade_gps", "upgrade_laser", "upgrade_redin",
    "upgrade_redout",
):
    path = RECIPES / f"{name}.json"
    if path.exists():
        path.unlink()

loot = ROOT / "data/ncat_minecraft/loot_tables/blocks"
save(loot / "ssh_kb_left.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "ncat_minecraft:ssh_keyboard"}]}]})
save(loot / "ssh_kb_right.json", {"type": "minecraft:block", "pools": []})
save(loot / "gaming_chair.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "ncat_minecraft:gaming_chair", "conditions": [{"condition": "minecraft:block_state_property", "block": "ncat_minecraft:gaming_chair", "properties": {"half": "lower"}}]}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

pickaxe = ROOT / "data/minecraft/tags/blocks/mineable/pickaxe.json"
tag = json.loads(pickaxe.read_text(encoding="utf-8"))
for block in ("ssh_screen", "log_screen", "devtools_screen", "ssh_kb_left", "ssh_kb_right"):
    name = f"ncat_minecraft:{block}"
    if name not in tag["values"]:
        tag["values"].append(name)
save(pickaxe, tag)

axe = ROOT / "data/minecraft/tags/blocks/mineable/axe.json"
axe_tag = json.loads(axe.read_text(encoding="utf-8")) if axe.exists() else {"replace": False, "values": []}
if "ncat_minecraft:gaming_chair" not in axe_tag["values"]:
    axe_tag["values"].append("ncat_minecraft:gaming_chair")
save(axe, axe_tag)
