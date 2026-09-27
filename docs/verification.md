# Arena verification

## Audit outcome (2026-09-27)

The baseline has working game rules, agents, training and frozen evaluation,
REST/SSE control, replay, and a browser spectator. This audit fixed GUI defects
that backend tests could not detect. It does not establish that XCS becomes a
strong draughts player.

| Area | Evidence | Remaining boundary |
| --- | --- | --- |
| Draughts | Opening full-path perft through depth six; mandatory maximum capture, flying kings, delayed removal, promotion, repetition, move clocks and terminal precedence tests | No independent engine differential test; see the rule interpretation below |
| XCS | Covering, legal-action filtering, old-prediction error update, delayed own-turn targets, GA, mutation, merging, capacity protection, seeded determinism and frozen isolation tests | A new rewarded-action test demonstrates learning on a small problem; no draughts strength benchmark |
| UCT and random | Legal moves, forced wins, both players' value perspectives, root visits, rollout truncation, cancellation and deterministic seeds | Search strength is bounded by configured simulations and rollout length |
| Arena | Alternating seats, separate frozen evaluation, pause/resume/stop, bounded history, rule inspection and live telemetry tests | One in-memory run; no persistence or long-duration load test |
| Browser | Firefox automation exercises start/stop/restart, SSE updates, pause/reload/resume, all three agents, rule inspection, replay playback, learning/evaluation charts and three viewport widths | Chromium/WebKit have not been tested |

### Defects fixed in this audit

- Starting a run left the Start button pending after the run stopped.
- Pacing could not be chosen for a replacement run after stopping.
- The client accepted noncanonical seed text without normalizing it for the API.
- The bounded decision view could omit the selected action, including exploratory
  XCS choices and MCTS ties.
- Decision bar fills were inline spans, so their widths did not render.
- Population counts and fractional learning metrics shared a chart scale. A metric
  selector now keeps units separate; axes and isolated samples are visible.
- Failed-run detail appeared only in a short-lived toast. It now remains visible.
- Unchanged pieces were recreated and animated on every render. They are now
  reused, and reduced-motion preferences are respected.
- Board squares had no accessible piece descriptions. Rule attributes also now
  identify their square/context position in tooltips.

### Rule interpretation still requiring confirmation

