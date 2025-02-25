import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        try {
            // Kết nối tới registry trên server
            Registry registry = LocateRegistry.getRegistry("192.168.0.102", 1099);

            // Tìm kiếm đối tượng đã đăng ký với tên "Client"
            ClientInterface c = (ClientInterface) registry.lookup("Client");

            // Nhập giá trị x và y từ bàn phím
            Scanner input_val = new Scanner(System.in);
            System.out.print("Enter X: ");
            int x = input_val.nextInt();
            System.out.print("Enter Y: ");
            int y = input_val.nextInt();

            // Hiển thị kết quả
            System.out.println("Result: " + c.addNumber(x, y));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
