import java.io.Serializable;

public class ClientInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String clientId;
    private String ipAddress;
    private int port;
    private long lastHeartbeat;
    private int activeConnections = 0;
    
    public ClientInfo(String clientId, String ipAddress, int port) {
        this.clientId = clientId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public int getPort() {
        return port;
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
    
    public int getActiveConnections() {
        return activeConnections;
    }
    
    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }
    
    @Override
    public String toString() {
        return clientId + " (" + ipAddress + ":" + port + ")";
    }
}