package smarthome.protocol;

/**
 * CommandParser parses and validates incoming text lines received over TCP sockets.
 * It provides strict checking for command syntax and parameter ranges before routing.
 */
public class CommandParser {

    /**
     * Parses a raw line of text into a structured ParsedCommand.
     *
     * @param line Raw input string received from socket or terminal.
     * @return ParsedCommand containing parsing result or validation error.
     */
    public static ParsedCommand parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return ParsedCommand.invalid("EMPTY COMMAND", "");
        }

        String trimmed = line.trim();
        String[] tokens = trimmed.split("\\s+");
        String firstToken = tokens[0].toUpperCase();

        // 1. Device and User Registration
        if (firstToken.equals(Protocol.CMD_REGISTER)) {
            if (tokens.length < 2) {
                return ParsedCommand.invalid("MISSING REGISTRATION IDENTIFIER. USE: REGISTER <LIGHT|FAN|AC|SMARTLOCK|USER>", trimmed);
            }
            String roleOrDevice = tokens[1].toUpperCase();
            if (roleOrDevice.equals(Protocol.ROLE_USER)) {
                return ParsedCommand.register(ParsedCommand.Type.REGISTER_USER, Protocol.ROLE_USER, trimmed);
            } else if (isValidDeviceName(roleOrDevice)) {
                return ParsedCommand.register(ParsedCommand.Type.REGISTER_DEVICE, roleOrDevice, trimmed);
            } else {
                return ParsedCommand.invalid("UNKNOWN DEVICE TYPE: " + roleOrDevice + ". ALLOWED: LIGHT, FAN, AC, SMARTLOCK", trimmed);
            }
        }

        // 2. Direct Server Commands
        if (firstToken.equals(Protocol.CMD_LIST) || firstToken.equals("DEVICES")) {
            return ParsedCommand.server(ParsedCommand.Type.SERVER_LIST, trimmed);
        }

        if (trimmed.equalsIgnoreCase(Protocol.CMD_STATUS_ALL)) {
            return ParsedCommand.server(ParsedCommand.Type.SERVER_STATUS_ALL, trimmed);
        }

        if (firstToken.equals(Protocol.CMD_HELP)) {
            return ParsedCommand.server(ParsedCommand.Type.SERVER_HELP, trimmed);
        }

        if (firstToken.equals(Protocol.CMD_QUIT) || firstToken.equals(Protocol.CMD_EXIT)) {
            return ParsedCommand.server(ParsedCommand.Type.SERVER_QUIT, trimmed);
        }

        // 3. Scheduled Action Command: SCHEDULE <DEVICE> <ACTION> <SECONDS>
        if (firstToken.equals(Protocol.CMD_SCHEDULE)) {
            if (tokens.length < 4) {
                return ParsedCommand.invalid("INVALID SCHEDULE FORMAT. USE: SCHEDULE <DEVICE> <ACTION> <SECONDS>", trimmed);
            }
            String targetDevice = tokens[1].toUpperCase();
            String action = tokens[2].toUpperCase();
            String secondsStr = tokens[3];

            if (!isValidDeviceName(targetDevice)) {
                return ParsedCommand.invalid("UNKNOWN TARGET DEVICE: " + targetDevice, trimmed);
            }

            try {
                int seconds = Integer.parseInt(secondsStr);
                if (seconds <= 0 || seconds > 86400) {
                    return ParsedCommand.invalid("SCHEDULE DELAY MUST BE BETWEEN 1 AND 86400 SECONDS", trimmed);
                }
            } catch (NumberFormatException e) {
                return ParsedCommand.invalid("INVALID SCHEDULE DELAY: MUST BE AN INTEGER", trimmed);
            }

            return ParsedCommand.schedule(targetDevice, action, secondsStr, trimmed);
        }

        // 4. Device Targeted Commands
        if (isValidDeviceName(firstToken)) {
            String targetDevice = firstToken;
            if (tokens.length < 2) {
                return ParsedCommand.invalid("MISSING ACTION FOR DEVICE " + targetDevice, trimmed);
            }

            String action = tokens[1].toUpperCase();

            // Device specific validations
            switch (targetDevice) {
                case Protocol.DEVICE_LIGHT:
                    if (action.equals(Protocol.CMD_ON) || action.equals(Protocol.CMD_OFF) || action.equals(Protocol.CMD_STATUS)) {
                        return ParsedCommand.device(targetDevice, action, null, trimmed);
                    }
                    return ParsedCommand.invalid("UNKNOWN LIGHT ACTION: " + action + ". USE ON, OFF, OR STATUS", trimmed);

                case Protocol.DEVICE_FAN:
                    if (action.equals(Protocol.CMD_ON) || action.equals(Protocol.CMD_OFF) || action.equals(Protocol.CMD_STATUS)) {
                        return ParsedCommand.device(targetDevice, action, null, trimmed);
                    }
                    if (action.equals(Protocol.CMD_SPEED) || action.equals(Protocol.CMD_SET)) {
                        // Support "FAN SPEED <num>" or "FAN SET SPEED <num>"
                        String speedToken = null;
                        if (action.equals(Protocol.CMD_SPEED) && tokens.length >= 3) {
                            speedToken = tokens[2];
                        } else if (action.equals(Protocol.CMD_SET) && tokens.length >= 4 && tokens[2].equalsIgnoreCase("SPEED")) {
                            speedToken = tokens[3];
                        } else if (action.equals(Protocol.CMD_SET) && tokens.length >= 3) {
                            speedToken = tokens[2];
                        }

                        if (speedToken == null) {
                            return ParsedCommand.invalid("MISSING FAN SPEED. USE: FAN SPEED <1-5>", trimmed);
                        }

                        try {
                            int speed = Integer.parseInt(speedToken);
                            if (speed < Protocol.FAN_MIN_SPEED || speed > Protocol.FAN_MAX_SPEED) {
                                return ParsedCommand.invalid("INVALID FAN SPEED: USE 1-5", trimmed);
                            }
                            return ParsedCommand.device(targetDevice, Protocol.CMD_SPEED, String.valueOf(speed), trimmed);
                        } catch (NumberFormatException e) {
                            return ParsedCommand.invalid("INVALID FAN SPEED: MUST BE A NUMBER 1-5", trimmed);
                        }
                    }
                    return ParsedCommand.invalid("UNKNOWN FAN ACTION: " + action + ". USE ON, OFF, SPEED <1-5>, OR STATUS", trimmed);

                case Protocol.DEVICE_AC:
                    if (action.equals(Protocol.CMD_ON) || action.equals(Protocol.CMD_OFF) || action.equals(Protocol.CMD_STATUS)) {
                        return ParsedCommand.device(targetDevice, action, null, trimmed);
                    }
                    if (action.equals(Protocol.CMD_SET)) {
                        if (tokens.length < 3) {
                            return ParsedCommand.invalid("MISSING TEMPERATURE. USE: AC SET <16-30>", trimmed);
                        }
                        try {
                            int temp = Integer.parseInt(tokens[2]);
                            if (temp < Protocol.AC_MIN_TEMP || temp > Protocol.AC_MAX_TEMP) {
                                return ParsedCommand.invalid("INVALID TEMPERATURE: USE 16-30", trimmed);
                            }
                            return ParsedCommand.device(targetDevice, Protocol.CMD_SET, String.valueOf(temp), trimmed);
                        } catch (NumberFormatException e) {
                            return ParsedCommand.invalid("INVALID TEMPERATURE: MUST BE A NUMBER 16-30", trimmed);
                        }
                    }
                    return ParsedCommand.invalid("UNKNOWN AC ACTION: " + action + ". USE ON, OFF, SET <16-30>, OR STATUS", trimmed);

                case Protocol.DEVICE_SMARTLOCK:
                    if (action.equals(Protocol.CMD_LOCK) || action.equals(Protocol.CMD_UNLOCK) || action.equals(Protocol.CMD_STATUS)) {
                        return ParsedCommand.device(targetDevice, action, null, trimmed);
                    }
                    return ParsedCommand.invalid("UNKNOWN SMARTLOCK ACTION: " + action + ". USE LOCK, UNLOCK, OR STATUS", trimmed);

                default:
                    return ParsedCommand.invalid("UNSUPPORTED DEVICE: " + targetDevice, trimmed);
            }
        }

        // Unrecognized format
        return ParsedCommand.invalid("UNKNOWN COMMAND: '" + trimmed + "'. TYPE 'HELP' FOR USAGE.", trimmed);
    }

    public static boolean isValidDeviceName(String name) {
        if (name == null) return false;
        String upper = name.toUpperCase();
        return upper.equals(Protocol.DEVICE_LIGHT) ||
               upper.equals(Protocol.DEVICE_FAN) ||
               upper.equals(Protocol.DEVICE_AC) ||
               upper.equals(Protocol.DEVICE_SMARTLOCK);
    }
}
