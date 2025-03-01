import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

public class DownloadManager {
    private String clientId;
    private String clientFolder;
    private DirectoryService directoryService;
    private ProgressCallback progressCallback;
    private LogCallback logCallback;
    
    public DownloadManager(String clientId, String clientFolder, DirectoryService directoryService,
                         ProgressCallback progressCallback, LogCallback logCallback) {
        this.clientId = clientId;
        this.clientFolder = clientFolder;
        this.directoryService = directoryService;
        this.progressCallback = progressCallback;
        this.logCallback = logCallback;
    }
    
    public boolean downloadFile(String fileName, List<String> selectedSources, int numFragments) throws Exception {
        logCallback.log("Starting download of " + fileName + " from " + selectedSources.size() + " sources");
        
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
            logCallback.log("No available sources for file: " + fileName);
            return false;
        }
        
        // Log download start
        directoryService.logDownloadStart(clientId, fileName, selectedSources);
        
        // Get file size from first source
        ClientInfo firstSource = selectedSourcesMap.values().iterator().next();
        long fileSize = getFileSize(firstSource, fileName);
        
        if (fileSize <= 0) {
            logCallback.log("Could not determine file size");
            return false;
        }
        
        logCallback.log("File size: " + formatFileSize(fileSize));
        
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
                    progressCallback.updateProgress(progress);
                    
                    logCallback.log("Fragment " + i + " downloaded from " + result.sourceClient + 
                        " in " + result.downloadTime + "ms");
                } else {
                    allSuccessful = false;
                    logCallback.log("Fragment " + i + " failed: " + result.errorMessage);
                }
            } catch (Exception e) {
                allSuccessful = false;
                logCallback.log("Error downloading fragment " + i + ": " + e.getMessage());
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
            logCallback.log("Download completed successfully in " + totalTime + "ms");
            return true;
        } else {
            logCallback.log("Download failed");
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
            logCallback.log("Error getting file size: " + e.getMessage());
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
    
    // Inner class for fragment downloads
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
    
    // Callback interfaces
    public interface ProgressCallback {
        void updateProgress(int progress);
    }
    
    public interface LogCallback {
        void log(String message);
    }
    
    // Helper classes
    public static class Fragment {
        public long start;
        public long end;
        
        public Fragment(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}