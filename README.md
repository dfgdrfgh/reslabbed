# Slabbed physical-cell rewrite

Fabric 1.21.1. Experimental implementation of the original Slabbed idea: blocks and decorations sit directly on slab surfaces.

Pieces occupy physical half-block cells. One-block pieces that cross a cell boundary store a fragment in each of the two intersected cells. Vanilla raycasts, reach and collision therefore use the visible position. Placement origins are permanent; see LAW.md.

Supported adapters: static building cubes, slabs, logs, glass, glazed terracotta, leaves, ordinary and soul torches, chains, lanterns, flower pots and carpets. Signs, hanging signs, beds, banners, skulls and supported workstations retain native placement and interactions at a fixed half-block position. Signs keep their text and editing; beds keep their native pair and sleep behavior. Crafting, smithing, cartography, loom, stonecutter and grindstone menus remain native.

Placement checks physical clearance before accepting a click. Native blocks retain their ordinary states, block entities, inventories, scheduled ticks and interactions at a remembered half-block position. Separate static pieces can occupy unused physical space in a native owner's cell without replacing its data. Normal grid-aligned building pieces remain vanilla blocks whenever they fit one cell.

The native adapters cover containers, paired doors and beds, wall attachments, rails and minecarts, switches, redstone components, scaffolding and supported plants. Native particles use the remembered position. Supplemental luminous pieces participate in normal light propagation and chunk relighting. Custom modded mechanics still require dedicated compatibility testing.

Pistons move complete static pieces atomically, preserve independent slabs and stationary native inventories, and reject a thirteenth pushed piece. The native head animates; static physical pieces currently relocate immediately. Physical slime/honey adhesion and mixed pushes into separately seated native objects are not supported yet. Fluid simulation and migration from the earlier offset format are also outside this candidate.

This is a fresh-world candidate. It does not interpret the old offset attachment format. Existing saves require an explicit migration before upgrading.

Based on the Slabbed concept by its original contributors: https://modrinth.com/mod/slabbed and https://github.com/peetsamods/slabbed. Distributed under GPL-3.0-only; see LICENSE.
