import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import ci
import source_dependencies as sources
import suite


class SourceDependencyTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.patch = patch.object(ci, 'ROOT', self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def test_bundle_is_deterministic_and_keeps_source_identity(self):
        path = self.root / 'api.jar'
        path.write_bytes(b'compiled API')
        provenance = dict(core=dict(key='source-A', artifacts={'api.jar': ci.digest(path)}))
        first = sources.bundle(dict(api=path), provenance, self.root / 'one.zip')
        os.utime(path, (1000000000, 1000000000))
        second = sources.bundle(dict(api=path), provenance, self.root / 'two.zip')
        self.assertEqual(first, second)
        provenance['core']['key'] = 'source-B'
        self.assertNotEqual(first, sources.bundle(dict(api=path), provenance, self.root / 'three.zip'))

    def test_duplicate_dependency_filenames_are_rejected(self):
        a = self.root / 'a' / 'same.jar'; a.parent.mkdir(); a.write_bytes(b'a')
        b = self.root / 'b' / 'same.jar'; b.parent.mkdir(); b.write_bytes(b'b')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            sources.bundle(dict(a=a, b=b), {}, self.root / 'bundle.zip')

    def test_cached_external_jar_requires_exact_checksum_and_mod_id(self):
        record = dict(url='https://example.invalid/dependency.jar', file='dependency.jar',
                      sha256='0' * 64, mod_id='fixture')
        with patch.object(sources, 'urlopen') as fetch:
            record['url'] = 'http://example.invalid/dependency.jar'
            with self.assertRaisesRegex(ValueError, 'HTTPS'):
                sources.external_artifact('external', record)
            fetch.assert_not_called()

    def test_external_checksum_mismatch_is_never_accepted(self):
        record = dict(url='https://example.invalid/dependency.jar', file='dependency.jar',
                      sha256='0' * 64, mod_id='fixture')
        response = io.BytesIO(b'wrong file')
        response.geturl = lambda: record['url']
        with patch.object(sources, 'urlopen', return_value=response), self.assertRaisesRegex(ValueError, 'checksum'):
            sources.external_artifact('external', record)
        self.assertFalse(list(self.root.rglob('*.jar')))

    def test_downloaded_external_cache_is_checked_without_a_second_download(self):
        data = b'fixture jar'
        record = dict(url='https://example.invalid/dependency.jar', file='dependency.jar',
                      sha256=sources.hashlib.sha256(data).hexdigest(), mod_id='fixture')
        response = io.BytesIO(data); response.geturl = lambda: record['url']
        with patch.object(sources, 'urlopen', return_value=response) as fetch, patch.object(ci, 'validate_jar') as validate:
            first = sources.external_artifact('external', record)
            self.assertEqual(first, sources.external_artifact('external', record))
            self.assertEqual(fetch.call_count, 1)
            validate.assert_called_with(first, 'fixture')

    def test_compact_build_order_includes_the_before_compact(self):
        with patch.dict(os.environ, SUITE_TARGET='compact'):
            self.assertEqual(suite.stages(), ('ui', 'core', 'tinkerer', 'the', 'compact'))
            self.assertNotIn('compact', suite.DEPS['the'])

    def test_workflow_needs_no_manual_bundle_variables(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/build.yml').read_text()
        self.assertIn('source_dependencies.py', workflow)
        self.assertNotIn('vars.AOP_DEPENDENCIES_', workflow)
        self.assertNotIn('dependency_url:', workflow)

    def test_generated_bundle_tampering_fails_before_download_or_build(self):
        path = self.root / 'generated.zip'; path.write_bytes(b'changed')
        config = self.root / 'config.json'; config.write_text(json.dumps(dict(dependency_roles=['api'])))
        with patch.object(ci, 'CONFIG', config), patch.dict(os.environ, AOP_DEPENDENCIES_FILE=str(path),
                AOP_DEPENDENCIES_SHA256='0' * 64), patch.object(ci, 'urlopen') as fetch:
            with self.assertRaisesRegex(ValueError, 'Generated dependency bundle SHA-256'):
                ci.prepare()
            fetch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
