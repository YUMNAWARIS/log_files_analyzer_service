# Log Files Analyzer Service

A multithreaded log-file analysis engine built **from scratch in core Java** — no
`java.util.concurrent.Executor`, no `BlockingQueue`, no third-party concurrency
libraries. Every concurrency primitive (thread pool, task queue, worker
lifecycle, producer/consumer coordination) is hand-implemented using `wait()`
/ `notifyAll()`, `synchronized`, and raw `Thread`s, then driven through a
real workload: counting `INFO` / `WARN` / `ERROR` entries across many log
files in parallel.

The project exists to demonstrate Java concurrency fundamentals at the
implementation level, not just usage of existing concurrency APIs.

---

## Why this project

Most "I know multithreading" projects stop at `ExecutorService.submit(...)`.
This one goes one level deeper and asks: *what is actually happening inside
that thread pool?* It implements:

- A bounded, thread-safe, **resizable circular buffer queue** with manual
  monitor-based blocking (the kind of data structure `LinkedBlockingQueue`
  hides from you).
- A **custom thread pool executor** with a fixed worker pool, task dispatch,
  result collection, and graceful shutdown via a poison-pill pattern — built
  without `Executors.newFixedThreadPool(...)`.
- A **producer/consumer pipeline**: workers pull `Task`s off an input queue,
  execute them, and push completed tasks onto an output queue for the caller
  to collect — decoupling submission from result retrieval.
- A **parallel log analyzer** (`LogCounter`) that uses the executor to fan
  out file-reading work across worker threads and fan in aggregated counts.

---

## Folder structure

```
log_files_analyzer_service/
├── src/
│   ├── main/java/
│   │   ├── queue/
│   │   │   └── CustomBlockingQueue.java     # Hand-built thread-safe blocking queue
│   │   └── executor/
│   │       ├── Task.java                    # Unit-of-work contract
│   │       ├── Worker.java                  # Runnable consumer loop run by each pool thread
│   │       └── ThreadPoolExecutor.java      # Custom fixed-size thread pool
│   │   └── logCounter/
│   │       └── LogCounter.java              # Task implementation: counts log lines by type
│   │   └── benchmark/
│   │       └── LogBenchmark.java            # Thread-count vs. execution-time experiment
│   │
│   └── test/java/
│       ├── queue/
│       │   └── CustomBlockingQueueTests.java
│       ├── executor/
│       │   └── ThreadPoolExecutorTests.java
│       └── logCounter/
│           ├── LogFileGenerator.java        # Deterministic test-data generator
│           └── LogCounterTests.java         # End-to-end parallel analyzer test
│
├── docs/
│   ├── LogBenchmarkExperiment.md            # Benchmark methodology, full results, analysis
│   └── LogBenchmarkResults.pdf              # Execution-time-vs-threads bar chart
│
├── .classpath / .project                    # Eclipse project metadata
└── README.md
```

> Note: package names mirror their folder path under `src/`
> (e.g. `main/java/executor/Worker.java` → `package main.java.executor;`),
> matching this Eclipse project's single-source-folder layout.

---

## Architecture

```
                 ┌────────────────────────────────────────────┐
                 │             ThreadPoolExecutor              │
                 │                                              │
  dispatch(task) │   ┌───────────────┐         ┌──────────────┐ │  collect()
 ───────────────►│──►│  inputQueue   │         │ outputQueue  │─│────────────►
                 │   │ (CustomBlockingQueue)    │ (CustomBlockingQueue)
                 │   └───────┬───────┘         └──────▲───────┘ │
                 │           │  retrieve()             │ insert()│
                 │     ┌─────┴─────┬─────────────┬─────┴─────┐  │
                 │     ▼           ▼             ▼           │  │
                 │  Worker-1    Worker-2   ...  Worker-N      │  │
                 │  (Thread)    (Thread)        (Thread)      │  │
                 └────────────────────────────────────────────┘
```

