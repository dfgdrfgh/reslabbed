package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabbedLabFixtures;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.Map;

/** Real creation and explicit saved geometry are separate fixtures (LAW.md). */
public final class SlabbedLabFixtureTest {
    private static BlockPos origin(TestContext h) { return h.getAbsolutePos(new BlockPos(2, 2, 2)); }
    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }
    private static void seed(ServerWorld w, BlockPos p, BlockState state, double dy) {
        w.setBlockState(p, state, Block.NOTIFY_LISTENERS);
        if (SlabAnchorAttachment.writePlacementDyBatch(w, Map.of(p, Double.doubleToRawLongBits(dy))) != 1)
            throw new AssertionError("Saved fixture height did not publish");
    }
    private static double dy(ServerWorld w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }
    private static void place(TestContext h, Item item, BlockPos owner, Direction side) {
        var w = h.getWorld();
        var box = w.getBlockState(owner).getOutlineShape(w, owner, ShapeContext.absent()).getBoundingBox();
        double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
        switch (side) {
            case UP -> y = box.maxY;
            case DOWN -> y = box.minY;
            case EAST -> { x = box.maxX; y = box.minY + (box.maxY - box.minY) * .25; }
            case WEST -> { x = box.minX; y = box.minY + (box.maxY - box.minY) * .25; }
            case NORTH -> z = box.minZ;
            case SOUTH -> z = box.maxZ;
        }
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(owner.getX() + 4.5, owner.getY(), owner.getZ() + 4.5);
        var hit = new BlockHitResult(new Vec3d(owner.getX()+x, owner.getY()+y, owner.getZ()+z), side, owner, false);
        h.assertTrue(PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(item), hit).isAccepted(),
                "Real placement refused: " + item + " face=" + side);
    }
    private static BlockPos onSlab(TestContext h, Item item) {
        BlockPos p = origin(h);
        h.getWorld().setBlockState(p, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
        place(h, item, p, Direction.UP);
        h.assertTrue(dy(h.getWorld(), p.up()) == -.5, "Placement must rest on the visible slab top");
        return p.up();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void labSupportCycle(TestContext h) {
        var w = h.getWorld();
        BlockPos p = h.getAbsolutePos(new BlockPos(0, 1, 0));
        h.assertTrue(SlabbedLabFixtures.placeBasicFixture(w, p).ok(), "Basic fixture did not fit");
        for (String lane : new String[]{"FULL", "BOTTOM_SLAB", "TOP_SLAB"})
            h.assertTrue(SlabbedLabFixtures.queryStatus(w, p, lane).get(0).supportMatch(), "Wrong support: " + lane);
        h.assertTrue(SlabbedLabFixtures.breakSupport(w,p,"FULL").ok(), "Break failed");
        h.assertTrue(SlabbedLabFixtures.restoreSupport(w,p,"FULL").ok(), "Restore failed");
        h.assertTrue(SlabbedLabFixtures.neighborUpdatePulse(w,p,"FULL").ok(), "Pulse failed");
        h.assertTrue(SlabbedLabFixtures.queryStatus(w,p,"FULL").get(0).supportMatch(), "Pulse changed support");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void mc1211ServerStateOverlapMatrix(TestContext h) {
        var w = h.getWorld();
        BlockPos p = origin(h);
        Item[] items = {Items.STONE, Items.GLASS, Items.JUKEBOX, Items.COMPOSTER};
        for (Item item : items) {
            w.setBlockState(p.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            w.setBlockState(p, slab(SlabType.BOTTOM), Block.NOTIFY_ALL);
            place(h,item,p,Direction.UP);
            var body = w.getBlockState(p.up()).getOutlineShape(w,p.up()).getBoundingBox();
            h.assertTrue(dy(w,p.up()) == -.5 && body.minY == -.5,
                    "Placed body must meet the support without overlap: " + item);
        }
        w.setBlockState(p.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(p,slab(SlabType.TOP),Block.NOTIFY_ALL);
        place(h,Items.STONE,p,Direction.UP);
        h.assertTrue(dy(w,p.up()) == 0, "Top slab must support a native-height block");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void outlineRaycastParity(TestContext h) {
        BlockPos p = onSlab(h,Items.COMPOSTER);
        var w=h.getWorld(); var state=w.getBlockState(p);
        var outline=state.getOutlineShape(w,p).getBoundingBox();
        var ray=state.getRaycastShape(w,p).getBoundingBox();
        h.assertTrue(outline.minY == -.5 && ray.minY == outline.minY, "Outline and raycast need exactly one offset");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void blockEntityFullCubeSitsOnSlab(TestContext h) {
        BlockPos p=onSlab(h,Items.JUKEBOX); var w=h.getWorld();
        h.assertTrue(SlabSupport.isLoweredBlockEntityVisual(w,p,w.getBlockState(p)), "Block entity visual lost its seat");
        h.assertTrue(w.getBlockState(p).getOutlineShape(w,p).getMin(Direction.Axis.Y)==-.5,"Block entity outline mismatch");
        h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void solidCubeLowersOverSlab(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE);
        h.assertTrue(h.getWorld().getBlockState(p).getCollisionShape(h.getWorld(),p).getMin(Direction.Axis.Y)==-.5,
                "Solid cube collision must use its placed height"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetOutlineNotDoubled(TestContext h) {
        BlockPos p=onSlab(h,Items.WHITE_CARPET); var w=h.getWorld();
        var shape=w.getBlockState(p).getOutlineShape(w,p);
        h.assertTrue(shape.getMin(Direction.Axis.Y)==-.5 && shape.getMax(Direction.Axis.Y)==-.4375,
                "Carpet must translate its native thin outline once"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void frozenFlatBlockStaysFlatWhenSlabAddedBelow(TestContext h) {
        var w=h.getWorld(); BlockPos p=origin(h);
        w.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        place(h,Items.STONE,p,Direction.UP); BlockPos subject=p.up();
        h.assertTrue(SlabAnchorAttachment.rawPlacementDyFact(w,subject).present() && dy(w,subject)==0,"Explicit zero is required");
        w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(p,slab(SlabType.BOTTOM),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,subject)==0 && w.getBlockState(subject).getOutlineShape(w,subject).getMin(Direction.Axis.Y)==0,
                "Later support must not move an explicit zero"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void naturalBlockStaysNativeWhenSlabAddedBelow(TestContext h) {
        var w=h.getWorld(); BlockPos p=origin(h).up();
        w.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(!SlabAnchorAttachment.rawPlacementDyFact(w,p).present(),"Natural control acquired a fact");
        w.setBlockState(p.down(),slab(SlabType.BOTTOM),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,p)==0,"Natural block moved when a slab appeared"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredDecorativeObjectKeepsItsSeat(TestContext h) {
        BlockPos p=onSlab(h,Items.LANTERN); var w=h.getWorld();
        long before=SlabAnchorAttachment.rawPlacementDyFact(w,p).rawBits();
        w.setBlockState(p.down(),slab(SlabType.TOP),Block.NOTIFY_LISTENERS);
        h.assertTrue(w.getBlockState(p).isOf(Blocks.LANTERN) && dy(w,p)==-.5
                && SlabAnchorAttachment.rawPlacementDyFact(w,p).rawBits()==before,
                "Support retyping moved the decorative object"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorAndCeilingObjectsUseTheirClickedFace(TestContext h) {
        var w=h.getWorld(); BlockPos p=origin(h);
        for(Item item:new Item[]{Items.CHAIN,Items.POINTED_DRIPSTONE,Items.TORCH}) {
            w.setBlockState(p.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            w.setBlockState(p,slab(SlabType.BOTTOM),Block.NOTIFY_ALL);
            place(h,item,p,Direction.UP);
            h.assertTrue(dy(w,p.up())==-.5,"Floor object lost clicked floor height: "+item);
        }
        w.setBlockState(p.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(p,slab(SlabType.TOP),Block.NOTIFY_ALL);
        place(h,Items.LANTERN,p,Direction.DOWN);
        h.assertTrue(dy(w,p.down())==.5,"Hanging object must meet the underside of the top slab"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabHeightStepFacePredicate(TestContext h) {
        var w=h.getWorld(); BlockPos p=origin(h).up();
        seed(w,p,Blocks.STONE.getDefaultState(),-.5);
        seed(w,p.east(),Blocks.STONE.getDefaultState(),0);
        h.assertTrue(SlabSupport.isSlabHeightStepFace(w,p,w.getBlockState(p),Direction.EAST)
                && SlabSupport.isSlabHeightStepFace(w,p.east(),w.getBlockState(p.east()),Direction.WEST),"Step seam must expose both strips");
        seed(w,p.east(),Blocks.STONE.getDefaultState(),-.5);
        h.assertTrue(!SlabSupport.isSlabHeightStepFace(w,p,w.getBlockState(p),Direction.EAST),"Flush seats must retain culling");
        w.setBlockState(p.east(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(!SlabSupport.isSlabHeightStepFace(w,p,w.getBlockState(p),Direction.EAST),"Air needs no forced seam"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advCompoundStackTopMustBeFlush(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE); var w=h.getWorld();
        place(h,Items.STONE_SLAB,p,Direction.UP); place(h,Items.STONE,p.up(),Direction.UP);
        h.assertTrue(dy(w,p)==-.5 && dy(w,p.up())==-.5 && dy(w,p.up(2))==-1,
                "Alternating stack must accumulate only the placed slab heights"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advGroundedBesideLoweredMustNotSink(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE); var w=h.getWorld();
        w.setBlockState(p.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,p)==-.5 && dy(w,p.east())==0,"Neighboring natural terrain was adopted"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advCantileverTwoOutConsistent(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE); var w=h.getWorld();
        place(h,Items.STONE,p,Direction.EAST); place(h,Items.STONE,p.east(),Direction.EAST);
        h.assertTrue(dy(w,p.east())==-.5 && dy(w,p.east(2))==-.5,"Real side placements must keep the aimed plane"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advStaleHeightClearedAfterAirReplace(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE); var w=h.getWorld();
        w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(!SlabAnchorAttachment.rawPlacementDyFact(w,p).present(),"Removal retained a stale height");
        w.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,p)==0,"New natural block inherited the removed placement"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void advRefillSameCellNoCorruption(TestContext h) {
        BlockPos p=onSlab(h,Items.STONE); var w=h.getWorld();
        for(int i=0;i<4;i++) {
            w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            place(h,Items.STONE,p.down(),Direction.UP);
            h.assertTrue(dy(w,p)==-.5,"Repeated refill changed the seat");
        }
        w.setBlockState(p.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,p)==-.5,"Removing support moved the final refill"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void naturalCantileverDoesNotInferItsNeighborsHeight(TestContext h) {
        var w=h.getWorld(); BlockPos p=onSlab(h,Items.STONE), natural=p.east();
        w.setBlockState(natural,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,natural)==0,"Natural neighbor inferred a lowered seat");
        w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,natural)==0,"Source removal changed the natural neighbor"); h.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void adjacentCompoundColumnsKeepIndependentSeats(TestContext h) {
        var w=h.getWorld(); BlockPos p=origin(h).up(2);
        seed(w,p,Blocks.STONE.getDefaultState(),-1);
        seed(w,p.east(),Blocks.STONE.getDefaultState(),-.5);
        h.assertTrue(dy(w,p)==-1 && dy(w,p.east())==-.5
                && SlabSupport.isSlabHeightStepFace(w,p,w.getBlockState(p),Direction.EAST),"Independent saved seats must expose their step");
        w.setBlockState(p.down(),slab(SlabType.BOTTOM),Block.NOTIFY_ALL);
        h.assertTrue(dy(w,p)==-1 && dy(w,p.east())==-.5,"A support edit homogenized independent positions"); h.complete();
    }
}
