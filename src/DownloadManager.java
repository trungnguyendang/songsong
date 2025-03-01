import java.io.*;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DownloadManager {
    // Kích thước mỗi đoạn (fragment) tải về (bytes)
    private static final int FRAGMENT_SIZE = 1024;
    // Số lượng luồng tối đa dùng cho tải song song
    private static final int THREAD_POOL_SIZE = 4;

    /**
     * Tải file theo mô hình song song (load balanced).
     *
     * @param fileName Tên file cần tải (file được nhận diện duy nhất theo tên)
     * @param savePath Đường dẫn file sẽ được lưu lại (ví dụ: "Client/Download/myfile.txt")
     * @param directory Remote object để truy vấn thông tin file từ Directory
     */
    public static void downloadFileParallel(String fileName, String savePath, ClientInterface directory) {
        try {
            // Truy vấn danh sách client có file này từ Directory
            List<String> clientNames = directory.getClientsWithFile(fileName);
            if (clientNames == null || clientNames.isEmpty()) {
                System.out.println("No client found having file: " + fileName);
                return;
            }
            // Lấy danh sách IP tương ứng
            List<String> clientIPs = new ArrayList<>();
            for (String clientName : clientNames) {
                try {
                    clientIPs.add(directory.getClientIP(clientName));
                } catch (RemoteException ex) {
                    System.err.println("Cannot get IP for client: " + clientName);
                }
            }
            if (clientIPs.isEmpty()) {
                System.out.println("No valid IP available for file: " + fileName);
                return;
            }

            // Sử dụng client đầu tiên để truy vấn kích thước file
            long fileSize = queryFileSize(clientIPs.get(0), fileName);
            if (fileSize <= 0) {
                System.out.println("Failed to determine file size for " + fileName);
                return;
            }
            int numFragments = (int) Math.ceil((double)fileSize / FRAGMENT_SIZE);
            System.out.println("Downloading file " + fileName + " (" + fileSize + " bytes) in " + numFragments + " fragments.");

            byte[][] fragments = new byte[numFragments][];
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            List<Future<Boolean>> futures = new ArrayList<>();

            // Phân bổ tải theo vòng tròn (round-robin)
            for (int i = 0; i < numFragments; i++) {
                final int fragIndex = i;
                final long start = fragIndex * FRAGMENT_SIZE;
                final int length = (int)Math.min(FRAGMENT_SIZE, fileSize - start);
                // Chọn client theo vòng tròn
                String sourceIP = clientIPs.get(fragIndex % clientIPs.size());
                Future<Boolean> future = executor.submit(() -> {
                    byte[] data = downloadFragment(sourceIP, fileName, start, length);
                    if (data != null && data.length > 0) {
                        fragments[fragIndex] = data;
                        System.out.println("Fragment " + fragIndex + " downloaded from " + sourceIP);
                        return true;
                    }
                    return false;
                });
                futures.add(future);
            }
            // Đợi tất cả các fragment được tải xong
            for (Future<Boolean> f : futures) {
                f.get();
            }
            executor.shutdown();

            // Ghép các fragment lại thành file hoàn chỉnh
            try (FileOutputStream fos = new FileOutputStream(new File(savePath))) {
                for (int i = 0; i < numFragments; i++) {
                    fos.write(fragments[i]);
                }
            }
            System.out.println("File downloaded successfully at: " + savePath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Phương thức truy vấn kích thước file từ một client qua TCP
    private static long queryFileSize(String ip, String fileName) {
        try (Socket socket = new Socket(ip, 5001);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            // Giao thức: gửi fileName, sau đó start = -1 và length = 0 để truy vấn kích thước
            byte[] fileNameBytes = fileName.getBytes();
            dos.writeInt(fileNameBytes.length);
            dos.write(fileNameBytes);
            dos.writeLong(-1L);
            dos.writeInt(0);
            return dis.readLong();
        } catch (IOException ex) {
            ex.printStackTrace();
            return -1;
        }
    }

    // Phương thức tải một fragment của file từ một client qua TCP
    private static byte[] downloadFragment(String ip, String fileName, long start, int length) {
        try (Socket socket = new Socket(ip, 5001);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            byte[] fileNameBytes = fileName.getBytes();
            dos.writeInt(fileNameBytes.length);
            dos.write(fileNameBytes);
            dos.writeLong(start);
            dos.writeInt(length);
            int bytesRead = dis.readInt();
            if (bytesRead <= 0) return null;
            byte[] data = new byte[bytesRead];
            dis.readFully(data);
            return data;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
