package com.slabbed.cell;

import com.slabbed.Slabbed;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;

public final class CellPlacement {
    private CellPlacement() {}
    /** Null delegates unchanged to vanilla; FAIL reserves nothing and consumes nothing. */
    public static ActionResult place(BlockItem item, ItemPlacementContext context) {
        var world=context.getWorld(); var player=context.getPlayer();
        if(player==null) return null;
        var hit=context.getHitPos(); var face=context.getSide();
        var clicked=BlockPos.ofFloored(hit.subtract(Vec3d.of(face.getVector()).multiply(1e-5)));
        var clickedState=world.getBlockState(clicked);
        com.slabbed.core.CellContents.Fragment<BlockState> fragment=null;
        if(clickedState.isOf(Slabbed.CELL) || clickedState.getBlock() instanceof SlabBlock) {
            if(clickedState.getBlock() instanceof SlabBlock && clickedState.get(SlabBlock.WATERLOGGED)) return null;
            fragment=CellWorld.pick(world,clicked,new net.minecraft.util.hit.BlockHitResult(hit,face,clicked,false));
        }
        if(fragment==null) {
            // A bounded native seat can project into either adjacent physical cell.
            for(int n=-1;n<=1;n++) {
                var owner=clicked.up(n);var seat=com.slabbed.nativeblock.NativeSeats.seat(world,owner);
                var nativeState=world.getBlockState(owner);
                if(seat==null || !PieceRules.supports(nativeState))continue;
                var local=hit.subtract(Vec3d.of(face.getVector()).multiply(1e-5)).subtract(Vec3d.of(owner));
                var shape=com.slabbed.nativeblock.NativeShapes.occupied(world,owner,nativeState,seat.halfOffset());
                if(shape.getBoundingBoxes().stream().noneMatch(box->box.expand(1e-6).contains(local)))continue;
                clicked=owner;clickedState=nativeState;
                fragment=new com.slabbed.core.CellContents.Fragment<>(seat.halfOffset(),2,nativeState);break;
            }
        }
        if(fragment==null) return null;
        var support=fragment.value();
        if(!PieceRules.supports(support)) return null;
        var block=item.getBlock();
        var state=block.getPlacementState(context);
        if(state==null) state=block.getDefaultState();
        if(block instanceof SlabBlock) state=state.with(SlabBlock.TYPE,SlabType.BOTTOM).with(SlabBlock.WATERLOGGED,false);
        if(state.contains(Properties.PERSISTENT)) state=state.with(Properties.PERSISTENT,true);
        if(state.contains(Properties.WATERLOGGED)) state=state.with(Properties.WATERLOGGED,false);
        if(block instanceof LanternBlock) state=state.with(Properties.HANGING,face==Direction.DOWN);
        if(block instanceof ChainBlock) state=state.with(Properties.AXIS,face.getAxis());
        if(face.getAxis().isHorizontal()) {
            if(block==Blocks.TORCH) state=Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING,face);
            if(block==Blocks.SOUL_TORCH) state=Blocks.SOUL_WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING,face);
        }
        if(!PieceRules.accepts(state)) return null;
        int length=CellWorld.length(state);
        int origin=clicked.getY()*2+fragment.offset();
        int x=clicked.getX(),z=clicked.getZ();
        if(face==Direction.UP) origin+=fragment.length();
        else if(face==Direction.DOWN) origin-=length;
        else { x+=face.getOffsetX(); z+=face.getOffsetZ(); }
        // Matching vanilla slabs retain vanilla's single-cell double-slab behavior.
        if(clickedState.getBlock() instanceof SlabBlock && item.getBlock()==clickedState.getBlock()
                && face.getAxis().isVertical() && clickedState.get(SlabBlock.TYPE)!=SlabType.DOUBLE) return null;
        var base=new BlockPos(x,com.slabbed.core.HalfGrid.at(origin).cellY(),z);
        if(!player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player,base)
                || !world.canPlayerModifyAt(player,new BlockPos(x,com.slabbed.core.HalfGrid.at(origin+length-1).cellY(),z))) return ActionResult.FAIL;
        var shape=state.getCollisionShape(world,base).offset(x,origin*.5,z);
        if(!world.doesNotIntersectEntities(null,shape)) return ActionResult.FAIL;
        if(!PieceSupport.supported(world,base,new com.slabbed.core.CellContents.Fragment<>(origin-base.getY()*2,length,state))) return ActionResult.FAIL;
        if(!CellWorld.canPlace(world,x,z,origin,state)) return ActionResult.FAIL;
        if(world.isClient) return ActionResult.SUCCESS;
        if(!CellWorld.place(world,x,z,origin,state)) return ActionResult.FAIL;
        var sounds=state.getSoundGroup();
        world.playSound(null,base,sounds.getPlaceSound(),SoundCategory.BLOCKS,(sounds.getVolume()+1)*.5f,sounds.getPitch()*.8f);
        if(!player.isCreative()) context.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