1. The caller `dispatch()`es `Task` objects into the **input queue**.
2. Each `Worker` thread blocks on `inputQueue.retrieve()`, executes the task
   (`task.execute()`), and pushes the completed task into the **output
   queue**.
3. The caller `collect()`s completed tasks from the output queue —
   submission and result-retrieval are fully decoupled.
4. `shutdown()` enqueues one **poison pill** per worker; each worker exits
   its loop the moment it dequeues the pill, and `shutdown()` joins every
   worker thread before returning.

Applied to log analysis: each log file is read by up to 3 parallel
`LogCounter` tasks (one per log type), so a 10-file workload becomes 30
independent tasks distributed across the worker pool.

---

## Concepts demonstrated

### 1. Manual thread synchronization (`CustomBlockingQueue`)
- `synchronized` blocks guarding all shared mutable state (`head`, `tail`,
  `size`, backing array) to enforce mutual exclusion.
- **Monitor pattern**: `wait()` inside a `while` loop (guarding against
  spurious wakeups) + `notifyAll()` to implement blocking `retrieve()` on an
  empty queue, without spinning or polling.
- **Circular buffer** indexing (`(head + i) % capacity`) for O(1) enqueue/
  dequeue without shifting elements.
- **Dynamic resizing**: queue doubles its backing array and re-linearizes
  the circular buffer when full, so the queue never blocks on `insert()`.
- Generic, type-safe API (`CustomBlockingQueue<V>`) built on a raw
  `Object[]` array — no `java.util.Collection` types used internally.

### 2. Thread pool construction (`ThreadPoolExecutor`)
- Fixed-size **worker pool** of raw `Thread` objects (no `Executors` factory
  methods) started eagerly in the constructor.
- **Task abstraction** (`Task` interface) decoupling *what* runs from *how/
  where* it runs — the executor only knows `execute()`.
- **Producer/consumer decoupling** via two independent queues (input for
  work, output for results), enabling async submission and on-demand result
  collection (`collect()`).
- **Poison pill shutdown pattern**: a sentinel `Task` instance signals each
  worker to terminate its loop, avoiding flags checked mid-task or unsafe
  interruption of in-flight work.
- **Graceful shutdown**: `shutdown()` blocks the calling thread via
  `Thread.join()` on every worker until all in-flight and queued work has
  drained.
- A `volatile boolean shuttingDown` flag guards against new work being
  accepted once shutdown has begun — demonstrating safe publication of a
  flag across threads without full synchronization.

### 3. Parallel workload (`LogCounter` + log analysis)
- **Task-per-file-per-type fan-out**: a single multi-file analysis job is
  decomposed into independent, embarrassingly-parallel units of work.
- **Shared-nothing tasks**: each `LogCounter` owns its own file handle and
  result counter, eliminating contention — no locking needed in the hot
  path of `execute()`.
- **Fan-in aggregation**: results are summed back together per category
  after collection, demonstrating the map-reduce shape of the pipeline.
- I/O-bound concurrency: reading many files in parallel via
  `BufferedReader`, where threads overlap I/O wait time instead of
  serializing it.

### 4. Testing concurrent code (JUnit 5)
- **Reflection-based contract tests** (`testDataTypes`) asserting the queue/
  executor implementations don't smuggle in `java.util.Collection` or
  `java.util.concurrent.Executor` under the hood — verifying the
  "build it yourself" constraint is actually honored.
- **`assertTimeoutPreemptively`** to fail fast (rather than hang forever)
  if a concurrency bug introduces a deadlock or missed `notify()`.
- **`@RepeatedTest`** to flush out flaky, timing-dependent race conditions
  that a single run could miss.
- **Producer/consumer stress tests**: multiple producer threads and
  multiple consumer threads hammering the same queue concurrently, with
  correctness verified by comparing a parallel-computed sum against a
  sequential one (a classic technique for validating lock-free/lock-based
  correctness without inspecting internal state).
