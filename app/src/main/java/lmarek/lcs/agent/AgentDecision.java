package lmarek.lcs.agent;

import java.util.Objects;

public record AgentDecision<M>(M move, DecisionTrace trace) {
  public AgentDecision {
    Objects.requireNonNull(move);
    Objects.requireNonNull(trace);
  }
}
