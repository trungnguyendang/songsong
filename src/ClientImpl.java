import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ClientImpl extends UnicastRemoteObject implements ClientInterface {
    // Constructor bắt buộc phải có để xử lý RemoteException
    protected ClientImpl() throws RemoteException {
        super();
    }

    // Thực thi phương thức addNumber
    @Override
    public int addNumber(int x, int y) throws RemoteException {
        System.out.println("Client request to add 2 numbers: " + x + " + " + y);
        return x + y;
    }
}
