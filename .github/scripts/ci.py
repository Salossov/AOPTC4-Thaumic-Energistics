"""Verified dependency bundles, reproducible build identity and artifact checks.

Follows the AOP suite's content-addressed cache/provenance model. Builds and release packaging use the same pinned inputs. Local act runs may
only inspect publication plans; hosted publication is an explicit workflow mode.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import stat
import struct
import subprocess
import sys
import tomllib
from urllib.parse import urlparse
from urllib.request import urlopen
import zipfile

import release

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / '.github/ci-project.json'


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def write(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def safe_name(name):
    p = PurePosixPath(name)
    if not name or '\\' in name or ':' in name or p.is_absolute() or '..' in p.parts or str(p) != name:
        raise ValueError('Unsafe archive member')
    return name


def validate_jar(path, mod_id=None):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if mod_id:
            metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            if mod_id not in [entry['modId'] for entry in metadata.get('mods', [])]:
                raise ValueError('Wrong dependency or gameplay mod ID: ' + mod_id)
        return names


def extract_bundle(bundle, destination, required):
    destination = Path(destination).resolve()
    with zipfile.ZipFile(bundle) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Duplicate archive member')
        for entry in archive.infolist():
            safe_name(entry.filename)
            if stat.S_ISLNK(entry.external_attr >> 16) or entry.file_size > 1024 ** 3:
                raise ValueError('Unsupported bundle member')
        manifest = json.loads(archive.read('manifest.json'))
        if manifest.get('format') != 1 or not isinstance(manifest.get('artifacts'), dict):
            raise ValueError('Unsupported dependency manifest')
        artifacts = manifest['artifacts']
        if not set(required).issubset(artifacts):
            raise ValueError('Missing dependency roles: ' + ', '.join(sorted(set(required) - set(artifacts))))
        expected = {'manifest.json'}
        paths = {}
        for role, entry in artifacts.items():
            name = safe_name(entry['file'])
            if not name.endswith('.jar') or name in expected or not re.fullmatch('[0-9a-f]{64}', entry['sha256']):
                raise ValueError('Invalid artifact record')
            expected.add(name)
            target = destination / name
            data = archive.read(name)
            if hashlib.sha256(data).hexdigest() != entry['sha256']:
                raise ValueError('Dependency checksum mismatch: ' + role)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            validate_jar(target, entry.get('mod_id'))
            paths[role] = str(target)
        if set(archive.namelist()) != expected:
            raise ValueError('Unlisted dependency bundle entries')
        write(destination / 'manifest.json', manifest)
        return paths


def source_identity():
    inventory = ROOT / '.ci-source.json'
    if os.environ.get('ACT') == 'true' and inventory.is_file():
        row = load(inventory)
        revision, names = row['revision'], row['files']
    else:
        revision = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip()
        names = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
    if not re.fullmatch('[0-9a-f]{40}', revision):
        raise ValueError('Invalid source revision')
    files = {}
    for name in sorted(filter(None, names)):
        safe_name(name)
        p = ROOT / name
        if p.is_symlink():
            raise ValueError('Source inventory may not contain symlinks')
        files[name] = digest(p)
    if not files:
        raise ValueError('Empty source inventory')
    return {'revision': revision, 'content': hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()}


def output(key, value):
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as stream:
            stream.write(key + '=' + value + '\n')


def prepare():
    config = load(CONFIG)
    checksum = os.environ.get('AOP_DEPENDENCIES_SHA256', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', checksum):
        raise ValueError('Set AOP_DEPENDENCIES_SHA256 to the dependency ZIP SHA-256')
    generated = os.environ.get('AOP_DEPENDENCIES_FILE', '')
    bundle = Path(generated) if generated else ROOT / '.cache/ci-dependencies' / (checksum + '.zip')
    if generated and (bundle.is_symlink() or not bundle.is_file() or digest(bundle) != checksum):
        raise ValueError('Generated dependency bundle SHA-256 mismatch')
    if not generated and (not bundle.is_file() or digest(bundle) != checksum):
        url = os.environ.get('AOP_DEPENDENCIES_URL', '')
        parsed = urlparse(url)
        local = os.environ.get('ACT') == 'true' and parsed.scheme == 'http' and parsed.hostname in {'127.0.0.1', 'localhost', 'host.docker.internal'}
        if parsed.username or parsed.password or not (parsed.scheme == 'https' or local):
            raise ValueError('Set AOP_DEPENDENCIES_URL to an HTTPS dependency bundle URL')
        bundle.parent.mkdir(parents=True, exist_ok=True)
        temporary = bundle.with_suffix('.part')
        with urlopen(url, timeout=90) as response, temporary.open('wb') as stream:
            total = 0
            for block in iter(lambda: response.read(1024 * 1024), b''):
                total += len(block)
                if total > 1024 ** 3:
                    raise ValueError('Dependency bundle exceeds 1 GiB')
                stream.write(block)
        if digest(temporary) != checksum:
            temporary.unlink()
            raise ValueError('Dependency bundle SHA-256 mismatch')
        temporary.replace(bundle)
    paths = extract_bundle(bundle, ROOT / '.dependencies/verified', config['dependency_roles'])
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                 if '=' in line and not line.lstrip().startswith(('#', '!')))
    java = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
    source = source_identity()
    mode = release.select_mode(os.environ.get('GITHUB_EVENT_NAME', 'pull_request'), os.environ.get('RELEASE_REQUESTED', '').lower() == 'true')
    version = release.select_version('release', os.environ.get('RELEASE_VERSION', ''), props['mod_version'].strip(), '')
    if mode == 'nightly': version += '-nightly.' + source['revision'][:12]
    lock = {'format': 1, 'source': source, 'bundle_sha256': checksum,
            'mode': mode, 'version': version, 'mod_id': config['mod_id'],
            'dependency_checksums': {role: digest(path) for role, path in paths.items()},
            'runtime': {'os': platform.system(), 'java': java.stdout + java.stderr}, 'dependencies': paths}
    # Absolute extraction paths are not part of the reusable identity.
    identity = {k: v for k, v in lock.items() if k != 'dependencies'}
    lock['key'] = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
    write(ROOT / '.cache/ci-lock.json', lock)
    output('key', lock['key'])
    output('version', version)
    output('mode', mode)
    print('Pinned dependency bundle ' + checksum + '; source ' + lock['source']['revision'])


def audit_gameplay(path, mod_id, version):
    names = validate_jar(path, mod_id)
    bad = ('forbidden/acceptance/', 'forbidden/verification/', 'thaumicenergistics/verification/', 'integrationqa/', 'thaumcraft/')
    if any(n.startswith(bad) or n.endswith('.jar') for n in names):
        raise ValueError('Private QA or dependency JAR leaked into gameplay artifact')
    with zipfile.ZipFile(path) as z:
        meta = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
        if next(x for x in meta['mods'] if x['modId'] == mod_id)['version'] != version:
            raise ValueError('Artifact version mismatch')
        if 'LICENSE' not in names or 'NOTICE.md' not in names or 'logo.png' not in names:
            raise ValueError('Missing branding or license notice')
        png = z.read('logo.png')
        if png[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', png[16:24]) != (512, 512):
            raise ValueError('Invalid packaged logo')


def verified_dist(lock):
    manifest = load(ROOT / 'dist/manifest.json')
    if manifest['key'] != lock['key']:
        return False
    for name, checksum in manifest['artifacts'].items():
        safe_name(name)
        p = ROOT / 'dist' / name
        if not p.is_file() or digest(p) != checksum:
            return False
        if not name.endswith('-sources.jar'):
            audit_gameplay(p, lock['mod_id'], lock['version'])
    names = list(manifest['artifacts'])
    return len(names) == 2 and all(n.endswith('.jar') for n in names) and sum(n.endswith('-sources.jar') for n in names) == 1


def build(force=False):
    lock = load(ROOT / '.cache/ci-lock.json')
    if source_identity() != lock['source']:
        raise ValueError('Sources changed after prepare; run prepare again')
    for role, path in lock['dependencies'].items():
        if digest(path) != lock['dependency_checksums'][role]:
            raise ValueError('Dependency changed after prepare: ' + role)
    if not force:
        try:
            if verified_dist(lock):
                print('REUSED verified build ' + lock['key']); return
        except (OSError, ValueError, KeyError, zipfile.BadZipFile):
            pass
    command = ['java', '-Xmx768m', '-Dorg.gradle.jvmargs=-Xmx1536m', '-classpath',
               'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain',
               '--no-daemon', '--console=plain', '--no-configuration-cache', '--max-workers=2', 'clean', 'build', 'sourcesJar']
    command.append('-Pmod_version=' + lock['version'])
    command += ['-P' + key + '=' + value for key, value in sorted(lock['dependencies'].items())]
    dist = ROOT / 'dist'; dist.mkdir(exist_ok=True)
    with (dist / 'build.log').open('w', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            print(line, end='', flush=True); log.write(line)
        code = process.wait()
    if code:
        raise SystemExit(code)
    jars = sorted((ROOT / 'build/libs').glob('*.jar'))
    if len(jars) != 2 or sum(p.name.endswith('-sources.jar') for p in jars) != 1:
        raise ValueError('Expected exactly gameplay and sources JARs')
    for old in dist.glob('*.jar'):
        old.unlink()
    artifacts = {}
    for p in jars:
        if not p.name.endswith('-sources.jar'):
            audit_gameplay(p, lock['mod_id'], lock['version'])
        target = dist / p.name; shutil.copy2(p, target); artifacts[p.name] = digest(target)
    write(dist / 'manifest.json', {k: v for k, v in lock.items() if k != 'dependencies'} | {'artifacts': artifacts})
    if not verified_dist(lock):
        raise ValueError('Artifact verification failed')
    print('BUILT and verified ' + lock['mod_id'])



def package_release():
    lock = load(ROOT / '.cache/ci-lock.json')
    if not verified_dist(lock): raise ValueError('Release packaging requires verified artifacts')
    manifest = load(ROOT / 'dist/manifest.json')
    destination = ROOT / 'dist/release'; destination.mkdir(exist_ok=True)
    for old in destination.iterdir():
        if old.is_file(): old.unlink()
    for name in sorted(manifest['artifacts']): shutil.copy2(ROOT / 'dist' / name, destination / name)
    write(destination / 'aop-build-manifest.json', manifest)
    (destination / 'SHA256SUMS.txt').write_text(''.join(checksum + '  ' + name + '\n' for name, checksum in sorted(manifest['artifacts'].items())), encoding='utf-8')
    print('Packaged gameplay JAR, sources, checksums and provenance; no build log is published')


def publish(dry_run=False):
    if os.environ.get('ACT') == 'true' and not dry_run:
        raise ValueError('Local act runs may only verify a dry-run publication plan')
    lock = load(ROOT / '.cache/ci-lock.json')
    if lock['mode'] == 'build': raise ValueError('Pull requests cannot publish')
    if not verified_dist(lock): raise ValueError('Cannot publish unverified artifacts')
    config = load(CONFIG)
    repository = os.environ.get('GITHUB_REPOSITORY') or config['repository']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository): raise ValueError('Invalid repository')
    os.environ.update(RELEASE_MODE=lock['mode'], BUILD_VERSION=lock['version'], RELEASE_NAME=config['name'],
                      GH_REPO=repository, GITHUB_SHA=lock['source']['revision'], RELEASE_REQUIREMENTS=config['requirements'])
    release.publish(dry_run, str(ROOT / 'dist/release'))


def inventory():
    names = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
    write(ROOT / '.ci-source.json', {'revision': subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip(), 'files': sorted(filter(None, names))})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['prepare', 'build', 'package', 'publish', 'inventory'])
    parser.add_argument('--force', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    if args.command == 'build': build(args.force)
    elif args.command == 'prepare': prepare()
    elif args.command == 'package': package_release()
    elif args.command == 'publish': publish(args.dry_run)
    else: inventory()
