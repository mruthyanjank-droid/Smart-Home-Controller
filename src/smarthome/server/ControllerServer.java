package smarthome.server;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * =========================================================================
 * CONTROLLER SERVER: CENTRAL HUB
 * =========================================================================
 * ControllerServer acts as the central coordinator for the simulated smart home.
 * It opens a TCP ServerSocket, accepts incoming connections from both devices
 * and user clients, dispatches them to multithreaded handlers, and routes commands.
 */
public class ControllerServer implements Runnable {

    private final int port;
    private final DeviceRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final List<ClientHandler> activeHandlers;

    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread serverThread;

    public ControllerServer() {
        this(Protocol.DEFAULT_PORT);
    }

    public ControllerServer(int port) {
        this.port = port;
        this.registry = new DeviceRegistry();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.activeHandlers = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Starts the controller server asynchronously on a dedicated thread.
     */
    public synchronized void start() {
        if (running) {
            AppLogger.warn("SERVER", "Server is already running on port " + port);
            return;
        }
        running = true;
        serverThread = new Thread(this, "ControllerServerThread");
        serverThread.start();
    }

    @Override
    public void run() {
        running = true;
        AppLogger.info("SERVER", "Starting Smart Home Controller Server on port " + port + "...");

        try {
            serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getByName("0.0.0.0"));
            AppLogger.info("SERVER", "Server successfully bound to 0.0.0.0 and listening on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, registry, scheduler, this);
                    activeHandlers.add(handler);
                    new Thread(handler, "ClientHandler-" + clientSocket.getRemoteSocketAddress()).start();
                } catch (IOException e) {
                    if (running) {
                        AppLogger.error("SERVER", "Error accepting client connection: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            AppLogger.error("SERVER", "Could not start server on port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Stops the server, closes all active connections and releases system resources.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        AppLogger.info("SERVER", "Stopping Smart Home Controller Server...");

        // 1. Close ServerSocket to stop accepting new connections
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        // 2. Close all active client handlers
        synchronized (activeHandlers) {
            for (ClientHandler handler : activeHandlers) {
                handler.close();
            }
            activeHandlers.clear();
        }

        // 3. Close all registered device sessions
        registry.closeAll();

        // 4. Shutdown scheduled tasks executor
        scheduler.shutdownNow();

        AppLogger.info("SERVER", "Smart Home Controller Server stopped successfully.");
    }

    public void removeClientHandler(ClientHandler handler) {
        activeHandlers.remove(handler);
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public DeviceRegistry getRegistry() {
        return registry;
    }

    public int getPort() {
        return port;
    }

    /**
     * Standalone main method to start the controller from terminal.
     */
    public static void main(String[] args) {
        int port = Protocol.DEFAULT_PORT;
        String envPort = System.getenv("CONTROLLER_PORT");
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument: " + args[0] + ". Using default port " + Protocol.DEFAULT_PORT);
            }
        } else if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid CONTROLLER_PORT env: " + envPort + ". Using default port " + Protocol.DEFAULT_PORT);
            }
        }

        ControllerServer server = new ControllerServer(port);

        // Register JVM shutdown hook for graceful exit (e.g. on Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AppLogger.info("SERVER", "JVM Shutdown Hook triggered.");
            server.stop();
        }));

        server.run(); // Run synchronously on the main thread
    }
}
