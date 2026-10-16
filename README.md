# Slabbed physical-cell rewrite

Fabric 1.21.1. Experimental implementation of the original Slabbed idea: blocks and decorations sit directly on slab surfaces.

Pieces occupy physical half-block cells. One-block pieces that cross a cell boundary store a fragment in each of the two intersected cells. Vanilla raycasts, reach and collision therefore use the visible position. Placement origins are permanent; see LAW.md.

Supported adapters: static building cubes, slabs, logs, glass, glazed terracotta, leaves, ordinary and soul torches, chains, lanterns, flower pots and carpets. Blocks with inventories, other block entities, falling behavior, fluids, pistons, redstone machinery and other unadapted mechanics keep vanilla placement. These mechanics are not emulated inside a physical cell. Normal grid-aligned blocks remain vanilla blocks whenever they fit one cell.

This is a fresh-world candidate. It does not interpret the old offset attachment format. Existing saves require an explicit migration before upgrading.

Based on the Slabbed concept by its original contributors: https://modrinth.com/mod/slabbed and https://github.com/peetsamods/slabbed. Distributed under GPL-3.0-only; see LICENSE.
