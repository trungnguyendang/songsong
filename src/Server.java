import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
    public static void main(String[] args) {
        try {
            // Khởi tạo registry trên cổng 1099
            Registry registry = LocateRegistry.createRegistry(1099);

            // Khởi tạo đối tượng remote
            ClientImpl c = new ClientImpl();

            // Bind đối tượng vào registry
            registry.rebind("Client", c);

            System.out.println("Server is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
