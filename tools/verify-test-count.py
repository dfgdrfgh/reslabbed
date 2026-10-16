from pathlib import Path
import re
sources=list(Path('src/gametest/java').rglob('*.java'))
expected=sum(len(re.findall(r'@GameTest\(',p.read_text())) for p in sources)
log=Path('build/run/gameTest/logs/latest.log').read_text()
actual=re.findall(r'All (\d+) required tests passed',log)
assert actual and int(actual[-1])==expected, f'Expected {expected} tests, got {actual}'
print(f'PHYSICAL_SERVER_PASS {expected}/{expected}')
