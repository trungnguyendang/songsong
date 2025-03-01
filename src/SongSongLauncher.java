import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class SongSongLauncher {
    private JFrame frame;
    
    public SongSongLauncher() {
        // Create directories if they don't exist
        createDirectories();
        
        // Initialize GUI
        initializeGUI();
    }
    
    private void createDirectories() {
        File[] dirs = {
            new File("./shared"),
            new File("./downloads")
        };
        
        for (File dir : dirs) {
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }
    
    private void initializeGUI() {
        frame = new JFrame("SongSong - Parallel Download System");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Title
        JLabel titleLabel = new JLabel("SongSong Parallel Download System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Description
        JTextArea descriptionArea = new JTextArea(
            "This system enables parallel file downloads from multiple sources.\n" +
            "You can start different components of the system from this launcher."
        );
        descriptionArea.setEditable(false);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setLineWrap(true);
        descriptionArea.setBackground(null);
        descriptionArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        buttonPanel.setMaximumSize(new Dimension(300, 200));
        buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JButton directoryButton = createButton("Start Directory Server", e -> startDirectoryServer());
        JButton daemonButton = createButton("Start Daemon", e -> startDaemon());
        JButton downloaderButton = createButton("Start Downloader", e -> startDownloader());
        
        buttonPanel.add(directoryButton);
        buttonPanel.add(daemonButton);
        buttonPanel.add(downloaderButton);
        
        // Add components to main panel
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(descriptionArea);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(buttonPanel);
        
        frame.add(mainPanel);
        frame.setVisible(true);
    }
    
    private JButton createButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.addActionListener(listener);
        return button;
    }
    
    private void startDirectoryServer() {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "SongSongDirectoryServer");
            pb.redirectErrorStream(true);
            pb.start();
            JOptionPane.showMessageDialog(frame, "Directory Server started successfully", 
                                         "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error starting Directory Server: " + e.getMessage(),
                                         "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startDaemon() {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "Daemon");
            pb.redirectErrorStream(true);
            pb.start();
            JOptionPane.showMessageDialog(frame, "Daemon started successfully", 
                                         "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Error starting Daemon: " + e.getMessage(),
                                         "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startDownloader() {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "Downloader");
            pb.redirectErrorStream(true);
            pb.start();
            JOptionPane.showMessageDialog(frame, "Downloader started successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error starting Downloader: " + e.getMessage(),"Error", JOptionPane.ERROR_MESSAGE);
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