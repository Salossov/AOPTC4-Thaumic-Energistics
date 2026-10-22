"""Import implemented research branches from the pinned sources and actual release."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
ADDON = PROJECT.parent
ROOT = PROJECT / "src/main/resources"
FEATURES = {
    "FeatureThaumicEnergisticsResearch": ("TERESEARCH", "teresearch", "thaumicenergistics:textures/research/tab.icon.png"),
    "FeatureConversionCores": ("TECORES", "tecores", "thaumicenergistics:coalescence_core"),
    "FeatureCells": ("TESTORAGE", "testorage", "thaumicenergistics:essentia_cell_64k"),
    "FeatureEssentiaIOBuses": ("TEIO", "teio", "thaumicenergistics:essentia_export_bus"),
}
ASPECTS = {"SLIME": "limus", "MAGIC": "praecantatio", "MECHANISM": "machina", "EXCHANGE": "permutatio",
           "VOID": "vacuos", "ENERGY": "potentia", "CRYSTAL": "vitreus", "METAL": "metallum", "AIR": "aer"}
PARENTS = {
    "teresearch": [],
    "tecores": ["thaumicenergistics:teresearch", "thaumicenergistics:pseudo_distilessentia"],
    "testorage": ["thaumicenergistics:teresearch", "thaumicenergistics:pseudo_distilessentia", "thaumicenergistics:pseudo_wardedarcana"],
    "teio": ["thaumicenergistics:tecores", "thaumicenergistics:pseudo_tubefilter"],
}
HIDDEN_PARENTS = {"teresearch": [], "tecores": ["DISTILESSENTIA"], "testorage": ["DISTILESSENTIA"], "teio": ["TUBEFILTER"]}


def write(relative: str, value: object) -> None:
    target = ROOT / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def method(path: Path) -> str:
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)
    start = re.search(r"void\s+registerResearch\s*\([^)]*\)\s*\{", text).end()
    depth, end = 1, start
    while depth:
        if text[end] == "{": depth += 1
        elif text[end] == "}": depth -= 1
        end += 1
    return text[start:end - 1]


def extract(path: Path, original: str) -> dict:
    text = method(path)
    costs = {ASPECTS[match[1]]: int(match[2]) for match in re.finditer(r"\.add\s*\(\s*Aspect\.(\w+)\s*,\s*(\d+)\s*\)", text)}
    arrays = {match[1]: re.findall(r"RecipeRegistry\.(\w+)", match[2]) for match in re.finditer(r"\w+\[\]\s+(\w+)\s*=\s*new\s+\w+\[\]\s*\{(.*?)\}", text, re.S)}
    pages = []
    for match in re.finditer(r"new\s+ResearchPage\s*\(\s*(.*?)\s*\)", text, re.S):
        expression = match[1]
        number = re.search(r"getPageName\s*\(\s*(\d+)", expression)
        recipe = re.search(r"RecipeRegistry\.(\w+)", expression)
        if number: pages.append({"text": f"thaumicenergistics.research_page.{original}.{number[1]}"})
        elif recipe: pages.append({"recipes": [recipe[1]]})
        elif expression.strip() in arrays: pages.append({"recipes": arrays[expression.strip()]})
        else: raise ValueError(f"Unrecognized original page: {path}: {expression}")
    complexity = re.search(r"createResearchItem\s*\(\s*\w+\s*,\s*(\d+|(?:ResearchRegistry\.)?COMPLEXITY_MEDIUM)", text)
    result = {"aspects": costs, "complexity": 0 if complexity is None else (2 if not complexity[1].isdigit() else int(complexity[1])),
              "pages": pages, "concealed": ".setConcealed(" in text, "round": ".setRound(" in text,
              "auto_unlock": ".setAutoUnlock(" in text}
    if not pages: raise ValueError(f"No original pages: {path}")
    return result


def main() -> None:
    if hashlib.sha1((ADDON / "thaumicenergistics-1.1.3.0.jar").read_bytes()).hexdigest() != "a6240b09a0680f3e3246a6d0fe17650299533a84":
        raise ValueError("Wrong release reference")
    recipe_report = json.loads((ADDON / "analysis/imported-recipes.json").read_text(encoding="utf-8"))
    recipes = {entry["registry"]: "thaumicenergistics:" + Path(entry["output"]).stem for entry in recipe_report["recipes"]}
    source_registry = (ADDON / "source_code/src/main/java/thaumicenergistics/common/registries/ResearchRegistry.java").read_text(encoding="utf-8")
    release_registry = (ADDON / "decompiled/thaumicenergistics/common/registries/ResearchRegistry.java").read_text(encoding="utf-8")
    report = {"research": [], "pending": "All remaining feature branches, config filtering, and Vis's original arcane-terminal parent"}
    write("data/thaumicenergistics/thaumcraft/category.json", {
        "kind": "book_category", "id": "thaumicenergistics:main", "name": "tc.research_category.thaumicenergistics",
        "icon": "thaumicenergistics:textures/research/tab.icon.png", "background": "thaumicenergistics:textures/research/research.background.png", "order": 100})
    for feature, (original, modern, icon) in FEATURES.items():
        source = ADDON / f"source_code/src/main/java/thaumicenergistics/common/features/{feature}.java"
        release = ADDON / f"decompiled/thaumicenergistics/common/features/{feature}.java"
        extracted = extract(release, original)
        if extract(source, original) != extracted: raise ValueError(f"Source/release research mismatch: {feature}")
        pattern = rf'\w+\s*\(\s*"{original[2:]}"\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)'
        coordinates = re.search(pattern, release_registry)
        if coordinates.groups() != re.search(pattern, source_registry).groups(): raise ValueError("Coordinate mismatch")
        value = {"kind": "research", "key": f"thaumicenergistics:{modern}", "category": "thaumicenergistics:main",
                 "parents": PARENTS[modern], "hidden_parents": HIDDEN_PARENTS[modern], "icon": icon,
                 "column": int(coordinates[1]), "row": int(coordinates[2]), "aspects": extracted["aspects"],
                 "complexity": extracted["complexity"], "auto_unlock": extracted["auto_unlock"],
                 "concealed": extracted["concealed"], "round": extracted["round"], "text_pages": [],
                 "recipe_pages": [], "recipe_groups": {}, "page_order": []}
        for index, page in enumerate(extracted["pages"]):
            if "text" in page:
                value["text_pages"].append(page["text"])
                value["page_order"].append(page["text"])
            else:
                variants = [recipes[recipe] for recipe in page["recipes"]]
                value["recipe_pages"].extend(variants)
                if len(variants) == 1: value["page_order"].extend(variants)
                else:
                    group = f"thaumicenergistics:{modern}_recipes_{index}"
                    value["recipe_groups"][group] = variants
                    value["page_order"].append(group)
        write(f"data/thaumicenergistics/thaumcraft/research/{modern}.json", value)
        report["research"].append({"key": value["key"], "original_key": "thaumicenergistics." + original,
            "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "release_sha256": hashlib.sha256(release.read_bytes()).hexdigest(),
            "extracted": extracted})
    for target, column, row in (("DISTILESSENTIA", -2, 0), ("TUBEFILTER", -3, 0), ("WARDEDARCANA", 1, 2)):
        write(f"data/thaumicenergistics/thaumcraft/research/pseudo_{target.lower()}.json", {
            "kind": "research", "key": f"thaumicenergistics:pseudo_{target.lower()}", "category": "thaumicenergistics:main",
            "linked_research": target, "column": column, "row": row, "complexity": 1, "stub": True, "hidden": True, "icon": "minecraft:air"})
    for language in (ROOT / "assets/thaumicenergistics/lang").glob("*.json"):
        values = json.loads(language.read_text(encoding="utf-8"))
        for original, modern, icon in FEATURES.values():
            for kind in ("name", "text"):
                key = f"tc.research_{kind}.thaumicenergistics.{original}"
                if key in values: values[f"tc.research_{kind}.thaumicenergistics:{modern}"] = values[key]
        language.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    (ADDON / "analysis/imported-research.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("THE_RELEASE_RESEARCH_IMPORTED nodes=4 links=3 source_release_equivalent=true")


if __name__ == "__main__":
    main()
