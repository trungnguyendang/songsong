import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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
            
            // Start refresh timer
            new javax.swing.Timer(3000, e -> refreshData()).start();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting Directory Server: " + e.getMessage(),
                                         "Server Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void initializeGUI() {
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
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
        
        // Left Panel - Client List
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
        
        // Right Panel - Log
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        
        JButton clearLogButton = new JButton("Clear Log");
        clearLogButton.addActionListener(e -> {
            logArea.setText("");
        });
        
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        rightPanel.add(clearLogButton, BorderLayout.SOUTH);
        
        // Layout
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
            // Update client list
            List<ClientInfo> clients = directoryService.getOnlineClients();
            clientListModel.clear();
            for (ClientInfo client : clients) {
                clientListModel.addElement(client.getClientId() + " (" + 
                                         client.getIpAddress() + ":" + client.getPort() + ")");
            }
            
            // Update file list
            Set<String> files = directoryService.getAllAvailableFiles();
            fileListModel.clear();
            for (String file : files) {
                fileListModel.addElement(file);
            }
            
            // Update log
            List<String> logs = directoryService.getActivityLog();
            logArea.setText("");
            for (String log : logs) {
                logArea.append(log + "\n");
            }
            
            // Auto-scroll to bottom
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