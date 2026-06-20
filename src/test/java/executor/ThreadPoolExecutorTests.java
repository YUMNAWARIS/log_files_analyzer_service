package test.java.executor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import main.java.executor.Task;
import main.java.executor.ThreadPoolExecutor;

public class ThreadPoolExecutorTests {

    // #########
    // # TESTS #
    // #########

    @Test
    public void testDataTypes() {
        Class<?> executor = ThreadPoolExecutor.class;

        for (Field field : executor.getDeclaredFields()) {
            if (Executor.class.isAssignableFrom(field.getType())) {
                fail("Usage of java.util.concurrent.Executor data types is not allowed for the executor service solution which was used in variable: "
                        + field.getName() + " of type: " + field.getType());
            }
        }
    }

    @Test
    public void testRejectsNonPositiveThreadCount() {
        assertThrows(IllegalArgumentException.class, () -> new ThreadPoolExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new ThreadPoolExecutor(-1));
    }

    @RepeatedTest(10)
    public void testParallelCounter() {
        // In case the executor is not implemented correctly and blocks indefinitely, timeout after 10 seconds
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {

            // Create number values with Random
            Random random = new Random();
            List<Integer> numbers = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                numbers.add(random.nextInt(2000));
            }

            // Create the executor under test
            ThreadPoolExecutor executor = new ThreadPoolExecutor(10);

            // Reset the shared counter to ensure no previous test can influence the results
            ParallelCounterTask.reset_counter();

            // Submit all numbers as tasks to be added concurrently
            for (Integer n : numbers) {
                executor.dispatch(new ParallelCounterTask(n));
            }

            // Add all numbers sequentially for comparison
            int sum = 0;
            for (Integer n : numbers) {
                sum += n;
            }

            // Drain the output queue so every task is known to have completed
            for (int i = 0; i < numbers.size(); i++) {
                assertNotNull(executor.collect());
            }

            executor.shutdown();

            // Compare result of concurrent and sequential addition
            assertEquals(sum, ParallelCounterTask.get_counter());
        }, "Test execution exceeded 10 seconds");
    }

    @Test
    public void testDispatchAfterShutdownIsIgnored() {
        // dispatch() must silently ignore tasks once the executor has shut down, never block or throw
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {

            ThreadPoolExecutor executor = new ThreadPoolExecutor(2);
            ParallelCounterTask.reset_counter();

            executor.dispatch(new ParallelCounterTask(5));
            assertNotNull(executor.collect());

            executor.shutdown();

            executor.dispatch(new ParallelCounterTask(100));

            assertEquals(5, ParallelCounterTask.get_counter());
        }, "Test execution exceeded 10 seconds");
    }

    @Test
    public void testCollectReturnsTasksInCompletionOrder() {
        // With a single worker thread, tasks must complete (and be collectible) in dispatch order
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {

            ThreadPoolExecutor executor = new ThreadPoolExecutor(1);
            ParallelCounterTask.reset_counter();

            int[] values = { 1, 2, 3, 4, 5 };
            for (int v : values) {
                executor.dispatch(new ParallelCounterTask(v));
            }

            for (int v : values) {
                Task completed = executor.collect();
                assertTrue(completed instanceof ParallelCounterTask);
                assertEquals(v, ((ParallelCounterTask) completed).getValue());
            }

            executor.shutdown();
        }, "Test execution exceeded 10 seconds");
    }

    // ###########
    // # HELPERS #
    // ###########

    public static class ParallelCounterTask implements Task {

        /* Shared thread-safe counter */
        private static AtomicInteger counter = new AtomicInteger(0);

        /* Task property */
        private final int value;

        public ParallelCounterTask(int value) {
            this.value = value;
        }

        @Override
        public void execute() {
            counter.addAndGet(this.value);
        }

        public int getValue() {
            return this.value;
        }

        public static int get_counter() {
            return counter.get();
        }

        public static void reset_counter() {
            counter = new AtomicInteger(0);
        }
    }
}
