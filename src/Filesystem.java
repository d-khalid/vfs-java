import java.io.*;
import java.nio.file.*;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


class FileSystem implements Serializable {
    private static Path osFilePath;
    private Directory root;
    private Directory workingDir;
    private  HashMap<String, FileSystemEntity> dirEntryTable = new HashMap<>();

    static final int STARTING_ADDR = 65536;
    // Allocates blocks of storage in 1024 byte chunks
    public static  BlockAllocator allocator = new BlockAllocator();

    public FileSystem(String pathToFile) throws InvalidPathException, IOException {
        osFilePath = Path.of(pathToFile);

        if (!Files.exists(osFilePath)) {
            // First boot: Create file and pre-allocate the 64KB superblock
            Files.createFile(osFilePath);
            Files.write(osFilePath, new byte[STARTING_ADDR]);

            root = new Directory("/");
            dirEntryTable.put(root.getFullPath(), root);
            workingDir = root;
        } else {
            // Subsequent boot: Attempt to load metadata
            loadMetadata();

            // In case the file existed but was completely blank
            if (root == null) {
                root = new Directory("/");
                dirEntryTable.put(root.getFullPath(), root);
                workingDir = root;
            }
        }
    }

    public static Path getOsFilePath() {
        return osFilePath;
    }

    public static String joinPath(String p1, String p2) {
        if(p1.isEmpty()) return p2;
        if(p1.charAt(p1.length() - 1) == '/') return p1 + p2;
        return p1 + "/" + p2;
    }

    void createFile(String path) {
        String fullPath = FileSystem.joinPath(workingDir.getFullPath(), path);

        if(dirEntryTable.containsKey(fullPath)) {
            System.out.println("File exists! Nothing created.");
            return;
        }

        // Split the path to separate the parent directory from the new file name
        int lastSlash = path.lastIndexOf('/');
        String parentPathStr = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        // Find the parent directory (default to workingDir if no slashes were in the path)
        Directory parentDir = parentPathStr.isEmpty() ? workingDir : getDir(parentPathStr);

        if (parentDir == null) {
            System.out.println("Error: Parent directory does not exist!");
            return;
        }

        // Create the file and attach it to the correct parent
        File f = new File(fileName);
        f.setParent(parentDir);
        dirEntryTable.put(fullPath, f);
        parentDir.addChild(f);
    }

    void createDir(String path) {
        String fullPath = FileSystem.joinPath(workingDir.getFullPath(), path);

        if(dirEntryTable.containsKey(fullPath)) {
            System.out.println("Directory exists! Nothing created.");
            return;
        }

        // Split the path to separate the parent directory from the new directory name
        int lastSlash = path.lastIndexOf('/');
        String parentPathStr = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        String dirName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        // Find the parent directory
        Directory parentDir = parentPathStr.isEmpty() ? workingDir : getDir(parentPathStr);

        if (parentDir == null) {
            System.out.println("Error: Parent directory does not exist!");
            return;
        }

        // Create the directory and attach it to the correct parent
        Directory d = new Directory(dirName);
        d.setParent(parentDir);
        dirEntryTable.put(fullPath, d);
        parentDir.addChild(d);
    }

    void listAll() {
        for(FileSystemEntity fe : workingDir.getChildren()) {
            if(fe.isDirectory())
            {
                System.out.print(Vfs.ANSI_BLUE + fe.getName() + Vfs.ANSI_RESET + "\t");
            }
            else System.out.print(fe.getName() + "\t");
        }
        System.out.println();
    }

    File getFile(String fName) {
        FileSystemEntity f = dirEntryTable.get(FileSystem.joinPath(workingDir.getFullPath(), fName));

        if(f == null) {
            System.out.println("File doesn't exist!");
            return null;
        }
        if(f.isDirectory()) {
            System.out.println("Name refers to a directory!");
            return null;
        }
        return (File)f;
    }

    Directory getDir(String dirPathOrName) {
        FileSystemEntity d = dirEntryTable.get(FileSystem.joinPath(workingDir.getFullPath(), dirPathOrName));

        if(d == null || !d.isDirectory()) {
            FileSystemEntity fsEntity = dirEntryTable.get(dirPathOrName);
            if(fsEntity == null || !fsEntity.isDirectory()) {
                System.out.println("Directory doesn't exist!");
                return null;
            }
            d = (Directory) fsEntity;
        }
        return (Directory) d;
    }

    void changeDir(String dirPathOrName) {
        if("..".equals(dirPathOrName) && workingDir.getParent() != null)
        {
            workingDir = workingDir.getParent();
            return;
        }

        Directory d = getDir(dirPathOrName);
        if(d != null) workingDir = d;
    }

