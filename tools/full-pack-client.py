from pathlib import Path
import json,os,shutil,zipfile,subprocess,sys

ROOT=Path.cwd()
runtime=ROOT/'ghost-fullpack-runtime/runtime'
control='--control' in sys.argv
game=ROOT/('ghost-fullpack-control' if control else 'ghost-fullpack-fixed')
game.mkdir(exist_ok=True)
with zipfile.ZipFile(ROOT/'full-pack.mrpack') as pack:
    index=json.loads(pack.read('modrinth.index.json'))
    for name in pack.namelist():
        if name.startswith('overrides/') and not name.endswith('/'):
            destination=game/name.removeprefix('overrides/')
            destination.parent.mkdir(parents=True,exist_ok=True)
            destination.write_bytes(pack.read(name))
for entry in index['files']:
    source=runtime/'pack-indexed'/entry['path'];target=game/entry['path']
    assert source.is_file(),source
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source,target)
mods=game/'mods'
for jar in list(mods.glob('*.jar')):
    with zipfile.ZipFile(jar) as z: metadata=json.loads(z.read('fabric.mod.json'))
    if metadata['id']=='slabbed': jar.unlink()
release=(ROOT/'control.jar') if control else next(runtime.glob('slabbed-0.6.8*.jar'))
shutil.copy2(release,mods/release.name)
with zipfile.ZipFile(runtime/'vertical-step-probe.jar') as z: probe={i.filename:z.read(i.filename) for i in z.infolist()}
metadata=json.loads(probe['fabric.mod.json']);metadata.pop('provides',None)
probe['fabric.mod.json']=json.dumps(metadata).encode()
with zipfile.ZipFile(mods/'vertical-step-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
    for name,data in probe.items():z.writestr(name,data)
data=json.loads((runtime/'dependencies.json').read_text())
gradle=runtime/'gradle';modules=gradle/'caches/modules-2/files-2.1';classpath=[runtime/'fabric-loader-0.19.5.jar']
def add(p):
    p=p.resolve()
    if p not in classpath:classpath.append(p)
def jars(group,name,version=None):
    base=modules/group/name
    if version:base/=version
    return sorted(p for p in base.rglob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
add(jars('net.fabricmc','intermediary','1.21.1')[-1])
for artifact in data['artifacts']:
    if artifact['group']=='net.minecraft' or artifact['name']=='fabric-loader' or 'remapped_mods' in artifact['path']:continue
    options=jars(artifact['group'],artifact['name'],artifact['version'])
    classifier=artifact.get('classifier')
    options=[p for p in options if p.name.endswith('-'+classifier+'.jar')] if classifier else [p for p in options if '-natives-' not in p.name]
    if options:add(options[0])
for name in ['tiny-remapper','mapping-io']:add(jars('net.fabricmc',name)[-1])
for p in (modules/'org.lwjgl').rglob('*3.3.3-natives-linux.jar'):add(p)
games=[]
for p in (gradle/'caches/fabric-loom').rglob('minecraft-client.jar'):
    with zipfile.ZipFile(p) as z:
        if 'fzf.class' in z.namelist() and json.loads(z.read('version.json'))['id']=='1.21.1':games.append(p)
assert games
add(games[0])
gametest=jars('net.fabricmc.fabric-api','fabric-gametest-api-v1')
shutil.copy2(gametest[-1],mods/gametest[-1].name)
indexes=list((gradle/'caches/fabric-loom').rglob('indexes/*17*.json'));assert len(indexes)==1
asset_index=indexes[0];assets=asset_index.parent.parent
(game/'options.txt').write_text('renderClouds:false\nrenderDistance:6\nmaxFps:60\n')
java=runtime/'java/bin/java';java.chmod(0o755);xvfb=runtime/'Xvfb';xvfb.chmod(0o755)
env=os.environ.copy();env['LD_LIBRARY_PATH']=str(runtime/'system');env['LIBGL_ALWAYS_SOFTWARE']='1';env['DISPLAY']=':97' if control else ':96'
display=subprocess.Popen([str(xvfb),env['DISPLAY'],'-screen','0','1280x720x24','-nolisten','tcp','-ac'],env=env,stdout=(game/'xvfb.log').open('w'),stderr=subprocess.STDOUT)
command=[str(java),'-Xmx3G','-Dfabric.development=false','-Dslabbed.verticalStepProbe=true','-Dslabbed.realTerrainProbe=true',
         '-cp',os.pathsep.join(map(str,classpath)),'net.fabricmc.loader.impl.launch.knot.KnotClient',
         '--gameDir',str(game),'--assetsDir',str(assets),'--assetIndex',asset_index.stem,'--username','SlabProbe','--version','1.21.1','--accessToken','0']
print('Launching',len(index['files']),'indexed files, latest pack overrides,',release.name,'loader',index['dependencies']['fabric-loader'],flush=True)
try:
    with (game/'production-launch.log').open('w') as log:result=subprocess.run(command,cwd=game,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=300)
finally:display.terminate()
print('Exit',result.returncode,flush=True)
lines=(game/'production-launch.log').read_text(errors='replace').splitlines()
for line in lines:
    if any(x in line for x in ['OVERHEAD','VERTICAL_STEP_CLIENT_PASS','REAL_TERRAIN_SLABS_LOADED','required tests','Exception','Caused by:','ERROR']):print(line[:550])
if control:assert result.returncode!=0 and any('pulled an existing overhead block down' in l for l in lines)
else:assert result.returncode==0 and (game/'vertical-step-pass.txt').is_file()
