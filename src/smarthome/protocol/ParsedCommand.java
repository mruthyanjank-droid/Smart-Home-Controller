package smarthome.protocol;

/**
 * ParsedCommand encapsulates the structured components of a user or system command
 * after grammatical validation by CommandParser.
 */
public class ParsedCommand {
    public enum Type {
        SERVER_LIST,
        SERVER_STATUS_ALL,
        SERVER_HELP,
        SERVER_QUIT,
        SERVER_SCHEDULE,
        DEVICE_COMMAND,
        REGISTER_DEVICE,
        REGISTER_USER,
        INVALID
    }

    private final Type type;
    private final String targetDevice;
    private final String action;
    private final String argument;
    private final String rawCommand;
    private final String errorMessage;

    public ParsedCommand(Type type, String targetDevice, String action, String argument, String rawCommand, String errorMessage) {
        this.type = type;
        this.targetDevice = targetDevice;
        this.action = action;
        this.argument = argument;
        this.rawCommand = rawCommand;
        this.errorMessage = errorMessage;
    }

    public static ParsedCommand server(Type type, String rawCommand) {
        return new ParsedCommand(type, null, null, null, rawCommand, null);
    }

    public static ParsedCommand device(String targetDevice, String action, String argument, String rawCommand) {
        return new ParsedCommand(Type.DEVICE_COMMAND, targetDevice, action, argument, rawCommand, null);
    }

    public static ParsedCommand register(Type type, String name, String rawCommand) {
        return new ParsedCommand(type, name, null, null, rawCommand, null);
    }

    public static ParsedCommand schedule(String targetDevice, String action, String argument, String rawCommand) {
        return new ParsedCommand(Type.SERVER_SCHEDULE, targetDevice, action, argument, rawCommand, null);
    }

    public static ParsedCommand invalid(String errorMessage, String rawCommand) {
        return new ParsedCommand(Type.INVALID, null, null, null, rawCommand, errorMessage);
    }

    public Type getType() {
        return type;
    }

    public String getTargetDevice() {
        return targetDevice;
    }

    public String getAction() {
        return action;
    }

    public String getArgument() {
        return argument;
    }

    public String getRawCommand() {
        return rawCommand;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isValid() {
        return type != Type.INVALID;
    }

    @Override
    public String toString() {
        return "ParsedCommand{" +
                "type=" + type +
                ", targetDevice='" + targetDevice + '\'' +
                ", action='" + action + '\'' +
                ", argument='" + argument + '\'' +
                ", rawCommand='" + rawCommand + '\'' +
                '}';
    }
}
