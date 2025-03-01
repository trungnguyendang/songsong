import java.io.Serializable;

public class ClientInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String clientId;
    private String ipAddress;
    private int port;
    private int currentLoad;
    private long lastHeartbeat;
    
    public ClientInfo(String clientId, String ipAddress, int port, int currentLoad) {
        this.clientId = clientId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.currentLoad = currentLoad;
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
    
    public int getCurrentLoad() {
        return currentLoad;
    }
    
    public void setCurrentLoad(int currentLoad) {
        this.currentLoad = currentLoad;
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
    
    @Override
    public String toString() {
        return clientId + " (" + ipAddress + ":" + port + ")";
    }
}