"""Package the production client inputs for isolated full-modpack verification."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import hashlib
import concurrent.futures

project = Path.cwd()
metadata = json.loads((project / 'build/vertical-step-runtime/dependencies.json').read_text())
gradle = Path(metadata['gradleHome'])
out = project / 'build/portable-client-runtime'
out.mkdir(parents=True, exist_ok=True)
subprocess.run([str(Path(os.environ['JAVA_HOME']) / 'bin/jlink'), '--add-modules', 'ALL-MODULE-PATH',
                '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=2',
                '--output', str(out / 'java')], check=True)
selected = set()
modules = gradle / 'caches/modules-2/files-2.1'
for artifact in metadata['artifacts']:
    group = modules / artifact['group'] / artifact['name'] / artifact['version']
    if group.exists(): selected.update(group.rglob('*.jar'))
for group, name in [('net.fabricmc', 'intermediary'), ('net.fabricmc', 'tiny-remapper'),
                    ('net.fabricmc', 'mapping-io'), ('net.fabricmc.fabric-api', 'fabric-api'),
                    ('net.fabricmc.fabric-api', 'fabric-gametest-api-v1')]:
    selected.update((modules / group / name).rglob('*.jar'))
selected.update((modules / 'org.lwjgl').rglob('*natives-linux.jar'))
for candidate in (gradle / 'caches/fabric-loom').rglob('minecraft-client.jar'):
    selected.add(candidate)
for candidate in (gradle / 'caches/fabric-loom').rglob('indexes/*17*.json'):
    selected.add(candidate)
for source in selected:
    if source.name.endswith(('-sources.jar', '-javadoc.jar')): continue
    target = out / 'gradle' / source.relative_to(gradle)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
for name in ['dependencies.json', 'vertical-step-probe.jar']:
    shutil.copy2(project / 'build/vertical-step-runtime' / name, out / name)
for source in (project / 'build/libs').glob('*.jar'):
    if not source.name.endswith(('-sources.jar', '-dev.jar')): shutil.copy2(source, out / source.name)
manifest = project / 'tools/verification-pack-downloads.json'
if manifest.exists():
    def download(entry):
        data = urllib.request.urlopen(entry['downloads'][0], timeout=90).read()
        for kind, expected in entry['hashes'].items():
            assert hashlib.new(kind, data).hexdigest() == expected, entry['path']
        target = out / 'pack-indexed' / entry['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(download, json.loads(manifest.read_text())))
xvfb = Path(shutil.which('Xvfb'))
shutil.copy2(xvfb, out / 'Xvfb')
libs = subprocess.check_output(['ldd', str(xvfb)], text=True)
(out / 'system').mkdir(exist_ok=True)
for line in libs.splitlines():
    fields = line.split()
    if '=>' not in fields or len(fields) < 3: continue
    source = Path(fields[2])
    if source.is_file() and not source.name.startswith(('libc.', 'ld-linux', 'libm.', 'libpthread.', 'libdl.', 'librt.')):
        shutil.copy2(source, out / 'system' / source.name)
with tarfile.open(project / 'build/portable-client-runtime.tar.gz', 'w:gz') as archive:
    archive.add(out, arcname='runtime')
