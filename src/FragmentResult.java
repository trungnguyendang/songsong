public class FragmentResult {
    public int fragmentId;
    public String sourceClient;
    public long downloadTime;
    public boolean success;
    public byte[] data;
    public String errorMessage;
    
    public FragmentResult(int fragmentId, String sourceClient, long downloadTime, 
                       boolean success, byte[] data, String errorMessage) {
        this.fragmentId = fragmentId;
        this.sourceClient = sourceClient;
        this.downloadTime = downloadTime;
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }
}