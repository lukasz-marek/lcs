package lmarek.lcs.xcs;

/** Optional JFR probe for the end-to-end duration of XCS decisions in a benchmark. */
public final class DecisionLatencyProbe {
  private DecisionLatencyProbe() {}

  public static void enable(XcsAgent<?, ?> agent) {
    agent.decisionDurationObserver(DecisionLatencyProbe::record);
  }

  private static void record(long durationNanos) {
    var event = new Decision();
    if (!event.isEnabled()) return;
    event.durationNanos = durationNanos;
    event.commit();
  }

  @jdk.jfr.Name("lcs.XcsDecisionLatency")
  @jdk.jfr.Label("XCS decision latency")
  @jdk.jfr.StackTrace(false)
  public static class Decision extends jdk.jfr.Event {
    public long durationNanos;
  }
}
