import java.util.*;
import java.rmi.*;

public class Client {
    private static Scanner input_val;

    public static void main(String[] args) {
        try{
            System.out.println("Finding");
            // khai bao doi tuong c tim duoc o registry
            ClientInterface c = (ClientInterface) Naming.lookup("rmi://192.168.0.102/Client");
            // Nhap 2 gia tri x va y
            input_val = new Scanner(System.in);
            System.out.println("Enter X:");
            int x = input_val.nextInt();
            System.out.println("Enter Y:");
            int y = input_val.nextInt();
            // hien thi ket qua
            System.out.println("Result:" + c.addNumber(x, y));
        }
        catch (Exception e){
            System.out.println("Error:" + e);
        }
    }
}
