"""Print narrowly scoped vanilla client methods for renderer regression diagnosis."""
import os, pathlib, zipfile
home=pathlib.Path(os.environ['GRADLE_USER_HOME'])
classes={
    'BlockRenderManager.java': ['renderBlock(', 'renderBlockAsEntity('],
    'ClientPlayerInteractionManager.java': ['breakBlock(', 'updateBlockBreakingProgress('],
    'WorldRenderer.java': ['updateBlock(', 'scheduleBlockRerenderIfNeeded(', 'processWorldEvent('],
    'RenderLayers.java': ['getEntityBlockLayer('],
}
for jar in (home/'caches/fabric-loom').rglob('*sources.jar'):
    with zipfile.ZipFile(jar) as z:
        for path in z.namelist():
            name=path.rsplit('/',1)[-1]
            if name not in classes: continue
            lines=z.read(path).decode().splitlines()
            for i,line in enumerate(lines):
                if any(key in line for key in classes[name]) and ('public ' in line or 'private ' in line or 'protected ' in line):
                    end=i+1;depth=line.count('{')-line.count('}')
                    while end<len(lines) and (depth or end==i+1):
                        depth+=lines[end].count('{')-lines[end].count('}');end+=1
                    print('VANILLA_SOURCE',path,'\n'+'\n'.join(lines[i:end]))
