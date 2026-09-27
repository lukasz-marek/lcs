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

A 3-fork, 5-warmup, 5-measurement one-million-rule matching campaign has completed on the local machine and is being repeated with JSON output for the report. No 60-minute soak has been completed, so the memory and sustained-load gates are still open. The jcstress run completed 84 configurations across three tests with zero forbidden outcomes; it provides evidence but is not a proof that no race exists. The available machine
reports AMD Ryzen 7 9800X3D (8 cores / 16 threads), 62 GiB physical memory, and Temurin OpenJDK
25.0.4. This differs from the requested 15-worker target only in the number of logical CPUs exposed;
benchmark worker counts remain available through 15. Run the harnesses on the target host and record
JMH JSON, JFR, heap/GC metrics, p50/p95 and comparison results here before considering a parallel
threshold default. Retained histories remain 50 entries per resident rule; memory acceptance at two
million total rules is currently unmeasured.

The normal regression suite passes on the integrated code. The cancellation tests force actual task
exit with latches, and subscriber operations use a synchronized single-slot mailbox. jcstress tests ran successfully in quick mode. They test publication and subscriber close/order behavior, but do not prove the absence of every race. Existing web and browser tests should also be run as part of the target
release campaign.
