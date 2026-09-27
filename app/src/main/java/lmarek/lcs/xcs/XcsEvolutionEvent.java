package lmarek.lcs.xcs;

import java.util.List;

/** One inspectable population change. Mutation records candidates, not proven improvements. */
public record XcsEvolutionEvent(
    long sequence, long iteration, Type type, long ruleId, List<Long> parentIds, String detail) {
  public XcsEvolutionEvent {
    parentIds = List.copyOf(parentIds);
  }

  public enum Type {
    COVERED,
    OFFSPRING,
    MUTATED,
    UPDATED,
    SUBSUMED,
    MERGED,
    DELETED,
    CAPACITY_REJECTED
  }
}