The implementation treats the long-diagonal clause in FMJD Annex 1 §6.3 as an
extension from 16 to 21 moves per player, checked when both players reach 16.
The [2024 FMJD English text](https://www.fmjd.org/downloads/FMJD_Annexes_2024_8-sig.pdf)
does not clearly specify that timing. Existing tests establish consistency with
this implementation, not independent confirmation of the interpretation. Obtain
an authoritative clarification before describing the engine as fully certified
against that clause. The audit has left this behavior unchanged.

## Backend checks

```shell
./gradlew :app:test :app:spotlessCheck
```

The initial audit finished with 161 passing Java tests. The performance changes add deterministic reference comparison, executor cancellation, position-analysis, and publication tests; the full Java suite now has 173+ tests. Compiler warnings about intentional
exception reference comparisons remain; they do not fail the build.

## Repeat the browser check

Use a separate server: the script starts and replaces runs on the specified URL.
Start the server in one terminal:

```shell
./gradlew :app:bootRun --args='--server.port=18080'
```

Prepare Python and start Firefox with an isolated profile in another terminal:

```shell
python3 -m venv /tmp/lcs-browser-venv
/tmp/lcs-browser-venv/bin/pip install marionette_driver==3.7.1
mkdir -p /tmp/lcs-firefox-profile
firefox --headless --no-remote --profile /tmp/lcs-firefox-profile --marionette about:blank
```

Run the check from the repository root in a third terminal:

```shell
/tmp/lcs-browser-venv/bin/python scripts/check-arena-ui.py \
  --url http://127.0.0.1:18080 --screenshot /tmp/lcs-arena-mobile.png
```

The script uses actual browser clicks and changes, observes server-driven UI
updates, checks for runtime errors, and checks for horizontal overflow at desktop,
tablet and mobile widths. It stops its run before exiting. Firefox's Marionette
port defaults to 2828; pass `--port` when using a different port.

## Java 25 population performance and concurrency work

The optimized XCS implementation keeps ordered resident IDs, action buckets, structural buckets,
exact micro-classifier counts, and display-only incremental sums. Conditions are published as
private arrays with an immutable wildcard sentinel. The original `XcsPopulation`, `XcsAgent`, and
`MctsAgent` implementations are retained under `src/test` as deterministic references. Fixed seeds
and randomized mutation sequences compare decisions, traces, rule snapshots, events, histories, and
telemetry for 1, 2, 4, 8, and 15 matching workers. `verifyIndexes()` reconstructs indexes and counts
in those tests. Display averages use a 1e-12 absolute tolerance; no display aggregate feeds learning.

Arena matching defaults to one worker. Set `arena.performance.matching-workers` and
`arena.performance.parallel-threshold` to opt in. The pool is run-owned, bounded, and uses platform
threads; matching workers only inspect candidates. MCTS and training remain sequential. This keeps
seeded random draws and sequential UCT updates in their original order. Position analyses are scoped
to a move or rollout step and are bound to their source game and state.

`./gradlew benchmarks` runs JMH. It uses three forks and five warmup and measurement iterations,
with GC profiling and JSON output. Override the parameter selection using `-PbenchmarkArgs`; for
example, compare 100k sparse-rule fixtures with
`-PbenchmarkArgs='-f 3 -wi 5 -i 5 -p size=100000 -p implementation=baseline,sequential,parallel -p actions=10 -p numerosity=1 -p highMatch=false -p histories=false PopulationBenchmark.matching'`.
Fixture construction is outside the measured methods. The harness covers matching, ID lookup,
covering at capacity, telemetry, frozen copies, legal-position analysis, checked/analyzed moves, and
MCTS. `./gradlew concurrencyStress` runs jcstress tests for progress publication and subscriber
close/order protocols. `./gradlew largePopulationSoak` runs two one-million-macro populations with
50 retained metric updates per rule under a 32 GiB heap and writes `build/soak.jfr`. Its duration can
be changed with `-PsoakSeconds=3600`.

### Measurements and gates

The one-million-rule JMH campaign used Temurin OpenJDK 25.0.4, a 32 GiB heap, an AMD Ryzen 7
9800X3D (8 cores / 16 threads), and 1,000 action IDs. The fixture exposed 50 legal actions, so
matching inspected 50,000 of 1,000,000 candidates. All matching conditions matched. JMH used three
forks, five one-second warmup iterations, five one-second measurements, throughput and sample-time
modes, and GC profiling. The JSON report is at `app/build/jmh-sparse-million-final.json`.

| Implementation | Throughput | Sample p95 | Allocation per decision |
| --- | ---: | ---: | ---: |
| Original sequential reference | 31.9 decisions/s | 35.0 ms | 7.43 MB |
| Indexed sequential | 166.1 decisions/s | 8.63 ms | 11.40 MB |
| Indexed with 15 matching workers | 196.5 decisions/s | 6.39 ms | 12.73 MB |

The indexed sequential path is 5.2× faster than the reference on this fixture. Parallel matching is
18.3% faster than indexed sequential and lowers sample p95 by 26%, but misses the 20% throughput
gate. It stays disabled by default (`matching-workers=1`). Parallel matching allocates about 1.33 MB
more per decision on this fixture. The 2× baseline throughput gate passes for this sparse legal-action
fixture; this does not establish performance for every population shape.

The 10k-rule fixture used the same 50-of-1,000 legal-action ratio, with matching conditions all
matching. Indexed sequential measured 20,574 decisions/s and 0.052 ms sample p95, versus 6,946/s
and 0.147 ms for the reference. There is no regression on this fixture. Full results are in
`app/build/jmh-small-population.json`.

The 10-second soak smoke test constructed two populations with one million macro-classifiers each
and 50 retained changes per rule, then copied them and ran a frozen matching decision. It completed
under the 32 GiB heap. JFR showed 17.4 GiB used after a full collection and a temporary 32 GiB heap
before that collection; the full compaction pause took 1.81 seconds during fixture construction.
This is a setup and smoke result, not the required 60-minute stability test. The JFR recording is
`app/build/soak.jfr`. The harness does not exercise HTTP latency or repeated SSE reconnects alongside
those populations. Retained-heap behavior after warmup, long-run GC pauses, thread counts,
HTTP/control p95, and two-second cancellation remain unmeasured.

The jcstress quick run completed 84 configurations across progress publication and subscriber
close/order tests with zero forbidden outcomes. It does not prove that no races exist. The regression
suite and `spotlessCheck` pass. Run the full one-hour integrated soak and browser automation before
release; parallel matching stays opt-in until its throughput gate passes on the target host.
