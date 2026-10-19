package com.slabbed.test;

import com.google.gson.JsonParser;
import com.slabbed.debug.DiagnosticRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

/** Real default bindings, gameplay events, thread-owned context and local report output. */
public final class DiagnosticClientProbe {
    private static int beginTicks,reportTicks;
    private static boolean armed,recordingStarted,exported,done,finished;
    private static Set<Path> previous=Set.of();
    private DiagnosticClientProbe() {}
    public static boolean begin(MinecraftClient client) {
        if(Boolean.getBoolean("slabbed.controlProbe"))return true;
        if(recordingStarted)return true;
        if(!armed) {
            check(!DiagnosticRecorder.isActive(),"recorder starts off");
            KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F8));armed=true;return false;
        }
        if(!DiagnosticRecorder.isActive()) {check(++beginTicks<50,"F8 enables optional recorder");return false;}
        recordingStarted=true;return true;
    }
    public static boolean finish(MinecraftClient client) throws Exception {
        if(finished || Boolean.getBoolean("slabbed.controlProbe"))return true;
        if(done) {
            if(DiagnosticRecorder.isActive()) {check(++reportTicks<550,"F8 stops recording");return false;}
            finished=true;System.out.println("PHYSICAL_DIAGNOSTIC_REPORT_PASS integrated_server_and_client valid_json png keybindings");return true;
        }
        var directory=client.runDirectory.toPath().resolve("logs/slabbed-debug");
        if(!exported) {
            previous=reports(directory);KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F9));
            KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F9));exported=true;return false;
        }
        check(++reportTicks<500,"F9 report export completes without blocking gameplay");
        var report=reports(directory).stream().filter(p->!previous.contains(p)).findFirst();
        if(report.isEmpty())return false;
        check(reports(directory).stream().filter(p->!previous.contains(p)).count()==1,"only one report in flight");
        try(var zip=new ZipFile(report.get().toFile())) {
            var context=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("context.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(context.get("coverage").getAsString().equals("integrated_server_and_client"),"report includes integrated server coverage");
            check(context.has("client") && context.has("server") && context.has("mods") && context.has("resourcePacks"),"version and both owner-thread contexts");
            check(context.getAsJsonObject("client").getAsJsonArray("blocks").size()==125,"bounded loaded block context");
            var lines=new String(zip.getInputStream(zip.getEntry("events.jsonl")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            boolean server=false,clientAction=false;
            for(var line:lines) {
                var event=JsonParser.parseString(line).getAsJsonObject();
                server|=event.get("side").getAsString().equals("server") && event.get("kind").getAsString().startsWith("piston_");
                clientAction|=event.get("side").getAsString().equals("client") && (event.get("kind").getAsString().equals("block_use") || event.get("kind").getAsString().equals("block_attack"));
            }
            check(server && clientAction,"actual server piston decisions and client action results recorded: server="+server+" action="+clientAction);
            var png=zip.getInputStream(zip.getEntry("screenshot.png")).readNBytes(8);
            check(png.length==8 && png[0]==(byte)137 && png[1]==80 && png[2]==78 && png[3]==71,"real PNG screenshot exported");
        }
        KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F8));done=true;return false;
    }
    private static Set<Path> reports(Path directory) throws Exception {
        if(!Files.isDirectory(directory))return Set.of();
        try(var paths=Files.list(directory)) {return paths.filter(p->p.getFileName().toString().endsWith(".zip")).collect(java.util.stream.Collectors.toSet());}
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
