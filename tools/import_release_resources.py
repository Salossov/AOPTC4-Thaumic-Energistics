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
            target = (RESOURCE_ROOT / name).resolve()
            if not target.is_relative_to(RESOURCE_ROOT.resolve()):
                raise ValueError(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            data = jar.read(name)
            target.write_bytes(data)
            records.append({"path": name, "sha256": hashlib.sha256(data).hexdigest()})
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
    report = {"reference_sha1": "a6240b09a0680f3e3246a6d0fe17650299533a84", "assets": records, "language_files": translations}
    write_json(ADDON / "analysis/imported-resources.json", report)
    print(f"THE_RELEASE_RESOURCES_IMPORTED assets={len(records)} languages={translations} item_models=10")


if __name__ == "__main__":
    main()
