package smarthome.device;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

/**
 * =========================================================================
 * OOP CONCEPT: INHERITANCE & POLYMORPHISM
 * =========================================================================
 * Fan extends Device and provides concrete state management and speed control
 * (levels 1-5) for a smart ceiling fan.
 */
public class Fan extends Device {

    // =====================================================================
    // OOP CONCEPT: ENCAPSULATION
    // =====================================================================
    private boolean isOn = false;
    private int speed = 0; // 0 when off, 1-5 when on

    public Fan() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public Fan(String host, int port) {
        super(Protocol.DEVICE_FAN, host, port);
    }

    @Override
    public synchronized String processCommand(String command) {
        String cmd = command.trim().toUpperCase();

        if (cmd.equals(Protocol.CMD_ON)) {
            isOn = true;
            if (speed == 0) {
                speed = 1;
            }
            AppLogger.info(deviceName, "State changed: Fan is now ON (Speed " + speed + ")");
            return Protocol.ok("FAN IS ON (SPEED " + speed + ")");
        } else if (cmd.equals(Protocol.CMD_OFF)) {
            isOn = false;
            speed = 0;
            AppLogger.info(deviceName, "State changed: Fan is now OFF");
            return Protocol.ok("FAN IS OFF");
        } else if (cmd.startsWith(Protocol.CMD_SPEED) || cmd.startsWith(Protocol.CMD_SET)) {
            // Parses "SPEED <val>" or "SET SPEED <val>"
            String[] tokens = cmd.split("\\s+");
            String speedStr = null;
            if (tokens.length == 2 && tokens[0].equals(Protocol.CMD_SPEED)) {
                speedStr = tokens[1];
            } else if (tokens.length == 3 && tokens[0].equals(Protocol.CMD_SET) && tokens[1].equals(Protocol.CMD_SPEED)) {
                speedStr = tokens[2];
            }

            if (speedStr != null) {
                try {
                    int val = Integer.parseInt(speedStr);
                    if (val >= Protocol.FAN_MIN_SPEED && val <= Protocol.FAN_MAX_SPEED) {
                        isOn = true;
                        speed = val;
                        AppLogger.info(deviceName, "Speed adjusted: Fan speed is now " + speed);
                        return Protocol.ok("FAN SPEED SET TO " + speed);
                    } else {
                        return Protocol.error("INVALID FAN SPEED: USE 1-5");
                    }
                } catch (NumberFormatException e) {
                    return Protocol.error("INVALID FAN SPEED FORMAT: MUST BE A NUMBER 1-5");
                }
            }
            return Protocol.error("INVALID SPEED COMMAND FORMAT. USE: FAN SPEED <1-5>");
        } else if (cmd.equals(Protocol.CMD_STATUS)) {
            return getStatus();
        } else {
            return Protocol.error("UNKNOWN COMMAND FOR FAN: " + command);
        }
    }

    @Override
    public synchronized String getStatus() {
        if (isOn) {
            return Protocol.ok("FAN STATUS: ON (SPEED " + speed + ")");
        } else {
            return Protocol.ok("FAN STATUS: OFF");
        }
    }

    public synchronized boolean isOn() {
        return isOn;
    }

    public synchronized int getSpeed() {
        return speed;
    }

    /**
     * Allows Fan to be run as an independent Java process.
     */
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : Protocol.DEFAULT_HOST;
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        AppLogger.info("FAN", "Launching standalone Fan device client...");
        Fan fan = new Fan(host, port);
        fan.setAutoReconnect(true);
        fan.run();
    }
}
