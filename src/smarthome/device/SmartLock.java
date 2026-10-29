package smarthome.device;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

/**
 * =========================================================================
 * OOP CONCEPT: INHERITANCE & POLYMORPHISM
 * =========================================================================
 * SmartLock extends Device and manages secure door lock state (LOCKED / UNLOCKED).
 */
public class SmartLock extends Device {

    // =====================================================================
    // OOP CONCEPT: ENCAPSULATION
    // Safe default state: LOCKED
    // =====================================================================
    private boolean isLocked = true;

    public SmartLock() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public SmartLock(String host, int port) {
        super(Protocol.DEVICE_SMARTLOCK, host, port);
    }

    @Override
    public synchronized String processCommand(String command) {
        String cmd = command.trim().toUpperCase();

        if (cmd.equals(Protocol.CMD_LOCK)) {
            isLocked = true;
            AppLogger.info(deviceName, "State changed: Door has been LOCKED");
            return Protocol.ok("SMARTLOCK IS LOCKED");
        } else if (cmd.equals(Protocol.CMD_UNLOCK)) {
            isLocked = false;
            AppLogger.info(deviceName, "State changed: Door has been UNLOCKED");
            return Protocol.ok("SMARTLOCK IS UNLOCKED");
        } else if (cmd.equals(Protocol.CMD_STATUS)) {
            return getStatus();
        } else {
            return Protocol.error("UNKNOWN COMMAND FOR SMARTLOCK: " + command);
        }
    }

    @Override
    public synchronized String getStatus() {
        return Protocol.ok("SMARTLOCK STATUS: " + (isLocked ? "LOCKED" : "UNLOCKED"));
    }

    public synchronized boolean isLocked() {
        return isLocked;
    }

    /**
     * Allows SmartLock to be run as an independent Java process.
     */
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : Protocol.DEFAULT_HOST;
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        AppLogger.info("SMARTLOCK", "Launching standalone SmartLock device client...");
        SmartLock lock = new SmartLock(host, port);
        lock.setAutoReconnect(true);
        lock.run();
    }
}
