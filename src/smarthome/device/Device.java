package smarthome.device;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * =========================================================================
 * OOP CONCEPT: ABSTRACTION
 * =========================================================================
 * Device is an abstract base class that encapsulates all shared networking,
 * connection lifecycle, registration, and communication handling for all
 * simulated smart home devices.
 *
 * Specific devices (Light, Fan, AC, SmartLock) extend this class and supply
 * concrete implementations of device-specific command processing and state management.
 */
public abstract class Device implements Runnable {

    // =====================================================================
    // OOP CONCEPT: ENCAPSULATION
    // Device identity, socket references, and state flags are protected or
    // private with controlled access methods.
    // =====================================================================
    protected final String deviceName;
    protected final String host;
    protected final int port;
    protected volatile boolean running = false;
    protected volatile boolean connected = false;
    protected boolean autoReconnect = false;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread workerThread;

    /**
     * Constructs a device with a given name, target host, and port.
     *
     * @param deviceName Unique device name (e.g. LIGHT, FAN, AC, SMARTLOCK).
     * @param host       Server IP or hostname.
     * @param port       Server listening port.
     */
    public Device(String deviceName, String host, int port) {
        this.deviceName = deviceName.toUpperCase();
        this.host = host;
        this.port = port;
    }

    /**
     * =====================================================================
     * OOP CONCEPT: POLYMORPHISM
     * Subclasses must provide device-specific command execution logic.
     * =====================================================================
     *
     * @param command The specific command received from the controller.
     * @return Formatted response string acknowledging execution or reporting error.
     */
    public abstract String processCommand(String command);

    /**
     * Returns the current operational status of the device.
     *
     * @return Human-readable status string.
     */
    public abstract String getStatus();

    /**
     * Starts the device client asynchronously on a dedicated thread.
     */
    public synchronized void start() {
        if (running) {
            AppLogger.warn(deviceName, "Device is already running.");
            return;
        }
        running = true;
        workerThread = new Thread(this, "DeviceThread-" + deviceName);
        workerThread.start();
    }

    /**
     * Main execution loop running in the device's worker thread.
     */
    @Override
    public void run() {
        AppLogger.info(deviceName, "Device client initialized for " + host + ":" + port);

        while (running) {
            try {
                connectAndListen();
            } catch (Exception e) {
                if (running) {
                    AppLogger.warn(deviceName, "Connection lost: " + e.getMessage());
                }
            }

            if (running && autoReconnect) {
                AppLogger.info(deviceName, "Attempting reconnection in 3 seconds...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                break;
            }
        }

        disconnect();
        AppLogger.info(deviceName, "Device client stopped.");
    }

    /**
     * Connects to the controller server, sends registration, and handles incoming commands.
     */
    private void connectAndListen() throws IOException {
        AppLogger.info(deviceName, "Connecting to controller at " + host + ":" + port + "...");
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Step 1: Send registration handshake
        String regMessage = Protocol.CMD_REGISTER + " " + deviceName;
        AppLogger.info(deviceName, "Sending registration: " + regMessage);
        out.println(regMessage);

        // Step 2: Read server acknowledgement
        String ack = in.readLine();
        if (ack == null || !ack.startsWith(Protocol.RESP_OK)) {
            throw new IOException("Server rejected registration: " + ack);
        }

        connected = true;
        AppLogger.info(deviceName, "Successfully registered with controller: " + ack);

        // Step 3: Listen for incoming commands
        String incoming;
        while (running && (incoming = in.readLine()) != null) {
            AppLogger.debug(deviceName, "Received command: " + incoming);

            String correlationId = null;
            String actualCommand = incoming.trim();

            // Check if server included a request ID for correlation: "REQ-<id> <COMMAND>"
            if (actualCommand.startsWith("REQ-")) {
                int firstSpace = actualCommand.indexOf(' ');
                if (firstSpace > 0) {
                    correlationId = actualCommand.substring(0, firstSpace);
                    actualCommand = actualCommand.substring(firstSpace + 1).trim();
                }
            }

            // If the command starts with the device's own name, strip it (e.g. "LIGHT ON" -> "ON")
            if (actualCommand.toUpperCase().startsWith(deviceName + " ")) {
                actualCommand = actualCommand.substring(deviceName.length()).trim();
            }

            // Execute command polymorphically
            String response;
            if (actualCommand.equalsIgnoreCase(Protocol.CMD_STATUS)) {
                response = getStatus();
            } else {
                response = processCommand(actualCommand);
            }

            // Prepend correlation ID if one was supplied
            String outgoing = (correlationId != null) ? correlationId.replace("REQ-", "RES-") + " " + response : response;
            AppLogger.debug(deviceName, "Sending response: " + outgoing);
            out.println(outgoing);
        }
    }

    /**
     * Cleanly shuts down the socket and streams.
     */
    public synchronized void stop() {
        running = false;
        disconnect();
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }
    }

    private synchronized void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
}
