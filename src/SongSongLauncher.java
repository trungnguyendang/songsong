import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SongSongLauncher extends JFrame {
    
    public SongSongLauncher() {
        super("SongSong Parallel Download System");
        
        // Create directories if they don't exist
        File clientsDir = new File("./clients");
        if (!clientsDir.exists()) {
            clientsDir.mkdirs();
        }
        
        // Initialize GUI
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("SongSong Parallel Download System", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        
        // Description
        JTextArea descriptionArea = new JTextArea(
            "This system enables parallel file downloads from multiple sources.\n" +
            "First start the Directory Server, then start one or more Clients."
        );
        descriptionArea.setEditable(false);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setLineWrap(true);
        descriptionArea.setBackground(null);
        descriptionArea.setFont(new Font("Arial", Font.PLAIN, 14));
        descriptionArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        
        JButton directoryButton = new JButton("Start Directory Server");
        directoryButton.setFont(new Font("Arial", Font.BOLD, 16));
        directoryButton.addActionListener(e -> startDirectoryServer());
        
        JButton clientButton = new JButton("Start Client");
        clientButton.setFont(new Font("Arial", Font.BOLD, 16));
        clientButton.addActionListener(e -> startClient());
        
        buttonPanel.add(directoryButton);
        buttonPanel.add(clientButton);
        
        // Layout
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        mainPanel.add(descriptionArea, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void startDirectoryServer() {
        try {
            SwingUtilities.invokeLater(() -> new DirectoryServer());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting Directory Server: " + e.getMessage(),
                                         "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startClient() {
        try {
            // Show client setup dialog to get clientId and directoryHost
            JTextField clientIdField = new JTextField("Client" + (int)(Math.random() * 1000));
            JTextField directoryHostField = new JTextField("localhost");
            
            JPanel panel = new JPanel(new GridLayout(0, 1));
            panel.add(new JLabel("Client ID:"));
            panel.add(clientIdField);
            panel.add(new JLabel("Directory Host:"));
            panel.add(directoryHostField);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Client Setup", 
                                                     JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (result == JOptionPane.OK_OPTION) {
                String clientId = clientIdField.getText().trim();
                String directoryHost = directoryHostField.getText().trim();
                
                if (clientId.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Client ID cannot be empty", 
                                                 "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                SwingUtilities.invokeLater(() -> new Client(clientId, directoryHost));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error starting Client: " + e.getMessage(),
                                         "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new SongSongLauncher());
    }
}