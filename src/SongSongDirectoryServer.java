import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SongSongDirectoryServer {
    private DirectoryService directoryService;
    private JFrame frame;
    private JTextArea logArea;
    private DefaultListModel<String> clientListModel;
    private JList<String> clientList;
    
    public SongSongDirectoryServer() {
        try {
            // Create directory service
            directoryService = new DirectoryServiceImpl();
            
            // Create or get the registry
            Registry registry = LocateRegistry.createRegistry(1099);
            
            // Bind the directory service
            registry.rebind("DirectoryService", directoryService);
            
            // Initialize GUI
            initializeGUI();
            
            logMessage("Directory Server started successfully on port 1099");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error starting Directory Server: " + e.getMessage(),
                                         "Server Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initializeGUI() {
        frame = new JFrame("SongSong Directory Server");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("SongSong Directory Server");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        
        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        
        // Client list
        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        JScrollPane clientScrollPane = new JScrollPane(clientList);
        clientScrollPane.setBorder(BorderFactory.createTitledBorder("Connected Clients"));
        
        // Refresh button
        JButton refreshButton = new JButton("Refresh Client List");
        refreshButton.addActionListener(e -> refreshClientList());
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel statusLabel = new JLabel("Server Status: Running");
        statusLabel.setForeground(Color.GREEN.darker());
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusPanel.add(statusLabel);
        
        // Layout
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(clientScrollPane, BorderLayout.CENTER);
        rightPanel.add(refreshButton, BorderLayout.SOUTH);
        
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(logScrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        frame.add(mainPanel);
        frame.setVisible(true);
        
        // Start client list refresh timer
        new Timer(5000, e -> refreshClientList()).start();
    }
    
    private void refreshClientList() {
        try {
            clientListModel.clear();
            List<ClientInfo> clients = directoryService.getAvailableClients();
            for (ClientInfo client : clients) {
                clientListModel.addElement(client.toString() + " (Load: " + client.getCurrentLoad() + ")");
            }
        } catch (Exception e) {
            logMessage("Error refreshing client list: " + e.getMessage());
        }
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%tF %<tT] %s%n", System.currentTimeMillis(), message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new SongSongDirectoryServer());
    }
}