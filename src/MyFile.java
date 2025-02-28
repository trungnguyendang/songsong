

public class MyFile {
    private int id;
    private String name;
    private byte[] data;
    private String fileExtension;

    public MyFile(int id, String name, byte[] data, String fileExtension) {
        this.id = id;
        this.name = name;
        this.data = data;
        this.fileExtension = fileExtension;
    }

    public int getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public byte[] getData() {
        return data;
    }
    public String getFileExtension() {
        return fileExtension;
    }
}
