# Parallel learning within one arena

`arena.performance.learning-workers` (environment variable
`ARENA_PERFORMANCE_LEARNING_WORKERS`) defaults to **16** and accepts **1–16**. Matching workers accept **1–15**.
`GET /api/arena/options` exposes the effective `performance.learningWorkers`.
Outside full-speed mode, matching and learning share a run-owned pool containing
at most `max(matchingWorkers, learningWorkers)` threads. Each phase uses its own
worker limit. Both phases use `parallel-threshold` (default 32,768
candidate/action-set rules). In full-speed mode, the matching pool uses up to
`min(trainingWorkers, 15)` workers and update calculations remain sequential.

Standard training and evaluation games are sequential; full-speed mode runs
games concurrently. Inside a shared XCS population, action selection, covering,
random draws, genetic operations, deletion and subsumption remain sequential.
Workers calculate matching results and, outside full-speed mode, prediction,
error, action-set size and accuracy into disjoint scratch arrays. The owner sums
accuracy in ascending rule-ID order, then commits rule state, fitness, history
and metrics in that order. No rule is changed before all calculations finish.
Agent inspection retains the existing agent monitor. Cancellation joins actual
worker exits before returning; shutdown also cancels queued futures so their
callers can finish.

## Measurements (2026-09-27)

These are exploratory measurements on the development machine using JDK 25.0.4
and JMH 1.37, with a 32 GiB maximum heap. They are **not release-gate evidence**:
one fork and one measurement iteration cannot establish a reliable speedup.
These measurements preceded the user-requested change to a default of 16 learning workers. Raw summarized throughput, allocation and GC
results are in [learning-throughput.csv](learning-throughput.csv).

An initial matching baseline, taken before the implementation changed, measured
1,526 / 89.6 / 3.62 matches per second at 10k / 100k / 1m rules respectively
(high match density, one action). Subsequent comparisons use the new sequential
path as their one-worker baseline. A full-loop profile was not collected before
editing; the new one-worker results must not be presented as a historical
before/after comparison.

### Update kernel

Every rule is updated, with alternating targets and retained history enabled by
the production implementation. `histories=false` means no history was prefilled.
One second of warmup and one second of measurement per case:

| Rules | 1 worker | 2 | 4 | 8 | 15 |
|---|---:|---:|---:|---:|---:|
| 10k | 1,485 | 1,500 | 1,456 | 1,581 | 1,494 |
| 100k | 66.2 | 74.9 | 67.5 | 77.7 | 74.6 |
| 1m | 5.20 | 5.97 | 6.85 | 6.72 | 7.38 |

Values are updates/second. At 10k, every configuration takes the sequential
threshold path. The 1m result suggests a 42% kernel gain at 15 workers, but this
includes serial rule lookup, reduction and history work and says little about
how often a real game has such a large action set.

### Matching kernel

Matches/second, using the same short warmup/measurement settings:

| Rules | 1 worker | 2 | 4 | 8 | 15 |
|---|---:|---:|---:|---:|---:|
| 10k | 1,531 | 1,632 | 1,623 | 1,602 | 1,401 |
| 100k | 75.1 | 126.1 | 131.0 | 151.4 | 157.4 |
| 1m | 3.87 | 4.37 | 4.52 | 4.60 | 5.07 |

The one-million-rule result is about 31% faster at 15 workers. Variation at 10k,
where no workers are used, reinforces the need for longer repeated measurements.

### Complete training games

The harness invokes the actual arena game loop in TURBO pacing, including
learning, game rules, telemetry, histories, replays and publication. It runs one
XCS learner against the uniform random agent, seed 834. Evaluation pauses and
network subscribers are excluded. Population setup uses reflection outside the
measured operation; the normal application has no rule importer.

Sparse fixtures contain dormant classifiers with unrelated action IDs. They
exercise population maintenance and covering pressure. Dense fixtures contain
wildcard classifiers for opening moves, providing large matching and action sets.
These are synthetic extremes, not trained models. Population state evolves
within each measurement. Dense results include JFR and cold code, whereas sparse
results use one second of warmup. Compare worker counts within a fixture only.

