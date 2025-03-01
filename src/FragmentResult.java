public class FragmentResult {
    public boolean success;
    public byte[] data;
    public String errorMessage;
    
    public FragmentResult(boolean success, byte[] data, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }
}