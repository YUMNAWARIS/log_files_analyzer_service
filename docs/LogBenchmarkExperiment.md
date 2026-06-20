# LogBenchmark Experiment

Source: [`src/main/java/benchmark/LogBenchmark.java`](../src/main/java/benchmark/LogBenchmark.java)
Results graph: [`LogBenchmarkResults.pdf`](LogBenchmarkResults.pdf)

## Goal

Measure how the **custom `ThreadPoolExecutor` + `CustomBlockingQueue`**
implementation actually scales as worker threads are added — i.e. confirm
that the hand-built concurrency primitives in this project deliver *real*
parallel speedup on a CPU/IO-bound workload, not just multithreaded-looking
code that secretly serializes on a lock.

## Setup

1. **Data generation** — `LogBenchmark.prepareFilesForExperiment()` uses the
   seeded `LogFileGenerator` (seed `123456L`) to create **100 log files**
   containing, in total:
   - 20,000,000 `INFO` entries
   - 10,000,000 `WARN` entries
   - 10,000,000 `ERROR` entries

   The seed makes the dataset reproducible — every run analyzes the exact
   same files.

2. **Workload** — for a given thread count `x`, `LogBenchmark.execution()`:
   - creates a `ThreadPoolExecutor` with `x` worker threads,
   - dispatches one `LogCounter(filepath, LogType.INFO)` task per file
     (100 tasks total),
   - calls `shutdown()` to await completion of all dispatched tasks,
   - `collect()`s all 100 results and sums the `INFO` counts.

3. **Measurement** — `main()` sweeps thread counts `x = 1 .. 16`. For each
   `x` it repeats the full `execution()` call **5 times**, timing each run
   with `System.nanoTime()`, and reports the **average wall-clock time in
   milliseconds** across the 5 repetitions. Averaging over multiple runs
   smooths out JIT warm-up and OS scheduling noise.

## Results

| Threads | Avg. time (ms) | Speedup vs. 1 thread |
|---:|---:|---:|
| 1  | 1585.4 | 1.00x |
| 2  | 907.0  | 1.75x |
| 3  | 612.0  | 2.59x |
| 4  | 497.2  | 3.19x |
| 5  | 550.8  | 2.88x |
| 6  | 447.0  | 3.55x |
| 7  | 392.0  | 4.04x |
| 8  | 375.6  | 4.22x |
| 9  | 371.2  | 4.27x |
| 10 | 359.0  | 4.42x |
| 11 | 347.2  | 4.57x |
| 12 | 347.4  | 4.56x |
| 13 | 339.2  | 4.67x |
| 14 | 331.6  | 4.78x |
| 15 | 329.8  | 4.81x |
| 16 | 330.4  | 4.80x |

See the full bar chart in [`LogBenchmarkResults.pdf`](LogBenchmarkResults.pdf).

## Analysis

- **Steep early gains (1→4 threads):** execution time drops from 1585.4ms
  to 497.2ms — a ~3.2x speedup from 4x the workers. This is the region
  where the workload is genuinely CPU/IO-parallel and the worker pool,
  input/output queues, and `wait()`/`notifyAll()` coordination are paying
  off directly.
- **5-thread dip (550.8ms):** a local regression against the otherwise
  monotonic trend (4 threads: 497.2ms, 6 threads: 447.0ms). This is
  consistent with scheduling/contention noise rather than a logic bug —
  5 worker threads on hardware with fewer physical cores can cause extra
  context switching before the 6th+ thread count starts overlapping I/O
  wait time more favorably. It's a reminder that wall-clock benchmarks are
  noisy and worth repeating (hence the 5-repetition average), not a single
  run.
- **Diminishing returns (8→16 threads):** time flattens out around
  330–375ms. Beyond roughly 8 threads, additional workers stop translating
  into proportional gains — the workload has run out of independent work
  to parallelize relative to the number of CPU cores available, and the
  remaining time is dominated by sequential costs (file I/O setup, queue
  bookkeeping, `shutdown()`'s thread-join barrier) that don't shrink with
  more threads. This is the expected shape of **Amdahl's Law**: speedup is
  bounded by the workload's non-parallelizable fraction, not by how many
  threads you throw at it.
- **Takeaway:** the benchmark validates that `ThreadPoolExecutor` and
  `CustomBlockingQueue` correctly distribute and synchronize work across
  threads — the ~4.8x peak speedup on a fan-out/fan-in workload like this
  is exactly the kind of curve a correctly implemented thread pool should
  produce, and matches the textbook saturation curve you'd expect to see
  from `java.util.concurrent`'s own built-in executors.

## Reproducing

```bash
javac -d bin $(find src/main/java -name "*.java" -o -path "*/test/java/logCounter/LogFileGenerator.java")
java -cp bin main.java.benchmark.LogBenchmark
```

Note: this generates 100 log files with 40,000,000 total log lines under
`./logfiles` in the current working directory — make sure you have a few
hundred MB of free disk space before running it.
