package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import com.slabbed.nativeblock.NativeShapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** Every original placement family must retain its physical pose and gameplay identity. */
public final class OriginalFamiliesTest {
    private record Case(String id,Item item,Block block,boolean nativeRequired,Block soil) {
        Case(String id,Item item,Block block) {this(id,item,block,true,null);}
        Case(String id,Item item,Block block,boolean nativeRequired) {this(id,item,block,nativeRequired,null);}
        Case(String id,Item item,Block block,Block soil) {this(id,item,block,true,soil);}
    }
    private static final Case[] FLOOR={
            new Case("full_block",Items.STONE,Blocks.STONE,false),
            new Case("bottom_slab",Items.STONE_SLAB,Blocks.STONE_SLAB,false),
            new Case("stairs",Items.STONE_STAIRS,Blocks.STONE_STAIRS),
            new Case("anvil",Items.ANVIL,Blocks.ANVIL),
            new Case("chest",Items.CHEST,Blocks.CHEST),
            new Case("hopper",Items.HOPPER,Blocks.HOPPER),
            new Case("trapdoor",Items.OAK_TRAPDOOR,Blocks.OAK_TRAPDOOR),
            new Case("candle",Items.CANDLE,Blocks.CANDLE),
            new Case("campfire",Items.CAMPFIRE,Blocks.CAMPFIRE),
            new Case("soul_campfire",Items.SOUL_CAMPFIRE,Blocks.SOUL_CAMPFIRE),
            new Case("carpet",Items.WHITE_CARPET,Blocks.WHITE_CARPET,false),
            new Case("snow",Items.SNOW,Blocks.SNOW),
            new Case("powder_snow_bucket",Items.POWDER_SNOW_BUCKET,Blocks.POWDER_SNOW),
            new Case("torch",Items.TORCH,Blocks.TORCH,false),
            new Case("soul_torch",Items.SOUL_TORCH,Blocks.SOUL_TORCH,false),
            new Case("redstone_torch",Items.REDSTONE_TORCH,Blocks.REDSTONE_TORCH),
            new Case("rail",Items.RAIL,Blocks.RAIL),
            new Case("wire",Items.REDSTONE,Blocks.REDSTONE_WIRE),
            new Case("repeater",Items.REPEATER,Blocks.REPEATER),
            new Case("comparator",Items.COMPARATOR,Blocks.COMPARATOR),
            new Case("sign",Items.OAK_SIGN,Blocks.OAK_SIGN),
            new Case("banner",Items.WHITE_BANNER,Blocks.WHITE_BANNER),
            new Case("skull",Items.SKELETON_SKULL,Blocks.SKELETON_SKULL),
            new Case("button",Items.ACACIA_BUTTON,Blocks.ACACIA_BUTTON),
            new Case("lever",Items.LEVER,Blocks.LEVER),
            new Case("bell",Items.BELL,Blocks.BELL),
            new Case("fence",Items.OAK_FENCE,Blocks.OAK_FENCE),
            new Case("wall",Items.COBBLESTONE_WALL,Blocks.COBBLESTONE_WALL),
            new Case("pane",Items.GLASS_PANE,Blocks.GLASS_PANE),
            new Case("gate",Items.OAK_FENCE_GATE,Blocks.OAK_FENCE_GATE),
            new Case("flower_pot",Items.FLOWER_POT,Blocks.FLOWER_POT,false),
            new Case("decorated_pot",Items.DECORATED_POT,Blocks.DECORATED_POT),
            new Case("cauldron",Items.CAULDRON,Blocks.CAULDRON),
            new Case("brewing_stand",Items.BREWING_STAND,Blocks.BREWING_STAND),
            new Case("cake",Items.CAKE,Blocks.CAKE),
            new Case("sea_pickle",Items.SEA_PICKLE,Blocks.SEA_PICKLE,Blocks.PRISMARINE),
            new Case("turtle_egg",Items.TURTLE_EGG,Blocks.TURTLE_EGG,Blocks.SAND),
            new Case("lightning_rod",Items.LIGHTNING_ROD,Blocks.LIGHTNING_ROD),
            new Case("end_rod",Items.END_ROD,Blocks.END_ROD),
            new Case("enchanting_table",Items.ENCHANTING_TABLE,Blocks.ENCHANTING_TABLE),
            new Case("piston",Items.PISTON,Blocks.PISTON),
            new Case("sticky_piston",Items.STICKY_PISTON,Blocks.STICKY_PISTON),
            new Case("observer",Items.OBSERVER,Blocks.OBSERVER),
            new Case("flower",Items.DANDELION,Blocks.DANDELION,Blocks.DIRT),
            new Case("sapling",Items.OAK_SAPLING,Blocks.OAK_SAPLING,Blocks.DIRT),
            new Case("cactus",Items.CACTUS,Blocks.CACTUS,Blocks.SAND),
            new Case("crop",Items.WHEAT_SEEDS,Blocks.WHEAT,Blocks.FARMLAND)
    };
    private static final Case[] WALL={
            new Case("ladder",Items.LADDER,Blocks.LADDER),
            new Case("torch",Items.TORCH,Blocks.WALL_TORCH,false),
            new Case("sign",Items.OAK_SIGN,Blocks.OAK_WALL_SIGN),
            new Case("banner",Items.WHITE_BANNER,Blocks.WHITE_WALL_BANNER),
            new Case("skull",Items.SKELETON_SKULL,Blocks.SKELETON_WALL_SKULL),
            new Case("button",Items.ACACIA_BUTTON,Blocks.ACACIA_BUTTON),
            new Case("lever",Items.LEVER,Blocks.LEVER),
            new Case("bell",Items.BELL,Blocks.BELL),
            new Case("trapdoor",Items.OAK_TRAPDOOR,Blocks.OAK_TRAPDOOR)
    };
    private static final Case[] CEILING={
            new Case("slab",Items.STONE_SLAB,Blocks.STONE_SLAB,false),
            new Case("chain",Items.CHAIN,Blocks.CHAIN,false),
            new Case("lantern",Items.LANTERN,Blocks.LANTERN,false),
            new Case("sign",Items.OAK_HANGING_SIGN,Blocks.OAK_HANGING_SIGN),
            new Case("dripstone",Items.POINTED_DRIPSTONE,Blocks.POINTED_DRIPSTONE),
            new Case("button",Items.ACACIA_BUTTON,Blocks.ACACIA_BUTTON),
            new Case("lever",Items.LEVER,Blocks.LEVER),
            new Case("bell",Items.BELL,Blocks.BELL),
            new Case("trapdoor",Items.OAK_TRAPDOOR,Blocks.OAK_TRAPDOOR)
    };
    private record Pose(BlockPos owner,BlockState state,int origin,VoxelShape shape,boolean nativePose) {}

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void originalFortySevenFloorFamiliesKeepPhysicalPlacement(TestContext t) {
        var failures=new ArrayList<String>();var base=t.getAbsolutePos(new BlockPos(3,8,3));
        for(var entry:FLOOR)run(failures,entry.id(),()-> {
            clear(t.getWorld(),base);t.getWorld().setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
            int origin=base.getY()*2+1;
            if(entry.soil()!=null) {
                if(entry.soil()==Blocks.FARMLAND || entry.soil()==Blocks.SAND) {
                    // Stateful soil keeps its ordinary native lifecycle in this support fixture.
                    var support=base.up();t.getWorld().setBlockState(support,entry.soil().getDefaultState());
                    NativeSeats.put(t.getWorld(),support,new NativeSeats.Seat(-1,Registries.BLOCK.getId(entry.soil()).toString()));
                } else require(CellWorld.place(t.getWorld(),base.getX(),base.getZ(),origin,entry.soil().getDefaultState()),"shifted soil fixture rejected");
                origin+=2;
                t.getWorld().setBlockState(base.add(2,2,0),Blocks.GLOWSTONE.getDefaultState(),Block.NOTIFY_ALL);
            }
            var hit=ray(t,base,new Vec3d(.5,4,.5),new Vec3d(.5,0,.5));
            require(hit.getType()==HitResult.Type.BLOCK,"support ray missed");
            require(Math.abs(hit.getPos().y-origin*.5)<1e-6 || entry.soil()==Blocks.FARMLAND,"support ray hit wrong height: "+hit.getPos());
            placeAndVerify(t,entry,base,hit,origin,base.getX(),base.getZ());
        });
        finish(t,failures,"47 floor families");
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void originalNineWallFamiliesUsePhysicalMount(TestContext t) {
        var failures=new ArrayList<String>();var base=t.getAbsolutePos(new BlockPos(3,8,3));
        for(var entry:WALL)run(failures,entry.id(),()-> {
            clear(t.getWorld(),base);t.getWorld().setBlockState(base,Blocks.OAK_SLAB.getDefaultState());
            require(CellWorld.place(t.getWorld(),base.getX(),base.getZ(),base.getY()*2+1,Blocks.STONE.getDefaultState()),"shifted wall fixture rejected");
            var hit=ray(t,base,new Vec3d(3,1,.5),new Vec3d(.5,1,.5));
            require(hit.getType()==HitResult.Type.BLOCK && hit.getSide()==Direction.EAST,"physical wall face ray missed");
            placeAndVerify(t,entry,base,hit,base.getY()*2+1,base.getX()+1,base.getZ());
        });
        finish(t,failures,"9 wall families");
    }

    @GameTest(templateName="fabric-gametest-api-v1:empty")
    public void originalNineCeilingFamiliesUsePhysicalUnderside(TestContext t) {
        var failures=new ArrayList<String>();var base=t.getAbsolutePos(new BlockPos(3,8,3));
        for(var entry:CEILING)run(failures,entry.id(),()-> {
            clear(t.getWorld(),base);
            t.getWorld().setBlockState(base,Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.TOP));
            var hit=ray(t,base,new Vec3d(.5,-3,.5),new Vec3d(.5,1,.5));
            require(hit.getType()==HitResult.Type.BLOCK && hit.getSide()==Direction.DOWN,"physical ceiling face ray missed");
            int length=entry.block() instanceof SlabBlock?1:2;
            placeAndVerify(t,entry,base,hit,base.getY()*2+1-length,base.getX(),base.getZ());
        });
        finish(t,failures,"9 ceiling families");
    }

