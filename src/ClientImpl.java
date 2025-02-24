import java.rmi.*;

public class ClientImpl implements ClientInterface {
    // thực thi phương thức addNumber
    @Override
    public int addNumber(int x, int y) throws RemoteException {
        System.out.println("Client request to add 2 number");
        return (x + y);
    }
}
