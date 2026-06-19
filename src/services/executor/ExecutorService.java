package services.executor;

/**
 * Worker-pool executor backed by two {@link BlockingQueue} instances:
 * one for pending tasks and one for completed tasks.
 */
public class ExecutorService {

    private static final Task SHUTDOWN_SENTINEL = new Task() {
        @Override
        public void execute() {
        }
    };

    private final BlockingQueue<Task> inputQueue = new BlockingQueue<>();
    private final BlockingQueue<Task> outputQueue = new BlockingQueue<>();
    private final InputCoordinator inputCoordinator = new InputCoordinator();
    private final OutputCoordinator outputCoordinator = new OutputCoordinator();
    private final Thread[] workers;

    private volatile boolean shuttingDown;
    private volatile boolean shutdownComplete;

    public ExecutorService(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Number of threads must be greater than 0.");
        }

        workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(new Worker(), "executor-worker-" + i);
            workers[i] = worker;
            worker.start();
        }
    }

    public void dispatch(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null.");
        }
        if (shuttingDown) {
            throw new IllegalStateException("Executor service is shut down.");
        }
        inputQueue.insert(task);
        inputCoordinator.produce();
    }

    public Task collect() {
        try {
            if (!outputCoordinator.consume()) {
                return null;
            }
            return outputQueue.retrieve();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void shutdown() throws InterruptedException {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;

        for (int i = 0; i < workers.length; i++) {
            inputQueue.insert(SHUTDOWN_SENTINEL);
            inputCoordinator.produce();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        shutdownComplete = true;
        outputCoordinator.signalShutdown();
    }

    /**
     * Counting coordinator for the input queue (lecture slide 27).
     * {@link #produce()} is called after enqueue; {@link #consume()} before dequeue.
     */
    private final class InputCoordinator {

        private int available;

        synchronized void produce() {
            available++;
            notifyAll();
        }

        synchronized void consume() throws InterruptedException {
            while (available <= 0) {
                wait();
            }
            available--;
        }

    }

    /**
     * Counting coordinator for the output queue (lecture slide 27).
     * Returns {@code false} when shutdown is complete and no results remain.
     */
    private final class OutputCoordinator {

        private int available;

        synchronized void produce() {
            available++;
            notifyAll();
        }

        synchronized boolean consume() throws InterruptedException {
            while (available <= 0) {
                if (shutdownComplete) {
                    return false;
                }
                wait();
            }
            available--;
            return true;
        }

        synchronized void signalShutdown() {
            notifyAll();
        }

    }

    private final class Worker implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    inputCoordinator.consume();
                    Task task = inputQueue.retrieve();
                    if (task == SHUTDOWN_SENTINEL) {
                        break;
                    }

                    task.execute();
                    outputQueue.insert(task);
                    outputCoordinator.produce();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

    }

}
