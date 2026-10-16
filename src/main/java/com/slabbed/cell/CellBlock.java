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
        if (!world.isClient && !next.isOf(this) && !CellWorld.mutating() && world.getBlockEntity(pos) instanceof CellBlockEntity cell)
            CellWorld.clearOutsideCell(world,pos,cell.contents());
        super.onStateReplaced(state,world,pos,next,moved);
    }
    @Override protected float calcBlockBreakingDelta(BlockState state,PlayerEntity player,BlockView view,BlockPos pos) {
        if(!(view instanceof World world)) return 0;
        var hit=player.raycast(player.getBlockInteractionRange(),1,false);
        var part=CellWorld.pick(world,pos,hit instanceof BlockHitResult h && h.getBlockPos().equals(pos)?h:null);
        return part==null?0:part.value().calcBlockBreakingDelta(player,view,pos);
    }
    @Override protected java.util.List<ItemStack> getDroppedStacks(BlockState state,net.minecraft.loot.context.LootContextParameterSet.Builder builder) {
        if(!(builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY) instanceof CellBlockEntity cell)) return java.util.List.of();
        var drops=new java.util.ArrayList<ItemStack>();
        for(var part:cell.contents().fragments()) drops.addAll(part.value().getDroppedStacks(builder));
        return drops;
    }
    @Override protected void neighborUpdate(BlockState state,World world,BlockPos pos,Block source,BlockPos sourcePos,boolean notify) {
        if(!world.isClient && !CellWorld.mutating()) PieceSupport.revalidate(world,pos);
    }

}
