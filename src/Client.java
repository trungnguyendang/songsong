import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class Client extends JFrame {
    private String clientId;
    private String clientFolder;
    private String ipAddress;
    private int port;
    private DirectoryService directoryService;
    private Map<String, File> availableFiles = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService fragmentExecutor = Executors.newCachedThreadPool();
    private int activeConnections = 0;
    private volatile boolean running = true;
    private boolean registeredWithDirectory = false;
    private boolean lastHeartbeatSuccessful = true;
    
    // GUI Components
    private JLabel statusLabel;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JPanel downloadPanel;
    private JComboBox<String> fileComboBox;
    private JList<String> sourcesList;
    private DefaultListModel<String> sourcesListModel;
    private JSpinner fragmentsSpinner;
    private JProgressBar progressBar;
    private JTextArea logArea;
    
    public Client(String clientId, String directoryHost) {
        super("SongSong Client: " + clientId);
        this.clientId = clientId;
        this.clientFolder = "./clients/" + clientId;
        
        try {
            // Make sure client directory exists
            File dir = new File(clientFolder);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Setup connection
            this.ipAddress = InetAddress.getLocalHost().getHostAddress();
            this.port = findAvailablePort();
            
            // Connect to directory service
            Registry registry = LocateRegistry.getRegistry(directoryHost, 1099);
            directoryService = (DirectoryService) registry.lookup("DirectoryService");
            
            // Initialize GUI
            initializeGUI();
            
            // Start server TRƯỚC
            startServer();
            
            // Scan files
            scanFiles();
            
            // ĐĂNG KÝ SAU KHI đã có thông tin file
            List<String> fileNames = new ArrayList<>(availableFiles.keySet());
            directoryService.registerClient(clientId, ipAddress, port, fileNames);
            registeredWithDirectory = true;
            
            // Schedule heartbeat
            scheduler.scheduleAtFixedRate(this::sendHeartbeat, 15, 15, TimeUnit.SECONDS);
            
            // Schedule file rescanning
            scheduler.scheduleAtFixedRate(this::scanFiles, 30, 30, TimeUnit.SECONDS);
            
            log("Client started and registered with directory");
            statusLabel.setText("Connected to Directory: " + directoryHost);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting client: " + e.getMessage(), 
                                         "Client Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
        
        // Handle shutdown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }
    
    private void initializeGUI() {
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Title
        JLabel titleLabel = new JLabel("SongSong Client: " + clientId, JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        
        // Status Panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Initializing...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        JLabel addressLabel = new JLabel("Address: " + ipAddress + ":" + port);
        
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(addressLabel, BorderLayout.EAST);
        
        // Left Panel - Available Files
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("My Files"));
        
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        
        JButton addFileButton = new JButton("Add File");
        addFileButton.addActionListener(e -> addFile());
        
        JButton deleteFileButton = new JButton("Delete File");
        deleteFileButton.addActionListener(e -> deleteSelectedFile());
        
        JButton refreshFilesButton = new JButton("Refresh");
        refreshFilesButton.addActionListener(e -> scanFiles());
        
        JPanel fileButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileButtonsPanel.add(addFileButton);
        fileButtonsPanel.add(deleteFileButton);
        fileButtonsPanel.add(refreshFilesButton);
        
        leftPanel.add(fileScrollPane, BorderLayout.CENTER);
        leftPanel.add(fileButtonsPanel, BorderLayout.SOUTH);
        
        // Center Panel - Download
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Download File"));
        
        downloadPanel = new JPanel();
        downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.Y_AXIS));
        
        JPanel fileSelectPanel = new JPanel(new BorderLayout(5, 5));
        fileSelectPanel.add(new JLabel("File to download:"), BorderLayout.WEST);
        fileComboBox = new JComboBox<>();
        fileComboBox.addActionListener(e -> updateSourcesList());
        fileSelectPanel.add(fileComboBox, BorderLayout.CENTER);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshAvailableFiles());
        fileSelectPanel.add(refreshButton, BorderLayout.EAST);
        
        JPanel sourcesPanel = new JPanel(new BorderLayout(5, 5));
        sourcesPanel.add(new JLabel("Available sources:"), BorderLayout.NORTH);
        sourcesListModel = new DefaultListModel<>();
        sourcesList = new JList<>(sourcesListModel);
        sourcesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane sourcesScrollPane = new JScrollPane(sourcesList);
        sourcesPanel.add(sourcesScrollPane, BorderLayout.CENTER);
        
        JPanel fragmentsPanel = new JPanel(new BorderLayout(5, 5));
        fragmentsPanel.add(new JLabel("Number of fragments:"), BorderLayout.WEST);
        fragmentsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 20, 1));
        fragmentsPanel.add(fragmentsSpinner, BorderLayout.CENTER);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        
        JButton downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> startDownload());
        
        downloadPanel.add(fileSelectPanel);
        downloadPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        downloadPanel.add(sourcesPanel);
        downloadPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        downloadPanel.add(fragmentsPanel);
        downloadPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        downloadPanel.add(progressBar);
        downloadPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        downloadPanel.add(downloadButton);
        
        centerPanel.add(downloadPanel, BorderLayout.CENTER);
        
        // Right Panel - Log
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        
        rightPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // Layout
        JPanel contentPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        contentPanel.add(leftPanel);
        contentPanel.add(centerPanel);
        contentPanel.add(rightPanel);
        
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        setVisible(true);
        
        // Initial refresh
        refreshAvailableFiles();
    }
    
    private void scanFiles() {
        File directory = new File(clientFolder);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        // Get current files
        Set<String> currentFiles = new HashSet<>(availableFiles.keySet());
        
        // Scan for new files
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    availableFiles.put(fileName, file);
                    currentFiles.remove(fileName); // Remove from current list
                    
                    // Update GUI
                    SwingUtilities.invokeLater(() -> {
                        if (!fileListModel.contains(fileName)) {
                            fileListModel.addElement(fileName);
                        }
                    });
                }
            }
        }
        
        // Remove files that no longer exist
        for (String removedFile : currentFiles) {
            availableFiles.remove(removedFile);
            
            // Update GUI
            SwingUtilities.invokeLater(() -> {
                fileListModel.removeElement(removedFile);
            });
        }
        
        // Update directory service only if already registered
        if (registeredWithDirectory) {
            try {
                List<String> fileNames = new ArrayList<>(availableFiles.keySet());
                directoryService.updateClientFiles(clientId, fileNames);
                log("Files updated: " + fileNames.size() + " files");
            } catch (Exception e) {
                log("Error updating file list: " + e.getMessage());
            }
        }
    }
    
    private void refreshAvailableFiles() {
        try {
            Set<String> files = directoryService.getAllAvailableFiles();
            fileComboBox.removeAllItems();
            for (String file : files) {
                fileComboBox.addItem(file);
            }
            updateSourcesList();
        } catch (Exception e) {
            log("Error refreshing available files: " + e.getMessage());
        }
    }
    
    private void updateSourcesList() {
        sourcesListModel.clear();
        String selectedFile = (String) fileComboBox.getSelectedItem();
        if (selectedFile == null) return;
        
        try {
            Map<String, ClientInfo> sources = directoryService.getClientsWithFile(selectedFile);
            for (Map.Entry<String, ClientInfo> entry : sources.entrySet()) {
                // Don't include current client in sources
                if (!entry.getKey().equals(clientId)) {
                    sourcesListModel.addElement(entry.getKey());
                }
            }
        } catch (Exception e) {
            log("Error getting sources: " + e.getMessage());
        }
    }
    
    private void addFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select File to Add");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            File destFile = new File(clientFolder, selectedFile.getName());
            
            try {
                // Copy file to client folder
                Files.copy(selectedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                
                // Update local file list
                availableFiles.put(destFile.getName(), destFile);
                fileListModel.addElement(destFile.getName());
                
                // Update directory
                directoryService.addClientFile(clientId, destFile.getName());
                
                log("File added: " + destFile.getName());
            } catch (Exception e) {
                log("Error adding file: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error adding file: " + e.getMessage(),
                                             "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void deleteSelectedFile() {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a file to delete", 
                                        "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete " + selectedFile + "?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            File fileToDelete = new File(clientFolder, selectedFile);
            if (fileToDelete.exists() && fileToDelete.delete()) {
                // Remove from availableFiles map
                availableFiles.remove(selectedFile);
                
                // Remove from GUI list
                fileListModel.removeElement(selectedFile);
                
                // Update directory
                try {
                    directoryService.removeClientFile(clientId, selectedFile);
                    log("File deleted: " + selectedFile);
                } catch (Exception e) {
                    log("Error updating directory after file deletion: " + e.getMessage());
                }
            } else {
                log("Error deleting file: " + selectedFile);
                JOptionPane.showMessageDialog(this, "Error deleting file", 
                                            "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            log("Server started on port " + port);
            
            new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        incrementConnections(1);
                        fragmentExecutor.submit(() -> handleClientRequest(socket));
                    } catch (IOException e) {
                        if (running) {
                            log("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error starting server: " + e.getMessage(),
                                         "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void handleClientRequest(Socket socket) {
        try {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            
            // Read request type
            String requestType = dis.readUTF();
            
            switch (requestType) {
                case "LIST_FILES":
                    handleListFilesRequest(dos);
                    break;
                case "FILE_INFO":
                    handleFileInfoRequest(dis, dos);
                    break;
                case "DOWNLOAD_FRAGMENT":
                    handleFragmentRequest(dis, dos);
                    break;
                default:
                    log("Unknown request type: " + requestType);
            }
            
            socket.close();
            incrementConnections(-1);
        } catch (IOException e) {
            log("Error handling client request: " + e.getMessage());
            incrementConnections(-1);
        }
    }
    
    private void handleListFilesRequest(DataOutputStream dos) throws IOException {
        List<String> fileNames = new ArrayList<>(availableFiles.keySet());
        dos.writeInt(fileNames.size());
        for (String fileName : fileNames) {
            dos.writeUTF(fileName);
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
                
                log("Sent fragment of " + fileName + " (" + startByte + "-" + endByte + ")");
            }
        } else {
            dos.writeBoolean(false);
            log("File not found: " + fileName);
        }
    }
    
    private byte[] compressData(byte[] data, int offset, int length) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data, offset, length);
        }
        return baos.toByteArray();
    }
    
    private void startDownload() {
        String fileName = (String) fileComboBox.getSelectedItem();
        if (fileName == null || fileName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a file to download", 
                                         "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Check if we already have the file
        if (availableFiles.containsKey(fileName)) {
            JOptionPane.showMessageDialog(this, "You already have this file", 
                                         "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Get selected sources
        List<String> selectedSources = sourcesList.getSelectedValuesList();
        if (selectedSources.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one source", 
                                         "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int numFragments = (Integer) fragmentsSpinner.getValue();
        
        // Disable download panel
        setDownloadPanelEnabled(false);
        progressBar.setValue(0);
        
        // Create DownloadManager
        DownloadManager downloadManager = new DownloadManager(
            clientId, 
            clientFolder, 
            directoryService,
            progress -> SwingUtilities.invokeLater(() -> progressBar.setValue(progress)),
            message -> log(message)
        );
        
        // Start download in background
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return downloadManager.downloadFile(fileName, selectedSources, numFragments);
            }
            
            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(Client.this, 
                            "File downloaded successfully: " + fileName, 
                            "Download Complete", 
                            JOptionPane.INFORMATION_MESSAGE);
                        
                        // Refresh file list
                        scanFiles();
                    } else {
                        JOptionPane.showMessageDialog(Client.this, 
                            "Download failed: " + fileName, 
                            "Download Error", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    log("Download error: " + e.getMessage());
                    JOptionPane.showMessageDialog(Client.this, 
                        "Download error: " + e.getMessage(), 
                        "Download Error", 
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    setDownloadPanelEnabled(true);
                    progressBar.setValue(0);
                }
            }
        }.execute();
    }
    
    private void setDownloadPanelEnabled(boolean enabled) {
        for (Component comp : downloadPanel.getComponents()) {
            if (comp instanceof JComponent) {
                ((JComponent) comp).setEnabled(enabled);
            }
        }
    }
    
    private void sendHeartbeat() {
        try {
            directoryService.heartbeat(clientId);
            
            // Log chỉ khi trạng thái thay đổi từ lỗi sang thành công
            if (!lastHeartbeatSuccessful) {
                log("Reconnected to directory service");
                lastHeartbeatSuccessful = true;
            }
        } catch (Exception e) {
            // Log chỉ khi đây là lỗi đầu tiên
            if (lastHeartbeatSuccessful) {
                log("Error sending heartbeat: " + e.getMessage());
                lastHeartbeatSuccessful = false;
            }
        }
    }
    
    private void incrementConnections(int delta) {
        activeConnections += delta;
        try {
            // Update connection count in directory if this is a daemon
            if (!clientId.startsWith("Downloader")) {
                ClientInfo info = new ClientInfo(clientId, ipAddress, port);
                info.setActiveConnections(activeConnections);
                // In a real implementation, we would update this in the directory
            }
        } catch (Exception e) {
            log("Error updating connection count: " + e.getMessage());
        }
    }
    
    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.out.println("[" + clientId + "] " + message);
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 5000 + new Random().nextInt(1000); // Fallback to random port
        }
    }
    
    private void shutdown() {
        try {
            running = false;
            
            // Unregister from directory
            directoryService.unregisterClient(clientId);
            log("Unregistered from directory");
            
            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // Shutdown executors
            fragmentExecutor.shutdown();
            scheduler.shutdown();
            
            // Xóa thư mục client
            File clientDir = new File(clientFolder);
            if (clientDir.exists()) {
                deleteDirectory(clientDir);
                log("Client directory deleted: " + clientFolder);
            }
            
            log("Shutdown complete");
        } catch (Exception e) {
            log("Error during shutdown: " + e.getMessage());
        } finally {
            // Thay thế System.exit(0) bằng việc đóng frame
            dispose();
        }
    }
    
    // Phương thức hỗ trợ xóa thư mục và tất cả nội dung bên trong
    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Simple dialog to get client ID and directory host
        JTextField clientIdField = new JTextField("Client" + new Random().nextInt(1000));
        JTextField directoryHostField = new JTextField("localhost");
        
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Client ID:"));
        panel.add(clientIdField);
        panel.add(new JLabel("Directory Host:"));
        panel.add(directoryHostField);
        
        int result = JOptionPane.showConfirmDialog(null, panel, "Client Setup", 
                                                 JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String clientId = clientIdField.getText().trim();
            String directoryHost = directoryHostField.getText().trim();
            
            if (clientId.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Client ID cannot be empty", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            SwingUtilities.invokeLater(() -> new Client(clientId, directoryHost));
        }
    }
}