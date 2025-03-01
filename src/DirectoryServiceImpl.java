import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectoryServiceImpl extends UnicastRemoteObject implements DirectoryService {
    private Map<String, ClientInfo> clientRegistry = new ConcurrentHashMap<>();
    private Map<String, Set<String>> fileToClientsMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final long TIMEOUT_MS = 60000; // 1 minute timeout for inactive clients

    public DirectoryServiceImpl() throws RemoteException {
        super();
        // Start health check to detect failed clients
        scheduler.scheduleAtFixedRate(this::checkClientHealth, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void registerClient(String clientId, String ipAddress, int port, List<String> fileList) 
            throws RemoteException {
        
        ClientInfo clientInfo = new ClientInfo(clientId, ipAddress, port, 0);
        clientInfo.setLastHeartbeat(System.currentTimeMillis());
        clientRegistry.put(clientId, clientInfo);
        
        // Register all files for this client
        for (String file : fileList) {
            fileToClientsMap.computeIfAbsent(file, k -> new HashSet<>()).add(clientId);
        }
        
        System.out.println("Client registered: " + clientId + " at " + ipAddress + ":" + port + 
                           " with " + fileList.size() + " files");
    }

    @Override
    public synchronized void unregisterClient(String clientId) throws RemoteException {
        ClientInfo client = clientRegistry.remove(clientId);
        if (client != null) {
            // Remove client from all file mappings
            for (Set<String> clients : fileToClientsMap.values()) {
                clients.remove(clientId);
            }
            
            // Clean up empty file entries
            fileToClientsMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            System.out.println("Client unregistered: " + clientId);
        }
    }

    @Override
    public Map<String, ClientInfo> getClientsWithFile(String filename) throws RemoteException {
        Map<String, ClientInfo> result = new HashMap<>();
        Set<String> clientIds = fileToClientsMap.get(filename);
        
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
    public void updateClientLoad(String clientId, int currentConnections) throws RemoteException {
        ClientInfo clientInfo = clientRegistry.get(clientId);
        if (clientInfo != null) {
            clientInfo.setCurrentLoad(currentConnections);
            clientInfo.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    @Override
    public void updateClientFiles(String clientId, List<String> fileList) throws RemoteException {
        // Remove client from all existing file mappings
        for (Set<String> clients : fileToClientsMap.values()) {
            clients.remove(clientId);
        }
        
        // Add client to new file mappings
        for (String file : fileList) {
            fileToClientsMap.computeIfAbsent(file, k -> new HashSet<>()).add(clientId);
        }
        
        // Update heartbeat
        ClientInfo clientInfo = clientRegistry.get(clientId);
        if (clientInfo != null) {
            clientInfo.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    @Override
    public void heartbeat(String clientId) throws RemoteException {
        ClientInfo clientInfo = clientRegistry.get(clientId);
        if (clientInfo != null) {
            clientInfo.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    @Override
    public List<ClientInfo> getAvailableClients() throws RemoteException {
        return new ArrayList<>(clientRegistry.values());
    }

    @Override
    public long getFileSize(String filename) throws RemoteException {
        // For this implementation, we'll get the size from any client that has the file
        Set<String> clientIds = fileToClientsMap.get(filename);
        if (clientIds != null && !clientIds.isEmpty()) {
            // Size lookup would require client communication - for simplicity, we'll
            // just return a placeholder value that would be replaced with real implementation
            return -1; // In a real implementation, we would query a client for the file size
        }
        throw new RemoteException("File not found: " + filename);
    }

    @Override
    public int getClientCount() throws RemoteException {
        return clientRegistry.size();
    }

    private void checkClientHealth() {
        long currentTime = System.currentTimeMillis();
        List<String> inactiveClients = new ArrayList<>();
        
        for (Map.Entry<String, ClientInfo> entry : clientRegistry.entrySet()) {
            if (currentTime - entry.getValue().getLastHeartbeat() > TIMEOUT_MS) {
                inactiveClients.add(entry.getKey());
            }
        }
        
        // Remove inactive clients
        for (String clientId : inactiveClients) {
            try {
                System.out.println("Removing inactive client: " + clientId);
                unregisterClient(clientId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}