package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.cell.CellBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real crosshair, interaction packets and server confirmations for physical stacks. */
public final class PhysicalClientProbe implements ClientModInitializer {
    private static final BlockPos BASE=new BlockPos(8,48,8);
    private static final int COUNT=258;
    private int ticks,stage,step,started;
    private boolean requested;
    private volatile boolean ready,pending;
    private volatile Throwable failure;
    private volatile int confirmed=-1;
    private volatile boolean broken,saved;
    private boolean reopen;
    private int clearanceStage;
    private boolean hitParticlesChecked;
    private boolean lifecycleChecked;
    private double top=BASE.getY()+.5;
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("slabbed.physicalProbe")) return;
        reopen=Boolean.getBoolean("slabbed.reopenProbe");
        String manifest=System.getProperty("slabbed.packManifest","");
        if(!manifest.isEmpty()) {
            try {
                var entries=com.google.gson.JsonParser.parseString(Files.readString(Path.of(manifest))).getAsJsonArray();
                for(var raw:entries) {
                    var e=raw.getAsJsonObject(); var mod=FabricLoader.getInstance().getModContainer(e.get("id").getAsString()).orElseThrow();
                    check(mod.getMetadata().getVersion().getFriendlyString().equals(e.get("version").getAsString()),"wrong pack version: "+e);
                }
                System.out.println("PHYSICAL_FULL_PACK_LOADED "+entries.size());
            } catch(Exception e) { throw new IllegalStateException("Pack verification failed",e); }
        }
        ClientTickEvents.END_CLIENT_TICK.register(client->{ try { tick(client); } catch(Throwable e) { e.printStackTrace(); System.exit(1); } });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context->CellFeedbackAudit.changedSections.clear());
    }
    private void tick(MinecraftClient client) throws Exception {
        check(++ticks<10000,"physical client timeout stage="+stage+" step="+step);
        if(failure!=null) throw new AssertionError("server failed",failure);
        if(!requested && client.isFinishedLoading()) {
            requested=true;
            if(reopen) client.createIntegratedServerLoader().start("physical-proof",()->{throw new AssertionError("world reopen cancelled");});
            else client.createIntegratedServerLoader().createAndStart("physical-proof",
                    new LevelInfo("Physical slab proof",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(0L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),null);
            return;
        }
        if(client.world==null || client.player==null || client.getServer()==null) return;
        client.options.pauseOnLostFocus=false;
        if(!DiagnosticClientProbe.begin(client))return;
        if(!NativeFunctionalClientProbe.tick(client,reopen))return;
        if(!DiagnosticClientProbe.finish(client))return;
        if(client.currentScreen!=null) client.setScreen(null);
        client.player.getAbilities().flying=true;
        client.player.setVelocity(Vec3d.ZERO);
        if(reopen && stage==0) { top=BASE.getY()+.5+COUNT*.75; step=COUNT; stage=4; started=ticks; CellRenderAudit.begin((int)Math.floor(top-2)); teleport(client); return; }
        if(stage==0) {
            stage=1;
            server(client,()->{
                var w=client.getServer().getOverworld();
                for(int y=BASE.getY();y<BASE.getY()+COUNT;y++) w.setBlockState(new BlockPos(BASE.getX(),y,BASE.getZ()),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                w.setBlockState(BASE,Blocks.STONE_SLAB.getDefaultState(),Block.NOTIFY_ALL);
                w.setBlockState(BASE.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ready=true;
            }); return;
        }
        if(!reopen && !ready) return;
        if(!reopen && clearanceStage<4) {
            if(clearanceStage==0) { if(pending)return;teleport(client);clearanceStage=1;started=ticks;return; }
            if(clearanceStage==1) {
                if(ticks-started<10 || !client.world.getBlockState(BASE.up()).isOf(Blocks.STONE))return;
                var stack=new ItemStack(Items.STONE,64);client.player.setStackInHand(Hand.MAIN_HAND,stack);
                client.interactionManager.clickCreativeStack(stack.copy(),36+client.player.getInventory().selectedSlot);
                var hit=new BlockHitResult(new Vec3d(BASE.getX()+.5,BASE.getY()+.5,BASE.getZ()+.5),Direction.UP,BASE,false);
                CellClearancePacketAudit.arm(BASE);
                var result=client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit);
                boolean control=Boolean.getBoolean("slabbed.controlProbe");
                System.out.println("PHYSICAL_CLEARANCE_OBSERVED accepted="+result.isAccepted()+" control="+control);
                check(result.isAccepted()==control,"client accepted placement into an occupied upper half");
                clearanceStage=2;return;
            }
            if(clearanceStage==2) {
                if(!CellClearancePacketAudit.completed)return;
                check(CellClearancePacketAudit.unchanged,"blocked placement changed the server fixture");
                System.out.println("PHYSICAL_CLEARANCE_SERVER_PACKET_REJECTED");
                if(Boolean.getBoolean("slabbed.controlProbe")) {
                    finish(client,"PHYSICAL_CONTROL_REPRODUCED_BLOCKED_PLACEMENT");return;
                }
                server(client,()->client.getServer().getOverworld().setBlockState(BASE.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL));
                clearanceStage=3;return;
            }
            if(pending || !client.world.getBlockState(BASE.up()).isAir())return;
            clearanceStage=4;System.out.println("PHYSICAL_CLEARANCE_PASS");
        }
        if(!reopen && !lifecycleChecked && !Boolean.getBoolean("slabbed.controlProbe")) {
            var fixture=BASE.east().up();
            aimAt(client,Vec3d.ofCenter(fixture));
            if(!CellClientLifecycleRegression.transition(client,fixture))return;
            lifecycleChecked=true;
        }
        if(stage==1) { if(pending)return; started=ticks; stage=2; teleport(client); return; }
        if(stage==2) {
            if(ticks-started<5 || client.player.getPos().squaredDistanceTo(new Vec3d(BASE.getX()+2.5,top+1,BASE.getZ()+.5))>.02) return;
            aim(client);
            var expected=BlockPos.ofFloored(BASE.getX(),top-1e-4,BASE.getZ());
            check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(expected) && hit.getSide()==Direction.UP,"crosshair missed physical top step="+step+" hit="+client.crosshairTarget);
            var stack=new ItemStack(step%2==0?Items.STONE:Items.OAK_SLAB,64);
            client.player.setStackInHand(Hand.MAIN_HAND,stack);
            client.interactionManager.clickCreativeStack(stack.copy(),36+client.player.getInventory().selectedSlot);
            check(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,(BlockHitResult)client.crosshairTarget).isAccepted(),"client refused step "+step);
            stage=3;started=ticks;return;
        }
        if(stage==3) {
            check(ticks-started<120,"server rejected step "+step);
            if(!pending) {
                final int n=step; final double expectedTop=top+(n%2==0?1:.5);
                server(client,()->{
                    var w=client.getServer().getOverworld(); var player=w.getPlayers().getFirst();
                    var ray=w.raycast(new RaycastContext(new Vec3d(BASE.getX()+.5,expectedTop+1,BASE.getZ()+.5),new Vec3d(BASE.getX()+.5,expectedTop-.2,BASE.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                    if(ray.getType()!=HitResult.Type.BLOCK || Math.abs(ray.getPos().y-expectedTop)>1e-5) return;
                    var previous=player.getPos();
                    player.setPosition(BASE.getX()+.5,expectedTop+.4,BASE.getZ()+.5);
                    player.move(MovementType.SELF,new Vec3d(0,-.8,0));
                    check(Math.abs(player.getY()-expectedTop)<1e-5,"server standing failed at "+n);
                    player.setPosition(previous);
                    confirmed=n;
                });
            }
            if(confirmed!=step) return;
            double expectedTop=top+(step%2==0?1:.5);
            var ray=client.world.raycast(new RaycastContext(new Vec3d(BASE.getX()+.5,expectedTop+1,BASE.getZ()+.5),new Vec3d(BASE.getX()+.5,expectedTop-.2,BASE.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,client.player));
            if(ray.getType()!=HitResult.Type.BLOCK || Math.abs(ray.getPos().y-expectedTop)>1e-5) return;
            top=expectedTop;step++;
            if(step%32==0) System.out.println("PHYSICAL_PACKET_STACK_CONFIRMED "+step+" top="+top);
            if(step<COUNT) { stage=1; return; }
            stage=4;started=ticks;CellRenderAudit.begin((int)Math.floor(top-2));client.worldRenderer.reload();
            client.reloadResources().exceptionally(e->{failure=e;return null;});
            System.out.println("PHYSICAL_NETWORK_PLACEMENTS_PASS count=258");return;
        }
        if(stage==4) {
            if(ticks-started<100 || pending) return;
            CellRenderAudit.verify(top);
            aim(client);
            var ray=client.player.raycast(client.player.getBlockInteractionRange(),1,false);
            check(ray instanceof BlockHitResult h && h.getType()==HitResult.Type.BLOCK && Math.abs(h.getPos().y-top)<1e-5,"reload or reopen lost physical target");
            if(!reopen) {
                stage=7;started=ticks;
                server(client,()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).networkHandler
                        .requestTeleport(BASE.getX()+80.5,top+1,BASE.getZ()+.5,90,0));
                return;
            }
            if(!hitParticlesChecked && !Boolean.getBoolean("slabbed.controlProbe")) {
                server(client,()->{
                    var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    p.changeGameMode(GameMode.SURVIVAL);p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();
                });
                stage=10;started=ticks;return;
            }
            CellFeedbackAudit.reset();
            client.interactionManager.attackBlock(((BlockHitResult)ray).getBlockPos(),Direction.UP);
            stage=6;started=ticks;return;
        }
        if(stage==10) {
            check(ticks-started<120,"survival mode not confirmed");
            if(pending || client.interactionManager.getCurrentGameMode()!=GameMode.SURVIVAL)return;
            aim(client);
            CellClientLifecycleRegression.beginHits(Blocks.OAK_SLAB.getDefaultState(),top-.5,top);
            client.options.attackKey.setPressed(true);stage=11;started=ticks;return;
        }
        if(stage==11) {
            aim(client);
            if(ticks-started<8)return;
            client.options.attackKey.setPressed(false);client.interactionManager.cancelBlockBreaking();
            CellClientLifecycleRegression.verifyHits();
            server(client,()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).changeGameMode(GameMode.CREATIVE));
            stage=12;return;
        }
        if(stage==12) {
            if(pending || client.interactionManager.getCurrentGameMode()!=GameMode.CREATIVE)return;
            hitParticlesChecked=true;stage=4;started=ticks-100;return;
        }
        if(stage==7) {
            if(client.player.getX()<BASE.getX()+79)return;
            aim(client);CellRenderAudit.begin((int)Math.floor(top-2));stage=8;started=ticks;return;
        }
        if(stage==8) {
            aim(client);
            if(ticks-started<100 || pending)return;
            CellRenderAudit.verify(top);System.out.println("PHYSICAL_FAR_RENDER_PASS distance=80");
            server(client,()->{client.getServer().saveAll(false,true,true);saved=true;});stage=5;return;
        }
        if(stage==5 && saved) {
            System.out.println("PHYSICAL_TRANSITION_OBSERVED terrainLighting="+CellRenderAudit.terrainLighting
                    +" updates="+CellFeedbackAudit.cellUpdates+" queued="+CellFeedbackAudit.queuedCellSections
                    +" delayed="+CellFeedbackAudit.delayedCellSections+" deferred="+CellFeedbackAudit.deferredCellUpdates);
            finish(client,"PHYSICAL_SAVE_PASS 258 pieces");return;
        }
        if(stage==6) {
            check(ticks-started<120,"break not confirmed");
            if(!pending) server(client,()->{
                var w=client.getServer().getOverworld();var p=w.getPlayers().getFirst();
                var ray=w.raycast(new RaycastContext(new Vec3d(BASE.getX()+.5,top+1,BASE.getZ()+.5),new Vec3d(BASE.getX()+.5,top-1,BASE.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
                broken=ray.getType()==HitResult.Type.BLOCK && Math.abs(ray.getPos().y-(top-.5))<1e-5;
            });
            if(broken) {
                if(ticks-started<15)return;
                System.out.println("PHYSICAL_FEEDBACK_OBSERVED terrainLighting="+CellRenderAudit.terrainLighting
                        +" breakParticles="+CellFeedbackAudit.breaks+" material="+CellFeedbackAudit.material
                        +" sounds="+CellFeedbackAudit.sounds+" sound="+CellFeedbackAudit.sound
                        +" updates="+CellFeedbackAudit.cellUpdates+" queued="+CellFeedbackAudit.queuedCellSections
                        +" delayed="+CellFeedbackAudit.delayedCellSections+" deferred="+CellFeedbackAudit.deferredCellUpdates);
                check(CellRenderAudit.terrainLighting,"cell faces use entity lighting in addition to terrain shading");
                check(CellFeedbackAudit.breaks>0 && CellFeedbackAudit.material.isOf(Blocks.OAK_SLAB),"missing or wrong material break particles");
                check(CellFeedbackAudit.minParticleY>=top-.5 && CellFeedbackAudit.maxParticleY<=top,"slab debris escaped the physical piece");
                check(CellFeedbackAudit.sounds==1 && Blocks.OAK_SLAB.getDefaultState().getSoundGroup().getBreakSound().getId().equals(CellFeedbackAudit.sound),"missing or wrong material break sound");
                check(CellFeedbackAudit.cellUpdates>0 && CellFeedbackAudit.deferredCellUpdates==0,"native chunk mesh lags immediate cell renderer");
                if(FabricLoader.getInstance().isModLoaded("sodium"))
                    check(CellFeedbackAudit.queuedCellSections>0 && CellFeedbackAudit.delayedCellSections==0,"Sodium defers cell geometry across render frames");
                top-=.5;aim(client);CellFeedbackAudit.reset();broken=false;
                check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                        && Math.abs(hit.getPos().y-top)<1e-5,"client retained ghost geometry after slab break");
                client.interactionManager.attackBlock(((BlockHitResult)client.crosshairTarget).getBlockPos(),Direction.UP);
                stage=9;started=ticks;
            }
        }
        if(stage==9) {
            check(ticks-started<120,"full-block break not confirmed");
            if(!pending) server(client,()->{
                var w=client.getServer().getOverworld();var p=w.getPlayers().getFirst();
                var ray=w.raycast(new RaycastContext(new Vec3d(BASE.getX()+.5,top+1,BASE.getZ()+.5),new Vec3d(BASE.getX()+.5,top-1.2,BASE.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
                broken=ray.getType()==HitResult.Type.BLOCK && Math.abs(ray.getPos().y-(top-1))<1e-5;
            });
            if(!broken || ticks-started<15)return;
            check(CellFeedbackAudit.breaks>0 && CellFeedbackAudit.material.isOf(Blocks.STONE),"full-block particles used the carrier material");
            check(CellFeedbackAudit.minParticleY>=top-1 && CellFeedbackAudit.maxParticleY<=top,"full-block debris escaped the physical piece");
            check(CellFeedbackAudit.sounds==1 && Blocks.STONE.getDefaultState().getSoundGroup().getBreakSound().getId().equals(CellFeedbackAudit.sound),"full-block break sound missing or duplicated");
            top-=1;aim(client);
            check(client.crosshairTarget instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK
                    && Math.abs(hit.getPos().y-top)<1e-5,"client retained ghost geometry after full-block break");
            System.out.println("PHYSICAL_MATERIAL_BREAK_EFFECTS_PASS oak_slab stone");
            finish(client,"PHYSICAL_REOPEN_RELOAD_BREAK_PASS 258 pieces");
        }
    }
    private void server(MinecraftClient c,Runnable task) {
        if(pending) return;pending=true;
        c.getServer().execute(()->{try{task.run();}catch(Throwable e){failure=e;}finally{pending=false;}});
    }
    private void teleport(MinecraftClient client) {
        server(client,()->{
            var p=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            p.getAbilities().flying=true;p.sendAbilitiesUpdate();
            p.networkHandler.requestTeleport(BASE.getX()+2.5,top+1,BASE.getZ()+.5,90,50);
        });
    }
    private void aim(MinecraftClient client) {
        aimAt(client,new Vec3d(BASE.getX()+.5,top,BASE.getZ()+.5));
    }
    private void aimAt(MinecraftClient client,Vec3d target) {
        var d=target.subtract(client.player.getEyePos());
        client.player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        client.player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        client.gameRenderer.updateCrosshairTarget(1);
    }
    private void finish(MinecraftClient client,String marker) throws Exception {
        Files.writeString(Path.of("physical-pass.txt"),marker+"\n");System.out.println(marker);stage=99;client.scheduleStop();
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
