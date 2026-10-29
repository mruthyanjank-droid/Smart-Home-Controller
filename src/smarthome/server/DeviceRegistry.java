package smarthome.server;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =========================================================================
 * OOP CONCEPT: THREAD-SAFE COLLECTIONS & ENCAPSULATION
 * =========================================================================
 * DeviceRegistry maintains the synchronized registry of all currently active
 * simulated smart home devices. It routes commands to appropriate devices,
 * aggregates full-system status, and cleans up on disconnections.
 */
public class DeviceRegistry {

    private final Map<String, DeviceSession> activeDevices = new ConcurrentHashMap<>();
    private final Map<String, String> lastCommands = new ConcurrentHashMap<>();
    private final Map<String, String> lastResponses = new ConcurrentHashMap<>();
    private final Map<String, String> lastUpdatedTimes = new ConcurrentHashMap<>();

    /**
     * Registers a new device session if no active conflicting registration exists.
     *
     * @param deviceName Unique device name (e.g. LIGHT, FAN, AC, SMARTLOCK).
     * @param session    DeviceSession managing socket communication.
     * @return true if registration succeeded; false if device is already active.
     */
    public synchronized boolean registerDevice(String deviceName, DeviceSession session) {
        String key = deviceName.toUpperCase();
        DeviceSession existing = activeDevices.get(key);

        if (existing != null && existing.isActive()) {
            AppLogger.warn("REGISTRY", "Registration rejected: Device " + key + " is already active.");
            ServerStats.recordEvent("REGISTRATION", key, "Duplicate registration rejected: already active", false);
            return false;
        }

        activeDevices.put(key, session);
        lastCommands.put(key, "REGISTER " + key);
        lastResponses.put(key, "OK REGISTERED " + key);
        lastUpdatedTimes.put(key, new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
        AppLogger.info("REGISTRY", "Successfully registered device: " + key + " (Total active: " + activeDevices.size() + ")");
        ServerStats.recordEvent("REGISTRATION", key, "Device registered successfully via TCP Socket", true);
        return true;
    }

    /**
     * Removes a disconnected device from the registry.
     *
     * @param deviceName Name of the device.
     * @param session    Session instance to remove (to avoid removing newer reconnected sessions).
     */
    public synchronized void unregisterDevice(String deviceName, DeviceSession session) {
        String key = deviceName.toUpperCase();
        DeviceSession current = activeDevices.get(key);

        if (current == session) {
            activeDevices.remove(key);
            lastUpdatedTimes.put(key, new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
            AppLogger.info("REGISTRY", "Device unregistered: " + key + " (Remaining active: " + activeDevices.size() + ")");
            ServerStats.recordEvent("DISCONNECT", key, "Device socket closed; removed from registry", false);
        }
    }

    /**
     * Routes a validated command to the designated device session.
     *
     * @param targetDevice Target device name.
     * @param command      Command string to execute.
     * @return Response returned by the device.
     */
    public String routeCommand(String targetDevice, String command) {
        String key = targetDevice.toUpperCase();
        DeviceSession session = activeDevices.get(key);

        lastCommands.put(key, command);
        String now = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        lastUpdatedTimes.put(key, now);

        if (session == null || !session.isActive()) {
            AppLogger.warn("REGISTRY", "Routing failed: Device " + key + " is not connected.");
            String err = Protocol.error("DEVICE NOT CONNECTED: " + key);
            lastResponses.put(key, err);
            ServerStats.recordCommand(false);
            ServerStats.recordEvent("ROUTING_FAIL", key, command + " -> " + err, false);
            return err;
        }

        String response = session.executeCommand(command);
        lastResponses.put(key, response);
        boolean ok = response.startsWith(Protocol.RESP_OK);
        ServerStats.recordCommand(ok);
        ServerStats.recordEvent(ok ? "COMMAND_ACK" : "COMMAND_ERR", key, command + " -> " + response, ok);
        return response;
    }

    /**
     * Queries all registered devices for their operational status and aggregates the results.
     *
     * @return Aggregated status line for all active devices.
     */
    public String getStatusAll() {
        if (activeDevices.isEmpty()) {
            return Protocol.ok("NO DEVICES CONNECTED");
        }

        List<String> sortedKeys = new ArrayList<>(activeDevices.keySet());
        Collections.sort(sortedKeys);

        StringBuilder sb = new StringBuilder();
        sb.append("STATUS ");

        for (int i = 0; i < sortedKeys.size(); i++) {
            String name = sortedKeys.get(i);
            DeviceSession session = activeDevices.get(name);

            String statusResult = "UNAVAILABLE";
            if (session != null && session.isActive()) {
                String raw = session.executeCommand(Protocol.CMD_STATUS);
                // Strip "OK " prefix for compact status reporting if present
                if (raw.startsWith("OK ")) {
                    statusResult = raw.substring(3);
                } else {
                    statusResult = raw;
                }
                // Strip redundant "DEVICE STATUS: " or "STATUS: "
                if (statusResult.toUpperCase().startsWith(name + " STATUS: ")) {
                    statusResult = statusResult.substring((name + " STATUS: ").length());
                } else if (statusResult.toUpperCase().startsWith("STATUS: ")) {
                    statusResult = statusResult.substring(8);
                }
            }

            sb.append(name).append(": ").append(statusResult);
            if (i < sortedKeys.size() - 1) {
                sb.append(" | ");
            }
        }

        return Protocol.ok(sb.toString());
    }

    /**
     * Returns a sorted list of currently registered device names.
     */
    public List<String> getConnectedDeviceNames() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, DeviceSession> entry : activeDevices.entrySet()) {
            if (entry.getValue().isActive()) {
                list.add(entry.getKey());
            }
        }
        Collections.sort(list);
        return list;
    }

