package test.java.logCounter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.RepeatedTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import main.java.executor.ThreadPoolExecutor;
import main.java.logCounter.LogCounter;
import main.java.logCounter.LogCounter.LogType;

public class LogCounterTests {

    @RepeatedTest(10)
    public void testParallelLogAnalyzer() throws InterruptedException {
        // Define number of LogFiles and LogFile entries
        Random random = new Random();
        int num_files = random.nextInt(5, 10);
        int num_info = 15 * num_files + random.nextInt(1000);
        int num_warn = 10 * num_files + random.nextInt(750);
        int num_error = 7 * num_files + random.nextInt(450);
        long seed = 123456L;

        // Prepare folder for LogFiles (directory exists before LogFile creation)
        Path path = Paths.get(System.getProperty("user.dir"), "logfiles");
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create num_files many LogFiles with a known number of INFO/WARN/ERROR entries
        String[] filepaths = LogFileGenerator.generateLogFiles(path.toString(), num_files, seed, num_info, num_warn,
                num_error);

        // Create a ThreadPoolExecutor with num_files worker threads
        ThreadPoolExecutor executor = new ThreadPoolExecutor(num_files);

        // Dispatch a LogCounter task for each LogFile and each type of log entry
        for (String filepath : filepaths) {
            executor.dispatch(new LogCounter(filepath, LogType.INFO));
            executor.dispatch(new LogCounter(filepath, LogType.WARN));
            executor.dispatch(new LogCounter(filepath, LogType.ERROR));
        }

        // Shutdown the executor (awaits completion of all dispatched tasks)
        executor.shutdown();

        // Aggregate results
        int counter_info = 0;
        int counter_warn = 0;
        int counter_error = 0;

        for (int i = 0; i < 3 * num_files; i++) {
            LogCounter result = (LogCounter) executor.collect();
            switch (result.getType()) {
                case INFO:
                    counter_info += result.getResult();
                    break;
                case WARN:
                    counter_warn += result.getResult();
                    break;
                case ERROR:
                    counter_error += result.getResult();
                    break;
            }
        }

        // Compare results
        assertEquals(num_info, counter_info, "The number of INFO log entries from the executor service do not match");
        assertEquals(num_warn, counter_warn, "The number of WARN log entries from the executor service do not match");
        assertEquals(num_error, counter_error, "The number of ERROR log entries from the executor service do not match");
    }
}
