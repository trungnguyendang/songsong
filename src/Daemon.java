import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class Daemon {
    private String clientId;
    private String ipAddress;
    private int port;
    private DirectoryService directoryService;
    private Map<String, File> availableFiles = new ConcurrentHashMap<>();
    private int currentConnections = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService fragmentExecutor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    
    // GUI components
    private JFrame frame;
    private JLabel statusLabel;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JLabel connectionCountLabel;
    
    public Daemon(String clientId, String directoryHost) {
        this.clientId = clientId;
        
        try {
            this.ipAddress = InetAddress.getLocalHost().getHostAddress();
            this.port = 5000; // Default port
            
            // Connect to the directory service
            Registry registry = LocateRegistry.getRegistry(directoryHost, 1099);
            directoryService = (DirectoryService) registry.lookup("DirectoryService");
            
            // Scan for available files
            scanFiles();
            
            // Initialize GUI
            initializeGUI();
            
            // Start the daemon server
            startServer();
            
            // Register with the directory
            List<String> fileNames = new ArrayList<>(availableFiles.keySet());
            directoryService.registerClient(clientId, ipAddress, port, fileNames);
            
            // Schedule heartbeat
            scheduler.scheduleAtFixedRate(this::sendHeartbeat, 20, 20, TimeUnit.SECONDS);
            
            // Schedule file rescanning
            scheduler.scheduleAtFixedRate(this::rescanFiles, 60, 60, TimeUnit.SECONDS);
            
            updateStatus("Daemon started and registered with directory");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error starting daemon: " + e.getMessage(), 
                                         "Daemon Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initializeGUI() {
        frame = new JFrame("SongSong Daemon - " + clientId);
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header
        JLabel titleLabel = new JLabel("SongSong Daemon - " + clientId);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Status panel
        JPanel statusPanel = new JPanel(new GridLayout(3, 1));
        statusLabel = new JLabel("Status: Initializing...");
        connectionCountLabel = new JLabel("Active connections: 0");
        JLabel addressLabel = new JLabel("Address: " + ipAddress + ":" + port);
        statusPanel.add(statusLabel);
        statusPanel.add(connectionCountLabel);
        statusPanel.add(addressLabel);
        
        // File list
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        fileScrollPane.setBorder(BorderFactory.createTitledBorder("Available Files"));
        
        // Control panel
        JPanel controlPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh Files");
        refreshButton.addActionListener(e -> rescanFiles());
        JButton addFileButton = new JButton("Add File");
        addFileButton.addActionListener(e -> addFile());
        controlPanel.add(refreshButton);
        controlPanel.add(addFileButton);
        
        // Log panel
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        // Layout
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(statusPanel, BorderLayout.NORTH);
        centerPanel.add(fileScrollPane, BorderLayout.CENTER);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        mainPanel.add(logScrollPane, BorderLayout.EAST);
        
        frame.add(mainPanel);
        frame.setVisible(true);
        
        // Add shutdown hook to unregister on close
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }
    
    private void scanFiles() {
        File directory = new File("./shared");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            availableFiles.clear();
            fileListModel.clear();
            
            for (File file : files) {
                if (file.isFile()) {
                    availableFiles.put(file.getName(), file);
                    fileListModel.addElement(file.getName());
                }
            }
            
            updateStatus("Found " + availableFiles.size() + " files");
        }
    }
    
    private void rescanFiles() {
        scanFiles();
        try {
            List<String> fileNames = new ArrayList<>(availableFiles.keySet());
            directoryService.updateClientFiles(clientId, fileNames);
        } catch (Exception e) {
            updateStatus("Error updating file list: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void addFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select File to Share");
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                File destFile = new File("./shared/" + selectedFile.getName());
                Files.copy(selectedFile.toPath(), destFile.toPath(), 
                          StandardCopyOption.REPLACE_EXISTING);
                rescanFiles();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error adding file: " + e.getMessage(), 
                                             "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            updateStatus("Server socket opened on port " + port);
            
            new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        updateConnectionCount(1);
                        fragmentExecutor.submit(() -> handleClientRequest(socket));
                    } catch (IOException e) {
                        if (running) {
                            updateStatus("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            updateStatus("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleClientRequest(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            
            // Read request type
            String requestType = dis.readUTF();
            
            if (requestType.equals("FILE_INFO")) {
                handleFileInfoRequest(dis, dos);
            } else if (requestType.equals("DOWNLOAD_FRAGMENT")) {
                handleFragmentRequest(dis, dos);
            } else {
                updateStatus("Unknown request type: " + requestType);
            }
            
            socket.close();
            updateConnectionCount(-1);
        } catch (IOException e) {
            updateStatus("Error handling client request: " + e.getMessage());
            updateConnectionCount(-1);
        }
    }
    
    private void handleFileInfoRequest(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        File file = availableFiles.get(fileName);
        
        if (file != null && file.exists()) {
            dos.writeBoolean(true);
            dos.writeLong(file.length());
        } else {
            dos.writeBoolean(false);
        }
    }
    
    private void handleFragmentRequest(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        long startByte = dis.readLong();
        long endByte = dis.readLong();
        boolean useCompression = dis.readBoolean();
        
        File file = availableFiles.get(fileName);
        
        if (file != null && file.exists()) {
            dos.writeBoolean(true);
            
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long fragmentSize = endByte - startByte + 1;
                byte[] buffer = new byte[(int)fragmentSize];
                
                raf.seek(startByte);
                int bytesRead = raf.read(buffer);
                
                if (useCompression) {
                    byte[] compressedData = compressData(buffer, 0, bytesRead);
                    dos.writeInt(compressedData.length);
                    dos.write(compressedData);
                } else {
                    dos.writeInt(bytesRead);
                    dos.write(buffer, 0, bytesRead);
                }
                
                updateStatus("Sent fragment of " + fileName + " (" + startByte + "-" + endByte + ")");
            }
        } else {
            dos.writeBoolean(false);
            updateStatus("File not found: " + fileName);
        }
    }
    
    private byte[] compressData(byte[] data, int offset, int length) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data, offset, length);
        }
        return baos.toByteArray();
    }
    
    private void sendHeartbeat() {
        try {
            directoryService.heartbeat(clientId);
            directoryService.updateClientLoad(clientId, currentConnections);
        } catch (Exception e) {
            updateStatus("Error sending heartbeat: " + e.getMessage());
        }
    }
    
    private synchronized void updateConnectionCount(int delta) {
        currentConnections += delta;
        SwingUtilities.invokeLater(() -> {
            connectionCountLabel.setText("Active connections: " + currentConnections);
        });
    }
    
    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: " + status);
            System.out.println("[" + clientId + "] " + status);
        });
    }
    
    private void shutdown() {
        try {
            running = false;
            updateStatus("Shutting down...");
            
            // Unregister from directory
            directoryService.unregisterClient(clientId);
            
            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // Shutdown executors
            fragmentExecutor.shutdown();
            scheduler.shutdown();
            
            updateStatus("Shutdown complete");
        } catch (Exception e) {
            updateStatus("Error during shutdown: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // Simple dialog to get client ID and directory host
        JTextField clientIdField = new JTextField("Client1");
        JTextField directoryHostField = new JTextField("localhost");
        
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Client ID:"));
        panel.add(clientIdField);
        panel.add(new JLabel("Directory Host:"));
        panel.add(directoryHostField);
        
        int result = JOptionPane.showConfirmDialog(null, panel, "Daemon Setup", 
                                                 JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String clientId = clientIdField.getText();
            String directoryHost = directoryHostField.getText();
            
            if (clientId.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "Client ID cannot be empty", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            new Daemon(clientId, directoryHost);
        }
    }
}