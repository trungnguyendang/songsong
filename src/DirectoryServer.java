import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class DirectoryServer extends JFrame {
    private DirectoryServiceImpl directoryService;
    private JTextArea logArea;
    private DefaultListModel<String> clientListModel;
    private DefaultListModel<String> fileListModel;
    private JList<String> clientList;
    private JList<String> fileList;
    private JLabel statusLabel;
    
    public DirectoryServer() {
        super("SongSong Directory Server");
        try {
            // Create directory service
            directoryService = new DirectoryServiceImpl();
            
            // Create or get the registry
            Registry registry = LocateRegistry.createRegistry(1099);
            
            // Bind the directory service
            registry.rebind("DirectoryService", directoryService);
            
            // Initialize GUI
            initializeGUI();
            
            // Add WindowListener to call shutdown() when the window is closing
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    shutdown();
                }
            });
            
            // Start refresh timer
            new javax.swing.Timer(3000, e -> refreshData()).start();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting Directory Server: " + e.getMessage(),
                                          "Server Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void startClient() {
    try {
        // Kiểm tra xem Directory Server đã chạy chưa
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            DirectoryService directoryService = (DirectoryService) registry.lookup("DirectoryService");
        } catch (Exception e) {
            // Nếu không kết nối được đến Directory Server, hiển thị thông báo
            JOptionPane.showMessageDialog(this, 
                "Directory Server is not running. Please start it first!", 
                "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Nếu Directory Server đang chạy, tiếp tục tạo Client
        JTextField clientIdField = new JTextField("Client" + (int)(Math.random() * 1000));
        JTextField directoryHostField = new JTextField("localhost");
        
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Client ID:"));
        panel.add(clientIdField);
        panel.add(new JLabel("Directory Host:"));
        panel.add(directoryHostField);
        
        int result = JOptionPane.showConfirmDialog(this, panel, "Client Setup", 
                                                 JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String clientId = clientIdField.getText().trim();
            String directoryHost = directoryHostField.getText().trim();
            
            if (clientId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Client ID cannot be empty", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            SwingUtilities.invokeLater(() -> new Client(clientId, directoryHost));
        }
    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Error starting Client: " + e.getMessage(),
                                     "Error", JOptionPane.ERROR_MESSAGE);
    }
}
    // Shutdown method called when the window is closing
    private void shutdown() {
        try {
            log("Directory Server shutting down...");
            
            // Delete the clients directory recursively if it exists
            File clientsDir = new File("./clients");
            if (clientsDir.exists()) {
                deleteDirectory(clientsDir);
                log("Clients directory deleted");
            }
            
            // Unbind the directory service from the registry
            try {
                Registry registry = LocateRegistry.getRegistry(1099);
                registry.unbind("DirectoryService");
                log("Directory Service unbound from registry");
            } catch (Exception e) {
                log("Error unbinding service: " + e.getMessage());
            }
            
            log("Shutdown complete");
        } catch (Exception e) {
            log("Error during shutdown: " + e.getMessage());
        } finally {
            System.exit(0);
        }
    }
    
    // Helper method to recursively delete a directory
    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directoryToBeDeleted.delete();
    }
    
    // Log method to print messages to both the UI and the console
    private void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = timestamp + " - " + message;
        System.out.println("[Directory Server] " + logEntry);
        
        SwingUtilities.invokeLater(() -> {
            logArea.append(logEntry + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private void initializeGUI() {
        setSize(1000, 700);
        // Set the default close operation to DO_NOTHING_ON_CLOSE to handle shutdown manually
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Title
        JLabel titleLabel = new JLabel("SongSong Directory Server", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        
        // Status Panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Server Status: Running");
        statusLabel.setForeground(Color.GREEN.darker());
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusPanel.add(statusLabel);
        
        // Left Panel - Connected Clients
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        clientList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        
        leftPanel.add(clientScrollPane, BorderLayout.CENTER);
        
        // Center Panel - Available Files
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Available Files"));
        
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        
        centerPanel.add(fileScrollPane, BorderLayout.CENTER);
        
        // Right Panel - Activity Log
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> {
            logArea.setText("");
            try {
                // Clear log in DirectoryServiceImpl if the clearActivityLog method exists
                ((DirectoryServiceImpl)directoryService).clearActivityLog();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        rightPanel.add(clearLogButton, BorderLayout.SOUTH);
        
        // Layout organization
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel contentPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        contentPanel.add(leftPanel);
        contentPanel.add(centerPanel);
        contentPanel.add(rightPanel);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        setVisible(true);
    }
    
    private void refreshData() {
        try {
            // Update the client list
            List<ClientInfo> clients = directoryService.getOnlineClients();
            clientListModel.clear();
            for (ClientInfo client : clients) {
                clientListModel.addElement(client.getClientId() + " (" + 
                                           client.getIpAddress() + ":" + client.getPort() + ")");
            }
            
            // Update the file list
            Set<String> files = directoryService.getAllAvailableFiles();
            fileListModel.clear();
            for (String file : files) {
                fileListModel.addElement(file);
            }
            
            // Update the activity log
            List<String> logs = directoryService.getActivityLog();
            logArea.setText("");
            for (String log : logs) {
                logArea.append(log + "\n");
            }
            
            // Auto-scroll to the bottom of the log area
            logArea.setCaretPosition(logArea.getDocument().getLength());
            
        } catch (Exception e) {
            statusLabel.setText("Server Status: Error refreshing data");
            statusLabel.setForeground(Color.RED);
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new DirectoryServer());
    }
}
