package lmarek.lcs.xcs;

import java.util.List;
import java.util.Objects;

/** An atomic rule-inspector view from one locked population read. */
public record XcsRuleInspection(XcsRuleSnapshot rule, List<XcsEvolutionEvent> changes) {
  public XcsRuleInspection {
    Objects.requireNonNull(rule);
    changes = List.copyOf(changes);
  }
}
