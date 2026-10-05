#!/usr/bin/env bash
# Mixin-target scan for this NeoForge line: every target string of every registered mixin must
# resolve on the game the build targets. NeoForge patches add members (BlockState.isScaffolding),
# so the scanned jar is the patched Minecraft jar ModDevGradle produced for that build plus the
# NeoForge universal jar's classes. The skips are exactly the mixins the config plugins withhold on
# that version; each is justified in its plugin.
#
# Usage: tools/scan-mixin-targets.sh [<minecraft version> <neoforge version>]
# Defaults come from gradle.properties. Run after `./gradlew build` for that version (the patched
# jar lives under build/moddev/artifacts). Exit 0 only when nothing is unresolved.
set -euo pipefail
cd "$(dirname "$0")/.."
MC="${1:-$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)}"
NEO="${2:-$(grep '^neo_version=' gradle.properties | cut -d= -f2)}"
GUH="${GRADLE_USER_HOME:-$HOME/.gradle}"
PATCHED="build/moddev/artifacts/minecraft-patched-$NEO-merged.jar"
UNIVERSAL="$(find "$GUH/caches/modules-2" -name "neoforge-$NEO-universal.jar" | head -1)"
if [ ! -f "$PATCHED" ]; then echo "missing $PATCHED (build this version first)" >&2; exit 2; fi
if [ -z "$UNIVERSAL" ]; then echo "NeoForge $NEO universal jar not in the Gradle cache" >&2; exit 2; fi
WORK="build/mixin-scan/$NEO"
rm -rf "$WORK" && mkdir -p "$WORK/x"
( cd "$WORK/x" && unzip -qo "$OLDPWD/$PATCHED" '*.class' && unzip -qn "$UNIVERSAL" '*.class' && zip -qr ../combined.jar . )
CONFIGS=(
  --config src/main/resources/slabbed.mixins.json
  --config src/client/resources/slabbed.client.mixins.json
  --config src/diagnostics/resources/slabbed.diagnostics.client.mixins.json
  --config src/gametest/resources/slabbed-test31.mixins.json
  --config src/gametest/resources/slabbed.rig.mixins.json
)
case "$MC" in
  26.2) SKIPS=(--skip CushionRestsOnDrawnTopMixin --skip RedstoneWireBlockMixin
               --skip UseItemAckArmMixin --skip PaintingRigDropCaptureMixin) ;;
  *)    SKIPS=(--skip LegacyRedStoneWireBlockMixin --skip LegacyUseItemAckArmMixin) ;;
esac
python3 tools/mixin-target-scan.py "$WORK/combined.jar" src/main/java src/client/java src/diagnostics/java src/gametest/java \
  "${CONFIGS[@]}" "${SKIPS[@]}"
