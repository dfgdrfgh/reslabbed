"""Run the remapped physical-cell probe in an unmodified production client namespace."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile,urllib.request

parser=argparse.ArgumentParser();parser.add_argument('--pack',action='store_true');parser.add_argument('--sodium',action='store_true');args=parser.parse_args()
root=Path.cwd();runtime=json.loads((root/'build/probe/dependencies.json').read_text())
home=Path(runtime['gradleHome']);modules=home/'caches/modules-2/files-2.1'
game=root/('build/run/physical-pack' if args.pack else 'build/run/physical-sodium' if args.sodium else 'build/run/physical-indigo')
assert not game.exists(),'Client requires a fresh run directory'
game.mkdir(parents=True);mods=game/'mods';mods.mkdir()
classpath=[]
def jars(group,name,version=None):
    folder=modules/group/name
    if version:folder/=version
    return sorted(p for p in folder.rglob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
def add(p):
    p=p.resolve()
    if p not in classpath:classpath.append(p)
release=[p for p in (root/'build/libs').glob('*.jar') if not p.name.endswith(('-sources.jar','-dev.jar'))];assert len(release)==1
if args.pack:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    for source in (root/'build/full-pack-baseline').rglob('*'):
        if source.is_file():
            target=game/source.relative_to(root/'build/full-pack-baseline');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    add(root/'build/full-pack-loader.jar')
else:
    add(jars('net.fabricmc','fabric-loader')[-1])
    api=jars('net.fabricmc.fabric-api','fabric-api',runtime['fabricVersion'])[-1];shutil.copy2(api,mods/api.name)
if args.sodium:
    url='https://cdn.modrinth.com/data/AANobbMI/versions/SMxNOGZ6/sodium-fabric-0.8.13%2Bmc1.21.1.jar'
    data=urllib.request.urlopen(url).read()
    assert hashlib.sha512(data).hexdigest()=='8614a374593698069a80ac74de8cd9c28f3928d16819a0e0d6320b538e823f7e54e8be06d5ea5a9c268beb0e02073209d1f831a02dede851588b972367680853'
    (mods/'sodium.jar').write_bytes(data)
expected=[]
for jar in list(mods.glob('*.jar')):
    with zipfile.ZipFile(jar) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
    if metadata['id']=='slabbed':jar.unlink();continue
    expected.append({'id':metadata['id'],'version':'1.4.0.2638.1.1' if metadata['id']=='platform' else metadata['version']})
shutil.copy2(release[0],mods/release[0].name)
with zipfile.ZipFile(release[0]) as archive:m=json.loads(archive.read('fabric.mod.json'))
expected.append({'id':m['id'],'version':m['version']})
if args.pack:assert len(expected)==fixture['modCount'],(len(expected),fixture['modCount'])
(game/'expected-mods.json').write_text(json.dumps(expected))
shutil.copy2(root/'build/probe/physical-probe.jar',mods/'physical-probe.jar')
api=jars('net.fabricmc.fabric-api','fabric-gametest-api-v1')[-1];shutil.copy2(api,mods/api.name)
add(jars('net.fabricmc','intermediary','1.21.1')[-1])
for artifact in runtime['artifacts']:
    if artifact['group']=='net.minecraft' or artifact['name']=='fabric-loader' or 'remapped_mods' in artifact['path']:continue
    options=jars(artifact['group'],artifact['name'],artifact['version']);classifier=artifact.get('classifier')
    options=[p for p in options if p.name.endswith('-'+classifier+'.jar')] if classifier else [p for p in options if '-natives-' not in p.name]
    if options:add(options[0])
for name in ['tiny-remapper','mapping-io']:add(jars('net.fabricmc',name)[-1])
for path in (modules/'org.lwjgl').rglob('*3.3.3-natives-linux.jar'):add(path)
games=[]
for path in (home/'caches/fabric-loom').rglob('minecraft-client.jar'):
    with zipfile.ZipFile(path) as archive:
        if 'fzf.class' in archive.namelist() and json.loads(archive.read('version.json'))['id']=='1.21.1':games.append(path)
assert games;add(games[0])
indexes=list((home/'caches/fabric-loom').rglob('indexes/*17*.json'));assert len(indexes)==1;index=indexes[0]
(game/'options.txt').write_text('renderClouds:false\nrenderDistance:6\nmaxFps:60\n')
print('PHYSICAL_JAR_SHA256',hashlib.sha256(release[0].read_bytes()).hexdigest(),flush=True)
for phase in ['fresh','reopen']:
    marker=game/'physical-pass.txt';marker.unlink(missing_ok=True)
    command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xmx4G','-Dfabric.development=false','-Dslabbed.physicalProbe=true',
             '-Dslabbed.reopenProbe='+str(phase=='reopen').lower(),'-Dslabbed.packManifest='+str(game/'expected-mods.json'),
             '-cp',os.pathsep.join(map(str,classpath)),'net.fabricmc.loader.impl.launch.knot.KnotClient',
             '--gameDir',str(game),'--assetsDir',str(index.parent.parent),'--assetIndex',index.stem,'--username','SlabProbe','--version','1.21.1','--accessToken','0']
    output=game/f'production-{phase}.log'
    try:
        with output.open('w') as stream:result=subprocess.run(command,cwd=game,stdout=stream,stderr=subprocess.STDOUT,timeout=500)
    except subprocess.TimeoutExpired:
        print('\n'.join(output.read_text(errors='replace').splitlines()[-100:]),flush=True);raise
    lines=output.read_text(errors='replace').splitlines()
    for line in lines:
        if any(s in line for s in ['PHYSICAL_','Caused by:','AssertionError','Exception']):print(line[:1200])
    if result.returncode!=0 or not marker.exists():print('\n'.join(lines[-100:]));raise AssertionError('Physical client failed '+phase)
    print(marker.read_text().strip(),flush=True)
print('PHYSICAL_CLIENT_PHASES_PASS pack='+str(args.pack)+' sodium='+str(args.sodium),flush=True)
