"""Print the exact mapped pressure-plate methods used by the runtime build."""
import os
import subprocess
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
        for name in ['PressurePlateBlock', 'WeightedPressurePlateBlock']:
            print('NATIVE_BYTECODE ' + name, flush=True)
            subprocess.run(['javap', '-c', '-p', '-classpath', str(jar), 'net.minecraft.block.' + name], check=True)
        raise SystemExit(0)
raise SystemExit('Mapped Minecraft JAR not found')