- **Deterministic, seeded test-data generation** (`LogFileGenerator`) so
  randomized-looking log files still produce an exactly known expected
  count — letting concurrent results be asserted against ground truth.
- **Blocking-behavior tests**: spinning up a consumer thread against an
  empty queue and asserting it parks until an `insert()` wakes it,
  directly exercising the `wait()`/`notifyAll()` contract.
- **Wrap-around / capacity-growth tests** targeting the circular-buffer
  index math and dynamic-resize logic specifically.

---

## Component reference

| Component | File | Responsibility |
|---|---|---|
| `CustomBlockingQueue<V>` | `queue/CustomBlockingQueue.java` | Thread-safe, resizable, circular-buffer FIFO queue with blocking `retrieve()` and non-blocking, auto-growing `insert()`. |
| `Task` | `executor/Task.java` | Functional contract for a unit of work (`execute()`). |
| `Worker` | `executor/Worker.java` | `Runnable` consumer loop: retrieve → execute → publish result, until a poison pill is received. |
| `ThreadPoolExecutor` | `executor/ThreadPoolExecutor.java` | Fixed-size custom thread pool: `dispatch()`, `collect()`, `shutdown()`. |
| `LogCounter` | `logCounter/LogCounter.java` | `Task` that counts lines of a given `LogType` (`INFO`/`WARN`/`ERROR`) in one file. |
| `LogFileGenerator` | `test/java/logCounter/LogFileGenerator.java` | Test utility: generates seeded, reproducible log files with an exact known entry distribution. |
| `LogBenchmark` | `benchmark/LogBenchmark.java` | Measures average execution time of the parallel log analysis across 1–16 worker threads. |

---

## Performance benchmark: does the thread pool actually scale?

It's easy to write multithreaded-*looking* code that never actually runs in
parallel (e.g. an accidental global lock around the "parallel" part). To
prove the custom `ThreadPoolExecutor` + `CustomBlockingQueue` genuinely
scale, [`LogBenchmark`](src/main/java/benchmark/LogBenchmark.java) analyzes
**100 generated log files (40,000,000 total log lines)** with worker counts
swept from **1 to 16 threads**, averaging 5 runs per thread count.

| Threads | Avg. time (ms) | Speedup vs. 1 thread |
|---:|---:|---:|
| 1  | 1585.4 | 1.00x |
| 2  | 907.0  | 1.75x |
| 4  | 497.2  | 3.19x |
| 8  | 375.6  | 4.22x |
| 12 | 347.4  | 4.56x |
| 16 | 330.4  | 4.80x |

Execution time drops ~5x going from 1 to 16 threads, with the steepest
gains between 1–4 threads and a clear saturation curve after ~8 threads —
the textbook shape of **Amdahl's Law** in action. Full results (all 16
data points) and chart: **[LogBenchmarkExperiment.md](docs/LogBenchmarkExperiment.md)** ·
**[LogBenchmarkResults.pdf](docs/LogBenchmarkResults.pdf)**

---

## Running the tests

This is a plain Eclipse Java project (no Maven/Gradle wrapper committed yet).
To run the test suite:

1. Add JUnit 5 (`junit-jupiter`) to the project's build path.
2. Run the test classes under `src/test/java/**` as JUnit 5 tests:
   - `CustomBlockingQueueTests` — queue correctness, blocking semantics,
     resizing, and concurrent producer/consumer stress tests.
   - `ThreadPoolExecutorTests` — pool construction, dispatch/collect,
     shutdown semantics, and completion ordering.
   - `LogCounterTests` — end-to-end parallel log analysis correctness
     against generated ground-truth files.

---

## Possible extensions

- Swap the fixed worker count for a dynamically resizable pool.
- Add a `CompletableFuture`-style API on top of `dispatch()`/`collect()`.
- Add per-task timeouts and cancellation.
- Stream large log files instead of reading line-by-line synchronously,
  to better exercise backpressure on the bounded queue.
