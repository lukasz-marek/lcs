package lmarek.lcs.agent;

import java.util.Map;
import java.util.Objects;

public record MctsDecisionTrace(
    String selectedMove,
    Map<String, Long> visits,
    Map<String, Double> meanValues,
    int simulations,
    long truncatedRollouts)
    implements DecisionTrace {
  public MctsDecisionTrace {
    Objects.requireNonNull(selectedMove);
    visits = Map.copyOf(visits);
    meanValues = Map.copyOf(meanValues);
  }
}
