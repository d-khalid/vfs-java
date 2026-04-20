import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;

class Directory implements FileSystemEntity {
    private String name;
    private ArrayList<FileSystemEntity> children = new ArrayList<>();
    private Directory parent;


    public Directory(String name) {
        this.name = name;
    }

    public void delete() {}

    public void setName(String newName) {
        this.name = newName;
    }

    public void addChild(FileSystemEntity fe) {
        children.add(fe);

        fe.setParent(this);

    }

    public boolean removeChild(FileSystemEntity fe) {
        return children.remove(fe);
    }

    public List<FileSystemEntity> getChildren() {
        return children;
    }

    public Directory getParent() {
        return parent;
    }

    public void setParent(Directory parent) {
        this.parent = parent;
    }

    public boolean isDirectory() {
        return true;
    }

    public String getName() {
        return name;
    }

    // Calculated dynamically
    public String getFullPath() {
        if (parent == null) {
            return "/"; // This is the root
        }
        String parentPath = parent.getFullPath();
        return FileSystem.joinPath(parentPath, name);
    }
}