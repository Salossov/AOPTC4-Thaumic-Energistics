"""Author chamber resources from the pinned release, adapting AE2's current orientation format."""
from pathlib import Path
import hashlib,json,re,zipfile

PROJECT=Path(__file__).resolve().parents[1]
ADDON=PROJECT.parent
ROOT=PROJECT/'src/main/resources'


def write(path,value):
    target=ROOT/path;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    assert hashlib.sha1((ADDON/'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest()=='a6240b09a0680f3e3246a6d0fe17650299533a84'
    refs=[]
    for base in ('decompiled','source_code/src/main/java'):
        root=ADDON/base/'thaumicenergistics/common'
        for relative in ('features/FeatureEssentiaVibrationChamber.java','tiles/TileEssentiaVibrationChamber.java','tiles/abstraction/TileEVCBase.java','blocks/BlockEssentiaVibrationChamber.java','items/ItemBlockEssentiaVibrationChamber.java'):
            path=root/relative;refs.append({'path':str(path.relative_to(ADDON)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        feature=re.sub(r'\s+',' ',(root/'features/FeatureEssentiaVibrationChamber.java').read_text())
        assert 'cdi.VibrationChamber, DiffusionCore, cdi.WardedJar' in feature
        for aspect,amount in [('WATER',2),('ENTROPY',4),('FIRE',7),('ORDER',4)]:
            assert re.search(r'chamberAspects.add\(\s*Aspect.'+aspect+r'\s*,\s*'+str(amount)+r'\s*\)',feature)
    ae2=PROJECT.parents[2]/'.cache/gradle/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/19.2.18/bcd04bc7f181b2ffa314625f284c8eca6fee7fe4/appliedenergistics2-19.2.18.jar'
    with zipfile.ZipFile(ae2) as z:orientation=json.loads(z.read('assets/ae2/blockstates/vibration_chamber.json'))['variants']
    variants={}
    for active,name in enumerate(('off','ignis','potentia')):
        textures={face:'thaumicenergistics:blocks/e.vibration.input' for face in ('down','up','north','south','west','east')}
        textures.update(north='thaumicenergistics:blocks/e.vibration.face.'+name,particle='thaumicenergistics:blocks/e.vibration.input')
        model='thaumicenergistics:block/essentia_vibration_chamber_'+name
        write('assets/thaumicenergistics/models/block/essentia_vibration_chamber_'+name+'.json',{'parent':'minecraft:block/cube','textures':textures})
        for state,transform in orientation.items():
            if not state.startswith('active=false,'):continue
            value={key:entry for key,entry in transform.items() if key!='model'};value['model']=model
            variants[state.replace('active=false',f'burning={active}')]=value
    write('assets/thaumicenergistics/blockstates/essentia_vibration_chamber.json',{'variants':variants})
    # Original inventory getIcon(side,metadata) places the off face on WEST.
    item_textures={face:'thaumicenergistics:blocks/e.vibration.input' for face in ('down','up','north','south','west','east')}
    item_textures.update(west='thaumicenergistics:blocks/e.vibration.face.off',particle='thaumicenergistics:blocks/e.vibration.input')
    write('assets/thaumicenergistics/models/item/essentia_vibration_chamber.json',{'parent':'minecraft:block/cube','textures':item_textures})
    write('data/thaumicenergistics/recipe/essentia_vibration_chamber.json',{'type':'thaumcraft:arcane_shapeless','research':'thaumicenergistics:teessvibcmbr','vis':{'aqua':2,'perditio':4,'ignis':7,'ordo':4},'ingredients':[{'item':'ae2:vibration_chamber'},{'item':'thaumicenergistics:diffusion_core'},{'type':'thaumicenergistics:empty_jar'}],'result':{'id':'thaumicenergistics:essentia_vibration_chamber','count':1}})
    for path in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
        values=json.loads(path.read_text(encoding='utf-8'));key='thaumicenergistics.block.essentia.vibration.chamber.name'
        if key in values:values['block.thaumicenergistics.essentia_vibration_chamber']=values[key]
        write(str(path.relative_to(ROOT)),values)
    (ADDON/'analysis/imported-chamber.json').write_text(json.dumps({'references':refs,'ae2_orientation_jar_sha256':hashlib.sha256(ae2.read_bytes()).hexdigest(),'variants':len(variants),'stored_capacity':64,'fuel_rules':{'ignis':{'coal_time_divisor':2,'ae_per_processing_tick':5},'potentia':{'coal_time_divisor':1.6,'division':'float32 then int','ae_per_processing_tick':8}},'processing_speed':{'min':20,'max':200,'divisor':100},'ticking':{'min':10,'max':40},'light':12,'suction':'full=0; int(128F*(1F-stored*.75F/64F)); empty switches by serverTick%200>100','approved_fixes':['reject nonfuel aspects before mutation','container add returns unaccepted remainder'],'implementation_verified':False,'verification_stage':'deferred until full port implemented by explicit user request'},indent=2)+'\n',encoding='utf-8')
    print('THE_CHAMBER_RESOURCES_AUTHORED variants=72 verification_deferred=true')


if __name__=='__main__':main()
