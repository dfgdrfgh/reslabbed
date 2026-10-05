# Slabbed — agent guide

Rules for ANY coding agent working in this repo (Claude, Codex, or otherwise). AGENTS.md is an
identical copy for tools that read that name; edit both together.

## The two laws

1. **LAW 1 — placement is permanent.** Read `LAW.md` before touching anything in the placement or
   height-resolution path. `LAW.md` is supreme; no other doc may redefine it. Its enforcement is
   `NeighborUpdateInvarianceTest` (the S-2 gate), which is **blocking by default** — a violation
   fails the build. `-Dslabbed.lawGate=false` downgrades to a printed inventory and is only for
   fixing a deliberate new RED forward, never for landing one.

2. **The discretion law (maintainer ruling, 2026-08-07) — this repo is public and stays
   impersonal.** No personal names, no machine-local usernames or absolute home-directory paths,
   no session/recorder identifiers, and no development-diary narrative in ANY tracked file —
   source comments included. Cite decisions as "maintainer ruling, \<date\>". The commit hook's
   S-6 gate rejects violations mechanically. Development narrative (handoffs, live-test ledgers,
   incident logs) lives OUTSIDE the repo in the maintainer's local notes directory
   (`Slabbed-notes/` beside the worktree roots; the hook's S-5 gate knows the path). Do not
   create new process docs in the repo — write them to the notes directory.

## Comments document invariants, not history

A comment states what must stay true and, at most, one dated ruling-of-record pointer. Session
play-by-play, quotes, coordinates, and run ids belong in the local notes ledger and git history.
Keep the "do not re-add X" guard comments — they are anti-regression tripwires.

## Verification is not optional

- `./gradlew build runGameTest` on a CLEAN run dir (`rm -rf build/run/gameTest` first — a stale
  dir silently drops tests and still prints green).
- The reported count MUST match `python3 tools/expected-gametest-count.py`. A green with the
  wrong count is a false green.
- Never write the literal `@GameTest` token in comments of registered test classes — the count
  script counts occurrences.
- The release jar is gated by a closed-world allowlist (`RELEASE_ALLOWLIST.md`) checked during
  `build`; debug/dev tooling ships in every jar default-off, but the file-writing audit/recorder
  packages never ship.

## Line notes (Fabric 1.21.4)

- Java 21. Fabric API 0.119.4+1.21.4 is the renderer API floor: `OffsetBlockStateModel` is a vanilla
  `WrapperBakedModel` implementing the Fabric block-quad entry point (emitter parameter, early cull test).
  The cull test is wrapped so lowered-vs-flat seam faces survive early culling; the fence and rail
  geometry capture the wrapped model's quads into a `MutableMesh` and re-emit them. The block-model
  modifier wraps the top-level model of every block state once; a nested wrapper emits plain.
- 1.21.2 refactors followed here: `getStateForNeighborUpdate` is (state, world view, tick view, pos,
  direction, neighbour pos, neighbour state, random); `neighborUpdate` carries a `WireOrientation`;
  `replaceWithStateForNeighborUpdate` takes (direction, reacting pos, neighbour pos, neighbour state);
  wire power lives on `RedstoneController.calculateWirePowerAt`; minecart rail movement, snaps and
  position writes live on `DefaultMinecartController` (the seat is split between the entity mixin and
  the controller mixin through `SlabbedRailSeatCarrier`); world events are handled by
  `WorldEventHandler`; entity renderers work from render states (the legacy render offsets travel on
  the state through `SlabbedRenderStateDy`); `World.addParticle` with overrides takes two booleans;
  `BlockCollisionSpliterator` builds its iterator in the shape-context constructor.
- Pale moss carpet is its own block class: judged by type wherever carpets are, and the carpet outline
  mixin targets both classes.
- No Fabric client-GameTest API runs on this line: the dev-client proofs are the probe run
  configurations in `build.gradle`. `tools/mixin-target-scan.py` against the merged Yarn jar
  (common + client-only) resolves every mixin target string; run it after any mixin change.

## Commit hygiene

`git config core.hooksPath tools/hooks` once per checkout. Gates: S-1 (any tracked doc using law
vocabulary must reference LAW.md), S-4 (`LAW-PREFLIGHT: n|y` trailer on `src/main` commits), S-5
(behavior commits require a fresh entry in the local out-of-repo ledger), S-6 (discretion — see
above). S-3 (a keyword regex) was retired 2026-08-07; do not reintroduce it.
