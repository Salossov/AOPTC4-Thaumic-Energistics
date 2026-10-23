"""Knowledge Core and Inscriber resource verification and release parity importer."""
from pathlib import Path
import hashlib
import json
import re

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT / 'src/main/resources'


def main():
    addon = next(p for p in Path(__file__).resolve().parents if p.name == 'ThaumicEnergistics')
    jar_path = addon / 'thaumicenergistics-1.1.3.0.jar'
    if jar_path.is_file():
        assert hashlib.sha1(jar_path.read_bytes()).hexdigest() == 'a6240b09a0680f3e3246a6d0fe17650299533a84', 'Jar SHA1 mismatch'

    # Verify source and decompiled consistency
    for base in ('decompiled', 'source_code/src/main/java'):
        kcore = addon / base / 'thaumicenergistics/common/items/ItemKnowledgeCore.java'
        handler = addon / base / 'thaumicenergistics/common/inventory/HandlerKnowledgeCore.java'
        pattern = addon / base / 'thaumicenergistics/common/integration/tc/ArcaneCraftingPattern.java'
        container = addon / base / 'thaumicenergistics/common/container/ContainerKnowledgeInscriber.java'
        tile = addon / base / 'thaumicenergistics/common/tiles/TileKnowledgeInscriber.java'
        block = addon / base / 'thaumicenergistics/common/blocks/BlockKnowledgeInscriber.java'
        gui = addon / base / 'thaumicenergistics/client/gui/GuiKnowledgeInscriber.java'

        assert kcore.is_file(), f'Missing {kcore}'
        assert handler.is_file(), f'Missing {handler}'
        assert pattern.is_file(), f'Missing {pattern}'
        assert container.is_file(), f'Missing {container}'
        assert tile.is_file(), f'Missing {tile}'
        assert block.is_file(), f'Missing {block}'
        assert gui.is_file(), f'Missing {gui}'

        handler_src = handler.read_text(encoding='utf-8', errors='ignore')
        assert 'MAXIMUM_STORED_PATTERNS = 21' in handler_src

        container_src = container.read_text(encoding='utf-8', errors='ignore')
        assert 'KCORE_SLOT_X = 186' in container_src
        assert 'KCORE_SLOT_Y = 8' in container_src
        assert 'CRAFTING_SLOT_X = 26' in container_src
        assert 'CRAFTING_SLOT_Y = 90' in container_src
        assert 'PATTERN_SLOT_X = 26' in container_src
        assert 'PATTERN_SLOT_Y = 18' in container_src

        pattern_src = pattern.read_text(encoding='utf-8', errors='ignore')
        assert 'GRID_SIZE = 9' in pattern_src

    print("Knowledge Inscriber release sources verified successfully.")


if __name__ == '__main__':
    main()
