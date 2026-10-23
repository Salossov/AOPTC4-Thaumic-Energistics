"""Validate the batch-2 extras (wand focus AE wrench, quartz duplication, Thaumcraft facades) against the pinned release.

Read-only validation: asserts the decompilation facts, the authored resources in this worktree and the
original Thaumcraft 1.7.10 jar lang mapping. Writes nothing; exits nonzero on any mismatch.
"""
from pathlib import Path
import hashlib
import json
import re
import sys
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
# Main checkouts keep the pinned release jar in PROJECT.parent; this worktree sits at repo/.worktree/<name>,
# so the addon root is the nearest ancestor holding the release jar.
ADDON = next((p for p in PROJECT.parents if (p / "thaumicenergistics-1.1.3.0.jar").is_file()), PROJECT.parents[2])
PORT_ROOT = ADDON.parents[1]
ROOT = PROJECT / "src/main/resources"
RELEASE = ADDON / "thaumicenergistics-1.1.3.0.jar"
ORIGINAL_JAR = PORT_ROOT / "Thaumcraft-1.7.10-4.2.3.5.jar"
DECOMPILED = ADDON / "decompiled/thaumicenergistics"
TC_BLOCKSTATES = PORT_ROOT / "repo/mod/src/main/resources/assets/thaumcraft/blockstates"
LANGS = ("en_us", "ru_ru", "zh_cn", "de_de", "ko_kr")

CHECKS = []
NOTES = []


def check(name, ok, detail=""):
    CHECKS.append((name, bool(ok), detail))
    return bool(ok)


