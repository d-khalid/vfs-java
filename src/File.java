import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

class File implements FileSystemEntity {
    private String name;
    private List<Integer> blocks;
    private long size;

    private Directory parent;

    public File(String name) {
        this.name = name;
        this.size = 0;
        this.blocks = new ArrayList<>();
    }

    public void delete() {
        FileSystem.allocator.freeBlocks(blocks);
        blocks.clear();
        this.size = 0;
    }

    public void copyTo(String destPath) {}
    public void moveTo(String destPath) {}

    public boolean isDirectory() {
        return false;
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    public Directory getParent() { return parent; }

    public void setParent(Directory parent) { this.parent = parent; }

    public String getFullPath() {
        if (parent == null) return "/" + name;
        return FileSystem.joinPath(parent.getFullPath(), name);
    }

    public byte[] readAllBytes() {
        return readFrom(0, (int)size);
    }

    public byte[] readFrom(int offset, int readSize) {
        if (offset + readSize > this.size) {
            System.out.println("File offset + size goes out of bounds!");
            return null;
        }

        byte[] buffer = new byte[readSize];


        try (RandomAccessFile file = new RandomAccessFile(FileSystem.getOsFilePath().toString(), "r")) {
            int read = 0;
            int currentOffset = offset;

            while (read < readSize) {
                int blockIndex = currentOffset / 1024;
                int offsetInBlock = currentOffset % 1024;
                int blockId = blocks.get(blockIndex);

                int physicalOffset = FileSystem.STARTING_ADDR + (blockId * 1024) + offsetInBlock;
                int chunk = Math.min(readSize - read, 1024 - offsetInBlock);

                file.seek(physicalOffset);

                // Exact reads needed here
                file.readFully(buffer, read, chunk);

                read += chunk;
                currentOffset += chunk;
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

        return buffer;
    }

    // Frees all unneeded blocks
    public void truncate(int newSize) {
        if (newSize >= this.size) return;

        int neededBlocks = (int) Math.ceil((double) newSize / 1024.0);
        if (neededBlocks < blocks.size()) {
            List<Integer> toFree = new ArrayList<>(blocks.subList(neededBlocks, blocks.size()));
            FileSystem.allocator.freeBlocks(toFree);
            blocks.subList(neededBlocks, blocks.size()).clear();
        }
        this.size = newSize;
    }

    // Core method that handles all block allocation and physical writing
    public void writeBytes(byte[] data, int offset) {
        int bytesToWrite = data.length;
        int newSize = Math.max((int)this.size, offset + bytesToWrite);

        int currentBlocks = blocks.size();
        int neededBlocks = (int) Math.ceil((double) newSize / 1024.0);

        // Allocate new blocks if file is growing
        if (neededBlocks > currentBlocks) {
            List<Integer> newBlocks = FileSystem.allocator.allocateBlocks((neededBlocks - currentBlocks) * 1024);
            if (newBlocks == null) {
                System.out.println("Failed to allocate blocks for write operation.");
                return;
            }
            blocks.addAll(newBlocks);
        }

        try (RandomAccessFile file = new RandomAccessFile(FileSystem.getOsFilePath().toString(), "rw")) {
            int written = 0;
            int currentOffset = offset;

            while (written < bytesToWrite) {
                int blockIndex = currentOffset / 1024;
                int offsetInBlock = currentOffset % 1024;
                int blockId = blocks.get(blockIndex);

                int physicalOffset = FileSystem.STARTING_ADDR + (blockId * 1024) + offsetInBlock;
                int chunk = Math.min(bytesToWrite - written, 1024 - offsetInBlock);

                file.seek(physicalOffset);
                file.write(data, written, chunk);

                written += chunk;
                currentOffset += chunk;
            }

            this.size = newSize;
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    // String overload, appends to the end
    void write(String data) {
        write(data, (int)this.size);
    }

    // String overload, writes at specific offset
    public void write(String data, int offset) {
        writeBytes(data.getBytes(), offset);
    }

    // Moves a chunk of data from one location to another within the file
    public void moveInFile(int startOffset, int size, int targetOffset) {
        if (startOffset + size > this.size) {
            System.out.println("Source region goes out of bounds!");
            return;
        }

        // Read data into memory first
        byte[] buffer = readFrom(startOffset, size);
        if (buffer == null) return;

        // Delegate the physical writing to the core method
        writeBytes(buffer, targetOffset);

        System.out.println("Successfully moved " + size + " bytes to offset " + targetOffset);
    }





}
