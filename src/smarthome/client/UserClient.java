package smarthome.client;

import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * =========================================================================
 * USER CLIENT: CONSOLE / CLI INTERFACE
 * =========================================================================
 * UserClient connects to the ControllerServer over TCP, registers as a user,
 * and provides an interactive command-line interface to operate the smart home.
 */
public class UserClient {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = false;

    public UserClient() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public UserClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to the controller server and performs registration.
     */
    public synchronized boolean connect() {
        if (connected) {
            return true;
        }

        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Register as User
            out.println(Protocol.CMD_REGISTER + " " + Protocol.ROLE_USER);
            String response = in.readLine();

            if (response != null && response.startsWith(Protocol.RESP_OK)) {
                connected = true;
                AppLogger.info("USER", "Connected to controller at " + host + ":" + port);
                return true;
            } else {
                AppLogger.error("USER", "Registration rejected by server: " + response);
                disconnect();
                return false;
            }

        } catch (IOException e) {
            AppLogger.error("USER", "Failed to connect to " + host + ":" + port + " - " + e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * Sends a command line to the controller and waits for response.
     * Synchronized to prevent concurrent writes/reads on the user socket.
     *
     * @param command Text command (e.g. "LIGHT ON", "STATUS ALL").
     * @return Server response.
     */
    public synchronized String sendCommand(String command) {
        if (!connected || socket == null || socket.isClosed()) {
            return Protocol.error("NOT CONNECTED TO CONTROLLER SERVER");
        }

        try {
            out.println(command);
            if (out.checkError()) {
                disconnect();
                return Protocol.error("CONNECTION DROPPED BY SERVER");
            }

            String response = in.readLine();
            if (response == null) {
                disconnect();
                return Protocol.error("SERVER CLOSED THE CONNECTION");
            }

            return response;

        } catch (IOException e) {
            disconnect();
            return Protocol.error("COMMUNICATION ERROR: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the server cleanly.
     */
    public synchronized void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Starts the interactive command loop in the terminal.
     */
    public void startInteractiveSession() {
        if (!connect()) {
            System.err.println("Could not connect to Smart Home Controller at " + host + ":" + port);
            System.err.println("Please make sure ControllerServer is running first.");
            return;
        }

        System.out.println("=================================================================");
        System.out.println("           SMART HOME CONTROLLER - USER CONSOLE                  ");
        System.out.println("=================================================================");
        System.out.println(" Connected to: " + host + ":" + port);
        System.out.println(" Type 'HELP' for a list of available commands, or 'QUIT' to exit.");
        System.out.println("=================================================================\n");

        Scanner scanner = new Scanner(System.in);
        while (connected) {
            System.out.print("SmartHome> ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            if (input.equalsIgnoreCase(Protocol.CMD_QUIT) || input.equalsIgnoreCase(Protocol.CMD_EXIT)) {
                String response = sendCommand(input);
                System.out.println("Server: " + response);
                break;
            }

            String response = sendCommand(input);
            System.out.println("Response: " + response);

            if (!connected) {
                System.out.println("\n[Notice] Connection to server was closed.");
                break;
            }
        }

        disconnect();
        System.out.println("Exiting Smart Home User Console. Goodbye!");
    }

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : Protocol.DEFAULT_HOST;
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        UserClient client = new UserClient(host, port);
        client.startInteractiveSession();
    }
}
