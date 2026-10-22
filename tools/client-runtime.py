"""Run the remapped physical-cell probe in an unmodified production client namespace."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile,urllib.request,urllib.error,io

parser=argparse.ArgumentParser();parser.add_argument('--pack',action='store_true');parser.add_argument('--sodium',action='store_true');parser.add_argument('--lithium',action='store_true');parser.add_argument('--control',action='store_true');parser.add_argument('--optimized',action='store_true');parser.add_argument('--backport',action='store_true');parser.add_argument('--asyncparticles',action='store_true');args=parser.parse_args()
if args.asyncparticles:args.optimized=True
if args.backport:args.optimized=True
if args.optimized: args.lithium=True
if args.lithium:
    assert not args.pack,'Lithium subset requires its own fresh runtime'
    args.sodium=True
root=Path.cwd();runtime=json.loads((root/'build/probe/dependencies.json').read_text())
home=Path(runtime['gradleHome']);modules=home/'caches/modules-2/files-2.1'
game=root/('build/run/physical-asyncparticles' if args.asyncparticles else 'build/run/physical-backport' if args.backport else 'build/run/physical-optimized' if args.optimized else 'build/run/physical-pack' if args.pack else 'build/run/physical-lithium' if args.lithium else 'build/run/physical-sodium' if args.sodium else 'build/run/physical-indigo')
if args.control: game=root/'build/run/physical-control'
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
if args.control:
    assert args.pack,'Control requires the complete pack'
    release=[root/'build/feedback-control.jar']
if args.pack:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    staged=json.loads((root/'build/full-pack-baseline/pack-staging.json').read_text())
    assert staged['complete'] and staged['sourcePackSha256']==fixture['sourcePackSha256'],'Client requires the exact complete latest pack'
    assert staged['modCount']==fixture.get('sourceModCount',fixture['modCount']) and staged['resourcePackCount']==fixture['resourcePackCount'],staged
    for source in (root/'build/full-pack-baseline').rglob('*'):
        if source.is_file():
            target=game/source.relative_to(root/'build/full-pack-baseline');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    add(root/'build/full-pack-loader.jar')
    print('PHYSICAL_PACK_SOURCE_SHA256',staged['sourcePackSha256'],'mods='+str(staged['modCount']),
          'resourcepacks='+str(staged['resourcePackCount']),flush=True)
else:
    add(jars('net.fabricmc','fabric-loader')[-1])
    api=jars('net.fabricmc.fabric-api','fabric-api',runtime['fabricVersion'])[-1];shutil.copy2(api,mods/api.name)
if args.sodium:
    url='https://cdn.modrinth.com/data/AANobbMI/versions/SMxNOGZ6/sodium-fabric-0.8.13%2Bmc1.21.1.jar'
    data=urllib.request.urlopen(url).read()
    assert hashlib.sha512(data).hexdigest()=='8614a374593698069a80ac74de8cd9c28f3928d16819a0e0d6320b538e823f7e54e8be06d5ea5a9c268beb0e02073209d1f831a02dede851588b972367680853'
    (mods/'sodium.jar').write_bytes(data)
if args.lithium:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    lithium=next(entry for entry in fixture['downloads'] if entry['path'].startswith('mods/lithium-'))
    data=urllib.request.urlopen(lithium['downloads'][0]).read()
    for algorithm,expected_hash in lithium['hashes'].items():
        assert hashlib.new(algorithm,data).hexdigest()==expected_hash,'Lithium fixture hash mismatch'
    (mods/Path(lithium['path']).name).write_bytes(data)
if args.optimized:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    for prefix in ['moreculling-', 'cloth-config-', 'yet_another_config_lib_v3-']:
        entry=next(entry for entry in fixture['downloads'] if Path(entry['path']).name.startswith(prefix))
        data=urllib.request.urlopen(entry['downloads'][0]).read()
        for algorithm,expected_hash in entry['hashes'].items():
            assert hashlib.new(algorithm,data).hexdigest()==expected_hash,'Render subset hash mismatch'
        (mods/Path(entry['path']).name).write_bytes(data)
    for path in ['config/lithium.properties','config/moreculling.toml']:
        target=game/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(fixture['configs'][path])
    print('PHYSICAL_OPTIMIZED_SUBSET Sodium Lithium MoreCulling with pinned configuration libraries',flush=True)
if args.backport:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    entry=next(entry for entry in fixture['forks'] if entry['id']=='vanillabackport')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args):return None
    headers={'Accept':'application/vnd.github+json'}
    if os.environ.get('GH_TOKEN'):headers['Authorization']='Bearer '+os.environ['GH_TOKEN']
    request=urllib.request.Request(f"https://api.github.com/repos/{entry['repo']}/actions/artifacts/{entry['artifact']}/zip",headers=headers)
    try:response=urllib.request.build_opener(NoRedirect()).open(request,timeout=90)
    except urllib.error.HTTPError as error:
        if error.code in (301,302,303,307,308):response=urllib.request.urlopen(error.headers['Location'],timeout=90)
        elif error.code in (401,403,404):response=urllib.request.urlopen(f"https://nightly.link/{entry['repo']}/actions/artifacts/{entry['artifact']}.zip",timeout=90)
        else:raise
    with response:archive_data=response.read()
    with zipfile.ZipFile(io.BytesIO(archive_data)) as archive:
        data=archive.read(next(name for name in archive.namelist() if Path(name).name==entry['file']))
    assert hashlib.sha256(data).hexdigest()==entry['sha256'],'Backport fixture hash mismatch'
    (mods/entry['file']).write_bytes(data)
    for path in ['config/vanillabackport-client.toml','config/vanillabackport-common.toml']:
        target=game/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(fixture['configs'][path])
    print('PHYSICAL_BACKPORT_SUBSET exact pinned VanillaBackport with Sodium Lithium MoreCulling',flush=True)
if args.asyncparticles:
    fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
    entry=next(entry for entry in fixture['downloads'] if Path(entry['path']).name.startswith('AsyncParticles-'))
    data=urllib.request.urlopen(entry['downloads'][0]).read()
    for algorithm,expected_hash in entry['hashes'].items():
        assert hashlib.new(algorithm,data).hexdigest()==expected_hash,'AsyncParticles fixture hash mismatch'
    (mods/Path(entry['path']).name).write_bytes(data)
    for path in ['config/asyncparticles/asyncparticles.json','config/asyncparticles/asyncparticles-mixin.properties']:
        target=game/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(fixture['configs'][path])
    print('PHYSICAL_ASYNC_PARTICLES_SUBSET exact pinned AsyncParticles with original cache and GPU settings',flush=True)
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
options=game/'options.txt'
settings=dict(line.split(':',1) for line in options.read_text().splitlines() if ':' in line) if options.exists() else {}
settings.update(renderClouds='false',renderDistance='6',maxFps='60')
options.write_text(''.join(key+':'+value+'\n' for key,value in settings.items()))
print('PHYSICAL_JAR_SHA256',hashlib.sha256(release[0].read_bytes()).hexdigest(),flush=True)
for phase in (['fresh'] if args.control else ['fresh','reopen']):
    marker=game/'physical-pass.txt';marker.unlink(missing_ok=True)
    command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xmx4G','-Dfabric.development=false','-Dslabbed.physicalProbe=true',
             '-Dslabbed.controlProbe='+str(args.control).lower(),
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
    if args.control:
        assert result.returncode==0 and marker.exists(),'Control did not finish its blocked-placement check'
        assert any('PHYSICAL_CLEARANCE_OBSERVED accepted=true control=true' in line for line in lines),'Control unexpectedly rejected the obstructed click'
        assert any('PHYSICAL_CLEARANCE_SERVER_PACKET_REJECTED' in line for line in lines),'Control did not receive server rejection'
        assert marker.read_text().strip()=='PHYSICAL_CONTROL_REPRODUCED_BLOCKED_PLACEMENT','Control marker mismatch'
        print(marker.read_text().strip(),flush=True)
        continue
    if result.returncode!=0 or not marker.exists():print('\n'.join(lines[-100:]));raise AssertionError('Physical client failed '+phase+' exit='+str(result.returncode))
    assert any('PHYSICAL_REDSTONE_MODEL_HEIGHT_PASS' in line for line in lines),'Redstone model height checks did not complete'
    assert any('PHYSICAL_DROPPED_ITEM_LIGHT_PASS' in line for line in lines),'Dropped item light checks did not complete'
    assert any('PHYSICAL_SLAB_DUST_LIGHT_PASS' in line for line in lines),'Slab dust light checks did not complete'
    assert any('PHYSICAL_SHARED_SLAB_PACKET_PASS' in line for line in lines),'Shared slab packet checks did not complete'
    assert any('PHYSICAL_REAL_SLAB_FEEDBACK_PASS' in line for line in lines),'Actual slab debug and debris checks did not complete'
    assert any('PHYSICAL_REAL_SLAB_DUST_COLLISION samples=9' in line for line in lines),'Emitted break dust collision checks did not complete'
    assert any('PHYSICAL_SLAB_DELAYED_FEEDBACK_OBSERVED ticks=4' in line for line in lines),'Delayed server confirmation debris check did not complete'
    assert any('PHYSICAL_DEFERRED_DEBRIS_ORDER_PASS' in line for line in lines),'Deferred debris packet order and lifecycle checks did not complete'
    assert any('PHYSICAL_SURFACE_DUST_PASS' in line for line in lines),'Ceiling and side-lit physical debris checks did not complete'
    assert any('PHYSICAL_STACK_DUST_PASS' in line for line in lines),'Odd/even stack debris checks did not complete'
    if any(entry['id']=='asyncparticles' for entry in expected):
        assert any('PHYSICAL_ASYNC_DUST_CACHE_PASS' in line for line in lines),'Cached and initial GPU light controls did not complete'
    assert any('PHYSICAL_SIDE_SLAB_PACKET_PASS' in line for line in lines),'Side slab placement packets did not complete'
    assert sum('PHYSICAL_SLAB_DUST_COLLISION_PASS' in line for line in lines)>=5,'Soil surface collision checks did not complete'
    assert any('PHYSICAL_OFFSET_PISTON_CONTACT_PASS' in line for line in lines),'Offset piston contact checks did not complete'
    assert any('PHYSICAL_NATIVE_PISTON_CYCLE_PASS' in line for line in lines),'Native piston client cycle did not complete'
    assert any('PHYSICAL_DIAGNOSTIC_REPORT_PASS' in line for line in lines),'Optional recorder report probe did not complete'
    assert any('PHYSICAL_PISTON_CAPTURE_PASS' in line for line in lines),'Authoritative moving pose capture probe did not complete'
    assert any('PHYSICAL_PISTON_PACKET_ORDER_PASS' in line for line in lines),'Piston packet ordering probe did not complete'
    assert any('PHYSICAL_NATIVE_FACE_CULLING_PASS' in line for line in lines),'Native face culling probe did not complete'
    for pose in ['extended','retracted']:
        assert any('PHYSICAL_PISTON_MODELS_PASS '+pose+' all_six_directions' in line for line in lines),'Piston model height probe did not complete: '+pose
    if args.lithium:
        assert any('PHYSICAL_NATIVE_PISTON_CYCLE_PASS lithium=true' in line for line in lines),'Lithium piston initialization not exercised'
    if phase=='fresh':
        row=next(line for line in reversed(lines) if 'PHYSICAL_TRANSITION_OBSERVED' in line)
        assert ('terrainLighting=false' if args.control else 'terrainLighting=true') in row,row
        deferred=int(row.rsplit('deferred=',1)[1])
        assert (deferred>0 if args.control else deferred==0),row
        if args.pack or args.sodium:
            assert int(row.split('queued=',1)[1].split()[0])>0,row
            delayed=int(row.split('delayed=',1)[1].split()[0])
            assert (delayed>0 if args.control else delayed==0),row
        print('PHYSICAL_TRANSITION_CHECK_PASS control='+str(args.control),flush=True)
    print(marker.read_text().strip(),flush=True)
print('PHYSICAL_CLIENT_PHASES_PASS pack='+str(args.pack)+' sodium='+str(args.sodium)+' lithium='+str(args.lithium)+' control='+str(args.control),flush=True)
