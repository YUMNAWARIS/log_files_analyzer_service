package main.java.logCounter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import main.java.executor.Task;

public class LogCounter implements Task {
	// #########
	// # STATE #
	// #########
	String filepath;
	LogType type;
	int result = 0;

	/* Constructor */
	public LogCounter(String filepath, LogType type) {
		// TODO implement constructor
		this.filepath = filepath;
		this.type = type;

	}

	// ########
	// # TYPE #
	// ########
	public enum LogType {
		INFO, WARN, ERROR
	}

	// ###########
	// # EXECUTE #
	// ###########

	@Override
	public void execute() {
		BufferedReader reader;

		try {

			reader = new BufferedReader(new FileReader(this.filepath));

			String line;
			while (true) {
				line = reader.readLine();
				if (line == null) {
					break;
				}
				if (line.startsWith(this.type.toString())) {
					result++;
				}
			}

			// Close the stream
			reader.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	// ##########
	// # GETTER #
	// ##########

	public LogType getType() {
		return this.type;
	}

	public int getResult() {
		return result;
	}

}
