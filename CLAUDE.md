# Slabbed — agent guide

Rules for ANY coding agent working in this repo (Claude, Codex, or otherwise). AGENTS.md is an
identical copy for tools that read that name; edit both together.

## The laws

1. **LAW 1 — placement is permanent.** Read `LAW.md` before touching anything in the placement or
   height-resolution path. `LAW.md` is supreme; no other doc may redefine it. Its enforcement is
   `NeighborUpdateInvarianceTest` (the S-2 gate), which is **blocking by default** — a violation
   fails the build. `-Dslabbed.lawGate=false` downgrades to a printed inventory and is only for
   fixing a deliberate new RED forward, never for landing one. A green S-2 row proves nothing
   unless a mutation provably reaches that subject — every new subject names its mutation in a
   comment.

2. **LAW 2 — everything can lower (maintainer ruling, 2026-08-06).** Eligibility follows
   **geometry** — whether the support actually presents a lowered top face — never a block-class
   allow-list, a namespace string, or anchor-set membership. "This block type is excluded" is not a
   reason; it is the bug. Hazards are excluded **by behaviour, not by classname**.

3. **The discretion law (maintainer ruling, 2026-08-07) — this repo is public and stays
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

- `./gradlew build runGameTestServer` on a CLEAN run dir (`rm -rf build/run/gameTest` first — a stale
  dir silently drops tests and still prints green). This line needs JDK 25; `./gradlew25` pins one
  when the default JDK is older.
- The reported count MUST match `python3 tools/expected-gametest-count.py`. A green with the
  wrong count is a false green.
- This line ships ONE jar per Minecraft version from one source: 26.2 with the pins in
  `gradle.properties`, 26.3 with `minecraft_version`/`neo_version` switched. A green on one is not a
  green on the other: run the suite with each version's pins and `tools/mixin-target-scan.py` against
  each version's game jar (merge the NeoForge universal jar's classes into the scanned jar — NeoForge
  patches add members such as `BlockState.isScaffolding`) before any parity or release claim. The
  scan may skip only the mixins a config plugin withholds on that version (26.2:
  `CushionRestsOnDrawnTopMixin`, `RedstoneWireBlockMixin`, `LevelRendererAllChangedMixin`; 26.3:
  `LegacyRedStoneWireBlockMixin`, `LevelRendererInvalidateCompiledGeometryMixin`).
- Keep the literal `@GameTest` token out of comments in registered test classes as hygiene, but the
  count script strips comments before counting and no longer depends on it. (It once did: five
  `{@code @GameTest}` javadoc mentions inflated the expected total to 575 against a true 570, so the
  gate reported a permanent false mismatch — fixed 2026-08-20.)
- The count covers the registered classes in `SlabbedGameTestClasses.CLASSES` plus the one vanilla
  harness test NeoForge runs alongside. The dev-client world proof (`runClient` with
  `-Dslabbed.neoforge.clientWorldProof=true`) is **not** counted by any gate.
- The release jar is gated by a closed-world allowlist (`RELEASE_ALLOWLIST.md`) checked during
  `build`; debug/dev tooling ships in every jar default-off, but the file-writing audit/recorder
  packages never ship.

## Commit hygiene

`git config core.hooksPath tools/hooks` once per checkout. Gates: S-1 (any tracked doc using law
vocabulary must reference LAW.md), S-4 (`LAW-PREFLIGHT: n|y` trailer on `src/main` commits), S-5
(behavior commits require a fresh entry in the local out-of-repo ledger), S-6 (discretion — see
above). S-3 (a keyword regex) was retired 2026-08-07; do not reintroduce it.

## Line notes (NeoForge 26.2)

- Loader layer: `com.slabbed.loader.Loader` is the only place that names the loader; `Attachments`
  gives attachment reads an explicit "absent" (NeoForge's `getData` would create the default).
- Attachments register on a `DeferredRegister` with a sync handler that notifies the client
  observer after each decode (`SlabAnchorAttachment.setClientSyncObserver`), which is what the
  Fabric lines' per-chunk attachment-set listeners did.
- Payloads register in `SlabbedNetwork` as optional; sends go through `SlabbedNetwork` (server)
  and `SlabbedClientNetwork` (client). Vanilla clients connect without them.
- Model layer: `OffsetBlockStateModel` extends NeoForge's delegate block-state model and overrides
  the level-aware `collectParts`; parts are copied with translated quads (`QuadPart`), seam faces
  move to the unculled bucket, chain bridges are standalone models. No Fabric renderer API.
- Game tests: `com.slabbed.gametest.GameTest` is the suite's annotation (same attributes and
  defaults as Fabric's); `SlabbedGameTests` registers one instance per method on
  `RegisterGameTestsEvent`, with one environment per distinct name. The registered class list is
  `SlabbedGameTestClasses` (the count script reads it). Test blocks are lazy holders registered
  in the register event: a block constructed outside it fails ("registry is already frozen").
- Java modules: NeoForge forbids a package split across mods. Test-only classes live under
  `com.slabbed.test.*`, diagnostics under `com.slabbed.diagnostics.*`; the main members they need
  are public.
- The structure block of a test occupies relative cell (0,0,0) on this version too (see the
  1.20.3 notes on the Fabric lines); test structures ship as `.nbt` under `structure/`.
- No client-GameTest API: the 14 client gametests of the Fabric line are not run here.
