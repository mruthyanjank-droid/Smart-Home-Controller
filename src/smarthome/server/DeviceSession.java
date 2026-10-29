package smarthome.server;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DeviceSession manages the TCP connection lifecycle, message correlation,
 * and command routing for an individual connected smart device.
 *
 * It uses an asynchronous request-response correlation mechanism (REQ-ID -> RES-ID)
 * with CompletableFuture so that multiple user requests never conflict on socket I/O.
 */
public class DeviceSession implements Runnable {

    private final String deviceName;
    private final Socket socket;
    private final DeviceRegistry registry;
    private final BufferedReader in;
    private final PrintWriter out;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    private volatile boolean active = true;
    private Thread listenerThread;

    public DeviceSession(String deviceName, Socket socket, DeviceRegistry registry) throws IOException {
        this.deviceName = deviceName.toUpperCase();
        this.socket = socket;
        this.registry = registry;
        this.socket.setTcpNoDelay(true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void startListener() {
        listenerThread = new Thread(this, "DeviceSessionListener-" + deviceName);
        listenerThread.start();
    }

    /**
     * Dedicated reader loop: only THIS thread ever reads from the device's socket.
     */
    @Override
    public void run() {
        AppLogger.info("SERVER", "Device session reader started for " + deviceName);
        try {
            String line;
            while (active && (line = in.readLine()) != null) {
                handleIncomingDeviceMessage(line.trim());
            }
        } catch (IOException e) {
            if (active) {
                AppLogger.warn("SERVER", "Device session I/O error for " + deviceName + ": " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Correlates incoming device responses back to the waiting user thread.
     */
    private void handleIncomingDeviceMessage(String message) {
        AppLogger.debug("SERVER", "Received from device " + deviceName + ": " + message);

        if (message.startsWith("RES-")) {
            int spaceIdx = message.indexOf(' ');
            if (spaceIdx > 0) {
                String reqId = message.substring(4, spaceIdx); // Extract ID after RES-
                String payload = message.substring(spaceIdx + 1).trim();
                CompletableFuture<String> future = pendingRequests.remove(reqId);
                if (future != null) {
                    future.complete(payload);
                    return;
                }
            }
        }

        // If message is un-correlated or single pending request
        if (!pendingRequests.isEmpty()) {
            String firstKey = pendingRequests.keySet().iterator().next();
            CompletableFuture<String> future = pendingRequests.remove(firstKey);
            if (future != null) {
                future.complete(message);
            }
        }
    }

    /**
     * Dispatches a command to the physical or simulated device and waits for response.
     * Thread-safe and non-blocking for other devices.
     *
     * @param command Command string to execute on the device.
     * @return Response string from the device.
     */
    public String executeCommand(String command) {
        if (!active || socket.isClosed()) {
            return Protocol.error("DEVICE NOT CONNECTED: " + deviceName);
        }

        String reqId = String.valueOf(requestIdCounter.getAndIncrement());
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(reqId, future);

        String wireMessage = "REQ-" + reqId + " " + command;
        AppLogger.debug("SERVER", "Sending to device " + deviceName + ": " + wireMessage);

        synchronized (out) {
            out.println(wireMessage);
            if (out.checkError()) {
                pendingRequests.remove(reqId);
                close();
                return Protocol.error("DEVICE CONNECTION FAILED: " + deviceName);
            }
        }

        try {
            // Wait up to 5 seconds for device acknowledgement
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            pendingRequests.remove(reqId);
            AppLogger.error("SERVER", "Timeout waiting for response from " + deviceName);
            return Protocol.error("DEVICE TIMEOUT: " + deviceName + " DID NOT RESPOND");
        } catch (Exception e) {
            pendingRequests.remove(reqId);
            AppLogger.error("SERVER", "Error dispatching command to " + deviceName + ": " + e.getMessage());
            return Protocol.error("COMMUNICATION ERROR WITH " + deviceName);
        }
    }

    /**
     * Cleanly closes the device session and frees all pending requests.
     */
    public synchronized void close() {
        if (!active) {
            return;
        }
        active = false;
        AppLogger.info("SERVER", "Closing device session for " + deviceName);

        // Cancel all pending futures with error
        for (CompletableFuture<String> future : pendingRequests.values()) {
            future.complete(Protocol.error("DEVICE DISCONNECTED: " + deviceName));
        }
        pendingRequests.clear();

        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        registry.unregisterDevice(deviceName, this);
    }

    public boolean isActive() {
        return active && !socket.isClosed();
    }

    public String getDeviceName() {
        return deviceName;
    }
}
