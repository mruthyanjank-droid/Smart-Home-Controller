package smarthome.device;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

/**
 * =========================================================================
 * OOP CONCEPT: INHERITANCE & POLYMORPHISM
 * =========================================================================
 * AC extends Device and provides concrete state management and temperature
 * control (16°C to 30°C) for a smart air conditioner.
 */
public class AC extends Device {

    // =====================================================================
    // OOP CONCEPT: ENCAPSULATION
    // =====================================================================
    private boolean isOn = false;
    private int temperature = 24; // Default comfortable room temperature

    public AC() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public AC(String host, int port) {
        super(Protocol.DEVICE_AC, host, port);
    }

    @Override
    public synchronized String processCommand(String command) {
        String cmd = command.trim().toUpperCase();

        if (cmd.equals(Protocol.CMD_ON)) {
            isOn = true;
            AppLogger.info(deviceName, "State changed: AC is now ON at " + temperature + "°C");
            return Protocol.ok("AC IS ON (" + temperature + "°C)");
        } else if (cmd.equals(Protocol.CMD_OFF)) {
            isOn = false;
            AppLogger.info(deviceName, "State changed: AC is now OFF");
            return Protocol.ok("AC IS OFF");
        } else if (cmd.startsWith(Protocol.CMD_SET)) {
            String[] tokens = cmd.split("\\s+");
            if (tokens.length >= 2) {
                try {
                    int targetTemp = Integer.parseInt(tokens[1]);
                    if (targetTemp >= Protocol.AC_MIN_TEMP && targetTemp <= Protocol.AC_MAX_TEMP) {
                        this.temperature = targetTemp;
                        AppLogger.info(deviceName, "Temperature adjusted: AC set to " + temperature + "°C");
                        return Protocol.ok("AC TEMPERATURE SET TO " + temperature + "°C");
                    } else {
                        return Protocol.error("INVALID TEMPERATURE: USE 16-30");
                    }
                } catch (NumberFormatException e) {
                    return Protocol.error("INVALID TEMPERATURE FORMAT: MUST BE A NUMBER 16-30");
                }
            }
            return Protocol.error("MISSING TEMPERATURE VALUE. USE: AC SET <16-30>");
        } else if (cmd.equals(Protocol.CMD_STATUS)) {
            return getStatus();
        } else {
            return Protocol.error("UNKNOWN COMMAND FOR AC: " + command);
        }
    }

    @Override
    public synchronized String getStatus() {
        if (isOn) {
            return Protocol.ok("AC STATUS: ON (" + temperature + "°C)");
        } else {
            return Protocol.ok("AC STATUS: OFF (" + temperature + "°C)");
        }
    }

    public synchronized boolean isOn() {
        return isOn;
    }

    public synchronized int getTemperature() {
        return temperature;
    }

    /**
     * Allows AC to be run as an independent Java process.
     */
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : Protocol.DEFAULT_HOST;
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        AppLogger.info("AC", "Launching standalone AC device client...");
        AC ac = new AC(host, port);
        ac.setAutoReconnect(true);
        ac.run();
    }
}
