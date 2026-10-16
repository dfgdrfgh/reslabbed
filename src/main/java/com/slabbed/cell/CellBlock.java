package com.slabbed.cell;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class CellBlock extends BlockWithEntity {
    public static final MapCodec<CellBlock> CODEC=createCodec(CellBlock::new);
    public static final IntProperty LIGHT=IntProperty.of("light",0,15);
    public CellBlock(Settings settings) { super(settings); setDefaultState(getStateManager().getDefaultState().with(LIGHT,0)); }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(LIGHT); }
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state) { return new CellBlockEntity(pos,state); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return world.getBlockEntity(pos) instanceof CellBlockEntity cell ? cell.outline() : VoxelShapes.empty();
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return world.getBlockEntity(pos) instanceof CellBlockEntity cell ? cell.collision() : VoxelShapes.empty();
    }
    @Override public ItemStack getPickStack(net.minecraft.world.WorldView world,BlockPos pos,BlockState state) {
        var p=world instanceof World w?CellWorld.pick(w,pos,null):null; return p==null?ItemStack.EMPTY:new ItemStack(p.value().getBlock());
    }
    @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved) {
        if (!next.isOf(this) && !CellWorld.mutating()) CellWorld.clearCell(world,pos);
        super.onStateReplaced(state,world,pos,next,moved);
    }
}
