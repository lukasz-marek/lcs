package lmarek.lcs.arena;

/** Startup settings; parallel execution remains opt-in until measured release gates pass. */
public record ArenaPerformance(int matchingWorkers, int parallelThreshold) {
  public ArenaPerformance {
    if (matchingWorkers < 1 || matchingWorkers > 15 || parallelThreshold < 1) {
      throw new IllegalArgumentException(
          "arena.performance requires 1..15 matching workers and a positive parallel threshold");
    }
  }

  public static ArenaPerformance defaults() {
    return new ArenaPerformance(1, 32_768);
  }

  public static int recommendedWorkers() {
    return Math.max(1, Math.min(15, Runtime.getRuntime().availableProcessors() - 1));
  }
}
