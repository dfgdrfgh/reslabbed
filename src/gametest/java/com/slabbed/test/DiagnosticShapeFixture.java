package com.slabbed.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Contextual shape fixture must never make report capture load a distant chunk. */
public final class DiagnosticShapeFixture implements ModInitializer {
    static final ThreadLocal<BlockPos> TARGET=new ThreadLocal<>();
    static int outlines,collisions;
    static final Block BLOCK=new Block(Block.Settings.create().dynamicBounds()) {
        @Override public VoxelShape getOutlineShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context) {
            var target=TARGET.get();if(target!=null) {outlines++;view.getBlockState(target);}return VoxelShapes.fullCube();
        }
        @Override public VoxelShape getCollisionShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context) {
            var target=TARGET.get();if(target!=null) {collisions++;view.getFluidState(target);view.getBlockEntity(target);}return VoxelShapes.fullCube();
        }
    };
    @Override public void onInitialize() {Registry.register(Registries.BLOCK,Identifier.of("slabbed_test","diagnostic_shape"),BLOCK);}
}
