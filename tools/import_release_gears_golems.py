"""Pinned gears, gearboxes, and wireless golem backpack references and release assets."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
# Support both direct repo checkout and .worktree/<branch>
ADDON = PROJECT.parent if (PROJECT.parent / 'thaumicenergistics-1.1.3.0.jar').is_file() else PROJECT.parents[2]
ROOT = PROJECT / 'src/main/resources'


def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    jar_path = ADDON / 'thaumicenergistics-1.1.3.0.jar'
    assert jar_path.is_file(), f"Missing jar: {jar_path}"
    assert hashlib.sha1(jar_path.read_bytes()).hexdigest() == 'a6240b09a0680f3e3246a6d0fe17650299533a84'

    references = []
    rel_files = [
        'common/features/FeatureGearbox.java',
        'common/features/FeatureGolemBackpack.java',
        'common/tiles/TileGearBox.java',
        'common/items/ItemGolemWirelessBackpack.java',
        'common/items/ItemMaterial.java',
        'common/blocks/AbstractBlockGearBoxBase.java',
        'common/blocks/BlockGearBox.java',
        'common/blocks/BlockGolemGearBox.java',
        'common/entities/WirelessGolemHandler.java',
        'common/entities/AIAENetworkGolem.java',
        'common/integration/tc/GolemHooks.java',
        'common/integration/tc/GolemSyncRegistry.java',
        'common/integration/tc/GolemCoreType.java',
        'common/integration/tc/GolemUpgradeTypes.java',
        'client/render/model/ModelGearbox.java',
        'client/render/model/ModelGolemWifiBackpack.java',
        'client/render/RenderTileGearbox.java',
    ]

    for base in ('decompiled', 'source_code/src/main/java'):
        for rel in rel_files:
            file_path = ADDON / base / 'thaumicenergistics' / rel
            assert file_path.is_file(), f"Missing reference file: {file_path}"
            references.append({
                'path': str(file_path.relative_to(ADDON)),
                'sha256': hashlib.sha256(file_path.read_bytes()).hexdigest()
            })

    # Models - Items
    write('assets/thaumicenergistics/models/item/iron_gear.json', {
        'parent': 'minecraft:item/generated',
        'textures': {
            'layer0': 'thaumicenergistics:items/material.iron.gear'
        }
    })
    write('assets/thaumicenergistics/models/item/golem_wireless_backpack.json', {
        'parent': 'minecraft:item/generated',
        'textures': {
            'layer0': 'thaumicenergistics:items/golem.wifi.backpack'
        }
    })
    write('assets/thaumicenergistics/models/item/iron_gear_box.json', {
        'parent': 'thaumicenergistics:block/iron_gear_box'
    })
    write('assets/thaumicenergistics/models/item/thaumium_gear_box.json', {
        'parent': 'thaumicenergistics:block/thaumium_gear_box'
    })

    # Models - Blocks
    write('assets/thaumicenergistics/models/block/iron_gear_box.json', {
        'parent': 'minecraft:block/cube_all',
        'ambientocclusion': False,
        'textures': {
            'all': 'thaumicenergistics:blocks/gear.box.fallback',
            'particle': 'thaumicenergistics:blocks/gear.box.fallback'
        }
    })
    write('assets/thaumicenergistics/models/block/thaumium_gear_box.json', {
        'parent': 'minecraft:block/cube_all',
        'ambientocclusion': False,
        'textures': {
            'all': 'thaumicenergistics:blocks/golem.gear.box.fallback',
            'particle': 'thaumicenergistics:blocks/golem.gear.box.fallback'
        }
    })

    # Blockstates
    write('assets/thaumicenergistics/blockstates/iron_gear_box.json', {
        'variants': {
            '': {'model': 'thaumicenergistics:block/iron_gear_box'}
        }
    })
    write('assets/thaumicenergistics/blockstates/thaumium_gear_box.json', {
        'variants': {
            '': {'model': 'thaumicenergistics:block/thaumium_gear_box'}
        }
    })

    # Loot Tables
    write('data/thaumicenergistics/loot_table/blocks/iron_gear_box.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': 'thaumicenergistics:iron_gear_box'
            }],
            'conditions': [{
                'condition': 'minecraft:survives_explosion'
            }]
        }]
    })
    write('data/thaumicenergistics/loot_table/blocks/thaumium_gear_box.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:item',
                'name': 'thaumicenergistics:thaumium_gear_box'
            }],
            'conditions': [{
                'condition': 'minecraft:survives_explosion'
            }]
        }]
    })

    # Tags
    write('data/c/tags/item/gears.json', {
        'replace': False,
        'values': [
            'thaumicenergistics:iron_gear'
        ]
    })
    write('data/c/tags/item/gears/iron.json', {
        'replace': False,
        'values': [
            'thaumicenergistics:iron_gear'
        ]
    })

    # Recipes
    # 1. Iron Gear: " I ", " W ", "I I", 'I': iron ingot, 'W': wood gear / stick, Vis terra 1, ignis 1
    write('data/thaumicenergistics/recipe/iron_gear.json', {
        'type': 'thaumcraft:arcane',
        'research': 'thaumicenergistics:tegearbox',
        'vis': {
            'terra': 1,
            'ignis': 1
        },
        'pattern': [
            ' I ',
            ' W ',
            'I I'
        ],
        'key': {
            'I': {
                'tag': 'c:ingots/iron'
            },
            'W': [
                {'tag': 'c:gears/wooden'},
                {'item': 'minecraft:stick'}
            ]
        },
        'result': {
            'id': 'thaumicenergistics:iron_gear',
            'count': 1
        }
    })

    # 2. Iron Gear Box: "SGS", "GGG", "SGS", 'S': cobblestone, 'G': iron gear, Vis aer 2, ordo 2
    write('data/thaumicenergistics/recipe/iron_gear_box.json', {
        'type': 'thaumcraft:arcane',
        'research': 'thaumicenergistics:tegearbox',
        'vis': {
            'aer': 2,
            'ordo': 2
        },
        'pattern': [
            'SGS',
            'GGG',
            'SGS'
        ],
        'key': {
            'S': [
                {'tag': 'c:cobblestones'},
                {'item': 'minecraft:cobblestone'}
            ],
            'G': [
                {'tag': 'c:gears/iron'},
                {'item': 'thaumicenergistics:iron_gear'}
            ]
        },
        'result': {
            'id': 'thaumicenergistics:iron_gear_box',
            'count': 1
        }
    })

    # 3. Thaumium Gear Box: crucible conversion, catalyst iron gear box, aspects metallum 16, praecantatio 16
    write('data/thaumicenergistics/recipe/thaumium_gear_box.json', {
        'type': 'thaumcraft:crucible',
        'research': 'thaumicenergistics:tethaumiumgearbox',
        'catalyst': {
            'item': 'thaumicenergistics:iron_gear_box'
        },
        'aspects': {
            'metallum': 16,
            'praecantatio': 16
        },
        'result': {
            'id': 'thaumicenergistics:thaumium_gear_box',
            'count': 1
        }
    })

    # 4. Wireless Golem Backpack: arcane "TIT", "NWN", "FCF", Vis aer 6, ignis 4, ordo 3
    write('data/thaumicenergistics/recipe/golem_wireless_backpack.json', {
        'type': 'thaumcraft:arcane',
        'research': 'thaumicenergistics:tegolembackpack',
        'vis': {
            'aer': 6,
            'ignis': 4,
            'ordo': 3
        },
        'pattern': [
            'TIT',
            'NWN',
            'FCF'
        ],
        'key': {
            'T': [
                {'tag': 'c:ingots/thaumium'},
                {'item': 'thaumcraft:thaumium_ingot'}
            ],
            'I': [
                {'item': 'ae2:cable_interface'},
                {'tag': 'ae2:interface'}
            ],
            'N': [
                {'tag': 'thaumcraft:nitor'},
                {'item': 'thaumcraft:nitor'}
            ],
            'W': {
                'item': 'ae2:wireless_receiver'
            },
            'F': [
                {'tag': 'c:gems/fluix'},
                {'item': 'ae2:fluix_crystal'}
            ],
            'C': {
                'item': 'ae2:charger'
            }
        },
        'result': {
            'id': 'thaumicenergistics:golem_wireless_backpack',
            'count': 1
        }
    })

    # Pseudo-research nodes
    write('data/thaumicenergistics/research_templates/pseudo_coreuse.json', {
        'kind': 'research',
        'key': 'thaumicenergistics:pseudo_coreuse',
        'category': 'thaumicenergistics:main',
        'linked_research': 'COREUSE',
        'column': 5,
        'row': -6,
        'complexity': 1,
        'stub': True,
        'hidden': True,
        'icon': 'minecraft:air',
        'aspects': {}
    })
    write('data/thaumicenergistics/research_templates/pseudo_coregather.json', {
        'kind': 'research',
        'key': 'thaumicenergistics:pseudo_coregather',
        'category': 'thaumicenergistics:main',
        'linked_research': 'COREGATHER',
        'column': 0,
        'row': -5,
        'complexity': 1,
        'stub': True,
        'hidden': True,
        'icon': 'minecraft:air',
        'aspects': {}
    })

    # Research
    # 1. Iron Gearbox: column 4, row -5, aspects machina 6, metallum 4, permutatio 4, complexity 1, secondary true
    write('data/thaumicenergistics/thaumcraft/research/tegearbox.json', {
        'kind': 'research',
        'key': 'thaumicenergistics:tegearbox',
        'category': 'thaumicenergistics:main',
        'parents': [],
        'hidden_parents': [],
        'icon': 'thaumicenergistics:iron_gear',
        'column': 4,
        'row': -5,
        'aspects': {
            'machina': 6,
            'metallum': 4,
            'permutatio': 4
        },
        'complexity': 1,
        'auto_unlock': False,
        'concealed': False,
        'round': False,
        'text_pages': [
            'thaumicenergistics.research_page.TEIRONGEARBOX.1',
            'thaumicenergistics.research_page.TEIRONGEARBOX.2'
        ],
        'secondary': True,
        'special': False,
        'recipe_pages': [
            'thaumicenergistics:iron_gear',
            'thaumicenergistics:iron_gear_box'
        ],
        'recipe_groups': {},
        'page_order': [
            'thaumicenergistics.research_page.TEIRONGEARBOX.1',
            'thaumicenergistics.research_page.TEIRONGEARBOX.2',
            'thaumicenergistics:iron_gear',
            'thaumicenergistics:iron_gear_box'
        ]
    })

    # 2. Thaumium Gearbox: column 4, row -6, parent tegearbox, hidden COREUSE, aspects machina 10, praecantatio 8, metallum 5, complexity 1, secondary true
    write('data/thaumicenergistics/thaumcraft/research/tethaumiumgearbox.json', {
        'kind': 'research',
        'key': 'thaumicenergistics:tethaumiumgearbox',
        'category': 'thaumicenergistics:main',
        'parents': [
            'thaumicenergistics:tegearbox'
        ],
        'hidden_parents': [
            'thaumicenergistics:pseudo_coreuse'
        ],
        'icon': 'thaumicenergistics:thaumium_gear_box',
        'column': 4,
        'row': -6,
        'aspects': {
            'machina': 10,
            'praecantatio': 8,
            'metallum': 5
        },
        'complexity': 1,
        'auto_unlock': False,
        'concealed': False,
        'round': False,
        'text_pages': [
            'thaumicenergistics.research_page.TETHAUMGBOX.1'
        ],
        'secondary': True,
        'special': False,
        'recipe_pages': [
            'thaumicenergistics:thaumium_gear_box'
        ],
        'recipe_groups': {},
        'page_order': [
            'thaumicenergistics.research_page.TETHAUMGBOX.1',
            'thaumicenergistics:thaumium_gear_box'
        ]
    })

    # 3. Golem Wifi Backpack: column 0, row -6, concealed, complexity 3 (LARGE), parents tethaumiumgearbox + pseudo_coregather
    write('data/thaumicenergistics/thaumcraft/research/tegolembackpack.json', {
        'kind': 'research',
        'key': 'thaumicenergistics:tegolembackpack',
        'category': 'thaumicenergistics:main',
        'parents': [
            'thaumicenergistics:tethaumiumgearbox',
            'thaumicenergistics:pseudo_coregather'
        ],
        'hidden_parents': [],
        'icon': 'thaumicenergistics:golem_wireless_backpack',
        'column': 0,
        'row': -6,
        'aspects': {
            'potentia': 5,
            'auram': 5,
            'cognitio': 3,
            'tutamen': 3,
            'motus': 1,
            'permutatio': 1
        },
        'complexity': 3,
        'auto_unlock': False,
        'concealed': True,
        'round': False,
        'text_pages': [
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.1',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.2',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.3',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREGATHER',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREFILL',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.CORELIQUID',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREALCHEMY',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.UPGRADEORDER',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.ADVANCEDGOLEM'
        ],
        'secondary': False,
        'special': False,
        'recipe_pages': [
            'thaumicenergistics:golem_wireless_backpack'
        ],
        'recipe_groups': {},
        'page_order': [
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.1',
            'thaumicenergistics:golem_wireless_backpack',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.2',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.3',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREGATHER',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREFILL',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.CORELIQUID',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.COREALCHEMY',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.UPGRADEORDER',
            'thaumicenergistics.research_page.TEGOLEMWIFIBACKPACK.ADVANCEDGOLEM'
        ]
    })

    analysis_dir = ADDON / 'analysis'
    analysis_dir.mkdir(parents=True, exist_ok=True)
    (analysis_dir / 'imported-gears-golems.json').write_text(json.dumps({
        'references': references,
        'base_power': 6,
        'required_power': 18,
        'min_ticks_per_sync': 6,
        'item_rates': [8, 24, 32],
        'fluid_rates': [100, 250, 500],
        'essentia_rates': [4, 12, 16],
        'skins': ['clay', 'diamond', 'flesh', 'gold', 'iron', 'stone', 'straw', 'tallow', 'thaumium', 'wood']
    }, indent=2) + '\n', encoding='utf-8')

    print(f'THE_RELEASE_GEARS_GOLEMS_IMPORTED references={len(references)}')


if __name__ == '__main__':
    main()
