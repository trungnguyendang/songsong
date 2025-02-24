import java.rmi.*;

public interface ClientInterface extends Remote {
    public int addNumber(int x, int y) throws RemoteException;

}
