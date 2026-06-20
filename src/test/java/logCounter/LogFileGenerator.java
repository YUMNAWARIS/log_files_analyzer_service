package test.java.logCounter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

/* Test utility: generates log files with a known, reproducible number of
 * INFO / WARN / ERROR entries so that LogCounter results can be verified
 * against an expected count. */
public class LogFileGenerator {

    private final static long SEED = 127493;

    // ####################
    // # LogFileGenerator #
    // ####################

    public static String[] generateLogFiles(String path, int num_files, long seed, int num_info, int num_warn,
            int num_error) {

        // Init counter for each log category
        int remainingInfo = num_info;
        int remainingWarn = num_warn;
        int remainingError = num_error;
        int remainingTotal = remainingInfo + remainingWarn + remainingError;
        int entriesPerFile = remainingTotal / num_files;

        // Init random with seed
        Random random = new Random(seed);

        // Init num_files many LogFile writers
        BufferedWriter[] writers = new BufferedWriter[num_files];

        // Init result Array containing all newly created LogFile paths as Strings
        String[] filepaths = new String[num_files];

        // Write each LogFile sequentially
        for (int i = 0; i < writers.length; i++) {
            // Adjust entriesPerFile so that the last file shall contain all remaining entries
            if (i == (writers.length - 1)) {
                entriesPerFile += ((num_info + num_warn + num_error) % num_files);
            }

            try {
                // Create a new empty LogFile
                Path filepath = Paths.get(path, "LogFile" + i + ".txt");
                writers[i] = Files.newBufferedWriter(filepath);
                filepaths[i] = filepath.toString();

                String entry = "";

                // Write entriesPerFile many lines to the current LogFile
                for (int e = 0; e < entriesPerFile; e++) {

                    // Randomly generate a value in range [0, remainingTotal[
                    int r = random.nextInt(remainingTotal);

                    // Selection criteria for log entry category
                    if (r < remainingInfo) {
                        entry = LogFileEntries.info.get(random.nextInt(LogFileEntries.info.size()));
                        remainingInfo--;

                    } else if (r < (remainingInfo + remainingWarn)) {
                        entry = LogFileEntries.warn.get(random.nextInt(LogFileEntries.warn.size()));
                        remainingWarn--;

                    } else {
                        entry = LogFileEntries.error.get(random.nextInt(LogFileEntries.error.size()));
                        remainingError--;
                    }
                    remainingTotal--;

                    // Write the entry to the LogFile
                    writers[i].write(entry);
                    if (e < entriesPerFile - 1) {
                        writers[i].newLine();
                    }
                }

                writers[i].close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        assert remainingInfo == 0 : "remainingInfo should be 0 but was " + remainingInfo;
        assert remainingWarn == 0 : "remainingWarn should be 0 but was " + remainingWarn;
        assert remainingError == 0 : "remainingError should be 0 but was " + remainingError;
        assert remainingTotal == 0 : "remainingTotal should be 0 but was " + remainingTotal;

        return filepaths;
    }

    // ########
    // # MAIN #
    // ########

    public static void main(String[] args) {

        if (args.length != 4) {
            System.out.println("Usage: java LogFileGenerator <num_files> <num_info> <num_warn> <num_error>");
            return;
        }

        try {
            int num_files = Integer.parseInt(args[0]);
            int num_info = Integer.parseInt(args[1]);
            int num_warn = Integer.parseInt(args[2]);
            int num_error = Integer.parseInt(args[3]);

            Path path = Paths.get(System.getProperty("user.dir"), "logfiles");
            Files.createDirectories(path);

            String[] filepaths = generateLogFiles(path.toString(), num_files, LogFileGenerator.SEED, num_info,
                    num_warn, num_error);

            System.out.println("Successfully created the following " + num_files + " LogFiles at " + path + ".");
            for (String filepath : filepaths) {
                System.out.println("\t" + filepath);
            }
            System.out.println("Together all files contain " + num_info + " INFO log entries " + num_warn
                    + " WARN entries and " + num_error + " ERROR entries.");

        } catch (NumberFormatException e) {
            System.out.println("Error: Arguments must all be integers.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class LogFileEntries {

        static List<String> info = List.of(
                "INFO User registered",
                "INFO User logged in",
                "INFO User logged out",
                "INFO Password changed",
                "INFO Session expired",
                "INFO User profile updated",
                "INFO Request received",
                "INFO Request processed",
                "INFO Database connection established",
                "INFO Query executed successfully",
                "INFO Service started",
                "INFO Service stopped",
                "INFO Message sent",
                "INFO Message received",
                "INFO Job started");

        static List<String> warn = List.of(
                "WARN Failed login attempt",
                "WARN Invalid request parameters",
                "WARN Slow query detected",
                "WARN Cache miss",
                "WARN High memory usage detected",
                "WARN Disk space running low",
                "WARN Unauthorized access attempt",
                "WARN Retry attempt initiated",
                "WARN Partial failure detected",
                "WARN Task execution delayed");

        static List<String> error = List.of(
                "ERROR Account locked due to multiple failures",
                "ERROR Failed to process request",
                "ERROR Endpoint not found",
                "ERROR Database connection lost",
                "ERROR Failed to write to database",
                "ERROR Failed to reach external service",
                "ERROR Connection timeout",
                "ERROR Out of memory",
                "ERROR Failed to load configuration",
                "ERROR Access denied",
                "ERROR Invalid token",
                "ERROR Failed to process event",
                "ERROR Task execution failed",
                "ERROR Timeout occurred");
    }
}
