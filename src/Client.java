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
import java.util.zip.GZIPInputStream;
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
    
    // GUI Components
    private JLabel statusLabel;
    private DefaultListModel<String> fileListModel;
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
            
            // Scan files
            scanFiles();
            
            // Start server
            startServer();
            
            // Register with directory
            List<String> fileNames = new ArrayList<>(availableFiles.keySet());
            directoryService.registerClient(clientId, ipAddress, port, fileNames);
            
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
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
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
        JList<String> fileList = new JList<>(fileListModel);
        fileList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane fileScrollPane = new JScrollPane(fileList);
        
        JButton addFileButton = new JButton("Add File");
        addFileButton.addActionListener(e -> addFile());
        
        leftPanel.add(fileScrollPane, BorderLayout.CENTER);
        leftPanel.add(addFileButton, BorderLayout.SOUTH);
        
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
        
        // Update directory service
        try {
            List<String> fileNames = new ArrayList<>(availableFiles.keySet());
            directoryService.updateClientFiles(clientId, fileNames);
            log("Files updated: " + fileNames.size() + " files");
        } catch (Exception e) {
            log("Error updating file list: " + e.getMessage());
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
        
        // Start download in background
        new SwingWorker<Boolean, Integer>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return downloadFile(fileName, selectedSources, numFragments);
            }
            
            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int progress = chunks.get(chunks.size() - 1);
                    progressBar.setValue(progress);
                }
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
    
    private boolean downloadFile(String fileName, List<String> selectedSources, int numFragments) throws Exception {
        log("Starting download of " + fileName + " from " + selectedSources.size() + " sources");
        
        // Get client info for selected sources
        Map<String, ClientInfo> allSourcesMap = directoryService.getClientsWithFile(fileName);
        Map<String, ClientInfo> selectedSourcesMap = new HashMap<>();
        
        for (String sourceId : selectedSources) {
            ClientInfo info = allSourcesMap.get(sourceId);
            if (info != null) {
                selectedSourcesMap.put(sourceId, info);
            }
        }
        
        if (selectedSourcesMap.isEmpty()) {
            log("No available sources for file: " + fileName);
            return false;
        }
        
        // Log download start
        directoryService.logDownloadStart(clientId, fileName, selectedSources);
        
        // Get file size from first source
        ClientInfo firstSource = selectedSourcesMap.values().iterator().next();
        long fileSize = getFileSize(firstSource, fileName);
        
        if (fileSize <= 0) {
            log("Could not determine file size");
            return false;
        }
        
        log("File size: " + formatFileSize(fileSize));
        
        // Calculate fragments
        List<Fragment> fragments = calculateFragments(fileSize, numFragments);
        
        // Create output file
        File outputFile = new File(clientFolder, fileName);
        RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
        raf.setLength(fileSize);
        
        // Create download tasks
        ExecutorService executor = Executors.newFixedThreadPool(fragments.size());
        List<Future<FragmentResult>> futures = new ArrayList<>();
        Map<Integer, FragmentStats> fragmentStats = new HashMap<>();
        
        // Distribute fragments among sources (round-robin)
        List<ClientInfo> sourcesList = new ArrayList<>(selectedSourcesMap.values());
        
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            
            // Select source (round-robin)
            ClientInfo source = sourcesList.get(i % sourcesList.size());
            String sourceId = source.getClientId();
            
            // Create download task
            FragmentDownloader downloader = new FragmentDownloader(
                i, sourceId, source, fileName, fragment.start, fragment.end, true);
            
            futures.add(executor.submit(downloader));
        }
        
        // Wait for all fragments and write to file
        long totalBytesDownloaded = 0;
        boolean allSuccessful = true;
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            Future<FragmentResult> future = futures.get(i);
            
            try {
                FragmentResult result = future.get();
                
                fragmentStats.put(i, new FragmentStats(
                    result.sourceClient,
                    result.downloadTime,
                    result.success,
                    result.errorMessage
                ));
                
                if (result.success) {
                    raf.seek(fragment.start);
                    raf.write(result.data);
                    totalBytesDownloaded += result.data.length;
                    
                    // Update progress
                    int progress = (int)((totalBytesDownloaded * 100) / fileSize);
                    final int progressFinal = progress;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progressFinal);
                    });
                    log("Fragment " + i + " downloaded from " + result.sourceClient + 
                        " in " + result.downloadTime + "ms");
                } else {
                    allSuccessful = false;
                    log("Fragment " + i + " failed: " + result.errorMessage);
                }
            } catch (Exception e) {
                allSuccessful = false;
                log("Error downloading fragment " + i + ": " + e.getMessage());
                fragmentStats.put(i, new FragmentStats(
                    "unknown", 0, false, e.getMessage()
                ));
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        raf.close();
        executor.shutdown();
        
        // Log download completion
        directoryService.logDownloadComplete(clientId, fileName, allSuccessful, totalTime, fragmentStats);
        
        if (allSuccessful) {
            log("Download completed successfully in " + totalTime + "ms");
            // Add file to available files
            availableFiles.put(fileName, outputFile);
            return true;
        } else {
            log("Download failed");
            outputFile.delete();
            return false;
        }
    }
    
    private long getFileSize(ClientInfo client, String fileName) {
        try (Socket socket = new Socket(client.getIpAddress(), client.getPort())) {
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            
            dos.writeUTF("FILE_INFO");
            dos.writeUTF(fileName);
            
            boolean exists = dis.readBoolean();
            if (exists) {
                return dis.readLong();
            }
        } catch (IOException e) {
            log("Error getting file size: " + e.getMessage());
        }
        return -1;
    }
    
    private List<Fragment> calculateFragments(long fileSize, int numFragments) {
        List<Fragment> fragments = new ArrayList<>();
        long fragmentSize = fileSize / numFragments;
        long remainder = fileSize % numFragments;
        
        long position = 0;
        for (int i = 0; i < numFragments; i++) {
            long currentSize = fragmentSize + (i < remainder ? 1 : 0);
            if (currentSize > 0) {
                fragments.add(new Fragment(position, position + currentSize - 1));
                position += currentSize;
            }
        }
        
        return fragments;
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
        } catch (Exception e) {
            log("Error sending heartbeat: " + e.getMessage());
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
            
            log("Shutdown complete");
        } catch (Exception e) {
            log("Error during shutdown: " + e.getMessage());
        } finally {
            System.exit(0);
        }
    }
    
    // Inner classes
    private class Fragment {
        public long start;
        public long end;
        
        public Fragment(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
    
    private class FragmentDownloader implements Callable<FragmentResult> {
        private int fragmentId;
        private String sourceId;
        private ClientInfo source;
        private String fileName;
        private long startByte;
        private long endByte;
        private boolean useCompression;
        
        public FragmentDownloader(int fragmentId, String sourceId, ClientInfo source, 
                                String fileName, long startByte, long endByte, boolean useCompression) {
            this.fragmentId = fragmentId;
            this.sourceId = sourceId;
            this.source = source;
            this.fileName = fileName;
            this.startByte = startByte;
            this.endByte = endByte;
            this.useCompression = useCompression;
        }
        
        @Override
        public FragmentResult call() {
            long startTime = System.currentTimeMillis();
            try {
                Socket socket = new Socket(source.getIpAddress(), source.getPort());
                socket.setSoTimeout(10000); // 10 second timeout
                
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                
                // Send fragment request
                dos.writeUTF("DOWNLOAD_FRAGMENT");
                dos.writeUTF(fileName);
                dos.writeLong(startByte);
                dos.writeLong(endByte);
                dos.writeBoolean(useCompression);
                
                // Check if file exists on source
                boolean fileExists = dis.readBoolean();
                if (!fileExists) {
                    long downloadTime = System.currentTimeMillis() - startTime;
                    return new FragmentResult(fragmentId, sourceId, downloadTime, false, null, "File not found on source");
                }
                
                // Read fragment data
                int dataLength = dis.readInt();
                byte[] data = new byte[dataLength];
                dis.readFully(data);
                
                // Decompress if needed
                if (useCompression) {
                    data = decompressData(data);
                }
                
                socket.close();
                
                long downloadTime = System.currentTimeMillis() - startTime;
                return new FragmentResult(fragmentId, sourceId, downloadTime, true, data, null);
                
            } catch (Exception e) {
                long downloadTime = System.currentTimeMillis() - startTime;
                return new FragmentResult(fragmentId, sourceId, downloadTime, false, null, e.getMessage());
            }
        }
        
        private byte[] decompressData(byte[] compressedData) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            try (GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzipIn.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }
            
            return baos.toByteArray();
        }
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