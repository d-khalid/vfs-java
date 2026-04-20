import java.io.Serializable;

interface FileSystemEntity extends Serializable {
    void delete();
    boolean isDirectory();

    String getName();
    void setName(String newName);

    String getFullPath();

    Directory getParent();
    void setParent(Directory parent);
}