    void delete(String path) {
        String fullPath = FileSystem.joinPath(workingDir.getFullPath(), path);

        // Look up the entity first
        FileSystemEntity entity = dirEntryTable.get(fullPath);

        if (entity == null) {
            System.out.println("File or directory not found!");
            return;
        }

        // Recursively remove this entity AND all its nested children from the HashMap
        removePathsFromTable(entity);

        // Free its blocks

        entity.delete();

        // Remove it from its parent directory's children list
        Directory parent = entity.getParent();
        if (parent != null) {
            parent.removeChild(entity);
        }
    }

    public void showMemMap() {
        allocator.printMap();
    }
    public Directory getWorkingDir() {
        return workingDir;
    }

    // Recursively remove old paths from the HashMap
    private void removePathsFromTable(FileSystemEntity entity) {
        dirEntryTable.remove(entity.getFullPath());
        if (entity.isDirectory()) {
            for (FileSystemEntity child : ((Directory) entity).getChildren()) {
                removePathsFromTable(child);
            }
        }
    }

    // Recursively add new paths to the HashMap
    private void addPathsToTable(FileSystemEntity entity) {
        dirEntryTable.put(entity.getFullPath(), entity);
        if (entity.isDirectory()) {
            for (FileSystemEntity child : ((Directory) entity).getChildren()) {
                addPathsToTable(child);
            }
        }
    }

    // Moves entities and updates the hashmap
    void move(String srcPath, String destPath) {
        String fullSrc = FileSystem.joinPath(workingDir.getFullPath(), srcPath);
        String fullDest = FileSystem.joinPath(workingDir.getFullPath(), destPath);

        FileSystemEntity entity = dirEntryTable.get(fullSrc);
        if (entity == null) {
            System.out.println("Source not found!");
            return;
        }

        // Remove old paths from lookup map BEFORE changing tree structure
        removePathsFromTable(entity);

        // Remove from old parent
        Directory oldParent = entity.getParent();
        if (oldParent != null) oldParent.removeChild(entity);

        String newParentPath;
        String newName;

        // Check if destination is already an existing directory
        FileSystemEntity existingDest = dirEntryTable.get(fullDest);
        if (existingDest != null && existingDest.isDirectory()) {
            // Move inside the directory, keep original name
            newParentPath = fullDest;
            newName = entity.getName();
        } else {
            // Treat as a rename/move operation
            int destSlash = fullDest.lastIndexOf('/');
            newParentPath = destSlash > 0 ? fullDest.substring(0, destSlash) : "/";
            newName = fullDest.substring(destSlash + 1);
        }

        Directory newParent = (Directory) dirEntryTable.get(newParentPath);
        if (newParent == null) {
            System.out.println("Destination directory not found! Rolling back.");
            if (oldParent != null) oldParent.addChild(entity);
            addPathsToTable(entity); // Rollback map entries
            return;
        }

        // Update structure
        entity.setName(newName);
        newParent.addChild(entity); // Automatically sets parent if your addChild is updated

        // Add new dynamically generated paths to lookup map
        addPathsToTable(entity);
    }

    // FOR SAVING AND LOADING THE METADATA
    public void saveMetadata() {
        try {
            // Serialize objects to an in-memory byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(root);
            oos.writeObject(allocator);
            oos.flush();
            byte[] metaBytes = baos.toByteArray();

            // Ensure it fits in the superblock!
            if (metaBytes.length > STARTING_ADDR - 4) {
                System.err.println("CRITICAL ERROR: Metadata exceeds the 64KB superblock size!");
                return;
            }

            // Write the size, then the data to the very beginning of the file
            try (RandomAccessFile raf = new RandomAccessFile(osFilePath.toFile(), "rw")) {
                raf.seek(0);
                raf.writeInt(metaBytes.length); // Write the size (4 bytes)
                raf.write(metaBytes);           // Write the actual serialized data
            }
            System.out.println("Metadata saved successfully to superblock.");

        } catch (IOException e) {
            System.err.println("Failed to save metadata: " + e.getMessage());
        }
    }

    private void loadMetadata() {
        try (RandomAccessFile raf = new RandomAccessFile(osFilePath.toFile(), "r")) {
            if (raf.length() == 0) return;

            // Read the size of the metadata
            raf.seek(0);
            int metaSize = raf.readInt();

            // If the size is invalid or uninitialized, abort loading
            if (metaSize <= 0 || metaSize > STARTING_ADDR) return;

            // Read the exact byte array length
            byte[] metaBytes = new byte[metaSize];
            raf.readFully(metaBytes);

            // Deserialize back into objects
            ByteArrayInputStream bais = new ByteArrayInputStream(metaBytes);
            ObjectInputStream ois = new ObjectInputStream(bais);

            this.root = (Directory) ois.readObject();
            FileSystem.allocator = (BlockAllocator) ois.readObject();

            // Rebuild the HashMap and reset working directory
            dirEntryTable.clear();
            addPathsToTable(this.root);
            this.workingDir = this.root;

            System.out.println("Metadata loaded successfully from superblock.");

        } catch (EOFException e) {
            // Superblock uninitialized (expected on first boot)
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load metadata: " + e.getMessage());
        }
    }


}
