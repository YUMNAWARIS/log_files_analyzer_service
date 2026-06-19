package main.java.executor;

import main.java.queue.CustomBlockingQueue;

public class ThreadPoolExecutor {

	// ##################
	// # INITIALIZATION #
	// ##################

	// ##########
	// # QUEUES #
	// ##########
	private final CustomBlockingQueue<Task> inputQueue = new CustomBlockingQueue<>();
	private final CustomBlockingQueue<Task> outputQueue = new CustomBlockingQueue<>();

	private final PoisonPill poisonPill = new PoisonPill();

	// Worker Thread Pool - Stores threads
	private final Thread[] worker_pool;

	private volatile boolean shuttingDown = false;

	public ThreadPoolExecutor(int threads) {
		if (threads <= 0) {
			throw new IllegalArgumentException("Number of threads must be greater than 0.");
		}

		this.worker_pool = new Thread[threads];

		for (int i = 0; i < threads; i++) {
			Worker w = new Worker(inputQueue, outputQueue, poisonPill);
			Thread t = new Thread(w, "thread_" + i);
			this.worker_pool[i] = t;
			this.worker_pool[i].start();
		}
	}

	/* Dispatch new task for execution */
	public void dispatch(Task task) {

		if (task == null) {
			return;
		}

		if (this.shuttingDown) {
			return;
		}

		inputQueue.insert(task);

	}

	/* Collect results from a finished task */
	public Task collect() {

		try {
			return outputQueue.retrieve();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	// ############
	// # SHUTDOWN #
	// ############

	/* Terminate worker threads and await their termination */
	public void shutdown() throws InterruptedException {

		for (int i = 0; i < this.worker_pool.length; i++) {
			this.dispatch(poisonPill);
		}

		if (this.shuttingDown) {
			return;
		}

		this.shuttingDown = true;

		for (Thread worker : worker_pool) {
			worker.join();
		}

	}

	class PoisonPill implements Task {
		@Override
		public void execute() {
		}
	}

}
