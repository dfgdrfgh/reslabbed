package com.slabbed.nativeblock;

import com.slabbed.cell.CellWorld;
import com.slabbed.cell.PieceRules;
import com.slabbed.mixin.NativeBlockItemAccess;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** A scoped native placement keeps BlockItem's complete vanilla lifecycle and freezes its pose once. */
public final class NativePlacement {
    private static final ThreadLocal<Plan> ACTIVE=new ThreadLocal<>();
    private static final class Plan {
        final World world;
        final Map<BlockPos,NativeSeats.Seat> seats=new LinkedHashMap<>();
        final Map<BlockPos,BlockState> states=new LinkedHashMap<>();
        Plan(World world) {this.world=world;}
    }
    private NativePlacement() {}
    public static NativeSeats.Seat pending(BlockView world,BlockPos pos) {
        var plan=ACTIVE.get();return plan!=null && plan.world==world?plan.seats.get(pos):null;
    }
    public static boolean active(World world,BlockPos pos) {return pending(world,pos)!=null;}
    public static ActionResult place(BlockItem item,ItemPlacementContext original,Function<ItemPlacementContext,ActionResult> vanilla) {
        if(!NativeRules.accepts(item.getBlock().getDefaultState()) || original.getPlayer()==null)return vanilla.apply(original);
        var world=original.getWorld();var source=source(original);
        BlockPos owner=original.getBlockPos();int half=0;
        if(source!=null) {
            int origin=source.origin();var side=original.getSide();
            int x=source.pos().getX(),z=source.pos().getZ();
            if(side==Direction.UP)origin+=source.length();
            else if(side==Direction.DOWN)origin-=2;
            else {x+=side.getOffsetX();z+=side.getOffsetZ();}
            int y=-Math.floorDiv(-origin,2);owner=new BlockPos(x,y,z);half=origin-y*2;
        }
        var plan=new Plan(world);var previous=ACTIVE.get();ACTIVE.set(plan);
        try {
            var context=new SeatContext(original,owner);
            if(!prepare(item,context,plan,owner,half)) {
                if(source==null || original.getSide()!=Direction.DOWN || half!=-1)return ActionResult.FAIL;
                owner=owner.down();half=1;context=new SeatContext(original,owner);
                if(!prepare(item,context,plan,owner,half))return ActionResult.FAIL;
            }
            var result=vanilla.apply(context);
            if(result.isAccepted()) {
                for(var entry:plan.seats.entrySet()) {
                    var actual=world.getBlockState(entry.getKey());
                    if(actual.getBlock()==plan.states.get(entry.getKey()).getBlock())
                        NativeSeats.put(world,entry.getKey(),seat(entry.getValue().halfOffset(),actual));
                }
            }
            return result;
        } finally {if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
    private static boolean prepare(BlockItem item,SeatContext context,Plan plan,BlockPos owner,int half) {
        plan.states.clear();plan.seats.clear();
        plan.seats.put(owner,new NativeSeats.Seat(half,Registries.BLOCK.getId(item.getBlock()).toString()));
        var selected=((NativeBlockItemAccess)item).slabbed$getPlacementState(context);
        if(selected==null)return false;
        var component=context.getStack().get(net.minecraft.component.DataComponentTypes.BLOCK_STATE);
        if(component!=null)selected=component.applyToState(selected);
        if(half!=0 && !selected.getFluidState().isEmpty())return false;
        plan.states.put(owner,selected);plan.seats.put(owner,seat(half,selected));
        if(selected.getBlock() instanceof BedBlock) {
            var other=owner.offset(selected.get(Properties.HORIZONTAL_FACING));
            var head=selected.with(BedBlock.PART,BedPart.HEAD);
            plan.states.put(other,head);plan.seats.put(other,seat(half,head));
        }
        return preflight(context,plan);
    }
    private static NativeSeats.Seat seat(int half,BlockState state) {
        return new NativeSeats.Seat(half,Registries.BLOCK.getId(state.getBlock()).toString());
    }
    private record Source(BlockPos pos,int origin,int length) {}
    private static Source source(ItemPlacementContext context) {
        var world=context.getWorld();var hit=context.getHitPos();var side=context.getSide();
        var physical=BlockPos.ofFloored(hit.subtract(Vec3d.of(side.getVector()).multiply(1e-5)));
        var part=CellWorld.pick(world,physical,new BlockHitResult(hit,side,physical,false));
        if(part!=null && PieceRules.supports(part.value()))return new Source(physical,physical.getY()*2+part.offset(),part.length());
        // A projected native full cube can be hit in either physical cell it occupies.
        for(int n=-1;n<=1;n++) {
            var owner=physical.up(n);var stored=NativeSeats.seat(world,owner);
            if(stored==null || !PieceRules.supports(world.getBlockState(owner)))continue;
            var local=hit.subtract(Vec3d.of(side.getVector()).multiply(1e-5)).subtract(Vec3d.of(owner));
            var shape=NativeShapes.occupied(world,owner,world.getBlockState(owner),stored.halfOffset());
            if(shape.getBoundingBoxes().stream().anyMatch(box->box.expand(1e-6).contains(local)))
                return new Source(owner,owner.getY()*2+stored.halfOffset(),2);
        }
        return null;
    }
    private static boolean preflight(ItemPlacementContext context,Plan plan) {
        var world=plan.world;var player=context.getPlayer();
        for(var entry:plan.states.entrySet()) {
            var owner=entry.getKey();var state=entry.getValue();var old=world.getBlockState(owner);
            if(!world.isInBuildLimit(owner) || !world.getWorldBorder().contains(owner) || !world.isChunkLoaded(owner)
                    || !player.getAbilities().allowModifyWorld || !world.canPlayerModifyAt(player,owner)
                    || !old.canReplace(context) || old.hasBlockEntity())return false;
            var raw=VoxelShapes.union(NativeShapes.outline(world,owner,state),NativeShapes.collision(world,owner,state));
            if(!NativeShapes.bounded(raw))return false;
            var seat=plan.seats.get(owner);var physical=raw.offset(owner.getX(),owner.getY()+seat.offset(),owner.getZ());
            var collision=NativeShapes.collision(world,owner,state).offset(owner.getX(),owner.getY()+seat.offset(),owner.getZ());
            if(!world.doesNotIntersectEntities(null,collision))return false;
            if(physical.isEmpty())continue;
            var bounds=physical.getBoundingBox();
            int low=(int)Math.floor(bounds.minY+1e-7),high=(int)Math.floor(bounds.maxY-1e-7);
            for(int y=low;y<=high;y++) {
                var cell=new BlockPos(owner.getX(),y,owner.getZ());
                if(!world.isInBuildLimit(cell) || !world.getWorldBorder().contains(cell) || !world.isChunkLoaded(cell)
                        || !world.canPlayerModifyAt(player,cell))return false;
                if(!plan.states.containsKey(cell)) {
                    var existing=world.getBlockState(cell);
                    if(!world.getFluidState(cell).isEmpty())return false;
                    if(!existing.isAir()) {
                        var existingSeat=NativeSeats.seat(world,cell);
                        var occupied=existingSeat==null
                                ?VoxelShapes.union(NativeShapes.outline(world,cell,existing),NativeShapes.collision(world,cell,existing))
                                :NativeShapes.occupied(world,cell,existing,existingSeat.halfOffset());
                        occupied=occupied.offset(cell.getX(),cell.getY(),cell.getZ());
                        if(VoxelShapes.matchesAnywhere(physical,occupied,BooleanBiFunction.AND))return false;
                    }
                }
                // Native owners within one vertical cell may project into this physical cell.
                for(int n=-1;n<=1;n++) {
                    var other=cell.up(n);var otherSeat=NativeSeats.seat(world,other);
                    if(otherSeat!=null && !plan.states.containsKey(other)) {
                        var occupied=NativeShapes.occupied(world,other,world.getBlockState(other),otherSeat.halfOffset()).offset(other.getX(),other.getY(),other.getZ());
                        if(VoxelShapes.matchesAnywhere(physical,occupied,BooleanBiFunction.AND))return false;
                    }
                }
            }
        }
        return true;
    }
    private static final class SeatContext extends ItemPlacementContext {
        private BlockPos owner;
        SeatContext(ItemPlacementContext original,BlockPos owner) {super(original);this.owner=owner;canReplaceExisting=false;}
        @Override public BlockPos getBlockPos() {return owner==null?super.getBlockPos():owner;}
        @Override public boolean canPlace() {return getWorld().getBlockState(getBlockPos()).canReplace(this);}
    }
}
