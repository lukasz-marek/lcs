package lmarek.lcs.arena;

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

/** Full arena game, including learning, rules, telemetry, replay and publication. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.SECONDS)
public class TrainingBenchmark {
  @Param({"10000", "100000", "1000000"})
  public int size;

  @Param({"1", "2", "4", "8", "15"})
  public int workers;

  @Param({"false", "true"})
  public boolean dense;

  private ArenaRun run;
  private Method play;

  @Setup
  public void setup() throws Exception {
    var learner =
        new AgentConfiguration(
            AgentKind.XCS, "STANDARD", Map.of("maximumPopulation", (double) size));
    run =
        new ArenaRun(
            new ArenaRunRequest(
                learner,
                new AgentConfiguration(AgentKind.RANDOM, "UNIFORM", Map.of()),
                "834",
                Pacing.TURBO,
                500,
                20),
            new ArenaPerformance(workers, workers, 32768));
    var agentField = ArenaRun.class.getDeclaredField("agentA");
    agentField.setAccessible(true);
    PopulationFixture.seedAgent(agentField.get(run), size, dense);
    var delegate = agentField.get(run);
    agentField.set(
        run,
        java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {lmarek.lcs.agent.Agent.class, lmarek.lcs.xcs.XcsInspectable.class},
            (proxy, method, args) -> {
              var event = new AgentCall();
              event.operation = method.getName();
              event.begin();
              try {
                return method.invoke(delegate, args);
              } catch (java.lang.reflect.InvocationTargetException failure) {
                throw failure.getCause();
              } finally {
                event.end();
                event.commit();
              }
            }));
    play = ArenaRun.class.getDeclaredMethod("playTrainingGame");
    play.setAccessible(true);
  }

  @jdk.jfr.Name("lcs.BenchmarkAgentCall")
  @jdk.jfr.Label("Training agent call")
  @jdk.jfr.StackTrace(false)
  public static class AgentCall extends jdk.jfr.Event {
    public String operation;
  }

  @Benchmark
  public void trainingGame() throws Exception {
    play.invoke(run);
  }

  @TearDown
  public void close() {
    run.close();
  }
}
