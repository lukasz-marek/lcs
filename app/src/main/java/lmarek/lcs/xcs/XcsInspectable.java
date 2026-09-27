package lmarek.lcs.xcs;

import java.util.List;
import java.util.Optional;

public interface XcsInspectable {
  List<XcsRuleSnapshot> rules();

  Optional<XcsRuleSnapshot> rule(long id);

  /** Returns the rule and its retained changes from one population snapshot. */
  Optional<XcsRuleInspection> inspectRule(long id);

  List<XcsEvolutionEvent> evolutionEventsAfter(long sequence, int limit);

  /** The last 50 changes for a rule that is still in the population. */
  List<XcsEvolutionEvent> ruleChanges(long ruleId);
}
