package smarthome;

import smarthome.client.UserClient;
import smarthome.device.AC;
import smarthome.device.Fan;
import smarthome.device.Light;
import smarthome.device.SmartLock;
import smarthome.server.ControllerServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * =========================================================================
 * AUTOMATED SMART HOME TEST SUITE
 * =========================================================================
 * Performs automated verification of all 13 required test cases specified
 * in the Smart Home Controller PRD, without requiring external testing frameworks.
 */
public class SmartHomeTestSuite {

    private static final int TEST_PORT = 5055;
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static final List<String> testSummary = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("            SMART HOME CONTROLLER - AUTOMATED TEST SUITE                        ");
        System.out.println("================================================================================\n");

        ControllerServer server = null;
        Light light = null;
        Fan fan = null;
        AC ac = null;
        SmartLock lock = null;
        UserClient client1 = null;
        UserClient client2 = null;

        try {
            // Test 1: Start Server
            server = new ControllerServer(TEST_PORT);
            server.start();
            Thread.sleep(600);
            assertTest("1. Start Controller Server", server.isRunning(), "Server successfully bound to port " + TEST_PORT);

            // Test 2: Start Light Client & Verify Registration
            light = new Light("localhost", TEST_PORT);
            light.start();
            Thread.sleep(600);
            client1 = new UserClient("localhost", TEST_PORT);
            boolean client1Connected = client1.connect();
            assertTest("2. Client 1 Connect & Light Registration",
                    client1Connected && server.getRegistry().isDeviceConnected("LIGHT"),
                    "Light registered in Controller DeviceRegistry");

            // Test 3: Send LIGHT ON
            String resp3 = client1.sendCommand("LIGHT ON");
            assertTest("3. Command: LIGHT ON",
                    resp3.contains("OK") && resp3.contains("LIGHT IS ON") && light.isOn(),
                    "Light state verified ON. Response: " + resp3);

            // Test 4: Send LIGHT OFF
            String resp4 = client1.sendCommand("LIGHT OFF");
            assertTest("4. Command: LIGHT OFF",
                    resp4.contains("OK") && resp4.contains("LIGHT IS OFF") && !light.isOn(),
                    "Light state verified OFF. Response: " + resp4);

            // Test 5: Start AC and Send AC SET 24
            ac = new AC("localhost", TEST_PORT);
            ac.start();
            Thread.sleep(600);
            String resp5 = client1.sendCommand("AC SET 24");
            assertTest("5. Command: AC SET 24",
                    resp5.contains("OK") && resp5.contains("24") && ac.getTemperature() == 24,
                    "AC temperature set to 24°C. Response: " + resp5);

            // Test 6: Send Invalid AC Temperature
            String resp6 = client1.sendCommand("AC SET 99");
            assertTest("6. Command: Invalid AC Temp (99°C)",
                    resp6.contains("ERROR") && resp6.contains("16-30") && ac.getTemperature() == 24,
                    "Rejected out-of-bounds temperature; state remained 24°C. Response: " + resp6);

            // Test 7: Start Fan and SmartLock, then Send STATUS ALL
            fan = new Fan("localhost", TEST_PORT);
            fan.start();
            lock = new SmartLock("localhost", TEST_PORT);
            lock.start();
            Thread.sleep(800);

            String resp7 = client1.sendCommand("STATUS ALL");
            assertTest("7. Command: STATUS ALL",
                    resp7.contains("OK") && resp7.contains("LIGHT") && resp7.contains("FAN") && resp7.contains("AC") && resp7.contains("SMARTLOCK"),
                    "All 4 active devices reported status. Response: " + resp7);

            // Test 8: Fan Speed Control
            String resp8 = client1.sendCommand("FAN SPEED 3");
            assertTest("8. Command: FAN SPEED 3",
                    resp8.contains("OK") && resp8.contains("3") && fan.getSpeed() == 3,
                    "Fan speed changed to 3. Response: " + resp8);

            // Test 9: SmartLock Locking & Unlocking
            String resp9Unlock = client1.sendCommand("SMARTLOCK UNLOCK");
            String resp9Lock = client1.sendCommand("SMARTLOCK LOCK");
            assertTest("9. Command: SMARTLOCK UNLOCK / LOCK",
                    resp9Unlock.contains("UNLOCKED") && resp9Lock.contains("LOCKED") && lock.isLocked(),
                    "Door Lock state transition verified. Response: " + resp9Lock);

            // Test 10: Disconnect Fan Client and Verify Removal from Registry
            fan.stop();
            Thread.sleep(600);
            assertTest("10. Fan Disconnect Handling",
                    !server.getRegistry().isDeviceConnected("FAN"),
                    "Fan successfully removed from active registry upon socket close");

            // Test 11: Send Command to Disconnected Device
            String resp11 = client1.sendCommand("FAN ON");
            assertTest("11. Command to Disconnected Device (FAN ON)",
                    resp11.contains("ERROR") && resp11.contains("NOT CONNECTED"),
                    "Server caught missing device and returned clear error. Response: " + resp11);

            // Test 12: Unknown Command Handling
            String resp12 = client1.sendCommand("MAGIC_DEVICE FIRE");
            assertTest("12. Unknown Command Handling",
                    resp12.contains("ERROR") && resp12.contains("UNKNOWN COMMAND"),
                    "Server rejected invalid command syntax. Response: " + resp12);

            // Test 13: Concurrent Multiple User Clients
            client2 = new UserClient("localhost", TEST_PORT);
            client2.connect();

            ExecutorService exec = Executors.newFixedThreadPool(2);
            final UserClient c1 = client1;
            final UserClient c2 = client2;

            Callable<String> task1 = () -> c1.sendCommand("LIGHT ON");
            Callable<String> task2 = () -> c2.sendCommand("AC SET 22");

            Future<String> f1 = exec.submit(task1);
            Future<String> f2 = exec.submit(task2);

            String r1 = f1.get();
            String r2 = f2.get();
            exec.shutdown();

            assertTest("13. Concurrent Multi-User Requests",
                    r1.contains("LIGHT IS ON") && r2.contains("22"),
                    "Concurrent user commands routed accurately without cross-talk: [" + r1 + "] & [" + r2 + "]");

            // Test 14: Server Stop & Client Disconnection
            server.stop();
            Thread.sleep(600);
            assertTest("14. Graceful Server Shutdown",
                    !server.isRunning(),
                    "Server stopped cleanly, socket unbound");

        } catch (Exception e) {
            System.err.println("Unexpected exception in test suite: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (light != null) light.stop();
            if (fan != null) fan.stop();
            if (ac != null) ac.stop();
            if (lock != null) lock.stop();
            if (client1 != null) client1.disconnect();
            if (client2 != null) client2.disconnect();
            if (server != null && server.isRunning()) server.stop();
        }

        printFinalReport();
    }

