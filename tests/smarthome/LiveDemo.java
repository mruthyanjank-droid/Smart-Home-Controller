package smarthome;

import smarthome.client.UserClient;

public class LiveDemo {
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "127.0.0.1";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;

        UserClient client = new UserClient(host, port);
        if (!client.connect()) {
            System.err.println("Could not connect to " + host + ":" + port);
            return;
        }

        System.out.println("=== LIVE CONTROLLER VERIFICATION ===");
        System.out.println("Querying Devices  : " + client.sendCommand("LIST"));
        System.out.println("Status All Initial: " + client.sendCommand("STATUS ALL"));
        System.out.println("Turning ON Light  : " + client.sendCommand("LIGHT ON"));
        System.out.println("Setting AC Temp   : " + client.sendCommand("AC SET 21"));
        System.out.println("Setting Fan Speed : " + client.sendCommand("FAN SPEED 4"));
        System.out.println("Unlocking Door    : " + client.sendCommand("SMARTLOCK UNLOCK"));
        System.out.println("Status All Final  : " + client.sendCommand("STATUS ALL"));
        client.disconnect();
        System.out.println("=== LIVE TEST COMPLETED SUCCESSFULLY ===");
    }
}
