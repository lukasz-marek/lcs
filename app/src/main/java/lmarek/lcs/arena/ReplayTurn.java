package lmarek.lcs.arena;

import java.util.Objects;
import lmarek.lcs.game.Player;

public record ReplayTurn(
    int ply,
    Player actor,
    MoveView move,
    BoardView boardAfter,
    DecisionTraceView trace,
    String agentKind) {
  public ReplayTurn {
    Objects.requireNonNull(actor);
    Objects.requireNonNull(move);
    Objects.requireNonNull(boardAfter);
    Objects.requireNonNull(trace);
    Objects.requireNonNull(agentKind);
  }
}