    private static void assertTest(String testName, boolean condition, String details) {
        totalTests++;
        String result;
        if (condition) {
            passedTests++;
            result = "[PASS]";
            System.out.printf("%-45s %s\n   -> %s\n\n", testName, result, details);
        } else {
            result = "[FAIL]";
            System.err.printf("%-45s %s\n   -> %s\n\n", testName, result, details);
        }
        testSummary.add(String.format("| %-42s | %-6s | %-45s |", testName, result, details));
    }

    private static void printFinalReport() {
        System.out.println("================================================================================");
        System.out.println("                             FINAL TEST REPORT                                  ");
        System.out.println("================================================================================");
        System.out.println("| Test Case Description                      | Status | Details                                       |");
        System.out.println("|--------------------------------------------|--------|-----------------------------------------------|");
        for (String row : testSummary) {
            System.out.println(row);
        }
        System.out.println("================================================================================");
        System.out.printf("TOTAL TESTS: %d | PASSED: %d | FAILED: %d\n", totalTests, passedTests, (totalTests - passedTests));
        if (passedTests == totalTests) {
            System.out.println(">>> ALL TESTS PASSED SUCCESSFULLY! PROJECT IS 100% OPERATIONAL. <<<");
        } else {
            System.err.println(">>> SOME TESTS FAILED. PLEASE REVIEW LOGS. <<<");
        }
        System.out.println("================================================================================\n");
    }
}
