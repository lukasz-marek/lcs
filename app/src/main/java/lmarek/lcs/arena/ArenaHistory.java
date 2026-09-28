package lmarek.lcs.arena;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;

/** Run-local aggregates and bounded detail retained for the spectator UI. */
final class ArenaHistory {
  static final int MAX_CHART_POINTS = 2_048;
  static final int MAX_REPLAYS = 20;
  static final int MAX_EVOLUTION_EVENTS = 200;
  private static final int ROLLING_WINDOW = 100;

  private final Instant startedAt;
  private final Deque<ResultForA> rolling = new ArrayDeque<>();
  private final Deque<GameReplay> replays = new ArrayDeque<>();
  private final Deque<EvolutionHighlight> evolution = new ArrayDeque<>();
  private final Map<String, HierarchicalSeries> resultSeries = new LinkedHashMap<>();
  private final Map<String, HierarchicalSeries> learningSeries = new LinkedHashMap<>();
  private final Map<String, HierarchicalSeries> evaluationSeries = new LinkedHashMap<>();
  private long chartsRevision;
  private long replaysRevision;
  private long evolutionRevision;
  private @org.jspecify.annotations.Nullable Map<String, Map<String, List<ChartPoint>>>
      cachedCharts;
  private @org.jspecify.annotations.Nullable List<ReplaySummary> cachedReplays;
  private @org.jspecify.annotations.Nullable List<EvolutionHighlight> cachedEvolution;
  private long trainingGames;
  private long evaluationGames;
  private long aWins;
  private long bWins;
  private long draws;
  private long totalTrainingPlies;

  ArenaHistory(Instant startedAt) {
    this.startedAt = startedAt;
    resultSeries.put("A wins", new HierarchicalSeries(MAX_CHART_POINTS));
    resultSeries.put("B wins", new HierarchicalSeries(MAX_CHART_POINTS));
    resultSeries.put("Draws", new HierarchicalSeries(MAX_CHART_POINTS));
    evaluationSeries.put("A score", new HierarchicalSeries(MAX_CHART_POINTS));
    evaluationSeries.put("B score", new HierarchicalSeries(MAX_CHART_POINTS));
  }

  synchronized void recordTraining(
      GameReplay replay,
      GameOutcome outcome,
      Map<String, Double> telemetryA,
      Map<String, Double> telemetryB) {
    chartsRevision++;
    cachedCharts = null;
    trainingGames++;
    totalTrainingPlies += replay.turns().size();
    var result = resultForA(outcome, replay.whiteCompetitorId());
    rolling.addLast(result);
    if (rolling.size() > ROLLING_WINDOW) {
      rolling.removeFirst();
    }
    switch (result) {
      case WIN -> aWins++;
      case LOSS -> bWins++;
      case DRAW -> draws++;
    }
    series(resultSeries, "A wins").add(trainingGames, (double) aWins);
    series(resultSeries, "B wins").add(trainingGames, (double) bWins);
    series(resultSeries, "Draws").add(trainingGames, (double) draws);
    recordLearning("A", telemetryA);
    recordLearning("B", telemetryB);
    retainReplay(replay);
  }

  synchronized void recordEvaluationSeries(long atTrainingGame, double aScore, double bScore) {
    chartsRevision++;
    cachedCharts = null;
    series(evaluationSeries, "A score").add(atTrainingGame, aScore);
    series(evaluationSeries, "B score").add(atTrainingGame, bScore);
  }

  synchronized void recordEvaluationReplay(GameReplay replay) {
    evaluationGames++;
    retainReplay(replay);
  }

  synchronized void addEvolution(EvolutionHighlight highlight) {
    evolutionRevision++;
    cachedEvolution = null;
    evolution.addLast(highlight);
    while (evolution.size() > MAX_EVOLUTION_EVENTS) {
      evolution.removeFirst();
    }
  }

  synchronized long trainingGames() {
    return trainingGames;
  }

