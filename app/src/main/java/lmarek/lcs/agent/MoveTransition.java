package lmarek.lcs.agent;

import java.util.Objects;
import lmarek.lcs.game.Player;

public record MoveTransition<S, M>(S before, M move, S after, Player actor, int ply) {
  public MoveTransition {
    Objects.requireNonNull(before);
    Objects.requireNonNull(move);
    Objects.requireNonNull(after);
    Objects.requireNonNull(actor);
  }
}
