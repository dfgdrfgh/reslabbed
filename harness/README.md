# Per-version harness

A ranged Slabbed jar declares several Minecraft versions. Each declared version is proven against
the EXACT release jar (plus the test kit, which carries the dev-only classes the suite needs):

- **Server GameTest legs** (default and deep-dy-alphabet) run in Loom's development environment for
  that version: Loom remaps the release jar and test kit from intermediary to the version's named
  mappings, as it does for any mod dependency, and the shared `src/gametest` sources are compiled
  against that version. Vanilla's GameTest runner cannot parse the test annotation in a production
  runtime, so this is the server proof. Both legs must report the count printed by
  `tools/expected-gametest-count.py`.
- **Production client leg**: a real client for that version boots from the intermediary jars with
  the release jar and the harness's own boot test (`harness/boot-test`): every client mixin must
  apply, a world is joined, a lowered torch must read its stored height on the client, and the
  version-selected crosshair pick must target it where it is drawn. The line's own client GameTests
  cannot run there, because vanilla's test runner cannot parse the test annotation in a production
  runtime; they stay a dev-run proof on the build version.
- **Linkage scan**: `tools/linkage-scan.py` resolves every Minecraft reference and refmap entry of
  the release jar against the version's intermediary names.

```bash
./gradlew build remapTestkitJar                      # from the line root: release jar + test kit
V=fabric-1.21.5
./gradlew -p harness/$V runGameTest verifyHarnessGameTestCount -Pleg=default
./gradlew -p harness/$V runGameTest verifyHarnessGameTestCount -Pleg=deepdy -Dslabbed.deepDyAlphabet=true
./gradlew -p harness/$V runProductionClientGameTest verifyHarnessClientLeg
python3 tools/linkage-scan.py build/libs/<jar> <intermediary.tiny> <official client jar>
```

Receipts land in `harness/<version>/build/harness-receipts/`. Each harness is its own Gradle build
(its own `settings.gradle`), so it never shares the line's Loom configuration or run directories;
its run directories are ignored.