Sparse games/second:

| Rules | 1 worker | 2 | 4 | 8 | 15 |
|---|---:|---:|---:|---:|---:|
| 10k | 12.93 | 14.37 | 12.74 | 13.02 | 12.94 |
| 100k | 1.152 | 1.181 | 1.160 | 1.132 | 1.168 |
| 1m | 0.036 | 0.038 | 0.035 | 0.035 | 0.036 |

Dense games/second (JFR enabled, no warmup):

| Rules | 1 worker | 2 | 4 | 8 | 15 |
|---|---:|---:|---:|---:|---:|
| 10k | 9.680 | 9.593 | 9.766 | 9.719 | 9.825 |
| 100k | 0.785 | 0.798 | 0.813 | 0.823 | 0.835 |
| 1m | 0.0256 | 0.0254 | 0.0256 | 0.0262 | 0.0255 |

Neither workload demonstrates a 20% full-game gain.

### Decision latency and CPU profile

All dense cases have decision latency and worker sample counts in
[learning-decision-latency.csv](learning-decision-latency.csv). At 1m rules:

| Metric | 1 worker | 15 workers |
|---|---:|---:|
| Mean XCS decision | 708.5 ms | 711.7 ms |
| Decision p50 | 792.9 ms | 835.0 ms |
| Decision p95 | 1,140.3 ms | 1,175.2 ms |
| Mean telemetry call | 0.010 ms | 0.010 ms |
| Matching/covering CPU samples | 517 | 511 |
| Genetics/deletion CPU samples | 1,826 | 1,919 |
| Learning update CPU samples | 10 | 11 |
| Game-rule CPU samples | 1 | 1 |
| Other/runtime CPU samples | 1,444 | 1,477 |
| Worker CPU samples | 0 | 136 |
| Coordinator CPU samples | 3,798 | 3,783 |

These recordings contain 55 learner decisions each. No publication or telemetry
CPU samples were captured at 1m; this does not mean those phases cost zero.
JFR's shallow/truncated stacks leave substantial work unattributed. The largest
resolved hotspot is `isDeletable` and deletion-related stream traversal.
Workers account for only 3.5% of execution samples in the 15-worker recording.
The raw mean JFR thread CPU-load values were about 0.0612 for the coordinator and
0.0003–0.0004 per worker; these are reported as recorded, without converting them to percentages of
one core. This explains why faster independent update arithmetic does not
materially change full-game throughput in these fixtures.

## Reproduce

Run benchmark processes sequentially to avoid CPU contention. For release
qualification use at least three forks, five warmup and five measurement
iterations, and repeat with representative saved/trained populations. The
current target is at least 50% more complete training games per second on large
populations, with no more than 10% regression on small populations. The current
default of 16 learning workers was explicitly requested after these measurements.

### Full-speed matching experiment (2026-09-28)

A one-fork JFR profile of the 100k dense fixture against RANDOM recorded 452
matching/covering samples, 25 genetics/deletion samples, 10 learning-update
samples, and 163 other/runtime samples. The benchmark fixture uses wildcard
rules and is not a trained population. The profile supported testing full-speed
matching, which had previously been forced to one worker.

The first change gives matching up to the configured full-speed game-worker
count (capped at 15); learning updates stay sequential. A second change caches
whether a rule is fully general when it enters the population, so matching skips
the per-attribute scan for wildcard-only rules. Three forks with five one-second
warmup and measurement iterations produced:

| Game workers | Sequential matching | Plus parallel matching | Plus general-rule fast path | Plus cursor heap | Plus no-deletion fast path |
|---:|---:|---:|---:|---:|---:|
| 1 | 3.26 | 3.26 | 3.26 | 3.26 | 4.63 |
| 4 | 3.09 | 3.79 | 4.29 | 4.49 | 4.67 |
| 8 | 2.46 | 3.03 | 3.30 | 3.58 | 3.54 |

