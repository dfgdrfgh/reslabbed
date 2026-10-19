package com.slabbed.debug;

import java.util.ArrayDeque;
import java.util.List;

/** Optional bounded history; observing an action never changes placement facts (LAW.md). */
public final class DiagnosticRecorder {
    private static final int CAPACITY=4096,MAX_TEXT=4096;
    private static final ArrayDeque<Event> EVENTS=new ArrayDeque<>();
    private static volatile boolean active;
    private static long sequence,dropped;
    private DiagnosticRecorder() {}
    public record Event(long sequence,long millis,String side,String kind,String details) {}
    public record Snapshot(List<Event> events,long dropped,boolean active) {
        public Snapshot {events=List.copyOf(events);}
    }
    public static boolean isActive() {return active;}
    public static synchronized void start() {EVENTS.clear();sequence=0;dropped=0;active=true;}
    public static synchronized void stop() {active=false;}
    public static void record(String side,String kind,String details) {
        if(!active)return;
        append(side,kind,details);
    }
    private static synchronized void append(String side,String kind,String details) {
        if(!active)return;
        if(EVENTS.size()==CAPACITY) {EVENTS.removeFirst();dropped++;}
        EVENTS.addLast(new Event(++sequence,System.currentTimeMillis(),limit(side),limit(kind),limit(details)));
    }
    private static String limit(String text) {return text==null?"":text.substring(0,Math.min(text.length(),MAX_TEXT));}
    public static synchronized Snapshot snapshot() {return new Snapshot(List.copyOf(EVENTS),dropped,active);}
}
