#!/usr/bin/env python3
import configparser
import zipfile
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / 'build/package/processing-nozzle'
LIB = PACKAGE / 'library'

required = [
    PACKAGE / 'library.properties',
    LIB / 'processing-nozzle.jar',
    PACKAGE / 'examples/NozzleDiagnostics/NozzleDiagnostics.pde',
    PACKAGE / 'examples/NozzleSenderCpu/NozzleSenderCpu.pde',
    PACKAGE / 'examples/NozzleReceiverCpu/NozzleReceiverCpu.pde',
    PACKAGE / 'reference/index.html',
]
for path in required:
    if not path.exists():
        raise SystemExit(f'missing {path}')

props = {}
for line in (PACKAGE / 'library.properties').read_text(encoding='utf-8').splitlines():
    if '=' in line:
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
for key in ['name', 'authors', 'url', 'categories', 'sentence', 'version']:
    if key not in props or not props[key]:
        raise SystemExit(f'missing library.properties field {key}')
if props['name'] != 'processing-nozzle':
    raise SystemExit('library.properties name must be processing-nozzle')
int(props['version'])

platform_dirs = [p.name for p in LIB.iterdir() if p.is_dir()]
valid_prefixes = {'macos-aarch64', 'macos-x86_64', 'windows-amd64', 'linux-amd64'}
if not platform_dirs:
    raise SystemExit('missing platform native directory')
for name in platform_dirs:
    if name not in valid_prefixes:
        raise SystemExit(f'unexpected Processing 4 platform folder: {name}')
    natives = list((LIB / name).glob('*processing_nozzle_jni*'))
    if not natives:
        raise SystemExit(f'missing native helper in {name}')

zips = sorted((ROOT / 'build').glob('processing-nozzle-latest-*.zip'))
if len(zips) != 1:
    raise SystemExit(f'expected one zip, found {zips}')
with zipfile.ZipFile(zips[0]) as zf:
    names = zf.namelist()
    roots = {n.split('/', 1)[0] for n in names if n}
    if roots != {'processing-nozzle'}:
        raise SystemExit(f'zip has wrong roots: {roots}')
    if any(n.startswith('processing-nozzle-latest-') for n in names):
        raise SystemExit('zip contains an extra wrapper directory')

print('processing package shape ok')