    private static void placeAndVerify(TestContext t,Case entry,BlockPos base,BlockHitResult hit,int origin,int x,int z) {
        var player=t.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(base.getX()+.5,base.getY()-1,base.getZ()-3);player.setYaw(0);player.setPitch(0);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(entry.item(),entry.item()==Items.POWDER_SNOW_BUCKET?1:2));
        var result=player.getMainHandStack().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
        require(result.isAccepted(),"real item use rejected: "+result);
        if(entry.item()==Items.POWDER_SNOW_BUCKET)require(player.getMainHandStack().isOf(Items.BUCKET),"powder snow placement did not return bucket");
        else require(player.getMainHandStack().getCount()==1,"item placement did not consume exactly once");
        var pose=pose(t.getWorld(),entry,x,z,origin);
        require(pose!=null,"item has no matching physical piece/native seat at origin "+origin);
        require(!entry.nativeRequired() || pose.nativePose(),"functional family lost native identity to carrier storage");
        if(pose.nativePose()) {
            require(pose.state().canPlaceAt(t.getWorld(),pose.owner()),"native state fails physical support rule");
            if(pose.state().hasBlockEntity()) {
                var entity=t.getWorld().getBlockEntity(pose.owner());
                require(entity!=null && entity.getCachedState().isOf(entry.block()),"native block entity identity missing");
            }
        }
        require(!pose.shape().isEmpty(),"placed physical outline empty");
        var box=pose.shape().getBoundingBox();
        require(box.minY>=origin*.5-1e-6 && box.maxY<=origin*.5+1.5+1e-6,"outline escaped physical placement bounds: "+box);
        var center=box.getCenter();
        var visible=t.getWorld().raycast(new RaycastContext(center.add(0,2,0),center,
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        // Ceiling support can occlude a top-down ray; approach that attachment from below instead.
        if(hit.getSide()==Direction.DOWN)visible=t.getWorld().raycast(new RaycastContext(center.add(0,-2,0),center,
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        require(visible.getType()==HitResult.Type.BLOCK,"physical placed-object ray missed");
        require(pose.nativePose()?visible.getBlockPos().equals(pose.owner()):containsPiece(t.getWorld(),visible.getBlockPos(),entry.block(),origin),"physical ray selected another object: "+visible);
        var mutation=base.add(0,0,2);t.getWorld().setBlockState(mutation,Blocks.GLASS.getDefaultState(),Block.NOTIFY_ALL);t.getWorld().removeBlock(mutation,false);
        var after=pose(t.getWorld(),entry,x,z,origin);
        require(after!=null && after.nativePose()==pose.nativePose(),"neighbor edit removed or relocated placement fact");
    }
    private static Pose pose(ServerWorld world,Case entry,int x,int z,int origin) {
        int low=Math.floorDiv(origin,2)-1;
        for(int y=low;y<=low+3;y++) {
            var owner=new BlockPos(x,y,z);var state=world.getBlockState(owner);var seat=NativeSeats.seat(world,owner);
            if(state.isOf(entry.block()) && seat!=null && y*2+seat.halfOffset()==origin)
                return new Pose(owner,state,origin,NativeShapes.outline(world,owner,state).offset(x,origin*.5,z),true);
            var contents=CellWorld.contents(world,owner);
            if(contents!=null)for(var part:contents.fragments())if(part.value().isOf(entry.block()) && y*2+part.offset()==origin)
                return new Pose(owner,part.value(),origin,NativeShapes.outline(world,owner,part.value()).offset(x,origin*.5,z),false);
        }
        return null;
    }
    private static boolean containsPiece(ServerWorld world,BlockPos cell,Block block,int origin) {
        var contents=CellWorld.contents(world,cell);
        return contents!=null && contents.fragments().stream().anyMatch(part->part.value().isOf(block) && cell.getY()*2+part.offset()==origin);
    }
    private static BlockHitResult ray(TestContext t,BlockPos base,Vec3d start,Vec3d end) {
        return t.getWorld().raycast(new RaycastContext(start.add(Vec3d.of(base)),end.add(Vec3d.of(base)),
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,t.createMockPlayer(GameMode.SURVIVAL)));
    }
    private static void clear(ServerWorld world,BlockPos base) {
        for(int x=-1;x<=2;x++)for(int y=-3;y<=5;y++)for(int z=-1;z<=2;z++) {
            var pos=base.add(x,y,z);world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);NativeSeats.remove(world,pos);
        }
    }
    private static void run(List<String> failures,String label,Runnable task) {
        try {task.run();}catch(RuntimeException|AssertionError failure) {failures.add(label+": "+failure.getMessage());}
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void finish(TestContext t,List<String> failures,String label) {
        t.assertTrue(failures.isEmpty(),label+" failures ("+failures.size()+"): "+String.join("; ",failures));t.complete();
    }
}
