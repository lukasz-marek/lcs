package lmarek.lcs.arena;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.GameRunner;
import lmarek.lcs.game.Player;
import lmarek.lcs.search.RandomAgent;
import lmarek.lcs.xcs.XcsAgent;
import tools.jackson.databind.ObjectMapper;

/** Trains both full-speed policies and compares them on paired, color-balanced held-out games. */
public final class ShardingQualityExperiment {
  private static final int DEFAULT_TRAINING_GAMES = 1_000;
  private static final int DEFAULT_HELD_OUT_GAMES = 1_000;
  private static final int DEFAULT_WORKERS = 4;
  private static final long SEED = 8_347_211L;

  private ShardingQualityExperiment() {}

  public static void main(String[] arguments) throws Exception {
    int trainingGames = argument(arguments, 0, DEFAULT_TRAINING_GAMES);
    int heldOutGames = argument(arguments, 1, DEFAULT_HELD_OUT_GAMES);
    int workers = argument(arguments, 2, DEFAULT_WORKERS);
    if (trainingGames < 1 || trainingGames % workers != 0) {
      throw new IllegalArgumentException("trainingGames must be positive and divisible by workers");
    }
    if (heldOutGames < 2 || heldOutGames % 2 != 0) {
      throw new IllegalArgumentException("heldOutGames must be a positive even number");
    }

    var learner =
        new AgentConfiguration(
            AgentKind.XCS,
            "STANDARD",
            Map.of("maximumPopulation", 1_000_000.0, "explorationProbability", 0.2));
    var opponent = new AgentConfiguration(AgentKind.RANDOM, "UNIFORM", Map.of());
    var request =
        new ArenaRunRequest(
            learner,
            opponent,
            Long.toString(SEED),
            Pacing.TURBO,
            trainingGames,
            2,
            TrainingMode.FULL_SPEED,
            workers);
    var performance = new ArenaPerformance(1, 1, 32_768);
    try (var shared =
            new ArenaRun(request, performance, null, null, FullSpeedPopulationPolicy.SHARED);
        var sharded =
            new ArenaRun(request, performance, null, null, FullSpeedPopulationPolicy.SHARDED)) {
      while (shared.snapshot().trainingGames() < trainingGames) {
        shared.playFullSpeedBatch();
        sharded.playFullSpeedBatch();
      }
      sharded.promoteShardsAtCheckpoint();

      var fixtureDirectory =
          arguments.length > 3 ? arguments[3] : "build/sharding-quality-fixtures";
      var fixtures = new XcsRuleSetStore(new ObjectMapper(), fixtureDirectory);
      fixtures.save(
          "shared-trained-start",
          learner(shared).parameters(),
          learner(shared).populationSnapshot());

      var paired = compareHeldOut(shared, sharded, heldOutGames);
      System.out.printf(
          "Training games: %d, workers: %d, held-out games: %d%n"
              + "Shared: %.3f, sharded: %.3f, paired difference: %.3f pp (95%% CI %.3f to %.3f pp)%n"
              + "Rules: shared %d, sharded %d; quality guard: %s%n",
          trainingGames,
          workers,
          heldOutGames,
          paired.sharedScore(),
          paired.shardedScore(),
          paired.meanDifference() * 100.0,
          paired.lowerConfidenceBound() * 100.0,
          paired.upperConfidenceBound() * 100.0,
          learner(shared).rules().size(),
          learner(sharded).rules().size(),
          paired.meanDifference() >= -0.05 ? "PASS" : "FAIL");
      System.out.printf(
          "Saved trained shared fixture: %s%n", Path.of(fixtureDirectory).toAbsolutePath());
    }
  }

  private static PairedScore compareHeldOut(ArenaRun shared, ArenaRun sharded, int games)
      throws Exception {
    var sharedLearner = learner(shared).frozenCopy(mixSeed(SEED ^ 0xA0761D6478BD642FL));
    var shardedLearner = learner(sharded).frozenCopy(mixSeed(SEED ^ 0xA0761D6478BD642FL));
    var game = new DraughtsGame();
    var runner = new GameRunner<DraughtsState, DraughtsMove>(game, 1_000);
    double sharedPoints = 0.0;
    double shardedPoints = 0.0;
    double differenceSum = 0.0;
    double differenceSquares = 0.0;
    for (int index = 0; index < games; index++) {
      boolean aIsWhite = index % 2 == 0;
      long pairedSeed = mixSeed(SEED ^ 0xE7037ED1A0B428DBL ^ index);
      var sharedOpponent = new RandomAgent<DraughtsState, DraughtsMove>("B", "Random", pairedSeed);
      var shardedOpponent = new RandomAgent<DraughtsState, DraughtsMove>("B", "Random", pairedSeed);
      var sharedOutcome =
          runner
              .run(
                  aIsWhite ? sharedLearner : sharedOpponent,
                  aIsWhite ? sharedOpponent : sharedLearner,
                  index + 1L,
                  false,
                  ignored -> {})
              .outcome();
      var shardedOutcome =
          runner
              .run(
                  aIsWhite ? shardedLearner : shardedOpponent,
                  aIsWhite ? shardedOpponent : shardedLearner,
                  index + 1L,
                  false,
                  ignored -> {})
              .outcome();
      double sharedScore = pointsFor(sharedOutcome, aIsWhite);
      double shardedScore = pointsFor(shardedOutcome, aIsWhite);
      double difference = shardedScore - sharedScore;
      sharedPoints += sharedScore;
      shardedPoints += shardedScore;
      differenceSum += difference;
      differenceSquares += difference * difference;
    }
    double meanDifference = differenceSum / games;
    double variance =
        Math.max(0.0, (differenceSquares - differenceSum * differenceSum / games) / (games - 1));
    double margin = 1.96 * Math.sqrt(variance / games);
    return new PairedScore(
        sharedPoints / games,
        shardedPoints / games,
        meanDifference,
        meanDifference - margin,
        meanDifference + margin);
  }

  @SuppressWarnings("unchecked")
  private static XcsAgent<DraughtsState, DraughtsMove> learner(ArenaRun run) throws Exception {
    Field field = ArenaRun.class.getDeclaredField("agentA");
    field.setAccessible(true);
    return (XcsAgent<DraughtsState, DraughtsMove>) field.get(run);
  }

  private static double pointsFor(GameOutcome outcome, boolean candidateIsWhite) {
    if (outcome.isDraw()) return 0.5;
    return (outcome.winner() == Player.WHITE) == candidateIsWhite ? 1.0 : 0.0;
  }

  private static int argument(String[] arguments, int index, int fallback) {
    return arguments.length > index ? Integer.parseInt(arguments[index]) : fallback;
  }

  private static long mixSeed(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }

  private record PairedScore(
      double sharedScore,
      double shardedScore,
      double meanDifference,
      double lowerConfidenceBound,
      double upperConfidenceBound) {}
}
