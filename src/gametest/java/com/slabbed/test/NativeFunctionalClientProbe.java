package com.slabbed.test;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.BedBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.util.function.BooleanSupplier;

/** Native menus, editing, sleep and paired removal through a production client connection. */
public final class NativeFunctionalClientProbe {
    private static final BlockPos SIGN_BASE=new BlockPos(18,63,8),SIGN=SIGN_BASE.up();
    private static final BlockPos BED_BASE=new BlockPos(22,63,8),FOOT=BED_BASE.up(),HEAD=FOOT.south();
    private static final BlockPos TABLE_BASE=new BlockPos(26,63,8),TABLE=TABLE_BASE.up();
    private static final BlockPos LIGHT_OWNER=new BlockPos(30,64,8);
    private static final String MESSAGE="Physical seat";
    private static int stage,ticks,started;
    private static volatile boolean pending,observed;
    private static volatile Throwable failure;
    private static boolean bedDiagnosed;
    private NativeFunctionalClientProbe() {}

    /** Call before the parent probe closes screens; false means this helper still owns the player. */
    public static boolean tick(MinecraftClient client,boolean reopen) {
        if(Boolean.getBoolean("slabbed.controlProbe"))return true;
        if(!NativePistonClientProbe.tick(client))return false;
        if(!NativeOffsetPistonClientProbe.tick(client))return false;
        if(!NativeParticleClientProbe.tick(client))return false;
        if(!SharedSlabClientProbe.tick(client))return false;
        if(!SurfaceDustLightChecks.tick(client))return false;
        if(!StackDustLightChecks.tick(client))return false;
        if(!SideSlabClientProbe.tick(client))return false;
        if(!NativeRedstoneLightClientProbe.tick(client))return false;
        if(failure!=null)throw new AssertionError("native functional server probe failed",failure);
        if(stage==99)return true;
        NativeRenderAudit.tick(client);
        ticks++;
        check(ticks-started<240,"native functional timeout stage="+stage+" screen="+client.currentScreen);
        if(pending)return false;
        if(stage==0) {
            if(reopen) {
                move(client,24.5,65,8.5);stage=20;started=ticks;return false;
            }
            server(client,()->{
                var world=client.getServer().getOverworld();
                for(int x=17;x<=28;x++)for(int y=63;y<=67;y++)for(int z=7;z<=10;z++)
                    world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                for(var pos:new BlockPos[]{SIGN_BASE,BED_BASE,BED_BASE.south(),TABLE_BASE})
                    world.setBlockState(pos,Blocks.OAK_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(new BlockPos(24,64,8),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                for(int y=63;y<=66;y++)world.setBlockState(new BlockPos(30,y,8),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                world.setBlockState(LIGHT_OWNER,Blocks.BARREL.getDefaultState(),Block.NOTIFY_ALL);
                com.slabbed.nativeblock.NativeSeats.put(world,LIGHT_OWNER,new com.slabbed.nativeblock.NativeSeats.Seat(-1,"minecraft:barrel"));
                ((net.minecraft.block.entity.BarrelBlockEntity)world.getBlockEntity(LIGHT_OWNER)).setStack(0,new ItemStack(Items.DIAMOND,7));
                check(com.slabbed.cell.CellWorld.place(world,30,8,129,Blocks.GLOWSTONE.getDefaultState()),"native light fixture has clear upper half");
                world.setTimeOfDay(13000);
            });
            next(1);return false;
        }
        if(stage==1) { move(client,20.5,65,8.5);next(2);return false; }
        if(stage==2) {
            if(!arrived(client,20.5,65,8.5) || !client.world.getBlockState(SIGN_BASE).isOf(Blocks.OAK_SLAB))return false;
            equip(client,new ItemStack(Items.OAK_SIGN,4));place(client,SIGN_BASE);next(3);return false;
        }
        if(stage==3) {
            if(!(client.world.getBlockEntity(SIGN) instanceof SignBlockEntity) || !(client.currentScreen instanceof AbstractSignEditScreen))return false;
            System.out.println("PHYSICAL_NATIVE_SIGN_EDITOR_PASS");
            for(char character:MESSAGE.toCharArray())client.currentScreen.charTyped(character,0);
            client.getNetworkHandler().sendPacket(new UpdateSignC2SPacket(SIGN,true,MESSAGE,"","",""));
            client.setScreen(null);next(4);return false;
        }
        if(stage==4) {
            if(!poll(client,()->{
                if(!sign(client).getFrontText().getMessages(false)[0].getString().equals(MESSAGE))return false;
                verifyServerSignGeometry(client);return true;
            }))return false;
            verifySignGeometry(client);
            equip(client,new ItemStack(Items.GLOW_INK_SAC,4));use(client,SIGN,new Vec3d(18.5,64.25,8.5));next(5);return false;
        }
        if(stage==5) {
            if(!poll(client,()->sign(client).getFrontText().isGlowing() || sign(client).getBackText().isGlowing()))return false;
            equip(client,new ItemStack(Items.HONEYCOMB,4));use(client,SIGN,new Vec3d(18.5,64.25,8.5));next(6);return false;
        }
        if(stage==6) {
            if(!poll(client,()->sign(client).isWaxed()))return false;
            if(!(client.world.getBlockEntity(SIGN) instanceof SignBlockEntity local) || !local.isWaxed())return false;
            aim(client,new Vec3d(18.5,64.25,8.5));NativeRenderAudit.beginBlockEntity(client,SIGN);next(60);return false;
        }
        if(stage==60) {
            if(ticks-started<8 || !NativeRenderAudit.readyBlockEntity(SIGN))return false;
            NativeRenderAudit.verifyBlockEntity(SIGN);move(client,24.5,65,8.5);next(7);return false;
        }
        if(stage==7) {
            if(!arrived(client,24.5,65,8.5))return false;
            equip(client,new ItemStack(Items.RED_BED,1));place(client,BED_BASE);next(8);return false;
        }
        if(stage==8) {
            if(!poll(client,()->nativeBed(client)))return false;
            if(!client.world.getBlockState(FOOT).isOf(Blocks.RED_BED) || !client.world.getBlockState(HEAD).isOf(Blocks.RED_BED))return false;
            for(var pos:new BlockPos[]{FOOT,HEAD})if(com.slabbed.nativeblock.NativeSeats.offset(client.world,pos)!=-.5)return false;
            verifyBedGeometry(client);aim(client,new Vec3d(22.5,64.0625,9));
            NativeRenderAudit.beginBlockEntity(client,FOOT);NativeRenderAudit.beginBlockEntity(client,HEAD);next(61);return false;
        }
        if(stage==61) {
            if(ticks-started<8 || !NativeRenderAudit.readyBlockEntity(FOOT) || !NativeRenderAudit.readyBlockEntity(HEAD))return false;
            NativeRenderAudit.verifyBlockEntity(FOOT);NativeRenderAudit.verifyBlockEntity(HEAD);
            equip(client,ItemStack.EMPTY);use(client,FOOT,new Vec3d(22.5,64.0625,8.5));next(9);return false;
        }
        if(stage==9) {
            if(!poll(client,()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                if(!player.isSleeping())return false;
                check(Math.abs(player.getY()-(FOOT.getY()+.1875))<1e-5,"sleep pose ignored physical bed seat: "+player.getY());
                return true;
            }))return false;
            client.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(client.player,ClientCommandC2SPacket.Mode.STOP_SLEEPING));
            next(10);return false;
        }
        if(stage==10) {
            if(!poll(client,()->!client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).isSleeping()))return false;
            if(client.currentScreen!=null)client.setScreen(null);
            System.out.println("PHYSICAL_NATIVE_BED_SLEEP_WAKE_PASS");
            move(client,28.5,65,8.5);next(11);return false;
        }
        if(stage==11) {
            if(!arrived(client,28.5,65,8.5))return false;
            equip(client,new ItemStack(Items.CRAFTING_TABLE,4));place(client,TABLE_BASE);next(12);return false;
        }
        if(stage==12) {
            if(!client.world.getBlockState(TABLE).isOf(Blocks.CRAFTING_TABLE))return false;
            var shape=client.world.getBlockState(TABLE).getOutlineShape(client.world,TABLE).getBoundingBox();
            check(Math.abs(shape.minY+.5)<1e-5 && Math.abs(shape.maxY-.5)<1e-5,"workstation outline lost physical seat");
            aim(client,new Vec3d(26.5,64.5,8.5));NativeRenderAudit.beginModel(client,TABLE);next(62);return false;
        }
        if(stage==62) {
            if(ticks-started<16 || !NativeRenderAudit.readyModel(TABLE))return false;
            NativeRenderAudit.verifyModel(TABLE);
            equip(client,ItemStack.EMPTY);use(client,TABLE,new Vec3d(26.5,64.5,8.5));next(13);return false;
        }
        if(stage==13) {
            if(!(client.currentScreen instanceof CraftingScreen))return false;
            if(!poll(client,()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).currentScreenHandler instanceof CraftingScreenHandler))return false;
            client.player.closeHandledScreen();
            server(client,()->client.getServer().getOverworld().setTimeOfDay(1000));next(14);return false;
        }
        if(stage==14) {
            if(!poll(client,()->lightSource(client)) || client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_OWNER)!=15)return false;
            System.out.println("PHYSICAL_SUPPLEMENTAL_LIGHT_FRESH_PASS");
            System.out.println("PHYSICAL_NATIVE_FUNCTIONAL_PACKETS_PASS sign_text glow wax bed_sleep crafting_menu");
            stage=99;return true;
        }
        if(stage==20) {
            if(!arrived(client,24.5,65,8.5) || !(client.world.getBlockEntity(SIGN) instanceof SignBlockEntity))return false;
            for(var pos:new BlockPos[]{SIGN,FOOT,HEAD,TABLE})
                if(com.slabbed.nativeblock.NativeSeats.offset(client.world,pos)!=-.5)return false;
            if(!poll(client,()->{
                var sign=sign(client);
                check(sign.getFrontText().getMessages(false)[0].getString().equals(MESSAGE),"saved sign text missing");
                check(sign.isWaxed() && (sign.getFrontText().isGlowing() || sign.getBackText().isGlowing()),"saved sign glow/wax missing");
                verifyServerSignGeometry(client);
                check(nativeBed(client),"saved bed lost native pair");
                check(client.getServer().getOverworld().getBlockState(TABLE).isOf(Blocks.CRAFTING_TABLE),"saved workstation missing");
                if(!lightSource(client))return false;
                System.out.println("PHYSICAL_SUPPLEMENTAL_LIGHT_REOPEN_PASS");return true;
            }))return false;
            verifySignGeometry(client);verifyBedGeometry(client);
            aim(client,new Vec3d(18.5,64.25,8.5));NativeRenderAudit.beginBlockEntity(client,SIGN);next(63);return false;
        }
        if(stage==63) {
            if(ticks-started<8 || !NativeRenderAudit.readyBlockEntity(SIGN))return false;
            NativeRenderAudit.verifyBlockEntity(SIGN);aim(client,new Vec3d(22.5,64.0625,9));
            NativeRenderAudit.beginBlockEntity(client,FOOT);NativeRenderAudit.beginBlockEntity(client,HEAD);next(64);return false;
        }
        if(stage==64) {
            if(ticks-started<8 || !NativeRenderAudit.readyBlockEntity(FOOT) || !NativeRenderAudit.readyBlockEntity(HEAD))return false;
            NativeRenderAudit.verifyBlockEntity(FOOT);NativeRenderAudit.verifyBlockEntity(HEAD);
            aim(client,new Vec3d(26.5,64.5,8.5));NativeRenderAudit.beginModel(client,TABLE);next(65);return false;
        }
        if(stage==65) {
            if(ticks-started<16 || !NativeRenderAudit.readyModel(TABLE))return false;
            NativeRenderAudit.verifyModel(TABLE);
            server(client,()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.changeGameMode(GameMode.SURVIVAL);
                player.getAbilities().allowFlying=true;player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });next(21);return false;
        }
        if(stage==21) {
            if(ticks-started<10 || client.interactionManager.getCurrentGameMode()!=GameMode.SURVIVAL)return false;
            aim(client,new Vec3d(22.5,64.0625,8.5));
            check(client.crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(FOOT),"saved bed physical target missed: "+client.crosshairTarget);
            client.interactionManager.attackBlock(FOOT,Direction.UP);next(22);return false;
        }
        if(stage==22) {
            if(!client.world.getBlockState(FOOT).isAir())client.interactionManager.updateBlockBreakingProgress(FOOT,Direction.UP);
            if(!poll(client,()->{
                var world=client.getServer().getOverworld();
                if(!world.getBlockState(FOOT).isAir() || !world.getBlockState(HEAD).isAir())return false;
                int drops=world.getEntitiesByClass(ItemEntity.class,new Box(FOOT).expand(3),entity->entity.getStack().isOf(Items.RED_BED))
                        .stream().mapToInt(entity->entity.getStack().getCount()).sum();
                check(drops==1,"native bed pair did not drop exactly once: "+drops);return true;
            }))return false;
            if(!client.world.getBlockState(FOOT).isAir() || !client.world.getBlockState(HEAD).isAir())return false;
            var ray=client.world.raycast(new RaycastContext(new Vec3d(22.5,66,8.5),new Vec3d(22.5,63,8.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,client.player));
            check(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(BED_BASE) && Math.abs(ray.getPos().y-63.5)<1e-5,"bed removal retained physical ghost geometry");
            server(client,()->{
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.sendAbilitiesUpdate();
            });
            next(23);return false;
        }
        if(stage==23) {
            if(client.interactionManager.getCurrentGameMode()!=GameMode.CREATIVE)return false;
            System.out.println("PHYSICAL_NATIVE_REOPEN_PAIR_BREAK_PASS sign_text glow wax bed_pair one_drop");
            server(client,()->com.slabbed.cell.CellWorld.remove(client.getServer().getOverworld(),LIGHT_OWNER,1,null,false));
            next(24);return false;
        }
        if(stage==24) {
            if(!poll(client,()-> {
                var world=client.getServer().getOverworld();
                check(com.slabbed.cell.SupplementalCells.luminance(world,LIGHT_OWNER)==0,"removed light source attachment remains");
                check(world.getBlockEntity(LIGHT_OWNER) instanceof net.minecraft.block.entity.BarrelBlockEntity barrel
                        && barrel.getStack(0).getCount()==7,"source removal changed native inventory");
                return world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_OWNER)<15;
            }) || client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_OWNER)>=15)return false;
            System.out.println("PHYSICAL_SUPPLEMENTAL_LIGHT_REMOVAL_PASS");stage=99;return true;
        }
        return false;
    }

    private static boolean lightSource(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        check(world.getBlockEntity(LIGHT_OWNER) instanceof net.minecraft.block.entity.BarrelBlockEntity barrel
                && barrel.getStack(0).isOf(Items.DIAMOND) && barrel.getStack(0).getCount()==7,"light fixture native inventory missing");
        check(com.slabbed.nativeblock.NativeSeats.offset(world,LIGHT_OWNER)==-.5,"light fixture native seat moved");
        check(com.slabbed.cell.SupplementalCells.luminance(world,LIGHT_OWNER)==15,"saved supplemental source missing");
        return world.getLightLevel(net.minecraft.world.LightType.BLOCK,LIGHT_OWNER)==15;
    }
    private static void next(int value) {stage=value;started=ticks;observed=false;}
    private static void server(MinecraftClient client,Runnable task) {
        pending=true;client.getServer().execute(()->{try{task.run();}catch(Throwable error){failure=error;}finally{pending=false;}});
    }
    private static boolean poll(MinecraftClient client,BooleanSupplier predicate) {
        if(observed){observed=false;return true;}
        server(client,()->observed=predicate.getAsBoolean());return false;
    }
    private static void move(MinecraftClient client,double x,double y,double z) {
        server(client,()->{
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.networkHandler.requestTeleport(x,y,z,0,35);
        });
    }
    private static boolean arrived(MinecraftClient client,double x,double y,double z) {
        client.player.setVelocity(Vec3d.ZERO);
        return ticks-started>=10 && client.player.getPos().squaredDistanceTo(new Vec3d(x,y,z))<.05;
    }
    private static void equip(MinecraftClient client,ItemStack stack) {
        check(stack.isEmpty() || stack.getCount()<=stack.getMaxCount(),"invalid creative test stack: "+stack);
        client.player.setStackInHand(Hand.MAIN_HAND,stack);
        client.interactionManager.clickCreativeStack(stack.copy(),36+client.player.getInventory().selectedSlot);
    }
    private static void place(MinecraftClient client,BlockPos base) {
        if(base.equals(BED_BASE))System.out.println("PHYSICAL_NATIVE_BED_CLIENT_PLACEMENT yaw="+client.player.getYaw()
                +" position="+client.player.getPos()+" support="+client.world.getBlockState(base));
        check(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofBottomCenter(base).add(0,.5,0),Direction.UP,base,false)).isAccepted(),"native placement refused at "+base);
    }
    private static void use(MinecraftClient client,BlockPos owner,Vec3d point) {
        aim(client,point);
        check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(owner),"native physical target missed "+owner+": "+client.crosshairTarget);
        check(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,(BlockHitResult)client.crosshairTarget).isAccepted(),"native interaction refused at "+owner);
    }
    private static void aim(MinecraftClient client,Vec3d point) {
        var delta=point.subtract(client.player.getEyePos());
        client.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        client.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        client.gameRenderer.updateCrosshairTarget(1);
    }
    private static SignBlockEntity sign(MinecraftClient client) {
        var entity=client.getServer().getOverworld().getBlockEntity(SIGN);
        check(entity instanceof SignBlockEntity,"native sign block entity missing");return (SignBlockEntity)entity;
    }
    private static boolean nativeBed(MinecraftClient client) {
        var world=client.getServer().getOverworld();var foot=world.getBlockState(FOOT);var head=world.getBlockState(HEAD);
        if(!foot.isOf(Blocks.RED_BED) || !head.isOf(Blocks.RED_BED)) {
            if(!bedDiagnosed && ticks-started>20) {
                bedDiagnosed=true;
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                System.out.println("PHYSICAL_NATIVE_BED_SERVER_PLACEMENT foot="+foot+" head="+head
                        +" north="+world.getBlockState(FOOT.north())+" east="+world.getBlockState(FOOT.east())
                        +" west="+world.getBlockState(FOOT.west())+" support="+world.getBlockState(BED_BASE)
                        +" seat="+com.slabbed.nativeblock.NativeSeats.seat(world,FOOT)
                        +" yaw="+player.getYaw()+" held="+player.getMainHandStack()+" position="+player.getPos());
            }
            return false;
        }
        for(var pos:new BlockPos[]{FOOT,HEAD}) {
            var shape=world.getBlockState(pos).getOutlineShape(world,pos).getBoundingBox();
            check(Math.abs(shape.minY+.5)<1e-5 && Math.abs(shape.maxY-.0625)<1e-5,"server bed outline lost physical seat at "+pos);
        }
        return foot.get(BedBlock.PART)==BedPart.FOOT && head.get(BedBlock.PART)==BedPart.HEAD && foot.get(BedBlock.FACING)==Direction.SOUTH;
    }
    private static void verifyServerSignGeometry(MinecraftClient client) {
        var world=client.getServer().getOverworld();var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        var ray=world.raycast(new RaycastContext(new Vec3d(18.5,63.75,6),new Vec3d(18.5,63.75,10),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        check(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(SIGN),"server sign ray lost native owner: "+ray);
    }
    private static void verifySignGeometry(MinecraftClient client) {
        var shape=client.world.getBlockState(SIGN).getOutlineShape(client.world,SIGN).getBoundingBox();
        check(Math.abs(shape.minY+.5)<1e-5,"native sign outline lost physical seat");
        var ray=client.world.raycast(new RaycastContext(new Vec3d(18.5,63.75,6),new Vec3d(18.5,63.75,10),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,client.player));
        check(ray.getType()==HitResult.Type.BLOCK && ray.getBlockPos().equals(SIGN),"native sign ray lost owner below native cell: "+ray);
    }
    private static void verifyBedGeometry(MinecraftClient client) {
        for(var pos:new BlockPos[]{FOOT,HEAD}) {
            var shape=client.world.getBlockState(pos).getOutlineShape(client.world,pos).getBoundingBox();
            check(Math.abs(shape.minY+.5)<1e-5 && Math.abs(shape.maxY-.0625)<1e-5,"native bed outline lost physical seat at "+pos);
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
