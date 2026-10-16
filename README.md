# Slabbed physical-cell rewrite

Fabric 1.21.1. Experimental implementation of the original Slabbed idea: blocks and decorations sit directly on slab surfaces.

Pieces occupy physical half-block cells. One-block pieces that cross a cell boundary store a fragment in each of the two intersected cells. Vanilla raycasts, reach and collision therefore use the visible position. Placement origins are permanent; see LAW.md.

Supported adapters: static building cubes, slabs, logs, glass, glazed terracotta, leaves, ordinary and soul torches, chains, lanterns, flower pots and carpets. Signs, hanging signs, beds, banners, skulls and supported workstations retain native placement and interactions at a fixed half-block position. Signs keep their text and editing; beds keep their native pair and sleep behavior. Crafting, smithing, cartography, loom, stonecutter and grindstone menus remain native.

Placement checks physical clearance before accepting a click. Native functional objects reserve their owner cells and retain their original block entities and data. Inventories, falling behavior, fluids, pistons, redstone machinery and other unadapted mechanics keep vanilla placement. Normal grid-aligned building pieces remain vanilla blocks whenever they fit one cell.

This is a fresh-world candidate. It does not interpret the old offset attachment format. Existing saves require an explicit migration before upgrading.

Based on the Slabbed concept by its original contributors: https://modrinth.com/mod/slabbed and https://github.com/peetsamods/slabbed. Distributed under GPL-3.0-only; see LICENSE.
