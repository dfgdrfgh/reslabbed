"""Inspect native adapter APIs from Loom's generated sources during a regression run."""
import os,pathlib,zipfile
targets={
 'BedBlock.java':{'onUse','onPlaced','getPlacementState'},
 'AbstractSignBlock.java':{'onUse','onUseWithItem','getStateForNeighborUpdate'},
 'SignBlock.java':{'getPlacementState','canPlaceAt'},
 'BlockItem.java':{'place','postPlacement'},
 'World.java':{'setBlockState','raycast'},
 'WorldChunk.java':{'setBlockState'},
 'ParticleManager.java':{'addBlockBreakingParticles','addBlockBreakParticles'},
}
seen=set()
for root in [pathlib.Path('.gradle'),pathlib.Path(os.environ['GRADLE_USER_HOME'])/'caches/fabric-loom']:
 for jar in root.rglob('*sources.jar'):
  with zipfile.ZipFile(jar) as z:
   for name in z.namelist():
    short=name.rsplit('/',1)[-1]
    if short not in targets or name in seen:continue
    seen.add(name);lines=z.read(name).decode().splitlines()
    for i,line in enumerate(lines):
     if ('public ' in line or 'protected ' in line or 'private ' in line) and any(key+'(' in line for key in targets[short]):
      end=i+1;depth=line.count('{')-line.count('}')
      while end<len(lines) and (depth or end==i+1):
       depth+=lines[end].count('{')-lines[end].count('}');end+=1
      print('NATIVE_ADAPTER_SOURCE',name,'\n'+'\n'.join(lines[i:end]))
