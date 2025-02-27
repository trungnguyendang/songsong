import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
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

    // Hàm gửi ping (nếu cần sử dụng).
    public static void sendPing(String ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            System.out.println("Ping sent to " + ip);
        } catch (Exception e) {
            System.err.println("Ping failed: " + e);
        }
    }

    public static void main(String[] args) {
        try {
            // Kết nối tới RMI registry trên server
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            ClientInterface c = (ClientInterface) registry.lookup("Client");

            final File[] fileToSend = new File[1];

            // Tạo JFrame chính cho giao diện Swing
            JFrame jFrame = new JFrame("This is Client");
            jFrame.setSize(550, 750);
            jFrame.setLayout(new BoxLayout(jFrame.getContentPane(), BoxLayout.Y_AXIS));
            jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Tiêu đề cho file sender
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

            // Panel và JList để hiển thị danh sách các client available
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

            // Action cho nút Register: đăng ký tên của người dùng lên server.
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

            // Action cho nút Choose File
            jbChooseFile.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JFileChooser jFileChooser = new JFileChooser();
                    jFileChooser.setDialogTitle("Choose a file to send.");
                    if (jFileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        fileToSend[0] = jFileChooser.getSelectedFile();
                        jlFileName.setText("The file you want to send is: " + fileToSend[0].getName());
                    }
                }
            });

            // Action cho nút Send File
            jbSendFile.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (fileToSend[0] == null) {
                        jlFileName.setText("Please choose a file to send first!");
                    } else {
                        try {
                            // Lấy tên client được chọn từ danh sách
                            String selectedClient = jListClients.getSelectedValue();
                            if(selectedClient == null || selectedClient.trim().isEmpty()){
                                JOptionPane.showMessageDialog(jFrame, "Please select a recipient client from the list!", "Error", JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                            // Lấy địa chỉ IP của client được chọn thông qua RMI
                            String recipientAddress = c.getClientIP(selectedClient);

                            // Đọc nội dung file cần gửi
                            FileInputStream fileInputStream = new FileInputStream(fileToSend[0].getAbsolutePath());
                            byte[] fileBytes = new byte[(int) fileToSend[0].length()];
                            fileInputStream.read(fileBytes);
                            String fileName = fileToSend[0].getName();
                            byte[] fileNameBytes = fileName.getBytes();

                            // Tạo kết nối Socket đến client nhận (theo IP lấy được)
                            Socket socket = new Socket(recipientAddress, 1234);
                            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

                            // Gửi dữ liệu: độ dài tên file, tên file, độ dài file, nội dung file
                            dataOutputStream.writeInt(fileNameBytes.length);
                            dataOutputStream.write(fileNameBytes);
                            dataOutputStream.writeInt(fileBytes.length);
                            dataOutputStream.write(fileBytes);

                            dataOutputStream.close();
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

            //check tên trùng, nếu có thì raise error (để sau)
//            jbRegister.addActionListener(new ActionListener() {
//                @Override
//                public void actionPerformed(ActionEvent actionEvent) {
//                    String userName = tfUserName.getText().trim();
//                    if (userName.isEmpty()) {
//                        JOptionPane.showMessageDialog(jFrame, "Please enter your name!", "Error", JOptionPane.ERROR_MESSAGE);
//                        return;
//                    }
//
//                    // Lấy danh sách client từ server để đảm bảo dữ liệu mới nhất
//                    try {
//                        List<String> currentClients = c.getClientList();
//                        boolean exists = false;
//                        // Kiểm tra xem tên đã có trong danh sách hay chưa
//                        for (String client : currentClients) {
//                            if (client.equalsIgnoreCase(userName)) {
//                                exists = true;
//                                break;
//                            }
//                        }
//                        if (exists) {
//                            JOptionPane.showMessageDialog(jFrame, "Client name already exists!", "Error", JOptionPane.ERROR_MESSAGE);
//                        } else {
//                            // Lấy IP của máy client
//                            String ip = InetAddress.getLocalHost().getHostAddress();
//                            // Đăng ký tên client lên server qua RMI
//                            c.registerClient(userName, ip);
//                            // Refresh danh sách client
//                            currentClients = c.getClientList();
//                            clientListModel.clear();
//                            for (String client : currentClients) {
//                                clientListModel.addElement(client);
//                            }
//                            JOptionPane.showMessageDialog(jFrame, "Registered successfully as " + userName, "Success", JOptionPane.INFORMATION_MESSAGE);
//                        }
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                        JOptionPane.showMessageDialog(jFrame, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
//                    }
//                }
//            });

            // Khi chọn một client từ danh sách, tự động cập nhật vào ô text (ở đây ta dùng tfUserName để hiển thị tên đã chọn – bạn có thể tạo ô riêng nếu muốn)
            jListClients.addListSelectionListener(e -> {
                String selectedClient = jListClients.getSelectedValue();
                if (selectedClient != null) {
                    // Ví dụ: Hiển thị tên client được chọn vào một JOptionPane
                    JOptionPane.showMessageDialog(jFrame, "Selected client: " + selectedClient);
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
            jFrame.setVisible(true);

            // Khởi động TCP server để nhận ping từ các client khác.
            new Thread(Client::startTCPServer).start();

            // Ban đầu làm mới danh sách client
            List<String> clients = c.getClientList();
            clientListModel.clear();
            for (String client : clients) {
                clientListModel.addElement(client);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
