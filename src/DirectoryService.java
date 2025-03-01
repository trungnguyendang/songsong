import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface DirectoryService extends Remote {
    // Client registration methods
    void registerClient(String clientId, String ipAddress, int port, List<String> fileList) throws RemoteException;
    void unregisterClient(String clientId) throws RemoteException;
    
    // File and client discovery methods
    Map<String, ClientInfo> getClientsWithFile(String filename) throws RemoteException;
    List<ClientInfo> getAvailableClients() throws RemoteException;
    
    // Client status updates
    void updateClientLoad(String clientId, int currentConnections) throws RemoteException;
    void updateClientFiles(String clientId, List<String> fileList) throws RemoteException;
    void heartbeat(String clientId) throws RemoteException;
    
    // File information methods
    long getFileSize(String filename) throws RemoteException;
    int getClientCount() throws RemoteException;
}