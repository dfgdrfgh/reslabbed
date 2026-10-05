# LAW.md — Slabbed placement law

## Placement is permanent

Where a player places a block is where it stays. Compute its height once from the placement aim,
then retain that value. Later neighbor or support edits must never recompute the height of a block
that remains present. A genuine vanilla mechanic may remove the block; it must not silently reseat it.

Wall-mounted blocks take their seat from the mounted face at placement. Item frames and paintings
carry their own remembered, saved and synchronized seat. Rebuilding the support changes the support,
not the decoration.

Legacy reads for cells without a stored placement fact are compatibility behavior for old worlds;
they do not redefine the stored-height contract.

## Lowering follows geometry

Eligibility to lower comes from the actual placement geometry, not a class list, namespace or
marker-set membership. Protect real hazards by their behavior.

## Proof matches the target

A placement repair needs a native test that places through the real item-use path, then applies a
reachable neighbor or support mutation and verifies the recorded height is unchanged. The test must
state the mutation that exercises its boundary. Compilation alone does not prove this contract.

Maintained platform candidates require the blocking invariance, complete-count and artifact-purity
gates. The historical default snapshot lacks the invariance test and artifact allowlist; its build
cannot certify those missing gates. See AGENTS.md for the current checkout instructions.
