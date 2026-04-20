import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class BlockAllocator implements Serializable {

    private BitSet freeSpaceBitmap = new BitSet(10000); // Supports 10,000 blocks
    private final int MAX_BLOCKS = 10000;

    public List<Integer> allocateBlocks(int bytesNeeded) {
        List<Integer> allocatedBlocks = new ArrayList<>();

        // Calculate how many 1024-byte blocks we need
        // Math.ceil ensures even 1 byte gets a full 1 KB block
        int blocksNeeded = (int) Math.ceil((double) bytesNeeded / 1024.0);

        for (int i = 0; i < blocksNeeded; i++) {
            // Find the absolute first available block starting from index 0
            int freeBlock = freeSpaceBitmap.nextClearBit(0);

            // Check if the disk is full
            if (freeBlock >= MAX_BLOCKS) {
                System.err.println("Error: Disk is out of space!");
                return null;
            }

            // Mark it as used
            freeSpaceBitmap.set(freeBlock);

            allocatedBlocks.add(freeBlock);
        }

        return allocatedBlocks;
    }

    public void freeBlocks(List<Integer> blocks)
    {
        for(int i : blocks)
        {
            freeSpaceBitmap.clear(i);
        }
    }

    // For the memory map
    public void printMap() {
        System.out.println("Occupied Blocks:");
        List<Integer> occupied = new ArrayList<>();

        for (int i = 0; i < freeSpaceBitmap.length(); i++) {
            if (freeSpaceBitmap.get(i)) {
                occupied.add(i);
            }
        }

        if (occupied.isEmpty()) {
            System.out.println("None (Disk is entirely free)");
        } else {
            // Print as a  comma-separated list like: [0, 1, 2, 5, 6]
            System.out.println(occupied.toString());
        }
    }

}