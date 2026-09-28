package lmarek.lcs.arena;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Compare cached history reads with the previous chart materialization work. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HistoryBenchmark {
  @Param({"100", "2048"})
  public int points;

  private ArenaHistory history;
  private final Map<String, HierarchicalSeries> series = new LinkedHashMap<>();

  @Setup
  public void setup() {
    history = new ArenaHistory(Instant.now());
    var board = new BoardView(List.of(), List.of(), List.of(), List.of());
    var telemetry =
        Map.of(
            "xcs.population.micro",
            100.0,
            "xcs.generality",
            0.5,
            "xcs.predictionError",
            0.2,
            "xcs.covering",
            10.0);
    for (int i = 1; i <= points; i++) {
      history.recordTraining(
          new GameReplay(i, false, "A", "B", board, List.of(), "A win", "NO_PIECES"),
          GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES),
          telemetry,
          telemetry);
      history.recordEvaluationSeries(i, 0.5, 0.5);
    }
    history
        .charts()
        .forEach(
            (group, lines) ->
                lines.forEach(
                    (name, values) -> {
                      var line = new HierarchicalSeries(ArenaHistory.MAX_CHART_POINTS);
                      values.forEach(point -> line.add(point.x(), point.value()));
                      series.put(name, line);
                    }));
    history.view();
  }

  @Benchmark
  public Object cachedView() {
    return history.view();
  }

  @Benchmark
  public Object previousChartMaterialization() {
    var result = new LinkedHashMap<String, List<ChartPoint>>();
    series.forEach((name, line) -> result.put(name, line.points()));
    return Map.copyOf(result);
  }
}
