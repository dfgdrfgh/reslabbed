"""Stage hash-matched public artifacts without publishing local account or cache data."""
from pathlib import Path
import hashlib,json,os,tarfile,urllib.request,urllib.error,zipfile

root=Path.cwd()
fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None
opener=urllib.request.build_opener(NoRedirect())
def download(repo,artifact):
    request=urllib.request.Request(f'https://api.github.com/repos/{repo}/actions/artifacts/{artifact}/zip',
            headers={'Authorization':'Bearer '+os.environ['GH_TOKEN'],'Accept':'application/vnd.github+json'})
    try: response=opener.open(request,timeout=90)
    except urllib.error.HTTPError as e:
        if e.code not in (301,302,303,307,308):
            raise RuntimeError(f'Public build download unavailable: {repo} artifact {artifact}, HTTP {e.code}') from None
        response=urllib.request.urlopen(e.headers['Location'],timeout=90)
    target=root/f'artifact-{artifact}.zip'
    with response,target.open('wb') as output:
        while chunk:=response.read(1024*1024): output.write(chunk)
    return target

overrides={}
for name,entry in fixture['overrides'].items():
    path=download(entry['repo'],entry['artifact'])
    found=None
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if not item.filename.endswith('.jar'):continue
            data=archive.read(item.filename)
            if hashlib.sha256(data).hexdigest()==entry['sha256']:found=data;break
    assert found is not None, f'Public artifact did not match the pack JAR: {name}'
    overrides[name]=found
    print('Matched pack JAR:',name,flush=True)
control=download('dfgdrfgh/reslabbed',11932900631)
with zipfile.ZipFile(control) as archive:
    jars=[n for n in archive.namelist() if n.endswith('.jar')];assert len(jars)==1
    (root/'control.jar').write_bytes(archive.read(jars[0]))
assert hashlib.sha256((root/'control.jar').read_bytes()).hexdigest()=='62baf089d511738cda3e82cc0f8021b8af8575e497e1eb8c984a364225a9e3d1'
archive_path=download('dfgdrfgh/reslabbed',11943913100)
runtime=root/'ghost-fullpack-runtime';runtime.mkdir(exist_ok=True)
with zipfile.ZipFile(archive_path) as archive:archive.extractall(runtime)
with tarfile.open(runtime/'portable-client-runtime.tar.gz') as archive:archive.extractall(runtime)
with zipfile.ZipFile(root/'full-pack.mrpack','w',zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('modrinth.index.json',json.dumps({'dependencies':{'minecraft':'1.21.1','fabric-loader':'0.19.5'},'files':fixture['downloads']}))
    for name,data in overrides.items():archive.writestr('overrides/mods/'+name,data)
    archive.writestr('overrides/mods/slabbed-control.jar',(root/'control.jar').read_bytes())
    for name,data in fixture['configs'].items():archive.writestr('overrides/'+name,data)
print('Full pack inputs staged: 56 indexed mod JARs, 6 fork JARs, relevant pack configs.',flush=True)
