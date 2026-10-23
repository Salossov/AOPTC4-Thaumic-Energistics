"""Conversion-monitor frame variant, recipe and native language aliases."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT / 'src/main/resources'


def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    addon = PROJECT.parent
    assert hashlib.sha1((addon / 'thaumicenergistics-1.1.3.0.jar').read_bytes()).hexdigest() == 'a6240b09a0680f3e3246a6d0fe17650299533a84'
    refs = []
    for base in ('decompiled', 'source_code/src/main/java'):
        part = addon / base / 'thaumicenergistics/common/parts/PartEssentiaConversionMonitor.java'
        feature = addon / base / 'thaumicenergistics/common/features/FeatureEssentiaMonitoring.java'
        text = part.read_text()
        recipe = re.sub(r'\s+', ' ', feature.read_text())
        assert re.search(r'DOUBLE_CLICK_TICKS\s*=\s*(40L?|2\s*\*\s*20L)\s*;', text)
        assert 'darkCornerTexture = CableBusTextures.PartConversionMonitor_Colored' in text
        assert 'lightCornerTexture = CableBusTextures.PartConversionMonitor_Dark' in text
        assert 'CoalescenceCore, EssentiaStorageMonitor, DiffusionCore' in recipe
        refs.append({'part': str(part.relative_to(addon)), 'part_sha256': hashlib.sha256(part.read_bytes()).hexdigest(),
                     'feature_sha256': hashlib.sha256(feature.read_bytes()).hexdigest()})
    for state in ('off', 'on', 'empty_off', 'empty_on'):
        model = json.loads((ROOT / f'assets/thaumicenergistics/models/part/essentia_storage_monitor_{state}.json').read_text())
        model['textures']['dark'], model['textures']['light'] = model['textures']['light'], model['textures']['dark']
        write(f'assets/thaumicenergistics/models/part/essentia_conversion_monitor_{state}.json', model)
    write('assets/thaumicenergistics/models/item/essentia_conversion_monitor.json', {
        'parent': 'thaumicenergistics:part/essentia_conversion_monitor_empty_off',
        'textures': {'side': 'thaumicenergistics:blocks/parts/essentia.termina.border'}})
    write('data/thaumicenergistics/recipe/essentia_conversion_monitor.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'thaumicenergistics:coalescence_core'}, {'item': 'thaumicenergistics:essentia_storage_monitor'},
                        {'item': 'thaumicenergistics:diffusion_core'}],
        'result': {'id': 'thaumicenergistics:essentia_conversion_monitor', 'count': 1}})
    for path in (ROOT / 'assets/thaumicenergistics/lang').glob('*.json'):
        values = json.loads(path.read_text(encoding='utf-8'))
        key = 'thaumicenergistics.aeparts.essentia.conversion.monitor.name'
        if key in values:
            values['item.thaumicenergistics.essentia_conversion_monitor'] = values[key]
            write(str(path.relative_to(ROOT)), values)
    (addon / 'analysis/imported-conversion-monitor.json').write_text(json.dumps({
        'references': refs, 'idle_ae': .0625, 'double_click_ticks': 40,
        'approved_fix': 'Phial deposits require all eight units before consuming one phial.',
        'adaptations': ['Modern AE2 removed separate legacy INJECT/EXTRACT security; use Platform.hasPermissions (Level.mayInteract) and player action source.',
                        'Fill results drop beside the part as in the release; user also approved dropping unaccepted deposit-result containers on full inventory.',
                        'Modern frame textures use the storage-monitor replacement set with the original dark/light swap.']}, indent=2) + '\n', encoding='utf-8')
    print('THE_RELEASE_CONVERSION_MONITOR_IMPORTED')


if __name__ == '__main__':
    main()
