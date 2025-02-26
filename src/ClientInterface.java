import java.rmi.*;
import java.util.List;

public interface ClientInterface extends Remote {
//    public int addNumber(int x, int y) throws RemoteException;
    void registerClient(String clientName, String ip) throws RemoteException;
    List<String> getClientList() throws RemoteException;
    String getClientIP(String clientName) throws RemoteException;
}
