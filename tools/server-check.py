"""Bound the server check and print JVM evidence if compilation or gameplay stalls."""
import os
import signal
import subprocess

command = ['bash', 'gradlew', '--no-daemon', '--console=plain', 'build', 'runGameTest', 'writeProbeRuntime']
process = subprocess.Popen(command, start_new_session=True)
try:
    raise SystemExit(process.wait(timeout=360))
except subprocess.TimeoutExpired:
    print('SERVER_CHECK_TIMEOUT: collecting JVM thread dumps', flush=True)
    listing = subprocess.run(['jcmd', '-l'], capture_output=True, text=True, timeout=15)
    print(listing.stdout, flush=True)
    for line in listing.stdout.splitlines():
        fields = line.split(maxsplit=1)
        if len(fields) != 2 or not fields[0].isdigit() or 'sun.tools.jcmd.JCmd' in fields[1]:
            continue
        try:
            subprocess.run(['jcmd', fields[0], 'Thread.print'], timeout=20, check=False)
        except subprocess.TimeoutExpired:
            print('JVM_DUMP_TIMEOUT ' + fields[0], flush=True)
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()
    raise SystemExit(124)
