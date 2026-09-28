package lmarek.lcs.xcs;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class PopulationBenchmark {
  @Param({"10000", "100000", "1000000"})
  public int size;

  @Param({"baseline", "sequential", "parallel"})
  public String implementation;

  @Param({"1", "1000"})
  public int actions;

  @Param({"1", "100"})
  public int numerosity;

  @Param({"false", "true"})
  public boolean highMatch;

  @Param({"false", "true"})
  public boolean histories;

  @Param({"1", "2", "4", "8", "15"})
  public int workers;

  private List<Long> updateIds;
  private Object population;
  private MatchingExecutor executor;
  private long nextAction;
  private List<String> legalActions;

  @Setup
  public void setup() throws Exception {
    population =
        PopulationFixture.create(
            implementation.equals("baseline"), size, actions, numerosity, highMatch, histories);
    executor =
        new MatchingExecutor(
            implementation.equals("parallel") ? workers : 1,
            implementation.equals("parallel") ? workers : 1,
            32_768);
    if (population instanceof XcsPopulation optimized) optimized.matchingExecutor(executor);
    updateIds = java.util.stream.LongStream.rangeClosed(1, size / numerosity).boxed().toList();
    int legalCount = Math.max(1, actions / 20);
    legalActions =
        java.util.stream.IntStream.range(0, legalCount).mapToObj(index -> "a" + index).toList();
  }

  @TearDown
  public void close() {
    executor.close();
  }

  @Benchmark
  public Object matching() {
    if (population instanceof XcsPopulation optimized)
      return optimized.match(PopulationFixture.STATE, legalActions, false, Set.of());
    return ((ReferenceXcsPopulation) population)
        .match(PopulationFixture.STATE, legalActions, false, Set.of());
  }

  @Benchmark
  public void update() {
    if (population instanceof XcsPopulation optimized)
      optimized.update(updateIds, (double) (nextAction++ % 2));
    else ((ReferenceXcsPopulation) population).update(updateIds, (double) (nextAction++ % 2));
  }

  @Benchmark
  public Object lookup() {
    if (population instanceof XcsPopulation optimized) return optimized.snapshot(size / numerosity);
    return ((ReferenceXcsPopulation) population).snapshot(size / numerosity);
  }

  @Benchmark
  public Object telemetry() {
    if (population instanceof XcsPopulation optimized) return optimized.telemetry();
    return ((ReferenceXcsPopulation) population).telemetry();
  }

  @Benchmark
  public Object frozenCopy() {
    if (population instanceof XcsPopulation optimized)
      return optimized.deepCopy(new SplittableRandom(9));
    return ((ReferenceXcsPopulation) population).deepCopy(new SplittableRandom(9));
  }

  @Benchmark
  public Object coveringAtCapacity() {
    var action = List.of("new" + nextAction++);
    if (population instanceof XcsPopulation optimized)
      return optimized.match(PopulationFixture.STATE, action, true, Set.of());
    return ((ReferenceXcsPopulation) population)
        .match(PopulationFixture.STATE, action, true, Set.of());
  }
}
