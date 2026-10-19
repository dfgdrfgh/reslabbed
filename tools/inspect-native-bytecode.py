"""Print exact mapped native methods used by the runtime build."""
import os
import subprocess
import sys
import re
from pathlib import Path
from zipfile import ZipFile, BadZipFile

roots = [Path('.gradle'), Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches']
for name in (sys.argv[1:] or ['net.minecraft.block.PressurePlateBlock', 'net.minecraft.block.WeightedPressurePlateBlock']):
    target = name.partition('#')[0].replace('.', '/') + '.class'
    found = False
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
            print('NATIVE_BYTECODE ' + name, flush=True)
            class_name, _, methods = name.partition('#')
            result = subprocess.run(['javap', '-c', '-p', '-classpath', str(jar), class_name], check=True, capture_output=True, text=True)
            if not methods:
                print(result.stdout, flush=True)
            else:
                keep = False
                for line in result.stdout.splitlines():
                    if re.match(r'^  (public|protected|private|static)', line):
                        keep = any(re.search(r'\b'+re.escape(method)+r'\(', line) for method in methods.split(','))
                    if keep:
                        print(line, flush=True)
            found = True
            break
        if found:
            break
    if not found:
        raise SystemExit('Mapped Minecraft class not found: ' + target)
