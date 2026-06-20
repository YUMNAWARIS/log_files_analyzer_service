package main.java.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import main.java.executor.ThreadPoolExecutor;
import main.java.logCounter.LogCounter;
import main.java.logCounter.LogCounter.LogType;
import test.java.logCounter.LogFileGenerator;


public class LogBenchmark {
	public static String[] prepareFilesForExperiment() {
		// Define number of LogFiles and LogFile entries
		int num_files = 100;
		int num_info = 20000000;
		int num_warn = 10000000;
		int num_error = 10000000;
		long SEED = 123456L;

		// Prepare folder for LogFiles (directory exists and is empty before the LogFile
		// creation)
		Path path = Paths.get(System.getProperty("user.dir"), "logfiles");
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Create num_files many LogFiles
		String[] filepaths = LogFileGenerator.generateLogFiles(path.toString(), num_files, SEED, num_info, num_warn,
				num_error);

		return filepaths;
	}

	public static void execution(String[] filepaths, int x) {
		// Create an ExecutorService with x worker threads
		ThreadPoolExecutor executor = new ThreadPoolExecutor(x);

		// Create a new DSGLogAnalyzer (DSGTask) for each LogFile in the directory,
		// ’path' is pointing to and for each type of log entry
		for (String filepath : filepaths) {
			executor.dispatch(new LogCounter(filepath, LogType.INFO));
		}

		// Shutdown the Executor Service
		try {
			executor.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Collect result
		int counter_info = 0;

		for (int i = 0; i < 100; i++) {
			LogCounter result = (LogCounter) executor.collect();
			counter_info += result.getResult();
		}
	}

	public static void main(String args[]) {

		String[] filepaths = prepareFilesForExperiment();
		int repetitions = 5;

		for (int i = 1; i <= 16 ; i++) {
			
			long totalTime = 0;
			
			for (int j = 0; j < repetitions; j++) {

				long startTime = System.nanoTime();

				execution(filepaths, i);

				long endTime = System.nanoTime();

				long executionTime = (endTime - startTime) / 1000000;
				totalTime += executionTime;

			}
			
			double averageTime = totalTime / (double) repetitions;

	        System.out.println("Average for " + i + " threads: " + averageTime + "ms");

		}

	}
}
