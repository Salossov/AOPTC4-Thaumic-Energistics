"""Extract supported registrations; compare pinned Java with the release decompilation."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
ADDON = PROJECT.parent
RESOURCES = PROJECT / "src/main/resources"
FEATURES = {"FeatureCells": "testorage", "FeatureConversionCores": "tecores",
            "FeatureEssentiaIOBuses": "teio", "FeatureVisRelayInterface": "tevisint"}
ASPECTS = {"AIR": "aer", "EARTH": "terra", "FIRE": "ignis", "WATER": "aqua", "ORDER": "ordo", "ENTROPY": "perditio"}
ITEMS = {"EssentiaCell_Housing": "thaumicenergistics:storage_casing", "EssentiaCellWorkbench": "thaumicenergistics:essentia_cell_workbench",
         "DiffusionCore": "thaumicenergistics:diffusion_core", "CoalescenceCore": "thaumicenergistics:coalescence_core",
         "EssentiaStorageBus": "thaumicenergistics:essentia_storage_bus", "EssentiaImportBus": "thaumicenergistics:essentia_import_bus",
         "EssentiaExportBus": "thaumicenergistics:essentia_export_bus", "VisInterface": "thaumicenergistics:vis_interface",
         "cdi.WardedGlass": "thaumcraft:warded_glass", "cdi.RedstoneDust": "minecraft:redstone", "cdi.ThaumiumIngot": "thaumcraft:thaumium_ingot",
         "cdi.EtheralEssence": "thaumcraft:wisp_essence", "cdi.CertusQuartz": "#thaumicenergistics:cell_certus_quartz",
         "cdi.LogicProcessor": "ae2:logic_processor", "cdi.CalculationProcessor": "ae2:calculation_processor",
         "cdi.EngineeringProcessor": "ae2:engineering_processor", "cdi.QuartzGlass": "ae2:quartz_glass", "cdi.SalisMundus": "thaumcraft:salis_mundus",
         "cdi.MECellWorkbench": "ae2:cell_workbench", "cdi.QuickSilverDrop": "thaumcraft:quicksilver_drop", "cdi.OrderShard": "thaumcraft:order_shard",
         "cdi.EntropyShard": "thaumcraft:entropy_shard", "cdi.FormationCore": "ae2:formation_core", "cdi.AnnihilationCore": "ae2:annihilation_core",
         "cdi.FilterTube": "thaumcraft:filtered_essentia_tube", "cdi.IronIngot": "#c:ingots/iron", "cdi.WardedJar": "thaumcraft:warded_jar",
         "cdi.BallanceShard": "thaumcraft:balanced_shard", "cdi.MEP2P": "ae2:me_p2p_tunnel"}
for tier in ("1k", "4k", "16k", "64k"):
    ITEMS[f"EssentiaStorageComponent_{tier}"] = f"thaumicenergistics:storage_component_{tier}"
    ITEMS[f"EssentiaCell_{tier}"] = f"thaumicenergistics:essentia_cell_{tier}"


def write(path: str, value: object) -> None:
    target = RESOURCES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def split_args(text: str) -> list[str]:
    args, start, depth, quote, escaped = [], 0, 0, "", False
    for i, char in enumerate(text):
        if quote:
            if escaped: escaped = False
            elif char == "\\": escaped = True
            elif char == quote: quote = ""
        elif char in "\"'": quote = char
        elif char in "({[": depth += 1
        elif char in ")}]": depth -= 1
        elif char == "," and depth == 0: args.append(text[start:i].strip()); start = i + 1
    if text[start:].strip(): args.append(text[start:].strip())
    return args


def flatten(args: list[str]) -> list[str]:
    result = []
    for arg in args:
        array = re.fullmatch(r"new\s+Object\[\]\s*\{(.*)\}", arg, re.S)
        result.extend(flatten(split_args(array[1])) if array else [arg])
    return result


def extract(path: Path) -> dict:
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)
    method = re.search(r"void\s+registerCrafting\([^)]*\)\s*\{",text)
    if method is None: raise ValueError(f"Missing crafting method: {path}")
    depth, end = 1, method.end()
    while depth and end < len(text):
        if text[end] == "{": depth += 1
        elif text[end] == "}": depth -= 1
        end += 1
    text = text[method.end():end-1]
    arrays = {m[1]: flatten(split_args(m[2])) for m in re.finditer(r"Object\[\]\s+(\w+)\s*=\s*new\s+Object\[\]\s*\{(.*?)\}", text, re.S)}
    costs = {}
    for m in re.finditer(r"(\w+)\.add\(\s*Aspect\.(\w+)\s*,\s*(\d+)\s*\)", text):
        costs.setdefault(m[1], {})[m[2]] = int(m[3])
    result = {}
    calls = r"RecipeRegistry\.([A-Z\d_]+)\s*=\s*(new\s+(?:ShapedOreRecipe|ShapelessOreRecipe)|ThaumcraftApi\.(?:addArcaneCraftingRecipe|addShapelessArcaneCraftingRecipe))\s*\("
    for m in re.finditer(calls, text):
        start, depth, i = m.end(), 1, m.end()
        while depth and i < len(text):
            if text[i] == "(": depth += 1
            elif text[i] == ")": depth -= 1
            i += 1
        args = flatten(split_args(text[start:i-1]))
        arcane, shapeless = m[2].startswith("ThaumcraftApi"), "Shapeless" in m[2]
        if arcane:
            if args[0] != "this.researchKey": raise ValueError(args)
            output, aspects, *ingredients = args[1:]
            vis = {ASPECTS[name]: count for name, count in costs[aspects].items()}
        else:
            output, *ingredients = args
            vis = {}
        mirrored = True
        if not shapeless:
            if ingredients[0] in ("true", "false"): mirrored = ingredients.pop(0) == "true"
            if len(ingredients) != 1 or ingredients[0] not in arrays: raise ValueError(ingredients)
            ingredients = arrays[ingredients[0]].copy()
            if ingredients[0] in ("true", "false"): mirrored = ingredients.pop(0) == "true"
            pattern = []
            while ingredients and ingredients[0].startswith('"'): pattern.append(json.loads(ingredients.pop(0)))
            if len(ingredients) % 2: raise ValueError(ingredients)
            keys = {ingredients[j][1:-1]: ingredients[j+1] for j in range(0, len(ingredients), 2)}
            if not mirrored and any(row != row[::-1] for row in pattern): raise ValueError("Needs a non-mirroring serializer")
            shape = {"pattern": pattern, "symbols": keys}
        else: shape = {"ingredients": ingredients}
        result[m[1]] = {"output": output, "vis": vis, "shapeless": shapeless, "mirrored": mirrored, **shape}
    return result


def ingredient(symbol: str) -> dict:
    value = ITEMS[symbol]
    return {"tag": value[1:]} if value.startswith("#") else {"item": value}


def main() -> None:
    if hashlib.sha1((ADDON / "thaumicenergistics-1.1.3.0.jar").read_bytes()).hexdigest() != "a6240b09a0680f3e3246a6d0fe17650299533a84":
        raise ValueError("Wrong original release")
    report = {"recipes": [], "mappings": ITEMS, "replacements": {
        "PureCertusQuartz": "Removed modern AE2 item; regular certus is its analogue. Original group now contains only regular and charged certus.",
        "IronIngot": "Forge ingotIron -> common c:ingots/iron tag", "research_keys": "Original TE names -> lowercase namespaced IDs; new worlds only."},
        "research_status": "Aliases await approval; Vis arcane-terminal parent is still unported."}
    for feature, research in FEATURES.items():
        relative = f"thaumicenergistics/common/features/{feature}.java"
        source = ADDON / "source_code/src/main/java" / relative
        decompiled = ADDON / "decompiled" / relative
        recipes = extract(source)
        if recipes != extract(decompiled): raise ValueError(f"Published source differs from release registrations: {feature}")
        for registry, recipe in recipes.items():
            output = ITEMS[recipe["output"]]; name = output.split(":")[1]
            if registry.endswith("_SHAPED"): name += "_shaped"
            elif registry.endswith("_SHAPELESS"): name += "_shapeless"
            value = {"type": ("thaumcraft:arcane_shapeless" if recipe["shapeless"] else "thaumcraft:arcane") if recipe["vis"] else
                     ("minecraft:crafting_shapeless" if recipe["shapeless"] else "minecraft:crafting_shaped")}
            if recipe["vis"]: value.update(research=f"thaumicenergistics:{research}", vis=recipe["vis"])
            else: value["category"] = "misc"
            if recipe["shapeless"]: value["ingredients"] = [ingredient(s) for s in recipe["ingredients"]]
            else: value.update(pattern=recipe["pattern"], key={k: ingredient(s) for k, s in recipe["symbols"].items()})
            value["result"] = {"id": output, "count": 1}
            path = f"data/thaumicenergistics/recipe/{name}.json"; write(path, value)
            report["recipes"].append({"registry": registry, "source": str(source.relative_to(ADDON)),
                "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "release_decompiled_sha256": hashlib.sha256(decompiled.read_bytes()).hexdigest(),
                "output": path, "original": recipe})
    if len(report["recipes"]) != 20: raise ValueError("Unexpected original registration count")
    write("data/thaumicenergistics/tags/item/cell_certus_quartz.json", {"replace": False,
          "values": ["ae2:certus_quartz_crystal", "ae2:charged_certus_quartz_crystal"]})
    (ADDON / "analysis/imported-recipes.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"THE_RELEASE_RECIPES_IMPORTED count={len(report['recipes'])} source_release_equivalent=true")


if __name__ == "__main__":
    main()
