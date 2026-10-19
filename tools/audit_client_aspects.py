"""Compare real Minecraft framebuffer pixels with the runtime aspect PNGs, tint and alpha."""
from io import BytesIO
from pathlib import Path
import hashlib
import json
import zipfile
from PIL import Image


def audit_aspects(project: Path, result: dict, core: Path) -> dict:
    screenshots = [Path(path) for path in result["screenshots"] if Path(path).name.endswith("-aspect-palette.png")]
    samples = result.get("aspect_palette", [])
    expected_tags = {"aer","terra","ignis","aqua","ordo","perditio","praecantatio"}
    if len(screenshots) != 1 or len(samples) != 7 or {a["tag"] for a in samples} != expected_tags or not result.get("aspect_palette_rendered"):
        raise SystemExit("Missing real-client aspect palette evidence")
    scale = result["gui_scale"]
    if scale != int(scale) or scale < 1:
        raise SystemExit("Pixel oracle requires an integer GUI scale")
    scale = int(scale)
    image = Image.open(screenshots[0]).convert("RGB")
    report = {"screenshot_sha256": hashlib.sha256(screenshots[0].read_bytes()).hexdigest(),
              "core_sha256": hashlib.sha256(core.read_bytes()).hexdigest(), "gui_scale": scale, "aspects": []}
    with zipfile.ZipFile(core) as jar:
        for sample in samples:
            namespace, resource = sample["texture"].split(":", 1)
            texture = Image.open(BytesIO(jar.read(f"assets/{namespace}/{resource}"))).convert("RGBA")
            color = sample["color"]
            tint = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
            wrong, max_error, transparent, opaque = [], 0, 0, 0
            for y in range(16 * scale):
                for x in range(16 * scale):
                    source = texture.getpixel((int((x+.5)*texture.width/(16*scale)), int((y+.5)*texture.height/(16*scale))))
                    background = (184,75,54) if (x//(4*scale)+y//(4*scale))%2 == 0 else (50,110,171)
                    alpha = source[3]/255
                    expected = tuple(round(source[c]*tint[c]/255*alpha+background[c]*(1-alpha)) for c in range(3))
                    actual = image.getpixel((sample["x"]*scale+x,sample["y"]*scale+y))
                    error = max(abs(actual[c]-expected[c]) for c in range(3))
                    max_error = max(max_error,error)
                    if source[3] == 0: transparent += 1
                    elif source[3] == 255: opaque += 1
                    if error > 3 and len(wrong) < 5:
                        wrong.append({"pixel": [x,y], "actual": actual, "expected": expected})
            if wrong or not transparent or not opaque:
                raise SystemExit(f"Aspect color/alpha mismatch for {sample['tag']}: max_error={max_error}, examples={wrong}")
            report["aspects"].append({"tag": sample["tag"], "color": color, "pixels": (16*scale)**2,
                                      "transparent_pixels": transparent, "opaque_pixels": opaque, "max_channel_error": max_error})
    report["passed"] = True
    # Check the real ghost slot before and after installing the inverter/allow-void toggle.
    # Palette-only success cannot catch a container overlay hiding the icon afterwards.
    report["storage_filters"] = []
    water = next(sample for sample in samples if sample["tag"] == "aqua")
    with zipfile.ZipFile(core) as jar:
        namespace, resource = water["texture"].split(":", 1)
        texture = Image.open(BytesIO(jar.read(f"assets/{namespace}/{resource}"))).convert("RGBA")
    background = Image.open(project/"src/main/resources/assets/thaumicenergistics/textures/gui/essentia.storage.bus.png").convert("RGBA")
    color = water["color"]
    tint = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
    for name in ("storage-filter", "storage-inverter-void"):
        path = next(Path(p) for p in result["screenshots"] if Path(p).name.endswith(f"-{name}.png"))
        frame = Image.open(path).convert("RGB")
        left, top = (frame.width//scale-246)//2, (frame.height//scale-184)//2
        max_error = 0
        for y in range(16*scale):
            for x in range(16*scale):
                source = texture.getpixel((int((x+.5)*texture.width/(16*scale)),int((y+.5)*texture.height/(16*scale))))
                base = background.getpixel((80+x//scale,40+y//scale))
                if base[3] != 255: raise SystemExit("Storage slot pixel oracle needs an opaque background")
                alpha = source[3]/255
                expected = tuple(round(source[c]*tint[c]/255*alpha+base[c]*(1-alpha)) for c in range(3))
                actual = frame.getpixel(((left+80)*scale+x,(top+40)*scale+y))
                max_error = max(max_error,max(abs(actual[c]-expected[c]) for c in range(3)))
        if max_error > 3: raise SystemExit(f"Storage filter hidden or tinted incorrectly in {name}: max_error={max_error}")
        report["storage_filters"].append({"name": name,"screenshot_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                                         "pixels": (16*scale)**2,"max_channel_error": max_error})
    (project.parent/"analysis/aspect-render-audit.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    return report


if __name__ == "__main__":
    project = Path(__file__).resolve().parents[1]
    result_path = project/"runs/client/thaumicenergistics-smoke/result.json"
    result = json.loads(result_path.read_text(encoding="utf-8"))
    receipt = json.loads((project/"build/test-results/client-smoke-inputs.json").read_text(encoding="utf-8"))
    if not receipt.get("completed") or not result.get("automation_passed") or receipt.get("result_sha256") != hashlib.sha256(result_path.read_bytes()).hexdigest():
        raise SystemExit("Client smoke receipt/result incomplete or mismatched")
    core = Path(receipt["inputs"]["core"]["path"])
    if hashlib.sha256(core.read_bytes()).hexdigest() != receipt["inputs"]["core"]["sha256"]:
        raise SystemExit("Core rebuilt since client smoke; rerun against current dependencies")
    report = audit_aspects(project,result,core)
    print(f"THE_CLIENT_ASPECT_PIXELS_PASS aspects={len(report['aspects'])} pixels={sum(a['pixels'] for a in report['aspects'])}")
