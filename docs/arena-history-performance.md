# Arena history updates

## Behavior

History remains capped at 2,048 points per chart series, 20 replay summaries,
and 200 evolution highlights. Each section has its own monotonically increasing,
run-local revision. Chart, replay-summary, and evolution snapshots are immutable
and reused until the corresponding data changes. Elapsed-time statistics are
still calculated when progress is captured. Turbo moves capture progress once.

The original `GET /api/arena/runs/current` and command responses retain the full
snapshot, with an added `historyRevisions` object. The browser uses:

- `GET /api/arena/runs/current/live`: board, controls, statistics, telemetry, run
  identity, snapshot revision, and history revisions; no history arrays.
- `GET /api/arena/runs/current/history?runId=...&charts=...&replays=...&evolution=...`:
  history from one published snapshot. Only sections with different revisions
  are included. Missing revisions default to `-1`. A different run ID returns all
  sections, even if revision numbers match. The response includes its run ID,
  snapshot revision, and all three history revisions.

Both endpoints read immutable published data. The browser allows one history
request at a time, coalesces newer revisions, and rejects responses from a previous
run or older section revision. It retains cached data after a request failure and
retries, including after terminal snapshots. History can briefly lag the live
board while its separate request is pending.

Charts redraw when their data or presentation changes. Replay options and the
population event feed update when their data changes. Stepping or playing a
loaded replay only renders the replay board, decision evidence, and controls.
A loaded replay remains usable after its summary leaves server retention.

## Verification

Run Java regression tests and checks:

```sh
./gradlew test spotlessCheck compileBenchmarkJava compileStressJava
```

For isolated browser checks, install `marionette_driver`, start Firefox with a
separate profile and its automation port, then run:

```sh
mkdir -p /tmp/lcs-history-firefox
firefox --headless --no-remote --profile /tmp/lcs-history-firefox --marionette about:blank
python scripts/check-arena-history-ui.py
```

The script uses the actual HTML, CSS, and JavaScript with controlled responses.
It checks incremental fetches, coalescing, stale responses, run replacement,
retry after a terminal update, replay retention, and unchanged history rendering.
It also prints synthetic history payload sizes and browser timings.
The existing `scripts/check-arena-ui.py` exercises an actual arena server,
including start/stop, pause/reload/resume, evaluation, replay, rule inspection,
and responsive layouts.

Measure backend allocations with:

```sh
./gradlew benchmarks -PbenchmarkArgs='HistoryBenchmark -f 1 -wi 1 -i 2 -w 1s -r 1s -prof gc -rf json -rff build/history-benchmark.json'
```

## Measurements, 2026-09-28

A short JMH run with 13 populated series gave:

| Points per series | Previous chart materialization | Cached history view | Previous allocation | Cached allocation |
|---|---:|---:|---:|---:|
| 100 | 7.51 µs | 0.52 µs | 81,656 B | 1,594 B |
| 2,048 | 96.57 µs | 0.57 µs | 1,521,513 B | 1,616 B |

The baseline executes the previous per-series copy/sort work; it excludes other
old snapshot costs. The cached benchmark includes the history view and fresh
statistics. These are focused microbenchmarks, not end-to-end training results.
Rebuilding changed history still incurs materialization cost.

At 2,048 points per series, headless Firefox measured approximately 0.21 ms per
unchanged render plus forced layout, compared with roughly 5 ms in the initial
investigation. The synthetic full snapshot was 998,958 bytes and took about
2.14 ms to parse; its lightweight counterpart was 175 bytes. The synthetic
fixture omits the current board and decision trace, so 175 bytes is not an
estimate of a real live response. The relevant change is removing roughly 1 MB
of unchanged history from each board update. Changed sections still transfer in
full. Browser measurements exclude painting and network/server latency.

Validation passed: 199 Java tests, formatting, benchmark and stress compilation,
the controlled browser history checks, and the existing browser lifecycle suite.
