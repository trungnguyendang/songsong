import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class Downloader {
    private DirectoryService directoryService;
    private String clientId;
    private String downloadDirectory = "./downloads";
    private boolean useCompression = true;
    
    // GUI components
    private JFrame frame;
    private JTextField fileNameField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JSpinner sourcesSpinner;
    private JCheckBox compressionCheckbox;
    
    public Downloader(String clientId, String directoryHost) {
        this.clientId = clientId;
        
        try {
            // Connect to directory service
            Registry registry = LocateRegistry.getRegistry(directoryHost, 1099);
            directoryService = (DirectoryService) registry.lookup("DirectoryService");
            
            // Create download directory
            File dir = new File(downloadDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // Initialize GUI
            initializeGUI();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error connecting to directory: " + e.getMessage(), 
                                         "Connection Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initializeGUI() {
        frame = new JFrame("SongSong Downloader - " + clientId);
        frame.setSize(700, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // North panel - Title
        JLabel titleLabel = new JLabel("SongSong Parallel File Downloader");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        
        // Center panel - Download controls
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        
        formPanel.add(new JLabel("File Name:"));
        fileNameField = new JTextField(20);
        formPanel.add(fileNameField);
        
        formPanel.add(new JLabel("Number of Sources:"));
        SpinnerNumberModel sourcesModel = new SpinnerNumberModel(4, 1, 16, 1);
        sourcesSpinner = new JSpinner(sourcesModel);
        formPanel.add(sourcesSpinner);
        
        formPanel.add(new JLabel("Use Compression:"));
        compressionCheckbox = new JCheckBox("", useCompression);
        compressionCheckbox.addActionListener(e -> useCompression = compressionCheckbox.isSelected());
        formPanel.add(compressionCheckbox);
        
        centerPanel.add(formPanel, BorderLayout.NORTH);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        centerPanel.add(progressBar, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Ready");
        centerPanel.add(statusLabel, BorderLayout.SOUTH);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> startDownload());
        buttonPanel.add(downloadButton);
        
        // File list panel
        DefaultListModel<String> availableFilesModel = new DefaultListModel<>();
        JList<String> availableFilesList = new JList<>(availableFilesModel);
        JScrollPane filesScrollPane = new JScrollPane(availableFilesList);
        filesScrollPane.setBorder(BorderFactory.createTitledBorder("Available Files"));
        
        // Refresh files button
        JButton refreshButton = new JButton("Refresh File List");
        refreshButton.addActionListener(e -> {
            try {
                Set<String> uniqueFiles = new HashSet<>();
                List<ClientInfo> clients = directoryService.getAvailableClients();
                
                for (ClientInfo client : clients) {
                    try {
                        Socket socket = new Socket(client.getIpAddress(), client.getPort());
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        
                        dos.writeUTF("LIST_FILES");
                        
                        int fileCount = dis.readInt();
                        for (int i = 0; i < fileCount; i++) {
                            uniqueFiles.add(dis.readUTF());
                        }
                        
                        socket.close();
                    } catch (Exception ex) {
                        System.out.println("Error connecting to client: " + client.getClientId());
                    }
                }
                
                availableFilesModel.clear();
                for (String file : uniqueFiles) {
                    availableFilesModel.addElement(file);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Error refreshing file list: " + ex.getMessage(),
                                             "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Double-click file to select it
        availableFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedFile = availableFilesList.getSelectedValue();
                    if (selectedFile != null) {
                        fileNameField.setText(selectedFile);
                    }
                }
            }
        });
        
        // Layout
        JPanel eastPanel = new JPanel(new BorderLayout());
        eastPanel.add(filesScrollPane, BorderLayout.CENTER);
        eastPanel.add(refreshButton, BorderLayout.SOUTH);
        
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(eastPanel, BorderLayout.EAST);
        
        frame.add(mainPanel);
        frame.setVisible(true);
    }
    
    private void startDownload() {
        String fileName = fileNameField.getText().trim();
        if (fileName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please enter a file name", 
                                         "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int numSources = (Integer) sourcesSpinner.getValue();
        
        new SwingWorker<File, Integer>() {
            @Override
            protected File doInBackground() throws Exception {
                return downloadFile(fileName, numSources, useCompression);
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
                    File downloadedFile = get();
                    if (downloadedFile != null) {
                        statusLabel.setText("Download completed: " + downloadedFile.getAbsolutePath());
                        JOptionPane.showMessageDialog(frame, "File downloaded successfully to: " + 
                                                    downloadedFile.getAbsolutePath(),
                                                    "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Download failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame, "Download failed: " + e.getMessage(),
                                                "Download Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
                progressBar.setValue(0);
            }
        }.execute();
    }
    
    public File downloadFile(String fileName, int numSources, boolean useCompression) throws Exception {
        statusLabel.setText("Starting download of " + fileName);
        progressBar.setValue(0);
        
        // Get clients with the file
        Map<String, ClientInfo> sourceClients = directoryService.getClientsWithFile(fileName);
        if (sourceClients.isEmpty()) {
            throw new Exception("No clients have the requested file");
        }
        
        // Get file info from first client
        ClientInfo firstClient = sourceClients.values().iterator().next();
        FileInfo fileInfo = getFileInfo(firstClient, fileName);
        if (fileInfo == null || !fileInfo.exists) {
            throw new Exception("File not found on any client");
        }
        
        long fileSize = fileInfo.size;
        statusLabel.setText("File size: " + formatFileSize(fileSize));
        
        // Determine fragment count based on available sources
        int actualSources = Math.min(numSources, sourceClients.size());
        List<Fragment> fragments = calculateFragments(fileSize, actualSources);
        
        // Create output file
        File outputFile = new File(downloadDirectory, fileName);
        RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
        raf.setLength(fileSize);
        
        // Create download tasks
        ExecutorService executor = Executors.newFixedThreadPool(actualSources);
        List<Future<FragmentResult>> downloadTasks = new ArrayList<>();
        
        List<ClientInfo> clientList = new ArrayList<>(sourceClients.values());
        // Sort clients by load (fewer connections first)
        Collections.sort(clientList, Comparator.comparingInt(ClientInfo::getCurrentLoad));
        
        // Assign fragments to clients and create download tasks
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            // Select client (round-robin among available clients)
            ClientInfo client = clientList.get(i % clientList.size());
            
            // Create alternative client list (for failover)
            List<ClientInfo> alternates = new ArrayList<>(clientList);
            alternates.remove(client);
            
            FragmentDownloader downloader = new FragmentDownloader(
                client, alternates, fileName, fragment.start, fragment.end, useCompression);
            downloadTasks.add(executor.submit(downloader));
        }
        
        // Wait for all fragments and write to file
        final long[] totalBytesDownloadedRef = {0}; 
        boolean success = true;
        
        for (int i = 0; i < fragments.size(); i++) {
            Fragment fragment = fragments.get(i);
            Future<FragmentResult> future = downloadTasks.get(i);
            
            try {
                FragmentResult result = future.get();
                if (result.success) {
                    raf.seek(fragment.start);
                    raf.write(result.data);
                    totalBytesDownloadedRef[0] += result.data.length;
                    
                    // Update progress
                    final int progress = (int) ((totalBytesDownloadedRef[0] * 100) / fileSize);
                    final long currentBytes = totalBytesDownloadedRef[0]; // Tạo biến final để dùng trong lambda
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        statusLabel.setText("Downloaded: " + formatFileSize(currentBytes) + 
                                          " / " + formatFileSize(fileSize));
                    });
                } else {
                    success = false;
                    statusLabel.setText("Fragment download failed: " + result.errorMessage);
                }
            } catch (Exception e) {
                success = false;
                statusLabel.setText("Fragment download error: " + e.getMessage());
            }
        }
        
        raf.close();
        executor.shutdown();
        
        if (!success) {
            outputFile.delete();
            throw new Exception("Download failed due to fragment errors");
        }
        
        return outputFile;
    }
    
    private FileInfo getFileInfo(ClientInfo client, String fileName) {
        try {
            Socket socket = new Socket(client.getIpAddress(), client.getPort());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            
            dos.writeUTF("FILE_INFO");
            dos.writeUTF(fileName);
            
            boolean exists = dis.readBoolean();
            long fileSize = exists ? dis.readLong() : 0;
            
            socket.close();
            
            return new FileInfo(exists, fileSize);
        } catch (Exception e) {
            return null;
        }
    }
    
    private List<Fragment> calculateFragments(long fileSize, int fragmentCount) {
        List<Fragment> fragments = new ArrayList<>();
        long fragmentSize = fileSize / fragmentCount;
        long remainder = fileSize % fragmentCount;
        
        long position = 0;
        for (int i = 0; i < fragmentCount; i++) {
            long currentSize = fragmentSize;
            if (i < remainder) {
                currentSize++;
            }
            
            if (currentSize > 0) {
                fragments.add(new Fragment(position, position + currentSize - 1));
                position += currentSize;
            }
        }
        
        return fragments;
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
    
    public static void main(String[] args) {
        // Simple dialog to get client ID and directory host
        JTextField clientIdField = new JTextField("Downloader");
        JTextField directoryHostField = new JTextField("localhost");
        
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Client ID:"));
        panel.add(clientIdField);
        panel.add(new JLabel("Directory Host:"));
        panel.add(directoryHostField);
        
        int result = JOptionPane.showConfirmDialog(null, panel, "Downloader Setup", 
                                                 JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (result == JOptionPane.OK_OPTION) {
            String clientId = clientIdField.getText();
            String directoryHost = directoryHostField.getText();
            
            if (clientId.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "Client ID cannot be empty", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            SwingUtilities.invokeLater(() -> new Downloader(clientId, directoryHost));
        }
    }
    
    // Inner classes
    private static class Fragment {
        public long start;
        public long end;
        
        public Fragment(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
    
    private static class FileInfo {
        public boolean exists;
        public long size;
        
        public FileInfo(boolean exists, long size) {
            this.exists = exists;
            this.size = size;
        }
    }
}