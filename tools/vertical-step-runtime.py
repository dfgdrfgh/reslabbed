"""Launch the remapped test mod and release JAR in Fabric's production namespace."""
import json
import os
from pathlib import Path
import subprocess
import zipfile

project = Path.cwd()
metadata = json.loads((project / 'build/vertical-step-runtime/dependencies.json').read_text())
home = Path(metadata['gradleHome'])
modules = home / 'caches/modules-2/files-2.1'
classpath = []

def add(path):
    path = Path(path).resolve()
    if path not in classpath:
        classpath.append(path)

def module_jars(group, name, version=None):
    root = modules / group / name
    if version is not None:
        root /= version
    return sorted(p for p in root.rglob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))

intermediary = module_jars('net.fabricmc', 'intermediary', '1.21.1')
assert intermediary, 'Production intermediary mappings are missing'
add(intermediary[-1])
for artifact in metadata['artifacts']:
    if artifact['group'] == 'net.minecraft':
        continue
    originals = module_jars(artifact['group'], artifact['name'], artifact['version'])
    classifier = artifact.get('classifier')
    if classifier:
        originals = [p for p in originals if p.name.endswith('-' + classifier + '.jar')]
    else:
        originals = [p for p in originals if '-natives-' not in p.name]
    if originals:
        add(originals[0])
    else:
        candidate = Path(artifact['path'])
        assert 'remapped_mods' not in str(candidate), 'Original dependency is missing'
        if candidate.suffix == '.jar':
            add(candidate)

# Fabric remaps the ordinary official game JAR to intermediary at production startup.
games = []
for path in (home / 'caches/fabric-loom').rglob('minecraft-client.jar'):
    with zipfile.ZipFile(path) as jar:
        if 'fzf.class' in jar.namelist() and json.loads(jar.read('version.json'))['id'] == '1.21.1':
            games.append(path)
assert games, 'Official Minecraft 1.21.1 client is missing'
add(games[0])
for name in ['tiny-remapper', 'mapping-io']:
    jars = module_jars('net.fabricmc', name)
    assert jars, 'Fabric remapping dependency is missing: ' + name
    add(jars[-1])

# Include native library resources independently of Loom's named development launcher.
for root in (modules / 'org.lwjgl').iterdir():
    if root.is_dir():
        for path in root.rglob('*3.3.3-natives-linux.jar'):
            add(path)

release = [p for p in (project / 'build/libs').glob('*.jar')
           if not p.name.endswith(('-sources.jar', '-dev.jar'))]
assert len(release) == 1, 'Expected one release mod'
add(release[0])
add(project / 'build/vertical-step-runtime/vertical-step-probe.jar')
game = project / 'build/run/vertical-step-client'
game.mkdir(parents=True, exist_ok=True)
(game / 'options.txt').write_text('renderClouds:false\nrenderDistance:8\nmaxFps:60\n')
assets = home / 'caches/fabric-loom/assets'
assert (assets / 'indexes/17.json').exists(), 'Minecraft 1.21.1 assets are missing'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
command = [java, '-Xmx2G', '-Dfabric.development=false', '-Dslabbed.verticalStepProbe=true',
           '-cp', os.pathsep.join(map(str, classpath)), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
           '--gameDir', str(game), '--assetsDir', str(assets), '--assetIndex', '17',
           '--username', 'FaceProbe', '--version', '1.21.1', '--accessToken', '0']
with (game / 'production-launch.log').open('w') as log:
    result = subprocess.run(command, cwd=game, stdout=log, stderr=subprocess.STDOUT, timeout=300)
assert result.returncode == 0, 'Production client failed; see production-launch.log'
assert (game / 'vertical-step-pass.txt').exists(), 'Production client did not complete the face checks'
print((game / 'vertical-step-pass.txt').read_text().strip())
