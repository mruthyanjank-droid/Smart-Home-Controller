package smarthome.protocol;

/**
 * Protocol defines the standardized constants, command keywords, and response formats
 * for line-based TCP communication between the Controller Server, Devices, and User Clients.
 */
public class Protocol {

    // Default Networking Configuration
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5000;
    public static final int SOCKET_TIMEOUT_MS = 8000;

    // Registration Keywords
    public static final String CMD_REGISTER = "REGISTER";
    public static final String ROLE_USER = "USER";

    // Standard Device Names
    public static final String DEVICE_LIGHT = "LIGHT";
    public static final String DEVICE_FAN = "FAN";
    public static final String DEVICE_AC = "AC";
    public static final String DEVICE_SMARTLOCK = "SMARTLOCK";

    // Common Commands
    public static final String CMD_ON = "ON";
    public static final String CMD_OFF = "OFF";
    public static final String CMD_STATUS = "STATUS";
    public static final String CMD_SET = "SET";
    public static final String CMD_SPEED = "SPEED";
    public static final String CMD_LOCK = "LOCK";
    public static final String CMD_UNLOCK = "UNLOCK";

    // Server/Controller Direct Commands
    public static final String CMD_LIST = "LIST";
    public static final String CMD_STATUS_ALL = "STATUS ALL";
    public static final String CMD_HELP = "HELP";
    public static final String CMD_QUIT = "QUIT";
    public static final String CMD_EXIT = "EXIT";
    public static final String CMD_SCHEDULE = "SCHEDULE";

    // Response Status Prefixes
    public static final String RESP_OK = "OK";
    public static final String RESP_ERROR = "ERROR";

    // Device Parameter Boundaries
    public static final int AC_MIN_TEMP = 16;
    public static final int AC_MAX_TEMP = 30;
    public static final int FAN_MIN_SPEED = 1;
    public static final int FAN_MAX_SPEED = 5;

    // Helper methods for formatting standard responses
    public static String ok(String message) {
        return RESP_OK + " " + message;
    }

    public static String error(String message) {
        return RESP_ERROR + " " + message;
    }
}
