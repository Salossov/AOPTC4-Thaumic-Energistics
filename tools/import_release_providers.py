"""Author provider models/recipes from the pinned source and release registrations."""
from pathlib import Path
import copy,hashlib,json,re
from import_release_research import ASPECTS,write

PROJECT=Path(__file__).resolve().parents[1]
ADDON=PROJECT.parent
ROOT=PROJECT/'src/main/resources'
ITEMS={'cdi.FilterTube':'thaumcraft:filtered_essentia_tube','cdi.SalisMundus':'thaumcraft:salis_mundus',
       'CoalescenceCore':'thaumicenergistics:coalescence_core','DiffusionCore':'thaumicenergistics:diffusion_core',
       'cdi.WaterShard':'thaumcraft:water_shard','cdi.AirShard':'thaumcraft:air_shard','cdi.EssentiaMirror':'thaumcraft:essentia_mirror'}


def crafting(path):
    text=re.sub(r'/\*.*?\*/|//[^\n]*','',path.read_text(encoding='utf-8'),flags=re.S)
    body=text.split('void registerCrafting(',1)[1].split('protected void registerPseudoParents',1)[0]
    costs={ASPECTS[a]:int(n) for a,n in re.findall(r'\.add\s*\(\s*Aspect\.(\w+)\s*,\s*(\d+)\s*\)',body)}
    components=re.search(r'ItemStack\s*\[\s*\]\s+\w+\s*=\s*(?:new\s+ItemStack\s*\[\s*\]\s*)?\{(.*?)\}',body,re.S)[1]
    refs=[entry.strip() for entry in components.split(',') if entry.strip()]
    recipe=re.search(r'addInfusionCraftingRecipe\s*\(\s*this.researchKey\s*,\s*\w+\s*,\s*(\d+)\s*,\s*\w+\s*,\s*(cdi.\w+)',body)
    return {'aspects':costs,'components':refs,'instability':int(recipe[1]),'input':recipe[2]}


def main():
    assert hashlib.sha1((ADDON/'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest()=='a6240b09a0680f3e3246a6d0fe17650299533a84'
    report=[]
    for feature,kind,key in [('FeatureEssentiaProvider','essentia','teessprov'),('FeatureInfusionProvider','infusion','teinfprov')]:
        paths=[ADDON/base/f'thaumicenergistics/common/features/{feature}.java' for base in ('decompiled','source_code/src/main/java')]
        data=crafting(paths[0]);assert data==crafting(paths[1])
        assert data['input']=='cdi.MEInterface'
        modern=f'{kind}_provider'
        recipe={'type':'thaumcraft:infusion','research':f'thaumicenergistics:{key}',
                'result':{'id':f'thaumicenergistics:{modern}','count':1},'instability':data['instability'],'aspects':data['aspects'],
                'input':{'item':'ae2:interface'},'components':[{'item':ITEMS[ref]} for ref in data['components']]}
        if kind=='infusion':
            recipe['neoforge:conditions']=[{'type':'thaumcraft:mirrors_enabled'}]
            alternate=copy.deepcopy(recipe)
            alternate['neoforge:conditions']=[{'type':'neoforge:not','value':{'type':'thaumcraft:mirrors_enabled'}}]
            alternate['components']=[{'type':'thaumicenergistics:empty_jar'} if component.get('item')=='thaumcraft:essentia_mirror' else component for component in alternate['components']]
            write('data/thaumicenergistics/recipe/infusion_provider_no_mirrors.json',alternate)
        write(f'data/thaumicenergistics/recipe/{modern}.json',recipe)
        write(f'assets/thaumicenergistics/models/block/{modern}.json',{'parent':'minecraft:block/cube_all','ambientocclusion':False,
            'textures':{'all':f'thaumicenergistics:blocks/{kind}.provider','particle':f'thaumicenergistics:blocks/{kind}.provider'+('.overlay' if kind=='infusion' else '')},
            'elements':[{'from':[0,0,0],'to':[16,16,16],'shade':False,
                         'faces':{face:{'texture':'#all','cullface':face} for face in ('down','up','north','south','west','east')}}]})
        write(f'assets/thaumicenergistics/models/item/{modern}.json',{'parent':f'thaumicenergistics:block/{modern}'})
        write(f'assets/thaumicenergistics/blockstates/{modern}.json',{'variants':{'':{'model':f'thaumicenergistics:block/{modern}'}}})
        for language in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
            values=json.loads(language.read_text(encoding='utf-8'))
            legacy=f'thaumicenergistics.block.{kind}.provider.name'
            if legacy in values:values[f'block.thaumicenergistics.{modern}']=values[legacy]
            write(str(language.relative_to(ROOT)),values)
        report.append({'feature':feature,'references':[{'path':str(path.relative_to(ADDON)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()} for path in paths],
                       'crafting':data,'recipe':modern,'implemented_not_verified':True})
    (ADDON/'analysis/imported-providers.json').write_text(json.dumps({'providers':report,'idle_ae':[3,5],'channel_required':True,'transfer_ae_per_unit':.3,
        'pipe_suction':8,'pipe_min_suction':1,'pipe_ticks':{'idle':15,'after_accepted_insertion':5},'infusion_atomic_request':True,
        'color':'legacy cable-selected color; explicit recolor forces permanently; placement records clicked-face opposite',
        'source_quirks':['extraction returns first simulated amount','no additional channel gate inside legacy provider transfer path','infusion source advertises null inventory and never accepts insertion'],
        'adaptations':['modern public essentia capabilities','loaded-chunk guards','native AE2 storage watcher totals converted to signed deltas for optional CC','weak own listeners released on unload/removal','translucent overlay offset .0001 to avoid modern depth flicker'],
        'verification':'deferred until full port implementation at explicit user request'},indent=2)+'\n',encoding='utf-8')
    print('THE_PROVIDER_RESOURCES_AUTHORED providers=2 mirror_and_jar_recipes=3 verification_deferred=true')


if __name__=='__main__':main()