Games/second is JMH batch throughput multiplied by games per batch. This
benchmark seeds every classifier with a wildcard condition, so it stresses the
fast path and does not represent a trained population. A separate one-fork JFR
recording showed `Rule.matches` in 230 baseline stack samples and 36 samples
after the fast path; treat that as diagnostic evidence, not a reliable speedup
estimate. Before the covering fast path, the best result was 38% above the
one-worker baseline. The current four-worker mean is 43% above that baseline
and 51% above the four-worker sequential-matching result; the eight-worker mean
is 9% above the one-worker baseline and 44% above its same-worker baseline.
Confidence intervals remain broad, and this is still a synthetic wildcard
population, so the 50% target is not yet established on representative trained
populations.

The cursor-heap change reuses one mutable cursor per legal action instead of
allocating one cursor per candidate rule. On the one-million-rule, 50-legal-
action matching kernel, three forks measured 0.198 ± 0.007 matches/ms and
13.26 MB/op before, versus 0.219 ± 0.012 matches/ms and 11.66 MB/op after.
That is a 10.6% kernel throughput gain and 12.1% less allocation. The full-game
change from the preceding experiment is smaller and noisy, so the kernel result
supports keeping this focused allocation reduction without claiming it meets
the end-to-end target.

The covering-capacity fast path returns before scanning the population when no
deletion is needed. In the one-worker profile, the previously sampled
`reserveCoveringSpace` stream scan no longer appears. The complete-game result
did not show a clear improvement at four or eight workers, so this remains a
profile-backed removal of unnecessary work rather than a claimed throughput
gain. Benchmark JSON files are
`app/build/xcs-experiment-baseline.json`,
`app/build/xcs-parallel-matching.json`, and
`app/build/xcs-fast-general-rules.json`,
`app/build/xcs-cursor-heap.json`, and
`app/build/heap-current-million-kernel.json`,
`app/build/xcs-cover-early-return.json`, and
`app/build/xcs-cover-early-profile.json`.

```sh
./gradlew benchmarks -PbenchmarkArgs='PopulationBenchmark.update -p size=10000,100000,1000000 -p implementation=parallel -p workers=1,2,4,8,15 -p actions=1 -p numerosity=1 -p highMatch=true -p histories=false -f 3 -wi 5 -i 5 -w 1s -r 1s -bm thrpt,sample -prof gc -rf json -rff build/learning-update.json'
./gradlew benchmarks -PbenchmarkArgs='PopulationBenchmark.matching -p size=10000,100000,1000000 -p implementation=parallel -p workers=1,2,4,8,15 -p actions=1 -p numerosity=1 -p highMatch=true -p histories=false -f 3 -wi 5 -i 5 -w 1s -r 1s -bm thrpt,sample -prof gc -rf json -rff build/learning-matching.json'
./gradlew benchmarks -PbenchmarkArgs='TrainingBenchmark -p dense=false,true -p size=10000,100000,1000000 -p workers=1,2,4,8,15 -f 3 -wi 5 -i 5 -w 1s -r 1s -bm thrpt,sample -prof gc -rf json -rff build/learning-training.json'
```

For CPU attribution and decision latency, add
`-prof jfr:dir=build/learning-jfr` to a separate training benchmark run. The
benchmark emits `lcs.BenchmarkAgentCall` duration events without adding production
instrumentation. To summarize a recording:

```sh
jfr print --json --events jdk.ExecutionSample,jdk.ThreadCPULoad,lcs.BenchmarkAgentCall path/to/profile.jfr > /tmp/training-profile.json
python3 scripts/summarize-training-profile.py /tmp/training-profile.json
```

The summary discards fixture setup before the first episode. CPU sample categories
are approximate stack-based attribution, not exact wall time. Short profiles may
have no samples for cheap phases. Thread CPU load is reported as JFR recorded it;
worker sample counts show whether the pool had useful work.

## Verification

The tests compare exact decisions, traces, rule snapshots, history entries and
evolution ordering with the reference implementation at 1, 2, 4, 8 and 15 workers.
Additional repeated populations verify ordered updates through history rollover.
Lifecycle tests cover cancellation, worker failure, queued-task shutdown,
inspection while learning and unchanged state after an interrupted calculation.
Configuration tests verify the worker limits and effective options.
