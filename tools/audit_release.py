"""Check the built artifact against release assets and record dependency/test provenance."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
WORKSPACE = PROJECT.parents[2]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    artifact = PROJECT / "build/libs/thaumicenergistics-1.21.1-1.1.3.0-port.1.jar"
    reference = json.loads((PROJECT.parent / "analysis/imported-resources.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(artifact) as jar:
        forbidden = [name for name in jar.namelist() if name.startswith(("thaumcraft/", "appeng/", "docs/", "tools/"))
                     or any(part in name for part in ("verification/", "StorageRulesTest", "decompiled/", "source_code/"))]
        changed = [entry["path"] for entry in reference["assets"]
                   if hashlib.sha256(jar.read(entry["path"])).hexdigest() != entry["sha256"]]
        if forbidden or changed:
            raise SystemExit(f"Invalid artifact: forbidden={forbidden}, changed_assets={changed}")
        if "LICENSE" not in jar.namelist() or "NOTICE.md" not in jar.namelist():
            raise SystemExit("Missing license or original credits")
    report_path = PROJECT / "build/test-results/gametest.xml"
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
        "core_sha256": sha256(WORKSPACE / "repo/mod/build/libs/thaumcraft-1.21.1-1.0.0.jar"),
        "api_sha256": sha256(WORKSPACE / "repo/api/build/libs/thaumcraft-api-1.0.0.jar"),
        "native_report_sha256": sha256(report_path), "native_tests": len(cases),
        "test_names": [case.attrib["name"] for case in cases], "client_verified": False,
        "full_port_complete": False,
    }
    output = PROJECT.parent / "analysis/release-audit.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
