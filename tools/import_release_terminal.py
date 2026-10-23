"""Original terminal geometry, recipe and aliases; reference material remains outside Git."""
from pathlib import Path
import hashlib
import json
import re

PROJECT=Path(__file__).resolve().parents[1]
ROOT=PROJECT/'src/main/resources'


def write(path,value):
    target=ROOT/path;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    addon=PROJECT.parent
    assert hashlib.sha1((addon/'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest()=='a6240b09a0680f3e3246a6d0fe17650299533a84'
    refs=[]
    for base in ('decompiled','source_code/src/main/java'):
        part=addon/base/'thaumicenergistics/common/parts/PartEssentiaTerminal.java'
        feature=addon/base/'thaumicenergistics/common/features/FeatureEssentiaMonitoring.java'
        text=part.read_text();recipe=re.sub(r'\s+',' ',feature.read_text())
        assert '0.5' in text and '"sortMode"' in text and '"ViewMode"' in text and '"slots"' in text
        assert 'cdi.IlluminatedPanel, DiffusionCore, CoalescenceCore, cdi.LogicProcessor, cdi.VisFilter' in recipe
        for aspect,amount in [('WATER',5),('ORDER',2),('FIRE',1)]:
            assert re.search(r'etAspectList.add\(\s*Aspect.'+aspect+r'\s*,\s*'+str(amount)+r'\s*\)',feature.read_text())
        refs.append({'part':str(part.relative_to(addon)),'part_sha256':hashlib.sha256(part.read_bytes()).hexdigest(),
                     'feature_sha256':hashlib.sha256(feature.read_bytes()).hexdigest()})
    faces=('north','south','east','west','up','down')
    for rotation in range(4):
        for active in (False,True):
            elements=[]
            for lower,upper in (([2,2,0],[14,14,2]),([4,4,2],[12,12,3]),([5,5,3],[11,11,4])):
                elements.append({'from':lower,'to':upper,'faces':{face:{'texture':'#side'} for face in faces}})
            bright={'neoforge_data':{'block_light':15,'sky_light':15}} if active else {}
            elements[0]['faces']['north']={'texture':'#border',**bright}
            for index,name in enumerate(('dark','medium','light')):
                elements.append({'from':[3,3,-.001*(index+1)],'to':[13,13,-.001*(index+1)],'shade':False,
                                 'faces':{'north':{'texture':'#'+name,'tintindex':index+1,'uv':[3,3,13,13],
                                                   'rotation':(0,90,270,180)[rotation],**bright}}})
            # Existing native bus-light sprites replace the legacy AE2 bus light rendering.
            for face in ('east','west','up','down'):
                elements[2]['faces'][face]={'texture':'#status','tintindex':2,**bright}
            write(f'assets/thaumicenergistics/models/part/essentia_terminal_{rotation}_{"on" if active else "off"}.json',{
                'parent':'minecraft:block/block','textures':{
                    'side':'thaumicenergistics:blocks/parts/essentia.terminal.side',
                    'particle':'thaumicenergistics:blocks/parts/essentia.terminal.side',
                    'border':'thaumicenergistics:blocks/parts/essentia.termina.border',
                    'dark':'thaumicenergistics:blocks/parts/essentia.terminal.overlay.dark',
                    'medium':'thaumicenergistics:blocks/parts/essentia.terminal.overlay.medium',
                    'light':'thaumicenergistics:blocks/parts/essentia.terminal.overlay.light',
                    'status':'thaumicenergistics:blocks/parts/bus.color.light' if active else 'thaumicenergistics:blocks/parts/bus.color.dark'},
                'elements':elements})
    write('assets/thaumicenergistics/models/item/essentia_terminal.json',{'parent':'thaumicenergistics:part/essentia_terminal_0_on'})
    write('data/thaumicenergistics/recipe/essentia_terminal.json',{'type':'thaumcraft:arcane_shapeless','research':'thaumicenergistics:teessterm',
        'vis':{'aqua':5,'ordo':2,'ignis':1},'ingredients':[{'tag':'ae2:illuminated_panel'},{'item':'thaumicenergistics:diffusion_core'},
        {'item':'thaumicenergistics:coalescence_core'},{'item':'ae2:logic_processor'},{'item':'thaumcraft:vis_filter'}],
        'result':{'id':'thaumicenergistics:essentia_terminal','count':1}})
    for path in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
        values=json.loads(path.read_text(encoding='utf-8'));key='thaumicenergistics.aeparts.essentia.terminal.name'
        if key in values:values['item.thaumicenergistics.essentia_terminal']=values[key]
        if path.stem=='en_us':
            # Exact AE2 rv3 en_US labels; removed from modern AE2's localization.
            values['thaumicenergistics.gui.craft.small']='Craft';values['thaumicenergistics.gui.craft.large']='+'
        write(str(path.relative_to(ROOT)),values)
    legacy=addon/'analysis/ae2_reference/legacy/en_US.lang'
    assert 'gui.appliedenergistics2.SmallFontCraft=Craft' in legacy.read_text()
    assert 'gui.appliedenergistics2.LargeFontCraft=+' in legacy.read_text()
    (addon/'analysis/imported-terminal.json').write_text(json.dumps({'references':refs,'idle_ae':.5,'work_threshold':3,'work_budget':64,
        'legacy_craft_labels_sha256':hashlib.sha256(legacy.read_bytes()).hexdigest(),
        'legacy_craft_labels_source':'https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/rv3-1.7.10/src/main/resources/assets/appliedenergistics2/lang/en_US.lang',
        'research_progression_pending':True,'wireless_cell_terminal_pending':True,
        'notes':['Label SIMULATE and MODULATE each count one work unit; retain 32 labels per cycle.',
                 'Crafting menus use native AE2 ISubMenuHost and the real essentia key; crafting providers remain later scope.',
                 'Modern bus-light sprites and quad UV rotations adapt removed legacy rendering APIs.']},indent=2)+'\n',encoding='utf-8')
    print('THE_RELEASE_ESSENTIA_TERMINAL_IMPORTED')


if __name__=='__main__':main()
