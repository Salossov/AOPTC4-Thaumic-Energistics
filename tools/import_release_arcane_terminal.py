"""Author original arcane terminal resources; gameplay verification is deferred."""
from pathlib import Path
import hashlib,json,re

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
        feature=ADDON/base/'thaumicenergistics/common/features/FeatureACT.java'
        text=re.sub(r'\s+',' ',feature.read_text(encoding='utf-8'))
        assert 'cdi.METerminal, cdi.ArcaneWorkTable, cdi.CalculationProcessor' in text
        for aspect in ('AIR','EARTH','ENTROPY','FIRE','ORDER','WATER'):
            assert re.search(r'actAspectList.add\(\s*Aspect.'+aspect+r'\s*,\s*10\s*\)',text)
        refs.append({'path':str(feature.relative_to(ADDON)),'sha256':hashlib.sha256(feature.read_bytes()).hexdigest()})
    faces=('north','south','east','west','up','down')
    for rotation in range(4):
        for active in (False,True):
            bright={'neoforge_data':{'block_light':15,'sky_light':15}}
            elements=[{'from':low,'to':high,'faces':{face:{'texture':'#side'} for face in faces}}
                      for low,high in (([2,2,0],[14,14,2]),([5,5,2],[11,11,3]))]
            elements[0]['faces']['north']={'texture':'#base','rotation':(0,90,270,180)[rotation]}
            if active:
                for layer,(name,tint) in enumerate((('dark',1),('medium',2),('runes',4))):
                    elements.append({'from':[2,2,-.001*(layer+1)],'to':[14,14,-.001*(layer+1)],'shade':False,
                        'faces':{'north':{'texture':'#'+name,'tintindex':tint,'uv':[2,2,14,14],'rotation':(0,90,270,180)[rotation],**bright}}})
            for face in ('east','west','up','down'):
                elements[1]['faces'][face]={'texture':'#status','tintindex':2,**(bright if active else {})}
            write(f'assets/thaumicenergistics/models/part/arcane_crafting_terminal_{rotation}_{"on" if active else "off"}.json',{
                'parent':'minecraft:block/block','textures':{'side':'thaumicenergistics:blocks/parts/arcane.crafting.side',
                    'particle':'thaumicenergistics:blocks/parts/arcane.crafting.side','base':'thaumicenergistics:blocks/parts/arcane.crafting.overlay1',
                    'dark':'thaumicenergistics:blocks/parts/arcane.crafting.overlay3','medium':'thaumicenergistics:blocks/parts/arcane.crafting.overlay2',
                    'runes':'thaumicenergistics:blocks/parts/arcane.crafting.overlay4',
                    'status':'thaumicenergistics:blocks/parts/bus.color.light' if active else 'thaumicenergistics:blocks/parts/bus.color.dark'},'elements':elements})
    # Inventory's dark border is black in the original rather than host color;
    # the own tint handler maps the standard transparent AE palette for icons.
    write('assets/thaumicenergistics/models/item/arcane_crafting_terminal.json',{'parent':'thaumicenergistics:part/arcane_crafting_terminal_0_on'})
    write('data/thaumicenergistics/recipe/arcane_crafting_terminal.json',{'type':'thaumcraft:arcane_shapeless','research':'thaumicenergistics:tearcaneterm',
        'vis':{aspect:10 for aspect in ('aer','terra','perditio','ignis','ordo','aqua')},
        'ingredients':[{'item':'ae2:terminal'},{'item':'thaumcraft:arcane_workbench'},{'item':'ae2:calculation_processor'}],
        'result':{'id':'thaumicenergistics:arcane_crafting_terminal','count':1}})
    for path in (ROOT/'assets/thaumicenergistics/lang').glob('*.json'):
        values=json.loads(path.read_text(encoding='utf-8'));key='thaumicenergistics.aeparts.arcane.crafting.terminal.name'
        if key in values:values['item.thaumicenergistics.arcane_crafting_terminal']=values[key]
        if path.stem in ('en_us','ru_ru'):
            labels=(['Search mode','Automatic focus','Manual focus','Automatic focus + recipe viewer','Manual focus + recipe viewer']
                    if path.stem=='en_us' else ['Режим поиска','Автоматический фокус','Ручной фокус','Автоматический фокус + просмотр рецептов','Ручной фокус + просмотр рецептов'])
            values['thaumicenergistics.gui.search_mode']=labels[0]
            for index,label in enumerate(labels[1:]):values[f'thaumicenergistics.gui.search_mode.{index}']=label
        write(str(path.relative_to(ROOT)),values)
    (ADDON/'analysis/imported-arcane-terminal.json').write_text(json.dumps({'references':refs,'implemented_unverified':True,
        'idle_ae':.5,'tick_request':[2,20],'light':9,'shared_inventory_slots':20,'gui':[230,243],
        'slot_layout':{'grid':[0,8],'wand':9,'result':10,'views':[11,15],'armor':[16,19]},
        'recipe_precedence':'vanilla before arcane','charge_units':'centivis','craft_vis_units':'whole vis before native quote',
        'approved_deviation':'Preserve remaining ingredient stack and return container when player inventory is full',
        'search_implemented_unverified':['RV3 regular expressions with literal fallback','@ mod ID and default-enabled tooltip fallback',
            'RV3 name/mod/amount comparison and craftable-only zero-count ordering','four focus/viewer modes through native AE2 flags and ItemListMod'],
        'aspect_hover':'native item tooltip path invokes core ScanUi with public aspect/knowledge settings; no reflection or new core API',
        'pending_review':['legacy SEARCH_TOOLTIPS off config seam (AE2 19 removed it)','inventory icon palette and rune layer brightness',
            'real viewer synchronization and focus/cursor/resize behavior','full server/native/client/artifact verification'],
        'verification':'deferred until full agreed port implemented'},indent=2)+'\n',encoding='utf-8')
    print('THE_ARCANE_TERMINAL_RESOURCES_AUTHORED verification_deferred=true')


if __name__=='__main__':main()
