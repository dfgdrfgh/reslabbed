package com.slabbed.test;

import com.slabbed.cell.CellWorld;
import com.slabbed.nativeblock.NativeSeats;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Production placement, selection payload and break packets for a slab beside lowered soil. */
public final class SharedSlabClientProbe {
    private static final BlockPos OWNER=new BlockPos(64,64,18);
    private static final BlockPos CARRIER=OWNER.east(3);
    private static final BlockPos DARK=OWNER.east(8);
    private static int stage,ticks,brokenAt;
    private static volatile boolean pending,confirmed;
    private static volatile Throwable failure;
    private SharedSlabClientProbe() {}
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        if(failure!=null)throw new AssertionError("shared slab server fixture failed",failure);
        check(++ticks<300,"shared slab packet timeout stage="+stage);
        if(pending)return false;
        if(stage>=4 && stage<99)SlabFeedbackAudit.observe(client,OWNER);
        if(stage==0) {
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(int x=-1;x<=1;x++)for(int z=-3;z<=1;z++)for(int y=-2;y<=3;y++)
                    world.setBlockState(OWNER.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(OWNER.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(OWNER,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                NativeSeats.put(world,OWNER,new NativeSeats.Seat(-1,"minecraft:grass_block"));
                check(CellWorld.place(world,OWNER.getX(),OWNER.getZ(),OWNER.getY()*2+1,Blocks.OAK_SLAB.getDefaultState()),"first shared slab placed");
                for(int y=-2;y<=3;y++)world.setBlockState(CARRIER.up(y),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(CARRIER.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                check(CellWorld.place(world,CARRIER.getX(),CARRIER.getZ(),CARRIER.getY()*2-1,Blocks.STONE.getDefaultState()),"static carrier floor placed");
                check(CellWorld.place(world,CARRIER.getX(),CARRIER.getZ(),CARRIER.getY()*2+1,Blocks.BIRCH_SLAB.getDefaultState()),"carrier slab placed");
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-2;y<=2;y++)
                    world.setBlockState(DARK.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(DARK,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                NativeSeats.put(world,DARK,new NativeSeats.Seat(-1,"minecraft:grass_block"));
                check(CellWorld.place(world,DARK.getX(),DARK.getZ(),DARK.getY()*2+1,Blocks.OAK_SLAB.getDefaultState()),"enclosed slab placed");
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
                    world.setBlockState(DARK.add(x,1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    world.setBlockState(DARK.add(x,-2,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    if(x!=0 || z!=0)for(int y=-1;y<=0;y++)world.setBlockState(DARK.add(x,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                }
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.teleport(world,OWNER.getX()+.5,OWNER.getY()+.5,OWNER.getZ()-2.5,0,30);
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });stage=1;return false;
        }
        client.player.setVelocity(Vec3d.ZERO);
        if(stage==1) {
            if(CellWorld.contents(client.world,OWNER)==null || CellWorld.contents(client.world,OWNER).fragments().isEmpty()
                    || CellWorld.contents(client.world,CARRIER)==null || CellWorld.contents(client.world,CARRIER).fragments().size()!=2
                    || CellWorld.contents(client.world,DARK)==null || CellWorld.contents(client.world,DARK).fragments().isEmpty()
                    || NativeSeats.offset(client.world,DARK)!=-.5
                    || client.world.getLightLevel(net.minecraft.world.LightType.SKY,DARK.up())!=0
                    || client.world.getLightLevel(net.minecraft.world.LightType.SKY,DARK)!=0
                    || client.player.getPos().squaredDistanceTo(new Vec3d(OWNER.getX()+.5,OWNER.getY()+.5,OWNER.getZ()-2.5))>.1)return false;
            System.out.println("PHYSICAL_SLAB_DARK_FIXTURE_READY ticks="+ticks+" roofSky="+client.world.getLightLevel(net.minecraft.world.LightType.SKY,DARK.up())
                    +" ownerSky="+client.world.getLightLevel(net.minecraft.world.LightType.SKY,DARK));
            var firstHit=new BlockHitResult(Vec3d.ofBottomCenter(OWNER).add(0,1,0),Direction.UP,OWNER,false);
            debug(client,firstHit,"bottom");
            debugBlock(client,new BlockHitResult(Vec3d.ofBottomCenter(CARRIER).add(0,1,0),Direction.UP,CARRIER,false),"minecraft:birch_slab","bottom");
            SlabFeedbackAudit.beginHit(DARK.getY()+1.1);
            client.crosshairTarget=new BlockHitResult(Vec3d.ofBottomCenter(DARK).add(0,1,0),Direction.UP,DARK,false);
            client.particleManager.addBlockBreakingParticles(DARK,Direction.UP);
            SlabFeedbackAudit.end();SlabFeedbackAudit.contact(DARK,0);
            client.crosshairTarget=firstHit;
            SlabFeedbackAudit.beginHit(OWNER.getY()+1.1);
            client.particleManager.addBlockBreakingParticles(OWNER,Direction.UP);
            SlabFeedbackAudit.end();
            SlabFeedbackAudit.contact(OWNER);
            var stack=new ItemStack(Items.OAK_SLAB,16);client.player.setStackInHand(Hand.MAIN_HAND,stack);
            client.interactionManager.clickCreativeStack(stack.copy(),36+client.player.getInventory().selectedSlot);
            var hit=new BlockHitResult(Vec3d.ofBottomCenter(OWNER).add(0,1,0),Direction.UP,OWNER,false);
            check(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit).isAccepted(),"client accepts second matching slab");
            stage=2;return false;
        }
        if(stage==2) {
            if(!doubled(client.world))return false;
            server(client,()->{check(doubled(client.getServer().getOverworld()),"server confirms double slab from actual placement packet");confirmed=true;});
            stage=3;return false;
        }
        if(stage==3) {
            if(!confirmed)return false;
            // Aim at the lower half, which shares the native grass owner's cell.
            var delta=Vec3d.ofBottomCenter(OWNER).add(0,.75,0).subtract(client.player.getEyePos());
            client.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            client.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            client.gameRenderer.updateCrosshairTarget(1);
            check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(OWNER) && CellWorld.pick(client.world,OWNER,hit)!=null,"crosshair selects the visible shared slab");
            var hit=(BlockHitResult)client.crosshairTarget;
            debug(client,hit,"double");
            // Keep the unconfirmed piece for four ticks, as on a delayed server connection.
            SlabFeedbackAudit.beginBreak(OWNER.getY()+.5);
            com.slabbed.client.CellBreakEffects.play(client.world,OWNER,CellWorld.pick(client.world,OWNER,hit));
            SlabFeedbackAudit.end();brokenAt=ticks;stage=6;return false;
        }
        if(stage==6) {
            if(ticks-brokenAt<4)return false;
            check(doubled(client.world),"unconfirmed slab remains during delayed feedback check");
            System.out.println("PHYSICAL_SLAB_DELAYED_FEEDBACK_OBSERVED ticks="+(ticks-brokenAt));
            var hit=(BlockHitResult)client.crosshairTarget;
            SlabFeedbackAudit.beginBreak(OWNER.getY()+.5);
            check(client.interactionManager.attackBlock(OWNER,hit.getSide()),"creative break packet accepted");
            SlabFeedbackAudit.end();
            SlabFeedbackAudit.contact(OWNER);
            brokenAt=ticks;
            confirmed=false;stage=4;return false;
        }
        if(stage==4) {
            var parts=CellWorld.contents(client.world,OWNER);
            if(parts==null || !parts.fragments().isEmpty() || !client.world.getBlockState(OWNER.up()).isAir())return false;
            server(client,()-> {
                intact(client.getServer().getOverworld());confirmed=true;
            });stage=5;return false;
        }
        if(stage==5 && confirmed) {
            if(ticks-brokenAt<35)return false;
            intact(client.world);
            debugBlock(client,new BlockHitResult(Vec3d.ofBottomCenter(OWNER).add(0,.5,0),Direction.UP,OWNER,false),"minecraft:grass_block",null);
            SlabFeedbackAudit.collision(OWNER,15);
            SlabFeedbackAudit.verify();
            System.out.println("PHYSICAL_SHARED_SLAB_PACKET_PASS double_placement lower_half_selection whole_pair_break native_pose_preserved");
            stage=99;return true;
        }
        return false;
    }
    private static void debug(MinecraftClient client,BlockHitResult hit,String type) {
        debugBlock(client,hit,"minecraft:oak_slab",type);
    }
    private static void debugBlock(MinecraftClient client,BlockHitResult hit,String id,String type) {
        var hud=(com.slabbed.test.mixin.DebugHudAccess)client.getDebugHud();
        hud.slabbed$blockHit(hit);hud.slabbed$fluidHit(hit);
        var lines=hud.slabbed$rightText();
        int start=0;
        for(int i=0;i<lines.size();i++)if(lines.get(i).contains("Targeted Block")){start=i;break;}
        var target=lines.subList(start,Math.min(start+4,lines.size()));
        System.out.println("PHYSICAL_SLAB_F3_OBSERVED expected="+id+" type="+type+" target="+target);
        SlabFeedbackAudit.error(lines.stream().anyMatch(s->s.contains(id)),"F3 must name selected "+id+": "+target);
        SlabFeedbackAudit.error(type==null || lines.stream().anyMatch(s->s.contains("type") && s.contains(type)),"F3 must show selected slab type="+type);
    }
    private static boolean doubled(World world) {
        var parts=CellWorld.contents(world,OWNER);
        return parts!=null && parts.fragments().size()==1 && parts.fragments().getFirst().length()==2
                && parts.fragments().getFirst().value().isOf(Blocks.OAK_SLAB)
                && parts.fragments().getFirst().value().get(SlabBlock.TYPE)==SlabType.DOUBLE;
    }
    private static void intact(World world) {
        check(world.getBlockState(OWNER).isOf(Blocks.GRASS_BLOCK) && NativeSeats.offset(world,OWNER)==-.5,"grass remains in its original pose");
        check(CellWorld.contents(world,OWNER).fragments().isEmpty() && world.getBlockState(OWNER.up()).isAir(),"both double-slab fragments are removed");
        check(world.getBlockState(OWNER.down()).isOf(Blocks.OAK_SLAB),"original bottom support survives");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void server(MinecraftClient client,Runnable action) {
        pending=true;client.getServer().execute(()-> {
            try {action.run();}catch(Throwable error) {failure=error;}finally {pending=false;}
        });
    }
}
