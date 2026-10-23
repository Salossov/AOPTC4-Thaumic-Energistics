"""Check the built artifact against release assets and record dependency/test provenance."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from audit_client_aspects import audit_aspects

PROJECT = Path(__file__).resolve().parents[1]
WORKSPACE = PROJECT.parents[2]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def client_evidence(code_files: list[Path]) -> dict:
    result_path = PROJECT / "runs/client/thaumicenergistics-smoke/result.json"
    receipt_path = PROJECT / "build/test-results/client-smoke-inputs.json"
    review_path = PROJECT.parent / "analysis/client-menu-review.json"
    if not receipt_path.is_file():
        return {"automation_passed": False, "visual_reviewed": False}
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if not receipt.get("completed") or receipt.get("result_sha256") != sha256(result_path):
        raise SystemExit("Client receipt incomplete or belongs to another result")
    for name, entry in receipt["inputs"].items():
        if sha256(Path(entry["path"])) != entry["sha256"]:
            raise SystemExit(f"Client input rebuilt since verification: {name}; rerun client smoke")
    if any(path.stat().st_mtime_ns > result_path.stat().st_mtime_ns for path in code_files):
        raise SystemExit("Client smoke predates a Java source change; rerun client smoke")
    result = json.loads(result_path.read_text(encoding="utf-8"))
    screenshots = result.get("screenshots", [])
    checks = ("server_cursor_filter_packet", "storage_filter_retained_after_inverter", "inverter_tool_and_void_packets", "absolute_priority_real_payload",
              "backspace_at_typing_limit", "io_capacity_card_packet", "workbench_real_cell_and_partition_packets",
              "vis_drain_color_real_client_sync", "hidden_unfocused_window", "sound_playback_blocked", "aspect_palette_rendered", "linked_research_real_client_sync", "imported_research_real_client_sync",
              "emitter_menu_real_packets", "emitter_world_flare_synced")
    if not result.get("automation_passed") or len(screenshots) < 19 or not all(result.get(key) is True for key in checks):
        raise SystemExit("Client smoke failed or screenshots missing")
    hashes = {str(Path(path).resolve()): sha256(Path(path)) for path in screenshots}
    review = json.loads(review_path.read_text(encoding="utf-8")) if review_path.is_file() else {}
    reviewed = (review.get("result_sha256") == sha256(result_path)
                and review.get("screenshots") == hashes and review.get("passed") is True)
    return {"automation_passed": True, "visual_reviewed": reviewed,
            "result_sha256": sha256(result_path), "screenshots": hashes,
            "vis_link_drain_color_synced": result["vis_drain_color_real_client_sync"],
            "hidden_unfocused_window": result["hidden_unfocused_window"], "sound_playback_blocked": result["sound_playback_blocked"],
            "aspect_pixels": audit_aspects(PROJECT,result,Path(receipt["inputs"]["core"]["path"])),
            "scope": "storage bus, priority editor, import/export menus, cell workbench, essentia level emitter menu/world flare, Water Vis-interface and seven-aspect color/alpha palette at GUI scale 2"}


def main() -> None:
    artifact = PROJECT / "build/libs/thaumicenergistics-1.21.1-1.1.3.0-port.1.jar"
    reference = json.loads((PROJECT.parent / "analysis/imported-resources.json").read_text(encoding="utf-8"))
    recipes = json.loads((PROJECT.parent / "analysis/imported-recipes.json").read_text(encoding="utf-8"))["recipes"]
    with zipfile.ZipFile(artifact) as jar:
        forbidden = [name for name in jar.namelist() if name.startswith(("thaumcraft/", "appeng/", "docs/", "tools/", "thaumicenergistics-smoke"))
                     or name.startswith("data/thaumicenergistics/thaumcraft/qa_")
                     or any(part in name for part in ("verification/", "StorageRulesTest", "decompiled/", "source_code/"))]
        changed = [entry["path"] for entry in reference["assets"]
                   if hashlib.sha256(jar.read(entry["path"])).hexdigest() != entry["sha256"]]
        invalid_paths = [name for name in jar.namelist() if name.startswith("assets/") and name != name.lower()]
        stale_recipes = [entry["output"] for entry in recipes if jar.read(entry["output"]) != (PROJECT / "src/main/resources" / entry["output"]).read_bytes()]
        if forbidden or changed or invalid_paths or stale_recipes:
            raise SystemExit(f"Invalid artifact: forbidden={forbidden}, changed_assets={changed}, invalid_paths={invalid_paths}, stale_recipes={stale_recipes}")
        if "LICENSE" not in jar.namelist() or "NOTICE.md" not in jar.namelist():
            raise SystemExit("Missing license or original credits")
    report_path = PROJECT / "build/test-results/gametest.xml"
    provenance = json.loads((PROJECT / "build/test-results/native-inputs.json").read_text(encoding="utf-8"))
    if not provenance.get("completed") or provenance.get("report_sha256") != sha256(report_path):
        raise SystemExit("Native dependency provenance is incomplete or belongs to another report")
    for name, entry in provenance["inputs"].items():
        if sha256(Path(entry["path"])) != entry["sha256"]:
            raise SystemExit(f"Native input rebuilt since verification: {name}; rerun GameTests")
    code_files = [path for source in ("src/main/java", "src/verification/java")
                  for path in (PROJECT / source).rglob("*.java")]
    if any(path.stat().st_mtime_ns > report_path.stat().st_mtime_ns for path in code_files):
        raise SystemExit("Native test report predates a Java source change; run GameTests again")
    tests = ET.parse(report_path).getroot()
    cases = list(tests.iter("testcase"))
    if not cases or list(tests.iter("failure")) or list(tests.iter("error")):
        raise SystemExit("GameTest report missing, empty or failed")
    report = {
        "artifact": artifact.name, "artifact_sha256": sha256(artifact),
        "identical_release_assets": len(reference["assets"]), "forbidden_entries": forbidden,
        "renamed_release_assets": sum(entry.get("original_path",entry["path"]) != entry["path"] for entry in reference["assets"]),
        "source_release_equivalent_recipes": len(recipes), "research_progression_verified": False,
        "implemented_research_scope": {
            "ordinary_nodes": ["teresearch", "tecores", "testorage", "teio"],
            "native_links": ["DISTILESSENTIA", "WARDEDARCANA", "TUBEFILTER"],
            "arcane_gate_and_note_creation_tested": any(case.attrib.get("name") == "nativeimportedresearchandarcanegate" for case in cases),
            "puzzle_completion_tested": False,
            "configuration_branches_tested": False,
        },
        "core_sha256": sha256(WORKSPACE / "repo/mod/build/libs/thaumcraft-1.21.1-1.0.0.jar"),
        "api_sha256": sha256(WORKSPACE / "repo/api/build/libs/thaumcraft-api-1.0.0.jar"),
        "native_report_sha256": sha256(report_path), "native_tests": len(cases),
        "test_names": [case.attrib["name"] for case in cases],
        "implemented_menus_client": client_evidence(code_files),
        "full_mod_client_verified": False,
        "full_port_complete": False,
    }
    output = PROJECT.parent / "analysis/release-audit.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
