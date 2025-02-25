import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import java.net.*;


public class Client {

    public static void startTCPServer() {
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("TCP Server started...");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Received ping from: " + socket.getInetAddress().getHostAddress());
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("TCP Server error: " + e);
        }
    }

    public static void sendPing(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            System.out.println("Ping sent to " + ip);
        } catch (Exception e) {
            System.err.println("Ping failed: " + e);
        }
    }
    public static void main(String[] args) {
        try {
            // Kết nối tới registry trên server
            Registry registry = LocateRegistry.getRegistry("192.168.0.102", 1099);

            // Tìm kiếm đối tượng đã đăng ký với tên "Client"
            ClientInterface c = (ClientInterface) registry.lookup("Client");

            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your name: ");
            String clientName = scanner.nextLine();

            String ip = InetAddress.getLocalHost().getHostAddress();
            c.registerClient(clientName, ip);
            new Thread(Client::startTCPServer).start();

            // Hiển thị danh sách Client
            System.out.println("Clients online: " + c.getClientList());

            // Gửi ping đến Client khác
            while (true) {
                System.out.print("Enter Client name to ping: ");
                String target = scanner.nextLine();
                List<String> clients = c.getClientList();

                if (clients.contains(target)) {
                    System.out.println("Pinging " + target + "...");
                    sendPing(ip, 5000);
                } else {
                    System.out.println("Client not found!");
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
