import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectoryServiceImpl extends UnicastRemoteObject implements DirectoryService {
    private Map<String, ClientInfo> clientRegistry = new ConcurrentHashMap<>();
    private Map<String, Set<String>> fileToClientsMap = new ConcurrentHashMap<>();
    private List<String> activityLog = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long CLIENT_TIMEOUT_MS = 60000; // 1 minute timeout
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public DirectoryServiceImpl() throws RemoteException {
        super();
        log("Directory Service started");
        scheduler.scheduleAtFixedRate(this::checkClientStatus, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void registerClient(String clientId, String ipAddress, int port, List<String> fileList) 
            throws RemoteException {
        ClientInfo clientInfo = new ClientInfo(clientId, ipAddress, port);
        clientInfo.setLastHeartbeat(System.currentTimeMillis());
        clientRegistry.put(clientId, clientInfo);
        
        // Register all client files
        for (String fileName : fileList) {
            addFileMapping(clientId, fileName);
        }
        
        log("Client connected: " + clientId + " at " + ipAddress + ":" + port + 
            " with " + fileList.size() + " files");
    }

    @Override
    public synchronized void unregisterClient(String clientId) throws RemoteException {
        ClientInfo client = clientRegistry.remove(clientId);
        if (client != null) {
            // Remove client from all file mappings
            for (Map.Entry<String, Set<String>> entry : fileToClientsMap.entrySet()) {
                entry.getValue().remove(clientId);
            }
            
            // Clean up empty file entries
            fileToClientsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            log("Client disconnected: " + clientId);
        }
    }

    @Override
    public void addClientFile(String clientId, String fileName) throws RemoteException {
        if (clientRegistry.containsKey(clientId)) {
            addFileMapping(clientId, fileName);
            log("File added: '" + fileName + "' by client " + clientId);
        } else {
            throw new RemoteException("Client not registered: " + clientId);
        }
    }

    @Override
    public void removeClientFile(String clientId, String fileName) throws RemoteException {
        if (clientRegistry.containsKey(clientId)) {
            Set<String> clients = fileToClientsMap.get(fileName);
            if (clients != null) {
                clients.remove(clientId);
                if (clients.isEmpty()) {
                    fileToClientsMap.remove(fileName);
                }
                log("File removed: '" + fileName + "' from client " + clientId);
            }
        } else {
            throw new RemoteException("Client not registered: " + clientId);
        }
    }

    @Override
    public void updateClientFiles(String clientId, List<String> fileList) throws RemoteException {
        if (clientRegistry.containsKey(clientId)) {
            // Remove client from all existing file mappings
            for (Set<String> clients : fileToClientsMap.values()) {
                clients.remove(clientId);
            }
            
            // Clean up empty file entries
            fileToClientsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            // Add client to new file mappings
            for (String fileName : fileList) {
                addFileMapping(clientId, fileName);
            }
            
            log("Files updated for client " + clientId + ": " + fileList.size() + " files");
            clientRegistry.get(clientId).setLastHeartbeat(System.currentTimeMillis());
        } else {
            throw new RemoteException("Client not registered: " + clientId);
        }
    }

    @Override
    public Map<String, ClientInfo> getClientsWithFile(String fileName) throws RemoteException {
        Map<String, ClientInfo> result = new HashMap<>();
        Set<String> clientIds = fileToClientsMap.get(fileName);
        
        if (clientIds != null) {
            for (String clientId : clientIds) {
                ClientInfo info = clientRegistry.get(clientId);
                if (info != null) {
                    result.put(clientId, info);
                }
            }
        }
        
        return result;
    }

    @Override
    public List<ClientInfo> getOnlineClients() throws RemoteException {
        return new ArrayList<>(clientRegistry.values());
    }

    @Override
    public Set<String> getAllAvailableFiles() throws RemoteException {
        return new HashSet<>(fileToClientsMap.keySet());
    }

    @Override
    public void heartbeat(String clientId) throws RemoteException {
        ClientInfo clientInfo = clientRegistry.get(clientId);
        if (clientInfo != null) {
            clientInfo.setLastHeartbeat(System.currentTimeMillis());
        } else {
            throw new RemoteException("Client not registered: " + clientId);
        }
    }

    @Override
    public void logDownloadStart(String clientId, String fileName, List<String> sourceClients) 
            throws RemoteException {
        StringBuilder sb = new StringBuilder();
        sb.append("Download started: Client ").append(clientId)
          .append(" is downloading '").append(fileName).append("' from ");
        
        for (int i = 0; i < sourceClients.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sourceClients.get(i));
        }
        
        log(sb.toString());
    }

    @Override
    public void logDownloadComplete(String clientId, String fileName, boolean success, long totalTime, 
                                  Map<Integer, FragmentStats> fragmentStats) throws RemoteException {
        StringBuilder sb = new StringBuilder();
        if (success) {
            sb.append("Download completed: Client ").append(clientId)
              .append(" successfully downloaded '").append(fileName)
              .append("' in ").append(totalTime).append("ms");
            
            // Add client to file mapping
            addFileMapping(clientId, fileName);
        } else {
            sb.append("Download failed: Client ").append(clientId)
              .append(" failed to download '").append(fileName).append("'");
        }
        log(sb.toString());
        
        // Log fragment statistics
        for (Map.Entry<Integer, FragmentStats> entry : fragmentStats.entrySet()) {
            FragmentStats stats = entry.getValue();
            log("Fragment " + entry.getKey() + 
                ": from " + stats.sourceClient + 
                ", time: " + stats.downloadTime + "ms, " +
                (stats.success ? "success" : "failed - " + stats.errorMessage));
        }
    }

    public List<String> getActivityLog() {
        return new ArrayList<>(activityLog);
    }
    
    private void addFileMapping(String clientId, String fileName) {
        fileToClientsMap.computeIfAbsent(fileName, k -> new HashSet<>()).add(clientId);
    }
    
    private void log(String message) {
        String logEntry = dateFormat.format(new Date()) + " - " + message;
        activityLog.add(logEntry);
        System.out.println("[Directory] " + logEntry);
    }
    
    private void checkClientStatus() {
        long currentTime = System.currentTimeMillis();
        List<String> inactiveClients = new ArrayList<>();
        
        for (Map.Entry<String, ClientInfo> entry : clientRegistry.entrySet()) {
            if (currentTime - entry.getValue().getLastHeartbeat() > CLIENT_TIMEOUT_MS) {
                inactiveClients.add(entry.getKey());
            }
        }
        
        // Remove inactive clients
        for (String clientId : inactiveClients) {
            try {
                log("Client timed out: " + clientId);
                unregisterClient(clientId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}