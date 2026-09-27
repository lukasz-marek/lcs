package lmarek.lcs.arena;

import java.util.List;
import lmarek.lcs.xcs.XcsEvolutionEvent;
import lmarek.lcs.xcs.XcsRuleSnapshot;

public record RuleView(
    long id,
    List<String> condition,
    String action,
    double prediction,
    double error,
    double fitness,
    long experience,
    int numerosity,
    double actionSetSize,
    List<Long> parentIds,
    String birthReason,
    List<XcsEvolutionEvent> changes) {
  static RuleView from(XcsRuleSnapshot rule, List<XcsEvolutionEvent> changes) {
    return new RuleView(
        rule.id(),
        rule.condition(),
        rule.actionId(),
        rule.prediction(),
        rule.predictionError(),
        rule.fitness(),
        rule.experience(),
        rule.numerosity(),
        rule.actionSetSize(),
        rule.parentIds(),
        rule.birthReason().name(),
        List.copyOf(changes));
  }
}
