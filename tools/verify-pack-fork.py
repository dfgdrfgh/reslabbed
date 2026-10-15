"""Verify a rebuilt dependency against the pack's code and resources, ignoring ZIP metadata."""
from pathlib import Path
import hashlib,io,json,shutil,sys,zipfile

def content_digest(blob):
    h=hashlib.sha256()
    with zipfile.ZipFile(io.BytesIO(blob)) as archive:
        for name in sorted(n for n in archive.namelist() if not n.endswith('/')):
            data=archive.read(name)
            if name.endswith('.jar'):
                try:data=bytes.fromhex(content_digest(data))
                except zipfile.BadZipFile:pass
            elif name.endswith('.json'):
                try:data=json.dumps(json.loads(data),sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()
                except (ValueError,UnicodeError):pass
            h.update(name.encode()+b'\0'+len(data).to_bytes(8,'big')+data)
    return h.hexdigest()

if __name__=='__main__':
    fixture=json.loads(Path('tools/full-pack-fixture.json').read_text())
    entry=next(e for e in fixture['forks'] if e['kind']==sys.argv[1])
    folder=Path('dependency')
    candidates=[]
    for p in folder.rglob('*.jar'):
        if p.name!=entry['file']:continue
        if '/.gradle/' in str(p) or '/vendor/' in str(p):continue
        candidates.append(p)
    exact=[]
    for jar in candidates:
        if content_digest(jar.read_bytes())==entry['content_digest']:exact.append(jar)
    if not exact:
        raise AssertionError('Pinned rebuild did not match the pack code/resources: '+entry['id']
                             +' candidates='+str(candidates))
    out=Path('pack-forks');out.mkdir(exist_ok=True)
    shutil.copy2(exact[0],out/entry['file'])
    print('PACK_FORK_CONTENT_MATCH',entry['id'],entry['version'],entry['content_digest'])
