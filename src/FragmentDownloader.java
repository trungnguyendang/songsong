import java.util.*;
import java.util.concurrent.Callable;
import java.io.*;
import java.net.*;
import java.util.zip.GZIPInputStream;

public class FragmentDownloader implements Callable<FragmentResult> {
    private ClientInfo primaryClient;
    private List<ClientInfo> alternateClients;
    private String fileName;
    private long startByte;
    private long endByte;
    private boolean useCompression;
    private int maxRetries = 3;
    private int connectionTimeout = 5000; // 5 seconds
    
    public FragmentDownloader(ClientInfo primaryClient, List<ClientInfo> alternateClients,
                              String fileName, long startByte, long endByte, boolean useCompression) {
        this.primaryClient = primaryClient;
        this.alternateClients = alternateClients;
        this.fileName = fileName;
        this.startByte = startByte;
        this.endByte = endByte;
        this.useCompression = useCompression;
    }
    
    @Override
    public FragmentResult call() {
        Exception lastException = null;
        
        // Try primary client first
        try {
            return downloadFragmentFromClient(primaryClient);
        } catch (Exception e) {
            lastException = e;
            System.out.println("Error downloading from primary client: " + e.getMessage());
        }
        
        // If primary fails, try alternates
        for (ClientInfo client : alternateClients) {
            try {
                return downloadFragmentFromClient(client);
            } catch (Exception e) {
                lastException = e;
                System.out.println("Error downloading from alternate client: " + e.getMessage());
            }
        }
        
        // All attempts failed
        String errorMessage = "Failed to download fragment after all retries";
        if (lastException != null) {
            errorMessage += ": " + lastException.getMessage();
        }
        
        return new FragmentResult(false, null, errorMessage);
    }
    
    private FragmentResult downloadFragmentFromClient(ClientInfo client) throws Exception {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(client.getIpAddress(), client.getPort()), connectionTimeout);
                socket.setSoTimeout(connectionTimeout * 2);
                
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                
                // Send download request
                dos.writeUTF("DOWNLOAD_FRAGMENT");
                dos.writeUTF(fileName);
                dos.writeLong(startByte);
                dos.writeLong(endByte);
                dos.writeBoolean(useCompression);
                
                // Check if file exists
                boolean fileExists = dis.readBoolean();
                if (!fileExists) {
                    socket.close();
                    throw new FileNotFoundException("File not found on client: " + client.getClientId());
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
                
                return new FragmentResult(true, data, null);
            } catch (Exception e) {
                if (attempt == maxRetries - 1) {
                    throw e; // Last attempt, propagate exception
                }
                // Sleep before retry
                Thread.sleep(1000);
            }
        }
        
        throw new IOException("Failed after " + maxRetries + " attempts");
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