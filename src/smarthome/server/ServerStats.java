package smarthome.server;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ServerStats provides centralized telemetry, command counters, uptime calculation,
 * and circular event log tracking for the Smart Home Command Center.
 */
public class ServerStats {

    public static class EventLog {
        public final String timestamp;
        public final String type;
        public final String source;
        public final String message;
        public final boolean success;

        public EventLog(String type, String source, String message, boolean success) {
            this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            this.type = type;
            this.source = source;
            this.message = message;
            this.success = success;
        }

        public String toJson() {
            return String.format("{\"timestamp\":\"%s\",\"type\":\"%s\",\"source\":\"%s\",\"message\":\"%s\",\"success\":%b}",
                    escape(timestamp), escape(type), escape(source), escape(message), success);
        }
    }

    private static final long startTimeMs = System.currentTimeMillis();
    private static final AtomicLong totalCommands = new AtomicLong(0);
    private static final AtomicLong totalAcknowledged = new AtomicLong(0);
    private static final AtomicLong totalErrors = new AtomicLong(0);
    private static final ConcurrentLinkedDeque<EventLog> events = new ConcurrentLinkedDeque<>();
    private static final int MAX_EVENTS = 100;

    static {
        recordEvent("SYSTEM", "CONTROLLER", "Smart Home Controller C2 Uplink Initialized on port 5000", true);
    }

    public static void recordEvent(String type, String source, String message, boolean success) {
        events.addFirst(new EventLog(type, source, message, success));
        while (events.size() > MAX_EVENTS) {
            events.pollLast();
        }
    }

    public static void recordCommand(boolean success) {
        totalCommands.incrementAndGet();
        if (success) {
            totalAcknowledged.incrementAndGet();
        } else {
            totalErrors.incrementAndGet();
        }
    }

    public static long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTimeMs) / 1000;
    }

    public static long getTotalCommands() {
        return totalCommands.get();
    }

    public static long getTotalAcknowledged() {
        return totalAcknowledged.get();
    }

    public static long getTotalErrors() {
        return totalErrors.get();
    }

    public static List<EventLog> getRecentEvents(int limit) {
        List<EventLog> list = new ArrayList<>(events);
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public static String getEventsJson(int limit) {
        List<EventLog> list = getRecentEvents(limit);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).toJson());
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
