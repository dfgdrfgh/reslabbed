# Slabbed agent guide

Read LAW.md before changing placement, storage, geometry or interactions. Positions are decided at placement and remain fixed. Neighbor changes may remove a decoration under vanilla survival rules; they may not move it.

The physical-cell implementation targets Minecraft 1.21.1 Fabric only. Other version branches must not be changed as part of work on this line. DY_SPEC.md describes the persistent format and bounded geometry.

## Verification

Run `./gradlew build runGameTest` with a clean `build/run/gameTest` directory, then `python3 tools/verify-test-count.py`. A reported server count differing from registered tests is a failed run. `build` also runs the standalone half-grid assertions. Real packet checks, rendering and saved-world reopening run through the production client probe. Do not describe server item use as a packet test.

Use the latest supplied complete modpack for compatibility testing. State explicitly when the full pack was unavailable or only partly tested. Publish only the changed mod's production JAR. Keep diagnostics in Actions logs; do not publish the pack or diagnostic artifacts with the mod.

## Public repository and commits

Keep tracked files impersonal: no personal names, local machine usernames, absolute home-directory paths or development diary narrative. Comments describe invariants. Write process notes outside the repository in Slabbed-notes beside the checkout roots. Do not add process documents to the tracked tree.

Keep this file identical to CLAUDE.md. Configure `core.hooksPath tools/hooks`. Every commit changing src/main requires a `LAW-PREFLIGHT: n|y` trailer. The commit hook enforces documentation references, a fresh external behavior ledger and impersonal tracked content. Never bypass the hook to hide an unverified behavior change.
