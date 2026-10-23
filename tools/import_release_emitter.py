"""Generate level emitter models from release geometry and retain its arcane gate."""
from pathlib import Path
import hashlib
import json

PROJECT=Path(__file__).resolve().parents[1]
ROOT=PROJECT/"src/main/resources"


def write(path: str,value: object) -> None:
    target=ROOT/path
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n",encoding="utf-8",newline="\n")


def main() -> None:
    release=PROJECT.parent/"thaumicenergistics-1.1.3.0.jar"
    if hashlib.sha1(release.read_bytes()).hexdigest()!="a6240b09a0680f3e3246a6d0fe17650299533a84":raise ValueError("Wrong release")
    for state in ("off","on"):
        elements=[]
        for lower,upper,texture in (([7,7,3],[9,9,5],"base"),([7,7,1],[9,9,3],"head")):
            elements.append({"from":lower,"to":upper,"faces":{face:{"texture":"#"+texture} for face in ("north","south","east","west","up","down")}})
        if state=="on":
            # Release renderStatic adds full-bright active faces at x/y 6.999..9.001 by 6..10, z12..16;
            # AE2 1.21 part models face north, so legacy z maps to 16-z.
            bright={"neoforge_data":{"block_light":15,"sky_light":15}}
            elements.append({"from":[6.999,6,0],"to":[9.001,10,4],"shade":False,"faces":{
                face:{"texture":"#head",**bright} for face in ("east","west")}})
            elements.append({"from":[6,6.999,0],"to":[10,9.001,4],"shade":False,"faces":{
                face:{"texture":"#head",**bright} for face in ("up","down")}})
        write(f"assets/thaumicenergistics/models/part/essentia_level_emitter_{state}.json",{
            "parent":"minecraft:block/block","textures":{
                "base":"thaumicenergistics:blocks/parts/essentia.level.emitter.base",
                "head":"thaumicenergistics:blocks/parts/essentia.level.emitter."+("active" if state=="on" else "inactive"),
                "particle":"thaumicenergistics:blocks/parts/essentia.level.emitter.base"},"elements":elements})
    write("assets/thaumicenergistics/models/item/essentia_level_emitter.json",{"parent":"thaumicenergistics:part/essentia_level_emitter_off"})
    write("data/thaumicenergistics/recipe/essentia_level_emitter.json",{"type":"thaumcraft:arcane_shapeless",
        "research":"thaumicenergistics:teessterm","vis":{"ignis":4},
        "ingredients":[{"item":"ae2:calculation_processor"},{"item":"minecraft:redstone_torch"},{"item":"thaumcraft:salis_mundus"}],
        "result":{"id":"thaumicenergistics:essentia_level_emitter","count":4}})
    for language in (ROOT/"assets/thaumicenergistics/lang").glob("*.json"):
        values=json.loads(language.read_text(encoding="utf-8"))
        values["item.thaumicenergistics.essentia_level_emitter"]=values["thaumicenergistics.aeparts.essentia.levelemitter.name"]
        write(str(language.relative_to(ROOT)),values)
    print("THE_RELEASE_EMITTER_IMPORTED output=4 research=teessterm costs=ignis4")


if __name__=="__main__":main()
