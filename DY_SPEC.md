# Physical placement contract

See LAW.md. This document specifies storage and geometry; it does not redefine permanent placement.

## Coordinates

A piece has an integer origin in half-block units and a length of one or two half blocks. Division uses floor division, including below Y=0. Each intersected Minecraft cell stores one fragment. A fragment's origin relative to its cell is -1, 0 or 1 half steps; its occupancy mask intersects only that cell's lower and upper halves. No operation scans an accumulated stack depth.

A bottom slab and a full block above it share one cell: the slab occupies its lower half, and the full block starts at its upper half. The full block's remaining half belongs to the cell above. Removing either fragment removes that full block once while preserving other pieces.

## Persistent format

The `slabbed:cell` block entity stores `format=1` and a `parts` compound list. Every entry contains integer `offset`, integer `length`, and the underlying vanilla block `state`. Fragments in the same cell must have disjoint occupancy masks. A crossing piece duplicates its state in both fragments so each cell's collision and render data are locally available.

NBT loading validates local occupancy. The old placement-offset attachment is not read. This format requires fresh worlds until an explicit migration exists.

## Placement and changes

Placement validates every intersected cell before committing any change. The origin is permanent. A piece fitting one native cell returns to its native block state; cells sharing pieces retain the carrier block entity. Canonicalization preserves physical position exactly.

Neighbors may remove a decoration under its vanilla support rules. They cannot move a piece. Transformations such as planting a flower pot replace the piece's state across both fragments while retaining its origin. External replacement snapshots the outgoing cell's block entity and clears only companion fragments outside the replaced cell.

## Collision, targeting and rendering

Collision and raycast shapes are clipped to physical cells and cached in their block entity. Vanilla collision, ray traversal, reach and chunk indexing therefore operate at the visible location. Selection outlines draw the entire selected piece. Rendered models use bounded local offsets and world block colors.

Vanilla placement packets carry normal physical hit positions. Vanilla breaking packets identify only a cell, so a small selection payload identifies the aimed piece. The server validates finite coordinates, loaded cells, ordinary reach and an unobstructed physical ray before accepting that selection. It expires and is cleared on disconnect. Tool eligibility, mining speed, loot and explosion resistance derive from the underlying material.

## Adapters

Static building cubes, slabs, logs, glass, glazed terracotta, persistent leaves, ordinary and soul torches, chains, lanterns, flower pots and carpets have explicit handling. Unsupported stateful mechanics remain on vanilla placement rather than being stored with incomplete behavior. Carrier cells are block entities and are consequently immovable by vanilla pistons.
