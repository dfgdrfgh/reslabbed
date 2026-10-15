"""Run the same updated-pack scene with the control and fixed release JARs."""
from pathlib import Path
import json,os,shutil,subprocess,sys,zipfile

root=Path.cwd()
control='--control' in sys.argv
fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
baseline=root/'build/full-pack-baseline'
game=root/('build/full-pack-control' if control else 'build/full-pack-fixed')
game.mkdir(parents=True,exist_ok=True)
for source in baseline.rglob('*'):
    if source.is_file():
        target=game/source.relative_to(baseline)
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
mods=game/'mods'
control_entry=next(e for e in fixture['forks'] if e['id']=='slabbed')
release=root/'pack-forks'/control_entry['file'] if control else next(
    p for p in (root/'build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-dev.jar')))
expected=[]
for jar in list(mods.glob('*.jar')):
    with zipfile.ZipFile(jar) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
    if metadata['id']=='slabbed':jar.unlink();continue
    # VanillaBackport's newer embedded Platform wins ordinary Fabric resolution.
    expected.append({'id':metadata['id'],'version':'1.4.0.2638.1.1' if metadata['id']=='platform' else metadata['version']})
shutil.copy2(release,mods/release.name)
with zipfile.ZipFile(release) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
expected.append({'id':metadata['id'],'version':metadata['version']})
assert len(expected)==61
(game/'expected-mods.json').write_text(json.dumps(expected))
with zipfile.ZipFile(root/'build/vertical-step-runtime/vertical-step-probe.jar') as archive:
    probe={i.filename:archive.read(i.filename) for i in archive.infolist()}
metadata=json.loads(probe['fabric.mod.json']);metadata.pop('provides',None)
metadata.get('entrypoints',{}).pop('fabric-gametest',None)
probe['fabric.mod.json']=json.dumps(metadata).encode()
with zipfile.ZipFile(mods/'vertical-step-probe.jar','w',zipfile.ZIP_DEFLATED) as archive:
    for name,data in probe.items():archive.writestr(name,data)
runtime=json.loads((root/'build/vertical-step-runtime/dependencies.json').read_text())
gradle=Path(runtime['gradleHome']);modules=gradle/'caches/modules-2/files-2.1'
classpath=[root/'build/full-pack-loader.jar']
def jars(group,name,version=None):
    folder=modules/group/name
    if version:folder/=version
    return sorted(p for p in folder.rglob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
def add(p):
    p=p.resolve()
    if p not in classpath:classpath.append(p)
add(jars('net.fabricmc','intermediary','1.21.1')[-1])
for artifact in runtime['artifacts']:
    if artifact['group']=='net.minecraft' or artifact['name']=='fabric-loader' or 'remapped_mods' in artifact['path']:continue
    options=jars(artifact['group'],artifact['name'],artifact['version'])
    classifier=artifact.get('classifier')
    options=[p for p in options if p.name.endswith('-'+classifier+'.jar')] if classifier else [p for p in options if '-natives-' not in p.name]
    if options:add(options[0])
for name in ['tiny-remapper','mapping-io']:add(jars('net.fabricmc',name)[-1])
for path in (modules/'org.lwjgl').rglob('*3.3.3-natives-linux.jar'):add(path)
games=[]
for path in (gradle/'caches/fabric-loom').rglob('minecraft-client.jar'):
    with zipfile.ZipFile(path) as archive:
        if 'fzf.class' in archive.namelist() and json.loads(archive.read('version.json'))['id']=='1.21.1':games.append(path)
assert games
add(games[0])
api=jars('net.fabricmc.fabric-api','fabric-gametest-api-v1')[-1]
shutil.copy2(api,mods/api.name)
indexes=list((gradle/'caches/fabric-loom').rglob('indexes/*17*.json'));assert len(indexes)==1
index=indexes[0]
(game/'options.txt').write_text('renderClouds:false\nrenderDistance:6\nmaxFps:60\n')
command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xmx4G','-Dfabric.development=false',
         '-Dslabbed.verticalStepProbe=true','-Dslabbed.vanillaSlabProbe=true',
         '-Dslabbed.packManifest='+str(game/'expected-mods.json'),
         '-cp',os.pathsep.join(map(str,classpath)),'net.fabricmc.loader.impl.launch.knot.KnotClient',
         '--gameDir',str(game),'--assetsDir',str(index.parent.parent),'--assetIndex',index.stem,
         '--username','SlabProbe','--version','1.21.1','--accessToken','0']
print('Launching updated 61-mod pack:',release.name,'loader',fixture['dependencies']['fabric-loader'],flush=True)
with (game/'production-launch.log').open('w') as log:
    result=subprocess.run(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,timeout=360)
lines=(game/'production-launch.log').read_text(errors='replace').splitlines()
for line in lines:
    if any(s in line for s in ['FULL_PACK','REPEATED_STACK','OVERHEAD','VERTICAL_STEP_CLIENT_PASS','Caused by:','AssertionError']):print(line[:650])
if control:
    assert result.returncode!=0 and any('pulled an existing overhead block down' in line for line in lines), 'Control did not reach the expected regression'
    print('FULL_PACK_CONTROL_REPRODUCED')
else:
    assert result.returncode==0 and (game/'vertical-step-pass.txt').is_file(), 'Fixed full-pack scene failed'
    print('FULL_PACK_FIXED_PASS')
