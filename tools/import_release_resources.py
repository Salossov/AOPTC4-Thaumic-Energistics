"""Import MIT-licensed ThE release assets and generate modern cell item resources."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
ADDON = PROJECT.parent
RESOURCE_ROOT = PROJECT / "src/main/resources"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def language(data: bytes) -> dict[str, str]:
    values = {}
    for line in data.decode("utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().replace("%d", "%s")
    return values


def main() -> None:
    jar_path = ADDON / "thaumicenergistics-1.1.3.0.jar"
    if hashlib.sha1(jar_path.read_bytes()).hexdigest() != "a6240b09a0680f3e3246a6d0fe17650299533a84":
        raise SystemExit("Wrong release reference")
    records = []
    with zipfile.ZipFile(jar_path) as jar:
        english = language(jar.read("assets/thaumicenergistics/lang/en_US.lang"))
        for name in jar.namelist():
            if not name.startswith("assets/thaumicenergistics/") or name.endswith("/") or "/lang/" in name:
                continue
            modern_name = name.replace("golemBackpack", "golem_backpack").lower()
            # Keep the requested spelling: Windows resolve() may return the old
            # uppercase on-disk filename during a case-only modernization.
            target = RESOURCE_ROOT.resolve() / modern_name
            if not target.resolve().is_relative_to(RESOURCE_ROOT.resolve()):
                raise ValueError(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            data = jar.read(name)
            if modern_name != name:
                old = RESOURCE_ROOT.resolve() / name
                if old.is_file():
                    if old.read_bytes() != data:
                        raise ValueError(f"Refusing to remove modified legacy asset {name}")
                    old.unlink()
                    if old.parent != target.parent and not any(old.parent.iterdir()): old.parent.rmdir()
            target.write_bytes(data)
            records.append({"path": modern_name, "original_path": name, "sha256": hashlib.sha256(data).hexdigest()})
        translations = 0
        for name in jar.namelist():
            if not name.startswith("assets/thaumicenergistics/lang/") or not name.endswith(".lang"):
                continue
            values = language(jar.read(name))
            values["itemGroup.thaumicenergistics"] = "Thaumic Energistics"
            values["keytype.thaumicenergistics.essentia"] = "Эссенция" if name.endswith("ru_RU.lang") else "Essentia"
            for suffix in ("1k", "4k", "16k", "64k", "creative"):
                key = f"thaumicenergistics.item.essentia.cell.{suffix}.name"
                values[f"item.thaumicenergistics.essentia_cell_{suffix}"] = values.get(key, english[key])
                if suffix != "creative":
                    key = f"thaumicenergistics.item.storage.component.{suffix}.name"
                    values[f"item.thaumicenergistics.storage_component_{suffix}"] = values.get(key, english[key])
            for modern, legacy in (("item.thaumicenergistics.storage_casing", "thaumicenergistics.item.storage.casing.name"),
                                   ("tooltip.thaumicenergistics.cell_bytes", "thaumicenergistics.tooltip.essentia.cell.bytes"),
                                   ("tooltip.thaumicenergistics.cell_types", "thaumicenergistics.tooltip.essentia.cell.types")):
                values[modern] = values.get(legacy, english[legacy])
            values["tooltip.thaumicenergistics.partitioned"] = "Размечено" if name.endswith("ru_RU.lang") else "Partitioned"
            values["tooltip.thaumicenergistics.cell_contents"] = "%s × %s"
            legacy = "thaumicenergistics.block.essentia.cell.workbench.name"
            values["block.thaumicenergistics.essentia_cell_workbench"] = values.get(legacy, english[legacy])
            legacy = "thaumicenergistics.aeparts.essentia.StorageBus.name"
            values["item.thaumicenergistics.essentia_storage_bus"] = values.get(legacy, english[legacy])
            for kind in ("import", "export"):
                legacy = f"thaumicenergistics.aeparts.essentia.{kind.title()}Bus.name"
                values[f"item.thaumicenergistics.essentia_{kind}_bus"] = values.get(legacy, english[legacy])
            target = RESOURCE_ROOT / "assets/thaumicenergistics/lang" / (Path(name).stem.lower() + ".json")
            write_json(target, values)
            translations += 1
    for suffix in ("1k", "4k", "16k", "64k", "creative"):
        write_json(RESOURCE_ROOT / f"assets/thaumicenergistics/models/item/essentia_cell_{suffix}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"thaumicenergistics:items/essentia.cell.{suffix}"}})
        if suffix != "creative":
            write_json(RESOURCE_ROOT / f"assets/thaumicenergistics/models/item/storage_component_{suffix}.json", {
                "parent": "minecraft:item/generated", "textures": {"layer0": f"thaumicenergistics:items/storage.component.{suffix}"}})
    write_json(RESOURCE_ROOT / "assets/thaumicenergistics/models/item/storage_casing.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": "thaumicenergistics:items/essentia.cell.casing"}})
    for kind in ("import", "export"):
        textures = {name: f"thaumicenergistics:blocks/parts/essentia.{kind}.bus.{name}" for name in ("face", "overlay", "chamber", "side")}
        textures["particle"] = textures["side"]
        textures["light"] = "thaumicenergistics:blocks/parts/bus.color.light"
        # Original part boxes, mirrored from the old SOUTH coordinate convention to AE2's NORTH.
        boxes = [([4,4,0],[12,12,1],"side"), ([4,4,1],[12,12,2],"chamber"),
                 ([5,5,2],[11,11,3],"chamber"), ([5,5,3],[11,11,4],"side")] if kind == "import" else [
                 ([4,4,3.5],[12,12,4],"side"), ([4,4,2.5],[12,12,3.5],"chamber"),
                 ([5,5,1.5],[11,11,2.5],"chamber"), ([5,5,1],[11,11,1.5],"side"), ([6,6,0],[10,10,1],"face")]
        for state in ("off", "on"):
            elements = []
            for lower, upper, texture in boxes:
                faces = {face:{"texture":"#"+texture} for face in ("north","south","east","west","up","down")}
                if kind == "import" and lower == [4,4,0]: faces["north"] = {"texture":"#face"}
                elements.append({"from":lower,"to":upper,"faces":faces})
            elements.append({"from":[6,6,4],"to":[10,10,5],"faces":{
                face:{"texture":"#light" if state == "on" else "#chamber"} for face in ("north","south","east","west","up","down")}})
            write_json(RESOURCE_ROOT / f"assets/thaumicenergistics/models/part/essentia_{kind}_bus_{state}.json", {
                "parent":"minecraft:block/block", "textures":textures,"elements":elements})
        write_json(RESOURCE_ROOT / f"assets/thaumicenergistics/models/item/essentia_{kind}_bus.json", {
            "parent":f"thaumicenergistics:part/essentia_{kind}_bus_off"})
    write_json(RESOURCE_ROOT / "assets/thaumicenergistics/blockstates/essentia_cell_workbench.json", {
        "variants": {"": {"model": "thaumicenergistics:block/essentia_cell_workbench"}}})
    for state in ("off", "on"):
        textures = {name: f"thaumicenergistics:blocks/parts/essentia.storage.bus.{name}" for name in ("face", "overlay", "side")}
        textures["particle"] = textures["side"]
        textures["light"] = "thaumicenergistics:blocks/parts/bus.color.light"
        elements = []
        for lower, upper in (([1,1,0],[15,15,1]),([4,4,1],[12,12,2]),([5,5,2],[11,11,3])):
            faces = {face: {"texture": "#light" if state == "on" and lower == [5,5,2] else "#side"}
                     for face in ("north", "south", "east", "west", "up", "down")}
            if lower == [1,1,0]: faces["north"] = {"texture": "#face"}
            elements.append({"from": lower, "to": upper, "faces": faces})
        elements.append({"from": [1,1,-0.001], "to": [15,15,-0.001], "faces": {"north": {"texture": "#overlay"}}})
        write_json(RESOURCE_ROOT / f"assets/thaumicenergistics/models/part/essentia_storage_bus_{state}.json", {
            "parent": "minecraft:block/block", "textures": textures, "elements": elements})
    write_json(RESOURCE_ROOT / "assets/thaumicenergistics/models/item/essentia_storage_bus.json", {
        "parent": "thaumicenergistics:part/essentia_storage_bus_off"})
    write_json(RESOURCE_ROOT / "assets/thaumicenergistics/models/block/essentia_cell_workbench.json", {
        "parent": "minecraft:block/cube_bottom_top", "textures": {
            face: f"thaumicenergistics:blocks/essentia.cell.workbench.{face}" for face in ("top", "bottom", "side")}})
    write_json(RESOURCE_ROOT / "assets/thaumicenergistics/models/item/essentia_cell_workbench.json", {
        "parent": "thaumicenergistics:block/essentia_cell_workbench"})
    write_json(RESOURCE_ROOT / "data/thaumicenergistics/loot_table/blocks/essentia_cell_workbench.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": "thaumicenergistics:essentia_cell_workbench"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(RESOURCE_ROOT / "data/minecraft/tags/block/mineable/pickaxe.json", {
        "replace": False, "values": ["thaumicenergistics:essentia_cell_workbench"]})
    report = {"reference_sha1": "a6240b09a0680f3e3246a6d0fe17650299533a84", "assets": records, "language_files": translations}
    write_json(ADDON / "analysis/imported-resources.json", report)
    print(f"THE_RELEASE_RESOURCES_IMPORTED assets={len(records)} languages={translations} item_models=14 part_models=6 block_models=1")


if __name__ == "__main__":
    main()
