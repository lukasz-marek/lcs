package lmarek.lcs.xcs;

import java.util.List;

/** Immutable data shown by the rule inspector. A {@code *} condition value is a wildcard. */
public record XcsRuleSnapshot(
    long id,
    List<String> condition,
    String actionId,
    double prediction,
    double predictionError,
    double fitness,
    long experience,
    int numerosity,
    double actionSetSize,
    long timestamp,
    List<Long> parentIds,
    XcsBirthReason birthReason) {
  public XcsRuleSnapshot {
    condition = List.copyOf(condition);
    parentIds = List.copyOf(parentIds);
  }

  public double generality() {
    return condition.stream().filter(XcsRuleSnapshot::isWildcard).count()
        / (double) condition.size();
  }

  public static boolean isWildcard(String value) {
    return "*".equals(value);
  }
}
