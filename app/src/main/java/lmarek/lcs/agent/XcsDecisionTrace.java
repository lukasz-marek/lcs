package lmarek.lcs.agent;

import java.util.List;
import java.util.Map;

public record XcsDecisionTrace(
    Map<String, Double> predictions,
    String selectedAction,
    boolean exploratory,
    List<String> unknownLegalActions,
    List<Long> matchingRuleIds,
    List<Long> actionSetRuleIds)
    implements DecisionTrace {
  public XcsDecisionTrace {
    predictions = Map.copyOf(predictions);
    unknownLegalActions = List.copyOf(unknownLegalActions);
    matchingRuleIds = List.copyOf(matchingRuleIds);
    actionSetRuleIds = List.copyOf(actionSetRuleIds);
  }
}
