package smarthome.client;

import smarthome.protocol.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =========================================================================
 * USER CLIENT: SWING DESKTOP DASHBOARD
 * =========================================================================
 * SmartHomeGUI provides a clean graphical dashboard for controlling all
 * smart home devices over TCP socket connections.
 *
 * All network commands run asynchronously off the Swing Event Dispatch Thread (EDT)
 * ensuring a smooth, responsive user interface.
 */
public class SmartHomeGUI extends JFrame {

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    private UserClient client;

    // Header Components
    private JTextField hostField;
    private JTextField portField;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel statusBadge;

    // Device Status Labels
    private JLabel lightStatusLabel;
    private JLabel fanStatusLabel;
    private JLabel acStatusLabel;
    private JLabel lockStatusLabel;

    // Device Controls
    private JSpinner acTempSpinner;
    private JComboBox<Integer> fanSpeedCombo;

    // Global Console & Log
    private JTextArea consoleArea;
    private JLabel registeredDevicesLabel;

    public SmartHomeGUI() {
        super("Smart Home Controller - Advanced Dashboard");
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 700);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Connection Header
        mainPanel.add(createConnectionPanel(), BorderLayout.NORTH);

        // 2. Center: Device Control Cards (2x2 Grid)
        mainPanel.add(createDeviceGrid(), BorderLayout.CENTER);

        // 3. Bottom: System Toolbar and Activity Console
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        updateConnectionState(false);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Controller Server Connection"));

        panel.add(new JLabel("Host:"));
        hostField = new JTextField(Protocol.DEFAULT_HOST, 10);
        panel.add(hostField);

