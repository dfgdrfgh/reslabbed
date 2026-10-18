"""Print exact mapped native methods used by the runtime build."""
import os
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile, BadZipFile

roots = [Path('.gradle'), Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches']
target = 'net/minecraft/block/PressurePlateBlock.class'
for root in roots:
    for jar in root.rglob('*.jar'):
        if 'minecraft' not in str(jar).lower() or 'sources' in jar.name:
            continue
        try:
            with ZipFile(jar) as archive:
                if target not in archive.namelist():
                    continue
        except (BadZipFile, OSError):
            continue
        for name in (sys.argv[1:] or ['net.minecraft.block.PressurePlateBlock', 'net.minecraft.block.WeightedPressurePlateBlock']):
            print('NATIVE_BYTECODE ' + name, flush=True)
            subprocess.run(['javap', '-c', '-p', '-classpath', str(jar), name], check=True)
        raise SystemExit(0)
raise SystemExit('Mapped Minecraft JAR not found')
