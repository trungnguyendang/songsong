import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class ClientImpl extends UnicastRemoteObject implements ClientInterface {

    private Map<String, String> clientRegistry = new HashMap<>();

    public ClientImpl() throws RemoteException {}


    @Override
    public void registerClient(String clientName, String ip) throws RemoteException {
        clientRegistry.put(clientName, ip);
        System.out.println("Client registered: " + clientName + " - " + ip);
    }

    @Override
    public List<String> getClientList() throws RemoteException {
        return new ArrayList<>(clientRegistry.keySet());
    }
}