def load_json(name, path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        check(f"resource {name}", False, f"{type(error).__name__}: {error}")
        return None


def release_lang_keys(lang):
    """The batch-2 keys the original release jar itself ships for this language."""
    try:
        with zipfile.ZipFile(RELEASE) as jar:
            # Release file names are like de_DE.lang / ko_KR.lang (lowercase prefix).
            lines = jar.read(f"assets/thaumicenergistics/lang/{lang[:2]}_{lang[3:].upper()}.lang").decode("utf-8", errors="replace").splitlines()
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        check(f"release lang {lang} readable", False, f"{type(error).__name__}: {error}")
        return set()
    wanted = ("item.focus.aewrench", "TEFOCUSWRENCH", "TECERTUSDUPE")
    return {line.split("=", 1)[0].strip() for line in lines
            if "=" in line and any(w in line.split("=", 1)[0] for w in wanted)}


def load_source(name, path):
    try:
        text = path.read_text(encoding="utf-8")
        check(f"source {name} readable", True, str(path.relative_to(ADDON)))
        return text
    except OSError as error:
        check(f"source {name} readable", False, f"{type(error).__name__}: {error}")
        return ""


def source_facts(name, text, facts):
    for label, pattern in facts:
        check(f"source {name} {label}", re.search(pattern, text, re.S) is not None)


def main():
    digest = hashlib.sha1(RELEASE.read_bytes()).hexdigest() if RELEASE.is_file() else "missing"
    check("pinned release jar sha1", digest == "a6240b09a0680f3e3246a6d0fe17650299533a84", digest)

    # --- Pinned-source facts (release decompilation) ---
    wrench = load_source("FeatureWrenchFocus", DECOMPILED / "common/features/FeatureWrenchFocus.java")
    if wrench:
        source_facts("FeatureWrenchFocus", wrench, [
            ("pattern ANF/NWN/FNA", r'\{"ANF", "NWN", "FNA",'),
            ("keys A=AirShard F=FireShard N=NetherQuartz W=CertusWrench",
             r"'A', cdi\.AirShard, 'F', cdi\.FireShard, 'N', cdi\.NetherQuartz, 'W', cdi\.CertusWrench"),
            ("arcane vis AIR 10", r"wrenchAspects\.add\(Aspect\.AIR, 10\)"),
            ("arcane vis FIRE 10", r"wrenchAspects\.add\(Aspect\.FIRE, 10\)"),
            ("research MECHANISM 6", r"focusAspects\.add\(Aspect\.MECHANISM, 6\)"),
            ("research TOOL 5", r"focusAspects\.add\(Aspect\.TOOL, 5\)"),
            ("research MAGIC 3", r"focusAspects\.add\(Aspect\.MAGIC, 3\)"),
            ("complexity 1", r"createResearchItem\(focusAspects, 1, focusIcon, focusPages\)"),
            ("page 1 + recipe page", r"FOCUS_WRENCH\.getPageName\(1\)\),\s*new ResearchPage\(RecipeRegistry\.ITEM_WRENCH_FOCUS\)"),
            ("secondary", r"FOCUSFIRE\.getKey\(\)\}\)\.setSecondary\(\)"),
            ("pseudo parent FOCUSFIRE", r"PseudoResearchTypes\.FOCUSFIRE\.registerPsudeoResearch\(\)"),
        ])

    dupe = load_source("FeatureQuartzDupe", DECOMPILED / "common/features/FeatureQuartzDupe.java")
    if dupe:
        source_facts("FeatureQuartzDupe", dupe, [
            ("certus crucible CRYSTAL 4", r"certusAspects\.add\(Aspect\.CRYSTAL, 4\)"),
            ("certus crucible WATER 2", r"certusAspects\.add\(Aspect\.WATER, 2\)"),
            ("nether crucible CRYSTAL 4", r"nQAspects\.add\(Aspect\.CRYSTAL, 4\)"),
            ("nether crucible WATER 2", r"nQAspects\.add\(Aspect\.WATER, 2\)"),
            ("nether crucible ENERGY 2", r"nQAspects\.add\(Aspect\.ENERGY, 2\)"),
            ("certus result count 2", r"Certus2\.field_77994_a = 2;"),
            ("nether result count 2", r"NetherQuartz2\.field_77994_a = 2;"),
            ("oredict output certus", r"OreDictionary\.getOres\(Certus1\)\.get\(0\)"),
            ("oredict output nether", r"OreDictionary\.getOres\(NetherQuartz1\)\.get\(0\)"),
            ("research CRYSTAL 5", r"certusDupeAspects\.add\(Aspect\.CRYSTAL, 5\)"),
            ("complexity 1", r"createResearchItem\(certusDupeAspects, 1, certusDupeIcon, certusDupePages\)"),
            ("secondary + concealed", r"setSecondary\(\)\.setConcealed\(\)"),
            ("hidden parent ALCHEMICALDUPLICATION", r'setParentsHidden\(new String\[\]\{"ALCHEMICALDUPLICATION"\}\)'),
            ("item trigger certus icon", r"setItemTriggers\(new ItemStack\[\]\{certusDupeIcon\}\)"),
            ("icon certus quartz crystal", r"certusQuartzCrystal\(\)\.maybeStack\(1\)\.get\(\)"),
            ("pages text1 + DUPE_CERTUS + DUPE_NETHER_QUARTZ",
             r"getPageName\(1\)\)\s*,\s*new ResearchPage\(RecipeRegistry\.DUPE_CERTUS\)\s*,"
             r"\s*new ResearchPage\(RecipeRegistry\.DUPE_NETHER_QUARTZ\)"),
            ("pseudo parent DUPE", r"PseudoResearchTypes\.DUPE\.registerPsudeoResearch\(\)"),
            ("parents pseudo DUPE", r"setParents\(new String\[\]\{ResearchRegistry\.PseudoResearchTypes\.DUPE\.getKey\(\)\}\)"),
        ])
        NOTES.append("ADAPTATION: the release picks crucible outputs via OreDictionary.getOres(...).get(0), then clones and resizes the"
                     " stack to 2; the port pins explicit ids (ae2:certus_quartz_crystal, minecraft:quartz) so recipe loading cannot crash"
                     " with IndexOutOfBoundsException when the 1.7.10 ore-dictionary entries are absent.")

    registry = load_source("ResearchRegistry", DECOMPILED / "common/registries/ResearchRegistry.java")
    if registry:
        source_facts("ResearchRegistry", registry, [
            ("CERTUS_DUPE -5/-5", r'CERTUS_DUPE\("CERTUSDUPE", -5, -5\)'),
            ("FOCUS_WRENCH -3/-7", r'FOCUS_WRENCH\("FOCUSWRENCH", -3, -7\)'),
            ("pseudo DUPE -5/-6", r'DUPE\("ALCHEMICALDUPLICATION", "ALCHEMY", -5, -6\)'),
            ("pseudo FOCUSFIRE -4/-7", r'FOCUSFIRE\("FOCUSFIRE", "THAUMATURGY", -4, -7\)'),
            ("key convention TE+internalName", r'"TE" \+ internalName'),
            ("key namespacing", r'"thaumicenergistics\." \+ this\.internalName'),
            ("page key convention", r'"thaumicenergistics\.research_page\." \+ this\.internalName'),
        ])

    facades = load_source("FeatureThaumcraftFacades", DECOMPILED / "common/features/FeatureThaumcraftFacades.java")
    if facades:
        forced = re.findall(r"forceEnabled\(ConfigBlocks\.(\w+), (\d+)\)", facades)
        expected = [("blockCosmeticOpaque", "0"), ("blockCosmeticOpaque", "1"), ("blockCosmeticOpaque", "2"),
                    ("blockCosmeticSolid", "1"), ("blockCosmeticSolid", "4"), ("blockCosmeticSolid", "5"),
                    ("blockCosmeticSolid", "6"), ("blockCosmeticSolid", "7"), ("blockCosmeticSolid", "11"),
                    ("blockCosmeticSolid", "12"), ("blockCosmeticSolid", "14"),
                    ("blockWoodenDevice", "6"), ("blockWoodenDevice", "7"),
                    ("blockTaint", "0"), ("blockTaint", "2")]
        check("source FeatureThaumcraftFacades whitelist", forced == expected, f"{len(forced)} forceEnabled calls")
        check("source FeatureThaumcraftFacades allowMirrors gates blockCosmeticOpaque 2",
              re.search(r"if \(Config\.allowMirrors\) \{\s*this\.forceEnabled\(ConfigBlocks\.blockCosmeticOpaque, 2\);", facades) is not None)

    # --- Authored resources in this worktree ---
    templates = ROOT / "data/thaumicenergistics/research_templates"
    pseudo = load_json("pseudo_focusfire.json", templates / "pseudo_focusfire.json")
    if pseudo is not None:
        check("pseudo_focusfire key", pseudo.get("key") == "thaumicenergistics:pseudo_focusfire", str(pseudo.get("key")))
        check("pseudo_focusfire linked_research", pseudo.get("linked_research") == "FOCUSFIRE", str(pseudo.get("linked_research")))
        check("pseudo_focusfire column -4 row -7", (pseudo.get("column"), pseudo.get("row")) == (-4, -7))
        check("pseudo_focusfire stub+hidden", pseudo.get("stub") is True and pseudo.get("hidden") is True)
        check("pseudo_focusfire complexity 1", pseudo.get("complexity") == 1)

    pseudo = load_json("pseudo_alchemicalduplication.json", templates / "pseudo_alchemicalduplication.json")
    if pseudo is not None:
        check("pseudo_alchemicalduplication key", pseudo.get("key") == "thaumicenergistics:pseudo_alchemicalduplication", str(pseudo.get("key")))
        check("pseudo_alchemicalduplication linked_research", pseudo.get("linked_research") == "ALCHEMICALDUPLICATION", str(pseudo.get("linked_research")))
        check("pseudo_alchemicalduplication column -5 row -6", (pseudo.get("column"), pseudo.get("row")) == (-5, -6))
        check("pseudo_alchemicalduplication stub+hidden", pseudo.get("stub") is True and pseudo.get("hidden") is True)

    data = load_json("tefocuswrench.json", templates / "tefocuswrench.json")
    if data is not None:
        check("tefocuswrench key", data.get("key") == "thaumicenergistics:tefocuswrench", str(data.get("key")))
        check("tefocuswrench category", data.get("category") == "thaumicenergistics:main", str(data.get("category")))
        check("tefocuswrench parents", data.get("parents") == ["thaumicenergistics:pseudo_focusfire"], str(data.get("parents")))
        check("tefocuswrench column -3 row -7", (data.get("column"), data.get("row")) == (-3, -7))
        check("tefocuswrench aspects", data.get("aspects") == {"machina": 6, "instrumentum": 5, "praecantatio": 3}, str(data.get("aspects")))
        check("tefocuswrench complexity 1", data.get("complexity") == 1)
        check("tefocuswrench secondary", data.get("secondary") is True)
        check("tefocuswrench text page", data.get("text_pages") == ["thaumicenergistics.research_page.TEFOCUSWRENCH.1"], str(data.get("text_pages")))
        check("tefocuswrench recipe page", data.get("recipe_pages") == ["thaumicenergistics:focus_ae_wrench"], str(data.get("recipe_pages")))
        check("tefocuswrench page order (text then recipe)", data.get("page_order") == ["thaumicenergistics.research_page.TEFOCUSWRENCH.1", "thaumicenergistics:focus_ae_wrench"])

    data = load_json("tecertusdupe.json", templates / "tecertusdupe.json")
    if data is not None:
        check("tecertusdupe key", data.get("key") == "thaumicenergistics:tecertusdupe", str(data.get("key")))
        check("tecertusdupe parents", data.get("parents") == ["thaumicenergistics:pseudo_alchemicalduplication"], str(data.get("parents")))
        check("tecertusdupe hidden parents", data.get("hidden_parents") == ["ALCHEMICALDUPLICATION"], str(data.get("hidden_parents")))
        check("tecertusdupe column -5 row -5", (data.get("column"), data.get("row")) == (-5, -5))
        check("tecertusdupe aspects", data.get("aspects") == {"vitreus": 5}, str(data.get("aspects")))
        check("tecertusdupe complexity 1", data.get("complexity") == 1)
        check("tecertusdupe secondary + concealed", data.get("secondary") is True and data.get("concealed") is True)
        check("tecertusdupe item triggers", data.get("item_triggers") == ["ae2:certus_quartz_crystal"], str(data.get("item_triggers")))
        check("tecertusdupe recipe pages order", data.get("recipe_pages") == ["thaumicenergistics:certus_quartz_dupe", "thaumicenergistics:nether_quartz_dupe"], str(data.get("recipe_pages")))
        check("tecertusdupe page order (text then both recipes)", data.get("page_order") == ["thaumicenergistics.research_page.TECERTUSDUPE.1", "thaumicenergistics:certus_quartz_dupe", "thaumicenergistics:nether_quartz_dupe"])

    data = load_json("focus_ae_wrench.json", ROOT / "data/thaumicenergistics/recipe/focus_ae_wrench.json")
    if data is not None:
        check("focus_ae_wrench arcane + research", data.get("type") == "thaumcraft:arcane" and data.get("research") == "thaumicenergistics:tefocuswrench")
        check("focus_ae_wrench pattern", data.get("pattern") == ["ANF", "NWN", "FNA"], str(data.get("pattern")))
        check("focus_ae_wrench keys", data.get("key") == {"A": {"item": "thaumcraft:air_shard"}, "F": {"item": "thaumcraft:fire_shard"},
                                                          "N": {"item": "minecraft:quartz"}, "W": {"item": "ae2:certus_quartz_wrench"}}, str(data.get("key")))
        check("focus_ae_wrench whole-vis aer 10 / ignis 10", data.get("vis") == {"aer": 10, "ignis": 10}, str(data.get("vis")))
        check("focus_ae_wrench result", data.get("result") == {"id": "thaumicenergistics:focus_ae_wrench", "count": 1}, str(data.get("result")))
        conditions = data.get("neoforge:conditions", [])
        check("focus_ae_wrench extras condition", any(c.get("type") == "thaumicenergistics:extras_feature" and c.get("feature") == "wrench_focus" for c in conditions), str(conditions))
        check("focus_ae_wrench item condition", any(c.get("type") == "neoforge:item_exists" and c.get("item") == "ae2:certus_quartz_wrench" for c in conditions), str(conditions))

    data = load_json("certus_quartz_dupe.json", ROOT / "data/thaumicenergistics/recipe/certus_quartz_dupe.json")
    if data is not None:
        check("certus_quartz_dupe crucible + research", data.get("type") == "thaumcraft:crucible" and data.get("research") == "thaumicenergistics:tecertusdupe")
        check("certus_quartz_dupe result count 2", data.get("result") == {"id": "ae2:certus_quartz_crystal", "count": 2}, str(data.get("result")))
        check("certus_quartz_dupe catalyst tag", data.get("catalyst") == {"tag": "thaumicenergistics:cell_certus_quartz"}, str(data.get("catalyst")))
        check("certus_quartz_dupe aspects vitreus 4 / aqua 2", data.get("aspects") == {"vitreus": 4, "aqua": 2}, str(data.get("aspects")))
        conditions = data.get("neoforge:conditions", [])
        check("certus_quartz_dupe extras condition", any(c.get("type") == "thaumicenergistics:extras_feature" and c.get("feature") == "certus_dupe" for c in conditions), str(conditions))
        check("certus_quartz_dupe tag condition", any(c.get("type") == "neoforge:not" and c.get("value", {}).get("tag") == "thaumicenergistics:cell_certus_quartz" for c in conditions), str(conditions))
        check("certus_quartz_dupe item condition", any(c.get("type") == "neoforge:item_exists" and c.get("item") == "ae2:certus_quartz_crystal" for c in conditions), str(conditions))

    data = load_json("nether_quartz_dupe.json", ROOT / "data/thaumicenergistics/recipe/nether_quartz_dupe.json")
    if data is not None:
        check("nether_quartz_dupe crucible + research", data.get("type") == "thaumcraft:crucible" and data.get("research") == "thaumicenergistics:tecertusdupe")
        check("nether_quartz_dupe result count 2", data.get("result") == {"id": "minecraft:quartz", "count": 2}, str(data.get("result")))
        check("nether_quartz_dupe catalyst tag", data.get("catalyst") == {"tag": "c:gems/quartz"}, str(data.get("catalyst")))
        check("nether_quartz_dupe aspects vitreus 4 / aqua 2 / potentia 2", data.get("aspects") == {"vitreus": 4, "aqua": 2, "potentia": 2}, str(data.get("aspects")))
        conditions = data.get("neoforge:conditions", [])
        check("nether_quartz_dupe extras condition", any(c.get("type") == "thaumicenergistics:extras_feature" and c.get("feature") == "certus_dupe" for c in conditions), str(conditions))
        check("nether_quartz_dupe tag condition", any(c.get("type") == "neoforge:not" and c.get("value", {}).get("tag") == "c:gems/quartz" for c in conditions), str(conditions))
        check("nether_quartz_dupe item condition", any(c.get("type") == "neoforge:item_exists" and c.get("item") == "minecraft:quartz" for c in conditions), str(conditions))

    whitelisted = ["thaumcraft:amber_block", "thaumcraft:amber_bricks", "thaumcraft:warded_glass",
                   "thaumcraft:obsidian_tile", "thaumcraft:thaumium_block",
                   "thaumcraft:tallow_block", "thaumcraft:arcane_stone", "thaumcraft:arcane_stone_bricks",
                   "thaumcraft:ancient_stone", "thaumcraft:greatwood_planks", "thaumcraft:silverwood_planks",
                   "thaumcraft:fibrous_taint", "thaumcraft:tainted_flesh"]
    pack = load_source("ExtrasFacadePack", PROJECT / "src/main/java/thaumicenergistics/extras/ExtrasFacadePack.java")
    if pack:
        match = re.search(r"BLOCKS = List.of\((.*?)\);", pack, re.S)
        names = re.findall(r'"([a-z_]+)"', match.group(1)) if match else []
        check("facades source maps exact release counterparts", ["thaumcraft:" + name for name in names] == whitelisted)
        check("unconditional facade tag removed", not (ROOT / "data/ae2/tags/block/whitelisted/facades.json").exists())
        NOTES.append("FACADES: source mapping only. Runtime pack loading, both option gates, optional entries and data reload"
                     " must be exercised by the deferred native test; this script cannot establish them.")

    data = load_json("focus_ae_wrench model", ROOT / "assets/thaumicenergistics/models/item/focus_ae_wrench.json")
    if data is not None:
        check("focus_ae_wrench model parent", data.get("parent") == "minecraft:item/generated", str(data.get("parent")))
        check("focus_ae_wrench model layer0", data.get("textures", {}).get("layer0") == "thaumicenergistics:items/foci.aewrench", str(data.get("textures", {}).get("layer0")))

    lang_keys = ("thaumicenergistics.item.focus.aewrench.name", "thaumicenergistics.item.focus.aewrench.disabled.name",
                 "tc.research_name.thaumicenergistics.TEFOCUSWRENCH", "tc.research_text.thaumicenergistics.TEFOCUSWRENCH",
                 "thaumicenergistics.research_page.TEFOCUSWRENCH.1", "tc.research_name.thaumicenergistics.TECERTUSDUPE",
                 "tc.research_text.thaumicenergistics.TECERTUSDUPE", "thaumicenergistics.research_page.TECERTUSDUPE.1")
    for lang in LANGS:
        values = load_json(f"lang {lang}", ROOT / f"assets/thaumicenergistics/lang/{lang}.json")
        if values is None:
            continue
        missing = [key for key in lang_keys if not values.get(key)]
        # en/ru/zh were complete in the release; de/ko had the same gaps in the original jar
        # (verified below), so those two must match the release key set instead of all eight.
        release_complete = lang not in ("de_de", "ko_kr")
        if release_complete:
            check(f"lang {lang} extras keys", not missing, f"{len(lang_keys) - len(missing)}/{len(lang_keys)} present" + (f"; missing {', '.join(missing)}" if missing else ""))
        else:
            present = {key for key in lang_keys if values.get(key)}
            release_keys = release_lang_keys(lang)
            check(f"lang {lang} extras keys match release", present == release_keys,
                  f"port {len(present)}/release {len(release_keys)}; diff {sorted(present ^ release_keys)}")

    # --- Original jar lang mapping and modern port blockstates ---
    try:
        with zipfile.ZipFile(ORIGINAL_JAR) as jar:
            lines = jar.read("assets/thaumcraft/lang/en_US.lang").decode("utf-8", errors="replace").splitlines()
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        check("original jar lang readable", False, f"{type(error).__name__}: {error}")
        lines = []
    original = {}
    for line in lines:
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            original[key.strip()] = value.strip()
    expected_names = {"tile.blockCosmeticOpaque.0.name": "Amber Block", "tile.blockCosmeticOpaque.1.name": "Amber Bricks",
                      "tile.blockCosmeticOpaque.2.name": "Warded Glass", "tile.blockCosmeticSolid.1.name": "Obsidian Tile",
                      "tile.blockCosmeticSolid.4.name": "Thaumium Block", "tile.blockCosmeticSolid.5.name": "Tallow Block",
                      "tile.blockCosmeticSolid.6.name": "Arcane Stone Block", "tile.blockCosmeticSolid.7.name": "Arcane Stone Bricks",
                      "tile.blockCosmeticSolid.11.name": "Ancient Stone", "tile.blockCosmeticSolid.12.name": "Ancient Rock",
                      "tile.blockCosmeticSolid.14.name": "Crusted Stone", "tile.blockWoodenDevice.6.name": "Greatwood Planks",
                      "tile.blockWoodenDevice.7.name": "Silverwood Planks", "tile.blockTaint.0.name": "Crusted Taint",
                      "tile.blockTaint.2.name": "Block of Flesh"}
    for key, value in expected_names.items():
        check(f"original lang {key} = {value}", original.get(key) == value, original.get(key, "<missing>"))

    modern = ("amber_block", "amber_bricks", "obsidian_tile", "tallow_block", "arcane_stone", "arcane_stone_bricks",
              "ancient_stone", "greatwood_planks", "silverwood_planks", "fibrous_taint", "tainted_flesh", "warded_glass")
    for block in modern:
        check(f"modern tc blockstates {block}", (TC_BLOCKSTATES / f"{block}.json").is_file())

    # Original whitelist metas intentionally absent from the modern whitelist (reported, not asserted,
    # because the port may grow these blocks independently).
    ported = lambda name: f"port has {'thaumcraft:' + name} blockstates" if (TC_BLOCKSTATES / f"{name}.json").is_file() else f"no {name} blockstates in port"
    NOTES.append("REPORT: Thaumium Block is included. Missing original counterparts - blockCosmeticSolid 12 (Ancient Rock): "
                 + ported("ancient_rock") + " (no such modern block, intentionally absent from the port); blockCosmeticSolid 14"
                 " (Crusted Stone): " + ported("crusted_stone") + " (no such modern block, intentionally absent from the port).")

    failed = 0
    width = max(len(name) for name, _, _ in CHECKS)
    for name, ok, detail in CHECKS:
        failed += not ok
        print(f"{'OK  ' if ok else 'FAIL'} {name:<{width}}  {detail}")
    for note in NOTES:
        print()
        print(note)
    print()
    if failed:
        print(f"BATCH2_EXTRAS_VALIDATION_FAILED checks={len(CHECKS)} failed={failed}")
        return 1
    print(f"BATCH2_EXTRAS_VALIDATED checks={len(CHECKS)} failed=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
