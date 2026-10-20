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
    private static int stage,ticks;
    private static volatile boolean pending,confirmed;
    private static volatile Throwable failure;
    private SharedSlabClientProbe() {}
    public static boolean tick(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe") || stage==99)return true;
        if(failure!=null)throw new AssertionError("shared slab server fixture failed",failure);
        check(++ticks<300,"shared slab packet timeout stage="+stage);
        if(pending)return false;
        if(stage==0) {
            server(client,()-> {
                var world=client.getServer().getOverworld();
                for(int x=-1;x<=1;x++)for(int z=-3;z<=1;z++)for(int y=-2;y<=3;y++)
                    world.setBlockState(OWNER.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(OWNER.down(),Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(OWNER,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
                NativeSeats.put(world,OWNER,new NativeSeats.Seat(-1,"minecraft:grass_block"));
                check(CellWorld.place(world,OWNER.getX(),OWNER.getZ(),OWNER.getY()*2+1,Blocks.OAK_SLAB.getDefaultState()),"first shared slab placed");
                var player=world.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.teleport(world,OWNER.getX()+.5,OWNER.getY()+.5,OWNER.getZ()-2.5,0,30);
                player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });stage=1;return false;
        }
        client.player.setVelocity(Vec3d.ZERO);
        if(stage==1) {
            if(CellWorld.contents(client.world,OWNER)==null || CellWorld.contents(client.world,OWNER).fragments().isEmpty()
                    || client.player.getPos().squaredDistanceTo(new Vec3d(OWNER.getX()+.5,OWNER.getY()+.5,OWNER.getZ()-2.5))>.1)return false;
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
            check(client.interactionManager.attackBlock(OWNER,hit.getSide()),"creative break packet accepted");
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
            intact(client.world);
            System.out.println("PHYSICAL_SHARED_SLAB_PACKET_PASS double_placement lower_half_selection whole_pair_break native_pose_preserved");
            stage=99;return true;
        }
        return false;
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
