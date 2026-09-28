package lmarek.lcs.arena;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.xcs.PopulationFixture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/** Measures complete concurrent training batches, including shared XCS updates. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.SECONDS)
public class FullSpeedTrainingBenchmark {
  @Param({"10000", "100000", "1000000"})
  public int size;

  @Param({"1", "2", "4", "8", "16"})
  public int workers;

  @Param({"RANDOM", "MCTS", "XCS"})
  public String opponent;

  private ArenaRun run;
  private Method batch;

  @Setup
  public void setup() throws Exception {
    var learner =
        new AgentConfiguration(AgentKind.XCS, "STANDARD", Map.of("maximumPopulation", 1_000_000.0));
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
                1_000_000,
                20,
                TrainingMode.FULL_SPEED,
                workers));
    seedPopulation("agentA");
    if (opponent.equals("XCS")) seedPopulation("agentB");
    batch = ArenaRun.class.getDeclaredMethod("playFullSpeedBatch");
    batch.setAccessible(true);
  }

  private void seedPopulation(String fieldName) throws Exception {
    Field field = ArenaRun.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    PopulationFixture.seedAgent(field.get(run), size, size * 2, true);
  }

  @Benchmark
  public void trainingBatch() throws Exception {
    batch.invoke(run);
  }

  @TearDown
  public void close() {
    run.close();
  }
}
