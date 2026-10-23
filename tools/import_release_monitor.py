"""Storage-monitor geometry, recipe and translations from the pinned release."""
from pathlib import Path
import hashlib
import json
import re

PROJECT=Path(__file__).resolve().parents[1]
ROOT=PROJECT/'src/main/resources'


def write(path,value):
    p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    addon=PROJECT.parent
    assert hashlib.sha1((addon/'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest()=='a6240b09a0680f3e3246a6d0fe17650299533a84'
    refs=[]
    for base in ('decompiled','source_code/src/main/java'):
        part=addon/base/'thaumicenergistics/common/parts/PartEssentiaStorageMonitor.java'
        feature=addon/base/'thaumicenergistics/common/features/FeatureEssentiaMonitoring.java'
        text=part.read_text();recipe=re.sub(r'\s+',' ',feature.read_text())
        # Fail if these source contracts drift; emitted geometry maps legacy z to 16-z.
        assert '0.0625' in text and '"Locked"' in text and '"TrackedAspect"' in text
        assert 'EssentiaStorageMonitor' in recipe and 'EssentiaLevelEmitter, cdi.IlluminatedPanel' in recipe
        refs.append({'part':str(part.relative_to(addon)),'part_sha256':hashlib.sha256(part.read_bytes()).hexdigest(),
                     'feature_sha256':hashlib.sha256(feature.read_bytes()).hexdigest()})
    faces=('north','south','east','west','up','down')
    for active in (False,True):
        for empty in (False,True):
            elements=[]
            for lower,upper in (([2,2,0],[14,14,2]),([5,5,2],[11,11,3])):
                elements.append({'from':lower,'to':upper,'faces':{f:{'texture':'#side'} for f in faces}})
            bright={'neoforge_data':{'block_light':15,'sky_light':15}} if active else {}
            # Modern AE2 conversion frame textures replace its removed CableBusTextures sprites.
            for i,(texture,tint) in enumerate((('dark',1),('medium',2),('light',3))):
                elements.append({'from':[2,2,-.001*(i+1)],'to':[14,14,-.001*(i+1)],'shade':False,
                                 'faces':{'north':{'uv':[2,2,14,14],'texture':'#'+texture,'tintindex':tint,**bright}}})
            if empty:
                elements.append({'from':[2,2,-.004],'to':[14,14,-.004],'shade':False,
                                 'faces':{'north':{'uv':[2,2,14,14],'texture':'#empty','tintindex':2,**bright}}})
            name=('empty_' if empty else '')+('on' if active else 'off')
            write(f'assets/thaumicenergistics/models/part/essentia_storage_monitor_{name}.json',{
                'parent':'minecraft:block/block','textures':{'side':'thaumicenergistics:blocks/parts/arcane.crafting.side',
                'particle':'thaumicenergistics:blocks/parts/arcane.crafting.side','dark':'ae2:part/monitor_front',
                'medium':'ae2:part/conversion_monitor_medium','light':'ae2:part/conversion_monitor_bright',
                'empty':'thaumicenergistics:blocks/parts/essentia.terminal.overlay.dark'},'elements':elements})
    write('assets/thaumicenergistics/models/item/essentia_storage_monitor.json',{'parent':'thaumicenergistics:part/essentia_storage_monitor_empty_off',
          'textures':{'side':'thaumicenergistics:blocks/parts/essentia.termina.border'}})
    write('data/thaumicenergistics/recipe/essentia_storage_monitor.json',{'type':'minecraft:crafting_shapeless','category':'misc',
        'ingredients':[{'item':'thaumicenergistics:essentia_level_emitter'},{'tag':'ae2:illuminated_panel'}],
        'result':{'id':'thaumicenergistics:essentia_storage_monitor','count':1}})
    for p in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
        values=json.loads(p.read_text(encoding='utf-8'))
        if 'thaumicenergistics.aeparts.essentia.storage.monitor.name' not in values:continue
        values['item.thaumicenergistics.essentia_storage_monitor']=values['thaumicenergistics.aeparts.essentia.storage.monitor.name']
        write(str(p.relative_to(ROOT)),values)
    (addon/'analysis/imported-monitor.json').write_text(json.dumps({'references':refs,'idle_ae':.0625,
        'deviations':['Removed legacy AE2 CableBusTextures frame sprites use modern AE2 conversion-monitor frame textures.','AE2 storage watcher may update selected totals on the next storage tick.'],
        'recipe':'One level emitter plus any AE2 illuminated_panel variant; output one.'},indent=2)+'\n',encoding='utf-8')
    print('THE_RELEASE_STORAGE_MONITOR_IMPORTED')


if __name__=='__main__':main()
