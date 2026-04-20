import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Vfs {

    private FileSystem fs;
    // Tracks open files: Maps filename to the active File handle
    private Map<String, File> openFiles;
    private Scanner scanner;

    // ANSI Escape Codes for terminal colors
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLUE = "\u001B[1;34m";  // Blue for directories
    public static final String ANSI_GREEN = "\u001B[1;32m"; // Green for the path prompt

    public Vfs(String osFilePath) {
        try {
            fs = new FileSystem(osFilePath);
            openFiles = new HashMap<>();
            scanner = new Scanner(System.in);
            System.out.println("Virtual File System initialized at: " + osFilePath);
        } catch (InvalidPathException | IOException e) {
            System.err.println("Failed to initialize VFS: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() {
        System.out.println("Type 'help' for a list of commands, or 'exit' to quit.");

        while (true) {
            System.out.printf(ANSI_GREEN + "%s> " + ANSI_RESET, fs.getWorkingDir().getFullPath());
            System.out.flush();

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            // Split on spaces, but ONLY if they are followed by an even number of quotes
            String[] args = input.split("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*$)");

            // Remove end quotes from parsed strings
            for (int i = 0; i < args.length; i++) {
                args[i] = args[i].replace("\"", "");
            }

            if (args.length == 0) continue;

            String command = args[0].toLowerCase();

            try {
                processCommand(command, args);
            } catch (Exception e) {
                System.err.println("Error processing command: " + e.getMessage());
            }
        }
    }

    private void processCommand(String command, String[] args) {
        switch (command) {
            case "help":
                printHelp();
                break;
            case "exit":
            case "quit":
                System.out.println("Exiting VFS...");

                // IMPORTANT: Save metadata here
                fs.saveMetadata();

                System.exit(0);
                break;
            case "create":
                if (checkArgs(args, 2)) fs.createFile(args[1]);
                break;
            case "delete":
                if (checkArgs(args, 2)) fs.delete(args[1]);
                break;
            case "mkdir":
                if (checkArgs(args, 2)) fs.createDir(args[1]);
                break;
            case "chdir":
            case "cd":
                if (checkArgs(args, 2)) {
                    fs.changeDir(args[1]);
                }
                break;
            case "move":
                if (checkArgs(args, 3)) fs.move(args[1], args[2]);
                break;
            case "ls":
                fs.listAll();
                break;

            case "write":
                // "write <fName> <text>
                // write <fName> <offset> <text>
                if (checkArgs(args, 4)) {
                    File f = fs.getFile(args[1]);
                    if (f == null) break;

                    f.write(args[3], Integer.parseInt(args[2]));
                }
                else if (checkArgs(args, 3)) {
                    File f = fs.getFile(args[1]);
                    if (f == null) break;

                    f.write(args[2]);
                }

                break;
            case "read":
                // Usage: read <fName> OR read <fName> <start> <size>

                if (checkArgs(args, 4)) {
                    File f = fs.getFile(args[1]);
                    if (f == null) break;

                    System.out.println(new String(
                            f.readFrom(Integer.parseInt(args[2]), Integer.parseInt(args[3])),
                            StandardCharsets.UTF_8)
                    );
                }
                else if (checkArgs(args, 2)) {
                    File f = fs.getFile(args[1]);
                    if (f == null) break;

                    System.out.println(new String(f.readAllBytes(), StandardCharsets.UTF_8));

                }
                break;
            case "move_in_file":
                if (checkArgs(args, 5))
                {
                    File f = fs.getFile(args[1]);
                    f.moveInFile(
                            Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]),
                            Integer.parseInt(args[4])
                            );
                }
                break;
            case "truncate":
                if (checkArgs(args, 3)) {
                    int newSize = Integer.parseInt(args[2]);
                    File f = fs.getFile(args[1]);
                    f.truncate(
                            Integer.parseInt(args[2])
                    );

                    System.out.println("Truncated to: " + newSize + " bytes!");

                }
                break;
            case "memmap":
                fs.showMemMap();
                break;
            default:
                System.out.println("Unknown command: " + command);
                break;
        }
    }

    // Helper to ensure enough arguments are passed
    private boolean checkArgs(String[] args, int expectedLength) {
        return args.length == expectedLength;
    }

    // Helper to fetch an open file and alert the user if it's not open
    private File getOpenFile(String fName) {
        File obj = openFiles.get(fName);
        if (obj == null) {
            System.out.println("Error: File '" + fName + "' is not open. Use 'open " + fName + " <mode>' first.");
        }
        return obj;
    }

    private void printHelp() {
        System.out.println("=== VFS Commands ===");
        System.out.println("create <fName>                      - Create a file");
        System.out.println("delete <fName>                      - Delete a file or directory");
        System.out.println("mkdir <dirName>                     - Create a directory");
        System.out.println("cd <dirName>                        - Change directory");
        System.out.println("move <source> <target>              - Move a file");
//        System.out.println("open <fName> <mode>                 - Open a file (e.g., r, w, rw)");
//        System.out.println("close <fName>                       - Close an open file");
        System.out.println("write <fName> <text>                - Write text to file");
        System.out.println("write <fName> <offset> <text>       - Write text at a specific offset");
        System.out.println("read <fName>                        - Read entire file");
        System.out.println("read <fName> <start> <size>         - Read specific bytes");
        System.out.println("move_in_file <fName> <start> <size> <target> - Move data within file");
        System.out.println("truncate <fName> <maxSize>          - Truncate open file to max size");
        System.out.println("memmap                              - Show memory map");
        System.out.println("exit                                - Quit VFS");
    }

    public static void main(String[] args) {
        if(args.length != 1)
        {
            System.out.print("Usage: vfs <dat_file_name>\tThe file to use for the virtual file system\n");
            return;
        }

        Vfs cli = new Vfs(args[0]);
        cli.start();
    }
}