package com.slabbed.client.debug;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slabbed.debug.DiagnosticArchive;
import com.slabbed.debug.DiagnosticRecorder;
import com.slabbed.debug.DiagnosticWorld;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

/** Local opt-in diagnostics; world reads remain on their owner threads and disk IO is asynchronous. */
public final class DiagnosticClient {
    private static final Gson JSON=new Gson();
    private static final ScheduledExecutorService WORKER=Executors.newSingleThreadScheduledExecutor(task->{
        var thread=new Thread(task,"slabbed-diagnostics");thread.setDaemon(true);return thread;
    });
    private static final AtomicBoolean REPORTING=new AtomicBoolean(),CHECKPOINTING=new AtomicBoolean();
    private static KeyBinding toggle,export;
    private static long lastHeartbeat,lastCheckpoint;
    private static volatile Path directory;
    private DiagnosticClient() {}
    public static void init() {
        toggle=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.slabbed.debug_toggle",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.slabbed.debug"));
        export=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.slabbed.debug_report",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F9,"category.slabbed.debug"));
        ClientTickEvents.END_CLIENT_TICK.register(DiagnosticClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)-> {stop(client,false);});
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("slabbeddebug")
                .then(literal("start").executes(context->{start(MinecraftClient.getInstance());return 1;}))
                .then(literal("stop").executes(context->{stop(MinecraftClient.getInstance(),true);return 1;}))
                .then(literal("report").executes(context->{report(MinecraftClient.getInstance(),"");return 1;})
                        .then(argument("description",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(context->{
                            report(MinecraftClient.getInstance(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"description"));return 1;})))));
    }
    private static void start(MinecraftClient client) {
        if(client.world==null) {message(client,"Join a world before starting Slabbed recording.");return;}
        directory=client.runDirectory.toPath().resolve("logs/slabbed-debug");
        DiagnosticRecorder.start();lastHeartbeat=lastCheckpoint=0;
        DiagnosticRecorder.record("client","recording_started","coverage="+(client.getServer()==null?"client_only":"integrated_server_and_client"));
        message(client,"Slabbed recording ON. Reproduce the issue, then use the Export debug report key (default F9).");
    }
    private static void stop(MinecraftClient client,boolean feedback) {
        boolean active=DiagnosticRecorder.isActive();
        DiagnosticRecorder.record("client","recording_stopped","");DiagnosticRecorder.stop();
        if(active)checkpoint();
        if(feedback)message(client,"Slabbed recording OFF. Recorded history is still available to export.");
    }
    private static void tick(MinecraftClient client) {
        while(toggle.wasPressed()) {if(DiagnosticRecorder.isActive())stop(client,true);else start(client);}
        while(export.wasPressed())report(client,"");
        if(!DiagnosticRecorder.isActive())return;
        long now=System.nanoTime();
        if(now-lastHeartbeat>=TimeUnit.SECONDS.toNanos(1)) {
            lastHeartbeat=now;
            if(client.player!=null)DiagnosticRecorder.record("client","player_context",player(client).toString());
        }
        if(now-lastCheckpoint>=TimeUnit.SECONDS.toNanos(5)) {lastCheckpoint=now;checkpoint();}
    }
    private static void checkpoint() {
        var location=directory;if(location==null || !CHECKPOINTING.compareAndSet(false,true))return;
        var snapshot=DiagnosticRecorder.snapshot();
        WORKER.execute(()-> {try {DiagnosticArchive.checkpoint(location,events(snapshot));}
            catch(Exception ignored) {}finally {CHECKPOINTING.set(false);}});
    }
    private static String events(DiagnosticRecorder.Snapshot snapshot) {
        var out=new StringBuilder();for(var event:snapshot.events())out.append(JSON.toJson(event)).append('\n');return out.toString();
    }
    private static JsonObject player(MinecraftClient client) {
        var out=new JsonObject();var player=client.player;if(player==null)return out;
        out.addProperty("position",player.getPos().toString());out.addProperty("yaw",player.getYaw());out.addProperty("pitch",player.getPitch());
        out.addProperty("mainHand",held(player.getMainHandStack()));out.addProperty("offHand",held(player.getOffHandStack()));
        out.addProperty("camera",client.gameRenderer.getCamera().getPos().toString());
        out.addProperty("perspective",client.options.getPerspective().name());
        if(client.crosshairTarget instanceof BlockHitResult hit) {out.addProperty("target",hit.getBlockPos().toShortString());out.addProperty("face",hit.getSide().name());out.addProperty("hit",hit.getPos().toString());}
        return out;
    }
    private static void report(MinecraftClient client,String description) {
        if(!REPORTING.compareAndSet(false,true)) {message(client,"A Slabbed report is already being saved.");return;}
        NativeImage image=null;
        try {
            var root=new JsonObject();var limitations=new JsonArray();root.add("limitations",limitations);
            root.addProperty("format",1);root.addProperty("createdMillis",System.currentTimeMillis());
            root.addProperty("description",description.substring(0,Math.min(4096,description.length())));
            root.addProperty("screenshotNotice","Screenshot may include visible UI; review before sharing.");
            var snapshot=DiagnosticRecorder.snapshot();root.addProperty("recording",snapshot.active());root.addProperty("droppedEvents",snapshot.dropped());
            var mods=new JsonArray();FabricLoader.getInstance().getAllMods().stream().sorted(java.util.Comparator.comparing(m->m.getMetadata().getId())).forEach(mod->{
                var value=new JsonObject();value.addProperty("id",mod.getMetadata().getId());value.addProperty("version",mod.getMetadata().getVersion().getFriendlyString());mods.add(value);
            });root.add("mods",mods);
            var packs=new JsonArray();client.getResourcePackManager().getEnabledProfiles().forEach(pack->packs.add(pack.getId()));root.add("resourcePacks",packs);
            var settings=new JsonObject();settings.addProperty("graphics",client.options.getGraphicsMode().getValue().name());
            settings.addProperty("viewDistance",client.options.getViewDistance().getValue());settings.addProperty("fov",client.options.getFov().getValue());
            settings.addProperty("gamma",client.options.getGamma().getValue());root.add("renderer",settings);
            var clientContext=player(client);var center=client.crosshairTarget instanceof BlockHitResult hit?hit.getBlockPos():client.player==null?BlockPos.ORIGIN:client.player.getBlockPos();
            var clientWorld=client.world;
            if(clientWorld!=null)clientContext=combine(clientContext,DiagnosticWorld.snapshot(clientWorld,center));
            root.add("client",clientContext);
            try {image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer());}
            catch(RuntimeException failed) {limitations.add("Screenshot unavailable: "+failed.getClass().getSimpleName());}
            var server=client.getServer();var serverContext=new CompletableFuture<JsonObject>();
            if(server==null || clientWorld==null)serverContext.complete(null);
            else {
                var key=clientWorld.getRegistryKey();var position=center.toImmutable();
                server.execute(()-> {if(serverContext.isDone())return;try {var world=server.getWorld(key);serverContext.complete(world==null?null:DiagnosticWorld.snapshot(world,position));}
                    catch(RuntimeException failed) {serverContext.completeExceptionally(failed);}});
            }
            var location=client.runDirectory.toPath().resolve("logs/slabbed-debug");var captured=image;image=null;
            message(client,"Saving Slabbed report locally. Review its screenshot before sharing.");
            serverContext.completeOnTimeout(null,5,TimeUnit.SECONDS).handleAsync((context,failure)-> {
                try {
                    if(context!=null) {root.add("server",context);root.addProperty("coverage","integrated_server_and_client");}
                    else {root.addProperty("coverage","client_only");limitations.add(server==null?"Remote server decisions are unavailable.":"Integrated server snapshot unavailable or timed out.");}
                    byte[] png=null;
                    if(captured!=null)try {png=captured.getBytes();}catch(Exception failed) {limitations.add("Screenshot encoding unavailable.");}
                    var path=DiagnosticArchive.write(location,events(snapshot),JSON.toJson(root),png);
                    client.execute(()-> {if(client.player!=null)client.player.sendMessage(Text.literal("Slabbed report saved: ").append(Text.literal(path.getFileName().toString())
                            .formatted(Formatting.UNDERLINE,Formatting.GREEN).styled(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE,path.getParent().toAbsolutePath().toString())))),false);});
                } catch(Exception failed) {client.execute(()->message(client,"Slabbed report could not be saved: "+failed.getClass().getSimpleName()));}
                finally {if(captured!=null)captured.close();REPORTING.set(false);}
                return null;
            },WORKER);
        } catch(RuntimeException failed) {
            if(image!=null)image.close();REPORTING.set(false);message(client,"Slabbed report capture failed: "+failed.getClass().getSimpleName());
        }
    }
    private static String held(net.minecraft.item.ItemStack stack) {return net.minecraft.registry.Registries.ITEM.getId(stack.getItem())+" count="+stack.getCount();}
    private static JsonObject combine(JsonObject first,JsonObject second) {second.entrySet().forEach(entry->first.add(entry.getKey(),entry.getValue()));return first;}
    private static void message(MinecraftClient client,String message) {if(client.player!=null)client.player.sendMessage(Text.literal(message),false);}
}