    public boolean isDeviceConnected(String deviceName) {
        DeviceSession session = activeDevices.get(deviceName.toUpperCase());
        return session != null && session.isActive();
    }

    /**
     * Constructs a full JSON snapshot of all 4 devices with real live state queries.
     */
    public String getDevicesTelemetryJson() {
        String[] devKeys = {Protocol.DEVICE_LIGHT, Protocol.DEVICE_FAN, Protocol.DEVICE_AC, Protocol.DEVICE_SMARTLOCK};
        StringBuilder sb = new StringBuilder("{");

        for (int i = 0; i < devKeys.length; i++) {
            String key = devKeys[i];
            DeviceSession session = activeDevices.get(key);
            boolean isConn = (session != null && session.isActive());

            String rawStatus = "OFFLINE";
            boolean isOn = false;
            int speed = 0;
            int temp = 24;
            boolean isLocked = true;

            if (isConn) {
                rawStatus = session.executeCommand(Protocol.CMD_STATUS);
                if (rawStatus.startsWith("OK ")) rawStatus = rawStatus.substring(3);
                String upper = rawStatus.toUpperCase();

                if (key.equals(Protocol.DEVICE_LIGHT)) {
                    isOn = upper.contains("ON");
                } else if (key.equals(Protocol.DEVICE_FAN)) {
                    isOn = upper.contains("ON");
                    if (upper.contains("SPEED")) {
                        try {
                            String spdStr = upper.substring(upper.indexOf("SPEED") + 5).replaceAll("[^0-9]", "");
                            if (!spdStr.isEmpty()) speed = Integer.parseInt(spdStr);
                        } catch (Exception ignored) {}
                    }
                } else if (key.equals(Protocol.DEVICE_AC)) {
                    isOn = upper.contains("ON");
                    if (upper.contains("°C") || upper.contains("C")) {
                        try {
                            String tempStr = upper.replaceAll("[^0-9]", "");
                            if (!tempStr.isEmpty()) temp = Integer.parseInt(tempStr);
                        } catch (Exception ignored) {}
                    }
                } else if (key.equals(Protocol.DEVICE_SMARTLOCK)) {
                    isLocked = upper.contains("LOCKED") && !upper.contains("UNLOCKED");
                }
            }

            String lastCmd = lastCommands.getOrDefault(key, "NONE");
            String lastResp = lastResponses.getOrDefault(key, "NONE");
            String lastTime = lastUpdatedTimes.getOrDefault(key, "--:--:--");

            sb.append(String.format("\"%s\":{\"connected\":%b,\"rawStatus\":\"%s\",\"isOn\":%b,\"speed\":%d,\"temp\":%d,\"isLocked\":%b,\"lastCommand\":\"%s\",\"lastResponse\":\"%s\",\"lastUpdated\":\"%s\"}",
                    key, isConn, ServerStats.escape(rawStatus), isOn, speed, temp, isLocked,
                    ServerStats.escape(lastCmd), ServerStats.escape(lastResp), ServerStats.escape(lastTime)));

            if (i < devKeys.length - 1) sb.append(",");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Disconnects and unregisters all devices during controller shutdown.
     */
    public synchronized void closeAll() {
        AppLogger.info("REGISTRY", "Closing all device sessions...");
        for (DeviceSession session : activeDevices.values()) {
            session.close();
        }
        activeDevices.clear();
    }
}
