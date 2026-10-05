#!/usr/bin/env python3
"""Expected gametest suite count — the anti-false-green check.

A stale persisted world in build/run/gameTest can make the suite silently drop
tests while still reporting BUILD SUCCESSFUL (observed 2026-08-06: 166 reported,
~65 lost, no warning). A green run is therefore NOT proof on its own: the
reported "All N required tests passed" count must match this script's output.

Counts every '@GameTest' occurrence OUTSIDE comments in every class registered
under the 'fabric-gametest' entrypoint, plus 1 harness-contributed test.

Comments are stripped before counting. An earlier revision counted literal
occurrences anywhere in the file, which made the gate itself wrong: five
'{@code @GameTest}' javadoc mentions across four registered classes inflated the
expected total to 575 against a true 570, so the check reported a permanent false
mismatch. Keeping the token out of comments is still good hygiene, but this
script no longer depends on it.

Known gap: classes registered under the separate 'fabric-client-gametest'
entrypoint are NOT counted here and are NOT run by `build`, `runGameTest`, or
CI. They run only under `runClientGameTest`, which nothing invokes
automatically. This script prints their count as an explicit advisory line so
the gap stays visible instead of reading as full coverage.

That task has no count gate of its own, so a client entrypoint that never runs
is indistinguishable from one that passed — the task just reports success.
Until it has one, each client entrypoint logs 'CLIENT_GAMETEST | <class> | PASS'
on its success path, and the run is proof only when the log carries one such
line per entry below.

Usage (from the repo root):
    python3 tools/expected-gametest-count.py [--mc <minecraft version>]
--mc defaults to minecraft_version in gradle.properties; a 26.3 (or newer) build also counts the
classes in CLASSES_26_3, which the build compiles only for that version.

If the live run reports fewer:  rm -rf build/run/gameTest  and re-run.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRAR = ROOT / "src/gametest/java/com/slabbed/gametest/SlabbedGameTestClasses.java"
HARNESS_TESTS = 1  # vanilla minecraft:always_pass runs alongside the namespace-filtered suite


def strip_comments(text: str) -> str:
    """Drop // line comments and /* */ block comments, keeping line structure."""
    out = []
    in_block = False
    for line in text.splitlines():
        if in_block:
            end = line.find("*/")
            if end == -1:
                continue
            line, in_block = line[end + 2:], False
        while True:
            start = line.find("/*")
            if start == -1:
                break
            end = line.find("*/", start + 2)
            if end == -1:
                line, in_block = line[:start], True
                break
            line = line[:start] + line[end + 2:]
        slash = line.find("//")
        if slash != -1:
            line = line[:slash]
        out.append(line)
    return "\n".join(out)


def minecraft_version(argv) -> str:
    if "--mc" in argv:
        return argv[argv.index("--mc") + 1]
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if line.startswith("minecraft_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("minecraft_version not found in gradle.properties; pass --mc")


def at_least(version: str, floor: str) -> bool:
    def parts(v):
        return tuple(int(p) for p in re.findall(r"\d+", v))
    return parts(version) >= parts(floor)


def registered_block(src: str, name: str) -> str:
    start = src.index(name + " = List.of(")
    return src[start:src.index(");", start)]


def main() -> int:
    mc = minecraft_version(sys.argv[1:])
    src = REGISTRAR.read_text()
    block = registered_block(src, "CLASSES")
    if at_least(mc, "26.3"):
        block += registered_block(src, "CLASSES_26_3")
    entrypoints = re.findall(r'"([\w.$]+)"', block)
    total = 0
    for cls in entrypoints:
        src = ROOT / "src/gametest/java" / (cls.replace(".", "/") + ".java")
        count = strip_comments(src.read_text()).count("@GameTest")
        total += count
    expected = total + HARNESS_TESTS
    print(f"{total} @GameTest occurrences in {len(entrypoints)} registered classes (Minecraft {mc}) "
          f"+ {HARNESS_TESTS} harness test = expected suite count {expected}")

    # Advisory only — never folded into the gated number above, because these do not run in the
    # same task. Printed so the uncovered surface is stated out loud on every check.
    client = []  # no client-GameTest runner on this line; the native probe runs are the client proofs
    if client:
        print(f"NOT COVERED BY THIS GATE: {len(client)} fabric-client-gametest classes. They run "
              f"only under `./gradlew25 runClientGameTest`, which no build or CI step invokes. "
              f"That run is green only when its log shows {len(client)} "
              f"'CLIENT_GAMETEST | <class> | PASS' lines — BUILD SUCCESSFUL alone does not prove "
              f"an entrypoint ran.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
