package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellBlockEntity;
import com.slabbed.core.CellContents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Splits the real state/data packet handlers across rendered frames, then watches live BER vertices. */
public final class CellClientLifecycleRegression {
    private static BlockPos watched;
    private static BlockState original;
    private static int phase,frames,vertices,completeFrames;
    private static int rendererCalls;
    private static String rendererState="not_called";
    private static boolean installed;
    private static boolean watchingHits;
    private static BlockState hitMaterial;
    private static double hitLow,hitHigh;
    private static int hits;
    private CellClientLifecycleRegression() {}

    /** Call each client tick, with a loaded, visible, disposable air position, until true. */
    public static boolean transition(MinecraftClient client,BlockPos pos) {
        if(!installed) {
            installed=true;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(context->{vertices=0;rendererCalls=0;rendererState="not_called";});
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context->afterFrame(client));
        }
        if(phase==0) {
            watched=pos.toImmutable();original=client.world.getBlockState(pos);
            check(original.isAir(),"transition fixture needs disposable air");
            client.world.setBlockState(pos,Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL|Block.REDRAW_ON_MAIN_THREAD);
            phase=1;frames=0;
        } else if(phase==1 && frames>=8) {
            client.getNetworkHandler().onBlockUpdate(new BlockUpdateS2CPacket(watched,Slabbed.CELL.getDefaultState()));
            phase=2;frames=0;
        } else if(phase==2 && frames>=5) {
            var data=new CellBlockEntity(watched,Slabbed.CELL.getDefaultState());
            data.setWorld(client.world);
            data.setContents(CellContents.<BlockState>empty()
                    .add(new CellContents.Fragment<>(0,1,Blocks.OAK_SLAB.getDefaultState()))
                    .add(new CellContents.Fragment<>(1,1,Blocks.BIRCH_SLAB.getDefaultState())));
            client.getNetworkHandler().onBlockEntityUpdate(BlockEntityUpdateS2CPacket.create(data,
                    (entity,lookup)->entity.createNbt(client.world.getRegistryManager())));
            phase=3;frames=0;
        } else if(phase==3 && frames>=8) {
            check(completeFrames>=8,"complete carrier did not submit vertices in every rendered frame");
            verifyPendingPrediction(client);
            verifyCancelledTransition(client);
            client.world.setBlockState(watched,original,Block.NOTIFY_ALL|Block.REDRAW_ON_MAIN_THREAD);
            watched=null;phase=4;
            System.out.println("PHYSICAL_RENDER_LIFECYCLE_PASS split_packets=5_frames complete_vertex_frames="+completeFrames);
        }
        return phase==4;
    }
    private static void afterFrame(MinecraftClient client) {
        if(watched==null || client.world==null)return;
        frames++;
        if(phase==2) check(client.world.getBlockState(watched).isOf(Blocks.OAK_SLAB),
                "rendered frame exposed an empty carrier between blockstate and contents packets");
        if(phase==3) {
            check(client.world.getBlockEntity(watched) instanceof CellBlockEntity cell && cell.contents().fragments().size()==2,
                    "rendered frame exposed partial carrier contents");
            var entity=(CellBlockEntity)client.world.getBlockEntity(watched);
            check(vertices>0,"carrier missing from live renderer in frame "+frames+" calls="+rendererCalls
                    +" entry="+rendererState+" world="+entity.hasWorld()+" bounds="+entity.renderBounds()
                    +" chunkContains="+client.world.getWorldChunk(watched).getBlockEntities().containsKey(watched));
            check(vertices==48,"two slab carrier submitted duplicate or incomplete geometry: "+vertices);
            completeFrames++;
        }
    }
    private static void verifyPendingPrediction(MinecraftClient client) {
        client.world.setBlockState(watched,Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
        var predictions=((com.slabbed.test.mixin.CellPendingUpdateAccess)client.world).slabbed$pendingUpdates();
        predictions.addPendingUpdate(watched,Blocks.OAK_SLAB.getDefaultState(),client.player);
        client.getNetworkHandler().onBlockUpdate(new BlockUpdateS2CPacket(watched,Slabbed.CELL.getDefaultState()));
        var data=new CellBlockEntity(watched,Slabbed.CELL.getDefaultState());
        data.setWorld(client.world);
        data.setContents(CellContents.<BlockState>empty()
                .add(new CellContents.Fragment<>(0,1,Blocks.OAK_SLAB.getDefaultState()))
                .add(new CellContents.Fragment<>(1,1,Blocks.BIRCH_SLAB.getDefaultState())));
        client.getNetworkHandler().onBlockEntityUpdate(BlockEntityUpdateS2CPacket.create(data,
                (entity,lookup)->entity.createNbt(client.world.getRegistryManager())));
        var complete=client.world.getBlockEntity(watched);
        check(complete instanceof CellBlockEntity cell && cell.contents().fragments().size()==2,
                "pending vanilla prediction discarded complete carrier contents");
        client.world.handlePlayerActionResponse(predictions.getSequence());
        check(client.world.getBlockEntity(watched)==complete,"prediction acknowledgement replaced complete carrier contents");
        System.out.println("PHYSICAL_CARRIER_PREDICTION_ACK_PASS");
    }
    private static void verifyCancelledTransition(MinecraftClient client) {
        client.getNetworkHandler().onBlockUpdate(new BlockUpdateS2CPacket(watched,Blocks.AIR.getDefaultState()));
        client.getNetworkHandler().onBlockUpdate(new BlockUpdateS2CPacket(watched,Slabbed.CELL.getDefaultState()));
        client.getNetworkHandler().onBlockUpdate(new BlockUpdateS2CPacket(watched,Blocks.STONE.getDefaultState()));
        var stale=new CellBlockEntity(watched,Slabbed.CELL.getDefaultState());
        stale.setWorld(client.world);
        stale.setContents(CellContents.<BlockState>empty().add(new CellContents.Fragment<>(0,1,Blocks.OAK_SLAB.getDefaultState())));
        client.getNetworkHandler().onBlockEntityUpdate(BlockEntityUpdateS2CPacket.create(stale,
                (entity,lookup)->entity.createNbt(client.world.getRegistryManager())));
        check(client.world.getBlockState(watched).isOf(Blocks.STONE),"late cell contents resurrected a replaced carrier");
    }
    public static boolean watches(BlockPos pos) { return watched!=null && watched.equals(pos); }
    public static void vertex() { vertices++; }
    public static void renderer(CellBlockEntity cell,boolean visible) {
        if(!watches(cell.getPos()))return;
        rendererCalls++;rendererState="world="+cell.hasWorld()+",bounds="+cell.renderBounds()+",visible="+visible;
    }

    /** Arm before holding the real attack key in survival; captures actual dust constructors, including vanilla's direct path. */
    public static void beginHits(BlockState material,double low,double high) {
        watchingHits=true;hitMaterial=material;hitLow=low;hitHigh=high;hits=0;
    }
    public static void dust(BlockState material,double x,double y,double z) {
        if(!watchingHits)return;
        check(material.isOf(hitMaterial.getBlock()),"survival hit particles used "+material+" instead of "+hitMaterial);
        check(y>=hitLow-.101 && y<=hitHigh+.101,"survival hit particles escaped selected piece: "+y);
        hits++;
    }
    public static void verifyHits() {
        watchingHits=false;
        check(hits>0,"survival attack emitted no hit particles");
        System.out.println("PHYSICAL_SURVIVAL_HIT_PARTICLES_PASS material="+hitMaterial+" hits="+hits);
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
