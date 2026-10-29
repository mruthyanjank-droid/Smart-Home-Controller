package smarthome.device;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

/**
 * =========================================================================
 * OOP CONCEPT: INHERITANCE & POLYMORPHISM
 * =========================================================================
 * Light extends Device and provides concrete state management and command
 * execution for a smart light bulb.
 */
public class Light extends Device {

    // =====================================================================
    // OOP CONCEPT: ENCAPSULATION
    // Internal light state is private and only modified through controlled logic.
    // =====================================================================
    private boolean isOn = false;

    public Light() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public Light(String host, int port) {
        super(Protocol.DEVICE_LIGHT, host, port);
    }

    @Override
    public synchronized String processCommand(String command) {
        String cmd = command.trim().toUpperCase();

        if (cmd.equals(Protocol.CMD_ON)) {
            isOn = true;
            AppLogger.info(deviceName, "State changed: Light is now ON");
            return Protocol.ok("LIGHT IS ON");
        } else if (cmd.equals(Protocol.CMD_OFF)) {
            isOn = false;
            AppLogger.info(deviceName, "State changed: Light is now OFF");
            return Protocol.ok("LIGHT IS OFF");
        } else if (cmd.equals(Protocol.CMD_STATUS)) {
            return getStatus();
        } else {
            return Protocol.error("UNKNOWN COMMAND FOR LIGHT: " + command);
        }
    }

    @Override
    public synchronized String getStatus() {
        return Protocol.ok("LIGHT STATUS: " + (isOn ? "ON" : "OFF"));
    }

    public synchronized boolean isOn() {
        return isOn;
    }

    /**
     * Allows Light to be run as an independent Java process.
     */
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : Protocol.DEFAULT_HOST;
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        AppLogger.info("LIGHT", "Launching standalone Light device client...");
        Light light = new Light(host, port);
        light.setAutoReconnect(true);
        light.run(); // Runs on main thread for standalone process
    }
}
