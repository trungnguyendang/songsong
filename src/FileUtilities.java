import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

public class FileUtilities {
    
    /**
     * Calculate MD5 checksum for a file
     */
    public static String calculateMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Split a file into fragments
     */
    public static List<File> splitFile(File file, int numFragments) throws IOException {
        List<File> fragments = new ArrayList<>();
        
        long fileSize = file.length();
        long fragmentSize = fileSize / numFragments;
        long remainder = fileSize % numFragments;
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            for (int i = 0; i < numFragments; i++) {
                long currentSize = fragmentSize;
                if (i < remainder) {
                    currentSize++;
                }
                
                File fragmentFile = new File(file.getParent(), 
                                           file.getName() + ".part" + i);
                try (FileOutputStream fos = new FileOutputStream(fragmentFile)) {
                    byte[] buffer = new byte[(int) Math.min(currentSize, 8192)];
                    long bytesRemaining = currentSize;
                    
                    while (bytesRemaining > 0) {
                        int bytesToRead = (int) Math.min(bytesRemaining, buffer.length);
                        int bytesRead = raf.read(buffer, 0, bytesToRead);
                        
                        if (bytesRead == -1) {
                            break;
                        }
                        
                        fos.write(buffer, 0, bytesRead);
                        bytesRemaining -= bytesRead;
                    }
                }
                
                fragments.add(fragmentFile);
            }
        }
        
        return fragments;
    }
    
    /**
     * Join file fragments back into a single file
     */
    public static File joinFragments(List<File> fragments, String outputPath) throws IOException {
        File outputFile = new File(outputPath);
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (File fragment : fragments) {
                Files.copy(fragment.toPath(), fos);
            }
        }
        
        return outputFile;
    }
    
    /**
     * Compress data using GZIP
     */
    public static byte[] compressData(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
        }
        return baos.toByteArray();
    }
    
    /**
     * Decompress GZIP data
     */
    public static byte[] decompressData(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Format file size in human-readable format
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}