  synchronized long evaluationGames() {
    return evaluationGames;
  }

  synchronized ArenaStatisticsView statistics() {
    long rollingWins = rolling.stream().filter(result -> result == ResultForA.WIN).count();
    long rollingDraws = rolling.stream().filter(result -> result == ResultForA.DRAW).count();
    double elapsed =
        Math.max(0.001, Duration.between(startedAt, Instant.now()).toMillis() / 1_000.0);
    return new ArenaStatisticsView(
        aWins,
        bWins,
        draws,
        rolling.isEmpty()
            ? "—"
            : "%dW · %dD · %dL"
                .formatted(rollingWins, rollingDraws, rolling.size() - rollingWins - rollingDraws),
        trainingGames == 0 ? 0.0 : (double) totalTrainingPlies / trainingGames,
        trainingGames / elapsed);
  }

  synchronized List<ReplaySummary> replaySummaries() {
    if (cachedReplays == null)
      cachedReplays = List.copyOf(replays.stream().map(ReplaySummary::from).toList());
    return cachedReplays;
  }

  synchronized GameReplay replay(long gameNumber) {
    return replays.stream()
        .filter(replay -> replay.gameNumber() == gameNumber)
        .findFirst()
        .orElseThrow(() -> new ArenaNotFoundException("Replay is no longer retained"));
  }

  synchronized List<EvolutionHighlight> evolutionHighlights() {
    if (cachedEvolution == null) cachedEvolution = List.copyOf(evolution);
    return cachedEvolution;
  }

  synchronized Map<String, Map<String, List<ChartPoint>>> charts() {
    if (cachedCharts == null)
      cachedCharts =
          Map.of(
              "results", points(resultSeries),
              "learning", points(learningSeries),
              "evaluation", points(evaluationSeries));
    return cachedCharts;
  }

  synchronized ArenaHistoryView view() {
    return new ArenaHistoryView(
        trainingGames,
        evaluationGames,
        statistics(),
        replaySummaries(),
        evolutionHighlights(),
        charts(),
        new HistoryRevisions(chartsRevision, replaysRevision, evolutionRevision));
  }

  private void recordLearning(String competitor, Map<String, Double> telemetry) {
    addLearning(competitor + " population", telemetry.get("xcs.population.micro"));
    addLearning(competitor + " generality", telemetry.get("xcs.generality"));
    addLearning(competitor + " error", telemetry.get("xcs.predictionError"));
    addLearning(competitor + " covering", telemetry.get("xcs.covering"));
  }

  private void addLearning(String name, @org.jspecify.annotations.Nullable Double value) {
    if (value != null) {
      learningSeries
          .computeIfAbsent(name, ignored -> new HierarchicalSeries(MAX_CHART_POINTS))
          .add(trainingGames, value);
    }
  }

  private void retainReplay(GameReplay replay) {
    replaysRevision++;
    cachedReplays = null;
    replays.addLast(replay);
    while (replays.size() > MAX_REPLAYS) {
      replays.removeFirst();
    }
  }

  private static ResultForA resultForA(GameOutcome outcome, String whiteCompetitorId) {
    if (outcome.isDraw()) {
      return ResultForA.DRAW;
    }
    var aWasWhite = "A".equals(whiteCompetitorId);
    var aWon = outcome.winner() == (aWasWhite ? Player.WHITE : Player.BLACK);
    return aWon ? ResultForA.WIN : ResultForA.LOSS;
  }

  private static Map<String, List<ChartPoint>> points(Map<String, HierarchicalSeries> series) {
    var result = new LinkedHashMap<String, List<ChartPoint>>();
    series.forEach((name, values) -> result.put(name, values.points()));
    return Map.copyOf(result);
  }

  private static HierarchicalSeries series(Map<String, HierarchicalSeries> values, String name) {
    return java.util.Objects.requireNonNull(values.get(name));
  }

  private enum ResultForA {
    WIN,
    DRAW,
    LOSS
  }
}
