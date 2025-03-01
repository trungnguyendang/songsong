import java.io.Serializable;

public class FragmentStats implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public String sourceClient;
    public long downloadTime;
    public boolean success;
    public String errorMessage;
    
    public FragmentStats(String sourceClient, long downloadTime, boolean success, String errorMessage) {
        this.sourceClient = sourceClient;
        this.downloadTime = downloadTime;
        this.success = success;
        this.errorMessage = errorMessage;
    }
}