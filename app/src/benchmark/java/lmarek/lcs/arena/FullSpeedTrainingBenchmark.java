package lmarek.lcs.arena;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.xcs.DecisionLatencyProbe;
import lmarek.lcs.xcs.PopulationFixture;
import lmarek.lcs.xcs.XcsAgent;
import lmarek.lcs.xcs.XcsPopulationSnapshot;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import tools.jackson.databind.ObjectMapper;

/** Measures complete concurrent training batches, including shared XCS updates. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.SECONDS)
public class FullSpeedTrainingBenchmark {
  private static final int EVALUATION_INTERVAL = 500;

  @Param({"10000", "100000", "1000000"})
  public int size;

  @Param({"1", "2", "4", "8", "16"})
  public int workers;

  @Param({"RANDOM", "MCTS", "XCS"})
  public String opponent;

  @Param({"SHARED", "SHARDED"})
  public String populationPolicy;

  @Param({"false", "true"})
  public boolean dense;

  @Param({"false"})
  public boolean measureDecisionLatency;

  @Param({"SYNTHETIC"})
  public String populationFixture;

  private ArenaRun run;
  private @Nullable XcsPopulationSnapshot trainedPopulation;
  private Method batch;
  private Method promote;
  private Method evaluate;

  @Setup
  public void setup() throws Exception {
    int effectiveWorkers = Math.min(workers, Runtime.getRuntime().availableProcessors());
    int maximumPopulation = Math.min(1_000_000, size * 100);
    var learner =
        new AgentConfiguration(
            AgentKind.XCS, "STANDARD", Map.of("maximumPopulation", (double) maximumPopulation));
    var other =
        switch (opponent) {
          case "MCTS" -> new AgentConfiguration(AgentKind.MCTS, "FAST", Map.of());
          case "XCS" -> learner;
          default -> new AgentConfiguration(AgentKind.RANDOM, "UNIFORM", Map.of());
        };
    run =
        new ArenaRun(
            new ArenaRunRequest(
                learner,
                other,
                "834",
                Pacing.TURBO,
                EVALUATION_INTERVAL,
                2,
                TrainingMode.FULL_SPEED,
                effectiveWorkers),
            ArenaPerformance.defaults(),
            null,
            null,
            FullSpeedPopulationPolicy.valueOf(populationPolicy));
    if (populationFixture.equals("TRAINED")) {
      var fixtureDirectory = System.getProperty("lcs.sharding.fixtureDirectory");
      if (fixtureDirectory == null || fixtureDirectory.isBlank()) {
        throw new IllegalStateException(
            "Set -PshardingFixtureDirectory to the trained fixture directory");
      }
      var fixtures = new XcsRuleSetStore(new ObjectMapper(), fixtureDirectory);
      var trained = fixtures.load("shared-trained-start");
      if (trained == null) {
        throw new IllegalStateException("No shared-trained-start rule set in " + fixtureDirectory);
      }
      trainedPopulation = trained.population();
    }
    seedPopulation("agentA");
    if (opponent.equals("XCS")) seedPopulation("agentB");
    if (measureDecisionLatency) {
      instrumentPopulation("agentA");
      if (opponent.equals("XCS")) instrumentPopulation("agentB");
    }
    batch = ArenaRun.class.getDeclaredMethod("playFullSpeedBatch");
    batch.setAccessible(true);
    promote = ArenaRun.class.getDeclaredMethod("promoteShardsAtCheckpoint");
    promote.setAccessible(true);
    evaluate = ArenaRun.class.getDeclaredMethod("playEvaluationSeries");
    evaluate.setAccessible(true);
  }

  private void instrumentPopulation(String fieldName) throws Exception {
    var field = ArenaRun.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    DecisionLatencyProbe.enable((lmarek.lcs.xcs.XcsAgent<?, ?>) field.get(run));
    if (populationPolicy.equals("SHARDED")) {
      var shardsField =
          ArenaRun.class.getDeclaredField(fieldName.equals("agentA") ? "shardsA" : "shardsB");
      shardsField.setAccessible(true);
      for (var shard : (java.util.List<?>) shardsField.get(run)) {
        DecisionLatencyProbe.enable((lmarek.lcs.xcs.XcsAgent<?, ?>) shard);
      }
    }
  }

  private void seedPopulation(String fieldName) throws Exception {
    Field field = ArenaRun.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    var template = field.get(run);
    int maximumPopulation = Math.min(1_000_000, size * 100);
    var populationAgents = new java.util.ArrayList<XcsAgent<?, ?>>();
    populationAgents.add((XcsAgent<?, ?>) template);
    if (populationPolicy.equals("SHARDED")) {
      var shardsField =
          ArenaRun.class.getDeclaredField(fieldName.equals("agentA") ? "shardsA" : "shardsB");
      shardsField.setAccessible(true);
      var shards = (java.util.List<?>) shardsField.get(run);
      populationAgents.clear();
      for (var shard : shards) populationAgents.add((XcsAgent<?, ?>) shard);
    }
    if (populationFixture.equals("TRAINED")) {
      var snapshot = Objects.requireNonNull(trainedPopulation);
      for (var agent : populationAgents) agent.restorePopulation(snapshot);
    } else {
      PopulationFixture.seedAgent(template, size, maximumPopulation, dense);
      if (populationAgents.size() > 1) {
        for (int index = 1; index < populationAgents.size(); index++) {
          PopulationFixture.seedAgent(populationAgents.get(index), size, maximumPopulation, dense);
        }
      }
    }
    for (var agent : populationAgents) agent.sampledDeletion(true);
  }

  @Benchmark
  public void trainingBatch() throws Exception {
    batch.invoke(run);
    if (run.snapshot().trainingGames() % EVALUATION_INTERVAL == 0) {
      promote.invoke(run);
      evaluate.invoke(run);
    }
  }

  @TearDown
  public void close() {
    run.close();
  }
}
