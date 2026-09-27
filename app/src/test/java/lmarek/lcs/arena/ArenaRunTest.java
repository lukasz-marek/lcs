package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lmarek.lcs.agent.AgentKind;
import org.junit.jupiter.api.Test;

class ArenaRunTest {
  @Test
  void alternatesCompetitorColorsAndStopsAtADecisionBoundary() throws Exception {
    var run = new ArenaRun(request(random(), random(), 500, 20));
    try {
      run.start();
      await(() -> run.snapshot().trainingGames() >= 4, Duration.ofSeconds(8));
      run.stop();
      await(() -> run.snapshot().status() == RunStatus.STOPPED, Duration.ofSeconds(3));

      var replays = run.snapshot().recentReplays();
      assertThat(replays).hasSizeGreaterThanOrEqualTo(4);
      for (var replay : replays) {
        assertThat(replay.whiteCompetitorId()).isEqualTo(replay.gameNumber() % 2 == 1 ? "A" : "B");
      }
    } finally {
      run.close();
    }
  }

  @Test
  void schedulesSeparateAlternatingFrozenEvaluationGamesForALearner() throws Exception {
    var xcs =
        new AgentConfiguration(
            AgentKind.XCS,
            "STANDARD",
            Map.of(
                "maximumPopulation", 10_000.0,
                "beta", 0.2,
                "gamma", 0.99,
                "explorationProbability", 0.2,
                "wildcardProbability", 0.33));
    var run = new ArenaRun(request(xcs, random(), 1, 2));
    try {
      run.start();
      await(() -> run.snapshot().evaluationGames() >= 2, Duration.ofSeconds(15));
      run.stop();
      await(() -> run.snapshot().status() == RunStatus.STOPPED, Duration.ofSeconds(3));

      var evaluations =
          run.snapshot().recentReplays().stream()
              .filter(ReplaySummary::evaluation)
              .sorted(java.util.Comparator.comparingLong(ReplaySummary::gameNumber))
              .toList();
      assertThat(evaluations).hasSizeGreaterThanOrEqualTo(2);
      assertThat(evaluations).extracting(ReplaySummary::whiteCompetitorId).contains("A", "B");
      assertThat(
              java.util.stream.IntStream.range(0, evaluations.size() - 1)
                  .filter(
                      index ->
                          evaluations.get(index + 1).gameNumber()
                              == evaluations.get(index).gameNumber() + 1)
                  .anyMatch(
                      index ->
                          evaluations.get(index).whiteCompetitorId().equals("A")
                              && evaluations.get(index + 1).whiteCompetitorId().equals("B")))
          .isTrue();
      assertThat(run.snapshot().trainingGames()).isPositive();
    } finally {
      run.close();
    }
  }

  @Test
  void exposesLearningBeforeTheFirstGameCompletesAndRetainsItWhenStopped() throws Exception {
    var learner = new AgentConfiguration(AgentKind.XCS, "STANDARD", Map.of());
    var run = new ArenaRun(new ArenaRunRequest(learner, random(), "91", Pacing.LIVE, 500, 20));
    try {
      run.start();
      await(
          () -> run.snapshot().currentGame() != null && run.snapshot().currentGame().ply() >= 1,
          Duration.ofSeconds(5));
      run.pause();
      await(() -> run.snapshot().status() == RunStatus.PAUSED, Duration.ofSeconds(3));
      var paused = run.snapshot();
      assertThat(paused.trainingGames()).isZero();
      assertThat(paused.telemetry().get("A").get("xcs.population.micro")).isPositive();
      assertThat(paused.evolutionHighlights()).isNotEmpty();
      assertThat(paused.evolutionHighlights())
          .allSatisfy(event -> assertThat(event.gameNumber()).isEqualTo(1));
      run.stop();
      await(() -> run.snapshot().status() == RunStatus.STOPPED, Duration.ofSeconds(3));
      assertThat(run.snapshot().telemetry()).isEqualTo(paused.telemetry());
      assertThat(run.snapshot().evolutionHighlights()).isEqualTo(paused.evolutionHighlights());
    } finally {
      run.close();
    }
  }

  private static ArenaRunRequest request(
      AgentConfiguration a, AgentConfiguration b, int evaluationInterval, int evaluationGames) {
    return new ArenaRunRequest(a, b, "91", Pacing.TURBO, evaluationInterval, evaluationGames);
  }

  private static AgentConfiguration random() {
    return new AgentConfiguration(AgentKind.RANDOM, "UNIFORM", Map.of());
  }

  private static void await(CheckedCondition condition, Duration timeout) throws Exception {
    var deadline = Instant.now().plus(timeout);
    while (!condition.evaluate() && Instant.now().isBefore(deadline)) {
      Thread.sleep(2);
    }
    assertThat(condition.evaluate()).as("condition before timeout").isTrue();
  }

  @FunctionalInterface
  private interface CheckedCondition {
    boolean evaluate() throws Exception;
  }
}
