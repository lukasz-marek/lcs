package lmarek.lcs.xcs;

import java.util.List;
import java.util.Map;

/** Durable learning state for one XCS population. Null condition entries represent wildcards. */
public record XcsPopulationSnapshot(
    int version,
    List<Classifier> classifiers,
    List<XcsEvolutionEvent> events,
    Map<Long, List<XcsEvolutionEvent>> ruleChanges,
    long nextRuleId,
    long nextEventSequence,
    long iteration,
    long coveringCount,
    long gaCount) {
  public static final int CURRENT_VERSION = 1;

  public XcsPopulationSnapshot {
    classifiers = List.copyOf(classifiers);
    events = List.copyOf(events);
    ruleChanges =
        ruleChanges.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }

  public record Classifier(
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
    public Classifier {
      condition = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(condition));
      parentIds = List.copyOf(parentIds);
    }
  }
}
