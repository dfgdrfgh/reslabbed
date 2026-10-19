package com.slabbed.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local evidence only; writes publish completed files and retain at most ten reports. */
public final class DiagnosticArchive {
    private DiagnosticArchive() {}
    public static void checkpoint(Path directory,String events) throws IOException {
        Files.createDirectories(directory);
        var temporary=Files.createTempFile(directory,"checkpoint-",".tmp");
        try {Files.writeString(temporary,events,StandardCharsets.UTF_8);publish(temporary,directory.resolve("last-session.jsonl"));}
        finally {Files.deleteIfExists(temporary);}
    }
    public static Path write(Path directory,String events,String context,byte[] screenshot) throws IOException {
        Files.createDirectories(directory);
        var target=directory.resolve("slabbed-report-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".zip");
        var temporary=Files.createTempFile(directory,"report-",".tmp");
        try {
            try(var output=new ZipOutputStream(Files.newOutputStream(temporary),StandardCharsets.UTF_8)) {
                entry(output,"events.jsonl",events.getBytes(StandardCharsets.UTF_8));
                entry(output,"context.json",context.getBytes(StandardCharsets.UTF_8));
                if(screenshot!=null)entry(output,"screenshot.png",screenshot);
            }
            publish(temporary,target);
        } finally {Files.deleteIfExists(temporary);}
        // Cleanup is best effort: a completed report remains usable if older files are locked.
        try(var files=Files.list(directory)) {
            var reports=files.filter(p->p.getFileName().toString().matches("slabbed-report-[0-9]+-[a-f0-9-]+\\.zip"))
                    .sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).toList();
            for(int i=10;i<reports.size();i++)try {Files.deleteIfExists(reports.get(i));}catch(IOException ignored) {}
        }catch(IOException ignored) {}
        return target;
    }
    private static void entry(ZipOutputStream output,String name,byte[] bytes) throws IOException {
        output.putNextEntry(new ZipEntry(name));output.write(bytes);output.closeEntry();
    }
    private static void publish(Path source,Path target) throws IOException {
        try {Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported) {Files.move(source,target,StandardCopyOption.REPLACE_EXISTING);}
    }
}
