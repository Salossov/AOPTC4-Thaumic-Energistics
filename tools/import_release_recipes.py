"""Extract supported original registrations; fail rather than guess changed ingredients."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
ADDON = PROJECT.parent


def main() -> None:
    source = ADDON / "source_code/src/main/java/thaumicenergistics/common/features/FeatureCells.java"
    data = source.read_bytes()
    text = data.decode("utf-8")
    match = re.search(r"RecipeRegistry\.BLOCK_CELL_WORKBENCH\s*=\s*new ShapelessOreRecipe\(\s*"
                      r"(\w+)\s*,\s*(\w+)\s*,\s*cdi\.(\w+)\s*\)", text)
    if match is None:
        raise ValueError("Original cell-workbench registration not found")
    mapping = {"EssentiaCellWorkbench": "thaumicenergistics:essentia_cell_workbench",
               "EssentiaStorageComponent_1k": "thaumicenergistics:storage_component_1k",
               "MECellWorkbench": "ae2:cell_workbench"}
    output, *ingredients = (mapping[name] for name in match.groups())
    recipe = {"type": "minecraft:crafting_shapeless", "category": "misc",
              "ingredients": [{"item": item} for item in ingredients],
              "result": {"id": output, "count": 1}}
    target = PROJECT / "src/main/resources/data/thaumicenergistics/recipe/essentia_cell_workbench.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")
    report = {"source": str(source.relative_to(ADDON)), "source_sha256": hashlib.sha256(data).hexdigest(),
              "source_line": text[:match.start()].count("\n") + 1,
              "output": str(target.relative_to(PROJECT)), "original_symbols": list(match.groups()),
              "mappings": mapping, "remaining_original_recipes": "not yet imported"}
    (ADDON / "analysis/imported-recipes.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("THE_RELEASE_RECIPES_IMPORTED count=1")


if __name__ == "__main__":
    main()
