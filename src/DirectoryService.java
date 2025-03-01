import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DirectoryService extends Remote {
    // Client registration
    void registerClient(String clientId, String ipAddress, int port, List<String> fileList) throws RemoteException;
    void unregisterClient(String clientId) throws RemoteException;
    
    // File operations
    void addClientFile(String clientId, String fileName) throws RemoteException;
    void removeClientFile(String clientId, String fileName) throws RemoteException;
    void updateClientFiles(String clientId, List<String> fileList) throws RemoteException;
    
    // Client & file queries
    Map<String, ClientInfo> getClientsWithFile(String fileName) throws RemoteException;
    List<ClientInfo> getOnlineClients() throws RemoteException;
    Set<String> getAllAvailableFiles() throws RemoteException;
    
    // Status updates
    void heartbeat(String clientId) throws RemoteException;
    void logDownloadStart(String clientId, String fileName, List<String> sourceClients) throws RemoteException;
    void logDownloadComplete(String clientId, String fileName, boolean success, long totalTime, 
                            Map<Integer, FragmentStats> fragmentStats) throws RemoteException;
}