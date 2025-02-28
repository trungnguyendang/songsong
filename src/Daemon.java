import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Daemon {

    // Danh sách file đã nhận
    static ArrayList<MyFile> myFiles = new ArrayList<>();
    static int fileId = 0;

    public static void main(String[] args) {
        try {
            // Tạo GUI để hiển thị file nhận được
            JFrame jFrame = new JFrame("Daemon - File Receiver");
            jFrame.setSize(400, 400);
            jFrame.setLayout(new BoxLayout(jFrame.getContentPane(), BoxLayout.Y_AXIS));
            jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel jPanel = new JPanel();
            jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));
            JScrollPane jScrollPane = new JScrollPane(jPanel);
            jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

            JLabel jlTitle = new JLabel("Daemon's File Receiver");
            jlTitle.setFont(new Font("Arial", Font.BOLD, 25));
            jlTitle.setBorder(new EmptyBorder(20, 0, 10, 0));
            jlTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            jFrame.add(jlTitle);
            jFrame.add(jScrollPane);
            jFrame.setVisible(true);

            // Lắng nghe trên cổng 1234 để nhận file
            ServerSocket serverSocket = new ServerSocket(1234);
            System.out.println("Daemon is listening on port 1234...");

            while (true) {
                try {
                    // Chờ client khác kết nối để gửi file
                    Socket socket = serverSocket.accept();
                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

                    // Đọc độ dài tên file
                    int fileNameLength = dataInputStream.readInt();
                    if (fileNameLength > 0) {
                        byte[] fileNameBytes = new byte[fileNameLength];
                        dataInputStream.readFully(fileNameBytes, 0, fileNameBytes.length);
                        String fileName = new String(fileNameBytes);

                        // Đọc nội dung file
                        int fileContentLength = dataInputStream.readInt();
                        if (fileContentLength > 0) {
                            byte[] fileContentBytes = new byte[fileContentLength];
                            dataInputStream.readFully(fileContentBytes, 0, fileContentBytes.length);

                            // Tạo JPanel để hiển thị file
                            JPanel jpFileRow = new JPanel();
                            jpFileRow.setLayout(new BoxLayout(jpFileRow, BoxLayout.X_AXIS));
                            JLabel jlFileName = new JLabel(fileName);
                            jlFileName.setFont(new Font("Arial", Font.BOLD, 20));
                            jlFileName.setBorder(new EmptyBorder(10, 0, 10, 0));

                            // Đặt tên cho JPanel để khi click vào biết fileId
                            jpFileRow.setName(String.valueOf(fileId));
                            jpFileRow.addMouseListener(getMyMouseListener());
                            jpFileRow.add(jlFileName);

                            jPanel.add(jpFileRow);
                            jFrame.validate();

                            myFiles.add(new MyFile(fileId, fileName, fileContentBytes, getFileExtension(fileName)));
                            fileId++;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lấy phần mở rộng của file (vd: "txt", "png", ...)
    public static String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        } else {
            return "No extension found.";
        }
    }

    // MouseListener để khi click vào file, hỏi người dùng có muốn download hay không
    public static MouseListener getMyMouseListener() {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel jPanel = (JPanel) e.getSource();
                int fileId = Integer.parseInt(jPanel.getName());
                for (MyFile myFile : myFiles) {
                    if (myFile.getId() == fileId) {
                        JFrame jfPreview = createFrame(myFile.getName(), myFile.getData(), myFile.getFileExtension());
                        jfPreview.setVisible(true);
                    }
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {}
            @Override
            public void mouseReleased(MouseEvent e) {}
            @Override
            public void mouseEntered(MouseEvent e) {}
            @Override
            public void mouseExited(MouseEvent e) {}
        };
    }

    // Tạo frame để hỏi người dùng có muốn download file không
    public static JFrame createFrame(String fileName, byte[] fileData, String fileExtension) {
        JFrame jFrame = new JFrame("Daemon's File Downloader");
        jFrame.setSize(400, 400);

        JPanel jPanel = new JPanel();
        jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.Y_AXIS));

        JLabel jlTitle = new JLabel("Daemon's File Downloader");
        jlTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        jlTitle.setFont(new Font("Arial", Font.BOLD, 25));
        jlTitle.setBorder(new EmptyBorder(20, 0, 10, 0));

        JLabel jlPrompt = new JLabel("Download " + fileName + "?");
        jlPrompt.setFont(new Font("Arial", Font.BOLD, 20));
        jlPrompt.setBorder(new EmptyBorder(20, 0, 10, 0));
        jlPrompt.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton jbYes = new JButton("Yes");
        jbYes.setPreferredSize(new Dimension(150, 75));
        jbYes.setFont(new Font("Arial", Font.BOLD, 20));

        JButton jbNo = new JButton("No");
        jbNo.setPreferredSize(new Dimension(150, 75));
        jbNo.setFont(new Font("Arial", Font.BOLD, 20));

        JLabel jlFileContent = new JLabel();
        jlFileContent.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel jpButtons = new JPanel();
        jpButtons.setBorder(new EmptyBorder(20, 0, 10, 0));
        jpButtons.add(jbYes);
        jpButtons.add(jbNo);

        // Nếu file .txt thì hiển thị text, còn lại thì hiển thị dưới dạng ImageIcon
        if (fileExtension.equalsIgnoreCase("txt")) {
            jlFileContent.setText("<html>" + new String(fileData) + "</html>");
        } else {
            jlFileContent.setIcon(new ImageIcon(fileData));
        }

        jbYes.addActionListener(ae -> {
            File fileToDownload = new File(fileName);
            try {
                FileOutputStream fos = new FileOutputStream(fileToDownload);
                fos.write(fileData);
                fos.close();
                jFrame.dispose();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        jbNo.addActionListener(ae -> jFrame.dispose());

        jPanel.add(jlTitle);
        jPanel.add(jlPrompt);
        jPanel.add(jlFileContent);
        jPanel.add(jpButtons);

        jFrame.add(jPanel);
        return jFrame;
    }
}
