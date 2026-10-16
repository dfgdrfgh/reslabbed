"""Run the same updated-pack scene with the control and fixed release JARs."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,zipfile

root=Path.cwd()
parser=argparse.ArgumentParser()
parser.add_argument('--control',action='store_true')
parser.add_argument('--phase',choices=['fresh','baseline-save','candidate-reopen'],default='fresh')
parser.add_argument('--world-dir',type=Path)
args=parser.parse_args()
control=args.control
assert not control or args.phase=='fresh','Control is a separate regression run'
core_phase=args.phase!='fresh'
world_dir=(args.world_dir or root/'build/stored-core-world').resolve()
controls_file=world_dir.parent/'stored-core-controls.tsv'
fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
baseline=root/'build/full-pack-baseline'
game=root/('build/full-pack-'+args.phase if core_phase else 'build/full-pack-control' if control else 'build/full-pack-fixed')
game.mkdir(parents=True,exist_ok=True)
for source in baseline.rglob('*'):
    if source.is_file():
        target=game/source.relative_to(baseline)
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
mods=game/'mods'
control_entry=next(e for e in fixture['forks'] if e['id']=='slabbed')
release=root/'pack-forks'/control_entry['file'] if control else next(
    p for p in (root/'build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-dev.jar')))
if args.phase=='baseline-save':
    assert not world_dir.exists(),'Baseline world destination must be new'
    release=root/'pack-forks'/fixture['storedCoreBaseline']['file']
if args.phase=='candidate-reopen':
    assert world_dir.is_dir(),'Missing saved baseline world'
    destination=game/'saves/slabbed-stored-core'
    assert not destination.exists(),'Reopen uses a fresh copy of the baseline world'
    shutil.copytree(world_dir,destination)
expected=[]
for jar in list(mods.glob('*.jar')):
    with zipfile.ZipFile(jar) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
    if metadata['id']=='slabbed':jar.unlink();continue
    # VanillaBackport's newer embedded Platform wins ordinary Fabric resolution.
    expected.append({'id':metadata['id'],'version':'1.4.0.2638.1.1' if metadata['id']=='platform' else metadata['version']})
shutil.copy2(release,mods/release.name)
with zipfile.ZipFile(release) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
expected.append({'id':metadata['id'],'version':metadata['version']})
assert len(expected)==fixture['modCount']
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
         '-Dslabbed.verticalStepProbe='+str(not core_phase).lower(),'-Dslabbed.vanillaSlabProbe=true',
         '-Dslabbed.packManifest='+str(game/'expected-mods.json'),
         '-cp',os.pathsep.join(map(str,classpath)),'net.fabricmc.loader.impl.launch.knot.KnotClient',
         '--gameDir',str(game),'--assetsDir',str(index.parent.parent),'--assetIndex',index.stem,
         '--username','SlabProbe','--version','1.21.1','--accessToken','0']
if core_phase:
    command[1:1]=['-Dslabbed.coreProbePhase='+args.phase,
                  '-Dslabbed.keepProbe.evidenceDir='+str(game/'core-evidence'),
                  '-Dslabbed.keepProbe.controls='+str(controls_file),
                  '-Dslabbed.keepProbe.world=slabbed-stored-core']
print('SLABBED_JAR_SHA256',hashlib.sha256(release.read_bytes()).hexdigest(),release.name,flush=True)
print('Launching updated pack:',len(expected),'mods',release.name,'loader',fixture['dependencies']['fabric-loader'],flush=True)
with (game/'production-launch.log').open('w') as log:
    result=subprocess.run(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,timeout=360)
lines=(game/'production-launch.log').read_text(errors='replace').splitlines()
for line in lines:
    if any(s in line for s in ['FULL_PACK','REPEATED_STACK','OVERHEAD','CLIENT_STALE_BACKING','VERTICAL_STEP_CLIENT_PASS','Caused by:','AssertionError','CORE_SAVED_WORLD','KEEP_REOPEN_PROBE_SUMMARY','[KEEP_REOPEN_PROBE]']):print(line[:1200])
if core_phase:
    assert result.returncode==0 and any('verdict=GREEN' in line and 'KEEP_REOPEN_PROBE_SUMMARY' in line for line in lines), 'Saved-world probe failed'
    if args.phase=='baseline-save':
        shutil.copytree(game/'saves/slabbed-stored-core',world_dir)
    print('CORE_SAVED_WORLD_PHASE_PASS',args.phase)
elif control:
    assert result.returncode!=0 and any('pulled an existing overhead block down' in line for line in lines), 'Control did not reach the expected regression'
    print('FULL_PACK_CONTROL_REPRODUCED')
else:
    assert result.returncode==0 and (game/'vertical-step-pass.txt').is_file(), 'Fixed full-pack scene failed'
    print('FULL_PACK_FIXED_PASS')
