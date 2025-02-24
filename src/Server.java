import java.rmi.*;
import java.rmi.server.*;

public class Server  {
    public static void main(String[] args) {
        try{
            // khai báo đối tượng thuộc lớp ClientImpl
            ClientImpl c =  new ClientImpl();
            System.out.println("Success!");
            // Khai báo đối tượng c có khả năng remote
            UnicastRemoteObject.exportObject(c);
            // đăng ký đối tượng c lên registry
            Naming.bind("rmi://192.168.0.102/Client", c);
            System.out.println("Registered!");
        }
        catch (Exception e){
            System.out.println("Error:" + e);
        }
    }
}