        panel.add(new JLabel("Port:"));
        portField = new JTextField(String.valueOf(Protocol.DEFAULT_PORT), 5);
        panel.add(portField);

        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> connectToServer());
        panel.add(connectBtn);

        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.addActionListener(e -> disconnectFromServer());
        panel.add(disconnectBtn);

        panel.add(Box.createHorizontalStrut(15));
        panel.add(new JLabel("Status:"));
        statusBadge = new JLabel(" DISCONNECTED ");
        statusBadge.setOpaque(true);
        statusBadge.setBackground(new Color(220, 53, 69));
        statusBadge.setForeground(Color.WHITE);
        statusBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
        panel.add(statusBadge);

        return panel;
    }

    private JPanel createDeviceGrid() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));

        grid.add(createLightCard());
        grid.add(createFanCard());
        grid.add(createACCard());
        grid.add(createLockCard());

        return grid;
    }

    private JPanel createLightCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                " Smart Light ", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14), new Color(40, 116, 240)));

        lightStatusLabel = new JLabel("Status: UNKNOWN", SwingConstants.CENTER);
        lightStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        lightStatusLabel.setOpaque(true);
        lightStatusLabel.setBackground(new Color(240, 240, 240));
        lightStatusLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(lightStatusLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 10));
        JButton onBtn = new JButton("Turn ON");
        onBtn.setBackground(new Color(40, 167, 69));
        onBtn.setForeground(Color.WHITE);
        onBtn.addActionListener(e -> sendAsyncCommand("LIGHT ON"));

        JButton offBtn = new JButton("Turn OFF");
        offBtn.setBackground(new Color(108, 117, 125));
        offBtn.setForeground(Color.WHITE);
        offBtn.addActionListener(e -> sendAsyncCommand("LIGHT OFF"));

        JButton statusBtn = new JButton("Status");
        statusBtn.addActionListener(e -> sendAsyncCommand("LIGHT STATUS"));

        btnPanel.add(onBtn);
        btnPanel.add(offBtn);
        btnPanel.add(statusBtn);

        card.add(btnPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFanCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                " Smart Ceiling Fan ", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14), new Color(40, 116, 240)));

        fanStatusLabel = new JLabel("Status: UNKNOWN", SwingConstants.CENTER);
        fanStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        fanStatusLabel.setOpaque(true);
        fanStatusLabel.setBackground(new Color(240, 240, 240));
        fanStatusLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(fanStatusLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        JButton onBtn = new JButton("Turn ON");
        onBtn.setBackground(new Color(40, 167, 69));
        onBtn.setForeground(Color.WHITE);
        onBtn.addActionListener(e -> sendAsyncCommand("FAN ON"));

        JButton offBtn = new JButton("Turn OFF");
        offBtn.setBackground(new Color(108, 117, 125));
        offBtn.setForeground(Color.WHITE);
        offBtn.addActionListener(e -> sendAsyncCommand("FAN OFF"));

        JButton statusBtn = new JButton("Status");
        statusBtn.addActionListener(e -> sendAsyncCommand("FAN STATUS"));

        p1.add(onBtn);
        p1.add(offBtn);
        p1.add(statusBtn);

        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        p2.add(new JLabel("Set Speed (1-5):"));
        fanSpeedCombo = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5});
        p2.add(fanSpeedCombo);

        JButton setSpeedBtn = new JButton("Apply Speed");
        setSpeedBtn.addActionListener(e -> {
            Integer speed = (Integer) fanSpeedCombo.getSelectedItem();
            sendAsyncCommand("FAN SPEED " + speed);
        });
        p2.add(setSpeedBtn);

        centerPanel.add(p1);
        centerPanel.add(p2);
        card.add(centerPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createACCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                " Smart Air Conditioner ", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14), new Color(40, 116, 240)));

        acStatusLabel = new JLabel("Status: UNKNOWN", SwingConstants.CENTER);
        acStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        acStatusLabel.setOpaque(true);
        acStatusLabel.setBackground(new Color(240, 240, 240));
        acStatusLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(acStatusLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        JPanel p1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        JButton onBtn = new JButton("Turn ON");
        onBtn.setBackground(new Color(40, 167, 69));
        onBtn.setForeground(Color.WHITE);
        onBtn.addActionListener(e -> sendAsyncCommand("AC ON"));

        JButton offBtn = new JButton("Turn OFF");
        offBtn.setBackground(new Color(108, 117, 125));
        offBtn.setForeground(Color.WHITE);
        offBtn.addActionListener(e -> sendAsyncCommand("AC OFF"));

        JButton statusBtn = new JButton("Status");
        statusBtn.addActionListener(e -> sendAsyncCommand("AC STATUS"));

        p1.add(onBtn);
        p1.add(offBtn);
        p1.add(statusBtn);

        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        p2.add(new JLabel("Temp (°C):"));
        acTempSpinner = new JSpinner(new SpinnerNumberModel(24, 16, 30, 1));
        p2.add(acTempSpinner);

        JButton setTempBtn = new JButton("Set Temp");
        setTempBtn.addActionListener(e -> {
            int temp = (Integer) acTempSpinner.getValue();
            sendAsyncCommand("AC SET " + temp);
        });
        p2.add(setTempBtn);

        centerPanel.add(p1);
        centerPanel.add(p2);
        card.add(centerPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createLockCard() {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true),
                " Smart Door Lock ", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 14), new Color(40, 116, 240)));

        lockStatusLabel = new JLabel("Status: UNKNOWN", SwingConstants.CENTER);
        lockStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        lockStatusLabel.setOpaque(true);
        lockStatusLabel.setBackground(new Color(240, 240, 240));
        lockStatusLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
        card.add(lockStatusLabel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 10));
        JButton lockBtn = new JButton("LOCK Door");
        lockBtn.setBackground(new Color(220, 53, 69));
        lockBtn.setForeground(Color.WHITE);
        lockBtn.addActionListener(e -> sendAsyncCommand("SMARTLOCK LOCK"));

        JButton unlockBtn = new JButton("UNLOCK Door");
        unlockBtn.setBackground(new Color(40, 167, 69));
        unlockBtn.setForeground(Color.WHITE);
        unlockBtn.addActionListener(e -> sendAsyncCommand("SMARTLOCK UNLOCK"));

        JButton statusBtn = new JButton("Status");
        statusBtn.addActionListener(e -> sendAsyncCommand("SMARTLOCK STATUS"));

        btnPanel.add(lockBtn);
        btnPanel.add(unlockBtn);
        btnPanel.add(statusBtn);

        card.add(btnPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(6, 6));

        // System Action Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.setBorder(BorderFactory.createTitledBorder("System Operations"));

        JButton refreshAllBtn = new JButton("Refresh All (STATUS ALL)");
        refreshAllBtn.addActionListener(e -> sendAsyncCommand("STATUS ALL"));
        toolbar.add(refreshAllBtn);

        JButton listDevicesBtn = new JButton("List Connected Devices");
        listDevicesBtn.addActionListener(e -> sendAsyncCommand("LIST"));
        toolbar.add(listDevicesBtn);

        JButton scheduleBtn = new JButton("Schedule Delayed Action...");
        scheduleBtn.addActionListener(e -> showScheduleDialog());
        toolbar.add(scheduleBtn);

        JButton clearLogBtn = new JButton("Clear Console");
        clearLogBtn.addActionListener(e -> consoleArea.setText(""));
        toolbar.add(clearLogBtn);

        registeredDevicesLabel = new JLabel("Devices: None");
        toolbar.add(Box.createHorizontalStrut(15));
        toolbar.add(registeredDevicesLabel);

        bottom.add(toolbar, BorderLayout.NORTH);

        // Activity Console
        consoleArea = new JTextArea(8, 60);
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBackground(new Color(248, 249, 250));
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Activity & Command Console"));

        bottom.add(scrollPane, BorderLayout.CENTER);

        return bottom;
    }

    private void showScheduleDialog() {
        if (client == null || !client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Please connect to the controller server first.", "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        JComboBox<String> deviceBox = new JComboBox<>(new String[]{"LIGHT", "FAN", "AC", "SMARTLOCK"});
        JComboBox<String> actionBox = new JComboBox<>(new String[]{"OFF", "ON", "LOCK", "UNLOCK"});
        JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 3600, 5));

        panel.add(new JLabel("Target Device:"));
        panel.add(deviceBox);
        panel.add(new JLabel("Action:"));
        panel.add(actionBox);
        panel.add(new JLabel("Delay (Seconds):"));
        panel.add(delaySpinner);

        int result = JOptionPane.showConfirmDialog(this, panel, "Schedule Action", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String dev = (String) deviceBox.getSelectedItem();
            String act = (String) actionBox.getSelectedItem();
            int delay = (Integer) delaySpinner.getValue();
            sendAsyncCommand("SCHEDULE " + dev + " " + act + " " + delay);
        }
    }

    private void connectToServer() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        logToConsole("Connecting to " + host + ":" + port + "...");
        connectBtn.setEnabled(false);

        networkExecutor.submit(() -> {
            client = new UserClient(host, port);
            boolean success = client.connect();

            SwingUtilities.invokeLater(() -> {
                if (success) {
                    updateConnectionState(true);
                    logToConsole("Successfully connected to controller!");
                    // Auto-refresh status upon connection
                    sendAsyncCommand("STATUS ALL");
                } else {
                    updateConnectionState(false);
                    logToConsole("Failed to connect to controller server.");
                    JOptionPane.showMessageDialog(SmartHomeGUI.this,
                            "Could not connect to " + host + ":" + port + ".\nPlease verify ControllerServer is running.",
                            "Connection Failed", JOptionPane.ERROR_MESSAGE);
                }
            });
        });
    }

    private void disconnectFromServer() {
        if (client != null) {
            networkExecutor.submit(() -> {
                client.sendCommand("QUIT");
                client.disconnect();
                SwingUtilities.invokeLater(() -> {
                    updateConnectionState(false);
                    logToConsole("Disconnected from controller server.");
                });
            });
        }
    }

    private void sendAsyncCommand(String command) {
        if (client == null || !client.isConnected()) {
            logToConsole("Error: Not connected to controller.");
            return;
        }

        logToConsole(">> " + command);

        networkExecutor.submit(() -> {
            String response = client.sendCommand(command);

            SwingUtilities.invokeLater(() -> {
                logToConsole("<< " + response);
                processResponseForGUI(command, response);

                if (!client.isConnected()) {
                    updateConnectionState(false);
                    logToConsole("[Notice] Server closed connection.");
                }
            });
        });
    }

    /**
     * Parses the response string and updates the visual status badges on device cards.
     */
    private void processResponseForGUI(String sentCommand, String response) {
        String upper = response.toUpperCase();

        if (sentCommand.startsWith("LIGHT") || upper.contains("LIGHT")) {
            if (upper.contains("LIGHT IS ON") || upper.contains("LIGHT STATUS: ON")) {
                lightStatusLabel.setText("Status: ON");
                lightStatusLabel.setBackground(new Color(212, 237, 218));
            } else if (upper.contains("LIGHT IS OFF") || upper.contains("LIGHT STATUS: OFF")) {
                lightStatusLabel.setText("Status: OFF");
                lightStatusLabel.setBackground(new Color(248, 215, 218));
            } else if (upper.contains("DEVICE NOT CONNECTED: LIGHT")) {
                lightStatusLabel.setText("Status: DISCONNECTED");
                lightStatusLabel.setBackground(new Color(230, 230, 230));
            }
        }

        if (sentCommand.startsWith("FAN") || upper.contains("FAN")) {
            if (upper.contains("FAN IS ON") || upper.contains("FAN STATUS: ON") || upper.contains("FAN SPEED")) {
                fanStatusLabel.setText("Status: " + extractFanStatus(response));
                fanStatusLabel.setBackground(new Color(212, 237, 218));
            } else if (upper.contains("FAN IS OFF") || upper.contains("FAN STATUS: OFF")) {
                fanStatusLabel.setText("Status: OFF");
                fanStatusLabel.setBackground(new Color(248, 215, 218));
            } else if (upper.contains("DEVICE NOT CONNECTED: FAN")) {
                fanStatusLabel.setText("Status: DISCONNECTED");
                fanStatusLabel.setBackground(new Color(230, 230, 230));
            }
        }

        if (sentCommand.startsWith("AC") || upper.contains("AC")) {
            if (upper.contains("AC IS ON") || upper.contains("AC STATUS: ON") || upper.contains("AC TEMPERATURE")) {
                acStatusLabel.setText("Status: " + extractACStatus(response));
                acStatusLabel.setBackground(new Color(212, 237, 218));
            } else if (upper.contains("AC IS OFF") || upper.contains("AC STATUS: OFF")) {
                acStatusLabel.setText("Status: " + extractACStatus(response));
                acStatusLabel.setBackground(new Color(248, 215, 218));
            } else if (upper.contains("DEVICE NOT CONNECTED: AC")) {
                acStatusLabel.setText("Status: DISCONNECTED");
                acStatusLabel.setBackground(new Color(230, 230, 230));
            }
        }

        if (sentCommand.startsWith("SMARTLOCK") || upper.contains("SMARTLOCK")) {
            if (upper.contains("LOCKED") && !upper.contains("UNLOCKED")) {
                lockStatusLabel.setText("Status: LOCKED");
                lockStatusLabel.setBackground(new Color(248, 215, 218));
            } else if (upper.contains("UNLOCKED")) {
                lockStatusLabel.setText("Status: UNLOCKED");
                lockStatusLabel.setBackground(new Color(212, 237, 218));
            } else if (upper.contains("DEVICE NOT CONNECTED: SMARTLOCK")) {
                lockStatusLabel.setText("Status: DISCONNECTED");
                lockStatusLabel.setBackground(new Color(230, 230, 230));
            }
        }

        if (sentCommand.equals("STATUS ALL") && upper.startsWith("OK STATUS")) {
            // Re-evaluate individual devices if in STATUS ALL response
            parseStatusAll(response);
        }

        if (sentCommand.equals("LIST") && upper.startsWith("OK CONNECTED DEVICES:")) {
            registeredDevicesLabel.setText("Devices: " + response.substring("OK CONNECTED DEVICES:".length()).trim());
        }
    }

    private void parseStatusAll(String response) {
        // e.g. "OK STATUS AC: OFF (24°C) | FAN: ON (SPEED 2) | LIGHT: ON | SMARTLOCK: LOCKED"
        String[] parts = response.replace("OK STATUS ", "").split("\\|");
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith("LIGHT:")) {
                boolean on = item.toUpperCase().contains("ON");
                lightStatusLabel.setText("Status: " + (on ? "ON" : "OFF"));
                lightStatusLabel.setBackground(on ? new Color(212, 237, 218) : new Color(248, 215, 218));
            } else if (item.startsWith("FAN:")) {
                fanStatusLabel.setText("Status: " + item.replace("FAN:", "").trim());
                fanStatusLabel.setBackground(item.toUpperCase().contains("ON") ? new Color(212, 237, 218) : new Color(248, 215, 218));
            } else if (item.startsWith("AC:")) {
                acStatusLabel.setText("Status: " + item.replace("AC:", "").trim());
                acStatusLabel.setBackground(item.toUpperCase().contains("ON") ? new Color(212, 237, 218) : new Color(248, 215, 218));
            } else if (item.startsWith("SMARTLOCK:")) {
                boolean locked = item.toUpperCase().contains("LOCKED") && !item.toUpperCase().contains("UNLOCKED");
                lockStatusLabel.setText("Status: " + (locked ? "LOCKED" : "UNLOCKED"));
                lockStatusLabel.setBackground(locked ? new Color(248, 215, 218) : new Color(212, 237, 218));
            }
        }
    }

    private String extractFanStatus(String resp) {
        if (resp.contains("SPEED")) {
            int idx = resp.indexOf("SPEED");
            return "ON (" + resp.substring(idx).replace(")", "") + ")";
        }
        return "ON";
    }

    private String extractACStatus(String resp) {
        if (resp.contains("°C")) {
            int start = resp.indexOf("(");
            int end = resp.indexOf(")");
            if (start >= 0 && end > start) {
                return (resp.contains("IS ON") || resp.contains("STATUS: ON") ? "ON " : "OFF ") + resp.substring(start, end + 1);
            }
        }
        if (resp.contains("SET TO")) {
            return "ON (" + resp.substring(resp.indexOf("SET TO") + 7) + ")";
        }
        return resp.contains("IS ON") ? "ON" : "OFF";
    }

    private void updateConnectionState(boolean isConnected) {
        connectBtn.setEnabled(!isConnected);
        disconnectBtn.setEnabled(isConnected);
        hostField.setEditable(!isConnected);
        portField.setEditable(!isConnected);

        if (isConnected) {
            statusBadge.setText(" CONNECTED ");
            statusBadge.setBackground(new Color(40, 167, 69));
        } else {
            statusBadge.setText(" DISCONNECTED ");
            statusBadge.setBackground(new Color(220, 53, 69));
            registeredDevicesLabel.setText("Devices: None");
        }
    }

    private void logToConsole(String message) {
        String time = timeFormat.format(new Date());
        consoleArea.append("[" + time + "] " + message + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        // Set System Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            SmartHomeGUI gui = new SmartHomeGUI();
            gui.setVisible(true);
        });
    }
}
