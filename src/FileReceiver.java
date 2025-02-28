import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class FileReceiver extends Thread {
    private int port;
    private DefaultListModel<String> fileListModel; // Model của JList hiển thị file nhận được
    private ArrayList<MyFile> receivedFiles;        // Danh sách lưu trữ thông tin file

    public FileReceiver(int port, DefaultListModel<String> fileListModel, ArrayList<MyFile> receivedFiles) {
        this.port = port;
        this.fileListModel = fileListModel;
        this.receivedFiles = receivedFiles;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("FileReceiver is listening on port " + port + "...");
            while (true) {
                Socket socket = serverSocket.accept();
                DataInputStream dis = new DataInputStream(socket.getInputStream());

                // Đọc độ dài tên file
                int fileNameLength = dis.readInt();
                if (fileNameLength > 0) {
                    byte[] fileNameBytes = new byte[fileNameLength];
                    dis.readFully(fileNameBytes);
                    String fileName = new String(fileNameBytes);

                    // Đọc độ dài nội dung file
                    int fileContentLength = dis.readInt();
                    if (fileContentLength > 0) {
                        byte[] fileContentBytes = new byte[fileContentLength];
                        dis.readFully(fileContentBytes);

                        int fileId = receivedFiles.size();
                        MyFile newFile = new MyFile(fileId, fileName, fileContentBytes, getFileExtension(fileName));
                        receivedFiles.add(newFile);

                        // Cập nhật model của JList trên EDT
                        SwingUtilities.invokeLater(() -> {
                            fileListModel.addElement(fileName);
                        });
                        System.out.println("Received file: " + fileName);
                    }
                }
                socket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        } else {
            return "No extension";
        }
    }
}
