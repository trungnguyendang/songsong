import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Client {

    // (Phần gửi file, đăng ký,... đã được cài đặt trong code của bạn)

    public static void main(String[] args) {
        try {
            // Kết nối tới RMI registry trên server
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            ClientInterface c = (ClientInterface) registry.lookup("Client");

            final File[] fileToSend = new File[1];

            // Tạo danh sách để lưu các file nhận được
            ArrayList<MyFile> receivedFiles = new ArrayList<>();
            DefaultListModel<String> receivedFileListModel = new DefaultListModel<>();

            // Tạo JFrame chính cho giao diện Swing
            JFrame jFrame = new JFrame("This is Client2");
            jFrame.setSize(550, 900);
            jFrame.setLayout(new BoxLayout(jFrame.getContentPane(), BoxLayout.Y_AXIS));
            jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Tiêu đề
            JLabel jlTitle = new JLabel("Client's File Sender");
            jlTitle.setFont(new Font("Arial", Font.BOLD, 25));
            jlTitle.setBorder(new EmptyBorder(20, 0, 10, 0));
            jlTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Panel đăng ký tên người dùng
            JPanel jpRegister = new JPanel();
            jpRegister.setLayout(new BoxLayout(jpRegister, BoxLayout.Y_AXIS));
            jpRegister.setBorder(new EmptyBorder(20, 0, 10, 0));
            JLabel jlRegister = new JLabel("Enter Your Name:");
            jlRegister.setFont(new Font("Arial", Font.BOLD, 20));
            jlRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
            JTextField tfUserName = new JTextField();
            tfUserName.setMaximumSize(new Dimension(300, 30));
            tfUserName.setAlignmentX(Component.CENTER_ALIGNMENT);
            JButton jbRegister = new JButton("Register");
            jbRegister.setFont(new Font("Arial", Font.BOLD, 16));
            jbRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
            jpRegister.add(jlRegister);
            jpRegister.add(tfUserName);
            jpRegister.add(Box.createRigidArea(new Dimension(0,10)));
            jpRegister.add(jbRegister);

            // Label hiển thị thông tin file được chọn
            JLabel jlFileName = new JLabel("Choose a file to send.");
            jlFileName.setFont(new Font("Arial", Font.BOLD, 20));
            jlFileName.setBorder(new EmptyBorder(20, 0, 0, 0));
            jlFileName.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Panel chứa các nút Send File và Choose File
            JPanel jpButton = new JPanel();
            jpButton.setBorder(new EmptyBorder(20, 0, 10, 0));
            JButton jbSendFile = new JButton("Send File");
            jbSendFile.setPreferredSize(new Dimension(150, 75));
            jbSendFile.setFont(new Font("Arial", Font.BOLD, 20));
            JButton jbChooseFile = new JButton("Choose File");
            jbChooseFile.setPreferredSize(new Dimension(150, 75));
            jbChooseFile.setFont(new Font("Arial", Font.BOLD, 20));
            jpButton.add(jbSendFile);
            jpButton.add(jbChooseFile);

            // Panel và JList hiển thị danh sách các client available
            JLabel jlAvailableClients = new JLabel("List of available Clients:");
            jlAvailableClients.setFont(new Font("Arial", Font.BOLD, 20));
            jlAvailableClients.setBorder(new EmptyBorder(20, 0, 10, 0));
            jlAvailableClients.setAlignmentX(Component.CENTER_ALIGNMENT);
            DefaultListModel<String> clientListModel = new DefaultListModel<>();
            JList<String> jListClients = new JList<>(clientListModel);
            JScrollPane scrollClients = new JScrollPane(jListClients);
            scrollClients.setPreferredSize(new Dimension(300, 100));
            scrollClients.setAlignmentX(Component.CENTER_ALIGNMENT);
            JButton jbRefreshClients = new JButton("Refresh Client List");
            jbRefreshClients.setFont(new Font("Arial", Font.BOLD, 16));
            jbRefreshClients.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Panel và JList để hiển thị danh sách file nhận được (các file có thể tải)
            JLabel jlReceivedFiles = new JLabel("Files received from other users:");
            jlReceivedFiles.setFont(new Font("Arial", Font.BOLD, 20));
            jlReceivedFiles.setBorder(new EmptyBorder(20, 0, 10, 0));
            jlReceivedFiles.setAlignmentX(Component.CENTER_ALIGNMENT);
            JList<String> jListReceivedFiles = new JList<>(receivedFileListModel);
            jListReceivedFiles.setFont(new Font("Arial", Font.PLAIN, 18));
            JScrollPane scrollReceivedFiles = new JScrollPane(jListReceivedFiles);
            scrollReceivedFiles.setPreferredSize(new Dimension(300, 150));
            scrollReceivedFiles.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Panel download (nếu muốn, thêm ô nhập tên file để download)
            JPanel downloadPanel = new JPanel();
            downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.Y_AXIS));
            downloadPanel.setBorder(new EmptyBorder(20, 0, 10, 0));
            JButton jbDownloadFile = new JButton("Download File");
            jbDownloadFile.setFont(new Font("Arial", Font.BOLD, 16));
            jbDownloadFile.setAlignmentX(Component.CENTER_ALIGNMENT);
            downloadPanel.add(Box.createRigidArea(new Dimension(0,10)));
            downloadPanel.add(jbDownloadFile);

            // Action cho nút Register
            jbRegister.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        String clientName = tfUserName.getText().trim();
                        if (clientName.isEmpty()) {
                            JOptionPane.showMessageDialog(jFrame, "Please enter your name!", "Warning", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        String ip = InetAddress.getLocalHost().getHostAddress();
                        c.registerClient(clientName, ip);
                        JOptionPane.showMessageDialog(jFrame, "Registered as " + clientName, "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(jFrame, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            // Action cho nút Choose File: chọn file cần gửi từ "Client/Directory"
            jbChooseFile.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JFileChooser jFileChooser = new JFileChooser(new File("Client/Directory"));
                    jFileChooser.setDialogTitle("Choose a file to send from Client/Directory.");
                    if (jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        fileToSend[0] = jFileChooser.getSelectedFile();
                        if (!fileToSend[0].exists()) {
                            JOptionPane.showMessageDialog(jFrame, "Selected file does not exist in Client/Directory!", "Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            jlFileName.setText("The file you want to send is: " + fileToSend[0].getName());
                        }
                    }
                }
            });

            // Action cho nút Send File: gửi file đến client được chọn
            jbSendFile.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (fileToSend[0] == null) {
                        jlFileName.setText("Please choose a file to send first!");
                    } else {
                        try {
                            String selectedClient = jListClients.getSelectedValue();
                            if (selectedClient == null || selectedClient.trim().isEmpty()) {
                                JOptionPane.showMessageDialog(jFrame, "Please select a recipient client from the list!", "Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            String recipientAddress = c.getClientIP(selectedClient);
                            FileInputStream fis = new FileInputStream(fileToSend[0].getAbsolutePath());
                            byte[] fileBytes = new byte[(int) fileToSend[0].length()];
                            fis.read(fileBytes);
                            String fileName = fileToSend[0].getName();
                            byte[] fileNameBytes = fileName.getBytes();

                            // Kết nối đến daemon của client đích (giả sử daemon chạy trên cổng 5001)
                            Socket socket = new Socket(recipientAddress, 5001);
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            dos.writeInt(fileNameBytes.length);
                            dos.write(fileNameBytes);
                            dos.writeInt(fileBytes.length);
                            dos.write(fileBytes);
                            dos.close();
                            socket.close();
                            jlFileName.setText("File sent successfully to " + selectedClient + "!");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(jFrame, "File sending failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            });

            // Action cho nút Refresh Client List
            jbRefreshClients.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        List<String> clients = c.getClientList();
                        clientListModel.clear();
                        for (String client : clients) {
                            clientListModel.addElement(client);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Action cho nút Download File (sử dụng ô nhập trong downloadPanel)
            jbDownloadFile.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Lấy chỉ số của mục được chọn trong danh sách file nhận được
                    int selectedIndex = jListReceivedFiles.getSelectedIndex();
                    if (selectedIndex < 0) {
                        JOptionPane.showMessageDialog(jFrame, "Please select a file from the list to download!", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    // Lấy đối tượng MyFile tương ứng từ danh sách receivedFiles
                    MyFile selectedFile = receivedFiles.get(selectedIndex);

                    // Xác định thư mục lưu file download
                    File downloadDir = new File("Client/Download");
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }
                    File targetFile = new File(downloadDir, selectedFile.getName());
                    if (targetFile.exists()) {
                        JOptionPane.showMessageDialog(jFrame, "File already exists in Client/Download folder!", "Info", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    new Thread(() -> {
                        DownloadManager.downloadFileParallel(String.valueOf(selectedIndex), targetFile.getAbsolutePath(), c);
                    }).start();

                    // Lưu file xuống ổ đĩa
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(selectedFile.getData());
                        JOptionPane.showMessageDialog(jFrame, "File downloaded successfully:\n" + targetFile.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(jFrame, "Download failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            // Khi chọn một file trong danh sách file nhận được, hiển thị hộp thoại hỏi download
            jListReceivedFiles.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                        int index = jListReceivedFiles.locationToIndex(e.getPoint());
                        if (index >= 0) {
                            MyFile selectedFile = receivedFiles.get(index);
                            int choice = JOptionPane.showConfirmDialog(jFrame,
                                    "Do you want to download " + selectedFile.getName() + "?",
                                    "Download Confirmation", JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                File downloadDir = new File("Client/Download");
                                if (!downloadDir.exists()) {
                                    downloadDir.mkdirs();
                                }
                                File fileToSave = new File(downloadDir, selectedFile.getName());
                                try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
                                    fos.write(selectedFile.getData());
                                    JOptionPane.showMessageDialog(jFrame,
                                            "File downloaded successfully at:\n" + fileToSave.getAbsolutePath(),
                                            "Success", JOptionPane.INFORMATION_MESSAGE);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    JOptionPane.showMessageDialog(jFrame,
                                            "Download failed: " + ex.getMessage(),
                                            "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    }
                }
            });

            // Thêm WindowListener để deregister khi đóng ứng dụng
            jFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    try {
                        String clientName = tfUserName.getText().trim();
                        if (!clientName.isEmpty()) {
                            c.deregisterClient(clientName);
                            System.out.println("Client deregistered: " + clientName);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.exit(0);
                    }
                }
            });

            // Thêm các thành phần vào frame.
            jFrame.add(jlTitle);
            jFrame.add(jpRegister);
            jFrame.add(jlFileName);
            jFrame.add(jpButton);
            jFrame.add(jlAvailableClients);
            jFrame.add(scrollClients);
            jFrame.add(jbRefreshClients);
            jFrame.add(Box.createRigidArea(new Dimension(0,20)));
            jFrame.add(jlReceivedFiles);
            jFrame.add(scrollReceivedFiles);
            jFrame.add(Box.createRigidArea(new Dimension(0,20)));
            jFrame.add(downloadPanel);
            jFrame.setVisible(true);

            // Khởi động TCP server để nhận file từ các client khác (chạy trên cổng 5000)
            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(5000)) {
                    System.out.println("File Receiver (daemon) started on port 5000...");
                    while (true) {
                        Socket socket = serverSocket.accept();
                        DataInputStream dis = new DataInputStream(socket.getInputStream());
                        int fileNameLength = dis.readInt();
                        if (fileNameLength > 0) {
                            byte[] fileNameBytes = new byte[fileNameLength];
                            dis.readFully(fileNameBytes);
                            String fileName = new String(fileNameBytes);
                            int fileContentLength = dis.readInt();
                            if (fileContentLength > 0) {
                                byte[] fileContentBytes = new byte[fileContentLength];
                                dis.readFully(fileContentBytes);
                                int fileId = receivedFiles.size();
                                MyFile newFile = new MyFile(fileId, fileName, fileContentBytes, getFileExtension(fileName));
                                receivedFiles.add(newFile);
                                SwingUtilities.invokeLater(() -> {
                                    receivedFileListModel.addElement(fileName);
                                });
                                System.out.println("Received file: " + fileName);
                            }
                        }
                        socket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }).start();

            // Ban đầu làm mới danh sách client từ server
            List<String> clients = c.getClientList();
            clientListModel.clear();
            for (String client : clients) {
                clientListModel.addElement(client);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getFileExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        } else {
            return "No extension found.";
        }
    }
}
