package com.slabbed.cell;

import com.slabbed.Slabbed;
import com.slabbed.core.CellContents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

public final class CellBlockEntity extends BlockEntity {
    private CellContents<BlockState> contents = CellContents.empty();
    private VoxelShape collision = VoxelShapes.empty();
    private VoxelShape outline = VoxelShapes.empty();
    public CellBlockEntity(BlockPos pos, BlockState state) { super(Slabbed.CELL_ENTITY, pos, state); }
    public CellContents<BlockState> contents() { return contents; }
    public VoxelShape collision() { return collision; }
    public VoxelShape outline() { return outline; }
    public void setContents(CellContents<BlockState> next) {
        contents = next;
        rebuild();
        markDirty();
        if (world != null) {
            int light = contents.fragments().stream().mapToInt(p -> p.value().getLuminance()).max().orElse(0);
            var current = world.getBlockState(pos);
            if (current.isOf(Slabbed.CELL) && current.get(CellBlock.LIGHT) != light)
                world.setBlockState(pos,current.with(CellBlock.LIGHT,light),Block.NOTIFY_LISTENERS);
            if (!world.isClient) world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_ALL);
        }
    }
    private void rebuild() {
        outline = VoxelShapes.empty(); collision = VoxelShapes.empty();
        var view = world == null ? net.minecraft.world.EmptyBlockView.INSTANCE : world;
        for (var part : contents.fragments()) {
            var state = part.value();
            outline = VoxelShapes.union(outline, clip(state.getOutlineShape(view,pos).offset(0,part.offset()*.5,0)));
            collision = VoxelShapes.union(collision, clip(state.getCollisionShape(view,pos).offset(0,part.offset()*.5,0)));
        }
    }
    private static VoxelShape clip(VoxelShape shape) {
        return VoxelShapes.combineAndSimplify(shape,VoxelShapes.fullCube(),BooleanBiFunction.AND);
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt,lookup);
        var parts = new NbtList();
        for (var p : contents.fragments()) {
            var tag = new NbtCompound(); tag.putInt("offset",p.offset()); tag.putInt("length",p.length());
            tag.put("state",NbtHelper.fromBlockState(p.value())); parts.add(tag);
        }
        nbt.putInt("format",1); nbt.put("parts",parts);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt,lookup);
        var next=CellContents.<BlockState>empty();
        for (var element : nbt.getList("parts",NbtElement.COMPOUND_TYPE)) {
            var tag=(NbtCompound)element;
            var state=NbtHelper.toBlockState(lookup.getWrapperOrThrow(RegistryKeys.BLOCK),tag.getCompound("state"));
            if (state.isAir() || state.isOf(Slabbed.CELL)) continue;
            try { next=next.add(new CellContents.Fragment<>(tag.getInt("offset"),tag.getInt("length"),state)); }
            catch (IllegalArgumentException malformed) { throw new IllegalStateException("Invalid Slabbed physical cell at "+pos,malformed); }
        }
        contents=next; rebuild();
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) { return createNbt(lookup); }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
