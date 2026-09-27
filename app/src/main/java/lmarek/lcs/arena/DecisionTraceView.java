package lmarek.lcs.arena;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lmarek.lcs.agent.DecisionTrace;
import lmarek.lcs.agent.MctsDecisionTrace;
import lmarek.lcs.agent.RandomDecisionTrace;
import lmarek.lcs.agent.XcsDecisionTrace;
import org.jspecify.annotations.Nullable;

/** Bounded, presentation-safe decision evidence retained by the arena. */
public record DecisionTraceView(
    String algorithm,
    String selectedAction,
    int legalMoveCount,
    List<ActionScoreView> actions,
    @Nullable Boolean exploratory,
    @Nullable Integer simulations,
    @Nullable Long truncatedRollouts,
    @Nullable Integer matchingRuleCount,
    @Nullable Integer actionSetRuleCount,
    @Nullable Integer unknownLegalActionCount) {
  private static final int MAX_ACTIONS = 16;

  public DecisionTraceView {
    actions = List.copyOf(actions);
    if (actions.size() > MAX_ACTIONS) {
      throw new IllegalArgumentException("A decision trace retains at most 16 actions");
    }
  }

  static DecisionTraceView from(DecisionTrace trace, String selectedAction) {
    Objects.requireNonNull(trace);
    Objects.requireNonNull(selectedAction);
    return switch (trace) {
      case XcsDecisionTrace xcs -> fromXcs(xcs);
      case MctsDecisionTrace mcts -> fromMcts(mcts);
      case RandomDecisionTrace random ->
          new DecisionTraceView(
              "RANDOM",
              selectedAction,
              random.legalMoveCount(),
              List.of(),
              null,
              null,
              null,
              null,
              null,
              null);
      default ->
          new DecisionTraceView(
              trace.getClass().getSimpleName(),
              selectedAction,
              0,
              List.of(),
              null,
              null,
              null,
              null,
              null,
              null);
    };
  }

  private static DecisionTraceView fromXcs(XcsDecisionTrace trace) {
    var actions =
        trace.predictions().entrySet().stream()
            .sorted(
                Comparator.<java.util.Map.Entry<String, Double>, Boolean>comparing(
                        entry -> !entry.getKey().equals(trace.selectedAction()))
                    .thenComparing(
                        Comparator.comparingDouble(
                                (java.util.Map.Entry<String, Double> entry) -> entry.getValue())
                            .reversed())
                    .thenComparing(java.util.Map.Entry::getKey))
            .limit(MAX_ACTIONS)
            .map(entry -> new ActionScoreView(entry.getKey(), entry.getValue(), null, null))
            .toList();
    return new DecisionTraceView(
        "XCS",
        trace.selectedAction(),
        trace.predictions().size(),
        actions,
        trace.exploratory(),
        null,
        null,
        trace.matchingRuleIds().size(),
        trace.actionSetRuleIds().size(),
        trace.unknownLegalActions().size());
  }

  private static DecisionTraceView fromMcts(MctsDecisionTrace trace) {
    var actions =
        trace.visits().entrySet().stream()
            .sorted(
                Comparator.<java.util.Map.Entry<String, Long>, Boolean>comparing(
                        entry -> !entry.getKey().equals(trace.selectedMove()))
                    .thenComparing(
                        Comparator.comparingLong(
                                (java.util.Map.Entry<String, Long> entry) -> entry.getValue())
                            .reversed())
                    .thenComparing(java.util.Map.Entry::getKey))
            .limit(MAX_ACTIONS)
            .map(
                entry ->
                    new ActionScoreView(
                        entry.getKey(),
                        null,
                        entry.getValue(),
                        trace.meanValues().get(entry.getKey())))
            .toList();
    return new DecisionTraceView(
        "MCTS",
        trace.selectedMove(),
        trace.visits().size(),
        actions,
        null,
        trace.simulations(),
        trace.truncatedRollouts(),
        null,
        null,
        null);
  }
}
