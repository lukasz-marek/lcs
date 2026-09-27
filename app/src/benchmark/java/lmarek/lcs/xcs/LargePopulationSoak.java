package lmarek.lcs.xcs;

import java.lang.management.ManagementFactory;
import java.util.SplittableRandom;

/** Allocation and lifecycle soak for two retained-history populations. */
public final class LargePopulationSoak {
  private LargePopulationSoak() {}

  public static void main(String[] args) throws Exception {
    long durationSeconds = args.length == 0 ? 3600 : Long.parseLong(args[0]);
    if (durationSeconds < 1) throw new IllegalArgumentException("duration must be positive");
    System.out.printf(
        "JDK=%s processors=%d maxHeap=%d durationSeconds=%d%n",
        Runtime.version(),
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory(),
        durationSeconds);
    var first = (XcsPopulation) PopulationFixture.create(false, 1_000_000, 1_000, 1, false, true);
    var second = (XcsPopulation) PopulationFixture.create(false, 1_000_000, 1_000, 1, false, true);
    var matcher = new MatchingExecutor(1, 32_768);
    first.matchingExecutor(matcher);
    second.matchingExecutor(matcher);
    long deadline = System.nanoTime() + durationSeconds * 1_000_000_000L;
    long round = 0;
    while (System.nanoTime() < deadline) {
      var state = PopulationFixture.STATE;
      first.match(state, java.util.List.of("a0"), false, java.util.Set.of());
      second.match(state, java.util.List.of("a0"), false, java.util.Set.of());
      if (round % 10 == 0) {
        first.snapshot(1);
        first.ruleChanges(1);
        first.telemetry();
        var frozenA = first.deepCopy(new SplittableRandom(round));
        var frozenB = second.deepCopy(new SplittableRandom(round + 1));
        frozenA.match(state, java.util.List.of("a0"), false, java.util.Set.of());
        frozenB.match(state, java.util.List.of("a0"), false, java.util.Set.of());
        if (frozenA.telemetry().get("xcs.population.macro") != 1_000_000.0
            || frozenB.telemetry().get("xcs.population.macro") != 1_000_000.0) {
          throw new AssertionError("Frozen evaluation copy lost residents");
        }
      }
      if (round % 100 == 0) {
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        System.out.printf(
            "round=%d usedHeap=%d committedHeap=%d%n",
            round, memory.getUsed(), memory.getCommitted());
      }
      round++;
    }
    matcher.close();
    first.verifyIndexes();
    second.verifyIndexes();
  }
}
