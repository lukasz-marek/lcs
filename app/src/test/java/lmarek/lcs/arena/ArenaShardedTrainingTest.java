package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.game.Player;
import lmarek.lcs.xcs.MatchingExecutor;
import lmarek.lcs.xcs.StateActionEncoder;
import lmarek.lcs.xcs.XcsAgent;
import lmarek.lcs.xcs.XcsBirthReason;
import lmarek.lcs.xcs.XcsParameters;
import lmarek.lcs.xcs.XcsPopulationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("StringConcatToTextBlock")
class ArenaShardedTrainingTest {
  @TempDir Path temporaryDirectory;

  private static final StateActionEncoder<String, String> ENCODER =
      new StateActionEncoder<>() {
        @Override
        public lmarek.lcs.xcs.CategoricalState encode(String state, Player perspective) {
          return new lmarek.lcs.xcs.CategoricalState(state, perspective.name());
        }

        @Override
        public String actionId(String move, Player perspective) {
          return move;
        }
      };

  @Test
  void trainingGamesAreAssignedToDeterministicDistinctSlotsWithinEachBatch() {
    assertThat(
            java.util.stream.LongStream.rangeClosed(1, 8)
                .map(game -> ArenaRun.shardForTrainingGame(game, 4))
                .boxed()
                .toList())
        .containsExactly(0L, 1L, 2L, 3L, 0L, 1L, 2L, 3L);

    for (int firstGame = 1; firstGame <= 8; firstGame += 4) {
      assertThat(
              java.util.stream.LongStream.range(firstGame, firstGame + 4)
                  .map(game -> ArenaRun.shardForTrainingGame(game, 4))
                  .distinct()
                  .count())
          .isEqualTo(4);
    }
  }

  @Test
  void shardsAreIsolatedAndPromotionCopiesTheWinningUniqueRuleSnapshot() {
    var first = agent(snapshot(1, "first"));
    var second = agent(snapshot(1, "second", 2, "third"));
    assertThat(first.populationSnapshot()).isNotEqualTo(second.populationSnapshot());

    int selected = ArenaRun.promoteShards(List.of(first, second), new double[] {0.25, 0.75});

    assertThat(selected).isEqualTo(1);
    assertThat(first.populationSnapshot()).isEqualTo(second.populationSnapshot());
    assertThat(first.populationSnapshot().classifiers())
        .extracting(XcsPopulationSnapshot.Classifier::id)
        .doesNotHaveDuplicates();
  }

  @Test
  void equalPromotionScoresKeepTheLowestWorkerIndex() {
    assertThat(ArenaRun.championIndex(new double[] {0.5, 0.5, 0.25})).isZero();
  }

  @Test
  void stoppingBetweenShardedBatchesPersistsTheLastPromotedCheckpoint() throws Exception {
    var savedName = "sharded-checkpoint";
    var learner = new AgentConfiguration(AgentKind.XCS, "STANDARD", Map.of(), savedName);
    var request =
        new ArenaRunRequest(
            learner,
            new AgentConfiguration(AgentKind.RANDOM, "UNIFORM", Map.of()),
            "1001",
            Pacing.TURBO,
            2,
            2,
            TrainingMode.FULL_SPEED,
            2);
    var store = new XcsRuleSetStore(new ObjectMapper(), temporaryDirectory.toString());
    var run =
        new ArenaRun(
            request, ArenaPerformance.defaults(), store, null, FullSpeedPopulationPolicy.SHARDED);
    try {
      run.start();
      await(() -> run.snapshot().evaluationGames() >= 2, Duration.ofSeconds(30));
      run.pause();
      await(() -> run.snapshot().status() == RunStatus.PAUSED, Duration.ofSeconds(10));
      long pausedTrainingGames = run.snapshot().trainingGames();
      Thread.sleep(20);
      assertThat(run.snapshot().trainingGames()).isEqualTo(pausedTrainingGames);

      run.stop();
      await(() -> run.snapshot().status() == RunStatus.STOPPED, Duration.ofSeconds(10));

      var persisted = store.load(savedName);
      assertThat(persisted).isNotNull();
      assertThat(persisted.population()).isEqualTo(promotedSnapshot(run));
    } finally {
      run.close();
    }
  }

  private static XcsAgent<String, String> agent(XcsPopulationSnapshot snapshot) {
    return XcsAgent.restore(
        "x", "XCS", ENCODER, XcsParameters.defaults(), 83, MatchingExecutor.sequential(), snapshot);
  }

  private static XcsPopulationSnapshot snapshot(Object... idActionPairs) {
    var classifiers =
        java.util.stream.IntStream.range(0, idActionPairs.length / 2)
            .mapToObj(
                index ->
                    new XcsPopulationSnapshot.Classifier(
                        ((Number) idActionPairs[index * 2]).longValue(),
                        Collections.singletonList(null),
                        (String) idActionPairs[index * 2 + 1],
                        0.0,
                        0.0,
                        0.01,
                        0,
                        1,
                        1.0,
                        0,
                        List.of(),
                        XcsBirthReason.COVERING))
            .toList();
    long nextId =
        classifiers.stream().mapToLong(XcsPopulationSnapshot.Classifier::id).max().orElse(0) + 1;
    return new XcsPopulationSnapshot(
        XcsPopulationSnapshot.CURRENT_VERSION,
        classifiers,
        List.of(),
        Map.of(),
        nextId,
        1,
        0,
        0,
        0);
  }

  private static XcsPopulationSnapshot promotedSnapshot(ArenaRun run) throws Exception {
    var field = ArenaRun.class.getDeclaredField("promotedSnapshotA");
    field.setAccessible(true);
    return (XcsPopulationSnapshot) field.get(run);
  }

  private static void await(CheckedCondition condition, Duration timeout) throws Exception {
    var deadline = Instant.now().plus(timeout);
    while (!condition.evaluate() && Instant.now().isBefore(deadline)) Thread.sleep(2);
    assertThat(condition.evaluate()).as("condition before timeout").isTrue();
  }

  @FunctionalInterface
  private interface CheckedCondition {
    boolean evaluate() throws Exception;
  }
}
