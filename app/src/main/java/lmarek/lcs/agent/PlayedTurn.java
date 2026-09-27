package lmarek.lcs.agent;

import java.util.Objects;

public record PlayedTurn<S, M>(MoveTransition<S, M> transition, AgentDecision<M> decision) {
  public PlayedTurn {
    Objects.requireNonNull(transition);
    Objects.requireNonNull(decision);
  }
}
