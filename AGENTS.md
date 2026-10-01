# Slabbed — agent guide

Rules for ANY coding agent working in this repo (Claude, Codex, or otherwise). AGENTS.md is an
identical copy for tools that read that name; edit both together.

## The two laws

1. **LAW 1 — placement is permanent.** Read `LAW.md` before touching anything in the placement or
   height-resolution path. `LAW.md` is supreme; no other doc may redefine it. Maintained platform branches enforce it with
   `NeighborUpdateInvarianceTest`. This historical default snapshot does not contain that test;
   a build here must never be described as passing the S-2 gate. A placement change must bring
   the applicable native invariance proof with it before it can be considered release-ready.

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
- Maintained release candidates require the closed-world artifact allowlist and hygiene gates.
  `RELEASE_ALLOWLIST.md` is absent from this historical default snapshot, so its ordinary build
  does not establish artifact purity. Use the verified platform candidate for release work.

## Commit hygiene

`git config core.hooksPath tools/hooks` once per checkout. Gates: S-1 (any tracked doc using law
vocabulary must reference LAW.md), S-4 (`LAW-PREFLIGHT: n|y` trailer on `src/main` commits), S-5
(behavior commits require a fresh entry in the local out-of-repo ledger), S-6 (discretion — see
above). S-3 (a keyword regex) was retired 2026-08-07; do not reintroduce it.

## Release labels

Use `MAJOR.MINOR.PATCH-alpha` or `MAJOR.MINOR.PATCH-beta`, without routine numbered channel counters.
Keep established platform identifiers in `+metadata`. A matching core is a behavioral parity claim:
assign it only after the applicable platform proof passes. Published versions are immutable.
