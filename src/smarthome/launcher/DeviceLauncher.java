package smarthome.launcher;

import smarthome.device.AC;
import smarthome.device.Device;
import smarthome.device.Fan;
import smarthome.device.Light;
import smarthome.device.SmartLock;
import smarthome.protocol.Protocol;
import smarthome.util.AppLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * =========================================================================
 * DEVICE SIMULATOR LAUNCHER
 * =========================================================================
 * DeviceLauncher allows launching all 4 simulated smart devices (Light, Fan,
 * AC, SmartLock) together in a single console session, or controlling them
 * interactively. This makes full-system testing and viva demonstrations seamless.
 */
public class DeviceLauncher {

    private final List<Device> devices = new ArrayList<>();
    private final String host;
    private final int port;

    public DeviceLauncher(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void launchAll() {
        AppLogger.info("LAUNCHER", "Initializing all 4 simulated devices on " + host + ":" + port + "...");

        Light light = new Light(host, port);
        Fan fan = new Fan(host, port);
        AC ac = new AC(host, port);
        SmartLock lock = new SmartLock(host, port);

        devices.add(light);
        devices.add(fan);
        devices.add(ac);
        devices.add(lock);

        for (Device d : devices) {
            d.setAutoReconnect(true);
            d.start();
        }

        AppLogger.info("LAUNCHER", "All 4 devices launched and connected to controller.");
    }

    public void stopAll() {
        AppLogger.info("LAUNCHER", "Stopping all devices...");
        for (Device d : devices) {
            d.stop();
        }
        devices.clear();
    }

    public static void main(String[] args) {
        String host = Protocol.DEFAULT_HOST;
        int port = Protocol.DEFAULT_PORT;

        String envHost = System.getenv("CONTROLLER_HOST");
        String envPort = System.getenv("CONTROLLER_PORT");

        if (args.length > 0) {
            host = args[0];
        } else if (envHost != null && !envHost.trim().isEmpty()) {
            host = envHost.trim();
        }

        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument: " + args[1] + ". Using default port " + Protocol.DEFAULT_PORT);
            }
        } else if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid CONTROLLER_PORT env: " + envPort + ". Using default port " + Protocol.DEFAULT_PORT);
            }
        }

        DeviceLauncher launcher = new DeviceLauncher(host, port);
        launcher.launchAll();

        System.out.println("\n[DeviceLauncher] All 4 simulated smart devices are running in the background.");
        System.out.println("[DeviceLauncher] Type 'STOP' to terminate all devices, or press Ctrl+C to exit.\n");

        Runtime.getRuntime().addShutdownHook(new Thread(launcher::stopAll));

        if (System.console() != null) {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("STOP") || line.equalsIgnoreCase("EXIT") || line.equalsIgnoreCase("QUIT")) {
                    break;
                }
            }
        } else {
            // Running in background / headless mode
            try {
                while (true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        launcher.stopAll();
        System.out.println("[DeviceLauncher] Devices terminated. Goodbye!");
    }
}
