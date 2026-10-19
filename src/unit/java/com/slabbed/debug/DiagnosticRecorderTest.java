package com.slabbed.debug;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Recorder limits and archive behavior without a running game. */
public final class DiagnosticRecorderTest {
    private static Class<?> recorder,archive;
    private static int checks;
    public static void main(String[] args) throws Exception {
        try {recorder=Class.forName("com.slabbed.debug.DiagnosticRecorder");}
        catch(ClassNotFoundException missing) {throw new AssertionError("optional gameplay recorder is missing",missing);}
        archive=Class.forName("com.slabbed.debug.DiagnosticArchive");
        call("stop");event("disabled");check(events().isEmpty(),"off by default");
        call("start");event("first");var old=events();
        check(old.size()==1,"enabled capture");
        call("stop");event("ignored");check(events().size()==1,"stopping preserves history without capturing");
        call("start");check(events().isEmpty(),"new session clears history");
        for(int i=0;i<4200;i++)event("event-"+i);
        check(events().size()==4096,"bounded memory under spam");
        check(detail(events().get(0)).equals("event-104"),"oldest events evicted first");
        check(((Number)snapshot().getClass().getMethod("dropped").invoke(snapshot())).longValue()==104,"evictions are reported");
        check(old.size()==1 && detail(old.get(0)).equals("first"),"snapshots immutable across starts");
        event("x".repeat(9000));check(detail(events().get(4095)).length()==4096,"per-event details bounded");
        try {events().clear();throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected) {checks++;}
        call("start");var threads=new ArrayList<Thread>();
        for(int n=0;n<4;n++) {var thread=new Thread(()->{for(int j=0;j<1000;j++)try{event("parallel");}catch(Exception e){throw new RuntimeException(e);}});threads.add(thread);thread.start();}
        for(var thread:threads)thread.join();
        var captured=events();check(captured.size()==4000,"concurrent client/server events retained");
        long previous=0;
        for(var value:captured) {long sequence=((Number)value.getClass().getMethod("sequence").invoke(value)).longValue();check(sequence>previous,"ordered unique sequence");previous=sequence;}
        call("stop");
        var root=Files.createTempDirectory("slabbed-recorder-test");
        try {
            Path zip=write(root,"{\"kind\":\"block_use\"}\n","{\"coverage\":\"client_only\"}",new byte[]{1,2,3});
            try(var file=new ZipFile(zip.toFile())) {
                check(file.getEntry("events.jsonl")!=null && file.getEntry("context.json")!=null && file.getEntry("screenshot.png")!=null,"shareable archive entries");
                check(new String(file.getInputStream(file.getEntry("context.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("client_only"),"context preserved");
            }
            Path noImage=write(root,"","{}",null);
            try(var file=new ZipFile(noImage.toFile())) {check(file.getEntry("events.jsonl")!=null && file.getEntry("screenshot.png")==null,"missing screenshot retains evidence");}
            for(int i=0;i<11;i++)write(root,"","{}",null);
            try(var entries=Files.list(root)) {check(entries.filter(p->p.toString().endsWith(".zip")).count()==10,"ten report retention limit");}
            archive.getMethod("checkpoint",Path.class,String.class).invoke(null,root,"first");
            archive.getMethod("checkpoint",Path.class,String.class).invoke(null,root,"second");
            check(Files.readString(root.resolve("last-session.jsonl")).equals("second"),"checkpoint atomically replaced");
            Path blocked=root.resolve("not-a-directory");Files.writeString(blocked,"keep");
            try {write(blocked,"","{}",null);throw new AssertionError("disk failure hidden");}
            catch(InvocationTargetException expected) {check(expected.getCause() instanceof java.io.IOException,"disk errors propagated to report owner");}
            check(Files.readString(blocked).equals("keep"),"failed export preserves existing file");
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("DIAGNOSTIC_RECORDER_PASS "+checks+" assertions");
    }
    private static Object call(String name) throws Exception {return recorder.getMethod(name).invoke(null);}
    private static void event(String details) throws Exception {recorder.getMethod("record",String.class,String.class,String.class).invoke(null,"client","action",details);}
    private static Object snapshot() throws Exception {return call("snapshot");}
    @SuppressWarnings("unchecked") private static List<Object> events() throws Exception {var s=snapshot();return (List<Object>)s.getClass().getMethod("events").invoke(s);}
    private static String detail(Object event) throws Exception {return (String)event.getClass().getMethod("details").invoke(event);}
    private static Path write(Path root,String events,String context,byte[] image) throws Exception {return (Path)archive.getMethod("write",Path.class,String.class,String.class,byte[].class).invoke(null,root,events,context,image);}
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
}
