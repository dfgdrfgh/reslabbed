"""Stage the indexed pack files and content-verified pinned fork builds."""
from pathlib import Path
import concurrent.futures,hashlib,json,shutil,urllib.request

root=Path.cwd()
fixture=json.loads((root/'tools/full-pack-fixture.json').read_text())
out=root/'build/full-pack-baseline';out.mkdir(parents=True,exist_ok=True)
def download(entry):
    target=out/entry['path'];target.parent.mkdir(parents=True,exist_ok=True)
    error=None
    for url in entry['downloads']:
        try:
            data=urllib.request.urlopen(url,timeout=120).read()
            for algorithm,expected in entry['hashes'].items():
                assert hashlib.new(algorithm,data).hexdigest()==expected,entry['path']
            target.write_bytes(data);return
        except Exception as e:error=e
    raise RuntimeError('Indexed pack download failed: '+entry['path']) from error
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    list(pool.map(download,fixture['downloads']))
for entry in fixture['forks']:
    source=root/'pack-forks'/entry['file']
    assert source.is_file(),source
    target=out/'mods'/entry['file'];target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source,target)
for name,data in fixture['configs'].items():
    target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(data)
print('UPDATED_PACK_BASELINE_STAGED mods='+str(len(list((out/'mods').glob('*.jar'))))
      +' loader='+fixture['dependencies']['fabric-loader'])
loader=fixture['dependencies']['fabric-loader']
(root/'build/full-pack-loader.jar').write_bytes(urllib.request.urlopen(
    f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar',timeout=90).read())
