"""Pinned wireless item/recipe references and existing release assets; no decompile in Git."""
from pathlib import Path
import hashlib
import json
import re

PROJECT=Path(__file__).resolve().parents[1]
ADDON=PROJECT.parent
ROOT=PROJECT/'src/main/resources'


def write(path,value):
    target=ROOT/path;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    assert hashlib.sha1((ADDON/'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest()=='a6240b09a0680f3e3246a6d0fe17650299533a84'
    references=[]
    for base in ('decompiled','source_code/src/main/java'):
        paths=[ADDON/base/'thaumicenergistics/common'/name for name in ('items/ItemWirelessEssentiaTerminal.java','inventory/HandlerWirelessEssentiaTerminal.java','grid/EssentiaPassThroughMonitor.java','features/FeatureEssentiaMonitoring.java')]
        item,handler,power,feature=[p.read_text(encoding='utf-8') for p in paths]
        assert re.search(r'1600000(?:\.0)?',item)
        assert 'wireless_getDrainRate' in handler
        if 'EssentiaMonitor.AE_PER_ESSENTIA' in power:
            base_power=(ADDON/base/'thaumicenergistics/common/grid/EssentiaMonitor.java').read_text(encoding='utf-8')
            assert re.search(r'AE_PER_ESSENTIA\s*=\s*0\.3',base_power)
            power=power.replace('EssentiaMonitor.AE_PER_ESSENTIA','0.3')
        assert re.search(r'0\.3(?:D)?\s*\*\s*amount',power)
        assert 'cdi.WirelessReceiver, EssentiaTerminal, cdi.DenseCell' in re.sub(r'\s+',' ',feature)
        references.extend({'path':str(p.relative_to(ADDON)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths)
    write('assets/thaumicenergistics/models/item/wireless_essentia_terminal.json',{'parent':'minecraft:item/generated','textures':{'layer0':'thaumicenergistics:items/wireless.essentia.terminal'}})
    write('data/thaumicenergistics/recipe/wireless_essentia_terminal.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'ae2:wireless_receiver'},{'item':'thaumicenergistics:essentia_terminal'},{'item':'ae2:dense_energy_cell'}],'result':{'id':'thaumicenergistics:wireless_essentia_terminal','count':1}})
    for path in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
        values=json.loads(path.read_text(encoding='utf-8'));key='thaumicenergistics.item.wireless.essentia.terminal.name'
        if key in values:values['item.thaumicenergistics.wireless_essentia_terminal']=values[key]
        write(str(path.relative_to(ROOT)),values)
    (ADDON/'analysis/imported-wireless.json').write_text(json.dumps({'references':references,'capacity_ae':1600000,'essentia_unit_cost':.3,'initial_distance_multiplier':1,'initial_idle_counter':1,'idle_threshold':10,'default_recipe':'wireless receiver + essentia terminal + dense energy cell','approved_adaptations':['native GridLinkables/GlobalPos AP binding','initial AP search when accessPoint=null'],'config_branch_pending':True},indent=2)+'\n',encoding='utf-8')
    print('THE_RELEASE_WIRELESS_IMPORTED references=8 capacity=1600000')


if __name__=='__main__':main()
