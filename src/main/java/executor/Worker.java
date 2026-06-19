package main.java.executor;

import main.java.executor.ThreadPoolExecutor.PoisonPill;
import main.java.queue.CustomBlockingQueue;

class Worker implements Runnable {

	private CustomBlockingQueue<Task> inputQueue;
	private CustomBlockingQueue<Task> outputQueue;

	private PoisonPill poisonPill;
	
	public Worker(CustomBlockingQueue<Task> inputQueue, CustomBlockingQueue<Task> outputQueue, PoisonPill poisonPill) {
		this.inputQueue = inputQueue;
		this.outputQueue = outputQueue;
		this.poisonPill = poisonPill;
		
	}

	@Override
	public void run() {
		while (true) {
			try {

				Task task = this.inputQueue.retrieve();

				if (task == poisonPill) {
					break;
				}

				task.execute();

				this.outputQueue.insert(task);

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}
}