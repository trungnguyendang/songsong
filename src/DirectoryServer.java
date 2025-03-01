import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class DirectoryServer {
    public static void main(String[] args) {
        try {
            // Set security manager if needed
            // if (System.getSecurityManager() == null) {
            //     System.setSecurityManager(new SecurityManager());
            // }
            
            // Create and export the directory service
            DirectoryService directoryService = new DirectoryServiceImpl();
            
            // Create or get the registry
            Registry registry = LocateRegistry.createRegistry(1099);
            
            // Bind the directory service
            registry.rebind("DirectoryService", directoryService);
            
            System.out.println("Directory Server is running...");
        } catch (Exception e) {
            System.err.println("Directory Server exception:");
            e.printStackTrace();
        }
    }
}