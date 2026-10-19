"""Stage hash-verified published fork JARs and the indexed pack files."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,shutil,urllib.request,urllib.error,zipfile
from pack_source import resolve_source

root=Path.cwd()
fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
parser=argparse.ArgumentParser()
parser.add_argument('--source-pack',type=Path,default=os.environ.get('SLABBED_FULL_PACK'),
                    help='Local copy of the exact supplied complete pack; never uploaded')
args=parser.parse_args()
source_pack=None
source_path=resolve_source(fixture,root/'build/private-source-pack.mrpack',args.source_pack,
                           os.environ.get('SLABBED_FULL_PACK_URL'))
if source_path:
    source_pack=zipfile.ZipFile(source_path)
out=root/'build/full-pack-baseline';out.mkdir(parents=True,exist_ok=True)
# A stale baseline must never satisfy a newer fixture's count or file checks.
shutil.rmtree(out);out.mkdir(parents=True)
forks=root/'build/pack-forks';forks.mkdir(exist_ok=True)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args):return None
def public_artifact(repo,artifact):
    headers={'Accept':'application/vnd.github+json'}
    if os.environ.get('GH_TOKEN'):headers['Authorization']='Bearer '+os.environ['GH_TOKEN']
    request=urllib.request.Request(f'https://api.github.com/repos/{repo}/actions/artifacts/{artifact}/zip',headers=headers)
    opener=urllib.request.build_opener(NoRedirect())
    try:response=opener.open(request,timeout=90)
    except urllib.error.HTTPError as error:
        if error.code in (301,302,303,307,308):
            response=urllib.request.urlopen(error.headers['Location'],timeout=90)
        elif error.code in (401,403,404):
            response=urllib.request.urlopen(f'https://nightly.link/{repo}/actions/artifacts/{artifact}.zip',timeout=90)
        else:raise
    destination=root/'build'/f'public-{artifact}.zip'
    with response,destination.open('wb') as stream:
        while chunk:=response.read(1024*1024):stream.write(chunk)
    return destination

for entry in fixture['forks']:
    data=None
    if source_pack:
        data=source_pack.read('overrides/mods/'+entry['file'])
        assert hashlib.sha256(data).hexdigest()==entry['sha256'],entry['id']
    else:
        archive_path=public_artifact(entry['repo'],entry['artifact'])
        with zipfile.ZipFile(archive_path) as archive:
            for name in archive.namelist():
                if not name.endswith('.jar'):continue
                candidate=archive.read(name)
                if hashlib.sha256(candidate).hexdigest()==entry['sha256']:
                    data=candidate;break
    assert data is not None,'Published artifact does not match pack: '+entry['id']
    (forks/entry['file']).write_bytes(data)
    destination=out/'mods'/entry['file'];destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_bytes(data)
    print('PACK_FORK_SHA256_MATCH',entry['id'],entry['version'],'source=pack' if source_pack else 'source=published',flush=True)

baseline_entry=fixture.get('storedCoreBaseline')
if baseline_entry:
    archive_path=public_artifact(baseline_entry['repo'],baseline_entry['artifact'])
    with zipfile.ZipFile(archive_path) as archive:
        matches=[archive.read(name) for name in archive.namelist() if name.endswith('.jar')
                 and hashlib.sha256(archive.read(name)).hexdigest()==baseline_entry['sha256']]
    assert len(matches)==1,'Stored-core baseline artifact hash mismatch'
    (forks/baseline_entry['file']).write_bytes(matches[0])
    print('STORED_CORE_BASELINE_SHA256_MATCH',baseline_entry['file'],flush=True)

baseline_entry=fixture.get('feedbackBaseline')
if baseline_entry:
    archive_path=public_artifact(baseline_entry['repo'],baseline_entry['artifact'])
    with zipfile.ZipFile(archive_path) as archive:
        matches=[archive.read(name) for name in archive.namelist() if name.endswith('.jar')
                 and hashlib.sha256(archive.read(name)).hexdigest()==baseline_entry['sha256']]
    assert len(matches)==1,'Feedback baseline artifact hash mismatch'
    (root/'build/feedback-control.jar').write_bytes(matches[0])
    print('FEEDBACK_BASELINE_SHA256_MATCH',baseline_entry['file'],flush=True)

def download(entry):
    target=out/entry['path'];target.parent.mkdir(parents=True,exist_ok=True)
    error=None
    for url in entry['downloads']:
        try:
            data=urllib.request.urlopen(url,timeout=120).read()
            for algorithm,expected in entry['hashes'].items():
                assert hashlib.new(algorithm,data).hexdigest()==expected,entry['path']
            target.write_bytes(data);return
        except Exception as exc:error=exc
    raise RuntimeError('Indexed pack download failed: '+entry['path']) from error
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    list(pool.map(download,fixture['downloads']))
for entry in fixture.get('overrides',[]):
    if source_pack:
        data=source_pack.read('overrides/'+entry['path'])
        for algorithm,expected in entry['hashes'].items():
            assert hashlib.new(algorithm,data).hexdigest()==expected,entry['path']
        target=out/entry['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    else:download(entry)
for name,data in fixture['configs'].items():
    target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data.encode('utf-8'))
loader=fixture['dependencies']['fabric-loader']
(root/'build/full-pack-loader.jar').write_bytes(urllib.request.urlopen(
    f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar',timeout=90).read())
mod_count=len(list((out/'mods').glob('*.jar')))
resource_pack_count=len(list((out/'resourcepacks').glob('*.zip')))
assert mod_count==fixture['modCount'],(mod_count,fixture['modCount'])
assert resource_pack_count==fixture['resourcePackCount'],(resource_pack_count,fixture['resourcePackCount'])
(out/'pack-staging.json').write_text(json.dumps({'sourcePackSha256':fixture['sourcePackSha256'],'modCount':mod_count,'resourcePackCount':resource_pack_count,'complete':True})+'\n')
if source_pack:source_pack.close()
print('UPDATED_PACK_BASELINE_STAGED complete=true mods='+str(mod_count)+' resourcepacks='+str(resource_pack_count)+' loader='+loader,flush=True)
