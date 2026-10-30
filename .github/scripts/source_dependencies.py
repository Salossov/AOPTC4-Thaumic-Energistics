"""Build local AOP dependency checkouts and generate a checksum-pinned bundle.

No repository URL/checksum variables or pre-existing releases are required.
Third-party compile inputs, when needed, are pinned in ci-external.json.
"""
import hashlib
import json
import os
from pathlib import Path
import re
from urllib.parse import urlparse
from urllib.request import urlopen
import zipfile

import ci
import suite


def external_artifact(role, record):
    url = urlparse(record['url'])
    if url.scheme != 'https' or url.username or url.password:
        raise ValueError('External dependencies must use HTTPS')
    checksum = record['sha256']
    if not re.fullmatch('[0-9a-f]{64}', checksum):
        raise ValueError('Invalid external dependency SHA-256')
    name = ci.safe_name(record['file'])
    if Path(name).name != name or not name.endswith('.jar'):
        raise ValueError('Invalid external dependency filename')
    path = ci.ROOT / '.cache/source-builds/external' / checksum / name
    if not path.is_file() or ci.digest(path) != checksum:
        path.parent.mkdir(parents=True, exist_ok=True)
        partial = path.with_suffix('.part')
        with urlopen(record['url'], timeout=90) as response, partial.open('wb') as output:
            if urlparse(response.geturl()).scheme != 'https':
                raise ValueError('External dependency redirected outside HTTPS')
            total = 0
            for block in iter(lambda: response.read(1024 * 1024), b''):
                total += len(block)
                if total > 1024 ** 3:
                    raise ValueError('External dependency exceeds 1 GiB')
                output.write(block)
        if ci.digest(partial) != checksum:
            partial.unlink()
            raise ValueError('External dependency checksum mismatch: ' + role)
        partial.replace(path)
    ci.validate_jar(path, record['mod_id'])
    return path


def bundle(paths, provenance, destination):
    """Fixed ZIP metadata makes identical inputs produce the same bundle SHA."""
    manifest = dict(format=1, artifacts={}, source_builds=provenance)
    contents = {}
    for role, path in sorted(paths.items()):
        name = ci.safe_name('jars/' + path.name)
        if name in contents:
            raise ValueError('Duplicate generated dependency filename')
        data = path.read_bytes()
        manifest['artifacts'][role] = dict(file=name, sha256=hashlib.sha256(data).hexdigest())
        contents[name] = data
    contents['manifest.json'] = (json.dumps(manifest, sort_keys=True, indent=2) + '\n').encode()
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(contents.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    return ci.digest(destination)


def main():
    config = ci.load(ci.CONFIG)
    needs_tinkerer = 'tinkererJar' in config['dependency_roles']
    os.environ['SUITE_TARGET'] = 'tinkerer' if needs_tinkerer else 'core'
    # Dependency builds use their configured versions; no releases are published.
    os.environ['RELEASE_REQUESTED'] = 'true'
    os.environ.setdefault('SUITE_UI_ROOT', '.dependencies/aopui')
    os.environ.setdefault('SUITE_CORE_ROOT', '.dependencies/thaumcraft')
    os.environ.setdefault('SUITE_TINKERER_ROOT', '.dependencies/tinkerer')
    suite.CACHE = Path('.cache/source-builds')
    suite.prepare()
    for stage in suite.stages():
        suite.plan(stage)
        suite.build(stage, force=os.environ.get('FORCE_REBUILD') == 'true')
    paths = dict(aopUiJar=Path(suite.artifact('ui', 'aopui-')),
                 thaumcraftApiJar=Path(suite.artifact('core', 'thaumcraft-api-')),
                 thaumcraftRuntimeJar=Path(suite.artifact('core', 'thaumcraft-1.21.1-')))
    if needs_tinkerer:
        paths['tinkererJar'] = Path(suite.artifact('tinkerer', 'ThaumicTinkerer-'))
    external = ci.ROOT / '.github/ci-external.json'
    if external.is_file():
        for role, record in ci.load(external).items():
            if role in paths:
                raise ValueError('External input cannot replace an AOP source build')
            paths[role] = external_artifact(role, record)
    if set(paths) != set(config['dependency_roles']):
        raise ValueError('Generated dependencies do not match the declared roles')
    provenance = {stage: suite.verified(stage) for stage in suite.stages()}
    destination = ci.ROOT / '.cache/ci-dependencies/from-sources.zip'
    checksum = bundle(paths, provenance, destination)
    if os.environ.get('GITHUB_ENV'):
        with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
            output.write('AOP_DEPENDENCIES_FILE=' + str(destination) + '\n')
            output.write('AOP_DEPENDENCIES_SHA256=' + checksum + '\n')
    print('Generated dependency bundle from verified source builds: ' + checksum)


if __name__ == '__main__':
    main()
