import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import stat
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

spec = importlib.util.spec_from_file_location('ci', Path(__file__).with_name('ci.py'))
ci = importlib.util.module_from_spec(spec); spec.loader.exec_module(ci)


def jar(mid='fixture'):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as z:
        z.writestr(zipfile.ZipInfo('META-INF/neoforge.mods.toml'), '[[mods]]\nmodId="'+mid+'"\nversion="1"\n')
    return stream.getvalue()


class DependencyBundleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def bundle(self, data=None, checksum=None, mid='fixture', extra=None):
        data = jar() if data is None else data
        row = {'file':'jars/test.jar', 'sha256': checksum or hashlib.sha256(data).hexdigest(), 'mod_id':mid}
        path = self.root/'bundle.zip'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('manifest.json', json.dumps({'format':1,'artifacts':{'api':row}}))
            z.writestr(row['file'], data)
            if extra: z.writestr(*extra)
        return path

    def test_valid_role_is_extracted_and_pinned(self):
        paths=ci.extract_bundle(self.bundle(), self.root/'out', ['api'])
        self.assertEqual(ci.digest(paths['api']), hashlib.sha256(jar()).hexdigest())

    def test_wrong_sha_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'checksum'): ci.extract_bundle(self.bundle(checksum='0'*64),self.root/'out',['api'])

    def test_wrong_mod_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'mod ID'): ci.extract_bundle(self.bundle(mid='another'),self.root/'out',['api'])

    def test_missing_role_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Missing dependency'): ci.extract_bundle(self.bundle(),self.root/'out',['runtime'])

    def test_traversal_is_rejected_without_writing_outside(self):
        with self.assertRaisesRegex(ValueError,'Unsafe'): ci.extract_bundle(self.bundle(extra=('../outside.jar',b'bad')),self.root/'out',['api'])
        self.assertFalse((self.root/'outside.jar').exists())

    def test_unlisted_file_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'Unlisted'): ci.extract_bundle(self.bundle(extra=('extra.jar',jar())),self.root/'out',['api'])

    def test_symlink_is_rejected(self):
        info=zipfile.ZipInfo('symlink'); info.create_system=3; info.external_attr=(stat.S_IFLNK|0o777)<<16
        with self.assertRaisesRegex(ValueError,'Unsupported'): ci.extract_bundle(self.bundle(extra=(info,b'jars/test.jar')),self.root/'out',['api'])

    def test_unsafe_platform_names(self):
        for name in ['/a','../a','a/../b','C:/a','a\\b','a//b']:
            with self.subTest(name=name), self.assertRaises(ValueError): ci.safe_name(name)

    def test_modified_cached_artifact_is_not_reused(self):
        dist=self.root/'dist';dist.mkdir();(dist/'a.jar').write_bytes(b'changed')
        ci.write(dist/'manifest.json',{'key':'key','artifacts':{'a.jar':'0'*64,'a-sources.jar':'1'*64}})
        with patch.object(ci,'ROOT',self.root): self.assertFalse(ci.verified_dist({'key':'key'}))

    def test_old_build_identity_is_not_reused(self):
        ci.write(self.root/'dist/manifest.json',{'key':'old','artifacts':{}})
        with patch.object(ci,'ROOT',self.root): self.assertFalse(ci.verified_dist({'key':'new'}))


class ResourceContracts(unittest.TestCase):
    def test_svg_sources_and_engine_exports(self):
        root=ci.ROOT; config=ci.load(ci.CONFIG); mid=config['mod_id']
        svg=ET.parse(root/'assets/branding/logo.svg').getroot()
        self.assertEqual(svg.attrib['viewBox'],'0 0 512 512')
        self.assertFalse(any(node.tag.endswith('image') for node in svg.iter()),'SVG must be real vector artwork')
        for suffix,size in [(f'assets/aop_ui/textures/branding/{mid}.png',512),(f'assets/aop_ui/textures/branding/items/{mid}.png',64),('logo.png',512)]:
            data=(root/'src/main/resources'/suffix).read_bytes()
            self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n')
            self.assertEqual(ci.struct.unpack('>II',data[16:24]),(size,size))
        self.assertEqual(ci.digest(root/'src/main/resources/logo.png'),ci.digest(root/f'src/main/resources/assets/aop_ui/textures/branding/{mid}.png'))

    def test_every_existing_setting_has_both_languages_and_help(self):
        root=ci.ROOT;mid=ci.load(ci.CONFIG)['mod_id']
        paths=[]
        if mid=='forbidden':
            source=(root/'src/main/java/forbidden/config/ForbiddenConfig.java').read_text()
            category=None
            for line in source.splitlines():
                match=re.search(r'\.push\("([^"]+)"\)',line)
                if match:category=match[1]
                match=re.search(r'\.define(?:InRange|ListAllowEmpty)?\("([^"]+)"',line)
                if match: paths.append('aopui.option.forbidden.'+category+'.'+match[1])
            self.assertEqual(len(paths),27)
            self.assertEqual(len(re.findall(r'\.gameRestart\(\)\.define(?:InRange|ListAllowEmpty)?\(',source)),27,'AOP UI requires restart metadata for every STARTUP value')
        else:
            folder=root/'src/main/java/thaumicenergistics/config'
            text=(folder/'ThEContentConfig.java').read_text()+(folder/'ThEClientConfig.java').read_text()
            keys=re.findall(r'crafting\(builder, "([^"]+)"',text)+re.findall(r'\.translation\("thaumicenergistics.configuration.([^"]+)"\)',text)
            paths=['thaumicenergistics.configuration.'+key for key in keys]
            self.assertEqual(len(paths),16)
        for lang in ['en_us','ru_ru']:
            data=ci.load(root/f'src/main/resources/assets/{mid}/lang/{lang}.json')
            for key in paths:
                self.assertTrue(data.get(key),lang+': '+key)
                self.assertTrue(data.get(key+'.tooltip'),lang+': '+key+'.tooltip')
