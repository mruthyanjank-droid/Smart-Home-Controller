package smarthome.server;

import smarthome.protocol.CommandParser;
import smarthome.protocol.ParsedCommand;
import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * =========================================================================
 * OOP CONCEPT: MULTITHREADING & ENCAPSULATION
 * =========================================================================
 * ClientHandler processes incoming connections on dedicated threads.
 * It identifies whether a connection represents a simulated device or a user,
 * hands off devices to DeviceSession, and serves user commands.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final DeviceRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final ControllerServer server;

    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean running = true;
    private String clientType = "UNKNOWN";
    private String clientAddress;

    public ClientHandler(Socket socket, DeviceRegistry registry, ScheduledExecutorService scheduler, ControllerServer server) {
        this.socket = socket;
        this.registry = registry;
        this.scheduler = scheduler;
        this.server = server;
        this.clientAddress = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        AppLogger.info("SERVER", "Accepted connection from " + clientAddress);

        try {
            socket.setTcpNoDelay(true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read the initial handshake/command
            String firstLine = in.readLine();
            if (firstLine == null) {
                AppLogger.warn("SERVER", "Client disconnected without sending data: " + clientAddress);
                return;
            }

            // Check if connection is from a web browser via HTTP GET
            if (firstLine.toUpperCase().startsWith("GET ") || firstLine.toUpperCase().startsWith("HEAD ") || firstLine.toUpperCase().startsWith("POST ")) {
                handleHttpRequest(firstLine);
                return;
            }

            ParsedCommand initialCommand = CommandParser.parse(firstLine);

            // Check if this connection is a smart device registration
            if (initialCommand.getType() == ParsedCommand.Type.REGISTER_DEVICE) {
                handleDeviceRegistration(initialCommand.getTargetDevice());
                return; // DeviceSession takes ownership of socket reading
            }

            // Otherwise, treat connection as a User client
            clientType = Protocol.ROLE_USER;
            if (initialCommand.getType() == ParsedCommand.Type.REGISTER_USER) {
                out.println(Protocol.ok("REGISTERED USER"));
                AppLogger.info("SERVER", "User client registered: " + clientAddress);
            } else {
                // If the user sent a command directly on connect, process it immediately
                processUserCommand(initialCommand);
            }

            // Loop to handle interactive user commands
            String line;
            while (running && (line = in.readLine()) != null) {
                ParsedCommand cmd = CommandParser.parse(line);
                boolean keepGoing = processUserCommand(cmd);
                if (!keepGoing) {
                    break;
                }
            }

        } catch (IOException e) {
            AppLogger.info("SERVER", "Client disconnected (" + clientType + " " + clientAddress + "): " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Handles device registration and hands off socket ownership to DeviceSession.
     */
    private void handleDeviceRegistration(String deviceName) throws IOException {
        clientType = deviceName;
        DeviceSession session = new DeviceSession(deviceName, socket, registry);

        boolean registered = registry.registerDevice(deviceName, session);
        if (registered) {
            out.println(Protocol.ok("REGISTERED " + deviceName));
            session.startListener();
        } else {
            out.println(Protocol.error("DEVICE ALREADY REGISTERED: " + deviceName));
            socket.close();
        }
    }

    /**
     * Processes a validated user command and transmits the result back to the user client.
     *
     * @return true to keep connection open; false if user quit.
     */
    private boolean processUserCommand(ParsedCommand cmd) {
        if (!cmd.isValid()) {
            AppLogger.warn("SERVER", "Invalid command from " + clientAddress + ": " + cmd.getErrorMessage());
            out.println(Protocol.error(cmd.getErrorMessage()));
            return true;
        }

        switch (cmd.getType()) {
            case SERVER_LIST:
                List<String> devices = registry.getConnectedDeviceNames();
                if (devices.isEmpty()) {
                    out.println(Protocol.ok("NO DEVICES CONNECTED"));
                } else {
                    out.println(Protocol.ok("CONNECTED DEVICES: " + String.join(", ", devices)));
                }
                break;

            case SERVER_STATUS_ALL:
                String statusAll = registry.getStatusAll();
                out.println(statusAll);
                break;

            case SERVER_HELP:
                out.println(getHelpText());
                break;

            case SERVER_QUIT:
                out.println(Protocol.ok("GOODBYE"));
                return false;

            case SERVER_SCHEDULE:
                handleScheduleCommand(cmd);
                break;

            case DEVICE_COMMAND:
                String targetDevice = cmd.getTargetDevice();
                String actionPart = cmd.getAction();
                if (cmd.getArgument() != null) {
                    actionPart = actionPart + " " + cmd.getArgument();
                }

                AppLogger.info("SERVER", "Routing command to [" + targetDevice + "]: " + actionPart);
                String response = registry.routeCommand(targetDevice, actionPart);
                AppLogger.info("SERVER", "Response from [" + targetDevice + "]: " + response);
                out.println(response);
                break;

            default:
                out.println(Protocol.error("UNKNOWN COMMAND"));
                break;
        }

        return true;
    }

    /**
     * Handles optional scheduling: executes an action after a specified delay.
     */
    private void handleScheduleCommand(ParsedCommand cmd) {
        String targetDevice = cmd.getTargetDevice();
        String action = cmd.getAction();
        int seconds = Integer.parseInt(cmd.getArgument());

        AppLogger.info("SERVER", "Scheduled task queued: " + targetDevice + " " + action + " in " + seconds + " seconds.");
        out.println(Protocol.ok("SCHEDULED " + targetDevice + " " + action + " IN " + seconds + " SECONDS"));

        scheduler.schedule(() -> {
            AppLogger.info("SERVER", "Executing scheduled action: " + targetDevice + " " + action);
            String result = registry.routeCommand(targetDevice, action);
            AppLogger.info("SERVER", "Scheduled action result for " + targetDevice + ": " + result);
        }, seconds, TimeUnit.SECONDS);
    }

    private String getHelpText() {
        return Protocol.ok("COMMANDS: LIST | STATUS ALL | LIGHT [ON|OFF|STATUS] | " +
                "FAN [ON|OFF|SPEED 1-5|STATUS] | AC [ON|OFF|SET 16-30|STATUS] | " +
                "SMARTLOCK [LOCK|UNLOCK|STATUS] | SCHEDULE <DEVICE> <ACTION> <SECS> | QUIT");
    }

    /**
     * Intercepts and handles HTTP requests from the CTF Cyber Command Center web application.
     */
    private void handleHttpRequest(String requestLine) {
        try {
            String[] reqParts = requestLine.split("\\s+");
            String method = reqParts[0].toUpperCase();
            String path = reqParts.length > 1 ? reqParts[1] : "/";

            // 1. Drain HTTP headers and extract Content-Length
            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.trim().isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(headerLine.substring(15).trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Read POST body if present
            String postBody = "";
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = in.read(bodyChars, totalRead, contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                postBody = new String(bodyChars, 0, totalRead);
            }

            // Handle CORS preflight
            if (method.equals("OPTIONS")) {
                sendHttpResponse("200 OK", "text/plain", "OK");
                return;
            }

            // 2. API Route: GET /api/status (Live telemetry & device states)
            if (path.startsWith("/api/status")) {
                String statusJson = String.format("{\"server\":{\"status\":\"ONLINE\",\"port\":%d,\"uptimeSeconds\":%d,\"totalCommands\":%d,\"totalAcked\":%d,\"totalErrors\":%d},\"devices\":%s,\"events\":%s}",
                        server.getPort(), ServerStats.getUptimeSeconds(), ServerStats.getTotalCommands(), ServerStats.getTotalAcknowledged(), ServerStats.getTotalErrors(),
                        registry.getDevicesTelemetryJson(), ServerStats.getEventsJson(40));
                sendHttpResponse("200 OK", "application/json; charset=UTF-8", statusJson);
                return;
            }

            // 3. API Route: POST /api/command or GET /api/command?cmd=...
            if (path.startsWith("/api/command")) {
                String cmdStr = "";

                if (path.contains("cmd=")) {
                    int cIdx = path.indexOf("cmd=");
                    cmdStr = java.net.URLDecoder.decode(path.substring(cIdx + 4), "UTF-8");
                    int amp = cmdStr.indexOf('&');
                    if (amp > 0) cmdStr = cmdStr.substring(0, amp);
                } else if (postBody.contains("command")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"?command\"?\\s*[:=]\\s*\"?([^\"}\r\n]+)\"?").matcher(postBody);
                    if (m.find()) {
                        cmdStr = m.group(1).trim();
                    }
                }

                if (cmdStr.isEmpty() && !postBody.trim().isEmpty() && !postBody.trim().startsWith("{")) {
                    cmdStr = postBody.trim();
                }

                if (cmdStr.isEmpty()) {
                    sendHttpResponse("400 Bad Request", "application/json; charset=UTF-8",
                            "{\"success\":false,\"error\":\"MISSING COMMAND PARAMETER\"}");
                    return;
                }

                // Process command through CommandParser & DeviceRegistry
                ParsedCommand parsed = CommandParser.parse(cmdStr);
                if (!parsed.isValid()) {
                    ServerStats.recordCommand(false);
                    ServerStats.recordEvent("INVALID_CMD", "OPERATOR", cmdStr + " -> " + parsed.getErrorMessage(), false);
                    String errJson = String.format("{\"success\":false,\"error\":\"%s\",\"rawCommand\":\"%s\"}",
                            ServerStats.escape(parsed.getErrorMessage()), ServerStats.escape(cmdStr));
                    sendHttpResponse("200 OK", "application/json; charset=UTF-8", errJson);
                    return;
                }

                long tStart = System.currentTimeMillis();
                String target = "CONTROLLER";
                String response;

                switch (parsed.getType()) {
                    case SERVER_LIST:
                        List<String> devs = registry.getConnectedDeviceNames();
                        response = devs.isEmpty() ? "OK NO DEVICES CONNECTED" : "OK CONNECTED DEVICES: " + String.join(", ", devs);
                        break;
                    case SERVER_STATUS_ALL:
                        response = registry.getStatusAll();
                        break;
                    case SERVER_SCHEDULE:
                        handleScheduleCommand(parsed);
                        response = "OK SCHEDULED " + parsed.getTargetDevice() + " " + parsed.getAction() + " IN " + parsed.getArgument() + " SECONDS";
                        break;
                    case DEVICE_COMMAND:
                        target = parsed.getTargetDevice();
                        String act = parsed.getAction() + (parsed.getArgument() != null ? " " + parsed.getArgument() : "");
                        response = registry.routeCommand(target, act);
                        break;
                    default:
                        response = Protocol.error("UNKNOWN COMMAND TYPE");
                        break;
                }

                long latency = System.currentTimeMillis() - tStart;
                boolean isOk = response.startsWith(Protocol.RESP_OK);

                String traceJson = String.format("[\"OPERATOR > %s\",\"CONTROLLER > COMMAND ROUTED TO %s (TCP/%d)\",\"%s > %s\",\"SYSTEM > %s\"]",
                        ServerStats.escape(cmdStr), ServerStats.escape(target), server.getPort(),
                        ServerStats.escape(target), ServerStats.escape(response), isOk ? "COMMAND ACKNOWLEDGED" : "COMMAND FAILED");

                String resJson = String.format("{\"success\":%b,\"command\":\"%s\",\"targetDevice\":\"%s\",\"response\":\"%s\",\"latencyMs\":%d,\"trace\":%s,\"devices\":%s}",
                        isOk, ServerStats.escape(cmdStr), ServerStats.escape(target), ServerStats.escape(response), latency, traceJson, registry.getDevicesTelemetryJson());

                sendHttpResponse("200 OK", "application/json; charset=UTF-8", resJson);
                return;
            }

            // 4. API Route: GET /api/events
            if (path.startsWith("/api/events")) {
                sendHttpResponse("200 OK", "application/json; charset=UTF-8", ServerStats.getEventsJson(50));
                return;
            }

            // 5. Static CSS
            if (path.startsWith("/css/cyber-c2.css")) {
                java.io.File cssFile = new java.io.File("web/css/cyber-c2.css");
                serveFile(cssFile, "text/css; charset=UTF-8");
                return;
            }

            // 6. Static JS
            if (path.startsWith("/js/cyber-c2.js")) {
                java.io.File jsFile = new java.io.File("web/js/cyber-c2.js");
                serveFile(jsFile, "application/javascript; charset=UTF-8");
                return;
            }

            // 7. HTML Landing & SPA Routes (/, /index.html, /devices/*, /architecture)
            java.io.File htmlFile = new java.io.File("web/index.html");
            if (htmlFile.exists()) {
                serveFile(htmlFile, "text/html; charset=UTF-8");
            } else {
                sendHttpResponse("200 OK", "text/html; charset=UTF-8", "<h1>Smart Home C2 Controller Online</h1>");
            }

        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void serveFile(java.io.File file, String contentType) throws IOException {
        if (file.exists() && file.isFile()) {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            sendHttpResponseBytes("200 OK", contentType, bytes);
        } else {
            sendHttpResponse("404 Not Found", "text/plain", "File Not Found: " + file.getName());
        }
    }

    private void sendHttpResponse(String status, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        sendHttpResponseBytes(status, contentType, bytes);
    }

    private void sendHttpResponseBytes(String status, String contentType, byte[] bytes) throws IOException {
        out.print("HTTP/1.1 " + status + "\r\n");
        out.print("Content-Type: " + contentType + "\r\n");
        out.print("Content-Length: " + bytes.length + "\r\n");
        out.print("Access-Control-Allow-Origin: *\r\n");
        out.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        out.print("Access-Control-Allow-Headers: Content-Type\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.flush();

        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    public void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed() && !clientType.equals("UNKNOWN") && !CommandParser.isValidDeviceName(clientType)) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        server.removeClientHandler(this);
    }
}
