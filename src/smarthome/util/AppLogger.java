package smarthome.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * AppLogger provides unified, timestamped logging for the Smart Home system.
 * It outputs formatted log messages to both the standard console and a persistent log file.
 * Thread-safe for concurrent operations across multiple threads.
 */
public class AppLogger {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logs/smarthome.log";
    private static PrintWriter fileWriter;
    private static boolean initialized = false;

    // Static initializer block to set up the log directory and file
    static {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            fileWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
            initialized = true;
        } catch (IOException e) {
            System.err.println("Failed to initialize file logger: " + e.getMessage());
        }
    }

    public enum Level {
        INFO, WARN, ERROR, DEBUG
    }

    /**
     * Log a message with a specific log level and component tag.
     *
     * @param level     Severity level of the message.
     * @param tag       Component identifier (e.g. SERVER, LIGHT, GUI).
     * @param message   The message body.
     */
    public static synchronized void log(Level level, String tag, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String formatted = String.format("[%s] [%-5s] [%-12s] %s", timestamp, level, tag, message);

        // Output to standard console
        if (level == Level.ERROR) {
            System.err.println(formatted);
        } else {
            System.out.println(formatted);
        }

        // Output to persistent file if available
        if (initialized && fileWriter != null) {
            fileWriter.println(formatted);
        }
    }

    public static void info(String tag, String message) {
        log(Level.INFO, tag, message);
    }

    public static void warn(String tag, String message) {
        log(Level.WARN, tag, message);
    }

    public static void error(String tag, String message) {
        log(Level.ERROR, tag, message);
    }

    public static void debug(String tag, String message) {
        log(Level.DEBUG, tag, message);
    }

    public static void error(String tag, String message, Throwable throwable) {
        log(Level.ERROR, tag, message + " - " + throwable.getMessage());
        if (initialized && fileWriter != null) {
            throwable.printStackTrace(fileWriter);
        }
    }
}
