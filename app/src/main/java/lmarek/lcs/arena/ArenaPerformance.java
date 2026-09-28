package lmarek.lcs.arena;

/** Startup worker settings for an arena run. */
public record ArenaPerformance(int matchingWorkers, int learningWorkers, int parallelThreshold) {
  public ArenaPerformance(int matchingWorkers, int parallelThreshold) {
    this(matchingWorkers, 16, parallelThreshold);
  }

  public ArenaPerformance {
    if (learningWorkers < 1
        || learningWorkers > 16
        || matchingWorkers < 1
        || matchingWorkers > 15
        || parallelThreshold < 1) {
      throw new IllegalArgumentException(
          "arena.performance requires 1..15 matching workers, 1..16 learning workers and a positive parallel threshold");
    }
  }

  public static ArenaPerformance defaults() {
    return new ArenaPerformance(1, 32_768);
  }

  public static int recommendedWorkers() {
    return Math.max(1, Math.min(15, Runtime.getRuntime().availableProcessors() - 1));
  }
}
